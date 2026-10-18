# Jasper single instance, windows and workspace tabs

Status: **Approved on 2026-10-16** (the user: "Spec approved. Write the plans once Enterprise 6-11 has merged.").
Amended 2026-10-17: section 5.4 no longer adds `LSMultipleInstancesProhibited`, which limits an app to one user
session rather than one instance (plan 1's spec issue). Amended 2026-10-18: closing a tab keeps its group maximized, as
VS Code does (sections 7.3 and 15, plan 2 Ruling 5). Branch: `claude/workspace-tabs`, from `main` at `2bc30401c`.
Planned **after the Enterprise presentation series** (its plans 6–11) has merged: the implementation plans are written
then, against the code as it stands, and every line reference in section 2 is re-verified at that point.

## 1. Intent and decisions

The user asked (2026-10-16) for two changes to Jasper's window and tab architecture:

1. **A single-instance window model.** Exactly one main instance owns all global chrome and manages every other window.
   Child windows are minimal windows for terminals, editors, tools and plugin views: no toolbars or chrome, and not
   instances of the app. "New Window" is replaced by type-specific commands. Duplicate instances must be structurally
   impossible: a single-instance lock, with every second launch (Dock click, CLI, file-open event) routed to the running
   instance, which opens the right child window or focuses itself. The main instance alone owns child-window lifecycle,
   state and plugin window registration.
2. **A VSCode-style workspace.** Everything is a tab: terminal, editor, tool view or plugin view. A tab fills the
   workspace or splits it with other tabs; splits nest to any depth and are always splits of the workspace, never of a
   tab. Cmd+D and Cmd+Shift+D create a new tab that splits the workspace (vertical and horizontal, as today). Any tab can
   be dragged out into its own child window, and dragged back into a workspace.

Deliverables the user named: a serializable layout model that persists across restarts; tab content decoupled from its
container so a terminal's PTY session and an editor's state survive moves between workspaces and windows; the edge
cases (closing the last tab in a split, closing the main window while child windows are open, restoring child windows
on relaunch); obsolete code removed rather than left dormant (per-terminal splits, New Window paths); tests for the
layout model and for single-instance enforcement.

The user's decisions (2026-10-16):

1. **The dock stays.** Tool windows (Sessions, Tunnels, SFTP, History, Notifications, File Servers) stay docked in the
   main window's chrome, as in IntelliJ and VS Code. Tool and plugin views that are tabs are content tabs. Child windows
   have no dock.
2. **Plugin windows become tabs.** `Windows.create` and `PluginWindow` go; the Credential Vault manager and the sample
   plugin's About become content tabs. A plugin that wants its view in its own window opens the tab in a new child
   window.
3. **Closing the main window closes its child windows too**, after the usual close guards, and saves the whole layout;
   residency then decides, as today, whether Jasper stays in the background or quits. Reopening restores the layout,
   child windows included.
4. **Tab strips are always shown**, one per group, even for a single tab filling the workspace (VS Code).
5. **Relaunch restores the layout with fresh content:** windows, splits, groups and tab order; terminal tabs start fresh
   shells in their last local directory with their profile and renamed title; editors reopen their files; plugin tabs
   come back if their plugin can restore them, otherwise as a placeholder. No scrollback.
6. **One instance per Jasper home, always**, whatever the residency setting. A second launch in the same home hands off
   and exits; start-up flags (`--safe-mode`, `--config`, `--plugin-dir`) on a second launch are ignored with a notice to
   quit first. `--standalone` is removed. Restart waits for the old process instead of going standalone.
7. **Sequencing:** all of the Enterprise presentation series merges first; this series is planned on top of it.
8. **Approach:** a VS Code-style grid (n-ary splits with alternating orientation, tab-group leaves) over an app-wide tab
   registry, with tab drags on plain mouse events (section 3).
9. **When the main window's last tab closes, the empty workspace stays** (VS Code, IntelliJ), showing an empty state;
   the window closes only through Close Window or its title bar.
10. Design sections 1–4 as presented in the session were approved as written; sections 5–15 below record them.

## 2. Verified facts this design rests on

Established on 2026-10-16 at `main` `2bc30401c`. Enterprise plans 6–11 will move many of these lines; each plan
re-verifies the facts it relies on.

### 2.1 Windows

- Every Jasper window is a `TerminalWindow` (`jasper-app/.../workspace/TerminalWindow.java:25`), a `JFrame` around a
  `WindowContent` with all of the chrome: header and toolbar, menus, the tool window dock and stripes, the status bar,
  the notification corner layer. There can be any number of them; `JasperApplication` keeps them in
  `Set<TerminalWindow> windows` (`application/JasperApplication.java:85`).
- `JasperApplication.composeWindow` (`:396-460`) is the only `new TerminalWindow` (`:409`). It is reached from
  `newWindow()` (`:364`), `newWindow(Path)` (`:370`, null makes a *detached* window with no tab) and `openWindow`
  (`:378`). Callers: the `new_window` action (cmd+n / ctrl+shift+n, `commands/ActionId.java:13`) through
  `WindowContent.requestNewWindow` (`workspace/WindowContent.java:669`), File › New Window and the toolbar button
  (`workspace/WindowChrome.java`), the palette (`workspace/WindowCommands.java`), the auxiliary frames' File menu
  (`windows/NativeShells.java:494-515`), the macOS menu bar item and Windows tray (`platform/ResidentTray.java:60`),
  `openOrRaise` for the handoff, `jasper` with no arguments and the macOS reopen event (`JasperApplication.java:735-746`),
  notification clicks (`raiseFor`, `:1479-1488`), the content registry's factory (`:379-383`, `:900-904`) and the
  benchmark (`benchmark/BenchmarkRun.java`).
- A "separate window" for content is a full `TerminalWindow` with `detached = true` and no shell tab
  (`WindowContent.java:212-219`, `:278-279`).
- Auxiliary frames (Settings, About, Licenses, Errors, plugin windows and dialogs) are `AuxiliarySurface`s built by
  `NativeShells` (`windows/NativeShells.java:86-92`, `:141-171`, `:207-277`) and tracked by `AuxiliaryWindows`
  (`windows/AuxiliaryWindows.java:12`).

### 2.2 Tabs and splits

- Each window has one `TerminalDeck` (`workspace/TerminalDeck.java:8`), a `JTabbedPane` whose tab area is hidden, drawn
  by `WindowTabs` (`workspace/WindowTabs.java:22`), which hides itself while the window holds one tab (`:93`).
- A tab is either a `TerminalTab` (`workspace/TerminalTab.java:14`), which owns a `SplitTree`
  (`workspace/SplitTree.java:9`, binary `Branch(id, axis, ratio, first, second)` with `Leaf(paneId)`) of `TerminalPane`s
  drawn as nested `JSplitPane`s (`TerminalTab.java:194-256`), or a plugin content tab from `ContentRegistry`
  (`contributions/ContentRegistry.java:12`) mounted into the same deck (`WindowContent.mountContent`, `:798-816`).
- Splits exist only inside terminal tabs: `split_right` (cmd+d), `split_down` (cmd+shift+d), `close_pane` (cmd+w),
  `zoom_pane`, `focus_pane_*` act on `currentTab()` (`workspace/WorkspaceActions.java:45-60`). `close_tab` is alt+cmd+w.
- Terminal tabs cannot leave their window; they reorder within it (`WindowContent.reorderTab`, `:1021-1032`). Content
  tabs move between "main" and "separate" (`ContentRegistry.move`, `:97-129`; tab menu "Open in Separate Window" /
  "Move to Main Window", `WindowContent.java:836-838`).

### 2.3 Sessions survive reparenting

- `TerminalView.removeNotify` (`jasper-terminal/.../view/TerminalView.java:204-214`) stops presentation work and
  unregisters its listener but leaves the session open; `addNotify` (`:191-201`) attaches a fresh listener generation
  and reconciles rows. `TerminalTab` already reparents panes without replacing sessions (its class comment, `:13`).
- What ties a pane to its window is wiring `WindowContent` installs: `configurePane` (`:976-1003`: shortcut handler,
  context menu, file links, first-terminal timing) and `connectActivity` (`:937-972`: activity and registry events whose
  "go to" callbacks capture the tab and window).

### 2.4 Persistence

- Nothing about tabs, splits, sessions or extra windows is saved. `ui-state.toml` (`persistence/UiState.java`) holds the
  dock (`[dock]`, `[panels."<id>"]`), rail and Zen, auxiliary window bounds (`[windows."<id>"]`), one `[main_window]`
  (`:275-280`), the palette position and chooser preferences. Every window writes into the one shared instance, so the
  last window closed wins. `docs/STATUS.md` records that sessions and documents are not resurrected.
- tomlj 1.1.1 is on the app's class path (`jasper-app/build.gradle.kts:14`); `TomlStateFile` does bounded reads and
  atomic writes (`persistence/TomlStateFile.java:20-46`).

### 2.5 Single instance

- Only a resident process binds the handoff endpoint `<home>/daemon/socket` (`residency/HandoffSocket.java:74-111`,
  `bootstrap/ApplicationBootstrap.java:128-133`, `:362-364`); `daemon/lock` is held only while binding and closing
  (`HandoffSocket.java:85-87`, `:197-220`). Liveness is a connect probe (`:270-276`).
- A second full instance starts when residency is off (the Linux default, `config/CoreSettings.java:116`), for any
  standalone launch (`--config`, `--safe-mode`, `--plugin-dir`, `--standalone`, a home override including dev runs:
  `bootstrap/AppArguments.java:43-45`, `jasper-app/build.gradle.kts:79-80`), when two cold starts race (the endpoint binds
  after plugin start, `ApplicationBootstrap.java:120-133`), after a STALE handoff (the old process releases the endpoint
  and keeps its windows, `JasperApplication.java:715-718`), with `open -n`, and for `jasper --option` (the CLI spawns the
  launcher directly, `packaging/cli/src/jasper_cli.c:467-500`).
- The handoff request carries no arguments or working directory by design (`residency/LaunchRequest.java:11-14`); its
  kinds are OPEN and RETIRE (`:27-32`).
- The command socket `daemon/commands` (`commandline/CommandListener.java:87-122`) serves the `jasper` CLI; a private
  launch serves `commands-<pid>` (`ApplicationBootstrap.java:370-372`, `:395-401`).
- macOS open-file AppleEvents are not handled: no `setOpenFileHandler` anywhere, and the plist declares no document
  types (`gradle/packaging.gradle:281-307`). The reopen event (Dock click, Finder, `open -a`) is handled
  (`JasperApplication.java:311-317`, `:634-643`).

### 2.6 The SDK

- `Terminals`, `WindowHandle`, `TabHandle` and `PaneHandle` (`jasper-sdk/.../terminal/`) model window → tab → panes:
  `TabHandle.panes()` and `activePane()` (`TabHandle.java:28-35`), `Terminals.split(pane, Direction, request)`.
  `WindowHandle.tabs()` lists terminal tabs only (`WindowTerminals.java:29` reads `WindowContent.terminalTabs()`, which
  filters with `instanceof TerminalTab`, `:757-761`), and `activeTab()` is empty while a content tab is selected.
- `ContentTab.move(boolean separate)` and `isSeparate()` are a binary main/separate model (`ui/ContentTab.java:24-27`).
- `Windows.create(WindowSpec)` returns a `PluginWindow` (`ui/Windows.java:61`); its users are the Vault manager
  (`plugins/vault/.../ui/VaultManagerWindow.java:59`) and the sample plugin's About (`plugins/sample/.../SamplePlugin.java:127`).
  `ShortcutContext.Kind.WINDOW` names such windows (`ui/ShortcutContext.java:32`).
- Panels are built once per window (`ui/Panels.java:6`, `ui/PanelFactory.java:9`); plugins key per-window panel state by
  window id (`plugins/remote/.../ui/sftp/SftpUi.java:94`).
- Remote is the only plugin that splits (`RemotePlugin.java:365`, `:385`); remote, indexer, history, snippets,
  fileserver, editor and sample read tab and pane structure. The editor is the only content-tab user
  (`plugins/editor/.../ui/EditorWindow.java:762`).

### 2.7 The industry standard

- **VS Code:** the editor area is a grid (`src/vs/base/browser/ui/grid/gridview.ts`): branch nodes with any number of
  sized children, orientation alternating by depth, editor groups as leaves, each with its own tab strip; the grid
  serializes as a nested tree. Auxiliary windows ("Move Editor into New Window") hold an editor grid and no activity
  bar, side bar or panel; closing the main window closes them. A second launch connects to the running instance's IPC
  socket, passes its arguments and exits; a socket nobody answers is deleted and the launch becomes the main process.
  A terminal moved between the panel and the editor area keeps its process.
- **IntelliJ IDEA:** editor splitters are a tree of splitters with tab groups (`EditorWindow`) as leaves, stored in
  `workspace.xml`; a tab dragged out of the frame becomes a floating editor window with no tool windows or toolbars,
  and can be dragged back onto drop zones that split a group. One instance per config and system directory
  (`DirectoryLock`); a second launch forwards its command line, and one that cannot reach a running instance shows an
  error instead of starting.

## 3. Approach

Three were considered:

1. **A VS Code-style grid** (chosen): n-ary splits with weights, alternating orientation, tab-group leaves; an app-wide
   registry owns tab content and each window's workspace only lays out tab ids; tab drags use plain mouse events in
   screen coordinates, as `DockDrag` does, so synthetic events drive them in tests.
2. **A binary tree:** today's `SplitTree` shape with groups as leaves. Simpler, but a second split in the same direction
   gives 50/25/25, and dropping a tab between two columns needs another nesting level.
3. **A third-party docking library:** tear-out for free, but it fights the theme engine, the Enterprise kit and the
   existing dock, and adds a UI dependency.

## 4. Principles

- **The application owns windows and tab content.** A workspace places tab ids; it never creates or disposes content.
- **One process per home, one main window per process**, both enforced by construction, not by hiding entry points.
- **A pure, serializable model; a view that renders it.** `dev.jasper.app.layout` is JDK-only, like `dev.jasper.app.dock`.
- **A clean break** (Jasper is an unpublished MVP): removed actions, keys, SDK types and files go with no migration or
  compatibility shims; in-repo plugins, the testkit and the docs move with each change.
- **No motion:** splits, moves, drags and tear-out are instant.

## 5. Single instance

### 5.1 The lock

At bootstrap, before AWT starts, Jasper opens `<home>/daemon/instance.lock` and calls `FileChannel.tryLock()`, keeping
the channel open for the life of the process. The operating system releases the lock however the process ends, so it is
never stale. The process that holds it is the **owner**; there is never more than one owner per home. A home override
(`JASPER_HOME`, `-Djasper.home`) selects another home and so another lock: dev runs keep working beside the installed
app.

### 5.2 What a launch does

| Launch | Lock free (becomes owner) | Lock held |
|---|---|---|
| Plain launch, Dock or Finder start, `open -n` | Restores the main window (section 13) | Hands off RAISE, exits 0 |
| With paths (command-line paths, a file-open event at start) | Restores the main window, then opens the paths | Hands off OPEN_PATHS, exits 0 |
| `--safe-mode`, `--config`, `--plugin-dir` | Starts with the flag | Hands off RAISE with the ignored flags, exits 0; the owner shows "Jasper is already running: quit it first to start with --safe-mode" |
| `--background` (login item) | Resident warm-up, as today | Exits 0 |
| `--no-window` (the CLI's cold start) | Serves commands without a window, as today | Exits 0 (the owner serves the CLI) |
| `--replacing <pid>` (restart's replacement, internal) | Starts | Waits for the lock up to 30 s, then shows the unresponsive-owner error |

`--standalone` is removed. Residency (`system.run_in_background`) now decides only whether the owner outlives its
windows; it still applies only to the default home launched without start-up flags, so a dev run quits with its last
window as today.

### 5.3 Endpoints and the handoff

- The owner binds the handoff endpoint (`daemon/socket`) and the command socket (`daemon/commands`) right after taking
  the lock, before AWT. Neither depends on residency any more. The bind-time locks (`daemon/lock`,
  `daemon/commands.lock`) go: the instance lock already excludes every other binder.
- Requests that arrive before the application is ready are queued and run in order once the main window can take them.
  A handoff is answered OK on receipt; a CLI call is answered when it has run.
- **Handoff protocol v2** (`LaunchRequest`): the magic line and token as today, protocol `2`, the code location and
  modification time, then the intent: `RAISE` or `OPEN_PATHS`, the launch's working directory, its paths (resolved
  against that directory) and the start-up flags it ignored. RETIRE is removed: there is never another instance to
  retire. Replies: OK, TOKEN, UPDATED, PROTOCOL.
- **An owner that does not answer:** the launch retries the handoff for up to 10 s (the owner may be between taking the
  lock and binding), then shows "Jasper is already running but not responding. Quit it (process *pid*) and try again."
  and exits 1. It never starts as a second instance.
- **A newer build:** an owner whose code location or modification time differs from the launch's answers UPDATED. With
  no window open it restarts itself into the new build (the existing reopen-after-update path), passing the request's
  paths to the replacement; with windows open it carries out the request itself and posts "Jasper was updated" with a
  Restart action. The launch exits 0 either way.
- **Restart** (Plugins manager, VM options, updates) spawns the replacement with `--replacing <pid>` and quits through
  the usual guards; the replacement takes the lock once the old process is gone. `RestartFlow`'s probe-retire-standalone
  path and `ResidentControl` are removed.

### 5.4 Platforms

- **macOS:** `Desktop.setOpenFileHandler` is installed at start-up; each file opens as an editor tab in the main
  window's active group (through the default editor, as Cmd-click does), each directory as a terminal tab there. The
  plist gains `CFBundleDocumentTypes` for `public.text`, `public.source-code` and `public.folder` at `LSHandlerRank`
  `Alternate`, so Jasper appears in Open With and accepts drops on its Dock icon without claiming any type as default.
  `open -n` starts a process that meets the instance lock and hands off like any other launch. The reopen event restores
  or raises the main window.
- **Windows and Linux:** paths on the command line are positional arguments (`AppArguments` gains them); a second
  `Jasper.exe file.txt` hands off OPEN_PATHS. On Windows the launch still passes its foreground right
  (`WindowsForeground.allow`) before handing off.
- **The `jasper` CLI:** `jasper --option` still spawns the launcher, which now hands off; `jasper` with no arguments
  raises or restores the main window; `jasper <path>…` opens the paths as OPEN_PATHS does (today it is "unknown
  command").

## 6. Windows

### 6.1 The window manager

`WindowManager` (application-owned, EDT) holds at most one `MainWindow` and any number of `ChildWindow`s. It creates the
main window only when none exists; `MainWindow`'s constructor is package-private and `verifyApplicationArchitecture`
pins its one call site. `JasperApplication` delegates every window question to it; the `windows` set, `newWindow`,
`openWindow` and `composeWindow` go.

### 6.2 The main window

The main window keeps all global chrome: header and toolbar, menus, the tool window dock and stripes, the status bar,
the notification corner layer and the Buddy. Its centre, inside `ToolWindowArea`, is the main workspace. It is built
exactly once per opening; once closed it is rebuilt from the saved layout by whatever needs it next (section 6.4).

### 6.3 Child windows

A child window has a title bar and a workspace, nothing else: no toolbar, header, status bar, dock, stripes, notification
layer or Buddy. Its title is the active tab's title.

- **macOS:** a focused child window shows the same menus as the main window in the screen menu bar, built from the same
  menu model (IntelliJ's floating editor windows do the same; macOS expects menus there).
- **Windows and Linux:** no menu bar; the title pane shows the title only.
- In both, the Keymap applies (root bindings per frame, with the same contexts), the command palette opens over the
  focused window, and the workspace has the same translucency as the main window's.

### 6.4 Lifecycle

| Event | Result |
|---|---|
| A child window's last tab closes or moves out | The child window closes |
| The user closes a child window | Its tabs close, with their guards (unsaved editors ask) |
| The main window's last tab closes | The empty workspace stays, with New Terminal Tab and Open File (section 10) |
| The user closes the main window, or Quit | Guards run for every window's tabs; the layout is saved; every child window closes, then the main window; residency decides whether the process stays |
| macOS Cmd+Q while resident (`toBackground`) | As closing the main window |
| Any request while no main window is open (handoff, reopen, CLI, notification click, a plugin opening a tab, the tray) | The main window and its child windows are restored first, then the request runs |

### 6.5 Opening windows

New Window is removed everywhere: the `new_window` action, File › New Window, the toolbar button, the palette entry, the
auxiliary frames' File menu, the macOS menu bar item and the content registry's factory. In their place:

- **New Terminal Window** (`new_terminal_window`, cmd+n / ctrl+shift+n): a child window with a terminal tab in the
  current terminal's directory. It replaces New Window in the File menu, the toolbar and the menu bar item.
- **New Editor Window** (the Editor plugin, no default key): a child window with a new untitled document.
- **Move Tab to New Window** and **Move Tab to Main Window** (no default keys; tab menu and palette).
- **A placement for plugins:** `Windows.openTab(..., TabPlacement.NEW_WINDOW, ...)` (section 14).

### 6.6 What stays a dialog or utility frame

Settings, About and Licenses, the file chooser, plugin dialogs (`DialogSpec`) and overlays remain auxiliary surfaces.
The Errors window, a non-modal tool view, becomes an app-owned content tab, opened in the main window's active group. The auxiliary frames' File menu keeps Close Window and,
off macOS, Quit.

## 7. The layout model

### 7.1 Values

`dev.jasper.app.layout` (JDK-only; `verifyApplicationArchitecture` enforces it) holds immutable values:

- `TabId`, `GroupId`: UUID records.
- `Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized)`.
- `sealed interface Node permits Split, Group`.
- `Split(Orientation orientation, List<Child> children)` with `Child(Node node, double weight)`;
  `Orientation { HORIZONTAL, VERTICAL }` (HORIZONTAL lays children left to right).
- `Group(GroupId id, List<TabId> tabs, TabId selected)`.

### 7.2 Invariants

- A `Split` has at least two children; no child is a `Split` of the same orientation (such nesting is flattened); its
  weights are positive and sum to 1 (normalized on construction).
- A `Group` holds its tabs in order with `selected` among them. The only empty group allowed is the sole group of an
  empty workspace, and only the main window's workspace may be empty.
- Tab and group ids are unique within a layout; `activeGroup` and `maximized` name groups in it.

### 7.3 Operations

Each returns a new `Layout` and rejects arguments that break the invariants.

| Operation | Result |
|---|---|
| `open(tab, group, index)` | Inserts the tab, selects it, activates the group |
| `select(tab)`, `activate(group)` | Selection and focus |
| `split(tab, group, side)` | A new group holding the tab on `side` (LEFT, RIGHT, UP, DOWN) of `group`; it takes half of `group`'s space; a same-orientation parent gains a sibling instead of nesting |
| `move(tab, group, index)` | Within or between groups |
| `moveToNewGroup(tab, group, side)` | As `split` with an existing tab |
| `remove(tab)` | Removes the tab (closing or moving out); an emptied group is removed, a split left with one child collapses into it and nesting is re-flattened; the active group becomes the most recently used remaining group |
| `resize(split path, weights)` | From divider drags |
| `toggleMaximize(group)` | The group fills the workspace; a move between groups, a new group, the maximized group's removal or a single remaining group clears it; closing one of its tabs keeps it (VS Code) |
| `neighbour(group, direction)` | The group in that direction by geometry over the unit square (today's `SplitTree.navigate` rule: nearest in the direction, then nearest centre across it) |

The model keeps a most-recently-used order of groups for focus after removal; it is not persisted.

## 8. Encoding

`<home>/workspace.toml`, written atomically through `TomlStateFile` and read with tomlj, capped like `ui-state.toml`:

```toml
version = 1

# ids shortened here; the file holds full UUIDs

[[windows]]
role = "main"            # exactly one "main"; the others are "child"
x = 40
y = 60
width = 1400
height = 900
extended_state = 6       # maximized bits only, as [main_window] today
active_group = "3f1e…"
maximized = ""           # a group id, or empty

[[windows.nodes]]        # the tree, flat: a node names its parent ("" for the root)
id = "a91c…"
kind = "split"
orientation = "horizontal"
parent = ""
weight = 1.0

[[windows.nodes]]
id = "3f1e…"
kind = "group"
parent = "a91c…"
weight = 0.5
tabs = ["7d22…", "c0b4…"]
selected = "7d22…"

[[windows.tabs]]
id = "7d22…"
kind = "terminal"        # "terminal", or "content:<plugin id>:<restorer kind>"
title = "build"          # a renamed title; empty when not renamed
directory = "/Users/me/src/jasper"
profile = "zsh"

[[windows.tabs]]
id = "c0b4…"
kind = "content:dev.jasper.editor:document"
title = "README.md"
state = { path = "/Users/me/src/jasper/README.md" }
```

- Children are stored in order; weights are normalized on read.
- A bad tab entry is dropped; a group left empty by that is dropped as `remove` would; a bad window entry is dropped;
  no main window, or an unreadable file, sets the file aside as `workspace.toml.bad` and starts with one terminal tab.
- The main window's bounds move here; `UiState`'s `[main_window]` is removed. The dock layout stays in `ui-state.toml`.

## 9. Tabs and their content

### 9.1 The tab registry

`TabRegistry` (application-owned, EDT) owns every tab: its id, kind, content, presentation (title, icon, unsaved marker,
detail, status), commands, close guard and focus target, and which window and group hold it. `ContentRegistry` folds
into it, keeping its guards: per-plugin close guards, batched approval, and the synchronous `mayClose` used by native
quit. A tab is disposed only when it closes or the application shuts down.

### 9.2 Content kinds

- **Terminal:** one `TerminalPane`, so one session. `TerminalTab` and its `SplitTree` are deleted; the rename state
  (`TabState`) moves to the tab.
- **Hosted:** a plugin's `UiView` component (today's `ContentEntry`).
- **Placeholder:** a tab that could not be restored, naming why (plugin missing or disabled, restorer failed) with
  Close, and Retry when the plugin is present.

### 9.3 Hosts and moves

Each workspace gives the tabs it holds a `TabHost` supplying everything window-specific: the shortcut, context-menu and
file-link handlers, where activity and registry events report from, the translucency and theme zone, and focus.
Moving a tab detaches it from one host and attaches it to the next; its Swing component is removed from one group view
and added to another. The terminal session is never touched (section 2.3). "Go to" callbacks in notifications and
activity resolve the tab by id when they run, so they follow it to whichever window holds it.

### 9.4 The terminal registry

`TerminalRegistry` keeps window → tabs, where a window is the main window or a child window and every tab is listed;
a terminal tab has exactly one pane. `TabEntry` gains a kind and loses its pane list; `PaneEntry.split` opens a new
terminal tab in a new group beside the pane's group. `SplitAxis` is replaced by the layout's sides.

## 10. The workspace view

- `WorkspaceView` renders a `Layout`. Splits use one `SplitContainer` with draggable dividers for any number of children;
  divider drags write weights back to the layout. The theme-update divider handling `TerminalTab` does today moves into
  it.
- `GroupView` is an always-visible tab strip over a card area showing the selected tab. The strip is Enterprise's
  `TabStrip` (its plan 6b), so the active kit supplies `EqualTabs` or `ClassicTabs`; the strip is the drag source for
  tab drags.
- The active group's strip shows as active; `terminal.dim_inactive_panes` dims every terminal tab outside the active
  group, in place of inactive panes.
- An empty main workspace shows New Terminal Tab and Open File with their current shortcuts.
- `TerminalDeck`, `WindowTabs`' window-wide role, `TerminalTab` and `SplitTree` are deleted.

## 11. Commands

| Command | macOS / Windows and Linux | Does |
|---|---|---|
| `new_tab` | ⌘T / Ctrl+Shift+T | A terminal tab in the active group |
| `split_right` | ⌘D / Ctrl+Shift+D | A terminal tab in a new group to the right of the active group, in the active terminal's directory and profile |
| `split_down` | ⌘⇧D / Ctrl+Alt+Shift+D | The same, below |
| `close_tab` | ⌘W / Ctrl+Shift+W | Closes the active tab (absorbs `close_pane`, which is removed) |
| `close_group` | ⌥⌘W / Ctrl+Alt+Shift+W | Closes every tab in the active group |
| `close_window` | ⇧⌘W / none | Closes the focused window (Alt+F4 and the title bar already do elsewhere) |
| `maximize_group` | ⌘⇧↩ / Ctrl+Alt+Shift+Enter | Toggles the active group filling the workspace (replaces `zoom_pane`) |
| `focus_group_left`, `_right`, `_up`, `_down` | ⌘⌥←→↑↓ / Ctrl+Alt+Shift+arrows | Focus the neighbouring group (replace `focus_pane_*`) |
| `next_tab`, `previous_tab`, `select_tab_1`–`9` | as today | Within the active group |
| `new_terminal_window` | ⌘N / Ctrl+Shift+N | Section 6.5 (replaces `new_window`) |
| `move_tab_to_new_window`, `move_tab_to_main_window` | none | Section 6.5 |

- Every command acts on the window that has focus; a command run from the palette acts on the window it opened over.
- The tab menu: Rename (terminal tabs), Move to New Window or Move to Main Window, Close, Close Others, Close Group, then
  the content's own commands.
- Enterprise's SPLIT palette verb ("Open in a split pane", spec 8.2 of the Enterprise design) opens its target in a new
  group to the right.
- `KeyContext.TERMINAL` means "inside a terminal tab"; `KeyContext.WINDOW` and the SDK's `ShortcutContext.Kind.WINDOW`
  are removed with plugin windows (the Vault manager's shortcuts become CONTENT shortcuts).
- Saved key bindings for removed ids are reported as unknown by the configuration diagnostics, like any unknown key.

## 12. Tear-out and drag-back

- One application-wide `TabDrag`. A press on a tab that moves past the kit's drag threshold starts it. It uses plain
  mouse events with screen coordinates: Swing keeps delivering drag events to the pressed tab outside its window. A
  small undecorated window shows the tab's label under the cursor, with no motion.
- A pure `WorkspaceDropResolver` (beside `DockDropResolver`) maps the cursor over any Jasper workspace to a drop:
  over a strip, insert at that position; over a group's centre, add to that group; over a group's outer quarter on any
  side, split that side. An overlay in the target window shows the landing zone, as `DockDropOverlay` does for the dock.
- Releasing outside every Jasper window tears the tab out into a new child window at the cursor, sized like its old
  group. Releasing over a Jasper window outside its workspace (the dock, chrome or a title bar) cancels, as do Esc, a
  lost release and focus moving to another application.
- Where workspaces overlap, the most recently active window under the cursor wins.
- Dragging the only tab of a child window outside every window does nothing; that window moves by its title bar.

## 13. Saving and restoring

- **Saved:** within one second of each layout change (on a virtual thread), and when any window closes, on Quit and when
  going to the background; a crash loses at most a second.
- **Restored at start**, and whenever section 6.4 restores the main window: the main window and its child windows with
  their bounds, checked against the current displays as `WindowPlacement` does today, with their splits, groups, tab
  order, selection and active group.
- **Terminal tabs** keep a renamed title and their profile and start a shell in their last local directory (a remote
  directory falls back to where a new shell starts). Only each group's selected tab starts at once; the others start
  when first shown, and until then the registry and the SDK report them as not started. A tab that ran a program comes
  back as a plain shell. No scrollback.
- **Content tabs** are rebuilt by their plugin's restorer (section 14) from the state the plugin gave when opening the
  tab: the Editor reopens files (an unsaved untitled buffer is not kept), Remote chooses between reconnecting and a
  Reconnect state. A tab whose plugin is missing or whose restorer fails becomes a placeholder.
- The `--background` warm-up rehearsal never reads or writes `workspace.toml`.

## 14. SDK changes

A clean break after Enterprise's 0.18. The changes land in plans 3, 4 and 6; each bumps the SDK version as the
repository's SDK rules require.

| Area | Change |
|---|---|
| `TabHandle` | Every tab: gains `kind()` (TERMINAL, CONTENT) and `terminal(): Optional<PaneHandle>`; `panes()` and `activePane()` are removed |
| `PaneHandle` | Stays the terminal handle (`sendText`, `paste`, `info`, …); `tab()` is its tab |
| `WindowHandle` | `tabs()` lists every tab in workspace order; `activeTab()` is the active group's selected tab; gains `isMain()` |
| `Terminals.activePane()` | Unchanged: the active tab's terminal if it is one, else empty |
| `Terminals.split(pane, direction, request)` | A terminal tab in a new group beside the pane's group |
| `Terminals.openTab(window, request)` | A terminal tab in the window's active group |
| `Windows.openTab(spec, owner, view)` | Gains a `TabPlacement`: ACTIVE_GROUP (default), SPLIT_RIGHT, SPLIT_DOWN, NEW_WINDOW |
| `ContentSpec` | Gains restore state: a restorer kind and a small string map |
| `Windows.restorer(kind, restorer)` | New: rebuilds a plugin's tabs at start from their restore state |
| `ContentTab` | `move(boolean)` and `isSeparate()` are replaced by `moveToNewWindow()`, `moveToMainWindow()` and `window()` |
| `Windows.create`, `PluginWindow`, `WindowSpec`, `ShortcutContext.Kind.WINDOW` | Removed |
| `Panels`, `PanelFactory`, `PanelHost.window()` | Panels live in the main window only; the factory runs once |
| `OverlaySpec` | Its owner may be a child window |
| Events | `TAB_*` cover content tabs too; new `TAB_MOVED`; child windows send `WINDOW_*` |
| `StandardMenu.PANE` | Folded into `TAB` |
| Wording | "terminal window" becomes "Jasper window" throughout (`WindowOwner`, `FileEditor`, `CommandCall.window`, `ShortcutContext.GLOBAL`) |

Every in-repo plugin moves: Remote (splits, per-window panels become one), Vault (manager tab), sample (About tab),
Editor (placements, restorer, New Editor Window), indexer, history, snippets and fileserver (tab and pane reads). The
testkit fakes (`FakeTerminals`, `FakeUi`, `FakeContent`) and the contract suites follow. THD Sourcing Crypto, outside
this repository, gets a hand-off note in `docs/STATUS.md`.

## 15. Edge cases

| Case | Behaviour |
|---|---|
| The last tab in a group closes | The group goes; its neighbours take the space; focus goes to the most recently used group |
| The last tab in a child window closes or moves out | The child window closes |
| The last tab in the main window closes | The empty workspace stays (section 10) |
| The main window closes with child windows open | Section 6.4 |
| A shell exits | `terminal.on_exit` applies to its tab |
| A maximized group loses a tab or gains one by a move | Maximize clears; closing one of its tabs keeps it |
| A plugin is disabled with tabs open | Its tabs close; at the next start they restore as placeholders |
| A tab moves while its old window shows an overlay (Remote's "Connecting…") | The overlay belongs to the window and stays |
| A tab moves while its find bar is open | The find bar is inside the terminal tab and moves with it |
| A modal dialog opens during a tab drag | The drag cancels |
| Displays change between runs | Windows off every display are placed as `WindowPlacement` does today |
| `workspace.toml` is unreadable | Set aside as `workspace.toml.bad`; one terminal tab |

## 16. Removed

Per-terminal splits (`TerminalTab`, `SplitTree`, `TerminalDeck`, `close_pane`, `zoom_pane`, `focus_pane_*`,
`SplitAxis`); every New Window path (section 6.5) and `WindowCallbacks.newWindow`; detached windows and
`ContentRegistry`'s factory; `ContentRegistry` itself (into `TabRegistry`); `Windows.create`, `PluginWindow`,
`WindowSpec`, `ShortcutContext.Kind.WINDOW`, `KeyContext.WINDOW`; `--standalone`, private `commands-<pid>` sockets,
`daemon/lock`, the RETIRE request, `RestartFlow`'s retire path and `ResidentControl`; `UiState`'s `[main_window]`.

## 17. Testing

TDD in every plan; headless throughout.

- **Layout model:** every operation and invariant, flattening, removal down to an empty workspace, the MRU rule,
  navigation, maximize.
- **Encoding:** round trips, each kind of bad entry dropped alone, an unreadable file set aside, normalization of
  weights.
- **Drop resolver:** every zone, overlapping windows.
- **Single instance**, with real child JVMs: a second process cannot take the lock; several racing cold starts end with
  exactly one owner and every other launch handing off; a killed owner releases the lock; the replacement waits for the
  lock; an unresponsive owner produces the error, never a second instance; the launch decision table; protocol v2
  decoding; the UPDATED path; macOS file-open routing; CLI paths.
- **Views:** a terminal tab moved between two windows keeps its session (`FakeConnector`); Cmd+D makes a group;
  closing the last tab of a group collapses it; a child window closes when empty; closing the main window closes the
  child windows after their guards; the main workspace can be empty; restore lazily starts unselected terminals.
- **Architecture:** `MainWindow` has one constructor call site; `dev.jasper.app.layout` is JDK-only.
- **SDK:** contract tests on the app and the testkit.
- **GUI checks for the user:** tear-out and drag-back, multiple displays, the macOS menu bar in child windows, Dock
  drops, Open With.

## 18. Delivery

Six plans, each written once Enterprise 6–11 has merged, each leaving Jasper runnable:

1. **Single instance:** the lock, early endpoints, handoff v2, the decision table, restart's replacement, macOS
   file-open handling and plist keys, CLI paths; `--standalone`, private command sockets and RETIRE removed.
2. **Layout model and encoding:** `dev.jasper.app.layout` and `workspace.toml`; no visible change.
3. **Tab registry and workspace view:** groups and strips in the window, Cmd+D and Cmd+Shift+D split the workspace,
   per-terminal splits deleted, the SDK's tab and pane changes.
4. **Main and child windows:** `WindowManager`, New Window removed, New Terminal Window and New Editor Window, Close
   Window, plugin windows and the Errors window become tabs.
5. **Tear-out and drag-back.**
6. **Saving and restoring**, with the plugin restorers.

Docs (`docs/app-architecture.md`, `docs/keyboard-shortcuts.md`, `docs/configuration.md`, `docs/plugin-ui.md`,
`docs/sdk-architecture.md`, `docs/packaging.md`), `docs/STATUS.md` and the testkit move with each plan.

## 19. Risks

1. **Enterprise's chrome is built for today's window.** Its `TabStrip`, `WorkspaceChrome`, `ShellChrome` and the classic
   header and menus land first; plan 3 and 4 adapt them to group strips and child windows. Enterprise spec items that
   name New Window (2.24's auxiliary File menu, 10.3) are superseded by this design.
2. **The SDK break** touches every plugin, the testkit and an external plugin.
3. **Tighter single-instance rules** touch residency, restart, safe mode, login launches and the CLI; an owner that hangs
   must produce a clear error and never lock the user out silently.
4. **Moving live components between frames:** per-window translucency, the macOS title bar, theme refresh, focus and the
   palette's key routing all assume one tab deck per window today.
5. **Test churn:** about 74 test files reference tabs, splits or New Window.
6. **Drags cannot be verified headlessly beyond the resolver and synthetic events;** the user checks them in the GUI.

## 20. Out of scope

Tool windows as tabs; dock or stripes in child windows; more than one main window; scrollback or running processes that
survive a relaunch; showing one tab in two places ("Copy Editor into New Window"); preview or pinned tabs.
