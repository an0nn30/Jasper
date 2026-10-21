# Workspace tabs 6a: saving and restoring the workspace Implementation Plan

**Status: Tasks 1-5 executed (`30cc4ca35..3f273675e`); Tasks 6-10 re-planned in place on 2026-10-21 on the
tabs-and-panes model (plans 8a-8c); see Workspace.** Tasks 1-5 ran on the parent-tabs model, which had pane windows;
their text below is history and is not run again. The pre-flight rulings F1-F21 of the earlier versions stand except
where "Superseded" says otherwise. Record every deviation from the text of Tasks 6-10 here and in `docs/STATUS.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jasper saves its windows to `<home>/workspace.toml` within a second of every change and whenever a window
closes, Jasper quits or goes to the background, and restores them whenever the main window opens: at start, and every
time something needs the main window after it closed. The main window and each full window come back where they were,
with their tabs in strip order and the one each showed, each tab with its split layout of panes and its active pane,
and each pane with its one item; terminals come back with their renamed title, profile and last local directory, and
only the terminals of the tab each window shows start at once, the others when their tab first shows. The main window
saved as its empty workspace comes back empty. SDK 0.21.0 reports a restored terminal not yet shown as `NOT_STARTED`.
Plugins' items come back in plan 6b.

**Architecture:** Tasks 1-5 (executed) gave SDK 0.21.0 and a terminal that waits to be shown
(`TerminalPane.restored()`, `NOT_STARTED`, the testkit's `FakePluginHost.holdPane`), every window's place
(`JasperWindow.Placement`, `WindowPlacement` with plan 5's keep-on-screen helpers), plan 2's codec on a window's tabs
(`WorkspaceFile.WindowRecord` over `ParentTabs`, `WorkspaceFile.keeping`), the snapshot (`WindowManager.snapshot`) and
the store (`workspace.WorkspaceStore`: a one-second save timer that does not slide, serialized writes on virtual
threads, a hold while every window closes, and the last saved snapshot for a restore within the same process); plan 8a
then took pane windows out of all of them. Task 6 is the store's review fix round. Task 7 writes the file in the spec's
words: each window's tabs in strip order with the one it shows, each tab's nodes with its panes, each pane naming its
one item, and the window's items. Task 8 restores in `WindowManager.ensureMain`, the one opening point: the main window
opens empty, `WindowContent.restore` registers each saved terminal under its saved id, unstarted, and
`ParentTabsView.restore` shows the saved tabs (one `WorkspaceView.restoreSaved` each) before the frame exists, then each
full window the same way; whenever a window's tabs or layout change, the terminals of the tab it shows start at the end
of that EDT turn. Task 9 gives the application a store over the Jasper home, a notice for saved windows set aside and a
wait for the last write at shutdown, and a main window that shows no terminal marks the first paint itself.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md`](../specs/2026-10-20-jasper-tabs-and-panes-design.md)
(decisions 2.1 and 2.2, sections 3's terms, 4, 12, 13's "a new terminal from outside" row and 16), the binding
authority, which amends and wins over [`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md)
(sections 4 and 9) and [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1 decision 5, 6.4, 8, 13, 14's state and version, 15's display and unreadable-file rows, 16's
`[main_window]`, 17 and 18). Where the tabs-and-panes spec is silent, the workspace tabs spec's section 13 governs, and
where both are, the industry standard: VS Code, iTerm2 and IntelliJ restore every window with its tabs and splits and
start nothing a person has not looked at. The first half of section 18's plan 6;
[`2026-10-18-workspace-6b-plugin-restorers.md`](2026-10-18-workspace-6b-plugin-restorers.md) (the SDK's restore state and
restorers, placeholders, and the Editor's, Remote's and the sample's items) follows it and is re-planned with it.
Plans [8a](2026-10-20-workspace-8a-tabs-hold-panes.md), [8b](2026-10-20-workspace-8b-split-with.md) and
[8c](2026-10-20-workspace-8c-moving-panes.md) execute first.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 8c's final-review fix wave. Run every command from the worktree root. Tasks 1-5 are
history: their steps are checked, and their blocks quote code as it stood before them, so a replay or an execution
starts at Task 6. Every anchor of Tasks 6-10 was matched at its stated count, and every file creation and edit script
of Tasks 6-10 replayed in order with no failure, on a `git archive` copy of `81f80c1af` ("Tabs and panes: a window
losing the focus ends Esc's swallow"), after plan 8c's final-review fixes; plan 6b's Tasks 1-6 replayed after them.
Earlier rounds replayed on `a7833dbda` and `a0cce53dc`, with the same result; this round replayed after the plan
review's fixes. On that copy JBR 25's `javac` compiled the main and test sources of `jasper-sdk`, `jasper-ui`,
`jasper-buddy`, `jasper-terminal` and `jasper-app`, with the testkit and the test fixtures they use, and the bundled
plugins; `-Xlint:all` warned of nothing in the files this plan touches but a dangling comment `JasperApplication` already
had, and doclint at the Javadoc task's level found no new error. A plain JUnit launcher then ran, headless
(`-Djava.awt.headless=true`, no Gradle, no window), every test class of the SDK and the testkit (308, all passing), of the
application's `workspace`, `persistence`, `application`, `plugins`, `contributions`, `layout`, `terminals`, `appearance`
and `documentation` packages (2,338: 2,256 passing, 12 skipped and 82 failing, the very 82 that fail on `81f80c1af`
itself under that launcher, of 2,314 there; Verified facts 11) and of the Editor, Remote, sample, Vault, Enterprise and
Project Index plugins (all passing, one Vault test aborted as on `81f80c1af`); among them `WorkspaceStoreTest` (14),
`WorkspaceFileTest` (6), `WorkspaceFileReadTest` (31), `WorkspaceRestoreTest` (13) and `JasperApplicationWorkspaceTest`
(7). The plain launcher's 82 failures could hide a failure there, so the whole application suite also ran with the
bundled plugins staged as Gradle's test task stages them (each plugin's jar built from its compiled classes and
resources, with its third-party jars; `-Djasper.stagedPlugins` and `-Djasper.plugins.bundled`): 4,144 tests after Tasks
6-10, 4,095 passing, 17 skipped, 31 failing and 18 aborted, against 4,120 on `81f80c1af` (4,072 passing, 30 failing, 18
aborted). No test fails after this plan that does not fail on `81f80c1af` but two of its staged transfer tests
(`StagedRemoteSftpTest.measuresChangedTransfersWithTwoHundredListedFilesAtFiveHertz`,
`StagedTransferLifecycleTest.hostShutdownDrainsOwnedLoopsAndReleasesDurableQueue`), which fail only under load: three
such runs shared the machine, and run alone they passed twice in each state, as did the two `SessionLaunchCoordinatorTest`
cases that failed under load on `81f80c1af`. Line numbers are `81f80c1af`'s. Every edit quotes the text it replaces, and the scripts stop with a
message when that text is not found the stated number of times. Then re-find the quoted text (a change since may have
reworded it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later
tasks anchor on text earlier tasks of this plan wrote, and quote it as they leave it.

**Hand-offs absorbed** (`.superpowers/sdd/workspace-tabs-series.md` and this plan's ledger
`.superpowers/sdd/2026-10-18-workspace-6a-save-restore/progress.md`):
- **Task 5's review** (the ledger's last entries): its Important finding (a write the executor refuses is dropped
  without a log), its Minors (`save()` cancels the scheduled save before it knows it can save; an `Error` from the
  snapshot escapes; the tests' log noise; `layoutChanged` before the terminal registry, a `finally` preferred; wording)
  and the report's concern that `TestWindows` says "EDT only" are Task 6 (Rulings 33 to 36). Its notes for the next
  tasks hold: the store is read only after `ensureMain`'s own checks (Task 8's `openMain`, Rulings 38), and the
  application's tests wait for `written()` after the hold Quit makes (Task 9's `quit` helper).
- **The earlier tasks' Minors the ledger gave 6a's final fix wave** stay with that wave, which follows Task 10: Task 1's
  re-review (the fake keeps only the grid while a pane is held and starts from the current snapshot; one assertion for
  the fixture's refused provided session); Task 2's review (the dead `placement = null` in `WindowFrame.discard`, the
  `@param discard` wording of `WindowShell`); Task 4's review (idle `launch()` calls, Javadoc). Task 1's re-review item 3
  (`ContentTab`'s "a new pane window") is gone: plan 8a rewrote that Javadoc (Rulings 40).
- **Plan 8a's 6a impact and Rulings 2**: 8a took pane windows out of `WorkspaceFile`, `WindowRecord`, `keeping` and the
  snapshot, and made a group hold one tab, a saved group listing two a bad entry; it left "the file's shape" to this
  re-plan. Task 7 writes it (Rulings 32).
- **Plan 8c's 6a impact**: a pane move is a layout change and saves as one; the file does not change for it.
- **Plans 7a's and 7b's SDK Javadoc rewording is moot:** 7a's (`WindowHandle.tabs`, `TabPlacement`, `Windows`,
  `Terminals.openTab`) and 7b's (`ContentTab.moveToNewWindow`). Plans 8a and 8b rewrote that Javadoc, and
  `git grep -i "parent tab\|active group\|pane window" jasper-sdk/src/main/java` finds nothing.
- **The series notes:**
  - restore never goes through `TabRegistry.open` (plan 4b): a restored terminal registers under its saved id through
    `TabRegistry.add`, straight into its window (Task 8); 6b's plugins' items do the same;
  - a child window holding only Jasper's own items is not saved as an empty child (plan 4b): Task 4's snapshot leaves it
    out (`WorkspaceSnapshotTest`'s Errors tab case, which plan 8a kept);
  - `ContentTab.window()` names a never-open window while an item waits for the main window (plan 4b): this plan
    restores no plugin's item, so it is plan 6b's;
  - a child window's frame bounds are restored through `WindowPlacement`, the keep-on-screen helpers shared (plan 5):
    Task 2 moved the helpers, and Task 8's `JasperWindow.restore` sets the saved place that the frame takes through
    `WindowFrame.place` and `WindowPlacement.restore`, never `Opening.bounds` (Rulings 21);
  - 4a's `closeAll` hook must not reopen the main window: the save in `closed()` takes a snapshot only (Task 5), and the
    restore adds no hook to `closeAll` or `closed()` (Task 8);
  - Remote's `NOT_STARTED` local panes and 4b's fake main-window close are plan 6b's; the Vault manager is a dialog, so
    it has no restorer, and dialogs are not restored (Verified facts 12).

## Spec issues

The earlier versions' readings 1-3 stand (spec 13's start rule, `[main_window]` with no migration, the set-aside
notice); reading 4 is superseded (see Superseded). This re-plan's:

5. **Spec 4 has the main window's empty tab hold no pane; plan 8a's model keeps one empty pane** (a `Group` with no
   tab, as `Layout.empty()` makes). Read as the model holds it: the file writes the empty tab with its one pane naming
   no item (`item = ""`), and it comes back so (Rulings 32). Cost if wrong: none a person sees.
6. **Spec 12 keeps "6a's Tasks 1-5 where they still fit"; Task 3's file shape no longer does.** Its pane-window half is
   gone (plan 8a), and what remains says parent tab, group and tab where the spec says tab, pane and item. Read as spec
   12's "the codec changes with the model": Task 7 writes the spec's words (Rulings 32).
7. **Spec 13's "a single-instance request for a new terminal opens a new tab in the main window" and the restore.** The
   spec does not say whether the restored tab that showed starts when a request's new tab shows over it. Read as
   workspace tabs spec 13's "the others start when first shown": the new tab shows, and the restored tab waits like
   any hidden one (Rulings 37).

## Superseded

What the tabs-and-panes model makes moot in the earlier versions. Rulings 1-3, 5-13, 15, 16 and 18-24 stand as written,
their numbers kept, read with tab for parent tab, pane for group and item for tab where they name them; 32 to 42 are
this re-plan's.

- **Rulings 25 (the file's shape: `role` `main`, `full` or `pane`, `[[windows.parent_tabs]]`, a pane window's
  `origin`) is superseded** by Rulings 32: pane windows are gone (plan 8a), and the file says window, tab, pane and item.
- **Rulings 26 loses its origin clause** (an unreadable origin fell back to `WorkspaceFile.NO_ORIGIN`): plan 8a took
  origins out. A bad tab entry, one repeating an earlier one's ids and one with no item beside others still drop alone.
- **Rulings 27 is reworded:** tab, pane and item ids come back as saved and a window's id is new at every opening,
  because the saved layout names its items by id and saving straight after a restore writes what was read; no origin
  names a tab any more.
- **Rulings 29 (a pane window comes back as a pane window) is moot**, and **Rulings 30** is read as the restore taking
  the strip's places at once (`ParentTabBar.atOnce`, plan 8c Rulings 9; plan 8a Rulings 20), the window's theme and glass coming from the
  one helper for every tab's workspace (plan 7b).
- **Rulings 4 is narrowed** to a pane's one item: a restored terminal starts once the tab holding it is the one its
  window shows when an EDT turn ends, every pane of that tab, a maximize's hidden panes too (Rulings 37).
- **Rulings 14 is reworded:** a tab every item leaves goes, a full window every item leaves is left out, and the main
  window may be saved as its shown tab, empty.
- **Rulings 17 is reworded:** a main window none of whose shown tab's shown panes holds a terminal (the maximized pane
  alone counts while one is) once its opening's EDT turn ends marks the first paint with its own (Rulings 42).
- **Rulings 28 is read for tabs and panes:** a tab opening, closing, showing or moving along its strip, and every move
  between windows (Move Tab to New Window, Move to Main Window, the tab and pane drags), reach
  `WindowContent.workspaceChanged`, where the save is asked for.
- **Rulings 31 is read for tabs and panes:** the gestures that show tabs one at a time are Move to Main Window and the
  tab moves of `WindowManager.moveParentTab`; the start reads what shows when the turn ends.
- **The Goal, Architecture, Review Focus, Verified facts, File structure and the Tasks after Task 5 are rewritten**;
  the old Tasks 6, 7 and 8 are Tasks 8, 9 and 10, and Tasks 6 and 7 are new.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `workspace.toml`'s keys change with no migration and no new version (Rulings 32); nothing has ever
  written one, since the application gets its store only in Task 9. No shim, no "was removed" text.
- Spec 8's file is `<home>/workspace.toml` (`AppDirs.workspaceState()`), set aside as `workspace.toml.bad`; plan 2's
  `WorkspaceFile` is the codec.
- Spec 13's timing: a change is saved within one second (`WorkspaceStore.DELAY`), on a virtual thread, never on the EDT;
  a window closing, Quit and going to the background save at once. The `--background` warm-up rehearsal never reads or
  writes `workspace.toml`.
- The SDK stays 0.21.0 (Task 1's bump) and does not change here; nor do the testkit, the contract cases or any plugin.
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): none changes. `workspace`
  already names `persistence` and `layout`; `persistence` names `layout`. `MainWindow` is still made only in
  `WindowManager.ensureMain` (a lambda declared there counts as it).
- No motion (AGENTS.md; spec 5): restored windows, tabs and panes appear at once.
- The theme-migration ledger (`docs/design/theme-engine/migration.csv`, checked by `ThemeMigrationCoverageTest`): a new
  Java file whose text matches its `CANDIDATE` pattern gets a row in the task that creates it, with `ledger.add(...)`
  and "New in workspace-tabs plan 6a Task N": `WorkspaceRestoreTest` (Task 8, for `new JasperWindow.Placement`) and
  `JasperApplicationWorkspaceTest` (Task 9, for `new JasperApplication`).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment with its `@param` and `@return`.
- `WindowContent` imports both `java.awt.*` and `java.util.*`: write `java.util.List` in full there.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- User-facing text says tab and pane (spec 3); the code keeps its names (plan 8a Rulings 1), and `workspace.toml` says
  window, tab, pane and item.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless: windows are the window manager's stand-in frames (`TestWindows`, as `WindowManagerTest`'s), the
  application's frames are stand-ins too, and the shells are idle sessions launched when a test says.

### The two helper scripts

Create them once, before Task 6, in the scratch directory `build/plan6a/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan6a
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan6a/
ls build/plan6a/edits.py build/plan6a/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan6a")`. Java text inside a script is in triple-quoted strings; where it holds
a backslash the string is raw (`r"""…"""`), so the Java text reads as written. A file a task creates is written from the
plan's code block.

## Review Focus

1. **Quit, then start again (or close the main window and reopen it) with two windows, several tabs and a split tab.**
   Every window comes back where it was, each tab in its place with its panes and the one that showed, and saving
   straight after the restore writes what was read. Task 8:
   `WorkspaceRestoreTest.theMainWindowAndItsFullWindowsComeBackAsTheyWere`,
   `reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed`; Task 9:
   `JasperApplicationWorkspaceTest.quitSavesEveryWindowAndTheNextStartRestoresThem`,
   `goingToTheBackgroundSavesAndReopeningRestoresTheSameWindows`.
2. **Only what shows starts.** A hidden tab's terminals report `NOT_STARTED` until their tab shows, then start, every
   pane of it; Move to Main Window starts none of the tabs it passes over; a request's new tab leaves the restored tabs
   waiting; a directory that is gone starts where a new shell starts. Task 8:
   `aHiddenTabsTerminalsWaitAndStartWhenItFirstShows`,
   `movingARestoredFullWindowToTheMainWindowStartsOnlyTheTabThatShowsThere`,
   `aRestoredMainWindowOpensTheRequestsDirectoryInANewTab`, `aSavedDirectoryThatIsGoneStartsWhereANewShellStarts`.
3. **A file this build did not write, and nothing saved is lost.** A tab whose entry cannot be read drops alone, its
   window comes back, and the file is kept aside and told; a window that cannot be restored leaves the saved windows in
   `workspace.toml.bad`; a write the writer refuses and an `Error` from a snapshot are logged and cost only that save.
   Task 7: `WorkspaceFileReadTest.aPaneNamingAListOfItemsDropsItsTabAlone`, `aBrokenTreeDropsItsWindowAlone`,
   `aFileInTheParentTabsWordsIsSetAside`; Task 8:
   `aTabWhoseEntryCannotBeReadIsDroppedAloneAndTheFileIsKeptAside`,
   `aMainWindowThatCannotBeRestoredOpensANewOneAndTheSavedWindowsAreKept`,
   `aChildWindowThatCannotBeRestoredIsLeftOutAndTheSavedWindowsAreKept`; Task 6:
   `WorkspaceStoreTest.aWriteTheWriterRefusesIsLogged`, `aSnapshotThatThrowsAnErrorCostsOnlyThatSave`.
4. **The empty workspace.** The main window saved with no item comes back empty, under its saved ids, showing New
   Terminal Tab and Open File, and still marks the first paint. Task 8: `theEmptyWorkspaceComesBack`; Task 9:
   `aMainWindowRestoredWithoutATerminalStillMarksTheFirstPaint`.
5. **A save asked for while the main window opens.** A window discarded during a restore saves at once, which finds
   nothing to save yet; the save a change scheduled before still comes a second later. Task 6:
   `WorkspaceStoreTest.aSaveWithNothingToSaveKeepsTheSaveAChangeScheduled`.

## Verified facts

From `81f80c1af`, where plans 8a, 8b and 8c (with plan 8c's final-review fixes) and this plan's Tasks 1-5 are executed.

1. **What Tasks 1-5 left, as plan 8a left it.** `TerminalPane.restored()` and `waiting()` (`workspace/TerminalPane
   .java:258-261`) hold a restored pane, which reports `NOT_STARTED` (`:482-484`) until `start()` (`:264`); a saved
   directory gone by then starts where a new shell starts (`ShellLauncher.launch`'s `anyDirectory`). `JasperWindow
   .Placement` and `placed(Placement)` (`JasperWindow.java:41-96`) keep where a window is; the application's frame takes
   a window's `placement()` when it is built (`JasperApplication.frameOnScreen`, `:566`: `frame.place(window
   .placement(), window::placed)`, which fits it onto today's displays through `WindowPlacement.restore`).
   `WindowManager.snapshot()` (`:596-618`) saves each window's place, its `ParentTabs` and a `TabRecord.Terminal` per
   local terminal, the rest taken out by `WorkspaceFile.keeping`; `WorkspaceStore` (`workspace/WorkspaceStore.java`) is
   the store, and `WindowManager.workspace(WorkspaceStore)` (`:136`), its setter, has no caller in the main code: nothing
   reads or writes `workspace.toml` yet.
2. **The codec** (`persistence/WorkspaceFile.java`). `WindowRecord(Role, Optional<UiState.Bounds>, int, ParentTabs,
   Map<TabId, TabRecord>)` (`:109`); `Role` is `MAIN` or `CHILD`, written `main` or `full` (`:572`). The reader takes a
   window's `parent_tabs` and `active_parent_tab` (`parentTabs`, `:343-374`), a tab's `nodes`, `active_group` and
   `maximized` (`layout`, `:417-438`), a node of `kind = "group"` with `tabs` and `selected` (`node`, `:441-456`) and the
   window's `tabs` (`:478-492`); the writer the same (`encode`, `:569-584`; `appendNode`, `:601-618`; `appendTab`,
   `:620-630`). A `Group` holds at most one tab (`layout/Group.java:21`, plan 8a Rulings 2), so a group listing two
   throws in `node`, and its tab entry drops alone (`parentTabs`' per-entry `catch`, `:361-363`). `tabId(Object)`
   (`:539-546`) serves only `selected`.
3. **The store's review findings** (`WorkspaceStore.java`). `save()` (`:124-145`) cancels the scheduled save before it
   asks for a snapshot, catches only a `RuntimeException` from it (`:135`), and returns when the snapshot is empty;
   `writeLater` (`:168-171`) takes back a write the writer refused (`whenComplete`'s `refused`) with no log, while
   `write` (`:173-180`) logs one that failed. `WorkspaceStoreTest` (`jasper-app/src/test/…/workspace/`) records no log,
   so its failure cases print warnings.
4. **The opening point.** `WindowManager.ensureMain` (`:211-233`) returns early for an open or closing main window, while
   every window closes, and refuses a second request while the main window opens (`openingMain`), then opens it with
   `open(Opening, make)` (`:551-584`), which composes the content, hands it the shared drag and itself, makes the window
   (`main = new MainWindow(…)` at `:222`), sets `content.onLayoutChanged = this::changed` (`:563`), builds the frame,
   shows it and reports it, and discards it on a failure. `Opening.empty` (`:47`) makes the content open with no item
   (`WindowContent`'s constructor). A main window is made only in methods named `ensureMain`, lambdas declared there
   included (`gradle/application-architecture.gradle.kts:109`, `:126`). `moveToMainWindow` (`:412-443`) moves a full
   window's tabs one at a time through `moveParentTab`, each showing as it lands, then shows the one the full window
   showed.
5. **A window's tabs** (`workspace/ParentTabsView.java`). It starts with one empty `WorkspaceView` under a random id
   (`:43-54`); `workspace(ParentTabId)` (`:367-377`) makes a tab's workspace with the window's theme and glass, the one
   helper for every tab (plan 7b); `changed(WorkspaceView)` (`:380-382`) ignores a workspace not yet in `views`;
   `bar()` (`:59`) is the strip, whose `atOnce(Runnable)` (`ParentTabBar.java:103-111`) makes it take every change at
   once. `WorkspaceView` (`workspace/WorkspaceView.java`) renders a layout over the items it holds (`apply`, `:156-172`,
   which calls `owner.workspaceChanged`), shows the main window's empty workspace (`EmptyWorkspace`, `:199-207`,
   `emptyWorkspace()` `:226`), and already has `restore(Tab, Layout)` (`:106-110`), plan 8c's way back for a failed pane
   move.
6. **The window's side.** `WindowContent.workspaceChanged(WorkspaceView, boolean)` (`:1278-1294`) lets the model take the
   change, returns while a pane moves between tabs (`holding`, plan 8c Rulings 17), then asks for the save
   (`layoutChanged()`), tells the terminal registry, the palette and the items, and refocuses. `attach` (`:1214-1248`)
   mounts an item (theme, change listener, `WindowTerminals.tabArrived`, `TabHost.attach`); `openTerminal`
   (`:1745-1761`) registers a new terminal through `TabRegistry.add(TabId, Tab.Kind, TabContent, UUID)`
   (`contributions/TabRegistry.java:167`) and starts it. `TabHost.attach(Tab, boolean)` (`TabHost.java:30-49`) wires a
   terminal and announces its pane when told. `newTab(Path)` (`:1687`) opens a terminal in a new tab, which shows.
7. **The application.** `startPlugins(AppDirs)` (`JasperApplication.java:1159`) loads `ui-state.toml` (`:1168`);
   `windowOpened` (`:580-586`) ends a window's timing; `timeFirstTerminal` (`:1730-1735`) marks `FIRST_PAINT` from the
   first terminal that paints; the shutdown awaits a list of futures (`:2174-2182`); `open` (`:935-944`) and
   `openFirstWindow` (`:954-961`) go through `ensureMain` with the request's first directory, then `openRest` opens each
   other directory with `newTab` (`:963-966`). `NotificationsFeature.GENERAL` and `notificationCenter.post` carry an
   application notice (`:990`).
8. **What starts a terminal.** A new terminal starts at once (`openTerminal`'s `terminal.start()`); a restored one only
   through `TerminalPane.start()`, once (`started`); `TerminalContent.start()` (`TerminalContent.java:68`) calls it.
9. **The ledger.** `ThemeMigrationCoverageTest.CANDIDATE` matches `new J[A-Z]`: `WorkspaceRestoreTest` names `new
   JasperWindow.Placement` and `JasperApplicationWorkspaceTest` `new JasperApplication`; `TestWindows` and the other
   tests this plan edits have rows already.
10. **The documents.** `docs/configuration.md:665-673` still says `ui-state.toml` keeps the main window's size, position
    and maximized state (Task 2 took it out of the code); `docs/app-architecture.md:106-108` and its `workspace` row
    (`:179`), `docs/app-maintenance.md:512-513` (the rehearsal) and `docs/plugin-authoring.md:364` are where the store,
    the rehearsal and a restored terminal belong; `jasper-sdk/README.md:45` says "0.21.0 is a breaking boundary for
    saved workspaces", and plan 8b's `## Tabs, panes and split items (SDK 0.21.0)` (`:65`, `512c48ac4`) describes the
    other half of 0.21.0; no section describes saved workspaces, and `## Tabs of every kind (SDK 0.19.0)` (`:76`)
    follows plan 8b's.
11. **Plain-launcher baseline.** Under a plain JUnit launcher with `java.awt.headless=true`, 82 `jasper-app` tests
    fail on `81f80c1af` itself, which read the staged plugins and fonts Gradle's test task provides: two of the
    `workspace` and `application` packages
    (`IntegratedWorkspaceTest.dockedPluginHeadersAreSingleWithHostActionsAndHideCollapsesOnlyThatPanel`,
    `EnterpriseFirstWindowTest.theFirstWindowOfAFreshHomeUnderEnterpriseUsesTheKitsDefaults`) and 80 nearly all in the
    `plugins` and `appearance` packages, `BundledSamplePluginTest` among them. With the plugins staged, 30 tests fail and
    18 abort there, none of them a test either plan adds or changes (Workspace).
12. **Dialogs are not part of the workspace:** the Vault manager is a modeless plugin dialog over the main window
    (`144ff89f1`), and a plugin dialog closes with its Jasper window (`7b3149ab4`); nothing saves either.
## Rulings

Record them in `docs/STATUS.md` (Task 10). Rulings 1 to 23 are the first version's and 24 to 31 the parent-tabs
re-plan's, their numbers kept, as "What — why" (see Superseded for those the model makes moot or rewords); 32 to 42 are
this re-plan's, each as "What. Reason. Cost if wrong".

1. **Plan 6 is two plans: 6a (windows and terminal tabs, the store, SDK 0.21.0) and 6b (plugins' tabs: restore state,
   restorers, placeholders, the Editor's, Remote's and the sample's tabs)** — the brief's split past about twelve tasks;
   6a leaves Jasper runnable and saving.
2. **One SDK bump, to 0.21.0, in 6a's Task 1; 6b's additions are `@since 0.21.0` and ship before any release** — spec 14
   ("each bumps the SDK version"); THD Sourcing Crypto is rebuilt once, against the series' last version.
3. **`SessionState.NOT_STARTED` and `PaneSnapshot.State.NOT_STARTED` are each enum's first constant** — spec 13 ("report
   them as not started"); the constants follow a session's life.
4. **A restored terminal tab starts when it is the selected tab of a pane of the parent tab its window shows, after any
   layout or parent-tab change, the restore included; the selected tabs of panes a maximize hides start too, and a hidden
   parent tab's tabs wait until it shows** — parent tabs spec 9 ("the active parent tab's selected item in each pane");
   one rule in one place (`WindowContent.startShown`).
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
    file (plan 2's codec sets it aside), a readable one whose reader dropped a window, a parent tab or a tab entry (the
    codec copies it there, `Loaded.Restore.keptAside`, and that copy stands), or a readable one holding a window that
    cannot be restored (`WorkspaceStore.setAside(Snapshot)`, which writes it there before the next save would replace
    it)** — spec 15's set-aside; a vanished workspace with no word would look like a bug.
14. **The snapshot takes out every tab that cannot come back as closing it would (`WorkspaceFile.keeping`, over
    `Layout.remove`): a parent tab left with none goes and the one used before it shows when it showed, a full window or
    a pane window left with none is left out, and the main window may be saved as its shown parent tab, empty** — plan 2
    Rulings 20; parent tabs spec 4's invariants.
15. **No snapshot while no main window is open or while it opens** — a restore must never save itself half-done, and
    an empty `windows` list is no workspace.
16. **A restored child window's timing line says `restore`** — `StartupRuns` picks a window by its trigger; a restored
    child window is not a menu's window.
17. **A main window that shows no terminal tab when it opens (no selected tab of a pane of the parent tab it shows is a
    terminal: the maximized pane alone counts while one is maximized) marks the first paint with its own paint** — fact
    8: a hidden terminal paints nothing, and without it neither the presentation's proof nor residency's settle would
    come.
18. **The rehearsal never touches the store: the store is created in `startPlugins` and read only in `ensureMain`,
    which the rehearsal never calls** — spec 13; fact 8.
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
24. **The keep-on-screen helpers move beside `WindowPlacement` (`WindowPlacement.fitted`, `WindowPlacement.displayFor`),
    their cases with them into `WindowPlacementTest`; `WindowPlacement.restore` fits a window that no longer fits its
    displays onto the one it overlaps most with `fitted`** — plan 5's final-review hand-off ("so main and child restore
    share one algorithm"); a restored main, full or pane window and a torn-out one are kept on a display by one clamp.
25. **`workspace.toml` keeps spec 8's nodes and tabs one level down: a window's `role` is `main`, `full` or `pane`; a
    main or full window names its shown parent tab (`active_parent_tab`) and lists its parent tabs in bar order
    (`[[windows.parent_tabs]]`, each with its `id`, `active_group`, `maximized` and `[[windows.parent_tabs.nodes]]`); a pane
    window keeps its `origin` as an inline table (`parent_tab`, `pane`, `side`, `share`) beside its `active_group`,
    `maximized` and `[[windows.nodes]]`; every window's tabs follow under `[[windows.tabs]]`, in window order; `version`
    stays 1** — parent tabs spec 4 ("records parent tabs, pane windows and their origins"; "the format changes without
    migration") leaves the shape open, and nothing ever wrote a version 1 file, so no reader of one can meet the new.
26. **Reading a file this build did not write: a bad parent tab entry, and one with no tab beside others, is dropped
    alone, as a bad tab entry is; a parent tab its tabs all leave goes as closing them would; an unreadable `active_parent_tab` falls back to the first parent
    tab and an unreadable origin to `WorkspaceFile.NO_ORIGIN`, a parent tab and a pane no window holds, so Bring Back
    takes the panes beside the main window's active pane; a window holding a parent tab an earlier window holds is
    dropped** — plan 2's rules one level down (bounds and choices fall back alone, bad entries drop alone); parent tabs
    spec 5's fallback; ids stay unique across windows (plan 7b Rulings 10).
27. **Parent tab, group and tab ids come back as saved; a window's id is new at every opening** — origins name parent
    tabs only (plan 7b Rulings 1 and Spec issues 1), so a restored pane window still brings its panes back.
28. **Parent tabs opening, closing, showing or moving along their bar, and every move between windows (pop outs, Bring
    Back, Move to Main Window, drags), are changes worth saving too: each reaches `WindowContent.workspaceChanged`, where
    the save is asked for** — parent tabs spec 9 saves the parent tabs and the one that shows; plan 7a's hook (fact 3).
29. **A pane window comes back as a pane window: the window manager makes the empty child one with its saved origin before
    it restores, as `openChild` does (plan 7b Rulings 9), and `WindowContent.restore` refuses a record of the other
    shape; a full window comes back with its parent tabs, the main window with its own** — parent tabs spec 9 and 11
    ("restored as pane windows").
30. **A restored parent tab's workspace takes the window's theme and glass when it is made, and restored tabs take their
    places at once, without the open motion, as moved tabs do** — no animations (AGENTS.md); plan 7a's Task 3 review
    found a new parent tab's workspace without either.
31. **The start of a window's shown restored tabs waits for the end of the EDT turn and is asked for once per turn: it
    starts what shows when the turn ends, and nothing in a window that closed meanwhile; a close or a move of several tabs
    (Close Pane, Close Parent Tab, Close Others, Pop Out Pane, Bring Back, a parent tab's move), which shows each tab it
    passes over for an instant, starts none of them** — spec 13's "the others start when first shown": a person never saw
    a tab a gesture passed over, and its shell would run for nothing.

32. **`workspace.toml` says window, tab, pane and item, as spec 3 does.** A window keeps its `role` (`main` or `full`),
    bounds, `extended_state` and `shown_tab`, and lists its tabs in strip order under `[[windows.tabs]]`, each with its
    `id`, `active_pane`, `maximized` and `[[windows.tabs.nodes]]`; a node is a `split` or a `pane`, and a pane names its
    one item (`item`, empty for the main window's empty workspace, spec 4 as the model holds it); the window's items
    follow under `[[windows.items]]`, in window order. `version` stays 1. The code keeps its names (plan 8a Rulings 1)
    and `Group` its list (plan 8a Rulings 2). Reason: spec 12 ("each pane saves its one item") and spec 3's words; the
    file is the one name that outlives a refactor, and nothing has written one yet, since the store reaches the
    application only in Task 9. Cost if wrong: renaming the keys later sets every saved workspace aside once.
33. **A write the writer refuses is logged as a write that fails is, with its file and the refusal (Task 5's review,
    Important).** Reason: Rulings 22 keeps the next write going, and a refusal is a lost save that nothing else reports.
    Cost if wrong: none.
34. **`WorkspaceStore.save()` takes over the save a change scheduled only once it has windows to write;** with nothing
    to save yet (no main window open, or it opens) or a snapshot that fails, the scheduled save stays. Reason: Task 5's
    review: a window discarded during a restore saves at once, finds nothing to save, and took the change scheduled
    before it, which then nothing saved until the next change. Cost if wrong: none.
35. **An `Error` from the snapshot costs that save alone, logged, as a write's `Error` already does.** Reason: Rulings
    22; a close, Quit or the hold must not stop on a window that cannot say what it holds (Task 5's review). Cost if
    wrong: an `OutOfMemoryError` in a snapshot is logged on the EDT instead of thrown there.
36. **The store's tests record its log, and `layoutChanged()` stays right after the model takes a change, not in a
    `finally`.** Reason: Task 5's review: the warnings the failure cases provoke are asserted, not printed; Task 5's
    extra 5 already asks for the save before any listener can throw, and a `finally` would ask for one after a change
    the model refused, which has nothing new to save. Cost if wrong: none.
37. **A restored terminal starts once the tab holding it is the one its window shows when an EDT turn ends: every pane
    of that tab, a maximize's hidden panes too.** A tab a hover shows during a pane drag counts (it shows, plan 8c
    Rulings 4); a request's new tab, which shows, leaves the restored tab that showed waiting, as any tab a gesture
    passes over. Reason: spec 12 with workspace tabs spec 13 ("the others start when first shown") and Rulings 31's end
    of turn; a pane holds one item, so "the selected tab of each pane" is every pane. Cost if wrong: a restored tab that
    a request covered starts when it is first shown instead of at start.
38. **Every window restores by one route:** `WindowManager.ensureMain` reads the store (after its own checks, since
    reading ends the store's hold), opens the main window empty and restores it before its frame exists
    (`JasperWindow.restore`, `WindowContent.restore`, `ParentTabsView.restore`, `WorkspaceView.restoreSaved`), then each
    full window the same way, the main window in front; the strip takes the restored tabs at once
    (`ParentTabBar.atOnce`). Reason: pane windows are gone, so the main window and a full window restore alike (Rulings
    12, 21 and 30). Cost if wrong: none.
39. **Task 5's review fix round is a task of its own (Task 6).** Reason: the store and the codec are separate files with
    separate reviews; a reviewer could reject one and approve the other. Cost if wrong: one commit more.
40. **The earlier tasks' Minors the ledger gave the final fix wave stay with it**, and Task 1's re-review item 3 is
    closed by plan 8a's rewrite of `ContentTab`'s Javadoc. Reason: the brief gives this re-plan Task 5's review; none of
    the others touches the file, the store or the restore. Cost if wrong: none.
41. **A request's new tab that a restored main window fails to open is logged and costs only itself;** the main window
    stays opened, and the tabs plugins asked for while it was composed open in it. Reason: the window has shown by then,
    and a throw reaching `ensureMain`'s `catch` would report a shown window failed (`mainFailed`) and skip `mainOpened`;
    `restoreChild`'s failures are logged the same way (6a review M2). No test pins it: the headless fixtures cannot make
    `newTab` throw cheaply (a shell's launch fails later, through its completion; the terminal registry catches its
    listeners' failures; a new item's id is fresh). Cost if wrong: a directory that cannot open in a new tab is only
    logged.
42. **The first-paint check reads the main window once its opening's EDT turn ends** (`SwingUtilities.invokeLater` in
    `windowOpened`, `!window.closed() && window.content().showsNoTerminal()`). Reason: `windowOpened` runs inside
    `WindowManager.open`, before the same turn's `newTab(directory)`, `mainOpened` and requested file; the start-up's
    first-paint proof, its settle and the memory watch should not wait for a restored terminal that a request's editor
    tab hides (6a review M3). Cost if wrong: the mark comes one EDT turn later.

## File structure

- `dev.jasper.app.workspace`: `WorkspaceStore` (Task 6); `TerminalContent`, `WorkspaceView`, `ParentTabsView`,
  `WindowContent`, `WindowManager`, `JasperWindow` (Task 8); `WindowContent` (Task 9).
- `dev.jasper.app.persistence`: `WorkspaceFile` (Task 7, the file's words). `dev.jasper.app.layout`: `Group` (Task 7,
  Javadoc).
- `dev.jasper.app.application`: `JasperApplication` (Task 9).
- Tests: `WorkspaceStoreTest`, `TestWindows` (Javadoc) (Task 6); `WorkspaceFileTest`, `WorkspaceFileReadTest` (Task 7);
  new `WorkspaceRestoreTest` (Tasks 8 and 9); new `JasperApplicationWorkspaceTest` (Task 9).
- Documents (Task 10): `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/STATUS.md`, this plan's status banner; the ledger in Tasks
  8 and 9.

## Before you start

Plans 8a, 8b and 8c are executed first, plan 8c with its final-review fix wave; Tasks 1-5 of this plan are executed.

```bash
git branch --show-current                                                                                  # claude/workspace-tabs
git status --short                                                                                         # nothing
git log --oneline -1 --grep "Workspace tabs: save the windows within a second and when one closes"        # Task 5's commit
grep -c 'VERSION = "0.21.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                       # 1
grep -c '.whenComplete((done, refused) -> { if (refused != null) unwritten(target, writing); });' jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java  # 1
grep -c 'window.get(List.of("parent_tabs"))' jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java  # 1
grep -c 'public Optional<MainWindow> ensureMain(String trigger, Path directory)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'void workspaceChanged(WorkspaceView view, boolean refocus) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'private WorkspaceView workspace(ParentTabId id) {' jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java  # 1
grep -c 'void atOnce(Runnable change) {' jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabBar.java  # 1
grep -rn 'workspace(store\|\.workspace(workspaceStore)' jasper-app/src/main | wc -l                         # 0
```

Every command prints the stated result (the `git log` one commit). Each executed plan of this series added a
`docs/STATUS.md` entry at the top; this plan's goes above plan 8c's. Create the two helper scripts (Global Constraints).

---

### Task 1: SDK 0.21.0 and a terminal tab that waits to be shown

> **Executed** (`30cc4ca35`; its review's fix round `f0d788ae2`). History: its steps ran on the parent-tabs model and are
> not run again. Its deviations are in the ledger: `PresentationSelectionTest`'s newer-SDK pin is `>=0.22.0`, and
> `holdPane` holds a local pane only (`TerminalFixture` refuses a provided one), which starts when its tab is selected
> after `TAB_SELECTED`. Later plans changed its code: plan 8a's Task 4 rewrote the SDK Javadoc its F20 step had worded for
> parent tabs (`WindowHandle.tabs()`, `TabPlacement`, `Windows.openTab`, `Terminals.openTab`, `ContentTab`'s moves), and
> plan 8b renamed `TabPlacement.ACTIVE_GROUP` to `NEW_TAB` and gave the testkit's `FakeWorkspace` its two levels.
> `SessionState.NOT_STARTED`, `TerminalPane.restored()`, `FakePluginHost.holdPane`, `RestoredPaneTest` and
> `PluginContractTest.aRestoredPaneReportsNotStartedAndDropsText` stand as executed.

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

- [x] **Step 1: Write the failing tests**

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

- [x] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.CapabilitiesTest' :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL: `expected: "0.21.0" but was: "0.20.0"`, and `cannot find symbol` for `NOT_STARTED`, `holdPane`,
`restored()`, `waiting()` and the seven-argument `launch`.

- [x] **Step 3: The version, the manifests and the guides**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java", [('VERSION = "0.20.0";', 'VERSION = "0.21.0";', 1)])
# The SDK's Javadoc speaks of parent tabs and pane windows (pre-flight F20: plans 7a and 7b left the SDK frozen).
edit("jasper-sdk/src/main/java/dev/jasper/sdk/terminal/WindowHandle.java", [
    ("     * The window's tabs, terminal and content, in workspace order (group by group, each group's tabs in strip order);\n",
     "     * The window's tabs, terminal and content: parent tab by parent tab in bar order, then group by group, each group's\n"
     "     * tabs in strip order;\n", 1),
    ("     * The active tab: the selected tab of the window's active group, terminal or content.\n",
     "     * The active tab: the selected tab of the active pane of the window's shown parent tab, terminal or content.\n", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/terminal/Terminals.java", [
    ("     * Opens a terminal tab in the window's active group and selects it.",
     "     * Opens a terminal tab in the active pane of the window's shown parent tab and selects it.", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java", [
    ("     * Opens an owned view as a content tab at the end of the active group of the owner's window\n",
     "     * Opens an owned view as a content tab at the end of the active pane of the owner's window's shown parent tab\n", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java", [
    ("    /** At the end of the active group of the owner's window, or of the main window when there is no owner. */\n",
     "    /**\n     * At the end of the active pane of the owner's window's shown parent tab, or of the main window's when there is\n"
     "     * no owner.\n     */\n", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java", [
    ("     * Move Tab to New Window: a new child window takes the tab and its content. A child window's only tab, a tab with a\n",
     "     * Pop Out Tab: a new pane window takes the tab and its content, and its Bring Back returns them. A child window's\n"
     "     * only tab, a tab with a\n", 1),
    ("     * Move Tab to Main Window: the end of the main window's active group takes the tab and its content, the main window\n",
     "     * Move to Main Window: the end of the active pane of the main window's shown parent tab takes the tab and its\n"
     "     * content, the main window\n", 1)])
for plugin in ["editor", "enterprise", "fileserver", "history", "indexer", "remote", "sample", "snippets", "vault"]:
    edit(f"plugins/{plugin}/src/main/resources/plugin.toml", [('sdk = ">=0.20.0, <0.21"', 'sdk = ">=0.21.0, <0.22"', 1)])
edit("docs/examples/external-plugin/src/main/resources/plugin.toml", [('sdk = ">=0.20.0, <0.21"', 'sdk = ">=0.21.0, <0.22"', 1)])
edit("docs/examples/external-plugin/build.gradle.kts", [('getOrElse("0.20.0")', 'getOrElse("0.21.0")', 1)])
# 0.21 is a clean break: a guide's range for an older feature no longer loads, so every range in the guides is 0.21's.
for guide, count in {"docs/plugin-authoring.md": 15, "jasper-sdk/README.md": 8, "docs/sdk-icons.md": 1}.items():
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

Task 8 points the README's version line at the section it adds.

- [x] **Step 4: The state and the waiting pane**

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

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.workspace.RestoredPaneTest' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.plugins.HostedTerminalsTest' --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: PASS. Then `./gradlew check --continue`: BUILD SUCCESSFUL (every manifest loads on 0.21.0, the guides' ranges
are 0.21's, and the external example builds against 0.21.0).

- [x] **Step 6: Commit**

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

> **Executed** (`010bd1f9f`). History: not run again. Plans 8a-8c left its code as it ran (`JasperWindow.Placement` and
> `placed`, `WindowFrame.place`, `WindowPlacement` with `fitted` and `displayFor`); plan 8c rewrote `TabDrag` around the two
> helpers this task had moved out. Its review's Minors 1 and 4 (a frame's window-state listener; `placed` ignoring an
> equal place) went into Task 5; Minors 2 and 3 are the final fix wave's (Rulings 40).

Spec 13, 15 and 16: every window's bounds are saved, the main window's, each full window's and each pane window's alike,
and restored against today's displays as `WindowPlacement` does; `UiState`'s `[main_window]` goes with no migration
(Rulings 7). Plan 5's keep-on-screen helpers move beside `WindowPlacement`, so a restored window and a torn-out one are
kept on a display by one clamp (Rulings 24).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`, `persistence/WorkspaceFile.java` (one
  Javadoc line), `workspace/JasperWindow.java`, `workspace/WindowFrame.java`, `workspace/WindowShell.java` (Javadoc),
  `workspace/TabDrag.java` (the two helpers leave), `application/JasperApplication.java`
- Replace: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPlacement.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPlacementTest.java`
- Test: `persistence/UiStateTest.java`, `workspace/WindowManagerTest.java`, `workspace/TabDragWindowsTest.java` (its two
  helper cases move to `WindowPlacementTest`)

**Interfaces:**
- Consumes: `UiState.Bounds` (unchanged).
- Produces: `public record JasperWindow.Placement(Optional<UiState.Bounds> bounds, int extendedState)` with
  `Placement.NONE`; `public final JasperWindow.Placement JasperWindow.placement()`; `public final void
  JasperWindow.placed(JasperWindow.Placement value)`; `public void WindowFrame.place(JasperWindow.Placement saved,
  Consumer<JasperWindow.Placement> report)`; package `WindowPlacement(Optional<UiState.Bounds> saved, int extendedState)`,
  `JasperWindow.Placement WindowPlacement.placement(int extendedState)`, `static Rectangle WindowPlacement.fitted(Rectangle
  bounds, Rectangle usable)` and `static OptionalInt WindowPlacement.displayFor(Point cursor, List<Rectangle> displays)`.
- Removes: `UiState.MainWindow`, `UiState.mainWindow()`, `UiState.setMainWindow`, `WindowFrame.restoreLayout(UiState)`,
  `WindowPlacement.save`, `TabDrag.fitted`, `TabDrag.displayFor`.

- [x] **Step 1: Write the failing tests**

`WindowPlacementTest` is replaced whole: its subject no longer reads `UiState`, and it takes `TabDragWindowsTest`'s two
cases of the keep-on-screen helpers.

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPlacementTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window's place (workspace tabs spec 13 and 15): its normal bounds, kept apart from maximizing, fitted to today's
 * displays, and kept on a display as a restored window and a torn-out one are.
 */
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

    /** Plan 5's tear-out case, moved here with its helper (workspace plan 6a Rulings 24). */
    @Test void aTearOutWindowIsKeptOnTheDisplayUnderTheCursor() {
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        assertThat(WindowPlacement.fitted(new Rectangle(100, 100, 800, 600), usable)).as("on it: as it is").isEqualTo(new Rectangle(100, 100, 800, 600));
        assertThat(WindowPlacement.fitted(new Rectangle(1000, 600, 800, 600), usable)).as("past its right and bottom edges: moved onto it")
            .isEqualTo(new Rectangle(640, 300, 800, 600));
        assertThat(WindowPlacement.fitted(new Rectangle(-50, 0, 2000, 1200), usable)).as("larger: as large as it").isEqualTo(usable);
        assertThat(WindowPlacement.fitted(new Rectangle(5, 5, 800, 600), new Rectangle())).as("no display known: as it is")
            .isEqualTo(new Rectangle(5, 5, 800, 600));
    }

    /**
     * A tear-out's workspace reaches right of and below the cursor by most of a group, so near the edge of a display left
     * of or above another it mostly covers that other one: the display under the cursor takes the window all the same.
     * Plan 5's case, moved here with its helper.
     */
    @Test void aTearOutWindowOpensOnTheDisplayUnderTheCursor() {
        List<Rectangle> displays = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(1440, 0, 1920, 1080));
        assertThat(WindowPlacement.displayFor(new Point(1500, 220), displays)).as("its corner on the left display, the cursor on the right")
            .hasValue(1);
        assertThat(WindowPlacement.displayFor(new Point(130, 120), displays)).as("on the left").hasValue(0);
        assertThat(WindowPlacement.displayFor(new Point(1440, 0), displays)).as("on the edge: the display it begins").hasValue(1);
        assertThat(WindowPlacement.displayFor(new Point(5000, 5000), displays)).as("on none: the default display").isEmpty();
        List<Rectangle> left = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1440, 0, 1440, 900));
        Rectangle workspace = new Rectangle(-80, 280, 800, 600);
        assertThat(workspace.intersection(left.get(0)).width).as("the workspace is mostly on the primary").isGreaterThan(700);
        assertThat(WindowPlacement.displayFor(new Point(-50, 300), left)).as("the cursor on the display left of the primary").hasValue(1);
        List<Rectangle> above = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(0, -900, 1440, 900));
        assertThat(WindowPlacement.displayFor(new Point(400, -40), above)).as("the cursor on the display above the primary").hasValue(1);
        List<Rectangle> mirrored = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(0, 0, 1440, 900));
        assertThat(WindowPlacement.displayFor(new Point(100, 100), mirrored)).as("displays that overlap: the first").hasValue(0);
        assertThat(WindowPlacement.displayFor(new Point(100, 100), List.of())).as("no display known: the default").isEmpty();
    }
}
```

`UiState` neither reads nor writes `[main_window]`, a window keeps where its frame last said it is, and
`TabDragWindowsTest` gives its two helper cases to `WindowPlacementTest`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit, span
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
span("jasper-app/src/test/java/dev/jasper/app/workspace/TabDragWindowsTest.java",
    "\n    @Test void aTearOutWindowIsKeptOnTheDisplayUnderTheCursor() {\n",
    """        assertThat(TabDrag.displayFor(new Point(100, 100), List.of())).as("no display known: the default").isEmpty();
    }
""", "")
PY
```

- [x] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `JasperWindow.Placement`, `placement()`, `placed(…)`, the constructor
`WindowPlacement(Optional, int)`, `WindowPlacement.fitted` and `WindowPlacement.displayFor`.

- [x] **Step 3: `UiState` loses the main window**

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

- [x] **Step 4: Placements**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPlacement.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A window's normal bounds, tracked apart from maximizing and minimizing, and restored against today's usable displays
 * (workspace tabs spec 13 and 15): what {@code workspace.toml} keeps for every window. Its two helpers keep a window on a
 * display, a restored window that no longer fits and a torn-out one alike (workspace plan 6a Rulings 24).
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
        normal = fitted(new Rectangle(normal.getLocation(), InitialWindowSize.fit(normal.getSize(), minimum, screen)), screen);
        return Optional.of(new Rectangle(normal));
    }

    /**
     * {@code bounds} kept on {@code usable}: no larger than it, then moved wholly onto it, as IntelliJ keeps a floating
     * window on its screen; as they are when {@code usable} is empty (no display known). A restored window that no longer
     * fits its displays, and a torn-out window's frame, keep themselves on their display through this.
     */
    static Rectangle fitted(Rectangle bounds, Rectangle usable) {
        if (usable.isEmpty()) return new Rectangle(bounds);
        int width = Math.min(bounds.width, usable.width), height = Math.min(bounds.height, usable.height);
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - height));
        return new Rectangle(x, y, width, height);
    }

    /**
     * The display a torn-out window opens on: the one under the cursor, the first of those that hold it. The new workspace
     * reaches right of and below the cursor, so it can cover a neighbouring display more than the cursor's.
     *
     * @param cursor the cursor on screen
     * @param displays the displays' bounds on screen
     * @return that display's index; empty when the cursor is on none, for the default display
     */
    static OptionalInt displayFor(Point cursor, List<Rectangle> displays) {
        for (int i = 0; i < displays.size(); i++) if (displays.get(i).contains(cursor)) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

    private static long overlap(Rectangle a, Rectangle b) {
        Rectangle intersection = a.intersection(b);
        return intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
    }
}
```

`JasperWindow` keeps its `Placement`; `WindowFrame.place` puts the frame where it was saved and reports where it is
when it shows, moves, resizes or is maximized; disposing the frame saves nothing; a torn-out window's frame keeps itself
on its display through `WindowPlacement`'s helpers, which leave `TabDrag`; the application places every window's frame,
the main window's and each child window's, and its window manager's comment stops saying frames keep anything:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit, span
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
    ("import dev.jasper.app.persistence.UiState;\n", "", 1),
    ("        java.util.OptionalInt under = TabDrag.displayFor(cursor,", "        java.util.OptionalInt under = WindowPlacement.displayFor(cursor,", 1),
    ("        frame.setBounds(TabDrag.fitted(bounds, usableBounds(display)));", "        frame.setBounds(WindowPlacement.fitted(bounds, usableBounds(display)));", 1)])
# The keep-on-screen helpers are WindowPlacement's now (plan 5's hand-off to plan 6).
span(W + "TabDrag.java", """    /**
     * {@code bounds} kept on {@code usable}: no larger than it, then moved wholly onto it, as IntelliJ keeps a floating
""", """        for (int i = 0; i < displays.size(); i++) if (displays.get(i).contains(cursor)) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

""", "")
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

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowPlacementTest' --tests 'dev.jasper.app.persistence.UiStateTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.workspace.TabDragWindowsTest' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add -A jasper-app
git commit -F - <<'EOF'
Workspace tabs: every window says where it is

Each window keeps its normal bounds and maximized bits as its frame
reports them, and a frame is placed where its window was saved, checked
against today's displays. UiState's [main_window] goes: workspace.toml
keeps every window's place. The keep-on-screen helpers move from TabDrag
to WindowPlacement, so a restored window and a torn-out one share them.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: `workspace.toml` keeps parent tabs, pane windows and their origins

> **Executed** (`6844e17cf`; its review's fix round `f001c70d2`: a tab entry repeating an earlier one's ids drops alone).
> History: not run again. Plan 8a's Task 2 (`6b6db31a4`) took pane windows out of its code (the `pane` role, `origin`,
> `WorkspaceFile.NO_ORIGIN`, `PaneLayout`'s case of `keeping`; `WindowRecord.layout` on `ParentTabs`), and plan 8a's Task
> 4 (`db9d3890d`) made a group hold one tab, so a saved group listing two is a bad entry and its tab drops alone. Task 7
> below writes the file in the spec's words (Rulings 32, which supersedes Rulings 25).

Parent tabs spec 4 and 9: the file records every window kind, the main window's and each full window's parent tabs
with the one that shows, and each pane window's layout and origin (Rulings 25 to 27). A `WindowRecord` holds 7a's
`WindowLayout`; the reader keeps plan 2's rules one level down (Rulings 26); `WorkspaceFile.keeping` takes a window's
tabs out as closing them would, for the reader here and for the snapshot and the restore later (Rulings 14). Nothing
writes the file yet.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java`, `layout/ParentTabId.java` (Javadoc),
  `platform/AppDirs.java` (Javadoc)
- Replace: `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileReadTest.java`

**Interfaces:**
- Consumes: 7a's `ParentTabs` (`of`, `parentTabs`, `active`, `activeParentTab`, `isEmpty`, `replace`, `remove`), `ParentTab`,
  `ParentTabId` (`parse`), `WindowLayout`; 7b's `PaneLayout(Layout, Origin)` and `Origin(ParentTabId, GroupId, Side,
  double)`; plan 2's `Layout.remove`.
- Produces: `public record WorkspaceFile.WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState,
  WindowLayout layout, Map<TabId, TabRecord> tabs)` (the main window holds `ParentTabs`; a child window's `ParentTabs`
  is a full window, a `PaneLayout` a pane window); `Snapshot` also refuses a parent tab in two windows; `public static
  Optional<WindowLayout> WorkspaceFile.keeping(WindowLayout layout, Predicate<TabId> kept, boolean main)`; package
  `static final Origin WorkspaceFile.NO_ORIGIN`; test fixtures `WorkspaceFileTest.parentTabId(int)`, `one(int, Layout)`,
  `single(int)`, `terminals(WindowLayout)`, `ORIGIN`, `parentTabs()`, and `WorkspaceFileReadTest.shown(WindowRecord)`.

- [x] **Step 1: Write the failing tests**

`WorkspaceFileTest` is replaced whole: its fixture gives the main window one parent tab and the child window an origin,
its exact texts are the new shape, and it pins `keeping`. Every expected text was written by a Python model of the
writer below and parses with Python's `tomllib` to the tables the reader reads.

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java`:

```java
package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.PaneLayout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.WindowLayout;
import dev.jasper.app.persistence.WorkspaceFile.Role;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8 and parent tabs spec 4 and 9: the workspace file's values and its exact text. */
class WorkspaceFileTest {
    @TempDir Path dir;

    static final UiState.Bounds BOUNDS = new UiState.Bounds(40, 60, 1400, 900);

    static TabId tab(int n) {
        return TabId.parse("00000000-0000-4000-8000-%012d".formatted(n));
    }

    static GroupId groupId(int n) {
        return GroupId.parse("00000000-0000-4000-9000-%012d".formatted(n));
    }

    static ParentTabId parentTabId(int n) {
        return ParentTabId.parse("00000000-0000-4000-a000-%012d".formatted(n));
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(WorkspaceFileTest::tab).toList(), Optional.of(tab(selected)));
    }

    /** Group {@code n} alone, holding tab {@code n}. */
    static Layout single(int n) {
        return new Layout(group(n, n, n), groupId(n), Optional.empty());
    }

    /** A main or full window's only parent tab, {@code id}, holding {@code layout}. */
    static ParentTabs one(int id, Layout layout) {
        return ParentTabs.of(new ParentTab(parentTabId(id), layout));
    }

    /** A record of a terminal tab in /tmp for each tab of {@code layout}. */
    static Map<TabId, TabRecord> terminals(WindowLayout layout) {
        var tabs = new HashMap<TabId, TabRecord>();
        for (TabId tab : layout.tabs()) tabs.put(tab, new TabRecord.Terminal("", "/tmp", ""));
        return tabs;
    }

    /** Where {@link #snapshot()}'s pane window came from: left of group 1 in parent tab 1, a quarter of their space. */
    static final Origin ORIGIN = new Origin(parentTabId(1), groupId(1), Side.LEFT, 0.25);

    /**
     * Spec 8's example as the main window's one parent tab: two columns, the right one in two rows, its lower right group
     * maximized; and a pane window without bounds. Listed pane window first: the snapshot puts the main window first.
     */
    static Snapshot snapshot() {
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3, 3), 0.75), new Child(group(3, 4, 4), 0.25))), 0.5)));
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 6, one(1, new Layout(root, groupId(2), Optional.of(groupId(2)))), Map.of(
            tab(1), new TabRecord.Terminal("build", "/Users/me/src/jasper", "zsh"),
            tab(2), new TabRecord.Content("README.md", "dev.jasper.editor", "document", Map.of("path", "/Users/me/src/jasper/README.md")),
            tab(3), new TabRecord.Terminal("", "C:\\work", ""),
            tab(4), new TabRecord.Content("Files \"prod\"", "dev.jasper.remote", "sftp", Map.of("remote path", "/srv/a", "host", "example"))));
        var pane = new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(new Layout(group(4, 5, 5), groupId(4), Optional.empty()),
            ORIGIN), Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(pane, main));
    }

    /** A main window of two parent tabs, the second showing, and a full window of two, the first showing (parent tabs spec 9). */
    static Snapshot parentTabs() {
        var main = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))), parentTabId(2));
        var full = new ParentTabs(List.of(new ParentTab(parentTabId(3), single(3)), new ParentTab(parentTabId(4), single(4))), parentTabId(3));
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, main, terminals(main)),
            new WindowRecord(Role.CHILD, Optional.empty(), 0, full, terminals(full))));
    }

    @Test void writesSpecEightsWorkspaceWithAPaneWindowExactly() throws Exception {
        Path file = dir.resolve("nested/workspace.toml");
        WorkspaceFile.write(file, snapshot());
        assertThat(Files.readString(file)).isEqualTo("""
            version = 1

            [[windows]]
            role = "main"
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 6
            active_parent_tab = "00000000-0000-4000-a000-000000000001"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = "00000000-0000-4000-9000-000000000002"

            [[windows.parent_tabs.nodes]]
            id = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            kind = "split"
            orientation = "horizontal"
            parent = ""
            weight = 1.0

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.parent_tabs.nodes]]
            id = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.75
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000003"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.25
            tabs = ["00000000-0000-4000-8000-000000000004"]
            selected = "00000000-0000-4000-8000-000000000004"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = "build"
            directory = "/Users/me/src/jasper"
            profile = "zsh"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "content:dev.jasper.editor:document"
            title = "README.md"
            state = { path = "/Users/me/src/jasper/README.md" }

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000003"
            kind = "terminal"
            title = ""
            directory = "C:\\\\work"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000004"
            kind = "content:dev.jasper.remote:sftp"
            title = "Files \\"prod\\""
            state = { host = "example", "remote path" = "/srv/a" }

            [[windows]]
            role = "pane"
            extended_state = 0
            origin = { parent_tab = "00000000-0000-4000-a000-000000000001", pane = "00000000-0000-4000-9000-000000000001", side = "left", share = 0.25 }
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.nodes]]
            id = "00000000-0000-4000-9000-000000000004"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000005"]
            selected = "00000000-0000-4000-8000-000000000005"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000005"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""
            """);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).as("no temporary file is left").containsExactly(file);
        }
    }

    @Test void writesEveryParentTabOfTheMainWindowAndAFullWindowInBarOrder() {
        assertThat(WorkspaceFile.encode(parentTabs())).isEqualTo("""
            version = 1

            [[windows]]
            role = "main"
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000002"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_group = "00000000-0000-4000-9000-000000000001"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000001"]
            selected = "00000000-0000-4000-8000-000000000001"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000002"
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows]]
            role = "full"
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000003"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000003"
            active_group = "00000000-0000-4000-9000-000000000003"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000003"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000004"
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000004"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000004"]
            selected = "00000000-0000-4000-8000-000000000004"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000003"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000004"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""
            """);
    }

    @Test void theEmptyMainWorkspaceWritesItsParentTabWithAGroupWithNoTabs() {
        Layout empty = new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty());
        assertThat(WorkspaceFile.encode(new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(new UiState.Bounds(0, 0, 800, 600)), 0,
            one(1, empty), Map.of())))))
            .isEqualTo("""
                version = 1

                [[windows]]
                role = "main"
                x = 0
                y = 0
                width = 800
                height = 600
                extended_state = 0
                active_parent_tab = "00000000-0000-4000-a000-000000000001"

                [[windows.parent_tabs]]
                id = "00000000-0000-4000-a000-000000000001"
                active_group = "00000000-0000-4000-9000-000000000001"
                maximized = ""

                [[windows.parent_tabs.nodes]]
                id = "00000000-0000-4000-9000-000000000001"
                kind = "group"
                parent = ""
                weight = 1.0
                tabs = []
                selected = ""
                """);
    }

    @Test void splitIdsAreNameBasedUuidsOfTheirPath() {
        assertThat(WorkspaceFile.splitId("")).isEqualTo("e8b5eccb-037b-3d65-a6c6-5e4889602b31");
        assertThat(WorkspaceFile.splitId("1")).isEqualTo("59943c66-5e92-39d1-a8ea-1f88b440a667");
        assertThat(UUID.fromString(WorkspaceFile.splitId("1.0")).version()).as("name-based, never a random id").isEqualTo(3);
    }

    @Test void theRecordsCheckTheirInvariants() {
        ParentTabs held = one(1, Layout.of(tab(1)));
        Map<TabId, TabRecord> record = Map.of(tab(1), new TabRecord.Terminal("", "/tmp", ""));
        assertThatIllegalArgumentException().as("a tab without a record")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, held, Map.of()));
        assertThatIllegalArgumentException().as("a record without a tab")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, one(1, Layout.empty()), record));
        assertThatIllegalArgumentException().as("an empty full window")
            .isThrownBy(() -> new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.empty()), Map.of()));
        assertThatIllegalArgumentException().as("a main window holding a pane window's layout")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, new PaneLayout(Layout.of(tab(1)), ORIGIN), record));
        assertThatIllegalArgumentException().as("minimized is not kept")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 1, held, record));
        var main = new WindowRecord(Role.MAIN, Optional.empty(), 6, held, record);
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.of(tab(2))), Map.of(tab(2), new TabRecord.Terminal("", "", "")));
        var pane = new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(Layout.of(tab(3)), ORIGIN),
            Map.of(tab(3), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(pane, child, main)).windows()).as("the main window first").containsExactly(main, pane, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main, pane)).children()).containsExactly(child, pane);
        assertThatIllegalArgumentException().as("no main window").isThrownBy(() -> new Snapshot(List.of(child)));
        assertThatIllegalArgumentException().as("two main windows").isThrownBy(() -> new Snapshot(List.of(main, main)));
        assertThatIllegalArgumentException().as("a tab in two windows")
            .isThrownBy(() -> new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.of(tab(1))), record))));
        Layout sameGroup = new Layout(new Group(held.activeParentTab().layout().activeGroup(), List.of(tab(2)), Optional.of(tab(2))),
            held.activeParentTab().layout().activeGroup(), Optional.empty());
        assertThatIllegalArgumentException().as("a group in two windows").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, new PaneLayout(sameGroup, ORIGIN), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().as("a parent tab in two windows (workspace plan 7b Rulings 10)").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, one(1, Layout.of(tab(2))), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x:y", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x", "", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new TabRecord.Terminal(null, "", ""));
    }

    /** Workspace plan 6a Rulings 14: what stays of a window when only some of its tabs can stay, as closing the others would. */
    @Test void keepingLeavesWhatClosingTheOtherTabsWouldLeave() {
        ParentTabs window = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2)),
            new ParentTab(parentTabId(3), single(3))), parentTabId(1)).select(parentTabId(3)).select(parentTabId(2));
        assertThat(WorkspaceFile.keeping(window, tab -> !tab.equals(tab(2)), false)).as("the shown one goes: the one used before it shows")
            .contains(new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(3), single(3))), parentTabId(3)));
        assertThat(WorkspaceFile.keeping(window, tab -> false, true)).as("the main window keeps its shown parent tab, empty")
            .contains(ParentTabs.of(new ParentTab(parentTabId(2), single(2).remove(tab(2)))));
        assertThat(WorkspaceFile.keeping(window, tab -> false, false)).as("a full window keeps nothing").isEmpty();
        Layout two = single(4).split(tab(5), groupId(4), Side.RIGHT);
        assertThat(WorkspaceFile.keeping(new PaneLayout(two, ORIGIN), tab -> !tab.equals(tab(5)), false)).as("a pane window keeps its origin")
            .contains(new PaneLayout(two.remove(tab(5)), ORIGIN));
        assertThat(WorkspaceFile.keeping(new PaneLayout(two, ORIGIN), tab -> false, false)).isEmpty();
    }
}
```

The reading tests keep their cases, one level down: the main window's one parent tab, a pane window where the child
window was (`role = "pane"`), full windows where two-window texts had a child (`role = "full"`), nodes under
`[[windows.parent_tabs.nodes]]`; and they gain parent tabs read back, a bad parent tab entry, a shown parent tab its
tabs leave, an active parent tab that falls back, a parent tab id two windows hold, and an origin that falls back:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
R = "jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileReadTest.java"
# The main window holds parent tabs and the snapshot's child is a pane window; the reading rules are the same, one level down.
edit(R, [
    ("import static dev.jasper.app.persistence.WorkspaceFileTest.BOUNDS;\n",
     "import static dev.jasper.app.persistence.WorkspaceFileTest.BOUNDS;\nimport static dev.jasper.app.persistence.WorkspaceFileTest.ORIGIN;\n", 1),
    ("import static dev.jasper.app.persistence.WorkspaceFileTest.groupId;\n",
     "import static dev.jasper.app.persistence.WorkspaceFileTest.groupId;\nimport static dev.jasper.app.persistence.WorkspaceFileTest.one;\n"
     "import static dev.jasper.app.persistence.WorkspaceFileTest.parentTabId;\nimport static dev.jasper.app.persistence.WorkspaceFileTest.parentTabs;\n", 1),
    ("import static dev.jasper.app.persistence.WorkspaceFileTest.tab;\n",
     "import static dev.jasper.app.persistence.WorkspaceFileTest.single;\nimport static dev.jasper.app.persistence.WorkspaceFileTest.tab;\n", 1),
    ("import dev.jasper.app.layout.Orientation;\n",
     "import dev.jasper.app.layout.Orientation;\nimport dev.jasper.app.layout.PaneLayout;\nimport dev.jasper.app.layout.ParentTab;\n"
     "import dev.jasper.app.layout.ParentTabs;\n", 1),
    ("import dev.jasper.app.layout.TabId;\n", "import dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.WindowLayout;\n", 1),
    ("""/** Workspace tabs spec 8 and 17: reading the workspace file back, dropping bad entries alone, setting a bad file aside. */""",
     """/**
 * Workspace tabs spec 8 and 17, parent tabs spec 4 and 9: reading the workspace file back, dropping bad entries alone,
 * setting a bad file aside.
 */""", 1),
    ("""    /** {@link WorkspaceFileTest#snapshot()} with its main window changed. */
    static Snapshot withMain(Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, bounds, extendedState, layout, tabs), snapshot().children().getFirst()));
    }
""", """    /** {@link WorkspaceFileTest#snapshot()} with its main window's one parent tab, or what it keeps, changed. */
    static Snapshot withMain(Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, bounds, extendedState, one(1, layout), tabs), snapshot().children().getFirst()));
    }

    /** The layout a window shows: a main or full window's shown parent tab's, or a pane window's. */
    static Layout shown(WindowRecord window) {
        return switch (window.layout()) {
            case ParentTabs parents -> parents.activeParentTab().layout();
            case PaneLayout pane -> pane.layout();
        };
    }
""", 1),
    ("""    static Map<TabId, TabRecord> terminals(Layout layout) {
        var tabs = new HashMap<TabId, TabRecord>();""",
     """    static Map<TabId, TabRecord> terminals(WindowLayout layout) {
        var tabs = new HashMap<TabId, TabRecord>();""", 1),
    ("""    /** A window of groups {@code first} and {@code second} side by side, each holding the tab of its number. */
    static WindowRecord window(Role role, int first, int second) {
        Layout layout = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(first, first, first), 0.5),
            new Child(group(second, second, second), 0.5))), groupId(first), Optional.empty());
        return new WindowRecord(role, Optional.of(BOUNDS), 0, layout, terminals(layout));
    }""",
     """    /**
     * A main or full window whose one parent tab, numbered {@code first}, holds groups {@code first} and {@code second} side
     * by side, each holding the tab of its number.
     */
    static WindowRecord window(Role role, int first, int second) {
        ParentTabs layout = one(first, new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(first, first, first), 0.5),
            new Child(group(second, second, second), 0.5))), groupId(first), Optional.empty()));
        return new WindowRecord(role, Optional.of(BOUNDS), 0, layout, terminals(layout));
    }""", 1),
    ("""        var empty = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.empty(), Map.of())));""",
     """        var empty = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, one(1, Layout.empty()), Map.of())));""", 1),
    ("""        Layout layout = window(Role.MAIN, 1, 2).layout();""", """        WindowLayout layout = window(Role.MAIN, 1, 2).layout();""", 1),
    ("""        var saved = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, drifting, terminals(drifting))));""",
     """        var saved = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, one(1, drifting), terminals(one(1, drifting)))));""", 1),
    ("""        assertThat(((Split) restored.snapshot().main().layout().root()).children()).extracting(Child::weight).containsExactly(0.75, 0.25);""",
     """        assertThat(((Split) shown(restored.snapshot().main()).root()).children()).extracting(Child::weight).containsExactly(0.75, 0.25);""", 1),
    ("""        WindowRecord main = snapshot().main();
        Layout layout = main.layout();
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString();""",
     """        WindowRecord main = snapshot().main();
        Layout layout = shown(main);
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString();""", 1),
    ("""        var droppedTerminal = keptAside(withMain(main.bounds(), 6, main.layout().remove(tab(1)), without(main.tabs(), tab(1))));""",
     """        var droppedTerminal = keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(1)), without(main.tabs(), tab(1))));""", 1),
    ("""        var droppedContent = keptAside(withMain(main.bounds(), 6, main.layout().remove(tab(2)), without(main.tabs(), tab(2))));""",
     """        var droppedContent = keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(2)), without(main.tabs(), tab(2))));""", 1),
    ("""        assertThat(read(replaceOnce(text, "\\n[[windows]]\\nrole = \\"child\\"", second + "\\n[[windows]]\\nrole = \\"child\\"")))""",
     """        assertThat(read(replaceOnce(text, "\\n[[windows]]\\nrole = \\"pane\\"", second + "\\n[[windows]]\\nrole = \\"pane\\"")))""", 1),
    ("""        Layout expected = main.layout().remove(tab(3));""", """        Layout expected = shown(main).remove(tab(3));""", 1),
    ("""        Layout expected = main.layout().remove(tab(4));""", """        Layout expected = shown(main).remove(tab(4));""", 1),
    ("""        String split = "kind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"\\"\\nweight = 1.0\\n\\n[[windows.nodes]]\\nid = \\"" + g3 + "\\"";""",
     """        String split = "kind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"\\"\\nweight = 1.0\\n\\n[[windows.parent_tabs.nodes]]\\nid = \\"" + g3 + "\\"";""", 1),
    ("""                {"role = \\"child\\"", "role = \\"side\\""},""", """                {"role = \\"full\\"", "role = \\"side\\""},""", 1),
    ("""        String cycle = "\\n[[windows.nodes]]\\nid = \\"x\\"\\nkind = \\"split\\"\\norientation = \\"vertical\\"\\nparent = \\"y\\"\\nweight = 0.5\\n"
            + "\\n[[windows.nodes]]\\nid = \\"y\\"\\nkind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"x\\"\\nweight = 0.5\\n";""",
     """        // After the tabs, these nodes still join the full window's last parent tab.
        String cycle = "\\n[[windows.parent_tabs.nodes]]\\nid = \\"x\\"\\nkind = \\"split\\"\\norientation = \\"vertical\\"\\nparent = \\"y\\"\\nweight = 0.5\\n"
            + "\\n[[windows.parent_tabs.nodes]]\\nid = \\"y\\"\\nkind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"x\\"\\nweight = 0.5\\n";""", 1),
    ("""        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.of(tab(1)), Map.of(tab(1), new TabRecord.Terminal("", "/tmp", "")));""",
     """        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, one(1, Layout.of(tab(1))), Map.of(tab(1), new TabRecord.Terminal("", "/tmp", "")));""", 1),
    ("""            var saved = new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, nested, terminals(nested))));""",
     """            var saved = new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, nested), terminals(one(2, nested)))));""", 1),
    ("""        assertThat(read(replaceOnce(text, "role = \\"child\\"", "role = \\"main\\""))).as("a second main window").isEqualTo(mainOnly);""",
     """        assertThat(read(replaceOnce(text, "role = \\"pane\\"", "role = \\"main\\""))).as("a second main window").isEqualTo(mainOnly);""", 1),
    ("""        assertThat(read(replaceOnce(text, t5 + "kind = \\"terminal\\"", t5 + "kind = \\"x\\""))).as("a child window left empty").isEqualTo(mainOnly);""",
     """        assertThat(read(replaceOnce(text, t5 + "kind = \\"terminal\\"", t5 + "kind = \\"x\\""))).as("a pane window left empty").isEqualTo(mainOnly);""", 1),
    ("""        int child = text.indexOf("\\n[[windows]]\\nrole = \\"child\\"");""", """        int child = text.indexOf("\\n[[windows]]\\nrole = \\"full\\"");""", 1),
    ("""        return replaceOnce(text, "role = \\"child\\"", "role = \\"side\\"");""", """        return replaceOnce(text, "role = \\"full\\"", "role = \\"side\\"");""", 1),
    ("""            .isEqualTo(clean(withMain(Optional.empty(), 6, main.layout(), main.tabs())));
        assertThat(bad()).as("a fallback is not a drop").doesNotExist();""",
     """            .isEqualTo(clean(withMain(Optional.empty(), 6, shown(main), main.tabs())));
        assertThat(bad()).as("a fallback is not a drop").doesNotExist();""", 1),
    ("""        assertThat(read(badEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, main.layout().remove(tab(1)), without(main.tabs(), tab(1)))));""",
     """        assertThat(read(badEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(1)), without(main.tabs(), tab(1)))));""", 1),
    ("""        assertThat(read(noEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, main.layout().remove(tab(4)), without(main.tabs(), tab(4)))));""",
     """        assertThat(read(noEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(4)), without(main.tabs(), tab(4)))));""", 1),
    ("""        cases.put("no main window", replaceOnce(valid, "role = \\"main\\"", "role = \\"child\\"").getBytes(StandardCharsets.UTF_8));""",
     """        cases.put("no main window", replaceOnce(valid, "role = \\"main\\"", "role = \\"full\\"").getBytes(StandardCharsets.UTF_8));""", 1),
    # Parent tabs and pane windows: read back as written, a bad parent tab or origin alone.
    ("""    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {""",
     """    /** {@link WorkspaceFileTest#parentTabs()} with its main window's parent tabs changed. */
    static Snapshot withParentTabs(ParentTabs main) {
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, main, terminals(main)), parentTabs().children().getFirst()));
    }

    @Test void parentTabsReadBackInBarOrderWithTheOneThatShows() throws Exception {
        WorkspaceFile.write(file(), parentTabs());
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(parentTabs()));
    }

    @Test void aBadParentTabEntryIsDroppedAloneAndAParentTabItsTabsLeaveGoes() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        ParentTabs second = ParentTabs.of(new ParentTab(parentTabId(2), single(2)));
        String p1 = "id = \\"" + parentTabId(1).value() + "\\"\\n";
        assertThat(read(replaceOnce(text, p1, "id = \\"not an id\\"\\n"))).as("a parent tab whose id cannot be read")
            .isEqualTo(keptAside(withParentTabs(second)));
        String t2 = "id = \\"" + id(tab(2)) + "\\"\\nkind = \\"terminal\\"";
        assertThat(read(replaceOnce(text, t2, "id = \\"" + id(tab(2)) + "\\"\\nkind = \\"telnet\\""))).as("the shown one's only tab dropped: the other shows")
            .isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(1), single(1))))));
        String active = "active_parent_tab = \\"" + parentTabId(2).value() + "\\"";
        assertThat(read(replaceOnce(text, active, "active_parent_tab = \\"nope\\""))).as("a choice that falls back to the first, not a drop")
            .isEqualTo(clean(withParentTabs(new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))),
                parentTabId(1)))));
    }

    /** Workspace plan 6a Rulings 26: only a window's only parent tab may be empty; one beside others drops alone. */
    @Test void anEmptyParentTabBesideOthersIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t1 = id(tab(1));
        assertThat(read(replaceOnce(text, "tabs = [\\"" + t1 + "\\"]\\nselected = \\"" + t1 + "\\"", "tabs = []\\nselected = \\"\\"")))
            .as("its window keeps its other parent tab").isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(2), single(2))))));
    }

    @Test void aWindowHoldingAParentTabAnEarlierWindowHoldsIsDropped() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String p3 = "id = \\"" + parentTabId(3).value() + "\\"\\n";
        assertThat(read(replaceOnce(text, p3, "id = \\"" + parentTabId(1).value() + "\\"\\n")))
            .as("parent tab ids are unique across windows (workspace plan 7b Rulings 10)")
            .isEqualTo(keptAside(new Snapshot(List.of(parentTabs().main()))));
    }

    @Test void aPaneWindowsUnreadableOriginFallsBackToOneNoWindowHolds() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord pane = snapshot().children().getFirst();
        var fallen = new Snapshot(List.of(snapshot().main(), new WindowRecord(Role.CHILD, pane.bounds(), 0,
            new PaneLayout(shown(pane), WorkspaceFile.NO_ORIGIN), pane.tabs())));
        for (String[] change : new String[][] {
                {"side = \\"left\\"", "side = \\"aslant\\""},
                {"share = 0.25", "share = 1.5"},
                {"pane = \\"" + groupId(1).value() + "\\"", "pane = \\"one\\""},
                {"origin = { parent_tab = ", "origin = { tab = "}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(clean(fallen));
        assertThat(WorkspaceFile.NO_ORIGIN.parentTab()).isNotEqualTo(ORIGIN.parentTab());
        assertThat(WorkspaceFile.NO_ORIGIN.parentTab().value().version()).as("name-based, never a random id").isEqualTo(3);
    }

    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {""", 1)])
PY
```

- [x] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `incompatible types: ParentTabs cannot be converted to Layout` (the record still holds a `Layout`), and
`cannot find symbol` for `keeping` and `NO_ORIGIN`.

- [x] **Step 3: The records, the reader and the writer**

A record holds what its window holds; the reader takes a window's `role` as its kind and reads parent tabs or a pane
window's origin; the writer writes them; `keeping` serves all three uses:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit, span
F = "jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java"
# What a window holds is 7a's WindowLayout: parent tabs, or a pane window's layout and origin (parent tabs spec 4 and 9).
edit(F, [
    ("import dev.jasper.app.layout.Orientation;\n",
     "import dev.jasper.app.layout.Orientation;\nimport dev.jasper.app.layout.Origin;\nimport dev.jasper.app.layout.PaneLayout;\n"
     "import dev.jasper.app.layout.ParentTab;\nimport dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.ParentTabs;\n"
     "import dev.jasper.app.layout.Side;\n", 1),
    ("import dev.jasper.app.layout.TabId;\n", "import dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.WindowLayout;\n", 1),
    ("import java.util.List;\n", "import java.util.List;\nimport java.util.Locale;\n", 1),
    ("import java.util.UUID;\n", "import java.util.UUID;\nimport java.util.function.Predicate;\n", 1),
    (""" * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8): each Jasper window's
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. {@link #write} writes it
 * atomically; {@link #read} never throws: it drops bad tab and window entries alone, keeping a copy of the file as
 * {@code workspace.toml.bad}, and sets an unreadable file aside under that name. Stateless: the caller chooses the
 * thread; the values are immutable.""",
     """ * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8; parent tabs spec 4 and
 * 9): each Jasper window's kind, bounds and maximized state, its parent tabs or, for a pane window, its one layout and
 * its origin, and what each of its tabs needs to come back. {@link #write} writes it atomically; {@link #read} never
 * throws: it drops bad tab, parent tab and window entries alone, keeping a copy of the file as {@code workspace.toml.bad},
 * and sets an unreadable file aside under that name. Stateless: the caller chooses the thread; the values are
 * immutable.""", 1),
    ("    /** Whether a window is the main window or a child window. */",
     "    /** Whether a window is the main window or a child window: a full window or a pane window, as its layout says. */", 1),
    ("         * The saved workspace, its bad entries dropped. A main window whose layout is empty restores the empty workspace.",
     "         * The saved workspace, its bad entries dropped. A main window whose only parent tab is empty restores the empty\n"
     "         * workspace.", 1),
    ("""     * Rulings 13 and 14). A bad tab entry, or a tab a group lists without one, is dropped as {@link Layout#remove(TabId)}
     * drops it; a bad window entry is dropped; bounds and choices fall back alone. When a window or a tab entry was
     * dropped, the original file is copied to {@code workspace.toml.bad} beside it, replacing an older one, and
     * {@link Loaded.Restore#keptAside()} reports the copy. A file that cannot be read, does not parse, has another
     * version or keeps no main window is moved to {@code workspace.toml.bad} instead.""",
     """     * Rulings 13 and 14). A bad tab entry, or a tab a group lists without one, is dropped as {@link Layout#remove(TabId)}
     * drops it, and a parent tab it leaves with no tab goes ({@link #keeping(WindowLayout, Predicate, boolean)}); a bad
     * parent tab or window entry is dropped; bounds, choices and a pane window's origin fall back alone. When a window, a
     * parent tab or a tab entry was dropped, the original file is copied to {@code workspace.toml.bad} beside it,
     * replacing an older one, and {@link Loaded.Restore#keptAside()} reports the copy. A file that cannot be read, does
     * not parse, has another version or keeps no main window is moved to {@code workspace.toml.bad} instead.""", 1),
    ("""        for (WindowRecord window : read) {
            List<GroupId> ids = window.layout().groups().stream().map(Group::id).toList();
            if (window.role() == Role.MAIN && windows.stream().anyMatch(kept -> kept.role() == Role.MAIN)) {
                drop(dropped, "Dropping a second main window from the workspace state");
            } else if (ids.stream().anyMatch(groups::contains) || window.layout().tabs().stream().anyMatch(tabs::contains)) {
                drop(dropped, "Dropping a window whose groups or tabs an earlier window holds");
            } else {
                groups.addAll(ids);
                tabs.addAll(window.layout().tabs());
                windows.add(window);
            }
        }""",
     """        var parentTabs = new HashSet<ParentTabId>();
        for (WindowRecord window : read) {
            List<GroupId> ids = groupIds(window.layout());
            List<ParentTabId> parents = parentTabIds(window.layout());
            if (window.role() == Role.MAIN && windows.stream().anyMatch(kept -> kept.role() == Role.MAIN)) {
                drop(dropped, "Dropping a second main window from the workspace state");
            } else if (ids.stream().anyMatch(groups::contains) || window.layout().tabs().stream().anyMatch(tabs::contains)
                    || parents.stream().anyMatch(parentTabs::contains)) {
                drop(dropped, "Dropping a window whose parent tabs, groups or tabs an earlier window holds");
            } else {
                groups.addAll(ids);
                tabs.addAll(window.layout().tabs());
                parentTabs.addAll(parents);
                windows.add(window);
            }
        }""", 1),
    ("""            Object name = entry.get(List.of("role"));
            Role role = "main".equals(name) ? Role.MAIN : "child".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            Layout layout = layout(entry);
            Map<TabId, TabRecord> records = tabs(entry, dropped);
            for (TabId tab : layout.tabs()) {
                if (records.containsKey(tab)) continue;
                drop(dropped, "Dropping tab " + tab + " from the workspace state: it has no readable entry");
                layout = layout.remove(tab);
            }
            records.keySet().retainAll(Set.copyOf(layout.tabs()));
            int extendedState = UiState.number(entry, "extended_state").filter(state -> (state & ~6) == 0).orElse(0);
            return Optional.of(new WindowRecord(role, UiState.bounds(entry), extendedState, layout, records));""",
     """            Object name = entry.get(List.of("role"));
            Role role = "main".equals(name) ? Role.MAIN : "full".equals(name) || "pane".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            WindowLayout held = "pane".equals(name) ? new PaneLayout(layout(entry), origin(entry.get(List.of("origin"))))
                : parentTabs(entry, dropped);
            Map<TabId, TabRecord> records = tabs(entry, dropped);
            for (TabId tab : held.tabs())
                if (!records.containsKey(tab)) drop(dropped, "Dropping tab " + tab + " from the workspace state: it has no readable entry");
            WindowLayout layout = keeping(held, records::containsKey, role == Role.MAIN)
                .orElseThrow(() -> new IllegalArgumentException("a child window keeps no tab"));
            records.keySet().retainAll(Set.copyOf(layout.tabs()));
            int extendedState = UiState.number(entry, "extended_state").filter(state -> (state & ~6) == 0).orElse(0);
            return Optional.of(new WindowRecord(role, UiState.bounds(entry), extendedState, layout, records));""", 1),
    ("""    /** A window's tree from its flat nodes, with the active group and the maximize falling back alone. */""",
     """    /**
     * A main or full window's parent tabs in bar order, each a table with its id and its layout's keys; a bad entry, and
     * one with no tab beside others, is dropped alone, logged and recorded in {@code dropped}. The one
     * {@code active_parent_tab} names shows, else the first.
     */
    private static ParentTabs parentTabs(TomlTable window, List<String> dropped) {
        if (!(window.get(List.of("parent_tabs")) instanceof TomlArray entries)) throw new IllegalArgumentException("a window has no parent tabs");
        var read = new ArrayList<ParentTab>();
        for (int i = 0; i < entries.size(); i++) {
            try {
                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a parent tab entry is not a table");
                read.add(new ParentTab(ParentTabId.parse(string(entry, "id")), layout(entry)));
            } catch (RuntimeException bad) {
                drop(dropped, "Dropping a parent tab from the workspace state: " + bad.getMessage());
            }
        }
        if (read.isEmpty()) throw new IllegalArgumentException("a window keeps no parent tab");
        // Only a window's only parent tab may be empty (parent tabs spec 4): one with no tab beside others drops alone.
        List<ParentTab> holding = read.stream().filter(parent -> !parent.layout().isEmpty()).toList();
        List<ParentTab> kept = holding.isEmpty() ? List.of(read.getFirst()) : holding;
        for (ParentTab parent : read)
            if (!kept.contains(parent)) drop(dropped, "Dropping parent tab " + parent.id() + " from the workspace state: it holds no tab");
        ParentTabId active = parentTabId(window.get(List.of("active_parent_tab")))
            .filter(id -> kept.stream().anyMatch(parent -> parent.id().equals(id))).orElse(kept.getFirst().id());
        return new ParentTabs(kept, active);
    }

    /**
     * Where a pane window's panes came from (parent tabs spec 4); {@link #NO_ORIGIN} when the entry cannot be read, so
     * Bring Back takes them beside the main window's active pane (spec 5).
     */
    private static Origin origin(Object value) {
        try {
            if (!(value instanceof TomlTable table)) throw new IllegalArgumentException("a pane window has no origin");
            if (!(table.get(List.of("share")) instanceof Number share)) throw new IllegalArgumentException("an origin's share is not a number");
            return new Origin(ParentTabId.parse(string(table, "parent_tab")), GroupId.parse(string(table, "pane")),
                Side.valueOf(string(table, "side").toUpperCase(Locale.ROOT)), share.doubleValue());
        } catch (IllegalArgumentException unreadable) {
            return NO_ORIGIN;
        }
    }

    /**
     * {@code layout} with only the tabs {@code kept} keeps, as closing the others one by one would leave it (workspace tabs
     * spec 7.3's {@code remove}; parent tabs spec 4): a parent tab they all leave goes, and the one used before it shows
     * when it showed; a window they all leave keeps nothing, except the main window, whose shown parent tab stays as its
     * empty workspace. What a snapshot saves of a window, what the reader keeps of a window whose tabs lack entries, and
     * what a window restores (workspace plan 6a Rulings 14).
     *
     * @param layout a window's parent tabs, or a pane window's layout
     * @param kept the tabs that stay
     * @param main whether the window is the main window, whose workspace may be empty
     * @return what stays; empty when no tab does and the window is not the main window
     */
    public static Optional<WindowLayout> keeping(WindowLayout layout, Predicate<TabId> kept, boolean main) {
        return switch (layout) {
            case PaneLayout pane -> {
                Layout rest = keeping(pane.layout(), kept);
                yield rest.isEmpty() ? Optional.empty() : Optional.of(new PaneLayout(rest, pane.origin()));
            }
            case ParentTabs parents -> {
                // The shown parent tab goes last, so that a window every tab leaves keeps it as the empty workspace.
                var order = new ArrayList<>(parents.parentTabs());
                order.sort(Comparator.comparing(parent -> parent.id().equals(parents.active())));
                ParentTabs rest = parents;
                for (ParentTab parent : order) {
                    Layout left = keeping(parent.layout(), kept);
                    rest = left.isEmpty() && rest.parentTabs().size() > 1 ? rest.remove(parent.id()) : rest.replace(new ParentTab(parent.id(), left));
                }
                yield rest.isEmpty() && !main ? Optional.empty() : Optional.of(rest);
            }
        };
    }

    private static Layout keeping(Layout layout, Predicate<TabId> kept) {
        Layout rest = layout;
        for (TabId tab : layout.tabs()) if (!kept.test(tab)) rest = rest.remove(tab);
        return rest;
    }

    /** Every group a window holds: each parent tab's, or the pane window's. */
    private static List<GroupId> groupIds(WindowLayout layout) {
        return switch (layout) {
            case ParentTabs parents -> parents.parentTabs().stream().flatMap(parent -> parent.layout().groups().stream()).map(Group::id).toList();
            case PaneLayout pane -> pane.layout().groups().stream().map(Group::id).toList();
        };
    }

    /** The parent tabs a main or full window holds; none for a pane window. */
    private static List<ParentTabId> parentTabIds(WindowLayout layout) {
        return layout instanceof ParentTabs parents ? parents.parentTabs().stream().map(ParentTab::id).toList() : List.of();
    }

    /** A layout's tree from its flat nodes, with the active group and the maximize falling back alone. */""", 1),
    ("""    private static Optional<TabId> tabId(Object value) {""",
     """    private static Optional<ParentTabId> parentTabId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(ParentTabId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<TabId> tabId(Object value) {""", 1),
    ("""    private static final String CONTENT = "content:";""",
     """    private static final String CONTENT = "content:";
    /**
     * The origin of a pane window whose saved origin cannot be read: a parent tab and a pane no window holds, as their
     * name-based ids are never a random id's (workspace plan 6a Rulings 26).
     */
    static final Origin NO_ORIGIN = new Origin(new ParentTabId(UUID.nameUUIDFromBytes("jasper.origin:none".getBytes(StandardCharsets.UTF_8))),
        new GroupId(UUID.nameUUIDFromBytes("jasper.origin:none".getBytes(StandardCharsets.UTF_8))), Side.RIGHT, 0.5);""", 1),
    ("""    /** The file's text: the windows in order, each window's nodes parents first, then its tabs in workspace order. */
    static String encode(Snapshot snapshot) {
        var text = new StringBuilder("version = 1\\n");
        for (WindowRecord window : snapshot.windows()) {
            Layout layout = window.layout();
            text.append("\\n[[windows]]\\nrole = \\"").append(window.role() == Role.MAIN ? "main" : "child").append("\\"\\n");
            window.bounds().ifPresent(bounds -> text.append("x = ").append(bounds.x()).append("\\ny = ").append(bounds.y())
                .append("\\nwidth = ").append(bounds.width()).append("\\nheight = ").append(bounds.height()).append('\\n'));
            text.append("extended_state = ").append(window.extendedState())
                .append("\\nactive_group = \\"").append(layout.activeGroup().value())
                .append("\\"\\nmaximized = \\"").append(layout.maximized().map(id -> id.value().toString()).orElse("")).append("\\"\\n");
            appendNode(text, layout.root(), "", 1.0, "");
            for (TabId tab : layout.tabs()) appendTab(text, tab, window.tabs().get(tab));
        }
        return text.toString();
    }""",
     """    /**
     * The file's text: the windows in order; a main or full window's parent tabs in bar order, each with its nodes parents
     * first, or a pane window's origin and nodes; then the window's tabs in window order.
     */
    static String encode(Snapshot snapshot) {
        var text = new StringBuilder("version = 1\\n");
        for (WindowRecord window : snapshot.windows()) {
            String kind = window.role() == Role.MAIN ? "main" : window.layout() instanceof PaneLayout ? "pane" : "full";
            text.append("\\n[[windows]]\\nrole = \\"").append(kind).append("\\"\\n");
            window.bounds().ifPresent(bounds -> text.append("x = ").append(bounds.x()).append("\\ny = ").append(bounds.y())
                .append("\\nwidth = ").append(bounds.width()).append("\\nheight = ").append(bounds.height()).append('\\n'));
            text.append("extended_state = ").append(window.extendedState()).append('\\n');
            switch (window.layout()) {
                case ParentTabs parents -> {
                    text.append("active_parent_tab = \\"").append(parents.active()).append("\\"\\n");
                    for (ParentTab parent : parents.parentTabs()) {
                        text.append("\\n[[windows.parent_tabs]]\\nid = \\"").append(parent.id()).append("\\"\\n");
                        appendLayout(text, parent.layout(), "windows.parent_tabs.nodes");
                    }
                }
                case PaneLayout pane -> {
                    Origin origin = pane.origin();
                    text.append("origin = { parent_tab = \\"").append(origin.parentTab()).append("\\", pane = \\"").append(origin.pane())
                        .append("\\", side = \\"").append(origin.side().name().toLowerCase(Locale.ROOT)).append("\\", share = ")
                        .append(origin.share()).append(" }\\n");
                    appendLayout(text, pane.layout(), "windows.nodes");
                }
            }
            for (TabId tab : window.layout().tabs()) appendTab(text, tab, window.tabs().get(tab));
        }
        return text.toString();
    }

    /** A layout's active group and maximize, then its nodes, parents first, each under the array table {@code nodes}. */
    private static void appendLayout(StringBuilder text, Layout layout, String nodes) {
        text.append("active_group = \\"").append(layout.activeGroup().value())
            .append("\\"\\nmaximized = \\"").append(layout.maximized().map(id -> id.value().toString()).orElse("")).append("\\"\\n");
        appendNode(text, layout.root(), "", 1.0, "", nodes);
    }""", 1),
    ("""    private static void appendNode(StringBuilder text, Node node, String parent, double weight, String path) {""",
     """    private static void appendNode(StringBuilder text, Node node, String parent, double weight, String path, String nodes) {""", 1),
    ("""                text.append("\\n[[windows.nodes]]\\nid = \\"").append(id).append("\\"\\nkind = \\"split\\"\\norientation = \\"")""",
     """                text.append("\\n[[").append(nodes).append("]]\\nid = \\"").append(id).append("\\"\\nkind = \\"split\\"\\norientation = \\"")""", 1),
    ("""                    appendNode(text, child.node(), id, child.weight(), path.isEmpty() ? Integer.toString(i) : path + "." + i);""",
     """                    appendNode(text, child.node(), id, child.weight(), path.isEmpty() ? Integer.toString(i) : path + "." + i, nodes);""", 1),
    ("""            case Group group -> text.append("\\n[[windows.nodes]]\\nid = \\"").append(group.id().value())""",
     """            case Group group -> text.append("\\n[[").append(nodes).append("]]\\nid = \\"").append(group.id().value())""", 1)])
# The records hold a window's parent tabs or a pane window's layout and origin.
span(F, """    /**
     * One window as saved.
""", """            if (role == Role.CHILD && layout.isEmpty()) throw new IllegalArgumentException("Only the main window's workspace may be empty");
        }
    }
""", """    /**
     * One window as saved.
     *
     * @param role main or child
     * @param bounds its normal bounds; empty places it as a new window
     * @param extendedState its maximized bits only (2 and 4): a window never comes back minimized
     * @param layout what it holds (parent tabs spec 4): parent tabs for the main window and a full window, whose shown one
     *     is its active one; one layout and its origin for a pane window. Only the main window's may be empty
     * @param tabs a record for exactly each tab of the layout
     */
    public record WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, WindowLayout layout, Map<TabId, TabRecord> tabs) {
        /** Copies the records and checks them against the layout. */
        public WindowRecord {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(layout, "layout");
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Only the maximized bits are kept: " + extendedState);
            tabs = Map.copyOf(tabs);
            if (!tabs.keySet().equals(Set.copyOf(layout.tabs())))
                throw new IllegalArgumentException("A window's tab records name exactly its layout's tabs");
            if (role == Role.MAIN && !(layout instanceof ParentTabs)) throw new IllegalArgumentException("The main window holds parent tabs");
            if (role == Role.CHILD && layout instanceof ParentTabs parents && parents.isEmpty())
                throw new IllegalArgumentException("Only the main window's workspace may be empty");
        }
    }
""")
edit(F, [
    ("     * @param windows exactly one main window and any child windows; no group or tab in two windows",
     "     * @param windows exactly one main window and any child windows; no parent tab, group or tab in two windows", 1),
    ("""            var groups = new HashSet<GroupId>();
            var tabs = new HashSet<TabId>();
            for (WindowRecord window : ordered) {
                for (Group group : window.layout().groups())
                    if (!groups.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in two windows");
                for (TabId tab : window.layout().tabs())
                    if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two windows");
            }""",
     """            var groups = new HashSet<GroupId>();
            var tabs = new HashSet<TabId>();
            var parentTabs = new HashSet<ParentTabId>();
            for (WindowRecord window : ordered) {
                for (GroupId group : groupIds(window.layout()))
                    if (!groups.add(group)) throw new IllegalArgumentException("Group " + group + " is in two windows");
                for (TabId tab : window.layout().tabs())
                    if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two windows");
                // Parent tab ids stay unique across windows (workspace plan 7b Rulings 10).
                for (ParentTabId parent : parentTabIds(window.layout()))
                    if (!parentTabs.add(parent)) throw new IllegalArgumentException("Parent tab " + parent + " is in two windows");
            }""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/layout/ParentTabId.java", [(
    " * A parent tab's identity (parent tabs spec 4), unique in the application.",
    " * A parent tab's identity (parent tabs spec 4), unique in the application and kept in {@code workspace.toml}.", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java", [(
    "    /** The saved windows, splits, groups and tabs ({@code workspace.toml}). */",
    "    /** The saved windows, their parent tabs, splits, groups and tabs ({@code workspace.toml}). */", 1)])
PY
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.layout.*'`
Expected: PASS (`WorkspaceFileTest` 6, `WorkspaceFileReadTest` 5 more than before).

- [x] **Step 5: Commit**

```bash
git add -A jasper-app
git commit -F - <<'EOF'
Workspace tabs: workspace.toml keeps parent tabs and pane windows

A saved window holds what the window holds: the main window's and each
full window's parent tabs with the one that shows, or a pane window's
layout and origin. The reader drops a bad parent tab alone, lets a
parent tab its tabs leave go as closing them would, and falls back to
the first parent tab and to an origin no window holds; keeping takes a
window's tabs out the same way for the snapshot and the restore.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: What `workspace.toml` keeps of the open windows

> **Executed** (`6d7ef40a4`). History: not run again. Plan 8a's Task 2 moved `WindowManager.snapshot` onto `ParentTabs`
> and its tests from pop-outs and Bring Back to `moveToNewWindow` and Move to Main Window, and plan 8a's Task 4 gave them
> one-item panes. Its review's Minors 1-4 went into Task 5; Minors 5-7 are the final fix wave's (Rulings 40).

Spec 8 and 13, parent tabs spec 9: the snapshot is each window's place, what it holds (its parent tabs and the one that
shows, or a pane window's layout and origin) and, for each tab that can come back, what it needs; the rest are taken
out as closing them would (`WorkspaceFile.keeping`): a parent tab left with none goes, a child window left with none is
left out (plan 2 Rulings 20, Rulings 6 and 14). A rename, a new local directory, every layout change and every
parent-tab change tell the window's `onLayoutChanged` (Rulings 8 and 28), which Task 5 turns into a save.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java`, `workspace/TabHost.java`,
  `workspace/WindowContent.java`, `workspace/WindowManager.java`, `docs/design/theme-engine/migration.csv` (two rows)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSnapshotTest.java`

**Interfaces:**
- Consumes: `WorkspaceFile.Snapshot`, `WindowRecord`, `TabRecord.Terminal`, `Role`, `keeping` (Task 3); 7a's
  `WindowContent.windowLayout()`, `workspaceChanged(WorkspaceView, boolean)`, `newParentTab`, `activeParentTab`,
  `parentTabCount`; 7b's `WindowManager.popOutTab(Tab)`, `PaneLayout.origin()`; `JasperWindow.placement()` (Task 2).
- Produces: `public Optional<WorkspaceFile.Snapshot> WindowManager.snapshot()`; package
  `Optional<WorkspaceFile.TabRecord.Terminal> TerminalContent.record()`, `Runnable TerminalContent.onRenamed`,
  `Runnable WindowContent.onLayoutChanged`, `void WindowContent.layoutChanged()`; test fixture `TestWindows` (fields
  `pending`, `started`, `events`, `model`, `terminals`, `manager`; `TestWindows()`,
  `TestWindows(Function<Path, TerminalSession>)`, `launch()`).

- [x] **Step 1: Write the failing tests**

`TestWindows` is a window manager over stand-in frames for this plan's tests, which record where each shell started and
what the frames did.

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java`:

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

    /**
     * Runs the shells' launches the windows asked for so far, once the EDT has run what it was given before: a restored
     * tab starts at the end of its EDT turn (workspace plan 6a Rulings 31). Off the EDT.
     */
    void launch() throws Exception {
        DesktopTestSupport.edt(() -> { });
        while (!pending.isEmpty()) pending.remove().run();
    }
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
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.PaneLayout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8 and 13, parent tabs spec 9: what {@code workspace.toml} keeps of the open windows. */
class WorkspaceSnapshotTest {
    @TempDir Path folder;
    private TestWindows windows;

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private void windows() throws Exception { edt(() -> windows = new TestWindows()); }

    @Test void aSnapshotKeepsEachWindowsPlaceParentTabsPaneWindowAndTerminalTabs() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            Tab first = main.content().selectedTab();
            main.content().openTab(logs, null, Optional.of("zsh"));
            Tab tail = main.content().selectedTab();
            ((TerminalContent) tail.content()).rename("tail");
            main.content().split(Side.RIGHT);
            ParentTabId firstParent = main.content().activeParentTab();
            main.content().newParentTab(work);
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            ChildWindow full = windows.manager.openTerminalWindow(work).orElseThrow();
            ChildWindow pane = windows.manager.popOutTab(tail).orElseThrow();
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            assertThat(snapshot.windows()).hasSize(3);
            WorkspaceFile.WindowRecord saved = snapshot.main();
            assertThat(saved.role()).isEqualTo(WorkspaceFile.Role.MAIN);
            assertThat(saved.bounds()).contains(new UiState.Bounds(40, 60, 1400, 900));
            assertThat(saved.extendedState()).isEqualTo(6);
            assertThat(saved.layout()).as("its parent tabs in bar order, the one that shows, their splits, groups, order and selection")
                .isEqualTo(main.content().windowLayout());
            assertThat(((ParentTabs) saved.layout()).parentTabs()).extracting(ParentTab::id).first().isEqualTo(firstParent);
            assertThat(saved.tabs().get(first.id())).isEqualTo(new WorkspaceFile.TabRecord.Terminal("", work.toString(), ""));
            assertThat(snapshot.children()).extracting(WorkspaceFile.WindowRecord::layout)
                .as("a full window and a pane window, in the order they opened")
                .containsExactly(full.content().windowLayout(), pane.content().windowLayout());
            WorkspaceFile.WindowRecord popped = snapshot.children().get(1);
            assertThat(popped.role()).isEqualTo(WorkspaceFile.Role.CHILD);
            assertThat(((PaneLayout) popped.layout()).origin()).as("where its tab came from: its pane, which keeps the first tab")
                .isEqualTo(new Origin(firstParent, first.group().orElseThrow(), Side.RIGHT, 0.5));
            assertThat(popped.tabs().get(tail.id())).as("renamed, in its directory, with its profile")
                .isEqualTo(new WorkspaceFile.TabRecord.Terminal("tail", logs.toString(), "zsh"));
            assertThat(popped.bounds()).as("its frame has not said where it is").isEmpty();
        });
        windows.launch();
    }

    @Test void tabsThatCannotComeBackAreTakenOutAsClosingThemWouldLeaveTheirWindows() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            Tab terminal = main.content().selectedTab();
            ParentTabId first = main.content().activeParentTab();
            Tab notes = windows.model.tabs().open("dev.example.notes", "todo", "Todo", main.id(), new javax.swing.JPanel());
            // Jasper's own Errors tab, alone in a pane window (workspace plan 4b's app-owned tab).
            Tab errors = windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.moveToNewWindow();
            assertThat(windows.manager.children()).hasSize(1);
            main.content().newParentTab(folder);
            Tab other = main.content().selectedTab();
            Tab away = windows.model.tabs().open("dev.example.notes", "away", "Away", main.id(), new javax.swing.JPanel());
            main.content().closeTab(other);
            assertThat(main.content().parentTabCount()).as("the parent tab that shows holds a plugin's tab alone").isEqualTo(2);
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            var saved = (ParentTabs) snapshot.main().layout();
            assertThat(saved.parentTabs()).as("a parent tab holding only such tabs is left out").extracting(ParentTab::id).containsExactly(first);
            assertThat(saved.active()).as("and the one used before it shows, as closing its tabs would").isEqualTo(first);
            assertThat(saved.tabs()).as("a plugin's tab with no restorer is not saved").containsExactly(terminal.id());
            assertThat(snapshot.main().tabs()).containsOnlyKeys(terminal.id());
            assertThat(snapshot.children()).as("a pane window holding only Jasper's own tab is left out").isEmpty();
            assertThat(List.of(notes, away, errors)).as("taking them out of what is saved closes nothing").noneMatch(Tab::closed);
            terminal.close();
            assertThat(((ParentTabs) windows.manager.snapshot().orElseThrow().main().layout()).isEmpty())
                .as("the main window may be saved empty").isTrue();
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

