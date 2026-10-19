# Workspace tabs 5: tear-out and drag-back Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One application-wide tab drag replaces the strips' release-to-reorder. A press on any tab that moves past the
kit's threshold drags it, its label in a small window beside the cursor and its landing shown in the target window: to
another place in its strip, into another group's strip or centre, into a new group on any side of a group, into another
Jasper window, or out of every window into a new child window at the cursor, its workspace sized like its old group. A
release over a Jasper window outside its workspace (a dock, chrome, a title bar, Settings), Esc, a lost release, a modal
dialog, another application taking the focus and the tab closing or moving meanwhile cancel it; a drop that would
change nothing does nothing.

**Architecture:** A pure `WorkspaceDropResolver` beside `DockDropResolver` in the JDK-only `dock` package maps the cursor
over a workspace's group areas to a drop, gives the overlay's landing rectangle, and picks the most recently active of
the windows under the cursor. `WorkspaceView` lists its drop areas (the strips report their tabs' rectangles) and carries
a drop out with plan 2's and 3a's layout operations. `TabDrag` (workspace) runs the gesture on plain mouse events in
screen coordinates with `TabDragLabel` (an undecorated `JWindow`) and the existing `DockDropOverlay`; each window starts
with one of its own, and the `WindowManager` gives its own to every window it opens, moves tabs between windows through
the tab registry and opens tear-out windows. Headless tests drive the drag with synthetic events over stand-in frames on
a pretend screen; real drags are GUI checks for the user (spec 19, risk 6).

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1, 3, 4, 7.3's moves and maximize, 9.3, 10's strips, 12, 15's maximize and modal-dialog rows, 17's drop
resolver and GUI checks, and 18). Section 18's plan 5; plans [1](2026-10-17-workspace-1-single-instance.md),
[2](2026-10-17-workspace-2-layout-model.md), [3a](2026-10-17-workspace-3a-tab-registry.md),
[3b](2026-10-18-workspace-3b-sdk-tabs.md), [4a](2026-10-18-workspace-4a-windows.md) and
[4b](2026-10-18-workspace-4b-plugin-windows.md) execute first, in that order.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`,
after plan 4b's last commit (the branch is `main` at `4ed94fb78` plus this series). Run every command from the worktree
root. Every anchor below was re-found at, and every edit script of Tasks 1-5 replayed on, a copy of HEAD `094729d08`
(plan 4b's last commit) by the pre-flight, and line numbers are from it; nothing was compiled or run. Every edit quotes
the text it replaces, and the scripts stop with a message when that text is not found the stated number of times:
re-find the quoted text (a plan before this one may have reworded it), adjust the anchor, re-run, and record it in this
plan's banner; never edit around a missing anchor. Later tasks anchor on text earlier tasks of this plan wrote, and
quote it as those tasks leave it.

## Spec issues

None. Spec 12's "the kit's drag threshold" for tabs is the kit's tab drag role, `TAB_REORDER_THRESHOLD` (Rulings 3),
and its "under the cursor" places the label window just right of and below the pointer's tip (Rulings 6).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 4): the strips' release-to-reorder and `GroupView.reorder` go outright with their tests' uses; no
  shim and no "was removed" text.
- No motion (spec 4): nothing moves during a drag; a drop, a tear-out and a cancel are instant; a moved tab joins its
  strip without the open motion (plan 3a Rulings 27).
- Sessions and content are never touched (spec 9.3): every move is a layout operation of plans 2 and 3a, the tab
  registry's move, or the window manager's move to a new window.
- The SDK does not change: no file under `jasper-sdk`, `jasper-sdk-testkit` or `plugins` changes. A drag between
  windows reports plan 3b's `TAB_MOVED` through the registry's move.
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture` checks the graph is acyclic and
  the JDK-only packages): `dock` gains one edge, to `layout` (Task 1); nothing else gains one (`workspace` already names
  `contributions`, `dock`, `layout` and `lifecycle`).
- The theme-migration ledger: every new Java file matching `ThemeMigrationCoverageTest`'s `CANDIDATE` pattern (all of
  this plan's: they import `java.awt` or `javax.swing`) gets a `docs/design/theme-engine/migration.csv` row in its task's
  commit, through the `ledger.py` helper, `nonvisual-helper,none,none` (plan 4a Rulings 21).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): no `JWindow` or `JFrame`; tests drive `TabDrag` with synthetic events
  (spec 3) over stand-in frames, and assert the label window through `TabDragLabel`'s state.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan5/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan5
cp build/plan3a/edits.py build/plan3a/ledger.py build/plan5/ 2>/dev/null || echo "plan 3a's helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan5 in place of build/plan3a"
ls build/plan5/edits.py build/plan5/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'`
from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan5")`. Java text inside a script is in
triple-quoted strings, so `\n` and `…` in Java source stay as written.

## Review Focus

1. A release Swing never delivers (the button let go over another application, or the system took the release): the
   label window must not stay on screen, and no drop must happen later. Any plain move or press anywhere ends the drag.
   Task 3: `TabDragTest.aLostReleaseEndsTheDrag`.
2. A child window's last tab dragged into another window: the tab arrives with its session, `TAB_MOVED` is reported
   before the child window's `WINDOW_CLOSED`, and no empty window is left. Task 4:
   `TabDragWindowsTest.aChildWindowsLastTabDraggedBackClosesItAfterTheMoveReportsItself`.
3. Windows the user cannot see under the cursor: where windows overlap the drop goes to the one that came to the front
   last, never to the one beneath, and on macOS a window in full screen (on a Space of its own) takes none. Task 1:
   `WorkspaceDropResolverTest.whereWindowsOverlapTheFirstListedTakesTheDrop`; Task 4:
   `TabDragWindowsTest.whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop`,
   `onMacOsAWindowInFullScreenOtherThanTheDragsOwnTakesNoDrop`.
4. A drag that ends where it began (the tab's own place in its strip, its own group's centre, an edge of the group it is
   alone in): no landing shows and the layout stays as it was, with no new group id. Task 2:
   `WorkspaceDropTest.aDropThatWouldChangeNothingIsNoChange`; Task 3:
   `TabDragTest.aDropThatWouldChangeNothingShowsNothingAndDoesNothing`.
5. A tab that changes under the drag (its shell exits, a keyboard command moves it to another group or window, a modal
   owner pins it): the drag ends or keeps it in its window, and no drop lands somewhere the registry would refuse. Task 3:
   `TabDragTest.aTabThatClosesOrLeavesItsGroupUnderThePressDragsNothing`, `theDraggedTabClosingEndsTheDrag`; Task 4:
   `TabDragWindowsTest.aTabMovedToAnotherWindowDuringTheDragEndsIt`, `aPinnedTabMovesOnlyWithinItsWindow`.

## Verified facts

From the code at HEAD `094729d08` (see Workspace).

1. The strips reorder on their own: `WindowTabs.Entry` (its `gestures` adapter) and `ClassicTabs.Entry` (its `mouse`
   adapter) select on a left press and, on a release more than the kit's `TAB_REORDER_THRESHOLD` (5,
   `jasper-ui/.../metrics/DesignMetrics.java:98`) from the press, call `GroupView.reorder(Tab, int)`, its only production
   caller. `WindowTabs`' adapter takes no mouse motion; `ClassicTabs`' takes it only for the ×'s hover. `ClassicTabs`' ×
   takes its own press (`closePressed`) and closes on a release over it.
   `WorkspaceMetricsTest.nativeAdaptersDoNotBorrowGenericKitRoles` requires `TAB_REORDER_THRESHOLD` in
   `WindowTabs.java`.
2. Plan 3a's `GroupView.stripBounds(Component)` and `contentBounds(Component)` give the strip's and the card area's
   rectangles in any component's coordinates, together the group's (`WorkspaceViewTest.groupsReportTheirStripAndCardRectangles`);
   `GroupView.tabs()` lists the group's tabs in strip order. `WindowTabs` lays its entries out in slots inside its
   `viewport` at (0, 0); `ClassicTabs`' entries are its own children.
3. `WorkspaceView`: `reorder(Tab, GroupId, int)` keeps the tab the group showed; `moveTab(Tab, GroupId, int)` and
   `moveToNewGroup(Tab, GroupId, Side)` mark the tab moved (no motion, plan 3a Rulings 27); `render` puts only the
   maximized group's view in the tree while one is maximized, and the main window's empty workspace shows
   `EmptyWorkspace` in place of any group view.
4. `Layout.move` between groups is structural and clears the maximize, within a group it keeps it;
   `Layout.moveToNewGroup` refuses a group's only tab beside its own group; a group a move empties is removed.
5. `DockDrag` runs on plain mouse events, starts past its window's `DOCK_DRAG_THRESHOLD`, owns Esc through a
   `KeyEventDispatcher`, shows `DockDropOverlay` on the root pane's `DRAG_LAYER`, and ends on `WindowContent.setActive(false)`
   ("a modal dialog opening takes the drag's release, and so does another application taking focus") and `close()`.
   `DockDropOverlay.show(Rectangle highlight, Rectangle placeholder, Icon ghost, Point cursor)`, `clear()`,
   `highlight()` and `placeholder()` are package-private.
6. `TabRegistry.move(Tab, UUID)` detaches the tab from its window and attaches it at the end of the target's active group
   through `transfer`, with `Tab.movingFrom` set: `WindowContent.attach` ends the target's maximize and reports
   `TAB_MOVED` (`WindowTerminals.tabArrived`, the only place it is reported: between windows only), `detach` ends the
   source's; then it selects the tab and raises its window. Plan 4a's `TabRegistry.Windows.movedOut` closes a child window
   the move emptied after the move has reported itself. A pinned tab stays.
7. Plan 4a: `WindowManager.windowsByActivation()` lists the open windows, the one that came to the front last first;
   `moveToNewWindow(Tab, Rectangle bounds)` opens the child window with its frame at those screen bounds
   (`Opening.bounds`, then `WindowShell.place` before it shows; `WindowFrame` gives `frame::setBounds`) and refuses a
   child window's only tab; `WindowManager.open` composes each window's content through `Hooks.content`. Plan 4b:
   `WorkspaceHost.attachBeside` is a `WorkspaceHost.Beside` with `attach(Tab tab, GroupId group, Side side)`, a group of
   that window or null for its active group.
8. `gradle/application-architecture.gradle.kts`: classes of `dev.jasper.app.dock` may name only `dock` itself, `java.*`
   outside `java.awt`, and `java.awt.Rectangle` and `Point`; classes of `dev.jasper.app.layout` nothing from `java.awt`.
   `layout` names no other Jasper package.
9. Tests run headless (`build.gradle.kts:31`): no `JWindow` or `JFrame`. `SwingUtilities.convertPointToScreen` and
   `convertPointFromScreen` over components in no native window add or subtract each ancestor's location up to the
   outermost one, whose own location places it on a pretend screen; a `MouseEvent` on a component that does not show has
   no screen point, so the drag converts the event's point itself. `Toolkit.getAWTEventListeners(mask)` lists global
   listeners, and `MenusTest` hands them events directly.
10. `CommandPaletteShortcutsTest.RecordingFocusManager` records the key dispatchers a drag adds (`DockDragTest`);
    `TabMoveTest` and `WindowManagerTest` show a terminal tab keeping its `IdleTerminalSession` across moves between
    windows.
11. `Tab.window(UUID)` tells the tab's change listeners (`Tab.onChanged`), so a move to another window is heard when the
    registry's `transfer` starts it; `Tab.pin()` keeps a tab where it is: `TabRegistry.move` and
    `WindowManager.moveToNewWindow` refuse a pinned tab.
12. `WindowTabs.refresh` moves a tab that opens in a strip already laid out into its place over the open motion's
    475 ms, on a Swing timer, so within one EDT task its entry has no width; `WindowContent.setAnimations(new
    AnimationSettings(false, 0, 0, 0))` makes it take its place at once (`WindowTabsTest` does the same). `ClassicTabs`
    never move.
13. A native frame's `getInsets()` are its system decorations only: Jasper's title bars sit inside the frame
    (`TitleBarStyle`: macOS's unified bar is `MacTitleBar` over full-window content, FlatLaf's title pane is the default
    on Windows and Linux), so only the packed frame knows the chrome around its workspace; `Window.getWindows()` lists
    every window of the process, and a headless JVM has none.

## Rulings

Record them in `docs/STATUS.md` (Task 5). "What — why".

1. **`WorkspaceDropResolver` lives in `dev.jasper.app.dock`, beside `DockDropResolver`, and `dock` gains one allowed edge,
   to `layout`, for `GroupId` and `Side`; `verifyApplicationArchitecture` allows it** — spec 12 puts it beside
   `DockDropResolver`; `layout` forbids `java.awt`, while `dock` already allows `Rectangle` and `Point`; `layout` names no
   Jasper package, so no cycle can form.
2. **Zones: over a strip, the place before the first of its other tabs whose middle is not left of the cursor; over a
   card area, a side's outer quarter (less than a quarter of the width from the left edge, at least three quarters from
   it for the right; up and down alike), the nearer side by share of the area's size in a corner, a tie going left, right,
   up, down; elsewhere in it, the centre. A strip has no edge zones; the main window's empty workspace is one area, all
   centre; a divider between groups is no zone** — `DockDropResolver`'s strip rule and share-based edges; spec 12's
   "outer quarter on any side"; the empty workspace has no group to split.
3. **The drag starts past the kit's `TAB_REORDER_THRESHOLD` (5 px, scaled), the strips' tab drag threshold until now;
   `DOCK_DRAG_THRESHOLD` stays the dock's** — spec 12's "the kit's drag threshold" for a tab; the kit already has a role
   for it.
4. **The landing: a centre drop highlights the whole group, an edge drop the group's half on that side (where the new
   group goes); a strip drop shows a bar `TAB_INDICATOR` wide (2 px) at the place, as the overlay's placeholder; the
   overlay draws no ghost** — spec 12's "as `DockDropOverlay` does for the dock"; the label window already follows the
   cursor.
5. **A drop that would change nothing is the tab's own place in its strip, its own group's centre, or an edge of the group
   it is alone in: it shows no landing and a release does nothing** — spec 12; `Layout.moveToNewGroup` refuses the last
   case, and the others would only churn the layout.
6. **The label window is an undecorated `JWindow` (popup type, never focusable, always on top) with the tab's capped
   title and icon in the chrome's card colours inside a one-pixel window border, its corner 8 scaled px right of and below
   the cursor's tip; it is made when a drag starts and disposed when it ends, and never in a headless JVM** — spec 12's
   "small undecorated window ... under the cursor"; nothing sits under the pointer's hot spot, and it never takes focus
   from the window the drag belongs to.
7. **A drag ends without a drop on Esc (consumed, in any window), on a plain move or a press anywhere (a global AWT
   listener, registered only while a drag runs: Swing sends neither while the button is held, so the release was lost), on
   its source window losing the focus or closing (`WindowContent.setActive(false)` and `close()`, which cover a modal
   dialog and another application, as `DockDrag`), when the dragged tab closes or leaves its window or its group (a
   command moving it: `Tab.onChanged`, and a check at each drag event), and on a release over no target; a press on a
   tab that closed or moved before the threshold starts nothing; another window losing the focus leaves it alone. The
   drag registers its Esc dispatcher and listener before the label shows, and a release takes the label and the landing
   away whatever happens** — spec 12 and 15's modal-dialog row; nothing is left on screen.
8. **Within a window a drop is a layout operation: a reorder along the tab's own strip (keeping the selection, which the
   press gave the tab), a move into another group's strip or after its tabs (selected there, the group active), or a move
   into a new group beside a group** — plan 3a's `WorkspaceView` operations; spec 9.3.
