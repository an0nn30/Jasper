# Workspace tabs 7a: parent tabs Implementation Plan

**Status: executed on 2026-10-20** (`8e129ff34..05521e053`; the Vault dialog commits, Remote 0.15.0 and plans 7b and
7c's documents lie between; then its final fix wave, "Parent tabs: final review fixes"); see `docs/STATUS.md`. Deviations
from the plan's text:
`ParentTabs.java` got a ledger row (`new ArrayList` matches `CANDIDATE`); a new parent tab takes the window's theme and
glass; `ParentTabsView` keeps its views in bar order; a strip in a hidden parent tab runs no motion; pins for Rulings 3,
10 and 21; `MockUiTest` measures the content height relative to its own value; `WindowTabsTest` overrides
`select_tab_1`; strip tab tooltips carry no shortcut by default; `StripSource.closeName()` makes the bar's × read
"Close Parent Tab"; `ParentTabBarTest`'s search assertion names the first view's strip; tests pin a whole parent tab's
close and no motion in the bar. Task 6 also lists `new_tab_in_pane` and `select_parent_tab_1` among
`docs/configuration.md`'s action IDs and says in the README that Ctrl+PageUp and Ctrl+PageDown, disabled with one
parent tab, still do not reach a terminal's shell.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A window holds parent tabs, each an unnamed set of panes (the split grid of plans 3 to 5), and shows one at a
time: New Tab opens a parent tab, a bar above the workspace lists them (from two parent tabs in the standard look,
always under Enterprise), switching keeps every tab's component and session, a parent tab closes with its last tab, a
pane holding one tab shows no strip in the standard look, and the commands and keys of parent tabs spec 8 take their
places, with "pane" for a group in every label.

**Architecture:** The pure model joins `dev.jasper.app.layout`: `ParentTabId`, `ParentTab(id, Layout)` and
`ParentTabs` (the parent tabs in bar order, the active one, an order of use and the operations), and the sealed
`WindowLayout` naming a window's two content shapes (`ParentTabs`, and `PaneLayout` for the pane windows plan 7b
creates). In `dev.jasper.app.workspace` a `ParentTabsView` takes the terminal column's place in every window: one
`WorkspaceView` per parent tab, the hidden ones kept in the tree, invisible, with the model kept current from every
layout change; `WindowContent` routes attaching, detaching, selecting and closing through it, so the registry, the SDK,
single-instance requests and plan 5's drag see the window's tabs as before and land in the active parent tab. The look's
`TabStrip` reads a `StripSource` (`GroupView`, or the new `ParentTabBar`, which shows each parent tab as its active tab).
Headless tests drive windows, keys and menus directly; the bar's place and the keys in real windows are GUI checks.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md)
(sections 2, 3, 4, 7, 8, 10, 11, 12's first plan and 14), which amends and wins over
[`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1's decision 4, 6.5, 7, 9, 10, 11, 14 and 15 as amended). Section 12's first plan; plan 7b
(`2026-10-19-workspace-7b-pop-out.md`: Pop Out, Bring Back, the window menu) and plan 7c
(`2026-10-19-workspace-7c-parent-tab-drag.md`: parent-tab drags) build on it, then plans 6a and 6b are re-planned.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after `bba5c472e` ("Editor: the New Editor Window button wears the Editor's file glyph"; the
branch holds plans 1 to 5, the single-tab strip change `128a0ac89`, the Editor's toolbar button and the merge of local
`main` with the Enterprise series' plans 12 to 14, `23436efc3` to `94c91e6d2`). Every anchor below was matched at its
stated count, and every edit script of Tasks 1-6 replayed in order, on a `git archive` copy of `bba5c472e` (and, before
the merge was committed, on `7c477286d`; after the plan's review, also on `144ff89f1`, the Credential Vault dialog
commit, which edits no file this plan quotes); line numbers are `bba5c472e`'s. Nothing was compiled or run. Run every
command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message when that
text is not found the stated number of times: re-find the quoted text (a change since may have reworded it), adjust the
anchor, re-run, and record it in this plan's banner; never edit around a missing anchor. Later tasks anchor on text
earlier tasks of this plan wrote, and quote it as those tasks leave it.

**Hand-offs absorbed:** `.superpowers/sdd/single-tab-strip/progress.md` hands the minors of `128a0ac89`'s review to
this plan, which replaces that change's window-level rule with per-pane hiding. Each is handled here:
- `TabDragWindowsTest`'s class comment (`:47-48`), which names a window's only tab and leaves the mixed looks unsaid:
  Task 2 rewords it for a pane's only tab and says the mix of looks is the tests' own.
- `WindowTabMotionTest`'s vacuous `f.strip.isVisible()` assertion (`:67`, on a standalone strip that never hides): Task
  2 deletes it.
- `docs/app-architecture.md:227` ("each with its own tab strip"): Task 6 rewrites the sentence for parent tabs and
  hidden strips.
- No test of a split of one-tab panes collapsing to one pane: absorbed by Task 2's
  `WorkspaceViewTest.aPaneShowsItsStripFromTwoTabsWhateverTheOtherPanesHold`, where a pane that drops to one tab hides its
  strip and one that reaches two shows it.
- `ClassicTabsTest.theDefaultLookKeepsTheEqualTabs`'s `getHeight()` assertion: moot; that strip never shows, so its
  height stays zero, and its `isVisible()` false is the assertion that matters.
- No test of the source window's strip hiding after a cross-window move: re-handed to `7c`, whose moves between windows
  assert both windows' pane strips; the pane rule renders on every layout change, a move's detach included.
- No test of a torn-out window starting without a strip: re-handed to `7c`, where plan 5's tear-out makes a pane window.
- The residual hide motion (a hide with the group's tabs unchanged leaves an earlier close motion running): moot; under
  the pane rule a strip hides only when its own pane's tabs change, and that refresh runs while it is hidden, so it
  settles at once.
- Workspace tabs spec 11 and 12's unrecorded lone-tab exception: moot; parent tabs spec 7 records the pane rule, its
  section 11's last row supersedes the one-tab rule, and that spec wins.
- The single-tab strip STATUS entry's unwrapped long line (its counts and GUI check): Task 6 wraps it.

## Spec issues

1. **Spec 8's Windows and Linux keys for Select Parent Tab 1-9, Alt+1-9, are taken.** The tool windows own Alt+digit
   off macOS, as in IntelliJ: Remote's Toggle SSH hosts is Alt+1, Toggle SFTP Alt+2 and Transfers Alt+3
   (`plugins/remote/.../RemotePlugin.java:286`, `:290`, `ui/transfers/TransferUi.java:44`), and the stripes label a
   tool window "1: SSH hosts" from that digit (`workspace/StripeNumbers.java`). A built-in default wins over a plugin's:
   `KeyBindings.withExtensions` drops a plugin default whose stroke a built-in holds (`config/KeyBindings.java:177-186`,
   `DEFAULT_DROPPED`), so Remote's three would lose their keys, and `RemoteShortcutConfigTest` and
   `KeyBindingsContextTest:77` fail. Alt+digit is also readline's digit argument in the shell. **This plan moves today's
   Ctrl+1-9 from Select Tab to Select Parent Tab on Windows and Linux, as spec 8 moves today's ⌘1-9 on macOS**, so
   Select Tab 1-9 have no default on either platform (Rulings 13). If the user prefers GNOME Terminal's Alt+1-9, Remote's
   three toggles move first (a Remote change and version bump) and Tasks 4 and 6 change their key rows; nothing else
   depends on it.

Spec 4's "a parent tab's layout is empty only in the main window when it has one parent tab" needs the window's role,
which a value does not have: `ParentTabs` holds "only a window's only parent tab may be empty" and the window closes a
child window that empties (Rulings 6). Spec 8's ⌃⇥ for Next Tab on macOS is the one macOS default without ⌘; the keymap
test states it (Rulings 13). Spec 7 shows Enterprise's bar always, so an Enterprise window has two rows of classic tabs
where the Workstation mocks draw one; the mock test reads the pane's strip under the bar (Rulings 18).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (workspace tabs spec 4, 11; parent tabs spec 8): `close_group`, `maximize_group` and `focus_group_left`,
  `_right`, `_up`, `_down` become `close_pane`, `maximize_pane` and `focus_pane_*` with no alias; saved bindings for the
  old ids are reported as unknown keys, like any unknown key; Select Tab 1-9 lose their defaults; the window-level strip
  rule of `128a0ac89` goes. No shim, no migration, no "was removed" text.
- No motion: a parent tab, the bar and a pane's strip appear and disappear at once; the bar's entries never move.
- Sessions and content are never touched: switching, opening and closing parent tabs only show, hide, add and remove
  workspaces; a tab's component stays in its group's card area while its parent tab is hidden.
- The SDK does not change: no file under `jasper-sdk`, `jasper-sdk-testkit` or `plugins` changes; SDK 0.20.0 stays
  (plan 6a bumps to 0.21.0). Plugins see windows and tabs as before (parent tabs spec 10).
- `dev.jasper.app.layout` stays JDK-only and names no other Jasper package; no package gains an edge (`workspace`
  already names `layout`, `contributions`, `commands`).
- The theme-migration ledger: every new Java file under `jasper-app/src` matching `ThemeMigrationCoverageTest`'s
  `CANDIDATE` pattern gets a `docs/design/theme-engine/migration.csv` row in its task's commit, through `ledger.py`,
  `nonvisual-helper,none,none` (plan 4a Rulings 21). The `layout` files and `ParentTabsTest` match nothing and get none.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): windows are `WindowContent`s, with the window manager's stand-in frames
  where a test needs windows (`WindowManagerTest`) and stand-in frames on a pretend screen for drags (`TabDragTest`).

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan7a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan7a
cp build/plan5/edits.py build/plan5/ledger.py build/plan7a/ 2>/dev/null || echo "plan 5's helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan7a in place of build/plan3a"
ls build/plan7a/edits.py build/plan7a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add(path, owner, surface, evidence, disposition)`. Every script below runs as
`python3 -I - <<'PY'` from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan7a")`. Java
text inside a script is in triple-quoted strings.

## Review Focus

1. A hidden parent tab's last tab closing on its own (its shell exits under `terminal.on_exit`, a plugin closes its
   view): that parent tab goes, the shown one stays shown with its focus, and the bar loses an entry. Task 3:
   `ParentTabsViewTest.aHiddenParentTabsLastTabClosingLeavesTheShownOneAlone`.
2. The window's tab keys from inside a plugin's view: on Windows and Linux a scroll pane (the Editor's, a tool window's
   table) binds Ctrl+PageUp and Ctrl+PageDown itself, and a text area traverses focus on Ctrl+Tab on both platforms now.
   Next and Previous Parent Tab and Next Tab still run. Task 4:
   `TabShortcutsTest.tabAndParentTabKeysRunBeforeAPluginViewsOwnBindings`.
3. The main window's empty workspace: New Tab, a request and a plugin's tab fill its only parent tab rather than adding
   a second, empty one beside it; under Enterprise its bar shows only "+". Task 3:
   `ParentTabsViewTest.aParentTabGoesWithItsLastTabAndTheOneUsedBeforeItShows`; Task 4:
   `ParentTabCommandsTest.newTabInTheEmptyMainWorkspaceFillsItsOnlyParentTab`; Task 5:
   `ParentTabBarTest.enterprisesBarShowsNoEntryForTheEmptyMainWorkspace`.
4. A tab drag while the window's parent tab changes under it (Cmd+T mid-drag): the drag ends, nothing lands in the
   parent tab that now shows, the tab stays where it was. Task 3: `TabDragTest.aDragEndsWhenItsParentTabStopsShowing`.
5. Closing the shown parent tab while the focus is in a tool window or the palette: the parent tab used before it shows
   and the focus stays where it is; with the focus nowhere a person sees, the shown parent tab's tab takes it. Task 3:
   `ParentTabsViewTest.closingTheShownParentTabLeavesFocusThatIsElsewhereWhereItIs`.

## Verified facts

From the code at `bba5c472e` (see Workspace).

1. `WorkspaceView` (`workspace/WorkspaceView.java:42`) renders one `Layout`; every change goes through `apply`
   (`:207-217`), which calls `owner.workspaceChanged(boolean)` (`WindowContent.java:1146-1153`; its only caller);
   `render` (`:230-257`) hands each `GroupView.display` the window-level answer `owner.look().showsStrip(layout.tabs()
   .size())` (`:240`, from `128a0ac89`); `holdsFocus` (`:224-228`) is private; the empty main workspace shows
   `EmptyWorkspace` (`:245`).
2. `WindowContent` holds one `WorkspaceView workspace` (`:77`), built at `:322` and put at the centre of a non-opaque
   `terminalColumn` (`:324-327`), which `ToolWindowArea` takes (`:331`); a child window shows the same area
   (`:348`). `tabs()`, `tabCount()`, `selectedTab()` (`:1158-1161`) read that view; `workspace()` (`:1169`) returns it.
   `GraphiteChromeTest:26`, `:74` and `:106` and `WindowDragTest:289` walk `workspace().getParent()` to the terminal
   column and its host.
3. The registry-facing host (`WindowContent.workspaceHost`, `:1074-1078`) answers `groupOf` and `attachBeside` from
   that one view; `attach(Tab, boolean, GroupId, Side)` (`:1091-1118`) opens in the active group or beside a group and
   removes the tab again on a failure; `detach` (`:1125-1140`) ends a moving tab's maximize, removes it, and runs
   `onEmpty` when the workspace is empty and the tab did not move. `TabRegistry.groupOf` (`contributions/TabRegistry
   .java:152-155`) asks the host; `move(Tab, UUID)` and `move(Tab, UUID, GroupId, Side)` (`:386-413`) refuse a move within
   one window, and `transfer` (`:423-479`) selects the tab after it arrives; `TAB_MOVED` is reported only by
   `WindowTerminals.tabArrived` (`workspace/WindowTerminals.java:86-90`).
4. `WindowTerminals`' `WindowEntry` (`:31-32`), the SDK's `WindowHandle.tabs()` and `activeTab()`, read `owner.tabs()`
   and `owner.selectedTab()`. `TabHost` sets `lastInWindow` to `owner.tabCount() == 1` (`TabHost.java:39`).
   `WindowManager.movedOut` closes a child window whose `tabCount()` is 0 (`WindowManager.java:387-390`).
5. `WindowContent.newTab(Path)` (`:1296`) and `openTab(...)` open a terminal in the active group;
   `JasperApplication.openRest` opens each requested folder with `newTab` (`application/JasperApplication.java:963`);
   `WindowTerminals.openTab` (the SDK's `Terminals.openTab`) uses `openTab`; `TabRegistry.open` places
   `ACTIVE_GROUP` and `NEW_WINDOW` tabs with the host's `attach` and `SPLIT_RIGHT`/`SPLIT_DOWN` with `attachBeside(tab,
   null, side)` (`TabRegistry.java:246-250`), `NEW_WINDOW` in an empty child window the manager opens (`:239`).
6. The strips: `TabStrip` (`TabStrip.java:15`) is sealed over `WindowTabs` and `ClassicTabs`; both take a `GroupView`
   (`WindowTabs.java:30`, `:42-45`; `ClassicTabs.java:46`, `:52-54`) and call it 25 and 21 times as `group.`, among
   them `group.owner().lookAnimates()` once (`WindowTabs.java:82`); `WorkspaceChrome.tabs(GroupView)` (`:107-113`) makes
   them; `GroupView`'s strip methods are package-private (`GroupView.java:55-114`). `WindowTabs` is named `windowTabs`
   and its entries' buttons `select:<title>` and `close:<title>`; tests find them by name, depth first
   (`WindowTabsTest.named`, `:213-219`).
7. Keys: `ActionId` (`commands/ActionId.java:10-54`) gives `next_tab` ⌘⇧] / Ctrl+Tab, `previous_tab` ⌘⇧[ /
   Ctrl+Shift+Tab, `select_tab_1`-`9` ⌘1-9 / Ctrl+1-9. `KeyBindings.defaults` throws on a collision between built-ins
   (`KeyBindings.java:352-359`); an override that takes a built-in default's stroke is a collision too, so
   `ConfigLoaderTest`'s `next_tab = "cmd+}"` would collide with a ⌘⇧] default. `KeymapDefaultsTest`
   (`config/KeymapDefaultsTest.java:91-112`) requires ⌘ in every macOS default and Ctrl+Shift, Ctrl+digit or Ctrl+Tab in
   every other default.
8. `WindowContent.traversalShortcut` (`:660-680`), a `KeyEventDispatcher`, runs a root-bound shortcut before focus
   traversal when its key is a traversal key of the focus owner (Ctrl+Tab in a text area), and swallows its typed and
   released tail; any later key press clears `traversalTyped` first. Swing's `ScrollPane.ancestorInputMap` binds
   `ctrl PAGE_UP` and `ctrl PAGE_DOWN` to scrolling, and lists and tables bind them too (`javax/swing/plaf/basic/
   BasicLookAndFeel.java`), ahead of a root pane's `WHEN_IN_FOCUSED_WINDOW` bindings. A terminal asks the window first
   (`TerminalView` hands every key to its shortcut handler, `TabHost.java:141`). No bundled plugin default uses Ctrl+Tab,
   Ctrl+PageUp/PageDown or Ctrl+digit.
9. Menus and labels: File holds Close Group (`WindowChrome.java:81`), View Maximize Group (`:92`), the toolbar's button
   reads "Maximize group" (`:124`, `:481`), the Tab menu `GROUP_ITEMS` then `TAB_ITEMS` with Select Tab 1-9
   (`:437-449`), the terminal context menu Maximize Group (`:573`), the classic row Maximize (`ClassicHeader.java:46`);
   the tab menu's Close Group (`WindowContent.java:1198`); the palette's Maximize/Restore Group
   (`WindowCommands.java:141-142`); the Keymap's categories Tabs and Groups (`shortcuthelp/ShortcutCatalog.java:79-95`).
10. `TabDrag` reads `window.workspace()` for its drops (`TabDrag.java:263-269`, `:334-350`) and ends a drag whose tab is
    no longer in its press's group of that workspace (`inPlace`, `:221-224`); it never reads `WindowContent.tabs()`.
11. `EnterpriseWorkstationMockTest.theTabsAreTheMocks` reads the tab strip at a fixed row under the toolbar (`25 +
    TOOLBAR + 1`, three times, where `7c477286d` had `25 + 35`) and compares it with the mock's one row of tabs;
    `theRowsDownTheWindowAreTheMocks` compares the first six runs down column 2000 (menu, toolbar, tab strip and their
    rules).
12. Tests open tabs with `newTab(Path)` (stacking in the active group) far more often than with the `new_tab` action:
    `WindowTabsTest:24` and `WorkspaceTabsTest:228` invoke `NEW_TAB` and expect a tab in the active group; other
    `NEW_TAB` callers count `tabCount()`. `CommandPaletteShortcutsTest:50-60` and `TabShortcutsTest:20-56` select tabs
    with the primary modifier and a digit, and `WorkspaceViewTest.onlyTheActiveGroupsTabsShowTheirSelectTabShortcut`
    needs Select Tab 1's shortcut.
13. Splits whose panes hold one tab each, whose strips showed under the window-level rule: `WorkspaceViewTest`
    `twoGroupsRenderASplitWithAStripEach` and `groupsReportTheirStripAndCardRectangles`, `WorkspaceDropTest`
    `everyGroupThatShowsIsADropAreaWithoutTheDraggedTab`, `TabDragTest.aDropThatWouldChangeNothingShowsNothingAndDoesNothing`
    and `TabDragWindowsTest`'s `aTabDroppedIntoAnotherWindowsMaximizedGroupEndsThatMaximize` and
    `aTabDroppedIntoAnotherWindowsInactiveGroupLandsThereAndActivatesIt`; `TabDragTestSupport.entry` and `onTab` need a
    shown strip.

## Rulings

Record them in `docs/STATUS.md` (Task 6). "What — why".

1. **The model lives in `dev.jasper.app.layout`: `ParentTabId` (a UUID record like `GroupId`), `ParentTab(ParentTabId,
   Layout)`, `ParentTabs` (immutable, with `open`, `select`, `replace`, `remove`, `step` and lookups) and the sealed
   `WindowLayout` over `ParentTabs` and `PaneLayout(Layout)`; 7b gives `PaneLayout` its origin and creates pane windows**
   — spec 4's two content shapes, and the brief puts both in 7a; the layout package is the JDK-only home of the model.
2. **When the shown parent tab closes, the one used most recently before it shows; parent tabs never shown follow after
   it in the bar, then before it, nearest first** — a group's tabs follow the same rule (plan 2 Rulings 7), as VS Code
   focuses the most recently used editor after a close; one rule for every tab-like row.
3. **New Tab puts its parent tab at the end of the bar** — iTerm2, Windows Terminal and WezTerm open a new tab at the end.
4. **One `WorkspaceView` per parent tab, inside a `ParentTabsView` that takes the terminal column's place; hidden parent
   tabs stay in the tree, invisible, as a group's unselected tabs do** — spec 7 ("hidden parent tabs keep their
   components and sessions; nothing restarts"); the dock, palette, glass and tests find the workspace where they did
   (fact 2).
5. **`WindowContent.workspace()` is the shown parent tab's workspace; `tabs()`, `tabCount()`, `terminalTabs()` and so
   the SDK's `WindowHandle.tabs()` cover every parent tab, in bar order; `WindowContent.windowLayout()` gives the model**
   — plugins see windows and tabs as today (spec 10); a window's tabs are all of its tabs, which shell-exit, the window
   manager and the registry count (fact 4).
6. **A parent tab joins holding its first tab and leaves with its last; `ParentTabs` refuses an empty parent tab beside
   another, so the main window's empty workspace is its only parent tab, and a child window whose only parent tab
   empties closes as before** — spec 4's invariants; a value cannot know its window's role.
7. **`newTab(Path)`, `openTab`, `Terminals.openTab`, the registry's placements and single-instance requests open in the
   shown parent tab's active pane; only New Tab (`newParentTab`) makes a parent tab, and in the main window's empty
   workspace it fills the only parent tab** — spec 8, 10 and 11's single-instance row; the many callers of `newTab`
   (fact 12) keep their meaning.
8. **A group id names its parent tab: the window's `groupOf` and `attachBeside` look in every parent tab, so a tab placed
   beside a group of a hidden parent tab lands there, and the registry's selection then shows that parent tab** — the
   brief's "they know which parent tab holds a tab"; ids are unique across windows (spec 4).
9. **No tab moves between parent tabs of one window in 7a: the registry refuses a move within a window (fact 3), so
   opening, closing and switching parent tabs report no `TAB_MOVED`, and a move between windows still reports one** —
   spec 10's events; 7b and 7c move tabs between windows through the registry.
10. **A person's switch (the bar, a key, a menu, a tab's "go to") focuses the shown parent tab's active tab, as
    selecting a tab does; when the shown parent tab closes, the one after it takes the focus only if the focus was in
    the window's parent tabs or nowhere a person sees** — plan 3a's `holdsFocus` rule (now `WorkspaceView.holdsFocus(
    WindowContent, Component)`); a tool window or the palette keeps its focus.
11. **A pane shows its strip by its own tab count: under the equal tabs from two, under the classic tabs always;
    `WorkspaceChrome.showsStrip(count)` now counts a pane's tabs** — spec 7, superseding `128a0ac89`'s window count (spec
    11's last row).
12. **Labels say "Pane" for a group and "Parent Tab" for a parent tab: New Tab in Pane, Close Pane, Close Parent Tab,
    Maximize Pane and Restore Pane, Focus Pane Left/Right/Up/Down, Next Parent Tab, Previous Parent Tab, Select Parent
    Tab 1-9; New Tab and the toolbar's "New tab" keep theirs; the Keymap's Groups category becomes Panes and the
    parent-tab actions join Tabs** — spec 3 and 8 name both; code keeps `Group` and `Tab` (spec 3).
13. **Defaults: `new_tab` ⌘T / Ctrl+Shift+T (unchanged); `new_tab_in_pane` and `close_parent_tab` none; `close_pane`
    ⌥⌘W / Ctrl+Alt+Shift+W; `next_parent_tab` ⌘⇧] / Ctrl+PageDown; `previous_parent_tab` ⌘⇧[ / Ctrl+PageUp;
    `select_parent_tab_1`-`9` ⌘1-9 / Ctrl+1-9 (Spec issues 1); `next_tab` and `previous_tab` Ctrl+Tab and Ctrl+Shift+Tab
    on every platform; `select_tab_1`-`9` none** — spec 8; the keymap test admits Ctrl+Tab on macOS (macOS's Terminal
    and Safari switch tabs on it) and Ctrl+PageUp/PageDown elsewhere (GNOME Terminal's), and pins that no built-in takes
    the tool windows' digits (Spec issues 1).
14. **Next and Previous Parent Tab run from the window's key dispatcher before the focus owner's own bindings, as Ctrl+Tab
    already does; other keys keep today's order** — fact 8: otherwise a scroll pane keeps Ctrl+PageUp/PageDown wherever an
    editor or a table has the focus; GNOME Terminal and browsers switch tabs on them wherever the focus is.
15. **Menus: File reads New Tab, New Terminal Window, Open File…, Close Tab, Close Pane, Close Parent Tab, Close Window,
    …; the Tab menu reads the pane and prompt items, a rule, New Tab in Pane, Next Tab, Previous Tab, Rename Tab, a rule,
    Next Parent Tab, Previous Parent Tab, Select Parent Tab 1-9; Select Tab 1-9 leave the menu and stay in the palette and
    the Keymap; New Tab in Pane gets the "+" icon** — a menu shows the keys a person has; ⌘1-9 now select parent tabs.
16. **The bar: the look's strip over a `StripSource`, the interface `GroupView` and `ParentTabBar` implement; each parent
    tab shows as its active tab (title, icon, tooltip, the classic dot); the bar's entries never move and a press on one
    starts no drag (7c's); its "+" is New Tab, its × and its menu's Close Parent Tab close a parent tab with its tabs'
    guards, and its tooltips carry Select Parent Tab's shortcut; each classic "+" is named for what it opens (New Tab
    in the bar, New Tab in Pane in a pane's strip)** — spec 4 ("its active item's title"), 7 ("it reuses the look's
    `TabStrip`") and 6 (the bar's menu, which 7b fills with Pop Out Parent Tab and Move to Main Window); under
    Enterprise both "+" buttons show at once.
17. **The bar is the top row of the parent-tabs view, so it sits between docked tool windows, below the header in the
    main window and at the top of a child window's workspace; it shows from two parent tabs under the equal tabs and
    always under the classic tabs, where the main window's empty workspace has a bar with "+" alone** — spec 7; the
    exact place is a GUI check.
18. **`EnterpriseWorkstationMockTest.theTabsAreTheMocks` reads the pane's strip where it is (`mounted.firstTab().y`),
    under the bar** — the mocks draw one row of tabs; the pane's strip is still the mocks' (spec 7 adds the bar above it).
19. **The bar's strip is named `parentTabBar` and stays the parent-tabs view's last child, so a search by name or by
    child order meets a pane's strip first** — fact 6.
20. **New files take ledger rows `nonvisual-helper,none,none` with "New in workspace-tabs plan 7a Task N"** — plan 4a
    Rulings 21's convention.
21. **A split beside a hidden parent tab's terminal (SDK `PaneHandle.split`) opens there and does not show it** — the
    target is the plugin's choice (`openBeside` attaches beside the target's group in whichever parent tab holds it).

## File structure

- `dev.jasper.app.layout`: `ParentTabId`, `ParentTab`, `ParentTabs`, `WindowLayout`, `PaneLayout`, `package-info`,
  `Ids` (Task 1).
- `dev.jasper.app.workspace`: `WorkspaceView`, `WorkspaceChrome`, `GroupView`, `TabStrip`, `WindowTabs` (Task 2);
  `ParentTabsView` (new), `WorkspaceView`, `WindowContent`, `WindowCommandPalette` (Task 3); `WorkspaceActions`,
  `WindowCommands`, `WindowChrome`, `ClassicHeader`, `WindowContent`, `GroupView` (Task 4); `StripSource`,
  `ParentTabBar` (new), `ParentTabsView`, `GroupView`, `WindowTabs`, `ClassicTabs`, `TabStrip`, `WorkspaceChrome`,
  `WindowContent` (Task 5).
- `dev.jasper.app.commands`: `ActionId` (Task 4). `dev.jasper.app.shortcuthelp`: `ShortcutCatalog` (Task 4).
- Tests: new `ParentTabsTest` (Task 1), `ParentTabsViewTest` (Task 3), `ParentTabCommandsTest` (Task 4),
  `ParentTabBarTest` (Task 5); changed `WorkspaceViewTest`, `ClassicTabsTest`, `WorkspaceDropTest`, `TabDragTest`,
  `TabDragWindowsTest`, `WindowTabMotionTest` (Task 2); `WindowManagerTest`, `TabDragTest`, `JasperApplicationWindowsTest`
  (Task 3);
  `KeymapDefaultsTest`, `KeyBindingsTest`, `ConfigLoaderTest`, `TabShortcutsTest`, `CommandPaletteShortcutsTest`,
  `WindowTabsTest`, `WorkspaceTabsTest`, `WorkspaceViewTest`, `TabMenuTest`, `ShortcutHelpTest` and the renamed ids'
  and labels' tests (Task 4); `EnterpriseWorkstationMockTest`, `ClassicTabsTest` (Task 5).
- Documents (Task 6): `README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/sdk-architecture.md`, `docs/STATUS.md`; the ledger in Tasks 3-5.

## Before you start

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
git log --oneline -1 --grep "the New Editor Window button wears the Editor's file glyph"                     # bba5c472e Editor: the New Editor Window button wears the Editor's file glyph
grep -c 'boolean strips = owner.look().showsStrip(layout.tabs().size());' jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java  # 1
grep -c 'private final WorkspaceView workspace;' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java                          # 1
grep -c 'void workspaceChanged(boolean refocus) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java                         # 1
grep -c 'TabStrip tabs(GroupView group) {' jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java                               # 1
grep -c 'CLOSE_GROUP("close_group", "Close Group", "alt+cmd+w", "ctrl+alt+shift+w"),' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java  # 1
grep -c 'withDefaultBindings("cmd+alt+1", "alt+1")' plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java                           # 1
```

Every command prints the stated result. Each executed plan of this series added a
`docs/STATUS.md` entry at the top; this plan's goes above them. Create the two helper scripts (Global Constraints).

---

### Task 1: The parent-tabs model

Spec 4: a window's content is parent tabs with an active one (the main window and full windows) or one layout (a pane
window); a parent tab is an id and a layout; ids are unique; only a window's only parent tab may be empty. Pure values
beside `Layout`, with the operations the window will use (Rulings 1, 2, 3, 6). Nothing uses them yet.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/layout/ParentTabId.java`, `ParentTab.java`, `ParentTabs.java`,
  `WindowLayout.java`, `PaneLayout.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/package-info.java`, `Ids.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/ParentTabsTest.java`

**Interfaces:**
- Consumes: plan 2's `Layout` (`of`, `empty`, `open`, `select`, `split`, `remove`, `tabs`, `groups`, `groupOf`,
  `isEmpty`, `activeGroup`), `Group`, `GroupId`, `TabId`, `Side`, `Ids.uuid`.
- Produces: `public record ParentTabId(UUID value)` with `random()`, `parse(String)`, `toString()`;
  `public record ParentTab(ParentTabId id, Layout layout)`; `public sealed interface WindowLayout permits ParentTabs,
  PaneLayout` with `List<TabId> tabs()`; `public record PaneLayout(Layout layout) implements WindowLayout` (never
  empty); `public final class ParentTabs implements WindowLayout` with `ParentTabs(List<ParentTab>, ParentTabId)`,
  `static ParentTabs of(ParentTab)`, `List<ParentTab> parentTabs()`, `ParentTabId active()`, `ParentTab
  activeParentTab()`, `List<ParentTabId> recent()`, `Optional<ParentTab> parentTab(ParentTabId)`, `int
  indexOf(ParentTabId)`, `List<TabId> tabs()`, `boolean isEmpty()`, `Optional<ParentTabId> holding(TabId)`,
  `Optional<ParentTabId> holding(GroupId)`, `ParentTabs open(ParentTab, int)`, `ParentTabs select(ParentTabId)`,
  `ParentTabs replace(ParentTab)`, `ParentTabs remove(ParentTabId)`, `ParentTabId step(int)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/layout/ParentTabsTest.java`:

```java
package dev.jasper.app.layout;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Parent tabs spec 4 and 14: a window's parent tabs, their operations and invariants, and a window's two content shapes. */
class ParentTabsTest {
    private static final TabId A = TabId.random(), B = TabId.random(), C = TabId.random(), D = TabId.random();

