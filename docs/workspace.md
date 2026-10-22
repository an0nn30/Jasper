# The workspace

A Jasper window is the workspace: tabs in the middle, each holding split panes of terminals, Editor files and plugins'
views. The main window adds tool windows docked around them, a header with the command palette's search field and the
toolbar, the menus and a status bar. This guide describes that frame and the dialogs that belong to it. What runs
inside a terminal pane is in [Terminal](terminal.md); features such as SSH, the Credential Vault or the Editor come from
bundled plugins and have their own guides, listed in [Plugins](plugins.md). Every shortcut named here is a default;
[Keyboard shortcuts](keyboard-shortcuts.md) lists them all and shows how to change them.

## The window at a glance

Jasper has one main window and any number of child windows. From top to bottom, the main window shows:

- **Header.** The search field in the middle shows what the focused pane is doing (`user@~/project : make` for a local
  shell, `host:path` for a remote one, or the title a program set) with the command palette's shortcut; clicking it
  opens the [command palette](#the-command-palette). The [toolbar](#the-toolbar) sits at the trailing end.
- **Menus.** On macOS in the screen menu bar; on Linux and Windows in Jasper's title bar (see
  [Title bar and menus](#title-bar-and-menus)).
- **Tool window area.** [Tool windows](#tool-windows-and-the-dock) dock on the left, on the right and in a bottom area
  that spans the window's width, with a rail of buttons on each side. Between them is the terminal column: the
  [tab strip](#tabs) (shown once the window has two tabs) above the shown tab and its [panes](#panes-and-splits).
- **[Status bar](#the-status-bar).** The focused pane's shell and folder, plugin items, the terminal size and the
  configuration state.

Notification cards appear in the bottom-right corner of the tool window area, above the status bar. A child window has
only its title bar, which shows the shown tab's title and ends with the window's ⋮ menu, above its tabs: no toolbar,
tool windows, status bar or Zen of its own, and on Linux and Windows no menu row. On macOS the screen menu bar shows the
same menus while a child window has the focus, and they act on that window. A child window has the same command palette
and keymap as the main window.

## Windows

**New Terminal Window** (File menu, the toolbar's New Terminal Window button, Cmd+N on macOS, Ctrl+Shift+N on Linux and
Windows) opens a child window with one terminal tab. Its shell starts in the focused pane's folder when Jasper knows
it, otherwise in your home folder. **Close Window** (File menu, Cmd+Shift+W on macOS, no default shortcut on Linux and
Windows) closes the window it runs in, as its title-bar close button does.

Closing a window closes every tab in it at once; Jasper does not ask about running programs. Only a plugin's item with
unsaved work, such as an Editor document, can ask first, and a refusal leaves the window open. A child window closes
with its last tab, whether that tab closes or moves to another window. Closing the main window closes every child
window too, after asking the items of every window. The main window's last tab leaves it open with an empty workspace
that offers **New Terminal Tab** and **Open File…** with their shortcuts; a new tab, a file you open and a tab or pane
dropped on it take its place.

**Move Tab to New Window** (the window's ⋮, a terminal's right-click menu, a tab's menu and the command palette; no
default shortcut) moves the shown tab, or the tab whose menu it was, with all its panes into a new child window, when
its window holds two or more tabs. Dragging a tab out of every window does the same ([Tabs](#tabs)). In a child window,
**Move to Main Window** (the window's ⋮, a terminal's right-click menu and the command palette) moves its tabs into the
main window in their order, after the tab the main window shows: the tab the child window showed shows there, the main
window comes to the front, and the child window closes. A move keeps every shell, file and view as it was. Neither runs
while a dialog is open in one of the panes it would move.

### Size and position

A new window is sized for `window.columns` × `window.lines` cells (100 × 35 by default; Settings › Window › New
Windows › Initial size) at the terminal font size. A tab dragged out of every window opens a window as large as the
workspace it left, where you release it.

### Saving and restoring the windows

Jasper saves its windows to `workspace.toml` in Jasper home within a second of each change, at once when a window
closes, and on Quit and when it goes to the background. It restores them at the next start and whenever the main
window opens again: the main window and each child window with its size, position and maximised state (fitted onto a
display when its place is no longer on one; never minimised), its tabs in order with the one it showed, and each tab's
split panes with their sizes, the active pane, a maximised pane and the item each pane held. A terminal comes back with
its name and profile and a new shell in its last local folder, or where a new shell starts when that folder is gone;
only the shown tab's terminals start at once, the others when their tab first shows. Scrollback, running programs and
dialogs, such as the Credential Vault's, do not come back. An Editor file ([Editor](editor.md#tabs)) or a Remote SSH tab
([Remote](remote.md#restored-ssh-tabs)) comes back through its plugin; a plugin's item that cannot come back shows a
placeholder that says why, with **Close** and, while its plugin runs, **Retry**. When the saved windows cannot all come
back, Jasper opens what it can, keeps the saved file as `workspace.toml.bad` and says "Jasper could not restore your
windows". Folders a launch names open in new tabs after the restored ones. See
[workspace.toml](configuration.md#workspacetoml).

### Title bar and menus

| | macOS | Linux and Windows |
|---|---|---|
| Title bar | The header shares the title bar with the window controls. | Jasper draws the title bar: the menus, then the header. |
| Menus | The screen menu bar. | In the title bar. As the window narrows, toolbar labels go first, then toolbar buttons move into **More actions** (⋯), then the menus after File fold into **More menus** (…), then all of them into one **Application menu** (☰); the search field shrinks last. F10 opens the first menu. |

The title bar and header row are as tall as `window.toolbar_height` (30 logical pixels by default). On Linux and
Windows, `window.native_decorations = true` (Settings › Window › Use the system title bar) uses the system's title bar
instead, with the menus as a row in the window and the header below them; it applies after a restart. The Compact
[view mode](#view-modes) also uses the system title bar, on every platform.

In Jasper's own title bar, blank parts of the header and toolbar drag the window.

## Tabs

A window holds tabs and shows one at a time; each tab holds one or more [panes](#panes-and-splits), and each pane one
item: a terminal, an Editor file or a plugin's view. **New Tab** (Cmd+T; Ctrl+Shift+T) opens a terminal tab at the end
of the strip whose shell starts in the focused pane's folder. The tab strip appears at the top of the terminal column
once a window has two or more tabs (under the Enterprise look always); tabs share its width equally and centre their
titles, each named after its active pane.

| To | Do this |
|---|---|
| Select a tab | Click it, or use **Select Tab 1**–**9** (Cmd+1–9; Ctrl+1–9), **Next Tab** (Cmd+Shift+]; Ctrl+PageDown) or **Previous Tab** (Cmd+Shift+[; Ctrl+PageUp), all in the Tab menu. Next and Previous wrap around. |
| Close a tab | Click the × that appears on hover (when the tab is wide enough to show it), middle-click the tab, choose **Close Tab** on its right-click menu or in the File menu, or press Cmd+Option+W (Ctrl+Alt+Shift+W). Close Tab closes every pane of the tab; when one of its items with unsaved work stays open, none closes. |
| Reorder or move tabs | Drag a tab along the strip to reorder it, onto another window's strip (or the top of a window that shows none, or anywhere over the main window's empty workspace) to move it there, onto a pane of the tab another window shows to put all its panes beside that pane, on the side the landing shows, or out of every window into a new child window (not a window's only tab). Hold it still over another tab's entry, in any window, for half a second to show that tab, then drop it beside one of its panes. A child window left without tabs closes. Where another of Jasper's windows, such as Settings, lies over the window under the cursor, nothing takes the drop. A tab with a dialog open in one of its panes moves along its strip only. Escape cancels. |
| Move a tab into another tab | **Move Tab Into** on the tab's right-click menu, or in the Tab menu for the tab that shows, lists the window's other tabs, numbered as **Select Tab** numbers them; choosing one puts all the tab's panes to the right of that tab's active pane, keeping their arrangement and sizes. The moved tab's entry goes, the tab it joined shows, and the moved tab's active pane takes the focus, so the tab is named after that pane. **Move Tab Into Tab 1** to **9** do the same for the tab that shows from the keyboard; they have no default shortcut. |
| Rename a tab | **Rename Tab** (Tab menu or the tab's right-click menu, Cmd+I; Ctrl+Shift+I) asks "Tab name (leave blank for automatic):" and names the terminal in the tab's active pane. |

Closing a terminal tab stops its shells immediately. A tab is named after its active pane: a name you gave that pane's
terminal with Rename Tab, otherwise the title a program set, the running command, the foreground program or the
shell's name ([Titles](terminal.md#titles)). A name you give stays until you rename the terminal with a blank name.
While `ui.animations.enabled` is on, tabs slide open in 475 ms and closed in 425 ms, unless the look has no motion
([Animations](configuration.md#animations)); a tab that moves in or out lands at once. A terminal tab's tooltip shows
its title (a plugin tab's shows its location), followed by its **Select Tab** shortcut when it has one.

### Plugin tabs

Plugins can open their own items in tabs and panes beside terminals; the Editor opens each document this way (see
[Editor](editor.md)). A plugin opens an item in a new tab, in a new pane beside the focused one (from a
[Split](#panes-and-splits) list) or in a new child window; a new tab fills the main window's empty workspace. A plugin's
item shows ● before its title while it has unsaved changes. While one is active:

- its own commands take their shortcuts (the Editor's Cmd+S, Ctrl+S on Linux and Windows, and so on), and **Find**,
  **Find Next**, **Find Previous**, **Copy**, **Paste**, **Increase Font Size**, **Decrease Font Size** and
  **Reset Font Size** act on it; **Close Pane** closes it, and **Close Tab** its whole tab;
- the header's search field shows its title and host, and the status bar shows its location and its own items.

A tab's right-click menu has **Rename Tab** (for a terminal), **Move Tab to New Window**, **Move Tab Into**, **Close
Tab**, then the active item's own commands.

## Panes and splits

Each tab holds one or more panes, and each pane one terminal, Editor file or plugin view.

- **Split Right** (Cmd+D; Ctrl+Shift+D) and **Split Down** (Cmd+Shift+D; Ctrl+Alt+Shift+D) halve the focused pane with
  a new terminal. The new pane takes the focus; beside a running local shell it starts in the same folder with the same
  shell profile, and beside anything else (an SSH pane, a shell that has exited, a plugin's view) where a new tab would
  start, with the configured profile. The **Split**
  submenu of the Tab menu and of a terminal's or an editor's right-click menu opens a terminal (**Terminal Right**,
  **Terminal Down**), an editor (**Editor Right**, **Editor Down**), a file you choose (**Open File Right…**, **Open
  File Down…**) or a plugin's split item in a new pane beside the focused one. A split of the same direction as the
  one around the pane adds a pane to it, so a row or column can hold any number of panes.
- The Editor's **Split File Right** and **Split File Down** show the active file in another pane beside its own
  ([Views of one file](editor.md#views-of-one-file)).
- **Move Pane to New Tab** (Cmd+Option+T; Ctrl+Alt+Shift+T) gives the focused pane a tab of its own, right after its
  tab, while its tab holds another pane.
- **Move Pane to Tab** (Tab menu, and a terminal's or an editor's right-click menu) lists the window's other tabs,
  numbered as **Select Tab** numbers them, and moves the focused pane to the right of the chosen tab's active pane: that
  tab shows, the pane keeps the focus, a maximised pane there is restored, and a tab left without panes closes. **Move
  Pane to Tab 1** to **9** do the same from the keyboard; they have no default shortcut.
- Hold Cmd+Option (Ctrl+Alt on Linux and Windows; on Windows the left Alt key) and drag inside a pane to move it: onto
  another pane of the tab any window shows, which it splits on the side nearest the cursor; over the middle of a tab's
  entry, which shows that tab for the drop; onto the outer quarter of a tab's entry (under the Enterprise look also its
  "+" or the room after the tabs), where it becomes a new tab; or anywhere over the main window's empty workspace. The
  moved pane takes the focus where it lands, and a pane never becomes a window. Because the keys start the drag, a
  Cmd+Option-click (Ctrl+Alt-click) reaches neither a link nor the program in a terminal. Let go of the keys before
  pressing Escape to cancel, as Cmd+Option+Esc is Force Quit on macOS and Ctrl+Alt+Esc kills a window on some Linux
  desktops (KDE, Xfce). A Linux desktop that moves windows with Ctrl+Alt-drag keeps that gesture for itself.
- Drag the line between two panes to resize them: it trades space between the two panes beside it and leaves each at
  least a tenth of the row or column.
- Click a pane to focus it, or use **Focus Pane Left**, **Right**, **Up** and **Down** (Cmd+Option+Arrow;
  Ctrl+Alt+Shift+Arrow), which pick the nearest pane in that direction.
- **Maximize Pane** (Cmd+Shift+Enter; Ctrl+Alt+Shift+Enter) shows only the focused pane, while the tab holds two or
  more; run it again (the palette calls it **Restore Pane** then) to bring the others back. Focus Pane maximises the
  pane it moves to instead; splitting, or closing the maximised pane, restores the others.
- **Close Pane** (Cmd+W; Ctrl+Shift+W) closes the focused pane, after asking when its item has unsaved work, and
  focuses the pane you used before it. Closing the last pane closes the tab.

While a tab holds two or more panes, every pane but the active one is dimmed by `terminal.dim_inactive_panes` (0.3 by
default), and a window in the background dims its terminals. **Increase**, **Decrease** and **Reset Font Size** (View ›
Font Size) change only the focused pane, one point at a time; Reset returns a terminal to `terminal.font.size`.

**Find** (Cmd+F; Ctrl+Shift+F) opens the focused pane's find bar; **Find Next** (Cmd+G; Ctrl+Shift+G) and **Find
Previous** (Cmd+Shift+G; Ctrl+Alt+Shift+G) move between its matches. See [Terminal](terminal.md#find).

## The toolbar

The toolbar sits at the trailing end of the header:

| Button | Action |
|---|---|
| New Tab, New Terminal Window | **New Tab**, **New Terminal Window** |
| **Split** ▾ | A menu with **Split Right** and **Split Down** |
| Maximize Pane | **Maximize Pane** |
| Plugin buttons | What plugins add, after their own separator. The Editor adds **New Editor Window**, which opens an untitled file in a new child window; Remote adds a **Sessions** menu of your saved hosts, your WSL distributions on Windows, and **Manage Sessions…**. |
| Find, Settings | **Find**, [Settings](#settings) |
| ⋮ (View Options) | The window menu: **Move Tab to New Window** |

A button's tooltip names its action and current shortcut, for example "Find (Ctrl+Shift+F)" on Linux and Windows. When
the header is too narrow, buttons move into **More actions** (⋯), the least important first; Find and Settings stay
longest, and the ⋮ never moves.

View › Show/Hide › Toolbar, or the palette's **Toolbar: Icons and Labels**, **Toolbar: Icons Only** and **Toolbar:
Hidden**, choose how it shows; the choice is saved as `window.toolbar`. The built-in buttons are always icons; **Icons
and Labels** also shows the labels of Split and of plugin buttons. **Hidden** hides the buttons, the ⋮ included, and
keeps the header with its search field. Settings › Window sets the same choice and the toolbar height.

## The status bar

From left to right:

- A dot showing whether the focused pane's session is running.
- The shell's name with ● when shell integration is active or ○ when it is not detected (hover for which); a plugin's
  session, such as an SSH pane, shows no shell name, because its folder starts with the host.
- The folder as breadcrumbs: a local path starts at `~` or the root, with folders separated by chevrons; when space is
  short the ancestors collapse into … and the current folder stays. A remote path starts with the host; while the remote
  shell has not said where it is, only the host shows, and its tooltip reads `<host>: directory not reported`. Hover
  for the full path.
- Plugins' items, on the left and the right. The bundled plugins show, for example, `N SSH sessions` (Remote; click it
  to toggle the Sessions tool window), running tunnels, transfer progress, `⇅ N servers` (File Server), the vault's
  state (`Vault: Locked`, `Vault: Unlocked`) and indexing progress (Project Index). Most show only while they have
  something to report.
- The memory indicator (`<used> of <max>M`), when `window.memory_indicator` is on (Settings › Window › Show memory
  indicator); clicking it runs garbage collection ([Memory](diagnostics.md#memory)).
- The terminal size, `<columns> × <rows>`, or `Starting terminal` before the shell is up.
- The configuration button:

| Text | Meaning |
|---|---|
| `Built-in defaults` | There is no `config.toml`; every setting has its default. |
| `Config loaded` | `config.toml` loaded without problems. |
| `Config warnings` | It loaded with warnings. |
| `Config error` | It has an error. After a syntax or type error, or when the file cannot be read, Jasper keeps the last good settings; after any other error the setting at fault uses its default (an invalid built-in shortcut, or two actions on one shortcut, puts every shortcut back to its default). |

With warnings or an error, ` (line N)` names the first problem's line. The button's tooltip is the file's path.
Clicking it shows the path and every problem, with a **Migrate Config…** button when the file uses old setting names.
See [Configuration](configuration.md).

While a plugin's item is active, the bar shows its location and its own items instead of the shell, folder and size.
View › Show/Hide › Status Bar (palette: **Show Status Bar** / **Hide Status Bar**; no default shortcut,
`toggle_status_bar`) shows or hides it and saves `window.status_bar`; Settings › Window also sets its height.

## Menus

Menu items show their current shortcuts. Opening a menu refreshes which items are available.

| Menu | Items |
|---|---|
| File | New Tab, New Terminal Window, Open File…, Close Pane, Close Tab, Close Window, Settings, Reload Config, Quit; then, each after a separator, Jasper's **Manage Plugins…** and the plugins' items: Editor's **Open in Editor…**, **New Editor Tab**, **Split Right with Editor**, **Open in Editor Pane…** and **New Editor Window**, Credential Vault's **Open Vault...**, **Change Vault Master Password...** and **Vault Plugin Grants...**, Project Index's **Index Folder…** |
| Edit | Copy, Paste, Find, Find Next, Find Previous, Clear Scrollback |
| View | Command Palette; View Modes › Zen; Maximize Pane; Show/Hide › Toolbar (Icons and Labels, Icons Only, Hidden), Status Bar, Rail; Font Size › Increase Font Size, Decrease Font Size, Reset Font Size; Tool Windows › one toggle per tool window |
| Tab | Split › Terminal Right, Terminal Down, then each split item's Right and Down (Editor Right, Editor Down, Open File Right…, Open File Down…); Move Pane to New Tab, Move Pane to Tab ›, Move Tab Into ›, Focus Pane Left, Focus Pane Right, Focus Pane Up, Focus Pane Down, Previous Prompt, Next Prompt; Rename Tab; Next Tab, Previous Tab, Select Tab 1 to Select Tab 9 |
| Session | Plugins' session items. Remote: Connect to SSH Host..., Toggle SSH hosts, Split with Same Host, New Host..., Import from ~/.ssh/config..., Toggle Tunnels, Start or Stop a Tunnel..., New Tunnel..., Forward a Port..., Stop All Tunnels |
| Transfer | Plugins' transfer items. Remote: Open SFTP here, Transfers; File Server: Share Folder…, New File Server… |
| Tools | Plugins' tool items |
| Plugins' own menus | Any menus plugins add, in the order they start |
| Help | Keyboard Shortcuts…; Change Memory Settings…, Edit Custom VM Options…; About Jasper |

Session, Transfer, Tools and plugins' own menus appear only while a plugin has put something in them.

- **Open File…** opens a file you choose in the [file chooser](#the-file-chooser), starting in the focused pane's
  folder, the same way a Cmd-click (Ctrl-click) on a file path in the terminal does; see [Terminal](terminal.md).
- **Settings** opens [Settings](#settings); **Reload Config** reads `config.toml` again now.
- **Keyboard Shortcuts…** opens Settings › Keymap. **Change Memory Settings…** opens Settings › System Settings at the
  maximum heap size. **Edit Custom VM Options…** opens `jasper.vmoptions` in your editor (in an installed Jasper;
  otherwise it opens the heap setting). See [Configuration](configuration.md).

On macOS, while the menus are in the screen menu bar, the application menu (Jasper) holds **About Jasper**,
**Settings…** and **Quit Jasper**, and Help ends at Edit Custom VM Options…. Help windows have a short File menu:
**Close Window** (Cmd+W; Ctrl+W) and, on Linux and Windows, **Quit**.

### Context menus

| Where | Items |
|---|---|
| A terminal pane | Copy, Paste, Find, Clear Terminal, Split › (as in the Tab menu), Move Pane to New Tab, Move Pane to Tab ›, Maximize Pane, Close Pane, then the window menu's Move Tab to New Window (and Move to Main Window in a child window); then plugins' items: Remote's **Forward a Port...** and **Open SFTP here**, File Server's **Share Path** › HTTP, HTTPS, FTP, FTPS, SFTP, TFTP |
| A tab's entry in the strip | Rename Tab (for a terminal), Move Tab to New Window, Move Tab Into ›, Close Tab, then the active item's own commands |
| A rail button, or a tool window's Options (⋯) button | Move to › Left Top, Left Bottom, Bottom Left, Right Top, Right Bottom, Bottom Right (the current slot checked); Hide |

## Tool windows and the dock

Tool windows are panels such as the Sessions list, the SFTP browser or the Notifications history. Jasper and plugins
supply them, and they dock in the main window only: a tool window's toggle run in a child window brings the main window
to the front and shows the tool window there, and never hides it. Under the default look they start here:

| Tool window | From | Starts in | Toggle |
|---|---|---|---|
| Sessions | Remote | Left Top | Toggle SSH hosts (Cmd+Option+1; Alt+1) |
| Tunnels | Remote | Left Top | Toggle Tunnels |
| File Servers | File Server | Left Top | Toggle File Servers |
| SFTP | Remote | Left Bottom | Toggle SFTP (Cmd+Option+2; Alt+2) |
| Notifications | Jasper | Right Top | Notifications: Show History |

Remote's file transfers show at the foot of the SFTP tool window; its **Transfers** action (Cmd+Option+3; Alt+3)
reveals them. Every toggle is also in View › Tool Windows and on the tool window's rail button. A plugin's tool window
that names no toggle of its own gets **Toggle** *title* (`<panel id>.toggle`).

### Slots

Tool windows dock in six slots. Each side, left and right, has a top slot (its upper half) and a bottom slot (its lower
half); the bottom area has a left half (**Bottom Left**) and a right half (**Bottom Right**). A slot alone on its side
fills it. Each slot shows one tool window at a time: showing another one in the same slot hides the first. Hiding a
tool window by any route returns the focus to the active pane of the shown tab. Under the
Enterprise look, a tool window opened from its stripe or its toggle in the hidden one's place takes the keyboard
instead.

A tool window is built the first time it shows in a window; a hidden one keeps its state. A tool window that fails to
build shows "This panel could not be loaded."

### Rails

The rail on each side has a button per tool window: the top slot's buttons at the top, the bottom slot's under a
separator, and the button for that side's half of the bottom area at the foot. A button is highlighted while its tool
window shows; click it to show or hide the window. A dot on a button marks unread news (the Notifications button's
tooltip gives the count; see [the Notifications panel](notifications.md#the-notifications-panel)). Right-click a button
for **Move to** and **Hide**.

A rail shows only while it holds a button. View › Show/Hide › Rail (palette: **Show Rail** / **Hide Rail**; no default
shortcut, `toggle_rail`) hides or shows both rails in the window, and Jasper remembers the choice in `ui-state.toml`.
`window.rail_width` (Settings › Window › Panel rail width) sets their width.

Each tool window has a header: its title, the plugin's buttons, **Options** (⋯) with **Move to** and **Hide**, and
**Hide** (the chevron).

### Moving and resizing

- **Drag** a rail button, or a tool window's header, a few pixels to move it. The rails open a gap where it would go
  and an outline shows where it would land. Release it on a rail to move it there, keeping it shown or hidden as it
  was, or over the workspace near its left, right or bottom edge to move it there and show it. Escape, a click with
  another button or switching windows cancels the drag. An empty, hidden rail still takes a drop at its window edge.
- **Move to** on the right-click or Options menu moves it straight to a named slot.
- **Resize** by dragging the gap between a tool window and the terminal, or between the two tool windows on a side.
  The change applies when you release. A side or the bottom area snaps to its default size (260 logical pixels wide,
  200 high, or equal halves) when you release within 8 pixels of it; sizes stay between 120 and 4000.

When the window is too small for everything, the bottom area gives way first, then the right side, then the left,
before the terminal column shrinks.

Jasper saves the layout, which tool windows show and the rails' visibility in `ui-state.toml` whenever it changes; the
next start uses the last saved layout.

## View modes

**Zen** (View › View Modes › Zen; palette **Enter Zen Mode** / **Exit Zen Mode**; no default shortcut,
`toggle_zen_mode`) hides the status bar, both rails and every tool window that was showing, which Jasper remembers.
The header, toolbar and tabs stay. A tool window you open while in Zen shows in its slot as usual, without the
rails. Leaving Zen hides those and shows the remembered ones again. View › Show/Hide › Status Bar and Rail, and their
shortcuts, are unavailable in Zen. Zen is the main window's: the main window opens in the Zen saved in
`ui-state.toml` and saves it each time you toggle it, and a child window has no Zen. The saved layout keeps the tool
windows that showed before Zen, so one opened in Zen is not saved as showing.

**Compact** (Settings › Appearance › View mode, `window.view_mode = "compact"`, after a restart) uses the system's
title bar on every platform and removes the header row, so there is no search field or toolbar; the command palette
opens centred over the window. **Normal** is the default.

## The file chooser

Jasper uses its own file chooser wherever it asks for a file or folder: **Open File…**, Browse… buttons in Settings,
Import Theme…, and plugins' pickers. It opens over the window that asked and remembers its size.

- **Places** on the left: **Terminal folder** (the focused pane's folder), your home folder, Desktop and Downloads
  when they exist; **Pinned** folders; **Recent** (the last ten folders you chose from); then each plugin source, such
  as Remote's SFTP hosts, which connect when you open them.
- Right-click a folder, a place or a breadcrumb for **Pin** or **Unpin**.
- The top row has Back, Forward, Up and Refresh, the location as breadcrumbs (click to type a path, or Cmd+L; Ctrl+L), a
  search field that filters the folder, **New folder** (when saving), **Show hidden items** (on by default; Cmd+Shift+.;
  Ctrl+Shift+.) and the **Preview pane** switch (off by default).
- The listing has Name, Modified and Size columns, folders first; click a column to sort by it, again to reverse.
- The preview shows the start of a text file (its first 8 KiB), `Binary file`, a folder's item count, or how many items
  are selected.
- The primary button reads **Open**, **Save** or **Choose**. Saving over an existing file asks
  "<name> already exists. Do you want to replace it?".

Keys: Enter presses the primary button, Escape cancels, Alt+Left and Alt+Right go back and forward, Alt+Up (or
Backspace in the listing) goes up. Pinned and recent folders, the preview and hidden-items choices are kept in
`ui-state.toml`.

## Settings

**Settings** (File menu, the toolbar, Cmd+, on macOS, Ctrl+Shift+, on Linux and Windows) opens one Settings dialog over
the window. On macOS it is also Jasper › Settings…. Other commands open it at a page: **Keyboard Shortcuts…** at Keymap,
**Manage Plugins…** at Plugins, **Themes…** at Appearance, **Window Settings…** at Window and **Change Memory
Settings…** at System Settings. Otherwise it returns to the page you last visited.

- The left side has **Search settings** and the page tree. Every word you type must match; the tree shows only the
  pages that match, each with its number of matches, and matching rows are highlighted. On Keymap, the search filters
  the shortcuts.
- The header has **Back** and **Forward** through the pages you visited, and the page's place in the tree.
- Edits are held until you press **Apply** (save and stay) or **OK** (save and close). **Cancel** or Escape closes
  without saving; Enter presses OK when no control takes it. Closing with unsaved edits asks "Discard changes?" with
  **Keep Editing** and **Discard**.
- A row says when its change takes effect: "Applies to new panes", "Applies to new windows" or "Takes effect after
  restart"; everything else applies at once. Reset returns a row to its default.
- **Help** (?) opens the matching section of [Configuration](configuration.md) on GitHub.
- When `config.toml` cannot be applied (a syntax or type error, or the file cannot be read), a banner says so with
  **Open config.toml**, and Apply and OK are unavailable until it is fixed. When it uses old setting names, the banner
  offers **Migrate Config…**.

Saving writes `config.toml` in Jasper's home, keeping your comments;
[Configuration](configuration.md#supported-settings) lists every key.
[Configuration](configuration.md#settings-dialog) lists every page and what it holds.

## The command palette

The command palette finds and runs everything: every menu command, every plugin action, and plugins' searches such as
saved SSH hosts, shell history, snippets or project files. Open it with Cmd+Shift+P (Ctrl+Shift+P), View › Command
Palette, or by clicking the header's search field; it drops down from the field. In Compact mode, and in a child
window, which has no header, it opens centred over the window. See [Command palette](command-palette.md).

## Notifications

Jasper reports results and problems as cards in the bottom-right corner of the main window instead of dialogs. The
Notifications tool window (bell button, Right Top) keeps this session's history
([the Notifications panel](notifications.md#the-notifications-panel)), and **Notifications: Show Errors** opens the
Errors tab in the main window. When Jasper is in the background, the operating system shows them too. See
[Notifications](notifications.md).

## Quitting, background residency and restarts

**Quit** (File menu, Cmd+Q; Ctrl+Alt+Shift+Q) first asks whatever would lose something: plugins' items with unsaved
work, such as Editor documents, and plugins with work running, such as File Server's servers
([File Server](file-server.md#quitting)). A refusal leaves everything open; otherwise Jasper closes every window and
stops, except on macOS while it runs in the background (below).

One Jasper runs per home. Launching it again, with its launcher or the
[`jasper` command](command-line.md#one-jasper-per-home), hands the request to the running one, which brings its main
window to the front, restoring its windows first when none is open, and opens the files and folders the launch
named (`Jasper notes.txt src`): each folder as a terminal tab in the main window, and each file in the
[default editor](editor.md#default-editor), which is the Editor, in the main window, unless you chose another. On macOS
a file or folder you open with Jasper in Finder (Open With), or drop on its Dock icon, opens the same way. A running
Jasper that does not answer within 10 seconds is reported with its process id, and the launch starts no second one.

With **Run in background** on (Settings › System Settings, `system.run_in_background`; on by default on macOS and
Windows, off on Linux; applies after a restart), Jasper keeps running when you close the main window, and with it every
window, with its plugins but no shells, so the next window opens quickly. In an installed Jasper on macOS and Windows it
also starts at login, with no window. The menu bar item or notification-area icon appears once start-up has settled:
when the first window has painted its shell's first output, 2 seconds after its first paint, or, at login, once Jasper
has prepared its first window.

- **macOS:** in the installed app, Quit (Cmd+Q, Jasper › Quit Jasper, the Dock's Quit) closes every window and leaves
  Jasper running. Plugins' items are asked first, as when you close them, but plugins are not: their
  work, such as a File Server share, carries on until **Quit Jasper Completely**. The Dock icon, and Jasper's place in
  Cmd+Tab, show only while a Jasper window is open; clicking the Dock icon, Spotlight or `open -a Jasper` opens a window
  in the running Jasper. The menu bar item has **New Terminal Window**, **Settings…** and **Quit Jasper Completely**.
  Logging out, restarting or shutting down quits for real, and so does Cmd+Q before start-up has settled.
- **Windows:** the notification-area icon (Windows may keep it among the hidden icons until you pin it) has **Open
  Jasper** and **Quit Jasper**. Double-clicking it opens or raises a window, or, while a notification's balloon waits
  unread, opens that notification. Quit in the File menu quits.
- **Linux:** there is no icon; launch Jasper again to get a window. Quit quits.

With Run in background off, closing the main window quits Jasper. See
[Configuration](configuration.md#background-residency).

Finder does not replace a running app, so choose **Quit Jasper Completely** before installing a new version from the
disk image. When the app is replaced another way while Jasper runs, the next Dock click or launch with no window open
starts the new build in its place, opening what the launch named; with windows open it brings the main window to the
front, and a launch of the new build says that Jasper was updated, with a Restart action ([Command line](command-line.md)).

Some changes need a restart: rows marked "Takes effect after restart", plugin changes and custom VM options. The
**Restart Now** buttons on the Plugins page and under System Settings › Java Virtual Machine quit Jasper and start it
again the same way it was started; the new Jasper waits for the old one to exit ([Command line](command-line.md)).

## Safe mode

`jasper --safe-mode` starts Jasper without the plugins you installed and without any plugin that provides a look, the
bundled Enterprise look included. The Plugins page then says "Safe mode: installed plugins are not loaded." and offers
**Restart Normally**. See [Plugins](plugins.md) and [Command line](command-line.md).

## About and Licenses

**About Jasper** (the application menu on macOS, Help › About Jasper elsewhere, or the palette) shows the version, a
**Development build** label unless this is a release, the branch and commit, the commit date, the licence and the
copyright. **Copy** copies the version, commit, operating system and Java runtime for a bug report; **Licenses…**
opens the Licenses window, which lists Jasper and every third-party component with its licence, homepage, notice and
licence text. Escape closes either window; **Done** closes Licenses.

## Where Jasper keeps its files

Jasper keeps its files in **Jasper home**, the folder that holds `config.toml` (`~/.config/jasper` on macOS,
`$XDG_CONFIG_HOME/jasper` or `~/.config/jasper` on Linux, `%APPDATA%\jasper` on Windows);
[Files in Jasper's home](configuration.md#files-in-jaspers-home) lists what it holds and
[ui-state.toml](configuration.md#ui-statetoml) what the layout file records.

The `JASPER_HOME` environment variable, or the `jasper.home` system property, moves it; a Jasper in another home runs
beside the installed app's and is never resident (see [Command line](command-line.md)).

## Under the Enterprise look

The bundled Enterprise look changes the chrome: IntelliJ Classic's title bar on macOS and Windows (the system's on
Linux), with the menus in the screen menu bar on macOS, in the title bar on Windows and in a row on Linux, and a Window
menu that gathers the Tab and tool window items; a toolbar row of IntelliJ's classic icons (Icons Only by
default) ending in a Search Everywhere button and Settings…; tabs that always show; a status bar of ruled cells
starting with **▶ Run…**; labelled stripes, each label led by its tool window's icon, with a bottom strip; tool windows
that take the keyboard when you click in them, open them from their stripe or toggle them; the Run window instead of
the palette card; and different starting slots for the tool windows. See [Enterprise](enterprise.md).