9. **A window's frame on screen is its native window's bounds, title bar included, or, for content in no native window (a
   headless test's), its outermost component's. Main and child windows take drops; Jasper's other showing windows
   (Settings, About, Licenses, a dialog or popup, from `Window.getWindows()` less the label's) are Jasper windows too, so
   a release over one where no main or child window is under the cursor drops nothing. Main and child windows are looked
   at first: Java gives no stacking order, so an auxiliary frame over a main window does not shield it. Other
   applications' windows are invisible to Java: a release over one that covers the cursor counts as outside every
   window and tears out** — spec 12's "releasing over a Jasper window outside its workspace cancels".
10. **The window manager owns one `TabDrag` and gives it to every window it opens (`WindowContent.tabDrag(TabDrag)`); a
    window no manager opened (a test's, the warm-up rehearsal's) keeps one of its own, which drops within itself and tears
    nothing out** — spec 12's "one application-wide `TabDrag`"; plan 4a's `WindowManager` already reaches every window.
11. **A drop on another window moves the tab through the tab registry: an edge drop with the new `TabRegistry.move(Tab,
    UUID, GroupId, Side)` straight into a new group beside that group (plan 4b's `WorkspaceHost.attachBeside`); a strip or
    centre drop with `move(Tab, UUID)` to the end of the window's active group, after which the window's workspace places
    it, in the same EDT turn** — every move reports `TAB_MOVED`, ends both maximizes and closes an emptied child window
    after it (spec 15, plans 3b and 4a); nothing paints between the two steps.
12. **A tear-out opens the child window through plan 4a's `moveToNewWindow(Tab, Rectangle)` with the new window's
    workspace bounds: the old group's size, its first tab where the dragged tab was under the cursor (the cursor less
    where the press held the tab from the tab's own corner); the new frame, packed, grows them by the chrome it has
    around its workspace (its title bar, the system's or Jasper's own) and keeps itself on the usable part of the
    display there (no larger, moved wholly onto it)** — spec 12's "at the cursor, sized like its old group"; IntelliJ
    and VS Code size a torn-out window from the content dragged out of it, and IntelliJ keeps a floating window on its
    screen; only the new frame knows its title bar (fact 13).
13. **A child window's only tab, and a pinned tab, have no tear-out target: a release outside every window does nothing,
    while the label still follows the cursor** — spec 12; plan 4a's `moveToNewWindow` refuses both too.
14. **`GroupView.reorder` and the strips' release-to-reorder go; four tests reorder through `WorkspaceView.reorder`, and
    `WindowTabsTest`'s drag test drags** — spec 12 replaces the gesture; a clean break.
15. **New files take ledger rows `nonvisual-helper,none,none` with "New in workspace-tabs plan 5 Task N"** — plan 4a
    Rulings 21's convention.
16. **The user guide for tabs is `README.md`'s tabs paragraph; `docs/keyboard-shortcuts.md` describes the Keymap window and
    has no section on tabs, so it does not change** — the brief's "the user guide that describes tabs".
17. **A pinned tab (a modal owner holds it, `Tab.pin`) is offered targets in its own window only: no other window and no
    tear-out** — the registry's moves refuse a pinned tab, so the drag must not show a landing it cannot keep.
18. **On macOS a window other than the drag's own whose frame is exactly its display's bounds is in full screen (which
    covers the menu bar), on a Space of its own the user cannot see while dragging from another window: it takes no drop.
    Java cannot see Spaces, so a window on another Space that is not in full screen still takes drops where its frame
    lies; both stay GUI checks. Elsewhere a window covering its display is in sight (a tiling window manager's), so the
    rule is macOS's alone** — spec 12 means the windows the user sees; the review's full-screen rule, kept to the platform
    whose full screen is a Space.

## File structure

- `dev.jasper.app.dock`: `WorkspaceDropResolver` (Task 1), `package-info` (Task 1).
- `dev.jasper.app.workspace`: `TabStrip`, `WindowTabs`, `ClassicTabs`, `GroupView`, `WorkspaceView` (Tasks 2, 3);
  `TabDrag`, `TabDragLabel` (Task 3; both again in Task 4); `WindowContent`, `DockDropOverlay` (Task 3;
  `WindowContent` again in Task 4); `WindowManager`, `WindowFrame`, `WindowShell` (Task 4).
- `dev.jasper.app.contributions`: `TabRegistry` (Task 4).
- `gradle/application-architecture.gradle.kts` (Task 1).
- Tests: new `WorkspaceDropResolverTest` (Task 1), `WorkspaceDropTest` (Task 2), `TabDragTestSupport`, `TabDragTest`
  (Task 3), `TabDragWindowsTest` (Task 4); changed `WindowTabsTest`, `WorkspaceMetricsTest`, `WorkspaceViewTest`,
  `HostedContentTest`, `TerminalContentTest` (Task 3), `TabRegistryTest` (Task 4).
- Documents (Task 5): `README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/sdk-architecture.md`,
  `docs/STATUS.md`; the ledger in Tasks 1-4.

## Before you start

```bash
git branch --show-current                                                                              # claude/workspace-tabs
git status --short                                                                                     # nothing
git log --oneline -1 --grep "Workspace tabs: WindowSurface names a dialog or an overlay"              # 094729d08 Workspace tabs: WindowSurface names a dialog or an overlay
grep -c 'void reorder(Tab tab, int index) { workspace.reorder(tab, id, index); }' jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java   # 1
grep -c 'Rectangle stripBounds(Component target)' jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java                                  # 1
grep -c 'public List<JasperWindow> windowsByActivation()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java                     # 1
grep -c 'public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'void attach(Tab tab, GroupId group, Side side);' jasper-app/src/main/java/dev/jasper/app/contributions/WorkspaceHost.java                   # 1
grep -c 'try { windows.movedOut().accept(old.id()); }' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java                     # 1
```

Every command prints the stated result. Plans 1 to 4b each added a `docs/STATUS.md` entry; this plan's goes above them.
Create the two helper scripts (Global Constraints).

---

### Task 1: The workspace drop resolver

Spec 12 and 17: a pure function maps the cursor over a workspace's groups to a drop (a place in a strip, a group's
centre, a group's outer quarter on a side), gives the rectangle the overlay shows for it, and picks the window that takes
it where windows overlap. It lives beside `DockDropResolver` in the JDK-only `dock` package, which may now name the
layout's group ids and sides (Rulings 1, 2). Nothing calls it yet.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/package-info.java`, `gradle/application-architecture.gradle.kts`
- Test: `jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java`
- Ledger: two rows

**Interfaces:**
- Consumes: plan 2's `dev.jasper.app.layout.GroupId` (`GroupId.random()`) and `Side` (LEFT, RIGHT, UP, DOWN).
- Produces: `public final class WorkspaceDropResolver` with
  `public record Area(GroupId group, Rectangle strip, List<Rectangle> tabs, Rectangle content, boolean splits)` (its
  accessors return copies; `Rectangle bounds()` is the strip and the card area together, the card area alone when the
  strip is empty); `public sealed interface Drop` with `GroupId group()` and the records `Drop.Strip(GroupId group, int
  index)`, `Drop.Centre(GroupId group)`, `Drop.Edge(GroupId group, Side side)`;
  `public static Optional<Drop> resolve(Point cursor, List<Area> areas)`;
  `public static Rectangle landing(Drop drop, List<Area> areas, int bar)`;
  `public static OptionalInt windowAt(Point cursor, List<Rectangle> windows)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/WorkspaceDropResolverTest.java`:

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

/** Workspace tabs spec 12 and 17: where a dragged tab drops, from the cursor alone, for every zone and overlapping windows. */
class WorkspaceDropResolverTest {
    private static final GroupId LEFT = GroupId.random(), RIGHT = GroupId.random();
    /**
     * Two groups side by side, 6 px apart, under 28 px strips: the left strip shows two tabs besides the dragged one, the
     * right strip one tab 120 px wide.
     */
    private static final List<Area> TWO = List.of(
        new Area(LEFT, new Rectangle(0, 0, 400, 28), List.of(new Rectangle(0, 0, 200, 28), new Rectangle(200, 0, 200, 28)),
            new Rectangle(0, 28, 400, 572), true),
        new Area(RIGHT, new Rectangle(406, 0, 394, 28), List.of(new Rectangle(406, 0, 120, 28)),
            new Rectangle(406, 28, 394, 572), true));
    /** One group whose card area is 100 x 80 under a 10 px strip that shows no other tab, for pixel-exact boundaries. */
    private static final List<Area> SMALL = List.of(new Area(LEFT, new Rectangle(0, 0, 100, 10), List.of(), new Rectangle(0, 10, 100, 80), true));

    private static Drop at(List<Area> areas, int x, int y) {
        return WorkspaceDropResolver.resolve(new Point(x, y), areas).orElseThrow();
    }

    @Test void overAStripTheTabGoesBeforeTheFirstTabWhoseMiddleIsNotLeftOfTheCursor() {
        assertThat(at(TWO, 10, 10)).isEqualTo(new Drop.Strip(LEFT, 0));
        assertThat(at(TWO, 100, 10)).as("on a middle: still before that tab").isEqualTo(new Drop.Strip(LEFT, 0));
        assertThat(at(TWO, 101, 10)).isEqualTo(new Drop.Strip(LEFT, 1));
        assertThat(at(TWO, 399, 27)).as("the strip's last pixel").isEqualTo(new Drop.Strip(LEFT, 2));
        assertThat(at(TWO, 700, 0)).as("the empty end of a strip").isEqualTo(new Drop.Strip(RIGHT, 1));
        assertThat(at(SMALL, 50, 5)).as("a strip whose only tab is the dragged one").isEqualTo(new Drop.Strip(LEFT, 0));
    }

    @Test void theMiddleOfACardAreaAddsToItsGroup() {
        assertThat(at(TWO, 200, 300)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(TWO, 603, 314)).isEqualTo(new Drop.Centre(RIGHT));
    }

    @Test void eachOuterQuarterOfACardAreaSplitsItsSide() {
        assertThat(at(TWO, 40, 300)).isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(TWO, 360, 300)).isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(at(TWO, 200, 60)).as("just under the strip").isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(TWO, 200, 560)).isEqualTo(new Drop.Edge(LEFT, Side.DOWN));
        assertThat(at(TWO, 420, 300)).as("the right group's own left quarter").isEqualTo(new Drop.Edge(RIGHT, Side.LEFT));
    }

    @Test void theQuartersArePixelExact() {
        assertThat(at(SMALL, 0, 50)).as("the left edge is inside").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 24, 50)).isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 25, 50)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 74, 50)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 75, 50)).isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 29)).isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(SMALL, 50, 30)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 50, 69)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 50, 70)).isEqualTo(new Drop.Edge(LEFT, Side.DOWN));
        assertThat(at(SMALL, 99, 89)).as("the last pixel").isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(WorkspaceDropResolver.resolve(new Point(100, 50), SMALL)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 90), SMALL)).as("nor the bottom edge").isEmpty();
    }

    @Test void aCornerGoesToTheNearerEdgeAndATieToLeftThenRightThenUpThenDown() {
        assertThat(at(SMALL, 10, 15)).as("nearer the top").isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(SMALL, 5, 20)).as("nearer the left").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 5, 14)).as("as near the left as the top").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 95, 86)).as("as near the right as the bottom").isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
    }

    @Test void theEmptyWorkspacesGroupIsItsCentreEverywhere() {
        var empty = List.of(new Area(LEFT, new Rectangle(), List.of(), new Rectangle(0, 0, 800, 600), false));
        assertThat(at(empty, 0, 0)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(empty, 400, 300)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(empty, 799, 599)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(empty.getFirst().bounds()).as("with no strip, its whole is its card area").isEqualTo(new Rectangle(0, 0, 800, 600));
    }

    @Test void nothingBetweenOrOutsideTheGroups() {
        assertThat(WorkspaceDropResolver.resolve(new Point(403, 300), TWO)).as("the divider").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(900, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(-1, 10), TWO)).isEmpty();
    }

    @Test void theLandingIsTheWholeGroupItsHalfOnASideOrABarInTheStrip() {
        Rectangle whole = new Rectangle(0, 0, 400, 600);
        assertThat(TWO.getFirst().bounds()).as("the strip and the card area").isEqualTo(whole);
        assertThat(WorkspaceDropResolver.landing(new Drop.Centre(LEFT), TWO, 2)).isEqualTo(whole);
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.LEFT), TWO, 2)).isEqualTo(new Rectangle(0, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.RIGHT), TWO, 2)).isEqualTo(new Rectangle(200, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.UP), TWO, 2)).isEqualTo(new Rectangle(0, 0, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.DOWN), TWO, 2)).isEqualTo(new Rectangle(0, 300, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(RIGHT, Side.RIGHT), TWO, 2)).as("an odd width: the new group's half")
            .isEqualTo(new Rectangle(603, 0, 197, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 1), TWO, 2)).as("between the two tabs")
            .isEqualTo(new Rectangle(199, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 0), TWO, 2)).as("before the first, kept inside the strip")
            .isEqualTo(new Rectangle(0, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 2), TWO, 2)).as("after the last, kept inside the strip")
            .isEqualTo(new Rectangle(398, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(RIGHT, 1), TWO, 2)).as("after the last, mid-strip")
            .isEqualTo(new Rectangle(525, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 0), SMALL, 2)).as("a strip with no other tab: its start")
            .isEqualTo(new Rectangle(0, 0, 2, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.landing(new Drop.Centre(GroupId.random()), TWO, 2));
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

    @Test void anAreaKeepsItsOwnRectangles() {
        Rectangle strip = new Rectangle(0, 0, 100, 10);
        var area = new Area(LEFT, strip, List.of(new Rectangle(0, 0, 50, 10)), new Rectangle(0, 10, 100, 80), true);
        strip.width = 1;
        area.strip().width = 2;
        area.tabs().getFirst().width = 3;
        assertThat(area.strip()).isEqualTo(new Rectangle(0, 0, 100, 10));
        assertThat(area.tabs()).containsExactly(new Rectangle(0, 0, 50, 10));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.WorkspaceDropResolverTest'`
Expected: FAIL to compile: `cannot find symbol: class WorkspaceDropResolver`.

- [ ] **Step 3: The resolver**

Create `jasper-app/src/main/java/dev/jasper/app/dock/WorkspaceDropResolver.java`:

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
 * Where a dragged tab would drop (workspace tabs spec 12), from the cursor alone: into a group's strip at a place among
 * its tabs, into a group over the middle of its card area, or into a new group on the side whose outer quarter of the card
 * area the cursor is over; the rectangle that shows the landing; and which of several windows takes the drop. The tab
 * drag's twin of {@link DockDropResolver}. Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class WorkspaceDropResolver {
    private WorkspaceDropResolver() { }

    /**
     * One group of a workspace as a tab drag sees it.
     *
     * @param group the group
     * @param strip its tab strip; empty for the empty workspace's group, which shows none
     * @param tabs its tabs' rectangles in strip order, without the dragged tab's own
     * @param content its card area; for the empty workspace's group, the whole workspace
     * @param splits whether the card area's outer quarters split the group; false for the empty workspace's group, all of
     *     whose area adds to it
     */
    public record Area(GroupId group, Rectangle strip, List<Rectangle> tabs, Rectangle content, boolean splits) {
        /** Requires the group and copies the rectangles. */
        public Area {
            Objects.requireNonNull(group, "group");
            strip = new Rectangle(strip);
            tabs = tabs.stream().map(Rectangle::new).toList();
            content = new Rectangle(content);
        }

        @Override public Rectangle strip() { return new Rectangle(strip); }

        @Override public List<Rectangle> tabs() { return tabs.stream().map(Rectangle::new).toList(); }

        @Override public Rectangle content() { return new Rectangle(content); }

        /**
         * The whole group.
         *
         * @return the strip and the card area together; the card area alone when there is no strip
         */
        public Rectangle bounds() { return strip.isEmpty() ? new Rectangle(content) : strip.union(content); }
    }

    /** Where a dragged tab would go. */
    public sealed interface Drop {
        /**
         * The group the cursor is over.
         *
         * @return the group
         */
        GroupId group();

        /**
         * Into the group's strip, before the tab now at {@code index} of its tabs without the dragged one.
         *
         * @param group the group
         * @param index 0 to the number of the group's tabs without the dragged one
         */
        record Strip(GroupId group, int index) implements Drop {
            /** Requires the group and an index of at least 0. */
            public Strip {
                Objects.requireNonNull(group, "group");
                if (index < 0) throw new IllegalArgumentException("Index " + index + " is negative");
            }
        }

        /**
         * Into the group, after its tabs.
         *
         * @param group the group
         */
        record Centre(GroupId group) implements Drop {
            /** Requires the group. */
            public Centre {
                Objects.requireNonNull(group, "group");
            }
        }

        /**
         * Into a new group on {@code side} of the group, which takes half of the group's space.
         *
         * @param group the group
         * @param side where the new group goes
         */
        record Edge(GroupId group, Side side) implements Drop {
            /** Requires both. */
            public Edge {
                Objects.requireNonNull(group, "group");
                Objects.requireNonNull(side, "side");
            }
        }
    }

    /**
     * The drop at {@code cursor}. Over a strip: that group, before the first of its tabs whose middle is not left of the
     * cursor. Over a card area: the side whose outer quarter holds the cursor, the nearer when two do (each distance a
     * share of the area's own width or height, so the zones keep their shape at any aspect ratio; a tie goes to the left,
     * then the right, then up, then down), otherwise the group's centre; the empty workspace's group is its centre
     * everywhere. A point is in the left quarter when it lies less than a quarter of the width from the left edge and in
     * the right quarter when it lies at least three quarters of the width from it; up and down alike. An area's left and
     * top edges lie inside it, its right and bottom edges do not. Anywhere else, such as a divider between groups: nothing.
     *
     * @param cursor the cursor
     * @param areas the groups of one workspace
     * @return the drop, or empty
     */
    public static Optional<Drop> resolve(Point cursor, List<Area> areas) {
        Objects.requireNonNull(cursor, "cursor");
        for (Area area : areas) {
            if (area.strip.contains(cursor)) {
                int index = 0;
                for (Rectangle tab : area.tabs) if (tab.x + tab.width / 2.0 < cursor.x) index++;
                return Optional.of(new Drop.Strip(area.group, index));
            }
            if (!area.content.contains(cursor)) continue;
            Optional<Side> side = area.splits ? side(cursor, area.content) : Optional.empty();
            return Optional.of(side.<Drop>map(edge -> new Drop.Edge(area.group, edge)).orElseGet(() -> new Drop.Centre(area.group)));
        }
        return Optional.empty();
    }

    /** The side whose outer quarter of {@code content} holds {@code cursor}, the nearer when two do; empty in the middle. */
    private static Optional<Side> side(Point cursor, Rectangle content) {
        // The shares dx / w, (w - dx) / w, dy / h and (h - dy) / h, scaled by w * h: whole numbers, so ties are exact.
        long width = content.width, height = content.height, whole = width * height;
        long dx = cursor.x - content.x, dy = cursor.y - content.y;
        long[] distance = {dx * height, (width - dx) * height, dy * width, (height - dy) * width};
        // dx < w / 4 from the left and top; w - dx <= w / 4, that is dx >= 3w / 4, from the right and bottom.
        boolean[] inside = {4 * distance[0] < whole, 4 * distance[1] <= whole, 4 * distance[2] < whole, 4 * distance[3] <= whole};
        Side[] sides = {Side.LEFT, Side.RIGHT, Side.UP, Side.DOWN};
        Side nearest = null;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < sides.length; i++) {
            if (inside[i] && distance[i] < best) {
                nearest = sides[i];
                best = distance[i];
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Where a drop would land, for the overlay: for a strip drop a bar {@code bar} wide and as tall as the strip, centred
     * on the place between the tabs before and after it and kept inside the strip; for a centre drop the whole group; for
     * an edge drop the group's half on that side, where the new group goes.
     *
     * @param drop a drop {@link #resolve} gave for these areas
     * @param areas the groups it was resolved over
     * @param bar the strip marker's width
     * @return the rectangle, in the areas' coordinates
     * @throws IllegalArgumentException when no area is the drop's group
     */
    public static Rectangle landing(Drop drop, List<Area> areas, int bar) {
        Area area = areas.stream().filter(candidate -> candidate.group.equals(drop.group())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No area for group " + drop.group()));
        Rectangle whole = area.bounds();
        return switch (drop) {
            case Drop.Strip strip -> marker(area, strip.index(), bar);
            case Drop.Centre centre -> whole;
            case Drop.Edge edge -> switch (edge.side()) {
                case LEFT -> new Rectangle(whole.x, whole.y, whole.width / 2, whole.height);
                case RIGHT -> new Rectangle(whole.x + whole.width - whole.width / 2, whole.y, whole.width / 2, whole.height);
                case UP -> new Rectangle(whole.x, whole.y, whole.width, whole.height / 2);
                case DOWN -> new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2);
            };
        };
    }

    /** A bar {@code width} wide, centred on the place before the tab at {@code index} (after the last at the end), in the strip. */
    private static Rectangle marker(Area area, int index, int width) {
        List<Rectangle> tabs = area.tabs;
        Rectangle strip = area.strip;
        int x;
        if (tabs.isEmpty()) x = strip.x;
        else if (index == 0) x = tabs.getFirst().x;
        else if (index >= tabs.size()) x = tabs.getLast().x + tabs.getLast().width;
        else x = (tabs.get(index - 1).x + tabs.get(index - 1).width + tabs.get(index).x) / 2;
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

- [ ] **Step 4: The dock package may name the layout's values**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/dock/package-info.java", [
    (""" * The tool window dock model: the six slots, one window's {@code DockState}, the {@code DockGeometry} that lays it out
 * and the {@code DockDropResolver} that finds where a dragged window would drop. JDK-only: no Swing or AWT components,
 * and from {@code java.awt} only the {@code Rectangle} and {@code Point} values; {@code verifyApplicationArchitecture}
 * enforces it. Each window owns its state and confines it to the EDT.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
""", """ * The tool window dock model: the six slots, one window's {@code DockState}, the {@code DockGeometry} that lays it out
 * and the {@code DockDropResolver} that finds where a dragged window would drop; and the {@code WorkspaceDropResolver}
 * that finds where a dragged tab would drop on a workspace. JDK-only: no Swing or AWT components, and from
 * {@code java.awt} only the {@code Rectangle} and {@code Point} values; {@code verifyApplicationArchitecture} enforces
 * it. Each window owns its state and confines it to the EDT.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.layout (its group ids and sides).
""", 1)])
edit("gradle/application-architecture.gradle.kts", [
    ("        // The dock model is pure: JDK types only, and from java.awt only two value classes.\n",
     "        // The dock model is pure: JDK types and the layout's values only, and from java.awt only two value classes.\n", 1),
    ("""                    check(to.startsWith("dev.jasper.app.dock.") || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
""", """                    check(to.startsWith("dev.jasper.app.dock.") || to.startsWith("dev.jasper.app.layout.")
                        || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
""", 1)])
PY
```

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/dock/", "jasper-app/src/test/java/dev/jasper/app/dock/"
ledger.add(M + "WorkspaceDropResolver.java", "app.dock", "WorkspaceDropResolver", T + "WorkspaceDropResolverTest.java",
           "New in workspace-tabs plan 5 Task 1; where a dragged tab drops on a workspace")
ledger.add(T + "WorkspaceDropResolverTest.java", "app.dock", "WorkspaceDropResolverTest", T + "WorkspaceDropResolverTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 5 Task 1; every drop zone and overlapping windows")
PY
```

- [ ] **Step 6: Run the tests and the architecture check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WorkspaceDropResolverTest` 10). Then `./gradlew verifyApplicationArchitecture`: PASS (dock gains its one
edge, to layout, which depends on no Jasper package, so the graph stays acyclic). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src gradle/application-architecture.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tab drag: the workspace drop resolver

WorkspaceDropResolver maps the cursor over a workspace's groups to a drop:
a place in a strip, a group's centre, or a new group on the side whose
outer quarter the cursor is over; the empty workspace's group is its
centre everywhere. It gives the rectangle the overlay shows and picks the
most recently active of overlapping windows. It sits beside
DockDropResolver in the JDK-only dock package, which may now name the
layout's group ids and sides.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Drop areas and drops in the workspace view

Spec 12: a window's workspace tells a tab drag which groups it shows (the maximized group alone while one is; the main
window's empty workspace as one area that is all centre), where each strip's tabs are, and carries out a drop on its own
tabs through the layout operations plans 2 and 3a gave it: a reorder within a strip, a move into another group, a split
beside a group. A drop that would change nothing is no change (Rulings 5). Both strip faces report their tabs'
rectangles. Nothing drags yet.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabStrip.java`, `WindowTabs.java`, `ClassicTabs.java`,
  `GroupView.java`, `WorkspaceView.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceDropTest.java`
- Ledger: one row

**Interfaces:**
- Consumes: Task 1's `WorkspaceDropResolver.Area` and `Drop`; plan 3a's `WorkspaceView.reorder(Tab, GroupId, int)`,
  `moveTab(Tab, GroupId, int)`, `moveToNewGroup(Tab, GroupId, Side)`, `contains(Tab)`, `model()`, `groupView(GroupId)`,
  `GroupView.stripBounds(Component)`, `contentBounds(Component)`, `tabs()`.
- Produces: `TabStrip.tabBounds(): List<Rectangle>` (strip coordinates, the group's tab order) on both faces;
  `GroupView.tabBounds(Component target): List<Rectangle>`; `WorkspaceView.dropAreas(Tab dragged): List<Area>` (the
  view's coordinates), `boolean changes(Tab tab, Drop drop)` (always true for a tab the workspace does not hold) and
  `void drop(Tab tab, Drop drop)` (acts only on a tab the workspace holds).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceDropTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver.Area;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 12: the groups a tab drag sees in a window's workspace, and the drops it makes there. */
class WorkspaceDropTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window with three terminal tabs in one group, sized and laid out. */
    private static WindowContent window() {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        owner.newTab(HOME);
        owner.newTab(HOME);
        layout(owner);
        return owner;
    }

    private static void layout(WindowContent owner) {
        owner.setSize(1208, 700);
        LayoutTestSupport.layoutTree(owner);
        LayoutTestSupport.layoutTree(owner);
    }

    @Test void theEqualTabsReportTheirTabsLeftToRightInsideTheStrip() throws Exception {
        edt(() -> {
            var owner = window();
            var group = owner.workspace().activeGroupView();
            var strip = group.strip().component();
            List<Rectangle> tabs = group.tabBounds(strip);
            assertThat(tabs).hasSize(3);
            for (int i = 0; i < tabs.size(); i++) {
                assertThat(new Rectangle(strip.getSize()).contains(tabs.get(i))).as("tab %d inside its strip", i).isTrue();
                assertThat(tabs.get(i).width).as("tab %d has room", i).isPositive();
                if (i > 0) assertThat(tabs.get(i).x).as("tab %d after tab %d", i, i - 1).isGreaterThanOrEqualTo(tabs.get(i - 1).x + tabs.get(i - 1).width);
            }
            assertThat(group.tabBounds(owner.workspace())).as("in another component's coordinates")
                .containsExactlyElementsOf(tabs.stream().map(tab -> SwingUtilities.convertRectangle(strip, tab, owner.workspace())).toList());
        });
    }

    @Test void theClassicTabsReportTheirContentSizedTabs() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(idleLauncher(new ArrayDeque<>()), themes, ClassicTabsTest.classic());
                owner.newTab(HOME);
                layout(owner);
                var group = owner.workspace().activeGroupView();
                var strip = (ClassicTabs) group.strip();
                assertThat(group.tabBounds(strip)).containsExactly(strip.entries().get(0).getBounds(), strip.entries().get(1).getBounds());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void everyGroupThatShowsIsADropAreaWithoutTheDraggedTab() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            GroupId left = workspace.model().activeGroup();
            workspace.moveToNewGroup(tabs.get(2), left, Side.RIGHT);
            layout(owner);
            GroupId right = workspace.model().activeGroup();
            List<Area> areas = workspace.dropAreas(tabs.get(0));
            assertThat(areas).extracting(Area::group).containsExactly(left, right);
            var view = workspace.groupView(left);
            Area first = areas.getFirst();
            assertThat(first.strip()).isEqualTo(view.stripBounds(workspace));
            assertThat(first.content()).isEqualTo(view.contentBounds(workspace));
            assertThat(first.tabs()).as("the dragged tab's own place is left out").containsExactly(view.tabBounds(workspace).get(1));
            assertThat(first.splits()).isTrue();
            assertThat(areas.get(1).tabs()).containsExactly(workspace.groupView(right).tabBounds(workspace).getFirst());
            assertThat(workspace.dropAreas(tabs.get(2)).get(1).tabs()).as("a group whose only tab is dragged").isEmpty();
        });
    }

    @Test void aMaximizedGroupIsTheOnlyArea() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(2), workspace.model().activeGroup(), Side.DOWN);
            workspace.toggleMaximize();
            layout(owner);
            GroupId shown = workspace.model().activeGroup();
            List<Area> areas = workspace.dropAreas(owner.tabs().get(0));
            assertThat(areas).extracting(Area::group).containsExactly(shown);
            var view = workspace.groupView(shown);
            assertThat(areas.getFirst().bounds()).as("the maximized group fills the workspace")
                .isEqualTo(SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), workspace))
                .isEqualTo(new Rectangle(workspace.getSize()));
        });
    }

    @Test void theMainWindowsEmptyWorkspaceIsOneCentreOverItsWhole() throws Exception {
        edt(() -> {
            var owner = window();
            Tab elsewhere = content(idleLauncher(new ArrayDeque<>())).selectedTab();
            for (Tab tab : owner.tabs()) owner.closeTab(tab);
            layout(owner);
            var workspace = owner.workspace();
            assertThat(workspace.emptyWorkspace()).as("the empty state shows").isNotNull();
            List<Area> areas = workspace.dropAreas(elsewhere);
            assertThat(areas).hasSize(1);
            Area whole = areas.getFirst();
            assertThat(whole.group()).isEqualTo(workspace.model().activeGroup());
            assertThat(whole.strip().isEmpty()).as("no strip").isTrue();
            assertThat(whole.splits()).isFalse();
            assertThat(whole.content()).isEqualTo(new Rectangle(workspace.getSize()));
        });
    }

    @Test void dropsReorderMoveAndSplit() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab a = tabs.get(0), b = tabs.get(1), c = tabs.get(2);
            GroupId group = workspace.model().activeGroup();
            owner.selectTab(a);
            workspace.drop(a, new Drop.Strip(group, 2));
            assertThat(owner.tabs()).as("along its strip").containsExactly(b, c, a);
            assertThat(owner.selectedTab()).isSameAs(a);
            workspace.drop(c, new Drop.Edge(group, Side.RIGHT));
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            assertThat(right).as("a new group").isNotEqualTo(group);
            assertThat(((Split) workspace.model().root()).orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(workspace.model().activeGroup()).isEqualTo(right);
            workspace.drop(b, new Drop.Centre(right));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).as("after the group's tabs").containsExactly(c.id(), b.id());
            assertThat(owner.selectedTab()).isSameAs(b);
            workspace.drop(a, new Drop.Strip(right, 1));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).as("into another strip, between its tabs")
                .containsExactly(c.id(), a.id(), b.id());
            assertThat(workspace.model().groups()).as("the emptied group went").hasSize(1);
            assertThat(workspace.moved(a)).as("a moved tab shows at once, without motion").isTrue();
        });
    }

    @Test void aMoveIntoANewGroupEndsAMaximizeAndAReorderKeepsIt() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab b = tabs.get(1), c = tabs.get(2);
            workspace.moveToNewGroup(c, workspace.model().activeGroup(), Side.RIGHT);
            GroupId right = workspace.model().activeGroup();
            workspace.moveTab(b, right, 1);
            workspace.toggleMaximize();
            assertThat(workspace.model().maximized()).contains(right);
            workspace.drop(b, new Drop.Strip(right, 0));
            assertThat(workspace.model().group(right).orElseThrow().tabs()).containsExactly(b.id(), c.id());
            assertThat(workspace.model().maximized()).as("a reorder keeps it").contains(right);
            workspace.drop(b, new Drop.Edge(right, Side.DOWN));
            assertThat(workspace.model().maximized()).as("a new group ends it").isEmpty();
        });
    }

    @Test void aDropThatWouldChangeNothingIsNoChange() throws Exception {
        edt(() -> {
            var owner = window();
            var workspace = owner.workspace();
            var tabs = owner.tabs();
            Tab a = tabs.get(0), c = tabs.get(2);
            GroupId group = workspace.model().activeGroup();
            workspace.moveToNewGroup(c, group, Side.RIGHT);
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            assertThat(workspace.changes(a, new Drop.Strip(group, 0))).as("its own place").isFalse();
            assertThat(workspace.changes(a, new Drop.Strip(group, 1))).isTrue();
            assertThat(workspace.changes(a, new Drop.Centre(group))).as("its own group's centre").isFalse();
            assertThat(workspace.changes(a, new Drop.Edge(group, Side.UP))).as("an edge of its group, which holds another tab").isTrue();
            assertThat(workspace.changes(c, new Drop.Edge(right, Side.LEFT))).as("an edge of the group it is alone in").isFalse();
            assertThat(workspace.changes(c, new Drop.Centre(group))).isTrue();
            Layout before = workspace.model();
            workspace.drop(a, new Drop.Strip(group, 0));
            workspace.drop(a, new Drop.Centre(group));
            workspace.drop(c, new Drop.Edge(right, Side.LEFT));
            assertThat(workspace.model()).isSameAs(before);
            Tab elsewhere = content(idleLauncher(new ArrayDeque<>())).selectedTab();
            assertThat(workspace.changes(elsewhere, new Drop.Centre(group))).as("a tab from another window always moves").isTrue();
            workspace.drop(elsewhere, new Drop.Centre(group));
            assertThat(workspace.model()).as("a tab of another window is the registry's to move in").isSameAs(before);
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceDropTest'`
Expected: FAIL to compile: `cannot find symbol: method tabBounds(...)`, `dropAreas(...)`, `changes(...)`, `drop(...)`.

- [ ] **Step 3: The strips, the group view and the workspace view**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "TabStrip.java", [
    ("import dev.jasper.app.config.AnimationSettings;\n",
     "import dev.jasper.app.config.AnimationSettings;\nimport java.awt.Rectangle;\nimport java.util.List;\n", 1),
    ("""    JPopupMenu tabList();
""", """    JPopupMenu tabList();
    /** Each tab's rectangle in this strip's coordinates, in its group's tab order: a tab drag drops between them. */
    List<Rectangle> tabBounds();
""", 1)])
edit(W + "WindowTabs.java", [(
    """    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(group.owner().tabHeight())); }
""", """    @Override public List<Rectangle> tabBounds() {
        var bounds = new ArrayList<Rectangle>(order.size());
        for (Tab tab : order) bounds.add(SwingUtilities.convertRectangle(viewport, entries.get(tab).getBounds(), this));
        return bounds;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(group.owner().tabHeight())); }
""", 1)])
edit(W + "ClassicTabs.java", [(
    "    List<Entry> entries() { return List.copyOf(entries); }\n",
    "    @Override public List<Rectangle> tabBounds() { return entries.stream().map(Entry::getBounds).toList(); }\n\n"
    "    List<Entry> entries() { return List.copyOf(entries); }\n", 1)])
edit(W + "GroupView.java", [(
    """    Rectangle contentBounds(Component target) { return SwingUtilities.convertRectangle(this, cards.getBounds(), target); }
""", """    Rectangle contentBounds(Component target) { return SwingUtilities.convertRectangle(this, cards.getBounds(), target); }
    /** Each tab's rectangle in the strip, in strip order, in {@code target}'s coordinates: a tab drag drops between them. */
    List<Rectangle> tabBounds(Component target) {
        return strip.tabBounds().stream().map(bounds -> SwingUtilities.convertRectangle(strip.component(), bounds, target)).toList();
    }
""", 1)])
edit(W + "WorkspaceView.java", [
    ("import dev.jasper.app.contributions.Tab;\n",
     "import dev.jasper.app.contributions.Tab;\nimport dev.jasper.app.dock.WorkspaceDropResolver.Area;\n"
     "import dev.jasper.app.dock.WorkspaceDropResolver.Drop;\n", 1),
    ("import java.awt.BorderLayout;\n", "import java.awt.BorderLayout;\nimport java.awt.Rectangle;\n", 1),
    ("""    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
""", """    /**
     * The groups a tab drag can drop on, in this view's coordinates (workspace tabs spec 12): each group that shows (the
     * maximized one alone while one is), with its strip, its tabs' rectangles but {@code dragged}'s, and its card area. The
     * empty workspace is one area, all of it its group's centre.
     */
    List<Area> dropAreas(Tab dragged) {
        if (layout.isEmpty())
            return List.of(new Area(layout.activeGroup(), new Rectangle(), List.of(), new Rectangle(0, 0, getWidth(), getHeight()), false));
        var areas = new ArrayList<Area>();
        for (GroupView group : groups.values()) {
            if (!SwingUtilities.isDescendingFrom(group, this)) continue;
            List<Tab> held = group.tabs();
            List<Rectangle> bounds = group.tabBounds(this);
            var others = new ArrayList<Rectangle>();
            for (int i = 0; i < held.size() && i < bounds.size(); i++) if (held.get(i) != dragged) others.add(bounds.get(i));
            areas.add(new Area(group.id(), group.stripBounds(this), others, group.contentBounds(this), true));
        }
        return areas;
    }

    /**
     * Whether dropping {@code tab} there would change anything (spec 12: a drop that would change nothing does nothing). A
     * tab of another window always would; one of this workspace's would unless the drop is its own place in its strip, its
     * own group's centre, or an edge of the group it is alone in.
     */
    boolean changes(Tab tab, Drop drop) {
        if (!contains(tab)) return true;
        GroupId home = layout.groupOf(tab.id()).orElseThrow();
        if (!drop.group().equals(home)) return true;
        List<TabId> held = layout.group(home).orElseThrow().tabs();
        return switch (drop) {
            case Drop.Strip strip -> strip.index() != held.indexOf(tab.id());
            case Drop.Centre centre -> false;
            case Drop.Edge edge -> held.size() > 1;
        };
    }

    /**
     * Places {@code tab}, one of this workspace's, where a tab drag dropped it: along its own strip as a reorder, into
     * another group's strip or after its tabs as a move, beside a group as a new group. A drop that would change nothing,
     * or a tab this workspace does not hold, does nothing.
     */
    void drop(Tab tab, Drop drop) {
        if (!contains(tab) || !changes(tab, drop)) return;
        switch (drop) {
            case Drop.Strip strip -> {
                if (layout.groupOf(tab.id()).orElseThrow().equals(strip.group())) reorder(tab, strip.group(), strip.index());
                else moveTab(tab, strip.group(), strip.index());
            }
            case Drop.Centre centre -> moveTab(tab, centre.group(), layout.group(centre.group()).orElseThrow().tabs().size());
            case Drop.Edge edge -> moveToNewGroup(tab, edge.group(), edge.side());
        }
    }

    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
""", 1)])
PY
```

- [ ] **Step 4: Ledger row**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "WorkspaceDropTest.java", "app.workspace", "WorkspaceDropTest", T + "WorkspaceDropTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 5 Task 2; drop areas and drops in a workspace")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WorkspaceDropTest` 8; every other workspace test unchanged). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tab drag: drop areas and drops in the workspace view

A window's workspace lists the groups a tab drag can drop on (the
maximized group alone, the empty workspace as one centre), with each
strip's tabs from both strip faces, and carries out a drop on its own
tabs as a reorder, a move into another group or a split beside one; a
drop that would change nothing is no change.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The tab drag in one window