    /** A parent tab of its own id holding {@code tabs} in one group, the first selected. */
    private static ParentTab parent(TabId... tabs) {
        Layout layout = Layout.of(tabs[0]);
        for (int i = 1; i < tabs.length; i++) layout = layout.open(tabs[i], layout.activeGroup(), i);
        return new ParentTab(ParentTabId.random(), layout.select(tabs[0]));
    }

    @Test void aWindowStartsWithOneParentTab() {
        ParentTab only = parent(A);
        ParentTabs tabs = ParentTabs.of(only);
        assertThat(tabs.parentTabs()).containsExactly(only);
        assertThat(tabs.active()).isEqualTo(only.id());
        assertThat(tabs.activeParentTab()).isEqualTo(only);
        assertThat(tabs.recent()).containsExactly(only.id());
        assertThat(tabs.tabs()).containsExactly(A);
        assertThat(tabs.isEmpty()).isFalse();
        assertThat(ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.empty())).isEmpty())
            .as("the main window's empty workspace").isTrue();
    }

    @Test void openPutsAParentTabAtItsPlaceAndShowsIt() {
        ParentTab first = parent(A), second = parent(B), third = parent(C);
        ParentTabs tabs = ParentTabs.of(first).open(second, 1).open(third, 0);
        assertThat(tabs.parentTabs()).containsExactly(third, first, second);
        assertThat(tabs.active()).isEqualTo(third.id());
        assertThat(tabs.recent()).containsExactly(third.id(), second.id(), first.id());
        assertThat(tabs.tabs()).as("every tab, parent tab by parent tab").containsExactly(C, A, B);
        assertThat(tabs.indexOf(first.id())).isEqualTo(1);
        assertThat(tabs.indexOf(ParentTabId.random())).isEqualTo(-1);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(parent(D), 4)).withMessageContaining("Index 4");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(first, 0)).withMessageContaining("twice");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(parent(A), 0)).as("a tab in two parent tabs")
            .withMessageContaining("Tab " + A);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(new ParentTab(ParentTabId.random(), first.layout()), 0))
            .as("a group in two parent tabs").withMessageContaining("Group");
    }

    @Test void onlyAWindowsOnlyParentTabMayBeEmpty() {
        ParentTab empty = new ParentTab(ParentTabId.random(), Layout.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabs.of(empty).open(parent(A), 1))
            .as("New Tab fills the empty workspace instead").withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(parent(A), empty), empty.id()));
        ParentTab first = parent(A);
        ParentTabs one = ParentTabs.of(first);
        assertThat(one.replace(new ParentTab(first.id(), first.layout().remove(A))).isEmpty()).as("its last tab closed").isTrue();
        ParentTabs two = one.open(parent(B), 1);
        assertThatIllegalArgumentException().isThrownBy(() -> two.replace(new ParentTab(first.id(), first.layout().remove(A))))
            .withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(), first.id())).withMessageContaining("at least one");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(first), ParentTabId.random()))
            .withMessageContaining("not in the window");
    }

    @Test void selectShowsAParentTabAndPutsItFirstInTheOrderOfUse() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), a.id());
        ParentTabs next = tabs.select(c.id());
        assertThat(next.active()).isEqualTo(c.id());
        assertThat(next.parentTabs()).as("the bar keeps its order").containsExactly(a, b, c);
        assertThat(next.recent()).containsExactly(c.id(), a.id(), b.id());
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.select(ParentTabId.random())).withMessageContaining("not in the window");
    }

    @Test void removingTheShownParentTabShowsTheOneUsedBeforeIt() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), a.id()).select(c.id()).select(b.id());
        ParentTabs without = tabs.remove(b.id());
        assertThat(without.parentTabs()).containsExactly(a, c);
        assertThat(without.active()).as("the one used before it").isEqualTo(c.id());
        assertThat(without.remove(a.id()).active()).as("removing a hidden one keeps the shown one").isEqualTo(c.id());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabs.of(a).remove(a.id())).withMessageContaining("only parent tab");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.remove(ParentTabId.random())).withMessageContaining("not in the window");
    }

    @Test void parentTabsNeverShownFollowAfterTheShownOneThenBeforeIt() {
        ParentTab a = parent(A), b = parent(B), c = parent(C), d = parent(D);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c, d), b.id());
        assertThat(tabs.recent()).containsExactly(b.id(), c.id(), d.id(), a.id());
        assertThat(tabs.remove(b.id()).active()).as("the next one, as a tab strip does").isEqualTo(c.id());
        assertThat(new ParentTabs(List.of(a, b, c, d), d.id()).remove(d.id()).active()).as("the last: the one before it")
            .isEqualTo(c.id());
    }

    @Test void stepGoesAlongTheBarAndWraps() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), c.id());
        assertThat(tabs.step(1)).isEqualTo(a.id());
        assertThat(tabs.step(-1)).isEqualTo(b.id());
        assertThat(tabs.step(3)).isEqualTo(c.id());
        assertThat(ParentTabs.of(a).step(1)).isEqualTo(a.id());
    }

    @Test void replaceGivesAParentTabItsNewLayoutAndLookupsFollowIt() {
        ParentTab a = parent(A), b = parent(B);
        ParentTabs tabs = new ParentTabs(List.of(a, b), b.id());
        Layout grown = a.layout().split(C, a.layout().activeGroup(), Side.RIGHT);
        ParentTabs next = tabs.replace(new ParentTab(a.id(), grown));
        assertThat(next.parentTab(a.id()).orElseThrow().layout()).isEqualTo(grown);
        assertThat(next.active()).as("the shown one stays").isEqualTo(b.id());
        assertThat(next.holding(C)).contains(a.id());
        assertThat(next.holding(grown.activeGroup())).contains(a.id());
        assertThat(next.holding(B)).contains(b.id());
        assertThat(next.holding(D)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.replace(new ParentTab(ParentTabId.random(), grown)))
            .withMessageContaining("not in the window");
    }

    @Test void equalityIgnoresTheOrderOfUse() {
        ParentTab a = parent(A), b = parent(B);
        ParentTabs plain = new ParentTabs(List.of(a, b), a.id());
        ParentTabs used = plain.select(b.id()).select(a.id());
        assertThat(used).isEqualTo(plain).hasSameHashCodeAs(plain);
        assertThat(used.recent()).containsExactly(a.id(), b.id());
        assertThat(plain.select(b.id())).isNotEqualTo(plain);
    }

    @Test void aWindowHoldsParentTabsOrOnePaneLayout() {
        WindowLayout full = ParentTabs.of(parent(A, B));
        WindowLayout pane = new PaneLayout(Layout.of(C));
        for (WindowLayout layout : List.of(full, pane)) {
            List<TabId> tabs = switch (layout) {
                case ParentTabs parents -> parents.parentTabs().getFirst().layout().tabs();
                case PaneLayout single -> single.layout().tabs();
            };
            assertThat(layout.tabs()).isEqualTo(tabs);
        }
        assertThat(full.tabs()).containsExactly(A, B);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneLayout(Layout.empty())).withMessageContaining("pane window");
    }

    @Test void aParentTabIdIsAUuidThatParsesBack() {
        ParentTabId id = ParentTabId.random();
        assertThat(ParentTabId.parse(id.toString())).isEqualTo(id);
        assertThat(id).isNotEqualTo(ParentTabId.random());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabId.parse("not an id"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.ParentTabsTest'`
Expected: FAIL to compile: `cannot find symbol: class ParentTab`.

- [ ] **Step 3: The values**

Create `jasper-app/src/main/java/dev/jasper/app/layout/ParentTabId.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A parent tab's identity (parent tabs spec 4), unique in the application.
 *
 * @param value the UUID
 */
public record ParentTabId(UUID value) {
    /** Checks the value is present. */
    public ParentTabId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static ParentTabId random() {
        return new ParentTabId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static ParentTabId parse(String text) {
        return new ParentTabId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/ParentTab.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;

/**
 * One parent tab (parent tabs spec 4): an unnamed set of panes, the groups of its {@link Layout}, under its own id.
 *
 * @param id the parent tab's id
 * @param layout its panes in splits, with their tabs
 */
public record ParentTab(ParentTabId id, Layout layout) {
    /** Requires both. */
    public ParentTab {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(layout, "layout");
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/WindowLayout.java`:

```java
package dev.jasper.app.layout;

import java.util.List;

/**
 * What a window holds (parent tabs spec 4): parent tabs with an active one, as the main window and full windows do, or
 * one layout, as a pane window does.
 */
public sealed interface WindowLayout permits ParentTabs, PaneLayout {
    /**
     * Every tab the window holds, in its order.
     *
     * @return the tabs
     */
    List<TabId> tabs();
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/PaneLayout.java`:

```java
package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;

/**
 * A pane window's content (parent tabs spec 4): one layout of panes, never empty, as a pane window closes with its last
 * tab.
 *
 * @param layout the panes
 */
public record PaneLayout(Layout layout) implements WindowLayout {
    /** Requires a layout holding at least one tab. */
    public PaneLayout {
        Objects.requireNonNull(layout, "layout");
        if (layout.isEmpty()) throw new IllegalArgumentException("A pane window holds at least one tab");
    }

    /** The layout's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        return layout.tabs();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/ParentTabs.java`:

```java
package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A window's parent tabs (parent tabs spec 4), as the main window and full windows hold them: the parent tabs in bar
 * order, the one that shows and their order of use. Immutable: each operation returns new parent tabs and rejects
 * arguments that would break an invariant with an {@link IllegalArgumentException}: a window holds at least one parent
 * tab, parent tab, group and tab ids are unique across its parent tabs, and only a window's only parent tab may be
 * empty, as the main window's empty workspace is (a child window closes instead). Equality covers the parent tabs and the
 * active one; the order of use only decides which parent tab shows after the shown one closes, and is not saved.
 */
public final class ParentTabs implements WindowLayout {
    private final List<ParentTab> parentTabs;
    private final ParentTabId active;
    private final List<ParentTabId> recent;

    /**
     * Parent tabs in bar order with {@code active} showing. Their order of use starts with {@code active}, then the
     * parent tabs after it in the bar, then those before it, nearest first, as a group's tabs do (workspace plan 2,
     * Rulings 7).
     *
     * @param parentTabs at least one, in bar order
     * @param active one of them
     * @throws IllegalArgumentException when an invariant does not hold
     */
    public ParentTabs(List<ParentTab> parentTabs, ParentTabId active) {
        this(parentTabs, active, List.of());
    }

    private ParentTabs(List<ParentTab> parentTabs, ParentTabId active, List<ParentTabId> previous) {
        this.parentTabs = List.copyOf(parentTabs);
        this.active = Objects.requireNonNull(active, "active");
        if (this.parentTabs.isEmpty()) throw new IllegalArgumentException("A window holds at least one parent tab");
        var ids = new HashSet<ParentTabId>();
        var groups = new HashSet<GroupId>();
        var tabs = new HashSet<TabId>();
        for (ParentTab parent : this.parentTabs) {
            if (!ids.add(parent.id())) throw new IllegalArgumentException("Parent tab " + parent.id() + " is in the window twice");
            if (parent.layout().isEmpty() && this.parentTabs.size() > 1)
                throw new IllegalArgumentException("Only a window's only parent tab may be empty: " + parent.id());
            for (Group group : parent.layout().groups())
                if (!groups.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in two parent tabs");
            for (TabId tab : parent.layout().tabs())
                if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two parent tabs");
        }
        if (!ids.contains(active)) throw new IllegalArgumentException("The active parent tab " + active + " is not in the window");
        var order = new LinkedHashSet<ParentTabId>();
        order.add(active);
        for (ParentTabId id : previous) if (ids.contains(id)) order.add(id);
        int at = indexOf(active);
        for (int i = at + 1; i < this.parentTabs.size(); i++) order.add(this.parentTabs.get(i).id());
        for (int i = at - 1; i >= 0; i--) order.add(this.parentTabs.get(i).id());
        this.recent = List.copyOf(order);
    }

    /**
     * A window's one parent tab, showing.
     *
     * @param parentTab the parent tab; its layout may be empty
     * @return the parent tabs
     */
    public static ParentTabs of(ParentTab parentTab) {
        return new ParentTabs(List.of(parentTab), parentTab.id());
    }

    /**
     * The parent tabs in bar order.
     *
     * @return the parent tabs
     */
    public List<ParentTab> parentTabs() {
        return parentTabs;
    }

    /**
     * The parent tab that shows.
     *
     * @return its id
     */
    public ParentTabId active() {
        return active;
    }

    /**
     * The parent tab that shows.
     *
     * @return the parent tab
     */
    public ParentTab activeParentTab() {
        return parentTabs.get(indexOf(active));
    }

    /**
     * The parent tabs' order of use, most recent first; not part of equality or the file.
     *
     * @return every parent tab's id, the active one first
     */
    public List<ParentTabId> recent() {
        return recent;
    }

    /**
     * The parent tab with this id.
     *
     * @param id the parent tab's id
     * @return the parent tab, or empty when the window does not have it
     */
    public Optional<ParentTab> parentTab(ParentTabId id) {
        int at = indexOf(id);
        return at < 0 ? Optional.empty() : Optional.of(parentTabs.get(at));
    }

    /**
     * Where a parent tab is in the bar.
     *
     * @param id the parent tab's id
     * @return its index, or -1 when the window does not have it
     */
    public int indexOf(ParentTabId id) {
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < parentTabs.size(); i++) if (parentTabs.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Every tab: the parent tabs in bar order, each parent tab's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        var tabs = new ArrayList<TabId>();
        for (ParentTab parent : parentTabs) tabs.addAll(parent.layout().tabs());
        return List.copyOf(tabs);
    }

    /**
     * Whether the window holds no tab: its only parent tab is empty, the main window's empty workspace.
     *
     * @return true for the empty workspace
     */
    public boolean isEmpty() {
        return parentTabs.size() == 1 && parentTabs.getFirst().layout().isEmpty();
    }

    /**
     * The parent tab holding a tab.
     *
     * @param tab the tab
     * @return its parent tab's id, or empty when the window does not hold it
     */
    public Optional<ParentTabId> holding(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        for (ParentTab parent : parentTabs) if (parent.layout().contains(tab)) return Optional.of(parent.id());
        return Optional.empty();
    }

    /**
     * The parent tab holding a group.
     *
     * @param group the group
     * @return its parent tab's id, or empty when the window does not hold it
     */
    public Optional<ParentTabId> holding(GroupId group) {
        Objects.requireNonNull(group, "group");
        for (ParentTab parent : parentTabs) if (parent.layout().group(group).isPresent()) return Optional.of(parent.id());
        return Optional.empty();
    }

    /**
     * Puts {@code parentTab} at {@code index} of the bar and shows it.
     *
     * @param parentTab a parent tab holding at least one tab, none of whose ids the window has
     * @param index 0 to the number of parent tabs
     * @return the new parent tabs
     * @throws IllegalArgumentException when an id repeats, the index is outside the bar or a parent tab would be empty
     *     beside another
     */
    public ParentTabs open(ParentTab parentTab, int index) {
        Objects.requireNonNull(parentTab, "parentTab");
        if (index < 0 || index > parentTabs.size())
            throw new IllegalArgumentException("Index " + index + " is outside 0 to " + parentTabs.size());
        var next = new ArrayList<>(parentTabs);
        next.add(index, parentTab);
        return new ParentTabs(next, parentTab.id(), recent);
    }

    /**
     * Shows a parent tab and puts it first in the order of use.
     *
     * @param id a parent tab of the window
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it
     */
    public ParentTabs select(ParentTabId id) {
        requireParentTab(id);
        return new ParentTabs(parentTabs, id, recent);
    }

    /**
     * Gives a parent tab a new layout, in its place.
     *
     * @param parentTab the parent tab with its new layout; its id is one of the window's
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it, an id repeats or it would be empty beside another
     */
    public ParentTabs replace(ParentTab parentTab) {
        Objects.requireNonNull(parentTab, "parentTab");
        int at = requireParentTab(parentTab.id());
        var next = new ArrayList<>(parentTabs);
        next.set(at, parentTab);
        return new ParentTabs(next, active, recent);
    }

    /**
     * Removes a parent tab. When it showed, the one used most recently before it shows (Rulings 2 of plan 7a).
     *
     * @param id a parent tab of the window, not its only one
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it, or it is the window's only parent tab
     */
    public ParentTabs remove(ParentTabId id) {
        int at = requireParentTab(id);
        if (parentTabs.size() == 1)
            throw new IllegalArgumentException("The window's only parent tab cannot be removed: the window closes instead");
        var next = new ArrayList<>(parentTabs);
        next.remove(at);
        ParentTabId heir = active.equals(id) ? recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow() : active;
        return new ParentTabs(next, heir, recent);
    }

    /**
     * The parent tab {@code delta} places along the bar from the one that shows, wrapping around (Next and Previous
     * Parent Tab).
     *
     * @param delta how far, negative for back
     * @return its id
     */
    public ParentTabId step(int delta) {
        return parentTabs.get(Math.floorMod(indexOf(active) + delta, parentTabs.size())).id();
    }

    /** Equal when the parent tabs, in order, and the active one are equal; the order of use is ignored. */
    @Override public boolean equals(Object other) {
        return other instanceof ParentTabs tabs && parentTabs.equals(tabs.parentTabs) && active.equals(tabs.active);
    }

    @Override public int hashCode() {
        return Objects.hash(parentTabs, active);
    }

    @Override public String toString() {
        return "ParentTabs[parentTabs=" + parentTabs + ", active=" + active + "]";
    }

    private int requireParentTab(ParentTabId id) {
        int at = indexOf(id);
        if (at < 0) throw new IllegalArgumentException("Parent tab " + id + " is not in the window");
        return at;
    }
}
```

- [ ] **Step 4: The package says what it holds**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
L = "jasper-app/src/main/java/dev/jasper/app/layout/"
edit(L + "package-info.java", [(
    " * {@code Layout}, and each group's region of the unit square. JDK-only: nothing from Swing, AWT or a file format;\n",
    " * {@code Layout}, each group's region of the unit square, and what a window holds (parent tabs spec 4): its parent\n"
    " * tabs ({@code ParentTabs}) or a pane window's one layout ({@code PaneLayout}). JDK-only: nothing from Swing, AWT or a\n"
    " * file format;\n", 1)])
edit(L + "Ids.java", [(
    "/** Parsing shared by {@link TabId} and {@link GroupId}. */",
    "/** Parsing shared by {@link TabId}, {@link GroupId} and {@link ParentTabId}. */", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*'`
Expected: PASS (`ParentTabsTest` 11; the other layout tests unchanged). Then `./gradlew :jasper-app:check`: PASS (the
architecture rule keeps `layout` JDK-only).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Parent tabs: the model

A window's parent tabs are a pure value beside the layout: a parent
tab is an id and a layout, and ParentTabs keeps them in bar order with
the one that shows and an order of use, opens, selects, replaces and
removes them, and steps along the bar, holding spec 4's invariants.
WindowLayout names what a window holds: parent tabs, or a pane
window's one layout.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: A pane's own tabs decide its strip

Spec 7: under the equal tabs a pane holding one tab shows no strip, whatever the window's other panes hold; the classic
tabs show every strip. This replaces `128a0ac89`'s window-level count (Rulings 11). Tests that split a window into
panes of one tab each and read or drag from their strips move to panes of two tabs, or to the panes' card areas
(fact 13).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` (`render`), `WorkspaceChrome.java`,
  `GroupView.java`, `TabStrip.java`, `WindowTabs.java` (Javadoc)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceViewTest.java`, `ClassicTabsTest.java`,
  `WorkspaceDropTest.java`, `TabDragTest.java`, `TabDragWindowsTest.java`, `WindowTabMotionTest.java`

**Interfaces:**
- Consumes: `WorkspaceChrome.showsStrip(int)` (`128a0ac89`), `GroupView.display(List<Tab>, Tab, boolean)`.
- Produces: `boolean WorkspaceChrome.showsStrip(int count)`, now a pane's tab count.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(T + "WorkspaceViewTest.java", [
    ("""    @Test void twoGroupsRenderASplitWithAStripEach() throws Exception {
""", """    /** Parent tabs spec 7: under the equal tabs a pane shows its strip from two tabs, whatever the other panes hold. */
    @Test void aPaneShowsItsStripFromTwoTabsWhateverTheOtherPanesHold() throws Exception {
        edt(() -> {
            var owner = window(3);
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            var left = workspace.model().activeGroup();
            workspace.moveToNewGroup(tabs.get(2), left, Side.RIGHT);
            var right = workspace.model().activeGroup();
            assertThat(workspace.groupView(left).strip().component().isVisible()).as("two tabs").isTrue();
            assertThat(workspace.groupView(right).strip().component().isVisible()).as("one tab, though the window holds three")
                .isFalse();
            workspace.moveTab(tabs.get(1), right, 1);
            assertThat(workspace.groupView(left).strip().component().isVisible()).as("one tab left").isFalse();
            assertThat(workspace.groupView(right).strip().component().isVisible()).as("two tabs now").isTrue();
            assertThat(((WindowTabs) workspace.groupView(right).strip()).motionRunning()).as("shown settled").isFalse();
        });
    }

    @Test void twoGroupsRenderASplitWithAStripEach() throws Exception {
""", 1),
    ("""            assertThat(workspace.groupViews()).as("a split holds two tabs: every group shows its strip")
                .allSatisfy(view -> assertThat(view.strip().component().isVisible()).isTrue());
""", """            assertThat(workspace.groupViews()).as("each pane holds one tab: no pane shows its strip (parent tabs spec 7)")
                .allSatisfy(view -> assertThat(view.strip().component().isVisible()).isFalse());
""", 1),
    ("""            var owner = window(2);
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            layout(owner);
            for (GroupView group : workspace.groupViews()) {
""", """            var owner = window(4);
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            workspace.moveToNewGroup(tabs.get(2), workspace.model().activeGroup(), Side.RIGHT);
            // Two tabs in each pane, so each shows its strip (parent tabs spec 7).
            workspace.moveTab(tabs.get(3), workspace.model().activeGroup(), 1);
            layout(owner);
            for (GroupView group : workspace.groupViews()) {
""", 1)])
edit(T + "ClassicTabsTest.java", [(
    """    @Test void oneTabStillShowsTheStrip() throws Exception {
""", """    /** Parent tabs spec 7: the classic tabs show every pane's strip, a pane of one tab's too. */
    @Test void everyPaneShowsItsClassicTabs() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.split(dev.jasper.app.layout.Side.RIGHT);
                assertThat(owner.workspace().groupViews()).hasSize(2)
                    .allSatisfy(group -> assertThat(group.strip().component().isVisible()).isTrue());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void oneTabStillShowsTheStrip() throws Exception {
""", 1)])
edit(T + "WorkspaceDropTest.java", [(
    "            assertThat(areas.get(1).tabs()).containsExactly(workspace.groupView(right).tabBounds(workspace).getFirst());\n",
    "            assertThat(areas.get(1).strip().isEmpty()).as(\"a pane holding one tab shows no strip (parent tabs spec 7)\").isTrue();\n"
    "            assertThat(areas.get(1).tabs()).isEmpty();\n", 1)])
edit(T + "TabDragTest.java", [(
    """            Layout split = workspace.model();
            drop(owner, c, inside(cards(owner, alone), 0.05, 0.5));
            assertThat(workspace.model()).as("an edge of the group it is alone in").isEqualTo(split);
""", """            // A pane's only tab shows no strip in the standard look (parent tabs spec 7): there is nothing to drag it from,
            // and an edge of its own pane would change nothing (WorkspaceDropTest.aDropThatWouldChangeNothingIsNoChange).
            assertThat(workspace.groupView(alone).strip().component().isVisible()).isFalse();
            assertThat(workspace.changes(c, new Drop.Edge(alone, Side.LEFT))).isFalse();
""", 1)])
edit(T + "TabDragWindowsTest.java", [
    ("            drop(child.content(), tab, onTab(to, right, 0.75));\n",
     "            // Each pane holds one tab, so neither shows a strip (parent tabs spec 7): the drop is on the shown pane's middle.\n"
     "            drop(child.content(), tab, inside(cards(to, maximized), 0.5, 0.5));\n", 1),
    ("""            // The registry's move puts it at the end of the active group, shown there; the drop then takes it on.
            drop(child.content(), tab, onTab(to, a, 0.25));
            var layout = to.workspace().model();
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(layout.group(left).orElseThrow().tabs()).as("before the group's tab").containsExactly(tab.id(), a.id());
""", """            // The registry's move puts it at the end of the active group, shown there; the drop then takes it on. The left
            // pane holds one tab, so it shows no strip (parent tabs spec 7): the drop is on its middle.
            drop(child.content(), tab, inside(cards(to, left), 0.5, 0.5));
            var layout = to.workspace().model();
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(layout.group(left).orElseThrow().tabs()).as("after the group's tab").containsExactly(a.id(), tab.id());
""", 1),
    (""" * window far away until a test places it. Under the standard look a window's only tab shows no strip (spec 1, decision
 * 4), so it has none to drag from or drop on; a test that drags a window's only tab gives its child window the classic
 * tabs, which always show.
""", """ * window far away until a test places it. Under the standard look a pane's only tab shows no strip (parent tabs spec 7),
 * so a window's only tab has none to drag from or drop on; a test that drags a window's only tab gives its child window
 * the classic tabs, which always show (a mix of looks only these tests make; the drag reads no look).
""", 1)])
# The motion fixture's strip is a standalone one that never hides: its visibility says nothing about a window's strip.
edit(T + "WindowTabMotionTest.java", [(
    "            assertThat(f.strip.isVisible()).as(\"its group view, not the strip, hides it for a window's only tab\").isTrue();\n",
    "", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceViewTest' --tests 'dev.jasper.app.workspace.WorkspaceDropTest' --tests 'dev.jasper.app.workspace.TabDragTest' --continue`
Expected: FAIL: `aPaneShowsItsStripFromTwoTabsWhateverTheOtherPanesHold` ("one tab, though the window holds three"),
`twoGroupsRenderASplitWithAStripEach`, `everyGroupThatShowsIsADropAreaWithoutTheDraggedTab` and
`aDropThatWouldChangeNothingShowsNothingAndDoesNothing`, each where a pane of one tab still shows its strip.

- [ ] **Step 3: The pane's count, and the Javadoc that named the window's**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WorkspaceView.java", [(
    """        // Every change to the window's tab count renders here, so the strips follow it in the same turn.
        boolean strips = owner.look().showsStrip(layout.tabs().size());
        for (Group group : layout.groups())
            groups.get(group.id()).display(group.tabs().stream().map(tabs::get).toList(), group.selected().map(tabs::get).orElse(null), strips);
""", """        // Every change to a pane's tab count renders here, so its strip follows it in the same turn (parent tabs spec 7).
        for (Group group : layout.groups())
            groups.get(group.id()).display(group.tabs().stream().map(tabs::get).toList(), group.selected().map(tabs::get).orElse(null),
                owner.look().showsStrip(group.tabs().size()));
""", 1)])
edit(W + "WorkspaceChrome.java", [(
    """     * Whether a group shows its strip in a window holding {@code count} tabs (workspace tabs spec 1, decision 4): the
     * classic tabs always show; the equal tabs show unless the window holds a single tab.
""", """     * Whether a pane holding {@code count} tabs shows its strip (parent tabs spec 7): the classic tabs always show; the
     * equal tabs show unless the pane holds a single tab.
""", 1)])
edit(W + "GroupView.java", [(
    " * group's selected tab. The strip shows unless its workspace hides it (spec 1, decision 4: a window's single tab in the\n",
    " * group's selected tab. The strip shows unless its workspace hides it (parent tabs spec 7: a pane's single tab in the\n", 1)])
edit(W + "TabStrip.java", [(
    " * their window holds a single tab (spec 1, decision 4), or the classic tabs, always shown. It lists, selects, closes and\n",
    " * their pane holds a single tab (parent tabs spec 7), or the classic tabs, always shown. It lists, selects, closes and\n", 1)])
edit(W + "WindowTabs.java", [(
    """ * Equal-width, centered tabs of one tab group (workspace tabs spec 10), hidden by their group view while their window
 * holds a single tab (spec 1, decision 4). A tab that opens or closes moves into or out of its place; one that joins or
""", """ * Equal-width, centered tabs of one tab group (workspace tabs spec 10), hidden by their group view while their pane
 * holds a single tab (parent tabs spec 7). A tab that opens or closes moves into or out of its place; one that joins or
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS (`WorkspaceViewTest` +1, `ClassicTabsTest` +1; the rest unchanged in number). Then
`./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Pane strips: a pane's own tabs decide its strip

Under the standard look a pane holding one tab shows no strip,
whatever the window's other panes hold, and a pane of two shows it;
the classic tabs show every pane's strip. This replaces the window's
tab count from the single-tab strip change. The tests that read or
drag from a split's one-tab panes use panes of two tabs, or drop on a
pane's middle; the motion test drops an assertion on a strip that
never hides.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Parent tabs in every window

Spec 4, 7, 10 and 11: every window holds parent tabs, shows one, keeps the others' components and sessions while they
are hidden, closes a parent tab with its last tab (the main window keeps its only one as the empty workspace, a full
window closes with its last), and everything that opens a tab (the registry's placements, requests, the SDK) lands in
the shown parent tab's active pane, or beside a group in that group's parent tab (Rulings 4-10). New Tab is not yet
bound to parent tabs (Task 4); `newParentTab` makes one.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java`, `WindowContent.java`,
  `WindowCommandPalette.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabsViewTest.java` (new), `WindowManagerTest.java`,
  `TabDragTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWindowsTest.java`
- Ledger: two rows

**Interfaces:**
- Consumes: Task 1's `ParentTabs`, `ParentTab`, `ParentTabId`, `WindowLayout`; `WorkspaceView`'s `open`, `openBeside`,
  `remove`, `select`, `focused`, `unmaximize`, `contains`, `tabs`, `tabCount`, `isEmpty`, `selected`, `model`, `refresh`,
  `applyTheme`, `setGlass`, `setAnimations`, `groupViews`, `close`.
- Produces: package `final class ParentTabsView extends JPanel` with `ParentTabsView(WindowContent)`, `ParentTabs
  model()`, `WorkspaceView active()`, `List<WorkspaceView> views()`, `int count()`, `Optional<WorkspaceView>
  view(ParentTabId)`, `Optional<WorkspaceView> holding(Tab)`, `Optional<WorkspaceView> holding(GroupId)`,
  `Optional<GroupId> groupOf(TabId)`, `boolean contains(Tab)`, `List<Tab> tabs()`, `int tabCount()`, `boolean
  isEmpty()`, `WorkspaceView openParentTab(Tab)`, `void select(ParentTabId)`, `void remove(Tab)`, `void
  changed(WorkspaceView)`, `void refresh()`, `void applyTheme(ResolvedTheme, boolean)`, `void setGlass(boolean)`, `void
  setAnimations(AnimationSettings)`, `void revalidateStrips()`, `void close()`; `WorkspaceView(WindowContent,
  ParentTabId)`, `ParentTabId WorkspaceView.id()`, `static boolean WorkspaceView.holdsFocus(WindowContent, Component)`;
  on `WindowContent`: `void workspaceChanged(WorkspaceView view, boolean refocus)` (every layout change and every
  parent-tab change, the hook plan 6a's save takes), `ParentTabsView parentTabs()`, `public WindowLayout
  windowLayout()`, `public TerminalContent newParentTab(Path)`, `void selectParentTab(ParentTabId)`, `void
  selectParentTabAt(int)`, `void selectParentTabRelative(int)`, `void closeParentTab(ParentTabId)`,
  `Optional<ParentTabId> parentTabOf(Tab)`, `ParentTabId activeParentTab()`, `int parentTabCount()`; `workspace()` is
  the shown parent tab's.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabsViewTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Parent tabs spec 4, 7, 10, 11 and 14: a window's parent tabs, each a workspace of its own; what lands in which, what
 * closes them, and that switching keeps every tab's component and session.
 */
class ParentTabsViewTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}: the tab of its new parent tab. */
    private static Tab open(WindowContent owner) {
        owner.newParentTab(HOME);
        return owner.selectedTab();
    }

    private static dev.jasper.terminal.session.TerminalSession session(Tab tab) {
        return ((TerminalContent) tab.content()).pane().session();
    }

    @Test void aNewParentTabHoldsOneTerminalAtTheEndOfTheBarAndShowsAlone() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab first = owner.selectedTab();
            WorkspaceView before = owner.workspace();
            TerminalContent opened = owner.newParentTab(HOME);
            Tab second = owner.selectedTab();
            assertThat(second.content()).isSameAs(opened);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.workspace()).as("the new parent tab shows").isNotSameAs(before);
            assertThat(owner.workspace().tabs()).containsExactly(second);
            assertThat(owner.workspace().isVisible()).isTrue();
            assertThat(before.isVisible()).as("the first stays in the tree, hidden").isFalse();
            assertThat(before.getParent()).isSameAs(owner.parentTabs());
            assertThat(SwingUtilities.isDescendingFrom(first.component(), before)).as("its tab stays where it was").isTrue();
            assertThat(owner.tabs()).as("every tab, the parent tabs in bar order").containsExactly(first, second);
            var parents = (ParentTabs) owner.windowLayout();
            assertThat(parents.parentTabs()).extracting(ParentTab::id).containsExactly(before.id(), owner.workspace().id());
            assertThat(parents.active()).isEqualTo(owner.activeParentTab()).isEqualTo(owner.workspace().id());
            assertThat(parents.activeParentTab().layout()).isEqualTo(owner.workspace().model());
            assertThat(owner.parentTabOf(first)).contains(before.id());
            assertThat(first.group()).as("the registry finds a hidden parent tab's group").contains(before.model().activeGroup());
            owner.applyBackgroundOpacity(0.5);
            assertThat(owner.workspaceOpacity(first.component())).as("a hidden parent tab is in the workspace").isEqualTo(0.5);
        });
    }

    @Test void switchingParentTabsKeepsEveryTabsComponentAndSession() throws Exception {
        var launcher = new ShellLauncher(Runnable::run, directory -> IdleTerminalSession.start(), "idle-test-session");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].newTab(HOME);
            owner[0].newParentTab(HOME);
            owner[0].setSize(1208, 700);
            LayoutTestSupport.layoutTree(owner[0]);
        });
        edt(() -> { });
        edt(() -> {
            var window = owner[0];
            var tabs = window.tabs();
            var sessions = tabs.stream().map(ParentTabsViewTest::session).toList();
            var places = tabs.stream().map(tab -> tab.component().getParent()).toList();
            assertThat(sessions).as("every shell started").hasSize(3).doesNotContainNull();
            ParentTabId first = window.parentTabOf(tabs.get(0)).orElseThrow(), second = window.activeParentTab();
            for (ParentTabId id : List.of(first, second, first)) {
                window.selectParentTab(id);
                LayoutTestSupport.layoutTree(window);
                assertThat(window.activeParentTab()).isEqualTo(id);
                assertThat(window.tabs()).as("nothing opened or closed").isEqualTo(tabs);
                for (int i = 0; i < tabs.size(); i++) {
                    assertThat(session(tabs.get(i))).as("tab %d keeps its session", i).isSameAs(sessions.get(i));
                    assertThat(sessions.get(i).exitFuture()).as("tab %d's shell runs", i).isNotDone();
                    assertThat(tabs.get(i).component().getParent()).as("tab %d keeps its place", i).isSameAs(places.get(i));
                }
                assertThat(window.parentTabs().views()).filteredOn(java.awt.Component::isVisible).containsExactly(window.workspace());
            }
            assertThat(window.selectedTab()).as("the first parent tab shows the tab it showed").isSameAs(tabs.get(1));
        });
    }

    @Test void aParentTabGoesWithItsLastTabAndTheOneUsedBeforeItShows() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab a = owner.selectedTab();
            ParentTabId first = owner.activeParentTab();
            Tab b = open(owner);
            ParentTabId second = owner.activeParentTab();
            Tab c = open(owner);
            ParentTabId third = owner.activeParentTab();
            owner.selectParentTab(first);
            owner.selectParentTab(third);
            owner.selectParentTab(second);
            owner.closeTab(b);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.activeParentTab()).as("the parent tab used before it").isEqualTo(third);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(((ParentTabs) owner.windowLayout()).parentTabs()).extracting(ParentTab::id).containsExactly(first, third);
            owner.closeTab(c);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.closeTab(a);
            assertThat(owner.parentTabCount()).as("the main window keeps its only parent tab").isEqualTo(1);
            assertThat(owner.windowLayout().tabs()).isEmpty();
            assertThat(owner.workspace().emptyWorkspace()).as("as its empty workspace").isNotNull();
            owner.newParentTab(HOME);
            assertThat(owner.parentTabCount()).as("New Tab fills it rather than opening a second, empty one beside it")
                .isEqualTo(1);
            assertThat(owner.tabs()).hasSize(1);
        });
    }

    @Test void aChildWindowClosesWithItsLastParentTabsLastTab() throws Exception {
        int[] emptied = {0};
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> emptied[0]++);
            Tab a = child.selectedTab();
            Tab b = open(child);
            child.closeTab(a);
            assertThat(child.parentTabCount()).isEqualTo(1);
            assertThat(emptied[0]).as("its other parent tab keeps it open").isZero();
            child.closeTab(b);
            assertThat(emptied[0]).as("a full window closes with its last parent tab").isEqualTo(1);
        });
    }

    @Test void closeParentTabClosesItsTabsAsOneGestureAfterTheirGuards() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            ParentTabId first = owner.activeParentTab();
            owner.split(Side.RIGHT);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            Tab other = open(owner);
            boolean[] allow = {false};
            var asked = new ArrayList<List<Tab>>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            owner.closeParentTab(first);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(owner.parentTabCount()).as("a refusal closes nothing").isEqualTo(2);
            assertThat(terminal.closed()).isFalse();
            allow[0] = true;
            owner.closeParentTab(first);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.tabs()).as("the rest of the window").containsExactly(other);
        });
    }

    @Test void whatOpensLandsInTheShownParentTabsActivePane() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            open(owner);
            ParentTabId shown = owner.activeParentTab();
            GroupId pane = owner.workspace().model().activeGroup();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel());
            assertThat(owner.parentTabOf(notes)).as("TabPlacement.ACTIVE_GROUP: the shown parent tab's active pane").contains(shown);
            assertThat(notes.group()).contains(pane);
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(owner.parentTabOf(beside)).contains(shown);
            assertThat(owner.workspace().model().groups()).as("a new pane of the shown parent tab").hasSize(2);
            owner.newTab(HOME);
            assertThat(owner.parentTabOf(owner.selectedTab())).as("a request's folder (single instance), Terminals.openTab")
                .contains(shown);
            assertThat(owner.parentTabCount()).isEqualTo(2);
        });
    }

    @Test void parentTabsOpeningClosingAndSwitchingMoveNoTabAndAMoveBetweenWindowsReportsOne() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var source = content(idleLauncher(pending));
            var target = content(idleLauncher(pending));
            source.connectContributions(model);
            target.connectContributions(model);
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            Tab a = source.selectedTab();
            ParentTabId first = source.activeParentTab();
            Tab b = open(source);
            ParentTabId targetFirst = target.activeParentTab();
            GroupId hidden = target.workspace().model().activeGroup();
            open(target);
            facts.clear();
            source.selectParentTab(first);
            source.selectParentTab(source.parentTabOf(b).orElseThrow());
            source.closeTab(open(source));
            assertThat(facts).as("parent tabs opening, closing and switching move no tab")
                .noneMatch(TerminalEvent.TabMoved.class::isInstance);
            facts.clear();
            model.tabs().move(a, target.id(), hidden, Side.RIGHT);
            assertThat(facts).filteredOn(TerminalEvent.TabMoved.class::isInstance)
                .containsExactly(new TerminalEvent.TabMoved(a.id().value(), source.id(), target.id()));
            assertThat(source.parentTabCount()).as("its parent tab went with it").isEqualTo(1);
            assertThat(target.parentTabOf(a)).as("beside the group it named, in that group's parent tab").contains(targetFirst);
            assertThat(target.activeParentTab()).as("which shows it").isEqualTo(targetFirst);
            assertThat(target.workspace().model().groupOf(a.id())).isPresent().isNotEqualTo(Optional.of(hidden));
        });
    }

    /** Review Focus 1: a hidden parent tab's last tab closes on its own, as a shell's exit or a plugin's close does. */
    @Test void aHiddenParentTabsLastTabClosingLeavesTheShownOneAlone() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            Tab hidden = owner.selectedTab();
            Tab shown = open(owner);
            ParentTabId active = owner.activeParentTab();
            int[] focused = {0};
            shown.focusTarget(() -> focused[0]++);
            hidden.close();
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.activeParentTab()).isEqualTo(active);
            assertThat(owner.selectedTab()).isSameAs(shown);
            assertThat(focused[0]).as("the focus is left where it is").isZero();
        });
    }

    /** Review Focus 5 (workspace plan 7a Rulings 10). */
    @Test void closingTheShownParentTabLeavesFocusThatIsElsewhereWhereItIs() throws Exception {
        var prior = new KeyboardFocusManager[1];
        var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
        var outside = new JPanel();
        var field = new JTextField();
        outside.add(field);
        try {
            edt(() -> {
                prior[0] = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
                var owner = content(idleLauncher(pending));
                Tab first = owner.selectedTab();
                Tab second = open(owner);
                int[] focused = {0};
                first.focusTarget(() -> focused[0]++);
                outside.addNotify();
                assertThat(field.isShowing()).isTrue();
                keys.own(field);
                owner.closeTab(second);
                assertThat(owner.selectedTab()).isSameAs(first);
                assertThat(focused[0]).as("the focus in a tool window or the palette stays there").isZero();
                keys.own(null);
                owner.closeTab(open(owner));
                assertThat(focused[0]).as("with the focus nowhere a person sees, the shown parent tab's tab takes it").isEqualTo(1);
            });
        } finally {
            edt(() -> {
                if (prior[0] != null) KeyboardFocusManager.setCurrentKeyboardFocusManager(prior[0]);
                outside.removeNotify();
            });
        }
    }
}
```

Add a test to `WindowManagerTest`, `TabDragTest` and `JasperApplicationWindowsTest` each:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "workspace/WindowManagerTest.java", [
    ("import dev.jasper.app.launch.ShellLauncher;\n",
     "import dev.jasper.app.launch.ShellLauncher;\nimport dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.ParentTabs;\n", 1),
    ("""    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {
""", """    /** Parent tabs spec 8 and 10: a plugin's tab goes to the main window's shown parent tab; new windows are full windows. */
    @Test void aPluginsTabLandsInTheShownParentTabAndNewWindowsTakeParentTabs() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            var content = main.content();
            ParentTabId first = content.activeParentTab();
            content.newParentTab(HOME);
            content.selectParentTab(first);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(content.parentTabOf(notes)).as("a plugin's tab with no window: the main window's shown parent tab")
                .contains(first);
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            ChildWindow child = manager.children().getFirst();
            assertThat(child.content().windowLayout()).as("TabPlacement.NEW_WINDOW opens a full window").isInstanceOf(ParentTabs.class);
            assertThat(child.content().parentTabOf(away)).isPresent();
            child.content().newParentTab(HOME);
            assertThat(child.content().parentTabCount()).as("which opens parent tabs of its own").isEqualTo(2);
            ChildWindow terminal = manager.openTerminalWindow(null).orElseThrow();
            assertThat(terminal.content().windowLayout()).as("New Terminal Window too").isInstanceOf(ParentTabs.class);
            assertThat(content.parentTabCount()).as("the main window is not touched").isEqualTo(2);
        });
        launch();
    }

    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {
""", 1)])
edit(T + "workspace/TabDragTest.java", [(
    """    @Test void escEndsTheDragWithoutADrop() throws Exception {
""", """    /** Review Focus 4 (workspace plan 7a): a drag ends when its parent tab stops showing; nothing lands in the one that shows. */
    @Test void aDragEndsWhenItsParentTabStopsShowing() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            assertThat(owner.tabDrag().active()).isTrue();
            owner.newParentTab(HOME);
            mouse(source, MouseEvent.MOUSE_DRAGGED, pastC);
            assertThat(owner.tabDrag().active()).as("the dragged tab's pane no longer shows").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.parentTabOf(a)).as("the tab stays where it was").isNotEqualTo(Optional.of(owner.activeParentTab()));
            assertThat(owner.tabs().subList(0, 3)).as("in its order").containsExactly(a, b, c);
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
""", 1)])
edit(T + "application/JasperApplicationWindowsTest.java", [
    ("import dev.jasper.app.commands.CommandHistory;\n",
     "import dev.jasper.app.commands.CommandHistory;\nimport dev.jasper.app.layout.ParentTabs;\n", 1),
    ("""    /** Rulings 20: an outside request raises the main window and opens there, though a child window was used last. */
""", """    /** Parent tabs spec 11: a request opens in the main window's shown parent tab, and no parent tab opens for it. */
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
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /** Rulings 20: an outside request raises the main window and opens there, though a child window was used last. */
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ParentTabsViewTest'`
Expected: FAIL to compile: `cannot find symbol: method newParentTab(java.nio.file.Path)`.

- [ ] **Step 3: The parent-tabs view**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JPanel;

/**
 * A window's parent tabs (parent tabs spec 4 and 7): a {@link WorkspaceView} for each, and the {@link ParentTabs} they
 * make up, which every layout change keeps current. It stands where the terminal column stood, in the dock's centre,
 * and paints nothing itself. Only the shown parent tab's workspace is visible; the others stay in the tree, hidden, with
 * their groups, tabs and sessions as they were, so a switch restarts nothing. A parent tab joins holding its first tab
 * and leaves with its last one, except the window's only parent tab, which the main window keeps as its empty
 * workspace. EDT only.
 */
final class ParentTabsView extends JPanel {
    private final WindowContent owner;
    /** Each parent tab's workspace; the model gives their order. */
    private final Map<ParentTabId, WorkspaceView> views = new HashMap<>();
    private ParentTabs model;
    private boolean closed;

    ParentTabsView(WindowContent owner) {
        super(new Stack());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("parentTabs");
        setOpaque(false);
        var first = new WorkspaceView(owner, ParentTabId.random());
        views.put(first.id(), first);
        add(first);
        model = ParentTabs.of(new ParentTab(first.id(), first.model()));
    }

    /** The parent tabs in bar order with their layouts, and the one that shows. */
    ParentTabs model() { return model; }
    /** The shown parent tab's workspace. */
    WorkspaceView active() { return views.get(model.active()); }
    /** Every parent tab's workspace, in bar order. */
    List<WorkspaceView> views() { return model.parentTabs().stream().map(parent -> views.get(parent.id())).toList(); }
    /** How many parent tabs the window has. */
    int count() { return model.parentTabs().size(); }
    /** The workspace of the parent tab {@code id}; empty for a parent tab the window does not have. */
    Optional<WorkspaceView> view(ParentTabId id) { return Optional.ofNullable(views.get(id)); }

    /** The workspace of the parent tab holding {@code tab}; empty for a tab the window does not hold, and for null. */
    Optional<WorkspaceView> holding(Tab tab) {
        for (WorkspaceView view : views.values()) if (view.contains(tab)) return Optional.of(view);
        return Optional.empty();
    }

    /** The workspace of the parent tab holding {@code group} (workspace plan 7a Rulings 8). */
    Optional<WorkspaceView> holding(GroupId group) {
        for (WorkspaceView view : views.values()) if (view.model().group(group).isPresent()) return Optional.of(view);
        return Optional.empty();
    }

    /** The group holding {@code tab}, in whichever parent tab holds it: the tab registry's {@code groupOf}. */
    Optional<GroupId> groupOf(TabId tab) {
        for (WorkspaceView view : views.values()) {
            Optional<GroupId> group = view.model().groupOf(tab);
            if (group.isPresent()) return group;
        }
        return Optional.empty();
    }

    boolean contains(Tab tab) { return holding(tab).isPresent(); }

    /** Every tab of the window: the parent tabs in bar order, each one's tabs in workspace order. */
    List<Tab> tabs() {
        var all = new ArrayList<Tab>();
        for (WorkspaceView view : views()) all.addAll(view.tabs());
        return all;
    }

    int tabCount() {
        int count = 0;
        for (WorkspaceView view : views.values()) count += view.tabCount();
        return count;
    }

    /** Whether the window holds no tab: its only parent tab is empty, as only the main window's may stay. */
    boolean isEmpty() { return count() == 1 && active().isEmpty(); }

    /**
     * A new parent tab holding {@code tab}, at the end of the bar and shown (New Tab, parent tabs spec 8; workspace plan
     * 7a Rulings 3). The window's only parent tab must not be empty: the empty workspace takes a first tab itself. The tab
     * takes the focus when the window's parent tabs held it.
     */
    WorkspaceView openParentTab(Tab tab) {
        boolean held = WorkspaceView.holdsFocus(owner, this);
        var view = new WorkspaceView(owner, ParentTabId.random());
        try {
            view.open(tab);
            model = model.open(new ParentTab(view.id(), view.model()), count());
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        views.put(view.id(), view);
        add(view, 0);
        reveal(view);
        owner.workspaceChanged(view, held);
        return view;
    }

    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
    void select(ParentTabId id) {
        if (closed || model.active().equals(id) || !views.containsKey(id)) return;
        model = model.select(id);
        reveal(views.get(id));
        owner.workspaceChanged(active(), false);
    }

    /**
     * Takes {@code tab} out of its parent tab, as it closes or moves to another window. A parent tab's last tab takes the
     * parent tab with it and the one used before it shows, except the window's only parent tab (spec 4; workspace plan 7a
     * Rulings 2 and 6); the one that shows then takes the focus when the parent tabs held it (Rulings 10).
     */
    void remove(Tab tab) {
        WorkspaceView view = holding(tab).orElse(null);
        if (view == null) return;
        if (view.tabCount() > 1 || count() == 1) {
            view.remove(tab);
            return;
        }
        boolean held = view == active() && WorkspaceView.holdsFocus(owner, this);
        model = model.remove(view.id());
        views.remove(view.id());
        super.remove(view);
        view.close();
        reveal(active());
        owner.workspaceChanged(active(), held);
    }

    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */
    void changed(WorkspaceView view) {
        if (!closed && views.get(view.id()) == view) model = model.replace(new ParentTab(view.id(), view.model()));
    }

    /** Shows {@code shown} alone; the others stay in the tree, hidden. */
    private void reveal(WorkspaceView shown) {
        shown.setVisible(true);
        for (WorkspaceView view : views.values()) if (view != shown) view.setVisible(false);
        revalidate();
        repaint();
    }

    /** Refreshes every parent tab's strips. */
    void refresh() {
        if (closed) return;
        for (WorkspaceView view : views.values()) view.refresh();
    }

    /** Every parent tab takes the theme; a hidden one is in the tree, so the window's tree update reaches its delegates. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        for (WorkspaceView view : views.values()) view.applyTheme(theme, updateDelegates);
    }

    void setGlass(boolean value) {
        for (WorkspaceView view : views.values()) view.setGlass(value);
        repaint();
    }

    void setAnimations(AnimationSettings settings) {
        for (WorkspaceView view : views.values()) view.setAnimations(settings);
    }

    /** Every strip takes a new tab height. */
    void revalidateStrips() {
        for (WorkspaceView view : views.values())
            for (GroupView group : view.groupViews()) {
                group.strip().component().revalidate();
                group.strip().component().repaint();
            }
    }

    /** The window closed: every parent tab's strips are released; the tabs are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (WorkspaceView view : views.values()) view.close();
    }

    /** Every parent tab's workspace fills the view; only the shown one is visible. */
    private static final class Stack implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return largest(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return largest(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            for (Component child : parent.getComponents())
                child.setBounds(insets.left, insets.top, parent.getWidth() - insets.left - insets.right,
                    parent.getHeight() - insets.top - insets.bottom);
        }
        private static Dimension largest(Container parent, boolean minimum) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
}
```

- [ ] **Step 4: Each workspace is a parent tab's, and the window routes through its parent tabs**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WorkspaceView.java", [
    ("import dev.jasper.app.layout.Node;\n", "import dev.jasper.app.layout.Node;\nimport dev.jasper.app.layout.ParentTabId;\n", 1),
    (" * One window's workspace (workspace tabs spec 10): its {@link Layout} and the tabs the layout places, drawn as\n",
     " * One parent tab's workspace (workspace tabs spec 10; parent tabs spec 4), under the parent tab's id: its\n"
     " * {@link Layout} and the tabs the layout places, drawn as\n", 1),
    ("""    private final WindowContent owner;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
""", """    private final WindowContent owner;
    private final ParentTabId id;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
""", 1),
    ("""    WorkspaceView(WindowContent owner) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
""", """    WorkspaceView(WindowContent owner, ParentTabId id) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.id = Objects.requireNonNull(id, "id");
""", 1),
    ("    Layout model() { return layout; }\n",
     "    Layout model() { return layout; }\n    /** The parent tab this workspace is. */\n    ParentTabId id() { return id; }\n", 1),
    ("        owner.workspaceChanged(held\n", "        owner.workspaceChanged(this, held\n", 1),
    ("""    /**
     * Whether focus may follow a change of this workspace: the palette's card does not hold the window's input, and the
     * permanent focus owner is in this workspace or nowhere a person sees it (a closed terminal, or the palette that just
     * closed to run a command). Focus in the open palette, the Run window or a tool window stays there.
     */
    private boolean holdsFocus() {
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return focus == null || !focus.isShowing() || SwingUtilities.isDescendingFrom(focus, this);
    }
""", """    private boolean holdsFocus() { return holdsFocus(owner, this); }

    /**
     * Whether focus may follow a change of {@code area}, a workspace or a window's parent tabs: the palette's card does not
     * hold the window's input, and the permanent focus owner is in {@code area} or nowhere a person sees it (a closed
     * terminal, or the palette that just closed to run a command). Focus in the open palette, the Run window or a tool
     * window stays there.
     */
    static boolean holdsFocus(WindowContent owner, Component area) {
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return focus == null || !focus.isShowing() || SwingUtilities.isDescendingFrom(focus, area);
    }
""", 1)])
edit(W + "WindowCommandPalette.java", [
    ("owner.addComponentListener(resize); owner.workspace().addComponentListener(resize);",
     "owner.addComponentListener(resize); owner.parentTabs().addComponentListener(resize);", 1),
    ("owner.removeComponentListener(resize); owner.workspace().removeComponentListener(resize);",
     "owner.removeComponentListener(resize); owner.parentTabs().removeComponentListener(resize);", 1)])
edit(W + "WindowContent.java", [
    ("import dev.jasper.app.layout.GroupId;\n",
     "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.WindowLayout;\n", 1),
    ("""    /** The window's tabs as groups in splits, each group under the look's strip (workspace tabs spec 10). */
    private final WorkspaceView workspace;
""", """    /**
     * The window's parent tabs, each a workspace of tabs as groups in splits under the look's strips (workspace tabs spec
     * 10; parent tabs spec 4), where the terminal column stood.
     */
    private final ParentTabsView parentTabs;
""", 1),
    ("""        workspace = new WorkspaceView(this);
        // The workspace belongs to the terminal column, so the stripes and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(workspace, BorderLayout.CENTER);
""", """        // The parent tabs are the terminal column, so the stripes and the side panels reach the toolbar; the rounded host
        // paints behind this layout-only container, including partial tab repaints.
        parentTabs = new ParentTabsView(this);
""", 1),
    ("        area = new ToolWindowArea(terminalColumn, dock, themes.kit(), look().dock().flushFrames());\n",
     "        area = new ToolWindowArea(parentTabs, dock, themes.kit(), look().dock().flushFrames());\n", 1),
    ("""    /** The registry-facing side of this window's workspace (workspace tabs spec 9.1). */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            tab -> workspace.model().groupOf(tab),
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace.model().activeGroup(), side));
    }
""", """    /**
     * The registry-facing side of this window's workspace (workspace tabs spec 9.1): its groups are every parent tab's, so
     * a tab placed beside a group lands in that group's parent tab (workspace plan 7a Rulings 8).
     */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            parentTabs::groupOf,
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side, false));
    }
""", 1),
    ("    private void attach(Tab tab) { attach(tab, false, null, null); }\n",
     "    private void attach(Tab tab) { attach(tab, false, null, null, false); }\n", 1),
    ("""     * As {@link #attach(Tab)}; with {@code opened} a new terminal tab's pane is announced as opened, and with
     * {@code beside} the tab opens in a new group on {@code side} of that group.
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side) {
""", """     * As {@link #attach(Tab)}; with {@code opened} a new terminal tab's pane is announced as opened, with {@code beside}
     * the tab opens in a new group on {@code side} of that group, in the parent tab holding it, and with
     * {@code parentTab} it opens in a new parent tab of its own (parent tabs spec 8).
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side, boolean parentTab) {
""", 1),
    ("""            if (beside == null) workspace.open(tab); else workspace.openBeside(tab, beside, side);
            // A tab that moves in ends this window's maximize; one that opens here does not (spec 15).
            if (tab.moving()) workspace.unmaximize();
""", """            if (parentTab) parentTabs.openParentTab(tab);
            else if (beside == null) workspace().open(tab);
            else parentTabs.holding(beside).orElseThrow(() -> new IllegalArgumentException("Group " + beside + " is not in this window"))
                .openBeside(tab, beside, side);
            // A tab that moves in ends its parent tab's maximize; one that opens here does not (spec 15).
            if (tab.moving()) parentTabs.holding(tab).ifPresent(WorkspaceView::unmaximize);
""", 1),
    ("""            workspace.remove(tab);
            WorkspaceTheme.clear(component);
""", """            parentTabs.remove(tab);
            WorkspaceTheme.clear(component);
""", 1),
    ("""        // A tab that moves out ends the maximize; closing a tab keeps it while its group stands (spec 15).
        if (tab.moving()) workspace.unmaximize();
        workspace.remove(tab);
""", """        // A tab that moves out ends its parent tab's maximize; closing a tab keeps it while its group stands (spec 15).
        if (tab.moving()) parentTabs.holding(tab).ifPresent(WorkspaceView::unmaximize);
        // A parent tab goes with its last tab, except the window's only one (parent tabs spec 4).
        parentTabs.remove(tab);
""", 1),
    ("        if (workspace.isEmpty() && !tab.moving()) onEmpty.run();\n",
     "        if (parentTabs.isEmpty() && !tab.moving()) onEmpty.run();\n", 1),
    ("""     * The workspace's layout changed: the registry hears the selection, the palette a new active tab; the active tab takes
     * focus when {@code refocus}.
     */
    void workspaceChanged(boolean refocus) {
        if (closed) return;
        if (terminals != null) terminals.tabSelected();
""", """     * A parent tab's layout changed, or the window's parent tabs did (one opened, closed or shows now): the model takes
     * {@code view}'s layout, the registry hears the selection, the palette a new active tab; the active tab takes focus
     * when {@code refocus} and {@code view} is the shown parent tab's.
     */
    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        if (terminals != null) terminals.tabSelected();
""", 1),
    ("""        update();
        if (refocus) focusSelected();
    }
""", """        update();
        if (refocus && view == parentTabs.active()) focusSelected();
    }
""", 1),
    ("""    /** The active group's selected tab; null in an empty workspace. */
    Tab selectedTab() { return workspace.selected(); }
    /** This window's tabs in workspace order. */
    java.util.List<Tab> tabs() { return workspace.tabs(); }
    int tabCount() { return workspace.tabCount(); }
    /** The active group's tab count. */
    int groupTabCount() { return workspace.activeGroupView().tabs().size(); }
""", """    /** The shown parent tab's active group's selected tab; null in an empty workspace. */
    Tab selectedTab() { return parentTabs.active().selected(); }
    /** This window's tabs: its parent tabs in bar order, each one's tabs in workspace order. */
    java.util.List<Tab> tabs() { return parentTabs.tabs(); }
    int tabCount() { return parentTabs.tabCount(); }
    /** The active group's tab count. */
    int groupTabCount() { return workspace().activeGroupView().tabs().size(); }
""", 1),
    ("    WorkspaceView workspace() { return workspace; }\n",
     """    /** The shown parent tab's workspace. */
    WorkspaceView workspace() { return parentTabs.active(); }
    /** The window's parent tabs. */
    ParentTabsView parentTabs() { return parentTabs; }

    /**
     * What this window holds (parent tabs spec 4): its parent tabs with their layouts, and the one that shows.
     *
     * @return the parent tabs
     */
    public WindowLayout windowLayout() { return parentTabs.model(); }
""", 1),
    ("""    TabStrip tabBar() { return workspace.activeGroupView().strip(); }
    private void refreshTabs() { workspace.refresh(); }
""", """    TabStrip tabBar() { return workspace().activeGroupView().strip(); }
    private void refreshTabs() { parentTabs.refresh(); }
""", 1),
    ("    public void newTab(Path directory) { openTab(directory); }\n",
     """    public void newTab(Path directory) { openTab(directory); }

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

    /** Shows the parent tab {@code id} and focuses its active tab (workspace plan 7a Rulings 10); nothing for another window's. */
    void selectParentTab(ParentTabId id) {
        if (closed || parentTabs.view(id).isEmpty()) return;
        parentTabs.select(id);
        focusSelected();
    }

    /** Select Parent Tab 1 to 9: the parent tab at {@code index} of the bar; nothing past the last. */
    void selectParentTabAt(int index) {
        var all = parentTabs.model().parentTabs();
        if (index >= 0 && index < all.size()) selectParentTab(all.get(index).id());
    }

    /** Next and Previous Parent Tab: the parent tab {@code delta} places along the bar, wrapping around. */
    void selectParentTabRelative(int delta) { if (parentTabs.count() > 1) selectParentTab(parentTabs.model().step(delta)); }

    /**
     * Close Parent Tab (parent tabs spec 8): every tab of the parent tab {@code id} as one gesture, after their guards; the
     * parent tab goes with its last tab (spec 4).
     */
    void closeParentTab(ParentTabId id) { parentTabs.view(id).ifPresent(view -> closeTabs(view.tabs())); }

    /** The parent tab holding {@code tab}; empty for a tab this window does not hold. */
    Optional<ParentTabId> parentTabOf(Tab tab) { return parentTabs.holding(tab).map(WorkspaceView::id); }

    /** The shown parent tab. */
    ParentTabId activeParentTab() { return parentTabs.model().active(); }

    /** How many parent tabs this window has. */
    int parentTabCount() { return parentTabs.count(); }
""", 1),
    ("        return openTerminal(directory, requestOrNull, profile, program, null, null);\n",
     "        return openTerminal(directory, requestOrNull, profile, program, null, null, false);\n", 1),
    ("""     * A terminal tab starting in {@code directory} (null: where a new shell starts), in the active group, or in a new
     * group on {@code side} of {@code beside} when that is given. Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side) {
""", """     * A terminal tab starting in {@code directory} (null: where a new shell starts), in the active group, in a new group
     * on {@code side} of {@code beside} when that is given, or in a new parent tab of its own when {@code parentTab}.
     * Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side,
                                         boolean parentTab) {
""", 1),
    ("            attach(tab, true, beside, side);\n", "            attach(tab, true, beside, side, parentTab);\n", 1),
    ("        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace.model().activeGroup(), side);\n",
     "        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace().model().activeGroup(), side, false);\n", 1),
    ("""        GroupId group = workspace.model().groupOf(beside.id()).orElseThrow();
        Path directory = directoryOrNull == null ? target.pane().inheritableDirectory() : directoryOrNull;
        return openTerminal(directory, requestOrNull, profile, program, group, side);
""", """        GroupId group = parentTabs.groupOf(beside.id()).orElseThrow();
        Path directory = directoryOrNull == null ? target.pane().inheritableDirectory() : directoryOrNull;
        return openTerminal(directory, requestOrNull, profile, program, group, side, false);
""", 1),
    ("    void toggleMaximize() { workspace.toggleMaximize(); }\n", "    void toggleMaximize() { workspace().toggleMaximize(); }\n", 1),
    ("""    void focusGroup(Side side) {
        workspace.neighbour(side).ifPresent(group -> {
""", """    void focusGroup(Side side) {
        WorkspaceView workspace = workspace();
        workspace.neighbour(side).ifPresent(group -> {
""", 1),
    ("    void tabFocused(Tab tab) { if (!closed && tab != null) workspace.focused(tab); }\n",
     """    void tabFocused(Tab tab) {
        // Only the shown parent tab can hold the focus.
        if (!closed && tab != null) parentTabs.holding(tab).filter(view -> view == parentTabs.active()).ifPresent(view -> view.focused(tab));
    }
""", 1),
    ("""    /** Shows {@code tab} in its group, activates the group and focuses the tab. */
    void selectTab(Tab tab) {
        if (closed || !workspace.contains(tab)) return;
        workspace.select(tab);
        focusSelected();
    }
""", """    /** Shows {@code tab}'s parent tab, then the tab in its group, activates the group and focuses the tab. */
    void selectTab(Tab tab) {
        WorkspaceView view = closed ? null : parentTabs.holding(tab).orElse(null);
        if (view == null) return;
        parentTabs.select(view.id());
        view.select(tab);
        focusSelected();
    }
""", 1),
    ("        var group = workspace.activeGroupView().tabs();\n", "        var group = workspace().activeGroupView().tabs();\n", 1),
    ("        var view = workspace.activeGroupView();\n", "        var view = workspace().activeGroupView();\n", 1),
    ("        if (!workspace.contains(tab)) return;\n", "        if (!parentTabs.contains(tab)) return;\n", 1),
    ("""        return workspace.model().groupOf(tab.id()).map(workspace::groupView)
            .map(group -> java.util.List.copyOf(group.tabs())).orElse(java.util.List.of());
""", """        return parentTabs.holding(tab).flatMap(view -> view.model().groupOf(tab.id()).map(view::groupView))
            .map(group -> java.util.List.copyOf(group.tabs())).orElse(java.util.List.of());
""", 1),
    ("        var live = batch.stream().filter(workspace::contains).toList();\n",
     "        var live = batch.stream().filter(parentTabs::contains).toList();\n", 1),
    ("        workspace.applyTheme(theme, updateDelegates);\n", "        parentTabs.applyTheme(theme, updateDelegates);\n", 1),
    ("""        workspace.setGlass(glass);
        for (TerminalContent terminal : terminalTabs()) terminal.setBackgroundOpacity(value);
        workspace.refresh();
""", """        parentTabs.setGlass(glass);
        for (TerminalContent terminal : terminalTabs()) terminal.setBackgroundOpacity(value);
        parentTabs.refresh();
""", 1),
    ("        return component != null && SwingUtilities.isDescendingFrom(component, workspace) ? backgroundOpacity : 1;\n",
     "        return component != null && SwingUtilities.isDescendingFrom(component, parentTabs) ? backgroundOpacity : 1;\n", 1),
    ("        workspace.setAnimations(settings);\n", "        parentTabs.setAnimations(settings);\n", 1),
    ("        for (GroupView group : workspace.groupViews()) { group.strip().component().revalidate(); group.strip().component().repaint(); }\n",
     "        parentTabs.revalidateStrips();\n", 1),
    ("        workspace.close(); removeRootBindings();\n", "        parentTabs.close(); removeRootBindings();\n", 1)])
PY
grep -n 'workspace\.' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
```

Expected: the `grep` lists only the two Javadoc lines this step writes, `focusGroup`'s two uses of its local
`workspace`, and `requestGlass`'s two uses of its local (`workspace.glass()`, `workspace.dark()`); any other line names
the removed field and must be routed as above.

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); import ledger
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "ParentTabsView.java", "app.workspace", "ParentTabsView", T + "ParentTabsViewTest.java",
           "New in workspace-tabs plan 7a Task 3; a window's parent tabs, one workspace each")
ledger.add(T + "ParentTabsViewTest.java", "app.workspace", "ParentTabsViewTest", T + "ParentTabsViewTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7a Task 3; parent tabs in a window")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.JasperApplicationWindowsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`ParentTabsViewTest` 9, `WindowManagerTest` +1, `TabDragTest` +1, `JasperApplicationWindowsTest` +1;
every other workspace test unchanged). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Parent tabs: every window holds parent tabs and shows one

A ParentTabsView takes the terminal column's place: one workspace per
parent tab, the hidden ones kept in the tree with their components and
sessions, and the ParentTabs model kept current from every layout
change. The window routes attaching, detaching, selecting and closing
through it: a parent tab goes with its last tab (the main window keeps
its only one as the empty workspace), the one used before it shows,
and a new parent tab opens at the end of the bar. Requests, plugin
placements and the SDK land in the shown parent tab's active pane, or
beside a group in that group's parent tab; no tab moves between parent
tabs of one window, so none reports TAB_MOVED.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Commands, keys and menus

Spec 8: New Tab opens a parent tab, New Tab in Pane stacks a terminal in the active pane, Close Parent Tab, Next and
Previous Parent Tab and Select Parent Tab 1-9 act on parent tabs, Next and Previous Tab act within the pane on Ctrl+Tab,
and the group commands become pane commands, with their ids, labels, menus, palette entries and Keymap categories
(Rulings 12-15, Spec issues 1). The classic pane strip's "+" stacks in its pane. Next and Previous Parent Tab run before
a focused plugin view's own bindings (Rulings 14).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java`,
  `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java`, `WindowCommands.java`, `WindowChrome.java`,
  `ClassicHeader.java`, `WindowContent.java`, `GroupView.java`,
  `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabCommandsTest.java` (new); replace
  `jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java`; modify `config/KeyBindingsTest.java`,
  `config/ConfigLoaderTest.java`, `workspace/TabShortcutsTest.java`, `workspace/CommandPaletteShortcutsTest.java`,
  `workspace/WindowTabsTest.java`, `workspace/WorkspaceTabsTest.java`, `workspace/WorkspaceViewTest.java`,
  `workspace/TabMenuTest.java`, `shortcuthelp/ShortcutHelpTest.java`, and the renamed ids' and labels' uses in
  `ApplicationActionsTest`, `ShellExitBehaviorTest`, `TerminalContextMenuTest`, `WindowChromeContributionsTest`,
  `WindowChromeTest`, `WindowCommandPaletteTest`, `WindowManagerTest`, `ZenModeTest`
- Ledger: one row

**Interfaces:**
- Consumes: Task 3's `newParentTab`, `selectParentTabAt`, `selectParentTabRelative`, `closeParentTab`, `activeParentTab`,
  `parentTabCount`, `parentTabs()`.
- Produces: `ActionId.NEW_TAB_IN_PANE`, `CLOSE_PANE`, `CLOSE_PARENT_TAB`, `MAXIMIZE_PANE`, `FOCUS_PANE_LEFT`, `_RIGHT`,
  `_UP`, `_DOWN`, `NEXT_PARENT_TAB`, `PREVIOUS_PARENT_TAB`, `SELECT_PARENT_TAB_1` to `_9` (ids `new_tab_in_pane`,
  `close_pane`, `close_parent_tab`, `maximize_pane`, `focus_pane_*`, `next_parent_tab`, `previous_parent_tab`,
  `select_parent_tab_1`-`9`), with `CLOSE_GROUP`, `MAXIMIZE_GROUP` and `FOCUS_GROUP_*` gone; the Keymap category
  "Panes".

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabCommandsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.layout.ParentTabId;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 8: New Tab, New Tab in Pane, the parent-tab commands and Close Pane, run as actions. */
class ParentTabCommandsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void newTabOpensAParentTabAndNewTabInPaneStacksATabInTheActivePane() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            var pane = owner.workspace().model().activeGroup();
            owner.invoke(ActionId.NEW_TAB_IN_PANE);
            assertThat(owner.parentTabCount()).isEqualTo(1);
            assertThat(owner.workspace().model().group(pane).orElseThrow().tabs()).as("stacked in the active pane").hasSize(2);
            owner.invoke(ActionId.NEW_TAB);
            assertThat(owner.parentTabCount()).as("a parent tab with one terminal").isEqualTo(2);
            assertThat(owner.workspace().tabs()).hasSize(1);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    /** Review Focus 3. */
    @Test void newTabInTheEmptyMainWorkspaceFillsItsOnlyParentTab() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            main.invoke(ActionId.NEW_TAB);
            assertThat(main.parentTabCount()).isEqualTo(1);
            assertThat(main.tabCount()).isEqualTo(1);
        });
    }

    @Test void parentTabsSwitchByNumberAndInTurnAndCloseByCommand() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            ParentTabId first = owner.activeParentTab();
            owner.updateActions();
            assertThat(owner.action(ActionId.NEXT_PARENT_TAB).isEnabled()).as("one parent tab").isFalse();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_1).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_2).isEnabled()).isFalse();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId second = owner.activeParentTab();
            owner.invoke(ActionId.NEW_TAB);
            ParentTabId third = owner.activeParentTab();
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_3).isEnabled()).isTrue();
            owner.invoke(ActionId.SELECT_PARENT_TAB_1);
            assertThat(owner.activeParentTab()).isEqualTo(first);
            owner.invoke(ActionId.PREVIOUS_PARENT_TAB);
            assertThat(owner.activeParentTab()).as("wraps to the last").isEqualTo(third);
            owner.invoke(ActionId.NEXT_PARENT_TAB);
            assertThat(owner.activeParentTab()).as("and back to the first").isEqualTo(first);
            owner.invoke(ActionId.SELECT_PARENT_TAB_9);
            assertThat(owner.activeParentTab()).as("nothing past the last").isEqualTo(first);
            owner.invoke(ActionId.SELECT_PARENT_TAB_2);
            owner.invoke(ActionId.CLOSE_PARENT_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.parentTabs().view(second)).isEmpty();
            assertThat(owner.activeParentTab()).as("the parent tab used before it").isEqualTo(first);
        });
    }

    @Test void closePaneClosesTheActivePaneAndItsParentTabGoesWithTheLast() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.invoke(ActionId.NEW_TAB);
            owner.invoke(ActionId.SPLIT_RIGHT);
            owner.invoke(ActionId.NEW_TAB_IN_PANE);
            assertThat(owner.workspace().model().groups()).hasSize(2);
            assertThat(owner.workspace().activeGroupView().tabs()).hasSize(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.workspace().model().groups()).as("the pane went with its tabs").hasSize(1);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            owner.invoke(ActionId.CLOSE_PANE);
            assertThat(owner.parentTabCount()).as("the parent tab went with its last pane").isEqualTo(1);
        });
    }

    @Test void theCommandsAreInThePaletteUnderTheirNames() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab",
                    "select_parent_tab_1", "close_pane", "maximize_pane", "focus_pane_left"))
                assertThat(owner.commands().find(id)).as(id).isPresent();
            assertThat(owner.commands().find("close_group")).as("a clean break").isEmpty();
            assertThat(owner.action(ActionId.CLOSE_PANE).getValue(Action.NAME)).isEqualTo("Close Pane");
            assertThat(owner.action(ActionId.NEW_TAB_IN_PANE).getValue(Action.NAME)).isEqualTo("New Tab in Pane");
            assertThat(owner.action(ActionId.SELECT_PARENT_TAB_1).getValue(Action.NAME)).isEqualTo("Select Parent Tab 1");
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java` with:

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approved defaults (spec 2026-09-30-keymap-defaults-design.md, Defaults) for the built-in actions plan K1
 * covers; the comment on each row is the spec's row number, or the spec that set it since. Find, Find Next, Find
 * Previous, Copy and Paste stay Global until plan K3.
 */
class KeymapDefaultsTest {
    private record Row(String id, KeyContext context, String macOs, String other) { }

    private static final List<Row> ROWS = List.of(
        new Row("new_terminal_window", KeyContext.GLOBAL, "cmd+n", "ctrl+shift+n"),            // 1, workspace tabs spec 11
        new Row("close_window", KeyContext.GLOBAL, "cmd+shift+w", "none"),                    // workspace tabs spec 11
        new Row("move_tab_to_new_window", KeyContext.GLOBAL, "none", "none"),                 // workspace tabs spec 11
        new Row("move_tab_to_main_window", KeyContext.GLOBAL, "none", "none"),                // workspace tabs spec 11
        new Row("quit", KeyContext.GLOBAL, "cmd+q", "ctrl+alt+shift+q"),                       // 3
        new Row("open_settings", KeyContext.GLOBAL, "cmd+,", "ctrl+shift+,"),                  // 4
        new Row("open_file", KeyContext.GLOBAL, "none", "none"),  // Enterprise spec 13: File › Open File…, unbound
        new Row("reload_config", KeyContext.GLOBAL, "none", "none"),                           // 5
        new Row("command_palette", KeyContext.GLOBAL, "cmd+shift+p", "ctrl+shift+p"),          // 14
        new Row("new_tab", KeyContext.GLOBAL, "cmd+t", "ctrl+shift+t"),                        // 22; parent tabs spec 8: a parent tab
        new Row("new_tab_in_pane", KeyContext.GLOBAL, "none", "none"),                         // parent tabs spec 8
        new Row("close_tab", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                      // 23, workspace tabs spec 11
        new Row("close_pane", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),             // parent tabs spec 8
        new Row("close_parent_tab", KeyContext.GLOBAL, "none", "none"),                        // parent tabs spec 8
        new Row("next_parent_tab", KeyContext.GLOBAL, "cmd+shift+]", "ctrl+pagedown"),         // parent tabs spec 8
        new Row("previous_parent_tab", KeyContext.GLOBAL, "cmd+shift+[", "ctrl+pageup"),
        new Row("select_parent_tab_1", KeyContext.GLOBAL, "cmd+1", "ctrl+1"),                  // parent tabs spec 8; plan 7a Spec issues 1
        new Row("select_parent_tab_2", KeyContext.GLOBAL, "cmd+2", "ctrl+2"),
        new Row("select_parent_tab_3", KeyContext.GLOBAL, "cmd+3", "ctrl+3"),
        new Row("select_parent_tab_4", KeyContext.GLOBAL, "cmd+4", "ctrl+4"),
        new Row("select_parent_tab_5", KeyContext.GLOBAL, "cmd+5", "ctrl+5"),
        new Row("select_parent_tab_6", KeyContext.GLOBAL, "cmd+6", "ctrl+6"),
        new Row("select_parent_tab_7", KeyContext.GLOBAL, "cmd+7", "ctrl+7"),
        new Row("select_parent_tab_8", KeyContext.GLOBAL, "cmd+8", "ctrl+8"),
        new Row("select_parent_tab_9", KeyContext.GLOBAL, "cmd+9", "ctrl+9"),
        new Row("next_tab", KeyContext.GLOBAL, "ctrl+tab", "ctrl+tab"),                        // 24; parent tabs spec 8
        new Row("previous_tab", KeyContext.GLOBAL, "ctrl+shift+tab", "ctrl+shift+tab"),        // 25
        new Row("select_tab_1", KeyContext.GLOBAL, "none", "none"),                            // 26; the digits select parent tabs now
        new Row("select_tab_2", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_3", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_4", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_5", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_6", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_7", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_8", KeyContext.GLOBAL, "none", "none"),
        new Row("select_tab_9", KeyContext.GLOBAL, "none", "none"),                            // 27, Select Last Tab in K2
        new Row("rename_tab", KeyContext.GLOBAL, "cmd+i", "ctrl+shift+i"),                     // 28
        new Row("split_right", KeyContext.GLOBAL, "cmd+d", "ctrl+shift+d"),                    // 32
        new Row("split_down", KeyContext.GLOBAL, "cmd+shift+d", "ctrl+alt+shift+d"),           // 33
        new Row("maximize_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"), // 35, parent tabs spec 8
        new Row("toggle_zen_mode", KeyContext.GLOBAL, "none", "none"),                       // Zen follow-up
        new Row("focus_pane_left", KeyContext.GLOBAL, "cmd+alt+left", "ctrl+alt+shift+left"),  // 36, parent tabs spec 8
        new Row("focus_pane_right", KeyContext.GLOBAL, "cmd+alt+right", "ctrl+alt+shift+right"),
        new Row("focus_pane_up", KeyContext.GLOBAL, "cmd+alt+up", "ctrl+alt+shift+up"),
        new Row("focus_pane_down", KeyContext.GLOBAL, "cmd+alt+down", "ctrl+alt+shift+down"),
        new Row("copy", KeyContext.GLOBAL, "cmd+c", "ctrl+shift+c"),                           // 42
        new Row("paste", KeyContext.GLOBAL, "cmd+v", "ctrl+shift+v"),                          // 43
        new Row("clear_scrollback", KeyContext.TERMINAL, "cmd+k", "ctrl+shift+k"),             // 45
        new Row("clear_terminal", KeyContext.TERMINAL, "none", "none"),                       // Clear Terminal follow-up
        new Row("find", KeyContext.GLOBAL, "cmd+f", "ctrl+shift+f"),                           // 47
        new Row("find_next", KeyContext.GLOBAL, "cmd+g", "ctrl+shift+g"),                      // 48
        new Row("find_previous", KeyContext.GLOBAL, "cmd+shift+g", "ctrl+alt+shift+g"),        // 49
        new Row("previous_prompt", KeyContext.TERMINAL, "cmd+up", "ctrl+shift+up"),            // 50
        new Row("next_prompt", KeyContext.TERMINAL, "cmd+down", "ctrl+shift+down"),            // 51
        new Row("font_bigger", KeyContext.GLOBAL, "cmd+=", "ctrl+shift+="),                    // 55
        new Row("font_smaller", KeyContext.GLOBAL, "cmd+-", "ctrl+shift+-"),
        new Row("font_reset", KeyContext.GLOBAL, "cmd+0", "ctrl+shift+0"));

    @Test void everyBuiltInActionHasItsApprovedDefaultsAndContext() {
        Set<String> seen = new HashSet<>();
        for (Row row : ROWS) {
            ActionId action = ActionId.forId(row.id()).orElseThrow(() -> new AssertionError("no built-in " + row.id()));
            assertThat(seen.add(row.id())).as("listed once: %s", row.id()).isTrue();
            assertThat(action.context()).as("context of %s", row.id()).isEqualTo(row.context());
            assertThat(KeyBindings.parse(action.defaultBinding(true), true)).as("macOS default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.macOs(), true));
            assertThat(KeyBindings.parse(action.defaultBinding(false), false)).as("Linux/Windows default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.other(), false));
        }
        assertThat(seen).as("every built-in action is in the table").hasSize(ActionId.values().length);
    }

    @Test void bothKeymapsBuildAndBindEveryDefault() {
        for (boolean mac : new boolean[]{true, false}) {
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (ActionId action : ActionId.values())
                assertThat(bindings.strokeFor(action)).as("%s on %s", action, mac ? "macOS" : "Linux")
                    .isEqualTo(KeyBindings.parse(action.defaultBinding(mac), mac));
        }
    }

    /** Every macOS default uses Cmd, but Next and Previous Tab's Ctrl+Tab, as macOS's Terminal and Safari have it. */
    @Test void macDefaultsUseCmdOrCtrlTabAndLeaveMacOsItsKeys() {
        Set<KeyStroke> system = new HashSet<>();
        for (String key : List.of("cmd+h", "alt+cmd+h", "cmd+space", "alt+cmd+d", "ctrl+cmd+d", "ctrl+cmd+q",
                "shift+cmd+/", "shift+cmd+3", "shift+cmd+4", "shift+cmd+5"))
            system.add(KeyBindings.parse(key, true).orElseThrow());
        Set<KeyStroke> tabs = Set.of(KeyBindings.parse("ctrl+tab", true).orElseThrow(), KeyBindings.parse("ctrl+shift+tab", true).orElseThrow());
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(true), true).ifPresent(stroke -> {
            if (!tabs.contains(stroke)) assertThat(stroke.getModifiers() & InputEvent.META_DOWN_MASK).as("%s uses Cmd", action).isNotZero();
            assertThat(system).as("%s leaves macOS its keys", action).doesNotContain(stroke);
        });
    }

    /** Off macOS a default is Ctrl+Shift, Ctrl and a digit, Ctrl+Tab, or Ctrl+PageUp and PageDown, as GNOME Terminal's tabs. */
    @Test void linuxDefaultsLeaveTheShellItsKeys() {
        int mask = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(false), false).ifPresent(stroke -> {
            int modifiers = stroke.getModifiers() & mask, key = stroke.getKeyCode();
            boolean ctrlShift = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0 && (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
            boolean ctrlDigit = modifiers == InputEvent.CTRL_DOWN_MASK && key >= KeyEvent.VK_1 && key <= KeyEvent.VK_9;
            boolean ctrlTab = key == KeyEvent.VK_TAB && (modifiers & ~InputEvent.SHIFT_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK;
            boolean ctrlPage = modifiers == InputEvent.CTRL_DOWN_MASK && (key == KeyEvent.VK_PAGE_UP || key == KeyEvent.VK_PAGE_DOWN);
            assertThat(ctrlShift || ctrlDigit || ctrlTab || ctrlPage).as("%s: %s", action, KeyBindings.format(stroke, false)).isTrue();
            assertThat(modifiers & InputEvent.META_DOWN_MASK).as("%s avoids the Super key", action).isZero();
        });
    }

    /**
     * Workspace plan 7a Spec issues 1: the tool windows keep their digits, Alt+1 to 9 off macOS (IntelliJ's; Remote's SSH
     * hosts, SFTP and Transfers) and Cmd+Alt+1 to 9 on macOS; no built-in default takes one.
     */
    @Test void noBuiltInDefaultTakesTheToolWindowsDigits() {
        for (boolean mac : new boolean[]{true, false}) {
            int toolWindows = mac ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int key = KeyEvent.VK_1; key <= KeyEvent.VK_9; key++)
                assertThat(bindings.actionFor(KeyStroke.getKeyStroke(key, toolWindows)))
                    .as("digit %d on %s", key - KeyEvent.VK_0, mac ? "macOS" : "Linux").isEmpty();
        }
    }
}
```

