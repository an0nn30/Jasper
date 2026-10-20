# Workspace tabs 7c: dragging parent tabs Implementation Plan

**Status: not executed.** Written on 2026-10-20 against `claude/workspace-tabs` at `8e129ff34` with plans 7a's and 7b's
text applied (see Workspace).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent tab drags as a tab does. A press on its entry in the parent-tab bar that moves past the kit's
threshold carries the whole parent tab:
- along its bar, to another place;
- onto another main or full window's bar, or the top of a window that shows none, to move there whole;
- out of every window, into a new full window at the cursor.

A tab released outside every window pops out into a pane window whose origin is where it was, in place of plan 5's
full child window. Nothing moves until the release, the bar's entries take their new places at once, and every move
between windows keeps each tab's session and reports one `TAB_MOVED` per tab.

**Architecture:** Plan 5's `TabDrag` takes a second kind of press, `pressed(ParentTabBar, Tab, MouseEvent)`, and one
more target, `Target.Bar(window, index)`. The pure `WorkspaceDropResolver` gains `place`, a parent tab's place in a bar
by the strip rule, and makes its strip `marker` public for the bar's landing; `ParentTabs` gains `move`. A drop along
the drag's own bar reorders it (`ParentTabsView.reorder`). A drop on another window, and a release outside every
window, go to the window manager of the drag's window (`WindowContent.manager()`): plan 7b's `moveParentTab`,
`popOutParentTab(…, bounds, cursor)` and `popOutTab(…, bounds, cursor)`, which replaces plan 5's
`moveToNewWindow(Tab, Rectangle, Point)`. Headless tests drive the drags over plan 5's stand-in frames on a pretend
screen; real drags are GUI checks for the user.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md)
(sections 4, 5's dragging rows, 7, 10's events, 12's third plan, 13 and 14), which amends and wins over
[`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(section 12, tear-out and drag-back, and 15's modal-dialog row). It builds on
[plan 5](2026-10-18-workspace-5-tear-out.md) (the drag), [plan 7a](2026-10-19-workspace-7a-parent-tabs.md) (parent tabs
and their bar) and [plan 7b](2026-10-19-workspace-7b-pop-out.md) (pane windows and the moves). Plans 6a and 6b
(`2026-10-18-workspace-6a-save-restore.md`, `2026-10-18-workspace-6b-plugin-restorers.md`) are re-planned after it.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 7b's commits. The plan was written at `8e129ff34` ("Docs: workspace tabs plan 7a,
parent tabs"), with plan 7a's and then plan 7b's text applied, plan 7b as committed in `8dcdf4f0a`. Every anchor below
was matched at its stated count, and every edit script of Tasks 1-4 replayed in order after plan 7a's and plan 7b's
file creations and edit scripts, on a `git archive` copy of `8e129ff34`. They replayed too on `899c0bcfe`, where plan
7a's Tasks 1-3 are executed, after the rest of plan 7a and plan 7b. Line numbers are the first replay's (`8e129ff34`
with 7a and 7b applied), or "7a's" and "7b's" where the text is theirs. Nothing was compiled or run.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan, plan 7a or plan 7b wrote, and quote it as they leave it.

**Hand-offs absorbed:**
- Plan 7a's "Hand-offs absorbed" re-hands two of `128a0ac89`'s review minors to this plan:
  - no test of the source window's strip hiding after a cross-window move: Task 3 asserts both windows' pane strips in
    `TabDragWindowsTest.aTabDroppedOnAnotherWindowsStripMovesThereWithItsSession`, and the source window's bar hiding in
    `aParentTabDroppedOnAnotherWindowsBarMovesThereWhole`;
  - no test of a torn-out window starting without a strip: Task 3's
    `TabDragWindowsTest.aTabReleasedOutsideEveryWindowPopsOutIntoAPaneWindowUnderTheCursor` asserts the pane window shows
    neither a pane strip nor a parent-tab bar.
- Plan 7a's STATUS hand-off "for plan 7c: a drag from `ParentTabBar.pressed` (the bar's entries start none), with moves
  between parent tabs of one window still refused by the registry": Task 2 starts the drag there. The registry's refusal
  of a move within one window does not matter, as a reorder moves no tab; between windows the tabs move through the
  registry (Task 3).
- Plan 7b's "Left for plan 7c" (its Task 6 STATUS entry) and its Rulings 19: a drag onto another window's bar is
  `WindowManager.moveParentTab`, a parent tab dragged outside every window is `popOutParentTab(window, id, bounds,
  cursor)`, and a tab torn out is `popOutTab(tab, bounds, cursor)`, in place of `moveToNewWindow(Tab, Rectangle, Point)`,
  which goes (Task 3; Rulings 4, 6, 10).
- Plan 5's final-review hand-off to plan 6 stands unchanged: `Opening.bounds` and `WindowShell.place` are a tear-out's
  workspace bounds, for a pane window and a full window alike.

## Spec issues

None. Spec 5 drops a parent tab "on another window's parent-tab bar", while spec 7 hides the bar in the standard look
whenever a window has one parent tab, the usual case; such a window takes the drop over the band where its bar shows
(Rulings 5). Spec 13 puts dropping a parent tab onto a pane out of scope, so panes, pane strips and pane windows take
none (Rulings 9).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break: `WindowManager.moveToNewWindow(Tab, Rectangle, Point)` and `TabDrag`'s tear-out callback go outright with
  their uses; no shim and no "was removed" text (MVP).
- No motion (AGENTS.md; parent tabs spec 5): nothing moves during a drag; on the release the bar's entries take their
  new places at once (its `ParentTabBar.animates()` is false, plan 7a), and windows open in place.
- Sessions and content are never touched: within a window a drop changes the bar's order only; every move between
  windows goes through plan 7b's `WindowManager` operations, which move tab by tab through the tab registry (spec 2.3,
  plan 7b Rulings 6).
- The SDK does not change: no file under `jasper-sdk`, `jasper-sdk-testkit` or `plugins` changes; SDK 0.20.0 stays.
  `ContentTab.moveToNewWindow()` keeps plan 7b's meaning (Pop Out Tab).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): none changes. `dock` keeps its
  one edge, to `layout`, and names no parent-tab type; `layout` stays JDK-only and names no other Jasper package.
- The theme-migration ledger: this plan creates no Java file, so `docs/design/theme-engine/migration.csv` gains no row.
- `TabDrag.fitted` and `TabDrag.displayFor`, the keep-on-screen helpers, stay where they are and as they are: plan 6a
  moves them beside `WindowPlacement` (plan 5's hand-off to plan 6). No edit of this plan touches them.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): the drag runs on synthetic events over plan 5's stand-in frames on a
  pretend screen (`TabDragTestSupport`, `TabDragWindowsTest`), the label window through `TabDragLabel`'s state.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan7c/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan7c
cp build/plan7b/edits.py build/plan7b/ledger.py build/plan7c/ 2>/dev/null || cp build/plan7a/edits.py build/plan7a/ledger.py build/plan7c/
ls build/plan7c/edits.py build/plan7c/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan7c")`. Java text inside a script is in triple-quoted strings and holds no
backslash.

## Review Focus

1. **A parent tab holding a tab with a modal prompt open (pinned), or one being closed.** It moves along its own bar
   only: no other window shows a landing, a release outside every window opens nothing, and no `TAB_MOVED` is reported.
   Task 3: `TabDragWindowsTest.aParentTabHoldingAPinnedTabMovesAlongItsOwnBarOnly`. The closing case takes the pinned
   case's path, `TabRegistry.movable` through `TabDrag.movable()`, so that test covers both.
2. **The standard look's usual window, which shows no bar (one parent tab, the main window's empty workspace too).** A
   parent tab dragged over the top of it, where its bar shows, joins the end of its bar and the bar then shows, or it
   takes the empty workspace's place; over its panes nothing lands. Task 3:
   `TabDragWindowsTest.aWindowShowingNoBarTakesAParentTabAlongItsTop`, `theEmptyMainWorkspaceTakesAParentTabAlongItsTop`.
3. **A full window's only parent tab (Enterprise shows its bar).** Released outside every window it stays, as its
   window already is its own; dropped on another window's bar it moves there, and its window closes after the move
   reports itself. Task 3: `TabDragWindowsTest.aFullWindowsOnlyParentTabStaysOutsideButMovesOntoAnotherWindowsBar`.
4. **A parent tab released where it would change nothing, or over something that takes no parent tab** (its own place
   in its bar, a pane, a pane's strip, a pane window). No landing shows and nothing changes. Tests:
   - Task 2: `TabDragTest.aParentTabDropsOnlyAlongItsBarAndNotWhereItIs`;
   - Task 3: `TabDragWindowsTest.aPaneWindowTakesNoParentTab`.
5. **The tab a dragged parent tab's entry shows closing, or giving way to another of its tabs, under the drag** (its
   shell exits; Ctrl+Tab, a request or a plugin opening a tab there). The drag ends clean (label, landing, Esc
   dispatcher and lost-release listener gone), and nothing lands. Task 2:
   `TabDragTest.aParentTabsDragEndsWhenTheTabItsEntryShowsCloses`, `aParentTabsDragEndsWhenItsEntryShowsAnotherTab`.

## Verified facts

From `8e129ff34` with plans 7a's and 7b's text applied (see Workspace).

1. **The drag takes tabs only.** `TabDrag.pressed(GroupView, Tab, MouseEvent)` (`workspace/TabDrag.java:137-149`) is its
   one press. Its state is the group, the tab, the pressed component, the press, the grab and the group's size
   (`:98-105`); `inPlace` (`:221-224`), `start` (`:240`) and `update` (`:244-265`) read `group.owner()`. `update` offers
   an `Into` drop in the window under the cursor and a `TearOut` outside every window, but not for a pinned tab or a
   child window's only tab. `drop` (`:335-354`) hands a `TearOut` to the constructor's
   `BiConsumer<Tab, Target.TearOut>` (`:85`, `:115-118`). `WindowManager`'s is
   `(tab, out) -> moveToNewWindow(tab, out.bounds(), out.cursor())` (`WindowManager.java:95-96`), and a window's own
   drag's is `(tab, out) -> { }` (`WindowContent.java:135`).
2. **The bar's presses.** 7a's `ParentTabBar.pressed` only cancels a dock drag, and its `dragged` and `released` do
   nothing (`ParentTabBar.java:72-74`). Both strips select an entry on a left press and then call `source.pressed`, and
   send drags and releases to the source (`WindowTabs.java:276-284`, `ClassicTabs.java:184-200`).
   `ParentTabBar.tabs()` lists each parent tab's active tab in bar order and skips the main window's empty workspace
   (`:54-58`). `TabStrip.tabBounds()` gives the entries' rectangles in the strip's coordinates, in that order.
3. **The bar never slides.** `WindowTabs.refresh` re-slots a reorder at once (`:69`). An entry that arrives takes its
   place at once, because `ParentTabBar.moved` is true and `animates()` is false (`:83`). `ClassicTabs` never move.
   Both strips' preferred height is the scaled tab height (`WindowTabs.java:117-118`, `ClassicTabs.java:103-104`).
4. **Parent tabs have no move.** 7a's `ParentTabs` has `open`, `select`, `replace`, `remove` and `step`
   (`layout/ParentTabs.java:180-242`); its constructor keeps the order of use it is given. `ParentTabsView.select`
   (`:153-158`) ends with `owner.workspaceChanged(active(), false)`, which refreshes the bar through `update` and
   `refreshTabs` (`WindowContent.java:1931-1946`, `:1608`).
5. **The bar's place.** 7a's `ParentTabsView.Stack` (`:259-…`) lays the bar out on top at its preferred height while it
   shows, at height 0 while it hides, and every workspace below it. The bar is the view's direct child. 7a as executed
   (`53414b814`) keeps the views in bar order ahead of the bar (`ParentTabsView.java:120-121`, pinned by
   `ParentTabsViewTest.aNewParentTabGoesAtTheEndOfTheBarAndTheViewsFollowTheBar`); 7a's text adds a new view at 0, so
   `TabDragTest`'s check that the views follow the bar holds on the executed code, and plan 7b's `openParentTab` with an
   id must keep that order (its pre-flight).
6. **7b's moves.**
   - `moveParentTab(from, id, to, index)` (`WindowManager.java:351-361`) refuses the same window, a closed window, a pane
     window on either side and a parent tab holding an unmovable tab. Otherwise `to.receive` lands the tabs at `index` of
     `to`'s bar, or in place of an empty main workspace (`ParentTabsView.openParentTab`, `:118-140`). A full window it
     empties closes after the moves, and a main window it empties gets an empty workspace under a new id
     (`ParentTabsView.renew`, `:187-200`).
   - `popOutParentTab(window, id, bounds, cursor)` (`:324-336`) needs `mayPopOutParentTab`: the main window, or a full
     window with another parent tab, and tabs that may all move (`WindowContent.java:1359-1363`). It opens the full
     window at those workspace bounds on the cursor's display.
   - `popOutTab(tab, bounds, cursor)` (`:255-259`) refuses a closed or pinned tab and a child window's only tab
     (`leaving`, `:453-458`). Its origin, `popOutOrigin(tab)`, is computed while the tab is still in its pane.
   - `moveToNewWindow(Tab, Rectangle, Point)` (`:434-447`) is the tear-out's alone (7b Rulings 19). Its only other
     caller is `WindowManagerTest:479`.
   - `TabRegistry.movable(Tab)` (`contributions/TabRegistry.java:360`).
7. **A tab's activity.** `Tab.onActive` (`contributions/Tab.java:139`) hears `Tab.active`, which `WindowContent.update`
   sets for every tab on every change (`:1937`): true for the window's active tab only.
8. **The window's manager.** 7b's `WindowContent.manager` field (`:80`) is set by `WindowManager.open` (`:575-577`)
   through its only accessor, the setter `manager(WindowManager)` (`:1556`).
9. **Where a torn-out window goes.** `WindowFrame.placeWorkspace` (`:142-165`) puts `content.workspace()` at the bounds
   it is given, grown by the packed frame's chrome, on the display under the cursor.
10. **The test fixtures.**
   - `TabDragTestSupport` (`:26-139`) finds a pane strip's entries only (`entry`, `onTab`).
   - `TabDragWindowsTest` (`:43-138`) sets the pretend screen: the main window at 0,0 (1208 x 800) and a full child
     window at 1400,100 (800 x 600), two tabs each in one parent tab. `placed` and `cursors` record what a new window was
     given; `classicChild()` swaps in a child window under the classic tabs.
   - `TabDragTest` (`:43-84`) has one window at 100,50 holding tabs `a`, `b` and `c` in one pane.
   - `DockDropOverlay.highlight()` and `placeholder()` (`:77-79`) give the landing.

## Rulings

Record them in `docs/STATUS.md` (Task 4). "What — why".

1. **A parent tab drags with the one tab drag.** A press on its bar entry (`ParentTabBar.pressed`) hands `TabDrag` the
   parent tab. The label shows the entry's title and icon, and plan 5's threshold, Esc, lost-release, focus and close
   rules hold (plan 5 Rulings 3 and 7). The drag ends when the tab the entry shows closes, leaves the parent tab or
   gives way to another of its tabs (Ctrl+Tab, a request or a plugin's tab opening there: `Tab.onActive`), as an item's
   drag ends when its tab leaves its pane. Reason: spec 5's "dragging follows plan 5", and workspace tabs spec 12's one
   application-wide drag; the drag holds the entry, which is that tab.
2. **Along its own bar,** a release puts the parent tab before the first of the bar's other entries whose middle is not
   left of the cursor (the strip rule, `WorkspaceDropResolver.place`). The landing is a bar `TAB_INDICATOR` wide at that
   place, as for a strip (plan 5 Rulings 4). Its own place shows no landing and does nothing (plan 5 Rulings 5). Reason:
   the bar is a strip, and the same gesture keeps the same rules.
3. **Nothing moves until the release; then the bar's entries take their new places at once.** A reorder is a change of
   the window's parent tabs, not a move: it reports no `TAB_MOVED` and reaches `workspaceChanged`, the hook plan 6a's
   save takes. Reason: no animations (AGENTS.md, the brief); spec 10 reports `TAB_MOVED` only between windows.
4. **Onto another main or full window's bar,** at the place the strip rule gives, the parent tab moves whole with its
   id and layout through `WindowManager.moveParentTab`. That is one `TAB_MOVED` per tab; a full window it empties
   closes after the moves, and a main window it empties keeps its empty workspace under a new id. Reason: spec 5; plan
   7b Rulings 6 and 10.
5. **A window whose bar hides takes a parent tab over the band its bar takes when it shows,** at the end of its bar.
   This is the standard look's window with one parent tab, the main window's empty workspace among them. The band is
   the top of its parent tabs, the bar's preferred height. The landing highlights the band; the bar shows once the
   window has two parent tabs, and the main window's empty workspace gives way to the parent tab. Reason: otherwise a
   parent tab could never join the standard look's usual window (Spec issues).
6. **Outside every window, a parent tab becomes a new full window** through `popOutParentTab(window, id, bounds,
   cursor)`. Its workspace is the size of the parent tab's workspace, its corner where the entry's corner was under the
   cursor, and it opens on the display under the cursor. Reason: spec 5; plan 5 Rulings 12 and its final review.
7. **A full window's only parent tab has no tear-out target, yet another window's bar takes it.** The main window's
   only parent tab may tear out, under Enterprise whose bar shows it, leaving the empty workspace. Reason: plan 7b
   Rulings 8 (a child window's only parent tab stays, its window already is its own; the main window's may all go),
   applied as plan 5 applied it to a child window's only tab.
8. **A parent tab holding a tab that is pinned or being closed takes targets along its own bar only:** no other window
   and no tear-out (`TabRegistry.movable`). Reason: plan 7b Rulings 7; plan 5 Rulings 17.
9. **A parent tab drops on bars alone.** A workspace, a pane's strip, a pane window, a window's chrome and Jasper's
   other windows take none, and a release there does nothing. Reason: spec 13 (dropping on a pane is out of scope), and
   a pane window has no parent tabs (spec 4).
10. **A tab released outside every window pops out into a pane window** through `WindowManager.popOutTab(tab, bounds,
    cursor)`. Its origin is where the tab was (`popOutOrigin`, plan 7b Rulings 3 and 4), and its place and size are
    plan 5's tear-out's: the old pane's size, the tab's corner under the cursor, the cursor's display. A child window's
    only tab and a pinned tab still have none. `moveToNewWindow(Tab, Rectangle, Point)` goes, and plan 7b Rulings 19
    ends. Reason: spec 5; the clean break.
11. **A tab dropped on a parent-tab bar drops nothing,** as over any part of a window outside its workspace (plan 5
    Rulings 9). Reason: spec 5 lists a tab's drops (strips, a pane's centre and edges, outside every window), and making
    a parent tab from a tab is not among them.
12. **The drag reaches the window manager through the drag's window** (`WindowContent.manager()`), in place of plan 5's
    tear-out callback. A window no manager opened moves nothing out of itself (plan 5 Rulings 10). Reason: every move
    out of a window is the manager's (plan 7b), and the manager already reaches every window it opens.

## File structure

- `dev.jasper.app.dock`: `WorkspaceDropResolver` (`place`, the public `marker`) and `package-info` (Task 1).
- `dev.jasper.app.layout`: `ParentTabs` (`move`) (Task 1).
- `dev.jasper.app.workspace`:
  - Task 2: `TabDrag` (the parent tab's press, targets along its bar, landing and drop), `ParentTabBar` (its press,
    drag and release), `ParentTabsView` (`reorder`), `WindowContent` (`reorderParentTab`), `TabStrip` (Javadoc);
  - Task 3: `TabDrag` (other windows' bars, the band, tear-outs, the constructor), `WindowContent` (`manager()`, its own
    drag), `WindowManager` (the drag's construction, `moveToNewWindow(Tab, Rectangle, Point)` gone).
- Tests:
  - Task 1: `WorkspaceDropResolverTest`, `ParentTabsTest` (7a's);
  - Task 2: `TabDragTestSupport`, `TabDragTest`;
  - Task 3: `TabDragWindowsTest`, `WindowManagerTest`.
- Documents (Task 4): `README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/STATUS.md`, this plan's status banner.

## Before you start

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
git log --oneline -1 --grep "documentation and status for pane windows"                                      # plan 7b's Task 6 commit
grep -c '@Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }' jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabBar.java  # 1
grep -c 'public boolean moveParentTab(WindowContent from, ParentTabId parentTab, WindowContent to, int index) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'public Optional<ChildWindow> popOutTab(Tab tab, Rectangle bounds, Point cursor) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds, Point cursor) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'void manager(WindowManager value) { manager = Objects.requireNonNull(value, "value"); }' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'private TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this), (tab, out) -> { });' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above them. Create the two helper scripts (Global Constraints).

---

### Task 1: A parent tab's place in a bar, and its move along it

Spec 5: a parent tab dragged along its bar takes another place there, by the strip rule (Rulings 2). Pure parts beside
plan 5's resolver and 7a's model; nothing uses them yet.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java` (class Javadoc, `resolve`'s strip
  rule `:120-124`, `landing` `:168`, `marker` `:179-190`), `dock/package-info.java`,
  `jasper-app/src/main/java/dev/jasper/app/layout/ParentTabs.java` (before `step`, 7a's `:233`)
- Test: `jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/layout/ParentTabsTest.java` (7a's)

**Interfaces:**
- Consumes: plan 5's `WorkspaceDropResolver.resolve`, `landing`; 7a's `ParentTabs` (its private constructor with the
  order of use, `requireParentTab`).
- Produces:
  - `public static OptionalInt WorkspaceDropResolver.place(Point cursor, Rectangle bar, List<Rectangle> entries)`: 0 to
    `entries.size()`, empty off the bar;
  - `public static Rectangle WorkspaceDropResolver.marker(Rectangle strip, List<Rectangle> entries, int index, int
    width)`, the strip drop's landing, now public;
  - `public ParentTabs ParentTabs.move(ParentTabId id, int index)`: `index` among the others, 0 to their number; the
    shown parent tab and the order of use stay.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java", [(
    """    @Test void whereWindowsOverlapTheFirstListedTakesTheDrop() {
""", """    /** Parent tabs spec 5 and workspace plan 7c Rulings 2: a parent tab's place in a bar follows the strip rule. */
    @Test void aParentTabGoesBeforeTheFirstOtherEntryOfABarWhoseMiddleIsNotLeftOfTheCursor() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThat(WorkspaceDropResolver.place(new Point(10, 10), bar, entries)).hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(100, 10), bar, entries)).as("on a middle: still before that entry")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(101, 10), bar, entries)).hasValue(1);
        assertThat(WorkspaceDropResolver.place(new Point(599, 27), bar, entries)).as("the bar's last pixel").hasValue(2);
        assertThat(WorkspaceDropResolver.place(new Point(300, 5), bar, List.of())).as("a bar with no other entry").hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(300, 28), bar, entries)).as("just under the bar").isEmpty();
        assertThat(WorkspaceDropResolver.place(new Point(-1, 10), bar, entries)).isEmpty();
        assertThat(WorkspaceDropResolver.marker(bar, entries, 1, 2)).as("between the two entries")
            .isEqualTo(new Rectangle(299, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, entries, 2, 2)).as("after the last, kept inside the bar")
            .isEqualTo(new Rectangle(598, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, List.of(), 0, 2)).as("no other entry: the bar's start")
            .isEqualTo(new Rectangle(0, 0, 2, 28));
    }

    @Test void whereWindowsOverlapTheFirstListedTakesTheDrop() {
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/layout/ParentTabsTest.java", [(
    """    @Test void replaceGivesAParentTabItsNewLayoutAndLookupsFollowIt() {
""", """    /** Parent tabs spec 5 and workspace plan 7c Rulings 2: a parent tab dragged along its bar takes another place there. */
    @Test void moveTakesAParentTabToAnotherPlaceAndKeepsTheShownOne() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), b.id()).select(c.id());
        ParentTabs moved = tabs.move(a.id(), 2);
        assertThat(moved.parentTabs()).containsExactly(b, c, a);
        assertThat(moved.active()).as("the shown one stays").isEqualTo(c.id());
        assertThat(moved.recent()).as("and so does the order of use").isEqualTo(tabs.recent());
        assertThat(moved.move(a.id(), 0).parentTabs()).containsExactly(a, b, c);
        assertThat(tabs.move(b.id(), 1)).as("its own place").isEqualTo(tabs);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(a.id(), 3)).withMessageContaining("Index 3");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(a.id(), -1)).withMessageContaining("Index -1");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(ParentTabId.random(), 0))
            .withMessageContaining("not in the window");
    }

    @Test void replaceGivesAParentTabItsNewLayoutAndLookupsFollowIt() {
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.WorkspaceDropResolverTest' --tests 'dev.jasper.app.layout.ParentTabsTest'`
Expected: FAIL to compile: `place` and `move` are not found, and `marker` has private access.

- [ ] **Step 3: The resolver finds a place in a bar, and parent tabs move**

`place` and the strip drop share one rule (`index`), and `landing` uses the public `marker`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit, span
D = "jasper-app/src/main/java/dev/jasper/app/dock/"
R = D + "WorkspaceDropResolver.java"
edit(R, [
    (""" * its tabs, into a group over the middle of its card area, or into a new group on the side whose outer quarter of the card
 * area the cursor is over; the rectangle that shows the landing; and which of several windows takes the drop. The tab
 * drag's twin of {@link DockDropResolver}. Pure; every rectangle and the cursor are in the caller's one coordinate space.
""", """ * its tabs, into a group over the middle of its card area, or into a new group on the side whose outer quarter of the card
 * area the cursor is over; where a dragged parent tab would go in a parent-tab bar (parent tabs spec 5); the rectangle
 * that shows the landing; and which of several windows takes the drop. The tab drag's twin of {@link DockDropResolver}.
 * Pure; every rectangle and the cursor are in the caller's one coordinate space.
""", 1),
    ("""            if (area.strip.contains(cursor)) {
                int index = 0;
                for (Rectangle tab : area.tabs) if (tab.x + tab.width / 2.0 < cursor.x) index++;
                return Optional.of(new Drop.Strip(area.group, index));
            }
""", """            if (area.strip.contains(cursor)) return Optional.of(new Drop.Strip(area.group, index(cursor, area.tabs)));
""", 1),
    ("""    /** The side whose outer quarter of {@code content} holds {@code cursor}, the nearer when two do; empty in the middle. */
""", """    /**
     * Where a dragged parent tab would go in a window's parent-tab bar (parent tabs spec 5), by the strip rule of
     * {@link #resolve}: before the first of the bar's other entries whose middle is not left of the cursor.
     *
     * @param cursor the cursor
     * @param bar the bar
     * @param entries the bar's entries in bar order, without the dragged parent tab's own
     * @return the place, 0 to the number of entries; empty when the cursor is not over the bar
     */
    public static OptionalInt place(Point cursor, Rectangle bar, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        return bar.contains(cursor) ? OptionalInt.of(index(cursor, entries)) : OptionalInt.empty();
    }

    /** How many of {@code entries}, in order along a strip, have their middle left of {@code cursor}. */
    private static int index(Point cursor, List<Rectangle> entries) {
        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return index;
    }

    /** The side whose outer quarter of {@code content} holds {@code cursor}, the nearer when two do; empty in the middle. */
""", 1),
    ("            case Drop.Strip strip -> marker(area, strip.index(), bar);\n",
     "            case Drop.Strip strip -> marker(area.strip, area.tabs, strip.index(), bar);\n", 1)])
span(R, "    /** A bar {@code width} wide, centred on the place before the tab at {@code index} (after the last at the end), in the strip. */\n",
     "        return new Rectangle(left, strip.y, width, strip.height);\n    }\n",
     """    /**
     * A drop's marker in a strip or a parent-tab bar: a bar {@code width} wide and as tall as {@code strip}, centred on
     * the place before the entry at {@code index} (after the last at the end) and kept inside {@code strip}.
     *
     * @param strip the strip or bar
     * @param entries its entries in order, without the dragged one's own
     * @param index the place, 0 to the number of entries
     * @param width the marker's width
     * @return the marker, in the strip's coordinates
     */
    public static Rectangle marker(Rectangle strip, List<Rectangle> entries, int index, int width) {
        int x;
        if (entries.isEmpty()) x = strip.x;
        else if (index == 0) x = entries.getFirst().x;
        else if (index >= entries.size()) x = entries.getLast().x + entries.getLast().width;
        else x = (entries.get(index - 1).x + entries.get(index - 1).width + entries.get(index).x) / 2;
        int left = Math.max(strip.x, Math.min(x - width / 2, strip.x + strip.width - width));
        return new Rectangle(left, strip.y, width, strip.height);
    }
""")
edit(D + "package-info.java", [(
    """ * that finds where a dragged tab would drop on a workspace. JDK-only: no Swing or AWT components, and from
 * {@code java.awt} only the {@code Rectangle} and {@code Point} values; {@code verifyApplicationArchitecture} enforces
 * it. Each window owns its state and confines it to the EDT.
""", """ * that finds where a dragged tab would drop on a workspace, and a dragged parent tab in a parent-tab bar. JDK-only: no
 * Swing or AWT components, and from {@code java.awt} only the {@code Rectangle} and {@code Point} values;
 * {@code verifyApplicationArchitecture} enforces it. Each window owns its state and confines it to the EDT.
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/layout/ParentTabs.java", [(
    """    /**
     * The parent tab {@code delta} places along the bar from the one that shows, wrapping around (Next and Previous
""", """    /**
     * Moves a parent tab to another place in the bar, as a drag along the bar drops it (parent tabs spec 5). The one
     * that shows and the order of use stay.
     *
     * @param id a parent tab of the window
     * @param index its place among the others, 0 to their number
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it or the index is outside the bar
     */
    public ParentTabs move(ParentTabId id, int index) {
        int at = requireParentTab(id);
        if (index < 0 || index >= parentTabs.size())
            throw new IllegalArgumentException("Index " + index + " is outside 0 to " + (parentTabs.size() - 1));
        var next = new ArrayList<>(parentTabs);
        next.add(index, next.remove(at));
        return new ParentTabs(next, active, recent);
    }

    /**
     * The parent tab {@code delta} places along the bar from the one that shows, wrapping around (Next and Previous
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*' --tests 'dev.jasper.app.layout.*' --continue`
Expected: PASS (`WorkspaceDropResolverTest` +1, `ParentTabsTest` +1; every other dock and layout test unchanged). Then
`./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Parent tab drag: a parent tab's place in a bar, and its move along it

The workspace drop resolver finds where a dragged parent tab would go
in a parent-tab bar by the strip rule, and its strip marker is public
for the bar's landing. Parent tabs move to another place in the bar,
keeping the one that shows and the order of use.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: A parent tab drags along its bar

Spec 5: a parent tab drags within its bar to reorder. The bar's press starts the tab drag, which carries the parent tab
(Rulings 1); along its own bar a release puts it at the strip rule's place (Rulings 2), and its entries jump there
(Rulings 3). It drops on nothing else yet: other windows and tear-outs follow in Task 3, and panes never take one
(Rulings 9).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (imports, class Javadoc, `Target`, the
  press state `:98-105`, `dragged` `:123-124`, `pressed` `:137-149`, `cancelFrom` `:202`, `inPlace` `:220-224`, `start`
  `:240`, `update` `:252-263`, after `into` `:274`, `showLanding` `:276-298`, `finish` `:303-309`, `released`
  `:186-194`, `drop` `:329-337`, `tearOutBounds` `:356-363`), `ParentTabBar.java` (7a's class Javadoc and `:70-74`),
  `ParentTabsView.java` (before 7a's `select`), `WindowContent.java` (after 7a's `parentTabCount`), `TabStrip.java` (7a's
  class Javadoc)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java` (`:110-123`, after `entry`
  `:99`), `TabDragTest.java`

**Interfaces:**
- Consumes: Task 1's `WorkspaceDropResolver.place`, `marker`, `ParentTabs.move`; 7a's `ParentTabBar` (`owner`, `tabs`,
  `strip`), `ParentTabsView` (`view`, `model`, `bar`, `active`), `WindowContent.parentTabs()`, `parentTabBar()`,
  `parentTabOf(Tab)`, `tabTitle`, `tabIcon`.
- Produces:
  - in `TabDrag`: `record Target.Bar(WindowContent window, int index) implements Target`; `void pressed(ParentTabBar,
    Tab, MouseEvent)`; `ParentTabId draggedParentTab()`; the private `onBar(WindowContent, Point)`, `barArea(WindowContent)`
    and `barEntries(WindowContent)`; `drop(Tab, WindowContent, ParentTabId, Target)`;
  - `void ParentTabsView.reorder(ParentTabId id, int index)` and `void WindowContent.reorderParentTab(ParentTabId id, int
    index)`;
  - test support: `TabDragTestSupport.onParentTab(WindowContent, Tab, double)`, `barEntry(WindowContent, Tab)`,
    `startParentTab(WindowContent, Tab, Point...)` and `dropParentTab(WindowContent, Tab, Point...)`, each for the bar
    entry that shows `tab`.

- [ ] **Step 1: Write the failing tests**

The drag's test support finds the bar's entries as it finds a strip's:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java", [
    ("""        return SwingUtilities.getDeepestComponentAt(strip, (int) bounds.getCenterX(), (int) bounds.getCenterY());
    }
""", """        return SwingUtilities.getDeepestComponentAt(strip, (int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /** A point in the bar's entry that shows {@code tab}, {@code across} of its width, on the pretend screen. */
    static Point onParentTab(WindowContent window, Tab tab, double across) {
        TabStrip bar = window.parentTabBar();
        Rectangle entry = bar.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return inside(onScreen(bar.component(), entry), across, 0.5);
    }

    /** The component a press on the parent-tab bar's entry that shows {@code tab} reaches. */
    static Component barEntry(WindowContent window, Tab tab) {
        TabStrip bar = window.parentTabBar();
        Rectangle bounds = bar.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return SwingUtilities.getDeepestComponentAt(bar.component(), (int) bounds.getCenterX(), (int) bounds.getCenterY());
    }
""", 1),
    ("""    static Component start(WindowContent window, Tab tab, Point... path) {
        Component source = entry(window, tab);
        Point press = middle(source);
""", """    static Component start(WindowContent window, Tab tab, Point... path) { return startAt(entry(window, tab), path); }

    /** As {@link #start}, from the parent-tab bar's entry that shows {@code tab}: the drag of its whole parent tab. */
    static Component startParentTab(WindowContent window, Tab tab, Point... path) {
        return startAt(barEntry(window, tab), path);
    }

    /** As {@link #startParentTab}, then releases at the last point. */
    static void dropParentTab(WindowContent window, Tab tab, Point... path) {
        mouse(startParentTab(window, tab, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1]);
    }

    /** Presses in the middle of {@code source}, drags 12 px down and on to each point, without releasing. */
    private static Component startAt(Component source, Point... path) {
        Point press = middle(source);
""", 1)])
T = "jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTest.java"
edit(T, [
    ("import dev.jasper.app.layout.Orientation;\n",
     "import dev.jasper.app.layout.Orientation;\nimport dev.jasper.app.layout.ParentTab;\nimport dev.jasper.app.layout.ParentTabId;\n"
     "import dev.jasper.app.layout.ParentTabs;\n", 1),
    ("import java.util.ArrayDeque;\n", "import java.util.ArrayDeque;\nimport java.util.Arrays;\n", 1),
    ("""    @Test void escEndsTheDragWithoutADrop() throws Exception {
""", """    /** {@code title}'s entry's left edge in {@code bar}. */
    private static int left(WindowTabs bar, String title) {
        return SwingUtilities.convertPoint(WindowTabsTest.named(bar, "select:" + title), 0, 0, bar).x;
    }

    /**
     * Parent tabs spec 5 and workspace plan 7c Rulings 1 to 3: a parent tab dragged along its bar takes its new place on
     * the release, its entries there at once though motion is on, and keeps its tabs and the one that shows.
     */
    @Test void aParentTabDraggedAlongItsBarTakesItsNewPlaceAtOnce() throws Exception {
        edt(() -> {
            ParentTabId first = owner.activeParentTab();
            owner.currentTerminal().rename("first");
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("second");
            ParentTabId middle = owner.activeParentTab();
            owner.newParentTab(HOME);
            owner.currentTerminal().rename("third");
            Tab third = owner.selectedTab();
            ParentTabId last = owner.activeParentTab();
            owner.update();
            owner.setAnimations(AnimationSettings.defaults());
            relayout(frame);
            var bar = (WindowTabs) owner.parentTabBar();
            Tab shown = owner.parentTabs().bar().tabs().getFirst();
            Point pastThird = onParentTab(owner, third, 0.75);
            Component source = startParentTab(owner, shown, pastThird);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.active()).isTrue();
            assertThat(drag.dragged()).as("the parent tab's entry").isSameAs(shown);
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).contains(new TabDrag.Target.Bar(owner, 2));
            assertThat(drag.label().text()).isEqualTo("first");
            assertThat(drag.overlay().getParent()).isSameAs(SwingUtilities.getRootPane(owner).getLayeredPane());
            assertThat(drag.overlay().placeholder()).as("a bar in the parent-tab bar").isNotNull();
            assertThat(drag.overlay().highlight()).isNull();
            assertThat(owner.parentTabs().model().parentTabs()).extracting(ParentTab::id).as("nothing moves before the release")
                .containsExactly(first, middle, last);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastThird);
            assertThat(owner.parentTabs().model().parentTabs()).extracting(ParentTab::id).containsExactly(middle, last, first);
            assertThat(Arrays.stream(owner.parentTabs().getComponents()).filter(WorkspaceView.class::isInstance)
                .map(view -> ((WorkspaceView) view).id()).toList()).as("the views follow the bar").containsExactly(middle, last, first);
            assertThat(owner.activeParentTab()).as("the press showed it, and it stays shown").isEqualTo(first);
            assertThat(left(bar, "second")).isLessThan(left(bar, "third"));
            assertThat(left(bar, "third")).as("the entries are in their new places at once").isLessThan(left(bar, "first"));
            assertThat(bar.motionRunning()).as("and never slide").isFalse();
            assertThat(owner.parentTabs().view(first).orElseThrow().tabs()).as("its tabs stay in it").containsExactly(a, b, c);
            assertThat(drag.active()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
        });
    }

    /** Review Focus 4 and workspace plan 7c Rulings 2 and 9: its own place, a pane and a pane's strip take no parent tab. */
    @Test void aParentTabDropsOnlyAlongItsBarAndNotWhereItIs() throws Exception {
        edt(() -> {
            ParentTabId first = owner.activeParentTab();
            owner.newParentTab(HOME);
            relayout(frame);
            Tab shown = owner.parentTabs().bar().tabs().getFirst();
            Component source = startParentTab(owner, shown, onParentTab(owner, shown, 0.25));
            TabDrag drag = owner.tabDrag();
            ParentTabs pressed = owner.parentTabs().model();
            assertThat(drag.draggedParentTab()).isEqualTo(first);
            assertThat(drag.target()).as("its own place in the bar").isEmpty();
            assertThat(drag.overlay().getParent()).as("no landing shows").isNull();
            Point pane = inside(cards(owner, owner.workspace().model().activeGroup()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, pane);
            assertThat(drag.target()).as("a pane (parent tabs spec 13)").isEmpty();
            Point strip = onTab(owner, a, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, strip);
            assertThat(drag.target()).as("a pane's strip").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, strip);
            assertThat(owner.parentTabs().model()).as("nothing changes").isEqualTo(pressed);
            assertThat(owner.parentTabs().view(first).orElseThrow().tabs()).containsExactly(a, b, c);
        });
    }

    /** Review Focus 5 and workspace plan 7c Rulings 1: the tab a parent tab's entry shows closing ends its drag clean. */
    @Test void aParentTabsDragEndsWhenTheTabItsEntryShowsCloses() throws Exception {
        edt(() -> {
            ParentTabId first = owner.activeParentTab();
            owner.newParentTab(HOME);
            Tab other = owner.selectedTab();
            ParentTabId second = owner.activeParentTab();
            relayout(frame);
            Tab shown = owner.parentTabs().bar().tabs().getFirst();
            Point pastOther = onParentTab(owner, other, 0.75);
            Component source = startParentTab(owner, shown, pastOther);
            assertThat(owner.tabDrag().target()).isPresent();
            owner.closeTab(shown);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).as("the Esc dispatcher goes").isEmpty();
            assertThat(motionListeners()).as("the lost-release listener goes").isEqualTo(listeners);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastOther);
            assertThat(owner.parentTabs().model().parentTabs()).extracting(ParentTab::id).as("nothing lands")
                .containsExactly(first, second);
        });
    }

    /** Workspace plan 7c Rulings 1: the parent tab's entry showing another of its tabs ends its drag. */
    @Test void aParentTabsDragEndsWhenItsEntryShowsAnotherTab() throws Exception {
        edt(() -> {
            ParentTabId first = owner.activeParentTab();
            owner.newParentTab(HOME);
            ParentTabId second = owner.activeParentTab();
            Tab other = owner.selectedTab();
            relayout(frame);
            Point pastOther = onParentTab(owner, other, 0.75);
            Component source = startParentTab(owner, c, pastOther);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.Bar(owner, 1));
            owner.selectTab(a);
            assertThat(owner.tabDrag().active()).as("its entry shows a, no longer c").isFalse();
            assertThat(owner.tabDrag().overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastOther);
            assertThat(owner.parentTabs().model().parentTabs()).extracting(ParentTab::id).containsExactly(first, second);
        });
    }

    /** Parent tabs spec 7: Enterprise's bar, the classic tabs, always shows, and its parent tabs drag along it too. */
    @Test void enterprisesBarDragsItsParentTabsAlongItToo() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                ParentTabId first = classic.activeParentTab();
                classic.newParentTab(HOME);
                ParentTabId second = classic.activeParentTab();
                frame(classic, 100, 50, 1208, 760);
                assertThat(classic.parentTabBar()).isInstanceOf(ClassicTabs.class);
                var entries = classic.parentTabs().bar().tabs();
                dropParentTab(classic, entries.get(1), onParentTab(classic, entries.get(0), 0.25));
                assertThat(classic.parentTabs().model().parentTabs()).extracting(ParentTab::id).containsExactly(second, first);
            } finally {
                if (classic != null) classic.tabDrag().cancel();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragTest'`
Expected: FAIL to compile: `cannot find symbol` for `TabDrag.Target.Bar` and `draggedParentTab()`.

- [ ] **Step 3: The drag carries a parent tab along its bar**

`TabDrag` keeps its window (`from`) for both kinds of drag, and either a pane (`group`) or a parent tab (`parentTab`).
Both presses share `press`. A parent tab's targets come from `onBar`, which in this task takes only the drag's own bar
while it shows. Its landing is the strip marker in the bar's coordinates, and its drop is the window's reorder.

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
D = W + "TabDrag.java"
edit(D, [
    ("import dev.jasper.app.dock.WorkspaceDropResolver.Drop;\n",
     "import dev.jasper.app.dock.WorkspaceDropResolver.Drop;\nimport dev.jasper.app.layout.ParentTabId;\n", 1),
    (""" * dragged tab closing or leaving its window or its group (a command moving it) end the drag without a drop. EDT only.
 */
final class TabDrag {
""", """ * dragged tab closing or leaving its window or its group (a command moving it) end the drag without a drop.
 * <p>A left press on a parent tab's entry in a window's parent-tab bar drags the whole parent tab the same way (parent
 * tabs spec 5): along its bar, where a release puts it before the first of the bar's other entries whose middle is not
 * left of the cursor and the bar's entries take their new places at once. A pane takes no parent tab (parent tabs spec
 * 13). The drag ends when the tab its entry shows closes, leaves it or gives way to another of its tabs. EDT only.
 */
final class TabDrag {
""", 1),
    ("""        /**
         * Out of every window, into a new child window whose workspace takes {@code bounds}, on the display under
""", """        /**
         * Into {@code window}'s parent-tab bar at {@code index}: a parent tab's drop (parent tabs spec 5).
         *
         * @param window the window
         * @param index the parent tab's place among the bar's entries other than its own
         */
        record Bar(WindowContent window, int index) implements Target { }

        /**
         * Out of every window, into a new child window whose workspace takes {@code bounds}, on the display under
""", 1),
    ("""    /** While a press is pending or a drag runs: the tab's group, the tab, the component the press reached, the press on screen. */
    private GroupView group;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    /** Where the press held the tab, from the tab's top-left corner, and the tab's group's size: a tear-out's window. */
    private Point grab;
    private Dimension groupSize;
""", """    /**
     * While a press is pending or a drag runs: the drag's window; the tab's group, or for a parent tab's drag its id
     * (the other is null); the tab, or the parent tab's entry, the tab it shows; the component the press reached; the
     * press on screen.
     */
    private WindowContent from;
    private GroupView group;
    private ParentTabId parentTab;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    /**
     * Where the press held the entry, from its top-left corner, and the size of the tab's group or of the parent tab's
     * workspace: a tear-out's window.
     */
    private Point grab;
    private Dimension size;
""", 1),
    ("""    /** @return the dragged tab while a drag runs, else null */
    Tab dragged() { return dragging ? tab : null; }
""", """    /** @return the dragged tab, or the dragged parent tab's entry, while a drag runs; else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return the dragged parent tab while a parent tab's drag runs, else null */
    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }
""", 1),
    ("""    void pressed(GroupView group, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        int index = group.tabs().indexOf(tab);
        List<Rectangle> places = group.tabBounds(group);
        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || index < 0 || index >= places.size()) return;
        this.group = group;
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
        Point inGroup = SwingUtilities.convertPoint(source, event.getPoint(), group);
        grab = new Point(inGroup.x - places.get(index).x, inGroup.y - places.get(index).y);
        groupSize = group.getSize();
    }
""", """    void pressed(GroupView group, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        if (press(group.owner(), tab, event, group, group.tabs().indexOf(tab), group.tabBounds(group), group.getSize()))
            this.group = group;
    }

    /**
     * A left press on {@code tab}'s entry in {@code bar}, the entry of the parent tab holding it: a drag of that whole
     * parent tab may start from it (parent tabs spec 5). A press while another is pending or a drag runs ends that one
     * first.
     */
    void pressed(ParentTabBar bar, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        WindowContent window = bar.owner();
        Optional<ParentTabId> id = window.parentTabOf(tab);
        Optional<WorkspaceView> view = id.flatMap(window.parentTabs()::view);
        if (view.isPresent() && press(window, tab, event, bar.strip().component(), bar.tabs().indexOf(tab),
                bar.strip().tabBounds(), view.get().getSize()))
            parentTab = id.get();
    }

    /**
     * Takes a left press in {@code window} on the entry at {@code index} of {@code places}, the entries' rectangles in
     * {@code strip}'s coordinates; {@code size} is a tear-out's. False for any other press, which starts nothing.
     */
    private boolean press(WindowContent window, Tab tab, MouseEvent event, Component strip, int index,
                          List<Rectangle> places, Dimension size) {
        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || index < 0 || index >= places.size())
            return false;
        from = window;
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
        Point inStrip = SwingUtilities.convertPoint(source, event.getPoint(), strip);
        grab = new Point(inStrip.x - places.get(index).x, inStrip.y - places.get(index).y);
        this.size = size;
        return true;
    }
""", 1),
    ("        if (group != null && group.owner() == window) cancel();\n", "        if (window != null && from == window) cancel();\n", 1),
    ("""    /** Whether the tab is still where the press found it: open, in the press's window and in the press's group. */
    private boolean inPlace() {
        return tab != null && !tab.closed() && Objects.equals(tab.window(), group.owner().id())
            && group.owner().workspace().model().groupOf(tab.id()).filter(group.id()::equals).isPresent();
    }
""", """    /**
     * Whether the entry is still where the press found it: its tab open and in the press's window, there in the press's
     * group of the shown parent tab, or, for a parent tab's drag, still the tab the press's parent tab shows.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        // A parent tab's drag holds its entry: the tab its parent tab shows.
        if (parentTab != null)
            return from.parentTabs().view(parentTab).map(WorkspaceView::selected).filter(tab::equals).isPresent();
        return from.workspace().model().groupOf(tab.id()).filter(group.id()::equals).isPresent();
    }
""", 1),
    ("        label.show(group.title(tab), group.icon(tab), labelAt(at));\n",
     "        label.show(from.tabTitle(tab), from.tabIcon(tab), labelAt(at));\n", 1),
    ("""    /** End the drag when the dragged tab closes, or changes so that it is no longer where the press found it. */
    private Subscription closing, changed;
""", """    /**
     * End the drag when the dragged tab closes, or changes so that it is no longer where the press found it, and a
     * parent tab's drag when its entry gives way to another of its tabs.
     */
    private Subscription closing, changed, shown;
""", 1),
    ("        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });\n", """        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });
        // A parent tab's entry giving way to another of its tabs (Ctrl+Tab, a request opening there) ends its drag.
        if (parentTab != null) shown = tab.onActive(active -> { if (!inPlace()) cancel(); });
""", 1),
    ("""        if (changed != null) {
            changed.close();
            changed = null;
        }
""", """        if (changed != null) {
            changed.close();
            changed = null;
        }
        if (shown != null) {
            shown.close();
            shown = null;
        }
""", 1),
    ("""        GraphicsDevice home = device(group.owner());
        OptionalInt under = WorkspaceDropResolver.windowAt(at,
            open.stream().map(window -> onScreen(window, window == group.owner(), home)).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A pinned tab stays in its window, as the registry keeps it there: no other window takes it.
            if (!tab.pinned() || window == group.owner()) target = into(window, at);
        } else if (WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !tab.pinned()
                && (group.owner().isMain() || group.owner().tabCount() > 1)) {
""", """        GraphicsDevice home = device(from);
        OptionalInt under = WorkspaceDropResolver.windowAt(at,
            open.stream().map(window -> onScreen(window, window == from, home)).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A parent tab drops on a bar alone; a pinned tab stays in its window, as the registry keeps it there.
            if (parentTab != null) target = onBar(window, at);
            else if (!tab.pinned() || window == from) target = into(window, at);
        } else if (parentTab == null && WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !tab.pinned()
                && (from.isMain() || from.tabCount() > 1)) {
""", 1),
    ("""            .map(drop -> new Target.Into(window, drop));
    }
""", """            .map(drop -> new Target.Into(window, drop));
    }

    /**
     * The dragged parent tab's drop on {@code window}'s parent-tab bar at {@code at} (parent tabs spec 5): before the
     * first of the bar's other entries whose middle is not left of the cursor. Empty off the bar and for the place the
     * parent tab has; for now, any bar but the drag's own takes none.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window != from || !shows) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, window.parentTabs());
        OptionalInt index = WorkspaceDropResolver.place(local, barArea(window), barEntries(window));
        if (index.isEmpty() || window == from && index.getAsInt() == from.parentTabs().model().indexOf(parentTab))
            return Optional.empty();
        return Optional.of(new Target.Bar(window, index.getAsInt()));
    }

    /** {@code window}'s parent-tab bar in its parent-tabs view's coordinates. */
    private static Rectangle barArea(WindowContent window) { return window.parentTabBar().component().getBounds(); }

    /**
     * The bar's entries in {@code window}'s parent-tabs view's coordinates, in bar order, without the dragged one's
     * own.
     */
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
""", 1),
    ("""        dragging = false;
        group = null;
        tab = null;
""", """        dragging = false;
        from = null;
        group = null;
        parentTab = null;
        tab = null;
""", 1),
    ("        groupSize = null;\n", "        size = null;\n", 1),
    ("""        Tab moving = tab;
        Optional<Target> where = Optional.empty();
""", """        Tab moving = tab;
        WindowContent home = from;
        ParentTabId parent = parentTab;
        Optional<Target> where = Optional.empty();
""", 1),
    ("        where.ifPresent(place -> drop(moving, place));\n", "        where.ifPresent(place -> drop(moving, home, parent, place));\n", 1),
    (""" * the window's active group, where the drop then puts it in place. Out of every window, the window manager's tear-out.
     */
    private void drop(Tab moving, Target where) {
        if (moving.closed()) return;
        switch (where) {
""", """ * the window's active group, where the drop then puts it in place. Out of every window, the window manager's tear-out.
     * A parent tab dropped along its bar takes its new place there ({@code home}, the drag's window, holds {@code parent}).
     */
    private void drop(Tab moving, WindowContent home, ParentTabId parent, Target where) {
        if (moving.closed()) return;
        switch (where) {
            case Target.Bar bar -> home.reorderParentTab(parent, bar.index());
""", 1),
    ("""    /**
     * A tear-out's workspace on screen: the size of the tab's old group, with the tab where the cursor holds it, as
     * IntelliJ and VS Code size a window from the content dragged out of it. The new frame grows it by its own title
     * bar and keeps itself on the display under the cursor ({@code WindowFrame}).
     */
    private Rectangle tearOutBounds(Point at) {
        return new Rectangle(at.x - grab.x, at.y - grab.y, groupSize.width, groupSize.height);
    }
""", """    /**
     * A tear-out's workspace on screen: the size of the tab's old group, or of the parent tab's workspace, with the
     * entry where the cursor holds it, as IntelliJ and VS Code size a window from the content dragged out of it. The
     * new frame grows it by its own title bar and keeps itself on the display under the cursor ({@code WindowFrame}).
     */
    private Rectangle tearOutBounds(Point at) {
        return new Rectangle(at.x - grab.x, at.y - grab.y, size.width, size.height);
    }
""", 1)])
span(D, """    /**
     * Shows the target's landing on its window's layered pane: the area the tab would take as the highlight, or for a strip
""", """        else overlay.show(landing, null, null, null);
    }
""", """    /**
     * Shows the target's landing on its window's layered pane: the area the tab would take as the highlight, or for a strip
     * drop a bar where it would go in the strip as the placeholder, and for a parent tab a bar where it would go in the
     * parent-tab bar. Without a target the overlay leaves its window.
     */
    private void showLanding() {
        WindowContent window = switch (target.orElse(null)) {
            case null -> null;
            case Target.Into into -> into.window();
            case Target.Bar bar -> bar.window();
            case Target.TearOut out -> null;
        };
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
        switch (target.orElseThrow()) {
            case Target.Into into -> {
                WorkspaceView workspace = window.workspace();
                Rectangle landing = SwingUtilities.convertRectangle(workspace,
                    WorkspaceDropResolver.landing(into.drop(), workspace.dropAreas(tab), marker), layers);
                if (into.drop() instanceof Drop.Strip) placeholder = landing;
                else highlight = landing;
            }
            case Target.Bar bar -> placeholder = SwingUtilities.convertRectangle(window.parentTabs(),
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
            case Target.TearOut out -> { }
        }
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, null, null);
    }
""")
edit(W + "ParentTabBar.java", [
    (' * the classic "+" is New Tab; its entries never move, and a press on one starts no drag. EDT only.\n',
     ' * the classic "+" is New Tab. A press on an entry may start the tab drag, which carries the whole parent tab (parent\n'
     ' * tabs spec 5); the entries never slide, and a drop puts them in their new places at once. EDT only.\n', 1),
    ("""    /** A press ends a dock drag whose release was lost; a parent tab does not drag. */
    @Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }
    @Override public void dragged(MouseEvent event) { }
    @Override public void released(MouseEvent event) { }
""", """    /**
     * A left press on a parent tab's entry: the tab drag may start from it, carrying the whole parent tab (parent tabs
     * spec 5). A dock drag still running lost its release, so it ends first.
     */
    @Override public void pressed(Tab tab, MouseEvent event) {
        owner.dockDrag().cancel();
        owner.tabDrag().pressed(this, tab, event);
    }
    /** A drag event from the bar, which the tab drag follows. */
    @Override public void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }
    /** A release on the bar, which ends the tab drag. */
    @Override public void released(MouseEvent event) { owner.tabDrag().released(event); }
""", 1)])
edit(W + "ParentTabsView.java", [(
    """    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
""", """    /**
     * Moves the parent tab {@code id} to {@code index} among the others in the bar, where a drag along the bar dropped
     * it (parent tabs spec 5): its workspace takes its new place among the views. The one that shows stays, no tab
     * moves, and the bar takes the new order at once.
     */
    void reorder(ParentTabId id, int index) {
        if (closed || !views.containsKey(id) || index < 0 || index >= count() || model.indexOf(id) == index) return;
        model = model.move(id, index);
        // The views keep the bar's order, ahead of any other child; a z-order move keeps the focus where it is.
        setComponentZOrder(views.get(id), index);
        owner.workspaceChanged(active(), false);
    }

    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
""", 1)])
edit(W + "WindowContent.java", [(
    """    /** How many parent tabs this window has. */
    int parentTabCount() { return parentTabs.count(); }
""", """    /** How many parent tabs this window has. */
    int parentTabCount() { return parentTabs.count(); }

    /**
     * A parent tab dragged along this window's bar (parent tabs spec 5): it takes {@code index} among the others, and
     * the one that shows stays.
     */
    void reorderParentTab(ParentTabId id, int index) { if (!closed) parentTabs.reorder(id, index); }
""", 1)])
edit(W + "TabStrip.java", [(
    """ * classic tabs, always shown. It lists, selects, closes and opens through its {@link StripSource} only, a group's presses
 * start the tab drag (workspace tabs spec 12), and it shows as active while its group is the active group of an active
 * window, or while the bar's window is active. EDT only.
""", """ * classic tabs, always shown. It lists, selects, closes and opens through its {@link StripSource} only, its presses
 * start the tab drag (workspace tabs spec 12; from the bar, of a whole parent tab: parent tabs spec 5), and it shows as
 * active while its group is the active group of an active window, or while the bar's window is active. EDT only.
""", 1)])
PY
grep -n 'groupSize\|group\.owner()\|group\.title\|group\.icon' jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java
```

Expected: one line, `pressed(GroupView, …)` handing `press` its group's window; every other read of the drag's window
is `from`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS (`TabDragTest` +5; `ParentTabBarTest`, `TabDragWindowsTest` and every other workspace test unchanged in
number). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Parent tab drag: a parent tab drags along its bar

A press on a parent tab's entry starts the tab drag, which carries the
whole parent tab under plan 5's rules. Along its own bar a release puts
it before the first other entry whose middle is not left of the
cursor, the landing a bar in the parent-tab bar; the entries take
their new places at once, and its tabs and the shown parent tab stay.
Its own place, a pane and a pane's strip take no parent tab, and the
tab its entry shows closing ends the drag.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Parent tabs between windows, and tabs torn out into pane windows

Spec 5: a parent tab dropped on another window's bar moves there (Rulings 4), a window showing no bar takes it along
its top (Rulings 5), and one released outside every window becomes a full window (Rulings 6), except a full window's
only parent tab (Rulings 7) and one holding a pinned tab (Rulings 8). A tab released outside every window pops out into
a pane window with its origin (Rulings 10). The drag reaches the window manager through its window (Rulings 12), and
`moveToNewWindow(Tab, Rectangle, Point)` goes. This task also absorbs plan 7a's two hand-offs (Hand-offs absorbed).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (Task 2's text: class Javadoc, `Target`,
  the constructor `:84-118`, `update`, `onBar`, `barArea`, `showLanding`, `drop`), `WindowContent.java` (its own drag
  `:130-135`, after 7b's `manager(WindowManager)`), `WindowManager.java` (the drag `:94-96`, `:434-447`, `:460-461`,
  7b's `popOutTab` Javadoc `:246-248`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java`, `WindowManagerTest.java` (`:479`)

**Interfaces:**
- Consumes: Task 2's `Target.Bar`, `onBar`, `barArea`, `barEntries`, `drop(Tab, WindowContent, ParentTabId, Target)`,
  `TabDragTestSupport.onParentTab`, `barEntry`, `startParentTab`, `dropParentTab`; 7b's
  `WindowManager.moveParentTab(WindowContent, ParentTabId, WindowContent, int)`, `popOutParentTab(WindowContent,
  ParentTabId, Rectangle, Point)`, `popOutTab(Tab, Rectangle, Point)`, `WindowContent.isPaneWindow()`, `origin()`,
  `mayPopOutParentTab(ParentTabId)`, `TabRegistry.movable(Tab)`, `Origin`.
- Produces:
  - `TabDrag(Supplier<List<WindowContent>> windows)`, the one constructor;
  - `Optional<WindowManager> WindowContent.manager()`;
  - `WindowManager.moveToNewWindow(Tab, Rectangle, Point)` gone.

- [ ] **Step 1: Write the failing tests**

The tear-out test now expects a pane window, and the cross-window strip test reads both windows' pane strips (plan
7a's hand-offs). New tests drag parent tabs between the pretend screen's windows. `WindowManagerTest`'s placed tear-out
pops out:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(T + "WindowManagerTest.java", [(
    """            ChildWindow child = manager.moveToNewWindow(tab, new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60))
                .orElseThrow();
""", """            ChildWindow child = manager.popOutTab(tab, new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60))
                .orElseThrow();
            assertThat(child.content().isPaneWindow()).as("a torn-out tab's window is a pane window (parent tabs spec 5)").isTrue();
""", 1)])
edit(T + "TabDragWindowsTest.java", [
    ("import dev.jasper.app.layout.GroupId;\n", "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Layout;\n", 1),
    ("import dev.jasper.app.layout.Orientation;\n",
     "import dev.jasper.app.layout.Orientation;\nimport dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.ParentTab;\n"
     "import dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.ParentTabs;\n", 1),
    (""" * the classic tabs, which always show (a mix of looks only these tests make; the drag reads no look).
 */
""", """ * the classic tabs, which always show (a mix of looks only these tests make; the drag reads no look). A parent tab's
 * drag starts from a window's parent-tab bar, which the standard look shows from two parent tabs.
 */
""", 1),
    ("""    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }
""", """    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    /** A new parent tab in {@code window}, shown and laid out, so its bar shows: its id. Its shell starts at the next launch. */
    private ParentTabId secondParentTab(JasperWindow window) {
        window.content().newParentTab(HOME);
        relayout(frameOf(window));
        return window.content().activeParentTab();
    }

    /** The tab that the bar's entry of {@code window}'s parent tab {@code id} shows. */
    private static Tab entryOf(WindowContent window, ParentTabId id) {
        return window.parentTabs().view(id).orElseThrow().selected();
    }

    /** {@code window}'s parent tabs, in bar order. */
    private static List<ParentTabId> parentTabIds(WindowContent window) {
        return ((ParentTabs) window.windowLayout()).parentTabs().stream().map(ParentTab::id).toList();
    }

    /** The band at the top of {@code window}'s parent tabs where its bar shows, on the pretend screen. */
    private static Rectangle band(WindowContent window) {
        var parents = window.parentTabs();
        int height = window.parentTabBar().component().getPreferredSize().height;
        return onScreen(parents, new Rectangle(0, 0, parents.getWidth(), height));
    }
""", 1),
    ("""            assertThat(from.tabs()).hasSize(1);
""", """            assertThat(from.tabs()).hasSize(1);
            // Parent tabs spec 7 (workspace plan 7a's hand-off): after the move each pane shows its strip by its own tabs.
            assertThat(from.windowTabs().isVisible()).as("the pane it left holds one tab").isFalse();
            assertThat(to.windowTabs().isVisible()).as("the pane it joined holds three").isTrue();
""", 1),
    ("""    @Test void aTabReleasedOutsideEveryWindowTearsOutWithItsGroupsSizeUnderTheCursor() throws Exception {
""", """    /** Parent tabs spec 5 and workspace plan 7c Rulings 10: a tab released outside every window pops out into a pane window. */
    @Test void aTabReleasedOutsideEveryWindowPopsOutIntoAPaneWindowUnderTheCursor() throws Exception {
""", 1),
    ("""            assertThat(torn.content().tabs()).containsExactly(tab);
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
""", """            assertThat(torn.content().tabs()).containsExactly(tab);
            assertThat(torn.content().isPaneWindow()).isTrue();
            assertThat(torn.content().origin()).as("where the tab was: its pane, which keeps the other tab")
                .contains(new Origin(from.activeParentTab(), group.id(), Side.RIGHT, 0.5));
            // Workspace plan 7a's hand-off: its one tab shows no strip, and a pane window has no parent-tab bar.
            assertThat(torn.content().windowTabs().isVisible()).isFalse();
            assertThat(torn.content().parentTabBar().component().isVisible()).isFalse();
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
""", 1),
    ("""    @Test void aPluginsTabTearsOutAndDragsBackLikeATerminalTab() throws Exception {
""", """    /** Parent tabs spec 5 and workspace plan 7c Rulings 4: a parent tab dropped on another window's bar moves there whole. */
    @Test void aParentTabDroppedOnAnotherWindowsBarMovesThereWhole() throws Exception {
        ParentTabId[] ids = new ParentTabId[3];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            secondParentTab(main);
            ids[1] = child.content().activeParentTab();
            ids[2] = secondParentTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            ParentTabId moving = ids[0], left = ids[1], right = ids[2];
            WorkspaceView workspace = from.parentTabs().view(moving).orElseThrow();
            List<Tab> tabs = workspace.tabs();
            Layout layout = workspace.model();
            var sessions = tabs.stream().map(TabDragWindowsTest::session).toList();
            Point between = onParentTab(to, entryOf(to, right), 0.25);
            facts.clear();
            Component source = startParentTab(from, entryOf(from, moving), between);
            assertThat(manager.tabDrag().draggedParentTab()).isEqualTo(moving);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Bar(to, 1));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            assertThat(manager.tabDrag().overlay().placeholder()).as("a bar between its entries").isNotNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, between);
            assertThat(parentTabIds(to)).containsExactly(left, moving, right);
            assertThat(to.parentTabs().view(moving).orElseThrow().model()).as("whole, with its id and layout").isEqualTo(layout);
            assertThat(to.activeParentTab()).isEqualTo(moving);
            assertThat(parentTabIds(from)).hasSize(1);
            assertThat(from.parentTabBar().component().isVisible()).as("one parent tab left: its bar hides").isFalse();
            assertThat(moves()).as("one move per tab, reported where it arrives").containsExactlyElementsOf(tabs.stream()
                .map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id())).toList());
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            for (int i = 0; i < tabs.size(); i++) assertThat(session(tabs.get(i))).as("the same session").isSameAs(sessions.get(i));
        });
    }

    /**
     * Review Focus 2 and workspace plan 7c Rulings 5: a window showing no bar (the standard look, one parent tab) takes a
     * parent tab over the band at its top, where its bar shows, at its end; over its panes, nothing.
     */
    @Test void aWindowShowingNoBarTakesAParentTabAlongItsTop() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondParentTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            assertThat(to.parentTabBar().component().isVisible()).as("one parent tab").isFalse();
            Tab moving = entryOf(from, ids[1]);
            Point panes = inside(cards(to, activeGroup(child)), 0.5, 0.5);
            Component source = startParentTab(from, moving, panes);
            assertThat(manager.tabDrag().target()).as("a pane takes no parent tab (parent tabs spec 13)").isEmpty();
            Rectangle band = band(to);
            Point top = inside(band, 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, top);
            assertThat(manager.tabDrag().target()).as("at the end of its bar").contains(new TabDrag.Target.Bar(to, 1));
            JLayeredPane layers = SwingUtilities.getRootPane(to).getLayeredPane();
            Point corner = new Point(band.getLocation());
            SwingUtilities.convertPointFromScreen(corner, layers);
            assertThat(manager.tabDrag().overlay().highlight()).as("the band where its bar shows")
                .isEqualTo(new Rectangle(corner, band.getSize()));
            assertThat(manager.tabDrag().overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(parentTabIds(to)).containsExactly(ids[0], ids[1]);
            assertThat(to.parentTabBar().component().isVisible()).as("two parent tabs: its bar shows").isTrue();
            assertThat(moving.window()).isEqualTo(child.id());
            assertThat(from.parentTabBar().component().isVisible()).isFalse();
        });
    }

    /**
     * Review Focus 2 and workspace plan 7c Rulings 5: the main window's empty workspace, which shows no bar, takes a
     * parent tab dragged along its top in its place.
     */
    @Test void theEmptyMainWorkspaceTakesAParentTabAlongItsTop() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondParentTab(child));
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            for (Tab open : List.copyOf(to.tabs())) to.closeTab(open);
            relayout(frameOf(main));
            assertThat(to.tabs()).isEmpty();
            assertThat(to.parentTabBar().component().isVisible()).as("the empty workspace shows no bar").isFalse();
            Tab moving = entryOf(from, id[0]);
            Point top = inside(band(to), 0.5, 0.5);
            Component source = startParentTab(from, moving, top);
            assertThat(manager.tabDrag().target()).as("its bar has no entry").contains(new TabDrag.Target.Bar(to, 0));
            mouse(source, MouseEvent.MOUSE_RELEASED, top);
            assertThat(parentTabIds(to)).as("in the empty workspace's place").containsExactly(id[0]);
            assertThat(to.parentTabBar().component().isVisible()).as("one parent tab: still no bar").isFalse();
            assertThat(moving.window()).isEqualTo(main.id());
            assertThat(parentTabIds(from)).hasSize(1);
        });
    }

    /** Parent tabs spec 5 and workspace plan 7c Rulings 6: a parent tab released outside every window becomes a full window. */
    @Test void aParentTabReleasedOutsideEveryWindowBecomesAFullWindowUnderTheCursor() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondParentTab(main));
        launch();
        edt(() -> {
            WindowContent from = main.content();
            WorkspaceView workspace = from.parentTabs().view(id[0]).orElseThrow();
            Layout layout = workspace.model();
            Tab moving = entryOf(from, id[0]);
            Object session = session(moving);
            TabStrip bar = from.parentTabBar();
            Rectangle home = onScreen(bar.component(), bar.tabBounds().get(1));
            Point press = middle(barEntry(from, moving));
            Point outside = new Point(2600, 900);
            // The new window's workspace is the parent tab's size, with the entry's corner where the cursor holds it.
            Rectangle expected = new Rectangle(outside.x - (press.x - home.x), outside.y - (press.y - home.y),
                workspace.getWidth(), workspace.getHeight());
            facts.clear();
            Component source = startParentTab(from, moving, outside);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.TearOut(expected, outside));
            assertThat(manager.tabDrag().overlay().getParent()).as("no landing in any window").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow full = manager.children().getLast();
            assertThat(full).isNotSameAs(child);
            assertThat(full.content().isPaneWindow()).as("a full window, with parent tabs").isFalse();
            assertThat(full.content().windowLayout()).as("the parent tab, whole").isEqualTo(ParentTabs.of(new ParentTab(id[0], layout)));
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
            assertThat(cursors).as("on the display under the cursor").containsExactly(outside);
            assertThat(session(moving)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), full.id()));
            assertThat(from.parentTabCount()).isEqualTo(1);
        });
    }

    /**
     * Review Focus 3 and workspace plan 7c Rulings 7: a full window's only parent tab stays when released outside every
     * window, as its window already is its own, but moves onto another window's bar, and its window closes after the move.
     */
    @Test void aFullWindowsOnlyParentTabStaysOutsideButMovesOntoAnotherWindowsBar() throws Exception {
        edt(() -> {
            classicChild();
            secondParentTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            ParentTabId only = from.activeParentTab();
            Tab moving = entryOf(from, only);
            Point outside = new Point(3000, 1500);
            Component source = startParentTab(from, moving, outside);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("its window already is its own").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onParentTab(from, moving, 0.75));
            assertThat(manager.tabDrag().target()).as("and its own bar has no other place for it").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            List<ParentTabId> before = parentTabIds(to);
            facts.clear();
            dropParentTab(from, moving, onParentTab(to, to.parentTabs().bar().tabs().getLast(), 0.75));
            assertThat(parentTabIds(to)).as("at the end of the main window's bar").containsExactly(before.get(0), before.get(1), only);
            assertThat(child.closed()).as("its last parent tab moved out").isTrue();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /** Review Focus 1 and workspace plan 7c Rulings 8: a parent tab holding a pinned tab moves along its own bar only. */
    @Test void aParentTabHoldingAPinnedTabMovesAlongItsOwnBarOnly() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondParentTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content();
            var pin = from.parentTabs().view(ids[0]).orElseThrow().tabs().getFirst().pin();
            try {
                facts.clear();
                Point there = inside(band(child.content()), 0.5, 0.5);
                Component source = startParentTab(from, entryOf(from, ids[0]), there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("a modal owner holds one of its tabs in its window").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(2600, 900));
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                Point past = onParentTab(from, entryOf(from, ids[1]), 0.75);
                mouse(source, MouseEvent.MOUSE_DRAGGED, past);
                assertThat(manager.tabDrag().target()).as("its own bar still takes it").contains(new TabDrag.Target.Bar(from, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, past);
                assertThat(parentTabIds(from)).containsExactly(ids[1], ids[0]);
                assertThat(manager.children()).containsExactly(child);
                assertThat(moves()).isEmpty();
            } finally {
                pin.close();
            }
        });
    }

    /** Review Focus 4 and workspace plan 7c Rulings 9: a pane window has no parent tabs and takes none; nothing moves. */
    @Test void aPaneWindowTakesNoParentTab() throws Exception {
        ParentTabId[] id = new ParentTabId[1];
        edt(() -> id[0] = secondParentTab(main));
        launch();
        edt(() -> {
            ChildWindow pane = manager.popOutTab(child.content().tabs().get(1)).orElseThrow();
            place(frameOf(pane), 100, 900, 800, 400);
            assertThat(pane.content().isPaneWindow()).isTrue();
            WindowContent from = main.content();
            Tab moving = entryOf(from, id[0]);
            Point top = inside(band(pane.content()), 0.5, 0.5);
            Component source = startParentTab(from, moving, top);
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

    @Test void aPluginsTabTearsOutAndDragsBackLikeATerminalTab() throws Exception {
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --continue`
Expected: FAIL. `aTabReleasedOutsideEveryWindowPopsOutIntoAPaneWindowUnderTheCursor` fails on `isPaneWindow()`, and
`aParentTabDroppedOnAnotherWindowsBarMovesThereWhole`, `aWindowShowingNoBarTakesAParentTabAlongItsTop`,
`theEmptyMainWorkspaceTakesAParentTabAlongItsTop`,
`aParentTabReleasedOutsideEveryWindowBecomesAFullWindowUnderTheCursor` and
`aFullWindowsOnlyParentTabStaysOutsideButMovesOntoAnotherWindowsBar` find no target where they expect one. Every
other test passes: `WindowManagerTest`'s port uses plan 7b's `popOutTab` already.

- [ ] **Step 3: The drag moves parent tabs between windows and tears tabs out into pane windows**

What leaves the drag's window goes to that window's manager. The constructor loses its tear-out callback:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
D = W + "TabDrag.java"
edit(D, [
    ("import java.util.function.BiConsumer;\n", "", 1),
    ("""         * {@code cursor}.
         *
         * @param bounds the new window's workspace on screen; its frame adds its title bar
""", """         * {@code cursor}: a pane window for a tab, a full window for a parent tab (parent tabs spec 5).
         *
         * @param bounds the new window's workspace on screen; its frame adds its title bar
""", 1),
    ("""    private final Supplier<List<WindowContent>> windows;
    private final BiConsumer<Tab, Target.TearOut> tearOut;
""", """    private final Supplier<List<WindowContent>> windows;
""", 1),
    ("""    /**
     * @param windows the windows a drag can reach, the most recently active first
     * @param tearOut opens a new child window for a tab where a tear-out target says, or leaves the tab where it is
     */
    TabDrag(Supplier<List<WindowContent>> windows, BiConsumer<Tab, Target.TearOut> tearOut) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.tearOut = Objects.requireNonNull(tearOut, "tearOut");
    }
""", """    /**
     * @param windows the windows a drag can reach, the most recently active first; what leaves its window goes through
     *     the window manager of the drag's window ({@code WindowContent.manager()})
     */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }
""", 1),
    ("""        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A parent tab drops on a bar alone; a pinned tab stays in its window, as the registry keeps it there.
            if (parentTab != null) target = onBar(window, at);
            else if (!tab.pinned() || window == from) target = into(window, at);
        } else if (parentTab == null && WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !tab.pinned()
                && (from.isMain() || from.tabCount() > 1)) {
            // Outside every window of Jasper's: a new child window, except for a child window's only tab (spec 12).
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
""", """        // What the registry keeps in its window stays there: a pinned tab, or a parent tab holding a tab that is pinned
        // or being closed. No other window takes it, and it tears nothing out.
        boolean stays = parentTab == null ? tab.pinned() : !movable();
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A parent tab drops on a bar alone (parent tabs spec 13).
            if (!stays || window == from) target = parentTab == null ? into(window, at) : onBar(window, at);
        } else if (WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !stays && tearsOut()) {
            // Outside every window of Jasper's: a pane window for a tab, a full window for a parent tab.
            target = Optional.of(new Target.TearOut(tearOutBounds(at), new Point(at)));
        }
""", 1),
    ("""    /**
     * The dragged parent tab's drop on {@code window}'s parent-tab bar at {@code at} (parent tabs spec 5): before the
     * first of the bar's other entries whose middle is not left of the cursor. Empty off the bar and for the place the
     * parent tab has; for now, any bar but the drag's own takes none.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window != from || !shows) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, window.parentTabs());
        OptionalInt index = WorkspaceDropResolver.place(local, barArea(window), barEntries(window));
""", """    /**
     * The dragged parent tab's drop on {@code window}'s parent-tab bar at {@code at} (parent tabs spec 5): before the
     * first of the bar's other entries whose middle is not left of the cursor; while another window's bar hides, at its
     * end, over the band the bar takes when it shows (workspace plan 7c Rulings 5). Empty for a pane window, which has
     * no parent tabs, off the bar, and for the place the parent tab has.
     */
    private Optional<Target> onBar(WindowContent window, Point at) {
        boolean shows = window.parentTabBar().component().isVisible();
        if (window.isPaneWindow() || window == from && !shows) return Optional.empty();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, window.parentTabs());
        Rectangle area = barArea(window);
        OptionalInt index = shows ? WorkspaceDropResolver.place(local, area, barEntries(window))
            : area.contains(local) ? OptionalInt.of(window.parentTabs().bar().tabs().size()) : OptionalInt.empty();
""", 1),
    ("""    /** {@code window}'s parent-tab bar in its parent-tabs view's coordinates. */
    private static Rectangle barArea(WindowContent window) { return window.parentTabBar().component().getBounds(); }
""", """    /**
     * {@code window}'s parent-tab bar in its parent-tabs view's coordinates: the bar while it shows, else the band at
     * the view's top that it takes when it shows.
     */
    private static Rectangle barArea(WindowContent window) {
        Component bar = window.parentTabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }

    /**
     * Whether every tab of the dragged parent tab may move: none pinned or being closed (workspace plan 7b Rulings 7).
     */
    private boolean movable() {
        Optional<WorkspaceView> view = from.parentTabs().view(parentTab);
        return view.isPresent() && view.get().tabs().stream().allMatch(from.tabRegistry()::movable);
    }

    /**
     * Whether a release outside every window takes the entry out of its window: never a child window's only tab, whose
     * window already is its own (spec 12), nor a full window's only parent tab (workspace plan 7b Rulings 8).
     */
    private boolean tearsOut() {
        return parentTab == null ? from.isMain() || from.tabCount() > 1 : from.mayPopOutParentTab(parentTab);
    }
""", 1),
    ("""            case Target.Bar bar -> placeholder = SwingUtilities.convertRectangle(window.parentTabs(),
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
""", """            case Target.Bar bar -> {
                // A bar that hides takes the parent tab at its end: its band shows (workspace plan 7c Rulings 5).
                if (!window.parentTabBar().component().isVisible())
                    highlight = SwingUtilities.convertRectangle(window.parentTabs(), barArea(window), layers);
                else placeholder = SwingUtilities.convertRectangle(window.parentTabs(),
                    WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
            }
""", 1),
    (""" * the window's active group, where the drop then puts it in place. Out of every window, the window manager's tear-out.
     * A parent tab dropped along its bar takes its new place there ({@code home}, the drag's window, holds {@code parent}).
     */
""", """ * the window's active group, where the drop then puts it in place. Out of every window, the window manager pops it
     * out into a pane window. A parent tab dropped along its bar takes its new place there; on another window's bar, or
     * out of every window into a full window, the window manager moves it whole ({@code home}, the drag's window, holds
     * {@code parent}). A window no manager opened moves nothing out of itself.
     */
""", 1),
    ("""            case Target.Bar bar -> home.reorderParentTab(parent, bar.index());
""", """            case Target.Bar bar -> {
                if (bar.window() == home) home.reorderParentTab(parent, bar.index());
                // Another window's bar: the window manager moves the parent tab whole, through the tab registry.
                else home.manager().ifPresent(manager -> manager.moveParentTab(home, parent, bar.window(), bar.index()));
            }
""", 1),
    ("""            case Target.TearOut out -> tearOut.accept(moving, out);
""", """            case Target.TearOut out -> home.manager().ifPresent(manager -> {
                if (parent == null) manager.popOutTab(moving, out.bounds(), out.cursor());
                else manager.popOutParentTab(home, parent, out.bounds(), out.cursor());
            });
""", 1)])
span(D, " * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of\n",
     " * 13). The drag ends when the tab its entry shows closes, leaves it or gives way to another of its tabs. EDT only.\n",
     """ * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of
 * the drag's own window but that window), the most recently active; over its workspace, the resolver's drop unless it
 * would change nothing; anywhere else in that window (its dock, chrome or title bar), and over any other window of
 * Jasper's (Settings, About, a dialog), nothing; outside every window, a new pane window whose origin is where the tab
 * was (parent tabs spec 5) and whose workspace is the size of the tab's old group, with the tab where the cursor holds
 * it and kept on the display under the cursor, except for a child window's only tab, whose window already is its own. A
 * pinned tab (a modal owner holds it in its window) takes targets in its own window only. Releasing the left button
 * carries the target out: within a window through the workspace's layout operations, into another window through the
 * tab registry's moves, out of every window through the window manager's; none touches a tab's content or session.
 * Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's window
 * losing the focus (a modal dialog, another application), and the dragged tab closing or leaving its window or its
 * group (a command moving it) end the drag without a drop.
 * <p>A left press on a parent tab's entry in a window's parent-tab bar drags the whole parent tab the same way (parent
 * tabs spec 5): along its bar, or onto the bar of another main or full window, where a release puts it before the first
 * of the bar's other entries whose middle is not left of the cursor and the bar's entries take their new places at
 * once; a window whose bar hides (the standard look's one parent tab, or the main window's empty workspace) takes it at
 * its end over the band its bar takes when it shows; outside every window it becomes a full window the size of its
 * workspace, except a full window's only parent tab. A pane and a pane window take no parent tab (parent tabs spec 13),
 * and one holding a tab that is pinned or being closed takes targets along its own bar only. The drag ends when the tab
 * its entry shows closes, leaves it or gives way to another of its tabs. Out of its window the drag goes through the
 * window manager of the drag's window; a window no manager opened moves nothing out of itself. EDT only.
""")
edit(W + "WindowContent.java", [
    ("""    /**
     * Drags a tab from any of this window's strips (workspace tabs spec 12): the window manager's, which every window it
     * opens shares; until it gives that, one of this window's own, which drops within this window and tears nothing out:
     * outside this window it reports a TearOut target its tear-out ignores (Rulings 10).
     */
    private TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this), (tab, out) -> { });
""", """    /**
     * Drags a tab, or a parent tab from the bar, from any of this window's strips (workspace tabs spec 12; parent tabs
     * spec 5): the window manager's, which every window it opens shares; until it gives that, one of this window's own,
     * which drops within this window and moves nothing out of it: outside this window it reports a TearOut target,
     * which a window no manager opened ignores (Rulings 10).
     */
    private TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this));
""", 1),
    ("""    void manager(WindowManager value) { manager = Objects.requireNonNull(value, "value"); }
""", """    void manager(WindowManager value) { manager = Objects.requireNonNull(value, "value"); }

    /** The window manager that opened this window, which carries a drag out of it; empty for any other window. */
    Optional<WindowManager> manager() { return Optional.ofNullable(manager); }
""", 1)])
edit(W + "WindowManager.java", [
    ("""    /** The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first. */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList(),
        (tab, out) -> moveToNewWindow(tab, out.bounds(), out.cursor()));
""", """    /**
     * The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first, and
     * carries what leaves a window to this manager, which each window knows (parent tabs spec 5).
     */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList());
