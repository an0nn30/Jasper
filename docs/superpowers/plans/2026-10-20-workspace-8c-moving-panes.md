# Workspace tabs 8c: moving panes Implementation Plan

**Status: executed on 2026-10-20** (`107b7eb55..5ec032b5b`); see `docs/STATUS.md`. Written against
`claude/workspace-tabs` at `c1540b4e9` ("Docs: workspace tabs plan 8a, tabs hold panes"; its code is `2d7233474`'s) with
plan 8a's committed text and then plan 8b's text applied on top (see Workspace). Record every deviation from this text
here and in `docs/STATUS.md`. Amendment 1 (2026-10-20, from plan 8b's final review, I2; the controller's ruling): a pane
moving between tabs of its window is announced once, where it lands, and one that fails to land goes back where it was
(Rulings 17; Tasks 2 and 3). Its case for plan 8b's Move Pane to New Tab is Task 2's, with the hold, because that
command announces its old tab's next pane before this plan: that RED exists before Task 3. Deviations from the plan's
text: (1) amendment 1, above; no other: Tasks 1-3 ran their text verbatim. Known: a Cmd+Option-click (Ctrl+Alt-click on
Linux and Windows) starts the pane gesture, so it no longer opens a link or reaches a program that reads the mouse, and
Cmd-click, Ctrl-click and Alt-drag keep their meanings; on Windows only the left Alt counts, since Java reports the
right one as AltGraph; a move to another window that fails lands its item in a new tab of its old window (the registry's
recovery), and nothing closes.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Holding Cmd+Option (Ctrl+Alt on Linux and Windows) and dragging inside a pane moves the pane, the iTerm way:
beside another pane of any window, on the side nearest the cursor; over the middle of a tab's entry, which shows that
tab for the drop; onto an entry's outer quarter, or a strip's end or its "+", where it becomes a new tab at that place;
and nowhere outside the windows. The gesture never reaches the pane's item: a terminal neither reports it to its program
nor selects, and the Editor neither selects nor drags its text.

**Architecture:** The tab drag (`TabDrag`, plans 5 and 7c) gains a second kind: a window's `paneClicks` hands it a
modifier press on an item of its shown tab, consumed, and a gesture listener the drag adds consumes that press's drags
and release before any listener of the item hears them; `TerminalView` leaves a consumed mouse event alone. The drag
keeps its threshold, label, overlay, Esc, lost-release, focus-loss and window reach, and finds a pane's targets with the
reshaped `WorkspaceDropResolver` (a pane's nearest side, and an entry's outer quarters) and the strips' new
`TabStrip.newTabPlace(Point)`. A text view's own text drag sits out the gesture. A drop within its window is a layout
change (`ParentTabsView.moveBeside`, `moveToNewTab(Tab, int)`); into another window it goes through the window manager
(`movePane`, `movePaneToNewTab`) and the tab registry, one `TAB_MOVED`, the session kept.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md`](../specs/2026-10-20-jasper-tabs-and-panes-design.md)
(decisions 2.6 and 2.12, sections 4, 5's motion, 7, 13's maximize and hover rows, 14's third plan and 16). It builds on
[plan 8a](2026-10-20-workspace-8a-tabs-hold-panes.md)'s text and on plan 8b's (`2026-10-20-workspace-8b-split-with.md`),
which execute first. Plans 6a and 6b are re-planned after this one (`2026-10-18-workspace-6a-save-restore.md`,
`2026-10-18-workspace-6b-plugin-restorers.md`).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`. Every anchor below was matched at its stated count, and every file creation and edit script of
Tasks 1-4 replayed in order with no failure, on a `git archive` copy of `0a6c6a18c`, where plans 8a (with its final fix
wave, `d45b8171d`) and 8b (with its final fix wave) are executed. On that copy JBR 25's `javac` compiled the main and
test sources of `jasper-sdk`, `jasper-ui`, `jasper-buddy`, `jasper-terminal` and `jasper-app`, with the testkit and the
test fixtures they use; `-Xlint:all` warned of nothing in the files this plan touches, and doclint at the Javadoc task's
level found nothing new. A plain JUnit launcher then ran, headless (`-Djava.awt.headless=true`, no Gradle, no window),
`PaneDragTest` (27), `TabDragWindowsTest` (30), `MovePaneToNewTabTest` (7), `ParentTabsViewTest` (14),
`WorkspaceViewTest` (9), `WindowTabMotionTest` (13), `TabDragTest` (16), `WindowTabsTest` (10), `ClassicTabsTest` (10),
`SplitListsTest` (6), `WorkspaceDropResolverTest` (10) and `TerminalViewInteractionTest` (44 of 45; an existing case's
off-macOS assumption aborts it on a Mac), with no failure. Earlier rounds replayed the plan on `c1540b4e9` after plan
8a's and plan 8b's texts, and on `a49e33f69`, `45658712e` and `512c48ac4`; since `ee3cd8989` Task 1 quotes 8a's final
fix wave's `paneClicks`, which the first three lack. Line numbers are `0a6c6a18c`'s. Anchors in text an earlier plan
writes quote it as that plan leaves it. Plan 8a's execution words three things otherwise than its text:
`TabRegistry.move(Tab, UUID, GroupId, Side)`'s Javadoc, `WorkspaceDropResolver`'s class Javadoc and a pattern variable
in `TabDrag.showLanding`; Task 2's spans and its whole-file replacement take either text.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan wrote, and quote it as they leave it.

**Hand-offs absorbed:**
- Plan 8a's "Left for 8c": the tab drag's machinery is reused whole (press, threshold, Esc, lost release, focus loss,
  label, overlay, window reach); `WorkspaceDropResolver`'s `Area`, `Drop`, `resolve` and `landing` take the pane drag's
  shape, and `Drop.Strip` goes with `Drop.Centre` (Rulings 13); `TabRegistry.move(Tab, UUID, GroupId, Side)` gets its
  production caller (`WindowManager.movePane`), and `WorkspaceView.moveToNewGroup` with `Layout.moveToNewGroup` theirs
  (`ParentTabsView.moveBeside`); `WindowContent.paneClicks` carries the pane press.
- Plan 8b's "Left for plan 8c": `ParentTabsView.moveToNewTab(Tab)` gains an index (`moveToNewTab(Tab, int)`), which the
  drop on a new-tab place uses; `ContentTab.split` and `moveToNewTab` need no SDK member for the drag, so the SDK, the
  testkit and the contract do not change.
- Plan 8b's final review (I2), left for this plan: Move Pane to New Tab announces its old tab's next pane on the way (a
  stray `TAB_SELECTED`, two `ACTIVE_PANE_CHANGED`, `onActive` true then false, a focus request), and the testkit's fake
  announces nothing; Rulings 17 holds the window's announcements for that command and for every pane drop between its
  tabs (amendment 1).
- Plan 7c's controller rulings for the tab drag (the hidden strip's band, the empty workspace) stand; a pane uses
  neither the band nor the tear-out.

**6a impact:** the file format, `WorkspaceFile`, the snapshot and the store are untouched. A pane move is a layout
change, which reaches `workspaceChanged` and the store's save as any layout change does; a move between tabs reaches it
once where it lands (Rulings 17), and a drop once more as `selectTab` selects the moved item; hovering a tab and showing
the earlier one again go through `ParentTabsView.select`, then `workspaceChanged` and `layoutChanged`, so each asks the
store for a save, as Select Tab does.

## Spec issues

1. **Spec 7's "a strip's empty end, or its '+'" has nothing to match in the standard look's shown strip.** The equal
   tabs share the strip's whole width and have no "+" (plan 8a Rulings 11; Verified facts 7), so with two or more tabs
   they show neither. **Ruled** (the controller, 2026-10-20): in either look an entry's outer quarters are new-tab
   places, the leading quarter the place before it and the trailing quarter the place after it; the classic tabs' "+"
   and the room after them are the end (from the "+" on in a crowded strip); an entry's middle half shows its tab, and a
   hidden strip has none (Rulings 3). Why: the places between entries are iTerm2's insertion points and the tab drag's
   own marker. Cost if wrong: a pane released near an entry's edge becomes a new tab instead of nothing.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `WorkspaceDropResolver.Drop.Strip`, `Drop.Centre`, `Area`'s strip, tabs and `splits` go outright,
  with no alias. No shim, no migration, no "this was removed" text.
- The modifier (spec 7): Cmd+Option on macOS, Ctrl+Alt elsewhere. It is not a keymap action and has no setting (spec 2.12
  binds commands; spec 15's out-of-scope list adds none).
- Motion (AGENTS.md; spec 5): a pane's landing shows and goes at once; a tab a pane drop makes takes its place at once
  (Rulings 9); hovering shows a tab at once (Rulings 4). Nothing animates.
- Sessions and content are never touched: an item's component moves between panes, tabs and windows; every move
  between windows goes through the tab registry, one `TAB_MOVED` per item (spec 7; plan 7b Rulings 6); moves within a
  window never reach the registry (spec 7, as plan 7a's tab moves).
- The SDK stays 0.21.0 and does not change; nor do the testkit, the contract cases or any plugin.
- `jasper-terminal` changes inside its supported `view` package only, with no public API change (AGENTS.md: no JediTerm
  type in a public signature, `verifyTerminalArchitecture`).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): none changes. `dock` stays
  JDK-only but `Rectangle` and `Point`, naming `layout` alone.
- The theme-migration ledger `docs/design/theme-engine/migration.csv`: a new file matching `ThemeMigrationCoverageTest`'s
  pattern gets `ledger.add(...)` with "New in workspace-tabs plan 8c Task N" (plan 4a Rulings 21).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment with its `@param` and `@return`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- User-facing text says "tab" and "pane" (spec 3).
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): views driven directly, gestures on synthetic events over plan 5's
  stand-in frames on a pretend screen (`TabDragTestSupport`, `TabDragWindowsTest`), the terminal view through its
  package-private `handleMouse`.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan8c/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan8c
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan8c/
ls build/plan8c/edits.py build/plan8c/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan8c")`. Java text inside a script is in triple-quoted strings and holds no
backslash; a `\n` in a one-line Python string is a newline. A file a task replaces whole is written from the plan's
code block.

## Review Focus

1. **A mouse-reporting program in the terminal (tmux, vim with `mouse=a`) while the user Cmd+Option-drags, presses Esc
   mid-drag and keeps dragging before the release.** No press, drag or release reaches the program, and no selection
   starts. Task 1: `TerminalViewInteractionTest.aGestureAnotherListenerConsumedNeitherSelectsNorReachesTheProgram` and
   `PaneDragTest.everyEventOfAGestureIsThePaneDragsWhateverEndsTheDrag`.
2. **A Cmd+Option-drag that starts over selected text in an editor pane, also in Vim's Normal or Visual mode.** The
   text keeps its selection and caret, and no text drag starts: the text view's drag is off for the gesture and back
   after it, its release or Esc's (a real text drag needs a display: a GUI check). Task 1:
   `PaneDragTest.aTextViewsSelectionStaysThroughAPaneGesture`, `aTextViewsTextDragSitsOutAPaneGesture` and
   `aTextViewsTextDragComesBackAtTheReleaseAfterEsc`.
3. **Hovering another tab of the drag's own window, then Esc or a release over nothing.** The tab that showed before
   shows again, its item focused. Task 2: `PaneDragTest.aDragEndingElsewhereShowsTheTabThatShowedBefore`.
4. **A pane dropped into a tab whose pane is maximized** (hovered first, so only the maximized pane shows). The
   maximize ends and the pane splits beside the maximized one (spec 13). Task 2:
   `PaneDragTest.aDropIntoAMaximizedTabEndsItsMaximize`.
5. **A window's last pane dragged away.** A full window closes once the move has reported itself (`TAB_MOVED` before
   `WINDOW_CLOSED`); the main window keeps its empty workspace under the same id, which no other window shares, and that
   empty workspace takes a pane anywhere over it. Task 2:
   `TabDragWindowsTest.aFullWindowsLastPaneLeavingClosesItAfterItsMove` and
   `theMainWindowsOnlyPaneLeavingLeavesItsEmptyWorkspace`; Task 3:
   `TabDragWindowsTest.theMainWindowsEmptyWorkspaceTakesAPaneAnywhereOverIt`.