Then the other tests:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit, span, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
span(T + "config/KeyBindingsTest.java", "        assertThat(EnumSet.allOf(ActionId.class)).containsExactly(\n",
     "            ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,\n",
     """        assertThat(EnumSet.allOf(ActionId.class)).containsExactly(
            ActionId.NEW_TAB, ActionId.NEW_TAB_IN_PANE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE, ActionId.CLOSE_PARENT_TAB,
            ActionId.NEW_TERMINAL_WINDOW, ActionId.CLOSE_WINDOW,
            ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_MAIN_WINDOW, ActionId.OPEN_FILE,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE,
            ActionId.TOGGLE_ZEN_MODE,
            ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT,
            ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
            ActionId.NEXT_PARENT_TAB, ActionId.PREVIOUS_PARENT_TAB,
            ActionId.SELECT_PARENT_TAB_1, ActionId.SELECT_PARENT_TAB_2, ActionId.SELECT_PARENT_TAB_3,
            ActionId.SELECT_PARENT_TAB_4, ActionId.SELECT_PARENT_TAB_5, ActionId.SELECT_PARENT_TAB_6,
            ActionId.SELECT_PARENT_TAB_7, ActionId.SELECT_PARENT_TAB_8, ActionId.SELECT_PARENT_TAB_9,
            ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB,
            ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3,
            ActionId.SELECT_TAB_4, ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6,
            ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,
""")
span(T + "config/KeyBindingsTest.java", "    void tabDefaultsFollowEachPlatformsTerminals() {\n",
     "                modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).isEmpty();\n        }\n    }\n",
     """    void tabDefaultsFollowEachPlatformsTerminals() {
        for (boolean mac : new boolean[]{true, false}) {
            int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int number = 1; number <= 9; number++) {
                KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_1 + number - 1, modifier);
                assertThat(bindings.actionFor(stroke)).as("parent tabs spec 8: the digits select parent tabs")
                    .contains(ActionId.valueOf("SELECT_PARENT_TAB_" + number));
                assertThat(bindings.strokeFor(ActionId.valueOf("SELECT_TAB_" + number))).isEmpty();
            }
            KeyStroke previousParent = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK);
            KeyStroke nextParent = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK);
            assertThat(bindings.actionFor(previousParent)).contains(ActionId.PREVIOUS_PARENT_TAB);
            assertThat(bindings.actionFor(nextParent)).contains(ActionId.NEXT_PARENT_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)))
                .contains(ActionId.PREVIOUS_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK))).contains(ActionId.NEXT_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier))).isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).isEmpty();
        }
    }
""")
edit(T + "config/KeyBindingsTest.java", [(
    '            .withMessageContaining("copy").withMessageContaining("previous_tab");\n',
    '            .withMessageContaining("copy").withMessageContaining("previous_parent_tab");\n', 1)])