""", 1),
    ("""    /**
     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}.
""", """    /**
     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}: a tab the tab drag releases outside every window too (parent tabs spec 5).
""", 1),
    ("""    /**
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

""", "", 1),
    ("    /** A new child window takes {@code tab}: a pane window from {@code origin}, or a full window when it is null. */\n",
     "    /** A new pane window from {@code origin} takes {@code tab}: Pop Out Tab, and a tab the drag tears out. */\n", 1)])
PY
git grep -n --untracked -E 'moveToNewWindow\(Tab tab, Rectangle|moveToNewWindow\(tab, |BiConsumer<Tab, Target' -- jasper-app/src
```

Expected: the `git grep` prints nothing; the three-argument `moveToNewWindow` and the drag's callback are gone.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS (`TabDragWindowsTest` +7; `TabDragTest`, `WindowManagerTest`, `PopOutTest`, `BringBackTest` and every
other workspace test unchanged in number). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Parent tab drag: between windows, out to a full window, tabs to pane windows

A parent tab dropped on another main or full window's bar moves there
whole through the window manager, one TAB_MOVED per tab; a window
showing no bar takes it over the band at its top, at its end; released
outside every window it becomes a full window at the cursor, except a
full window's only parent tab. A parent tab holding a pinned tab moves
along its own bar only, and pane windows take none. A tab released
outside every window pops out into a pane window with its origin where
it was, in place of plan 5's full child window: the drag reaches the
window manager through its window, and the three-argument
moveToNewWindow is gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Documentation, STATUS and the whole build

**Files:**
- Modify: `README.md` (the drag sentence of 7a's and 7b's shortcut paragraph), `docs/configuration.md` (7a's bar
  sentence), `docs/app-architecture.md` (the `workspace` row, `:180`), `docs/app-maintenance.md` (7a's bar sentence
  `:685-686`, the tab drag paragraph `:695-706`), `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