6. **The selected item's pane moved between tabs of its window: by Move Pane to New Tab, beside another tab's pane, on a
   new-tab place, and with a landing that throws.** The registry hears no `TAB_SELECTED`, `TAB_MOVED`, `TAB_CLOSED` or
   `ACTIVE_PANE_CHANGED`, the items it leaves hear no activation change, and a failed landing leaves the window's tabs
   as they were, the item in its pane. Task 2: `PaneDragTest.aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands`,
   `movePaneToNewTabIsAnnouncedOnceWhereThePaneLands`, `aPaneThatFailsToLandGoesBackWhereItWas`,
   `aTabsLastPaneThatFailsToLandKeepsItsTab`, `aNewTabThatFailsOnceItStandsGoesAgain` and
   `hoveringATabsEntryShowsItAndAPaneOfItTakesTheDrop` (a drag's drop announces the moved item once); Task 3:
   `PaneDragTest.aPaneDroppedOnANewTabPlaceIsAnnouncedOnceWhereItLands`.

## Verified facts

From `0a6c6a18c`, where plans 8a and 8b are executed (see Workspace).

1. **The tab drag** (`workspace/TabDrag.java`): `pressed(ParentTabBar, Tab, MouseEvent)` (`:147-164`) records a press;
   `dragged` (`:170-189`) starts the drag past `TAB_REORDER_THRESHOLD` once `inPlace` (`:241-244`) holds; `released`
   (`:195-212`) updates and carries the target out through `drop` (`:423-433`); `start` (`:247-263`) adds the Esc
   dispatcher and the lost-release AWT listener (mouse and motion) and ends the drag when the item closes, changes or
   gives way; `update` (`:266-289`) finds the window under the cursor (`windowAt` over `onScreen`), then `onBar`
   (`:297-314`) or a tear-out; `showLanding` (`:356-381`) puts `DockDropOverlay` on the target's layered pane;
   `finish` (`:384-415`) takes it all away. The window manager's one drag is shared by its windows
   (`WindowManager.java:99`, `:511`); a window has its own until then (`WindowContent.java:142`); a window losing the
   focus or closing ends a drag from it (`:2146-2147`, `:2174`).
2. **The resolver** (`dock/WorkspaceDropResolver.java`): `Area(group, strip, tabs, content, splits)` (`:33-54`), `Drop`
   with `Strip`, `Centre` and `Edge` (`:57-104`), `resolve` (`:119-128`: outer quarters split, the middle is the
   centre), `landing` (`:182-196`), and the tab drag's `place` (`:139-142`), `marker` (`:209-219`) and `windowAt`
   (`:230-233`). After plan 8a only `TabDrag` (`place`, `marker`, `windowAt`) and the tests call it.
3. **A press on a pane.** `WindowContent.paneClicks` (`:76-82`), an AWT listener for mouse events each window adds
   (after `addHost`) and removes (in `close`), makes the pane of the shown tab that a left press lands on active
   (`tabHolding(under(source, point))`: plan 8a's final fix wave finds the item by the press's location, since AWT hands
   a press on a view's bare area to an ancestor; `selectTab`). Every window's listener hears every event; only the
   window holding the component under the press acts.
4. **AWT delivery** (JBR 25 sources, `java.desktop`). `Component.dispatchEventImpl` hands an event to the AWT listeners
   (step 2, `java/awt/Component.java:4911-4914`) before input methods (step 4, `:4932-4957`) and the component's own
   listeners (step 6, `:5000`); step 4 returns for an input event already consumed when the component has input methods
   enabled (the default with key events, `:621`, `:5167-5174`; every `JComponent` enables key events,
   `javax/swing/JComponent.java:655`) and an input context (a window's, `java/awt/Window.java:1404-1411`). So in a
   window, a mouse event an AWT listener consumes reaches no listener of a `JComponent` that keeps input methods on;
   one that turns them off hears it, consumed, as the Editor's text area does in Vim's Normal and Visual modes
   (`VimEditor.java:897`, `area.enableInputMethods(mode.inserting())`); headless, with no window, every component hears
   it consumed. While a button is held Swing sends a press's drags and release to the pressed component
   (`java/awt/Container.java:4518-4527`, `:4540-4581`), shown or hidden: `retargetMouseEvent` (`:4849-4877`) needs it
   only in the window's tree.
5. **The terminal's mouse.** `TerminalView` hands every press, release, drag, move and wheel event to
   `MouseController.handle` (`jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java:139-167`,
   `MouseController.java:63`), which never asks `isConsumed()`: a press with the link modifier (Cmd, Ctrl elsewhere)
   opens a link or starts a selection, Alt starts a block selection, and while the program reports the mouse a press,
   its drags and its release are reported; a drag or release with no press of its own is reported too
   (`MouseController.java:103-104`). Nothing consumes a terminal's mouse event before it today: the app's AWT listeners
   (`paneClicks`, `ToolWindowArea`'s press listener, the tab drag's lost release, the Vault's activity clock) never
   consume one, and Swing's `PopupMenu.consumeEventOnClose` stays `false` (`BasicLookAndFeel.java:1158`; neither FlatLaf
   3.7 nor Jasper sets it), so a consumed check changes nothing else.
6. **The Editor's mouse.** RSyntaxTextArea 3.6.0's caret leaves a consumed press, drag and release alone
   (`ConfigurableCaret.java:333-337`, `:385-402`; JDK `javax/swing/text/DefaultCaret.java:543`, `:597`, `:636`), but
   `BasicTextUI.DragListener` (`javax/swing/plaf/basic/BasicTextUI.java:2268-2303`), which starts a text drag over a
   selection, never asks, and `RTextArea` enables that drag outside headless (`RTextArea.java:912`) and turns it off and
   on again itself around its triple-click (`RTextArea.java:1063`, `:1069`). Fact 4 keeps a consumed press from it in a
   window, but not in Vim's Normal and Visual modes, which turn input methods off: there the drag itself must sit out
   (Rulings 16).
7. **Strips.** The equal tabs (`WindowTabs`) share the strip's whole width among their entries (`doLayout`
   `:145-163`) and have no "+" (plan 8a Rulings 11); the classic tabs (`ClassicTabs`) are content-sized, with the "+"
   after the last entry, or at the trailing edge over the entries a crowded strip clips (`doLayout` `:106-124`).
   `TabStrip` (`:16`, sealed: the two) gives each entry's rectangle (`tabBounds`, `:28`).
8. **Moves.** `WorkspaceView.moveToNewGroup(Tab, GroupId, Side)` (`:112-115`, over `Layout.moveToNewGroup`
   `:285-291`) moves a pane within its tab; `openBeside` (`:84-87`) splits a pane, which ends a maximize
   (`Layout.split` `:267-273`, `activated` `:391-393`); `ParentTabsView.remove(Tab)` (`:196-210`) takes a tab with its
   last pane, except the window's only tab; plan 8b's `ParentTabsView.moveToNewTab(Tab)` (`:162-168`) gives a pane a
   tab right after its own, and `WindowContent.mayMoveToNewTab` (`:1324-1327`) refuses a tab's only pane, a pinned item
   and one being closed. `TabRegistry.move(Tab, UUID)` (`:444`) attaches an item in a new tab (plan 8a Rulings 3) and
   `move(Tab, UUID, GroupId, Side)` (`:456-460`) beside a pane, both through `transfer`: one `TAB_MOVED` where it
   arrives, the window it left told after (a full window it emptied closes), then `select`, which shows and raises it.
9. **Motion.** `WindowTabs.refresh` (`:54-107`) opens an arriving entry with the motion unless its item is `moving()`
   (a move between windows) or `ParentTabBar.animates()` (`:97`) is false; a move within a window is not `moving()`.
10. **The ledger.** `ThemeMigrationCoverageTest.CANDIDATE` matches an `import java.awt` or `javax.swing`, so the new
    `PaneDragTest` needs a row.
11. **Field initializers.** `WindowContent.paneClicks` (`:76`) is a lambda in a field initializer, declared before the
    blank finals `macOs` (`:107`, assigned at `:333`) and `dockDrag` (`:136`, at `:361`) and the field `tabDrag`
    (`:142`). Definite assignment forbids it to read a blank final it precedes, even as `this.macOs` (JBR 25's `javac`:
    "variable macOs might not have been initialized"), so it reads `macOs()`, `dockDrag()` and `tabDrag()`, methods that
    run once the constructor is done. `TabDrag.gestureEvents` is a method reference (`this::gestureEvent`, like
    `lostRelease`); `hovered` and `textDrag` read nothing. No other field initializer this plan adds reads a field.
12. **A move between tabs of one window announces its steps.** Plan 8b's `ParentTabsView.moveToNewTab(Tab)` (`:162-168`)
    runs `from.remove(tab)`, then `openParentTab`. Each step reaches `WindowContent.workspaceChanged` (`:1255-1266`),
    through `WorkspaceView.apply` (`:152-162`) or `openParentTab`'s own call: it asks for a save,
    `WindowTerminals.tabSelected` (`:107-113`) publishes a changed selection, `update` (`:1877-1894`) sets each item's
    `active` (`Tab.active`, `:146-150`, tells its `onActive` listeners) and asks the registry to `refresh` its active
    pane, and a refocus focuses the shown tab's item. So a move from the shown tab first announces the pane that the old
    tab selects next, then the moved item again. `TerminalRegistry.publish` (`:53-58`) refreshes after each event unless
    `atomically` (`:61-66`) holds it, and `WindowTerminals.atomically` (`:79`) runs a change at once once the window has
    closed. `ParentTabsView.remove(Tab)` (`:191-210`) takes a tab with its last item and never takes that item out of
    the tab's workspace; `WorkspaceView.remove` (`:94-100`) and `place` (`:89-92`) change its items and layout and
    render them. `WorkspaceView.remove` of a tab's last item empties its layout, which `ParentTabsView.changed` hands to
    `ParentTabs.replace`, whose constructor (`ParentTabs.java:46-51`) refuses an empty parent tab beside others and a
    tab in two parent tabs.

## Rulings

Record them in `docs/STATUS.md` (Task 4). "What — why".

1. **The modifier is exactly Cmd+Option on macOS and Ctrl+Alt elsewhere, with the left button** (`TabDrag.movesPane`):
   Shift, AltGr elsewhere (which Windows reports with Ctrl and Alt) or the other platform's modifier added make it none;
   on macOS either Option key counts, the right one, which macOS reports as AltGraph, too (the controller's ruling); the
   window's keymap platform (`WindowContent.macOs()`) decides, as for every shortcut. Reason: spec 7 names the keys; the
   extra keys stay the item's (a terminal's Shift-drag selects past a mouse-reporting program), and macOS has no AltGr
   gesture to protect. Cost if wrong: none.
2. **A pane's gesture is the pane drag's from its press to its left button's release**, whether the drag runs, ends
   (Esc, a lost release, focus loss, the item closing) or never starts: the window consumes the modifier press and a
   click with the modifier, and the drag's gesture listener the press's drags and release, before the item's listeners;
   `TerminalView` leaves a consumed mouse event alone. A new left press, another press away from the gesture or a plain
   move means its release went elsewhere: the gesture ends, with the press or drag it began, and that event is the
   item's. Reason: spec 7 keeps the plain drag the item's, so the modifier drag must be none of it; a release after Esc
   would otherwise reach a mouse-reporting program (Verified facts 5).
3. **New-tab places** (`TabStrip.newTabPlace`, over `WorkspaceDropResolver.newTabPlace`; the controller's ruling): in
   either look an entry's leading quarter is the place before it and its trailing quarter the place after it; the
   classic tabs' "+" and the room after their last entry are the end, from the "+" on in a crowded strip; an entry's
   middle half shows its tab (Rulings 4), and a hidden strip has none. The landing marks the place as the tab drag marks
   a tab's. Reason: spec 7 names a strip's end or "+", and the equal tabs have neither; the places between entries are
   iTerm2's insertion points and the tab drag's own marker. Cost if wrong: a pane released near an entry's edge becomes
   a new tab instead of nothing.
4. **Hovering an entry's middle half shows its tab at once.** In the drag's own window its active item takes the focus,
   as Select Tab gives it; in another window the focus stays put, since a focus request there would raise that window
   and take the focus from the drag's own, which ends the drag. A release on an entry's middle half drops nothing.
   Reason: Jasper's changes take effect at once and spec 7 names no delay; cost if wrong: a dwell timer in
   `TabDrag.hover`, as tab bars that spring a tab open during a drag wait about half a second first.
5. **The tabs a drag showed show again when it ends**, in every window but the one its drop lands in. Reason: spec 13.
6. **A pane drops beside the pane under the cursor on the side whose edge is nearest**, each distance a share of that
   pane's width or height (the four triangles between its diagonals), a tie going left, right, up, then down as plan
   5's; there is no centre. Reason: spec 7; a pane holds one item, so nothing drops into one.
7. **"Where it already is"** is the dragged pane's own pane, anywhere over it, and its own tab's entry's middle half,
   which only shows that tab. A drop on a neighbour's facing side moves the pane and evens the split's weights. Reason:
   spec 7; cost if wrong: comparing the arrangement before and after.
8. **A tab's only pane dropped in its own window, at any new-tab place, is no target** (the controller's ruling): no
   landing, and the release drops nothing; in another window it is one, its tab going with it. Reason: the tab drag
   already reorders tabs, and one rule ("a tab's only pane takes no new tab in its own window") matches Move Pane to New
   Tab (plan 8b Rulings 9). Cost if wrong: a user expecting iTerm2's reorder drags the tab instead.
9. **Plan 8b's Move Pane to New Tab, a command, keeps the strip's open motion** (the controller's ruling); **a tab a
   pane drop makes lands at once** (`ParentTabBar.atOnce`), as one moving in from another window does. Reason: spec
   5's "a dragged tab takes its place at once, and nothing else animates"; plan 8a Rulings 20 lands a move at once, and
   a command opens a tab as New Tab does. Cost if wrong: one `atOnce` call in Move Pane to New Tab.
10. **The main window's empty workspace takes a pane anywhere over it**, as the tab in its place. Reason: it is the main
    window's one tab and holds no pane; as it takes a tab (plan 7c's final fix wave) and as anything opening there
    does (plan 8a Rulings 3).
11. **The drag of an item that is pinned or being closed never starts**: no label, no landing, its gesture still the
    drag's. Reason: spec 7's "does not move"; Move Pane to New Tab is disabled for it too.
12. **Routing.** Within its window a drop is a layout change (`WindowContent.movePane` over
    `ParentTabsView.moveBeside`; `movePaneToNewTab` over `moveToNewTab(Tab, int)`), with no `TAB_MOVED`; into another
    window it goes through the drag's window manager (`movePane`, `movePaneToNewTab`) and the tab registry's `move`,
    which shows the item there and raises that window. A window no manager opened moves nothing out of itself. The
    moved item takes the focus where it lands. Reason: spec 7; plan 8a's hand-off.
13. **The resolver keeps the pane drag's shape:** `Area(group, bounds)`, `Drop(group, side)`, `resolve` gives the
    nearest side and `landing` that half; `Drop.Strip`, `Drop.Centre`, `Area`'s strip, tabs and `splits` go. Reason:
    no pane has a strip (plan 8a leaves `Drop.Strip` to this plan), and nothing drops into a pane.
14. **A modifier press makes its pane the active one**, as any press does, and a drag that ends without a drop leaves it
    so. Reason: plan 8a Rulings 21.
15. **No action is added.** The modifier is a gesture, Esc ends it as it ends the tab drag, and Move Pane to New Tab is
    the keyboard's way to a new tab. Reason: spec 2.12 binds commands; the drag is none.
16. **A text view sits out its gesture's text drag** (the controller's ruling): from the gesture's press to its end the
    pressed text component, or its nearest text ancestor, has its drag off, and the gesture's end turns it on again.
    Reason: the Editor's Vim Normal and Visual modes turn input methods off, so a consumed press still reaches the text
    area, whose Swing text drag never asks (Verified facts 4 and 6); RTextArea already toggles `setDragEnabled` itself.
    Cost if wrong: none; the text drag returns at the release.
17. **A pane moving between tabs of its window is announced once, where it lands** (`WindowContent.held`; the
    controller's ruling, amendment 1). While `ParentTabsView.moveBeside` into another tab, or `moveToNewTab` (plan 8b's
    command and Task 3's drop), runs, `workspaceChanged` lets the parent tabs' model take each layout and holds the
    rest, keeping any focus request; at the end one `workspaceChanged` of the shown tab runs inside
    `terminals.atomically`. Move Pane to New Tab keeps its item selected, so a plugin hears nothing; a drop beside
    another tab's pane follows the hover that showed that tab (Rulings 4) and announces the moved item once. A tab's
    last pane takes its tab out of the strip's model before it leaves, as no tab beside others may be empty
    (`ParentTabs`), and its workspace goes once the pane has landed (`discard`). A move that throws restores both tabs'
    layouts (`WorkspaceView.restore`), and a new tab that stood goes again: the item is back in its pane, nothing
    closed, and the failure propagates, an announcement's failure suppressed under it. Reason: the selection does not
    change, so a plugin should hear nothing, as plan 8a's `selectTab` fix established and the testkit's fake already
    does; the move's steps announced the old tab's next pane (Verified facts 12). Cost if wrong: a listener that relied
    on hearing that pane would miss it; none does.

## File structure

- `jasper-terminal`, `dev.jasper.terminal.view`: `MouseController` (Task 1).
- `dev.jasper.app.dock`: `WorkspaceDropResolver` (Task 2, replaced; Task 3), `package-info` (Task 2).
- `dev.jasper.app.contributions`: `TabRegistry` (Task 2, Javadoc).
- `dev.jasper.app.workspace`: `TabDrag` (Tasks 1-3), `WindowContent` (Tasks 1-3), `ParentTabsView` (Tasks 2-3),
  `WorkspaceView` (Task 2), `WindowManager` (Tasks 2-3), `ParentTabBar`, `TabStrip`, `WindowTabs`, `ClassicTabs` (Task
  3).
- Tests: `TerminalViewInteractionTest` (Task 1); new `PaneDragTest` and `TabDragTestSupport` (Tasks 1-3);
  `WorkspaceDropResolverTest` (Task 2, replaced); `TabDragWindowsTest` (Tasks 2-3); `WindowTabsTest`,
  `ClassicTabsTest`, `WindowTabMotionTest` (Task 3).
- Documents (Task 4): `README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/terminal-architecture.md`, `docs/STATUS.md`, this plan's status banner.

## Before you start

Plans 8a and 8b are executed first; plan 8b's last commit is "Tabs and panes: split with a choice, documentation and
status" (or its final fix wave).

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
grep -c 'private final AWTEventListener paneClicks = event -> {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'void moveToNewTab(Tab tab) { if (mayMoveToNewTab(tab)) parentTabs.moveToNewTab(tab); }' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'record TearOut(Rectangle bounds, Point cursor) implements Target { }' jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java  # 1
grep -c 'record Strip(GroupId group, int index) implements Drop {' jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java  # 1
grep -c 'public void move(Tab tab, UUID window, GroupId group, Side side) {' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
grep -c '    void handle(MouseEvent e) {' jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseController.java  # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above plan 8b's. Create the two helper scripts (Global Constraints).

---

### Task 1: The pane gesture: a modifier press is the pane drag's

Spec 7's start and ends: a left press with the modifier inside a pane of the shown tab, past plan 5's threshold, starts
a pane drag, which shows the item's label, owns Esc and ends on Esc, a lost release, focus loss, the item closing or
leaving its tab, and never starts for a pinned item or one being closed (Rulings 11). Every event of the gesture is the
drag's and none reaches the item (Rulings 2): `TerminalView` leaves a consumed mouse event alone, and the window and the
drag consume the gesture before any listener of the item hears it; a text view sits out its text drag for the gesture
(Rulings 16), and a new left press ends a gesture whose release went elsewhere. The drag has no target yet; Tasks 2 and 3
add them.

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseController.java` (`handle`, `:63`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (class Javadoc `:39-63`, `LABEL_OFFSET`
  `:86`, fields after `:116`, `draggedParentTab` `:133`, after `pressed` `:164`, `dragged` `:176-177`, after
  `lostRelease` `:235`, `inPlace` `:237-244`, `update` `:275-277`, `finish` `:393-394`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`paneClicks` `:72-82`, the `tabDrag`
  field's comment `:137-141`, after `lookAnimates` `:1054`, `tabDrag()` `:1498`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/PaneDragTest.java` (and its ledger row)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java` (class Javadoc, the end)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java`

**Interfaces:**
- Consumes: plan 8a's `WindowContent.paneClicks`, `tabHolding(Component)`, `selectTab(Tab)`, `parentTabOf(Tab)`,
  `tabRegistry()`; plan 5's `TabDrag.dragged(MouseEvent)`, `released(MouseEvent)`, `cancel()`.
- Produces:
  - `static boolean TabDrag.movesPane(MouseEvent event, boolean macOs)`: the left button with exactly the pane drag's
    modifier;
  - `void TabDrag.pressedPane(WindowContent window, Tab item, MouseEvent event)`: a pane's gesture begins;
  - `boolean TabDrag.movingPane()`: a running drag carries a pane;
  - `boolean WindowContent.macOs()`: whether the window's keymap is macOS's, which `paneClicks` reads;
  - `TabDrag`'s private `pane` flag, `gesture` state and `update`'s pane branch, which Tasks 2 and 3 fill;
  - test helpers `TabDragTestSupport.PANE`, `mouse(Component, int, Point, int)` → `MouseEvent`, `inPane(WindowContent,
    Tab)`, `startPane(WindowContent, Tab, Point...)` → `Component`, `dropPane(WindowContent, Tab, Point...)`;
  - `PaneDragTest` with its fixture (`owner`, `a`, `b`, `c`, `d`, `first`, `second`, `view`, `shape`, `moves`), which
    Tasks 2 and 3 extend.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PaneDragTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 7: the pane drag within one window, on synthetic events over a stand-in frame on a pretend screen.
 * The main window holds two tabs of plugin views: the first, showing, "a" beside "b" above "c" (H[a, V[b, c]]), the
 * second "d"; its strip shows, as it holds two tabs. No window manager opened it, so its drag is its own.
 */
class PaneDragTest {
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
    private Tab a, b, c, d;
    private ParentTabId first, second;

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
            c = view("c", TabRegistry.Placement.SPLIT_DOWN);
            d = view("d", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(d).orElseThrow();
            owner.selectParentTab(first);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A gesture or a drag that outlived its test would keep its listeners into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no gesture or lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        closeOwners();
    }

    /** A plugin's view titled {@code title} in {@code owner}, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) { return view(title, new JPanel(), placement); }

    private Tab view(String title, JComponent component, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), component, placement);
    }

    /** The arrangement of {@code window}'s shown tab: each pane by its item's title, each split as H[...] or V[...]. */
    static String shape(WindowContent window) { return shape(window.workspace(), window.workspace().model().root()); }

    private static String shape(WorkspaceView workspace, Node node) {
        return switch (node) {
            case Group group -> group.selected().flatMap(workspace::tab).map(Tab::title).orElse("-");
            case Split split -> (split.orientation() == Orientation.HORIZONTAL ? "H" : "V")
                + split.children().stream().map(child -> shape(workspace, child.node())).toList();
        };
    }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    private boolean esc() {
        return keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
    }

    /** A left press on {@code source} with {@code held} keys, not dispatched. */
    private static MouseEvent press(Component source, int held) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, held | InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
            MouseEvent.BUTTON1);
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 2 and 14: a press with the pane drag's modifier inside a pane is
     * the pane drag's, consumed before the item hears it, and makes the pane the active one; within plan 5's threshold
     * nothing runs, past it the drag runs with the item's label and owns Esc.
     */
    @Test void aModifierPressInAPaneIsThePaneDragsAndPastTheThresholdItRuns() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = inPane(owner, b);
            Point press = middle(source);
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE).isConsumed()).as("the press").isTrue();
            assertThat(owner.selectedTab()).as("its pane is the active one, as any press makes it").isSameAs(b);
            assertThat(motionListeners()).as("the gesture listens").isEqualTo(listeners + 1);
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).as("4.2 px is within the threshold").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).isTrue();
            assertThat(drag.movingPane()).as("a pane, not a tab").isTrue();
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(drag.label().showing()).isTrue();
            assertThat(keys.dispatchers).as("it owns Esc while it runs").hasSize(1);
            assertThat(motionListeners()).as("the gesture's and the lost release's listeners").isEqualTo(listeners + 2);
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x, press.y + 12), PANE).isConsumed()).isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(shape(owner)).as("released over its own pane: nothing moves").isEqualTo("H[a, V[b, c]]");
            assertThat(motionListeners()).as("the release ended the gesture").isEqualTo(listeners);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 1: without exactly the modifier a press and its drag are the
     * item's own, Shift added or another button too; a click with the modifier is the pane drag's.
     */
    @Test void withoutExactlyTheModifierAPressIsTheItemsOwn() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = inPane(owner, b);
            Point press = middle(source), below = new Point(press.x, press.y + 12);
            for (int held : new int[] {0, PANE | InputEvent.SHIFT_DOWN_MASK}) {
                assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, held).isConsumed()).as("keys %d", held).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, below, held).isConsumed()).isFalse();
                assertThat(drag.active()).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, below, held).isConsumed()).isFalse();
            }
            var right = new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, PANE | InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, false,
                MouseEvent.BUTTON3);
            source.dispatchEvent(right);
            assertThat(right.isConsumed()).as("another button").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_CLICKED, press, PANE).isConsumed()).as("a click with the modifier").isTrue();
            assertThat(motionListeners()).as("no gesture began").isEqualTo(listeners);
        });
    }

    /**
     * Workspace plan 8c Rulings 1: Cmd+Option on macOS (either Option key), Ctrl+Alt elsewhere, with the left button and
     * no other key.
     */
    @Test void theModifierIsCmdOptionOnMacOsAndCtrlAltElsewhere() {
        int mac = InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK, other = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        var panel = new JPanel();
        assertThat(TabDrag.movesPane(press(panel, mac), true)).isTrue();
        assertThat(TabDrag.movesPane(press(panel, other), false)).isTrue();
        assertThat(TabDrag.movesPane(press(panel, other), true)).as("Ctrl+Option on macOS").isFalse();
        assertThat(TabDrag.movesPane(press(panel, mac), false)).as("Meta+Alt elsewhere").isFalse();
        assertThat(TabDrag.movesPane(press(panel, mac | InputEvent.SHIFT_DOWN_MASK), true)).as("Shift too").isFalse();
        assertThat(TabDrag.movesPane(press(panel, other | InputEvent.ALT_GRAPH_DOWN_MASK), false)).as("AltGr").isFalse();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK), false))
            .as("Ctrl and AltGr elsewhere").isFalse();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK), true))
            .as("Cmd and the right Option key on macOS (Rulings 1)").isTrue();
        assertThat(TabDrag.movesPane(press(panel, InputEvent.ALT_DOWN_MASK), false)).as("Alt alone").isFalse();
        var right = new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, other | InputEvent.BUTTON3_DOWN_MASK, 1, 1, 1, false,
            MouseEvent.BUTTON3);
        assertThat(TabDrag.movesPane(right, false)).as("another button").isFalse();
    }

    /**
     * Workspace plan 8c Rulings 2 and Review Focus 1: every event of a pane's gesture, to its release, is the pane drag's,
     * whatever ends the drag: here Esc, after which the rest of the gesture still reaches no listener of the item unconsumed.
     */
    @Test void everyEventOfAGestureIsThePaneDragsWhateverEndsTheDrag() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            var listener = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { heard.add(event.isConsumed()); }
                @Override public void mouseReleased(MouseEvent event) { heard.add(event.isConsumed()); }
                @Override public void mouseDragged(MouseEvent event) { heard.add(event.isConsumed()); }
            };
            Component source = inPane(owner, a);
            source.addMouseListener(listener);
            source.addMouseMotionListener(listener);
            TabDrag drag = owner.tabDrag();
            Point low = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            startPane(owner, a, low);
            assertThat(drag.active()).isTrue();
            assertThat(esc()).as("Esc").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(keys.dispatchers).isEmpty();
            Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, over, 0);
            mouse(source, MouseEvent.MOUSE_RELEASED, over, 0);
            assertThat(heard).as("the press, two drags, a drag after Esc and the release: each consumed first")
                .hasSize(5).containsOnly(true);
            assertThat(motionListeners()).as("the release ended the gesture").isEqualTo(listeners);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, middle(source), 0).isConsumed()).as("the next plain press").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, middle(source), 0);
        });
    }

    /** Tabs and panes spec 7: a lost release and the window losing the focus end the pane drag, as they end a tab drag. */
    @Test void aLostReleaseAndTheWindowLosingTheFocusEndThePaneDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
            startPane(owner, a, over);
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).as("a plain move: the release never came").isFalse();
            assertThat(motionListeners()).as("and the gesture is over").isEqualTo(listeners);
            Component source = startPane(owner, a, over);
            owner.setActive(false);
            assertThat(drag.active()).as("a modal dialog or another application took the focus").isFalse();
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE).isConsumed()).as("its release is still the drag's").isTrue();
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /** Tabs and panes spec 7: the pane drag ends when its item closes, or leaves its tab meanwhile. */
    @Test void thePaneDragEndsWhenItsItemClosesOrLeavesItsTab() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Point over = inside(pane(owner, a.group().orElseThrow()), 0.5, 0.8);
            Component source = startPane(owner, c, over);
            model.tabs().remove(c);
            assertThat(drag.active()).as("its item closed").isFalse();
            relayout(frame);
            mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE);
            source = startPane(owner, b, over);
            owner.moveToNewTab(b);
            mouse(source, MouseEvent.MOUSE_DRAGGED, over, PANE);
            assertThat(drag.active()).as("its item left its tab").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE);
            assertThat(owner.parentTabCount()).as("Move Pane to New Tab moved it, the drag nothing").isEqualTo(3);
        });
    }

    /** Tabs and panes spec 7 and workspace plan 8c Rulings 11: a pinned item's pane drag never starts; its gesture is still the drag's. */
    @Test void aPinnedItemsPaneDragNeverStarts() throws Exception {
        edt(() -> {
            var pin = a.pin();
            try {
                Point over = inside(pane(owner, c.group().orElseThrow()), 0.5, 0.5);
                Component source = startPane(owner, a, over);
                assertThat(owner.tabDrag().active()).isFalse();
                assertThat(owner.tabDrag().label().showing()).isFalse();
                assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, over, PANE).isConsumed()).isTrue();
                assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
            } finally {
                pin.close();
            }
        });
    }

    /** Review Focus 2: a text view's selection and caret stay as they were through a pane gesture over its selection. */
    @Test void aTextViewsSelectionStaysThroughAPaneGesture() throws Exception {
        edt(() -> {
            var text = new JTextArea("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            text.select(4, 7);
            assertThat(inPane(owner, notes)).isSameAs(text);
            Point over = middle(text), past = new Point(over.x + 40, over.y);
            Component source = startPane(owner, notes, past);
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getSelectionStart()).isEqualTo(4);
            assertThat(text.getSelectionEnd()).isEqualTo(7);
        });
    }

    /** A text view that drags its text, as the Editor's does outside headless: the drag is counted here, not refused. */
    private static final class DraggingText extends JTextArea {
        boolean drag = true;
        DraggingText(String text) { super(text); }
        @Override public boolean getDragEnabled() { return drag; }
        @Override public void setDragEnabled(boolean value) { drag = value; }
    }

    /** Review Focus 2 and workspace plan 8c Rulings 16: a text view's text drag sits out a pane gesture and comes back after its release. */
    @Test void aTextViewsTextDragSitsOutAPaneGesture() throws Exception {
        edt(() -> {
            var text = new DraggingText("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            Point past = new Point(middle(text).x + 40, middle(text).y);
            Component source = startPane(owner, notes, past);
            assertThat(text.getDragEnabled()).as("during the gesture").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getDragEnabled()).as("after its release").isTrue();
        });
    }

    /** Workspace plan 8c Rulings 2 and 16: after Esc the gesture lasts to its release, and so does the text drag's rest. */
    @Test void aTextViewsTextDragComesBackAtTheReleaseAfterEsc() throws Exception {
        edt(() -> {
            var text = new DraggingText("one two three");
            Tab notes = view("notes", text, TabRegistry.Placement.SPLIT_RIGHT);
            relayout(frame);
            Point past = new Point(middle(text).x + 40, middle(text).y);
            Component source = startPane(owner, notes, past);
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(esc()).isTrue();
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(text.getDragEnabled()).as("Esc ends the drag, not the gesture").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, past, PANE);
            assertThat(text.getDragEnabled()).as("the release ends the gesture").isTrue();
        });
    }

    /**
     * Workspace plan 8c Rulings 2: a gesture whose release went elsewhere ends at the next left press, which is the item's
     * own, and so does the press it began: a plain drag after it drags no pane.
     */
    @Test void aLeftPressAfterAGestureWhoseReleaseWasLostIsTheItemsOwn() throws Exception {
        edt(() -> {
            Component source = inPane(owner, a);
            Point press = middle(source), below = new Point(press.x, press.y + 12);
            mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE);
            assertThat(motionListeners()).as("the gesture listens").isEqualTo(listeners + 1);
            // No release came. The next left press, a plain one on the same view, is not the gesture's.
            assertThat(mouse(source, MouseEvent.MOUSE_PRESSED, press, 0).isConsumed()).isFalse();
            assertThat(motionListeners()).as("the gesture is over").isEqualTo(listeners);
            assertThat(mouse(source, MouseEvent.MOUSE_DRAGGED, below, 0).isConsumed()).isFalse();
            assertThat(owner.tabDrag().active()).as("its press ended with it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, below, 0);
        });
    }

    /** Review Focus 1: a terminal's view is a pane like any other: its gesture is the pane drag's and selects nothing. */
    @Test void aTerminalsGestureStartsThePaneDragAndNoSelection() throws Exception {
        WindowContent[] shell = new WindowContent[1];
        JPanel[] shown = new JPanel[1];
        edt(() -> {
            shell[0] = content(idleLauncher(pending));
            shown[0] = frame(shell[0], 100, 900, 800, 500);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        edt(() -> {
            relayout(shown[0]);
            Tab terminal = shell[0].selectedTab();
            var view = ((TerminalContent) terminal.content()).pane().view();
            assertThat(inPane(shell[0], terminal)).as("its view takes the press").isSameAs(view);
            Point from = middle(view), to = new Point(from.x + 80, from.y + 40);
            Component source = startPane(shell[0], terminal, to);
            assertThat(shell[0].tabDrag().movingPane()).isTrue();
            assertThat(mouse(source, MouseEvent.MOUSE_RELEASED, to, PANE).isConsumed()).isTrue();
            assertThat(view.hasSelection()).as("no selection").isFalse();
        });
    }
}
```

Then edit the shared test support, the terminal's test and the ledger:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
import ledger
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(W + "TabDragTestSupport.java", [
    (""" * strip, which shows the item of the tab's active pane: "the entry of an item" is that entry.
 */
""", """ * strip, which shows the item of the tab's active pane: "the entry of an item" is that entry. A pane drags from a press
 * with the pane drag's modifier ({@link #PANE}) inside it (tabs and panes spec 7).
 */
""", 1),
    ("""    static void broadcast(MouseEvent event) {
        for (var listener : Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK)) listener.eventDispatched(event);
    }
}
""", """    static void broadcast(MouseEvent event) {
        for (var listener : Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK)) listener.eventDispatched(event);
    }

    /** The pane drag's modifier on this host (tabs and panes spec 7): Cmd+Option on macOS, Ctrl+Alt elsewhere. */
    static final int PANE = System.getProperty("os.name").startsWith("Mac")
        ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    /**
     * A left-button event at {@code screen} with {@code keys} held, sent to {@code source} as Swing sends a gesture's events
     * to the pressed component: the button is down but on its release and its click.
     *
     * @return the event, which a listener may have consumed
     */
    static MouseEvent mouse(Component source, int id, Point screen, int keys) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        boolean up = id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        var event = new MouseEvent(source, id, System.currentTimeMillis(), keys | (up ? 0 : InputEvent.BUTTON1_DOWN_MASK), at.x,
            at.y, 1, false, button);
        source.dispatchEvent(event);
        return event;
    }

    /** The component a press in the middle of {@code item}'s pane reaches: the deepest one there. */
    static Component inPane(WindowContent window, Tab item) {
        GroupView pane = window.parentTabs().holding(item).orElseThrow().groupView(item.group().orElseThrow());
        return SwingUtilities.getDeepestComponentAt(pane, pane.getWidth() / 2, pane.getHeight() / 2);
    }

    /**
     * Presses the left button with the pane drag's modifier in the middle of {@code item}'s pane, drags 12 px down (past
     * the 5 px threshold at any scale up to 2) and on to each point, without releasing.
     *
     * @return the pressed component, which takes the gesture's later events
     */
    static Component startPane(WindowContent window, Tab item, Point... path) {
        Component source = inPane(window, item);
        Point press = middle(source);
        mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE);
        mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12), PANE);
        for (Point point : path) mouse(source, MouseEvent.MOUSE_DRAGGED, point, PANE);
        return source;
    }

    /** As {@link #startPane}, then releases at the last point. */
    static void dropPane(WindowContent window, Tab item, Point... path) {
        mouse(startPane(window, item, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1], PANE);
    }
}
""", 1)])
T = "jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java"
edit(T, [
    ("""    @Test
    void shiftDraggingSelectsEvenWhenTheProgramWantsTheMouse() throws Exception {
""", """    /** A gesture an earlier listener consumed, as the application's pane drag does, is not the terminal's. */
    @Test
    void aGestureAnotherListenerConsumedNeitherSelectsNorReachesTheProgram() throws Exception {
        show("hello world", 0, "hello world");
        consumed(press(0, 0, 1, 0), drag(4, 0), release(4, 0));
        assertThat(view.hasSelection()).as("no selection").isFalse();

        enableSgrMouse();
        consumed(press(1, 1, 1, 0), drag(3, 1), release(3, 1));
        assertThat(connector.written()).as("no report reaches the program").isEmpty();
    }

    @Test
    void shiftDraggingSelectsEvenWhenTheProgramWantsTheMouse() throws Exception {
""", 1),
    ("""    private MouseEvent press(int column, int row, int clicks, int modifiers) {
""", """    /** Hands {@code events} to the view consumed, as an earlier listener leaves them. */
    private void consumed(MouseEvent... events) {
        for (MouseEvent event : events) {
            event.consume();
            view.handleMouse(event);
        }
    }

    private MouseEvent drag(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, x(column), y(row), 1, false,
            MouseEvent.NOBUTTON);
    }

    private MouseEvent press(int column, int row, int clicks, int modifiers) {
""", 1)])
ledger.add(W + "PaneDragTest.java", "app.workspace", "PaneDragTest", W + "PaneDragTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8c Task 1; the pane drag within one window")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.view.TerminalViewInteractionTest' :jasper-app:test --tests 'dev.jasper.app.workspace.PaneDragTest' --continue`
Expected: FAIL: the terminal's case selects and reports (no consumed check); `PaneDragTest` does not compile
(`TabDrag.movesPane`, `movingPane` do not exist).

- [ ] **Step 3: Implement**

The terminal view leaves a consumed event alone; the window hands the drag a modifier press, consumed, and the drag
keeps the gesture's later events from the item:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit, span
edit("jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseController.java", [(
    """    void handle(MouseEvent e) {
        Type type = typeOf(e);
""", """    void handle(MouseEvent e) {
        // A gesture an earlier listener took, such as the application's pane drag, is not the terminal's: no report, no
        // selection.
        if (e.isConsumed()) return;
        Type type = typeOf(e);
""", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java"
edit(P, [
    ("import java.awt.event.AWTEventListener;\nimport java.awt.event.KeyEvent;\n",
     "import java.awt.event.AWTEventListener;\nimport java.awt.event.InputEvent;\nimport java.awt.event.KeyEvent;\n", 1),
    ("import javax.swing.SwingUtilities;\n", "import javax.swing.SwingUtilities;\nimport javax.swing.text.JTextComponent;\n", 1),
    ("""    static final int LABEL_OFFSET = 8;
""", """    static final int LABEL_OFFSET = 8;

    /** The keys a mouse event may hold besides its buttons: a pane's press holds its modifier of them and no other. */
    private static final int KEYS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
        | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
""", 1),
    ("""    private Optional<Target> target = Optional.empty();
""", """    private Optional<Target> target = Optional.empty();
    /** Whether the pending press or the drag carries an item's pane rather than a whole tab (tabs and panes spec 7). */
    private boolean pane;
    /**
     * While a pane's gesture lasts, from its press to its left button's release: the component the press reached, whose
     * events are the pane drag's, and the press itself, which the gesture's listener does not take for a new one.
     */
    private Component gesture;
    private MouseEvent gesturePress;
    private final AWTEventListener gestureEvents = this::gestureEvent;
    /** The text view whose text drag sits out the gesture, while it does (workspace plan 8c Rulings 16). */
    private JTextComponent textDrag;
""", 1),
    ("""    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }
""", """    ParentTabId draggedParentTab() { return dragging ? parentTab : null; }

    /** @return whether a running drag carries a pane (tabs and panes spec 7) */
    boolean movingPane() { return dragging && pane; }
""", 1),
    ("""        grab = new Point(inStrip.x - places.get(index).x, inStrip.y - places.get(index).y);
        size = view.get().getSize();
    }
""", """        grab = new Point(inStrip.x - places.get(index).x, inStrip.y - places.get(index).y);
        size = view.get().getSize();
    }

    /**
     * Whether {@code event} carries the pane drag's modifier (tabs and panes spec 7; workspace plan 8c Rulings 1): the left
     * button with Cmd+Option on macOS, either Option key, and Ctrl+Alt elsewhere, and no other key; Shift, and elsewhere
     * AltGr, which Windows reports with Ctrl and Alt, make it none.
     *
     * @param event a mouse event
     * @param macOs whether the window's keymap is macOS's
     * @return whether a press with it begins a pane's gesture
     */
    static boolean movesPane(MouseEvent event, boolean macOs) {
        int modifier = macOs ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            : InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        int keys = event.getModifiersEx() & KEYS;
        // macOS reports the right Option key as AltGraph; it has no AltGr of its own, so either Option counts.
        if (macOs && (keys & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) keys = keys & ~InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        return SwingUtilities.isLeftMouseButton(event) && keys == modifier;
    }

    /**
     * A left press with the pane drag's modifier on {@code item}, an item of {@code window}'s shown tab, which the window
     * consumes (tabs and panes spec 7): the press's gesture is the pane drag's from now to its release (workspace plan 8c
     * Rulings 2), and a drag of the item's pane may start from it. A press while another is pending or a drag runs ends
     * that one first.
     */
    void pressedPane(WindowContent window, Tab item, MouseEvent event) {
        if (this.tab != null) cancel();
        Optional<ParentTabId> id = window.parentTabOf(item);
        if (id.isEmpty()) return;
        beginGesture(event);
        from = window;
        parentTab = id.get();
        tab = item;
        pane = true;
        source = event.getComponent();
        pressedAt = screen(event);
    }
""", 1),
    ("""            if (at.distance(pressedAt) <= NativeStyles.installed().metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
            if (!inPlace()) {
""", """            if (at.distance(pressedAt) <= NativeStyles.installed().metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
            // A pinned item, or one being closed, does not move: its pane's drag never starts (workspace plan 8c Rulings 11).
            if (!inPlace() || pane && !from.tabRegistry().movable(tab)) {
""", 1),
    ("""    private void lostRelease(AWTEvent event) {
        if (dragging && (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_PRESSED)) cancel();
    }
""", """    private void lostRelease(AWTEvent event) {
        if (dragging && (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_PRESSED)) cancel();
    }

    /** A pane's gesture begins with {@code press}: its later events come to {@link #gestureEvent} until its release. */
    private void beginGesture(MouseEvent press) {
        endGesture();
        gesture = press.getComponent();
        gesturePress = press;
        // A text view with its input methods off (the Editor's Vim Normal and Visual modes) still hears a consumed press,
        // and Swing's text drag never asks: it sits out the gesture, as RTextArea's own triple-click handling does.
        JTextComponent text = gesture instanceof JTextComponent own ? own
            : (JTextComponent) SwingUtilities.getAncestorOfClass(JTextComponent.class, gesture);
        if (text != null && text.getDragEnabled()) {
            text.setDragEnabled(false);
            textDrag = text;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(gestureEvents, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    /** The pane's gesture is over: a text view's text drag comes back, and its listener goes. */
    private void endGesture() {
        if (gesture == null) return;
        gesture = null;
        gesturePress = null;
        if (textDrag != null) {
            textDrag.setDragEnabled(true);
            textDrag = null;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(gestureEvents);
    }

    /** The gesture's release never came: the gesture is over, and so is the press or the drag it began. */
    private void lost() {
        endGesture();
        if (pane) cancel();
    }

    /**
     * An event while a pane's gesture lasts, before any component hears it (workspace plan 8c Rulings 2): the pressed
     * component's events while the left button is held, and that button's release, are consumed, a drag followed and the
     * release ending the gesture and carrying the drag out; a new left press, another press away from it, or a plain move
     * means that release never came: the gesture ends, with the press or the drag it began, and that event is not its.
     */
    private void gestureEvent(AWTEvent event) {
        if (gesture == null || event == gesturePress || !(event instanceof MouseEvent mouse)) return;
        // A new left press means this gesture's release went elsewhere: the gesture is over and the press is not its.
        if (mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getButton() == MouseEvent.BUTTON1) {
            lost();
            return;
        }
        boolean release = mouse.getID() == MouseEvent.MOUSE_RELEASED && mouse.getButton() == MouseEvent.BUTTON1;
        if (mouse.getComponent() == gesture && (release || (mouse.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0)) {
            mouse.consume();
            if (mouse.getID() == MouseEvent.MOUSE_DRAGGED) dragged(mouse);
            if (release) {
                endGesture();
                released(mouse);
            }
        } else if (mouse.getID() == MouseEvent.MOUSE_PRESSED || mouse.getID() == MouseEvent.MOUSE_MOVED) lost();
    }
""", 1),
    ("""    /**
     * Whether the entry is still where the press found it: its item open and in the press's window, and still the item
     * the press's tab shows.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        return from.parentTabs().view(parentTab).map(WorkspaceView::selected).filter(tab::equals).isPresent();
    }
""", """    /**
     * Whether what the press found is still in place: its item open and in the press's window, and for a tab drag still
     * the item the press's tab shows, for a pane drag still in the press's tab.
     */
    private boolean inPlace() {
        if (tab == null || tab.closed() || !Objects.equals(tab.window(), from.id())) return false;
        Optional<WorkspaceView> view = from.parentTabs().view(parentTab);
        if (pane) return view.filter(held -> held.contains(tab)).isPresent();
        return view.map(WorkspaceView::selected).filter(tab::equals).isPresent();
    }
""", 1),
    ("""            open.stream().map(window -> onScreen(window, window == from, home)).toList());
        // What the registry keeps in its window stays there:""", """            open.stream().map(window -> onScreen(window, window == from, home)).toList());
        if (pane) {
            // A pane takes targets in a window alone: it never becomes a window (tabs and panes spec 7).
            showLanding();
            return;
        }
        // What the registry keeps in its window stays there:""", 1),
    ("""        size = null;
        target = Optional.empty();
        if (closing != null) {
""", """        size = null;
        target = Optional.empty();
        pane = false;
        if (closing != null) {
""", 1)])
span(P, "/**\n * The tab drag (workspace tabs spec 12; tabs and panes spec 6). A left press on a tab's entry in its window's strip,\n",
    " * only.\n */\n", """/**
 * The tab drag and the pane drag (workspace tabs spec 12; tabs and panes spec 6 and 7). A left press on a tab's entry in
 * its window's strip, the {@link ParentTabBar}, that moves past the kit's tab drag threshold
 * ({@code TAB_REORDER_THRESHOLD}, 5 px by default) starts a tab drag, carrying the whole tab with every pane of it. A
 * left press with the pane drag's modifier ({@link #movesPane}: Cmd+Option on macOS, Ctrl+Alt elsewhere) on an item of a
 * window's shown tab, which that window hands over ({@link #pressedPane}), starts a pane drag past the same threshold,
 * carrying the item's pane. Every event of a pane's gesture, from its press to its left button's release, is the pane
 * drag's, whether the drag runs, has ended or never starts: the window consumes the press, and this drag the press's
 * drags and its release, before any listener of the item hears them, so a terminal neither reports them to its program
 * nor selects and a text view neither moves its caret nor drags its text. Both drags use plain mouse events in screen
 * coordinates, never AWT drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor
 * goes, and synthetic events drive them in tests. While a drag runs, {@link TabDragLabel} shows the entry's or the item's
 * label beside the cursor and the target window's layered pane shows where it would land ({@link DockDropOverlay});
 * nothing moves until the release.
 * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen on the display of
 * the drag's own window but that window), the most recently active. For a tab, over its strip, the place before the
 * first of the strip's other entries whose middle is not left of the cursor, where the entries take their new places at
 * once on the release; a window whose strip hides (the standard look's one tab) takes it at its end over the band its
 * strip takes when it shows, and the main window's empty workspace, which has no panes, anywhere over it, in its place.
 * Anywhere else in that window (a pane, its dock, chrome or title bar), and over any other window of Jasper's (Settings,
 * About, a dialog), nothing; outside every window, a new full window the size of the tab's workspace, with the entry
 * where the cursor holds it and kept on the display under the cursor, except for a window's only tab (a full window's
 * already is its own; workspace plan 8a Rulings 6). A tab holding an item that is pinned (a modal owner holds it in its
 * window), or one being closed, takes targets along its own strip only.
 * <p>A pane takes targets in windows alone, as a pane never becomes a window (tabs and panes spec 7); the drag of an item
 * that is pinned or being closed never starts.
 * <p>Releasing the left button carries the target out: a tab along its own strip as a reorder, into another window or
 * out of every window through the window manager of the drag's window, which moves the tab's items through the tab
 * registry; none touches an item's content or session, and a window no manager opened moves nothing out of itself.
 * Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's window losing
 * the focus (a modal dialog, another application), and the dragged item closing or leaving its tab, or, for a tab drag,
 * the item its entry shows giving way to another of its panes end the drag without a drop. EDT only.
 */
""")
C = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(C, [
    ("""    /**
     * A left press on a pane's item makes its pane the active one, a view that takes no focus too (tabs and panes spec 5;
     * workspace plan 8a Rulings 21).
     */
    private final AWTEventListener paneClicks = event -> {
        if (event.getID() != MouseEvent.MOUSE_PRESSED || closed() || !(event instanceof MouseEvent press)
            || !SwingUtilities.isLeftMouseButton(press) || !(press.getSource() instanceof Component source)) return;
        Tab tab = tabHolding(under(source, press.getPoint()));
        if (tab != null && tab != selectedTab()
            && parentTabs().holding(tab).filter(view -> view == parentTabs().active()).isPresent()) selectTab(tab);
    };
""", """    /**
     * A left press on a pane's item makes its pane the active one, a view that takes no focus too (tabs and panes spec 5;
     * workspace plan 8a Rulings 21). With the pane drag's modifier ({@link TabDrag#movesPane}) the press and its click are
     * the pane drag's (spec 7; workspace plan 8c Rulings 2): consumed before the item hears them, the press handing the
     * drag its gesture.
     */
    private final AWTEventListener paneClicks = event -> {
        if (closed() || !(event instanceof MouseEvent press) || !SwingUtilities.isLeftMouseButton(press)
            || !(press.getSource() instanceof Component source)) return;
        int id = press.getID();
        if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_CLICKED) return;
        Tab tab = tabHolding(under(source, press.getPoint()));
        if (tab == null || parentTabs().holding(tab).filter(view -> view == parentTabs().active()).isEmpty()) return;
        if (TabDrag.movesPane(press, macOs())) {
            press.consume();
            if (id == MouseEvent.MOUSE_PRESSED) {
                // A dock drag still running lost its release: it ends first, as a press on a strip's entry ends it.
                dockDrag().cancel();
                tabDrag().pressedPane(this, tab, press);
            }
        }
        if (id == MouseEvent.MOUSE_PRESSED && tab != selectedTab()) selectTab(tab);
    };
""", 1),
    ("""     * Drags a parent tab from the bar (tabs and panes spec 6): the window manager's, which every window it opens shares;
     * until it gives that, one of this window's own, which reorders the bar and moves nothing out of this window, as a
     * tear-out needs a manager ({@link #mayMoveTabToNewWindow}).
""", """     * Drags a tab from the strip, or a pane by its modifier (tabs and panes spec 6 and 7): the window manager's, which
     * every window it opens shares; until it gives that, one of this window's own, which moves tabs and panes within this
     * window and nothing out of it, as a move to another window needs a manager ({@link #mayMoveTabToNewWindow}).
""", 1),
    ("""    /** The tab drag this window's strips start. */
""", """    /** The drag this window's strip starts with a tab and its panes with a modifier press. */
""", 1),
    # paneClicks is a field initializer above the blank final macOs: it reads it through a method, run after construction.
    ("""    boolean lookAnimates() { return themes.animationsEnabled(); }
""", """    boolean lookAnimates() { return themes.animationsEnabled(); }
    /** Whether the keymap and shortcut text are macOS's: the pane drag's modifier follows it (workspace plan 8c Rulings 1). */
    boolean macOs() { return macOs; }
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS: the new cases, `TabDragTest` and `TabDragWindowsTest` unchanged (a tab drag never sets `pane`),
`PaneDimTest.aPressOnAPaneMakesItTheActivePane` (a plain press still activates its pane) and the rest. Then
`./gradlew :jasper-terminal:check :jasper-app:check`: PASS (`verifyTerminalArchitecture`, the ledger test sees
`PaneDragTest`'s row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-terminal/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: a modifier press inside a pane is the pane drag's

Cmd+Option (Ctrl+Alt elsewhere) and a left press inside a pane of the
shown tab hand the tab drag a pane's gesture: past the threshold the
pane drag runs with the item's label, and Esc, a lost release, focus
loss or the item closing end it. Every event of the gesture is
consumed before the item hears it, and the terminal view leaves a
consumed mouse event alone, so no report or selection follows; a
text view's text drag sits out the gesture. A pinned item's drag
never starts. No target yet.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: A pane beside another pane, in any window

Spec 7's first two targets and spec 13's hover row: a pane drops beside another pane of the shown tab, in its own
window or another, on the side the cursor is nearest (Rulings 6), the landing showing that half; a tab's entry shows
its tab at once so a pane of it can take the drop (Rulings 4), and the tabs a drag showed show again when it ends
elsewhere (Rulings 5). Within its window the move is a layout change (its tab goes with its last pane); into another
window it goes through the window manager and the tab registry (Rulings 12). The resolver takes the pane drag's shape
(Rulings 13). A pane moving between tabs of its window, by this drop or by plan 8b's Move Pane to New Tab, is announced
once, where it lands, and one that fails to land goes back where it was (Rulings 17).

**Files:**
- Replace: `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java`
- Replace: `jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (imports; Task 1's class Javadoc paragraphs;
  `Target` `:65-83`; Task 1's fields; Task 1's `update` branch; after `barArea` `:320-324`; `released` `:205-210`;
  `showLanding` `:351-381`; `finish` `:383-415`; `drop` `:417-433`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java` (imports `:6`; plan 8b's
  `moveToNewTab` `:157-168`; `remove` `:191-210`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (fields after `landing` `:99`;
  `workspaceChanged` `:1250-1266`; after `layoutChanged` `:1269`; after plan 8b's `moveToNewTab` `:1336`; `update`
  `:1877-1879`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` (after `remove` `:94-100`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java` (imports `:6-9`; after
  `moveParentTab` `:322-353`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java` (`move`'s Javadoc `:447-448`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/package-info.java` (`:3-4`)
- Test: `PaneDragTest` (Task 1's), `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java` (class
  Javadoc `:44-50`, imports, the end)

**Interfaces:**
- Consumes: Task 1's `TabDrag.pressedPane`, `movingPane()`, its `pane` flag and `update`'s pane branch, and
  `TabDragTestSupport.PANE`, `mouse(Component, int, Point, int)`, `startPane`, `dropPane`; plan 8a's
  `WorkspaceView.moveToNewGroup(Tab, GroupId, Side)`, `openBeside`, `groupViews()`, `ParentTabsView.remove(Tab)`,
  `select(ParentTabId)`, `holding(GroupId)`, `WindowContent.selectParentTab(ParentTabId)`, `activeParentTab()`,
  `selectTab(Tab)`, `workspaceChanged(WorkspaceView, boolean)`, `WindowTerminals.atomically(Runnable)`; plan 8b's
  `ParentTabsView.moveToNewTab(Tab)` and `ActionId.MOVE_PANE_TO_NEW_TAB`; plan 7b's `TabRegistry.move(Tab, UUID,
  GroupId, Side)`.
- Produces:
  - `WorkspaceDropResolver.Area(GroupId group, Rectangle bounds)`, `Drop(GroupId group, Side side)`,
    `resolve(Point, List<Area>)` → `Optional<Drop>`, `landing(Drop, List<Area>)` → `Rectangle`; `place`, `marker` and
    `windowAt` as before;
  - `TabDrag.Target.Beside(WindowContent window, GroupId group, Side side)`;
  - `ParentTabsView.moveBeside(Tab tab, GroupId group, Side side)`;
  - `WindowContent.movePane(Tab tab, GroupId group, Side side)`;
  - `void WindowContent.held(Runnable move)`: a pane move between the window's tabs, announced once, where it ends;
  - `void WorkspaceView.restore(Tab tab, Layout before)`: a failed move's way back;
  - `ParentTabsView`'s private `drop(WorkspaceView)`, `discard(WorkspaceView, boolean)` and `undo(Throwable, Tab,
    WorkspaceView, Layout, WorkspaceView, Layout)`, and plan 8b's `moveToNewTab(Tab)` held, which Task 3's
    `moveToNewTab(Tab, int)` takes over;
  - `PaneDragTest.shellBesideC`, `announced` and `Refusing`, and the cases
    `aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands`, `movePaneToNewTabIsAnnouncedOnceWhereThePaneLands`,
    `aPaneThatFailsToLandGoesBackWhereItWas`, `aTabsLastPaneThatFailsToLandKeepsItsTab` and
    `aNewTabThatFailsOnceItStandsGoesAgain`;
  - `WindowManager.movePane(WindowContent from, Tab tab, WindowContent to, GroupId group, Side side)` → `boolean`, and
    its private `mayMovePane(WindowContent, Tab, WindowContent)`;
  - `TabDrag`'s private `onPanes(WindowContent, Point)`, `hover`, `restore`, `finish(WindowContent)`, `window(Target)`
    and `besideLanding`, which Task 3 extends.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java` with:

```java
package dev.jasper.app.dock;

import dev.jasper.app.dock.WorkspaceDropResolver.Area;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tabs and panes spec 6 and 7, workspace tabs spec 12 and 17: where a dragged pane or tab drops, from the cursor alone,
 * and which of overlapping windows takes it.
 */
class WorkspaceDropResolverTest {
    private static final GroupId LEFT = GroupId.random(), RIGHT = GroupId.random();
    /** Two panes side by side, 6 px apart. */
    private static final List<Area> TWO = List.of(new Area(LEFT, new Rectangle(0, 0, 400, 600)),
        new Area(RIGHT, new Rectangle(406, 0, 394, 600)));
    /** One pane 100 x 80 from 0, 10, for pixel-exact boundaries: the shares are dx / 100 and dy / 80. */
    private static final List<Area> SMALL = List.of(new Area(LEFT, new Rectangle(0, 10, 100, 80)));

    private static Drop at(List<Area> areas, int x, int y) {
        return WorkspaceDropResolver.resolve(new Point(x, y), areas).orElseThrow();
    }

    /** Workspace plan 8c Rulings 6: beside the pane under the cursor, on the nearer side; a pane has no centre. */
    @Test void aPaneGoesBesideThePaneUnderTheCursorOnTheNearestSide() {
        assertThat(at(TWO, 40, 300)).isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(TWO, 360, 300)).isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(TWO, 200, 60)).isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(TWO, 200, 560)).isEqualTo(new Drop(LEFT, Side.DOWN));
        assertThat(at(TWO, 420, 300)).as("the right pane's own left").isEqualTo(new Drop(RIGHT, Side.LEFT));
        assertThat(at(TWO, 190, 300)).as("near the middle: still the nearer side").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(TWO, 210, 300)).isEqualTo(new Drop(LEFT, Side.RIGHT));
    }

    @Test void theSidesAreTheTrianglesBetweenThePanesDiagonalsPixelExact() {
        assertThat(at(SMALL, 0, 50)).as("the left edge is inside").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 50, 10)).as("the top edge is inside").isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 99, 50)).as("the last column").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 89)).as("the last row").isEqualTo(new Drop(LEFT, Side.DOWN));
        assertThat(at(SMALL, 20, 30)).as("a fifth of the width against a quarter of the height").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 30, 30)).as("three tenths against a quarter").isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 50, 20)).isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 80, 60)).isEqualTo(new Drop(LEFT, Side.RIGHT));
    }

    @Test void aTieGoesLeftThenRightThenUpThenDown() {
        assertThat(at(SMALL, 25, 30)).as("as near the left as the top").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 75, 30)).as("as near the right as the top").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 75, 70)).as("as near the right as the bottom").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 50)).as("the middle, as near every side").isEqualTo(new Drop(LEFT, Side.LEFT));
    }

    @Test void nothingBetweenOrOutsideThePanes() {
        assertThat(WorkspaceDropResolver.resolve(new Point(403, 300), TWO)).as("the divider").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(900, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(-1, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(100, 50), SMALL)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 90), SMALL)).as("nor the bottom edge").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 50), List.of())).as("no pane").isEmpty();
    }

    @Test void theLandingIsThePanesHalfOnItsSide() {
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.LEFT), TWO)).isEqualTo(new Rectangle(0, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.RIGHT), TWO)).isEqualTo(new Rectangle(200, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.UP), TWO)).isEqualTo(new Rectangle(0, 0, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.DOWN), TWO)).isEqualTo(new Rectangle(0, 300, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop(RIGHT, Side.RIGHT), TWO)).as("an odd width: the new pane's half")
            .isEqualTo(new Rectangle(603, 0, 197, 600));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.landing(new Drop(GroupId.random(), Side.LEFT), TWO));
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 2: a tab's place in a strip follows the strip rule. */
    @Test void aTabGoesBeforeTheFirstOtherEntryOfAStripWhoseMiddleIsNotLeftOfTheCursor() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThat(WorkspaceDropResolver.place(new Point(10, 10), bar, entries)).hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(0, 0), bar, entries)).as("the strip's left and top edges are inside")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(100, 10), bar, entries)).as("on a middle: still before that entry")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(101, 10), bar, entries)).hasValue(1);
        assertThat(WorkspaceDropResolver.place(new Point(599, 27), bar, entries)).as("the strip's last pixel").hasValue(2);
        assertThat(WorkspaceDropResolver.place(new Point(300, 5), bar, List.of())).as("a strip with no other entry").hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(300, 28), bar, entries)).as("just under the strip").isEmpty();
        assertThat(WorkspaceDropResolver.place(new Point(-1, 10), bar, entries)).isEmpty();
        assertThat(WorkspaceDropResolver.marker(bar, entries, 1, 2)).as("between the two entries")
            .isEqualTo(new Rectangle(299, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, entries, 2, 2)).as("after the last, kept inside the strip")
            .isEqualTo(new Rectangle(598, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, List.of(), 0, 2)).as("no other entry: the strip's start")
            .isEqualTo(new Rectangle(0, 0, 2, 28));
    }

    /** A marker's place is 0 to the number of entries; any other is refused. */
    @Test void aMarkerOutsideItsEntriesIsRefused() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, entries, -1, 2))
            .withMessage("Index -1 is outside 0 to 2");
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, entries, 3, 2))
            .withMessage("Index 3 is outside 0 to 2");
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, List.of(), 1, 2))
            .withMessage("Index 1 is outside 0 to 0");
    }

    @Test void whereWindowsOverlapTheFirstListedTakesTheDrop() {
        Rectangle older = new Rectangle(0, 0, 800, 600), newer = new Rectangle(400, 300, 800, 600);
        Point both = new Point(500, 400);
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(newer, older))).as("the most recently active, listed first")
            .isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(older, newer))).isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(new Point(100, 100), List.of(newer, older))).isEqualTo(OptionalInt.of(1));
        assertThat(WorkspaceDropResolver.windowAt(new Point(1100, 800), List.of(newer, older))).isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(new Point(2000, 2000), List.of(newer, older))).as("outside every window").isEmpty();
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(new Rectangle(), older))).as("a window that does not show")
            .isEqualTo(OptionalInt.of(1));
    }

    @Test void anAreaKeepsItsOwnRectangleAndADropNeedsBoth() {
        Rectangle bounds = new Rectangle(0, 0, 100, 80);
        var area = new Area(LEFT, bounds);
        bounds.width = 1;
        area.bounds().width = 2;
        assertThat(area.bounds()).isEqualTo(new Rectangle(0, 0, 100, 80));
        assertThatNullPointerException().isThrownBy(() -> new Drop(LEFT, null));
        assertThatNullPointerException().isThrownBy(() -> new Area(null, bounds));
    }
}
```

Then add the drag's cases to `PaneDragTest` and `TabDragWindowsTest`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(W + "PaneDragTest.java", [
    ("import static org.assertj.core.api.Assertions.assertThat;\n",
     "import static org.assertj.core.api.Assertions.assertThat;\nimport static org.assertj.core.api.Assertions.assertThatThrownBy;\n", 1),
    ("import dev.jasper.app.config.AnimationSettings;\n",
     "import dev.jasper.app.commands.ActionId;\nimport dev.jasper.app.config.AnimationSettings;\n", 1),
    ("import dev.jasper.app.layout.ParentTabId;\n", "import dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.Side;\n", 1),
    ("import java.awt.Point;\n", "import java.awt.Point;\nimport java.awt.Rectangle;\n", 1),
    ("import javax.swing.JTextArea;\n", "import javax.swing.JTextArea;\nimport javax.swing.SwingUtilities;\n", 1),
    ("""            assertThat(view.hasSelection()).as("no selection").isFalse();
        });
    }
}
""", """            assertThat(view.hasSelection()).as("no selection").isFalse();
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 6 and 12: a pane dropped on another pane of its tab goes beside
     * it on the side whose edge the cursor is nearest; the landing shows that half; nothing reaches the tab registry.
     */
    @Test void aPaneDropsBesideAnotherPaneOfItsTabOnTheNearestSide() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            GroupView under = owner.workspace().groupView(c.group().orElseThrow());
            Point low = inside(pane(owner, under.id()), 0.5, 0.9);
            facts.clear();
            Component source = startPane(owner, a, low);
            assertThat(drag.target()).contains(new TabDrag.Target.Beside(owner, under.id(), Side.DOWN));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            assertThat(drag.overlay().getParent()).as("the landing shows in its window").isSameAs(layers);
            assertThat(drag.overlay().highlight()).as("c's lower half").isEqualTo(SwingUtilities.convertRectangle(under,
                new Rectangle(0, under.getHeight() - under.getHeight() / 2, under.getWidth(), under.getHeight() / 2), layers));
            mouse(source, MouseEvent.MOUSE_RELEASED, low, PANE);
            assertThat(shape(owner)).isEqualTo("V[b, c, a]");
            assertThat(owner.selectedTab()).as("the moved pane is the active one").isSameAs(a);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(moves()).as("within its window: no TAB_MOVED").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
        });
    }

    /** Tabs and panes spec 7 and workspace plan 8c Rulings 7: over its own pane, a divider or the title bar a pane takes no drop. */
    @Test void aPaneOverItsOwnPaneADividerOrTheTitleBarTakesNoDrop() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, b, inside(pane(owner, b.group().orElseThrow()), 0.9, 0.9));
            assertThat(drag.active()).isTrue();
            assertThat(drag.target()).as("its own pane, near an edge").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_DRAGGED, middle(owner.workspace().split(List.of()).divider(0)), PANE);
            assertThat(drag.target()).as("the divider between a and the column").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, onTitleBar(frame), PANE);
            assertThat(drag.target()).as("the title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame), PANE);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /**
     * Tabs and panes spec 7 and 13: hovering another tab's entry shows that tab at once, with no drop of its own, and a
     * pane of it then takes the pane, which leaves its tab and shows in its new one.
     */
    @Test void hoveringATabsEntryShowsItAndAPaneOfItTakesTheDrop() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, c, onEntry(owner, d, 0.5));
            assertThat(owner.activeParentTab()).as("the hovered tab shows at once").isEqualTo(second);
            assertThat(drag.target()).as("an entry takes no drop").isEmpty();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            assertThat(drag.target()).contains(new TabDrag.Target.Beside(owner, d.group().orElseThrow(), Side.RIGHT));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE);
            assertThat(owner.activeParentTab()).as("the tab it landed in shows").isEqualTo(second);
            assertThat(shape(owner)).isEqualTo("H[d, c]");
            assertThat(owner.selectedTab()).isSameAs(c);
            assertThat(moves()).isEmpty();
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), c.id().value()));
            owner.selectParentTab(first);
            assertThat(shape(owner)).as("its old tab keeps its other panes").isEqualTo("H[a, b]");
        });
    }

    /**
     * Tabs and panes spec 13 and Review Focus 3: a drag that showed another tab and ends elsewhere shows the tab that
     * showed before again, its item focused: after Esc, and after a release over nothing.
     */
    @Test void aDragEndingElsewhereShowsTheTabThatShowedBefore() throws Exception {
        edt(() -> {
            int[] focused = {0};
            b.focusTarget(() -> focused[0]++);
            Point entry = onEntry(owner, d, 0.5);
            Component source = startPane(owner, b, entry);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            focused[0] = 0;
            assertThat(esc()).isTrue();
            assertThat(owner.activeParentTab()).as("after Esc").isEqualTo(first);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(focused[0]).as("its item takes the focus again").isPositive();
            mouse(source, MouseEvent.MOUSE_RELEASED, entry, PANE);
            source = startPane(owner, b, entry, onTitleBar(frame));
            assertThat(owner.activeParentTab()).as("still the hovered tab while the drag runs").isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, onTitleBar(frame), PANE);
            assertThat(owner.activeParentTab()).as("after a release over the title bar").isEqualTo(first);
            assertThat(shape(owner)).isEqualTo("H[a, V[b, c]]");
        });
    }

    /** Tabs and panes spec 7: a tab's last pane moved into another tab of its window takes its tab with it. */
    @Test void aTabsLastPaneMovedIntoAnotherTabClosesItsTab() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            Tab shownThere = owner.parentTabs().view(first).orElseThrow().selected();
            Component source = startPane(owner, d, onEntry(owner, shownThere, 0.5));
            assertThat(owner.activeParentTab()).isEqualTo(first);
            Point left = inside(pane(owner, a.group().orElseThrow()), 0.1, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, left, PANE);
            mouse(source, MouseEvent.MOUSE_RELEASED, left, PANE);
            assertThat(owner.parentTabCount()).as("its tab went with it").isEqualTo(1);
            assertThat(shape(owner)).isEqualTo("H[d, a, V[b, c]]");
            assertThat(owner.tabBar().component().isVisible()).as("one tab: the strip hides").isFalse();
        });
    }

    /**
     * Workspace plan 8c Rulings 17's cases: a terminal opens beside "c" and is selected, so the registry, which takes this
     * window as its active one, has an active pane to report; "a", "b" and "c", the plugin views a move of the terminal
     * leaves behind, record into {@code heard} every activation change they hear from then on.
     *
     * @return the terminal's item
     */
    private Tab shellBesideC(List<Boolean> heard) {
        owner.selectTab(c);
        TerminalContent terminal = owner.split(Side.RIGHT);
        Tab shell = owner.selectedTab();
        assertThat(shell.content()).isSameAs(terminal);
        terminals.windowActivated(owner.id());
        for (Tab left : List.of(a, b, c)) left.onActive(heard::add);
        facts.clear();
        return shell;
    }

    /** What the registry heard of selections, moves, closes and the active pane since {@link #facts} was last cleared. */
    private List<TerminalEvent> announced() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabSelected || fact instanceof TerminalEvent.TabMoved
            || fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.ActivePaneChanged).toList();
    }

    /**
     * Workspace plan 8c Rulings 17: the selected item's pane moved beside a pane of another tab of its window is announced
     * once, where it lands. It stays the selected item, so the registry hears no selection, move, close or active pane,
     * and no item of the tab it left turns active on the way. This pins {@code movePane} itself, called while the item's
     * own tab shows: a drag shows the other tab first (Rulings 4), so its drop announces the moved item once, which
     * {@code hoveringATabsEntryShowsItAndAPaneOfItTakesTheDrop} pins.
     */
    @Test void aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            owner.movePane(shell, d.group().orElseThrow(), Side.RIGHT);
            assertThat(owner.activeParentTab()).isEqualTo(second);
            assertThat(owner.workspace().tabs()).containsExactly(d, shell);
            assertThat(owner.selectedTab()).isSameAs(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /**
     * Workspace plan 8c Rulings 17: Move Pane to New Tab, plan 8b's command, is announced once too, where the pane lands,
     * though the command announced its old tab's next pane before this plan.
     */
    @Test void movePaneToNewTabIsAnnouncedOnceWhereThePaneLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            owner.updateActions();
            owner.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.workspace().tabs()).as("its own tab, shown").containsExactly(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /** A plugin's view whose next showing throws once armed, as a view that fails to take its new pane does. */
    private static final class Refusing extends JPanel {
        boolean armed;

        @Override public void setVisible(boolean visible) {
            if (visible && armed) {
                armed = false;
                throw new IllegalStateException("refused");
            }
            super.setVisible(visible);
        }
    }

    /**
     * Workspace plan 8c Rulings 17: a pane that fails to land goes back where it was, beside another tab's pane and into a
     * new tab alike: the window's tabs and layouts as they were, the item in its pane, nothing closed, and the failure
     * propagates.
     */
    @Test void aPaneThatFailsToLandGoesBackWhereItWas() throws Exception {
        edt(() -> {
            var refusing = new Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.SPLIT_RIGHT);
            var before = owner.windowLayout();
            var pane = notes.group().orElseThrow();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.movePane(notes, d.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("beside another tab's pane").isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(owner.workspace().groupView(pane));
            refusing.armed = true;
            assertThatThrownBy(() -> owner.moveToNewTab(notes)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("into a new tab").isEqualTo(before);
            assertThat(refusing.getParent()).isSameAs(owner.workspace().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
        });
    }

    /**
     * Workspace plan 8c Rulings 17: a tab's last pane that fails to land beside another tab's pane goes back, its tab
     * standing as it stood; once it lands, its tab goes with it (spec 4).
     */
    @Test void aTabsLastPaneThatFailsToLandKeepsItsTab() throws Exception {
        edt(() -> {
            var refusing = new Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            owner.selectParentTab(first);
            var before = owner.windowLayout();
            var pane = notes.group().orElseThrow();
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.movePane(notes, a.group().orElseThrow(), Side.LEFT)).hasMessage("refused");
            assertThat(owner.windowLayout()).isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane")
                .isSameAs(owner.parentTabs().view(own).orElseThrow().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
            owner.movePane(notes, a.group().orElseThrow(), Side.LEFT);
            assertThat(owner.parentTabCount()).as("its tab went with it").isEqualTo(2);
            assertThat(shape(owner)).isEqualTo("H[notes, a, V[b, c]]");
        });
    }

    /**
     * Workspace plan 8c Rulings 17: a new tab that fails once it stands in the strip, where a displayable window first
     * gives its item's view a peer, goes again, and the pane goes back where it was.
     */
    @Test void aNewTabThatFailsOnceItStandsGoesAgain() throws Exception {
        edt(() -> {
            boolean[] armed = {false};
            owner.parentTabs().addContainerListener(new java.awt.event.ContainerAdapter() {
                @Override public void componentAdded(java.awt.event.ContainerEvent event) {
                    if (!armed[0]) return;
                    armed[0] = false;
                    throw new IllegalStateException("refused");
                }
            });
            var before = owner.windowLayout();
            var pane = c.group().orElseThrow();
            armed[0] = true;
            assertThatThrownBy(() -> owner.moveToNewTab(c)).hasMessage("refused");
            assertThat(owner.windowLayout()).isEqualTo(before);
            assertThat(owner.parentTabs().views()).as("no tab stays behind").hasSize(2);
            assertThat(c.component().getParent()).as("back in its pane").isSameAs(owner.workspace().groupView(pane));
        });
    }

    /** Tabs and panes spec 13 and Review Focus 4: a pane dropped into a tab whose pane is maximized ends the maximize. */
    @Test void aDropIntoAMaximizedTabEndsItsMaximize() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            view("e", TabRegistry.Placement.SPLIT_RIGHT);
            owner.selectTab(d);
            owner.workspace().toggleMaximize();
            owner.selectParentTab(first);
            relayout(frame);
            Component source = startPane(owner, b, onEntry(owner, d, 0.5));
            assertThat(owner.workspace().model().maximized()).as("the hovered tab shows maximized").isPresent();
            Point right = inside(pane(owner, d.group().orElseThrow()), 0.9, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, right, PANE);
            assertThat(owner.tabDrag().target()).as("only the maximized pane shows")
                .contains(new TabDrag.Target.Beside(owner, d.group().orElseThrow(), Side.RIGHT));
            mouse(source, MouseEvent.MOUSE_RELEASED, right, PANE);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(shape(owner)).isEqualTo("H[d, b, e]");
        });
    }
}
""", 1)])
T = W + "TabDragWindowsTest.java"
edit(T, [
    (""" * Workspace tabs spec 12, 15 and 17, tabs and panes spec 6: one tab drag across Jasper's windows, on synthetic events,
 * headless. The window manager's windows stand in stand-in frames on a pretend screen, each under a 28 px title bar: the
 * main window at 0,0 (1208 x 800) and a child window beside it at 1400,100 (800 x 600), one tab each holding one
 * terminal, and a new window far away until a test places it. The standard look shows a window's strip from two tabs,
 * so a test gives the window it drags from a second tab first.
