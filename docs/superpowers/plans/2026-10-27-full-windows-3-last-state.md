# Full windows 3: the last state and relaunch Implementation Plan

> **Status (2026-10-27):** Executed, Tasks 1-4, on `claude/full-windows` (not merged or pushed). Additions to the plan
> text: `WindowManager.Opening` gained `ToolWindows tools` (the window's own tool windows: the saved window's for a
> restore, `ToolWindows.BUILT_IN` for a first window with nothing saved, else a copy of the active window's).
> `WindowContent.replaceToolWindows` and `WindowContributions.discard` serve the failed-restore fallback, which swaps a
> restored window's tool windows for the built-in ones. Controller Rulings: 1, the status bar's visibility stays the
> `window.status_bar` setting (not saved per window); 2, a failed restore opens the `BUILT_IN` tool windows; 3, a
> window's activation schedules the debounced save. Task 4 also made `UiState` read an older `version` quietly (INFO, no
> stack trace) and updated the guides.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each window keeps its own tool window state; `workspace.toml` saves one window, the last to close; relaunch
(and reopening while resident) opens exactly one window, from that last state or the built-in layout, as the new
`system.reopen` setting says.

**Architecture:** The window-shaped half of `UiState` (rail, Zen, each look's dock and panels) becomes a per-window
`ToolWindowState` built from an immutable `ToolWindows` value, which `workspace.toml` (version 2) stores with the one
window it keeps. `WindowManager.snapshot()` saves the most recently active window; a user's Close Window saves that
window at once; restore opens one window. Plan 4 adds the user's default layout; until then "Empty State" and new
windows use the built-in layout and the active window's tool windows respectively.

**Tech Stack:** Java 25 (JBR 25), Swing, tomlj 1.1.1, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md`](../specs/2026-10-26-jasper-full-windows-design.md)
sections 6 and 11 (decisions 6 and 7). Plans 1 and 2 are done on this branch.

## Global Constraints

- Worktree `.claude/worktrees/full-windows`, branch `claude/full-windows`; never build in `~/projects/moray`; never
  launch the GUI or a benchmark; `./gradlew` only. One commit per task ending with
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- MVP, no migration: `workspace.toml` goes to `version = 2`; a version-1 file is read as no saved state (no `.bad`, no
  notice; the next save replaces it). `ui-state.toml` goes to `version = 3`; an older one is read as defaults.
- A window's **state**: its bounds and maximised state, its tabs and panes (as today), and its tool windows: Zen, rail
  visibility, each look's dock sizes and ratios, each tool window's slot, order, visibility, mode and own-window bounds.
  A look the state has none for starts from that look's built-in dock.
- `workspace.toml` keeps **one** window's state. Written: within a second of each change, with the most recently
  active window's state; at once when the user closes a window (Close Window, the title bar), with that window's state;
  on Quit and going to the background, with the most recently active window's state. A window closed because a move
  emptied it writes nothing at once.
- `system.reopen` (System Settings › General, beside Run in background): `last_known_state` (default, label **Last
  Known State**) opens the last state; `empty_state` (label **Empty State**) opens the built-in layout (plan 4: the
  default layout) at the last state's bounds when there is one. It applies whenever Jasper opens a window while none
  is open: at start, and while resident (Dock click, a handoff, a notification click).
- The built-in layout: one terminal tab starting where a new shell starts (the requested directory when there is one),
  and the look's built-in dock (`ToolWindows.BUILT_IN`).
- Restore failure: keep the file as `workspace.toml.bad`, open the built-in layout, post "Jasper could not restore your
  window".
- *Ruling (this plan):* until plan 4, a window opened while another is open (New Window, tear-out, `NEW_WINDOW`) takes
  a copy of the most recently active window's tool windows with Zen off and Float/Window tool windows hidden
  (`ToolWindowState.forNewWindow()`); plan 1's shared-state machinery for that (`anotherOpen`, held panels,
  `keepsSavedZen`) goes, since nothing is shared any more.
- *Ruling (this plan):* the `--background` rehearsal uses `ToolWindows.BUILT_IN` (it never reads `workspace.toml`).

## Review Focus

1. **Close one window, keep working in another, then Quit:** relaunch opens the window that was active at Quit. →
   Task 2 `quitAfterClosingOneWindowRestoresTheOneActiveAtQuit`.
2. **A window emptied by dragging its last tab into another window does not become the last state**, even when it was
   the most recently active. → Task 2 `aWindowAMoveEmptiesIsNotSavedAsTheLastState`.
3. **An old `workspace.toml` (version 1) or `ui-state.toml` (version 2)** opens the built-in layout with no "could not
   restore" notice and no `.bad` file. → Task 1 `aVersionOneFileIsReadAsNoSavedStateAndLeftInPlace`, `UiStateTest`
   `aVersionTwoFileIsReadAsDefaults`.
4. **A launch naming a directory** (`jasper ~/src`) opens it in a new tab after the restored window's tabs; with Empty
   State the built-in window's terminal starts there. → Task 3 `emptyStateStartsTheRequestedDirectory`.
5. **The last state saved under one look, restored under another** (Enterprise turned on) gives that look's built-in
   dock and keeps the other look's dock for later. → Task 1 `aLookTheStateHasNoneForStartsFromItsBuiltInDock`.
6. **Reopening while resident** (no window, Dock click) follows `system.reopen` exactly as a start does. → Task 3
   `reopeningWhileResidentFollowsTheSetting`.

---

### Task 1: One window's state is saved and restored

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/persistence/ToolWindows.java`, `jasper-app/src/main/java/dev/jasper/app/persistence/ToolWindowState.java`
- Modify: `persistence/UiState.java` (drop `railVisible`, `zenMode`, `dock`, `panel(s)`, `putPanel`, `setDock`, the look keying and the v1/v2 readers; `version = 3`; `Panel`, `Dock`, `Bounds` records stay here)
- Modify: `persistence/WorkspaceFile.java` (`version = 2`; one `[window]`; `WindowRecord` gains `tools`; `Snapshot(WindowRecord window)`; version 1 → `Fresh(Optional.empty())` without moving the file)
- Modify: `workspace/WindowContent.java` (`:1112`, `:1196-1233`, `:2007`), `workspace/WindowContributions.java` (ctor `:87-95`, `:359-412`, `persist` `:530-553`, `close` `:662-668`), `workspace/WindowManager.java` (`snapshot` `:801`, `holding`, `restore` `:296-344`, `restoreLater`, the new-window paths), `application/JasperApplication.java` (`composeContent` `:573-594`, `rehearsalWindow` `:1237-1240`, `workspaceSetAside`'s notice text)
- Modify: `jasper-app/src/main/resources/dev/jasper/app/benchmark/startup-workspace.toml` (regenerated at version 2)
- Test: new `persistence/ToolWindowsTest.java`; `persistence/WorkspaceFileTest`, `WorkspaceFileReadTest`, `UiStateTest`, `UiStateLookTest`; `workspace/WorkspaceRestoreTest`, `WorkspaceSnapshotTest`, `WorkspaceSavingTest`, `WindowManagerTest`, `ZenModeTest`, `WindowPanelsTest`, `DetachedToolWindowsTest` and the other `UiState` users the compiler names; `application/JasperApplicationWorkspaceTest`, `EnterpriseFirstWindowTest`; `benchmark/StartupBenchTest`

**Interfaces:**
- Produces (`persistence`):
  - `record ToolWindows(boolean railVisible, boolean zenMode, Map<String, ToolWindows.Look> looks)` with
    `record Look(Map<String, UiState.Panel> panels, UiState.Dock dock)` (keyed by look id, `""` the default look) and
    `static final ToolWindows BUILT_IN = new ToolWindows(true, false, Map.of())`.
  - `final class ToolWindowState` (EDT): `ToolWindowState(ToolWindows saved, String look)`; `look()`;
    `Optional<UiState.Panel> panel(String id)`; `Map<String, UiState.Panel> panels()`; `putPanel(String, UiState.Panel)`;
    `UiState.Dock dock()`; `setDock(UiState.Dock)`; `railVisible()`, `setRailVisible(boolean)`; `zenMode()`,
    `setZenMode(boolean)`; `void changed()` (runs the listener; replaces `UiState.save()` for these callers);
    `void onChanged(Runnable listener)`; `ToolWindows snapshot()` (every look's layout, not only the current one);
    `ToolWindowState forNewWindow()` (a copy with Zen off and every panel saved visible in Float or Window mode saved
    hidden, mode and bounds kept).
  - `WorkspaceFile.WindowRecord(Optional<UiState.Bounds> bounds, int extendedState, ParentTabs layout, Map<TabId, TabRecord> tabs, ToolWindows tools)`;
    `WorkspaceFile.Snapshot(WindowRecord window)`.
  - TOML: `version = 2`; `[window]` `x`, `y`, `width`, `height`, `extended_state`, `shown_tab`; `[[window.tabs]]`,
    `[[window.tabs.nodes]]`, `[[window.items]]` as the `[[windows…]]` tables are today; `[window.tool_windows]`
    `rail_visible`, `zen_mode`; `[window.tool_windows.dock]`; `[window.tool_windows.panels."<id>"]`;
    `[window.tool_windows.look."<id>".dock]` and `.panels."<id>"`, with the keys `ui-state.toml` uses for them today.
    A bad tool-window entry falls back alone, as `UiState` does today.
- Produces (`workspace`): `WindowContent.connectContributions(Contributions model, UiState app, ToolWindowState tools, boolean hostsContent)`
  (`app` keeps the palette and run-window positions); `WindowContent.toolWindows()` returns its `ToolWindowState`;
  `WindowManager.snapshot()` returns the most recently active open window's record; a restore opens one window.

- [ ] **Step 1: Write the failing tests**

```java
// ToolWindowsTest
@Test void forNewWindowTurnsZenOffAndHidesDetachedToolWindows() {
    var floating = new UiState.Panel(Slot.RIGHT_TOP, 0, true, Optional.of(ToolWindowMode.FLOAT), Optional.of(new UiState.Bounds(10, 10, 300, 200)));
    var docked = new UiState.Panel(Slot.LEFT_TOP, 0, true, Optional.empty(), Optional.empty());
    var state = new ToolWindowState(new ToolWindows(true, true, Map.of("", new ToolWindows.Look(Map.of("a.f", floating, "a.d", docked), UiState.Dock.DEFAULT))), "");
    var copy = state.forNewWindow();
    assertThat(copy.zenMode()).isFalse();
    assertThat(copy.panel("a.f").orElseThrow().visible()).isFalse();
    assertThat(copy.panel("a.f").orElseThrow().window()).contains(new UiState.Bounds(10, 10, 300, 200));
    assertThat(copy.panel("a.d").orElseThrow().visible()).isTrue();
    assertThat(state.zenMode()).as("the original is untouched").isTrue();
}

@Test void aLookTheStateHasNoneForStartsFromItsBuiltInDock() {
    // ToolWindows with only the default look's dock; new ToolWindowState(that, "dev.jasper.enterprise"): dock() is
    // UiState.Dock.DEFAULT and panels() empty; after setDock(x), snapshot().looks() has both "" and the Enterprise look.
}

// WorkspaceFileTest / WorkspaceFileReadTest
@Test void oneWindowWithItsToolWindowsRoundTrips() { /* write(Snapshot(one record with non-default ToolWindows)) then read() == Restore(same) */ }
@Test void aVersionOneFileIsReadAsNoSavedStateAndLeftInPlace() {
    // A file holding the old `version = 1` / `[[windows]]` text: read() is Fresh(Optional.empty()); the file still
    // exists unchanged and no workspace.toml.bad was written.
}
@Test void aBadToolWindowEntryFallsBackAlone() { /* a panel with an unknown slot is dropped; the rest and the tabs come back */ }

// UiStateTest
@Test void aVersionTwoFileIsReadAsDefaults() { /* old text with [dock] and window bounds: everything is default */ }
@Test void windowShapedKeysAreNeitherReadNorWritten() { /* save() text has no rail_visible, zen_mode, [dock], [panels], [look */ }

// WindowManagerTest / WorkspaceSnapshotTest
@Test void theSnapshotIsTheMostRecentlyActiveWindowWithItsToolWindows() {
    // Two windows; the second's ToolWindowState has zen on; activate it: snapshot().window().tools().zenMode() true and
    // its tabs are the second window's.
}
@Test void aRestoreOpensOneWindowWithItsToolWindows() {
    // A store holding one window whose tools hide the rail: ensureWindow opens exactly one window; its content's
    // toolWindows().railVisible() is false.
}
@Test void aNewWindowCopiesTheActiveWindowsToolWindowsForANewWindow() {
    // Active window in Zen with a FLOAT panel visible; openWindow(null): the new window's zenMode() false and that panel
    // not visible; the active window unchanged.
}
```

  Rewrite or delete every test that expects several saved windows (`WorkspaceRestoreTest`'s multi-window cases,
  `JasperApplicationWorkspaceTest.quitSavesEveryWindowAndTheNextStartRestoresThem` → one window,
  `goingToTheBackgroundSavesAndReopeningRestoresTheSameWindows` → the same window). `StartupBenchTest`'s seed test
  keeps its shape against the version-2 seed (regenerate the resource with `WorkspaceFile.write` for one window, one
  terminal tab, `ToolWindows.BUILT_IN`, bounds 100,100,960,600).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile (`ToolWindows`, `ToolWindowState` and the new record shapes are missing).

- [ ] **Step 3: Implement.** Move the window-shaped code from `UiState` into `ToolWindowState` (same look keying,
  same validation, same bad-entry fallback). Each window gets its own `ToolWindowState`: the restored window's from its
  record's `tools`; a fresh first window's from `BUILT_IN`; any window opened while another is open from
  `lastActive()`'s `toolWindows().forNewWindow()`. Its listener is the manager's `changed()`. Remove
  `WindowContributions`' `anotherOpen`/held/`keepsSavedZen` code and `composeContent`'s `!windows.isEmpty()` argument;
  `persist` writes into the window's own state and calls `changed()`. `restore` opens one window from
  `saved.window()` and keeps today's first-window failure handling (`keepAside`, then a new window); `restoreLater` and
  the multi-window loop go. `rehearsalWindow` uses `new ToolWindowState(ToolWindows.BUILT_IN, look)`.

- [ ] **Step 4: Run the application module**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 5: Commit** — `Workspace: each window keeps its own tool windows; workspace.toml saves one window`

---

### Task 2: Last close wins

**Files:**
- Modify: `workspace/WorkspaceStore.java` (add `save(Snapshot)`), `workspace/WindowManager.java` (`closed` `:1047-1062`, the user-close path through `JasperWindow.requestClose`, `closeAll` `:687-711`), `workspace/JasperWindow.java` (`requestClose`)
- Test: `workspace/WorkspaceStoreTest`, `WorkspaceSavingTest`, `WindowManagerTest`, `application/JasperApplicationWorkspaceTest`

**Interfaces:**
- Consumes: Task 1's one-window `Snapshot` and `snapshot()`.
- Produces: `void WorkspaceStore.save(WorkspaceFile.Snapshot snapshot)` — writes exactly this snapshot now (ordered
  after earlier writes, skipped when held, remembered as this process's last save so a `load()` returns it). The
  window manager calls it with the closing window's record when the user closes a window (after its guards agree, while
  its content is whole); `closed()` no longer calls `store.save()`; a close caused by a move writes nothing at once;
  `closeAll` still holds the store after saving the most recently active window.

- [ ] **Step 1: Write the failing tests**

```java
// WorkspaceSavingTest / WindowManagerTest
@Test void closingAWindowSavesThatWindowAsTheLastState() {
    // Windows A (active) and B; B.requestClose(): the store's last write is B's record, though A is active.
}
@Test void aWindowAMoveEmptiesIsNotSavedAsTheLastState() {
    // B active with one tab; moveTabToWindow(B, its tab, A): B closes; no write happens at once, and the next
    // (debounced) write is A's record.
}
@Test void aGuardThatRefusesTheCloseSavesNothing() { /* B's tab refuses: no write, B stays */ }

// JasperApplicationWorkspaceTest
@Test void quitAfterClosingOneWindowRestoresTheOneActiveAtQuit() {
    // Open A and B; close B; work in A (a change); Quit; a fresh application on the same home restores A only.
}
@Test void closingTheLastWindowWhileResidentRestoresItOnReopen() {
    // Resident; one window with two tabs; Close Window; reopen (Dock click path): one window with those two tabs.
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*WorkspaceSavingTest' --tests '*WindowManagerTest' --tests '*JasperApplicationWorkspaceTest' --tests '*WorkspaceStoreTest'`
Expected: FAIL (a close saves the remaining windows, not the closed one).

- [ ] **Step 3: Implement** as the Interfaces block says.

- [ ] **Step 4: Run the application module** — `./gradlew :jasper-app:test`, expected PASS.

- [ ] **Step 5: Commit** — `Workspace: the last window to close is the one saved`

---

### Task 3: The `system.reopen` setting

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/ReopenMode.java` (`enum ReopenMode { LAST_KNOWN_STATE, EMPTY_STATE }`)
- Modify: `config/CoreSettings.java` (a `SYSTEM_REOPEN` choice setting after `SYSTEM_RUN_IN_BACKGROUND` in `SCHEMA`; the `system` section header if it no longer fits), `config/ConfigSnapshot.java` (a `reopen` component, `from`, the builder, `defaults`), `settingsui/CorePages.java:183-189` (`.row(SYSTEM_REOPEN, Editors.choice(...))` with labels **Last Known State** and **Empty State**), `workspace/WindowManager.java` (`reopen(Supplier<ReopenMode>)`; `restore` reads it), `application/JasperApplication.java` (wires `configuration.snapshot().reopen()`), `config.example.toml` (the generated template, if `ConfigTemplateTest` writes it)
- Modify: `docs/configuration.md` (settings table row beside `system.run_in_background` `:182`; Background residency `:350-368` one sentence)
- Test: `config/CoreSettingsTest`, `ConfigTemplateTest`, `settingsui/CorePagesTest`, `documentation/AppDocumentationTest`, `workspace/WindowManagerTest`, `application/JasperApplicationWorkspaceTest`

**Interfaces:**
- Consumes: Task 1's restore and `ToolWindows.BUILT_IN`.
- Produces: `Setting<ReopenMode> CoreSettings.SYSTEM_REOPEN` = key `system.reopen`, choices `last_known_state` →
  `LAST_KNOWN_STATE` (default), `empty_state` → `EMPTY_STATE`, label "Reopen with", description "What the window Jasper
  opens while none is open holds: the last window you closed, or the built-in layout.", `Applies.LIVE`;
  `ReopenMode ConfigSnapshot.reopen()`; `void WindowManager.reopen(Supplier<ReopenMode> mode)` (default
  `LAST_KNOWN_STATE` when never given). With `EMPTY_STATE` and a saved state, the restore opens the built-in layout at
  the saved window's bounds and extended state; the saved file is left as it is (the next save replaces it).

- [ ] **Step 1: Write the failing tests**

```java
// CoreSettingsTest
@Test void reopenDefaultsToTheLastKnownState() {
    assertThat(CoreSettings.SYSTEM_REOPEN.key()).isEqualTo("system.reopen");
    assertThat(CoreSettings.SYSTEM_REOPEN.defaultValue()).isEqualTo(ReopenMode.LAST_KNOWN_STATE);
}

// WindowManagerTest
@Test void emptyStateOpensTheBuiltInLayoutAtTheSavedBounds() {
    // A store holding a window with three tabs at Bounds(50, 60, 1000, 700); reopen(() -> EMPTY_STATE); ensureWindow:
    // one window, one terminal tab, placement bounds (50, 60, 1000, 700), toolWindows().snapshot() == BUILT_IN.
}
@Test void emptyStateStartsTheRequestedDirectory(@TempDir Path folder) {
    // EMPTY_STATE; ensureWindow("handoff", folder): its one terminal started in folder.
}

// JasperApplicationWorkspaceTest
@Test void reopeningWhileResidentFollowsTheSetting() {
    // Resident; close the last window; set system.reopen = empty_state in the configuration; reopen: the built-in layout.
}
```

- [ ] **Step 2: Run them to verify they fail** —
  `./gradlew :jasper-app:test --tests '*CoreSettingsTest' --tests '*WindowManagerTest' --tests '*JasperApplicationWorkspaceTest'`,
  expected FAIL (`SYSTEM_REOPEN` undefined).

- [ ] **Step 3: Implement** as the Interfaces block says; every `new ConfigSnapshot(...)` call site gains the value.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test`, expected PASS.

- [ ] **Step 5: Commit** — `Settings: system.reopen chooses the last known state or the built-in layout`

---

### Task 4: Documentation and the whole check

**Files:**
- Modify: `docs/configuration.md` (`### ui-state.toml` `:620-638`: version 3, the window-shaped rows go; `### workspace.toml` `:640-661`: one window, version 2, the tool windows, last close wins, the `.bad` notice wording, version 1 read as nothing saved; the "Files in Jasper's home" rows `:44-45`), `docs/workspace.md` (`### Saving and restoring the windows` `:60-74`: one window; the New Window and Quit wording that names "as Jasper last saved them" or "every saved window"), `docs/STATUS.md` (a 2026-10-27 plan 3 entry; drop plan 1's "relaunch restores every saved window" and "dock shared through ui-state.toml" interims), this plan's status banner
- Modify: `NotificationCard.java:34` (rewrap the one long Javadoc line parked from plan 2)

- [ ] **Step 1: Find every stale mention** —
  `grep -rniE 'every saved window|restores every|shared .*ui-state|last window to change or close' docs/*.md jasper-*/README.md`;
  each hit is updated or is a dated record.
- [ ] **Step 2: Update the guides** with the smallest accurate edits (AGENTS.md "Documentation edits").
- [ ] **Step 3: Run** `./gradlew check`, expected BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `Docs: one window comes back (full windows plan 3)`
- [ ] **Step 5: Hand the GUI checks to the user** (not run by an agent): two windows with different tool windows keep
  them apart; Quit and relaunch opens the window active at Quit with its tabs and tool windows; closing one of two
  windows and relaunching after a crash; Empty State opens one terminal at the last bounds; Dock click while resident
  follows the setting; New Window copies the active window's tool windows without Zen or floating windows.
