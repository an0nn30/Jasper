# Workspace tabs 4a: the main window and child windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jasper has one main window, with all of its chrome around the main workspace, and any number of child windows,
each a title bar over a workspace; an application-owned `WindowManager` makes, tracks and closes every one of them,
restores the main window before any request runs, closes a child window with its last tab, keeps the main window's empty
workspace, and closes the child windows before the main window. New Window is gone: New Terminal Window, Close Window,
Move Tab to New Window and Move Tab to Main Window take its place, and every outside request opens in the main window.

**Architecture:** `dev.jasper.app.workspace` gains `WindowRole` (MAIN, CHILD), `WindowManager`, the sealed `JasperWindow`
(`MainWindow`, `ChildWindow`), `WindowShell` (what a native frame does, as functions) and `WindowFrame` (today's
`TerminalWindow`, renamed: the JFrame around any window's content). A child window is a `WindowContent` in the CHILD
role: the same actions, menus, keymap, palette and tab registry as the main window's, with only its workspace mounted
and no dock, stripes, status entries or toolbar views; the main window's empty workspace shows `EmptyWorkspace` (New
Terminal Tab, Open File). `JasperApplication` composes and wires each window's content for the manager (`composeContent`)
and builds its frame (`frames`, a test seam), and asks the manager every window question; the tab registry reaches the
manager through `TabRegistry.Windows` in place of its factory and detached hosts. As with `AuxiliarySurface` and
`NativeShells`, everything testable lives in the headless core, and headless tests run the lifecycle with stand-in shells.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1, 4, 5.4's retarget, 6.1-6.5, 9.3, 10's empty state, 11's window rows, 15, 16, 17 and 18). The first half of
section 18's plan 4; [4b](2026-10-18-workspace-4b-plugin-windows.md) (plugin windows become tabs, the Errors window as a
tab, the SDK's window rows) follows it. Plans [1](2026-10-17-workspace-1-single-instance.md),
[2](2026-10-17-workspace-2-layout-model.md), [3a](2026-10-17-workspace-3a-tab-registry.md) and
[3b](2026-10-18-workspace-3b-sdk-tabs.md) execute first, in that order.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`,
after plan 3b's last commit (the branch is `main` at `11e40192c` plus this series). Run every command from the worktree
root. Every anchor below was taken from, and every edit script of Tasks 1-7 replayed on, a copy of `22b702456` with the
edit scripts and created files of plans 1, 2, 3a and 3b applied in order (3b as committed in `17830fac6`); nothing was
compiled or run. Every edit quotes the text it replaces, and the scripts stop with a message when that text is not found
the stated number of times: re-find the quoted text (a plan before this one may have reworded it), adjust the anchor,
re-run, and record it in this plan's banner; never edit around a missing anchor. Later tasks anchor on text earlier
tasks of this plan wrote, and quote it as those tasks leave it.

## Spec issues

None. One planned deviation: section 6.4's last row restores "the main window and its child windows" before a request
runs, and section 18 puts saving and restoring in plan 6, so until then restoring opens a fresh main window and no child
window (Rulings 4), and closing the main window saves what it saves today (its bounds and the dock layout); STATUS
records it as a deviation. Section 6.3's macOS bullet as amended (`d67ba6668`: under a look with in-window menus a
child window has no menu bar) is Rulings 7. Section 6.6's Errors tab and section 14's SDK rows are plan 4b's.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 4, 16): `TerminalWindow` (renamed `WindowFrame`), `WindowCallbacks`, `JasperApplication.newWindow`,
  `openWindow`, `composeWindow`, its `windows` set and `lastActive`, the `new_window` action, every New Window menu,
  toolbar and tray entry, `TabRegistry.setFactory`, `activated`, `detached` and `move(Tab, boolean)`, `Tab.detached` and
  `move(boolean)`, and `WorkspaceHost`'s `detached` go outright with their tests' uses. Saved bindings for `new_window`
  become unknown keys (spec 11). No shim, no migration, no "was removed" text.
- The SDK keeps its shape: no file under `jasper-sdk` or `jasper-sdk-testkit` changes except for the order a move reports
  in (Task 4 Step 4: `TabMoved`'s Javadoc, the README, the fake and the contract test; Rulings 23), and under `plugins`
  only Task 6's wording does (plan 4b changes the rest).
  `ContentTab.move(boolean)` and `isSeparate()` keep their meaning through the new registry (Rulings 13).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture` checks the graph is acyclic):
  nothing gains an edge. `workspace` already names `contributions`; `application` already names `workspace`.
- No motion (spec 4): windows open, close and take tabs at once.
- The theme-migration ledger: `ThemeMigrationCoverageTest` (`appearance/ThemeMigrationCoverageTest.java:17-18`) requires a
  `docs/design/theme-engine/migration.csv` row for every Java file under `jasper-app/src` whose text matches its
  `CANDIDATE` pattern (`import javax.swing`, `import java.awt`, the word appearance, `new J…`, `new …Window`, `…Panel`,
  `…View`, …). Each task that creates, renames or deletes such a file edits the ledger in the same commit with the
  `ledger.py` helper below. `WindowRole`, `MainWindow` and `ChildWindow` match nothing and get no row.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan4a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan4a
cp build/plan3a/edits.py build/plan3a/ledger.py build/plan4a/ 2>/dev/null || echo "plan 3a's helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan4a in place of build/plan3a"
ls build/plan4a/edits.py build/plan4a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'`
from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan4a")`. Java text inside a script is
in triple-quoted strings, so `\n` and `…` in Java source stay as written.

## Review Focus

1. Move Tab to Main Window on a child window's last tab: the child window closes only after the move has reported itself
   (`TAB_MOVED`, then its `WINDOW_CLOSED`), the tab arrives selected in the main window with its session, the child's
   frame is disposed once, and a failed attach goes back to the window it left. Task 2:
   `WindowManagerTest.aTerminalTabMovedToANewWindowAndBackKeepsItsSession`; Task 4:
   `WindowManagerTest.aChildWindowAMoveEmptiesClosesAfterTheMoveIsReported`,
   `TabRegistryTest.theWindowAMoveLeavesHearsOfItAfterTheMoveAndAFailedMoveGoesBackToIt`,
   `WindowManagerTest.aPluginsTabMovesThroughTheRegistryToANewWindowAndBack`.
2. Closing the main window while a child window holds an unsaved editor: the editor's guard is asked once, a refusal
   closes nothing in any window, and an agreement closes the child window, then the main window, in that order. Task 2:
   `WindowManagerTest.closingTheMainWindowAsksEveryWindowsGuardsThenClosesTheChildWindowsFirst`; Task 3:
   `JasperApplicationWindowsTest.closingTheMainWindowClosesItsChildWindowsAndThenTheProcess`.
3. Move Tab to New Window on a child window's only tab, or on a tab being closed: nothing happens and no empty window is
   left behind. Task 2: `aTerminalTabMovedToANewWindowAndBackKeepsItsSession`; Task 5:
   `WindowManagerTest.theWindowCommandsOpenCloseAndMoveWindows` (the command is disabled there).
4. A plugin opening a view while the main window is still being composed (a panel factory, a `WINDOW_OPENED` listener):
   no second main window, and the view opens in the main window once it is up; a request while the main window closes
   opens no second one either. Task 4: `WindowManagerTest.aPluginsTabAskedForWhileTheMainWindowOpensOpensThereOnceItIsUp`;
   Task 2: `WindowManagerTest.aRequestWhileTheMainWindowClosesOpensNoSecondOne`; Task 3:
   `JasperApplicationWindowsTest.theMainWindowItsManagerAndTheFramesAreMadeOnlyWhereTheApplicationSays`.
5. An operating-system notification clicked after its command's tab moved to another window: the window holding the tab
   now comes forward, not the one it finished in. Task 3:
   `SystemNoticesTest.aClickRaisesTheWindowThatHoldsThePaneNowNotTheOneItPostedFrom`,
   `CommandNotifierTest.aNotificationNamesThePaneThatPostedItUntilThePaneCloses`.

## Verified facts

From a replay of plans 1, 2, 3a and 3b on `22b702456` (see Workspace).

1. `TerminalWindow` (`workspace/TerminalWindow.java:25-205`) is the only `JFrame` of a Jasper window: it builds its
   `WindowContent` (`:77-78`), applies the initial configuration before mounting (`:79-82`), mounts it with
   `WindowContent.mount` (`:83`), installs the root bindings (`:85`), glass on macOS (`:91-94`), packs and sizes
   (`:95-100`); `restoreLayout(UiState)` restores the saved bounds (`:105-119`); `close()` saves the bounds after closing
   the content (`:192-204`); `discard()` forgets the layout (`:185-190`). `WindowCallbacks` (`:11-20`) carries
   `newWindow`, `quit`, `activated`, `closed`, `stateChanged`.
2. `JasperApplication` keeps `Set<TerminalWindow> windows` and `lastActive`; `newWindow()`, `newWindow(Path)` (null makes a
   detached window), `openWindow` (installs the content registry's factory, counts `openingWindows`) and `composeWindow`
   (launcher, `windowOpening()`, the frame, `configuration.register`, `connectContributions(contributions, uiState)`, the
   error/info/launch/open-file hooks, `notificationViews.attach`, `connectTerminals`, glass, Buddy, the activity listener,
   `restoreLayout`, `show`) make every window; `startPlugins` installs the factory again and gives `NativeShells`
   `() -> newWindow()`. Plan 1's `open(List<Path>, String)`, `openFirstWindow(List<Path>)` (returns `TerminalWindow`) and
   `openRest` target the last active window; `handedOff` calls `open`; `newWindowFromMenuBar` and `settingsFromMenuBar`
   serve the menu bar item. `raiseFor`, `raiseTerminal`, `showConfigurationProblems`, `openSettings`, `nativeWindow`,
   `owns`, `glassOpacity`, `forget`, `windowClosed`, `windowStateChanged`, `windowActivated`, `updateBuddyActions`,
   `toBackground` and `quitNow` walk the set. The warm-up rehearsal builds its own `TerminalWindow` with
   `Rehearsal.CALLBACKS` and `connectContributions(contributions, rehearsalState, false)`.
3. After 3a/3b `WindowContent` holds its `WorkspaceView` (`workspace`), a `TabRegistry` it joins on
   `connectContributions`, registering `workspaceHost()` = `new WorkspaceHost(id, detached, this::attach, this::detach,
   this::selectTab, ...)`; `detach` runs `onEmpty` when the workspace empties; `setActive` calls
   `registry.activated(id)`; the tab menu offers Open in Separate Window or Move to Main Window for a plugin's tab
   (`tab.detached()`, `tab.move(...)`); `requestNewWindow()` runs the `newWindow` consumer with `directory()`.
   `WorkspaceView.render` draws the layout's groups, the empty workspace's one empty group included.
4. `TabRegistry` (3a, 3b): `setFactory(Function<Boolean, UUID>)`, `lastMain`, `activated(UUID)`, `detached(UUID)`, `main()`
   (last main host, first non-detached, else the factory), `move(Tab, boolean)`, `move(Tab, UUID)` and `transfer` (sets
   `movingFrom`, detaches, attaches, recovers to the old window or another non-detached one, selects); `Tab.detached()`
   and `move(boolean)`; `WorkspaceHost(UUID id, boolean detached, ...)`. `WindowContent.attach` ends a maximize for a
   moving tab and reports `tabArrived` (taken back with `tabWithdrawn` when the attach fails), `detach` reports
   `tabClosed` only for a tab not moving, so every move through `transfer` unmaximizes both windows and reports
   `TAB_MOVED` (3a Rulings 17, 3b Rulings 5 and 6).
5. `HostedUi`'s `ContentTab` implements `move(boolean)` with `entry.move(separate)` and `isSeparate()` with
   `entry.detached()` (`plugins/HostedUi.java:567-568`).
6. `WindowContributions` (`workspace/WindowContributions.java:68-96`) connects every window to the contributions model:
   actions, toolbar entries, menus, status entries, palette scopes and requests, and docks every panel; it persists the
   dock on close. `WindowChrome.windowOpened()` builds toolbar views; `connectTerminals` calls it.
7. `NativeShells` takes a `newWindow` runnable for the auxiliary frames' File menu (New Window, Close Window, Quit off
   macOS; `windows/NativeShells.java:63-99`, `:698-718`). `ResidentTray.Entry.NEW_WINDOW` ("New Window") runs
   `Actions.newWindow` (`platform/ResidentTray.java:44-72`, `:121`).
8. `ActionId.NEW_WINDOW` (`new_window`, `cmd+n` / `ctrl+shift+n`) is in the File menu and the unified toolbar
   (`WindowChrome.java:80`, `:120`, `:479`), the palette (`WindowCommands.java:42`, `CommandsScope.java:23`),
   `WorkspaceActions` (`:53`, `:116`), and the tests `KeymapDefaultsTest`, `KeyBindingsTest`, `KeyBindingsContextTest`,
   `ApplicationActionsTest`, `TabMenuTest`, `WindowChromeTest`, `Settings9bPreview`. No default binding uses
   `cmd+shift+w`.
9. `MacTitleBar.install(root, content, minimumSizeChanged, supported)` sets the content pane and, when supported, adds a
   transparent unified bar with a centred title (`Style.WINDOW`, `setTitle`); `setMenuBar` puts a menu bar on the root,
   which the macOS screen menu bar shows while `apple.laf.useScreenMenuBar` is true (`bootstrap/ApplicationBootstrap.java`
   sets it from the look's `DesktopFlags`).
10. `SystemNotices.clicked` raises `windows.raise().apply(notification.request().target())` before the activity's
    `activate` runs (`application/SystemNotices.java:87-99`); `JasperApplication` passes the window id captured when it
    composed the window to `CommandNotifier.finished` (3a's hand-off, its STATUS "Left for plan 4"). `CommandNotifier`
    keeps `outcomes`: each producer key (the pane's id) to the notifications it posted, until the pane closes.
11. `NotificationsFeature.showHistory(window)` acts only for an attached window (`application/NotificationsFeature.java:113`).
12. `verifyApplicationArchitecture` (`gradle/application-architecture.gradle.kts:95-101`) runs `javap -c -p` over every app
    class in batches of 100.
13. Headless tests cannot build a `JFrame`; `AuxiliarySurface`/`NativeShells` keep everything testable in a headless core
    with a `Shell` record of functions.

## Rulings

Record them in `docs/STATUS.md` (Task 7). "What — why".

1. **A child window is a `WindowContent` in the CHILD role, not a class of its own: the same actions, menus, keymap,
   palette, tab host and tab registry as the main window's, with only its workspace mounted; its header, toolbar, status
   bar, dock and stripes exist and are never mounted; its contributions take actions, menus, palette scopes and palette
   requests and no panels, status entries or toolbar views** — one key dispatch and one menu model for both kinds (spec
   6.3: "built from the same menu model"); splitting the 1,650-line class would move most of it twice.
2. **`WindowManager`, the sealed `JasperWindow` (`MainWindow`, `ChildWindow`), `WindowShell` and `WindowFrame` live in
   `workspace`; the manager and the windows are a headless core, `WindowShell` the functions a frame gives it, and
   `WindowFrame` (today's `TerminalWindow`, renamed) the only `JFrame`** — `AuxiliarySurface`'s pattern (fact 13), so the
   lifecycle runs headlessly with stand-in shells; `MainWindow`'s package-private constructor needs its maker beside it.
3. **One main window per process, structurally: `MainWindow`'s one call site is `WindowManager.ensureMain`,
   `WindowManager`'s is `JasperApplication`'s constructor, and `WindowFrame`'s are `JasperApplication.frameOnScreen` and
   `rehearsalWindow`, each an explicit `new`; `verifyApplicationArchitecture` checks them over every class's constant pool
   (`javap -v`, which also shows an `X::new` reference that `javap -c` would not; Task 2 pins `MainWindow`, Task 3 the
   rest), `JasperApplicationWindowsTest` over the class files, and `WindowManagerTest` that `MainWindow`'s constructor is
   package-private** — spec 6.1 and 17.
4. **Until plan 6, restoring the main window opens a fresh one, with one terminal tab where a new shell starts, or in
   the first directory a request names, and no child window; `WindowManager.ensureMain(String trigger, Path directory)`
   is the one method plan 6 changes** — spec 18 puts saving and restoring in plan 6; spec 6.4's restoring of the child
   windows is a planned deviation until then (Spec issues).
5. **Every request with no main window restores it first: `open` (handoff, CLI, reopen, open-file), `openFirstWindow`, a
   notification click (`raiseFor`), a plugin's tab with no window (`TabRegistry.Windows.main`), New Terminal Window, and
   the menu bar item's Open Jasper, New Terminal Window and Settings…; its Quit does not** — spec 6.4's table lists the
   tray among the requests.
6. **The main window alone keeps the dock layout, Zen, its bounds (`[main_window]`), the notification card layer, the
   Notifications panel and the Buddy's visibility; a child window gets an in-memory `UiState`, no corner layer, and the
   Buddy follows the main window alone** — spec 6.2 lists them as the main window's chrome.
7. **A child window's title bar is the unified macOS bar in its WINDOW style (a centred title, no header) under a
   unified-title look and the system title bar otherwise (and under Compact, as the main window's); on macOS its root
   carries its menus only while the screen menu bar shows them, so under a look that puts the menus in the window (the
   Enterprise look turns the screen menu bar off) a child window has no menu bar and its shortcuts still work; it never
   shows a menu row; off macOS FlatLaf's title pane holds the title alone in the chrome's colours; the title is the
   active tab's** — spec 6.3 as amended (`d67ba6668`); an auxiliary frame's title bar does the same.
8. **In a child window View › Toolbar, Status Bar, Rail and Zen are disabled; a panel request from a child window (a tool
   window's toggle, a plugin's `Panels.show`) goes to the main window (`Contributions.panelRoute`), which comes to the
   front for SHOW and TOGGLE** — the child window has no dock (spec 1 decision 1).
9. **New Terminal Window (`new_terminal_window`, ⌘N / Ctrl+Shift+N) opens a child window with a terminal tab in
   `WindowContent.directory()` (the current terminal's inheritable directory; where a new shell starts when there is
   none), after restoring the main window; the menu bar item's opens where a new shell starts** — spec 6.5.
10. **Move Tab to New Window is enabled while a tab is selected in the main window, or in a child window that has another
    tab; the manager opens an empty child window and moves the tab through `TabRegistry.move(Tab, UUID)` (which
    unmaximizes both windows and reports `TAB_MOVED`, fact 4), and a move the registry refuses or fails closes the empty
    window again. Move Tab to Main Window is enabled in a child window; the child window closes when the move empties it**
    — spec 6.4 and 6.5; a child window's only tab already is its own window (spec 12's last row).
11. **The tab menu reads Rename Tab (terminal tabs), then Move to New Window in the main window or Move to Main Window in a
    child window, then Close Tab, Close Others, Close Group and the content's commands; File reads New Tab, New Terminal
    Window, Open File…, Close Tab, Close Group, Close Window, Settings, Reload Config, Quit; the moves are on the tab menu
    and in the palette, not in the menu bar; the toolbar's New Window button becomes New Terminal Window** — spec 11's tab
    menu ("or") and 6.5.
12. **Close Window (`close_window`, ⇧⌘W on macOS, none elsewhere) closes the focused window as its title bar does: a child
    window after its tabs' guards, the main window after every window's tabs' guards, with its child windows** — spec 6.4
    and 11.
13. **The registry's factory, detached hosts, `activated` and `move(Tab, boolean)` go; the window manager gives the
    registry `TabRegistry.Windows(main, isMain, newWindow)`, and without it (a test) the first registered workspace is the
    main window and nothing moves to a new window. Until plan 4b the SDK's `ContentTab.move(true)` is Move Tab to New
    Window, `move(false)` Move Tab to Main Window, and `isSeparate()` is "not in the main window"** — spec 16 removes the
    factory and detached windows; plan 4b replaces the SDK's binary model.
14. **An operating-system notification's click raises the window that holds the notification's pane when the click
    arrives (`CommandNotifier.producer`, then the terminal registry's pane, tab and window; `SystemNotices.Windows.holder`),
    else its target; the Notifications panel then shows in the main window, which comes forward when the click raised
    another** — plan 3a's hand-off (its Rulings 18); the panel is the main window's (Rulings 6).
15. **Closing the main window asks every window's tabs' guards (`TabRegistry.mayClose` over every window's tabs); Quit
    and going to the background keep today's guards (plugin tabs, plugin windows, quit guards); then every child window
    closes, then the main window, and residency decides through the closed hook as today** — spec 6.4; terminal tabs have
    no guard (3a Rulings 4).
16. **The warm-up rehearsal builds a main window's content and a `WindowFrame` itself and never a `MainWindow`: the
    window manager never hears of it** — it must stay invisible to everything (`docs/app-maintenance.md`).
17. **The benchmark's window is the main window (`ensureMain("menu", directory)`), and its window cycles open and close a
    child window (`openTerminalWindow`, `requestClose`)** — they measured opening and closing a window.
18. **A child window opens at the configured grid's size (`window.columns` × `window.lines`) where the platform places it;
    an empty one (a moved tab's) sizes its workspace to that grid; only the main window restores saved bounds** — tear-out
    placement is plan 5's (spec 12).
19. **`JasperApplication.frames` is a package-private test seam for the native frame, as `restartPlanner` and `spawner`
    are; `JasperApplicationWindowsTest` runs the application's windows with stand-in shells** — headless tests cannot
    build a `JFrame` (fact 13).
20. **`open(paths, trigger)` with the main window open raises the main window, not a child window used last** — spec 5.4:
    files and directories open in the main window's active group.
21. **New files take ledger rows `nonvisual-helper,none,none` with "New in workspace-tabs plan 4a Task N"; the renamed
    `TerminalWindow` keeps its row under `WindowFrame`** — plan 3a Rulings 24's convention.
22. **"Jasper window" replaces "terminal window" in the application's comments and user-facing text, the plugins'
    comments and messages, and the user guides; where only the main window has the thing (the Buddy's visibility, the
    classic menu row, the toolbar, the notification card layer) the text names the main window; New Terminal Window keeps
    its name. The shortcut help's Global row reads "Anywhere in a Jasper window", and Remote's failures "Open a Jasper
    window to connect" and "… needs an open Jasper window for the sign-in prompt". The comment on `KeyContext.WINDOW`'s
    case in `WindowContent.applies` goes with that case in plan 4b** — plan 3b's Rulings 11 left the application's
    wording to this plan; spec 14's wording row; spec 6.5 keeps the command's name.
23. **A move reports itself before the window it emptied closes: `WindowContent.detach` skips `onEmpty` for a moving
    tab, and `TabRegistry.Windows.movedOut` lets the window manager close a child window the move left empty once the
    move has finished, so plugins hear `TAB_MOVED`, then that window's `WINDOW_CLOSED`, and a failed attach can go back
    to the window it left; every move takes this path (the moves, the SDK's `ContentTab.move`, plan 5's drag through
    `TabRegistry.move`). The SDK's `TabMoved` Javadoc, its README, both fakes and the contract test follow, the reverse of
    plan 3b's order** — a window must not close in the middle of a move.
24. **A plugin's tab asked for while the main window is being composed (a panel factory, a `WINDOW_OPENED` listener)
    opens there once it is up: `TabRegistry.Windows.main` gives null while the main window is composed, and the registry
    attaches the waiting tab on a later EDT turn; `ensureMain` still refuses a nested call** — spec 6.4: the main window
    is restored first, "then the request runs".
25. **`ensureMain` never treats a closing main window as absent: while the main window closes it returns empty, so a
    request made then (a closing tab's listener, say) opens no second main window** — spec 6.1.
26. **A child window's workspace sits in the same `ToolWindowArea` centre surface as the main window's, with no dock or
    stripes beside it, so it is glass as the main window's is** — spec 6.3: "the workspace has the same translucency as
    the main window's".
27. **Coming back to any window shows the notification cards that waited for focus, in the main window** — the cards are
    the main window's (Rulings 6), and a child window is attention too.
28. **For plan 6, `WindowManager.Hooks` has `closing` (a window is about to close, its content still whole) and
    `closingAll` (once, before `closeAll` closes any window: closing the main window, Quit, going to the background,
    `quitNow`); the application passes no-ops until plan 6 saves there** — spec 1 decision 3: the layout is saved with
    the child windows.
29. **For plan 5, `Opening.bounds` and `moveToNewWindow(Tab, Rectangle)` open a torn-out tab's child window at screen
    bounds through `WindowShell.place`, before it shows; `windowsByActivation()` lists the open windows, the one that
    came to the front last first** — spec 12's tear-out and drops on overlapping windows.

## File structure

- `dev.jasper.app.workspace`: `WindowRole`, `EmptyWorkspace` (Task 1); `WindowManager`, `JasperWindow`, `MainWindow`,
  `ChildWindow`, `WindowShell` (Task 2); `WindowFrame` (renamed from `TerminalWindow`, Task 3); `WindowContent`,
  `WorkspaceView`, `WindowContributions`, `WindowCommands`, `WorkspaceActions` (Tasks 1, 3, 4, 5); `WindowChrome` (Task
  5). `WindowCallbacks` deleted (Task 3).
- `dev.jasper.app.contributions`: `Contributions.panelRoute` (Task 2); `TabRegistry`, `Tab`, `WorkspaceHost` (Task 4).
- `dev.jasper.app.application`: `JasperApplication`, `Rehearsal`, `OpenPlan`, `SystemNotices` (Task 3; Task 5's tray
  rename); `dev.jasper.app.notifications.CommandNotifier` (Task 3); `dev.jasper.app.benchmark.BenchmarkRun` (Task 3);
  `dev.jasper.app.plugins.HostedUi` (Task 4); `dev.jasper.app.commands.ActionId`, `palette.CommandsScope`,
  `shortcuthelp.ShortcutCatalog`, `platform.ResidentTray`, `windows.NativeShells` (Task 5).
- `gradle/application-architecture.gradle.kts` (Task 2).
- Tests: new `WindowRolesTest` (Task 1), `WindowManagerTest` (Task 2), `JasperApplicationWindowsTest` (Task 3); changed
  `DesktopTestSupport`, `SystemNoticesTest`, `CommandNotifierTest`, `WindowShellTest`, `JasperApplicationBackgroundTest`,
  `WindowCommandPaletteTest`, `ApplicationTestSupport` (Task 3), `TabRegistryTest`, `AppContractTest`, `KitMountingTest`,
  `StagedEditorHost`, `WorkspaceContentRouteTest`, `HostedChooserTest`, `HostedContentTest`, `PluginPaneAppearanceTest`,
  `WorkspaceThemeTest`, `RuledCellsTest` (Task 4), `KeymapDefaultsTest`, `KeyBindingsTest`, `KeyBindingsContextTest`,
  `ApplicationActionsTest`, `TabMenuTest`, `WindowChromeTest`, `ResidentTrayTest`, `ResidentIndicatorTest`,
  `ShellProfilePaneTest`, `Settings9bPreview` (Task 5).
- Wording (Task 6): comments in 16 application files and 6 plugin files, `ShortcutCatalog`'s Global row, Remote's two
  messages and their tests, `docs/app-maintenance.md`, `docs/configuration.md`, `docs/editor.md`,
  `docs/editor-verification.md`, `docs/keyboard-shortcuts.md`, `docs/remote.md`.
- Documents (Task 7): `README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/configuration.md`,
  `docs/packaging.md`, `docs/sdk-architecture.md`, `docs/STATUS.md`; the ledger in Tasks 1-3.

## Before you start

```bash
git branch --show-current                                                                         # claude/workspace-tabs
git status --short                                                                                # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for the SDK's tab break"   # 3b's last commit
grep -c 'VERSION = "0.19.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                                       # 1
grep -c 'public void move(Tab tab, UUID window)' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java   # 1
grep -c 'public void setFactory(Function<Boolean, UUID> factory)' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
grep -c 'public Optional<UUID> movingFrom()' jasper-app/src/main/java/dev/jasper/app/contributions/Tab.java                # 1
grep -c 'terminals.tabArrived(tab, tab.movingFrom()); announced = true;' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'Layout model() { return layout; }' jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java          # 1
grep -c 'public TerminalWindow openFirstWindow(List<Path> paths)' jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java  # 1
grep -c 'contributions.tabs().setFactory(separate -> {' jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java   # 2
```

Every command prints the stated result. Plans 1, 2, 3a and 3b each added a `docs/STATUS.md` entry; this plan's goes
above them. Create the two helper scripts (Global Constraints).

---

### Task 1: The main window's and a child window's content, and the empty main workspace

Spec 6.2, 6.3 and 10: a window's content has a role. The main window's shows Jasper's chrome around its workspace and,
once its last tab has closed, New Terminal Tab and Open File; a child window's shows its workspace alone, with the same
actions, menus, keymap and palette, and docks nothing (Rulings 1, 8). Nothing creates a child window yet; Task 2 does.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowRole.java`, `EmptyWorkspace.java`
- Modify: `workspace/WindowContent.java`, `WorkspaceView.java`, `WindowContributions.java`, `WindowCommands.java`,
  `WorkspaceActions.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowRolesTest.java`; `DesktopTestSupport.java`
- Ledger: two rows

**Interfaces:**
- Consumes: 3a's `WorkspaceView.render`, `WindowContent.detach` (runs `onEmpty`), `WindowContributions`.
- Produces: `public enum WindowRole { MAIN, CHILD }`; `public WindowContent(WindowRole role, ShellLauncher launcher, Path
  directory, boolean empty, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty, ThemeController themes, KeyBindings
  bindings, CommandHistory history, boolean macOs, WorkspaceChrome look)` (the other constructors are the main window's);
  `public WindowRole role()`, `public boolean isMain()`, package `String shortcutText(ActionId)`; `final class
  EmptyWorkspace extends JPanel` with `void refresh()`, `JButton button(ActionId)`, `JLabel shortcut(ActionId)`;
  `WorkspaceView.emptyWorkspace()` (the empty state while it shows, else null); test helper
  `DesktopTestSupport.content(ShellLauncher, WindowRole, Path directory, boolean empty, Runnable emptied)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowRolesTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 6.2, 6.3 and 10: the main window's chrome and its empty workspace; a child window's workspace alone. */
class WindowRolesTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    @Test void aChildWindowShowsItsWorkspaceAndNoChrome() throws Exception {
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            assertThat(child.role()).isEqualTo(WindowRole.CHILD);
            assertThat(child.isMain()).isFalse();
            assertThat(child.getComponentCount()).as("its workspace and nothing else").isEqualTo(1);
            assertThat(SwingUtilities.isDescendingFrom(child.workspace(), child)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(child.chrome().header(), child)).as("no header or toolbar").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(child.status(), child)).as("no status bar").isFalse();
            assertThat(child.area().getParent()).as("the centre surface alone: no dock or stripes around it").isSameAs(child);
            assertThat(SwingUtilities.isDescendingFrom(child.workspace(), child.area())).as("as the main window's").isTrue();
            assertThat(SwingUtilities.isDescendingFrom(child.leftStripe(), child)).as("no stripes").isFalse();
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            assertThat(main.role()).isEqualTo(WindowRole.MAIN);
            assertThat(SwingUtilities.isDescendingFrom(main.chrome().header(), main)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(main.status(), main)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(main.workspace(), main.area())).as("the dock's centre").isTrue();
        });
        launch();
    }

    /** Workspace tabs spec 6.3: a child window's workspace has the same translucency as the main window's. */
    @Test void aChildWindowsWorkspaceIsGlassAsTheMainWindowsIs() throws Exception {
        edt(() -> {
            // An empty child window: nothing but the centre surface paints the middle of its workspace.
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, true, () -> { });
            child.setSize(900, 600);
            LayoutTestSupport.layoutTree(child);
            LayoutTestSupport.layoutTree(child);
            var workspace = child.workspace();
            assertThat(workspace.getWidth()).as("the workspace is laid out").isPositive();
            Point middle = SwingUtilities.convertPoint(workspace, workspace.getWidth() / 2, workspace.getHeight() / 2, child);
            assertThat(alpha(child, middle)).as("opaque").isEqualTo(255);
            child.applyBackgroundOpacity(0.5);
            assertThat(alpha(child, middle)).as("glass of the terminal background, as in the main window").isEqualTo(128);
            child.applyBackgroundOpacity(1);
            assertThat(alpha(child, middle)).isEqualTo(255);
        });
    }

    private static int alpha(JComponent root, Point at) {
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { root.paint(graphics); } finally { graphics.dispose(); }
        return new Color(image.getRGB(at.x, at.y), true).getAlpha();
    }

    @Test void aChildWindowTakesTheKeymapAndThePalette() throws Exception {
        WindowContent[] child = new WindowContent[1];
        edt(() -> child[0] = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { }));
        launch();
        edt(() -> {
            var root = WindowCommandPaletteTest.install(child[0]);
            KeyStroke newTab = child[0].bindings().strokeFor(ActionId.NEW_TAB).orElseThrow();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(newTab)).as("the Keymap on the child's own root")
                .isEqualTo(ActionId.NEW_TAB.id());
            assertThat(child[0].dispatchShortcut(newTab, child[0])).isTrue();
            assertThat(child[0].tabCount()).isEqualTo(2);
            child[0].commandPalette().toggle();
            assertThat(child[0].commandPalette().isOpen()).as("the palette opens over the child window").isTrue();
            child[0].commandPalette().dismiss();
        });
        launch();
    }

    @Test void aChildWindowLeavesTheMainWindowsChromeAlone() throws Exception {
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            for (var window : java.util.List.of(child, main)) {
                window.updateActions();
                boolean expected = window.isMain();
                assertThat(window.action(ActionId.TOGGLE_ZEN_MODE).isEnabled()).as("Zen").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.status_bar").isEnabled()).as("Status Bar").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.rail").isEnabled()).as("Rail").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.toolbar.icons").isEnabled()).as("Toolbar").isEqualTo(expected);
                assertThat(window.action(ActionId.NEW_TAB).isEnabled()).as("its own tabs").isTrue();
            }
        });
        launch();
    }

    @Test void aChildWindowBuildsNoToolbarViewsAndDocksNoPanels() throws Exception {
        edt(() -> {
            var model = new Contributions();
            int[] asked = {0};
            model.addToolbar(new ToolbarEntry.View(window -> { asked[0]++; return Optional.empty(); }));
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            child.connectContributions(model);
            child.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(asked[0]).as("no toolbar, no toolbar views").isZero();
            assertThat(child.dock().slot("test.panel")).as("panels are the main window's").isEmpty();
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            main.connectContributions(model);
            main.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(asked[0]).isEqualTo(1);
            assertThat(main.dock().slot("test.panel")).contains(Slot.LEFT_TOP);
        });
        launch();
    }

    @Test void theMainWorkspaceCanBeEmptyAndOffersANewTerminalTabAndOpenFile() throws Exception {
        WindowContent[] main = new WindowContent[1];
        int[] emptied = {0};
        edt(() -> main[0] = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> emptied[0]++));
        launch();
        edt(() -> {
            main[0].closeTab(main[0].selectedTab());
            assertThat(main[0].tabCount()).isZero();
            assertThat(main[0].closed()).as("the empty workspace stays").isFalse();
            assertThat(emptied[0]).as("its owner hears it").isEqualTo(1);
            EmptyWorkspace empty = main[0].workspace().emptyWorkspace();
            assertThat(empty).as("New Terminal Tab and Open File show").isNotNull();
            assertThat(SwingUtilities.isDescendingFrom(empty, main[0].workspace())).isTrue();
            assertThat(main[0].workspace().groupViews()).allSatisfy(group ->
                assertThat(SwingUtilities.isDescendingFrom(group, main[0].workspace())).as("no empty strip").isFalse());
            assertThat(empty.button(ActionId.NEW_TAB).getText()).isEqualTo("New Terminal Tab");
            assertThat(empty.shortcut(ActionId.NEW_TAB).getText()).isEqualTo(main[0].shortcutText(ActionId.NEW_TAB)).isNotEmpty();
            assertThat(empty.button(ActionId.OPEN_FILE).getText()).isEqualTo("Open File…");
            assertThat(empty.shortcut(ActionId.OPEN_FILE).isVisible()).as("Open File has no default shortcut").isFalse();
            empty.button(ActionId.NEW_TAB).doClick();
            assertThat(main[0].tabCount()).isEqualTo(1);
            assertThat(main[0].workspace().emptyWorkspace()).as("a tab takes its place").isNull();
        });
        launch();
    }

    @Test void aChildWindowIsNeverEmpty() throws Exception {
        WindowContent[] child = new WindowContent[1];
        int[] emptied = {0};
        edt(() -> child[0] = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> emptied[0]++));
        launch();
        edt(() -> {
            child[0].closeTab(child[0].selectedTab());
            assertThat(emptied[0]).as("its last tab closed: its window closes it").isEqualTo(1);
            assertThat(child[0].workspace().emptyWorkspace()).as("no empty state in a child window").isNull();
        });
    }
}
```

Add the helper to `DesktopTestSupport`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java", [(
"""    /** Closes the test windows now, on the EDT: before a test closes the theme controller its windows are bound to. */
""", """    /**
     * A window in {@code role} with this host's keymap: its first tab starts in {@code directory} (null: where a new shell
     * starts) unless it is {@code empty}, and {@code emptied} runs when its last tab has closed or moved out.
     */
    public static WindowContent content(ShellLauncher launcher, WindowRole role, Path directory, boolean empty, Runnable emptied) {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        WindowContent owner = new WindowContent(role, launcher, directory, empty, path -> {}, () -> {}, emptied, ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD);
        OWNERS.add(owner);
        return owner;
    }
    /** Closes the test windows now, on the EDT: before a test closes the theme controller its windows are bound to. */
""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `WindowRole`, `role()`, `isMain()`, `emptyWorkspace()`, `EmptyWorkspace`,
`shortcutText`.

- [ ] **Step 3: The role, the child window's content and the empty state**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowRole.java`:

```java
package dev.jasper.app.workspace;

/**
 * Which of Jasper's windows a window is (workspace tabs spec 6): the one main window, which has all of Jasper's chrome
 * around its workspace, or a child window, which has a title bar and a workspace and nothing else.
 */
public enum WindowRole {
    /** The main window: header and toolbar, menus, the tool window dock and stripes, the status bar and the notification layer. */
    MAIN,
    /** A child window: a title bar and a workspace. */
    CHILD
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/EmptyWorkspace.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * What the main window's workspace shows once its last tab has closed (workspace tabs spec 1 decision 9, spec 10): New
 * Terminal Tab and Open File, each with its current shortcut, as VS Code's and IntelliJ's empty editor areas do. A click
 * runs the window's action. A child window is never empty: it closes with its last tab. EDT only.
 */
final class EmptyWorkspace extends JPanel {
    /** One command: the button that runs it and the caption that shows its shortcut. */
    private record Row(ActionId id, JButton button, JLabel shortcut) { }

    private final WindowContent owner;
    private final List<Row> rows = new ArrayList<>();

    EmptyWorkspace(WindowContent owner) {
        super(new GridBagLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("workspace.empty");
        setOpaque(false);
        addRow(ActionId.NEW_TAB, "New Terminal Tab");
        addRow(ActionId.OPEN_FILE, "Open File…");
        refresh();
    }

    private void addRow(ActionId id, String label) {
        var button = new JButton(label);
        button.setName("workspace.empty." + id.id());
        button.setFocusable(false);
        button.getAccessibleContext().setAccessibleName(label);
        NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        button.addActionListener(event -> owner.invoke(id));
        var shortcut = new JLabel();
        shortcut.setName("workspace.empty." + id.id() + ".shortcut");
        NativeStyles.installed().label(shortcut, Typography.Role.CAPTION, SemanticColor.TEXT_PLACEHOLDER, () -> ThemeZone.theme(shortcut));
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        var place = new GridBagConstraints();
        place.gridy = rows.size();
        place.anchor = GridBagConstraints.LINE_END;
        place.insets = new Insets(gap / 2, 0, gap / 2, gap);
        add(button, place);
        place = new GridBagConstraints();
        place.gridy = rows.size();
        place.anchor = GridBagConstraints.LINE_START;
        place.insets = new Insets(gap / 2, 0, gap / 2, 0);
        add(shortcut, place);
        rows.add(new Row(id, button, shortcut));
    }

    /** Shows each command's shortcut as the keymap binds it now; a command with none shows no caption. */
    void refresh() {
        for (Row row : rows) {
            String text = owner.shortcutText(row.id());
            row.shortcut().setText(text);
            row.shortcut().setVisible(!text.isEmpty());
            row.button().setEnabled(owner.action(row.id()).isEnabled());
        }
    }

    /** The button that runs {@code id}, for tests. */
    JButton button(ActionId id) { return rows.stream().filter(row -> row.id() == id).findFirst().orElseThrow().button(); }

    /** The caption showing {@code id}'s shortcut, for tests. */
    JLabel shortcut(ActionId id) { return rows.stream().filter(row -> row.id() == id).findFirst().orElseThrow().shortcut(); }
}
```

Then the edits (3a's text; `detached` becomes `empty`, the window starting with no tab):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowContent.java", [
    ("""    private Runnable bringToFront = () -> {};
    private final boolean detached;
""", """    private Runnable bringToFront = () -> {};
    /** The main window, with Jasper's chrome, or a child window, a workspace alone (workspace tabs spec 6). */
    private final WindowRole role;
""", 1),
    ("""    /**
     * A {@code detached} window starts with no tab and holds detached content; any other starts one tab in
     * {@code directory}, where null means where a new shell starts. {@code newWindow} receives null the same way.
     */
    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, detached, newWindow, quit, onEmpty, themes, bindings, history, macOs, WorkspaceChrome.STANDARD);
    }

    /** As above, under the look's workspace chrome (Enterprise presentation spec 9.1). */
    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look) {
        super(new BorderLayout());
        this.look = Objects.requireNonNull(look, "look");
        this.detached = detached;
""", """    /**
     * An {@code empty} window starts with no tab; any other starts one tab in {@code directory}, where null means where a
     * new shell starts. {@code newWindow} receives null the same way.
     */
    WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, empty, newWindow, quit, onEmpty, themes, bindings, history, macOs, WorkspaceChrome.STANDARD);
    }

    /** As above, as the main window, under the look's workspace chrome (Enterprise presentation spec 9.1). */
    WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look) {
        this(WindowRole.MAIN, launcher, directory, empty, newWindow, quit, onEmpty, themes, bindings, history, macOs, look);
    }

    /**
     * A window's content in {@code role} (workspace tabs spec 6): the main window's shows Jasper's chrome around its
     * workspace; a child window's shows its workspace alone, with the same actions, menus, keymap and palette, and docks
     * no tool window.
     *
     * @param role the main window or a child window
     * @param launcher starts the window's shells
     * @param directory where the first tab starts, or null for where a new shell starts
     * @param empty whether the window starts without a tab
     * @param newWindow opens a window with a terminal tab in a directory, or where a new shell starts when it is null
     * @param quit quits Jasper
     * @param onEmpty runs when the window's last tab has closed or moved out
     * @param themes the application's look
     * @param bindings the keymap until a configuration arrives
     * @param history the shared command history
     * @param macOs whether the keymap and shortcut text are macOS's
     * @param look the workspace's chrome under the look
     */
    public WindowContent(WindowRole role, ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow,
                         Runnable quit, Runnable onEmpty, ThemeController themes, KeyBindings bindings, CommandHistory history,
                         boolean macOs, WorkspaceChrome look) {
        super(new BorderLayout());
        this.role = Objects.requireNonNull(role, "role");
        this.look = Objects.requireNonNull(look, "look");
""", 1),
    ("""        add(chrome.header(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH);
""", """        // A child window shows its workspace alone, in the centre surface that paints the main window's glass, with no dock,
        // stripes or chrome around it; its chrome serves its menus and actions and is never shown (spec 6.3).
        if (role == WindowRole.MAIN) { add(chrome.header(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH); }
        else add(area, BorderLayout.CENTER);
""", 1),
    ("""        if (!detached) newTab(directory);
""", """        if (!empty) newTab(directory);
""", 1),
    ("""        boolean left = railsEnabled() && !leftStripe.empty(), right = railsEnabled() && !rightStripe.empty();
        boolean bottom = stripes.bottom().map(strip -> railsEnabled() && !strip.empty()).orElse(false);
""", """        // A child window has no stripes: its workspace fills the centre surface edge to edge (workspace tabs spec 6.3).
        boolean rails = isMain() && railsEnabled();
        boolean left = rails && !leftStripe.empty(), right = rails && !rightStripe.empty();
        boolean bottom = stripes.bottom().map(strip -> rails && !strip.empty()).orElse(false);
""", 1),
    ("""    public UUID id() { return id; }
""", """    public UUID id() { return id; }

    /** @return whether this is the main window or a child window (workspace tabs spec 6) */
    public WindowRole role() { return role; }

    /** @return whether this is the main window, which has Jasper's chrome and dock and whose workspace may be empty */
    public boolean isMain() { return role == WindowRole.MAIN; }
""", 1),
    ("""        chrome.windowOpened();
""", """        // A child window has no toolbar, so it builds no toolbar views (workspace tabs spec 6.3).
        if (role == WindowRole.MAIN) chrome.windowOpened();
""", 1),
    ("""        setZenMode(state.zenMode());
""", """        if (role == WindowRole.MAIN) setZenMode(state.zenMode());
""", 1),
    ("new WorkspaceHost(id, detached,", "new WorkspaceHost(id, role == WindowRole.CHILD,", 1),
    ("""    String tabShortcut(int index) {
""", """    /** {@code id}'s shortcut as this platform writes it, from the live keymap; "" while it has none. */
    String shortcutText(ActionId id) {
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    String tabShortcut(int index) {
""", 1)])
edit(W + "WorkspaceView.java", [
    ("""    private Object shape;
""", """    private Object shape;
    /** The shape of the main window's empty workspace, which shows {@link #empty} (workspace tabs spec 10). */
    private static final Object EMPTY = new Object();
    /** The main window's empty state, made the first time its workspace is empty. */
    private EmptyWorkspace empty;
""", 1),
    ("""        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        Object next = shape(shown);
        if (!next.equals(shape)) {
            shape = next;
            splits.clear();
            removeAll();
            add(build(shown, List.of()), BorderLayout.CENTER);
            revalidate();
""", """        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        // The main window's empty workspace shows New Terminal Tab and Open File in place of its empty group (spec 10).
        boolean showEmpty = layout.isEmpty() && owner.isMain();
        Object next = showEmpty ? EMPTY : shape(shown);
        if (!next.equals(shape)) {
            shape = next;
            splits.clear();
            removeAll();
            add(showEmpty ? emptyState() : build(shown, List.of()), BorderLayout.CENTER);
            revalidate();
""", 1),
    ("""    /** The tree's shape without its weights: what a rebuild depends on. */
""", """    private EmptyWorkspace emptyState() {
        if (empty == null) empty = new EmptyWorkspace(owner);
        empty.refresh();
        return empty;
    }

    /** The main window's empty state while it shows; null otherwise. */
    EmptyWorkspace emptyWorkspace() { return shape == EMPTY ? empty : null; }

    /** The tree's shape without its weights: what a rebuild depends on. */
""", 1),
    ("""    void refresh() { if (!closed) for (GroupView group : groups.values()) group.refresh(); }
""", """    void refresh() {
        if (closed) return;
        for (GroupView group : groups.values()) group.refresh();
        if (empty != null) empty.refresh();
    }
""", 1)])
edit(W + "WindowContributions.java", [
    ("""    private Subscription panelRequests;
""", """    private Subscription panelRequests;
    /** Whether this window docks panels and shows status entries: the main window does, a child window does not. */
    private final boolean docks;
""", 1),
    ("""        if (owner.zenMode()) beforeZen = new LinkedHashSet<>();
        syncActions();
        owner.chrome().connect(this);
        renderStatus();
        restoreSizes();
        for (DockStripe stripe : owner.stripes().all()) {
            stripe.onToggle = this::togglePanel;
            stripe.onMove = this::movePanel;
            stripe.onHide = this::hidePanel;
        }
        owner.area().onMove = this::movePanel;
        owner.area().onHide = this::hidePanel;
        owner.dockDrag().onDrop = this::dropped;
        owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;
        owner.dockDrag().labels = this::stripeLabel;
        dock.addListener(docked);
        panelRequests = model.onPanelRequest(this::requested);
        paletteRequests = model.onPaletteRequest(this::requestedPalette);
        syncScopes();
        syncPanels();
        renderStripes();
        listening = model.onChanged(this::changed);
""", """        if (owner.zenMode()) beforeZen = new LinkedHashSet<>();
        // A child window takes the actions, the menus and the palette scopes; panels and status entries are the main
        // window's (workspace tabs spec 6.3).
        docks = owner.isMain();
        syncActions();
        owner.chrome().connect(this);
        if (docks) {
            renderStatus();
            restoreSizes();
            for (DockStripe stripe : owner.stripes().all()) {
                stripe.onToggle = this::togglePanel;
                stripe.onMove = this::movePanel;
                stripe.onHide = this::hidePanel;
            }
            owner.area().onMove = this::movePanel;
            owner.area().onHide = this::hidePanel;
            owner.dockDrag().onDrop = this::dropped;
            owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;
            owner.dockDrag().labels = this::stripeLabel;
            dock.addListener(docked);
            panelRequests = model.onPanelRequest(this::requested);
        }
        paletteRequests = model.onPaletteRequest(this::requestedPalette);
        syncScopes();
        if (docks) {
            syncPanels();
            renderStripes();
        }
        listening = model.onChanged(this::changed);
""", 1),
    ("""    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                boolean structural = syncActions();
                if (structural) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
""", """    private void changed(Contributions.Kind kind) {
        if (!docks && (kind == Contributions.Kind.PANELS || kind == Contributions.Kind.RAIL || kind == Contributions.Kind.BADGES
            || kind == Contributions.Kind.STATUS)) return;
        switch (kind) {
            case ACTIONS -> {
                boolean structural = syncActions();
                if (structural) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                if (!docks) return;
                renderStatus();
""", 1),
    ("""        if (remember) persist();
""", """        if (remember && docks) persist();
""", 1)])
edit(W + "WindowCommands.java", [
    ("""        view("view.status_bar").setEnabled(!zen);
""", """        // A child window has no toolbar, status bar, rail or Zen of its own (workspace tabs spec 6.3).
        boolean main = owner.isMain();
        view("view.status_bar").setEnabled(main && !zen);
""", 1),
    ("""        view("view.rail").setEnabled(!zen);
""", """        view("view.rail").setEnabled(main && !zen);
""", 1),
    ("""            action.putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
""", """            action.putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
            action.setEnabled(main);
""", 1)])
edit(W + "WorkspaceActions.java", [
    ("case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, TOGGLE_ZEN_MODE, SPLIT_RIGHT, SPLIT_DOWN -> true;",
     "case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT, SPLIT_DOWN -> true;\n"
     "                    case TOGGLE_ZEN_MODE -> owner.isMain();", 1)])
PY
```

The `WorkspaceHost` keeps its `detached` flag for now, true for a child window (Task 4 removes the flag).

- [ ] **Step 4: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "EmptyWorkspace.java", "app.workspace", "EmptyWorkspace", T + "WindowRolesTest.java",
           "New in workspace-tabs plan 4a Task 1; the main window's empty workspace")
ledger.add(T + "WindowRolesTest.java", "app.workspace", "WindowRolesTest", T + "WindowRolesTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 4a Task 1; main and child windows' content")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WindowRolesTest` 7; every other workspace test unchanged: their windows are main windows). Then
`./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Windows: main and child window content, and the empty main workspace

A window's content has a role. The main window's keeps Jasper's chrome and,
once its last tab closes, shows New Terminal Tab and Open File with their
shortcuts; a child window's shows its workspace alone, with the same
actions, menus, keymap and palette, no dock and no toolbar views.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The window manager

Spec 6.1 and 6.4: at most one main window, any number of child windows; the main window made in one place and only
when none is open; a child window closes with its last tab; closing the main window asks every window's tabs' guards,
then closes the child windows and the main window; a plugin's view with no window, and a child window's panel requests,
go to the main window (Rulings 2-5, 8, 10, 15). Headless: the frame is a `WindowShell` the application gives (Task 3).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowShell.java`, `JasperWindow.java`, `MainWindow.java`,
  `ChildWindow.java`, `WindowManager.java`