""", """ * Workspace tabs spec 12, 15 and 17, tabs and panes spec 6 and 7: one tab drag, which drags panes too, across Jasper's
 * windows, on synthetic events, headless. The window manager's windows stand in stand-in frames on a pretend screen,
 * each under a 28 px title bar: the main window at 0,0 (1208 x 800) and a child window beside it at 1400,100 (800 x
 * 600), one tab each holding one terminal, and a new window far away until a test places it. The standard look shows a
 * window's strip from two tabs, so a test gives the window it drags from a second tab first.
""", 1),
    ("import java.awt.event.MouseEvent;\n", "import java.awt.event.KeyEvent;\nimport java.awt.event.MouseEvent;\n", 1),
    ("""        assertThat(TabDrag.dropFrame(display, null, false, false, true)).as("no display known").isEqualTo(display);
    }
}
""", """        assertThat(TabDrag.dropFrame(display, null, false, false, true)).as("no display known").isEqualTo(display);
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 12: a pane dropped beside another window's pane moves there
     * through the tab registry, one TAB_MOVED, its session kept, and shows there; its old tab keeps its other pane.
     */
    @Test void aPaneDroppedBesideAnotherWindowsPaneMovesThroughTheRegistry() throws Exception {
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            List<Tab> panes = from.workspace().tabs();
            Tab stays = panes.get(0), moving = panes.get(1), there = to.selectedTab();
            GroupId target = there.group().orElseThrow();
            Object kept = session(moving);
            Point left = inside(pane(to, target), 0.1, 0.5);
            facts.clear();
            Component source = startPane(from, moving, left);
            assertThat(manager.tabDrag().movingPane()).isTrue();
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Beside(to, target, Side.LEFT));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            mouse(source, MouseEvent.MOUSE_RELEASED, left, PANE);
            assertThat(to.workspace().tabs()).as("beside it, on its left").containsExactly(moving, there);
            assertThat(to.selectedTab()).as("shown where it landed").isSameAs(moving);
            assertThat(from.workspace().tabs()).containsExactly(stays);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), child.id()));
            assertThat(session(moving)).as("the same session").isSameAs(kept);
        });
    }

    /** Review Focus 5: a full window's last pane dropped into another window moves there, and its window closes after the move. */
    @Test void aFullWindowsLastPaneLeavingClosesItAfterItsMove() throws Exception {
        edt(() -> {
            WindowContent from = child.content(), to = main.content();
            Tab moving = from.selectedTab(), there = to.selectedTab();
            Point right = inside(pane(to, there.group().orElseThrow()), 0.9, 0.5);
            facts.clear();
            dropPane(from, moving, right);
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
            assertThat(child.closed()).as("its last pane moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            int moved = facts.indexOf(new TerminalEvent.TabMoved(moving.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    /**
     * Review Focus 5 and spec 4: the main window's only pane dropped beside another window's leaves the main window's
     * empty workspace, under the id it had, which no other window holds.
     */
    @Test void theMainWindowsOnlyPaneLeavingLeavesItsEmptyWorkspace() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.selectedTab(), there = to.selectedTab();
            ParentTabId emptied = from.activeParentTab();
            dropPane(from, moving, inside(pane(to, there.group().orElseThrow()), 0.5, 0.9));
            assertThat(to.workspace().tabs()).as("below it").containsExactly(there, moving);
            assertThat(main.closed()).isFalse();
            assertThat(from.parentTabs().isEmpty()).as("the main window's empty workspace").isTrue();
            assertThat(tabIds(from)).as("its one tab, empty").containsExactly(emptied);
            assertThat(tabIds(to)).as("no id in two windows").doesNotContain(emptied);
        });
    }

    /** Tabs and panes spec 7: a tab's only pane dropped into another window takes its tab with it; its window keeps the other. */
    @Test void aTabsOnlyPaneLeavingForAnotherWindowTakesItsTab() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = main.content().activeParentTab();
            ids[1] = secondTab(main);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = entryOf(from, ids[1]), there = to.selectedTab();
            dropPane(from, moving, inside(pane(to, there.group().orElseThrow()), 0.9, 0.5));
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
            assertThat(tabIds(from)).as("its tab went with it").containsExactly(ids[0]);
            assertThat(from.tabBar().component().isVisible()).as("one tab: the strip hides").isFalse();
        });
    }

    /**
     * Tabs and panes spec 13 and workspace plan 8c Rulings 4 and 5: hovering another window's tab shows it there until the
     * drag ends elsewhere, when the tab that showed before shows again; a pane of the hovered tab takes the drop.
     */
    @Test void hoveringAnotherWindowsTabShowsItUntilTheDragEndsElsewhere() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> recordingKeys(keys -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1), there = entryOf(to, ids[0]);
            Point entry = onEntry(to, there, 0.5);
            Component source = startPane(from, moving, entry);
            assertThat(to.activeParentTab()).as("the hovered tab shows").isEqualTo(ids[0]);
            assertThat(keys.send(new KeyEvent(from, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).isTrue();
            assertThat(to.activeParentTab()).as("Esc: the tab that showed before").isEqualTo(ids[1]);
            mouse(source, MouseEvent.MOUSE_RELEASED, entry, PANE);
            source = startPane(from, moving, entry);
            Point below = inside(pane(to, there.group().orElseThrow()), 0.5, 0.9);
            mouse(source, MouseEvent.MOUSE_DRAGGED, below, PANE);
            mouse(source, MouseEvent.MOUSE_RELEASED, below, PANE);
            assertThat(to.activeParentTab()).as("the tab it landed in shows").isEqualTo(ids[0]);
            assertThat(to.workspace().tabs()).containsExactly(there, moving);
        }));
    }

    /** Tabs and panes spec 7: outside every window a pane drops nothing, and no window opens for it. */
    @Test void outsideEveryWindowAPaneDropsNothing() throws Exception {
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
        });
        launch();
        edt(() -> {
            WindowContent from = main.content();
            Tab moving = from.workspace().tabs().get(1);
            Point outside = new Point(3000, 3000);
            Component source = startPane(from, moving, outside);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside, PANE);
            assertThat(manager.windows()).containsExactly(main, child);
            assertThat(placed).as("no window was placed").isEmpty();
            assertThat(from.workspace().tabs()).contains(moving);
        });
    }
}
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.WorkspaceDropResolverTest' --tests 'dev.jasper.app.workspace.PaneDragTest' --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --continue`
Expected: FAIL: compilation (`Area(GroupId, Rectangle)`, `Drop(GroupId, Side)`, `TabDrag.Target.Beside` do not exist).

