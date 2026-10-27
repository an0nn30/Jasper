# Full windows 1: every window is a full window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every Jasper window has all of Jasper's chrome and its own tool windows; the main/child split goes; cmd+n opens a
new full window; anything without a window goes to the most recently active one; SDK 0.23.0.

**Architecture:** `WindowContent` stops gating chrome on a role (Task 1), then `WindowManager` keeps one kind of window
in a list with an activation order, and every "main window" route becomes "the most recently active window, restored
first when none is open" (Task 2). Commands follow (Task 3), then the SDK (Task 4) and the plugins whose panels assumed
one instance (Task 5). Relaunch still restores every saved window and the dock is still shared through `ui-state.toml`;
plans 3 and 4 change both.

**Tech Stack:** Java 25 (JBR 25), Swing, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md`](../specs/2026-10-26-jasper-full-windows-design.md)
(sections 5, 9, 10, 13 plan 1, 14 risk 1). Read it first; this plan argues from it.

## Global Constraints

- Work in the worktree `.claude/worktrees/full-windows` on branch `claude/full-windows`; never build in
  `~/projects/moray` itself (the user's running app uses its jars). Never launch the GUI or a benchmark.
- `./gradlew` only. Each task ends with the module checks it names passing; Task 6 runs `./gradlew check`.
- MVP: remove outright, no deprecation, no migration (`jasper-mvp-no-compat`). Old `workspace.toml` files with a `role`
  key still load (the key is no longer read); plan 3 changes the file's version.
- One commit per task, message ending with the two attribution lines:
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source.
- Naming copy, verbatim: action `new_window`, label **New Window**, keys `cmd+n` (macOS) / `ctrl+shift+n` (elsewhere);
  **Move Tab to Window** (a submenu listing each other window by its active tab's title); the menu bar item's entry
  **New Window**.
- SDK version `0.23.0`; every bundled plugin's `sdk = ">=0.23.0, <0.24"` and its `version` one minor up; the external
  example's default SDK `0.23.0`.
- "The most recently active window" is `WindowManager.lastActive()`: the open window that came to the front last, else
  the one opened last.

## Review Focus

1. **Closing one of several windows** (title bar or Close Window) closes only that window after its own tabs' guards;
   whichever window was opened first, the others stay; closing the last one hands over to residency. → Task 2
   `closingOneOfSeveralWindowsLeavesTheOthersOpen`, `closingTheLastWindowReachesTheClosedHookWithNoWindowLeft`.
2. **Owner-less requests go to the window the user was in**, not the first opened: a plugin tab with no owner, a
   handoff's paths, the Errors tab. → Task 2 `aPluginsTabWithNoOwnerOpensInTheMostRecentlyActiveWindow`.
3. **A request while no window is open opens exactly one restore**, then runs there, including two requests in one EDT
   turn. → Task 2 `twoRequestsWhileNoWindowIsOpenRestoreOnce`.
4. **A panel shown from window B shows in B**, and closing window A (its panel instance's `onClosed`) leaves B's panel
   working. → Task 5's per-plugin `showsInTheWindowItIsAskedFrom` and `closingOneWindowsPanelKeepsTheOthers`.
5. **Zen, rail and status bar toggles act on their own window only.** → Task 1
   `zenRailAndStatusBarToggleOnlyTheirOwnWindow`.
6. **Quit with a refusing guard in a later window keeps every window.** → Task 2
   `quitAsksEveryWindowsGuardsAndARefusalKeepsThemAll`.

---

### Task 1: Every window's content has the full chrome

The chrome gates go; `WindowContent` keeps its `role` only for what Task 2 rewrites (moves, restore, the empty
workspace).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`:409`, `:1287`, `:1322`, `:2083`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowFrame.java:94` (every window mounts with `WindowContent.mount`; `mountChild` and `mountWindowMenu`'s child branch go)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java:160-162`, `WindowCommands.java:151-158`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java:88-112`, `:664-666` (`docks` always true; every window persists)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:588-595` (every window gets `uiState`, the detached shells when `nativeToolWindows`, and `notificationViews.attach`)
- Test: rename `jasper-app/src/test/java/dev/jasper/app/workspace/WindowRolesTest.java` to `FullWindowChromeTest.java`; adjust `WindowShellTest`, `ClassicWindowsTitleTest`, `ClassicMacTitleTest`, `DetachedToolWindowsTest`, `NotificationsFeatureTest`

**Interfaces:**
- Produces: a `WindowContent` built with `WindowRole.CHILD` has the header, status bar, stripes, dock, toolbar views,
  Zen and the notification corner layer exactly as one built with `WindowRole.MAIN`. `WindowContent.isMain()` and
  `role()` remain until Task 2.

- [ ] **Step 1: Write the failing tests** in `FullWindowChromeTest` (keep the file's `content(...)` helper and the
  empty-workspace tests that still hold; delete `aChildWindowShowsItsWorkspaceAndNoChrome`,
  `aChildWindowLeavesTheMainWindowsChromeAlone`, `aChildWindowBuildsNoToolbarViewsAndDocksNoPanels`):

```java
@Test void everyWindowHasTheHeaderStatusBarDockAndStripes() throws Exception {
    edt(() -> {
        for (WindowRole role : WindowRole.values()) {
            var window = content(idleLauncher(pending), role, HOME, false, () -> { });
            assertThat(SwingUtilities.isDescendingFrom(window.chrome().header(), window)).as(role + " header").isTrue();
            assertThat(SwingUtilities.isDescendingFrom(window.status(), window)).as(role + " status bar").isTrue();
            assertThat(SwingUtilities.isDescendingFrom(window.workspace(), window.area())).as(role + " dock centre").isTrue();
            assertThat(SwingUtilities.isDescendingFrom(window.leftStripe(), window)).as(role + " stripes").isTrue();
        }
    });
    launch();
}

@Test void aSecondWindowDocksPanelsAndBuildsToolbarViews() throws Exception {
    // As the deleted aChildWindowBuildsNoToolbarViewsAndDocksNoPanels set up, with the assertions inverted: the CHILD
    // window's contributed toolbar view is built once and a registered panel shows in its own dock on request.
}

@Test void zenRailAndStatusBarToggleOnlyTheirOwnWindow() throws Exception {
    edt(() -> {
        var first = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
        var second = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
        second.perform(ActionId.TOGGLE_ZEN_MODE);
        assertThat(second.zenMode()).isTrue();
        assertThat(first.zenMode()).isFalse();
        assertThat(second.actionEnabled(ActionId.TOGGLE_STATUS_BAR)).as("off in Zen").isFalse();
        first.perform(ActionId.TOGGLE_STATUS_BAR);
        assertThat(first.status().isVisible()).isFalse();
        assertThat(second.status().isVisible()).as("its own status bar").isFalse(); // hidden by its Zen
        second.perform(ActionId.TOGGLE_ZEN_MODE);
        assertThat(second.status().isVisible()).as("the first window's toggle did not reach it").isTrue();
    });
    launch();
}
```

  `perform` and `actionEnabled` stand for however the existing tests run an action on a window (`WorkspaceActions`
  through the content); use that helper, do not add API for the test.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*FullWindowChromeTest'`
Expected: FAIL (a CHILD window has no header, status bar or stripes; Zen is disabled in it).

- [ ] **Step 3: Remove the chrome gates** listed under Files. `WindowFrame` mounts every window as the main window is
  mounted today (title bar or header, menu row or screen menu bar). `JasperApplication.composeContent` passes `uiState`
  to every window; `WindowContributions` docks and persists for every window, so the last window to change or close the
  dock is what `ui-state.toml` holds (the class comment's "the last window closed wins" now applies to every window).
  The notification layer attaches to every window; `NotificationsFeature`'s routing (target, last active, first
  attached) is unchanged, so a card still shows in one window until plan 2.

- [ ] **Step 4: Run the workspace and application tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS once the tests that asserted a child window's missing chrome are inverted or removed (each listed under
Files; no other expectation changes).