- Modify: `contributions/Contributions.java` (`panelRoute`), `gradle/application-architecture.gradle.kts`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowManagerTest.java`
- Ledger: four rows

**Interfaces:**
- Consumes: Task 1's `WindowRole`, `WindowContent(WindowRole, …)`, `isMain()`, `emptyWorkspace()`; 3a's
  `TabRegistry.move(Tab, UUID)`, `inWindow`, `mayClose(Supplier)`, `setFactory`; `WindowContent.tabCount()`,
  `setActive`, `mayClose()`, `forgetLayout()`, `focusSelected()`.
- Produces: `public record WindowShell(Runnable show, Runnable toFront, Runnable dispose, Runnable discard,
  Supplier<java.awt.Window> window, Predicate<Component> owns, Consumer<Rectangle> place)` (and the six-function form,
  `place` doing nothing); `public abstract sealed class JasperWindow permits
  MainWindow, ChildWindow` with `public record Events(Runnable closing, Consumer<Boolean> activated, BiConsumer<Boolean,
  Boolean> state)` (and `Events.NONE`), `content()`, `id()`, `role()`, `isMain()`, `closed()`, `toFront()`,
  `nativeWindow()`, `owns(Component)`, `requestClose()`, `events()`; `public final class MainWindow` (package
  constructor), `public final class ChildWindow`; `public final class WindowManager` with `record Opening(WindowRole role,
  boolean empty, Path directory, String trigger, Runnable emptied, Optional<Rectangle> bounds)`, `record State(JasperWindow
  window, boolean showing, boolean iconified)`, `record Hooks(Function<Opening, WindowContent> content,
  Function<JasperWindow, WindowShell> frames, Consumer<JasperWindow> opened, Consumer<JasperWindow> activated,
  Consumer<JasperWindow> closed, Consumer<State> state, Consumer<JasperWindow> closing, Runnable closingAll)`,
  `WindowManager(Contributions, Hooks)`, `Optional<MainWindow> main()`, `List<ChildWindow> children()`,
  `List<JasperWindow> windows()`, `List<JasperWindow> windowsByActivation()`, `boolean isEmpty()`, `int opening()`,
  `Optional<JasperWindow> window(UUID)`, `Optional<JasperWindow> lastActive()`, `Optional<MainWindow> ensureMain(String
  trigger, Path directory)` (empty while the main window closes), `Optional<ChildWindow> openTerminalWindow(Path)`,
  `Optional<ChildWindow> moveToNewWindow(Tab)` and `moveToNewWindow(Tab, Rectangle bounds)`, `void moveToMainWindow(Tab)`,
  `void closeAll()` (the closing-all hook first); `Contributions.panelRoute(UnaryOperator<PanelRequest>)`. For plan 5:
  the bounds and `windowsByActivation()`; an empty main workspace shows `EmptyWorkspace` and no `GroupView`, so plan 5's
  drop resolver maps that surface to the empty group's centre. For plan 6: `Hooks.closing` and `closingAll`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowManagerTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 6.1, 6.4, 6.5 and 17: one main window, child windows and their lifecycle, headless. */
class WindowManagerTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final List<Path> started = new ArrayList<>();
    /** What the stand-in frames did and what the application heard, in order. */
    private final List<String> events = new ArrayList<>();
    /** What the closing hooks heard, with whether the window's content was still whole. */
    private final List<String> closings = new ArrayList<>();
    private boolean quitting, failFrames, reenter;
    /** The tab a plugin asked for while the main window was being composed. */
    private Tab early;
    private WindowManager manager;

    @BeforeEach void manager() throws Exception {
        edt(() -> manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> events.add("closed " + name(window)), state -> { },
            window -> closings.add("closing " + name(window) + (window.content().closed() ? " (closed)" : "")),
            () -> closings.add("closing all"))));
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> manager.closeAll());
        closeOwners();
    }

    private static String name(JasperWindow window) { return window.isMain() ? "main" : "child"; }

    private WindowContent compose(WindowManager.Opening opening) {
        if (quitting) return null;
        // A plugin opening its view while the main window is still being composed (a panel factory, say).
        if (reenter) { reenter = false; early = model.tabs().open("dev.example.editor", "early", "early.txt", null, new JPanel()); }
        var launcher = new ShellLauncher(pending::add, directory -> { started.add(directory); return IdleTerminalSession.start(); },
            "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        return content;
    }

    private WindowShell frame(JasperWindow window) {
        if (failFrames) throw new IllegalStateException("no frame");
        String name = name(window);
        return new WindowShell(() -> events.add("show " + name), () -> events.add("front " + name), () -> events.add("dispose " + name),
            () -> events.add("discard " + name), () -> null, component -> SwingUtilities.isDescendingFrom(component, window.content()),
            bounds -> events.add("place " + name + " " + bounds.x + "," + bounds.y + " " + bounds.width + "x" + bounds.height));
    }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    @Test void theMainWindowIsMadeOnlyWhenNoneIsOpen() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            assertThat(manager.ensureMain("handoff", null)).containsSame(main);
            assertThat(manager.main()).containsSame(main);
            assertThat(manager.windows()).containsExactly(main);
            assertThat(main.role()).isEqualTo(WindowRole.MAIN);
            assertThat(main.content().isMain()).isTrue();
            assertThat(events).containsExactly("show main", "opened main");
            assertThat(manager.opening()).isZero();
        });
        launch();
    }

    @Test void theMainWindowsConstructorIsPackagePrivate() {
        assertThat(MainWindow.class.getDeclaredConstructors()).allSatisfy(constructor ->
            assertThat(Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())).isFalse());
    }

    @Test void aNewTerminalWindowIsAChildWindowInTheGivenDirectoryAfterTheMainWindow(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(directory).orElseThrow();
            assertThat(manager.main()).as("restored first").isPresent();
            assertThat(manager.windows()).containsExactly(manager.main().orElseThrow(), child);
            assertThat(child.role()).isEqualTo(WindowRole.CHILD);
            assertThat(child.content().isMain()).isFalse();
            assertThat(child.content().tabCount()).isEqualTo(1);
            assertThat(events).containsExactly("show main", "opened main", "show child", "opened child");
        });
        launch();
        assertThat(started).as("the main window's tab where a new shell starts, then the child's").containsExactly(HOME, directory);
    }

    @Test void aChildWindowClosesWhenItsLastTabCloses() throws Exception {
        ChildWindow[] child = new ChildWindow[1];
        edt(() -> child[0] = manager.openTerminalWindow(null).orElseThrow());
        launch();
        edt(() -> {
            events.clear();
            child[0].content().closeTab(child[0].content().selectedTab());
            assertThat(child[0].closed()).isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(events).containsExactly("dispose child", "closed child");
        });
    }

    @Test void theMainWindowStaysWithAnEmptyWorkspace() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> main[0] = manager.ensureMain("cold", null).orElseThrow());
        launch();
        edt(() -> {
            main[0].content().closeTab(main[0].content().selectedTab());
            assertThat(main[0].closed()).isFalse();
            assertThat(manager.main()).containsSame(main[0]);
            assertThat(main[0].content().workspace().emptyWorkspace()).isNotNull();
        });
    }

    @Test void closingTheMainWindowAsksEveryWindowsGuardsThenClosesTheChildWindowsFirst() throws Exception {
        boolean[] allow = {false};
        int[] asked = {0};
        ChildWindow[] child = new ChildWindow[1];
        Tab[] notes = new Tab[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            notes[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", child[0].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> { asked[0]++; return CompletableFuture.completedFuture(allow[0]); });
        });
        launch();
        edt(() -> {
            MainWindow main = manager.main().orElseThrow();
            events.clear();
            main.requestClose();
            assertThat(asked[0]).as("the child window's tab was asked").isEqualTo(1);
            assertThat(manager.windows()).as("a refusal closes nothing").containsExactly(main, child[0]);
            assertThat(events).isEmpty();
            allow[0] = true;
            main.requestClose();
            assertThat(manager.isEmpty()).isTrue();
            assertThat(notes[0].closed()).isTrue();
            assertThat(events).containsExactly("dispose child", "closed child", "dispose main", "closed main");
        });
    }

    @Test void aChildWindowClosedByItsUserClosesItsTabsAfterTheirGuards() throws Exception {
        boolean[] allow = {false};
        ChildWindow[] child = new ChildWindow[1];
        Tab[] notes = new Tab[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            notes[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", child[0].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(allow[0]));
        });
        launch();
        edt(() -> {
            child[0].requestClose();
            assertThat(child[0].closed()).as("an unsaved document keeps it open").isFalse();
            allow[0] = true;
            child[0].requestClose();
            assertThat(child[0].closed()).isTrue();
            assertThat(notes[0].closed()).isTrue();
            assertThat(manager.main()).as("the main window stays").isPresent();
        });
    }

    @Test void aTerminalTabMovedToANewWindowAndBackKeepsItsSession() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = manager.ensureMain("cold", null).orElseThrow();
            main[0].content().newTab(HOME);
        });
        launch();
        edt(() -> {
            Tab tab = main[0].content().selectedTab();
            var pane = ((TerminalContent) tab.content()).pane();
            var session = pane.session();
            assertThat(session).isNotNull();
            ChildWindow child = manager.moveToNewWindow(tab).orElseThrow();
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(child.content().selectedTab()).isSameAs(tab);
            assertThat(main[0].content().tabCount()).as("the main window keeps its other tab").isEqualTo(1);
            assertThat(manager.moveToNewWindow(tab)).as("a child window's only tab stays where it is").isEmpty();
            assertThat(manager.children()).containsExactly(child);
            manager.moveToMainWindow(tab);
            assertThat(tab.window()).isEqualTo(main[0].id());
            assertThat(child.closed()).as("emptied, it closed").isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(pane.session()).as("the same session throughout").isSameAs(session);
            assertThat(session.exitFuture()).isNotDone();
        });
    }

    @Test void aPluginsViewWithNoWindowOpensTheMainWindowFirst() throws Exception {
        edt(() -> {
            assertThat(manager.isEmpty()).isTrue();
            Tab tab = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(tab.window()).isEqualTo(main.id());
            manager.closeAll();
            assertThat(manager.isEmpty()).isTrue();
            MainWindow again = manager.ensureMain("reopen", null).orElseThrow();
            assertThat(again).as("a fresh main window").isNotSameAs(main);
        });
        launch();
    }

    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.SHOW));
            assertThat(main.content().dock().visible("test.panel")).as("the main window has the dock").isTrue();
            assertThat(events).as("and comes forward to show it").containsExactly("front main");
        });
        launch();
    }

    /** Workspace tabs spec 6.1: while the main window closes, a request for it opens no second one beside it. */
    @Test void aRequestWhileTheMainWindowClosesOpensNoSecondOne() throws Exception {
        List<Optional<MainWindow>> asked = new ArrayList<>();
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            notes.onClosed(() -> asked.add(manager.ensureMain("plugin", null)));
            manager.closeAll();
            assertThat(asked).as("a closing main window is not absent").containsExactly(Optional.empty());
            assertThat(manager.isEmpty()).isTrue();
            assertThat(events.stream().filter("opened main"::equals).count()).as("no second main window").isEqualTo(1);
        });
        launch();
    }

    /** For plan 6: the hooks hear of every window while it is whole, and of closing them all once before any closes. */
    @Test void theClosingHooksHearOfEachWindowWhileItIsWholeAndOfClosingThemAllFirst() throws Exception {
        edt(() -> {
            manager.openTerminalWindow(null).orElseThrow().requestClose();
            assertThat(closings).containsExactly("closing child");
            manager.openTerminalWindow(null).orElseThrow();
            closings.clear();
            manager.closeAll();
            assertThat(closings).containsExactly("closing all", "closing child", "closing main");
            manager.closeAll();
            assertThat(closings).as("nothing is left to close").hasSize(3);
        });
        launch();
    }

    /** For plan 5: a torn-out tab's window opens at its bounds, and the window on top comes first. */
    @Test void aTornOutTabsWindowOpensAtItsBoundsAndTheWindowsListTheLastActiveFirst() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            main.content().newTab(HOME);
            Tab tab = main.content().selectedTab();
            events.clear();
            ChildWindow child = manager.moveToNewWindow(tab, new java.awt.Rectangle(40, 50, 600, 400)).orElseThrow();
            assertThat(events).as("placed before it shows").containsSubsequence("place child 40,50 600x400", "show child");
            assertThat(manager.windowsByActivation()).as("none came forward yet").containsExactly(main, child);
            child.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(child, main);
            main.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(main, child);
            child.requestClose();
            assertThat(manager.windowsByActivation()).containsExactly(main);
        });
        launch();
    }

    @Test void theWindowThatCameToTheFrontLastIsTheLastActive() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            assertThat(manager.lastActive()).as("none came forward yet: the main window").containsSame(main);
            child.events().activated().accept(true);
            assertThat(manager.lastActive()).containsSame(child);
            assertThat(child.content().isActiveAndOpen()).isTrue();
            child.requestClose();
            assertThat(manager.lastActive()).containsSame(main);
        });
        launch();
    }

    @Test void nothingOpensWhileJasperQuits() throws Exception {
        edt(() -> {
            quitting = true;
            assertThat(manager.ensureMain("handoff", null)).isEmpty();
            assertThat(manager.openTerminalWindow(null)).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).isEmpty();
        });
    }

    @Test void aRequestWhileTheMainWindowOpensIsRefusedRatherThanOpeningASecondOne() throws Exception {
        edt(() -> {
            reenter = true;
            assertThatThrownBy(() -> manager.ensureMain("cold", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The main window is still opening");
            assertThat(manager.main()).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(model.tabs().tabs()).isEmpty();
            assertThat(manager.ensureMain("cold", null)).as("the next request opens it").isPresent();
        });
        launch();
    }

    @Test void aWindowWhoseFrameFailsIsDiscardedAndForgotten() throws Exception {
        edt(() -> {
            failFrames = true;
            assertThatThrownBy(() -> manager.ensureMain("cold", null)).isInstanceOf(IllegalStateException.class).hasMessage("no frame");
            assertThat(manager.main()).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).containsExactly("closed main");
            failFrames = false;
            assertThat(manager.ensureMain("cold", null)).as("the next request opens one").isPresent();
        });
        launch();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `WindowManager`, `MainWindow`, `ChildWindow`, `JasperWindow`, `WindowShell`.

- [ ] **Step 3: The windows and their manager**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowShell.java`:

```java
package dev.jasper.app.workspace;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a Jasper window's native frame does for it (workspace tabs plan 4a), as functions: {@code WindowFrame} gives them
 * for a frame on screen, and a headless test gives its own. EDT only.
 *
 * @param show shows the frame for the first time
 * @param toFront brings the frame to the front, restoring it when it is minimized
 * @param dispose remembers what the frame keeps (the main window's bounds) and disposes it, after its content has closed
 * @param discard disposes the frame and remembers nothing: a window that failed to open
 * @param window the frame, for parenting dialogs; null in a headless test
 * @param owns whether a component is the frame or sits inside it
 * @param place puts the frame at screen bounds before it first shows: a torn-out tab's window (workspace tabs plan 5)
 */
public record WindowShell(Runnable show, Runnable toFront, Runnable dispose, Runnable discard, Supplier<Window> window,
                          Predicate<Component> owns, Consumer<Rectangle> place) {
    /** Requires every function. */
    public WindowShell {
        Objects.requireNonNull(show, "show"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(dispose, "dispose");
        Objects.requireNonNull(discard, "discard"); Objects.requireNonNull(window, "window"); Objects.requireNonNull(owns, "owns");
        Objects.requireNonNull(place, "place");
    }

    /**
     * A frame that opens where the platform puts it, as a headless test's stand-in does.
     *
     * @param show shows the frame for the first time
     * @param toFront brings the frame to the front, restoring it when it is minimized
     * @param dispose remembers what the frame keeps and disposes it, after its content has closed
     * @param discard disposes the frame and remembers nothing
     * @param window the frame, for parenting dialogs; null in a headless test
     * @param owns whether a component is the frame or sits inside it
     */
    public WindowShell(Runnable show, Runnable toFront, Runnable dispose, Runnable discard, Supplier<Window> window,
                       Predicate<Component> owns) {
        this(show, toFront, dispose, discard, window, owns, bounds -> { });
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/JasperWindow.java`:

```java
package dev.jasper.app.workspace;

import java.awt.Component;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One of Jasper's windows (workspace tabs spec 6): the {@link MainWindow} or a {@link ChildWindow}, around its
 * {@link WindowContent}. The {@link WindowManager} makes, tracks and closes every one; the window's native frame drives it
 * through {@link #events()}. EDT only.
 */
public abstract sealed class JasperWindow permits MainWindow, ChildWindow {
    /**
     * What a window's native frame tells it.
     *
     * @param closing the title bar's close button, Alt+F4 or the Window menu's Close
     * @param activated the frame came to the front ({@code true}) or lost it
     * @param state the frame is showing, and whether it is minimized
     */
    public record Events(Runnable closing, Consumer<Boolean> activated, BiConsumer<Boolean, Boolean> state) {
        /** A frame nobody listens to, as the warm-up rehearsal's. */
        public static final Events NONE = new Events(() -> { }, active -> { }, (showing, iconified) -> { });

        /** Requires every callback. */
        public Events {
            Objects.requireNonNull(closing, "closing"); Objects.requireNonNull(activated, "activated"); Objects.requireNonNull(state, "state");
        }
    }

    private final WindowManager manager;
    private final WindowContent content;
    private WindowShell shell;
    private boolean closed;

    JasperWindow(WindowManager manager, WindowContent content) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** @return the window's content: its workspace and actions, and the main window's chrome */
    public final WindowContent content() { return content; }

    /** @return the window's id, its content's */
    public final UUID id() { return content.id(); }

    /** @return whether this is the main window or a child window */
    public abstract WindowRole role();

    /** @return whether this is the main window */
    public final boolean isMain() { return role() == WindowRole.MAIN; }

    /** @return whether the window has closed */
    public final boolean closed() { return closed; }

    /** Brings the window to the front, restoring it when it is minimized, and focuses its active tab. */
    public final void toFront() {
        if (closed || shell == null) return;
        shell.toFront().run();
        content.focusSelected();
    }

    /** @return the native window, for parenting dialogs; null before the frame exists and in a headless test */
    public final java.awt.Window nativeWindow() { return shell == null ? null : shell.window().get(); }

    /**
     * Whether {@code component} is this window's frame or sits inside it.
     *
     * @param component a component, or null
     * @return true for this window's own components
     */
    public final boolean owns(Component component) { return shell != null && component != null && shell.owns().test(component); }

    /**
     * Closes the window as its title bar's close button does (workspace tabs spec 6.4): a child window after its tabs'
     * guards; the main window after every window's tabs' guards, with every child window.
     */
    public final void requestClose() { if (!closed) closeRequested(); }

    /** @return what this window's native frame tells it */
    public final Events events() { return new Events(this::requestClose, this::activated, this::stateChanged); }

    /** The close this window's kind makes on a request: see {@link #requestClose()}. */
    abstract void closeRequested();

    final WindowManager manager() { return manager; }

    /** The frame the manager built for this window. */
    final void shell(WindowShell value) { shell = Objects.requireNonNull(value, "value"); }

    final WindowShell shell() { return shell; }

    private void activated(boolean active) {
        if (closed) return;
        content.setActive(active);
        if (active) manager.activated(this);
    }

    private void stateChanged(boolean showing, boolean iconified) {
        if (!closed) manager.stateChanged(this, showing, iconified);
    }

    /**
     * Closes the content, which closes the tabs it still holds, then the frame; the manager forgets the window. The
     * manager's closing hook hears of it first, while its content is whole (plan 6 saves the layout there).
     */
    final void close() {
        if (closed) return;
        closed = true;
        try {
            try {
                manager.closing(this);
            } finally {
                content.close();
            }
        } finally {
            try {
                if (shell != null) shell.dispose().run();
            } finally {
                manager.closed(this);
            }
        }
    }

    /** As {@link #close()}, for a window that failed to open: neither its bounds nor its dock layout are remembered. */
    final void discard() {
        if (closed) return;
        closed = true;
        try {
            content.forgetLayout();
            content.close();
        } finally {
            try {
                if (shell != null) shell.discard().run();
            } finally {
                manager.closed(this);
            }
        }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/MainWindow.java`:

```java
package dev.jasper.app.workspace;

/**
 * The main window (workspace tabs spec 6.2): Jasper's chrome (header and toolbar, menus, the tool window dock and
 * stripes, the status bar, the notification layer and the Buddy) around the main workspace, which its last tab leaves
 * empty rather than closed. There is at most one. Only {@link WindowManager} makes it, in one place, which
 * {@code verifyApplicationArchitecture} checks. EDT only.
 */
public final class MainWindow extends JasperWindow {
    MainWindow(WindowManager manager, WindowContent content) { super(manager, content); }

    @Override public WindowRole role() { return WindowRole.MAIN; }

    /** Closing the main window closes every child window too, after every window's tabs' guards (spec 6.4). */
    @Override void closeRequested() { manager().closeMain(); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ChildWindow.java`:

```java
package dev.jasper.app.workspace;

/**
 * A child window (workspace tabs spec 6.3): a title bar and a workspace, nothing else. It closes when its last tab closes
 * or moves out, and closing it closes its tabs after their guards. Only {@link WindowManager} makes one. EDT only.
 */
public final class ChildWindow extends JasperWindow {
    ChildWindow(WindowManager manager, WindowContent content) { super(manager, content); }

    @Override public WindowRole role() { return WindowRole.CHILD; }

    /** Its tabs close after their guards; a refusal leaves the window as it is (spec 6.4). */
    @Override void closeRequested() { if (content().mayClose()) close(); }

    /** Its last tab closed or moved out: the window closes (spec 6.4). */
    void emptied() { close(); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Jasper's windows (workspace tabs spec 6.1 and 6.4): at most one {@link MainWindow} and any number of
 * {@link ChildWindow}s. It makes the main window only when none is open, in one place ({@link #ensureMain}), and every
 * child window; it closes a child window when its last tab goes and every child window before the main window; it sends a
 * plugin's view without a window, and a child window's panel requests, to the main window. The application owns it and
 * asks it every window question. EDT only.
 */
public final class WindowManager {
    /**
     * A window about to open.
     *
     * @param role the main window or a child window
     * @param empty whether it starts without a tab, for a tab that moves into it
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @param trigger what asked for it, for its start-up timing line
     * @param emptied what its content runs when its last tab has gone
     * @param bounds where a child window opens on screen, a torn-out tab's (workspace tabs plan 5); empty for where the
     *     platform puts it
     */
    public record Opening(WindowRole role, boolean empty, Path directory, String trigger, Runnable emptied,
                          Optional<Rectangle> bounds) {
        /** Requires the role, the trigger, the callback and the bounds. */
        public Opening {
            Objects.requireNonNull(role, "role"); Objects.requireNonNull(trigger, "trigger"); Objects.requireNonNull(emptied, "emptied");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    /**
     * A window was shown, minimized or restored.
     *
     * @param window the window
     * @param showing whether its frame shows
     * @param iconified whether it is minimized
     */
    public record State(JasperWindow window, boolean showing, boolean iconified) { }

    /**
     * What the application gives the window manager.
     *
     * @param content composes a window's content for an opening, wired to the application; null while Jasper quits, and
     *     then no window opens
     * @param frames builds a window's native frame, or a headless test's stand-in
     * @param opened a window has opened and shows
     * @param activated a window came to the front
     * @param closed a window has closed, or failed to open
     * @param state a window was shown, minimized or restored
     * @param closing a window is about to close, its content still whole; never for one that failed to open
     * @param closingAll every window is about to close (closing the main window, Quit, going to the background), before any
     *     does: plan 6 saves the main window and its child windows here
     */
    public record Hooks(Function<Opening, WindowContent> content, Function<JasperWindow, WindowShell> frames,
                        Consumer<JasperWindow> opened, Consumer<JasperWindow> activated, Consumer<JasperWindow> closed,
                        Consumer<State> state, Consumer<JasperWindow> closing, Runnable closingAll) {
        /** Requires every hook. */
        public Hooks {
            Objects.requireNonNull(content, "content"); Objects.requireNonNull(frames, "frames"); Objects.requireNonNull(opened, "opened");
            Objects.requireNonNull(activated, "activated"); Objects.requireNonNull(closed, "closed"); Objects.requireNonNull(state, "state");
            Objects.requireNonNull(closing, "closing"); Objects.requireNonNull(closingAll, "closingAll");
        }
    }

    private final Contributions model;
    private final Hooks hooks;
    private final List<ChildWindow> children = new ArrayList<>();
    /** The windows that came to the front, the last one first. */
    private final List<JasperWindow> activations = new ArrayList<>();
    private MainWindow main;
    private JasperWindow lastActive;
    private int openings;
    private boolean openingMain;

    /**
     * The windows of the application whose contributions are {@code model}: its tab registry places a plugin's view
     * through this manager, and its panel requests from a child window reach the main window.
     *
     * @param model the application's contributions
     * @param hooks what the application gives the manager
     */
    public WindowManager(Contributions model, Hooks hooks) {
        this.model = Objects.requireNonNull(model, "model");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        // A plugin's view opened with no window goes to the main window, restored first when none is open (spec 6.4).
        model.tabs().setFactory(separate -> (separate ? openChild(true, null, null).map(JasperWindow::id) : ensureMain("plugin", null)
            .map(JasperWindow::id)).orElseThrow(() -> new IllegalStateException("Jasper is quitting")));
        model.panelRoute(this::routePanel);
    }

    /** @return the main window while it is open */
    public Optional<MainWindow> main() { return Optional.ofNullable(main); }

    /** @return the child windows, in the order they opened */
    public List<ChildWindow> children() { return List.copyOf(children); }

    /** @return every open window: the main window first, then the child windows in the order they opened */
    public List<JasperWindow> windows() {
        var all = new ArrayList<JasperWindow>();
        if (main != null) all.add(main);
        all.addAll(children);
        return List.copyOf(all);
    }

    /** @return whether no window is open */
    public boolean isEmpty() { return main == null && children.isEmpty(); }

    /** @return how many windows are being opened right now: from the start of an opening until it shows or fails */
    public int opening() { return openings; }

    /**
     * The open window with this id.
     *
     * @param id a window's id, or null
     * @return the window, while it is open
     */
    public Optional<JasperWindow> window(UUID id) {
        if (id == null) return Optional.empty();
        return windows().stream().filter(window -> window.id().equals(id)).findFirst();
    }

    /**
     * @return every open window, the one that came to the front last first; those that never did follow in the order of
     *     {@link #windows()}: the window on top where windows overlap, for a drop (workspace tabs plan 5)
     */
    public List<JasperWindow> windowsByActivation() {
        var ordered = new ArrayList<JasperWindow>();
        for (JasperWindow window : activations) if (!window.closed()) ordered.add(window);
        for (JasperWindow window : windows()) if (!ordered.contains(window)) ordered.add(window);
        return List.copyOf(ordered);
    }

    /** @return the window that came to the front last while it is open; else the main window; else the first child window */
    public Optional<JasperWindow> lastActive() {
        if (lastActive != null && !lastActive.closed()) return Optional.of(lastActive);
        if (main != null) return Optional.of(main);
        return children.isEmpty() ? Optional.empty() : Optional.of(children.getFirst());
    }

    /**
     * The main window, opened first when none is open (workspace tabs spec 6.4: every request restores it first).
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts; null for where a new shell starts
     * @return the main window; empty while Jasper quits or the main window closes
     * @throws IllegalStateException when asked again while the main window is still opening
     */
    public Optional<MainWindow> ensureMain(String trigger, Path directory) {
        requireEdt();
        // A main window that is closing is not absent: no second one opens while it goes (workspace tabs spec 6.1).
        if (main != null) return main.closed() ? Optional.empty() : Optional.of(main);
        if (openingMain) throw new IllegalStateException("The main window is still opening");
        openingMain = true;
        try {
            return open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty()),
                content -> main = new MainWindow(this, content));
        } finally {
            openingMain = false;
        }
    }

    /**
     * New Terminal Window (workspace tabs spec 6.5): a child window with a terminal tab in {@code directory}, after the
     * main window is open.
     *
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @return the child window; empty while Jasper quits
     */
    public Optional<ChildWindow> openTerminalWindow(Path directory) {
        requireEdt();
        if (ensureMain("menu", null).isEmpty()) return Optional.empty();
        return openChild(false, directory, null);
    }

    /**
     * Move Tab to New Window (workspace tabs spec 6.5): a new child window takes {@code tab}, its content and session
     * untouched. A tab being closed or pinned stays, and so does a child window's only tab: that window already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab) { return moveToNewWindow(tab, null); }

    /**
     * As {@link #moveToNewWindow(Tab)}, the new child window at {@code bounds} on screen: a tab torn out of its window
     * (workspace tabs plan 5).
     *
     * @param tab a tab of one of these windows
     * @param bounds where the new window goes on screen; null for where the platform puts it
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds) {
        requireEdt();
        if (tab == null || tab.closed() || tab.pinned()) return Optional.empty();
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || !from.get().isMain() && from.get().content().tabCount() < 2) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds);
        if (opened.isEmpty()) return opened;
        ChildWindow child = opened.get();
        try {
            model.tabs().move(tab, child.id());
        } finally {
            // A move the registry refused, or one that failed, leaves the new window empty: it goes.
            if (!child.closed() && child.content().tabCount() == 0) child.close();
        }
        return child.closed() ? Optional.empty() : opened;
    }

    /**
     * Move Tab to Main Window (workspace tabs spec 6.5): {@code tab} goes to the main window's active group; its child
     * window closes when that was its last tab.
     *
     * @param tab a tab of a child window
     */
    public void moveToMainWindow(Tab tab) {
        requireEdt();
        if (tab == null || tab.closed()) return;
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || from.get().isMain()) return;
        ensureMain("menu", null).ifPresent(target -> model.tabs().move(tab, target.id()));
    }

    /**
     * Closes every child window, then the main window, without asking any guard: Quit and going to the background ask
     * first. The closing-all hook hears of it once before anything closes, while every window is whole.
     */
    public void closeAll() {
        requireEdt();
        try {
            if (!isEmpty()) hooks.closingAll().run();
        } finally {
            for (ChildWindow child : List.copyOf(children)) child.close();
            if (main != null) main.close();
        }
    }

    /**
     * The user closes the main window (workspace tabs spec 6.4): every window's tabs' guards decide; when they agree, every
     * child window closes, then the main window. Residency then decides whether Jasper stays, through the closed hook.
     *
     * @return whether the windows closed
     */
    boolean closeMain() {
        requireEdt();
        if (main == null) return true;
        if (!model.tabs().mayClose(this::everyTab)) return false;
        closeAll();
        return true;
    }

    private List<Tab> everyTab() {
        var all = new ArrayList<Tab>();
        for (JasperWindow window : windows()) all.addAll(model.tabs().inWindow(window.id()));
        return all;
    }

    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds) {
        ChildWindow[] made = new ChildWindow[1];
        return open(new Opening(WindowRole.CHILD, empty, directory, "menu", () -> { if (made[0] != null) made[0].emptied(); },
            Optional.ofNullable(bounds).map(Rectangle::new)), content -> {
            made[0] = new ChildWindow(this, content);
            children.add(made[0]);
            return made[0];
        });
    }

    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make) {
        requireEdt();
        openings++;
        W window = null;
        try {
            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
            window = make.apply(content);
            window.shell(hooks.frames().apply(window));
            // A torn-out tab's window opens where it was dropped (workspace tabs plan 5).
            opening.bounds().ifPresent(window.shell().place());
            window.shell().show().run();
            hooks.opened().accept(window);
            return Optional.of(window);
        } catch (RuntimeException | Error failure) {
            if (window != null) {
                try {
                    window.discard();
                } catch (RuntimeException | Error discarding) {
                    failure.addSuppressed(discarding);
                }
            }
            throw failure;
        } finally {
            openings--;
        }
    }

    /** A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. */
    private Contributions.PanelRequest routePanel(Contributions.PanelRequest request) {
        if (main == null || children.stream().noneMatch(child -> child.id().equals(request.windowId()))) return request;
        if (request.op() != Contributions.PanelRequest.Op.HIDE) main.toFront();
        return new Contributions.PanelRequest(main.id(), request.panelId(), request.op());
    }

    void activated(JasperWindow window) {
        lastActive = window;
        activations.remove(window);
        activations.addFirst(window);
        hooks.activated().accept(window);
    }

    /** A window is about to close, its content whole. */
    void closing(JasperWindow window) { hooks.closing().accept(window); }

    void stateChanged(JasperWindow window, boolean showing, boolean iconified) {
        hooks.state().accept(new State(window, showing, iconified));
    }

    /** A window closed or was discarded: it is forgotten first, so a failing hook cannot keep it. */
    void closed(JasperWindow window) {
        if (window == main) main = null;
        else children.remove(window);
        activations.remove(window);
        if (lastActive == window) lastActive = null;
        hooks.closed().accept(window);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Windows belong to the EDT");
    }
}
```

