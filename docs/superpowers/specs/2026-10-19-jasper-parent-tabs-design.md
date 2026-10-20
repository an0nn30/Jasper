# Jasper parent tabs, panes and the window menu

Status: **Approved on 2026-10-19** (the user: "Spec approved. Write the parent-tabs plans, then re-plan 6a and 6b on top of them."). This design amends
[`2026-10-16-jasper-workspace-tabs-design.md`](2026-10-16-jasper-workspace-tabs-design.md) (the workspace tabs spec,
"spec" below) in sections 1, 6, 7, 8, 10, 11, 12, 13, 14 and 15. Where the two disagree, this document wins. It lands
after plan 5 and before plans 6a and 6b, which are paused and will be re-planned on top of it.
Amended 2026-10-20 (user decision): Enterprise shows no parent-tab bar (sections 2 and 7).

## 1. Intent

Coding and terminal work do not share one workflow. In a coding project you keep one set of files open, organized as
tabs in one place. In terminal work you split, and you want tabs that each hold more splits. Several projects should
fit in one window, each with its own split layout. For example:

- project A: a few terminals split side by side;
- project B: the editor in one pane and terminals in others.

You should be able to pop B's editor pane out into its own window, work in it, and bring it back as a split pane
rather than as a tab.

Plans 1-5 built one level of splitting per window: a grid of groups, each holding tabs. This design adds a level above
it. Terminal apps work the same way: iTerm2, WezTerm, Windows Terminal and Zellij all use window → tabs → panes, and
tmux has `break-pane` and `join-pane` for popping a pane out and back. VS Code's terminal panel works the same way too:
tabs that each hold split terminals.

## 2. Decisions (the user, 2026-10-19)

1. **Three levels:** window → **parent tabs** → **panes** (the split grid of plans 3-5) → **items** stacked in a pane.
   Parent tabs are unnamed sets of panes, with no name and no root folder. "Project" is only how you think about them.
2. **One kind of split.** Cmd+D and Cmd+Shift+D split the active pane inside the active parent tab. There is no
   second, inner split: a pane that stacks several items shows them as tabs.
3. **In the standard look, a pane holding one item shows no strip.** Pop Out and Bring Back stay reachable from a
   context menu and from the window menu (section 6).
4. **A popped-out pane becomes a plain pane window.** It has no parent tabs and remembers where it came from. It can be
   split further, and Bring Back returns all of it, nested, to where it was.
5. **A popped-out parent tab becomes a full window** with a parent-tab bar, where more parent tabs can be created.
6. **The editor:** each file is its own item in a pane (option A, as plan 4b built it). If this does not feel right,
   the fallback is the editor keeping its own file tabs inside one pane (option B), which changes only the Editor
   plugin.
7. **Every window has a small toolbar** with a vertical ⋮ view-options menu, as IntelliJ's tool windows do (section 6).
   It holds Bring Back and the other window options, and leaves room for future options.
