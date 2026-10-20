# Workspace tabs 6a: saving and restoring the workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jasper saves its windows to `<home>/workspace.toml` within a second of every change and whenever a window
closes, Jasper quits or goes to the background, and restores them whenever the main window opens: at start, and every
time something needs the main window after it closed. The main window and its child windows come back where they were,
with their splits, groups, tab order, selection and active group; terminal tabs come back with their renamed title,
profile and last local directory, and only each group's selected tab starts its shell at once. SDK 0.21.0 reports a
restored terminal tab not yet shown as `NOT_STARTED`. Plugins' tabs come back in plan 6b.

**Architecture:** Plan 2's codec is wired by a new `workspace.WorkspaceStore` (the file, a one-second save timer that
does not slide, serialized writes on virtual threads, a hold while every window closes, and the last saved snapshot for
a restore within the same process). `WindowManager` builds the snapshot from its windows (`snapshot()`: each window's
`Placement`, its workspace's `Layout`, and a `TabRecord` for each tab that can come back, the others taken out with
`Layout.remove`) and restores it in `ensureMain`, its one opening point: the main window opens empty and
`WindowContent.restore` rebuilds its tabs before it shows, then each child window the same way. A restored terminal
tab's `TerminalPane` waits (`restored()`) until `WindowContent` sees it become its group's selected tab. Every frame
reports where its window is (`JasperWindow.placed`), and `WindowPlacement` takes saved bounds instead of `UiState`'s
`[main_window]`, which goes. The application creates the store with the Jasper home and awaits its writes at shutdown.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1 decision 5, 4, 6.4, 8, 9.2, 13, 14's state and version, 15's display and `workspace.toml` rows, 16's
`[main_window]`, 17 and 18). The first half of section 18's plan 6; [6b](2026-10-18-workspace-6b-plugin-restorers.md)
(the SDK's restore state and restorers, placeholders, and the Editor's, Remote's, the Vault's and the sample's tabs)
follows it. Plans [1](2026-10-17-workspace-1-single-instance.md), [2](2026-10-17-workspace-2-layout-model.md),
[3a](2026-10-17-workspace-3a-tab-registry.md), [3b](2026-10-18-workspace-3b-sdk-tabs.md),
[4a](2026-10-18-workspace-4a-windows.md), [4b](2026-10-18-workspace-4b-plugin-windows.md) and
[5](2026-10-18-workspace-5-tear-out.md) execute first, in that order.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 5's last commit. Run every command from the worktree root. Every anchor below was
re-taken from, and every edit script of Tasks 1-7 replayed on, plan 5's last commit `5b1ad0093` (the pre-flight's
refresh: `.superpowers/sdd/2026-10-18-workspace-6a-save-restore/preflight.md`); nothing was compiled or run. Every
edit quotes the text it replaces, and the scripts stop with a message when that text is not found the stated number of
times: re-find the quoted text (a plan before this one may have reworded it), adjust the anchor, re-run, and record it
in this plan's banner; never edit around a missing anchor. No anchor here is in plan 5's drag code, and nothing here
depends on how a child window got its bounds. Later tasks anchor on text earlier tasks of this plan wrote, and quote it
as those tasks leave it.

## Spec issues

None that block. Readings, each a ruling below:

1. Section 13 starts "each group's selected tab" at once and the others "when first shown". A maximized group hides the
   other groups, whose selected tabs still start: the rule is per group, as the spec words it (Rulings 4).
2. Section 16 removes `UiState`'s `[main_window]` and section 8 has `workspace.toml` keep every window's bounds; with no
   migration (spec 4's clean break), the first start after this plan opens the main window where a new window goes
   (Rulings 7).
3. Section 15 sets an unreadable `workspace.toml` aside with "one terminal tab"; it does not say whether anyone is told,
   nor what happens to a file that reads well but holds a window this build cannot restore. A notice names where the
   file was kept, and saved windows that do not all come back are set aside the same way (Rulings 13 and 20).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 4, 16): `UiState.MainWindow`, `mainWindow()`, `setMainWindow` and the `[main_window]` table go
  outright; an existing `ui-state.toml`'s `[main_window]` is ignored and dropped on its next save, with no migration and
  no "was removed" text. `WindowFrame.restoreLayout(UiState)` and `WindowPlacement.save` go.
- Spec 8's file is `<home>/workspace.toml` (`AppDirs.workspaceState()`), set aside as `workspace.toml.bad`; plan 2's
  `WorkspaceFile` is the codec and is not changed except one Javadoc line.
- Spec 13's timing: a change is saved within one second (`WorkspaceStore.DELAY`), on a virtual thread, never on the EDT;
  a window closing, Quit and going to the background save at once. The `--background` warm-up rehearsal never reads or
  writes `workspace.toml`.
- SDK versioning (the 0.17 to 0.20 bumps): `JasperSdk.VERSION` is `0.21.0`; every bundled manifest, the example external
  plugin and every range in the guides declare `sdk = ">=0.21.0, <0.22"`; `VersionRangeTest` lists 0.20 among the
  incompatible minors; plugin versions do not change in this plan (6b bumps the four plugins that restore tabs).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): nothing gains an edge.
  `workspace` already names `persistence` and `layout`; `persistence` names `layout`.
- No motion (spec 4): restored windows and tabs appear at once.
- The theme-migration ledger (`docs/design/theme-engine/migration.csv`, checked by `ThemeMigrationCoverageTest`): a new
  Java file under `jasper-app/src` whose text matches its `CANDIDATE` pattern (a Swing or AWT import, the word
  "appearance", `new J…`, `new …List`, `…Panel`, `…View`, `…Window`, …) gets a row in the task that creates it, with the
  `ledger.py` helper: `TestWindows` and `WorkspaceSnapshotTest` (Task 3), `WorkspaceStore`, `WorkspaceStoreTest` and
  `WorkspaceSavingTest` (Task 4), `WorkspaceRestoreTest` (Task 5), `JasperApplicationWorkspaceTest` (Task 6).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member and SDK enum constant has a comment, and an SDK addition carries `@since 0.21.0`.
- `WindowContent` and its tests import both `java.awt.*` and `java.util.*`: write `java.util.List` in full there.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan6a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan6a
cp build/plan3a/edits.py build/plan3a/ledger.py build/plan6a/ 2>/dev/null || cp build/plan4b/edits.py build/plan4b/ledger.py build/plan6a/ 2>/dev/null || echo "the helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan6a in place of build/plan3a"
ls build/plan6a/edits.py build/plan6a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` (replaces from the one `start`
through the first `end` after it, both included) and `regex(path, pattern, replacement, count)`; `ledger.py` gives
`add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan6a")`. Java text inside a script is in triple-quoted strings, so `\n` and
`…` in Java source stay as written.

## Review Focus

1. Closing every window never saves their tabs closing: the windows are saved first, then the store holds until they
   are restored, and a child window closed alone is saved without it at once. Task 4:
   `WorkspaceSavingTest.closingEveryWindowSavesThemFirstAndThenNothing`, `aChildWindowClosedAloneIsSavedWithoutItAtOnce`,
   `WorkspaceStoreTest.holdingSavesOnceThenNothingUntilTheWindowsAreRestoredFromWhatWasSaved`; Task 5:
   `WorkspaceRestoreTest.reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed`; Task 6:
   `JasperApplicationWorkspaceTest.goingToTheBackgroundSavesAndReopeningRestoresTheSameWindows`.
2. The timer: one save one second after the first change, holding every change made meanwhile; never slid, never on the
   EDT, writes in order, and shutdown waits for them. Task 4: `WorkspaceStoreTest.aChangeIsSavedOneSecondLater…`,
   `writesRunInTheOrderTheyWereAskedFor`; `WorkspaceSavingTest.everyChangeToWhatIsSavedIsSavedWithinASecond`; Task 6:
   `quitSavesEveryWindowAndTheNextStartRestoresThem`.
3. Restore fidelity: ids, groups, order, selection, active group, weights, maximize, places, names and profiles come
   back, and only each group's selected terminal tab starts; the others report `NOT_STARTED` to the registry and the SDK
   until shown. Task 5: `WorkspaceRestoreTest.theMainWindowAndItsChildWindowsComeBack…`,
   `aTabThatIsNotShownWaitsAndStartsWhenFirstShown`; Task 1: `RestoredPaneTest`, `HostedTerminalsTest
   .aPaneThatHasNotStartedSaysSo`.
4. What is not saved: a remote directory (saved as where a new shell starts), a program (a plain shell), a provided
   session (6b), and a child window left with nothing; nothing while no main window is open or while it opens. Task 3:
   `WorkspaceSnapshotTest`.
5. The start-up path: the first paint still settles residency when a restored main window shows no terminal (a
   maximized group counts alone), the rehearsal never reaches the store, and an unreadable file or a main window that
   cannot be restored still opens one terminal tab. Task 6: `aMainWindowRestoredWithoutATerminalStillMarksTheFirstPaint`,
   `anUnreadableWorkspaceStartsWithOneTabAndSaysWhereItWasKept`, `WorkspaceRestoreTest
   .aWindowShowsNoTerminalWhenAMaximizedGroupHidesTheOneThatStarted`.
6. Nothing saved is lost: a window that cannot be restored leaves the saved windows in `workspace.toml.bad` with the
   notice, and a failing snapshot or write never stops a window closing, Quit, holding or the next write. Task 5:
   `aMainWindowThatCannotBeRestoredOpensANewOneAndTheSavedWindowsAreKept`,
   `aChildWindowThatCannotBeRestoredIsLeftOutAndTheSavedWindowsAreKept`; Task 4: `WorkspaceStoreTest
   .aSnapshotThatFailsCostsOnlyThatSaveAndHoldingStillHolds`, `aWriteThatFailsOrIsRefusedNeverStopsTheNextOne`,
   `windowsThatDidNotAllComeBackAreKeptAsTheBadFileAndTold`, `WorkspaceSavingTest
   .aSnapshotThatFailsNeverKeepsAWindowFromClosing`.
7. The SDK's `NOT_STARTED` is the same on both runtimes: Task 1's `PluginContractTest
   .aRestoredPaneReportsNotStartedAndDropsText` (`FakeContractTest` and `AppContractTest`).

## Verified facts

Line numbers are from `5b1ad0093`, plan 5's last commit (see Workspace).

1. Plan 2's codec: `WorkspaceFile.read(Path)` (`persistence/WorkspaceFile.java:208`) returns `Loaded.Restore(Snapshot,
   Optional<Path> keptAside)` (`:169`) or `Loaded.Fresh(Optional<Path> setAside)` (`:183`), moving an unreadable file
   aside as `workspace.toml.bad`, and copying a readable one there (`keptAside`) when it dropped a window or a tab entry;
   `write(Path, Snapshot)` (`:458`) writes atomically. `WindowRecord` requires exactly one record per layout tab and a
   non-empty child layout; `Snapshot` puts the main window first. `TabRecord.Terminal(title, directory, profile)` and
   `TabRecord.Content(title, pluginId, restorer, state)`. `AppDirs.workspaceState()` (`platform/AppDirs.java:60`) is
   `<home>/workspace.toml`; nothing reads or writes it yet.
2. `UiState.MainWindow` and `[main_window]` (`persistence/UiState.java:75`, `:185`, `:306-307`, `:336`) are read only by
   `WindowPlacement` (`workspace/WindowPlacement.java:17-33`), which `WindowFrame.restoreLayout(UiState)` (`:121`)
   builds for the main window alone (`application/JasperApplication.java:565`); `WindowFrame.dispose` saves the main
   window's bounds into `ui-state.toml` (`:225-235`).
3. `WindowManager.ensureMain` (`workspace/WindowManager.java:188`) is the only place a main window opens, and it hands
   out no main window that is closing, nor any while `closeAll` runs; every request (`JasperApplication.java:902`,
   `:939`, `:956`, `:1821`, and the tab registry's `Windows.main`, whose tabs wait while the main window is being
   composed and open there through `TabRegistry.mainOpened` once it shows, or go with it through `mainFailed`) goes
   through it. `open(Opening, make)` (`:337`) composes, frames, shows and reports a window (an `Opening` may carry a
   torn-out tab's workspace bounds with its cursor) and discards it on a failure; `closeAll` (`:275`) serves closing the
   main window, `toBackground` (`JasperApplication.java:2055`) and `quitNow` (`:2112`), runs the closing-all hook once,
   while every window is whole, before it closes any, and closes every window whatever one throws, the first failure
   rethrown; the application's `closing` and `closingAll` hooks are empty (`:188-190`). `closed(JasperWindow)` (`:415`)
   forgets a window, closed or discarded, and then runs the closed hook, which (`JasperApplication.windowClosed`,
   `:1770`) requests shutdown when no window is left, the process is not resident and no auxiliary window is open.
4. `WindowContent`'s constructor opens one terminal tab unless `empty` (`workspace/WindowContent.java:345`); every
   layout change goes through `WorkspaceView.apply`, which calls `WindowContent.workspaceChanged` (`:1059`).
   `TabRegistry.add(TabId, Tab.Kind, TabContent, UUID)` (`contributions/TabRegistry.java:167`) registers a tab under a
   given id ("Workspace plan 6 restores saved ids through here"); `TerminalContent(TabId, …)`
   (`workspace/TerminalContent.java:46`) takes the id; `renamed()` (`:71`) is "what workspace plan 6 saves".
5. `TerminalPane.start()` (`workspace/TerminalPane.java:247`) launches at once; `localDirectory()` (`:500`) is empty for
   a remote OSC 7 report; `inheritableDirectory()` (`:513`) and `profile()` (`:537`). `ShellLauncher.launch` (`launch/
   ShellLauncher.java:138`) fails an explicit directory that is missing.
6. `StartupTrace.Milestone.FIRST_PAINT` is marked only by a window's first terminal painting
   (`JasperApplication.java:1729-1735`); the presentation's proof and residency's settle fallback wait for it.
7. The warm-up rehearsal builds its window and frame in `rehearsalWindow` (`JasperApplication.java:1089`) and never
   calls `WindowManager`.
8. `JasperApplication.afterDelay(Duration, Runnable)` (`:441`) is a one-shot Swing timer returning its cancel;
   `shutdown.await(pending)` (`:2181`) waits for a list of futures under the exit deadline.
9. Plan 5 gives every window `WindowManager.open` opens the manager's shared `TabDrag` (`content.tabDrag(tabDrag)`,
   before `make`); a tear-out opens its child window with `Opening.bounds` and `Opening.cursor`, the new window's
   workspace bounds and the release point, which `WindowShell.place` hands to `WindowFrame.placeWorkspace`
   (`WindowFrame.java:148`): it grows them by the chrome measured in the packed frame and keeps the frame on the
   display under the cursor (`TabDrag.fitted`, `TabDrag.displayFor`); every drop is a layout change through
   `WorkspaceView.apply` or the registry's `move`, in one window or two, within one EDT turn. It does not touch
   `WindowPlacement` or `WindowFrame.restoreLayout`.
10. THD Sourcing Crypto (read only, `~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at
   `3740e4e`) names `SessionState.RUNNING` in one test fixture (`CryptoPluginTest.java:35`) and switches over no
   `SessionState`; it opens only a dialog.

## Rulings

Record them in `docs/STATUS.md` (Task 7). "What — why".

1. **Plan 6 is two plans: 6a (windows and terminal tabs, the store, SDK 0.21.0) and 6b (plugins' tabs: restore state,
   restorers, placeholders, the Editor's, Remote's, the Vault's and the sample's tabs)** — the brief's split past about
   twelve tasks; 6a leaves Jasper runnable and saving.
2. **One SDK bump, to 0.21.0, in 6a's Task 1; 6b's additions are `@since 0.21.0` and ship before any release** — spec 14
   ("each bumps the SDK version"); THD Sourcing Crypto is rebuilt once, against the series' last version.
3. **`SessionState.NOT_STARTED` and `PaneSnapshot.State.NOT_STARTED` are each enum's first constant** — spec 13 ("report
   them as not started"); the constants follow a session's life.
4. **A restored terminal tab starts when it is its group's selected tab after any layout change, the restore included;
   the selected tabs of groups a maximize hides start too** — spec 13 names "each group's selected tab"; one rule in one
   place (`WindowContent.startShown`).
5. **A restored pane whose directory is gone, or unusable as a path, starts where a new shell starts; only a restored
   pane falls back (`anyDirectory`), and a WSL share is not probed** — spec 13 ("their last local directory"); an
   explicit request still fails loudly, and `LaunchSettings.at` already decides WSL paths off the EDT.
6. **A terminal tab saves its rename (else empty), its inheritable directory when the pane has a local one (else empty:
   where a new shell starts) and its profile name (else empty); a tab that ran a program saves as a shell; a provided
   session's tab is not saved in 6a** — spec 13; 6b saves provided sessions through their plugin.
7. **Every window keeps the place its frame reports (`JasperWindow.placed`): normal bounds and maximized bits, as it
   shows, moves, resizes or is maximized; `WindowPlacement` takes saved bounds; `[main_window]` goes with no migration**
   — spec 8, 13, 15 and 16; Jasper is an MVP (clean break).
8. **A change worth saving is any `WorkspaceView.apply` (open, close, move, select, activate, split, divider, maximize),
   a rename, a terminal's new local directory, a window opening or closing, and a frame reporting a new place** — spec
   8 saves the selection and the active group, so a selection is a layout change.
9. **The save timer starts at the first change after a save and never slides; the snapshot is taken on the EDT when it
   fires; a snapshot equal to the last saved is not written; writes run one after another on virtual threads** — spec 13
   ("within one second", "a crash loses at most a second"); a sliding timer would never fire while a shell keeps
   changing directory.
