# Full windows 5: panes tear out into full windows Implementation Plan

> **Status (2026-10-27):** Executed, Tasks 1-2, on `claude/full-windows` (not merged or pushed). Deviation from the plan
> text: the new window keeps the cursor at the same spot within the moved pane, sized to the whole tab's workspace.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A pane dragged outside every Jasper window becomes a full window holding a tab with that pane, and every
window's empty workspace takes a dropped tab or pane, as the first window's always did.

**Architecture:** `TabDrag`'s pane branch gains the tab branch's tear-out target outside every window; its drop goes to a
new `WindowManager.movePaneToNewWindow`, which opens an empty full window where the pane was dropped (the default
layout's tool windows, as a torn-out tab's window gets them) and moves the item into it through the tab registry, as
`movePaneToNewTab` moves an item into an empty workspace. The source tab and window close by the existing rules.

**Tech Stack:** Java 25 (JBR 25), Swing, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md`](../specs/2026-10-26-jasper-full-windows-design.md)
section 5.4 (decision 4), which reverses the tabs and panes spec's "Panes never become windows". Plans 1 to 4 are done
on this branch.

## Global Constraints

- Worktree `.claude/worktrees/full-windows`, branch `claude/full-windows`; never build in `~/projects/moray`; never
  launch the GUI or a benchmark; `./gradlew` only. One commit per task ending with
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- A pane released outside every Jasper window becomes a new full window holding a tab with that pane, at the cursor,
  sized as a torn-out tab's window is (`tearOutBounds`).
- A tab's only pane in a window's only tab, dragged outside every window, does nothing: that window moves by its title
  bar. A pinned item, or one being closed, never tears out (`TabRegistry.movable`).
- The new window takes only the default layout's tool windows (plan 4's ruling for torn-out windows, through
  `forNewWindow()` while another window is open); its tab holds the pane alone.
- The pane's tab in the source window closes when the pane was its last; the source window closes when that left it
  empty (a move-emptied window writes nothing to `workspace.toml` at once).
- Esc, a lost release and focus moving to another application end the drag, as today.

## Review Focus

1. **A pane torn out of a two-pane tab** leaves the other pane in place, its session running, and the new window holds
   the moved pane's live session (no restart). → Task 1 `aPaneReleasedOutsideEveryWindowBecomesAFullWindow`.
2. **The only pane of a window's only tab** dragged outside every window drops nothing and opens no window. → Task 1
   `theOnlyPaneOfTheOnlyTabTearsNothingOut`.
3. **A pane whose tab is the last pane of a window with other tabs** tears out; its tab goes, the window stays with its
   other tabs. → Task 1 `aTabsOnlyPaneTearsOutAndItsTabGoes`.
4. **A pinned pane** (a modal prompt open in it) never tears out. → Task 1 `aPinnedPaneNeverTearsOut`.
5. **A second window's empty workspace** takes a dropped tab and a dropped pane anywhere over it. → Task 1
   `anyWindowsEmptyWorkspaceTakesATabAndAPane`.

---

### Task 1: A pane tears out into a full window

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabDrag.java` (the pane branch `:607-611`; `drop` `:993-1013`; the "main window's empty workspace" wording in its Javadoc `:67`, `:85`, `:127`, `:736`, `:769`, `:799`, `:902`, `:986`, `:1007`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java` (`movePaneToNewWindow`, beside `movePaneToNewTab` `:755`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`mayMovePaneToNewWindow(Tab)`, beside `mayMoveTabToNewWindow`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java` (replace `outsideEveryWindowAPaneDropsNothing`; rename `theEmptyMainWorkspaceTakesATab…` and `theMainWindowsEmptyWorkspaceTakesAPaneAnywhereOverIt` to role-free names), `WindowManagerTest.java`

**Interfaces:**
- Produces: `Optional<JasperWindow> WindowManager.movePaneToNewWindow(WindowContent from, Tab tab, Rectangle bounds, Point cursor)`
  (EDT): empty when the item may not move (`mayMovePane`'s rules) or is the only pane of `from`'s only tab; otherwise
  opens an empty window as a tab tear-out does (`openEmpty` with the bounds and cursor), moves `tab` into it through the
  registry (`model.tabs().move(tab, newWindowId)`), and closes the new window again when nothing arrived (`settle`).
  `boolean WindowContent.mayMovePaneToNewWindow(Tab tab)`: false for the only pane of the window's only tab, else
  whether the registry moves it.
- `TabDrag`: a pane drag outside every window targets `Target.TearOut(tearOutBounds(at), at)` when
  `from.mayMovePaneToNewWindow(item)`; its drop calls `movePaneToNewWindow(home, moving, bounds, cursor)`.

- [ ] **Step 1: Write the failing tests** (in `TabDragWindowsTest`'s style, driving the drag with synthetic events)

```java
@Test void aPaneReleasedOutsideEveryWindowBecomesAFullWindow() throws Exception {
    // Window A, one tab split into panes P and Q (terminals). Pane-drag P (the modifier) and release outside every
    // window: a second window opens at the cursor's display with one tab holding P alone; A's tab holds Q; P's session
    // object is the same one (no new session started).
}
@Test void theOnlyPaneOfTheOnlyTabTearsNothingOut() throws Exception {
    // Window A with one tab and one pane: release outside every window → no target, no window opened, A unchanged.
}
@Test void aTabsOnlyPaneTearsOutAndItsTabGoes() throws Exception {
    // Window A with tabs T1 (one pane P) and T2: pane-drag P outside → a new window holds P; A keeps T2 only and stays open.
}
@Test void aPinnedPaneNeverTearsOut() throws Exception { /* a pinned item (Tab.pinned) → no TearOut target */ }
@Test void anyWindowsEmptyWorkspaceTakesATabAndAPane() throws Exception {
    // Windows A and B; empty B's workspace by closing its only tab; drop a tab of A anywhere over B: it lands in B; then
    // a pane of A: it lands in B as a new tab.
}

// WindowManagerTest
@Test void aPaneMovedToANewWindowTakesTheDefaultLayoutsToolWindows() {
    // A saved default layout with the rail hidden: movePaneToNewWindow → the new window's toolWindows().railVisible()
    // false, Zen off, and one tab holding the pane.
}
```

- [ ] **Step 2: Run them to verify they fail** —
  `./gradlew :jasper-app:test --tests '*TabDragWindowsTest' --tests '*WindowManagerTest'`, expected FAIL (no tear-out
  for panes; `movePaneToNewWindow` undefined).
- [ ] **Step 3: Implement** as the Interfaces block says; reuse the tab tear-out's bounds, landing and window opening.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test`, expected PASS.
- [ ] **Step 5: Commit** — `Tabs and panes: a pane dragged outside every window becomes a full window`

---

### Task 2: Documentation and the whole check

**Files:**
- Modify: `docs/workspace.md` (the pane-drag section: outside every window a full window, the only-pane exception; the
  tab and pane drop targets say "any window's empty workspace"), `docs/STATUS.md` (a 2026-10-27 plan 5 entry closing
  the full-windows series: plans 1-5 on `claude/full-windows`, not merged or pushed, the pending GUI checks), this
  plan's status banner
- [ ] **Step 1: Find stale mentions** — `grep -rniE 'panes? (never|cannot|can.t) become|main window.s empty workspace' docs/*.md jasper-*/README.md`;
  each hit is updated or a dated record.
- [ ] **Step 2: Update the guides** with the smallest accurate edits (AGENTS.md "Documentation edits").
- [ ] **Step 3: Run** `./gradlew check`, expected BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `Docs: panes tear out into windows (full windows plan 5)`
- [ ] **Step 5: Hand the GUI checks to the user** (not run by an agent): Cmd+Option-drag (Ctrl+Alt elsewhere) a pane
  out of a split onto the desktop on macOS, Windows and Linux; drag it back into another window's pane, strip and empty
  workspace; try the only pane of the only tab.
