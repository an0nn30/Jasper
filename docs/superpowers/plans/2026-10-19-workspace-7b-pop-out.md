# Workspace tabs 7b: Pop Out, Bring Back and the window menu Implementation Plan

**Status: executed on 2026-10-20** (`e80f2c322..66be37837`; plan 6b's re-planned document lies between); see
`docs/STATUS.md`. Written against `claude/workspace-tabs` at `bba5c472e` with plan 7a's text applied (see Workspace).
Deviations from the plan's text: 7b's `openParentTab(Tab, ParentTabId, int)` was refreshed before execution to keep 7a's
executed theme and glass and its bar-order insert; the origin names a pane, so a pane whose sibling was a subtree comes
back beside one group of it, and `Layout.origin` clamps its share into [0.01, 0.99]; `OptionalAssert.doesNotContain` does
not exist, so `TabDragWindowsTest` uses `.isPresent().get().isNotEqualTo(pane)`; `WindowManager.leaving` uses
`registry.movable`; one `ParentTabsView.workspace(id)` gives every new workspace, a renewed one too, the window's theme
and glass; `landing` is restored rather than nulled; `holderOf` skips closing windows; a whole parent tab keeps its
maximize when it moves; `rearrange` keeps held tabs the plan lacks and does nothing when the plan names none of the
workspace's tabs; `WindowMenuButton` calls `setText(null)`; `WindowPanelsTest` hides the toolbar, because the pinned ⋮
widens the main window's minimum by about 56 px; Pop Out Tab's enablement adds `registry.movable(tab)`
(`mayPopOutTab(Tab)`) on every surface, and the `may*` checks are false without a manager.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tab, a pane or a parent tab leaves its window, and a pane window comes back: Pop Out Tab and Pop Out Pane
open a pane window (one layout, no parent tabs, an origin), Pop Out Parent Tab opens a full window, Bring Back returns a
pane window's whole layout as a nested split beside the pane it came from, and Move to Main Window joins a full window's
parent tabs to the main window's bar. Every window has a ⋮ offering these for its kind, and a tab's menu, a terminal's
context menu, the parent-tab bar's menu and the palette offer them too. Every move keeps each tab's session and reports
one `TAB_MOVED` per tab.

**Architecture:** The pure model gains `layout.Origin` (the parent tab, the pane beside, the side and the share),
`Layout.origin` (where a pane sits) and `Layout.graft` (Bring Back's layout), and 7a's `PaneLayout` carries its origin. A
pane window is a child window whose `WindowContent` the window manager gives an origin while it is empty. Every move
between windows goes through the tab registry one tab at a time, so plan 4a's failure handling and the one `TAB_MOVED`
per tab hold. The receiving window lands the tabs in a chosen parent tab (`WindowContent.receive`) and then gives that
parent tab its planned layout at once (`WorkspaceView.rearrange`). `WindowManager` holds the operations; the commands, the
menus and the ⋮ (`WindowMenuButton`) call them.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md)
(sections 2, 4, 5, 6, 8's pop-out row, 10, 11, 12's second plan and 14), which amends and wins over
[`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 6.2, 6.3, 6.5, 11, 12 and 15 as amended). It builds on
[plan 7a](2026-10-19-workspace-7a-parent-tabs.md). Plan 7c (`2026-10-19-workspace-7c-parent-tab-drag.md`) builds on
both, then plans 6a and 6b are re-planned.

**Hand-offs absorbed:** 7a's "Left for plan 7b":
- `PaneLayout`'s origin (Task 1);
- the pane windows that hold it (Task 2);
- Pop Out Parent Tab and Move to Main Window in `WindowContent.parentTabMenu` (Task 4).

It also absorbs 7a's Rulings 9 (7b and 7c move tabs between windows through the registry, one `TAB_MOVED` each; Tasks
2 and 3) and spec 8's pop-out row (`pop_out_tab`, `pop_out_pane`, `pop_out_parent_tab`, `bring_back`,
`move_to_main_window`; Task 4).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 7a's commits. The plan was written at `bba5c472e` ("Editor: the New Editor Window
button wears the Editor's file glyph") with plan 7a's text applied. Since then three commits landed, up to `8e129ff34`:
- the Vault manager as a modeless dialog (`144ff89f1`, `plugins/vault/**` and the app's Vault tests, which 7b does not
  touch);
- the main-sync fix wave (`fa022dd87`: `Contributions.Op`, `WindowManager.routePanel`, `WindowContributions`,
  `WindowContent.mountChild`'s non-macOS classic branch, `setActive` and `close()`), where no anchor below lands;
- plan 7a's text itself (`8e129ff34`).

Every anchor below was matched at its stated count, and every edit script of Tasks 1-6 replayed in order, on
`git archive` copies of `bba5c472e` and of `8e129ff34`, each after plan 7a's file creations and edit scripts. Line
numbers are `bba5c472e`'s, or "7a's" where the text is plan 7a's. Nothing was compiled or run.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan, or plan 7a, wrote, and quote it as they leave it.

## Spec issues

1. **Spec 4's origin records "the window and parent tab"; this plan records the parent tab only.** Spec 11 says the origin
   "follows the parent tab by id", so Bring Back looks for the parent tab in every main and full window when it runs
   (Rulings 1, 5). A recorded window id would add nothing: it never decides where the panes go, and plan 6a gives every
   restored window a new id. If the user wants Bring Back to prefer the recorded window, `Origin` gains a `UUID window`
   and `WindowManager.bringBack` looks there first; nothing else changes.
2. **The Workstation mocks predate the ⋮.** Spec 6 puts the main window's ⋮ at the trailing end of its toolbar. Under
   Enterprise that is after "Settings…", where the mocks end the row with "Settings…". The ⋮ (a 22 px classic icon button
   8 px after "Settings…") moves "Settings…" and the search field 30 px left. `EnterpriseWorkstationMockTest` and
   `ClassicHeaderTest` measure them from the ⋮ (Rulings 20), as plan 7a's Rulings 18 did for the parent-tab bar.
3. **Resolved by the controller: the overflow's glyph and place.** The standard toolbar's overflow ("More actions")
   showed the same IntelliJ `moreVertical` glyph (`WindowToolbar.java:24`) as the window's ⋮, so on a narrow window two
   ⋮ would have sat side by side. The controller ruled that the overflow shows a horizontal ellipsis: `IconRegistry`'s
   built-in `"more"` (`jasper-ui/.../icons/IconRegistry.java:406`, three dots in a row), which `ToolWindowHeader` already
   shows through `AppIcons.semantic("more")`. The overflow also sits before the ⋮, so every toolbar ends with the ⋮
   (the controller's ruling; Rulings 16, Task 5).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (parent tabs spec 8): `move_tab_to_new_window` becomes `pop_out_tab` and `move_tab_to_main_window` becomes
  `bring_back`, with no alias. Saved bindings for the old ids are reported as unknown keys, like any unknown key. No
  shim, no migration, no "was removed" text.
- No motion (AGENTS.md, parent tabs spec 5): pane windows, full windows and panes appear in place at once; a tab that
  arrives takes its strip place without the open motion.
- Sessions and content are never touched: every move between windows goes through `TabRegistry.move`, which detaches
  and attaches the tab's component and never restarts a session (spec 2.3, plan 4a).
- The SDK does not change: no file under `jasper-sdk`, `jasper-sdk-testkit` or `plugins` changes; SDK 0.20.0 stays.
  Plugins see a pane window as a `WindowHandle` that is not the main window (parent tabs spec 10).
- `dev.jasper.app.layout` stays JDK-only and names no other Jasper package. No package gains an edge: `workspace`
  already names `layout`, `contributions`, `commands` and `platform`.
- The theme-migration ledger: every new Java file under `jasper-app/src` that matches `ThemeMigrationCoverageTest`'s
  `CANDIDATE` pattern gets a `docs/design/theme-engine/migration.csv` row in its task's commit, through `ledger.py`, as
  `nonvisual-helper,none,none` (plan 4a Rulings 21). The `layout` files and `OriginTest` match nothing and get none.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`). Windows are the window manager's stand-in frames (`MovesFixture`, as in
  `WindowManagerTest`), and on a pretend screen where a place matters (`TabDragWindowsTest`).

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan7b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan7b
cp build/plan7a/edits.py build/plan7a/ledger.py build/plan7b/ 2>/dev/null || cp build/plan5/edits.py build/plan5/ledger.py build/plan7b/
ls build/plan7b/edits.py build/plan7b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add(path, owner, surface, evidence, disposition)`. Every script below runs as
`python3 -I - <<'PY'` from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan7b")`. Java
text inside a script is in triple-quoted strings.

## Review Focus

1. **A pane or parent tab holding a tab that is pinned (a modal prompt open) or being closed.** Nothing moves, the pane or
   parent tab stays whole, no window opens, and Bring Back leaves the pane window as it is. Tests:
   - Task 2: `PopOutTest.aPaneHoldingAPinnedTabStaysWhole`;
   - Task 3: `BringBackTest.aPinnedTabKeepsItsParentTabAndItsPaneWindowWhole`.
2. **Bring Back after the main window's only pane popped out.** The panes fill the empty workspace rather than splitting
   beside an empty pane. Task 3: `BringBackTest.bringBackFillsTheEmptyMainWorkspaceItLeft`.
3. **Pop Out Pane of a parent tab's only pane while the window has other parent tabs.** That parent tab closes and the one
   used before it shows; Bring Back then goes beside the active pane of the main window's active parent tab. Task 3:
   `BringBackTest.withItsParentTabGoneThePanesGoBesideTheMainWindowsActivePane`.
4. **A menu opened on a tab that is not the active one.** A tab's menu acts on that tab's pane and parent tab, and the
   bar's menu on its own parent tab, not on the active ones. Task 4:
   `PopOutCommandsTest.aTabsMenuAndTheBarsMenuActOnTheirOwnPaneAndParentTab`.
5. **Where the focus goes.** The focus follows the moved tab into its pane window, and on Bring Back it lands on the pane
   window's active tab in its new place. Tests:
   - Task 2: `PopOutTest.popOutTabTakesOneTabToAPaneWindowFromItsPane`;
   - Task 3: `BringBackTest.bringBackReturnsThePanesNestedBesideTheOriginPane`.

## Verified facts

From the code at `bba5c472e` with plan 7a's text applied (see Workspace).

1. **Moves between windows.** `TabRegistry.move(Tab, UUID)` and `move(Tab, UUID, GroupId, Side)`
   (`contributions/TabRegistry.java:386-413`) refuse a closed or pinned tab, one being closed (`pending`) and a move
   within one window. `transfer` (`:423-479`) does the rest:
   - it detaches the tab from the old host and attaches it to the new one;
   - `windows.movedOut` then closes a child window the move emptied (`WindowManager.movedOut`, `:387-390`);
   - then it selects the tab, which raises its window and focuses it (`select`, `:347-351`).

   The arriving window's `attach` reports the move: `WindowTerminals.tabArrived` publishes one `TabMoved` per tab
   (`workspace/WindowTerminals.java:86-90`). A window opened empty reports nothing when it connects
   (`WindowTerminals`' constructor, `:29-40`).
2. **Placing an arriving tab.** In 7a's text, `WindowContent.attach(Tab, boolean, GroupId, Side, boolean)` places a tab
   the registry moves with `workspace().open(tab)`: the shown parent tab's active pane. A tab placed beside a group goes
   into that group's parent tab (`parentTabs.holding(beside)`). No path lands a tab in a hidden parent tab's active pane
   or in a new parent tab with a given id.
3. **Rendering any layout.** `WorkspaceView.render` (`workspace/WorkspaceView.java:230-257`) keeps a `GroupView` per
   group id, makes one for a new id and closes one whose group went. `GroupView.display` (`GroupView.java:70-83`) mounts
   the tabs a group gained, so `apply` can render any layout over the tabs the view holds. In 7a's text a
   `WorkspaceView`'s parent tab id is fixed at construction (`WorkspaceView(WindowContent, ParentTabId)`).
4. **Moving a parent tab's last tab.** In 7a's text, `ParentTabsView.remove` takes a parent tab away with its last tab,
   except the window's only parent tab, whose emptied layout keeps its id and its group's id (`Layout.remove`,
   `layout/Layout.java:288-304`). `ParentTabsView.openParentTab(Tab)` opens a parent tab with a random id at the end of the
   bar and refuses to sit beside an empty one (`ParentTabs`' invariant).
5. **Where a pane's space goes.** `Layout.remove`'s `without` (`Layout.java:481-503`) gives a removed group's weight to
   the sibling before it, or after it when it was first, and collapses a one-child split. `Split`'s constructor
   (`layout/Split.java:21-40`) flattens a child split of its own orientation, scaling the weights.
   `Layout.neighbour(group, direction)` (`:391-411`) finds the nearest group across an edge, ties going to the first in
   tree order.
6. **Today's move commands and the tear-out.**
   - `WindowManager.moveToNewWindow(Tab)` (`:232`) and `moveToNewWindow(Tab, Rectangle, Point)` (`:243-267`) open an
     empty child window, move the tab, and close the window again when the move left it empty.
   - The registry's `Windows.newWindow` is `this::moveToNewWindow` (`:112-114`); `ContentTab.moveToNewWindow()` reaches it
     through `TabRegistry.moveToNewWindow` (`:359-363`, `plugins/HostedUi.java:522`).
   - Plan 5's tear-out calls the three-argument form (`:90-91`).
   - `openChild(boolean, Path, Rectangle, Point)` (`:324-334`) composes the content, then runs `make` before the frame
     exists and shows.
   - `WindowManager.open` hands every content the shared tab drag (`content.tabDrag(tabDrag)`, `:345`).
7. **The move commands and their menus.**
   - `ActionId.MOVE_TAB_TO_NEW_WINDOW` and `MOVE_TAB_TO_MAIN_WINDOW` (`commands/ActionId.java:16-17`, in 7a's span).
   - `WorkspaceActions` runs them on the selected tab and enables the first while `isMain() || tabCount() > 1`
     (`WorkspaceActions.java:55-56`, `:121-122`).
   - The palette keywords (`WindowCommands.java:54-55`) and the Keymap's "Tabs" category (7a's `ShortcutCatalog` text).
   - The tab menu offers Move to New Window or Move to Main Window (7a's `WindowContent.tabMenu`).
   - The terminal context menu (`WindowChrome.contextMenu`, `:569-584`) lists built-in actions and then contributed
     sections; `TabHost.bind` (`TabHost.java:141-145`) selects the right-clicked tab before showing it.
8. **The toolbars.**
   - The standard toolbar ends with Find and Settings, then `finish()` adds the overflow button
     (`WindowChrome.buildUnifiedToolbar`, `:120-131`).
   - In the trailing layout the overflow, a ⋮ named `toolbarOverflow` (`WindowToolbar.java:24`, `:32-40`), sits at the
     far right. A `JButton` with no overflow entry never gives way (`:318`, and `:140` in the leading layout).
   - A label is applied only to a button whose `keepLabel` is not `false` and that has a `label` (`:199-202`).
   - The classic header's trailing group holds "Settings…" alone, 8 px from the content edge (`ClassicHeader.java:39-57`,
     `:68-73`).
   - `ClassicToolbarButton` with no text is IntelliJ's 22 px icon button (`ClassicToolbarButton.java:52-53`).
9. **A child window's title row.**
   - `WindowContent.mountChild` (`:409-457`) gives a child window the unified `MacTitleBar` (WINDOW style, a centred title,
     no children but the title), IntelliJ Classic's `MacTitleBand` on macOS under a classic title (documented as "no
     children and no text", `platform/MacTitleBand.java:26`), or the system's title bar.
   - On Windows and Linux it is FlatLaf's title pane or, with native decorations or Compact, the system's.
   - Only macOS's screen menu bar sets a child root's `JMenuBar` (`:446`).
   - FlatLaf 3.7's title pane embeds the root's menu bar and moves the title into a horizontal glue in it, with what
     follows the glue at the trailing end (`FlatTitlePane.java:342-352`, `:679-699` in `flatlaf-3.7-sources.jar`).
   - A `MacTitleBar` lays its title out with insets of at least 120 px (`safeInset`, `MacTitleBar.java:225-230`).
   - The child's content puts `area` at `BorderLayout.CENTER` and nothing at `NORTH` (`:347-348`).
10. **The SDK's move.** `ContentTab.moveToNewWindow()` is documented as "Move Tab to New Window: a new child window takes
    the tab" and `moveToMainWindow()` as the end of the main window's active group
    (`jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java:24-38`). A pane window is a child window, so the
    first stays true when Pop Out Tab replaces Move Tab to New Window.
11. **Screen coordinates.** `SwingUtilities.convertPointToScreen` walks a component's parents and asks only a `Window` for
    its place on screen. `TabDragTestSupport`'s stand-in frames on a pretend screen rely on that (`TabDragTestSupport.java:33-63`).

## Rulings

Record them in `docs/STATUS.md` (Task 6). "What — why".

1. **`Origin(ParentTabId parentTab, GroupId pane, Side side, double share)` in `layout`, and `PaneLayout(Layout,
   Origin)`; the window is the one holding the parent tab when Bring Back runs** — spec 4 and 11 (Spec issues 1); parent
   tab ids are what plan 6a saves.
2. **Where a pane sits (`Layout.origin`):**
   - the group beside it is the one across its edge, within the sibling its space goes to when it leaves: the sibling
     before it, or after it when it is first (fact 5);
   - the side is the side of that group it is on;
   - the share is its weight over the two weights;
   - a layout's only group records itself, on the right, at half.

   Reason: grafting at that place restores a split whose sibling is a pane, weights included (Task 1's round-trip test).
3. **Pop Out Tab's origin.** A pane's only tab takes its pane along, so the origin is the pane's (Rulings 2). A tab whose
   pane keeps other tabs records that pane, the right side and a half — Split Right's place for a new pane, and spec 5
   gives a single item no side of its own.
4. **What pops out of a pane window keeps that window's origin** — a pane window has no parent tab (spec 4), its panes
   came from the origin's place, and origins then only ever name parent tabs.
5. **Bring Back's target:**
   - the origin's parent tab, in whichever main or full window holds it (spec 11), beside the origin pane when that
     parent tab still has it, else beside its active pane;
   - when no window holds the parent tab, beside the active pane of the main window's active parent tab (spec 5);
   - always on the recorded side, with the recorded share.

   The maximize there ends (spec 11), the parent tab shows, and the pane window's active tab is selected, focused and
   raised. Reason: spec 5 and 11 name every case.
6. **Every move between windows goes through the tab registry one tab at a time:**
   - the receiving window lands the tabs in a parent tab chosen for the move (`WindowContent.receive`'s landing);
   - that parent tab then takes the planned layout at once (`WorkspaceView.rearrange`).

   Reason: the registry is the one path that moves a tab between windows, reports one `TAB_MOVED` and keeps the session
   (fact 1), and plan 4a's failure handling stays whole.
7. **A pane, a parent tab or a pane window moves whole or not at all.** While any of its tabs is pinned or being closed
   nothing moves (`TabRegistry.movable`), and Pop Out Pane, Pop Out Parent Tab, Bring Back and Move to Main Window are
   disabled wherever they are offered: the window menu, the tab menu, the bar's menu and the palette (the controller's
   ruling). Pop Out Tab keeps today's rule for its one tab. Reason: a pane or a parent tab never ends up split across
   windows.
8. **A child window's only tab, only pane and (in a full window) only parent tab stay** — that window already is its own
   (spec 5 for parent tabs; plan 4a's rule for tabs). The main window's may all pop out, leaving its empty workspace.
9. **A pane window is a child window whose content has an origin:**
   - `WindowRole` stays `MAIN` or `CHILD`, and the window manager sets the origin while the window is empty, before its
     frame exists;
   - `windowLayout()` is a `PaneLayout` from its first tab on;
   - it has no parent-tab bar in either look;
   - New Tab stacks a terminal in its active pane, and Close Parent Tab is disabled.

   Reason: spec 4 and 7 (no parent tabs); the commands keep working.
10. **A parent tab keeps its id and layout when it moves to another window** (Pop Out Parent Tab, Move to Main Window,
    and plan 7c's drag through `WindowManager.moveParentTab`). A window whose only parent tab moves whole to another
    window takes a new id for its empty workspace (`ParentTabsView.renew`, which `moveParentTab` calls). A pane or a tab
    popping out of that parent tab leaves it its id, so the pane window's origin still names it. Reason: spec 11's
    origins follow the parent tab, and spec 4 keeps ids unique across windows.
11. **Where windows open.**
    - Pop Out Tab and Pop Out Pane open the pane window at the pane's place and size on screen, on the display under the
      pane's middle (spec 5, plan 5's `WindowShell.place`). Pane bounds are known only while the pane shows and has a
      size.
    - Pop Out Parent Tab's full window opens where the platform puts it, as New Terminal Window's does.

    Reason: spec 5 places the pane window only.
12. **Move to Main Window:**
    - the full window's parent tabs join the main window's bar after its active parent tab, in their order;
    - the main window's empty workspace gives way to the first of them;
    - the full window's shown parent tab shows, and the main window comes to the front.

    Reason: spec 5 and its "after its active parent tab".
13. **Commands.** The new commands are `pop_out_tab` "Pop Out Tab", `pop_out_pane` "Pop Out Pane", `pop_out_parent_tab` "Pop
    Out Parent Tab", `bring_back` "Bring Back" and `move_to_main_window` "Move to Main Window", none with a default key.
    - The Keymap lists Pop Out Tab and Pop Out Parent Tab under Tabs, Pop Out Pane under Panes, and Bring Back and Move to
      Main Window under Application, with New Terminal Window and Close Window.
    - The menu bar does not take them.

    Reason: spec 8 (no keys) and 5 (the names); the Keymap groups commands by what they act on.
14. **The window menu's entries by kind (spec 6):**
    - main window: Pop Out Pane, Pop Out Parent Tab;
    - full window: Pop Out Pane, Pop Out Parent Tab while it has two parent tabs, Move to Main Window;
    - pane window: Bring Back, then Pop Out Pane while it has two panes.

    An entry the spec makes conditional is left out; the others show disabled while their action is (a full window's only
    pane, the empty workspace). They act on the active pane and parent tab. Reason: spec 6's list.
15. **The same commands elsewhere (spec 6):**
    - a tab's menu: Rename Tab, Pop Out Tab, the window menu's entries for that tab's pane and parent tab, a rule, then
      Close Tab, Close Others and Close Pane;
    - a terminal's context menu: its items, a rule, Pop Out Tab, the window menu's entries;
    - the parent-tab bar's menu: Pop Out Parent Tab and Move to Main Window where the window menu has them, a rule, Close
      Parent Tab;
    - the palette: every one, under its id.

    Reason: a menu acts on what it was opened on (Review Focus 4).
16. **The main window's ⋮.** It is the toolbar's last control: after Settings in the standard toolbar, styled as its
    overflow button, and after "Settings…" in the classic row, as a 22 px classic icon button.
    - It is named `windowMenu`, with the tooltip and accessible name "View Options", and shows no label.
    - It is pinned (`WindowToolbar.pin`): it never moves into the overflow menu, and the overflow, a horizontal
      ellipsis, sits just before it, so the row always ends with the ⋮ (Spec issues 3, the controller's ruling).
    - It hides with the toolbar ("Toolbar: Hidden", Compact). Its commands then stay in the palette and, for a terminal,
      in its context menu. A pane's strip and the parent-tab bar offer them only while they show, and a plugin's view has
      no context menu of Jasper's.

    Reason: spec 6 ("at the trailing end of its existing toolbar"); IntelliJ's tool window Options button.
17. **A child window's ⋮, its compact toolbar.** It sits at the trailing end of the title row Jasper draws:
    - macOS, the unified bar (the standard look): `MacTitleBar.setTrailing`;
    - macOS under the classic title (Enterprise): `MacTitleBand.setTrailing`;
    - Windows and Linux: a menu bar of its own, a horizontal glue then the ⋮, which FlatLaf's title pane embeds with the
      title in the glue; under the system's title bar (native decorations, Compact, Linux's classic title) that menu bar
      is a row of the window;
    - macOS under the system's title bar (Compact, a look with a native title): a row at the top of the window, the ⋮ at
      its trailing end.

    It shows under Compact too, unlike the main window's toolbar: a pane window's ⋮ is its only Bring Back control
    besides the palette and a terminal's context menu. A pane window has no parent-tab bar, its only tab shows no strip
    in the standard look, and a plugin's view has no context menu of Jasper's.

    Reason: spec 6 ("at the trailing end of their title row"); fact 9 says where Swing can draw in each title row.
18. **The SDK does not change.**
    - `ContentTab.moveToNewWindow()` pops the tab out as Pop Out Tab does: a pane window with its origin.
    - `moveToMainWindow()` keeps its meaning: the main window's shown parent tab's active pane.

    Reason: spec 10 (pane windows are windows to plugins; no parent tabs in the SDK), fact 10.
19. **Plan 5's tear-out stays a full child window** (`WindowManager.moveToNewWindow(Tab, Rectangle, Point)`, kept for the
    drag alone) — the controller's ruling; plan 7c replaces it.
20. **Tests that read the main toolbar take the ⋮ into account:**
    - `EnterpriseWorkstationMockTest` and `ClassicHeaderTest` measure "Settings…" and the field 30 px left of where the
      mocks have them (Spec issues 2);
    - the standard toolbar's tests (`MockUiTest`, `WindowChromeTest`, `WindowChromeContributionsTest`) count one more
      button, labelled "View Options";
    - `WindowToolbarTest` reads the overflow's horizontal ellipsis and a pinned control after it.

    Reason: spec 6 adds a control the mocks and the earlier tests predate.
21. **New files take ledger rows `nonvisual-helper,none,none` with "New in workspace-tabs plan 7b Task N"** — plan 4a
    Rulings 21's convention.

## File structure

- `dev.jasper.app.layout`:
  - `Origin` (new), `PaneLayout` and `Layout` (`origin`, `graft`) (Task 1);
  - `package-info` (Task 1, 7a's sentence).
- `dev.jasper.app.contributions`: `TabRegistry` (`movable`, Javadoc) and `Tab` (Javadoc) (Task 2).
- `dev.jasper.app.workspace`:
  - Task 2: `WindowManager` (pop-outs, `openChild`), `WindowContent` (pane windows, origins), `ParentTabBar`,
    `WorkspaceActions` and `TabDrag` (a comment);
  - Task 3: `WorkspaceView` (`rearrange`), `ParentTabsView` (`openParentTab` with an id, `land`, `renew`),
    `WindowContent` (`receive`, the move checks) and `WindowManager` (parent-tab moves, Bring Back, Move to Main
    Window);
  - Task 4: `WorkspaceActions`, `WindowCommands`, `WindowChrome` (`contextMenu`), `WindowContent` (commands and menus)
    and `WindowManager` (the window's link);
  - Task 5: `WindowMenuButton` (new), `WindowToolbar` (pinned controls, the horizontal ellipsis), `WindowChrome`,
    `ClassicHeader` and `WindowContent` (`mountChild`).
- `dev.jasper.app.commands`: `ActionId` (Task 4). `dev.jasper.app.shortcuthelp`: `ShortcutCatalog` (Task 4).
- `dev.jasper.app.platform`: `MacTitleBar` and `MacTitleBand` (`setTrailing`) (Task 5).
- Tests:
  - Task 1: new `OriginTest`; changed `ParentTabsTest` (7a's).
  - Task 2: new `MovesFixture` and `PopOutTest`; changed `WindowManagerTest` and `TabDragWindowsTest`.
  - Task 3: new `BringBackTest`.
  - Task 4: new `PopOutCommandsTest`; changed `KeymapDefaultsTest` (7a's), `KeyBindingsTest`, `ConfigLoaderTest`,
    `TabMenuTest`,
    `WindowManagerTest`, `TerminalContextMenuTest`, `ParentTabBarTest` (7a's), `ShortcutHelpTest` and
    `ShortcutEditorIntegrationTest`.
  - Task 5: new `WindowMenuButtonTest`; changed `WindowToolbarTest`, `MacTitleBandTest`, `MockUiTest`,
    `WindowChromeTest`,
    `WindowChromeContributionsTest`, `ClassicHeaderTest`, `EnterpriseWorkstationMockTest`, `WindowShellTest` and
    `ClassicWindowsTitleTest`.
- Documents (Task 6): `README.md`, `docs/app-architecture.md`, `docs/STATUS.md`. The ledger changes in Tasks 2-5, and
  Task 5 rewords `WindowToolbar`'s row.

## Before you start

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for parent tabs"                      # plan 7a's Task 6 commit
grep -c 'public record PaneLayout(Layout layout) implements WindowLayout {' jasper-app/src/main/java/dev/jasper/app/layout/PaneLayout.java       # 1
grep -c 'private void attach(Tab tab, boolean opened, GroupId beside, Side side, boolean parentTab) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'public Optional<ChildWindow> moveToNewWindow(Tab tab) { return moveToNewWindow(tab, null, null); }' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java  # 1
grep -c 'JPopupMenu parentTabMenu(Tab tab) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above them. Create the two helper scripts (Global Constraints).

---

### Task 1: The origin and Bring Back's layout

Spec 4 and 5: a pane window keeps an origin (the parent tab, the pane its panes sat beside, the side and the share), and
Bring Back puts the pane window's whole layout back there as a nested split. Pure values and operations beside `Layout`
(Rulings 1, 2); nothing uses them yet.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/layout/Origin.java`
- Replace: `jasper-app/src/main/java/dev/jasper/app/layout/PaneLayout.java` (7a's)
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java` (after `unmaximize()`, `:377-379`),
  `package-info.java` (7a's sentence)
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/OriginTest.java` (new), `ParentTabsTest.java` (7a's, two lines)

**Interfaces:**
- Consumes:
  - plan 2's `Layout`: `of`, `empty`, `split`, `remove`, `toggleMaximize`, `group`, `groupOf`, `neighbour`, `activeGroup`,
    `activeTab`, `maximized`, `root`;
  - `Split`, `Child`, `Group`, `Side`, `Orientation`;
  - 7a's `ParentTabId`, `PaneLayout`, `WindowLayout`.
- Produces:
  - `public record Origin(ParentTabId parentTab, GroupId pane, Side side, double share)`, with the share above 0 and
    below 1;
  - `public record PaneLayout(Layout layout, Origin origin) implements WindowLayout`;
  - `public Origin Layout.origin(ParentTabId parentTab, GroupId group)`;
  - `public Layout Layout.graft(Layout panes, GroupId beside, Side side, double share)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/layout/OriginTest.java`:

```java
package dev.jasper.app.layout;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/** Parent tabs spec 4, 5 and 11: where a pane sits, the origin a pane window keeps, and Bring Back's layout. */
class OriginTest {
    private static final ParentTabId PARENT = ParentTabId.random();
    private static final TabId A = TabId.random(), B = TabId.random(), C = TabId.random(), X = TabId.random(), Y = TabId.random();

    /** A | B | C, taking a half, a quarter and a quarter. */
    private static Layout row() {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), Side.RIGHT);
        return layout.split(C, layout.activeGroup(), Side.RIGHT);
    }

    private static GroupId of(Layout layout, TabId tab) { return layout.groupOf(tab).orElseThrow(); }

    @Test void aPaneSitsBesideTheSiblingItsSpaceGoesToWithItsShareOfTheTwo() {
        Layout layout = row();
        assertThat(((Split) layout.root()).children()).extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
        assertThat(layout.origin(PARENT, of(layout, C))).isEqualTo(new Origin(PARENT, of(layout, B), Side.RIGHT, 0.5));
        Origin middle = layout.origin(PARENT, of(layout, B));
        assertThat(middle.pane()).isEqualTo(of(layout, A));
        assertThat(middle.side()).isEqualTo(Side.RIGHT);
        assertThat(middle.share()).isCloseTo(1.0 / 3, within(1e-12));
        Origin first = layout.origin(PARENT, of(layout, A));
        assertThat(first.pane()).as("the first gives its space to the one after it").isEqualTo(of(layout, B));
        assertThat(first.side()).isEqualTo(Side.LEFT);
        assertThat(first.share()).isCloseTo(2.0 / 3, within(1e-12));
        assertThat(first.parentTab()).isEqualTo(PARENT);
    }

    @Test void aPaneInAColumnSitsAboveOrBelowAndTheOneBesideIsNearestItsMiddle() {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), Side.RIGHT);
        layout = layout.split(C, layout.activeGroup(), Side.DOWN);
        // A | (B over C)
        assertThat(layout.origin(PARENT, of(layout, C))).isEqualTo(new Origin(PARENT, of(layout, B), Side.DOWN, 0.5));
        assertThat(layout.origin(PARENT, of(layout, B))).isEqualTo(new Origin(PARENT, of(layout, C), Side.UP, 0.5));
        assertThat(layout.origin(PARENT, of(layout, A))).as("both touch its edge: the first in tree order")
            .isEqualTo(new Origin(PARENT, of(layout, B), Side.LEFT, 0.5));
    }

    @Test void aLayoutsOnlyPaneSitsOnTheRightOfItself() {
        Layout layout = Layout.of(A);
        assertThat(layout.origin(PARENT, layout.activeGroup())).isEqualTo(new Origin(PARENT, layout.activeGroup(), Side.RIGHT, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> layout.origin(PARENT, GroupId.random())).withMessageContaining("not in the layout");
    }

    @Test void graftingAtTheOriginRestoresTheSplitThePaneLeft() {
        Layout layout = row();
        Origin origin = layout.origin(PARENT, of(layout, C));
        Layout rest = layout.remove(C);
        assertThat(((Split) rest.root()).children()).extracting(Child::weight).containsExactly(0.5, 0.5);
        Layout panes = Layout.of(C);
        Layout back = rest.graft(panes, origin.pane(), origin.side(), origin.share());
        var children = ((Split) back.root()).children();
        assertThat(children).extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
        assertThat(children).extracting(Child::node).containsExactly(rest.group(of(rest, A)).orElseThrow(),
            rest.group(of(rest, B)).orElseThrow(), panes.root());
        assertThat(back.activeGroup()).as("the panes come back active").isEqualTo(panes.activeGroup());
        assertThat(back.activeTab()).contains(C);
    }

    @Test void graftingNestsThePanesOnTheGivenSide() {
        Layout panes = Layout.of(X);
        panes = panes.split(Y, panes.activeGroup(), Side.DOWN);
        Layout layout = Layout.of(A);
        GroupId a = layout.activeGroup();
        Layout right = layout.graft(panes, a, Side.RIGHT, 0.25);
        Split split = (Split) right.root();
        assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(split.children()).extracting(Child::node).containsExactly(layout.root(), panes.root());
        assertThat(split.children()).extracting(Child::weight).containsExactly(0.75, 0.25);
        assertThat(right.activeGroup()).isEqualTo(panes.activeGroup());
        Layout left = layout.graft(panes, a, Side.LEFT, 0.5);
        assertThat(((Split) left.root()).children()).extracting(Child::node).as("leading: the panes first")
            .containsExactly(panes.root(), layout.root());
        Layout below = layout.graft(panes, a, Side.DOWN, 0.5);
        assertThat(((Split) below.root()).children()).as("panes of the split's orientation join it (spec 7.2)")
            .extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
    }

    @Test void graftingEndsAMaximizeAndTheEmptyWorkspaceTakesThePanesWhole() {
        Layout maximized = row();
        maximized = maximized.toggleMaximize(maximized.activeGroup());
        assertThat(maximized.maximized()).isPresent();
        Layout back = maximized.graft(Layout.of(X), of(maximized, A), Side.RIGHT, 0.5);
        assertThat(back.maximized()).as("spec 11: Bring Back ends a maximize").isEmpty();
        Layout empty = Layout.empty();
        Layout panes = Layout.of(X);
        assertThat(empty.graft(panes, empty.activeGroup(), Side.RIGHT, 0.5)).isEqualTo(panes);
    }

    @Test void graftingRefusesWhatWouldBreakTheLayout() {
        Layout layout = Layout.of(A);
        GroupId a = layout.activeGroup();
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(A), a, Side.RIGHT, 0.5)).withMessageContaining("twice");
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.empty(), a, Side.RIGHT, 0.5)).withMessageContaining("no panes");
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(X), GroupId.random(), Side.RIGHT, 0.5))
            .withMessageContaining("not in the layout");
        for (double share : new double[] {0, 1, -0.5, Double.NaN})
            assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(X), a, Side.RIGHT, share)).withMessageContaining("share");
    }

    @Test void anOriginNeedsEveryValueAndAShareBetweenZeroAndOne() {
        GroupId pane = GroupId.random();
        assertThatIllegalArgumentException().isThrownBy(() -> new Origin(PARENT, pane, Side.RIGHT, 1)).withMessageContaining("share");
        assertThatIllegalArgumentException().isThrownBy(() -> new Origin(PARENT, pane, Side.RIGHT, 0)).withMessageContaining("share");
        assertThatNullPointerException().isThrownBy(() -> new Origin(null, pane, Side.RIGHT, 0.5));
        Origin origin = new Origin(PARENT, pane, Side.UP, 0.25);
        var window = new PaneLayout(Layout.of(A), origin);
        assertThat(window.origin()).isEqualTo(origin);
        assertThat(window.tabs()).containsExactly(A);
        assertThatNullPointerException().isThrownBy(() -> new PaneLayout(Layout.of(A), null));
    }
}
```

7a's `ParentTabsTest` builds a `PaneLayout` from a layout alone; it gains an origin:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/layout/ParentTabsTest.java", [
    ("        WindowLayout pane = new PaneLayout(Layout.of(C));\n",
     "        WindowLayout pane = new PaneLayout(Layout.of(C), new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5));\n", 1),
    ("        assertThatIllegalArgumentException().isThrownBy(() -> new PaneLayout(Layout.empty())).withMessageContaining(\"pane window\");\n",
     "        assertThatIllegalArgumentException().isThrownBy(() -> new PaneLayout(Layout.empty(),\n"
     "            new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5))).withMessageContaining(\"pane window\");\n", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.OriginTest'`