Spec 12: one gesture replaces the strips' release-to-reorder. A left press on a tab in either strip face that moves past
the kit's tab drag threshold starts `TabDrag`; a label window follows the cursor, the target window's layered pane shows
the landing, and the release reorders within the strip, moves the tab into another group or splits a group's side.
Esc, a lost release, the window losing the focus (a modal dialog, another application) and the dragged tab closing or
leaving its window or group end it without a drop; a pinned tab takes targets in its own window only. Each window has a
drag of its own until Task 4 shares one across the windows (Rulings 3, 4, 6, 7, 8, 17).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java`, `TabDragLabel.java`
- Modify: `workspace/WindowTabs.java`, `ClassicTabs.java`, `TabStrip.java`, `GroupView.java`, `WindowContent.java`,
  `DockDropOverlay.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java`, `TabDragTest.java`; modified
  `WindowTabsTest.java`, `WorkspaceMetricsTest.java`, `WorkspaceViewTest.java`, `HostedContentTest.java`,
  `TerminalContentTest.java`
- Ledger: four rows

**Interfaces:**
- Consumes: Task 1's `WorkspaceDropResolver.resolve`, `landing`, `windowAt`; Task 2's `GroupView.tabBounds(Component)`,
  `WorkspaceView.dropAreas(Tab)`, `changes(Tab, Drop)`, `drop(Tab, Drop)`; `DockDropOverlay.show(Rectangle, Rectangle,
  Icon, Point)`, `clear()`, `highlight()`, `placeholder()`; `WindowContent.closed()`, `workspace()`, `tabTitle(Tab)`.
- Produces: `final class TabDrag` with `TabDrag(Supplier<List<WindowContent>> windows)` (the windows most recently active
  first), `sealed interface Target` with `record Into(WindowContent window, Drop drop)`, `static final int
  LABEL_OFFSET = 8`, `void pressed(GroupView, Tab, MouseEvent)`, `void dragged(MouseEvent)`, `void released(MouseEvent)`,
  `void cancel()`, `void cancelFrom(WindowContent)`, `boolean dispatchKeyEvent(KeyEvent)`, `boolean active()`, `Tab
  dragged()`, `Optional<Target> target()`, `DockDropOverlay overlay()`, `TabDragLabel label()`; `final class
  TabDragLabel` with `show(String, Icon, Point)`, `move(Point)`, `hide()`, `showing()`, `text()`, `location()`;
  `GroupView.pressed(Tab, MouseEvent)`, `dragged(MouseEvent)`, `released(MouseEvent)` (`GroupView.reorder` goes);
  `WindowContent.tabDrag()`; the test helpers `TabDragTestSupport.frame` (the title bar is the frame's top inset,
  `TITLE` = 28), `place`, `relayout`, `onTitleBar`, `onScreen`,
  `middle`, `inside`, `groupOf`, `onTab`, `cards`, `entry`, `mouse`, `start`, `drop`, `motionListeners`, `broadcast`.

- [ ] **Step 1: The test support**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTestSupport.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Headless tab drags (workspace tabs spec 12): windows in stand-in frames on a pretend screen, and the mouse events Swing
 * sends a drag, in that screen's coordinates. A stand-in frame is a parentless panel at a place on the pretend screen: a
 * root pane holding the window's content, under a title bar that is the panel's top inset, as a native frame's insets
 * hold its title bar. Screen conversions, the drag's window bounds, the frame's decorations and the overlay's layered
 * pane then work without a display.
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

    /** Lays a stand-in frame out again, as Swing would after a drop changed its workspace. */
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

    /** The group view that holds {@code tab} in {@code window}. */
    static GroupView groupOf(WindowContent window, Tab tab) {
        return window.workspace().groupView(window.workspace().model().groupOf(tab.id()).orElseThrow());
    }

    /** A point in {@code tab}'s strip entry, {@code across} of its width from its left, on the pretend screen. */
    static Point onTab(WindowContent window, Tab tab, double across) {
        GroupView group = groupOf(window, tab);
        return inside(onScreen(group, group.tabBounds(group).get(group.tabs().indexOf(tab))), across, 0.5);
    }

    /** {@code group}'s card area in {@code window}, on the pretend screen. */
    static Rectangle cards(WindowContent window, GroupId group) {
        WorkspaceView workspace = window.workspace();
        return onScreen(workspace, workspace.groupView(group).contentBounds(workspace));
    }

    /** The component a press on {@code tab}'s strip entry reaches: the equal tabs' title button, or a classic tab. */
    static Component entry(WindowContent window, Tab tab) {
        GroupView group = groupOf(window, tab);
        Component strip = group.strip().component();
        Rectangle bounds = group.tabBounds(strip).get(group.tabs().indexOf(tab));
        return SwingUtilities.getDeepestComponentAt(strip, (int) bounds.getCenterX(), (int) bounds.getCenterY());
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
     * Presses in the middle of {@code tab}'s strip entry, drags 12 px down (past the 5 px threshold at any scale up to 2)
     * and on to each point, without releasing.
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

- [ ] **Step 2: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.EnterpriseLook;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 12 and 15: the tab drag within one window, driven by synthetic mouse events on its strips. */
class TabDragTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    private Tab a, b, c;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            owner = content(idleLauncher(pending));
            // A tab opening in a laid-out strip would move into its place over several EDT turns: tests take it at once.
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.newTab(HOME);
            owner.newTab(HOME);
            a = owner.tabs().get(0);
            b = owner.tabs().get(1);
            c = owner.tabs().get(2);
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

    @Test void aPressThatMovesNoFartherThanTheThresholdIsAClick() throws Exception {
        edt(() -> {
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabDrag().active()).as("4.2 px is within the threshold").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, new Point(press.x + 3, press.y + 3));
            assertThat(owner.tabs()).containsExactly(a, b, c);
            assertThat(owner.selectedTab()).as("the press selected it").isSameAs(a);
            assertThat(owner.tabDrag().label().showing()).isFalse();
        });
    }

    @Test void draggingATabAlongItsStripReordersIt() throws Exception {
        edt(() -> {
            GroupId group = owner.workspace().model().activeGroup();
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.active()).isTrue();
            assertThat(drag.dragged()).isSameAs(a);
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, new Drop.Strip(group, 2)));
            assertThat(drag.label().showing()).as("the tab's label follows the cursor").isTrue();
            assertThat(drag.label().text()).isEqualTo(owner.tabTitle(a));
            int offset = UIScale.scale(TabDrag.LABEL_OFFSET);
            assertThat(drag.label().location()).isEqualTo(new Point(pastC.x + offset, pastC.y + offset));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            assertThat(drag.overlay().getParent()).as("the landing shows in the window").isSameAs(layers);
            assertThat(drag.overlay().placeholder()).as("a bar in the strip").isNotNull();
            assertThat(drag.overlay().highlight()).isNull();
            assertThat(owner.tabs()).as("nothing moves before the release").containsExactly(a, b, c);
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(b, c, a);
            assertThat(owner.selectedTab()).isSameAs(a);
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).as("the landing goes").isNull();
        });
    }

    @Test void aDropThatWouldChangeNothingShowsNothingAndDoesNothing() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            Component source = start(owner, a, onTab(owner, a, 0.5));
            Layout before = workspace.model();
            assertThat(owner.tabDrag().active()).isTrue();
            assertThat(owner.tabDrag().target()).as("its own place in its strip").isEmpty();
            assertThat(owner.tabDrag().overlay().getParent()).as("no landing shows").isNull();
            Point centre = inside(cards(owner, before.activeGroup()), 0.5, 0.5);
            mouse(source, MouseEvent.MOUSE_DRAGGED, centre);
            assertThat(owner.tabDrag().target()).as("its own group's centre").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(workspace.model()).isEqualTo(before);
            workspace.moveToNewGroup(c, before.activeGroup(), Side.RIGHT);
            relayout(frame);
            GroupId alone = workspace.model().groupOf(c.id()).orElseThrow();
            Layout split = workspace.model();
            drop(owner, c, inside(cards(owner, alone), 0.05, 0.5));
            assertThat(workspace.model()).as("an edge of the group it is alone in").isEqualTo(split);
        });
    }

    @Test void aTabDroppedOnAnotherGroupsCentreJoinsThatGroup() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            GroupId left = workspace.model().activeGroup();
            workspace.moveToNewGroup(c, left, Side.RIGHT);
            relayout(frame);
            GroupId right = workspace.model().groupOf(c.id()).orElseThrow();
            Point centre = inside(cards(owner, right), 0.5, 0.5);
            Component source = start(owner, a, centre);
            TabDrag drag = owner.tabDrag();
            assertThat(drag.target()).contains(new TabDrag.Target.Into(owner, new Drop.Centre(right)));
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            GroupView view = workspace.groupView(right);
            assertThat(drag.overlay().highlight()).as("the whole group")
                .isEqualTo(SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), layers));
            assertThat(drag.overlay().placeholder()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, centre);
            assertThat(workspace.groupView(right).tabs()).containsExactly(c, a);
            assertThat(workspace.groupView(left).tabs()).containsExactly(b);
            assertThat(workspace.model().activeGroup()).isEqualTo(right);
            assertThat(owner.selectedTab()).isSameAs(a);
        });
    }

    @Test void aTabDroppedOnAGroupsOuterQuarterSplitsThatSide() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            GroupId group = workspace.model().activeGroup();
            Point low = inside(cards(owner, group), 0.5, 0.9);
            Component source = start(owner, a, low);
            assertThat(owner.tabDrag().target()).contains(new TabDrag.Target.Into(owner, new Drop.Edge(group, Side.DOWN)));
            GroupView view = workspace.groupView(group);
            var layers = SwingUtilities.getRootPane(owner).getLayeredPane();
            Rectangle whole = SwingUtilities.convertRectangle(view, new Rectangle(view.getSize()), layers);
            assertThat(owner.tabDrag().overlay().highlight()).as("the lower half, where the new group goes")
                .isEqualTo(new Rectangle(whole.x, whole.y + whole.height - whole.height / 2, whole.width, whole.height / 2));
            mouse(source, MouseEvent.MOUSE_RELEASED, low);
            relayout(frame);
            drop(owner, b, inside(cards(owner, group), 0.05, 0.5));
            var root = (Split) workspace.model().root();
            assertThat(root.orientation()).isEqualTo(Orientation.VERTICAL);
            var upper = (Split) root.children().get(0).node();
            assertThat(upper.orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(((Group) upper.children().get(0).node()).tabs()).as("b left of the upper group").containsExactly(b.id());
            assertThat(((Group) upper.children().get(1).node()).tabs()).containsExactly(c.id());
            assertThat(((Group) root.children().get(1).node()).tabs()).as("a below").containsExactly(a.id());
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    @Test void anEdgeDropInAMaximizedGroupSplitsItAndEndsTheMaximize() throws Exception {
        edt(() -> {
            var workspace = owner.workspace();
            workspace.moveToNewGroup(c, workspace.model().activeGroup(), Side.RIGHT);
            GroupId right = workspace.model().activeGroup();
            workspace.moveTab(b, right, 1);
            workspace.toggleMaximize();
            relayout(frame);
            assertThat(workspace.model().maximized()).contains(right);
            drop(owner, b, inside(cards(owner, right), 0.95, 0.5));
            assertThat(workspace.model().maximized()).as("a new group ends the maximize (spec 15)").isEmpty();
            assertThat(workspace.model().groupOf(b.id())).isNotEqualTo(Optional.of(right));
            assertThat(workspace.model().groups()).hasSize(3);
        });
    }

    @Test void escEndsTheDragWithoutADrop() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            TabDrag drag = owner.tabDrag();
            assertThat(keys.dispatchers).as("the drag owns Esc while it runs").hasSize(1);
            assertThat(keys.send(new KeyEvent(owner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))
                .as("consumed").isTrue();
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(drag.overlay().getParent()).isNull();
            assertThat(keys.dispatchers).isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).as("the release after Esc drops nothing").containsExactly(a, b, c);
        });
    }

    @Test void aLostReleaseEndsTheDrag() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            start(owner, a, onTab(owner, c, 0.75));
            assertThat(motionListeners()).as("it listens for a lost release while it runs").isEqualTo(listeners + 1);
            // Swing sends a plain move only while no button is held: the release never came.
            broadcast(new MouseEvent(owner.workspace(), MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false, MouseEvent.NOBUTTON));
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            start(owner, a, onTab(owner, c, 0.75));
            // So does another press, here on another tab; that press may start a drag of its own.
            Component other = entry(owner, b);
            mouse(other, MouseEvent.MOUSE_PRESSED, middle(other));
            assertThat(drag.active()).isFalse();
            mouse(other, MouseEvent.MOUSE_DRAGGED, onTab(owner, c, 0.75));
            assertThat(drag.dragged()).isSameAs(b);
            assertThat(owner.tabs()).as("nothing dropped").containsExactly(a, b, c);
        });
    }

    @Test void theWindowLosingTheFocusEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            // A modal dialog opening, or another application coming forward, takes the focus and the release with it.
            owner.setActive(false);
            assertThat(owner.tabDrag().active()).isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(a, b, c);
        });
    }

    @Test void theDraggedTabClosingEndsTheDrag() throws Exception {
        edt(() -> {
            Point pastC = onTab(owner, c, 0.75);
            Component source = start(owner, a, pastC);
            owner.closeTab(a);
            assertThat(owner.tabDrag().active()).isFalse();
            assertThat(owner.tabDrag().label().showing()).isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.tabs()).containsExactly(b, c);
        });
    }

    @Test void aTabThatClosesOrLeavesItsGroupUnderThePressDragsNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = entry(owner, a);
            Point press = middle(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, press);
            owner.closeTab(a);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 40, press.y));
            assertThat(drag.active()).as("a tab that closed since the press has nothing to drag").isFalse();
            assertThat(drag.label().showing()).isFalse();
            assertThat(motionListeners()).isEqualTo(listeners);
            relayout(frame);
            Point pastC = onTab(owner, c, 0.75);
            Component other = start(owner, b, pastC);
            assertThat(drag.active()).isTrue();
            // A command moves the dragged tab meanwhile (Split Right on it, say): the drag ends at its next event.
            owner.workspace().moveToNewGroup(b, owner.workspace().model().activeGroup(), Side.RIGHT);
            mouse(other, MouseEvent.MOUSE_DRAGGED, pastC);
            assertThat(drag.active()).isFalse();
            assertThat(drag.label().showing()).isFalse();
            mouse(other, MouseEvent.MOUSE_RELEASED, pastC);
            assertThat(owner.workspace().model().groups()).as("only the command's split").hasSize(2);
            assertThat(owner.workspace().model().group(owner.workspace().model().groupOf(c.id()).orElseThrow()).orElseThrow().tabs())
                .containsExactly(c.id());
        });
    }

    @Test void releasingOverTheWindowOutsideItsWorkspaceChangesNothing() throws Exception {
        edt(() -> {
            TabDrag drag = owner.tabDrag();
            Component source = start(owner, a, onTitleBar(frame));
            assertThat(drag.target()).as("over the title bar").isEmpty();
            Rectangle workspace = onScreen(owner.workspace(), new Rectangle(owner.workspace().getSize()));
            Point chrome = new Point(workspace.x + workspace.width / 2, workspace.y - 3);
            mouse(source, MouseEvent.MOUSE_DRAGGED, chrome);
            assertThat(drag.target()).as("over the chrome above the workspace").isEmpty();
            assertThat(drag.overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, chrome);
            assertThat(owner.tabs()).containsExactly(a, b, c);
            drop(owner, a, new Point(frame.getX() + frame.getWidth() + 200, frame.getY() + 100));
            assertThat(owner.tabs()).as("a window of its own moves nothing out").containsExactly(a, b, c);
            assertThat(owner.tabCount()).isEqualTo(3);
        });
    }

    @Test void classicTabsDragAlongTheirStripButTheirCloseButtonNeverDrags() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            WindowContent classic = null;
            try {
                classic = DesktopTestSupport.content(idleLauncher(pending), themes, ClassicTabsTest.classic());
                classic.newTab(HOME);
                classic.newTab(HOME);
                var tabs = classic.tabs();
                JPanel shell = frame(classic, 100, 50, 1208, 760);
                assertThat(classic.tabBar()).isInstanceOf(ClassicTabs.class);
                drop(classic, tabs.get(0), onTab(classic, tabs.get(2), 0.75));
                assertThat(classic.tabs()).containsExactly(tabs.get(1), tabs.get(2), tabs.get(0));
                relayout(shell);
                var entry = ((ClassicTabs) classic.tabBar()).entries().get(0);
                Rectangle close = onScreen(entry, entry.closeTarget());
                Point onClose = new Point(close.x + close.width / 2, close.y + close.height / 2);
                Point far = onTab(classic, tabs.get(0), 0.75);
                mouse(entry, MouseEvent.MOUSE_PRESSED, onClose);
                mouse(entry, MouseEvent.MOUSE_DRAGGED, far);
                assertThat(classic.tabDrag().active()).as("a press on the x never drags").isFalse();
                mouse(entry, MouseEvent.MOUSE_RELEASED, far);
                assertThat(classic.tabs()).as("nor closes once dragged off it").containsExactly(tabs.get(1), tabs.get(2), tabs.get(0));
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

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragTest'`
Expected: FAIL to compile: `cannot find symbol: class TabDrag` and `method tabDrag()`.