Both match the ledger's pattern (the word `appearance` in an import, `new JasperWindow…`, `new javax.swing.JPanel`):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "TestWindows.java", "app.workspace", "TestWindows", T + "WorkspaceSnapshotTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 4; a window manager over stand-in frames")
ledger.add(T + "WorkspaceSnapshotTest.java", "app.workspace", "WorkspaceSnapshotTest", T + "WorkspaceSnapshotTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 4; what workspace.toml keeps of the open windows")
PY
```

- [x] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `snapshot()`.

- [x] **Step 3: What a terminal tab saves, and the signal that something saved changed**

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
    ("""    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus && view == parentTabs.active()) focusSelected();
    }
""", """    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus && view == parentTabs.active()) focusSelected();
        // Every parent tab's layout change and every parent-tab change ends here (workspace plan 6a Rulings 8 and 28).
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

- [x] **Step 4: The snapshot**

The window manager saves what each window holds (`windowLayout()`), taking out the tabs that cannot come back with Task
3's `keeping`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
M = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java"
edit(M, [
    ("import dev.jasper.app.layout.ParentTabId;\n",
     "import dev.jasper.app.layout.ParentTabId;\nimport dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.WindowLayout;\n"
     "import dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("import java.util.ArrayList;\n", "import java.util.ArrayList;\nimport java.util.HashMap;\n", 1),
    ("""    /**
     * A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. A""",
     """    /**
     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13; parent tabs spec 9): the main window, its full
     * windows and its pane windows, each with where it is, what it holds (its parent tabs and the one that shows, or a
     * pane window's layout and origin) and what each of its tabs needs to come back. A tab that cannot come back is taken
     * out first, as closing it would take it out ({@code WorkspaceFile.keeping}): a parent tab left with none goes, a
     * child window left with none is left out (workspace plan 2 Rulings 20), and the main window may be empty.
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
        var tabs = new HashMap<TabId, WorkspaceFile.TabRecord>();
        for (Tab tab : window.content().tabs()) tabRecord(tab).ifPresent(saved -> tabs.put(tab.id(), saved));
        Optional<WindowLayout> kept = WorkspaceFile.keeping(window.content().windowLayout(), tabs::containsKey, window.isMain());
        if (kept.isEmpty()) return Optional.empty();
        JasperWindow.Placement placement = window.placement();
        return Optional.of(new WorkspaceFile.WindowRecord(window.isMain() ? WorkspaceFile.Role.MAIN : WorkspaceFile.Role.CHILD,
            placement.bounds(), placement.extendedState(), kept.get(), tabs));
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

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceSnapshotTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.workspace.ParentTabsViewTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (the remote-directory test is skipped on Windows, where `/bin/sh` is not there).

- [x] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: what workspace.toml keeps of the open windows

The window manager's snapshot holds each window's place and what it
holds: its parent tabs and the one that shows, or a pane window's layout
and origin; and, for each terminal tab, its name, last local directory
and profile. Tabs that cannot come back are taken out as closing them
would, and a parent tab or a child window left with none goes. Renames,
directory changes, layout changes and parent-tab changes tell the
window that something saved changed.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: Saving within a second, and when a window closes

> **Executed** (`3f273675e`, with the controller's extras from the Task 2 and Task 4 reviews). History: not run again.
> Plan 8a's Task 2 moved `WorkspaceSavingTest` from pop-outs and Bring Back to Move Tab to New Window and Move to Main
> Window; `WorkspaceStore` is as it ran. Its review's findings are Task 6 below.

Spec 13: a change is saved within one second, on a virtual thread, and a window closing is saved at once; closing every
window (closing the main window, Quit, going to the background) saves them first and then nothing until they come back,
so closing their tabs never overwrites them (Rulings 9, 10 and 15). Every window kind is saved alike, a pane window's
pop-out and Bring Back and a parent tab's opening and showing included (Rulings 28). A snapshot or a write that fails
costs only that save (Rulings 22), and the store can keep a workspace aside as `workspace.toml.bad` for Task 6 (Rulings
13).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceSavingTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java`, `workspace/JasperWindow.java`,
  `docs/design/theme-engine/migration.csv` (three rows)