Expected: FAIL to compile: `cannot find symbol: class Origin`.

- [ ] **Step 3: The origin, the pane layout and the two operations**

Create `jasper-app/src/main/java/dev/jasper/app/layout/Origin.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;

/**
 * Where a pane window's panes came from (parent tabs spec 4), which Bring Back returns them to (spec 5): beside the pane
 * {@code pane} of the parent tab {@code parentTab}, wherever that parent tab is by then (spec 11), on {@code side} of it,
 * taking {@code share} of the space the two share. The window is whichever holds the parent tab (workspace plan 7b
 * Rulings 1).
 *
 * @param parentTab the parent tab the panes left
 * @param pane the pane they sat beside
 * @param side the side of that pane they sat on
 * @param share their part of the space they and that pane shared: above 0 and below 1
 */
public record Origin(ParentTabId parentTab, GroupId pane, Side side, double share) {
    /** Requires every value, and a share above 0 and below 1. */
    public Origin {
        Objects.requireNonNull(parentTab, "parentTab");
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(side, "side");
        if (!(share > 0 && share < 1)) throw new IllegalArgumentException("A share is above 0 and below 1: " + share);
    }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/layout/PaneLayout.java` with:

```java
package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;

/**
 * A pane window's content (parent tabs spec 4): one layout of panes, never empty, as a pane window closes with its last
 * tab, and the origin Bring Back returns it to.
 *
 * @param layout the panes
 * @param origin where they came from
 */
public record PaneLayout(Layout layout, Origin origin) implements WindowLayout {
    /** Requires a layout holding at least one tab, and the origin. */
    public PaneLayout {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(origin, "origin");
        if (layout.isEmpty()) throw new IllegalArgumentException("A pane window holds at least one tab");
    }

    /** The layout's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        return layout.tabs();
    }
}
```

