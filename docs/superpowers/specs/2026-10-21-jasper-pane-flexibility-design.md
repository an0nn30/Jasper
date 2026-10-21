# Jasper pane flexibility: open beside, move into tabs, two views of a file

Status: **Approved by the user on 2026-10-21** ("The pane-flexibility spec looks right. Write plans 9a and 9b and
execute them on the branch."). This design amends the
[tabs and panes spec](2026-10-20-jasper-tabs-and-panes-design.md); where they disagree, this document wins. It is
planned as two plans:
- **9a:** open beside, and moving panes and tabs into tabs (sections 3 to 5);
- **9b:** two views of one file (section 6).

## 1. Intent

The user, 2026-10-21:

> I think it would be useful to have the ability to open a file in an editor in a split pane. Or move an editor to a
> tab, I'd like to be able to move into into another tab as a pane. Or if I open a tab I want to be able to move it
> into another tab as a pane.

The controller answered with three gaps and their sizes. The user's reply was "sounds good". The user did not choose
between moving an already-open file's pane and showing the file in two panes. This draft follows VS Code and IntelliJ
and shows it in two panes; section 6 is that part, and it is a plan of its own so the user can drop it.

## 2. What already works and stays

- **Split › Editor Right or Editor Down** opens an untitled editor in a new pane beside the focused one.
- **A pane drag** moves any pane into another tab of any window: hold Cmd+Option (Ctrl+Alt) and drag inside the pane,
  hover over the middle of a tab's entry to show it, then drop beside one of its panes. This includes a tab's only
  pane, and its emptied tab closes.
- **Move Pane to New Tab** (Cmd+Option+T) gives a pane its own tab.
- **A tab's entry drag** reorders the tab, moves it to another window's strip, or tears it out into a new window.

## 3. Open a file beside (9a)

- **The Split submenu.** The Editor adds a second split item, **Open File**. Every Split submenu (the Tab menu's, a
  terminal's and an editor's right-click) therefore gains **Open File Right…** and **Open File Down…**.
  - Each one opens the Editor's file chooser, taking a single file.
  - The file opens in a new pane to the right of, or below, the focused pane of the window's shown tab, where
    `SPLIT_RIGHT` and `SPLIT_DOWN` place items today. The new pane takes the focus.
  - Both are keymap actions with no default shortcut.
- **The File menu.** **Open in Editor Pane…** sits next to **New Editor Pane** and does what Open File Right does.
- **A file that is already open.**
  - Until 9b lands, Open File Right and Down select its open pane, as **Open in Editor…** does.
  - With 9b, they show a second view of the file (section 6).
- **Cancel.** Cancelling the chooser opens nothing.

## 4. Move a pane into another tab (9a)

- **Move Pane to Tab ▸** goes after **Move Pane to New Tab**: in the Tab menu, a terminal's right-click menu, the
  Editor's right-click menu, and every plugin item's menu that offers Move Pane to New Tab.
  - It lists the window's other tabs by name, numbered as **Select Tab** numbers them.
  - Choosing one moves the focused pane to the right of that tab's active pane. That tab then shows, and the moved
    pane has the focus.
  - The submenu is disabled while the window has one tab.
- **Keymap.** **Move Pane to Tab 1** to **Move Pane to Tab 9** are keymap actions with no default shortcut, as
  **Select Tab 1–9** are. A number that names the pane's own tab, or no tab, does nothing.
- **Same rules as a drag.** A pane that a pane drag may not move cannot be moved this way either. An emptied tab
  closes, as it does after a pane drag. A maximized pane in the target tab stops being maximized.

## 5. Move a whole tab into another tab (9a)

- **The tab's right-click menu.** **Move Tab Into ▸** lists the window's other tabs.
  - Choosing one moves every pane of the tab to the right of the target tab's active pane, as one nested split that
    keeps their arrangement and sizes.
  - The moved tab's entry goes. Names stay with their panes: a tab is named after its active pane, so the merged tab
    shows the moved tab's active pane's name, which takes the focus (amended 2026-10-21, plan 9a's spec issue; a tab has
    no name of its own to keep or drop).
  - The moved tab's active pane takes the focus.
- **Dragging a tab's entry.** On a strip it does exactly what it does today. Two things are new:
  - **Over a pane** of any window's shown tab, the landing overlay shows the side nearest the cursor, as in a pane
    drag. Releasing there moves the tab's panes beside that pane, as one nested split.
  - **Spring-loading.** Holding the dragged entry still over another entry of a strip for 0.5 s (macOS's spring-loading
    delay) shows that tab at once, with no motion, so its panes become targets. The reorder marker behaves as it does
    today. Esc, or a release over no target, brings back the tab that showed before, as a pane drag does.
  - The tab's own panes are never targets.
- **Same rules as moving a tab.**
  - A tab with a dialog open in one of its panes still moves only along its strip.
  - A child window whose only tab moves into another window's tab closes, as it does when its last pane is dragged
    away.

## 6. Two views of one file (9b)

- **Opening a second view.**
  - **Open File Right…** and **Open File Down…** of a file that is already open show a second view of the same file in
    the new pane.
  - The Editor's right-click menu and its commands gain **Split File Right** and **Split File Down**, which show the
    focused file in a second view beside it. Both are keymap actions with no default shortcut.
- **One document, many views.**
  - An edit in one view shows in every view of that file at once.
  - Each view has its own caret, selection, scroll position, find bar, Vim state, and Markdown mode and preview.
  - Undo and redo belong to the document, as in VS Code and IntelliJ: undo in any view undoes the last edit made in any
    view.
  - The ● marker, Save, Revert to Saved and Reload belong to the document and act on it from any view.
- **Closing.**
  - Closing a view closes only that view.
  - Jasper asks about unsaved changes only when you close the file's last view.
- **Other behaviour.**
  - **Open in Editor…** of a file that is already open selects its most recently focused view.
  - Recent Files records the file once.
  - Every view comes back when Jasper restores its windows, and a remote file is read once for all its views.

## 7. Not in this design

- **Panes still never become windows,** by the user's rule.
- Candidates for later:
  - Option-click on a Recent Files item to open it beside;
  - Cmd+Shift-click on a file link in a terminal to open it beside;
  - a `jasper editor` flag that opens a file beside.

## 8. SDK and versions

- Plugin item menus get **Move Pane to Tab ▸** through the pane items the host already gives plugins for **Split** and
  **Move Pane to New Tab** (plan 8b). Every SDK addition goes into SDK 0.21.0, which has not shipped, so there is no new
  break and no version bump.
- The Editor takes the next minor version above the one it has when each plan executes.