10. **Closing every window (the main window's close, Quit, going to the background) saves them first and holds saving
    until they are restored: in `WindowManager.closeAll`, at plan 4a's closing-all point, before its hook runs, since the
    manager owns the store (the application's `closing` and `closingAll` hooks stay empty); a child window closed alone is
    saved at once without it. The hold holds, and the closing-all and closed hooks run, whatever that save does** — spec
    6.4 and 13; closing their tabs must not overwrite the windows that come back, a child window the user closed is gone,
    and residency, the Dock and shutdown must hear of every window.
11. **A restore in the same process uses the snapshot it saved last, not the file** — a write may still be on its
    virtual thread; both hold the same windows.
12. **`WindowManager.ensureMain` is the one restore point: the main window, then each child window, each before its
    frame exists, the main window to the front when a child window came back; a request's directory opens as a new
    terminal tab after the restored ones; with nothing to restore, a new main window with one terminal tab there** —
    spec 6.4 ("every request restores it first") and 13.
13. **Saved windows that do not all come back are kept as `workspace.toml.bad`, with a WARNING notice "Jasper could not
    restore your windows" that names it ("Jasper opened what it could and kept the saved windows in …"): an unreadable
    file (plan 2's codec sets it aside), a readable one whose reader dropped a window or a tab entry (the codec copies it
    there, `Loaded.Restore.keptAside`, and that copy stands), or a readable one holding a window that cannot be restored
    (`WorkspaceStore.setAside(Snapshot)`, which writes it there before the next save would replace it)** — spec 15's
    set-aside; a vanished workspace with no word would look like a bug.
14. **The snapshot takes out every tab that cannot come back with `Layout.remove` and leaves out a child window left
    with none; the main window may be saved empty** — plan 2 Rulings 20.
15. **No snapshot while no main window is open or while it opens** — a restore must never save itself half-done, and
    an empty `windows` list is no workspace.
16. **A restored child window's timing line says `restore`** — `StartupRuns` picks a window by its trigger; a restored
    child window is not a menu's window.
17. **A main window that shows no terminal tab when it opens (no shown group's selected tab is a terminal: the
    maximized group alone counts while one is maximized) marks the first paint with its own paint** — fact 6: a hidden
    terminal paints nothing, and without it neither the presentation's proof nor residency's settle would come.
18. **The rehearsal never touches the store: the store is created in `startPlugins` and read only in `ensureMain`,
    which the rehearsal never calls** — spec 13; fact 7.
19. **Shutdown waits for the last write (`WorkspaceStore.written()` joins `shutdown.await`)** — Quit saves, then exits.
20. **A child window that cannot be restored is logged and left out; a main window that cannot be restored opens as a
    new main window with one terminal tab, in place and never discarded (the closed hook of the last window ends a
    Jasper that is not resident); either way the saved windows are kept aside (Rulings 13), and a restored terminal tab
    that fails to register closes its pane** — one bad record must not keep Jasper from opening, nor cost the user the
    windows it could not show.
21. **A restored window's place reaches its frame through `JasperWindow.placement`, checked against today's displays
    with its maximized bits, never through `Opening.bounds`, which stays plan 5's workspace bounds for a torn-out tab; a
    torn-out window's bounds are saved as its frame reports them when it shows, like any window's** — spec 13 and 15
    ("checked against the current displays"); workspace bounds carry no maximize, and `WindowFrame.placeWorkspace` grows
    them by the frame's chrome, which would grow a restored frame on every restore. Restored windows open
    through `WindowManager.open`, so they share plan 5's tab drag (fact 9), and a drop's one or two layout changes join
    one save (Rulings 9).
22. **A snapshot that throws costs only that save, logged; every write runs after the one before it however that ended
    (failed, refused by the executor, or an `Error`), and `written()` never completes exceptionally** — a broken window
    must not stop a close, Quit or the saves after it, and shutdown waits for writes, not their outcome.
23. **The testkit's fake holds a pane as Jasper holds a restored terminal tab (`FakePluginHost.holdPane`): it reports
    `NOT_STARTED`, drops what plugins send it, and starts when its tab is shown; the contract suite checks it on both
    runtimes** — spec 17's SDK line; plugin authors test against the fake.

## File structure

- SDK (`jasper-sdk`): `JasperSdk`, `terminal/SessionState` (Task 1).
- Testkit (`jasper-sdk-testkit`): `FakeWorkspace`, `FakeTerminals`, `FakePluginHost` (`holdPane`), `ContractHarness`,
  `PluginContractTest`, `FakeContractTest` (Task 1).
- Application: `terminals.PaneSnapshot`, `workspace.TerminalPane`, `launch.ShellLauncher`, `plugins.HostedTerminals`
  (Task 1); `persistence.UiState`, `persistence.WorkspaceFile` (Javadoc), `workspace.JasperWindow`, `WindowFrame`,
  `WindowPlacement` (whole file), `WindowShell` (Javadoc), `application.JasperApplication` (Task 2);
  `workspace.TerminalContent`, `TabHost`, `WindowContent`, `WindowManager` (Task 3); `workspace.WorkspaceStore` (new),
  `WindowManager`, `JasperWindow` (Task 4); `workspace.TerminalContent`, `WorkspaceView`, `WindowContent`,
  `WindowManager`, `JasperWindow` (Task 5); `workspace.WindowContent`, `application.JasperApplication` (Task 6).
- Plugins: every `plugin.toml` (Task 1).
- Tests: the SDK's version tests and `TerminalValuesTest`, `VersionRangeTest`, `KitCompatibilityTest`,
  `PluginStateStoreOptInTest`, `PresentationSelectionTest`, `LookFixtures`, `TerminalFixture`, `AppContractTest`,
  `HostedTerminalsTest`, `ShellLauncherTest`, `RestoredPaneTest` (new) (Task 1);
  `WindowPlacementTest` (whole file), `UiStateTest`, `WindowManagerTest` (Task 2); `TestWindows` and
  `WorkspaceSnapshotTest` (new) (Task 3); `WorkspaceStoreTest` and `WorkspaceSavingTest` (new) (Task 4);
  `WorkspaceRestoreTest` (new) (Task 5); `JasperApplicationWorkspaceTest` (new) and `WorkspaceRestoreTest` (one case)
  (Task 6).
- Documents: `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-icons.md`, the external example (Task 1);
  `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/plugin-authoring.md`,
  `jasper-sdk/README.md`, `docs/STATUS.md` (Task 7); the ledger in Tasks 3 to 6.

## Before you start

```bash
git branch --show-current                                                                                  # claude/workspace-tabs
git status --short                                                                                         # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for plugin windows as tabs"         # 4b's last commit
git log --oneline -1 --grep "Workspace tabs: documentation and status for tear-out and drag-back"          # 5's last commit
grep -c 'VERSION = "0.20.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                                         # 1
grep -c 'public Optional<MainWindow> ensureMain(String trigger, Path directory)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'public void restoreLayout(UiState state)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowFrame.java         # 1
grep -c 'public Path workspaceState()' jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java                           # 1
grep -c 'public static Loaded read(Path file)' jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java          # 1
grep -c 'public Tab add(TabId id, Tab.Kind kind, TabContent content, UUID window)' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
```

Every command prints the stated result (each `git log` one commit); plan 5's last commit sits above 4b's. Plans 1 to 5 each added a
`docs/STATUS.md` entry; this plan's goes above them. Create the two helper scripts (Global Constraints).

---

### Task 1: SDK 0.21.0 and a terminal tab that waits to be shown

Spec 13 and 14: a restored terminal tab runs nothing until it is first shown, and the registry and the SDK say it has not
started (`PaneSnapshot.State.NOT_STARTED`, `SessionState.NOT_STARTED`, Rulings 3); a restored tab's directory that is
gone by then starts the shell where a new shell starts (Rulings 5). The SDK version moves to 0.21.0 with it (Rulings 2).

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`, `terminal/SessionState.java`; every
  `plugins/*/src/main/resources/plugin.toml`, `docs/examples/external-plugin/src/main/resources/plugin.toml` and
  `build.gradle.kts`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-icons.md`
- Modify (application): `terminals/PaneSnapshot.java`, `workspace/TerminalPane.java`, `launch/ShellLauncher.java`,
  `plugins/HostedTerminals.java`
- Modify (testkit): `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`, `FakeTerminals.java`,
  `FakePluginHost.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/RestoredPaneTest.java`
- Test: `CapabilitiesTest`, `FilesValuesTest`, `PaletteValuesTest`, `KitBoundaryTest`, `TerminalValuesTest` (SDK);
  `testFixtures/.../contract/ContractHarness.java`, `PluginContractTest.java`, `src/test/.../FakeContractTest.java`
  (testkit); `VersionRangeTest`, `KitCompatibilityTest`, `PluginStateStoreOptInTest`, `PresentationSelectionTest`,
  `testsupport/LookFixtures.java`, `TerminalFixture`, `AppContractTest`, `HostedTerminalsTest`, `ShellLauncherTest`
  (application)

**Interfaces:**
- Consumes: nothing new.
- Produces: `JasperSdk.VERSION = "0.21.0"`; `SessionState.NOT_STARTED` (first constant); `PaneSnapshot.State.NOT_STARTED`;
  package `void TerminalPane.restored()` and `boolean TerminalPane.waiting()`; `ShellLauncher.Launch launch(Optional<Path>
  directory, boolean anyDirectory, Optional<String> profile, Optional<List<String>> program, Optional<UUID> pane,
  Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion)`; testkit `public void
  FakePluginHost.holdPane(UUID paneId)` (`@since 0.21.0`); test `TerminalFixture.holdPane(UUID)`,
  `ContractHarness.holdTerminalPane(UUID)`.

- [ ] **Step 1: Write the failing tests**

The SDK reports 0.21.0, a 0.20 manifest is skipped by its range, the SDK's states run in the order of a session's life,
the contract fixture can hold a pane and plugins see it as not started, a restored pane waits and then starts where a new
shell starts when its directory is gone, and the launcher falls back only for such a pane:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
S = "jasper-sdk/src/test/java/dev/jasper/sdk/"
for test in ["CapabilitiesTest.java", "files/FilesValuesTest.java", "palette/PaletteValuesTest.java", "ui/KitBoundaryTest.java"]:
    edit(S + test, [('isEqualTo("0.20.0")', 'isEqualTo("0.21.0")', 1)])
edit(S + "CapabilitiesTest.java", [("    @Test void theSdkIsVersionTwentyWithThePresentationCapability() {",
                                    "    @Test void theSdkIsVersionTwentyOneWithThePresentationCapability() {", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/"
edit(A + "plugins/VersionRangeTest.java", [
    ("    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroTwenty() {",
     "    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroTwentyOne() {", 1),
    ('        assertThat(VersionRange.parse(">=0.20.0, <0.21").contains(host)).as("every bundled range").isTrue();\n',
     '        assertThat(VersionRange.parse(">=0.20.0, <0.21").contains(host)).as("the former bundled range").isFalse();\n'
     '        assertThat(VersionRange.parse(">=0.21.0, <0.22").contains(host)).as("every bundled range").isTrue();\n', 1)])
edit(A + "plugins/KitCompatibilityTest.java", [(r'''    /** Workspace tabs plan 4b: a manifest of the previous minor, 0.19, is skipped by its range before any class is read. */
    @Test void aZeroNineteenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.19.0, <0.20",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.19.0, <0.20.0; this is SDK 0.20.0"));
    }''', r'''    /** Workspace tabs plan 6a: a manifest of the previous minor, 0.20, is skipped by its range before any class is read. */
    @Test void aZeroTwentyRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.20.0, <0.21",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.20.0, <0.21.0; this is SDK 0.21.0"));
    }''', 1)])
edit(A + "testsupport/LookFixtures.java", [
    ("    /** The required descriptor keys of a plugin on SDK 0.20. */", "    /** The required descriptor keys of a plugin on SDK 0.21. */", 1),
    (r'+ "sdk = \">=0.20.0, <0.21\"\n";', r'+ "sdk = \">=0.21.0, <0.22\"\n";', 1)])
# The SDK names a pane that has not started; the app's contract fixture can hold one.
edit("jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java", [(
    "        assertThat(unknown.state()).isEqualTo(SessionState.EXITED);\n",
    "        assertThat(unknown.state()).isEqualTo(SessionState.EXITED);\n"
    "        assertThat(SessionState.values()).as(\"in the order of a session's life\")\n"
    "            .containsExactly(SessionState.NOT_STARTED, SessionState.CONNECTING, SessionState.RUNNING, SessionState.EXITED);\n", 1)])
edit(A + "plugins/TerminalFixture.java", [
    ("                pane.state.equals(\"CONNECTING\") ? PaneSnapshot.State.STARTING : pane.state.equals(\"EXITED\") ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING,",
     "                pane.state.equals(\"NOT_STARTED\") ? PaneSnapshot.State.NOT_STARTED : pane.state.equals(\"CONNECTING\") ? PaneSnapshot.State.STARTING\n"
     "                    : pane.state.equals(\"EXITED\") ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING,", 1),
    ("""    /** What the pane's shell reports: a local directory when {@code hostOrEmpty} is empty, a remote one otherwise. */
    void reportDirectory(UUID paneId, String hostOrEmpty, String path) {""",
     """    /** The pane waits, as a restored terminal tab not yet shown does (workspace tabs spec 13). */
    void holdPane(UUID paneId) { panes.get(paneId).state = "NOT_STARTED"; }

    /** What the pane's shell reports: a local directory when {@code hostOrEmpty} is empty, a remote one otherwise. */
    void reportDirectory(UUID paneId, String hostOrEmpty, String path) {""", 1)])
edit(A + "plugins/HostedTerminalsTest.java", [("    @Test void aClosedPaneKeepsItsLastValuesAndIgnoresCommands() {",
    """    /** Workspace tabs plan 6a: a restored terminal tab not yet shown reports that its session has not started. */
    @Test void aPaneThatHasNotStartedSaysSo() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), pane = fixture.addPane(tab, "zsh", Path.of("/src"));
        fixture.holdPane(pane);
        assertThat(terminals.pane(pane).orElseThrow().info().state()).isEqualTo(SessionState.NOT_STARTED);
        assertThat(terminals.pane(pane).orElseThrow().info().exitStatus()).isEmpty();
    }

    @Test void aClosedPaneKeepsItsLastValuesAndIgnoresCommands() {""", 1)])
edit(A + "workspace/ShellLauncherTest.java", [("""    @Test @DisabledOnOs(OS.WINDOWS)
    void aPanesShellNamesThePaneLastOverTheConfiguredEnvironment() throws Exception {""",
    """    /** Workspace tabs plan 6a: a restored tab's saved directory that is gone starts the shell where a new shell starts. */
    @Test void aMissingDirectoryStartsWhereANewShellStartsOnlyWhenAnyDirectoryWillDo() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        Queue<Path> started = new ArrayDeque<>();
        Queue<String> errors = new ArrayDeque<>();
        Path missing = directory.resolve("missing");
        BiConsumer<TerminalSession, Throwable> completion = (session, error) -> errors.add(error.getMessage());
        edt(() -> {
            new ShellLauncher(pending::add, () -> settings, (path, launch) -> { started.add(path); throw new IllegalStateException("started"); })
                .launch(Optional.of(missing), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
            new ShellLauncher(pending::add, path -> { started.add(path); throw new IllegalStateException("started"); }, "sh")
                .launch(Optional.of(missing), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
            new ShellLauncher(pending::add, () -> settings, (path, launch) -> { throw new AssertionError(); })
                .launch(Optional.of(missing), false, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(started).containsExactly(HOME, HOME);
        assertThat(errors).hasSize(3);
        assertThat(errors.remove()).isEqualTo("started");
        assertThat(errors.remove()).isEqualTo("started");
        assertThat(errors.remove()).as("any other launch still needs its directory").contains("Directory is unavailable");
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aPanesShellNamesThePaneLastOverTheConfiguredEnvironment() throws Exception {""", 1)])
PY
```

The contract suite holds both runtimes to it (Rulings 23): a held pane reports `NOT_STARTED`, drops what is sent to it,
and starts when its tab is shown; the app's fixture drops and starts as the fake will:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
C = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/"
edit(C + "ContractHarness.java", [(
    """    /** UI thread: the user turned to this window. */
    void activateTerminalWindow(java.util.UUID windowId);""",
    """    /**
     * UI thread: the pane waits as a restored terminal tab not yet shown does (workspace tabs spec 13): not started, dropping
     * what is sent to it, until its tab is shown.
     */
    void holdTerminalPane(java.util.UUID paneId);

    /** UI thread: the user turned to this window. */
    void activateTerminalWindow(java.util.UUID windowId);""", 1)])
edit(C + "PluginContractTest.java", [(
    """    @Test void injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt() throws Exception {""",
    """    /** Workspace tabs spec 13: a restored terminal tab not yet shown has not started, drops text, and starts when shown. */
    @Test void aRestoredPaneReportsNotStartedAndDropsText() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT), Set.of(), Set.of(), alpha::set);
        h.holdTerminalPane(only);
        h.ui(() -> {
            PaneHandle pane = alpha.get().terminals().pane(only).orElseThrow();
            assertThat(pane.info().state()).isEqualTo(SessionState.NOT_STARTED);
            pane.sendText("dropped");
        });
        h.flush();
        assertThat(h.sentToPane(only)).isEmpty();
        h.focusTerminalPane(only);
        h.ui(() -> {
            PaneHandle pane = alpha.get().terminals().pane(only).orElseThrow();
            assertThat(pane.info().state()).as("shown, it starts").isEqualTo(SessionState.RUNNING);
            pane.sendText("kept");
        });
        h.flush();
        assertThat(h.sentToPane(only)).containsExactly("write:kept");
    }

    @Test void injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt() throws Exception {""", 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
    """            @Override public void activateTerminalWindow(java.util.UUID windowId) { host.activateTerminalWindow(windowId); }""",
    """            @Override public void holdTerminalPane(java.util.UUID paneId) { host.holdPane(paneId); }
            @Override public void activateTerminalWindow(java.util.UUID windowId) { host.activateTerminalWindow(windowId); }""", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(A + "AppContractTest.java", [(
    """            @Override public void activateTerminalWindow(UUID windowId) { onEdt(() -> terminalFixture.activateWindow(windowId)); }""",
    """            @Override public void holdTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.holdPane(paneId)); }
            @Override public void activateTerminalWindow(UUID windowId) { onEdt(() -> terminalFixture.activateWindow(windowId)); }""", 1)])
edit(A + "TerminalFixture.java", [
    ("""            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),""",
     """            // A pane that has not started drops what is sent to it (workspace tabs spec 13).
            bytes -> { if (!pane.state.equals("NOT_STARTED")) pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)); },
            text -> { if (!pane.state.equals("NOT_STARTED")) pane.sent.add("paste:" + text); },""", 1),
    ("""        registry.atomically(() -> { select(pane.tab); registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id)); });""",
     """        // A held pane starts when its tab is shown, as a restored terminal tab does.
        if (pane.state.equals("NOT_STARTED")) pane.state = "";
        registry.atomically(() -> { select(pane.tab); registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id)); });""", 1)])
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/RestoredPaneTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: a restored terminal tab's pane runs nothing until its tab is shown, then starts where it can. */
class RestoredPaneTest {
    @TempDir Path folder;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final Queue<Path> started = new ArrayDeque<>();
    private final ShellLauncher launcher = new ShellLauncher(pending::add,
        directory -> { started.add(directory); return IdleTerminalSession.start(); }, "idle-test-session");

    @Test void aRestoredPaneSaysItHasNotStartedUntilItStartsWhereANewShellStartsWhenItsDirectoryIsGone() throws Exception {
        Path gone = folder.resolve("deleted");
        TerminalPane[] pane = new TerminalPane[1];
        try {
            edt(() -> {
                pane[0] = new TerminalPane(gone, launcher);
                pane[0].restored();
                PaneSnapshot waiting = pane[0].snapshot();
                assertThat(pane[0].waiting()).isTrue();
                assertThat(waiting.state()).isEqualTo(PaneSnapshot.State.NOT_STARTED);
                assertThat(waiting.workingDirectory()).contains(gone);
                assertThat(waiting.columns()).isZero();
                assertThat(pane[0].running()).isFalse();
                assertThat(pending).as("nothing launches before the tab is shown").isEmpty();
                pane[0].start();
                assertThat(pane[0].waiting()).isFalse();
                assertThat(pane[0].snapshot().state()).isEqualTo(PaneSnapshot.State.STARTING);
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> pane[0].running());
            assertThat(started).as("the saved directory is gone: where a new shell starts").containsExactly(HOME);
            edt(() -> assertThat(pane[0].snapshot().state()).isEqualTo(PaneSnapshot.State.RUNNING));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }

    @Test void aPaneJasperOpenedStillNeedsItsDirectory() throws Exception {
        Path gone = folder.resolve("deleted");
        TerminalPane[] pane = new TerminalPane[1];
        Queue<String> failures = new ArrayDeque<>();
        try {
            edt(() -> {
                pane[0] = new TerminalPane(gone, launcher);
                pane[0].onFailure = (message, cause) -> failures.add(message);
                assertThat(pane[0].waiting()).isFalse();
                pane[0].start();
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> !failures.isEmpty());
            assertThat(failures.remove()).contains("Directory is unavailable");
            assertThat(started).isEmpty();
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.CapabilitiesTest' :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL: `expected: "0.21.0" but was: "0.20.0"`, and `cannot find symbol` for `NOT_STARTED`, `holdPane`,
`restored()`, `waiting()` and the seven-argument `launch`.

- [ ] **Step 3: The version, the manifests and the guides**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java", [('VERSION = "0.20.0";', 'VERSION = "0.21.0";', 1)])
for plugin in ["editor", "enterprise", "fileserver", "history", "indexer", "remote", "sample", "snippets", "vault"]:
    edit(f"plugins/{plugin}/src/main/resources/plugin.toml", [('sdk = ">=0.20.0, <0.21"', 'sdk = ">=0.21.0, <0.22"', 1)])
edit("docs/examples/external-plugin/src/main/resources/plugin.toml", [('sdk = ">=0.20.0, <0.21"', 'sdk = ">=0.21.0, <0.22"', 1)])
edit("docs/examples/external-plugin/build.gradle.kts", [('getOrElse("0.20.0")', 'getOrElse("0.21.0")', 1)])
# 0.21 is a clean break: a guide's range for an older feature no longer loads, so every range in the guides is 0.21's.
for guide, count in {"docs/plugin-authoring.md": 14, "jasper-sdk/README.md": 8, "docs/sdk-icons.md": 1}.items():
    edit(guide, [('sdk = ">=0.20.0, <0.21"', 'sdk = ">=0.21.0, <0.22"', count)])
edit("jasper-sdk/README.md", [(
    "(0.20.0 is a breaking boundary, described under Main and child windows below; 0.19.0 is one too,",
    "(0.21.0 is a breaking boundary for saved workspaces; 0.20.0 is one too, described under Main and child windows\n"
    "below; 0.19.0 is one too,", 1)])
# The tests that pin the current SDK by hand follow it, as plan 4b moved them to 0.20.
T = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(T + "KitCompatibilityTest.java", [('var current=candidate(">=0.20.0, <0.21",', 'var current=candidate(">=0.21.0, <0.22",', 1)])
edit(T + "PluginStateStoreOptInTest.java", [('Version.parse("0.20.0");', 'Version.parse("0.21.0");', 1),
    ('VersionRange.parse(">=0.20.0, <0.21")', 'VersionRange.parse(">=0.21.0, <0.22")', 1)])
edit(T + "PresentationSelectionTest.java", [('Version.parse("0.20.0");', 'Version.parse("0.21.0");', 1),
    ('VersionRange.parse(">=0.20.0, <0.21")', 'VersionRange.parse(">=0.21.0, <0.22")', 2)])
PY
```

Task 7 points the README's version line at the section it adds.

- [ ] **Step 4: The state and the waiting pane**

`SessionState` and `PaneSnapshot.State` gain `NOT_STARTED`; `HostedTerminals` maps one to the other; a pane that is
`restored()` reports `NOT_STARTED` until `start()`, which passes `anyDirectory` to the launcher; the launcher, off the
EDT, starts such a pane's shell where a new shell starts when its directory is not there (a WSL share is never touched
here, as `LaunchSettings.at` decides):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/terminal/SessionState.java", [(
    """public enum SessionState {
    /** Not running yet: a local shell is starting, or a plugin is connecting. */""",
    """public enum SessionState {
    /**
     * Not started: the pane of a terminal tab Jasper restored at start and has not shown yet. Its shell starts, or its
     * plugin's session connects, when the tab is first shown; until then nothing runs and text sent to it is dropped.
     *
     * @since 0.21.0
     */
    NOT_STARTED,
    /** Not running yet: a local shell is starting, or a plugin is connecting. */""", 1)])
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "terminals/PaneSnapshot.java", [(
    "    /** A session's life. */\n    public enum State { STARTING, RUNNING, EXITED }",
    "    /** A session's life; {@code NOT_STARTED} is a restored terminal tab's pane until its tab is first shown. */\n"
    "    public enum State { NOT_STARTED, STARTING, RUNNING, EXITED }", 1)])
edit(A + "plugins/HostedTerminals.java", [(
    "            case STARTING -> SessionState.CONNECTING; case RUNNING -> SessionState.RUNNING; case EXITED -> SessionState.EXITED;",
    "            case NOT_STARTED -> SessionState.NOT_STARTED;\n"
    "            case STARTING -> SessionState.CONNECTING; case RUNNING -> SessionState.RUNNING; case EXITED -> SessionState.EXITED;", 1)])
P = A + "workspace/TerminalPane.java"
edit(P, [
    ("""    /** The program this local pane runs instead of the shell; such a pane closes when it exits with status 0. */
    private final Optional<java.util.List<String>> program;
""", """    /** The program this local pane runs instead of the shell; such a pane closes when it exits with status 0. */
    private final Optional<java.util.List<String>> program;
    /** A restored pane whose tab has not been shown: nothing runs until {@link #start()} (workspace tabs spec 13). */
    private boolean waiting;
    /** Jasper restored this pane with its tab: a saved directory gone by the time it starts starts it where a new shell starts. */
    private boolean restored;
""", 1),
    ("""    void start() {
        if (request != null) { connect(); return; }
        ShellLauncher.Launch launched = launcher.launch(defaultDirectory ? Optional.empty() : Optional.of(launchDirectory),
                requestedProfile, program, Optional.of(id), replaced -> {""",
     """    /**
     * A pane Jasper restored with its tab (workspace tabs spec 13): it reports {@code NOT_STARTED} and runs nothing until
     * {@link #start()}, which its window calls when the tab is first shown; a saved directory that is gone by then starts
     * its shell where a new shell starts.
     */
    void restored() { waiting = true; restored = true; }

    /** Whether this pane is restored and waits for its tab to be shown. */
    boolean waiting() { return waiting; }

    void start() {
        waiting = false;
        if (request != null) { connect(); return; }
        ShellLauncher.Launch launched = launcher.launch(defaultDirectory ? Optional.empty() : Optional.of(launchDirectory), restored,
                requestedProfile, program, Optional.of(id), replaced -> {""", 1),
    ("""    PaneSnapshot snapshot() {
        Optional<String> provider = request == null ? Optional.empty() : Optional.of(request.providerId());
""", """    PaneSnapshot snapshot() {
        Optional<String> provider = request == null ? Optional.empty() : Optional.of(request.providerId());
        if (waiting) return new PaneSnapshot(title(), request == null ? Optional.of(launchDirectory) : Optional.empty(), 0, 0, false,
            PaneSnapshot.State.NOT_STARTED, OptionalInt.empty(), provider, Optional.empty(), shellLabel);
""", 1)])
L = A + "launch/ShellLauncher.java"
edit(L, [
    ("""    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program, Optional<UUID> pane,
                         Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        Objects.requireNonNull(directory, "directory");""",
     """    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program, Optional<UUID> pane,
                         Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, false, profile, program, pane, replaced, completion);
    }

    /**
     * As above; with {@code anyDirectory}, a {@code directory} that is not there once the shell starts counts as empty, so
     * the shell starts where a new shell starts instead of failing: a restored terminal tab's (workspace tabs spec 13).
     */
    public Launch launch(Optional<Path> directory, boolean anyDirectory, Optional<String> profile, Optional<List<String>> program,
                         Optional<UUID> pane, Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        Objects.requireNonNull(directory, "directory");""", 1),
    ("""                        Path target = directory.filter(path -> !WINDOWS || !UncHosts.namesForeignHost(path.toString(), false)).orElse(home);
                        if (!Files.isDirectory(target)) {""",
     """                        Path target = directory.filter(path -> !WINDOWS || !UncHosts.namesForeignHost(path.toString(), false)).orElse(home);
                        if (anyDirectory && !Files.isDirectory(target)) target = home;
                        if (!Files.isDirectory(target)) {""", 1),
    ("""                        Optional<Path> requested = chosen.startable(directory, WINDOWS);
""", """                        Optional<Path> requested = chosen.startable(directory, WINDOWS);
                        // A WSL share is never touched here: the profile's own start decides what to do with it.
                        if (anyDirectory) requested = requested.filter(path -> WslPaths.distroOf(path).isPresent() || Files.isDirectory(path));
""", 1)])
PY
```

The testkit's fake holds a pane as Jasper does: `NOT_STARTED`, dropping text, until its tab is shown, when a local pane
runs and a provided session connects:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
# A pane that has not started (a restored terminal tab not yet shown) on both runtimes: the testkit's fake and the
# application's contract fixture report it NOT_STARTED, drop text sent to it, and start it when its tab is shown.
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeWorkspace.java", [
    ("""        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
""", """        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        /** Restored and not shown yet: it reports NOT_STARTED, drops what is sent to it, and starts when its tab is shown. */
        boolean held;
""", 1),
    ("""        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected == tab) return;
        window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));""",
     """        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        // A held pane starts when its tab is shown, as a restored terminal tab does (workspace tabs spec 13).
        if (tab.pane != null && tab.pane.held) start(tab.pane);
        if (window.selected == tab) return;
        window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));""", 1),
    ("""    /** A terminal tab's one pane; a second, or one in a content tab, is refused. */""",
     """    /** A held pane starts: a provided session connects, a local shell runs. */
    private void start(Pane pane) {
        pane.held = false;
        if (pane.session != null) { sessions.connect(pane.session.owner, pane, pane.session.spec); return; }
        pane.info = FakeTerminals.withState(pane.info, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty());
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED,
            new TerminalEvents.SessionStateChanged(pane.id, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty()));
    }

    /** A terminal tab's one pane; a second, or one in a content tab, is refused. */""", 1)])
edit(K + "FakeTerminals.java", [(
    """            synchronized (workspace) { model().ifPresent(pane -> pane.sent.add(line)); }""",
    """            // A pane that has not started drops what is sent to it, as Jasper's does (workspace tabs spec 13).
            synchronized (workspace) { model().filter(pane -> !pane.held).ifPresent(pane -> pane.sent.add(line)); }""", 1)])
edit(K + "FakePluginHost.java", [(
    """    /**
     * Sets the text selected in a pane.""",
    """    /**
     * Holds a pane as Jasper holds a restored terminal tab it has not shown yet (workspace tabs spec 13): it reports
     * {@code SessionState.NOT_STARTED} and drops what plugins send it until its tab is shown ({@link #focusTerminalPane}
     * or its {@code TabHandle.select()}), when it starts.
     *
     * @param paneId the pane
     * @since 0.21.0
     */
    public void holdPane(UUID paneId) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.held = true;
            pane.info = FakeTerminals.withState(pane.info, dev.jasper.sdk.terminal.SessionState.NOT_STARTED, java.util.OptionalInt.empty());
        });
    }

    /**
     * Sets the text selected in a pane.""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.workspace.RestoredPaneTest' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.plugins.HostedTerminalsTest' --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: PASS. Then `./gradlew check --continue`: BUILD SUCCESSFUL (every manifest loads on 0.21.0, the guides' ranges
are 0.21's, and the external example builds against 0.21.0).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins docs
git commit -F - <<'EOF'
Workspace tabs: SDK 0.21.0 and a terminal tab that waits to be shown

A restored terminal tab's pane runs nothing until its tab is shown and
reports NOT_STARTED to the registry and the SDK; a saved directory that
is gone by then starts its shell where a new shell starts. The testkit's
fake holds a pane the same way, and the contract suite checks both. The
SDK, every bundled manifest, the external example and the guides move to
0.21.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Every window says where it is, and `[main_window]` goes

Spec 13, 15 and 16: every window's bounds are saved, the main window's and each child window's alike, and restored
against today's displays as `WindowPlacement` does; `UiState`'s `[main_window]` goes with no migration (Rulings 7).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`, `persistence/WorkspaceFile.java` (one
  Javadoc line), `workspace/JasperWindow.java`, `workspace/WindowFrame.java`, `workspace/WindowShell.java` (Javadoc),
  `application/JasperApplication.java`
- Replace: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPlacement.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPlacementTest.java`
- Test: `persistence/UiStateTest.java`, `workspace/WindowManagerTest.java`

**Interfaces:**
- Consumes: `UiState.Bounds` (unchanged).
- Produces: `public record JasperWindow.Placement(Optional<UiState.Bounds> bounds, int extendedState)` with
  `Placement.NONE`; `public final JasperWindow.Placement JasperWindow.placement()`; `public final void
  JasperWindow.placed(JasperWindow.Placement value)`; `public void WindowFrame.place(JasperWindow.Placement saved,
  Consumer<JasperWindow.Placement> report)`; package `WindowPlacement(Optional<UiState.Bounds> saved, int extendedState)`
  and `JasperWindow.Placement WindowPlacement.placement(int extendedState)`.
- Removes: `UiState.MainWindow`, `UiState.mainWindow()`, `UiState.setMainWindow`, `WindowFrame.restoreLayout(UiState)`,
  `WindowPlacement.save`.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPlacementTest.java` (its subject no longer
reads `UiState`):

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** A window's place (workspace tabs spec 13 and 15): its normal bounds, kept apart from maximizing, fitted to today's displays. */
class WindowPlacementTest {
    private static final Dimension MINIMUM = new Dimension(300, 200);

    private static WindowPlacement saved(int x, int y, int width, int height, int extendedState) {
        return new WindowPlacement(Optional.of(new UiState.Bounds(x, y, width, height)), extendedState);
    }

    @Test void savedBoundsComeBackExactlyOnADisplayTheyFit() {
        var placement = saved(-1200, 75, 1103, 807, Frame.NORMAL);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(-1600, 30, 1600, 1000)))).hasValue(new Rectangle(-1200, 75, 1103, 807));
        assertThat(placement.extendedState()).isEqualTo(Frame.NORMAL);
    }

    @Test void maximizingAndMinimizingKeepTheNormalBoundsAndOnlyTheMaximizedBitsAreKept() {
        var placement = new WindowPlacement(Optional.empty(), 0);
        assertThat(placement.placement(Frame.NORMAL)).as("nothing seen yet").isEqualTo(JasperWindow.Placement.NONE);
        placement.observe(new Rectangle(100, 90, 1103, 807), Frame.NORMAL);
        placement.observe(new Rectangle(0, 0, 1600, 1000), Frame.MAXIMIZED_BOTH);
        placement.observe(new Rectangle(0, 0, 1, 1), Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        JasperWindow.Placement kept = placement.placement(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        assertThat(kept).isEqualTo(new JasperWindow.Placement(Optional.of(new UiState.Bounds(100, 90, 1103, 807)), Frame.MAXIMIZED_BOTH));
        var restored = new WindowPlacement(kept.bounds(), kept.extendedState());
        assertThat(restored.restore(MINIMUM, List.of(new Rectangle(0, 0, 1600, 1000)))).hasValue(new Rectangle(100, 90, 1103, 807));
        assertThat(restored.extendedState()).as("never restored minimized").isEqualTo(Frame.MAXIMIZED_BOTH);
    }

    @Test void aWindowSpanningUnchangedDisplaysKeepsItsExactBounds() {
        assertThat(saved(1200, 100, 1000, 700, Frame.NORMAL).restore(MINIMUM,
            List.of(new Rectangle(0, 0, 1600, 1000), new Rectangle(1600, 0, 1600, 1000)))).hasValue(new Rectangle(1200, 100, 1000, 700));
    }

    @Test void aRemovedMonitorFitsTheWindowOntoTheRemainingScreen() {
        var placement = saved(-1700, 80, 1500, 1100, Frame.NORMAL);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(0, 25, 1280, 775)))).hasValue(new Rectangle(0, 25, 1280, 775));
        assertThat(placement.placement(Frame.NORMAL).bounds()).as("what is saved next is where it went")
            .hasValue(new UiState.Bounds(0, 25, 1280, 775));
    }

    @Test void aWindowWithoutSavedBoundsGoesWhereTheFramePutsANewWindow() {
        var placement = new WindowPlacement(Optional.empty(), Frame.MAXIMIZED_BOTH);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(0, 0, 1600, 1000)))).isEmpty();
        assertThat(placement.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
    }
}
```

`UiState` neither reads nor writes `[main_window]`, and a window keeps where its frame last said it is:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "persistence/UiStateTest.java", [(
    """    @Test void aVersionOneFileMigratesRegionsSizesAndVisibilityAndKeepsTheOtherSections() throws Exception {""",
    """    /** Workspace tabs spec 8: the main window's bounds live in workspace.toml; a [main_window] table is dropped on the next save. */
    @Test void aMainWindowTableIsNeitherReadNorWritten() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 2\\nrail_visible = false\\n[main_window]\\nx = 10\\ny = 20\\nwidth = 800\\nheight = 700\\nextended_state = 6\\n");
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).as("the rest still reads").isFalse();
        state.save();
        assertThat(Files.readString(file)).doesNotContain("main_window").contains("rail_visible = false");
    }

    @Test void aVersionOneFileMigratesRegionsSizesAndVisibilityAndKeepsTheOtherSections() throws Exception {""", 1)])
W = T + "workspace/WindowManagerTest.java"
edit(W, [
    ("import dev.jasper.app.launch.ShellLauncher;\n", "import dev.jasper.app.launch.ShellLauncher;\nimport dev.jasper.app.persistence.UiState;\n", 1),
    ("import java.util.List;\n", "import java.util.List;\nimport java.util.Optional;\n", 1),
    ("""    @Test void theMainWindowsConstructorIsPackagePrivate() {""",
     """    /** Workspace tabs spec 13: a window keeps where its frame last said it is, which workspace.toml saves. */
    @Test void aWindowKeepsWhereItsFrameSaysItIs() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.placement()).isEqualTo(JasperWindow.Placement.NONE);
            var placed = new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6);
            main.placed(placed);
            assertThat(main.placement()).isEqualTo(placed);
            assertThatThrownBy(() -> new JasperWindow.Placement(Optional.empty(), java.awt.Frame.ICONIFIED))
                .as("never saved minimized").isInstanceOf(IllegalArgumentException.class);
            main.requestClose();
            main.placed(JasperWindow.Placement.NONE);
            assertThat(main.placement()).as("a closed window keeps its last place").isEqualTo(placed);
        });
        launch();
    }

    @Test void theMainWindowsConstructorIsPackagePrivate() {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `JasperWindow.Placement`, `placement()`, `placed(…)` and the constructor
`WindowPlacement(Optional, int)`.

- [ ] **Step 3: `UiState` loses the main window**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
P = "jasper-app/src/main/java/dev/jasper/app/persistence/"
# The main window's bounds move to workspace.toml with every other window's (workspace tabs spec 8 and 16).
edit(P + "UiState.java", [
    ("""    /** Main window's normal bounds and maximized axes (AWT bits 2 and 4); never restore minimization. */
    public record MainWindow(Bounds bounds, int extendedState) {
        public MainWindow {
            Objects.requireNonNull(bounds);
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Invalid maximized state");
        }
    }

""", "", 1),
    ("    private MainWindow mainWindow;\n", "", 1),
    ("state.chooser = Chooser.DEFAULT; state.mainWindow = null; state.zenMode = false;", "state.chooser = Chooser.DEFAULT; state.zenMode = false;", 1),
    ("""        if (toml.get(List.of("main_window")) instanceof TomlTable table)
            bounds(table).ifPresent(bounds -> {
                int extended = number(table, "extended_state").filter(value -> (value & ~6) == 0).orElse(0);
                mainWindow = new MainWindow(bounds, extended);
            });
""", "", 1),
    ("""    public Optional<MainWindow> mainWindow() { return Optional.ofNullable(mainWindow); }
    public void setMainWindow(MainWindow value) { mainWindow = Objects.requireNonNull(value); }
""", "", 1),
    ("""        if (mainWindow != null) {
            Bounds bounds = mainWindow.bounds();
            text.append("\\n[main_window]\\nx = ").append(bounds.x()).append("\\ny = ").append(bounds.y())
                .append("\\nwidth = ").append(bounds.width()).append("\\nheight = ").append(bounds.height())
                .append("\\nextended_state = ").append(mainWindow.extendedState()).append('\\n');
        }
""", "", 1)])
edit(P + "WorkspaceFile.java", [(
    "     * @param extendedState its maximized bits only (2 and 4), as {@code UiState.MainWindow} keeps them",
    "     * @param extendedState its maximized bits only (2 and 4): a window never comes back minimized", 1)])
PY
```

- [ ] **Step 4: Placements**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPlacement.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A window's normal bounds, tracked apart from maximizing and minimizing, and restored against today's usable displays
 * (workspace tabs spec 13 and 15): what {@code workspace.toml} keeps for the main window and each child window.
 */
final class WindowPlacement {
    private Rectangle normal;
    private final int restoredState;

    /**
     * A window that was saved at {@code saved} with {@code extendedState}, or a new one.
     *
     * @param saved its normal bounds; empty for a new window, which goes where its frame puts it
     * @param extendedState its maximized bits; any other bit is dropped, so a window never comes back minimized
     */
    WindowPlacement(Optional<UiState.Bounds> saved, int extendedState) {
        normal = saved.map(b -> new Rectangle(b.x(), b.y(), b.width(), b.height())).orElse(null);
        restoredState = extendedState & Frame.MAXIMIZED_BOTH;
    }

    void observe(Rectangle bounds, int extendedState) {
        if (extendedState == Frame.NORMAL && bounds.width > 0 && bounds.height > 0) normal = new Rectangle(bounds);
    }

    /**
     * Where the window is now.
     *
     * @param extendedState the frame's state; only its maximized bits are kept
     * @return its normal bounds, empty until it has had any, and its maximized bits
     */
    JasperWindow.Placement placement(int extendedState) {
        return new JasperWindow.Placement(Optional.ofNullable(normal).map(b -> new UiState.Bounds(b.x, b.y, b.width, b.height)),
            extendedState & Frame.MAXIMIZED_BOTH);
    }

    int extendedState() { return restoredState; }

    Optional<Rectangle> restore(Dimension minimum, List<Rectangle> screens) {
        if (normal == null || screens.isEmpty()) return Optional.empty();
        // A deliberate span across adjacent displays is still an exact, usable placement.
        var uncovered = new java.awt.geom.Area(normal);
        for (Rectangle screen : screens) uncovered.subtract(new java.awt.geom.Area(screen));
        if (uncovered.isEmpty() && normal.width >= minimum.width && normal.height >= minimum.height)
            return Optional.of(new Rectangle(normal));
        // Pick the display with the largest overlap, falling back to the primary display if it was removed.
        Rectangle screen = screens.stream().max(Comparator.comparingLong(s -> overlap(normal, s))).orElseThrow();
        Dimension size = InitialWindowSize.fit(normal.getSize(), minimum, screen);
        int x = (int) Math.max(screen.x, Math.min((long) normal.x, (long) screen.x + screen.width - size.width));
        int y = (int) Math.max(screen.y, Math.min((long) normal.y, (long) screen.y + screen.height - size.height));
        normal = new Rectangle(x, y, size.width, size.height);
        return Optional.of(new Rectangle(normal));
    }

    private static long overlap(Rectangle a, Rectangle b) {
        Rectangle intersection = a.intersection(b);
        return intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
    }
}
```

`JasperWindow` keeps its `Placement`; `WindowFrame.place` puts the frame where it was saved and reports where it is
when it shows, moves, resizes or is maximized; disposing the frame saves nothing; the application places every window's
frame, the main window's and each child window's, and its window manager's comment stops saying frames keep anything:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "JasperWindow.java", [
    ("import java.awt.Component;\n", "import dev.jasper.app.persistence.UiState;\nimport java.awt.Component;\n", 1),
    ("import java.util.Objects;\n", "import java.util.Objects;\nimport java.util.Optional;\n", 1),
    ("""    private final WindowManager manager;
    private final WindowContent content;
    private WindowShell shell;
    private boolean closed;
""", """    /**
     * Where a window is (workspace tabs spec 13): what {@code workspace.toml} keeps, and where a restored window's frame
     * puts it.
     *
     * @param bounds its normal bounds, kept while it is maximized or minimized; empty until its frame has any
     * @param extendedState its maximized bits only ({@code Frame.MAXIMIZED_BOTH}'s 2 and 4)
     */
    public record Placement(Optional<UiState.Bounds> bounds, int extendedState) {
        /** A window not placed yet: its frame puts it where the platform puts a new window. */
        public static final Placement NONE = new Placement(Optional.empty(), 0);

        /** Requires the bounds; only the maximized bits are kept. */
        public Placement {
            Objects.requireNonNull(bounds, "bounds");
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Only the maximized bits are kept: " + extendedState);
        }
    }

    private final WindowManager manager;
    private final WindowContent content;
    private WindowShell shell;
    private boolean closed;
    private Placement placement = Placement.NONE;
""", 1),
    ("""    /** @return whether the window has closed */
    public final boolean closed() { return closed; }
""", """    /** @return whether the window has closed */
    public final boolean closed() { return closed; }

    /** @return where the window is, as its frame last said; where a restored window's frame puts it */
    public final Placement placement() { return placement; }

    /**
     * Its frame says where the window is now: after it shows, moves, resizes or is maximized. Ignored once it has closed.
     *
     * @param value the window's normal bounds and maximized bits
     */
    public final void placed(Placement value) {
        if (closed) return;
        placement = Objects.requireNonNull(value, "value");
    }
""", 1)])
F = W + "WindowFrame.java"
edit(F, [
    ("""    private WindowPlacement placement;
""", """    private WindowPlacement placement;
    private java.util.function.Consumer<JasperWindow.Placement> report = placed -> { };
""", 1),
    ("""    /** The main window's bounds from {@code state}, checked against the current displays; before the frame shows. */
    public void restoreLayout(UiState state) {
        placement = new WindowPlacement(state);
""", """    /**
     * Places the frame where {@code saved} puts it, checked against the current displays, and reports where the window is
     * from then on (workspace tabs spec 13); a window placed nowhere goes where the platform puts a new one. Before the
     * frame shows.
     *
     * @param saved where the window was saved, or {@link JasperWindow.Placement#NONE}
     * @param report hears where the window is whenever it changes
     */
    public void place(JasperWindow.Placement saved, java.util.function.Consumer<JasperWindow.Placement> report) {
        placement = new WindowPlacement(saved.bounds(), saved.extendedState());
        this.report = java.util.Objects.requireNonNull(report, "report");
""", 1),
    ("""    private void rememberBounds() {
        if (!closed && placement != null && frame.isShowing()) placement.observe(frame.getBounds(), frame.getExtendedState());
    }
""", """    private void rememberBounds() {
        if (closed || placement == null || !frame.isShowing()) return;
        placement.observe(frame.getBounds(), frame.getExtendedState());
        report.accept(placement.placement(frame.getExtendedState()));
    }
""", 1),
    ("    private void show() { frame.setVisible(true); reportState(); content.focusSelected(); }",
     "    private void show() { frame.setVisible(true); rememberBounds(); reportState(); content.focusSelected(); }", 1),
    ("""    /** After the content closed: remembers the main window's bounds, then disposes the frame. */
    private void dispose() {
        if (closed) return;
        rememberBounds();
        closed = true;
        frame.removeComponentListener(geometry);
        if (glass != null) glass.close();
        if (placement != null) placement.save(frame.getExtendedState());
        if (titleBar != null) titleBar.close();""", """    /** After the content closed: disposes the frame. */
    private void dispose() {
        if (closed) return;
        closed = true;
        frame.removeComponentListener(geometry);
        if (glass != null) glass.close();
        if (titleBar != null) titleBar.close();""", 1),
    ("    /** Disposes the frame without remembering its bounds: its window failed to open, or it is the rehearsal's. */",
     "    /** Disposes the frame, reporting nothing more: its window failed to open, or it is the rehearsal's. */", 1),
    ("import dev.jasper.app.persistence.UiState;\n", "", 1)])
edit(W + "WindowShell.java", [(
    " * @param dispose remembers what the frame keeps (the main window's bounds) and disposes it, after its content has closed",
    " * @param dispose disposes the frame, after its content has closed", 1), (
    "     * @param dispose remembers what the frame keeps and disposes it, after its content has closed",
    "     * @param dispose disposes the frame, after its content has closed", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [(
    """     * Every window: the main window and the child windows (workspace tabs spec 6.1). EDT. Its closing hooks are plan 6's,
     * which saves the windows there; until then each frame keeps what it keeps when it closes.""",
    """     * Every window: the main window and the child windows (workspace tabs spec 6.1). EDT. Its closing hooks are empty: no
     * frame keeps where its window was, the window does (workspace plan 6a Rulings 7 and 10).""", 1), (
    """    /**
     * A window's native frame under this process's look: the main window's at its saved bounds. A failure after the
     * frame is built disposes it, since its window never gets its shell to dispose it through, and propagates. EDT.
     */
    private WindowShell frameOnScreen(JasperWindow window) {
        var frame = new WindowFrame(window.content(), windowLook(), configuration == null ? null : windowConfiguration(), window.events());
        try {
            if (window.isMain()) frame.restoreLayout(uiState);""",
    """    /**
     * A window's native frame under this process's look, where the window was saved or, for a new window, where the
     * platform puts it; it reports where the window is from then on (workspace tabs spec 13). A failure after the frame
     * is built disposes it, since its window never gets its shell to dispose it through, and propagates. EDT.
     */
    private WindowShell frameOnScreen(JasperWindow window) {
        var frame = new WindowFrame(window.content(), windowLook(), configuration == null ? null : windowConfiguration(), window.events());
        try {
            frame.place(window.placement(), window::placed);""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowPlacementTest' --tests 'dev.jasper.app.persistence.UiStateTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app
git commit -F - <<'EOF'
Workspace tabs: every window says where it is

Each window keeps its normal bounds and maximized bits as its frame
reports them, and a frame is placed where its window was saved, checked
against today's displays. UiState's [main_window] goes: workspace.toml
keeps every window's place.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: What `workspace.toml` keeps of the open windows

Spec 8 and 13: the snapshot is each window's place, its layout and, for each tab that can come back, what it needs;
the rest are taken out of the layout as closing them would, and a child window left with none is left out (plan 2
Rulings 20, Rulings 6 and 14). A rename, a new local directory and every layout change tell the window's
`onLayoutChanged` (Rulings 8), which Task 4 turns into a save.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java`, `workspace/TabHost.java`,
  `workspace/WindowContent.java`, `workspace/WindowManager.java`, `docs/design/theme-engine/migration.csv` (two rows)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSnapshotTest.java`

**Interfaces:**
- Consumes: `WorkspaceFile.Snapshot`, `WindowRecord`, `TabRecord.Terminal`, `Role` (plan 2); `Layout.remove`,
  `Layout.isEmpty`; `JasperWindow.placement()` (Task 2).
- Produces: `public Optional<WorkspaceFile.Snapshot> WindowManager.snapshot()`; package
  `Optional<WorkspaceFile.TabRecord.Terminal> TerminalContent.record()`, `Runnable TerminalContent.onRenamed`,
  `Runnable WindowContent.onLayoutChanged`, `void WindowContent.layoutChanged()`; test fixture `TestWindows` (fields
  `pending`, `started`, `events`, `model`, `terminals`, `manager`; `TestWindows()`,
  `TestWindows(Function<Path, TerminalSession>)`, `launch()`).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java`, a window manager over stand-in frames for
this plan's tests:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Function;

/**
 * A window manager over stand-in frames, as {@code WindowManagerTest}'s, for the saved workspace's tests (workspace tabs
 * spec 13): each window's content is connected to one model and one terminal registry, and shells are idle sessions
 * launched when the test runs {@link #launch()}. EDT only.
 */
final class TestWindows {
    final Queue<Runnable> pending = new ArrayDeque<>();
    /** Where each shell started, in order. */
    final Queue<Path> started = new ArrayDeque<>();
    /** What the stand-in frames did and what the application heard, in order. */
    final Queue<String> events = new ArrayDeque<>();
    final Contributions model = new Contributions();
    final TerminalRegistry terminals = new TerminalRegistry();
    final WindowManager manager;
    private final ShellLauncher launcher;

    /** Windows whose shells are idle sessions. */
    TestWindows() { this(directory -> IdleTerminalSession.start()); }

    /** Windows whose shells {@code shells} starts in the directory it is given. */
    TestWindows(Function<Path, dev.jasper.terminal.session.TerminalSession> shells) {
        launcher = new ShellLauncher(pending::add, directory -> { started.add(directory); return shells.apply(directory); }, "idle-test-session");
        manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> events.add("closed " + name(window)), state -> { }, window -> { }, () -> { }));
    }

    static String name(JasperWindow window) { return window.isMain() ? "main" : "child"; }

    private WindowContent compose(WindowManager.Opening opening) {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
        return content;
    }

    private WindowShell frame(JasperWindow window) {
        String name = name(window);
        return new WindowShell(() -> events.add("show " + name), () -> events.add("front " + name), () -> events.add("dispose " + name),
            () -> events.add("discard " + name), () -> null, component -> false);
    }

    /** Runs the shells' launches the windows asked for so far. */
    void launch() { while (!pending.isEmpty()) pending.remove().run(); }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSnapshotTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8 and 13: what {@code workspace.toml} keeps of the open windows. */
class WorkspaceSnapshotTest {
    @TempDir Path folder;
    private TestWindows windows;

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private void windows() throws Exception { edt(() -> windows = new TestWindows()); }

    @Test void aSnapshotKeepsEachWindowsPlaceLayoutAndTerminalTabs() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            main.content().openTab(logs, null, Optional.of("zsh"));
            ((TerminalContent) main.content().selectedTab().content()).rename("tail");
            main.content().split(Side.RIGHT);
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            ChildWindow child = windows.manager.openTerminalWindow(work).orElseThrow();
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            assertThat(snapshot.windows()).hasSize(2);
            WorkspaceFile.WindowRecord saved = snapshot.main();
            assertThat(saved.role()).isEqualTo(WorkspaceFile.Role.MAIN);
            assertThat(saved.bounds()).contains(new UiState.Bounds(40, 60, 1400, 900));
            assertThat(saved.extendedState()).isEqualTo(6);
            assertThat(saved.layout()).as("the splits, groups, order and selection").isEqualTo(main.content().workspace().model());
            List<Tab> tabs = main.content().tabs();
            assertThat(saved.tabs().get(tabs.get(0).id())).isEqualTo(new WorkspaceFile.TabRecord.Terminal("", work.toString(), ""));
            assertThat(saved.tabs().get(tabs.get(1).id())).as("renamed, in its directory, with its profile")
                .isEqualTo(new WorkspaceFile.TabRecord.Terminal("tail", logs.toString(), "zsh"));
            assertThat(snapshot.children()).singleElement().satisfies(record -> {
                assertThat(record.role()).isEqualTo(WorkspaceFile.Role.CHILD);
                assertThat(record.bounds()).as("its frame has not said where it is").isEmpty();
                assertThat(record.layout()).isEqualTo(child.content().workspace().model());
            });
        });
        windows.launch();
    }

    @Test void tabsThatCannotComeBackAreTakenOutAndAChildWindowOfOnlySuchTabsIsLeftOut() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            Tab terminal = main.content().selectedTab();
            Tab plugins = windows.model.tabs().open("dev.example.notes", "todo", "Todo", main.id(), new javax.swing.JPanel());
            Tab away = windows.model.tabs().open("dev.example.notes", "away", "Away", main.id(), new javax.swing.JPanel());
            away.moveToNewWindow();
            assertThat(windows.manager.children()).hasSize(1);
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            assertThat(snapshot.main().layout().tabs()).as("a plugin's tab with no restorer is not saved").containsExactly(terminal.id());
            assertThat(snapshot.main().tabs()).containsOnlyKeys(terminal.id());
            assertThat(snapshot.children()).as("a child window holding only such tabs is left out").isEmpty();
            assertThat(plugins.closed()).as("taking it out of what is saved closes nothing").isFalse();
            terminal.close();
            assertThat(windows.manager.snapshot().orElseThrow().main().layout().isEmpty()).as("the main window may be empty").isTrue();
        });
        windows.launch();
    }

    @Test void aProgramTabIsSavedAsAShellAndNothingIsSavedWithoutAMainWindow() throws Exception {
        windows();
        edt(() -> {
            assertThat(windows.manager.snapshot()).as("no main window").isEmpty();
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            main.content().openTab(folder, null, Optional.empty(), Optional.of(List.of("top")));
            Tab program = main.content().selectedTab();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(program.id()))
                .as("the program is not saved: the tab comes back as a plain shell")
                .isEqualTo(new WorkspaceFile.TabRecord.Terminal("", folder.toString(), ""));
            windows.manager.closeAll();
            assertThat(windows.manager.snapshot()).isEmpty();
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aShellOnAnotherHostIsSavedAsWhereANewShellStarts() throws Exception {
        // The shell reports a directory on another host, as one does after ssh: that host's directory means nothing here.
        edt(() -> windows = new TestWindows(directory -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder()
                    .command(List.of("/bin/sh", "-c", "printf '\\033]7;file://build-host.example/srv/app\\007'; read answer"))
                    .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }));
        Tab[] tab = new Tab[1];
        edt(() -> tab[0] = windows.manager.ensureMain("cold", folder).orElseThrow().content().selectedTab());
        windows.launch();
        until(() -> ((TerminalContent) tab[0].content()).pane().snapshot().remoteDirectory().isPresent());
        edt(() -> assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(tab[0].id()))
            .isEqualTo(new WorkspaceFile.TabRecord.Terminal("", "", "")));
    }
}
```

Both match the ledger's pattern (the word `appearance` in an import, `new JasperWindow…`):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "TestWindows.java", "app.workspace", "TestWindows", T + "WorkspaceSnapshotTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 3; a window manager over stand-in frames")
ledger.add(T + "WorkspaceSnapshotTest.java", "app.workspace", "WorkspaceSnapshotTest", T + "WorkspaceSnapshotTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 3; what workspace.toml keeps of the open windows")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `snapshot()`.

- [ ] **Step 3: What a terminal tab saves, and the signal that something saved changed**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
# What a terminal tab saves (workspace tabs spec 13), and the signals that something saved changed.
edit(W + "TerminalContent.java", [
    ("import dev.jasper.app.launch.ShellLauncher;\n",
     "import dev.jasper.app.launch.ShellLauncher;\nimport dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("""    /** The pane asked to close (its shell exited under a closing policy, its provider ended, a failed launch). */""",
     """    /** The user renamed the tab, which {@code workspace.toml} saves. */
    Runnable onRenamed = () -> { };
    /** The pane asked to close (its shell exited under a closing policy, its provider ended, a failed launch). */""", 1),
    ("    void rename(String name) { state.rename(name); onChanged.run(); }",
     "    void rename(String name) { state.rename(name); onChanged.run(); onRenamed.run(); }", 1),
    ("""    /** The name the user gave the tab, kept apart from the shell's title (workspace plan 6 saves it). */
    Optional<String> renamed() { return state.renamed(); }
""", """    /** The name the user gave the tab, kept apart from the shell's title (workspace plan 6 saves it). */
    Optional<String> renamed() { return state.renamed(); }

    /**
     * What {@code workspace.toml} saves for this tab (workspace tabs spec 13): the name the user gave it, its shell's last
     * local directory and its profile. A directory on another host is saved as where a new shell starts, and a tab that ran
     * a program comes back as a plain shell. A session a plugin provides is not a shell: it is saved as its plugin says.
     *
     * @return the record; empty for a provided session's tab and once the tab has closed
     */
    Optional<WorkspaceFile.TabRecord.Terminal> record() {
        if (closed || pane.snapshot().providerId().isPresent()) return Optional.empty();
        Path directory = pane.localDirectory().isEmpty() ? null : pane.inheritableDirectory();
        return Optional.of(new WorkspaceFile.TabRecord.Terminal(renamed().orElse(""), directory == null ? "" : directory.toString(),
            pane.profile().orElse("")));
    }
""", 1),
    ("        onChanged = () -> { }; onEmpty = () -> { }; onError = (message, cause) -> { };",
     "        onChanged = () -> { }; onRenamed = () -> { }; onEmpty = () -> { }; onError = (message, cause) -> { };", 1)])
edit(W + "TabHost.java", [
    ("        terminal.onEmpty = () -> owner.closeTab(tab);\n",
     "        terminal.onEmpty = () -> owner.closeTab(tab);\n        terminal.onRenamed = owner::layoutChanged;\n", 1),
    ("        terminal.onEmpty = () -> { };\n",
     "        terminal.onEmpty = () -> { };\n        terminal.onRenamed = () -> { };\n", 1),
    ("        pane.onDirectoryChanged = (directory, remote) -> owner.report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));",
     """        pane.onDirectoryChanged = (directory, remote) -> {
            owner.report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
            // workspace.toml saves the last local directory (workspace tabs spec 13).
            owner.layoutChanged();
        };""", 1)])
edit(W + "WindowContent.java", [
    ("    Runnable onCloseWindow = () -> {};\n",
     "    Runnable onCloseWindow = () -> {};\n"
     "    /** What {@code workspace.toml} saves of this window changed: its layout, or a terminal tab's name or directory (spec 13). */\n"
     "    Runnable onLayoutChanged = () -> {};\n", 1),
    ("""    void workspaceChanged(boolean refocus) {
        if (closed) return;
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus) focusSelected();
    }
""", """    void workspaceChanged(boolean refocus) {
        if (closed) return;
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus) focusSelected();
        layoutChanged();
    }

    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }
""", 1),
    ("        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {}; fileChooser = folder -> java.util.Optional.empty();",
     "        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {}; fileChooser = folder -> java.util.Optional.empty();\n"
     "        onLayoutChanged = () -> {};", 1)])
PY
```

- [ ] **Step 4: The snapshot**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
M = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(M, [
    ("import dev.jasper.app.contributions.TabRegistry;\n",
     "import dev.jasper.app.contributions.TabRegistry;\nimport dev.jasper.app.layout.Layout;\nimport dev.jasper.app.layout.TabId;\n"
     "import dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("import java.util.ArrayList;\n", "import java.util.ArrayList;\nimport java.util.HashMap;\n", 1),
    ("""    /**
     * A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. A""",
     """    /**
     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13): the main window and its child windows, each
     * with where it is, its layout and what each of its tabs needs to come back. A tab that cannot come back is taken out
     * of its window's layout first, as closing it would, and a child window left with none is left out (workspace plan 2
     * Rulings 20); the main window may be empty.
     *
     * @return the snapshot; empty while no main window is open, or while it opens
     */
    public Optional<WorkspaceFile.Snapshot> snapshot() {
        requireEdt();
        if (main == null || openingMain) return Optional.empty();
        var records = new ArrayList<WorkspaceFile.WindowRecord>();
        for (JasperWindow window : windows()) windowRecord(window).ifPresent(records::add);
        return Optional.of(new WorkspaceFile.Snapshot(records));
    }

    private Optional<WorkspaceFile.WindowRecord> windowRecord(JasperWindow window) {
        WorkspaceView workspace = window.content().workspace();
        Layout layout = workspace.model();
        var tabs = new HashMap<TabId, WorkspaceFile.TabRecord>();
        for (TabId id : workspace.model().tabs()) {
            Optional<WorkspaceFile.TabRecord> saved = workspace.tab(id).flatMap(this::tabRecord);
            if (saved.isPresent()) tabs.put(id, saved.get());
            else layout = layout.remove(id);
        }
        if (!window.isMain() && layout.isEmpty()) return Optional.empty();
        JasperWindow.Placement placement = window.placement();
        return Optional.of(new WorkspaceFile.WindowRecord(window.isMain() ? WorkspaceFile.Role.MAIN : WorkspaceFile.Role.CHILD,
            placement.bounds(), placement.extendedState(), layout, tabs));
    }

    /** What a tab needs to come back: a terminal tab's name, directory and profile; any other tab is not saved. */
    private Optional<WorkspaceFile.TabRecord> tabRecord(Tab tab) {
        if (tab.content() instanceof TerminalContent terminal) return terminal.record().map(WorkspaceFile.TabRecord.class::cast);
        return Optional.empty();
    }

    /**
     * A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. A""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceSnapshotTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (the remote-directory test is skipped on Windows, where `/bin/sh` is not there).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: what workspace.toml keeps of the open windows

The window manager's snapshot holds each window's place and layout and,
for each terminal tab, its name, last local directory and profile; tabs
that cannot come back are taken out of the layout, and a child window
left with none is left out. Renames, directory changes and every layout
change tell the window that something saved changed.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Saving within a second, and when a window closes

Spec 13: a change is saved within one second, on a virtual thread, and a window closing is saved at once; closing every
window (closing the main window, Quit, going to the background) saves them first and then nothing until they come back,
so closing their tabs never overwrites them (Rulings 9, 10 and 15). A snapshot or a write that fails costs only that
save (Rulings 22), and the store can keep a workspace aside as `workspace.toml.bad` for Task 5 (Rulings 13).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSavingTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java`, `workspace/JasperWindow.java`,
  `docs/design/theme-engine/migration.csv` (three rows)

**Interfaces:**
- Consumes: `WindowManager.snapshot()`, `WindowContent.onLayoutChanged` (Task 3); `JasperWindow.placed` (Task 2);
  `WorkspaceFile.read`, `write`, `Loaded` (plan 2); `closeAll`'s closing-all point (plan 4a).
- Produces: `public final class WorkspaceStore` with `public static final Duration DELAY` (one second), `public
  WorkspaceStore(Path file, Supplier<Optional<WorkspaceFile.Snapshot>> snapshot, BiFunction<Duration, Runnable, Runnable>
  schedule, Executor writer, Consumer<Path> setAside)`, `public WorkspaceFile.Loaded load()`, `public void changed()`,
  `public void setAside(WorkspaceFile.Snapshot windows)`, `public void save()`, `public void hold()`, `public boolean
  held()`, `public CompletableFuture<Void> written()` (never exceptional);
  `public void WindowManager.workspace(WorkspaceStore value)`; package `void WindowManager.changed()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: saved within a second of a change, at once when a window closes, off the EDT, in order. */
class WorkspaceStoreTest {
    @TempDir Path dir;
    /** The saves changes scheduled: each runs when the test says its second has passed. */
    private final Queue<Runnable> due = new ArrayDeque<>();
    private final Queue<Duration> delays = new ArrayDeque<>();
    private int cancelled;
    /** The writes asked for, run by the test thread, never the EDT. */
    private final Queue<Runnable> writes = new ArrayDeque<>();
    private final Queue<Path> setAside = new ArrayDeque<>();
    private Optional<WorkspaceFile.Snapshot> current = Optional.empty();
    /** Thrown by the next snapshot when set, as a window that cannot say what it holds would. */
    private RuntimeException failing;
    /** Refuses the next write when set, as an executor that is shutting down does. */
    private boolean refuseNext;
    private WorkspaceStore store;

    private Path file() { return dir.resolve("workspace.toml"); }

    private void store() throws Exception {
        edt(() -> store = new WorkspaceStore(file(), () -> {
            if (failing != null) throw failing;
            return current;
        }, (delay, task) -> {
            delays.add(delay);
            due.add(task);
            return () -> { cancelled++; due.remove(task); };
        }, task -> {
            if (refuseNext) { refuseNext = false; throw new java.util.concurrent.RejectedExecutionException("shutting down"); }
            writes.add(task);
        }, setAside::add));
    }

    /** A workspace of one main window holding one terminal tab in {@code directory}. */
    private static WorkspaceFile.Snapshot workspace(String directory) {
        TabId tab = TabId.random();
        return new WorkspaceFile.Snapshot(java.util.List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            Layout.of(tab), Map.of(tab, new WorkspaceFile.TabRecord.Terminal("", directory, "")))));
    }

    private void write() {
        while (!writes.isEmpty()) {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            writes.remove().run();
        }
    }

    private WorkspaceFile.Snapshot onDisk() {
        return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot();
    }

    @Test void aChangeIsSavedOneSecondLaterWithEveryChangeMadeMeanwhile() throws Exception {
        store();
        current = Optional.of(workspace("/work/one"));
        edt(() -> { store.changed(); store.changed(); });
        assertThat(delays).containsExactly(Duration.ofSeconds(1));
        current = Optional.of(workspace("/work/two"));
        edt(() -> store.changed());
        assertThat(due).as("one save for every change within the second").hasSize(1);
        assertThat(file()).doesNotExist();
        edt(() -> due.remove().run());
        assertThat(file()).as("written off the EDT").doesNotExist();
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        edt(() -> store.changed());
        assertThat(due).as("the next change schedules the next save").hasSize(1);
    }

    @Test void aClosingWindowSavesAtOnceAndANothingChangedSaveWritesNothing() throws Exception {
        store();
        current = Optional.of(workspace("/work"));
        edt(() -> { store.changed(); store.save(); });
        assertThat(cancelled).as("the scheduled save is not needed").isEqualTo(1);
        assertThat(due).isEmpty();
        edt(() -> store.save());
        assertThat(writes).as("the same workspace is not written twice").hasSize(1);
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        current = Optional.empty();
        edt(() -> store.save());
        assertThat(writes).as("no main window: nothing to save").isEmpty();
    }

    @Test void writesRunInTheOrderTheyWereAskedFor() throws Exception {
        store();
        current = Optional.of(workspace("/first"));
        edt(() -> store.save());
        current = Optional.of(workspace("/second"));
        edt(() -> store.save());
        assertThat(writes).hasSize(1);
        assertThat(store.written()).isNotDone();
        writes.remove().run();
        assertThat(writes).as("the second write waits for the first").hasSize(1);
        write();
        assertThat(store.written()).isDone();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void holdingSavesOnceThenNothingUntilTheWindowsAreRestoredFromWhatWasSaved() throws Exception {
        store();
        WorkspaceFile.Snapshot open = workspace("/work");
        current = Optional.of(open);
        edt(() -> store.hold());
        write();
        assertThat(onDisk()).isEqualTo(open);
        current = Optional.of(workspace("/elsewhere"));
        edt(() -> {
            assertThat(store.held()).isTrue();
            store.changed();
            store.save();
        });
        assertThat(due).isEmpty();
        assertThat(writes).as("closing the windows' tabs is not saved").isEmpty();
        Files.delete(file());
        edt(() -> {
            assertThat(store.load()).as("what this process saved last, not the file").isEqualTo(new WorkspaceFile.Loaded.Restore(open, Optional.empty()));
            assertThat(store.held()).isFalse();
            store.changed();
        });
        assertThat(due).hasSize(1);
    }

    @Test void theFirstLoadReadsTheFileAndTellsWhatWasSetAside() throws Exception {
        store();
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Fresh(Optional.empty())));
        assertThat(setAside).as("no file: nothing set aside").isEmpty();
        WorkspaceFile.Snapshot saved = workspace("/work");
        WorkspaceFile.write(file(), saved);
        store();
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(saved, Optional.empty())));
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        store();
        Path bad = dir.resolve("workspace.toml.bad");
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Fresh(Optional.of(bad))));
        assertThat(setAside).containsExactly(bad);
        assertThat(bad).exists();
    }

    @Test void aSnapshotThatFailsCostsOnlyThatSaveAndHoldingStillHolds() throws Exception {
        store();
        failing = new IllegalStateException("a window cannot say what it holds");
        edt(() -> {
            store.save();
            store.hold();
            assertThat(store.held()).as("held although its save failed").isTrue();
        });
        assertThat(writes).isEmpty();
        failing = null;
        current = Optional.of(workspace("/work"));
        edt(() -> { store.load(); store.save(); });
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void aWriteThatFailsOrIsRefusedNeverStopsTheNextOne() throws Exception {
        store();
        refuseNext = true;
        current = Optional.of(workspace("/first"));
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        current = Optional.of(workspace("/second"));
        edt(() -> store.save());
        write();
        assertThat(store.written()).as("never completed exceptionally").isCompletedWithValue(null);
        assertThat(onDisk()).isEqualTo(current.get());
        Files.delete(file());
        Files.createDirectory(file());
        current = Optional.of(workspace("/third"));
        edt(() -> store.save());
        write();
        assertThat(file()).as("a write that failed is logged").isDirectory();
        Files.delete(file());
        current = Optional.of(workspace("/fourth"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void windowsThatDidNotAllComeBackAreKeptAsTheBadFileAndTold() throws Exception {
        store();
        WorkspaceFile.Snapshot loaded = workspace("/work");
        WorkspaceFile.write(file(), loaded);
        edt(() -> {
            assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(loaded, Optional.empty()));
            store.setAside(loaded);
        });
        Path bad = dir.resolve("workspace.toml.bad");
        assertThat(setAside).containsExactly(bad);
        write();
        assertThat(((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(bad)).snapshot()).isEqualTo(loaded);
        current = Optional.of(workspace("/fresh"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).as("the next save writes the windows as they are").isEqualTo(current.get());
    }

    /** Workspace plan 2's keptAside: a file whose reader dropped a window is kept aside as it was, and told once. */
    @Test void aFileThatDroppedAWindowOnReadingIsKeptAsideAsItWasAndToldOnce() throws Exception {
        WorkspaceFile.Snapshot kept = workspace("/work");
        TabId away = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(java.util.List.of(kept.main(), new WorkspaceFile.WindowRecord(
            WorkspaceFile.Role.CHILD, Optional.empty(), 0, Layout.of(away), Map.of(away, new WorkspaceFile.TabRecord.Terminal("", "/away", ""))))));
        String original = Files.readString(file()).replace("role = \"child\"", "role = \"side\"");
        Files.writeString(file(), original);
        store();
        Path bad = dir.resolve("workspace.toml.bad");
        edt(() -> {
            assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(kept, Optional.of(bad)));
            store.setAside(kept);
        });
        write();
        assertThat(setAside).as("told once, for the notice").containsExactly(bad);
        assertThat(Files.readString(bad)).as("the original text, with the window the reader dropped").isEqualTo(original);
    }

    @Test void theStoreBelongsToTheEdt() throws Exception {
        store();
        assertThatThrownBy(() -> store.changed()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.load()).isInstanceOf(IllegalStateException.class);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSavingTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: what is saved, and when: within a second of each change, and at once when a window closes. */
class WorkspaceSavingTest {
    @TempDir Path folder;
    private TestWindows windows;
    private WorkspaceStore store;
    /** The saves changes scheduled; {@link #second()} runs them, as their second passing does. */
    private final Queue<Runnable> due = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private Path file() { return folder.resolve("workspace.toml"); }

    private void windows() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); },
                Runnable::run, kept -> { });
            windows.manager.workspace(store);
        });
    }

    /** One scheduled save is waiting, and it saves now. */
    private void second() {
        assertThat(due).as("a change scheduled one save").hasSize(1);
        due.remove().run();
    }

    private WorkspaceFile.Snapshot saved() { return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot(); }

    @Test void everyChangeToWhatIsSavedIsSavedWithinASecond() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            second();
            assertThat(saved().main().layout()).as("the window opening").isEqualTo(main.content().workspace().model());
            Tab first = main.content().selectedTab();
            main.content().newTab(folder);
            second();
            assertThat(saved().main().layout().tabs()).as("a tab opening").hasSize(2);
            main.content().split(Side.RIGHT);
            second();
            assertThat(saved().main().layout().groups()).as("a split").hasSize(2);
            main.content().workspace().resizeSplit(List.of(), List.of(0.25, 0.75));
            second();
            assertThat(saved().main().layout()).as("a divider drag").isEqualTo(main.content().workspace().model());
            main.content().toggleMaximize();
            second();
            assertThat(saved().main().layout().maximized()).as("a maximize").isPresent();
            main.content().toggleMaximize();
            second();
            main.content().selectTab(first);
            second();
            assertThat(saved().main().layout().activeTab()).as("a selection").contains(first.id());
            var terminal = (TerminalContent) first.content();
            terminal.rename("build");
            second();
            assertThat(saved().main().tabs().get(first.id()).title()).as("a rename").isEqualTo("build");
            terminal.pane().onDirectoryChanged.accept(Optional.of(folder), Optional.empty());
            second();
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0));
            second();
            assertThat(saved().main().bounds()).as("the window moving").contains(new UiState.Bounds(10, 20, 1200, 800));
            main.content().closeTab(first);
            second();
            assertThat(saved().main().layout().tabs()).as("a tab closing").doesNotContain(first.id());
            main.content().newTab(folder);
            main.content().newTab(folder);
            assertThat(due).as("changes within the second join one save").hasSize(1);
            second();
        });
        windows.launch();
    }

    @Test void aChildWindowClosedAloneIsSavedWithoutItAtOnce() throws Exception {
        windows();
        edt(() -> {
            windows.manager.ensureMain("cold", folder).orElseThrow();
            ChildWindow child = windows.manager.openTerminalWindow(folder).orElseThrow();
            second();
            assertThat(saved().children()).hasSize(1);
            child.requestClose();
            assertThat(saved().children()).as("saved at once, its tabs closed with it").isEmpty();
            assertThat(due).isEmpty();
        });
        windows.launch();
    }

    @Test void aSnapshotThatFailsNeverKeepsAWindowFromClosing() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), () -> { throw new IllegalStateException("a window cannot say what it holds"); },
                (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); }, Runnable::run, kept -> { });
            windows.manager.workspace(store);
            windows.manager.ensureMain("cold", folder).orElseThrow();
            windows.manager.openTerminalWindow(folder).orElseThrow().requestClose();
            windows.manager.closeAll();
            assertThat(windows.events).as("the closed hook heard every window").contains("closed child", "closed main");
            assertThat(windows.manager.isEmpty()).isTrue();
            assertThat(store.held()).isTrue();
        });
        assertThat(file()).doesNotExist();
        windows.launch();
    }

    @Test void closingEveryWindowSavesThemFirstAndThenNothing() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            windows.manager.openTerminalWindow(folder).orElseThrow();
            main.content().newTab(folder);
            windows.manager.closeAll();
            assertThat(due).as("the save it waited for is done").isEmpty();
            assertThat(saved().children()).as("the child windows, saved before they closed").hasSize(1);
            assertThat(saved().main().layout().tabs()).hasSize(2);
            assertThat(store.held()).as("closing their tabs is not saved").isTrue();
        });
        windows.launch();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `WorkspaceStore` and `workspace(…)`.

- [ ] **Step 3: The store**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.WorkspaceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Where Jasper's windows are saved (workspace tabs spec 13): {@code workspace.toml} in the Jasper home. The first change
 * after a save is saved {@link #DELAY} later, with every change made meanwhile, so a crash loses at most that; a window
 * closing saves at once. Each save takes its snapshot on the EDT and writes it on {@code writer}, off the EDT, after the
 * writes before it, whatever happened to them. Closing the main window, Quit and going to the background save, then hold
 * every save ({@link #hold()}) until the windows are restored ({@link #load()}). A snapshot or a write that fails is
 * logged and costs only that save. EDT only, except {@link #written()}.
 */
public final class WorkspaceStore {
    /** The longest a change waits to be saved (spec 13: "within one second of each layout change"). */
    public static final Duration DELAY = Duration.ofSeconds(1);
    private static final System.Logger LOG = System.getLogger(WorkspaceStore.class.getName());

    private final Path file;
    private final Supplier<Optional<WorkspaceFile.Snapshot>> snapshot;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Executor writer;
    private final Consumer<Path> setAside;
    /** Cancels the save a change scheduled; null while none is pending. */
    private Runnable pending;
    private boolean held;
    /** What this process saved last: what reopening the main window restores. */
    private WorkspaceFile.Snapshot saved;
    /** Whether the reader kept the file {@link #load()} read last aside already ({@code Loaded.Restore.keptAside}). */
    private boolean keptOnLoad;
    private volatile CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

    /**
     * The store of {@code file}.
     *
     * @param file {@code workspace.toml}
     * @param snapshot what to save now; empty while there is nothing to save (no main window open)
     * @param schedule runs a task on the EDT after a delay and returns how to cancel it
     * @param writer where writes run, off the EDT
     * @param setAside told where saved windows that could not be restored were kept ({@code workspace.toml.bad}): an
     *     unreadable file {@link #load()} read, a file whose reader dropped a window or a tab entry, or a workspace
     *     {@link #setAside(WorkspaceFile.Snapshot)} keeps
     */
    public WorkspaceStore(Path file, Supplier<Optional<WorkspaceFile.Snapshot>> snapshot, BiFunction<Duration, Runnable, Runnable> schedule,
                          Executor writer, Consumer<Path> setAside) {
        this.file = Objects.requireNonNull(file, "file");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.setAside = Objects.requireNonNull(setAside, "setAside");
    }

    /**
     * What the main window restores, and saving starts again: what this process saved last, else the file. A file that
     * cannot be read was set aside ({@code workspace.toml.bad}) and {@code setAside} hears where; so does a file whose
     * reader dropped a window or a tab entry, which the reader copied there (workspace plan 2).
     *
     * @return the windows to restore, or that the main window opens fresh
     */
    public WorkspaceFile.Loaded load() {
        requireEdt();
        held = false;
        keptOnLoad = false;
        if (saved != null) return new WorkspaceFile.Loaded.Restore(saved, Optional.empty());
        WorkspaceFile.Loaded loaded = WorkspaceFile.read(file);
        if (loaded instanceof WorkspaceFile.Loaded.Fresh fresh) fresh.setAside().ifPresent(setAside);
        if (loaded instanceof WorkspaceFile.Loaded.Restore restore && restore.keptAside().isPresent()) {
            keptOnLoad = true;
            setAside.accept(restore.keptAside().get());
        }
        return loaded;
    }

    /**
     * Keeps {@code windows}, which {@link #load()} gave and which did not all come back, as {@code workspace.toml.bad}
     * (spec 15's set-aside: the next save would otherwise overwrite them), and {@code setAside} hears where. When the
     * reader kept the file they came from aside already, that copy, which holds them and what the reader dropped, stays
     * and nothing more is said. The next save writes the windows as they are then.
     *
     * @param windows the workspace as loaded
     */
    public void setAside(WorkspaceFile.Snapshot windows) {
        requireEdt();
        Objects.requireNonNull(windows, "windows");
        saved = null;
        if (keptOnLoad) return;
        // Named as WorkspaceFile names an unreadable file it sets aside.
        Path kept = file.resolveSibling(file.getFileName() + ".bad");
        writeLater(kept, windows);
        setAside.accept(kept);
    }

    /** Something that is saved changed: it is saved within {@link #DELAY}, with whatever else changes meanwhile. */
    public void changed() {
        requireEdt();
        if (held || pending != null) return;
        pending = schedule.apply(DELAY, this::due);
    }

    private void due() {
        pending = null;
        save();
    }

    /** Saves now, as a closing window does; a save a change scheduled is not needed any more. */
    public void save() {
        requireEdt();
        if (pending != null) {
            Runnable cancel = pending;
            pending = null;
            cancel.run();
        }
        if (held) return;
        Optional<WorkspaceFile.Snapshot> taken;
        try {
            taken = snapshot.get();
        } catch (RuntimeException failure) {
            // A window that cannot say what it holds costs this save, never the close or quit that asked for it.
            LOG.log(System.Logger.Level.WARNING, "Could not take the workspace's snapshot to save", failure);
            return;
        }
        if (taken.isEmpty() || taken.get().equals(saved)) return;
        WorkspaceFile.Snapshot writing = taken.get();
        saved = writing;
        writeLater(file, writing);
    }

    /**
     * The main window and its child windows close together (closing the main window, Quit, going to the background): saves
     * them as they are now, then nothing until {@link #load()} restores them, so closing their tabs is not saved. It holds
     * even when that save fails.
     */
    public void hold() {
        requireEdt();
        try {
            save();
        } finally {
            held = true;
        }
    }

    /** @return whether saving waits for the windows to be restored */
    public boolean held() { return held; }

    /** @return completes, never exceptionally, once every write asked for so far has finished, written or not; any thread */
    public CompletableFuture<Void> written() { return writes.handle((done, failure) -> (Void) null); }

    /** Writes after every write asked for before, however that one ended: a failed write, or a refused one, ends no chain. */
    private void writeLater(Path target, WorkspaceFile.Snapshot writing) {
        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(target, writing), writer);
    }

    private static void write(Path target, WorkspaceFile.Snapshot writing) {
        try {
            WorkspaceFile.write(target, writing);
        } catch (IOException | RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the workspace to " + target, failure);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The workspace store belongs to the EDT");
    }
}
```

The store and both tests match the ledger's pattern (`import javax.swing…`, `new JasperWindow…`):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "WorkspaceStore.java", "app.workspace", "WorkspaceStore", T + "WorkspaceStoreTest.java",
    "New in workspace-tabs plan 6a Task 4; saves workspace.toml; no component")
ledger.add(T + "WorkspaceStoreTest.java", "app.workspace", "WorkspaceStoreTest", T + "WorkspaceStoreTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 4; the store's timing, order and hold")
ledger.add(T + "WorkspaceSavingTest.java", "app.workspace", "WorkspaceSavingTest", T + "WorkspaceSavingTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 4; what is saved and when")
PY
```

- [ ] **Step 4: The window manager saves**

A window's layout change, a window opening, a window's frame moving it and a window closing reach the store; closing
every window holds it first, at the closing-all point, before the hook (Rulings 10):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
M = W + "WindowManager.java"
edit(M, [
    ("    private boolean openingMain;\n",
     "    private boolean openingMain;\n    /** Where the windows are saved; none in a test that does not save. */\n    private WorkspaceStore store;\n", 1),
    ("""    /** @return the main window while it is open */""",
     """    /**
     * Where the windows are saved and restored from (workspace tabs spec 13): the application gives it once it knows the
     * Jasper home. Without it nothing is saved and every main window opens fresh.
     *
     * @param value the store
     */
    public void workspace(WorkspaceStore value) { store = Objects.requireNonNull(value, "value"); }

    /** Something {@code workspace.toml} saves changed: a layout, a tab's name or directory, where a window is. */
    void changed() { if (store != null) store.changed(); }

    /** @return the main window while it is open */""", 1),
    ("""            window = make.apply(content);
            window.shell(hooks.frames().apply(window));
""", """            window = make.apply(content);
            content.onLayoutChanged = this::changed;
            window.shell(hooks.frames().apply(window));
""", 1),
    ("""            hooks.opened().accept(window);
            return Optional.of(window);""", """            hooks.opened().accept(window);
            changed();
            return Optional.of(window);""", 1),
    ("""     * @param closingAll every window is about to close (closing the main window, Quit, going to the background), before any
     *     does: plan 6 saves the main window and its child windows here""",
     """     * @param closingAll every window is about to close (closing the main window, Quit, going to the background), before any
     *     does, and the workspace store has saved them as they are""", 1),
    ("""     * first. The closing-all hook hears of it once before anything closes, while every window is whole. Every window
     * closes whatever closing another one throws, and no child window opens meanwhile; the first failure is rethrown once
     * the main window has closed, the others suppressed under it.
     */
    public void closeAll() {
        requireEdt();
        boolean nested = closingAll;
        closingAll = true;
        Throwable failure = null;
        try {
            try {
                if (!isEmpty()) hooks.closingAll().run();
            } catch (RuntimeException | Error thrown) {
                failure = thrown;
            }""", """     * first. While every window is whole, the workspace store saves them all and then holds until they are restored, and
     * the closing-all hook hears of it, once, before anything closes (workspace tabs spec 6.4 and 13). Every window
     * closes whatever closing another one throws, and no child window opens meanwhile; the first failure is rethrown once
     * the main window has closed, the others suppressed under it.
     */
    public void closeAll() {
        requireEdt();
        boolean nested = closingAll;
        closingAll = true;
        Throwable failure = null;
        try {
            if (!isEmpty()) {
                try {
                    // Closing the windows closes their tabs: that is not saved, or nothing would come back.
                    if (store != null) store.hold();
                } catch (RuntimeException | Error thrown) {
                    failure = thrown;
                }
                try {
                    hooks.closingAll().run();
                } catch (RuntimeException | Error thrown) {
                    failure = keep(failure, thrown);
                }
            }""", 1),
    ("""        if (lastActive == window) lastActive = null;
        hooks.closed().accept(window);""", """        if (lastActive == window) lastActive = null;
        try {
            // A window closing is saved at once: a child window the user closed is gone with its tabs (spec 13).
            if (store != null) store.save();
        } finally {
            // Residency, the Dock and shutdown hear of every window, whatever the save did.
            hooks.closed().accept(window);
        }""", 1)])
edit(W + "JasperWindow.java", [("""        if (closed) return;
        placement = Objects.requireNonNull(value, "value");
    }""", """        if (closed) return;
        placement = Objects.requireNonNull(value, "value");
        manager.changed();
    }""", 1), (
    """     * Closes the content, which closes the tabs it still holds, then the frame; the manager forgets the window. The
     * manager's closing hook hears of it first, while its content is whole (plan 6 saves the layout there). Every step
     * runs whatever an earlier one throws; the first failure is rethrown at the end, the others suppressed under it.""",
    """     * Closes the content, which closes the tabs it still holds, then the frame; the manager forgets the window, which
     * saves the windows left (workspace tabs spec 13). The manager's closing hook hears of it first, while its content is
     * whole. Every step runs whatever an earlier one throws; the first failure is rethrown at the end, the others
     * suppressed under it.""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceStoreTest' --tests 'dev.jasper.app.workspace.WorkspaceSavingTest' --tests 'dev.jasper.app.workspace.WorkspaceSnapshotTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: save the windows within a second and when one closes

WorkspaceStore saves the window manager's snapshot one second after the
first change, with every change made meanwhile, and at once when a
window closes; writes run in order on the writer it is given. Closing
every window saves them first and holds saving until they come back.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: Restoring the windows and their terminal tabs

Spec 6.4 and 13: whenever the main window opens it is restored as saved, with its child windows, their places, splits,
groups, tab order, selection and active group; a terminal tab comes back with its id, name, profile and directory, and
starts its shell once its group shows it (Rulings 4, 11, 12, 16, 20 and 21).

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java`, `workspace/WorkspaceView.java`,
  `workspace/WindowContent.java`, `workspace/WindowManager.java`, `workspace/JasperWindow.java`,
  `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: `WorkspaceStore.load()` (Task 4); `TerminalPane.restored()`, `waiting()` (Task 1); `TabRegistry.add(TabId,
  Tab.Kind, TabContent, UUID)` (plan 3a); `WindowTerminals.atomically`, `tabArrived` (plan 3b).
- Produces: package `int WindowContent.restore(WorkspaceFile.WindowRecord record)` (how many tabs came back), `void
  WorkspaceView.restore(Layout saved, List<Tab> restored)`, `void TerminalContent.startIfWaiting()`, `final int
  JasperWindow.restore(WorkspaceFile.WindowRecord record)`. `WindowManager.ensureMain(trigger, directory)` keeps its
  signature and restores; a window that cannot be restored has the store keep the saved windows aside
  (`WorkspaceStore.setAside`, Task 4).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: the main window and its child windows come back as they were saved; terminal tabs start when shown. */
class WorkspaceRestoreTest {
    @TempDir Path folder;
    private TestWindows windows;
    private WorkspaceStore store;
    private final Queue<Path> setAside = new ArrayDeque<>();
    private final Queue<Runnable> due = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private Path file() { return folder.resolve("workspace.toml"); }

    private void windows() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); },
                Runnable::run, setAside::add);
            windows.manager.workspace(store);
        });
    }

    private static Group group(TabId selected, TabId... tabs) {
        return new Group(GroupId.random(), List.of(tabs), Optional.of(selected));
    }

    private static WorkspaceFile.TabRecord.Terminal shell(String title, Path directory, String profile) {
        return new WorkspaceFile.TabRecord.Terminal(title, directory.toString(), profile);
    }

    @Test void theMainWindowAndItsChildWindowsComeBackWithTheirPlacesSplitsAndSelection() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId build = TabId.random(), tail = TabId.random(), shell = TabId.random(), away = TabId.random();
        Group left = group(tail, build, tail), right = group(shell, shell);
        var mainLayout = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.3), new Child(right, 0.7))), right.id(), Optional.empty());
        var main = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6, mainLayout,
            Map.of(build, shell("build", work, "zsh"), tail, shell("tail", logs, ""), shell, shell("", work, "")));
        Layout childLayout = Layout.of(away);
        var child = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.of(new UiState.Bounds(900, 100, 800, 600)), 0,
            childLayout, Map.of(away, shell("", logs, "")));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(main, child)));
        windows();
        edt(() -> {
            MainWindow restored = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(restored.content().workspace().model()).as("splits, groups, order, selection, active group").isEqualTo(mainLayout);
            assertThat(restored.placement()).isEqualTo(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            assertThat(restored.content().tabs()).extracting(Tab::id).containsExactly(build, tail, shell);
            assertThat(restored.content().selectedTab().id()).isEqualTo(shell);
            var titles = restored.content().tabs().stream().map(tab -> ((TerminalContent) tab.content()).renamed().orElse("")).toList();
            assertThat(titles).containsExactly("build", "tail", "");
            assertThat(((TerminalContent) restored.content().tabs().getFirst().content()).pane().profile()).contains("zsh");
            ChildWindow window = windows.manager.children().getFirst();
            assertThat(window.content().workspace().model()).isEqualTo(childLayout);
            assertThat(window.placement().bounds()).contains(new UiState.Bounds(900, 100, 800, 600));
            assertThat(windows.events).as("both frames shown, the main window brought to the front last")
                .containsSubsequence("show main", "opened main", "show child", "opened child", "front main");
        });
        windows.launch();
        assertThat(windows.started).as("each group's selected tab starts: tail, shell, and the child's").containsExactly(logs, work, logs);
    }

    @Test void aTabThatIsNotShownWaitsAndStartsWhenFirstShown() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId first = TabId.random(), second = TabId.random();
        Group only = group(second, first, second);
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            new Layout(only, only.id(), Optional.empty()), Map.of(first, shell("", work, ""), second, shell("", logs, ""))))));
        windows();
        Tab[] waiting = new Tab[1];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            waiting[0] = main.content().tabs().getFirst();
            var pane = ((TerminalContent) waiting[0].content()).pane();
            assertThat(pane.snapshot().state()).as("the registry reports it not started").isEqualTo(PaneSnapshot.State.NOT_STARTED);
            var entry = windows.terminals.tab(waiting[0].id().value()).orElseThrow().terminal().get().orElseThrow();
            assertThat(entry.snapshot().get().state()).isEqualTo(PaneSnapshot.State.NOT_STARTED);
        });
        windows.launch();
        assertThat(windows.started).as("only the selected tab").containsExactly(logs);
        edt(() -> windows.manager.main().orElseThrow().content().selectTab(waiting[0]));
        windows.launch();
        assertThat(windows.started).as("shown, it starts").containsExactly(logs, work);
    }

    @Test void aSavedDirectoryThatIsGoneStartsWhereANewShellStarts() throws Exception {
        TabId tab = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            Layout.of(tab), Map.of(tab, shell("", folder.resolve("deleted"), ""))))));
        windows();
        edt(() -> windows.manager.ensureMain("cold", null).orElseThrow());
        windows.launch();
        assertThat(windows.started).containsExactly(HOME);
        edt(() -> assertThat(windows.manager.main().orElseThrow().content().tabCount()).as("the tab stays").isEqualTo(1));
    }

    @Test void aFreshStartOpensOneTerminalTabAndAnUnreadableFileIsSetAsideAndTold() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabCount()).isEqualTo(1);
            windows.manager.closeAll();
        });
        windows.launch();
        assertThat(windows.started).as("no file: the request's directory").containsExactly(work);
        assertThat(setAside).isEmpty();
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabCount()).isEqualTo(1);
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
    }

    @Test void aRestoredMainWindowOpensTheRequestsDirectoryAsANewTab() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path asked = Files.createDirectories(folder.resolve("asked"));
        TabId tab = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            Layout.of(tab), Map.of(tab, shell("", work, ""))))));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", asked).orElseThrow();
            assertThat(main.content().tabs()).hasSize(2);
            assertThat(main.content().tabs().getFirst().id()).as("after the restore").isEqualTo(tab);
        });
        windows.launch();
        assertThat(windows.started).as("the restored tab, then the request's").containsExactly(work, asked);
    }

    @Test void aChildWindowWithNothingToRestoreIsLeftOut() throws Exception {
        TabId tab = TabId.random(), document = TabId.random();
        var main = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, Layout.of(tab), Map.of(tab, shell("", folder, "")));
        var child = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, Layout.of(document),
            Map.of(document, new WorkspaceFile.TabRecord.Content("notes.md", "dev.example.editor", "document", Map.of("path", "/notes.md"))));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(main, child)));
        windows();
        edt(() -> {
            windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(windows.events).doesNotContain("show child");
        });
    }

    /** A tab already registered under {@code tab}'s id makes a restore that rebuilds it fail, as a bug would. */
    private void taken(TabId tab) {
        windows.model.tabs().add(tab, Tab.Kind.TERMINAL, new dev.jasper.app.contributions.TabContent() {
            @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
            @Override public void dispose() { }
        }, java.util.UUID.randomUUID());
    }

    private WorkspaceFile.Snapshot keptAside() {
        return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(folder.resolve("workspace.toml.bad"))).snapshot();
    }

    @Test void aMainWindowThatCannotBeRestoredOpensANewOneAndTheSavedWindowsAreKept() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        TabId tab = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            Layout.of(tab), Map.of(tab, shell("", folder, "")))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            taken(tab);
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabs()).hasSize(1);
            assertThat(main.content().tabs().getFirst().id()).isNotEqualTo(tab);
            assertThat(windows.events).as("it opens as a new main window, never closed: the last window's closed hook can end Jasper")
                .containsExactly("show main", "opened main");
        });
        assertThat(setAside).as("told, for the notice").containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).as("not lost to the next save").isEqualTo(saved);
        windows.launch();
        assertThat(windows.started).as("the failed tab's shell never started").containsExactly(work);
    }

    @Test void aChildWindowThatCannotBeRestoredIsLeftOutAndTheSavedWindowsAreKept() throws Exception {
        TabId shell = TabId.random(), away = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, Layout.of(shell), Map.of(shell, shell("", folder, ""))),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, Layout.of(away), Map.of(away, shell("", folder, "")))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            taken(away);
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
            assertThat(windows.manager.children()).isEmpty();
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).isEqualTo(saved);
        windows.launch();
    }

    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        windows();
        TabId[] saved = new TabId[2];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            main.content().newTab(work);
            saved[0] = main.content().tabs().get(0).id();
            saved[1] = main.content().tabs().get(1).id();
            windows.manager.openTerminalWindow(work);
            windows.manager.closeAll();
            assertThat(store.held()).as("closing every window saved them first").isTrue();
            MainWindow again = windows.manager.ensureMain("reopen", null).orElseThrow();
            assertThat(again.content().tabs()).extracting(Tab::id).containsExactly(saved[0], saved[1]);
            assertThat(windows.manager.children()).as("its child window too").hasSize(1);
        });
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "WorkspaceRestoreTest.java", "app.workspace", "WorkspaceRestoreTest", T + "WorkspaceRestoreTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 5; windows restored from workspace.toml")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceRestoreTest'`
Expected: FAIL: the main window opens with one new terminal tab (`expected: … but was: …` on the layout), and the child
window is missing.

- [ ] **Step 3: A window restores its workspace**

A restored terminal tab is registered under its saved id, unstarted; every tab is mounted and the saved layout shown in
one change of the terminal registry; whenever the layout changes, each group's selected tab that waits starts:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "TerminalContent.java", [
    ("    void start() { pane.start(); }\n",
     "    void start() { pane.start(); }\n"
     "    /** Starts a restored tab's shell the first time its group shows it (workspace tabs spec 13). */\n"
     "    void startIfWaiting() { if (!closed && pane.waiting()) pane.start(); }\n", 1)])
edit(W + "WorkspaceView.java", [
    ("""    private void place(Tab tab, Layout next) {""",
     """    /**
     * Shows a restored workspace at once (workspace tabs spec 13): {@code saved} places exactly {@code restored}, which no
     * window showed before. Only an empty workspace restores.
     */
    void restore(Layout saved, List<Tab> restored) {
        if (!tabs.isEmpty() || !layout.isEmpty()) throw new IllegalStateException("Only an empty workspace restores");
        var placed = new HashMap<TabId, Tab>();
        for (Tab tab : restored) placed.put(tab.id(), tab);
        if (!placed.keySet().equals(Set.copyOf(saved.tabs()))) throw new IllegalArgumentException("The layout places other tabs");
        tabs.putAll(placed);
        apply(saved);
    }

    private void place(Tab tab, Layout next) {""", 1)])
edit(W + "WindowContent.java", [
    ("import dev.jasper.app.persistence.UiState;\n",
     "import dev.jasper.app.persistence.UiState;\nimport dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("""    void workspaceChanged(boolean refocus) {
        if (closed) return;
        if (terminals != null) terminals.tabSelected();""",
     """    void workspaceChanged(boolean refocus) {
        if (closed) return;
        startShown();
        if (terminals != null) terminals.tabSelected();""", 1),
    ("""    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }
""", """    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }

    /** Starts each restored terminal tab its group shows now; the others wait until first shown (workspace tabs spec 13). */
    private void startShown() {
        for (dev.jasper.app.layout.Group group : workspace.model().groups())
            group.selected().flatMap(workspace::tab).ifPresent(tab -> {
                if (tab.content() instanceof TerminalContent terminal) terminal.startIfWaiting();
            });
    }

    /**
     * Shows a saved workspace in this window, which holds no tab yet (workspace tabs spec 13): each terminal tab with its
     * saved id, renamed title, profile and last local directory, its shell not started until its group shows it. A tab
     * this window cannot rebuild is left out of the layout as {@code Layout.remove} leaves it out.
     *
     * @param record the window as saved
     * @return how many tabs came back
     */
    int restore(WorkspaceFile.WindowRecord record) {
        if (closed || workspace.tabCount() > 0) throw new IllegalStateException("Only an empty open window restores");
        var restored = new ArrayList<Tab>();
        try {
            dev.jasper.app.layout.Layout layout = record.layout();
            for (TabId saved : record.layout().tabs()) {
                if (record.tabs().get(saved) instanceof WorkspaceFile.TabRecord.Terminal terminal) restored.add(restoredTerminal(saved, terminal));
                else layout = layout.remove(saved);
            }
            dev.jasper.app.layout.Layout shown = layout;
            Runnable show = () -> {
                for (Tab tab : restored) mount(tab);
                workspace.restore(shown, restored);
            };
            if (terminals != null) terminals.atomically(show); else show.run();
        } catch (RuntimeException | Error failure) {
            // Nothing half-restored stays: each tab closes, and is announced closed where it was announced.
            for (Tab tab : restored) {
                try { tab.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            throw failure;
        }
        return restored.size();
    }

    /** A saved terminal tab whose shell waits until its tab is first shown (workspace tabs spec 13). */
    private Tab restoredTerminal(TabId tabId, WorkspaceFile.TabRecord.Terminal saved) {
        Path directory = null;
        try { if (!saved.directory().isEmpty()) directory = Path.of(saved.directory()); }
        catch (java.nio.file.InvalidPathException unusable) { /* where a new shell starts */ }
        Optional<String> profile = saved.profile().isEmpty() ? Optional.empty() : Optional.of(saved.profile());
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, null, profile, Optional.empty());
        terminal.pane().restored();
        if (!saved.title().isEmpty()) terminal.rename(saved.title());
        Tab tab;
        try {
            tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        } catch (RuntimeException | Error failure) {
            // Not registered, so no tab closes it: its pane is closed here.
            try { terminal.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            throw failure;
        }
        tab.focusTarget(terminal::focusTerminal);
        return tab;
    }

    /** Wires a restored tab to this window as {@link #attach(Tab)} does, without placing it: the restore places every tab at once. */
    private void mount(Tab tab) {
        WorkspaceTheme.apply(tab.component(), themes.current());
        tabListeners.put(tab, tab.onChanged(this::update));
        if (terminals != null) terminals.tabArrived(tab, Optional.empty());
        tabHost.attach(tab, true);
    }
""", 1)])
PY
```

- [ ] **Step 4: `ensureMain` restores**

The main window opens empty and restores before its frame exists, then each child window the same way; a child window
with nothing that came back is discarded unshown; the main window comes to the front when a child window came back, and
the request's directory opens as a tab after the restored ones:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
M = W + "WindowManager.java"
edit(M, [
    ("import java.util.function.Function;\n", "import java.util.function.Function;\nimport java.util.function.Predicate;\n", 1),
    ("""     * @param bounds where a child window's workspace opens on screen, a torn-out tab's (workspace tabs plan 5); empty
     *     for where the platform puts it""",
     """     * @param bounds where a child window's workspace opens on screen, a torn-out tab's (workspace tabs plan 5); empty
     *     for where the platform puts a new window, or where a restored window's saved place puts it""", 1),
    ("""    private final Contributions model;
    private final Hooks hooks;""", """    private static final System.Logger LOG = System.getLogger(WindowManager.class.getName());
    private final Contributions model;
    private final Hooks hooks;""", 1),
    ("""    /**
     * The main window, opened first when none is open (workspace tabs spec 6.4: every request restores it first). The
     * tabs a plugin asked for while it was being composed open in it once it shows, or go with it when it fails to
     * open, their failures under its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts; null for where a new shell starts""",
     """    /**
     * The main window, restored first when none is open (workspace tabs spec 6.4 and 13: every request restores it first):
     * as the store has it, with its child windows, else a new main window with one terminal tab. The tabs a plugin asked
     * for while it was being composed open in it once it shows, or go with it when it fails to open, their failures under
     * its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts, or, when the main window is restored, a terminal tab it
     *     opens after its own; null for where a new shell starts, or no tab""", 1),
    ("""        try {
            opened = open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(),
                Optional.empty()), content -> main = new MainWindow(this, content));
        } catch (RuntimeException | Error failure) {
            // Still opening: a waiting tab's close listener cannot ask for another main window.
            model.tabs().mainFailed(failure);
            throw failure;
        } finally {
            openingMain = false;
        }
        // A main window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().mainOpened(window.id()));
        return opened;
    }
""", """        try {
            opened = openMain(trigger, directory);
        } catch (RuntimeException | Error failure) {
            // Still opening: a waiting tab's close listener cannot ask for another main window.
            model.tabs().mainFailed(failure);
            throw failure;
        } finally {
            openingMain = false;
        }
        // A main window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().mainOpened(window.id()));
        return opened;
    }

    /**
     * The main window as the store has it (workspace tabs spec 13): the saved main window, then each saved child window,
     * each where it was with its layout and tabs, the main window in front; with nothing to restore, or a main window that
     * cannot be restored, a new main window with one terminal tab in {@code directory}. A restored main window opens
     * {@code directory} as a tab after its own. A window that cannot be restored is not lost: the store keeps the saved
     * windows as {@code workspace.toml.bad} and says so (spec 15).
     */
    private Optional<MainWindow> openMain(String trigger, Path directory) {
        WorkspaceFile.Loaded loaded = store == null ? new WorkspaceFile.Loaded.Fresh(Optional.empty()) : store.load();
        if (!(loaded instanceof WorkspaceFile.Loaded.Restore restore)) return openNewMain(trigger, directory);
        WorkspaceFile.Snapshot saved = restore.snapshot();
        boolean[] restored = new boolean[1];
        Optional<MainWindow> opened = open(new Opening(WindowRole.MAIN, true, null, trigger, () -> { }, Optional.empty(),
            Optional.empty()), content -> main = new MainWindow(this, content), window -> {
                try {
                    window.restore(saved.main());
                    restored[0] = true;
                } catch (RuntimeException failure) {
                    // It opens as a new main window instead, never discarded: the closed hook of the last window ends a
                    // Jasper that is not resident. Its restore closed what it made; the saved windows are kept before this
                    // window's first save replaces them.
                    LOG.log(System.Logger.Level.WARNING, "Could not restore the main window; it opens as a new one", failure);
                    store.setAside(saved);
                    window.content().newTab(directory);
                }
                return true;
            });
        if (opened.isEmpty() || !restored[0]) return opened;
        boolean whole = true;
        for (WorkspaceFile.WindowRecord child : saved.children()) {
            try {
                restoreChild(child);
            } catch (RuntimeException failure) {
                // A child window that cannot come back costs only itself, and the saved windows are kept.
                LOG.log(System.Logger.Level.WARNING, "Could not restore a child window", failure);
                whole = false;
            }
        }
        if (!whole) store.setAside(saved);
        if (directory != null) opened.get().content().newTab(directory);
        if (!saved.children().isEmpty()) opened.get().toFront();
        return opened;
    }

    private Optional<MainWindow> openNewMain(String trigger, Path directory) {
        return open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(), Optional.empty()),
            content -> main = new MainWindow(this, content));
    }

    /**
     * A saved child window, where it was with its layout and tabs; one with no tab that can come back is left out. Its
     * timing line says {@code restore}, which no benchmark reads as a menu's window (workspace plan 6a Rulings 16).
     */
    private void restoreChild(WorkspaceFile.WindowRecord record) {
        ChildWindow[] made = new ChildWindow[1];
        // Its saved place reaches its frame through its placement, checked against today's displays, never as a tear-out's
        // workspace bounds, which its frame would grow by its chrome.
        open(new Opening(WindowRole.CHILD, true, null, "restore", () -> { if (made[0] != null) made[0].emptied(); }, Optional.empty(),
            Optional.empty()), content -> {
            made[0] = new ChildWindow(this, content);
            children.add(made[0]);
            return made[0];
        }, window -> window.restore(record) > 0);
    }
""", 1),
    ("""    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make) {""",
     """    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make) {
        return open(opening, make, window -> true);
    }

    /**
     * As {@link #open(Opening, Function)}; {@code prepare} readies the window before its frame exists, as a restore does,
     * and a window it refuses is discarded unshown.
     */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make, Predicate<? super W> prepare) {""", 1),
    ("""            content.onLayoutChanged = this::changed;
            window.shell(hooks.frames().apply(window));""", """            content.onLayoutChanged = this::changed;
            if (!prepare.test(window)) {
                window.discard();
                return Optional.empty();
            }
            window.shell(hooks.frames().apply(window));""", 1)])
edit(W + "JasperWindow.java", [
    ("import dev.jasper.app.persistence.UiState;\n", "import dev.jasper.app.persistence.UiState;\nimport dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("""    /** The frame the manager built for this window. */""",
     """    /**
     * Restores the window as it was saved (workspace tabs spec 13): where it was, and its workspace's layout and tabs.
     * Before its frame exists.
     *
     * @return how many tabs came back
     */
    final int restore(WorkspaceFile.WindowRecord record) {
        placement = new Placement(record.bounds(), record.extendedState());
        return content.restore(record);
    }

    /** The frame the manager built for this window. */""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: restore the windows and their terminal tabs

Opening the main window restores it as saved, then each child window,
with their places, splits, groups, tab order, selection and active
group. A terminal tab keeps its id, name, profile and directory and
starts its shell once its group shows it; the request's directory opens
as a tab after the restored ones.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The application saves and restores

Spec 13 and 15 through the application: the store lives in the Jasper home from `startPlugins` on, Quit and going to
the background save every window, shutdown waits for the last write, an unreadable file is set aside with a notice, the
warm-up rehearsal never touches it, and a main window restored with no terminal starting still marks the first paint
(Rulings 13, 17, 18 and 19).

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWorkspaceTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, `workspace/WindowContent.java`,
  `docs/design/theme-engine/migration.csv` (one row)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java` (one case)

**Interfaces:**
- Consumes: `WorkspaceStore` (Task 4), `WindowManager.workspace`, `snapshot` (Tasks 3 and 4), `AppDirs.workspaceState()`
  (plan 2).
- Produces: package `void JasperApplication.workspace(Path file)` and `WorkspaceStore JasperApplication.workspaceStore()`;
  `public boolean WindowContent.showsNoTerminal()`, `public void WindowContent.afterNextPaint(Runnable task)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWorkspaceTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.MainWindow;
import dev.jasper.app.workspace.WindowShell;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 13 through the application, with stand-in frames: the windows are saved when they close, on Quit and
 * when Jasper goes to the background, and restored at start and whenever the main window opens again.
 */
class JasperApplicationWorkspaceTest {
    @TempDir Path home;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    private Path file() { return home.resolve("workspace.toml"); }

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, terminate);
            held[0].frames = window -> new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
                component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            held[0].workspace(file());
        });
        return held[0];
    }

    private WorkspaceFile.Snapshot saved() { return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    @Test void quitSavesEveryWindowAndTheNextStartRestoresThem() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication first = application(terminated::countDown);
        edt(() -> {
            MainWindow main = first.openFirstWindow(List.of()).orElseThrow();
            main.content().newTab(home);
            first.windows().openTerminalWindow(home);
            first.quitNow();
        });
        launch();
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("shutdown waits for the save").isTrue();
        assertThat(saved().main().layout().tabs()).hasSize(2);
        assertThat(saved().children()).hasSize(1);
        JasperApplication second = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = second.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().terminalTabs()).hasSize(2);
                assertThat(second.windows().children()).as("its child window too").hasSize(1);
            });
            launch();
        } finally {
            edt(second::quitNow);
        }
    }

    @Test void goingToTheBackgroundSavesAndReopeningRestoresTheSameWindows() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                MainWindow main = application.openFirstWindow(List.of()).orElseThrow();
                main.content().newTab(home);
                application.windows().openTerminalWindow(home);
                application.toBackground();
                assertThat(application.windows().isEmpty()).isTrue();
                application.open(List.of(), "reopen");
                assertThat(application.windows().main().orElseThrow().content().terminalTabs()).hasSize(2);
                assertThat(application.windows().children()).hasSize(1);
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().children()).hasSize(1);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aChildWindowTheUserClosesIsSavedWithoutIt() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.openFirstWindow(List.of()).orElseThrow();
                application.windows().openTerminalWindow(home).orElseThrow().requestClose();
            });
            launch();
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
            assertThat(saved().children()).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aLaunchsPathsOpenAfterTheRestore() throws Exception {
        Path first = Files.createDirectories(home.resolve("first")), second = Files.createDirectories(home.resolve("second"));
        JasperApplication saving = application(() -> { });
        edt(() -> {
            saving.openFirstWindow(List.of()).orElseThrow();
            saving.quitNow();
        });
        launch();
        saving.workspaceStore().written().get(5, TimeUnit.SECONDS);
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = application.openFirstWindow(List.of(first, second)).orElseThrow();
                var tabs = main.content().terminalTabs();
                assertThat(tabs).as("the restored tab, then one per directory").hasSize(3);
                assertThat(tabs.get(1).pane().directory()).isEqualTo(first);
                assertThat(tabs.get(2).pane().directory()).isEqualTo(second);
            });
            launch();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void anUnreadableWorkspaceStartsWithOneTabAndSaysWhereItWasKept() throws Exception {
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = application.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().terminalTabs()).hasSize(1);
                assertThat(application.notificationCenter().history()).anySatisfy(notice -> {
                    assertThat(notice.request().title()).isEqualTo("Jasper could not restore your windows");
                    assertThat(notice.request().content()).contains(home.resolve("workspace.toml.bad").toString());
                });
            });
            launch();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        byte[] before = Files.readAllBytes(file());
        JasperApplication application = application(() -> { });
        try {
            CountDownLatch done = new CountDownLatch(1);
            edt(() -> application.warmUp(done::countDown));
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> assertThat(application.windows().isEmpty()).isTrue());
            assertThat(Files.readAllBytes(file())).as("not read: an unreadable file would have been set aside").isEqualTo(before);
            assertThat(home.resolve("workspace.toml.bad")).doesNotExist();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aMainWindowRestoredWithoutATerminalStillMarksTheFirstPaint() throws Exception {
        JasperApplication saving = application(() -> { });
        edt(() -> {
            MainWindow main = saving.openFirstWindow(List.of()).orElseThrow();
            main.content().closeTab(main.content().currentTerminal());
            saving.quitNow();
        });
        launch();
        saving.workspaceStore().written().get(5, TimeUnit.SECONDS);
        assertThat(saved().main().layout().isEmpty()).isTrue();
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.trace(trace);
                MainWindow main = application.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().showsNoTerminal()).isTrue();
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).isEmpty();
                main.content().setSize(200, 100);
                main.content().paint(new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics());
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).as("the empty workspace's paint").isPresent();
            });
        } finally {
            edt(application::quitNow);
        }
    }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/application/"
ledger.add(T + "JasperApplicationWorkspaceTest.java", "app.application", "JasperApplicationWorkspaceTest", T + "JasperApplicationWorkspaceTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 6; saving and restoring through the application")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `workspace(Path)`, `workspaceStore()`, `showsNoTerminal()`.

- [ ] **Step 3: The first paint of a window with no terminal starting**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(W, [("    public boolean isActiveAndOpen() { return active && !closed; }\n",
"""    public boolean isActiveAndOpen() { return active && !closed; }

    private final java.util.List<Runnable> afterPaint = new ArrayList<>();

    /**
     * Whether no terminal tab shows in this window now, so none paints the window's first output (workspace tabs spec 13):
     * every group that shows (the maximized group alone while one is maximized) has a plugin's tab selected, or the
     * workspace is empty.
     *
     * @return true when no shown group's selected tab is a terminal tab
     */
    public boolean showsNoTerminal() {
        dev.jasper.app.layout.Layout layout = workspace.model();
        java.util.List<dev.jasper.app.layout.Group> shown = layout.maximized().flatMap(layout::group).map(java.util.List::of)
            .orElseGet(layout::groups);
        for (dev.jasper.app.layout.Group group : shown)
            if (group.selected().flatMap(workspace::tab).filter(tab -> tab.content() instanceof TerminalContent).isPresent()) return false;
        return true;
    }

    /**
     * Runs {@code task} once, after this window's content next paints. EDT.
     *
     * @param task what runs after the paint
     */
    public void afterNextPaint(Runnable task) {
        afterPaint.add(Objects.requireNonNull(task, "task"));
        repaint();
    }

    @Override public void paint(Graphics graphics) {
        super.paint(graphics);
        if (afterPaint.isEmpty()) return;
        var tasks = java.util.List.copyOf(afterPaint);
        afterPaint.clear();
        for (Runnable task : tasks) task.run();
    }
""", 1)])
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [("""        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
    }
""", """        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
        // A main window that shows no terminal paints none at once: its own first paint is the start-up's, which proves
        // the look and settles residency (workspace plan 6a Rulings 17).
        if (window.isMain() && window.content().showsNoTerminal()) window.content().afterNextPaint(() -> {
            if (windowTrace != null) windowTrace.mark(WindowTrace.Milestone.FIRST_PAINT);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        });
    }
""", 1)])
# The maximized case: a terminal that started but that a maximize hides paints nothing.
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java", [(
    """    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {""",
    """    /** Workspace plan 6a Rulings 17: only the groups that show count, the maximized one alone while one is maximized. */
    @Test void aWindowShowsNoTerminalWhenAMaximizedGroupHidesTheOneThatStarted() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            assertThat(main.content().showsNoTerminal()).isFalse();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.md", main.id(), new javax.swing.JPanel(),
                dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
            main.content().selectTab(notes);
            assertThat(main.content().showsNoTerminal()).as("the terminal's group still shows").isFalse();
            main.content().toggleMaximize();
            assertThat(main.content().showsNoTerminal()).as("only the maximized group shows").isTrue();
        });
        windows.launch();
    }

    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {""", 1)])
PY
```

- [ ] **Step 4: The store in the application**

`startPlugins` gives the window manager a store over `<home>/workspace.toml`: saves scheduled on the EDT's timer and
written on a virtual thread, a set-aside file told in a notice; shutdown waits for the writes; `open` and
`openFirstWindow` say what they now do:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [
    ("import dev.jasper.app.workspace.WorkspaceActivity;\n",
     "import dev.jasper.app.workspace.WorkspaceActivity;\nimport dev.jasper.app.workspace.WorkspaceStore;\n", 1),
    ("    private UiState uiState = UiState.inMemory();\n",
     "    private UiState uiState = UiState.inMemory();\n"
     "    /** Where the windows are saved and restored from; null before {@link #startPlugins(AppDirs)}, and then nothing is. */\n"
     "    private WorkspaceStore workspaceStore;\n", 1),
    ("""     * Every outside request to show Jasper ends here: a second launch's handoff, the {@code jasper} command, the macOS
     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3, 5.4 and 6.4). The main window is restored
     * first when none is open (until workspace-tabs plan 6, a new main window opens, in the first directory the request
     * names when there is one), else raised; each other directory opens as a terminal tab in its active group and each
     * file in the editor a plugin provides, else revealed ({@code openRequestedFile}) ({@code OpenPlan}). {@code trigger}
     * names the caller for start-up timing. EDT.""",
     """     * Every outside request to show Jasper ends here: a second launch's handoff, the {@code jasper} command, the macOS
     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3, 5.4, 6.4 and 13). The main window is
     * restored first when none is open, with its child windows as {@code workspace.toml} has them, and the first directory
     * the request names opens as a terminal tab after the restored ones (with nothing to restore, a new main window opens
     * there); an open main window is raised. Each other directory opens as a terminal tab in its active group and each
     * file in the editor a plugin provides, else revealed ({@code openRequestedFile}) ({@code OpenPlan}). {@code trigger}
     * names the caller for start-up timing. EDT.""", 1),
    ("""     * A launch's main window, timed as a cold start unless a {@code --background} launch warmed up: its tab starts in the
     * first directory {@code paths} names, else where a new shell starts, and the rest of the paths open in it as
     * {@link #open} opens them (plan Ruling 13). EDT.""",
     """     * A launch's main window, timed as a cold start unless a {@code --background} launch warmed up: restored with its child
     * windows as {@code workspace.toml} has them (workspace tabs spec 13), the first directory {@code paths} names opening
     * as a terminal tab after the restored ones; with nothing to restore, a new main window whose tab starts in that
     * directory, else where a new shell starts. The rest of the paths open in it as {@link #open} opens them (plan Ruling
     * 13). EDT.""", 1),
    ("""        uiState = UiState.load(dirs.uiState(), presentation().projections().dockLayout().key());
""", """        uiState = UiState.load(dirs.uiState(), presentation().projections().dockLayout().key());
        workspace(dirs.workspaceState());
""", 1),
    ("""    /** Tests replace the process-wide trace. EDT. */""",
     """    /**
     * Saves the windows to {@code file} and restores them from it (workspace tabs spec 13): within a second of each change
     * and whenever a window closes, written on a virtual thread; read when the main window opens. Saved windows that do not
     * all come back are kept aside and a notice names where. The warm-up rehearsal never reaches it: it bypasses the window
     * manager.
     * Before the first window. EDT.
     */
    void workspace(Path file) {
        workspaceStore = new WorkspaceStore(file, windows::snapshot, JasperApplication::afterDelay,
            task -> Thread.ofVirtual().name("jasper-workspace-save").start(task), this::workspaceSetAside);
        windows.workspace(workspaceStore);
    }

    /** The store {@link #workspace(Path)} made; null before it. Tests. */
    WorkspaceStore workspaceStore() { return workspaceStore; }

    /**
     * Saved windows did not all come back (spec 15): {@code workspace.toml} could not be read, or a window in it could not
     * be restored; the saved windows were kept as {@code kept}.
     */
    private void workspaceSetAside(Path kept) {
        notificationCenter.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.WARNING, "Jasper could not restore your windows")
            .withContent("Jasper opened what it could and kept the saved windows in " + kept + "."));
    }

    /** Tests replace the process-wide trace. EDT. */""", 1),
    ("""        pending.add(history.closedFuture());
""", """        pending.add(history.closedFuture());
        // The last save (closing every window saved them) reaches the disk before the process ends.
        if (workspaceStore != null) pending.add(workspaceStore.written());
""", 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: the application saves and restores its windows

The store keeps workspace.toml in the Jasper home from the moment the
plugins start; Quit, going to the background and closing the main
window save every window, shutdown waits for the write, and a file that
cannot be read is set aside with a notice. A main window restored with
no terminal starting marks the first paint itself.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/plugin-authoring.md`,
  `jasper-sdk/README.md`, `docs/STATUS.md` (the entry at the top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentences true (AGENTS.md "Documentation edits"): `ui-state.toml` no longer
keeps the main window's bounds and `workspace.toml` keeps the windows; the architecture names the store; the rehearsal
never reaches `workspace.toml`; plugin authors learn that a restored tab may not have started; the SDK README's version
line points at the section it gains:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
edit("docs/configuration.md", [(
    """launch. `ui-state.toml` beside `config.toml` remembers each panel's slot and place on its stripe, the
dock's sizes and split ratios (each look apart: the Enterprise look keeps its own), which panels are open, stripe visibility, main-window size/position and maximized
state, and the bounds of Settings, About and Licenses. The last main window closed supplies the layout for the next launch, including
the terminal area left between its panels; a resident Jasper (see [Background residency](#background-residency))
uses it for its next window too, also after Quit sent it to the background, which closes every window as a real
Quit does. Normal bounds are retained while maximized or minimized; relaunch
never starts minimized. If a saved window no longer fits an available display, its bounds are fitted to a usable
display. This file is written by Jasper and is not meant to be edited. Terminal sessions and editor documents
are not recreated by this layout state.""",
    """launch. `ui-state.toml` beside `config.toml` remembers each panel's slot and place on its stripe, the
dock's sizes and split ratios (each look apart: the Enterprise look keeps its own), which panels are open, stripe visibility,
and the bounds of Settings, About and Licenses. The last main window closed supplies the layout for the next launch, including
the terminal area left between its panels; a resident Jasper (see [Background residency](#background-residency))
uses it for its next window too, also after Quit sent it to the background, which closes every window as a real
Quit does. This file is written by Jasper and is not meant to be edited.

`workspace.toml` beside it keeps Jasper's windows: the main window and each child window with its size, position and
maximized state, its splits and tab groups, each group's tabs in order with the one it shows, and the active group.
Jasper saves it within a second of each change, when a window closes, on Quit and when it goes to the background, and
restores it at start and whenever the main window opens again. A window keeps its normal bounds while maximized or
minimized and never comes back minimized; one that no longer fits an available display is fitted to a usable display.
A terminal tab comes back with its name and profile and a new shell in its last local directory, or where a new shell
starts when that directory was on another host or is gone; only the tab each group shows starts its shell at once, the
others when first shown. Scrollback and running programs do not come back. A file Jasper cannot read is kept as
`workspace.toml.bad`, and Jasper starts with one terminal tab and says so. This file is written by Jasper and is not
meant to be edited.""", 1)])
edit("docs/app-architecture.md", [
    ("""restore the main window first; closing the main window, Quit and going to the background close the child windows, then
the main window. `MainWindow`'s constructor has one call site, `WindowManager.ensureMain`, which
`verifyApplicationArchitecture` checks.
""", """restore the main window first; closing the main window, Quit and going to the background close the child windows, then
the main window. `MainWindow`'s constructor has one call site, `WindowManager.ensureMain`, which
`verifyApplicationArchitecture` checks. `WorkspaceStore` (workspace) keeps the windows in `workspace.toml`
(`persistence.WorkspaceFile`): `WindowManager.snapshot()` builds what is saved, within a second of each change (a
non-sliding timer on the EDT, the write on a virtual thread) and at once when a window closes; closing every window
saves first and holds saving until the windows come back. `ensureMain` restores the main window, then its child
windows, each with its place, layout and tabs, before it shows; a restored terminal tab's shell starts when its group
first shows it, and until then its pane reports `NOT_STARTED`.
""", 1),
    ("their workspaces of tab groups (`WorkspaceView`, `GroupView`, `SplitContainer`), terminal tabs and panes,",
     "their workspaces of tab groups (`WorkspaceView`, `GroupView`, `SplitContainer`), the saved workspace (`WorkspaceStore`), terminal tabs and panes,", 1)])
edit("docs/plugin-authoring.md", [(
    """- `PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title.
""", """- `PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title.
- **A restored tab may not have started (SDK 0.21.0).** Jasper restores its windows at start and whenever the main
  window opens again; a terminal tab its group does not show reports `SessionState.NOT_STARTED` until it is first
  shown: nothing runs there yet, and text sent to it is dropped. In the testkit `FakePluginHost.holdPane` holds a pane
  the same way until its tab is shown.
""", 1)])
R = "jasper-sdk/README.md"
edit(R, [
    ("(0.21.0 is a breaking boundary for saved workspaces; 0.20.0 is one too, described under Main and child windows\nbelow;",
     "(0.21.0 is a breaking boundary, described under Saved workspaces below; 0.20.0 is one too, described under Main and\nchild windows below;", 1),
    ("""## Main and child windows (SDK 0.20.0)
""", """## Saved workspaces (SDK 0.21.0)

Jasper saves its windows and restores them at start and whenever the main window opens again: the main window and its
child windows, their tab groups and their tabs. A restored terminal tab that its group does not show has not started:
its pane's `info().state()` is `SessionState.NOT_STARTED` until the tab is first shown, and text sent to it is dropped;
then its shell starts as a new tab's does.

## Main and child windows (SDK 0.20.0)
""", 1)])
edit("docs/app-maintenance.md", [(
    """discarded rather than closed (`WindowFrame.discard`; a window that failed to open is discarded by `WindowManager`), so its default
size and layout never become the last window closed, which the next window restores.""",
    """discarded rather than closed (`WindowFrame.discard`; a window that failed to open is discarded by `WindowManager`), so its default
layout never becomes the last window closed's, which the next window restores; it bypasses `WindowManager`, so it never
reads or writes `workspace.toml`.""", 1)])
PY
```

Then check that no guide still describes the main window's bounds in `ui-state.toml`:

```bash
git grep -n -i -E "main_window|main-window size|restoreLayout|[^a-z]mainWindow\(\)" -- README.md jasper-sdk/README.md docs ':!docs/superpowers' ':!docs/STATUS.md' ':!docs/design' || echo "none left"
```

Expected: `none left`.

- [ ] **Step 2: Source hygiene**

Run the AGENTS.md character check over the files this plan touched:

```bash
python3 -I - $(git diff --name-only --diff-filter=d HEAD~6 -- '*.java') <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 5's entry),
in the style of the earlier entries:

```markdown
**Workspace tabs, plan 6a: saving and restoring the workspace — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-6a-save-restore.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). Jasper saves its windows to
`<home>/workspace.toml` (`workspace.WorkspaceStore` over plan 2's `WorkspaceFile`): one second after the first change
(a timer that does not slide), at once when a window closes, and before closing every window (closing the main window,
Quit, going to the background), after which saving waits until the windows come back; writes run in order on virtual
threads, each after the one before however it ended, and shutdown waits for them; a snapshot that fails costs only that
save, and never a close or Quit. `WindowManager.snapshot()` keeps each window's `JasperWindow.Placement` (its
frame reports it), its `Layout` and a `TabRecord.Terminal` per local terminal tab (rename, last local directory,
profile); other tabs are taken out with `Layout.remove`, and a child window left with none is left out.
`WindowManager.ensureMain` restores: the main window, then its child windows, each before its frame exists, the main
window in front; a request's directory opens as a tab after the restored ones; with nothing saved, an unreadable file
or a main window that cannot be restored, a new main window opens. Saved windows that do not all come back (an
unreadable file, a window that cannot be restored) are kept as `workspace.toml.bad` (`WorkspaceStore.setAside`), with
the notice "Jasper could not restore your windows". A main window that shows no terminal (a maximized group counts
alone) marks the first paint with its own. A restored terminal tab waits (`TerminalPane.restored()`) until its group
shows it and reports `NOT_STARTED`, in the testkit's fake too (`FakePluginHost.holdPane`, checked on both runtimes); a directory that is gone starts the shell where a new shell starts. `UiState`'s
`[main_window]` is gone (no migration: the first start after this plan opens the main window where a new window goes).
The SDK is 0.21.0, a breaking boundary: `SessionState.NOT_STARTED`; every bundled manifest, the external example and
every guide range declare `sdk = ">=0.21.0, <0.22"`. Rulings (plan, "Rulings"): <one clause each, 1 to 23>. Deviations:
<none, or each with its reason>. Left for plan 6b: plugins' tabs (restore state, restorers, placeholders), which add to
SDK 0.21.0, restored with their saved ids straight into their windows, never through `TabRegistry.open` (a random id,
and a placement lost while the main window is composed), and never saving Jasper's own tabs (`TabRegistry.APP_OWNER`);
`ContentTab.window()` names a never-open window for a tab that waits for the main window, which now waits through every
child window's restore; from plans 4b and 5, `AppContractTest`'s `emptyWindow` and panel routing and
`FakeWorkspace.closeWindow` closing and nulling `Panel.instance`, before the first contract case that needs them; THD
Sourcing Crypto is rebuilt after 6b, against 0.21.0. `./gradlew check --continue`: **<counts>**. GUI
checks for the user: quit with a split main window and a child window, start again: both come back where they were, with
their groups, tab order, selection and names, and only the shown tabs' shells start; a background tab starts when
clicked; a renamed tab keeps its name; maximize a window, quit, start: it comes back maximized; unplug a display between
runs: the window lands on the remaining one; close a child window, quit, start: it stays closed; corrupt
`workspace.toml`, start: one tab and the notice, the file kept as `workspace.toml.bad`; with residency, close the main
window and reopen from the Dock: the same windows. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md jasper-sdk/README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for saving the workspace

The configuration guide describes workspace.toml and what ui-state.toml
keeps now, the architecture names the store, the plugin guides say a
restored tab may not have started; STATUS records plan 6a with its
rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 6.4's "every request restores it first" (Task 5, through `ensureMain`; Task 6, through the
  application's requests); 8's file (Tasks 3 to 5, over plan 2's codec); 13 line by line: saved within one second on a
  virtual thread and when any window closes, on Quit and when going to the background (Tasks 4 and 6), restored at start
  and on reopen with bounds checked against today's displays, splits, groups, order, selection and active group (Tasks 2
  and 5), terminal tabs' title, profile and last local directory with a remote directory falling back (Tasks 3 and 1),
  each group's selected tab starting at once and the others reported not started until shown (Tasks 1 and 5), a program
  coming back as a plain shell and no scrollback (Task 3), the rehearsal never reading or writing the file (Task 6);
  14's state and version (Task 1); 15's display and unreadable-file rows, the set-aside extended to windows that cannot
  be restored (Tasks 2, 4, 5 and 6); 16's `[main_window]` (Task 2); 17's "restore lazily starts unselected terminals"
  (Task 5) and SDK line (Task 1's contract test on both runtimes). Out of scope here: 13's content tabs and placeholders, 14's
  `ContentSpec` restore state and `Windows.restorer`, and 15's disabled-plugin row, all plan 6b's.
- Every quoted anchor was matched, at its stated count, in a replay of Tasks 1-7 in order on `5b1ad0093` (the
  pre-flight); nothing was compiled. The ledger rows were checked with a Python copy of `ThemeMigrationCoverageTest`'s
  pattern, and the documents' links with a Python copy of `AppDocumentationTest`'s rules.
- Type consistency: `JasperWindow.Placement`, `WindowFrame.place`, `WindowManager.snapshot`/`workspace`/`changed`,
  `WorkspaceStore` (`DELAY`, `load`, `changed`, `save`, `hold`, `held`, `written`), `WindowContent.restore`/
  `onLayoutChanged`/`layoutChanged`/`showsNoTerminal`/`afterNextPaint`, `TerminalContent.record`/`onRenamed`/
  `startIfWaiting`, `TerminalPane.restored`/`waiting`, the seven-argument `ShellLauncher.launch`, `TestWindows`,
  `FakePluginHost.holdPane`, `ContractHarness.holdTerminalPane`, `WorkspaceStore.setAside(Snapshot)` and
  `JasperApplication.workspace`/`workspaceStore` keep one name and one signature from the task that creates them to the
  last task that uses them.