Then the two operations, with a small helper:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
L = "jasper-app/src/main/java/dev/jasper/app/layout/"
edit(L + "Layout.java", [("""    public Layout unmaximize() {
        return maximized.isEmpty() ? this : new Layout(root, activeGroup, Optional.empty(), recent, tabOrders);
    }
""", """    public Layout unmaximize() {
        return maximized.isEmpty() ? this : new Layout(root, activeGroup, Optional.empty(), recent, tabOrders);
    }

    /**
     * Where {@code group} sits (parent tabs spec 4's origin; workspace plan 7b Rulings 2): the group beside it, across its
     * edge with the sibling its space goes to when it leaves (the one before it, or after it when it is first, as
     * {@link #remove(TabId)} gives it); the side of that group it is on; and its weight's share of the two siblings'. Of the
     * groups across that edge, the one {@link #neighbour} finds is beside it. A layout's only group sits on the right of
     * itself, at half.
     *
     * @param parentTab the parent tab this layout is
     * @param group a group of the layout
     * @return the origin
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Origin origin(ParentTabId parentTab, GroupId group) {
        Objects.requireNonNull(parentTab, "parentTab");
        requireGroup(group);
        Optional<Parent> parent = parent(root, group);
        if (parent.isEmpty()) return new Origin(parentTab, group, Side.RIGHT, 0.5);
        Split split = parent.get().split();
        int index = parent.get().index(), sibling = index > 0 ? index - 1 : 1;
        boolean horizontal = split.orientation() == Orientation.HORIZONTAL;
        Side side = index > 0 ? (horizontal ? Side.RIGHT : Side.DOWN) : (horizontal ? Side.LEFT : Side.UP);
        Side toward = index > 0 ? (horizontal ? Side.LEFT : Side.UP) : (horizontal ? Side.RIGHT : Side.DOWN);
        var near = new ArrayList<Group>();
        collect(split.children().get(sibling).node(), near);
        GroupId beside = neighbour(group, toward).filter(found -> near.stream().anyMatch(candidate -> candidate.id().equals(found)))
            .orElse(near.getFirst().id());
        double own = split.children().get(index).weight(), other = split.children().get(sibling).weight();
        return new Origin(parentTab, beside, side, own / (own + other));
    }

    /**
     * Bring Back's layout (parent tabs spec 5): {@code panes} beside {@code beside}, on {@code side} with {@code share} of
     * the space the two share, nested as they were; their active group becomes the active one and a maximize ends (spec
     * 11). The empty workspace takes {@code panes} whole.
     *
     * @param panes the layout coming back: at least one tab, and no id this layout has
     * @param beside a group of this layout
     * @param side where the panes go
     * @param share their part of the space they and {@code beside} share: above 0 and below 1
     * @return the new layout
     * @throws IllegalArgumentException when {@code panes} is empty or shares an id with this layout, {@code beside} is not
     *     in it, or the share is out of range
     */
    public Layout graft(Layout panes, GroupId beside, Side side, double share) {
        Objects.requireNonNull(panes, "panes");
        Objects.requireNonNull(side, "side");
        if (panes.isEmpty()) throw new IllegalArgumentException("The empty workspace has no panes to bring back");
        if (!(share > 0 && share < 1)) throw new IllegalArgumentException("A share is above 0 and below 1: " + share);
        requireGroup(beside);
        if (isEmpty()) return panes.unmaximize();
        Node next = replace(root, beside, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(panes.root, share), new Child(found, 1 - share))
            : List.of(new Child(found, 1 - share), new Child(panes.root, share))));
        var order = new ArrayList<>(panes.recent);
        order.addAll(recent);
        var orders = new HashMap<>(tabOrders);
        orders.putAll(panes.tabOrders);
        return new Layout(next, panes.activeGroup, Optional.empty(), order, orders);
    }

    /** The split whose child {@code group} is, and that child's index. */
    private record Parent(Split split, int index) { }

    /** The split under {@code node} whose child is the group {@code group}; empty when it is {@code node} itself. */
    private static Optional<Parent> parent(Node node, GroupId group) {
        if (!(node instanceof Split split)) return Optional.empty();
        for (int i = 0; i < split.children().size(); i++) {
            Node child = split.children().get(i).node();
            if (child instanceof Group found && found.id().equals(group)) return Optional.of(new Parent(split, i));
            Optional<Parent> deeper = parent(child, group);
            if (deeper.isPresent()) return deeper;
        }
        return Optional.empty();
    }
""", 1)])
edit(L + "package-info.java", [(
    " * tabs ({@code ParentTabs}) or a pane window's one layout ({@code PaneLayout}). JDK-only: nothing from Swing, AWT or a\n",
    " * tabs ({@code ParentTabs}) or a pane window's one layout and where its panes came from ({@code PaneLayout},\n"
    " * {@code Origin}). JDK-only: nothing from Swing, AWT or a\n", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*'`
Expected: PASS (`OriginTest` 8; `ParentTabsTest` unchanged in number). Then `./gradlew :jasper-app:check`: PASS (the
architecture rule keeps `layout` JDK-only).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Pane windows: the origin and Bring Back's layout

An Origin records where a pane window's panes came from: the parent
tab, the pane they sat beside, the side and the share. Layout.origin
finds where a pane sits, beside the sibling its space goes to when it
leaves, and Layout.graft puts a layout back there as a nested split,
active, ending a maximize; the empty workspace takes it whole. A
PaneLayout carries its origin.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Pane windows, Pop Out Tab and Pop Out Pane

Spec 4 and 5: a pane window holds one layout and an origin, has no parent tabs, and closes with its last tab. Pop Out
Tab takes one tab there and Pop Out Pane a whole pane, each from the place where it sat, through the tab registry (one
`TAB_MOVED` per tab, sessions untouched). Rulings 3, 4, 7, 8, 9 and 11 apply. Pop Out Tab replaces Move Tab to New Window
behind the registry's `moveToNewWindow`, which `ContentTab.moveToNewWindow()` reaches too (Rulings 18). Plan 5's
tear-out keeps its full window (Rulings 19). The commands follow in Task 4.

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java`: `:225-267`, `openChild` (`:324-334`), the
    registry's wiring (`:112-114`) and the imports;
  - `WindowContent.java` (7a's `windowLayout` and `newParentTab`), `ParentTabBar.java` (7a's `refresh`),
    `WorkspaceActions.java` (7a's Close Parent Tab line), `TabDrag.java` (a comment, `:233`);
  - `jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java` (`:63`, `:83`, `:95`, `:353-358`, `:366`) and
    `Tab.java` (`:122`, `:124`): Javadoc and `movable`.
- Test:
  - `jasper-app/src/test/java/dev/jasper/app/workspace/MovesFixture.java` and `PopOutTest.java` (new);
  - `WindowManagerTest.java` and `TabDragWindowsTest.java`.
- Ledger: two rows.

**Interfaces:**
- Consumes:
  - Task 1's `Origin`, `PaneLayout` and `Layout.origin`;
  - 7a's `ParentTabsView` (`holding`, `groupOf`, `contains`, `views`, `active`, `isEmpty`, `refresh`) and
    `WorkspaceView` (`id`, `model`, `tab`, `groupView`);
  - plan 4a's `TabRegistry.move`, `select`, `inWindow` and `WindowManager.window`, `keep`, `rethrow`.
- Produces:
  - `public boolean TabRegistry.movable(Tab)`;
  - on `WindowManager`: `public Optional<ChildWindow> popOutTab(Tab)`, `public Optional<ChildWindow> popOutTab(Tab,
    Rectangle, Point)` and `public Optional<ChildWindow> popOutPane(WindowContent, GroupId)`;
  - `openChild(boolean, Path, Rectangle, Point, Origin)` (private);
  - `public Optional<ChildWindow> moveToNewWindow(Tab, Rectangle, Point)` stays, for plan 5's tear-out alone;
    `moveToNewWindow(Tab)` goes;
  - on `WindowContent` (package-private): `void paneWindow(Origin)`, `boolean isPaneWindow()`, `Optional<Origin>
    origin()`, `Origin originOf(GroupId, boolean whole)`, `Origin popOutOrigin(Tab)`, `Optional<java.awt.Rectangle>
    paneOnScreen(GroupId)`, `int paneCount()` and `boolean mayPopOutPane(GroupId)`;
  - `public WindowLayout windowLayout()` is a `PaneLayout` for a pane window holding a tab;
  - test support: `MovesFixture` with `model`, `terminals`, `facts`, `manager`, `main()`, `launch()`, `tabFacts()`,
    `static moved(List<Tab>, UUID, UUID)` and `static session(Tab)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/MovesFixture.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.session.TerminalSession;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.SwingUtilities;

/**
 * Jasper's windows for the moves of parent tabs spec 5, headless: a window manager over stand-in frames whose windows
 * share one tab registry and one terminal registry, which records what it reports. Made and used on the EDT, as the
 * windows are; {@link #launch()} runs off it.
 */
final class MovesFixture implements AutoCloseable {
    final Contributions model = new Contributions();
    final TerminalRegistry terminals = new TerminalRegistry();
    /** What the terminal registry reported, in order. */
    final List<TerminalEvent> facts = new ArrayList<>();
    final WindowManager manager;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    MovesFixture() {
        terminals.onEvent(facts::add);
        manager = new WindowManager(model, new WindowManager.Hooks(this::compose, MovesFixture::frame, window -> { },
            window -> { }, window -> { }, state -> { }, window -> { }, () -> { }));
    }

    private WindowContent compose(WindowManager.Opening opening) {
        var launcher = new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
        return content;
    }

    /** A frame that shows nothing and opens where the platform puts it. */
    private static WindowShell frame(JasperWindow window) {
        return new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
            component -> SwingUtilities.isDescendingFrom(component, window.content()));
    }

    /** The main window, opened with one terminal tab when none is open. */
    MainWindow main() { return manager.ensureMain("cold", null).orElseThrow(); }

    /** Starts every shell asked for so far; off the EDT. */
    void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** What the terminal registry reported of tabs opening, closing and moving between windows, in order. */
    List<TerminalEvent> tabFacts() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabMoved || fact instanceof TerminalEvent.TabOpened
            || fact instanceof TerminalEvent.TabClosed).toList();
    }

    /** One {@code TAB_MOVED} for each of {@code tabs}, in order, from window {@code from} to window {@code to}. */
    static List<TerminalEvent> moved(List<Tab> tabs, UUID from, UUID to) {
        return tabs.stream().map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), from, to)).toList();
    }

    /** A terminal tab's session. */
    static TerminalSession session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    /** Closes every window, as Quit does. */
    @Override public void close() { manager.closeAll(); }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PopOutTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.PaneLayout;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 4, 5 and 14: Pop Out Tab and Pop Out Pane, and the pane windows they open. */
class PopOutTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 5 and Review Focus 5: one tab leaves for a pane window whose origin is its pane; one TAB_MOVED; focus follows. */
    @Test void popOutTabTakesOneTabToAPaneWindowFromItsPane() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().newTab(HOME); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            Tab tab = content.selectedTab();
            var running = session(tab);
            GroupId pane = content.workspace().model().activeGroup();
            int[] focused = {0};
            tab.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            ChildWindow child = windows.manager.popOutTab(tab).orElseThrow();
            Origin origin = new Origin(content.activeParentTab(), pane, Side.RIGHT, 0.5);
            assertThat(child.content().isPaneWindow()).isTrue();
            assertThat(child.content().origin()).as("its pane, which keeps the other tab: it goes back on the right").contains(origin);
            assertThat(child.content().windowLayout()).isEqualTo(new PaneLayout(child.content().workspace().model(), origin));
            assertThat(child.content().tabs()).containsExactly(tab);
            assertThat(windows.tabFacts()).as("one move; no tab opened or closed").isEqualTo(moved(List.of(tab), main[0].id(), child.id()));
            assertThat(session(tab)).isSameAs(running);
            assertThat(running.exitFuture()).isNotDone();
            assertThat(focused[0]).as("the tab has the focus in its pane window").isPositive();
            assertThat(content.tabCount()).isEqualTo(1);
        });
    }

    /** Workspace plan 7b Rulings 3: a pane's only tab takes its pane along; the origin is where the pane sat. */
    @Test void aPanesOnlyTabPopsOutFromWhereItsPaneSat() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            GroupId left = content.workspace().model().groups().getFirst().id();
            ChildWindow child = windows.manager.popOutTab(content.selectedTab()).orElseThrow();
            assertThat(child.content().origin()).contains(new Origin(content.activeParentTab(), left, Side.RIGHT, 0.5));
            assertThat(content.workspace().model().groups()).as("the pane went with its tab").hasSize(1);
        });
    }

    /** Spec 5: a pane's tabs leave together, in order, showing the one it showed; one TAB_MOVED each; sessions kept. */
    @Test void popOutPaneTakesEveryTabOfThePaneInOrder() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = windows.main();
            main[0].content().newTab(HOME);
            main[0].content().newTab(HOME);
            main[0].content().split(Side.DOWN);
        });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            var workspace = content.workspace();
            GroupId top = workspace.model().groups().get(0).id(), bottom = workspace.model().groups().get(1).id();
            List<Tab> moving = workspace.groupView(top).tabs();
            Tab shown = moving.get(1);
            content.selectTab(shown);
            var sessions = moving.stream().map(MovesFixture::session).toList();
            windows.facts.clear();
            ChildWindow child = windows.manager.popOutPane(content, top).orElseThrow();
            assertThat(child.content().origin()).as("the pane below takes its space; it goes back above it")
                .contains(new Origin(content.activeParentTab(), bottom, Side.UP, 0.5));
            assertThat(child.content().workspace().model().groups()).singleElement().satisfies(group -> {
                assertThat(group.tabs()).containsExactlyElementsOf(moving.stream().map(Tab::id).toList());
                assertThat(group.selected()).contains(shown.id());
            });
            assertThat(child.content().selectedTab()).isSameAs(shown);
            assertThat(content.workspace().model().groups()).extracting(Group::id).containsExactly(bottom);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), child.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
        });
    }

    /** Workspace plan 7b Rulings 8: a child window's only tab and only pane stay, as that window already is its own. */
    @Test void aChildWindowsOnlyTabAndOnlyPaneStay() throws Exception {
        edt(() -> {
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            assertThat(windows.manager.popOutTab(full.content().selectedTab())).isEmpty();
            assertThat(windows.manager.popOutPane(full.content(), full.content().workspace().model().activeGroup())).isEmpty();
            full.content().newTab(HOME);
            ChildWindow pane = windows.manager.popOutTab(full.content().selectedTab()).orElseThrow();
            assertThat(windows.manager.popOutPane(pane.content(), pane.content().workspace().model().activeGroup()))
                .as("a pane window's only pane").isEmpty();
            assertThat(windows.manager.children()).containsExactly(full, pane);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 4: what pops out of a pane window keeps that window's origin. */
    @Test void whatPopsOutOfAPaneWindowKeepsItsOrigin() throws Exception {
        ChildWindow[] first = new ChildWindow[1];
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            first[0] = windows.manager.popOutTab(main.selectedTab()).orElseThrow();
            first[0].content().newTab(HOME);
            first[0].content().newTab(HOME);
        });
        windows.launch();
        edt(() -> {
            var pane = first[0].content();
            Origin origin = pane.origin().orElseThrow();
            ChildWindow second = windows.manager.popOutTab(pane.selectedTab()).orElseThrow();
            assertThat(second.content().origin()).contains(origin);
            pane.split(Side.RIGHT);
            ChildWindow third = windows.manager.popOutPane(pane, pane.workspace().model().activeGroup()).orElseThrow();
            assertThat(third.content().origin()).contains(origin);
        });
        windows.launch();
    }

    /** Spec 4 and 7, workspace plan 7b Rulings 9: a pane window has no parent tabs, so New Tab stacks in its pane. */
    @Test void aPaneWindowHasNoParentTabsAndNewTabStacksInItsPane() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            pane.invoke(ActionId.NEW_TAB);
            assertThat(pane.parentTabCount()).isEqualTo(1);
            assertThat(pane.tabCount()).as("New Tab stacks in its pane").isEqualTo(2);
            assertThat(pane.workspace().model().groups()).hasSize(1);
            assertThat(pane.parentTabBar().component().isVisible()).isFalse();
            assertThat(pane.windowLayout()).isInstanceOf(PaneLayout.class);
            pane.updateActions();
            assertThat(pane.action(ActionId.CLOSE_PARENT_TAB).isEnabled()).as("it has no parent tab to close").isFalse();
        });
        windows.launch();
    }

    /** Spec 7: Enterprise always shows the parent-tab bar, but a pane window has none. */
    @Test void enterprisesPaneWindowShowsNoParentTabBar() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var pane = track(new WindowContent(WindowRole.CHILD, idleLauncher(new ArrayDeque<>()), null, true, path -> { },
                    () -> { }, () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                assertThat(pane.parentTabBar().component().isVisible()).as("as a full window's").isTrue();
                pane.paneWindow(new Origin(ParentTabId.random(), GroupId.random(), Side.RIGHT, 0.5));
                assertThat(pane.parentTabBar().component().isVisible()).isFalse();
                pane.newTab(HOME);
                assertThat(pane.parentTabBar().component().isVisible()).isFalse();
                assertThat(pane.tabBar().component().isVisible()).as("its pane's classic strip shows").isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 1: a pane holding a tab with a modal prompt open stays whole; nothing moves and no window opens. */
    @Test void aPaneHoldingAPinnedTabStaysWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.newTab(HOME);
            Tab pinned = content.selectedTab();
            Subscription pin = pinned.pin();
            windows.facts.clear();
            assertThat(windows.manager.popOutPane(content, content.workspace().model().activeGroup())).isEmpty();
            assertThat(windows.manager.popOutTab(pinned)).isEmpty();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(windows.tabFacts()).isEmpty();
            assertThat(content.tabCount()).isEqualTo(2);
            pin.close();
            assertThat(windows.manager.popOutPane(content, content.workspace().model().activeGroup())).isPresent();
        });
        windows.launch();
    }
}
```

The tests that moved a tab to a new window move it with Pop Out Tab now, and the pretend screen shows where a pane window
opens, and that a pane a maximize hides has no place:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
regex(T + "WindowManagerTest.java", r"manager\.moveToNewWindow\((tab|notes|todo)\)", r"manager.popOutTab(\1)", 5)
edit(T + "TabDragWindowsTest.java", [
    ("""            // Move Tab to New Window from the keyboard while the mouse still drags the tab.
            ChildWindow moved = manager.moveToNewWindow(tab).orElseThrow();
""", """            // Pop Out Tab from the keyboard while the mouse still drags the tab.
            ChildWindow moved = manager.popOutTab(tab).orElseThrow();
""", 1),
    ("""    @Test void aTabMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
""", """    /** Parent tabs spec 5 and workspace plan 7b Rulings 11: a pane window opens where its pane was, at its size. */
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

    /**
     * Workspace plan 7b Rulings 11: a pane a maximize keeps out of the tree keeps its last size but has no place on
     * screen, so the platform places its pane window.
     */
    @Test void aPaneAMaximizeHidesPopsOutWhereThePlatformPutsIt() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab hidden = from.tabs().get(0);
            from.workspace().moveToNewGroup(from.tabs().get(1), activeGroup(main), Side.RIGHT);
            relayout(frameOf(main));
            from.workspace().toggleMaximize();
            relayout(frameOf(main));
            GroupId pane = hidden.group().orElseThrow();
            assertThat(from.workspace().model().maximized()).isPresent().doesNotContain(pane);
            assertThat(from.workspace().groupView(pane).getWidth()).as("its last size, out of the tree").isPositive();
            ChildWindow popped = manager.popOutTab(hidden).orElseThrow();
            assertThat(popped.content().isPaneWindow()).isTrue();
            assertThat(placed).as("no place on screen").isEmpty();
        });
    }

    @Test void aTabMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.PopOutTest'`
Expected: FAIL to compile: `cannot find symbol: method popOutTab(dev.jasper.app.contributions.Tab)`.

- [ ] **Step 3: Pane windows and their origins**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
C = "jasper-app/src/main/java/dev/jasper/app/contributions/"
edit(W + "WindowContent.java", [
    ("import dev.jasper.app.layout.ParentTabId;\n",
     "import dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\n", 1),
    ("    private final ParentTabsView parentTabs;\n", """    private final ParentTabsView parentTabs;
    /** A pane window's origin (parent tabs spec 4), which the window manager gives it once while it is empty; else null. */
    private Origin origin;
""", 1),
    ("""    /**
     * What this window holds (parent tabs spec 4): its parent tabs with their layouts, and the one that shows.
     *
     * @return the parent tabs
     */
    public WindowLayout windowLayout() { return parentTabs.model(); }
""", """    /**
     * What this window holds (parent tabs spec 4): its parent tabs with their layouts, and the one that shows; a pane
     * window's one layout and its origin from its first tab on (a pane window still opening, empty, reports its empty
     * parent tab).
     *
     * @return the parent tabs, or the pane window's layout
     */
    public WindowLayout windowLayout() {
        return origin != null && !parentTabs.isEmpty() ? new PaneLayout(workspace().model(), origin) : parentTabs.model();
    }

    /**
     * Makes this window, a child window still empty, a pane window from {@code value} (parent tabs spec 4; workspace plan
     * 7b Rulings 9): one layout and no parent-tab bar. The window manager does it once, before the window's frame exists.
     *
     * @param value where its panes come from
     * @throws IllegalStateException for the main window, a window holding a tab, or one that already is a pane window
     */
    void paneWindow(Origin value) {
        if (isMain() || !parentTabs.isEmpty() || origin != null)
            throw new IllegalStateException("Only a new, empty child window becomes a pane window");
        origin = Objects.requireNonNull(value, "value");
        parentTabs.refresh();
    }

    /** Whether this is a pane window (parent tabs spec 4). */
    boolean isPaneWindow() { return origin != null; }

    /** A pane window's origin; empty for the main window and a full window. */
    Optional<Origin> origin() { return Optional.ofNullable(origin); }

    /**
     * Where a pane window made from {@code pane} comes from (parent tabs spec 4 and 5): a pane window's own origin, as its
     * panes came from there (workspace plan 7b Rulings 4); else, when the pane goes {@code whole}, where it sits in its
     * parent tab ({@link dev.jasper.app.layout.Layout#origin}); else the pane itself, which keeps its other tabs, with the
     * new pane on its right at half its width (Rulings 3).
     */
    Origin originOf(GroupId pane, boolean whole) {
        if (origin != null) return origin;
        WorkspaceView view = parentTabs.holding(pane)
            .orElseThrow(() -> new IllegalArgumentException("Pane " + pane + " is not in this window"));
        return whole ? view.model().origin(view.id(), pane) : new Origin(view.id(), pane, Side.RIGHT, 0.5);
    }

    /** Pop Out Tab's origin for {@code tab}: its pane's, which goes with it when it is the pane's only tab (Rulings 3). */
    Origin popOutOrigin(Tab tab) {
        GroupId pane = parentTabs.groupOf(tab.id())
            .orElseThrow(() -> new IllegalArgumentException("Tab " + tab.id() + " is not in this window"));
        return originOf(pane, groupTabs(tab).size() == 1);
    }

    /**
     * {@code pane}'s rectangle on screen while it shows in the shown parent tab and has a size; empty otherwise, as for a
     * pane a maximize keeps out of the tree with its last size, which {@link WorkspaceView#dropAreas} skips too.
     */
    Optional<java.awt.Rectangle> paneOnScreen(GroupId pane) {
        WorkspaceView shown = parentTabs.active();
        return parentTabs.holding(pane).filter(view -> view == shown).map(view -> view.groupView(pane))
            .filter(group -> SwingUtilities.isDescendingFrom(group, shown))
            .filter(group -> group.getWidth() > 0 && group.getHeight() > 0).map(group -> {
                var corner = new Point(0, 0);
                SwingUtilities.convertPointToScreen(corner, group);
                return new java.awt.Rectangle(corner, group.getSize());
            });
    }

    /** How many panes this window has across its parent tabs; the main window's empty workspace counts its one. */
    int paneCount() {
        int count = 0;
        for (WorkspaceView view : parentTabs.views()) count += view.model().groups().size();
        return count;
    }

    /**
     * Whether Pop Out Pane may take {@code pane}: one of this window's, holding tabs that may all move, in the main window
     * or in a child window that has another pane (workspace plan 7b Rulings 7 and 8).
     */
    boolean mayPopOutPane(GroupId pane) {
        if (closed || pane == null) return false;
        WorkspaceView view = parentTabs.holding(pane).orElse(null);
        if (view == null || !(isMain() || paneCount() > 1)) return false;
        java.util.List<Tab> held = new ArrayList<>();
        for (TabId id : view.model().group(pane).orElseThrow().tabs()) view.tab(id).ifPresent(held::add);
        return movable(held);
    }

    /** Whether {@code moving} holds tabs and the registry would move every one ({@code TabRegistry.movable}, Rulings 7). */
    private boolean movable(java.util.List<Tab> moving) {
        return !moving.isEmpty() && moving.stream().allMatch(tab -> registry.movable(tab));
    }
""", 1),
    ("""     * empty (workspace plan 7a Rulings 7).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal tab; null once the window has closed
     */
    public TerminalContent newParentTab(Path directory) {
        return parentTabs.isEmpty() ? openTab(directory)
""", """     * empty (workspace plan 7a Rulings 7). A pane window, which has no parent tabs, takes it in its active pane (workspace
     * plan 7b Rulings 9).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal tab; null once the window has closed
     */
    public TerminalContent newParentTab(Path directory) {
        return parentTabs.isEmpty() || origin != null ? openTab(directory)
""", 1)])
edit(W + "ParentTabBar.java", [(
    """    /** Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last. */
    void refresh() {
        boolean shown = owner.look().showsParentTabBar(parentTabs.count());
""", """    /**
     * Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last. A pane window has
     * no parent tabs and shows no bar (parent tabs spec 7).
     */
    void refresh() {
        boolean shown = !owner.isPaneWindow() && owner.look().showsParentTabBar(parentTabs.count());
""", 1)])
edit(W + "WorkspaceActions.java", [(
    "                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null;\n",
    "                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null && !owner.isPaneWindow();\n", 1)])
edit(W + "TabDrag.java", [(
    "        // A command that moves the tab while it is dragged (Move Tab to New Window from the keyboard) ends the drag.\n",
    "        // A command that moves the tab while it is dragged (Pop Out Tab from the keyboard) ends the drag.\n", 1)])
