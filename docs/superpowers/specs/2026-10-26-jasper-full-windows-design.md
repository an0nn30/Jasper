# Jasper full windows and layouts

**Date:** 2026-10-26. **Status:** draft for the user's approval. Branch `claude/full-windows`, from `main` at
`c2c04684d`. Amends the [workspace tabs spec](2026-10-16-jasper-workspace-tabs-design.md) (sections 1, 6, 12, 13, 14
and 20), the [parent tabs spec](2026-10-19-jasper-parent-tabs-design.md) and the
[tabs and panes spec](2026-10-20-jasper-tabs-and-panes-design.md) (decision 4 and sections 6 and 7); this spec wins where
they disagree. Choices the user did not state are marked *Ruling*, with the reason, for review.
Amended 2026-10-27 (the user): status bar visibility stays the app-wide `window.status_bar` setting.

## 1. Intent

The user's words (2026-10-26): "bring back the ability to have multiple instances of the app open (cmd+n opens a new
instance, like we had before that refactor), and keep dragging tabs into their own windows - but instead of them being
bare windows, they are full instances of the app and can be dragged from one window to another."

"Instance" means a full window, as before the workspace tabs series: every window had all of Jasper's chrome, and
cmd+n made another one in the same process. One process stays: a terminal's shell session cannot move between
processes, so dragging tabs between windows needs every window in one process.

## 2. Decisions (the user, 2026-10-26)

1. **IntelliJ is the gold standard.** "Treat IntelliJ as the 'gold standard' for open design questions from now and
   forever. If the answer can be obtained by just copying IntelliJ behavior, then that's what we do." A Jasper window
   plays the part of an IntelliJ project frame.
2. **Every window is a full window,** with its own header and toolbar, menus, tool window dock and stripes, status bar
   and notification layer, as an IntelliJ project frame has its own tool windows. The main window and child windows go.
3. **cmd+n opens a new full window.**
4. **Dragging out makes a full window** (an exception to IntelliJ, whose detached editor window is bare): a tab or a
   pane dragged outside every window becomes a full window, and tabs and panes snap into any other full window. This
   reverses the tabs and panes spec's "Panes never become windows".
5. **The process lock and residency stay** as they are (one process per Jasper home; the menu bar item and tray icon).
6. **Relaunch opens a single window,** never every window that was open (an exception to IntelliJ's reopening of all
   projects).
7. **Last close wins.** The state of the last window to close is what relaunch loads. A setting chooses between
   **Last Known State** (that window's tabs, panes and tool windows) and **Empty State** (the default layout).
8. **Notifications show in every open window.** When no Jasper window is focused, a system notification goes out
   instead. Clicking a card or a system notification takes the user to the window the notification pertains to: "Window
   A has a process that finished and we get a notification, that notification goes to all open windows (or a system
   notification if none are focused), and clicking it takes me to the window that the notification pertains to."
9. **A default layout for new windows.** The user sets it by saving the active window's layout as the default, or
   resets it to the fresh empty state.
10. **A layout is the tool windows, the tabs and the panes within the tabs** (beyond IntelliJ, whose layouts hold tool
    windows only).
11. **Named layouts.** Save Current Layout As a new named layout that need not be the default. It is listed under
    Window › Layouts with Default and Reset Default.
12. **Applying a layout replaces the active window's contents.** Its tabs close, after their guards, and the layout's
    tabs, panes and tool windows take their place.

## 3. IntelliJ, the reference

Verified on 2026-10-26 against intellij-community master and the installed IntelliJ IDEA 2026.2.3 (build
262.10968.63, `idea/PlatformActions.xml` and `messages/ActionsBundle.properties` in `intellij.platform.ide.impl.jar`).

- **Tool windows per frame.** Each project has its own `ToolWindowManagerImpl`; `ToolWindowFactory.createToolWindowContent`
  runs once per project. The layout is project state (`ToolWindowManagerState.kt`, in `.idea/workspace.xml`). Frame
  bounds are per project (`ProjectFrameBounds.kt`).
- **Window › Layouts** (`LayoutsGroup` in `WindowMenu`), in this order: **Default**
  (`RestoreFactoryDefaultLayout`); a separator; one submenu per saved layout (`CustomLayoutsGroup`); a separator;
  **Restore Current Layout**, **Save Changes in Current Layout**, **Save Current Layout as New…** (a "New Layout" dialog,
  first name "Custom"). A saved layout's submenu is Apply, Rename, a separator, Delete… (the active one has Restore and
  Save Changes instead of Apply, and a check mark). Default cannot be edited or deleted; the current layout cannot be
  deleted. Applying changes only the current frame.