The user guide for tabs is `README.md`'s shortcut paragraph; `docs/configuration.md` says how the bar moves; the
architecture's `workspace` row names the drag; the maintenance recipe for drags names its parts. Each edit is the
smallest that makes its sentence true (AGENTS.md "Documentation edits"). The plugin documents stay as they are: the SDK
does not change.

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan7c"); from edits import edit
edit("README.md", [(
    "onto another Jasper window, or out of every window into a new one (not a child window's only tab, nor a tab a dialog "
    "holds, which moves within its window only); Esc cancels.",
    "onto another Jasper window, or out of every window into a pane window of its own (not a child window's only tab, nor "
    "a tab a dialog holds, which moves within its window only); drag a parent tab along its bar to reorder it, onto another "
    "window's parent-tab bar (or the top of a main or full window showing none) to move it there, or out of every window "
    "into a full window (not a full window's only parent tab, nor one holding a tab a dialog holds, which moves along its "
    "bar only); Esc cancels.", 1)])
edit("docs/configuration.md", [(
    "at once too, and its tabs never move.\n",
    "at once too, and its tabs never slide: a parent tab dragged along it takes its new place at once.\n", 1)])
edit("docs/app-architecture.md", [(
    "the tab drag (`TabDrag`, the window manager's one, shared by its windows),",
    "the tab drag (`TabDrag`, the window manager's one, shared by its windows, for tabs and parent tabs),", 1)])