edit(C + "TabRegistry.java", [
    ("     * @param newWindow moves a tab into a new child window, or leaves it where it is\n",
     "     * @param newWindow pops a tab out into a new pane window, or leaves it where it is\n", 3),
    ("""    /**
     * Move Tab to New Window (workspace tabs spec 6.5): the window manager opens a child window for the tab, or leaves it
     * where it is (a child window's only tab). A pinned tab or one being closed stays.
     *
     * @param tab the tab
     */
""", """    /**
     * Whether a move would take {@code tab}: one of this registry's, open, neither pinned nor being closed. A pane or a
     * parent tab moves whole or not at all (workspace plan 7b Rulings 7).
     *
     * @param tab the tab
     * @return whether it may move
     */
    public boolean movable(Tab tab) { return tabs.contains(tab) && !tab.closed() && !tab.pinned() && !pending.contains(tab); }

    /**
     * Pop Out Tab (parent tabs spec 5): the window manager opens a pane window for the tab, or leaves it where it is (a
     * child window's only tab). A pinned tab or one being closed stays.
     *
     * @param tab the tab
     */
""", 1)])
edit(C + "Tab.java", [
    ("    /** Move Tab to New Window: a new child window takes the tab (workspace tabs spec 6.5). */\n",
     "    /** Pop Out Tab: a new pane window takes the tab (parent tabs spec 5). */\n", 1),
    ("    /** Move Tab to Main Window: the main window takes the tab (workspace tabs spec 6.5). */\n",
     "    /** The main window takes the tab, as {@code ContentTab.moveToMainWindow()} asks (workspace tabs spec 6.5). */\n", 1)])
edit(C + "TabRegistry.java", [(
    "     * Move Tab to Main Window (workspace tabs spec 6.5): the end of the main window's active group. A pinned tab, one being\n"
    "     * closed or one the main window holds stays.\n",
    "     * {@code ContentTab.moveToMainWindow()} (workspace tabs spec 6.5): the end of the active pane of the main window's\n"
    "     * shown parent tab. A pinned tab, one being closed or one the main window holds stays.\n", 1)])
PY
```

- [ ] **Step 4: The window manager pops tabs and panes out**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(W, [
    ("import dev.jasper.app.contributions.TabRegistry;\n",
     "import dev.jasper.app.contributions.TabRegistry;\nimport dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Origin;\n", 1),
    ("            this::moveToNewWindow, this::movedOut, this::emptyWindow));\n",
     "            this::popOutTab, this::movedOut, this::emptyWindow));\n", 1),
    ("        return openChild(false, directory, null, null);\n", "        return openChild(false, directory, null, null, null);\n", 1),
    ("        return openChild(true, null, null, null).map(JasperWindow::id)\n",
     "        return openChild(true, null, null, null, null).map(JasperWindow::id)\n", 1),
    ("""    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor) {
""", """    /**
     * A child window: a pane window from {@code origin}, made one while it is empty and before its frame exists (parent tabs
     * spec 4), or a full window when it is null.
     */
    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor, Origin origin) {
""", 1),
    ("""            Optional.ofNullable(bounds).map(Rectangle::new), Optional.ofNullable(cursor).map(Point::new)), content -> {
            made[0] = new ChildWindow(this, content);
""", """            Optional.ofNullable(bounds).map(Rectangle::new), Optional.ofNullable(cursor).map(Point::new)), content -> {
            if (origin != null) content.paneWindow(origin);
            made[0] = new ChildWindow(this, content);
""", 1)])
span(W, """    /**
     * Move Tab to New Window (workspace tabs spec 6.5): a new child window takes {@code tab}, its content and session
""", """        rethrow(failure);
        return child.closed() ? Optional.empty() : opened;
    }
""", """    /**
     * Pop Out Tab (parent tabs spec 5): a new pane window takes {@code tab}, its content and session untouched, where its
     * pane is on screen and at the pane's size, with the origin {@code WindowContent.popOutOrigin} gives (workspace plan
     * 7b Rulings 3, 4 and 11). A tab being closed or pinned stays, and so does a child window's only tab: that window
     * already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new pane window; empty when the tab stayed
     */
    public Optional<ChildWindow> popOutTab(Tab tab) {
        requireEdt();
        WindowContent from = leaving(tab);
        Rectangle bounds = from == null ? null : tab.group().flatMap(from::paneOnScreen).orElse(null);
        return popOutTab(tab, bounds, centre(bounds));
    }

    /**
     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}.
     *
     * @param tab a tab of one of these windows
     * @param bounds where the pane window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new pane window; empty when the tab stayed
     */
    public Optional<ChildWindow> popOutTab(Tab tab, Rectangle bounds, Point cursor) {
        requireEdt();
        WindowContent from = leaving(tab);
        return from == null ? Optional.empty() : moveToNewChild(tab, bounds, cursor, from.popOutOrigin(tab));
    }

    /**
     * Pop Out Pane (parent tabs spec 5): a new pane window takes every tab of {@code pane}, in their order, showing the one
     * the pane showed, where the pane is on screen and at its size; its origin is where the pane sat
     * ({@code WindowContent.originOf}). A child window's only pane stays, as its only tab does, and a pane holding a tab
     * that is pinned or being closed stays whole (workspace plan 7b Rulings 7 and 8).
     *
     * @param window the window holding the pane
     * @param pane the pane
     * @return the new pane window; empty when the pane stayed
     */
    public Optional<ChildWindow> popOutPane(WindowContent window, GroupId pane) {
        requireEdt();
        if (window == null || !window.mayPopOutPane(pane)) return Optional.empty();
        WorkspaceView view = window.parentTabs().holding(pane).orElseThrow();
        var group = view.model().group(pane).orElseThrow();
        List<Tab> moving = group.tabs().stream().map(id -> view.tab(id).orElseThrow()).toList();
        Tab shown = group.selected().flatMap(view::tab).orElse(null);
        Rectangle bounds = window.paneOnScreen(pane).orElse(null);
        Optional<ChildWindow> opened = openChild(true, null, bounds, centre(bounds), window.originOf(pane, true));
        if (opened.isEmpty()) return opened;
        ChildWindow child = opened.get();
        Throwable failure = null;
        for (Tab tab : moving) {
            try {
                model.tabs().move(tab, child.id());
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        // The pane window shows the tab the pane showed.
        if (shown != null && child.content().parentTabs().contains(shown)) {
            try {
                model.tabs().select(shown);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        return settle(child, failure);
    }

    /**
     * Plan 5's tear-out: a new full child window takes {@code tab}, its content and session untouched, its workspace at
     * {@code bounds} on screen, on the display under {@code cursor}. A tab being closed or pinned stays, and so does a
     * child window's only tab.
     *
     * @param tab a tab of one of these windows
     * @param bounds where the new window's workspace goes on screen; null for where the platform puts it
     * @param cursor where the cursor released the tab on screen; null exactly when {@code bounds} is
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds, Point cursor) {
        requireEdt();
        return leaving(tab) == null ? Optional.empty() : moveToNewChild(tab, bounds, cursor, null);
    }

    /**
     * The window {@code tab} may leave for a window of its own: none for a closed or pinned tab, nor for a child window's
     * only tab, whose window already is its own.
     */
    private WindowContent leaving(Tab tab) {
        if (tab == null || tab.closed() || tab.pinned()) return null;
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || !from.get().isMain() && from.get().content().tabCount() < 2) return null;
        return from.get().content();
    }

    /** A new child window takes {@code tab}: a pane window from {@code origin}, or a full window when it is null. */
    private Optional<ChildWindow> moveToNewChild(Tab tab, Rectangle bounds, Point cursor, Origin origin) {
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor, origin);
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
     * A new child window that a refused or failed move left without a tab goes; the move's failure propagates, the close's
     * kept behind it.
     */
    private Optional<ChildWindow> settle(ChildWindow child, Throwable failure) {
        if (!child.closed() && model.tabs().inWindow(child.id()).isEmpty()) {
            try {
                child.close();
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return child.closed() ? Optional.empty() : Optional.of(child);
    }

    /** The middle of {@code bounds} on screen, whose display a window opening there takes; null for null. */
    private static Point centre(Rectangle bounds) {
        return bounds == null ? null : new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }
""")
PY
git grep -n --untracked -E 'manager\.moveToNewWindow\((tab|notes|todo)\)|Optional<ChildWindow> moveToNewWindow\(Tab tab\)' -- jasper-app/src
```

Expected: the `git grep` prints nothing; the one-argument `WindowManager.moveToNewWindow` and its callers are gone.

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "MovesFixture.java", "app.workspace", "MovesFixture", T + "PopOutTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7b Task 2; windows for moves between windows")
ledger.add(T + "PopOutTest.java", "app.workspace", "PopOutTest", T + "PopOutTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7b Task 2; Pop Out Tab, Pop Out Pane and pane windows")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`PopOutTest` 8, `TabDragWindowsTest` +2; `WindowManagerTest` unchanged in number). Then
`./gradlew :jasper-app:check`: PASS. A plugin test that moves a `ContentTab` to a new window
(`EditorDocumentPreservationTest`, `HostedChooserTest`, `WorkspaceContentRouteTest`, `AppContractTest`) runs
unchanged: the tab lands in a pane window, a child window.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Pane windows: Pop Out Tab and Pop Out Pane

A pane window is a child window whose content has an origin, given
while it is empty: one layout, no parent-tab bar, New Tab stacking in
its pane. Pop Out Tab takes a tab there from its pane, and Pop Out
Pane every tab of a pane in order, both through the tab registry, one
TAB_MOVED per tab with every session running, at the pane's place on
screen. The origin is where the pane sat, or the pane a tab left; what
pops out of a pane window keeps its origin. A child window's only tab
or pane stays, and a pane holding a pinned tab stays whole. The
registry's Move Tab to New Window, which plugins reach, is Pop Out Tab
now; plan 5's tear-out still opens a full window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Pop Out Parent Tab, Bring Back and Move to Main Window

Spec 5 and 11: a parent tab pops out whole into a full window and a full window's parent tabs join the main window, each
with its id and layout. A pane window's whole layout comes back beside its origin pane, following the origin's parent
tab into whichever window holds it, with the fallbacks of spec 5 and 11. The receiving window lands the moving tabs in
the chosen parent tab, then gives it the planned layout at once (Rulings 5, 6, 7, 10 and 12). The commands are enabled
only while every tab they would move may move (Rulings 7).

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` (before `resizeSplit`, `:190`);
  - `ParentTabsView.java` (7a's `openParentTab`, and before its `changed`);
  - `WindowContent.java` (Task 2's text, and 7a's `attach`);
  - `WindowManager.java` (Task 2's text).
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/BringBackTest.java` (new)
- Ledger: one row.

**Interfaces:**
- Consumes:
  - Task 1's `Origin`, `Layout.graft`, `PaneLayout`;
  - Task 2's `isPaneWindow`, `origin()`, `openChild(..., Origin)`, `settle`, `TabRegistry.movable` and `MovesFixture`;
  - 7a's `ParentTabsView` (`view`, `model`, `count`, `isEmpty`, `active`, `reveal`), `ParentTabs` (`of`, `open`,
    `indexOf`), `WindowContent.selectParentTab` and `activeParentTab`.
- Produces:
  - `void WorkspaceView.rearrange(Layout)`;
  - on `ParentTabsView`: `WorkspaceView openParentTab(Tab, ParentTabId, int)`, `void land(Tab, ParentTabId, int)` and
    `void renew(ParentTabId)`;
  - on `WindowContent`: `boolean receive(java.util.List<Tab>, ParentTabId, int, Layout)`, `boolean
    mayPopOutParentTab(ParentTabId)`, `boolean mayBringBack()` and `boolean mayMoveToMainWindow()`;
  - on `WindowManager`:
    - `public Optional<ChildWindow> popOutParentTab(WindowContent, ParentTabId)`;
    - `public Optional<ChildWindow> popOutParentTab(WindowContent, ParentTabId, Rectangle, Point)`;
    - `public boolean moveParentTab(WindowContent from, ParentTabId, WindowContent to, int index)`, which plan 7c's
      drag onto another window's bar uses;
    - `public Optional<JasperWindow> bringBack(WindowContent)`;
    - `public Optional<MainWindow> moveToMainWindow(WindowContent)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/BringBackTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.MovesFixture.moved;
import static dev.jasper.app.workspace.MovesFixture.session;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 5, 11 and 14: Pop Out Parent Tab, Move to Main Window and Bring Back, and the origins they follow. */
class BringBackTest {
    private MovesFixture windows;

    @BeforeEach void windows() throws Exception { edt(() -> windows = new MovesFixture()); }

    @AfterEach void cleanup() throws Exception {
        edt(() -> windows.close());
        closeOwners();
    }

    /** Spec 5 and Review Focus 5: the pane window's layout returns nested beside its origin pane; its active tab has the focus. */
    @Test void bringBackReturnsThePanesNestedBesideTheOriginPane() throws Exception {
        MainWindow[] main = new MainWindow[1];
        ChildWindow[] pane = new ChildWindow[1];
        edt(() -> { main[0] = windows.main(); main[0].content().split(Side.RIGHT); });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            pane[0] = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            pane[0].content().split(Side.DOWN);
        });
        windows.launch();
        edt(() -> {
            var content = main[0].content();
            GroupId left = content.workspace().model().activeGroup();
            Layout panes = pane[0].content().workspace().model();
            List<Tab> moving = pane[0].content().tabs();
            var sessions = moving.stream().map(MovesFixture::session).toList();
            Tab active = pane[0].content().selectedTab();
            int[] focused = {0};
            active.focusTarget(() -> focused[0]++);
            windows.facts.clear();
            assertThat(windows.manager.bringBack(pane[0].content())).containsSame(main[0]);
            assertThat(pane[0].closed()).as("the pane window closes with its last tab").isTrue();
            Layout layout = content.workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> {
                assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
                assertThat(split.children()).extracting(Child::weight).containsExactly(0.5, 0.5);
                assertThat(split.children()).extracting(Child::node).as("nested as it was in the pane window")
                    .containsExactly(layout.group(left).orElseThrow(), panes.root());
            });
            assertThat(content.selectedTab()).as("the pane window's active tab").isSameAs(active);
            assertThat(focused[0]).isPositive();
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, pane[0].id(), main[0].id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
        });
    }

    /** Spec 11: Bring Back into a maximized parent tab ends the maximize. */
    @Test void bringBackIntoAMaximizedParentTabEndsTheMaximize() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            content.split(Side.RIGHT);
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.toggleMaximize();
            assertThat(content.workspace().model().maximized()).isPresent();
            windows.manager.bringBack(pane.content());
            assertThat(content.workspace().model().maximized()).isEmpty();
            assertThat(content.workspace().model().groups()).hasSize(3);
        });
        windows.launch();
    }

    /** Spec 11: with the origin pane gone and its parent tab still there, the panes go beside its active pane. */
    @Test void withTheOriginPaneGoneThePanesGoBesideItsParentTabsActivePane() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            Tab first = content.selectedTab();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.split(Side.DOWN);
            GroupId below = content.workspace().model().activeGroup();
            content.closeTab(first);
            assertThat(content.workspace().model().groups()).extracting(Group::id).containsExactly(below);
            Tab back = pane.content().selectedTab();
            windows.manager.bringBack(pane.content());
            Layout layout = content.workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split -> {
                assertThat(split.orientation()).as("on the recorded side").isEqualTo(Orientation.HORIZONTAL);
                assertThat(split.children().get(0).node()).isEqualTo(layout.group(below).orElseThrow());
                assertThat(((Group) split.children().get(1).node()).tabs()).containsExactly(back.id());
            });
        });
        windows.launch();
    }

    /** Review Focus 3 and spec 5: a parent tab's only pane popping out closes it; Bring Back then uses the main window's. */
    @Test void withItsParentTabGoneThePanesGoBesideTheMainWindowsActivePane() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.newParentTab(HOME);
            ParentTabId second = content.activeParentTab();
            GroupId secondPane = content.workspace().model().activeGroup();
            content.selectParentTab(first);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            assertThat(content.parentTabCount()).as("the parent tab went with its only pane").isEqualTo(1);
            assertThat(content.activeParentTab()).as("the one used before it shows").isEqualTo(second);
            assertThat(pane.content().origin().orElseThrow().parentTab()).isEqualTo(first);
            Tab back = pane.content().selectedTab();
            assertThat(windows.manager.bringBack(pane.content())).containsSame(windows.main());
            Layout layout = content.workspace().model();
            assertThat(content.parentTabOf(back)).contains(second);
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::node).first().isEqualTo(layout.group(secondPane).orElseThrow()));
        });
        windows.launch();
    }

    /** Spec 11: the origin follows its parent tab by id, into the full window that parent tab popped out to. */
    @Test void theOriginFollowsItsParentTabIntoAFullWindow() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            GroupId left = content.workspace().model().activeGroup();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.newParentTab(HOME);
            ChildWindow full = windows.manager.popOutParentTab(content, first).orElseThrow();
            assertThat(full.content().isPaneWindow()).isFalse();
            assertThat(full.content().activeParentTab()).as("the parent tab keeps its id").isEqualTo(first);
            Tab back = pane.content().selectedTab();
            assertThat(windows.manager.bringBack(pane.content())).containsSame(full);
            Layout layout = full.content().workspace().model();
            assertThat(layout.root()).isInstanceOfSatisfying(Split.class, split ->
                assertThat(split.children()).extracting(Child::node).first().isEqualTo(layout.group(left).orElseThrow()));
            assertThat(full.content().parentTabOf(back)).contains(first);
        });
        windows.launch();
    }

    /**
     * Review Focus 2: the main window's only pane popped out; its empty workspace keeps the parent tab's id, which the pane
     * window's origin names, and Bring Back fills it (workspace plan 7b Rulings 10).
     */
    @Test void bringBackFillsTheEmptyMainWorkspaceItLeft() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId only = content.activeParentTab();
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            assertThat(content.workspace().emptyWorkspace()).as("the main window's empty workspace").isNotNull();
            assertThat(content.activeParentTab()).as("a pane popping out leaves the id").isEqualTo(only);
            pane.content().split(Side.RIGHT);
            Layout panes = pane.content().workspace().model();
            windows.manager.bringBack(pane.content());
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(only);
            assertThat(content.workspace().model()).as("the panes, whole").isEqualTo(panes);
            assertThat(content.workspace().emptyWorkspace()).isNull();
        });
        windows.launch();
    }

    /**
     * Workspace plan 7b Rulings 10: the main window's only parent tab moves whole with its id, so the empty workspace it
     * leaves takes a new one, and none of the moved panes' ids (parent tabs spec 4).
     */
    @Test void theMainWindowsOnlyParentTabPopsOutWholeAndItsEmptyWorkspaceTakesANewId() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId only = content.activeParentTab();
            content.split(Side.RIGHT);
            Layout moving = content.workspace().model();
            ChildWindow full = windows.manager.popOutParentTab(content, only).orElseThrow();
            assertThat(full.content().activeParentTab()).isEqualTo(only);
            assertThat(full.content().workspace().model()).isEqualTo(moving);
            assertThat(content.activeParentTab()).as("the empty workspace's new id").isNotEqualTo(only);
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.workspace().emptyWorkspace()).isNotNull();
            assertThat(moving.groups()).extracting(Group::id).doesNotContain(content.workspace().model().activeGroup());
        });
        windows.launch();
    }

    /** Spec 5 and workspace plan 7b Rulings 10: a parent tab pops out whole into a full window, with its id and layout. */
    @Test void popOutParentTabMovesItWholeToAFullWindow() throws Exception {
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
            ChildWindow full = windows.manager.popOutParentTab(content, first).orElseThrow();
            assertThat(full.content().windowLayout()).isInstanceOfSatisfying(ParentTabs.class, parents ->
                assertThat(parents.parentTabs()).containsExactly(new ParentTab(first, before)));
            assertThat(content.parentTabCount()).isEqualTo(1);
            assertThat(content.activeParentTab()).isEqualTo(second);
            assertThat(windows.tabFacts()).isEqualTo(moved(moving, main[0].id(), full.id()));
            for (int i = 0; i < moving.size(); i++) assertThat(session(moving.get(i))).isSameAs(sessions.get(i));
            assertThat(windows.manager.popOutParentTab(full.content(), first)).as("a full window's only parent tab stays").isEmpty();
        });
        windows.launch();
    }

    /** Spec 5 and workspace plan 7b Rulings 12: a full window's parent tabs join the main window's bar after its active one. */
    @Test void moveToMainWindowJoinsTheParentTabsAfterTheActiveOne() throws Exception {
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
            var layouts = ((ParentTabs) content.windowLayout()).parentTabs();
            int tabs = content.tabCount();
            windows.facts.clear();
            assertThat(windows.manager.moveToMainWindow(content)).containsSame(windows.main());
            assertThat(full.closed()).as("the full window closes with its last tab").isTrue();
            var parents = (ParentTabs) main.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(m1, p1, p2, m2);
            assertThat(parents.parentTabs().subList(1, 3)).as("each with its layout").containsExactlyElementsOf(layouts);
            assertThat(main.activeParentTab()).as("the parent tab it showed").isEqualTo(p2);
            assertThat(windows.tabFacts()).hasSize(tabs).allMatch(TerminalEvent.TabMoved.class::isInstance);
        });
        windows.launch();
    }

    /** Workspace plan 7b Rulings 12: the main window's empty workspace gives way to the first parent tab. */
    @Test void moveToMainWindowFillsAnEmptyMainWorkspace() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.closeTab(main.selectedTab());
            ChildWindow full = windows.manager.openTerminalWindow(null).orElseThrow();
            ParentTabId p1 = full.content().activeParentTab();
            windows.manager.moveToMainWindow(full.content());
            assertThat(((ParentTabs) main.windowLayout()).parentTabs()).extracting(ParentTab::id).containsExactly(p1);
            assertThat(main.tabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Review Focus 1: a pinned tab keeps its parent tab and its pane window whole, and nothing moves. */
    @Test void aPinnedTabKeepsItsParentTabAndItsPaneWindowWhole() throws Exception {
        edt(() -> {
            var content = windows.main().content();
            ParentTabId first = content.activeParentTab();
            content.split(Side.RIGHT);
            ChildWindow pane = windows.manager.popOutPane(content, content.workspace().model().activeGroup()).orElseThrow();
            content.newParentTab(HOME);
            Subscription heldHere = content.tabs().getFirst().pin();
            Subscription heldThere = pane.content().selectedTab().pin();
            windows.facts.clear();
            assertThat(windows.manager.popOutParentTab(content, first)).isEmpty();
            assertThat(windows.manager.bringBack(pane.content())).isEmpty();
            assertThat(windows.manager.moveParentTab(content, first, pane.content(), 0)).as("nor into a pane window").isFalse();
            assertThat(pane.closed()).isFalse();
            assertThat(windows.manager.children()).containsExactly(pane);
            assertThat(windows.tabFacts()).isEmpty();
            heldHere.close();
            heldThere.close();
            assertThat(windows.manager.bringBack(pane.content())).isPresent();
        });
        windows.launch();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.BringBackTest'`
