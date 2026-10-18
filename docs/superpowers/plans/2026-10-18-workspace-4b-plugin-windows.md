# Workspace tabs 4b: plugin windows become tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The SDK becomes 0.20.0, its window rows of spec 14: `WindowHandle.isMain()`, a `TabPlacement` for
`Windows.openTab` (the active group, a new group to the right or below, a new child window), and
`ContentTab.moveToNewWindow()`, `moveToMainWindow()` and `window()`; `Windows.create`, `PluginWindow`, `WindowSpec` and
the window shortcut context go, so the Vault manager and the sample's About become content tabs, which carry no window
chrome (no Done: the tab strip closes them); panels, the toolbar and the status bar are the main window's, in the fake
too, and Remote and File Servers keep one panel's state; the Errors window becomes an app-owned content tab in the main
window, without its Done; the Editor gains New Editor Window. The testkit's fakes and the app's contract
fixture keep a window's tabs in groups and keep the main window open when its last tab leaves, so the contract suite
holds on both runtimes.

**Architecture:** The SDK adds `dev.jasper.sdk.ui.TabPlacement`; the three-argument `Windows.openTab` becomes a default
method that passes `ACTIVE_GROUP` to the new four-argument one. In the application `TabRegistry.open` takes the app's
twin, `TabRegistry.Placement`: a split placement attaches through the new `WorkspaceHost.attachBeside(tab, group, side)`
(a new group beside the named group, the active one when the group is null), and `NEW_WINDOW` attaches the tab to a
child window the window manager opens empty for it (`TabRegistry.Windows.emptyWindow`), so the owner's window stays as it
was. `WindowEntry` gains `main`, so `HostedTerminals` answers `isMain()`. `HostedUi` loses `OwnedWindow`, `create` and
the window context; `KitCompatibility` refuses what 0.20 removed; the auxiliary windows' title-action, content-size and
close-command chain, which only plugin windows used, goes with them. The testkit's `FakeWorkspace` gains groups (most
recently used first) and the main window's rule, `FakeContent` the new moves, and the app's `TerminalFixture` the same
model; the testkit's and the contract harness's panel methods lose their window id. `ErrorsTab` replaces
`ErrorsWindow`, filed in the tab registry under `TabRegistry.APP_OWNER` (`jasper`); its view, like the Vault manager's,
paints from the workspace route as content does and has no Done.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 6.5's New Editor Window and placement, 6.6, 14, 15's window rows, 16's SDK and `KeyContext.WINDOW` rows, 17's
SDK line and 18). The second half of section 18's plan 4; [4a](2026-10-18-workspace-4a-windows.md) (the main window and
child windows) executes first, after plans [1](2026-10-17-workspace-1-single-instance.md),
[2](2026-10-17-workspace-2-layout-model.md), [3a](2026-10-17-workspace-3a-tab-registry.md) and
[3b](2026-10-18-workspace-3b-sdk-tabs.md).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`,
after plan 4a's last commit (the branch is `main` at `11e40192c` plus this series). Run every command from the worktree
root. Every anchor below was taken from, and every edit script of Tasks 1-8 replayed on, a copy of `22b702456` with the
edit scripts and created files of plans 1, 2, 3a, 3b (as committed in `17830fac6`) and 4a applied in order; nothing was
compiled or run. Every edit quotes the text it replaces, and the scripts stop with a message when that text is not found
the stated number of times: re-find the quoted text (a plan before this one may have reworded it), adjust the anchor,
re-run, and record it in this plan's banner; never edit around a missing anchor. Later tasks anchor on text earlier
tasks of this plan wrote, and quote it as those tasks leave it.

## Spec issues

None that block. One reading: spec 14's "the factory runs once" holds per main window. Closing the main window closes
its chrome (spec 6.4), panels included, and the next main window builds them again; `PanelFactory`'s Javadoc says so
(Rulings 14). Section 14's `ContentSpec` restore state and `Windows.restorer`, and the Editor's restorer, are plan 6's.

One departure, recorded as a deviation in STATUS (Rulings 20): Enterprise's 07 Credential Vault mocks draw the manager as
a window, with Done in a dialog band under its status row. As a content tab it has neither, so `ClassicVaultRenderTest`
pins the manager's status row to the render (Task 3 Step 6), and the user checks the tab in the GUI.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 14, 16; Jasper is an MVP): `Windows.create`, `PluginWindow`, `WindowSpec` (with `SizeBasis`),
  `ShortcutContext.window` and `Kind.WINDOW`, `ContentTab.move(boolean)` and `isSeparate()`, `KeyContext.WINDOW`,
  `HostedUi.OwnedWindow`, `AuxiliaryWindows.mayQuit`, the testkit's plugin-window paths and `closeCommand`, the Vault's
  `VaultManagerWindow`, `ManagerPanel.titleActions`, `ErrorsWindow`, the auxiliary windows' title-action, content-size
  and close-command chain (Rulings 13), the window id of the testkit's and the contract harness's panel methods, and the
  Done of the Vault manager and the Errors view go outright, with the tests whose subject they were. A test of behaviour
  that stays is rewritten, not dropped (Rulings 11). No default that keeps an old call working, no "was removed" text in
  a guide.
- The SDK is 0.20.0 from Task 1 on. `KitCompatibility` refuses what 0.20 removed (Task 4), so a plugin compiled against
  0.19 that claims 0.20 fails at load, not at the call.
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`, `verifySdkArchitecture` and
  `verifyPluginArchitecture`): nothing gains an edge. `contributions` already reaches `layout` (`GroupId`, `TabId`);
  `application` already reaches `contributions`; `plugins` already reaches `contributions`.
- No motion (spec 4): a placed tab, a moved tab and a new child window appear at once.
- The theme-migration ledger (`docs/design/theme-engine/migration.csv`, checked by `ThemeMigrationCoverageTest`): the
  renamed `VaultManagerTab`, `VaultManagerTabTest`, `ErrorsTab` and `ErrorsTabTest` keep their rows under the new names
  (`ledger.rename`, Tasks 3 and 6). `TabPlacement` lives in the SDK, which the ledger does not cover.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment, and an SDK addition carries `@since 0.20.0`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate and describe what the system does (AGENTS.md
  "Documentation edits"). `AppDocumentationTest` checks every guide's links, `docs/STATUS.md` and the plans included, and
  that a guide's `<!-- example:… -->` snippet equals its source between the `// example:…:start` and `:end` markers.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan4b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan4b
cp build/plan3a/edits.py build/plan3a/ledger.py build/plan4b/ 2>/dev/null || echo "plan 3a's helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan4b in place of build/plan3a"
ls build/plan4b/edits.py build/plan4b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` (replaces from the one `start`
through the first `end` after it, both included) and `regex(path, pattern, replacement, count)`; `ledger.py` gives
`add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan4b")`. Java text inside a script is in triple-quoted strings, so `\n` and
`…` in Java source stay as written.

## Review Focus

1. `TabPlacement.NEW_WINDOW`: the tab opens straight in a child window opened empty for it, and the owner's window is
   not touched (no new group, no selection, no focus change); the topics report `WINDOW_OPENED`, then `TAB_OPENED` in
   the new window; a window that fails to show the tab closes again; a key that is already open is selected where it
   is. Task 2: `PluginContractTest.aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow` (on the fake and
   on the application), `TabRegistryTest.aPlacementAttachesBesideTheActiveGroupOrInANewWindowAndLeavesTheOwnerAlone`,
   `WindowManagerTest.aPluginsTabPlacedBesideOpensAGroupAndOnePlacedInANewWindowAChildWindow`.
2. The fakes and the application agree on the main window: it outlives its last tab, a child window closes with its
   last tab, `moveToNewWindow()` on a child window's only tab and `moveToMainWindow()` in the main window do nothing.
   Task 2: `PluginContractTest.theMainWindowOutlivesItsLastTabWhileAChildWindowClosesWithIt`,
   `aContentTabMovesToANewChildWindowAndBackToTheMainWindow` (`FakeContractTest` and `AppContractTest`).
3. The Vault manager as a tab: one tab however often it is asked for, its editors and sheets attached to the window that
   holds the tab, and locking with an editor open clears it. Task 3:
   `VaultManagerTabTest.singletonSelectionDetailsAndCloseAreHeadless`,
   `lockClearsDetailsAndEditorsWithoutCreatingAnUnlockWaiter`,
   `VaultNativeCompositionTest.theActionRowOpensEditorsAttachedToTheWindowAndLocks`.
4. A plugin compiled against 0.19 that claims 0.20 and still opens a plugin window, moves a tab with `move(boolean)` or
   names the window shortcut context is refused when it loads. Task 1:
   `KitCompatibilityTest.aZeroNineteenRangeIsSkippedBeforeItLoads`; Task 4:
   `KitCompatibilityTest.thePluginWindowsSdkZeroTwentyRemovedAreRefused`.
5. Requests from a child window for the main window's things: a panel's toggle shows the one panel in the main window,
   and Show more… or Notifications: Show Errors opens the one Errors tab in the main window, selecting the incident.
   Task 5: `FakePanelsAndWindowsTest.aChildWindowsToggleShowsThePanelInTheMainWindow`,
   `PluginContractTest.aKitPanelsHeaderIsReplacedContainedAndIgnoredAfterClose` (one instance, in the main window, on
   both runtimes), `ClassicSessionsWiringTest.anotherWindowsChoiceLeavesTheMainWindowsTreeAlone`; Task 6:
   `ErrorsTabTest.showErrorsOpensOneErrorsTabInTheMainWindow`, `showMoreSelectsItsIncidentInTheSameTab`.
6. A content tab carries no window chrome (Rulings 20): the Vault manager and the Errors tab have no Done and Escape
   closes neither; the Errors view refuses a context off the workspace route; the classic manager's render is what the
   host mounts (no dialog band forced on it), and what that moves is re-pinned to the render and listed old → new: the
   status row and the bands that reach the footer, never the sheets', Unlock's, the toolbar verbs' or the Fields grid's
   assertions (Task 3 Step 6). Task 3:
   `ManagerPanelTest.theManagerHasNoDoneAndEscapeLeavesItOpen`, `VaultEnterBaselineTest`,
   `ClassicVaultRenderTest`; Task 6: `ErrorsViewTest.theTabIsAManagerListAndDetailWithTheFooterBand`,
   `aChromeContextIsRefused`, `ErrorsTabTest.showErrorsOpensOneErrorsTabInTheMainWindow`.
7. The chain that goes in Task 4 (Rulings 13) had no caller outside itself and its tests: Task 4 Step 5's first grep
   shows it before anything is deleted, and Close Window still asks the guards (`requestClose()`).
8. Coverage of behaviour that stays survives the removals (Rulings 11): `VaultSheetRenderTest
   .attachedFormsRenderAcrossThemesFontsAndShortOwners` (Add Login and Add SSH Key over a stand-in Jasper window),
   `JasperApplicationBackgroundTest.aContentTabThatRefusesToCloseKeepsEverythingOpenUntilItAgrees`,
   `JasperApplicationPluginsTest.quitNowDoesNotAskTheQuitGuards`.

## Verified facts

From a replay of plans 1, 2, 3a, 3b and 4a on `22b702456` (see Workspace).

1. `JasperSdk.VERSION` is `"0.19.0"`; the nine bundled `plugin.toml` files, the example external plugin and the guides
   (`docs/plugin-authoring.md` 14 times, `jasper-sdk/README.md` 8, `docs/sdk-icons.md` 1) declare
   `sdk = ">=0.19.0, <0.20"`; `VersionRangeTest` reads the guides' ranges.
2. `Windows.openTab(ContentSpec, WindowOwner, UiView)` is abstract (`ui/Windows.java:19`); `Windows.create(WindowSpec)`
   returns `PluginWindow` (`:61`); `ContentTab.move(boolean)` and `isSeparate()` (`ui/ContentTab.java:25-27`) mean, after
   plan 4a, Move Tab to New Window, Move Tab to Main Window and "not in the main window" (4a Rulings 13;
   `plugins/HostedUi.java:567-571`). `WindowHandle` has no `isMain()`.
3. Only two plugins call `Windows.create`: the Vault's `VaultManagerWindow` (`ui/VaultManagerWindow.java:69`, with
   `setTitleActions` for the ROWS layout at `:77`) and the sample's About (`SamplePlugin.java:127`); the Editor's
   `DialogsTest` uses one as a dialog owner (`:37`).
4. `ShortcutContext.window(id)` and `Kind.WINDOW` (`ui/ShortcutContext.java:84`) map to `KeyContext.window`
   (`HostedUi.java:221`), which `WindowContent.applies` answers false (`workspace/WindowContent.java:1406-1407`, with the
   comment plan 4a left for this plan) and the shortcut help names "<id> window" (`ShortcutCatalog.java:53`).
5. Quit and restart call `pluginWindowsMayClose()` (`application/JasperApplication.java:1923`), which asks the content
   tabs' guards, `AuxiliaryWindows.mayQuit()` (`windows/AuxiliaryWindows.java:179`, the plugin windows' guards) and the
   quit guards; `pluginWindowsOpen` (`:755`, `:1726`) keeps a non-resident process alive while an auxiliary window is
   open.
6. `TabRegistry` (4a) has `Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID>
   movedOut)` with a three-argument constructor for tests, `open(plugin, key, title, owner, component)` (a plugin's tab
   asked for while the main window is composed opens there once it is up), `moveToNewWindow(Tab)` and
   `moveToMainWindow(Tab)`; `Tab.inMainWindow()`. A move reports `TAB_MOVED` before the child window it emptied closes
   (`movedOut`). `WorkspaceHost(id, attach, detach, select, toFront, groupOf)`; `WindowContent.attach(tab, opened,
   beside, side)` opens a tab beside any group, and `WorkspaceView.openBeside` opens it in the empty group of an empty
   workspace, which `Layout.split` cannot split. `WindowManager.openChild(empty, directory, bounds)` opens a child
   window, empty or with a terminal tab.
7. Panels: plan 4a docks panels in the main window only and routes a child window's panel requests there
   (`Contributions.panelRoute`). Remote and File Servers still keep one panel per window: `TunnelsUi` (`panels`,
   `panelHosts` maps), `TransfersPanels` (`hosts`), `SftpUi`, `FileServerPlugin` (`panels`, `panelHosts`) and
   `RemotePlugin` (`panels`, `panelHosts`).
8. `ErrorsWindow` (`application/ErrorsWindow.java`) is an auxiliary window over `ErrorsView`, opened by
   `JasperApplication.showIncident` (`:1475`) and the action `notifications.errors`; `IncidentNotices` leaves failures in
   it to the log.
9. The testkit's `FakeWorkspace` keeps a window's tabs in insertion order with a `detached` flag, and a window closes
   with its last tab; `FakeUi.openComponent` falls back to the first window that is not detached (`FakeUi.java:554`);
   `FakeContent.move(boolean)` (`:42`). The app's `TerminalFixture` follows the same rules. `FakeUi` keeps a panel's
   instances per window id, and `FakePluginHost.openPanel`, `panelView`, `panelHeader` and `closePanel` (and
   `ContractHarness`'s panel methods) take a window id.
10. `KitCompatibility` keeps removed types and members by SDK version in `TYPES` and `MEMBERS` (`plugins/KitCompatibility
    .java:19-24`, the 0.19 rows at `:69`).
11. The Editor registers `dev.jasper.editor.new` (New Editor File) and puts it in the File menu
    (`EditorPlugin.java:46`); `EditorWindow.newUntitled(WindowOwner)` (`ui/EditorWindow.java:310`) opens every document
    with the three-argument `openTab`.
12. Only plugin windows used the auxiliary windows' title slot, content sizing and close-command handlers:
    `AuxiliarySurface.setTitleActions`, `titleActions()`, `contentSize(boolean)`, `onCloseCommand`, `closeCommand()`;
    `NativeShells` mounts the title slot (`:531-545`), sizes a window by its content (`:341`) and sends Close Window to
    `closeCommand()` (`:705`, `:727`); `KitView.titleStrip` styles a title slot's menus (`Menus.java:238`); the testkit's
    `SurfaceMount` carries a `title` flag. Task 4 Step 5's first grep lists every reference.
13. Versions: Remote is 0.12.0 and File Server 0.4.0; the Indexer optionally requires `dev.jasper.remote >=0.12` and the
    Editor `>=0.7`, Remote the Vault `>=0.2` and the Editor `>=0.2`, File Server the Vault `>=0.2`; Enterprise is
    `>=0.1` wherever it is required.
14. Remote's `SessionSelection.onChanged` moves the Sessions panel's row for every window's choice
    (`RemotePlugin.java:250`, per window); only the main window's toolbar sets one in the application, while the fake
    builds a toolbar view for any window a test names.
15. The Vault manager's `ManagerPanel` and `ErrorsView` are WINDOW forms with a primary Done that Escape presses; the
    kit draws no title in a WINDOW form. `HostedUi.openTab` mounts a plugin's content tab on the workspace route over a
    painted surface (`HostedUi.java:665`); `ErrorsView` is built on the chrome context. `ClassicVaultRenderTest` gives the
    manager the dialog band its frame gave it and pins the 07 mocks' Done in its status row (`STATUS`, device column
    1600).
16. THD Sourcing Crypto (read only, `~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at
    `3740e4e`): its main code opens one modal dialog owned by the action's window (`CryptoPlugin.java:42`); its tests
    read `FakePluginHost.windows()` and `windowView`, which list dialogs, and add one window with
    `addTerminalWindow`; nothing in it uses what 0.20 changes.

## Rulings

Record them in `docs/STATUS.md` (Task 8). "What — why".

1. **The SDK is 0.20.0; every bundled manifest, the example external plugin and every range in the guides declare
   `sdk = ">=0.20.0, <0.21"`; the plugins whose behaviour changes bump their versions: the Vault (0.6.0), the sample
   (0.3.0), the Editor (0.10.0), Remote (0.13.0) and File Server (0.5.0), whose panels become one, in the main window;
   the others keep theirs, and every cross-plugin range still holds (the Indexer's optional `remote >=0.12`, fact 13)**
   — spec 14 ("each bumps the SDK version"); plan 3b's rule that a guide's older range no longer loads.
2. **`WindowHandle.isMain()` is abstract: the application, the testkit and every test stand-in answer it** — a clean
   break; a default could only lie.
3. **`TabPlacement` is `ACTIVE_GROUP`, `SPLIT_RIGHT`, `SPLIT_DOWN`, `NEW_WINDOW`; the four-argument `Windows.openTab` is
   the abstract one and the three-argument form a default that passes `ACTIVE_GROUP`** — spec 14's row ("ACTIVE_GROUP
   (default)").
4. **A split placement opens the tab in a new group on that side of the owner window's active group; the window's
   `WorkspaceHost.attachBeside(tab, group, side)` attaches beside any group, the active one when `group` is null
   (`WindowContent.attach(tab, false, group, side)`), which plan 5's drop beside a group uses; in an empty workspace the
   tab opens in the empty group** — `Layout.split` cannot split the empty group (fact 6); one entry for both plans.
5. **`NEW_WINDOW` attaches the tab straight to a child window the window manager opens empty for it
   (`TabRegistry.Windows.emptyWindow`, `WindowManager.emptyWindow()`): the owner's window is not touched (no new group,
   no selection, no unmaximize, no focus change), the topics report `WINDOW_OPENED`, then `TAB_OPENED` in the new window,
   and a window that fails to show the tab closes again (`movedOut`); a key that is already open is selected where it
   is, whatever the placement** — the owner stays as the user left it; opening an open key already selected it (SDK
   0.8.5).
6. **`moveToNewWindow()` leaves a child window's only tab, a closed tab, one with a modal prompt open and one being
   closed where they are; `moveToMainWindow()` leaves a tab the main window holds, restoring the main window first when
   none is open; `window()` is the window that holds the tab, else the last one that held it** — spec 14's row and 4a
   Rulings 10 (a child window's only tab already is its own window).
7. **The fakes model the application's windows: the window opened while no main window is open is the main window,
   which outlives its last tab; any other window is a child window, which closes with its last tab, after the move that
   emptied it reports `TAB_MOVED` (4a's order); a window's tabs sit in groups, each remembering the tabs it showed most
   recently, a split or a placement beside opens a group after the active one, and closing a group's selected tab
   selects the tab it showed before; `NEW_WINDOW` adds a child window for the tab. `FakePluginHost.addTerminalWindow`
   and `ContractHarness.addTerminalWindow` keep their names. The app's `TerminalFixture` has the same groups and gains
   `addMainWindow()`; its `addWindow()` adds a child window, so the application's own tests keep a window that closes
   with its last tab, and `AppContractTest`'s first window is a main window** — the contract suite runs on both runtimes
   and must hold on both (spec 17); plan 3b's hand-off.
8. **The Vault manager is a content tab (`VaultManagerTab`, key `manager`, titled Credential Vault) in the window the
   request came from; the ROWS layout's title actions become the first row of its body (a DOCUMENT split), since a tab
   has no title bar, and `ManagerPanel.titleActions` goes; it has no Done, and Escape closes nothing (Rulings 20)** —
   spec 14 ("Vault (manager tab)").
9. **The sample's About is a content tab whose Details sheet the tab owns; its method is `installPanelAndAboutTab`** —
   spec 14 ("sample (About tab)"); the guide shows the method.
10. **The testkit finds a plugin's content tab and its kit view by title (`FakePluginHost.contentTab`, `contentView`,
    `@since 0.20.0`), as `windowView` finds a dialog's** — the Vault's and the sample's tests read the tabs that replaced
    their windows.
11. **Tests whose subject was a plugin window go (`windowsAreSingletons…`, the close-command tests, the anchored title
    and body tests, `singletonReplacement…`, the plugin-window quit tests); the rest take a content tab, a Jasper window
    or an anchor as their owner; a test of behaviour that stays is rewritten for its new owner, not dropped:
    `VaultSheetRenderTest`'s Add Login and Add SSH Key sheets over a short owner (a stand-in Jasper window),
    `JasperApplicationBackgroundTest`'s refusing guard (a content tab's guard, which then agrees) and
    `JasperApplicationPluginsTest`'s `quitNow` (the quit-guard fixture)** — spec 16; the subject is gone, the behaviour
    is not.
12. **Quit and restart ask the content tabs' guards and the quit guards (`guardsAllowQuit`, formerly
    `pluginWindowsMayClose`); going to the background asks the content tabs' guards; an open auxiliary window (Settings,
    About, Licenses) keeps a non-resident process alive (`auxiliaryOpen`)** — no plugin window is left to ask (fact 5).
13. **The auxiliary windows' title-action, content-size and close-command chain goes with plugin windows:
    `AuxiliarySurface.setTitleActions`, `titleActions()`, `onCloseCommand`, `closeCommand()` and `contentSize(boolean)`,
    `NativeShells`' title-slot mounting (the toolbar row, the title bar's accessory) and the content branch of
    `initialSize`, `MacTitleBar`'s accessory, `KitView.titleStrip` and its branch in `Menus`, and `SurfaceMount`'s `title`
    in the application and the testkit; Close Window and Cmd/Ctrl+W call `requestClose()`, which asks the guards as the
    close button does** — nothing else reached them (fact 12; Task 4 Step 5's first grep shows it), and Jasper is an
    MVP: no dormant machinery.
14. **Panels live in the main window: one instance, built when the panel first shows there and again only for a new
    main window; `PanelHost.window()` is the main window; a toggle from a child window shows the panel in the main
    window, in the fake as in the application (plan 4a's `panelRoute`). The fake keeps one instance per panel, and
    `FakePluginHost.openPanel`, `panelView`, `panelHeader` and `closePanel` and `ContractHarness`'s panel methods take
    no window: they act on the main window's panel (`openPanel` returns null while no main window is open). Remote's
    Tunnels, Transfers, SFTP and Sessions panels and File Servers keep one panel's state; the Sessions panel follows only
    its own window's row** — spec 14's Panels row and Remote row ("per-window panels become one"); a window id the
    fake would ignore or refuse is a call that cannot be right.
15. **The SDK's Javadoc says the toolbar and the status bar are the main window's; the contract suite keeps building a
    toolbar view for whichever window the harness names** — plan 4a Rulings 6 made them the main window's; in the
    application only the main window asks, which the harness stands for.
16. **The Errors window becomes `ErrorsTab`, an app-owned content tab (owner `TabRegistry.APP_OWNER`, key `errors`) in
    the main window's active group, opened by Show more…, Notifications: Show Errors and a system notification's click;
    a second request selects it and its incident; a view that fails to build opens no tab. Its view paints from the
    workspace route over its own surface, refuses a context on another route, and has no Done (Rulings 20)** — spec 6.6.
17. **New Editor Window (`dev.jasper.editor.new_window`, no default key, in the File menu after New Editor File and in
    the palette) opens an untitled document with `TabPlacement.NEW_WINDOW`; the Editor's other opens keep the active
    group** — spec 6.5.
18. **The guides describe 0.20 under a new "Main and child windows (SDK 0.20.0)" section of `jasper-sdk/README.md`,
    stating what is and not what went; two older STATUS entries' links follow the renamed headings; Task 8's check reads
    every guide and comment with its lines joined, so a wrapped phrase is found** — AGENTS.md "Documentation edits";
    `AppDocumentationTest` checks STATUS's links.
19. **THD Sourcing Crypto stays skipped: plan 6 bumps the SDK again, and its one rebuild, after the series' last bump,
    carries 0.19's changes and 0.20's, which need only its range** — plan 3b's hand-off; fact 16.
20. **A content tab carries no window chrome: the tab strip holds its title and closes it. The Vault manager, the
    sample's About and the Errors tab have no Done, and Escape closes none of them; the manager and the Errors view keep
    the WINDOW form's full-bleed body and pinned footer band (the kit draws no title in it; the title names the view for
    assistive technology). `HostedUi` mounts a plugin's content tab on the workspace route over a painted surface, and
    the render and evidence tests render exactly that, with no dialog band forced on it; what that moves in
    `ClassicVaultRenderTest` is re-pinned to the render at execution and listed old → new. The manager's departure from
    Enterprise's 07 window mock (its Done over a dialog band) is a STATUS deviation and a GUI check** — the user's
    "plugin windows become tabs"; a window's Done in a tab would close a tab the strip already closes.
21. **Jasper's own content tabs share one owner, `TabRegistry.APP_OWNER = "jasper"` (a plugin's id is never `jasper`),
    and plan 6 never saves or restores them: the Errors tab is not part of a saved workspace** — the Errors tab lists
    this session's incidents, which a restart clears; one owner lets plan 6 skip them all.

## File structure

- SDK (`jasper-sdk`): `JasperSdk` (Task 1); `ui/TabPlacement` (new), `ui/Windows`, `ui/ContentTab`,
  `terminal/WindowHandle`, `terminal/TerminalEvents`, `ui/OverlaySpec` (Task 2); `ui/Windows`, `ui/DialogSpec`,
  `ui/Rail`, `ui/WindowSurface`, `WindowOwner`, `ui/ShortcutContext`, `files/FileChoice`, `ui/PluginWindow` and
  `ui/WindowSpec` deleted (Task 4); `ui/Panels`, `PanelFactory`, `PanelHost`, `Toolbar`, `ToolbarItem`,
  `ToolbarViewFactory`, `StatusBar`, `StatusItem`, `StatusProgress` (Task 5); `ui/PanelHeader` (Task 8).
- Kit (`jasper-ui`): `swing/KitView`, `Menus`, `Buttons` and `KitViewToolbarStripTest` (Task 4).
- Testkit (`jasper-sdk-testkit`): `FakeWorkspace`, `FakeContent` (whole files), `FakeUi`, `FakeTerminals`,
  `FakePluginHost`, `ContractHarness`, `PluginContractTest` (Task 2); `FakeContent`, `FakeUi`, `FakePluginHost` (Task 3);
  `FakeUi`, `FakePluginHost`, `FakePluginContext`, `ContractHarness`, `PluginContractTest`, `AnchorContractTest` and the
  fake's tests (Task 4); `FakeUi`, `FakePluginHost`, `ContractHarness`, `PluginContractTest`, `FakeContractTest`,
  `FakePanelsAndWindowsTest`, `FakeKitMountingTest` (Task 5).
- Application: `terminals.WindowEntry`, `workspace.WindowTerminals`, `WindowContent`, `plugins.HostedTerminals`,
  `HostedUi`, `contributions.WorkspaceHost`, `TabRegistry` (Task 2); `commands.KeyContext`, `workspace.WindowContent`,
  `shortcuthelp.ShortcutCatalog`, `plugins.HostedUi`, `KitCompatibility`, `PluginRuntime`, `windows.AuxiliaryWindows`,
  `contributions.QuitGuards`, `application.JasperApplication`, `windows.AuxiliarySurface`, `NativeShells`,
  `platform.MacTitleBar`, `plugins.SurfaceMount` (Task 4); `commands.KeyContext` (Task 5); `application.ErrorsTab`
  (renamed from `ErrorsWindow`), `IncidentNotices`, `JasperApplication`, `contributions.TabRegistry`,
  `notificationui.ErrorsView` (Task 6); `application.JasperApplication` (Task 8).
- Plugins: the Vault's `VaultManagerTab` (renamed from `VaultManagerWindow`), `ManagerPanel`, `VaultPlugin`; the sample
  (Task 3); Remote's `TunnelsUi`, `TransfersPanels`, `SftpUi`, `RemotePlugin`, File Servers' `FileServerPlugin`, both
  `plugin.toml` (Task 5); the Editor's `EditorPlugin`, `EditorWindow` (Task 7), `api/Editor`, `EditorWindow` (Task 8);
  every `plugin.toml` (Task 1).
- Tests: `TerminalFixture` (whole file), `AppContractTest`, `TabRegistryTest`, `WindowManagerTest`,
  `TerminalRegistryTest`, the stand-ins of `WindowHandle` (Task 2); `VaultNativeCompositionTest` (whole file), the
  staged Vault host and its render tests, `VaultManagerTabTest` (renamed) (Task 3); the app's plugin-window tests (Task
  4); Remote's and File Servers' test accessors, every caller of the testkit's panel methods, `ClassicSessionsWiringTest`
  (Task 5); `ErrorsTabTest` (renamed, whole file), `ErrorsViewTest`, the notification views' evidence tests,
  `KitTestSupport` (Task 6); `EditorPluginTest` (Task 7).
- Documents (Task 8): `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/plugin-ui.md`,
  `docs/sdk-architecture.md`, `docs/credential-vault.md`, `docs/editor.md`, `docs/editor-verification.md`,
  `docs/keyboard-shortcuts.md`, `docs/README.md`, `docs/configuration.md`, `docs/app-maintenance.md`,
  `docs/app-architecture.md`, `docs/notifications.md`, `docs/packaging.md`, `docs/remote.md`, `docs/STATUS.md`; the
  ledger in Tasks 3 and 6.

## Before you start

```bash
git branch --show-current                                                                                  # claude/workspace-tabs
git status --short                                                                                         # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for the main and child windows"     # 4a's last commit
grep -c 'VERSION = "0.19.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                                         # 1
grep -c '    PluginWindow create(WindowSpec spec);' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java                     # 1
grep -c 'public record Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut) {' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
grep -c 'public void moveToNewWindow(Tab tab)' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java        # 1
grep -c 'public boolean inMainWindow()' jasper-app/src/main/java/dev/jasper/app/contributions/Tab.java                        # 1
grep -c 'public Optional<ChildWindow> moveToNewWindow(Tab tab)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'case WINDOW -> false;' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java                          # 1
grep -c 'final class ErrorsWindow' jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java                      # 1
grep -c 'public Subscription onCloseCommand(BooleanSupplier handler)' jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java  # 1
grep -c 'version = "0.12.0"' plugins/remote/src/main/resources/plugin.toml                                                    # 1
grep -c 'version = "0.4.0"' plugins/fileserver/src/main/resources/plugin.toml                                                 # 1
```

Every command prints the stated result. Plans 1, 2, 3a, 3b and 4a each added a `docs/STATUS.md` entry; this plan's goes
above them. Create the two helper scripts (Global Constraints).

---

### Task 1: SDK 0.20.0

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, every `plugins/*/src/main/resources/plugin.toml`,
  `docs/examples/external-plugin/src/main/resources/plugin.toml` and `build.gradle.kts`, `docs/plugin-authoring.md`,
  `jasper-sdk/README.md`, `docs/sdk-icons.md`
- Test: `CapabilitiesTest`, `FilesValuesTest`, `PaletteValuesTest`, `KitBoundaryTest` (SDK); `VersionRangeTest`,
  `KitCompatibilityTest`, `testsupport/LookFixtures.java` (application)

**Interfaces:**
- Consumes: nothing new.
- Produces: `JasperSdk.VERSION = "0.20.0"`; every manifest and guide range `>=0.20.0, <0.21`.

- [ ] **Step 1: Write the failing tests**

The SDK reports 0.20.0, the bundled range is 0.20's, and a 0.19 manifest is skipped by its range before a class is
read:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
S = "jasper-sdk/src/test/java/dev/jasper/sdk/"
for test in ["CapabilitiesTest.java", "files/FilesValuesTest.java", "palette/PaletteValuesTest.java", "ui/KitBoundaryTest.java"]:
    edit(S + test, [('isEqualTo("0.19.0")', 'isEqualTo("0.20.0")', 1)])
edit(S + "CapabilitiesTest.java", [("    @Test void theSdkIsVersionNineteenWithThePresentationCapability() {",
                                    "    @Test void theSdkIsVersionTwentyWithThePresentationCapability() {", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(A + "VersionRangeTest.java", [
    ("    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroNineteen() {",
     "    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroTwenty() {", 1),
    ('        assertThat(VersionRange.parse(">=0.19.0, <0.20").contains(host)).as("every bundled range").isTrue();\n',
     '        assertThat(VersionRange.parse(">=0.19.0, <0.20").contains(host)).as("the former bundled range").isFalse();\n'
     '        assertThat(VersionRange.parse(">=0.20.0, <0.21").contains(host)).as("every bundled range").isTrue();\n', 1)])
edit(A + "KitCompatibilityTest.java", [(r'''    /** Workspace tabs plan 3b: a manifest of the previous minor, 0.18, is skipped by its range before any class is read. */
    @Test void aZeroEighteenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.18.0, <0.19.0; this is SDK 0.19.0"));
    }''', r'''    /** Workspace tabs plan 4b: a manifest of the previous minor, 0.19, is skipped by its range before any class is read. */
    @Test void aZeroNineteenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.19.0, <0.20",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.19.0, <0.20.0; this is SDK 0.20.0"));
    }''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.CapabilitiesTest' :jasper-app:test --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: FAIL, `expected: "0.20.0" but was: "0.19.0"`, `every bundled range` false, and the 0.19 manifest loads.

- [ ] **Step 3: The version, the manifests and the guides**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java", [('VERSION = "0.19.0";', 'VERSION = "0.20.0";', 1)])
for plugin in ["editor", "enterprise", "fileserver", "history", "indexer", "remote", "sample", "snippets", "vault"]:
    edit(f"plugins/{plugin}/src/main/resources/plugin.toml", [('sdk = ">=0.19.0, <0.20"', 'sdk = ">=0.20.0, <0.21"', 1)])
edit("docs/examples/external-plugin/src/main/resources/plugin.toml", [('sdk = ">=0.19.0, <0.20"', 'sdk = ">=0.20.0, <0.21"', 1)])
edit("docs/examples/external-plugin/build.gradle.kts", [('getOrElse("0.19.0")', 'getOrElse("0.20.0")', 1)])
# 0.20 is a clean break: a guide's range for an older feature no longer loads, so every range in the guides is 0.20's.
for guide, count in {"docs/plugin-authoring.md": 14, "jasper-sdk/README.md": 8, "docs/sdk-icons.md": 1}.items():
    edit(guide, [('sdk = ">=0.19.0, <0.20"', 'sdk = ">=0.20.0, <0.21"', count)])
edit("jasper-sdk/README.md", [(
    "(0.19.0 is a breaking boundary, described under Tabs of every kind below; 0.18.0 is one too:",
    "(0.20.0 is a breaking boundary for main and child windows; 0.19.0 is one too, described under Tabs of every kind below;\n"
    "so is 0.18.0:", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java", [
    ("    /** The required descriptor keys of a plugin on SDK 0.19. */", "    /** The required descriptor keys of a plugin on SDK 0.20. */", 1),
    (r'+ "sdk = \">=0.19.0, <0.20\"\n";', r'+ "sdk = \">=0.20.0, <0.21\"\n";', 1)])
PY
```

Task 8 points the README's version line at the section it adds.

- [ ] **Step 4: Run the build to verify it passes**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL: every plugin's manifest loads on 0.20.0, `VersionRangeTest` reads 0.20's range in every
guide, and the external example builds against 0.20.0.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk jasper-app plugins docs
git commit -F - <<'EOF'
Workspace tabs: SDK 0.20.0

The SDK, every bundled manifest, the external example and the guides'
ranges move to 0.20, a breaking boundary for the main and child windows;
a 0.19 manifest is skipped by its range.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The main window, child windows and placements in the SDK and the fakes

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java`, `ui/ContentTab.java`, `terminal/WindowHandle.java`,
  `terminal/TerminalEvents.java`, `ui/OverlaySpec.java`
- Replace: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`, `FakeContent.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`
- Modify (testkit): `FakeUi.java`, `FakeTerminals.java`, `FakePluginHost.java`, `contract/ContractHarness.java`
- Modify (application): `terminals/WindowEntry.java`, `workspace/WindowTerminals.java`, `plugins/HostedTerminals.java`,
  `contributions/WorkspaceHost.java`, `workspace/WindowContent.java`, `contributions/TabRegistry.java`,
  `workspace/WindowManager.java`, `plugins/HostedUi.java`
- Test: `PluginContractTest` (three new tests), `AppContractTest`, `TabRegistryTest`, `WindowManagerTest`,
  `TerminalRegistryTest`; the moves in `FakeKitMountingTest`, `HostedChooserTest`, `KitMountingTest`,
  `WorkspaceContentRouteTest`, `EditorDocumentPreservationTest`, the Editor's `EditorWindowTest`; `StagedEditorHost`;
  `isMain()` in eight test stand-ins of `WindowHandle`; File Server's `FileServerPluginTest` (a window that closes with
  its last tab is now a child window)

**Interfaces:**
- Consumes: plan 4a's `TabRegistry.Windows(main, isMain, newWindow, movedOut)`, `moveToNewWindow(Tab)`,
  `moveToMainWindow(Tab)`, `Tab.inMainWindow()`, `WindowContent.isMain()`, `WindowContent.attach(Tab, boolean, GroupId,
  Side)`, `WindowManager.openChild(boolean, Path, Rectangle)` and its `movedOut`.
- Produces: `public enum TabPlacement { ACTIVE_GROUP, SPLIT_RIGHT, SPLIT_DOWN, NEW_WINDOW }`;
  `ContentTab Windows.openTab(ContentSpec, WindowOwner, UiView, TabPlacement)` (the three-argument form a default);
  `void ContentTab.moveToNewWindow()`, `void moveToMainWindow()`, `WindowHandle window()`; `boolean
  WindowHandle.isMain()`. App: `WindowEntry(id, tabs, selectedTab, active, BooleanSupplier main, toFront, openTab)`;
  `WorkspaceHost(…, WorkspaceHost.Beside attachBeside)` with `Beside.attach(Tab tab, GroupId group, Side side)` (beside
  `group`, the active group when it is null; plan 5's drop beside a group calls it), the six- and five-argument
  constructors kept for fakes; `TabRegistry.Windows(main, isMain, newWindow, movedOut, Supplier<UUID> emptyWindow)`
  with the three-argument test constructor kept; `TabRegistry.Placement` and `TabRegistry.open(plugin, key, title,
  owner, component, Placement)`. Fakes:
  `FakeWorkspace.Group`, `Window.main`, `main()`, `addTab(window, kind, title, Group beside)`; `TerminalFixture
  .addMainWindow()` and `addContentTab(…, boolean beside)`.

- [ ] **Step 1: Write the failing tests**

The moves get their new names everywhere a test used `move(boolean)` and `isSeparate()`, the editor host's first
window is a main window, File Server's test that needs its window to close with its last tab adds the main window
first, and the eight stand-ins of `WindowHandle` answer `isMain()` as they answer `isOpen()`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
T = "jasper-app/src/test/java/dev/jasper/app/"
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
# ContentTab.move(boolean) and isSeparate() become moveToNewWindow(), moveToMainWindow() and window().isMain().
for path, moves, backs, separate, together in [
        (P, 6, 2, 2, 4),
        ("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitMountingTest.java", 2, 0, 1, 1),
        (T + "plugins/HostedChooserTest.java", 2, 0, 1, 1),
        (T + "plugins/KitMountingTest.java", 1, 0, 0, 0),
        (T + "plugins/WorkspaceContentRouteTest.java", 1, 0, 0, 0),
        (T + "plugins/EditorDocumentPreservationTest.java", 3, 1, 1, 1),
        ("plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java", 2, 1, 1, 1)]:
    regex(path, r"\.move\(true\)", ".moveToNewWindow()", moves)
    regex(path, r"\.move\(false\)", ".moveToMainWindow()", backs)
    regex(path, r"\.isSeparate\(\)\)\.isTrue\(\)", ".window().isMain()).isFalse()", separate)
    regex(path, r"\.isSeparate\(\)\)\.isFalse\(\)", ".window().isMain()).isTrue()", together)
edit(T + "plugins/EditorDocumentPreservationTest.java", [("                    editor.contentTab().move(detached);",
    "                    if (detached) editor.contentTab().moveToNewWindow(); else editor.contentTab().moveToMainWindow();", 1)])
# The hosts' main windows are main windows of the terminal registry too, so a content tab's window says so.
edit(T + "plugins/StagedEditorHost.java", [("            var main = terminals.addWindow(); var separate = terminals.addWindow();",
                                           "            var main = terminals.addMainWindow(); var separate = terminals.addWindow();", 1)])
edit(T + "plugins/HostedChooserTest.java", [(
"""        java.util.function.Supplier<UUID> window = () -> {
            UUID id=fixture.addWindow();contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,e -> {},e -> {},e -> {},() -> {}));return id;
        };
        UUID main=window.get();
        contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> contributions.tabs().move(tab, window.get())));""",
"""        java.util.function.Function<Boolean, UUID> window = first -> {
            UUID id=first?fixture.addMainWindow():fixture.addWindow();contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,e -> {},e -> {},e -> {},() -> {}));return id;
        };
        UUID main=window.apply(true);
        contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> main, main::equals, tab -> contributions.tabs().move(tab, window.apply(false))));""", 1)])
# Test stand-ins of WindowHandle answer isMain() as they answer isOpen(): each stands for the main window.
for path, count in [("plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServerScopeTest.java", 1),
                    ("plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsTest.java", 1),
                    ("plugins/remote/src/test/java/dev/jasper/remote/edit/TestWindow.java", 1),
                    ("plugins/remote/src/test/java/dev/jasper/remote/ui/RemoteScopeTest.java", 1),
                    ("plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsScopeTest.java", 1),
                    ("plugins/vault/src/test/java/dev/jasper/vault/service/VaultServiceTest.java", 2),
                    ("plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java", 1),
                    ("plugins/vault/src/test/java/dev/jasper/vault/ui/VaultScopeTest.java", 1)]:
    regex(path, r"\n( *)((?:@Override )?)public boolean isOpen\(\) \{ return ([^;]+); \}", r"\g<0>\n\1\2public boolean isMain() { return \3; }", count)
# The first window a test adds is the main window, which outlives its last tab: a test whose window must close with its
# last tab adds the main window first, so its own window is a child window.
edit("plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java", [(
"""    @Test void noRestartPromptWhenTheEditorOrItsWindowWasClosedBeforeTheSaveFinished() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");""",
"""    @Test void noRestartPromptWhenTheEditorOrItsWindowWasClosedBeforeTheSaveFinished() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            host.addTerminalWindow();
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");""", 1)])
PY
```

Three new contract tests, run on the fake and on the application, and the application's own tests of the placement
(`AppContractTest`'s workspace adds the main window first and emulates the window manager's moves; `TabRegistryTest`
and `WindowManagerTest` place a plugin's tab beside the active group and in a new window, and check that the owner's
window is not touched):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [
    ("""    @Test void aTabMovedToAnotherWindowKeepsItsPaneAndReportsOnlyTheMove() {
        UUID first = h.addTerminalWindow(), stays = h.addTerminalTab(first, "stays");""",
     """    @Test void aTabMovedToAnotherWindowKeepsItsPaneAndReportsOnlyTheMove() {
        // The main window outlives its last tab; the windows below are child windows (workspace tabs spec 6.4).
        UUID main = h.addTerminalWindow();
        h.addTerminalPane(h.addTerminalTab(main, "main"), "zsh", Path.of("/"));
        UUID first = h.addTerminalWindow(), stays = h.addTerminalTab(first, "stays");""", 1),
    ("""        h.start(new PluginInfo("test.overlay2", "Second", "1.0.0", Set.of()), Set.of(), Set.of(), second::set);
        UUID window = h.addTerminalWindow();""",
     """        h.start(new PluginInfo("test.overlay2", "Second", "1.0.0", Set.of()), Set.of(), Set.of(), second::set);
        h.addTerminalWindow();
        // The overlay's owner is a child window (workspace tabs spec 14), which closes with its last tab.
        UUID window = h.addTerminalWindow();""", 1),
    ("""    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {""",
     """    /** Workspace tabs spec 6.4: the main window keeps an empty workspace when its last tab closes; a child window closes. */
    @Test void theMainWindowOutlivesItsLastTabWhileAChildWindowClosesWithIt() {
        UUID main = h.addTerminalWindow(), first = h.addTerminalTab(main, "zsh");
        UUID firstPane = h.addTerminalPane(first, "zsh", Path.of("/"));
        UUID child = h.addTerminalWindow(), second = h.addTerminalTab(child, "make");
        UUID secondPane = h.addTerminalPane(second, "make", Path.of("/src"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_CLOSED)) context.events().subscribe(topic, heard::add);
        });
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.window(main).orElseThrow().isMain()).as("the first window is the main window").isTrue();
            assertThat(terminals.window(child).orElseThrow().isMain()).as("a later one is a child window").isFalse();
        });
        h.flush();
        heard.clear();
        h.closeTerminalPane(firstPane);
        h.closeTerminalPane(secondPane);
        h.flush();
        assertThat(heard).as("only the child window closes").containsExactly(new TerminalEvents.WindowEvent(child));
        h.ui(() -> {
            var window = alpha.get().terminals().window(main).orElseThrow();
            assertThat(window.tabs()).isEmpty();
            assertThat(window.activeTab()).as("an empty workspace").isEmpty();
            assertThat(window.isMain()).isTrue();
            assertThat(alpha.get().terminals().window(child)).isEmpty();
        });
        UUID another = h.addTerminalWindow();
        h.ui(() -> assertThat(alpha.get().terminals().window(another).orElseThrow().isMain()).as("the main window is still open").isFalse());
    }

    /** Workspace tabs spec 6.5 and 14: a plugin's tab moves to a new child window and back, and says which window holds it. */
    @Test void aContentTabMovesToANewChildWindowAndBackToTheMainWindow() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var tab = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), null, kitView(alpha.get()));
            assertThat(tab.window().id()).as("no owner: the main window").isEqualTo(main);
            assertThat(tab.window().isMain()).isTrue();
            tab.moveToMainWindow();
            assertThat(tab.window().id()).as("already there").isEqualTo(main);
            tab.moveToNewWindow();
            var child = tab.window();
            assertThat(child.isMain()).isFalse();
            assertThat(child.tabs()).singleElement().satisfies(only -> assertThat(only.kind()).isEqualTo(TabHandle.Kind.CONTENT));
            assertThat(alpha.get().terminals().window(main).orElseThrow().tabs()).extracting(TabHandle::id).containsExactly(shell);
            tab.moveToNewWindow();
            assertThat(tab.window()).as("a child window's only tab stays").isEqualTo(child);
            tab.moveToMainWindow();
            assertThat(tab.window().id()).isEqualTo(main);
            assertThat(child.isOpen()).as("the child window closed with its last tab").isFalse();
            tab.close();
            assertThat(tab.window().id()).as("a closed tab names the last window that held it").isEqualTo(main);
        });
    }

    /** Workspace tabs spec 14's TabPlacement: the end of the active group, a new group after it, or a new child window. */
    @Test void aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow() {
        UUID main = h.addTerminalWindow(), left = h.addTerminalTab(main, "left");
        UUID pane = h.addTerminalPane(left, "zsh", Path.of("/"));
        UUID right = h.addTerminalTab(main, "right");
        h.addTerminalPane(right, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            var windows = alpha.get().windows();
            var owner = terminals.window(main).orElseThrow();
            UUID split = terminals.split(terminals.pane(pane).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow().tab().id();
            terminals.tab(left).orElseThrow().select();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("here", "Here"), owner, kitView(alpha.get()));
            var below = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Below"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
            List<UUID> ids = owner.tabs().stream().map(TabHandle::id).toList();
            assertThat(ids).hasSize(5);
            assertThat(ids.subList(0, 2)).as("the active group's tabs come first").containsExactly(left, right);
            assertThat(ids.getLast()).as("the split's group stays last").isEqualTo(split);
            UUID here = ids.get(2), placed = ids.get(3);
            assertThat(owner.activeTab().map(TabHandle::id)).as("a placed tab opens selected").contains(placed);
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("next", "Next"), owner, kitView(alpha.get()));
            assertThat(owner.tabs().stream().map(TabHandle::id).toList()).as("the new group is the active group")
                .hasSize(6).startsWith(left, right, here, placed).endsWith(split);
            var active = owner.activeTab().map(TabHandle::id);
            var away = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("away", "Away"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW);
            assertThat(away.window().isMain()).as("a new child window").isFalse();
            assertThat(away.window().tabs()).hasSize(1);
            assertThat(owner.tabs()).as("the owner's window is not touched").hasSize(6);
            assertThat(owner.activeTab().map(TabHandle::id)).as("nor its active tab").isEqualTo(active);
            assertThat(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Again"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW)).as("an open key is selected where it is").isSameAs(below);
            assertThat(below.window().id()).isEqualTo(main);
        });
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {""", 1)])
edit(T + "plugins/AppContractTest.java", [
    ("""        // The window manager's part: the window added last that no move made is the main window (workspace plan 4a).
        UUID[] main = new UUID[1];
        java.util.Set<UUID> separate = new java.util.HashSet<>();
        java.util.function.Function<Boolean, UUID> workspace = forMove -> {
            UUID id = terminalFixture.addWindow();
            if (forMove) separate.add(id); else main[0] = id;
            var tabs = contributions.tabs();""",
     """        // The window manager's part (workspace plan 4a): the window added while none is open is the main window, which
        // outlives its last tab; every other window is a child window, whose only tab stays where it is.
        UUID[] main = new UUID[1];
        java.util.function.Supplier<UUID> workspace = () -> {
            boolean first = main[0] == null;
            UUID id = first ? terminalFixture.addMainWindow() : terminalFixture.addWindow();
            if (first) main[0] = id;
            var tabs = contributions.tabs();""", 1),
    ("""                entry -> {
                    for (var other : tabs.inWindow(id)) other.active(other == entry);
                    terminalFixture.selectTab(entry.id().value());
                }, () -> {}));""",
     """                entry -> {
                    for (var other : tabs.inWindow(id)) other.active(other == entry);
                    terminalFixture.selectTab(entry.id().value());
                }, () -> {}, tab -> Optional.empty(),
                (tab, group, side) -> terminalFixture.addContentTab(id, tab.id().value(), tab::title, true)));""", 1),
    ("""        onEdt(() -> contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(
            () -> main[0] != null ? main[0] : workspace.apply(false), id -> !separate.contains(id),
            tab -> contributions.tabs().move(tab, workspace.apply(true)))));""",
     """        onEdt(() -> contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(
            () -> main[0] != null ? main[0] : workspace.get(), id -> id.equals(main[0]),
            tab -> {
                boolean only = terminalFixture.registry.window(tab.window()).map(window -> window.tabs().get().size() == 1).orElse(false);
                if (!tab.window().equals(main[0]) && only) return;
                contributions.tabs().move(tab, workspace.get());
            }, window -> { }, workspace)));""", 1),
    ("            @Override public UUID addTerminalWindow() { return onEdtValue(() -> workspace.apply(false)); }",
     "            @Override public UUID addTerminalWindow() { return onEdtValue(workspace); }", 1)])
edit(T + "contributions/TabRegistryTest.java", [(
"""    @Test void withoutTheWindowManagerTheFirstWorkspaceIsTheMainWindowAndNothingMovesToANewWindow() {""",
"""    @Test void aPlacementAttachesBesideTheActiveGroupOrInANewWindowAndLeavesTheOwnerAlone() {
        var heard = new ArrayList<String>();
        registry.addHost(new WorkspaceHost(main, tab -> heard.add("attach " + tab.title()), mounted::remove,
            tab -> heard.add("select " + tab.title()), () -> {}, tab -> Optional.empty(),
            (tab, group, side) -> heard.add("beside " + tab.title() + " " + side + (group == null ? " of the active group" : ""))));
        registry.addHost(new WorkspaceHost(detached, tab -> heard.add("new window " + tab.title()), mounted::remove, tab -> {}, () -> {}));
        registry.windows(new TabRegistry.Windows(() -> main, main::equals, tab -> registry.move(tab, detached), window -> {}, () -> detached));
        registry.open("plugin.file", "right", "Right", main, new JPanel(), TabRegistry.Placement.SPLIT_RIGHT);
        registry.open("plugin.file", "down", "Down", main, new JPanel(), TabRegistry.Placement.SPLIT_DOWN);
        var here = registry.open("plugin.file", "here", "Here", main, new JPanel());
        assertThat(heard).containsExactly("beside Right RIGHT of the active group", "select Right",
            "beside Down DOWN of the active group", "select Down", "attach Here", "select Here");
        heard.clear();
        var away = registry.open("plugin.file", "away", "Away", main, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
        assertThat(away.window()).isEqualTo(detached);
        assertThat(heard).as("opened in the new window; the owner's window is not touched").containsExactly("new window Away");
        assertThat(here.window()).isEqualTo(main);
        assertThat(registry.open("plugin.file", "right", "Again", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW).window())
            .as("an open key is selected where it is").isEqualTo(main);
    }

    @Test void withoutTheWindowManagerTheFirstWorkspaceIsTheMainWindowAndNothingMovesToANewWindow() {""", 1)])
edit(T + "workspace/WindowManagerTest.java", [
    ("import dev.jasper.app.contributions.Tab;\n", "import dev.jasper.app.contributions.Tab;\nimport dev.jasper.app.contributions.TabRegistry;\n", 1),
    ("""    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {""",
     """    @Test void aPluginsTabPlacedBesideOpensAGroupAndOnePlacedInANewWindowAChildWindow() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(main.content().workspace().model().groups()).hasSize(1);
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", null, new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(main.content().workspace().model().groups()).as("a new group").hasSize(2);
            assertThat(beside.group()).isNotEqualTo(notes.group());
            Tab selected = main.content().selectedTab();
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            assertThat(away.window()).isEqualTo(manager.children().getFirst().id());
            assertThat(manager.children().getFirst().content().tabCount()).isEqualTo(1);
            assertThat(main.content().workspace().model().groups()).as("the main window is not touched").hasSize(2);
            assertThat(main.content().selectedTab()).isSameAs(selected);
        });
        launch();
    }

    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:compileTestJava --continue`
Expected: FAIL, `cannot find symbol` for `moveToNewWindow`, `moveToMainWindow`, `isMain`, `TabPlacement`,
`addMainWindow`, `TabRegistry.Placement` and `attachBeside`.

- [ ] **Step 3: The SDK**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java`:

```java
package dev.jasper.sdk.ui;

/**
 * Where {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView, TabPlacement)} puts a new content tab
 * (workspace tabs spec 14). A tab already open for the same key is selected where it is, whatever the placement.
 *
 * @since 0.20.0
 */
public enum TabPlacement {
    /** At the end of the active group of the owner's window, or of the main window when there is no owner. */
    ACTIVE_GROUP,
    /** In a new group to the right of that active group. */
    SPLIT_RIGHT,
    /** In a new group below that active group. */
    SPLIT_DOWN,
    /**
     * In a new child window of its own, opened for it; the owner's window is not touched. The topics report
     * {@code WINDOW_OPENED}, then {@code TAB_OPENED} in the new window.
     */
    NEW_WINDOW
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
D = "jasper-sdk/src/main/java/dev/jasper/sdk/"
edit(D + "ui/Windows.java", [(
"""    /** Opens an owned view as content; moving retains it, final closure disposes it. */
    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content);
""", """    /**
     * Opens an owned view as a content tab at the end of the active group of the owner's window
     * ({@link TabPlacement#ACTIVE_GROUP}); moving retains it, final closure disposes it.
     *
     * @param spec the tab's key and title
     * @param owner the window the tab opens in, or a content tab whose window it opens in; null, or a closed window, for
     *              the main window
     * @param content the plugin's owned view
     * @return the new tab, or the open tab with the same key, selected
     */
    default ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content) {
        return openTab(spec, owner, content, TabPlacement.ACTIVE_GROUP);
    }

    /**
     * Opens an owned view as a content tab where {@code placement} puts it; moving retains it, final closure disposes it.
     *
     * @param spec the tab's key and title
     * @param owner the window the tab opens in, or a content tab whose window it opens in; null, or a closed window, for
     *              the main window
     * @param content the plugin's owned view
     * @param placement where a new tab goes; the open tab with the same key is selected where it is
     * @return the new tab, or the open tab with the same key, selected
     * @throws IllegalArgumentException for a foreign owner or a closed content tab
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @since 0.20.0
     */
    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);
""", 1)])
edit(D + "ui/ContentTab.java", [(
"""    /** @param separate true moves to a new separate workspace; false moves to the last main workspace */
    void move(boolean separate);
    /** @return whether the content lives in a separate window */
    boolean isSeparate();
""", """    /**
     * Move Tab to New Window: a new child window takes the tab and its content. A child window's only tab, a tab with a
     * modal prompt open and a tab being closed stay where they are.
     *
     * @since 0.20.0
     */
    void moveToNewWindow();
    /**
     * Move Tab to Main Window: the end of the main window's active group takes the tab and its content, the main window
     * restored first when none is open. A tab the main window holds, a tab with a modal prompt open and a tab being
     * closed stay where they are.
     *
     * @since 0.20.0
     */
    void moveToMainWindow();
    /**
     * The window that holds the tab; {@link dev.jasper.sdk.terminal.WindowHandle#isMain()} tells the main window from a
     * child window.
     *
     * @return the window, or the last window that held the tab once it is closed
     * @since 0.20.0
     */
    dev.jasper.sdk.terminal.WindowHandle window();
""", 1)])
edit(D + "terminal/WindowHandle.java", [(
"""    /** Raises the window. Ignored once it is closed. */
""", """    /**
     * Whether this is the main window, the one with Jasper's toolbar, tool windows and status bar, which stays open
     * when its last tab closes; any other window is a child window, a title bar over its tabs, which closes with its
     * last tab (workspace tabs spec 6).
     *
     * @return true for the main window while it is open
     * @since 0.20.0
     */
    boolean isMain();

    /** Raises the window. Ignored once it is closed. */
""", 1)])
edit(D + "terminal/TerminalEvents.java", [
    ("    /** A Jasper window opened. */", "    /** A Jasper window, the main window or a child window, opened. */", 1),
    ("    /** A Jasper window closed. */", "    /** A Jasper window closed; the main window stays open when its last tab closes. */", 1)])
edit(D + "ui/OverlaySpec.java", [(" * @param owner an open Jasper window from this host", " * @param owner an open Jasper window, the main window or a child window, from this host", 1)])
PY
```

- [ ] **Step 4: The testkit's windows and groups**

Replace the whole of `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 6, 9.4 and 15). The
 * window opened while no main window is open is the main window, which stays open with an empty workspace when its last
 * tab leaves; any other window is a child window, which closes with its last tab. A window's tabs sit in groups and are
 * listed group by group: a new tab opens at the end of the active group, a split or a placement beside it opens a new
 * group after it, closing a group's selected tab selects the tab the group showed before it, and closing a group's last
 * tab makes the group used before it active. A terminal tab holds one pane, a split opens a terminal tab of its own, the
 * active pane is the pane of the active tab of the window activated last when that tab is a terminal tab, focusing a pane
 * selects its tab, a pane closes with its tab, a tab moved to another window is announced once as a move, and every
 * change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    /** One group of a window's tabs; {@code recent} holds them most recently selected first. */
    static final class Group {
        final List<Tab> recent = new ArrayList<>();
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; Group group; String title; Pane pane; boolean open = true;
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID();
        /** Whether this is the main window, which outlives its last tab. */
        final boolean main;
        /** Every tab, group by group. */
        final List<Tab> tabs = new ArrayList<>();
        /** The groups that hold tabs, most recently active first: the first is the active group. */
        final List<Group> recent = new ArrayList<>();
        Tab selected; boolean open = true;
        Window(boolean main) { this.main = main; }
        /** @return the active group, or null in an empty workspace */
        Group active() { return recent.isEmpty() ? null : recent.getFirst(); }
    }

    private final FakePluginHost host;
    final List<Window> windows = new ArrayList<>();
    /** Every pane ever added, so a test can still read what was sent to one that closed. */
    private final List<Pane> everyPane = new ArrayList<>();
    private final List<Tab> everyTab = new ArrayList<>();
    final List<String> openRequests = new ArrayList<>();
    /** The shell profiles the test says the application detected. */
    List<ShellProfileInfo> profiles = List.of();
    /** Every live {@code onProfilesChanged} listener, of every plugin. */
    final List<Runnable> profileListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    Window lastActive;
    private UUID reportedActive;

    final FakeSessions sessions;

    FakeWorkspace(FakePluginHost host) { this.host = host; this.sessions = new FakeSessions(host, this); }

    List<Pane> everyOpenPane() { return everyPane.stream().filter(pane -> pane.open).toList(); }

    Optional<Window> window(UUID id) { return windows.stream().filter(window -> window.id.equals(id)).findFirst(); }
    /** @return the main window, while it is open */
    Optional<Window> main() { return windows.stream().filter(window -> window.main).findFirst(); }
    Optional<Tab> tab(UUID id) { return everyTab.stream().filter(tab -> tab.open && tab.id.equals(id)).findFirst(); }
    Optional<Pane> pane(UUID id) { return everyPane.stream().filter(pane -> pane.open && pane.id.equals(id)).findFirst(); }
    Optional<Pane> anyPane(UUID id) { return everyPane.stream().filter(pane -> pane.id.equals(id)).findFirst(); }
    Optional<Pane> activePane() {
        return Optional.ofNullable(lastActive).filter(window -> window.open).map(window -> window.selected).map(tab -> tab.pane);
    }

    private void refreshActive() {
        UUID now = activePane().map(pane -> pane.id).orElse(null);
        if (Objects.equals(now, reportedActive)) return;
        reportedActive = now;
        host.publishApp(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.ofNullable(now)));
    }

    /** A window: the main window when none is open, else a child window. */
    Window addWindow() {
        var window = new Window(main().isEmpty());
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title, null); }

    /** A tab of {@code kind}, selected, at the end of the active group. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) { return addTab(window, kind, title, null); }

    /** A tab of {@code kind}, selected, in a new group after {@code beside}; at the end of the active group when it is null. */
    Tab addTab(Window window, TabHandle.Kind kind, String title, Group beside) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        place(tab, window, beside);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        select(tab);
        return tab;
    }

    /** Puts {@code tab} at the end of {@code window}'s active group, or in a new group after {@code beside}. */
    private void place(Tab tab, Window window, Group beside) {
        Group group = beside == null ? window.active() : null;
        Group after = beside != null ? beside : group;
        int index = window.tabs.size();
        if (after != null) for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == after) index = i + 1;
        if (group == null) { group = new Group(); window.recent.add(group); }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }

    void select(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected == tab) return;
        window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));
        refreshActive();
    }

    /** A terminal tab's one pane; a second, or one in a content tab, is refused. */
    Pane addPane(Tab tab, PaneInfo info) {
        if (tab.kind != TabHandle.Kind.TERMINAL) throw new IllegalArgumentException("A content tab holds no pane");
        if (tab.pane != null) throw new IllegalStateException("A terminal tab holds one pane");
        var pane = new Pane(tab, info);
        everyPane.add(pane);
        tab.pane = pane;
        host.publishApp(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        refreshActive();
        return pane;
    }

    void activate(Window window) {
        if (!window.open) return;
        lastActive = window;
        host.publishApp(TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }

    void focus(Pane pane) {
        if (!pane.open) return;
        select(pane.tab);
        host.publishApp(TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(pane.tab.id, pane.id));
        refreshActive();
    }

    /**
     * Moves a tab to the end of another open window's active group, where it is selected; a child window left with no
     * tab closes after the move is announced.
     */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        if (takeOut(tab)) reselect(from);
        place(tab, to, null);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty() && !from.main) closeWindow(from);
        refreshActive();
    }

    /** A pane closes with its tab. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes a tab, its pane with it; a child window's last tab takes the window with it. */
    void close(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        Pane pane = tab.pane;
        if (pane != null && pane.open) {
            sessions.closing(pane);
            pane.open = false;
            host.publishApp(TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        }
        tab.open = false;
        boolean selected = takeOut(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (selected) reselect(window);
        if (window.tabs.isEmpty() && !window.main) closeWindow(window);
        refreshActive();
    }

    /** Takes {@code tab} out of its window's groups; true when it was the window's selected tab. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected tab left: the tab the active group showed before it, else the group used before, takes over. */
    private void reselect(Window window) {
        if (!window.recent.isEmpty()) select(window.active().recent.getFirst());
    }

    private void closeWindow(Window window) {
        window.open = false;
        windows.remove(window);
        if (lastActive == window) lastActive = null;
        host.closeToolbarViews(window.id);
        host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
    }
}
```

Replace the whole of `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * Recorded host content, retaining the actual component across moves. Its tab is a content tab of the scripted
 * workspace (workspace tabs spec 14): it opens, is selected, moves and closes on the tab topics like any tab, and moves to
 * a new child window or the main window as the application's do (spec 6.5).
 */
final class FakeContent implements ContentTab {
    final FakeUi ui;
    final String key;
    final JComponent component;
    /** The workspace tab that shows this content. */
    final FakeWorkspace.Tab tab;
    ContentPresentation presentation;
    List<ContentCommand> commands=List.of();
    List<ContentStatus> status=List.of();
    WindowOwner owner;
    boolean active,closed;
    int pins;
    Runnable focus=()->{};
    final List<Consumer<Boolean>> activated=new ArrayList<>();
    final List<Runnable> disposed=new ArrayList<>();
    FakeContent(FakeUi ui,ContentSpec spec,WindowOwner owner,JComponent component,FakeWorkspace.Tab tab){
        this.ui=ui;this.key=spec.key();this.owner=owner;this.component=component;this.tab=tab;
        presentation=new ContentPresentation(spec.title(),"",null,false,"","");
    }
    public void setPresentation(ContentPresentation value){if(!closed){ui.context.kit().validateIcon(value.icon());presentation=value;tab.title=value.title();}}
    public void setCommands(List<ContentCommand> values){if(!closed)commands=List.copyOf(values);}
    public void setStatus(List<ContentStatus> values){if(!closed)status=List.copyOf(values);}
    public void setFocusTarget(Runnable value){if(!closed)focus=value;}
    public void select(){if(closed)return;ui.host.workspace.select(tab);for(var value:ui.host.contentTabs()) { var other=(FakeContent)value; if(other!=this && Objects.equals(owner, other.owner))other.active(false); }active(true);focus.run();}
    void active(boolean value){if(active==value)return;active=value;for(var listener:List.copyOf(activated))listener.accept(value);}
    /** Neither closed, pinned by a modal prompt, nor waiting on a close decision. */
    private boolean movable(){return !closed&&pins==0&&ui.pendingContent==null;}
    public void moveToNewWindow(){
        // A child window's only tab already is its own window.
        if(!movable()||!tab.window.main&&tab.window.tabs.size()==1)return;
        moveTo(ui.host.workspace.addWindow());
    }
    public void moveToMainWindow(){
        if(!movable()||tab.window.main)return;
        moveTo(ui.host.workspace.main().orElseGet(ui.host.workspace::addWindow));
    }
    private void moveTo(FakeWorkspace.Window window){
        active(false);
        ui.host.workspace.move(tab, window);
        owner=ui.context.terminals.windowHandle(window.id); select();
    }
    public WindowHandle window(){return ui.context.terminals.windowHandle(tab.window.id);}
    public boolean isActive(){return active;} public boolean isClosed(){return closed;}
    public CompletableFuture<Boolean> requestClose(){return ui.closeContent(List.of(this),true);}
    public Subscription onActive(Consumer<Boolean> value){activated.add(value);return ()->activated.remove(value);}
    public Subscription onClosed(Runnable value){disposed.add(value);return ()->disposed.remove(value);}
    public void close(){if(closed)return;active(false);closed=true;ui.contents.remove(this);ui.host.workspace.close(tab);for(var listener:List.copyOf(disposed))listener.run();disposed.clear();activated.clear();}
}
```

Then the placement in `FakeUi`, `isMain()` and splits beside their group in `FakeTerminals`, and the documents of
`FakePluginHost` and `ContractHarness`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeUi.java", [
    ("""            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content) {
                context.requireOpen();var view=context.kit().requireOwned(content);
                for(var entry:contents)if(entry.key.equals(spec.key())){entry.select();return entry;}
                view.mount(spec);view.route="WORKSPACE";try{var tab=openComponent(spec,owner,view.component);tab.onClosed(view::close);return tab;}catch(RuntimeException | Error failure){view.route="CHROME";view.unmount(spec);throw failure;}
            }
            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component) {
""", """            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                context.requireOpen();java.util.Objects.requireNonNull(placement,"placement");var view=context.kit().requireOwned(content);
                for(var entry:contents)if(entry.key.equals(spec.key())){entry.select();return entry;}
                view.mount(spec);view.route="WORKSPACE";try{var tab=openComponent(spec,owner,view.component,placement);tab.onClosed(view::close);return tab;}catch(RuntimeException | Error failure){view.route="CHROME";view.unmount(spec);throw failure;}
            }
            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component,
                    dev.jasper.sdk.ui.TabPlacement placement) {
""", 1),
    ("""                if (owner == null || !((WindowHandle)owner).isOpen()) {
                    var window = host.workspace.windows.stream().filter(w -> w.open && !w.detached).findFirst().orElseGet(host.workspace::addWindow);
                    owner = context.terminals.windowHandle(window.id);
                }
                // A plugin's content is a content tab of its window (workspace tabs spec 14).
                var shown = host.workspace.window(((WindowHandle) owner).id()).orElseThrow();
                var entry=new FakeContent(FakeUi.this,spec,owner,component,host.workspace.addTab(shown,dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT,spec.title()));
                contents.add(entry);entry.select();return entry;
""", """                if (owner == null || !((WindowHandle)owner).isOpen()) {
                    // The main window, as the application restores it first when none is open (workspace tabs spec 6.4).
                    var window = host.workspace.main().orElseGet(host.workspace::addWindow);
                    owner = context.terminals.windowHandle(window.id);
                }
                // A plugin's content is a content tab of its window (workspace tabs spec 14), placed as asked: in a new child
                // window of its own, opened for it, the owner's window untouched.
                var shown = placement == dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW ? host.workspace.addWindow()
                    : host.workspace.window(((WindowHandle) owner).id()).orElseThrow();
                if (placement == dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW) owner = context.terminals.windowHandle(shown.id);
                var beside = placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT || placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN ? shown.active() : null;
                var entry=new FakeContent(FakeUi.this,spec,owner,component,host.workspace.addTab(shown,dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT,spec.title(),beside));
                contents.add(entry);entry.select();
                return entry;
""", 1)])
edit(K + "FakeTerminals.java", [
    ("""        @Override public boolean isOpen() { context.requireOpen(); return workspace.window(id).isPresent(); }
        @Override public void toFront() { context.requireOpen(); workspace.window(id).ifPresent(window -> workspace.openRequests.add("front|" + id)); }
""", """        @Override public boolean isOpen() { context.requireOpen(); return workspace.window(id).isPresent(); }
        @Override public boolean isMain() { context.requireOpen(); return workspace.window(id).map(window -> window.main).orElse(false); }
        @Override public void toFront() { context.requireOpen(); workspace.window(id).ifPresent(window -> workspace.openRequests.add("front|" + id)); }
""", 1),
    ("""            // A split is a terminal tab of its own in the target's window, as in the application (spec 9.4).
""", """            // A split is a terminal tab of its own in a new group after the target's, as in the application (spec 9.4).
""", 1),
    ("                    yield workspace.addPane(workspace.addTab(window, \"opened\"), openedInfo(local));\n",
     "                    yield workspace.addPane(workspace.addTab(window, TabHandle.Kind.TERMINAL, \"opened\", model.tab.group), openedInfo(local));\n", 1),
    ("                    FakeWorkspace.Pane created = workspace.addPane(workspace.addTab(window, session.spec().title()), sessionInfo(session));\n",
     "                    FakeWorkspace.Pane created = workspace.addPane(workspace.addTab(window, TabHandle.Kind.TERMINAL, session.spec().title(), model.tab.group),\n"
     "                        sessionInfo(session));\n", 1)])
edit(K + "FakePluginHost.java", [
    ("""    /**
     * Adds a Jasper window. Windows, tabs and panes announce themselves on the terminal topics like the
     * application's, delivered by {@link #flush()}.
     *
     * @return the new window's id
     */""", """    /**
     * Adds a Jasper window: the main window when none is open, which stays open when its last tab leaves, else a child
     * window, which closes with its last tab. Windows, tabs and panes announce themselves on the terminal topics like the
     * application's, delivered by {@link #flush()}.
     *
     * @return the new window's id
     */""", 1),
    ("""    /**
     * Closes a pane with its tab; the last tab takes its window with it.
     *""", """    /**
     * Closes a pane with its tab; a child window's last tab takes the window with it.
     *""", 1),
    ("""     * {@code TAB_MOVED}, and a window left with no tab closes after it. A plugin moves its own content tab with
     * {@code ContentTab.move}.""", """     * {@code TAB_MOVED}, and a child window left with no tab closes after it. A plugin moves its own content tab with
     * {@code ContentTab.moveToNewWindow} or {@code moveToMainWindow}.""", 1),
    ('                throw new IllegalArgumentException("A plugin moves its own content tab with ContentTab.move");',
     '                throw new IllegalArgumentException("A plugin moves its own content tab with ContentTab.moveToNewWindow or moveToMainWindow");', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [
    ("    /** UI thread: adds a Jasper window, which announces itself on the terminal topics. */",
     "    /**\n     * UI thread: adds a Jasper window, which announces itself on the terminal topics: the main window when none is open,\n"
     "     * which stays open when its last tab leaves, else a child window, which closes with its last tab.\n     */", 1),
    ("    /** UI thread: closes a pane with its tab; the last tab takes its window with it. */",
     "    /** UI thread: closes a pane with its tab; a child window's last tab takes the window with it. */", 1),
    ("    /** UI thread: moves a tab the test added to another open window, where it is selected; a window left with no tab closes. */",
     "    /** UI thread: moves a tab the test added to another open window, where it is selected; a child window left with no tab closes. */", 1)])
PY
```

- [ ] **Step 5: The application**

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java` (the app's contract
fixture, with the fakes' model):

```java
package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.session.AttachedConnection;

/**
 * A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. A terminal tab holds one
 * pane, a split opens a terminal tab of its own in a new group after the pane's, focusing a pane selects its tab, and a
 * pane closes with its tab (workspace tabs spec 9.4). A window's tabs sit in groups, listed group by group, as the
 * testkit's fake keeps them: a new tab opens at the end of the active group, closing a group's selected tab selects the
 * tab the group showed before it, and closing a group's last tab makes the group used before it active. A main window
 * ({@link #addMainWindow}) stays open when its last tab leaves; any other window closes with its last tab (spec 6.4). A
 * plugin's tab is a content tab of its window, and a tab moved to another window is reported as a move. EDT only.
 */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); dev.jasper.app.terminals.RemoteLocation remote;
        SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = ""; String shell = "zsh"; }
    /** One group of a window's tabs; {@code recent} holds them most recently selected first. */
    private static final class Group { final List<Tab> recent = new ArrayList<>(); }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; Group group; TabEntry.Kind kind = TabEntry.Kind.TERMINAL; Supplier<String> title; Pane pane; }
    private static final class Window {
        UUID id = UUID.randomUUID(); boolean main; List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; Runnable closed = () -> { };
        /** The groups that hold tabs, most recently active first: the first is the active group. */
        final List<Group> recent = new ArrayList<>();
        Group active() { return recent.isEmpty() ? null : recent.getFirst(); }
    }

    final TerminalRegistry registry = new TerminalRegistry();
    private final Map<UUID, Window> windows = new LinkedHashMap<>();
    private final Map<UUID, Tab> tabs = new LinkedHashMap<>();
    private final Map<UUID, Pane> panes = new LinkedHashMap<>();
    /** Kept so a test can still read what was sent to a pane that closed, as the testkit's fake allows. */
    private final Map<UUID, Pane> closedPanes = new LinkedHashMap<>();
    private final List<String> opened = new ArrayList<>();

    private PaneEntry entry(Pane pane) {
        return new PaneEntry(pane.id, pane.tab.id,
            () -> new PaneSnapshot(pane.title, pane.remote == null ? Optional.ofNullable(pane.directory) : Optional.empty(), 80, 24, true,
                pane.state.equals("CONNECTING") ? PaneSnapshot.State.STARTING : pane.state.equals("EXITED") ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING,
                OptionalInt.empty(), Optional.ofNullable(pane.request).map(SessionRequest::providerId), Optional.ofNullable(pane.remote), pane.shell),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (side, spec) -> {
                // A split is a terminal tab of its own, in a new group beside the pane's group (spec 9.4).
                UUID window = pane.tab.window.id;
                UUID created = switch (spec) {
                    case OpenSpec.Local local -> {
                        opened.add("split|" + pane.id + "|" + side + "|" + local.directory().map(Path::toString).orElse("-")
                            + local.profile().map(profile -> "|" + profile).orElse("")
                            + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                        yield addPane(addTab(window, "split", pane.tab.group), "split", local.directory().orElse(pane.directory));
                    }
                    case OpenSpec.Session session -> {
                        opened.add("session-split|" + pane.id + "|" + side + "|" + session.request().title());
                        yield addSessionPane(addTab(window, session.request().title(), pane.tab.group), session.request());
                    }
                };
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, tab.kind, () -> Optional.ofNullable(tab.pane).map(this::entry), tab.title, () -> select(tab));
    }

    /** A child window, which closes with its last tab. */
    UUID addWindow() { return addWindow(false); }

    /** The main window, which stays open with an empty workspace when its last tab leaves. */
    UUID addMainWindow() { return addWindow(true); }

    private UUID addWindow(boolean main) {
        var window = new Window();
        window.main = main;
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> window.main, () -> opened.add("front|" + window.id), spec -> switch (spec) {
                case OpenSpec.Local local -> {
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-")
                        + local.profile().map(profile -> "|" + profile).orElse("")
                        + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                    yield Optional.of(entry(panes.get(addPane(addTab(window.id, "opened"), "opened", local.directory().orElse(null)))));
                }
                case OpenSpec.Session session -> {
                    opened.add("session-tab|" + window.id + "|" + session.request().title());
                    yield Optional.of(entry(panes.get(addSessionPane(addTab(window.id, session.request().title()), session.request()))));
                }
            }));
        return window.id;
    }

    /** A terminal tab at the end of the active group, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) { return addTab(windowId, title, null); }

    /** A terminal tab, selected, in a new group after {@code beside}; at the end of the active group when it is null. */
    private UUID addTab(UUID windowId, String title, Group beside) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.title = () -> title;
        tabs.put(tab.id, tab);
        place(tab, window, beside);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
        return tab.id;
    }

    /** Puts {@code tab} at the end of {@code window}'s active group, or in a new group after {@code beside}. */
    private void place(Tab tab, Window window, Group beside) {
        Group group = beside == null ? window.active() : null;
        Group after = beside != null ? beside : group;
        int index = window.tabs.size();
        if (after != null) for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == after) index = i + 1;
        if (group == null) { group = new Group(); window.recent.add(group); }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }

    private void select(Tab tab) {
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected == tab) return;
        window.selected = tab;
        registry.publish(new TerminalEvent.TabSelected(window.id, tab.id));
    }

    /** Takes {@code tab} out of its window's groups; true when it was the window's selected tab. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected tab left: the tab the active group showed before it, else the group used before, takes over. */
    private void reselect(Window window) {
        if (!window.recent.isEmpty()) select(window.active().recent.getFirst());
    }

    /** A terminal tab's one pane; a second, or a pane in a content tab, is refused. */
    UUID addPane(UUID tabId, String title, Path directory) {
        Tab tab = tabs.get(tabId);
        if (tab.kind != TabEntry.Kind.TERMINAL || tab.pane != null) throw new IllegalStateException("A terminal tab holds one pane");
        var pane = new Pane(); pane.tab = tab; pane.title = title; pane.directory = directory;
        panes.put(pane.id, pane);
        tab.pane = pane;
        registry.publish(new TerminalEvent.PaneOpened(tab.id, pane.id));
        return pane.id;
    }

    /** A content tab, such as a plugin's view, with the id and title its window gives it, at the end of the active group; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) { addContentTab(windowId, tabId, title, false); }

    /** As above; with {@code beside}, in a new group after the active group, as a split placement opens it. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title, boolean beside) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.id = tabId; tab.title = title; tab.kind = TabEntry.Kind.CONTENT;
        tabs.put(tab.id, tab);
        place(tab, window, beside ? window.active() : null);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
    }

    /** Selects a tab in its window, as a click on it does. */
    void selectTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) select(tab);
    }

    /**
     * Moves a tab to the end of another window's active group, where it is selected: the move is reported once, its pane
     * keeps its id with no PaneOpened or PaneClosed, and a child window left with no tab closes after the move is
     * reported (workspace plan 3b Rulings 5, 6; plan 4a Rulings 23).
     */
    void moveTab(UUID tabId, UUID windowId) {
        Tab tab = tabs.get(tabId);
        Window to = windows.get(windowId);
        if (tab == null || to == null || tab.window == to) return;
        Window from = tab.window;
        registry.atomically(() -> {
            if (takeOut(tab)) reselect(from);
            place(tab, to, null);
            registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
            select(tab);
            if (from.tabs.isEmpty() && !from.main) closeWindow(from);
        });
    }

    /** Closes a tab of any kind, a terminal tab's pane with it. */
    void closeTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) close(tab);
    }

    /** Runs {@code closed} once the window closes, as the application drops a closed window's workspace. */
    void onWindowClosed(UUID windowId, Runnable closed) { windows.get(windowId).closed = closed; }

    void activateWindow(UUID windowId) { registry.windowActivated(windowId); }

    /** Focusing a pane selects its tab, as in the application. */
    void focusPane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null) return;
        registry.atomically(() -> { select(pane.tab); registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id)); });
    }

    /** A pane closes with its tab, as in the application. */
    void closePane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane != null) close(pane.tab);
    }

    /** Closes a tab, its pane with it; a child window's last tab takes the window with it. */
    private void close(Tab tab) {
        if (tabs.remove(tab.id) == null) return;
        Window window = tab.window;
        Pane pane = tab.pane;
        if (pane != null && panes.remove(pane.id) != null) {
            closedPanes.put(pane.id, pane);
            if (pane.attempt != null) pane.attempt.cancel();
            if (pane.connection != null) { pane.connection.close().run(); pane.connection = null; }
        }
        registry.atomically(() -> {
            if (pane != null) registry.publish(new TerminalEvent.PaneClosed(tab.id, pane.id));
            boolean selected = takeOut(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (selected) reselect(window);
            if (window.tabs.isEmpty() && !window.main) closeWindow(window);
        });
    }

    private void closeWindow(Window window) {
        windows.remove(window.id);
        window.registration.close();
        window.closed.run();
    }

    void select(UUID paneId, String text) { panes.get(paneId).selection = text; }
    List<String> sent(UUID paneId) {
        Pane pane = panes.containsKey(paneId) ? panes.get(paneId) : closedPanes.get(paneId);
        return pane == null ? List.of() : List.copyOf(pane.sent);
    }
    List<String> opened() { return List.copyOf(opened); }

    void finishCommand(UUID paneId, String command, int exitStatus) {
        registry.publish(new TerminalEvent.CommandFinished(paneId, command, OptionalInt.of(exitStatus), Duration.ofMillis(1500),
            Optional.ofNullable(panes.get(paneId)).filter(pane -> pane.remote == null).map(pane -> pane.directory),
            Optional.ofNullable(panes.get(paneId)).map(pane -> pane.remote)));
    }

    private UUID addSessionPane(UUID tabId, SessionRequest request) {
        UUID id = addPane(tabId, request.title(), null);
        panes.get(id).request = request;
        connect(panes.get(id));
        return id;
    }

    /** What a pane does for a provided session, without a pane: the attempt, the attach, the exit and the close. */
    private void connect(Pane pane) {
        // Attempts are driven from any thread; the registry belongs to the EDT. Inline when already there, so EDT tests stay synchronous.
        var attempt = new SessionAttempt(pane.id, 80, 24, pane.request.cleanup(),
            task -> { if (javax.swing.SwingUtilities.isEventDispatchThread()) task.run(); else javax.swing.SwingUtilities.invokeLater(task); });
        pane.attempt = attempt;
        pane.state = "CONNECTING"; pane.status = "";
        attempt.onStatus = text -> { if (pane.attempt == attempt) pane.status = text; };
        attempt.onAttached = connection -> {
            if (pane.attempt != attempt || !panes.containsKey(pane.id)) { connection.close().run(); return; }
            pane.connection = connection; pane.everAttached = true; pane.state = "RUNNING"; pane.status = "";
            registry.publish(new TerminalEvent.SessionStarted(pane.id));
            connection.exited().whenComplete((status, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (pane.connection != connection) return;
                connection.close().run();
                pane.connection = null; pane.state = "EXITED";
                pane.status = error == null ? "exit " + status : String.valueOf(error.getMessage());
                registry.publish(new TerminalEvent.SessionExited(pane.id, error == null ? OptionalInt.of(status) : OptionalInt.empty()));
                if (pane.request.closeOnExit()) closePane(pane.id);
            }));
        };
        attempt.onFailed = message -> { if (pane.attempt == attempt) { pane.state = "EXITED"; pane.status = message; } };
        registry.publish(new TerminalEvent.SessionConnecting(pane.id));
        pane.request.connector().accept(attempt);
    }

    String sessionState(UUID paneId) {
        Pane pane = panes.get(paneId);
        return pane == null ? "CLOSED|" : pane.state + "|" + pane.status;
    }

    void cancelSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.attempt == null) return;
        pane.attempt.cancel();
        // As in a real pane: with nothing ever shown there is nothing to return to.
        if (!pane.everAttached) closePane(paneId); else { pane.state = "EXITED"; pane.status = "Connection cancelled"; }
    }

    void reconnectSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane != null && pane.request != null && pane.state.equals("EXITED")) connect(pane);
    }

    void typeIntoSession(UUID paneId, String text) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return;
        try { pane.connection.input().write(text.getBytes(StandardCharsets.UTF_8)); pane.connection.input().flush(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    String sessionOutput(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return "";
        try {
            var stream = pane.connection.output();
            return new String(stream.readNBytes(stream.available()), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** What the pane's shell reports: a local directory when {@code hostOrEmpty} is empty, a remote one otherwise. */
    void reportDirectory(UUID paneId, String hostOrEmpty, String path) {
        Pane pane = panes.get(paneId);
        if (hostOrEmpty.isEmpty()) { pane.remote = null; pane.directory = Path.of(path); }
        else { pane.remote = new dev.jasper.app.terminals.RemoteLocation(hostOrEmpty, path); }
        registry.publish(new TerminalEvent.DirectoryChanged(paneId, hostOrEmpty.isEmpty() ? Optional.of(Path.of(path)) : Optional.empty(),
            Optional.ofNullable(pane.remote)));
    }
}
```

`WindowEntry` gains `main`, `WorkspaceHost` gains `attachBeside`, `TabRegistry.open` takes a placement, the window
manager opens an empty child window for `NEW_WINDOW`, and `HostedUi` implements the new moves and placements:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
M, T = "jasper-app/src/main/java/dev/jasper/app/", "jasper-app/src/test/java/dev/jasper/app/"
edit(M + "terminals/WindowEntry.java", [
    (""" * and {@code selectedTab} is the active group's selected tab. EDT only. {@code openTab} returns the new tab's pane, or
 * empty when the window is closing or the spec cannot be opened.
 */
public record WindowEntry(UUID id, Supplier<List<TabEntry>> tabs, Supplier<Optional<TabEntry>> selectedTab, BooleanSupplier active,
                          Runnable toFront, Function<OpenSpec, Optional<PaneEntry>> openTab) {""",
     """ * and {@code selectedTab} is the active group's selected tab; {@code main} says whether it is the main window rather
 * than a child window (workspace tabs spec 6). EDT only. {@code openTab} returns the new tab's pane, or empty when the
 * window is closing or the spec cannot be opened.
 */
public record WindowEntry(UUID id, Supplier<List<TabEntry>> tabs, Supplier<Optional<TabEntry>> selectedTab, BooleanSupplier active,
                          BooleanSupplier main, Runnable toFront, Function<OpenSpec, Optional<PaneEntry>> openTab) {""", 1)])
edit(M + "workspace/WindowTerminals.java", [(
    "            () -> Optional.ofNullable(owner.selectedTab()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);",
    "            () -> Optional.ofNullable(owner.selectedTab()).map(this::entry), owner::isActiveAndOpen, owner::isMain, toFront, this::openTab);", 1)])
edit(T + "terminals/TerminalRegistryTest.java", [(
    "        final WindowEntry entry = new WindowEntry(id, () -> List.copyOf(tabs), () -> Optional.ofNullable(selected), () -> true, () -> { },",
    "        final WindowEntry entry = new WindowEntry(id, () -> List.copyOf(tabs), () -> Optional.ofNullable(selected), () -> true, () -> true, () -> { },", 1)])
edit(M + "plugins/HostedTerminals.java", [(
    """        @Override public boolean isOpen() { requireUi("isOpen"); return registry.window(id).isPresent(); }
        @Override public void toFront() { requireUi("toFront"); registry.window(id).ifPresent(window -> window.toFront().run()); }""",
    """        @Override public boolean isOpen() { requireUi("isOpen"); return registry.window(id).isPresent(); }
        @Override public boolean isMain() { requireUi("isMain"); return registry.window(id).map(window -> window.main().getAsBoolean()).orElse(false); }
        @Override public void toFront() { requireUi("toFront"); registry.window(id).ifPresent(window -> window.toFront().run()); }""", 1)])
edit(M + "contributions/WorkspaceHost.java", [
    ("import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.TabId;\n",
     "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Side;\nimport dev.jasper.app.layout.TabId;\n", 1),
    (""" * @param groupOf the group that holds a tab of this window
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf) {
    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
    }
""", """ * @param groupOf the group that holds a tab of this window
 * @param attachBeside shows a tab the registry placed in this window in a new group on a side of one of its groups
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside) {
    /** Shows a tab in a new group beside a group of the window (workspace tabs spec 14's split placements; plan 5's drops). */
    @FunctionalInterface
    public interface Beside {
        /**
         * Shows {@code tab} in a new group on {@code side} of {@code group}.
         *
         * @param tab a tab the registry placed in this window
         * @param group a group of this window; null for its active group
         * @param side where the new group goes
         */
        void attach(Tab tab, GroupId group, Side side);
    }

    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
        Objects.requireNonNull(attachBeside, "attachBeside");
    }

    /**
     * A workspace that places every tab at the end of its active group, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     * @param groupOf the group that holds a tab of this window
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront,
                         Function<TabId, Optional<GroupId>> groupOf) {
        this(id, attach, detach, select, toFront, groupOf, (tab, group, side) -> attach.accept(tab));
    }
""", 1)])
edit(M + "workspace/WindowContent.java", [(
    """        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            tab -> workspace.model().groupOf(tab));""",
    """        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            tab -> workspace.model().groupOf(tab),
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace.model().activeGroup(), side));""", 1)])
R = M + "contributions/TabRegistry.java"
edit(R, [
    ("import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.TabId;\n",
     "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Side;\nimport dev.jasper.app.layout.TabId;\n", 1),
    ("""    /**
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
""", """    /** Where {@link #open} puts a new plugin's tab: the app's twin of the SDK's {@code TabPlacement} (workspace tabs spec 14). */
    public enum Placement {
        /** At the end of the window's active group. */
        ACTIVE_GROUP,
        /** In a new group to the right of the active group. */
        SPLIT_RIGHT,
        /** In a new group below the active group. */
        SPLIT_DOWN,
        /** In a new child window of its own, opened empty for it: the owner's window is not touched. */
        NEW_WINDOW
    }

    /**
     * How the registry reaches the windows (workspace tabs spec 6.4 and 6.5); the window manager gives it.
     *
     * @param main the main window's id, restored first when none is open; null while the main window is being composed,
     *     and a plugin's tab asked for then opens there once it is up
     * @param isMain whether a window is the main window
     * @param newWindow moves a tab into a new child window, or leaves it where it is
     * @param movedOut a tab left this window (a move that has reported itself, or a placement the window failed to show):
     *     the window manager closes a child window left empty now, never in the middle of the move
     * @param emptyWindow opens an empty child window for a tab placed in a new window and gives its id; null when none
     *     opens (the main window is being composed, or a test plays the windows), and the tab then opens where an
     *     {@link Placement#ACTIVE_GROUP} one would
     */
    public record Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut,
                          Supplier<UUID> emptyWindow) {
        /** Requires every function. */
        public Windows {
            Objects.requireNonNull(main, "main"); Objects.requireNonNull(isMain, "isMain"); Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(movedOut, "movedOut"); Objects.requireNonNull(emptyWindow, "emptyWindow");
        }
""", 1),
    ("""        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow) {
            this(main, isMain, newWindow, window -> { });
        }""", """        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow) {
            this(main, isMain, newWindow, window -> { }, () -> null);
        }""", 1),
    ("""    /**
     * Opens a plugin's view as a tab in {@code owner}'s window, or the main window when {@code owner} is null or not
     * registered, and selects it. A (plugin, key) already open is selected again, and attached to a window again when it
     * lost its own.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component) {
        requireEdt();""", """    /**
     * Opens a plugin's view as a tab at the end of the active group of {@code owner}'s window, or the main window when
     * {@code owner} is null or not registered, and selects it. A (plugin, key) already open is selected again, and
     * attached to a window again when it lost its own.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component) {
        return open(plugin, key, title, owner, component, Placement.ACTIVE_GROUP);
    }

    /**
     * As {@link #open(String, String, String, UUID, JComponent)}, the new tab where {@code placement} puts it: in that
     * window's active group or a new group beside it, or in a new child window of its own (workspace tabs spec 14). A
     * (plugin, key) already open is selected where it is.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component, Placement placement) {
        requireEdt();
        Objects.requireNonNull(placement, "placement");""", 1)])
span(R, """        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
""", """        select(tab);
        return tab;
    }
""", """        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        WorkspaceHost host = owner != null ? hosts.get(owner) : null;
        // A new child window of its own, opened empty for the tab: the owner's window is not touched (workspace tabs plan 4b).
        UUID child = placement == Placement.NEW_WINDOW && windows != null ? windows.emptyWindow().get() : null;
        if (child != null) host = hosts.get(child);
        if (host == null) host = main();
        tabs.add(tab);
        if (host == null) { later(tab); return tab; }
        tab.window(host.id());
        try {
            switch (placement) {
                case SPLIT_RIGHT -> host.attachBeside().attach(tab, null, Side.RIGHT);
                case SPLIT_DOWN -> host.attachBeside().attach(tab, null, Side.DOWN);
                case ACTIVE_GROUP, NEW_WINDOW -> host.attach().accept(tab);
            }
        }
        catch (RuntimeException | Error failure) {
            rollbackAttach(host, tab, failure);
            tabs.remove(tab);
            try { tab.dispose(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            // The child window opened for the tab is left empty: it closes.
            if (child != null && windows != null) windows.movedOut().accept(child);
            throw failure;
        }
        select(tab);
        return tab;
    }
""")
edit(M + "workspace/WindowManager.java", [
    ("""        model.tabs().windows(new TabRegistry.Windows(() -> openingMain ? null : ensureMain("plugin", null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException("Jasper is quitting")), this::isMain, this::moveToNewWindow, this::movedOut));""",
     """        model.tabs().windows(new TabRegistry.Windows(() -> openingMain ? null : ensureMain("plugin", null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException("Jasper is quitting")), this::isMain, this::moveToNewWindow, this::movedOut,
            this::emptyWindow));""", 1),
    ("""    void activated(JasperWindow window) {
""", """    /**
     * An empty child window for a plugin's tab placed in a new window (workspace tabs spec 14's
     * {@code TabPlacement.NEW_WINDOW}), after the main window; null while the main window is being composed.
     */
    private UUID emptyWindow() {
        if (openingMain) return null;
        if (ensureMain("plugin", null).isEmpty()) throw new IllegalStateException("Jasper is quitting");
        return openChild(true, null, null).map(JasperWindow::id).orElseThrow(() -> new IllegalStateException("Jasper is quitting"));
    }

    void activated(JasperWindow window) {
""", 1)])
H = M + "plugins/HostedUi.java"
edit(H, [
    ("""        public void move(boolean separate) {
            requireUi("move content");
            if (separate) entry.moveToNewWindow(); else entry.moveToMainWindow();
        }
        public boolean isSeparate() { requireUi("content host"); return !entry.inMainWindow(); }
""", """        public void moveToNewWindow() { requireUi("move content"); entry.moveToNewWindow(); }
        public void moveToMainWindow() { requireUi("move content"); entry.moveToMainWindow(); }
        public WindowHandle window() {
            requireUi("content window");
            // A tab no window could take back (its move failed everywhere) names no window.
            return terminals.windowHandle(entry.window() != null ? entry.window() : new java.util.UUID(0, 0));
        }
""", 1),
    ("""            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content) {
                guard("openTab");
                var view = kit.requireOwned(content);
                var existing = ownedContent.values().stream().filter(c -> c.entry.key().equals(spec.key())).findFirst();
                if(existing.isPresent()) return openComponent(spec, owner, existing.get().entry.component());""",
     """            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                guard("openTab");
                java.util.Objects.requireNonNull(placement, "placement");
                var view = kit.requireOwned(content);
                var existing = ownedContent.values().stream().filter(c -> c.entry.key().equals(spec.key())).findFirst();
                if(existing.isPresent()) return openComponent(spec, owner, existing.get().entry.component(), placement);""", 1),
    ("""                    var tab = openComponent(spec, owner, view.component());
                    tab.onClosed(view::close);""", """                    var tab = openComponent(spec, owner, view.component(), placement);
                    tab.onClosed(view::close);""", 1),
    ("""            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component) {""",
     """            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component,
                    dev.jasper.sdk.ui.TabPlacement placement) {""", 1),
    ("""                var entry = model.tabs().open(pluginId, spec.key(), spec.title(), window, component);""",
     """                var entry = model.tabs().open(pluginId, spec.key(), spec.title(), window, component, switch (placement) {
                    case ACTIVE_GROUP -> dev.jasper.app.contributions.TabRegistry.Placement.ACTIVE_GROUP;
                    case SPLIT_RIGHT -> dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT;
                    case SPLIT_DOWN -> dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_DOWN;
                    case NEW_WINDOW -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_WINDOW;
                });""", 1)])
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.terminals.*' --continue`
Expected: PASS, the three new contract tests on `FakeContractTest` and `AppContractTest`. Then `./gradlew check
--continue`: BUILD SUCCESSFUL (every plugin's tests use the new moves and stand-ins).

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -F - <<'EOF'
Workspace tabs: the main window, child windows and placements in the SDK

WindowHandle.isMain(), TabPlacement for Windows.openTab and the content
tab's moveToNewWindow, moveToMainWindow and window() replace move(boolean)
and isSeparate(). The testkit's fakes and the app's contract fixture keep
tabs in groups and the main window open when its last tab leaves.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The Vault manager and the sample's About as content tabs

While `Windows.create` still exists, the two plugins that call it move to content tabs, so Task 4 removes an API
nothing calls.

**Files:**
- Rename and replace: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java` →
  `VaultManagerTab.java`; rename `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java` →
  `VaultManagerTabTest.java`
- Modify: the Vault's `VaultPlugin.java`, `ui/ManagerPanel.java`, `plugin.toml` and tests (`VaultPluginTest`,
  `ManagerPanelTest`, `CompositionParityTest`, `ThemeMigrationEvidenceTest`, `ClassicManagerTest`, `ClassicEditorsTest`,
  `VaultCompositionTest`); Remote's `AnchoredImportIntegrationTest`; the sample's `SamplePlugin.java`, `plugin.toml`,
  `SamplePluginTest.java`; `docs/plugin-authoring.md` (the two sample snippets); the testkit's `FakeContent.java`,
  `FakeUi.java`, `FakePluginHost.java`
- Test (application): `VaultNativeCompositionTest` (whole file), `StagedVaultHost`, `KitViewTestAccess`, the four panel
  fixtures, `StagedAnchoredPickerTest`, `VaultEnterBaselineTest`, `VaultRenderEvidenceTest`, `ClassicVaultRenderTest`
  (re-pinned in Step 6), `windows/VaultSheetRenderTest`, `ManagedVaultLoadingTest`, `SampleSurfacesEvidenceTest`,
  `BundledSamplePluginTest`
- Ledger: two renamed rows

**Interfaces:**
- Consumes: Task 2's `Windows.openTab`, `ContentTab`.
- Produces: `public final class VaultManagerTab` (`KEY = "manager"`, `show(WindowHandle, Optional<UUID>)`, `isOpen()`,
  `changed()`, `showUnlock`, `dialog`, `add`, `showGrants`, `changePassword`); `ManagerPanel` without Done, `OKAY`,
  `CLOSE` or its `onClose` argument (Rulings 20); `FakePluginHost.contentTab(String pluginId, String title)` and
  `contentView(String pluginId, String title)`; `StagedVaultHost.manager()`, `managerOrNull()`, `awaitManager()`,
  `closeManager()`, `requestCloseManager()`.

- [ ] **Step 1: Write the failing tests**

The application's staged Vault opens its manager as a tab of the main window's workspace, its render and baseline
tests read the tab's component as the host mounts it (no Done, no dialog band), the Add Login and Add SSH Key sheets
render over a stand-in Jasper window, and the sample's evidence test finds its About tab there:

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/plugins/VaultNativeCompositionTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/** Real staged plugin through the manager's tab, its action row and EntryPresenter. */
class VaultNativeCompositionTest {
    @TempDir Path directory;
    /** Workspace tabs plan 4b: the manager is a tab, so the Lock and Add actions its window's title bar held are its first row. */
    @Test void theActionRowOpensEditorsAttachedToTheWindowAndLocks() throws Exception {
        var previousLaf = AppContractTest.onEdtValue(UIManager::getLookAndFeel);
        onEdt(() -> assertThat(dev.jasper.app.appearance.ThemeTestSupport.install(true)).isTrue());
        try (var vault = new StagedVaultHost(directory, true)) {
            vault.createVault("synthetic-pass");var manager = vault.manager();
            onEdt(() -> {
                assertThat(named(manager, "lock")).isInstanceOf(AbstractButton.class);
                var menu = ((JComponent) named(manager, "add")).getComponentPopupMenu();
                var nativeDelegate = menu.getUI(); var invoker = menu.getInvoker();
                var selection = MenuSelectionManager.defaultManager().getSelectedPath();
                var add = (AbstractButton) named(manager, "add.main");
                boolean selected = add.isSelected();
                assertThat(menu.isVisible()).isFalse();
                assertThat(nativeDelegate).isInstanceOf(com.formdev.flatlaf.ui.FlatPopupMenuUI.class);
                try {
                    // Keep the real Add action and popup lifecycle; only native screen discovery
                    // in FlatLaf's delegate is outside this headless integration boundary.
                    menu.setUI(new javax.swing.plaf.basic.BasicPopupMenuUI());
                    for (String label : java.util.List.of("Login", "SSH Key", "Secure Note", "Generate SSH Key...")) {
                        assertThat(add.isEnabled()).isTrue(); assertThat(add.isVisible()).isTrue();
                        add.doClick(0);
                        assertThat(menu.isVisible()).as("actual Add opens its menu").isTrue();
                        java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton b && b.getText().equals(label))
                            .map(AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
                        assertThat(menu.isVisible()).as("selection dismisses the real popup").isFalse();
                        var editor = vault.auxiliary.open().stream().filter(s -> !s.closed()).findFirst().orElseThrow();
                        var form = (dev.jasper.ui.model.Nodes.Form) kitView(editor).spec().root();
                        assertThat(form.title()).isEqualTo(editor.title());
                        assertThat(form.kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.SHEET);
                        assertThat(editor.attachedSheet()).isTrue();
                        assertThat(editor.ownerWindow()).as("the tab's window owns the sheet").contains(vault.window);
                        editor.close();
                    }
                } finally {
                    menu.setVisible(false); menu.setUI(nativeDelegate); menu.setInvoker(invoker); add.setSelected(selected);
                    MenuSelectionManager.defaultManager().setSelectedPath(selection);
                }
                ((AbstractButton) named(manager, "lock")).doClick(0);
            });
            await(() -> node(manager, "locked") != null, "lock updates manager body");
            onEdt(() -> assertThat(named(manager, "lock").isEnabled()).isFalse());
            assertThat(vault.problems).isEmpty();
        } finally {
            onEdt(() -> {
                try { UIManager.setLookAndFeel(previousLaf); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            });
        }
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex, span
T = "jasper-app/src/test/java/dev/jasper/app/"
H = T + "plugins/StagedVaultHost.java"
edit(H, [
    ("""    /** The staged Enterprise plugin's loader in Enterprise mode (plan 10b), else null. */
    final PluginClassLoader enterpriseLoader;
""", """    /** The staged Enterprise plugin's loader in Enterprise mode (plan 10b), else null. */
    final PluginClassLoader enterpriseLoader;
    /** The main window's workspace: the Vault's manager opens there as a tab (workspace tabs spec 1 decision 2). */
    final JComponent workspace;
""", 1),
    ("""        terminals = onEdtValue(TerminalFixture::new);
        host = AppContractTest.host(data, Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary, terminals.registry,""",
     """        terminals = onEdtValue(TerminalFixture::new);
        workspace = onEdtValue(() -> new javax.swing.JPanel(new java.awt.BorderLayout()));
        host = AppContractTest.host(data, Duration.ofMillis(200), contributions, new AtomicBoolean(dark), auxiliary, terminals.registry,""", 1),
    ("""            opened.set(terminals.addWindow()); terminals.activateWindow(opened.get());""",
     """            opened.set(terminals.addMainWindow()); terminals.activateWindow(opened.get());
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(opened.get(),
                tab -> workspace.add(tab.component()), tab -> workspace.remove(tab.component()), tab -> tab.active(true), () -> { }));""", 1),
    ("""    AuxiliarySurface awaitSurface(String title) throws InterruptedException {
        await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return surface(title);
    }
""", """    AuxiliarySurface awaitSurface(String title) throws InterruptedException {
        await(() -> auxiliary.open().stream().anyMatch(s -> s.title().equals(title) && !s.closed()), "surface " + title);
        return surface(title);
    }

    /** The open manager, the Vault's Credential Vault tab. UI thread. */
    private Optional<dev.jasper.app.contributions.Tab> managerTab() {
        return contributions.tabs().hosted().stream().filter(tab -> tab.title().equals("Credential Vault") && !tab.closed()).findFirst();
    }

    /** The manager tab's component, its kit view, while it is open; else null. */
    JComponent managerOrNull() { return onEdtValue(() -> managerTab().map(dev.jasper.app.contributions.Tab::component).orElse(null)); }

    JComponent manager() {
        var found = managerOrNull();
        assertThat(found).as("open manager tab").isNotNull();
        return found;
    }

    JComponent awaitManager() throws InterruptedException {
        await(() -> managerOrNull() != null, "the manager tab");
        return manager();
    }

    /** Closes the manager tab at once, as stopping the plugin does. */
    void closeManager() { onEdt(() -> managerTab().ifPresent(dev.jasper.app.contributions.Tab::close)); }

    /** Closes the manager tab as Close Tab does, through its plugin's guard. */
    void requestCloseManager() { onEdt(() -> managerTab().ifPresent(dev.jasper.app.contributions.Tab::requestClose)); }
""", 1),
    ("""            click(create, "submit");
        });
        awaitSurface("Credential Vault");
    }""", """            click(create, "submit");
        });
        awaitManager();
    }""", 1),
    ("""        var managerWindowField = plugin.getClass().getDeclaredField("managerWindow"); managerWindowField.setAccessible(true);
        onEdt(() -> {
            try {
                Object windowOwner = managerWindowField.get(plugin);
                windowOwner.getClass().getMethod("show",dev.jasper.sdk.terminal.WindowHandle.class,Optional.class)
                    .invoke(windowOwner,consumer.get().terminals().window(window).orElseThrow(),Optional.of(selected));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            var rows = (dev.jasper.ui.model.Nodes.ItemList)node(surface("Credential Vault"),"rows");""",
     """        var managerTabField = plugin.getClass().getDeclaredField("managerTab"); managerTabField.setAccessible(true);
        onEdt(() -> {
            try {
                Object managerTab = managerTabField.get(plugin);
                managerTab.getClass().getMethod("show",dev.jasper.sdk.terminal.WindowHandle.class,Optional.class)
                    .invoke(managerTab,consumer.get().terminals().window(window).orElseThrow(),Optional.of(selected));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            var rows = (dev.jasper.ui.model.Nodes.ItemList)node(manager(),"rows");""", 1),
    ("""                Object window = field(plugin, "managerWindow"), panel = field(window, "panel");""",
     """                Object managerTab = field(plugin, "managerTab"), panel = field(managerTab, "panel");""", 1),
    ("""    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }
""", """    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }

    /** As the surface forms above, inside a tab's component, such as the manager's. */
    static Component require(Container root, String name) {
        var found = named(root, name);
        assertThat(found).as("component %s", name).isNotNull();
        return found;
    }

    static void click(Container root, String name) { ((AbstractButton) require(root, name)).doClick(0); }

    static void type(Container root, String name, String text) { ((JTextComponent) require(root, name)).setText(text); }
""", 1),
    ("""    static dev.jasper.ui.swing.KitView kitView(Container root) {
        for (var child : root.getComponents()) {""", """    static dev.jasper.ui.swing.KitView kitView(Container root) {
        // A tab's component is its kit view.
        if (root instanceof dev.jasper.ui.swing.KitView view) return view;
        for (var child : root.getComponents()) {""", 1),
    ("""    /** A node of the surface's live descriptor, through the containers Vault views use. */
    static dev.jasper.ui.model.Nodes.Node node(AuxiliarySurface surface, String id) {
        return find(kitView(surface).spec().root(), id);
    }
""", """    /** A node of the surface's live descriptor, through the containers Vault views use. */
    static dev.jasper.ui.model.Nodes.Node node(AuxiliarySurface surface, String id) {
        return find(kitView(surface).spec().root(), id);
    }

    /** A node of the live descriptor of the kit view in {@code root}, such as the manager tab's. */
    static dev.jasper.ui.model.Nodes.Node node(Container root, String id) {
        return find(kitView(root).spec().root(), id);
    }
""", 1)])
P = T + "plugins/"
edit(P + "KitViewTestAccess.java", [
    ("""    StagedVaultHost vault() { return vault; }
    public AuxiliarySurface surface() { return surface; }
""", """    StagedVaultHost vault() { return vault; }
    public AuxiliarySurface surface() { return surface; }
    /** The manager tab's component for {@code vault-manager}, which is a tab and no surface; else null. */
    private javax.swing.JComponent manager;
    public javax.swing.JComponent manager() { return manager; }
""", 1),
    ("""                onEdt(() -> vault.surface("Credential Vault").close());""", """                vault.closeManager();""", 2),
    ("""                var manager = vault.awaitSurface("Credential Vault");
                if (id.equals("vault-manager")) { surface = manager; return; }
                onEdt(() -> {
                    var menu = ((javax.swing.JComponent)StagedVaultHost.named(manager.titleActions(), "add")).getComponentPopupMenu();""",
     """                var shown = vault.awaitManager();
                if (id.equals("vault-manager")) { manager = shown; surface = null; return; }
                onEdt(() -> {
                    var menu = ((javax.swing.JComponent)StagedVaultHost.named(shown, "add")).getComponentPopupMenu();""", 1),
    ("""                onEdt(() -> vault.surface("Credential Vault").requestClose());""", """                vault.requestCloseManager();""", 1)])
for name in ["PanelSurfaceFixture.java", "SessionsSurfaceFixture.java", "TunnelSurfaceFixture.java", "WorkspacePanelsFixture.java"]:
    edit(P + name, [("""        onEdt(() -> vault.surface("Credential Vault").requestClose());""", """        vault.requestCloseManager();""", 1)])
edit(P + "StagedAnchoredPickerTest.java", [("""onEdt(()->vault.surface("Credential Vault").close());""", """vault.closeManager();""", 1)])
E = P + "VaultEnterBaselineTest.java"
edit(E, [
    ("""            var manager = vault.surface("Credential Vault");
            vault.invoke("dev.jasper.vault.lock");""", """            var manager = vault.manager();
            vault.invoke("dev.jasper.vault.lock");""", 1),
    ("""            await(() -> named(manager.holder(), "unlock.password.input") != null, "inline unlock form");""",
     """            await(() -> named(manager, "unlock.password.input") != null, "inline unlock form");""", 1),
    ("""            await(() -> vault.surfaceOrNull("Credential Vault") == null, "Enter on Okay closes the unlocked manager");""",
     """            await(() -> vault.managerOrNull() == null, "Enter on Okay closes the unlocked manager");""", 1),
    ("""            onEdt(() -> vault.surface("Credential Vault").requestClose());""", """            vault.requestCloseManager();""", 1)])
R = P + "VaultRenderEvidenceTest.java"
edit(R, [
    ("""                    var manager=vault.surface("Credential Vault");
                    onEdt(()->menu(manager.titleActions(),"add","Secure Note"));""",
     """                    var manager=vault.manager();
                    onEdt(()->menu(manager,"add","Secure Note"));""", 1),
    ("""getDeclaredField("managerWindow");field.setAccessible(true);Object window=field.get(vault.plugin);reflect(window.getClass(),window,"show",""",
     """getDeclaredField("managerTab");field.setAccessible(true);Object window=field.get(vault.plugin);reflect(window.getClass(),window,"show",""", 1),
    ("""    private static void natural(String theme,String state,AuxiliarySurface surface) {
        onEdt(()->{
            var holder=surface.holder();var size=holder.getPreferredSize();
            boolean manager=surface.title().equals("Credential Vault");int width=manager?900:Math.max(1,size.width),height=manager?555:Math.max(1,size.height);
            ThemeMigrationCapture.save("vault-companions",theme,state,holder,width,height);
        });
    }
""", """    private static void natural(String theme,String state,AuxiliarySurface surface) {
        onEdt(()->{
            var holder=surface.holder();var size=holder.getPreferredSize();
            ThemeMigrationCapture.save("vault-companions",theme,state,holder,Math.max(1,size.width),Math.max(1,size.height));
        });
    }
    /** The manager, a tab, at the size its window had. */
    private static void natural(String theme,String state,JComponent manager) {
        onEdt(()->ThemeMigrationCapture.save("vault-companions",theme,state,manager,900,555));
    }
""", 1),
    ("""            var manager = vault.awaitSurface("Credential Vault");
            save(theme, "manager-empty", manager, 900, 580);""", """            var manager = vault.awaitManager();
            save(theme, "manager-empty", manager, 900, 580);""", 1),
    ("""            onEdt(() -> manager.requestClose());
            vault.invoke("dev.jasper.vault.open");""", """            vault.requestCloseManager();
            vault.invoke("dev.jasper.vault.open");""", 1),
    ("""    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {""",
     """    /** The manager, a tab: its component. */
    static void save(String theme, String state, JComponent manager, int width, int height) throws Exception {
        onEdt(() -> ThemeMigrationCapture.save(FAMILY, theme, state, manager, UIScale.scale(width), UIScale.scale(height)));
        assertThat(Files.size(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png"))).isPositive();
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {""", 1)])
C = P + "ClassicVaultRenderTest.java"
edit(C, [
    ("""    /**
     * The surface's kit view and everything below it in its holder,""",
     """    /** As below for the manager, a tab: its kit view, with the band its window's frame gave it. */
    static BufferedImage render(javax.swing.JComponent manager, int width, int height) {
        return onEdtValue(() -> {
            StagedVaultHost.kitView(manager).dialogBand(true);
            manager.setSize(width, height);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(manager);
            var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(2, 2); manager.printAll(graphics); } finally { graphics.dispose(); }
            Color under = EnterpriseLook.resolved(false).workspace().semantic().color(SemanticColor.SURFACE_DIALOG);
            return MockMatch.onto(image, under);
        });
    }

    /**
     * The surface's kit view and everything below it in its holder,""", 1),
    ("""render(vault.surface("Credential Vault"), NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());""",
     """render(vault.manager(), NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());""", 1),
    ("""render(vault.surface("Credential Vault"), SELECTED.logicalWidth(), SELECTED.logicalHeight());""",
     """render(vault.manager(), SELECTED.logicalWidth(), SELECTED.logicalHeight());""", 1),
    ("""                var manager = vault.surface("Credential Vault");
                var none = render(manager, NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());""",
     """                var manager = vault.manager();
                var none = render(manager, NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());""", 1),
    ("""                var manager = vault.surface("Credential Vault");
                onEdt(manager::requestClose);""", """                vault.requestCloseManager();""", 1)])
W = T + "windows/VaultSheetRenderTest.java"
# The manager window, its title menu and its chrome are gone: its sheets attach to the tab's Jasper window.
span(W, """    @Test void mountedTitleMenuAndAttachedFormsRenderAcrossThemesFontsAndShortOwners() throws Exception {""",
     """    /** Unlock and Create are modal overlays inside the asking window: centred over its dimmed content. */""",
     """    /** Unlock and Create are modal overlays inside the asking window: centred over its dimmed content. */""")
span(W, """    private static void render(AuxiliarySurface surface,String state,String id) {""",
     """    private static void capture(String state,String id,JComponent root,JComponent child,Rectangle at,String metadata) {""",
     """    private static void capture(String state,String id,JComponent root,JComponent child,Rectangle at,String metadata) {""")
edit(W, [("""    @Test void populatedManagerKeepsFullActionsReachableAtNarrowAndEnlargedWidths() throws Exception {
        var controller=SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for(boolean enlarged:new boolean[]{false,true}) {
                SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, enlarged?new UiFontConfig("system",20f):UiFontConfig.defaults());return null;});
                try(var fixture=new KitViewTestAccess(home.resolve("narrow-"+enlarged),"vault-manager",SurfaceFixtures.edt(controller::current))) {
                    SurfaceFixtures.edt(()->{
                        var surface=fixture.surface();var root=new JRootPane();
                        try(var chrome=NativeShells.installChrome(root,surface,true)) {
                            var edit=(JButton)find(surface.holder(),"edit");var delete=(JButton)find(surface.holder(),"delete");
                            var search=(JTextField)find(surface.holder(),"search.input");search.setText(" ");var document=search.getDocument();
                            Rectangle originalHeading=null;
                            for(int width:new int[]{com.formdev.flatlaf.util.UIScale.scale(900),com.formdev.flatlaf.util.UIScale.scale(540),com.formdev.flatlaf.util.UIScale.scale(400),400,com.formdev.flatlaf.util.UIScale.scale(900)}) {
                                int logical=com.formdev.flatlaf.util.UIScale.unscale(width),height=com.formdev.flatlaf.util.UIScale.scale(555);
                                root.setSize(width,height+chrome.bar().getPreferredSize().height);layout(root);
                                assertThat(find(surface.holder(),"search.input")).isSameAs(search);assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                                if(logical==900) {
                                    var bounds=find(surface.holder(),"detail.heading").getBounds();
                                    if(originalHeading==null)originalHeading=new Rectangle(bounds);else assertThat(bounds).isEqualTo(originalHeading);
                                }
                                for(var button:List.of(delete,edit)) {
                                    assertThat(find(surface.holder(),button.getName())).isSameAs(button);
                                    assertThat(button.getWidth()).as("%s at %s enlarged=%s",button.getName(),logical,enlarged).isGreaterThanOrEqualTo(button.getPreferredSize().width);
                                    assertThat(button.isFocusable()).isTrue();
                                    assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("pressed SPACE"))).isNotNull();
                                    var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                    for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,button));
                                    layout(root);assertThat(button.getVisibleRect()).isEqualTo(new Rectangle(0,0,button.getWidth(),button.getHeight()));
                                }
                                for(String rowId:List.of("detail.username","detail.password")) {
                                    var row=find(surface.holder(),rowId);var title=find(row,rowId+".title");
                                    var value=rowId.endsWith("password")?ending(row,".value"):find(row,rowId+".description");
                                    assertThat(value.getWidth()).as("visible detail value %s at %s",rowId,width).isGreaterThan(value.getFontMetrics(value.getFont()).charWidth('W'));
                                    var valueBounds=SwingUtilities.convertRectangle(value.getParent(),value.getBounds(),row);
                                    var titleInkBounds=new Rectangle(title.getBounds());titleInkBounds.width=Math.min(titleInkBounds.width,title.getPreferredSize().width);
                                    assertThat(titleInkBounds.intersects(valueBounds)).as("label/value separation %s at %s",rowId,width).isFalse();
                                    for(var action:List.of(ending(row,".copy"),rowId.endsWith("password")?ending(row,".reveal"):ending(row,".copy"))) {
                                        assertThat(action.getWidth()).as("full detail action %s at %s",action.getName(),width).isGreaterThanOrEqualTo(action.getPreferredSize().width);
                                        var actionBounds=SwingUtilities.convertRectangle(action.getParent(),action.getBounds(),row);
                                        assertThat(new Rectangle(0,0,row.getWidth(),row.getHeight()).contains(actionBounds)).isTrue();
                                        assertThat(actionBounds.intersects(titleInkBounds)).as("label/action separation %s at %s",action.getName(),width).isFalse();assertThat(actionBounds.intersects(valueBounds)).as("value/action separation %s at %s",action.getName(),width).isFalse();
                                        var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                        for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,action));
                                        layout(root);assertThat(action.getVisibleRect()).isEqualTo(new Rectangle(0,0,action.getWidth(),action.getHeight()));
                                    }
                                }
                                capture("fix1-"+(enlarged?"enlarged":"normal"),"manager-width-"+width,root,null,null,"actual populated manager; Delete/Edit full bounds, focusability, keyboard activation binding and focus-owner scroll reveal asserted; no native traversal peer");
                            }
                            controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());fixture.theme(controller.current());
                            root.setSize(900,555+chrome.bar().getPreferredSize().height);layout(root);
                            assertThat(find(surface.holder(),"edit")).isSameAs(edit);assertThat(find(surface.holder(),"delete")).isSameAs(delete);
                            assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                            assertThat(edit.getWidth()).isEqualTo(edit.getPreferredSize().width);
                            assertThat(delete.getWidth()).isEqualTo(delete.getPreferredSize().width);
                            assertThat(find(surface.holder(),"detail.heading").getHeight()).isEqualTo(44);
                        }
                        return null;
                    });
                }
            }
        }finally{SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());controller.close();return null;});}
    }
""", """    @Test void populatedManagerKeepsFullActionsReachableAtNarrowAndEnlargedWidths() throws Exception {
        var controller=SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for(boolean enlarged:new boolean[]{false,true}) {
                SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, enlarged?new UiFontConfig("system",20f):UiFontConfig.defaults());return null;});
                try(var fixture=new KitViewTestAccess(home.resolve("narrow-"+enlarged),"vault-manager",SurfaceFixtures.edt(controller::current))) {
                    SurfaceFixtures.edt(()->{
                        // The manager is a tab: its component fills the window's workspace, with no title bar of its own.
                        var manager=fixture.manager();var root=new JRootPane();
                        root.setContentPane(new JPanel(new BorderLayout()));root.getContentPane().add(manager);
                        {
                            var edit=(JButton)find(manager,"edit");var delete=(JButton)find(manager,"delete");
                            var search=(JTextField)find(manager,"search.input");search.setText(" ");var document=search.getDocument();
                            Rectangle originalHeading=null;
                            for(int width:new int[]{com.formdev.flatlaf.util.UIScale.scale(900),com.formdev.flatlaf.util.UIScale.scale(540),com.formdev.flatlaf.util.UIScale.scale(400),400,com.formdev.flatlaf.util.UIScale.scale(900)}) {
                                int logical=com.formdev.flatlaf.util.UIScale.unscale(width),height=com.formdev.flatlaf.util.UIScale.scale(555);
                                root.setSize(width,height);layout(root);
                                assertThat(find(manager,"search.input")).isSameAs(search);assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                                if(logical==900) {
                                    var bounds=find(manager,"detail.heading").getBounds();
                                    if(originalHeading==null)originalHeading=new Rectangle(bounds);else assertThat(bounds).isEqualTo(originalHeading);
                                }
                                for(var button:List.of(delete,edit)) {
                                    assertThat(find(manager,button.getName())).isSameAs(button);
                                    assertThat(button.getWidth()).as("%s at %s enlarged=%s",button.getName(),logical,enlarged).isGreaterThanOrEqualTo(button.getPreferredSize().width);
                                    assertThat(button.isFocusable()).isTrue();
                                    assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("pressed SPACE"))).isNotNull();
                                    var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                    for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,button));
                                    layout(root);assertThat(button.getVisibleRect()).isEqualTo(new Rectangle(0,0,button.getWidth(),button.getHeight()));
                                }
                                for(String rowId:List.of("detail.username","detail.password")) {
                                    var row=find(manager,rowId);var title=find(row,rowId+".title");
                                    var value=rowId.endsWith("password")?ending(row,".value"):find(row,rowId+".description");
                                    assertThat(value.getWidth()).as("visible detail value %s at %s",rowId,width).isGreaterThan(value.getFontMetrics(value.getFont()).charWidth('W'));
                                    var valueBounds=SwingUtilities.convertRectangle(value.getParent(),value.getBounds(),row);
                                    var titleInkBounds=new Rectangle(title.getBounds());titleInkBounds.width=Math.min(titleInkBounds.width,title.getPreferredSize().width);
                                    assertThat(titleInkBounds.intersects(valueBounds)).as("label/value separation %s at %s",rowId,width).isFalse();
                                    for(var action:List.of(ending(row,".copy"),rowId.endsWith("password")?ending(row,".reveal"):ending(row,".copy"))) {
                                        assertThat(action.getWidth()).as("full detail action %s at %s",action.getName(),width).isGreaterThanOrEqualTo(action.getPreferredSize().width);
                                        var actionBounds=SwingUtilities.convertRectangle(action.getParent(),action.getBounds(),row);
                                        assertThat(new Rectangle(0,0,row.getWidth(),row.getHeight()).contains(actionBounds)).isTrue();
                                        assertThat(actionBounds.intersects(titleInkBounds)).as("label/action separation %s at %s",action.getName(),width).isFalse();assertThat(actionBounds.intersects(valueBounds)).as("value/action separation %s at %s",action.getName(),width).isFalse();
                                        var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                        for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,action));
                                        layout(root);assertThat(action.getVisibleRect()).isEqualTo(new Rectangle(0,0,action.getWidth(),action.getHeight()));
                                    }
                                }
                                capture("fix1-"+(enlarged?"enlarged":"normal"),"manager-width-"+width,root,null,null,"actual populated manager; Delete/Edit full bounds, focusability, keyboard activation binding and focus-owner scroll reveal asserted; no native traversal peer");
                            }
                            controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());fixture.theme(controller.current());
                            root.setSize(900,555);layout(root);
                            assertThat(find(manager,"edit")).isSameAs(edit);assertThat(find(manager,"delete")).isSameAs(delete);
                            assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                            assertThat(edit.getWidth()).isEqualTo(edit.getPreferredSize().width);
                            assertThat(delete.getWidth()).isEqualTo(delete.getPreferredSize().width);
                            assertThat(find(manager,"detail.heading").getHeight()).isEqualTo(44);
                        }
                        return null;
                    });
                }
            }
        }finally{SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());controller.close();return null;});}
    }
""", 1)])
edit(P + "ManagedVaultLoadingTest.java", [('                var manager = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();',
                                          '                var manager = host.contentView("dev.jasper.vault", "Credential Vault").orElseThrow();', 1)])
# The sample's About is a content tab of the main window's workspace now.
SE = P + "SampleSurfacesEvidenceTest.java"
edit(SE, [
    (""" * Plan 3 Task 8: the real staged Sample plugin's panel, About window and details sheet through the plugin runtime with
 * the application's appearance wiring, at device scale 2 in dark and light: the panel body and the About window are the""",
     """ * Plan 3 Task 8: the real staged Sample plugin's panel, About tab and details sheet through the plugin runtime with
 * the application's appearance wiring, at device scale 2 in dark and light: the panel body and the About tab are the""", 1),
    ("    void samplePanelWindowAndSheetUseTheCanonicalComponents() throws Exception {",
     "    void samplePanelAboutTabAndSheetUseTheCanonicalComponents() throws Exception {", 1),
    ("""        var runtime = new AtomicReference<PluginRuntime>();
""", """        var runtime = new AtomicReference<PluginRuntime>();
        // The main window's workspace, where the About tab opens (workspace tabs plan 4b).
        var terminals = onEdtValue(TerminalFixture::new);
        var workspace = onEdtValue(() -> new javax.swing.JPanel(new java.awt.BorderLayout()));
        UUID main = onEdtValue(() -> {
            UUID id = terminals.addMainWindow();
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,
                tab -> workspace.add(tab.component()), tab -> workspace.remove(tab.component()), tab -> tab.active(true), () -> { }));
            return id;
        });
""", 1),
    ("                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, new dev.jasper.app.terminals.TerminalRegistry()));",
     "                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, terminals.registry));", 1),
    ("""                    contributions.action("dev.jasper.sample.about").orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
                });
                var about = await(windows, "About Sample");
                onEdt(() -> {
                    EditorEvidence.capture(theme, "sample-about", about.holder(), 360, 200, "sample.about", "sample.about.icon", "sample.about.details");
                    WorkspaceContentRouteTest.named(about.holder(), "sample.about.details", AbstractButton.class).doClick();
                });""", """                    contributions.action("dev.jasper.sample.about").orElseThrow().invoke(new Contributions.Invocation(main, Optional.empty()));
                });
                var about = onEdtValue(() -> contributions.tabs().hosted().stream()
                    .filter(tab -> tab.title().equals("About Sample") && !tab.closed()).findFirst().orElseThrow());
                onEdt(() -> {
                    EditorEvidence.capture(theme, "sample-about", about.component(), 360, 200, "sample.about", "sample.about.icon", "sample.about.details");
                    WorkspaceContentRouteTest.named(about.component(), "sample.about.details", AbstractButton.class).doClick();
                });""", 1),
    ("                    details.requestClose(); about.requestClose();", "                    details.requestClose(); about.close();", 1)])
# Rulings 20: the manager tab has no Done; its inline unlock is its one action.
span(E, " * manager's inline unlock is the window's default, so Enter on a footer button unlocks.\n",
     "            await(() -> vault.managerOrNull() == null, \"Enter on Okay closes the unlocked manager\");\n            assertThat(vault.problems).isEmpty();\n        }\n    }\n",
     """ * manager's inline unlock is its one action, so Enter in its field unlocks, and the manager, a tab, has no Done.
 */
class VaultEnterBaselineTest {
    static final PluginInfo CONSUMER = new PluginInfo("test.consumer", "Consumer", "0.1.0", Set.of());
    @TempDir Path root;

    @Test void enterInTheInlineUnlockUnlocksAndTheManagerTabStaysOpen() throws Exception {
        try (var vault = new StagedVaultHost(root.resolve("home"), true)) {
            vault.createVault("synthetic-enter-pass");
            var manager = vault.manager();
            vault.invoke("dev.jasper.vault.lock");
            onEdt(() -> click(manager, "unlock"));
            await(() -> named(manager, "unlock.password.input") != null, "inline unlock form");
            onEdt(() -> {
                type(manager, "unlock.password.input", "synthetic-enter-pass");
                enter(require(manager, "unlock.password.input"));
            });
            await(() -> node(manager, "search") != null, "Enter in the field pressed Unlock and the entries came back");
            assertThat(onEdtValue(() -> node(manager, "unlock.password"))).isNull();
            assertThat(onEdtValue(() -> node(manager, "okay"))).as("a tab has no Done").isNull();
            assertThat(vault.managerOrNull()).as("Enter leaves the manager open").isNotNull();
            assertThat(vault.problems).isEmpty();
        }
    }
""")
# The manager renders as production mounts it: the workspace route over its painted surface, with no band forced on it.
edit(C, [
    ("""    /** As below for the manager, a tab: its kit view, with the band its window's frame gave it. */
    static BufferedImage render(javax.swing.JComponent manager, int width, int height) {
        return onEdtValue(() -> {
            StagedVaultHost.kitView(manager).dialogBand(true);
            manager.setSize(width, height);""", """    /** As below for the manager, a tab: its kit view as the host mounts it, on the workspace route over its own surface. */
    static BufferedImage render(javax.swing.JComponent manager, int width, int height) {
        return onEdtValue(() -> {
            manager.setSize(width, height);""", 1),
    ("""                bands("the status row and Done", render, 1600, 1068, 1142, STATUS);""",
     """                bands("the status row", render, 1600, 1068, 1142, STATUS);""", 1),
    ("""                bands("the status row and Done", selected, 1600, 1068, 1142, STATUS);""",
     """                bands("the status row", selected, 1600, 1068, 1142, STATUS);""", 1)])
# Add Login and Add SSH Key attach to the Jasper window that holds the manager's tab: a stand-in window owns them here.
edit(W, [("""    /** Unlock and Create are modal overlays inside the asking window: centred over its dimmed content. */""",
"""    /**
     * Add Login and Add SSH Key attach as sheets to the Jasper window that holds the manager's tab, across themes, fonts
     * and a short window (workspace tabs plan 4b: a stand-in Jasper window owns them here, as the manager is a tab).
     */
    @Test void attachedFormsRenderAcrossThemesFontsAndShortOwners() throws Exception {
        var controller = SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for (String state : List.of("dark", "light", "enlarged", "short")) {
                SurfaceFixtures.edt(() -> {controller.configure(!state.equals("light"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, state.equals("enlarged") ? new UiFontConfig("system",20f) : UiFontConfig.defaults()); return null;});
                for (String id : List.of("vault-login", "vault-key")) {
                    try(var fixture = new KitViewTestAccess(home.resolve(state+"-"+id), id, SurfaceFixtures.edt(controller::current))) {
                        SurfaceFixtures.edt(() -> {sheet(fixture.surface(),state,id);return null;});
                    }
                }
            }
        } finally { SurfaceFixtures.edt(() -> {controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());controller.close();return null;}); }
    }
    /** The sheet over a stand-in Jasper window: a 38-pixel title row over a body {@code width} by {@code height} logical pixels. */
    private static void sheet(AuxiliarySurface surface,String state,String id) {
        assertThat(surface.attachedSheet()).as("%s attaches as a sheet", id).isTrue();
        int width = state.equals("enlarged") ? 1350 : 900;
        int height = state.equals("short") ? 45 : state.equals("enlarged") ? 833 : 555;
        var root = new JRootPane(); var content = new JPanel(new BorderLayout()); var title = new JPanel(); var window = new JPanel();
        title.setPreferredSize(new Dimension(width, 38)); content.add(title, BorderLayout.NORTH); content.add(window, BorderLayout.CENTER);
        root.setContentPane(content); root.setSize(width, height + 38); layout(root);
        var child = new JRootPane();
        try (var body = dev.jasper.app.platform.WindowBody.install(root, window, () -> new Rectangle(0, 0, window.getWidth(), window.getHeight()));
             var sheetChrome = NativeShells.installChrome(child, surface, true)) {
            assertThat(body.boundsInRoot().orElseThrow().height).isEqualTo(height);
            var bounds = new java.util.concurrent.atomic.AtomicReference<Rectangle>();
            try (var sheet = NativeShells.attachSheet(surface, body, new AttachedSheet.Target(() -> true, Point::new,
                    () -> new Rectangle(0, 0, width, Math.max(900, height + 100)), value -> {
                        bounds.set(value);
                        if (!child.getSize().equals(value.getSize())) { child.setSize(value.getSize()); layout(child); }
                    }))) {
                sheet.fitNow(); sheet.fitNow();
                assertThat(surface.holder().getSize()).isEqualTo(child.getSize());
                for (String action : List.of("cancel", "save")) {
                    var button = find(surface.holder(), action);
                    assertThat(button).as("actual footer action %s", action).isNotNull();
                    if (!state.equals("short"))
                        assertThat(new Rectangle(child.getSize()).contains(SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), child)))
                            .as("native final allocation contains %s", action).isTrue();
                }
                assertThat(sheet.fallback()).isEqualTo(state.equals("short"));
                if (id.equals("vault-login")) {
                    // The form segment height inside the form track inset.
                    int segmentHeight = com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_SEGMENT_HEIGHT));
                    assertThat(find(surface.holder(), "auth.option.password").getHeight())
                        .as("actual Login segment preserves its nonheader height in %s", state).isEqualTo(segmentHeight);
                    assertThat(find(surface.holder(), "auth.input").getHeight())
                        .isEqualTo(segmentHeight + 2 * com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_SEGMENT_PADDING)));
                }
                capture(state,id,root,child,bounds.get(),"body="+body.boundsInRoot().orElseThrow()+"\\nsheet="+bounds.get()+"\\nfallback="+sheet.fallback());
                capture(state,id+"-sheet",child,null,null,"same actual mounted attached sheet; no native shadow/peer");
            }
        }
    }

    /** Unlock and Create are modal overlays inside the asking window: centred over its dimmed content. */""", 1)])
# The manager departs from the 07 mocks where a tab differs from their window: no Done, and no dialog band under its
# status row. Step 6 re-pins what the render shows there.
edit(C, [
    (""" * Enterprise kit, Plex fonts and theme (Light: the 07 mocks have no Dark), against the 07 Credential Vault mocks. Images
 * go to jasper-app/build/enterprise-compositions/light/, paired in tools/visual/pairs.json.
 */""", """ * Enterprise kit, Plex fonts and theme (Light: the 07 mocks have no Dark), against the 07 Credential Vault mocks. Images
 * go to jasper-app/build/enterprise-compositions/light/, paired in tools/visual/pairs.json. The manager is a content tab
 * (workspace tabs spec 14), mounted as the host mounts one: it has no Done and no dialog band, which the mocks' window
 * shows, so its status row is pinned to the render, not to the mocks.
 */""", 1),
    ("""    static final String STATUS = \"""", """    /** The status row at device column 1600: the tab's own row, where the mocks' window shows Done over a dialog band. */
    static final String STATUS = \"""", 1),
    ("""     * {@code CLASSIC_FLAGS}, {@code hostTitled}, {@code dialogBand} and {@code dialogCard}); the manager window's view
     * takes the band its frame gives it ({@code NativeShells.frame}, spec 10.8).
     */""", """     * {@code CLASSIC_FLAGS}, {@code hostTitled}, {@code dialogBand} and {@code dialogCard}).
     */""", 1),
    ("""                content.hostTitled(true); content.dialogBand(true); content.dialogCard(true);
            } else content.dialogBand(true);""", """                content.hostTitled(true); content.dialogBand(true); content.dialogCard(true);
            }""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.VaultNativeCompositionTest' --tests 'dev.jasper.app.plugins.VaultEnterBaselineTest' --continue`
Expected: FAIL: the manager still opens as an auxiliary window, so no Credential Vault tab is hosted (the wait for
`the manager tab` times out, or `open manager tab` is null).

- [ ] **Step 3: The Vault's manager tab**

```bash
V=plugins/vault/src/main/java/dev/jasper/vault/ui; VT=plugins/vault/src/test/java/dev/jasper/vault/ui
git mv $V/VaultManagerWindow.java $V/VaultManagerTab.java
git mv $VT/VaultManagerWindowTest.java $VT/VaultManagerTabTest.java
```

Replace the whole of `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerTab.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.ManagedSshKey;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The manager, one content tab (workspace tabs spec 1 decision 2), and its modal editors, which the tab owns. The plugin
 * forwards state/save/config changes here.
 */
public final class VaultManagerTab implements AutoCloseable {
    /** The manager tab's key: opening it again selects the open one. */
    public static final String KEY = "manager";
    private final PluginContext context;
    private final LockManager lock;
    private final VaultManager manager;
    private final VaultService service;
    /** Presentation of the configured provider, auto-lock policy and actual vault path. */
    public record Status(String deviceSecret, String autoLock, String path) { }
    private final Supplier<Status> status;
    private final Runnable generate;
    private final EntryPresenter editors;
    private final Layout layout;
    private final CopyCards copies;
    private ContentTab tab;
    private WindowHandle owner;
    private UnlockPrompt unlockPrompt;
    ManagerPanel panel;
    PasswordPanel passwordPanel;

    public VaultManagerTab(PluginContext context, LockManager lock, VaultManager manager,
                           VaultService service, Supplier<Status> status, Runnable generate) {
        this(context, lock, manager, service, status, generate, Layout.ROWS);
    }

    /** The manager in {@code layout}'s composition (Enterprise presentation spec 14.8). */
    public VaultManagerTab(PluginContext context, LockManager lock, VaultManager manager,
                           VaultService service, Supplier<Status> status, Runnable generate, Layout layout) {
        this.layout = layout;
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;
        this.status = status; this.generate = generate;this.editors=new EntryPresenter(context, layout);
        this.copies = CopyCards.declare(context);
    }
    public boolean isOpen() { return tab != null; }
    /** The plugin's kit, for the forms this tab owns. */
    public UiKit kit() { return context.kit(); }
    /** Opens the manager as a tab in {@code owner}'s window, or selects the open one, with {@code selection} selected. */
    public void show(WindowHandle owner, Optional<UUID> selection) {
        this.owner = owner;
        if (tab == null) {
            var shown = new ManagerPanel(context.kit(), layout, manager::entry, this::add, this::edit, this::delete, this::copyPublic,
                grant -> report("Could not revoke the grant", manager.revoke(grant)), generate, lock::lock,
                () -> service.requestUnlock(this.owner));
            shown.copies(copies);
            panel = shown;
            tab = context.windows().openTab(new ContentSpec(KEY, "Credential Vault"), owner, shown.view());
            tab.onClosed(() -> {
                // The tab has already disposed the view and erased its storage; nothing touches it from here.
                tab = null; shown.close(); closeDialogs();
                if (unlockPrompt != null) unlockPrompt.cancel();
                passwordPanel = null; unlockPrompt = null;
            });
        }
        changed(); selection.ifPresent(panel::select); tab.select();
    }
    /** Refresh existing content; never opens the manager in response to an unrelated consumer unlock. */
    public void changed() {
        if (tab == null) return;
        boolean unlocked = lock.state() == LockState.UNLOCKED;
        if (!unlocked) closeDialogs();
        panel.refresh(manager.rows(), manager.grants(), status.get(), unlocked);
        if (unlockPrompt == null) panel.passiveUnlock();
    }
    /** Routes the service's existing shared prompt into the manager, with no second unlock attempt. */
    public boolean showUnlock(UnlockPrompt prompt) {
        if (tab == null) return false;
        unlockPrompt = prompt; passwordPanel = panel.showUnlock(prompt);
        PasswordPanel shown = passwordPanel;
        boolean[] active = {true};
        prompt.onPresentationRetired(() -> {
            active[0] = false;
            if (unlockPrompt == prompt && passwordPanel == shown) {
                unlockPrompt = null; passwordPanel = null;
                panel.passiveUnlock();
            } else shown.close();
        });
        prompt.onDismiss(() -> {
            if (!active[0]) return;
            shown.clear();
            if (unlockPrompt == prompt && passwordPanel == shown) { unlockPrompt = null; passwordPanel = null; changed(); }
        });
        tab.select(); return true;
    }
    /** Every form, including the generator, gets the same modal ownership and close-time wipe. */
    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) { dialog(title, Nodes.Hue.NONE, factory); }
    /** As {@link #dialog(String, Function)}, the sheet's swatch in {@code hue} under the classic layout (plan 10b Rulings 5). */
    public void dialog(String title, Nodes.Hue hue, Function<Runnable, ? extends EditorForm> factory) {
        if (tab == null || lock.state() != LockState.UNLOCKED) return;
        editors.show(tab,title,hue,factory,()->{});
    }
    /** Opens the sheet for a new entry of the type over the open manager; does nothing when it is closed or locked. */
    public void add(VaultManager.Type type) {
        UiKit kit = context.kit();
        switch (type) {
            case LOGIN -> dialog("Add Login", Nodes.Hue.BLUE, close -> EntryEditor.login(kit, null, manager::saveAccount, close));
            case SSH_KEY -> dialog("Add SSH Key", Nodes.Hue.GREEN, close -> EntryEditor.key(kit, null, manager::importKey, close));
            case NOTE -> dialog("Add Secure Note", Nodes.Hue.AMBER, close -> EntryEditor.note(kit, null, manager::saveNote, close));
        }
    }
    private void edit(UUID id) {
        UiKit kit = context.kit();
        manager.entry(id).ifPresent(value -> {
            switch (value) {
                case Account a -> dialog("Edit Login", Nodes.Hue.BLUE, close -> EntryEditor.login(kit, a, manager::saveAccount, close));
                case dev.jasper.vault.model.ManagedSshKey k -> dialog("Edit stored SSH key", Nodes.Hue.GREEN, close -> {
                    var form = new EditorForm(kit, close);
                    form.text(EntryEditor.NAME, "Name", k.name());
                    form.display("storage", "Storage", "Stored in Vault");
                    form.display("fingerprint", "Fingerprint", k.fingerprint());
                    UUID keyId = k.id(); form.submit(() -> manager.renameManaged(keyId, form.value(EntryEditor.NAME).strip())); return form;
                });
                case SshKey k -> dialog("Edit SSH Key", Nodes.Hue.GREEN, close -> EntryEditor.key(kit, k, manager::importKey, close));
                case Note n -> dialog("Edit Secure Note", Nodes.Hue.AMBER, close -> EntryEditor.note(kit, n, manager::saveNote, close));
                default -> throw new IllegalArgumentException("Unknown vault entry");
            }
        });
    }
    static final String DELETE_FILES = "delete-files";
    private void delete(UUID id) {
        UiKit kit = context.kit();
        manager.entry(id).ifPresent(value -> dialog("Delete entry", close -> {
            var form = new EditorForm(kit, close);
            form.description("entry", "", "Remove this entry? " + manager.rows().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().name());
            if (value instanceof SshKey key) {
                form.toggle(DELETE_FILES, "Delete private and public key files too", false);
                form.display("private-file", "Private file", key.privatePath().toString());
                form.display("public-file", "Public file", key.publicPath().toString());
                form.display("shared-files", "Shared files", "Other accounts using these paths will also lose the files.");
            }
            form.setSaveLabel("Delete"); form.submit(() -> manager.delete(id, form.checked(DELETE_FILES))); return form;
        }));
    }
    /** Shows the saved plugin grants in the open manager; does nothing when it is closed or locked. */
    public void showGrants() { if (tab != null) { panel.showGrants(); tab.select(); } }
    /** Asks for a new master password over the open manager; does nothing when it is closed or locked. */
    public void changePassword() {
        dialog("Change Master Password", close -> new ChangePasswordForm(context.kit(), lock, close, failure -> {
            if (failure != null) context.notifications().group().error("Master password was not changed", EditorForm.message(failure), failure);
        }));
    }
    private void copyPublic(UUID id) {
        String name = manager.entry(id).map(entry -> switch (entry) {
            case SshKey key -> key.name();
            case ManagedSshKey key -> key.name();
            default -> "";
        }).orElse("");
        CompletableFuture<Void> copied = manager.publicKey(id).thenAccept(text -> copies.plain("public key", name, text));
        report("Could not copy the public key", copied);
    }
    private void report(String title, CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) context.notifications().group().error(title, EditorForm.message(failure), failure);
            changed();
        });
    }
    private void closeDialogs() {editors.close();}
    @Override public void close() {
        closeDialogs();
        if (tab != null) tab.close();
        if (panel != null) panel.close();
    }
}
```

The testkit finds a content tab and its view by title:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeContent.java", [("    WindowOwner owner;\n", "    WindowOwner owner;\n    /** The owned view the tab shows; null for raw content. */\n    FakeKitView view;\n", 1)])
edit(K + "FakeUi.java", [(
    'view.mount(spec);view.route="WORKSPACE";try{var tab=openComponent(spec,owner,view.component,placement);tab.onClosed(view::close);return tab;}',
    'view.mount(spec);view.route="WORKSPACE";try{var tab=(FakeContent)openComponent(spec,owner,view.component,placement);tab.view=view;tab.onClosed(view::close);return tab;}', 1)])
edit(K + "FakePluginHost.java", [(
"""    /** @param tab hosted content handle
     * @return published status descriptors */
    public List<dev.jasper.sdk.ui.ContentStatus> contentStatus(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).status; }
""", """    /** @param tab hosted content handle
     * @return published status descriptors */
    public List<dev.jasper.sdk.ui.ContentStatus> contentStatus(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent)tab).status; }

    /**
     * A plugin's open content tab, found by its title, as a plugin's manager or document opens it. Call it on the UI
     * thread.
     *
     * @param pluginId the tab's owner
     * @param title the tab's exact presentation title
     * @return the tab while it is open; empty after close or if absent
     * @since 0.20.0
     */
    public java.util.Optional<dev.jasper.sdk.ui.ContentTab> contentTab(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.contents.stream().filter(content -> !content.closed && content.presentation.title().equals(title))
            .map(content -> (dev.jasper.sdk.ui.ContentTab) content).findFirst();
    }

    /**
     * The owned view a plugin's open content tab shows, found by its title, for driving {@code UiView} content in headless
     * tests as {@link #windowView} does for dialogs. Call it on the UI thread.
     *
     * @param pluginId the tab's owner
     * @param title the tab's exact presentation title
     * @return the view while the tab is open; empty after close, for raw content or if absent
     * @since 0.20.0
     */
    public java.util.Optional<FakeKitView> contentView(String pluginId, String title) {
        return contentTab(pluginId, title).map(tab -> ((FakeContent) tab).view);
    }
""", 1)])
PY
```

The ledger rows, the rename through the Vault and its tests, the ROWS actions row, the manager without Done (Rulings
20) and the tests' tab readings:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
import ledger
V, VT = "plugins/vault/src/main/java/dev/jasper/vault/", "plugins/vault/src/test/java/dev/jasper/vault/"
ledger.rename(V + "ui/VaultManagerWindow.java", V + "ui/VaultManagerTab.java", "VaultManagerTab")
ledger.rename(VT + "ui/VaultManagerWindowTest.java", VT + "ui/VaultManagerTabTest.java", "VaultManagerTabTest")
for path, count in [(V + "VaultPlugin.java", 5), (V + "ui/ManagerPanel.java", 3), (VT + "ui/VaultManagerTabTest.java", 21),
                    (VT + "ui/CompositionParityTest.java", 2), (VT + "ui/ThemeMigrationEvidenceTest.java", 3),
                    (VT + "ui/ClassicManagerTest.java", 3), (VT + "ui/VaultCompositionTest.java", 2), (VT + "ui/ManagerPanelTest.java", 9),
                    (VT + "ui/ClassicEditorsTest.java", 2)]:
    regex(path, r"\bVaultManagerWindow\b", "VaultManagerTab", count)
regex(V + "VaultPlugin.java", r"\bmanagerWindow\b", "managerTab", 15)
edit(V + "VaultPlugin.java", [(" * Owns the manager window, status item, rail action and palette scope.", " * Owns the manager tab, status item, rail action and palette scope.", 1)])
edit("plugins/vault/src/main/resources/plugin.toml", [('version = "0.5.0"', 'version = "0.6.0"', 1)])
# The manager is a tab, so its title actions (Lock, Add) are its first row under ROWS, as the toolbar row is under CLASSIC.
edit(V + "ui/ManagerPanel.java", [
    ("    private final UiView view, titleActions;\n", "    private final UiView view;\n", 1),
    ("        view = kit.create(new ViewSpec(node()), this::handle);\n        titleActions = kit.create(new ViewSpec(actions()), this::handle);\n",
     "        view = kit.create(new ViewSpec(node()), this::handle);\n", 1),
    ("    UiView titleActions() { return titleActions; }\n", "", 1),
    ("    /** Routes the service's shared unlock prompt into this window's locked area. */",
     "    /** Routes the service's shared unlock prompt into the manager's locked area. */", 1),
    ("        view.update(new ViewSpec(node())); titleActions.update(new ViewSpec(actions()));", "        view.update(new ViewSpec(node()));", 1),
    ("""            body = new Nodes.Split("classic", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(toolbar(), body));
        }
""", """            body = new Nodes.Split("classic", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(toolbar(), body));
        }
        // A tab has no title bar: the Lock and Add actions are the first row, over the body (workspace tabs plan 4b).
        else body = new Nodes.Split("rows", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(actions(), body));
""", 1),
    ("    /** The window closed (its surface already disposed the view and erased its storage) or is closing. */",
     "    /** The tab closed (it already disposed the view and erased its storage) or is closing. */", 1),
    ("        view.close(); titleActions.close();", "        view.close();", 1)])
regex(VT + "ui/CompositionParityTest.java", r"reachable\(rows\.panel\(\)\.view\(\), rows\.panel\(\)\.titleActions\(\)\)", "reachable(rows.panel().view())", 2)
regex(VT + "ui/ManagerPanelTest.java", r"panel\.titleActions\(\)", "panel.view()", 5)
regex(VT + "ui/ThemeMigrationEvidenceTest.java", r"manager\.titleActions\(\)", "manager.view()", 1)
regex(VT + "ui/VaultCompositionTest.java", r"panel\.titleActions\(\)", "panel.view()", 1)
M = VT + "ui/VaultManagerTabTest.java"
edit(M, [
    ("class VaultManagerWindowTest {", "class VaultManagerTabTest {", 1),
    # The manager opens in a window of the host: a stand-in owner names no window a tab could open in.
    ("windowRef.set(window); window.show(owner, Optional.empty()); f.lock.lock(); window.changed();",
     "windowRef.set(window); window.show(terminal, Optional.empty()); f.lock.lock(); window.changed();", 1),
    ("""                assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
                var view = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();""",
     """                assertThat(host.contentTabs()).as("one manager tab").hasSize(1);
                assertThat(host.windows()).isEmpty();
                var view = host.contentView("dev.jasper.vault", "Credential Vault").orElseThrow();""", 1),
    ("""                assertThat(host.requestClose("dev.jasper.vault.manager")).isTrue();""",
     """                assertThat(host.contentTab("dev.jasper.vault", "Credential Vault").orElseThrow().requestClose().join()).isTrue();""", 1),
    ("""                assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
                assertThat(prompts[0]).isZero();""",
     """                assertThat(host.windows()).as("the editor closed").isEmpty();
                assertThat(host.contentView("dev.jasper.vault", "Credential Vault")).isPresent();
                assertThat(prompts[0]).isZero();""", 1),
    ("                // Closing the window while a prompt shows cancels it without touching the disposed view.",
     "                // Closing the tab while a prompt shows cancels it without touching the disposed view.", 1),
    ("                assertThat(host.requestClose(VaultManagerTab.ID)).isTrue();",
     "                assertThat(host.contentTab(\"dev.jasper.vault\", \"Credential Vault\").orElseThrow().requestClose().join()).isTrue();", 1)])
P = VT + "VaultPluginTest.java"
edit(P, [
    ("""            assertThat(host.windows()).as("no manager window").isEmpty();""",
     """            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).as("no manager").isEmpty();""", 1),
    ("""            assertThat(host.windows()).contains("dev.jasper.vault.manager|Credential Vault|true", "dialog|Change Master Password|true");""",
     """            assertThat(host.contentView("dev.jasper.vault", "Credential Vault")).isPresent();
            assertThat(host.windows()).contains("dialog|Change Master Password|true");""", 1),
    ("""            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            var manager = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();""",
     """            assertThat(host.windows()).isEmpty();
            var manager = host.contentView("dev.jasper.vault", "Credential Vault").orElseThrow();""", 1),
    ("""                assertThat(host.windows()).contains("dev.jasper.vault.manager|Credential Vault|true", "dialog|" + title + "|true");""",
     """                assertThat(host.contentView("dev.jasper.vault", "Credential Vault")).isPresent();
                assertThat(host.windows()).contains("dialog|" + title + "|true");""", 1),
    ("""            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            host.invoke("dev.jasper.vault.generate_key", window, null);""",
     """            assertThat(host.contentTabs()).as("one manager").hasSize(1);
            assertThat(host.windows()).isEmpty();
            host.invoke("dev.jasper.vault.generate_key", window, null);""", 1),
    ("""            assertThat(host.windows()).as("the existing manager hosts the shared unlock form").containsExactly("dev.jasper.vault.manager|Credential Vault|true");""",
     """            assertThat(host.windows()).as("the existing manager hosts the shared unlock form").isEmpty();
            assertThat(host.contentTabs()).hasSize(1);""", 1),
    ("""            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThat(host.windows()).hasSize(1);""",
     """            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThat(host.windows()).isEmpty();
            assertThat(host.contentTabs()).hasSize(1);""", 1)])
edit("plugins/remote/src/test/java/dev/jasper/remote/AnchoredImportIntegrationTest.java", [(
    'host.requestClose("dev.jasper.vault.manager");',
    'host.contentTab("dev.jasper.vault","Credential Vault").ifPresent(manager->manager.requestClose());', 1)])
# A content tab carries no window chrome (Rulings 20): the tab strip holds the manager's title and
# closes it, so the manager has no Done and Escape no longer closes it; while it prompts, Unlock is its one action.
edit(V + "ui/ManagerPanel.java", [
    (" * metadata is retained. Enter in the list edits the selected entry; elsewhere it presses Done, and while an unlock prompt shows, Enter in its field unlocks. UI thread only.",
     " * metadata is retained. Enter in the list edits the selected entry, and while an unlock prompt shows, Enter in its field\n"
     " * unlocks. The tab that shows the manager holds its title and closes it. UI thread only.", 1),
    ('GRANTS = "grants", BACK = "back", REVOKE = "revoke", STATUS = "status", CANCEL = "cancel", OKAY = "okay", CLOSE = "close",',
     'GRANTS = "grants", BACK = "back", REVOKE = "revoke", STATUS = "status", CANCEL = "cancel",', 1),
    ("    private final Runnable onGenerate, onLock, onUnlock, onClose;", "    private final Runnable onGenerate, onLock, onUnlock;", 1),
    ("""                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this(kit, Layout.ROWS, entry, onAdd, onEdit, onDelete, onCopy, onRevoke, onGenerate, onLock, onUnlock, onClose);""",
     """                 Runnable onLock, Runnable onUnlock) {
        this(kit, Layout.ROWS, entry, onAdd, onEdit, onDelete, onCopy, onRevoke, onGenerate, onLock, onUnlock);""", 1),
    ("""                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this.layout = layout;""", """                 Runnable onLock, Runnable onUnlock) {
        this.layout = layout;""", 1),
    ("        this.onLock = onLock; this.onUnlock = onUnlock; this.onClose = onClose;", "        this.onLock = onLock; this.onUnlock = onUnlock;", 1),
    ("            case CLOSE -> onClose.run();\n", "", 1),
    ("""        boolean prompting = unlock != null && !unlocked;
        var actions = new ArrayList<Nodes.Button>(List.of(new Nodes.Button(OKAY, ControlState.READY, "Done", "", prompting ? Nodes.ButtonKind.SECONDARY : Nodes.ButtonKind.PRIMARY, CLOSE, "")));
        if (prompting) actions.add(unlock.submitButton());
        return new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Credential Vault", Optional.empty(), List.of(body),
            Optional.of(layout == Layout.CLASSIC ? classicStatus() : status()), actions, prompting ? unlock.cancel : CLOSE);""",
     """        boolean prompting = unlock != null && !unlocked;
        // One full-bleed body over its status row, the kit's WINDOW form, whose title is only the view's name; no Done and no
        // Escape: the tab strip closes the manager (workspace tabs plan 4b). While it prompts, Unlock is its one action.
        List<Nodes.Button> actions = prompting ? List.of(unlock.submitButton()) : List.of();
        return new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Credential Vault", Optional.empty(), List.of(body),
            Optional.of(layout == Layout.CLASSIC ? classicStatus() : status()), actions, prompting ? unlock.cancel : "");""", 1)])
edit(VT + "ui/ManagerPanelTest.java", [
    ("""                () -> generated[0] = true, () -> { }, () -> { }, () -> { });""", """                () -> generated[0] = true, () -> { }, () -> { });""", 1),
    ("""    @Test void okayIsThePrimaryAndEveryCloseControlClosesTheWindow() throws Exception {
        edt(() -> {
            int[] closes = {0};
            var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.empty(), t -> { }, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> { }, () -> { }, () -> { }, () -> closes[0]++);
            var view = panel.view();
            var form = node(view, ManagerPanel.ROOT, Nodes.Form.class);
            assertThat(form.actions()).filteredOn(b -> b.kind() == Nodes.ButtonKind.PRIMARY).singleElement()
                .satisfies(b -> assertThat(b.id()).isEqualTo(ManagerPanel.OKAY));
            press(view, button(view, ManagerPanel.OKAY).action()); press(view, form.cancelAction());
            assertThat(closes[0]).isEqualTo(2);
            panel.close();
        });
    }""", """    /** Workspace tabs plan 4b: the tab strip closes the manager, which has no Done; Escape leaves it open. */
    @Test void theManagerHasNoDoneAndEscapeLeavesItOpen() throws Exception {
        edt(() -> {
            var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.empty(), t -> { }, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> { }, () -> { }, () -> { });
            var form = node(panel.view(), ManagerPanel.ROOT, Nodes.Form.class);
            assertThat(form.actions()).as("no Done").isEmpty();
            assertThat(form.cancelAction()).as("no Escape").isEmpty();
            assertThat(form.preview()).as("the status row stays").isPresent();
            panel.close();
        });
    }""", 1),
    ("""            () -> { }, () -> { }, () -> { }, () -> { });
    }
}""", """            () -> { }, () -> { }, () -> { });
    }
}""", 1)])
edit(VT + "ui/CompositionParityTest.java", [
    ("""            grant -> asked.add("revoke " + grant.pluginId()), () -> asked.add("generate"), () -> asked.add("lock"), () -> asked.add("unlock"),
            () -> asked.add("close"));""",
     """            grant -> asked.add("revoke " + grant.pluginId()), () -> asked.add("generate"), () -> asked.add("lock"), () -> asked.add("unlock"));""", 1),
    ("""            // The copies go through the plugin's CopyCards by the same act() case in both layouts; Done is checked last.
            actions.removeIf(action -> action.startsWith("detail.") || action.equals(ManagerPanel.CLOSE));""",
     """            // The copies go through the plugin's CopyCards by the same act() case in both layouts.
            actions.removeIf(action -> action.startsWith("detail."));""", 1),
    ("""            press(rows.panel().view(), ManagerPanel.CLOSE); press(classic.panel().view(), ManagerPanel.CLOSE);
""", "", 1)])
edit(VT + "ui/ThemeMigrationEvidenceTest.java", [(
    """                () -> { }, () -> { }, () -> { }, () -> { });""", """                () -> { }, () -> { }, () -> { });""", 1)])
edit(VT + "ui/ClassicManagerTest.java", [
    ("""            () -> asked.add("unlock"), () -> asked.add("close"));""", """            () -> asked.add("unlock"));""", 1),
    ("""            assertThat(button(view, ManagerPanel.OKAY).label()).isEqualTo("Done");
            assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);""",
     """            assertThat(node(view, ManagerPanel.ROOT, Nodes.Form.class).actions()).as("no Done: the tab strip closes the manager").isEmpty();""", 1)])
edit(VT + "ui/VaultCompositionTest.java", [
    ("""t->{},id->{},id->{},id->{},g->{},()->{},()->{},()->{},()->{});""", """t->{},id->{},id->{},id->{},g->{},()->{},()->{},()->{});""", 1),
    ("""t -> {}, id -> {}, id -> {}, id -> {}, grant -> {}, () -> {}, () -> {}, () -> {}, () -> {});""",
     """t -> {}, id -> {}, id -> {}, id -> {}, grant -> {}, () -> {}, () -> {}, () -> {});""", 1),
    ("""            assertThat(button(view,ManagerPanel.OKAY).label()).isEqualTo("Done");""",
     """            assertThat(node(view,ManagerPanel.ROOT,Nodes.Form.class).actions()).as("no Done in a tab").isEmpty();""", 1)])
edit(VT + "ui/VaultManagerTabTest.java", [
    ("""                    .satisfies(b -> { assertThat(b.label()).isEqualTo("Unlock"); assertThat(b.action()).isEqualTo(window.passwordPanel.submit); });
                assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.SECONDARY);""",
     """                    .satisfies(b -> { assertThat(b.label()).isEqualTo("Unlock"); assertThat(b.action()).isEqualTo(window.passwordPanel.submit); });
                assertThat(root.actions()).as("Unlock is the prompt's one action: a tab has no Done").hasSize(1);""", 1),
    ("""                assertThat(find(view, ManagerPanel.UNLOCK_PREFIX + "password")).isEmpty();
                assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);""",
     """                assertThat(find(view, ManagerPanel.UNLOCK_PREFIX + "password")).isEmpty();
                assertThat(node(view, ManagerPanel.ROOT, Nodes.Form.class).actions()).as("unlocked, no action").isEmpty();""", 1)])
PY
```

- [ ] **Step 4: The sample's About tab**

The script also regenerates the guide's `pluginpanels` snippet from the source and renames the call in its `pluginui`
snippet, which `AppDocumentationTest` compares with `SamplePlugin.java`:

```bash
python3 -I - <<'PY'
import sys, pathlib, re; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
S = "plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java"
edit(S, [
    ("import dev.jasper.sdk.ui.Anchor;\nimport dev.jasper.sdk.ui.DialogSpec;\n",
     "import dev.jasper.sdk.ui.Anchor;\nimport dev.jasper.sdk.ui.ContentSpec;\nimport dev.jasper.sdk.ui.ContentTab;\nimport dev.jasper.sdk.ui.DialogSpec;\n", 1),
    ("import dev.jasper.sdk.ui.PluginDialog;\nimport dev.jasper.sdk.ui.PluginWindow;\n", "import dev.jasper.sdk.ui.PluginDialog;\n", 1),
    ("import dev.jasper.sdk.ui.ToolbarItem;\nimport dev.jasper.sdk.ui.WindowSpec;\n", "import dev.jasper.sdk.ui.ToolbarItem;\n", 1),
    ("import java.awt.Dimension;\n", "", 1),
    ("""        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
            // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
            PluginWindow window = context.windows().create(
                new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
            var content = context.kit().create(new ViewSpec(new Nodes.Empty("sample.about", ControlState.READY,
                new Nodes.Icon("sample.about.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"), "Sample Plugin",
                "This window's chrome belongs to Jasper; its content to the plugin.",
                List.of(new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
                if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample Details", window, true));""",
     """        ContentTab[] about = {null};
        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
            // A tab in the window the action ran in, which Jasper draws with its strip; an open About is selected again.
            if (about[0] != null && !about[0].isClosed()) { about[0].select(); return; }
            var content = context.kit().create(new ViewSpec(new Nodes.Empty("sample.about", ControlState.READY,
                new Nodes.Icon("sample.about.icon", ControlState.READY, FLASK, Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Sample"), "Sample Plugin",
                "This tab's strip and window belong to Jasper; its content to the plugin.",
                List.of(new Nodes.Button(DETAILS_BUTTON, ControlState.READY, "Details…", "", Nodes.ButtonKind.SECONDARY, DETAILS_BUTTON, "")))), event -> {
                if (!(event instanceof UiEvent.Action action) || !action.id().equals(DETAILS_BUTTON)) return;
                // The tab owns the sheet, which follows it to whichever window holds it.
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample Details", about[0], true));""", 1),
    ("""                dialog.setContent(body);
                dialog.show();
            });
            window.setContent(content);
            window.show();
            window.toFront();
        });""", """                dialog.setContent(body);
                dialog.show();
            });
            about[0] = context.windows().openTab(new ContentSpec("about", "About Sample"), invoked.window(), content);
        });""", 1)])
regex(S, r"\binstallPanelAndWindow\b", "installPanelAndAboutTab", 2)
edit("docs/plugin-authoring.md", [("    installPanelAndWindow(context, stepMillis);\n}", "    installPanelAndAboutTab(context, stepMillis);\n}", 1)])
edit("plugins/sample/src/main/resources/plugin.toml", [('version = "0.2.1"', 'version = "0.3.0"', 1)])
# The guide shows the same block, indented as a method.
source = pathlib.Path(S).read_text(encoding="utf-8")
block = source.split("// example:pluginpanels:start\n", 1)[1].split("// example:pluginpanels:end", 1)[0]
lines = block.rstrip().split("\n")
indent = min(len(line) - len(line.lstrip()) for line in lines if line.strip())
snippet = "\n".join(line[indent:] for line in lines) + "\n"
guide = pathlib.Path("docs/plugin-authoring.md")
text = guide.read_text(encoding="utf-8")
start = text.index("<!-- example:pluginpanels -->\n```java\n") + len("<!-- example:pluginpanels -->\n```java\n")
end = text.index("```", start)
guide.write_text(text[:start] + snippet + text[end:], encoding="utf-8")
T = "plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java"
edit(T, [
    ('                var about = host.windowView("dev.jasper.sample", "About Sample").orElseThrow();',
     '                var about = host.contentView("dev.jasper.sample", "About Sample").orElseThrow();', 1),
    ('            assertThat(host.windows()).as("a singleton").containsExactly("dev.jasper.sample.about-window|About Sample|true");',
     '            assertThat(host.windows()).isEmpty();\n'
     '            assertThat(host.contentTabs()).as("one About tab").hasSize(1);\n'
     '            assertThat(host.contentView("dev.jasper.sample", "About Sample")).isPresent();', 1),
    ("    @Test void mountedPanelAndWindowButtonsDispatchTheirDeclaredActions() throws Exception {",
     "    @Test void mountedPanelAndAboutTabButtonsDispatchTheirDeclaredActions() throws Exception {", 1),
    ("    @Test void theDemoUiAddsAPanelARailButtonAndAWindow() throws Exception {",
     "    @Test void theDemoUiAddsAPanelARailButtonAndAnAboutTab() throws Exception {", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java", [(
    '''            .anySatisfy(line -> assertThat(line).contains("dev.jasper.sample", "0.2.1", "BUNDLED", "ACTIVE"))''',
    '''            .anySatisfy(line -> assertThat(line).contains("dev.jasper.sample", "0.3.0", "BUNDLED", "ACTIVE"))''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:check :jasper-plugin-sample:check :jasper-plugin-remote:test --tests '*AnchoredImportIntegrationTest' :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.windows.VaultSheetRenderTest' --tests 'dev.jasper.app.documentation.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS, except in `ClassicVaultRenderTest`'s three manager tests (`theManagerHasTheMocksToolbarAndStatusRows`,
`theCategoriesAndItemsMatchTheItemSelectedMock`, `theManagerMatchesItsMocksWithAndWithoutASelection`) where a band
the tab changed fails: Step 6 re-pins it. Its sheets' and Unlock's tests pass unchanged.

- [ ] **Step 6: Re-pin what the tab moved in the classic manager's render**

The manager renders as the host mounts a content tab (Rulings 20): no Done and no dialog band under its status row,
which the 07 mocks' window shows. Its footer is the status row alone, so the status row's band (`STATUS`, device column
1600) and the bands that reach the footer (the last runs of `CATEGORIES`, `ITEMS`, `DETAILS` and `DETAILS_NONE`, and
the rows they are read between) can move. Pin each to the render, nothing else:

1. For every failing `bands(...)` assertion in the three manager tests, the failure message reads `expected [...]` then
   `actual [...]`: replace that constant (or the literal in the call) with the `actual` list without its brackets.
2. If the footer's top moved, the rows read (`1068, 1142` for the status row, `1070` as the columns' bottom) no longer
   bracket it: add `System.out.println(MockMatch.bands(render, 1600, 0, render.getHeight()));` after the render in
   `theManagerHasTheMocksToolbarAndStatusRows`, run that test, read the runs from its `<system-out>` in
   `jasper-app/build/test-results/test/TEST-dev.jasper.app.plugins.ClassicVaultRenderTest.xml` (the status row starts at
   the last `#C4C7CC` rule), set the rows from them, then delete the line.
3. A `fontsFloor` agreement below 0.55 is re-pinned to the measured agreement rounded down to two decimals, with the
   reason in a comment beside it (the 07 mock shows a window, not the tab).
4. Leave everything else as it is: `TOOLBAR`, `toolbarVerbs`, the Fields grid's `inkRuns`, the colour checks, and the
   sheets' and Unlock's tests. If one of those fails, stop and report it: that is a layout change, not a band.

Write every re-pinned value, old → new, into this plan's banner and the STATUS deviation (Task 8). Then run
`./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest'` (PASS) and `./gradlew check
--continue`: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A plugins jasper-sdk-testkit jasper-app docs
git commit -F - <<'EOF'
Workspace tabs: the Vault manager and the sample's About as content tabs

The Credential Vault manager is one content tab in the window that asked,
its ROWS actions the first row of its body, with no Done: the tab strip
closes it. The sample's About is a tab that owns its Details sheet. The
testkit finds a content tab and its view by title; the classic manager's
render is the tab's, re-pinned where it leaves the 07 window mock.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Plugin windows go

**Files:**
- Delete: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginWindow.java`, `ui/WindowSpec.java`
- Modify (SDK): `ui/Windows.java`, `ui/DialogSpec.java`, `ui/Rail.java`, `ui/WindowSurface.java`, `WindowOwner.java`,
  `ui/ShortcutContext.java`, `files/FileChoice.java`; tests `ShortcutContextTest`, `PanelAndWindowValuesTest`
- Modify (testkit): `FakeUi.java`, `FakePluginHost.java`, `FakePluginContext.java`, `contract/ContractHarness.java`,
  `contract/PluginContractTest.java`, `AnchorContractTest.java`; tests `FakeContractTest`, `FakeUiTest`,
  `FakePanelsAndWindowsTest`, `FakeChooserTest`
- Modify (application): `commands/KeyContext.java`, `workspace/WindowContent.java`, `shortcuthelp/ShortcutCatalog.java`,
  `plugins/HostedUi.java`, `plugins/KitCompatibility.java`, `plugins/PluginRuntime.java`, `plugins/SurfaceMount.java`,
  `windows/AuxiliaryWindows.java`, `windows/AuxiliarySurface.java`, `windows/NativeShells.java`,
  `platform/MacTitleBar.java`, `contributions/QuitGuards.java`, `application/JasperApplication.java`
- Modify (kit): `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java`, `Menus.java`, `Buttons.java`; test
  `KitViewToolbarStripTest`
- Test (application): `KitCompatibilityTest`, `KeyContextTest`, `AuxiliaryWindowsTest`, `ShellChromeTest`,
  `NativeShellsChromeTest`, `HostedAnchorContractTest`, `HostedAnchorTest`, `HostedChooserTest`, `HostedUiTest`,
  `KitMountingTest`, `StagedEditorHost`, `WorkspaceContentRouteTest`, `JasperApplicationBackgroundTest`,
  `JasperApplicationPluginsTest`, `AppContractTest`; the Editor's `DialogsTest`

**Interfaces:**
- Consumes: Task 3 (nothing calls `Windows.create`).
- Produces: `Windows` without `create`; `ShortcutContext.Kind { GLOBAL, TERMINAL, CONTENT, PANEL }`;
  `KeyContext.Kind { GLOBAL, TERMINAL, CONTENT, PANEL }`; `KitCompatibility` refuses 0.20's removals;
  `JasperApplication.guardsAllowQuit()`; `ContractHarness` without `closeCommand`; `AuxiliarySurface` without
  `titleActions`, `setTitleActions`, `contentSize`, `onCloseCommand` or `closeCommand` (Close Window calls
  `requestClose()`); `MacTitleBar` without an accessory; `KitView` without `titleStrip`; `SurfaceMount(surface)`.

- [ ] **Step 1: Write the failing test**

A plugin compiled against 0.19 that claims 0.20 and still names a plugin window's type, calls `Windows.create`,
`ContentTab.move(boolean)` or names `ShortcutContext.Kind.WINDOW` is refused when it loads (a `Windows.create`
reference for the removed types its descriptor names, which the check reads before the member); the member that took
its place loads:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
KT = "jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java"
edit(KT, [("""    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {""",
"""    /**
     * Workspace tabs plan 4b: a plugin built on 0.19 that claims 0.20 and still names a plugin window's type, moves a tab
     * with {@code move(boolean)} or names the window shortcut context is refused at load. A {@code Windows.create}
     * reference names the removed types in its descriptor, which the check reads first.
     */
    @Test void thePluginWindowsSdkZeroTwentyRemovedAreRefused() throws Exception {
        for (String type : List.of("dev.jasper.sdk.ui.PluginWindow", "dev.jasper.sdk.ui.WindowSpec", "dev.jasper.sdk.ui.WindowSpec$SizeBasis"))
            rejects(ClassFile.of().build(ClassDesc.of("test.kit.Probe"), b -> b.constantPool().classEntry(ClassDesc.of(type))),
                "references removed SDK type " + type.replace('.', '/') + ";", "0.20");
        rejects(reference("dev.jasper.sdk.ui.Windows","create","(Ldev/jasper/sdk/ui/WindowSpec;)Ldev/jasper/sdk/ui/PluginWindow;"),
            "references removed SDK type dev/jasper/sdk/ui/","0.20");
        rejects(reference("dev.jasper.sdk.ui.ContentTab","move","(Z)V"),"dev/jasper/sdk/ui/ContentTab#move(Z)V","0.20");
        rejects(reference("dev.jasper.sdk.ui.ContentTab","isSeparate","()Z"),"dev/jasper/sdk/ui/ContentTab#isSeparate()Z","0.20");
        rejects(reference("dev.jasper.sdk.ui.ShortcutContext","window","(Ljava/lang/String;)Ldev/jasper/sdk/ui/ShortcutContext;"),
            "dev/jasper/sdk/ui/ShortcutContext#window(Ljava/lang/String;)Ldev/jasper/sdk/ui/ShortcutContext;","0.20");
        rejects(constant("dev.jasper.sdk.ui.ShortcutContext$Kind","WINDOW"),"dev/jasper/sdk/ui/ShortcutContext$Kind#WINDOWLdev/jasper/sdk/ui/ShortcutContext$Kind;","0.20");
        var current=candidate(">=0.20.0, <0.21",Map.of("test/kit/Probe.class",reference("dev.jasper.sdk.ui.ContentTab","moveToNewWindow","()V")),false);
        try(var loader=PluginLoader.load(current,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as("the member that took its place").isNotNull();}
    }
    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.KitCompatibilityTest'`
Expected: FAIL in `thePluginWindowsSdkZeroTwentyRemovedAreRefused`: the 0.20 references load.

- [ ] **Step 3: The SDK, `KitCompatibility` and the testkit**

```bash
git rm -q jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginWindow.java jasper-sdk/src/main/java/dev/jasper/sdk/ui/WindowSpec.java
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
D = "jasper-sdk/src/main/java/dev/jasper/sdk/"
edit(D + "ui/Windows.java", [
    ("""    /**
     * Creates a window on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the window is
     * @return the window, or the open one for a singleton id
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginWindow create(WindowSpec spec);

""", "", 1),
    ("     * Registers a guard asked when the user quits or restarts Jasper, after plugin windows' and content tabs' guards",
     "     * Registers a guard asked when the user quits or restarts Jasper, after content tabs' guards", 1),
    ("     * Quit and a Jasper window's File &rsaquo; Quit by closing its windows through the content tabs' and plugin windows'\n     * closing guards",
     "     * Quit and a Jasper window's File &rsaquo; Quit by closing its windows through the content tabs'\n     * closing guards", 1),
    ("     * @param owner live Jasper window or shown window created by this plugin", "     * @param owner an open Jasper window, or a content tab of this plugin", 3)])
edit(D + "ui/DialogSpec.java", [(" * @param owner the Jasper window or plugin window the dialog belongs to",
                                  " * @param owner the Jasper window, content tab or anchor the dialog belongs to", 1)])
edit(D + "ui/Rail.java", [("for example a button that opens a plugin window. */", "for example a button that opens a plugin's tab. */", 1)])
edit(D + "ui/WindowSurface.java", [
    (" * What plugin windows and dialogs share. The application owns the frame, title bar, icon, menu bar,\n * theme tracking and saved bounds",
     " * A plugin's dialog. The application owns the frame, title bar, icon, menu bar,\n * theme tracking and saved bounds", 1),
    ("it open; a guard that throws is treated as allowing the close. Since 0.8.4, quitting or restarting Jasper asks the guards of every open plugin window (not dialogs or\n     * overlays) first, and a refusal cancels the quit; nothing closes while they are asked.\n",
     "it open; a guard that throws is treated as allowing the close.\n", 1)])
edit(D + "WindowOwner.java", [(" * Something a dialog can be parented to: one of the application's Jasper windows or a plugin's own\n * window.",
                                " * Something a dialog can be parented to: one of the application's Jasper windows, a plugin's content tab or\n * an anchor.", 1)])
edit(D + "ui/ShortcutContext.java", [
    (" * most specific applicable one runs: a panel, window or content context, then {@link #terminal()}, then",
     " * most specific applicable one runs: a panel or content context, then {@link #terminal()}, then", 1),
    (" * @param id the panel's or window's id for {@link Kind#PANEL} and {@link Kind#WINDOW}; empty for the other kinds",
     " * @param id the panel's id for {@link Kind#PANEL}; empty for the other kinds", 1),
    ("""        /** Inside one of the plugin's panels. */
        PANEL,
        /**
         * Inside one of the plugin's windows. Window shortcuts take effect once plugin windows get the Keymap
         * (plan K4); until then an action in this context never applies.
         */
        WINDOW
    }

    /** Checks that panel and window contexts name an id and the other kinds do not. */
    public ShortcutContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        boolean named = kind == Kind.PANEL || kind == Kind.WINDOW;
        if (named && id.filter(value -> !value.isBlank()).isEmpty())
            throw new IllegalArgumentException("A " + kind + " context needs the id of one of the plugin's "
                + (kind == Kind.PANEL ? "panels" : "windows"));
        if (!named && id.isPresent()) throw new IllegalArgumentException("A " + kind + " context takes no id");
    }
""", """        /** Inside one of the plugin's panels. */
        PANEL
    }

    /** Checks that a panel context names an id and the other kinds do not. */
    public ShortcutContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        boolean named = kind == Kind.PANEL;
        if (named && id.filter(value -> !value.isBlank()).isEmpty())
            throw new IllegalArgumentException("A " + kind + " context needs the id of one of the plugin's panels");
        if (!named && id.isPresent()) throw new IllegalArgumentException("A " + kind + " context takes no id");
    }
""", 1)])
span(D + "ui/ShortcutContext.java", """    /**
     * Inside one of the plugin's windows. Window shortcuts take effect once plugin windows get the Keymap""",
     """    public static ShortcutContext window(String windowId) { return new ShortcutContext(Kind.WINDOW, Optional.ofNullable(windowId)); }
""", "")
T = "jasper-sdk/src/test/java/dev/jasper/sdk/ui/"
edit(T + "ShortcutContextTest.java", [
    ("    @Test void panelAndWindowContextsNameAnIdAndTheOthersDoNot() {", "    @Test void aPanelContextNamesAnIdAndTheOthersDoNot() {", 1),
    ("""        assertThat(ShortcutContext.window("dev.x.manager"))
            .isEqualTo(new ShortcutContext(ShortcutContext.Kind.WINDOW, Optional.of("dev.x.manager")));
""", "", 1),
    ("""        assertThatIllegalArgumentException().isThrownBy(() -> ShortcutContext.window(" "));
""", """        assertThatIllegalArgumentException().isThrownBy(() -> new ShortcutContext(ShortcutContext.Kind.PANEL, Optional.of(" ")));
""", 1)])
edit(T + "PanelAndWindowValuesTest.java", [
    ("""    @Test void windowAndDialogSpecsValidateAndCopyTheSize() {
        var size = new Dimension(640, 480);
        var spec = new WindowSpec("dev.x.tool.manager", "Manager", size, true);
        size.width = 1;
        assertThat(spec.preferredSize()).isEqualTo(new Dimension(640, 480));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("nodot", "Manager", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(0, 10), false));
        WindowOwner owner""", """    @Test void dialogSpecsValidateTheirTitleAndOwner() {
        WindowOwner owner""", 1),
    ("""    @Test void oldNativeConstructorsPreserveCenteredAndOuterDefaults() throws Exception {
        WindowOwner owner = new WindowOwner() { };
        assertThat(new DialogSpec("Old", owner, true).presentation().toString()).isEqualTo("CENTERED");
        var old = new WindowSpec("dev.x.window", "Old", new Dimension(300, 200), true);
        assertThat(old.sizeBasis().toString()).isEqualTo("OUTER");
        old.preferredSize().width = 1;
        assertThat(old.preferredSize()).isEqualTo(new Dimension(300, 200));
    }""", """    @Test void theOldNativeConstructorPreservesTheCenteredDefault() throws Exception {
        WindowOwner owner = new WindowOwner() { };
        assertThat(new DialogSpec("Old", owner, true).presentation().toString()).isEqualTo("CENTERED");
    }""", 1),
    ("""        assertThatNullPointerException().isThrownBy(() -> new WindowSpec("dev.x.window", "Window", new Dimension(1,1), false, null));
""", "", 1)])
K = "jasper-app/src/main/java/dev/jasper/app/plugins/KitCompatibility.java"
edit(K, [
    ("""        "dev/jasper/sdk/ui/PluginWindow#setContent(Ljavax/swing/JComponent;)V",
""", "", 1),
    ("""        "dev/jasper/sdk/ui/PanelFactory$View")));""", """        "dev/jasper/sdk/ui/PanelFactory$View"),
        // The plugin windows SDK 0.20 removed with workspace tabs.
        "0.20", List.of(
        "dev/jasper/sdk/ui/PluginWindow",
        "dev/jasper/sdk/ui/WindowSpec",
        "dev/jasper/sdk/ui/WindowSpec$SizeBasis")));""", 1),
    ("""        "dev/jasper/sdk/ui/StandardMenu#PANELdev/jasper/sdk/ui/StandardMenu;")));""", """        "dev/jasper/sdk/ui/StandardMenu#PANELdev/jasper/sdk/ui/StandardMenu;"),
        // The members SDK 0.20 removed with plugin windows and the separate workspace; Windows.create and every PluginWindow
        // member name a removed type, which the type check finds first.
        "0.20", List.of(
        "dev/jasper/sdk/ui/ContentTab#move(Z)V",
        "dev/jasper/sdk/ui/ContentTab#isSeparate()Z",
        "dev/jasper/sdk/ui/ShortcutContext#window(Ljava/lang/String;)Ldev/jasper/sdk/ui/ShortcutContext;",
        "dev/jasper/sdk/ui/ShortcutContext$Kind#WINDOWLdev/jasper/sdk/ui/ShortcutContext$Kind;")));""", 1)])
edit(D + "files/FileChoice.java", [(" * @param owner         a live Jasper window, a shown window of this plugin, or one of its content tabs",
                                    " * @param owner         a live Jasper window or one of this plugin's content tabs", 1)])
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
U = K + "FakeUi.java"
edit(U, [
    ("import dev.jasper.sdk.ui.PluginWindow;\n", "", 1),
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    ("""            spec.context().id().ifPresent(owned -> requireNamespace(owned,
                spec.context().kind() == ShortcutContext.Kind.PANEL ? "A panel context" : "A window context"));""",
     """            spec.context().id().ifPresent(owned -> requireNamespace(owned, "A panel context"));""", 1),
    ("""    private static dev.jasper.sdk.ui.ViewSpec titleSpec(dev.jasper.sdk.ui.ViewSpec value) {
        if (!(value.root() instanceof dev.jasper.sdk.ui.Nodes.Stack stack)
                || stack.role() != dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE
                || stack.children().stream().anyMatch(node -> !(node instanceof dev.jasper.sdk.ui.Nodes.Button)
                    && !(node instanceof dev.jasper.sdk.ui.Nodes.MenuButton)))
            throw new IllegalArgumentException("Title actions require an INLINE Stack of Buttons and MenuButtons");
        return value;
    }
    record SurfaceMount(FakeWindow surface, boolean title) { }

    final class FakeWindow implements PluginWindow, PluginDialog {""", """    record SurfaceMount(FakeWindow surface, boolean title) { }

    /** A plugin's dialog, or a {@code Windows.overlay}. */
    final class FakeWindow implements PluginDialog {""", 1),
    ("""        final SurfaceMount bodyMount = new SurfaceMount(this, false), titleMount = new SurfaceMount(this, true);
        FakeKitView mounted, titleActions;""", """        final SurfaceMount bodyMount = new SurfaceMount(this, false);
        FakeKitView mounted;""", 1),
    ("""        @Override public void setTitleActions(Optional<dev.jasper.sdk.ui.UiView> actions) {
            context.requireOpen(); java.util.Objects.requireNonNull(actions, "actions");
            if (closed) throw new IllegalStateException("Surface is closed");
            var next = actions.map(context.kit()::requireOwned).orElse(null);
            if (next == titleActions) return;
            if (next != null) { titleSpec(next.spec()); next.mount(titleMount); }
            var previous = titleActions; titleActions = next;
            if (next != null) next.hostSpec = FakeUi::titleSpec;
            if (previous != null) previous.close();
        }
""", "", 1),
    ("""        final List<BooleanSupplier> closeCommands = new ArrayList<>();
        @Override public Subscription onCloseCommand(BooleanSupplier handler) {
            closeCommands.add(java.util.Objects.requireNonNull(handler));
            return () -> closeCommands.remove(handler);
        }
        boolean closeCommand() {
            if (closed) return true;
            List<BooleanSupplier> handlers = new ArrayList<>(closeCommands);
            java.util.Collections.reverse(handlers);
            for (BooleanSupplier handler : handlers) {
                try { if (handler.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " close command: " + failure); }
            }
            return requestClose();
        }
""", "", 1),
    ("            if(titleActions!=null){titleActions.close();titleActions=null;}\n", "", 1),
    ("""        if (owner instanceof FakeWindow window) return windows.contains(window) && window.shown && !window.closed;
""", "", 1),
    ("""                } else if (owner instanceof FakeWindow window) {
                    if (window.closed || !windows.contains(window)) throw new IllegalArgumentException("A dialog needs an open owner window");
                    parent = window;
                } else if""", """                } else if""", 1),
    ('throw new IllegalArgumentException("A dialog\'s owner must be an open window of this plugin or a Jasper window");',
     'throw new IllegalArgumentException("A dialog\'s owner must be a Jasper window, a content tab or an anchor from this host");', 1)])
import re, pathlib
text = pathlib.Path(U).read_text(encoding="utf-8")
count = text.count('"Picker requires a shown plugin window or an open Jasper window from this host"')
if count != 2: sys.exit(f"{U}: expected 2 picker messages, found {count}")
pathlib.Path(U).write_text(text.replace('"Picker requires a shown plugin window or an open Jasper window from this host"',
    '"Picker requires an open Jasper window or content tab from this host"'), encoding="utf-8")
span(U, "            @Override public PluginWindow create(WindowSpec spec) {", "                return window;\n            }\n", "")
P = K + "FakePluginHost.java"
edit(P, [
    ("""    /**
     * Inspects the actual Swing content of an open plugin surface for headless form tests.
     * @param pluginId surface owner
     * @param title exact surface title""", """    /**
     * Inspects the actual Swing content of an open plugin dialog for headless form tests.
     * @param pluginId dialog owner
     * @param title exact dialog title""", 1),
    ("""     * The owned view mounted on an open plugin window or dialog, for driving {@code UiView} content in headless tests
     * (secrets through {@link FakeKitView#setSecret}, intents through {@link FakeKitView#emit}). Call it on the UI thread.
     * @param pluginId surface owner
     * @param title exact surface title
     * @return the mounted view while the surface is open, empty after close, for raw content or if absent""",
     """     * The owned view mounted on an open plugin dialog, for driving {@code UiView} content in headless tests
     * (secrets through {@link FakeKitView#setSecret}, intents through {@link FakeKitView#emit}). Call it on the UI thread.
     * @param pluginId dialog owner
     * @param title exact dialog title
     * @return the mounted view while the dialog is open, empty after close, for raw content or if absent""", 1),
    ("""     * @return the presentation while the dialog is open; empty for windows, overlays, closed or absent dialogs""",
     """     * @return the presentation while the dialog is open; empty for overlays and closed or absent dialogs""", 1),
    ("""     * @return the swatch while the dialog (any presentation, {@code OVERLAY} dialogs included) is open; empty for plugin
     *     windows, {@code Windows.overlay} surfaces and closed or absent dialogs""",
     """     * @return the swatch while the dialog (any presentation, {@code OVERLAY} dialogs included) is open; empty for
     *     {@code Windows.overlay} surfaces and closed or absent dialogs""", 1),
    ("""    /** The currently shown plugin windows. */
    public List<String> windows() {""", """    /** The open plugin dialogs and overlays as {@code id|title|shown}, in creation order; the id is {@code dialog} or {@code overlay}. */
    public List<String> windows() {""", 1),
    ("""    /**
     * Closes the first open window with this id as the user would, consulting its closing guards.
     *
     * @param windowId the window id, or {@code dialog}
     * @return false when a guard vetoed the close or there is no such window
     */""", """    /**
     * Closes the first open dialog or overlay with this id as the user would, consulting its closing guards.
     *
     * @param windowId {@code dialog} or {@code overlay}
     * @return false when a guard vetoed the close, for an overlay, which the user cannot close, or when there is none
     */""", 1),
    ("""    /**
     * Runs the Close Window command of the first open window with this id, as File → Close Window or Cmd/Ctrl+W
     * would: its close-command handlers newest first, then, when none handled it, its closing guards.
     *
     * @param windowId the window id
     * @return true when the window closed; false when a handler handled the command, a guard vetoed or there is no such window
     * @since 0.8.4
     */
    public boolean closeCommand(String windowId) {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows)) if (window.id.equals(windowId)) return window.closeCommand();
        return false;
    }

""", "", 1),
    ("""     * Asks the closing guards of every open plugin window, then every quit guard, as quitting or restarting Jasper
     * does, and closes nothing. Dialogs and overlays are not asked. A quit guard whose future is still pending counts
     * as a refusal here: the fake does not pump events.""", """     * Asks every plugin's content tabs' closing guard, then every quit guard, as quitting or restarting Jasper
     * does, and closes nothing. Dialogs and overlays are not asked. A quit guard whose future is still pending counts
     * as a refusal here: the fake does not pump events.""", 1),
    ("""        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows))
                if (!window.id.equals("dialog") && !window.id.equals("overlay") && !window.mayClose()) return false;
""", "", 1)])
C = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java"
edit(C, [
    ("    /** Open plugin windows and dialogs as {@code id|title|shown} in creation order; a dialog's id is {@code dialog}. */",
     "    /** Open plugin dialogs and overlays as {@code id|title|shown} in creation order; the id is {@code dialog} or {@code overlay}. */", 1),
    ("    /** UI thread: closes the first open window with this id as the user would; false when vetoed or absent. */",
     "    /** UI thread: closes the first open dialog ({@code dialog}) or overlay ({@code overlay}) as the user would; false when vetoed or absent. */", 1),
    ("""    /**
     * UI thread: runs the Close Window command of the first open window with this id: its close-command
     * handlers newest first, then its closing guards. True when the window closed.
     */
    boolean closeCommand(String windowId);

""", "", 1),
    ("    /** UI thread: asks every open plugin window's closing guards, then every quit guard, as quitting Jasper does, closing nothing; true when the quit may go ahead. */",
     "    /** UI thread: asks every plugin's content tabs' closing guard, then every quit guard, as quitting Jasper does, closing nothing; true when the quit may go ahead. */", 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [
    ("            @Override public boolean closeCommand(String windowId) { return host.closeCommand(windowId); }\n", "", 1)])
edit(K + "FakePluginContext.java", [("    /** Plugin windows and dialogs. */", "    /** Plugin dialogs, overlays, content tabs and pickers. */", 1)])
PY
```

- [ ] **Step 4: The contract suites and the tests**

The tests whose subject was a plugin window go; the others take a content tab, a Jasper window or an anchor as owner,
and a behaviour that stays keeps its test: the refusing guard is a content tab's, which then agrees, and `quitNow` skips
the quit-guard fixture (Rulings 11):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [
    ("import dev.jasper.sdk.ui.PluginWindow;\n", "", 1),
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    # A dialog is the surface that remains; it holds owned views as a window did.
    ("""            var window=ctx.windows().create(new WindowSpec("test.owned.window","Window",new Dimension(400,300),true));
            assertThatThrownBy(() -> window.setContent(alien)).isInstanceOf(IllegalArgumentException.class);
            window.setContent(view);window.setContent(view);
            assertThat(h.viewRoute(view)).as("window content keeps the chrome slot").isEqualTo("CHROME");
            assertThat(ctx.windows().create(new WindowSpec("test.owned.window","Window",new Dimension(400,300),true))).isSameAs(window);
            var veto=window.onClosing(() -> false);
            assertThat(h.requestClose("test.owned.window")).isFalse();assertThat(disposed.get()).isZero();""",
     """            var owner=ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("owner","Owner"),null,kitView(ctx));
            var window=ctx.windows().dialog(new DialogSpec("Window",owner,false));
            assertThatThrownBy(() -> window.setContent(alien)).isInstanceOf(IllegalArgumentException.class);
            window.setContent(view);window.setContent(view);
            assertThat(h.viewRoute(view)).as("dialog content keeps the chrome slot").isEqualTo("CHROME");
            var veto=window.onClosing(() -> false);
            assertThat(h.requestClose("dialog")).isFalse();assertThat(disposed.get()).isZero();""", 1),
    ("""    @Test void shortcutContextsNameOnlyThePluginsOwnPanelsAndWindows() {""", """    @Test void shortcutContextsNameOnlyThePluginsOwnPanels() {""", 1),
    ("""            context.actions().register(ActionSpec.of("test.alpha.copy", "Copy")
                .withContext(ShortcutContext.window("test.alpha.manager")), invoked -> { });
""", "", 1),
    ("""            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.near", "Near")
                .withContext(ShortcutContext.window("test.alphabet.manager")), invoked -> { }));
""", """            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.near", "Near")
                .withContext(ShortcutContext.panel("test.alphabet.hosts")), invoked -> { }));
""", 1),
    ("""        assertThat(h.actions()).containsExactly("test.alpha.new|New|true", "test.alpha.save|Save|true", "test.alpha.copy|Copy|true");""",
     """        assertThat(h.actions()).containsExactly("test.alpha.new|New|true", "test.alpha.save|Save|true");""", 1),
    ("""            PluginWindow manager = context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true));
            PluginWindow other = context.windows().create(new WindowSpec("test.alpha.other", "Other", new Dimension(640, 480), true));""",
     """            var manager = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, kitView(context));
            var other = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("other", "Other"), null, kitView(context));""", 1)])
span(P, "    @Test void windowsAreSingletonsGuardedOwnedAndClosedWithTheirPlugin() {",
     "    /** SDK 0.18: a dialog's swatch reaches the host with the dialog; NONE asks for no square. */",
     "    /** SDK 0.18: a dialog's swatch reaches the host with the dialog; NONE asks for no square. */")
span(P, "    @Test void theCloseCommandAsksTheContentNewestFirstBeforeTheGuards() {",
     "    @Test void quitGuardsAreAskedInTurnAndOnlyAFalseDecisionRefuses() {",
     """    /** Quit asks the content tabs' guards and closes nothing; a dialog's guard is not asked. */
    @Test void quitAsksThePluginsContentTabGuardAndClosesNothing() {
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("first", "First"), null, kitView(context));
            var second = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("second", "Second"), null, kitView(context));
            context.windows().onTabsClosing((tabs, batch) -> { asked.add(String.valueOf(tabs.size())); return CompletableFuture.completedFuture(allow.get()); });
            context.windows().dialog(new DialogSpec("Details", second, false)).onClosing(() -> { asked.add("dialog"); return false; });
        });
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("2");
        allow.set(true);
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("dialogs are not asked").isTrue());
        assertThat(asked).containsExactly("2");
        assertThat(h.windows()).as("asking closes nothing").hasSize(1);
    }

    @Test void quitGuardsAreAskedInTurnAndOnlyAFalseDecisionRefuses() {""")
A = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/AnchorContractTest.java"
edit(A, [
    (""" static PluginWindow mount(Endpoint e,UiView view,String id){var w=e.windows().create(new WindowSpec(id+".owner","Owner",new Dimension(300,200),false));w.setContent(view);w.show();return w;}""",
     """ /** Mounts {@code view} where anchors may be taken from it: a content tab of the plugin, which closing disposes. */
 static ContentTab mount(Endpoint e,UiView view,String id){return e.windows().openTab(new ContentSpec(id+".owner","Owner"),null,view);}""", 1),
    (""" static void title(PluginWindow window,Optional<UiView> view){window.setTitleActions(view);}
""", "", 1)])
span(A, " @Test void titleAndBodyHaveIndependentAdmissionUpdateAndLifetime()throws Exception{",
     " @Test void closedSurfaceImmediatelyRunsLateCleanupRegistrationOnce()throws Exception{SwingUtilities.invokeAndWait(()->{\n",
     " /** A dialog is a surface: a cleanup registered after it closed runs at once, and once. */\n"
     " @Test void closedSurfaceImmediatelyRunsLateCleanupRegistrationOnce()throws Exception{SwingUtilities.invokeAndWait(()->{\n")
edit(A, [(
"""  try(var f=fixture()){var e=f.endpoint("source");var view=e.kit().create(actions(),event->{});var w=mount(e,view,"source");w.close();""",
"""  try(var f=fixture()){var e=f.endpoint("source");var view=e.kit().create(actions(),event->{});mount(e,view,"source");
   var w=e.windows().dialog(new DialogSpec("Surface",view.anchor("lock"),false));w.setContent(e.kit().create(picker(ControlState.READY),event->{}));w.show();w.close();""", 1)])
T = "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/"
edit(T + "FakeUiTest.java", [(
"""            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("dev.x.tool.other", "Other")
                .withContext(ShortcutContext.window("dev.y.manager")), invoked -> { }));""",
"""            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("dev.x.tool.other", "Other")
                .withContext(ShortcutContext.panel("dev.y.hosts")), invoked -> { }));""", 1)])
F = T + "FakePanelsAndWindowsTest.java"
edit(F, [
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    ("""    @Test void fileSelectionWorksForWindowsAndDialogsAndRequiresALiveShownOwner(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var window = context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true));
            var dialog = context.windows().dialog(new DialogSpec("Edit", window, true));
            var initial = java.util.Optional.of(directory.resolve("original"));
            for (var surface : java.util.List.<dev.jasper.sdk.ui.WindowSurface>of(window, dialog)) {
                assertThatIllegalStateException().isThrownBy(() -> surface.chooseFile("Choose key", initial));
                surface.show();
                host.queueFileSelection(java.util.Optional.of(directory.resolve("selected")));
                assertThat(surface.chooseFile("Choose key", initial)).contains(directory.resolve("selected"));
                assertThat(surface.chooseFile("Choose key", initial)).isEmpty();
                assertThatIllegalArgumentException().isThrownBy(() -> surface.chooseFile(" ", initial));
            }
            window.close();""", """    @Test void fileSelectionWorksForDialogsAndRequiresALiveShownOwner(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var tab = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, KitFixtures.view(context));
            var dialog = context.windows().dialog(new DialogSpec("Edit", tab, true));
            var initial = java.util.Optional.of(directory.resolve("original"));
            assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", initial));
            dialog.show();
            host.queueFileSelection(java.util.Optional.of(directory.resolve("selected")));
            assertThat(dialog.chooseFile("Choose key", initial)).contains(directory.resolve("selected"));
            assertThat(dialog.chooseFile("Choose key", initial)).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> dialog.chooseFile(" ", initial));
            tab.close();""", 1),
    ("""    @Test void recordsPanelsRailAndWindows() throws Exception {""", """    @Test void recordsPanelsRailAndDialogs() throws Exception {""", 1),
    ("""            var manager = context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true));
            assertThat(context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true))).isSameAs(manager);
            var veto = manager.onClosing(() -> false);
            context.windows().dialog(new DialogSpec("Unlock", manager, true)).show();
            assertThat(host.windows()).containsExactly("dev.x.tool.manager|Manager|false", "dialog|Unlock|true");
            manager.show();
            assertThat(host.requestClose("dev.x.tool.manager")).isFalse();
            veto.close();
            assertThat(host.requestClose("dev.x.tool.manager")).isTrue();
            assertThat(host.windows()).as("the dialog went with its owner").isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().dialog(new DialogSpec("Late", manager, true)));""",
     """            var manager = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, KitFixtures.view(context));
            var unlock = context.windows().dialog(new DialogSpec("Unlock", manager, true));
            var veto = unlock.onClosing(() -> false);
            unlock.show();
            assertThat(host.windows()).containsExactly("dialog|Unlock|true");
            assertThat(host.requestClose("dialog")).isFalse();
            veto.close();
            manager.close();
            assertThat(host.windows()).as("the dialog went with its owner").isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().dialog(new DialogSpec("Late", manager, true)));""", 1)])
C = T + "FakeChooserTest.java"
edit(C, [
    ("import dev.jasper.sdk.ui.WindowSpec;\nimport java.awt.Dimension;\n", "", 1),
    ("""            var owner = context.windows().create(new WindowSpec("dev.x.chooser.window", "Upload", new Dimension(500,400), true));
            owner.show();""", """            // A child window, which closes with its last tab (workspace tabs spec 6.4).
            host.addTerminalWindow();
            UUID child = host.addTerminalWindow();
            UUID pane = host.addTerminalPane(host.addTerminalTab(child, "zsh"), dev.jasper.sdk.terminal.PaneInfo.unknown());
            var owner = context.terminals().window(child).orElseThrow();""", 1),
    ("""            host.duringPathSelection(owner::close);""", """            host.duringPathSelection(() -> host.closeTerminalPane(pane));""", 1)])
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
T = "jasper-app/src/test/java/dev/jasper/app/"
P = T + "plugins/"
edit(T + "commands/KeyContextTest.java", [
    ("""        assertThatIllegalArgumentException().isThrownBy(() -> KeyContext.window(" "));""",
     """        assertThatIllegalArgumentException().isThrownBy(() -> KeyContext.panel(" "));""", 1),
    ("""        assertThat(KeyContext.TERMINAL.conflictsWith(KeyContext.window("dev.x.w"))).isFalse();""",
     """        assertThat(KeyContext.TERMINAL.conflictsWith(KeyContext.panel("dev.x.w"))).isFalse();""", 1)])
edit(T + "windows/AuxiliaryWindowsTest.java", [("""    @Test void quitAsksOnlyWindowGuardsInCreationOrderAndClosesNothing() {
        List<String> asked = new ArrayList<>();
        boolean[] allow = {false};
        AuxiliarySurface first = windows.window("dev.x.first", "First", new Dimension(100, 100), false);
        first.onClosing(() -> { asked.add("first"); return true; });
        AuxiliarySurface second = windows.window("dev.x.second", "Second", new Dimension(100, 100), false);
        second.onClosing(() -> { asked.add("second"); return allow[0]; });
        second.onClosing(() -> { throw new IllegalStateException("boom"); });
        windows.dialog("Details", false, second).onClosing(() -> { asked.add("dialog"); return false; });
        windows.overlay("Connecting", UUID.randomUUID());
        assertThat(windows.mayQuit()).isFalse();
        assertThat(asked).containsExactly("first", "second");
        allow[0] = true;
        asked.clear();
        assertThat(windows.mayQuit()).isTrue();
        assertThat(asked).containsExactly("first", "second");
        assertThat(windows.open()).hasSize(4).noneMatch(AuxiliarySurface::closed);
    }
""", "", 1)])
edit(P + "HostedAnchorContractTest.java", [(
    "var ui=new HostedUi(id,new dev.jasper.app.contributions.Contributions(),containment,",
    "var model=new dev.jasper.app.contributions.Contributions();"
    "model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(java.util.UUID.randomUUID(),tab->{},tab->{},tab->{},()->{}));"
    "var ui=new HostedUi(id,model,containment,", 1)])
edit(P + "HostedAnchorTest.java", [
    ("""            var ui=new HostedUi("test.picker",new dev.jasper.app.contributions.Contributions(),""",
     """            // A workspace for the view's content tab, where it may grant anchors.
            var model=new dev.jasper.app.contributions.Contributions();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(java.util.UUID.randomUUID(),tab -> {},tab -> {},tab -> {},() -> {}));
            var ui=new HostedUi("test.picker",model,""", 1),
    ("""            var window=ui.windows().create(new WindowSpec("test.picker.owner","Owner",new java.awt.Dimension(300,200),false));
            window.setContent(view);window.show();""",
     """            var window=ui.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("owner","Owner"),null,view);""", 1)])
C = P + "HostedChooserTest.java"
edit(C, [
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    ("""    @Test void auxiliaryOwnerCloseAndPluginStopDisposeAndDiscardResults() {
        var owner = ui.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(500,400), true));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.windows().chooseFiles(owner, "Upload", Optional.empty()));
        owner.show();
        during.set(owner::close);
        assertThat(ui.windows().chooseFiles(owner, "Upload", Optional.empty())).isEmpty();
        assertThat(disposed).isTrue();
        var terminal = terminals.windowHandle(fixture.addWindow());
        disposed.set(false);
        during.set""", """    @Test void pluginStopDisposesThePickerAndDiscardsItsResult() {
        var terminal = terminals.windowHandle(fixture.addWindow());
        during.set""", 1),
    ("""        assertThat(requests.getLast().initial()).isEmpty();
        var owner = ui.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(500, 400), true));
        owner.show();
        fixture.activateWindow(id);
        ui.windows().chooseFiles(owner, "Open", Optional.empty());
        assertThat(requests.getLast().terminalFolder()).as("a plugin window uses the active pane").contains(Location.local(Path.of("/work/project")));
    }""", """        assertThat(requests.getLast().initial()).isEmpty();
    }""", 1)])
U = P + "HostedUiTest.java"
edit(U, [
    ("""    @Test void fileChooserIsAvailableOnBothSurfacesAndStopsWithThePlugin() {
        var window = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        var dialog = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Edit", window, true));
        for (var surface : List.<dev.jasper.sdk.ui.WindowSurface>of(window, dialog)) {
            assertThatIllegalStateException().isThrownBy(() -> surface.chooseFile("Choose key", Optional.empty()));
            surface.show();
            assertThat(surface.chooseFile("Choose key", Optional.empty())).isEmpty();
        }""", """    @Test void fileChooserIsAvailableOnADialogAndStopsWithThePlugin() {
        var dialog = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Edit", terminals.windowHandle(terminalFixture.addWindow()), true));
        assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", Optional.empty()));
        dialog.show();
        assertThat(dialog.chooseFile("Choose key", Optional.empty())).isEmpty();""", 1),
    ("""    @Test void windowsAndDialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {
        var manager = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        assertThat(ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true)))
            .as("an open singleton is the same window").isSameAs(manager);
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.other.window", "Foreign", new java.awt.Dimension(10, 10), false)));
        manager.setContent(view());
        manager.show();
        List<String> events = new ArrayList<>();
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        var prompt = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", manager, true));
        var trust = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Trust?", terminals.windowHandle(terminalFixture.addWindow()), false));
        assertThatIllegalArgumentException().as("an owner this application did not create")
            .isThrownBy(() -> ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Bad", new dev.jasper.sdk.WindowOwner() { }, true)));
        assertThat(auxiliary.open()).hasSize(3);
        assertThat(auxiliary.open().get(0).requestClose()).as("a throwing guard is contained and allows the close").isTrue();
        assertThat(events).containsExactly("closed");
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);
        assertThat(auxiliary.open()).as("the owner took its dialog with it").hasSize(1);
        ui.closeAll();
        assertThat(auxiliary.open()).isEmpty();
        assertThatCode(() -> { prompt.close(); trust.close(); manager.setTitle("after close"); }).doesNotThrowAnyException();
    }""", """    @Test void dialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {
        var owner = terminals.windowHandle(terminalFixture.addWindow());
        List<String> events = new ArrayList<>();
        var prompt = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", owner, true));
        prompt.setContent(view());
        prompt.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        prompt.onClosed(() -> events.add("closed"));
        var trust = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Trust?", owner, false));
        assertThatIllegalArgumentException().as("an owner this application did not create")
            .isThrownBy(() -> ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Bad", new dev.jasper.sdk.WindowOwner() { }, true)));
        assertThat(auxiliary.open()).hasSize(2);
        assertThat(auxiliary.open().get(0).requestClose()).as("a throwing guard is contained and allows the close").isTrue();
        assertThat(events).containsExactly("closed");
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);
        assertThat(auxiliary.open()).hasSize(1);
        ui.closeAll();
        assertThat(auxiliary.open()).isEmpty();
        assertThatCode(() -> { prompt.close(); trust.close(); prompt.setTitle("after close"); }).doesNotThrowAnyException();
    }""", 1)])
K = P + "KitMountingTest.java"
edit(K, [
    ("""            var window=ui.windows().create(new WindowSpec("test.kit.extra","Extra",new Dimension(400,300),false));window.setContent(incoming);""",
     """            var window=ui.windows().dialog(new DialogSpec("Extra",tab,false));window.setContent(incoming);""", 1),
    ("""            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, null);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            var owner = ui.windows().create(new WindowSpec("test.kit.owner", "Owner", new Dimension(400, 300), true));
            owner.show();""", """            var fixture = new TerminalFixture();
            var terminals = new HostedTerminals("test.kit", new CapabilityGate("test.kit", java.util.Set.of()), fixture.registry, Runnable::run, () -> true,
                () -> true, new HostedSessions("test.kit", containment, Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, terminals);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            var owner = terminals.windowHandle(fixture.addWindow());""", 1)])
span(K, "    @Test void singletonReplacementRejectsForeignAndClosesOwnedContentExactlyOnce() throws Exception {",
     "    /** Plan 2 Task 7 host contract: closing a dialog that holds a Vault-style password prompt. */",
     "    /** Plan 2 Task 7 host contract: closing a dialog that holds a Vault-style password prompt. */")
E = P + "StagedEditorHost.java"
edit(E, [
    ("import dev.jasper.sdk.ui.PluginWindow;\n", "", 1),
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    (""" * Saving through the plugin's own {@code SaveWait}), owned by a plugin window of the Editor's (production owns them by
 * the selected content tab; the owner does not change the dialog's content).""",
     """ * Saving through the plugin's own {@code SaveWait}), owned by the main window (production owns them by the selected
 * content tab, which resolves to its window; the owner does not change the dialog's content).""", 1),
    ("    final PluginWindow owner;\n", "    final dev.jasper.sdk.terminal.WindowHandle owner;\n", 1),
    ("""        owner = onEdtValue(() -> {
            var window = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(900, 600), true));
            window.show();
            return window;
        });""", """        owner = onEdtValue(() -> context.terminals().windows().getFirst());""", 1)])
W = P + "WorkspaceContentRouteTest.java"
edit(W, [
    ("""            var window = ui.windows().create(new WindowSpec("test.kit.form", "Form", new Dimension(400, 300), false));
            var form = kit.create(new ViewSpec(new Nodes.Text("label", ControlState.READY, "Name", Nodes.TextRole.BODY)), e -> {});
            window.setContent(form);
            KitView formComponent = ((HostedKitView) form).component();
            assertThat(formComponent.route()).as("windows keep chrome").isEqualTo(ThemeRoute.CHROME);
            assertThat(formComponent.isOpaque()).isFalse();
            assertThat(named(formComponent, "label", JLabel.class).getForeground()).isEqualTo(chromeText);

            tab.close(); assertThat(disposed.get()).isEqualTo(1);
            window.close(); ui.closeAll(); kit.close();""", """            tab.close(); assertThat(disposed.get()).isEqualTo(1);
            ui.closeAll(); kit.close();""", 1),
    ("""            var window = ui.windows().create(new WindowSpec("test.kit.form", "Form", new Dimension(400, 300), false));
            window.setContent(view);
            window.close(); ui.closeAll(); kit.close();""", """            ui.closeAll(); kit.close();""", 1)])
A = T + "application/"
B = A + "JasperApplicationBackgroundTest.java"
span(B, "    private static final String GUARD_FIXTURE = \"\"\"",
     "    @Test void aReopenAfterAnUpdateRelaunchesTheNewBuildFromTheBackground() throws Exception {",
     "    @Test void aReopenAfterAnUpdateRelaunchesTheNewBuildFromTheBackground() throws Exception {")
edit(B, [
    ("""    @Test void aContentTabThatRefusesToCloseKeepsEverythingOpen() throws Exception {
        var asked = new AtomicInteger();
        JasperApplication application = settled(() -> { }, true);
        try {""", """    @Test void aContentTabThatRefusesToCloseKeepsEverythingOpenUntilItAgrees() throws Exception {
        var terminations = new AtomicInteger();
        var asked = new AtomicInteger();
        var allow = new AtomicBoolean();
        JasperApplication application = settled(terminations::incrementAndGet, true);
        try {""", 1),
    ("""                content.guard("dev.example.editor", (entries, batch) -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("the editor was asked, as when its window closes").hasValue(1);
            assertThat(dock).as("nothing closed, so Jasper stays in the Dock").isEmpty();
        } finally {""", """                content.guard("dev.example.editor", (entries, batch) -> {
                    asked.incrementAndGet();
                    return CompletableFuture.completedFuture(allow.get());
                });
            });
            edt(application::quitRequested);
            assertThat(asked).as("the editor was asked, as when its window closes").hasValue(1);
            assertThat(dock).as("nothing closed, so Jasper stays in the Dock").isEmpty();
            allow.set(true);
            edt(application::quitRequested);
            assertThat(asked).hasValue(2);
            assertThat(dock).as("it closes once the editor agrees").containsExactly(false);
            assertThat(terminations).as("and the process stays").hasValue(0);
        } finally {""", 1)])
edit(B, [
    ("""        // "An upgrade must never take a running terminal down": any open window, a plugin's included, keeps the running
        // build, and the reopen goes on as an ordinary one.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "guard.jar", PluginJars.descriptor("dev.example.guard", "1.0.0", "fix.guard.Main"),
            Map.of("fix.guard.Main", GUARD_FIXTURE), List.of());""", """        // "An upgrade must never take a running terminal down": any open window, an auxiliary one included, keeps the
        // running build, and the reopen goes on as an ordinary one.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));""", 1),
    ("""        // Spec 5.3: with windows open the owner carries the request out itself and says it was updated.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "guard.jar", PluginJars.descriptor("dev.example.guard", "1.0.0", "fix.guard.Main"),
            Map.of("fix.guard.Main", GUARD_FIXTURE), List.of());""", """        // Spec 5.3: with windows open the owner carries the request out itself and says it was updated.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));""", 1)])
from edits import regex
regex(B, r"                application\.startPlugins\(null, dev, false, dirs\);\n                application\.restartPlanner",
      "                application.startPlugins(dirs);\n                application.auxiliary().window(\"test.window\", \"Window\", new java.awt.Dimension(100, 100), false);\n                application.restartPlanner", 2)
regex(B, r"            // The plugin's window is open; ", "            // An auxiliary window is open; ", 2)
G = A + "JasperApplicationPluginsTest.java"
span(G, "    private static final String GUARD_FIXTURE = \"\"\"", "    private static final String QUIT_FIXTURE = \"\"\"", "    private static final String QUIT_FIXTURE = \"\"\"")
span(G, "    @Test void quitNowDoesNotAskPluginWindows() throws Exception {", "    @Test void anUnknownCommandLineLeavesJasperRunning() throws Exception {",
     """    @Test void quitNowDoesNotAskTheQuitGuards() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "quit.jar", PluginJars.descriptor("dev.example.quit", "1.0.0", "fix.quit.Main"),
            Map.of("fix.quit.Main", QUIT_FIXTURE), List.of());
        Path data = dirs.plugins().resolve("dev.example.quit/data");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(application[0]::quitNow);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(data.resolve("asked")).as("the guard was never consulted").doesNotExist();
    }

    @Test void anUnknownCommandLineLeavesJasperRunning() throws Exception {""")
edit(P + "AppContractTest.java", [
    ("""            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::requestClose).orElse(false));
            }
            @Override public boolean closeCommand(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::closeCommand).orElse(false));
            }
            @Override public boolean requestQuit() { return onEdtValue(() -> contributions.tabs().mayClose(contributions.tabs().hosted()) && auxiliary.mayQuit() && contributions.quitGuards().mayQuit()); }""",
     """            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream()
                    .filter(surface -> (surface.kind() == AuxiliarySurface.Kind.OVERLAY ? "overlay" : "dialog").equals(windowId)).findFirst()
                    .map(surface -> surface.kind() != AuxiliarySurface.Kind.OVERLAY && surface.requestClose()).orElse(false));
            }
            @Override public boolean requestQuit() { return onEdtValue(() -> contributions.tabs().mayClose(contributions.tabs().hosted()) && contributions.quitGuards().mayQuit()); }""", 1),
    ("""                    .map(surface -> (switch (surface.kind()) { case DIALOG -> "dialog"; case OVERLAY -> "overlay"; case WINDOW -> surface.id(); })""",
     """                    .map(surface -> (surface.kind() == AuxiliarySurface.Kind.OVERLAY ? "overlay" : "dialog")""", 1),
    ("""                    .filter(surface -> surface.kind() != AuxiliarySurface.Kind.WINDOW && surface.title().equals(title))""",
     """                    .filter(surface -> surface.title().equals(title))""", 1)])
edit(P + "AppContractTest.java", [("    /** UI thread: plugin windows over shells that touch no native window. */",
                                   "    /** UI thread: plugin dialogs over shells that touch no native window. */", 1)])
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
D = "plugins/editor/src/test/java/dev/jasper/editor/ui/DialogsTest.java"
edit(D, [
    ("import dev.jasper.sdk.ui.WindowSpec;\nimport java.awt.Dimension;\n", "", 1),
    ("""        edt(() -> {
            var window = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(100, 100), true));
            window.show();
            owner = window;
        });""", """        // The main window owns the dialogs, as the selected content tab's window does in the Editor.
        host.addTerminalWindow();
        edt(() -> owner = context.terminals().windows().getFirst());""", 1),
    ('assertThat(host.windows()).as("a finished save needs no dialog").hasSize(1);', 'assertThat(host.windows()).as("a finished save needs no dialog").isEmpty();', 1),
    ('assertThat(host.windows()).contains("dev.jasper.editor.window|Editor|true").hasSize(2);', 'assertThat(host.windows()).hasSize(1);', 1)])
regex(D, r'containsExactly\("dev\.jasper\.editor\.window\|Editor\|true"\)', "isEmpty()", 5)
PY
```

- [ ] **Step 5: The application**

`KeyContext.WINDOW`, its case and plan 4a's comment on it in `WindowContent`, the shortcut help's window row,
`HostedUi`'s plugin windows, `AuxiliaryWindows.mayQuit` and the comments that still name plugin windows:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "commands/KeyContext.java", [
    (""" * a terminal pane, its find bar included; the owned kinds apply inside one plugin's content tabs, one panel or one
 * plugin window, which never nest. A stroke may belong to several actions; the most specific context containing the
 * focus owner wins.
 *
 * @param kind the kind of place
 * @param owner the plugin id for {@link Kind#CONTENT}, the panel or window id for {@link Kind#PANEL} and
 *              {@link Kind#WINDOW}, empty for the others
 */
public record KeyContext(Kind kind, String owner) {
    public enum Kind { GLOBAL, TERMINAL, CONTENT, PANEL, WINDOW }""", """ * a terminal pane, its find bar included; the owned kinds apply inside one plugin's content tabs or one panel, which
 * never nest. A stroke may belong to several actions; the most specific context containing the focus owner wins.
 *
 * @param kind the kind of place
 * @param owner the plugin id for {@link Kind#CONTENT}, the panel id for {@link Kind#PANEL}, empty for the others
 */
public record KeyContext(Kind kind, String owner) {
    public enum Kind { GLOBAL, TERMINAL, CONTENT, PANEL }""", 1),
    ("""    /** Inside one plugin window. */
    public static KeyContext window(String windowId) { return new KeyContext(Kind.WINDOW, windowId); }

""", "", 1),
    ("            case CONTENT, PANEL, WINDOW -> 2;", "            case CONTENT, PANEL -> 2;", 1)])
edit(M + "workspace/WindowContent.java", [(
    """            case PANEL -> contributed != null && contributed.panelContains(context.owner(), focus);
            // Plugin windows get the Keymap in plan K4; nothing in a terminal window is inside one.
            case WINDOW -> false;
""", """            case PANEL -> contributed != null && contributed.panelContains(context.owner(), focus);
""", 1)])
edit(M + "shortcuthelp/ShortcutCatalog.java", [
    ("    /** The Where column: Global, Terminal, a plugin's tabs, a panel or a window. */", "    /** The Where column: Global, Terminal, a plugin's tabs or a panel. */", 1),
    ("""            case PANEL -> panels.getOrDefault(context.owner(), context.owner()) + " panel";
            case WINDOW -> context.owner() + " window";
""", """            case PANEL -> panels.getOrDefault(context.owner(), context.owner()) + " panel";
""", 1),
    ("            case CONTENT, PANEL, WINDOW -> \"Only in the \" + where(context, plugins, panels);", "            case CONTENT, PANEL -> \"Only in the \" + where(context, plugins, panels);", 1)])
H = M + "plugins/HostedUi.java"
edit(H, [
    ("import dev.jasper.sdk.ui.PluginWindow;\n", "", 1),
    ("import dev.jasper.sdk.ui.WindowSpec;\n", "", 1),
    ("""            spec.context().id().ifPresent(owned -> requireNamespace(owned,
                spec.context().kind() == ShortcutContext.Kind.PANEL ? "A panel context" : "A window context"));""",
     """            spec.context().id().ifPresent(owned -> requireNamespace(owned, "A panel context"));""", 1),
    ("""            case PANEL -> KeyContext.panel(context.id().orElseThrow());
            case WINDOW -> KeyContext.window(context.id().orElseThrow());
""", """            case PANEL -> KeyContext.panel(context.id().orElseThrow());
""", 1),
    ("    /** One SDK view of an application surface; windows and dialogs differ only in their SDK type. */",
     "    /** One SDK view of an application surface: a plugin's dialog. */", 1),
    ("""    private final java.util.Map<AuxiliarySurface, OwnedWindow> ownedWindows = new java.util.HashMap<>();

""", "", 1),
    ("""        if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window && window.surface.shown()) {
            choice = new dev.jasper.app.windows.PathChoice(null, window.surface, title, initial, mode, multiple,
                terminalStart(null, sources), suggestedName, sources);
            ownerClosed = wrap(window.surface.onClosed(cancel));
        } else if (owner instanceof WindowHandle terminal && terminals.ownsOpenWindow(terminal)) {""",
     """        if (owner instanceof WindowHandle terminal && terminals.ownsOpenWindow(terminal)) {""", 1),
    ("""        } else throw new IllegalArgumentException("Picker requires a shown plugin window or an open Jasper window from this host");""",
     """        } else throw new IllegalArgumentException("Picker requires an open Jasper window or content tab from this host");""", 1),
    ("""            @Override public PluginWindow create(WindowSpec spec) {
                guard("create");
                requireNamespace(spec.id(), "A window");
                AuxiliarySurface surface = windows.window(spec.id(), spec.title(), spec.preferredSize(), spec.singleton());
                OwnedWindow existing = ownedWindows.get(surface);
                if (existing != null) return existing;
                surface.contentSize(spec.sizeBasis() == WindowSpec.SizeBasis.CONTENT);
                var window = new OwnedWindow(surface);
                ownedWindows.put(surface, window);
                surface.onClosed(() -> ownedWindows.remove(surface));
                closers.add(surface::close);
                return window;
            }
""", "", 1),
    ("""                } else if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window)
                    surface = overlay ? windows.modalOverlay(spec.title(), window.surface) : windows.dialog(spec.title(), spec.modal(), window.surface);
                else if (owner instanceof WindowHandle terminalWindow""", """                } else if (owner instanceof WindowHandle terminalWindow""", 1),
    ("""                else throw new IllegalArgumentException("A dialog's owner must be an open window of this plugin or a Jasper window");""",
     """                else throw new IllegalArgumentException("A dialog's owner must be a Jasper window, a content tab or an anchor from this host");""", 1)])
span(H, "    private static dev.jasper.sdk.ui.ViewSpec titleSpec(dev.jasper.sdk.ui.ViewSpec value) {",
     "    private final class OwnedDialog extends Surface implements PluginDialog { OwnedDialog(AuxiliarySurface surface) { super(surface); } }\n",
     "    private final class OwnedDialog extends Surface implements PluginDialog { OwnedDialog(AuxiliarySurface surface) { super(surface); } }\n")
edit(M + "windows/AuxiliaryWindows.java", [(
    """    /**
     * Quit and restart ask every open window's closing guards first, in creation order; the first refusal
     * cancels them. Dialogs and overlays are not asked, and nothing closes.
     *
     * @return whether the quit may go ahead
     */
    public boolean mayQuit() {
        for (AuxiliarySurface surface : List.copyOf(open))
            if (surface.kind() == AuxiliarySurface.Kind.WINDOW && !surface.mayClose()) return false;
        return true;
    }

""", "", 1)])
edit(M + "application/JasperApplication.java", [(
    """    /** Content tabs' and plugin windows' closing guards: what closing every window asks. Quit guards are not asked. EDT. */
    private boolean windowsMayClose() {
        var requested = contributions.tabs().hosted();
        return contributions.tabs().mayClose(requested) && (auxiliary == null || auxiliary.mayQuit())
            && requested.equals(contributions.tabs().hosted());""",
    """    /** Content tabs' closing guards: what closing every window asks. Quit guards are not asked. EDT. */
    private boolean windowsMayClose() {
        var requested = contributions.tabs().hosted();
        return contributions.tabs().mayClose(requested) && requested.equals(contributions.tabs().hosted());""", 1)])
from edits import regex
J = M + "application/JasperApplication.java"
edit(J, [("    /** Plugin windows', content tabs' and quit guards, asked before quit or restart changes anything and before the exit deadline is armed. */",
          "    /** Content tabs' and quit guards, asked before quit or restart changes anything and before the exit deadline is armed. */", 1)])
regex(J, r"\bpluginWindowsMayClose\b", "guardsAllowQuit", 3)
# Plugin windows are gone: an auxiliary window (About, Licenses, Settings) is what keeps Jasper running without a Jasper window.
regex(J, r"\bpluginWindowsOpen\b", "auxiliaryOpen", 4)
# The comments that still name plugin windows.
edit(J, [
    ("    /** Quits a Jasper that is not resident once nothing keeps it: no terminal or plugin window, and {@code busy} false. EDT. */",
     "    /** Quits a Jasper that is not resident once nothing keeps it: no Jasper or auxiliary window, and {@code busy} false. EDT. */", 1),
    ("     * like the problems posted directly. With only a plugin window open Jasper is not idle, because cards target",
     "     * like the problems posted directly. With only an auxiliary window open Jasper is not idle, because cards target", 1),
    ("     *     true. It also returns true, with nothing changed, when a plugin window's closing guard refuses the quit.",
     "     *     true. It also returns true, with nothing changed, when a content tab's closing guard or a quit guard refuses.", 1),
    ("    /** Asks plugin windows first; any refusal leaves Jasper running with nothing changed. */",
     "    /** Asks the content tabs' and quit guards first; any refusal leaves Jasper running with nothing changed. */", 1),
    ("     * Closes every window through the guards closing them asks (content tabs, plugin windows) and keeps the process, its",
     "     * Closes every window through the guards closing them asks (content tabs) and keeps the process, its", 1),
    ("    /** Quits without asking plugin windows: start-up rollback and programmatic exits, which no user asked for. EDT. */",
     "    /** Quits without asking any guard: start-up rollback and programmatic exits, which no user asked for. EDT. */", 1)])
edit(M + "contributions/QuitGuards.java", [(
    " * Plugins' quit and restart guards, asked in registration order after plugin windows and content tabs, before",
    " * Plugins' quit and restart guards, asked in registration order after content tabs, before", 1)])
regex(M + "plugins/PluginRuntime.java", r"@param windows builds plugin windows and dialogs", "@param windows builds plugin dialogs and overlays", 3)
PY
```

The auxiliary windows' title-action, content-size and close-command chain served plugin windows only (Rulings 13).
First see that nothing else calls it:

```bash
git grep -c -E '\.(setTitleActions|onCloseCommand|contentSize|titleStrip|closeCommand)\(' -- jasper-app/src jasper-ui/src jasper-sdk-testkit/src plugins
```

Expected, exactly: `AuxiliarySurface.java:1` (its own `titleStrip` call), `NativeShells.java:3`, `Menus.java:1`, and the
tests `AuxiliaryWindowsTest.java:5`, `NativeShellsChromeTest.java:2`, `ShellChromeTest.java:1`,
`KitViewToolbarStripTest.java:2`. Any other file means a caller this plan did not see: stop and report it. Then the
chain goes, with its tests' cases (the title-actions and close-command tests go; `ShellChromeTest` keeps Close Window
through the guards; the toolbar strip test no longer compares with a title strip):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
# With plugin windows gone nothing fills a window's title slot, sizes a window by its content or handles its Close
# Window: the chain that served them goes (workspace tabs plan 4b Rulings 13).
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "windows/AuxiliarySurface.java", [
    ("    private final List<BooleanSupplier> closeCommands = new ArrayList<>();\n", "", 1),
    ("""    private final JPanel titleActions = new JPanel(new BorderLayout());
    private Runnable titleActionsChanged = () -> { };
    /** Stable title slot, always outside the body holder. */
    public JComponent titleActions() { return titleActions; }
    public void setTitleActions(JComponent content) {
        if (closed) return;
        titleActions.removeAll();
        if (closed) return;
        // Kit content in the title slot takes the shared title-strip presentation (menu indicators, control surface).
        if (content instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()) view.titleStrip(true);
        if (content != null) titleActions.add(content, BorderLayout.CENTER);
        titleActions.setVisible(content != null); titleActionsChanged();
    }
    public void titleActionsChanged() { titleActions.revalidate(); titleActions.repaint(); titleActionsChanged.run(); }
    void onTitleActionsChanged(Runnable handler) { titleActionsChanged = handler; }
""", "", 1),
    ("""    private boolean contentSize, attachedSheet;
    public boolean contentSize() { return contentSize; }
    public void contentSize(boolean value) { contentSize = value; }
""", """    private boolean attachedSheet;
""", 1),
    ("""        guards.clear();
        closeCommands.clear();
""", """        guards.clear();
""", 1)])
span(A + "windows/AuxiliarySurface.java", """    /**
     * The window's Close Window command: handlers newest first;""", """        return new Subscription(() -> closeCommands.remove(handler));
    }

""", "")
N = A + "windows/NativeShells.java"
edit(N, [
    ("            else { frame.setSize(initialSize(surface, frame.getSize(), surface.holder().getSize(), usableBounds(frame))); frame.setLocationByPlatform(true); }",
     "            else { frame.setSize(initialSize(surface, usableBounds(frame))); frame.setLocationByPlatform(true); }", 1),
    ("""    /** Content default adds the measured actual chrome delta once; remembered bounds bypass this path. */
    static java.awt.Dimension initialSize(AuxiliarySurface surface, java.awt.Dimension packed, java.awt.Dimension body, Rectangle usable) {
        var requested = surface.preferredSize();
        if (surface.contentSize()) {
            requested.width += Math.max(0, packed.width - body.width);
            requested.height += Math.max(0, packed.height - body.height);
        }
        return""", """    /** The window's preferred size within the usable screen; remembered bounds bypass this path. */
    static java.awt.Dimension initialSize(AuxiliarySurface surface, Rectangle usable) {
        var requested = surface.preferredSize();
        return""", 1),
    ("        @Override public void close() { surface.onTitleActionsChanged(() -> {}); body.close(); if (bar != null) bar.close(); }",
     "        @Override public void close() { body.close(); if (bar != null) bar.close(); }", 1),
    ("""        MacTitleBar bar = MacTitleBar.install(root, surface.holder(), () -> { }, supported);
        surface.titleActions().setOpaque(false);
        surface.titleActions().setVisible(surface.titleActions().getComponentCount() > 0);
        if (bar == null && surface.kind() == AuxiliarySurface.Kind.WINDOW) {
            var content = new JPanel(new java.awt.BorderLayout());
            var toolbar = new JPanel(new java.awt.BorderLayout());
            toolbar.add(surface.titleActions(), java.awt.BorderLayout.EAST);
            toolbar.setVisible(surface.titleActions().isVisible());
            content.add(toolbar, java.awt.BorderLayout.NORTH); content.add(surface.holder(), java.awt.BorderLayout.CENTER);
            root.setContentPane(content);
            surface.onTitleActionsChanged(() -> { toolbar.setVisible(surface.titleActions().isVisible()); content.revalidate(); content.repaint(); });
        }
        if (bar != null) {
            bar.setStyle(surface.kind() == AuxiliarySurface.Kind.WINDOW ? MacTitleBar.Style.WINDOW : MacTitleBar.Style.DIALOG);
            if (surface.kind() == AuxiliarySurface.Kind.WINDOW) {
                bar.setAccessory(surface.titleActions()); surface.onTitleActionsChanged(bar::accessoryChanged);
            }
            bar.setTitle(surface.title());""", """        MacTitleBar bar = MacTitleBar.install(root, surface.holder(), () -> { }, supported);
        if (bar != null) {
            bar.setStyle(surface.kind() == AuxiliarySurface.Kind.WINDOW ? MacTitleBar.Style.WINDOW : MacTitleBar.Style.DIALOG);
            bar.setTitle(surface.title());""", 1),
    ("if (!dev.jasper.app.platform.WindowInput.captureShortcut(surface.holder(), close.getAccelerator())) surface.closeCommand();",
     "if (!dev.jasper.app.platform.WindowInput.captureShortcut(surface.holder(), close.getAccelerator())) surface.requestClose();", 1),
    ("                if (!dev.jasper.app.platform.WindowInput.captureShortcut(root, key)) surface.closeCommand();",
     "                if (!dev.jasper.app.platform.WindowInput.captureShortcut(root, key)) surface.requestClose();", 1)])
T = A + "platform/MacTitleBar.java"
edit(T, [
    ("    private JComponent header, accessory;", "    private JComponent header;", 1),
    ("""    /** Auxiliary actions retain their kit borders and identity; the host still owns the centered title. */
    public void setAccessory(JComponent value) {
        if (closed || header != null || accessory != null) throw new IllegalStateException("Title accessory already installed");
        accessory = java.util.Objects.requireNonNull(value); add(value);
        value.addPropertyChangeListener(headerChanged);
        headerGeometryChanged();
    }
    /** Action updates change layout within existing outer bounds. */
    public void accessoryChanged() { if (!closed) headerGeometryChanged(); }

""", "", 1),
    ("""    private int accessoryHeight() { return accessory != null && accessory.isVisible() ? accessory.getPreferredSize().height : 0; }

""", "", 1),
    ("""            case WINDOW -> Math.max(metric(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_TITLE_HEIGHT)
                + metric(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE), accessoryHeight());
            case DIALOG -> Math.max(metric(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_TITLE_HEIGHT), accessoryHeight());""",
     """            case WINDOW -> metric(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_TITLE_HEIGHT)
                + metric(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE);
            case DIALOG -> metric(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_TITLE_HEIGHT);""", 1),
    ("        if (header == null) return new Dimension(2 * Math.max(safeInset(), accessory != null && accessory.isVisible() ? accessory.getMinimumSize().width + Math.max(UIScale.scale(8), nativeRight) : nativeRight) + UIScale.scale(64), titleHeight());",
     "        if (header == null) return new Dimension(2 * Math.max(safeInset(), nativeRight) + UIScale.scale(64), titleHeight());", 1),
    ("""        int right = Math.max(UIScale.scale(8), nativeRight);
        int actionWidth = accessory != null && accessory.isVisible()
            ? Math.min(accessory.getPreferredSize().width, Math.max(0, getWidth() - safeInset() - right)) : 0;
        int center = titleCenter();
        if (accessory != null) {
            int height = Math.min(Math.max(1, accessory.getPreferredSize().height), getHeight());
            accessory.setBounds(Math.max(0, getWidth() - right - actionWidth), Math.max(0, center - height / 2), actionWidth, height);
        }
        int inset = Math.max(Math.min(safeInset(), getWidth()), actionWidth + right);""", """        int right = Math.max(UIScale.scale(8), nativeRight);
        int center = titleCenter();
        int inset = Math.max(Math.min(safeInset(), getWidth()), right);""", 1),
    ("        if (accessory != null) accessory.removePropertyChangeListener(headerChanged);\n", "", 1)])
edit(A + "plugins/SurfaceMount.java", [("record SurfaceMount(AuxiliarySurface surface, boolean title) { }", "record SurfaceMount(AuxiliarySurface surface) { }", 1)])
edit(A + "plugins/HostedUi.java", [("bodyMount = new SurfaceMount(surface, false);", "bodyMount = new SurfaceMount(surface);", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java"
edit(K, [("    record SurfaceMount(FakeWindow surface, boolean title) { }", "    record SurfaceMount(FakeWindow surface) { }", 1),
         ("        final SurfaceMount bodyMount = new SurfaceMount(this, false);", "        final SurfaceMount bodyMount = new SurfaceMount(this);", 1)])
U = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
span(U + "KitView.java", """    /**
     * App-only: this view is a native window's title action strip.""", """    private boolean titleStrip;
""", "")
edit(U + "KitView.java", [(
    "     * show their menu indicator on the borderless control surface, as a title strip's do, and its controls keep the",
    "     * show their menu indicator on the borderless control surface, and its controls keep the", 1)])
edit(U + "Menus.java", [(
    """            // A title or toolbar strip's icon-only menu shows its indicator on the borderless control surface.
            boolean strip = (view.titleStrip() || view.toolbarStrip()) && menu.action().isBlank()""",
    """            // A toolbar strip's icon-only menu shows its indicator on the borderless control surface.
            boolean strip = view.toolbarStrip() && menu.action().isBlank()""", 1)])
edit(U + "Buttons.java", [("        /** Paints its kind's fill without the kind's outline (a title-strip menu surface). */",
                          "        /** Paints its kind's fill without the kind's outline (a toolbar strip's menu surface). */", 1)])
# Their tests.
T = "jasper-app/src/test/java/dev/jasper/app/windows/"
span(T + "AuxiliaryWindowsTest.java", "    @Test void theCloseCommandAsksHandlersNewestFirstThenCloses() {",
     "        assertThat(editor.closed()).isTrue();\n    }\n\n", "")
edit(T + "ShellChromeTest.java", [
    ("""            var commands = new java.util.concurrent.atomic.AtomicInteger();
            surface.onCloseCommand(() -> { commands.incrementAndGet(); return true; });""",
     """            var commands = new java.util.concurrent.atomic.AtomicInteger();
            // Close Window closes as the close button does: through the window's closing guards, which keep it open here.
            surface.onClosing(() -> { commands.incrementAndGet(); return false; });""", 1),
    ("""            assertThat(commands).as("it runs the window's Close Window command").hasValue(1);""",
     """            assertThat(commands).as("it asks the window's closing guards").hasValue(1);""", 1)])
span(T + "NativeShellsChromeTest.java", "    @Test void actualChromeMountsTitleActionsOutsideBodyWithCenteredNativeTitleAndFallback() {", """                    assertThat(title.getX() + title.getWidth()).isLessThanOrEqualTo(surface.titleActions().getX());
                }
            }
        }
    }
""", """    @Test void actualChromeCentresTheNativeTitleOverTheBodyAndFallsBack() {
        for (boolean supported : java.util.List.of(true, false)) {
            var surface = new AuxiliarySurface("test", "Manager", AuxiliarySurface.Kind.WINDOW, false,
                new Dimension(900, 555), null, null, ignored -> { throw new AssertionError(); });
            var root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, surface, supported)) {
                var bar = chrome.bar();
                root.setSize(900, 620); layout(root);
                int bodyTop = javax.swing.SwingUtilities.convertPoint(surface.holder(), 0, 0, root).y;
                assertThat(chrome.body().boundsInRoot()).contains(new java.awt.Rectangle(0, bodyTop, 900, 620 - bodyTop));
                assertThat(NativeShells.initialSize(surface, new java.awt.Rectangle(0, 0, 2000, 1500))).isEqualTo(new Dimension(900, 555));
                assertThat(NativeShells.initialSize(surface, new java.awt.Rectangle(0, 0, 800, 500))).as("within the usable screen")
                    .isEqualTo(new Dimension(800, 500));
                assertThat(root.getSize()).isEqualTo(new Dimension(900, 620));
                if (supported) {
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.isVisible()).isTrue();
                    assertThat(title.getX() * 2 + title.getWidth()).isEqualTo(900);
                }
            }
        }
    }
""")
K2 = "jasper-ui/src/test/java/dev/jasper/ui/swing/KitViewToolbarStripTest.java"
edit(K2, [
    ("/** Plan 3d: a contributed toolbar view is a toolbar strip, which presents an icon-only menu as a title strip does. */",
     "/** Plan 3d: a contributed toolbar view is a toolbar strip, which presents an icon-only menu on the borderless control surface. */", 1),
    ("""    @Test void aToolbarStripPresentsAnIconOnlyMenuAsATitleStripDoes() {
        Edt.run(() -> {
            int[] plain = pixels(view -> { }), title = pixels(view -> view.titleStrip(true)), toolbar = pixels(view -> view.toolbarStrip(true));
            assertThat(title).as("a strip changes the icon-only menu").isNotEqualTo(plain);
            assertThat(toolbar).isEqualTo(title);
        });
    }""", """    @Test void aToolbarStripPresentsAnIconOnlyMenuOnTheBorderlessSurface() {
        Edt.run(() -> {
            int[] plain = pixels(view -> { }), toolbar = pixels(view -> view.toolbarStrip(true));
            assertThat(toolbar).as("a strip changes the icon-only menu").isNotEqualTo(plain);
        });
    }""", 1),
    ("""                assertThat(view.titleStrip()).as("a separate flag").isFalse();
""", "", 1)])
PY
```

```bash
git grep -n -E 'PluginWindow|WindowSpec|Windows#create|KeyContext\.window|ShortcutContext\.window|ShortcutContext\.Kind\.WINDOW|KeyContext\.Kind\.WINDOW|pluginWindows|auxiliary\.mayQuit|setTitleActions|titleActions\(|onCloseCommand|closeCommand|contentSize\(|titleStrip|setAccessory' -- jasper-sdk/src jasper-sdk-testkit/src jasper-app/src jasper-ui/src plugins | grep -v KitCompatibility || echo "none left"
grep -l WINDOW jasper-sdk/src/main/java/dev/jasper/sdk/ui/ShortcutContext.java jasper-app/src/main/java/dev/jasper/app/commands/KeyContext.java || echo "no WINDOW"
```

Expected: `none left` (`KitCompatibility` and its test name the removed members on purpose), then `no WINDOW`.

- [ ] **Step 6: Run the build to verify it passes**

The auxiliary windows' chrome first, with the Enterprise chrome mock tests:
Run: `./gradlew :jasper-ui:test --tests '*KitViewToolbarStripTest' :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*ShellChromeTest' --tests '*NativeShellsChromeTest' --tests '*MacTitleBarTest' --tests '*ChromeRenderTest' --tests '*SettingsChromeTest' --tests '*SettingsChromeMockTest' --tests '*EnterpriseDialogMockTest' --tests '*ClassicCompositionRenderTest' --continue`
Expected: PASS.
Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -F - <<'EOF'
Workspace tabs: plugin windows go

Windows.create, PluginWindow, WindowSpec and the window shortcut context
leave the SDK, the testkit and the application, and KitCompatibility
refuses them as 0.20's. Quit and restart ask the content tabs' and the
quit guards. The auxiliary windows' title-action, content-size and
close-command chain, which only plugin windows used, goes with them.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Panels in the main window only

**Files:**
- Modify (SDK Javadoc): `ui/Panels.java`, `PanelFactory.java`, `PanelHost.java`, `Toolbar.java`, `ToolbarItem.java`,
  `ToolbarViewFactory.java`, `StatusBar.java`, `StatusItem.java`, `StatusProgress.java`
- Modify (testkit): `FakeUi.java`, `FakePluginHost.java`, `contract/ContractHarness.java`,
  `contract/PluginContractTest.java`; the application's `commands/KeyContext.java` (Javadoc)
- Modify (plugins): Remote's `ui/tunnels/TunnelsUi.java`, `ui/transfers/TransfersPanels.java`, `ui/sftp/SftpUi.java`,
  `RemotePlugin.java`, `plugin.toml` (0.13.0); File Servers' `FileServerPlugin.java`, `plugin.toml` (0.5.0); the
  sample's `SamplePlugin.java` (comment and snippet)
- Test: `FakePanelsAndWindowsTest`, `FakeKitMountingTest`, `FakeContractTest`; Remote's `SftpTestAccess`,
  `TunnelsTestAccess`, `ClassicSessionsWiringTest`, `SftpPluginTest` and every caller of the testkit's panel methods,
  File Servers' `FileServerPluginTest`, the sample's `SamplePluginTest`; the application's `AppContractTest`,
  `KitCompatibilityTest`, `FileServerRenderEvidenceTest`, `PanelSurfaceFixture`, `SessionsSurfaceFixture`,
  `StagedRemoteHost`, `WorkspacePanelsFixture`

**Interfaces:**
- Consumes: plan 4a's `Contributions.panelRoute`; Task 2's `WindowHandle.isMain()`, `FakeWorkspace.main()`.
- Produces: `FakeUi`'s toggle shows the one panel in the main window; `FakePluginHost.openPanel(String)` (null while no
  main window is open), `panelView(String)`, `panelHeader(String)`, `closePanel(String)`; `ContractHarness.openPanel
  (String)`, `panelHeader(String)`, `runPanelHeaderAction(String, String)`, `closePanel(String)`; Remote's
  `TunnelsUi.panel()`, `SftpUi.panel()`, `RemotePlugin.hostsPanel()`, File Servers' `FileServerPlugin.panel()` (one
  panel each, for tests); Remote 0.13.0, File Server 0.5.0.

- [ ] **Step 1: Write the failing test**

A child window's toggle shows the one panel in the main window, and the main window's toggle hides it:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
F = "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePanelsAndWindowsTest.java"
edit(F, [(
    """            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().toggle("dev.other.panel", window));
        }
    }); }
""", """            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().toggle("dev.other.panel", window));
        }
    }); }

    /** Workspace tabs spec 14: panels live in the main window; a child window's toggle shows the panel there. */
    @Test void aChildWindowsToggleShowsThePanelInTheMainWindow() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> instances = new ArrayList<>();
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.LEFT), panel -> { instances.add(panel); return KitFixtures.view(context); });
            UUID main = host.addTerminalWindow(), child = host.addTerminalWindow();
            context.panels().toggle("dev.x.tool.hosts", context.terminals().window(child).orElseThrow());
            assertThat(instances).singleElement().satisfies(panel -> {
                assertThat(panel.window().id()).isEqualTo(main);
                assertThat(panel.window().isMain()).isTrue();
                assertThat(panel.visible()).isTrue();
            });
            context.panels().toggle("dev.x.tool.hosts", context.terminals().window(main).orElseThrow());
            assertThat(instances).as("one instance, hidden by the main window's toggle").singleElement()
                .satisfies(panel -> assertThat(panel.visible()).isFalse());
        }
    }); }
""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakePanelsAndWindowsTest'`
Expected: FAIL in `aChildWindowsToggleShowsThePanelInTheMainWindow`: the panel's window is the child window.

- [ ] **Step 3: The SDK's word, and the fake's panels in the main window**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
D = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/"
edit(D + "Panels.java", [
    ("/** Tool window panels. Each registered panel gets a stripe icon in every window. */",
     "/** Tool window panels. Each registered panel gets a stripe icon in the main window, the only window with tool windows. */", 1),
    ("     * @param factory builds one instance per window, lazily", "     * @param factory builds the panel lazily, in the main window", 1),
    ("     * @return the registration; closing it removes the panel from every window", "     * @return the registration; closing it removes the panel", 1)])
import pathlib, re
p = pathlib.Path(D + "Panels.java"); t = p.read_text(encoding="utf-8")
m = re.search(r"     \* Toggles this plugin's registered panel in a [^\n]*\n     \* Requests for a closed window have no effect\.\n     \* @param panelId a registered panel id owned by this plugin\n     \* @param window the [^\n]*\n", t)
if not m: sys.exit("Panels.java: the toggle comment moved")
t = t.replace(m.group(0), """     * Toggles this plugin's registered panel in the main window, creating it lazily; the main window comes to the front.
     * Requests from a closed window have no effect.
     * @param panelId a registered panel id owned by this plugin
     * @param window the window the request comes from, the main window or a child window
""")
p.write_text(t, encoding="utf-8")
edit(D + "PanelFactory.java", [(
    """/** Builds a panel's content for one window. */""", """/** Builds a panel's content, in the main window. */""", 1)])
p = pathlib.Path(D + "PanelFactory.java"); t = p.read_text(encoding="utf-8")
old = "     * Called on the UI thread once per window, the first time the panel is shown there. Build an owned kit view; share your own model between the instances."
if t.count(old) != 1: sys.exit("PanelFactory.java: the create comment moved")
p.write_text(t.replace(old, "     * Called on the UI thread the first time the panel is shown in the main window, and again after the main window\n"
    "     * closed and opened anew. Build an owned kit view; keep your own model outside it."), encoding="utf-8")
edit(D + "PanelHost.java", [
    ("/** One panel instance's place in one window. All methods are UI-thread only. */", "/** The panel's place in the main window. All methods are UI-thread only. */", 1),
    ("""    /**
     * The window that holds this instance.
     *
     * @return the window
     */""", """    /**
     * The main window, which holds the panel.
     *
     * @return the window
     */""", 1),
    ("    /** Shows the panel in this window, hiding whatever else occupied its slot. */", "    /** Shows the panel, hiding whatever else occupied its slot. */", 1),
    ("    /** Hides the panel in this window. */", "    /** Hides the panel. */", 1),
    ("""     * Whether the panel is showing in this window.""", """     * Whether the panel is showing.""", 1),
    ("""     * Runs the handler after each change of visibility in this window.""", """     * Runs the handler after each change of the panel's visibility.""", 1),
    ("""     * Runs the handler when this instance is discarded because its window closed or the panel was removed.""",
     """     * Runs the handler when this instance is discarded because the main window closed or the panel was removed.""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/commands/KeyContext.java", [
    ("    /** Inside one panel, in whichever window shows it. */", "    /** Inside one panel, in the main window. */", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java"
edit(K, [(
    """            if (!window.isOpen()) return;
            var instance = panel.instances.get(window.id());
            if (instance == null) openPanel(panelId, window.id());
            else instance.setVisible(!instance.visible());""",
    """            if (!window.isOpen()) return;
            // Panels live in the main window (workspace tabs spec 14): a request from any window toggles it there.
            var main = host.workspace.main().orElse(null);
            if (main == null) return;
            var instance = panel.instances.get(main.id);
            if (instance == null) openPanel(panelId, main.id);
            else instance.setVisible(!instance.visible());""", 1)])
# The toolbar and the status bar are the main window's too (plan 4a Rulings 6).
edit(D + "Toolbar.java", [("/** The main toolbar's plugin section, which follows the built-in buttons in every window. */",
                           "/** The main toolbar's plugin section, which follows the built-in buttons in the main window, the only window with a toolbar. */", 1)])
edit(D + "ToolbarItem.java", [
    ("     * A small group of the plugin's own controls, one per window, such as a session choice with a Connect button. The\n     * factory runs when a window opens, and for each window already open from inside {@code add}, never for a window\n",
     "     * A small group of the plugin's own controls, one per window with a toolbar (the main window), such as a session\n     * choice with a Connect button. The factory runs when such a window opens, and for each one already open from\n     * inside {@code add}, never for a window\n", 1)])
edit(D + "ToolbarViewFactory.java", [
    (" * Builds one window's toolbar view (see {@link ToolbarItem.View}). The host calls it on the UI thread, once per window,\n * when the window opens, and for every window already open from inside the {@code Toolbar.add} that registers it: finish\n",
     " * Builds one window's toolbar view (see {@link ToolbarItem.View}); the main window is the only window with a toolbar.\n * The host calls it on the UI thread, once per window, when the window opens, and for every such window already open\n * from inside the {@code Toolbar.add} that registers it: finish\n", 1)])
edit(D + "StatusBar.java", [("     * It shares the status-item namespace and ordering; one handle drives every window.",
                             "     * It shares the status-item namespace and ordering; one handle drives it in the main window's status bar.", 1)])
edit(D + "StatusItem.java", [(" * A status bar item the application renders in every window. One handle drives them all; per-window\n * content is not supported.",
                             " * A status bar item the application renders in the main window, the only window with a status bar. One handle\n * drives it; per-window content is not supported.", 1)])
edit(D + "StatusProgress.java", [(" * A host-rendered progress control in every window. Mutators run on the UI thread and",
                                 " * A host-rendered progress control in the main window's status bar. Mutators run on the UI thread and", 1)])
PY
```

The fake keeps one instance per panel, in the main window; the testkit's and the contract harness's panel methods
lose their window id, and the contract test of a panel's header checks one instance, opened again from the main window,
on both runtimes (Rulings 14):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, span
# Panels live in the main window in the fake too (workspace tabs spec 14): one instance per panel, and the testkit's
# panel methods take no window.
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeUi.java", [
    ("    static final class Panel { final PanelSpec spec; final PanelFactory factory; final Map<UUID, PanelInstance> instances = new LinkedHashMap<>(); Panel(PanelSpec spec, PanelFactory factory) { this.spec = spec; this.factory = factory; } }",
     "    /** A registered panel and its instance in the main window, while one is built. */\n"
     "    static final class Panel { final PanelSpec spec; final PanelFactory factory; PanelInstance instance; Panel(PanelSpec spec, PanelFactory factory) { this.spec = spec; this.factory = factory; } }", 1),
    ("        for (Panel panel : List.copyOf(panels)) panel.instances.values().forEach(PanelInstance::close);",
     "        for (Panel panel : List.copyOf(panels)) if (panel.instance != null) panel.instance.close();", 1),
    ("            return () -> { if (panels.remove(panel)) { panel.instances.values().forEach(PanelInstance::close); panel.instances.clear(); } };",
     "            return () -> { if (panels.remove(panel) && panel.instance != null) { panel.instance.close(); panel.instance = null; } };", 1),
    ("""            // Panels live in the main window (workspace tabs spec 14): a request from any window toggles it there.
            var main = host.workspace.main().orElse(null);
            if (main == null) return;
            var instance = panel.instances.get(main.id);
            if (instance == null) openPanel(panelId, main.id);
            else instance.setVisible(!instance.visible());""",
     """            // Panels live in the main window (workspace tabs spec 14): a request from any window toggles it there.
            if (panel.instance == null) openPanel(panelId);
            else panel.instance.setVisible(!panel.instance.visible());""", 1),
    ("""    JComponent openPanel(String panelId, UUID windowId) {
        for (Panel panel : panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var existing = panel.instances.get(windowId);
            if (existing != null) { existing.show(); return existing.content; }
            try {
                var instance = new PanelInstance(windowId);""",
     """    /** Builds the panel in the main window, or shows it there; null without a main window or when the factory fails. */
    JComponent openPanel(String panelId) {
        var main = host.workspace.main().orElse(null);
        if (main == null) return null;
        for (Panel panel : panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var existing = panel.instance;
            if (existing != null) { existing.show(); return existing.content; }
            try {
                var instance = new PanelInstance(main.id);""", 1),
    ("                if (instance.content != null) panel.instances.put(windowId, instance);",
     "                if (instance.content != null) panel.instance = instance;", 1)])
span(K + "FakePluginHost.java", "    /** Returns the currently published header of an open panel instance.",
     """        return java.util.Optional.empty();
    }
""", """    /**
     * Returns the header the open panel last published.
     *
     * @param panelId panel identifier
     * @return header metadata, or an empty header if the panel is not open
     */
    public dev.jasper.sdk.ui.PanelHeader panelHeader(String panelId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            if (panel.instance != null) return panel.instance.header;
        }
        return dev.jasper.sdk.ui.PanelHeader.EMPTY;
    }

    /**
     * Discards the open panel as the main window would when it closes: its header clears, retained header callbacks
     * become inert and its close handlers run. Nothing happens when the panel is not open.
     *
     * @param panelId panel identifier
     */
    public void closePanel(String panelId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var instance = panel.instance;
            panel.instance = null;
            if (instance != null) instance.close();
        }
    }

    /**
     * Invokes a panel's factory as the main window showing it would, or shows the panel built there: panels live in the
     * main window (workspace tabs spec 14). A factory failure is recorded.
     *
     * @param panelId the panel to build
     * @return the component, or null when no main window is open, there is no such panel or its factory failed
     */
    public javax.swing.JComponent openPanel(String panelId) {
        for (FakePluginContext context : contexts.values()) {
            javax.swing.JComponent built = context.ui.openPanel(panelId);
            if (built != null) return built;
        }
        return null;
    }

    /**
     * The mounted owned view of the open panel, when its content is a kit view.
     *
     * @param panelId panel identifier
     * @return the view, or empty when the panel is not open
     */
    public java.util.Optional<FakeKitView> panelView(String panelId) {
        for (var context : contexts.values()) for (var panel : context.ui.panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var instance = panel.instance;
            if (instance == null || instance.done) return java.util.Optional.empty();
            return context.kit().views().stream().filter(view -> view.component == instance.content).findFirst();
        }
        return java.util.Optional.empty();
    }
""")
# The shared contract: the harness opens, reads and closes the main window's panel.
C = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/"
edit(C + "ContractHarness.java", [
    ("""    /** UI thread: invokes the panel's factory as a window showing it would; null when there is no such panel or the factory failed. */
    javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId);

    /**
     * UI thread: the header metadata an open panel instance published through {@code PanelHost.setHeader}, as
     * {@code detail|active|label;label} with {@code -} for no actions; {@code |false|-} when it published none or no
     * instance is open.
     */
    String panelHeader(String panelId, java.util.UUID windowId);

    /** UI thread: activates the labelled header action of an open panel instance as its header would; false when there is none. */
    boolean runPanelHeaderAction(String panelId, java.util.UUID windowId, String label);

    /** UI thread: discards an open panel instance as its window would when the window or registration goes away. */
    void closePanel(String panelId, java.util.UUID windowId);""",
     """    /**
     * UI thread: invokes the panel's factory as the main window showing it would, or returns the panel built there;
     * null when no main window is open, there is no such panel or the factory failed.
     */
    javax.swing.JComponent openPanel(String panelId);

    /**
     * UI thread: the header metadata the open panel published through {@code PanelHost.setHeader}, as
     * {@code detail|active|label;label} with {@code -} for no actions; {@code |false|-} when it published none or the
     * panel is not open.
     */
    String panelHeader(String panelId);

    /** UI thread: activates the labelled header action of the open panel as its header would; false when there is none. */
    boolean runPanelHeaderAction(String panelId, String label);

    /** UI thread: discards the open panel as the main window would when it closes or the registration goes away. */
    void closePanel(String panelId);""", 1)])
P = C + "PluginContractTest.java"
edit(P, [
    ("            h.openPanel(\"test.icons.panel\",window);", "            h.openPanel(\"test.icons.panel\");", 1),
    ("h.closePanel(\"test.icons.panel\",window);", "h.closePanel(\"test.icons.panel\");", 1),
    ("    @Test void panelsAreNamespacedLazyPerWindowAndContained() {", "    @Test void panelsAreNamespacedLazyInTheMainWindowAndContained() {", 1),
    ("""        assertThat(hosts).as("no instance until a window shows the panel").isEmpty();
        UUID window = UUID.randomUUID();
        h.ui(() -> {
            assertThat(h.openPanel("test.alpha.hosts", window)).isNotNull();
            assertThat(h.openPanel("test.alpha.broken", window)).as("a throwing factory is contained").isNull();
            assertThat(h.openPanel("test.alpha.absent", window)).isNull();
        });""", """        assertThat(hosts).as("no instance until the main window shows the panel").isEmpty();
        UUID window = h.addTerminalWindow();
        h.ui(() -> {
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(h.openPanel("test.alpha.broken")).as("a throwing factory is contained").isNull();
            assertThat(h.openPanel("test.alpha.absent")).isNull();
        });""", 1)])
span(P, "    /**\n     * PanelHost.setHeader (0.14.2) on a kit (ViewSpec) panel: per-window metadata",
     """            assertThat(calls).containsExactly("new", "import");
        }));
    }
""", """    /**
     * PanelHost.setHeader (0.14.2) on a kit (ViewSpec) panel: metadata the host shows in the panel's one header row,
     * replaced wholesale, cleared by EMPTY, contained when an action fails and ignored after the panel closes. The panel
     * lives in the main window, so opening it again shows the same instance.
     */
    @Test void aKitPanelsHeaderIsReplacedContainedAndIgnoredAfterClose() throws Exception {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        var context = started("test.alpha", Set.of());
        var icon = "file";
        UUID main = h.addTerminalWindow();
        javax.swing.SwingUtilities.invokeAndWait(() -> h.ui(() -> {
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "folder", Anchor.LEFT), host -> {
                hosts.add(host);
                host.setHeader(new dev.jasper.sdk.ui.PanelHeader(String.valueOf(hosts.size() + 2), true, List.of(
                    new dev.jasper.sdk.ui.PanelHeader.Action("New session", icon, () -> calls.add("new")),
                    new dev.jasper.sdk.ui.PanelHeader.Action("Broken", icon, () -> { throw new IllegalStateException("header failed"); }))));
                var C = dev.jasper.sdk.ui.ControlState.READY;
                return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Panel("hosts", C,
                    new dev.jasper.sdk.ui.Nodes.Header("hosts-header", C, "Hosts", java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                    Optional.empty(), new dev.jasper.sdk.ui.Nodes.Text("body", C, "Body", dev.jasper.sdk.ui.Nodes.TextRole.BODY), Optional.empty())), e -> { });
            });
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(hosts).as("one instance, in the main window").singleElement()
                .satisfies(host -> assertThat(host.window().id()).isEqualTo(main));
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("3|true|New session;Broken");

            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "New session")).isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Broken")).as("a failing action is contained").isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Absent")).isFalse();
            assertThat(calls).containsExactly("new");

            hosts.get(0).setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("import")))));
            assertThat(h.panelHeader("test.alpha.hosts")).as("replaced wholesale").isEqualTo("amp|false|Import");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Import")).isTrue();
            assertThat(calls).containsExactly("new", "import");

            hosts.get(0).setHeader(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");
            assertThatNullPointerException().isThrownBy(() -> hosts.get(0).setHeader(null));
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");

            h.closePanel("test.alpha.hosts");
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");
            hosts.get(0).setHeader(new dev.jasper.sdk.ui.PanelHeader("late", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("late")))));
            assertThat(h.panelHeader("test.alpha.hosts")).as("a closed panel publishes nothing").isEqualTo("|false|-");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Import")).isFalse();
            assertThat(calls).containsExactly("new", "import");
        }));
    }
""")
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [
    ("""            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) { return host.openPanel(panelId, windowId); }
            @Override public String panelHeader(String panelId, java.util.UUID windowId) {
                var header = host.panelHeader(panelId, windowId);""",
     """            @Override public javax.swing.JComponent openPanel(String panelId) { return host.openPanel(panelId); }
            @Override public String panelHeader(String panelId) {
                var header = host.panelHeader(panelId);""", 1),
    ("""            @Override public boolean runPanelHeaderAction(String panelId, java.util.UUID windowId, String label) {
                var action = host.panelHeader(panelId, windowId).actions()""",
     """            @Override public boolean runPanelHeaderAction(String panelId, String label) {
                var action = host.panelHeader(panelId).actions()""", 1),
    ("            @Override public void closePanel(String panelId, java.util.UUID windowId) { host.closePanel(panelId, windowId); }",
     "            @Override public void closePanel(String panelId) { host.closePanel(panelId); }", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java"
edit(A, [
    ("""            /** Open instances' sites and the header metadata each last published, keyed by panel id and window, as a window keeps them. */
            final java.util.Map<String, dev.jasper.app.contributions.PanelSite> sites = new java.util.HashMap<>();
            final java.util.Map<String, dev.jasper.app.contributions.PanelHeaderState> headers = new java.util.HashMap<>();
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) {
                String key = panelId + "|" + windowId;
                return onEdtValue(() -> contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                    .map(panel -> {
                        var site = new dev.jasper.app.contributions.PanelSite(windowId, () -> { }, () -> { }, () -> true, header -> headers.put(key, header));
                        var built = panel.factory().apply(site);
                        if (built != null) sites.put(key, site);
                        return built;
                    })
                    .orElse(null));
            }
            @Override public String panelHeader(String panelId, java.util.UUID windowId) {
                return onEdtValue(() -> {
                    var header = headers.getOrDefault(panelId + "|" + windowId, dev.jasper.app.contributions.PanelHeaderState.EMPTY);""",
     """            /** Open panels' sites, components and the header metadata each last published, keyed by panel id, as the main window keeps them. */
            final java.util.Map<String, dev.jasper.app.contributions.PanelSite> sites = new java.util.HashMap<>();
            final java.util.Map<String, javax.swing.JComponent> components = new java.util.HashMap<>();
            final java.util.Map<String, dev.jasper.app.contributions.PanelHeaderState> headers = new java.util.HashMap<>();
            @Override public javax.swing.JComponent openPanel(String panelId) {
                return onEdtValue(() -> {
                    UUID window = main[0];
                    if (window == null) return null;
                    if (components.containsKey(panelId)) return components.get(panelId);
                    return contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                        .map(panel -> {
                            var site = new dev.jasper.app.contributions.PanelSite(window, () -> { }, () -> { }, () -> true, header -> headers.put(panelId, header));
                            var built = panel.factory().apply(site);
                            if (built != null) { sites.put(panelId, site); components.put(panelId, built); }
                            return built;
                        })
                        .orElse(null);
                });
            }
            @Override public String panelHeader(String panelId) {
                return onEdtValue(() -> {
                    var header = headers.getOrDefault(panelId, dev.jasper.app.contributions.PanelHeaderState.EMPTY);""", 1),
    ("""            @Override public boolean runPanelHeaderAction(String panelId, java.util.UUID windowId, String label) {
                return onEdtValue(() -> {
                    var action = headers.getOrDefault(panelId + "|" + windowId, dev.jasper.app.contributions.PanelHeaderState.EMPTY)""",
     """            @Override public boolean runPanelHeaderAction(String panelId, String label) {
                return onEdtValue(() -> {
                    var action = headers.getOrDefault(panelId, dev.jasper.app.contributions.PanelHeaderState.EMPTY)""", 1),
    ("""            @Override public void closePanel(String panelId, java.util.UUID windowId) {
                onEdt(() -> { var site = sites.remove(panelId + "|" + windowId); if (site != null) site.notifyClosed(); });""",
     """            @Override public void closePanel(String panelId) {
                onEdt(() -> { components.remove(panelId); var site = sites.remove(panelId); if (site != null) site.notifyClosed(); });""", 1)])
# The testkit's own tests.
T = "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/"
span(T + "FakePanelsAndWindowsTest.java", "    @Test void panelHeadersAreIndependentContainFailuresAndIgnoreRetainedActionsAfterClose() throws Exception {",
     """            assertThat(calls).containsExactly("first", "second");
            assertThat(host.failures()).hasSize(2);
        }
    }); }
""", """    @Test void panelHeadersContainFailuresAndIgnoreRetainedActionsAfterClose() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var panels = new ArrayList<PanelHost>();
            var calls = new ArrayList<String>();
            String id = "dev.x.tool.headers";
            var registration = context.panels().register(new PanelSpec(id, "Headers", "file", Anchor.LEFT),
                panel -> { panels.add(panel); return KitFixtures.view(context); });
            host.addTerminalWindow();
            host.openPanel(id); host.openPanel(id);
            assertThat(panels).as("one instance, in the main window").hasSize(1);
            var icon = "file";
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("5", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("first")),
                new dev.jasper.sdk.ui.PanelHeader.Action("Failure", icon, () -> { throw new IllegalStateException("header failed"); }),
                new dev.jasper.sdk.ui.PanelHeader.Action("Linkage failure", icon, () -> { throw new NoClassDefFoundError("header dependency"); }))));
            var stale = host.panelHeader(id);
            assertThat(stale.detail()).isEqualTo("5");
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("second")))));
            assertThat(host.panelHeader(id).detail()).isEqualTo("amp");
            stale.actions().forEach(action -> action.invoke().run());
            host.panelHeader(id).actions().getFirst().invoke().run();
            assertThat(host.failures()).hasSize(2);
            registration.close();
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("late", false, List.of()));
            stale.actions().forEach(action -> action.invoke().run());
            assertThat(host.panelHeader(id)).isEqualTo(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(calls).containsExactly("first", "second");
            assertThat(host.failures()).hasSize(2);
        }
    }); }
""")
edit(T + "FakePanelsAndWindowsTest.java", [
    ("""            UUID window = UUID.randomUUID();
            assertThat(host.openPanel("dev.x.tool.hosts", window)).isNotNull();
            assertThat(hosts.get(0).window().id()).isEqualTo(window);
            assertThat(hosts.get(0).visible()).isTrue();
            assertThat(host.openPanel("dev.x.tool.broken", window)).isNull();
            assertThat(host.openPanel("dev.x.tool.absent", window)).isNull();""",
     """            assertThat(host.openPanel("dev.x.tool.hosts")).as("no main window to show it in").isNull();
            UUID window = host.addTerminalWindow();
            assertThat(host.openPanel("dev.x.tool.hosts")).isNotNull();
            assertThat(hosts.get(0).window().id()).isEqualTo(window);
            assertThat(hosts.get(0).visible()).isTrue();
            assertThat(host.openPanel("dev.x.tool.broken")).isNull();
            assertThat(host.openPanel("dev.x.tool.absent")).isNull();""", 1)])
edit(T + "FakeKitMountingTest.java", [
    ("    @Test void panelRegistrationOwnsEachViewAndContainsFactoryFailure() throws Exception {",
     "    @Test void panelRegistrationOwnsItsViewAndContainsFactoryFailure() throws Exception {", 1),
    ("""                UUID one=UUID.randomUUID();host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",UUID.randomUUID());
                assertThat(views).hasSize(2);registration.close();registration.close();assertThat(disposed.get()).isEqualTo(2);""",
     """                host.addTerminalWindow();host.openPanel("test.panel.files");host.openPanel("test.panel.files");
                assertThat(views).as("one view, in the main window").hasSize(1);registration.close();registration.close();assertThat(disposed.get()).isEqualTo(1);""", 1),
    ("""                assertThat(host.openPanel("test.panel.broken",one)).isNull();""", """                assertThat(host.openPanel("test.panel.broken")).isNull();""", 1)])
PY
```

- [ ] **Step 4: Remote and File Servers keep one panel**

Tunnels and Transfers:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
R = "plugins/remote/src/main/java/dev/jasper/remote/ui/"
edit(R + "tunnels/TunnelsUi.java", [
    ("""    private final Map<UUID, TunnelsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
""", """    /** The Tunnels panel while it is open: panels live in the main window (workspace tabs spec 14). */
    private TunnelsPanel panel;
    private PanelHost panelHost;
""", 1),
    ("""        ticker = new Timer(1000, event -> panels.values().forEach(TunnelsPanel::refresh));""",
     """        ticker = new Timer(1000, event -> { if (panel != null) panel.refresh(); });""", 1),
    ("""    /** The window's Tunnels panel, once it was opened there. */
    TunnelsPanel panel(UUID windowId) { return panels.get(windowId); }""",
     """    /** The Tunnels panel while it is open. */
    TunnelsPanel panel() { return panel; }""", 1),
    ("""    private dev.jasper.sdk.ui.UiView create(PanelHost panelHost) {
        WindowHandle window = panelHost.window();
        // The switch, Enter and the menu act through the manager, whose confirmed state alone redraws the row.
        var panel = new TunnelsPanel(""", """    private dev.jasper.sdk.ui.UiView create(PanelHost host) {
        WindowHandle window = host.window();
        // The switch, Enter and the menu act through the manager, whose confirmed state alone redraws the row.
        var panel = new TunnelsPanel(""", 1),
    ("""        panel.onHeaderChanged(panelHost::setHeader);
        panels.put(window.id(), panel);
        panelHosts.put(window.id(), panelHost);
        // The host disposed the view; the panel goes inert, so no later state change or tick touches it.
        panelHost.onClosed(() -> {
            panel.close();
            if (panels.get(window.id()) == panel) { panels.remove(window.id()); panelHosts.remove(window.id()); }
        });""", """        panel.onHeaderChanged(host::setHeader);
        this.panel = panel;
        panelHost = host;
        // The host disposed the view; the panel goes inert, so no later state change or tick touches it.
        host.onClosed(() -> {
            panel.close();
            if (this.panel == panel) { this.panel = null; panelHost = null; }
        });""", 1),
    ("""        for (TunnelsPanel panel : panels.values()) fill(panel);""", """        if (panel != null) fill(panel);""", 1),
    ("""        PanelHost existing = panelHosts.get(window.id());
        if (existing == null) context.panels().toggle(PANEL, window); else existing.show();""",
     """        // Asked from any window, the panel shows in the main window.
        if (panelHost == null) context.panels().toggle(PANEL, window); else panelHost.show();""", 1)])
edit(R + "transfers/TransfersPanels.java", [
    (""" * "3: Transfers", and shows it on that action. Each window's panel shows that window's transfer rows. UI thread only.""",
     """ * "3: Transfers", and shows it on that action. The panel, in the main window, shows its transfer rows. UI thread only.""", 1),
    ("""    private final Map<UUID, PanelHost> hosts = new HashMap<>();
""", """    /** The panel's host while it is open: panels live in the main window (workspace tabs spec 14). */
    private PanelHost host;
""", 1),
    ("""        hosts.put(window.id(), host);
        host.onClosed(() -> { panel.close(); transfers.release(window, strip); if (hosts.get(window.id()) == host) hosts.remove(window.id()); });""",
     """        this.host = host;
        host.onClosed(() -> { panel.close(); transfers.release(window, strip); if (this.host == host) this.host = null; });""", 1),
    ("""    /** Shows the window's Transfers panel, opening it there when it is not open. */
    public void show(WindowHandle window) {
        PanelHost host = hosts.get(window.id());
        if (host == null) context.panels().toggle(TransfersPanel.PANEL, window); else host.show();""",
     """    /** Shows the Transfers panel, opening it in the main window when it is not open; {@code window} asked for it. */
    public void show(WindowHandle window) {
        if (host == null) context.panels().toggle(TransfersPanel.PANEL, window); else host.show();""", 1)])
PY
```

SFTP:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
F = "plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java"
edit(F, [
    (""" * Per-window explorers and captured picker flows; the coordinator owns all queued jobs. Each window's explorer is an
 * owned kit view ({@code register}); its dialogs""", """ * The explorer, in the main window's SFTP panel (workspace tabs spec 14), and captured picker flows; the coordinator owns
 * all queued jobs. The explorer is an owned kit view ({@code register}); its dialogs""", 1),
    ("""    private final Map<UUID,View> views=new HashMap<>();
    private final Set<PluginDialog> dialogs=new HashSet<>();
    private final Map<UUID,CompletableFuture<ConnectionIdentity>> browsing=new HashMap<>();""",
     """    /** The explorer while the SFTP panel is open: panels live in the main window. */
    private View explorer;
    private final Set<PluginDialog> dialogs=new HashSet<>();
    /** The host a Browse is resolving, until it connects or another Browse replaces it. */
    private CompletableFuture<ConnectionIdentity> browsing;""", 1),
    ("""invoked-> { var view=closed?null:views.get(invoked.window().id());if(view!=null) command.accept(view.panel()); });""",
     """invoked-> { var view=closed?null:explorer;if(view!=null) command.accept(view.panel()); });""", 1),
    ("""    /** The window's explorer while its panel is open. */
    SftpPanel panel(UUID window) { var view=views.get(window);return view==null?null:view.panel(); }""",
     """    /** The explorer while the SFTP panel is open. */
    SftpPanel panel() { return explorer==null?null:explorer.panel(); }""", 1),
    ("""        var view=new View(host,panel,controller,operations);views.put(window.id(),view);""",
     """        var view=new View(host,panel,controller,operations);explorer=view;""", 1),
    ("""releaseStrip.accept(window);cancelBrowse(window.id());views.remove(window.id());controller.close();""",
     """releaseStrip.accept(window);cancelBrowse();if(explorer==view) explorer=null;controller.close();""", 1),
    ("""    private View showView(WindowHandle window) {
        var view=views.get(window.id());if(view==null) { context.panels().toggle(PANEL,window);view=views.get(window.id()); }else view.host().show();return view;
    }""", """    /** The explorer, shown; asked from any window, the panel opens in the main window. */
    private View showView(WindowHandle window) {
        if(explorer==null) context.panels().toggle(PANEL,window);else explorer.host().show();return explorer;
    }""", 1),
    ("""    public void hostsChanged() { if(closed) return;for(var view:views.values()) view.panel().sessions(hosts.get()); }
    /** Shows the window's SFTP sidebar without choosing a host. */""",
     """    public void hostsChanged() { if(closed || explorer==null) return;explorer.panel().sessions(hosts.get()); }
    /** Shows the SFTP sidebar without choosing a host. */""", 1),
    ("""        cancelBrowse(window.id());var request=connections.resolveIdentity(host.id(),window);browsing.put(window.id(),request);
        request.whenComplete((identity,error)->ui.execute(()-> { if(closed || !window.isOpen() || browsing.get(window.id())!=request) return;browsing.remove(window.id());""",
     """        cancelBrowse();var request=connections.resolveIdentity(host.id(),window);browsing=request;
        request.whenComplete((identity,error)->ui.execute(()-> { if(closed || !window.isOpen() || browsing!=request) return;browsing=null;""", 1),
    ("""    private void cancelBrowse(UUID window) { var request=browsing.remove(window);if(request!=null) request.cancel(true); }
    public void pane(WindowHandle window,PaneTarget pane) { cancelBrowse(window.id());var view=views.get(window.id());if(view!=null) view.controller().open(pane.pane(),pane.identity(),pane.directory(),false); }
    public void directory(UUID pane,String path) { for(var view:views.values()) view.controller().follow(pane,path); }
    /** {@code pane}'s SSH session ended (closed, exited or lost): views showing it disconnect and clear. */
    public void ended(UUID pane) { for(var view:views.values()) view.controller().ended(pane); }""",
     """    private void cancelBrowse() { var request=browsing;browsing=null;if(request!=null) request.cancel(true); }
    public void pane(WindowHandle window,PaneTarget pane) { cancelBrowse();if(explorer!=null) explorer.controller().open(pane.pane(),pane.identity(),pane.directory(),false); }
    public void directory(UUID pane,String path) { if(explorer!=null) explorer.controller().follow(pane,path); }
    /** {@code pane}'s SSH session ended (closed, exited or lost): the explorer, showing it, disconnects and clears. */
    public void ended(UUID pane) { if(explorer!=null) explorer.controller().ended(pane); }""", 1),
    ("""    /** True while any visible view follows {@code pane}. */
    public boolean following(UUID pane) { return views.values().stream().anyMatch(view->view.controller().following(pane)); }
    public void notice(UUID pane,String text) { for(var view:views.values()) view.controller().notice(pane,text); }""",
     """    /** True while the explorer, visible, follows {@code pane}. */
    public boolean following(UUID pane) { return explorer!=null && explorer.controller().following(pane); }
    public void notice(UUID pane,String text) { if(explorer!=null) explorer.controller().notice(pane,text); }""", 1),
    ("""    /** Whether {@code view} is still {@code owner}'s open view, showing the same identity""",
     """    /** Whether {@code view} is still the open explorer, showing the same identity""", 1),
    ("""        return views.get(owner.id())==view && view.controller()""", """        return explorer==view && view.controller()""", 1),
    ("""closed=true;for(var request:List.copyOf(browsing.values())) request.cancel(true);browsing.clear();for(var dialog:List.copyOf(dialogs)) dialog.close();for(var view:List.copyOf(views.values())) { view.controller().close();view.operations().close();view.panel().close(); }views.clear(); }""",
     """closed=true;cancelBrowse();for(var dialog:List.copyOf(dialogs)) dialog.close();var view=explorer;explorer=null;if(view!=null) { view.controller().close();view.operations().close();view.panel().close(); } }""", 1)])
PY
```

File Servers, Sessions, the plugins' test accessors, SFTP's Back from a child window (it acts on the one explorer) and
the two plugins' versions (Rulings 1):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
F = "plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java"
edit(F, [
    ("""    private final Map<UUID, FileServersPanel> panels = new LinkedHashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new LinkedHashMap<>();
""", """    /** The File Servers panel while it is open: panels live in the main window (workspace tabs spec 14). */
    private FileServersPanel panel;
    private PanelHost panelHost;
""", 1),
    ("""            if (!stopped && panelHosts.values().stream().anyMatch(PanelHost::visible)) registry.refreshAddresses();""",
     """            if (!stopped && panelHost != null && panelHost.visible()) registry.refreshAddresses();""", 1),
    ("""    FileServersPanel panel(UUID window) { return panels.get(window); }""", """    FileServersPanel panel() { return panel; }""", 1),
    ("""        panels.put(window.id(), panel);
        panelHosts.put(window.id(), host);
        host.onVisibility(visible -> { if (visible) registry.refreshAddresses(); });
        host.onClosed(() -> { panel.close(); panels.remove(window.id()); panelHosts.remove(window.id()); });""",
     """        this.panel = panel;
        panelHost = host;
        host.onVisibility(visible -> { if (visible) registry.refreshAddresses(); });
        host.onClosed(() -> { panel.close(); if (this.panel == panel) { this.panel = null; panelHost = null; } });""", 1),
    ("""        PanelHost host = panelHosts.get(window.id());
        if (host == null) context.panels().toggle(PANEL, window);
        else if (!host.visible()) host.show();
        FileServersPanel panel = panels.get(window.id());
        if (panel != null) panel.select(id);""", """        // Asked from any window, the panel shows in the main window.
        if (panelHost == null) context.panels().toggle(PANEL, window);
        else if (!panelHost.visible()) panelHost.show();
        if (panel != null) panel.select(id);""", 1),
    ("""            if (!stopped) panels.values().forEach(FileServersPanel::refreshActivity);""",
     """            if (!stopped && panel != null) panel.refreshActivity();""", 1),
    ("""        for (FileServersPanel panel : panels.values()) {
            panel.setTlsFingerprint(tls.cachedFingerprint());
            panel.setHostKeyFingerprint(hostKey.cachedFingerprint());
            panel.setServers(registry.servers(), store.error());
            panel.refreshActivity();
        }""", """        if (panel == null) return;
        panel.setTlsFingerprint(tls.cachedFingerprint());
        panel.setHostKeyFingerprint(hostKey.cachedFingerprint());
        panel.setServers(registry.servers(), store.error());
        panel.refreshActivity();""", 1)])
regex("plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java", r"plugin\.panel\(window\)", "plugin.panel()", 2)
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ("""    private final Map<UUID, HostsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
""", """    /** The Sessions panel while it is open: panels live in the main window (workspace tabs spec 14). */
    private HostsPanel sessionsPanel;
    private PanelHost sessionsHost;
""", 1),
    ("""            PanelHost panel = panelHosts.get(window.id());
            if (panel == null) showPanel(window); else panel.show();""",
     """            if (sessionsHost == null) showPanel(window); else sessionsHost.show();""", 1),
    ("""        sessionSelection.onChanged((windowId, row) -> { var shown = panels.get(windowId); if (shown != null) row.ifPresent(shown::selectRow); });""",
     """        sessionSelection.onChanged((windowId, row) -> { if (sessionsPanel != null) row.ifPresent(sessionsPanel::selectRow); });""", 1),
    ("""        panels.put(window.id(), panel); panelHosts.put(window.id(), host);""", """        sessionsPanel = panel; sessionsHost = host;""", 1),
    ("""        host.onClosed(() -> { panel.close(); if (panels.get(window.id()) == panel) { panels.remove(window.id()); panelHosts.remove(window.id()); } });""",
     """        host.onClosed(() -> { panel.close(); if (sessionsPanel == panel) { sessionsPanel = null; sessionsHost = null; } });""", 1),
    ("""    private void refreshPanels() {
        for (HostsPanel panel : panels.values()) {
            panel.setCredentialLabels(this::credentialLabel);
            panel.setDefaultGroup(panelState.defaultGroup());
            panel.setHostDetails(this::hostDetails, this::sessionCount);
            panel.setDistros(distros, this::distroSessions);
            panel.setHosts(store.hosts(), store.error());
        }
        for (var entry : List.copyOf(panels.entrySet())) seedSessions(entry.getKey(), entry.getValue());
    }""", """    private void refreshPanels() {
        var panel = sessionsPanel;
        if (panel == null) return;
        panel.setCredentialLabels(this::credentialLabel);
        panel.setDefaultGroup(panelState.defaultGroup());
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.setDistros(distros, this::distroSessions);
        panel.setHosts(store.hosts(), store.error());
        seedSessions(sessionsHost.window().id(), panel);
    }""", 1),
    ("""        sessionsToolbar.renderViews();
        for (var entry : List.copyOf(panels.entrySet())) seedSessions(entry.getKey(), entry.getValue());""",
     """        sessionsToolbar.renderViews();
        if (sessionsPanel != null) seedSessions(sessionsHost.window().id(), sessionsPanel);""", 1),
    ("""                for (var panel : panels.values()) panel.setCollapsed(panelState.collapsed());""",
     """                if (sessionsPanel != null) sessionsPanel.setCollapsed(panelState.collapsed());""", 1),
    ("""    /** The Sessions panel open in a window, for tests. */
    HostsPanel hostsPanel(UUID windowId) { return panels.get(windowId); }""",
     """    /** The Sessions panel while it is open, for tests. */
    HostsPanel hostsPanel() { return sessionsPanel; }""", 1)])
T = "plugins/remote/src/test/java/dev/jasper/remote/"
for path, count in [(T + "RemotePluginTest.java", 6), (T + "ClassicSessionsWiringTest.java", 1), (T + "TunnelsPluginTest.java", 1)]:
    regex(path, r"\.hostsPanel\((\w+(\.\w+\(\))?)\)", ".hostsPanel()", count)
edit(T + "ui/sftp/SftpTestAccess.java", [
    ("""    /** The window's explorer once its panel was opened there. */
    public static SftpPanel panel(SftpUi sftp, UUID window) throws Exception { return Kits.value(() -> sftp.panel(window)); }""",
     """    /** The explorer once the SFTP panel was opened in the main window. */
    public static SftpPanel panel(SftpUi sftp) throws Exception { return Kits.value(sftp::panel); }""", 1)])
edit(T + "ui/tunnels/TunnelsTestAccess.java", [
    ("""    /** The window's Tunnels panel, once it was opened there. */
    public static TunnelsPanel panel(TunnelsUi tunnels, UUID windowId) { return tunnels.panel(windowId); }""",
     """    /** The Tunnels panel, once it was opened in the main window. */
    public static TunnelsPanel panel(TunnelsUi tunnels) { return tunnels.panel(); }""", 1)])
for path, count in [(T + "RemoteEditingPluginTest.java", 1), (T + "RemotePluginTest.java", 2), (T + "SftpChooseSessionTest.java", 2), (T + "SftpPluginTest.java", 3)]:
    regex(path, r"SftpTestAccess\.panel\(plugin\.sftp\(\), ?window\)", "SftpTestAccess.panel(plugin.sftp())", count)
regex(T + "TunnelsPluginTest.java", r"TunnelsTestAccess\.panel\(tunnels, (?:window|started\.window\(\))\)", "TunnelsTestAccess.panel(tunnels)", 5)
# One explorer, in the main window: Back from any window acts on it (the shortcut's panel context keeps a user's key in
# the panel; FakePluginHost.invoke skips that context).
edit(T + "SftpPluginTest.java", [
    ("    @Test void theSftpHeaderCarriesTheConnectionAndItsDotAndTheShortcutsActOnTheWindowsPanel() throws Exception {",
     "    @Test void theSftpHeaderCarriesTheConnectionAndItsDotAndTheShortcutsActOnTheOneExplorer() throws Exception {", 1),
    ("""            assertThat(moves).as("a window without the panel has nothing to go back in").hasSize(2);""",
     """            assertThat(moves).as("one explorer, in the main window: Back from a child window acts on it").containsExactly("back","forward","back");""", 1),
    (""".as("a closed panel leaves the map").isNull();""", """.as("a closed panel leaves no explorer").isNull();""", 1),
    ("""            assertThat(moves).hasSize(2);
            host.stopAll();""", """            assertThat(moves).as("a closed panel has nothing to go back in").hasSize(3);
            host.stopAll();""", 1)])
# Their panels changed: Remote 0.13.0 and File Server 0.5.0 (Indexer's optional "remote >=0.12" still holds).
edit("plugins/remote/src/main/resources/plugin.toml", [('version = "0.12.0"', 'version = "0.13.0"', 1)])
edit("plugins/fileserver/src/main/resources/plugin.toml", [('version = "0.4.0"', 'version = "0.5.0"', 1)])
PY
```

The application's staged tests read the one panel:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(T + "FileServerRenderEvidenceTest.java", [(
    """            var panelsField = plugin.getClass().getDeclaredField("panels"); panelsField.setAccessible(true);
            var owner = ((Map<UUID, ?>) panelsField.get(plugin)).get(windowId);""",
    """            var panelField = plugin.getClass().getDeclaredField("panel"); panelField.setAccessible(true);
            var owner = panelField.get(plugin);""", 1)])
edit(T + "PanelSurfaceFixture.java", [
    ("""                if (((Map<?, ?>) field(plugin, "panels")).get(vault.window) == null) throw new AssertionError("no Sessions panel");""",
     """                if (field(plugin, "sessionsPanel") == null) throw new AssertionError("no Sessions panel");""", 1),
    ("""                Object panel = ((Map<?, ?>) field(tunnelsUi, "panels")).get(vault.window);""",
     """                Object panel = field(tunnelsUi, "panel");""", 1),
    ("""        var panel = sftpUi.getClass().getDeclaredMethod("panel", UUID.class); panel.setAccessible(true);
        return panel.invoke(sftpUi, vault.window);""", """        var panel = sftpUi.getClass().getDeclaredMethod("panel"); panel.setAccessible(true);
        return panel.invoke(sftpUi);""", 1)])
edit(T + "SessionsSurfaceFixture.java", [
    ("""            var panels = field(remote, "panels");
            Object panel = ((Map<?,?>)panels).get(vault.window);""", """            Object panel = field(remote, "sessionsPanel");""", 1),
    ("""    public Object panel() { return ((Map<?,?>)field(remote,"panels")).get(vault.window); }""",
     """    public Object panel() { return field(remote,"sessionsPanel"); }""", 1)])
edit(T + "StagedRemoteHost.java", [
    ("""            // The plugin's per-window panel map: its method table names optional Vault types, which are absent here.
            var panels = plugin.getClass().getDeclaredField("panels"); panels.setAccessible(true);
            Object panel = ((Map<?, ?>) panels.get(plugin)).get(window.id());""",
     """            // The plugin's Sessions panel: its method table names optional Vault types, which are absent here.
            var sessions = plugin.getClass().getDeclaredField("sessionsPanel"); sessions.setAccessible(true);
            Object panel = sessions.get(plugin);""", 1),
    ("""            Object panel = ((Map<?, ?>) field(tunnelsUi, "panels")).get(window.id());""", """            Object panel = field(tunnelsUi, "panel");""", 1),
    ("""            var panel = sftpUi.getClass().getDeclaredMethod("panel", java.util.UUID.class); panel.setAccessible(true);
            return panel.invoke(sftpUi, window.id());""", """            var panel = sftpUi.getClass().getDeclaredMethod("panel"); panel.setAccessible(true);
            return panel.invoke(sftpUi);""", 1)])
edit(T + "WorkspacePanelsFixture.java", [
    ("""    /** Selects a synthetic host in the Sessions panel Remote built for {@code window}; false while it has none. */""",
     """    /** Selects a synthetic host in Remote's Sessions panel, in the main window; false while it has none. */""", 1),
    ("""        Object panel = onEdtValue(() -> ((Map<?, ?>) field(remote, "panels")).get(window));""",
     """        Object panel = onEdtValue(() -> field(remote, "sessionsPanel"));""", 1)])
PY
```

The sample's comment, and its guide snippet regenerated:

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan4b"); from edits import edit
S = "plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java"
edit(S, [("        // One instance per window, built the first time the panel is shown there. Its body is the kit's empty state:",
          "        // Built the first time the panel shows in the main window, the only window with tool windows. Its body is the kit's empty state:", 1)])
# The guide shows the same block, indented as a method.
source = pathlib.Path(S).read_text(encoding="utf-8")
block = source.split("// example:pluginpanels:start\n", 1)[1].split("// example:pluginpanels:end", 1)[0]
lines = block.rstrip().split("\n")
indent = min(len(line) - len(line.lstrip()) for line in lines if line.strip())
snippet = "\n".join(line[indent:] for line in lines) + "\n"
guide = pathlib.Path("docs/plugin-authoring.md")
text = guide.read_text(encoding="utf-8")
start = text.index("<!-- example:pluginpanels -->\n```java\n") + len("<!-- example:pluginpanels -->\n```java\n")
end = text.index("```", start)
guide.write_text(text[:start] + snippet + text[end:], encoding="utf-8")
PY
```

Every caller of the testkit's panel methods drops the window, and a helper or a local that only carried it goes:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
# The testkit's panel methods take no window (Step 1): every call drops it, and helpers that only passed it on lose it.
CALL = r"host\.(openPanel|panelView|panelHeader|closePanel)\((\"[^\"]*\"|[\w.]+),\s*[\w.]+(?:\(\))?\)"
R = "plugins/remote/src/test/java/dev/jasper/remote/"
F = "plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java"
for path, count in [(F, 5), (R + "ClassicSessionsWiringTest.java", 4), (R + "CompositionParityTest.java", 1),
                    (R + "RemoteEditingPluginTest.java", 1), (R + "RemotePluginTest.java", 13), (R + "SftpChooseSessionTest.java", 2),
                    (R + "SftpPluginTest.java", 6), (R + "TunnelsPluginTest.java", 8),
                    ("plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java", 3),
                    ("jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java", 2)]:
    regex(path, CALL, r"host.\1(\2)", count)
edit("plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java", [(
    """            assertThat(host.openPanel("dev.jasper.sample.panel")).isNotNull();""",
    """            host.addTerminalWindow();
            assertThat(host.openPanel("dev.jasper.sample.panel")).as("in the main window").isNotNull();""", 1)])
edit(F, [("""    static FileServersPanel openPanel(FakePluginHost host, FileServerPlugin plugin, UUID window) {""",
          """    static FileServersPanel openPanel(FakePluginHost host, FileServerPlugin plugin) {""", 1)])
regex(F, r"openPanel\(host, plugin, window\)", "openPanel(host, plugin)", 9)
W = R + "ClassicSessionsWiringTest.java"
edit(W, [("    static String tree(RemotePlugin plugin, UUID window) throws Exception {", "    static String tree(RemotePlugin plugin) throws Exception {", 1),
         ("    static Optional<String> properties(FakePluginHost host, UUID window) throws Exception {", "    static Optional<String> properties(FakePluginHost host) throws Exception {", 1)])
regex(W, r"tree\(plugin, window\)", "tree(plugin)", 8)
regex(W, r"properties\(host, window\)", "properties(host)", 4)
P = R + "RemotePluginTest.java"
edit(P, [("    static void headerAction(FakePluginHost host, UUID window, String label) {", "    static void headerAction(FakePluginHost host, String label) {", 1)])
regex(P, r"headerAction\(host, window, ", "headerAction(host, ", 2)
import pathlib
def unused(path, method, decl, new):
    """In test `method`, keeps the window its one `decl` adds but drops the variable only the panel calls read."""
    text = pathlib.Path(path).read_text(encoding="utf-8")
    if text.count(" void " + method + "(") != 1: sys.exit(f"{path}: expected one {method}")
    i = text.index(" void " + method + "(")
    end = text.find("\n    @", i); end = len(text) if end < 0 else end
    k = text.find(decl, i)
    if k < 0 or k > end: sys.exit(f"{path}: no {decl!r} in {method}")
    pathlib.Path(path).write_text(text[:k] + new + text[k + len(decl):], encoding="utf-8")
U, V = "UUID window = host.addTerminalWindow();", "host.addTerminalWindow();"
for method in ["showsTheHostKeyFingerprintAndCopiesIt", "registersItsPanelActionsSettingsAndAHiddenStatus",
               "pastingAKeyReadsTheClipboardAndAnEmptyOrBlankClipboardChangesNothing", "asynchronousStartFailureStaysVisibleInTheOwnedPanel",
               "showsTheCertificateFingerprintAndRegeneratesIt"]:
    unused(F, method, U, V)
for method in ["failedMutationsReportAfterTheEditorCloses", "renamesDefaultGroupThroughThePanel"]:
    unused(P, method, U, V)
for method in ["withoutSavedHostsTheMenuSaysSoAndOpensNoDialog", "theMenuListsSavedHostsLiterallyAndChoosingOneBrowsesIt"]:
    unused(R + "SftpChooseSessionTest.java", method, "var window=host.addTerminalWindow();", "host.addTerminalWindow();")
unused("jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java", "supportedPluginInstantiatesAndUsesTheFinalKitContract",
       "var window=host.addTerminalWindow();", "host.addTerminalWindow();")
PY
git grep -n -E 'host\.(openPanel|panelView|panelHeader|closePanel)\([^)]*,' -- plugins jasper-app/src/test jasper-sdk-testkit/src || echo "none left"
```

Expected: `none left`.

- [ ] **Step 5: The Sessions panel follows only its window's row**

Under the Enterprise look another window's session choice must not move the main window's Sessions tree (Rulings 14).
The fake builds a toolbar view for any window, which the test uses to make that choice:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
T = "plugins/remote/src/test/java/dev/jasper/remote/ClassicSessionsWiringTest.java"
edit(T, [
    ("""/**
 * Spec 14.4, end to end through RemotePlugin under Enterprise: each window's toolbar view, Sessions tree and Properties
 * show one row (the window's choice, else its active SSH pane's host, else the first), whichever of them changed it.
 */""", """/**
 * Spec 14.4, end to end through RemotePlugin under Enterprise: the main window's toolbar view, the Sessions tree and its
 * Properties show one row (the window's choice, else its active SSH pane's host, else the first), whichever of them
 * changed it. The Sessions panel is the main window's (workspace tabs spec 14), so another window's row leaves it alone.
 */""", 1),
    ("""    static void await(FakePluginHost host, Callable<Boolean> condition, String description) throws Exception {""",
     """    @Test void anotherWindowsChoiceLeavesTheMainWindowsTreeAlone(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            FakeEnterprise.start(host);
            var plugin = plugin(dir.resolve("ssh"));
            host.start(RemotePluginTest.INFO, Set.of(), OPTIONAL, plugin);
            settle(host);
            plugin.store().put(mac); plugin.store().put(minecraft);
            settle(host);
            UUID main = host.addTerminalWindow(), child = host.addTerminalWindow();
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            settle(host);
            assertThat(tree(plugin)).isEqualTo(row(mac));

            var view = toolbar(host, child);
            Kits.edt(() -> Kits.emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(row(minecraft)))));
            settle(host);
            assertThat(combo(host, child)).as("the other window's own row").isEqualTo(row(minecraft));
            assertThat(tree(plugin)).as("the main window's tree keeps its row").isEqualTo(row(mac));
            assertThat(combo(host, main)).isEqualTo(row(mac));
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static void await(FakePluginHost host, Callable<Boolean> condition, String description) throws Exception {""", 1)])
PY
```

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ClassicSessionsWiringTest'`
Expected: FAIL in `anotherWindowsChoiceLeavesTheMainWindowsTreeAlone`: `the main window's tree keeps its row` is the
other window's row.

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
edit("plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java", [(
    """        sessionSelection.onChanged((windowId, row) -> { if (sessionsPanel != null) row.ifPresent(sessionsPanel::selectRow); });""",
    """        // The Sessions panel is the main window's: only that window's row moves it.
        sessionSelection.onChanged((windowId, row) -> {
            if (sessionsPanel != null && windowId.equals(sessionsHost.window().id())) row.ifPresent(sessionsPanel::selectRow);
        });""", 1)])
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-plugin-remote:check :jasper-plugin-fileserver:check :jasper-plugin-sample:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.documentation.*' --continue`
Expected: PASS (`PluginContractTest.aKitPanelsHeaderIsReplacedContainedAndIgnoredAfterClose` on `FakeContractTest`
and `AppContractTest`). Then `./gradlew check --continue`: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins docs
git commit -F - <<'EOF'
Workspace tabs: panels in the main window only

Panels, the toolbar and the status bar are the main window's in the SDK's
words and in the fake, where a child window's toggle shows the one panel
in the main window and the panel methods take no window. Remote (0.13.0)
and File Servers (0.5.0) keep one panel's state, and the Sessions panel
follows only its own window's row.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: The Errors tab

**Files:**
- Rename and replace: `jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java` → `ErrorsTab.java`;
  `jasper-app/src/test/java/dev/jasper/app/application/ErrorsWindowTest.java` → `ErrorsTabTest.java`
- Modify: `application/JasperApplication.java`, `application/IncidentNotices.java`, `contributions/TabRegistry.java`
  (`APP_OWNER`), `notificationui/ErrorsView.java` (no Done, the workspace route), `docs/app-maintenance.md`; "Errors
  tab" in the comments of `SystemNotices`, `about/LicensesView`, `notifications/Incident`, `Incidents`,
  `NotificationRequest`, `notificationui/ErrorsView`, `NotificationStyle`, `package-info`
- Test: `ErrorsTabTest` (whole file), `IncidentNoticesTest`, `JasperApplicationIncidentsTest`, `SystemNoticesTest`;
  `notificationui/ErrorsViewTest`, `ErrorsViewTestSupport`, `ThemeMigrationEvidenceTest`, `Task9cNoticeEvidenceTest`;
  `testsupport/KitTestSupport` (`workspaceContext()`)
- Ledger: two renamed rows

**Interfaces:**
- Consumes: plan 4a's `TabRegistry.open` with a null owner (the main window, restored first).
- Produces: `public static final String TabRegistry.APP_OWNER = "jasper"`, the owner of Jasper's own content tabs, which
  plan 6 never saves (Rulings 21); `final class ErrorsTab` (`SHOW_ERRORS = "notifications.errors"`, `KEY = "errors"`,
  `ErrorsTab(Contributions, Incidents, ErrorsView.Hooks, ZoneId, Supplier<KitContext>)` with the workspace route's
  context, `void show(Optional<UUID>)`, test access `view()` and `tab()`); `ErrorsView(KitContext, Incidents, Hooks,
  ZoneId)`, which refuses a context off the workspace route; `KitTestSupport.workspaceContext()`;
  `ErrorsViewTestSupport.kitView(ErrorsView)`.

- [ ] **Step 1: Write the failing test**

```bash
T=jasper-app/src/test/java/dev/jasper/app/application
git mv $T/ErrorsWindowTest.java $T/ErrorsTabTest.java
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/application/ErrorsTabTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.notificationui.ErrorsViewTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 6.6: the Errors tab, an app-owned content tab of the main window. */
@ExtendWith(EdtTestExtension.class)
class ErrorsTabTest {
    private final List<String> workspace = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final UUID main = UUID.randomUUID();

    /** The main window's workspace, as the tab registry sees it: what it opens, closes and selects. */
    private Contributions withMainWindow(Contributions model) {
        model.tabs().addHost(new WorkspaceHost(main, tab -> workspace.add("open " + tab.title()), tab -> workspace.add("close " + tab.title()),
            tab -> workspace.add("select " + tab.title()), () -> { }));
        return model;
    }

    private ErrorsTab tab() { return tab(withMainWindow(contributions)); }

    private ErrorsTab tab(Contributions in) {
        return new ErrorsTab(in, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            dev.jasper.app.testsupport.KitTestSupport::workspaceContext);
    }

    /** The first component of {@code type} in {@code root}, depth first. */
    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Lays out the whole tree, as a window does when it shows its workspace (without a peer, {@code validate} does not). */
    private static void layOut(Component component) {
        if (!(component instanceof Container container)) return;
        container.doLayout();
        for (Component child : container.getComponents()) layOut(child);
    }

    /** Lets the EDT run what is already queued, as its next turn would. */
    private static void nextEdtTurn() {
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingUtilities.invokeLater(loop::exit);
        loop.enter();
    }

    @Test void showErrorsOpensOneErrorsTabInTheMainWindow() {
        tab();
        var action = contributions.action(ErrorsTab.SHOW_ERRORS).orElseThrow();
        assertThat(action.title()).isEqualTo("Notifications: Show Errors");
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(contributions.tabs().hosted()).singleElement().satisfies(tab -> {
            assertThat(tab.window()).as("in the main window").isEqualTo(main);
            assertThat(tab.plugin()).as("Jasper's own tab").isEqualTo(TabRegistry.APP_OWNER);
            assertThat(tab.title()).isEqualTo("Errors");
            assertThat(tab.component()).isInstanceOf(ErrorsView.class);
            // Content, as a plugin's content tab is: the workspace route on its painted surface, and no Done.
            var kitView = ErrorsViewTestSupport.kitView((ErrorsView) tab.component());
            assertThat(kitView.route()).isEqualTo(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
            assertThat(kitView.isOpaque()).as("it paints its surface").isTrue();
            var form = (dev.jasper.ui.model.Nodes.Form) kitView.spec().root();
            assertThat(form.actions()).extracting(dev.jasper.ui.model.Nodes.Button::label).containsExactly("Open Log Folder", "Clear");
            assertThat(form.cancelAction()).as("Escape leaves it open").isEmpty();
        });
        assertThat(workspace).containsExactly("open Errors", "select Errors", "select Errors");
    }

    /**
     * A view that fails to build opens no tab, so the next Show more… opens a working one.
     */
    @Test void aViewThatFailsToBuildOpensNoTabAndTheNextShowWorks() {
        boolean[] fail = {true};
        var errors = new ErrorsTab(withMainWindow(contributions), incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            () -> {
                if (fail[0]) { fail[0] = false; throw new NoClassDefFoundError("dev/jasper/ui/swing/Stacks$PromptLayout"); }
                return dev.jasper.app.testsupport.KitTestSupport.workspaceContext();
            });
        Incident incident = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("boom")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> errors.show(Optional.of(incident.id()))).isInstanceOf(NoClassDefFoundError.class);
        assertThat(contributions.tabs().hosted()).as("no tab without a view").isEmpty();
        errors.show(Optional.of(incident.id()));
        assertThat(contributions.tabs().hosted()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(incident.id());
    }

    @Test void showMoreSelectsItsIncidentInTheSameTab() {
        var errors = tab();
        Incident first = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("first")));
        Incident second = incidents.record("jasper", "b", "t", Capture.of(new IllegalArgumentException("second")));
        errors.show(Optional.of(first.id()));
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(first.id());
        errors.show(Optional.of(second.id()));
        assertThat(contributions.tabs().hosted()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(second.id());
    }

    @Test void showMoreOnAFreshTabRevealsItsIncidentOnceTheTabIsLaidOut() {
        // A window lays a tab out when it shows it, which can leave the list scrolled to the top.
        var laidOut = new Contributions();
        laidOut.tabs().addHost(new WorkspaceHost(main, tab -> {
            var shown = (ErrorsView) tab.component();
            shown.setSize(900, 560);
            layOut(shown);
            find((Container) ErrorsViewTestSupport.list(shown), JViewport.class).setViewPosition(new Point(0, 0));
        }, tab -> { }, tab -> { }, () -> { }));
        var errors = tab(laidOut);
        for (int i = 0; i < 40; i++) incidents.record("jasper", "a", "t", new Capture("x.Older" + i, "", "x.Older" + i, ""));
        Incident wanted = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("wanted")));
        for (int i = 0; i < 40; i++) incidents.record("jasper", "a", "t", new Capture("x.Newer" + i, "", "x.Newer" + i, ""));
        errors.show(Optional.of(wanted.id()));
        nextEdtTurn();
        var view = errors.view().orElseThrow();
        assertThat(ErrorsViewTestSupport.selected(view)).contains(wanted.id());
        assertThat(ErrorsViewTestSupport.listed(view).indexOf(incidents.list().stream().filter(i -> i.id().equals(wanted.id())).findFirst().orElseThrow())).isEqualTo(40);
        var viewport = find((Container) ErrorsViewTestSupport.list(view), JViewport.class);
        var summary = ErrorsViewTestSupport.summary(view, wanted.id());
        assertThat(summary).as("the selected row is mounted").isNotNull();
        var bounds = javax.swing.SwingUtilities.convertRectangle(summary.getParent(), summary.getBounds(), viewport.getView());
        assertThat(viewport.getViewPosition().y).as("scrolled away from the top").isPositive();
        assertThat(viewport.getViewRect().intersects(bounds)).as("the selected row is in view").isTrue();
    }

    @Test void closingTheTabStopsItsViewAndTheNextShowOpensAFreshOne() {
        var errors = tab();
        errors.show(Optional.empty());
        ErrorsView first = errors.view().orElseThrow();
        errors.tab().orElseThrow().close();
        assertThat(errors.view()).isEmpty();
        assertThat(workspace).contains("close Errors");
        incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("after close")));
        assertThat(ErrorsViewTestSupport.listed(first)).as("the closed view stopped listening").isEmpty();
        errors.show(Optional.empty());
        assertThat(errors.view().orElseThrow()).isNotSameAs(first);
        assertThat(ErrorsViewTestSupport.listed(errors.view().orElseThrow())).hasSize(1);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `ErrorsTab`.

- [ ] **Step 3: The Errors tab**

```bash
A=jasper-app/src/main/java/dev/jasper/app/application
git mv $A/ErrorsWindow.java $A/ErrorsTab.java
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/application/ErrorsTab.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.platform.AppIcons;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.SwingUtilities;

/**
 * The Errors tab (spec section 5; workspace tabs spec 6.6): one content tab over this session's incidents, in the main
 * window's active group, opened by an incident's Show more… link with that incident selected, and by Notifications: Show
 * Errors. Jasper owns it ({@link TabRegistry#APP_OWNER}); its tab strip closes it, closing it stops its view, and the tab
 * registry closes it at shutdown. EDT only.
 */
final class ErrorsTab {
    static final String SHOW_ERRORS = "notifications.errors";
    /** The tab's key under {@link TabRegistry#APP_OWNER}. */
    static final String KEY = "errors";

    private final Contributions contributions;
    private final Incidents incidents;
    private final ErrorsView.Hooks hooks;
    private final ZoneId zone;
    /** The workspace route's kit context: the view is a content tab's. */
    private final java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit;
    private Tab tab;
    private ErrorsView view;

    ErrorsTab(Contributions contributions, Incidents incidents, ErrorsView.Hooks hooks, ZoneId zone,
              java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this.kit = java.util.Objects.requireNonNull(kit, "kit");
        this.contributions = contributions;
        this.incidents = incidents;
        this.hooks = hooks;
        this.zone = zone;
        contributions.addAction(SHOW_ERRORS, "Notifications: Show Errors", AppIcons.semantic("error"),
            List.of("notifications", "errors", "exceptions", "stack trace", "incidents", "problems"), Optional.empty(),
            invocation -> show(Optional.empty()));
    }

    /**
     * Opens the tab in the main window, or selects it, and selects {@code incident} when it is still listed. A tab that
     * opens now is laid out as it shows, so its selected row is scrolled into view again on the next EDT turn.
     */
    void show(Optional<UUID> incident) {
        if (tab == null || tab.closed()) {
            // A view that fails to build opens no tab, so the next Show more… starts afresh.
            ErrorsView opened = new ErrorsView(kit.get(), incidents, hooks, zone);
            incident.ifPresent(opened::select);
            Tab created;
            try { created = contributions.tabs().open(TabRegistry.APP_OWNER, KEY, "Errors", null, opened); }
            catch (RuntimeException | Error failure) { opened.close(); throw failure; }
            created.onClosed(opened::close);
            tab = created;
            view = opened;
        } else {
            incident.ifPresent(view::select);
            tab.select();
        }
        if (incident.isPresent()) SwingUtilities.invokeLater(view::revealSelection);
    }

    /** Test access: the open tab's view. */
    Optional<ErrorsView> view() { return tab == null || tab.closed() ? Optional.empty() : Optional.of(view); }

    /** Test access: the open tab. */
    Optional<Tab> tab() { return tab == null || tab.closed() ? Optional.empty() : Optional.of(tab); }
}
```

The ledger rows, the application's field and calls, `IncidentNotices`' list of what it leaves to the log, the tests
that name the class, the maintenance guide's line, and "Errors tab" in the comments:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit, regex
import ledger
A, T = "jasper-app/src/main/java/dev/jasper/app/application/", "jasper-app/src/test/java/dev/jasper/app/application/"
ledger.rename(A + "ErrorsWindow.java", A + "ErrorsTab.java", "ErrorsTab")
ledger.rename(T + "ErrorsWindowTest.java", T + "ErrorsTabTest.java", "ErrorsTabTest")
edit(A + "JasperApplication.java", [
    ("    private ErrorsWindow errorsWindow;\n", "    private ErrorsTab errorsTab;\n", 1),
    ("""        errorsWindow = new ErrorsWindow(contributions, auxiliary, incidents, new ErrorsView.Hooks(this::pluginName,""",
     """        errorsTab = new ErrorsTab(contributions, incidents, new ErrorsView.Hooks(this::pluginName,""", 1),
    ("""    /** Show more… on an incident's notification: the Errors window, with that incident selected. */
    private void showErrors(UUID incident) {
        if (errorsWindow != null && !quitting && !stopped) errorsWindow.show(Optional.of(incident));""",
     """    /** Show more… on an incident's notification: the Errors tab, in the main window, with that incident selected. */
    private void showErrors(UUID incident) {
        if (errorsTab != null && !quitting && !stopped) errorsTab.show(Optional.of(incident));""", 1)])
edit(A + "IncidentNotices.java", [("        ErrorsWindow.class.getName(), ConfigProblemNotices.class.getName());",
                                   "        ErrorsTab.class.getName(), ConfigProblemNotices.class.getName());", 1)])
edit(T + "IncidentNoticesTest.java", [('thrownIn("dev.jasper.app.application.ErrorsWindow$1")', 'thrownIn("dev.jasper.app.application.ErrorsTab$1")', 1)])
edit(T + "JasperApplicationIncidentsTest.java", [("action(ErrorsWindow.SHOW_ERRORS)", "action(ErrorsTab.SHOW_ERRORS)", 1)])
edit(T + "SystemNoticesTest.java", [("    @Test void aClickOnAnIncidentOpensTheErrorsWindowAtIt() {", "    @Test void aClickOnAnIncidentOpensTheErrorsTabAtIt() {", 1)])
edit("docs/app-maintenance.md", [("`IncidentNotices`, `NotificationsFeature`, `ErrorsWindow` or `ConfigProblemNotices` is left to the log",
                                  "`IncidentNotices`, `NotificationsFeature`, `ErrorsTab` or `ConfigProblemNotices` is left to the log", 1)])
# Its users' word for it in the application's comments: the Errors tab.
M = "jasper-app/src/main/java/dev/jasper/app/"
for path, count in [(M + "about/LicensesView.java", 2), (A + "IncidentNotices.java", 1), (A + "SystemNotices.java", 3),
                    (M + "notifications/Incident.java", 1), (M + "notifications/Incidents.java", 2),
                    (M + "notifications/NotificationRequest.java", 1), (M + "notificationui/ErrorsView.java", 1),
                    (M + "notificationui/NotificationStyle.java", 1), (T + "IncidentNoticesTest.java", 1)]:
    regex(path, r"\bErrors window\b", "Errors tab", count)
edit(A + "JasperApplication.java", [("    /** Built with the errors window, once plugins start. */", "    /** Built with the Errors tab, once plugins start. */", 1)])
edit(M + "notificationui/package-info.java", [(
    """ * EDT Swing views of the notification center: each window's card stack, the history panel, the Errors window's
 * view, and their text, style and focus helpers. Views subscribe to the center or the incidents and close their
 * subscriptions when closed; the application owns the center, mounts each window's card layer and closes it with the
 * window, and the Errors window closes its view.""",
    """ * EDT Swing views of the notification center: the main window's card stack, the history panel, the Errors tab's
 * view, and their text, style and focus helpers. Views subscribe to the center or the incidents and close their
 * subscriptions when closed; the application owns the center, mounts the main window's card layer and closes it with
 * the window, and the Errors tab closes its view.""", 1)])
PY
```

The tab carries no window chrome (Rulings 20): its view paints from the workspace route over its own surface and has no
Done, so Escape closes nothing; Jasper's own tabs take `TabRegistry.APP_OWNER` (Rulings 21); the view's tests and its
evidence build it as production does:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
# A content tab carries no window chrome (Rulings 20): the Errors view paints from the workspace route on its own surface,
# as a plugin's content tab does, and has no Done; the tab strip closes it. Jasper's own tabs have one owner (Rulings 21).
M, T = "jasper-app/src/main/java/dev/jasper/app/", "jasper-app/src/test/java/dev/jasper/app/"
edit(M + "contributions/TabRegistry.java", [(
    """    private Windows windows;
    private List<Tab> pending = List.of();""",
    """    /** The owner of Jasper's own content tabs, such as the Errors tab; a plugin's id is never {@code jasper}. */
    public static final String APP_OWNER = "jasper";
    private Windows windows;
    private List<Tab> pending = List.of();""", 1)])
V = M + "notificationui/ErrorsView.java"
edit(V, [
    (""" * The Errors tab's content (spec section 5): this session's incidents, most recently seen first, beside the
 * selected one's detail. One chrome kit view composes it as a manager window (the Vault manager's arrangement without
 * its rail): a list column of incidents; a detail column with the incident's heading (its summary, source and
 * operation, then Disable Plugin for a plugin's incident and Copy), its message and stack trace each in a detail card
 * and when it was seen; and the window footer band with the count, Open Log Folder, Clear and Done. The message and""",
     """ * The Errors tab's content (spec section 5; workspace tabs spec 6.6): this session's incidents, most recently seen
 * first, beside the selected one's detail. One kit view on the workspace route composes it as a manager (the Vault
 * manager's arrangement without its rail) and paints its surface, as a plugin's content tab does: a list column of
 * incidents; a detail column with the incident's heading (its summary, source and operation, then Disable Plugin for a
 * plugin's incident and Copy), its message and stack trace each in a detail card and when it was seen; and the footer
 * band with the count, Open Log Folder and Clear. The tab strip carries its title and closes it. The message and""", 1),
    ("""    static final String COPY = "copy", DISABLE = "disable", LOG_FOLDER = "log-folder", CLEAR = "clear", DONE = "done";""",
     """    static final String COPY = "copy", DISABLE = "disable", LOG_FOLDER = "log-folder", CLEAR = "clear";""", 1),
    ("""    private final Hooks hooks;
    private final Runnable done;
""", """    private final Hooks hooks;
""", 1),
    ("""     * @param kit the chrome kit context
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     * @param done closes the window (Done and Escape)
     */
    public ErrorsView(KitContext kit, Incidents incidents, Hooks hooks, ZoneId zone, Runnable done) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.done = Objects.requireNonNull(done, "done");""",
     """     * @param kit the workspace route's kit context: the view is a content tab's
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     * @throws IllegalArgumentException when {@code kit} is not on the workspace route
     */
    public ErrorsView(KitContext kit, Incidents incidents, Hooks hooks, ZoneId zone) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        if (kit.route() != dev.jasper.ui.theme.ThemeRoute.WORKSPACE)
            throw new IllegalArgumentException("The Errors tab paints from the workspace route, not " + kit.route());
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");""", 1),
    ("""        view = new KitView(kit, spec(), this::event);
        view.getAccessibleContext().setAccessibleName("Errors");""",
     """        view = new KitView(kit, spec(), this::event);
        // A content tab paints its route's surface, as a plugin's content tab does.
        view.paintsSurface(true);
        view.getAccessibleContext().setAccessibleName("Errors");""", 1),
    ("""                    case CLEAR -> incidents.clear();
                    case DONE -> done.run();
""", """                    case CLEAR -> incidents.clear();
""", 1),
    ("""            button(CLEAR, "Clear", new ControlState(true, any, false, ""), Nodes.ButtonKind.SECONDARY),
            button(DONE, "Done", ControlState.READY, Nodes.ButtonKind.PRIMARY));
        return new ViewSpec(new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Errors", Optional.empty(), List.of(columns),
            Optional.of(status()), actions, DONE, Nodes.FormSubmission.NONE));""",
     """            button(CLEAR, "Clear", new ControlState(true, any, false, ""), Nodes.ButtonKind.SECONDARY));
        // The WINDOW form's full-bleed body and pinned footer band; the kit draws no title in it ("Errors" names it for
        // assistive technology) and nothing cancels it: Escape leaves the tab open.
        return new ViewSpec(new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Errors", Optional.empty(), List.of(columns),
            Optional.of(status()), actions, "", Nodes.FormSubmission.NONE));""", 1),
    ("""    AbstractButton doneButton() { return (AbstractButton) NotificationCard.find(view, DONE); }
""", "", 1)])
edit(M + "application/JasperApplication.java", [(
    """            this::copyReport, incidentNotices::disablePlugin, this::openLogFolder), notificationCenter.clock().getZone(),
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));""",
    """            this::copyReport, incidentNotices::disablePlugin, this::openLogFolder), notificationCenter.clock().getZone(),
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE));""", 1)])
# Tests build the view as production does: on the workspace route, with no Done.
edit(T + "testsupport/KitTestSupport.java", [(
    """    /** Fresh bindings a test owns and closes, for example to publish a theme switch; EDT only. */""",
    """    /** A workspace context over Slate Cobalt Dark, as a content tab paints from, shared by the test JVM; EDT only. */
    public static KitContext workspaceContext() {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Kit contexts are EDT confined");
        if (shared == null) shared = bindings(true);
        return shared.context(ThemeRoute.WORKSPACE);
    }

    /** Fresh bindings a test owns and closes, for example to publish a theme switch; EDT only. */""", 1)])
N = T + "notificationui/"
edit(N + "ErrorsViewTestSupport.java", [(
    """    /** Done in the window footer band. */
    public static javax.swing.AbstractButton done(ErrorsView view) { return view.doneButton(); }""",
    """    /** The view's one kit view. */
    public static dev.jasper.ui.swing.KitView kitView(ErrorsView view) { return view.kitView(); }""", 1)])
E = N + "ErrorsViewTest.java"
edit(E, [
    ("    private int logFolders, dones;", "    private int logFolders;", 1),
    ("""        return track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.context(), incidents, new ErrorsView.Hooks(id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            id -> id.equals("dev.jasper.remote") ? "2.1.0" : "", HERE, copied::add, disabled::add, () -> logFolders++),
            ZoneOffset.UTC, () -> dones++));""",
     """        return track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.workspaceContext(), incidents, new ErrorsView.Hooks(id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            id -> id.equals("dev.jasper.remote") ? "2.1.0" : "", HERE, copied::add, disabled::add, () -> logFolders++),
            ZoneOffset.UTC));""", 1),
    ("""ErrorsView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),incidents,
            new ErrorsView.Hooks(id->"Plugin",id->"",HERE,copied::add,disabled::add,()->{}),ZoneOffset.UTC,()->{});""",
     """ErrorsView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE),incidents,
            new ErrorsView.Hooks(id->"Plugin",id->"",HERE,copied::add,disabled::add,()->{}),ZoneOffset.UTC);""", 2),
    ("""            var footer=List.of(view.logFolderButton(),view.clearButton(),view.doneButton());""",
     """            var footer=List.of(view.logFolderButton(),view.clearButton());""", 1),
    ("""            for(var action:List.of(view.disableButton(),view.copyButton(),view.logFolderButton(),view.clearButton(),view.doneButton())) {""",
     """            for(var action:List.of(view.disableButton(),view.copyButton(),view.logFolderButton(),view.clearButton())) {""", 1),
    ("""    /**
     * The window is a manager window: a WINDOW form whose body is a list column beside the detail column (the Vault
     * manager's arrangement without its rail), the incident's heading with its actions, the message and the trace in
     * detail cards, the sightings caption, and Open Log Folder, Clear and the right-most primary Done in the footer band.
     */
    @Test void theWindowIsAManagerListAndDetailWithTheFooterBand() {""",
     """    /**
     * The tab is a manager: a WINDOW form whose body is a list column beside the detail column (the Vault manager's
     * arrangement without its rail), the incident's heading with its actions, the message and the trace in detail cards,
     * the sightings caption, and Open Log Folder and Clear in the footer band. The tab strip closes it: no Done, and
     * Escape cancels nothing.
     */
    @Test void theTabIsAManagerListAndDetailWithTheFooterBand() {""", 1),
    ("""        assertThat(form.submission()).as("Enter on a row never closes the window").isEqualTo(dev.jasper.ui.model.Nodes.FormSubmission.NONE);
        assertThat(form.cancelAction()).as("Escape is Done").isEqualTo(ErrorsView.DONE);""",
     """        assertThat(form.submission()).as("Enter on a row submits nothing").isEqualTo(dev.jasper.ui.model.Nodes.FormSubmission.NONE);
        assertThat(form.cancelAction()).as("Escape leaves the tab open").isEmpty();
        assertThat(view.kitView().route()).isEqualTo(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
        assertThat(view.kitView().isOpaque()).as("a content tab paints its surface").isTrue();""", 1),
    ("""        assertThat(form.actions()).extracting(dev.jasper.ui.model.Nodes.Button::label).containsExactly("Open Log Folder", "Clear", "Done");
        assertThat(form.actions().getLast().kind()).isEqualTo(dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY);""",
     """        assertThat(form.actions()).extracting(dev.jasper.ui.model.Nodes.Button::label).containsExactly("Open Log Folder", "Clear");""", 1),
    ("""        var done = bounds(view.doneButton(), view);
        var clear = bounds(view.clearButton(), view);
        var folder = bounds(view.logFolderButton(), view);
        assertThat(folder.x + folder.width).isLessThanOrEqualTo(clear.x);
        assertThat(clear.x + clear.width).isLessThanOrEqualTo(done.x);
        assertThat(done.y).as("one footer row").isEqualTo(clear.y).isEqualTo(folder.y);
        assertThat(view.statusText()).isEqualTo("1 error this session");
        view.doneButton().doClick();
        assertThat(dones).as("Done closes the window").isEqualTo(1);""",
     """        var clear = bounds(view.clearButton(), view);
        var folder = bounds(view.logFolderButton(), view);
        assertThat(folder.x + folder.width).isLessThanOrEqualTo(clear.x);
        assertThat(clear.y).as("one footer row").isEqualTo(folder.y);
        assertThat(view.statusText()).isEqualTo("1 error this session");""", 1),
    ("""        var view = track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.context(), incidents, new ErrorsView.Hooks(id -> "<html><b>Evil</b>", id -> "", HERE, copied::add,
            disabled::add, () -> { }), ZoneOffset.UTC, () -> { }));""",
     """        var view = track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.workspaceContext(), incidents, new ErrorsView.Hooks(id -> "<html><b>Evil</b>", id -> "", HERE, copied::add,
            disabled::add, () -> { }), ZoneOffset.UTC));""", 1),
    ("""            var kit = bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var view = new ErrorsView(kit, incidents, new ErrorsView.Hooks(id -> id, id -> "", HERE, copied::add, disabled::add, () -> { }), ZoneOffset.UTC, () -> { });""",
     """            var kit = bindings.context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
            var view = new ErrorsView(kit, incidents, new ErrorsView.Hooks(id -> id, id -> "", HERE, copied::add, disabled::add, () -> { }), ZoneOffset.UTC);""", 1),
    ("""    @Test void closingStopsListening() {""",
     """    @Test void aChromeContextIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.context(), incidents,
            new ErrorsView.Hooks(id -> id, id -> "", HERE, copied::add, disabled::add, () -> { }), ZoneOffset.UTC))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("workspace route");
    }

    @Test void closingStopsListening() {""", 1)])
edit(N + "ThemeMigrationEvidenceTest.java", [(
    """            var view = new ErrorsView(kit, incidents, new ErrorsView.Hooks(id -> "Example plugin", id -> "1.0.0",
                new IncidentReport.Environment("baseline", "Headless", "JBR 25"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC, () -> { });""",
    """            // The Errors tab, as production mounts it: the workspace route on its painted surface.
            var view = new ErrorsView(controller.appearance().context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE), incidents, new ErrorsView.Hooks(id -> "Example plugin", id -> "1.0.0",
                new IncidentReport.Environment("baseline", "Headless", "JBR 25"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC);""", 1)])
edit(N + "Task9cNoticeEvidenceTest.java", [(
    """                    var errors=new ErrorsView(kit,incidents,new ErrorsView.Hooks(id->"Example plugin",id->"1.0.0",
                        new IncidentReport.Environment("task9c","Headless","JBR25"),text->{},id->{},()->{}),ZoneOffset.UTC,()->{});""",
    """                    var errors=new ErrorsView(themes.appearance().context(ThemeRoute.WORKSPACE),incidents,new ErrorsView.Hooks(id->"Example plugin",id->"1.0.0",
                        new IncidentReport.Environment("task9c","Headless","JBR25"),text->{},id->{},()->{}),ZoneOffset.UTC);""", 1)])
edit(M + "notificationui/package-info.java", [(
    " * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) on the chrome KitContext, mounted in",
    " * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) on the chrome KitContext (the\n"
    " * Errors tab's on the workspace one, as content), mounted in", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.documentation.*' --continue`
Expected: PASS (`ErrorsTabTest` 5; `ErrorsViewTest` with `aChromeContextIsRefused`). Then `./gradlew :jasper-app:check`:
PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app docs
git commit -F - <<'EOF'
Workspace tabs: the Errors tab

The Errors window becomes an app-owned content tab in the main window's
active group, under TabRegistry.APP_OWNER; Show more…, Notifications:
Show Errors and a system notification's click open it or select it with
their incident. Its view paints from the workspace route, as content
does, and has no Done: the tab strip closes it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: New Editor Window

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`, `ui/EditorWindow.java`,
  `plugins/editor/src/main/resources/plugin.toml`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: Task 2's `TabPlacement.NEW_WINDOW`.
- Produces: the action `dev.jasper.editor.new_window` ("New Editor Window", no default key, File menu after New Editor
  File); `EditorWindow.newUntitled(WindowOwner, TabPlacement)`.

- [ ] **Step 1: Write the failing test**

New Editor Window has no key, follows New Editor File in the File menu, and opens its untitled tab in a new child
window:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
T = "plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java"
edit(T, [("""    @Test void theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen() throws Exception {""",
"""    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
    @Test void newEditorWindowOpensAnUntitledDocumentInANewChildWindow() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.actionSpec("dev.jasper.editor.new_window")).hasValueSatisfying(spec -> {
                assertThat(spec.title()).isEqualTo("New Editor Window");
                assertThat(spec.defaultBinding()).as("no default key").isEmpty();
            });
            assertThat(host.menu("FILE")).containsSubsequence("item:dev.jasper.editor.new", "item:dev.jasper.editor.new_window");
            assertThat(host.invoke("dev.jasper.editor.new_window", main, null)).isTrue();
            var tab = host.contentTabs().getFirst();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(tab.window().isMain()).as("a child window").isFalse();
            assertThat(tab.window().id()).isNotEqualTo(main);
            assertThat(tab.window().tabs()).hasSize(1);
            assertThat(host.contentPresentation(tab).title()).startsWith("Untitled");
        }
    }

    @Test void theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: FAIL in `newEditorWindowOpensAnUntitledDocumentInANewChildWindow`: no action `dev.jasper.editor.new_window`.

- [ ] **Step 3: The action**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
E = "plugins/editor/src/main/java/dev/jasper/editor/"
edit(E + "EditorPlugin.java", [
    ("""    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show";""",
     """    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show",
        NEW_WINDOW = "dev.jasper.editor.new_window";""", 1),
    ("""            invoked -> window().newUntitled(invoked.window()));
""", """            invoked -> window().newUntitled(invoked.window()));
        // Workspace tabs spec 6.5: an untitled document in a child window of its own; no default key.
        context.actions().register(ActionSpec.of(NEW_WINDOW, "New Editor Window").withKeywords(List.of("editor", "new", "window", "untitled")),
            invoked -> window().newUntitled(invoked.window(), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW));
""", 1),
    ("""        file.add(NEW);
""", """        file.add(NEW);
        file.add(NEW_WINDOW);
""", 1)])
edit(E + "ui/EditorWindow.java", [
    ("""    public void newUntitled(WindowOwner owner) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(model.nextUntitledName(), sessionEnvironment(ref));
        add(session, ref); select(session);
    }""", """    public void newUntitled(WindowOwner owner) { newUntitled(owner, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP); }
    /** A new untitled document whose tab opens where {@code placement} puts it: New Editor Window's is a new child window. */
    public void newUntitled(WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(model.nextUntitledName(), sessionEnvironment(ref));
        add(session, ref, placement); select(session);
    }""", 1),
    ("""    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef) {
        EditorSettings settings = environment.settings().get();""",
     """    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef) {
        add(session, ownerRef, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP);
    }

    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef,
                     dev.jasper.sdk.ui.TabPlacement placement) {
        EditorSettings settings = environment.settings().get();""", 1),
    ("""        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab.view());""",
     """        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab.view(), placement);""", 1)])
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.9.0"', 'version = "0.10.0"', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:check`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor
git commit -F - <<'EOF'
Workspace tabs: New Editor Window

The Editor's New Editor Window, with no default key, opens an untitled
document in a new child window through TabPlacement.NEW_WINDOW.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/plugin-ui.md`, `docs/sdk-architecture.md`,
  `docs/credential-vault.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/keyboard-shortcuts.md`,
  `docs/README.md`, `docs/configuration.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`,
  `docs/notifications.md`, `docs/packaging.md`, `docs/remote.md`, `docs/STATUS.md` (two links, then the entry at the
  top), this plan's status banner (line 3)
- Modify (comments that still named plugin windows): `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHeader.java`,
  `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, the Editor's `api/Editor.java` and
  `ui/EditorWindow.java`

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentence true (AGENTS.md "Documentation edits"); the SDK README gains the
section its version line points to:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan4b"); from edits import edit
R = "jasper-sdk/README.md"
edit(R, [
    ("`Panels`, `PanelSpec`, `PanelHost`, `PanelHeader`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `OverlaySpec`, `PluginWindow`, `PluginDialog`, `ContentTab`,",
     "`Panels`, `PanelSpec`, `PanelHost`, `PanelHeader`, `Rail`, `Windows`, `DialogSpec`, `OverlaySpec`, `PluginDialog`, `ContentTab`, `TabPlacement`,", 1),
    ("(0.20.0 is a breaking boundary for main and child windows; 0.19.0 is one too,",
     "(0.20.0 is a breaking boundary, described under Main and child windows below; 0.19.0 is one too,", 1),
    ("""## Tabs of every kind (SDK 0.19.0)
""", """## Main and child windows (SDK 0.20.0)

Jasper has one main window, which keeps an empty workspace when its last tab leaves, and any number of child windows,
which close with their last tab; `WindowHandle.isMain()` tells them apart. Panels, the toolbar and the status bar are the
main window's: a panel has one instance, there, and a toggle from a child window shows it in the main window.
`Windows.openTab(spec, owner, view, placement)` takes a `TabPlacement`: `ACTIVE_GROUP` (the three-argument form),
`SPLIT_RIGHT` or `SPLIT_DOWN` (a new tab group beside the active one) or `NEW_WINDOW` (a new child window).
`ContentTab.moveToNewWindow()` moves a tab to a new child window, `moveToMainWindow()` to the main window, and `window()`
is the window that holds it. A plugin's own view is a panel or a content tab; a dialog belongs to a Jasper window, a
content tab or an anchor, and a shortcut context is global, a terminal, the plugin's content tabs or one of its panels.
In the testkit the first window `FakePluginHost.addTerminalWindow` adds while no main window is open is the main window,
a window's tabs sit in groups, and `contentTab` and `contentView` find a plugin's content tab by its title.

## Tabs of every kind (SDK 0.19.0)
""", 1),
    ("pickers to a live Jasper window or plugin surface.", "pickers to a live Jasper window or content tab.", 1),
    ("after plugin windows and content tabs;\na `false` decision cancels,", "after content tabs;\na `false` decision cancels,", 1),
    ("""`global()` (the default), `terminal()` (a terminal pane), `content()` (the plugin's content tabs), `panel(id)` or
`window(id)` (one of the plugin's own panels or windows; window shortcuts take effect once plugin windows get the
Keymap, plan K4). Where focus is,""", """`global()` (the default), `terminal()` (a terminal pane), `content()` (the plugin's content tabs) or `panel(id)`
(one of the plugin's own panels). Where focus is,""", 1)])
A = "docs/plugin-authoring.md"
edit(A, [
    ("""`withContext(ShortcutContext.terminal())`,
  `content()`, `panel(id)` or `window(id)` limits where the key applies (SDK 0.11.0): the most specific
  context around focus wins, so a panel action may reuse a Global key, and a key whose action does not
  apply reaches the focused component. A `window(id)` shortcut takes effect once plugin windows get the
  Keymap (plan K4).""", """`withContext(ShortcutContext.terminal())`,
  `content()` or `panel(id)` limits where the key applies (SDK 0.11.0): the most specific
  context around focus wins, so a panel action may reuse a Global key, and a key whose action does not
  apply reaches the focused component.""", 1),
    ("""  of your own controls in the main toolbar, one per window, built when the window opens and closed with it or your
  registration.""", """  of your own controls in the main toolbar, which only the main window has, built when the window opens and closed
  with it or your registration.""", 1),
    ("- **Status items** are global: one handle updates the item in every window.",
     "- **Status items** are global: one handle updates the item in the main window's status bar.", 1),
    ("A panel is an owned `UiView` docked as a tool window in every Jasper window, as in IntelliJ:",
     "A panel is an owned `UiView` docked as a tool window in the main window, as in IntelliJ:", 1),
    ("""Register a panel once; Jasper asks your factory for one
instance per window, the first time the panel is shown there, and gives the panel a stripe icon, a""",
     """Register a panel once; Jasper asks your factory for it the
first time the panel is shown (a toggle from a child window shows it in the main window), and gives the panel a stripe icon, a""", 1),
    ("""A rail button, on the left stripe after the panel icons, runs one of your actions. A plugin window
is a frame Jasper builds: icon, macOS title bar, menu bar, theme tracking and remembered bounds are
its job; the content is yours. Dialogs belong to a Jasper window or to one of your windows, so
they are parented correctly without your plugin ever seeing a frame.""",
     """A rail button, on the left stripe after the panel icons, runs one of your actions. A view of your own
that is not a panel is a content tab (see Documents in workspace tabs): Jasper draws its tab and its window;
the content is yours. Dialogs belong to a Jasper window, a content tab or an anchor, so
they are parented correctly without your plugin ever seeing a frame.""", 1),
    ("""- `WindowSurface.onClosing` guards run when the user closes the window; returning false keeps
  it open. Since SDK 0.8.4, quitting or restarting Jasper asks the guards of your open windows first, so a guard
  can offer to save; a guard that returns false keeps Jasper running. `close()` closes at once.""",
     """- `WindowSurface.onClosing` guards run when the user closes the dialog; returning false keeps
  it open. `close()` closes at once.""", 1),
    ("""after plugin windows and
  content tabs, even when your plugin has no window:""", """after content tabs, even
  when your plugin has no tab:""", 1),
    ("""- In SDK 0.8.4+, `window.onCloseCommand(() -> …)` lets your content handle File → Close Window and
  Cmd/Ctrl+W, for example by closing a tab; return true when you handled it and false to let the
  window close. The close button is not affected.
""", "", 1),
    ("""- In SDK 0.7.1+, call `window.chooseFile("Choose file", Optional.of(currentPath))` (or
  `dialog.chooseFile`) from a Browse button on the UI thread.""",
     """- In SDK 0.7.1+, call `dialog.chooseFile("Choose file", Optional.of(currentPath))` from a
  Browse button on the UI thread.""", 1),
    ("- The application closes a plugin's panels, windows and dialogs when the plugin stops.",
     "- The application closes a plugin's panels, content tabs and dialogs when the plugin stops.", 1),
    ("- Jasper stays running while any plugin window is open, even when no other window is.\n", "", 1),
    ("which opens the Errors window at its stack trace.", "which opens the Errors tab at its stack trace.", 1),
    ("""`context.panels().toggle(panelId, window)` opens or hides your own
registered panel in a Jasper window. It creates the panel lazily and reuses that
window's instance.""", """`context.panels().toggle(panelId, window)` opens or hides your own
registered panel in the main window, which comes to the front. It creates the panel lazily
and reuses it.""", 1),
    ("One handle updates every window; close removes it.", "One handle updates the main window's status bar; close removes it.", 1),
    ("""with a live Jasper
window or a shown plugin window.""", """with a live Jasper
window or one of your content tabs.""", 1),
    ("continues to select one file for window-owned workflows.", "continues to select one file for dialog-owned workflows.", 1),
    ("""follow moves to another window. `move(true)` opens a separate app-managed workspace;
`move(false)` returns to the last main workspace. Preserve the component/session during moves.""",
     """follow moves to another window. `moveToNewWindow()` moves the tab to a new child window, `moveToMainWindow()` to the
main window, and `window()` is the window that holds it; since SDK 0.20 `openTab(spec, owner, view, placement)` opens
it in the active group (`TabPlacement.ACTIVE_GROUP`), in a new group beside it (`SPLIT_RIGHT`, `SPLIT_DOWN`) or in a
new child window (`NEW_WINDOW`). Preserve the component/session during moves.""", 1)])
edit("docs/plugin-ui.md", [("your model outside the view: Jasper asks for one instance per window.",
                            "your model outside the view: Jasper builds it once, in the main window.", 1)])
D = "docs/sdk-architecture.md"
edit(D, [
    ("## Panels, the rail and plugin windows", "## Panels, the rail and plugin dialogs", 1),
    ("""Each window's `WindowContributions` keeps one lazily built instance per panel and docks the panels
in the window's `DockState`""", """The main window's `WindowContributions` keeps one lazily built instance per panel and docks the panels
in the window's `DockState`""", 1),
    ("""Requests to show, hide or toggle a panel travel through the model tagged with a window id, so an
action or a `PanelHost` reaches exactly one window.""", """Requests to show, hide or toggle a panel travel through the model tagged with a window id, and
`Contributions.panelRoute` sends a child window's to the main window.""", 1),
    ("Plugin windows follow the `WindowFrame`/`WindowContent` split:", "Plugin dialogs and the application's auxiliary windows follow the `WindowFrame`/`WindowContent` split:", 1),
    ("""an unreadable file falls back to defaults, writes are atomic, and a version 1 file migrates on read.
The process stays alive while a plugin window is open.
""", """an unreadable file falls back to defaults, writes are atomic, and a version 1 file migrates on read.
""", 1),
    ("""`ShortcutContext` (`global`, `terminal`, `content`, `panel(id)`, `window(id)`) travels with `ActionSpec`. `HostedUi`
rejects a panel or window id outside the plugin's namespace""", """`ShortcutContext` (`global`, `terminal`, `content`, `panel(id)`) travels with `ActionSpec`. `HostedUi`
rejects a panel id outside the plugin's namespace""", 1),
    ("otherwise the key is not consumed. Window contexts apply once plugin windows get the Keymap (plan K4).",
     "otherwise the key is not consumed.", 1),
    ("### Close command, quit guards and the save chooser (SDK 0.8.4)", "### Quit guards and the save chooser (SDK 0.8.4)", 1),
    ("""`PluginWindow.onCloseCommand` lets a plugin window's content handle File → Close Window and
Cmd/Ctrl+W, for example by closing a tab. `NativeShells`' Close Window item (or, in a classic look's
frame, which has no menu row, its shortcut) calls `AuxiliarySurface.closeCommand()`, which asks the
handlers newest first and otherwise closes like
the close button, through the guards. The close button itself (`windowClosing`) still calls
`requestClose()`. With the macOS screen menu bar, the menu accelerator fires before the content's
key bindings see Cmd+W, so a key binding could not do this.

Quit and restart ask plugin windows first: `JasperApplication.quit()` and `restart()` call
`AuxiliaryWindows.mayQuit()`, which asks every open plugin window's guards in creation order (not
dialogs or overlays) without closing anything, before `quitting` is set and before
`ApplicationShutdown` arms the exit deadline.""", """Quit and restart ask first: `JasperApplication.quit()` and `restart()` call `guardsAllowQuit()`, which asks
every content tab's closing guard without closing anything, before `quitting` is set and before
`ApplicationShutdown` arms the exit deadline.""", 1),
    ("which asks only `windowsMayClose()` (content tabs and plugin windows), so", "which asks only `windowsMayClose()` (content tabs), so", 1),
    ("""opening an existing key selects its live tab. Null or closed originating windows use the
last main workspace, creating one when needed. Foreign owners are rejected.""", """opening an existing key selects its live tab. Null or closed originating windows use the
main window, restoring it when needed; a `TabPlacement` (SDK 0.20) opens the tab in the active group, a new group
beside it or a new child window. Foreign owners are rejected.""", 1),
    ("focus callback, and moves its retained component between main and separate workspaces.",
     "focus callback, and moves its retained component to a new child window or the main window.", 1)])
edit("docs/credential-vault.md", [
    ("Later use unlocks the vault and opens its manager window.", "Later use unlocks the vault and opens its manager in a tab.", 1),
    ("verify only one manager window opens.", "verify only one manager tab opens.", 1)])
edit("docs/editor.md", [
    ("edits local and remote text files in tabs in one Editor window,", "edits local and remote text files in tabs of Jasper windows,", 1),
    ("and **Open…** inside the Editor window.", "and **Open…** in an editor tab's ⋯ menu.", 1),
    ("""- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside the Editor window, for an untitled tab.
- Drop files from the desktop onto the Editor window. Folders are ignored.""",
     """- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside an editor tab, for an untitled tab; **New Editor Window**
  in the File menu or the palette (no default shortcut) opens the untitled tab in a new child window.
- Drop files from the desktop onto an editor tab. Folders are ignored.""", 1)])
edit("docs/editor-verification.md", [
    ("opens the chosen files in tabs in one Editor window.", "opens the chosen files in tabs in that window.", 1),
    ("Dropping a file from Finder onto the Editor window opens it;", "Dropping a file from Finder onto an editor tab opens it;", 1)])
edit("docs/keyboard-shortcuts.md", [
    ("## Editor window\n\nThe bundled Editor's window has its own shortcuts,", "## Editor tabs\n\nThe bundled Editor's tabs have their own shortcuts,", 1)])
edit("docs/README.md", [("editing local and remote files in tabs in the Editor window,", "editing local and remote files in tabs,", 1)])
edit("docs/configuration.md", [
    ("Content tabs and plugin windows are asked first, exactly as when you close them,", "Content tabs are asked first, exactly as when you close them,", 1),
    ("state, and plugin window bounds.", "state, and the bounds of Settings, About and Licenses.", 1)])
edit("docs/app-maintenance.md", [
    ("it is kept for the Errors window,", "it is kept for the Errors tab,", 1),
    ("([guide](notifications.md#the-errors-window),", "([guide](notifications.md#the-errors-tab),", 1),
    ("opens the Errors window for an incident,", "opens the Errors tab for an incident,", 1),
    ("""because `quit()` goes through `pluginWindowsMayClose()` (a content tab's or a plugin window's closing
guard, or a plugin quit guard).""", """because `quit()` goes through `guardsAllowQuit()` (a content tab's closing guard or a plugin quit
guard).""", 1)])
edit("docs/app-architecture.md", [
    ("a plugin window's File › Quit,", "an auxiliary window's File › Quit,", 1),
    ("each window's CardLayer, the history panel, the Errors window's ErrorsView,", "the main window's CardLayer, the history panel, the Errors tab's ErrorsView,", 1),
    ("the Errors window closes its view when it closes.", "the Errors tab closes its view when it closes.", 1)])
edit("docs/notifications.md", [
    ("which opens the Errors window at it.", "which opens the Errors tab at it.", 1),
    ("## The Errors window", "## The Errors tab", 1),
    ("**Show more…** opens the Errors window at that incident,", "**Show more…** opens the Errors tab at that incident, in the main window,", 1),
    ("Errors window for an internal error,", "Errors tab for an internal error,", 1),
    ("open the Errors window when clicked,", "open the Errors tab when clicked,", 1)])
edit("docs/packaging.md", [("(or another plugin window that asks before closing)", "(or another content tab that asks before closing)", 1)])
edit("docs/remote.md", [
    ("There is one remote browser in\neach window,", "There is one remote browser, in\nthe main window,", 1),
    ("in a tab of the Editor window;", "in an editor tab;", 1),
    ("(the Transfers panel under the Enterprise look), in every window:", "(the Transfers panel under the Enterprise look), in the main window:", 1)])
# Two earlier STATUS entries link to headings this task renames; the link check reads STATUS too.
edit("docs/STATUS.md", [
    ("[guide](notifications.md#the-errors-window)", "[guide](notifications.md#the-errors-tab)", 1),
    ("(sdk-architecture.md#panels-the-rail-and-plugin-windows)", "(sdk-architecture.md#panels-the-rail-and-plugin-dialogs)", 1)])
# Text the earlier passes missed, some of it wrapped across lines (the check below joins lines).
edit(A, [
    ("""`host.panels()`, `host.openPanel(id, windowId)`, `host.rail()`, `host.windows()` and
`host.requestClose(windowId)` cover panels and windows.""", """`host.panels()`, `host.openPanel(id)` (in the main window), `host.rail()`, `host.windows()` and
`host.requestClose(windowId)` cover panels, the rail and dialogs.""", 1),
    ("""off. The owner may be a Jasper window, one of
your windows or a `UiAnchor`;""", """off. The owner may be a Jasper window, one of
your content tabs or a `UiAnchor`;""", 1)])
edit("docs/plugin-ui.md", [("| Sheet attached to one of your windows |", "| Sheet attached to a Jasper window or one of your content tabs |", 1)])
edit(D, [
    ("It dispatches the same per-window lazy toggle as the rail;", "It dispatches the same lazy toggle as the rail, in the main window;", 1),
    ("### Per-window panel header metadata (SDK 0.14.2)", "### Panel header metadata (SDK 0.14.2)", 1),
    ("""The testkit exposes `panelHeader(id, windowId)`
for inspecting and invoking the same per-window contract (and `closePanel(id, windowId)` to discard an instance).""",
     """The testkit exposes `panelHeader(id)`
for inspecting and invoking the same contract (and `closePanel(id)` to discard the panel).""", 1)])
edit("docs/credential-vault.md", [("auto-lock and vault file in the status row beside **Done**.", "auto-lock and vault file in the status row.", 1)])
edit("docs/editor.md", [("""Closing the Editor
window, or quitting or restarting Jasper, asks""", """Closing a window with
editor tabs, or quitting or restarting Jasper, asks""", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHeader.java", [
    ("/** Per-window panel header metadata. Set through", "/** A panel's header metadata. Set through", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ("        // Plugins closed their own windows while stopping; this is the safety net, and the state's last write.",
     "        // Plugins closed their own dialogs while stopping; this is the safety net, and the state's last write.", 1)])
E = "plugins/editor/src/main/java/dev/jasper/editor/"
edit(E + "api/Editor.java", [
    ("     * Opens a local file in a tab, or selects the tab already showing it, and brings the Editor window to the front.",
     "     * Opens a local file in a tab, or selects the tab already showing it, and brings its window to the front.", 1),
    ("""    /** Opens in the originating workspace, or the last main workspace when null.
     * @param file local file
     * @param owner originating workspace
     */""", """    /** Opens in the window the request came from, or the main window when null.
     * @param file local file
     * @param owner the Jasper window or content tab the request came from
     */""", 1)])
edit(E + "ui/EditorWindow.java", [
    ("    /** Opens a file in the originating workspace; null chooses the last main workspace. */",
     "    /** Opens a file in the window the request came from; null chooses the main window. */", 1)])
PY
```

Then look for what is left, with every guide's and comment's lines joined, so a phrase wrapped across two lines is
found too (Rulings 18): the guides for every removed name and window phrase, the main sources for the phrases a comment
would use (`KitCompatibility` names the removed types on purpose):

```bash
python3 -I - <<'PY'
import pathlib, re, subprocess
# Wrapped phrases count: every run of whitespace is one space before matching.
DOCS = re.compile(r"[Pp]lugin windows?\b|[Yy]our (own )?windows\b|their own windows|\bPluginWindow\b|\bWindowSpec\b|"
                  r"Windows\.create|onCloseCommand|closeCommand|setTitleActions|titleStrip|isSeparate|pluginWindowsMayClose|"
                  r"move\((true|false)\)|\bwindow\(id\)|Errors window|Editor window|[Mm]anager window|[Pp]er-window (panel|lazy|contract)|"
                  r"last main workspace|separate (app-managed )?workspace|(openPanel|panelHeader|panelView|closePanel)\(id, windowId\)|"
                  r"beside \*\*Done\*\*")
CODE = re.compile(r"[Pp]lugin windows?\b|one of your windows|their own windows|Errors window|[Pp]er-window panel|"
                  r"last main workspace")
def files(*paths):
    return subprocess.run(["git", "ls-files", *paths], capture_output=True, text=True, check=True).stdout.split()
left = 0
for pattern, names in [(DOCS, [n for n in files("README.md", "jasper-sdk/README.md", "docs", ":!docs/superpowers", ":!docs/STATUS.md", ":!docs/design") if n.endswith(".md")]),
                       (CODE, [n for n in files("jasper-sdk/src/main", "jasper-sdk-testkit/src/main", "jasper-app/src/main", ":(glob)plugins/*/src/main/**",
                                                ":!jasper-app/src/main/java/dev/jasper/app/plugins/KitCompatibility.java") if n.endswith(".java")])]:
    for name in names:
        text = re.sub(r"\s+", " ", pathlib.Path(name).read_text(encoding="utf-8"))
        for match in pattern.finditer(text):
            left += 1
            print(name, "|", text[max(0, match.start() - 60):match.end() + 40])
print("none left" if left == 0 else f"{left} left")
PY
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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 4a's
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 4b: plugin windows become tabs — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-4b-plugin-windows.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). The SDK is 0.20.0, a breaking boundary: every
bundled manifest, the external example and every guide range declare `sdk = ">=0.20.0, <0.21"`; the Vault is 0.6.0,
the sample 0.3.0, the Editor 0.10.0, Remote 0.13.0 and File Server 0.5.0, and every cross-plugin range still holds.
`WindowHandle.isMain()` tells the main window from a child window; `Windows.openTab(spec, owner, view, placement)` takes
a `TabPlacement` (`ACTIVE_GROUP`, the three-argument form; `SPLIT_RIGHT` and `SPLIT_DOWN`, a new group beside the
active one; `NEW_WINDOW`, straight into a child window opened for the tab, the owner's window untouched, reported as
`WINDOW_OPENED` then `TAB_OPENED`); `ContentTab.moveToNewWindow()`, `moveToMainWindow()` and `window()` replace
`move(boolean)` and `isSeparate()`. `Windows.create`, `PluginWindow`, `WindowSpec`, `ShortcutContext.window` and
`KeyContext.WINDOW` are gone, and `KitCompatibility` refuses them as 0.20's; so is the auxiliary windows' title-action,
content-size and close-command chain that only plugin windows used (Close Window calls `requestClose()`). A content tab
carries no window chrome: the Vault manager is a content tab (`VaultManagerTab`; the ROWS actions are its body's first
row) without Done, the sample's About is a tab that owns its Details sheet, and the Errors window is `ErrorsTab`, an
app-owned content tab (`TabRegistry.APP_OWNER`, `jasper`, key `errors`) in the main window whose view paints from the
workspace route and has no Done. Panels, the toolbar and the status bar are the main window's in the SDK's words; a
child window's panel toggle shows the one panel in the main window in the fake as in the application, the testkit's
and the contract harness's panel methods take no window, Remote and File Servers keep one panel's state, and Remote's
Sessions panel follows only its own window's row. The Editor has New Editor Window (`dev.jasper.editor.new_window`, no
key). App side: `WindowEntry.main`, `WorkspaceHost.attachBeside(tab, group, side)` (any group), `TabRegistry.Placement`,
`TabRegistry.Windows.emptyWindow`, `guardsAllowQuit`. The testkit's `FakeWorkspace` and the app's `TerminalFixture` keep
a window's tabs in groups (most recently used first), make the first window opened while none is main the main window,
which outlives its last tab, and close a child window with its last tab after the move reports `TAB_MOVED`;
`FakePluginHost.contentTab` and `contentView` find a content tab by title; `PluginContractTest` checks placements, the
moves, the main window's rule and the one panel on both runtimes. Rulings (plan, "Rulings"): <one clause each, 1 to
21>. Deviations: the classic Vault manager departs from Enterprise's 07 Credential Vault mocks, which draw it as a window
with Done over a dialog band: `ClassicVaultRenderTest` pins it to the render (<each re-pinned constant, old → new>);
<any other, or none>. THD Sourcing Crypto (`~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1`
at `3740e4e`, not yet rebuilt for 0.18) stays skipped: plan 6 bumps the SDK again (restorers), so rebuild it once,
after the series' last SDK bump, against the jars `sdkRelease` then publishes. That rebuild carries every change since
0.17.1: its `plugin.toml` (`sdk = ">=0.16.1, <0.18"`) and `PluginPackagingTest.java:49` move to the final range, and
`build.gradle.kts:8` and `README.md:28` name the final version; 0.18's rename applies (`CryptoForm.java:112` and
`CryptoFormTest.java:61` read `ChoiceKind.SEGMENTED`, now `EXCLUSIVE`); 0.19's one pane per terminal tab applies
(`CryptoFormTest.java:39-41` adds two panes to one tab: give `other` its own tab,
`host.addTerminalPane(host.addTerminalTab(window, "zsh"), zsh())`, after which focusing `other` selects its tab); 0.20
needs nothing more (its dialog is owned by the action's window, and its tests read `FakePluginHost.windows()` and
`windowView`, which list dialogs, in one main window, and open no panel). Left for plan 5: tear-out and drag-back (a
child window opened at a screen point and sized like the source group, through 4a's `moveToNewWindow(Tab, Rectangle)`
and `windowsByActivation`, and a drop beside any group through `WorkspaceHost.attachBeside(tab, group, side)`). Left
for plan 6: `ContentSpec` restore state, `Windows.restorer` and the Editor's restorer, the next SDK bump, and restoring
the Vault manager's tab; Jasper's own tabs (`TabRegistry.APP_OWNER`, the Errors tab) are never saved.
`./gradlew check --continue`: **<counts>**. GUI checks for the user: the Vault manager opens as a Credential Vault tab
in the window that asked (Cmd+Shift+\ twice opens one tab) with no Done, Escape leaves it open and the tab's close
button closes it, its editors attach to that window, and Lock Vault with an editor open clears it; under the Enterprise
look, the manager tab beside the 07 Credential Vault mock (the recorded deviation: no Done, no dialog band under the
status row); Move Tab to New Window on the manager keeps its state; the sample's About tab (with the sample enabled) and
its Details sheet; Show more… on an error notification opens the Errors tab in the main window from a child window too,
with no Done; New Editor Window from the File menu and the palette opens an untitled document in a child window and
leaves the window it came from as it was; a tool window shortcut, SFTP's Open SFTP here and a tunnel's panel from a
child window show the panel in the main window; Settings, About and Licenses close with Close Window and Cmd/Ctrl+W;
Cmd+Q with an unsaved editor tab in a child window asks once. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md jasper-sdk jasper-app plugins docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for plugin windows as tabs

The guides describe SDK 0.20's main and child windows, placements and
moves, the Vault manager, About and Errors tabs, panels in the main
window and New Editor Window, and the comments that still named plugin
windows follow; STATUS records plan 4b with its rulings, the 07 mock
deviation, the THD Sourcing Crypto hand-off and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 6.5's New Editor Window and `TabPlacement.NEW_WINDOW` (Tasks 7, 2); 6.6's Errors tab (Task 6); 14 row
  by row: `WindowHandle.isMain()` (Task 2), `Windows.openTab`'s placement (Task 2), `ContentTab`'s moves (Task 2),
  `Windows.create`, `PluginWindow`, `WindowSpec`, `ShortcutContext.Kind.WINDOW` removed (Task 4), Panels in the main
  window only and the factory once per main window, in the fake too (Task 5), `OverlaySpec`'s child-window owner (Task
  2), `WINDOW_*` and `TAB_*` from child windows in the fakes (Task 2), the plugins (Vault and sample Task 3, Remote and
  File Servers Task 5, Editor Task 7), the testkit fakes and the contract suites (Tasks 2, 4, 5), the THD hand-off (Task
  8); 15's main window with an empty workspace and a child window closing with its last tab, in the fakes (Task 2); 16's
  `Windows.create`, `PluginWindow`, `WindowSpec`, `ShortcutContext.Kind.WINDOW`, `KeyContext.WINDOW` (Task 4); 17's SDK
  line: contract tests on the app and the testkit (Tasks 2, 5). Out of scope: plan 5's tear-out (it gets
  `WorkspaceHost.attachBeside` for any group), plan 6's restore state and restorers (it gets `TabRegistry.APP_OWNER`,
  whose tabs it never saves).
- Review findings applied: no window chrome on a content tab (Rulings 8, 16, 20; Tasks 3 and 6), the dead chain gone
  (Rulings 13, Task 4), `NEW_WINDOW` straight into a new child window (Rulings 5, Task 2), the fake's panels without a
  window (Rulings 14, Task 5), the Sessions row filter (Task 5 Step 5), Remote's and File Server's versions (Rulings 1),
  rewritten rather than dropped tests (Rulings 11), `KitCompatibilityTest`'s class-entry probes and the dead
  `Windows#create` row (Task 4), `BundledSamplePluginTest`'s version (Task 3), SFTP's Back from a child window (Task 5),
  File Server's child window (Task 2), and the wrapped stale phrases (Task 8's check).
- Every quoted anchor was matched, at its stated count, in a replay of plans 1, 2, 3a, 3b and 4a and of Tasks 1-8 in
  order; nothing was compiled or run. The documents' links and snippets were checked with a Python copy of
  `AppDocumentationTest`'s rules, and Task 8's stale-text check prints `none left` on the replay. Not verifiable without
  running: the values Task 3 Step 6 re-pins in `ClassicVaultRenderTest`, which the executor measures.
- Type consistency: `TabPlacement`, `TabRegistry.Placement`, `TabRegistry.Windows` (five components) and `APP_OWNER`,
  `WorkspaceHost.Beside`/`attachBeside`, `WindowEntry.main`, `ContentTab.moveToNewWindow`/`moveToMainWindow`/`window`,
  `WindowHandle.isMain`, `FakeWorkspace.Group`/`main()`, `TerminalFixture.addMainWindow`, `VaultManagerTab`,
  `ErrorsTab`, `ErrorsView`'s four-argument constructor, `KitTestSupport.workspaceContext`, `FakePluginHost.contentTab`/
  `contentView` and the window-less `openPanel`/`panelView`/`panelHeader`/`closePanel` keep one name and one signature
  from the task that creates them to the last task that uses them.