edit("docs/app-maintenance.md", [
    ("""`GroupView`), and its entries never move.
""", """`GroupView`), and its entries never slide: a parent tab dragged along it takes its new place at once.
""", 1),
    ("""(`GroupView.pressed`); past the kit's `TAB_REORDER_THRESHOLD` it follows the cursor in screen coordinates over the
""", """(`GroupView.pressed`), and a parent-tab bar's its whole parent tab (`ParentTabBar.pressed`); past the kit's
`TAB_REORDER_THRESHOLD` it follows the cursor in screen coordinates over the
""", 1),
    ("""`WorkspaceView.dropAreas`: change the zones in the resolver and its tests first. A drop within a window is
`WorkspaceView.drop`; one into another window goes through the tab registry first, and a tear-out through the window
manager. A child window's only tab and a pinned tab have no tear-out target, and a pinned tab takes targets in its own
window only.""", """`WorkspaceView.dropAreas`, and a parent tab's place in a bar (`place`): change the zones in the resolver and its tests
first. A drop within a window is `WorkspaceView.drop`, or `ParentTabsView.reorder` for a parent tab; one into another
window goes through the tab registry first (a parent tab's through `WindowManager.moveParentTab`; a window whose bar
hides takes it over the band at its top), and a tear-out through the window manager (`popOutTab` makes a pane window,
`popOutParentTab` a full window). A parent tab drops on bars alone. A child window's only tab, a full window's only
parent tab and a pinned tab have no tear-out target, and a pinned tab, or a parent tab holding one, takes targets in
its own window only.""", 1)])
PY
git diff --stat README.md docs
```

Expected: four files changed (`README.md`, `docs/configuration.md`, `docs/app-architecture.md` and
`docs/app-maintenance.md`).

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
**Workspace tabs, plan 7c: dragging parent tabs — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-19-workspace-7c-parent-tab-drag.md),
[spec](superpowers/specs/2026-10-19-jasper-parent-tabs-design.md)). A parent tab drags as a tab does: a press on its
bar entry (`ParentTabBar.pressed`) hands plan 5's `TabDrag` the whole parent tab (`pressed(ParentTabBar, Tab,
MouseEvent)`, `Target.Bar`). Along its bar a release puts it at the strip rule's place (`WorkspaceDropResolver.place`,
the public `marker`; `ParentTabs.move`, `ParentTabsView.reorder`, `WindowContent.reorderParentTab`) and the entries
take their new places at once. On another main or full window's bar it moves there whole through
`WindowManager.moveParentTab`, a window showing no bar taking it over the band at its top; outside every window it
becomes a full window through `popOutParentTab(…, bounds, cursor)`, except a full window's only parent tab. A parent
tab holding a pinned tab moves along its own bar only, and panes and pane windows take none. A tab released outside
every window pops out into a pane window with its origin (`popOutTab(tab, bounds, cursor)`); the drag reaches the
window manager through its window (`WindowContent.manager()`, `TabDrag(Supplier)`), and
`moveToNewWindow(Tab, Rectangle, Point)` is gone. Plan 7a's two hand-offs are tested: both windows' pane strips after
a move between them, and a torn-out pane window showing no strip and no bar. Rulings (plan, "Rulings"): <one clause
each, 1 to 12>. Spec issues: none. Deviations: <none, or each with its reason>. Left for plans 6a and 6b:
`WindowManager.moveToNewWindow` is gone in every form (plan 7b removed the one-argument form, this plan the
three-argument one), so the 6b re-plan pops a tab out with `popOutTab`; plan 5's hand-off stands (`Opening.bounds` and
`WindowShell.place` are a tear-out's workspace bounds, for pane and full windows alike). `./gradlew check --continue`: **<counts>**. GUI checks for the user:
- a parent tab dragged along its bar, in both looks: the landing bar, then the entries in their new places at once,
  with no slide;
- a parent tab dropped on another window's bar between its entries, and on the top of a standard window with one
  parent tab (the band highlights, then the bar shows with both), every shell running (a `top` stays current);
- a parent tab dragged along the top of the main window's empty workspace (all its tabs closed): it takes the empty
  workspace's place, and no bar shows;
- a parent tab released outside every window: a full window under the cursor, on that display, its workspace the
  parent tab's size (under Enterprise its bar sits above the workspace);
- under Enterprise, a full window's only parent tab: outside every window nothing happens; onto the main window's bar
  it moves and its window closes;
- a tab torn out into a pane window: under the cursor at its old pane's size, its origin where the tab was, no strip
  in the standard look, and Bring Back returning it beside its pane;
- a modal prompt in a tab: its parent tab moves along its own bar only;
- a parent tab dragged over a pane window, a pane or a pane's strip: no landing, and nothing on release.

Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for dragging parent tabs

The README says how a parent tab drags along its bar, onto another
window and out to a full window, and that a torn-out tab becomes a
pane window; configuration, architecture and maintenance name the
drag's parts; STATUS records plan 7c with its rulings, the absorbed
hand-offs and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- **Spec coverage.**
  - Section 5's dragging rows:
    - an item dropped on a strip, a pane's centre or edge in any window: plan 5, unchanged;
    - an item released outside every window, a pane window with its origin: Task 3, Rulings 10;
    - a parent tab reordered within its bar: Tasks 1 and 2;
    - a parent tab dropped on another window's bar: Task 3, Rulings 4 and 5;
    - a parent tab released outside every window, a full window: Task 3, Rulings 6 and 7;
    - a parent tab dropped on a pane, out of scope: Rulings 9, tested in Tasks 2 and 3.
  - Section 5's "no animation" and "sessions survive": Rulings 3, `TabDragTest.aParentTabDraggedAlongItsBarTakesItsNewPlaceAtOnce`;
    the session checks in `TabDragWindowsTest`.
  - Section 10's events: one `TAB_MOVED` per tab between windows, none for a reorder (Rulings 3).
  - Section 14: moves between windows report one `TAB_MOVED` per tab and keep sessions (Task 3); the GUI check
    "parent-tab drags between windows" goes to the user (Task 4).
  - Plan 7a's hand-offs and plan 7b's "Left for plan 7c" (Hand-offs absorbed).
- **Replay.** Every quoted anchor was matched at its stated count, in a replay of plan 7a's Tasks 1-6, plan 7b's Tasks
  1-6 and then this plan's Tasks 1-4, in order, on a `git archive` copy of `8e129ff34`. Nothing was compiled or run.
- **Type consistency.** These keep one name and one signature from the task that creates them to the last that uses
  them: `WorkspaceDropResolver.place(Point, Rectangle, List<Rectangle>)` and `marker(Rectangle, List<Rectangle>, int,
  int)`; `ParentTabs.move(ParentTabId, int)`; `ParentTabsView.reorder(ParentTabId, int)`;
  `WindowContent.reorderParentTab(ParentTabId, int)` and `manager()`; `TabDrag.Target.Bar(WindowContent, int)`,
  `pressed(ParentTabBar, Tab, MouseEvent)`, `draggedParentTab()` and `TabDrag(Supplier<List<WindowContent>>)`;
  `TabDragTestSupport.onParentTab`, `barEntry`, `startParentTab` and `dropParentTab`.
- **No motion.** The bar's entries jump: `ParentTabBar.animates()` is false (plan 7a), a reorder re-slots `WindowTabs`
  at once, and the reorder test runs with the motion on.