Expected: FAIL to compile: `cannot find symbol: method bringBack(dev.jasper.app.workspace.WindowContent)`.

- [ ] **Step 3: Landing a move in a chosen parent tab, and rearranging it**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WorkspaceView.java", [(
    "    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }\n",
    """    /**
     * Gives this workspace {@code arranged}: its own tabs in another arrangement, as panes come back or a parent tab moves
     * between windows (workspace plan 7b Rulings 6). Tabs {@code arranged} names that this workspace does not hold leave it
     * first; nothing changes when it would still leave out a tab this workspace holds. Every tab takes its place at once,
     * without the open motion, as a moved tab does.
     */
    void rearrange(Layout arranged) {
        Layout next = arranged;
        for (TabId tab : arranged.tabs()) if (!tabs.containsKey(tab)) next = next.remove(tab);
        if (!Set.copyOf(next.tabs()).equals(tabs.keySet())) return;
        moved.addAll(tabs.keySet());
        apply(next);
    }

    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
""", 1)])
edit(W + "ParentTabsView.java", [
    ("""    WorkspaceView openParentTab(Tab tab) {
        boolean held = WorkspaceView.holdsFocus(owner, this);
        var view = new WorkspaceView(owner, ParentTabId.random());
        int index = count();
        try {
            view.applyTheme(owner.theme(), false);
            view.setGlass((float) owner.backgroundOpacity() < 1f);
            view.open(tab);
            model = model.open(new ParentTab(view.id(), view.model()), index);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        views.put(view.id(), view);
        // The views keep the bar's order, ahead of any other child.
        add(view, index);
""", """    WorkspaceView openParentTab(Tab tab) { return openParentTab(tab, ParentTabId.random(), count()); }

    /**
     * As {@link #openParentTab(Tab)}, under {@code id} at {@code index} of the bar (its end past it): a parent tab moving
     * in from another window keeps its id (parent tabs spec 11; workspace plan 7b Rulings 10). While the window's only
     * parent tab is empty, the new one takes its place.
     */
    WorkspaceView openParentTab(Tab tab, ParentTabId id, int index) {
        boolean held = WorkspaceView.holdsFocus(owner, this);
        WorkspaceView empty = isEmpty() ? active() : null;
        var view = new WorkspaceView(owner, id);
        int at = empty != null ? 0 : Math.max(0, Math.min(index, count()));
        try {
            view.applyTheme(owner.theme(), false);
            view.setGlass((float) owner.backgroundOpacity() < 1f);
            view.open(tab);
            var opened = new ParentTab(id, view.model());
            model = empty != null ? ParentTabs.of(opened) : model.open(opened, at);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        if (empty != null) {
            views.remove(empty.id());
            super.remove(empty);
            empty.close();
        }
        views.put(view.id(), view);
        // The views keep the bar's order, ahead of any other child.
        add(view, at);
""", 1),
    ("""    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
""", """    /**
     * Puts {@code tab}, moving in, in the active pane of the parent tab {@code id}; a parent tab the window lacks opens
     * under that id at {@code index} of the bar ({@link #openParentTab(Tab, ParentTabId, int)}). Workspace plan 7b's moves.
     */
    void land(Tab tab, ParentTabId id, int index) {
        WorkspaceView view = views.get(id);
        if (view != null) view.open(tab);
        else openParentTab(tab, id, index);
    }

    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
""", 1),
    ("""    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */
""", """    /**
     * The window's only parent tab {@code id}, which moved whole to another window and keeps its id there (parent tabs spec
     * 11), gives way to an empty workspace under a new id, so ids stay unique across windows (spec 4; workspace plan 7b
     * Rulings 10). The window manager's {@code moveParentTab} calls it; it does nothing unless {@code id} is this window's
     * only parent tab and empty, and the window is open.
     */
    void renew(ParentTabId id) {
        WorkspaceView emptied = views.get(id);
        if (closed || emptied == null || count() != 1 || !emptied.isEmpty()) return;
        boolean held = WorkspaceView.holdsFocus(owner, this);
        var fresh = new WorkspaceView(owner, ParentTabId.random());
        views.remove(emptied.id());
        super.remove(emptied);
        emptied.close();
        views.put(fresh.id(), fresh);
        add(fresh, 0);
        model = ParentTabs.of(new ParentTab(fresh.id(), fresh.model()));
        reveal(fresh);
        owner.workspaceChanged(fresh, held);
    }

    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */
""", 1)])
edit(W + "WindowContent.java", [
    ("import dev.jasper.app.layout.Origin;\n", "import dev.jasper.app.layout.Layout;\nimport dev.jasper.app.layout.Origin;\n", 1),
    ("""    /** A pane window's origin (parent tabs spec 4), which the window manager gives it once while it is empty; else null. */
    private Origin origin;
""", """    /** A pane window's origin (parent tabs spec 4), which the window manager gives it once while it is empty; else null. */
    private Origin origin;
    /** Where tabs moving in land while {@link #receive} runs: a parent tab, and its place in the bar when it is new. */
    private record Landing(ParentTabId parentTab, int index) { }
    private Landing landing;
""", 1),
    ("""            if (parentTab) parentTabs.openParentTab(tab);
            else if (beside == null) workspace().open(tab);
""", """            if (parentTab) parentTabs.openParentTab(tab);
            // A tab a move between windows brings in lands in the parent tab chosen for it (workspace plan 7b Rulings 6).
            else if (beside == null && landing != null && tab.moving()) parentTabs.land(tab, landing.parentTab(), landing.index());
            else if (beside == null) workspace().open(tab);
""", 1),
    ("""    private boolean movable(java.util.List<Tab> moving) {
        return !moving.isEmpty() && moving.stream().allMatch(tab -> registry.movable(tab));
    }
""", """    private boolean movable(java.util.List<Tab> moving) {
        return !moving.isEmpty() && moving.stream().allMatch(tab -> registry.movable(tab));
    }

    /**
     * Whether Pop Out Parent Tab may take {@code parentTab}: one of this window's, holding tabs that may all move, in the
     * main window or in a full window that has another parent tab; never a pane window's (parent tabs spec 5; workspace
     * plan 7b Rulings 7 and 8).
     */
    boolean mayPopOutParentTab(ParentTabId parentTab) {
        if (closed || parentTab == null || origin != null) return false;
        Optional<WorkspaceView> view = parentTabs.view(parentTab);
        return view.isPresent() && (isMain() || parentTabs.count() > 1) && movable(view.get().tabs());
    }

    /** Whether Bring Back may take this window's tabs: a pane window's, every one of which may move (Rulings 7). */
    boolean mayBringBack() { return !closed && origin != null && movable(tabs()); }

    /** Whether Move to Main Window may take this window's tabs: a full window's, every one of which may move (Rulings 7). */
    boolean mayMoveToMainWindow() { return !closed && !isMain() && origin == null && movable(tabs()); }

    /**
     * Takes {@code moving}, other windows' tabs, into this window's parent tab {@code parentTab} through the tab registry,
     * one move each, so each keeps its content and session and reports {@code TAB_MOVED} once (parent tabs spec 5;
     * workspace plan 7b Rulings 6). A parent tab this window lacks opens under that id at {@code index} of the bar, in
     * place of the main window's empty workspace. That parent tab then takes {@code arranged}
     * ({@link WorkspaceView#rearrange}) and shows, and {@code arranged}'s active tab is selected, focused and raised. A tab
     * the registry does not move stays where it was; the first failure propagates once the others have moved.
     *
     * @param moving the tabs, in the order they move
     * @param parentTab the parent tab they land in
     * @param index where a new parent tab goes in the bar
     * @param arranged the parent tab's layout once they are all in
     * @return whether a tab moved in
     */
    boolean receive(java.util.List<Tab> moving, ParentTabId parentTab, int index, Layout arranged) {
        if (closed) return false;
        Throwable failure = null;
        landing = new Landing(parentTab, index);
        try {
            for (Tab tab : moving) {
                try {
                    registry.move(tab, id);
                } catch (RuntimeException | Error thrown) {
                    failure = WindowManager.keep(failure, thrown);
                }
            }
        } finally {
            landing = null;
        }
        WorkspaceView view = closed ? null : parentTabs.view(parentTab).orElse(null);
        boolean arrived = view != null && moving.stream().anyMatch(view::contains);
        if (arrived) {
            view.rearrange(arranged);
            Tab shown = view.selected();
            if (shown != null) {
                try {
                    registry.select(shown);
                } catch (RuntimeException | Error thrown) {
                    failure = WindowManager.keep(failure, thrown);
                }
            }
        }
        WindowManager.rethrow(failure);
        return arrived;
    }
""", 1)])
PY
```

- [ ] **Step 4: The window manager moves parent tabs and brings panes back**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(W, [
    ("import dev.jasper.app.layout.Origin;\n",
     "import dev.jasper.app.layout.Layout;\nimport dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.ParentTab;\n"
     "import dev.jasper.app.layout.ParentTabId;\n", 1),
    ("""    /**
     * Plan 5's tear-out: a new full child window takes {@code tab}, its content and session untouched, its workspace at
""", """    /**
     * Pop Out Parent Tab (parent tabs spec 5): a new full window takes the parent tab {@code parentTab} whole, with its id and
     * layout, where the platform puts it (workspace plan 7b Rulings 10 and 11). A full window's only parent tab stays (spec
     * 5), and so does a parent tab holding a tab that is pinned or being closed.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> popOutParentTab(WindowContent window, ParentTabId parentTab) {
        return popOutParentTab(window, parentTab, null, null);
    }

    /**
     * As {@link #popOutParentTab(WindowContent, ParentTabId)}, the full window's workspace at {@code bounds} on screen, on the
     * display under {@code cursor}.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @param bounds where the full window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> popOutParentTab(WindowContent window, ParentTabId parentTab, Rectangle bounds, Point cursor) {
        requireEdt();
        if (window == null || !window.mayPopOutParentTab(parentTab)) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor, null);
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
     * Moves the parent tab {@code parentTab} of {@code from} to {@code index} of {@code to}'s bar, whole, with its id and
     * layout (parent tabs spec 5 and 11; workspace plan 7b Rulings 6 and 10): through the tab registry, one
     * {@code TAB_MOVED} per tab, every session untouched. It shows there, raised, and a full window it empties closes; the
     * main window it empties shows an empty workspace under a new id. Both windows are main or full windows; a parent tab
     * holding a tab that is pinned or being closed stays.
     *
     * @param from the window holding the parent tab
     * @param parentTab the parent tab
     * @param to another main or full window
     * @param index where it goes in {@code to}'s bar
     * @return whether it moved
     */
    public boolean moveParentTab(WindowContent from, ParentTabId parentTab, WindowContent to, int index) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed() || from.isPaneWindow() || to.isPaneWindow())
            return false;
        WorkspaceView view = from.parentTabs().view(parentTab).orElse(null);
        if (view == null || view.isEmpty() || !view.tabs().stream().allMatch(model.tabs()::movable)) return false;
        boolean moved = to.receive(view.tabs(), parentTab, index, view.model());
        // The window's only parent tab is the other window's now: the empty workspace it leaves takes a new id (Rulings 10).
        if (moved && !from.closed()) from.parentTabs().renew(parentTab);
        return moved;
    }

    /**
     * Bring Back (parent tabs spec 5 and 11; workspace plan 7b Rulings 5): a pane window's whole layout returns as a nested
     * split beside its origin pane, on the recorded side with the recorded share, in the parent tab the origin names,
     * whichever main or full window holds it now. It goes beside that parent tab's active pane when the origin pane is
     * gone, and beside the active pane of the main window's active parent tab when no window holds the parent tab. A
     * maximize there ends; the parent tab shows with the pane window's active tab selected, focused and raised, and the
     * pane window closes with its last tab. While one of its tabs is pinned or being closed, nothing moves.
     *
     * @param window a pane window
     * @return the window the panes went to; empty when they stayed
     */
    public Optional<JasperWindow> bringBack(WindowContent window) {
        requireEdt();
        if (window == null || !window.mayBringBack()) return Optional.empty();
        List<Tab> moving = window.tabs();
        Origin origin = window.origin().get();
        Layout panes = window.workspace().model();
        JasperWindow target = holderOf(origin.parentTab()).orElse(null);
        ParentTabId parentTab = origin.parentTab();
        if (target == null) {
            target = ensureMain("menu", null).orElse(null);
            if (target == null) return Optional.empty();
            parentTab = target.content().activeParentTab();
        }
        Layout before = target.content().parentTabs().view(parentTab).orElseThrow().model();
        GroupId beside = before.group(origin.pane()).isPresent() ? origin.pane() : before.activeGroup();
        target.content().receive(moving, parentTab, 0, before.graft(panes, beside, origin.side(), origin.share()));
        return Optional.of(target);
    }

    /**
     * Move to Main Window (parent tabs spec 5; workspace plan 7b Rulings 12): a full window's parent tabs join the main
     * window's bar after its active parent tab, in their order, each with its id and layout; the parent tab the full window
     * showed shows there, the main window comes to the front, and the full window closes with its last tab. While one of
     * its tabs is pinned or being closed, nothing moves.
     *
     * @param window a full window
     * @return the main window; empty when nothing moved
     */
    public Optional<MainWindow> moveToMainWindow(WindowContent window) {
        requireEdt();
        if (window == null || !window.mayMoveToMainWindow()) return Optional.empty();
        Optional<MainWindow> main = ensureMain("menu", null);
        if (main.isEmpty()) return main;
        WindowContent target = main.get().content();
        ParentTabId shown = window.activeParentTab();
        List<ParentTabId> moving = window.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList();
        int at = target.parentTabs().model().indexOf(target.activeParentTab()) + 1;
        Throwable failure = null;
        for (ParentTabId parentTab : moving) {
            try {
                moveParentTab(window, parentTab, target, at);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
            int placed = target.parentTabs().model().indexOf(parentTab);
            if (placed >= 0) at = placed + 1;
        }
        target.selectParentTab(shown);
        main.get().toFront();
        rethrow(failure);
        return main;
    }

    /** The main or full window holding the parent tab {@code id}, which follows it between windows (parent tabs spec 11). */
    private Optional<JasperWindow> holderOf(ParentTabId id) {
        for (JasperWindow candidate : windows())
            if (!candidate.content().isPaneWindow() && candidate.content().parentTabs().view(id).isPresent()) return Optional.of(candidate);
        return Optional.empty();
    }

    /**
     * Plan 5's tear-out: a new full child window takes {@code tab}, its content and session untouched, its workspace at
""", 1)])
PY
```

- [ ] **Step 5: Ledger row**

`BringBackTest` matches `ThemeMigrationCoverageTest`'s `CANDIDATE` pattern through its `new MainWindow[1]` and
`new ChildWindow[1]`, so it takes a row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "BringBackTest.java", "app.workspace", "BringBackTest", T + "BringBackTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7b Task 3; Pop Out Parent Tab, Bring Back and Move to Main Window")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`BringBackTest` 11; every other workspace test unchanged in number). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Pane windows: Pop Out Parent Tab, Bring Back and Move to Main Window

A parent tab pops out whole into a full window, and a full window's
parent tabs join the main window's bar after its active one, each
keeping its id and layout. Bring Back returns a pane window's whole
layout nested beside its origin pane, on its side with its share, in
whichever window now holds the origin's parent tab; beside that parent
tab's active pane when the pane is gone, beside the main window's
active pane when the parent tab is gone, and into the empty workspace
whole. The receiving window lands each moving tab in the chosen parent
tab through the registry, one TAB_MOVED each, then gives it the
planned layout at once; a window whose only parent tab moves whole to
another window takes a new id for its empty workspace. The commands
wait while a tab they would move is pinned or being closed.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The commands, the window menu and the menus that offer them