- [ ] **Step 3: Implement**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java` with:

```java
package dev.jasper.app.dock;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a drag would drop, from the cursor alone (workspace tabs spec 12; tabs and panes spec 6 and 7): a dragged pane
 * beside the pane under the cursor, on the side whose edge is nearest; a dragged tab's place among a strip's entries; the
 * rectangles that show the landing; and which of several windows takes the drop. The tab and pane drag's twin of
 * {@link DockDropResolver}. Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class WorkspaceDropResolver {
    private WorkspaceDropResolver() { }

    /**
     * A pane a dragged pane may drop beside.
     *
     * @param group the pane
     * @param bounds its rectangle
     */
    public record Area(GroupId group, Rectangle bounds) {
        /** Requires the pane and copies the rectangle. */
        public Area {
            Objects.requireNonNull(group, "group");
            bounds = new Rectangle(bounds);
        }

        @Override public Rectangle bounds() { return new Rectangle(bounds); }
    }

    /**
     * Where a dragged pane would go: into a new pane on {@code side} of {@code group}, which takes half of its space.
     *
     * @param group the pane beside which it goes
     * @param side where it goes
     */
    public record Drop(GroupId group, Side side) {
        /** Requires both. */
        public Drop {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(side, "side");
        }
    }

    /**
     * The drop at {@code cursor} (tabs and panes spec 7; workspace plan 8c Rulings 6): beside the area holding it, on the
     * side whose edge is nearest, each distance a share of the area's own width or height, so that the four sides take the
     * triangles between its diagonals at any aspect ratio; a tie goes to the left, then the right, then up, then down. An
     * area's left and top edges lie inside it, its right and bottom edges do not. Anywhere else, such as a divider between
     * panes: nothing.
     *
     * @param cursor the cursor
     * @param areas the panes
     * @return the drop, or empty
     */
    public static Optional<Drop> resolve(Point cursor, List<Area> areas) {
        Objects.requireNonNull(cursor, "cursor");
        for (Area area : areas)
            if (area.bounds.contains(cursor)) return Optional.of(new Drop(area.group, nearest(cursor, area.bounds)));
        return Optional.empty();
    }

    /**
     * Where a dragged tab would go in a window's strip (tabs and panes spec 6): before the first of the strip's other
     * entries whose middle is not left of the cursor.
     *
     * @param cursor the cursor
     * @param bar the strip
     * @param entries the strip's entries in strip order, without the dragged tab's own
     * @return the place, 0 to the number of entries; empty when the cursor is not over the strip
     */
    public static OptionalInt place(Point cursor, Rectangle bar, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        if (!bar.contains(cursor)) return OptionalInt.empty();
        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return OptionalInt.of(index);
    }

    /** The side of {@code bounds} whose edge is nearest {@code cursor}, each distance a share of its width or height. */
    private static Side nearest(Point cursor, Rectangle bounds) {
        // The shares dx / w, (w - dx) / w, dy / h and (h - dy) / h, scaled by w * h: whole numbers, so ties are exact.
        long width = bounds.width, height = bounds.height;
        long dx = cursor.x - bounds.x, dy = cursor.y - bounds.y;
        long[] distance = {dx * height, (width - dx) * height, dy * width, (height - dy) * width};
        Side[] sides = {Side.LEFT, Side.RIGHT, Side.UP, Side.DOWN};
        int nearest = 0;
        for (int i = 1; i < sides.length; i++) if (distance[i] < distance[nearest]) nearest = i;
        return sides[nearest];
    }

    /**
     * Where a drop would land, for the overlay: the half of its area on its side, where the new pane goes.
     *
     * @param drop a drop {@link #resolve} gave for these areas
     * @param areas the panes it was resolved over
     * @return the rectangle, in the areas' coordinates
     * @throws IllegalArgumentException when no area is the drop's pane
     */
    public static Rectangle landing(Drop drop, List<Area> areas) {
        Rectangle whole = areas.stream().filter(candidate -> candidate.group.equals(drop.group())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No area for group " + drop.group())).bounds;
        return switch (drop.side()) {
            case LEFT -> new Rectangle(whole.x, whole.y, whole.width / 2, whole.height);
            case RIGHT -> new Rectangle(whole.x + whole.width - whole.width / 2, whole.y, whole.width / 2, whole.height);
            case UP -> new Rectangle(whole.x, whole.y, whole.width, whole.height / 2);
            case DOWN -> new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2);
        };
    }

    /**
     * A drop's marker in a strip: a bar {@code width} wide and as tall as {@code strip}, centred on the place before the
     * entry at {@code index} (after the last at the end) and kept inside {@code strip}.
     *
     * @param strip the strip
     * @param entries its entries in order, without a dragged tab's own
     * @param index the place, 0 to the number of entries
     * @param width the marker's width
     * @return the marker, in the strip's coordinates
     * @throws IllegalArgumentException when the index is outside 0 to the number of entries
     */
    public static Rectangle marker(Rectangle strip, List<Rectangle> entries, int index, int width) {
        if (index < 0 || index > entries.size())
            throw new IllegalArgumentException("Index " + index + " is outside 0 to " + entries.size());
        int x;
        if (entries.isEmpty()) x = strip.x;
        else if (index == 0) x = entries.getFirst().x;
        else if (index >= entries.size()) x = entries.getLast().x + entries.getLast().width;
        else x = (entries.get(index - 1).x + entries.get(index - 1).width + entries.get(index).x) / 2;
        int left = Math.max(strip.x, Math.min(x - width / 2, strip.x + strip.width - width));
        return new Rectangle(left, strip.y, width, strip.height);
    }

    /**
     * The window that takes a drop at {@code cursor}: the first of {@code windows} that holds it. The caller lists them most
     * recently active first, so where windows overlap the most recently active one wins (spec 12).
     *
     * @param cursor the cursor, on screen
     * @param windows each window's bounds on screen, most recently active first; an empty rectangle for one that does not
     *     show
     * @return the index of the window, or empty outside them all
     */
    public static OptionalInt windowAt(Point cursor, List<Rectangle> windows) {
        for (int i = 0; i < windows.size(); i++) if (windows.get(i).contains(cursor)) return OptionalInt.of(i);
        return OptionalInt.empty();
    }
}
```

Then the drag's targets, the moves and the window manager's move:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit, span
P = "jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java"
edit(P, [
    ("import dev.jasper.app.dock.WorkspaceDropResolver;\nimport dev.jasper.app.layout.ParentTabId;\n",
     "import dev.jasper.app.dock.WorkspaceDropResolver;\nimport dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.Side;\n", 1),
    ("import java.util.HashSet;\nimport java.util.List;\n", "import java.util.HashSet;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\n", 1),
    (""" * <p>A pane takes targets in windows alone, as a pane never becomes a window (tabs and panes spec 7); the drag of an item
 * that is pinned or being closed never starts.
""", """ * <p>A pane's targets (tabs and panes spec 7): a pane of the window's shown tab other than its own, beside which it goes
 * on the side whose edge the cursor is nearest; and a tab's entry in the window's strip, which shows that tab at once,
 * with no drop of its own, so that one of its panes can take the pane; the tab that showed there before shows again when
 * the drag ends, unless its drop lands in that window (spec 13). A pane takes targets in windows alone, as a pane never
 * becomes a window; the drag of an item that is pinned or being closed never starts.
""", 1),
    (""" * <p>Releasing the left button carries the target out: a tab along its own strip as a reorder, into another window or
 * out of every window through the window manager of the drag's window, which moves the tab's items through the tab
 * registry; none touches an item's content or session, and a window no manager opened moves nothing out of itself.
""", """ * <p>Releasing the left button carries the target out: a tab along its own strip as a reorder and a pane within its
 * window as a layout change, neither through the tab registry; into another window, or a tab out of every window,
 * through the window manager of the drag's window, which moves the items through the tab registry, a moved pane's item
 * taking the focus where it lands; none touches an item's content or session, and a window no manager opened moves
 * nothing out of itself.
""", 1),
    ("""    /** Where a release would put the dragged tab. */
""", """    /** Where a release would put the dragged tab or pane. */
""", 1),
    ("""        record TearOut(Rectangle bounds, Point cursor) implements Target { }
    }
""", """        record TearOut(Rectangle bounds, Point cursor) implements Target { }

        /**
         * Beside the pane {@code group} of {@code window}'s shown tab, on {@code side}: a pane drag's (tabs and panes spec
         * 7).
         *
         * @param window the window
         * @param group the pane
         * @param side where the dragged pane goes
         */
        record Beside(WindowContent window, GroupId group, Side side) implements Target { }
    }
""", 1),
    ("""    private final AWTEventListener gestureEvents = this::gestureEvent;
""", """    private final AWTEventListener gestureEvents = this::gestureEvent;
    /**
     * The windows a pane drag showed another tab of, each with the tab that showed there before, which shows again as the
     * drag ends unless its drop lands in that window (tabs and panes spec 13).
     */
    private final Map<WindowContent, ParentTabId> hovered = new LinkedHashMap<>();
""", 1),
    ("""            // A pane takes targets in a window alone: it never becomes a window (tabs and panes spec 7).
            showLanding();
""", """            // A pane takes targets in a window alone: it never becomes a window (tabs and panes spec 7).
            if (under.isPresent()) target = onPanes(open.get(under.getAsInt()), at);
            showLanding();
""", 1),
    ("""    private static Rectangle barArea(WindowContent window) {
        Component bar = window.tabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }
""", """    private static Rectangle barArea(WindowContent window) {
        Component bar = window.tabBar().component();
        if (bar.isVisible()) return bar.getBounds();
        return new Rectangle(0, 0, window.parentTabs().getWidth(), bar.getPreferredSize().height);
    }

    /**
     * A pane's target in {@code window} at {@code at} (tabs and panes spec 7): over the shown strip, an entry, whose tab
     * shows at once and takes no drop itself; over a pane of the shown tab other than the dragged one's own, beside it on
     * the side whose edge is nearest (workspace plan 8c Rulings 6 and 7). Empty anywhere else (the window's dock, chrome
     * and title bar, a divider, the dragged pane itself) and for an item that may no longer move.
     */
    private Optional<Target> onPanes(WindowContent window, Point at) {
        if (!from.tabRegistry().movable(tab)) return Optional.empty();
        ParentTabsView parents = window.parentTabs();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, parents);
        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        Component strip = window.tabBar().component();
        if (strip.isVisible() && strip.getBounds().contains(local)) {
            Point inStrip = SwingUtilities.convertPoint(parents, local, strip);
            List<Rectangle> entries = window.tabBar().tabBounds();
            List<Tab> listed = parents.bar().tabs();
            for (int i = 0; i < Math.min(entries.size(), listed.size()); i++)
                if (entries.get(i).contains(inStrip)) hover(window, parents.bar().id(listed.get(i)));
            return Optional.empty();
        }
        WorkspaceView workspace = parents.active();
        var panes = new ArrayList<WorkspaceDropResolver.Area>();
        for (GroupView view : workspace.groupViews())
            if (view.tab() != tab && view.isVisible() && SwingUtilities.isDescendingFrom(view, workspace))
                panes.add(new WorkspaceDropResolver.Area(view.id(),
                    SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents)));
        return WorkspaceDropResolver.resolve(local, panes).map(drop -> new Target.Beside(window, drop.group(), drop.side()));
    }

    /**
     * Shows {@code window}'s tab {@code id} for a pane drag (tabs and panes spec 7 and 13; workspace plan 8c Rulings 4),
     * remembering the tab that showed there before the drag first showed another. In the drag's own window its active
     * item takes the focus, as Select Tab gives it; another window's focus stays where it is, as a focus request there
     * would raise that window and take the focus from the drag's own, which ends the drag. Its panes take their places at
     * once, for the next target.
     */
    private void hover(WindowContent window, ParentTabId id) {
        if (id == null || window.activeParentTab().equals(id)) return;
        hovered.putIfAbsent(window, window.activeParentTab());
        if (window == from) window.selectParentTab(id);
        else window.parentTabs().select(id);
        window.parentTabs().validate();
    }

    /**
     * Each window a pane drag showed another tab of shows the tab that showed before (tabs and panes spec 13; workspace
     * plan 8c Rulings 5), except {@code kept}, the window its drop lands in, which shows what the drop shows; in
     * {@code home}, the drag's own window, that tab's active item takes the focus again. A tab gone meanwhile stays gone.
     */
    private void restore(WindowContent home, WindowContent kept) {
        var before = new LinkedHashMap<>(hovered);
        hovered.clear();
        for (Map.Entry<WindowContent, ParentTabId> entry : before.entrySet()) {
            WindowContent window = entry.getKey();
            if (window == kept || window.closed()) continue;
            if (window == home) window.selectParentTab(entry.getValue());
            else window.parentTabs().select(entry.getValue());
        }
    }
""", 1),
    ("""        try {
            update(screen(event));
            where = target;
        } finally {
            finish();
        }
""", """        try {
            update(screen(event));
            where = target;
        } finally {
            finish(where.map(TabDrag::window).orElse(null));
        }
""", 1),
    ("""    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() {
        boolean wasDragging = dragging;
        dragging = false;
""", """    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() { finish(null); }

    /**
     * As {@link #finish()}; the windows a pane drag showed another tab of show the tab that showed before, except
     * {@code kept}, the window the drop lands in (tabs and panes spec 13).
     */
    private void finish(WindowContent kept) {
        boolean wasDragging = dragging;
        WindowContent home = from;
        dragging = false;
""", 1),
    ("""        if (shown != null) {
            shown.close();
            shown = null;
        }
        if (!wasDragging) return;
""", """        if (shown != null) {
            shown.close();
            shown = null;
        }
        // The tabs a pane drag showed give way once nothing of the drag listens any more.
        restore(home, kept);
        if (!wasDragging) return;
""", 1),
    ("""     * Carries a release out; nothing touches an item's content or session. Along its own strip the tab takes its new
     * place there; on another window's strip, or out of every window into a full window, the window manager moves it
     * whole ({@code home}, the drag's window, holds {@code parent}). A window no manager opened moves nothing out of
     * itself.
     */
""", """     * Carries a release out; nothing touches an item's content or session. Along its own strip the tab takes its new
     * place there; on another window's strip, or out of every window into a full window, the window manager moves it
     * whole ({@code home}, the drag's window, holds {@code parent}). A pane beside another pane of its window moves there
     * as a layout change; beside another window's, the window manager moves its item there (workspace plan 8c Rulings
     * 12). A window no manager opened moves nothing out of itself.
     */
""", 1),
    ("""            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.moveTabToNewWindow(home, parent, out.bounds(), out.cursor()));
        }
    }
""", """            case Target.TearOut out -> home.manager().ifPresent(manager -> manager.moveTabToNewWindow(home, parent, out.bounds(), out.cursor()));
            case Target.Beside beside -> {
                if (beside.window() == home) home.movePane(moving, beside.group(), beside.side());
                // Another window's pane: the window manager moves the item there, through the tab registry.
                else home.manager().ifPresent(manager -> manager.movePane(home, moving, beside.window(), beside.group(), beside.side()));
            }
        }
    }

    /** The window {@code target} drops in; none for a tear-out's new window. */
    private static WindowContent window(Target target) {
        return switch (target) {
            case Target.Bar bar -> bar.window();
            case Target.Beside beside -> beside.window();
            case Target.TearOut out -> null;
        };
    }
""", 1)])
span(P, """    /**
     * Shows the target's landing on its window's layered pane: a bar where the tab would go in the strip as the
""", """        overlay.show(highlight, placeholder, null, null);
    }
""", """    /**
     * Shows the target's landing on its window's layered pane: for a tab, a bar where it would go in the strip as the
     * placeholder, or the band of a strip that hides as the highlight; for a pane, the half of the pane beside which it
     * would go as the highlight; the main window's whole empty workspace for what drops there. Without a target in a
     * window the overlay leaves its window.
     */
    private void showLanding() {
        WindowContent window = target.map(TabDrag::window).orElse(null);
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
        // The main window's empty workspace gives way to what drops there: all of it shows.
        if (parents.isEmpty())
            highlight = SwingUtilities.convertRectangle(parents, new Rectangle(parents.getSize()), layers);
        else switch (target.get()) {
            // The half of the pane beside which the dragged pane goes (tabs and panes spec 7).
            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents, besideLanding(parents, beside), layers);
            // A strip that hides takes the tab at its end: its band shows (workspace plan 7c Rulings 5).
            case Target.Bar bar when !window.tabBar().component().isVisible() ->
                highlight = SwingUtilities.convertRectangle(parents, barArea(window), layers);
            case Target.Bar bar -> placeholder = SwingUtilities.convertRectangle(parents,
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), bar.index(), marker), layers);
            case Target.TearOut out -> { }
        }
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, null, null);
    }

    /** Where a pane dropped as {@code beside} says lands, in {@code parents}' coordinates: the half of that pane on its side. */
    private static Rectangle besideLanding(ParentTabsView parents, Target.Beside beside) {
        GroupView view = parents.active().groupView(beside.group());
        var area = new WorkspaceDropResolver.Area(beside.group(),
            SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), parents));
        return WorkspaceDropResolver.landing(new WorkspaceDropResolver.Drop(beside.group(), beside.side()), List.of(area));
    }
""")
V = "jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java"
edit(V, [
    ("import dev.jasper.app.layout.GroupId;\n", "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Layout;\n", 1),
    ("""     * after it in the strip, which shows. Its component moves between the two workspaces: nothing closes, nothing leaves
     * the window, and the registry hears no move. Nothing for a tab's only pane or an item the window does not hold.
     */
    void moveToNewTab(Tab tab) {
        WorkspaceView from = holding(tab).orElse(null);
        if (closed || from == null || from.tabCount() < 2) return;
        int at = model.indexOf(from.id()) + 1;
        from.remove(tab);
        openParentTab(tab, ParentTabId.random(), at);
    }
""", """     * after it in the strip, which shows. Its component moves between the two workspaces: nothing closes, nothing leaves
     * the window, and the registry hears no move. The window announces it once, where it lands, and a new tab that fails
     * to take it leaves it where it was (workspace plan 8c Rulings 17). Nothing for a tab's only pane or an item the
     * window does not hold.
     */
    void moveToNewTab(Tab tab) {
        WorkspaceView from = holding(tab).orElse(null);
        if (closed || from == null || from.tabCount() < 2) return;
        int at = model.indexOf(from.id()) + 1;
        Layout before = from.model();
        ParentTabs strip = model;
        ParentTabId id = ParentTabId.random();
        owner.held(() -> {
            try {
                from.remove(tab);
                openParentTab(tab, id, at);
            } catch (RuntimeException | Error failure) {
                // A new tab that stood when the failure came goes again before its item goes back.
                WorkspaceView opened = views.remove(id);
                if (opened != null) {
                    model = strip;
                    try {
                        super.remove(opened);
                        opened.close();
                        reveal(active());
                    } catch (RuntimeException | Error undone) {
                        failure.addSuppressed(undone);
                    }
                }
                undo(failure, tab, from, before, null, null);
                throw failure;
            }
        });
    }

    /**
     * Moves {@code tab}'s pane beside the pane {@code group} of this window, on {@code side} (tabs and panes spec 7): within
     * its own tab a layout change ({@link WorkspaceView#moveToNewGroup}); into another tab it leaves its tab, which goes
     * with its last pane (spec 4), and joins that one, which shows, its maximize ending (spec 13). Its component moves
     * between the workspaces: nothing closes, nothing leaves the window and the registry hears no move. Into another tab
     * the window announces it once, where it lands, and a pane that fails to land goes back where it was (workspace plan
     * 8c Rulings 17). Nothing for an item or a pane the window does not hold, or for the item's own pane; the focus stays
     * where it is, for the caller to move.
     */
    void moveBeside(Tab tab, GroupId group, Side side) {
        WorkspaceView from = holding(tab).orElse(null), to = holding(group).orElse(null);
        if (closed || from == null || to == null || from.model().groupOf(tab.id()).filter(group::equals).isPresent()) return;
        if (from == to) {
            from.moveToNewGroup(tab, group, side);
            return;
        }
        Layout left = from.model(), joined = to.model();
        ParentTabs strip = model;
        // A tab's last pane takes its tab out of the strip first, as no tab beside others may be empty (spec 4); its
        // workspace stands until the pane has landed, so a pane that fails to land goes back into it.
        boolean last = from.tabCount() == 1, focused = last && from == active() && WorkspaceView.holdsFocus(owner, this);
        owner.held(() -> {
            try {
                if (last) {
                    model = model.remove(from.id());
                    views.remove(from.id());
                }
                from.remove(tab);
                to.openBeside(tab, group, side);
            } catch (RuntimeException | Error failure) {
                undo(failure, tab, from, left, to, joined);
                if (last) {
                    views.put(from.id(), from);
                    model = strip;
                }
                throw failure;
            }
            if (last) discard(from, focused);
            select(to.id());
        });
    }

    /**
     * Takes back a pane move whose landing failed (workspace plan 8c Rulings 17): {@code to}, when it was to take the pane,
     * and {@code from} return to the layouts they had, {@code from} holding {@code tab} again, so nothing closes. A
     * failure of that is suppressed under {@code failure}, which the caller rethrows.
     */
    private static void undo(Throwable failure, Tab tab, WorkspaceView from, Layout fromBefore, WorkspaceView to,
                             Layout toBefore) {
        try {
            if (to != null) to.restore(tab, toBefore);
            from.restore(tab, fromBefore);
        } catch (RuntimeException | Error undone) {
            failure.addSuppressed(undone);
        }
    }
""", 1),
    ("""        boolean held = view == active() && WorkspaceView.holdsFocus(owner, this);
        model = model.remove(view.id());
        views.remove(view.id());
        super.remove(view);
        view.close();
        reveal(active());
        owner.workspaceChanged(active(), held);
    }
""", """        drop(view);
    }

    /**
     * Takes the tab {@code view}, whose last pane closed or left, out of the strip with its workspace: the one used before
     * it shows, and takes the focus when the tab held it (spec 4; workspace plan 7a Rulings 2, 6 and 10).
     */
    private void drop(WorkspaceView view) {
        boolean held = view == active() && WorkspaceView.holdsFocus(owner, this);
        model = model.remove(view.id());
        views.remove(view.id());
        discard(view, held);
    }

    /**
     * Takes the workspace of {@code view}, a tab the strip's model no longer has, out of the tree and closes it: the tab
     * that shows then shows, taking the focus when {@code held}.
     */
    private void discard(WorkspaceView view, boolean held) {
        super.remove(view);
        view.close();
        reveal(active());
        owner.workspaceChanged(active(), held);
    }
""", 1)])
C = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(C, [
    ("""    private Landing landing;
""", """    private Landing landing;
    /** Pane moves between this window's tabs under way ({@link #held}): only where the last one ends is announced. */
    private int holding;
    /** Whether a change during a held move asked for the focus. */
    private boolean heldRefocus;
""", 1),
    ("""     * tab takes focus when {@code refocus} and {@code view} is the shown parent tab's.
     */
    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
""", """     * tab takes focus when {@code refocus} and {@code view} is the shown parent tab's. While a pane moves between tabs
     * ({@link #held}) only the model takes it, and the rest follows once, where the pane lands.
     */
    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        // A pane moving between tabs is announced once, where it lands (workspace plan 8c Rulings 17).
        if (holding > 0) {
            heldRefocus |= refocus;
            return;
        }
""", 1),
    ("""    void layoutChanged() { if (!closed) onLayoutChanged.run(); }
""", """    void layoutChanged() { if (!closed) onLayoutChanged.run(); }

    /**
     * Runs {@code move}, a pane moving between tabs of this window, as one change (workspace plan 8c Rulings 17): while it
     * runs the parent tabs' model takes each layout as it changes, and the save, the terminal registry, the palette, the
     * items' activation and the focus hear only where it ends, once, in one registry change. No item is announced
     * selected or turns active on the way. The move's failure propagates after that, a failure of the announcement
     * suppressed under it.
     *
     * @param move the move
     */
    void held(Runnable move) {
        holding++;
        Throwable failure = null;
        try {
            move.run();
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            if (--holding == 0) {
                boolean refocus = heldRefocus;
                heldRefocus = false;
                Runnable landed = () -> workspaceChanged(parentTabs.active(), refocus);
                try {
                    if (terminals != null) terminals.atomically(landed); else landed.run();
                } catch (RuntimeException | Error late) {
                    // The move's own failure stays the one that propagates.
                    if (failure == null) throw late;
                    failure.addSuppressed(late);
                }
            }
        }
    }
""", 1),
    ("""    void update() {
        if (closed) return;
        Tab selected = selectedTab();
""", """    void update() {
        // A held move's items hear where it ends, once (workspace plan 8c Rulings 17).
        if (closed || holding > 0) return;
        Tab selected = selectedTab();
""", 1),
    ("""    void moveToNewTab(Tab tab) { if (mayMoveToNewTab(tab)) parentTabs.moveToNewTab(tab); }
""", """    void moveToNewTab(Tab tab) { if (mayMoveToNewTab(tab)) parentTabs.moveToNewTab(tab); }

    /**
     * A pane drag's drop beside {@code group}, a pane of this window (tabs and panes spec 7; workspace plan 8c Rulings
     * 12): {@code tab}'s pane moves there on {@code side}, within its tab a layout change, into another tab leaving its
     * tab, which goes with its last pane; the item then takes the focus. Nothing closes, nothing leaves the window and the
     * registry hears no move. Nothing for an item that is pinned or being closed, or one this window does not hold.
     *
     * @param tab an item of this window
     * @param group another pane of this window
     * @param side where the item's pane goes
     */
    void movePane(Tab tab, GroupId group, Side side) {
        if (closed || tab == null || !registry.movable(tab) || !parentTabs.contains(tab)) return;
        parentTabs.moveBeside(tab, group, side);
        selectTab(tab);
    }
""", 1)])
K = "jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java"
edit(K, [("""        tabs.remove(tab.id());
        apply(next);
    }
""", """        tabs.remove(tab.id());
        apply(next);
    }

    /**
     * Takes back a pane move that failed (workspace plan 8c Rulings 17): this workspace returns to {@code before}, its
     * layout when the move began, holding {@code tab} again if {@code before} places it and letting it go if not.
     */
    void restore(Tab tab, Layout before) {
        if (before.contains(tab.id())) tabs.put(tab.id(), tab);
        else tabs.remove(tab.id());
        apply(before);
    }
""", 1)])
M = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(M, [
    ("import dev.jasper.app.layout.ParentTab;\n", "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.ParentTab;\n", 1),
    ("import dev.jasper.app.layout.ParentTabs;\n", "import dev.jasper.app.layout.ParentTabs;\nimport dev.jasper.app.layout.Side;\n", 1),
    ("""        rethrow(failure);
        return moved;
    }
""", """        rethrow(failure);
        return moved;
    }

    /**
     * A pane drag's drop beside {@code group}, a pane of {@code to}'s shown tab (tabs and panes spec 7; workspace plan 8c
     * Rulings 12): {@code tab}'s pane leaves {@code from} for a new pane on {@code side} of it, through the tab registry,
     * so it reports {@code TAB_MOVED} once and keeps its content and session, and {@code to} shows it, raised, its
     * maximize ended (spec 13). Its tab in {@code from} goes with its last pane, the main window keeping its empty
     * workspace, and a full window it empties closes after the move. An item that is pinned or being closed stays.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another main or full window
     * @param group a pane of {@code to}
     * @param side where the item's pane goes
     * @return whether it arrived
     */
    public boolean movePane(WindowContent from, Tab tab, WindowContent to, GroupId group, Side side) {
        requireEdt();
        if (!mayMovePane(from, tab, to) || to.parentTabs().holding(group).isEmpty()) return false;
        model.tabs().move(tab, to.id(), group, side);
        return to.parentTabs().contains(tab);
    }

    /** Whether {@code tab}, an item of {@code from}, may move to {@code to}, another open window: one the registry moves. */
    private boolean mayMovePane(WindowContent from, Tab tab, WindowContent to) {
        return from != null && to != null && from != to && !from.closed() && !to.closed() && from.parentTabs().contains(tab)
            && model.tabs().movable(tab);
    }
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/dock/package-info.java", [(
    """ * that finds where a dragged tab would drop on a workspace, and a dragged parent tab in a parent-tab bar. JDK-only: no
""", """ * that finds where a dragged pane would drop beside another pane, and a dragged tab in a window's tab strip. JDK-only: no
""", 1)])
# Plan 8a's executed fix round rewords this sentence; the span takes either wording.
span("jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java", "     * As {@link #move(Tab, UUID)}, ",
    "     * @param group a group of that window\n", """     * As {@link #move(Tab, UUID)}, into a new pane on {@code side} of the pane {@code group} in that window: a pane dragged
     * beside another window's pane (tabs and panes spec 7).
     *
     * @param tab the tab
     * @param window the window to move it to
     * @param group a pane of that window
""")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --continue`
Expected: PASS: the new cases, Task 1's, `TabDragTest` and the rest of `TabDragWindowsTest` (a tab drag's landing and
drop are unchanged), `MovePaneToNewTabTest` (plan 8b's command, now held, moves as before), `ParentTabsViewTest` (a
tab's last item closing still takes its tab) and `LayoutGeometryTest`'s pane moves. Then `./gradlew :jasper-app:check`:
PASS (`verifyApplicationArchitecture`: `dock` still names `layout` alone; the Javadoc of the public resolver and
`WindowManager.movePane`).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Tabs and panes: a pane drops beside another pane, in any window

The pane drag drops beside a pane of the shown tab on the side whose
edge the cursor is nearest, the landing showing that half; over a tab's
entry it shows that tab at once, and the tabs it showed show again when
the drag ends elsewhere. Within its window the move is a layout change,
its tab going with its last pane; into another window it goes through
the window manager and the tab registry, one TAB_MOVED. The resolver
keeps the pane drag's shape: no strip drop and no centre.

A pane moving between tabs of its window, Move Pane to New Tab's too,
is announced once, where it lands, inside one terminal-registry
change: its old tab's next pane is no longer announced selected and
active on the way. A pane that fails to land goes back where it was.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: A pane into a new tab

Spec 7's third target and the empty workspace. A pane dropped on a new-tab place of a strip becomes a new tab there,
which shows (Rulings 3): an entry's leading quarter is the place before it and its trailing quarter the place after it,
in either look, and the classic tabs' "+" and the room after them are the strip's end. An entry's middle half keeps
showing its tab (Task 2). The main window's empty workspace takes a pane anywhere over it, in its place (Rulings 10). In
its own window the only pane of its tab takes no new tab at any place (Rulings 8), and the new tab takes its place at
once, while plan 8b's Move Pane to New Tab, a command, keeps the strip's open motion (Rulings 9). Both are held as Task 2
holds them: announced once, where the pane lands, and back where it was if the new tab fails to take it (Rulings 17).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java` (Task 2's class Javadoc; after Task
  2's `place`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabStrip.java` (imports `:4-5`, after `tabBounds` `:28`),
  `WindowTabs.java` (imports `:3`, `:19`, after `tabBounds` `:136-140`), `ClassicTabs.java` (imports `:6`, `:18-22`,
  after `tabBounds` `:98`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabBar.java` (fields `:22-24`, `animates` `:96-97`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java` (Task 2's `moveToNewTab`, plan 8b's
  `:162-168`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (before `tabRegistry()` `:1389`; after
  Task 2's `movePane`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java` (before Task 2's `mayMovePane`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (Task 2's class Javadoc paragraph, `Target`,
  `onPanes`, `showLanding`, `drop` and `window`; `barEntries` `:338-349`)
- Test: `WorkspaceDropResolverTest`, `PaneDragTest`, `TabDragWindowsTest` (Task 2's), `WindowTabsTest.java` (before
  `named` `:215`), `ClassicTabsTest.java` (before `theDefaultLookKeepsTheEqualTabs` `:305`), `WindowTabMotionTest.java`
  (before `fixture` `:204`)

**Interfaces:**
- Consumes: Task 2's `TabDrag.onPanes`, `showLanding`, `drop`, `window(Target)`, `WindowManager.mayMovePane`,
  `WindowContent.movePane`, `held(Runnable)`, `ParentTabsView.moveToNewTab(Tab)` (held) and `undo`,
  `PaneDragTest.shellBesideC` and `announced`, `WorkspaceDropResolver.place`; plan 8b's
  `WindowContent.mayMoveToNewTab(Tab)`, `moveToNewTab(Tab)`; plan 8a's `ParentTabsView.openParentTab(Tab, ParentTabId,
  int)` and `land`, `WindowContent.landing` (`Landing(ParentTabId, int)`), `ParentTabBar.animates()`; plan 7b's
  `TabRegistry.move(Tab, UUID)`.
- Produces:
  - `static OptionalInt WorkspaceDropResolver.newTabPlace(Point cursor, List<Rectangle> entries)`;
  - `OptionalInt TabStrip.newTabPlace(Point point)`, in the strip's coordinates;
  - `void ParentTabBar.atOnce(Runnable change)`;
  - `void ParentTabsView.moveToNewTab(Tab tab, int index)`, which plan 8b's `moveToNewTab(Tab)` now calls, held as
    Task 2 holds that one;
  - `void WindowContent.movePaneToNewTab(Tab tab, int index)`, `void WindowContent.receiveAsTab(Tab tab, int index)`;
  - `boolean WindowManager.movePaneToNewTab(WindowContent from, Tab tab, WindowContent to, int index)`;
  - `TabDrag.Target.NewTab(WindowContent window, int index)`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java", [("""        assertThatNullPointerException().isThrownBy(() -> new Area(null, bounds));
    }
}
""", """        assertThatNullPointerException().isThrownBy(() -> new Area(null, bounds));
    }

    /** Workspace plan 8c Rulings 3: an entry's outer quarters are new-tab places, before and after it; its middle half is none. */
    @Test void anEntrysOuterQuartersAreNewTabPlaces() {
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 300, 30), new Rectangle(300, 0, 300, 30),
            new Rectangle(600, 0, 300, 30));
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(0, 15), entries)).as("the left edge is inside").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(74, 15), entries)).as("the leading quarter's last pixel").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(75, 15), entries)).as("the middle half").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(224, 15), entries)).isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(225, 15), entries)).as("the trailing quarter").hasValue(1);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(300, 15), entries)).as("the next one's leading quarter").hasValue(1);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(899, 15), entries)).as("after the last").hasValue(3);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(900, 15), entries)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(450, 30), entries)).as("nor the bottom edge").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(10, 10), List.of())).as("no entry").isEmpty();
    }
}
""", 1)])
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(W + "PaneDragTest.java", [
    ("import dev.jasper.app.appearance.ThemeTestSupport;\n",
     "import dev.jasper.app.appearance.ThemeController;\nimport dev.jasper.app.appearance.ThemeTestSupport;\n", 1),
    ("import dev.jasper.app.terminals.TerminalRegistry;\n",
     "import dev.jasper.app.terminals.TerminalRegistry;\nimport dev.jasper.app.testsupport.EnterpriseLook;\n", 1),
    ("""            assertThat(shape(owner)).isEqualTo("H[d, b, e]");
        });
    }
}
""", """            assertThat(shape(owner)).isEqualTo("H[d, b, e]");
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3 and 9: a pane dropped on an entry's outer quarter becomes a new
     * tab at that place, which shows and lands at once; a bar at the place shows where.
     */
    @Test void aPaneDroppedOnAnEntrysOuterQuarterBecomesANewTabThere() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = startPane(owner, b, onEntry(owner, d, 0.9));
            assertThat(drag.target()).as("after the last tab").contains(new TabDrag.Target.NewTab(owner, 2));
            Point before = onEntry(owner, d, 0.1);
            mouse(source, MouseEvent.MOUSE_DRAGGED, before, PANE);
            assertThat(drag.target()).as("before the second tab").contains(new TabDrag.Target.NewTab(owner, 1));
            assertThat(owner.activeParentTab()).as("a new-tab place shows no tab").isEqualTo(first);
            assertThat(drag.overlay().placeholder()).as("a bar at the place").isEqualTo(marker(owner, null, 1));
            facts.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, before, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.parentTabs().model().indexOf(owner.activeParentTab())).as("at that place, shown").isEqualTo(1);
            assertThat(shape(owner)).isEqualTo("b");
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(moves()).as("within its window: no TAB_MOVED").isEmpty();
            owner.selectParentTab(first);
            assertThat(shape(owner)).isEqualTo("H[a, c]");
        });
    }

    /**
     * Workspace plan 8c Rulings 17: the selected item's pane dropped on a new-tab place of its window is announced once,
     * where it lands: the registry hears no selection, move, close or active pane, and no item of its old tab turns active.
     */
    @Test void aPaneDroppedOnANewTabPlaceIsAnnouncedOnceWhereItLands() throws Exception {
        edt(() -> {
            var heard = new ArrayList<Boolean>();
            Tab shell = shellBesideC(heard);
            relayout(frame);
            Point after = onEntry(owner, d, 0.9);
            Component source = startPane(owner, shell, after);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.NewTab(owner, 2));
            facts.clear();
            heard.clear();
            mouse(source, MouseEvent.MOUSE_RELEASED, after, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.workspace().tabs()).as("its own tab, shown").containsExactly(shell);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(heard).as("no item it left turns active").isEmpty();
        });
    }

    /** Workspace plan 8c Rulings 8: in its own window a tab's only pane takes no new tab, at any new-tab place. */
    @Test void theOnlyPaneOfATabTakesNoNewTabInItsWindow() throws Exception {
        edt(() -> {
            owner.selectParentTab(second);
            Tab shownThere = owner.parentTabs().view(first).orElseThrow().selected();
            Point end = onEntry(owner, d, 0.9), start = onEntry(owner, shownThere, 0.1);
            Component source = startPane(owner, d, end);
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(owner.tabDrag().target()).as("after the last tab").isEmpty();
            mouse(source, MouseEvent.MOUSE_DRAGGED, start, PANE);
            assertThat(owner.tabDrag().target()).as("before the first").isEmpty();
            assertThat(owner.tabDrag().overlay().getParent()).as("no landing").isNull();
            assertThat(owner.activeParentTab()).as("an outer quarter shows no tab").isEqualTo(second);
            mouse(source, MouseEvent.MOUSE_RELEASED, start, PANE);
            assertThat(owner.parentTabCount()).isEqualTo(2);
            assertThat(owner.activeParentTab()).isEqualTo(second);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3: under Enterprise the "+", the room after the last tab and a
     * tab's outer quarter make a new tab; the tab's middle shows its tab and takes no drop.
     */
    @Test void underEnterpriseThePlusTheRoomAfterTheTabsAndATabsOuterQuarterMakeANewTab() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                WindowContent classic = content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.connectContributions(model);
                Tab shell = classic.selectedTab();
                Tab notes = model.tabs().open("dev.example.notes", "notes", "notes", classic.id(), new JPanel(),
                    TabRegistry.Placement.SPLIT_RIGHT);
                frame(classic, 100, 50, 1208, 760);
                var strip = (ClassicTabs) classic.tabBar();
                TabDrag drag = classic.tabDrag();
                Point plus = middle(strip.add());
                Component source = startPane(classic, notes, plus);
                assertThat(drag.target()).as("the +").contains(new TabDrag.Target.NewTab(classic, 1));
                Rectangle entry = onScreen(strip, strip.entries().getFirst().getBounds());
                mouse(source, MouseEvent.MOUSE_DRAGGED, inside(entry, 0.5, 0.5), PANE);
                assertThat(drag.target()).as("its tab's middle").isEmpty();
                mouse(source, MouseEvent.MOUSE_DRAGGED, inside(entry, 0.1, 0.5), PANE);
                assertThat(drag.target()).as("its tab's leading quarter").contains(new TabDrag.Target.NewTab(classic, 0));
                Point after = new Point(plus.x + strip.add().getWidth(), plus.y);
                mouse(source, MouseEvent.MOUSE_DRAGGED, after, PANE);
                assertThat(drag.target()).as("the room after the +").contains(new TabDrag.Target.NewTab(classic, 1));
                mouse(source, MouseEvent.MOUSE_RELEASED, after, PANE);
                assertThat(classic.parentTabCount()).isEqualTo(2);
                assertThat(classic.parentTabs().model().indexOf(classic.activeParentTab())).as("at the end, shown").isEqualTo(1);
                assertThat(classic.selectedTab()).isSameAs(notes);
                classic.selectTab(shell);
                assertThat(classic.workspace().tabs()).containsExactly(shell);
            } finally {
                closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
""", 1)])
edit(W + "TabDragWindowsTest.java", [("""            assertThat(from.workspace().tabs()).contains(moving);
        });
    }
}
""", """            assertThat(from.workspace().tabs()).contains(moving);
        });
    }

    /**
     * Tabs and panes spec 7 and workspace plan 8c Rulings 3 and 12: a pane dropped on the leading quarter of another
     * window's first entry becomes that window's first tab, shown, through the tab registry, one TAB_MOVED, under an id no
     * other window holds.
     */
    @Test void aPaneDroppedOnAnotherWindowsEntryQuarterBecomesANewTabThere() throws Exception {
        ParentTabId[] ids = new ParentTabId[2];
        edt(() -> {
            main.content().split(Side.RIGHT);
            relayout(frameOf(main));
            ids[0] = child.content().activeParentTab();
            ids[1] = secondTab(child);
        });
        launch();
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab moving = from.workspace().tabs().get(1);
            Point before = onEntry(to, entryOf(to, ids[0]), 0.1);
            facts.clear();
            Component source = startPane(from, moving, before);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.NewTab(to, 0));
            assertThat(manager.tabDrag().overlay().placeholder()).as("a bar before the first tab").isEqualTo(marker(to, null, 0));
            mouse(source, MouseEvent.MOUSE_RELEASED, before, PANE);
            List<ParentTabId> after = tabIds(to);
            assertThat(after).hasSize(3);
            assertThat(after.get(0)).as("first, shown").isEqualTo(to.activeParentTab());
            assertThat(after.subList(1, 3)).containsExactly(ids[0], ids[1]);
            assertThat(to.workspace().tabs()).containsExactly(moving);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(moving.id().value(), main.id(), child.id()));
            assertThat(after).as("no id in two windows").doesNotContainAnyElementsOf(tabIds(from));
        });
    }

    /** Review Focus 5 and workspace plan 8c Rulings 10: the main window's empty workspace takes a pane anywhere over it, in its place. */
    @Test void theMainWindowsEmptyWorkspaceTakesAPaneAnywhereOverIt() throws Exception {
        edt(() -> {
            child.content().split(Side.DOWN);
            relayout(frameOf(child));
        });
        launch();
        edt(() -> {
            WindowContent empty = main.content(), from = child.content();
            empty.closeTab(empty.selectedTab());
            assertThat(empty.parentTabs().isEmpty()).isTrue();
            ParentTabId only = empty.activeParentTab();
            Tab moving = from.workspace().tabs().get(1);
            var parents = empty.parentTabs();
            Point anywhere = inside(onScreen(parents, new Rectangle(parents.getSize())), 0.3, 0.7);
            Component source = startPane(from, moving, anywhere);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.NewTab(empty, 0));
            assertThat(manager.tabDrag().overlay().highlight()).as("all of the empty workspace").isEqualTo(SwingUtilities
                .convertRectangle(parents, new Rectangle(parents.getSize()), SwingUtilities.getRootPane(empty).getLayeredPane()));
            mouse(source, MouseEvent.MOUSE_RELEASED, anywhere, PANE);
            assertThat(empty.workspace().tabs()).containsExactly(moving);
            assertThat(tabIds(empty)).as("in its one tab's place, under its id").containsExactly(only);
            assertThat(from.workspace().tabs()).hasSize(1);
        });
    }
}
""", 1)])
edit(W + "WindowTabsTest.java", [("""    static JComponent named(Container parent, String name) {
""", """    /** Workspace plan 8c Rulings 3: an equal entry's outer quarters are new-tab places, before and after it. */
    @Test void anEntrysOuterQuartersAreNewTabPlaces() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
            owner.newTab(HOME); owner.newTab(HOME); owner.update();
            var strip = owner.windowTabs();
            layout(strip, 900, 30);
            assertThat(strip.tabBounds()).containsExactly(new Rectangle(0, 0, 300, 30), new Rectangle(300, 0, 300, 30),
                new Rectangle(600, 0, 300, 30));
            assertThat(strip.newTabPlace(new Point(10, 15))).hasValue(0);
            assertThat(strip.newTabPlace(new Point(150, 15))).as("the middle half shows its tab").isEmpty();
            assertThat(strip.newTabPlace(new Point(290, 15))).hasValue(1);
            assertThat(strip.newTabPlace(new Point(310, 15))).hasValue(1);
            assertThat(strip.newTabPlace(new Point(899, 15))).as("the strip's end").hasValue(3);
        });
    }

    static JComponent named(Container parent, String name) {
""", 1)])
edit(W + "ClassicTabsTest.java", [("""    @Test void theDefaultLookKeepsTheEqualTabs() throws Exception {
""", """    /**
     * Workspace plan 8c Rulings 3: the "+" and the room after the last tab are the strip's end, a tab's outer quarters the
     * places before and after it; a crowded strip's end is from its "+" on.
     */
    @Test void thePlusTheRoomAfterTheTabsAndATabsOuterQuartersAreNewTabPlaces() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.newTab(DesktopTestSupport.HOME);
                owner.update();
                var strip = tabs(owner, 800);
                var first = strip.entries().getFirst().getBounds();
                var plus = strip.add().getBounds();
                assertThat(strip.newTabPlace(new java.awt.Point(plus.x + plus.width / 2, plus.height / 2))).as("the +").hasValue(2);
                assertThat(strip.newTabPlace(new java.awt.Point(790, 5))).as("the room after it").hasValue(2);
                assertThat(strip.newTabPlace(new java.awt.Point(first.x + 1, first.height / 2))).as("a tab's leading quarter").hasValue(0);
                assertThat(strip.newTabPlace(new java.awt.Point(first.x + first.width / 2, first.height / 2))).as("its middle").isEmpty();
                assertThat(strip.newTabPlace(new java.awt.Point(900, 5))).as("off the strip").isEmpty();
                for (int i = owner.tabCount(); i < 15; i++) owner.newTab(DesktopTestSupport.HOME);
                owner.update();
                tabs(owner, 600);
                var crowded = strip.add().getBounds();
                assertThat(strip.entries().getLast().getX()).as("crowded: the last tabs clip").isGreaterThan(600);
                assertThat(strip.newTabPlace(new java.awt.Point(crowded.x + crowded.width / 2, crowded.height / 2)))
                    .as("the +, over the clipped tabs").hasValue(15);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theDefaultLookKeepsTheEqualTabs() throws Exception {
""", 1)])
edit(W + "WindowTabMotionTest.java", [("""    private Fixture fixture() {
""", """    /** Workspace plan 8c Rulings 9: a pane dropped on a new-tab place makes a tab that takes its place at once; an opened one still moves. */
    @Test void aPaneDroppedAsANewTabTakesItsPlaceAtOnce() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME);
            owner.split(dev.jasper.app.layout.Side.RIGHT);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            var strip = owner.windowTabs();
            assertThat(strip.motionRunning()).as("settled").isFalse();
            owner.movePaneToNewTab(owner.selectedTab(), owner.parentTabCount());
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(strip.motionRunning()).as("the new tab lands at once").isFalse();
            owner.newTab(HOME);
            assertThat(strip.motionRunning()).as("an opened tab still moves").isTrue();
        });
    }

    /** Workspace plan 8c Rulings 9: Move Pane to New Tab, a command, still opens its tab with the motion. */
    @Test void movePaneToNewTabStillMoves() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.newTab(HOME);
            owner.split(dev.jasper.app.layout.Side.RIGHT);
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            owner.moveToNewTab(owner.selectedTab());
            assertThat(owner.parentTabCount()).isEqualTo(3);
            assertThat(owner.windowTabs().motionRunning()).isTrue();
        });
    }

    private Fixture fixture() {
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.dock.*' --continue`
Expected: FAIL: compilation (`WorkspaceDropResolver.newTabPlace`, `TabStrip.newTabPlace`, `TabDrag.Target.NewTab`,
`WindowContent.movePaneToNewTab` do not exist).

- [ ] **Step 3: Implement**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java", [
    (""" * Where a drag would drop, from the cursor alone (workspace tabs spec 12; tabs and panes spec 6 and 7): a dragged pane
 * beside the pane under the cursor, on the side whose edge is nearest; a dragged tab's place among a strip's entries; the
 * rectangles that show the landing; and which of several windows takes the drop. The tab and pane drag's twin of
 * {@link DockDropResolver}. Pure; every rectangle and the cursor are in the caller's one coordinate space.
""", """ * Where a drag would drop, from the cursor alone (workspace tabs spec 12; tabs and panes spec 6 and 7): a dragged pane
 * beside the pane under the cursor, on the side whose edge is nearest, or into a new tab at a strip entry's outer
 * quarter; a dragged tab's place among a strip's entries; the rectangles that show the landing; and which of several
 * windows takes the drop. The tab and pane drag's twin of {@link DockDropResolver}. Pure; every rectangle and the cursor
 * are in the caller's one coordinate space.
""", 1),
    ("""        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return OptionalInt.of(index);
    }
""", """        int index = 0;
        for (Rectangle entry : entries) if (entry.x + entry.width / 2.0 < cursor.x) index++;
        return OptionalInt.of(index);
    }

    /**
     * Where a dragged pane over a strip's entries becomes a new tab (tabs and panes spec 7; workspace plan 8c Rulings 3):
     * over an entry's leading quarter, the place before it, and over its trailing quarter, the place after it; over its
     * middle half, which shows its tab, and anywhere off the entries, none. An entry's left and top edges lie inside it,
     * its right and bottom edges do not.
     *
     * @param cursor the cursor
     * @param entries the strip's entries in strip order
     * @return the place, 0 to the number of entries; empty over an entry's middle half or off the entries
     */
    public static OptionalInt newTabPlace(Point cursor, List<Rectangle> entries) {
        Objects.requireNonNull(cursor, "cursor");
        for (int i = 0; i < entries.size(); i++) {
            Rectangle entry = entries.get(i);
            if (!entry.contains(cursor)) continue;
            int quarter = entry.width / 4, across = cursor.x - entry.x;
            if (across < quarter) return OptionalInt.of(i);
            if (across >= entry.width - quarter) return OptionalInt.of(i + 1);
            return OptionalInt.empty();
        }
        return OptionalInt.empty();
    }
""", 1)])
S = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(S + "TabStrip.java", [
    ("import java.awt.Rectangle;\nimport java.util.List;\n",
     "import java.awt.Point;\nimport java.awt.Rectangle;\nimport java.util.List;\nimport java.util.OptionalInt;\n", 1),
    ("""    List<Rectangle> tabBounds();
""", """    List<Rectangle> tabBounds();
    /**
     * Where a dragged pane at {@code point}, in this strip's coordinates, becomes a new tab (tabs and panes spec 7;
     * workspace plan 8c Rulings 3): an entry's leading quarter is the place before it and its trailing quarter the place
     * after it, and under the classic tabs the "+" and the room after the last entry are the end; empty over an entry's
     * middle half, which shows its tab, and off the strip.
     */
    OptionalInt newTabPlace(Point point);
""", 1)])
edit(S + "WindowTabs.java", [
    ("import dev.jasper.app.contributions.Tab;\n", "import dev.jasper.app.contributions.Tab;\nimport dev.jasper.app.dock.WorkspaceDropResolver;\n", 1),
    ("import java.util.List;\nimport java.util.function.LongSupplier;\n",
     "import java.util.List;\nimport java.util.OptionalInt;\nimport java.util.function.LongSupplier;\n", 1),
    ("""    @Override public List<Rectangle> tabBounds() {
        var bounds = new ArrayList<Rectangle>(order.size());
        for (Tab tab : order) bounds.add(SwingUtilities.convertRectangle(viewport, entries.get(tab).getBounds(), this));
        return bounds;
    }
""", """    @Override public List<Rectangle> tabBounds() {
        var bounds = new ArrayList<Rectangle>(order.size());
        for (Tab tab : order) bounds.add(SwingUtilities.convertRectangle(viewport, entries.get(tab).getBounds(), this));
        return bounds;
    }

    @Override public OptionalInt newTabPlace(Point point) { return WorkspaceDropResolver.newTabPlace(point, tabBounds()); }
""", 1)])
edit(S + "ClassicTabs.java", [
    ("import dev.jasper.app.contributions.Tab;\n", "import dev.jasper.app.contributions.Tab;\nimport dev.jasper.app.dock.WorkspaceDropResolver;\n", 1),
    ("import java.awt.Graphics2D;\nimport java.awt.Rectangle;\n", "import java.awt.Graphics2D;\nimport java.awt.Point;\nimport java.awt.Rectangle;\n", 1),
    ("import java.util.ArrayList;\nimport java.util.List;\n", "import java.util.ArrayList;\nimport java.util.List;\nimport java.util.OptionalInt;\n", 1),
    ("""    @Override public List<Rectangle> tabBounds() { return entries.stream().map(Entry::getBounds).toList(); }
""", """    @Override public List<Rectangle> tabBounds() { return entries.stream().map(Entry::getBounds).toList(); }

    @Override public OptionalInt newTabPlace(Point point) {
        if (!new Rectangle(getSize()).contains(point)) return OptionalInt.empty();
        int end = entries.isEmpty() ? 0 : entries.getLast().getX() + entries.getLast().getWidth();
        // The "+" and the room after the last tab; a crowded strip's "+" stands over the tabs it clips.
        if (point.x >= (add.covering ? add.getX() : end)) return OptionalInt.of(entries.size());
        return WorkspaceDropResolver.newTabPlace(point, tabBounds());
    }
""", 1)])
edit(S + "ParentTabBar.java", [
    ("""    private final TabStrip strip;
""", """    private final TabStrip strip;
    /** While {@link #atOnce} runs: the strip takes every change at once. */
    private boolean still;
""", 1),
    ("""    /** Whether the strip's entries move as tabs open and close: while the look has motion (Rulings 20). */
    boolean animates() { return owner.lookAnimates(); }
""", """    /**
     * Runs {@code change} with the strip taking its places at once, as it takes a tab moving in from another window: a
     * pane dropped on a new-tab place makes a tab that opens with no motion (tabs and panes spec 5 and 7; workspace plan
     * 8c Rulings 9).
     */
    void atOnce(Runnable change) {
        boolean was = still;
        still = true;
        try {
            change.run();
        } finally {
            still = was;
        }
    }
    /** Whether the strip's entries move as tabs open and close: while the look has motion (Rulings 20), outside {@link #atOnce}. */
    boolean animates() { return !still && owner.lookAnimates(); }
""", 1)])
edit(S + "ParentTabsView.java", [("""    void moveToNewTab(Tab tab) {
        WorkspaceView from = holding(tab).orElse(null);
        if (closed || from == null || from.tabCount() < 2) return;
        int at = model.indexOf(from.id()) + 1;
        Layout before = from.model();
        ParentTabs strip = model;
        ParentTabId id = ParentTabId.random();
        owner.held(() -> {
            try {
                from.remove(tab);
                openParentTab(tab, id, at);
            } catch (RuntimeException | Error failure) {
                // A new tab that stood when the failure came goes again before its item goes back.
                WorkspaceView opened = views.remove(id);
                if (opened != null) {
                    model = strip;
                    try {
                        super.remove(opened);
                        opened.close();
                        reveal(active());
                    } catch (RuntimeException | Error undone) {
                        failure.addSuppressed(undone);
                    }
                }
                undo(failure, tab, from, before, null, null);
                throw failure;
            }
        });
    }
""", """    void moveToNewTab(Tab tab) {
        WorkspaceView from = holding(tab).orElse(null);
        if (from != null) moveToNewTab(tab, model.indexOf(from.id()) + 1);
    }

    /**
     * As {@link #moveToNewTab(Tab)}, the new tab at {@code index} of the strip (its end past it): a pane dropped on a
     * new-tab place of the strip (tabs and panes spec 7), held as that one is (workspace plan 8c Rulings 17).
     */
    void moveToNewTab(Tab tab, int index) {
        WorkspaceView from = holding(tab).orElse(null);
        if (closed || from == null || from.tabCount() < 2) return;
        Layout before = from.model();
        ParentTabs strip = model;
        ParentTabId id = ParentTabId.random();
        owner.held(() -> {
            try {
                from.remove(tab);
                openParentTab(tab, id, index);
            } catch (RuntimeException | Error failure) {
                // A new tab that stood when the failure came goes again before its item goes back.
                WorkspaceView opened = views.remove(id);
                if (opened != null) {
                    model = strip;
                    try {
                        super.remove(opened);
                        opened.close();
                        reveal(active());
                    } catch (RuntimeException | Error undone) {
                        failure.addSuppressed(undone);
                    }
                }
                undo(failure, tab, from, before, null, null);
                throw failure;
            }
        });
    }
""", 1)])
edit(S + "WindowContent.java", [
    ("""    TabRegistry tabRegistry() { return registry; }
""", """    /**
     * Takes {@code tab}, another window's item, into a new tab at {@code index} of this window's strip (its end past it),
     * through the tab registry, one move, its content and session untouched (tabs and panes spec 7; workspace plan 8c
     * Rulings 12): a pane dropped on a new-tab place of this window's strip. A tab moving in takes its place at once.
     * Nothing once the window has closed; a receive that runs within another leaves the outer one's landing as it was.
     *
     * @param tab another window's item
     * @param index where the new tab goes among the strip's entries
     */
    void receiveAsTab(Tab tab, int index) {
        if (closed) return;
        Landing outer = landing;
        landing = new Landing(ParentTabId.random(), index);
        try {
            registry.move(tab, id);
        } finally {
            landing = outer;
        }
    }
    TabRegistry tabRegistry() { return registry; }
""", 1),
    ("""        parentTabs.moveBeside(tab, group, side);
        selectTab(tab);
    }
""", """        parentTabs.moveBeside(tab, group, side);
        selectTab(tab);
    }

    /**
     * A pane drag's drop on a new-tab place of this window's strip (tabs and panes spec 7): Move Pane to New Tab's move,
     * the new tab at {@code index} of the strip, which takes it at once (workspace plan 8c Rulings 9); the item then takes
     * the focus. Nothing when {@link #mayMoveToNewTab} says no, as for its tab's only pane (Rulings 8).
     *
     * @param tab an item of this window
     * @param index where the new tab goes among the strip's entries
     */
    void movePaneToNewTab(Tab tab, int index) {
        if (!mayMoveToNewTab(tab)) return;
        parentTabs.bar().atOnce(() -> parentTabs.moveToNewTab(tab, index));
        selectTab(tab);
    }
""", 1)])
edit(S + "WindowManager.java", [("""    /** Whether {@code tab}, an item of {@code from}, may move to {@code to}, another open window: one the registry moves. */
""", """    /**
     * A pane drag's drop on a new-tab place of {@code to}'s strip, or on the main window's empty workspace (tabs and panes
     * spec 7; workspace plan 8c Rulings 10 and 12): as {@link #movePane}, into a new tab at {@code index} of {@code to}'s
     * strip; the main window's empty workspace takes it in its place, under its own id.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another main or full window
     * @param index where the new tab goes among {@code to}'s strip entries
     * @return whether it arrived
     */
    public boolean movePaneToNewTab(WindowContent from, Tab tab, WindowContent to, int index) {
        requireEdt();
        if (!mayMovePane(from, tab, to)) return false;
        if (to.parentTabs().isEmpty()) model.tabs().move(tab, to.id());
        else to.receiveAsTab(tab, index);
        return to.parentTabs().contains(tab);
    }

    /** Whether {@code tab}, an item of {@code from}, may move to {@code to}, another open window: one the registry moves. */
""", 1)])
P = S + "TabDrag.java"
edit(P, [
    (""" * <p>A pane's targets (tabs and panes spec 7): a pane of the window's shown tab other than its own, beside which it goes
 * on the side whose edge the cursor is nearest; and a tab's entry in the window's strip, which shows that tab at once,
 * with no drop of its own, so that one of its panes can take the pane; the tab that showed there before shows again when
 * the drag ends, unless its drop lands in that window (spec 13). A pane takes targets in windows alone, as a pane never
 * becomes a window; the drag of an item that is pinned or being closed never starts.
""", """ * <p>A pane's targets (tabs and panes spec 7): a pane of the window's shown tab other than its own, beside which it goes
 * on the side whose edge the cursor is nearest; the middle half of a tab's entry in the window's strip, which shows that
 * tab at once, with no drop of its own, so that one of its panes can take the pane; the tab that showed there before
 * shows again when the drag ends, unless its drop lands in that window (spec 13); a new-tab place in the strip
 * ({@link TabStrip#newTabPlace}: an entry's outer quarter, the classic tabs' "+" and the room after them), where it
 * becomes a new tab, except in its own window for its tab's only pane, which Move Pane to New Tab leaves where it is
 * too; and the main window's empty workspace, anywhere over it, a new tab in its place. A pane takes targets in windows
 * alone, as a pane never becomes a window; the drag of an item that is pinned or being closed never starts.
""", 1),
    ("""        record Beside(WindowContent window, GroupId group, Side side) implements Target { }
    }
""", """        record Beside(WindowContent window, GroupId group, Side side) implements Target { }

        /**
         * A new tab at {@code index} of {@code window}'s strip, or the main window's empty workspace in its place (index
         * 0): a pane drag's (tabs and panes spec 7).
         *
         * @param window the window
         * @param index the new tab's place among the strip's entries
         */
        record NewTab(WindowContent window, int index) implements Target { }
    }
""", 1),
    ("""    /**
     * A pane's target in {@code window} at {@code at} (tabs and panes spec 7): over the shown strip, an entry, whose tab
     * shows at once and takes no drop itself; over a pane of the shown tab other than the dragged one's own, beside it on
     * the side whose edge is nearest (workspace plan 8c Rulings 6 and 7). Empty anywhere else (the window's dock, chrome
     * and title bar, a divider, the dragged pane itself) and for an item that may no longer move.
     */
""", """    /**
     * A pane's target in {@code window} at {@code at} (tabs and panes spec 7): the main window's empty workspace anywhere
     * over it, a new tab in its place (workspace plan 8c Rulings 10); over the shown strip, a new-tab place
     * ({@link TabStrip#newTabPlace}), a new tab there, but not in the drag's own window for its tab's only pane (Rulings
     * 8), or an entry's middle half, whose tab shows at once and takes no drop itself; over a pane of the shown tab other
     * than the dragged one's own, beside it on the side whose edge is nearest (Rulings 6 and 7). Empty anywhere else (the
     * window's dock, chrome and title bar, a divider, the dragged pane itself) and for an item that may no longer move.
     */
""", 1),
    ("""        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        Component strip = window.tabBar().component();
""", """        if (!new Rectangle(parents.getSize()).contains(local)) return Optional.empty();
        if (parents.isEmpty()) return Optional.of(new Target.NewTab(window, 0));
        Component strip = window.tabBar().component();
""", 1),
    ("""            Point inStrip = SwingUtilities.convertPoint(parents, local, strip);
            List<Rectangle> entries = window.tabBar().tabBounds();
""", """            Point inStrip = SwingUtilities.convertPoint(parents, local, strip);
            OptionalInt place = window.tabBar().newTabPlace(inStrip);
            if (place.isPresent())
                return window != from || from.mayMoveToNewTab(tab) ? Optional.of(new Target.NewTab(window, place.getAsInt())) : Optional.empty();
            List<Rectangle> entries = window.tabBar().tabBounds();
""", 1),
    ("""    /** The strip's entries in {@code window}'s parent-tabs view's coordinates, in strip order, without the dragged one's own. */
""", """    /** The strip's entries in {@code window}'s parent-tabs view's coordinates, in strip order, without a dragged tab's own. */
""", 1),
    ("""            if (listed.get(i) == tab) continue;
""", """            if (!pane && listed.get(i) == tab) continue;
""", 1),
    ("""     * placeholder, or the band of a strip that hides as the highlight; for a pane, the half of the pane beside which it
     * would go as the highlight; the main window's whole empty workspace for what drops there. Without a target in a
     * window the overlay leaves its window.
""", """     * placeholder, or the band of a strip that hides as the highlight; for a pane, the half of the pane beside which it
     * would go as the highlight, or a bar at the new-tab place where it would become a tab as the placeholder; the main
     * window's whole empty workspace for what drops there. Without a target in a window the overlay leaves its window.
""", 1),
    ("""            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents, besideLanding(parents, beside), layers);
""", """            case Target.Beside beside -> highlight = SwingUtilities.convertRectangle(parents, besideLanding(parents, beside), layers);
            // A pane becoming a new tab: a bar at its place, as the tab drag marks a tab's.
            case Target.NewTab newTab -> placeholder = SwingUtilities.convertRectangle(parents,
                WorkspaceDropResolver.marker(barArea(window), barEntries(window), newTab.index(), marker), layers);
""", 1),
    ("""     * whole ({@code home}, the drag's window, holds {@code parent}). A pane beside another pane of its window moves there
     * as a layout change; beside another window's, the window manager moves its item there (workspace plan 8c Rulings
     * 12). A window no manager opened moves nothing out of itself.
""", """     * whole ({@code home}, the drag's window, holds {@code parent}). A pane beside another pane of its window, or onto a
     * new-tab place of its strip, moves there as a layout change; beside another window's pane, onto a new-tab place of
     * its strip or onto the main window's empty workspace, the window manager moves its item there (workspace plan 8c
     * Rulings 12). A window no manager opened moves nothing out of itself.
""", 1),
    ("""                else home.manager().ifPresent(manager -> manager.movePane(home, moving, beside.window(), beside.group(), beside.side()));
            }
        }
    }
""", """                else home.manager().ifPresent(manager -> manager.movePane(home, moving, beside.window(), beside.group(), beside.side()));
            }
            case Target.NewTab newTab -> {
                if (newTab.window() == home) home.movePaneToNewTab(moving, newTab.index());
                // Another window's strip, or the main window's empty workspace: through the window manager and the registry.
                else home.manager().ifPresent(manager -> manager.movePaneToNewTab(home, moving, newTab.window(), newTab.index()));
            }
        }
    }
""", 1),
    ("""            case Target.Beside beside -> beside.window();
""", """            case Target.Beside beside -> beside.window();
            case Target.NewTab newTab -> newTab.window();
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.dock.*' --continue`
Expected: PASS: the new cases and Tasks 1 and 2's (Task 2's hovers land on entries' middles), `MovePaneToNewTabTest`
(plan 8b's Move Pane to New Tab, now over `moveToNewTab(Tab, int)`, still opens right after its tab), `TabDragTest` and
`TabDragWindowsTest`'s tab cases (a tab drag's `barEntries` still leaves its own entry out). Then
`./gradlew :jasper-app:check`: PASS (the Javadoc of the public `WorkspaceDropResolver.newTabPlace` and
`WindowManager.movePaneToNewTab`).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Tabs and panes: a pane dropped on a new-tab place becomes a new tab

A pane released on an entry's outer quarter, or under Enterprise on
the "+" or the room after the tabs, becomes a new tab at that place,
which shows and takes its place at once; the main window's empty
workspace takes one anywhere over it. In its own window the only pane
of its tab takes no new tab. Into another window the move goes through
the window manager and the tab registry, one TAB_MOVED. Move Pane to
New Tab keeps its open motion. Within its window the move is announced
once, where it lands, and a pane the new tab fails to take goes back.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Documentation, STATUS and the whole build

The documents say what Tasks 1-3 made true, each edit the smallest that makes its sentence true (AGENTS.md
"Documentation edits"): the README's tab paragraph, the configuration guide's motion sentence, the architecture's
`workspace` row, the maintenance recipe for the drag and the terminal's mouse routing. Then the whole build, and the
STATUS entry.

**Files:**
- Modify: `README.md` (after 8b `:113`), `docs/configuration.md` (`:881-882`), `docs/app-architecture.md` (`:179`),
  `docs/app-maintenance.md` (after `:712`), `docs/terminal-architecture.md` (`:106`), `docs/STATUS.md` (a new entry at
  the top), this plan's status banner (line 3)

**Interfaces:**
- Consumes: every task's names, as they landed.
- Produces: the STATUS entry the 6a and 6b re-plans' writers read.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
edit("README.md", [(
    "which moves along its strip only); Esc cancels. Tabs share the strip's width.",
    "which moves along its strip only); Esc cancels. Hold Cmd+Option (Ctrl+Alt on Linux and Windows) and drag inside a "
    "pane to move it: onto another pane of any window, which it splits on the side nearest the cursor; over the middle of "
    "a tab's entry, which shows that tab for the drop; or onto the outer quarter of a tab's entry, or under Enterprise its "
    "\"+\" or the room after the tabs, where it becomes a new tab at that place. A pane never becomes a window, and Esc "
    "cancels. On Windows hold the left Alt key (the right one is AltGr); a Linux desktop that moves windows with "
    "Ctrl+Alt-drag keeps that gesture for itself. Tabs share the strip's width.", 1)])
edit("docs/configuration.md", [(
    """Enterprise look it always shows, and its tabs never move); a tab dragged along it, moved between windows or showing
another of its panes takes its new place at once.
""", """Enterprise look it always shows, and its tabs never move); a tab dragged along it, moved between windows, made from a
dragged pane or showing another of its panes takes its new place at once.
""", 1)])
edit("docs/app-architecture.md", [(
    "the tab drag (`TabDrag`, the window manager's one, shared by its windows, for parent tabs)",
    "the tab and pane drag (`TabDrag`, the window manager's one, shared by its windows)", 1)])
edit("docs/app-maintenance.md", [(
    """`TabDragWindowsTest` drive it headlessly over stand-in frames on a pretend screen (`TabDragTestSupport`).
""", """`TabDragWindowsTest` drive it headlessly over stand-in frames on a pretend screen (`TabDragTestSupport`).

A pane drags with the same `TabDrag`: a window's `paneClicks` hands it a left press with `TabDrag.movesPane`'s modifier
(Cmd+Option, Ctrl+Alt elsewhere) on an item of its shown tab, consumed, and the drag's gesture listener consumes that
press's drags and release, so no item hears the gesture (`TerminalView` leaves a consumed event alone). It drops beside
a pane of a shown tab (`WorkspaceDropResolver.resolve`, the nearest side), shows a hovered entry's tab until it ends
elsewhere, and makes a new tab at a strip's new-tab place (`TabStrip.newTabPlace`: an entry's outer quarter, the classic
"+" and the room after it) or on the main window's empty workspace; within its window `WindowContent.movePane` and
`movePaneToNewTab` move it, into another `WindowManager.movePane` and `movePaneToNewTab`, through the tab registry. A
text view's text drag sits out the gesture. `PaneDragTest` and `TabDragWindowsTest` drive it.
""", 1)])
edit("docs/terminal-architecture.md", [(
    """Shift bypasses application mouse capture. Fractional wheel motion accumulates.
""", """Shift bypasses application mouse capture. Fractional wheel motion accumulates.
A mouse event an earlier listener consumed, such as the application's pane drag,
is not the terminal's: the view neither reports nor selects with it.
""", 1)])
PY
```

- [ ] **Step 2: Add the STATUS entry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8c")
from edits import edit
edit("docs/STATUS.md", [("# Jasper — Status and Handoff\n\n", """# Jasper — Status and Handoff

**Workspace tabs, plan 8c: moving panes — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-20-workspace-8c-moving-panes.md),
[spec](superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md)). Holding Cmd+Option (Ctrl+Alt on Linux and
Windows) and dragging inside a pane moves it, the iTerm way: the tab drag (`TabDrag`) carries a pane too. A window's
`paneClicks` hands it the modifier press on an item of its shown tab, consumed, and the drag's gesture listener consumes
that press's drags and release, so no item hears any of them, whatever ends the drag; `TerminalView` leaves a consumed
mouse event alone, so a terminal neither reports nor selects, and a text view's text drag sits out the gesture. A pane
drops beside another pane of any window's shown tab, on the side whose edge is nearest (`WorkspaceDropResolver`, now
`Area(group, bounds)` and `Drop(group, side)`; `Drop.Strip` and `Drop.Centre` are gone), the landing showing that half;
over the middle half of a tab's entry it shows that tab at once, and the tabs a drag showed show again when it ends
elsewhere; on a new-tab place (`TabStrip.newTabPlace`: an entry's outer quarter in either look, the classic tabs' "+"
and the room after them) it becomes a new tab there, at once; the main window's empty workspace takes one anywhere over
it. Within its window a move is a layout change (`ParentTabsView.moveBeside`, `moveToNewTab(Tab, int)`), with no
`TAB_MOVED`; between its tabs it is announced once, where it lands, in one terminal-registry change, and a pane that
fails to land goes back (`WindowContent.held`, `WorkspaceView.restore`), Move Pane to New Tab's included; into another
window it goes through `WindowManager.movePane` and `movePaneToNewTab` and the tab registry, one `TAB_MOVED`, the
session kept, a full window it empties closing after the move. Outside every window nothing happens; a pinned item's
drag never starts. Rulings (plan, "Rulings"): (1) the modifier is exactly Cmd+Option (either Option key) or Ctrl+Alt,
with the left button; (2) a pane's gesture is the drag's from its press to its release, a new left press ending one
whose release went elsewhere, and the terminal view leaves a consumed event alone; (3) an entry's outer quarters are
new-tab places in either look, and the classic tabs' "+" and the room after them the strip's end; (4) hovering an
entry's middle half shows its tab at once, its item focused only in the drag's own window; (5) the tabs a drag showed
show again when it ends, but in the drop's window; (6) a pane drops on the nearest side, with no centre; (7) where it
already is means its own pane and its own tab's entry's middle half; (8) in its own window a tab's only pane takes no
new tab at any place; (9) Move Pane to New Tab, a command, keeps the strip's open motion, and a tab a pane drop makes
lands at once; (10) the main window's empty workspace takes a pane anywhere over it; (11) a pinned item's drag never
starts; (12) moves within a window skip the registry, moves between windows go through the window manager and the
registry; (13) the resolver keeps the pane drag's shape; (14) a modifier press makes its pane the active one; (15) no
action is added; (16) a text view's text drag sits out the gesture; (17) a pane moving between tabs of its window is
announced once, where it lands, and goes back if its landing fails. Spec issues: (1) the equal tabs have no empty end
and no "+"; ruled by the controller: an entry's outer quarters are new-tab places in either look. Deviations: (1)
amendment 1, from plan 8b's final review (I2, left for this plan): plan 8b's Move Pane to New Tab announced its old
tab's next pane on the way; Task 2 holds it with the pane drops, so its case comes before Task 3; <any other, each with
its cause>. `./gradlew check --continue`: **<counts>**.
GUI checks for the user:
- Cmd+Option-drag (Ctrl+Alt on Linux and Windows) a terminal pane onto another pane of its tab, then of another window:
  it splits beside that pane on the side nearest the cursor, the landing showing where, and the shell keeps running;