- [ ] **Step 4: Panel routing and the architecture rule**

`Contributions.panelRoute`, and the one place that makes the main window, checked over every class's constant pool:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java", [
    ("""    private final List<Consumer<PanelRequest>> panelListeners = new ArrayList<>();
""", """    private final List<Consumer<PanelRequest>> panelListeners = new ArrayList<>();
    private java.util.function.UnaryOperator<PanelRequest> panelRoute = java.util.function.UnaryOperator.identity();
""", 1),
    ("""    public void requestPanel(PanelRequest request) {
        requireEdt();
        for (Consumer<PanelRequest> listener : List.copyOf(panelListeners)) {
            try { listener.accept(request); }
""", """    /**
     * Where a panel request goes before any window hears it: the window manager sends a child window's to the main
     * window, which has the dock (workspace tabs spec 6.3). By default a request goes where it names.
     *
     * @param route maps a request to the one the windows hear
     */
    public void panelRoute(java.util.function.UnaryOperator<PanelRequest> route) {
        panelRoute = Objects.requireNonNull(route, "route");
    }

    public void requestPanel(PanelRequest request) {
        requireEdt();
        PanelRequest routed = Objects.requireNonNull(panelRoute.apply(Objects.requireNonNull(request, "request")), "routed");
        for (Consumer<PanelRequest> listener : List.copyOf(panelListeners)) {
            try { listener.accept(routed); }
""", 1)])
edit("gradle/application-architecture.gradle.kts", [(
"""                for (batch in types.chunked(100)) {
                    val code = tool("javap", listOf("-classpath", classes.absolutePath, "-c", "-p") + batch)
                    check(removed.none { code.contains(it.replace('.', '/')) }) { "Application references removed private presentation adapters" }
                    check(!code.contains("dev/jasper/terminal/internal/") && !code.contains("TerminalSession.internalAccess")) {
                        "Application bypasses supported terminal API"
                    }
                }
""", """                for (batch in types.chunked(100)) {
                    val code = tool("javap", listOf("-classpath", classes.absolutePath, "-c", "-p") + batch)
                    check(removed.none { code.contains(it.replace('.', '/')) }) { "Application references removed private presentation adapters" }
                    check(!code.contains("dev/jasper/terminal/internal/") && !code.contains("TerminalSession.internalAccess")) {
                        "Application bypasses supported terminal API"
                    }
                }
                // Workspace tabs spec 6.1: the main window is made in one place, WindowManager.ensureMain. The constant pool
                // (javap -v) also shows a constructor reference (MainWindow::new), which javap -c alone would miss.
                val makesMain = "dev/jasper/app/workspace/MainWindow.\\"<init>\\""
                val makers = mutableMapOf<String, Int>()
                for (batch in types.chunked(100)) {
                    val verbose = tool("javap", listOf("-classpath", classes.absolutePath, "-v", "-p") + batch)
                    for (section in verbose.split(Regex("(?m)^Classfile ")).drop(1)) {
                        if (!section.contains(makesMain)) continue
                        val type = Regex("this_class: #\\\\d+\\\\s+// (\\\\S+)").find(section)?.groupValues?.get(1)?.replace('/', '.') ?: continue
                        if (topLevel(type) == "dev.jasper.app.workspace.MainWindow") continue
                        makers[type] = section.lines().count { (it.contains("invokespecial") || it.contains("REF_newInvokeSpecial")) && it.contains(makesMain) }
                    }
                }
                check(makers.keys.map { topLevel(it) }.toSet() == setOf("dev.jasper.app.workspace.WindowManager") && makers.values.sum() == 1) {
                    "MainWindow is made in exactly one place, WindowManager.ensureMain: $makers"
                }
""", 1)])
PY
```

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
for name, what in [("WindowManager", "the main window and the child windows"), ("JasperWindow", "one of Jasper's windows"),
                   ("WindowShell", "what a window's native frame does for it")]:
    ledger.add(M + name + ".java", "app.workspace", name, T + "WindowManagerTest.java", "New in workspace-tabs plan 4a Task 2; " + what)
ledger.add(T + "WindowManagerTest.java", "app.workspace", "WindowManagerTest", T + "WindowManagerTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 4a Task 2; windows and their lifecycle, headless")
PY
```

- [ ] **Step 6: Run the tests and the architecture check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.workspace.WindowRolesTest' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WindowManagerTest` 14). Then `./gradlew verifyApplicationArchitecture`: PASS. To see the rule bite,
add a second `new MainWindow(this, content)` anywhere in `WindowManager`, run `./gradlew verifyApplicationArchitecture`
(FAIL: "MainWindow is made in exactly one place, WindowManager.ensureMain: {…=2}"), and remove it again. Then
`./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src gradle/application-architecture.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Windows: the window manager, the main window and child windows

WindowManager holds at most one MainWindow, made only in ensureMain and
only when none is open (verifyApplicationArchitecture checks the one call
site), and any number of ChildWindows; a child window closes with its last
tab, closing the main window asks every window's tabs' guards and closes
the child windows first, and a child window's panel requests go to the
main window. Native frames are a WindowShell of functions, so the
lifecycle runs headlessly.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The application asks the window manager

Spec 5.4's retarget, 6.1-6.4 and 9.3's notification click: `JasperApplication` composes and wires each window's content
for the manager and builds its `WindowFrame` (today's `TerminalWindow`, renamed); its window set, `newWindow`,
`openWindow`, `composeWindow`, detached windows and factory calls go; every outside request opens in the main window,
restored first; closing the main window, Quit and going to the background close the child windows first; an operating
system notification's click raises the window that holds its pane now (Rulings 4-7, 14-20).

**Files:**
- Rename: `workspace/TerminalWindow.java` to `workspace/WindowFrame.java` (whole new text); delete
  `workspace/WindowCallbacks.java`
- Modify: `workspace/WindowContent.java` (`mountChild`), `application/JasperApplication.java`, `application/Rehearsal.java`,
  `application/OpenPlan.java`, `application/SystemNotices.java`, `notifications/CommandNotifier.java`,
  `benchmark/BenchmarkRun.java`
- Test: `application/JasperApplicationWindowsTest.java` (new); `SystemNoticesTest`, `CommandNotifierTest`,
  `WindowShellTest`, `JasperApplicationBackgroundTest`, `WindowCommandPaletteTest`, `ApplicationTestSupport`,
  `ChromeRenderTest`, `WindowDragTest` (comments)
- Ledger: one row moved, one added

**Interfaces:**
- Consumes: Task 2's `WindowManager` and `Hooks`, `JasperWindow.events()`, `Events.NONE`, `WindowShell`; Task 1's
  `WindowContent(WindowRole, …)`; plan 1's `open`, `openFirstWindow`, `handedOff`, `OpenPlan`.
- Produces: `public final class WindowFrame` with `WindowFrame(WindowContent, WindowLook, ConfigSnapshot initial,
  JasperWindow.Events)`, `restoreLayout(UiState)`, `WindowShell shell()`, `java.awt.Window nativeWindow()`, `discard()`;
  package `static MacTitleBar WindowContent.mountChild(JRootPane, WindowContent, WindowLook, boolean screenMenuBar,
  Consumer<String>)`; `public WindowManager JasperApplication.windows()`; `public Optional<MainWindow>
  JasperApplication.openFirstWindow(List<Path>)`; package `Function<JasperWindow, WindowShell> JasperApplication.frames`;
  `void JasperApplication.windowClosed(JasperWindow)`; `public Optional<Object> CommandNotifier.producer(UUID)`;
  `SystemNotices.Windows(raise, showHistory, showErrors, Function<UUID, Optional<UUID>> holder)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWindowsTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.MainWindow;
import dev.jasper.app.workspace.WindowRole;
import dev.jasper.app.workspace.WindowShell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Workspace tabs spec 6.4 through the application, with stand-in frames: every request restores the main window first,
 * and closing the main window closes its child windows, after which residency decides.
 */
class JasperApplicationWindowsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final List<String> frames = new ArrayList<>();

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, terminate);
            held[0].frames = window -> {
                String name = window.isMain() ? "main" : "child";
                return new WindowShell(() -> frames.add("show " + name), () -> frames.add("front " + name),
                    () -> frames.add("dispose " + name), () -> frames.add("discard " + name), () -> null,
                    component -> SwingUtilities.isDescendingFrom(component, window.content()));
            };
        });
        return held[0];
    }

    @Test void everyRequestRestoresTheMainWindowFirst() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                MainWindow main = application.windows().main().orElseThrow();
                assertThat(application.windows().windows()).as("a handoff with no window restores the main window").containsExactly(main);
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                assertThat(main.content().tabCount()).as("a folder opens as a terminal tab in the main window").isEqualTo(2);
                assertThat(frames).contains("front main");
                application.windows().closeAll();
                assertThat(application.windows().isEmpty()).isTrue();
                application.windows().openTerminalWindow(null);
                assertThat(application.windows().windows()).as("New Terminal Window from the menu bar item restores the main window first")
                    .extracting(JasperWindow::role).containsExactly(WindowRole.MAIN, WindowRole.CHILD);
                application.windows().closeAll();
                var tab = application.contributions().tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
                assertThat(tab.window()).as("a plugin's tab with no window open goes to a restored main window")
                    .isEqualTo(application.windows().main().orElseThrow().id());
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * Workspace tabs spec 6.1, one main window per process: the main window, its manager and the native frames are made
     * only where the application says, each with an explicit {@code new} (a constructor reference would hide the site).
     * {@code verifyApplicationArchitecture} checks the same with javap.
     */
    @Test void theMainWindowItsManagerAndTheFramesAreMadeOnlyWhereTheApplicationSays() throws Exception {
        var pinned = java.util.Set.of("dev/jasper/app/workspace/MainWindow", "dev/jasper/app/workspace/WindowManager",
            "dev/jasper/app/workspace/WindowFrame");
        var sites = new java.util.TreeMap<String, List<String>>();
        var classes = java.nio.file.Path.of(JasperApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var files = java.nio.file.Files.walk(classes)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                var model = java.lang.classfile.ClassFile.of().parse(file);
                String owner = model.thisClass().asInternalName();
                for (var method : model.methods())
                    method.code().ifPresent(code -> {
                        for (var element : code)
                            if (element instanceof java.lang.classfile.instruction.InvokeInstruction call
                                    && call.opcode() == java.lang.classfile.Opcode.INVOKESPECIAL && call.name().equalsString("<init>")
                                    && pinned.contains(call.owner().asInternalName()) && !owner.equals(call.owner().asInternalName()))
                                sites.computeIfAbsent(call.owner().asInternalName(), made -> new ArrayList<>())
                                    .add(owner + "." + method.methodName().stringValue());
                    });
                for (var entry : model.constantPool())
                    if (entry instanceof java.lang.classfile.constantpool.MethodHandleEntry handle
                            && handle.reference().name().equalsString("<init>") && pinned.contains(handle.reference().owner().asInternalName()))
                        sites.computeIfAbsent(handle.reference().owner().asInternalName(), made -> new ArrayList<>()).add(owner + "::new");
            }
        }
        assertThat(sites).containsOnlyKeys(pinned);
        assertThat(sites.get("dev/jasper/app/workspace/MainWindow")).hasSize(1)
            .allMatch(site -> site.matches("dev/jasper/app/workspace/WindowManager\\.(ensureMain|lambda\\$ensureMain\\$\\d+)"));
        assertThat(sites.get("dev/jasper/app/workspace/WindowManager")).containsExactly("dev/jasper/app/application/JasperApplication.<init>");
        assertThat(sites.get("dev/jasper/app/workspace/WindowFrame")).hasSize(2)
            .anyMatch(site -> site.matches("dev/jasper/app/application/JasperApplication\\.(frameOnScreen|lambda\\$frameOnScreen\\$\\d+)"))
            .anyMatch(site -> site.matches("dev/jasper/app/application/JasperApplication\\.(rehearsalWindow|lambda\\$rehearsalWindow\\$\\d+)"));
    }

    @Test void closingTheMainWindowClosesItsChildWindowsAndThenTheProcess() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> {
            application.windows().openTerminalWindow(null);
            frames.clear();
            application.windows().main().orElseThrow().requestClose();
            assertThat(application.windows().isEmpty()).isTrue();
            assertThat(frames).as("every child window, then the main window").containsExactly("dispose child", "dispose main");
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("not resident: the last window ends the process").isTrue();
    }

    @Test void aResidentProcessOutlivesItsWindowsAndRestoresTheMainWindowOnRequest() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.windows().openTerminalWindow(null);
                frames.clear();
                application.toBackground();
                assertThat(frames).as("as closing the main window: the child windows first").containsExactly("dispose child", "dispose main");
                assertThat(application.windows().isEmpty()).isTrue();
                assertThat(application.acceptsRequests()).as("still running in the background").isTrue();
                application.open(List.of(), "reopen");
                assertThat(application.windows().main()).isPresent();
            });
        } finally {
            edt(application::quitNow);
        }
    }
}
```

Then the other tests:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "application/ApplicationTestSupport.java", [
    ("import dev.jasper.app.workspace.TerminalWindow;\n", "import dev.jasper.app.workspace.JasperWindow;\n", 1),
    ("    public static void windowClosed(JasperApplication application, TerminalWindow window) { application.windowClosed(window); }",
     "    public static void windowClosed(JasperApplication application, JasperWindow window) { application.windowClosed(window); }", 1)])
B = T + "application/JasperApplicationBackgroundTest.java"
regex(B, r"edt\((application|coldApplication|warmedApplication)::newWindow\)", r"edt(() -> \1.openFirstWindow(List.of()))", 3)
regex(B, r"edt\(\(\) -> application\.newWindow\(\)\)", "edt(() -> application.openFirstWindow(List.of()))", 3)
edit(T + "workspace/WindowCommandPaletteTest.java", [
    ("                assertThat(application.newWindow(DesktopTestSupport.HOME)).isNull();\n",
     "                assertThat(application.windows().openTerminalWindow(DesktopTestSupport.HOME)).isEmpty();\n", 1)])
S = T + "application/SystemNoticesTest.java"
edit(S, [
    ("""    /** What raising a window returns: the window it raised, empty while Jasper quits. */
    private Optional<UUID> raises = Optional.of(LAST_USED);
""", """    /** What raising a window returns: the window it raised, empty while Jasper quits. */
    private Optional<UUID> raises = Optional.of(LAST_USED);
    /** The window that holds a notification's pane now: none by default, so the notification's own target is raised. */
    private java.util.function.Function<UUID, Optional<UUID>> holder = id -> Optional.empty();
""", 1),
    ("""        }, histories::add, errors::add));
""", """        }, histories::add, errors::add, id -> holder.apply(id)));
""", 1),
    ("""    @Test void aClickOnAnIncidentOpensTheErrorsWindowAtIt() {
""", """    /** Workspace tabs spec 9.3: a command's tab moved to another window after its notification posted. */
    @Test void aClickRaisesTheWindowThatHoldsThePaneNowNotTheOneItPostedFrom() {
        UUID postedFrom = UUID.randomUUID(), holdsItNow = UUID.randomUUID();
        holder = id -> Optional.of(holdsItNow);
        var notices = notices();
        var ran = new ArrayList<String>();
        UUID id = post(request(Severity.INFO, "make").withTarget(postedFrom).withActivate(() -> ran.add("focus the pane")));
        notices.clicked(id);
        assertThat(raised).containsExactly(Optional.of(holdsItNow));
        assertThat(ran).containsExactly("focus the pane");
    }

    @Test void aClickOnAnIncidentOpensTheErrorsWindowAtIt() {
""", 1)])
edit(T + "notifications/CommandNotifierTest.java", [
    ("""    @Test void theGroupIsLongRunningCommandsShownAsCards() {
""", """    /** Workspace tabs spec 9.3: a click resolves the pane's window from the notification's producer. */
    @Test void aNotificationNamesThePaneThatPostedItUntilThePaneCloses() {
        var notifier = notifier(10);
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        UUID posted = center.history().getFirst().id();
        assertThat(notifier.producer(posted)).contains("pane");
        assertThat(notifier.producer(UUID.randomUUID())).isEmpty();
        notifier.closed("pane");
        assertThat(notifier.producer(posted)).as("the pane is gone").isEmpty();
    }

    @Test void theGroupIsLongRunningCommandsShownAsCards() {
""", 1)])
W = T + "workspace/WindowShellTest.java"
edit(W, [
    ("/** Spec 10.1 and 10.10: a terminal frame's chrome under the look, through the one mount TerminalWindow uses. */",
     "/** Spec 10.1 and 10.10, and workspace tabs spec 6.3: a window frame's chrome under the look, through the mounts WindowFrame uses. */", 1),
    ("""    @Test void compactUnderANativeTitleKeepsTheMenuRow() throws Exception {
""", """    /** Workspace tabs spec 6.3: a title bar over the workspace alone; its menus only where macOS's screen menu bar shows them. */
    @Test void aChildWindowMountsItsWorkspaceUnderItsTabsTitleAndNoMenuRow() throws Exception {
        edt(() -> {
            var child = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            var root = new JRootPane();
            String[] title = {""};
            try (var bar = WindowContent.mountChild(root, child, new WindowLook(WorkspaceChrome.STANDARD, true, false), true,
                    value -> title[0] = value)) {
                assertThat(bar).as("the unified bar, with a title and no header").isNotNull();
                assertThat(bar.style()).isEqualTo(dev.jasper.app.platform.MacTitleBar.Style.WINDOW);
                assertThat(root.getJMenuBar()).as("the screen menu bar shows the main window's menus, built for this window")
                    .isSameAs(child.menuBar());
                child.update();
                assertThat(title[0]).as("the active tab's title").isEqualTo(child.tabTitle(child.selectedTab()));
            }
            var other = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            var plain = new JRootPane();
            assertThat(WindowContent.mountChild(plain, other, new WindowLook(WorkspaceChrome.STANDARD, false, false), false, value -> { }))
                .as("the system's title bar").isNull();
            assertThat(plain.getJMenuBar()).as("no menu row in a child window").isNull();
            assertThat(plain.getContentPane()).as("its workspace alone").isSameAs(other);
            var compact = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            compact.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            var compactRoot = new JRootPane();
            assertThat(WindowContent.mountChild(compactRoot, compact, new WindowLook(WorkspaceChrome.STANDARD, true, false), true, value -> { }))
                .as("Compact: the system's title bar, as the main window's").isNull();
            assertThat(compactRoot.getContentPane()).isSameAs(compact);
        });
    }

    @Test void compactUnderANativeTitleKeepsTheMenuRow() throws Exception {
""", 1)])
edit(T + "workspace/ChromeRenderTest.java", [
    (" * combination and a strength-2 covering array of the nine chrome points, mounted as TerminalWindow mounts it and printed",
     " * combination and a strength-2 covering array of the nine chrome points, mounted as WindowFrame mounts it and printed", 1)])
edit(T + "workspace/WindowDragTest.java", [
    ("                // TerminalWindow on windowDeactivated:", "                // WindowFrame on windowDeactivated:", 1)])
regex(T + "workspace/TerminalTabTest.java", r"\bTerminalWindow\.minimumSize\(", "WindowFrame.minimumSize(", 5)
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `JasperApplication.frames`, `windows()`, `openFirstWindow` returning
`Optional`, `mountChild`, `producer`, the four-argument `SystemNotices.Windows`.

- [ ] **Step 3: The frame**

```bash
git mv jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowFrame.java
git rm -q jasper-app/src/main/java/dev/jasper/app/workspace/WindowCallbacks.java
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowFrame.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.platform.WindowGlass;
import dev.jasper.app.persistence.UiState;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/**
 * The native frame of one Jasper window (workspace tabs spec 6.2 and 6.3), around its {@link WindowContent}: the main
 * window's with the look's title bar, header and menus; a child window's with a title bar and the active tab's title, the
 * screen menu bar's menus on macOS while the look shows them there, and no menu row anywhere else. Both take the Keymap
 * on their own root, the command palette and workspace glass. Everything testable lives in {@link JasperWindow} and
 * {@link WindowManager}; this is never constructed in headless tests.
 */