- **Storage.** `ToolWindowDefaultLayoutManager`, an application service, keeps the layouts in `options/window.layouts.xml`.
  A layout holds tool window descriptors only (anchor, visibility, weights, float and window bounds), no editor tabs. A
  new project takes the active layout.
- **Notifications.** A project's balloon shows in its own frame; an application-level one in the focused frame
  (`NotificationsManagerImpl.findWindowForBalloon`). System notifications go out only while the IDE is not active
  (`SystemNotificationsImpl`); clicking one only brings the IDE forward.
- **Menus.** Each frame has its own screen menu bar on macOS (`MacMenuBar.kt`), refilled when the frame activates.
- **Closing.** Closing one of several frames closes that project; the last frame shows the Welcome screen on macOS, and
  quits elsewhere (`CloseProjectWindowHelper.kt`).

Jasper departs from this where section 2 says so (decisions 4, 6, 7, 8, 9, 10 and 12); elsewhere it copies it.

## 4. Verified facts this design rests on

Established on 2026-10-26 at `main` `c2c04684d`. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless
they start with `gradle/` or `jasper-sdk/`.

### 4.1 Windows

- `JasperWindow` is sealed, permitting `MainWindow` and `ChildWindow` (`workspace/JasperWindow.java:17`); `WindowRole`
  is MAIN or CHILD (`workspace/WindowRole.java:6-11`).
- `WindowManager` holds one `MainWindow main` and a `children` list (`workspace/WindowManager.java:136`, `:153`).
  `ensureMain` (`:279-302`) is the only place a main window is made (pinned by
  `gradle/application-architecture.gradle.kts:113`). `openMain` (`:313-359`) restores the saved main window and then
  every saved child window.
- `WindowContent` builds the same `WindowChrome` for every window (`workspace/WindowContent.java:374`), and its
  `DockState` and `ToolWindowArea` are already per window (`:157-158`, `:390`). The role gates the rest:
  - a main window gets the header, the body (stripes, area, bottom strip) and the status bar; a child only the area
    (`:409-410`);
  - only the main window mounts its menu row (`mount`, `:452-462`); a child sets its `JMenuBar` only for the macOS screen
    menu bar (`mountChild`, `:474-530`, `:518`);
  - toolbar views are built (`:1287`), Zen restored (`:1322`) and stripes shown (`:2083`) for the main window only;
  - `WindowContributions` docks panels and renders status entries only when `docks = owner.isMain()`
    (`workspace/WindowContributions.java:88-112`).
- Other role checks: Zen, rail and status bar toggles (`workspace/WorkspaceActions.java:160-162`), toolbar modes
  (`workspace/WindowCommands.java:151-158`), the empty workspace (`workspace/WorkspaceView.java:251`), first-paint timing
  (`application/JasperApplication.java:679`), and the Buddy, which follows the main window
  (`application/JasperApplication.java:1935-1940`).
- Everything with no window goes to `ensureMain`: plugin tabs (`WindowManager.java:179-181`, `TabRegistry.java:331-349`),
  `TabPlacement.NEW_WINDOW` through `emptyWindow()` (`WindowManager.java:1038-1043`), handoffs and `jasper` paths
  (`JasperApplication.java:1047-1056`, `:1101-1117`), plugin subcommands (`:811-821`), the reopen event (`:944-959`),
  the first window (`:1068-1075`), Settings from the menu bar item (`:1012-1016`), notification clicks (`:1968-1982`)
  and the benchmark (`benchmark/BenchmarkRun.java:203`).
- `routePanel` rewrites a child window's panel request to the main window (`WindowManager.java:1013-1023`).
- `closeMain` runs every window's guards and closes everything (`WindowManager.java:732-738`, `closeAll` `:689-714`); a
  child window closes when its last tab goes (`workspace/ChildWindow.java:16`).