Spec 5, 6 and 8: Pop Out Tab, Pop Out Pane, Pop Out Parent Tab, Bring Back and Move to Main Window are commands with no
default keys; `move_tab_to_new_window` and `move_tab_to_main_window` go. A window's menu lists the entries for its kind,
and a tab's menu, a terminal's context menu, the parent-tab bar's menu and the palette offer the same commands, each for
what it was opened on (Rulings 13-15), each enabled only while every tab it would move may move (Rulings 7). Every
window the window manager opens reaches it through its content.

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java` (7a's span);
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java` (`:55-56`, `:120-122`);
  - `WindowCommands.java` (`:54-55`), `WindowChrome.java` (7a's `contextMenu` line);
  - `WindowContent.java` (`:73`, `:909-912`, 7a's `tabMenu`, `parentTabMenu` and `tabDrag`);
  - `WindowManager.java` (`open`, `:345`);
  - `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java` (7a's text).
- Test:
  - `jasper-app/src/test/java/dev/jasper/app/workspace/PopOutCommandsTest.java` (new);
  - `config/KeymapDefaultsTest.java` (7a's), `config/KeyBindingsTest.java`, `config/ConfigLoaderTest.java` (after 7a's
    `aRemovedGroupIdIsReportedAsAnUnknownAction`), `workspace/TabMenuTest.java`,
    `workspace/WindowManagerTest.java`, `workspace/TerminalContextMenuTest.java`, `workspace/ParentTabBarTest.java` (7a's),
    `shortcuthelp/ShortcutHelpTest.java` (7a's test) and `workspace/ShortcutEditorIntegrationTest.java` (a comment).
- Ledger: one row.

**Interfaces:**
- Consumes:
  - Tasks 2 and 3's `WindowManager.popOutPane`, `popOutParentTab`, `bringBack` and `moveToMainWindow`;
  - Tasks 2 and 3's `WindowContent.mayPopOutPane`, `mayPopOutParentTab`, `mayBringBack`, `mayMoveToMainWindow`,
    `isPaneWindow` and `paneCount`;
  - the registry's `moveToNewWindow`;
  - 7a's `parentTabOf`, `activeParentTab`, `closeParentTab` and `tabMenuItem`.
- Produces:
  - `ActionId.POP_OUT_TAB` (`pop_out_tab`), `POP_OUT_PANE` (`pop_out_pane`), `POP_OUT_PARENT_TAB` (`pop_out_parent_tab`),
    `BRING_BACK` (`bring_back`) and `MOVE_TO_MAIN_WINDOW` (`move_to_main_window`), with `MOVE_TAB_TO_NEW_WINDOW` and
    `MOVE_TAB_TO_MAIN_WINDOW` gone;
  - on `WindowContent`:
    - `void manager(WindowManager)`;
    - `void popOutTab(Tab)`, `void popOutPane(Tab)`, `void popOutParentTab(Tab)`, `void bringBack()` and `void
      moveToMainWindow()`;
    - `java.util.List<ActionId> windowMenuItems()` and `JPopupMenu windowMenu()`, which Task 5's ⋮ opens;
    - `moveToNewWindow(Tab)` and `moveToMainWindow(Tab)` go.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PopOutCommandsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 5, 6, 8 and 14: the pop-out commands, the window menu of each kind, and the menus that offer them. */
class PopOutCommandsTest {
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

    /** Spec 6 and workspace plan 7b Rulings 14: each kind of window has its entries, enabled as its actions are. */
    @Test void theWindowMenuOffersEachKindOfWindowItsEntries() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(main.windowMenuItems()).containsExactly(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB);
            assertThat(texts(main.windowMenu())).containsExactly("Pop Out Pane", "Pop Out Parent Tab");
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            assertThat(full.windowMenuItems()).containsExactly(ActionId.POP_OUT_PANE, ActionId.MOVE_TO_MAIN_WINDOW);
            full.updateActions();
            assertThat(full.action(ActionId.POP_OUT_PANE).isEnabled()).as("a full window's only pane stays").isFalse();
            full.newParentTab(HOME);
            assertThat(full.windowMenuItems())
                .containsExactly(ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.MOVE_TO_MAIN_WINDOW);
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            assertThat(pane.windowMenuItems()).containsExactly(ActionId.BRING_BACK);
            pane.split(Side.RIGHT);
            assertThat(pane.windowMenuItems()).containsExactly(ActionId.BRING_BACK, ActionId.POP_OUT_PANE);
            for (var window : List.of(main, full, pane)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_TAB).isEnabled()).isTrue();
            assertThat(main.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(main.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(full.action(ActionId.POP_OUT_PANE).isEnabled()).as("it has another pane now").isTrue();
            assertThat(full.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(full.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.POP_OUT_PANE).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isFalse();
            assertThat(pane.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
        });
        windows.launch();
    }

    /**
     * Workspace plan 7b Rulings 7 (the controller's ruling): while a tab they would move is pinned, Pop Out Pane, Pop Out
     * Parent Tab, Bring Back and Move to Main Window are disabled in the window menu, a tab's menu, the bar's menu and the
     * palette.
     */
    @Test void aPinnedTabDisablesTheCommandsThatWouldMoveIt() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            var full = windows.manager.openTerminalWindow(null).orElseThrow().content();
            full.newParentTab(HOME);
            Tab inMain = main.selectedTab();
            Tab inFull = full.selectedTab();
            List<Subscription> held = List.of(inMain.pin(), pane.selectedTab().pin(), inFull.pin());
            for (var window : List.of(main, pane, full)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_PANE).isEnabled()).isFalse();
            assertThat(main.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isFalse();
            assertThat(main.commands().find("pop_out_parent_tab").orElseThrow().action().isEnabled()).as("in the palette").isFalse();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isFalse();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            assertThat(main.windowMenu().getComponents()).as("the window menu").noneMatch(java.awt.Component::isEnabled);
            assertThat(item(pane.windowMenu(), "Bring Back").isEnabled()).isFalse();
            assertThat(item(main.tabMenu(inMain), "Pop Out Pane").isEnabled()).isFalse();
            assertThat(item(main.parentTabMenu(inMain), "Pop Out Parent Tab").isEnabled()).isFalse();
            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isFalse();
            held.forEach(Subscription::close);
            for (var window : List.of(main, pane, full)) window.updateActions();
            assertThat(main.action(ActionId.POP_OUT_PANE).isEnabled()).isTrue();
            assertThat(main.action(ActionId.POP_OUT_PARENT_TAB).isEnabled()).isTrue();
            assertThat(pane.action(ActionId.BRING_BACK).isEnabled()).isTrue();
            assertThat(full.action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            assertThat(item(full.tabMenu(inFull), "Move to Main Window").isEnabled()).isTrue();
        });
        windows.launch();
    }

    /** Spec 5: the actions pop out the active tab, pane and parent tab, and bring back or move a whole window. */
    @Test void theActionsPopOutBringBackAndMove() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            main.newTab(HOME);
            main.invoke(ActionId.POP_OUT_TAB);
            ChildWindow popped = windows.manager.children().getFirst();
            assertThat(popped.content().isPaneWindow()).isTrue();
            popped.content().invoke(ActionId.BRING_BACK);
            assertThat(popped.closed()).isTrue();
            assertThat(main.tabCount()).isEqualTo(2);
            main.invoke(ActionId.POP_OUT_PANE);
            ChildWindow pane = windows.manager.children().getFirst();
            assertThat(pane.content().tabCount()).as("the active pane: the tab that came back").isEqualTo(1);
            main.newParentTab(HOME);
            main.invoke(ActionId.POP_OUT_PARENT_TAB);
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().isPaneWindow()).isFalse();
            assertThat(main.parentTabCount()).isEqualTo(1);
            full.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(full.closed()).isTrue();
            assertThat(main.parentTabCount()).isEqualTo(2);
        });
        windows.launch();
    }

    /** Review Focus 4: a tab's menu acts on that tab's pane and parent tab, and the bar's menu on its own parent tab. */
    @Test void aTabsMenuAndTheBarsMenuActOnTheirOwnPaneAndParentTab() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            Tab left = main.selectedTab();
            main.split(Side.RIGHT);
            Tab right = main.selectedTab();
            var menu = main.tabMenu(left);
            assertThat(texts(menu)).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "---",
                "Close Tab", "Close Others", "Close Pane");
            item(menu, "Pop Out Pane").doClick();
            ChildWindow pane = windows.manager.children().getFirst();
            assertThat(pane.content().tabs()).as("the pane of the tab the menu was opened on").containsExactly(left);
            assertThat(main.tabs()).containsExactly(right);
            var first = main.activeParentTab();
            main.newParentTab(HOME);
            var bar = main.parentTabMenu(right);
            assertThat(texts(bar)).containsExactly("Pop Out Parent Tab", "---", "Close Parent Tab");
            item(bar, "Pop Out Parent Tab").doClick();
            ChildWindow full = windows.manager.children().getLast();
            assertThat(full.content().activeParentTab()).as("the parent tab the entry stands for").isEqualTo(first);
            assertThat(full.content().tabs()).containsExactly(right);
            assertThat(main.parentTabCount()).isEqualTo(1);
        });
        windows.launch();
    }

    /** Spec 6: a terminal's context menu offers them, so they work while a pane shows no strip. */
    @Test void aTerminalsContextMenuOffersPopOutAndTheWindowMenu() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            assertThat(texts(main.chrome().contextMenu())).endsWith("---", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab");
            main.newTab(HOME);
            var pane = windows.manager.popOutTab(main.selectedTab()).orElseThrow().content();
            assertThat(texts(pane.chrome().contextMenu())).endsWith("---", "Pop Out Tab", "Bring Back");
        });
        windows.launch();
    }

    /** Spec 8: the commands are in the palette under their ids; the old ids are gone, a clean break. */
    @Test void theCommandsAreInThePaletteUnderTheirIds() throws Exception {
        edt(() -> {
            var main = windows.main().content();
            for (String id : List.of("pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isPresent();
            for (String id : List.of("move_tab_to_new_window", "move_tab_to_main_window"))
                assertThat(main.commands().find(id)).as(id).isEmpty();
            assertThat(main.action(ActionId.POP_OUT_TAB).getValue(Action.NAME)).isEqualTo("Pop Out Tab");
            assertThat(main.action(ActionId.BRING_BACK).getValue(Action.NAME)).isEqualTo("Bring Back");
        });
        windows.launch();
    }
}
```

Then the tests the renamed ids, labels and menus touch:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "config/KeymapDefaultsTest.java", [(
    """        new Row("move_tab_to_new_window", KeyContext.GLOBAL, "none", "none"),                 // workspace tabs spec 11
        new Row("move_tab_to_main_window", KeyContext.GLOBAL, "none", "none"),                // workspace tabs spec 11
""", """        new Row("pop_out_tab", KeyContext.GLOBAL, "none", "none"),                            // parent tabs spec 8
        new Row("pop_out_pane", KeyContext.GLOBAL, "none", "none"),
        new Row("pop_out_parent_tab", KeyContext.GLOBAL, "none", "none"),
        new Row("bring_back", KeyContext.GLOBAL, "none", "none"),
        new Row("move_to_main_window", KeyContext.GLOBAL, "none", "none"),
""", 1)])
edit(T + "config/ConfigLoaderTest.java", [(
    "    @Test void invalidOrCollidingBindingsDefaultEntireMapAndPreserveOtherFields() {\n",
    """    /** Parent tabs spec 8: the old move ids are a clean break too, so each is an unknown action (workspace plan 7b). */
    @Test void aRemovedMoveIdIsReportedAsAnUnknownAction() {
        for (String id : new String[] {"move_tab_to_new_window", "move_tab_to_main_window"}) {
            var result = parse("[keybindings]\\n" + id + " = \\"cmd+w\\"");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().keybindings()).doesNotContainKey(id);
            assertDiagnostic(result, "keybindings." + id, 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));
        }
    }

    @Test void invalidOrCollidingBindingsDefaultEntireMapAndPreserveOtherFields() {
""", 1)])
edit(T + "config/KeyBindingsTest.java", [(
    "            ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_MAIN_WINDOW, ActionId.OPEN_FILE,\n",
    "            ActionId.POP_OUT_TAB, ActionId.POP_OUT_PANE, ActionId.POP_OUT_PARENT_TAB, ActionId.BRING_BACK,\n"
    "            ActionId.MOVE_TO_MAIN_WINDOW, ActionId.OPEN_FILE,\n", 1)])
W = T + "workspace/"
edit(W + "TabMenuTest.java", [
    ('            assertThat(titles(menu)).containsExactly("Rename Tab", "Move to New Window", "Close Tab", "Close Others", "Close Pane");\n',
     '            assertThat(titles(menu)).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "Close Tab",\n'
     '                "Close Others", "Close Pane");\n', 1),
    ('            assertThat(titles(hosted)).containsExactly("Move to New Window", "Close Tab", "Close Others", "Close Pane", "Save");\n',
     '            assertThat(titles(hosted)).containsExactly("Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab", "Close Tab", "Close Others",\n'
     '                "Close Pane", "Save");\n', 1)])
edit(W + "WindowManagerTest.java", [(
    '            assertThat(titles).containsExactly("Rename Tab", "Move to Main Window", "Close Tab", "Close Others", "Close Pane");\n',
    '            assertThat(titles).containsExactly("Rename Tab", "Pop Out Tab", "Pop Out Pane", "Move to Main Window", "Close Tab",\n'
    '                "Close Others", "Close Pane");\n', 1)])
regex(W + "WindowManagerTest.java", r"\bMOVE_TAB_TO_NEW_WINDOW\b", "POP_OUT_TAB", 2)
regex(W + "WindowManagerTest.java", r"\bMOVE_TAB_TO_MAIN_WINDOW\b", "BRING_BACK", 3)
edit(W + "TerminalContextMenuTest.java", [(
    """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split Right", "Split Down",
                "Maximize Pane", "Close Tab");
""", """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split Right", "Split Down",
                "Maximize Pane", "Close Tab", "---", "Pop Out Tab", "Pop Out Pane", "Pop Out Parent Tab");
""", 1)])
edit(W + "ParentTabBarTest.java", [(
    """            var menu = owner.parentTabMenu(third);
            assertThat(Arrays.stream(menu.getComponents()).map(item -> ((JMenuItem) item).getText())).containsExactly("Close Parent Tab");
            ((JMenuItem) menu.getComponent(0)).doClick();
""", """            var menu = owner.parentTabMenu(third);
            // Workspace plan 7b Rulings 15: Pop Out Parent Tab, then a rule, before Close Parent Tab.
            assertThat(Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                .containsExactly("Pop Out Parent Tab", "Close Parent Tab");
            ((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick();
""", 1)])
edit(W + "ShortcutEditorIntegrationTest.java", [(
    "                    // The id: a search for the title also finds Move Tab to New Window, which sorts first.\n",
    "                    // The id: a search for the title also finds the other tab commands, which may sort first.\n", 1)])
edit(T + "shortcuthelp/ShortcutHelpTest.java", [
    ('        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1"))\n',
     '        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1", "pop_out_tab",\n'
     '                "pop_out_parent_tab"))\n', 1),
    ("""        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Panes"));
""", """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "pop_out_pane"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Panes"));
        // Workspace plan 7b Rulings 13: the window commands, with New Terminal Window and Close Window.
        for (String id : List.of("bring_back", "move_to_main_window", "new_terminal_window"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Application"));
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.PopOutCommandsTest'`
Expected: FAIL to compile: `cannot find symbol: variable POP_OUT_PANE`.

- [ ] **Step 3: The commands, their places and the window menu**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
W = A + "workspace/"
edit(A + "commands/ActionId.java", [(
    """    MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),
    MOVE_TAB_TO_MAIN_WINDOW("move_tab_to_main_window", "Move Tab to Main Window", "none", "none"),
""", """    POP_OUT_TAB("pop_out_tab", "Pop Out Tab", "none", "none"),
    POP_OUT_PANE("pop_out_pane", "Pop Out Pane", "none", "none"),
    POP_OUT_PARENT_TAB("pop_out_parent_tab", "Pop Out Parent Tab", "none", "none"),
    BRING_BACK("bring_back", "Bring Back", "none", "none"),
    MOVE_TO_MAIN_WINDOW("move_to_main_window", "Move to Main Window", "none", "none"),
""", 1)])
edit(W + "WorkspaceActions.java", [
    ("""            case MOVE_TAB_TO_NEW_WINDOW -> owner.moveToNewWindow(owner.selectedTab());
            case MOVE_TAB_TO_MAIN_WINDOW -> owner.moveToMainWindow(owner.selectedTab());
""", """            case POP_OUT_TAB -> owner.popOutTab(owner.selectedTab());
            case POP_OUT_PANE -> owner.popOutPane(owner.selectedTab());
            case POP_OUT_PARENT_TAB -> owner.popOutParentTab(owner.selectedTab());
            case BRING_BACK -> owner.bringBack();
            case MOVE_TO_MAIN_WINDOW -> owner.moveToMainWindow();
""", 1),
    ("""                    // A child window's only tab stays: that window already is its own (workspace tabs spec 6.5).
                    case MOVE_TAB_TO_NEW_WINDOW -> owner.selectedTab() != null && (owner.isMain() || owner.tabCount() > 1);
                    case MOVE_TAB_TO_MAIN_WINDOW -> owner.selectedTab() != null && !owner.isMain();
""", """                    // A child window's only tab, pane and parent tab stay: that window already is its own (workspace
                    // plan 7b Rulings 8).
                    case POP_OUT_TAB -> owner.selectedTab() != null && (owner.isMain() || owner.tabCount() > 1);
                    case POP_OUT_PANE -> owner.selectedTab() != null && owner.mayPopOutPane(owner.workspace().model().activeGroup());
                    case POP_OUT_PARENT_TAB -> owner.mayPopOutParentTab(owner.activeParentTab());
                    // A pane, a parent tab or a window moves whole or not at all (workspace plan 7b Rulings 7).
                    case BRING_BACK -> owner.mayBringBack();
                    case MOVE_TO_MAIN_WINDOW -> owner.mayMoveToMainWindow();
""", 1)])
edit(W + "WindowCommands.java", [(
    """                case MOVE_TAB_TO_NEW_WINDOW -> List.of("detach", "separate window", "child window");
                case MOVE_TAB_TO_MAIN_WINDOW -> List.of("attach", "main window");
""", """                case POP_OUT_TAB, POP_OUT_PANE -> List.of("detach", "separate window", "pane window");
                case POP_OUT_PARENT_TAB -> List.of("detach", "separate window", "full window");
                case BRING_BACK -> List.of("attach", "return", "dock", "origin");
                case MOVE_TO_MAIN_WINDOW -> List.of("attach", "main window", "merge");
""", 1)])
edit(A + "shortcuthelp/ShortcutCatalog.java", [
    ("""            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW,
                MOVE_TAB_TO_MAIN_WINDOW, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
""", """            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, POP_OUT_TAB,
                POP_OUT_PARENT_TAB, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
""", 1),
    ("                FOCUS_PANE_UP, FOCUS_PANE_DOWN -> \"Panes\";\n",
     "                FOCUS_PANE_UP, FOCUS_PANE_DOWN, POP_OUT_PANE -> \"Panes\";\n", 1)])
edit(W + "WindowChrome.java", [(
    "            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_TAB}) menu.add(owner.action(id));\n",
    """            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_TAB}) menu.add(owner.action(id));
        // Pop Out and the window menu's entries, which a pane showing no strip offers only here (parent tabs spec 6).
        menu.addSeparator();
        menu.add(owner.action(ActionId.POP_OUT_TAB));
        for (ActionId id : owner.windowMenuItems()) menu.add(owner.action(id));
""", 1)])
edit(W + "WindowManager.java", [(
    "            content.tabDrag(tabDrag);\n",
    "            content.tabDrag(tabDrag);\n            // Its pop-outs, Bring Back and Move to Main Window are this manager's (parent tabs spec 5).\n"
    "            content.manager(this);\n", 1)])
edit(W + "WindowContent.java", [
    ("    private Runnable bringToFront = () -> {};\n", """    private Runnable bringToFront = () -> {};
    /** The window manager that opened this window, which pops out, brings back and moves (parent tabs spec 5); else null. */
    private WindowManager manager;
""", 1),
    ("""    /** Move Tab to New Window (workspace tabs spec 6.5); the window manager decides whether it moves. */
    void moveToNewWindow(Tab tab) { if (tab != null) registry.moveToNewWindow(tab); }
    /** Move Tab to Main Window (workspace tabs spec 6.5). */
    void moveToMainWindow(Tab tab) { if (tab != null) registry.moveToMainWindow(tab); }
""", """    /** Pop Out Tab (parent tabs spec 5): the window manager decides whether {@code tab} moves (workspace plan 7b Rulings 8). */
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
    void moveToMainWindow() { if (manager != null) manager.moveToMainWindow(this); }
""", 1),
    ("""    /**
     * A tab's menu (workspace tabs spec 11): Rename Tab for a terminal tab, Move to New Window in the main window or Move
     * to Main Window in a child window, Close Tab, Close Others and Close Pane, then the content's commands.
     */
""", """    /**
     * A tab's menu (workspace tabs spec 11; parent tabs spec 6; workspace plan 7b Rulings 15): Rename Tab for a terminal tab,
     * Pop Out Tab and the window menu's entries for this tab's pane and parent tab, Close Tab, Close Others and Close Pane,
     * then the content's commands.
     */
""", 1),
    ("""        if (isMain()) menu.add(tabMenuItem("Move to New Window", ActionId.MOVE_TAB_TO_NEW_WINDOW, () -> moveToNewWindow(tab)));
        else menu.add(tabMenuItem("Move to Main Window", ActionId.MOVE_TAB_TO_MAIN_WINDOW, () -> moveToMainWindow(tab)));
""", """        var popOut = tabMenuItem("Pop Out Tab", ActionId.POP_OUT_TAB, () -> popOutTab(tab));
        // A child window's only tab stays: that window already is its own (workspace plan 7b Rulings 8).
        popOut.setEnabled(isMain() || tabCount() > 1);
        menu.add(popOut);
        for (ActionId id : windowMenuItems()) menu.add(windowItem(id, tab));
""", 1),
    ("""    /**
     * A parent tab's menu in the bar (parent tabs spec 6 and 8; workspace plan 7a Rulings 16): Close Parent Tab, for the
     * parent tab {@code tab} shows as its active tab.
     */
    JPopupMenu parentTabMenu(Tab tab) {
        var menu = new JPopupMenu();
        parentTabOf(tab).ifPresent(parent ->
            menu.add(tabMenuItem("Close Parent Tab", ActionId.CLOSE_PARENT_TAB, () -> closeParentTab(parent))));
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }
""", """    /**
     * A parent tab's menu in the bar (parent tabs spec 6 and 8; workspace plan 7a Rulings 16, plan 7b Rulings 15): Pop Out
     * Parent Tab and Move to Main Window where the window menu has them, a rule, then Close Parent Tab, for the parent tab
     * {@code tab} shows as its active tab.
     */
    JPopupMenu parentTabMenu(Tab tab) {
        var menu = new JPopupMenu();
        parentTabOf(tab).ifPresent(parent -> {
            for (ActionId id : windowMenuItems())
                if (id == ActionId.POP_OUT_PARENT_TAB || id == ActionId.MOVE_TO_MAIN_WINDOW) menu.add(windowItem(id, tab));
            if (menu.getComponentCount() > 0) menu.addSeparator();
            menu.add(tabMenuItem("Close Parent Tab", ActionId.CLOSE_PARENT_TAB, () -> closeParentTab(parent)));
        });
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * The window menu's entries (parent tabs spec 6; workspace plan 7b Rulings 14): Pop Out Pane and Pop Out Parent Tab in
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

    /**
     * The ⋮'s menu (parent tabs spec 6): {@link #windowMenuItems()}, each enabled as its action is, for the active pane
     * and parent tab.
     *
     * @return the menu
     */
    JPopupMenu windowMenu() {
        updateActions();
        var menu = new JPopupMenu();
        for (ActionId id : windowMenuItems()) {
            var item = new JMenuItem(action(id));
            item.putClientProperty("html.disable", true);
            menu.add(item);
        }
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /** A menu entry for {@code id}, one of {@link #windowMenuItems()}, acting on {@code tab}'s pane or parent tab, or this window. */
    private JMenuItem windowItem(ActionId id, Tab tab) {
        Runnable run = switch (id) {
            case POP_OUT_PANE -> () -> popOutPane(tab);
            case POP_OUT_PARENT_TAB -> () -> popOutParentTab(tab);
            case BRING_BACK -> this::bringBack;
            case MOVE_TO_MAIN_WINDOW -> this::moveToMainWindow;
            default -> throw new IllegalArgumentException("Not a window menu entry: " + id);
        };
        var item = tabMenuItem(id.label(), id, run);
        // Enabled only while every tab the entry would move may move (workspace plan 7b Rulings 7).
        item.setEnabled(switch (id) {
            case POP_OUT_PANE -> parentTabs.groupOf(tab.id()).map(this::mayPopOutPane).orElse(false);
            case POP_OUT_PARENT_TAB -> parentTabOf(tab).map(this::mayPopOutParentTab).orElse(false);
            case BRING_BACK -> mayBringBack();
            case MOVE_TO_MAIN_WINDOW -> mayMoveToMainWindow();
            default -> false;
        });
        return item;
    }
""", 1),
    ("""    /** Shares the window manager's tab drag; a drag of this window's own ends first. */
""", """    /** The window manager that opened this window: its pop-outs, Bring Back and Move to Main Window go through it. */
    void manager(WindowManager value) { manager = Objects.requireNonNull(value, "value"); }

    /** Shares the window manager's tab drag; a drag of this window's own ends first. */
""", 1)])
PY
git grep -n --untracked -E 'MOVE_TAB_TO_(NEW|MAIN)_WINDOW|move_tab_to_(new|main)_window|Move Tab to (New|Main) Window|"Move to New Window"' -- jasper-app/src plugins jasper-sdk jasper-sdk-testkit
```

Expected: the `git grep` lists only `PopOutCommandsTest`'s two ids and the id line of
`ConfigLoaderTest`'s `aRemovedMoveIdIsReportedAsAnUnknownAction`, which pin the clean break, and the SDK's
`ContentTab` Javadoc, whose two methods begin "Move Tab to New Window:" and "Move Tab to Main Window:" (Rulings 18: the
SDK does not change).

- [ ] **Step 4: Ledger row**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "PopOutCommandsTest.java", "app.workspace", "PopOutCommandsTest", T + "PopOutCommandsTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7b Task 4; the pop-out commands and their menus")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --continue`
Expected: PASS (`PopOutCommandsTest` 6, `KeymapDefaultsTest` 5, `ConfigLoaderTest` +1; the other edited tests unchanged
in number). A test this plan
does not name that fails on a removed id or label is ported the same way and recorded in the banner. Then
`./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Pane windows: the pop-out commands and the window menu

Pop Out Tab, Pop Out Pane, Pop Out Parent Tab, Bring Back and Move to
Main Window are commands with no default keys, in the palette and the
Keymap; move_tab_to_new_window and move_tab_to_main_window are gone.
Each window lists its kind's entries for its window menu: Pop Out Pane
and Pop Out Parent Tab in the main window, Move to Main Window too in a
full window, Bring Back and Pop Out Pane in a pane window. A tab's
menu, a terminal's context menu and the parent-tab bar's menu offer
them for what they were opened on. Each is disabled while a tab it
would move is pinned or being closed.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Every window's ⋮

Spec 6: every window has a small toolbar ending in a vertical ⋮ that opens its window menu (Task 4's `windowMenu()`).
The main window's ⋮ ends its toolbar in both looks: the toolbar pins it, and its overflow button, now a horizontal
ellipsis, sits before it (Spec issues 3, the controller's ruling). A child window, full or pane, shows a ⋮ alone at the
trailing end of its title row, wherever Jasper draws one, and in a row of its own under a system title bar on macOS
(Rulings 16, 17). The tests that read the main toolbar count the new control (Rulings 20, Spec issues 2).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowMenuButton.java`
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java` (`:18-24`, `view`, `layoutLeading`'s
    placement, `doLayout`'s placement);
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (`buildUnifiedToolbar`, `:128-130`; before
    `separator`, `:163`);
  - `ClassicHeader.java` (`:26-37`, `:53-54`);
  - `WindowContent.java` (`mountChild`: its Javadoc, `:407-411`, the comment at `:434-436`, and `:446`; before
    `installMacTitleBand`'s Javadoc, `:459`);
  - `jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java` (`:43`, `:168`, `:293`);
  - `MacTitleBand.java` (`:26`, `:32`, `:37`, `:120`).
- Test:
  - `jasper-app/src/test/java/dev/jasper/app/workspace/WindowMenuButtonTest.java` (new);
  - `workspace/WindowToolbarTest.java` (`:174`, and a new test);
  - `platform/MacTitleBandTest.java`;
  - `workspace/MockUiTest.java`, `WindowChromeTest.java`, `WindowChromeContributionsTest.java`, `ClassicHeaderTest.java`,
    `EnterpriseWorkstationMockTest.java`, `WindowShellTest.java` and `ClassicWindowsTitleTest.java`.
- Ledger: two rows, and `WindowToolbar`'s row reworded.

**Interfaces:**
- Consumes: Task 4's `WindowContent.windowMenu()`; `WindowChrome.classicButton`'s styling; `NativeStyles.button`;
  `AppIcons.semantic("more.vertical")` for the ⋮ and `AppIcons.semantic("more")`, `IconRegistry`'s built-in
  horizontal ellipsis, for the overflow.
- Produces:
  - `static void WindowToolbar.pin(JComponent)`: a pinned control ends the row, after the overflow button, in both
    layouts;
  - `final class WindowMenuButton extends JButton` with `static final String NAME = "windowMenu"`, `static final String
    LABEL = "View Options"` and `WindowMenuButton(WindowContent)`;
  - `void WindowChrome.classicWindowMenu(WindowToolbar)` and `void WindowContent.showWindowMenu(JComponent)`;
  - `public void MacTitleBar.setTrailing(JComponent)` and `public void MacTitleBand.setTrailing(JComponent)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowMenuButtonTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayDeque;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 6: every window's ⋮, at the trailing end of the main window's toolbar or of a child window's title row. */
class WindowMenuButtonTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JComponent menuIn(Container parent) { return WindowTabsTest.named(parent, WindowMenuButton.NAME); }

    /**
     * Workspace plan 7b Rulings 16: the standard toolbar ends with the ⋮, after Settings, and it never gives way; on a
     * narrow toolbar the overflow, a horizontal ellipsis, sits right before it (Spec issues 3).
     */
    @Test void theStandardToolbarEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.chrome().windowToolbar();
            var buttons = java.util.Arrays.stream(toolbar.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button != toolbar.overflowButton()).toList();
            assertThat(buttons.getLast()).isInstanceOf(WindowMenuButton.class);
            JButton menu = buttons.getLast();
            assertThat(menu.getName()).isEqualTo("windowMenu");
            assertThat(menu.getToolTipText()).isEqualTo("View Options");
            assertThat(menu.getIcon()).isSameAs(AppIcons.semantic("more.vertical"));
            assertThat(buttons.get(buttons.size() - 2).getToolTipText()).startsWith("Settings");
            toolbar.setSize(130, 30);
            toolbar.doLayout();
            assertThat(toolbar.overflowButton().isVisible()).as("the other buttons give way").isTrue();
            assertThat(menu.isVisible()).as("the ⋮ never does").isTrue();
            assertThat(menu.getText()).as("and never shows a label").isNull();
            JButton overflow = toolbar.overflowButton();
            assertThat(overflow.getIcon()).as("a horizontal ellipsis").isSameAs(AppIcons.semantic("more"));
            for (var child : toolbar.getComponents()) {
                if (child == menu || !child.isVisible() || !(child instanceof JButton)) continue;
                assertThat(child.getX() + child.getWidth()).as("the ⋮ ends the row").isLessThanOrEqualTo(menu.getX());
                if (child != overflow)
                    assertThat(child.getX() + child.getWidth()).as("the overflow right before it").isLessThanOrEqualTo(overflow.getX());
            }
            menu.doClick();
            assertThat(menu.isShowing()).as("off screen, a click opens nothing").isFalse();
        });
    }

    /** Workspace plan 7b Rulings 16: the classic row ends with Settings…, then the ⋮, a 22 px classic icon button. */
    @Test void theClassicRowEndsWithSettingsThenTheWindowMenu() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicTabsTest.classic());
                var trailing = ((ClassicHeader) owner.chrome().composition()).trailing();
                assertThat(((ClassicToolbarButton) trailing.getComponent(0)).getClientProperty("label")).isEqualTo("Settings…");
                var menu = (ClassicToolbarButton) trailing.getComponent(1);
                assertThat(menu.getName()).isEqualTo("windowMenu");
                assertThat(menu.getText()).as("an icon button").isNull();
                assertThat(menu.getToolTipText()).isEqualTo("View Options");
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                assertThat(menu.getText()).as("labels never reach it").isNull();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 7b Rulings 17: on macOS the unified bar of a child window ends with its ⋮. */
    @Test void aChildWindowsMenuEndsTheUnifiedMacOsBar() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                true, WindowRole.CHILD);
            var root = new JRootPane();
            try (var bar = WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, true, false), true, title -> { })) {
                var menu = menuIn(bar);
                assertThat(menu).isInstanceOf(WindowMenuButton.class);
                root.setSize(800, 500);
                LayoutTestSupport.layoutTree(root);
                assertThat(menu.getX() + menu.getWidth()).as("8 from the window's edge").isEqualTo(bar.getWidth() - 8);
                assertThat(root.getJMenuBar()).as("the screen menu bar's menus").isSameAs(child.menuBar());
            }
        });
    }

    /** Workspace plan 7b Rulings 17: under the classic title on macOS a child window's band ends with its ⋮. */
    @Test void aChildWindowsMenuEndsTheClassicMacOsBand() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicMacTitleTest.look().chrome(), true,
                    WindowRole.CHILD);
                var root = new JRootPane();
                assertThat(WindowContent.mountChild(root, child, ClassicMacTitleTest.look(), true, title -> { })).isNull();
                var menu = menuIn(child.titleBand());
                assertThat(menu).isInstanceOf(WindowMenuButton.class);
                root.setSize(800, 500);
                LayoutTestSupport.layoutTree(root);
                assertThat(menu.getX() + menu.getWidth()).isEqualTo(800 - 8);
                assertThat(menu.getY() * 2 + menu.getHeight()).as("centred on the band").isEqualTo(MacTitleBand.HEIGHT);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Workspace plan 7b Rulings 17: on Windows and Linux a child window's menu bar holds a glue, then the ⋮. */
    @Test void aChildWindowsMenuEndsAMenuBarOfItsOwnOnWindowsAndLinux() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                false, WindowRole.CHILD);
            var root = new JRootPane();
            assertThat(WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, false, false), false, title -> { }))
                .isNull();
            var row = root.getJMenuBar();
            assertThat(row.getName()).isEqualTo("windowBar");
            assertThat(row.getComponents()).hasSize(2);
            assertThat(row.getComponent(0)).as("the glue FlatLaf's title pane puts the title in").isInstanceOf(Box.Filler.class);
            assertThat(row.getComponent(1)).isInstanceOf(WindowMenuButton.class);
            assertThat(root.getContentPane()).as("its workspace alone").isSameAs(child);
        });
    }

    /** Workspace plan 7b Rulings 17: on macOS under the system's title bar the ⋮ ends a row at the top of the window. */
    @Test void aChildWindowsMenuEndsARowOfItsOwnUnderMacOsSystemTitleBar() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD,
                true, WindowRole.CHILD);
            var root = new JRootPane();
            assertThat(WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, false, false), true, title -> { }))
                .isNull();
            assertThat(root.getJMenuBar()).as("the screen menu bar's menus").isSameAs(child.menuBar());
            var row = (JComponent) ((BorderLayout) child.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            assertThat(row.getName()).isEqualTo("windowBar");
            assertThat(((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST)).isInstanceOf(WindowMenuButton.class);
        });
    }
}
```

Then the band's own test, and the tests that read the main toolbar or a child window's title row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
W = T + "workspace/"
edit(T + "platform/MacTitleBandTest.java", [(
    "    @Test void fullScreenRemovesTheBandAndLeavingRestoresIt() {\n",
    """    /** Parent tabs spec 6: a child window's ⋮ sits at the band's trailing end, centred on it, and only one does. */
    @Test void aTrailingControlSitsAtTheBandsTrailingEnd() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        var control = new JPanel();
        control.setPreferredSize(new java.awt.Dimension(22, 22));
        band.setTrailing(control);
        band.setTrailing(new JPanel());
        assertThat(band.getComponents()).as("once only").containsExactly(control);
        root.setSize(800, 600); layout(root);
        assertThat(control.getBounds()).isEqualTo(new Rectangle(800 - 8 - 22, 3, 22, 22));
        assertThat(band.getBounds()).isEqualTo(new Rectangle(0, 0, 800, MacTitleBand.HEIGHT));
    }

    @Test void fullScreenRemovesTheBandAndLeavingRestoresIt() {
""", 1)])
edit(W + "WindowToolbarTest.java", [
    ('        assertThat(bar.overflowButton().getIcon()).isSameAs(dev.jasper.app.platform.AppIcons.semantic("more.vertical"));\n',
     "        // Workspace plan 7b Spec issues 3: a horizontal ellipsis, so it never looks like a window's ⋮.\n"
     '        assertThat(bar.overflowButton().getIcon()).isSameAs(dev.jasper.app.platform.AppIcons.semantic("more"));\n', 1),
    ("    @Test void labelsGiveWayBeforeAnyControlOverflowsAndComeBackWithRoom() {\n",
     """    /** Workspace plan 7b Rulings 16: a pinned control ends the row, and the overflow button sits right before it. */
    @Test void aPinnedControlEndsTheRowAfterTheOverflowButton() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        for (int i = 0; i < 6; i++) button(bar, action("Plugin " + i, calls), i);
        var pinned = new JButton("P");
        pinned.setPreferredSize(new Dimension(24, 24));
        WindowToolbar.pin(pinned);
        bar.add(pinned);
        bar.finish();
        for (int width : new int[] {1000, 200, 120}) {
            bar.setSize(width, 30); bar.doLayout();
            var overflow = bar.overflowButton();
            assertThat(overflow.isVisible()).as("overflowing at %d", width).isEqualTo(width < 1000);
            assertThat(pinned.isVisible()).isTrue();
            assertThat(pinned.getX() + pinned.getWidth()).as("the row's end at %d", width).isEqualTo(width - 8);
            if (overflow.isVisible()) assertThat(overflow.getX() + overflow.getWidth() + 4).as("right before it").isEqualTo(pinned.getX());
            for (var child : bar.getComponents())
                if (child != pinned && child != overflow && child.isVisible() && child instanceof JButton)
                    assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(overflow.isVisible() ? overflow.getX() : pinned.getX());
        }
        assertThat(bar.overflowMenu().getComponentCount()).as("it never moves into the overflow menu").isEqualTo(6);
    }

    @Test void labelsGiveWayBeforeAnyControlOverflowsAndComeBackWithRoom() {
""", 1)])
edit(W + "MockUiTest.java", [(
    "            assertThat(buttons).hasSize(7);\n",
    "            // Six buttons, the window's ⋮ (workspace plan 7b Rulings 20) and the overflow button.\n"
    "            assertThat(buttons).hasSize(8);\n", 1)])
edit(W + "WindowChromeTest.java", [
    ('                .containsExactly(null, null, "Split", null, null, null, null);\n',
     '                .containsExactly(null, null, "Split", null, null, null, null, null);\n', 2),
    # Rulings 16: the window's ⋮ comes after Settings in the row's order, before the overflow button finish() adds,
    # which lays out before it.
    ('            assertThat(shape).containsExactly("New tab", "New terminal window", "|", "Split", "Maximize pane", "(|)", "|", "Find", "Settings", "More actions");\n',
     "            // Workspace plan 7b Rulings 16: the window's ⋮ follows Settings; the overflow button, last in the row's order,\n"
     "            // lays out before the ⋮.\n"
     '            assertThat(shape).containsExactly("New tab", "New terminal window", "|", "Split", "Maximize pane", "(|)", "|", "Find", "Settings",\n'
     '                "View Options", "More actions");\n', 1),
    ("            var settings = toolbar.getComponent(toolbar.getComponentCount() - 2);\n",
     "            var settings = toolbar.getComponent(toolbar.getComponentCount() - 3);\n", 1)])
edit(W + "WindowChromeContributionsTest.java", [(
    '            assertThat(after).endsWith("Maximize pane", "Run Tool", "Tool", "Find", "Settings", "More actions");\n',
    '            assertThat(after).endsWith("Maximize pane", "Run Tool", "Tool", "Find", "Settings", "View Options", "More actions");\n', 1)])
edit(W + "ClassicHeaderTest.java", [(
    """                int settingsRight = classic.trailing().getX() + settings.getX() + settings.getWidth();
                assertThat(settingsRight).as("8 from the content edge").isEqualTo(1440 - 8);
""", """                int settingsRight = classic.trailing().getX() + settings.getX() + settings.getWidth();
                // Workspace plan 7b Rulings 20: the window's ⋮, a 22 px icon button 8 after Settings…, ends the row.
                var windowMenu = (ClassicToolbarButton) classic.trailing().getComponent(1);
                assertThat(classic.trailing().getX() + windowMenu.getX() + windowMenu.getWidth()).as("8 from the content edge")
                    .isEqualTo(1440 - 8);
                assertThat(settingsRight).as("Settings… 8 before the ⋮").isEqualTo(1440 - 8 - 22 - 8);
""", 1)])
edit(W + "EnterpriseWorkstationMockTest.java", [
    ("    static final int SHORTER = 2 * (34 - 28);\n", """    static final int SHORTER = 2 * (34 - 28);
    /**
     * The device pixels the window's ⋮ (a 22 px icon button) and its 8 px gap take after Settings…, which the mocks predate
     * (workspace plan 7b Rulings 20).
     */
    static final int WINDOW_MENU = 2 * (22 + 8);
""", 1),
    ('        MockProfile.fonts(what + "search field left", 2 * field.x + X, 2116 - 2 * (16 + 6), 4);\n',
     "        // Workspace plan 7b Rulings 20: the window's ⋮ after Settings… moves it and the field left of the mocks'.\n"
     '        MockProfile.fonts(what + "search field left", 2 * field.x + X, 2116 - 2 * (16 + 6) - WINDOW_MENU, 4);\n', 1),
    ("        var rendered = MockProfile.inks(render, new Rectangle(2722 - 44 - X, 112 - Y, 160 + 44, 64 - SHORTER), 12, 12);\n",
     "        var rendered = MockProfile.inks(render, new Rectangle(2722 - 44 - WINDOW_MENU - X, 112 - Y, 160 + 44, 64 - SHORTER), 12, 12);\n", 1),
    ("""        MockProfile.fonts(what + "Settings… starts", rendered.getLast().start() + X, settings.getFirst().start(), 4);
        assertThat(rendered.getLast().end() + X).as(what + "Settings… ends").isCloseTo(settings.getFirst().end(), within(4));
""", """        MockProfile.fonts(what + "Settings… starts", rendered.getLast().start() + X + WINDOW_MENU, settings.getFirst().start(), 4);
        assertThat(rendered.getLast().end() + X + WINDOW_MENU).as(what + "Settings… ends").isCloseTo(settings.getFirst().end(), within(4));
""", 1)])
edit(W + "WindowShellTest.java", [
    ("""    /** Workspace tabs spec 6.3: a title bar over the workspace alone; its menus only where macOS's screen menu bar shows them. */
    @Test void aChildWindowMountsItsWorkspaceUnderItsTabsTitleAndNoMenuRow() throws Exception {
""", """    /**
     * Workspace tabs spec 6.3 and parent tabs spec 6: a title bar ending in the window's ⋮ over the workspace alone; its
     * menus only where macOS's screen menu bar shows them.
     */
    @Test void aChildWindowMountsItsWorkspaceUnderItsTabsTitleWithItsWindowMenuAndNoMenuRow() throws Exception {
""", 1),
    ('            assertThat(plain.getJMenuBar()).as("no menu row in a child window").isNull();\n',
    "            // Workspace plan 7b Rulings 17: off macOS the root's menu bar is the window's ⋮ alone, after a glue.\n"
    '            if (System.getProperty("os.name").startsWith("Mac")) assertThat(plain.getJMenuBar()).as("no menu row in a child window").isNull();\n'
    '            else assertThat(plain.getJMenuBar().getName()).as("no menu row: the window\'s ⋮ alone").isEqualTo("windowBar");\n', 1)])
edit(W + "ClassicWindowsTitleTest.java", [
    ("""     * colour and holds the title alone, with no menu row, and in-window dialogs start below it.
""", """     * colour and holds the title and the window's ⋮ (parent tabs spec 6), with no menu row, and in-window dialogs start
     * below it.
""", 1),
    (
    '                assertThat(root.getJMenuBar()).as("no menu row in a child window").isNull();\n',
    '                assertThat(root.getJMenuBar().getName()).as("no menu row: the title pane embeds the window\'s ⋮ alone (workspace plan 7b)")\n'
    '                    .isEqualTo("windowBar");\n', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowMenuButtonTest'`