public final class WindowFrame {
    private final JFrame frame = new JFrame("Jasper");
    private final WindowContent content;
    private final JasperWindow.Events events;
    private final MacTitleBar titleBar;
    private final WindowGlass glass;
    private boolean closed;
    private WindowPlacement placement;
    private final java.awt.event.ComponentAdapter geometry = new java.awt.event.ComponentAdapter() {
        @Override public void componentMoved(java.awt.event.ComponentEvent event) { rememberBounds(); }
        @Override public void componentResized(java.awt.event.ComponentEvent event) { rememberBounds(); }
    };
    private final WindowAdapter listener = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { events.closing().run(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
        @Override public void windowOpened(WindowEvent event) { reportState(); }
        @Override public void windowIconified(WindowEvent event) { reportState(); }
        @Override public void windowDeiconified(WindowEvent event) { reportState(); }
    };

    /**
     * The frame around {@code content}, sized for {@code initial}'s grid when it is given, not yet shown.
     *
     * @param content the window's content; its role decides the frame's chrome
     * @param look the look's chrome and title facts
     * @param initial the configuration a new window takes, or null
     * @param events what the frame tells its window
     */
    public WindowFrame(WindowContent content, WindowLook look, ConfigSnapshot initial, JasperWindow.Events events) {
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.events = java.util.Objects.requireNonNull(events, "events");
        frame.setIconImages(ApplicationIcon.images());
        if (initial != null) {
            JComponent sized = content.currentPane() != null ? content.currentPane() : content.workspace();
            sized.setPreferredSize(InitialWindowSize.terminalArea(initial));
        }
        titleBar = content.isMain() ? WindowContent.mount(frame.getRootPane(), content, look, frame::setTitle)
            : WindowContent.mountChild(frame.getRootPane(), content, look, screenMenuBar(), frame::setTitle);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        content.installRootBindings(frame.getRootPane());
        // Native initial focus must not fall through to the first restored tool-panel control.
        frame.setFocusTraversalPolicy(content.getFocusTraversalPolicy());
        content.onMinimumSizeChanged = this::updateMinimumSize;
        if (titleBar != null) titleBar.attach(frame);
        // Workspace glass exists only on macOS: elsewhere WindowGlass is never loaded and its requests are never made.
        if (SystemInfo.isMacOS && !GraphicsEnvironment.isHeadless()) {
            glass = new WindowGlass(frame.getRootPane(), WindowGlass.Platform.current(), content::applyBackgroundOpacity);
            content.setGlassRequests((levels, dark) -> glass.request(levels.opacity(), levels.blur(), dark));
        } else glass = null;
        frame.addWindowListener(listener); frame.pack();
        if (initial != null) {
            updateMinimumSize();
            frame.setSize(InitialWindowSize.fit(frame.getSize(), frame.getMinimumSize(), usableBounds()));
        }
        frame.setLocationByPlatform(true);
        content.update();
    }

    /** Whether macOS shows this process's menus in the screen menu bar, as the look committed before AWT started. */
    private static boolean screenMenuBar() {
        return SystemInfo.isMacOS && Boolean.getBoolean("apple.laf.useScreenMenuBar");
    }

    /** The main window's bounds from {@code state}, checked against the current displays; before the frame shows. */
    public void restoreLayout(UiState state) {
        placement = new WindowPlacement(state);
        updateMinimumSize();
        var screens = new java.util.ArrayList<Rectangle>();
        var environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        screens.add(usableBounds(environment.getDefaultScreenDevice().getDefaultConfiguration()));
        for (var device : environment.getScreenDevices()) {
            Rectangle bounds = usableBounds(device.getDefaultConfiguration());
            if (!screens.contains(bounds)) screens.add(bounds);
        }
        placement.restore(frame.getMinimumSize(), screens).ifPresent(frame::setBounds);
        placement.observe(frame.getBounds(), java.awt.Frame.NORMAL);
        frame.setExtendedState(placement.extendedState());
        frame.addComponentListener(geometry);
    }

    /** @return what this frame does for its window */
    public WindowShell shell() {
        return new WindowShell(this::show, this::toFront, this::dispose, this::discard, () -> frame, this::owns, frame::setBounds);
    }

    /** @return the native window, for painting the warm-up rehearsal */
    public java.awt.Window nativeWindow() { return frame; }

    private void rememberBounds() {
        if (!closed && placement != null && frame.isShowing()) placement.observe(frame.getBounds(), frame.getExtendedState());
    }

    static Dimension minimumSize(JRootPane root, Insets decorations) {
        Dimension contentMinimum = root.getMinimumSize();
        return new Dimension(contentMinimum.width + decorations.left + decorations.right,
            contentMinimum.height + decorations.top + decorations.bottom);
    }

    private void updateMinimumSize() {
        Dimension minimum = minimumSize(frame.getRootPane(), frame.getInsets());
        minimum = InitialWindowSize.fit(minimum, minimum, usableBounds());
        if (!frame.isMinimumSizeSet() || !minimum.equals(frame.getMinimumSize())) frame.setMinimumSize(minimum);
    }

    private Rectangle usableBounds() {
        return usableBounds(frame.getGraphicsConfiguration());
    }

    private static Rectangle usableBounds(java.awt.GraphicsConfiguration graphics) {
        Rectangle bounds = new Rectangle(graphics.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private void setActive(boolean active) {
        if (titleBar != null) titleBar.setActive(active);
        events.activated().accept(active);
    }

    private void reportState() {
        events.state().accept(frame.isShowing(), (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0);
    }

    private void toFront() {
        if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        frame.toFront(); frame.requestFocus();
    }

    private boolean owns(Component component) {
        return component == frame || SwingUtilities.getWindowAncestor(component) == frame;
    }

    private void show() { frame.setVisible(true); reportState(); content.focusSelected(); }

    /** Disposes the frame without remembering its bounds: its window failed to open, or it is the rehearsal's. */
    public void discard() {
        placement = null;
        dispose();
    }

    /** After the content closed: remembers the main window's bounds, then disposes the frame. */
    private void dispose() {
        if (closed) return;
        rememberBounds();
        closed = true;
        frame.removeComponentListener(geometry);
        if (glass != null) glass.close();
        if (placement != null) placement.save(frame.getExtendedState());
        if (titleBar != null) titleBar.close();
        frame.removeWindowListener(listener); frame.dispose();
    }
}
```

- [ ] **Step 4: The application**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, span
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [
    ("""import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.TerminalWindow;
import dev.jasper.app.workspace.WindowCallbacks;
import dev.jasper.app.workspace.WorkspaceActivity;
""", """import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.MainWindow;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.app.workspace.WindowFrame;
import dev.jasper.app.workspace.WindowManager;
import dev.jasper.app.workspace.WindowRole;
import dev.jasper.app.workspace.WindowShell;
import dev.jasper.app.workspace.WorkspaceActivity;
""", 1),
    ("""/** Application-level window and shell ownership; closing a window never exits sibling windows. */
""", """/**
 * The application: its windows (through its {@link WindowManager}: one main window and any number of child windows), its
 * shells, plugins, configuration, notifications and residency.
 */
""", 1),
    ("""    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
""", "", 1),
    ("""    /** Whether an open terminal window has been activated since the first of them opened; see {@link #presence}. */
    private boolean activatedSinceOpen;
    /** How many terminal windows are being opened right now (EDT): from the start of {@link #openWindow} until it returns or fails. */
    private int openingWindows;
""", """    /** Whether an open window has been activated since the first of them opened; see {@link #presence}. */
    private boolean activatedSinceOpen;
    /** The start-up timing of each window being opened, from its composition until it shows. */
    private final Map<UUID, WindowTrace> windowTraces = new HashMap<>();
""", 1),
    ("""    private final dev.jasper.app.contributions.Contributions contributions = new dev.jasper.app.contributions.Contributions();
""", """    private final dev.jasper.app.contributions.Contributions contributions = new dev.jasper.app.contributions.Contributions();
    /** Test seam: how a window's native frame is made. Production builds a {@link WindowFrame}. Before the manager, which reads it. */
    Function<JasperWindow, WindowShell> frames = this::frameOnScreen;
    /**
     * Every window: the main window and the child windows (workspace tabs spec 6.1). EDT. Its closing hooks are plan 6's,
     * which saves the windows there; until then each frame keeps what it keeps when it closes.
     */
    private final WindowManager windows = new WindowManager(contributions, new WindowManager.Hooks(this::composeContent,
        window -> frames.apply(window), this::windowOpened, this::windowActivated, this::windowClosed, this::windowStateChanged,
        window -> { }, () -> { }));
""", 1),
    ("""    private TerminalWindow lastActive;
    /** Every terminal window's tabs and panes, for features that must not hold Swing objects. */
""", """    /** Every window's tabs and terminal panes, for features that must not hold Swing objects. */
""", 1),
    ("""            new SystemNotices.Windows(this::raiseFor, notificationViews::showHistory, this::showErrors));
""", """            new SystemNotices.Windows(this::raiseFor, this::showHistory, this::showErrors, this::holderOf));
""", 1),
])
span(A, "    /** The opacity in effect behind {@code component}: its terminal window's within that window's workspace area, else 1. */\n",
     "            } catch (RuntimeException | Error disposing) {\n                failure.addSuppressed(disposing);\n            }\n        }\n    }\n",
     r'''    /** The opacity in effect behind {@code component}: its window's within that window's workspace area, else 1. */
    private double glassOpacity(Component component) {
        for (JasperWindow window : windows.windows()) if (window.owns(component)) return window.content().workspaceOpacity(component);
        return 1.0;
    }

    private void glassChanged() { for (Runnable listener : List.copyOf(glassListeners)) listener.run(); }

    /** Every window, which this application asks every window question (workspace tabs spec 6.1). EDT. */
    public WindowManager windows() { return windows; }

    /** New windows take live settings while retaining the application startup decoration choice. */
    ConfigSnapshot windowConfiguration() {
        ConfigSnapshot latest = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        return latest.toBuilder().viewMode(startupViewMode).build();
    }

    /**
     * A window's content for {@code opening}, wired to this application: its launcher, configuration, contributions,
     * notifications, terminal registry, glass and timing; the main window's alone gets the notification layer and the
     * saved dock layout. Null while the application quits, and then no window opens. A failure closes what was made and
     * propagates. EDT.
     */
    private WindowContent composeContent(WindowManager.Opening opening) {
        if (quitting || stopped) return null;
        WindowTrace windowTrace = trace.window(opening.trigger());
        windowsOpened++;
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(watchOutput(startSession(path, settings))), shellIntegrationDir, shellProfiles::resolve,
            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem, this::paneVariables, this::pluginShellVariables);
        // Back in the Dock before the window exists: macOS gives a window of a hidden application neither Jasper's menu
        // bar nor focus. If composing fails nothing opened, so the presence is worked out again.
        windowOpening();
        boolean macOs = com.formdev.flatlaf.util.SystemInfo.isMacOS;
        WindowContent content = null;
        try {
            content = new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
                directory -> windows.openTerminalWindow(directory), this::quitRequested, opening.emptied(), themes,
                KeyBindings.defaults(macOs), history, macOs, windowLook().chrome());
            // The first configuration a window takes keeps the start-up view mode, before any native chrome is installed.
            if (configuration != null) content.applyConfiguration(windowConfiguration(), macOs);
            if (configuration != null) configuration.register(content);
            boolean main = opening.role() == WindowRole.MAIN;
            // A child window has no dock or Zen of its own: its layout state is never saved (workspace tabs spec 6.3).
            content.connectContributions(contributions, main ? uiState : UiState.inMemory());
            UUID windowId = content.id();
            content.onError = (title, details) -> notificationViews.error(title, details, windowId);
            content.onInfo = (title, details) -> notificationViews.info(title, details, windowId);
            content.onLaunchFailure = (details, cause) -> incidentNotices.launchFailure(windowId, details, cause);
            content.onOpenFile = (file, line) -> openFileAt(file, line, windowId);
            // File › Open File… (spec 13): the plugins' file chooser over this window; nothing before plugins start.
            content.fileChooser = folder -> auxiliary == null ? Optional.empty() : auxiliary.openFile(windowId, folder);
            if (main) notificationViews.attach(content);
            content.connectTerminals(terminals, () -> windows.window(windowId).ifPresent(JasperWindow::toFront));
            if (WORKSPACE_GLASS) content.setGlassListener(this::glassChanged);
            content.onToggleBuddy = this::toggleBuddy;
            content.onFirstTerminal(pane -> timeFirstTerminal(pane, windowTrace));
            content.buddyEnabled = this::buddyEnabled;
            content.anyWindowActive = () -> windows.windows().stream().anyMatch(open -> open.content().isActiveAndOpen());
            content.activity(event -> {
                switch (event) {
                    case WorkspaceActivity.Started value -> notifications.started(value.id(), value.command(),
                        value.elapsedNanos(), value.activate(), value.watched());
                    case WorkspaceActivity.Finished value -> notifications.finished(value.id(), value.command(),
                        value.exitStatus(), value.duration(), new CommandNotice.Origin(value.origin().anyWindowActive(),
                            value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), windowId, value.activate());
                    case WorkspaceActivity.TitleChanged value -> notifications.titleChanged(value.id(), value.title());
                    case WorkspaceActivity.PaneState value -> {
                        switch (value.state()) {
                            case OPENED -> notifications.opened(value.id());
                            case CLOSED -> notifications.closed(value.id());
                            case FOCUSED -> { }
                            case BLURRED -> notifications.hidden(value.id());
                        }
                    }
                }
            });
            windowTraces.put(windowId, windowTrace);
            return content;
        } catch (RuntimeException | Error failure) {
            // A half-composed window must not outlive the failure, nor keep Jasper in the Dock with nothing open.
            try {
                if (content != null) {
                    notificationViews.detach(content);
                    content.forgetLayout();
                    content.close();
                }
            } catch (RuntimeException | Error closing) {
                failure.addSuppressed(closing);
            } finally {
                updateDock();
            }
            throw failure;
        }
    }

    /** A window's native frame under this process's look: the main window's at its saved bounds. EDT. */
    private WindowShell frameOnScreen(JasperWindow window) {
        var frame = new WindowFrame(window.content(), windowLook(), configuration == null ? null : windowConfiguration(), window.events());
        if (window.isMain()) frame.restoreLayout(uiState);
        startupSteps.workstation();
        return frame.shell();
    }

    /** A window shows: its timing ends, and the start-up steps with the first. EDT. */
    private void windowOpened(JasperWindow window) {
        memoryIndicator.windows(windows.windows().size());
        WindowTrace windowTrace = windowTraces.remove(window.id());
        if (windowTrace != null) windowTrace.mark(WindowTrace.Milestone.SHOWN);
        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
    }
''')
edit(A, [
    ("""    /** The menu bar item's New Window: always a new window, in front of other applications. EDT. */
    private void newWindowFromMenuBar() {
        if (quitting || stopped) return;
        openWindow(null, false, "menu");
        requestForeground();
    }

    /** The menu bar item's Settings\\u2026: over the last active window, or on its own while Jasper has none. EDT. */
    private void settingsFromMenuBar() {
        if (quitting || stopped) return;
        requestForeground();
        openSettings(null, null);
    }
""", """    /**
     * The menu bar item's New Window: a child window with a terminal tab, after the main window is restored (workspace
     * tabs spec 6.4), in front of other applications. EDT.
     */
    private void newWindowFromMenuBar() {
        if (quitting || stopped) return;
        windows.openTerminalWindow(null);
        requestForeground();
    }

    /** The menu bar item's Settings\\u2026: over the window used last, after the main window is restored (spec 6.4). EDT. */
    private void settingsFromMenuBar() {
        if (quitting || stopped) return;
        windows.ensureMain("menu", null);
        requestForeground();
        openSettings(null, null);
    }
""", 1)])
span(A, "    /**\n     * Every outside request to show Jasper ends here:", "        for (Path file : plan.files()) openRequestedFile(file, target.content().id());\n    }\n",
     r'''    /**
     * Every outside request to show Jasper ends here: a second launch's handoff, the {@code jasper} command, the macOS
     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3, 5.4 and 6.4). The main window is restored
     * first when none is open (until workspace-tabs plan 6, a new main window opens, in the first directory the request
     * names when there is one), else raised; each other directory opens as a terminal tab in its active group and each
     * file in the editor a plugin provides, else revealed ({@code openRequestedFile}) ({@code OpenPlan}). {@code trigger}
     * names the caller for start-up timing. EDT.
     *
     * @param paths absolute paths to open; none raises
     * @param trigger what asked, for the window's timing line
     */
    public void open(List<Path> paths, String trigger) {
        if (quitting || stopped) return;
        Optional<MainWindow> current = windows.main();
        OpenPlan plan = OpenPlan.of(paths, current.isPresent(), java.nio.file.Files::isDirectory, java.nio.file.Files::isRegularFile);
        Optional<MainWindow> target = current.isPresent() ? current : windows.ensureMain(trigger, plan.windowDirectory());
        if (target.isEmpty()) return;
        if (current.isPresent()) target.get().toFront();
        openRest(target.get(), plan);
    }

    /**
     * A launch's main window, timed as a cold start unless a {@code --background} launch warmed up: its tab starts in the
     * first directory {@code paths} names, else where a new shell starts, and the rest of the paths open in it as
     * {@link #open} opens them (plan Ruling 13). EDT.
     *
     * @param paths the launch's absolute paths
     * @return the main window; empty while the application is quitting
     */
    public Optional<MainWindow> openFirstWindow(List<Path> paths) {
        boolean open = windows.main().isPresent();
        OpenPlan plan = OpenPlan.of(paths, open, java.nio.file.Files::isDirectory, java.nio.file.Files::isRegularFile);
        Optional<MainWindow> window = windows.ensureMain(
            windowTrigger(windowsOpened, trace.millis(StartupTrace.Milestone.WARMED_UP).isPresent()), plan.windowDirectory());
        window.ifPresent(main -> openRest(main, plan));
        return window;
    }

    private void openRest(MainWindow target, OpenPlan plan) {
        for (Path directory : plan.tabs()) target.content().newTab(directory);
        for (Path file : plan.files()) openRequestedFile(file, target.id());
    }
''')
edit(A, [
    ("""    private Rehearsal.Stage rehearsalWindow(Rehearsal.Feed feed, ConfigSnapshot snapshot) {
        TerminalWindow window = new TerminalWindow(Rehearsal.CALLBACKS, Rehearsal.launcher(feed), null, false, themes, snapshot,
            new CommandHistory(), windowLook());
        // The pane's launch completion is queued, so this is in place before a failed launch could ask for a dialog.
        Rehearsal.silence(window.content());
        // Discarded, not closed: never shown and at the default size, it must not become the last window closed.
        Runnable close = () -> Rehearsal.closeAndDispose(window::discard, window.nativeWindow()::dispose);
""", """    private Rehearsal.Stage rehearsalWindow(Rehearsal.Feed feed, ConfigSnapshot snapshot) {
        // A main window's content and frame, made here and nowhere else: the window manager never hears of it.
        boolean macOs = com.formdev.flatlaf.util.SystemInfo.isMacOS;
        var content = new WindowContent(WindowRole.MAIN, Rehearsal.launcher(feed), null, false, directory -> { }, () -> { },
            () -> { }, themes, KeyBindings.defaults(macOs), new CommandHistory(), macOs, windowLook().chrome());
        content.applyConfiguration(snapshot, macOs);
        var window = new WindowFrame(content, windowLook(), snapshot, JasperWindow.Events.NONE);
        // The pane's launch completion is queued, so this is in place before a failed launch could ask for a dialog.
        Rehearsal.silence(content);
        // Discarded, not closed: never shown and at the default size, it must not become the last window closed.
        Runnable close = () -> Rehearsal.closeAndDispose(() -> {
            content.forgetLayout();
            try { content.close(); } finally { window.discard(); }
        }, window.nativeWindow()::dispose);
""", 1),
    ("""            window.content().connectContributions(contributions, rehearsalState, false);
""", """            content.connectContributions(contributions, rehearsalState, false);
""", 1),
    ("""        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(), this::quit, dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));
""", """        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> windows.openTerminalWindow(null), this::quit, dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));
""", 1),
    ("""        contributions.tabs().setFactory(separate -> {
            var created = separate ? newWindow(null) : newWindow();
            if (created == null) throw new IllegalStateException("Application is closing");
            return created.content().id();
        });
""", "", 1),
    ("""            java.util.UUID owner = ownerOrNull != null ? ownerOrNull
                : lastActive != null && windows.contains(lastActive) ? lastActive.content().id() : null;
""", """            java.util.UUID owner = ownerOrNull != null ? ownerOrNull : windows.lastActive().map(JasperWindow::id).orElse(null);
""", 1),
    ("""        boolean residentIdle = resident && startupSettled && !windowsOpen() && openingWindows == 0;
""", """        boolean residentIdle = resident && startupSettled && !windowsOpen() && windows.opening() == 0;
""", 1),
    ("""        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive : windows.stream().findFirst().orElse(null);
        if (target == null) return;
        target.toFront();
""", """        JasperWindow target = windows.lastActive().orElse(null);
        if (target == null) return;
        target.toFront();
""", 1),
])
span(A, "    /** A terminal window's native window, for parenting application-built dialogs; null when it is gone. */\n",
     "    private Optional<TerminalWindow> openWindowWith(UUID id) {\n        for (TerminalWindow window : windows) if (window.content().id().equals(id)) return Optional.of(window);\n        return Optional.empty();\n    }\n",
     r'''    /** A window's native window, for parenting application-built dialogs; null when it is gone. */
    private java.awt.Window nativeWindow(java.util.UUID id) {
        return windows.window(id).map(JasperWindow::nativeWindow).orElse(null);
    }

    /**
     * Forgets a window the application no longer holds: out of the notification cards and the Buddy's windows, and with
     * no window left nothing counts as activated. The window manager forgot it first. EDT.
     */
    private void forget(JasperWindow window) {
        memoryIndicator.windows(windows.windows().size());
        if (window != null) { // tests close "the last window" with null
            windowTraces.remove(window.id());
            notificationViews.detach(window.content());
        }
        buddy.removeWindow(window);
        if (windows.isEmpty()) activatedSinceOpen = false;
    }

    void windowClosed(JasperWindow window) {
        forget(window);
        // Residency keeps the warm process: the command history, the plugins and the configuration
        // watcher are precisely what makes the next window fast, and shutdown would close all of them.
        // Quit still terminates.
        boolean pluginWindowsOpen = auxiliary != null && !auxiliary.open().isEmpty();
        if (windows.isEmpty() && !resident && !pluginWindowsOpen) requestShutdown();
        else {
            // The next window's timing says how long this process sat without one.
            if (windows.isEmpty()) trace.windowsClosed();
            updateBuddyActions();
            updateDock();
        }
    }

    /** The Buddy is the main window's (workspace tabs spec 6.2): it shows while the main window does. */
    private void windowStateChanged(WindowManager.State state) {
        if (!state.window().isMain()) return;
        buddy.window(state.window(), state.showing(), state.iconified());
        updateBuddyActions();
    }

    /** Coming back to a Jasper window is attention: the buddy wakes and waves. */
    void windowActivated(JasperWindow window) {
        activatedSinceOpen = true;
        // The cards live in the main window (workspace tabs spec 6.2): coming back to any window shows those that waited.
        windows.main().ifPresent(main -> notificationViews.activated(main.content()));
        systemNotices.focused();
        terminals.windowActivated(window.id());
        if (buddy != null && !quitting && !stopped) buddy.greet();
    }

    private boolean owns(Component component) {
        for (JasperWindow window : windows.windows()) if (window.owns(component)) return true;
        return false;
    }

    void toggleBuddy() { buddy.toggle(); updateBuddyActions(); }

    boolean buddyEnabled() { return buddy.enabled(); }

    private void raiseTerminal() { windows.lastActive().ifPresent(JasperWindow::toFront); }

    /**
     * Brings a window to the front for a click on an OS notification: the notification's window while it is open (the
     * window holding its pane now, {@link #holderOf}), else the one used last, and with none open the main window,
     * restored first. Names the window; empty while Jasper quits.
     */
    private Optional<UUID> raiseFor(Optional<UUID> target) {
        if (quitting || stopped) return Optional.empty();
        Optional<JasperWindow> window = target.flatMap(windows::window).or(windows::lastActive)
            .or(() -> windows.ensureMain("notification", null));
        window.ifPresent(JasperWindow::toFront);
        return window.map(JasperWindow::id);
    }

    /** The Notifications panel is the main window's (workspace tabs spec 6.2): it shows there, whichever window a click raised. */
    private void showHistory(UUID window) {
        MainWindow target = windows.main().orElse(null);
        if (target == null) { notificationViews.showHistory(window); return; }
        if (!target.id().equals(window)) target.toFront();
        notificationViews.showHistory(target.id());
    }

    /**
     * The window that holds the pane a command's notification came from, when the click arrives: the pane's tab may have
     * moved to another window since it posted (workspace tabs spec 9.3). Empty for any other notification.
     */
    private Optional<UUID> holderOf(UUID notification) {
        return notifications.producer(notification).filter(UUID.class::isInstance).map(UUID.class::cast)
            .flatMap(terminals::pane).flatMap(pane -> terminals.tab(pane.tabId())).map(dev.jasper.app.terminals.TabEntry::windowId);
    }
''')
edit(A, [
    ("""        for (TerminalWindow window : List.copyOf(windows)) window.content().updateActions();
""", """        for (JasperWindow window : windows.windows()) window.content().updateActions();
""", 1),
    ("""        if (!windowsMayClose()) return;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        if (auxiliary != null) auxiliary.close();
""", """        if (!windowsMayClose()) return;
        // Every child window, then the main window (workspace tabs spec 6.4).
        windows.closeAll();
        if (auxiliary != null) auxiliary.close();
""", 1),
    ("""        quitting = true;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        requestShutdown();
""", """        quitting = true;
        windows.closeAll();
        requestShutdown();
""", 1),
])
edit(A, [("            // This bypasses openWindow: no window timing and no FIRST_PAINT or OUTPUT_PAINTED, which would settle\n",
          "            // This bypasses the window manager: no window timing and no FIRST_PAINT or OUTPUT_PAINTED, which would settle\n", 1)])
PY
```

- [ ] **Step 5: The child window's mount, the rehearsal and the benchmark**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowContent.java", [
    ("""     * shows it while the look keeps that on, and otherwise it is a row in the window. TerminalWindow and the chrome
     * renders share this path. Returns the unified bar, or null.
""", """     * shows it while the look keeps that on, and otherwise it is a row in the window. The main window's frame and the
     * chrome renders share this path. Returns the unified bar, or null.
""", 1),
    ("""    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
""", """    /**
     * Mounts a child window's {@code content} in its frame's {@code root} (workspace tabs spec 6.3): the look's title bar
     * (the unified macOS bar with a centred title, or the system's) showing the active tab's title, and no header,
     * toolbar or status bar. On macOS the screen menu bar shows the same menus as the main window's, built from the same
     * menu model, while the look keeps menus there ({@code screenMenuBar}); a child window never shows a menu row, and
     * on Windows and Linux the title pane holds the title alone. Returns the unified bar, or null.
     */
    static MacTitleBar mountChild(JRootPane root, WindowContent content, WindowLook look, boolean screenMenuBar,
                                  Consumer<String> nativeTitle) {
        // Compact keeps the system's title bar, as the main window's does (spec 10.1).
        boolean compact = content.viewMode == ViewMode.COMPACT;
        MacTitleBar bar = null;
        if (compact) root.setContentPane(content);
        else bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), look.unifiedTitle());
        if (bar != null) {
            MacTitleBar unified = bar;
            unified.setStyle(MacTitleBar.Style.WINDOW);
            // The native controls sit on the title bar, which a theme may make dark on a light theme or light on a dark one.
            content.onThemeChanged = theme -> unified.setLight(!dev.jasper.ui.theme.TitleBarTheme.of(theme.chrome()).dark());
        } else if (content.macOs && (compact || look.followThemeAppearance())) {
            content.onThemeChanged = theme -> dev.jasper.app.platform.MacAppearance.follow(root, theme.chrome().dark());
        } else if (!content.macOs) {
            // FlatLaf's title pane holds the title alone, in the chrome's colours, as the main window's does.
            content.onThemeChanged = theme -> {
                root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(null, SemanticColor.SURFACE_APP));
                root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
            };
        }
        content.onThemeChanged.accept(content.theme());
        content.followsThemeAppearance = bar == null && look.followThemeAppearance() && content.macOs;
        if (screenMenuBar) root.setJMenuBar(content.menuBar());
        MacTitleBar titled = bar;
        content.onTitle = title -> {
            nativeTitle.accept(title);
            if (titled != null) titled.setTitle(title);
        };
        if (content.bodyLease != null) content.bodyLease.close();
        content.bodyLease = dev.jasper.app.platform.WindowBody.install(root, content,
            () -> new java.awt.Rectangle(0, 0, content.getWidth(), content.getHeight()));
        content.update();
        return bar;
    }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java", [
    ("import dev.jasper.app.workspace.WindowCallbacks;\n", "", 1),
    ("""    /** A rehearsal window asks nothing of the application. */
    static final WindowCallbacks CALLBACKS = new WindowCallbacks(directory -> { }, () -> { }, window -> { }, window -> { },
        state -> { });

""", "", 1)])
B = "jasper-app/src/main/java/dev/jasper/app/benchmark/BenchmarkRun.java"
edit(B, [
    ("import dev.jasper.app.workspace.TerminalWindow;\n", "import dev.jasper.app.workspace.JasperWindow;\n", 1),
    ('env.put("ownership", "JasperApplication / TerminalWindow / WindowContent / TerminalPane / TerminalSession");',
     'env.put("ownership", "JasperApplication / WindowManager / JasperWindow / WindowContent / TerminalPane / TerminalSession");', 1),
    ("            window = application.newWindow(directory);\n",
     "            window = application.windows().ensureMain(\"menu\", directory).orElseThrow();\n", 1),
    ("""            TerminalWindow temporaryWindow = edt(() -> {
                TerminalWindow created = application.newWindow(directory);
""", """            JasperWindow temporaryWindow = edt(() -> {
                JasperWindow created = application.windows().openTerminalWindow(directory).orElseThrow();
""", 1),
    ("            edt(() -> { temporaryWindow.close(); return null; });\n", "            edt(() -> { temporaryWindow.requestClose(); return null; });\n", 1),
    ('result.put("windowSize", edt(() -> Map.of("width", window.size().width, "height", window.size().height)));',
     'result.put("windowSize", edt(() -> Map.of("width", window.nativeWindow().getWidth(), "height", window.nativeWindow().getHeight())));', 1)])
regex(B, r"\bwindow\.resize\(", "window.nativeWindow().setSize(", 3)
regex(B, r"\bTerminalWindow\b", "JasperWindow", 4)
PY
```

- [ ] **Step 6: The notification click at click time, and `OpenPlan`'s words**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/application/"
edit(A + "OpenPlan.java", [
    (""" * Where an outside request's paths open (workspace-tabs spec 5.3 and 5.4, through today's windows until the main window
 * exists): each directory as a terminal tab, each file in the editor a plugin provides; anything else is skipped and a
 * repeated path opens once. With no window open a window opens first, in the first directory when there is one. Pure.
 *
 * @param newWindow whether a window opens first
""", """ * Where an outside request's paths open (workspace-tabs spec 5.3, 5.4 and 6.4): in the main window, each directory as a
 * terminal tab, each file in the editor a plugin provides; anything else is skipped and a repeated path opens once. With
 * no main window open one opens first, in the first directory when there is one. Pure.
 *
 * @param newWindow whether the main window opens first
""", 1),
    ("""     * @param windowOpen whether a terminal window is open
""", """     * @param windowOpen whether the main window is open
""", 1)])
edit(A + "SystemNotices.java", [
    ("""     * @param showErrors opens the Errors window at that incident
     */
    record Windows(Function<Optional<UUID>, Optional<UUID>> raise, Consumer<UUID> showHistory, Consumer<UUID> showErrors) {
        Windows {
            Objects.requireNonNull(raise, "raise");
            Objects.requireNonNull(showHistory, "showHistory");
            Objects.requireNonNull(showErrors, "showErrors");
        }
    }
""", """     * @param showErrors opens the Errors window at that incident
     * @param holder the window that holds what a notification is about now (a command's pane, whose tab may have moved
     *     since it posted), resolved when the click arrives; empty to use the notification's own target
     */
    record Windows(Function<Optional<UUID>, Optional<UUID>> raise, Consumer<UUID> showHistory, Consumer<UUID> showErrors,
                   Function<UUID, Optional<UUID>> holder) {
        Windows {
            Objects.requireNonNull(raise, "raise");
            Objects.requireNonNull(showHistory, "showHistory");
            Objects.requireNonNull(showErrors, "showErrors");
            Objects.requireNonNull(holder, "holder");
        }
    }
""", 1),
    ("""        Optional<UUID> window = windows.raise().apply(entry.flatMap(found -> found.request().target()));
""", """        Optional<UUID> window = windows.raise().apply(entry.flatMap(found ->
            windows.holder().apply(found.id()).or(() -> found.request().target())));
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java", [
    ("import java.util.Objects;\nimport java.util.OptionalInt;\n", "import java.util.Objects;\nimport java.util.Optional;\nimport java.util.OptionalInt;\n", 1),
    ("""    /** The pane is gone: its running card goes, and its notifications expire, since there is no pane to focus. */
    public void closed(Object key) {
""", """    /**
     * Who posted a notification this notifier still answers for: the key of the pane whose command it reports, until that
     * pane closes. A click resolves the pane's window from it then, since its tab may have moved (workspace tabs spec 9.3).
     *
     * @param notification a notification's id
     * @return the pane's key; empty for any other notification
     */
    public Optional<Object> producer(UUID notification) {
        for (var entry : outcomes.entrySet()) if (entry.getValue().contains(notification)) return Optional.of(entry.getKey());
        return Optional.empty();
    }

    /** The pane is gone: its running card goes, and its notifications expire, since there is no pane to focus. */
    public void closed(Object key) {
""", 1)])
PY
```

- [ ] **Step 7: The one-place rule for the main window, its manager and the frames; the ledger**

One main window per process (spec 6.1) holds only while its manager is made once and the native frame only by the
application: Task 2's rule grows to pin `WindowManager."<init>"` to `JasperApplication`'s constructor and
`WindowFrame."<init>"` to `frameOnScreen` and `rehearsalWindow` (Rulings 3). `JasperApplicationWindowsTest` checks the
same over the class files:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import span
span("gradle/application-architecture.gradle.kts",
     """                // Workspace tabs spec 6.1: the main window is made in one place, WindowManager.ensureMain. The constant pool""",
     """                    "MainWindow is made in exactly one place, WindowManager.ensureMain: $makers"
                }
""", """                // Workspace tabs spec 6.1, one main window per process: MainWindow, its WindowManager and the native
                // WindowFrame are each made only at the sites below, with an explicit `new`. The constant pool (javap -v)
                // also shows a constructor reference (X::new), which javap -c alone would miss; it counts as a site.
                val pinned = mapOf(
                    "dev/jasper/app/workspace/MainWindow" to listOf("dev.jasper.app.workspace.WindowManager.ensureMain"),
                    "dev/jasper/app/workspace/WindowManager" to listOf("dev.jasper.app.application.JasperApplication.<init>"),
                    "dev/jasper/app/workspace/WindowFrame" to listOf("dev.jasper.app.application.JasperApplication.frameOnScreen",
                        "dev.jasper.app.application.JasperApplication.rehearsalWindow"))
                val sites = pinned.keys.associateWith { mutableListOf<String>() }
                val header = Regex("^  \\\\S.*?([\\\\w$.]+)\\\\(.*\\\\)( throws .*)?;$")
                for (batch in types.chunked(100)) {
                    val verbose = tool("javap", listOf("-classpath", classes.absolutePath, "-v", "-p") + batch)
                    for (section in verbose.split(Regex("(?m)^Classfile ")).drop(1)) {
                        if (pinned.keys.none { section.contains("$it.\\"<init>\\"") }) continue
                        val type = Regex("this_class: #\\\\d+\\\\s+// (\\\\S+)").find(section)?.groupValues?.get(1)?.replace('/', '.') ?: continue
                        var method = "(class)"
                        for (line in section.lines()) {
                            header.find(line)?.let { declared ->
                                val name = declared.groupValues[1]
                                method = when {
                                    name.contains('.') -> "<init>"
                                    name.startsWith("lambda$") -> name.removePrefix("lambda$").substringBefore('$').let { if (it == "new") "<init>" else it }
                                    else -> name
                                }
                            }
                            for ((made, list) in sites) {
                                if (!line.contains("$made.\\"<init>\\"") || topLevel(type) == made.replace('/', '.')) continue
                                if (line.contains("invokespecial")) list.add("${topLevel(type)}.$method")
                                else if (line.contains("REF_newInvokeSpecial")) list.add("${topLevel(type)}::new")
                            }
                        }
                    }
                }
                for ((made, allowed) in pinned) check(sites.getValue(made).sorted() == allowed.sorted()) {
                    "${made.substringAfterLast('/')} is made only at ${allowed.joinToString()}: ${sites.getValue(made)}"
                }
""")
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/"
ledger.rename(M + "TerminalWindow.java", M + "WindowFrame.java", "WindowFrame")
ledger.add(T + "application/JasperApplicationWindowsTest.java", "app.application", "JasperApplicationWindowsTest",
           T + "application/JasperApplicationWindowsTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 4a Task 3; the application's windows with stand-in frames")
PY
git grep -nwE 'TerminalWindow|WindowCallbacks|openingWindows|composeWindow|openWindow' -- jasper-app/src || echo "none left"
```

Expected: `none left`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`JasperApplicationWindowsTest` 4, `SystemNoticesTest` and `CommandNotifierTest` one more each,
`WindowShellTest` one more; the background tests reach the frame as before and fail there headlessly, as they assert).
Then `./gradlew verifyApplicationArchitecture` (the one-place rule) and `./gradlew :jasper-app:check`: PASS. To see the
rule bite, add a second `new WindowManager(contributions, …)` field to `JasperApplication`, run
`./gradlew verifyApplicationArchitecture` (FAIL: "WindowManager is made only at …"), and remove it again.

- [ ] **Step 9: Commit**

```bash
git add -A jasper-app/src gradle/application-architecture.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Windows: the application asks the window manager

JasperApplication composes each window's content for WindowManager and
builds its WindowFrame (TerminalWindow, renamed); its window set,
newWindow, openWindow and composeWindow go. Every outside request opens
in the main window, restored first; closing it, Quit and going to the
background close the child windows first. A child window's frame shows a
title bar and, on macOS, the main window's menus in the screen menu bar.
A notification's click raises the window that holds its pane now.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The tab registry asks the windows

Spec 16: the content registry's factory and detached windows go. The window manager gives the registry
`TabRegistry.Windows` (the main window, restored first; whether a window is the main window; a new child window for a
tab); `Tab` and the registry gain Move to New Window and Move to Main Window; `WorkspaceHost` loses `detached`; the
SDK's `ContentTab.move(boolean)` and `isSeparate()` keep their meaning through them until plan 4b (Rulings 13).

**Files:**
- Modify: `contributions/TabRegistry.java`, `contributions/Tab.java`, `contributions/WorkspaceHost.java`,
  `workspace/WindowContent.java`, `workspace/WindowManager.java`, `plugins/HostedUi.java`; the order a move reports
  (Step 4): `jasper-sdk/.../terminal/TerminalEvents.java` (Javadoc), `jasper-sdk/README.md`, the testkit's
  `FakeWorkspace.java`, `FakePluginHost.java` (Javadoc) and `contract/PluginContractTest.java`, and the app's
  `plugins/TerminalFixture.java`
- Test: `contributions/TabRegistryTest.java`, `plugins/AppContractTest.java`, `KitMountingTest.java`,
  `StagedEditorHost.java`, `WorkspaceContentRouteTest.java`, `HostedChooserTest.java`, `workspace/HostedContentTest.java`,
  `PluginPaneAppearanceTest.java`, `WorkspaceThemeTest.java`, `RuledCellsTest.java`, `WindowManagerTest.java`,
  `application/JasperApplicationBackgroundTest.java`

**Interfaces:**
- Consumes: Task 2's `WindowManager.ensureMain`, `moveToNewWindow(Tab)`.
- Produces: `public record TabRegistry.Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow,
  Consumer<UUID> movedOut)` (and its three-function form for tests; `main` gives null while the main window is composed),
  `TabRegistry.windows(Windows)`, `boolean isMain(UUID)`, `void moveToNewWindow(Tab)`, `void moveToMainWindow(Tab)`;
  `Tab.inMainWindow()`, `moveToNewWindow()`, `moveToMainWindow()`; `WorkspaceHost(UUID id, Consumer<Tab> attach,
  Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront[, Function<TabId, Optional<GroupId>> groupOf])`;
  `public boolean WindowManager.isMain(UUID)`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
R = T + "contributions/TabRegistryTest.java"
regex(R, r"new WorkspaceHost\((\w+), (?:false|true), ", r"new WorkspaceHost(\1, ", 19)
regex(R, r"registry\.setFactory\(separate -> separate \? (\w+) : (\w+)\);", r"registry.windows(windows(\2, \1));", 5)
regex(R, r"registry\.move\((\w+), ?true\)", r"registry.moveToNewWindow(\1)", 7)
regex(R, r"registry\.move\(tab, false\)", "registry.moveToMainWindow(tab)", 1)
regex(R, r"tab\.move\(true\)", "tab.moveToNewWindow()", 2)
edit(R, [(
"""        registry.windows(windows(main, detached));
    }
""", """        registry.windows(windows(main, detached));
    }

    /** The window manager's part as a test plays it: {@code main} is the main window, and a new window is {@code separate}. */
    private TabRegistry.Windows windows(UUID main, UUID separate) {
        return new TabRegistry.Windows(() -> main, main::equals, tab -> registry.move(tab, separate));
    }

    /**
     * Workspace plan 4a: the window a move leaves hears of it only after the move has reported itself, so a failed attach
     * can still go back to it; the window manager then closes a child window the move left empty.
     */
    @Test void theWindowAMoveLeavesHearsOfItAfterTheMoveAndAFailedMoveGoesBackToIt() {
        var heard = new ArrayList<String>();
        var child = UUID.randomUUID();
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(main, tab -> heard.add("attach main"), tab -> heard.add("detach main"), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(child, tab -> heard.add("attach child"), tab -> heard.add("detach child"), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(bad, tab -> { throw new IllegalStateException("destination failed"); }, tab -> {}, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> main, main::equals, tab -> {},
            window -> heard.add("moved out of " + (window.equals(child) ? "child" : "main"))));
        var tab = registry.open("plugin.file", "one", "One", child, new JPanel());
        heard.clear();
        tab.moveToMainWindow();
        assertThat(heard).containsExactly("detach child", "attach main", "moved out of child");
        registry.move(tab, child);
        heard.clear();
        assertThatThrownBy(() -> registry.move(tab, bad)).isInstanceOf(IllegalStateException.class).hasMessage("destination failed");
        assertThat(tab.window()).as("back in the window it left, which a move never closes midway").isEqualTo(child);
        assertThat(heard).containsExactly("detach child", "attach child", "moved out of child");
    }

    @Test void withoutTheWindowManagerTheFirstWorkspaceIsTheMainWindowAndNothingMovesToANewWindow() {
        var other = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(main, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(other, mounted::add, mounted::remove, tab -> {}, () -> {}));
        var tab = registry.open("plugin.file", "one", "One", null, new JPanel());
        assertThat(tab.window()).as("a view without a window goes to the main window").isEqualTo(main);
        assertThat(tab.inMainWindow()).isTrue();
        tab.moveToNewWindow();
        assertThat(tab.window()).isEqualTo(main);
        registry.move(tab, other);
        assertThat(tab.inMainWindow()).isFalse();
        tab.moveToMainWindow();
        assertThat(tab.window()).isEqualTo(main);
    }
""", 1)])
for name, count in [("plugins/KitMountingTest.java", 2), ("plugins/StagedEditorHost.java", 2), ("plugins/WorkspaceContentRouteTest.java", 3)]:
    regex(T + name, r"new dev\.jasper\.app\.contributions\.WorkspaceHost\((\w+), ?(?:false|true), ?", r"new dev.jasper.app.contributions.WorkspaceHost(\1, ", count)
edit(T + "plugins/KitMountingTest.java", [(
    "            model.tabs().setFactory(detached -> detached?separate:main);\n",
    "            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> model.tabs().move(tab, separate)));\n", 1)])
edit(T + "plugins/StagedEditorHost.java", [(
    "            contributions.tabs().setFactory(detached -> detached ? separate : main);\n",
    "            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals,\n"
    "                tab -> contributions.tabs().move(tab, separate)));\n", 1)])
edit(T + "plugins/WorkspaceContentRouteTest.java", [
    ("            model.tabs().setFactory(detached -> detached ? separate : main);\n",
     "            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> model.tabs().move(tab, separate)));\n", 1),
    ("            model.tabs().setFactory(detached -> main);\n",
     "            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> { }));\n", 1)])
edit(T + "plugins/HostedChooserTest.java", [(
"""        contributions.tabs().setFactory(separate -> {
            UUID id=fixture.addWindow();contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,separate,e -> {},e -> {},e -> {},() -> {}));return id;
        });
""", """        java.util.function.Supplier<UUID> window = () -> {
            UUID id=fixture.addWindow();contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,e -> {},e -> {},e -> {},() -> {}));return id;
        };
        UUID main=window.get();
        contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> contributions.tabs().move(tab, window.get())));
""", 1)])
edit(T + "plugins/AppContractTest.java", [
    ("""        java.util.function.Function<Boolean, UUID> workspace = separate -> {
            UUID id = terminalFixture.addWindow();
            var tabs = contributions.tabs();
            var registration = tabs.addHost(new dev.jasper.app.contributions.WorkspaceHost(id, separate,
""", """        // The window manager's part: the window added last that no move made is the main window (workspace plan 4a).
        UUID[] main = new UUID[1];
        java.util.Set<UUID> separate = new java.util.HashSet<>();
        java.util.function.Function<Boolean, UUID> workspace = forMove -> {
            UUID id = terminalFixture.addWindow();
            if (forMove) separate.add(id); else main[0] = id;
            var tabs = contributions.tabs();
            var registration = tabs.addHost(new dev.jasper.app.contributions.WorkspaceHost(id,
""", 1),
    ("""            terminalFixture.onWindowClosed(id, registration::close);
            return id;
        };
        onEdt(() -> contributions.tabs().setFactory(workspace));
""", """            terminalFixture.onWindowClosed(id, () -> {
                registration.close();
                if (id.equals(main[0])) main[0] = null;
            });
            return id;
        };
        onEdt(() -> contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(
            () -> main[0] != null ? main[0] : workspace.apply(false), id -> !separate.contains(id),
            tab -> contributions.tabs().move(tab, workspace.apply(true)))));
""", 1)])
edit(T + "application/JasperApplicationBackgroundTest.java", [(
    "content.addHost(new WorkspaceHost(UUID.randomUUID(), false, entry -> { }, entry -> { }, entry -> { }, () -> { }));",
    "content.addHost(new WorkspaceHost(UUID.randomUUID(), entry -> { }, entry -> { }, entry -> { }, () -> { }));", 1)])
edit(T + "workspace/RuledCellsTest.java", [(
    "registry.addHost(new WorkspaceHost(window, false, entry -> { }, entry -> { }, entry -> { }, () -> { }));",
    "registry.addHost(new WorkspaceHost(window, entry -> { }, entry -> { }, entry -> { }, () -> { }));", 1)])
H = T + "workspace/HostedContentTest.java"
edit(H, [
    ("""            model.tabs().setFactory(separate -> {
                created[0] = content(launcher(new ArrayDeque<>()));
                created[0].connectContributions(model, UiState.inMemory());
                return created[0].id();
            });
""", """            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> {
                created[0] = content(launcher(new ArrayDeque<>()));
                created[0].connectContributions(model, UiState.inMemory());
                return created[0].id();
            }, id -> created[0] != null && created[0].id().equals(id), tab -> { }));
""", 1),
    ("""    @Test void detachedWorkspaceDoesNotScheduleATerminalLaunch() throws Exception {
""", """    @Test void anEmptyWorkspaceDoesNotScheduleATerminalLaunch() throws Exception {
""", 1),
    ("""                assertThat(entry.detached()).isTrue();
""", """                assertThat(entry.window()).isEqualTo(owner.id());
""", 1)])
A = T + "workspace/PluginPaneAppearanceTest.java"
edit(A, [
    ("            contributions.tabs().setFactory(separate -> detached.id());\n",
     "            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(owner::id, owner.id()::equals,\n"
     "                tab -> contributions.tabs().move(tab, detached.id())));\n", 1),
    ("if (armed[0]) { armed[0] = false; contributions.tabs().move(entry, true); }",
     "if (armed[0]) { armed[0] = false; contributions.tabs().moveToNewWindow(entry); }", 1),
    ("            contributions.tabs().setFactory(separate -> second.id());\n",
     "            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(first::id, first.id()::equals,\n"
     "                tab -> contributions.tabs().move(tab, second.id())));\n", 1),
    ("            contributions.tabs().move(entry, true);\n", "            contributions.tabs().moveToNewWindow(entry);\n", 1),
    ("            contributions.tabs().move(entry, false);\n", "            contributions.tabs().moveToMainWindow(entry);\n", 1)])
edit(T + "workspace/WorkspaceThemeTest.java", [
    ("detached.connectContributions(model); model.tabs().setFactory(separate -> detached.id());",
     "detached.connectContributions(model);\n"
     "                model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(owner::id, owner.id()::equals,\n"
     "                    tab -> model.tabs().move(tab, detached.id())));", 1),
    ("                model.tabs().move(entry, true);\n", "                model.tabs().moveToNewWindow(entry);\n", 1),
    ("                model.tabs().move(entry, false);\n", "                model.tabs().moveToMainWindow(entry);\n", 1)])
edit(T + "workspace/WindowManagerTest.java", [(
"""    @Test void aRequestWhileTheMainWindowOpensIsRefusedRatherThanOpeningASecondOne() throws Exception {
        edt(() -> {
            reenter = true;
            assertThatThrownBy(() -> manager.ensureMain("cold", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The main window is still opening");
            assertThat(manager.main()).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(model.tabs().tabs()).isEmpty();
            assertThat(manager.ensureMain("cold", null)).as("the next request opens it").isPresent();
        });
        launch();
    }
""", """    /** Workspace tabs spec 6.4: a plugin's tab asked for while the main window is being composed opens there once it is up. */
    @Test void aPluginsTabAskedForWhileTheMainWindowOpensOpensThereOnceItIsUp() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            reenter = true;
            main[0] = manager.ensureMain("cold", null).orElseThrow();
            assertThat(manager.windows()).as("no second main window").containsExactly(main[0]);
            assertThat(early.window()).as("the tab waits for the main window").isNull();
        });
        edt(() -> {
            assertThat(early.window()).as("and opens there on the next turn").isEqualTo(main[0].id());
            assertThat(main[0].content().selectedTab()).isSameAs(early);
        });
        launch();
    }

    /** Workspace tabs spec 6.4: a child window a move empties closes once the move has reported itself. */
    @Test void aChildWindowAMoveEmptiesClosesAfterTheMoveIsReported() throws Exception {
        ChildWindow[] child = new ChildWindow[1];
        dev.jasper.app.terminals.TerminalRegistry[] terminals = new dev.jasper.app.terminals.TerminalRegistry[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            terminals[0] = new dev.jasper.app.terminals.TerminalRegistry();
            for (JasperWindow window : manager.windows()) window.content().connectTerminals(terminals[0], () -> { });
        });
        launch();
        edt(() -> {
            List<String> heard = new ArrayList<>();
            terminals[0].onEvent(event -> {
                if (event instanceof dev.jasper.app.terminals.TerminalEvent.TabMoved) heard.add("tab moved");
                if (event instanceof dev.jasper.app.terminals.TerminalEvent.WindowClosed closed && closed.windowId().equals(child[0].id()))
                    heard.add("child closed");
            });
            Tab tab = child[0].content().selectedTab();
            manager.moveToMainWindow(tab);
            assertThat(heard).containsExactly("tab moved", "child closed");
            assertThat(child[0].closed()).isTrue();
            assertThat(tab.window()).isEqualTo(manager.main().orElseThrow().id());
        });
        launch();
    }
""", 1), (
"""    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
""", """    @Test void aPluginsTabMovesThroughTheRegistryToANewWindowAndBack() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(notes.inMainWindow()).isTrue();
            notes.moveToNewWindow();
            ChildWindow child = manager.children().getFirst();
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(notes.inMainWindow()).isFalse();
            notes.moveToNewWindow();
            assertThat(manager.children()).as("a child window's only tab stays").containsExactly(child);
            notes.moveToMainWindow();
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(child.closed()).isTrue();
        });
        launch();
    }

    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `TabRegistry.Windows`, `windows(...)`, `moveToNewWindow`, `moveToMainWindow`,
`inMainWindow`, and the five-argument `WorkspaceHost`.

- [ ] **Step 3: The registry, the tab, the host and the SDK's bridge**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, span
C = "jasper-app/src/main/java/dev/jasper/app/contributions/"
edit(C + "TabRegistry.java", [
    ("import java.util.function.BiFunction;\nimport java.util.function.Function;\nimport java.util.function.Supplier;\n",
     "import java.util.function.BiFunction;\nimport java.util.function.Consumer;\nimport java.util.function.Predicate;\nimport java.util.function.Supplier;\n", 1),
    ("""    private Function<Boolean, UUID> factory = separate -> { throw new IllegalStateException("No workspace host available"); };
    private UUID lastMain;
""", """    private Windows windows;
""", 1),
    ("""    /** How a window for plugin views is made when none is open; the argument asks for a separate one. */
    public void setFactory(Function<Boolean, UUID> factory) { this.factory = Objects.requireNonNull(factory); }
""", """    /**
     * How the registry reaches the windows (workspace tabs spec 6.4 and 6.5); the window manager gives it.
     *
     * @param main the main window's id, restored first when none is open; null while the main window is being composed,
     *     and a plugin's tab asked for then opens there once it is up
     * @param isMain whether a window is the main window
     * @param newWindow moves a tab into a new child window, or leaves it where it is
     * @param movedOut a move took a tab out of this window, and has reported itself: the window manager closes a child
     *     window the move left empty now, never in the middle of the move
     */
    public record Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut) {
        /** Requires every function. */
        public Windows {
            Objects.requireNonNull(main, "main"); Objects.requireNonNull(isMain, "isMain"); Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(movedOut, "movedOut");
        }

        /**
         * Windows as a test plays them, where a move closes no window.
         *
         * @param main the main window's id
         * @param isMain whether a window is the main window
         * @param newWindow moves a tab into a new child window, or leaves it where it is
         */
        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow) {
            this(main, isMain, newWindow, window -> { });
        }
    }

    /**
     * Where a plugin's view without a window goes and how a tab moves to a new window or the main window. Until the window
     * manager gives this, the first registered workspace counts as the main window and no tab moves to a new window.
     *
     * @param value the window manager's part
     */
    public void windows(Windows value) { windows = Objects.requireNonNull(value, "value"); }

    /**
     * Whether {@code window} is the main window.
     *
     * @param window a window's id, or null
     * @return true for the main window
     */
    public boolean isMain(UUID window) {
        if (window == null) return false;
        if (windows != null) return windows.isMain().test(window);
        return hosts.keySet().stream().findFirst().map(window::equals).orElse(false);
    }
""", 1),
    ("""        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
        if (!host.detached()) lastMain = host.id();
""", """        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
""", 1),
    ("""    /** The user turned to this window; the last such main window takes plugin views opened without an owner. */
    public void activated(UUID id) {
        if (hosts.containsKey(id) && !hosts.get(id).detached()) lastMain = id;
    }
    public boolean detached(UUID id) { var host = hosts.get(id); return host != null && host.detached(); }
""", "", 1),
    ("""    private WorkspaceHost main() {
        var host = hosts.get(lastMain);
        if (host == null) host = hosts.values().stream().filter(h -> !h.detached()).findFirst().orElse(null);
        if (host == null) host = hosts.get(factory.apply(false));
        return Objects.requireNonNull(host, "Factory did not register a workspace");
    }
""", """    /**
     * The main window's workspace, restored first by the window manager when none is open (workspace tabs spec 6.4); null
     * while the main window is being composed.
     */
    private WorkspaceHost main() {
        UUID id = windows != null ? windows.main().get()
            : hosts.keySet().stream().findFirst().orElseThrow(() -> new IllegalStateException("No workspace host available"));
        if (id == null) return null;
        return Objects.requireNonNull(hosts.get(id), "The main window registered no workspace");
    }

    /**
     * Attaches a tab asked for while the main window was being composed (a panel factory or a {@code WINDOW_OPENED}
     * listener, say) once the main window is up, on a later EDT turn (workspace tabs spec 6.4).
     */
    private void later(Tab tab) {
        SwingUtilities.invokeLater(() -> {
            if (tab.closed() || !tabs.contains(tab) || tab.window() != null) return;
            WorkspaceHost host = main();
            if (host == null) { later(tab); return; }
            tab.window(host.id());
            try { host.attach().accept(tab); }
            catch (RuntimeException | Error failure) {
                rollbackAttach(host, tab, failure);
                tab.window(null);
                throw failure;
            }
            select(tab);
        });
    }
""", 1),
    ("""                if (retry == null) retry = main();
                tab.window(retry.id());
""", """                if (retry == null) retry = main();
                if (retry == null) { later(tab); return tab; }
                tab.window(retry.id());
""", 1),
    ("""        if (host == null) host = main();
        tabs.add(tab);
        tab.window(host.id());
""", """        if (host == null) host = main();
        tabs.add(tab);
        if (host == null) { later(tab); return tab; }
        tab.window(host.id());
""", 1),
    ("""        } finally {
            tab.movingFrom = null;
        }
        select(tab);
""", """        } finally {
            tab.movingFrom = null;
            // The window the tab left hears of it only now: a child window the move emptied closes after the move has
            // reported itself, never midway, so a failed attach could still go back to it (workspace plan 4a).
            if (windows != null) windows.movedOut().accept(old.id());
        }
        select(tab);
""", 1),
    ("""    /** Moves a tab to a separate window or back to the main one; a pinned tab or one being closed stays. */
    public void move(Tab tab, boolean separate) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || detached(tab.window()) == separate) return;
        var old = hosts.get(tab.window());
        var next = separate ? hosts.get(factory.apply(true)) : main();
        if (old == null || next == null || old == next) return;
        transfer(tab, old, next);
    }
""", """    /**
     * Move Tab to New Window (workspace tabs spec 6.5): the window manager opens a child window for the tab, or leaves it
     * where it is (a child window's only tab). A pinned tab or one being closed stays.
     *
     * @param tab the tab
     */
    public void moveToNewWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || windows == null) return;
        windows.newWindow().accept(tab);
    }

    /**
     * Move Tab to Main Window (workspace tabs spec 6.5): the end of the main window's active group. A pinned tab, one being
     * closed or one the main window holds stays.
     *
     * @param tab the tab
     */
    public void moveToMainWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || isMain(tab.window())) return;
        WorkspaceHost target = main();
        if (target != null) move(tab, target.id());
    }
""", 1),
    ("""                    for (var host : hosts.values())
                        if (host != old && host != next && !host.detached()) recovery.add(host);
""", """                    for (var host : hosts.values())
                        if (host != old && host != next) recovery.add(host);
""", 1),
])
edit(C + "Tab.java", [
    ("""    /** @return whether a separate window for plugin views holds the tab */
    public boolean detached() { return registry.detached(window); }
""", """    /** @return whether the main window holds the tab (workspace tabs spec 6) */
    public boolean inMainWindow() { return registry.isMain(window); }
""", 1),
    ("""    /** Moves a hosted tab to a separate window or back to the main one (workspace plan 4 replaces this). */
    public void move(boolean separate) { registry.move(this, separate); }
""", """    /** Move Tab to New Window: a new child window takes the tab (workspace tabs spec 6.5). */
    public void moveToNewWindow() { registry.moveToNewWindow(this); }
    /** Move Tab to Main Window: the main window takes the tab (workspace tabs spec 6.5). */
    public void moveToMainWindow() { registry.moveToMainWindow(this); }
""", 1)])
edit(C + "WorkspaceHost.java", [
    (""" * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). A {@code detached} workspace is a
 * separate window for plugin views (workspace plan 4 replaces it). EDT only.
 *
 * @param id the window's id
 * @param detached whether it is a separate window for plugin views
""", """ * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). EDT only.
 *
 * @param id the window's id
""", 1),
    ("""public record WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
""", """public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
""", 1),
    ("""     * @param id the window's id
     * @param detached whether it is a separate window for plugin views
     * @param attach shows a tab the registry placed in this window
""", """     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
""", 1),
    ("""    public WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                         Runnable toFront) {
        this(id, detached, attach, detach, select, toFront, tab -> Optional.empty());
""", """    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront) {
        this(id, attach, detach, select, toFront, tab -> Optional.empty());
""", 1)])
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowContent.java", [
    ("new WorkspaceHost(id, role == WindowRole.CHILD, this::attach,", "new WorkspaceHost(id, this::attach,", 1),
    ("""        if (workspace.isEmpty()) onEmpty.run();
""", """        // A move empties a window only once it has reported itself: the window manager closes it then (TabRegistry.Windows).
        if (workspace.isEmpty() && !tab.moving()) onEmpty.run();
""", 1),
    ("active = value; if (value) registry.activated(id); update();", "active = value; update();", 1),
    ("""        else menu.add(tabMenuItem(tab.detached() ? "Move to Main Window" : "Open in Separate Window", null, () -> tab.move(!tab.detached())));
""", """        else if (tab.inMainWindow()) menu.add(tabMenuItem("Open in Separate Window", null, tab::moveToNewWindow));
        else menu.add(tabMenuItem("Move to Main Window", null, tab::moveToMainWindow));
""", 1)])
edit(W + "WindowManager.java", [
    ("""        // A plugin's view opened with no window goes to the main window, restored first when none is open (spec 6.4).
        model.tabs().setFactory(separate -> (separate ? openChild(true, null, null).map(JasperWindow::id) : ensureMain("plugin", null)
            .map(JasperWindow::id)).orElseThrow(() -> new IllegalStateException("Jasper is quitting")));
""", """        // A plugin's view opened with no window goes to the main window, restored first when none is open (spec 6.4); while
        // the main window is being composed it waits for it. A child window a move empties closes after the move.
        model.tabs().windows(new TabRegistry.Windows(() -> openingMain ? null : ensureMain("plugin", null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException("Jasper is quitting")), this::isMain, this::moveToNewWindow, this::movedOut));
""", 1),
    ("""    void activated(JasperWindow window) {
""", """    /**
     * A move took a tab out of {@code window} and has reported itself: a child window it left empty closes now, so listeners
     * hear {@code TAB_MOVED} before that window's {@code WINDOW_CLOSED} (workspace tabs spec 6.4).
     */
    private void movedOut(UUID window) {
        for (ChildWindow child : List.copyOf(children))
            if (child.id().equals(window) && child.content().tabCount() == 0) child.close();
    }

    void activated(JasperWindow window) {
""", 1),
    ("import dev.jasper.app.contributions.Tab;\n", "import dev.jasper.app.contributions.Tab;\nimport dev.jasper.app.contributions.TabRegistry;\n", 1),
    ("""    /** @return whether no window is open */
""", """    /**
     * Whether {@code id} is the main window's.
     *
     * @param id a window's id, or null
     * @return true for the main window while it is open
     */
    public boolean isMain(UUID id) { return main != null && main.id().equals(id); }

    /** @return whether no window is open */
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java", [
    ("""        public void move(boolean separate) { requireUi("move content"); entry.move(separate); }
        public boolean isSeparate() { requireUi("content host"); return entry.detached(); }
""", """        public void move(boolean separate) {
            requireUi("move content");
            if (separate) entry.moveToNewWindow(); else entry.moveToMainWindow();
        }
        public boolean isSeparate() { requireUi("content host"); return !entry.inMainWindow(); }
""", 1)])
PY
git grep -nE 'setFactory|(tab|entry|registry|tabs\(\))\.detached\(|boolean detached|move\(Tab tab, boolean|\.move\((entry|tab), ?(true|false)\)' -- jasper-app/src || echo "none left"
```

Expected: `none left`.

- [ ] **Step 4: The order a move reports, in the SDK and its fakes**

A child window a move empties now closes after the move (Rulings 23): plugins hear `TAB_MOVED`, then that window's
`WINDOW_CLOSED`. Plan 3b documented the opposite order, so its Javadoc, the README, both fakes and the contract test
follow; the SDK's shape does not change:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TerminalEvents.java", [(
    """     * reports no {@code TAB_CLOSED} or {@code TAB_OPENED} and no {@code PANE_OPENED} or {@code PANE_CLOSED}. A window the
     * move empties reports {@code WINDOW_CLOSED}, and a window opened for the move {@code WINDOW_OPENED}, before it.""",
    """     * reports no {@code TAB_CLOSED} or {@code TAB_OPENED} and no {@code PANE_OPENED} or {@code PANE_CLOSED}. A window
     * opened for the move reports {@code WINDOW_OPENED} before it, and a window the move empties {@code WINDOW_CLOSED}
     * after it.""", 1)])
edit("jasper-sdk/README.md", [(
    """`PANE_CLOSED`, and its pane keeps its id; a window the move empties reports `WINDOW_CLOSED`, and a window opened for the
move `WINDOW_OPENED`, before the `TAB_MOVED`.""",
    """`PANE_CLOSED`, and its pane keeps its id; a window opened for the move reports `WINDOW_OPENED` before the `TAB_MOVED`,
and a window the move empties `WINDOW_CLOSED` after it.""", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeWorkspace.java", [(
    """    /** Moves a tab to another open window, where it is selected; a window left with no tab closes before the move is announced. */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        from.tabs.remove(tab);
        if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
        if (from.tabs.isEmpty()) closeWindow(from);
        tab.window = to;
        to.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        refreshActive();
    }""",
    """    /** Moves a tab to another open window, where it is selected; a window left with no tab closes after the move is announced. */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        from.tabs.remove(tab);
        if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
        tab.window = to;
        to.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty()) closeWindow(from);
        refreshActive();
    }""", 1)])
edit(K + "FakePluginHost.java", [(
    "     * {@code TAB_MOVED}, and a window left with no tab closes before it.",
    "     * {@code TAB_MOVED}, and a window left with no tab closes after it.", 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
    """        assertThat(heard).as("the window the move empties closes first").containsExactly(new TerminalEvents.WindowEvent(first),
            new TerminalEvents.TabMoved(stays, first, second));""",
    """        assertThat(heard).as("the window the move empties closes after the move").containsExactly(
            new TerminalEvents.TabMoved(stays, first, second), new TerminalEvents.WindowEvent(first));""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java", [
    ("""     * PaneOpened or PaneClosed, and a window left with no tab closes before the move is reported (workspace plan 3b
     * Rulings 5, 6).""",
     """     * PaneOpened or PaneClosed, and a window left with no tab closes after the move is reported (workspace plan 3b
     * Rulings 5, 6; plan 4a Rulings 23).""", 1),
    ("""            if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
            if (from.tabs.isEmpty()) closeWindow(from);
            tab.window = to;
            to.tabs.add(tab);
            registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
            select(tab);
""", """            if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
            tab.window = to;
            to.tabs.add(tab);
            registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
            select(tab);
            if (from.tabs.isEmpty()) closeWindow(from);
""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' --continue`
Expected: PASS (`TabRegistryTest` two more, `WindowManagerTest` 19; `AppContractTest` and `FakeContractTest` run the
whole contract suite, the move's order included; `move(true)` and `move(false)` behave as before). Then
`./gradlew :jasper-app:check :jasper-sdk:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src jasper-sdk jasper-sdk-testkit
git commit -F - <<'EOF'
Windows: the tab registry asks the window manager

TabRegistry's factory, detached hosts and move(Tab, boolean) go: the
window manager gives it the main window (restored first), which window is
the main window and a new child window for a tab. Tabs move to a new
window or the main window through it; the SDK's ContentTab.move and
isSeparate keep their meaning until plan 4b. A child window a move
empties closes after the move has reported itself, and a plugin's tab
asked for while the main window is composed opens there once it is up.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: New Terminal Window, Close Window and the moves

Spec 6.5, 6.6 and 11: `new_window` goes everywhere (the action, File, the toolbar, the palette, the auxiliary frames'
File menu, the macOS menu bar item); `new_terminal_window` (⌘N / Ctrl+Shift+N), `close_window` (⇧⌘W / none),
`move_tab_to_new_window` and `move_tab_to_main_window` (no keys) take its place; the tab menu offers the move its window
can make (Rulings 9-12).

**Files:**
- Modify: `commands/ActionId.java`, `workspace/WindowContent.java`, `JasperWindow.java`, `WorkspaceActions.java`,
  `WindowChrome.java`, `WindowCommands.java`, `palette/CommandsScope.java`, `shortcuthelp/ShortcutCatalog.java`,
  `platform/ResidentTray.java`, `application/JasperApplication.java`, `windows/NativeShells.java`
- Test: `KeymapDefaultsTest`, `KeyBindingsTest`, `KeyBindingsContextTest`, `Settings9bPreview`, `ApplicationActionsTest`,
  `ShellProfilePaneTest`, `DesktopTestSupport`, `TabMenuTest`, `ResidentTrayTest`, `ResidentIndicatorTest`,
  `WindowManagerTest`, `WindowChromeTest`

**Interfaces:**
- Consumes: Task 4's `TabRegistry.moveToNewWindow`/`moveToMainWindow`; Task 2's `JasperWindow.requestClose()`.
- Produces: `ActionId.NEW_TERMINAL_WINDOW`, `CLOSE_WINDOW`, `MOVE_TAB_TO_NEW_WINDOW`, `MOVE_TAB_TO_MAIN_WINDOW`
  (`NEW_WINDOW` is gone); package `WindowContent.requestNewTerminalWindow()`, `requestCloseWindow()`,
  `moveToNewWindow(Tab)`, `moveToMainWindow(Tab)`, field `Runnable onCloseWindow`; `ResidentTray.Actions(Runnable open,
  Runnable newTerminalWindow, Runnable settings, Runnable quit)`, `Entry.NEW_TERMINAL_WINDOW`; `NativeShells(ThemeController,
  UiState, Function<UUID, Window>, Runnable quit[, ShellChrome])`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "config/KeymapDefaultsTest.java", [(
    '        new Row("new_window", KeyContext.GLOBAL, "cmd+n", "ctrl+shift+n"),                     // 1\n',
    '        new Row("new_terminal_window", KeyContext.GLOBAL, "cmd+n", "ctrl+shift+n"),            // 1, workspace tabs spec 11\n'
    '        new Row("close_window", KeyContext.GLOBAL, "cmd+shift+w", "none"),                    // workspace tabs spec 11\n'
    '        new Row("move_tab_to_new_window", KeyContext.GLOBAL, "none", "none"),                 // workspace tabs spec 11\n'
    '        new Row("move_tab_to_main_window", KeyContext.GLOBAL, "none", "none"),                // workspace tabs spec 11\n', 1)])
edit(T + "config/KeyBindingsTest.java", [
    ("            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP, ActionId.NEW_WINDOW, ActionId.OPEN_FILE,\n",
     "            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP, ActionId.NEW_TERMINAL_WINDOW, ActionId.CLOSE_WINDOW,\n"
     "            ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_MAIN_WINDOW, ActionId.OPEN_FILE,\n", 1),
    ('Map.of("new_tab", "F13", "new_window", "F24")', 'Map.of("new_tab", "F13", "new_terminal_window", "F24")', 1),
    ("contains(ActionId.NEW_WINDOW);", "contains(ActionId.NEW_TERMINAL_WINDOW);", 1)])
regex(T + "config/KeyBindingsContextTest.java", r'"new_window"', '"new_terminal_window"', 3)
edit(T + "settingsui/Settings9bPreview.java", [('"@tree/shortcuts/action/new_window/title"', '"@tree/shortcuts/action/new_terminal_window/title"', 1)])
edit(T + "workspace/ApplicationActionsTest.java", [('assertThat(labels).containsSubsequence("New Window", "Open File…");',
                                                     'assertThat(labels).containsSubsequence("New Terminal Window", "Open File…", "Close Window");', 1)])
edit(T + "workspace/ShellProfilePaneTest.java", [("            owner[0].requestNewWindow();\n", "            owner[0].requestNewTerminalWindow();\n", 1)])
regex(T + "workspace/DesktopTestSupport.java", r"\bnewWindow\b", "newTerminalWindow", 2)
edit(T + "workspace/TabMenuTest.java", [
    ('assertThat(titles(menu)).containsExactly("Rename Tab", "Close Tab", "Close Others", "Close Group");',
     'assertThat(titles(menu)).containsExactly("Rename Tab", "Move to New Window", "Close Tab", "Close Others", "Close Group");', 1),
    ('assertThat(titles(hosted)).containsExactly("Open in Separate Window", "Close Tab", "Close Others", "Close Group", "Save");',
     'assertThat(titles(hosted)).containsExactly("Move to New Window", "Close Tab", "Close Others", "Close Group", "Save");', 1),
    ('assertThat(items(window, "File")).containsSubsequence("New Tab", "New Window", "Open File…", "Close Tab", "Close Group");',
     'assertThat(items(window, "File")).containsSubsequence("New Tab", "New Terminal Window", "Open File…", "Close Tab", "Close Group",\n'
     '                "Close Window");', 1)])
edit(T + "platform/ResidentTrayTest.java", [
    ("    @Test void theMenuBarItemOffersANewWindowSettingsAndTheRealQuit() {\n",
     "    @Test void theMenuBarItemOffersANewTerminalWindowSettingsAndTheRealQuit() {\n", 1),
    ('.containsExactly("New Window", "Settings…", null, "Quit Jasper Completely");',
     '.containsExactly("New Terminal Window", "Settings…", null, "Quit Jasper Completely");', 1),
    ('() -> ran.add("new window")', '() -> ran.add("new terminal window")', 1),
    ('assertThat(ran).containsExactly("open", "new window", "settings", "quit", "quit");',
     'assertThat(ran).containsExactly("open", "new terminal window", "settings", "quit", "quit");', 1)])
edit(T + "application/ResidentIndicatorTest.java", [(
    "        edt(() -> indicator.actions().get().newWindow().run());\n",
    "        edt(() -> indicator.actions().get().newTerminalWindow().run());\n", 1)])
edit(T + "workspace/WindowManagerTest.java", [
    ("import dev.jasper.app.appearance.ThemeTestSupport;\n", "import dev.jasper.app.appearance.ThemeTestSupport;\nimport dev.jasper.app.commands.ActionId;\n", 1),
    ("""    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
""", """    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = manager.ensureMain("cold", null).orElseThrow();
            main[0].content().newTab(directory);
        });
        launch();
        edt(() -> main[0].content().invoke(ActionId.NEW_TERMINAL_WINDOW));
        launch();
        assertThat(started).as("New Terminal Window: in the current terminal's directory").last().isEqualTo(directory);
        edt(() -> {
            ChildWindow child = manager.children().getFirst();
            child.content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(child.closed()).as("Close Window").isTrue();
            Tab tab = main[0].content().selectedTab();
            main[0].content().invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            ChildWindow moved = manager.children().getFirst();
            assertThat(tab.window()).isEqualTo(moved.id());
            moved.content().updateActions();
            assertThat(moved.content().action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("its only tab stays").isFalse();
            assertThat(moved.content().action(ActionId.MOVE_TAB_TO_MAIN_WINDOW).isEnabled()).isTrue();
            moved.content().invoke(ActionId.MOVE_TAB_TO_MAIN_WINDOW);
            assertThat(tab.window()).isEqualTo(main[0].id());
            assertThat(moved.closed()).isTrue();
            main[0].content().updateActions();
            assertThat(main[0].content().action(ActionId.MOVE_TAB_TO_MAIN_WINDOW).isEnabled()).isFalse();
            main[0].content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(manager.isEmpty()).as("Close Window on the main window closes it").isTrue();
        });
    }

    @Test void aChildWindowsTabMenuMovesToTheMainWindow() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            var menu = child.content().tabMenu(child.content().selectedTab());
            var titles = new ArrayList<String>();
            for (var item : menu.getComponents()) if (item instanceof javax.swing.JMenuItem entry) titles.add(entry.getText());
            assertThat(titles).containsExactly("Rename Tab", "Move to Main Window", "Close Tab", "Close Others", "Close Group");
        });
        launch();
    }

    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
""", 1)])
edit(T + "workspace/WindowChromeTest.java", [(
    'assertThat(shape).containsExactly("New tab", "New window", "|",',
    'assertThat(shape).containsExactly("New tab", "New terminal window", "|",', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `ActionId.NEW_TERMINAL_WINDOW`, `CLOSE_WINDOW`, `MOVE_TAB_TO_NEW_WINDOW`,
`requestNewTerminalWindow`, `Actions.newTerminalWindow`.

- [ ] **Step 3: The commands, menus, palette, tray and auxiliary frames**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
M = "jasper-app/src/main/java/dev/jasper/app/"
W = M + "workspace/"
edit(M + "commands/ActionId.java", [(
    '    NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n"),\n',
    '    NEW_TERMINAL_WINDOW("new_terminal_window", "New Terminal Window", "cmd+n", "ctrl+shift+n"),\n'
    '    CLOSE_WINDOW("close_window", "Close Window", "cmd+shift+w", "none"),\n'
    '    MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window", "Move Tab to New Window", "none", "none"),\n'
    '    MOVE_TAB_TO_MAIN_WINDOW("move_tab_to_main_window", "Move Tab to Main Window", "none", "none"),\n', 1)])
regex(W + "WindowContent.java", r"\bnewWindow\b", "newTerminalWindow", 19)
edit(W + "WindowContent.java", [
    ("    void requestNewWindow() { newTerminalWindow.accept(directory()); }\n",
     """    /** New Terminal Window (workspace tabs spec 6.5): a child window with a terminal tab where a tab from here starts. */
    void requestNewTerminalWindow() { newTerminalWindow.accept(directory()); }
    /** Close Window (workspace tabs spec 6.4 and 11): this window, as its title bar's close button closes it. */
    void requestCloseWindow() { onCloseWindow.run(); }
    /** Move Tab to New Window (workspace tabs spec 6.5); the window manager decides whether it moves. */
    void moveToNewWindow(Tab tab) { if (tab != null) registry.moveToNewWindow(tab); }
    /** Move Tab to Main Window (workspace tabs spec 6.5). */
    void moveToMainWindow(Tab tab) { if (tab != null) registry.moveToMainWindow(tab); }
""", 1),
    ("""    Runnable onMinimumSizeChanged = () -> {};
""", """    Runnable onMinimumSizeChanged = () -> {};
    /** What Close Window does: the window it belongs to sets it (workspace tabs spec 6.4). */
    Runnable onCloseWindow = () -> {};
""", 1),
    ("""        if (tab.kind() == Tab.Kind.TERMINAL)
            menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(tab); invoke(ActionId.RENAME_TAB); }));
        else if (tab.inMainWindow()) menu.add(tabMenuItem("Open in Separate Window", null, tab::moveToNewWindow));
        else menu.add(tabMenuItem("Move to Main Window", null, tab::moveToMainWindow));
""", """        if (tab.kind() == Tab.Kind.TERMINAL)
            menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(tab); invoke(ActionId.RENAME_TAB); }));
        if (isMain()) menu.add(tabMenuItem("Move to New Window", ActionId.MOVE_TAB_TO_NEW_WINDOW, () -> moveToNewWindow(tab)));
        else menu.add(tabMenuItem("Move to Main Window", ActionId.MOVE_TAB_TO_MAIN_WINDOW, () -> moveToMainWindow(tab)));
""", 1),
    ("""     * A tab's menu (workspace plan 3a Rulings 13): Rename Tab for a terminal tab, Open in Separate Window or Move to
     * Main Window for a plugin's tab, Close Tab, Close Others and Close Group, then the content's commands.
""", """     * A tab's menu (workspace tabs spec 11): Rename Tab for a terminal tab, Move to New Window in the main window or Move
     * to Main Window in a child window, Close Tab, Close Others and Close Group, then the content's commands.
""", 1)])
edit(W + "JasperWindow.java", [(
"""        this.content = Objects.requireNonNull(content, "content");
    }
""", """        this.content = Objects.requireNonNull(content, "content");
        content.onCloseWindow = this::requestClose;
    }
""", 1)])
edit(W + "WorkspaceActions.java", [
    ("            case NEW_WINDOW -> owner.requestNewWindow();\n",
     "            case NEW_TERMINAL_WINDOW -> owner.requestNewTerminalWindow();\n"
     "            case CLOSE_WINDOW -> owner.requestCloseWindow();\n"
     "            case MOVE_TAB_TO_NEW_WINDOW -> owner.moveToNewWindow(owner.selectedTab());\n"
     "            case MOVE_TAB_TO_MAIN_WINDOW -> owner.moveToMainWindow(owner.selectedTab());\n", 1),
    ("case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT, SPLIT_DOWN -> true;",
     "case COMMAND_PALETTE, NEW_TAB, NEW_TERMINAL_WINDOW, CLOSE_WINDOW, OPEN_FILE, QUIT, SPLIT_RIGHT, SPLIT_DOWN -> true;\n"
     "                    // A child window's only tab stays: that window already is its own (workspace tabs spec 6.5).\n"
     "                    case MOVE_TAB_TO_NEW_WINDOW -> owner.selectedTab() != null && (owner.isMain() || owner.tabCount() > 1);\n"
     "                    case MOVE_TAB_TO_MAIN_WINDOW -> owner.selectedTab() != null && !owner.isMain();", 1)])
edit(W + "WindowChrome.java", [
    ("""        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP,
            ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
""", """        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_TERMINAL_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP,
            ActionId.CLOSE_WINDOW, ActionId.OPEN_SETTINGS, ActionId.RELOAD_CONFIG, ActionId.QUIT);
""", 1),
    ('addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");',
     'addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_TERMINAL_WINDOW, "app-window");', 1),
    ('case NEW_TAB -> "New tab"; case NEW_WINDOW -> "New window";', 'case NEW_TAB -> "New tab"; case NEW_TERMINAL_WINDOW -> "New terminal window";', 1)])
edit(W + "WindowCommands.java", [
    ('case NEW_TAB -> "square-plus"; case NEW_WINDOW -> "app-window";', 'case NEW_TAB -> "square-plus"; case NEW_TERMINAL_WINDOW -> "app-window";', 1),
    ("""                case OPEN_FILE -> List.of("open", "editor", "browse");
""", """                case OPEN_FILE -> List.of("open", "editor", "browse");
                case NEW_TERMINAL_WINDOW -> List.of("new window", "shell", "child window");
                case CLOSE_WINDOW -> List.of("window", "close");
                case MOVE_TAB_TO_NEW_WINDOW -> List.of("detach", "separate window", "child window");
                case MOVE_TAB_TO_MAIN_WINDOW -> List.of("attach", "main window");
""", 1)])
edit(M + "palette/CommandsScope.java", [(
    'List.of("new_tab", "split_right", "open_settings", "new_window");', 'List.of("new_tab", "split_right", "open_settings", "new_terminal_window");', 1)])
edit(M + "shortcuthelp/ShortcutCatalog.java", [(
    "            case NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, SELECT_TAB_1, SELECT_TAB_2,\n",
    "            case NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, MOVE_TAB_TO_NEW_WINDOW, MOVE_TAB_TO_MAIN_WINDOW,\n"
    "                SELECT_TAB_1, SELECT_TAB_2,\n", 1)])
edit(M + "platform/ResidentTray.java", [
    ("     * @param newWindow macOS: always opens a new window\n",
     "     * @param newTerminalWindow macOS: opens a window with a terminal tab, after the main window is restored\n", 1),
    ("    public record Actions(Runnable open, Runnable newWindow, Runnable settings, Runnable quit) {\n",
     "    public record Actions(Runnable open, Runnable newTerminalWindow, Runnable settings, Runnable quit) {\n", 1),
    ('            Objects.requireNonNull(newWindow, "newWindow");\n', '            Objects.requireNonNull(newTerminalWindow, "newTerminalWindow");\n', 1),
    ('        OPEN("Open Jasper"), NEW_WINDOW("New Window"), SETTINGS("Settings…"), SEPARATOR(null),\n',
     '        OPEN("Open Jasper"), NEW_TERMINAL_WINDOW("New Terminal Window"), SETTINGS("Settings…"), SEPARATOR(null),\n', 1),
    ("                case NEW_WINDOW -> actions.newWindow();\n", "                case NEW_TERMINAL_WINDOW -> actions.newTerminalWindow();\n", 1),
    ("        return mac ? List.of(Entry.NEW_WINDOW, Entry.SETTINGS, Entry.SEPARATOR, Entry.QUIT_COMPLETELY)\n",
     "        return mac ? List.of(Entry.NEW_TERMINAL_WINDOW, Entry.SETTINGS, Entry.SEPARATOR, Entry.QUIT_COMPLETELY)\n", 1)])
A = M + "application/JasperApplication.java"
edit(A, [
    ("() -> open(List.of(), \"menu\"), this::newWindowFromMenuBar, this::settingsFromMenuBar, this::quit));",
     "() -> open(List.of(), \"menu\"), this::newTerminalWindowFromMenuBar, this::settingsFromMenuBar, this::quit));", 1),
    ("""     * The menu bar item's New Window: a child window with a terminal tab, after the main window is restored (workspace
     * tabs spec 6.4), in front of other applications. EDT.
     */
    private void newWindowFromMenuBar() {
""", """     * The menu bar item's New Terminal Window: a child window with a terminal tab, after the main window is restored
     * (workspace tabs spec 6.4 and 6.5), in front of other applications. EDT.
     */
    private void newTerminalWindowFromMenuBar() {
""", 1),
    ("""        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> windows.openTerminalWindow(null), this::quit, dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));
""", """        shells = new NativeShells(themes, uiState, this::nativeWindow, this::quit,
            dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));
""", 1)])
N = M + "windows/NativeShells.java"
edit(N, [
    ("    private final Runnable newWindow;\n", "", 1),
    ("""     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit) {
        this(themes, state, terminalWindows, newWindow, quit, ShellChrome.standard());
    }
""", """     * @param terminalWindows finds a Jasper window's native window by id, or returns null
     * @param quit quits the application
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows, Runnable quit) {
        this(themes, state, terminalWindows, quit, ShellChrome.standard());
    }
""", 1),
    ("""     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     * @param shell the look's title bar, menu row and appearance
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit, ShellChrome shell) {
""", """     * @param terminalWindows finds a Jasper window's native window by id, or returns null
     * @param quit quits the application
     * @param shell the look's title bar, menu row and appearance
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows, Runnable quit,
                        ShellChrome shell) {
""", 1),
    ("        this.newWindow = Objects.requireNonNull(newWindow); this.quit = Objects.requireNonNull(quit);\n",
     "        this.quit = Objects.requireNonNull(quit);\n", 1),
    ("""    /** A minimal menu bar: macOS otherwise shows only the application menu while this window has focus. */
""", """    /**
     * A minimal menu bar, Close Window and, off macOS, Quit (workspace tabs spec 6.6): macOS otherwise shows only the
     * application menu while this window has focus.
     */
""", 1),
    ("""        var open = new JMenuItem("New Window");
        open.addActionListener(event -> newWindow.run());
""", "", 1),
    ("        file.add(open); file.add(close);\n", "        file.add(close);\n", 1)])
PY
git grep -nE 'ActionId\.NEW_WINDOW|case NEW_WINDOW[ ,]|"new_window"|requestNewWindow|newWindowFromMenuBar|Open in Separate Window|"New Window"' -- jasper-app/src || echo "none left"
```

Expected: `none left` (the `app-window` icon, `NEW_WINDOW` in `AppIcons` and `NamedIcons`, stays: New Terminal Window's button shows it).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.windows.*' --continue`
Expected: PASS (`WindowManagerTest` 20). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Windows: New Terminal Window, Close Window and the tab moves

new_window goes from the action catalog, File, the toolbar, the palette,
the auxiliary frames' File menu and the macOS menu bar item.
new_terminal_window (Cmd+N, Ctrl+Shift+N) opens a child window in the
current terminal's directory; close_window (Shift+Cmd+W) closes the
focused window; move_tab_to_new_window and move_tab_to_main_window move
the active tab, from the tab menu and the palette.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: "Jasper window" in the application's and the plugins' words

Spec 14's wording row, which plan 3b applied to everything a plugin reads and left the rest of to this plan (its Rulings
11): with a main window and child windows, every window is a Jasper window. The application's comments and user-facing
text, the plugins' comments and messages, and the user guides say "Jasper window", or "the main window" where only the
main window has the thing (Rulings 22).

**Files:**
- Modify (comments): `application/JasperApplication.java`, `NotificationsFeature.java`, `commands/KeyContext.java`,
  `files/ChooserRequest.java`, `ChooserSource.java`, `notifications/BuddyVisibility.java`, `platform/ClassicMenuRow.java`,
  `plugins/FileEditors.java`, `HostedUi.java`, `PluginRuntime.java`, `terminals/package-info.java`,
  `windows/AuxiliaryWindows.java`, `NativeShells.java`, `ShellChrome.java`, `workspace/MenuBarItems.java`, `WindowLook.java`; the Editor's `DefaultEditor` and `EditorWindow`,
  File Servers' `FileServerPlugin`, Remote's `RemoteHosts` and `RemoteEditing`, Vault's `VaultPlugin`
- Modify (text): `shortcuthelp/ShortcutCatalog.java:59`, Remote's `chooser/RemoteHosts.java:105` and
  `edit/EditChannels.java:99`; `docs/app-maintenance.md:636-638`, `docs/configuration.md:241-244`,
  `docs/editor.md:9-11,143`, `docs/editor-verification.md:9,29`, `docs/keyboard-shortcuts.md:8-9,26-27,42,76-77,94-95`,
  `docs/remote.md:233-234`
- Test: Remote's `EditChannelsTest`, `RemoteEditingTest`; descriptions and comments in `JasperApplicationBackgroundTest`,
  `PresentationReachTest`, `StagedRemoteHost`, `ShortcutHelpTest`, `ShortcutSectionsTest`, `AuxiliaryWindowsTest`,
  `VaultSheetRenderTest`, the Editor's `DefaultEditorTest` and `EditorWindowTest`, Remote's `RemoteEditingPluginTest` and
  `TestWindow`

**Interfaces:**
- Consumes: Tasks 1-5 (the main window and child windows exist).
- Produces: no API; the strings above.

- [ ] **Step 1: Write the failing tests**

Remote's two sign-in failures name a Jasper window:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
R = "plugins/remote/src/"
edit(R + "test/java/dev/jasper/remote/edit/EditChannelsTest.java", [
    ('"Reconnecting to prod-web needs an open terminal window for the sign-in prompt."', '"Reconnecting to prod-web needs an open Jasper window for the sign-in prompt."', 1)])
edit(R + "test/java/dev/jasper/remote/edit/RemoteEditingTest.java", [
    ('"Reconnecting to prod needs an open terminal window for the sign-in prompt."', '"Reconnecting to prod needs an open Jasper window for the sign-in prompt."', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests '*EditChannelsTest' --tests '*RemoteEditingTest' --continue`
Expected: FAIL, "Expecting message … needs an open Jasper window … but was … needs an open terminal window …" in both.

- [ ] **Step 3: The words**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit, regex
M, T = "jasper-app/src/main/java/dev/jasper/app/", "jasper-app/src/test/java/dev/jasper/app/"
edit(M + "application/JasperApplication.java", [
    ("    /** What every terminal window of this process is made of:", "    /** What every Jasper window of this process is made of:", 1),
    ("    /** What terminal windows and the rehearsal's are made of", "    /** What Jasper windows and the rehearsal's are made of", 1),
    ("no terminal window being opened)", "no Jasper window being opened)", 1),
    ("     * terminal windows: the next one still shows what waited. EDT.", "     * Jasper windows: the next one still shows what waited. EDT.", 1)])
edit(M + "application/NotificationsFeature.java", [
    (" * badge and actions, and one card layer per terminal window, routing each requested card to its target window or the\n * window used last.",
     " * badge and actions, and one card layer per attached window (the application attaches the main window's), routing each\n * requested card to its target window or the window used last.", 1),
    ("    /** A terminal window was activated:", "    /** A Jasper window was activated:", 1)])
edit(M + "commands/KeyContext.java", [("applies anywhere in a terminal window and", "applies anywhere in a Jasper window and", 1)])
edit(M + "files/ChooserRequest.java", [("@param ownerWindow the terminal window the chooser", "@param ownerWindow the Jasper window the chooser", 1)])
edit(M + "files/ChooserSource.java", [("@param ownerWindow the terminal window the chooser", "@param ownerWindow the Jasper window the chooser", 1)])
edit(M + "notifications/BuddyVisibility.java", [("/** Saved default, session toggle and terminal-window states", "/** Saved default, session toggle and main-window states", 1)])
edit(M + "platform/ClassicMenuRow.java", [("(the terminal window's folding bar)", "(the main window's folding bar)", 1)])
edit(M + "plugins/FileEditors.java", [
    ("for an open terminal window's id", "for an open Jasper window's id", 2),
    ("the terminal window the open came from", "the Jasper window the open came from", 2)])
edit(M + "plugins/HostedUi.java", [("    /** A toolbar view's mount: the main toolbar of one terminal window. */",
                                    "    /** A toolbar view's mount: the main window's toolbar. */", 1)])
edit(M + "plugins/PluginRuntime.java", [
    ("the application-wide directory of terminal windows, tabs and panes", "the application-wide directory of Jasper windows, tabs and panes", 3),
    ("UI thread with the active terminal window,", "UI thread with the active Jasper window,", 1),
    ("@param window the terminal window the click came from", "@param window the Jasper window the click came from", 1)])
edit(M + "shortcuthelp/ShortcutCatalog.java", [('case GLOBAL -> "Anywhere in a terminal window";', 'case GLOBAL -> "Anywhere in a Jasper window";', 1)])
edit(M + "terminals/package-info.java", [("An app-native directory of terminal windows, tabs and panes", "An app-native directory of Jasper windows, tabs and panes", 1)])
edit(M + "windows/AuxiliaryWindows.java", [
    ("chosen over terminal window {@code window}", "chosen over Jasper window {@code window}", 1),
    ("over the terminal window {@code ownerWindowOrNull}", "over the Jasper window {@code ownerWindowOrNull}", 1),
    ("over the terminal window {@code ownerWindow}", "over the Jasper window {@code ownerWindow}", 1),
    ("Reserves one progress overlay per terminal owner,", "Reserves one progress overlay per Jasper window,", 1)])
edit(M + "windows/NativeShells.java", [("inside the owner's window: a terminal window, an auxiliary window or a dialog.",
                                        "inside the owner's window: a Jasper window, an auxiliary window or a dialog.", 1)])
edit(M + "windows/ShellChrome.java", [("whether the look's terminal windows show the classic menu row;", "whether the look's main window shows the classic menu row;", 1)])
edit(M + "workspace/MenuBarItems.java", [(" * Terminal-window menu-bar items.", " * Jasper-window menu-bar items.", 1)])
edit(M + "workspace/WindowLook.java", [(" * What a terminal window is made of under the look", " * What a Jasper window is made of under the look", 1)])
edit(T + "application/JasperApplicationBackgroundTest.java", [
    ("no terminal window can be composed headlessly", "no Jasper window can be composed headlessly", 1),
    ("the request then opens a terminal window, which cannot", "the request then opens the main window, which cannot", 1)])
edit(T + "application/PresentationReachTest.java", [('.as("terminal windows and the rehearsal\'s")', '.as("Jasper windows and the rehearsal\'s")', 1)])
edit(T + "plugins/StagedRemoteHost.java", [("// A terminal window of the registry,", "// A Jasper window of the registry,", 1)])
regex(T + "shortcuthelp/ShortcutHelpTest.java", r'"Terminal window"', '"Jasper window"', 2)
regex(T + "shortcuthelp/ShortcutSectionsTest.java", r'"Terminal window"', '"Jasper window"', 8)
edit(T + "windows/AuxiliaryWindowsTest.java", [("local file over the terminal window, from its pane's folder", "local file over the Jasper window, from its pane's folder", 1)])
edit(T + "windows/VaultSheetRenderTest.java", [("// A stand-in terminal window:", "// A stand-in Jasper window:", 1)])
E = "plugins/editor/src/"
edit(E + "main/java/dev/jasper/editor/external/DefaultEditor.java", [
    ("get a new tab in {@code window} when it is a terminal window, else in the active one.", "get a new tab in {@code window} when it is a Jasper window, else in the active one.", 1),
    ("false when no terminal window is open. */", "false when no Jasper window is open. */", 1)])
edit(E + "main/java/dev/jasper/editor/ui/EditorWindow.java", [
    ("     * when that is a terminal window, else in the last main window.", "     * when that is a Jasper window, else in the main window.", 1),
    ("@return the terminal window the Editor was last opened from,", "@return the Jasper window the Editor was last opened from,", 1)])
edit(E + "test/java/dev/jasper/editor/external/DefaultEditorTest.java", [
    ('.as("no terminal window is open")', '.as("no Jasper window is open")', 1), ('.as("not a terminal window")', '.as("not a Jasper window")', 1)])
edit(E + "test/java/dev/jasper/editor/ui/EditorWindowTest.java", [('.as("not a terminal window")', '.as("not a Jasper window")', 1)])
edit("plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java", [
    ("asks over the active terminal window;", "asks over the active Jasper window;", 1)])
R = "plugins/remote/src/"
edit(R + "main/java/dev/jasper/remote/chooser/RemoteHosts.java", [
    ("@param activeWindow the terminal window used last, for sign-in prompts when the chooser has no terminal owner",
     "@param activeWindow the Jasper window used last, for sign-in prompts when the chooser has no window owner", 1),
    ('new IOException("Open a terminal window to connect")', 'new IOException("Open a Jasper window to connect")', 1)])
edit(R + "main/java/dev/jasper/remote/edit/EditChannels.java", [
    ('+ " needs an open terminal window for the sign-in prompt."', '+ " needs an open Jasper window for the sign-in prompt."', 1)])
edit(R + "main/java/dev/jasper/remote/edit/RemoteEditing.java", [
    ("@param activeWindow the terminal window used last,", "@param activeWindow the Jasper window used last,", 2),
    ("@param from the terminal window it was opened from,", "@param from the Jasper window it was opened from,", 1)])
edit(R + "test/java/dev/jasper/remote/RemoteEditingPluginTest.java", [("the SFTP panel, the terminal window and SSH pane,", "the SFTP panel, the Jasper window and SSH pane,", 1)])
edit(R + "test/java/dev/jasper/remote/edit/TestWindow.java", [("/** A terminal window whose openness a test controls. */", "/** A Jasper window whose openness a test controls. */", 1)])
edit("plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java", [("uses any terminal window. */", "uses any Jasper window. */", 1)])
edit("docs/app-maintenance.md", [
    ("In-window overlays are `AuxiliarySurface.Kind.OVERLAY`: a terminal window's progress overlay", "In-window overlays are `AuxiliarySurface.Kind.OVERLAY`: a Jasper window's progress overlay", 1),
    ("`DialogSpec.Presentation.OVERLAY`) over a terminal window, an auxiliary window or a dialog.", "`DialogSpec.Presentation.OVERLAY`) over a Jasper window, an auxiliary window or a dialog.", 1)])
edit("docs/configuration.md", [
    ("while at least one\nterminal window is open and not minimized.", "while the main\nwindow is open and not minimized.", 1),
    ("double-click him to bring the last\nactive terminal window forward.", "double-click him to bring the last\nactive Jasper window forward.", 1)])
edit("docs/editor-verification.md", [
    ("(Cmd/Ctrl+O) in a terminal window opens the chosen files", "(Cmd/Ctrl+O) in a Jasper window opens the chosen files", 1),
    ("asking for sign-in in a terminal window if needed; with every terminal window closed, the save fails with \"Reconnecting to <host> needs an open terminal window for the sign-in prompt.\"",
     "asking for sign-in in a Jasper window if needed.", 1)])
edit("docs/editor.md", [
    ("(Cmd+O on macOS, Ctrl+O elsewhere) in a terminal window's File menu or the\n  palette", "(Cmd+O on macOS, Ctrl+O elsewhere) in the File menu or the\n  palette", 1),
    ("- **Open File…** in a terminal window's File menu or the palette", "- **Open File…** in the File menu or the palette", 1),
    ("(a terminal editor with no terminal window open,", "(a terminal editor with no Jasper window open,", 1)])
edit("docs/keyboard-shortcuts.md", [
    ("applies the change to all\nterminal windows immediately.", "applies the change to all\nJasper windows immediately.", 1),
    ("The context distinguishes a terminal-window\nshortcut", "The context distinguishes a Jasper-window\nshortcut", 1),
    ("The catalog uses the same resolution as terminal windows,", "The catalog uses the same resolution as Jasper windows,", 1),
    ("from different\n  terminal windows; only one editor opens.", "from different\n  Jasper windows; only one editor opens.", 1),
    ("They belong to that window's content, not to terminal windows, so they are not in this catalog; the\nterminal-window action **Open in Editor…**",
     "They belong to the Editor's content, so they are not in this catalog; the\naction **Open in Editor…**", 1)])
edit("docs/remote.md", [(
    "appear in the terminal window the file was opened from, or else in the active one. With no terminal window open it\nfails with \"Reconnecting to prod-web needs an open terminal window for the sign-in prompt.\"",
    "appear in the Jasper window the file was opened from, or else in the active one. With no Jasper window open it\nfails with \"Reconnecting to prod-web needs an open Jasper window for the sign-in prompt.\"", 1)])
PY
git grep -n -i 'terminal[ -]window\|terminal owner' -- jasper-app/src plugins README.md docs ':!docs/superpowers' ':!docs/STATUS.md' ':!docs/design' | grep -v -i 'new terminal window' || echo "none left"
```

The last command prints only `docs/app-architecture.md`'s menu-row sentence, which Task 7 rewrites, and
`WindowContent.java`'s comment on `KeyContext.WINDOW`'s case, which plan 4b deletes with the case.
`docs/editor-verification.md`'s item 18 loses its second clause: an Editor tab is in a Jasper window, so no save runs
with every window closed.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-editor:test :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.application.*' --continue`
Expected: PASS. Then `./gradlew check --continue`: BUILD SUCCESSFUL (doclint reads the changed Javadoc).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src plugins docs
git commit -F - <<'EOF'
Windows: "Jasper window" in the application's and the plugins' words

With a main window and child windows every window is a Jasper window: the
application's comments, the shortcut help's Global row, Remote's connect
and sign-in failures, the plugins' comments and the user guides say so,
or name the main window where only it has the thing.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Documentation, STATUS and the whole build

**Files:**
- Modify: `README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/configuration.md`,
  `docs/packaging.md`, `docs/sdk-architecture.md`, `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentence true (AGENTS.md "Documentation edits"):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4a"); from edits import edit
edit("README.md", [(
    "On macOS: Cmd+T new tab, Cmd+N new window, Cmd+D split right,",
    "On macOS: Cmd+T new tab, Cmd+N new terminal window (a child window), Cmd+Shift+W close window, Cmd+D split right,", 1)])
edit("docs/app-architecture.md", [
    ("""Residency retains indexes/config/history after the last window,
""", """Windows are one main window and any number of child windows (workspace tabs spec 6), all made and closed by the
application's `WindowManager` (workspace): the main window has every piece of chrome around its workspace and keeps an
empty workspace when its last tab closes; a child window (`WindowRole.CHILD` content) shows a title bar over its
workspace and closes with its last tab. Every outside request, a plugin's tab with no window and the menu bar item
restore the main window first; closing the main window, Quit and going to the background close the child windows, then
the main window. `MainWindow`'s constructor has one call site, `WindowManager.ensureMain`, which
`verifyApplicationArchitecture` checks.
Residency retains indexes/config/history after the last window,
""", 1),
    ("Every window has a header row above its workspace of tab groups, each with its own tab strip.",
     "The main window has a header row above its workspace of tab groups, each with its own tab strip; a child window has only its title bar.", 1),
    ("macOS's screen menu bar off, so each terminal window's menus are a row in the window (an auxiliary frame shows none\nand binds Close Window's shortcut itself).",
     "macOS's screen menu bar off, so the main window's menus are a row in the window (a child window shows none, and an\nauxiliary frame shows none and binds Close Window's shortcut itself). On macOS a focused child window's screen menu bar\nshows the main window's menus, built from the same model.", 1),
    ("| `windows` | Application-built auxiliary windows and dialogs: a headless surface core and the only other native frame boundary besides TerminalWindow.",
     "| `windows` | Application-built auxiliary windows and dialogs: a headless surface core and the only other native frame boundary besides WindowFrame.", 1),
    ("| `workspace` | EDT windows, their workspaces of tab groups",
     "| `workspace` | EDT windows (`WindowManager`, `MainWindow`, `ChildWindow`, their `WindowFrame`s), their workspaces of tab groups", 1)])
edit("docs/app-maintenance.md", [
    ("valid and are silently ignored, `TerminalWindow` constructs no `WindowGlass`", "valid and are silently ignored, `WindowFrame` constructs no `WindowGlass`", 1),
    ("discarded rather than closed (`TerminalWindow.discard`, also used for a window that failed to compose)",
     "discarded rather than closed (`WindowFrame.discard`; a window that failed to open is discarded by `WindowManager`)", 1),
    ("headlessly, and a headless JVM cannot build a `TerminalWindow`, so some failure paths here are covered by reading only.",
     "headlessly, and a headless JVM cannot build a `WindowFrame`, so some failure paths here are covered by reading only.", 1)])
edit("docs/configuration.md", [
    ("with **New Window**, **Settings…** and **Quit Jasper Completely**.", "with **New Terminal Window**, **Settings…** and **Quit Jasper Completely**.", 1),
    ("""New windows inherit the saved mode; existing windows retain their own mode. As with the dock layout, the last
closed window saves its mode for the next launch.""", """The main window opens in the saved mode and saves its mode when it closes, as it does the dock layout; a child window
has no Zen.""", 1),
    ("Action IDs include `new_tab`, `new_window`, `close_tab`,", "Action IDs include `new_tab`, `new_terminal_window`, `close_window`, `close_tab`,", 1)])
edit("docs/packaging.md", [(
    "New Window, Settings… and Quit Jasper Completely work with Jasper in the background and with windows open, and New Window and Settings… come to the front.",
    "New Terminal Window, Settings… and Quit Jasper Completely work with Jasper in the background and with windows open, and New Terminal Window and Settings… come to the front.", 1)])
edit("docs/sdk-architecture.md", [
    ("Plugin windows follow the `TerminalWindow`/`WindowContent` split:", "Plugin windows follow the `WindowFrame`/`WindowContent` split:", 1),
    ("`JasperApplication` builds it from its `TerminalWindow`s (`owns(component)`, then the window",
     "`JasperApplication` builds it from its windows (`owns(component)`, then the window", 1)])
PY
git grep -nwE 'TerminalWindow|new_window|New Window' -- README.md docs/app-architecture.md docs/app-maintenance.md docs/configuration.md docs/packaging.md docs/sdk-architecture.md || echo "none left"
```

Expected: `none left`.

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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 3b's
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 4a: the main window and child windows — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-4a-windows.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). Jasper has one main window and any number of child
windows, all made, tracked and closed by the application's `workspace.WindowManager`: `MainWindow` is made only in
`ensureMain`, and only when none is open (`verifyApplicationArchitecture` checks its constructor's one call site);
`ChildWindow`s close with their last tab; closing the main window asks every window's tabs' guards and closes the child
windows first; every outside request (handoff, `jasper`, reopen, open-file, a notification's click, a plugin's tab, the
menu bar item) restores the main window first, which until plan 6 opens a new one; a plugin's tab asked for while the
main window is composed opens there once it is up, and a closing main window is never taken for an absent one.
`verifyApplicationArchitecture` and `JasperApplicationWindowsTest` pin `MainWindow`, `WindowManager` and `WindowFrame`
to their one place each. A child window is a `WindowContent` in the CHILD role: a title bar (the active tab's title; on
macOS the main window's menus in the screen menu bar, none under a look with in-window menus) over its workspace, in the
main window's centre surface and glass, with the same keymap and palette, no dock, stripes, status bar, notification
layer or Buddy; its tool window toggles open in the main window, and coming back to it shows the cards that waited. A
child window a move empties closes after the move has reported itself: `TAB_MOVED`, then `WINDOW_CLOSED` (the SDK's
Javadoc, README, fakes and contract test follow, the reverse of plan 3b's order). For plan 5: `Opening.bounds`,
`moveToNewWindow(Tab, Rectangle)` with `WindowShell.place`, `windowsByActivation()`; for plan 6: `Hooks.closing` and
`closingAll`. The main window's last tab leaves `EmptyWorkspace` (New Terminal Tab, Open File).
`WindowFrame` (formerly `TerminalWindow`) is the one `JFrame`; `WindowShell` its functions, so `WindowManagerTest` and
`JasperApplicationWindowsTest` run the lifecycle headlessly. `new_window` is gone everywhere; `new_terminal_window`
(⌘N / Ctrl+Shift+N), `close_window` (⇧⌘W / none), `move_tab_to_new_window` and `move_tab_to_main_window` (no keys; the
tab menu and the palette) are new; the menu bar item offers New Terminal Window; an auxiliary frame's File menu holds
Close Window and, off macOS, Quit. `TabRegistry`'s factory, detached hosts and `move(Tab, boolean)` are gone
(`TabRegistry.Windows` from the window manager); the SDK's `ContentTab.move(boolean)`/`isSeparate()` mean Move to New
Window / Move to Main Window / "not in the main window" until plan 4b. An operating-system notification's click raises
the window that holds its pane now (plan 3a's hand-off). The application's comments and user-facing text, Remote's
messages and the user guides say "Jasper window" (taking over plan 3b's leftovers, its Rulings 11). Rulings (plan,
"Rulings"): <one clause each, 1 to 29>.
Deviations: (1) until plan 6, restoring opens a fresh main window and no child window (spec 6.4's last row; spec 18
puts restoring in plan 6); <any other, each with its reason>. Left for plan 4b: plugin windows as tabs (`Windows.create`, `PluginWindow`,
`WindowSpec`, `ShortcutContext.Kind.WINDOW`, `KeyContext.WINDOW`; the Vault manager and the sample About), the Errors
window as a tab, `TabPlacement`, the `ContentTab` move API, panels in the main window only in the SDK, `WindowHandle.isMain()`,
and `WindowContent`'s comment on `KeyContext.WINDOW`'s case.
`./gradlew check --continue`: **<counts>**. GUI checks for the user: Cmd+N opens a child window in the current
terminal's folder with a title bar and no toolbar, status bar or tool windows; on macOS a focused child window shows the
same menus as the main window in the menu bar (none at all under the Enterprise look, whose menus sit in the window,
where its shortcuts still work: spec 6.3 as amended); a child window in Compact view mode; Shift+Cmd+W and the title
bar close a child window (an unsaved editor asks) and the main window with its child windows; the main window's last
tab leaves New Terminal Tab and Open File; Move to New Window and Move to Main Window from a tab's menu keep a running
command running; a tool window shortcut in a child window opens the tool window in the main window; Cmd+Q while
resident closes every window and keeps Jasper in the background; the menu bar item's New Terminal Window with no window
open restores the main window first; a translucent workspace in a child window looks as the main window's; a
notification card posted while a child window is in front shows in the main window when a window comes forward. Not
merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for the main and child windows

The documents describe the main window, child windows and their window
manager, New Terminal Window and Close Window; STATUS records plan 4a
with its rulings, the hand-off to plan 4b and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 5.4's retarget (Task 3: `open`, `openFirstWindow`, the open-file event and `jasper` go through `open`,
  which restores and targets the main window); 6.1 (Task 2, Rulings 2-3; Task 3 delegates every window question); 6.2
  (Tasks 1, 3: the main window keeps the chrome, the corner layer, the Buddy); 6.3 (Task 1: the child window's content;
  Task 3: its title bar, macOS menus, Windows/Linux title pane, root bindings, palette, glass); 6.4's table row by row
  (Task 2: a child window closes when its last tab closes or moves out, a user-closed child window asks its tabs'
  guards; Task 1: the main window's empty workspace; Tasks 2, 3: closing the main window or Quit, children first, then
  residency; Task 3: Cmd+Q while resident is `toBackground`; Tasks 2-4: every request restores the main window first);
  6.5 (Task 5; New Editor Window and `TabPlacement.NEW_WINDOW` are plan 4b's); 9.3's "go to" after a move (Task 3);
  10's empty state (Task 1); 11's `new_terminal_window`, `close_window`, `move_tab_to_*` rows and the tab menu (Task 5);
  15's "the main window closes with child windows open" (Task 2); 16's New Window paths, `WindowCallbacks.newWindow`,
  detached windows and the registry's factory (Tasks 3-5); 17's tests: one constructor call site (Task 2), closing the
  main window closes the child windows after their guards (Task 2, 3), a child window closes when empty (Task 2), the
  main workspace can be empty (Tasks 1, 2), entry points restore the main window first (Tasks 2, 3), New Terminal Window
  opens a child window in the current directory (Tasks 2, 5), a terminal tab moved to a new window and back keeps its
  session (Task 2); 14's wording row for the application, Remote's messages and the guides (Task 6). Out of scope: plan
  4b's (6.6's Errors tab, 14's SDK rows, the plugins), plan 5's tear-out, plan 6's saving and restoring.
- Review findings (2026-10-18) folded in: the frames seam before the manager (JLS 8.3.3), Rulings 3's structural pins,
  4's deviation, 7's amended spec, 23-29.
- Every quoted anchor was matched, at its stated count, in a replay of plans 1, 2, 3a and 3b and of Tasks 1-7 in order;
  nothing was compiled.
- Type consistency: `WindowRole`, `JasperWindow.Events`, `WindowShell` (with `place`), `WindowManager.Opening` (with
  `bounds`)/`Hooks` (with `closing`, `closingAll`)/`State`, `ensureMain(String, Path)`, `openTerminalWindow(Path)`,
  `moveToNewWindow(Tab)` and `moveToNewWindow(Tab, Rectangle)`, `windowsByActivation()`, `TabRegistry.Windows(main,
  isMain, newWindow, movedOut)` (and its three-function form for tests) and `ActionId.NEW_TERMINAL_WINDOW` keep one name
  and one signature from the task that creates them to the last task that uses them.