### 4.2 Commands and menus

- `new_terminal_window` (cmd+n / ctrl+shift+n), `close_window` (cmd+shift+w on macOS), `move_tab_to_new_window` and
  `move_to_main_window` (`commands/ActionId.java:14-17`).
- The menu model is per window (`workspace/WindowChrome.java:84-118`). `StandardMenuBar` has no Window menu;
  `ClassicMenuBar` has one (`workspace/ClassicMenuBar.java:13-46`). The ⋮ menu lists Move Tab to New Window, and in a
  child window Move to Main Window (`WindowContent.java:1974-1995`).
- The macOS menu bar item offers New Terminal Window, Settings… and Quit Jasper Completely; the Windows tray icon offers
  Open Jasper and Quit Jasper (`platform/ResidentTray.java:60-62`, `:121-124`).

### 4.3 Persistence

- `ui-state.toml` (`persistence/UiState.java`) holds one `rail_visible`, one `zen_mode`, and one dock (`[dock]`,
  `[panels."<id>"]`) per look (`[look."<id>"]`), with the auxiliary windows' bounds, the palette and the chooser. It is
  given to the main window only; children get `UiState.inMemory()` (`JasperApplication.java:589-590`).
  `WindowContributions.close` persists only when it docks ("the last window closed wins", `:664-666`).
- `workspace.toml` (`persistence/WorkspaceFile.java`) keeps every window: `Role {MAIN, CHILD}` (`:14-19`), and a
  `Snapshot` with exactly one main window (`:131-136`, `:299`, `:311`). `WorkspaceStore` debounces saves by one second
  (`workspace/WorkspaceStore.java:112`).
- The AOT and start-up benchmark seed, `benchmark/startup-workspace.toml`, has one main window
  (`benchmark/StartupRuns.java:85`, `gradle/packaging-support.gradle:204`).

### 4.4 Drags

- One `TabDrag` serves every window (`WindowManager.java:143`). A tab released outside every window tears out
  (`workspace/TabDrag.java:620-624`) through `moveTabToNewWindow`, which makes a child window
  (`WindowManager.java:440-466`, `:747-757`). Panes never tear out (`TabDrag.java:607-611`). The main window's empty
  workspace takes a drop anywhere over it (`TabDrag.java:68`, `:85`, `:1007`).
- `DockDrag` works within one window; released outside it, or over another Jasper window, the tool window detaches
  (`workspace/DockDrag.java:37-55`). Detached tool windows exist for the main window only (`JasperApplication.java:592`,
  `workspace/DetachedToolWindows.java:27-38`).

### 4.5 Notifications

- One `NotificationCenter` holds the history (at most 500, in memory) and one pending set
  (`notifications/NotificationCenter.java:24-58`). `Delivery.route` shows a card while Jasper is FOCUSED, and otherwise
  sends a system notification or keeps the card pending (`notifications/Delivery.java:48-63`). FOCUSED means any Jasper
  window is active (`JasperApplication.java:1567-1585`).
- Cards show in the main window only: one `CardLayer` per attached window, and only the main window attaches
  (`application/NotificationsFeature.java:26-27`, `:67`, `:151-159`; `JasperApplication.java:595`, `:1945-1946`).
- A request names a target window (`notifications/NotificationRequest.java:12-19`); a command's notification also knows
  its pane, which a click turns into its tab's window (`notifications/CommandNotifier.java:202-212`,
  `JasperApplication.java:1984-1991`). A system notification's click raises that window, else the target, else the last
  active window (`application/SystemNotices.java:91-110`, `JasperApplication.java:1968-1974`). A card's click runs the
  request's action, which for a command focuses the pane (`notificationui/NotificationCard.java:89`,
  `CommandNotifier.java:180`).

### 4.6 The SDK