- [ ] **Step 4: The label window and the drag**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TabDragLabel.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JWindow;

/**
 * The small undecorated window that shows a dragged tab's label beside the cursor (workspace tabs spec 12): the chrome's
 * card colours inside a one-pixel window border, never focused, above every window, moved to each drag event and never
 * animated. A headless JVM has no windows: there the label keeps its text and place alone, which tests read. EDT only.
 */
final class TabDragLabel {
    private final JLabel label = new JLabel();
    private JWindow window;
    private Point location;

    TabDragLabel() {
        // A tab's title comes from the terminal (OSC 0/2): never HTML.
        label.putClientProperty("html.disable", true);
        label.setOpaque(true);
    }

    /** Shows {@code text} and {@code icon}, the label's top-left corner at {@code screen}. */
    void show(String text, Icon icon, Point screen) {
        label.setText(text);
        label.setIcon(icon);
        label.setFont(ThemeZone.font(Typography.Role.BODY, text));
        label.setForeground(ThemeZone.color(label, SemanticColor.TEXT_PRIMARY));
        label.setBackground(ThemeZone.color(label, SemanticColor.SURFACE_CARD));
        int vertical = UIScale.scale(4), horizontal = UIScale.scale(8);
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ThemeZone.color(label, SemanticColor.BORDER_WINDOW)),
            BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal)));
        if (!GraphicsEnvironment.isHeadless()) {
            if (window == null) {
                window = new JWindow();
                window.setType(Window.Type.POPUP);
                window.setFocusableWindowState(false);
                window.setAlwaysOnTop(true);
                window.getContentPane().add(label, BorderLayout.CENTER);
            }
            window.pack();
        }
        move(screen);
        if (window != null) window.setVisible(true);
    }

    /** Puts the label's top-left corner at {@code screen}. */
    void move(Point screen) {
        location = new Point(screen);
        if (window != null) window.setLocation(location);
    }

    /** Hides the label and releases its window. */
    void hide() {
        location = null;
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    /** @return whether the label shows */
    boolean showing() { return location != null; }

    /** @return the label's text */
    String text() { return label.getText(); }

    /** @return where the label's top-left corner is on screen while it shows, else null */
    Point location() { return location == null ? null : new Point(location); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
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
 * The tab drag (workspace tabs spec 12). A left press on a tab in a strip of either face that moves past the kit's tab
 * drag threshold ({@code TAB_REORDER_THRESHOLD}, 5 px by default) starts it. It is the strips' only drag, so reordering a
 * strip and moving a tab to another group are one gesture. It uses plain mouse events in screen coordinates, never AWT
 * drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor goes, and synthetic events
 * drive it in tests. While it runs, {@link TabDragLabel} shows the tab's label beside the cursor and the target window's
 * layered pane shows where the tab would land ({@link DockDropOverlay}, from {@link WorkspaceDropResolver#landing});
 * nothing moves until the release.
 * <p>The target: of the windows under the cursor, the most recently active; over its workspace, the resolver's drop
 * unless it would change nothing; anywhere else in that window (its dock, chrome or title bar), and outside every
 * window, nothing. A pinned tab (a modal owner holds it in its window) takes targets in its own window only. Releasing
 * the left button carries the target out through the workspace's layout operations, which never touch a tab's content
 * or session. Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's
 * window losing the focus (a modal dialog, another application), and the dragged tab closing or leaving its window or
 * its group (a command moving it) end the drag without a drop. EDT only.
 */
final class TabDrag {
    /** Where a release would put the dragged tab. */
    sealed interface Target {
        /**
         * Into {@code window}'s workspace, where {@code drop} says.
         *
         * @param window the window
         * @param drop the drop over its workspace
         */
        record Into(WindowContent window, Drop drop) implements Target { }
    }

    /** How far right of and below the cursor the label's corner sits, so no window is ever under the pointer's tip. */
    static final int LABEL_OFFSET = 8;

    private final Supplier<List<WindowContent>> windows;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final TabDragLabel label = new TabDragLabel();
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;
    private final AWTEventListener lostRelease = this::lostRelease;
    /** The focus manager the Esc dispatcher went to, while a drag runs. */
    private KeyboardFocusManager keys;
    /** While a press is pending or a drag runs: the tab's group, the tab, the component the press reached, the press on screen. */
    private GroupView group;
    private Tab tab;
    private Component source;
    private Point pressedAt;
    private boolean dragging;
    /** End the drag when the dragged tab closes, or changes so that it is no longer where the press found it. */
    private Subscription closing, changed;
    private Optional<Target> target = Optional.empty();

    /** @param windows the windows a drag can reach, the most recently active first */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }

    /** @return whether a drag runs: from the threshold to its release or its end */
    boolean active() { return dragging; }

    /** @return the dragged tab while a drag runs, else null */
    Tab dragged() { return dragging ? tab : null; }

    /** @return where a release would put the tab now */
    Optional<Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    TabDragLabel label() { return label; }

    /**
     * A left press on {@code tab}'s entry in {@code group}'s strip: a drag may start from it. A press while another is
     * pending or a drag runs ends that one first.
     */
    void pressed(GroupView group, Tab tab, MouseEvent event) {
        if (this.tab != null) cancel();
        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || !group.tabs().contains(tab)) return;
        this.group = group;
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
    }

    /**
     * A drag event from the pressed component: past the threshold the drag starts, then follows the cursor. A tab that
     * closed or moved since the press has nothing to drag.
     */
    void dragged(MouseEvent event) {
        if (tab == null || event.getComponent() != source) return;
        Point at = screen(event);
        if (!dragging) {
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
        Optional<Target> where = Optional.empty();
        try {
            update(screen(event));
            where = target;
        } finally {
            finish();
        }
        where.ifPresent(place -> drop(moving, place));
    }

    /** Ends a running drag or a pending press without a drop. */
    void cancel() { finish(); }

    /** {@code window} lost the focus or closed: a press or a drag from it ends without a drop. */
    void cancelFrom(WindowContent window) {
        if (group != null && group.owner() == window) cancel();
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

    /** Whether the tab is still where the press found it: open, in the press's window and in the press's group. */
    private boolean inPlace() {
        return tab != null && !tab.closed() && Objects.equals(tab.window(), group.owner().id())
            && group.owner().workspace().model().groupOf(tab.id()).filter(group.id()::equals).isPresent();
    }

    /** Starts the drag: what ends it is in place before anything shows, so {@link #finish} can always take it away. */
    private void start(Point at) {
        dragging = true;
        keys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keys.addKeyEventDispatcher(escape);
        Toolkit.getDefaultToolkit().addAWTEventListener(lostRelease, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        closing = tab.onClosed(this::cancel);
        // A command that moves the tab while it is dragged (Move Tab to New Window from the keyboard) ends the drag.
        changed = tab.onChanged(() -> { if (!inPlace()) cancel(); });
        // The title button saw the press: its release after a drag must not click it too.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        label.show(WindowTabs.capped(group.title(tab)), group.icon(tab), labelAt(at));
    }

    /** Finds the target under {@code at}, moves the label and shows the landing; a tab no longer in place ends the drag. */
    private void update(Point at) {
        if (!inPlace()) {
            cancel();
            return;
        }
        label.move(labelAt(at));
        target = Optional.empty();
        List<WindowContent> open = windows.get().stream().filter(window -> !window.closed()).toList();
        OptionalInt under = WorkspaceDropResolver.windowAt(at, open.stream().map(TabDrag::onScreen).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A pinned tab stays in its window, as the registry keeps it there: no other window takes it.
            if (!tab.pinned() || window == group.owner()) target = into(window, at);
        }
        showLanding();
    }

    /** The drop into {@code window}'s workspace at {@code at}; empty outside its workspace or for a drop that would change nothing. */
    private Optional<Target> into(WindowContent window, Point at) {
        WorkspaceView workspace = window.workspace();
        Point local = new Point(at);
        SwingUtilities.convertPointFromScreen(local, workspace);
        return WorkspaceDropResolver.resolve(local, workspace.dropAreas(tab)).filter(drop -> workspace.changes(tab, drop))
            .map(drop -> new Target.Into(window, drop));
    }

    /**
     * Shows the target's landing on its window's layered pane: the area the tab would take as the highlight, or for a strip
     * drop a bar where it would go in the strip as the placeholder. Without a target the overlay leaves its window.
     */
    private void showLanding() {
        WindowContent window = target.orElse(null) instanceof Target.Into into ? into.window() : null;
        JLayeredPane layers = window == null ? null : layers(window);
        Container parent = overlay.getParent();
        if (parent != null && parent != layers) {
            overlay.clear();
            parent.remove(overlay);
            parent.repaint();
        }
        if (layers == null) return;
        Drop drop = ((Target.Into) target.orElseThrow()).drop();
        WorkspaceView workspace = window.workspace();
        Rectangle landing = SwingUtilities.convertRectangle(workspace, WorkspaceDropResolver.landing(drop, workspace.dropAreas(tab),
            NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR)), layers);
        if (overlay.getParent() == null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        if (drop instanceof Drop.Strip) overlay.show(null, landing, null, null);
        else overlay.show(landing, null, null, null);
    }

    /** Ends a pending press or a drag: its subscriptions, its listeners, the label and the landing go. */
    private void finish() {
        boolean wasDragging = dragging;
        dragging = false;
        group = null;
        tab = null;
        source = null;
        pressedAt = null;
        target = Optional.empty();
        if (closing != null) {
            closing.close();
            closing = null;
        }
        if (changed != null) {
            changed.close();
            changed = null;
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

    /** Carries a release out: the window's workspace places the tab; its content and session are never touched. */
    private void drop(Tab moving, Target where) {
        if (moving.closed() || !(where instanceof Target.Into into) || into.window().closed()) return;
        into.window().workspace().drop(moving, into.drop());
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

    /**
     * {@code window}'s frame on screen, title bar and all: its native window's bounds, or its outermost component's for
     * content in no native window; empty while it does not show or is minimized.
     */
    private static Rectangle onScreen(WindowContent window) {
        Component top = outermost(window);
        boolean shows = top instanceof Window frame
            ? frame.isShowing() && !(frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
            : top.isVisible();
        if (!shows) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }

    private static JLayeredPane layers(WindowContent window) {
        JRootPane root = SwingUtilities.getRootPane(window);
        return root == null ? null : root.getLayeredPane();
    }
}
```

- [ ] **Step 5: The strips start it; the window holds it**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "TabStrip.java", [(
    " * tabs, always shown, even for one tab. It lists, selects, closes, reorders and opens tabs in its group view only, and\n"
    " * shows as active while that group is the active group of an active window. EDT only.\n",
    " * tabs, always shown, even for one tab. It lists, selects, closes and opens tabs in its group view only, its presses start\n"
    " * the tab drag (workspace tabs spec 12), and it shows as active while that group is the active group of an active window.\n"
    " * EDT only.\n", 1)])
edit(W + "WindowTabs.java", [("        private boolean hovered;\n        private Point origin;\n", "        private boolean hovered;\n", 1)])
span(W + "WindowTabs.java", "                @Override public void mousePressed(MouseEvent event) {\n                    origin = null;\n",
     "            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);\n",
     """                @Override public void mousePressed(MouseEvent event) {
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { group.close(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        group.select(tab);
                        // The tab drag may start here (workspace tabs spec 12).
                        group.pressed(tab, event);
                    }
                }
                @Override public void mouseDragged(MouseEvent event) { group.dragged(event); }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    group.released(event);
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            addMouseMotionListener(gestures); select.addMouseMotionListener(gestures); close.addMouseMotionListener(gestures);
""")
edit(W + "ClassicTabs.java", [
    ("import java.awt.Point;\n", "", 1),
    ("        private boolean closeHover, closePressed;\n        private Point origin;\n", "        private boolean closeHover, closePressed;\n", 1)])
span(W + "ClassicTabs.java", "                @Override public void mousePressed(MouseEvent event) {\n                    origin = null;\n",
     "                @Override public void mouseDragged(MouseEvent event) { if (closePressed) hover(event); }\n",
     """                @Override public void mousePressed(MouseEvent event) {
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { group.close(tab); return; }
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    // A press on the × neither selects nor drags; dragging off it before the release cancels the close.
                    if (closeTarget().contains(event.getPoint())) { closePressed = true; return; }
                    group.select(tab);
                    // The tab drag may start here (workspace tabs spec 12).
                    group.pressed(tab, event);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    boolean closing = closePressed;
                    closePressed = false;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (closing) {
                        if (SwingUtilities.isLeftMouseButton(event) && closeTarget().contains(event.getPoint())) closeClicked();
                        return;
                    }
                    group.released(event);
                }
                @Override public void mouseMoved(MouseEvent event) { hover(event); }
                // While the × is held, its hover shows whether a release would close; otherwise the tab drag follows the cursor.
                @Override public void mouseDragged(MouseEvent event) { if (closePressed) hover(event); else group.dragged(event); }
""")
edit(W + "GroupView.java", [
    ("import java.awt.Rectangle;\n", "import java.awt.Rectangle;\nimport java.awt.event.MouseEvent;\n", 1),
    (""" * showing the group's selected tab. The strip lists, selects, closes, reorders and opens tabs in this group only. A tab's
 * component enters and leaves the card area as the layout puts the tab in and out of the group, so a tab moved to
 * another group leaves one group view for the other with its session untouched (spec 2.3). EDT only.
""", """ * showing the group's selected tab. The strip lists, selects, closes and opens tabs in this group only and starts the tab
 * drag (spec 12). A tab's component enters and leaves the card area as the layout puts the tab in and out of the group,
 * so a tab moved to another group leaves one group view for the other with its session untouched (spec 2.3). EDT only.
""", 1),
    ("""    /** A strip drag: moves the tab to {@code index} of this group, keeping the selection. */
    void reorder(Tab tab, int index) { workspace.reorder(tab, id, index); }
""", """    /** A left press on {@code tab}'s entry in the strip: the tab drag may start from it (workspace tabs spec 12). */
    void pressed(Tab tab, MouseEvent event) { owner.tabDrag().pressed(this, tab, event); }
    /** A drag event from the strip, which the tab drag follows. */
    void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }
    /** A release on the strip, which ends the tab drag. */
    void released(MouseEvent event) { owner.tabDrag().released(event); }
""", 1)])
edit(W + "WindowContent.java", [
    ("    private final DockDrag dockDrag;\n",
     "    private final DockDrag dockDrag;\n"
     "    /** Drags a tab from any of this window's strips (workspace tabs spec 12). */\n"
     "    private final TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this));\n", 1),
    ("    DockDrag dockDrag() { return dockDrag; }\n",
     "    DockDrag dockDrag() { return dockDrag; }\n    /** The tab drag this window's strips start. */\n    TabDrag tabDrag() { return tabDrag; }\n", 1),
    ("        if (!value) dockDrag.cancel();\n",
     "        if (!value) { dockDrag.cancel(); tabDrag.cancelFrom(this); }\n", 1),
    ("        dockDrag.cancel();\n        paletteKeys.close();\n",
     "        dockDrag.cancel();\n        tabDrag.cancelFrom(this);\n        paletteKeys.close();\n", 1)])
edit(W + "DockDropOverlay.java", [(
    " * The drag guides, on the layered pane's DRAG_LAYER:",
    " * The drag guides of a dock drag and of a tab drag ({@link TabDrag}: the area the tab would take as the highlight, its\n"
    " * place in a strip as the placeholder), on the layered pane's DRAG_LAYER:", 1)])
PY
```

- [ ] **Step 6: The tests that leaned on the old gesture**

`GroupView.reorder` is gone, so four tests reorder through the workspace; the strip drag test now drags; the metrics
test finds the tab drag threshold in `TabDrag`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
for name in ("HostedContentTest.java", "WindowTabsTest.java"):
    edit(T + name, [("owner.workspace().activeGroupView().reorder(owner.tabs().get(1), 0);",
                     "owner.workspace().reorder(owner.tabs().get(1), owner.workspace().model().activeGroup(), 0);", 1)])
edit(T + "TerminalContentTest.java", [("owners[1].workspace().activeGroupView().reorder(owners[1].tabs().get(0), 1);",
     "owners[1].workspace().reorder(owners[1].tabs().get(0), owners[1].workspace().model().activeGroup(), 1);", 1)])
edit(T + "WorkspaceViewTest.java", [("owner.workspace().activeGroupView().reorder(tabs.get(2), 0);",
     "owner.workspace().reorder(tabs.get(2), owner.workspace().model().activeGroup(), 0);", 1)])
edit(T + "WindowTabsTest.java", [("""            layout(strip, 600, 38);
            JComponent one = named(strip, "select:first"), two = named(strip, "select:second");
            Point target = SwingUtilities.convertPoint(two, 10, 10, one);
            mouse(one, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
            mouse(one, MouseEvent.MOUSE_RELEASED, target.x, target.y, MouseEvent.BUTTON1);
""", """            // The whole window lays out, so the tab drag finds the strip inside it (workspace tabs spec 12).
            layout(owner, 800, 600);
            JComponent one = named(strip, "select:first"), two = named(strip, "select:second");
            Point target = SwingUtilities.convertPoint(two, two.getWidth() - 10, 10, one);
            mouse(one, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
            mouse(one, MouseEvent.MOUSE_DRAGGED, target.x, target.y, MouseEvent.NOBUTTON);
            mouse(one, MouseEvent.MOUSE_RELEASED, target.x, target.y, MouseEvent.BUTTON1);
""", 1)])
edit(T + "WorkspaceMetricsTest.java", [("""                "TAB_CLOSE_WIDTH", "TAB_CLOSE_TRAILING", "ChromePainter.installed().tabStrip", "TAB_REORDER_THRESHOLD"),
""", """                "TAB_CLOSE_WIDTH", "TAB_CLOSE_TRAILING", "ChromePainter.installed().tabStrip"),
            // Workspace tabs plan 5: every strip's drag starts past the kit's tab drag threshold, in the one tab drag.
            "TabDrag.java", List.of("TAB_REORDER_THRESHOLD"),
""", 1)])
PY
```

- [ ] **Step 7: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "TabDrag.java", "app.workspace", "TabDrag", T + "TabDragTest.java",
           "New in workspace-tabs plan 5 Task 3; the tab drag from any strip")
ledger.add(M + "TabDragLabel.java", "app.workspace", "TabDragLabel", T + "TabDragTest.java",
           "New in workspace-tabs plan 5 Task 3; a dragged tab's label beside the cursor")
ledger.add(T + "TabDragTestSupport.java", "app.workspace", "TabDragTestSupport", T + "TabDragTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 5 Task 3; stand-in frames and drag events on a pretend screen")
ledger.add(T + "TabDragTest.java", "app.workspace", "TabDragTest", T + "TabDragTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 5 Task 3; the tab drag within one window")
PY
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`TabDragTest` 13; `WindowTabsTest`, `WorkspaceMetricsTest`, `WorkspaceViewTest`, `HostedContentTest`,
`TerminalContentTest`, `ClassicTabsTest` and `DockDragTest` unchanged in count). Then `./gradlew :jasper-app:check`:
PASS.

- [ ] **Step 9: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tab drag: one gesture from every strip, within a window

A left press on a tab of either strip face that moves past the kit's tab
drag threshold starts TabDrag, which replaces the strips' release-to-
reorder: a label window follows the cursor, the target window shows the
landing, and the release reorders the strip, moves the tab into another
group or splits a group's side. Esc, a lost release, the window losing
the focus and the tab closing end it without a drop.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: One drag across every window: drag-back and tear-out

Spec 12, 15 and 17: the window manager gives every window it opens its one `TabDrag`, which reaches all of them, most
recently active first; on macOS a window in full screen other than the drag's own takes no drop. A drop on another
window moves the tab through the tab registry (its session and content untouched, both maximizes ended, `TAB_MOVED`
reported, a child window it empties closed after the move): an edge drop straight into a new group beside that group
through plan 4b's `WorkspaceHost.attachBeside`, any other to the end of that window's active group, where the drop puts
it in place. A release over another window of Jasper's (Settings, About) drops nothing. A release outside every window
tears the tab out through plan 4a's bounds-taking `moveToNewWindow`: the new window's workspace is the old group's size
with the tab under the cursor, its frame grown by its own title bar and kept on the display there; a child window's
only tab and a pinned tab stay (Rulings 9-13, 17, 18).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java`, `TabDragLabel.java`, `WindowContent.java`,
  `WindowManager.java`, `WindowFrame.java`, `WindowShell.java`;
  `jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java`; modified
  `jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java`
- Ledger: one row

**Interfaces:**
- Consumes: Task 3's `TabDrag`, `TabDragTestSupport`; plan 4a's `WindowManager.windowsByActivation()`,
  `moveToNewWindow(Tab, Rectangle bounds)` (through `Opening.bounds` and `WindowShell.place` before it shows; this task
  makes the bounds the new window's workspace, which `WindowFrame` grows by its title bar),
  `TabRegistry.Windows.movedOut` (a child window a move empties closes once the move has reported itself),
  `WindowManager.Hooks` with its eight hooks, the seven-function `WindowShell`; plan 4b's
  `WorkspaceHost.Beside.attach(Tab, GroupId, Side)`.
- Produces: `TabDrag(Supplier<List<WindowContent>> windows, BiConsumer<Tab, Rectangle> tearOut)`, `record
  Target.TearOut(Rectangle bounds)` (the new window's workspace on screen), `static Rectangle TabDrag.fitted(Rectangle
  bounds, Rectangle usable)` (`WindowFrame` calls it too), `static Rectangle TabDrag.dropFrame(Rectangle frame,
  Rectangle display, boolean source, boolean macOs)`, the test seam `Supplier<List<Rectangle>> TabDrag.otherWindows`;
  `boolean TabDragLabel.owns(Window)`; `WindowContent.tabDrag(TabDrag shared)`; package `TabDrag
  WindowManager.tabDrag()`; `public void TabRegistry.move(Tab tab, UUID window, GroupId group, Side side)`;
  `WindowFrame.shell()`'s `place` puts the frame where its workspace takes the bounds.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 12, 15 and 17: one tab drag across Jasper's windows, on synthetic events, headless. The window
 * manager's windows stand in stand-in frames on a pretend screen, each under a 28 px title bar: the main window, with
 * two terminal tabs, at 0,0 (1208 x 800), a child window, with one, beside it at 1400,100 (800 x 600), and a new window
 * far away until a test places it.
 */
class TabDragWindowsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Each window's stand-in frame, by window id. */
    private final Map<UUID, JPanel> frames = new HashMap<>();
    /** Where the window manager put tear-out windows' frames on the pretend screen. */
    private final List<Rectangle> placed = new ArrayList<>();
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
            main.content().newTab(HOME);
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
            component -> SwingUtilities.isDescendingFrom(component, shell), placed::add);
    }

    private JPanel frameOf(JasperWindow window) { return frames.get(window.id()); }

    private static GroupId activeGroup(JasperWindow window) { return window.content().workspace().model().activeGroup(); }

    private static Object session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    private List<TerminalEvent> moves() { return facts.stream().filter(TerminalEvent.TabMoved.class::isInstance).toList(); }

    @Test void everyWindowOfTheManagerSharesItsOneDrag() throws Exception {
        edt(() -> {
            assertThat(main.content().tabDrag()).isSameAs(manager.tabDrag());
            assertThat(child.content().tabDrag()).isSameAs(manager.tabDrag());
            Tab tab = main.content().tabs().get(0);
            Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
            Component source = start(main.content(), tab, there);
            child.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("another window losing the focus leaves the drag alone").isTrue();
            main.content().setActive(false);
            assertThat(manager.tabDrag().active()).as("its own window losing it ends it").isFalse();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(tab.window()).isEqualTo(main.id());
        });
    }

    @Test void aTabDroppedOnAnotherWindowsStripMovesThereWithItsSession() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab tab = from.tabs().get(0), there = to.tabs().get(0);
            Object session = session(tab);
            assertThat(session).as("its shell started").isNotNull();
            facts.clear();
            Point before = onTab(to, there, 0.25);
            Component source = start(from, tab, before);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.Into(to, new Drop.Strip(activeGroup(child), 0)));
            assertThat(manager.tabDrag().overlay().getParent()).as("the landing shows in the target window")
                .isSameAs(SwingUtilities.getRootPane(to).getLayeredPane());
            mouse(source, MouseEvent.MOUSE_RELEASED, before);
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(to.tabs()).as("before the child window's tab").containsExactly(tab, there);
            assertThat(to.selectedTab()).isSameAs(tab);
            assertThat(from.tabs()).hasSize(1);
            assertThat(session(tab)).as("the same session").isSameAs(session);
            assertThat(((TerminalContent) tab.content()).pane().session().exitFuture()).isNotDone();
            assertThat(moves()).as("one move, reported where it arrives")
                .containsExactly(new TerminalEvent.TabMoved(tab.id().value(), main.id(), child.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
        });
    }

    @Test void aTabDroppedOnAnotherWindowsGroupEdgeOpensAGroupBesideIt() throws Exception {
        edt(() -> {
            WindowContent from = main.content(), to = child.content();
            Tab tab = from.tabs().get(0), there = to.tabs().get(0);
            GroupId group = activeGroup(child);
            facts.clear();
            drop(from, tab, inside(cards(to, group), 0.95, 0.5));
            var layout = to.workspace().model();
            assertThat(layout.groups()).hasSize(2);
            assertThat(((Split) layout.root()).orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(layout.groupOf(there.id())).contains(group);
            assertThat(layout.groupOf(tab.id())).isNotEqualTo(layout.groupOf(there.id()));
            assertThat(layout.activeTab()).contains(tab.id());
            assertThat(moves()).hasSize(1);
        });
    }

    @Test void aTabDroppedIntoAnotherWindowsMaximizedGroupEndsThatMaximize() throws Exception {
        edt(() -> {
            WindowContent to = main.content();
            Tab right = to.tabs().get(1);
            to.workspace().moveToNewGroup(right, activeGroup(main), Side.RIGHT);
            to.workspace().toggleMaximize();
            relayout(frameOf(main));
            GroupId maximized = activeGroup(main);
            assertThat(to.workspace().model().maximized()).contains(maximized);
            Tab tab = child.content().tabs().get(0);
            drop(child.content(), tab, onTab(to, right, 0.75));
            assertThat(to.workspace().model().maximized()).as("a tab moving in ends it (spec 15)").isEmpty();
            assertThat(to.workspace().model().group(maximized).orElseThrow().tabs()).containsExactly(right.id(), tab.id());
        });
    }

    @Test void aChildWindowsLastTabDraggedBackClosesItAfterTheMoveReportsItself() throws Exception {
        edt(() -> {
            Tab tab = child.content().tabs().get(0);
            Object session = session(tab);
            GroupId group = activeGroup(main);
            facts.clear();
            drop(child.content(), tab, inside(cards(main.content(), group), 0.5, 0.5));
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(main.content().workspace().model().group(group).orElseThrow().tabs()).as("after the group's tabs").endsWith(tab.id());
            assertThat(child.closed()).as("its last tab moved out").isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(session(tab)).isSameAs(session);
            int moved = facts.indexOf(new TerminalEvent.TabMoved(tab.id().value(), child.id(), main.id()));
            int closed = facts.indexOf(new TerminalEvent.WindowClosed(child.id()));
            assertThat(moved).as("TAB_MOVED").isNotNegative();
            assertThat(closed).as("then WINDOW_CLOSED").isGreaterThan(moved);
        });
    }

    @Test void aTabReleasedOutsideEveryWindowTearsOutWithItsGroupsSizeUnderTheCursor() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab tab = from.tabs().get(1);
            GroupView group = groupOf(from, tab);
            Dimension size = group.getSize();
            Point press = middle(entry(from, tab));
            Rectangle home = onScreen(group, group.tabBounds(group).get(group.tabs().indexOf(tab)));
            Object session = session(tab);
            Point outside = new Point(2600, 900);
            // The workspace keeps the group's size and the tab stays under the cursor; its frame adds its title bar.
            Rectangle expected = new Rectangle(outside.x - (press.x - home.x), outside.y - (press.y - home.y),
                size.width, size.height);
            facts.clear();
            Component source = start(from, tab, outside);
            assertThat(manager.tabDrag().target()).contains(new TabDrag.Target.TearOut(expected));
            assertThat(manager.tabDrag().overlay().getParent()).as("no landing in any window").isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            ChildWindow torn = manager.children().getLast();
            assertThat(torn).isNotSameAs(child);
            assertThat(tab.window()).isEqualTo(torn.id());
            assertThat(torn.content().tabs()).containsExactly(tab);
            assertThat(placed).as("its workspace, which its frame grows by its title bar").containsExactly(expected);
            assertThat(session(tab)).isSameAs(session);
            assertThat(moves()).containsExactly(new TerminalEvent.TabMoved(tab.id().value(), main.id(), torn.id()));
            assertThat(facts).as("the window opens empty, then the tab moves in")
                .noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
        });
    }

    @Test void aPluginsTabTearsOutAndDragsBackLikeATerminalTab() throws Exception {
        edt(() -> {
            JPanel view = new JPanel();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), view);
            relayout(frameOf(main));
            drop(main.content(), notes, new Point(3000, 1200));
            ChildWindow torn = manager.children().getLast();
            assertThat(notes.window()).isEqualTo(torn.id());
            assertThat(notes.component()).as("the same view").isSameAs(view);
            assertThat(view.isVisible()).as("shown in its new window").isTrue();
            relayout(frameOf(torn));
            drop(torn.content(), notes, inside(cards(main.content(), activeGroup(main)), 0.5, 0.5));
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(torn.closed()).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(view, main.content())).isTrue();
            assertThat(view.isVisible()).as("shown again").isTrue();
            assertThat(notes.closed()).isFalse();
        });
    }

    @Test void theOnlyTabOfAChildWindowDraggedOutsideEveryWindowStays() throws Exception {
        edt(() -> {
            Tab only = child.content().tabs().get(0);
            Point outside = new Point(3000, 1500);
            Component source = start(child.content(), only, outside);
            assertThat(manager.tabDrag().active()).isTrue();
            assertThat(manager.tabDrag().target()).as("its window already is its own").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, outside);
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(only.window()).isEqualTo(child.id());
        });
    }

    @Test void releasingOverAWindowOutsideItsWorkspaceCancels() throws Exception {
        edt(() -> {
            Tab tab = main.content().tabs().get(0);
            Point title = onTitleBar(frameOf(child));
            Component source = start(main.content(), tab, title);
            assertThat(manager.tabDrag().target()).as("over the child window's title bar").isEmpty();
            mouse(source, MouseEvent.MOUSE_RELEASED, title);
            Tab only = child.content().tabs().get(0);
            Rectangle workspace = onScreen(main.content().workspace(), new Rectangle(main.content().workspace().getSize()));
            Point chrome = new Point(workspace.x + workspace.width / 2, workspace.y - 3);
            drop(child.content(), only, chrome);
            assertThat(tab.window()).isEqualTo(main.id());
            assertThat(only.window()).as("over the main window's chrome").isEqualTo(child.id());
            assertThat(manager.children()).containsExactly(child);
            assertThat(placed).isEmpty();
            assertThat(moves()).isEmpty();
        });
    }

    @Test void whereWindowsOverlapTheMostRecentlyActiveOneTakesTheDrop() throws Exception {
        edt(() -> {
            place(frameOf(child), 300, 200, 800, 500);
            Point overlap = inside(cards(child.content(), activeGroup(child)), 0.5, 0.5);
            Rectangle mainFrame = onScreen(frameOf(main), new Rectangle(frameOf(main).getSize()));
            assertThat(mainFrame.contains(overlap)).as("over both windows").isTrue();
            child.events().activated().accept(true);
            Tab tab = main.content().tabs().get(0);
            drop(main.content(), tab, overlap);
            assertThat(tab.window()).as("the child window came to the front last").isEqualTo(child.id());
            main.events().activated().accept(true);
            relayout(frameOf(child));
            Tab back = child.content().tabs().get(0);
            drop(child.content(), back, overlap);
            assertThat(back.window()).as("now the main window did").isEqualTo(main.id());
        });
    }

    @Test void aPinnedTabMovesOnlyWithinItsWindow() throws Exception {
        edt(() -> {
            WindowContent from = main.content();
            Tab tab = from.tabs().get(0), other = from.tabs().get(1);
            var pin = tab.pin();
            try {
                Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
                Component source = start(from, tab, there);
                assertThat(manager.tabDrag().active()).isTrue();
                assertThat(manager.tabDrag().target()).as("a modal owner holds it in its window").isEmpty();
                Point outside = new Point(2600, 900);
                mouse(source, MouseEvent.MOUSE_DRAGGED, outside);
                assertThat(manager.tabDrag().target()).as("nor does it tear out").isEmpty();
                Point past = onTab(from, other, 0.75);
                mouse(source, MouseEvent.MOUSE_DRAGGED, past);
                assertThat(manager.tabDrag().target()).as("its own window still takes it").isPresent();
                mouse(source, MouseEvent.MOUSE_RELEASED, past);
                assertThat(from.tabs()).containsExactly(other, tab);
                assertThat(manager.children()).containsExactly(child);
            } finally {
                pin.close();
            }
        });
    }

    @Test void aTabMovedToAnotherWindowDuringTheDragEndsIt() throws Exception {
        edt(() -> {
            Tab tab = main.content().tabs().get(0);
            Point there = onTab(child.content(), child.content().tabs().get(0), 0.25);
            Component source = start(main.content(), tab, there);
            assertThat(manager.tabDrag().active()).isTrue();
            // Move Tab to New Window from the keyboard while the mouse still drags the tab.
            ChildWindow moved = manager.moveToNewWindow(tab).orElseThrow();
            assertThat(manager.tabDrag().active()).isFalse();
            assertThat(manager.tabDrag().label().showing()).isFalse();
            assertThat(manager.tabDrag().overlay().getParent()).isNull();
            mouse(source, MouseEvent.MOUSE_RELEASED, there);
            assertThat(tab.window()).as("where the command put it").isEqualTo(moved.id());
            assertThat(child.content().tabs()).hasSize(1);
        });
    }

    @Test void releasingOverAnotherWindowOfJaspersLikeSettingsDropsNothing() throws Exception {
        edt(() -> {
            // A headless JVM has no windows: the drag is told where a Settings frame stands on the pretend screen.
            manager.tabDrag().otherWindows = () -> List.of(new Rectangle(2400, 700, 600, 500));
            Tab tab = main.content().tabs().get(1);
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

    @Test void onMacOsAWindowInFullScreenOtherThanTheDragsOwnTakesNoDrop() {
        Rectangle display = new Rectangle(0, 0, 1512, 982);
        assertThat(TabDrag.dropFrame(display, display, false, true)).as("full screen: a Space of its own").isEqualTo(new Rectangle());
        assertThat(TabDrag.dropFrame(display, display, true, true)).as("the drag's own window is in sight").isEqualTo(display);
        assertThat(TabDrag.dropFrame(new Rectangle(0, 25, 1512, 957), display, false, true)).as("zoomed, under the menu bar")
            .isEqualTo(new Rectangle(0, 25, 1512, 957));
        assertThat(TabDrag.dropFrame(display, display, false, false)).as("elsewhere a window covering its display is in sight")
            .isEqualTo(display);
        assertThat(TabDrag.dropFrame(display, null, false, true)).as("no display known").isEqualTo(display);
    }

    @Test void aTearOutWindowIsKeptOnTheDisplayUnderTheCursor() {
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        assertThat(TabDrag.fitted(new Rectangle(100, 100, 800, 600), usable)).as("on it: as it is").isEqualTo(new Rectangle(100, 100, 800, 600));
        assertThat(TabDrag.fitted(new Rectangle(1000, 600, 800, 600), usable)).as("past its right and bottom edges: moved onto it")
            .isEqualTo(new Rectangle(640, 300, 800, 600));
        assertThat(TabDrag.fitted(new Rectangle(-50, 0, 2000, 1200), usable)).as("larger: as large as it").isEqualTo(usable);
        assertThat(TabDrag.fitted(new Rectangle(5, 5, 800, 600), new Rectangle())).as("no display known: as it is")
            .isEqualTo(new Rectangle(5, 5, 800, 600));
    }
}
```

Add a test to `jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java", [
    ("import dev.jasper.app.layout.GroupId;\n", "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Side;\n", 1),
    ("""    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
""", """    /** Workspace tabs spec 12: a tab dropped on a group's edge in another window lands beside that group, as a move. */
    @Test void aTabMovesIntoANewGroupBesideAGroupOfAnotherWindow() {
        var beside = new ArrayList<String>();
        var group = GroupId.random();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, mounted::add, mounted::remove, tab -> {}, () -> {}, tab -> Optional.empty(),
            (tab, at, side) -> { beside.add(tab.title() + " " + at.equals(group) + " " + side + " moving " + tab.moving()); mounted.add(tab); }));
        registry.windows(windows(main, detached));
        var tab = registry.open("plugin.file", "notes", "Notes", main, new JPanel());
        registry.move(tab, detached, group, Side.DOWN);
        assertThat(beside).containsExactly("Notes true DOWN moving true");
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.moving()).isFalse();
        registry.move(tab, detached, group, Side.UP);
        assertThat(beside).as("a tab already there stays").hasSize(1);
    }

    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
""", 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --tests 'dev.jasper.app.contributions.TabRegistryTest'`
Expected: FAIL to compile: `cannot find symbol: method tabDrag()` (in `WindowManager`), `class TearOut`, `method fitted`,
`method dropFrame`, `variable otherWindows`, `method move(Tab, UUID, GroupId, Side)`.

- [ ] **Step 3: The registry moves a tab beside a group of another window**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java", [
    ("""    public void move(Tab tab, UUID window) {
        requireEdt();
        var next = hosts.get(Objects.requireNonNull(window, "window"));
        if (next == null) throw new IllegalArgumentException("No workspace " + window);
        if (tab.closed() || tab.pinned() || pending.contains(tab) || window.equals(tab.window())) return;
        var old = hosts.get(tab.window());
        if (old == null) return;
        transfer(tab, old, next);
    }
""", """    public void move(Tab tab, UUID window) { move(tab, window, next -> next.attach().accept(tab)); }

    /**
     * As {@link #move(Tab, UUID)}, into a new group on {@code side} of {@code group} in that window: a tab dropped on a
     * group's edge in another window (workspace tabs spec 12).
     *
     * @param tab the tab
     * @param window the window to move it to
     * @param group a group of that window
     * @param side where the new group goes
     * @throws IllegalArgumentException when no workspace {@code window} is registered
     */
    public void move(Tab tab, UUID window, GroupId group, Side side) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(side, "side");
        move(tab, window, next -> next.attachBeside().attach(tab, group, side));
    }

    /** Moves {@code tab} to {@code window}, where {@code attach} shows it. */
    private void move(Tab tab, UUID window, Consumer<WorkspaceHost> attach) {
        requireEdt();
        var next = hosts.get(Objects.requireNonNull(window, "window"));
        if (next == null) throw new IllegalArgumentException("No workspace " + window);
        if (tab.closed() || tab.pinned() || pending.contains(tab) || window.equals(tab.window())) return;
        var old = hosts.get(tab.window());
        if (old == null) return;
        transfer(tab, old, next, attach);
    }
""", 1),
    ("""     * Detaches the tab from {@code old} and attaches it to {@code next}, then selects it. A change listener that throws
     * as the tab takes {@code next} leaves it in {@code old}, and its failure propagates. When {@code next} fails, the
     * tab goes back to {@code old}, or another window, or stays in no window, and the failure propagates. When
     * {@code old} fails to let the tab go, the move still goes on, so the tab is shown in the window it names, and that
     * failure propagates afterwards. The window the tab left hears of the move after it, then the tab is selected
     * (workspace plan 4a); a failure of either is kept behind the move's own.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next) {""", """     * Detaches the tab from {@code old} and attaches it to {@code next} as {@code attach} does, then selects it. A
     * change listener that throws as the tab takes {@code next} leaves it in {@code old}, and its failure propagates.
     * When {@code next} fails, the tab goes back to {@code old}, or another window, or stays in no window, and the
     * failure propagates. When {@code old} fails to let the tab go, the move still goes on, so the tab is shown in the
     * window it names, and that failure propagates afterwards. The window the tab left hears of the move after it, then
     * the tab is selected (workspace plan 4a); a failure of either is kept behind the move's own.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next, Consumer<WorkspaceHost> attach) {""", 1),
    ("            try { next.attach().accept(tab); arrived = true; }\n", "            try { attach.accept(next); arrived = true; }\n", 1)])
PY
```

- [ ] **Step 4: The drag tears out and moves between windows; the manager shares it**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
span(W + "TabDrag.java", "/**\n * The tab drag (workspace tabs spec 12).", " * its group (a command moving it) end the drag without a drop. EDT only.\n */\n", """/**
 * The tab drag (workspace tabs spec 12). A left press on a tab in a strip of either face that moves past the kit's tab
 * drag threshold ({@code TAB_REORDER_THRESHOLD}, 5 px by default) starts it. It is the strips' only drag, so reordering a
 * strip, moving a tab to another group or window and tearing it out are one gesture. It uses plain mouse events in screen
 * coordinates, never AWT drag and drop: Swing keeps sending the pressed component its drag events wherever the cursor
 * goes, and synthetic events drive it in tests. While it runs, {@link TabDragLabel} shows the tab's label beside the
 * cursor and the target window's layered pane shows where the tab would land ({@link DockDropOverlay}, from
 * {@link WorkspaceDropResolver#landing}); nothing moves until the release.
 * <p>The target: of the windows under the cursor that can take a drop (on macOS none in full screen but the drag's own),
 * the most recently active; over its workspace, the resolver's drop unless it would change nothing; anywhere else in that
 * window (its dock, chrome or title bar), and over any other window of Jasper's (Settings, About, a dialog), nothing;
 * outside every window, a new child window whose workspace is the size of the tab's old group, with the tab where the
 * cursor holds it and kept on the display there, except for a child window's only tab, whose window already is its own.
 * A pinned tab (a modal owner holds it in its window) takes targets in its own window only. Releasing the left button
 * carries the target out: within a window through the workspace's layout operations, into another window through the tab
 * registry's moves, out of every window through the window manager's; none touches a tab's content or session.
 * Releasing on no target, Esc, another press or a plain move (both mean Swing lost the release), the drag's window losing
 * the focus (a modal dialog, another application), and the dragged tab closing or leaving its window or its group (a
 * command moving it) end the drag without a drop. EDT only.
 */
""")
edit(W + "TabDrag.java", [
    ("import com.formdev.flatlaf.util.UIScale;\n", "import com.formdev.flatlaf.util.SystemInfo;\nimport com.formdev.flatlaf.util.UIScale;\n", 1),
    ("import java.awt.Container;\n", "import java.awt.Container;\nimport java.awt.Dimension;\n", 1),
    ("import java.awt.Frame;\n", "import java.awt.Frame;\nimport java.awt.GraphicsConfiguration;\n", 1),
    ("import java.util.List;\n", "import java.util.ArrayList;\nimport java.util.HashSet;\nimport java.util.List;\n", 1),
    ("import java.util.function.Supplier;\n", "import java.util.function.BiConsumer;\nimport java.util.function.Supplier;\n", 1),
    ("""        record Into(WindowContent window, Drop drop) implements Target { }
    }
""", """        record Into(WindowContent window, Drop drop) implements Target { }

        /**
         * Out of every window, into a new child window whose workspace takes {@code bounds}.
         *
         * @param bounds the new window's workspace on screen; its frame adds its title bar
         */
        record TearOut(Rectangle bounds) implements Target { }
    }
""", 1),
    ("""    private final Supplier<List<WindowContent>> windows;
""", """    private final Supplier<List<WindowContent>> windows;
    private final BiConsumer<Tab, Rectangle> tearOut;
""", 1),
    ("""    private final TabDragLabel label = new TabDragLabel();
""", """    private final TabDragLabel label = new TabDragLabel();
    /**
     * Test seam: the frames on screen of Jasper's other showing windows (Settings, About, Licenses, a dialog, a popup),
     * where a release drops nothing; none of the windows the drag reaches, nor the label's. Production reads
     * {@link Window#getWindows()}, which a headless JVM leaves empty.
     */
    Supplier<List<Rectangle>> otherWindows = this::auxiliaryFrames;
""", 1),
    ("""    private Point pressedAt;
""", """    private Point pressedAt;
    /** Where the press held the tab, from the tab's top-left corner, and the tab's group's size: a tear-out's window. */
    private Point grab;
    private Dimension groupSize;
""", 1),
    ("""    /** @param windows the windows a drag can reach, the most recently active first */
    TabDrag(Supplier<List<WindowContent>> windows) {
        this.windows = Objects.requireNonNull(windows, "windows");
    }
""", """    /**
     * @param windows the windows a drag can reach, the most recently active first
     * @param tearOut opens a new child window for a tab, its frame at screen bounds, or leaves the tab where it is
     */
    TabDrag(Supplier<List<WindowContent>> windows, BiConsumer<Tab, Rectangle> tearOut) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.tearOut = Objects.requireNonNull(tearOut, "tearOut");
    }
""", 1),
    ("""        if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || !group.tabs().contains(tab)) return;
        this.group = group;
        this.tab = tab;
        source = event.getComponent();
        pressedAt = screen(event);
    }
""", """        int index = group.tabs().indexOf(tab);
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
""", 1),
    ("""        OptionalInt under = WorkspaceDropResolver.windowAt(at, open.stream().map(TabDrag::onScreen).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A pinned tab stays in its window, as the registry keeps it there: no other window takes it.
            if (!tab.pinned() || window == group.owner()) target = into(window, at);
        }
""", """        OptionalInt under = WorkspaceDropResolver.windowAt(at, open.stream().map(window -> onScreen(window, window == group.owner())).toList());
        if (under.isPresent()) {
            WindowContent window = open.get(under.getAsInt());
            // A pinned tab stays in its window, as the registry keeps it there: no other window takes it.
            if (!tab.pinned() || window == group.owner()) target = into(window, at);
        } else if (WorkspaceDropResolver.windowAt(at, otherWindows.get()).isEmpty() && !tab.pinned()
                && (group.owner().isMain() || group.owner().tabCount() > 1)) {
            // Outside every window of Jasper's: a new child window, except for a child window's only tab (spec 12).
            target = Optional.of(new Target.TearOut(tearOutBounds(at)));
        }
""", 1),
    ("""        pressedAt = null;
        target = Optional.empty();
""", """        pressedAt = null;
        grab = null;
        groupSize = null;
        target = Optional.empty();
""", 1),
    ("""    /** Carries a release out: the window's workspace places the tab; its content and session are never touched. */
    private void drop(Tab moving, Target where) {
        if (moving.closed() || !(where instanceof Target.Into into) || into.window().closed()) return;
        into.window().workspace().drop(moving, into.drop());
    }
""", """    /**
     * Carries a release out; nothing touches the tab's content or session. Within a window the workspace places the tab.
     * Into another window the tab registry moves it (both windows' maximizes end, TAB_MOVED reports it, a child window it
     * empties closes after the move): an edge drop straight into a new group beside its group, any other to the end of
     * the window's active group, where the drop then puts it in place. Out of every window, the window manager's tear-out.
     */
    private void drop(Tab moving, Target where) {
        if (moving.closed()) return;
        switch (where) {
            case Target.Into into -> {
                WindowContent window = into.window();
                if (window.closed()) return;
                WorkspaceView workspace = window.workspace();
                if (!workspace.contains(moving)) {
                    if (into.drop() instanceof Drop.Edge edge) {
                        window.tabRegistry().move(moving, window.id(), edge.group(), edge.side());
                        return;
                    }
                    window.tabRegistry().move(moving, window.id());
                    if (!workspace.contains(moving)) return;
                }
                workspace.drop(moving, into.drop());
            }
            case Target.TearOut out -> tearOut.accept(moving, out.bounds());
        }
    }

    /**
     * A tear-out's workspace on screen: the size of the tab's old group, with the tab where the cursor holds it, as
     * IntelliJ and VS Code size a window from the content dragged out of it. The new frame grows it by its own title
     * bar and keeps itself on the display there ({@code WindowFrame}).
     */
    private Rectangle tearOutBounds(Point at) {
        return new Rectangle(at.x - grab.x, at.y - grab.y, groupSize.width, groupSize.height);
    }

    /**
     * {@code bounds} kept on {@code usable}: no larger than it, then moved wholly onto it, as IntelliJ keeps a floating
     * window on its screen; as they are when {@code usable} is empty (no display known). A torn-out window's frame
     * keeps itself on its display through this.
     */
    static Rectangle fitted(Rectangle bounds, Rectangle usable) {
        if (usable.isEmpty()) return new Rectangle(bounds);
        int width = Math.min(bounds.width, usable.width), height = Math.min(bounds.height, usable.height);
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - height));
        return new Rectangle(x, y, width, height);
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
""", 1),
    ("""    /**
     * {@code window}'s frame on screen, title bar and all: its native window's bounds, or its outermost component's for
     * content in no native window; empty while it does not show or is minimized.
     */
    private static Rectangle onScreen(WindowContent window) {
        Component top = outermost(window);
        boolean shows = top instanceof Window frame
            ? frame.isShowing() && !(frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
            : top.isVisible();
        if (!shows) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }
""", """    /**
     * {@code window}'s frame on screen, title bar and all, where it can take a drop: its native window's bounds (empty while
     * it does not show, is minimized, or by {@link #dropFrame} is out of sight in full screen), or its outermost component's
     * for content in no native window.
     */
    private static Rectangle onScreen(WindowContent window, boolean source) {
        Component top = outermost(window);
        if (top instanceof Window frame) {
            if (!frame.isShowing() || frame instanceof Frame decorated && (decorated.getExtendedState() & Frame.ICONIFIED) != 0)
                return new Rectangle();
            GraphicsConfiguration display = frame.getGraphicsConfiguration();
            return dropFrame(frame.getBounds(), display == null ? null : display.getBounds(), source, SystemInfo.isMacOS);
        }
        if (!top.isVisible()) return new Rectangle();
        Point origin = new Point();
        SwingUtilities.convertPointToScreen(origin, top);
        return new Rectangle(origin, top.getSize());
    }

    /**
     * Where a window takes drops. On macOS a window other than the drag's own whose frame is exactly its display's bounds is
     * in full screen (which covers the menu bar too), on a Space of its own that the user cannot see while dragging from
     * another window: it takes none. Java sees no other Space, so a window on one that is not in full screen still takes
     * drops where its frame lies. Elsewhere a window covering its display is in sight (a tiling window manager's), so the
     * rule is macOS's alone.
     *
     * @param frame the window's frame on screen
     * @param display its display's bounds, or null when unknown
     * @param source whether it is the drag's own window
     * @param macOs whether this is macOS
     * @return the frame, or an empty rectangle where it takes no drop
     */
    static Rectangle dropFrame(Rectangle frame, Rectangle display, boolean source, boolean macOs) {
        if (macOs && !source && frame.equals(display)) return new Rectangle();
        return new Rectangle(frame);
    }
""", 1)])
edit(W + "TabDragLabel.java", [(
    """    /** @return whether the label shows */
""", """    /** @return whether {@code other} is the label's own window */
    boolean owns(Window other) { return window != null && window == other; }

    /** @return whether the label shows */
""", 1)])
edit(W + "WindowContent.java", [
    ("""    /** Drags a tab from any of this window's strips (workspace tabs spec 12). */
    private final TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this));
""", """    /**
     * Drags a tab from any of this window's strips (workspace tabs spec 12): the window manager's, which every window it
     * opens shares; until it gives that, one of this window's own, which drops within this window and tears nothing out.
     */
    private TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this), (tab, bounds) -> { });
""", 1),
    ("""    TabDrag tabDrag() { return tabDrag; }
""", """    TabDrag tabDrag() { return tabDrag; }
    /** Shares the window manager's tab drag; a drag of this window's own ends first. */
    void tabDrag(TabDrag shared) {
        tabDrag.cancel();
        tabDrag = Objects.requireNonNull(shared, "shared");
    }
""", 1)])
edit(W + "WindowManager.java", [
    ("""    private final List<JasperWindow> activations = new ArrayList<>();
""", """    private final List<JasperWindow> activations = new ArrayList<>();
    /** The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first. */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList(),
        (tab, bounds) -> moveToNewWindow(tab, bounds));
""", 1),
    ("""    /** @return the window that came to the front last while it is open; else the main window; else the first child window */
""", """    /** @return the tab drag every window of this manager shares */
    TabDrag tabDrag() { return tabDrag; }

    /** @return the window that came to the front last while it is open; else the main window; else the first child window */
""", 1),
    ("""            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
""", """            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
            // Every window drags tabs with the one drag, which reaches them all (workspace tabs spec 12).
            content.tabDrag(tabDrag);
""", 1),
    ("     * @param bounds where a child window opens on screen, a torn-out tab's (workspace tabs plan 5); empty for where the\n"
     "     *     platform puts it\n",
     "     * @param bounds where a child window's workspace opens on screen, a torn-out tab's (workspace tabs plan 5); empty\n"
     "     *     for where the platform puts it\n", 1),
    ("     * As {@link #moveToNewWindow(Tab)}, the new child window at {@code bounds} on screen: a tab torn out of its window\n",
     "     * As {@link #moveToNewWindow(Tab)}, the new child window's workspace at {@code bounds} on screen: a tab torn out of\n"
     "     * its window\n", 1),
    ("     * @param bounds where the new window goes on screen; null for where the platform puts it\n",
     "     * @param bounds where the new window's workspace goes on screen; null for where the platform puts it\n", 1)])
edit(W + "WindowShell.java", [(
    " * @param place puts the frame at screen bounds before it first shows: a torn-out tab's window (workspace tabs plan 5)\n",
    " * @param place puts the frame, before it first shows, so that its workspace takes screen bounds: a torn-out tab's\n"
    " *     window (workspace tabs plan 5)\n", 1)])
edit(W + "WindowFrame.java", [
    ("import java.awt.Insets;\n", "import java.awt.Insets;\nimport java.awt.Point;\n", 1),
    ("""        return new WindowShell(this::show, this::toFront, this::dispose, this::discard, () -> frame, this::owns, frame::setBounds);
    }
""", """        return new WindowShell(this::show, this::toFront, this::dispose, this::discard, () -> frame, this::owns, this::placeWorkspace);
    }

    /**
     * Puts the frame, before it first shows, so that its workspace takes {@code workspace} on screen: the workspace
     * grown by the chrome the packed frame has around it (its title bar: the system's, the unified macOS bar or
     * FlatLaf's title pane), then kept on the usable part of the display there. A torn-out tab's window (workspace tabs
     * plan 5).
     */
    private void placeWorkspace(Rectangle workspace) {
        // Laid out at the frame's present size: the constructor may have resized it after pack().
        frame.validate();
        JComponent area = content.workspace();
        Point corner = SwingUtilities.convertPoint(area, 0, 0, frame);
        int chromeWidth = frame.getWidth() - area.getWidth(), chromeHeight = frame.getHeight() - area.getHeight();
        Rectangle bounds = new Rectangle(workspace.x - corner.x, workspace.y - corner.y, workspace.width + chromeWidth,
            workspace.height + chromeHeight);
        java.awt.GraphicsConfiguration display = frame.getGraphicsConfiguration();
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            java.awt.GraphicsConfiguration candidate = device.getDefaultConfiguration();
            if (candidate.getBounds().contains(workspace.getLocation())) display = candidate;
        }
        frame.setBounds(TabDrag.fitted(bounds, usableBounds(display)));
    }
""", 1)])
PY
```

- [ ] **Step 5: Ledger row**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "TabDragWindowsTest.java", "app.workspace", "TabDragWindowsTest", T + "TabDragWindowsTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 5 Task 4; the tab drag across windows, tear-out and drag-back")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`TabDragWindowsTest` 15, `TabRegistryTest` one more; `TabDragTest`, `WindowManagerTest` and
`JasperApplicationWindowsTest` unchanged). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tab drag: across every window, drag-back and tear-out

The window manager gives every window its one tab drag, which reaches
them all, the most recently active first; on macOS a window in full
screen other than the drag's own takes no drop. A drop on another window
moves the tab through the tab registry, an edge drop straight beside its
group; a release over Settings or another window of Jasper's drops
nothing; a release outside every window tears the tab out into a child
window whose workspace is its old group's size, the tab under the
cursor, kept on the display. A child window's only tab and a pinned tab
stay; sessions and content are untouched.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation, STATUS and the whole build

**Files:**
- Modify: `README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/sdk-architecture.md`,
  `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

The user guide for tabs is `README.md`'s shortcut paragraph (`docs/keyboard-shortcuts.md` describes the Keymap window
and has no section on tabs). Each edit is the smallest that makes its sentence true (AGENTS.md "Documentation edits"):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan5"); from edits import edit
edit("README.md", [(
    "Tabs have hover close controls, support middle-click close and drag reorder.",
    "Tabs have hover close controls and support middle-click close. Drag a tab along its strip to reorder it, onto another "
    "group's tabs or middle to move it there, onto a group's outer edge to split that side, onto another Jasper window, or "
    "out of every window into a new one; Esc cancels.", 1)])
edit("docs/app-architecture.md", [
    ("terminal tabs and panes, the tool window area, stripes, headers and dock drag,",
     "terminal tabs and panes, the tab drag (`TabDrag`, the window manager's one, shared by its windows), the tool window area, stripes, headers and dock drag,", 1),
    ("| `dock` | Pure tool window dock model: the six slots, each window's `DockState`, `DockGeometry` and the drag's `DockDropResolver`. JDK-only (no Swing; `java.awt.Rectangle` and `Point` only, enforced by `verifyApplicationArchitecture`); each window owns its state on the EDT. |",
     "| `dock` | Pure tool window dock model: the six slots, each window's `DockState`, `DockGeometry` and the drag's `DockDropResolver`; and the tab drag's `WorkspaceDropResolver`. JDK-only (no Swing; `java.awt.Rectangle` and `Point` only, and the layout's group ids and sides, enforced by `verifyApplicationArchitecture`); each window owns its state on the EDT. |", 1)])
edit("docs/sdk-architecture.md", [(
    "| `dev.jasper.app.dock` | The tool window dock model: the six slots, each window's `DockState` and the pure `DockGeometry`; JDK-only | none |",
    "| `dev.jasper.app.dock` | The tool window dock model: the six slots, each window's `DockState` and the pure `DockGeometry`; the tab drag's pure `WorkspaceDropResolver`; JDK-only | `layout` |", 1)])
edit("docs/app-maintenance.md", [(
    "bounded tooltips. The fixed list/add controls and scrolling/underline motion are retired.\n",
    """bounded tooltips. The fixed list/add controls and scrolling/underline motion are retired.

Tab drags use plain mouse events too (`TabDrag`), never AWT drag and drop. A strip's left press hands the drag its tab
(`GroupView.pressed`); past the kit's `TAB_REORDER_THRESHOLD` it follows the cursor in screen coordinates over the
windows the `WindowManager` lists, most recently active first, with `TabDragLabel` beside the cursor and the landing on
the target window's `DockDropOverlay`. Where a tab would drop is `WorkspaceDropResolver`'s job, over
`WorkspaceView.dropAreas`: change the zones in the resolver and its tests first. A drop within a window is
`WorkspaceView.drop`; one into another window goes through the tab registry first, and a tear-out through the window
manager. A release over another window of Jasper's (`TabDrag.otherWindows`) drops nothing, and on macOS a window in full
screen other than the drag's own takes no drop (`TabDrag.dropFrame`). Esc, a plain move or a press anywhere (a lost
release), the source window losing the focus and the dragged tab closing or leaving its window or group end a drag.
`TabDragTest` and `TabDragWindowsTest` drive it headlessly over stand-in frames on a pretend screen
(`TabDragTestSupport`).
""", 1)])
PY
git diff --stat README.md docs
```

Expected: four files changed. `AGENTS.md`'s rule for `dev.jasper.app.dock` names what it may hold from `java.awt`, not its
Jasper dependencies, so it stays as it is.

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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 4b's
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 5: tear-out and drag-back — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-5-tear-out.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). One tab drag (`workspace.TabDrag`, the window
manager's, shared by every window it opens) replaces the strips' release-to-reorder: a left press on a tab of either
strip face that moves past the kit's `TAB_REORDER_THRESHOLD` (5 px) drags it on plain mouse events in screen
coordinates, with its label in a small undecorated window beside the cursor (`TabDragLabel`) and the landing on the
target window's `DockDropOverlay`. `dock.WorkspaceDropResolver` (pure; `dock` may now name the layout's group ids and
sides) maps the cursor to a drop: a place in a strip, a group's centre, a group's outer quarter on a side (the empty main
workspace is all centre), and picks the most recently active window under the cursor; on macOS a window in full screen
other than the drag's own takes no drop. Within a window `WorkspaceView.drop` reorders, moves or splits; into another
window the tab registry moves the tab (TAB_MOVED, both maximizes end, an emptied child window closes after it) and the
drop places it, an edge drop beside the target group directly (`TabRegistry.move(Tab, UUID, GroupId, Side)`); outside
every window the tab tears out into a new child window whose workspace is its old group's size with the tab under the
cursor, its frame grown by its own title bar (`WindowFrame` measures it) and kept on the display (none for a child
window's only tab or a pinned tab, which moves within its window only). A release over a window's dock, chrome or title
bar or over another window of Jasper's (Settings, About), a drop that would change nothing, Esc, a lost release, the
source window losing
the focus (a modal dialog, another application) and the dragged tab closing or leaving its window or group do nothing.
Sessions and content are never touched. `GroupView.reorder` is gone. Rulings (plan, "Rulings"): <one clause each, 1 to
18>.
Deviations: <none, or each with its reason>. Left for plan 6: saving a child window's bounds, which a tear-out now
chooses, and the layout after every drag, through the debounced save; from plan 4b, `AppContractTest`'s `emptyWindow`
(it makes the main window when none is open) and panel routing (it bypasses `panelRoute`) before the first contract
case that needs either, and `FakeWorkspace.closeWindow` closing and nulling `Panel.instance`, with `AppContractTest`
clearing its panel caches, once a fake main window can close. Deferred: plan 2's Rulings 8 limit, now reachable by a
drag: a tab dropped on a group's left or upper edge and dragged back gives the new group's space to the sibling before
it, so the group it came from halves on each round trip. `./gradlew check --continue`: **<counts>**. GUI
checks for the user: a tab dragged along its strip, onto another group's tabs and middle, and onto each outer quarter of
a group, in both tab faces (equal tabs and the Enterprise look's classic tabs), with the landing shown and no motion; a
tab dragged into another Jasper window's strip, middle and edge, and a child window's last tab dragged into the main
window (the child closes, the shell keeps running); a tab released on the desktop opens a child window under the cursor
whose workspace is the size of its old group, the tab under the cursor and the title bar above it (the unified macOS
bar, FlatLaf's title pane on Windows and Linux, and the system's), also on a second display, near a display's edge
(moved onto it) and across displays with different scales; a child window's only tab
released on the desktop stays; releases over the dock, the toolbar, a title bar and the Settings window change nothing;
Esc, Cmd+Tab (Alt+Tab) to another application and a modal dialog (a plugin's) during a drag end it with no label left on
screen; overlapping windows take the drop in the most recently active one; on macOS, a Jasper window in full screen
takes no drop from a window on the desktop, and a window on another Space (not in full screen), which Java cannot tell
apart, is noted as it behaves; on Windows and Linux, the label window stays above other windows and takes no focus,
including under a focus-follows-mouse window manager on Linux (the label must not take focus as the pointer crosses
it). Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for tear-out and drag-back

The README describes dragging tabs within and between windows and out
of them; the architecture and maintenance notes name the tab drag and
its resolver; STATUS records plan 5 with its rulings, the hand-off to
plan 6 and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```


## Self-review

- Spec coverage, section 12 line by line: one application-wide `TabDrag` started by a press past the kit's threshold
  (Task 3, Rulings 3; shared in Task 4, Rulings 10), on plain mouse events in screen coordinates (Task 3), with a small
  undecorated label window under the cursor and no motion (Task 3, Rulings 6); the pure `WorkspaceDropResolver` beside
  `DockDropResolver` with its strip, centre and outer-quarter zones (Task 1, Rulings 1-2) over any Jasper workspace
  (Tasks 2, 4) and an overlay in the target window (Tasks 3, 4, Rulings 4); releasing outside every window tears out at
  the cursor, its workspace sized like the old group (Task 4, Rulings 12); releasing over a Jasper window outside its
  workspace (a main or child window's chrome, or Settings, About and the other auxiliary windows), Esc, a lost release
  and focus moving to another application cancel (Tasks 3, 4, Rulings 7, 9); overlapping windows go to the most recently
  active, and on macOS a window in full screen takes none (Tasks 1, 4, Rulings 18); a child window's only tab dragged
  outside does nothing (Task 4, Rulings 13). A tab that closes, moves or is pinned under the drag never lands where the
  registry would refuse it (Tasks 3, 4, Rulings 7, 17). Section 15:
  a modal dialog during a drag cancels it (Task 3, through the window losing the focus); a maximized group gaining or
  losing a tab by a move ends its maximize (Tasks 2-4). Section 9.3 and 4: every move is a layout operation, the
  registry's move or the window manager's (Rulings 8, 11, 12), sessions untouched (Task 4's tests), no motion. Section
  10: the strip is the drag source (Task 3). Section 17: every drop zone and overlapping windows in the resolver's tests
  (Task 1); a moved terminal tab keeps its session (Task 4); the GUI checks go to the user (Task 5's STATUS entry).
  Section 18's docs: `docs/app-architecture.md` (Task 5); `docs/keyboard-shortcuts.md` has no tab section (Rulings 16).
- Every quoted anchor was matched, at its stated count, in a replay of Tasks 1-5 in order on HEAD `094729d08`; nothing
  was compiled.
- Type consistency: `WorkspaceDropResolver.Area`, `Drop.Strip`/`Centre`/`Edge`, `resolve`, `landing`, `windowAt`;
  `WorkspaceView.dropAreas(Tab)`, `changes(Tab, Drop)`, `drop(Tab, Drop)`; `TabStrip.tabBounds()`,
  `GroupView.tabBounds(Component)`, `pressed`, `dragged`, `released`; `TabDrag.Target.Into`/`TearOut`, `TabDrag(Supplier,
  BiConsumer)` (Task 3 has the one-argument form, which Task 4 replaces), `cancelFrom`, `fitted`, `dropFrame`,
  `otherWindows`; `TabDragLabel.owns`; `TabDragTestSupport.TITLE`, `onTitleBar`; `WindowContent.tabDrag()` and
  `tabDrag(TabDrag)`; `WindowManager.tabDrag()`; `TabRegistry.move(Tab, UUID, GroupId, Side)` keep one name and one
  signature from the task that creates them to the last task that uses them.
- Tests that open tabs in a laid-out strip turn its motion off first (`TabDragTest`, `TabDragWindowsTest`); the others
  open their tabs before the first layout, when the strip takes them at once (fact 12).