**Interfaces:**
- Consumes: `WindowManager.snapshot()`, `WindowContent.onLayoutChanged` (Task 4); `JasperWindow.placed` (Task 2);
  `WorkspaceFile.read`, `write`, `Loaded` (plan 2), `WindowRecord` over a `WindowLayout` (Task 3); `closeAll`'s
  closing-all point (plan 4a); 7a's `newParentTab`, `selectParentTab`, `activeParentTab`; 7b's `popOutTab(Tab)` and
  `bringBack(WindowContent)`.
- Produces: `public final class WorkspaceStore` with `public static final Duration DELAY` (one second), `public
  WorkspaceStore(Path file, Supplier<Optional<WorkspaceFile.Snapshot>> snapshot, BiFunction<Duration, Runnable, Runnable>
  schedule, Executor writer, Consumer<Path> setAside)`, `public WorkspaceFile.Loaded load()`, `public void changed()`,
  `public void setAside(WorkspaceFile.Snapshot windows)`, `public void save()`, `public void hold()`, `public boolean
  held()`, `public CompletableFuture<Void> written()` (never exceptional);
  `public void WindowManager.workspace(WorkspaceStore value)`; package `void WindowManager.changed()`.

- [x] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
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

    /** One parent tab of its own id holding {@code tab} alone. */
    private static ParentTabs only(TabId tab) {
        return ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(tab)));
    }

    /** A workspace of one main window holding one terminal tab in {@code directory}. */
    private static WorkspaceFile.Snapshot workspace(String directory) {
        TabId tab = TabId.random();
        return new WorkspaceFile.Snapshot(java.util.List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            only(tab), Map.of(tab, new WorkspaceFile.TabRecord.Terminal("", directory, "")))));
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

    /** A write that failed, or that the writer refused, leaves the windows unsaved: the same windows are written again. */
    @Test void aWorkspaceWhoseWriteFailedOrWasRefusedIsWrittenAgainUnchanged() throws Exception {
        store();
        current = Optional.of(workspace("/work"));
        refuseNext = true;
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        Files.createDirectory(file());
        edt(() -> store.save());
        write();
        assertThat(file()).as("that write failed").isDirectory();
        Files.delete(file());
        edt(() -> store.save());
        assertThat(writes).as("the same windows, still unsaved, are written again").hasSize(1);
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        edt(() -> store.save());
        assertThat(writes).as("written once they are, not again").isEmpty();
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
            WorkspaceFile.Role.CHILD, Optional.empty(), 0, only(away), Map.of(away, new WorkspaceFile.TabRecord.Terminal("", "/away", ""))))));
        String original = Files.readString(file()).replace("role = \"full\"", "role = \"side\"");
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
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
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