edit(T + "config/ConfigLoaderTest.java", [
    ('            next_tab = "cmd+}"\n            previous_tab = "cmd+{"\n',
     '            next_parent_tab = "cmd+}"\n            previous_parent_tab = "cmd+{"\n', 1),
    ("            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_TAB);\n",
     "            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_PARENT_TAB);\n", 1),
    ("    @Test void invalidOrCollidingBindingsDefaultEntireMapAndPreserveOtherFields() {\n", """    /** Parent tabs spec 8's last bullet: the group ids are a clean break, so an old one is an unknown action. */
    @Test void aRemovedGroupIdIsReportedAsAnUnknownAction() {
        var result = parse("[keybindings]\\nclose_group = \\"cmd+w\\"");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().keybindings()).doesNotContainKey("close_group");
        assertDiagnostic(result, "keybindings.close_group", 2, 1, ConfigDiagnostic.Severity.WARNING);
        assertThat(result.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));
    }

    @Test void invalidOrCollidingBindingsDefaultEntireMapAndPreserveOtherFields() {
""", 1)])
W = T + "workspace/"
edit(W + "TabShortcutsTest.java", [(
    "import dev.jasper.app.contributions.Contributions;\n",
    "import dev.jasper.app.contributions.Contributions;\nimport dev.jasper.app.layout.ParentTabId;\n", 1)])