- Version 0.22.0 (`jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:9`).
- `WindowHandle.isMain()` (`terminal/WindowHandle.java:46-54`), `ContentTab.moveToMainWindow()` (`ui/ContentTab.java:74`).
- `PanelFactory` runs "for the main window's dock", again "after the main window closed and opened anew"
  (`ui/PanelFactory.java:4-10`); `PanelHost.window()` is the main window (`ui/PanelHost.java:10`); `Panels`,
  `StatusItem`, `StatusBar`, `Toolbar`, `ToolbarItem` and `ToolbarViewFactory` say the main window is the only one with a
  dock, status bar or toolbar. `TabPlacement.NEW_WINDOW` is "a new child window"; a null owner in `Windows.openTab`
  means the main window (`ui/TabPlacement.java:12-22`, `ui/Windows.java:30-58`). Every SDK Javadoc that names the main
  or a child window is listed in the plan that changes it.

## 5. Windows

### 5.1 One kind of window

- `JasperWindow` becomes one final class; `MainWindow`, `ChildWindow` and `WindowRole` go. Every window mounts what
  the main window mounts today: header and toolbar, the menu row (or the macOS screen menu bar), the stripes, dock and
  bottom strip, the status bar, the notification corner layer and its ⋮ menu.
- `WindowManager` holds a list of windows in opening order and their activation order. It makes every window, in one
  method; `verifyApplicationArchitecture`'s MainWindow rule becomes "a `JasperWindow` is made only in
  `WindowManager.open`". Its WindowManager and WindowFrame rules stay.
- Each window has its own Zen mode, rail, dock, tool window placements and detached tool windows.
  The View menu's toggles act on the window they are chosen in. `window.toolbar` and the other settings stay app-wide.
- The Buddy follows the most recently active window. *Ruling:* nothing else in Jasper is special to one window any more.
- Settings, About, Licenses and the file chooser stay single app-wide surfaces. The Errors tab opens in the most
  recently active window.

### 5.2 Opening

| Request | Result |
|---|---|
| cmd+n (`new_window`, File › New Window, the toolbar button, the palette) | A new window from the default layout (section 7) |
| A tab or pane dragged outside every window | A new window holding that tab, or a tab holding that pane, with the default layout's tool windows |
| Move Tab to New Window | The same, for the active tab |
| New Editor Window (the Editor) | A new window with the default layout's tool windows and one tab holding a new untitled document |
| `TabPlacement.NEW_WINDOW` (a plugin) | A new window with the default layout's tool windows and that tab |
| Anything with no window of its own (a handoff, `jasper` paths or subcommands, a plugin tab without an owner, Settings from the menu bar item, the Errors tab), while a window is open | The most recently active window |
| The same while no window is open, the reopen event (Dock click), the first window at start, the Windows tray's Open Jasper | One window, as section 6 restores it, then the request runs in it |
| The macOS menu bar item's New Window | A new window from the default layout |

- `new_terminal_window` is renamed `new_window`, **New Window**, keeping cmd+n / ctrl+shift+n. The menu bar item's New
  Terminal Window becomes New Window.
- A new window opens where the platform puts one, cascaded from the active window (as the pre-refactor windows were); a
  torn-out one opens under the cursor, as today.
- *Ruling:* a dragged-out window takes only the default layout's tool windows, because the tab or pane it was made for
  is its content.

### 5.3 Closing

| Event | Result |
|---|---|
| Close Window (cmd+shift+w, the title bar) | That window's tabs' guards run; a refusal keeps the window. Its state is saved as the last state (section 6) and it closes |
| A window's last tab closes | The empty workspace stays (IntelliJ, and the workspace tabs spec's decision 9) |
| A window's last tab or pane moves to another window | The window closes. *Ruling:* otherwise every drag that merges a window into another would leave an empty window behind |
| The last window closes | Residency decides, as today, whether the process stays |
| Quit (Cmd+Q, the menu bar item's Quit Jasper Completely) | Every window's guards run; a refusal keeps every window. The most recently active window's state is saved as the last state; every window closes |
| macOS Cmd+Q while resident (`toBackground`) | As Quit, with the process staying resident |

*Ruling:* on Quit the most recently active window counts as the last to close, because it is the one the user was
working in, and the windows close together.

### 5.4 Moving tabs and panes

- Every drop target of today's tab and pane drags works in every window: another window's strip, beside a pane, a new
  tab place, and any window's empty workspace (today only the main window's).
