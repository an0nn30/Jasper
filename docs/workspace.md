# The workspace

A Jasper window is the workspace: terminal tabs and plugin tabs in the middle, tool windows docked around them, a header
with the command palette's search field and the toolbar, the menus and a status bar. This guide describes that frame
and the dialogs that belong to it. What runs inside a terminal pane is in [Terminal](terminal.md); features such as SSH,
the Credential Vault or the Editor come from bundled plugins and have their own guides, listed in [Plugins](plugins.md).
Every shortcut named here is a default; [Keyboard shortcuts](keyboard-shortcuts.md) lists them all and shows how to
change them.

## The window at a glance

From top to bottom:

- **Header.** The search field in the middle shows what the focused pane is doing (`user@~/project : make` for a local
  shell, `host:path` for a remote one, or the title a program set) with the command palette's shortcut; clicking it
  opens the [command palette](#the-command-palette). The [toolbar](#the-toolbar) sits at the trailing end.
- **Menus.** On macOS in the screen menu bar; on Linux and Windows in Jasper's title bar (see
  [Title bar and menus](#title-bar-and-menus)).
- **Tool window area.** [Tool windows](#tool-windows-and-the-dock) dock on the left, on the right and in a bottom area
  that spans the window's width, with a rail of buttons on each side. Between them is the terminal column: the
  [tab strip](#tabs) (shown once the window has two tabs) above the selected tab.
- **[Status bar](#the-status-bar).** The focused pane's shell and folder, plugin items, the terminal size and the
  configuration state.

Notification cards appear in the bottom-right corner of the tool window area, above the status bar.

## Windows

**New Window** (File menu, the toolbar's New Window button, Cmd+N on macOS, Ctrl+Shift+N on Linux and Windows) opens a
window with one tab. Its shell starts in the focused pane's folder when Jasper knows it, otherwise in your home folder.

Closing a window with its title-bar close button closes every tab in it at once; Jasper does not ask about running
programs. Only a plugin tab with unsaved work, such as an Editor document, can ask first. Closing the last tab of a
window closes the window.

### Size and position

The first window is sized for `window.columns` × `window.lines` cells (100 × 35 by default; Settings › Window › New
Windows › Initial size) at the terminal font size. When a window closes, Jasper remembers its size, position and
maximised state in `ui-state.toml`, and every new window opens there: exactly, when that place is still on a display,
otherwise fitted onto the display it overlapped most. A minimised window is never restored minimised. With several
windows open, the last one closed is remembered.

### Title bar and menus

| | macOS | Linux and Windows |
|---|---|---|
| Title bar | The header shares the title bar with the window controls. | Jasper draws the title bar: the menus, then the header. |
| Menus | The screen menu bar. | In the title bar. As the window narrows, toolbar labels go first, then toolbar buttons move into **More actions** (⋮), then the menus after File fold into **More menus** (…), then all of them into one **Application menu** (☰); the search field shrinks last. F10 opens the first menu. |

The title bar and header row are as tall as `window.toolbar_height` (30 logical pixels by default). On Linux and
Windows, `window.native_decorations = true` (Settings › Window › Use the system title bar) uses the system's title bar
instead, with the menus as a row in the window and the header below them; it applies after a restart. The Compact
[view mode](#view-modes) also uses the system title bar, on every platform.

In Jasper's own title bar, blank parts of the header and toolbar drag the window.

## Tabs

**New Tab** (Cmd+T; Ctrl+Shift+T) opens a terminal tab whose shell starts in the focused pane's folder. The tab strip
appears at the top of the terminal column once a window has two or more tabs; tabs share its width equally and centre
their titles.

| To | Do this |
|---|---|
| Select a tab | Click it, or use **Select Tab 1**–**9**, **Next Tab** or **Previous Tab** (Tab menu). Next and Previous wrap around. |
| Close a tab | Click the × that appears on hover (when the tab is wide enough to show it), middle-click the tab, choose **Close Tab** on its right-click menu or in the File menu, or press Cmd+Option+W (Ctrl+Alt+Shift+W). |
| Reorder tabs | Drag a tab and release it over another tab. |
| Rename a tab | **Rename Tab** (Tab menu, Cmd+I; Ctrl+Shift+I) asks "Tab name (leave blank for automatic):". |

Closing a terminal tab stops its shells immediately. A tab is named after its focused pane: the title a program set,
otherwise the running command, the foreground program or the shell's name ([Titles](terminal.md#titles)). A name you
give it stays until you rename it with a blank name. While `ui.animations.enabled` is on, tabs slide open in 475 ms and
closed in 425 ms, unless the look has no motion ([Animations](configuration.md#animations)).
A terminal tab's tooltip shows its title (a plugin tab's shows its location), followed by its **Select Tab** shortcut
when it has one.

### Plugin tabs

Plugins can open their own tabs beside terminal tabs; the Editor opens each document this way (see
[Editor](editor.md)). A plugin tab shows ● before its title while it has unsaved changes. While one is selected:

- its own commands take their shortcuts (the Editor's Cmd+S and Cmd+W, Ctrl+S and Ctrl+W on Linux and Windows, and so
  on), and **Find**, **Find Next**, **Find Previous**, **Copy**, **Paste**, **Close Tab** and **Close Pane** act on it;
- the header's search field shows its title and host, and the status bar shows its location and its own items.

Its right-click menu has **Open in Separate Window**, the tab's own commands and **Close Tab**. Open in Separate Window
moves the tab into a new window that starts without a terminal tab; there the menu offers **Move to Main Window**
instead. A separate window closes when its last tab leaves.

## Panes and splits

Each terminal tab holds one or more panes.

- **Split Right** (Cmd+D; Ctrl+Shift+D) and **Split Down** (Cmd+Shift+D; Ctrl+Alt+Shift+D) halve the focused pane. The
  new pane takes the focus and starts in the same folder with the same shell profile. A pane whose session has ended
  cannot be split.
- Drag the line between two panes to resize them; each side keeps between 10 % and 90 %.
- Click a pane to focus it, or use **Focus Pane Left**, **Right**, **Up** and **Down** (Cmd+Option+Arrow;
  Ctrl+Alt+Shift+Arrow), which pick the nearest pane in that direction.
- **Zoom Pane** (Cmd+Shift+Enter; Ctrl+Alt+Shift+Enter) shows only the focused pane; run it again (the palette calls it
  **Restore Pane** then) to bring the others back. Focus Pane moves the zoom to the neighbouring pane; splitting or
  closing a pane ends the zoom.
- **Close Pane** (Cmd+W; Ctrl+Shift+W) closes the focused pane and focuses its neighbour. Closing the last pane closes
  the tab.

Panes other than the focused one in a tab, and every pane while its window is not the active window, are dimmed by `terminal.dim_inactive_panes` (0.3 by default). **Increase**,
**Decrease** and **Reset Font Size** (View › Font Size) change only the focused pane, one point at a time; Reset
returns it to `terminal.font.size`.

**Find** (Cmd+F; Ctrl+Shift+F) opens the focused pane's find bar; **Find Next** (Cmd+G; Ctrl+Shift+G) and **Find
Previous** (Cmd+Shift+G; Ctrl+Alt+Shift+G) move between its matches. See [Terminal](terminal.md#find).

## The toolbar

The toolbar sits at the trailing end of the header:

| Button | Action |
|---|---|
| New Tab, New Window | **New Tab**, **New Window** |
| **Split** ▾ | A menu with **Split Right** and **Split Down** |
| Zoom Pane | **Zoom Pane** |
| Plugin buttons | What plugins add, after their own separator. Remote adds a **Sessions** menu of your saved hosts, your WSL distributions on Windows, and **Manage Sessions...**. |
| Find, Settings | **Find**, [Settings](#settings) |

A button's tooltip names its action and current shortcut, for example "Find (Ctrl+Shift+F)" on Linux and Windows. When
the header is too narrow, buttons move into **More actions** (⋮), the least important first; Find and Settings stay
longest.

View › Show/Hide › Toolbar, or the palette's **Toolbar: Icons and Labels**, **Toolbar: Icons Only** and **Toolbar:
Hidden**, choose how it shows; the choice is saved as `window.toolbar` and every window follows it. The built-in
buttons are always icons; **Icons and Labels** also shows the labels of Split and of plugin buttons. **Hidden** hides
the buttons and keeps the header with its search field. Settings › Window sets the same choice and the toolbar height.

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

While a plugin tab is selected, the bar shows that tab's location and its own items instead of the shell, folder and
size. View › Show/Hide › Status Bar (palette: **Show Status Bar** / **Hide Status Bar**) shows or hides it in every
window and saves `window.status_bar`; Settings › Window also sets its height.

## Menus

Menu items show their current shortcuts. Opening a menu refreshes which items are available.

| Menu | Items |
|---|---|
| File | New Tab, New Window, Open File…, Close Tab, Close Pane, Settings, Reload Config, Quit; then, each after a separator, Jasper's **Manage Plugins…** and the plugins' items: Editor's **Open in Editor…** and **New Editor File**, Credential Vault's **Open Vault...**, **Change Vault Master Password...** and **Vault Plugin Grants...**, Project Index's **Index Folder…** |
| Edit | Copy, Paste, Find, Find Next, Find Previous, Clear Scrollback |
| View | Command Palette; View Modes › Zen; Zoom Pane; Show/Hide › Toolbar (Icons and Labels, Icons Only, Hidden), Status Bar, Rail; Font Size › Increase Font Size, Decrease Font Size, Reset Font Size; Tool Windows › one toggle per tool window |
| Pane | Split Right, Split Down, Focus Pane Left, Focus Pane Right, Focus Pane Up, Focus Pane Down, Previous Prompt, Next Prompt |
| Tab | Next Tab, Previous Tab, Rename Tab, Select Tab 1 to Select Tab 9 |
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
**Settings…** and **Quit Jasper**, and Help ends at Edit Custom VM Options…. Plugin and help windows have a short File
menu: **New Window**, **Close Window** (Cmd+W; Ctrl+W) and, on Linux and Windows, **Quit**.

### Context menus

| Where | Items |
|---|---|
| A terminal pane | Copy, Paste, Find, Clear Terminal, Split Right, Split Down, Zoom Pane, Close Pane; then plugins' items: Remote's **Forward a Port...** and **Open SFTP here**, File Server's **Share Path** › HTTP, HTTPS, FTP, FTPS, SFTP, TFTP |
| A terminal tab | Close Tab |
| A plugin tab | Open in Separate Window (or Move to Main Window), the tab's own commands, Close Tab |
| A rail button, or a tool window's Options (⋯) button | Move to › Left Top, Left Bottom, Bottom Left, Right Top, Right Bottom, Bottom Right (the current slot checked); Hide |

## Tool windows and the dock

Tool windows are panels such as the Sessions list, the SFTP browser or the Notifications history. Jasper and plugins
supply them; under the default look they start here:

| Tool window | From | Starts in | Toggle |
|---|---|---|---|
| Sessions | Remote | Left Top | Toggle SSH hosts (Cmd+Option+1; Alt+1) |
| Tunnels | Remote | Left Top | Toggle Tunnels |
| File Servers | File Server | Left Top | Toggle File Servers |
| SFTP | Remote | Left Bottom | Toggle SFTP (Cmd+Option+2; Alt+2) |
| Notifications | Jasper | Right Top | Notifications: Show History |

Remote's file transfers show at the foot of the SFTP tool window; its **Transfers** action (Cmd+Option+3; Alt+3) reveals them.
Every toggle is also in View › Tool Windows and on the tool window's rail button. A plugin's tool window that names
no toggle of its own gets **Toggle** *title* (`<panel id>.toggle`).

### Slots

Tool windows dock in six slots. Each side, left and right, has a top slot (its upper half) and a bottom slot (its lower
half); the bottom area has a left half (**Bottom Left**) and a right half (**Bottom Right**). A slot alone on its side
fills it. Each slot shows one tool window at a time: showing another one in the same slot hides the first. Hiding a
tool window by any route returns the focus to the selected tab: its focused pane, or the plugin tab. Under the
Enterprise look, a tool window opened from its stripe in the hidden one's place takes the keyboard instead.

A tool window is built the first time it shows in a window; a hidden one keeps its state. A tool window that fails to
build shows "This panel could not be loaded."

### Rails

The rail on each side has a button per tool window: the top slot's buttons at the top, the bottom slot's under a
separator, and the button for that side's half of the bottom area at the foot. A button is highlighted while its tool
window shows; click it to show or hide the window. A dot on a button marks unread news (the Notifications button's
tooltip gives the count; see [the Notifications panel](notifications.md#the-notifications-panel)). Right-click a button
for **Move to** and **Hide**.

A rail shows only while it holds a button. View › Show/Hide › Rail (palette: **Show Rail** / **Hide Rail**) hides or
shows both rails in the window, and Jasper remembers the choice in `ui-state.toml`. `window.rail_width` (Settings ›
Window › Panel rail width) sets their width.

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

Jasper saves the layout, which tool windows show and the rails' visibility in `ui-state.toml` whenever it changes; a
new window, and the next start, use the last saved layout.

## View modes

**Zen** (View › View Modes › Zen; palette **Enter Zen Mode** / **Exit Zen Mode**; no default shortcut,
`toggle_zen_mode`) hides the status bar, both rails and every tool window that was showing, which Jasper remembers.
The header, toolbar and tabs stay. A tool window you open while in Zen shows in its slot as usual, without the
rails. Leaving Zen hides those and shows the remembered ones again. View › Show/Hide › Status Bar and Rail are
unavailable in Zen. Zen applies to the window you toggle it in; Jasper saves the last choice in `ui-state.toml`, and new
windows and the next start begin in it. The saved layout keeps the tool windows that showed before Zen, so one opened in
Zen is not saved as showing.

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
Palette, or by clicking the header's search field; it drops down from the field. In Compact mode it opens centred over
the window. See [Command palette](command-palette.md).

## Notifications

Jasper reports results and problems as cards in the bottom-right corner of the window instead of dialogs. The
Notifications tool window (bell button, Right Top) keeps this session's history
([the Notifications panel](notifications.md#the-notifications-panel)), and **Notifications: Show Errors** opens the
Errors window. When Jasper is in the background, the operating system shows them too. See
[Notifications](notifications.md).

## Quitting, background residency and restarts

**Quit** (File menu, Cmd+Q; Ctrl+Alt+Shift+Q) first asks whatever would lose something: plugin tabs with unsaved work,
such as Editor documents, plugin windows, and plugins with work running, such as File Server's servers
([File Server](file-server.md#quitting)). A refusal leaves everything open; otherwise Jasper closes every window and
stops, except on macOS while it runs in the background (below).

With **Run in background** on (Settings › System Settings, `system.run_in_background`; on by default on macOS and
Windows, off on Linux; applies after a restart), Jasper keeps running when you close its last window, with its plugins
but no shells, so the next window opens quickly. Launching Jasper again opens a window in the running one, or brings its
last active window to the front. In an installed Jasper on macOS and Windows it also starts at login, with no window.
The menu bar item or notification-area icon appears once start-up has settled: when the first window has painted its
shell's first output, 2 seconds after its first paint, or, at login, once Jasper has prepared its first window.

- **macOS:** in the installed app, Quit (Cmd+Q, Jasper › Quit Jasper, the Dock's Quit) closes every window and leaves
  Jasper running. Plugin tabs and plugin windows are asked first, as when you close them, but plugins are not: their
  work, such as a File Server share, carries on until **Quit Jasper Completely**. The Dock icon, and Jasper's place in
  Cmd+Tab, show only while a Jasper window is open; clicking the Dock icon, Spotlight or `open -a Jasper` opens a window
  in the running Jasper. The menu bar item has **New Window**, **Settings…** and **Quit Jasper Completely**. Logging
  out, restarting or shutting down quits for real, and so does Cmd+Q before start-up has settled.
- **Windows:** the notification-area icon (Windows may keep it among the hidden icons until you pin it) has **Open
  Jasper** and **Quit Jasper**. Double-clicking it opens or raises a window, or, while a notification's balloon waits
  unread, opens that notification. Quit in the File menu quits.
- **Linux:** there is no icon; launch Jasper again to get a window. Quit quits.

With Run in background off, closing the last workspace window quits Jasper, unless a plugin window is still open. See
[Configuration](configuration.md#background-residency).

Finder does not replace a running app, so choose **Quit Jasper Completely** before installing a new version from the
disk image. When the app is replaced another way while Jasper runs, the next Dock click with no window open starts the
new build in its place; with windows open it raises them. Launching the new build ends the old one's background role:
it quits at once when it has no window open, otherwise once its windows have closed
([Command line](command-line.md#standalone-launches)).

Some changes need a restart: rows marked "Takes effect after restart", plugin changes and custom VM options. The
**Restart Now** buttons on the Plugins page and under System Settings › Java Virtual Machine quit Jasper and start it
again the same way it was started ([Command line](command-line.md#standalone-launches)).

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

The `JASPER_HOME` environment variable, or the `jasper.home` system property, moves it; a Jasper started that way runs
on its own and never hands over to, or becomes, a background Jasper (see [Command line](command-line.md)).

## Under the Enterprise look

The bundled Enterprise look changes the chrome: a system title bar with the menus in the window and a Window menu
that gathers the Pane, Tab and tool window items, a toolbar row of IntelliJ's classic icons (Icons Only by default),
tabs that always show, a status bar of ruled cells starting with **▶ Run…**, labelled stripes with a bottom strip, tool
windows that take the keyboard when you click in them or open them from their stripe, the Run window instead of the
palette card, and different starting slots for the tool windows. See [Enterprise](enterprise.md).