span(W + "TabShortcutsTest.java", "    @Test void platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs() throws Exception {\n",
     "        });\n    }\n", """    /** Parent tabs spec 8: the digits and the bracket keys (Ctrl+PageUp/PageDown off macOS) switch parent tabs, Ctrl+Tab tabs. */
    @Test void platformDefaultsSwitchParentTabsAndTabsWithinAPane() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(true), keys);
            try {
                int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke nextParent = keys.strokeFor(ActionId.NEXT_PARENT_TAB).orElseThrow();
                KeyStroke previousParent = keys.strokeFor(ActionId.PREVIOUS_PARENT_TAB).orElseThrow();
                assertThat(nextParent).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
                assertThat(previousParent).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, primary | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
                KeyStroke next = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                KeyStroke previous = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
                assertThat(keys.strokeFor(ActionId.NEXT_TAB)).contains(next);
                assertThat(keys.strokeFor(ActionId.PREVIOUS_TAB)).contains(previous);
                assertThat(keys.strokeFor(ActionId.SELECT_TAB_1)).as("the digits select parent tabs").isEmpty();
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTerminal(); owner.newTab(HOME); var stacked = owner.currentTerminal();
                var other = owner.newParentTab(HOME);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isTrue();
                assertThat(owner.currentTerminal()).as("the first parent tab, as it was left").isSameAs(stacked);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTerminal()).as("Next Tab wraps within the pane").isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(stacked);
                assertThat(root.activate(nextParent)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(other);
                assertThat(root.activate(nextParent)).as("Next Parent Tab wraps").isTrue();
                assertThat(owner.currentTerminal()).isSameAs(stacked);
                assertThat(root.activate(previousParent)).isTrue();
                assertThat(owner.currentTerminal()).isSameAs(other);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, primary)))
                    .as("no ninth parent tab: the key reaches the focused component").isFalse();
                assertThat(owner.action(ActionId.SELECT_PARENT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, primary));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isFalse();
            } finally { owner.close(); }
        });
    }

    /**
     * Review Focus 2: in a plugin's view the window's tab keys still run first: Ctrl+Tab, on both platforms now, before
     * focus traversal, and Next Parent Tab before the view's own bindings, such as a scroll pane's Ctrl+PageDown.
     */
    @Test void tabAndParentTabKeysRunBeforeAPluginViewsOwnBindings() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = CommandPaletteShortcutsTest.owner(mac)) {
                var model = new Contributions(); owner.connectContributions(model);
                var area = new JTextArea();
                model.tabs().open("dev.x", "a", "A", owner.id(), new JScrollPane(area));
                ParentTabId first = owner.activeParentTab();
                owner.newParentTab(HOME);
                owner.selectParentTab(first);
                CommandPaletteShortcutsTest.install(owner);
                assertThat(owner.selectedContent()).as("the plugin's view shows").isNotNull();
                KeyEvent tab = PaletteKeyRouterTest.press(area, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
                assertThat(manager.send(tab)).as("Ctrl+Tab").isTrue();
                assertThat(owner.selectedContent()).as("Next Tab: the terminal beside it in the pane").isNull();
                manager.send(PaletteKeyRouterTest.release(area, KeyEvent.VK_TAB));
                owner.selectTab(owner.tabs().get(1));
                KeyStroke next = owner.bindings().strokeFor(ActionId.NEXT_PARENT_TAB).orElseThrow();
                KeyEvent press = PaletteKeyRouterTest.press(area, next.getKeyCode(), next.getModifiers());
                assertThat(manager.send(press)).as("Next Parent Tab").isTrue();
                assertThat(press.isConsumed()).isTrue();
                assertThat(owner.activeParentTab()).isNotEqualTo(first);
                assertThat(manager.send(PaletteKeyRouterTest.release(area, next.getKeyCode()))).as("its release").isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(area, KeyEvent.VK_A, 0))).as("the next key types as ever").isFalse();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }
""")
edit(W + "CommandPaletteShortcutsTest.java", [(
    "                var first = owner.currentTerminal(); owner.newTab(HOME); var second = owner.currentTerminal();\n",
    "                // The digits select parent tabs (parent tabs spec 8): the second terminal is a parent tab of its own.\n"
    "                var first = owner.currentTerminal(); owner.newParentTab(HOME); var second = owner.currentTerminal();\n", 1)])