- A tab released outside every window becomes a new window, as today. **A pane released outside every window becomes a
  new window** holding a tab with that pane. Dragging a window's only tab, or a tab's only pane in a window's only tab,
  outside every window does nothing; that window moves by its title bar.
- **Move to Main Window** goes. In its place **Move Tab to Window ▸** lists the other open windows by their title (the
  active tab's title) and moves the active tab to the one chosen. *Ruling:* a window with its strip hidden still needs a
  command to move its only tab, and there is no main window to name.
- The ⋮ menu lists Move Tab to New Window (from two tabs) and Move Tab to Window ▸ (while another window is open).
- `DockDrag` keeps IntelliJ's rule: a tool window dragged over another Jasper window detaches; tool windows do not move
  between windows.

### 5.5 Menus

- The standard menu bar gains a **Window** menu, after Tab, as IntelliJ's has; in the classic menu bar Layouts joins
  the existing Window menu. It holds **Layouts ▸** (section 7.3).
- On macOS each window's screen menu bar is its own, built from the same model, and follows the focused window, as now.

## 6. The last state and relaunch

### 6.1 What is saved

- A window's **state** is its bounds and maximised state, its tabs and panes (as `workspace.toml` keeps them today), and
  its tool windows: Zen and rail visibility, the dock's sizes and ratios, each tool window's slot, order,
  visibility, mode and own-window bounds. The dock keeps one entry per look it has been shown under, as `ui-state.toml`
  does today; a look the state has none for starts from that look's built-in dock.
- `workspace.toml` keeps **one** window's state, the last state. It is written:
  - within a second of each change, with the most recently active window's state (so a crash loses at most a second);
  - at once when a window closes, with that window's state;
  - on Quit, with the most recently active window's state.
  The last window to close is therefore the last to write: last close wins.
- `ui-state.toml` keeps the auxiliary windows' bounds, the palette and the chooser. `rail_visible`, `zen_mode`, `[dock]`,
  `[panels]` and their `[look."<id>"]` tables move into the window state. Its `version` and `workspace.toml`'s go up;
  an old file is read as no saved state (Jasper is an MVP and keeps no migration).

### 6.2 The setting

`system.reopen` (System Settings › General, beside Run in background, as IntelliJ's "Reopen projects on startup" sits in
System Settings):

| Value | Label | Opens |
|---|---|---|
| `last_known_state` (default) | Last Known State | The last state: its tabs and panes come back as restore does today (fresh shells in their last local directory, the Editor's files, plugin restorers or placeholders), with its tool windows and bounds |
| `empty_state` | Empty State | The default layout (section 7), at the last state's bounds when there is one |

It applies whenever Jasper opens a window while none is open: at start, and while resident (Dock click, a handoff, a
notification click). The `--background` rehearsal never reads or writes `workspace.toml`, as now. When the last state
cannot be restored, Jasper keeps it as `workspace.toml.bad`, opens the default layout and posts "Jasper could not restore
your window", as it does today for the windows.

## 7. Layouts

### 7.1 What a layout is

A layout is a window's state without its bounds: its tabs and panes and its tool windows (section 6.1). *Ruling:* a
layout keeps each terminal's renamed title, profile and last local directory, and each plugin view's restore state,
exactly as the last state does, so applying one brings back the same tabs. A new window's own bounds come from section
5.2; a detached tool window's bounds are kept, as IntelliJ keeps them.

- **The default layout** is what cmd+n and Empty State open. Until the user saves one it is the **built-in layout**: one
  terminal tab starting where a new shell starts (the active terminal's directory, for cmd+n), and the look's built-in
  dock.
- **Named layouts** are any number of saved layouts, each with a name.

### 7.2 Storage

`layouts.toml`, beside `workspace.toml`, as IntelliJ keeps its layouts in one application file (`window.layouts.xml`):
an optional `[default]` layout (absent means the built-in layout) and a `[[layouts]]` array of `name` plus layout. Same
256 KiB limit, bounded read and atomic write as `workspace.toml`. A bad entry is dropped alone and logged; an unreadable
file is kept as `layouts.toml.bad` and read as empty.

### 7.3 Window › Layouts

In IntelliJ's order:

| Item | Does |
|---|---|
| **Default** | Applies the default layout to the active window |
| ─ | |
| *Each named layout, by name* ▸ **Apply**, **Rename…**, ─, **Delete…** | Applies it; renames it; deletes it after a confirmation |
| ─ | |
| **Save Current Layout as New…** | Asks for a name (IntelliJ's "New Layout" dialog, first name "Custom") and saves the active window's layout under it |
| **Save Current Layout as Default** | Saves the active window's layout as the default |
| **Reset Default Layout** | After a confirmation, makes the default the built-in layout again |

- Each item is a bindable action with no default key (IntelliJ's Shift+F12 for Default belongs to the shell in a
  terminal): `apply_default_layout`, `save_layout_as_new`, `save_layout_as_default`, `reset_default_layout`, and per
  named layout the palette's **Apply Layout: <name>**.
- A name is 1 to 64 characters, trimmed; a name already in use is refused in the dialog, as IntelliJ refuses it.
- *Ruling:* no layout is marked active and named layouts have no Save Changes. IntelliJ's active layout is the one new
  projects take; here the default plays that part, by the user's decision 9. To change a named layout, delete it and
  save it again.

### 7.4 Applying a layout

- Applying replaces the active window's contents (decision 12). Every tab's guards run first (an unsaved editor asks);
  a refusal leaves the window unchanged. Then its tabs close and the layout's tabs, panes and tool windows take their
  place. The window's bounds stay.
- A tab whose plugin is missing or cannot restore it comes back as a placeholder, as restore does. An untitled editor
  and Jasper's own tabs (the Errors tab) are not kept in a layout, as they are not in the last state.

## 8. Notifications

- Every window attaches a card layer. A card shows in **every open window** at once; reading, dismissing or expiring it
  in one removes it from all, because there is one notification center. Dismiss Cards dismisses in every window.
- Delivery keeps today's presence rule: while any Jasper window is focused, cards; otherwise a system notification (or
  pending cards, as today), shown in every window when Jasper is focused again. A pending card shown while no window is
  open waits for the next window.
- **The window a notification pertains to** is, in order: its pane's window (a command's notification, through its pane,
  tab and window as today), its target window, else none.
- **Clicking a card**, in any window, raises the window it pertains to and then runs its action (a command's focuses its
  pane). With no such window, or one that has closed, the action runs in the window clicked.
- **Clicking a system notification** raises the window it pertains to, else the most recently active window (opening
  one, section 6, if none is open), then runs its action, as `SystemNotices.clicked` does today. This goes beyond
  IntelliJ, which only brings the IDE forward.
- Each window's Notifications tool window lists the whole history, as it does today.

## 9. The SDK (0.23.0)

- `WindowHandle.isMain()` and `ContentTab.moveToMainWindow()` go. `ContentTab` gains `moveToWindow(WindowHandle)`.
- `PanelFactory.create` runs once for each window whose dock shows the panel, and again for a window that opens later;
  `PanelHost.window()` is that window, and `onClosed` runs when that window closes or the panel is removed. A plugin
  keeps its model outside its views (as the Javadoc already says), so several windows show the same model.
- `Panels`, `StatusItem`, `StatusBar`, `Toolbar`, `ToolbarItem` and `ToolbarViewFactory` describe every window: each
  window shows every panel stripe button, status item and toolbar item. A plugin's call that names no window acts on the
  most recently active window.
- `TabPlacement.NEW_WINDOW` opens a new full window (section 5.2). A null owner in `Windows.openTab` means the most
  recently active window, opening one when none is open.
- Every Javadoc listed in section 4.6 is reworded; `sdkReference` regenerates the reference; the testkit's fake follows.
  Bundled plugins move to `sdk = ">=0.23.0, <0.24"`; the external example builds against 0.23.0.

## 10. Removed

`MainWindow`, `ChildWindow`, `WindowRole`, `ensureMain`, `openMain`'s child restore, `mountChild`, `routePanel`,
`moveToMainWindow` and `move_to_main_window`, `new_terminal_window` (renamed), `WorkspaceFile.Role` and its
`"main"`/`"full"` strings, the "exactly one main window" checks, `TabRegistry`'s `restoresMainFirst` and main host, every
`isMain()` gate in section 4.1, the window-shaped keys of `ui-state.toml`, and `WindowHandle.isMain()`. The workspace
tabs spec's section 6.3 and its out-of-scope "more than one main window" are superseded.

## 11. Edge cases

- **Several windows and plugin views:** a plugin view lives in one tab in one window; only panels (tool windows) exist
  once per window.
- **A window whose only tab is dragged into another window** closes (section 5.3); its tool window layout is not saved
  as the last state, because it did not close by the user's Close Window. *Ruling:* only a close or Quit writes the last
  state at once; the debounced save follows the most recently active window, which is the receiving one.
- **Applying a layout to an empty window:** no guards to run; the layout fills it.
- **Save Current Layout as New… while a tab holds an untitled editor:** the layout leaves that tab out and says so in
  the dialog's message.
- **Opening a window while another is opening** (cmd+n held down): each request opens its own window, as the
  pre-refactor New Window did.
- **The empty workspace** shows New Terminal Tab and Open File in every window.
- **Displays:** a restored or layout-opened window is fitted to a usable display, as `WindowPlacement` does today.

## 12. Testing

- `WindowManager`: opening, closing, the last-state writes (debounced, on close, on Quit), activation order, and
  routing with and without a window open; no window is special.
- `WorkspaceFile` and `LayoutsFile`: round trips, limits, bad entries, the `.bad` files, old versions read as empty.
- Layout commands: save as new (names, refusal), save as default, reset, rename, delete, apply with a guard refusal and
  with a placeholder.
- Notifications: a card in every window, dismissal everywhere, click routing to the pertaining window from a card in
  another window and from a system notification, pending cards with no window.
- Drags: the resolver and synthetic events for pane tear-out, drops into a non-first window's empty workspace, and the
  source window closing when emptied by a move.
- SDK: the testkit contract for per-window panels, `moveToWindow`, `NEW_WINDOW` and a null owner.
- Headless throughout. The user checks in the GUI: cmd+n, tearing out and snapping tabs and panes between windows,
  per-window tool windows, Layouts, notifications across windows, relaunch with each setting value, and residency's
  menu bar item and tray icon.

## 13. Delivery

Five plans, each leaving Jasper runnable, written with `superpowers:writing-plans`:

1. **Full windows:** one window class with all chrome, per-window Zen, rail, status bar and detached tool windows,
   panels and status items in every window, routing to the most recently active window, `new_window`, Move Tab to
   Window, the SDK 0.23.0 changes. Until plan 3, relaunch keeps restoring every saved window and the dock is shared
   through `ui-state.toml` as now.
2. **Notifications in every window** and click routing (section 8).
3. **The last state and relaunch:** the window state, `workspace.toml` with one window, `ui-state.toml` slimmed,
   `system.reopen`, the AOT and benchmark seed.
4. **Layouts:** `layouts.toml`, the default and named layouts, the Window menu, apply.
5. **Drags:** pane tear-out, any window's empty workspace as a target, the emptied source window closing.

Docs move with each plan: `docs/workspace.md`, `docs/configuration.md`, `docs/keyboard-shortcuts.md`,
`docs/plugin-ui.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/STATUS.md`, the SDK reference and the
testkit README.

## 14. Risks

1. **Panels built once per window.** Every bundled plugin's panel (Sessions, SFTP, Tunnels, History, Notifications, File
   Servers, Vault and others) gets an instance per window; one that keeps state in its view, or registers a listener per
   build without removing it on `onClosed`, misbehaves or leaks. Plan 1 audits each.
2. **Memory per window.** A full dock per window costs more than a bare child window; plan 1 measures one extra window
   with the memory benchmark, with the user's go-ahead.
3. **The SDK break** touches every plugin, the testkit and the external example.
4. **Enterprise's chrome in several windows** (classic header, menus, side stripes) has only run in one window so far.
5. **Test churn** in every test that names the main or a child window.
6. **Drags cannot be verified headlessly** beyond the resolver and synthetic events.

## 15. Out of scope

Several processes per Jasper home; tool windows moving between windows; one tab shown in two windows; an active layout
or Save Changes for named layouts; restoring more than one window; per-window settings beyond the window state.
