# Workspace tabs 8a: tabs hold panes Implementation Plan

**Status: executed on 2026-10-20** (`c1540b4e9..a49e33f69`); see `docs/STATUS.md`. Written against
`claude/workspace-tabs` at `2d7233474` ("Docs: tabs and panes spec, dimming and the strip's motion kept as today"); see
Workspace. Deviations from the plan's text: (1) Task 1: two `TabDragTest` pins for a press whose item closes, or whose
entry shows another pane, before the threshold; each also asserts the drag label never showed a title, since the
review's assertions alone passed with the guard removed. (2) Task 2: `MoveTabsTest` pins that a tab moved to a new
window, or back to the main window, takes the focus there (the deleted `BringBackTest` held the only such pin);
`WorkspaceFileReadTest` reads a legacy `role = "pane"` window and drops it alone. (3) Task 3:
`ClassicTabsTest.thePlusAndTheCloseActLikeButtons` keeps its strip after the "+" (which now opens a tab). (4) Task 4:
`ClassicTabsTest` expects the window strip's "Close Tab"; a controller-ruled pin,
`TabDragTest.aPressOnAnEntrysCloseStartsNoTabDrag`; and `WindowContent.selectTab` selects an item in a hidden tab before
its tab shows, so no other item of that tab turns active on the way (no stray `onActive` or SDK `TAB_SELECTED`), pinned
by `WorkspaceTabsTest` and `ParentTabsViewTest.selectingAnItemOfAHiddenTabPublishesOneTabSelectedForIt`. (5) The
final-review fix wave (`d45b8171d`): `paneClicks` finds the pane by the press's location, so a press on a view's bare
area activates its pane; the listeners register after `addHost`; pins for Move Tab to New Window from a full window,
Close Pane and Close Tab at the edges, a split tab arriving settled, and `paneClicks`'s right press, other window and
listener baseline. No anchor of Task 6 needed refreshing.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jasper has two levels: a window holds tabs, and a tab holds panes split in any direction, each pane holding
one item (a terminal, an editor file or a plugin's view). Each window has one tab strip, the parent-tab bar of plans
7a-7c, shown from two tabs in the standard look and always under Enterprise; no pane has a strip. Pane windows, Pop
Out Tab, Pop Out Pane, Bring Back and the item drag go; dragging a tab and Move Tab to New Window stay; the commands
and keys follow the design's table; and while a tab holds two or more panes, every pane but the focused one is dimmed.

**Architecture:** The model keeps its types: a `ParentTab` is a tab, a `Group` a pane that now holds at most one tab
(the item, `contributions.Tab`), and `ParentTabs` a window's tabs. `WindowContent.attach` opens anything placed without
a pane as a new tab, so New Tab, a single-instance request, `Terminals.openTab`, the registry's moves and a plugin's
`TabPlacement.ACTIVE_GROUP` all land in one route. `ParentTabBar` takes in `StripSource`, the strips' interface, and is
the window's only strip; `GroupView` is a card area for its one item, dimmed by its workspace. The window manager loses
every pane-window operation and keeps `moveParentTab`, `moveToMainWindow` and the tab drag's tear-out under the name
`moveTabToNewWindow`. Headless tests drive the views, the commands and the drag over plan 5's stand-in frames.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md`](../specs/2026-10-20-jasper-tabs-and-panes-design.md)
(sections 2-6, 10, 12, 13, 14's first plan and 16), which wins over
[`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md) and
amends [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md).
It builds on the executed [plan 7a](2026-10-19-workspace-7a-parent-tabs.md), [plan 7b](2026-10-19-workspace-7b-pop-out.md)
and [plan 7c](2026-10-19-workspace-7c-parent-tab-drag.md), and on plan 6a's executed Tasks 1-5
([plan 6a](2026-10-18-workspace-6a-save-restore.md)). Plans 8b (`2026-10-20-workspace-8b-split-with.md`) and 8c
(`2026-10-20-workspace-8c-moving-panes.md`) build on this plan's text.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, at `2d7233474`. Every anchor below was matched at its stated count, and every file creation
and edit script of Tasks 1-6 replayed in order on a `git archive` copy of `2d7233474` with no failure. Nothing was
compiled or run. Line numbers are `2d7233474`'s (its code is `991e54c1c`'s; only the spec changed), or the earlier
task's where the text is that task's.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan wrote, and quote it as they leave it.

**Hand-offs absorbed:**
- The controller's rulings of 2026-10-20 on this plan's first text, which spec 5 records since `2d7233474`: the
  window's strip keeps the open and close motion the pane strips had, where the look has motion, and a dragged tab
  still lands at once (Rulings 20, vetoing the first text's still strip); an inactive window keeps dimming every
  terminal, as today, with the focused-pane dim on top (Rulings 13 as amended); and both spec issues stand as this plan
  reads them.
- Plan 7c's controller rulings stand: a window whose strip is hidden takes a dragged tab along its top band (7c Rulings
  5), and the main window's empty workspace takes one anywhere over it (7c's final fix wave). Task 1 keeps both and
  their tests.
- Plan 7b's hand-off "the SDK 0.21.0 Javadoc pass rewords `ContentTab.moveToNewWindow`": Task 2 rewrites it for the
  move this plan gives it (Rulings 6), and Task 4 rewords `moveToMainWindow`, `TabPlacement.ACTIVE_GROUP`,
  `Windows.openTab`, `Terminals.openTab` and `split`, `Direction`, `TAB_SELECTED` and `WindowHandle.tabs()`/
  `activeTab()` for tabs and panes; `TabHandle` is 8b's.
- The user's decision of 2026-10-20 (the tabs and panes spec, decision 3): Enterprise always shows the strip, with one
  tab too. It supersedes the one-row change (`b819600c1`) and its `EnterpriseWorkstationMockTest` reading, which Task 4
  turns to the strip (spec 13's last row).
- Plan 6a's Task 5 review findings that were not fixed before the pause (the store's refused write is not logged, and
  the others in its ledger) stay with plan 6a's re-plan; nothing here touches `WorkspaceStore`.

**6a impact** (plan 6a's executed Tasks 1-5; its re-plan follows plans 8a-8c):
- Task 2: `WorkspaceFile` loses pane windows: the `pane` role, `origin`, `NO_ORIGIN`, the `PaneLayout` case of
  `keeping`; `WindowRecord.layout` and `keeping` take `ParentTabs`. `WindowManager.snapshot` follows. Tests:
  `WorkspaceFileTest`'s shared snapshot holds a full window where it held a pane window; `WorkspaceFileReadTest` loses
  `aPaneWindowsUnreadableOriginFallsBackToOneNoWindowHolds`; `WorkspaceSnapshotTest` and `WorkspaceSavingTest` move a
  tab into a full window (`moveToNewWindow`) where they popped one out, and save Move to Main Window where they saved
  Bring Back.
- Task 4: a pane holds one tab (Rulings 2), so a saved group listing two tabs makes its parent tab entry a bad entry,
  dropped alone (a new `WorkspaceFileReadTest` case); the shared snapshot's first pane splits in two; the multi-tab
  cases of `WorkspaceFileReadTest` read one-tab panes. The file's format does not change. `WorkspaceSnapshotTest` and
  `WorkspaceSavingTest` read what opens as a tab of its own (`openTab`, `newTab`).
- Plan 6a's `TerminalPane.restored`/`waiting`, `JasperWindow.Placement`, `WindowPlacement`, `WorkspaceStore` and
  `TestWindows` are untouched.

**Left for plans 8b and 8c** (what they extend; the brief's "say which"):
- 8b: the SDK's `TabPlacement.ACTIVE_GROUP` keeps its name and now opens a new tab (Rulings 4); 8b renames it
  `NEW_TAB` with `HostedUi`'s mapping (Task 4 leaves it at `HostedUi.java`'s `case ACTIVE_GROUP ->
  ...Placement.NEW_TAB`). The testkit's `FakeWorkspace`, the app's contract fixture `TerminalFixture` and the contract's
  placement case keep their one-level model (a new tab at the end of the active group) until 8b moves them with the
  SDK change (Rulings 4). The keys ⌥⌘T and Ctrl+Alt+Shift+T stay free for `move_pane_to_new_tab` (Task 3 pins it).
- 8c: the tab drag's machinery stays whole for the pane drag: `TabDrag`'s press, threshold, Esc, lost-release,
  focus-loss and close handling, its label (`TabDragLabel`) and landing overlay (`DockDropOverlay`), and its window
  reach (`windowsByActivation`, `dropFrame`, `otherWindows`). `WorkspaceDropResolver`'s `Area`, `Drop` (with
  `Centre` and `Edge`), `resolve` and `landing` stay untouched for its edge geometry, though no view calls them after
  Task 1; `Layout.moveToNewGroup` stays, a pane moving beside another in one layout. `Drop.Strip` has no strip to come
  from any more; 8c decides whether it goes. Task 1 removes `Target.Into`, `TabDrag.into`, its landing,
  `WorkspaceView.dropAreas`, `changes` and `drop`, and `GroupView`'s strip and content bounds; 8c adds a pane target
  over whole panes. `TabRegistry.move(Tab, UUID, GroupId, Side)` and `WorkspaceView.moveToNewGroup` stay, with no
  production caller (tests still call them), for 8c's cross-window and in-tab pane moves.

## Spec issues

1. **Spec 6 says two things of a window's only tab and Move Tab to New Window.** Its first list says a window's only
   tab, whose strip is hidden, "moves through its commands: Move Tab to New Window ...; Move to Main Window, for a full
   window's tabs", and its ⋮ list says "main window: Move Tab to New Window, from two tabs; full window: Move Tab to New
   Window, from two tabs, and Move to Main Window". A full window's only tab already is its own window, so only the
   main window's could move; the ⋮ list rules that out. **This plan follows the ⋮ list:** Move Tab to New Window, and
   the tab drag's tear-out, which is the same move, need two tabs in the window (Rulings 6), and a full window's only
   tab moves by Move to Main Window or by a drag onto another window's strip. If the user wants the main window's only
   tab to leave for a window of its own, leaving its empty workspace (plan 7b's Rulings 8), it is one condition in
   `WindowContent.mayMoveTabToNewWindow`. **Ruled** (the controller, 2026-10-20): it needs two tabs in its window.
2. **Spec 15 puts "a setting for the dimming" out of scope, and one exists:** `terminal.dim_inactive_panes`
   (`docs/configuration.md:228`, default 0.3, 0 turns dimming off) already sets how much an inactive terminal pane
   dims. The plan adds no setting and keeps that one as the amount every dimmed pane takes (Rulings 12). **Ruled** (the
   controller, 2026-10-20; spec 5 since `2d7233474`): `terminal.dim_inactive_panes` stays as the amount.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `pop_out_tab`, `pop_out_pane`, `pop_out_parent_tab`, `bring_back`, `new_tab_in_pane`,
  `next_parent_tab`, `previous_parent_tab`, `select_parent_tab_1`-`9` and `close_parent_tab` go with no alias; a saved
  binding naming one is reported as an unknown key, as plan 7a's were. `Origin`, `PaneLayout`, `WindowLayout`,
  `StripSource`, pane windows and the `pane` window role of `workspace.toml` go outright. No shim, no migration, no
  "this was removed" text.
- Motion (AGENTS.md; spec 5): the strip shows and hides at once; its equal tabs keep their open and close motion where
  the look has it (`ParentTabBar.animates()` is the window's `lookAnimates()`, Rulings 20), and nothing else moves: a
  tab dragged, reordered, moved between windows or showing another of its panes takes its place at once, and a dim
  comes and goes at once.
- Sessions and content are never touched: a tab's panes keep their components when it hides, and every move between
  windows goes through the tab registry, one `TAB_MOVED` per tab (plan 7b Rulings 6).
- The SDK stays 0.21.0, unreleased (spec 11): no type, constant or signature changes; Tasks 2 and 4 reword Javadoc only
  (Rulings 4). The testkit and the contract cases do not change (Rulings 4).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): none changes. `layout` stays
  JDK-only and names no other Jasper package; `dock` keeps its one edge, to `layout`.
- The theme-migration ledger `docs/design/theme-engine/migration.csv`: a deleted Java file's row goes with
  `ledger.drop(path, successor)`, a renamed one's with `ledger.rename(old, new, surface)`, and a new file that matches
  the coverage test's pattern gets `ledger.add(...)` with "New in workspace-tabs plan 8a Task N" (plan 4a Rulings 21);
  `ThemeMigrationCoverageTest` checks every row's file and every test a row cites.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- User-facing text says "tab" and "pane" (spec 3): no label, tooltip, keyword or message says "parent tab".
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): views driven directly, drags on synthetic events over plan 5's
  stand-in frames on a pretend screen (`TabDragTestSupport`, `TabDragWindowsTest`), windows through `MovesFixture` and
  `TestWindows`.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan8a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan8a
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan8a/
ls build/plan8a/edits.py build/plan8a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan8a")`. Java text inside a script is in triple-quoted strings; where it holds a
Java backslash, the task says so and writes it as a raw string (Tasks 2 and 4) or as `\\` (Task 5). A file a task
deletes goes inside its script (`pathlib.Path(...).unlink()`), so the replay sees it go.

## Review Focus

1. **The main window's empty workspace and whatever opens there** (New Tab, a plugin's view with `ACTIVE_GROUP`, a
   single-instance request's directory, `Terminals.openTab`, a tab moved or dragged in). It fills the one empty tab, in
   its place, and never leaves an empty tab beside a new one. Task 4:
   `ParentTabsViewTest.whatOpensWithoutAPaneOpensAsATabAndTheEmptyWorkspaceTakesTheFirst`.
2. **A tab whose panes hold a plugin's item with a close guard that refuses.** Close Tab, the strip's ×, a middle
   click and the strip menu's Close Tab ask the guard once for the whole tab, and a refusal closes nothing, terminals
   included. Task 4: `ParentTabBarTest.theStripsCloseAndAMiddleClickCloseAWholeTabAfterItsGuards`; Task 3:
   `TabMenuTest.closeTabAsksTheGuardsFirstAndARefusalClosesNothing`.
3. **The dim at its edges:** a tab with one pane (nothing dims while its window is active), a background window (every
   terminal dims, as today, and a plugin's view as the active pane stays clear), a maximized pane,
   `terminal.dim_inactive_panes = 0`, a plugin's view as the inactive pane, a press on a view that takes no focus, and
   focus moving by a click, Focus Pane Left or a tab switch (the dim follows at once). Task 5: every case of `PaneDimTest`.
4. **A `workspace.toml` whose group lists two tabs** (written before this plan by the branch's store). Its window's
   parent tab drops alone, as any bad entry does, and the original is kept aside as `workspace.toml.bad`. Task 4:
   `WorkspaceFileReadTest.aPaneListingTwoTabsDropsItsParentTabAlone`.
5. **Keys while a plugin's view has the focus:** Next and Previous Tab run before the view's own bindings (a scroll
   pane's Ctrl+PageDown), and Ctrl+Tab, no longer bound, reaches the view or its focus traversal. Task 3:
   `TabShortcutsTest.nextAndPreviousTabRunBeforeAPluginViewsOwnBindingsAndCtrlTabReachesTheView`.

## Verified facts

From `2d7233474` (see Workspace).

1. **Groups stack.** `layout.Group(GroupId, List<TabId>, Optional<TabId>)` (`layout/Group.java:16-27`) holds any
   number of tabs. `Layout.open(TabId, GroupId, int)` (`Layout.java:216-224`) and `move(TabId, GroupId, int)`
   (`:260-274`) stack tabs; `remove` (`:288-304`) keeps a group's other tabs and selects its most recently shown one
   (`tabOrders`, `tabOrder` `:689-697`). `moveToNewGroup` (`:335-342`) moves a tab beside a group; `origin` and `graft`
   (`:397-443`) serve pane windows only.
2. **Everything that opens without a group stacks.** `WindowContent.attach(Tab, boolean, GroupId, Side, boolean)`
   (`WindowContent.java:1186-1218`) opens a tab in a new parent tab only when asked (`parentTab`), lands a tab a
   `receive` brings in its parent tab (`ParentTabsView.land`, `ParentTabsView.java:149-153`, which stacks into an
   existing one with `view.open`), and otherwise stacks in the shown parent tab's active group
   (`workspace().open(tab)`, `WorkspaceView.java:89-93`). Its callers: the registry's `attach` (`open`'s placements but
   the splits, `move`, `mainOpened`, `moveToMainWindow`), `openTerminal` (`:1739-1756`) for `newTab(Path)` and every
   `openTab` (single-instance requests, `JasperApplication.java:963-966`; `Terminals.openTab`,
   `WindowTerminals.java:69-76`), and `newParentTab` (`:1677-1680`) for New Tab alone.
3. **The registry's placements.** `TabRegistry.Placement` (`contributions/TabRegistry.java:46-55`) is
   `ACTIVE_GROUP`, `SPLIT_RIGHT`, `SPLIT_DOWN`, `NEW_WINDOW`; `open` (`:217-266`) attaches `ACTIVE_GROUP` and
   `NEW_WINDOW` through `host.attach()` and the splits through `attachBeside`. `HostedUi` maps the SDK's `TabPlacement`
   onto it one to one (`plugins/HostedUi.java:662-667`).
4. **The contract never reaches a window.** `AppContractTest`'s workspace host attaches content to the scripted
   `TerminalFixture` (`plugins/AppContractTest.java:153-159`), whose groups stack as the testkit's `FakeWorkspace`
   does (`jasper-sdk-testkit/.../FakeWorkspace.java:136-147`). The contract case
   `aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow` (`PluginContractTest.java:1652-1710`) pins that
   stacking on both runtimes, through the registry's unchanged routing.
5. **Strips.** `GroupView` (`workspace/GroupView.java:29-169`) is a `StripSource` with a strip over its cards; its
   strip shows by `WorkspaceChrome.showsStrip(count)` (`WorkspaceChrome.java:119-124`, from `WorkspaceView.render`
   `:276-278`). `ParentTabBar` (`ParentTabBar.java:20-93`) is the other `StripSource` (`StripSource.java:14`, sealed,
   permits the two), shown by `showsParentTabBar(count)` (`WorkspaceChrome.java:131-136`): from two under the equal tabs,
   never under the classic tabs (`b819600c1`), and never in a pane window (`ParentTabBar.java:44`). `WindowTabs` and
   `ClassicTabs` take a `StripSource` (`WindowTabs.java:31-46`, `ClassicTabs.java:47-58`); only `ClassicTabs` has a
   "+" (`:315-324`), which runs the source's `newTab`.
6. **Pane windows.** `WindowContent` keeps an `origin` (`:88-89`) that `paneWindow` sets (`:1301-1306`), and
   `windowLayout()` (`:1290-1292`) returns a `PaneLayout` for it; `WindowManager` has `popOutTab` (`:258-278`),
   `popOutPane` (`:290-320`), `bringBack` (`:419-438`), `holderOf` (`:486-494`), `leaving` (`:501-506`),
   `moveToNewChild` (`:509-519`) and an `origin` parameter on `openChild` (`:610-621`). The registry's
   `Windows.newWindow` is `this::popOutTab` (`:125-127`), so `ContentTab.moveToNewWindow()` pops a tab out. Commands
   `POP_OUT_TAB`, `POP_OUT_PANE`, `POP_OUT_PARENT_TAB`, `BRING_BACK` (`commands/ActionId.java:18-21`).
7. **The item drag.** `TabDrag.pressed(GroupView, Tab, MouseEvent)` (`TabDrag.java:169-173`), `Target.Into` (`:80`),
   `into` (`:337-343`), the `Into` landing (`:439-445`) and drop (`:516-529`), and the tab tear-out (`:531`, to
   `popOutTab(tab, bounds, cursor)`) serve the item drag alone; `WorkspaceView.dropAreas`, `changes` and `drop`
   (`WorkspaceView.java:149-196`) are their only callers' targets. The parent-tab drag (`pressed(ParentTabBar, ...)`
   `:180-188`, `onBar` `:352-369`, `Target.Bar`, its tear-out through `popOutParentTab`) is separate.
8. **Commands.** `ActionId` (`:11-53`): `NEW_TAB` ⌘T / Ctrl+Shift+T, `NEW_TAB_IN_PANE`, `CLOSE_TAB` ⌘W / Ctrl+Shift+W
   (the active item), `CLOSE_PANE` ⌥⌘W / Ctrl+Alt+Shift+W (every item of the active pane), `CLOSE_PARENT_TAB`,
   `NEXT_PARENT_TAB` ⌘⇧] / Ctrl+PageDown, `PREVIOUS_PARENT_TAB` ⌘⇧[ / Ctrl+PageUp, `SELECT_PARENT_TAB_1`-`9` ⌘1-9 /
   Ctrl+1-9, `NEXT_TAB` and `PREVIOUS_TAB` Ctrl+Tab and Ctrl+Shift+Tab on both, `SELECT_TAB_1`-`9` none. The window's
   key dispatcher runs Next and Previous Parent Tab before the focus owner's bindings (`WindowContent.java:730-756`,
   plan 7a Rulings 14). `KeyBindings.defaults` refuses two built-in defaults on one stroke in conflicting contexts
   (`config/KeyBindings.java:104-112`), and `withExtensions` drops a plugin default a built-in holds (`:177-186`).
9. **The collision check, every default stroke on each platform.** The moved strokes (⌘W, ⌥⌘W, ⌘⇧], ⌘⇧[, ⌘1-9;
   Ctrl+Shift+W, Ctrl+Alt+Shift+W, Ctrl+PageDown, Ctrl+PageUp, Ctrl+1-9) are held today by one built-in each and by no
   bundled plugin; the bundled plugins' defaults are ⌘O, ⌘R, ⌘P / Ctrl+Alt+Shift+P, ⌘⇧O, ⌘⇧H / Ctrl+Shift+H, ⌘⌥1-3 /
   Alt+1-3, Alt+Left/Right/Up (SFTP panel), ⌘⌥J, ⌘⌥G, ⌘J, ⌘⇧\, ⌘\ and ⌘⇧L (`plugins/*/src/main`: `EditorPlugin.java:107`,
   `HistoryPlugin.java:40`, `IndexerPlugin.java:144`, `RemotePlugin.java:220`, `:283`, `:286`, `:290`,
   `SftpUi.java:77-79`, `TransferUi.java:44`, `SamplePlugin.java:84`, `:212`, `SnippetsPlugin.java:47`,
   `VaultPlugin.java:129-135`), off macOS by `Extension.defaultFor`'s rule (`KeyBindings.java:52-60`, `:334-337`). None
   of them is ⌥⌘T or Ctrl+Alt+Shift+T, which no built-in holds either: plan 8b's Move Pane to New Tab default is free.
   Ctrl+Tab and Ctrl+Shift+Tab go free.
10. **Dimming.** `WindowContent.update` sets every terminal tab `setActive(active && tab == selected)`
    (`:1980-1997`); `TerminalPane.setActive` (`TerminalPane.java:561-565`) sets the view's inactive dim to the
    configured amount (`terminal.dim_inactive_panes`, `setConfiguredDim` `:576-579`) unless it is the window's active
    tab in an active window, and the same `active` flag decides whether a finished launch takes the focus (`:335`). So
    every terminal but the window's active one dims: in an active window the other terminal panes of a split tab, in a
    background window every terminal; nothing dims a plugin's view. `TerminalView.setInactiveDim`
    (`jasper-terminal/.../TerminalView.java:389-395`) paints the dim in the palette's background colour.
11. **Close guards.** `WindowContent.closeTabs` (`:1891-1897`) asks the plugins' guards once for a batch and closes
    terminals without asking; `closeParentTab` (`:1702`) closes a parent tab through it, as the bar's × and middle
    click do (`ParentTabBar.java:71`).
12. **The window menu.** `WindowContent.windowMenuItems` (`:1526-1533`) lists Pop Out Pane and Pop Out Parent Tab (main),
    those with Move to Main Window (full), Bring Back and Pop Out Pane (pane window); a terminal's context menu adds Pop
    Out Tab and the same entries (`WindowChrome.java:594-603`); `tabMenu` (`:1475-1500`) and `parentTabMenu`
    (`:1507-1517`) offer them for what they were opened on.
13. **The ledger.** Rows exist for `GroupView`, `ParentTabBar`, `StripSource`, `TabDrag`, `WindowTabs`, `ClassicTabs`,
    `TabStrip`, `WorkspaceView`, `ParentTabsView` and their tests, for `PopOutTest`, `PopOutCommandsTest`,
    `BringBackTest`, `WorkspaceDropTest`, `ParentTabCommandsTest`, `MovesFixture` (citing `PopOutTest`), and none for
    `Origin`, `PaneLayout`, `WindowLayout` or `OriginTest` (`docs/design/theme-engine/migration.csv`).

## Rulings

Record them in `docs/STATUS.md` (Task 6). "What — why".

1. **Code names stay.** `ParentTab`, `ParentTabs`, `ParentTabId`, `ParentTabsView` and `ParentTabBar` are a tab, a
   window's tabs, their view and the window's tab strip; `Group`, `GroupId` and `GroupView` are a pane; the
   contributions' `Tab` is an item; their Javadoc says so. Methods keep their names too (`closeTab(Tab)` closes an item,
   `closeParentTab` a tab). Reason: renaming `ParentTab` to `Tab` collides with `contributions.Tab`, the item the SDK
   calls `TabHandle` (spec 3), used across the app, the plugins' host code and plans 6a, 6b, 8b and 8c; a rename wave
   would bury the behaviour change.
2. **A pane's group keeps `Group(id, tabs, selected)` and holds at most one tab** (exactly one but the empty
   workspace's). The layout loses every stacking operation; `workspace.toml` keeps its node format, and a saved group
   listing two tabs is a bad entry. Reason: the smallest model change that makes stacking impossible; plan 6a's
   re-plan owns the file's shape (spec 12).
3. **Whatever opens without a pane opens as a new tab** at the end of its window's strip, shown; the main window's
   empty workspace takes it in its place. That is `WindowContent.attach` with no group, so New Tab, `newTab(Path)`
   (single-instance requests, `Terminals.openTab`, the window's first tab), the registry's `open`, `move`,
   `mainOpened` and `moveToMainWindow` (`ContentTab.moveToMainWindow()`) all take it; `newParentTab` goes into `newTab`.
   Tabs moving in with a `receive` land in their tab beside its active pane, then take its layout. Reason: spec 13 and
   spec 2's two levels; one route keeps them from drifting apart.
4. **The SDK's names stay and its Javadoc follows the app.** The app's `TabRegistry.Placement.ACTIVE_GROUP` becomes
   `NEW_TAB`; the SDK's `TabPlacement.ACTIVE_GROUP` maps to it and its Javadoc says it opens a new tab, as do
   `Windows.openTab`, `Terminals.openTab` and `ContentTab`'s moves (Tasks 2 and 4). The testkit's fake, the app's
   `TerminalFixture` and the contract cases keep their one-level model, which the contract reaches through the
   registry's routing only (Verified facts 4); plan 8b renames the constant and moves them together. Reason: spec 11
   makes `TabPlacement` 8b's (the brief); the Javadoc must not describe behaviour the app no longer has.
5. **Pane windows go whole:** `Origin`, `PaneLayout`, `WindowLayout`, `Layout.origin` and `graft`, Pop Out Tab, Pop Out
   Pane, Bring Back, their commands, the `pane` role in `workspace.toml` and the window menu's pane-window entries.
   `windowLayout()` returns `ParentTabs`. Reason: spec 4.
6. **`ContentTab.moveToNewWindow()` opens a full window holding the item in a tab of its own**, where the platform puts
   a window; a child window's only item, a pinned item and one being closed stay. **Move Tab to New Window**
   (`move_tab_to_new_window`, plan 7b's Pop Out Parent Tab, `WindowManager.moveTabToNewWindow`) moves the shown tab
   whole into a new full window and needs two tabs in its window, main or full; the tab drag's tear-out is the same
   move with the same rule. Reason: spec 13 and spec 6's ⋮ list (Spec issues 1).
7. **The ⋮ menu lists Move Tab to New Window in every window and Move to Main Window in a full window,** each enabled
   while its action is, so the main window's ⋮ with one tab shows its one entry disabled. A terminal's context menu ends
   with the same entries. Reason: spec 6; an empty menu would read as broken, and plan 7b's Rulings 15 put the
   window's entries in the context menu.
8. **The strip's entry menu:** Rename Tab when its tab's active pane holds a terminal, Move Tab to New Window, a rule,
   Close Tab, then the shown item's own commands. Move to Main Window moves the whole window, so it stays in the ⋮ menu
   and the context menu and is not on the entry's own menu (the controller's ruling of 2026-10-20). The pane strip's
   Close Others and its item-only menu go. Reason: what the pane strips' menu offered and still applies to a tab moves
   to the one strip.
9. **`ParentTabBar` takes in `StripSource`;** `WindowTabs` and `ClassicTabs` take a `ParentTabBar`. Reason: AGENTS.md,
   no interface without two real implementations; `GroupView` was the other.
10. **The strip shows from two tabs under the equal tabs and always under the classic tabs**, in every window, the
    main window's empty workspace showing the classic strip with its "+" alone (`WorkspaceChrome.showsTabStrip`).
    Reason: spec 5; plan 7a's Rulings 17 had the same shape.
11. **The equal tabs keep having no "+";** the classic strip's "+" is New Tab. The toolbar's New Tab and ⌘T open a
    tab in the standard look, as today. Reason: spec 5 names "the strip's '+'", which only the classic strip has.
12. **A pane dims while its tab holds two or more panes and it is not the tab's active pane,** by
    `terminal.dim_inactive_panes`: a terminal through its view's own dim, which today's rule already gives it (Rulings
    13), any other item through the pane's overlay in the workspace's surface colour (`GroupView.dim`). Reason: spec 5
    dims panes, not terminals; the existing setting sets the amount (Spec issues 2, ruled).
13. **An inactive window keeps dimming its terminals, as today, and the focused-pane dim comes on top** (the
    controller's ruling of 2026-10-20, amending this plan's first text; spec 5 since `2d7233474`). Terminals keep
    today's rule unchanged (`WindowContent.update`, `TerminalPane.setActive`): every terminal but its window's active
    item dims, so in an active window a split tab's other terminal panes dim and a tab of one pane shows clear, and an
    inactive window dims every terminal it shows. A plugin's view dims under its pane's overlay while it is an inactive
    pane of a split tab, its window active or not, and shows clear as the active pane. Reason: spec 5's "an inactive
    window dims its terminals as it does today".
14. **Defaults (spec 10):** `close_pane` ⌘W / Ctrl+Shift+W, `close_tab` ⌥⌘W / Ctrl+Alt+Shift+W, `next_tab` ⌘⇧] /
    Ctrl+PageDown, `previous_tab` ⌘⇧[ / Ctrl+PageUp, `select_tab_1`-`9` ⌘1-9 / Ctrl+1-9, `move_tab_to_new_window` and
    `move_to_main_window` none; `new_tab`, `split_right`, `split_down`, `close_window`, `maximize_pane` and
    `focus_pane_*` as they are. Ctrl+Tab and Ctrl+Shift+Tab have no default. The collision check holds on both
    platforms (Verified facts 9). Reason: spec 10; each moved stroke changes id, not holder.
15. **`close_pane` closes the focused pane's item, a plugin's after its guard, and the tab with its last pane;
    `close_tab` closes every item of the shown tab as one gesture after their guards.** Next and Previous Tab, and
    Select Tab 1-9, act on the window's tabs. Reason: spec 10's table.
16. **Next and Previous Tab run from the window's key dispatcher before the focus owner's own bindings**, as Next and
    Previous Parent Tab did (plan 7a Rulings 14). Reason: a scroll pane would otherwise keep Ctrl+PageUp/PageDown.
17. **Menus:** File reads New Tab, New Terminal Window, Open File…, Close Pane, Close Tab, Close Window, then Settings,
    Reload Config and Quit; the Tab menu reads the split, focus and prompt items, a rule, Rename Tab, a rule, Next Tab,
    Previous Tab and Select Tab 1-9; a terminal's context menu reads Copy, Paste, Find, Clear Terminal, Split Right,
    Split Down, Maximize Pane, Close Pane, a rule, then the ⋮ entries. Reason: spec 10's names, as plan 7a Rulings 15
    ordered the menus; plan 8b adds its Split lists and Move Pane to New Tab to the Tab menu and the context menus.
18. **The item drag goes whole** (spec 4): `TabDrag` carries tabs only, from the strip; a pane takes no drop and starts
    no drag until plan 8c. A tab dragged over a pane, its own place or a window's chrome lands nothing. Reason: spec 6
    lists a tab's drops (its strip, another window's strip, outside every window); spec 7's pane moves are 8c's.
19. **Hidden strip, empty workspace:** plan 7c's Rulings 5 and its final fix wave stand: a window whose strip is hidden
    takes a dragged tab along the band at its top, at its end, and the main window's empty workspace takes one anywhere
    over it, in its place. Reason: the controller's rulings for 8a.
20. **The window's strip keeps the open and close motion** (the controller's ruling of 2026-10-20, vetoing this plan's
    first text; spec 5 since `2d7233474`): the equal tabs (`WindowTabs`) run the motion the pane strips ran, now over
    the window's tabs, where the look has motion (`ParentTabBar.animates()` is `lookAnimates()`), with
    `WindowTabMotionTest` (on the window's strip), `WindowTabMotionPreview` and its preview task. An entry is its tab's:
    it keeps its place while it shows another of the tab's panes (a split, a focus change, a pane closing). A tab that
    arrives or leaves by a move (its item `moving()`, or leaving unclosed), a reorder, and the strip showing or hiding
    take their places at once, so a dragged tab lands at once and the entries never slide during a drag (plan 7c). The
    classic tabs never move. Reason: the README's "Opening and closing tabs animate", `ui.animations.enabled` on by
    default, and the user never asked to remove that motion.
21. **A left press on a pane's item makes its pane the active one,** a view that takes no focus too
    (`WindowContent.paneClicks`, an AWT mouse listener each window adds and removes with itself). Reason: with the
    strips gone, a press on a plugin's view that takes no focus changed nothing and left its pane dimmed (the review's
    I1, ruled by the controller).

## File structure

- `dev.jasper.app.layout`: `Group`, `Layout`, `ParentTabs`, `package-info` (Tasks 2, 4); `Origin`, `PaneLayout`,
  `WindowLayout` go (Task 2).
- `dev.jasper.app.persistence`: `WorkspaceFile` (Tasks 2, 4).
- `dev.jasper.app.contributions`: `TabRegistry`, `Tab`, `WorkspaceHost` (Tasks 2, 4).
- `dev.jasper.app.commands`: `ActionId` (Tasks 2, 3).
- `dev.jasper.app.shortcuthelp`: `ShortcutCatalog` (Tasks 2, 3).
- `dev.jasper.app.plugins`: `HostedUi` (Task 4).
- `dev.jasper.app.workspace`:
  - Task 1: `TabDrag` (whole), `GroupView`, `WorkspaceView`, `TabStrip`, `WindowContent`, `WindowManager` (Javadoc);
  - Task 2: `WindowContent`, `WindowManager`, `WorkspaceActions`, `WindowCommands`, `WindowChrome`, `ParentTabBar`,
    `TabDrag`;
  - Task 3: `WorkspaceActions`, `WindowCommands`, `WindowChrome`, `WindowContent`, `ParentTabBar`, `GroupView`;
  - Task 4: `GroupView` (whole), `WorkspaceView`, `ParentTabsView`, `ParentTabBar`, `WindowTabs`, `ClassicTabs`,
    `TabStrip`, `WorkspaceChrome`, `WindowContent`, `WorkspaceActions`, `TabDrag`; `StripSource` goes;
  - Task 5: `GroupView`, `WorkspaceView`, `WindowContent`, `WorkspaceConfiguration`.
- SDK Javadoc only: `jasper-sdk/.../ui/ContentTab.java` (Tasks 2, 4), `ui/TabPlacement.java`, `ui/Windows.java`,
  `terminal/Terminals.java`, `terminal/WindowHandle.java`, `terminal/Direction.java`, `terminal/TerminalEvents.java`
  (Task 4).
- Tests: each task's Files list.
- Documents (Task 6): `README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md` where they name what changed,
  `docs/STATUS.md`, this plan's status banner.

## Before you start

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
git log --oneline -1                                                                                         # 2d7233474 Docs: tabs and panes spec, dimming and the strip's motion kept as today (or a later commit)
grep -c 'void pressed(GroupView group, Tab tab, MouseEvent event) {' jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java  # 1
grep -c 'public Optional<ChildWindow> popOutTab(Tab tab) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'NEW_TAB_IN_PANE("new_tab_in_pane", "New Tab in Pane", "none", "none"),' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java  # 1
grep -c 'sealed interface StripSource permits GroupView, ParentTabBar {' jasper-app/src/main/java/dev/jasper/app/workspace/StripSource.java  # 1
grep -c 'case CLASSIC -> false;' jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java      # 1
grep -c 'if (tab.content() instanceof TerminalContent terminal) terminal.setActive(active && tab == selected);' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above them. Create the two helper scripts (Global Constraints).

---

### Task 1: The item drag goes; a tab drags from its strip alone

Spec 4 and 6: the item drag goes (plan 5's strip, centre and edge drops and plan 7c's item tear-out), and plan 7c's
parent-tab drag stays as the one tab drag (Rulings 18, 19). A pane's strip starts no drag, and `WorkspaceView` loses its
drop areas and drops. `TabDrag` is rewritten whole, as most of it changes; the three drag test files are rewritten
whole on the strip, and `WorkspaceDropTest` goes with what it tested.

**Files:**
- Replace: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java` (class Javadoc `:21-28`, `pressed`,
  `dragged`, `released` `:99-110`, `stripBounds` and `tabBounds` Javadoc `:122-135`),
  `WorkspaceView.java` (imports `:6-7`, `:18`; `dropAreas`, `changes`, `drop` `:144-196`), `TabStrip.java` (class
  Javadoc `:9-16`), `WindowContent.java` (the `tabDrag` field's Javadoc `:130-136`), `WindowManager.java`
  (`popOutTab(Tab, Rectangle, Point)`'s Javadoc `:265-267`)
- Replace: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java`, `TabDragTest.java`,
  `TabDragWindowsTest.java`
- Delete: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceDropTest.java` (and its ledger row)
- Test: `WindowTabsTest.java` (`middleClickAndDragUseTheSameSelectionAndOrderModel` drags on the bar)

**Interfaces:**
- Consumes: plan 7c's `ParentTabBar.pressed`, `WindowManager.moveParentTab`, `popOutParentTab(WindowContent,
  ParentTabId, Rectangle, Point)`, `WindowContent.mayPopOutParentTab`, `reorderParentTab`, `manager()`;
  `WorkspaceDropResolver.windowAt`, `place`, `marker`.
- Produces:
  - `TabDrag.pressed(ParentTabBar bar, Tab tab, MouseEvent event)`, its only press; `TabDrag.Target` permits `Bar(WindowContent
    window, int index)` and `TearOut(Rectangle bounds, Point cursor)`; `dragged()`, `draggedParentTab()`, `target()`,
    `overlay()`, `label()`, `cancel()`, `cancelFrom(WindowContent)`, `otherWindows`, `dropFrame(...)` as before;
  - `GroupView.pressed` cancels a dock drag and starts nothing;
  - `TabDragTestSupport`: `frame`, `place`, `relayout`, `onTitleBar`, `onScreen`, `middle`, `inside`,
    `pane(WindowContent, GroupId)`, `onEntry(WindowContent, Tab, double)`, `marker(WindowContent, Tab, int)`,
    `entry(WindowContent, Tab)`, `mouse`, `start(WindowContent, Tab, Point...)`, `drop(WindowContent, Tab, Point...)`,
    `motionListeners()`, `broadcast(MouseEvent)`, `TITLE`; "entry" is the strip's entry of the tab whose active pane
    holds the given item.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Headless tab drags (workspace tabs spec 12; tabs and panes spec 6): windows in stand-in frames on a pretend screen,
 * and the mouse events Swing sends a drag, in that screen's coordinates. A stand-in frame is a parentless panel at a
 * place on the pretend screen: a root pane holding the window's content, under a title bar that is the panel's top
 * inset, as a native frame's insets hold its title bar. Screen conversions, the drag's window bounds, the frame's
 * decorations and the overlay's layered pane then work without a display. A tab drags from its entry in its window's
 * strip, which shows the item of the tab's active pane: "the entry of an item" is that entry.
 */
final class TabDragTestSupport {
    /** The stand-in title bar's height: the frame's top inset. */
    static final int TITLE = 28;

    private TabDragTestSupport() { }

    /** {@code window} in a stand-in frame at {@code x}, {@code y} on the pretend screen, {@code width} by {@code height}, laid out. */
    static JPanel frame(WindowContent window, int x, int y, int width, int height) {
        var root = new JRootPane();
        root.setContentPane(window);
        var frame = new JPanel(new BorderLayout());
        frame.setBorder(BorderFactory.createEmptyBorder(TITLE, 0, 0, 0));
        frame.add(root, BorderLayout.CENTER);
        place(frame, x, y, width, height);
        return frame;
    }

    /** Moves and sizes a stand-in frame, then lays it out. */
    static void place(JPanel frame, int x, int y, int width, int height) {
        frame.setBounds(x, y, width, height);
        relayout(frame);
    }

    /** Lays a stand-in frame out again, as Swing would after a drop changed its strip. */
    static void relayout(JPanel frame) {
        LayoutTestSupport.layoutTree(frame);
        LayoutTestSupport.layoutTree(frame);
    }

    /** The middle of a stand-in frame's title bar, on the pretend screen. */
    static Point onTitleBar(JPanel frame) { return new Point(frame.getX() + frame.getWidth() / 2, frame.getY() + TITLE / 2); }

    /** {@code bounds}, in {@code component}'s coordinates, on the pretend screen. */
    static Rectangle onScreen(Component component, Rectangle bounds) {
        Point origin = bounds.getLocation();
        SwingUtilities.convertPointToScreen(origin, component);
        return new Rectangle(origin, bounds.getSize());
    }

    /** The middle of {@code component}, on the pretend screen. */
    static Point middle(Component component) {
        Rectangle bounds = onScreen(component, new Rectangle(component.getSize()));
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /** A point at fractions of {@code bounds}' width and height, from its top left. */
    static Point inside(Rectangle bounds, double across, double down) {
        return new Point(bounds.x + (int) (bounds.width * across), bounds.y + (int) (bounds.height * down));
    }

    /** The pane {@code group} of {@code window}'s shown tab, on the pretend screen. */
    static Rectangle pane(WindowContent window, GroupId group) {
        GroupView view = window.workspace().groupView(group);
        return onScreen(view, new Rectangle(view.getSize()));
    }

    /** A point in the strip's entry of {@code tab}, {@code across} of its width from its left, on the pretend screen. */
    static Point onEntry(WindowContent window, Tab tab, double across) {
        TabStrip strip = window.parentTabBar();
        Rectangle entry = strip.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return inside(onScreen(strip.component(), entry), across, 0.5);
    }

    /**
     * The landing of a tab dropped at {@code index} of {@code window}'s shown strip, in its layered pane, as the drag
     * shows it: {@code WorkspaceDropResolver.marker} over the strip and its entries but {@code dragged}'s own, in its
     * parent-tabs view's coordinates, {@code TAB_INDICATOR} wide.
     */
    static Rectangle marker(WindowContent window, Tab dragged, int index) {
        TabStrip strip = window.parentTabBar();
        ParentTabsView parents = window.parentTabs();
        List<Tab> listed = parents.bar().tabs();
        List<Rectangle> bounds = strip.tabBounds();
        var entries = new ArrayList<Rectangle>();
        for (int i = 0; i < listed.size(); i++)
            if (listed.get(i) != dragged) entries.add(SwingUtilities.convertRectangle(strip.component(), bounds.get(i), parents));
        int width = NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR);
        Rectangle marker = WorkspaceDropResolver.marker(strip.component().getBounds(), entries, index, width);
        return SwingUtilities.convertRectangle(parents, marker, SwingUtilities.getRootPane(window).getLayeredPane());
    }

    /** The component a press on the strip's entry of {@code tab} reaches: the equal tabs' title button, or a classic tab. */
    static Component entry(WindowContent window, Tab tab) {
        TabStrip strip = window.parentTabBar();
        Rectangle bounds = strip.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return SwingUtilities.getDeepestComponentAt(strip.component(), (int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /** A left-button event at {@code screen}, sent to {@code source} as Swing sends a drag's events to the pressed component. */
    static void mouse(Component source, int id, Point screen) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }

    /**
     * Presses in the middle of the strip's entry of {@code tab}, drags 12 px down (past the 5 px threshold at any scale up
     * to 2) and on to each point, without releasing.
     *
     * @return the pressed component, which takes the drag's later events
     */
    static Component start(WindowContent window, Tab tab, Point... path) {
        Component source = entry(window, tab);
        Point press = middle(source);
        mouse(source, MouseEvent.MOUSE_PRESSED, press);
        mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12));
        for (Point point : path) mouse(source, MouseEvent.MOUSE_DRAGGED, point);
        return source;
    }

    /** As {@link #start}, then releases at the last point. */
    static void drop(WindowContent window, Tab tab, Point... path) {
        mouse(start(window, tab, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1]);
    }

    /** How many listeners take every mouse motion event, as a running drag's lost-release listener does. */
    static int motionListeners() {
        return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK).length;
    }

    /** Hands {@code event} to every listener of mouse motion events, as AWT does before any component sees it. */
    static void broadcast(MouseEvent event) {
        for (var listener : Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK)) listener.eventDispatched(event);
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTest.java` with:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12 and tabs and panes spec 6: the tab drag within one window, driven by synthetic mouse events on
 * its strip. The window holds three tabs, "first", "second" and "third", one terminal each, the first showing.
 */
class TabDragTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    /** Each tab's terminal, and the tabs. */
    private Tab a, b, c;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("second");
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("third");
            a = owner.tabs().get(0);
            b = owner.tabs().get(1);
            c = owner.tabs().get(2);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(b).orElseThrow();
            third = owner.parentTabOf(c).orElseThrow();
            owner.selectParentTab(first);
            owner.update();
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A drag that outlived its test would keep its Esc dispatcher and its listener into the next test of this JVM. */
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

    /** The window's tabs, in strip order. */
    private List<ParentTabId> ids() { return owner.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList(); }

    /** {@code title}'s entry's left edge in {@code strip}. */
    private static int left(WindowTabs strip, String title) {
        return SwingUtilities.convertPoint(WindowTabsTest.named(strip, "select:" + title), 0, 0, strip).x;
    }

    /** A right-button event at {@code screen}, sent to {@code source} as Swing sends it to the pressed component. */
    private static void right(Component source, int id, Point screen) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON3_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }

    @Test void aPressThatMovesNoFartherThanTheThresholdIsAClick() throws Exception {
        edt(() -> {
            owner.selectParentTab(third);
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabDrag().active()).as("4.2 px is within the threshold").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x + 3, press.y + 3));
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(owner.activeParentTab()).as("the press showed it").isEqualTo(first);
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
        });
    }

    /**
     * Workspace plan 7c Rulings 1: only a left press on an entry drags its tab. The strip hands the drag no other press,
     * and a press of another button handed to it is no drag's press, whatever moves after it.
     */
    @Test void aPressOfAnotherButtonStartsNoDrag() throws Exception {
        edt(() -> {
            Component source = entry(owner, a);
            Point press = middle(source), past = onEntry(owner, c, 0.75);
            right(source, MouseEvent.MOUSE_PRESSED, press);
            right(source, MouseEvent.MOUSE_DRAGGED, past);
            assertThat(owner.tabDrag().active()).as("through the strip").isFalse();
            right(source, MouseEvent.MOUSE_RELEASED, past);
            Point at = new Point(press);
            SwingUtilities.convertPointFromScreen(at, source);
            owner.tabDrag().pressed(owner.parentTabs().bar(), a, new MouseEvent(source, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK, at.x, at.y, 1, false, MouseEvent.BUTTON3));
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12));
            mouse(source, MouseEvent.MOUSE_DRAGGED, past);
            assertThat(owner.tabDrag().active()).as("handed to the drag").isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, past);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /**
     * Tabs and panes spec 6 and workspace plan 7c Rulings 1 to 3: a tab dragged along its strip takes its new place on the
     * release, its entries there at once though motion is on, and keeps its panes and the one that shows.
     */
    @Test void aTabDraggedAlongItsStripTakesItsNewPlaceAtOnce() throws Exception {
        edt(() -> {
            owner.split(Side.RIGHT);
            Tab beside = owner.selectedTab();
            owner.selectTab(a);
            owner.setAnimations(AnimationSettings.defaults());
            relayout(frame);
            var strip = (WindowTabs) owner.parentTabBar();
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.active()).isTrue();
            assertThat(drag.dragged()).as("the item its entry shows").isSameAs(a);
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).contains(new TabDrag.Target.Bar(owner, 2));
            assertThat(drag.label().text()).isEqualTo("first");
            assertThat(drag.label().location()).as("the label follows the cursor")
                .isEqualTo(new Point(pastThird.x + UIScale.scale(TabDrag.LABEL_OFFSET), pastThird.y + UIScale.scale(TabDrag.LABEL_OFFSET)));
            assertThat(drag.overlay().getParent()).isSameAs(SwingUtilities.getRootPane(owner).getLayeredPane());
            assertThat(drag.overlay().placeholder()).as("a bar in the strip, after the third entry").isEqualTo(marker(owner, a, 2));
            assertThat(drag.overlay().highlight()).isNull();
            assertThat(ids()).as("nothing moves before the release").containsExactly(first, second, third);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(second, third, first);
            assertThat(Arrays.stream(owner.parentTabs().getComponents()).filter(WorkspaceView.class::isInstance)
                .map(view -> ((WorkspaceView) view).id()).toList()).as("the views follow the strip").containsExactly(second, third, first);
            assertThat(owner.activeParentTab()).as("the press showed it, and it stays shown").isEqualTo(first);
            assertThat(left(strip, "second")).isLessThan(left(strip, "third"));
            assertThat(left(strip, "third")).as("the entries are in their new places at once").isLessThan(left(strip, "first"));
            assertThat(strip.motionRunning()).as("and never slide").isFalse();
            assertThat(owner.parentTabs().view(first).orElseThrow().tabs()).as("its panes stay in it").containsExactly(a, beside);
            assertThat(drag.active()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        });
    }

    /** Workspace plan 7c Rulings 2 and this plan's Rulings 18: its own place and a pane take no tab. */
    @Test void aTabDropsOnlyAlongItsStripAndNotWhereItIs() throws Exception {
        edt(() -> {
            Component source = start(owner, a, onEntry(owner, a, 0.25));
            TabDrag drag = owner.tabDrag();
            ParentTabs pressed = owner.parentTabs().model();
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).as("its own place in the strip").isEmpty();
            assertThat(drag.overlay().getParent()).as("no landing shows").isNull();
            Point inPane = inside(pane(owner, owner.workspace().model().activeGroup()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, inPane);
            assertThat(drag.target()).as("a pane").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, inPane);
            assertThat(owner.parentTabs().model()).as("nothing changes").isEqualTo(pressed);
        });
    }

    /** Workspace plan 7c Rulings 1: the item a tab's entry shows closing ends its drag clean. */
    @Test void aTabsDragEndsWhenTheItemItsEntryShowsCloses() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            assertThat(owner.tabDrag().target()).isPresent();
            owner.closeTab(a);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).as("nothing lands").containsExactly(second, third);
        });
    }

    /** Workspace plan 7c Rulings 1: the tab's entry showing another of its panes ends its drag. */
    @Test void aTabsDragEndsWhenItsEntryShowsAnotherPane() throws Exception {
        edt(() -> {
            owner.split(Side.RIGHT);
            Tab beside = owner.selectedTab();
            owner.selectTab(a);
            relayout(frame);
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.Bar(owner, 2));
            owner.selectTab(beside);
            assertThat(owner.tabDrag().active()).as("its entry shows the other pane's item now").isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /** Swing can lose a dock drag's release too: a press on an entry means it never came, so the dock drag ends there. */
    @Test void aPressOnAnEntryEndsADockDragWhoseReleaseWasLost() throws Exception {
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("sessions"));
            relayout(frame);
            try {
                AbstractButton sessions = owner.leftStripe().buttons().getFirst();
                Point from = middle(sessions);
                mouse(sessions, MouseEvent.MOUSE_PRESSED, from);
                mouse(sessions, MouseEvent.MOUSE_DRAGGED, new Point(from.x, from.y + UIScale.scale(owner.area().dragThreshold()) + 2));
                assertThat(owner.dockDrag().active()).as("past the dock's threshold").isTrue();
                Component entry = entry(owner, a);
                mouse(entry, MouseEvent.MOUSE_PRESSED, middle(entry));
                assertThat(owner.dockDrag().active()).as("the press on an entry ends it").isFalse();
                assertThat(owner.dockDrag().overlay().getParent()).isNull();
                mouse(entry, MouseEvent.MOUSE_RELEASED, middle(entry));
            } finally {
                owner.dockDrag().cancel();
            }
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            TabDrag drag = owner.tabDrag();
            assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
            assertThat(keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))
                .as("consumed").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).as("the release after Esc drops nothing").containsExactly(first, second, third);
        });
    }

    @Test void aLostReleaseEndsTheDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, a, onEntry(owner, c, 0.75));
            assertThat(motionListeners()).as("it listens for a lost release while it runs").isEqualTo(listeners + 1);
            // Swing sends a plain move only while no button is held: the release never came.
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            start(owner, a, onEntry(owner, c, 0.75));
            // So does another press, here on another entry; that press may start a drag of its own.
            Component other = entry(owner, b);
            mouse(other, MouseEvent.MOUSE_PRESSED, middle(other));
            assertThat(drag.active()).isFalse();
            mouse(other, MouseEvent.MOUSE_DRAGGED, new Point(middle(other).x, middle(other).y + 12));
            mouse(other, MouseEvent.MOUSE_DRAGGED, onEntry(owner, c, 0.75));
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(ids()).as("nothing dropped").containsExactly(first, second, third);
        });
    }

    @Test void theWindowLosingTheFocusEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastThird = onEntry(owner, c, 0.75);
            Component source = start(owner, a, pastThird);
            // A modal dialog opening, or another application coming forward, takes the focus and the release with it.
            owner.setActive(false);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(ids()).containsExactly(first, second, third);
        });
    }

    /** A window's title bar takes no tab, and a window no window manager opened moves nothing out of itself. */
    @Test void theTitleBarTakesNoTabAndAWindowOfItsOwnTearsNothingOut() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, a, onTitleBar(frame));
            assertThat(drag.target()).as("over the title bar").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame));
            Point outside = new Point(frame.getX() + frame.getWidth() + 200, frame.getY() + 100);
            source = start(owner, a, outside);
            assertThat(drag.target()).as("outside every window, with no window manager").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    /** The equal tabs report each entry's rectangle left to right inside their strip, which the drag drops between. */
    @Test void theEqualTabsReportTheirEntriesLeftToRightInsideTheStrip() throws Exception {
        edt(() -> {
            TabStrip strip = owner.parentTabBar();
            List<Rectangle> entries = strip.tabBounds();
            assertThat(entries).hasSize(3);
            for (int i = 0; i < entries.size(); i++) {
                assertThat(new Rectangle(strip.component().getSize()).contains(entries.get(i))).as("entry %d inside its strip", i).isTrue();
                assertThat(entries.get(i).width).as("entry %d has room", i).isPositive();
                if (i > 0) assertThat(entries.get(i).x).as("entry %d after entry %d", i, i - 1)
                    .isGreaterThanOrEqualTo(entries.get(i - 1).x + entries.get(i - 1).width);
            }
        });
    }

    /** Tabs and panes spec 4 and this plan's Rulings 18: a pane's strip starts no drag, under the classic tabs where it shows. */
    @Test void aPanesStripStartsNoDrag() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                frame(classic, 100, 50, 1208, 760);
                var strip = (ClassicTabs) classic.tabBar();
                assertThat(strip.component().isVisible()).as("the pane's classic strip shows").isTrue();
                Component entry = strip.entries().getFirst();
                Point press = middle(entry), away = new Point(press.x + 40, press.y + 20);
                mouse(entry, MouseEvent.MOUSE_PRESSED, press);
                mouse(entry, MouseEvent.MOUSE_DRAGGED, away);
                assertThat(classic.tabDrag().active()).as("past the threshold, still no drag").isFalse();
                assertThat(classic.tabDrag().label().showing()).isFalse();
                mouse(entry, MouseEvent.MOUSE_RELEASED, away);
                assertThat(classic.tabCount()).isEqualTo(1);
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }

    /**
     * The user's decision of 2026-10-20 (one row under Enterprise, until this plan's Task 4): Enterprise shows no
     * parent-tab bar, so no press starts a drag from it.
     */
    @Test void enterprisesBarNeverShowsSoNoTabDragStartsFromIt() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.newParentTab(HOME);
                assertThat(classic.parentTabCount()).isEqualTo(2);
                frame(classic, 100, 50, 1208, 760);
                assertThat(classic.parentTabBar()).isInstanceOf(ClassicTabs.class);
                assertThat(classic.parentTabBar().component().isVisible()).as("no bar to press").isFalse();
                assertThat(classic.parentTabBar().component().getHeight()).as("and it takes no row").isZero();
                assertThat(classic.tabDrag().active()).isFalse();
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java` with:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12, 15 and 17, tabs and panes spec 6: one tab drag across Jasper's windows, on synthetic events,
 * headless. The window manager's windows stand in stand-in frames on a pretend screen, each under a 28 px title bar: the
 * main window at 0,0 (1208 x 800) and a child window beside it at 1400,100 (800 x 600), one tab each holding one
 * terminal, and a new window far away until a test places it. The standard look shows a window's strip from two tabs,
 * so a test gives the window it drags from a second tab first.
 */
class TabDragWindowsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Each window's stand-in frame, by window id. */
    private final Map<UUID, JPanel> frames = new HashMap<>();
    /** Where the window manager put tear-out windows' frames on the pretend screen, and the cursor it gave with each. */
    private final List<Rectangle> placed = new ArrayList<>();
    private final List<Point> cursors = new ArrayList<>();
    private WindowManager manager;
    private MainWindow main;
    private ChildWindow child;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            terminals.onEvent(facts::add);
            manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame, window -> { }, window -> { },
                window -> { }, state -> { }, window -> { }, () -> { }));
            main = manager.ensureMain("cold", null).orElseThrow();
            child = manager.openTerminalWindow(null).orElseThrow();
            place(frames.get(main.id()), 0, 0, 1208, 800);
            place(frames.get(child.id()), 1400, 100, 800, 600);
        });
        launch();
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            manager.tabDrag().cancel();
            manager.closeAll();
        });
        closeOwners();
    }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    private WindowContent compose(WindowManager.Opening opening) {
        var launcher = new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
        // A tab opening in a laid-out strip would move into its place over several EDT turns: tests take it at once.
        content.setAnimations(new AnimationSettings(false, 0, 0, 0));
        return content;
    }

    /** A window's stand-in frame, far from the others until a test places it; the bounds the manager gives it are recorded. */
    private WindowShell frame(JasperWindow window) {
        JPanel shell = TabDragTestSupport.frame(window.content(), 5000, 5000, 800, 600);
        frames.put(window.id(), shell);
        return new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
            component -> SwingUtilities.isDescendingFrom(component, shell), (bounds, cursor) -> {
                placed.add(bounds);
                cursors.add(cursor);
            });
    }

    private JPanel frameOf(JasperWindow window) { return frames.get(window.id()); }

    private static GroupId activeGroup(JasperWindow window) { return window.content().workspace().model().activeGroup(); }

    private static Object session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    /** A new tab in {@code window}, shown and laid out, so its strip shows: its id. Its shell starts at the next launch. */
    private ParentTabId secondTab(JasperWindow window) {
        window.content().newParentTab(HOME);
        relayout(frameOf(window));
        return window.content().activeParentTab();
    }

    /** The item that the strip's entry of {@code window}'s tab {@code id} shows. */
    private static Tab entryOf(WindowContent window, ParentTabId id) {
        return window.parentTabs().view(id).orElseThrow().selected();
    }

    /** {@code window}'s tabs, in strip order. */
    private static List<ParentTabId> tabIds(WindowContent window) {
        return window.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList();
    }

    /** The band at the top of {@code window}'s tabs where its strip shows, on the pretend screen. */
    private static Rectangle band(WindowContent window) {
        var parents = window.parentTabs();
        int height = window.parentTabBar().component().getPreferredSize().height;
        return onScreen(parents, new Rectangle(0, 0, parents.getWidth(), height));
    }

    /** Runs {@code test} with a focus manager that records the drag's Esc dispatcher, then puts the previous one back. */
    private static void recordingKeys(Consumer<CommandPaletteShortcutsTest.RecordingFocusManager> test) {
        KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        try {
            test.accept(keys);
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
        }
    }

    /** The drag ended clean: no Esc dispatcher, lost-release listener, landing or label is left. */
    private void assertEnded(CommandPaletteShortcutsTest.RecordingFocusManager keys, int listeners) {
        assertThat(manager.tabDrag().active()).isFalse();
        assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
        assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
        assertThat(manager.tabDrag().overlay().getParent()).as("the landing goes").isNull();
        assertThat(manager.tabDrag().label().showing()).as("the label goes").isFalse();
    }

    @Test void everyWindowOfTheManagerSharesItsOneDrag() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            assertThat(main.content().tabDrag()).isSameAs(manager.tabDrag());
            assertThat(child.content().tabDrag()).isSameAs(manager.tabDrag());
            Tab moving = entryOf(main.content(), id[0]);
            Point there = inside(band(child.content()), 0.5, 0.5);
            Component source = start(main.content(), moving, there);
            assertThat(manager.tabDrag().target()).as("the child window's band").contains(new TabDrag.Target.Bar(child.content(), 1));
            child.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("another window losing the focus leaves the drag alone").isTrue();
            main.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("its own window losing it ends it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(moving.window()).isEqualTo(main.id());
        });
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 4: a tab dropped on another window's strip moves there whole. */
    @Test void aTabDroppedOnAnotherWindowsStripMovesThereWhole() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            main.content().split(Side.RIGHT);
            secondTab(main);
            ids[1] = child.content().activeParentTab();
            ids[2] = secondTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId moving = ids[0], left = ids[1], right = ids[2];
            WorkspaceView workspace = from.parentTabs().view(moving).orElseThrow();
            List<Tab> tabs = workspace.tabs();
            assertThat(tabs).as("its two panes").hasSize(2);
            Layout layout = workspace.model();
            var sessions = tabs.stream().map(TabDragWindowsTest::session).toList();
            Point between = onEntry(to, entryOf(to, right), 0.25);
            facts.clear();
            Component source = start(from, entryOf(from, moving), between);
            assertThat(manager.tabDrag().draggedParentTab()).isEqualTo(moving);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Bar(to, 1));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            assertThat(manager.tabDrag().overlay().placeholder()).as("a bar between its entries")
                .isEqualTo(marker(to, entryOf(from, moving), 1));
            mouse(source, MouseEvent.MOUSE_RELEASED, between);
            assertThat(tabIds(to)).containsExactly(left, moving, right);
            assertThat(to.parentTabs().view(moving).orElseThrow().model()).as("whole, with its id and layout").isEqualTo(layout);
            assertThat(to.activeParentTab()).isEqualTo(moving);
            assertThat(tabIds(from)).hasSize(1);
            assertThat(from.parentTabBar().component().isVisible()).as("one tab left: its strip hides").isFalse();
            assertThat(moves()).as("one move per item, reported where it arrives").containsExactlyElementsOf(tabs.stream()
                .map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id())).toList());
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            for (int i = 0; i < tabs.size(); i++) assertThat(session(tabs.get(i))).as("the same session").isSameAs(sessions.get(i));
        });
    }

    /** Workspace plan 7c Rulings 3: a tab dragged along its strip moves no item, so no TAB_MOVED, and the focus stays. */
    @Test void aTabDraggedAlongItsStripReportsNoMoveAndLeavesTheFocus() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> recordingKeys(keys -> {
            WindowContent from = main.content();
            Tab moving = entryOf(from, ids[0]);
            Point past = onEntry(from, entryOf(from, ids[1]), 0.75);
            facts.clear();
            Component source = start(from, moving, past);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Bar(from, 1));
            keys.own(moving.component());
            int[] focused = {0};
            moving.focusTarget(() -> focused[0]++);
            mouse(source, MouseEvent.MOUSE_RELEASED, past);
            assertThat(tabIds(from)).containsExactly(ids[1], ids[0]);
            assertThat(keys.getPermanentFocusOwner()).as("the focus stays where it was").isSameAs(moving.component());
            assertThat(focused[0]).as("and no item takes it").isZero();
            assertThat(moves()).as("no item moved").isEmpty();
        }));
    }

    /**
     * Workspace plan 7c Rulings 5 (the controller's ruling for 8a): a window showing no strip (the standard look, one tab)
     * takes a tab over the band at its top, where its strip shows, at its end; over its panes, nothing.
     */
    @Test void aWindowShowingNoStripTakesATabAlongItsTop() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            assertThat(to.parentTabBar().component().isVisible()).as("one tab").isFalse();
            Tab moving = entryOf(from, ids[1]);
            Point panes = inside(pane(to, activeGroup(child)), 0.5, 0.5);
            Component source = start(from, moving, panes);
            assertThat(manager.tabDrag().target()).as("a pane takes no tab").isEmpty();
            Rectangle band = band(to);
            Point top = inside(band, 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, top);
            assertThat(manager.tabDrag().target()).as("at the end of its strip").contains(new TabDrag.Target.Bar(to, 1));
            JLayeredPane layers = SwingUtilities.getRootPane(to).getLayeredPane();
            Point corner = new Point(band.getLocation());
            SwingUtilities.convertPointFromScreen(corner, layers);
            assertThat(manager.tabDrag().overlay().highlight()).as("the band where its strip shows")
                .isEqualTo(new Rectangle(corner, band.getSize()));
            assertThat(manager.tabDrag().overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(tabIds(to)).containsExactly(ids[0], ids[1]);
            assertThat(to.parentTabBar().component().isVisible()).as("two tabs: its strip shows").isTrue();
            assertThat(moving.window()).isEqualTo(child.id());
            assertThat(from.parentTabBar().component().isVisible()).isFalse();
        });
    }

    /** Workspace plan 7c Rulings 5: the main window's empty workspace takes a tab dragged along its top, in its place. */
    @Test void theEmptyMainWorkspaceTakesATabAlongItsTop() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            assertThat(to.tabs()).isEmpty();
            assertThat(to.parentTabBar().component().isVisible()).as("the empty workspace shows no strip").isFalse();
            Tab moving = entryOf(from, id[0]);
            Point top = inside(band(to), 0.5, 0.5);
            Component source = start(from, moving, top);
            assertThat(manager.tabDrag().target()).as("its strip has no entry").contains(new TabDrag.Target.Bar(to, 0));
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(tabIds(to)).as("in the empty workspace's place").containsExactly(id[0]);
            assertThat(to.parentTabBar().component().isVisible()).as("one tab: still no strip").isFalse();
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(tabIds(from)).hasSize(1);
        });
    }

    /**
     * Workspace plan 7c's final fix wave (the controller's ruling for 8a): the main window's empty workspace has no panes,
     * so it takes a tab dragged anywhere over it in its place, and the whole of it is the landing.
     */
    @Test void theEmptyMainWorkspaceTakesATabAnywhereOverIt() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            ParentTabId empty = to.activeParentTab();
            Tab moving = entryOf(from, id[0]);
            Point centre = middle(to.workspace());
            Component source = start(from, moving, centre);
            assertThat(manager.tabDrag().target()).as("in its middle").contains(new TabDrag.Target.Bar(to, 0));
            ParentTabsView parents = to.parentTabs();
            assertThat(manager.tabDrag().overlay().highlight()).as("all of it")
                .isEqualTo(SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()),
                    SwingUtilities.getRootPane(to).getLayeredPane()));
            assertThat(manager.tabDrag().overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(tabIds(to)).as("in the empty workspace's place").containsExactly(id[0]);
            assertThat(to.parentTabs().view(empty)).isEmpty();
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(tabIds(from)).hasSize(1);
        });
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 6: a tab released outside every window becomes a full window. */
    @Test void aTabReleasedOutsideEveryWindowBecomesAFullWindowUnderTheCursor() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content();
            WorkspaceView workspace = from.parentTabs().view(id[0]).orElseThrow();
            Layout layout = workspace.model();
            Tab moving = entryOf(from, id[0]);
            Object session = session(moving);
            TabStrip strip = from.parentTabBar();
            Rectangle home = onScreen(strip.component(), strip.tabBounds().get(1));
            Point press = middle(entry(from, moving));
            Point outside = new Point(2600, 900);
            // The new window's workspace is the tab's size, with the entry's corner where the cursor holds it.
            Rectangle expected = new Rectangle(outside.x - (press.x - home.x), outside.y - (press.y - home.y),
                workspace.getWidth(), workspace.getHeight());
            facts.clear();
            Component source = start(from, moving, outside);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.TearOut(expected, outside));
            assertThat(manager.tabDrag().overlay().getParent()).as("no landing in any window").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow full = manager.children().getLast();
            assertThat(full).isNotSameAs(child);
            assertThat(full.content().parentTabs().model()).as("a full window, the tab whole").isEqualTo(ParentTabs.of(new ParentTab(id[0], layout)));
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
            assertThat(cursors).as("on the display under the cursor").containsExactly(outside);
            assertThat(session(moving)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), full.id()));
            assertThat(from.parentTabCount()).isEqualTo(1);
        });
    }

    /**
     * Workspace plan 7c Rulings 7: no standard look shows a strip over a window's only tab, so none can be dragged; the
     * window manager still moves it, as Move to Main Window does, onto another window's strip, never out to a window of
     * its own, and its window closes after the move.
     */
    @Test void aFullWindowsOnlyTabMovesOntoAnotherWindowsStripAndItsWindowClosesAfterTheMove() throws Exception {
        edt(() -> secondTab(main));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            ParentTabId only = from.activeParentTab();
            Tab moving = entryOf(from, only);
            assertThat(from.parentTabBar().component().isVisible()).as("no strip over it to drag it from").isFalse();
            assertThat(from.mayPopOutParentTab(only)).as("a full window's only tab stays in its window").isFalse();
            List<ParentTabId> before = tabIds(to);
            facts.clear();
            assertThat(manager.moveParentTab(from, only, to, before.size())).isTrue();
            assertThat(tabIds(to)).as("at the end of the main window's strip").containsExactly(before.get(0), before.get(1), only);
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /** Workspace plan 7b Rulings 10: a move taking the main window's only tab leaves its empty workspace under a new id. */
    @Test void aMoveThatEmptiesTheMainWindowGivesItsEmptyWorkspaceANewId() throws Exception {
        edt(() -> secondTab(child));
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId only = from.activeParentTab();
            List<ParentTabId> before = tabIds(to);
            assertThat(manager.moveParentTab(from, only, to, 2)).isTrue();
            assertThat(tabIds(to)).as("at the end of its strip").containsExactly(before.get(0), before.get(1), only);
            assertThat(main.closed()).as("the main window stays").isFalse();
            assertThat(from.tabs()).isEmpty();
            ParentTabId empty = from.activeParentTab();
            assertThat(tabIds(from)).containsExactly(empty);
            assertThat(empty).as("its empty workspace's id is new").isNotEqualTo(only);
            assertThat(tabIds(to)).as("so no id is in two windows").doesNotContain(empty);
        });
    }

    /** Workspace plan 7c Rulings 8: a tab holding a pinned item moves along its own strip only. */
    @Test void aTabHoldingAPinnedItemMovesAlongItsOwnStripOnly() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content();
            var pin = from.parentTabs().view(ids[0]).orElseThrow().tabs().getFirst().pin();
            try {
                facts.clear();
                Point there = inside(band(child.content()), 0.5, 0.5);
                Component source = start(from, entryOf(from, ids[0]), there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("a modal owner holds one of its items in its window").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(2600, 900));
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                Point past = onEntry(from, entryOf(from, ids[1]), 0.75);
                mouse(source, MouseEvent.MOUSE_DRAGGED, past);
                assertThat(manager.tabDrag().target()).as("its own strip still takes it").contains(new TabDrag.Target.Bar(from, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, past);
                assertThat(tabIds(from)).containsExactly(ids[1], ids[0]);
                assertThat(manager.children()).containsExactly(child);
                assertThat(moves()).isEmpty();
            } finally {
                pin.close();
            }
        });
    }

    /** Workspace plan 7c's final review: a tab holding an item whose close waits for its guard moves along its strip only. */
    @Test void aTabHoldingAnItemBeingClosedMovesAlongItsOwnStripOnly() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            ParentTabId holding = from.parentTabOf(notes).orElseThrow();
            ParentTabId last = secondTab(main);
            from.selectParentTab(holding);
            from.selectTab(notes);
            relayout(frameOf(main));
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            try {
                assertThat(closing).as("the guard has not answered").isNotDone();
                facts.clear();
                Point there = inside(band(child.content()), 0.5, 0.5);
                Component source = start(from, notes, there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("another window takes no tab holding an item being closed").isEmpty();
                assertThat(manager.tabDrag().overlay().getParent()).as("no landing shows").isNull();
                mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(2600, 900));
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, onEntry(from, entryOf(from, last), 0.75));
                assertThat(manager.tabDrag().target()).as("its own strip still takes it").isPresent();
                mouse(source, MouseEvent.MOUSE_RELEASED, there);
                assertThat(notes.window()).isEqualTo(main.id());
                assertThat(manager.children()).containsExactly(child);
                assertThat(moves()).isEmpty();
            } finally {
                answer.complete(false);
            }
            assertThat(closing).isCompletedWithValue(false);
        });
    }

    /** Workspace plan 7c Rulings 9: a pane window has no tabs and takes none; nothing moves. */
    @Test void aPaneWindowTakesNoTab() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> {
            id[0] = secondTab(main);
            child.content().split(Side.RIGHT);
        });
        launch();
        edt(() -> {
            ChildWindow pane = manager.popOutTab(child.content().selectedTab()).orElseThrow();
            place(frameOf(pane), 100, 900, 800, 400);
            assertThat(pane.content().isPaneWindow()).isTrue();
            WindowContent from = main.content();
            Tab moving = entryOf(from, id[0]);
            Point top = inside(band(pane.content()), 0.5, 0.5);
            Component source = start(from, moving, top);
            assertThat(manager.tabDrag().target()).as("along its top").isEmpty();
            Point middle = middle(pane.content().workspace());
            mouse(source, MouseEvent.MOUSE_DRAGGED, middle);
            assertThat(manager.tabDrag().target()).as("nor over its pane").isEmpty();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, middle);
            assertThat(pane.content().tabs()).hasSize(1);
            assertThat(from.parentTabCount()).isEqualTo(2);
            assertThat(moving.window()).isEqualTo(main.id());
        });
    }

    /** A tab whose pane holds a plugin's view moves with the same view, shown in its new window. */
    @Test void aTabHoldingAPluginsViewMovesWithItsView() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            JPanel view = new JPanel();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), view);
            ParentTabId holding = from.parentTabOf(notes).orElseThrow();
            secondTab(main);
            from.selectParentTab(holding);
            from.selectTab(notes);
            relayout(frameOf(main));
            drop(from, notes, inside(band(to), 0.5, 0.5));
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(notes.component()).as("the same view").isSameAs(view);
            assertThat(SwingUtilities.isDescendingFrom(view, to)).isTrue();
            assertThat(view.isVisible()).as("shown in its new window").isTrue();
            assertThat(notes.closed()).isFalse();
        });
    }

    /** A title can run to 200 characters: the label keeps near a tab's width, as VS Code's and IntelliJ's drag images do. */
    @Test void theDragLabelCapsALongTitleWithAnEllipsis() throws Exception {
        edt(() -> {
            String title = "quarterly-report-final-revision-".repeat(5) + "notes.txt";
            Tab notes = model.tabs().open("dev.example.editor", "notes", title, main.id(), new JPanel());
            ParentTabId holding = main.content().parentTabOf(notes).orElseThrow();
            secondTab(main);
            main.content().selectParentTab(holding);
            main.content().selectTab(notes);
            relayout(frameOf(main));
            assertThat(main.content().tabTitle(notes)).isEqualTo(title);
            start(main.content(), notes, new Point(3000, 1200));
            assertThat(manager.tabDrag().label().text()).as("40 characters, the last an ellipsis")
                .isEqualTo(title.substring(0, 39) + "…");
        });
    }

    @Test void releasingOverAnotherWindowsTitleBarChangesNothing() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            Tab moving = entryOf(main.content(), id[0]);
            Point title = onTitleBar(frameOf(child));
            Component source = start(main.content(), moving, title);
            assertThat(manager.tabDrag().target()).as("over the child window's title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, title);
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(moves()).isEmpty();
        });
    }

    @Test void whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            place(frameOf(child), 300, 200, 800, 500);
            Point overlap = inside(band(child.content()), 0.5, 0.5);
            Rectangle mainFrame = onScreen(frameOf(main), new Rectangle(frameOf(main).getSize()));
            assertThat(mainFrame.contains(overlap)).as("over both windows").isTrue();
            Tab moving = entryOf(main.content(), id[0]);
            child.events().activated().accept(true);
            Component source = start(main.content(), moving, overlap);
            assertThat(manager.tabDrag().target()).as("the child window came to the front last")
                .contains(new TabDrag.Target.Bar(child.content(), 1));
            main.events().activated().accept(true);
            mouse(source, MouseEvent.MOUSE_DRAGGED, overlap);
            assertThat(manager.tabDrag().target()).as("now the main window did, whose pane takes no tab").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, overlap);
            assertThat(moving.window()).isEqualTo(main.id());
        });
    }

    /** Parent tabs spec 5 and workspace plan 7b Rulings 11: a pane window opens where its pane was, at its size. */
    @Test void aPoppedOutTabsPaneWindowOpensAtItsPanesPlace() throws Exception {
        edt(() -> {
            GroupView pane = main.content().workspace().activeGroupView();
            Rectangle expected = onScreen(pane, new Rectangle(pane.getSize()));
            ChildWindow popped = manager.popOutTab(main.content().selectedTab()).orElseThrow();
            assertThat(popped.content().isPaneWindow()).isTrue();
            assertThat(placed).as("the pane's place and size").containsExactly(expected);
            assertThat(cursors).as("on the display under its middle")
                .containsExactly(new Point((int) expected.getCenterX(), (int) expected.getCenterY()));
        });
    }

    /** Workspace plan 7b Rulings 11: a pane a maximize keeps out of the tree has no place on screen, so the platform places it. */
    @Test void aPaneAMaximizeHidesPopsOutWhereThePlatformPutsIt() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab hidden = from.selectedTab();
            from.split(Side.RIGHT);
            relayout(frameOf(main));
            from.workspace().toggleMaximize();
            relayout(frameOf(main));
            GroupId pane = hidden.group().orElseThrow();
            assertThat(from.workspace().model().maximized()).isPresent().get().isNotEqualTo(pane);
            assertThat(from.workspace().groupView(pane).getWidth()).as("its last size, out of the tree").isPositive();
            ChildWindow popped = manager.popOutTab(hidden).orElseThrow();
            assertThat(popped.content().isPaneWindow()).isTrue();
            assertThat(placed).as("no place on screen").isEmpty();
        });
    }

    /** A command moving the item a dragged tab's entry shows (Move to Main Window from the keyboard) ends the drag. */
    @Test void anItemMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> {
            Tab moving = entryOf(child.content(), id[0]);
            Point there = inside(band(main.content()), 0.5, 0.5);
            Component source = start(child.content(), moving, there);
            assertThat(manager.tabDrag().active()).isTrue();
            model.tabs().moveToMainWindow(moving);
            assertThat(manager.tabDrag().active()).isFalse();
            assertThat(manager.tabDrag().label().showing()).isFalse();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(moving.window()).as("where the command put it").isEqualTo(main.id());
            assertThat(child.content().tabs()).hasSize(1);
        });
    }

    @Test void aMoveThatIsRefusedOrFailsAtTheDropLeavesTheItemWhereItWasAndTheDragEnded() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            WindowContent from = main.content(), to = child.content();
            Tab tab = entryOf(from, id[0]);
            Object session = session(tab);
            Point there = inside(band(to), 0.5, 0.5);
            // Refused: a listener of the item throws as it takes the child window.
            var refused = new IllegalStateException("the tab will not go");
            var refusing = tab.onChanged(() -> { if (child.id().equals(tab.window())) throw refused; });
            Component source = start(from, tab, there);
            assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
            try {
                assertThatThrownBy(() -> mouse(source, MouseEvent.MOUSE_RELEASED, there)).isSameAs(refused);
            } finally {
                refusing.close();
            }
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(from.parentTabOf(tab)).as("still in its tab").contains(id[0]);
            assertThat(to.tabs()).doesNotContain(tab);
            assertEnded(keys, listeners);
            // Failed: the child window cannot show it (a listener of its TAB_MOVED throws an Error, which propagates).
            var failed = new Error("the window cannot show it");
            boolean[] thrown = {false};
            var failing = terminals.onEvent(fact -> {
                if (!thrown[0] && fact instanceof TerminalEvent.TabMoved moved && moved.toWindowId().equals(child.id())) {
                    thrown[0] = true;
                    throw failed;
                }
            });
            Component again = start(from, tab, there);
            try {
                assertThatThrownBy(() -> mouse(again, MouseEvent.MOUSE_RELEASED, there)).isSameAs(failed);
            } finally {
                failing.close();
            }
            assertThat(thrown[0]).isTrue();
            assertThat(tab.window()).as("back in the window it left").isEqualTo(main.id());
            assertThat(from.tabs()).contains(tab);
            assertThat(to.tabs()).doesNotContain(tab).hasSize(1);
            assertThat(session(tab)).as("the same session").isSameAs(session);
            assertThat(((TerminalContent) tab.content()).pane().session().exitFuture()).isNotDone();
            assertEnded(keys, listeners);
        }));
    }

    /** The drag's own window closing (Close Window, its last shell exiting) ends the drag clean, its landing elsewhere too. */
    @Test void theDragsOwnWindowClosingMidDragEndsItClean() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(child));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            Tab tab = entryOf(child.content(), id[0]);
            Point there = inside(band(main.content()), 0.5, 0.5);
            Component source = start(child.content(), tab, there);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the main window")
                .isSameAs(SwingUtilities.getRootPane(main.content()).getLayeredPane());
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertEnded(keys, listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(main.content().tabs()).as("nothing dropped").hasSize(1);
            assertThat(moves()).isEmpty();
        }));
    }

    /**
     * The target window closing while it holds the landing leaves the drag running: at the next event the landing leaves
     * that window, which takes no drop any more, and the drag goes on to its release.
     */
    @Test void theTargetWindowClosingUnderTheLandingLeavesTheDragRunning() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> recordingKeys(keys -> {
            int listeners = motionListeners();
            WindowContent from = main.content();
            ParentTabId other = tabIds(from).getFirst();
            Tab tab = entryOf(from, id[0]);
            Point there = inside(band(child.content()), 0.5, 0.5);
            Component source = start(from, tab, there);
            JLayeredPane layers = SwingUtilities.getRootPane(child.content()).getLayeredPane();
            assertThat(manager.tabDrag().overlay().getParent()).isSameAs(layers);
            child.requestClose();
            assertThat(child.closed()).isTrue();
            assertThat(manager.tabDrag().active()).as("another window closing leaves the drag alone").isTrue();
            mouse(source, MouseEvent.MOUSE_DRAGGED, there);
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing leaves the closed window").isNull();
            assertThat(manager.tabDrag().target()).as("where it stood, outside every open window")
                .hasValueSatisfying(target -> assertThat(target).isInstanceOf(TabDrag.Target.TearOut.class));
            Point before = onEntry(from, entryOf(from, other), 0.25);
            mouse(source, MouseEvent.MOUSE_DRAGGED, before);
            mouse(source, MouseEvent.MOUSE_RELEASED, before);
            assertThat(tabIds(from)).as("the drag went on to its drop").containsExactly(id[0], other);
            assertThat(manager.children()).isEmpty();
            assertEnded(keys, listeners);
        }));
    }

    @Test void releasingOverAnotherWindowOfJaspersLikeSettingsDropsNothing() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondTab(main));
        launch();
        edt(() -> {
            // A headless JVM has no windows: the drag is told where a Settings frame stands on the pretend screen.
            manager.tabDrag().otherWindows = () -> List.of(new Rectangle(2400, 700, 600, 500));
            Tab tab = entryOf(main.content(), id[0]);
            Point overSettings = new Point(2600, 900);
            Component source = start(main.content(), tab, overSettings);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("a window of Jasper's, outside any workspace").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, overSettings);
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(tab.window()).isEqualTo(main.id());
        });
    }

    /**
     * The stand-in frames are no native windows, so the drag reaches this rule only through its helper; a full-screen
     * window on a second display is a GUI check.
     */
    @Test void onMacOsAWindowInFullScreenOnTheDragsDisplayOtherThanTheDragsOwnTakesNoDrop() {
        Rectangle display = new Rectangle(0, 0, 1512, 982);
        assertThat(TabDrag.dropFrame(display, display, false, true, true)).as("full screen: a Space of its own").isEqualTo(new Rectangle());
        assertThat(TabDrag.dropFrame(display, display, true, true, true)).as("the drag's own window is in sight").isEqualTo(display);
        assertThat(TabDrag.dropFrame(new Rectangle(0, 25, 1512, 957), display, false, true, true)).as("zoomed, under the menu bar")
            .isEqualTo(new Rectangle(0, 25, 1512, 957));
        Rectangle second = new Rectangle(1512, 0, 1920, 1080);
        assertThat(TabDrag.dropFrame(second, second, false, false, true))
            .as("full screen on another display, which shows a Space of its own: in sight").isEqualTo(second);
        assertThat(TabDrag.dropFrame(display, display, false, true, false)).as("elsewhere a window covering its display is in sight")
            .isEqualTo(display);
        assertThat(TabDrag.dropFrame(display, null, false, false, true)).as("no display known").isEqualTo(display);
    }
}
```

Delete the tests of the item drag's drops with the drops, and let `WindowTabsTest`'s drag reorder tabs on the bar,
where a drag starts now, rather than a pane's items:

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan8a"); from ledger import drop
from edits import edit
path = "jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceDropTest.java"
pathlib.Path(path).unlink()
drop(path, "jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTest.java")
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java", [(
    """            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTerminal(); first.rename("first"); owner.newTab(HOME);
""", """            JComponent strip = owner.parentTabBar().component();
            var first = owner.currentTerminal(); first.rename("first"); owner.newParentTab(HOME);
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragTest' --tests 'dev.jasper.app.workspace.TabDragWindowsTest'`
Expected: FAIL in `TabDragTest.aPanesStripStartsNoDrag` only (a pane's strip still starts the item drag); every other
test compiles against today's `TabDrag` and passes, as the parent-tab drag already behaves so.

- [ ] **Step 3: The drag carries tabs alone**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` with:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * The tab drag (workspace tabs spec 12; tabs and panes spec 6). A left press on a tab's entry in its window's strip,
 * the {@link ParentTabBar}, that moves past the kit's tab drag threshold ({@code TAB_REORDER_THRESHOLD}, 5 px by
 * default) starts it, carrying the whole tab with every pane of it. It uses plain mouse events in screen coordinates,
 * never AWT drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor goes, and
 * synthetic events drive it in tests. While it runs, {@link TabDragLabel} shows the entry's label beside the cursor and
 * the target window's layered pane shows where the tab would land ({@link DockDropOverlay}); nothing moves until the
 * release.
 * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of
 * the drag's own window but that window), the most recently active. Over its strip, the place before the first of the
 * strip's other entries whose middle is not left of the cursor, where the entries take their new places at once on the
 * release; a window whose strip hides (the standard look's one tab) takes it at its end over the band its strip takes
 * when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place. Anywhere
 * else in that window (a pane, its dock, chrome or title bar), and over any other window of Jasper's (Settings, About, a
 * dialog), nothing; outside every window, a new full window the size of the tab's workspace, with the entry where the
 * cursor holds it and kept on the display under the cursor, except for a full window's only tab, whose window already
 * is its own. A tab holding an item that is pinned (a modal owner holds it in its window), or one being closed, takes
 * targets along its own strip only, and a pane window takes none. Releasing the left button carries the target out:
 * along its own strip as a reorder, into another window or out of every window through the window manager of the
 * drag's window, which moves the tab's items through the tab registry; none touches an item's content or session, and a
 * window no manager opened moves nothing out of itself. Releasing on no target, Esc, another press or a plain move
 * (both mean Swing lost the release), the drag's window losing the focus (a modal dialog, another application), and the
 * item the entry shows closing, leaving its tab or giving way to another of its panes end the drag without a drop. EDT
 * only.
 */
final class TabDrag {
    /** Where a release would put the dragged tab. */
    sealed interface Target {
        /**
         * Into {@code window}'s strip at {@code index} (tabs and panes spec 6).
         *
         * @param window the window
         * @param index the tab's place among the strip's entries other than its own
         */
        record Bar(WindowContent window, int index) implements Target { }

        /**
         * Out of every window, into a new full window whose workspace takes {@code bounds}, on the display under
         * {@code cursor} (tabs and panes spec 6).
         *
         * @param bounds the new window's workspace on screen; its frame adds its title bar
         * @param cursor the cursor on screen, inside {@code bounds}
         */
        record TearOut(Rectangle bounds, Point cursor) implements Target { }
    }

    /** How far right of and below the cursor the label's corner sits, so no window is ever under the pointer's tip. */
    static final int LABEL_OFFSET = 8;

    private final Supplier<List<WindowContent>> windows;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final TabDragLabel label = new TabDragLabel();
    /**
     * Test seam: the frames on screen of Jasper's other showing windows (Settings, About, Licenses, a dialog, a popup),
     * where a release drops nothing; none of the windows the drag reaches, nor the label's. Production reads
     * {@link Window#getWindows()}, which a headless JVM leaves empty.
     */
    Supplier<List<Rectangle>> otherWindows = this::auxiliaryFrames;
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;
    private final AWTEventListener lostRelease = this::lostRelease;
    /** The focus manager the Esc dispatcher went to, while a drag runs. */
    private KeyboardFocusManager keys;
    /**
     * While a press is pending or a drag runs: the drag's window; the dragged tab; the item its entry shows; the
     * component the press reached; the press on screen.
     */
    private WindowContent from;
    private ParentTabId parentTab;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    /** Where the press held the entry, from its top-left corner, and the size of the tab's workspace: a tear-out's window. */
    private Point grab;
    private Dimension size;
    private boolean dragging;
    /** End the drag when the item the entry shows closes, leaves the tab or gives way to another of its panes. */
    private Subscription closing, changed, shown;
    private Optional<Target> target = Optional.empty();

    /**
     * @param windows the windows a drag can reach, the most recently active first; what leaves its window goes through
     *     the window manager of the drag's window ({@code WindowContent.manager()})
     */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }

    /** @return whether a drag runs: from the threshold to its release or its end */
    boolean active() { return dragging; }

    /** @return the item the dragged tab's entry shows, while a drag runs; else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return the dragged tab while a drag runs, else null */
    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }

    /** @return where a release would put the tab now */
    Optional<Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    TabDragLabel label() { return label; }

    /**
     * A left press on {@code tab}'s entry in {@code bar}, the entry of the tab whose active pane holds it: a drag of that
     * whole tab may start from it. A press while another is pending or a drag runs ends that one first; any other press
     * starts nothing.
     */
    void pressed(ParentTabBar bar, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        WindowContent window = bar.owner();
        Optional<ParentTabId> id = window.parentTabOf(tab);
        Optional<WorkspaceView> view = id.flatMap(window.parentTabs()::view);
        List<Rectangle> places = bar.strip().tabBounds();
        int index = bar.tabs().indexOf(tab);
        if (view.isEmpty() || !SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || index < 0
                || index >= places.size()) return;
        from = window;
        parentTab = id.get();
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
        Point inStrip = SwingUtilities.convertPoint(source, event.getPoint(), bar.strip().component());
        grab = new Point(inStrip.x - places.get(index).x, inStrip.y - places.get(index).y);
        size = view.get().getSize();
    }

    /**
     * A drag event from the pressed component: past the threshold with the left button held the drag starts, then follows
     * the cursor. An entry that changed since the press has nothing to drag.
     */
    void dragged(MouseEvent event) {
        if (tab == null || event.getComponent() != source) return;
        Point at = screen(event);
        if (!dragging) {
            // Swing sends the pressed component a drag with any button held: only the left one drags a tab.
            if (!SwingUtilities.isLeftMouseButton(event)) return;
            if (at.distance(pressedAt) <= NativeStyles.installed().metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
            if (!inPlace()) {
                cancel();
                return;
            }
            try {
                start(at);
            } catch (RuntimeException | Error failure) {
                finish();
                throw failure;
            }
        }
        update(at);
    }

    /**
     * The left button's release on the pressed component: a running drag drops where its target says. The label and the
     * landing go whatever happens.
     */
    void released(MouseEvent event) {
        if (tab == null || event.getComponent() != source || event.getButton() != MouseEvent.BUTTON1) return;
        if (!dragging) {
            finish();
            return;
        }
        Tab moving = tab;
        WindowContent home = from;
        ParentTabId parent = parentTab;
        Optional<Target> where = Optional.empty();
        try {
            update(screen(event));
            where = target;
        } finally {
            finish();
        }
        where.ifPresent(place -> drop(moving, home, parent, place));
    }

    /** Ends a running drag or a pending press without a drop. */
    void cancel() { finish(); }

    /** {@code window} lost the focus or closed: a press or a drag from it ends without a drop. */
    void cancelFrom(WindowContent window) {
        if (window != null && from == window) cancel();
    }

    /** While a drag runs it owns Esc, wherever the focus is: Esc ends it. */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (!dragging || event.getKeyCode() != KeyEvent.VK_ESCAPE) return false;
        if (event.getID() == KeyEvent.KEY_PRESSED) cancel();
        return true;
    }

    /**
     * A release Swing lost: while the left button is held, Swing sends only drag events, to the pressed component, so a
     * plain move or a press anywhere means the drag's release never came.
     */
    private void lostRelease(AWTEvent event) {
        if (dragging && (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_PRESSED)) cancel();
    }

    /**
     * Whether the entry is still where the press found it: its item open and in the press's window, and still the item
     * the press's tab shows.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        return from.parentTabs().view(parentTab).map(WorkspaceView::selected).filter(tab::equals).isPresent();
    }

    /** Starts the drag: what ends it is in place before anything shows, so {@link #finish} can always take it away. */
    private void start(Point at) {
        dragging = true;
        keys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keys.addKeyEventDispatcher(escape);
        Toolkit.getDefaultToolkit().addAWTEventListener(lostRelease, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        closing = tab.onClosed(this::cancel);
        // A command that moves the item while its tab is dragged (Move to Main Window from the keyboard) ends the drag.
        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });
        // The entry giving way to another pane of its tab (a click there, a request opening there) ends the drag.
        shown = tab.onActive(active -> { if (!inPlace()) cancel(); });
        // The title button saw the press: its release after a drag must not click it too.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        label.show(from.tabTitle(tab), from.tabIcon(tab), labelAt(at));
    }

    /** Finds the target under {@code at}, moves the label and shows the landing; an entry no longer in place ends the drag. */
    private void update(Point at) {
        if (!inPlace()) {
            cancel();
            return;
        }
        label.move(labelAt(at));
        target = Optional.empty();
        List<WindowContent> open = windows.get().stream().filter(window -> !window.closed()).toList();
        GraphicsDevice home = device(from);
        OptionalInt under = WorkspaceDropResolver.windowAt(at,
            open.stream().map(window -> onScreen(window, window == from, home)).toList());
        // What the registry keeps in its window stays there: a tab holding an item that is pinned or being closed. No
        // other window takes it, and it tears nothing out.
        boolean stays = !movable();
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A tab drops on a strip alone (tabs and panes spec 6).
            if (!stays || window == from) target = onBar(window, at);
        } else if (WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !stays && tearsOut()) {
            // Outside every window of Jasper's: a full window.
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
        showLanding();
    }

    /**
     * The dragged tab's drop on {@code window}'s strip at {@code at}: before the first of the strip's other entries whose
     * middle is not left of the cursor; while another window's strip hides, at its end, over the band the strip takes
     * when it shows (workspace plan 7c Rulings 5); on the main window's empty workspace, which has no panes, anywhere over
     * it, in its place. Empty for a pane window, which has no tabs, off the strip, and for the place the tab has.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window.isPaneWindow() || (window == from && !shows)) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, window.parentTabs());
        Rectangle area = barArea(window);
        OptionalInt index = OptionalInt.empty();
        if (window != from && window.parentTabs().isEmpty()) {
            if (new Rectangle(window.parentTabs().getSize()).contains(local)) index = OptionalInt.of(0);
        } else if (shows) {
            index = WorkspaceDropResolver.place(local, area, barEntries(window));
        } else if (area.contains(local)) {
            index = OptionalInt.of(window.parentTabs().bar().tabs().size());
        }
        if (index.isEmpty() || (window == from && index.getAsInt() == from.parentTabs().model().indexOf(parentTab)))
            return Optional.empty();
        return Optional.of(new Target.Bar(window, index.getAsInt()));
    }

    /**
     * {@code window}'s strip in its parent-tabs view's coordinates: the strip while it shows, else the band at the view's
     * top that it takes when it shows.
     */
    private static Rectangle barArea(WindowContent window) {
        Component bar = window.parentTabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }

    /**
     * Whether the registry would move every item of the dragged tab: none pinned or being closed
     * ({@code TabRegistry.movable}; workspace plan 7b Rulings 7).
     */
    private boolean movable() {
        Optional<WorkspaceView> view = from.parentTabs().view(parentTab);
        return view.isPresent() && view.get().tabs().stream().allMatch(from.tabRegistry()::movable);
    }

    /** Whether a release outside every window takes the tab out of its window: never a full window's only tab. */
    private boolean tearsOut() { return from.mayPopOutParentTab(parentTab); }

    /** The strip's entries in {@code window}'s parent-tabs view's coordinates, in strip order, without the dragged one's own. */
    private List<Rectangle> barEntries(WindowContent window) {
        TabStrip bar = window.parentTabBar();
        List<Tab> listed = window.parentTabs().bar().tabs();
        List<Rectangle> bounds = bar.tabBounds();
        var entries = new ArrayList<Rectangle>();
        for (int i = 0; i < Math.min(listed.size(), bounds.size()); i++) {
            if (listed.get(i) == tab) continue;
            entries.add(SwingUtilities.convertRectangle(bar.component(), bounds.get(i), window.parentTabs()));
        }
        return entries;
    }

    /**
     * Shows the target's landing on its window's layered pane: a bar where the tab would go in the strip as the
     * placeholder, or the band of a strip that hides, or the main window's whole empty workspace, as the highlight.
     * Without a target in a window the overlay leaves its window.
     */
    private void showLanding() {
        Target.Bar bar = target.orElse(null) instanceof Target.Bar into ? into : null;
        WindowContent window = bar == null ? null : bar.window();
        JLayeredPane layers = window == null ? null : layers(window);
        Container parent = overlay.getParent();
        if (parent != null && parent != layers) {
            overlay.clear();
            parent.remove(overlay);
            parent.repaint();
        }
        if (layers == null) return;
        int marker = NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR);
        Rectangle highlight = null, placeholder = null;
        ParentTabsView parents = window.parentTabs();
        // The main window's empty workspace gives way to the tab: all of it shows.
        if (parents.isEmpty())
            highlight = SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()), layers);
        // A strip that hides takes the tab at its end: its band shows (workspace plan 7c Rulings 5).
        else if (!window.parentTabBar().component().isVisible())
            highlight = SwingUtilities.convertRectangle(parents, barArea(window), layers);
        else placeholder = SwingUtilities.convertRectangle(parents,
            WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, null, null);
    }

    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() {
        boolean wasDragging = dragging;
        dragging = false;
        from = null;
        parentTab = null;
        tab = null;
        source = null;
        pressedAt = null;
        grab = null;
        size = null;
        target = Optional.empty();
        if (closing != null) {
            closing.close();
            closing = null;
        }
        if (changed != null) {
            changed.close();
            changed = null;
        }
        if (shown != null) {
            shown.close();
            shown = null;
        }
        if (!wasDragging) return;
        if (keys != null) {
            keys.removeKeyEventDispatcher(escape);
            keys = null;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(lostRelease);
        label.hide();
        showLanding();
    }

    /**
     * Carries a release out; nothing touches an item's content or session. Along its own strip the tab takes its new
     * place there; on another window's strip, or out of every window into a full window, the window manager moves it
     * whole ({@code home}, the drag's window, holds {@code parent}). A window no manager opened moves nothing out of
     * itself.
     */
    private void drop(Tab moving, WindowContent home, ParentTabId parent, Target where) {
        if (moving.closed()) return;
        switch (where) {
            case Target.Bar bar -> {
                if (bar.window() == home) home.reorderParentTab(parent, bar.index());
                // Another window's strip: the window manager moves the tab whole, through the tab registry.
                else home.manager().ifPresent(manager -> manager.moveParentTab(home, parent, bar.window(), bar.index()));
            }
            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.popOutParentTab(home, parent, out.bounds(), out.cursor()));
        }
    }

    /**
     * A tear-out's workspace on screen: the size of the tab's workspace, with the entry where the cursor holds it, as
     * IntelliJ and VS Code size a window from the content dragged out of it. The new frame grows it by its own title bar
     * and keeps itself on the display under the cursor ({@code WindowFrame}).
     */
    private Rectangle tearOutBounds(Point at) {
        return new Rectangle(at.x - grab.x, at.y - grab.y, size.width, size.height);
    }

    /** The frames on screen of Jasper's showing windows the drag does not reach, but the label's: see {@link #otherWindows}. */
    private List<Rectangle> auxiliaryFrames() {
        var reached = new HashSet<Component>();
        for (WindowContent window : windows.get()) reached.add(outermost(window));
        var frames = new ArrayList<Rectangle>();
        for (Window window : Window.getWindows())
            if (window.isShowing() && !reached.contains(window) && !label.owns(window)) frames.add(window.getBounds());
        return frames;
    }

    /** Where the label's corner goes for the cursor at {@code at}. */
    private static Point labelAt(Point at) {
        int offset = UIScale.scale(LABEL_OFFSET);
        return new Point(at.x + offset, at.y + offset);
    }

    /** Where {@code event} happened, on screen. */
    private static Point screen(MouseEvent event) {
        Point point = new Point(event.getPoint());
        SwingUtilities.convertPointToScreen(point, event.getComponent());
        return point;
    }

    /** {@code window}'s native window, or for content in no native window (a headless test's) its outermost component. */
    private static Component outermost(WindowContent window) {
        Component top = window;
        while (!(top instanceof Window) && top.getParent() != null) top = top.getParent();
        return top;
    }

    /** The display {@code window}'s native window is on; null for content in no native window. */
    private static GraphicsDevice device(WindowContent window) {
        GraphicsConfiguration display = outermost(window) instanceof Window frame ? frame.getGraphicsConfiguration() : null;
        return display == null ? null : display.getDevice();
    }

    /**
     * {@code window}'s frame on screen, title bar and all, where it can take a drop: its native window's bounds (empty while
     * it does not show, is minimized, or by {@link #dropFrame} is out of sight in full screen), or its outermost component's
     * for content in no native window. {@code home} is the display of the drag's own window.
     */
    private static Rectangle onScreen(WindowContent window, boolean source, GraphicsDevice home) {
        Component top = outermost(window);
        if (top instanceof Window frame) {
            if (!frame.isShowing() || frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
                return new Rectangle();
            GraphicsConfiguration display = frame.getGraphicsConfiguration();
            return dropFrame(frame.getBounds(), display == null ? null : display.getBounds(), source,
                display != null && display.getDevice().equals(home), SystemInfo.isMacOS);
        }
        if (!top.isVisible()) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }

    /**
     * Where a window takes drops. On macOS a window whose frame is exactly its display's bounds is in full screen (which
     * covers the menu bar too), on a Space of its own. On the display of the drag's own window the user cannot see that
     * Space while dragging from another window: a window other than the drag's own takes none there. Each display shows a
     * Space of its own (macOS's default, "Displays have separate Spaces"), so on another display a window in full screen
     * can be in sight: it takes drops. Java sees no other Space, so a window on one that is not in full screen still takes
     * drops where its frame lies. Elsewhere a window covering its display is in sight (a tiling window manager's), so the
     * rule is macOS's alone.
     *
     * @param frame the window's frame on screen
     * @param display its display's bounds, or null when unknown
     * @param source whether it is the drag's own window
     * @param onSourceDisplay whether it is on the display of the drag's own window
     * @param macOs whether this is macOS
     * @return the frame, or an empty rectangle where it takes no drop
     */
    static Rectangle dropFrame(Rectangle frame, Rectangle display, boolean source, boolean onSourceDisplay, boolean macOs) {
        if (macOs && !source && onSourceDisplay && frame.equals(display)) return new Rectangle();
        return new Rectangle(frame);
    }

    private static JLayeredPane layers(WindowContent window) {
        JRootPane root = SwingUtilities.getRootPane(window);
        return root == null ? null : root.getLayeredPane();
    }
}
```

A pane's strip starts no drag, and the workspace loses its drops:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "GroupView.java", [
    (""" * group's selected tab. The strip shows unless its workspace hides it (parent tabs spec 7: a pane's single tab in the
 * standard look), and the card area then fills the group. The strip lists, selects, closes and opens tabs in this group
 * only and starts the tab drag (spec 12). A tab's component enters and leaves the card area as the layout puts the tab in
""", """ * group's selected tab. The strip shows unless its workspace hides it (parent tabs spec 7: a pane's single tab in the
 * standard look), and the card area then fills the group. The strip lists, selects, closes and opens tabs in this group
 * only and starts no drag: a tab drags from its window's parent-tab bar (tabs and panes spec 6). A tab's component
 * enters and leaves the card area as the layout puts the tab in
""", 1),
    ("""    /**
     * A left press on {@code tab}'s entry in the strip: the tab drag may start from it (workspace tabs spec 12). A dock
     * drag still running lost its release, so it ends first.
     */
    @Override public void pressed(Tab tab, MouseEvent event) {
        owner.dockDrag().cancel();
        owner.tabDrag().pressed(this, tab, event);
    }
    /** A drag event from the strip, which the tab drag follows. */
    @Override public void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }
    /** A release on the strip, which ends the tab drag. */
    @Override public void released(MouseEvent event) { owner.tabDrag().released(event); }
""", """    /**
     * A left press on {@code tab}'s entry in the strip: a dock drag still running lost its release, so it ends. A pane's
     * strip starts no tab drag (tabs and panes spec 6).
     */
    @Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }
    /** A pane's strip drags nothing. */
    @Override public void dragged(MouseEvent event) { }
    /** A pane's strip drags nothing. */
    @Override public void released(MouseEvent event) { }
""", 1),
    ("""    /** The strip's rectangle in {@code target}'s coordinates (workspace plan 5 resolves drops over it); empty while it is hidden. */
""", """    /** The strip's rectangle in {@code target}'s coordinates; empty while it is hidden. */
""", 1),
    ("""    /**
     * Each tab's rectangle in the strip, in strip order, in {@code target}'s coordinates: a tab drag drops between them.
     * None while the strip is hidden.
     */
""", """    /** Each tab's rectangle in the strip, in strip order, in {@code target}'s coordinates; none while the strip is hidden. */
""", 1)])
V = W + "WorkspaceView.java"
edit(V, [
    ("import dev.jasper.app.dock.WorkspaceDropResolver.Area;\nimport dev.jasper.app.dock.WorkspaceDropResolver.Drop;\n", "", 1),
    ("import java.awt.Rectangle;\n", "", 1)])
span(V, """    /**
     * The groups a tab drag can drop on, in this view's coordinates (workspace tabs spec 12): each group that shows (the
""", """            case Drop.Edge edge -> moveToNewGroup(tab, edge.group(), edge.side());
        }
    }

""", "")
edit(W + "TabStrip.java", [(
    """ * {@link StripSource} only, its presses start the tab drag (workspace tabs spec 12; from the bar, of a whole parent tab:
 * parent tabs spec 5), and it shows as active while its group is the active group of an active window, or while the
 * bar's window is active. EDT only.
""", """ * {@link StripSource} only, a press on the bar's entries starts the tab drag of a whole parent tab (tabs and panes spec
 * 6) while a pane's strip starts none, and it shows as active while its group is the active group of an active window,
 * or while the bar's window is active. EDT only.
""", 1)])
edit(W + "WindowContent.java", [(
    """    /**
     * Drags a tab, or a parent tab from the bar, from any of this window's strips (workspace tabs spec 12; parent tabs
     * spec 5): the window manager's, which every window it opens shares; until it gives that, one of this window's own,
     * which drops within this window and moves nothing out of it: outside this window it reports a TearOut target for a
     * tab only (a parent tab's needs a manager, {@link #mayPopOutParentTab}), which a window no manager opened ignores
     * (workspace plan 5 Rulings 10).
     */
""", """    /**
     * Drags a parent tab from the bar (tabs and panes spec 6): the window manager's, which every window it opens shares;
     * until it gives that, one of this window's own, which reorders the bar and moves nothing out of this window, as a
     * tear-out needs a manager ({@link #mayPopOutParentTab}).
     */
""", 1)])
edit(W + "WindowManager.java", [(
    """     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}: a tab the tab drag releases outside every window too (parent tabs spec 5).
""", """     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}.
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.dock.*' --continue`
Expected: PASS. `WorkspaceDropTest` is gone; `TabDragTest`, `TabDragTestSupport` and `TabDragWindowsTest` are the
files above; `WorkspaceDropResolverTest` is unchanged. Then `./gradlew :jasper-app:check`: PASS (the ledger test sees
`WorkspaceDropTest`'s row gone).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: the item drag goes; a tab drags from its strip alone

The tab drag carries a whole tab from its window's tab strip: along
the strip, onto another window's strip and out of every window. The
item drag's strip, centre and edge drops and its tear-out go, a
pane's strip starts no drag, and the workspace loses its drop areas.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Pane windows go; Move Tab to New Window and the window menu

Spec 4, 6 and 13: pane windows go with their origins, Pop Out Tab, Pop Out Pane and Bring Back (Rulings 5);
`ContentTab.moveToNewWindow()` opens a full window holding the item (Rulings 6); plan 7b's Pop Out Parent Tab becomes
Move Tab to New Window, from two tabs (Rulings 6, Spec issues 1); the ⋮ menu and a terminal's context menu offer Move
Tab to New Window and, in a full window, Move to Main Window (Rulings 7). `workspace.toml` loses its `pane` role (6a
impact). Items still stack in a pane until Task 4.

**Files:**
- Delete: `jasper-app/src/main/java/dev/jasper/app/layout/Origin.java`, `PaneLayout.java`, `WindowLayout.java`;
  `jasper-app/src/test/java/dev/jasper/app/layout/OriginTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/workspace/BringBackTest.java`, `PopOutTest.java`, `PopOutCommandsTest.java`
  (and their ledger rows)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/MoveTabsTest.java`, `WindowMenuTest.java`
- Modify: `layout/ParentTabs.java` (`:18`, `:131-132`), `layout/Layout.java` (`origin` to `parent` `:381-461`),
  `layout/package-info.java`; `persistence/WorkspaceFile.java` (imports, class Javadoc, `Role`, `WindowRecord`, `read`'s
  Javadoc, `window`, `origin`, `keeping`, `groupIds`, `parentTabIds`, `NO_ORIGIN`, `encode`);
  `contributions/TabRegistry.java` (Javadoc `:63`, `:83`, `:95`, `:353-372`), `contributions/Tab.java` (`:122`);
  `commands/ActionId.java` (`:18-21`); `shortcuthelp/ShortcutCatalog.java` (`category`);
  `workspace/WindowContent.java`, `WindowManager.java`, `WorkspaceActions.java`, `WindowCommands.java`,
  `WindowChrome.java` (`contextMenu`), `ParentTabBar.java` (`refresh`), `TabDrag.java` (Task 1's `onBar`, `tearsOut`,
  `drop`, class Javadoc); `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java` (`moveToNewWindow`'s Javadoc)
- Test: `layout/ParentTabsTest.java`, `persistence/WorkspaceFileTest.java`, `WorkspaceFileReadTest.java`,
  `config/KeymapDefaultsTest.java`, `config/KeyBindingsTest.java`, `config/ConfigLoaderTest.java`,
  `shortcuthelp/ShortcutHelpTest.java`, `workspace/MovesFixture.java`, `WindowManagerTest.java`, `WorkspaceSnapshotTest.java`, `WorkspaceSavingTest.java`,
  `ParentTabBarTest.java`, `TabMenuTest.java`, `TerminalContextMenuTest.java`, `WindowMenuButtonTest.java`,
  `TabDragWindowsTest.java` (Task 1's)

**Interfaces:**
- Consumes: Task 1's `TabDrag`, `TabDragWindowsTest`; plan 7b's `moveParentTab`, `moveToMainWindow`, `receive`,
  `ParentTabsView.renew` and `reid`, `MovesFixture.pinWhenItLeaves`, `refuseItsMove`.
- Produces:
  - `WindowManager.moveToNewWindow(Tab tab)` → `Optional<ChildWindow>` (the registry's `Windows.newWindow`);
    `WindowManager.moveTabToNewWindow(WindowContent window, ParentTabId parentTab)` and
    `moveTabToNewWindow(WindowContent, ParentTabId, Rectangle bounds, Point cursor)` → `Optional<ChildWindow>`;
  - `WindowContent.mayMoveTabToNewWindow(ParentTabId)`, `moveTabToNewWindow(Tab)` (the tab holding it),
    `public ParentTabs windowLayout()`, `windowMenuItems()` → `[MOVE_TAB_TO_NEW_WINDOW]` or
    `[MOVE_TAB_TO_NEW_WINDOW, MOVE_TO_MAIN_WINDOW]`;
  - `ActionId.MOVE_TAB_TO_NEW_WINDOW` (`move_tab_to_new_window`, "Move Tab to New Window", no keys);
  - `WorkspaceFile.WindowRecord(Role, Optional<UiState.Bounds>, int, ParentTabs layout, Map<TabId, TabRecord>)`,
    `WorkspaceFile.keeping(ParentTabs, Predicate<TabId>, boolean)` → `Optional<ParentTabs>`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/MoveTabsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 6 and 13: Move Tab to New Window, Move to Main Window and an item's move into a window of its own;
 * what moves whole, and what stays.
 */
class MoveTabsTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 6 and workspace plan 7b Rulings 10: a tab moves whole into a full window, with its id and layout. */
    @Test void moveTabToNewWindowMovesItWholeToAFullWindow() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            ParentTabId first = content.activeParentTab();
            content.workspace().resizeSplit(List.of(), List.of(0.3, 0.7));
            Layout before = content.workspace().model();
            List<Tab> moving = content.tabs();
            var sessions = moving.stream().map(MovesFixture::session).toList();
            content.newParentTab(HOME);
            ParentTabId second = content.activeParentTab();
            windows.facts.clear();
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().windowLayout()).isEqualTo(ParentTabs.of(new ParentTab(first, before)));
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(second);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), full.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
            assertThat(windows.manager.moveTabToNewWindow(full.content(), first)).as("a full window's only tab stays").isEmpty();
        });
        windows.launch();
    }

    /** This plan's Rulings 6 (Spec issues 1): the main window's only tab stays too, and no window opens for it. */
    @Test void theMainWindowsOnlyTabStays() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, content.activeParentTab())).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.tabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Spec 6 and workspace plan 7b Rulings 12: a full window's tabs join the main window's strip after its shown one. */
    @Test void moveToMainWindowJoinsTheTabsAfterTheShownOne() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId m1 = main.activeParentTab();
            main.newParentTab(HOME);
            ParentTabId m2 = main.activeParentTab();
            main.selectParentTab(m1);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            var content = full.content();
            ParentTabId p1 = content.activeParentTab();
            content.newParentTab(HOME);
            content.split(Side.RIGHT);
            ParentTabId p2 = content.activeParentTab();
            var layouts = content.windowLayout().parentTabs();
            int tabs = content.tabCount();
            windows.facts.clear();
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            var parents = main.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(m1, p1, p2, m2);
            assertThat(parents.parentTabs().subList(1, 3)).as("each with its layout").containsExactlyElementsOf(layouts);
            assertThat(main.activeParentTab()).as("the tab it showed").isEqualTo(p2);
            assertThat(windows.tabFacts()).hasSize(tabs).allMatch(TerminalEvent.TabMoved.class::isInstance);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 12: the main window's empty workspace gives way to the first tab. */
    @Test void moveToMainWindowFillsAnEmptyMainWorkspace() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            ParentTabId p1 = full.content().activeParentTab();
            windows.manager.moveToMainWindow(full.content());
            assertThat(main.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(p1);
            assertThat(main.tabCount()).isEqualTo(1);
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            assertThat(main.activeParentTab()).as("the tab it showed").isEqualTo(p1);
        });
        windows.launch();
    }

    /** Review Focus 1 of plan 7b: a pinned item keeps its tab whole, and nothing moves. */
    @Test void aPinnedItemKeepsItsTabWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.newParentTab(HOME);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            Subscription held = content.tabs().getFirst().pin();
            windows.facts.clear();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isEmpty();
            assertThat(windows.manager.moveParentTab(content, first, full.content(), 0)).as("nor onto another window").isFalse();
            assertThat(windows.manager.children()).containsExactly(full);
            assertThat(windows.tabFacts()).isEmpty();
            held.close();
            assertThat(windows.manager.moveTabToNewWindow(content, first)).isPresent();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab never moves whole within its own window. */
    @Test void aTabMovesNotWithinItsOwnWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.newParentTab(HOME);
            windows.facts.clear();
            assertThat(windows.manager.moveParentTab(content, first, content, 0)).isFalse();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 10: a tab moving whole keeps its layout, its maximize included. */
    @Test void aMaximizedTabMovesStillMaximized() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            content.toggleMaximize();
            Layout before = content.workspace().model();
            GroupId maximized = before.maximized().orElseThrow();
            content.newParentTab(HOME);
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().workspace().model().maximized()).as("on the same pane").contains(maximized);
            assertThat(full.content().workspace().model()).isEqualTo(before);
        });
        windows.launch();
    }

    /** The empty workspace a tab moving whole leaves behind takes the window's theme and glass, as a new one does. */
    @Test void theRenewedEmptyWorkspaceTakesTheWindowsThemeAndGlass() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.applyBackgroundOpacity(0.5);
            ParentTabId only = content.activeParentTab();
            content.split(Side.RIGHT);
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.moveParentTab(content, only, full.content(), 1)).isTrue();
            WorkspaceView renewed = content.workspace();
            assertThat(renewed.id()).as("the renewed workspace").isNotEqualTo(only);
            assertThat(renewed.isOpaque()).as("under glass it paints nothing behind its panes").isFalse();
            assertThat(renewed.getBackground()).as("the theme's surface").isEqualTo(content.theme().surface());
            content.split(Side.RIGHT);
            content.split(Side.RIGHT);
            SplitContainer split = renewed.split(List.of());
            assertThat(split).as("a split it builds later").isNotNull();
            assertThat(split.isOpaque()).isFalse();
            assertThat(split.getBackground()).isEqualTo(content.theme().surface());
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): a listener pins the tab's second item as its first moves. What arrived takes
     * a new tab id; what stayed keeps its ids; and Move Tab to New Window gives the full window holding what arrived.
     */
    @Test void aTabThatMovesInPartLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            GroupId stays = moving.get(1).group().orElseThrow();
            content.newParentTab(HOME);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(moving.get(0), moving.get(1));
            ChildWindow full = windows.manager.moveTabToNewWindow(content, first).orElseThrow();
            assertThat(full.content().tabs()).as("what arrived").containsExactly(moving.get(0));
            assertThat(full.content().activeParentTab()).as("under a new id").isNotEqualTo(first);
            assertThat(content.parentTabOf(moving.get(1))).as("what stayed keeps its tab").contains(first);
            assertThat(content.parentTabs().view(first).orElseThrow().model().groups()).extracting(Group::id).containsExactly(stays);
            windows.assertNoIdInTwoWindows(content.tabs());
            windows.assertNoIdInTwoWindows(moving);
            pin[0].close();
        });
        windows.launch();
    }

    /** As above, the second item's move failing: the failure propagates, and what arrived stays in the full window. */
    @Test void aTabWhoseSecondItemFailsToMoveLeavesNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            ParentTabId first = content.activeParentTab();
            List<Tab> moving = content.workspace().tabs();
            content.newParentTab(HOME);
            var refusal = MovesFixture.refuseItsMove(moving.get(1));
            assertThatThrownBy(() -> windows.manager.moveTabToNewWindow(content, first)).isSameAs(refusal);
            assertThat(windows.manager.children()).singleElement().satisfies(full -> {
                assertThat(full.content().tabs()).containsExactly(moving.get(0));
                assertThat(full.content().activeParentTab()).isNotEqualTo(first);
            });
            assertThat(content.parentTabOf(moving.get(1))).contains(first);
            windows.assertNoIdInTwoWindows(moving);
        });
        windows.launch();
    }

    /**
     * Parent tabs spec 4 (7b's final review): Move to Main Window with the shown tab moving in part. Its part that arrived
     * joins the strip under a new id, shows, and the next tab follows it; the full window keeps the rest.
     */
    @Test void tabsThatMoveToTheMainWindowInPartLeaveNoIdInTwoWindows() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId m1 = main.activeParentTab();
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            var content = full.content();
            content.split(Side.RIGHT);
            ParentTabId p1 = content.activeParentTab();
            List<Tab> split = content.workspace().tabs();
            content.newParentTab(HOME);
            ParentTabId p2 = content.activeParentTab();
            content.selectParentTab(p1);
            Subscription[] pin = MovesFixture.pinWhenItLeaves(split.get(0), split.get(1));
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            ParentTabId arrived = main.parentTabOf(split.get(0)).orElseThrow();
            assertThat(arrived).as("a new id").isNotEqualTo(p1);
            assertThat(main.windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(m1, arrived, p2);
            assertThat(main.activeParentTab()).as("what arrived of the tab the full window showed").isEqualTo(arrived);
            assertThat(full.closed()).isFalse();
            assertThat(content.tabs()).containsExactly(split.get(1));
            windows.assertNoIdInTwoWindows(main.tabs());
            windows.assertNoIdInTwoWindows(split);
            pin[0].close();
        });
        windows.launch();
    }

    /** A pane the planned layout lacks, one that opened meanwhile, stays in the tab; the plan still lands. */
    @Test void rearrangingKeepsAPaneThePlanLacks() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            WorkspaceView workspace = content.workspace();
            Tab planned = content.selectedTab();
            Layout plan = workspace.model().resize(List.of(), List.of(0.3, 0.7));
            content.split(Side.DOWN);
            Tab extra = content.selectedTab();
            workspace.rearrange(plan);
            Layout layout = workspace.model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::weight).as("the plan landed").containsExactly(0.3, 0.7));
            assertThat(layout.group(plan.activeGroup()).orElseThrow().tabs()).as("in its active pane")
                .containsExactly(planned.id(), extra.id());
            assertThat(content.selectedTab()).as("the plan's active item").isSameAs(planned);
            assertThat(content.tabs()).hasSize(3).contains(extra);
        });
        windows.launch();
    }

    /** Spec 13 and Review Focus 5 of plan 7b: an item leaves for a full window of its own; one TAB_MOVED; focus follows. */
    @Test void moveToNewWindowTakesOneItemToAFullWindowOfItsOwn() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            Tab tab = content.selectedTab();
            var running = session(tab);
            int[] focused = {0};
            tab.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            ChildWindow child = windows.manager.moveToNewWindow(tab).orElseThrow();
            assertThat(child.content().parentTabCount()).as("a full window, its tab holding the item").isEqualTo(1);
            assertThat(child.content().tabs()).containsExactly(tab);
            assertThat(windows.tabFacts()).as("one move; no tab opened or closed").isEqualTo(moved(List.of(tab), main[0].id(), child.id()));
            assertThat(session(tab)).isSameAs(running);
            assertThat(running.exitFuture()).isNotDone();
            assertThat(focused[0]).as("the item has the focus in its window").isPositive();
            assertThat(content.tabCount()).isEqualTo(1);
            assertThat(content.workspace().model().groups()).as("its pane went with it").hasSize(1);
        });
    }

    /** Workspace plan 7b Rulings 8: a child window's only item stays, as that window already is its own. */
    @Test void aChildWindowsOnlyItemStays() throws Exception {
        edt(() -> {
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.moveToNewWindow(full.content().selectedTab())).isEmpty();
            assertThat(windows.manager.children()).containsExactly(full);
        });
        windows.launch();
    }

    /** The main window's only item may leave, leaving its empty workspace. */
    @Test void theMainWindowsOnlyItemMovesAndLeavesItsEmptyWorkspace() throws Exception {
        edt(() -> {
            MainWindow main = windows.main();
            var content = main.content();
            assertThat(windows.manager.moveToNewWindow(content.selectedTab())).isPresent();
            assertThat(main.closed()).isFalse();
            assertThat(content.tabCount()).isZero();
            assertThat(content.workspace().emptyWorkspace()).as("its empty workspace shows").isNotNull();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: an item whose close waits for its guard stays, and no window opens for it even briefly. */
    @Test void anItemBeingClosedOrPinnedOpensNoWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.txt", content.id(), new JPanel());
            Subscription pin = content.tabs().getFirst().pin();
            windows.facts.clear();
            assertThat(windows.manager.moveToNewWindow(content.tabs().getFirst())).as("a pinned item").isEmpty();
            pin.close();
            var answer = new CompletableFuture<Boolean>();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var closing = notes.requestClose();
            assertThat(closing).as("the guard has not answered").isNotDone();
            assertThat(windows.manager.moveToNewWindow(notes)).isEmpty();
            assertThat(windows.facts).as("no window opened and closed again").noneMatch(TerminalEvent.WindowOpened.class::isInstance);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(content.tabs()).contains(notes);
            answer.complete(false);
            assertThat(closing).isCompletedWithValue(false);
            assertThat(windows.manager.moveToNewWindow(notes)).as("once the close is refused, it may go").isPresent();
        });
        windows.launch();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowMenuTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 6: the window menu of each kind of window, the menus that offer its entries, and its commands. */
class WindowMenuTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** A menu's entries by their text, with "---" for a rule. */
    private static List<String> texts(JPopupMenu menu) {
        var texts = new ArrayList<String>();
        for (var child : menu.getComponents()) texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        return texts;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(text)) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** Spec 6 and this plan's Rulings 6 and 7: each kind of window has its entries, enabled as its actions are. */
    @Test void theWindowMenuOffersEachKindOfWindowItsEntries() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(main.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            assertThat(texts(main.windowMenu())).containsExactly("Move Tab to New Window");
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("the main window's only tab stays").isFalse();
            main.newParentTab(HOME);
            main.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("from two tabs").isTrue();
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(full.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(texts(full.windowMenu())).containsExactly("Move Tab to New Window", "Move to Main Window");
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("a full window's only tab stays").isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            full.newParentTab(HOME);
            full.updateActions();
            assertThat(full.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 7: while an item they would move is pinned, the commands are disabled wherever they are offered. */
    @Test void aPinnedItemDisablesTheCommandsThatWouldMoveIt() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newParentTab(HOME);
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            full.newParentTab(HOME);
            Tab inMain = main.selectedTab();
            Tab inFull = full.selectedTab();
            List<Subscription> held = List.of(inMain.pin(), inFull.pin());
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isFalse();
            assertThat(main.commands().find("move_tab_to_new_window").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(main.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
            assertThat(item(main.parentTabMenu(inMain), "Move Tab to New Window").isEnabled()).isFalse();
            assertThat(item(full.parentTabMenu(inFull), "Move to Main Window").isEnabled()).isFalse();
            held.forEach(Subscription::close);
            for (var window : List.of(main, full)) window.updateActions();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(item(full.parentTabMenu(inFull), "Move to Main Window").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** A window no window manager opened cannot move its tabs, so every entry of its menus shows disabled. */
    @Test void aWindowWithoutAManagerDisablesItsWindowMenuEntries() throws Exception {
        edt(() -> {
            var main = content(launcher(new ArrayDeque<>()));
            main.newParentTab(HOME);
            var full = content(launcher(new ArrayDeque<>()), WindowRole.CHILD, HOME, false, () -> { });
            full.newParentTab(HOME);
            full.split(Side.RIGHT);
            assertThat(full.windowMenuItems()).containsExactly(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
            for (var window : List.of(main, full)) {
                window.updateActions();
                for (ActionId id : window.windowMenuItems()) assertThat(window.action(id).isEnabled()).as("%s", id).isFalse();
                assertThat(window.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
                var menu = window.parentTabMenu(window.selectedTab());
                for (ActionId id : window.windowMenuItems()) assertThat(item(menu, id.label()).isEnabled()).as("its tab's %s", id).isFalse();
            }
        });
    }

    /** Spec 6: in the main window's empty workspace the window menu's entry shows disabled. */
    @Test void theMainWindowsEmptyWorkspaceShowsItsWindowMenuEntryDisabled() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            assertThat(main.workspace().emptyWorkspace()).as("the empty workspace").isNotNull();
            assertThat(texts(main.windowMenu())).containsExactly("Move Tab to New Window");
            assertThat(main.windowMenu().getComponents()).noneMatch(java.awt.Component::isEnabled);
            for (ActionId id : main.windowMenuItems()) assertThat(main.action(id).isEnabled()).as("%s", id).isFalse();
        });
        windows.launch();
    }

    /** Spec 6: the actions move the shown tab to a new window, and a full window's tabs back. */
    @Test void theActionsMoveATabToANewWindowAndBack() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newParentTab(HOME);
            ParentTabId moving = main.activeParentTab();
            main.invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).isEqualTo(moving);
            assertThat(main.parentTabCount()).isEqualTo(1);
            full.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(full.closed()).isTrue();
            assertThat(main.parentTabCount()).isEqualTo(2);
            assertThat(main.activeParentTab()).isEqualTo(moving);
        });
        windows.launch();
    }

    /** Spec 6: the strip's entry menu acts on its own tab, though another one shows. */
    @Test void theStripsEntryMenuActsOnItsOwnTab() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            ParentTabId first = main.activeParentTab();
            Tab left = main.selectedTab();
            main.newParentTab(HOME);
            var menu = main.parentTabMenu(left);
            assertThat(texts(menu)).containsExactly("Move Tab to New Window", "---", "Close Parent Tab");
            item(menu, "Move Tab to New Window").doClick();
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).as("the tab the entry stands for").isEqualTo(first);
            assertThat(full.content().tabs()).containsExactly(left);
            assertThat(main.parentTabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 6 and this plan's Rulings 7: a terminal's context menu ends with the window menu's entries. */
    @Test void aTerminalsContextMenuEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(texts(main.chrome().contextMenu())).endsWith("---", "Move Tab to New Window");
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(texts(full.chrome().contextMenu())).endsWith("---", "Move Tab to New Window", "Move to Main Window");
        });
        windows.launch();
    }

    /** Spec 10: the commands are in the palette under their ids; the old ids are gone, a clean break. */
    @Test void theCommandsAreInThePaletteUnderTheirIds() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            for (String id : List.of("move_tab_to_new_window", "move_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isPresent();
            for (String id : List.of("pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isEmpty();
            assertThat(main.action(ActionId.MOVE_TAB_TO_NEW_WINDOW).getValue(Action.NAME)).isEqualTo("Move Tab to New Window");
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).getValue(Action.NAME)).isEqualTo("Move to Main Window");
        });
        windows.launch();
    }
}
```

The other tests follow: pane windows and their tests go, the window menu and the moves are the new files' and these
edits', and the codec's shared snapshot holds a full window where it held a pane window:

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan8a"); from edits import edit, span, regex; import ledger
T = "jasper-app/src/test/java/dev/jasper/app/"
W = T + "workspace/"
pathlib.Path(T + "layout/OriginTest.java").unlink()
for gone, successor in (("BringBackTest", "MoveTabsTest"), ("PopOutTest", "MoveTabsTest"), ("PopOutCommandsTest", "WindowMenuTest")):
    pathlib.Path(W + gone + ".java").unlink()
    ledger.drop(W + gone + ".java", W + successor + ".java")
ledger.add(W + "MoveTabsTest.java", "app.workspace", "MoveTabsTest", W + "MoveTabsTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8a Task 2; Move Tab to New Window, Move to Main Window and an item's move")
ledger.add(W + "WindowMenuTest.java", "app.workspace", "WindowMenuTest", W + "WindowMenuTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8a Task 2; the window menu and the menus that offer its entries")
edit(T + "layout/ParentTabsTest.java", [(
    """    @Test void aWindowHoldsParentTabsOrOnePaneLayout() {
        WindowLayout full = ParentTabs.of(parent(A, B));
        WindowLayout pane = new PaneLayout(Layout.of(C), new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5));
        for (WindowLayout layout : List.of(full, pane)) {
            List<TabId> tabs = switch (layout) {
                case ParentTabs parents -> parents.parentTabs().getFirst().layout().tabs();
                case PaneLayout single -> single.layout().tabs();
            };
            assertThat(layout.tabs()).isEqualTo(tabs);
        }
        assertThat(full.tabs()).containsExactly(A, B);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneLayout(Layout.empty(),
            new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5))).withMessageContaining("pane window");
    }
""", """    @Test void aWindowsTabsAreEachParentTabsInBarOrder() {
        ParentTab first = parent(A, B), second = parent(C);
        assertThat(new ParentTabs(List.of(first, second), second.id()).tabs()).containsExactly(A, B, C);
    }
""", 1)])
P = T + "persistence/"
edit(P + "WorkspaceFileTest.java", [
    ("import dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\n", "", 1),
    ("import dev.jasper.app.layout.WindowLayout;\n", "", 1),
    ("    static Map<TabId, TabRecord> terminals(WindowLayout layout) {\n", "    static Map<TabId, TabRecord> terminals(ParentTabs layout) {\n", 1),
    ("""    /** Where {@link #snapshot()}'s pane window came from: left of group 1 in parent tab 1, a quarter of their space. */
    static final Origin ORIGIN = new Origin(parentTabId(1), groupId(1), Side.LEFT, 0.25);

""", "", 1),
    ("""     * maximized; and a pane window without bounds. Listed pane window first: the snapshot puts the main window first.
""", """     * maximized; and a full window without bounds. Listed full window first: the snapshot puts the main window first.
""", 1),
    ("""        var pane = new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(new Layout(group(4, 5, 5), groupId(4), Optional.empty()),
            ORIGIN), Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(pane, main));
""", """        var full = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(5, new Layout(group(4, 5, 5), groupId(4), Optional.empty())),
            Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(full, main));
""", 1),
    ("    @Test void writesSpecEightsWorkspaceWithAPaneWindowExactly() throws Exception {\n",
     "    @Test void writesSpecEightsWorkspaceWithAFullWindowExactly() throws Exception {\n", 1),
    ("""            [[windows]]
            role = "pane"
            extended_state = 0
            origin = { parent_tab = "00000000-0000-4000-a000-000000000001", pane = "00000000-0000-4000-9000-000000000001", side = "left", share = 0.25 }
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.nodes]]
""", """            [[windows]]
            role = "full"
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000005"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000005"
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.parent_tabs.nodes]]
""", 1),
    ("""        assertThatIllegalArgumentException().as("a main window holding a pane window's layout")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, new PaneLayout(Layout.of(tab(1)), ORIGIN), record));
""", "", 1),
    ("""        var pane = new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(Layout.of(tab(3)), ORIGIN),
            Map.of(tab(3), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(pane, child, main)).windows()).as("the main window first").containsExactly(main, pane, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main, pane)).children()).containsExactly(child, pane);
""", """        var other = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(3, Layout.of(tab(3))),
            Map.of(tab(3), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(other, child, main)).windows()).as("the main window first").containsExactly(main, other, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main, other)).children()).containsExactly(child, other);
""", 1),
    ("""            new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(sameGroup, ORIGIN), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
""", """            new WindowRecord(Role.CHILD, Optional.empty(), 0, one(4, sameGroup), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
""", 1),
    ("""        Layout two = single(4).split(tab(5), groupId(4), Side.RIGHT);
        assertThat(WorkspaceFile.keeping(new PaneLayout(two, ORIGIN), tab -> !tab.equals(tab(5)), false)).as("a pane window keeps its origin")
            .contains(new PaneLayout(two.remove(tab(5)), ORIGIN));
        assertThat(WorkspaceFile.keeping(new PaneLayout(two, ORIGIN), tab -> false, false)).isEmpty();
""", """        Layout two = single(4).split(tab(5), groupId(4), Side.RIGHT);
        assertThat(WorkspaceFile.keeping(one(4, two), tab -> !tab.equals(tab(5)), false)).as("a full window keeps the pane that stays")
            .contains(one(4, two.remove(tab(5))));
""", 1)])
edit(P + "WorkspaceFileReadTest.java", [
    ("import static dev.jasper.app.persistence.WorkspaceFileTest.ORIGIN;\n", "", 1),
    ("import dev.jasper.app.layout.PaneLayout;\n", "", 1),
    ("import dev.jasper.app.layout.WindowLayout;\n", "", 1),
    ("""    /** The layout a window shows: a main or full window's shown parent tab's, or a pane window's. */
    static Layout shown(WindowRecord window) {
        return switch (window.layout()) {
            case ParentTabs parents -> parents.activeParentTab().layout();
            case PaneLayout pane -> pane.layout();
        };
    }
""", """    /** The layout a window shows: its shown parent tab's. */
    static Layout shown(WindowRecord window) { return window.layout().activeParentTab().layout(); }
""", 1),
    ("        WindowLayout layout = window(Role.MAIN, 1, 2).layout();\n", "        ParentTabs layout = window(Role.MAIN, 1, 2).layout();\n", 1),
    (""".as("a pane window left empty").isEqualTo(mainOnly);""", """.as("a full window left empty").isEqualTo(mainOnly);""", 1)])
# The Java string role = \"pane\" (a backslash before each quote), twice in aBadTabEntryIsDroppedAlone and once in
# aSecondMainWindowAnEmptiedChildAndAWindowHoldingAnotherWindowsTabAreDropped: a raw regex, as the text holds backslashes.
regex(P + "WorkspaceFileReadTest.java", r'role = \\"pane\\"', r'role = \\"full\\"', 3)
span(P + "WorkspaceFileReadTest.java", "    @Test void aPaneWindowsUnreadableOriginFallsBackToOneNoWindowHolds() throws Exception {\n",
     "    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {\n",
     "    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {\n")
edit(T + "config/KeymapDefaultsTest.java", [(
    """        new Row("pop_out_tab", KeyContext.GLOBAL, "none", "none"),                            // parent tabs spec 8
        new Row("pop_out_pane", KeyContext.GLOBAL, "none", "none"),
        new Row("pop_out_parent_tab", KeyContext.GLOBAL, "none", "none"),
        new Row("bring_back", KeyContext.GLOBAL, "none", "none"),
""", """        new Row("move_tab_to_new_window", KeyContext.GLOBAL, "none", "none"),                 // tabs and panes spec 10
""", 1)])
edit(T + "config/KeyBindingsTest.java", [(
    "            ActionId.POP_OUT_TAB, ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.BRING_BACK,\n",
    "            ActionId.MOVE_TAB_TO_NEW_WINDOW,\n", 1)])
# move_tab_to_new_window is an action again (Rulings 6); the ids this task removes are the unknown ones now.
edit(T + "config/ConfigLoaderTest.java", [
    ("    /** Parent tabs spec 8: the old move ids are a clean break too, so each is an unknown action (workspace plan 7b). */\n",
     "    /** Tabs and panes spec 10: the pane-window and Pop Out ids are a clean break, so each is an unknown action. */\n", 1),
    ('        for (String id : new String[] {"move_tab_to_new_window", "move_tab_to_main_window"}) {\n',
     '        for (String id : new String[] {"pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window"}) {\n', 1)])
edit(T + "shortcuthelp/ShortcutHelpTest.java", [
    ("""        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1", "pop_out_tab",
                "pop_out_parent_tab"))
""", """        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1",
                "move_tab_to_new_window"))
""", 1),
    ("""        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "pop_out_pane"))
""", """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left"))
""", 1),
    ("""        for (String id : List.of("bring_back", "move_to_main_window", "new_terminal_window"))
""", """        for (String id : List.of("move_to_main_window", "new_terminal_window"))
""", 1)])
edit(W + "MovesFixture.java", [
    ("import dev.jasper.app.layout.PaneLayout;\n", "", 1),
    ("import dev.jasper.app.layout.WindowLayout;\n", "", 1),
    ("""            WindowLayout layout = window.content().windowLayout();
            held.addAll(layout.tabs());
            switch (layout) {
                case ParentTabs parents -> parents.parentTabs().forEach(parent -> {
                    parentTabs.add(parent.id());
                    parent.layout().groups().forEach(group -> panes.add(group.id()));
                });
                case PaneLayout pane -> pane.layout().groups().forEach(group -> panes.add(group.id()));
            }
""", """            ParentTabs layout = window.content().windowLayout();
            held.addAll(layout.tabs());
            layout.parentTabs().forEach(parent -> {
                parentTabs.add(parent.id());
                parent.layout().groups().forEach(group -> panes.add(group.id()));
            });
""", 1)])
edit(W + "WindowManagerTest.java", [
    ("import dev.jasper.app.layout.Origin;\n", "", 1),
    ("""            ChildWindow child = manager.popOutTab(tab).orElseThrow();
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(child.content().selectedTab()).isSameAs(tab);
            assertThat(main[0].content().tabCount()).as("the main window keeps its other tab").isEqualTo(1);
            assertThat(manager.popOutTab(tab)).as("a child window's only tab stays where it is").isEmpty();
""", """            ChildWindow child = manager.moveToNewWindow(tab).orElseThrow();
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(child.content().selectedTab()).isSameAs(tab);
            assertThat(main[0].content().tabCount()).as("the main window keeps its other tab").isEqualTo(1);
            assertThat(manager.moveToNewWindow(tab)).as("a child window's only tab stays where it is").isEmpty();
""", 1),
    ("""            ParentTabId parentTab = main.content().activeParentTab();
            GroupId pane = notes.group().orElseThrow();
            notes.moveToNewWindow();
            ChildWindow child = manager.children().getFirst();
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(manager.isMain(notes.window())).isFalse();
            assertThat(child.content().isPaneWindow()).as("Pop Out Tab's pane window (workspace plan 7b Rulings 18)").isTrue();
            assertThat(child.content().origin()).contains(new Origin(parentTab, pane, Side.RIGHT, 0.5));
""", """            notes.moveToNewWindow();
            ChildWindow child = manager.children().getFirst();
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(manager.isMain(notes.window())).isFalse();
            assertThat(child.content().parentTabCount()).as("a full window, the item in a tab of its own (spec 13)").isEqualTo(1);
            assertThat(child.content().selectedTab()).isSameAs(notes);
""", 1),
    ("""            Tab tab = main[0].content().selectedTab();
            main[0].content().invoke(ActionId.POP_OUT_TAB);
            ChildWindow moved = manager.children().getFirst();
            assertThat(tab.window()).isEqualTo(moved.id());
            moved.content().updateActions();
            assertThat(moved.content().action(ActionId.POP_OUT_TAB).isEnabled()).as("its only tab stays").isFalse();
            assertThat(moved.content().action(ActionId.BRING_BACK).isEnabled()).isTrue();
            moved.content().invoke(ActionId.BRING_BACK);
            assertThat(tab.window()).isEqualTo(main[0].id());
            assertThat(moved.closed()).isTrue();
            main[0].content().updateActions();
            assertThat(main[0].content().action(ActionId.BRING_BACK).isEnabled()).isFalse();
""", """            main[0].content().newParentTab(directory);
            ParentTabId moving = main[0].content().activeParentTab();
            main[0].content().invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            ChildWindow moved = manager.children().getFirst();
            assertThat(moved.content().activeParentTab()).isEqualTo(moving);
            moved.content().updateActions();
            assertThat(moved.content().action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("its only tab stays").isFalse();
            assertThat(moved.content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            moved.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(main[0].content().parentTabs().view(moving)).as("back in the main window").isPresent();
            assertThat(moved.closed()).isTrue();
            main[0].content().updateActions();
            assertThat(main[0].content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
""", 1),
    ("""            assertThat(titles).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Move to Main Window", "Close Tab",
                "Close Others", "Close Pane");
""", """            assertThat(titles).containsExactly("Rename Tab", "Move Tab to New Window", "Move to Main Window", "Close Tab",
                "Close Others", "Close Pane");
""", 1),
    ("""            main.content().newTab(HOME);
            Tab tab = main.content().selectedTab();
            events.clear();
            ChildWindow child = manager.popOutTab(tab, new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60))
                .orElseThrow();
            assertThat(child.content().isPaneWindow()).as("a torn-out tab's window is a pane window (parent tabs spec 5)").isTrue();
""", """            main.content().newParentTab(HOME);
            events.clear();
            ChildWindow child = manager.moveTabToNewWindow(main.content(), main.content().activeParentTab(),
                new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60)).orElseThrow();
            assertThat(child.content().parentTabCount()).as("a torn-out tab's window is a full window").isEqualTo(1);
""", 1),
    ("                assertThatThrownBy(() -> manager.popOutTab(notes)).hasMessage(\"move failed\").satisfies(failure ->\n",
     "                assertThatThrownBy(() -> manager.moveToNewWindow(notes)).hasMessage(\"move failed\").satisfies(failure ->\n", 1),
    ("                assertThatThrownBy(() -> manager.popOutTab(notes)).hasMessage(\"listener failed\");\n",
     "                assertThatThrownBy(() -> manager.moveToNewWindow(notes)).hasMessage(\"listener failed\");\n", 1),
    ("            notes.onClosed(() -> asked.add(manager.popOutTab(todo)));\n",
     "            notes.onClosed(() -> asked.add(manager.moveToNewWindow(todo)));\n", 1)])
edit(W + "WorkspaceSnapshotTest.java", [
    ("import dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\n", "", 1),
    ("    @Test void aSnapshotKeepsEachWindowsPlaceParentTabsPaneWindowAndTerminalTabs() throws Exception {\n",
     "    @Test void aSnapshotKeepsEachWindowsPlaceParentTabsChildWindowsAndTerminalTabs() throws Exception {\n", 1),
    ("            ChildWindow pane = windows.manager.popOutTab(tail).orElseThrow();\n",
     "            ChildWindow moved = windows.manager.moveToNewWindow(tail).orElseThrow();\n", 1),
    ("""                .as("a full window and a pane window, in the order they opened")
                .containsExactly(full.content().windowLayout(), pane.content().windowLayout());
            WorkspaceFile.WindowRecord popped = snapshot.children().get(1);
            assertThat(popped.role()).isEqualTo(WorkspaceFile.Role.CHILD);
            assertThat(((PaneLayout) popped.layout()).origin()).as("where its tab came from: its pane, which keeps the first tab")
                .isEqualTo(new Origin(firstParent, first.group().orElseThrow(), Side.RIGHT, 0.5));
""", """                .as("two full windows, in the order they opened")
                .containsExactly(full.content().windowLayout(), moved.content().windowLayout());
            WorkspaceFile.WindowRecord popped = snapshot.children().get(1);
            assertThat(popped.role()).isEqualTo(WorkspaceFile.Role.CHILD);
""", 1),
    ("""            // Jasper's own Errors tab, alone in a pane window (workspace plan 4b's app-owned tab).
""", """            // Jasper's own Errors tab, alone in a full window (workspace plan 4b's app-owned tab).
""", 1),
    ("""            assertThat(snapshot.children()).as("a pane window holding only Jasper's own tab is left out").isEmpty();
""", """            assertThat(snapshot.children()).as("a child window holding only Jasper's own tab is left out").isEmpty();
""", 1)])
edit(W + "WorkspaceSavingTest.java", [
    ("""            ChildWindow pane = windows.manager.popOutTab(main.content().selectedTab()).orElseThrow();
            second();
            assertThat(saved().children()).as("a tab popping out into a pane window").singleElement()
                .satisfies(record -> assertThat(record.layout()).isEqualTo(pane.content().windowLayout()));
            windows.manager.bringBack(pane.content());
            assertThat(saved().children()).as("Bring Back: its window closing is saved at once").isEmpty();
""", """            ChildWindow full = windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();
            second();
            assertThat(saved().children()).as("an item moving into a window of its own").singleElement()
                .satisfies(record -> assertThat(record.layout()).isEqualTo(full.content().windowLayout()));
            windows.manager.moveToMainWindow(full.content());
            assertThat(saved().children()).as("Move to Main Window: its window closing is saved at once").isEmpty();
""", 1),
    ("            windows.manager.popOutTab(main.content().selectedTab()).orElseThrow();\n",
     "            windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();\n", 1),
    (""".as("the full window and the pane window, saved before they closed").hasSize(2);""",
     """.as("both child windows, saved before they closed").hasSize(2);""", 1)])
edit(W + "ParentTabBarTest.java", [(
    """            // Workspace plan 7b Rulings 15: Pop Out Parent Tab, then a rule, before Close Parent Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Pop Out Parent Tab", "Close Parent Tab");
""", """            // This plan's Rulings 7: Move Tab to New Window, then a rule, before Close Parent Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Move Tab to New Window", "Close Parent Tab");
""", 1)])
edit(W + "TabMenuTest.java", [
    ("""            assertThat(titles(menu)).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "Close Tab",
                "Close Others", "Close Pane");
""", """            assertThat(titles(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab", "Close Others", "Close Pane");
""", 1),
    ("""            assertThat(titles(hosted)).containsExactly("Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "Close Tab", "Close Others",
                "Close Pane", "Save");
""", """            assertThat(titles(hosted)).containsExactly("Move Tab to New Window", "Close Tab", "Close Others", "Close Pane", "Save");
""", 1)])
edit(W + "TerminalContextMenuTest.java", [(
    """                "Maximize Pane", "Close Tab", "---", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab");
""", """                "Maximize Pane", "Close Tab", "---", "Move Tab to New Window");
""", 1)])
B = W + "WindowMenuButtonTest.java"
regex(B, r'"Pop Out Pane", "Pop Out Parent Tab"\)', '"Move Tab to New Window")', 2)
regex(B, r'"Pop Out Pane", "Move to Main Window"\)', '"Move Tab to New Window", "Move to Main Window")', 6)
D = W + "TabDragWindowsTest.java"
edit(D, [("""            assertThat(from.mayPopOutParentTab(only)).as("a full window's only tab stays in its window").isFalse();
""", """            assertThat(from.mayMoveTabToNewWindow(only)).as("a full window's only tab stays in its window").isFalse();
""", 1)])
span(D, "    /** Workspace plan 7c Rulings 9: a pane window has no tabs and takes none; nothing moves. */\n",
     "    /** A tab whose pane holds a plugin's view moves with the same view, shown in its new window. */\n",
     "    /** A tab whose pane holds a plugin's view moves with the same view, shown in its new window. */\n")
span(D, "    /** Parent tabs spec 5 and workspace plan 7b Rulings 11: a pane window opens where its pane was, at its size. */\n",
     "    /** A command moving the item a dragged tab's entry shows (Move to Main Window from the keyboard) ends the drag. */\n",
     "    /** A command moving the item a dragged tab's entry shows (Move to Main Window from the keyboard) ends the drag. */\n")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: `moveToNewWindow(Tab)`, `moveTabToNewWindow`, `mayMoveTabToNewWindow`,
`ActionId.MOVE_TAB_TO_NEW_WINDOW` are not found; `windowLayout()` returns `WindowLayout`, which has no `parentTabs()`.

- [ ] **Step 3: The model, the file and the commands lose pane windows**

The layout loses `Origin`, `PaneLayout`, `WindowLayout`, `origin` and `graft`; the file loses its `pane` role; the
commands become Move Tab to New Window. `encode`'s replacement holds Java backslashes, so it is a raw string:

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan8a"); from edits import edit, span
A = "jasper-app/src/main/java/dev/jasper/app/"
L = A + "layout/"
for gone in ("Origin", "PaneLayout", "WindowLayout"): pathlib.Path(L + gone + ".java").unlink()
edit(L + "ParentTabs.java", [
    ("public final class ParentTabs implements WindowLayout {\n", "public final class ParentTabs {\n", 1),
    ("""    /** Every tab: the parent tabs in bar order, each parent tab's tabs in workspace order. */
    @Override public List<TabId> tabs() {
""", """    /**
     * Every tab: the parent tabs in bar order, each parent tab's tabs in workspace order.
     *
     * @return the tabs
     */
    public List<TabId> tabs() {
""", 1)])
span(L + "Layout.java", """    /**
     * Where {@code group} sits (parent tabs spec 4's origin; workspace plan 7b Rulings 2): the group beside it, across its
""", """            if (deeper.isPresent()) return deeper;
        }
        return Optional.empty();
    }

""", "")
edit(L + "package-info.java", [(
    """ * {@code Layout}, each group's region of the unit square, and what a window holds (parent tabs spec 4): its parent
 * tabs ({@code ParentTabs}) or a pane window's one layout and where its panes came from ({@code PaneLayout},
 * {@code Origin}). JDK-only: nothing from Swing, AWT or a
 * file format; {@code verifyApplicationArchitecture} enforces it. Immutable values, safe on any thread; whoever holds a
""", """ * {@code Layout}, each group's region of the unit square, and what a window holds (tabs and panes spec 4): its tabs
 * ({@code ParentTabs}). JDK-only: nothing from Swing, AWT or a
 * file format; {@code verifyApplicationArchitecture} enforces it. Immutable values, safe on any thread; whoever holds a
""", 1)])
F = A + "persistence/WorkspaceFile.java"
edit(F, [
    ("import dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\n", "", 1),
    ("import dev.jasper.app.layout.Side;\n", "", 1),
    ("import dev.jasper.app.layout.WindowLayout;\n", "", 1),
    ("import java.util.Locale;\n", "", 1),
    (""" * 9): each Jasper window's kind, bounds and maximized state, its parent tabs or, for a pane window, its one layout and
 * its origin, and what each of its tabs needs to come back. {@link #write} writes it atomically; {@link #read} never
""", """ * 9): each Jasper window's kind, bounds and maximized state, its parent tabs, and what each of its tabs needs to come
 * back. {@link #write} writes it atomically; {@link #read} never
""", 1),
    ("    /** Whether a window is the main window or a child window: a full window or a pane window, as its layout says. */\n",
     "    /** Whether a window is the main window or a child window, a full window. */\n", 1),
    ("""     * @param layout what it holds (parent tabs spec 4): parent tabs for the main window and a full window, whose shown one
     *     is its active one; one layout and its origin for a pane window. Only the main window's may be empty
     * @param tabs a record for exactly each tab of the layout
     */
    public record WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, WindowLayout layout, Map<TabId, TabRecord> tabs) {
""", """     * @param layout what it holds (tabs and panes spec 4): its parent tabs, whose shown one is its active one; only the
     *     main window's may be empty
     * @param tabs a record for exactly each tab of the layout
     */
    public record WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, ParentTabs layout, Map<TabId, TabRecord> tabs) {
""", 1),
    ("""            if (role == Role.MAIN && !(layout instanceof ParentTabs)) throw new IllegalArgumentException("The main window holds parent tabs");
            if (role == Role.CHILD && layout instanceof ParentTabs parents && parents.isEmpty())
""", """            if (role == Role.CHILD && layout.isEmpty())
""", 1),
    ("""     * drops it, and a parent tab it leaves with no tab goes ({@link #keeping(WindowLayout, Predicate, boolean)}); a bad
     * parent tab or window entry is dropped; bounds, choices and a pane window's origin fall back alone. When a window, a
""", """     * drops it, and a parent tab it leaves with no tab goes ({@link #keeping(ParentTabs, Predicate, boolean)}); a bad
     * parent tab or window entry is dropped; bounds and choices fall back alone. When a window, a
""", 1),
    ("""            Role role = "main".equals(name) ? Role.MAIN : "full".equals(name) || "pane".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            WindowLayout held = "pane".equals(name) ? new PaneLayout(layout(entry), origin(entry.get(List.of("origin"))))
                : parentTabs(entry, dropped);
""", """            Role role = "main".equals(name) ? Role.MAIN : "full".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            ParentTabs held = parentTabs(entry, dropped);
""", 1),
    ("            WindowLayout layout = keeping(held, records::containsKey, role == Role.MAIN)\n",
     "            ParentTabs layout = keeping(held, records::containsKey, role == Role.MAIN)\n", 1),
    ("""    /**
     * Where a pane window's panes came from (parent tabs spec 4); {@link #NO_ORIGIN} when the entry cannot be read, so
     * Bring Back takes them beside the main window's active pane (spec 5).
     */
    private static Origin origin(Object value) {
        try {
            if (!(value instanceof TomlTable table)) throw new IllegalArgumentException("a pane window has no origin");
            if (!(table.get(List.of("share")) instanceof Number share)) throw new IllegalArgumentException("an origin's share is not a number");
            return new Origin(ParentTabId.parse(string(table, "parent_tab")), GroupId.parse(string(table, "pane")),
                Side.valueOf(string(table, "side").toUpperCase(Locale.ROOT)), share.doubleValue());
        } catch (IllegalArgumentException unreadable) {
            return NO_ORIGIN;
        }
    }

""", "", 1),
    ("""     * @param layout a window's parent tabs, or a pane window's layout
     * @param kept the tabs that stay
     * @param main whether the window is the main window, whose workspace may be empty
     * @return what stays; empty when no tab does and the window is not the main window
     */
    public static Optional<WindowLayout> keeping(WindowLayout layout, Predicate<TabId> kept, boolean main) {
        return switch (layout) {
            case PaneLayout pane -> {
                Layout rest = keeping(pane.layout(), kept);
                yield rest.isEmpty() ? Optional.empty() : Optional.of(new PaneLayout(rest, pane.origin()));
            }
            case ParentTabs parents -> {
                // The shown parent tab goes last, so that a window every tab leaves keeps it as the empty workspace.
                var order = new ArrayList<>(parents.parentTabs());
                order.sort(Comparator.comparing(parent -> parent.id().equals(parents.active())));
                ParentTabs rest = parents;
                for (ParentTab parent : order) {
                    Layout left = keeping(parent.layout(), kept);
                    rest = left.isEmpty() && rest.parentTabs().size() > 1 ? rest.remove(parent.id()) : rest.replace(new ParentTab(parent.id(), left));
                }
                yield rest.isEmpty() && !main ? Optional.empty() : Optional.of(rest);
            }
        };
    }
""", """     * @param layout a window's parent tabs
     * @param kept the tabs that stay
     * @param main whether the window is the main window, whose workspace may be empty
     * @return what stays; empty when no tab does and the window is not the main window
     */
    public static Optional<ParentTabs> keeping(ParentTabs layout, Predicate<TabId> kept, boolean main) {
        // The shown parent tab goes last, so that a window every tab leaves keeps it as the empty workspace.
        var order = new ArrayList<>(layout.parentTabs());
        order.sort(Comparator.comparing(parent -> parent.id().equals(layout.active())));
        ParentTabs rest = layout;
        for (ParentTab parent : order) {
            Layout left = keeping(parent.layout(), kept);
            rest = left.isEmpty() && rest.parentTabs().size() > 1 ? rest.remove(parent.id()) : rest.replace(new ParentTab(parent.id(), left));
        }
        return rest.isEmpty() && !main ? Optional.empty() : Optional.of(rest);
    }
""", 1),
    ("""    /** Every group a window holds: each parent tab's, or the pane window's. */
    private static List<GroupId> groupIds(WindowLayout layout) {
        return switch (layout) {
            case ParentTabs parents -> parents.parentTabs().stream().flatMap(parent -> parent.layout().groups().stream()).map(Group::id).toList();
            case PaneLayout pane -> pane.layout().groups().stream().map(Group::id).toList();
        };
    }

    /** The parent tabs a main or full window holds; none for a pane window. */
    private static List<ParentTabId> parentTabIds(WindowLayout layout) {
        return layout instanceof ParentTabs parents ? parents.parentTabs().stream().map(ParentTab::id).toList() : List.of();
    }
""", """    /** Every group a window holds: each parent tab's. */
    private static List<GroupId> groupIds(ParentTabs layout) {
        return layout.parentTabs().stream().flatMap(parent -> parent.layout().groups().stream()).map(Group::id).toList();
    }

    /** The parent tabs a window holds. */
    private static List<ParentTabId> parentTabIds(ParentTabs layout) {
        return layout.parentTabs().stream().map(ParentTab::id).toList();
    }
""", 1),
    ("""    /**
     * The origin of a pane window whose saved origin cannot be read: a parent tab and a pane under a name-based id, an id
     * Jasper never writes for a parent tab or a pane, so no window holds them (workspace plan 6a Rulings 26).
     */
    static final Origin NO_ORIGIN = new Origin(new ParentTabId(UUID.nameUUIDFromBytes("jasper.origin:none".getBytes(StandardCharsets.UTF_8))),
        new GroupId(UUID.nameUUIDFromBytes("jasper.origin:none".getBytes(StandardCharsets.UTF_8))), Side.RIGHT, 0.5);
""", "", 1)])
span(F, """    /**
     * The file's text: the windows in order; a main or full window's parent tabs in bar order, each with its nodes parents
""", """        return text.toString();
    }
""", r"""    /**
     * The file's text: the windows in order; each window's parent tabs in bar order, each with its nodes parents first;
     * then the window's tabs in window order.
     */
    static String encode(Snapshot snapshot) {
        var text = new StringBuilder("version = 1\n");
        for (WindowRecord window : snapshot.windows()) {
            text.append("\n[[windows]]\nrole = \"").append(window.role() == Role.MAIN ? "main" : "full").append("\"\n");
            window.bounds().ifPresent(bounds -> text.append("x = ").append(bounds.x()).append("\ny = ").append(bounds.y())
                .append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
            text.append("extended_state = ").append(window.extendedState()).append('\n');
            text.append("active_parent_tab = \"").append(window.layout().active()).append("\"\n");
            for (ParentTab parent : window.layout().parentTabs()) {
                text.append("\n[[windows.parent_tabs]]\nid = \"").append(parent.id()).append("\"\n");
                appendLayout(text, parent.layout(), "windows.parent_tabs.nodes");
            }
            for (TabId tab : window.layout().tabs()) appendTab(text, tab, window.tabs().get(tab));
        }
        return text.toString();
    }
""")
C = A + "contributions/"
edit(C + "TabRegistry.java", [
    ("@param newWindow pops a tab out into a new pane window, or leaves it where it is\n",
     "@param newWindow moves a tab into a new full window of its own, or leaves it where it is\n", 3),
    ("""     * Whether a move would take {@code tab}: one of this registry's, open, neither pinned nor being closed. A pane or a
     * parent tab moves whole or not at all (workspace plan 7b Rulings 7).
""", """     * Whether a move would take {@code tab}: one of this registry's, open, neither pinned nor being closed. A parent tab
     * moves whole or not at all (workspace plan 7b Rulings 7).
""", 1),
    ("""     * Pop Out Tab (parent tabs spec 5): the window manager opens a pane window for the tab, or leaves it where it is (a
     * child window's only tab). A pinned tab or one being closed stays.
""", """     * {@code ContentTab.moveToNewWindow()} (tabs and panes spec 13): the window manager opens a full window holding the
     * tab, or leaves it where it is (a child window's only tab). A pinned tab or one being closed stays.
""", 1)])
edit(C + "Tab.java", [("    /** Pop Out Tab: a new pane window takes the tab (parent tabs spec 5). */\n",
     "    /** A new full window takes the tab, as {@code ContentTab.moveToNewWindow()} asks (tabs and panes spec 13). */\n", 1)])
edit(A + "commands/ActionId.java", [(
    """    POP_OUT_TAB("pop_out_tab", "Pop Out Tab", "none", "none"),
    POP_OUT_PANE("pop_out_pane", "Pop Out Pane", "none", "none"),
    POP_OUT_PARENT_TAB("pop_out_parent_tab", "Pop Out Parent Tab", "none", "none"),
    BRING_BACK("bring_back", "Bring Back", "none", "none"),
""", """    MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),
""", 1)])
edit(A + "shortcuthelp/ShortcutCatalog.java", [
    ("""            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, POP_OUT_TAB,
                POP_OUT_PARENT_TAB, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
""", """            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW,
                NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
""", 1),
    ("                FOCUS_PANE_UP, FOCUS_PANE_DOWN, POP_OUT_PANE -> \"Panes\";\n", "                FOCUS_PANE_UP, FOCUS_PANE_DOWN -> \"Panes\";\n", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java", [(
    """     * Pop Out Tab: a new pane window takes the tab and its content, and its Bring Back returns them. A child window's
     * only tab, a tab with a modal prompt open and a tab being closed stay where they are.
""", """     * A new child window takes the tab and its content. A child window's only tab, a tab with a modal prompt open and a
     * tab being closed stay where they are.
""", 1)])
PY
```

The windows lose pane windows, and Pop Out Parent Tab becomes Move Tab to New Window:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
C = W + "WindowContent.java"
edit(C, [
    ("import dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\nimport dev.jasper.app.layout.WindowLayout;\n",
     "import dev.jasper.app.layout.ParentTabs;\n", 1),
    ("    /** The window manager that opened this window, which pops out, brings back and moves (parent tabs spec 5); else null. */\n",
     "    /** The window manager that opened this window, which moves its tabs to other windows (tabs and panes spec 6); else null. */\n", 1),
    ("""    /** A pane window's origin (parent tabs spec 4), which the window manager gives it once while it is empty; else null. */
    private Origin origin;
""", "", 1),
    ("     * tear-out needs a manager ({@link #mayPopOutParentTab}).\n", "     * tear-out needs a manager ({@link #mayMoveTabToNewWindow}).\n", 1),
    ("""    /** Pop Out Tab (parent tabs spec 5): the window manager decides whether {@code tab} moves (workspace plan 7b Rulings 8). */
    void popOutTab(Tab tab) { if (tab != null) registry.moveToNewWindow(tab); }
    /** Pop Out Pane: {@code tab}'s pane, through the window manager; nothing in a window outside one. */
    void popOutPane(Tab tab) {
        if (manager != null && tab != null) parentTabs.groupOf(tab.id()).ifPresent(pane -> manager.popOutPane(this, pane));
    }
    /** Pop Out Parent Tab: {@code tab}'s parent tab. */
    void popOutParentTab(Tab tab) {
        if (manager != null && tab != null) parentTabOf(tab).ifPresent(parentTab -> manager.popOutParentTab(this, parentTab));
    }
    /** Bring Back: this pane window's panes go back where they came from. */
    void bringBack() { if (manager != null) manager.bringBack(this); }
    /** Move to Main Window: this full window's parent tabs join the main window's bar. */
""", """    /**
     * Move Tab to New Window (tabs and panes spec 6): the parent tab holding {@code tab}, through the window manager;
     * nothing in a window outside one.
     */
    void moveTabToNewWindow(Tab tab) {
        if (manager != null && tab != null) parentTabOf(tab).ifPresent(parentTab -> manager.moveTabToNewWindow(this, parentTab));
    }
    /** Move to Main Window: this full window's parent tabs join the main window's bar. */
""", 1),
    ("""     * A tab's menu (workspace tabs spec 11; parent tabs spec 6; workspace plan 7b Rulings 15): Rename Tab for a terminal tab,
     * Pop Out Tab and the window menu's entries for this tab's pane and parent tab, Close Tab, Close Others and Close Pane,
     * then the content's commands.
""", """     * A tab's menu (workspace tabs spec 11; tabs and panes spec 6; workspace plan 7b Rulings 15): Rename Tab for a terminal
     * tab, the window menu's entries for this tab's parent tab, Close Tab, Close Others and Close Pane, then the content's
     * commands.
""", 1),
    ("""        var popOut = tabMenuItem("Pop Out Tab", ActionId.POP_OUT_TAB, () -> popOutTab(tab));
        // A child window's only tab stays: that window already is its own (workspace plan 7b Rulings 8); so does a tab
        // that cannot move (Rulings 7).
        popOut.setEnabled(mayPopOutTab(tab));
        menu.add(popOut);
""", "", 1),
    ("""     * A parent tab's menu in the bar (parent tabs spec 6 and 8; workspace plan 7a Rulings 16, plan 7b Rulings 15): Pop Out
     * Parent Tab and Move to Main Window where the window menu has them, a rule, then Close Parent Tab, for the parent tab
     * {@code tab} shows as its active tab.
""", """     * A parent tab's menu in the bar (tabs and panes spec 6; workspace plan 7a Rulings 16, plan 7b Rulings 15): the window
     * menu's entries, a rule, then Close Parent Tab, for the parent tab {@code tab} shows as its active tab.
""", 1),
    ("""            for (ActionId id : windowMenuItems())
                if (id == ActionId.POP_OUT_PARENT_TAB || id == ActionId.MOVE_TO_MAIN_WINDOW) menu.add(windowItem(id, tab));
""", """            for (ActionId id : windowMenuItems()) menu.add(windowItem(id, tab));
""", 1),
    ("""     * The window menu's entries (parent tabs spec 6; workspace plan 7b Rulings 14): Pop Out Pane and Pop Out Parent Tab in
     * the main window; Pop Out Pane, Pop Out Parent Tab while it has another parent tab, and Move to Main Window in a full
     * window; Bring Back, then Pop Out Pane while it has another pane, in a pane window.
     *
     * @return the entries in menu order
     */
    java.util.List<ActionId> windowMenuItems() {
        if (origin != null) return paneCount() > 1 ? java.util.List.of(ActionId.BRING_BACK, ActionId.POP_OUT_PANE)
            : java.util.List.of(ActionId.BRING_BACK);
        if (isMain()) return java.util.List.of(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB);
        return parentTabs.count() > 1
            ? java.util.List.of(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.MOVE_TO_MAIN_WINDOW)
            : java.util.List.of(ActionId.POP_OUT_PANE, ActionId.MOVE_TO_MAIN_WINDOW);
    }
""", """     * The window menu's entries (tabs and panes spec 6; workspace plan 8a Rulings 7): Move Tab to New Window in every
     * window, then Move to Main Window in a full window.
     *
     * @return the entries in menu order
     */
    java.util.List<ActionId> windowMenuItems() {
        return isMain() ? java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW)
            : java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
    }
""", 1),
    ("""     * The ⋮'s menu (parent tabs spec 6): {@link #windowMenuItems()}, each enabled as its action is, for the active pane
     * and parent tab.
""", """     * The ⋮'s menu (tabs and panes spec 6): {@link #windowMenuItems()}, each enabled as its action is, for the shown
     * parent tab.
""", 1),
    ("""    /** A menu entry for {@code id}, one of {@link #windowMenuItems()}, acting on {@code tab}'s pane or parent tab, or this window. */
    private JMenuItem windowItem(ActionId id, Tab tab) {
        Runnable run = switch (id) {
            case POP_OUT_PANE -> () -> popOutPane(tab);
            case POP_OUT_PARENT_TAB -> () -> popOutParentTab(tab);
            case BRING_BACK -> this::bringBack;
            case MOVE_TO_MAIN_WINDOW -> this::moveToMainWindow;
""", """    /** A menu entry for {@code id}, one of {@link #windowMenuItems()}, acting on {@code tab}'s parent tab or this window. */
    private JMenuItem windowItem(ActionId id, Tab tab) {
        Runnable run = switch (id) {
            case MOVE_TAB_TO_NEW_WINDOW -> () -> moveTabToNewWindow(tab);
            case MOVE_TO_MAIN_WINDOW -> this::moveToMainWindow;
""", 1),
    ("""            case POP_OUT_PANE -> parentTabs.groupOf(tab.id()).map(this::mayPopOutPane).orElse(false);
            case POP_OUT_PARENT_TAB -> parentTabOf(tab).map(this::mayPopOutParentTab).orElse(false);
            case BRING_BACK -> mayBringBack();
            case MOVE_TO_MAIN_WINDOW -> mayMoveToMainWindow();
""", """            case MOVE_TAB_TO_NEW_WINDOW -> parentTabOf(tab).map(this::mayMoveTabToNewWindow).orElse(false);
            case MOVE_TO_MAIN_WINDOW -> mayMoveToMainWindow();
""", 1),
    ("    /** The window manager that opened this window: its pop-outs, Bring Back and Move to Main Window go through it. */\n",
     "    /** The window manager that opened this window: its moves to other windows go through it. */\n", 1),
    ("""     * empty (workspace plan 7a Rulings 7). A pane window, which has no parent tabs, takes it in its active pane (workspace
     * plan 7b Rulings 9).
""", """     * empty (workspace plan 7a Rulings 7).
""", 1),
    ("        return parentTabs.isEmpty() || origin != null ? openTab(directory)\n", "        return parentTabs.isEmpty() ? openTab(directory)\n", 1)])
span(C, """    /**
     * What this window holds (parent tabs spec 4): its parent tabs with their layouts, and the one that shows; a pane
""", """    boolean mayMoveToMainWindow() { return !closed && manager != null && !isMain() && origin == null && movable(tabs()); }
""", """    /**
     * What this window holds (tabs and panes spec 4): its parent tabs with their layouts, and the one that shows.
     *
     * @return the parent tabs
     */
    public ParentTabs windowLayout() { return parentTabs.model(); }

    /** Whether {@code moving} holds tabs and the registry would move every one ({@code TabRegistry.movable}, Rulings 7). */
    private boolean movable(java.util.List<Tab> moving) {
        return !moving.isEmpty() && moving.stream().allMatch(tab -> registry.movable(tab));
    }

    /**
     * Whether Move Tab to New Window may take {@code parentTab}: one of this window's, beside another parent tab, holding
     * tabs that may all move; never without a window manager (tabs and panes spec 6; workspace plan 7b Rulings 7, plan 8a
     * Rulings 6).
     */
    boolean mayMoveTabToNewWindow(ParentTabId parentTab) {
        if (closed || manager == null || parentTab == null) return false;
        Optional<WorkspaceView> view = parentTabs.view(parentTab);
        return view.isPresent() && parentTabs.count() > 1 && movable(view.get().tabs());
    }

    /**
     * Whether Move to Main Window may take this window's tabs: a full window's that the window manager opened, every one
     * of which may move (Rulings 7).
     */
    boolean mayMoveToMainWindow() { return !closed && manager != null && !isMain() && movable(tabs()); }
""")
M = W + "WindowManager.java"
edit(M, [
    ("import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Layout;\nimport dev.jasper.app.layout.Origin;\n", "", 1),
    ("import dev.jasper.app.layout.ParentTabId;\n", "import dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.ParentTabs;\n", 1),
    ("import dev.jasper.app.layout.WindowLayout;\n", "", 1),
    ("            this::popOutTab, this::movedOut, this::emptyWindow));\n", "            this::moveToNewWindow, this::movedOut, this::emptyWindow));\n", 1),
    ("""     * main window it empties shows an empty workspace under a new id. Both windows are main or full windows; a parent tab
     * holding a tab that is pinned or being closed stays. It keeps its maximize too: spec 15 ends a maximize for a tab
""", """     * main window it empties shows an empty workspace under a new id. A parent tab holding a tab that is pinned or being
     * closed stays. It keeps its maximize too: spec 15 ends a maximize for a tab
""", 1),
    ("        if (from == null || to == null || from == to || from.closed() || to.closed() || from.isPaneWindow() || to.isPaneWindow())\n",
     "        if (from == null || to == null || from == to || from.closed() || to.closed())\n", 1),
    ("""    /**
     * A child window: a pane window from {@code origin}, made one while it is empty and before its frame exists (parent tabs
     * spec 4), or a full window when it is null.
     */
    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor, Origin origin) {
""", """    /** A full child window, empty for tabs that move into it or holding a terminal tab in {@code directory}. */
    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor) {
""", 1),
    ("            if (origin != null) content.paneWindow(origin);\n", "", 1),
    ("            // Its pop-outs, Bring Back and Move to Main Window are this manager's (parent tabs spec 5).\n",
     "            // Its moves to other windows are this manager's (tabs and panes spec 6).\n", 1),
    ("        return openChild(false, directory, null, null, null);\n", "        return openChild(false, directory, null, null);\n", 1),
    ("        return openChild(true, null, null, null, null).map(JasperWindow::id)\n", "        return openChild(true, null, null, null).map(JasperWindow::id)\n", 1),
    ("""     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13; parent tabs spec 9): the main window, its full
     * windows and its pane windows, each with where it is, what it holds (its parent tabs and the one that shows, or a
     * pane window's layout and origin) and what each of its tabs needs to come back. A tab that cannot come back is taken
""", """     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13; tabs and panes spec 12): the main window and its
     * full windows, each with where it is, what it holds (its parent tabs and the one that shows) and what each of its
     * tabs needs to come back. A tab that cannot come back is taken
""", 1),
    ("        Optional<WindowLayout> kept = WorkspaceFile.keeping(window.content().windowLayout(), tabs::containsKey, window.isMain());\n",
     "        Optional<ParentTabs> kept = WorkspaceFile.keeping(window.content().windowLayout(), tabs::containsKey, window.isMain());\n", 1)])
span(M, """    /**
     * Pop Out Tab (parent tabs spec 5): a new pane window takes {@code tab}, its content and session untouched, where its
""", """        return settle(opened.get(), failure);
    }

    /**
     * Moves the parent tab {@code parentTab} of {@code from}""", """    /**
     * {@code ContentTab.moveToNewWindow()} (tabs and panes spec 13): a new full window takes {@code tab}, its content and
     * session untouched, where the platform puts a window (workspace plan 8a Rulings 6). A tab being closed or pinned
     * stays, and so does a child window's only tab: that window already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new full window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab) {
        requireEdt();
        if (leaving(tab) == null) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, null, null);
        if (opened.isEmpty()) return opened;
        Throwable failure = null;
        try {
            model.tabs().move(tab, opened.get().id());
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        return settle(opened.get(), failure);
    }

    /**
     * Move Tab to New Window (tabs and panes spec 6; plan 7b's Pop Out Parent Tab): a new full window takes the parent tab
     * {@code parentTab} whole, with its id and layout, where the platform puts it (workspace plan 7b Rulings 10 and 11).
     * A window's only parent tab stays (workspace plan 8a Rulings 6), and so does a parent tab holding a tab that is
     * pinned or being closed.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab) {
        return moveTabToNewWindow(window, parentTab, null, null);
    }

    /**
     * As {@link #moveTabToNewWindow(WindowContent, ParentTabId)}, the full window's workspace at {@code bounds} on screen,
     * on the display under {@code cursor}: the tab drag's tear-out.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @param bounds where the full window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab, Rectangle bounds, Point cursor) {
        requireEdt();
        if (window == null || !window.mayMoveTabToNewWindow(parentTab)) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor);
        if (opened.isEmpty()) return opened;
        Throwable failure = null;
        try {
            moveParentTab(window, parentTab, opened.get().content(), 0);
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        return settle(opened.get(), failure);
    }

    /**
     * Moves the parent tab {@code parentTab} of {@code from}""")
span(M, """    /**
     * Bring Back (parent tabs spec 5 and 11; workspace plan 7b Rulings 5): a pane window's whole layout returns as a nested
""", """        return target.content().receive(moving, parentTab, 0, arranged) ? Optional.of(target) : Optional.empty();
    }

""", "")
span(M, """    /**
     * The open main or full window holding the parent tab {@code id}, which follows it between windows (parent tabs spec
""", """        return Optional.empty();
    }

""", "")
span(M, """    /** A new pane window from {@code origin} takes {@code tab}: Pop Out Tab, and a tab the drag tears out. */
""", """        return settle(opened.get(), failure);
    }

""", "")
span(M, """    /** The middle of {@code bounds} on screen, whose display a window opening there takes; null for null. */
""", """        return bounds == null ? null : new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

""", "")
A = W + "WorkspaceActions.java"
edit(A, [
    ("""            case POP_OUT_TAB -> owner.popOutTab(owner.selectedTab());
            case POP_OUT_PANE -> owner.popOutPane(owner.selectedTab());
            case POP_OUT_PARENT_TAB -> owner.popOutParentTab(owner.selectedTab());
            case BRING_BACK -> owner.bringBack();
""", """            case MOVE_TAB_TO_NEW_WINDOW -> owner.moveTabToNewWindow(owner.selectedTab());
""", 1),
    ("""                    // A child window's only tab, pane and parent tab stay: that window already is its own (workspace
                    // plan 7b Rulings 8).
                    case POP_OUT_TAB -> owner.mayPopOutTab(owner.selectedTab());
                    case POP_OUT_PANE -> owner.selectedTab() != null && owner.mayPopOutPane(owner.workspace().model().activeGroup());
                    case POP_OUT_PARENT_TAB -> owner.mayPopOutParentTab(owner.activeParentTab());
                    // A pane, a parent tab or a window moves whole or not at all (workspace plan 7b Rulings 7).
                    case BRING_BACK -> owner.mayBringBack();
""", """                    // A window's only parent tab stays (workspace plan 8a Rulings 6).
                    case MOVE_TAB_TO_NEW_WINDOW -> owner.mayMoveTabToNewWindow(owner.activeParentTab());
                    // A parent tab or a window moves whole or not at all (workspace plan 7b Rulings 7).
""", 1),
    ("                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null && !owner.isPaneWindow();\n",
     "                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null;\n", 1)])
edit(W + "WindowCommands.java", [(
    """                case POP_OUT_TAB, POP_OUT_PANE -> List.of("detach", "separate window", "pane window");
                case POP_OUT_PARENT_TAB -> List.of("detach", "separate window", "full window");
                case BRING_BACK -> List.of("attach", "return", "dock", "origin");
""", """                case MOVE_TAB_TO_NEW_WINDOW -> List.of("detach", "separate window", "new window");
""", 1)])
edit(W + "WindowChrome.java", [(
    """        // Pop Out and the window menu's entries, which a pane showing no strip offers only here (parent tabs spec 6).
        menu.addSeparator();
        menu.add(owner.action(ActionId.POP_OUT_TAB));
""", """        // The window menu's entries, for a window whose strip hides (tabs and panes spec 6).
        menu.addSeparator();
""", 1)])
edit(W + "ParentTabBar.java", [
    ("""     * Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last. A pane window has
     * no parent tabs and shows no bar (parent tabs spec 7).
""", """     * Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last.
""", 1),
    ("        boolean shown = !owner.isPaneWindow() && owner.look().showsParentTabBar(parentTabs.count());\n",
     "        boolean shown = owner.look().showsParentTabBar(parentTabs.count());\n", 1)])
edit(W + "TabDrag.java", [
    (""" * cursor holds it and kept on the display under the cursor, except for a full window's only tab, whose window already
 * is its own. A tab holding an item that is pinned (a modal owner holds it in its window), or one being closed, takes
 * targets along its own strip only, and a pane window takes none. Releasing the left button carries the target out:
""", """ * cursor holds it and kept on the display under the cursor, except for a window's only tab (a full window's already is
 * its own; workspace plan 8a Rulings 6). A tab holding an item that is pinned (a modal owner holds it in its window), or
 * one being closed, takes targets along its own strip only. Releasing the left button carries the target out:
""", 1),
    ("""     * it, in its place. Empty for a pane window, which has no tabs, off the strip, and for the place the tab has.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window.isPaneWindow() || (window == from && !shows)) return Optional.empty();
""", """     * it, in its place. Empty off the strip, and for the place the tab has.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window == from && !shows) return Optional.empty();
""", 1),
    ("""    /** Whether a release outside every window takes the tab out of its window: never a full window's only tab. */
    private boolean tearsOut() { return from.mayPopOutParentTab(parentTab); }
""", """    /** Whether a release outside every window takes the tab out of its window: never a window's only tab (Rulings 6). */
    private boolean tearsOut() { return from.mayMoveTabToNewWindow(parentTab); }
""", 1),
    ("            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.popOutParentTab(home, parent, out.bounds(), out.cursor()));\n",
     "            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.moveTabToNewWindow(home, parent, out.bounds(), out.cursor()));\n", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.layout.*' --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.contributions.*' --continue`
Expected: PASS; `MoveTabsTest` and `WindowMenuTest` run, `BringBackTest`, `PopOutTest`, `PopOutCommandsTest` and
`OriginTest` are gone. Then `./gradlew :jasper-app:check :jasper-sdk:check`: PASS (doclint over the SDK Javadoc, the
architecture checks and the ledger test).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src jasper-sdk/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: pane windows go; Move Tab to New Window

Pane windows go with their origins, Pop Out Tab, Pop Out Pane and
Bring Back, and workspace.toml loses its pane role. A content tab's
move to a new window opens a full window holding it. Pop Out Parent
Tab becomes Move Tab to New Window, from two tabs, and every
window's menu offers it, with Move to Main Window in a full window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The commands and defaults of spec 10

Spec 10 and 16: the tab commands take the parent-tab commands' meanings and keys, Close Pane and Close Tab take their
new keys and meanings, and the removed ids go (Rulings 14-17). A strip entry's menu acts on its whole tab (Rulings 8),
and the strip's "+" is New Tab (Rulings 11). Items still stack, and the pane strips keep their entries' tooltips, until
Task 4 removes them; this task's tests already build tabs and panes as Task 4 leaves them (New Tab, splits and a
plugin's item opened beside), so they pass on both sides of it.

**Files:**
- Rename: `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabCommandsTest.java` →
  `TabCommandsTest.java` (and its ledger row)
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java`
- Modify: `commands/ActionId.java` (`:11-15`, `:29-48`); `layout/ParentTabs.java` (`step`'s Javadoc);
  `shortcuthelp/ShortcutCatalog.java` (`category`); `workspace/WorkspaceActions.java` (`invoke`, `update`);
  `workspace/WindowContent.java` (`traversalShortcut`'s Javadoc, `parentTabKey`, `tabShortcut`, `parentTabShortcut`,
  `groupTabCount`, `tabMenu`, `parentTabMenu`, `selectInGroup`, `selectRelative`, `groupTabs`, `closeOthers`,
  `closeGroup`, the Javadoc of `selectParentTabAt`, `selectParentTabRelative`, `closeParentTab`);
  `workspace/ParentTabBar.java` (`shortcut`, `menu`, `closeName`); `workspace/GroupView.java` (`newTab`,
  `newTabName`); `workspace/ClassicTabs.java` (Javadoc `:41`, comment `:56`); `workspace/WindowChrome.java` (File menu
  `:81-82`, `GROUP_ITEMS` to `tabMenu()` `:456-473`, `contextMenu`); `workspace/WindowCommands.java` (`:42`, `:56-58`)
- Test: replace `workspace/TabShortcutsTest.java` and `workspace/TabMenuTest.java` whole; edit
  `config/KeymapDefaultsTest.java`, `config/KeyBindingsTest.java`, `config/ConfigLoaderTest.java`,
  `config/ConfigTemplateTest.java`, `shortcuthelp/ShortcutHelpTest.java`, `workspace/ParentTabBarTest.java`,
  `WindowMenuTest.java` (Task 2's), `WindowManagerTest.java`, `ClassicTabsTest.java`, `WindowTabsTest.java`,
  `WorkspaceTabsTest.java`, `TerminalContextMenuTest.java`

**Interfaces:**
- Consumes: Task 2's `windowMenuItems()`, `windowItem`, `mayMoveTabToNewWindow`, `WindowMenuTest`; plan 7a's
  `selectParentTabAt(int)`, `selectParentTabRelative(int)`, `closeParentTab(ParentTabId)`, `parentTabOf(Tab)`,
  `closeTabs(List<Tab>)`.
- Produces:
  - `ActionId`: `NEW_TAB`, `CLOSE_PANE` (`close_pane`, "Close Pane", `cmd+w` / `ctrl+shift+w`), `CLOSE_TAB`
    (`close_tab`, "Close Tab", `alt+cmd+w` / `ctrl+alt+shift+w`), `NEXT_TAB` (`cmd+shift+]` / `ctrl+pagedown`),
    `PREVIOUS_TAB` (`cmd+shift+[` / `ctrl+pageup`), `SELECT_TAB_1`-`9` (`cmd+N` / `ctrl+N`); `NEW_TAB_IN_PANE`,
    `CLOSE_PARENT_TAB`, `NEXT_PARENT_TAB`, `PREVIOUS_PARENT_TAB` and `SELECT_PARENT_TAB_1`-`9` are gone;
  - `WindowContent.tabMenu(Tab item)` → `JPopupMenu`, the one menu of a strip entry, acting on the tab holding `item`
    (`parentTabMenu` is gone); `WindowContent.tabShortcut(int index)` → Select Tab `index + 1`'s shortcut text
    (`parentTabShortcut` is gone);
  - `ParentTabBar.closeName()` → "Close Tab"; `ParentTabBar.newTabName()` and `GroupView.newTabName()` → "New Tab".

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabCommandsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.Action;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 10: New Tab, Close Pane, Close Tab and the tab commands, run as actions. */
class TabCommandsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void newTabOpensATabHoldingOneTerminal() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.workspace().tabs()).as("one terminal in the new tab").hasSize(1);
            assertThat(owner.tabCount()).isEqualTo(2);
        });
    }

    /** Review Focus 1. */
    @Test void newTabInTheEmptyMainWorkspaceFillsItsOnlyTab() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            main.invoke(ActionId.NEW_TAB);
            assertThat(main.parentTabCount()).isEqualTo(1);
            assertThat(main.tabCount()).isEqualTo(1);
        });
    }

    /** Rulings 15: Next and Previous Tab and Select Tab 1 to 9 act on the window's tabs; Close Tab closes the shown one. */
    @Test void tabsSwitchByNumberAndInTurnAndCloseByCommand() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabId first = owner.activeParentTab();
            owner.updateActions();
            assertThat(owner.action(ActionId.NEXT_TAB).isEnabled()).as("one tab").isFalse();
            assertThat(owner.action(ActionId.PREVIOUS_TAB).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.SELECT_TAB_1).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.SELECT_TAB_2).isEnabled()).isFalse();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId second = owner.activeParentTab();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId third = owner.activeParentTab();
            assertThat(owner.action(ActionId.SELECT_TAB_3).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.NEXT_TAB).isEnabled()).isTrue();
            owner.invoke(ActionId.SELECT_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.invoke(ActionId.PREVIOUS_TAB);
            assertThat(owner.activeParentTab()).as("wraps to the last").isEqualTo(third);
            owner.invoke(ActionId.NEXT_TAB);
            assertThat(owner.activeParentTab()).as("and back to the first").isEqualTo(first);
            owner.invoke(ActionId.SELECT_TAB_9);
            assertThat(owner.activeParentTab()).as("nothing past the last").isEqualTo(first);
            owner.invoke(ActionId.SELECT_TAB_2);
            owner.invoke(ActionId.CLOSE_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(owner.activeParentTab()).as("the tab used before it").isEqualTo(first);
        });
    }

    /** Spec 10: Close Pane closes the focused pane and the tab with its last; Close Tab closes every pane of the tab. */
    @Test void closePaneClosesTheFocusedPaneAndCloseTabTheWholeTab() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_RIGHT);
            assertThat(owner.workspace().model().groups()).hasSize(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.workspace().model().groups()).as("the focused pane went").hasSize(1);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.parentTabCount()).as("the tab went with its last pane").isEqualTo(1);
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_DOWN);
            var doomed = List.copyOf(owner.workspace().tabs());
            assertThat(doomed).hasSize(2);
            owner.invoke(ActionId.CLOSE_TAB);
            assertThat(doomed).as("Close Tab closes every pane of the tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    /** Rulings 15: Close Pane on a plugin's item closes it after its guard, and a refusal keeps it. */
    @Test void closePaneOnAPluginsItemClosesItAfterItsGuard() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            owner.selectTab(view);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(view.closed()).as("a refusal keeps it").isFalse();
            assertThat(owner.workspace().model().groups()).hasSize(2);
            allow[0] = true;
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(view.closed()).isTrue();
            assertThat(owner.workspace().model().groups()).as("its pane went").hasSize(1);
        });
    }

    @Test void theCommandsAreInThePaletteUnderTheirNames() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            for (String id : List.of("new_tab", "close_pane", "close_tab", "next_tab", "previous_tab", "select_tab_1",
                    "maximize_pane", "focus_pane_left"))
                assertThat(owner.commands().find(id)).as(id).isPresent();
            for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab",
                    "select_parent_tab_1", "close_group"))
                assertThat(owner.commands().find(id)).as("a clean break: %s", id).isEmpty();
            assertThat(owner.action(ActionId.CLOSE_PANE).getValue(Action.NAME)).isEqualTo("Close Pane");
            assertThat(owner.action(ActionId.CLOSE_TAB).getValue(Action.NAME)).isEqualTo("Close Tab");
            assertThat(owner.action(ActionId.SELECT_TAB_1).getValue(Action.NAME)).isEqualTo("Select Tab 1");
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TabShortcutsTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Tabs and panes spec 10: the tab keys on each platform, and where they run while a plugin's view has the focus. */
class TabShortcutsTest {
    /** The digits and the bracket keys (Ctrl+PageUp and Ctrl+PageDown off macOS) switch tabs; Ctrl+Tab is no default. */
    @Test void platformDefaultsSwitchTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys);
            try {
                int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke next = keys.strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyStroke previous = keys.strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                assertThat(next).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
                assertThat(previous).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
                assertThat(keys.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)))
                    .as("Ctrl+Tab has no default").isEmpty();
                assertThat(keys.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).isEmpty();
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTerminal();
                owner.invoke(ActionId.NEW_TAB); var second = owner.currentTerminal();
                owner.invoke(ActionId.NEW_TAB); var third = owner.currentTerminal();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isTrue();
                assertThat(owner.currentTerminal()).as("Select Tab 1").isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTerminal()).as("Previous Tab wraps").isSameAs(third);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).as("and Next Tab back").isSameAs(first);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, primary)))
                    .as("no ninth tab: the key reaches the focused component").isFalse();
                assertThat(owner.action(ActionId.SELECT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, primary));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isFalse();
            } finally { owner.close(); }
        });
    }

    /**
     * Review Focus 5: in a plugin's view Next and Previous Tab run before the view's own bindings, such as a scroll pane's
     * Ctrl+PageDown (Rulings 16), and Ctrl+Tab, which no action holds, reaches the view.
     */
    @Test void nextAndPreviousTabRunBeforeAPluginViewsOwnBindingsAndCtrlTabReachesTheView() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = CommandPaletteShortcutsTest.owner(mac)) {
                var model = new Contributions(); owner.connectContributions(model);
                var area = new JTextArea();
                Tab view = model.tabs().open("dev.x", "a", "A", owner.id(), new JScrollPane(area));
                owner.invoke(ActionId.NEW_TAB);
                owner.selectTab(view);
                ParentTabId shown = owner.activeParentTab();
                CommandPaletteShortcutsTest.install(owner);
                assertThat(owner.selectedContent()).as("the plugin's view shows").isSameAs(view);
                KeyEvent ctrlTab = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(ctrlTab)).as("Ctrl+Tab is the view's").isFalse();
                assertThat(ctrlTab.isConsumed()).isFalse();
                assertThat(owner.activeParentTab()).isEqualTo(shown);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));
                KeyStroke next = owner.bindings().strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyEvent press = PaletteKeyRouterTest.press(area, next.getKeyCode(), next.getModifiers());
                assertThat(manager.send(press)).as("Next Tab").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(shown);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, next.getKeyCode()))).as("its release").isTrue();
                owner.selectTab(view);
                KeyStroke previous = owner.bindings().strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                KeyEvent back = PaletteKeyRouterTest.press(area, previous.getKeyCode(), previous.getModifiers());
                assertThat(manager.send(back)).as("Previous Tab").isTrue();
                assertThat(back.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(shown);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, previous.getKeyCode()))).as("its release").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_A, 0))).as("the next key types as ever").isFalse();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    @Test void braceOverrideUsesSameDispatchAndKeepsNativeEditingExemption() throws Exception {
        edt(() -> {
            var bindings = KeyBindings.withOverrides(false, Map.of("previous_tab", "alt+{", "next_tab", "alt+}"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), bindings)) {
                var first = owner.currentTerminal(); owner.invoke(ActionId.NEW_TAB);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
                    InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), owner)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(first);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), new JTextField())).isFalse();
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), owner)).isFalse();
            }
        });
    }

    /** A Ctrl+Tab binding still runs before focus traversal can take the key; plain Tab, no Keymap binding, traverses. */
    @Test void aCtrlTabBindingRunsBeforeFocusTraversalCanTakeIt() throws Exception {
        edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            var keys = KeyBindings.withOverrides(false, Map.of("next_tab", "ctrl+tab", "previous_tab", "ctrl+shift+tab"));
            try (var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys, new CommandHistory(), false)) {
                var model = new Contributions(); owner.connectContributions(model);
                var editor = new JPanel(); var area = new JTextArea(); var field = new JTextField();
                editor.add(area); editor.add(field);
                Tab view = model.tabs().open("dev.x", "a", "A", owner.id(), editor);
                owner.invoke(ActionId.NEW_TAB);
                owner.selectTab(view);
                ParentTabId shown = owner.activeParentTab();
                CommandPaletteShortcutsTest.install(owner);
                assertThat(manager.dispatchers).hasSize(1);
                AWTKeyStroke ctrlTab = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(area.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)).contains(ctrlTab);

                KeyEvent press = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(press)).as("Ctrl+Tab press").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).as("Next Tab").isNotEqualTo(shown);
                KeyEvent typed = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, '\t');
                assertThat(manager.send(typed)).as("its typed tail").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB))).as("its release").isTrue();

                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).as("Ctrl+Shift+Tab").isTrue();
                assertThat(owner.activeParentTab()).as("Previous Tab").isEqualTo(shown);
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));

                for (var focus : java.util.List.<java.awt.Component>of(area, field)) {
                    KeyEvent tab = PaletteKeyRouterTest.press(focus, KeyEvent.VK_TAB, 0);
                    assertThat(manager.send(tab)).as("plain Tab is no Keymap binding").isFalse();
                    assertThat(tab.isConsumed()).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.typed(focus, '\t'))).isFalse();
                    assertThat(manager.send(PaletteKeyRouterTest.release(focus, KeyEvent.VK_TAB))).isFalse();
                }
                assertThat(owner.activeParentTab()).isEqualTo(shown);
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TabMenuTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.ContentCommand;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Tabs and panes spec 6 and 10: a tab's menu, Close Tab and Close Pane, and the menus that name them. */
class TabMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with {@code count} tabs, each holding one running terminal. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            for (int i = 1; i < count; i++) owner[0].invoke(ActionId.NEW_TAB);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /** A plugin's item in a new pane to the right of {@code window}'s focused pane, in its shown tab. */
    private Tab beside(WindowContent window, String key) {
        return model.tabs().open("dev.example.editor", key, key + ".txt", window.id(), new JPanel(),
            TabRegistry.Placement.SPLIT_RIGHT);
    }

    private static List<String> titles(JPopupMenu menu) {
        var titles = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) titles.add(item.getText());
        return titles;
    }

    private static JMenuItem item(JPopupMenu menu, String title) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(title)) return item;
        throw new AssertionError("no " + title + " in " + titles(menu));
    }

    /** The plain items of the menu bar's menu called {@code title}; its submenus are left out. */
    private static List<String> items(WindowContent window, String title) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !title.equals(menu.getText())) continue;
            var items = new ArrayList<String>();
            for (var child : menu.getMenuComponents())
                if (child instanceof JMenuItem item && !(child instanceof JMenu)) items.add(item.getText());
            return items;
        }
        throw new AssertionError("no " + title + " menu");
    }

    /** Workspace plan 8a Rulings 8: Rename Tab for a terminal, Move Tab to New Window, Close Tab, the item's commands. */
    @Test void aTabsMenuRenamesMovesAndClosesItThenListsItsItemsCommands() throws Exception {
        var window = window(1);
        edt(() -> {
            var keys = window.bindings();
            var menu = window.tabMenu(window.selectedTab());
            assertThat(titles(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
            assertThat(item(menu, "Rename Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.RENAME_TAB).orElseThrow());
            assertThat(item(menu, "Close Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            Tab view = beside(window, "notes");
            int[] saved = {0};
            view.commands(List.of(new ContentCommand("save", "Save", null, true, () -> saved[0]++)));
            var hosted = window.tabMenu(view);
            assertThat(titles(hosted)).as("a plugin's item has no Rename Tab").containsExactly("Move Tab to New Window",
                "Close Tab", "Save");
            item(hosted, "Save").doClick();
            assertThat(saved[0]).isEqualTo(1);
            var full = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            assertThat(full.windowMenuItems()).as("a full window's window menu").contains(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(titles(full.tabMenu(full.selectedTab()))).as("Move to Main Window moves the whole window: not here")
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
        });
    }

    /** Review Focus 2: Close Tab asks the guards of the tab's plugin items once, and a refusal closes nothing. */
    @Test void closeTabAsksTheGuardsFirstAndARefusalClosesNothing() throws Exception {
        var window = window(2);
        edt(() -> {
            ParentTabId second = window.activeParentTab();
            Tab other = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            Tab terminal = window.selectedTab();
            Tab view = beside(window, "notes");
            assertThat(window.parentTabOf(view)).as("one tab holds both").isEqualTo(window.parentTabOf(terminal));
            var asked = new ArrayList<List<Tab>>();
            boolean[] allow = {false};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            window.invoke(ActionId.CLOSE_TAB);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(terminal.closed()).as("a refusal closes nothing, terminals included").isFalse();
            assertThat(window.tabs()).containsExactly(terminal, view, other);
            allow[0] = true;
            item(window.tabMenu(terminal), "Close Tab").doClick();
            assertThat(asked).hasSize(2);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(window.activeParentTab()).as("the tab went with its panes").isEqualTo(second);
        });
    }

    /** Runs {@code body} with the tab registry's log captured: what it logged. */
    private static List<java.util.logging.LogRecord> registryLog(Runnable body) {
        var logger = java.util.logging.Logger.getLogger(dev.jasper.app.contributions.TabRegistry.class.getName());
        var records = new java.util.concurrent.CopyOnWriteArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try { body.run(); }
        finally { logger.removeHandler(handler); logger.setUseParentHandlers(parents); }
        return records;
    }

    @Test void closeTabClosesEveryPaneWhenOneItemsCloseListenerThrows() throws Exception {
        var window = window(2);
        edt(() -> {
            Tab other = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            ParentTabId first = window.activeParentTab();
            Tab terminal = window.selectedTab();
            Tab view = beside(window, "notes");
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            terminal.onClosed(() -> { throw new IllegalStateException("close failed"); });
            var logged = registryLog(() -> window.closeParentTab(first));
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).as("the item after the failing one closed too").isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(logged).as("the failure is logged").singleElement()
                .satisfies(record -> assertThat(record.getThrown()).hasMessage("close failed"));
            Tab elsewhere = beside(window, "elsewhere");
            assertThat(elsewhere.requestClose()).as("a later close still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aBatchCloseThatEmptiesTheWindowRunsItsEmptyHandlingAndKeepsOtherClosesWorking() throws Exception {
        var emptied = new ArrayList<String>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            owners[0] = track(new WindowContent(idleLauncher(pending), HOME, path -> {}, () -> {}, () -> {
                emptied.add("empty");
                throw new IllegalStateException("window teardown failed");
            }));
            owners[0].connectContributions(model);
            owners[1] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owners[1].connectContributions(model);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var window = owners[0];
            var terminal = window.tabs().get(0);
            Tab view = beside(window, "notes");
            Tab elsewhere = beside(owners[1], "elsewhere");
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            var logged = registryLog(() -> window.closeParentTab(window.activeParentTab()));
            assertThat(emptied).as("the window's empty handling ran").containsExactly("empty");
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(logged).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("window teardown failed"));
            assertThat(elsewhere.requestClose()).as("a close in another window still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aTerminalOnlyCloseTabClosesWhileAnApprovalIsPendingElsewhere() throws Exception {
        var window = window(2);
        edt(() -> {
            Tab b1 = window.selectedTab();
            Tab view = beside(window, "notes");
            window.invoke(ActionId.SELECT_TAB_1);
            ParentTabId first = window.activeParentTab();
            Tab a1 = window.selectedTab();
            window.split(Side.RIGHT);
            Tab a2 = window.selectedTab();
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var asking = view.requestClose();
            assertThat(asking).as("the guard has not answered").isNotDone();
            window.closeParentTab(first);
            assertThat(a1.closed()).as("terminals need no approval").isTrue();
            assertThat(a2.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(b1, view);
            answer.complete(false);
            assertThat(asking).isCompletedWithValue(false);
            assertThat(view.closed()).isFalse();
        });
    }

    @Test void closePaneAndCloseTabRunFromTheirShortcuts() throws Exception {
        var window = window(2);
        Tab[] tabs = new Tab[3];
        edt(() -> {
            tabs[0] = window.selectedTab();
            window.invoke(ActionId.SELECT_TAB_1);
            tabs[1] = window.selectedTab();
            window.split(Side.RIGHT);
            tabs[2] = window.selectedTab();
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            Tab b1 = tabs[0], a1 = tabs[1], a2 = tabs[2];
            window.selectTab(a1);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_PANE).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).as("Close Pane closes the focused pane").containsExactly(a2, b1);
            window.split(Side.DOWN);
            Tab a3 = window.selectedTab();
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).as("Close Tab closes every pane of the tab").containsExactly(b1);
            assertThat(a3.closed()).isTrue();
        });
    }

    /** Workspace plan 8a Rulings 17. */
    @Test void theFileViewAndTabMenusNameTabsAndPanes() throws Exception {
        var window = window(1);
        edt(() -> {
            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Terminal Window", "Open File…", "Close Pane",
                "Close Tab", "Close Window").doesNotContain("Close Parent Tab");
            assertThat(items(window, "View")).contains("Maximize Pane");
            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Pane Left", "Focus Pane Right",
                "Focus Pane Up", "Focus Pane Down", "Previous Prompt", "Next Prompt", "Rename Tab", "Next Tab", "Previous Tab",
                "Select Tab 1", "Select Tab 2", "Select Tab 3", "Select Tab 4", "Select Tab 5", "Select Tab 6", "Select Tab 7",
                "Select Tab 8", "Select Tab 9");
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tabs and panes spec 10 and 16, the collision check on each platform: no bundled plugin's default shortcut loses to a
 * tab or pane action's, and Move Pane to New Tab's keys (plan 8b) stay free.
 */
class TabShortcutCollisionTest {
    /** The actions of spec 10's table that Jasper defines itself. */
    private static final EnumSet<ActionId> TABS_AND_PANES = EnumSet.of(ActionId.NEW_TAB, ActionId.SPLIT_RIGHT,
        ActionId.SPLIT_DOWN, ActionId.CLOSE_PANE, ActionId.CLOSE_TAB, ActionId.CLOSE_WINDOW, ActionId.NEXT_TAB,
        ActionId.PREVIOUS_TAB, ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,
        ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
        ActionId.MAXIMIZE_PANE, ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);

    @TempDir Path root;
    private PluginRuntime runtime;
    private Contributions contributions;

    @AfterEach void stop() throws Exception {
        if (runtime != null) {
            var stopped = onEdtValue(runtime::stop);
            CompletableFuture.allOf(stopped.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    @Test void theTabAndPaneDefaultsTakeNoBundledPluginsDefaultOnEitherPlatform() throws Exception {
        UnboundVault.settings(root.resolve("plugins")); // the runtime starts the staged Vault, whose keychain stays unused
        onEdt(() -> {
            contributions = new Contributions();
            var buddy = new BuddyTestSupport();
            runtime = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),
                root.resolve("plugins"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(buddy.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new TerminalRegistry());
            runtime.start(Map.of(), true);
        });
        onEdt(() -> {
            var extensions = contributions.actions().stream().map(ActionEntry::extension).toList();
            assertThat(extensions).as("the bundled plugins' actions").isNotEmpty();
            for (boolean mac : new boolean[]{true, false}) {
                String platform = mac ? "macOS" : "Linux and Windows";
                var resolved = KeyBindings.defaults(mac).withExtensions(extensions);
                for (ActionId action : TABS_AND_PANES)
                    assertThat(resolved.problems()).as("%s on %s", action.id(), platform)
                        .noneMatch(problem -> problem.kind() == KeyBindings.Problem.Kind.DEFAULT_DROPPED
                            && problem.message().contains("'" + action.id() + "'"));
                var movePane = KeyBindings.parse(mac ? "alt+cmd+t" : "ctrl+alt+shift+t", mac).orElseThrow();
                assertThat(resolved.bindings().idsFor(movePane)).as("Move Pane to New Tab's keys on %s", platform).isEmpty();
            }
        });
    }
}
```

Run the edit script for the other tests:

```bash
python3 -I - <<'PY'
import pathlib, sys; sys.path.insert(0, "build/plan8a")
from edits import edit, span, regex
import ledger
T = "jasper-app/src/test/java/dev/jasper/app/"
W = T + "workspace/"
pathlib.Path(W + "ParentTabCommandsTest.java").unlink()
ledger.rename(W + "ParentTabCommandsTest.java", W + "TabCommandsTest.java", "TabCommandsTest")
span(T + "config/KeymapDefaultsTest.java",
     '        new Row("new_tab", KeyContext.GLOBAL, "cmd+t", "ctrl+shift+t"),                        // 22; parent tabs spec 8: a parent tab\n',
     '        new Row("select_tab_9", KeyContext.GLOBAL, "none", "none"),                            // 27, Select Last Tab in K2\n',
     """        new Row("new_tab", KeyContext.GLOBAL, "cmd+t", "ctrl+shift+t"),                        // 22; tabs and panes spec 10
        new Row("close_pane", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                     // 23; tabs and panes spec 10
        new Row("close_tab", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),
        new Row("next_tab", KeyContext.GLOBAL, "cmd+shift+]", "ctrl+pagedown"),                // 24; tabs and panes spec 10
        new Row("previous_tab", KeyContext.GLOBAL, "cmd+shift+[", "ctrl+pageup"),              // 25
        new Row("select_tab_1", KeyContext.GLOBAL, "cmd+1", "ctrl+1"),                         // 26; workspace plan 7a Spec issues 1
        new Row("select_tab_2", KeyContext.GLOBAL, "cmd+2", "ctrl+2"),
        new Row("select_tab_3", KeyContext.GLOBAL, "cmd+3", "ctrl+3"),
        new Row("select_tab_4", KeyContext.GLOBAL, "cmd+4", "ctrl+4"),
        new Row("select_tab_5", KeyContext.GLOBAL, "cmd+5", "ctrl+5"),
        new Row("select_tab_6", KeyContext.GLOBAL, "cmd+6", "ctrl+6"),
        new Row("select_tab_7", KeyContext.GLOBAL, "cmd+7", "ctrl+7"),
        new Row("select_tab_8", KeyContext.GLOBAL, "cmd+8", "ctrl+8"),
        new Row("select_tab_9", KeyContext.GLOBAL, "cmd+9", "ctrl+9"),                         // 27, Select Last Tab in K2
""")
edit(T + "config/KeymapDefaultsTest.java", [
    ("""    /** Every macOS default uses Cmd, but Next and Previous Tab's Ctrl+Tab, as macOS's Terminal and Safari have it. */
    @Test void macDefaultsUseCmdOrCtrlTabAndLeaveMacOsItsKeys() {
""", """    /** Every macOS default uses Cmd, and none takes one of macOS's own keys. */
    @Test void macDefaultsUseCmdAndLeaveMacOsItsKeys() {
""", 1),
    ("""        Set<KeyStroke> tabs = Set.of(KeyBindings.parse("ctrl+tab", true).orElseThrow(), KeyBindings.parse("ctrl+shift+tab", true).orElseThrow());
""", "", 1),
    ("""            if (!tabs.contains(stroke)) assertThat(stroke.getModifiers() & InputEvent.META_DOWN_MASK).as("%s uses Cmd", action).isNotZero();
""", """            assertThat(stroke.getModifiers() & InputEvent.META_DOWN_MASK).as("%s uses Cmd", action).isNotZero();
""", 1),
    ("""    /** Off macOS a default is Ctrl+Shift, Ctrl and a digit, Ctrl+Tab, or Ctrl+PageUp and PageDown, as GNOME Terminal's tabs. */
""", """    /** Off macOS a default is Ctrl+Shift, Ctrl and a digit, or Ctrl+PageUp and PageDown, as GNOME Terminal's tabs. */
""", 1),
    ("""            boolean ctrlTab = key == KeyEvent.VK_TAB && (modifiers & ~InputEvent.SHIFT_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK;
""", "", 1),
    ("            assertThat(ctrlShift || ctrlDigit || ctrlTab || ctrlPage)",
     "            assertThat(ctrlShift || ctrlDigit || ctrlPage)", 1)])
edit(T + "config/KeyBindingsTest.java", [
    ("            ActionId.NEW_TAB, ActionId.NEW_TAB_IN_PANE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE, ActionId.CLOSE_PARENT_TAB,\n",
     "            ActionId.NEW_TAB, ActionId.CLOSE_PANE, ActionId.CLOSE_TAB,\n", 1),
    ("""            ActionId.NEXT_PARENT_TAB, ActionId.PREVIOUS_PARENT_TAB,
            ActionId.SELECT_PARENT_TAB_1, ActionId.SELECT_PARENT_TAB_2, ActionId.SELECT_PARENT_TAB_3,
            ActionId.SELECT_PARENT_TAB_4, ActionId.SELECT_PARENT_TAB_5, ActionId.SELECT_PARENT_TAB_6,
            ActionId.SELECT_PARENT_TAB_7, ActionId.SELECT_PARENT_TAB_8, ActionId.SELECT_PARENT_TAB_9,
""", "", 1),
    ('            .withMessageContaining("copy").withMessageContaining("previous_parent_tab");\n',
     '            .withMessageContaining("copy").withMessageContaining("previous_tab");\n', 1)])
span(T + "config/KeyBindingsTest.java", "    @Test\n    void tabDefaultsFollowEachPlatformsTerminals() {\n",
     "    @Test\n    void braceOverridesResolvePhysicalBracketEventsAndDetectEquivalentCollisions() {\n",
     """    /** Tabs and panes spec 10: each platform's tab and pane keys, and the keys plan 8b's Move Pane to New Tab takes. */
    @Test
    void tabDefaultsFollowEachPlatformsTerminals() {
        for (boolean mac : new boolean[]{true, false}) {
            int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            int shift = InputEvent.SHIFT_DOWN_MASK, alt = InputEvent.ALT_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int number = 1; number <= 9; number++) {
                KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_1 + number - 1, modifier);
                assertThat(bindings.actionFor(stroke)).as("the digits select tabs").contains(ActionId.valueOf("SELECT_TAB_" + number));
            }
            KeyStroke previous = mac ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | shift)
                : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK);
            KeyStroke next = mac ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | shift)
                : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK);
            assertThat(bindings.actionFor(previous)).contains(ActionId.PREVIOUS_TAB);
            assertThat(bindings.actionFor(next)).contains(ActionId.NEXT_TAB);
            KeyStroke closePane = mac ? KeyStroke.getKeyStroke(KeyEvent.VK_W, modifier)
                : KeyStroke.getKeyStroke(KeyEvent.VK_W, modifier | shift);
            KeyStroke closeTab = mac ? KeyStroke.getKeyStroke(KeyEvent.VK_W, modifier | alt)
                : KeyStroke.getKeyStroke(KeyEvent.VK_W, modifier | alt | shift);
            assertThat(bindings.actionFor(closePane)).contains(ActionId.CLOSE_PANE);
            assertThat(bindings.actionFor(closeTab)).contains(ActionId.CLOSE_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK))).as("Ctrl+Tab is free").isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | shift))).isEmpty();
            KeyStroke movePane = mac ? KeyStroke.getKeyStroke(KeyEvent.VK_T, modifier | alt)
                : KeyStroke.getKeyStroke(KeyEvent.VK_T, modifier | alt | shift);
            assertThat(bindings.actionFor(movePane)).as("free for plan 8b's Move Pane to New Tab").isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier))).isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | shift | alt))).isEmpty();
        }
    }

    @Test
    void braceOverridesResolvePhysicalBracketEventsAndDetectEquivalentCollisions() {
""")
edit(T + "config/ConfigLoaderTest.java", [
    ('            next_parent_tab = "cmd+}"\n            previous_parent_tab = "cmd+{"\n',
     '            next_tab = "cmd+}"\n            previous_tab = "cmd+{"\n', 1),
    ("            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_PARENT_TAB);\n",
     "            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_TAB);\n", 1),
    ("""    /** Tabs and panes spec 10: the pane-window and Pop Out ids are a clean break, so each is an unknown action. */
    @Test void aRemovedMoveIdIsReportedAsAnUnknownAction() {
        for (String id : new String[] {"pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window"}) {
""", """    /** Tabs and panes spec 10: the removed ids are a clean break, so each is an unknown action. */
    @Test void aRemovedIdIsReportedAsAnUnknownAction() {
        for (String id : new String[] {"pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window",
                "new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab", "select_parent_tab_1",
                "select_parent_tab_9"}) {
""", 1)])
# The template's literal defaults: raw regexes, as the Java text holds backslashes.
regex(T + "config/ConfigTemplateTest.java", r'"# close_tab = \\"cmd\+w\\""', r'"# close_pane = \\"cmd+w\\""', 1)
regex(T + "config/ConfigTemplateTest.java", r'"# next_tab = \\"ctrl\+tab\\""', r'"# next_tab = \\"ctrl+pagedown\\""', 1)
edit(T + "shortcuthelp/ShortcutHelpTest.java", [
    ("""    /** Parent tabs spec 8: the parent-tab actions join Tabs, and the pane actions are Panes. */
    @Test void theParentTabAndPaneActionsHaveTheirCategories() {
""", """    /** Tabs and panes spec 10: the tab actions are Tabs, and the pane actions are Panes. */
    @Test void theTabAndPaneActionsHaveTheirCategories() {
""", 1),
    ("""        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1",
                "move_tab_to_new_window"))
""", """        for (String id : List.of("new_tab", "close_tab", "next_tab", "select_tab_1", "move_tab_to_new_window"))
""", 1)])
edit(W + "ParentTabBarTest.java", [
    ("            String shortcut = owner.shortcutText(ActionId.SELECT_PARENT_TAB_2);\n",
     "            String shortcut = owner.shortcutText(ActionId.SELECT_TAB_2);\n", 1),
    ('.as("an entry\'s tip carries Select Parent Tab\'s shortcut")', '.as("an entry\'s tip carries Select Tab\'s shortcut")', 1),
    ("            assertThat(source.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_PARENT_TAB_1)).isNotEmpty();\n",
     "            assertThat(source.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_TAB_1)).isNotEmpty();\n", 1),
    ('.as("the bar\'s × closes a parent tab")', '.as("the bar\'s × closes a tab")', 1),
    ('isEqualTo("Close Parent Tab")', 'isEqualTo("Close Tab")', 3),
    ("""            var menu = owner.parentTabMenu(third);
            // This plan's Rulings 7: Move Tab to New Window, then a rule, before Close Parent Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Move Tab to New Window", "Close Parent Tab");
""", """            var menu = owner.tabMenu(third);
            // Workspace plan 8a Rulings 8: Rename Tab and Move Tab to New Window, then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
""", 1),
    ("     * Select Parent Tab 1 (Cmd+1) shows the first parent tab's tab again.\n",
     "     * Select Tab 1 (Cmd+1) shows the first parent tab's tab again.\n", 1),
    ("                owner.invoke(ActionId.SELECT_PARENT_TAB_1);\n", "                owner.invoke(ActionId.SELECT_TAB_1);\n", 1),
    ('.as("Select Parent Tab 1 shows the first parent tab\'s tab again")', '.as("Select Tab 1 shows the first parent tab\'s tab again")', 1),
    ("""                assertThat(((ClassicTabs) owner.tabBar()).add().getToolTipText()).as("a pane's + stacks in its pane")
                    .isEqualTo("New Tab in Pane");
""", """                assertThat(((ClassicTabs) owner.tabBar()).add().getToolTipText()).as("a pane's + opens a tab")
                    .isEqualTo("New Tab");
""", 1)])
edit(W + "WindowMenuTest.java", [
    (".parentTabMenu(", ".tabMenu(", 5),
    ('            assertThat(texts(menu)).containsExactly("Move Tab to New Window", "---", "Close Parent Tab");\n',
     '            assertThat(texts(menu)).containsExactly("Rename Tab", "Move Tab to New Window", "---", "Close Tab");\n', 1),
    # Workspace plan 8a Rulings 8: the entry's menu offers Move Tab to New Window alone of the window menu's entries.
    ('            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isFalse();\n',
     '            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isFalse();\n', 1),
    ('            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isTrue();\n',
     '            assertThat(item(full.tabMenu(inFull), "Move Tab to New Window").isEnabled()).isTrue();\n', 1),
    ('                for (ActionId id : window.windowMenuItems()) assertThat(item(menu, id.label()).isEnabled()).as("its tab\'s %s", id).isFalse();\n',
     '                assertThat(item(menu, ActionId.MOVE_TAB_TO_NEW_WINDOW.label()).isEnabled()).as("its tab\'s Move Tab to New Window").isFalse();\n', 1)])
span(W + "WindowManagerTest.java", "    @Test void aChildWindowsTabMenuMovesToTheMainWindow() throws Exception {\n",
     "        launch();\n    }\n", """    /** Workspace plan 8a Rulings 8: a child window's entry menu leaves Move to Main Window to the ⋮. */
    @Test void aChildWindowsTabMenuLeavesMoveToMainWindowToItsWindowMenu() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            var menu = child.content().tabMenu(child.content().selectedTab());
            var titles = new ArrayList<String>();
            for (var item : menu.getComponents()) if (item instanceof javax.swing.JMenuItem entry) titles.add(entry.getText());
            assertThat(titles).containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
            assertThat(child.content().windowMenuItems()).contains(ActionId.MOVE_TO_MAIN_WINDOW);
        });
        launch();
    }
""")
edit(W + "ClassicTabsTest.java", [(
    """                assertThat(button.getAccessibleName()).as("a pane's + stacks in its pane").isEqualTo("New Tab in Pane");
""", """                assertThat(button.getAccessibleName()).as("the + opens a new tab").isEqualTo("New Tab");
""", 1)])
span(W + "ClassicTabsTest.java",
     """    /** Workspace plan 7a Rulings 16: a pane's "+" is New Tab in Pane, a tab stacked in that pane, active or not. */
""", """                assertThat(owner.workspace().activeGroupView().id()).as("and that pane is active").isEqualTo(left);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
""", """    /** Tabs and panes spec 5 and workspace plan 8a Rulings 11: the strip's "+" is New Tab, a new terminal tab. */
    @Test void thePlusOpensANewTab() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                Tab first = owner.selectedTab();
                ((AbstractButton) ((ClassicTabs) owner.tabBar()).add()).doClick();
                assertThat(owner.parentTabCount()).as("a new tab").isEqualTo(2);
                assertThat(owner.selectedTab()).as("it shows").isNotSameAs(first);
                assertThat(owner.workspace().tabs()).as("holding one terminal").containsExactly(owner.selectedTab());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
""")
edit(W + "WindowTabsTest.java", [
    ("            owner.invoke(ActionId.NEW_TAB_IN_PANE);\n", "            owner.newTab(HOME);\n", 1),
    ("            // Select Tab 1 has no default since the digits select parent tabs (parent tabs spec 8): give it one.\n",
     "            // Select Tab 1's shortcut follows the Keymap: rebind it.\n", 1)])
edit(W + "WorkspaceTabsTest.java", [
    ("        Object[] view = new Object[1];\n", "", 1),
    ("            view[0] = opened;\n", "", 1),
    ("""        assertThat(profiles.getLast()).as("beside a plugin view, the default profile").isEmpty();
        edt(() -> {
            var opened = (dev.jasper.app.contributions.Tab) view[0];
            owner[0].selectTab(opened);
            owner[0].invoke(ActionId.NEW_TAB_IN_PANE);
            var layout = owner[0].workspace().model();
            assertThat(layout.groupOf(owner[0].selectedTab().id())).as("New Tab in Pane opens in the plugin view's group")
                .isEqualTo(layout.groupOf(opened.id()));
        });
        launch();
    }
""", """        assertThat(profiles.getLast()).as("beside a plugin view, the default profile").isEmpty();
    }
""", 1),
    ("""            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), pane.view())).isTrue();
            assertThat(left.closed()).as("the shortcut closed the focused tab").isTrue();
""", """            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_PANE).orElseThrow(), pane.view())).isTrue();
            assertThat(left.closed()).as("the shortcut closed the focused pane").isTrue();
""", 1)])
span(W + "WorkspaceTabsTest.java", "\n    @Test void nextPreviousAndSelectTabActWithinTheActiveGroup() throws Exception {\n",
     """            window.invoke(ActionId.NEXT_TAB);
            assertThat(window.selectedTab()).isSameAs(b1);
        });
    }
""", "")
edit(W + "TerminalContextMenuTest.java", [(
    '                "Maximize Pane", "Close Tab", "---", "Move Tab to New Window");\n',
    '                "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");\n', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.plugins.TabShortcutCollisionTest' --continue`
Expected: FAIL. Everything compiles, as the tests name only constants and methods that exist; the failures include
`KeymapDefaultsTest.everyBuiltInActionHasItsApprovedDefaultsAndContext` (no row for `new_tab_in_pane`),
`TabCommandsTest.tabsSwitchByNumberAndInTurnAndCloseByCommand` (Select Tab 1 selects within the active pane),
`TabMenuTest.aTabsMenuRenamesMovesAndClosesItThenListsItsItemsCommands` (Close Others and Close Pane are listed) and
`TabShortcutsTest.platformDefaultsSwitchTabs` (Next Tab is Ctrl+Tab). `TabShortcutCollisionTest` passes before and
after: it pins the check.

- [ ] **Step 3: Implement the commands, keys and menus**

`ActionId` takes spec 10's table; `WorkspaceActions` runs the tab commands on the window's tabs and Close Pane on the
focused pane's item; the window's key dispatcher runs Next and Previous Tab first (Rulings 16); `tabMenu` becomes the
strip entry's one menu (Rulings 8); the menus read as Rulings 17 lists them.

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a")
from edits import edit, span
A = "jasper-app/src/main/java/dev/jasper/app/"
W = A + "workspace/"
C = W + "WindowContent.java"
edit(A + "commands/ActionId.java", [
    ("""    NEW_TAB_IN_PANE("new_tab_in_pane", "New Tab in Pane", "none", "none"),
    CLOSE_TAB("close_tab", "Close Tab", "cmd+w", "ctrl+shift+w"),
    CLOSE_PANE("close_pane", "Close Pane", "alt+cmd+w", "ctrl+alt+shift+w"),
    CLOSE_PARENT_TAB("close_parent_tab", "Close Parent Tab", "none", "none"),
""", """    CLOSE_PANE("close_pane", "Close Pane", "cmd+w", "ctrl+shift+w"),
    CLOSE_TAB("close_tab", "Close Tab", "alt+cmd+w", "ctrl+alt+shift+w"),
""", 1),
    ("""    NEXT_PARENT_TAB("next_parent_tab", "Next Parent Tab", "cmd+shift+]", "ctrl+pagedown"),
    PREVIOUS_PARENT_TAB("previous_parent_tab", "Previous Parent Tab", "cmd+shift+[", "ctrl+pageup"),
    SELECT_PARENT_TAB_1("select_parent_tab_1", "Select Parent Tab 1", "cmd+1", "ctrl+1"),
    SELECT_PARENT_TAB_2("select_parent_tab_2", "Select Parent Tab 2", "cmd+2", "ctrl+2"),
    SELECT_PARENT_TAB_3("select_parent_tab_3", "Select Parent Tab 3", "cmd+3", "ctrl+3"),
    SELECT_PARENT_TAB_4("select_parent_tab_4", "Select Parent Tab 4", "cmd+4", "ctrl+4"),
    SELECT_PARENT_TAB_5("select_parent_tab_5", "Select Parent Tab 5", "cmd+5", "ctrl+5"),
    SELECT_PARENT_TAB_6("select_parent_tab_6", "Select Parent Tab 6", "cmd+6", "ctrl+6"),
    SELECT_PARENT_TAB_7("select_parent_tab_7", "Select Parent Tab 7", "cmd+7", "ctrl+7"),
    SELECT_PARENT_TAB_8("select_parent_tab_8", "Select Parent Tab 8", "cmd+8", "ctrl+8"),
    SELECT_PARENT_TAB_9("select_parent_tab_9", "Select Parent Tab 9", "cmd+9", "ctrl+9"),
    NEXT_TAB("next_tab", "Next Tab", "ctrl+tab", "ctrl+tab"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "ctrl+shift+tab", "ctrl+shift+tab"),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "none", "none"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "none", "none"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "none", "none"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "none", "none"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "none", "none"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "none", "none"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "none", "none"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "none", "none"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "none", "none"),
""", """    NEXT_TAB("next_tab", "Next Tab", "cmd+shift+]", "ctrl+pagedown"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "cmd+shift+[", "ctrl+pageup"),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "cmd+1", "ctrl+1"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "cmd+2", "ctrl+2"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "cmd+3", "ctrl+3"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "cmd+4", "ctrl+4"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "cmd+5", "ctrl+5"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "cmd+6", "ctrl+6"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "cmd+7", "ctrl+7"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "cmd+8", "ctrl+8"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9", "ctrl+9"),
""", 1)])
edit(A + "layout/ParentTabs.java", [(
    "     * The parent tab {@code delta} places along the bar from the one that shows, wrapping around (Next and Previous\n     * Parent Tab).\n",
    "     * The parent tab {@code delta} places along the strip from the one that shows, wrapping around (Next and Previous\n     * Tab).\n", 1)])
edit(A + "shortcuthelp/ShortcutCatalog.java", [(
    """            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW,
                NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
                SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9,
                SELECT_TAB_1, SELECT_TAB_2,
                SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5, SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> "Tabs";
""", """            case NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW, SELECT_TAB_1, SELECT_TAB_2,
                SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5, SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> "Tabs";
""", 1)])
edit(W + "WorkspaceActions.java", [
    ("            if (id == ActionId.CLOSE_TAB) { content.requestClose(); return; }\n",
     "            if (id == ActionId.CLOSE_PANE) { content.requestClose(); return; }\n", 1),
    ("""            case NEW_TAB_IN_PANE -> owner.newTab(owner.directory());
            case CLOSE_PARENT_TAB -> owner.closeParentTab(owner.activeParentTab());
            case NEXT_PARENT_TAB -> owner.selectParentTabRelative(1);
            case PREVIOUS_PARENT_TAB -> owner.selectParentTabRelative(-1);
            case SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                 SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9 ->
                owner.selectParentTabAt(id.ordinal() - ActionId.SELECT_PARENT_TAB_1.ordinal());
""", """            case CLOSE_TAB -> owner.closeParentTab(owner.activeParentTab());
            case NEXT_TAB -> owner.selectParentTabRelative(1);
            case PREVIOUS_TAB -> owner.selectParentTabRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                owner.selectParentTabAt(id.ordinal() - ActionId.SELECT_TAB_1.ordinal());
""", 1),
    ("""            case CLOSE_TAB -> owner.closeTab(owner.selectedTab());
            case CLOSE_PANE -> owner.closeGroup(owner.selectedTab());
""", """            case CLOSE_PANE -> owner.closeTab(owner.selectedTab());
""", 1),
    ("""            case NEXT_TAB -> owner.selectRelative(1);
            case PREVIOUS_TAB -> owner.selectRelative(-1);
            case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                owner.selectInGroup(id.ordinal() - ActionId.SELECT_TAB_1.ordinal());
""", "", 1),
    ("""                boolean contentEnabled = content != null && (id == ActionId.CLOSE_TAB
                    || id == ActionId.NEXT_TAB || id == ActionId.PREVIOUS_TAB || contentKey != null && content.commands().stream().anyMatch(c -> c.id().equals(contentKey) && c.enabled()));
""", """                // A plugin's item closes through its own close request (workspace plan 8a Rulings 15).
                boolean contentEnabled = content != null && (id == ActionId.CLOSE_PANE
                    || contentKey != null && content.commands().stream().anyMatch(c -> c.id().equals(contentKey) && c.enabled()));
""", 1),
    ("                    case COMMAND_PALETTE, NEW_TAB, NEW_TAB_IN_PANE, NEW_TERMINAL_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT,\n",
     "                    case COMMAND_PALETTE, NEW_TAB, NEW_TERMINAL_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT,\n", 1),
    ("""                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.groupTabCount();
                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null;
                    case NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB -> owner.parentTabCount() > 1;
                    case SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                         SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_PARENT_TAB_1.ordinal() < owner.parentTabCount();
""", """                    case CLOSE_TAB -> owner.selectedTab() != null;
                    case NEXT_TAB, PREVIOUS_TAB -> owner.parentTabCount() > 1;
                    case SELECT_TAB_1, SELECT_TAB_2, SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5,
                         SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.parentTabCount();
""", 1)])
edit(C, [
    ("""     * Runs a root-bound shortcut whose key is also one of the focus owner's focus-traversal keys before focus traversal
     * takes it, such as Ctrl+Tab and Ctrl+Shift+Tab in a text area, a table or a field, and Next and Previous Parent Tab
     * before the focus owner's own bindings, such as a scroll pane's Ctrl+PageUp and Ctrl+PageDown (workspace plan 7a
     * Rulings 14); it swallows that key's typed and released tail wherever focus went. Plain Tab is no Keymap binding, so
     * it still traverses.
""", """     * Runs a root-bound shortcut whose key is also one of the focus owner's focus-traversal keys before focus traversal
     * takes it, such as a Ctrl+Tab binding in a text area, a table or a field, and Next and Previous Tab before the focus
     * owner's own bindings, such as a scroll pane's Ctrl+PageUp and Ctrl+PageDown (workspace plan 8a Rulings 16); it
     * swallows that key's typed and released tail wherever focus went. Plain Tab is no Keymap binding, so it still
     * traverses.
""", 1),
    ("                if (!ownsKeySource(focus) || !(traversalKey(focus, event) || parentTabKey(stroke))\n",
     "                if (!ownsKeySource(focus) || !(traversalKey(focus, event) || tabKey(stroke))\n", 1),
    ("""    /** Whether {@code stroke} is Next or Previous Parent Tab's, which run before the focus owner's own bindings. */
    private boolean parentTabKey(KeyStroke stroke) {
        String bound = rootKeys.get(stroke);
        return ActionId.NEXT_PARENT_TAB.id().equals(bound) || ActionId.PREVIOUS_PARENT_TAB.id().equals(bound);
    }
""", """    /** Whether {@code stroke} is Next or Previous Tab's, which run before the focus owner's own bindings. */
    private boolean tabKey(KeyStroke stroke) {
        String bound = rootKeys.get(stroke);
        return ActionId.NEXT_TAB.id().equals(bound) || ActionId.PREVIOUS_TAB.id().equals(bound);
    }
""", 1),
    ("""    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    /** Select Parent Tab {@code index + 1}'s shortcut as this platform writes it; "" past 9 or while it has none. */
    String parentTabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        return shortcutText(ActionId.valueOf("SELECT_PARENT_TAB_" + (index + 1)));
    }
""", """    /** Select Tab {@code index + 1}'s shortcut as this platform writes it; "" past 9 or while it has none. */
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        return shortcutText(ActionId.valueOf("SELECT_TAB_" + (index + 1)));
    }
""", 1),
    ("""    /** The active group's tab count. */
    int groupTabCount() { return workspace().activeGroupView().tabs().size(); }
""", "", 1),
    ("""    /** Select Parent Tab 1 to 9: the parent tab at {@code index} of the bar; nothing past the last. */
""", """    /** Select Tab 1 to 9: the tab at {@code index} of the strip; nothing past the last. */
""", 1),
    ("""    /** Next and Previous Parent Tab: the parent tab {@code delta} places along the bar, wrapping around. */
""", """    /** Next and Previous Tab: the tab {@code delta} places along the strip, wrapping around. */
""", 1),
    ("""     * Close Parent Tab (parent tabs spec 8): every tab of the parent tab {@code id} as one gesture, after their guards; the
     * parent tab goes with its last tab (spec 4).
""", """     * Close Tab (tabs and panes spec 10): the items of every pane of the tab {@code id} as one gesture, after their
     * guards; the tab goes with its last pane (spec 4).
""", 1),
    ("""    /** Selects the active group's tab at {@code index}; nothing past its last tab. */
    void selectInGroup(int index) {
        var group = workspace().activeGroupView().tabs();
        if (index >= 0 && index < group.size()) selectTab(group.get(index));
    }

    /** Selects the active group's tab {@code delta} places from the selected one, wrapping around. */
    void selectRelative(int delta) {
        var view = workspace().activeGroupView();
        var group = view.tabs();
        if (group.isEmpty()) return;
        selectTab(group.get(Math.floorMod(Math.max(0, group.indexOf(view.selected())) + delta, group.size())));
    }

""", "", 1),
    ("""    /** The tabs of {@code tab}'s group, in strip order. */
    private java.util.List<Tab> groupTabs(Tab tab) {
        return parentTabs.holding(tab).flatMap(view -> view.model().groupOf(tab.id()).map(view::groupView))
            .map(group -> java.util.List.copyOf(group.tabs())).orElse(java.util.List.of());
    }

    /** Closes the other tabs of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeOthers(Tab tab) { closeTabs(groupTabs(tab).stream().filter(other -> other != tab).toList()); }

    /** Closes every tab of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeGroup(Tab tab) { if (tab != null) closeTabs(groupTabs(tab)); }

""", "", 1)])
span(C, """    /**
     * A tab's menu (workspace tabs spec 11; tabs and panes spec 6; workspace plan 7b Rulings 15): Rename Tab for a terminal
""", """        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * The window menu's entries (tabs and panes spec 6; workspace plan 8a Rulings 7)""", """    /**
     * A tab's menu, from its strip entry (tabs and panes spec 6 and 10; workspace plan 8a Rulings 8), for the tab holding
     * {@code item}: Rename Tab while {@code item} is a terminal, Move Tab to New Window, a rule, Close Tab, then
     * {@code item}'s own commands. Move to Main Window moves the whole window, so the window menu and the context menu
     * offer it alone. Empty for an item this window does not hold.
     *
     * @param item the item the entry shows, its tab's active pane's
     * @return the menu
     */
    JPopupMenu tabMenu(Tab item) {
        var menu = new JPopupMenu();
        parentTabOf(item).ifPresent(parent -> {
            if (item.kind() == Tab.Kind.TERMINAL)
                menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(item); invoke(ActionId.RENAME_TAB); }));
            menu.add(windowItem(ActionId.MOVE_TAB_TO_NEW_WINDOW, item));
            menu.addSeparator();
            menu.add(tabMenuItem(ActionId.CLOSE_TAB.label(), ActionId.CLOSE_TAB, () -> closeParentTab(parent)));
            if (!item.commands().isEmpty()) menu.addSeparator();
            for (var command : item.commands()) {
                var entry = new JMenuItem(command.title()); entry.putClientProperty("html.disable", true);
                entry.setEnabled(command.enabled()); entry.setAccelerator(command.shortcut());
                entry.addActionListener(event -> command.run().run());
                menu.add(entry);
            }
        });
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * The window menu's entries (tabs and panes spec 6; workspace plan 8a Rulings 7)""")
edit(W + "ParentTabBar.java", [
    ("""    /** Select Parent Tab's shortcut for the entry at {@code index}. */
    @Override public String shortcut(int index) { return owner.parentTabShortcut(index); }
""", """    /** Select Tab's shortcut for the entry at {@code index}. */
    @Override public String shortcut(int index) { return owner.tabShortcut(index); }
""", 1),
    ("    @Override public JPopupMenu menu(Tab tab) { return owner.parentTabMenu(tab); }\n",
     "    @Override public JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }\n", 1),
    ("""    /** Close Parent Tab: an entry's × closes every tab of its parent tab. */
    @Override public String closeName() { return ActionId.CLOSE_PARENT_TAB.label(); }
""", """    /** Close Tab: an entry's × closes every pane of its tab. */
    @Override public String closeName() { return ActionId.CLOSE_TAB.label(); }
""", 1)])
edit(W + "GroupView.java", [(
    """    /** The classic tabs' "+": a new terminal tab in this group. */
    @Override public void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB_IN_PANE); }
    /** New Tab in Pane: this pane's + stacks a tab in it. */
    @Override public String newTabName() { return ActionId.NEW_TAB_IN_PANE.label(); }
""", """    /** The classic tabs' "+": New Tab, a new terminal tab (tabs and panes spec 5). */
    @Override public void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: the "+" opens a tab. */
    @Override public String newTabName() { return ActionId.NEW_TAB.label(); }
""", 1)])
edit(W + "ClassicTabs.java", [
    (" * tabs a crowded strip clips) that opens what its source names (New Tab in Pane in a pane's strip, New Tab in the bar);\n",
     " * tabs a crowded strip clips) that opens a new tab (New Tab);\n", 1),
    ("""        // The "+" says what it opens: New Tab in Pane in a pane's strip, New Tab in the parent-tab bar.
""", """        // The "+" says what it opens: New Tab.
""", 1)])
edit(W + "WindowChrome.java", [
    ("""        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_TERMINAL_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,
            ActionId.CLOSE_PARENT_TAB, ActionId.CLOSE_WINDOW, ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
""", """        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_TERMINAL_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_PANE, ActionId.CLOSE_TAB,
            ActionId.CLOSE_WINDOW, ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
""", 1),
    ("""    /** The Tab menu's pane and prompt items, before its tab and parent tab items (workspace plan 3b Rulings 7). */
""", """    /** The Tab menu's pane and prompt items, before Rename Tab and its tab items (workspace plan 8a Rulings 17). */
""", 1),
    ("""    private static final ActionId[] TAB_ITEMS = {ActionId.NEW_TAB_IN_PANE, ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB};
    /** The parent tab items (parent tabs spec 8): Select Parent Tab 1 to 9 hold the digits Select Tab had. */
    private static final ActionId[] PARENT_TAB_ITEMS = {ActionId.NEXT_PARENT_TAB, ActionId.PREVIOUS_PARENT_TAB,
        ActionId.SELECT_PARENT_TAB_1, ActionId.SELECT_PARENT_TAB_2, ActionId.SELECT_PARENT_TAB_3, ActionId.SELECT_PARENT_TAB_4,
        ActionId.SELECT_PARENT_TAB_5, ActionId.SELECT_PARENT_TAB_6, ActionId.SELECT_PARENT_TAB_7, ActionId.SELECT_PARENT_TAB_8,
        ActionId.SELECT_PARENT_TAB_9};

    /** Tab: splitting and focusing panes and moving between prompts, a rule, the tab commands, a rule, the parent tab commands. */
    private JMenu tabMenu() {
        JMenu menu = menu("Tab", GROUP_ITEMS);
        menu.addSeparator();
        for (ActionId id : TAB_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
        menu.addSeparator();
        for (ActionId id : PARENT_TAB_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
        return menu;
    }
""", """    /** The tab items (tabs and panes spec 10): Next Tab, Previous Tab and Select Tab 1 to 9 switch the window's tabs. */
    private static final ActionId[] TAB_ITEMS = {ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.SELECT_TAB_1,
        ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4, ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6,
        ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9};

    /** Tab: splitting and focusing panes and moving between prompts, a rule, Rename Tab, a rule, the tab commands. */
    private JMenu tabMenu() {
        JMenu menu = menu("Tab", GROUP_ITEMS);
        menu.addSeparator();
        menu.add(new MenuBarItems.Item(owner.action(ActionId.RENAME_TAB)));
        menu.addSeparator();
        for (ActionId id : TAB_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
        return menu;
    }
""", 1),
    ("            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_TAB}) menu.add(owner.action(id));\n",
     "            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));\n", 1)])
edit(W + "WindowCommands.java", [
    ('                case NEW_TAB, NEW_TAB_IN_PANE -> "square-plus"; case NEW_TERMINAL_WINDOW -> "app-window";\n',
     '                case NEW_TAB -> "square-plus"; case NEW_TERMINAL_WINDOW -> "app-window";\n', 1),
    ("""                case NEW_TAB -> List.of("parent tab", "terminal");
                case NEW_TAB_IN_PANE -> List.of("stack", "pane", "terminal");
                case CLOSE_PARENT_TAB, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB -> List.of("parent tab");
""", """                case NEW_TAB -> List.of("terminal");
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.plugins.TabShortcutCollisionTest' --tests 'dev.jasper.app.plugins.RemoteShortcutConfigTest' --continue`
Expected: PASS. Then `./gradlew :jasper-app:check`: PASS (the ledger test sees `TabCommandsTest`'s row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: the commands and keys of spec 10

Close Pane takes Cmd+W and closes the focused pane's item; Close
Tab takes Alt+Cmd+W and closes the whole tab after its guards. Next
Tab, Previous Tab and Select Tab 1-9 switch tabs with the parent-tab
keys, and the parent-tab and stacking ids go with no alias. A strip
entry's one menu acts on its tab, and the strip's "+" is New Tab.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Tabs hold panes: one item per pane, the window's one strip

Spec 2, 4, 5 and 13: a pane holds one item, so the layout loses every stacking operation and a pane's group holds at
most one tab (Rulings 2); whatever opens without a pane opens as a new tab (Rulings 3); the parent-tab bar becomes the
window's only tab strip, shown from two tabs under the equal tabs and always under the classic tabs (Rulings 10), and
no pane shows a strip; `StripSource` folds into `ParentTabBar` (Rulings 9); the window's strip takes the open and
close motion the pane strips had, an entry keeping its place while its tab shows another pane (Rulings 20). The
registry's `ACTIVE_GROUP` becomes `NEW_TAB`, and the SDK keeps its names with Javadoc that says what the app does
(Rulings 4). `workspace.toml` keeps its format; a pane listing two tabs is a bad entry (6a impact, Review Focus 4).

**Files:**
- Replace: `jasper-app/src/main/java/dev/jasper/app/layout/Group.java`; `workspace/GroupView.java`,
  `WorkspaceView.java`, `WindowTabs.java`, `TabStrip.java`, `ParentTabBar.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/workspace/StripSource.java` (and its ledger row)
- Modify: `layout/Layout.java` (class Javadoc, fields, constructors, `tabs`, `open`, `move`, `remove`,
  `moveToNewGroup`, the `tabOrders` uses, `checkIndex`, `tabOrder`); `workspace/ParentTabsView.java` (class Javadoc,
  `land`, `refresh`, `setAnimations`, `revalidateStrips`, `close`); `workspace/ClassicTabs.java` (class Javadoc, `source`,
  two `{@link StripSource}`s); `workspace/WorkspaceChrome.java` (`tabs`, `showsStrip`,
  `showsParentTabBar`); `workspace/WindowContent.java` (the workspace host, `attach`, `selectedTab` and `tabs`
  Javadoc, `parentTabBar`, `windowTabs`, `tabBar`, `newTab`, `newParentTab`, `openTab`, `openTerminal`, `split`,
  `openBeside`); `workspace/WorkspaceActions.java` (`NEW_TAB`);
  `workspace/TabDrag.java` (`parentTabBar()` x4); `layout/ParentTab.java`, `ParentTabs.java`, `GroupId.java`,
  `package-info.java` and `contributions/Tab.java` (Javadoc: what each is, Rulings 1); `contributions/TabRegistry.java` (`Placement`, Javadoc, `open`, `move`'s
  Javadoc); `contributions/WorkspaceHost.java` (Javadoc); `plugins/HostedUi.java` (`ACTIVE_GROUP`'s mapping);
  `application/JasperApplication.java` (`open`'s Javadoc); `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java`,
  `ui/Windows.java`, `ui/ContentTab.java`, `terminal/Terminals.java` (`openTab`, `split`, `activePane`),
  `terminal/WindowHandle.java`, `terminal/Direction.java`, `terminal/TerminalEvents.java` (`TAB_SELECTED`) (Javadoc
  only)
- Test: replace `layout/LayoutOperationsTest.java`, `workspace/WorkspaceViewTest.java`, `workspace/ParentTabBarTest.java`
  and `workspace/WindowTabMotionPreview.java` whole; edit `workspace/WindowTabMotionTest.java` (on the window's
  strip); `layout/LayoutFixtures.java`, `LayoutGeometryTest.java`, `LayoutTest.java`, `LayoutValuesTest.java`,
  `ParentTabsTest.java`; `persistence/WorkspaceFileTest.java`, `WorkspaceFileReadTest.java`;
  `application/JasperApplicationWindowsTest.java`; `workspace/ClassicTabsTest.java`, `WindowTabsTest.java`,
  `HostedContentTest.java`, `TerminalContentTest.java`, `ConfigurationControllerTest.java`, `ThemeControllerTest.java`,
  `TabMoveTest.java`, `WorkspaceTabsTest.java`, `ParentTabsViewTest.java`, `TabDragTest.java`, `MoveTabsTest.java`
  (Task 2's), `WindowManagerTest.java`, `WorkspaceSnapshotTest.java`, `WorkspaceSavingTest.java`,
  `EnterpriseWorkstationMockTest.java`; and `newParentTab(` to `newTab(`, `parentTabBar()` to `tabBar()` in every test
  that names them

**Interfaces:**
- Consumes: Task 1's `TabDrag`; Task 2's `windowLayout()` → `ParentTabs`, `moveToNewWindow(Tab)`; Task 3's `tabMenu(Tab)`,
  `tabShortcut(int)`, `ParentTabBar.closeName()` and `newTabName()`; plan 7a's `ParentTabsView.openParentTab`,
  `selectParentTab`, `closeParentTab`, `reorderParentTab`, `parentTabOf`.
- Produces:
  - `Group(GroupId, List<TabId>, Optional<TabId>)` refuses more than one tab ("holds more than one tab");
    `Layout.open(TabId tab)` → `Layout`, the empty workspace's pane taking `tab` (else `IllegalArgumentException`
    naming "empty workspace"); `Layout.open(TabId, GroupId, int)` and `Layout.move(TabId, GroupId, int)` are gone;
    `Layout.moveToNewGroup(TabId, GroupId, Side)` refuses a tab's own pane;
  - `GroupView(GroupId id)`, `GroupView.id()`, `Tab tab()` (null in the empty workspace), `display(Tab)`, `close()`;
  - `WorkspaceView.open(Tab)` (the empty workspace's pane only), `openBeside(Tab, GroupId, Side)`,
    `moveToNewGroup(Tab, GroupId, Side)`, `rearrange(Layout)` (a lacking item joins to the right of the active pane);
    `moveTab`, `reorder`, `moved`, `setAnimations` are gone;
  - `WindowContent.newTab(Path directory)` → `TerminalContent`, New Tab (`newParentTab` is gone);
    `TabStrip tabBar()`, the window's strip; `WindowTabs windowTabs()` → `(WindowTabs) tabBar()`; `parentTabBar()` is
    gone; `animations()` and `setAnimations(AnimationSettings)` stay, the strip taking the motion; `attach(Tab, boolean,
    GroupId, Side)`;
  - `ParentTabBar` (no interface): `owner()`, `tabs()`, `selected()`, `title`, `icon`, `tooltip`, `shortcut(int)`,
    `terminal`, `running`, `menu`, `select`, `close(Tab)`, `pressed`, `dragged`, `released`, `newTab()`, `newTabName()`,
    `closeName()`, `ParentTabId id(Tab)` (the tab whose entry shows it; null for none), `boolean animates()` (the
    window's `lookAnimates()`), `strip()`, `refresh()`, `close()`; `WindowTabs(ParentTabBar)` and
    `WindowTabs(ParentTabBar, LongSupplier clock)` (motion kept, an entry kept by its tab), `ClassicTabs(ParentTabBar)`;
    `TabStrip.setAnimations(AnimationSettings)` stays;
  - `WorkspaceChrome.tabs(ParentTabBar)`, `WorkspaceChrome.showsTabStrip(int count)` (`showsStrip` and
    `showsParentTabBar` are gone);
  - `TabRegistry.Placement.NEW_TAB` (`ACTIVE_GROUP` is gone in the app; the SDK's `TabPlacement.ACTIVE_GROUP` maps to
    it until plan 8b).

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/layout/LayoutOperationsTest.java` with:

```java
package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.layout;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.3 and tabs and panes spec 4: the empty workspace taking a tab, selecting, activating and removing tabs, the
 * order of use and the maximize; a pane holds one tab (workspace plan 8a Rulings 2).
 */
class LayoutOperationsTest {
    /** Pane 1 holds tab 1, pane 2 tab 3; side by side, pane 2 active. */
    static Layout pair() {
        return layout(h(child(group(1), 0.5), child(group(2, 3), 0.5)), 2);
    }

    @Test void openFillsTheEmptyWorkspacesPane() {
        Layout empty = Layout.empty();
        Layout one = empty.open(tab(1));
        assertThat(one.root()).isEqualTo(new Group(empty.activeGroup(), List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
        assertThat(one.activeTab()).contains(tab(1));
    }

    /** A pane holds one tab, so only the empty workspace's pane takes one; a split puts a tab beside a pane. */
    @Test void openRefusesALayoutHoldingATab() {
        assertThatIllegalArgumentException().isThrownBy(() -> pair().open(tab(9))).withMessageContaining("empty workspace");
        Layout one = Layout.empty().open(tab(1));
        assertThatIllegalArgumentException().isThrownBy(() -> one.open(tab(2)));
        assertThatNullPointerException().isThrownBy(() -> Layout.empty().open(null));
    }

    @Test void selectShowsTheTabAndActivatesItsPane() {
        Layout selected = pair().select(tab(1));
        assertThat(selected.group(groupId(1))).contains(group(1));
        assertThat(selected.activeGroup()).isEqualTo(groupId(1));
        assertThat(selected.activeTab()).contains(tab(1));
        assertThatIllegalArgumentException().isThrownBy(() -> pair().select(tab(9))).withMessageContaining(tab(9).toString());
    }

    @Test void activateMovesTheGroupToTheFrontOfTheOrderOfUse() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        assertThat(three.recent()).containsExactly(groupId(1), groupId(2), groupId(3));
        Layout used = three.activate(groupId(3)).activate(groupId(2));
        assertThat(used.recent()).containsExactly(groupId(2), groupId(3), groupId(1));
        assertThat(used.activeGroup()).isEqualTo(groupId(2));
        assertThat(used.root()).isEqualTo(three.root());
        assertThatIllegalArgumentException().isThrownBy(() -> three.activate(groupId(9)));
    }

    @Test void equalityIgnoresTheOrderOfUse() {
        Layout direct = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 3);
        Layout used = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(2)).activate(groupId(3));
        assertThat(used).isEqualTo(direct).hasSameHashCodeAs(direct);
        assertThat(used.recent()).containsExactly(groupId(3), groupId(2), groupId(1));
        assertThat(direct.recent()).containsExactly(groupId(3), groupId(1), groupId(2));
    }

    @Test void anEmptiedGroupGivesItsSpaceToTheSiblingBeforeItElseAfter() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.5), child(group(3), 0.25)), 1);
        assertThat(three.remove(tab(2)).root()).as("the sibling before").isEqualTo(h(child(group(1), 0.75), child(group(3), 0.25)));
        assertThat(three.remove(tab(1)).root()).as("the first: the sibling after").isEqualTo(h(child(group(2), 0.75), child(group(3), 0.25)));
    }

    @Test void aSplitLeftWithOneChildCollapsesAndNestingFlattensAgain() {
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(h(child(group(3), 0.5), child(group(4), 0.5)), 0.5)), 0.5)), 1);
        assertThat(nested.remove(tab(2)).root()).isEqualTo(h(child(group(1), 0.5), child(group(3), 0.25), child(group(4), 0.25)));
    }

    @Test void theActiveGroupAfterARemovalIsTheMostRecentlyUsedRemainingGroup() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(3)).activate(groupId(2));
        Layout afterTwo = three.remove(tab(2));
        assertThat(afterTwo.activeGroup()).as("not the first in the tree nor the sibling before").isEqualTo(groupId(3));
        assertThat(afterTwo.recent()).containsExactly(groupId(3), groupId(1));
        assertThat(afterTwo.remove(tab(3)).activeGroup()).isEqualTo(groupId(1));
        assertThat(three.activate(groupId(1)).remove(tab(3)).activeGroup()).as("the active group stays when another goes").isEqualTo(groupId(1));
    }

    @Test void removalDownToTheEmptyWorkspaceKeepsItsGroupForTheNextTab() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout one = pair.remove(tab(1));
        assertThat(one.root()).as("the split collapses into the remaining group").isEqualTo(group(2));
        assertThat(one.activeGroup()).isEqualTo(groupId(2));
        Layout empty = one.remove(tab(2));
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.root()).isEqualTo(new Group(groupId(2), List.of(), Optional.empty()));
        assertThat(empty.activeGroup()).isEqualTo(groupId(2));
        assertThat(empty.tabs()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> empty.remove(tab(2)));
        assertThat(empty.open(tab(5)).root()).as("the next tab goes into the same pane").isEqualTo(group(2, 5));
    }

    @Test void theMaximizeFollowsTheActivePaneAndSurvivesClosedTabs() {
        Layout max = new Layout(h(child(group(1), 0.5), child(group(2), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(max.select(tab(1)).maximized()).as("another pane's tab is selected").contains(groupId(1));
        assertThat(max.activate(groupId(1)).maximized()).as("another pane is activated").contains(groupId(1));
        assertThat(max.remove(tab(2)).maximized()).as("its tab closes and it goes").isEmpty();
        assertThat(max.remove(tab(1)).maximized()).as("the last other pane goes: one pane is not maximized").isEmpty();
        Layout three = new Layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(three.remove(tab(3)).maximized()).as("another pane goes and two remain").contains(groupId(2));
        assertThat(three.remove(tab(3)).activeGroup()).isEqualTo(groupId(2));
        assertThat(three.remove(tab(2)).maximized()).as("its tab closes and two others remain").isEmpty();
        assertThat(three.remove(tab(2)).activeGroup()).as("the most recently used remaining pane").isEqualTo(groupId(1));
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceViewTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Tabs and panes spec 4 and 5: a tab's layout as panes in splits, each pane holding one item and none a strip. */
class WorkspaceViewTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window of one tab, sized and laid out. */
    private static WindowContent window() {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        owner.setSize(1208, 700);
        layout(owner);
        return owner;
    }

    /** A window whose {@code count} tabs run real in-process sessions, which a launch delivers on the next EDT turn. */
    private static WindowContent runningWindow(int count) throws Exception {
        var launcher = new ShellLauncher(Runnable::run, directory -> IdleTerminalSession.start(), "idle-test-session");
        var window = new WindowContent[1];
        edt(() -> {
            window[0] = content(launcher);
            for (int i = 1; i < count; i++) window[0].newTab(HOME);
            window[0].setSize(1208, 700);
            layout(window[0]);
        });
        edt(() -> { });
        return window[0];
    }

    private static dev.jasper.terminal.session.TerminalSession shellOf(Tab tab) {
        return ((TerminalContent) tab.content()).pane().session();
    }

    private static void layout(WindowContent owner) { LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner); }

    @Test void aWindowsOnlyTabFillsTheWorkspaceWithOnePane() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            assertThat(workspace.groupViews()).hasSize(1);
            var group = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).as("one pane fills the workspace").isSameAs(group);
            assertThat(group.getHeight()).as("laid out").isPositive();
            var tab = owner.selectedTab();
            assertThat(group.tab()).isSameAs(tab);
            assertThat(tab.component().getParent()).as("the item fills its pane").isSameAs(group);
            assertThat(tab.component().getBounds()).isEqualTo(new java.awt.Rectangle(group.getSize()));
            assertThat(tab.component().isVisible()).isTrue();
            assertThat(owner.tabRegistry().inWindow(owner.id())).as("the window's terminal tab is a registry tab").containsExactly(tab);
            assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tab.group()).contains(workspace.model().activeGroup());
        });
    }

    /** Spec 5: no pane shows a strip; each holds its own item. */
    @Test void aSplitRendersTwoPanesEachHoldingItsItem() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            Tab first = owner.selectedTab();
            var left = workspace.model().activeGroup();
            owner.split(Side.RIGHT);
            Tab second = owner.selectedTab();
            layout(owner);
            assertThat(workspace.model().root()).isInstanceOf(Split.class);
            var split = workspace.split(List.of());
            assertThat(workspace.getComponent(0)).isSameAs(split);
            assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
            var right = workspace.model().groupOf(second.id()).orElseThrow();
            assertThat(split.children()).containsExactly(workspace.groupView(left), workspace.groupView(right));
            assertThat(workspace.groupView(left).tab()).isSameAs(first);
            assertThat(workspace.groupView(right).tab()).isSameAs(second);
            assertThat(SwingUtilities.isDescendingFrom(second.component(), workspace.groupView(right))).isTrue();
            assertThat(WindowTabsTest.named(workspace, "windowTabs")).as("no pane shows a strip").isNull();
            assertThat(first.component().isVisible()).as("each pane shows its item").isTrue();
            assertThat(owner.selectedTab()).isSameAs(second);
            owner.selectTab(first);
            assertThat(workspace.model().activeGroup()).isEqualTo(left);
        });
    }

    @Test void aDividerDragWritesTheLayoutsWeights() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            owner.split(Side.RIGHT);
            layout(owner);
            var split = workspace.split(List.of());
            var divider = split.divider(0);
            int available = split.getWidth() - SplitContainer.dividerSize();
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 10, 1, false, MouseEvent.BUTTON1));
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 0, 0, 2 + available / 5, 10, 1, false, MouseEvent.BUTTON1));
            // AWT delivers the release relative to the divider where it is now, which followed the pointer: still 2 into it.
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 10, 1, false, MouseEvent.BUTTON1));
            var root = (Split) workspace.model().root();
            assertThat(root.children().get(0).weight()).isCloseTo(0.7, within(0.01));
            assertThat(workspace.split(List.of())).as("weights alone rebuild nothing").isSameAs(split);
            assertThat(split.weights().get(0)).isEqualTo(root.children().get(0).weight());
        });
    }

    @Test void aThemeChangeKeepsTheDividers() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            owner.split(Side.RIGHT);
            owner.split(Side.DOWN);
            workspace.resizeSplit(List.of(), List.of(0.63, 0.37));
            layout(owner);
            var split = workspace.split(List.of());
            var bounds = workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList();
            owner.selectVariant(false); layout(owner);
            owner.selectVariant(true); layout(owner);
            assertThat(workspace.split(List.of())).isSameAs(split);
            assertThat(workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList())
                .isEqualTo(bounds);
            assertThat(((Split) workspace.model().root()).children().get(0).weight()).isCloseTo(0.63, within(1e-9));
        });
    }

    @Test void closingAPanesItemCollapsesItAndRestoresTheSizes() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var a = workspace.model().activeGroup();
            owner.split(Side.RIGHT);
            Tab second = owner.selectedTab();
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            var b = workspace.model().groupOf(second.id()).orElseThrow();
            owner.split(Side.RIGHT);
            Tab third = owner.selectedTab();
            layout(owner);
            assertThat(workspace.split(List.of()).children()).hasSize(3);
            owner.closeTab(third);
            layout(owner);
            assertThat(workspace.model().groups()).extracting(Group::id).containsExactly(a, b);
            assertThat(workspace.split(List.of()).weights().get(0)).isCloseTo(0.7, within(1e-9));
            assertThat(workspace.split(List.of()).weights().get(1)).isCloseTo(0.3, within(1e-9));
            owner.closeTab(second);
            layout(owner);
            assertThat(workspace.getComponent(0)).as("one pane fills the workspace again").isSameAs(workspace.groupView(a));
            assertThat(workspace.split(List.of())).isNull();
        });
    }

    @Test void maximizeShowsTheActivePaneAloneAndKeepsTheOthersItems() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            Tab first = owner.selectedTab();
            owner.split(Side.DOWN);
            var hidden = workspace.groupView(workspace.model().groupOf(first.id()).orElseThrow());
            workspace.toggleMaximize();
            layout(owner);
            var shown = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).isSameAs(shown);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).as("the other pane is out of the tree").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(first.component(), hidden)).as("and keeps its item").isTrue();
            assertThat(owner.tabCount()).isEqualTo(2);
            workspace.toggleMaximize();
            layout(owner);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).isTrue();
            assertThat(workspace.split(List.of()).orientation()).isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void aTerminalTabWhoseCloseListenerThrowsStillClosesItsShellAndLeavesTheWorkspace() throws Exception {
        var owner = runningWindow(2);
        var shells = new dev.jasper.terminal.session.TerminalSession[1];
        edt(() -> {
            var tab = owner.tabs().get(1);
            shells[0] = shellOf(tab);
            assertThat(shells[0]).as("its shell started").isNotNull();
            assertThat(shells[0].exitFuture().isDone()).isFalse();
            tab.onClosed(() -> { throw new IllegalStateException("listener failed"); });
            assertThatThrownBy(() -> owner.closeTab(tab)).isInstanceOf(IllegalStateException.class).hasMessage("listener failed");
            assertThat(owner.tabs()).doesNotContain(tab);
            assertThat(owner.tabRegistry().tabs()).doesNotContain(tab);
            assertThat(owner.workspace().activeGroupView().tab()).as("the tab left shows").isSameAs(owner.tabs().get(0));
            assertThat(owner.windowTabs().tabList().getComponentCount()).as("the strip lists the tab left").isEqualTo(1);
        });
        until(() -> shells[0].exitFuture().isDone());
    }

    @Test void closingAWindowWhoseTabListenerThrowsStillClosesTheOtherTabsAndForgetsTheWindow() throws Exception {
        var owner = runningWindow(3);
        var shells = new java.util.ArrayList<dev.jasper.terminal.session.TerminalSession>();
        edt(() -> {
            for (Tab tab : owner.tabs()) shells.add(shellOf(tab));
            assertThat(shells).as("every shell started").doesNotContainNull().hasSize(3);
            owner.tabs().get(0).onClosed(() -> { throw new IllegalStateException("listener failed"); });
            assertThatThrownBy(owner::close).isInstanceOf(IllegalStateException.class).hasMessage("listener failed");
            assertThat(owner.tabRegistry().tabs()).isEmpty();
            assertThatCode(() -> owner.tabRegistry().addHost(new WorkspaceHost(owner.id(), tab -> { }, tab -> { }, tab -> { }, () -> { })))
                .as("the window's host is unregistered").doesNotThrowAnyException();
            assertThat(owner.windowTabs().isVisible()).as("the rest of the close ran: the strip is released").isFalse();
        });
        until(() -> shells.stream().allMatch(shell -> shell.exitFuture().isDone()));
    }

    @Test void theStripReordersTabsAndKeepsTheShownOne() throws Exception {
        edt(() -> {
            var owner = window();
            owner.newTab(HOME);
            owner.newTab(HOME);
            var tabs = owner.tabs();
            owner.selectTab(tabs.get(0));
            owner.reorderParentTab(owner.parentTabOf(tabs.get(2)).orElseThrow(), 0);
            assertThat(owner.tabs()).containsExactly(tabs.get(2), tabs.get(0), tabs.get(1));
            assertThat(owner.selectedTab()).as("reordering keeps the one that shows").isSameAs(tabs.get(0));
            assertThat(owner.parentTabs().bar().tabs()).isEqualTo(owner.tabs());
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabBarTest.java` with:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 5: a window's one tab strip, the look's strip over its tabs, each shown as its active pane's item;
 * no pane shows a strip. The code calls the strip the parent-tab bar (workspace plan 8a Rulings 1).
 */
class ParentTabBarTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}, its terminal renamed {@code title}: the new tab's item. */
    private static Tab open(WindowContent owner, String title) {
        owner.newTab(HOME);
        owner.currentTerminal().rename(title);
        owner.update();
        return owner.selectedTab();
    }

    private static AbstractButton button(WindowTabs strip, String name) { return (AbstractButton) WindowTabsTest.named(strip, name); }

    /** The names of the entries {@code strip} lists, in order. */
    private static List<String> names(ClassicTabs strip) {
        return strip.entries().stream().map(entry -> entry.getAccessibleContext().getAccessibleName()).toList();
    }

    @Test void theStandardLookShowsTheStripFromTwoTabsAndNoPaneShowsOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            var strip = owner.windowTabs();
            assertThat(strip).isSameAs(owner.tabBar().component());
            assertThat(strip.isVisible()).as("one tab: no strip").isFalse();
            open(owner, "second");
            assertThat(strip.isVisible()).isTrue();
            assertThat(WindowTabsTest.named(strip, "select:first")).isNotNull();
            assertThat(WindowTabsTest.named(strip, "select:second")).isNotNull();
            owner.split(Side.RIGHT);
            assertThat(owner.workspace().groupViews()).hasSize(2);
            assertThat(WindowTabsTest.named(owner.workspace(), "windowTabs")).as("no pane shows a strip").isNull();
            assertThat(WindowTabsTest.named(owner, "windowTabs")).as("the window's one strip").isSameAs(strip);
            owner.closeParentTab(owner.activeParentTab());
            assertThat(strip.isVisible()).as("one tab again: the strip hides at once").isFalse();
        });
    }

    /**
     * Spec 5 and workspace plan 8a Rulings 20: tabs open and close with the strip's motion where the look has it, and a
     * tab showing another of its panes keeps its entry and its place.
     */
    @Test void tabsOpenAndCloseWithTheStripsMotion() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            assertThat(owner.animations().enabled() && owner.lookAnimates()).as("the window's tabs animate").isTrue();
            owner.currentTerminal().rename("first");
            owner.update();
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            var strip = owner.windowTabs();
            open(owner, "second");
            LayoutTestSupport.layoutTree(owner);
            assertThat(strip.motionRunning()).as("the strip shows with its two tabs in place").isFalse();
            open(owner, "third");
            assertThat(strip.motionRunning()).as("a third tab opens with the motion").isTrue();
            owner.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(strip.motionRunning()).as("animations off settle it").isFalse();
            owner.setAnimations(AnimationSettings.defaults());
            var entry = WindowTabsTest.named(strip, "select:third").getParent();
            owner.split(Side.RIGHT);
            owner.currentTerminal().rename("beside");
            owner.update();
            assertThat(strip.motionRunning()).as("a split: the tab's entry shows its new pane").isFalse();
            assertThat(WindowTabsTest.named(strip, "select:beside").getParent()).as("in the same entry").isSameAs(entry);
            owner.focusGroup(Side.LEFT);
            assertThat(WindowTabsTest.named(strip, "select:third").getParent()).as("another pane focused").isSameAs(entry);
            owner.closeTab(owner.selectedTab());
            assertThat(owner.parentTabCount()).as("a pane of a split tab closed").isEqualTo(3);
            assertThat(WindowTabsTest.named(strip, "select:beside").getParent()).isSameAs(entry);
            assertThat(strip.motionRunning()).isFalse();
            button(strip, "close:second").doClick();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(strip.motionRunning()).as("a tab closes with the motion").isTrue();
        });
    }

    /** Spec 5: an entry shows its tab's active pane's item, and its tip carries Select Tab's shortcut. */
    @Test void eachTabShowsAsItsActivePanesItem() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("left");
            Tab left = owner.selectedTab();
            owner.split(Side.RIGHT);
            owner.currentTerminal().rename("right");
            Tab right = owner.selectedTab();
            Tab other = open(owner, "other");
            var strip = owner.windowTabs();
            var bar = owner.parentTabs().bar();
            assertThat(bar.tabs()).as("each tab's active item, in strip order").containsExactly(right, other);
            assertThat(bar.selected()).isSameAs(other);
            assertThat(WindowTabsTest.named(strip, "select:right")).isNotNull();
            String shortcut = owner.shortcutText(ActionId.SELECT_TAB_2);
            assertThat(shortcut).isNotEmpty();
            assertThat(button(strip, "select:other").getToolTipText()).as("an entry's tip carries Select Tab's shortcut")
                .isEqualTo("other (" + shortcut + ")");
            owner.selectTab(left);
            assertThat(bar.tabs()).containsExactly(left, other);
            assertThat(bar.selected()).isSameAs(left);
            assertThat(WindowTabsTest.named(strip, "select:left")).as("the entry follows its tab's active pane").isNotNull();
            assertThat(WindowTabsTest.named(strip, "select:right")).isNull();
            assertThat(bar.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_TAB_1)).isNotEmpty();
            assertThat(bar.shortcut(9)).isEmpty();
        });
    }

    @Test void aClickShowsATabAndItsCloseAndItsMenuCloseOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            ParentTabId first = owner.activeParentTab();
            Tab second = open(owner, "second");
            Tab third = open(owner, "third");
            var strip = owner.windowTabs();
            var close = button(strip, "close:second");
            assertThat(close.getToolTipText()).as("the strip's × closes a tab").isEqualTo("Close Tab");
            assertThat(close.getAccessibleContext().getAccessibleName()).isEqualTo("Close Tab");
            button(strip, "select:first").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            button(strip, "close:second").doClick();
            assertThat(second.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            var menu = owner.parentTabs().bar().menu(third);
            // Workspace plan 8a Rulings 8: Rename Tab and Move Tab to New Window, then a rule, before Close Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Rename Tab", "Move Tab to New Window", "Close Tab");
            ((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick();
            assertThat(third.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    /**
     * Review Focus 2: the strip's × and a middle click close every pane of a tab after its items' guards, asked once; a
     * refusal closes nothing, terminals included.
     */
    @Test void theStripsCloseAndAMiddleClickCloseAWholeTabAfterItsGuards() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            var model = new Contributions();
            owner.connectContributions(model);
            // The first tab: its terminal, one in a split and a plugin's view below that, whose guard decides.
            Tab first = owner.selectedTab();
            owner.split(Side.RIGHT);
            Tab split = owner.selectedTab();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            owner.selectTab(split);
            owner.currentTerminal().rename("guarded");
            owner.update();
            // The second: its terminal and one in a split; the third holds one terminal.
            Tab second = open(owner, "second");
            owner.split(Side.RIGHT);
            Tab secondSplit = owner.selectedTab();
            owner.currentTerminal().rename("doomed");
            owner.update();
            Tab third = open(owner, "third");
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            var strip = owner.windowTabs();
            var guarded = List.of(first, split, view);
            button(strip, "close:guarded").doClick();
            assertThat(asked).as("the plugin's guard is asked once").containsExactly(List.of(view));
            assertThat(guarded).as("a refusal closes nothing, terminals included").noneMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            allow[0] = true;
            button(strip, "close:guarded").doClick();
            assertThat(guarded).as("the × closes every pane of its tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).as("and the tab goes").isEqualTo(2);
            var doomed = button(strip, "select:doomed");
            doomed.dispatchEvent(new MouseEvent(doomed, MouseEvent.MOUSE_PRESSED, 1, 0, 5, 5, 1, false, MouseEvent.BUTTON2));
            assertThat(List.of(second, secondSplit)).as("a middle click closes every pane of its tab").allMatch(Tab::closed);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.tabs()).as("the rest of the window").containsExactly(third);
        });
    }

    /** The strip takes a new theme with the window's workspace. */
    @Test void theStripTakesANewTheme() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.currentTerminal().rename("first");
            owner.update();
            open(owner, "second");
            // A workspace of the other darkness than the installed theme's, so its colours are not the defaults.
            boolean dark = FlatLafInstaller.installedTheme().dark();
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(dark), ThemeTestSupport.generated(!dark));
            Color ink = split.workspace().semantic().color(SemanticColor.TEXT_PRIMARY);
            assertThat(ink).isNotEqualTo(FlatLafInstaller.installedTheme().semantic().color(SemanticColor.TEXT_PRIMARY));
            owner.applyTheme(split, true);
            var strip = owner.windowTabs();
            assertThat(strip.getClientProperty("Jasper.workspaceTheme")).isSameAs(split.workspace());
            assertThat(button(strip, "select:second").getForeground()).as("the selected entry").isEqualTo(ink);
        });
    }

    @Test void theStripSitsAboveTheShownWorkspace() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            open(owner, "second");
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            var parents = owner.parentTabs();
            var strip = owner.tabBar().component();
            assertThat(strip.getBounds()).isEqualTo(new Rectangle(0, 0, parents.getWidth(), owner.tabHeight()));
            assertThat(owner.workspace().getBounds()).as("the shown workspace under it")
                .isEqualTo(new Rectangle(0, owner.tabHeight(), parents.getWidth(), parents.getHeight() - owner.tabHeight()));
            assertThat(parents.getComponent(parents.getComponentCount() - 1)).as("the strip is the last child").isSameAs(strip);
            open(owner, "third");
            var children = new ArrayList<Component>(parents.views());
            children.add(strip);
            assertThat(parents.getComponents()).as("three tabs: the views in strip order, then the strip").containsExactlyElementsOf(children);
        });
    }

    /** Tabs and panes spec 5 (the user's decision of 2026-10-20): Enterprise always shows the strip, with one tab too. */
    @Test void enterpriseAlwaysShowsTheStripWithOneTabToo() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                owner.currentTerminal().rename("first");
                owner.update();
                var strip = (ClassicTabs) owner.tabBar();
                assertThat(strip.isVisible()).as("one tab: the strip shows").isTrue();
                assertThat(names(strip)).containsExactly("first");
                ParentTabId first = owner.activeParentTab();
                owner.split(Side.RIGHT);
                assertThat(names(strip)).as("a split adds no entry").hasSize(1);
                assertThat(WindowTabsTest.named(owner.workspace(), "classicTabs")).as("and no pane shows a strip").isNull();
                owner.invoke(ActionId.NEW_TAB);
                owner.currentTerminal().rename("second");
                owner.update();
                assertThat(owner.parentTabCount()).isEqualTo(2);
                assertThat(names(strip)).as("one row, every tab").hasSize(2).endsWith("second");
                owner.invoke(ActionId.SELECT_TAB_1);
                assertThat(owner.activeParentTab()).isEqualTo(first);
                assertThat(strip.isVisible()).isTrue();
                assertThat(strip.add().getToolTipText()).as("the + opens a tab").isEqualTo("New Tab");
                assertThat(owner.parentTabs().bar().closeName()).isEqualTo("Close Tab");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 8a Rulings 10: Enterprise's empty main workspace shows the strip with its "+" alone, which fills it. */
    @Test void enterprisesEmptyMainWorkspaceShowsTheStripWithItsPlusAlone() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var main = track(new WindowContent(WindowRole.MAIN, idleLauncher(pending), null, true, path -> { }, () -> { },
                    () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                var strip = (ClassicTabs) main.tabBar();
                assertThat(strip.isVisible()).as("over the empty workspace").isTrue();
                assertThat(strip.entries()).as("no tab: the + alone").isEmpty();
                ((AbstractButton) strip.add()).doClick();
                assertThat(main.parentTabCount()).as("the + fills the empty workspace").isEqualTo(1);
                assertThat(strip.entries()).hasSize(1);
                assertThat(strip.isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabMotionPreview.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/**
 * Actual headless Swing frames of a window's tab strip at deterministic transition times; no native window or shell
 * launch. The window's own strip runs on the system clock, so a strip over the same tabs on the preview's clock stands
 * in its place in each frame.
 */
public final class WindowTabMotionPreview {
    private WindowTabMotionPreview() {}
    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments.length == 0 ? "build/render-preview/tab-motion" : arguments[0]);
        Files.createDirectories(output);
        try {
            edt(() -> {
                for (String appearance : new String[]{"dark", "light", "glass"}) {
                    var owner = content(launcher(new ArrayDeque<>()));
                    owner.selectVariant(!appearance.equals("light"));
                    if (appearance.equals("glass")) owner.applyBackgroundOpacity(.55);
                    var model = new Contributions(); owner.connectContributions(model);
                    owner.closeTab(owner.currentTerminal());
                    var first = model.tabs().open("preview", "first", "tmux", owner.id(), terminalText());
                    first.select();
                    var root = new JRootPane();
                    WindowContent.installTitleBar(root, owner, true, title -> {});
                    owner.installRootBindings(root);
                    root.setSize(800, 420); LayoutTestSupport.layoutTree(root);
                    var clock = new AtomicLong();
                    var strip = new WindowTabs(owner.parentTabs().bar(), clock::get);
                    WorkspaceTheme.apply(strip, owner.theme());
                    strip.setSize(owner.parentTabs().getWidth(), strip.getPreferredSize().height);
                    strip.refresh();
                    render(root, owner, strip, output, appearance + "-single");
                    var second = model.tabs().open("preview", "second", "amp", owner.id(), terminalText());
                    strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-reveal");
                    var third = model.tabs().open("preview", "third", "pve-root", owner.id(), terminalText());
                    strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-open");
                    third.requestClose(); strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-close");
                    first.select(); strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-switch");
                    second.requestClose(); strip.refresh(); render(root, owner, strip, output, appearance + "-single-again");
                    strip.close();
                }
            });
        } finally { closeOwners(); }
        System.out.println(output.toAbsolutePath());
    }
    private static JComponent terminalText() {
        var text = new JTextArea("~/projects/jasper on codex/alacritty-tabs\n\n$ ./gradlew check\n\nBUILD SUCCESSFUL\n\nTerminal workspace ready.\n\n$ ");
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14)); text.setEditable(false);
        return text;
    }
    private static void frames(JRootPane root, WindowContent owner, WindowTabs strip, AtomicLong clock, Path output, String prefix) {
        long start = clock.get();
        for (int frame = 0; frame <= 60; frame++) {
            clock.set(start + frame * 1_000_000_000L / 60); strip.animate(clock.get());
            render(root, owner, strip, output, prefix + String.format("-%03d", frame));
        }
    }
    /** The window with the preview's strip painted where the window's own strip shows; the window alone while it hides. */
    private static void render(JRootPane root, WindowContent owner, WindowTabs strip, Path output, String name) {
        LayoutTestSupport.layoutTree(root);
        WindowTabs own = owner.windowTabs();
        Rectangle at = own.isVisible() && own.getHeight() > 0 ? SwingUtilities.convertRectangle(own.getParent(), own.getBounds(), root) : null;
        var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(2, 2);
            if (at != null) own.setVisible(false);
            root.printAll(g);
            if (at != null) {
                own.setVisible(true);
                var inStrip = (Graphics2D) g.create(at.x, at.y, at.width, at.height);
                try { strip.printAll(inStrip); } finally { inStrip.dispose(); }
            }
        } finally { g.dispose(); }
        try {
            ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
            if (at != null) ImageIO.write(image.getSubimage(at.x * 2, at.y * 2, at.width * 2, at.height * 2),
                "png", output.resolve(name + "-strip.png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
```

Run the edit script for the other tests. It turns the motion test to the window's strip, gives the layout and codec
tests one-tab panes, and turns the window tests from stacked items to tabs and splits; the last loop renames
`newParentTab(` to `newTab(` and `parentTabBar()` to `tabBar()` where tests still name them. The codec tests' Java holds
backslashes, so those anchors are raw strings (as in Task 2).

```bash
python3 -I - <<'PY'
import pathlib, sys; sys.path.insert(0, "build/plan8a")
from edits import edit, span, regex
import ledger
T = "jasper-app/src/test/java/dev/jasper/app/"
W = T + "workspace/"
L = T + "layout/"
P = T + "persistence/"
MT = W + "WindowTabMotionTest.java"
edit(MT, [("new WindowTabs(owner.workspace().activeGroupView(), clock::get)", "new WindowTabs(owner.parentTabs().bar(), clock::get)", 2)])
span(MT, "    /** Spec 4: a tab that joins or leaves a group by a move, or is reordered, takes its place at once (Rulings 27). */\n",
     "            assertThat(f.strip.motionRunning()).as(\"an open still moves\").isTrue();\n        });\n    }\n",
     """    /**
     * Tabs and panes spec 5 and workspace plan 8a Rulings 20: a reorder takes its place at once, and a tab's entry
     * keeps its place while the tab splits, shows another pane or closes one.
     */
    @Test void reordersAndPanesTakeTheirPlaceAtOnce() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            f.owner.reorderParentTab(f.owner.activeParentTab(), 0); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a reorder").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.split(dev.jasper.app.layout.Side.RIGHT); f.owner.currentTerminal().rename("beside"); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a split shows its new pane in the tab's entry").isFalse();
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.focusGroup(dev.jasper.app.layout.Side.LEFT); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("another pane focused").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.owner.closeTab(f.owner.currentTerminal()); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a pane of a split tab closed").isFalse();
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.add("fourth");
            assertThat(f.strip.motionRunning()).as("an open still moves").isTrue();
        });
    }

    /** Workspace plan 8a Rulings 20: a tab under a new id keeps its entry, settled as it shows another pane. */
    @Test void aTabUnderANewIdKeepsItsEntrySettled() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            var entry = f.entry("third");
            f.owner.parentTabs().reid(f.owner.activeParentTab(), dev.jasper.app.layout.ParentTabId.random()); f.strip.refresh();
            f.owner.split(dev.jasper.app.layout.Side.RIGHT); f.owner.currentTerminal().rename("beside"); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("the new pane shows in the tab's entry, settled").isFalse();
            assertThat(f.entry("beside")).as("the same entry").isSameAs(entry);
            assertThat(f.entry("beside").getBounds()).isEqualTo(new Rectangle(533, 0, 267, 30));
        });
    }
""")
span(MT, "    /** Workspace plan 7a: a strip in a hidden parent tab takes a closed tab at once; once it shows, a close moves. */\n",
     "            assertThat(strip.motionRunning()).as(\"once it shows, a close moves\").isTrue();\n        });\n    }\n",
     """    /** Tabs and panes spec 5: closing down to one tab hides the strip at once, its motion settled. */
    @Test void theStripHidesSettledWithOneTabLeft() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME); owner.newTab(HOME);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            var strip = owner.windowTabs();
            owner.closeTab(owner.currentTerminal());
            assertThat(strip.motionRunning()).as("two tabs left: the close moves").isTrue();
            owner.closeTab(owner.currentTerminal());
            assertThat(strip.isVisible()).as("one tab: no strip").isFalse();
            assertThat(strip.motionRunning()).as("it hides settled").isFalse();
        });
    }
""")
edit(L + "LayoutFixtures.java", [
    ("import java.util.Arrays;\n", "", 1),
    ("""    /** Group {@code id} holding the one tab of the same number. */
    static Group group(int id) {
        return group(id, id, id);
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(LayoutFixtures::tab).toList(), Optional.of(tab(selected)));
    }
""", """    /** Pane {@code id} holding the one tab of the same number. */
    static Group group(int id) {
        return group(id, id);
    }

    /** Pane {@code id} holding tab {@code n}, its one tab (workspace plan 8a Rulings 2). */
    static Group group(int id, int n) {
        return new Group(groupId(id), List.of(tab(n)), Optional.of(tab(n)));
    }
""", 1)])
span(L + "LayoutGeometryTest.java", "    @Test void moveToNewGroupMovesAnExistingTabBesideAGroup() {\n",
     "        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(1), groupId(9), Side.RIGHT));\n    }\n",
     """    /** Plan 8c's pane moves: a tab's pane goes beside another pane, its own pane's space going first. */
    @Test void moveToNewGroupMovesATabsPaneBesideAnotherPane() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2, 3), 0.5)), 1);
        Layout below = pair.moveToNewGroup(tab(1), groupId(2), Side.DOWN);
        assertThat(below.root()).as("its own pane goes; the other splits").isEqualTo(v(child(group(2, 3), 0.5), child(fresh(below, 1), 0.5)));
        assertThat(below.activeGroup()).isEqualTo(below.groupOf(tab(1)).orElseThrow());
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        Layout left = three.moveToNewGroup(tab(3), groupId(1), Side.LEFT);
        assertThat(left.root()).as("its emptied pane's space goes first")
            .isEqualTo(h(child(fresh(left, 3), 0.125), child(group(1), 0.125), child(group(2), 0.75)));
        assertThatIllegalArgumentException().as("beside its own pane").isThrownBy(() -> pair.moveToNewGroup(tab(3), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(9), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(1), groupId(9), Side.RIGHT));
    }
""")
edit(L + "LayoutGeometryTest.java", [(
    "        Layout max = layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 1).toggleMaximize(groupId(2));\n",
    "        Layout max = layout(h(child(group(1), 0.5), child(v(child(group(2, 3), 0.5), child(group(4, 2), 0.5)), 0.5)), 1).toggleMaximize(groupId(2));\n", 1)])
edit(L + "LayoutTest.java", [
    ("child(group(2, 1, 1), 0.5)", "child(group(2, 1), 0.5)", 1),
    ("        Group a = group(1, 2, 1, 2), b = group(2, 3, 3), c = group(3, 4, 4);\n",
     "        Group a = group(1, 2), b = group(2, 3), c = group(3, 4);\n", 1),
    ("        assertThat(grid.tabs()).containsExactly(tab(1), tab(2), tab(3), tab(4));\n",
     "        assertThat(grid.tabs()).containsExactly(tab(2), tab(3), tab(4));\n", 1)])
span(L + "LayoutValuesTest.java", "    @Test void aGroupSelectsOneOfItsTabsAndAnEmptyGroupSelectsNothing() {\n",
     "        assertThat(copied.tabs()).containsExactly(tab(1));\n    }\n",
     """    /** Workspace plan 8a Rulings 2: a pane holds one tab and shows it, and the empty workspace's pane holds none. */
    @Test void aPaneHoldsOneTabAndShowsItAndAnEmptyPaneShowsNothing() {
        assertThat(group(1, 2).selected()).contains(tab(2));
        assertThat(new Group(groupId(1), List.of(), Optional.empty()).tabs()).isEmpty();
        assertThatIllegalArgumentException().as("two tabs")
            .isThrownBy(() -> new Group(groupId(1), List.of(tab(1), tab(2)), Optional.of(tab(1)))).withMessageContaining("more than one tab");
        assertThatIllegalArgumentException().as("a tab it does not hold")
            .isThrownBy(() -> new Group(groupId(1), List.of(tab(1)), Optional.of(tab(3))));
        assertThatIllegalArgumentException().as("an empty pane selects nothing")
            .isThrownBy(() -> new Group(groupId(1), List.of(), Optional.of(tab(1))));
        assertThatIllegalArgumentException().as("a pane with a tab shows it")
            .isThrownBy(() -> new Group(groupId(1), List.of(tab(1)), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new Group(null, List.of(), Optional.empty()));
        var tabs = new ArrayList<>(List.of(tab(1)));
        Group copied = new Group(groupId(1), tabs, Optional.of(tab(1)));
        tabs.add(tab(2));
        assertThat(copied.tabs()).containsExactly(tab(1));
    }
""")
edit(L + "ParentTabsTest.java", [(
    """    /** A parent tab of its own id holding {@code tabs} in one group, the first selected. */
    private static ParentTab parent(TabId... tabs) {
        Layout layout = Layout.of(tabs[0]);
        for (int i = 1; i < tabs.length; i++) layout = layout.open(tabs[i], layout.activeGroup(), i);
""", """    /** A parent tab of its own id holding {@code tabs} in panes side by side, the first selected. */
    private static ParentTab parent(TabId... tabs) {
        Layout layout = Layout.of(tabs[0]);
        for (int i = 1; i < tabs.length; i++) layout = layout.split(tabs[i], layout.activeGroup(), Side.RIGHT);
""", 1)])
edit(P + "WorkspaceFileTest.java", [
    ("""    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(WorkspaceFileTest::tab).toList(), Optional.of(tab(selected)));
    }
""", """    /** Pane {@code id} holding tab {@code n}, its one tab (workspace plan 8a Rulings 2). */
    static Group group(int id, int n) {
        return new Group(groupId(id), List.of(tab(n)), Optional.of(tab(n)));
    }
""", 1),
    ("""     * Spec 8's example as the main window's one parent tab: two columns, the right one in two rows, its lower right group
     * maximized; and a full window without bounds. Listed full window first: the snapshot puts the main window first.
     */
    static Snapshot snapshot() {
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5),
""", """     * Spec 8's example as the main window's one parent tab: two columns, each in two rows, the right column's upper pane
     * active and maximized; and a full window without bounds. Listed full window first: the snapshot puts the main
     * window first.
     */
    static Snapshot snapshot() {
        Node left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1, 1), 0.5), new Child(group(5, 2, 2), 0.5)));
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5),
""", 1),
    ("""            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"
""", """            [[windows.parent_tabs.nodes]]
            id = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001"]
            selected = "00000000-0000-4000-8000-000000000001"

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000005"
            kind = "group"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"
""", 1)])
regex(P + "WorkspaceFileTest.java", r"group\((\w+), (\w+), \2\)", r"group(\1, \2)", 6)
R = P + "WorkspaceFileReadTest.java"
edit(R, [
    (r'''        Split root = (Split) layout.root();
        var firstTab = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1, 1, 2), 0.5), root.children().get(1)));
        assertThat(read(replaceOnce(text, "selected = \"" + id(tab(2)) + "\"", "selected = \"" + id(tab(9)) + "\""))).as("the first tab")
            .isEqualTo(clean(withMain(main.bounds(), 6, new Layout(firstTab, groupId(2), Optional.of(groupId(2))), main.tabs())));
''', r'''        assertThat(read(replaceOnce(text, "selected = \"" + id(tab(2)) + "\"", "selected = \"" + id(tab(9)) + "\""))).as("the pane's one tab")
            .isEqualTo(clean(snapshot()));
''', 1),
    ("""        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(3, 4, 4), 0.5))));
""", """        Split left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5), new Child(group(3, 4), 0.5))));
""", 1),
    ("""        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(2, 3, 3), 0.5))));
""", """        Split left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5), new Child(group(2, 3), 0.5))));
""", 1),
    ("new Child(group(1, 2, 1, 2), 0.3)", "new Child(group(1, 2), 0.3)", 1),
    ("new Child(group(8, 9, 8, 9), 0.75)", "new Child(group(8, 9), 0.75)", 1),
    ("    @Test void aWindowHoldingAParentTabAnEarlierWindowHoldsIsDropped() throws Exception {\n",
     r'''    /**
     * Review Focus 4 (workspace plan 8a Rulings 2): a pane holds one tab, so a parent tab whose pane lists two is a bad
     * entry, dropped alone, and the original is kept aside.
     */
    @Test void aPaneListingTwoTabsDropsItsParentTabAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t2 = id(tab(2));
        String twoTabs = replaceOnce(text, "tabs = [\"" + t2 + "\"]", "tabs = [\"" + t2 + "\", \"" + id(tab(9)) + "\"]");
        assertThat(read(twoTabs)).as("the main window keeps its first parent tab, which shows")
            .isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(1), single(1))))));
        assertThat(Files.readString(bad())).isEqualTo(twoTabs);
    }

    @Test void aWindowHoldingAParentTabAnEarlierWindowHoldsIsDropped() throws Exception {
''', 1)])
regex(R, r"group\((\w+), (\w+), \2\)", r"group(\1, \2)", 10)
edit(T + "application/JasperApplicationWindowsTest.java", [(
    """    /** Parent tabs spec 11: a request opens in the main window's shown parent tab, and no parent tab opens for it. */
    @Test void aRequestOpensInTheMainWindowsShownParentTab() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                var main = application.windows().main().orElseThrow().content();
                main.newParentTab(HOME);
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                var parents = (ParentTabs) main.windowLayout();
                assertThat(parents.parentTabs()).hasSize(2);
                assertThat(parents.activeParentTab().layout().tabs()).as("the folder's tab joins the shown parent tab").hasSize(2);
                assertThat(parents.parentTabs().getFirst().layout().tabs()).hasSize(1);
""", """    /** Tabs and panes spec 13: a request's folder opens as a new tab of the main window, at the end of its strip, shown. */
    @Test void aRequestOpensANewTabInTheMainWindow() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                var main = application.windows().main().orElseThrow().content();
                main.newTab(HOME);
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                ParentTabs parents = main.windowLayout();
                assertThat(parents.parentTabs()).hasSize(3);
                assertThat(parents.active()).as("the last, shown").isEqualTo(parents.parentTabs().getLast().id());
                assertThat(parents.parentTabs()).as("each holding one terminal").allSatisfy(parent -> assertThat(parent.layout().tabs()).hasSize(1));
""", 1)])
span(W + "ClassicTabsTest.java", "    /** Parent tabs spec 7: the classic tabs show every pane's strip, a pane of one tab's too. */\n",
     "            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }\n        });\n    }\n",
     """    /** Tabs and panes spec 5: no pane shows a strip, and the window's classic strip lists a split tab once. */
    @Test void aSplitTabIsOneEntryOfTheWindowsStrip() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.split(Side.RIGHT);
                assertThat(owner.workspace().groupViews()).hasSize(2);
                var strip = (ClassicTabs) owner.tabBar();
                assertThat(strip.isVisible()).isTrue();
                assertThat(strip.entries()).as("one tab, two panes: one entry").hasSize(1);
                assertThat(WindowTabsTest.named(owner.workspace(), "classicTabs")).as("no pane shows a strip").isNull();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
""")
edit(W + "WindowTabsTest.java", [
    ("            owner.workspace().reorder(owner.tabs().get(1), owner.workspace().model().activeGroup(), 0);\n",
     "            owner.reorderParentTab(owner.parentTabOf(owner.tabs().get(1)).orElseThrow(), 0);\n", 1)])
edit(W + "HostedContentTest.java", [(
    "owner.workspace().reorder(owner.tabs().get(1), owner.workspace().model().activeGroup(), 0);",
    "owner.reorderParentTab(owner.parentTabOf(owner.tabs().get(1)).orElseThrow(), 0);", 1)])
edit(W + "TerminalContentTest.java", [(
    "            owners[1].workspace().reorder(owners[1].tabs().get(0), owners[1].workspace().model().activeGroup(), 1);\n",
    "            owners[1].reorderParentTab(owners[1].parentTabOf(owners[1].tabs().get(0)).orElseThrow(), 1);\n", 1)])
edit(W + "ConfigurationControllerTest.java", [
    ("            assertThat(owner.workspace().model().maximized()).isPresent();\n",
     "            assertThat(owner.parentTabs().views().getFirst().model().maximized()).as(\"the first tab's maximize\").isPresent();\n", 1),
    ("            assertThat(first.workspace().model().maximized()).isPresent();\n",
     "            assertThat(first.parentTabs().views().getFirst().model().maximized()).as(\"the first tab's maximize\").isPresent();\n", 1)])
edit(W + "ThemeControllerTest.java", [(
    "            assertThat(owners[0].workspace().model().maximized()).isPresent();\n",
    "            assertThat(owners[0].parentTabs().views().getFirst().model().maximized()).as(\"the first tab keeps its maximize\").isPresent();\n", 1)])
span(W + "TabMoveTest.java", "    @Test void aMoveEndsTheMaximizeOnBothSidesAndClosingATabKeepsIt() throws Exception {\n",
     "            assertThat(target.selectedTab()).isSameAs(moving.get(1));\n        });\n    }\n",
     """    /** Spec 15: an item moving out ends its tab's maximize; closing another pane keeps it while the maximized one stands. */
    @Test void aMoveOutEndsTheMaximizeAndClosingAPaneKeepsIt() throws Exception {
        edt(() -> { source.split(Side.RIGHT); source.split(Side.RIGHT); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var from = source.workspace();
            var panes = from.tabs();
            source.selectTab(panes.get(0));
            from.toggleMaximize();
            var maximized = from.model().maximized();
            assertThat(maximized).contains(from.model().groupOf(panes.get(0).id()).orElseThrow());
            source.closeTab(panes.get(2));
            assertThat(from.model().maximized()).as("closing another pane keeps the maximize").isEqualTo(maximized);
            model.tabs().move(panes.get(1), target.id());
            assertThat(from.model().maximized()).as("a pane leaving by a move ends it").isEmpty();
            assertThat(target.selectedTab()).as("in a new tab of the target").isSameAs(panes.get(1));
            assertThat(target.workspace().model().maximized()).isEmpty();
        });
    }
""")
span(W + "TabMoveTest.java", "\n    @Test void aTabThatArrivesByAMoveIsShownWithoutTheOpenMotion() throws Exception {\n",
     "            assertThat(source.workspace().moved(tab)).isFalse();\n        });\n    }\n", """
    /** Tabs and panes spec 5 and workspace plan 8a Rulings 20: a tab that arrives by a move takes its place at once. */
    @Test void aTabThatArrivesByAMoveTakesItsPlaceInTheStripAtOnce() throws Exception {
        edt(() -> {
            target.newTab(HOME);
            target.setSize(1000, 700);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(target);
            var strip = target.windowTabs();
            assertThat(strip.isVisible()).as("two tabs").isTrue();
            assertThat(strip.motionRunning()).isFalse();
            model.tabs().move(source.selectedTab(), target.id());
            assertThat(target.parentTabCount()).isEqualTo(3);
            assertThat(strip.motionRunning()).as("a tab that arrives by a move").isFalse();
            target.newTab(HOME);
            assertThat(strip.motionRunning()).as("a new tab opens with the motion").isTrue();
        });
    }
""")
WT = W + "WorkspaceTabsTest.java"
edit(WT, [
    ("""    /** A window of {@code count} running terminal tabs in one group. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); for (int i = 1; i < count; i++) owner[0].newTab(HOME); });
""", """    /** A window of one tab holding {@code count} running terminals in panes side by side, the last one active. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); for (int i = 1; i < count; i++) owner[0].split(Side.RIGHT); });
""", 1),
    ("            workspace.moveToNewGroup(window.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);\n", "", 2),
    ("""        var window = window(2);
        edt(() -> {
            window.updateActions();
            assertThat(window.action(ActionId.MAXIMIZE_PANE).isEnabled()).as("one group already fills the workspace").isFalse();
            window.workspace().moveToNewGroup(window.tabs().get(1), window.workspace().model().activeGroup(), Side.DOWN);
""", """        var window = window(1);
        edt(() -> {
            window.updateActions();
            assertThat(window.action(ActionId.MAXIMIZE_PANE).isEnabled()).as("one group already fills the workspace").isFalse();
            window.split(Side.DOWN);
""", 1),
    ("""        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a = window.tabs().get(0); var b = window.tabs().get(1); var c = window.tabs().get(2);
            workspace.moveToNewGroup(b, workspace.model().activeGroup(), Side.RIGHT);
            workspace.moveToNewGroup(c, workspace.model().groupOf(b.id()).orElseThrow(), Side.DOWN);
""", """        var window = window(1);
        edt(() -> {
            window.split(Side.RIGHT);
            window.split(Side.DOWN);
            var a = window.tabs().get(0); var b = window.tabs().get(1); var c = window.tabs().get(2);
""", 1),
    ("            assertThat(layout.groups()).hasSize(3);\n",
     "            assertThat(layout.groups()).as(\"the view's tab and the pane beside it\").hasSize(2);\n", 1),
    ("            workspace.moveToNewGroup(right, workspace.model().activeGroup(), Side.RIGHT);\n", "", 1),
    ("""                var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel);
                window.workspace().moveToNewGroup(view, window.workspace().model().activeGroup(), Side.RIGHT);
""", """                var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel,
                    dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
""", 1)])
span(WT, "            var window = window(5);\n", "                view.focusTarget(() -> focused[0]++);\n",
     """            var window = window(1);
            var model = new Contributions();
            var inside = new JTextField();
            dev.jasper.app.contributions.Tab[] opened = new dev.jasper.app.contributions.Tab[1];
            edt(() -> {
                window.connectContributions(model);
                var panel = new JPanel(); panel.add(inside);
                var terminal = window.selectedTab();
                // The plugin's view alone on the left, four terminals one above another on the right.
                opened[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel,
                    dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
                window.closeTab(terminal);
                window.split(Side.RIGHT);
                for (int i = 0; i < 3; i++) window.split(Side.DOWN);
            });
            launch();
            edt(() -> {
                var view = opened[0];
                var background = window.tabs().subList(1, 5);
                window.selectTab(view);
                int[] focused = {0};
                view.focusTarget(() -> focused[0]++);
""")
PV = W + "ParentTabsViewTest.java"
edit(PV, [
    ("            owner[0].newTab(HOME);\n            owner[0].newParentTab(HOME);\n",
     "            owner[0].split(Side.RIGHT);\n            owner[0].newParentTab(HOME);\n", 1),
    ("""            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            Tab other = open(owner);
""", """            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            Tab other = open(owner);
""", 1)])
span(PV, "    @Test void whatOpensLandsInTheShownParentTabsActivePane() throws Exception {\n",
     "            assertThat(owner.parentTabCount()).isEqualTo(2);\n        });\n    }\n",
     """    /**
     * Review Focus 1 (tabs and panes spec 13; workspace plan 8a Rulings 3): whatever opens without a pane opens as a new
     * tab at the end of the strip, shown, and the main window's empty workspace takes the first in its place.
     */
    @Test void whatOpensWithoutAPaneOpensAsATabAndTheEmptyWorkspaceTakesTheFirst() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            var model = new Contributions();
            main.connectContributions(model);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            assertThat(main.parentTabCount()).as("the empty workspace takes it: no empty tab is left beside it").isEqualTo(1);
            assertThat(main.selectedTab()).isSameAs(notes);
            assertThat(main.workspace().model().groups()).as("in one pane").hasSize(1);
            TerminalContent terminal = main.newTab(HOME);
            assertThat(main.parentTabCount()).as("New Tab, a request's folder, Terminals.openTab: a new tab").isEqualTo(2);
            assertThat(main.selectedTab().content()).isSameAs(terminal);
            assertThat(main.workspace().tabs()).containsExactly(main.selectedTab());
            Tab second = model.tabs().open("dev.example.editor", "second", "second.txt", main.id(), new JPanel());
            assertThat(main.parentTabCount()).as("a plugin's view placed without a pane").isEqualTo(3);
            assertThat(main.windowLayout().parentTabs().getLast().layout().tabs()).as("at the end of the strip").containsExactly(second.id());
            assertThat(main.activeParentTab()).as("shown").isEqualTo(main.parentTabOf(second).orElseThrow());
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", main.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(main.parentTabOf(beside)).as("a split placement: a new pane of the shown tab").isEqualTo(main.parentTabOf(second));
            assertThat(main.workspace().model().groups()).hasSize(2);
        });
    }
""")
TD = W + "TabDragTest.java"
span(TD, "    /** Tabs and panes spec 4 and this plan's Rulings 18: a pane's strip starts no drag, under the classic tabs where it shows. */\n",
     "            } finally {\n                if (classic != null) classic.tabDrag().cancel();\n                DesktopTestSupport.closeOwnersNow();\n                themes.close();\n                EnterpriseLook.restoreDefault();\n            }\n        });\n    }\n",
     """    /** Tabs and panes spec 5 and 6: Enterprise's strip shows with one tab too, and a press on its entry starts the tab drag. */
    @Test void enterprisesStripAlwaysShowsAndStartsTheTabDrag() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                JPanel shown = frame(classic, 100, 50, 1208, 760);
                var strip = (ClassicTabs) classic.tabBar();
                assertThat(strip.component().isVisible()).as("one tab: the strip shows").isTrue();
                assertThat(strip.component().getHeight()).as("and takes its row").isEqualTo(classic.tabHeight());
                Tab only = classic.selectedTab();
                classic.newTab(HOME);
                relayout(shown);
                start(classic, only, onEntry(classic, classic.selectedTab(), 0.75));
                assertThat(classic.tabDrag().active()).as("a press on its entry starts the tab drag").isTrue();
                assertThat(classic.tabDrag().draggedParentTab()).isEqualTo(classic.parentTabOf(only).orElseThrow());
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
""")
span(TD, "\n    /**\n     * The user's decision of 2026-10-20 (one row under Enterprise, until this plan's Task 4): Enterprise shows no\n",
     "            } finally {\n                if (classic != null) classic.tabDrag().cancel();\n                DesktopTestSupport.closeOwnersNow();\n                themes.close();\n                EnterpriseLook.restoreDefault();\n            }\n        });\n    }\n", "")
edit(W + "MoveTabsTest.java", [(
    """            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::weight).as("the plan landed").containsExactly(0.3, 0.7));
            assertThat(layout.group(plan.activeGroup()).orElseThrow().tabs()).as("in its active pane")
                .containsExactly(planned.id(), extra.id());
""", """            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> assertThat(split.children())
                .extracting(Child::weight).as("the plan landed, its active pane split for the item it lacks").satisfiesExactly(
                    weight -> assertThat(weight).isCloseTo(0.3, org.assertj.core.api.Assertions.within(1e-9)),
                    weight -> assertThat(weight).isCloseTo(0.35, org.assertj.core.api.Assertions.within(1e-9)),
                    weight -> assertThat(weight).isCloseTo(0.35, org.assertj.core.api.Assertions.within(1e-9))));
            assertThat(layout.groupOf(extra.id())).as("in a pane of its own, beside the plan's active one").isPresent()
                .isNotEqualTo(layout.groupOf(planned.id()));
            assertThat(layout.activeGroup()).isEqualTo(plan.activeGroup());
""", 1)])
span(W + "WindowManagerTest.java",
     "    /** Parent tabs spec 8 and 10: a plugin's tab goes to the main window's shown parent tab; new windows are full windows. */\n",
     "            assertThat(content.parentTabCount()).as(\"the main window is not touched\").isEqualTo(2);\n",
     """    /** Tabs and panes spec 13: a plugin's tab with no window opens as a new tab of the main window; new windows are full. */
    @Test void aPluginsTabOpensANewTabOfTheMainWindowAndNewWindowsAreFullWindows() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            var content = main.content();
            ParentTabId first = content.activeParentTab();
            content.newParentTab(HOME);
            content.selectParentTab(first);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(content.parentTabCount()).as("a new tab of the main window").isEqualTo(3);
            assertThat(content.activeParentTab()).as("at the end of its strip, shown").isEqualTo(content.parentTabOf(notes).orElseThrow())
                .isEqualTo(content.windowLayout().parentTabs().getLast().id());
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            ChildWindow child = manager.children().getFirst();
            assertThat(child.content().windowLayout()).as("TabPlacement.NEW_WINDOW opens a full window").isInstanceOf(ParentTabs.class);
            assertThat(child.content().parentTabOf(away)).isPresent();
            child.content().newParentTab(HOME);
            assertThat(child.content().parentTabCount()).as("which opens parent tabs of its own").isEqualTo(2);
            ChildWindow terminal = manager.openTerminalWindow(null).orElseThrow();
            assertThat(terminal.content().windowLayout()).as("New Terminal Window too").isInstanceOf(ParentTabs.class);
            assertThat(content.parentTabCount()).as("the main window is not touched").isEqualTo(3);
""")
SN = W + "WorkspaceSnapshotTest.java"
edit(SN, [
    ("""            Tab first = main.content().selectedTab();
            main.content().openTab(logs, null, Optional.of("zsh"));
""", """            Tab first = main.content().selectedTab();
            ParentTabId firstParent = main.content().activeParentTab();
            main.content().openTab(logs, null, Optional.of("zsh"));
""", 1),
    ("            ParentTabId firstParent = main.content().activeParentTab();\n            main.content().newParentTab(work);\n",
     "            main.content().newParentTab(work);\n", 1),
    ("""            assertThat(main.content().parentTabCount()).as("the parent tab that shows holds a plugin's tab alone").isEqualTo(2);
""", """            assertThat(main.content().parentTabCount()).as("the terminal's tab, and each plugin's item in a tab of its own").isEqualTo(3);
""", 1)])
SV = W + "WorkspaceSavingTest.java"
edit(SV, [
    ("""            ParentTabId shown = main.content().activeParentTab();
            main.content().newParentTab(folder);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs()).as("a parent tab opening").hasSize(2);
            main.content().selectParentTab(shown);
            second();
            assertThat(((ParentTabs) saved().main().layout()).active()).as("a parent tab showing").isEqualTo(shown);
            ParentTabId opened = ((ParentTabs) saved().main().layout()).parentTabs().get(1).id();
            main.content().parentTabs().reorder(shown, 1);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs().get(1).id()).as("a parent tab moving along its bar").isEqualTo(shown);
            main.content().closeParentTab(opened);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs()).as("a parent tab closing").hasSize(1);
""", """            ParentTabId shown = main.content().activeParentTab();
            int count = main.content().parentTabCount();
            main.content().newTab(folder);
            second();
            assertThat(saved().main().layout().parentTabs()).as("a tab opening").hasSize(count + 1);
            main.content().selectParentTab(shown);
            second();
            assertThat(saved().main().layout().active()).as("a tab showing").isEqualTo(shown);
            ParentTabId opened = saved().main().layout().parentTabs().getLast().id();
            main.content().parentTabs().reorder(shown, count);
            second();
            assertThat(saved().main().layout().parentTabs().get(count).id()).as("a tab moving along its strip").isEqualTo(shown);
            main.content().closeParentTab(opened);
            second();
            assertThat(saved().main().layout().parentTabs()).as("a tab closing").hasSize(count);
""", 1),
    ("                main.content().newParentTab(folder);\n                main.content().newTab(folder);\n",
     "                main.content().newParentTab(folder);\n                main.content().split(Side.RIGHT);\n", 1)])
edit(W + "EnterpriseWorkstationMockTest.java", [
    ("""        // The pane's strip sits right under the toolbar again, the one row of tabs the mocks draw: Enterprise shows no
        // parent-tab bar (the user's decision of 2026-10-20), so the strip's top is the toolbar's rule's bottom.
""", """        // The window's tab strip sits right under the toolbar, the one row of tabs the mocks draw (tabs and panes spec 5
        // and 13): its top is the toolbar's rule's bottom.
""", 1),
    ('.as(what + "the strip under the toolbar, with no parent-tab bar between")', '.as(what + "the window\'s strip under the toolbar")', 1)])
for name, renames in {"CommandPaletteShortcutsTest": 1, "MoveTabsTest": 9, "TabDragWindowsTest": 1, "TabDragTest": 2,
        "ParentTabsViewTest": 5, "WorkspaceSavingTest": 1, "WorkspaceSnapshotTest": 2, "WindowMenuTest": 8,
        "WindowManagerTest": 4, "WindowTabsTest": 1}.items():
    regex(W + name + ".java", r"newParentTab\(", "newTab(", renames)
for name, renames in {"TabDragWindowsTest": 9, "TabDragTestSupport": 3, "TabDragTest": 2, "WindowTabsTest": 1}.items():
    regex(W + name + ".java", r"parentTabBar\(\)", "tabBar()", renames)
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: `Layout.open(TabId)` (`LayoutOperationsTest`), `GroupView.tab()` (`WorkspaceViewTest`)
and `TerminalContent terminal = main.newTab(HOME)` (`ParentTabsViewTest`; `newTab` returns nothing today) do not exist.

- [ ] **Step 3: Panes hold one item; the bar is the window's one strip**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/layout/Group.java` with:

```java
package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A pane, a leaf of the tree (tabs and panes spec 4): the one tab it holds and shows, or none in the empty workspace.
 * The record keeps a list and a selection, the shape {@code workspace.toml} saves (workspace plan 8a Rulings 2).
 *
 * @param id the pane's id
 * @param tabs its one tab, or none in the empty workspace's pane
 * @param selected the tab it shows, {@code tabs}' one; empty exactly when there is none
 */
public record Group(GroupId id, List<TabId> tabs, Optional<TabId> selected) implements Node {
    /** Copies the tab list and checks that it holds at most one tab, the one selected. */
    public Group {
        Objects.requireNonNull(id, "id");
        tabs = List.copyOf(tabs);
        Objects.requireNonNull(selected, "selected");
        if (tabs.size() > 1) throw new IllegalArgumentException("Pane " + id + " holds more than one tab");
        if (tabs.isEmpty() != selected.isEmpty())
            throw new IllegalArgumentException(tabs.isEmpty() ? "The empty pane " + id + " selects nothing" : "Pane " + id + " selects its tab");
        if (selected.isPresent() && !tabs.contains(selected.get()))
            throw new IllegalArgumentException("Pane " + id + " does not hold its selected tab " + selected.get());
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.JPanel;

/**
 * One pane of a tab's workspace (tabs and panes spec 4): it shows the pane's one item, or nothing in the empty
 * workspace, and no strip (spec 5); the window's strip lists its tabs. The item's component enters and leaves as the
 * layout puts the item in and out of the pane, so an item that moves to another pane or window keeps its session
 * (workspace tabs spec 2.3). The code calls a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
 */
final class GroupView extends JPanel {
    private final GroupId id;
    private Tab tab;
    private boolean closed;

    GroupView(GroupId id) {
        super(new BorderLayout());
        this.id = Objects.requireNonNull(id, "id");
        setName("group");
        setOpaque(false);
    }

    GroupId id() { return id; }

    /** The pane's item; null in the empty workspace. */
    Tab tab() { return tab; }

    /** Takes the pane's item from the layout and shows it, or nothing for the empty workspace's pane. */
    void display(Tab next) {
        if (closed || next == tab && (next == null || next.component().getParent() == this)) return;
        if (tab != null && tab != next && tab.component().getParent() == this) remove(tab.component());
        tab = next;
        if (next != null) {
            if (next.component().getParent() != this) add(next.component(), BorderLayout.CENTER);
            next.component().setVisible(true);
        }
        revalidate();
        repaint();
    }

    /** The pane left the layout: it keeps no item. */
    void close() {
        if (closed) return;
        closed = true;
        tab = null;
        removeAll();
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One tab's workspace (tabs and panes spec 4), under the tab's id: its {@link Layout} and the items the layout places,
 * drawn as {@link SplitContainer}s over {@link GroupView}s, one pane each, or the maximized pane alone. Every change is a
 * layout operation; the view renders the active pane and the maximize the layout holds and never picks them itself. It
 * rebuilds its splits only when the layout's shape changes (orientation, children, panes, maximize); otherwise it moves
 * items' components between pane views and passes divider weights on. It never creates or disposes an item: its window
 * attaches and detaches them. Its layout accessor is {@code model()}: a {@code JPanel} cannot declare a {@code layout()}
 * that returns a value, because {@code java.awt.Container} has a {@code void layout()}. The code calls a tab a parent
 * tab, a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
 */
final class WorkspaceView extends JPanel {
    private final WindowContent owner;
    private ParentTabId id;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
    private final Map<TabId, Tab> tabs = new HashMap<>();
    private final Map<List<Integer>, SplitContainer> splits = new HashMap<>();
    private Layout layout = Layout.empty();
    private Object shape;
    /** The shape of the main window's empty workspace, which shows {@link #empty} (workspace tabs spec 10). */
    private static final Object EMPTY = new Object();
    /** The main window's empty state, made the first time its workspace is empty. */
    private EmptyWorkspace empty;
    private boolean glass, closed;

    WorkspaceView(WindowContent owner, ParentTabId id) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.id = Objects.requireNonNull(id, "id");
        setName("workspace");
        render();
    }

    /** The layout this view renders. */
    Layout model() { return layout; }
    /** The tab this workspace is. */
    ParentTabId id() { return id; }
    /** The tab takes a new id ({@link ParentTabsView#reid}); its layout and items stay. */
    void id(ParentTabId value) { id = Objects.requireNonNull(value, "value"); }
    boolean isEmpty() { return layout.isEmpty(); }
    boolean contains(Tab tab) { return tab != null && tabs.get(tab.id()) == tab; }
    /** Every item in workspace order: the panes' items in tree order. */
    List<Tab> tabs() { return layout.tabs().stream().map(tabs::get).toList(); }
    int tabCount() { return tabs.size(); }
    /** The active pane's item; null in the empty workspace. */
    Tab selected() { return layout.activeTab().map(tabs::get).orElse(null); }
    Optional<Tab> tab(TabId id) { return Optional.ofNullable(tabs.get(id)); }
    List<GroupView> groupViews() { return List.copyOf(groups.values()); }
    GroupView groupView(GroupId id) { return groups.get(id); }
    GroupView activeGroupView() { return groups.get(layout.activeGroup()); }
    /** The split at {@code path}, while the layout has one there and shows it; null otherwise. */
    SplitContainer split(List<Integer> path) { return splits.get(path); }

    /** Puts {@code tab} into the empty workspace's pane, as a new tab's first item. */
    void open(Tab tab) { place(tab, layout.open(tab.id())); }

    /** Opens {@code tab} in a new pane on {@code side} of {@code group}; the empty workspace takes it in its pane. */
    void openBeside(Tab tab, GroupId group, Side side) {
        if (layout.isEmpty()) open(tab);
        else place(tab, layout.split(tab.id(), group, side));
    }

    private void place(Tab tab, Layout next) {
        tabs.put(tab.id(), tab);
        apply(next);
    }

    /** Takes {@code tab} out of the layout; its component leaves its pane, and its pane goes with it. */
    void remove(Tab tab) {
        if (!contains(tab)) return;
        Layout next = layout.remove(tab.id());
        tabs.remove(tab.id());
        apply(next);
    }

    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }

    /**
     * Focus went into {@code tab}: it becomes the active item without being focused again, so focus stays where it went
     * (Rulings 26); nothing changes while it already is the active item, so no focus request loops back here.
     */
    void focused(Tab tab) { if (contains(tab) && selected() != tab) apply(layout.select(tab.id()), false); }
    void activate(GroupId group) { apply(layout.activate(group)); }

    /** Moves {@code tab}'s pane to {@code side} of {@code group}, another pane of this workspace (plan 8c's pane moves). */
    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }

    /**
     * Gives this workspace {@code arranged}: its own items in another arrangement, as a tab moves between windows
     * (workspace plan 7b Rulings 6). Items {@code arranged} names that this workspace does not hold leave it first, and an
     * item it holds that {@code arranged} lacks, such as one a listener opened here meanwhile, joins in a new pane to the
     * right of the active one, so no item is lost and {@code arranged}'s active item still shows. Nothing changes when
     * {@code arranged} names none of this workspace's items.
     */
    void rearrange(Layout arranged) {
        Layout next = arranged;
        for (TabId tab : arranged.tabs()) if (!tabs.containsKey(tab)) next = next.remove(tab);
        if (next.isEmpty()) return;
        Optional<TabId> shown = next.activeTab();
        boolean joined = false;
        for (TabId tab : layout.tabs()) {
            if (next.contains(tab)) continue;
            next = next.split(tab, next.activeGroup(), Side.RIGHT);
            joined = true;
        }
        if (joined && shown.isPresent()) next = next.select(shown.get());
        apply(next);
    }

    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
    void toggleMaximize() { apply(layout.toggleMaximize(layout.activeGroup())); }
    /** Ends a maximize, as an item moving in or out of the window does (spec 15). */
    void unmaximize() { if (layout.maximized().isPresent()) apply(layout.unmaximize()); }
    /** The pane in {@code direction} from the active one, by the layout's geometry. */
    Optional<GroupId> neighbour(Side direction) { return layout.neighbour(layout.activeGroup(), direction); }

    private void apply(Layout next) { apply(next, true); }

    /**
     * Renders {@code next}; with {@code refocus} the active item takes focus when it, its pane or the shape changed
     * (Rulings 10), provided the workspace held focus when the change began.
     */
    private void apply(Layout next, boolean refocus) {
        if (closed) return;
        Tab before = selected();
        GroupId group = layout.activeGroup();
        Object previous = shape;
        boolean held = refocus && holdsFocus();
        layout = next;
        render();
        owner.workspaceChanged(this, held
            && (selected() != before || !layout.activeGroup().equals(group) || !Objects.equals(previous, shape)));
    }

    private boolean holdsFocus() { return holdsFocus(owner, this); }

    /**
     * Whether focus may follow a change of {@code area}, a workspace or a window's tabs: the palette's card does not hold
     * the window's input, and the permanent focus owner is in {@code area} or nowhere a person sees it (a closed terminal,
     * or the palette that just closed to run a command). Focus in the open palette, the Run window or a tool window stays
     * there.
     */
    static boolean holdsFocus(WindowContent owner, Component area) {
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return focus == null || !focus.isShowing() || SwingUtilities.isDescendingFrom(focus, area);
    }

    private void render() {
        var live = new LinkedHashMap<GroupId, GroupView>();
        for (Group group : layout.groups()) {
            GroupView view = groups.remove(group.id());
            live.put(group.id(), view != null ? view : new GroupView(group.id()));
        }
        for (GroupView gone : groups.values()) gone.close();
        groups.clear();
        groups.putAll(live);
        for (Group group : layout.groups()) groups.get(group.id()).display(group.selected().map(tabs::get).orElse(null));
        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        // The main window's empty workspace shows New Terminal Tab and Open File in place of its empty pane (spec 10).
        boolean showEmpty = layout.isEmpty() && owner.isMain();
        Object next = showEmpty ? EMPTY : shape(shown);
        if (!next.equals(shape)) {
            shape = next;
            splits.clear();
            removeAll();
            add(showEmpty ? emptyState() : build(shown, List.of()), BorderLayout.CENTER);
            revalidate();
        } else {
            for (var entry : splits.entrySet()) entry.getValue().setWeights(weightsAt(entry.getKey()));
        }
        repaint();
    }

    private EmptyWorkspace emptyState() {
        if (empty == null) {
            empty = new EmptyWorkspace(owner);
            // It paints over the workspace surface, so its controls take the workspace theme.
            WorkspaceTheme.apply(empty, owner.theme());
        }
        empty.refresh();
        return empty;
    }

    /** The main window's empty state while it shows; null otherwise. */
    EmptyWorkspace emptyWorkspace() { return shape == EMPTY ? empty : null; }

    /** The tree's shape without its weights: what a rebuild depends on. */
    private static Object shape(Node node) {
        return switch (node) {
            case Group group -> group.id();
            case Split split -> List.of(split.orientation(), split.children().stream().map(child -> shape(child.node())).toList());
        };
    }

    private JComponent build(Node node, List<Integer> path) {
        return switch (node) {
            case Group group -> groups.get(group.id());
            case Split split -> {
                var children = new ArrayList<JComponent>();
                for (int i = 0; i < split.children().size(); i++) {
                    var child = new ArrayList<>(path);
                    child.add(i);
                    children.add(build(split.children().get(i).node(), List.copyOf(child)));
                }
                var container = new SplitContainer(split.orientation(), path, split.children().stream().map(Child::weight).toList(),
                    children, this::resizeSplit);
                container.setBackground(getBackground());
                container.setGlass(glass);
                splits.put(path, container);
                yield container;
            }
        };
    }

    private List<Double> weightsAt(List<Integer> path) {
        Node node = layout.root();
        for (int index : path) node = ((Split) node).children().get(index).node();
        return ((Split) node).children().stream().map(Child::weight).toList();
    }

    /** Refreshes the empty state while it shows. */
    void refresh() {
        if (closed) return;
        if (empty != null) empty.refresh();
    }

    /**
     * Takes the theme: backgrounds, the empty state's zone, and the delegates of a pane the maximize keeps out of the tree
     * and of the empty state while an item hides it.
     */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        setBackground(theme.surface());
        for (SplitContainer split : splits.values()) split.setBackground(theme.surface());
        for (GroupView group : groups.values())
            if (updateDelegates && !SwingUtilities.isDescendingFrom(group, this)) SwingUtilities.updateComponentTreeUI(group);
        if (updateDelegates && empty != null && !SwingUtilities.isDescendingFrom(empty, this)) SwingUtilities.updateComponentTreeUI(empty);
        if (empty != null) WorkspaceTheme.apply(empty, theme);
    }

    /** Below opacity 1 the workspace and its splits paint nothing behind the items; dividers stay solid. */
    void setGlass(boolean value) {
        glass = value;
        setOpaque(!value);
        for (SplitContainer split : splits.values()) split.setGlass(value);
        repaint();
    }

    /** The window closed, or this tab went: every pane is released; the items are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (GroupView group : groups.values()) group.close();
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/TabStrip.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * A window's tab strip (tabs and panes spec 5; Enterprise presentation spec 10.5): the equal tabs, hidden while the
 * window holds one tab, or the classic tabs, always shown. It lists, selects, closes and opens through its
 * {@link ParentTabBar} only, a press on an entry starts the tab drag of a whole tab (spec 6), and it shows as active
 * while its window is. The equal tabs open and close with the configured motion where the look has it (workspace plan
 * 8a Rulings 20); the classic tabs never move. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the window's tabs' workspaces. */
    JComponent component();
    /** Takes the window's tabs, titles, selection and states. */
    void refresh();
    /** Whether the strip shows as active: its window active. */
    void setActive(boolean active);
    /** The configured animations; the classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** The window's tabs, as a menu of literal titles of at most {@code WindowTabs.TITLE_LIMIT} characters. */
    JPopupMenu tabList();
    /** Each tab's rectangle in this strip's coordinates, in strip order: a tab drag drops between them. */
    List<Rectangle> tabBounds();
    /** Releases the tabs. */
    @Override void close();
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTabs.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;

/**
 * The equal tabs (workspace tabs spec 10): a window's tab strip in the standard look (tabs and panes spec 5), equal
 * cells with centred titles, each entry a tab shown as its active pane's item; hidden while the window holds one tab. A
 * tab that opens or closes moves into or out of its place where the look has motion (spec 5; workspace plan 8a Rulings
 * 20). A tab that arrives or leaves by a move, is reordered, or arrives while the strip is hidden takes its place at
 * once, and an entry keeps its place while its tab shows another of its panes. EDT only.
 */
final class WindowTabs extends JPanel implements TabStrip {
    static final int TITLE_LIMIT = 200;
    private final ParentTabBar source;
    private final LongSupplier clock;
    private final List<Slot> slots = new ArrayList<>();
    private final IdentityHashMap<Tab, Entry> entries = new IdentityHashMap<>();
    private final List<Tab> order = new ArrayList<>();
    private final JPanel viewport = new JPanel(null);
    private AnimationSettings animations = AnimationSettings.defaults();
    private Timer timer;
    private Tab selected;
    private int layoutWidth = -1, layoutHeight = -1;
    private boolean active = true, disposed;

    WindowTabs(ParentTabBar source) { this(source, System::nanoTime); }
    WindowTabs(ParentTabBar source, LongSupplier clock) {
        super(null);
        this.source = source; this.clock = clock;
        setName("windowTabs"); setOpaque(false);
        viewport.setOpaque(false); viewport.setName("tabViewport"); add(viewport);
    }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);
        var updated = new ArrayList<>(source.tabs());
        // A tab whose entry shows another of its panes (a split, a focus change, a pane closing) keeps its entry and
        // its place: only what the entry shows changes (workspace plan 8a Rulings 20).
        for (Tab tab : List.copyOf(order)) {
            if (updated.contains(tab)) continue;
            Entry entry = entries.get(tab);
            for (Tab next : updated)
                if (!entries.containsKey(next) && entry.parent != null && entry.parent.equals(source.id(next))) {
                    entries.remove(tab); entry.tab = next; entries.put(next, entry);
                    order.set(order.indexOf(tab), next);
                    break;
                }
        }
        // An entry follows its tab's id, which ParentTabsView.reid and renew change.
        for (Tab tab : order) { Entry entry = entries.get(tab); if (entry != null) entry.parent = source.id(tab); }
        boolean changed = !updated.equals(order);
        boolean added = updated.stream().anyMatch(tab -> !entries.containsKey(tab));
        boolean removed = order.stream().anyMatch(tab -> !updated.contains(tab));
        // Only an open or a close moves: a tab that arrives or leaves by a move does not.
        boolean opened = updated.stream().anyMatch(tab -> !entries.containsKey(tab) && !tab.moving());
        boolean closing = order.stream().anyMatch(tab -> !updated.contains(tab) && tab.closed());
        if (changed) {
            for (Tab tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.remove(tab);
                entry.slot.entry = null; // The shrinking gap retains no closed session, plugin or controls.
                viewport.remove(entry);
            }
            if (!added && !removed) { settle(); slots.clear(); }
            order.clear(); order.addAll(updated);
            for (int i = 0; i < order.size(); i++) {
                Tab tab = order.get(i);
                if (!entries.containsKey(tab)) {
                    var entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
                    int at = i == 0 ? 0 : slots.indexOf(entries.get(order.get(i - 1)).slot) + 1;
                    while (at < slots.size() && slots.get(at).entry == null) at++;
                    slots.add(at, entry.slot);
                } else if (!added && !removed) slots.add(entries.get(tab).slot);
            }
            Curve curve = added ? Curve.OPEN : Curve.CLOSE;
            int millis = added ? animations.tabOpenMillis() : animations.tabCloseMillis();
            // A strip that does not show takes its tabs at once, so it shows and hides settled.
            if (order.size() <= 1 || !opened && !closing || !shown() || !animations.enabled() || !source.animates()
                || layoutWidth <= 0) millis = 0;
            for (Slot slot : slots) slot.width.target(slot.entry == null ? 0 : 1.0 / order.size(), now, millis, curve);
            sample(now);
        }
        selected = source.selected();
        setBackground(source.owner().theme().surface());
        for (Tab tab : order) entries.get(tab).refresh();
        doLayout(); syncTimer(); revalidate(); repaint();
    }

    /**
     * Whether the strip and every container above it are visible. It does not ask {@code isShowing()}, so a strip outside
     * a window, which has no parent, counts as visible.
     */
    private boolean shown() {
        for (Component component = this; component != null; component = component.getParent())
            if (!component.isVisible()) return false;
        return true;
    }

    @Override public void setActive(boolean active) { this.active = active; refresh(); }
    /** Whether the strip shows as active. */
    boolean active() { return active; }

    /** Literal bounded titles, also used by hosted-content integrations. */
    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Tab tab : order) {
            var item = new JCheckBoxMenuItem(capped(source.title(tab)), source.icon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) source.select(tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public List<Rectangle> tabBounds() {
        var bounds = new ArrayList<Rectangle>(order.size());
        for (Tab tab : order) bounds.add(SwingUtilities.convertRectangle(viewport, entries.get(tab).getBounds(), this));
        return bounds;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(source.owner().tabHeight())); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        // A genuine resize settles motion.
        if (layoutWidth > 0 && (width != layoutWidth || layoutHeight > 0 && height > 0 && height != layoutHeight)) settle();
        layoutWidth = width; layoutHeight = height;
        viewport.setBounds(0, 0, width, height);
        double x = 0;
        for (Slot slot : slots) {
            int left = (int) Math.round(x);
            x += slot.width.current * width;
            int right = (int) Math.round(x);
            slot.bounds.setBounds(left, 0, Math.max(0, right - left), height);
            if (slot.entry != null) {
                slot.entry.setBounds(slot.bounds);
                slot.entry.setVisible(right > left);
                slot.entry.doLayout();
            }
        }
    }

    // Repaint the translucent surface exactly once, even for child hover repaints.
    @Override protected boolean isPaintingOrigin() { return true; }
    @Override protected void paintComponent(Graphics graphics) {
        var bounds = new ArrayList<Rectangle>(slots.size());
        int chosen = -1, hovered = -1;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) chosen = bounds.size();
            if (slot.entry != null && slot.entry.hovered) hovered = bounds.size();
            bounds.add(slot.bounds);
        }
        ChromePainter.installed().tabStrip(this, graphics, source.owner().theme().workspace(), source.owner().workspaceSurface(), bounds, chosen, hovered, active);
    }

    private Color surface(boolean selected) {
        return ChromePainter.installed().tabSurface(source.owner().theme().workspace(), source.owner().workspaceSurface(), selected);
    }

    /** {@code title}, or past {@code TITLE_LIMIT} characters its start and an ellipsis within that limit, for tips and menus. */
    static String capped(String title) { return capped(title, TITLE_LIMIT); }

    /** {@code title}, or past {@code limit} characters its start and an ellipsis within that limit. */
    static String capped(String title, int limit) {
        if (title.length() <= limit) return title;
        int end = limit - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "\u2026";
    }

    @Override public void setAnimations(AnimationSettings settings) {
        if (disposed || animations.equals(settings)) return;
        animations = settings; settle(); doLayout(); repaint();
    }

    /** One elapsed-time frame; tests inject the same monotonic clock used when retargeting. */
    void animate(long now) {
        if (disposed) return;
        sample(now); doLayout(); syncTimer(); repaint();
    }
    private void sample(long now) {
        for (Slot slot : slots) slot.width.sample(now);
        slots.removeIf(slot -> slot.entry == null && !slot.width.running);
    }
    private void settle() {
        slots.removeIf(slot -> slot.entry == null);
        for (Slot slot : slots) slot.width.set(order.isEmpty() ? 0 : 1.0 / order.size());
        syncTimer();
    }
    boolean motionRunning() { return timer != null && timer.isRunning(); }
    private void syncTimer() {
        boolean moving = slots.stream().anyMatch(slot -> slot.width.running);
        if (moving && !disposed && timer == null) {
            timer = new Timer(8, event -> animate(clock.getAsLong())); timer.start();
        } else if ((!moving || disposed) && timer != null) { timer.stop(); timer = null; }
    }
    @Override public void close() {
        disposed = true; syncTimer(); slots.clear(); entries.clear(); order.clear(); selected = null;
        viewport.removeAll(); setVisible(false);
    }

    private static final class Slot {
        Entry entry;
        final Value width = new Value();
        final Rectangle bounds = new Rectangle();
        Slot(Entry entry) { this.entry = entry; }
    }

    /** Measured monotonic response, normalized to operation duration. See docs/design/alacritty-tabs.md. */
    private enum Curve {
        OPEN(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 192, 208, 225, 250, 283, 317, 367, 417, 475},
            new double[]{0, .088, .201, .331, .444, .546, .637, .711, .775, .824, .863, .894, .919, .940, .958, .975, .986, .993, .997, 1}),
        CLOSE(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 200, 233, 267, 300, 350, 375, 425},
            new double[]{0, .077, .250, .373, .486, .585, .665, .736, .796, .842, .877, .919, .951, .972, .986, .993, .997, 1});
        private final double[] times, values;
        Curve(double[] times, double[] values) { this.times = times; this.values = values; }
        double at(double fraction) {
            double time = fraction * times[times.length - 1];
            for (int i = 1; i < times.length; i++) if (time <= times[i]) {
                double local = (time - times[i - 1]) / (times[i] - times[i - 1]);
                return values[i - 1] + local * (values[i] - values[i - 1]);
            }
            return 1;
        }
    }
    private static final class Value {
        double current, from, to;
        long start, nanos;
        boolean running;
        Curve curve = Curve.OPEN;
        void set(double value) { current = from = to = value; running = false; }
        void target(double value, long now, int millis, Curve curve) {
            from = current; to = value; start = now; nanos = millis * 1_000_000L; this.curve = curve;
            running = millis > 0 && current != to;
            if (!running) set(value);
        }
        void sample(long now) {
            if (!running) return;
            double progress = Math.max(0, Math.min(1, (double) (now - start) / nanos));
            current = from + (to - from) * curve.at(progress);
            if (progress >= 1) set(to);
        }
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        NativeStyles.installed().unframed(button);
        button.setContentAreaFilled(false); button.setFocusable(false);
    }
    private final class Entry extends JPanel {
        /** What the entry shows: its tab's active pane's item, which changes as the tab shows another pane. */
        private Tab tab;
        /** The tab the entry is; it follows the tab's id, which a move between windows can change. */
        private ParentTabId parent;
        private final Slot slot;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
            }
        };
        private boolean hovered;

        Entry(Tab first) {
            super(null); tab = first; parent = source.id(first); slot = new Slot(this); setOpaque(false);
            flat(select); flat(close); select.setHorizontalAlignment(SwingConstants.CENTER);
            select.addActionListener(event -> { if (!disposed) source.select(tab); });
            close.setRolloverEnabled(true); close.setToolTipText(source.closeName());
            close.getAccessibleContext().setAccessibleName(source.closeName());
            close.addActionListener(event -> { if (!disposed) source.close(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                private void popup(MouseEvent event) { source.menu(tab).show(event.getComponent(), event.getX(), event.getY()); }
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { source.close(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        source.select(tab);
                        // The tab drag may start here (tabs and panes spec 6).
                        source.pressed(tab, event);
                    }
                }
                @Override public void mouseDragged(MouseEvent event) { source.dragged(event); }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    source.released(event);
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            addMouseMotionListener(gestures); select.addMouseMotionListener(gestures); close.addMouseMotionListener(gestures);
            add(close); add(select);
        }
        private void hover(boolean value) { hovered = value; doLayout(); repaint(); }
        void refresh() {
            String text = source.title(tab);
            select.setText(text); select.getAccessibleContext().setAccessibleName(text);
            select.setName("select:" + text); close.setName("close:" + text);
            String shortcut = source.shortcut(order.indexOf(tab));
            String tip = capped(source.tooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            select.getAccessibleContext().setAccessibleDescription(source.running(tab) ? "Session running" : null);
            Color surface = surface(tab == selected), ink = ThemeZone.color(this, SemanticColor.TEXT_PRIMARY);
            close.setIcon(AppIcons.forSurface(AppIcons.named("CLOSE"), surface, ink));
            close.setRolloverIcon(AppIcons.forSurface(AppIcons.named("CLOSE_HOVERED"), surface, ink));
            select.setSelected(tab == selected); select.setFont(ThemeZone.font(Typography.Role.BODY));
            select.setForeground(tab == selected ? ink
                : ThemeZone.color(this, !active ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_SECONDARY));
            doLayout();
        }
        @Override public void doLayout() {
            boolean closeFits = getWidth() >= NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH);
            int edge = Math.min(NativeStyles.installed().metric(closeFits ? DesignMetrics.Role.TAB_CLOSE_GUTTER
                : DesignMetrics.Role.TAB_COMPACT_GUTTER), getWidth() / 2);
            // Reserve symmetric close gutters only when text and the affordance can fit together.
            select.setBounds(edge, 0, Math.max(0, getWidth() - 2 * edge), getHeight());
            int closeWidth = NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_WIDTH);
            close.setBounds(Math.max(0, getWidth() - NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_TRAILING) - closeWidth),
                0, closeWidth, getHeight());
            close.setVisible(hovered && closeFits);
        }
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabBar.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * A window's one tab strip (tabs and panes spec 5): the look's strip over the window's tabs, each shown as its active
 * pane's item, as terminal apps title a tab. It shows from two tabs under the equal tabs and always under the classic
 * tabs (Enterprise), over the main window's empty workspace too (workspace plan 8a Rulings 10). A click shows a tab, and
 * its close button, a middle click and its menu close it after its items' guards; a press on an entry may start the tab
 * drag (spec 6). Under the equal tabs, tabs open and close with the motion where the look has it, and take their places
 * at once otherwise (Rulings 20). The code calls a tab a parent tab (Rulings 1). EDT only.
 */
final class ParentTabBar {
    private final WindowContent owner;
    private final ParentTabsView parentTabs;
    private final TabStrip strip;

    ParentTabBar(WindowContent owner, ParentTabsView parentTabs) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.parentTabs = Objects.requireNonNull(parentTabs, "parentTabs");
        strip = owner.look().tabs(this);
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        // The window starts with one tab; refresh keeps the strip's visibility from then on.
        strip.component().setVisible(owner.look().showsTabStrip(1));
    }

    /** The look's strip. */
    TabStrip strip() { return strip; }

    /** Takes the tabs and shows or hides the strip for their count at once: hidden first, shown last. */
    void refresh() {
        boolean shown = owner.look().showsTabStrip(parentTabs.count());
        if (!shown) strip.component().setVisible(false);
        strip.setActive(owner.windowActive());
        strip.component().setVisible(shown);
    }

    void close() { strip.close(); }

    /** The window the strip is in. */
    WindowContent owner() { return owner; }

    /** Each tab's active item, in strip order; the main window's empty workspace has none. */
    List<Tab> tabs() {
        var shown = new ArrayList<Tab>();
        for (WorkspaceView view : parentTabs.views()) if (view.selected() != null) shown.add(view.selected());
        return shown;
    }

    /** The shown tab's active item; null in the empty workspace. */
    Tab selected() { return parentTabs.active().selected(); }
    String title(Tab tab) { return owner.tabTitle(tab); }
    Icon icon(Tab tab) { return owner.tabIcon(tab); }
    String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** Select Tab's shortcut for the entry at {@code index}, as this platform writes it; empty for none. */
    String shortcut(int index) { return owner.tabShortcut(index); }
    /** Whether the entry is a terminal's, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    /** Whether the entry's shell runs, which the strip tells assistive technology. */
    boolean running(Tab tab) { return owner.running(tab); }
    /** The entry's menu (workspace plan 8a Rulings 8). */
    JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }
    /** Shows the tab whose entry shows {@code tab}. */
    void select(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::selectParentTab); }
    /** Closes the tab whose entry shows {@code tab}: every pane, after their items' guards. */
    void close(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::closeParentTab); }
    /**
     * A left press on a tab's entry: the tab drag may start from it, carrying the whole tab (spec 6). A dock drag still
     * running lost its release, so it ends first.
     */
    void pressed(Tab tab, MouseEvent event) {
        owner.dockDrag().cancel();
        owner.tabDrag().pressed(this, tab, event);
    }
    /** A drag event from the strip, which the tab drag follows. */
    void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }
    /** A release on the strip, which ends the tab drag. */
    void released(MouseEvent event) { owner.tabDrag().released(event); }
    /** The classic tabs' "+": New Tab (workspace plan 8a Rulings 11). */
    void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: what the "+" opens. */
    String newTabName() { return ActionId.NEW_TAB.label(); }
    /** Close Tab: an entry's × closes every pane of its tab. */
    String closeName() { return ActionId.CLOSE_TAB.label(); }
    /** The tab whose entry shows {@code tab}; null for an item no tab of the window holds, such as a closed one. */
    ParentTabId id(Tab tab) { return owner.parentTabOf(tab).orElse(null); }
    /** Whether the strip's entries move as tabs open and close: while the look has motion (Rulings 20). */
    boolean animates() { return owner.lookAnimates(); }
}
```

Run the edit script for the rest of the app, the SDK's Javadoc and the preview script:

```bash
python3 -I - <<'PY'
import pathlib, sys; sys.path.insert(0, "build/plan8a")
from edits import edit, span, regex
import ledger
A = "jasper-app/src/main/java/dev/jasper/app/"
W = A + "workspace/"
C = W + "WindowContent.java"
S = "jasper-sdk/src/main/java/dev/jasper/sdk/"
pathlib.Path(W + "StripSource.java").unlink()
ledger.drop(W + "StripSource.java", W + "ParentTabBar.java")
L = A + "layout/Layout.java"
edit(L, [
    ("import java.util.HashMap;\n", "", 1),
    (""" * One workspace's layout (spec 7): a tree of splits whose leaves are tab groups, the active group, the maximized group
 * if any, the groups' order of use and each group's tab order of use. Immutable: each operation returns a new layout and
 * rejects arguments that would break an invariant of spec 7.2 with an {@link IllegalArgumentException}. Equality covers
 * the tree, the active group and the maximized group; the orders of use only decide focus and selection after a removal
 * and are not saved.
""", """ * One tab's layout (spec 7; tabs and panes spec 4): a tree of splits whose leaves are panes, each holding one tab, the
 * active pane, the maximized pane if any and the panes' order of use. Immutable: each operation returns a new layout and
 * rejects arguments that would break an invariant of spec 7.2 with an {@link IllegalArgumentException}. Equality covers
 * the tree, the active pane and the maximized pane; the order of use only decides focus after a removal and is not
 * saved. The code calls a pane a group (workspace plan 8a Rulings 1).
""", 1),
    ("    private final Map<GroupId, List<TabId>> tabOrders;\n", "", 1),
    ("""     * A layout over {@code root}. Its order of use starts with {@code activeGroup}, then the other groups in tree order;
     * each group's tab order of use starts with its selected tab, then the tabs after it, then those before it, nearest
     * first (workspace plan 2, Rulings 7).
""", """     * A layout over {@code root}. Its order of use starts with {@code activeGroup}, then the other groups in tree order.
""", 1),
    ("        this(root, activeGroup, maximized, List.of(), Map.of());\n", "        this(root, activeGroup, maximized, List.of());\n", 1),
    ("""    private Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized, List<GroupId> recent,
            Map<GroupId, List<TabId>> tabOrders) {
""", """    private Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized, List<GroupId> recent) {
""", 1),
    ("""        var orders = new HashMap<GroupId, List<TabId>>();
        for (Group group : found) orders.put(group.id(), tabOrder(group, tabOrders.getOrDefault(group.id(), List.of())));
        this.tabOrders = Map.copyOf(orders);
""", "", 1),
    ("     * Every tab in workspace order: the groups in tree order, each group's tabs in strip order.\n",
     "     * Every tab in workspace order: the panes' tabs in tree order.\n", 1),
    ("""     * Moves {@code tab} into a new group on {@code side} of {@code group}, as {@link #split(TabId, GroupId, Side)} opens
     * one; a source group left empty is removed first, as {@link #remove(TabId)} removes it.
     *
     * @param tab a tab of the layout
     * @param group a group of the layout; the tab's own only when it holds other tabs
     * @param side where the new group goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the tab is the group's only tab
     */
    public Layout moveToNewGroup(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(side, "side");
        GroupId source = requireGroupOf(tab);
        Group target = requireGroup(group);
        if (source.equals(group) && target.tabs().size() == 1)
            throw new IllegalArgumentException("Tab " + tab + " is the only tab of group " + group + " and cannot split it");
        return remove(tab).beside(tab, group, side);
    }
""", """     * Moves {@code tab}'s pane to {@code side} of {@code group}, as {@link #split(TabId, GroupId, Side)} opens one; its own
     * pane goes first, as {@link #remove(TabId)} removes it (plan 8c's pane moves).
     *
     * @param tab a tab of the layout
     * @param group another pane of the layout
     * @param side where the pane goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the group is the tab's own
     */
    public Layout moveToNewGroup(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(side, "side");
        GroupId source = requireGroupOf(tab);
        requireGroup(group);
        if (source.equals(group)) throw new IllegalArgumentException("Tab " + tab + " cannot move beside its own pane " + group);
        return remove(tab).beside(tab, group, side);
    }
""", 1),
    ("""    private static void checkIndex(int index, int size) {
        if (index < 0 || index > size) throw new IllegalArgumentException("Index " + index + " is outside 0 to " + size);
    }

""", "", 1)])
span(L, """    /**
     * Inserts {@code tab} into {@code group} at {@code index}, selects it and activates the group.
""", """        return activated(replace(root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, false);
    }
""", """    /**
     * Puts {@code tab} into the empty workspace's pane, selected (tabs and panes spec 4: a pane holds one tab, so a tab
     * joins a layout that holds one by {@link #split} alone).
     *
     * @param tab a tab
     * @return the new layout
     * @throws IllegalArgumentException when this is not the empty workspace
     */
    public Layout open(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        if (!isEmpty()) throw new IllegalArgumentException("Only the empty workspace's pane takes a tab, not " + tab);
        return new Layout(new Group(activeGroup, List.of(tab), Optional.of(tab)), activeGroup, Optional.empty(), recent);
    }
""")
span(L, """    /**
     * Moves {@code tab} into {@code group} at {@code index}, counted without the tab, selects it and activates the group.
""", """        return rest.activated(replace(rest.root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, true);
    }

""", "")
span(L, """    /**
     * Removes {@code tab}, closing it or moving it out. When it was its group's selected tab, the group's most recently
""", """        return new Layout(next, heir, kept, recent, tabOrders);
    }
""", """    /**
     * Removes {@code tab}, closing it or moving it out, and its pane with it: the pane's weight goes to the sibling before
     * it (after it when it was first), a split left with one child collapses into that child and nesting is flattened
     * again, and the active pane becomes the most recently used remaining pane. The maximize stays while the maximized
     * pane stays and two panes remain (Rulings 5). Removing the last tab leaves the empty workspace, keeping its pane.
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout remove(TabId tab) {
        GroupId id = requireGroupOf(tab);
        if (root instanceof Group) return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty(), recent);
        GroupId heir = recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow();
        Node next = without(root, id).orElseThrow();
        Optional<GroupId> kept = maximized.filter(max -> !max.equals(id) && next instanceof Split);
        return new Layout(next, heir, kept, recent);
    }
""")
span(L, """
    /**
     * A group's tabs in order of use (workspace plan 2, Rulings 7): its selected tab, then the others in
""", """        return List.copyOf(order);
    }
""", "")
regex(L, r", recent, tabOrders\)", ", recent)", 4)
P = W + "ParentTabsView.java"
edit(P, [
    ("import dev.jasper.app.layout.ParentTabs;\n", "import dev.jasper.app.layout.ParentTabs;\nimport dev.jasper.app.layout.Side;\n", 1),
    (" * with the parent-tab bar ({@link ParentTabBar}) on top, and paints nothing itself.",
     " * with the window's tab strip ({@link ParentTabBar}) on top, and paints nothing itself.", 1),
    ("    /** The bar over the parent tabs (parent tabs spec 7), the view's last child (workspace plan 7a Rulings 19). */\n",
     "    /** The window's tab strip (tabs and panes spec 5), the view's last child (workspace plan 7a Rulings 19). */\n", 1),
    ("""     * Puts {@code tab}, moving in, in the active pane of the parent tab {@code id}; a parent tab the window lacks opens
     * under that id at {@code index} of the bar ({@link #openParentTab(Tab, ParentTabId, int)}). Workspace plan 7b's moves.
     */
    void land(Tab tab, ParentTabId id, int index) {
        WorkspaceView view = views.get(id);
        if (view != null) view.open(tab);
""", """     * Puts {@code tab}, moving in, in a new pane to the right of the active pane of the tab {@code id}, whose layout the
     * move gives it afterwards ({@link WorkspaceView#rearrange}); a tab the window lacks opens under that id at
     * {@code index} of the strip ({@link #openParentTab(Tab, ParentTabId, int)}). Workspace plan 7b's moves.
     */
    void land(Tab tab, ParentTabId id, int index) {
        WorkspaceView view = views.get(id);
        if (view != null) view.openBeside(tab, view.model().activeGroup(), Side.RIGHT);
""", 1),
    ("    /** Refreshes the bar and every parent tab's strips. */\n", "    /** Refreshes the strip and every tab's empty state. */\n", 1),
    ("""    void setAnimations(AnimationSettings settings) {
        for (WorkspaceView view : views.values()) view.setAnimations(settings);
        bar.strip().setAnimations(settings);
    }
""", """    /** The strip takes the configured motion (tabs and panes spec 5; workspace plan 8a Rulings 20). */
    void setAnimations(AnimationSettings settings) { bar.strip().setAnimations(settings); }
""", 1),
    ("""    /** Every strip, the bar's too, takes a new tab height. */
    void revalidateStrips() {
        bar.strip().component().revalidate();
        bar.strip().component().repaint();
        for (WorkspaceView view : views.values())
            for (GroupView group : view.groupViews()) {
                group.strip().component().revalidate();
                group.strip().component().repaint();
            }
    }
""", """    /** The strip takes a new tab height. */
    void revalidateStrips() {
        bar.strip().component().revalidate();
        bar.strip().component().repaint();
    }
""", 1),
    ("    /** The window closed: the bar and every parent tab's strips are released; the tabs are its registry's to close. */\n",
     "    /** The window closed: the strip and every tab's panes are released; the items are its registry's to close. */\n", 1)])
edit(W + "ClassicTabs.java", [
    ("""/**
 * Classic editor tabs of one tab group (spec 10.5; the Workstation mocks): always visible, content-sized, each with a
 * status dot for a terminal tab, its label and a persistent ×, a "+" after the last one (at the trailing edge, over the
 * tabs a crowded strip clips) that opens a new tab (New Tab);
 * the active tab on {@code surface.card} with a 2 px {@code tab.indicator} underline; 1 px {@code border.window}
 * dividers and the strip's rule on {@code surface.tabstrip}. Never moves. It shows a window's parent tabs too, each as
 * its active tab (parent tabs spec 7).
 */
""", """/**
 * The classic tabs (spec 10.5; the Workstation mocks): a window's tab strip under Enterprise (tabs and panes spec 5),
 * always visible, content-sized, each entry a tab shown as its active pane's item, with a status dot for a terminal, its
 * label and a persistent ×, and a "+" after the last one (at the trailing edge, over the tabs a crowded strip clips)
 * that opens a new tab (New Tab); the active tab on {@code surface.card} with a 2 px {@code tab.indicator} underline;
 * 1 px {@code border.window} dividers and the strip's rule on {@code surface.tabstrip}. Never moves.
 */
""", 1),
    ("    private final StripSource source;\n", "    private final ParentTabBar source;\n", 1),
    ("    ClassicTabs(StripSource source) {\n", "    ClassicTabs(ParentTabBar source) {\n", 1),
    ("{@link StripSource#closeName}", "{@link ParentTabBar#closeName}", 1),
    ("{@link StripSource#newTab}", "{@link ParentTabBar#newTab}", 1)])
span(W + "WorkspaceChrome.java", """    /** One tab group's strip, or a window's parent-tab bar (spec 10.5; workspace tabs spec 10; parent tabs spec 7). */
""", """            case CLASSIC -> false;
        };
    }
""", """    /** A window's tab strip (spec 10.5; tabs and panes spec 5). */
    TabStrip tabs(ParentTabBar bar) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(bar);
            case CLASSIC -> new ClassicTabs(bar);
        };
    }

    /**
     * Whether a window holding {@code count} tabs shows its tab strip (tabs and panes spec 5): the equal tabs from two
     * tabs; the classic tabs (Enterprise) always, over the main window's empty workspace too.
     */
    boolean showsTabStrip(int count) {
        return switch (tabs) {
            case EQUAL -> count > 1;
            case CLASSIC -> true;
        };
    }
""")
edit(C, [
    ("""     * The registry-facing side of this window's workspace (workspace tabs spec 9.1): its groups are every parent tab's, so
     * a tab placed beside a group lands in that group's parent tab (workspace plan 7a Rulings 8).
     */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            parentTabs::groupOf,
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side, false));
""", """     * The registry-facing side of this window's workspace (workspace tabs spec 9.1): its panes are every tab's, so an item
     * placed beside a pane lands in that pane's tab (workspace plan 7a Rulings 8), and one placed without a pane opens as
     * a new tab (plan 8a Rulings 3).
     */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            parentTabs::groupOf,
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side));
""", 1),
    ("""     * Shows a tab in this window, at the end of the shown parent tab's active group and selected: one the registry
     * placed here, or one this window opened. A failure leaves nothing mounted, takes back what the terminal registry
     * heard, and propagates.
     */
    private void attach(Tab tab) { attach(tab, false, null, null, false); }

    /**
     * As {@link #attach(Tab)}; with {@code opened} a new terminal tab's pane is announced as opened, with {@code beside}
     * the tab opens in a new group on {@code side} of that group, in the parent tab holding it, and with
     * {@code parentTab} it opens in a new parent tab of its own (parent tabs spec 8).
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side, boolean parentTab) {
""", """     * Shows an item in this window, in a new tab at the end of the strip and shown, which the main window's empty
     * workspace takes in its place (tabs and panes spec 13; workspace plan 8a Rulings 3): one the registry placed here, or
     * one this window opened. A failure leaves nothing mounted, takes back what the terminal registry heard, and
     * propagates.
     */
    private void attach(Tab tab) { attach(tab, false, null, null); }

    /**
     * As {@link #attach(Tab)}; with {@code opened} a new terminal's pane is announced as opened, and with {@code beside}
     * the item opens in a new pane on {@code side} of that pane, in the tab holding it.
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side) {
""", 1),
    ("""            if (parentTab) parentTabs.openParentTab(tab);
            // A tab a move between windows brings in lands in the parent tab chosen for it (workspace plan 7b Rulings 6).
            else if (beside == null && landing != null && tab.moving()) parentTabs.land(tab, landing.parentTab(), landing.index());
            else if (beside == null) workspace().open(tab);
""", """            // An item a move between windows brings in lands in the tab chosen for it (workspace plan 7b Rulings 6).
            if (beside == null && landing != null && tab.moving()) parentTabs.land(tab, landing.parentTab(), landing.index());
            // Whatever opens without a pane opens as a new tab; the main window's empty workspace takes it in its place.
            else if (beside == null && parentTabs.isEmpty()) workspace().open(tab);
            else if (beside == null) parentTabs.openParentTab(tab);
""", 1),
    ("    /** The shown parent tab's active group's selected tab; null in an empty workspace. */\n",
     "    /** The shown tab's active pane's item; null in an empty workspace. */\n", 1),
    ("    /** This window's tabs: its parent tabs in bar order, each one's tabs in workspace order. */\n",
     "    /** This window's items: its tabs in strip order, each one's items pane by pane. */\n", 1),
    ("""    /** The parent-tab bar's strip (parent tabs spec 7). */
    TabStrip parentTabBar() { return parentTabs.bar().strip(); }
""", "", 1),
    ("""    /** The active group's equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) tabBar(); }
    /** The active group's strip. */
    TabStrip tabBar() { return workspace().activeGroupView().strip(); }
""", """    /** The window's equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) tabBar(); }
    /** The window's one tab strip (tabs and panes spec 5). */
    TabStrip tabBar() { return parentTabs.bar().strip(); }
""", 1),
    ("""    /** A tab whose shell starts in {@code directory}; null means where a new shell starts (the user's home). */
    public void newTab(Path directory) { openTab(directory); }

    /**
     * New Tab (parent tabs spec 8): a terminal in a new parent tab at the end of the bar, shown, starting in
     * {@code directory}. The main window's empty workspace takes it in its only parent tab, the one parent tab that may be
     * empty (workspace plan 7a Rulings 7).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal tab; null once the window has closed
     */
    public TerminalContent newParentTab(Path directory) {
        return parentTabs.isEmpty() ? openTab(directory)
            : openTerminal(directory, null, Optional.empty(), Optional.empty(), null, null, true);
    }
""", """    /**
     * New Tab (tabs and panes spec 10): a terminal in a new tab at the end of the strip, shown, starting in
     * {@code directory}. The main window's empty workspace takes it in its only tab, the one tab that may be empty
     * (workspace plan 7a Rulings 7).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal; null once the window has closed
     */
    public TerminalContent newTab(Path directory) { return openTab(directory); }
""", 1),
    ("        return openTerminal(directory, requestOrNull, profile, program, null, null, false);\n",
     "        return openTerminal(directory, requestOrNull, profile, program, null, null);\n", 1),
    ("""     * A terminal tab starting in {@code directory} (null: where a new shell starts), in the active group, in a new group
     * on {@code side} of {@code beside} when that is given, or in a new parent tab of its own when {@code parentTab}.
     * Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side,
                                         boolean parentTab) {
""", """     * A terminal starting in {@code directory} (null: where a new shell starts), in a new tab of its own, or in a new
     * pane on {@code side} of {@code beside} when that is given. Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side) {
""", 1),
    ("            attach(tab, true, beside, side, parentTab);\n", "            attach(tab, true, beside, side);\n", 1),
    ("        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace().model().activeGroup(), side, false);\n",
     "        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace().model().activeGroup(), side);\n", 1),
    ("        return openTerminal(directory, requestOrNull, profile, program, group, side, false);\n",
     "        return openTerminal(directory, requestOrNull, profile, program, group, side);\n", 1),
    ])
edit(W + "WorkspaceChrome.java", [(
    "    /** The editor tabs, made for each tab group and each window's parent-tab bar by {@link #tabs(StripSource)}. */\n",
    "    /** The editor tabs, made for each window's tab strip by {@link #tabs(ParentTabBar)}. */\n", 1)])
edit(P, [("    /** The parent-tab bar. */\n", "    /** The window's tab strip. */\n", 1)])
edit(A + "layout/ParentTab.java", [(
    " * One parent tab (parent tabs spec 4): an unnamed set of panes, the groups of its {@link Layout}, under its own id.\n",
    " * One tab of a window (tabs and panes spec 4; the code calls it a parent tab): its panes, the groups of its\n"
    " * {@link Layout}, each holding one item, under its own id.\n", 1)])
edit(A + "layout/ParentTabs.java", [(
    " * A window's parent tabs (parent tabs spec 4), as the main window and full windows hold them: the parent tabs in bar\n",
    " * A window's tabs (tabs and panes spec 4; the code calls them parent tabs), as the main window and full windows hold\n"
    " * them: the parent tabs in strip\n", 1)])
edit(A + "layout/GroupId.java", [(
    " * A tab group's identity, unique in the application and kept in {@code workspace.toml}.\n",
    " * A pane's identity (the code calls a pane a group), unique in the application and kept in {@code workspace.toml}.\n", 1)])
edit(A + "layout/package-info.java", [(
    """ * The workspace layout model (workspace tabs spec, section 7): tab and group ids, a workspace's tab groups in nested
 * weighted splits, its active and maximized group and the groups' order of use, the operations that return a new
""", """ * The workspace layout model (workspace tabs spec, section 7; tabs and panes spec 4): tab and group ids, a tab's panes
 * (groups, one tab each) in nested weighted splits, its active and maximized pane and the panes' order of use, the
 * operations that return a new
""", 1)])
edit(A + "contributions/Tab.java", [(
    """ * One tab of any window (workspace tabs spec 9.1): its id and kind, its content, its presentation (title, detail, icon,
 * unsaved marker, location, host, status items), its commands and focus target, and the window that holds it. Moving the
""", """ * One item of any window, the one a pane shows (workspace tabs spec 9.1; tabs and panes spec 3; the code calls an item
 * a tab): its id and kind, its content, its presentation (title, detail, icon, unsaved marker, location, host, status
 * items), its commands and focus target, and the window that holds it. Moving the
""", 1)])
edit(W + "WorkspaceActions.java", [("            case NEW_TAB -> owner.newParentTab(owner.directory());\n",
    "            case NEW_TAB -> owner.newTab(owner.directory());\n", 1)])
regex(W + "TabDrag.java", r"window\.parentTabBar\(\)", "window.tabBar()", 4)
R = A + "contributions/TabRegistry.java"
edit(R, [
    ("""        /** At the end of the window's active group. */
        ACTIVE_GROUP,
        /** In a new group to the right of the active group. */
        SPLIT_RIGHT,
        /** In a new group below the active group. */
        SPLIT_DOWN,
""", """        /** In a new tab of the window, at the end of its strip and shown (tabs and panes spec 13). */
        NEW_TAB,
        /** In a new pane to the right of the window's focused pane. */
        SPLIT_RIGHT,
        /** In a new pane below the window's focused pane. */
        SPLIT_DOWN,
""", 1),
    ("{@link Placement#ACTIVE_GROUP} one would", "{@link Placement#NEW_TAB} one would", 2),
    ("""     * Opens a plugin's view as a tab at the end of the active group of {@code owner}'s window, or the main window when
     * {@code owner} is null or not registered, and selects it. A (plugin, key) already open is selected again, and
""", """     * Opens a plugin's view in a new tab of {@code owner}'s window, or of the main window when {@code owner} is null or
     * not registered, and shows it (tabs and panes spec 13). A (plugin, key) already open is selected again, and
""", 1),
    ("""     * As {@link #open(String, String, String, UUID, JComponent)}, the new tab where {@code placement} puts it: in that
     * window's active group or a new group beside it, or in a new child window of its own (workspace tabs spec 14). A
     * (plugin, key) already open is selected where it is. One asked for while the main window is being composed waits
     * for it and opens at the end of its active group, whatever its placement.
""", """     * As {@link #open(String, String, String, UUID, JComponent)}, the new tab where {@code placement} puts it: in a new
     * tab of that window or a new pane beside its focused pane, or in a new child window of its own (workspace tabs spec
     * 14). A (plugin, key) already open is selected where it is. One asked for while the main window is being composed
     * waits for it and opens in a new tab of it, whatever its placement.
""", 1),
    ("        return open(plugin, key, title, owner, component, Placement.ACTIVE_GROUP);\n",
     "        return open(plugin, key, title, owner, component, Placement.NEW_TAB);\n", 1),
    ("                case ACTIVE_GROUP, NEW_WINDOW -> host.attach().accept(tab);\n",
     "                case NEW_TAB, NEW_WINDOW -> host.attach().accept(tab);\n", 1),
    ("     * Moves a tab of any kind to {@code window}, at the end of that window's active group, and selects it there; its\n",
     "     * Moves a tab of any kind to {@code window}, in a new tab of that window, and selects it there; its\n", 1)])
edit(A + "contributions/WorkspaceHost.java", [
    (" * @param attach shows a tab the registry placed in this window, at the end of its active group\n",
     " * @param attach shows a tab the registry placed in this window, in a new tab of its own (tabs and panes spec 13)\n", 1),
    ("    /** Shows a tab in a new group beside a group of the window (workspace tabs spec 14's split placements; plan 5's drops). */\n",
     "    /** Shows a tab in a new pane beside a pane of the window (workspace tabs spec 14's split placements). */\n", 1),
    ("         * @param group a group of this window; null for its active group\n",
     "         * @param group a pane of this window; null for its focused pane\n", 1),
    ("     * A workspace that places every tab at the end of its active group, as a fake window in a test does.\n",
     "     * A workspace that attaches a tab placed beside a pane as it attaches any other, as a fake window in a test does.\n", 1)])
edit(A + "plugins/HostedUi.java", [(
    "                    case ACTIVE_GROUP -> dev.jasper.app.contributions.TabRegistry.Placement.ACTIVE_GROUP;\n",
    "                    case ACTIVE_GROUP -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_TAB;\n", 1)])
edit(A + "application/JasperApplication.java", [(
    "     * names when there is one), else raised; each other directory opens as a terminal tab in its active group and each\n",
    "     * names when there is one), else raised; each other directory opens as a new terminal tab and each\n", 1)])
edit(S + "ui/TabPlacement.java", [(
    """    /**
     * At the end of the active group of the owner's window's shown parent tab, or of the main window's when there is
     * no owner.
     */
    ACTIVE_GROUP,
    /** In a new group to the right of the group {@link #ACTIVE_GROUP} names. */
    SPLIT_RIGHT,
    /** In a new group below the group {@link #ACTIVE_GROUP} names. */
    SPLIT_DOWN,
""", """    /**
     * In a new tab of the owner's window, or of the main window when there is no owner: at the end of its tab strip and
     * shown, the main window's empty workspace taking it in its place. A content tab is an item, and each pane of a tab
     * holds one.
     */
    ACTIVE_GROUP,
    /** In a new pane to the right of the focused pane of that window's shown tab. */
    SPLIT_RIGHT,
    /** In a new pane below the focused pane of that window's shown tab. */
    SPLIT_DOWN,
""", 1)])
edit(S + "ui/Windows.java", [(
    """     * Opens an owned view as a content tab at the end of the active group of the owner's window's shown parent tab
     * ({@link TabPlacement#ACTIVE_GROUP}); moving retains it, final closure disposes it.
""", """     * Opens an owned view as a content tab in a new tab of the owner's window ({@link TabPlacement#ACTIVE_GROUP}); moving
     * retains it, final closure disposes it.
""", 1)])
edit(S + "ui/ContentTab.java", [(
    """     * Move to Main Window: the end of the active group of the main window's shown parent tab takes the tab and its
     * content, the main window restored first when none is open. A tab the main window holds, a tab with a modal prompt
""", """     * Move to Main Window: a new tab of the main window, at the end of its tab strip, takes the tab and its content, the
     * main window restored first when none is open. A tab the main window holds, a tab with a modal prompt
""", 1)])
edit(S + "terminal/Terminals.java", [
    ("     * The pane of the active window's active tab (the selected tab of its active group), when that tab is a terminal tab.\n",
     "     * The pane of the active window's active tab (the focused pane's, in its shown tab), when that tab is a terminal tab.\n", 1),
    ("     * Opens a terminal tab in the active group of the window's shown parent tab and selects it.",
     "     * Opens a terminal in a new tab of the window, at the end of its tab strip, and shows it.", 1)])
edit(S + "terminal/WindowHandle.java", [
    ("""     * The window's tabs, terminal and content: parent tab by parent tab in bar order, then group by group, each group's
     * tabs in strip order;
""", """     * The window's tabs, terminal and content, each the item of one pane: tab by tab in strip order, then pane by pane;
""", 1),
    ("     * The active tab: the selected tab of the active group of the window's shown parent tab, terminal or content.\n",
     "     * The active tab: the focused pane's item in the window's shown tab, terminal or content.\n", 1)])
edit(S + "terminal/Direction.java", [
    ("/** Which side of the target pane's tab group a split puts the new terminal tab's group. */\n",
     "/** Which side of the target pane a split puts the new terminal's pane. */\n", 1),
    ("    /** To the right of the target's group. */\n", "    /** To the right of the target pane. */\n", 1),
    ("    /** Below the target's group. */\n", "    /** Below the target pane. */\n", 1)])
edit(S + "terminal/Terminals.java", [
    ("""     * Opens a terminal tab in a new tab group beside the target pane's group and selects it: Jasper splits a window's
     * workspace, never a tab. A local request needs {@code terminal.open}; one naming a profile the host does not list
     * opens nothing.
""", """     * Opens a terminal in a new pane beside the target pane, in the target's tab, and selects it. A local request needs
     * {@code terminal.open}; one naming a profile the host does not list opens nothing.
""", 1),
    ("""     * @param target the pane whose group the new group goes beside
     * @param direction which side of the target's group the new group takes
""", """     * @param target the pane the new pane goes beside
     * @param direction which side of the target pane the new pane takes
""", 1)])
edit(S + "terminal/TerminalEvents.java", [(
    "    /** A window's active tab, the selected tab of its active group, changed. */\n",
    "    /** A window's active tab, the focused pane's item in its shown tab, changed. */\n", 1)])
PY
```

The Terminals edit keeps the rest of that Javadoc line, which runs on past the 120 columns as it did.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --continue`
Expected: PASS. Then `./gradlew :jasper-app:check :jasper-sdk:check`: PASS (doclint over the changed Javadoc, the
architecture checks and the ledger test, which sees `StripSource`'s row gone).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src jasper-sdk/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: a pane holds one item, the window one strip

A pane holds one item: the layout loses its stacking operations and
whatever opens without a pane opens as a new tab, the main window's
empty workspace taking it in its place. The parent-tab bar is the
window's one tab strip, from two tabs in the standard look and
always under Enterprise; no pane has a strip, and the strip's tabs
open and close with the motion the pane strips had. The registry's
ACTIVE_GROUP becomes NEW_TAB.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The focused pane: every other pane of a split tab dims

Spec 5: while a tab holds two or more panes, every pane but the active one is dimmed, by `terminal.dim_inactive_panes`
(Rulings 12, Spec issues 2), and an inactive window dims its terminals as it does today (Rulings 13). Terminals keep
today's rule, which already dims every terminal but the window's active item, so this task changes nothing for them;
any other item in an inactive pane of a split tab dims under the pane's overlay. A left press on a pane's item makes
its pane the active one, a view that takes no focus too (Rulings 21). Nothing animates.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/PaneDimTest.java` (and its ledger row)
- Modify: `workspace/GroupView.java` (Task 4's: imports, fields, `dim`, `paint`, `isPaintingOrigin`, class Javadoc),
  `workspace/WorkspaceView.java` (Task 4's: `render`, `dimPanes`, `applyTheme`), `workspace/WindowContent.java`
  (`paneClicks` after `focusOwners` `:71-73`, its registration after `:368`, its removal in the constructor's failure
  path `:376` and in `close` `:2273`; `paneDim` and `dimPanes` after `configured` `:1822`),
  `workspace/WorkspaceConfiguration.java` (`:49`, after the terminal loop)

**Interfaces:**
- Consumes: Task 4's `GroupView(GroupId)`, `GroupView.tab()`, `WorkspaceView.render`; today's
  `TerminalPane.setActive(boolean)` and `TerminalView.setInactiveDim(float)`, unchanged.
- Produces: `GroupView.dim(boolean dimmed, float amount, Color colour)` and `float dim()` (the overlay's amount, 0 for
  a terminal); `WorkspaceView.dimPanes()`; `WindowContent.paneDim()` → `float`, `WindowContent.dimPanes()`;
  `WindowContent`'s private `paneClicks`, an `AWTEventListener` on `AWTEvent.MOUSE_EVENT_MASK` (not counted by
  `TabDragTestSupport.motionListeners()`, which counts `MOUSE_MOTION_EVENT_MASK` listeners).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PaneDimTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 5 and Review Focus 3: in an active window, every pane of a split tab but the active one dims by
 * {@code terminal.dim_inactive_panes}, and a tab of one pane shows clear; an inactive window dims every terminal, as
 * before; a plugin's view dims under its pane's overlay while it is an inactive pane (workspace plan 8a Rulings 12 and
 * 13).
 */
class PaneDimTest {
    private static final float DIM = ConfigSnapshot.defaults().terminal().dimInactivePanes();
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** A window of one tab whose shells have started: one terminal, and with {@code split} a second to its right. */
    private WindowContent window(boolean split) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); if (split) owner[0].split(Side.RIGHT); });
        launch();
        edt(() -> { });
        return owner[0];
    }

    /** The dim a terminal item's view paints. */
    private static float dim(Tab tab) {
        try {
            var view = ((TerminalContent) tab.content()).pane().view();
            var field = view.getClass().getDeclaredField("inactiveDim");
            field.setAccessible(true);
            return (float) field.get(view);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The colour at the middle of {@code pane}, painted at 200 by 100. */
    private static Color middle(GroupView pane) {
        pane.setSize(200, 100);
        pane.doLayout();
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { pane.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(100, 50), true);
    }

    /** {@link ConfigSnapshot#defaults()} with {@code terminal.dim_inactive_panes} at {@code amount}. */
    private static ConfigSnapshot dimmedBy(float amount) {
        TerminalConfig t = ConfigSnapshot.defaults().terminal();
        return ConfigSnapshot.defaults().toBuilder().terminal(new TerminalConfig(t.shell(), t.env(), t.scrollback(), t.optionAsMeta(),
            t.cursorShape(), t.cursorBlink(), amount, t.copyOnSelect(), t.bell(), t.onExit(), t.shellIntegration())).build();
    }

    @Test void aTabOfOnePaneDimsOnlyWhileItsWindowIsInactive() throws Exception {
        var owner = window(false);
        edt(() -> {
            Tab only = owner.selectedTab();
            assertThat(DIM).as("the configured default").isPositive();
            assertThat(dim(only)).as("one pane in an active window").isZero();
            owner.setActive(false);
            assertThat(dim(only)).as("an inactive window dims its terminals, as before (Rulings 13)").isEqualTo(DIM);
            owner.setActive(true);
            assertThat(dim(only)).isZero();
        });
    }

    @Test void aTabOfTwoPanesDimsAllButItsActivePaneAndTheDimFollowsAtOnce() throws Exception {
        var owner = window(true);
        edt(() -> {
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            assertThat(dim(right)).as("the active pane").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.focusGroup(Side.LEFT);
            assertThat(dim(left)).as("Focus Pane Left").isZero();
            assertThat(dim(right)).isEqualTo(DIM);
            var view = ((TerminalContent) right.content()).pane().view();
            var gained = new FocusEvent(view, FocusEvent.FOCUS_GAINED);
            for (var listener : view.getFocusListeners()) listener.focusGained(gained);
            assertThat(dim(right)).as("a click into a pane").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.setActive(false);
            assertThat(dim(right)).as("an inactive window dims its active pane's terminal too").isEqualTo(DIM);
            assertThat(dim(left)).isEqualTo(DIM);
            owner.setActive(true);
            assertThat(dim(right)).isZero();
            ParentTabId first = owner.activeParentTab();
            owner.newTab(HOME);
            owner.selectParentTab(first);
            assertThat(dim(right)).as("a tab switch shows the tab as it was left").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.closeTab(right);
            assertThat(dim(left)).as("one pane again: nothing dims").isZero();
        });
    }

    @Test void aMaximizedPaneShowsClear() throws Exception {
        var owner = window(true);
        edt(() -> {
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            assertThat(dim(right)).as("the maximized pane").isZero();
            owner.toggleMaximize();
            assertThat(dim(left)).isEqualTo(DIM);
        });
    }

    @Test void aPluginsViewAsTheInactivePaneDimsUnderAnOverlay() throws Exception {
        var owner = window(false);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            var panel = new JPanel();
            panel.setOpaque(true);
            panel.setBackground(new Color(0xFF00FF));
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), panel, TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            assertThat(pane.dim()).as("the active pane").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
            Color clear = middle(pane);
            owner.selectTab(terminal);
            assertThat(pane.dim()).as("the inactive plugin's view").isEqualTo(DIM);
            assertThat(dim(terminal)).isZero();
            assertThat(middle(pane)).as("the overlay over the view").isNotEqualTo(clear);
            owner.setActive(false);
            assertThat(pane.dim()).as("an inactive pane's view stays dimmed in an inactive window").isEqualTo(DIM);
            owner.setActive(true);
            owner.selectTab(view);
            assertThat(pane.dim()).isZero();
            assertThat(middle(pane)).as("clear again at once").isEqualTo(clear);
            owner.setActive(false);
            assertThat(pane.dim()).as("the active pane's view stays clear: only terminals dim with the window").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
        });
    }

    /** Workspace plan 8a Rulings 21: a press on a pane makes it the active one, though its view takes no focus. */
    @Test void aPressOnAPaneMakesItTheActivePane() throws Exception {
        var owner = window(false);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            var panel = new JPanel();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), panel, TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            owner.selectTab(terminal);
            assertThat(pane.dim()).as("the inactive pane").isEqualTo(DIM);
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1));
            assertThat(owner.selectedTab()).as("the pressed pane's item").isSameAs(view);
            assertThat(pane.dim()).as("its pane is the active one").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
        });
    }

    @Test void zeroTurnsTheDimOffAndAnotherAmountTakesEffectAtOnce() throws Exception {
        var owner = window(true);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            owner.selectTab(left);
            boolean mac = System.getProperty("os.name").startsWith("Mac");
            owner.applyConfiguration(dimmedBy(0f), mac);
            assertThat(dim(right)).as("terminal.dim_inactive_panes = 0").isZero();
            assertThat(pane.dim()).isZero();
            owner.applyConfiguration(dimmedBy(0.5f), mac);
            assertThat(dim(right)).isEqualTo(0.5f);
            assertThat(pane.dim()).isEqualTo(0.5f);
            assertThat(dim(left)).as("the active pane").isZero();
        });
    }
}
```

Give it its ledger row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a")
import ledger
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(W + "PaneDimTest.java", "app.workspace", "PaneDimTest", W + "PaneDimTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8a Task 5; the focused pane's dim")
PY
```

`WorkspaceTabsTest.dimmingFollowsTheActiveGroup` and `ConfigurationControllerTest`'s dimming checks stay as they are:
the terminals' rule does not change (Rulings 13).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.PaneDimTest'`
Expected: FAIL to compile: `GroupView.dim()` does not exist.

- [ ] **Step 3: Dim the inactive panes of a split tab**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a")
from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "GroupView.java", [
    ("import java.awt.BorderLayout;\n", "import java.awt.BorderLayout;\nimport java.awt.Color;\nimport java.awt.Graphics;\nimport java.awt.Graphics2D;\n", 1),
    (""" * (workspace tabs spec 2.3). The code calls a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
""", """ * (workspace tabs spec 2.3). It dims while its tab holds two or more panes and it is not the active one (spec 5). The
 * code calls a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
""", 1),
    ("    private Tab tab;\n    private boolean closed;\n",
     "    private Tab tab;\n    private float dim;\n    private Color over;\n    private boolean closed;\n", 1),
    ("    /** The pane left the layout: it keeps no item. */\n", """    /**
     * Dims the pane (tabs and panes spec 5; workspace plan 8a Rulings 12): {@code dimmed} while its tab holds two or more
     * panes and it is not the active one. A terminal dims through its own view, which already dims every terminal but
     * its window's active item (Rulings 13), so it takes no overlay; any other item dims under an overlay of
     * {@code colour} at {@code amount}, the configured {@code terminal.dim_inactive_panes}. Nothing animates.
     *
     * @param dimmed whether the pane dims
     * @param amount how much, 0 to 1
     * @param colour the overlay's colour, the workspace's surface; null while the pane shows clear
     */
    void dim(boolean dimmed, float amount, Color colour) {
        float next = dimmed && tab != null && !(tab.content() instanceof TerminalContent) ? amount : 0f;
        if (next == dim && Objects.equals(colour, over)) return;
        dim = next;
        over = colour;
        repaint();
    }

    /** The overlay's amount over the pane's item; 0 while it shows clear, and always for a terminal. */
    float dim() { return dim; }

    /** While the overlay shows, the item's own repaints paint it too. */
    @Override protected boolean isPaintingOrigin() { return dim > 0; }

    @Override public void paint(Graphics graphics) {
        super.paint(graphics);
        if (dim <= 0 || over == null) return;
        var g = (Graphics2D) graphics.create();
        try {
            g.setColor(new Color(over.getRed(), over.getGreen(), over.getBlue(), Math.round(255 * dim)));
            g.fillRect(0, 0, getWidth(), getHeight());
        } finally { g.dispose(); }
    }

    /** The pane left the layout: it keeps no item. */
""", 1)])
edit(W + "WorkspaceView.java", [
    ("        for (Group group : layout.groups()) groups.get(group.id()).display(group.selected().map(tabs::get).orElse(null));\n",
     "        for (Group group : layout.groups()) groups.get(group.id()).display(group.selected().map(tabs::get).orElse(null));\n        dimPanes();\n", 1),
    ("""    /** Refreshes the empty state while it shows. */
    void refresh() {
        if (closed) return;
        if (empty != null) empty.refresh();
    }
""", """    /** Refreshes the empty state while it shows. */
    void refresh() {
        if (closed) return;
        if (empty != null) empty.refresh();
    }

    /**
     * Dims every pane but the active one while this tab holds two or more (tabs and panes spec 5; workspace plan 8a
     * Rulings 12), by the configured amount; a maximized pane is the active one and shows clear.
     */
    void dimPanes() {
        if (closed) return;
        boolean several = layout.groups().size() > 1;
        for (Group group : layout.groups()) {
            boolean dimmed = several && !group.id().equals(layout.activeGroup());
            groups.get(group.id()).dim(dimmed, dimmed ? owner.paneDim() : 0f, dimmed ? owner.theme().surface() : null);
        }
    }
""", 1),
    ("        if (empty != null) WorkspaceTheme.apply(empty, theme);\n    }\n",
     "        if (empty != null) WorkspaceTheme.apply(empty, theme);\n        dimPanes();\n    }\n", 1)])
edit(W + "WindowContent.java", [
    ("""    private final java.beans.PropertyChangeListener focusOwners = event -> {
        if (event.getNewValue() instanceof Component focused) tabFocused(tabHolding(focused));
    };
""", """    private final java.beans.PropertyChangeListener focusOwners = event -> {
        if (event.getNewValue() instanceof Component focused) tabFocused(tabHolding(focused));
    };
    /**
     * A left press on a pane's item makes its pane the active one, a view that takes no focus too (tabs and panes spec 5;
     * workspace plan 8a Rulings 21).
     */
    private final AWTEventListener paneClicks = event -> {
        if (event.getID() != MouseEvent.MOUSE_PRESSED || closed() || !(event instanceof MouseEvent press)
            || !SwingUtilities.isLeftMouseButton(press) || !(press.getSource() instanceof Component source)) return;
        Tab tab = tabHolding(source);
        if (tab != null && tab != selectedTab()
            && parentTabs().holding(tab).filter(view -> view == parentTabs().active()).isPresent()) selectTab(tab);
    };
""", 1),
    ("""        focusManager.addPropertyChangeListener("permanentFocusOwner", focusOwners);
        host = registry.addHost(workspaceHost());
""", """        focusManager.addPropertyChangeListener("permanentFocusOwner", focusOwners);
        Toolkit.getDefaultToolkit().addAWTEventListener(paneClicks, AWTEvent.MOUSE_EVENT_MASK);
        host = registry.addHost(workspaceHost());
""", 1),
    ("""                focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
                throw failure;
""", """                focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
                Toolkit.getDefaultToolkit().removeAWTEventListener(paneClicks);
                throw failure;
""", 1),
    ("""        if (closed) return;
        focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
""", """        if (closed) return;
        focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
        Toolkit.getDefaultToolkit().removeAWTEventListener(paneClicks);
""", 1),
    ("    ConfigSnapshot configured() { return workspaceConfiguration.snapshot(); }\n", """    ConfigSnapshot configured() { return workspaceConfiguration.snapshot(); }

    /** How much an inactive pane dims: the configured {@code terminal.dim_inactive_panes} (workspace plan 8a Rulings 12). */
    float paneDim() {
        ConfigSnapshot configured = configured();
        return (configured == null ? ConfigSnapshot.defaults() : configured).terminal().dimInactivePanes();
    }

    /** Every tab's panes take their dim again, after the configured amount changed. */
    void dimPanes() { if (!closed) for (WorkspaceView view : parentTabs.views()) view.dimPanes(); }
""", 1)])
edit(W + "WorkspaceConfiguration.java", [(
    """        if (previous == null || !previous.keybindings().equals(next.keybindings())) owner.setBindings(next.bindings(macOs));
""", """        // A plugin's view in a pane dims under an overlay of the configured amount too (workspace plan 8a Rulings 12).
        if (dimChanged) owner.dimPanes();
        if (previous == null || !previous.keybindings().equals(next.keybindings())) owner.setBindings(next.bindings(macOs));
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS, `WorkspaceTabsTest.dimmingFollowsTheActiveGroup` and `ConfigurationControllerTest` among them. Then
`./gradlew :jasper-app:check`: PASS (the ledger test sees `PaneDimTest`'s row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: every other pane of a split tab dims

While a tab holds two or more panes, every pane but the active one
dims by terminal.dim_inactive_panes: a terminal through its own
view, as it did, a plugin's view under the pane's overlay. An
inactive window still dims every terminal, and a press on a pane's
item makes its pane the active one.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, STATUS and the whole build

The documents say what Tasks 1-5 made true, each edit the smallest that makes its sentence true (AGENTS.md
"Documentation edits"): the README's tab paragraphs, the configuration guide's action ids, examples, dimming and motion
sentences, the architecture's windows, `workspace` and `layout` rows and window chrome, the maintenance recipes for the
strip and the drag, and the plugin documents' placements. The Alacritty design record stays as it is: the strip keeps
its motion (Rulings 20). Then the whole build, and the STATUS entry.

**Files:**
- Modify: `README.md` (`:113`, `:136`), `docs/editor.md` (`:54`), `docs/remote.md` (`:5`, `:33`, `:169`),
  `jasper-app/src/main/java/dev/jasper/app/application/ErrorsTab.java` (class Javadoc `:15-16`),
  `jasper-app/src/main/java/dev/jasper/app/layout/package-info.java` (Task 4's text), `docs/configuration.md` (`:512`,
  `:713`, `:723-724`, `:734-735`,
  `:879-883`), `docs/app-architecture.md` (`:101-103`, `:179`, `:181`, `:231`, `:264`), `docs/app-maintenance.md`
  (`:682-694`, `:696-714`), `docs/plugin-authoring.md` (`:296-297`, `:923-925`), `docs/sdk-architecture.md`
  (`:398-399`), `jasper-sdk/README.md` (`:56-57`, `:66-69`), `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan8a")
from edits import edit, span
span("README.md", "On macOS: Cmd+T new tab (a parent tab), ", "both wrapping at the ends.\n",
    "On macOS: Cmd+T new tab, Cmd+N new terminal window (a child window), Cmd+Shift+W close window, Cmd+D split right, "
    "Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter maximize a pane, Cmd+F find, F2 rename, "
    "Cmd+W close pane, Cmd+Option+W close tab. A window holds tabs, each its own split panes, and shows one at a time; "
    "each pane holds one terminal, file or plugin view. The window's tab strip names each tab by its active pane's "
    "title, from two tabs (under the Enterprise look always, with a \"+\" for New Tab); showing another tab keeps every "
    "shell running, and a tab closes with its last pane. While a tab holds two or more panes, every pane but the active "
    "one dims by `terminal.dim_inactive_panes`, and a window in the background dims its terminals. Move Tab to New "
    "Window (in each window's ⋮, a terminal's context menu, "
    "a tab's menu and the command palette) moves the shown tab into a full window when its window holds two or more, "
    "and a full window's Move to Main Window joins its tabs to the main window's strip. Tabs have hover close controls "
    "and support middle-click close. Drag a tab along the strip to reorder it, onto another window's strip (or the top "
    "of a window showing none, or anywhere over the main window's empty workspace) to move it there, or out of every "
    "window into a full window (not a window's only tab, nor a tab with a dialog open in one of its panes, which moves "
    "along its strip only); Esc cancels. Tabs share the strip's width. Opening and closing tabs animate with a quick "
    "eased settle; tab widths adjust as the row changes, and a dragged or moved tab takes its new place at once. "
    "Closing stops the shell immediately. A blank rename restores the shell/directory title. Cmd+1–9 selects a tab, "
    "and Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab, wrapping at the ends.\n")
edit("README.md", [(
    "On Linux/Windows, Ctrl+1–9 selects a parent tab, Ctrl+PageUp / Ctrl+PageDown selects the previous/next parent tab "
    "and Ctrl+Shift+Tab / Ctrl+Tab the previous/next tab in the active pane. With a single parent tab Ctrl+PageUp",
    "On Linux/Windows, Ctrl+1–9 selects a tab and Ctrl+PageUp / Ctrl+PageDown the previous/next tab, wrapping at the "
    "ends. With a single tab Ctrl+PageUp", 1)])
C = "docs/configuration.md"
edit(C, [
    ("Configured pane dimming persists through focus and theme changes.",
     "While a tab holds two or more panes, every pane but the active one dims by `dim_inactive_panes`, and a window in "
     "the background dims every terminal; the dimming persists through focus and theme changes.", 1),
    ("`new_tab`, `new_tab_in_pane`, `new_terminal_window`, `close_window`, `close_tab`, `close_pane`, `close_parent_tab`, "
     "`split_right`, `split_down`, `next_parent_tab`, `previous_parent_tab`, `select_parent_tab_1`, `next_tab`, "
     "`previous_tab`, `find`,",
     "`new_tab`, `new_terminal_window`, `close_window`, `close_pane`, `close_tab`, `split_right`, `split_down`, "
     "`next_tab`, `previous_tab`, `select_tab_1`, `move_tab_to_new_window`, `find`,", 1),
    ('previous_parent_tab = "cmd+{"\nnext_parent_tab = "cmd+}"\n', 'previous_tab = "cmd+{"\nnext_tab = "cmd+}"\n', 1),
    ('previous_parent_tab = "ctrl+pageup"\nnext_parent_tab = "ctrl+pagedown"\n',
     'previous_tab = "ctrl+pageup"\nnext_tab = "ctrl+pagedown"\n', 1)])
span(C, "Tab motion follows the [Alacritty reference](design/alacritty-tabs.md): opening takes 475 ms and\n",
     "parent tab dragged along it takes its new place at once.\n",
     """Tab motion follows the [Alacritty reference](design/alacritty-tabs.md): opening takes 475 ms and
closing 425 ms. Selection is immediate. A window's tab strip shows and hides at once, with its second tab (under the
Enterprise look it always shows, and its tabs never move); a tab dragged along it, moved between windows or showing
another of its panes takes its new place at once.
""")
A = "docs/app-architecture.md"
edit(A, [
    ("""workspace and closes with its last tab. A child window is a full window, with parent tabs, or a pane window, whose
content has one layout and an `Origin` (layout). `WindowManager` pops tabs, panes and parent tabs out, brings a pane window
back and moves a full window's parent tabs to the main window: `WindowContent.receive` lands each tab through the tab
""", """workspace and closes with its last tab. A child window is a full window, with parent tabs (the code's name for a window's
tabs; each group, a pane, holds one tab, its item). `WindowManager` moves a parent tab into a new full window and a full
window's parent tabs to the main window: `WindowContent.receive` lands each tab through the tab
""", 1),
    ("each a workspace of tab groups (`WorkspaceView`, `GroupView`, `SplitContainer`), terminal tabs and panes, the tab "
     "drag (`TabDrag`, the window manager's one, shared by its windows, for tabs and parent tabs),",
     "each a workspace of groups, its panes, one tab each (`WorkspaceView`, `GroupView`, `SplitContainer`), terminal tabs "
     "and panes, the tab drag (`TabDrag`, the window manager's one, shared by its windows, for parent tabs),", 1),
    ("a window's content (`WindowLayout`: `ParentTabs`, its parent tabs with the shown one and their order of use, or a "
     "`PaneLayout` with its `Origin`), parent tab, tab and group ids, a workspace's tab groups in nested weighted splits",
     "a window's parent tabs (`ParentTabs`, with the shown one and their order of use), parent tab, tab and group ids, a "
     "workspace's groups (its panes, one tab each) in nested weighted splits", 1),
    ("the parent-tab bar, then the shown parent tab's workspace of tab groups, each with its own tab strip (in the "
     "standard look, none while it holds a single tab); a child window has only its title bar, which ends with the "
     "window's ⋮, above its parent tabs, or above the one workspace of a pane window, which has no parent-tab bar.",
     "the parent-tab bar, the window's one tab strip (in the standard look, none while the window holds a single parent "
     "tab), then the shown parent tab's workspace of groups, its panes; a child window has only its title bar, which "
     "ends with the window's ⋮, above its parent tabs.", 1),
    ("`TabStrip` (one per tab group and one for the parent-tab bar: `WindowTabs`, or the content-sized `ClassicTabs`)",
     "`TabStrip` (the parent-tab bar's: `WindowTabs`, or the content-sized `ClassicTabs`)", 1)])
M = "docs/app-maintenance.md"
span(M, "Workspace tabs use centered text and equal-width cells; a pane holding a single tab hides\n",
     "bounded tooltips. The fixed list/add controls and scrolling/underline motion are retired.\n",
     """Workspace tabs use centered text and equal-width cells in the window's one strip, the parent-tab bar (`ParentTabBar`
over the look's `TabStrip`); a group (`GroupView`), a pane, shows its one tab and no strip. The strip shows from two
parent tabs under the equal tabs and always under the classic tabs (`WorkspaceChrome.showsTabStrip`, applied by
`ParentTabBar.refresh`), and shows and hides at once, without motion. An entry is its parent tab's
(`ParentTabBar.id`): it keeps its place while the parent tab shows another of its groups, and a parent tab moved in or
out, or dragged along the strip, takes its new place at once. While a parent tab holds two or more groups,
`WorkspaceView.dimPanes` dims every group but the active one by `terminal.dim_inactive_panes` (`WindowContent.paneDim`):
a terminal through its view, which `TerminalPane.setActive` dims as before (every terminal but its window's active
one), any other tab under the group's overlay (`GroupView.dim`).
`WindowTabs` derives selected/inactive surfaces from the workspace palette while
preserving glass alpha. Child repaints start at the strip to reconstruct translucent pixels
once. Width animations interpolate the sampled Alacritty reference; resize/reorder/animation
disable settle, and closed content is released before its empty gap contracts. See the
[approved layout and motion](design/alacritty-tabs.md), including reproducible headless previews.
The toolbar and shortcuts own new-tab actions; close is hover-only and titles retain literal,
bounded tooltips. The fixed list/add controls and scrolling/underline motion are retired.
""")
span(M, "Tab drags use plain mouse events too (`TabDrag`), never AWT drag and drop. A strip's left press hands the drag its tab\n",
     "(`TabDragTestSupport`).\n",
     """Tab drags use plain mouse events too (`TabDrag`), never AWT drag and drop. A left press on a strip entry hands the drag
its whole parent tab (`ParentTabBar.pressed`); past the kit's `TAB_REORDER_THRESHOLD` it follows the cursor in screen
coordinates over the windows the `WindowManager` lists, most recently active first, with `TabDragLabel` beside the
cursor and the landing on the target window's `DockDropOverlay`. A parent tab's place in a strip is
`WorkspaceDropResolver.place`: change the rule in the resolver and its tests first. A drop along its own strip is
`WindowContent.reorderParentTab`; one into another window goes through `WindowManager.moveParentTab` (a window whose
strip hides takes it over the band at its top, the main window's empty workspace anywhere over it), and a tear-out
through `WindowManager.moveTabToNewWindow`, a full window. A parent tab drops on strips alone; a window's only parent
tab has no tear-out target, and a parent tab holding a pinned tab or one being closed takes targets along its own strip
only. A release over another window of Jasper's (`TabDrag.otherWindows`) drops nothing, and on macOS a window in
full screen on the display of the drag's own window, other than that window, takes no drop (`TabDrag.dropFrame`). Esc,
a plain move or a press anywhere (a lost release), the source window losing the focus, and the tab its entry shows
closing, leaving its parent tab or giving way to another of its groups end a drag. `TabDragTest` and
`TabDragWindowsTest` drive it headlessly over stand-in frames on a pretend screen (`TabDragTestSupport`).
""")
edit("docs/plugin-authoring.md", [
    ("a split opens a terminal tab in a new tab group beside the pane's\ngroup. Handles keep",
     "a split opens a terminal tab in a new pane beside the pane.\nHandles keep", 1),
    ("opens\nit in the active group (`TabPlacement.ACTIVE_GROUP`), in a new group beside it (`SPLIT_RIGHT`, `SPLIT_DOWN`) "
     "or in a\nnew child window (`NEW_WINDOW`).",
     "opens\nit in a new tab of the window (`TabPlacement.ACTIVE_GROUP`), in a new pane beside the focused one "
     "(`SPLIT_RIGHT`,\n`SPLIT_DOWN`) or in a new child window (`NEW_WINDOW`).", 1)])
edit("docs/sdk-architecture.md", [(
    "opens the tab in the active\ngroup of the window's shown parent tab, a new group beside it or a new child window.",
    "opens the tab in a new tab\nof the window, a new pane beside the focused one or a new child window.", 1)])
edit("jasper-sdk/README.md", [
    ("`ACTIVE_GROUP` (the three-argument form),\n`SPLIT_RIGHT` or `SPLIT_DOWN` (a new tab group beside the active one) "
     "or `NEW_WINDOW` (a new child window).",
     "`ACTIVE_GROUP` (the three-argument form: a new\ntab of the window), `SPLIT_RIGHT` or `SPLIT_DOWN` (a new pane "
     "beside the focused one) or `NEW_WINDOW` (a new child window).", 1),
    ("and a split is a terminal\ntab in a new tab group. `WindowHandle.tabs()` lists every tab in workspace order and "
     "`activeTab()` is the active\ngroup's selected tab;",
     "and a split is a terminal\ntab in a new pane. `WindowHandle.tabs()` lists every tab in workspace order and "
     "`activeTab()` is the focused\npane's;", 1),
    ("`Terminals.split` opens a terminal tab beside the pane's group.", "`Terminals.split` opens a terminal tab beside the pane.", 1)])
edit("docs/editor.md", [(
    "| ⌘W / Ctrl+W | Close Tab; a child window closes with its last tab |\n",
    "| ⌘W / Ctrl+W | Close Pane (the file); its tab closes with its last pane, a child window with its last tab |\n", 1)])
edit("docs/remote.md", [("new tab group beside the current one", "new pane beside the current one", 3)])
edit("jasper-app/src/main/java/dev/jasper/app/application/ErrorsTab.java", [(
    "one content tab over this session's incidents, in the main\n * window's active group, opened by",
    "one content tab over this session's incidents, in a new tab\n * of the main window, opened by", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/layout/package-info.java", [(
    """ * operations that return a new
 * {@code Layout}, each group's region of the unit square, and what a window holds (tabs and panes spec 4): its tabs
 * ({@code ParentTabs}). JDK-only: nothing from Swing, AWT or a
 * file format; {@code verifyApplicationArchitecture} enforces it. Immutable values, safe on any thread; whoever holds a
 * layout owns replacing it.
""", """ * operations that return a new {@code Layout}, each group's region of the unit square, and what a window holds: its
 * tabs ({@code ParentTabs}). JDK-only: nothing from Swing, AWT or a file format; {@code verifyApplicationArchitecture}
 * enforces it. Immutable values, safe on any thread; whoever holds a layout owns replacing it.
""", 1)])
PY
git diff --stat README.md docs jasper-sdk/README.md jasper-app/src
```

Expected: eleven files changed (`README.md`, `docs/configuration.md`, `docs/app-architecture.md`,
`docs/app-maintenance.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`,
`docs/editor.md`, `docs/remote.md`, `ErrorsTab.java` and the layout `package-info.java`, Javadoc only). The testkit's sentence in
`jasper-sdk/README.md` ("a window's tabs sit in groups") stays: the testkit keeps its model until plan 8b (Rulings 4).
Then check that no document outside the dated records still names what went:

```bash
git grep -n -i -e 'pop out' -e 'pane window' -e 'bring back' -e 'new tab in pane' -e 'parent_tab' -e 'StripSource' \
  -e 'PaneLayout' -e 'Ctrl+Tab' -- README.md ':(glob)docs/*.md' jasper-sdk/README.md ':!docs/STATUS.md'
```

Expected: no output.

- [ ] **Step 2: Source hygiene**

Run the AGENTS.md check over every source root (no arguments):

```bash
python3 -I - <<'PY'
import pathlib, sys
for name in [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked")
PY
```

Expected: only `checked`.

- [ ] **Step 3: The whole build and its counts**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL (`AppDocumentationTest` checks every link the documents keep; `ConfigTemplateTest` the
configuration guide's table). Count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = ET.parse(report).getroot()
    tests += int(suite.get("tests", 0)); failures += int(suite.get("failures", 0)); errors += int(suite.get("errors", 0)); skipped += int(suite.get("skipped", 0))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Record the line in the STATUS entry.

- [ ] **Step 4: STATUS, the banner, and commit**

Add the entry at the top of `docs/STATUS.md`, below the title and above the entries already there; then replace its
angle-bracketed parts with the facts of the run (the commits, the date, the deviations, the counts):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8a")
from edits import edit
edit("docs/STATUS.md", [("# Jasper — Status and Handoff\n\n", """# Jasper — Status and Handoff

**Workspace tabs, plan 8a: tabs hold panes — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-20-workspace-8a-tabs-hold-panes.md),
[spec](superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md)). Jasper has two levels: a window holds tabs, and
a tab holds panes split in any direction, each holding one item (a terminal, an editor file or a plugin's view). The
code keeps its names: a `ParentTab` is a tab, a `Group` a pane holding at most one tab, the contributions' `Tab` an
item. Each window has one strip, the parent-tab bar (`ParentTabBar`, which takes in `StripSource`), shown from two tabs
under the equal tabs and always under Enterprise (`WorkspaceChrome.showsTabStrip`); no pane has a strip, and the
equal tabs open and close with the motion the pane strips had, an entry keeping its place while its tab shows another
pane and a dragged or moved tab landing at once. Whatever opens without a pane opens as a new tab (`WindowContent.attach`; New Tab,
`newTab(Path)`, the registry's `NEW_TAB` placement, which the SDK's `TabPlacement.ACTIVE_GROUP` maps to, its moves and
Move to Main Window), the main window's empty workspace taking it in its place. Pane windows (`Origin`, `PaneLayout`,
`WindowLayout`, the `pane` role of `workspace.toml`), Pop Out Tab, Pop Out Pane, Bring Back, New Tab in Pane and the
item drag are gone; the tab drag and Move Tab to New Window (`WindowManager.moveTabToNewWindow`, from two tabs) stay,
and `ContentTab.moveToNewWindow()` opens a full window holding the item in a tab of its own. The keys follow the spec's
table: Close Pane ⌘W / Ctrl+Shift+W, Close Tab ⌥⌘W / Ctrl+Alt+Shift+W, Next and Previous Tab ⌘⇧] ⌘⇧[ /
Ctrl+PageDown Ctrl+PageUp, Select Tab 1-9 ⌘1-9 / Ctrl+1-9, Ctrl+Tab free. While a tab holds two or more panes every
pane but the active one dims by `terminal.dim_inactive_panes` (a terminal through today's rule, unchanged, any other
item under `GroupView.dim`'s overlay), and an inactive window dims every terminal, as before. The controller's rulings
of 2026-10-20, which spec 5 records since `2d7233474`, kept the strip's motion and the inactive window's dimming. The
user's decision of 2026-10-20 (the
spec's decision 3: Enterprise always shows the strip, one tab too) supersedes the "Enterprise: one row of tabs" entry
below, which is history and is not edited. Rulings (plan, "Rulings"): (1) the code names stay, their Javadoc saying
tab, pane and item; (2) a group holds at most one tab, and a saved group listing two is a bad entry, its parent tab
dropped alone; (3) whatever opens without a pane opens as a new tab, the main window's empty workspace taking it in its
place; (4) the SDK's names stay and its Javadoc follows the app, the testkit and the contract cases keeping their model
for plan 8b; (5) pane windows go whole; (6) `ContentTab.moveToNewWindow()` opens a full window holding the item, and
Move Tab to New Window, like the tear-out, needs two tabs in its window; (7) the ⋮ menu lists Move Tab to New Window in
every window and Move to Main Window in a full window, each enabled while its action is; (8) a strip entry's menu reads
Rename Tab, Move Tab to New Window, Close Tab, then the shown item's commands, Move to Main Window staying in the ⋮
and the context menu; (9) `ParentTabBar`
takes in `StripSource`; (10) the strip shows from two tabs under the equal tabs and always under the classic tabs;
(11) the classic strip's "+" is New Tab and the equal tabs have none; (12) a pane dims while its tab holds two or more
panes and it is not the active one, by `terminal.dim_inactive_panes`; (13) an inactive window keeps dimming its
terminals, as today, with the focused-pane dim on top; (14)
the defaults of spec 10, Ctrl+Tab and Ctrl+Shift+Tab free; (15) Close Pane closes the focused pane's item after its
guard, Close Tab the shown tab as one gesture after its guards; (16) Next and Previous Tab run before the focus owner's
own bindings; (17) the File, Tab and context menus read as spec 10 names them; (18) the item drag goes whole; (19)
plan 7c's hidden-strip band and empty-workspace landing stand; (20) the window's strip keeps the open and close
motion where the look has it, an entry kept by its tab, a move, a reorder and a drag landing at once; (21) a left
press on a pane's item makes its pane the active one, a view that takes no focus too. Spec issues,
both ruled by the controller as the plan reads them: (1) spec 6 reads two ways on a window's only tab and Move Tab to
New Window, and Move Tab to New Window needs two tabs in its window; (2) spec 15 puts a dimming setting out of scope,
and `terminal.dim_inactive_panes` stays as the amount. Deviations: <none, or each with its reason>.
Left for plans 8b and 8c: the SDK's `TabPlacement.ACTIVE_GROUP` and the testkit's one-level model (8b), ⌥⌘T and
Ctrl+Alt+Shift+T free for Move Pane to New Tab (8b), and the tab drag's machinery with `WorkspaceDropResolver`'s
areas, drops and edges for the pane drag (8c). Plan 6a's re-plan follows plans 8a-8c. `./gradlew check --continue`: **<counts>**. GUI checks for the user:
- Cmd+T opens a tab in the window's strip, in both looks; the standard look shows the strip from two tabs, Enterprise
  always, its "+" opening a tab; no pane shows a strip, and a split tab shows one entry;
- in the standard look a third tab opens and a tab closes with the strip's quick eased settle; a split, a click into
  another pane and closing a pane leave the entries in place; with Enable animations off nothing moves;
- a tab of two panes: the inactive pane dims and the focused one is clear; a click into the other pane, Cmd+Option+arrow
  and a tab switch move the dim at once; an Editor file as the inactive pane dims too, and a click on it makes its pane
  the active one; a tab of one pane is clear; a window in the background dims its terminals, as before;
- Cmd+W closes the focused pane (an Editor file with unsaved edits asks first) and the tab with its last pane;
  Cmd+Option+W closes the whole tab after its guards; Cmd+Shift+] / [ and Cmd+1-9 switch tabs; on Linux and Windows
  Ctrl+PageDown and Ctrl+PageUp do, and Ctrl+Tab reaches the terminal;
- the ⋮ menu with two tabs: Move Tab to New Window opens a full window holding the tab with its panes, every shell
  running; with one tab it is disabled; a full window's Move to Main Window joins its tabs to the main window's strip,
  and a strip entry's menu has no Move to Main Window;
- a tab dragged along the strip, onto another window's strip, onto the top band of a window with one tab, over the
  main window's empty workspace and out of every window, landing at once with no slide; dragged over a pane it lands
  nothing, and a press on a pane starts no drag;
- an Editor file opened from Open File… and a `jasper` CLI request open as new tabs, the main window's empty workspace
  taking the first.

Not merged, not pushed.

""", 1)])
PY
```

Set this plan's status banner (line 3) to "**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`."
and list any deviation from the plan's text there.

```bash
git add -A README.md docs jasper-sdk/README.md jasper-app/src
git commit -F - <<'EOF'
Tabs and panes: documentation and status

The README says how a window's tabs hold panes, what dims, the new
keys, Move Tab to New Window and the tab drag; configuration,
architecture, maintenance and the plugin documents follow; STATUS
records plan 8a with its rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

Checked against the spec with this plan's text in hand:

- **Spec coverage.** Sections 2 and 3 (the decisions; tab, pane, item): Rulings 1-3, Tasks 2 and 4. Section 4 (the
  model): one item per pane and the empty workspace (Task 4), pane windows, Pop Out Tab, Pop Out Pane and Bring Back
  (Task 2), the item tear-out (Task 1). Section 5 (one strip, from two tabs, always under Enterprise; entries, "+" and
  tooltips; the dim; the motion): Tasks 3, 4 and 5. Section 6 (moving tabs, the ⋮ menu): Tasks 1 and 2, Rulings 6, 7 and 19.
  Section 10 (commands and keys): Task 3. Section 12 (`workspace.toml`): Tasks 2 and 4, the 6a impact. Section 13
  (edge cases): a request from outside and the content tabs' moves (Task 4, Review Focus 1), a split clearing a
  maximize (`Layout.split` already does), the Enterprise mocks (Task 4); hovering a hidden tab during a pane drag is
  8c's. Section 14's first plan: Tasks 1-6. Section 16 (tests): every task's tests. Sections 7-9 and 11 are plans 8b
  and 8c's (see "Left for plans 8b and 8c").
- **Type consistency.** `newTab(Path)` (Task 4) is what Tasks 4 and 5 call; `tabBar()` returns the bar's strip from
  Task 4 on; `GroupView(GroupId)`, `tab()`, `display(Tab)` (Task 4) are what Task 5's `dim` reads; `paneDim()` and
  `dimPanes()` (Task 5) are named alike in `WorkspaceView` and `WindowContent`; `moveTabToNewWindow` (Task 2) is the
  drag's tear-out (Task 1's `TabDrag` calls it under that name once Task 2 renames it).
- **Review Focus.** Each line names its test and its task.