Expected: FAIL to compile: `cannot find symbol: class WindowMenuButton`.

- [ ] **Step 3: The ⋮ and where each window shows it**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowMenuButton.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JButton;

/**
 * A window's ⋮ (parent tabs spec 6), as IntelliJ's tool windows have one: it opens the window menu for the window's active
 * pane and parent tab ({@link WindowContent#windowMenu()}). The standard toolbar ends with one, styled as its overflow
 * button; a child window shows one alone, its compact toolbar, at the trailing end of its title row (workspace plan 7b
 * Rulings 16 and 17). A toolbar pins it ({@link WindowToolbar#pin}): it never moves into the overflow menu, which sits
 * before it, and never shows a label. Its size is fixed. EDT only.
 */
final class WindowMenuButton extends JButton {
    /** Its name, by which tests find it. */
    static final String NAME = "windowMenu";
    /** Its tooltip, as IntelliJ names the menu. */
    static final String LABEL = "View Options";
    /** Its side outside a toolbar, in logical pixels: IntelliJ's icon button. */
    private static final int SIDE = 22;

    WindowMenuButton(WindowContent owner) {
        super(AppIcons.semantic("more.vertical"));
        Objects.requireNonNull(owner, "owner");
        setName(NAME);
        // Named for the toolbar's lists; a toolbar applies no label to it in any mode.
        putClientProperty("label", LABEL);
        putClientProperty("keepLabel", false);
        setToolTipText(LABEL);
        getAccessibleContext().setAccessibleName(LABEL);
        setFocusable(false);
        WindowToolbar.pin(this);
        NativeStyles.installed().button(this, () -> ThemeZone.theme(this));
        addActionListener(event -> owner.showWindowMenu(this));
    }

    /** Square: the toolbar's button side in a toolbar, else IntelliJ's 22 px icon button. */
    @Override public Dimension getPreferredSize() {
        int side = getParent() instanceof WindowToolbar bar ? bar.buttonSide() : UIScale.scale(SIDE);
        return new Dimension(side, side);
    }