- the same drag in a terminal running tmux or vim with the mouse on, with Esc mid-drag before the release: nothing
  reaches the program and no selection starts; without the modifier a drag selects or reaches the program as before;
- an editor pane with a selection: a Cmd+Option-drag over the selection moves the pane and never drags the text; a
  plain drag still selects or drags the text; with `vim = true`, in Visual mode, a Cmd+Option-drag over the selection
  moves the pane and never moves the text;
- Cmd and the right Option key drag a pane on macOS; on Windows the left Alt key with Ctrl does, and AltGr does not;
- hovering the middle of a tab's entry shows that tab at once and dropping on one of its panes moves the pane there;
  Esc, or a release over a title bar, brings back the tab that showed before, in the drag's window and in another
  window;
- dragging a pane over another tab of its own window keeps the drag running (the pressed view hides while the hovered
  tab shows) until the drop;
- a drop on any entry's outer quarter, in either look, or on the "+" or the room after the tabs under Enterprise, makes
  a new tab at that place, with no slide, the bar marking it first; a tab's only pane does nothing there in its own
  window; Move Pane to New Tab still opens its tab with the strip's motion;
- after Move Pane to New Tab, or a drop beside a pane of another tab, typing at once goes to the moved pane, and the
  pane its old tab shows next never takes the focus on the way;