/**
 * Workspace tabs spec 13, parent tabs spec 9: what is saved, and when: within a second of each change, and at once when a
 * window closes.
 */
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

    /** The saved main window's shown parent tab's layout. */
    private Layout shown() { return ((ParentTabs) saved().main().layout()).activeParentTab().layout(); }

    @Test void everyChangeToWhatIsSavedIsSavedWithinASecond() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            second();
            assertThat(saved().main().layout()).as("the window opening").isEqualTo(main.content().windowLayout());
            Tab first = main.content().selectedTab();
            main.content().newTab(folder);
            second();
            assertThat(saved().main().layout().tabs()).as("a tab opening").hasSize(2);
            main.content().split(Side.RIGHT);
            second();
            assertThat(shown().groups()).as("a split").hasSize(2);
            main.content().workspace().resizeSplit(List.of(), List.of(0.25, 0.75));
            second();
            assertThat(shown()).as("a divider drag").isEqualTo(main.content().workspace().model());
            main.content().toggleMaximize();
            second();
            assertThat(shown().maximized()).as("a maximize").isPresent();
            main.content().toggleMaximize();
            second();
            main.content().selectTab(first);
            second();
            assertThat(shown().activeTab()).as("a selection").contains(first.id());
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
            ParentTabId shown = main.content().activeParentTab();
            main.content().newParentTab(folder);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs()).as("a parent tab opening").hasSize(2);
            main.content().selectParentTab(shown);
            second();
            assertThat(((ParentTabs) saved().main().layout()).active()).as("a parent tab showing").isEqualTo(shown);
            ParentTabId opened = ((ParentTabs) saved().main().layout()).parentTabs().get(1).id();
            main.content().parentTabs().reorder(shown, 1);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs().get(1).id()).as("a parent tab moving along its bar").isEqualTo(shown);
            main.content().closeParentTab(opened);
            second();
            assertThat(((ParentTabs) saved().main().layout()).parentTabs()).as("a parent tab closing").hasSize(1);
            ChildWindow pane = windows.manager.popOutTab(main.content().selectedTab()).orElseThrow();
            second();
            assertThat(saved().children()).as("a tab popping out into a pane window").singleElement()
                .satisfies(record -> assertThat(record.layout()).isEqualTo(pane.content().windowLayout()));
            windows.manager.bringBack(pane.content());
            assertThat(saved().children()).as("Bring Back: its window closing is saved at once").isEmpty();
            second();
            assertThat(saved().main().layout()).as("and where its tab came back, within the second").isEqualTo(main.content().windowLayout());
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
            main.content().newTab(folder);
            windows.manager.popOutTab(main.content().selectedTab()).orElseThrow();
            windows.manager.closeAll();
            assertThat(due).as("the save it waited for is done").isEmpty();
            assertThat(saved().children()).as("the full window and the pane window, saved before they closed").hasSize(2);
            assertThat(saved().main().layout().tabs()).hasSize(2);
            assertThat(store.held()).as("closing their tabs is not saved").isTrue();
        });
        windows.launch();
    }
}
```

- [x] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `WorkspaceStore` and `workspace(…)`.

- [x] **Step 3: The store**

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
import java.util.concurrent.atomic.AtomicReference;
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
 * logged and costs only that save: the next save writes the windows, changed or not. EDT only, except
 * {@link #written()}.
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
    /**
     * What the file holds, or the latest write asked for will put there: an equal snapshot is not written again. A write
     * that fails or that the writer refuses takes it back, so the next save writes the same windows again. Any thread.
     */
    private final AtomicReference<WorkspaceFile.Snapshot> lastWritten = new AtomicReference<>();
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
        lastWritten.set(null);
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
        if (taken.isEmpty() || taken.get().equals(lastWritten.get())) return;
        WorkspaceFile.Snapshot writing = taken.get();
        saved = writing;
        lastWritten.set(writing);
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
        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(target, writing), writer)
            .whenComplete((done, refused) -> { if (refused != null) unwritten(target, writing); });
    }

    private void write(Path target, WorkspaceFile.Snapshot writing) {
        try {
            WorkspaceFile.write(target, writing);
        } catch (IOException | RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the workspace to " + target, failure);
            unwritten(target, writing);
        }
    }

    /** {@code writing} did not reach {@code target}: when it was the file's latest, the next save writes those windows again. */
    private void unwritten(Path target, WorkspaceFile.Snapshot writing) {
        if (target.equals(file)) lastWritten.compareAndSet(writing, null);
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
    "New in workspace-tabs plan 6a Task 5; saves workspace.toml; no component")
ledger.add(T + "WorkspaceStoreTest.java", "app.workspace", "WorkspaceStoreTest", T + "WorkspaceStoreTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 5; the store's timing, order and hold")
ledger.add(T + "WorkspaceSavingTest.java", "app.workspace", "WorkspaceSavingTest", T + "WorkspaceSavingTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 5; what is saved and when")
PY
```

