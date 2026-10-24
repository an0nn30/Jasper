# Floating tool windows 2: drag a tool window out Implementation Plan

**Status: executed on 2026-10-23** (`67a7a0c5a..12f936f60`, then the final review's documentation commit); see
`docs/STATUS.md`.
Deviations from the plan's text: (1) a detach drop activates the new window, as View Mode from a menu does (Task 2); (2)
Task 2 reuses `DetachedToolWindows`' size rule (a private `size`, shared by `initialBounds` and the new `droppedBounds`)
instead of copying it into `WindowContributions`; (3) Detach also holds over another workspace window in front of the
main window (`TabDrag.behind`, a new method beside `covers`), which Task 1's `covers` alone could not see (`f74ac4b49`);
(4) `DockDrag.frameOnScreen` replaces `SwingUtilities.getRoot`, which returns null for the stand-in frame in JDK 25, and
the label's title comes from a new `DockDrag.titles` hook; (5) the display is picked by whole bounds, then the window is
fitted to its usable part, through `WindowPlacement.Display` and `WindowFrame.displays()` (`7e5abb606`), where Task 2
named `WindowPlacement.displayFor` over the usable bounds; (6) the final review's fixes (`12f936f60`): a lost release
ends the dock drag as it ends a tab drag, the grab is the header's wherever in it the press landed, a release whose
handling fails still ends the drag, and the new window's title bar is not counted, so the guide says it opens near the
pointer.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Releasing a dragged stripe button (rail icon, labelled stripe tab, bottom strip tab) or docked header outside the main window opens that tool window in its own Float window at the release point, as IntelliJ does.

**Architecture:** `DockDrag` (root coordinates today) also tracks the pointer on screen; outside the main window's frame, or over another of Jasper's windows covering it (`TabDrag`'s covering rule), its target is Detach: the in-window guides clear and a `TabDragLabel` follows the pointer. A release there calls `onDetach`; `WindowContributions` computes the window's bounds and calls `DockState.detach` (plan 1). Inside the window nothing changes.

**Tech Stack:** Java 25 (JBR), Swing, JUnit 5, AssertJ; headless tests on `TabDragTestSupport`'s pretend screen.

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-floating-tool-windows-design.md` (section "The drag out")

**Depends on:** plan 1 (`2026-10-23-floating-1-detached-tool-windows.md`) executed on the same branch.

## Global Constraints

- Plain mouse events, no AWT drag and drop; the drag image is the existing `TabDragLabel` (no thumbnail).
- Esc, another press, the main window deactivating and `cancel()` end the drag without a drop, also while outside.
- A release inside the main window on no target drops nothing (unchanged).
- Never launch the GUI; headless tests only.
- New classes or test classes get `docs/design/theme-engine/migration.csv` rows.
- Commit messages end with `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Review Focus

1. Dragging back inside after leaving: the label goes and the guides return, at once — Task 1.
2. A drop near a display's edge: the window is fitted inside the display under the pointer, never across two — Task 2.
3. A detached tool window's own stripe button dragged out again: its window moves; Window mode stays Window — Task 2.
4. The main window deactivating mid-drag while the pointer is outside (another application raised): the label goes and
   nothing drops — Task 1.
5. A drop outside while the dragged tool window was hidden: it shows in its new window — Task 2.

---

### Task 1: Detach while outside the main window (`DockDrag`)

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java`, `workspace/TabDrag.java` (a covering query),
  `workspace/WindowContent.java` (wiring)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragDetachTest.java` (new: a real `WindowContent` from
  `DesktopTestSupport.content(...)` connected to contributions with two panels, in `TabDragTestSupport.frame(window,
  100, 100, 1000, 700)`; events via `TabDragTestSupport.mouse` in screen coordinates)

**Interfaces:**
- Produces:
  - `DockDrag.record Detach(Point screen, Point grab)` — the release point on screen and the press point within the
    pressed component.
  - `DockDrag.onDetach: BiConsumer<String, Detach>` (default no-op); `DockDrag.covered: Predicate<Point>` (default
    false), wired by `WindowContent` to `screen -> tabDrag().covers(this, screen)`.
  - `boolean TabDrag.covers(WindowContent window, Point screen)` — the existing private `covered(window, at)`.
  - `boolean DockDrag.detaching()` — whether the target is Detach now (tests).

- [ ] **Step 1: Write the failing tests:**
  - `releasingARailButtonOutsideTheWindowDetachesIt`: press the left rail's button for `a`, drag past the threshold, on to
    (1300, 400) (outside the frame), release: `onDetach` heard once with `a`, screen (1300, 400) and the grab within the
    button; no `onDrop`.
  - `releasingAHeaderOutsideDetachesIt` (shown docked `a`, drag its header title).
  - `whileOutsideTheLabelShowsAndTheGuidesClear`: at (1300, 400) `detaching()` true; the label (`label()` accessor or
    `TabDragLabel.showing()`) shows `a`'s title at the pointer + `TabDrag.LABEL_OFFSET` scaled; the overlay has no
    highlight, placeholder or ghost; back inside over the workspace the label hides and the landing guide shows.
  - `overAnotherOfJaspersWindowsCoveringTheMainWindowItDetaches`: `covered` true at an inside point → Detach.
  - `escapeOrDeactivationWhileOutsideDropsNothing`: Esc → no `onDetach`, label hidden; same for `content.setActive(false)`.
  - `insideOnNoTargetStillDropsNothing` (the title bar inset of the stand-in frame is inside the frame).
  - `aStripeDropOfAShownDetachedWindowPreviewsNoFrame`: `a` FLOAT and shown; over a stripe group the overlay highlight
    is null (it stays detached), the gap shows.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*DockDragDetachTest'` — failures.
- [ ] **Step 3: Implement.** In `update`, compute the pointer on screen (`SwingUtilities.convertPointToScreen` from
  `root`) and the main window's frame on screen (`SwingUtilities.getRoot(root)`: its origin converted to screen, its size);
  outside it, or `covered`, the target is empty, `detaching` true, `showGap(null, 0)` on the stripes,
  `overlay.show(null, null, null, null)` and the label shown or moved; else hide the label and resolve as today.
  `pressed` records the grab (the press point in the pressed component). `released` on Detach: `finish()` (which hides
  the label) then `onDetach.accept(id, new Detach(screen, grab))`. `staysHidden` for a stripe target also holds for a
  detached tool window. Update the class Javadoc (the drag may now leave the window).
- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'` — pass (`DockDragTest`'s
  existing cases unchanged).
- [ ] **Step 5: Commit** — `Tool windows: a dock drag released outside the window detaches the tool window`.

---

### Task 2: A detach drop opens the Float window under the pointer (`WindowContributions`)

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java`
- Test: `DockDragDetachTest` (end-to-end cases with plan 1's recording shells)

**Interfaces:**
- Consumes: `DockDrag.onDetach`, `Detach` (Task 1); `DockState.detach`, `DetachedToolWindows` and `initialBounds`'
  size rule (plan 1 Task 5).

- [ ] **Step 1: Write the failing tests:**
  - `aDetachDropOpensAFloatWindowWithTheGrabUnderThePointer`: hidden `a` dropped at (1300, 400) with grab (6, 9):
    `open a FLOAT` at x 1294, y 391, size `landingSize("a")` (at least 240×160 scaled); `a` visible; its stripe button
    stays in its slot, selected.
  - `aShownDockedWindowKeepsItsFrameSize`: dropped header of shown `a` → the window has the docked frame's size.
  - `aRememberedSizeWins`: `a` floated before at 500×400, docked, dragged out → 500×400.
  - `theWindowIsFittedToTheDisplayUnderThePointer`: displays `[0,0,1920,1080]`, `[1920,0,1920,1080]`; drop at
    (3830, 1070) → the window lies inside the second display.
  - `draggingADetachedButtonOutAgainMovesItsWindow`: `a` WINDOW and shown; its stripe button dropped outside →
    `place a …` (or reopen at the new bounds), mode still WINDOW.
  - `aDetachedButtonDroppedOnAStripeStaysDetached` and `…OnTheWorkspaceDocks` (plan 1 Task 1's model through the drag).
- [ ] **Step 2: Run** — failures.
- [ ] **Step 3: Implement** `detachDropped(String id, DockDrag.Detach drop)`: size from `dock.bounds(id)`, else
  `area.frameOnScreen(id)`, else `area.landingSize(id)`, at least the plan 1 minimum; location `drop.screen() -
  drop.grab()` (the grab clamped into the size); `WindowPlacement.fitted` to the display `WindowPlacement.displayFor`
  picks for `drop.screen()` from `detached.displays`; mode `dock.mode(id)` if detached else FLOAT; `dock.detach(id,
  mode, bounds)`. Wire `owner.dockDrag().onDetach = this::detachDropped` in the constructor and reset it in `close`.
- [ ] **Step 4: Run** the workspace tests — pass.
- [ ] **Step 5: Commit** — `Tool windows: a detach drop opens the Float window where it was dropped`.

---

### Task 3: Documentation and records

- Modify: `docs/workspace.md` (dragging a stripe button or header out of the window floats it; back onto a stripe or the
  workspace docks it), `docs/app-architecture.md` (the drag's Detach target), `docs/STATUS.md` (top entry with the
  check's counts and GUI checks: drag out from a rail icon, a labelled tab, the bottom strip and a header, on each
  platform; two displays), `migration.csv` rows for `DockDragDetachTest`.
- [ ] **Step 1:** Edit. **Step 2: Run** `./gradlew check --continue`; record the counts. **Step 3: Commit** —
  `Docs: dragging a tool window out of the window`.