edit(W + "WindowTabsTest.java", [("            owner.invoke(ActionId.NEW_TAB);\n", "            owner.invoke(ActionId.NEW_TAB_IN_PANE);\n", 1)])
edit(W + "WorkspaceTabsTest.java", [
    ("            owner[0].invoke(ActionId.NEW_TAB);\n", "            owner[0].invoke(ActionId.NEW_TAB_IN_PANE);\n", 1),
    ('.as("New Tab opens in the plugin view\'s group")', '.as("New Tab in Pane opens in the plugin view\'s group")', 1)])
edit(W + "WorkspaceViewTest.java", [(
    """            assertThat(owner.tabShortcut(0)).as("Select Tab 1 has a shortcut").isNotEmpty();
""", """            // Select Tab 1 has no default since the digits select parent tabs (parent tabs spec 8): give it one.
            owner.setBindings(dev.jasper.app.config.KeyBindings.withOverrides(System.getProperty("os.name").startsWith("Mac"),
                java.util.Map.of("select_tab_1", "alt+1")));
            assertThat(owner.tabShortcut(0)).as("Select Tab 1 has a shortcut").isNotEmpty();
""", 1)])
edit(W + "TabMenuTest.java", [(
    """            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Terminal Window", "Open File…", "Close Tab", "Close Group",
                "Close Window");
            assertThat(items(window, "View")).contains("Maximize Group");
            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Group Left", "Focus Group Right",
                "Focus Group Up", "Focus Group Down", "Previous Prompt", "Next Prompt", "Next Tab", "Previous Tab", "Rename Tab",
                "Select Tab 1", "Select Tab 2", "Select Tab 3", "Select Tab 4", "Select Tab 5", "Select Tab 6", "Select Tab 7",
                "Select Tab 8", "Select Tab 9");
""", """            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Terminal Window", "Open File…", "Close Tab", "Close Pane",
                "Close Parent Tab", "Close Window");
            assertThat(items(window, "View")).contains("Maximize Pane");
            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Pane Left", "Focus Pane Right",
                "Focus Pane Up", "Focus Pane Down", "Previous Prompt", "Next Prompt", "New Tab in Pane", "Next Tab", "Previous Tab",
                "Rename Tab", "Next Parent Tab", "Previous Parent Tab", "Select Parent Tab 1", "Select Parent Tab 2",
                "Select Parent Tab 3", "Select Parent Tab 4", "Select Parent Tab 5", "Select Parent Tab 6", "Select Parent Tab 7",
                "Select Parent Tab 8", "Select Parent Tab 9");
""", 1)])
edit(T + "shortcuthelp/ShortcutHelpTest.java", [(
    "class ShortcutHelpTest {\n",
    """class ShortcutHelpTest {
    /** Parent tabs spec 8: the parent-tab actions join Tabs, and the pane actions are Panes. */
    @Test void theParentTabAndPaneActionsHaveTheirCategories() {
        var rows = ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true);
        for (String id : List.of("new_tab_in_pane", "close_parent_tab", "next_parent_tab", "select_parent_tab_1"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Tabs"));
        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Panes"));
    }

""", 1)])
R = {"CLOSE_GROUP": (r"\bCLOSE_GROUP\b", "CLOSE_PANE"), "MAXIMIZE_GROUP": (r"\bMAXIMIZE_GROUP\b", "MAXIMIZE_PANE"),
     "FOCUS_GROUP_": (r"\bFOCUS_GROUP_(LEFT|RIGHT|UP|DOWN)\b", r"FOCUS_PANE_\1"), "Close Group": (r"Close Group", "Close Pane"),
     "Maximize Group": (r"Maximize Group", "Maximize Pane"), "Restore Group": (r"Restore Group", "Restore Pane"),
     "Maximize group": (r"Maximize group", "Maximize pane"), "Focus Group": (r"Focus Group", "Focus Pane")}
for path, counts in [
        ("ApplicationActionsTest.java", {"FOCUS_GROUP_": 1}),
        ("ShellExitBehaviorTest.java", {"MAXIMIZE_GROUP": 1}),
        ("TabMenuTest.java", {"CLOSE_GROUP": 4, "Close Group": 4}),
        ("TerminalContextMenuTest.java", {"Maximize Group": 1}),
        ("WindowChromeContributionsTest.java", {"Maximize group": 1}),
        ("WindowChromeTest.java", {"Maximize group": 1}),
        ("WindowCommandPaletteTest.java", {"MAXIMIZE_GROUP": 1, "Restore Group": 1}),
        ("WindowManagerTest.java", {"Close Group": 1}),
        ("WorkspaceTabsTest.java", {"MAXIMIZE_GROUP": 4, "FOCUS_GROUP_": 6, "Focus Group": 1}),
        ("ZenModeTest.java", {"MAXIMIZE_GROUP": 1, "Maximize Group": 1})]:
    for key, count in counts.items():
        pattern, replacement = R[key]
        regex(W + path, pattern, replacement, count)
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ParentTabCommandsTest'`
Expected: FAIL to compile: `cannot find symbol: variable NEW_TAB_IN_PANE`.

- [ ] **Step 3: The actions, their defaults and their places**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit, span, regex
A = "jasper-app/src/main/java/dev/jasper/app/"
W = A + "workspace/"
span(A + "commands/ActionId.java", '    NEW_TAB("new_tab", "New Tab", "cmd+t", "ctrl+shift+t"),\n',
     '    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9", "ctrl+9"),\n', """    NEW_TAB("new_tab", "New Tab", "cmd+t", "ctrl+shift+t"),
    NEW_TAB_IN_PANE("new_tab_in_pane", "New Tab in Pane", "none", "none"),
    CLOSE_TAB("close_tab", "Close Tab", "cmd+w", "ctrl+shift+w"),
    CLOSE_PANE("close_pane", "Close Pane", "alt+cmd+w", "ctrl+alt+shift+w"),
    CLOSE_PARENT_TAB("close_parent_tab", "Close Parent Tab", "none", "none"),
    NEW_TERMINAL_WINDOW("new_terminal_window", "New Terminal Window", "cmd+n", "ctrl+shift+n"),
    CLOSE_WINDOW("close_window", "Close Window", "cmd+shift+w", "none"),
    MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),
    MOVE_TAB_TO_MAIN_WINDOW("move_tab_to_main_window", "Move Tab to Main Window", "none", "none"),
    OPEN_FILE("open_file", "Open File…", "none", "none"),
    SPLIT_RIGHT("split_right", "Split Right", "cmd+d", "ctrl+shift+d"),
    SPLIT_DOWN("split_down", "Split Down", "cmd+shift+d", "ctrl+alt+shift+d"),
    MAXIMIZE_PANE("maximize_pane", "Maximize Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
    TOGGLE_ZEN_MODE("toggle_zen_mode", "Zen", "none", "none"),
    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left", "ctrl+alt+shift+left"),
    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right", "ctrl+alt+shift+right"),
    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up", "ctrl+alt+shift+up"),
    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down", "ctrl+alt+shift+down"),
    NEXT_PARENT_TAB("next_parent_tab", "Next Parent Tab", "cmd+shift+]", "ctrl+pagedown"),
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
""")
edit(W + "WorkspaceActions.java", [
    ("            case NEW_TAB -> owner.newTab(owner.directory());\n", """            case NEW_TAB -> owner.newParentTab(owner.directory());
            case NEW_TAB_IN_PANE -> owner.newTab(owner.directory());
            case CLOSE_PARENT_TAB -> owner.closeParentTab(owner.activeParentTab());
            case NEXT_PARENT_TAB -> owner.selectParentTabRelative(1);
            case PREVIOUS_PARENT_TAB -> owner.selectParentTabRelative(-1);
            case SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                 SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9 ->
                owner.selectParentTabAt(id.ordinal() - ActionId.SELECT_PARENT_TAB_1.ordinal());
""", 1),
    ("                    case COMMAND_PALETTE, NEW_TAB, NEW_TERMINAL_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT, SPLIT_DOWN -> true;\n",
     "                    case COMMAND_PALETTE, NEW_TAB, NEW_TAB_IN_PANE, NEW_TERMINAL_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT,\n"
     "                         SPLIT_DOWN -> true;\n", 1),
    ("                    case MAXIMIZE_GROUP -> owner.workspace().model().groups().size() > 1;\n", """                    case CLOSE_PARENT_TAB -> owner.selectedTab() != null;
                    case NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB -> owner.parentTabCount() > 1;
                    case SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                         SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9 ->
                        id.ordinal() - ActionId.SELECT_PARENT_TAB_1.ordinal() < owner.parentTabCount();
                    case MAXIMIZE_GROUP -> owner.workspace().model().groups().size() > 1;
""", 1)])
regex(W + "WorkspaceActions.java", r"\bCLOSE_GROUP\b", "CLOSE_PANE", 2)
regex(W + "WorkspaceActions.java", r"\bMAXIMIZE_GROUP\b", "MAXIMIZE_PANE", 2)
regex(W + "WorkspaceActions.java", r"\bFOCUS_GROUP_(LEFT|RIGHT|UP|DOWN)\b", r"FOCUS_PANE_\1", 8)
edit(W + "WindowCommands.java", [
    ('                case NEW_TAB -> "square-plus"; case NEW_TERMINAL_WINDOW -> "app-window";\n',
     '                case NEW_TAB, NEW_TAB_IN_PANE -> "square-plus"; case NEW_TERMINAL_WINDOW -> "app-window";\n', 1),
    ('                case OPEN_SETTINGS -> List.of("preferences", "configuration");\n', '''                case NEW_TAB -> List.of("parent tab", "terminal");
                case NEW_TAB_IN_PANE -> List.of("stack", "pane", "terminal");
                case CLOSE_PARENT_TAB, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB -> List.of("parent tab");
                case OPEN_SETTINGS -> List.of("preferences", "configuration");
''', 1)])
regex(W + "WindowCommands.java", r"\bMAXIMIZE_GROUP\b", "MAXIMIZE_PANE", 3)
regex(W + "WindowCommands.java", r"Maximize Group", "Maximize Pane", 1)
regex(W + "WindowCommands.java", r"Restore Group", "Restore Pane", 1)
edit(W + "WindowChrome.java", [
    ("ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP,\n            ActionId.CLOSE_WINDOW, ActionId.OPEN_SETTINGS,",
     "ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,\n            ActionId.CLOSE_PARENT_TAB, ActionId.CLOSE_WINDOW, ActionId.OPEN_SETTINGS,", 1),
    ("""    /** The Tab menu's group and prompt items, before its tab items (workspace plan 3b Rulings 7). */
    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_GROUP_LEFT,
        ActionId.FOCUS_GROUP_RIGHT, ActionId.FOCUS_GROUP_UP, ActionId.FOCUS_GROUP_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
    private static final ActionId[] TAB_ITEMS = {ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
        ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9};

    /** Tab: splitting and focusing groups and moving between prompts, a rule, then the tab commands. */
    private JMenu tabMenu() {
        JMenu menu = menu("Tab", GROUP_ITEMS);
        menu.addSeparator();
        for (ActionId id : TAB_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
        return menu;
    }
""", """    /** The Tab menu's pane and prompt items, before its tab and parent tab items (workspace plan 3b Rulings 7). */
    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
        ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
    private static final ActionId[] TAB_ITEMS = {ActionId.NEW_TAB_IN_PANE, ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB};
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
""", 1)])
regex(W + "WindowChrome.java", r"\bMAXIMIZE_GROUP\b", "MAXIMIZE_PANE", 4)
regex(W + "WindowChrome.java", r"Maximize group", "Maximize pane", 1)
regex(W + "ClassicHeader.java", r"\bMAXIMIZE_GROUP\b", "MAXIMIZE_PANE", 1)
edit(W + "GroupView.java", [(
    "    void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB); }\n",
    "    void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB_IN_PANE); }\n", 1)])