8. **Defaults the user accepted:**
   - Cmd+T opens a new parent tab.
   - Enterprise never shows the parent-tab bar: each pane's strip, which always shows, is the window's only row of tabs,
     and parent tabs are reached through the Tab menu and Cmd+1 to 9 (the user's decision of 2026-10-20).
   - New Terminal Window and New Editor Window open full windows.
   - Bring Back falls back to the main window when the origin is gone.
   - Saving and restoring covers every window kind.

## 3. Terms

| Word | Means | In code today |
|---|---|---|
| Parent tab | One entry in a window's parent-tab bar: a split layout of panes | new |
| Pane | One leaf of a parent tab's split grid; holds one or more items | `Group` / `GroupView` |
| Item (a tab in a pane) | A terminal, a file or a plugin view | `Tab` (the SDK's `TabHandle`) |
| Main window | The window with the chrome (spec 6.2) | `MainWindow` |
| Full window | A child window with a parent-tab bar | `ChildWindow` |
| Pane window | A child window holding one popped-out pane tree, with no parent-tab bar | new |

The plans keep `Group` and `Tab` as code names and add the new ones. Commands and labels the user sees say "pane" for a
group (section 8).

## 4. The model (amends spec 7 and 8)

- **A window's content is one of two shapes.**
  - The main window and full windows hold an ordered list of **parent tabs** and an active one.
  - A pane window holds one `Layout` and an **origin**.
- **A parent tab** is `(ParentTabId, Layout)`, where `Layout` is spec 7's value unchanged. Its splits, panes (groups),
  items (tabs), maximize and operations all keep their spec 7 rules, scoped to the parent tab.
- **The origin** records where a pane window's panes came from: the window and parent tab, the pane the subtree sat
  beside, the side, and its share. Bring Back uses it (section 5).
- **Invariants:**
  - Ids are unique across all windows.
  - A parent tab's layout is empty only in the main window when it has one parent tab: the empty workspace, spec 6.4.
  - Closing a parent tab's last item closes the parent tab. In the main window's sole parent tab, it leaves the empty
    workspace instead.
  - Closing a full window's last parent tab closes the window.
  - A pane window closes when its last item closes.
- **Focus:** each window has an active parent tab, and each parent tab its active pane, as spec 7 has an active group.
  A parent tab's label in its bar is its active item's title, as terminal apps title a tab by its active pane.
- **Encoding** (spec 8, `workspace.toml`) records parent tabs, pane windows and their origins. The format changes
  without migration (MVP clean break). Plan 2's codec has not shipped; plan 6a writes the first saved file.

## 5. Popping out and bringing back (amends spec 6.5 and 12)

| Action | Does |
|---|---|
| **Pop Out Tab** (one item) | The item leaves its pane for a new pane window at the pane's screen place and size. The origin is its pane and side. |
| **Pop Out Pane** | The whole pane, with every item it stacks, goes to a new pane window. The origin is the pane's neighbour, side and share. |
| **Pop Out Parent Tab** | The parent tab goes to a new full window. A full window's only parent tab cannot pop out. |
| **Bring Back** (pane window) | The window's whole layout returns as a nested split beside the origin pane, on the recorded side with the recorded share. The pane window closes. If the origin parent tab or window is gone, it goes beside the active pane of the main window's active parent tab. |
| **Move to Main Window** (full window) | The window's parent tabs join the main window's bar after its active parent tab, and the window closes. |

- **Dragging** follows plan 5, one level down: an item dropped on a strip, a pane's centre or a pane's edge, in any
  window, lands there.
  - Releasing an item outside every window pops it out as a pane window, with its origin set to where it was.
  - A parent tab drags within its bar to reorder. Dropped on another window's parent-tab bar, it moves there. Released
    outside every window, it becomes a full window.
  - Dropping a parent tab onto a pane, which would merge layouts, is out of scope (section 13).
- **No animation:** windows and panes appear in place (AGENTS.md).
- **Sessions survive every move**, as spec 2.3 and plans 3-5 guarantee.

## 6. The window toolbar and its ⋮ menu (amends spec 6.2 and 6.3)

- **Every window has a small toolbar ending in a vertical ⋮ button** that opens the window's view-options menu.
  IntelliJ puts its tool windows' options (View Mode, Move To) in the same kind of menu.
  - **Main window:** the ⋮ sits at the trailing end of its existing toolbar.
  - **Full and pane windows:** spec 6.3 gave them no toolbar. They gain a compact toolbar at the trailing end of their
    title row that holds only the ⋮ for now.
  - The plans fix the exact placement for each look and platform. It is a GUI check.
- **The ⋮ menu by window kind:**
  - **Main window:** Pop Out Pane and Pop Out Parent Tab, for the active pane or parent tab.
  - **Full window:** Pop Out Pane, Pop Out Parent Tab (when it has more than one) and Move to Main Window.
  - **Pane window:** Bring Back, then Pop Out Pane when it holds more than one pane.
- **The same commands are in other places too:**
  - the strip's tab menu, when the strip shows;
  - a terminal's context menu, so they work while a pane's strip is hidden;
  - the parent-tab bar's tab menu;
  - the command palette.
- **Room for future options:** the menu is the place for window-level view options. This design adds only the entries
  above.

## 7. Views (amends spec 10)

- **The parent-tab bar** sits above the workspace: below the header in the main window, at the top of a full window.
  Pane windows have none.
  - **Standard look:** it hides while the window has one parent tab.
  - **Enterprise:** it never shows; each pane's strip is the window's only row of tabs, and parent tabs are reached
    through the Tab menu and Cmd+1 to 9 (the user's decision of 2026-10-20). Cmd+T and New Session still open a
    parent tab.
  - It reuses the look's `TabStrip` (`WindowTabs` or `ClassicTabs`).
- **Pane strips** (`GroupView`):
  - **Standard look:** a pane holding one item shows no strip. This replaces the window-level rule landing now ("a
    window holding a single tab shows no strip").
  - **Enterprise:** pane strips always show.
- **Switching parent tabs** swaps the window's workspace view to that parent tab's layout. Hidden parent tabs keep
  their components and sessions; nothing restarts.
- **The empty main workspace** (spec 10) shows when the main window's sole parent tab is empty.

## 8. Commands (amends spec 11)

| Command | macOS / Windows and Linux | Does |
|---|---|---|
| `new_tab` | ⌘T / Ctrl+Shift+T | A new parent tab with one terminal pane, in the current terminal's directory |
| `new_tab_in_pane` | none | A terminal item stacked in the active pane |
| `split_right`, `split_down` | as today | A terminal in a new pane of the active parent tab (unchanged) |
| `close_tab` | ⌘W / Ctrl+Shift+W | Closes the active item; an emptied parent tab closes (section 4) |
| `close_pane` (was `close_group`) | ⌥⌘W / Ctrl+Alt+Shift+W | Closes every item in the active pane |
| `close_parent_tab` | none | Closes the active parent tab, with its items' guards |
| `maximize_pane` (was `maximize_group`) | as today | Unchanged behaviour |
| `focus_pane_left` … (were `focus_group_*`) | as today | Unchanged behaviour |
| `next_parent_tab`, `previous_parent_tab`, `select_parent_tab_1`–`9` | ⌘⇧] / ⌘⇧[ and ⌘1–9 (macOS); Ctrl+PageDown / Ctrl+PageUp and Alt+1–9 (Windows and Linux) | Switch parent tabs, as iTerm2 and Windows Terminal do |
| `next_tab`, `previous_tab` | ⌃⇥ / ⌃⇧⇥ (Ctrl+Tab / Ctrl+Shift+Tab) | Switch items within the active pane |
| `pop_out_tab` (was `move_tab_to_new_window`), `pop_out_pane`, `pop_out_parent_tab`, `bring_back` (was `move_tab_to_main_window`), `move_to_main_window` | none | Section 5 |
| `new_terminal_window`, New Editor Window | as today | Open a **full** window |

- The parent-tab and item key rows are a proposal: they move today's ⌘1–9 and ⌘⇧[ / ⌘⇧] from items to parent tabs.
  The plans re-check every default for collisions on each platform, as plan 3a did.
- The renamed ids are a clean break: removed ids are reported as unknown keys (spec 11).

## 9. Saving and restoring (amends spec 13)

Saving and restoring keep spec 13's rules and cover every window kind:
- the main window's parent tabs and active one;
- each full window's parent tabs;
- each pane window with its layout and origin.

Only each parent tab's visible items start at once: the active parent tab's selected item in each pane. The others
start when first shown, as spec 13 says for unselected items. Plans 6a and 6b are re-planned against this model, and
the hand-offs already recorded for them stay.

## 10. The SDK (amends spec 14)

- **Plugins see items as tabs and windows as windows,** as today.
  - `TabPlacement.ACTIVE_GROUP` means the active pane of the active parent tab.
  - `NEW_WINDOW` opens a full window, which is what New Editor Window needs.
- **Parent tabs and pane windows are not exposed to plugins in this round.** A plugin sees a pane window as a
  `WindowHandle` that is not the main window.
- **Events:** a move between parent tabs of one window is a layout change and reports no `TAB_MOVED`. A move into or
  out of a pane window does report one, since it moves between windows.

## 11. Edge cases (amends spec 15)

- Bring Back into a maximized parent tab ends the maximize, as a new group does today.
- Bring Back when the origin pane is gone but its parent tab remains: the panes go beside that parent tab's active pane.
- A pane window whose origin was a parent tab that has since popped out to a full window: the origin follows the
  parent tab by id.
- Quit with pane windows open: they are saved with their origins and restored as pane windows.
- Single-instance requests (plan 1) open in the main window's active parent tab.
- Enterprise and the standard look differ only in what shows; the model and commands are the same.
- The one-tab rule landing now ("a window holding a single tab shows no strip") is superseded by section 7's two
  rules.

## 12. Delivery

Plans 6a and 6b stay paused. Three plans come first, in this order:

1. **Parent tabs:** the model, the window content shapes, the parent-tab bar, pane-level strip hiding, `new_tab` and the
   parent-tab commands and keys, and the group → pane renames. Single-instance and SDK placement follow (section 10).
2. **Pop Out and Bring Back:** pane windows and origins, full windows from parent tabs, Move to Main Window, the window
   toolbar and its ⋮ menu, and the context-menu entries.
3. **Dragging parent tabs:** reorder, between windows, and out to a full window; plus an item torn out to a pane window
   (plan 5's tear-out now makes a pane window).

Then plans 6a and 6b are re-planned to save and restore parent tabs, pane windows and origins. Each plan runs as before:
pre-flight, tasks with reviews, a final review, a fix wave and a STATUS entry with GUI checks.

## 13. Out of scope

- Named parent tabs, and a root folder per parent tab.
- Dropping a parent tab onto a pane to merge two layouts.
- Option B for the editor, unless the user pivots after trying A.
- Parent tabs in the SDK.
- More ⋮ entries beyond section 6.

## 14. Testing

- **Model:** headless tests of parent-tab operations, window content shapes, origins, Bring Back fallbacks and the
  invariants.
- **Views and commands:**
  - strip and bar visibility in both looks;
  - parent-tab switching keeping sessions;
  - every command and its keys, with a collision check on each platform;
  - the ⋮ menu's entries for each window kind.
- **Moves:**
  - exactly one `TAB_MOVED` for a move between windows, and none for a move between parent tabs;
  - pop out and bring back keeping sessions and focus.
- **GUI checks for the user:**
  - the ⋮ placement in each look and platform;
  - parent-tab bar visibility;
  - pop out and bring back;
  - parent-tab drags between windows.