- [ ] **Step 5: Commit** — `Full windows: every window has the header, dock, stripes, status bar and cards`

---

### Task 2: One kind of window

**Files:**
- Delete: `workspace/MainWindow.java`, `workspace/ChildWindow.java`, `workspace/WindowRole.java`
- Modify: `workspace/JasperWindow.java` (a `final` class; `closeRequested` closes after `content().mayClose()`)
- Modify: `workspace/WindowManager.java` (whole class: fields, `ensureMain`→`ensureWindow`, `openMain`, `openChild`,
  `routePanel` removed, `moveToMainWindow`→`moveTabToWindow`, `leaving`, `movedOut`, `emptyWindow`, `closeMain`→
  `closeEveryWindow`, `snapshot`, `windowRecord`, `closed`)
- Modify: `workspace/WindowContent.java` (constructor loses `role` and `onEmpty`; `role()`, `isMain()`,
  `mayMoveToMainWindow` go; `:1505` keeps an empty layout for every window; `windowMenuItems` per Task 3)
- Modify: `workspace/WorkspaceView.java:251` (the empty workspace shows in every window)
- Modify: `contributions/TabRegistry.java` (`Windows`, `main()`, `restoresMainFirst`, `mainOpened`, `mainFailed`, `moveToMainWindow`)
- Modify: `persistence/WorkspaceFile.java` (`Role` goes)
- Modify: `application/JasperApplication.java` (every `windows.main()`/`ensureMain` call site in spec section 4.1, the Buddy `:1935-1940`, `showHistory`, `showErrors`, `raiseFor`, `windowOpened`'s first-paint timing for the first window of the process)
- Modify: `benchmark/BenchmarkRun.java:203`, `:308`
- Modify: `gradle/application-architecture.gradle.kts:113`
- Modify: `workspace/package-info.java` and the class Javadoc of each file above
- Test: `workspace/WindowManagerTest.java`, `TestWindows.java`, `contributions/TabRegistryTest.java`,
  `persistence/WorkspaceFileTest.java`, `WorkspaceFileReadTest.java`, `WorkspaceRestoreTest.java`,
  `WorkspaceSavingTest.java`, `WorkspaceSnapshotTest.java`, `application/JasperApplicationWindowsTest.java`,
  `JasperApplicationWorkspaceTest.java`, `ErrorsTabTest.java`, and every other test the compiler names

**Interfaces:**
- Consumes: Task 1's full-chrome `WindowContent`.
- Produces (`WindowManager`, all EDT):
  - `public List<JasperWindow> windows()` — open windows in opening order.
  - `public Optional<JasperWindow> lastActive()` — the open window that came to the front last, else the one opened last.
  - `public Optional<JasperWindow> ensureWindow(String trigger, Path directory)` — `lastActive()` when a window is open
    (`directory` ignored, as `ensureMain` ignored it for an open main window); otherwise restores the saved windows
    (Step 3) and returns the first restored, or opens a new window with one terminal tab in `directory`. Empty while
    Jasper quits or every window closes; throws `IllegalStateException` when asked again while that restore is opening.
  - `public Optional<JasperWindow> openWindow(Path directory)` — New Window: a new window with one terminal tab in
    `directory` (plan 4 makes it the default layout).
  - `moveToNewWindow(Tab)` and both `moveTabToNewWindow` overloads return `Optional<JasperWindow>`.
  - `public boolean moveTabToWindow(WindowContent from, ParentTabId parentTab, WindowContent to)` — moves the parent tab
    whole to just after `to`'s active parent tab, selects it there and brings `to`'s window to the front; `from` closes
    when that left it empty. False when nothing moved (pinned, being closed, `from == to`).
  - `boolean closeEveryWindow()` — every window's tabs' guards, then `closeAll()`; false on a refusal. Replaces
    `closeMain` for Quit and going to the background.
  - `public Optional<WorkspaceFile.Snapshot> snapshot()` — every open window, in activation order (most recent first);
    empty while none is open or the restore is opening.
  - `Opening(boolean empty, Path directory, String trigger, Optional<Rectangle> bounds, Optional<Point> cursor)`.
- Produces (`TabRegistry`): `Windows(Supplier<UUID> window, Consumer<Tab> newWindow, Consumer<UUID> movedOut, Supplier<UUID> emptyWindow)`
  (test constructors lose `isMain`); `windowOpened(UUID)` and `windowFailed(Throwable)` replace `mainOpened` and
  `mainFailed`; `moveToWindow(Tab tab, UUID window)` replaces `moveToMainWindow(Tab)`.
- Produces (`WorkspaceFile`): `WindowRecord(Optional<UiState.Bounds> bounds, int extendedState, ParentTabs layout, Map<TabId, TabRecord> tabs)`;
  `Snapshot(List<WindowRecord> windows)` requiring at least one window, with `windows()`; `main()` and `children()` go.
  Any window's layout may be empty.
- Produces (`WindowContent`): `WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow, Runnable quit, ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look)`.

- [ ] **Step 1: Write the failing tests** in `WindowManagerTest` (rewrite the main/child cases listed below; `TestWindows.name`
  becomes the window's index in `windows()`, `"w0"`, `"w1"`, …):

```java
@Test void ensureWindowOpensOneWindowOnlyWhenNoneIsOpen() throws Exception {
    edt(() -> {
        JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
        assertThat(manager.ensureWindow("handoff", null)).containsSame(first);
        assertThat(manager.windows()).containsExactly(first);
        assertThat(events).containsExactly("show w0", "opened w0");
    });
    launch();
}

@Test void newWindowOpensAFullWindowInTheGivenDirectory(@TempDir Path folder) throws Exception {
    Path directory = Files.createDirectories(folder.resolve("work"));
    edt(() -> {
        JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
        JasperWindow second = manager.openWindow(directory).orElseThrow();
        assertThat(manager.windows()).containsExactly(first, second);
        assertThat(second.content().chrome().header().isDisplayable() || second.content().status() != null).isTrue();
        assertThat(second.content().tabCount()).isEqualTo(1);
    });
    launch();
    assertThat(started).containsExactly(HOME, directory);
}

@Test void closingOneOfSeveralWindowsLeavesTheOthersOpen() throws Exception {
    JasperWindow[] w = new JasperWindow[2];
    edt(() -> { w[0] = manager.ensureWindow("cold", null).orElseThrow(); w[1] = manager.openWindow(null).orElseThrow(); });
    launch();
    edt(() -> {
        events.clear();
        w[0].requestClose();
        assertThat(w[0].closed()).isTrue();
        assertThat(manager.windows()).containsExactly(w[1]);
        assertThat(events).containsExactly("closing w0", "dispose w0", "closed w0");
    });
}

@Test void closingTheLastWindowReachesTheClosedHookWithNoWindowLeft() throws Exception {
    // One window; requestClose; the closed hook runs with manager.windows() empty and manager.isEmpty() true.
}

@Test void everyWindowStaysWithAnEmptyWorkspace() throws Exception {
    // Two windows; close the second window's only tab: it stays open with workspace().emptyWorkspace() showing.
}

@Test void aWindowAMoveEmptiesClosesAfterTheMoveIsReported() throws Exception {
    // As today's aChildWindowAMoveEmptiesClosesAfterTheMoveIsReported, with the FIRST window as the one emptied by
    // moveTabToWindow(first, its only parent tab, second): TabMoved precedes WindowClosed(first), first.closed().
}

@Test void aPluginsTabWithNoOwnerOpensInTheMostRecentlyActiveWindow() throws Exception {
    // Two windows; activate the first (manager.activated(w0) through its events().activated().accept(true)); a
    // model.tabs() open with no owner lands in w0, not w1 (opened last).
}

@Test void twoRequestsWhileNoWindowIsOpenRestoreOnce() throws Exception {
    // A store holding two saved windows; in one EDT turn ensureWindow("handoff") and a plugin tab with no owner: two
    // windows open (the saved ones), each once, and the tab lands in the first restored.
}

@Test void quitAsksEveryWindowsGuardsAndARefusalKeepsThemAll() throws Exception {
    // Two windows, a guarding tab in the second that refuses: closeEveryWindow() is false, both windows stay; when it
    // allows, both close and "closing" is heard for each.
}

@Test void moveTabToWindowPutsItAfterTheActiveTabAndRaisesTheTarget() throws Exception {
    // w0 with tabs A,B (A active), w1 with tab C: moveTabToWindow(w1, C's parent tab, w0) → w0's tabs A,C,B, C selected,
    // events end "front w0", and w1 closed (emptied by the move).
}

@Test void savedWindowsComeBackInActivationOrderTheMostRecentInFront() throws Exception {
    // Three windows, w1 activated last; snapshot().windows() lists w1 first; restoring that snapshot into a fresh
    // TestWindows opens all three and brings the first restored to the front.
}
```

  Delete `theMainWindowsConstructorIsPackagePrivate`, `aChildWindowsPanelRequestGoesToTheMainWindow`,
  `aPanelToggleFromAChildWindowShowsThePanel`, `anActivatingPanelToggleFromAChildWindowShowsAndActivatesThePanel`,
  `aChildWindowsRequestForADetachedPanelRaisesItsWindowNotTheMainWindow`,
  `aChildWindowsPlainToggleOfAnOpenDetachedPanelRaisesItsWindow` (each window handles its own panel requests) and
  `aChildWindowsTabMenuLeavesMoveToMainWindowToItsWindowMenu`; port the rest by renaming main/child to first/second.
  In `WorkspaceFileTest` add `aWindowsLayoutMayBeEmptyAndRoleIsNotWritten` (encode two windows, one empty; the text has no
  `role`; it reads back equal) and `aFileWithARoleKeyStillReads`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile (`ensureWindow`, `openWindow`, `moveTabToWindow`, `closeEveryWindow` not defined).

- [ ] **Step 3: Rewrite `WindowManager` to one list of windows.** Keep the existing machinery (`open`, `settle`,
  `fitted`, placeholders, the closing-all flag, `tabDrag`, `fronts`) and its failure handling; change only what the
  Interfaces block says. The restore (`openMain` → `restore`) opens every saved window in file order: a failure in the
  first opens it as a new window and keeps the file aside, as `openMain` does for the main window; a later failure costs
  only that window, as `restoreChild` does; at the end the first restored comes to the front. The one-restore-at-a-time
  rule is `openingMain` renamed `restoring`. `emptyWindow()` opens an empty window without `ensureWindow` first.
  `movedOut` closes any window left with no tab. `JasperWindow`'s activation and closing paths are unchanged.

- [ ] **Step 4: Move every caller** named under Files to the new names: owner-less and handoff routes to
  `ensureWindow`; `raiseFor`'s fallback, `showHistory`, `showErrors` and `restoreForSubcommand` to `ensureWindow`; the
  Buddy follows `lastActive()` and re-evaluates on every activation; the first-paint timing in `windowOpened` runs for
  the first window a process opens. `verifyApplicationArchitecture`'s rule becomes "`JasperWindow` is made only in
  `WindowManager.open`" (`:113`).

- [ ] **Step 5: Run the application module**

Run: `./gradlew :jasper-app:test :jasper-app:verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 6: Commit** — `Full windows: one kind of window; requests go to the most recently active window`

---

### Task 3: New Window and Move Tab to Window

**Files:**
- Modify: `commands/ActionId.java:14` (`NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n")`), `:17` (`MOVE_TO_MAIN_WINDOW` goes; add `MOVE_TAB_TO_WINDOW("move_tab_to_window", "Move Tab to Window", null, null)`)
- Modify: `workspace/WorkspaceActions.java:61-64`, `WindowChrome.java:84`, `:125`, `:637`, `WindowContent.java` (`requestNewTerminalWindow`→`requestNewWindow`, `windowMenuItems`, the tab context menu `:1945`), `palette/CommandsScope.java`
- Modify: `platform/ResidentTray.java:60-71`, `:121-124` and its Javadoc (`NEW_WINDOW("New Window")`), `JasperApplication.java:1005-1009` (`newWindowFromMenuBar` → `windows.openWindow(null)`)
- Modify: `docs/keyboard-shortcuts.md`, the shortcut-help catalogue if it lists `new_terminal_window`
- Test: `config/KeymapDefaultsTest.java`, `KeyBindingsTest.java`, `workspace/WindowMenuTest.java`, `WindowMenuButtonTest.java`, `TabMenuTest.java`, `shortcuthelp/ShortcutHelpTest.java`, `plugins/TabShortcutCollisionTest.java`, `ResidentTray`'s test

**Interfaces:**
- Consumes: Task 2's `openWindow(Path)`, `moveTabToWindow(WindowContent, ParentTabId, WindowContent)`, `windows()`.
- Produces: the ⋮ menu and the tab context menu hold **Move Tab to New Window** (from two tabs) and **Move Tab to
  Window ▸** (while another window is open), whose items are the other windows in `windows()` order, each titled with
  its active tab's title; choosing one calls `moveTabToWindow(this, activeParentTab(), that)`.

- [ ] **Step 1: Write the failing tests**

```java
// KeymapDefaultsTest
@Test void newWindowIsCmdNOnMacAndCtrlShiftNElsewhere() {
    assertThat(ActionId.NEW_WINDOW.id()).isEqualTo("new_window");
    assertThat(ActionId.NEW_WINDOW.label()).isEqualTo("New Window");
    assertThat(KeyBindings.defaults(true).keysFor(ActionId.NEW_WINDOW)).containsExactly("cmd+n");
    assertThat(KeyBindings.defaults(false).keysFor(ActionId.NEW_WINDOW)).containsExactly("ctrl+shift+n");
}

// WindowMenuTest
@Test void moveTabToWindowListsTheOtherWindowsByTheirActiveTabsTitle() throws Exception {
    // Three windows titled "alpha", "beta", "gamma" by their active tabs; in "beta"'s ⋮ menu, Move Tab to Window ▸ has
    // exactly "alpha", "gamma"; choosing "gamma" moves beta's active tab into gamma. With one window the submenu is absent.
}

// ResidentTray test
@Test void theMacMenuBarItemOffersNewWindow() {
    assertThat(ResidentTray.entries(true)).extracting(ResidentTray.Entry::label)
        .containsExactly("New Window", "Settings…", null, "Quit Jasper Completely");
}
```

  Use the accessor names the existing tests use (`keysFor`, `entries`); keep the test's shape if the helper differs.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*KeymapDefaultsTest' --tests '*WindowMenuTest' --tests '*ResidentTray*'`
Expected: FAIL (`NEW_WINDOW` undefined).

- [ ] **Step 3: Rename and add the actions** as listed under Files; the File menu, toolbar button (`app-window` icon,
  tooltip "New window") and palette use `NEW_WINDOW`. `[keybindings]` keys `new_terminal_window` and
  `move_to_main_window` are not read any more (MVP).

- [ ] **Step 4: Run the application module**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 5: Update `docs/keyboard-shortcuts.md`** (New Window row; Move Tab to Window, no default key) and commit —
  `Full windows: New Window (cmd+n) and Move Tab to Window`

---

### Task 4: SDK 0.23.0

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:9` (`"0.23.0"`)
- Modify: `jasper-sdk/.../terminal/WindowHandle.java:46-54` (`isMain()` goes)
- Modify: `jasper-sdk/.../ui/ContentTab.java:74` (`moveToMainWindow()` goes; add `moveToWindow(WindowHandle window)`)
- Modify: `jasper-sdk/.../ui/Panels.java` (add `show(String panelId, WindowHandle window)`; reword the class and `toggle`)
- Reword the Javadoc at every line listed in spec section 4.6 and the hand-off of `jasper-sdk/.../ui/PanelHost.java:7`, `:10`, `:73`, `PanelFactory.java:4-10`, `Windows.java:30-58`, `TabPlacement.java:12-22`, `Toolbar.java:5`, `ToolbarItem.java:36`, `:74`, `ToolbarViewFactory.java:6`, `StatusBar.java:17`, `StatusItem.java:6`, `PanelMode.java:4-14`, `OverlaySpec.java:9`, `StatusProgress.java:6`, `TabRestorer.java:5`, `:38`, `terminal/SessionState.java:6`, `terminal/TerminalEvents.java:156-158`
- Modify: `jasper-app/.../plugins/HostedTerminals.java:131`, `HostedUi.java` (`Panels.show`, `ContentTab.moveToWindow`), `terminals/TerminalRegistry` (the window's `main` supplier goes)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java:55`, `FakeUi.java:494-497`, `:615-617`, `FakeContent.java:60`, `FakePluginHost.java:1488-1499`, the testkit README
- Modify: every `plugins/*/src/main/resources/plugin.toml` (`sdk = ">=0.23.0, <0.24"`, `version` one minor up), `docs/examples/external-plugin/build.gradle.kts:7` and its `plugin.toml`
- Regenerate: `docs/sdk-reference.html` (`./gradlew sdkReference`)
- Test: `jasper-sdk-testkit/.../FakePanelsAndWindowsTest.java`, `contract/PluginContractTest.java`, `jasper-app/.../plugins/AppContractTest.java`, `HostedAnchorContractTest.java`, `WorkspaceContentRouteTest.java`

**Interfaces:**
- Consumes: Task 2's `TabRegistry.moveToWindow(Tab, UUID)` and `lastActive()`.
- Produces (SDK, each `@since 0.23.0` where new):
  - `void Panels.show(String panelId, WindowHandle window)` — shows the panel in `window`'s dock (or its own window
    when detached there), building that window's instance on first show; a closed window has no effect; same
    exceptions as `toggle`.
  - `void ContentTab.moveToWindow(WindowHandle window)` — moves the tab to the end of `window`'s strip and shows it;
    nothing when it is already there, pinned or being closed, or `window` is closed.
  - Wording: "every window has a dock, status bar and toolbar"; `PanelFactory.create` "once for each window whose dock
    shows the panel"; `PanelHost.window()` "the window whose dock holds this instance"; `onClosed` "when that window
    closes or the panel is removed"; a null owner in `Windows.openTab` "the most recently active window, opening one
    when none is open"; `TabPlacement.NEW_WINDOW` "in a new window of its own".
- Produces (testkit): `FakeUi` keeps one panel instance per window id; `toggle` and `show` act on the named window's
  instance; `FakeContent.moveToWindow(WindowHandle)`.

- [ ] **Step 1: Write the failing tests**

```java
// FakePanelsAndWindowsTest
@Test void eachWindowGetsItsOwnPanelInstance() {
    var host = FakePluginHost.start(plugin);            // as the existing tests start one
    var a = host.terminals().activeWindow().orElseThrow();
    var b = host.workspace().addWindow();               // the fake's existing second-window helper
    host.ui().panels().show(PANEL, a);
    host.ui().panels().show(PANEL, host.terminals().windowHandle(b.id));
    assertThat(built).hasSize(2);                       // the factory ran once per window
    assertThat(built.get(0).window()).isEqualTo(a);
    built.get(0).fireClosed();                          // a's window closes
    assertThat(built.get(1).visible()).isTrue();
}

@Test void moveToWindowPutsTheTabAtTheEndOfThatWindowsStrip() { /* open a ContentTab in a, moveToWindow(b): it is b's last tab, shown */ }

// PluginContractTest (runs against the fake and, through AppContractTest, the app)
@Test void panelsShowInTheWindowTheyAreAskedFrom() { /* show(PANEL, b) builds b's instance; a has none */ }
@Test void windowHandlesHaveNoMainWindow() {
    assertThat(java.util.Arrays.stream(WindowHandle.class.getMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("isMain");
}
```

  `built`, `fireClosed` and the fake's helpers are the test's own recording factory and the fake's existing methods;
  match what `FakePanelsAndWindowsTest` already uses.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: FAIL to compile (`Panels.show`, `ContentTab.moveToWindow` undefined).

- [ ] **Step 3: Change the SDK, the testkit and the app's hosting** as the Interfaces block says. `HostedUi`'s
  `Panels.show` sends `PanelRequest(window.id(), panelId, SHOW)`; `moveToWindow` calls `TabRegistry.moveToWindow`.

- [ ] **Step 4: Regenerate the reference and run the SDK checks**

Run: `./gradlew sdkReference :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, `docs/sdk-reference.html` changed.

- [ ] **Step 5: Commit** — `SDK 0.23.0: every window has a dock; Panels.show and ContentTab.moveToWindow`

---

### Task 5: Plugins' panels in every window

Spec risk 1: each plugin below keeps one panel instance in a field and uses `window.isMain()` to choose between showing
it and toggling. Each keeps its instances by window id instead and asks for the panel with `Panels.show(PANEL, window)`.

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java:129`, `:220`, `:277-279`, `:394-399`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java:133`, `:281` and its instance field
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanels.java:23`, `:40`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java:74-75`, `:120` (`explorer`)
- Check, change only if it keeps one instance: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:273` (Sessions), `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java:117`, `jasper-app/.../application/NotificationsFeature.java:72`
- Test: `plugins/fileserver/src/test/.../FileServerPluginTest.java`, `plugins/remote/src/test/.../RemotePluginTest.java`, `SftpPluginTest.java`, `ui/tunnels/TunnelsScopeTest.java`, the Transfers panel's test

**Interfaces:**
- Consumes: Task 4's `Panels.show(String, WindowHandle)` and the testkit's per-window instances.
- Produces: in each plugin, a `Map<UUID, Instance>` (the plugin's own instance type: host plus view) filled in its
  factory and emptied in that host's `onClosed`; everything that refreshes every instance (File Server's
  `registry.refreshAddresses()` at `:220`, a selection after `showPanel`) iterates the map. The model stays one per
  plugin; only views multiply.

- [ ] **Step 1: Write the failing tests**, one pair per plugin, against the testkit's two windows:

```java
@Test void showsInTheWindowItIsAskedFrom() {
    // Invoke the plugin's own entry point from window b (File Server: a temporary server's start; Tunnels: its toggle
    // action; Transfers: a transfer starting; SFTP: Open SFTP from a remote pane in b): b's instance is built and
    // visible, a has none built.
}

@Test void closingOneWindowsPanelKeepsTheOthers() {
    // Build the panel in a and b; close a's instance (the fake's onClosed): b's instance still refreshes and selects
    // (File Server: select(id) reaches b's view; SFTP: b's explorer still lists).
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-fileserver:test :jasper-plugin-remote:test`
Expected: FAIL (the second window's request reuses or toggles the first window's instance).

- [ ] **Step 3: Key each plugin's instances by window** as the Interfaces block says; delete every `isMain()` branch.

- [ ] **Step 4: Run the plugins' checks**

Run: `./gradlew :jasper-plugin-fileserver:check :jasper-plugin-remote:check :jasper-plugin-sample:check :jasper-app:verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit** — `Plugins: File Server, Tunnels, Transfers and SFTP keep a panel per window`

---

### Task 6: Documentation and the whole check

**Files:**
- Modify: `docs/workspace.md` (`:10-51` windows and moves, `:374-380` Zen per window, `:440-446` cards, `:448-487` quitting), `docs/configuration.md` (`:268-276`, `:286-301`, `:620-638` "the last window to change or close the dock", `:640-662` no roles, every window restored), `docs/plugin-ui.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/plugin-authoring.md` if it names the main window, `jasper-app/README.md`, `docs/STATUS.md` (a plan 1 entry: what landed, what plans 2-5 still change: cards in one window, every window restored, a shared dock state)
- Modify: this plan's status banner (deviations, if any)

- [ ] **Step 1: Find every stale mention**

Run: `grep -rniE 'main window|child window|new terminal window|move to main window' docs/*.md jasper-*/README.md plugins/*/README.md`
Expected: each hit is updated or describes a dated record (`docs/superpowers/**`, `docs/STATUS.md` entries).

- [ ] **Step 2: Update the guides** with the smallest edits that make them accurate (AGENTS.md "Documentation edits").

- [ ] **Step 3: Run everything**

Run: `./gradlew check && ./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 4: Commit** — `Docs: every window is a full window (full windows plan 1)`

- [ ] **Step 5: Hand the GUI checks to the user** (not run by an agent): cmd+n opens a full window; tool windows,
  Zen, rail and status bar per window; a tab torn out is a full window; Move Tab to Window; closing one window leaves
  the others; the menu bar item's New Window; File Server, Tunnels, Transfers and SFTP shown from a second window.