- [x] **Step 4: The window manager saves**

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

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceStoreTest' --tests 'dev.jasper.app.workspace.WorkspaceSavingTest' --tests 'dev.jasper.app.workspace.WorkspaceSnapshotTest' --tests 'dev.jasper.app.workspace.WindowManagerTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: save the windows within a second and when one closes

WorkspaceStore saves the window manager's snapshot one second after the
first change, with every change made meanwhile, a parent tab's and a
move between windows' included, and at once when a window closes;
writes run in order on the writer it is given. Closing every window
saves them first and holds saving until they come back.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The store's review: a refused write is logged, a save with nothing to save keeps the scheduled one

Task 5's review (the ledger; Rulings 33 to 36): a write the writer refuses is logged as one that fails is; `save()`
takes over the save a change scheduled only once it has windows to write, so a save asked for while the main window
opens loses nothing; an `Error` from the snapshot costs that save alone; the store's tests record its log instead of
printing it; and `TestWindows` says which of its methods runs off the EDT. `layoutChanged()` stays where Task 5's extra
put it (Rulings 36).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java` (class Javadoc `:21-24`; `save`
  `:123-145`; `writeLater` `:168-171`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java` (imports; fields `:34-39`; `store()`
  `:43-55`; new cases before `writesRunInTheOrderTheyWereAskedFor` and `aWriteThatFailsOrIsRefusedNeverStopsTheNextOne`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java` (class Javadoc `:19`)

**Interfaces:**
- Consumes: Task 5's `WorkspaceStore(Path, Supplier<Optional<WorkspaceFile.Snapshot>>, BiFunction<Duration, Runnable,
  Runnable>, Executor, Consumer<Path>)`, `changed()`, `save()`, `hold()`, `written()`.
- Produces: no new member; `save()`'s new rule (Rulings 34), which Task 8's restore relies on when it discards a window
  that restores nothing; `WorkspaceStoreTest`'s `warnings()` and `breaking`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceStoreTest.java"
edit(T, [
    ("import java.util.ArrayDeque;\n", "import java.util.ArrayDeque;\nimport java.util.ArrayList;\nimport java.util.List;\n", 1),
    ("import org.junit.jupiter.api.Test;\n",
     "import org.junit.jupiter.api.AfterEach;\nimport org.junit.jupiter.api.BeforeEach;\nimport org.junit.jupiter.api.Test;\n", 1),
    ("""    /** Thrown by the next snapshot when set, as a window that cannot say what it holds would. */
    private RuntimeException failing;
""", """    /** Thrown by the next snapshot when set, as a window that cannot say what it holds would. */
    private RuntimeException failing;
    /** Thrown by the next snapshot when set: an {@code Error}, as a bug deep in a window's layout would throw. */
    private Error breaking;
""", 1),
    ("""    private WorkspaceStore store;

    private Path file() { return dir.resolve("workspace.toml"); }
""", """    private WorkspaceStore store;
    /** What the store logged during the test: its warnings are asserted here, never printed. */
    private final List<java.util.logging.LogRecord> logged = new ArrayList<>();
    private final java.util.logging.Logger log = java.util.logging.Logger.getLogger(WorkspaceStore.class.getName());
    private final java.util.logging.Handler recorder = new java.util.logging.Handler() {
        @Override public void publish(java.util.logging.LogRecord record) { synchronized (logged) { logged.add(record); } }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean parents;

    @BeforeEach void record() {
        parents = log.getUseParentHandlers();
        log.setUseParentHandlers(false);
        log.addHandler(recorder);
    }

    @AfterEach void stopRecording() {
        log.removeHandler(recorder);
        log.setUseParentHandlers(parents);
    }

    /** The messages of the warnings the store logged, in order. */
    private List<String> warnings() {
        synchronized (logged) {
            return logged.stream().filter(record -> record.getLevel() == java.util.logging.Level.WARNING)
                .map(java.util.logging.LogRecord::getMessage).toList();
        }
    }

    private Path file() { return dir.resolve("workspace.toml"); }
""", 1),
    ("""        edt(() -> store = new WorkspaceStore(file(), () -> {
            if (failing != null) throw failing;
""", """        edt(() -> store = new WorkspaceStore(file(), () -> {
            if (failing != null) throw failing;
            if (breaking != null) throw breaking;
""", 1),
    ("""    @Test void writesRunInTheOrderTheyWereAskedFor() throws Exception {""",
     """    /** Workspace plan 6a Task 5's review: a save with nothing to save yet leaves the save a change scheduled in place. */
    @Test void aSaveWithNothingToSaveKeepsTheSaveAChangeScheduled() throws Exception {
        store();
        edt(() -> { store.changed(); store.save(); });
        assertThat(cancelled).as("no main window yet: the scheduled save stays").isZero();
        assertThat(due).hasSize(1);
        failing = new IllegalStateException("a window cannot say what it holds");
        edt(() -> store.save());
        assertThat(due).as("nor does a snapshot that fails take it").hasSize(1);
        failing = null;
        current = Optional.of(workspace("/work"));
        edt(() -> due.remove().run());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void writesRunInTheOrderTheyWereAskedFor() throws Exception {""", 1),
    ("""    @Test void aWriteThatFailsOrIsRefusedNeverStopsTheNextOne() throws Exception {""",
     """    /** Rulings 22, workspace plan 6a Task 5's review: an Error from a snapshot costs that save alone, logged. */
    @Test void aSnapshotThatThrowsAnErrorCostsOnlyThatSave() throws Exception {
        store();
        breaking = new AssertionError("a layout that breaks its own rule");
        edt(() -> store.save());
        assertThat(writes).isEmpty();
        assertThat(warnings()).containsExactly("Could not take the workspace's snapshot to save");
        breaking = null;
        current = Optional.of(workspace("/work"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    /** Workspace plan 6a Task 5's review: a write the writer refuses is logged as a failed one is, never dropped quietly. */
    @Test void aWriteTheWriterRefusesIsLogged() throws Exception {
        store();
        refuseNext = true;
        current = Optional.of(workspace("/work"));
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        assertThat(warnings()).as("logged with the file it was for").containsExactly("Could not save the workspace to " + file());
        synchronized (logged) {
            assertThat(logged.getLast().getThrown()).as("with why").hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        }
    }

    @Test void aWriteThatFailsOrIsRefusedNeverStopsTheNextOne() throws Exception {""", 1)])
T2 = "jasper-app/src/test/java/dev/jasper/app/workspace/TestWindows.java"
edit(T2, [(""" * launched when the test runs {@link #launch()}. EDT only.
""", """ * launched when the test runs {@link #launch()}. EDT only, but {@link #launch()}, which runs off it.
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceStoreTest'`
Expected: FAIL: `aSaveWithNothingToSaveKeepsTheSaveAChangeScheduled` (`[no main window yet: the scheduled save stays]
expected: 0 but was: 1`), `aSnapshotThatThrowsAnErrorCostsOnlyThatSave` (`java.lang.AssertionError: a layout that breaks
its own rule`), `aWriteTheWriterRefusesIsLogged` (`[logged with the file it was for]`, an empty list).

- [ ] **Step 3: Log a refused write; take the scheduled save only with windows to write; an `Error` costs one save**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
S = "jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceStore.java"
edit(S, [
    (""" * every save ({@link #hold()}) until the windows are restored ({@link #load()}). A snapshot or a write that fails is
 * logged and costs only that save: the next save writes the windows, changed or not. EDT only, except
 * {@link #written()}.
""", """ * every save ({@link #hold()}) until the windows are restored ({@link #load()}). A snapshot or a write that fails, or a
 * write the writer refuses, is logged and costs only that save: the next save writes the windows, changed or not. EDT
 * only, except {@link #written()}.
""", 1),
    ("""    /** Saves now, as a closing window does; a save a change scheduled is not needed any more. */
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
        if (taken.isEmpty() || taken.get().equals(lastWritten.get())) return;
""", """    /**
     * Saves now, as a closing window does; a save a change scheduled is not needed any more once this one has the
     * windows. With nothing to save yet (no main window open, or it opens), or a snapshot that fails, the scheduled save
     * stays.
     */
    public void save() {
        requireEdt();
        if (held) return;
        Optional<WorkspaceFile.Snapshot> taken;
        try {
            taken = snapshot.get();
        } catch (RuntimeException | Error failure) {
            // A window that cannot say what it holds costs this save, never the close or quit that asked for it.
            LOG.log(System.Logger.Level.WARNING, "Could not take the workspace's snapshot to save", failure);
            return;
        }
        if (taken.isEmpty()) return;
        if (pending != null) {
            Runnable cancel = pending;
            pending = null;
            cancel.run();
        }
        if (taken.get().equals(lastWritten.get())) return;
""", 1),
    ("""        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(target, writing), writer)
            .whenComplete((done, refused) -> { if (refused != null) unwritten(target, writing); });
""", """        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(target, writing), writer)
            .whenComplete((done, refused) -> {
                if (refused == null) return;
                // The writer refused it (it is shutting down, say): a write that never ran, logged as one that failed.
                LOG.log(System.Logger.Level.WARNING, "Could not save the workspace to " + target, refused);
                unwritten(target, writing);
            });
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceStoreTest' --tests 'dev.jasper.app.workspace.WorkspaceSavingTest' --tests 'dev.jasper.app.workspace.WorkspaceSnapshotTest'`
Expected: PASS (`WorkspaceStoreTest` 14).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app
git commit -F - <<'EOF'
Workspace tabs: a refused save is logged, and a save with nothing to save keeps the next

A write the writer refuses is logged as a failed one is. A save that finds
nothing to save yet, or a snapshot that fails, leaves the save a change
scheduled in place, and an Error from a snapshot costs that save alone.
The store's tests record its warnings instead of printing them.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: `workspace.toml` says window, tab, pane and item

Spec 12 and 3 (Rulings 32): a window saves its tabs in strip order with the one it shows (`shown_tab`,
`[[windows.tabs]]`), each tab its split layout and active pane (`active_pane`, `maximized`, `[[windows.tabs.nodes]]`),
each pane its one item (`kind = "pane"`, `item`), and the window's items follow (`[[windows.items]]`). The reader takes
the same words and drops what it drops today, a pane naming anything but one item's id (or none, for the empty
workspace) included. The records, `keeping` and the snapshot do not change; nor does `Group`, whose Javadoc says what
the file keeps of it.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java` (class Javadoc `:38-45`; `TabRecord`
  Javadoc `:55-81`; `WindowRecord` `:103-107`; `Snapshot` `:127`; `Loaded.Restore` `:173-179`; `read` `:211-216`;
  `Parsed` `:240`; `drop` `:260`; `parse` `:301`; `window` `:314-328`; `parentTabs` `:338-373`; `keeping` `:376-389`;
  `groupIds`, `parentTabIds` `:406-411`; `layout` `:416-437`; `node` `:441-456`; `first` `:467`; `tabs` `:474-489`;
  `tab` `:498-502`; `tabId` `:539-546`, which goes; `encode` `:565-580`; `appendLayout` `:586-588`; `appendNode`
  `:613-616`; `appendTab` `:621`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/Group.java` (class Javadoc `:9`)
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java` (the expected texts, the class
  Javadoc, one case's name), `WorkspaceFileReadTest.java` (the class Javadoc, the cases that edit the text, and a new
  case before `anAbsentFileStartsFreshAndCreatesNothing`)

**Interfaces:**
- Consumes: Task 3's `WorkspaceFile.read(Path)`, `write(Path, Snapshot)`, `encode(Snapshot)`, `WindowRecord`, `Snapshot`,
  `Loaded`, `keeping(ParentTabs, Predicate<TabId>, boolean)` (plan 8a's form).
- Produces: the file's text as `WorkspaceFileTest.writesSpecEightsWorkspaceWithAFullWindowExactly` pins it, which Tasks
  8 and 9 and plan 6b write and read through the codec; no member changes.

- [ ] **Step 1: Write the failing tests**

The two texts the writer must write, and every case that edits a written text, take the file's new words; a file in
Task 3's words is set aside:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit, regex
P = "jasper-app/src/test/java/dev/jasper/app/persistence/"
F = P + "WorkspaceFileTest.java"
# The file's words are the spec's (Rulings 32): items under [[windows.items]], tabs under [[windows.tabs]], a pane's one item.
regex(F, r"\[\[windows\.tabs\]\]", "[[windows.items]]", 9)
regex(F, r"\[\[windows\.parent_tabs\]\]", "[[windows.tabs]]", 7)
regex(F, r"\[\[windows\.parent_tabs\.nodes\]\]", "[[windows.tabs.nodes]]", 13)
regex(F, r"active_parent_tab = ", "shown_tab = ", 5)
regex(F, r"active_group = ", "active_pane = ", 7)
regex(F, r'kind = "group"', 'kind = "pane"', 10)
regex(F, r'tabs = \["([0-9a-f-]+)"\]\n( *)selected = "\1"', r'item = "\1"', 9)
regex(F, r'tabs = \[\]\n( *)selected = ""', 'item = ""', 1)
edit(F, [
    ("/** Workspace tabs spec 8 and parent tabs spec 4 and 9: the workspace file's values and its exact text. */\n",
     "/** Workspace tabs spec 8, tabs and panes spec 4 and 12: the workspace file's values and its exact text, in the spec's words. */\n", 1),
    ("    @Test void theEmptyMainWorkspaceWritesItsParentTabWithAGroupWithNoTabs() {\n",
     "    @Test void theEmptyMainWorkspaceWritesItsTabWithAPaneThatNamesNoItem() {\n", 1)])
R = P + "WorkspaceFileReadTest.java"
edit(R, [
    (""" * Workspace tabs spec 8 and 17, parent tabs spec 4 and 9: reading the workspace file back, dropping bad entries alone,
 * setting a bad file aside.
""", """ * Workspace tabs spec 8 and 17, tabs and panes spec 4 and 12: reading the workspace file back, dropping bad entries alone,
 * setting a bad file aside.
""", 1),
    ("""        text = replaceOnce(text, "weight = 0.5\\ntabs = [\\"" + id(tab(1)), "weight = 3\\ntabs = [\\"" + id(tab(1)));
        text = replaceOnce(text, "weight = 0.5\\ntabs = [\\"" + id(tab(2)), "weight = 1.0\\ntabs = [\\"" + id(tab(2)));
""", """        text = replaceOnce(text, "weight = 0.5\\nitem = \\"" + id(tab(1)), "weight = 3\\nitem = \\"" + id(tab(1)));
        text = replaceOnce(text, "weight = 0.5\\nitem = \\"" + id(tab(2)), "weight = 1.0\\nitem = \\"" + id(tab(2)));
""", 1),
    ("""        assertThat(read(replaceOnce(text, "active_group = \\"" + g2 + "\\"", "active_group = \\"nope\\""))).as("the first group; the maximize no longer names it")
""", """        assertThat(read(replaceOnce(text, "active_pane = \\"" + g2 + "\\"", "active_pane = \\"nope\\""))).as("the first pane; the maximize no longer names it")
""", 1),
    ("""        assertThat(read(replaceOnce(text, "selected = \\"" + id(tab(2)) + "\\"", "selected = \\"" + id(tab(9)) + "\\""))).as("the pane's one tab")
            .isEqualTo(clean(snapshot()));
""", "", 1),
    ("""        String second = "\\n[[windows.tabs]]\\nid = \\"" + id(tab(2))""", """        String second = "\\n[[windows.items]]\\nid = \\"" + id(tab(2))""", 1),
    ("""        String split = "kind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"\\"\\nweight = 1.0\\n\\n[[windows.parent_tabs.nodes]]\\nid = \\"" + g3 + "\\"";
        String g4Node = "id = \\"" + g4 + "\\"\\nkind = \\"group\\"\\nparent = \\"" + WorkspaceFile.splitId("") + "\\"";
        String g4Weight = "weight = 0.5\\ntabs = [\\"" + t4 + "\\"]";
        String g3Tabs = "tabs = [\\"" + t3 + "\\"]";
        for (String[] change : new String[][] {
                {"role = \\"full\\"", "role = \\"side\\""},
                {"role = \\"full\\"", "role = \\"pane\\""},
                {split, split.replace("kind = \\"split\\"", "kind = \\"pane\\"")},
                {split, split.replace("horizontal", "diagonal")},
                {g4Weight, g4Weight.replace("0.5", "-1.0")},
                {g4Weight, g4Weight.replace("0.5", "\\"half\\"")},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"group\\"\\nparent = \\"\\""},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"group\\"\\nparent = \\"" + g3 + "\\""},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"group\\"\\nparent = \\"00000000-0000-4000-9000-000000000099\\""},
                {g3Tabs, "tabs = [\\"" + t3 + "\\", \\"" + t3 + "\\"]"},
                {g3Tabs, "tabs = \\"" + t3 + "\\""},
                {g3Tabs, "tabs = [\\"three\\"]"},
                {g3Tabs + "\\nselected = \\"" + t3 + "\\"", "tabs = []\\nselected = \\"\\""},
                {"id = \\"" + g3 + "\\"", "id = \\"group-three\\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(mainOnly);
        // After the tabs, these nodes still join the full window's last parent tab.
        String cycle = "\\n[[windows.parent_tabs.nodes]]\\nid = \\"x\\"\\nkind = \\"split\\"\\norientation = \\"vertical\\"\\nparent = \\"y\\"\\nweight = 0.5\\n"
            + "\\n[[windows.parent_tabs.nodes]]\\nid = \\"y\\"\\nkind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"x\\"\\nweight = 0.5\\n";
""", """        String split = "kind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"\\"\\nweight = 1.0\\n\\n[[windows.tabs.nodes]]\\nid = \\"" + g3 + "\\"";
        String g4Node = "id = \\"" + g4 + "\\"\\nkind = \\"pane\\"\\nparent = \\"" + WorkspaceFile.splitId("") + "\\"";
        String g4Weight = "weight = 0.5\\nitem = \\"" + t4 + "\\"";
        String g3Item = "item = \\"" + t3 + "\\"";
        for (String[] change : new String[][] {
                {"role = \\"full\\"", "role = \\"side\\""},
                {"role = \\"full\\"", "role = \\"pane\\""},
                {split, split.replace("kind = \\"split\\"", "kind = \\"group\\"")},
                {split, split.replace("horizontal", "diagonal")},
                {g4Weight, g4Weight.replace("0.5", "-1.0")},
                {g4Weight, g4Weight.replace("0.5", "\\"half\\"")},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"pane\\"\\nparent = \\"\\""},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"pane\\"\\nparent = \\"" + g3 + "\\""},
                {g4Node, "id = \\"" + g4 + "\\"\\nkind = \\"pane\\"\\nparent = \\"00000000-0000-4000-9000-000000000099\\""},
                {g3Item, "item = [\\"" + t3 + "\\"]"},
                {g3Item, "item = 3"},
                {g3Item, "item = \\"three\\""},
                {g3Item, "item = \\"\\""},
                {"id = \\"" + g3 + "\\"", "id = \\"pane-three\\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(mainOnly);
        // After the items, these nodes still join the full window's last tab.
        String cycle = "\\n[[windows.tabs.nodes]]\\nid = \\"x\\"\\nkind = \\"split\\"\\norientation = \\"vertical\\"\\nparent = \\"y\\"\\nweight = 0.5\\n"
            + "\\n[[windows.tabs.nodes]]\\nid = \\"y\\"\\nkind = \\"split\\"\\norientation = \\"horizontal\\"\\nparent = \\"x\\"\\nweight = 0.5\\n";
""", 1),
    ("""        assertThat(text.split(Pattern.quote(id(tab(5))), -1)).hasSize(4);
""", """        assertThat(text.split(Pattern.quote(id(tab(5))), -1)).as("its pane's item and its entry").hasSize(3);
""", 1),
    ("""        String active = "active_parent_tab = \\"" + parentTabId(2).value() + "\\"";
        assertThat(read(replaceOnce(text, active, "active_parent_tab = \\"nope\\""))).as("a choice that falls back to the first, not a drop")
""", """        String active = "shown_tab = \\"" + parentTabId(2).value() + "\\"";
        assertThat(read(replaceOnce(text, active, "shown_tab = \\"nope\\""))).as("a choice that falls back to the first, not a drop")
""", 1),
    ("""        assertThat(read(replaceOnce(text, "tabs = [\\"" + t1 + "\\"]\\nselected = \\"" + t1 + "\\"", "tabs = []\\nselected = \\"\\"")))
""", """        assertThat(read(replaceOnce(text, "item = \\"" + t1 + "\\"", "item = \\"\\"")))
""", 1),
    ("""        assertThat(text.split(Pattern.quote(g2), -1)).as("the second parent tab's active group and its group").hasSize(3);
        assertThat(text.split(Pattern.quote(t2), -1)).as("its group's tabs and selection, and the tab's entry").hasSize(4);
""", """        assertThat(text.split(Pattern.quote(g2), -1)).as("the second tab's active pane and its pane").hasSize(3);
        assertThat(text.split(Pattern.quote(t2), -1)).as("its pane's item and the item's entry").hasSize(3);
""", 1),
    ("""    /**
     * Review Focus 4 (workspace plan 8a Rulings 2): a pane holds one tab, so a parent tab whose pane lists two is a bad
     * entry, dropped alone, and the original is kept aside.
     */
    @Test void aPaneListingTwoTabsDropsItsParentTabAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t2 = id(tab(2));
        String twoTabs = replaceOnce(text, "tabs = [\\"" + t2 + "\\"]", "tabs = [\\"" + t2 + "\\", \\"" + id(tab(9)) + "\\"]");
        assertThat(read(twoTabs)).as("the main window keeps its first parent tab, which shows")
""", """    /**
     * Workspace plan 6a Rulings 32 (plan 8a Rulings 2): a pane names its one item, so a tab whose pane names a list of
     * items is a bad entry, dropped alone, and the original is kept aside.
     */
    @Test void aPaneNamingAListOfItemsDropsItsTabAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t2 = id(tab(2));
        String twoTabs = replaceOnce(text, "item = \\"" + t2 + "\\"", "item = [\\"" + t2 + "\\", \\"" + id(tab(9)) + "\\"]");
        assertThat(read(twoTabs)).as("the main window keeps its first tab, which shows")
""", 1)])
edit(R, [("""    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {
""", """    /**
     * Workspace plan 6a Rulings 32: a file in the parent-tabs words Task 3 wrote (a window's {@code parent_tabs}, its
     * items under {@code tabs}) holds no tab this build reads, so it is set aside and starts fresh, never misread.
     */
    @Test void aFileInTheParentTabsWordsIsSetAside() throws Exception {
        String old = WorkspaceFile.encode(parentTabs())
            .replace("[[windows.tabs]]", "[[windows.parent_tabs]]")
            .replace("[[windows.tabs.nodes]]", "[[windows.parent_tabs.nodes]]")
            .replace("[[windows.items]]", "[[windows.tabs]]")
            .replace("shown_tab = ", "active_parent_tab = ")
            .replace("active_pane = ", "active_group = ");
        assertThat(read(old)).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }

    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.WorkspaceFile*'`
Expected: FAIL: 13 cases, among them `writesSpecEightsWorkspaceWithAFullWindowExactly` (the text still says
`active_parent_tab`, `[[windows.parent_tabs]]`, `kind = "group"`, `tabs = [...]` and `selected`),
`aFileInTheParentTabsWordsIsSetAside` (`expected: Fresh[…] but was: Restore[…]`: the old reader keeps what it reads),
`aPaneNamingAListOfItemsDropsItsTabAlone` and `boundsAndTheChoicesFallBackAlone` (an anchor the old text lacks:
`[occurrences of …]`).

- [ ] **Step 3: The writer and the reader in the spec's words**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "persistence/WorkspaceFile.java", [
    (""" * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8; parent tabs spec 4 and
 * 9): each Jasper window's kind, bounds and maximized state, its parent tabs, and what each of its tabs needs to come
 * back. {@link #write} writes it atomically; {@link #read} never
 * throws: it drops bad tab, parent tab and window entries alone, keeping a copy of the file as {@code workspace.toml.bad},
 * and sets an unreadable file aside under that name. Stateless: the caller chooses the thread; the values are
 * immutable.
 */""",
     """ * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8; tabs and panes spec 12):
 * each Jasper window's kind, bounds and maximized state, its tabs in strip order with the one it shows, each tab's split
 * layout of panes with its active pane, each pane's one item, and what each item needs to come back. The file says
 * window, tab, pane and item, as the spec does; the code calls a tab a parent tab, a pane a group and an item a tab
 * (workspace plan 8a Rulings 1). {@link #write} writes it atomically; {@link #read} never throws: it drops bad item, tab
 * and window entries alone, keeping a copy of the file as {@code workspace.toml.bad}, and sets an unreadable file aside
 * under that name. Stateless: the caller chooses the thread; the values are immutable.
 */""", 1),
    ("""    /** What a tab needs to come back (spec 13): a terminal's title, directory and profile, or a plugin's restore state. */""",
     """    /** What an item needs to come back (spec 13): a terminal's title, directory and profile, or a plugin's restore state. */""", 1),
    ("""         * A terminal tab ({@code kind = "terminal"}).""",
     """         * A terminal ({@code kind = "terminal"}).""", 1),
    ("""         * A plugin's content tab ({@code kind = "content:<plugin id>:<restorer kind>"}).""",
     """         * A plugin's view ({@code kind = "content:<plugin id>:<restorer kind>"}).""", 1),
    ("""     * @param layout what it holds (tabs and panes spec 4): its parent tabs, whose shown one is its active one; only the
     *     main window's may be empty
     * @param tabs a record for exactly each tab of the layout""",
     """     * @param layout what it holds (tabs and panes spec 4): its tabs, whose shown one is its active one; only the main
     *     window's may be empty
     * @param tabs a record for exactly each item of the layout""", 1),
    ("""     * @param windows exactly one main window and any child windows; no parent tab, group or tab in two windows""",
     """     * @param windows exactly one main window and any child windows; no tab, pane or item in two windows""", 1),
    ("""         * @param keptAside where the original file was copied ({@code workspace.toml.bad} beside it) because a window, a
         *     parent tab or a tab entry was dropped, so the caller can tell the user and a later save does not lose the
         *     dropped text for good; empty when nothing was dropped or the copy failed""",
     """         * @param keptAside where the original file was copied ({@code workspace.toml.bad} beside it) because a window, a
         *     tab or an item entry was dropped, so the caller can tell the user and a later save does not lose the dropped
         *     text for good; empty when nothing was dropped or the copy failed""", 1),
    ("""         * The saved workspace, its bad entries dropped. A main window whose only parent tab is empty restores the empty
         * workspace.""",
     """         * The saved workspace, its bad entries dropped. A main window whose only tab is empty restores the empty
         * workspace.""", 1),
    ("""     * Rulings 13 and 14). A bad tab entry, or a tab a group lists without one, is dropped as {@link Layout#remove(TabId)}
     * drops it, and a parent tab it leaves with no tab goes ({@link #keeping(ParentTabs, Predicate, boolean)}); a bad
     * parent tab or window entry is dropped; bounds and choices fall back alone. When a window, a
     * parent tab or a tab entry was dropped, the original file is copied to {@code workspace.toml.bad} beside it,""",
     """     * Rulings 13 and 14). A bad item entry, or an item a pane names without one, is dropped as
     * {@link Layout#remove(TabId)} drops it, and a tab it leaves with no item goes
     * ({@link #keeping(ParentTabs, Predicate, boolean)}); a bad tab or window entry is dropped; bounds and choices fall
     * back alone. When a window, a tab or an item entry was dropped, the original file is copied to
     * {@code workspace.toml.bad} beside it,""", 1),
    ("""    /** What {@link #parse} read, and whether it dropped any window, parent tab or tab entry. */""",
     """    /** What {@link #parse} read, and whether it dropped any window, tab or item entry. */""", 1),
    ("""    /** Records a dropped window, parent tab or tab entry and logs it. */""",
     """    /** Records a dropped window, tab or item entry and logs it. */""", 1),
    ("""                drop(dropped, "Dropping a window whose parent tabs, groups or tabs an earlier window holds");""",
     """                drop(dropped, "Dropping a window whose tabs, panes or items an earlier window holds");""", 1),
    ("""     * One window entry with its bad tabs dropped; empty when the entry itself is bad. Every drop is logged and recorded in
     * {@code dropped}.""",
     """     * One window entry with its bad items dropped; empty when the entry itself is bad. Every drop is logged and recorded in
     * {@code dropped}.""", 1),
    ("""                if (!records.containsKey(tab)) drop(dropped, "Dropping tab " + tab + " from the workspace state: it has no readable entry");
            ParentTabs layout = keeping(held, records::containsKey, role == Role.MAIN)
                .orElseThrow(() -> new IllegalArgumentException("a child window keeps no tab"));""",
     """                if (!records.containsKey(tab)) drop(dropped, "Dropping item " + tab + " from the workspace state: it has no readable entry");
            ParentTabs layout = keeping(held, records::containsKey, role == Role.MAIN)
                .orElseThrow(() -> new IllegalArgumentException("a child window keeps no item"));""", 1),
    ("""                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a tab entry is not a table");
                TabId id = TabId.parse(string(entry, "id"));""",
     """                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("an item entry is not a table");
                TabId id = TabId.parse(string(entry, "id"));""", 1),
    ("""     * A main or full window's parent tabs in bar order, each a table with its id and its layout's keys; a bad entry, one
     * repeating an earlier entry's parent tab, group or tab id, and one with no tab beside others, is dropped alone,
     * logged and recorded in {@code dropped}. The one {@code active_parent_tab} names shows, else the first.
     */
    private static ParentTabs parentTabs(TomlTable window, List<String> dropped) {
        if (!(window.get(List.of("parent_tabs")) instanceof TomlArray entries)) throw new IllegalArgumentException("a window has no parent tabs");""",
     """     * A window's tabs in strip order, each a table with its id and its layout's keys; a bad entry, one repeating an
     * earlier entry's tab, pane or item id, and one with no item beside others, is dropped alone, logged and recorded in
     * {@code dropped}. The one {@code shown_tab} names shows, else the first.
     */
    private static ParentTabs parentTabs(TomlTable window, List<String> dropped) {
        if (!(window.get(List.of("tabs")) instanceof TomlArray entries)) throw new IllegalArgumentException("a window has no tabs");""", 1),
    ("""                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a parent tab entry is not a table");""",
     """                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a tab entry is not a table");""", 1),
    ("""                // As with two windows, the later of two parent tabs whose ids clash is the one dropped (Rulings 26).""",
     """                // As with two windows, the later of two tabs whose ids clash is the one dropped (workspace plan 6a Rulings 26).""", 1),
    ("""                    throw new IllegalArgumentException("parent tab " + parent.id() + " repeats an id an earlier parent tab holds");""",
     """                    throw new IllegalArgumentException("tab " + parent.id() + " repeats an id an earlier tab holds");""", 1),
    ("""                drop(dropped, "Dropping a parent tab from the workspace state: " + bad.getMessage());""",
     """                drop(dropped, "Dropping a tab from the workspace state: " + bad.getMessage());""", 1),
    ("""        if (read.isEmpty()) throw new IllegalArgumentException("a window keeps no parent tab");
        // Only a window's only parent tab may be empty (parent tabs spec 4): one with no tab beside others drops alone.""",
     """        if (read.isEmpty()) throw new IllegalArgumentException("a window keeps no tab");
        // Only a window's only tab may be empty (tabs and panes spec 4): one with no item beside others drops alone.""", 1),
    ("""            if (!kept.contains(parent)) drop(dropped, "Dropping parent tab " + parent.id() + " from the workspace state: it holds no tab");
        ParentTabId active = parentTabId(window.get(List.of("active_parent_tab")))""",
     """            if (!kept.contains(parent)) drop(dropped, "Dropping tab " + parent.id() + " from the workspace state: it holds no item");
        ParentTabId active = parentTabId(window.get(List.of("shown_tab")))""", 1),
    ("""     * {@code layout} with only the tabs {@code kept} keeps, as closing the others one by one would leave it (workspace tabs
     * spec 7.3's {@code remove}; parent tabs spec 4): a parent tab they all leave goes, and the one used before it shows
     * when it showed; a window they all leave keeps nothing, except the main window, whose shown parent tab stays as its
     * empty workspace. What a snapshot saves of a window, what the reader keeps of a window whose tabs lack entries, and
     * what a window restores (workspace plan 6a Rulings 14).
     *
     * @param layout a window's parent tabs
     * @param kept the tabs that stay""",
     """     * {@code layout} with only the items {@code kept} keeps, as closing the others one by one would leave it (workspace
     * tabs spec 7.3's {@code remove}; tabs and panes spec 4): a tab they all leave goes, and the one used before it shows
     * when it showed; a window they all leave keeps nothing, except the main window, whose shown tab stays as its empty
     * workspace. What a snapshot saves of a window, what the reader keeps of a window whose items lack entries, and what
     * a window restores (workspace plan 6a Rulings 14).
     *
     * @param layout a window's tabs
     * @param kept the items that stay""", 1),
    ("""     * @return what stays; empty when no tab does and the window is not the main window""",
     """     * @return what stays; empty when no item does and the window is not the main window""", 1),
    ("""        // The shown parent tab goes last, so that a window every tab leaves keeps it as the empty workspace.""",
     """        // The shown tab goes last, so that a window every item leaves keeps it as the empty workspace.""", 1),
    ("""    /** Every group a window holds: each parent tab's. */""",
     """    /** Every pane a window holds: each tab's. */""", 1),
    ("""    /** The parent tabs a window holds. */""",
     """    /** The tabs a window holds. */""", 1),
    ("""    /** A layout's tree from its flat nodes, with the active group and the maximize falling back alone. */
    private static Layout layout(TomlTable window) {
        if (!(window.get(List.of("nodes")) instanceof TomlArray nodes)) throw new IllegalArgumentException("a window or parent tab has no nodes");""",
     """    /** A tab's tree from its flat nodes, with the active pane and the maximize falling back alone. */
    private static Layout layout(TomlTable tab) {
        if (!(tab.get(List.of("nodes")) instanceof TomlArray nodes)) throw new IllegalArgumentException("a tab has no nodes");""", 1),
    ("""            else throw new IllegalArgumentException("a window or parent tab has two root nodes");
        }
        if (root == null) throw new IllegalArgumentException("a window or parent tab has no root node");""",
     """            else throw new IllegalArgumentException("a tab has two root nodes");
        }
        if (root == null) throw new IllegalArgumentException("a tab has no root node");""", 1),
    ("""        if (reached.size() != byId.size()) throw new IllegalArgumentException("a node is not under the root of its window or parent tab");
        Layout plain = new Layout(tree, first(tree), Optional.empty());
        Layout active = groupId(window.get(List.of("active_group"))).filter(id -> plain.group(id).isPresent()).map(plain::activate).orElse(plain);
        return groupId(window.get(List.of("maximized"))).filter(active.activeGroup()::equals).map(active::toggleMaximize).orElse(active);""",
     """        if (reached.size() != byId.size()) throw new IllegalArgumentException("a node is not under the root of its tab");
        Layout plain = new Layout(tree, first(tree), Optional.empty());
        Layout active = groupId(tab.get(List.of("active_pane"))).filter(id -> plain.group(id).isPresent()).map(plain::activate).orElse(plain);
        return groupId(tab.get(List.of("maximized"))).filter(active.activeGroup()::equals).map(active::toggleMaximize).orElse(active);""", 1),
    ("""        if (depth > MAX_DEPTH) throw new IllegalArgumentException("a window's or parent tab's tree is deeper than " + MAX_DEPTH);""",
     """        if (depth > MAX_DEPTH) throw new IllegalArgumentException("a tab's tree is deeper than " + MAX_DEPTH);""", 1),
    ("""        if ("group".equals(kind)) {
            if (children.containsKey(id)) throw new IllegalArgumentException("group " + id + " is a parent");
            if (!(table.get(List.of("tabs")) instanceof TomlArray list)) throw new IllegalArgumentException("group " + id + " has no tab list");
            var tabs = new ArrayList<TabId>();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof String tab)) throw new IllegalArgumentException("group " + id + " lists a tab that is not a string");
                tabs.add(TabId.parse(tab));
            }
            Optional<TabId> selected = tabId(table.get(List.of("selected"))).filter(tabs::contains).or(() -> tabs.stream().findFirst());
            return new Group(GroupId.parse(id), tabs, selected);
        }""",
     """        if ("pane".equals(kind)) {
            if (children.containsKey(id)) throw new IllegalArgumentException("pane " + id + " is a parent");
            // A pane holds one item (tabs and panes spec 2), or none in the main window's empty workspace.
            if (!(table.get(List.of("item")) instanceof String item)) throw new IllegalArgumentException("pane " + id + " names no item");
            List<TabId> held = item.isEmpty() ? List.of() : List.of(TabId.parse(item));
            return new Group(GroupId.parse(id), held, held.stream().findFirst());
        }""", 1),
    ("""    /** The first group in tree order. */""",
     """    /** The first pane in tree order. */""", 1),
    ("""     * A window's tab entries by id; a bad entry is dropped, logged and recorded in {@code dropped}, and the first entry for
     * an id wins.
     */
    private static Map<TabId, TabRecord> tabs(TomlTable window, List<String> dropped) {
        var records = new HashMap<TabId, TabRecord>();
        if (!(window.get(List.of("tabs")) instanceof TomlArray entries)) return records;""",
     """     * A window's item entries by id; a bad entry is dropped, logged and recorded in {@code dropped}, and the first entry
     * for an id wins.
     */
    private static Map<TabId, TabRecord> tabs(TomlTable window, List<String> dropped) {
        var records = new HashMap<TabId, TabRecord>();
        if (!(window.get(List.of("items")) instanceof TomlArray entries)) return records;""", 1),
    ("""                if (records.putIfAbsent(id, parsed) != null) throw new IllegalArgumentException("tab " + id + " is listed twice");
            } catch (RuntimeException bad) {
                drop(dropped, "Dropping a tab entry from the workspace state: " + bad.getMessage());""",
     """                if (records.putIfAbsent(id, parsed) != null) throw new IllegalArgumentException("item " + id + " is listed twice");
            } catch (RuntimeException bad) {
                drop(dropped, "Dropping an item entry from the workspace state: " + bad.getMessage());""", 1),
    ("""        if (!kind.startsWith(CONTENT)) throw new IllegalArgumentException("unknown tab kind " + kind);""",
     """        if (!kind.startsWith(CONTENT)) throw new IllegalArgumentException("unknown item kind " + kind);""", 1),
    ("""        if (!(entry.get(List.of("state")) instanceof TomlTable table)) throw new IllegalArgumentException("a content tab has no state table");""",
     """        if (!(entry.get(List.of("state")) instanceof TomlTable table)) throw new IllegalArgumentException("a plugin's item has no state table");""", 1),
    ("""    private static Optional<TabId> tabId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(TabId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

""",
     """""", 1),
    ("""     * The file's text: the windows in order; each window's parent tabs in bar order, each with its nodes parents first;
     * then the window's tabs in window order.
     */""",
     """     * The file's text: the windows in order; each window's tabs in strip order, each with its nodes, parents first; then
     * the window's items in window order.
     */""", 1),
    (r"""            text.append("active_parent_tab = \"").append(window.layout().active()).append("\"\n");
            for (ParentTab parent : window.layout().parentTabs()) {
                text.append("\n[[windows.parent_tabs]]\nid = \"").append(parent.id()).append("\"\n");
                appendLayout(text, parent.layout(), "windows.parent_tabs.nodes");
            }""",
     r"""            text.append("shown_tab = \"").append(window.layout().active()).append("\"\n");
            for (ParentTab parent : window.layout().parentTabs()) {
                text.append("\n[[windows.tabs]]\nid = \"").append(parent.id()).append("\"\n");
                appendLayout(text, parent.layout(), "windows.tabs.nodes");
            }""", 1),
    (r"""    /** A layout's active group and maximize, then its nodes, parents first, each under the array table {@code nodes}. */
    private static void appendLayout(StringBuilder text, Layout layout, String nodes) {
        text.append("active_group = \"").append(layout.activeGroup().value())""",
     r"""    /** A tab's active pane and maximize, then its nodes, parents first, each under the array table {@code nodes}. */
    private static void appendLayout(StringBuilder text, Layout layout, String nodes) {
        text.append("active_pane = \"").append(layout.activeGroup().value())""", 1),
    (r"""            case Group group -> text.append("\n[[").append(nodes).append("]]\nid = \"").append(group.id().value())
                .append("\"\nkind = \"group\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight)
                .append("\ntabs = ").append(group.tabs().stream().map(tab -> "\"" + tab.value() + "\"").collect(Collectors.joining(", ", "[", "]")))
                .append("\nselected = \"").append(group.selected().map(tab -> tab.value().toString()).orElse("")).append("\"\n");""",
     r"""            case Group group -> text.append("\n[[").append(nodes).append("]]\nid = \"").append(group.id().value())
                .append("\"\nkind = \"pane\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight)
                .append("\nitem = \"").append(group.selected().map(tab -> tab.value().toString()).orElse("")).append("\"\n");""", 1),
    (r"""        text.append("\n[[windows.tabs]]\nid = \"").append(id.value()).append("\"\n");""",
     r"""        text.append("\n[[windows.items]]\nid = \"").append(id.value()).append("\"\n");""", 1),
])
edit(A + "layout/Group.java", [(
""" * The record keeps a list and a selection, the shape {@code workspace.toml} saves (workspace plan 8a Rulings 2).
""", """ * The record keeps a list and a selection (workspace plan 8a Rulings 2); {@code workspace.toml} saves its one tab
 * (workspace plan 6a Rulings 32).
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.workspace.WorkspaceStoreTest' --tests 'dev.jasper.app.workspace.WorkspaceSavingTest' --tests 'dev.jasper.app.layout.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app
git commit -F - <<'EOF'
Workspace tabs: workspace.toml says window, tab, pane and item

A window saves its tabs in strip order with the one it shows, each tab its
nodes with its active pane, each pane the one item it holds, and then the
window's items, as the tabs and panes spec words them. The reader takes
the same words and drops a pane that names anything but one item.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Restoring the windows, their tabs and their terminals

Spec 12 and 13, workspace tabs spec 6.4 and 13: whenever the main window opens it is restored as saved, then each full
window, each where it was with its tabs in strip order and the one it showed, each tab's splits, panes and active pane,
each pane's item; a terminal comes back with its id, name, profile and directory and starts its shell once its tab is
the one its window shows when an EDT turn ends (Rulings 4, 12, 16, 20, 21, 27, 30, 31, 37 and 38). The empty workspace
comes back empty; a tab whose entry the reader dropped is gone alone and the file kept aside; a window that cannot be
restored keeps the saved windows aside.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java` (and its ledger row)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java` (after `start()` `:68`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java` (imports `:23`; before `place` `:89`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java` (before `changed` `:379`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (imports `:29`; `workspaceChanged`
  `:1288-1290`; after `layoutChanged` `:1296-1297`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java` (imports `:23`; `Opening`'s Javadoc
  `:42-43`; fields `:92-93`; `ensureMain` `:201-233`; `open` `:550-564`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/JasperWindow.java` (imports `:3`; before `shell` `:133`)

**Interfaces:**
- Consumes: Task 6's `WorkspaceStore.load()`, `setAside(Snapshot)` and `save()`'s rule; Task 7's codec;
  `WorkspaceFile.keeping` (plan 8a's form); Task 1's `TerminalPane.restored()`, `waiting()`; Task 2's
  `JasperWindow.Placement`; `TabRegistry.add(TabId, Tab.Kind, TabContent, UUID)` (plan 3a); `WindowTerminals.atomically`,
  `tabArrived` (plan 3b); `TabHost.attach(Tab, boolean)`; plan 7b's `ParentTabsView.workspace(ParentTabId)`; plan 8c's
  `ParentTabBar.atOnce(Runnable)`; plan 8a's `WindowContent.newTab(Path)`, `parentTabCount()`;
  `WindowManager.moveToMainWindow`.
- Produces:
  - `int WindowContent.restore(WorkspaceFile.WindowRecord record)`: how many items came back; its private
    `restoredTerminal(TabId, WorkspaceFile.TabRecord.Terminal)` → `Tab` and `mount(Tab)`, and `startShown()`, which
    `workspaceChanged` asks for;
  - `void ParentTabsView.restore(ParentTabs saved, Map<TabId, Tab> tabs)`;
  - `void WorkspaceView.restoreSaved(Layout saved, List<Tab> restored)`;
  - `void TerminalContent.startIfWaiting()`;
  - `final int JasperWindow.restore(WorkspaceFile.WindowRecord record)`;
  - `WindowManager`'s private `openMain(String, Path, Function<WindowContent, MainWindow>)`, `openNewMain`,
    `restoreChild(WorkspaceFile.WindowRecord)` → `Optional<ChildWindow>` and `open(Opening, Function<WindowContent, W>,
    Predicate<? super W>)`;
    `ensureMain(trigger, directory)` keeps its signature and restores;
  - `WorkspaceRestoreTest` with its helpers (`windows()`, `pane(TabId)`, `shell(String, Path, String)`, `tab(Layout)`,
    `mainWindow(…)`, `taken(TabId)`, `keptAside()`), which Task 9 and plan 6b extend.

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
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
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

/**
 * Workspace tabs spec 13, tabs and panes spec 12: the main window and its full windows come back as they were saved,
 * with their tabs, splits and panes; only the shown tab's terminals start, the others when their tab first shows.
 */
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

    /** A pane holding {@code tab}, its one item. */
    private static Group pane(TabId tab) {
        return new Group(GroupId.random(), List.of(tab), Optional.of(tab));
    }

    private static WorkspaceFile.TabRecord.Terminal shell(String title, Path directory, String profile) {
        return new WorkspaceFile.TabRecord.Terminal(title, directory.toString(), profile);
    }

    /** A tab of its own id holding {@code layout}. */
    private static ParentTab tab(Layout layout) {
        return new ParentTab(ParentTabId.random(), layout);
    }

    /** A main window saved without a place, holding {@code tabs} with the first showing. */
    private static WorkspaceFile.WindowRecord mainWindow(Map<TabId, WorkspaceFile.TabRecord> items, ParentTab... tabs) {
        return new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, new ParentTabs(List.of(tabs), tabs[0].id()), items);
    }

    /** A main window saved without a place, holding one tab whose one pane holds {@code item}. */
    private static WorkspaceFile.WindowRecord mainWindow(TabId item, WorkspaceFile.TabRecord record) {
        return mainWindow(Map.of(item, record), tab(Layout.of(item)));
    }

    @Test void theMainWindowAndItsFullWindowsComeBackAsTheyWere() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId build = TabId.random(), tail = TabId.random(), shell = TabId.random(), hidden = TabId.random();
        TabId away = TabId.random(), other = TabId.random();
        Group left = pane(build), upper = pane(tail), lower = pane(shell);
        var rows = new Split(Orientation.VERTICAL, List.of(new Child(upper, 0.4), new Child(lower, 0.6)));
        var split = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.3), new Child(rows, 0.7))), lower.id(), Optional.empty());
        ParentTab first = tab(split), second = tab(Layout.of(hidden));
        var mainTabs = new ParentTabs(List.of(first, second), first.id());
        var main = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6, mainTabs,
            Map.of(build, shell("build", work, "zsh"), tail, shell("tail", logs, ""), shell, shell("", work, ""), hidden, shell("", logs, "")));
        ParentTab fullFirst = tab(Layout.of(away)), fullSecond = tab(Layout.of(other));
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.of(new UiState.Bounds(900, 100, 800, 600)), 0,
            new ParentTabs(List.of(fullFirst, fullSecond), fullSecond.id()), Map.of(away, shell("", logs, ""), other, shell("", work, "")));
        var saved = new WorkspaceFile.Snapshot(List.of(main, full));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            MainWindow restored = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(restored.content().windowLayout()).as("its tabs in strip order, the one that shows, their splits, panes and active panes")
                .isEqualTo(mainTabs);
            assertThat(restored.placement()).isEqualTo(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            assertThat(restored.content().tabs()).extracting(Tab::id).containsExactly(build, tail, shell, hidden);
            assertThat(restored.content().selectedTab().id()).as("the active pane's item").isEqualTo(shell);
            var titles = restored.content().tabs().stream().map(item -> ((TerminalContent) item.content()).renamed().orElse("")).toList();
            assertThat(titles).containsExactly("build", "tail", "", "");
            assertThat(((TerminalContent) restored.content().tabs().getFirst().content()).pane().profile()).contains("zsh");
            List<ChildWindow> children = windows.manager.children();
            assertThat(children).hasSize(1);
            assertThat(children.getFirst().content().windowLayout()).as("a full window, the tab it showed shown").isEqualTo(full.layout());
            assertThat(children.getFirst().placement().bounds()).contains(new UiState.Bounds(900, 100, 800, 600));
            assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(saved);
            assertThat(windows.events).as("every frame shown, the main window brought to the front last")
                .containsSubsequence("show main", "opened main", "show child", "opened child", "front main");
        });
        windows.launch();
        assertThat(windows.started).as("every pane of each window's shown tab: build, tail and shell, then the full window's other")
            .containsExactly(work, logs, work, work);
    }

    /**
     * Tabs and panes spec 12, workspace plan 6a Rulings 4 and 37: a hidden tab's terminals wait until their tab shows,
     * then every pane of it starts, the one its maximize hides too.
     */
    @Test void aHiddenTabsTerminalsWaitAndStartWhenItFirstShows() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId front = TabId.random(), back = TabId.random(), beside = TabId.random();
        Layout backLayout = Layout.of(back);
        Layout split = backLayout.split(beside, backLayout.activeGroup(), Side.RIGHT);
        ParentTab shown = tab(Layout.of(front)), hidden = tab(split.toggleMaximize(split.activeGroup()));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(Map.of(front, shell("", work, ""), back, shell("", logs, ""),
            beside, shell("", logs, "")), shown, hidden))));
        windows();
        Tab[] waiting = new Tab[1];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout().parentTabs().get(1).layout().maximized()).as("the hidden tab keeps its maximize").isPresent();
            waiting[0] = main.content().tabs().get(1);
            var pane = ((TerminalContent) waiting[0].content()).pane();
            assertThat(pane.snapshot().state()).as("the registry reports it not started").isEqualTo(PaneSnapshot.State.NOT_STARTED);
            var entry = windows.terminals.tab(waiting[0].id().value()).orElseThrow().terminal().get().orElseThrow();
            assertThat(entry.snapshot().get().state()).isEqualTo(PaneSnapshot.State.NOT_STARTED);
        });
        windows.launch();
        assertThat(windows.started).as("only the shown tab's").containsExactly(work);
        edt(() -> windows.manager.main().orElseThrow().content().selectTab(waiting[0]));
        windows.launch();
        assertThat(windows.started).as("shown, both of its panes start, the one the maximize hides too").containsExactly(work, logs, logs);
    }

    @Test void aSavedDirectoryThatIsGoneStartsWhereANewShellStarts() throws Exception {
        TabId item = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder.resolve("deleted"), "")))));
        windows();
        edt(() -> windows.manager.ensureMain("cold", null).orElseThrow());
        windows.launch();
        assertThat(windows.started).containsExactly(HOME);
        edt(() -> assertThat(windows.manager.main().orElseThrow().content().tabCount()).as("the item stays").isEqualTo(1));
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

    /** Tabs and panes spec 4 and 12: the main window saved as its empty workspace comes back empty, under its saved ids. */
    @Test void theEmptyWorkspaceComesBack() throws Exception {
        var empty = ParentTabs.of(tab(Layout.empty()));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(),
            0, empty, Map.of()))));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout()).isEqualTo(empty);
            assertThat(main.content().tabCount()).isZero();
            assertThat(main.content().workspace().emptyWorkspace()).as("New Terminal Tab and Open File show").isNotNull();
        });
        windows.launch();
        assertThat(windows.started).isEmpty();
    }

    /**
     * Tabs and panes spec 13: a request's directory opens in a new tab after the restored ones, which shows; the restored
     * tab that showed before waits as a hidden tab does (workspace plan 6a Rulings 4 and 31).
     */
    @Test void aRestoredMainWindowOpensTheRequestsDirectoryInANewTab() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        Path asked = Files.createDirectories(folder.resolve("asked"));
        TabId back = TabId.random(), front = TabId.random();
        ParentTab hidden = tab(Layout.of(back)), shown = tab(Layout.of(front));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            new ParentTabs(List.of(hidden, shown), shown.id()), Map.of(back, shell("", logs, ""), front, shell("", work, ""))))));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", asked).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).as("after the restored ones").startsWith(back, front).hasSize(3);
            assertThat(main.content().parentTabCount()).as("in a tab of its own").isEqualTo(3);
            assertThat(main.content().selectedTab()).as("which shows").isSameAs(main.content().tabs().getLast());
        });
        windows.launch();
        assertThat(windows.started).as("the request's tab alone: the restored tabs wait to be shown").containsExactly(asked);
    }

    @Test void aChildWindowWithNothingToRestoreIsLeftOut() throws Exception {
        TabId item = TabId.random(), document = TabId.random();
        var plugin = (WorkspaceFile.TabRecord) new WorkspaceFile.TabRecord.Content("notes.md", "dev.example.editor", "document", Map.of("path", "/notes.md"));
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, ParentTabs.of(tab(Layout.of(document))),
            Map.of(document, plugin));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder, "")), full)));
        windows();
        edt(() -> {
            windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).as("a full window holding only a plugin's item (plan 6b restores those)").isEmpty();
            assertThat(windows.events).doesNotContain("show child");
        });
    }

    /** Tabs and panes spec 12: a tab whose entry cannot be read is dropped alone, and the file is kept aside and told. */
    @Test void aTabWhoseEntryCannotBeReadIsDroppedAloneAndTheFileIsKeptAside() throws Exception {
        TabId first = TabId.random(), second = TabId.random();
        ParentTab kept = tab(Layout.of(first)), broken = tab(Layout.of(second));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(Map.of(first, shell("", folder, ""), second, shell("", folder, "")),
            kept, broken))));
        String text = Files.readString(file());
        Files.writeString(file(), text.replace("item = \"" + second.value() + "\"", "item = 3"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(kept.id());
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(first);
        });
        assertThat(setAside).as("told once, for the notice").containsExactly(folder.resolve("workspace.toml.bad"));
        windows.launch();
    }

    /** A tab already registered under {@code item}'s id makes a restore that rebuilds it fail, as a bug would. */
    private void taken(TabId item) {
        windows.model.tabs().add(item, Tab.Kind.TERMINAL, new dev.jasper.app.contributions.TabContent() {
            @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
            @Override public void dispose() { }
        }, java.util.UUID.randomUUID());
    }

    private WorkspaceFile.Snapshot keptAside() {
        return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(folder.resolve("workspace.toml.bad"))).snapshot();
    }

    @Test void aMainWindowThatCannotBeRestoredOpensANewOneAndTheSavedWindowsAreKept() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        TabId item = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder, ""))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            taken(item);
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabs()).hasSize(1);
            assertThat(main.content().tabs().getFirst().id()).isNotEqualTo(item);
            assertThat(windows.events).as("it opens as a new main window, never closed: the last window's closed hook can end Jasper")
                .containsExactly("show main", "opened main");
        });
        assertThat(setAside).as("told, for the notice").containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).as("not lost to the next save").isEqualTo(saved);
        windows.launch();
        assertThat(windows.started).as("the failed item's shell never started").containsExactly(work);
    }

    @Test void aChildWindowThatCannotBeRestoredIsLeftOutAndTheSavedWindowsAreKept() throws Exception {
        TabId shell = TabId.random(), away = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(mainWindow(shell, shell("", folder, "")),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, ParentTabs.of(tab(Layout.of(away))),
                Map.of(away, shell("", folder, "")))));
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

    /**
     * Workspace plan 6a Rulings 31: Move to Main Window moves a full window's tabs one at a time, each showing for an
     * instant; only the one that shows when the move ends starts, and the tabs it passed over still wait.
     */
    @Test void movingARestoredFullWindowToTheMainWindowStartsOnlyTheTabThatShowsThere() throws Exception {
        TabId front = TabId.random(), first = TabId.random(), second = TabId.random(), third = TabId.random();
        ParentTab one = tab(Layout.of(first)), two = tab(Layout.of(second)), three = tab(Layout.of(third));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(front, shell("", folder, "")),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, new ParentTabs(List.of(one, two, three), one.id()),
                Map.of(first, shell("", folder, ""), second, shell("", folder, ""), third, shell("", folder, ""))))));
        windows();
        edt(() -> windows.manager.ensureMain("cold", null).orElseThrow());
        windows.launch();
        assertThat(windows.started).as("each window's shown tab").hasSize(2);
        edt(() -> {
            MainWindow main = windows.manager.main().orElseThrow();
            assertThat(windows.manager.moveToMainWindow(windows.manager.children().getFirst().content())).containsSame(main);
            assertThat(main.content().selectedTab().id()).as("the tab the full window showed shows").isEqualTo(first);
        });
        windows.launch();
        assertThat(windows.started).as("the tabs the move passed over never started").hasSize(2);
        edt(() -> assertThat(windows.manager.main().orElseThrow().content().tabs().stream()
            .map(item -> ((TerminalContent) item.content()).pane().waiting()).toList())
            .as("front and first started at the restore; second and third wait to be shown").containsExactly(false, false, true, true));
    }

    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        windows();
        TabId[] saved = new TabId[3];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            main.content().newTab(work);
            main.content().split(Side.RIGHT);
            saved[0] = main.content().tabs().get(0).id();
            saved[1] = main.content().tabs().get(1).id();
            saved[2] = main.content().tabs().get(2).id();
            windows.manager.openTerminalWindow(work);
            windows.manager.closeAll();
            assertThat(store.held()).as("closing every window saved them first").isTrue();
            MainWindow again = windows.manager.ensureMain("reopen", null).orElseThrow();
            assertThat(again.content().tabs()).extracting(Tab::id).containsExactly(saved);
            assertThat(again.content().windowLayout().parentTabs()).as("its tabs, the second split in two").hasSize(2);
            assertThat(windows.manager.children()).as("its full window").hasSize(1);
        });
    }
}
```

Add its ledger row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "WorkspaceRestoreTest.java", "app.workspace", "WorkspaceRestoreTest", T + "WorkspaceRestoreTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 8; windows restored from workspace.toml")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceRestoreTest'`
Expected: FAIL: 10 of 12; the main window opens with one new terminal tab (`expected: ParentTabs[…]` on its tabs),
the hidden tab's item is missing (`Index 1 out of bounds for length 1`), and nothing is set aside. The two that pass
already, `aSavedDirectoryThatIsGoneStartsWhereANewShellStarts` and `aChildWindowWithNothingToRestoreIsLeftOut`, pin what
must not change.

- [ ] **Step 3: A window restores its tabs, and the terminals of the tab it shows start**

A restored terminal is registered under its saved id, unstarted; every item is mounted, and the saved tabs shown, in one
change of the terminal registry; whenever the window's tabs or layout change, each restored terminal of the tab it
shows starts at the end of that EDT turn (Rulings 31 and 37). `ensureMain` reads the store after its own checks, opens
the main window empty and restores it before its frame exists, then each full window; a full window that restores
nothing is discarded unshown; the main window comes to the front when a full window came back, and the request's
directory opens in a new tab after the restored ones, a failure there logged (Rulings 12, 38 and 41):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "TerminalContent.java", [
    ("    void start() { pane.start(); }\n",
     "    void start() { pane.start(); }\n"
     "    /** Starts a restored terminal's shell the first time its tab shows (workspace tabs spec 13). */\n"
     "    void startIfWaiting() { if (!closed && pane.waiting()) pane.start(); }\n", 1)])
edit(W + "WorkspaceView.java", [
    ("import java.util.Optional;\n", "import java.util.Optional;\nimport java.util.Set;\n", 1),
    ("""    private void place(Tab tab, Layout next) {""",
     """    /**
     * Shows a saved tab's layout over {@code restored}, items no window showed before, in this workspace, which holds none
     * (workspace tabs spec 13; tabs and panes spec 12): each item takes its saved pane at once.
     */
    void restoreSaved(Layout saved, List<Tab> restored) {
        if (!tabs.isEmpty() || !layout.isEmpty()) throw new IllegalStateException("Only an empty workspace restores");
        var placed = new HashMap<TabId, Tab>();
        for (Tab tab : restored) placed.put(tab.id(), tab);
        if (!placed.keySet().equals(Set.copyOf(saved.tabs()))) throw new IllegalArgumentException("The layout places other items");
        tabs.putAll(placed);
        apply(saved);
    }

    private void place(Tab tab, Layout next) {""", 1)])
edit(W + "ParentTabsView.java", [
    ("""    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */""",
     """    /**
     * Shows saved tabs in this window, which holds no item yet (workspace tabs spec 13; tabs and panes spec 12): a
     * workspace under each saved id, with the window's theme and glass, showing its saved layout over {@code tabs}, in
     * strip order, and the saved one shown, all at once (workspace plan 6a Rulings 30). The window's empty tab gives way
     * to them; a failure while the saved tabs' workspaces are built leaves it as it was.
     *
     * @param saved the tabs as saved
     * @param tabs the restored items by id: every one the tabs place
     */
    void restore(ParentTabs saved, Map<TabId, Tab> tabs) {
        if (closed || !isEmpty()) throw new IllegalStateException("Only an empty window restores its tabs");
        var made = new ArrayList<WorkspaceView>();
        try {
            for (ParentTab parent : saved.parentTabs()) {
                // The one helper for every tab's workspace gives it the window's theme and glass (workspace plan 7b).
                var view = workspace(parent.id());
                made.add(view);
                view.restoreSaved(parent.layout(), parent.layout().tabs().stream().map(tabs::get).toList());
            }
        } catch (RuntimeException | Error failure) {
            for (WorkspaceView view : made) view.close();
            throw failure;
        }
        WorkspaceView empty = active();
        views.remove(empty.id());
        super.remove(empty);
        empty.close();
        // In strip order, ahead of the strip, which stays the last child (workspace plan 7a Rulings 19).
        for (int i = 0; i < made.size(); i++) {
            views.put(made.get(i).id(), made.get(i));
            add(made.get(i), i);
        }
        model = saved;
        reveal(active());
        bar.atOnce(() -> owner.workspaceChanged(active(), false));
    }

    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */""", 1)])
edit(W + "WindowContent.java", [
    ("import dev.jasper.app.persistence.UiState;\n",
     "import dev.jasper.app.persistence.UiState;\nimport dev.jasper.app.persistence.WorkspaceFile;\n", 1),
    ("""        // Every parent tab's layout change and every parent-tab change ends here (workspace plan 6a Rulings 8 and 28): the
        // save is asked for once the model has it, so a listener below that throws costs no save.
        layoutChanged();
""", """        // Every parent tab's layout change and every parent-tab change ends here (workspace plan 6a Rulings 8 and 28): the
        // save is asked for once the model has it, so a listener below that throws costs no save.
        layoutChanged();
        startShown();
""", 1),
    ("""    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }
""", """    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }

    /** Whether the start of the shown restored terminals waits for the end of this EDT turn (workspace plan 6a Rulings 31). */
    private boolean startPending;

    /**
     * Starts, once this EDT turn ends, each restored terminal of the tab this window shows then, every pane of it, a
     * maximize's hidden ones too; the others wait until their tab first shows (workspace tabs spec 13; tabs and panes
     * spec 12; workspace plan 6a Rulings 4 and 31). A gesture that moves several tabs one at a time shows each for an
     * instant: the start, asked for once per turn, reads what shows when the gesture has ended, so those stay unstarted.
     */
    private void startShown() {
        if (startPending) return;
        startPending = true;
        SwingUtilities.invokeLater(() -> {
            startPending = false;
            if (closed) return;
            for (Tab tab : workspace().tabs())
                if (tab.content() instanceof TerminalContent terminal) terminal.startIfWaiting();
        });
    }

    /**
     * Shows a saved window in this window, which holds no item yet (workspace tabs spec 13; tabs and panes spec 12): its
     * tabs under their saved ids, in strip order, with the saved one shown, each with its split layout and active pane;
     * each terminal with its saved id, renamed title, profile and last local directory, its shell not started until its
     * tab first shows. An item this window cannot rebuild is left out as closing it would leave it out
     * ({@code WorkspaceFile.keeping}).
     *
     * @param record the window as saved
     * @return how many items came back
     */
    int restore(WorkspaceFile.WindowRecord record) {
        if (closed || !parentTabs.isEmpty()) throw new IllegalStateException("Only an empty open window restores");
        var restored = new LinkedHashMap<TabId, Tab>();
        try {
            for (TabId saved : record.layout().tabs())
                if (record.tabs().get(saved) instanceof WorkspaceFile.TabRecord.Terminal terminal) restored.put(saved, restoredTerminal(saved, terminal));
            Optional<ParentTabs> shown = WorkspaceFile.keeping(record.layout(), restored::containsKey, isMain());
            if (shown.isEmpty()) return 0;
            Runnable show = () -> {
                for (Tab tab : restored.values()) mount(tab);
                parentTabs.restore(shown.get(), restored);
            };
            if (terminals != null) terminals.atomically(show); else show.run();
        } catch (RuntimeException | Error failure) {
            // Nothing half-restored stays: each item closes, and is announced closed where it was announced.
            for (Tab tab : restored.values()) {
                try { tab.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            throw failure;
        }
        return restored.size();
    }

    /** A saved terminal whose shell waits until its tab first shows (workspace tabs spec 13). */
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
            // Under its saved id, straight into this window: never through TabRegistry.open (workspace plan 4b's hand-off).
            tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        } catch (RuntimeException | Error failure) {
            // Not registered, so no item closes it: its pane is closed here.
            try { terminal.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            throw failure;
        }
        tab.focusTarget(terminal::focusTerminal);
        return tab;
    }

    /** Wires a restored item to this window as {@link #attach(Tab)} does, without placing it: the restore places every one at once. */
    private void mount(Tab tab) {
        WorkspaceTheme.apply(tab.component(), themes.current());
        tabListeners.put(tab, tab.onChanged(this::update));
        if (terminals != null) terminals.tabArrived(tab, Optional.empty());
        tabHost.attach(tab, true);
    }
""", 1)])
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
     * as the store has it, with its full windows, else a new main window with one terminal tab. The tabs a plugin asked
     * for while it was being composed open in it once it shows, or go with it when it fails to open, their failures
     * under its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts, or, when the main window is restored, where a
     *     terminal starts in a new tab after the restored ones; null for where a new shell starts, or no new tab""", 1),
    ("""        try {
            opened = open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(),
                Optional.empty()), content -> main = new MainWindow(this, content));
        } catch (RuntimeException | Error failure) {""", """        try {
            // The one place a main window is made (workspace tabs spec 6.1; verifyApplicationArchitecture pins this method).
            Function<WindowContent, MainWindow> make = content -> main = new MainWindow(this, content);
            opened = openMain(trigger, directory, make);
        } catch (RuntimeException | Error failure) {""", 1),
    ("""        // A main window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().mainOpened(window.id()));
        return opened;
    }
""", """        // A main window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().mainOpened(window.id()));
        return opened;
    }

    /**
     * The main window as the store has it (workspace tabs spec 13; tabs and panes spec 12): the saved main window, then
     * each saved full window, each where it was with its tabs, splits and panes and its items, the main window in front;
     * with nothing to restore, or a main window that cannot be restored, a new main window with one terminal tab in
     * {@code directory}. A restored main window opens {@code directory} in a new tab after its own. A window that cannot
     * be restored is not lost: the store keeps the saved windows as {@code workspace.toml.bad} and says so (spec 15).
     * {@code make} is {@link #ensureMain}'s, the one place a main window is made; the store is read here, after
     * {@link #ensureMain}'s checks, since reading it ends its hold.
     */
    private Optional<MainWindow> openMain(String trigger, Path directory, Function<WindowContent, MainWindow> make) {
        WorkspaceFile.Loaded loaded = store == null ? new WorkspaceFile.Loaded.Fresh(Optional.empty()) : store.load();
        if (!(loaded instanceof WorkspaceFile.Loaded.Restore restore)) return openNewMain(trigger, directory, make);
        WorkspaceFile.Snapshot saved = restore.snapshot();
        boolean[] restored = new boolean[1];
        Optional<MainWindow> opened = open(new Opening(WindowRole.MAIN, true, null, trigger, () -> { }, Optional.empty(),
            Optional.empty()), make, window -> {
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
        var back = new ArrayList<ChildWindow>();
        for (WorkspaceFile.WindowRecord child : saved.children()) {
            try {
                restoreChild(child).ifPresent(back::add);
            } catch (RuntimeException failure) {
                // A full window that cannot come back costs only itself, and the saved windows are kept.
                LOG.log(System.Logger.Level.WARNING, "Could not restore a full window", failure);
                whole = false;
            }
        }
        if (!whole) store.setAside(saved);
        if (directory != null) {
            // The main window has shown: a new tab that fails costs only itself, never the window (workspace plan 6a Rulings 41).
            try { opened.get().content().newTab(directory); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "Could not open the requested directory in a new tab", failure); }
        }
        // In front when a full window came back (workspace plan 6a Rulings 12).
        if (!back.isEmpty()) opened.get().toFront();
        return opened;
    }

    /** A new main window with one terminal tab in {@code directory}, made by {@link #ensureMain}'s {@code make}. */
    private Optional<MainWindow> openNewMain(String trigger, Path directory, Function<WindowContent, MainWindow> make) {
        return open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(), Optional.empty()), make);
    }

    /**
     * A saved full window, where it was with its tabs and items; one with no item that can come back is left out. Its
     * timing line says {@code restore}, which no benchmark reads as a menu's window (workspace plan 6a Rulings 16).
     *
     * @return the window, once it shows; empty when it was left out
     */
    private Optional<ChildWindow> restoreChild(WorkspaceFile.WindowRecord record) {
        ChildWindow[] made = new ChildWindow[1];
        // Its saved place reaches its frame through its placement, checked against today's displays, never as a tear-out's
        // workspace bounds, which its frame would grow by its chrome (workspace plan 6a Rulings 21).
        return open(new Opening(WindowRole.CHILD, true, null, "restore", () -> { if (made[0] != null) made[0].emptied(); }, Optional.empty(),
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
     * Restores the window as it was saved (workspace tabs spec 13; tabs and panes spec 12): where it was, its tabs and
     * its items. Before its frame exists, which takes the saved place from {@link #placement()}.
     *
     * @return how many items came back
     */
    final int restore(WorkspaceFile.WindowRecord record) {
        placement = new Placement(record.bounds(), record.extendedState());
        return content.restore(record);
    }

    /** The frame the manager built for this window. */""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture`
Expected: PASS; `verifyApplicationArchitecture` still finds `new MainWindow` only in `WindowManager.ensureMain`, whose
`make` lambda holds it.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: restore the windows, their tabs and their terminals

Opening the main window restores it as saved, then each full window, with
their places, tabs in order and the one each showed, each tab's splits,
panes and active pane. A terminal keeps its id, name, profile and
directory and starts its shell once its tab is the one its window shows;
the request's directory opens in a new tab after the restored ones.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The application saves and restores

Spec 13 and 15 through the application: the store lives in the Jasper home from `startPlugins` on, Quit and going to
the background save every window, shutdown waits for the last write, saved windows that do not all come back are told
in a notice, the warm-up rehearsal never touches the file, and a main window restored with no terminal showing marks the
first paint (Rulings 13, 17, 18, 19 and 42).

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWorkspaceTest.java` (and its ledger row)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (after `isActiveAndOpen` `:1118`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (imports `:48`; fields `:165`;
  `windowOpened` `:584-585`; `open`'s Javadoc `:926-928`; `openFirstWindow`'s `:947-949`; before `trace` `:449`;
  `startPlugins` `:1168`; the shutdown `:2181`)
- Test: `WorkspaceRestoreTest` (one case, before `reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed`)

**Interfaces:**
- Consumes: Task 6's `WorkspaceStore` and `written()`; Task 8's restore through `ensureMain`; Task 5's
  `WindowManager.workspace(WorkspaceStore)`, `snapshot()`; `AppDirs.workspaceState()` (plan 2);
  `JasperApplication.afterDelay(Duration, Runnable)`.
- Produces: package `void JasperApplication.workspace(Path file)` and `WorkspaceStore JasperApplication.workspaceStore()`;
  `public boolean WindowContent.showsNoTerminal()`, `public void WindowContent.afterNextPaint(Runnable task)`; the
  notice "Jasper could not restore your windows"; `JasperApplicationWorkspaceTest`'s `application(Runnable)`,
  `saved()`, `launch()` and `quit(JasperApplication)`, which plan 6b extends.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWorkspaceTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.layout.Side;
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
 * Workspace tabs spec 13 and tabs and panes spec 12 through the application, with stand-in frames: the windows are saved
 * when they close, on Quit and when Jasper goes to the background, and restored at start and whenever the main window
 * opens again.
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

    /** Runs the shells' launches asked for so far, once the EDT has run what it was given: a restored terminal starts then. */
    private void launch() throws Exception {
        edt(() -> { });
        while (!pending.isEmpty()) pending.remove().run();
    }

    /** Quits {@code application} and waits for its last save, which lands in the temporary home before JUnit deletes it. */
    private static void quit(JasperApplication application) throws Exception {
        edt(application::quitNow);
        application.workspaceStore().written().get(5, TimeUnit.SECONDS);
    }

    @Test void quitSavesEveryWindowAndTheNextStartRestoresThem() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication first = application(terminated::countDown);
        edt(() -> {
            MainWindow main = first.openFirstWindow(List.of()).orElseThrow();
            main.content().newTab(home);
            main.content().split(Side.RIGHT);
            first.windows().openTerminalWindow(home);
            first.quitNow();
        });
        launch();
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("shutdown waits for the save").isTrue();
        assertThat(saved().main().layout().parentTabs()).as("its two tabs, the second split in two").hasSize(2);
        assertThat(saved().main().layout().tabs()).hasSize(3);
        assertThat(saved().children()).hasSize(1);
        JasperApplication second = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = second.openFirstWindow(List.of()).orElseThrow();
                assertThat(main.content().terminalTabs()).hasSize(3);
                assertThat(main.content().windowLayout().parentTabs()).as("its tabs").hasSize(2);
                assertThat(second.windows().children()).as("its full window").hasSize(1);
            });
            launch();
        } finally {
            quit(second);
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
            quit(application);
        }
    }

    @Test void aFullWindowTheUserClosesIsSavedWithoutIt() throws Exception {
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
            quit(application);
        }
    }

    /** Tabs and panes spec 13: each directory a launch names opens in a new tab after the restored ones. */
    @Test void aLaunchsPathsOpenInNewTabsAfterTheRestore() throws Exception {
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
                assertThat(tabs).as("the restored terminal, then one per directory").hasSize(3);
                assertThat(tabs.get(1).pane().directory()).isEqualTo(first);
                assertThat(tabs.get(2).pane().directory()).isEqualTo(second);
                assertThat(main.content().windowLayout().parentTabs()).as("each in a tab of its own").hasSize(3);
            });
            launch();
        } finally {
            quit(application);
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
            quit(application);
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
            quit(application);
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
        assertThat(saved().main().layout().isEmpty()).as("the empty workspace was saved").isTrue();
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            MainWindow[] main = new MainWindow[1];
            edt(() -> {
                application.trace(trace);
                main[0] = application.openFirstWindow(List.of()).orElseThrow();
                assertThat(main[0].content().showsNoTerminal()).isTrue();
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).isEmpty();
            });
            // The check reads the window once its opening's turn has ended (workspace plan 6a Rulings 42).
            edt(() -> { });
            edt(() -> {
                main[0].content().setSize(200, 100);
                main[0].content().paint(new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics());
                assertThat(trace.millis(StartupTrace.Milestone.FIRST_PAINT)).as("the empty workspace's paint").isPresent();
            });
        } finally {
            quit(application);
        }
    }
}
```

Add its ledger row, and the restore test's case for a terminal that a maximize or a hidden tab keeps from painting:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); import ledger; from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/application/"
ledger.add(T + "JasperApplicationWorkspaceTest.java", "app.application", "JasperApplicationWorkspaceTest", T + "JasperApplicationWorkspaceTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6a Task 9; saving and restoring through the application")
# The maximized and hidden cases: a terminal that started but that a maximize or a hidden tab hides paints nothing.
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRestoreTest.java", [(
    """    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {""",
    """    /** Workspace plan 6a Rulings 17: only the panes of the tab that shows count, the maximized one alone while one is. */
    @Test void aWindowShowsNoTerminalWhenAMaximizedPaneOrAHiddenTabHidesTheOneThatStarted() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            assertThat(main.content().showsNoTerminal()).isFalse();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.md", main.id(), new javax.swing.JPanel(),
                dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
            main.content().selectTab(notes);
            assertThat(main.content().showsNoTerminal()).as("the terminal's pane still shows").isFalse();
            main.content().toggleMaximize();
            assertThat(main.content().showsNoTerminal()).as("only the maximized pane shows").isTrue();
            main.content().toggleMaximize();
            windows.model.tabs().open("dev.example.editor", "more", "more.md", main.id(), new javax.swing.JPanel());
            assertThat(main.content().showsNoTerminal()).as("the tab that shows holds a plugin's item alone").isTrue();
        });
        windows.launch();
    }

    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `workspace(Path)`, `workspaceStore()` and `showsNoTerminal()`.

- [ ] **Step 3: The first paint of a window that shows no terminal, and the store in the application**

`showsNoTerminal` reads the shown panes of the tab the window shows, once the main window's opening turn has ended
(Rulings 42); `startPlugins` gives the window manager a store
over `<home>/workspace.toml`, its saves scheduled on the EDT's timer and written on a virtual thread, a set-aside file
told in a notice; shutdown waits for the writes; `open` and `openFirstWindow` say what they do now:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6a"); from edits import edit
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(W, [("    public boolean isActiveAndOpen() { return active && !closed; }\n",
"""    public boolean isActiveAndOpen() { return active && !closed; }

    private final java.util.List<Runnable> afterPaint = new ArrayList<>();

    /**
     * Whether no terminal shows in this window now, so none paints the window's first output (workspace tabs spec 13):
     * no pane of the tab it shows that shows (the maximized one alone while one is maximized) holds a terminal, or the
     * workspace is empty. A hidden tab's terminal shows nothing.
     *
     * @return true when no shown pane holds a terminal
     */
    public boolean showsNoTerminal() {
        WorkspaceView workspace = workspace();
        Layout layout = workspace.model();
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
edit(A, [
    ("""        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
    }
""", """        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
        // A main window that shows no terminal once its opening's turn ends (a request's new tab or file comes after this
        // in the same turn) paints none at once: its own first paint is the start-up's, which proves the look and settles
        // residency (workspace plan 6a Rulings 17 and 42).
        if (window.isMain()) SwingUtilities.invokeLater(() -> {
            if (!window.closed() && window.content().showsNoTerminal()) window.content().afterNextPaint(() -> {
                if (windowTrace != null) windowTrace.mark(WindowTrace.Milestone.FIRST_PAINT);
                trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            });
        });
    }
""", 1),
    ("import dev.jasper.app.workspace.WorkspaceActivity;\n",
     "import dev.jasper.app.workspace.WorkspaceActivity;\nimport dev.jasper.app.workspace.WorkspaceStore;\n", 1),
    ("    private UiState uiState = UiState.inMemory();\n",
     "    private UiState uiState = UiState.inMemory();\n"
     "    /** Where the windows are saved and restored from; null before {@link #startPlugins(AppDirs)}, and then nothing is. */\n"
     "    private WorkspaceStore workspaceStore;\n", 1),
    ("""     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3, 5.4 and 6.4). The main window is restored
     * first when none is open (until workspace-tabs plan 6, a new main window opens, in the first directory the request
     * names when there is one), else raised; each other directory opens as a new terminal tab and each""",
     """     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3, 5.4, 6.4 and 13). The main window is
     * restored first when none is open, with its full windows as {@code workspace.toml} has them, and the first directory
     * the request names opens in a new tab after the restored ones (with nothing to restore, a new main window opens
     * there); an open main window is raised. Each other directory opens as a new terminal tab and each""", 1),
    ("""     * A launch's main window, timed as a cold start unless a {@code --background} launch warmed up: its tab starts in the
     * first directory {@code paths} names, else where a new shell starts, and the rest of the paths open in it as
     * {@link #open} opens them (plan Ruling 13). EDT.""",
     """     * A launch's main window, timed as a cold start unless a {@code --background} launch warmed up: restored with its full
     * windows as {@code workspace.toml} has them (workspace tabs spec 13), the first directory {@code paths} names opening
     * in a new tab after the restored ones; with nothing to restore, a new main window whose tab starts in that
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
     * manager. Before the first window. EDT.
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

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (`JasperApplicationWorkspaceTest` 7, `WorkspaceRestoreTest` 13).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: the application saves and restores its windows

The store keeps workspace.toml in the Jasper home from the moment the
plugins start; Quit, going to the background and closing the main window
save every window, shutdown waits for the write, and saved windows that
do not all come back are kept aside with a notice. A main window
restored with no terminal showing marks the first paint itself.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/configuration.md` (`:665-673`), `docs/app-architecture.md` (`:106-108`, `:179`), `docs/app-maintenance.md`
  (`:512-513`), `docs/plugin-authoring.md` (`:364`), `jasper-sdk/README.md` (`:45-46`, `:76`), `docs/STATUS.md` (the
  entry at the top), this plan's status banner (line 3)
- Test: none new; the whole build

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentences true (AGENTS.md "Documentation edits"): `ui-state.toml` no longer
keeps the main window's bounds and `workspace.toml` keeps every window, its tabs and panes; the architecture names the
store; the rehearsal never reaches `workspace.toml`; plugin authors learn that a restored terminal may not have started;
the SDK README's version line points at plan 8b's section and the one it gains, which follows plan 8b's:

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

`workspace.toml` beside it keeps Jasper's windows: the main window and each full window, with its size, position and
maximized state, its tabs in order with the one it shows, and each tab's split panes, the active one and what each pane
holds. Jasper saves it within a second of each change, when a window closes, on Quit and when it goes to the
background, and restores it at start and whenever the main window opens again. A window keeps its normal bounds while
maximized or minimized and never comes back minimized; one that no longer fits an available display is fitted to a
usable display. A terminal comes back with its name and profile and a new shell in its last local directory, or where a
new shell starts when that directory was on another host or is gone; only the terminals of the tab each window shows
start at once, the others when their tab first shows. Scrollback and running programs do not come back. A file Jasper
cannot read is kept as `workspace.toml.bad`, and Jasper starts with one terminal tab and says so. This file is written
by Jasper and is not meant to be edited.""", 1)])
edit("docs/app-architecture.md", [
    ("""restore the main window first; closing the main window, Quit and going to the background close the child windows, then
the main window. `MainWindow`'s constructor has one call site, `WindowManager.ensureMain`, which
`verifyApplicationArchitecture` checks.
""", """restore the main window first; closing the main window, Quit and going to the background close the child windows, then
the main window. `MainWindow`'s constructor has one call site, `WindowManager.ensureMain`, which
`verifyApplicationArchitecture` checks. `WorkspaceStore` (workspace) keeps the windows in `workspace.toml`
(`persistence.WorkspaceFile`, which writes each window's tabs, their panes and each pane's item in the spec's words):
`WindowManager.snapshot()` builds what is saved, within a second of each change (a non-sliding timer on the EDT, the
write on a virtual thread) and at once when a window closes; closing every window saves first and holds saving until
the windows come back. `ensureMain` restores the main window, then its full windows, each with its place, tabs and
items, before it shows; a restored terminal's shell starts when its tab first shows, and until then its pane reports
`NOT_STARTED`.
""", 1),
    ("each a workspace of groups, its panes, one tab each (`WorkspaceView`, `GroupView`, `SplitContainer`), terminal tabs and panes,",
     "each a workspace of groups, its panes, one tab each (`WorkspaceView`, `GroupView`, `SplitContainer`), the saved workspace (`WorkspaceStore`), terminal tabs and panes,", 1)])
edit("docs/plugin-authoring.md", [(
    """- `PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title.
""", """- `PaneInfo.shell` is the launcher's label (`zsh`, `fish`) or a provided session's title.
- **A restored terminal may not have started (SDK 0.21.0).** Jasper restores its windows at start and whenever the main
  window opens again; a terminal whose tab Jasper has not shown yet reports `SessionState.NOT_STARTED` until its tab is
  first shown: nothing runs there yet, and text sent to it is dropped. In the testkit `FakePluginHost.holdPane` holds a
  pane the same way until its tab is shown.
""", 1)])
edit("jasper-sdk/README.md", [
    ("(0.21.0 is a breaking boundary for saved workspaces; 0.20.0 is one too, described under Main and child windows\nbelow;",
     "(0.21.0 is a breaking boundary, described under Tabs, panes and split items and under Saved workspaces below; 0.20.0 is one\ntoo, described under Main and child windows below;", 1),
    ("""## Tabs of every kind (SDK 0.19.0)
""", """## Saved workspaces (SDK 0.21.0)

Jasper saves its windows and restores them at start and whenever the main window opens again: the main window and its
full windows, their tabs, the panes of each tab and what each pane holds. A restored terminal whose tab Jasper has not
shown yet has not started: its pane's `info().state()` is `SessionState.NOT_STARTED` until its tab first shows, and text
sent to it is dropped; then its shell starts as a new terminal's does.

## Tabs of every kind (SDK 0.19.0)
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
git grep -n -i -E "\[main_window\]|main-window size|restoreLayout|[^a-z]mainWindow\(\)" -- README.md jasper-sdk/README.md docs ':!docs/superpowers' ':!docs/STATUS.md' ':!docs/design' || echo "none left"
```

Expected: `none left`.

- [ ] **Step 2: Source hygiene**

Run the AGENTS.md character check over the Java files Tasks 6-9 touched:

```bash
python3 -I - $(git diff --name-only --diff-filter=d HEAD~4 -- '*.java') <<'PY'
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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 8c's entry),
in the style of the earlier entries:

```markdown
**Workspace tabs, plan 6a: saving and restoring the workspace — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `30cc4ca35..3f273675e` on the parent-tabs model, then `<first>..<last>` and this
documentation commit; [plan](superpowers/plans/2026-10-18-workspace-6a-save-restore.md),
[spec](superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md)). Jasper saves its windows to
`<home>/workspace.toml` (`workspace.WorkspaceStore` over plan 2's `WorkspaceFile`): one second after the first change
(a timer that does not slide), at once when a window closes, and before closing every window (closing the main window,
Quit, going to the background), after which saving waits until the windows come back; writes run in order on virtual
threads, each after the one before however it ended, a write the writer refuses logged as a failed one, and shutdown
waits for them; a snapshot that fails, an `Error` too, costs only that save. The file says window, tab, pane and item
(spec 12): each window's place, `shown_tab` and tabs in strip order (`[[windows.tabs]]`), each tab's `active_pane`,
`maximized` and nodes, each pane naming its one item, then the window's items (`[[windows.items]]`); `version` stays 1.
`WindowManager.snapshot()` keeps each window's `JasperWindow.Placement`, its tabs and a `TabRecord.Terminal` per local
terminal (rename, last local directory, profile); other items are taken out as closing them would
(`WorkspaceFile.keeping`): a tab left with none goes, a full window left with none is left out, Jasper's own Errors tab
among them. `WindowManager.ensureMain` restores: the main window, then its full windows, each before its frame exists,
the main window in front; tab, pane and item ids come back as saved; a request's directory opens in a new tab after the
restored ones; with nothing saved, an unreadable file or a main window that cannot be restored, a new main window
opens. Saved windows that do not all come back (an unreadable file, a dropped window, tab or item entry, a window that
cannot be restored) are kept as `workspace.toml.bad` with the notice "Jasper could not restore your windows". A main
window that shows no terminal (a maximized pane counts alone) marks the first paint with its own. A restored terminal
waits (`TerminalPane.restored()`) until its tab is the one its window shows when an EDT turn ends (a gesture that passes
over tabs, such as Move to Main Window, starts none of them; a request's new tab leaves the restored ones waiting), and
reports `NOT_STARTED`, in the testkit's fake too (`FakePluginHost.holdPane`, checked on both runtimes); a directory that
is gone starts the shell where a new shell starts. `UiState`'s `[main_window]` is gone (no migration), and plan 5's
keep-on-screen helpers are `WindowPlacement.fitted` and `displayFor`. The SDK is 0.21.0, a breaking boundary:
`SessionState.NOT_STARTED`; every bundled manifest, the external example and every guide range declare
`sdk = ">=0.21.0, <0.22"`. Rulings (plan, "Rulings"): <one clause each, 1 to 42>; superseded or reworded for tabs and
panes: Rulings 4, 14, 17, 25 to 31 and Spec issues 4. Deviations: <none, or each with its reason>. Left for plan 6b:
plugins' items (restore state, restorers, placeholders) within SDK 0.21.0, restored with their saved ids straight into
their windows and tabs, never through `TabRegistry.open`, and never saving Jasper's own items (`TabRegistry.APP_OWNER`);
`ContentTab.window()` names a never-open window for an item that waits for the main window, which now waits through
every full window's restore; Remote's restored `NOT_STARTED` panes; `AppContractTest`'s `emptyWindow` and panel routing
and `FakeWorkspace.closeWindow` nulling `Panel.instance`; THD Sourcing Crypto rebuilt after 6b, against 0.21.0. Left for
6a's final fix wave: Task 1's re-review items 1 and 2, Task 2's review Minors 2 and 3, Task 4's review Minors 5 to 7.
The rehearsal never reaching the store is pinned by the code's structure (it never calls `WindowManager`); headless,
`warmUp` rehearses nothing. `./gradlew check --continue`: **<counts>**. GUI checks for the user: with two tabs in the
main window (one split), a full window of two tabs, quit and start again: every window comes back where it was, the tab
each showed shown, with its panes, their sizes and the active one, the tab strip as before in each look (Enterprise
always shows it), and only the shown tab's shells start; a hidden tab's shells start when it shows; a renamed tab keeps
its name; maximize a window, quit, start: it comes back maximized; unplug a display between runs: every window lands on
the remaining one; close a full window, quit, start: it stays closed; close every tab of the main window, quit, start:
the empty workspace comes back; corrupt `workspace.toml`, start: one tab and the notice, the file kept as
`workspace.toml.bad`; with residency, close the main window and reopen from the Dock: the same windows; under
Enterprise, a restored terminal tab not yet shown has the idle dot on its strip entry (a shell that ended has the same;
its entry tells assistive technology of no running session) until it is first shown, when the dot turns to running, and
the status bar names only the shown tab's active pane. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (line 3) to "**Status: Tasks
1-5 executed (`30cc4ca35..3f273675e`), Tasks 6-10 executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and
list any deviation from the plan's text there.

```bash
git add README.md jasper-sdk/README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for saving the workspace

The configuration guide describes workspace.toml and what ui-state.toml
keeps now; the architecture names the store; the plugin guides say a
restored terminal may not have started; STATUS records plan 6a with its
rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage, tabs and panes spec 12: a window saves its tabs in order with the shown one (Task 7's writer; Tasks 4
  and 8), each tab its split layout and each pane its one item (Task 7; Rulings 32), no pane window or origin in the file
  (plan 8a's Task 2, Task 7's reader dropping `role = "pane"`), Tasks 1-5 kept where they fit (SDK 0.21.0, waiting
  terminals, window placements, the store: history notes), the codec and the snapshot changed with the model (Task 7;
  plan 8a). Spec 4's empty workspace (Task 8's `theEmptyWorkspaceComesBack`; Spec issues 5); spec 13's request from
  outside (Task 8's `aRestoredMainWindowOpensTheRequestsDirectoryInANewTab`; Spec issues 7). Workspace tabs spec 6.4's
  "every request restores it first" (Task 8 through `ensureMain`; Task 9 through the application's requests); 13 line
  by line: saved within one second on a virtual thread and when any window closes, on Quit and when going to the
  background (Tasks 5, 6 and 9), restored at start and on reopen with bounds checked against today's displays (Tasks 2
  and 8), the shown tab's terminals starting at once and the others reported not started until shown (Tasks 1 and 8),
  the rehearsal never reading or writing the file (Task 9); 15's unreadable-file row and the set-aside (Tasks 5, 8 and
  9); 16's `[main_window]` (Task 2). Out of scope here: plugins' items and placeholders, plan 6b's.
- The Review Focus has a test for each item in the task that owns its code (Tasks 6, 7, 8 and 9).
- Every quoted anchor of Tasks 6-10 was matched at its stated count in a replay on a `git archive` copy of `81f80c1af`
  (see Workspace), where the replayed copy then compiled with JBR 25's `javac` and the named suites ran headless.
- Type consistency: `WorkspaceStore.save()` (Task 6) as Task 8 relies on it; `WindowContent.restore(WindowRecord)`,
  `ParentTabsView.restore(ParentTabs, Map<TabId, Tab>)`, `WorkspaceView.restoreSaved(Layout, List<Tab>)` (named apart
  from plan 8c's `restore(Tab, Layout)`), `TerminalContent.startIfWaiting()`, `JasperWindow.restore(WindowRecord)`,
  `WindowManager.openMain`/`restoreChild`/`open(…, prepare)` (Task 8), `WindowContent.showsNoTerminal`/`afterNextPaint`
  and `JasperApplication.workspace`/`workspaceStore` (Task 9) keep one name and one signature from the task that makes
  them to the last task and plan 6b that use them.