- a full window's last pane dragged into the main window closes the full window; the main window's last pane dragged
  away leaves its empty workspace, which takes a pane dropped anywhere over it;
- outside every window nothing happens and no window opens; on Linux, a desktop that takes Ctrl+Alt-drag for itself
  leaves Jasper no gesture.

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
for name in [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked")
PY
git grep -n -e 'Drop.Strip' -e 'Drop.Centre' -e 'Drop.Edge' -- ':!docs/superpowers' ':!docs/STATUS.md'
```

Expected: `checked` alone, and the `git grep` prints nothing.

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the counts from the XML in `*/build/test-results/test/` and put them in the STATUS entry
in place of `<counts>`, with the dates and commit range.

- [ ] **Step 4: Commit**

```bash
git add -A README.md docs
git commit -F - <<'EOF'
Tabs and panes: moving panes, documentation and status

The README, the configuration guide and the architecture and
maintenance documents describe the pane drag: its modifier, its
targets and its moves; the terminal's architecture says a consumed
mouse event is not the terminal's. STATUS records plan 8c with its
rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

Checked against the spec, the review's findings and the controller's rulings with this plan's text in hand:

- **Spec coverage.** 2.6 and 7's start (the modifier, plan 5's threshold, a plain drag keeping its meaning; the gesture
  reaching neither the shell nor a selection nor a text drag): Task 1, Rulings 1, 2, 14 and 16. 7's targets: another
  pane, in its tab or another window, on the nearest side, the landing showing where (Task 2, Rulings 6 and 12); a tab's
  entry showing its tab (Task 2, Rulings 4 and 5; Task 3 leaves it the middle half); a strip's empty end or its "+", read
  with the controller as every new-tab place (Task 3, Rulings 3, Spec issues 1). 7's "not a target" (Task 2's
  `outsideEveryWindowAPaneDropsNothing`, Task 1's update branch). 7's ends: Esc, focus loss, lost release (Task 1),
  dropped where it is (Tasks 1 and 2, Rulings 7). 7's source tab: its last pane leaving closes it or leaves the main
  window's empty workspace (Task 2's in-window and cross-window cases, Review Focus 5). 7's pinned panes (Task 1,
  Rulings 11). 4's unique ids (Tasks 2 and 3's id assertions). 5's motion (Task 3, Rulings 9, both cases pinned). 2.12
  (no action, Rulings 15; Move Pane to New Tab is plan 8b's). 12 (saving): the format does not change (6a impact). 13's
  maximize and hover rows (Task 2, Review Focus 3 and 4). 16 (headless views and the pretend screen; no action is added,
  so no keymap or collision case is due).
- **Type consistency.** `TabDrag.movesPane(MouseEvent, boolean)` and `pressedPane(WindowContent, Tab, MouseEvent)` (Task
  1) are what `WindowContent.paneClicks` calls, with `WindowContent.macOs()`; `Target.Beside(WindowContent, GroupId,
  Side)` (Task 2) and `Target.NewTab(WindowContent, int)` (Task 3) are what `drop`, `showLanding`, `window(Target)` and
  the tests name; `WorkspaceDropResolver.Area(GroupId, Rectangle)`, `Drop(GroupId, Side)`, `resolve` and `landing`
  (Task 2) are what `onPanes` and `besideLanding` call, and `newTabPlace(Point, List<Rectangle>)` (Task 3) what
  `WindowTabs.newTabPlace` and `ClassicTabs.newTabPlace` call; `WindowContent.movePane(Tab, GroupId, Side)`,
  `movePaneToNewTab(Tab, int)` and `WindowManager.movePane(WindowContent, Tab, WindowContent, GroupId, Side)`,
  `movePaneToNewTab(WindowContent, Tab, WindowContent, int)` are what `drop` calls, the latter over
  `WindowContent.receiveAsTab(Tab, int)`; `TabStrip.newTabPlace(Point)` and `ParentTabBar.atOnce(Runnable)` (Task 3)
  are what `onPanes` and `movePaneToNewTab` call; `WindowContent.held(Runnable)`, `WorkspaceView.restore(Tab, Layout)`
  and `ParentTabsView`'s `undo`, `drop` and `discard` (Task 2) are what plan 8b's `moveToNewTab(Tab)`, `moveBeside` and Task 3's
  `moveToNewTab(Tab, int)` call; `TabDragTestSupport.PANE`, `mouse(Component, int, Point, int)`, `inPane`,
  `startPane`, `dropPane` (Task 1) are what every pane test uses, and `PaneDragTest.shellBesideC` and `announced` (Task
  2) what the announcement cases use.
- **Amendment 1.** Every move between tabs of one window runs held: plan 8b's `moveToNewTab(Tab)` and `moveBeside`'s
  cross-tab branch from Task 2, and `moveToNewTab(Tab, int)` from Task 3, to which plan 8b's then delegates; a move
  within one tab is one layout change already. Each has its RED: the command's and the beside drop's in Task 2, the
  new-tab drop's in Task 3, and the rollback's for both kinds in Task 2. Moves between windows go through the tab
  registry and are not held.
- **Field initializers.** The one new lambda in a field initializer, `paneClicks`'s, reads `macOs()`, `tabDrag()` and
  `dockDrag()`, methods run after the constructor; `TabDrag.gestureEvents` is a method reference; `hovered` and
  `textDrag` read nothing (Verified facts 11).
- **Review Focus.** Each line names its test and its task.
- **Proportion.** The plan carries complete tests and new files, and every edit as an anchor and its replacement, as
  the brief requires; prose decides only names, rules and wording.