edit(W + "WindowContent.java", [
    ('        menu.add(tabMenuItem("Close Group", ActionId.CLOSE_GROUP, () -> closeGroup(tab)));\n',
     '        menu.add(tabMenuItem("Close Pane", ActionId.CLOSE_PANE, () -> closeGroup(tab)));\n', 1),
    ("     * to Main Window in a child window, Close Tab, Close Others and Close Group, then the content's commands.\n",
     "     * to Main Window in a child window, Close Tab, Close Others and Close Pane, then the content's commands.\n", 1),
    ("    /** Maximize Group: the active group fills the workspace, or the splits come back. */\n",
     "    /** Maximize Pane: the active pane fills the workspace, or the splits come back. */\n", 1),
    ("    /** Focus Group: the neighbouring group that way takes focus with its selected tab; nothing past the edge. */\n",
     "    /** Focus Pane: the neighbouring pane that way takes focus with its selected tab; nothing past the edge. */\n", 1),
    ("""     * Runs a root-bound shortcut whose key is also one of the focus owner's focus-traversal keys before focus traversal
     * takes it, such as Linux Ctrl+Tab and Ctrl+Shift+Tab in a text area, a table or a field, and swallows that key's
     * typed and released tail wherever focus went. Plain Tab is no Keymap binding, so it still traverses.
""", """     * Runs a root-bound shortcut whose key is also one of the focus owner's focus-traversal keys before focus traversal
     * takes it, such as Ctrl+Tab and Ctrl+Shift+Tab in a text area, a table or a field, and Next and Previous Parent Tab
     * before the focus owner's own bindings, such as a scroll pane's Ctrl+PageUp and Ctrl+PageDown (workspace plan 7a
     * Rulings 14); it swallows that key's typed and released tail wherever focus went. Plain Tab is no Keymap binding, so
     * it still traverses.
""", 1),
    ("                if (!ownsKeySource(focus) || !traversalKey(focus, event)\n",
     "                if (!ownsKeySource(focus) || !(traversalKey(focus, event) || parentTabKey(stroke))\n", 1),
    ("""    private static boolean traversalKey(Component focus, KeyEvent event) {
""", """    /** Whether {@code stroke} is Next or Previous Parent Tab's, which run before the focus owner's own bindings. */
    private boolean parentTabKey(KeyStroke stroke) {
        String bound = rootKeys.get(stroke);
        return ActionId.NEXT_PARENT_TAB.id().equals(bound) || ActionId.PREVIOUS_PARENT_TAB.id().equals(bound);
    }

    private static boolean traversalKey(Component focus, KeyEvent event) {
""", 1)])
S = A + "shortcuthelp/ShortcutCatalog.java"
edit(S, [
    ('case "Tabs"->"window";case "Groups"->"split";', 'case "Tabs"->"window";case "Panes"->"split";', 1),
    ("""            case NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW, MOVE_TAB_TO_MAIN_WINDOW,
                SELECT_TAB_1, SELECT_TAB_2,
                SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5, SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> "Tabs";
            case SPLIT_RIGHT, SPLIT_DOWN, CLOSE_GROUP, MAXIMIZE_GROUP, FOCUS_GROUP_LEFT, FOCUS_GROUP_RIGHT,
                FOCUS_GROUP_UP, FOCUS_GROUP_DOWN -> "Groups";
""", """            case NEW_TAB, NEW_TAB_IN_PANE, CLOSE_TAB, CLOSE_PARENT_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW,
                MOVE_TAB_TO_MAIN_WINDOW, NEXT_PARENT_TAB, PREVIOUS_PARENT_TAB,
                SELECT_PARENT_TAB_1, SELECT_PARENT_TAB_2, SELECT_PARENT_TAB_3, SELECT_PARENT_TAB_4, SELECT_PARENT_TAB_5,
                SELECT_PARENT_TAB_6, SELECT_PARENT_TAB_7, SELECT_PARENT_TAB_8, SELECT_PARENT_TAB_9,
                SELECT_TAB_1, SELECT_TAB_2,
                SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5, SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> "Tabs";
            case SPLIT_RIGHT, SPLIT_DOWN, CLOSE_PANE, MAXIMIZE_PANE, FOCUS_PANE_LEFT, FOCUS_PANE_RIGHT,
                FOCUS_PANE_UP, FOCUS_PANE_DOWN -> "Panes";
""", 1)])
PY
git grep -n -E 'CLOSE_GROUP|MAXIMIZE_GROUP|FOCUS_GROUP_|close_group|maximize_group|focus_group_|Close Group|Maximize Group|Restore Group|Maximize group|"Groups"' -- jasper-app/src plugins jasper-sdk jasper-sdk-testkit
```

Expected: the `git grep` lists only `settingsui/CorePages.java` and `CorePagesTest.java` (Notifications › Groups, a
different thing), and `ParentTabCommandsTest`'s `find("close_group")` and `ConfigLoaderTest`'s
`aRemovedGroupIdIsReportedAsAnUnknownAction` (`close_group` and `keybindings.close_group`), which pin the clean break.

- [ ] **Step 4: Ledger row**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "ParentTabCommandsTest.java", "app.workspace", "ParentTabCommandsTest", T + "ParentTabCommandsTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7a Task 4; the parent-tab and pane commands")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --continue`
Expected: PASS (`ParentTabCommandsTest` 5, `KeymapDefaultsTest` 5, `ShortcutHelpTest` +1, `TabShortcutsTest` +1,
`ConfigLoaderTest` +1; the
renamed tests unchanged in number). A test this plan does not name that fails on a removed id, a label or a key is
ported the same way and recorded in the banner. Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Parent tabs: their commands, keys and menus

New Tab opens a parent tab (its empty workspace's only one in an empty
main window), New Tab in Pane stacks a terminal in the active pane,
and Close Parent Tab, Next and Previous Parent Tab and Select Parent
Tab 1 to 9 act on parent tabs: Cmd+Shift+] and [ and Cmd+1-9 on macOS,
Ctrl+PageDown and PageUp and Ctrl+1-9 elsewhere, which leaves the tool
windows their Alt digits. Next and Previous Tab switch within the pane
on Ctrl+Tab on every platform; Select Tab 1-9 have no default. The
group commands are pane commands now (close_pane, maximize_pane,
focus_pane_*), and every label and the Keymap say Pane. Next and
Previous Parent Tab run before a plugin view's own bindings.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The parent-tab bar

Spec 4 and 7: the bar sits above the workspace and reuses the look's strip; each parent tab shows as its active tab;
under the equal tabs it shows from two parent tabs, under the classic tabs always. The strips read a `StripSource`,
which `GroupView` and the new `ParentTabBar` implement (Rulings 16-19); Enterprise's mock test reads the pane's strip
under the bar (Rulings 18).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/StripSource.java`, `ParentTabBar.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java`, `WindowTabs.java`, `ClassicTabs.java`,
  `TabStrip.java`, `WorkspaceChrome.java`, `ParentTabsView.java`, `WindowContent.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabBarTest.java` (new),
  `EnterpriseWorkstationMockTest.java`, `ClassicTabsTest.java`
- Ledger: three rows

**Interfaces:**
- Consumes: Task 3's `ParentTabsView` (`views`, `active`, `count`, `model`), `WindowContent.parentTabOf`,
  `selectParentTab`, `closeParentTab`, `tabTitle`, `tabIcon`, `tabTooltip`, `running`, `invoke`, `dockDrag`; Task 4's
  `ActionId.NEW_TAB` (a parent tab), `SELECT_PARENT_TAB_1`-`9`, `CLOSE_PARENT_TAB`.
- Produces: package `sealed interface StripSource permits GroupView, ParentTabBar` with `WindowContent owner()`,
  `List<Tab> tabs()`, `Tab selected()`, `String title(Tab)`, `Icon icon(Tab)`, `String tooltip(Tab)`, `String
  shortcut(int)`, `boolean terminal(Tab)`, `boolean running(Tab)`, `JPopupMenu menu(Tab)`, `void select(Tab)`, `void
  close(Tab)`, `void pressed(Tab, MouseEvent)`, `void dragged(MouseEvent)`, `void released(MouseEvent)`, `void
  newTab()`, `String newTabName()` (the classic "+"'s name), `boolean moved(Tab)`, `boolean animates()`; `final class
  ParentTabBar implements StripSource` with
  `ParentTabBar(WindowContent, ParentTabsView)`, `TabStrip strip()`, `void refresh()`, `void close()`;
  `WorkspaceChrome.tabs(StripSource)`, `boolean WorkspaceChrome.showsParentTabBar(int count)`; `ParentTabBar
  ParentTabsView.bar()`; on `WindowContent`: `TabStrip parentTabBar()`, `String parentTabShortcut(int)`, `JPopupMenu
  parentTabMenu(Tab)` (7b adds Pop Out Parent Tab and Move to Main Window to it; 7c's parent-tab drag starts from
  `ParentTabBar.pressed`).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ParentTabBarTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 4 and 7: the parent-tab bar, the look's strip over a window's parent tabs. */
class ParentTabBarTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** New Tab in {@code owner}, its terminal renamed {@code title}: the new parent tab's tab. */
    private static Tab open(WindowContent owner, String title) {
        owner.newParentTab(HOME);
        owner.currentTerminal().rename(title);
        owner.update();
        return owner.selectedTab();
    }

    private static AbstractButton button(WindowTabs bar, String name) { return (AbstractButton) WindowTabsTest.named(bar, name); }

    @Test void theStandardLookShowsTheBarFromTwoParentTabs() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.update();
            var bar = (WindowTabs) owner.parentTabBar();
            assertThat(bar.getName()).isEqualTo("parentTabBar");
            assertThat(bar.isVisible()).as("one parent tab: no bar").isFalse();
            Tab second = open(owner, "second");
            assertThat(bar.isVisible()).isTrue();
            assertThat(bar.motionRunning()).as("its entries never move").isFalse();
            assertThat(WindowTabsTest.named(bar, "select:first")).isNotNull();
            assertThat(WindowTabsTest.named(bar, "select:second")).isNotNull();
            assertThat(owner.windowTabs()).as("a pane's strip is another strip").isNotSameAs(bar);
            assertThat(WindowTabsTest.named(owner, "windowTabs")).as("a search by name meets a pane's strip").isSameAs(owner.windowTabs());
            owner.closeTab(second);
            assertThat(bar.isVisible()).as("one parent tab again: the bar hides at once").isFalse();
        });
    }

    @Test void eachParentTabShowsAsItsActiveTab() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("left");
            Tab left = owner.selectedTab();
            owner.newTab(HOME);
            owner.currentTerminal().rename("right");
            Tab right = owner.selectedTab();
            Tab other = open(owner, "other");
            var bar = (WindowTabs) owner.parentTabBar();
            var source = owner.parentTabs().bar();
            assertThat(source.tabs()).as("each parent tab's active tab, in bar order").containsExactly(right, other);
            assertThat(source.selected()).isSameAs(other);
            assertThat(WindowTabsTest.named(bar, "select:right")).isNotNull();
            owner.selectTab(left);
            assertThat(source.tabs()).containsExactly(left, other);
            assertThat(source.selected()).isSameAs(left);
            assertThat(WindowTabsTest.named(bar, "select:left")).as("the entry follows its parent tab's active tab").isNotNull();
            assertThat(WindowTabsTest.named(bar, "select:right")).isNull();
            assertThat(source.shortcut(0)).isEqualTo(owner.shortcutText(ActionId.SELECT_PARENT_TAB_1)).isNotEmpty();
            assertThat(source.shortcut(9)).isEmpty();
        });
    }

    @Test void aClickShowsAParentTabAndItsCloseAndItsMenuCloseOne() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.currentTerminal().rename("first");
            owner.update();
            ParentTabId first = owner.activeParentTab();
            Tab second = open(owner, "second");
            Tab third = open(owner, "third");
            var bar = (WindowTabs) owner.parentTabBar();
            button(bar, "select:first").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            button(bar, "close:second").doClick();
            assertThat(second.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(2);
            var menu = owner.parentTabMenu(third);
            assertThat(Arrays.stream(menu.getComponents()).map(item -> ((JMenuItem) item).getText())).containsExactly("Close Parent Tab");
            ((JMenuItem) menu.getComponent(0)).doClick();
            assertThat(third.closed()).isTrue();
            assertThat(owner.parentTabCount()).isEqualTo(1);
        });
    }

    @Test void theBarSitsAboveTheShownWorkspace() throws Exception {
        edt(() -> {
            var owner = content(idleLauncher(pending));
            open(owner, "second");
            owner.setSize(1000, 700);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            var parents = owner.parentTabs();
            var bar = owner.parentTabBar().component();
            assertThat(bar.getBounds()).isEqualTo(new Rectangle(0, 0, parents.getWidth(), owner.tabHeight()));
            assertThat(owner.workspace().getBounds()).as("the shown workspace under it")
                .isEqualTo(new Rectangle(0, owner.tabHeight(), parents.getWidth(), parents.getHeight() - owner.tabHeight()));
            assertThat(parents.getComponent(parents.getComponentCount() - 1)).as("the bar is the last child").isSameAs(bar);
        });
    }

    @Test void enterpriseAlwaysShowsTheBarAndItsPlusOpensAParentTab() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                var bar = (ClassicTabs) owner.parentTabBar();
                assertThat(bar.isVisible()).as("one parent tab: the bar shows").isTrue();
                assertThat(bar.add().getToolTipText()).as("the bar's + opens a parent tab").isEqualTo("New Tab");
                assertThat(((ClassicTabs) owner.tabBar()).add().getToolTipText()).as("a pane's + stacks in its pane")
                    .isEqualTo("New Tab in Pane");
                assertThat(bar.entries()).hasSize(1);
                ((AbstractButton) bar.add()).doClick();
                assertThat(owner.parentTabCount()).as("its + opens a parent tab").isEqualTo(2);
                assertThat(bar.entries()).hasSize(2);
                assertThat(owner.tabBar()).as("and the pane's strip shows under it").isInstanceOf(ClassicTabs.class).isNotSameAs(bar);
                assertThat(owner.tabBar().component().isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 3. */
    @Test void enterprisesBarShowsNoEntryForTheEmptyMainWorkspace() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                boolean macOs = System.getProperty("os.name").startsWith("Mac");
                var main = track(new WindowContent(WindowRole.MAIN, idleLauncher(pending), null, true, path -> { }, () -> { },
                    () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, ClassicTabsTest.classic()));
                var bar = (ClassicTabs) main.parentTabBar();
                assertThat(bar.isVisible()).isTrue();
                assertThat(bar.entries()).as("its only parent tab is empty").isEmpty();
                ((AbstractButton) bar.add()).doClick();
                assertThat(main.parentTabCount()).as("its + fills the empty workspace").isEqualTo(1);
                assertThat(bar.entries()).hasSize(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

Enterprise's mock test reads the pane's strip where it is, under the bar, and a pane's classic "+" is New Tab in Pane:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit, regex
M = "jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java"
edit(M, [(
    "        // The underline row: mock y 233",
    "        // The pane's strip, under the parent-tab bar Enterprise always shows (parent tabs spec 7; workspace plan 7a Rulings 18).\n"
    "        // The underline row: mock y 233", 1)])
regex(M, r"25 \+ (?:35|TOOLBAR \+ 1)", "mounted.firstTab().y", 3)
edit("jasper-app/src/test/java/dev/jasper/app/workspace/ClassicTabsTest.java", [(
    '                assertThat(button.getAccessibleName()).isEqualTo("New Tab");\n',
    '                assertThat(button.getAccessibleName()).as("a pane\'s + stacks in its pane").isEqualTo("New Tab in Pane");\n', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ParentTabBarTest'`
Expected: FAIL to compile: `cannot find symbol: method parentTabBar()`.

- [ ] **Step 3: The strip's source and the bar**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StripSource.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * What a tab strip shows and does (workspace tabs spec 10; parent tabs spec 7): a pane's tabs ({@link GroupView}), or a
 * window's parent tabs, each shown as its active tab ({@link ParentTabBar}). A strip lists, selects, closes and opens
 * through its source only. EDT only.
 */
sealed interface StripSource permits GroupView, ParentTabBar {
    /** The window the strip is in. */
    WindowContent owner();
    /** The entries in strip order. */
    List<Tab> tabs();
    /** The entry shown as selected; null for none. */
    Tab selected();
    String title(Tab tab);
    Icon icon(Tab tab);
    String tooltip(Tab tab);
    /** The shortcut that selects the entry at {@code index}, as this platform writes it; empty for none. */
    String shortcut(int index);
    /** Whether the entry is a terminal's, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab);
    /** Whether the entry's shell runs, which the strip tells assistive technology. */
    boolean running(Tab tab);
    JPopupMenu menu(Tab tab);
    void select(Tab tab);
    void close(Tab tab);
    /** A left press on an entry, where a drag may start. */
    void pressed(Tab tab, MouseEvent event);
    /** A drag event from the strip. */
    void dragged(MouseEvent event);
    /** A release on the strip. */
    void released(MouseEvent event);
    /** The classic tabs' "+". */
    void newTab();
    /** The "+"'s name: what it opens. */
    String newTabName();
    /** Whether the entry takes its place at once, without the open motion. */
    boolean moved(Tab tab);
    /** Whether the strip's entries may move as they open and close. */
    boolean animates();
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabBar.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * A window's parent-tab bar (parent tabs spec 7): the look's strip over the window's parent tabs, each shown as its
 * active tab, as terminal apps title a tab by its active pane (spec 4). It shows while the look says so for the window's
 * count of parent tabs: from two under the equal tabs, always under the classic tabs, which show no entry for the main
 * window's empty workspace. A click shows a parent tab, its close button and its menu close one after its tabs' guards,
 * the classic "+" is New Tab; its entries never move, and a press on one starts no drag. EDT only.
 */
final class ParentTabBar implements StripSource {
    private final WindowContent owner;
    private final ParentTabsView parentTabs;
    private final TabStrip strip;

    ParentTabBar(WindowContent owner, ParentTabsView parentTabs) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.parentTabs = Objects.requireNonNull(parentTabs, "parentTabs");
        strip = owner.look().tabs(this);
        // A search by name meets a pane's strip, never the bar (workspace plan 7a Rulings 19).
        strip.component().setName("parentTabBar");
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        // The window starts with one parent tab; refresh keeps the bar's visibility from then on.
        strip.component().setVisible(owner.look().showsParentTabBar(1));
    }

    TabStrip strip() { return strip; }

    /** Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last. */
    void refresh() {
        boolean shown = owner.look().showsParentTabBar(parentTabs.count());
        if (!shown) strip.component().setVisible(false);
        strip.setActive(owner.windowActive());
        strip.component().setVisible(shown);
    }

    void close() { strip.close(); }

    @Override public WindowContent owner() { return owner; }

    /** Each parent tab's active tab, in bar order; the main window's empty workspace has none. */
    @Override public List<Tab> tabs() {
        var shown = new ArrayList<Tab>();
        for (WorkspaceView view : parentTabs.views()) if (view.selected() != null) shown.add(view.selected());
        return shown;
    }

    @Override public Tab selected() { return parentTabs.active().selected(); }
    @Override public String title(Tab tab) { return owner.tabTitle(tab); }
    @Override public Icon icon(Tab tab) { return owner.tabIcon(tab); }
    @Override public String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** Select Parent Tab's shortcut for the entry at {@code index}. */
    @Override public String shortcut(int index) { return owner.parentTabShortcut(index); }
    @Override public boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    @Override public boolean running(Tab tab) { return owner.running(tab); }
    @Override public JPopupMenu menu(Tab tab) { return owner.parentTabMenu(tab); }
    @Override public void select(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::selectParentTab); }
    @Override public void close(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::closeParentTab); }
    /** A press ends a dock drag whose release was lost; a parent tab does not drag. */
    @Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }
    @Override public void dragged(MouseEvent event) { }
    @Override public void released(MouseEvent event) { }
    /** The classic tabs' "+": New Tab, a parent tab. */
    @Override public void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: the bar's "+" opens a parent tab. */
    @Override public String newTabName() { return ActionId.NEW_TAB.label(); }
    /** The bar's entries take their places at once. */
    @Override public boolean moved(Tab tab) { return true; }
    @Override public boolean animates() { return false; }
}
```

- [ ] **Step 4: The strips read a source; the parent tabs show the bar**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit, span, regex
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "GroupView.java", [
    ("final class GroupView extends JPanel {\n", "final class GroupView extends JPanel implements StripSource {\n", 1),
    ("    WindowContent owner() { return owner; }\n", "    @Override public WindowContent owner() { return owner; }\n", 1),
    ("    List<Tab> tabs() { return tabs; }\n", "    @Override public List<Tab> tabs() { return tabs; }\n", 1),
    ("    Tab selected() { return selected; }\n", "    @Override public Tab selected() { return selected; }\n", 1),
    ("    String title(Tab tab) { return owner.tabTitle(tab); }\n", "    @Override public String title(Tab tab) { return owner.tabTitle(tab); }\n", 1),
    ("    Icon icon(Tab tab) { return owner.tabIcon(tab); }\n", "    @Override public Icon icon(Tab tab) { return owner.tabIcon(tab); }\n", 1),
    ("    String tooltip(Tab tab) { return owner.tabTooltip(tab); }\n", "    @Override public String tooltip(Tab tab) { return owner.tabTooltip(tab); }\n", 1),
    ('    String shortcut(int index) { return activeGroup() ? owner.tabShortcut(index) : ""; }\n',
     '    @Override public String shortcut(int index) { return activeGroup() ? owner.tabShortcut(index) : ""; }\n', 1),
    ("    boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }\n",
     "    @Override public boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }\n", 1),
    ("    boolean running(Tab tab) { return owner.running(tab); }\n", "    @Override public boolean running(Tab tab) { return owner.running(tab); }\n", 1),
    ("    JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }\n", "    @Override public JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }\n", 1),
    ("    void select(Tab tab) { owner.selectTab(tab); }\n", "    @Override public void select(Tab tab) { owner.selectTab(tab); }\n", 1),
    ("    void close(Tab tab) { owner.closeTab(tab); }\n", "    @Override public void close(Tab tab) { owner.closeTab(tab); }\n", 1),
    ("    void pressed(Tab tab, MouseEvent event) {\n", "    @Override public void pressed(Tab tab, MouseEvent event) {\n", 1),
    ("    void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }\n",
     "    @Override public void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }\n", 1),
    ("    void released(MouseEvent event) { owner.tabDrag().released(event); }\n",
     "    @Override public void released(MouseEvent event) { owner.tabDrag().released(event); }\n", 1),
    ("    void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB_IN_PANE); }\n",
     "    @Override public void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB_IN_PANE); }\n"
     "    /** New Tab in Pane: this pane's + stacks a tab in it. */\n"
     "    @Override public String newTabName() { return ActionId.NEW_TAB_IN_PANE.label(); }\n", 1),
    ("    boolean moved(Tab tab) { return workspace.moved(tab); }\n", """    @Override public boolean moved(Tab tab) { return workspace.moved(tab); }
    /** The look's motion, which the equal tabs follow as tabs open and close. */
    @Override public boolean animates() { return owner.lookAnimates(); }
""", 1)])
edit(W + "WindowTabs.java", [
    ("    private final GroupView group;\n", "    private final StripSource source;\n", 1),
    ("""    WindowTabs(GroupView group) { this(group, System::nanoTime); }
    WindowTabs(GroupView group, LongSupplier clock) {
        super(null);
        this.group = group; this.clock = clock;
""", """    WindowTabs(StripSource source) { this(source, System::nanoTime); }
    WindowTabs(StripSource source, LongSupplier clock) {
        super(null);
        this.source = source; this.clock = clock;
""", 1),
    ("|| !group.owner().lookAnimates()", "|| !source.animates()", 1),
    (""" * Rulings 27). EDT only.
 */
final class WindowTabs""", """ * Rulings 27). It shows a window's parent tabs too, each as its active tab, and those never move (parent tabs spec 7).
 * EDT only.
 */
final class WindowTabs""", 1)])
regex(W + "WindowTabs.java", r"\bgroup\.", "source.", 24)
edit(W + "ClassicTabs.java", [
    ("    private final GroupView group;\n", "    private final StripSource source;\n", 1),
    ("""    ClassicTabs(GroupView group) {
        super(null);
        this.group = group;
""", """    ClassicTabs(StripSource source) {
        super(null);
        this.source = source;
        // The "+" says what it opens: New Tab in Pane in a pane's strip, New Tab in the parent-tab bar.
        add.setToolTipText(source.newTabName());
        add.getAccessibleContext().setAccessibleName(source.newTabName());
""", 1),
    (" * Never moves.\n */\n", " * Never moves. It shows a window's parent tabs too, each as its active tab (parent tabs spec 7).\n */\n", 1),
    ('    /** The "+" after the last tab: a flat, unfocusable button that opens a new tab in this group on a release over it. */\n',
     '    /** The "+" after the last tab: a flat, unfocusable button that runs its source\'s {@link StripSource#newTab} on a release. */\n', 1),
    ('            setToolTipText("New Tab");\n            getAccessibleContext().setAccessibleName("New Tab");\n', "", 1)])
regex(W + "ClassicTabs.java", r"\bgroup\.", "source.", 21)
edit(W + "TabStrip.java", [(
    """ * One tab group's strip (workspace tabs spec 10; Enterprise presentation spec 10.5): today's equal tabs, hidden while
 * their pane holds a single tab (parent tabs spec 7), or the classic tabs, always shown. It lists, selects, closes and
 * opens tabs in its group view only, its presses start the tab drag (workspace tabs spec 12), and it shows as active while
 * that group is the active group of an active window. EDT only.
""", """ * One tab group's strip, or a window's parent-tab bar (workspace tabs spec 10; parent tabs spec 7; Enterprise presentation
 * spec 10.5): today's equal tabs, hidden while their pane holds a single tab or their window a single parent tab, or the
 * classic tabs, always shown. It lists, selects, closes and opens through its {@link StripSource} only, a group's presses
 * start the tab drag (workspace tabs spec 12), and it shows as active while its group is the active group of an active
 * window, or while the bar's window is active. EDT only.
""", 1)])
edit(W + "WorkspaceChrome.java", [
    ("    /** The editor tabs, made for each tab group by {@link #tabs(GroupView)}. */\n",
     "    /** The editor tabs, made for each tab group and each window's parent-tab bar by {@link #tabs(StripSource)}. */\n", 1),
    ("""    /** One tab group's strip (spec 10.5; workspace tabs spec 10). */
    TabStrip tabs(GroupView group) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(group);
            case CLASSIC -> new ClassicTabs(group);
        };
    }
""", """    /** One tab group's strip, or a window's parent-tab bar (spec 10.5; workspace tabs spec 10; parent tabs spec 7). */
    TabStrip tabs(StripSource source) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(source);
            case CLASSIC -> new ClassicTabs(source);
        };
    }
""", 1),
    ("""     * equal tabs show unless the pane holds a single tab.
     */
    boolean showsStrip(int count) {
        return switch (tabs) {
            case EQUAL -> count != 1;
            case CLASSIC -> true;
        };
    }
""", """     * equal tabs show unless the pane holds a single tab.
     */
    boolean showsStrip(int count) {
        return switch (tabs) {
            case EQUAL -> count != 1;
            case CLASSIC -> true;
        };
    }

    /**
     * Whether a window holding {@code count} parent tabs shows its parent-tab bar (parent tabs spec 7): the classic tabs
     * (Enterprise) always; the equal tabs from two parent tabs.
     */
    boolean showsParentTabBar(int count) {
        return switch (tabs) {
            case EQUAL -> count > 1;
            case CLASSIC -> true;
        };
    }
""", 1)])
P = W + "ParentTabsView.java"
edit(P, [
    (""" * make up, which every layout change keeps current. It stands where the terminal column stood, in the dock's centre,
 * and paints nothing itself. Only the shown parent tab's workspace is visible; the others stay in the tree, hidden, with
 * their groups, tabs and sessions as they were, so a switch restarts nothing. A parent tab joins holding its first tab
 * and leaves with its last one, except the window's only parent tab, which the main window keeps as its empty
 * workspace. EDT only.
""", """ * make up, which every layout change keeps current. It stands where the terminal column stood, in the dock's centre,
 * with the parent-tab bar ({@link ParentTabBar}) on top, and paints nothing itself. Only the shown parent tab's
 * workspace is visible; the others stay in the tree, hidden, with their groups, tabs and sessions as they were, so a
 * switch restarts nothing. A parent tab joins holding its first tab and leaves with its last one, except the window's
 * only parent tab, which the main window keeps as its empty workspace. EDT only.
""", 1),
    ("""    private ParentTabs model;
    private boolean closed;
""", """    /** The bar over the parent tabs (parent tabs spec 7), the view's last child (workspace plan 7a Rulings 19). */
    private final ParentTabBar bar;
    private ParentTabs model;
    private boolean closed;
""", 1),
    ("        model = ParentTabs.of(new ParentTab(first.id(), first.model()));\n",
     "        model = ParentTabs.of(new ParentTab(first.id(), first.model()));\n        bar = new ParentTabBar(owner, this);\n"
     "        add(bar.strip().component());\n", 1),
    ("    ParentTabs model() { return model; }\n", "    ParentTabs model() { return model; }\n    /** The parent-tab bar. */\n    ParentTabBar bar() { return bar; }\n", 1),
    ("""    /** Refreshes every parent tab's strips. */
    void refresh() {
        if (closed) return;
        for (WorkspaceView view : views.values()) view.refresh();
    }
""", """    /** Refreshes the bar and every parent tab's strips. */
    void refresh() {
        if (closed) return;
        bar.refresh();
        for (WorkspaceView view : views.values()) view.refresh();
    }
""", 1),
    ("""        for (WorkspaceView view : views.values()) view.applyTheme(theme, updateDelegates);
    }
""", """        for (WorkspaceView view : views.values()) view.applyTheme(theme, updateDelegates);
        WorkspaceTheme.apply(bar.strip().component(), theme);
        bar.refresh();
    }
""", 1),
    ("""        for (WorkspaceView view : views.values()) view.setAnimations(settings);
    }
""", """        for (WorkspaceView view : views.values()) view.setAnimations(settings);
        bar.strip().setAnimations(settings);
    }
""", 1),
    ("""    /** Every strip takes a new tab height. */
    void revalidateStrips() {
""", """    /** Every strip, the bar's too, takes a new tab height. */
    void revalidateStrips() {
        bar.strip().component().revalidate();
        bar.strip().component().repaint();
""", 1),
    ("""        closed = true;
        for (WorkspaceView view : views.values()) view.close();
""", """        closed = true;
        bar.close();
        for (WorkspaceView view : views.values()) view.close();
""", 1)])
span(P, "    /** Every parent tab's workspace fills the view; only the shown one is visible. */\n",
     "            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);\n        }\n    }\n",
     """    /** The bar on top at its preferred height while it shows; every parent tab's workspace fills the rest. */
    private static final class Stack implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return size(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return size(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right, top = insets.top;
            Component bar = bar(parent);
            if (bar != null) {
                int height = bar.isVisible() ? bar.getPreferredSize().height : 0;
                bar.setBounds(insets.left, top, width, height);
                top += height;
            }
            for (Component child : parent.getComponents())
                if (child != bar) child.setBounds(insets.left, top, width, Math.max(0, parent.getHeight() - top - insets.bottom));
        }
        /** The bar's strip; null while the view is being built. */
        private static Component bar(Container parent) {
            ParentTabBar bar = ((ParentTabsView) parent).bar;
            return bar == null ? null : bar.strip().component();
        }
        private static Dimension size(Container parent, boolean minimum) {
            Component bar = bar(parent);
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                if (child == bar) continue;
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            if (bar != null && bar.isVisible()) {
                Dimension size = minimum ? bar.getMinimumSize() : bar.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
""")
edit(W + "WindowContent.java", [
    ("    public WindowLayout windowLayout() { return parentTabs.model(); }\n",
     "    public WindowLayout windowLayout() { return parentTabs.model(); }\n"
     "    /** The parent-tab bar's strip (parent tabs spec 7). */\n    TabStrip parentTabBar() { return parentTabs.bar().strip(); }\n", 1),
    ("""        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
""", """        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    /** Select Parent Tab {@code index + 1}'s shortcut as this platform writes it; "" past 9 or while it has none. */
    String parentTabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        return shortcutText(ActionId.valueOf("SELECT_PARENT_TAB_" + (index + 1)));
    }
""", 1),
    ("    /** A tab menu item showing {@code shortcut}'s binding, if any, that runs {@code run}. */\n", """    /**
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

    /** A tab menu item showing {@code shortcut}'s binding, if any, that runs {@code run}. */
""", 1)])
PY
grep -c '\bgroup\b' jasper-app/src/main/java/dev/jasper/app/workspace/WindowTabs.java jasper-app/src/main/java/dev/jasper/app/workspace/ClassicTabs.java
```