    /** As {@link #getPreferredSize()}: a title row's layout neither shrinks nor stretches it. */
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    /** As {@link #getPreferredSize()}: a title row's layout neither shrinks nor stretches it. */
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }
}
```

Then the toolbars, the title rows and the window's part:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
P = "jasper-app/src/main/java/dev/jasper/app/platform/"
edit(W + "WindowToolbar.java", [
    (" * into a live overflow menu behind a vertical ellipsis. A contributed view never does: it hides while the row is too\n"
     " * narrow for it. */\n",
     " * into a live overflow menu behind a horizontal ellipsis. A contributed view never does: it hides while the row is too\n"
     " * narrow for it. A pinned control ({@link #pin}), a window's ⋮, ends the row, after the overflow button. */\n", 1),
    ("""    private final JButton overflow = new JButton(AppIcons.semantic("more.vertical"));
""", """    /** Marks the control {@link #pin} pins. */
    private static final String PINNED = "Jasper.toolbarPinned";
    /** A horizontal ellipsis, so that it never looks like a window's ⋮ (workspace plan 7b Spec issues 3). */
    private final JButton overflow = new JButton(AppIcons.semantic("more"));
""", 1),
    ("""        component.putClientProperty(VIEW, Boolean.TRUE);
    }
""", """        component.putClientProperty(VIEW, Boolean.TRUE);
    }

    /**
     * Pins {@code control}, a button with no overflow entry, to the row's trailing end: the row always ends with it, the
     * overflow button just before it, as a window's ⋮ ends its toolbar (workspace plan 7b Rulings 16). One per row.
     */
    static void pin(JComponent control) { control.putClientProperty(PINNED, Boolean.TRUE); }

    /** The control {@link #pin} pinned in this row; null when there is none. */
    private Component pinned() {
        for (Component child : getComponents())
            if (placed(child) && Boolean.TRUE.equals(((JComponent) child).getClientProperty(PINNED))) return child;
        return null;
    }
""", 1),
    ("""        int x = padding;
        boolean previous = false, anyShown = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
""", """        int x = padding;
        boolean previous = false, anyShown = false;
        Component pin = pinned();
        for (Component child : getComponents()) {
            if (child == overflow || child == pin) continue;
""", 1),
    ("""        if (overflowing) overflow.setBounds(anyShown ? x + gap : x, (getHeight() - side) / 2, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }
""", """        if (overflowing) overflow.setBounds(anyShown ? x + gap : x, (getHeight() - side) / 2, side, side);
        else overflow.setBounds(0, 0, 0, 0);
        // A pinned control, the window's ⋮, ends the row after the overflow button (workspace plan 7b Rulings 16).
        if (pin != null) {
            int at = overflowing ? overflow.getX() + side + gap : previous ? x + gap : x;
            int height = pin instanceof ClassicToolbarButton icon && icon.getText() == null ? Math.min(side, icon.getPreferredSize().height) : side;
            pin.setVisible(true);
            pin.setBounds(at, (getHeight() - height) / 2, pin.getPreferredSize().width, height);
        }
    }
""", 1),
    ("""        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
""", """        boolean previousVisible = false;
        Component pin = pinned();
        for (Component child : getComponents()) {
            if (child == overflow || child == pin) continue;
""", 1),
    ("""        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }
""", """        // The overflow button follows the last control shown; a pinned control, the window's ⋮, ends the row after it
        // (workspace plan 7b Rulings 16). The totals above count the pinned control, so it ends where the row does.
        if (hidden) overflow.setBounds(pin == null ? Math.max(0, getWidth() - padding - side) : x, y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
        if (pin != null) pin.setBounds(hidden ? x + side + gap : x, y, pin.getPreferredSize().width, side);
    }
""", 1)])
edit(W + "WindowChrome.java", [
    ("""        addButton(ActionId.OPEN_SETTINGS, "settings");
        toolbar.finish();
""", """        addButton(ActionId.OPEN_SETTINGS, "settings");
        // The window's ⋮ ends the toolbar and never gives way to its overflow menu (parent tabs spec 6).
        toolbar.add(new WindowMenuButton(owner));
        toolbar.finish();
""", 1),
    ("    /** A toolbar rule; a classic one is a 1 px {@code border.window} line. */\n", """    /**
     * The classic row's ⋮ (parent tabs spec 6; workspace plan 7b Rulings 16): a 22 px classic icon button in {@code into}
     * that opens the window menu, with no label in any toolbar mode and no overflow entry.
     */
    void classicWindowMenu(WindowToolbar into) {
        var button = new ClassicToolbarButton(null, WindowMenuButton.LABEL, false, AppIcons.semantic("more.vertical"));
        button.setName(WindowMenuButton.NAME);
        button.putClientProperty("keepLabel", false);
        button.setText(null);
        WindowToolbar.pin(button);
        button.addActionListener(event -> owner.showWindowMenu(button));
        into.add(button);
    }

    /** A toolbar rule; a classic one is a 1 px {@code border.window} line. */
""", 1)])
edit(W + "ClassicHeader.java", [
    (""" * separated groups, the plugin group, then "Search everywhere" with the palette's keycap and "Settings…" at the trailing
 * edge, over a 1 px {@code border.window} rule. The row is window chrome under a native title, not a title-bar zone.
""", """ * separated groups, the plugin group, then "Search everywhere" with the palette's keycap, "Settings…" and the window's ⋮
 * at the trailing edge, over a 1 px {@code border.window} rule. The row is window chrome under a native title, not a
 * title-bar zone.
""", 1),
    ('    /** The trailing group: "Settings…". */\n', '    /** The trailing group: "Settings…", then the window\'s ⋮ (parent tabs spec 6). */\n', 1),
    ("""        chrome.classicButton(trailing, ActionId.OPEN_SETTINGS, "Settings…", "settings", 100);
        trailing.finish();
""", """        chrome.classicButton(trailing, ActionId.OPEN_SETTINGS, "Settings…", "settings", 100);
        chrome.classicWindowMenu(trailing);
        trailing.finish();
""", 1)])
edit(W + "WindowContent.java", [
    ("""     * the same menu model, while the look keeps menus there ({@code screenMenuBar}); a child window never shows a menu
     * row, and on Windows and Linux the title pane holds the title alone. Returns the unified bar, or null.
""", """     * the same menu model, while the look keeps menus there ({@code screenMenuBar}); a child window never shows a menu
     * row, and its title row ends with its ⋮ ({@code mountWindowMenu}): on Windows and Linux the title pane holds the
     * title and the ⋮. Returns the unified bar, or null.
""", 1),
    ("""            // FlatLaf's title pane holds the title alone, in the chrome's colours, as the main window's does; under a
            // classic title it is 28 px, as the main window's (plan 14 Rulings 13), and in-window dialogs and overlays
            // start below it (Rulings 19).
""", """            // FlatLaf's title pane holds the title and the window's ⋮ (mountWindowMenu), in the chrome's colours, as
            // the main window's holds its title; under a classic title it is 28 px, as the main window's (plan 14
            // Rulings 13), and in-window dialogs and overlays start below it (Rulings 19).
""", 1),
    ("        if (screenMenuBar) root.setJMenuBar(content.menuBar());\n",
     "        if (screenMenuBar) root.setJMenuBar(content.menuBar());\n        content.mountWindowMenu(root, bar);\n", 1),
    ("""    /**
     * The macOS classic title (plan 14 Rulings 7 and 8): IntelliJ Classic's band above the toolbar row, filled with the
""", """    /**
     * Puts a child window's ⋮, its compact toolbar (parent tabs spec 6; workspace plan 7b Rulings 17), at the trailing end
     * of its title row: the unified macOS bar's, or IntelliJ Classic's band on macOS. On Windows and Linux it ends a menu
     * bar of its own after a glue, which FlatLaf's title pane embeds with the title in the glue, and which is a row under
     * the system's title bar otherwise. On macOS under the system's title bar it ends a row at the top of the window.
     */
    private void mountWindowMenu(JRootPane root, MacTitleBar bar) {
        var button = new WindowMenuButton(this);
        if (bar != null) bar.setTrailing(button);
        else if (titleBand != null) titleBand.setTrailing(button);
        else if (!macOs) {
            var row = new JMenuBar();
            row.setName("windowBar");
            row.add(Box.createHorizontalGlue());
            row.add(button);
            root.setJMenuBar(row);
        } else {
            var row = new JPanel(new BorderLayout());
            row.setName("windowBar");
            row.setOpaque(false);
            row.add(button, BorderLayout.EAST);
            add(row, BorderLayout.NORTH);
        }
    }

    /** Opens {@link #windowMenu()} under {@code invoker}, a ⋮, while it shows (parent tabs spec 6). */
    void showWindowMenu(JComponent invoker) {
        if (closed || !invoker.isShowing()) return;
        windowMenu().show(invoker, 0, invoker.getHeight());
    }

    /**
     * The macOS classic title (plan 14 Rulings 7 and 8): IntelliJ Classic's band above the toolbar row, filled with the
""", 1)])
edit(P + "MacTitleBar.java", [
    ("    private JComponent header;\n",
     "    private JComponent header;\n    /** The control at the row's trailing end; null while it has none. */\n    private JComponent trailing;\n", 1),
    ("    /** How this title row looks; a workspace header keeps its own. */\n", """    /**
     * A control at the trailing end of a title row without a header, such as a child window's ⋮ (parent tabs spec 6):
     * centred on the row, clear of the window's edge, while the title stays centred on the window. Once only.
     *
     * @param component the control
     */
    public void setTrailing(JComponent component) {
        if (closed || header != null || trailing != null) return;
        trailing = java.util.Objects.requireNonNull(component, "component");
        add(component);
        revalidate(); repaint();
    }

    /** How this title row looks; a workspace header keeps its own. */
""", 1),
    ("""        title.setBounds(inset, Math.max(0, Math.min(getHeight() - height, center - height / 2)), Math.max(0, getWidth() - 2 * inset), height);
""", """        title.setBounds(inset, Math.max(0, Math.min(getHeight() - height, center - height / 2)), Math.max(0, getWidth() - 2 * inset), height);
        // The trailing control fits within the title's inset, which is at least 120 px.
        if (trailing != null) {
            Dimension size = trailing.getPreferredSize();
            int high = Math.min(size.height, getHeight());
            trailing.setBounds(Math.max(0, getWidth() - right - size.width), Math.max(0, Math.min(getHeight() - high, center - high / 2)),
                size.width, high);
        }
""", 1)])
edit(P + "MacTitleBand.java", [
    (" * fill it is given, with no rule, no children and no text. Full screen removes it, so the content meets the screen's\n",
     " * fill it is given, with no rule and no text, and holds no control but a child window's ⋮ at its trailing end (parent\n"
     " * tabs spec 6). Full screen removes it, so the content meets the screen's\n", 1),
    ("    public static final int HEIGHT = 28;\n", """    public static final int HEIGHT = 28;
    /** The trailing control's distance from the window's edge, in points, unscaled as the band is. */
    private static final int TRAILING_GAP = 8;
""", 1),
    ("    private boolean closed;\n", "    private boolean closed;\n    /** The control at the band's trailing end; null while it has none. */\n    private JComponent trailing;\n", 1),
    ("    @Override public Dimension getPreferredSize() { return new Dimension(0, HEIGHT); }\n", """    /**
     * A control at the band's trailing end, such as a child window's ⋮ (parent tabs spec 6), centred on the band and
     * 8 pt from the window's edge. Once only.
     *
     * @param component the control
     */
    public void setTrailing(JComponent component) {
        if (closed || trailing != null) return;
        trailing = Objects.requireNonNull(component, "component");
        add(component);
        revalidate();
        repaint();
    }

    @Override public void doLayout() {
        if (trailing == null) return;
        Dimension size = trailing.getPreferredSize();
        int height = Math.min(size.height, getHeight());
        trailing.setBounds(Math.max(0, getWidth() - TRAILING_GAP - size.width), (getHeight() - height) / 2, size.width, height);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(0, HEIGHT); }
""", 1)])
PY
```

- [ ] **Step 4: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); import ledger
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "WindowMenuButton.java", "app.workspace", "WindowMenuButton", T + "WindowMenuButtonTest.java",
           "New in workspace-tabs plan 7b Task 5; every window's view-options button")
ledger.add(T + "WindowMenuButtonTest.java", "app.workspace", "WindowMenuButtonTest", T + "WindowMenuButtonTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7b Task 5; the window's view-options button in each title row")
PY
```

`WindowToolbar`'s row names the overflow's old glyph; it now names the new one:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
edit("docs/design/theme-engine/migration.csv", [(
    "Task10a: existing responsive overflow requests more.vertical;",
    "Task10a: existing responsive overflow requests more (a horizontal ellipsis before a pinned window menu since workspace-tabs plan 7b);",
    1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WindowMenuButtonTest` 6, `MacTitleBandTest` +1, `WindowToolbarTest` +1; the edited tests unchanged in
number;
`EnterpriseWorkstationMockTest` passes where Plex resolves, as before). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Pane windows: every window's ⋮

The main window's toolbar ends with a ⋮ that opens the window menu:
after Settings in the standard toolbar, after Settings… as a classic
icon button under Enterprise. The toolbar pins it: it never moves into
the overflow menu, whose button, now a horizontal ellipsis, sits just
before it.
A child window shows a ⋮ alone at the trailing end of its title row:
the unified macOS bar, IntelliJ Classic's band, or a menu bar of its
own after a glue on Windows and Linux, which FlatLaf's title pane
embeds; under macOS's system title bar, a row at the top of the
window. The Enterprise mock test measures Settings… from the ⋮.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `README.md` (7a's shortcut paragraph);
  - `docs/app-architecture.md`: the windows paragraph (`:98-104`), the `layout` row (`:177`) and, in "Window chrome",
    7a's sentence (`:227`), the toolbar's groups (`:229`) and the overflow (`:231`), as 7a leaves them;
  - `docs/STATUS.md` (top), this plan's status banner (line 3).

- [ ] **Step 1: The documents**

The user guide for windows and tabs is `README.md`'s shortcut paragraph, and the architecture's window paragraph names
the window kinds; its `layout` row and "Window chrome" name the origin, the ⋮ and the overflow. Each edit is the smallest
that makes its sentence true (AGENTS.md "Documentation edits"). The plugin
documents (`jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`) stay as they are:
`moveToNewWindow()` still moves a tab to a new child window, a pane window (Rulings 18).

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7b"); from edits import edit
edit("README.md", [(
    "showing another keeps every shell running, and a parent tab closes with its last tab.",
    "showing another keeps every shell running, and a parent tab closes with its last tab. Pop Out Tab and Pop Out Pane (in "
    "a tab's menu, a terminal's context menu, each window's ⋮ and the command palette) move a tab or a pane into a pane "
    "window, which Bring Back returns as a split beside the pane it came from; Pop Out Parent Tab moves a parent tab into "
    "a full window, whose Move to Main Window joins its parent tabs to the main window's bar.", 1)])
edit("docs/app-architecture.md", [
    ("""workspace and closes with its last tab. Every outside request, a plugin's tab with no window and the menu bar item
""", """workspace and closes with its last tab. A child window is a full window, with parent tabs, or a pane window, whose
content has one layout and an `Origin` (layout). `WindowManager` pops tabs, panes and parent tabs out, brings a pane window
back and moves a full window's parent tabs to the main window: `WindowContent.receive` lands each tab through the tab
registry, then `WorkspaceView.rearrange` gives the parent tab its planned layout. Every outside request, a plugin's tab
with no window and the menu bar item
""", 1),
    ("their order of use, or a `PaneLayout`), parent tab,", "their order of use, or a `PaneLayout` with its `Origin`), parent tab,", 1),
    ("a child window has only its title bar above its parent tabs.",
     "a child window has only its title bar, which ends with the window's ⋮, above its parent tabs, or above the one "
     "workspace of a pane window, which has no parent-tab bar.", 1),
    ("pane, plugin, then Find/Settings groups; tooltips carry live shortcuts)",
     "pane, plugin, then Find/Settings groups, then the window's ⋮, `WindowMenuButton`; tooltips carry live shortcuts)", 1),
    ("the ⋮ menu; on Windows and Linux", "the overflow menu; on Windows and Linux", 1)])
PY
git diff --stat README.md docs
```

Expected: two files changed (`README.md` and `docs/app-architecture.md`).

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
Expected: BUILD SUCCESSFUL. Count the results:

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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above the entries
already there), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 7b: Pop Out, Bring Back and the window menu — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-19-workspace-7b-pop-out.md),
[spec](superpowers/specs/2026-10-19-jasper-parent-tabs-design.md)). Tabs, panes and parent tabs leave their window and
pane windows come back. The model gains `layout.Origin` (parent tab, pane, side, share), `Layout.origin` and
`Layout.graft`, and `PaneLayout` carries its origin. A pane window is a child window whose `WindowContent` the window
manager gives an origin while it is empty (`paneWindow`): one layout, no parent-tab bar in either look, New Tab stacking
in its pane, and `windowLayout()` a `PaneLayout`. `WindowManager` gains `popOutTab`, `popOutPane`, `popOutParentTab`,
`moveParentTab`, `bringBack` and `moveToMainWindow`. Every move goes through the tab registry one tab at a time (one
`TAB_MOVED` each, sessions untouched): `WindowContent.receive` lands the tabs in the chosen parent tab, then
`WorkspaceView.rearrange` gives it the planned layout. Bring Back follows the origin's parent tab into whichever window
holds it, falls back to that parent tab's active pane and then the main window's, ends a maximize, and fills an empty
main workspace. A window whose only parent tab moves whole to another window takes a new id for its empty workspace
(`ParentTabsView.renew`). Pop Out Pane, Pop Out Parent Tab, Bring Back and Move to Main Window are disabled while a tab
they would move is pinned or being closed (`mayBringBack`, `mayMoveToMainWindow`). Commands `pop_out_tab`,
`pop_out_pane`, `pop_out_parent_tab`, `bring_back` and `move_to_main_window`, no keys, replace `move_tab_to_new_window`
and `move_tab_to_main_window`; the registry's `moveToNewWindow` (and so `ContentTab.moveToNewWindow()`) pops a tab out;
plan 5's tear-out still opens a full window. Each window's ⋮ (`WindowMenuButton`; the classic row's icon button) opens
its window menu. A toolbar pins it (`WindowToolbar.pin`), and the overflow, now `IconRegistry`'s horizontal ellipsis
`more`, sits before it. The menu has Pop Out Pane and Pop Out Parent Tab in the main window, plus Move to Main Window in
a full window, Bring Back and Pop Out Pane in a pane window. A tab's menu, a terminal's context menu and the parent-tab
bar's menu offer the same commands for what they were opened on.
Rulings (plan, "Rulings"): <one clause each, 1 to 21>. Spec issues: <Spec issues 1-3 and the user's answers, or "open">.
Deviations: <none, or each with its reason>. Left for plan 7c:
- a drag onto another window's bar is `WindowManager.moveParentTab(from, id, to, index)`;
- a parent tab dragged outside every window is `popOutParentTab(window, id, bounds, cursor)`;
- an item torn out to a pane window is `popOutTab(tab, bounds, cursor)`, in place of `moveToNewWindow(Tab, Rectangle,
  Point)`.

For plans 6a and 6b: `windowLayout()` is a `PaneLayout` with its `Origin` for a pane window, and a restored pane
window becomes one through `WindowContent.paneWindow(Origin)` while it is empty, as `WindowManager.openChild` does. `./gradlew check --continue`:
**<counts>**. GUI checks for the user:
- the ⋮ at the end of the main window's toolbar, in the standard look (after Settings) and under Enterprise (after
  Settings…), and its menu;
- on a narrow standard window, the overflow's horizontal ellipsis ("More actions") just before the ⋮ (Spec issues 3);
- a child window's ⋮ at the end of its title row: macOS's unified bar, macOS under Enterprise's band, the Windows title
  pane (the title in the glue) in both looks, and the row under the system title bar in Compact and on Linux;
- Pop Out Tab and Pop Out Pane opening a pane window over the pane's place, on its display, with the focus in the tab;
- Bring Back of a pane window split further, landing nested beside its pane with the recorded share, its active tab
  focused;
- Bring Back after the origin parent tab popped out to a full window, after it closed, and into an empty main window;
- Pop Out Parent Tab and Move to Main Window keeping every shell running (a `top` stays current);
- the strip's tab menu, a terminal's context menu (on a pane showing no strip) and the parent-tab bar's menu acting on
  the tab they were opened on;
- a modal prompt in a tab blocking its pane's or parent tab's move.

Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for pane windows

The README says how a tab, a pane or a parent tab pops out and how a
pane window comes back; the architecture names the window kinds, the
moves and the window's menu; STATUS records plan 7b with its rulings,
the hand-offs to plans 7c and 6a and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- **Spec coverage.**
  - Section 2's decisions 3 to 5 and 7: pop-out and bring-back reachable without a strip (Tasks 4 and 5), a popped-out
    pane as a pane window that splits further and comes back nested (Tasks 2 and 3), a popped-out parent tab as a full
    window (Task 3), and every window's ⋮ (Task 5).
  - Section 4: pane windows and their origin (Tasks 1 and 2); ids unique across windows (Rulings 10); a pane window
    closing with its last tab (plan 4a's `movedOut`, exercised in Task 3).
  - Section 5's table row by row: Pop Out Tab and Pop Out Pane (Task 2); Pop Out Parent Tab, Bring Back with its
    fallbacks and Move to Main Window (Task 3). Sessions survive (`TAB_MOVED` and session checks in Tasks 2 and 3), with
    no motion. The drag rows are plan 7c's.
  - Section 6: the ⋮ in every window (Task 5); its entries by kind (Task 4, Rulings 14); the tab menu, the terminal's
    context menu, the bar's menu and the palette (Task 4).
  - Section 8: the pop-out row's ids with no keys, and the clean break (Task 4).
  - Section 10: pane windows are windows to plugins, and the SDK does not change (Rulings 18).
  - Section 11: a maximized parent tab, a gone origin pane, a parent tab that popped out, all in Task 3. Saving is plan
    6a's.
  - Section 14: the model (Task 1); the ⋮ menu's entries for each kind (Task 4); exactly one `TAB_MOVED` per tab moved
    between windows (Tasks 2 and 3); pop out and bring back keeping sessions and focus (Tasks 2 and 3). The GUI checks go
    to the user (Task 6).
- **Replay.** Every quoted anchor was matched at its stated count, in a replay of plan 7a's Tasks 1-6 and then this
  plan's Tasks 1-6, in order, on `git archive` copies of `bba5c472e` and `8e129ff34`, and again after the review's
  fixes on `8e129ff34`. Nothing was compiled or run.
- **Type consistency.** These names keep one name and one signature from the task that creates them to the last task
  that uses them:
  - `Origin(parentTab, pane, side, share)`, `PaneLayout(layout, origin)`, `Layout.origin(ParentTabId, GroupId)` and
    `Layout.graft(Layout, GroupId, Side, double)`;
  - `TabRegistry.movable(Tab)`;
  - on `WindowContent`: `paneWindow(Origin)`, `isPaneWindow()`, `origin()`, `originOf(GroupId, boolean)`,
    `popOutOrigin(Tab)`, `paneOnScreen(GroupId)`, `paneCount()`, `mayPopOutPane(GroupId)`,
    `mayPopOutParentTab(ParentTabId)`, `mayBringBack()`, `mayMoveToMainWindow()`, `receive(List<Tab>, ParentTabId, int,
    Layout)`, `manager(WindowManager)`, the five command methods, `windowMenuItems()`, `windowMenu()` and
    `showWindowMenu(JComponent)`;
  - `ParentTabsView.openParentTab(Tab, ParentTabId, int)`, `land(Tab, ParentTabId, int)` and `renew(ParentTabId)`;
  - `WorkspaceView.rearrange(Layout)`;
  - on `WindowManager`: `popOutTab(Tab)`, `popOutTab(Tab, Rectangle, Point)`, `popOutPane(WindowContent, GroupId)`,
    `popOutParentTab(WindowContent, ParentTabId)` and its four-argument form, `moveParentTab(WindowContent,
    ParentTabId, WindowContent, int)`, `bringBack(WindowContent)`, `moveToMainWindow(WindowContent)`, and
    `openChild(..., Origin)`;
  - `ActionId.POP_OUT_TAB`, `POP_OUT_PANE`, `POP_OUT_PARENT_TAB`, `BRING_BACK` and `MOVE_TO_MAIN_WINDOW`;
  - `WindowMenuButton.NAME` and `LABEL`, `WindowToolbar.pin(JComponent)`, `WindowChrome.classicWindowMenu(WindowToolbar)`,
    and `MacTitleBar.setTrailing` and `MacTitleBand.setTrailing`.
- **Tests that read laid-out strips.** The pretend screen of `TabDragWindowsTest` turns tab motion off already. The new
  tests read layouts and menus, not strips in motion.
