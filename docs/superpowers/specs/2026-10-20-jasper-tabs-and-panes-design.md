# Jasper tabs and panes

Status: **Directed by the user on 2026-10-20.** The design below is the user's own description of the feature,
together with their answers to the follow-up questions, and their instruction: "stop 6a, make this plan, execute, then
continue".

This design amends the [parent tabs spec](2026-10-19-jasper-parent-tabs-design.md) and the
[workspace tabs spec](2026-10-16-jasper-workspace-tabs-design.md). Where they disagree, this document wins. It replaces
the parent tabs spec's three levels (window, parent tabs, panes, items) with two levels: window, tabs, panes.

Plan 6a is stopped after its Task 5. Plans 6a and 6b will be re-planned on top of this design.

## 1. Intent

The user, 2026-10-20:

> We have a concept of Tabs and Panes. Tabs can contain panes. Panes can be split. Tabs can not. A tab can contain
> multiple split panes in any direction, but can not contain other tabs.
>
> Tabs live in the tab strip at the top of the window, always - in Normal Mode, a window with one tab does not show the
> tab bar. In enterprise mode, a window with one tab always shows that one tab.
>
> Tabs can be dragged out into their own windows. Panes can not.
>
> We introduce a new action where we can split-to-create-a-terminal or split-to-create-a-editor or
> split-to-create-<plugin feature> (so sdk update).
>
> If we hold down a modifier key and drag, we can move a pane around into different positions.
>
> Editor can still be in it's own tab, the user decides - new editor tab, new editor pane in currently focused tab.
> Editor right-click context gains a "split" option which splits into another editor or terminal (so really two
> options, but lets move them under a submenu so we don't make the context menu huge)

This is the model terminal users already know from iTerm2, Windows Terminal, WezTerm, Kitty, Ghostty and IntelliJ's
terminal tool window. Plans 7a-7c built a middle level, a tab strip inside every pane. That level is what made the two
looks behave differently, and it is gone.

## 2. Decisions (the user, 2026-10-20)

1. **Two levels.** A window holds **tabs**, and a tab holds **panes** split in any direction. A tab never holds tabs.
2. **A pane holds exactly one item:** a terminal, an editor file, or a plugin's view. A pane has no tab strip.
3. **One tab strip per window, at the top.**
   - **Normal mode:** it hides while the window holds one tab.
   - **Enterprise:** it always shows, even with one tab.
   - In everything else the two modes behave the same; only the look differs. This supersedes this morning's "Enterprise
     shows no parent-tab bar".
4. **Tabs can be dragged out into their own windows. Panes cannot.**
5. **Split with a choice.** A split can create a terminal, an editor, or a plugin's feature. The SDK gains this.
6. **Moving panes, the iTerm model.** A modifier-drag moves a pane. The modifier is ⌘⌥ on macOS and Ctrl+Alt elsewhere.
7. **The Editor in a tab or a pane.** The user decides: New Editor Tab, or New Editor Pane in the focused tab.
8. **Opening a file from outside the editor opens a new editor tab** by default. This covers Cmd-click in a terminal,
   `jasper file.txt` and Open in Editor….
9. **A plugin's view opens as a tab or as a pane;** the plugin chooses.
10. **Split submenus.**
    - The Editor's right-click menu gains a **Split** submenu: Terminal Right, Terminal Down, Editor Right, Editor Down.
    - A terminal's right-click menu gains the same **Split** submenu.
    - Plugins' split items are listed after these.
11. **A pane can become its own tab.** It has a context-menu entry and a shortcut.
12. **Every action can be bound.** Each command in this design is an action in the keymap engine: Settings › Keymap and
    `[keybindings]` can bind or unbind it, whether or not it has a default. A plugin's commands are bound the same way.

## 3. Terms

| Term | Means | Plans 7a-7c called it |
|---|---|---|
| Window | The main window, or a full window | Main window, full window. Pane windows are gone |
| Tab | A split layout of panes, an entry in the window's tab strip | Parent tab |
| Pane | One region of a tab's split layout, holding one item | Group, or pane |
| Item | What a pane shows: a terminal session, an editor file, a plugin view | Tab, or item |
| Tab strip | The row of tabs at the top of a window | The parent-tab bar |

User-facing text says **tab** and **pane**. Which code names to keep or rename is the plans' call. The SDK keeps its
type names (`TabHandle` is an item), and its Javadoc says what each one is.

## 4. The model

- A window holds an ordered list of tabs, one of which is shown.
- A tab holds a split layout of panes, one of which is active, plus an optional maximize. A pane holds one item.
- **The main window's only tab may hold no pane.** That is the empty workspace: New Terminal Tab, Open File.
- **Closing panes and tabs:**
  - Closing a tab's last pane closes the tab.
  - When that tab is the main window's only tab, the main window shows the empty workspace instead.
  - Closing a full window's last tab closes the window.
- Switching tabs keeps every pane's item alive, as plan 7a does for parent tabs.
- Ids stay unique across windows, as in plan 7b's Rulings 10.
- **Pane windows go.** That takes with it:
  - plan 7b's pane windows and their `Origin`;
  - Pop Out Tab (item to a pane window), Pop Out Pane and Bring Back;
  - plan 7c's tear-out of an item to a pane window.

## 5. The tab strip (views)

- Every window has one tab strip, at the top of its workspace. It uses the look's strip: equal tabs in normal mode,
  IntelliJ Classic tabs in Enterprise.
- **Visibility:**
  - Normal mode shows it from two tabs.
  - Enterprise always shows it.
- **Entries:**
  - An entry shows its tab's title, which is the active pane's item title.
  - Its tooltip carries Select Tab N's shortcut.
  - Its × closes the whole tab, after its items' close guards. Terminals close without a prompt.
- The strip's "+" opens a new terminal tab.
- No pane shows a strip, in either look.
- **The focused pane:** while a tab holds two or more panes, panes other than the focused one are dimmed, as iTerm2,
  Ghostty and Warp do; `terminal.dim_inactive_panes` sets how much. An inactive window dims its terminals as it does
  today.
- **Motion:** the tab strip keeps today's open and close motion where the look has motion. A dragged tab takes its
  place at once, and nothing else animates.

## 6. Moving tabs

- A tab drags along its strip to reorder.
- It drags onto another window's strip to move there whole.
- It drags outside every window to become a full window under the cursor.
- This is plan 7c's parent-tab drag, kept. A window whose strip is hidden takes a dragged tab along its top band, and
  the main window's empty workspace takes one anywhere over it.
- A window's only tab, whose strip is hidden in normal mode, moves through its commands:
  - **Move Tab to New Window**, which is plan 7b's Pop Out Parent Tab, renamed;
  - **Move to Main Window**, for a full window's tabs.
- Every window keeps plan 7b's ⋮ (View Options) menu. Its entries:
  - **main window:** Move Tab to New Window, from two tabs;
  - **full window:** Move Tab to New Window, from two tabs, and Move to Main Window.

## 7. Moving panes (the iTerm model)

- **Starting a drag.** Holding the modifier (⌘⌥ on macOS, Ctrl+Alt elsewhere) and dragging inside a pane starts a pane
  drag.
  - It begins past plan 5's drag threshold.
  - A drag without the modifier keeps its meaning in that pane, for example a terminal selection.
- **Targets:**
  - **Another pane, in this tab or another window:** the pane splits beside it, on the side the cursor is nearest to.
    The landing overlay shows where.
  - **A tab in any window's strip:** hovering over it shows that tab, so the pane can then be dropped beside one of its
    panes.
  - **A strip's empty end, its "+", or an entry's outer quarter:** the pane becomes a new tab there (the equal tabs have
    no "+" or empty end; amended 2026-10-21, plan 8c's ruling).
- **Not a target:** outside every window, nothing happens. Panes never become windows, by the user's rule above. iTerm2
  would make a window here; Jasper does not.
- **Ending a drag:**
  - Esc ends it, and so do focus loss and the lost-release cases, as in plan 5.
  - A pane dropped where it already is does nothing.
- **Effect on the source tab:** moving a tab's last pane out closes that tab, or leaves the main window's empty
  workspace.
- **Pinned panes:** a pinned item, or one being closed, does not move.

## 8. Splitting and creating

- **Split Right** (Cmd+D / Ctrl+Shift+D) and **Split Down** (Cmd+Shift+D / Ctrl+Alt+Shift+D) keep their meaning. They
  put a terminal pane beside the focused pane.
- **Split with a choice:**
  - Split Right and Split Down each list what the new pane can hold: Terminal, Editor, and each plugin's split item.
  - **Places:** the Tab menu, the palette, and the Split submenus of a terminal's and the Editor's context menus.
  - **Entries:** each one is its own bindable action, for example Split Right with Editor.
- **The Editor:**
  - New Editor Tab opens a new tab holding an editor pane.
  - New Editor Pane splits an editor pane beside the focused pane, to the right.
  - Split Down with Editor puts it below.
  - Each file is its own item, as in parent tabs decision A.
  - New Editor Window and its toolbar button stay.
- **Opening from outside opens a new editor tab** (decision 8). That covers a Cmd-clicked path, `jasper FILE`, Open in
  Editor… and Recent Files.
- **Plugins:** a plugin registers split items through the SDK (section 11), and Jasper lists them. The Editor uses that
  same SDK route.

## 9. A pane becomes a tab

**Move Pane to New Tab** moves the focused pane into a new tab right after its own tab, and shows the new tab.

- **Where:** a terminal's and the Editor's context menus, the Tab menu and the palette.
- **Default shortcut:** ⌥⌘T on macOS, Ctrl+Alt+Shift+T elsewhere, unless the plan's collision check finds a clash.
- **A tab's only pane:** the command is disabled there, because it would change nothing.

## 10. Commands

Every row is an action in the keymap engine (decision 12). Defaults go through the plan's per-platform collision
check.

| Action | Default (macOS / elsewhere) | Does |
|---|---|---|
| `new_tab` | ⌘T / Ctrl+Shift+T | A new terminal tab |
| `split_right`, `split_down` | ⌘D, ⌘⇧D / Ctrl+Shift+D, Ctrl+Alt+Shift+D | A terminal pane beside the focused pane |
| `split_right_editor`, `split_down_editor` | none | An editor pane beside the focused pane. Registered by the Editor through the SDK |
| A plugin's split items, right and down | none | That plugin's view in a new pane |
| `new_editor_tab` (the Editor's) | none | A new tab holding an editor pane |
| `close_pane` | ⌘W / Ctrl+Shift+W | Closes the focused pane, and its tab when it was the last |
| `close_tab` | ⌥⌘W / Ctrl+Alt+Shift+W | Closes the whole tab |
| `close_window` | ⌘⇧W / unchanged | Unchanged |
| `next_tab`, `previous_tab` | ⌘⇧] ⌘⇧[ / Ctrl+PageDown Ctrl+PageUp | Switch tabs |
| `select_tab_1`-`9` | ⌘1-9 / Ctrl+1-9 | Show tab N |
| `focus_pane_left`, `_right`, `_up`, `_down` | unchanged | Focus the neighbouring pane |
| `maximize_pane` | unchanged | Unchanged |
| `move_pane_to_new_tab` | ⌥⌘T / Ctrl+Alt+Shift+T | Section 9 |
| `move_tab_to_new_window` | none | A full window takes the shown tab |
| `move_to_main_window` | none | A full window's tabs join the main window |

**Removed, with no alias:**
- `new_tab_in_pane`;
- `next_parent_tab`, `previous_parent_tab`, `select_parent_tab_1`-`9`, `close_parent_tab` (their meanings move to the
  tab rows above);
- `pop_out_tab`, `pop_out_pane`, `pop_out_parent_tab`, `bring_back`.

Saved bindings that name a removed id are reported as unknown keys, as plan 7a does. Ctrl+Tab no longer cycles items in
a pane, because a pane holds one item.

## 11. The SDK

The SDK stays 0.21.0. It is unreleased on this branch, so the changes below fold into it.

- **`TabPlacement`:**
  - `ACTIVE_GROUP` becomes `NEW_TAB`: a new tab in the owner's window, or in the main window when there is no owner.
  - `SPLIT_RIGHT` and `SPLIT_DOWN` mean a new pane beside the owner window's focused pane.
  - `NEW_WINDOW` is unchanged.
  - This is a clean break.
- `Terminals.openTab` opens a new tab. `PaneHandle.split` keeps its meaning.
- **Split items.** A plugin registers one with an SDK call, for example
  `Windows.splitItem(SplitItemSpec(id, label, icon), opener)`, which returns a `Subscription`. The plan picks the exact
  names.
  - Jasper then lists the item in every Split list.
  - It registers the item's right and down actions, which are bindable like any plugin action.
  - When an action runs, Jasper calls the opener with the window and the placement (`SPLIT_RIGHT` or `SPLIT_DOWN`), and
    the plugin opens its view there.
- The testkit and the contract cases cover all of this on both runtimes.
- `jasper-sdk/README.md`, `docs/plugin-authoring.md` and `docs/sdk-architecture.md` move with the code.

## 12. Saving and restoring

Plans 6a and 6b are re-planned on this model.

- A window saves its tabs, in order, with the shown one.
- Each tab saves its split layout. Each pane saves its one item.
- Pane windows and origins are gone from the file.
- 6a's Tasks 1-5 stay where they still fit:
  - SDK 0.21.0;
  - waiting terminal tabs;
  - window placements;
  - the store.
- The codec and the snapshot change with the model.

## 13. Edge cases

- **A new terminal from outside:** a single-instance request for a new terminal opens a new tab in the main window.
- **Moving items:** `ContentTab.moveToNewWindow()` opens a full window holding a new tab with that item.
  `moveToMainWindow()` gives it a new tab in the main window.
- **A maximized pane:** it ends when a pane is dropped into its tab, or when another pane is split from it.
- **Hovering a hidden tab during a pane drag:** this shows the hidden tab for the drop. The tab that showed before shows
  again if the drag ends elsewhere.
- **The Enterprise mock tests:** they measure the strip with one tab again, now always shown, as in the original
  mocks.

## 14. Delivery

1. **Plan 8a, tabs hold panes.**
   - Each pane holds one item.
   - The per-pane strips go.
   - Each window's tab strip follows section 5's rules.
   - The commands and defaults follow section 10.
   - Pane windows, Pop Out and Bring Back go, and so does the item drag.
   - Tab dragging stays, renamed from 7c.
   - The focused-pane dimming.
2. **Plan 8b, split with a choice.**
   - The SDK's `TabPlacement` change and split items.
   - The Editor's tab or pane: New Editor Tab, Split with Editor.
   - The Split submenus.
   - Move Pane to New Tab.
   - Opening from outside opens a new editor tab.
3. **Plan 8c, moving panes:** the modifier-drag of section 7.
4. **Plans 6a and 6b**, re-planned on 8a-8c.

## 15. Out of scope

- Per-pane title bars.
- Broadcasting input to several panes.
- A setting for the dimming.
- A pane becoming a window.
- Naming tabs, apart from what a terminal's rename already does.

## 16. Testing

Tests stay headless, as in plans 5 and 7a-7c:
- views driven directly;
- the pretend screen for drags across windows;
- the contract on both runtimes for the SDK.

Every action in section 10 needs both of these:
- a keymap test that it is bindable and has its default;
- a collision test per platform.