Expected: `2` for each file, both class Javadoc lines: no code names `group` in either strip.

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); import ledger
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "StripSource.java", "app.workspace", "StripSource", T + "ParentTabBarTest.java",
           "New in workspace-tabs plan 7a Task 5; what a tab strip shows and does")
ledger.add(M + "ParentTabBar.java", "app.workspace", "ParentTabBar", T + "ParentTabBarTest.java",
           "New in workspace-tabs plan 7a Task 5; a window's parent-tab bar")
ledger.add(T + "ParentTabBarTest.java", "app.workspace", "ParentTabBarTest", T + "ParentTabBarTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 7a Task 5; the parent-tab bar in both looks")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`ParentTabBarTest` 6; `EnterpriseWorkstationMockTest` unchanged in number and passing where Plex
resolves). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Parent tabs: the parent-tab bar

The look's strip shows a window's parent tabs above its workspace,
each as its active tab: under the standard look from two parent tabs,
under Enterprise always. The strips read a StripSource, which a pane
and the bar provide; the bar's entries never move, a click shows a
parent tab, its close button and menu close one after its tabs'
guards, and the classic "+" is New Tab, named so beside a pane's
New Tab in Pane. The Enterprise mock test reads the pane's strip
under the bar.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---
### Task 6: Documentation, STATUS and the whole build

**Files:**
- Modify: `README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/sdk-architecture.md`, `docs/STATUS.md` (top, and one long line wrapped), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

The user guide for tabs and their keys is `README.md`'s two shortcut paragraphs and `docs/configuration.md`'s Shortcuts
and motion sections (`docs/keyboard-shortcuts.md` describes the Keymap window and has no section on tabs). Each edit is
the smallest that makes its sentence true (AGENTS.md "Documentation edits"). The plugin documents (`jasper-sdk/README.md`,
`docs/plugin-authoring.md`) keep "the active group": parent tabs are not exposed to plugins (spec 10), and a window's
active group is its shown parent tab's. The single-tab strip entry's long line in `docs/STATUS.md` is wrapped
(Hand-offs absorbed).

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7a"); from edits import edit
edit("README.md", [
    ("Cmd+T new tab, Cmd+N new terminal window (a child window)", "Cmd+T new tab (a parent tab), Cmd+N new terminal window (a child window)", 1),
    ("Cmd+Option+arrows focus a group, Cmd+Shift+Enter maximize a group,", "Cmd+Option+arrows focus a pane, Cmd+Shift+Enter maximize a pane,", 1),
    ("Cmd+W close tab, Cmd+Option+W close group.",
     "Cmd+W close tab, Cmd+Option+W close pane. A window holds parent tabs, each its own split panes, and shows one at a "
     "time: the parent-tab bar above them names each by its active tab's title, from two parent tabs (under the Enterprise "
     "look always), showing another keeps every shell running, and a parent tab closes with its last tab.", 1),
    ("onto another group's tabs or middle to move it there, onto a group's outer edge to split that side,",
     "onto another pane's tabs or middle to move it there, onto a pane's outer edge to split that side,", 1),
    ("Every tab group shows its tab strip, except that a window holding a single tab shows none (under the Enterprise look the strip always shows);",
     "Every pane shows its tab strip, except that a pane holding a single tab shows none (under the Enterprise look the strip always shows);", 1),
    ("Cmd+1–9 selects an existing tab; Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab and wraps at the ends.",
     "Cmd+1–9 selects a parent tab; Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next parent tab and Ctrl+Shift+Tab / "
     "Ctrl+Tab to the previous/next tab in the active pane, both wrapping at the ends.", 1),
    ("On Linux/Windows, Ctrl+1–9 selects an existing tab and Ctrl+Shift+[ / Ctrl+Shift+] selects the previous/next tab.",
     "On Linux/Windows, Ctrl+1–9 selects a parent tab, Ctrl+PageUp / Ctrl+PageDown selects the previous/next parent tab "
     "and Ctrl+Shift+Tab / Ctrl+Tab the previous/next tab in the active pane.", 1)])
edit("docs/configuration.md", [
    ("the ones that showed before return; Maximize Group\nworks independently.",
     "the ones that showed before return; Maximize Pane\nworks independently.", 1),
    ("`close_tab`, `split_right`, `split_down`, `next_tab`, `previous_tab`,",
     "`close_tab`, `close_pane`, `close_parent_tab`, `split_right`, `split_down`, `next_parent_tab`, `previous_parent_tab`, "
     "`next_tab`, `previous_tab`,", 1),
    ('previous_tab = "cmd+{"\nnext_tab = "cmd+}"\n', 'previous_parent_tab = "cmd+{"\nnext_parent_tab = "cmd+}"\n', 1),
    ('previous_tab = "ctrl+{"\nnext_tab = "ctrl+}"\n', 'previous_parent_tab = "ctrl+pageup"\nnext_parent_tab = "ctrl+pagedown"\n', 1),
    (" A window holding a single tab shows no tab strip, and its second tab shows the\n"
     "strip at once, without motion; under the Enterprise look the strip always shows.\n",
     " A pane holding a single tab shows no tab strip, and its second tab shows the\n"
     "strip at once, without motion; under the Enterprise look the strip always shows. The parent-tab bar shows and hides\n"
     "at once too, and its tabs never move.\n", 1)])
edit("docs/app-architecture.md", [
    ("their workspaces of tab groups (`WorkspaceView`, `GroupView`, `SplitContainer`)",
     "their parent tabs (`ParentTabsView`, with the parent-tab bar `ParentTabBar`), each a workspace of tab groups "
     "(`WorkspaceView`, `GroupView`, `SplitContainer`)", 1),
    ("| `layout` | Pure workspace layout model: tab and group ids,",
     "| `layout` | Pure workspace layout model: a window's content (`WindowLayout`: `ParentTabs`, its parent tabs with the "
     "shown one and their order of use, or a `PaneLayout`), parent tab, tab and group ids,", 1),
    ("The main window has a header row above its workspace of tab groups, each with its own tab strip; a child window has "
     "only its title bar.",
     "The main window has a header row above its parent tabs: the parent-tab bar, then the shown parent tab's workspace of "
     "tab groups, each with its own tab strip (in the standard look, none while it holds a single tab); a child window has "
     "only its title bar above its parent tabs.", 1),
    ("`TabStrip` (one per tab group: `WindowTabs`, or the content-sized `ClassicTabs`)",
     "`TabStrip` (one per tab group and one for the parent-tab bar: `WindowTabs`, or the content-sized `ClassicTabs`)", 1)])
edit("docs/app-maintenance.md", [(
    """Workspace tabs use centered text and equal-width cells; a window holding a single tab hides
its strip (`WorkspaceChrome.showsStrip`, applied on every `WorkspaceView` render), which
shows and hides at once, without motion.
""", """Workspace tabs use centered text and equal-width cells; a pane holding a single tab hides
its strip (`WorkspaceChrome.showsStrip`, applied to each group on every `WorkspaceView` render), and a window holding a
single parent tab its parent-tab bar (`WorkspaceChrome.showsParentTabBar`, applied by `ParentTabBar.refresh`); both
show and hide at once, without motion. The bar is the look's strip over a `StripSource` (`ParentTabBar`, beside
`GroupView`), and its entries never move.
""", 1)])
edit("docs/sdk-architecture.md", [(
    "a `TabPlacement` (SDK 0.20) opens the tab in the active group, a new group\nbeside it or a new child window.",
    "a `TabPlacement` (SDK 0.20) opens the tab in the active group of the window's shown parent tab, a new group\n"
    "beside it or a new child window.", 1)])
edit("docs/STATUS.md", [(
    "`README.md`, `docs/configuration.md` and `docs/app-maintenance.md`. `:jasper-app:check` passes: 3806 app tests "
    "(four new), 0 failures, 19 skipped. GUI check for the user:\n",
    "`README.md`, `docs/configuration.md` and `docs/app-maintenance.md`. `:jasper-app:check` passes: 3806 app tests\n"
    "(four new), 0 failures, 19 skipped. GUI check for the user:\n", 1)])
PY
git diff --stat README.md docs
```

Expected: six files changed (`README.md` and five under `docs`).

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
**Workspace tabs, plan 7a: parent tabs — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-19-workspace-7a-parent-tabs.md),
[spec](superpowers/specs/2026-10-19-jasper-parent-tabs-design.md)). A window holds parent tabs, each an unnamed set of
panes (the split grid of plans 3 to 5), and shows one at a time. The model joins `layout` (`ParentTabId`, `ParentTab`,
`ParentTabs` with its order of use, the sealed `WindowLayout` over `ParentTabs` and `PaneLayout`); `workspace.
ParentTabsView` takes the terminal column's place in every window with a `WorkspaceView` per parent tab, the hidden ones
kept in the tree, invisible, so a switch restarts nothing; `WindowContent.workspace()` is the shown parent tab's, its
tabs are every parent tab's, and `windowLayout()` gives the model. New Tab (⌘T / Ctrl+Shift+T) opens a parent tab at the
end of the bar, or fills the main window's empty workspace; `newTab`, plugins' `ACTIVE_GROUP` tabs, the registry's
placements and single-instance requests open in the shown parent tab's active pane; New Terminal Window and New Editor
Window open full windows. A parent tab closes with its last tab and the one used before it shows. The parent-tab bar
(`ParentTabBar`, the look's strip over a `StripSource`) sits above the workspace, shows each parent tab as its active tab,
never moves, and shows from two parent tabs under the equal tabs and always under the classic tabs. A pane shows its
strip by its own count (superseding `128a0ac89`'s window count). Commands: `new_tab_in_pane`, `close_pane`,
`close_parent_tab`, `maximize_pane`, `focus_pane_*`, `next_parent_tab`, `previous_parent_tab`, `select_parent_tab_1`-`9`
(the `*_group` ids removed); `next_tab` and `previous_tab` move within the active pane on Ctrl+Tab. Rulings (plan,
"Rulings"): <one clause each, 1 to 21>. Spec issues: <Spec issues 1 and the user's answer, or "open">. Deviations:
<none, or each with its reason>. Left for plan 7b: `PaneLayout`'s origin and the pane windows that hold it, and Pop Out
Parent Tab and Move to Main Window in `WindowContent.parentTabMenu`; for plan 7c: a drag from `ParentTabBar.pressed`
(the bar's entries start none), with moves between parent tabs of one window still refused by the registry; for plans
6a and 6b: `WindowContent.windowLayout()` is what a window saves, and `workspaceChanged(WorkspaceView, boolean)` runs on
every layout and parent-tab change. `./gradlew check --continue`: **<counts>**. GUI checks for the user: in the standard
look one parent tab shows no bar, ⌘T (Ctrl+Shift+T) and the toolbar's New tab show it at once above the panes, each
opening a parent tab, and each entry is its parent tab's active tab's title; clicking an entry, ⌘1-9 / Ctrl+1-9 and
⌘⇧[ ⌘⇧] / Ctrl+PageUp Ctrl+PageDown switch parent tabs with every shell still running (a `top` in a hidden parent tab is
current when it shows) and the focus on the shown parent tab's tab, also with the focus in the Editor or a tool window's
table; ⌃⇥ ⌃⇧⇥ (Ctrl+Tab, Ctrl+Shift+Tab) move within the active pane; a parent tab's last tab closing (Cmd+W, its shell
exiting, the bar's ×, Close Parent Tab) shows the one used before it, and closing to one parent tab hides the bar; a bar
entry's × and middle-click close every tab of that parent tab, without a prompt; under the Enterprise look the bar
always shows above the pane strips, its "+" (New Tab) and the toolbar's New Session open a parent tab (a bar entry), a
pane strip's "+" (New Tab in Pane) stacks in its pane, and the main window's empty workspace shows a bar with "+" alone;
a pane holding one tab shows no strip in the standard look, in a split too; the File menu's Close Pane and Close Parent
Tab, the Tab menu's new order and the Keymap's Panes category; Remote's Alt+1/2/3 still toggle its tool windows on
Windows and Linux; a `jasper` request and a plugin's tab open in the shown parent tab; New Terminal Window and New Editor
Window open windows that take parent tabs; a tab dragged within the shown parent tab and into another window lands as
before. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for parent tabs

The README and the configuration guide describe parent tabs, the
parent-tab bar, pane strips and the new keys; the architecture and
maintenance notes name the parent tabs' view, model and bar; STATUS
records plan 7a with its rulings, the hand-offs to plans 7b, 7c and 6a
and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage. Section 2: three levels (Tasks 1, 3), one kind of split inside the shown parent tab (Task 3, Rulings
  7), the standard look's one-item pane without a strip (Task 2), Cmd+T a parent tab (Task 4), Enterprise's bar and pane
  strips always (Tasks 2, 5), New Terminal Window and New Editor Window full windows (Task 3: every child window takes
  parent tabs). Section 3: labels say Pane and Parent Tab, code keeps `Group` and `Tab` (Task 4, Rulings 12). Section 4:
  the two content shapes (Task 1), the invariants (Task 1's `ParentTabs`, Task 3's join-and-leave, Rulings 6), focus
  and the bar's label (Tasks 3, 5, Rulings 10, 16); the encoding is plan 6a's. Section 7: the bar's place and visibility
  in both looks (Task 5, Rulings 17), the pane strips (Task 2, Rulings 11), switching keeping components and sessions
  (Task 3, Rulings 4), the empty main workspace (Tasks 3, 5). Section 8: every row but the pop-out ones (7b), with each
  platform's collisions re-checked (Task 4, Spec issues 1, Rulings 13-15); removed ids are unknown keys (Task 4). Section
  10: `ACTIVE_GROUP` lands in the shown parent tab's active pane, `NEW_WINDOW` opens a full window, no parent tabs in
  the SDK, no `TAB_MOVED` between parent tabs (Task 3, Rulings 7, 9). Section 11: single-instance requests (Task 3);
  section 7's two strip rules supersede the one-tab rule (Task 2). Section 14: model tests (Task 1), bar and strip
  visibility in both looks (Tasks 2, 5), switching keeping sessions (Task 3), every command and key with a collision
  check (Task 4), events (Task 3); the GUI checks go to the user (Task 6's STATUS entry). Out of 7a: pop-out, bring
  back, the ⋮ menu and pane windows (7b), parent-tab drags (7c), saving (6a).
- Every quoted anchor was matched, at its stated count, in a replay of Tasks 1-6 in order on a `git archive` copy of
  `bba5c472e`; nothing was compiled or run.
- Type consistency: `ParentTabId.random()`, `ParentTab(id, layout)`, `ParentTabs.of`, `open`, `select`, `replace`,
  `remove`, `step`, `active()`, `parentTabs()`, `WindowLayout`, `PaneLayout(Layout)`; `ParentTabsView(WindowContent)`
  with `model()`, `bar()`, `active()`, `views()`, `count()`, `view(ParentTabId)`, `holding(Tab)`, `holding(GroupId)`,
  `openParentTab`, `select`, `remove`, `changed(WorkspaceView)`, `refresh()`; `WorkspaceView(WindowContent,
  ParentTabId)`, `id()`, `holdsFocus(WindowContent, Component)`; `WindowContent.workspaceChanged(WorkspaceView,
  boolean)`, `windowLayout()`, `parentTabs()`, `newParentTab(Path)`, `selectParentTab(ParentTabId)`,
  `selectParentTabAt(int)`, `selectParentTabRelative(int)`, `closeParentTab(ParentTabId)`, `parentTabOf(Tab)`,
  `activeParentTab()`, `parentTabCount()`, `parentTabBar()`, `parentTabShortcut(int)`, `parentTabMenu(Tab)`;
  `StripSource` (`newTabName()` among its methods), `ParentTabBar(WindowContent, ParentTabsView)`, `strip()`,
  `refresh()`, `close()`;
  `WorkspaceChrome.tabs(StripSource)`, `showsStrip(int)`, `showsParentTabBar(int)`; `ActionId.NEW_TAB_IN_PANE`,
  `CLOSE_PANE`, `CLOSE_PARENT_TAB`, `MAXIMIZE_PANE`, `FOCUS_PANE_*`, `NEXT_PARENT_TAB`, `PREVIOUS_PARENT_TAB`,
  `SELECT_PARENT_TAB_1`-`9` keep one name and one signature from the task that creates them to the last task that uses
  them.
- Tests that open tabs in a laid-out equal strip turn its motion off first (`ParentTabBarTest`, plan 5's drag tests);
  the bar never animates, so its tests read it settled.
- Hand-offs: every minor `128a0ac89`'s review handed to 7a is listed in the header, handled in Task 2 or 6, re-handed to
  `7c` or marked moot with its reason.
