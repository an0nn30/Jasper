# Workspace tabs 6b: plugins' tabs come back Implementation Plan

**Status: not executed.** Re-planned in place on 2026-10-20 on top of plans 7a, 7b, 7c and the re-planned 6a (see
Workspace); the first version's Rulings stand except where "Superseded" says otherwise.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A plugin's tab comes back when Jasper restores its windows, in its saved parent tab and pane of whichever
window kind held it (the main window, a full window or a pane window): the plugin gives restore state (a restorer kind
and a few strings) when it opens the tab, and the restorer it registered rebuilds the tab there. The Editor reopens its
files, Remote's SSH tabs come back and reconnect or wait for Reconnect, and the sample's About comes back; a tab whose
plugin is missing, turned off or failed comes back as a placeholder that says why, with Close and, while its plugin
runs, Retry. The Credential Vault's manager is a dialog over the main window and does not come back.

**Architecture:** The SDK (still 0.21.0, plan 6a's) gains `dev.jasper.sdk.RestoreState`, an optional restore state on
`ContentSpec` and `SessionSpec`, `ContentTab.setRestore`, `Windows.restorer(kind, TabRestorer)` (a restorer may decline
with a reason) and `RestoreRequest`. In the application a tab carries its restore state (`Tab.restore()`, the app's twin
`layout.TabRestore`), and the `TabRegistry` keeps each plugin's restorers and, while one runs, a slot: the next content
tab that plugin opens (`TabRegistry.open`), or the next session it opens in the window being restored
(`WindowContent.openTerminal`), takes the saved id and that window, unshown, before any placement or wait for a main
window applies, and that window places it with its other saved tabs; meanwhile the window takes no other tab. Plan 6a's `WindowContent.restore` asks the window manager for each
`TabRecord.Content` of a window's `WindowRecord` (`WindowContent.savedTabs`) and places what comes back through
`ParentTabsView.restore` or a pane window's `WorkspaceView.restore`, exactly where it was saved. `WindowManager` saves a
plugin's tab as `TabRecord.Content` while its plugin has a restorer for its kind, never Jasper's own tabs, within the
size a read accepts, rebuilds it through the registry, and otherwise makes a `Tab.Kind.PLACEHOLDER` tab
(`workspace.PlaceholderContent`) that says why from how its plugin stands, which is saved again as it was and whose
Retry rebuilds the tab in the placeholder's pane of whichever parent tab and window hold it then. The testkit's fakes
restore as Jasper does (the tab placed, a session waiting until shown), and the contract suite runs on both runtimes.
The Editor defers a restored remote document until its tab is active and reads it with the Editor API's new quiet
read; Remote reopens remote documents before its hosts have loaded and connects restored SSH tabs one at a time per
window, quietly: a host-key question then is answered Cancel, and the tab offers Reconnect.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-19-jasper-parent-tabs-design.md`](../specs/2026-10-19-jasper-parent-tabs-design.md)
(sections 4's encoding, 9, 10 and 11's quit row), which amends and wins over
[`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1 decision 5, 9.2's placeholder, 13's content tabs, 14's `ContentSpec`, `SessionSpec` (amended in
`27874bf7d`) and `Windows.restorer` rows and its plugin list, 15's disabled-plugin row, 17's SDK line and 18). The
second half of section 18's plan 6; [6a](2026-10-18-workspace-6a-save-restore.md) (saving, restoring windows and
terminal tabs, SDK 0.21.0) executes first, after plans [1](2026-10-17-workspace-1-single-instance.md) to
[5](2026-10-18-workspace-5-tear-out.md) and [7a](2026-10-19-workspace-7a-parent-tabs.md),
[7b](2026-10-19-workspace-7b-pop-out.md) and [7c](2026-10-19-workspace-7c-parent-tab-drag.md).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 6a's last commit. Run every command from the worktree root. This plan was first
written on `22b702456` with plans 1 to 6a's first version applied; it was re-planned for parent tabs on `ec41c97db`
("Docs: workspace tabs plan 6a re-planned on parent tabs"). Every anchor below was matched at its stated count, and
every edit script of Tasks 1-6 replayed in order, on `git archive` copies of `ec41c97db` and of `e80f2c322` ("Plan 7b:
refresh anchors for execution", after plan 7a's final fixes), where plan 7a is executed, after plan 7b (its refreshed
copy), 7c and the re-planned 6a; and on a copy of `8e129ff34` ("Docs: workspace tabs plan 7a, parent tabs") after plan
7a's, plan 7b's (as committed in `8dcdf4f0a`), plan 7c's and plan 6a's file creations and edit scripts, where every
block matched but Task 4's manifest edit: Remote is 0.14.0 there and 0.15.0 from `0ca6d3c51` on, which the edit quotes
(Rulings 18). Line numbers are the first two replays' (they agree). Nothing was compiled or run. Every edit quotes the text it replaces, and the scripts stop with a message when
that text is not found the stated number of times: re-find the quoted text (a plan before this one may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner; never edit around a missing anchor. No anchor
here is in the drag code plans 5 and 7c wrote, nor in `ParentTabsView.openParentTab`. Later tasks anchor on text
earlier tasks of this plan wrote, and quote it as those tasks leave it.

**Hand-offs absorbed** (`.superpowers/sdd/workspace-tabs-series.md`, plan 6a's ledger
`.superpowers/sdd/2026-10-18-workspace-6a-save-restore/progress.md` and STATUS template, and the plan-writing ledger
`.superpowers/sdd/plan-writing/progress.md`):
- Plan 6a's ledger and plan 4b's final review: restored content tabs never go through `TabRegistry.open`'s way into a
  window (a random id, a placement, a wait for the main window being composed). The slot is checked in `open` before it
  makes an id or looks for a window, once an open tab of the same key has been selected as always: the plugin's tab is
  registered under its saved id straight into the window being restored, as `TabRegistry.add` registers a restored
  terminal tab, and that window places it (Task 1; Rulings 4 and 23).
- Plan 6a's ledger: content records skip `TabRegistry.APP_OWNER`. The snapshot never saves Jasper's own tabs (Rulings
  3), and a saved record that names `jasper`, which only a file this build did not write could hold, is left out at
  restore, never a placeholder (Task 2; Rulings 22).
- Plan 6a's ledger: `ContentTab.window()` returns `UUID(0,0)` while a tab waits for the main window, which now waits
  through every child window's restore. A restored tab never waits: it takes the window being restored at once, so its
  `window()` names that window even while the main window is still being composed; a tab a restorer opens besides the
  saved one waits as an owner-less tab does and names no window until it shows, as `ContentTab.window()`'s Javadoc
  says (Task 2's `aRestoredTabNamesItsWindowAtOnceAndARestorersOtherTabOpensOnceTheWindowShows`; Rulings 23 and 24).
- The series notes, plan 4b's final review: `AppContractTest`'s `emptyWindow` makes the main window when none is open,
  where the application opens the main window and then a child window. Task 1 fixes it, with a contract case on both
  runtimes (Rulings 25).
- The series notes, plan 4b's Task 5 and final review: `FakeWorkspace.closeWindow` closing and nulling `Panel.instance`
  with `AppContractTest` clearing its panel caches "when a fake main window can close", and `AppContractTest`'s panel
  routing. They close without code: neither runtime's harness can close a main window, panels live in the main window
  only, and no contract case toggles a panel, so neither change could be observed or tested (Rulings 26). The STATUS
  entry says so.
- Plan 6a's STATUS template, "Left for plan 6b": plugins' tabs (restore state, restorers, placeholders) within SDK
  0.21.0 (Tasks 1 and 2; Rulings 27), restored with their saved ids straight into their windows and parent tabs (Rulings
  4 and 19), never Jasper's own (Rulings 3 and 22), the never-open window (Rulings 23), the two `AppContractTest` and
  `FakeWorkspace` items (Rulings 25 and 26), and THD Sourcing Crypto rebuilt once after this plan, against 0.21.0 (Task
  6; Rulings 14).
- The plan-writing ledger, plan 7c's review: `WindowManager.moveToNewWindow` is gone in every form after 7c; Task 2's
  tests move a placeholder to another window with plan 7b's `WindowManager.popOutTab(Tab)`, which makes a pane window.
- The series notes' version rule: each plugin bumps to the next minor from its version at execution (Rulings 18).
- The user's decision of 2026-10-19 (series notes): the Vault manager is a modeless dialog over the main window
  (`144ff89f1`, `7b3149ab4`, `8f1b36f22`), so the first version's Vault restorer goes (Spec issues 1; Rulings 10
  superseded).

## Spec issues

1. Workspace tabs spec decision 2 and section 14's plugin list make the Credential Vault manager a content tab that
   comes back with the windows. The user decided on 2026-10-19 (series notes) that it is a modeless dialog over the main
   window, and `144ff89f1` made it one; a dialog is not part of the workspace and closes with its window (`7b3149ab4`;
   6a's Verified facts 10). This plan follows the user: the Vault keeps no restorer and its version (Rulings 10
   superseded, Rulings 28).
2. None open besides. Spec 14's `SessionSpec` row (amended in `27874bf7d`, "sessions carry restore state") gives a
   session's tab restore state, so Remote's SSH tabs come back through Remote like content tabs (Rulings 2). Section
   15's disabled-plugin row ("Its tabs close; at the next start they restore as placeholders") holds without code:
   turning a plugin off takes effect at the next start, so Quit saves its open tabs, and the next start, where it does
   not run, makes them placeholders (Rulings 7). Parent tabs spec 10 keeps parent tabs out of the SDK: a restorer sees
   only the window being restored, and Jasper puts the tab in its parent tab and pane (Rulings 19).

## Superseded

What the parent-tabs model, the user's Vault decision or the controller's brief for this re-plan makes moot in the first
version. Rulings 1 to 9, 11 and 13 to 17 stand, their numbers kept, 4 and 15 narrowed as below; their fact numbers
follow the new Verified facts (Rulings 7, 9 and 17 cite facts 10, 7 and 7); 18 to 31 are this re-plan's.

- **Rulings 10 (the Vault's manager comes back) is superseded** by the user's decision of 2026-10-19: the manager is a
  modeless dialog over the main window (`144ff89f1`), and dialogs are not restored (Spec issues 1, Rulings 28). Task 5's
  Vault part, its test and the credential-vault guide's edit go.
- **Rulings 12 (Editor 0.11.0, Remote 0.14.0, Vault 0.7.0, sample 0.4.0) is superseded** by the series rule (next minor
  from each plugin's version at execution): plan 4b already took those numbers and `0ca6d3c51` moved Remote to 0.15.0,
  so Rulings 18 gives Editor 0.12.0, Remote 0.16.0 and sample 0.5.0, and the Vault, which changes nothing here, keeps
  0.7.0.
- **Rulings 4 and 15 are narrowed for sessions**, as the application always did (`TabRegistry.slot` names the window):
  the first content tab the plugin opens takes the slot whatever window it names, and a session only when it opens in
  the window being restored; the fake now does the same (the plan's review, I2).
- **The tests' window records are parent tabs.** Plan 6a's `WindowRecord` holds a `WindowLayout`; every saved window
  here is `ParentTabs` or a pane window's `PaneLayout`, and Task 2's tests move a placeholder with `popOutTab` (7c's
  review) instead of the first version's `moveToNewWindow`.
- **The Review Focus, Verified facts and File structure are rewritten** for the parent-tabs code; the first version's
  Review Focus items stand inside the new list.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- The SDK stays 0.21.0 (plan 6a's bump; 6a's Rulings 2; Rulings 27): every SDK and testkit addition here carries
  `@since 0.21.0`, and no manifest range changes. The Editor (0.11.0 to 0.12.0), Remote (0.15.0 to 0.16.0) and the
  sample (0.4.0 to 0.5.0) bump their versions because their behaviour changes; the Vault and the other plugins keep
  theirs (Rulings 18). Should a manifest hold another version at execution, take the next minor from it, adjust the
  edit, and record it in the banner. The Editor's API (`dev.jasper.editor.api`) gains one default method,
  `EditorDocument.readQuietly`, which older callers never see.
- Plan 2's `WorkspaceFile` changes only as Task 2 says: `MAX_BYTES` becomes public and `encodedBytes(Snapshot)` is new,
  so a snapshot can stay within what a read accepts. Plan 6a's records, reader, writer and `keeping` stay as they are:
  a content record already rides in a window's `WindowRecord.tabs` beside its terminal records.
- Clean break: `ContentSpec` and `SessionSpec` gain a component, and their old constructors stay as the ordinary way to
  make a spec that does not come back; no deprecation.
- Package edges (package-info "Allowed outgoing" lists; `verifySdkArchitecture`, `verifyApplicationArchitecture`,
  `verifyPluginArchitecture`): nothing gains an edge. `RestoreState` is in the SDK's root package, which `ui` and
  `terminal` already reach and which reaches neither; `TabRestore` is in `layout`, which `contributions`, `terminals`,
  `workspace` and `plugins` already reach; `workspace` learns how a plugin stands through `WindowManager.PluginState`,
  never `plugins`.
- Plugins' code runs contained: a restorer runs through `Containment.attempt` on the EDT; a reason it returns is a
  placeholder's, with no incident, and what it throws is a placeholder's reason and the plugin's failure, never an
  exception out of a window's restore.
- No motion (spec 4; AGENTS.md): a placeholder, a restored plugin's tab and a tab Retry rebuilds appear at once.
- Dialogs are not restored: no restorer here opens one, and nothing asks for a password, or about a host key, while
  windows come back (Rulings 29).
- A restore state is saved in plain text in `workspace.toml`: no plugin here puts a secret in it (a host id, a name, a
  folder, a document key, the word "about").
- The theme-migration ledger (`docs/design/theme-engine/migration.csv`, checked by `ThemeMigrationCoverageTest`): a new
  Java file under `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` or a plugin's `src` whose text matches its
  `CANDIDATE` pattern (a Swing or AWT import, the word "appearance", `new J…`, `new …List`, `…Panel`, `…View`,
  `…Window`, …) gets a row in the task that creates it, with the `ledger.py` helper: `PlaceholderContent` and
  `PluginTabRestoreTest` (Task 2). Every other new file was checked against the pattern with a Python copy of the test
  and matches none: `RestoreState`, `TabRestorer`, `RestoreRequest`, `TabRestore`, `RestoreStateTest` and
  `SavedRemoteDocument`; keep it so.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment, and an SDK addition carries `@since 0.21.0`.
- `WindowContent` and its tests import both `java.awt.*` and `java.util.*`: write `java.util.List` in full there.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
  `AppDocumentationTest` checks every guide's links and that a guide's `<!-- example:… -->` snippet equals its source
  between the `// example:…:start` and `:end` markers (Task 5 changes the sample's `pluginpanels` block and the guide's
  copy together).
- Tests are headless: windows are plan 6a's `TestWindows` over stand-in frames, sessions are `SessionRequest`s whose
  attempts the test holds, and the plugins' tests run on the testkit's `FakePluginHost`.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan6b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan6b
cp build/plan6a/edits.py build/plan6a/ledger.py build/plan6b/ 2>/dev/null || cp build/plan7a/edits.py build/plan7a/ledger.py build/plan6b/ 2>/dev/null || echo "the helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan6b in place of build/plan3a"
ls build/plan6b/edits.py build/plan6b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'`
from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan6b")`. Java text inside a script is in
triple-quoted strings, so `\n` and `…` in Java source stay as written.

## Review Focus

1. **The slot.** While a restorer runs, the first content tab its plugin opens, whatever window or placement it named,
   or the first session it opens in the window being restored, takes the saved id (while it is free) and that window,
   is not shown, and the window places it where it was saved; one restore runs at a time, only a tab still open in that
   window counts, and the window being restored takes no other tab meanwhile, a waiting tab of the same key included,
   on both runtimes. Task 1: `TabRegistryTest.aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot`, the four restore
   contract tests on both runtimes, `FakeContractTest.whileARestorerRunsTheWindowBeingRestoredOpensNoOtherSession` and
   `HostedUiTest.aSavedStateTheSdkRefusesRestoresNothingAndRaisesNoIncident`; Task 2:
   `PluginTabRestoreTest.aSessionsTabComesBackThroughItsPluginAndConnectsWhenFirstShown`,
   `aRestoredTabNamesItsWindowAtOnceAndARestorersOtherTabOpensOnceTheWindowShows`.
2. **Where it comes back.** A plugin's tab comes back in its saved parent tab (a hidden one too) and pane, in the main
   window, a full window or a pane window, and saving straight after the restore writes what was read. Task 2:
   `aPluginsTabComesBackInItsSavedParentTabAndPaneInEveryKindOfWindow`; plan 6a's
   `WorkspaceRestoreTest.aChildWindowHoldingOnlyAMissingPluginsTabsComesBackWithTheirPlaceholders`.
3. **What is saved.** A plugin's tab only while its plugin restores its kind, a placeholder exactly as it was saved,
   never Jasper's own tab (the Errors tab), going or coming, and never more than a read accepts. Task 2:
   `aPluginsTabWithoutRestoreStateOrRestorerIsNotSavedAndJaspersOwnTabsNeverAre`, `aSavedTabNamingJasperItselfIsLeftOut`,
   `aTabWhosePluginIsNotRunningComesBackAsAPlaceholderAndIsSavedAgainAsItWas`,
   `aWorkspaceTooLargeToReadBackLeavesOutPluginsTabsLargestFirst`.
4. **Placeholders.** The reason follows how the plugin stands (asked again when it shows and on Retry), Retry only while
   it runs, Retry puts the rebuilt tab in the placeholder's pane and place, and Retry and Close act in the window that
   holds it after a pop-out. Task 2: `aRestorerThatFailsLeavesAPlaceholderAndRetryPutsItsTabInThePlaceholdersPlace`,
   `aPlaceholderSaysHowItsPluginStandsAndOffersRetryOnlyWhileItRuns`, `aPlaceholderPoppedOutToAPaneWindowRetriesThere`,
   `aPlaceholderInAPaneWindowClosesFromTheWindowThatHoldsItNow`.
5. **Nothing asks, and expected misses raise no incident.** A restored SSH tab connects only when first shown, after
   the hosts have loaded, when every hop signs in alone, one after another in a window; a restored remote document reads
   only when its tab is first active (or Load), asking nothing; neither asks about a host key; restorers that miss
   decline. Task 4: `RemotePluginTest.anSshTabComesBackAndConnectsOnlyWhenSigningInAsksNothing`,
   `restoredSshTabsShownTogetherInOneWindowAllConnect`, `aRestoredSshTabWhoseHostKeyIsUnknownAsksNothingAndOffersReconnect`,
   `aRestoredSshTabClosedWhileItWaitsPassesItsTurnOn`, `RemoteEditingTest
   .aKeyReopensBeforeTheSavedHostsHaveLoadedAndFindsThemWhenRead`, `aQuietReadSignsInOnlyWhenNothingWouldBeAsked`,
   `SessionPaneTest.aRestoredSessionLeftNotConnectedOffersReconnect`; Task 3:
   `EditorPluginTest.aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive`,
   `EditorTabTest.aDeferredDocumentOffersLoadReadsQuietlyFirstAndAsUsualOnRetry`.
6. **The Editor.** Its untitled buffer is not saved, a saved document comes back by its key, and a key nothing opens
   declines with why, telling a missing plugin from a missing file. Task 3: `EditorPluginTest.aDocumentComesBackFromItsKeyAndAnUntitledOneIsNotSaved`.
7. **The hand-offs.** `AppContractTest` opens the main window and then a child for a tab placed in a new window while
   none is open, as the application does. Task 1:
   `PluginContractTest.aTabPlacedInANewWindowWhileNoWindowIsOpenOpensTheMainWindowAndAChildForIt` on both runtimes.

## Verified facts

From the replays of plans 7a, 7b, 7c and 6a (see Workspace); line numbers are the replays' on `ec41c97db` and `e80f2c322`.

1. `ContentSpec(String key, String title)` (`jasper-sdk/…/ui/ContentSpec.java:8`) and `SessionSpec(String title,
   Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector)` (`terminal/SessionSpec.java:18`) are
   records; `Windows.openTab(spec, owner, content, placement)` is abstract with a three-argument default
   (`ui/Windows.java:28`, `:46`); `ContentTab` and `Windows` have one implementation each in the application
   (`plugins/HostedUi.java`, `OwnedContent` and `windows()`) and in the testkit (`FakeContent`, `FakeUi.windows()`). The
   SDK's root package reaches no other SDK package; `ui` and `terminal` reach it, and `terminal` may not reach `ui`.
2. `TabRegistry.open(plugin, key, title, owner, component[, placement])` (`contributions/TabRegistry.java:207`, `:217`)
   first selects an open tab of the same plugin and key, then makes a tab with a random id and attaches it through a
   window's host: a `NEW_WINDOW` placement through `Windows.emptyWindow`, and an owner-less tab, or one whose owner has no
   host, through the main window, waiting (`waitForMain`, `:285`) while it is being composed (`Windows.main` is then
   null); `TabRegistry.add(TabId, …)` (`:167`) registers a tab under a given id with no host call and refuses
   `Tab.Kind.HOSTED`. `HostedUi.openComponent` (`plugins/HostedUi.java:652`) passes a window only while
   `HostedTerminals.ownsOpenWindow` holds, which a window being restored already does (its content is connected before
   its restore), and `OwnedContent.window()` (`:546-549`) names `new UUID(0, 0)` for a tab with no window.
3. Plan 6a's `WindowContent.restore(WindowRecord)` (`workspace/WindowContent.java:1325`) rebuilds only
   `TabRecord.Terminal` records (`restoredTerminal`, `:1354`, through `TabRegistry.add`), takes the others out as
   closing them would (`WorkspaceFile.keeping`), mounts every restored tab (`mount`, `:1376`) and places them in one
   change: a main or full window's through `ParentTabsView.restore(ParentTabs, Map<TabId, Tab>)`
   (`ParentTabsView.java:228`), a pane window's through `WorkspaceView.restore(Layout, List<Tab>)` (`WorkspaceView.java
   :104`), each requiring exactly the tabs its layout names; it returns how many tabs came back, and the window manager
   leaves out a child window that restores none (`WindowManager.restoreChild`, `:297`). A restored terminal tab's pane
   waits (`TerminalPane.restored()`) and starts once a pane of its window's shown parent tab shows it at the end of an
   EDT turn (`startShown`, `:1301`); a provided session's pane connects then. `WindowManager.tabRecord` (`:730`) saves
   only local terminal tabs; `TerminalContent.record()` is empty for a provided session.
4. `WindowManager.ensureMain` (`:217`) keeps `openingMain` true through the main window's restore and every child
   window's (`openMain`, `:250`); the main window is the manager's (`main`, so `window(UUID)` finds it) from its `make`
   on, before it restores. Every window opens through `open(Opening, make, prepare)` (`:663`), where the content is
   composed and connected, then prepared (a restore) before its frame exists. Plan 7b's `popOutTab(Tab)` (`:333`) moves a
   tab into a new pane window; a child window, a pane window included, closes when its last tab closes.
5. A tab is active while it is the selected tab of the active pane of its window's shown parent tab
   (`WindowContent.update`, `:2079`); a placeholder's tab kind matters only where the code names `HOSTED` or
   `TERMINAL`: `WindowContent.closeTab` (`:1967`) asks a guard for `HOSTED` tabs only, the theme loop (`:2204`) themes
   `HOSTED` tabs, and `WindowTerminals` (`:50`) reports every non-terminal tab to plugins as `TabEntry.Kind.CONTENT`.
6. Plan 4b's Errors tab is app-owned: owner `TabRegistry.APP_OWNER` (`jasper`, `TabRegistry.java:39`), key `errors`.
   Plan 2's codec reads `kind = "content:<plugin id>:<kind>"` for any plugin id with no colon, `jasper` included
   (`persistence/WorkspaceFile.java:93`).
7. Remote opens an SSH tab with `new SessionSpec(host.name(), …)` (`plugins/remote/…/RemotePlugin.java:448`) and reads
   its hosts in the background at start (`store.load()`, `:256`; `HostStore.reload()` returns a future,
   `hosts/HostStore.java:53`); `Auth` is `Agent`, `Vault` or `VaultKeys`. A window shows one connection attempt at a
   time: `connect` refuses a second with "Another connection is already in progress" (`:369`, `focusExistingAttempt`,
   `:436`). Its reopener for Recent Files (`edit/RemoteEditing.java:97`) needs the saved hosts in memory, and an edit
   channel with no owner window fails rather than prompt (`edit/EditChannels.java:94`). Remote is 0.15.0
   (`0ca6d3c51`).
8. The Editor (0.11.0) opens a document's tab with `new ContentSpec("document:" + UUID, name)`
   (`plugins/editor/…/ui/EditorWindow.java:769`) and reads it at once (`:191`); Recent Files reopens a document by its
   key through `Reopeners.resolve(String)` (`recent/Reopeners.java:48`): `local:` itself, other prefixes through the
   plugin that registered them (Remote's `remote:`).
9. The sample (0.4.0) opens its About with `new ContentSpec("about", "About Sample")`
   (`plugins/sample/…/SamplePlugin.java:147`), inside the `pluginpanels` example the authoring guide copies. The Vault's
   manager is `ui/VaultManagerDialog`, a modeless plugin dialog over the main window (`144ff89f1`); a plugin dialog
   closes with its Jasper window (`7b3149ab4`).
10. Turning a plugin off writes its state for the next launch (`plugins/PluginAdmin.setEnabled`); the running plugin keeps
    running. `PluginRuntime` keeps each plugin's `PluginStatus` (`ACTIVE`, `DISABLED`, `NEEDS_CONSENT`, `SKIPPED`,
    `FAILED`; `statuses`, `PluginRuntime.java:424`) and has no public accessor for it. `Containment.attempt`
    (`plugins/Containment.java:34`) records what a plugin's code throws as its failure. Plugins start
    (`JasperApplication.startPlugins`) before the first window opens (`ApplicationBootstrap`'s `openFirstWindow`).
11. `WorkspaceFile.read` sets aside a file over `MAX_BYTES` (256 KiB, package-private, `persistence/WorkspaceFile
    .java:210`); `encode(Snapshot)` (`:597`) is the text `write` writes.
12. `AppContractTest`'s `TabRegistry.Windows` gives its window-making supplier as `emptyWindow`
    (`jasper-app/src/test/…/plugins/AppContractTest.java:172`), which makes the main window first when none is open;
    `WindowManager.emptyWindow` (`:764`) opens the main window and then a child window. Neither harness can close a main
    window (`FakeWorkspace` closes only a child window; `TerminalFixture` likewise), panels open in the main window only,
    and no contract case toggles a panel.

## Rulings

Record them in `docs/STATUS.md` (Task 6). "What — why". Rulings 1 to 17 are the first version's, their numbers kept
(see Superseded); 18 to 28 are this re-plan's.

1. **Restore state is `dev.jasper.sdk.RestoreState(kind, values)`: a kind of 1 to 64 letters, digits, dots, underscores
   or hyphens, at most 32 values of at most 4096 characters in all, in the SDK's root package** — spec 14 ("a restorer
   kind and a small string map"); `SessionSpec` in `terminal` needs it and `terminal` may not reach `ui`.
2. **`SessionSpec` carries restore state too, and a session's terminal tab is saved as `TabRecord.Content` under its
   plugin** — spec 14 as amended in `27874bf7d` ("sessions carry restore state").
3. **A plugin's tab is saved only while its plugin has a restorer for its kind; an app-owned tab
   (`TabRegistry.APP_OWNER`) never is** — plan 2 Rulings 20; the Errors tab must never come back as "plugin missing".
4. **A restorer rebuilds a tab by opening it the way the plugin always does (`Windows.openTab`, `Terminals.openTab` with
   a session) while the registry holds a slot: the first content tab that plugin opens, whatever owner or placement it
   named, or the first session it opens in the window being restored, takes the saved id, while no open tab has it, and
   that window, unshown, and the window places it with its other tabs; the slot is taken before `open` looks for a window, so neither `emptyWindow` nor the wait for a main
   window being composed applies; a restore asked for while one runs says "another tab is being restored"** — a
   restored tab is the plugin's ordinary tab, and its saved id keeps it in the saved layout.
5. **`TabRestorer.restore` returns empty once the tab is open, or why it cannot come back; a restorer runs contained on
   the EDT, and a reason it returns is the placeholder's with no incident, while what it throws is the placeholder's
   reason and the plugin's failure; opening nothing says "it opened no tab"** — expected misses (a file or host gone)
   must not raise an incident at every start, and plugins' code is contained everywhere.
6. **A placeholder (`Tab.Kind.PLACEHOLDER`, `workspace.PlaceholderContent`) keeps the saved title and record and is
   saved again unchanged. It says why from how its plugin stands: "The plugin <id> is not installed.", "<name> is turned
   off.", "<name> waits for your review in Manage Plugins.", "<name> could not start.", or, while it runs, "<name> could
   not restore this tab: <reason>." with Retry; it asks again whenever it shows and when Retry is pressed (a plugin that
   runs now offers Retry). Retry rebuilds the tab in the placeholder's group and place, selected, and Retry and Close act
   in whatever window holds the placeholder then** — spec 9.2 and 13; a placeholder can move like any tab.
7. **How a plugin stands is `PluginRuntime.standing` (running, waiting for review, turned off, failed; not installed when
   no plugin has the id), which the application gives the window manager as `WindowManager.PluginState`; turning one off
   takes effect at the next start, so its tabs, saved at Quit, come back as placeholders** — spec 15's row; fact 10; the
   `workspace` package may not reach `plugins`.
8. **The Editor saves a document's key (`document`, `key`) and its restorer reopens it through `Reopeners.resolve`, as
   Recent Files does (`EditorWindow.openRestored`): a local file is read at once; a document on another host waits until
   its tab is first active or Load is pressed, then reads with the Editor API's new `EditorDocument.readQuietly`, which
   asks nothing, and Retry reads as usual; an untitled document never saved has no key and is not saved; a key no running
   plugin opens, or a file already open, declines with why** — spec 13; a start must not sign in to every remote
   document, nor prompt.
9. **Remote saves an SSH tab's host id, name and folder (`ssh`); the restored tab waits until first shown, then, once
   the hosts have loaded, connects when every hop signs in through the agent or the unlocked Vault, after any connection
   in progress in its window (`connectWhenFree`), and otherwise shows "Not connected: Reconnect to sign in" with
   Reconnect; a state naming no host, or a host gone once the hosts have loaded, declines "it names no saved host"** —
   spec 13 ("chooses between reconnecting and a Reconnect state"); a start must not ask for a password, and a window
   shows one connection attempt at a time (fact 7).
10. *Superseded by Rulings 28:* the Vault's manager is a modeless dialog over the main window (`144ff89f1`), which is
    not restored. (Was: "The Vault's manager (`manager`) comes back as the Vault is, locked or not, asking nothing".)
11. **The sample's About (`about`) comes back through the one method its action uses; the sample's echo session is not
    saved** — the guide's pattern; a demo session has nothing to come back to.
12. *Superseded by Rulings 18:* the series takes each plugin's next minor at execution, and plan 4b and `0ca6d3c51`
    already took these numbers. (Was: "Editor 0.11.0, Remote 0.14.0 (plan 4b took 0.13.0), Vault 0.7.0, sample 0.4.0;
    the other plugins keep their versions".)
13. **A restored session that is not connected offers Reconnect, not Retry** — it was connected when it was saved.
14. **THD Sourcing Crypto rebuilds once, against 0.21.0, and 0.21 needs nothing more of it** — it names
    `SessionState.RUNNING` in one fixture, switches over no `SessionState` and opens only a dialog.
15. **The testkit's fake restores as Jasper does (`FakePluginHost.restoreTab`): the first content tab the plugin opens
    takes the window being restored, whatever it named, and so does the first session it opens there, placed, neither
    selected nor active, and the window takes no other tab meanwhile; a session's tab waits,
    `NOT_STARTED` with no connection attempt, until it is shown; a reason the restorer returns is kept in
    `declinedRestores()`, never `failures()`. It puts the tab at the end of the window's active group, where Jasper puts
    it where it was saved** — plugin tests must see the lazy start and the declines they ship with.
16. **A snapshot that `WorkspaceFile.encodedBytes` finds larger than `MAX_BYTES` leaves out tabs until it fits:
    plugins' tabs first, the largest restore state first, then terminal tabs; each is logged** — a file a read would set
    aside loses every window at the next start, while a restore state holds at most 4096 characters, so this takes many
    plugin tabs.
17. **Remote's reopener answers before its saved hosts have loaded: a recorded key reopens a document
    (`SavedRemoteDocument`) that finds its hosts when first used; once they have loaded, a key whose host is gone does
    not reopen** — Jasper restores its windows while the hosts load in the background (fact 7), and a restored remote
    document must not become a placeholder for that.
18. **Editor 0.12.0, Remote 0.16.0 and sample 0.5.0, each the next minor from its version now (0.11.0, 0.15.0, 0.4.0);
    the Vault (0.7.0) and the other plugins keep theirs** — the series rule (next minor at execution) and plan 4b
    Rulings 1's (a version moves with behaviour); the Vault changes nothing here.
19. **A plugin's tab comes back in its saved parent tab and pane, in whichever kind of window was saved (the main
    window, a full window, a pane window): its `TabRecord.Content` rides in the window's `WindowRecord.tabs`, its
    restorer runs while plan 6a's `WindowContent.restore` rebuilds that window, before its frame exists, and the window
    places what came back with its terminal tabs in one change (`ParentTabsView.restore`, or a pane window's
    `WorkspaceView.restore`); the restorer sees only the window (`RestoreRequest.window()`), and Retry rebuilds in the
    placeholder's pane of whichever parent tab and window hold it when pressed** — parent tabs spec 9 ("covers every
    window kind") and 10 (parent tabs and pane windows are not exposed to plugins).
20. **A content record whose restorer rebuilds nothing becomes a placeholder, so a window's restore keeps every tab it
    saved: a full or pane window holding only plugins' tabs comes back, holding placeholders when their plugins do not
    run** — spec 9.2 and 13 ("becomes a placeholder"); plan 6a left such a window out only because nothing could
    rebuild its tabs yet (6a's `aChildWindowWithNothingToRestoreIsLeftOut`, renamed and turned round here).
21. **The size limit (Rulings 16) takes a tab out as closing it would (`WorkspaceFile.keeping`): a parent tab it leaves
    with none goes, and a child window left with none is left out** — plan 6a Rulings 14: what is saved keeps parent tabs
    spec 4's invariants.
22. **A saved content record naming `jasper` (`TabRegistry.APP_OWNER`) is left out at restore, as a tab that cannot
    come back is, never a placeholder; with Rulings 3 Jasper's own tabs are neither saved nor restored** — plan 6a's
    hand-off ("content records skip `TabRegistry.APP_OWNER`"); only a file this build did not write could hold one
    (fact 6), and "the plugin jasper is not installed" would be false.
23. **A restored tab takes the window being restored at once, even while the main window is still being composed: its
    `Tab.window()`, so its `ContentTab.window()`, names that window from the moment its plugin opens it, never the
    never-open `UUID(0,0)`** — plan 6a's hand-off: the main window is composed through every child window's restore
    (fact 4), and a restorer that reads its tab's window must find the window it restores.
24. **While a restorer runs, the window being restored takes only the saved tab: another content tab its plugin opens
    with that window as owner goes where an owner-less tab goes (the main window, waiting while it is being composed,
    and naming no window meanwhile), and another session it opens there is refused (empty)** — the window places its
    saved tabs at once after its restorers ran (fact 3), and a tab placed in it before would make that window's restore
    fail and its saved windows be set aside.
25. **`AppContractTest`'s `emptyWindow` opens the main window first when none is open, then a child window for the
    tab, as `WindowManager.emptyWindow` does; a contract case pins it on both runtimes** — plan 4b's final-review
    hand-off (fact 12); the testkit's fake already does it.
26. **The other two test-harness hand-offs close without code: `FakeWorkspace.closeWindow` nulling `Panel.instance`
    with `AppContractTest` clearing its panel caches, and `AppContractTest`'s panel routing** — their trigger never
    arrives in this series: neither harness can close a main window, panels live in the main window only, and no
    contract case toggles a panel (fact 12), so neither change could be observed or tested; the STATUS entry names them
    for the plan that first adds such a case.
27. **6b's SDK additions stay in 0.21.0, with no bump of their own** — plan 6a's Rulings 2 and the controller's brief:
    6a and 6b execute back to back on this unmerged, unpushed branch, so no 0.21.0 ships without them, and THD Sourcing
    Crypto rebuilds once, against 0.21.0, after 6b (Rulings 14).
28. **Dialogs are not restored: the Vault's manager, a modeless dialog over the main window, and a plugin's dialog
    (the sample's Details sheet) close with their window and do not come back** — the user's decision of 2026-10-19 and
    the controller's ruling that a plugin dialog closes with its window (Spec issues 1); a restore must open nothing that
    asks.
29. **A restored SSH tab's first connect and a restored remote document's quiet read are quiet: while one runs in a
    window, Remote answers a host-key question there Cancel (`ConnectAttempt.quiet`, set by `connectWhenFree`'s connect;
    `EditChannels.submitQuietly`, whose channel opens through `RemotePlugin.quietly`); a quiet connect that fails closes
    its overlay and leaves the tab "Not connected: Reconnect to sign in"; Reconnect and Retry ask as usual** — the
    controller's ruling: `HostKeyVerifier.Decision.ONCE` trusts without saving, so a host accepted Once is unknown again
    at the next start, and a start must not ask before the user does.
30. **Only a tab still open in the window being restored is the saved tab, and a saved state the SDK refuses (a file
    this build did not write) runs no restorer: its placeholder says "its saved state is not valid", with no incident**
    — a restorer that closes or moves its tab has not rebuilt it in its place, and contained plugin code never sees
    values the SDK would refuse.
31. **The Editor's decline says which: "no running plugin opens remote: documents" (the key's own prefix) when no
    running plugin handles the key's prefix (`Reopeners.handles`), else "its file can no longer be opened"** — Remote running without the host a key
    names is not Remote missing, and the placeholder should say so.

## File structure

- SDK (`jasper-sdk`): `RestoreState` (new), `ui/ContentSpec`, `terminal/SessionSpec` (whole files), `ui/TabRestorer`
  and `ui/RestoreRequest` (new), `ui/Windows`, `ui/ContentTab` (Task 1).
- Testkit (`jasper-sdk-testkit`): `FakeContent`, `FakeUi`, `FakePluginHost`, `FakeWorkspace`, `FakeTerminals`,
  `FakeSessions`, `ContractHarness`, `PluginContractTest`, `FakeContractTest` (Task 1).
- Application:
  - Task 1: `layout.TabRestore` (new), `layout/package-info`, `contributions.Tab`, `TabRegistry`,
    `terminals.SessionRequest`, `plugins.HostedSessions`, `HostedUi`, `workspace.WindowContent`;
  - Task 2: `workspace.PlaceholderContent` (new), `contributions.Tab`, `persistence.WorkspaceFile`,
    `workspace.WorkspaceView`, `WindowContent`, `WindowManager`, `plugins.PluginRuntime`, `application.JasperApplication`;
  - Task 4: `workspace.TerminalPane`.
- Plugins: the Editor's `api/EditorDocument`, `session/DocumentSession`, `ui/EditorTab`, `ui/EditorWindow`,
  `recent/Reopeners`, `EditorPlugin` (Task 3); Remote's `RemotePlugin`, `edit/RemoteEditing`, `edit/RemoteDocument`,
  `edit/EditChannels`, `edit/package-info`, `edit/SavedRemoteDocument` (new) and `AGENTS.md`'s line on them (Task 4); the
  sample's `SamplePlugin` (Task 5); their `plugin.toml` files.
- Tests: `RestoreStateTest` (new), `TabRegistryTest`, `TerminalFixture`, `AppContractTest`, `HostedUiTest`,
  `FakeContractTest` (Task 1);
  `PluginTabRestoreTest` (new), `WorkspaceRestoreTest` (one case), `PluginRuntimeTest`, `JasperApplicationWorkspaceTest`
  (Task 2); `EditorPluginTest`, `EditorTabTest` (Task 3); `RemotePluginTest`, `RemoteEditingTest`, `SessionPaneTest`
  (Task 4); `SamplePluginTest` (Task 5).
- Documents (Task 6): `jasper-sdk/README.md`, `docs/plugin-authoring.md` (and its `pluginpanels` snippet in Task 5),
  `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/configuration.md`, `docs/editor.md`, `docs/remote.md`, `docs/STATUS.md`; the ledger
  in Task 2.

## Before you start

```bash
git branch --show-current                                                                                  # claude/workspace-tabs
git status --short                                                                                         # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for saving the workspace"           # 6a's last commit
grep -c 'VERSION = "0.21.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                                         # 1
grep -c 'public record ContentSpec(String key, String title) {' jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentSpec.java   # 1
grep -c 'public Optional<WorkspaceFile.Snapshot> snapshot()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'public Optional<ChildWindow> popOutTab(Tab tab) {' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'int restore(WorkspaceFile.WindowRecord record)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'void restore(ParentTabs saved, Map<TabId, Tab> tabs)' jasper-app/src/main/java/dev/jasper/app/workspace/ParentTabsView.java  # 1
grep -c 'public static final String APP_OWNER = "jasper";' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
grep -h '^version' plugins/editor/src/main/resources/plugin.toml plugins/remote/src/main/resources/plugin.toml plugins/sample/src/main/resources/plugin.toml  # 0.11.0, 0.15.0, 0.4.0
```

Every command prints the stated result (the `git log` one commit). Plan 6a's `docs/STATUS.md` entry is the newest; this
plan's goes above it. Create the two helper scripts (Global Constraints).

---

### Task 1: Restore state and restorers in the SDK, the fakes and the registry

Spec 14's `ContentSpec`/`SessionSpec` and `Windows.restorer` rows (sessions since the spec amendment `27874bf7d`): a
plugin says how its tab comes back and registers the restorer that rebuilds it, which may decline with a reason (Rulings
5); the application keeps the state on the tab and, while a restorer runs, gives the first tab the plugin opens the saved
id and the window being restored (Rulings 4), and the window being restored takes no other tab meanwhile (Rulings 24).
The contract suite holds both runtimes to it, and the testkit's fake restores as Jasper does: the tab takes the window
being restored, placed and not shown, and a session waits until its tab is shown. `AppContractTest` opens the main
window and then a child for a tab placed in a new window while none is open, as the application does (plan 4b's
hand-off; Rulings 25).

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/RestoreState.java`, `ui/TabRestorer.java`, `ui/RestoreRequest.java`;
  `jasper-app/src/main/java/dev/jasper/app/layout/TabRestore.java`; test `jasper-sdk/src/test/java/dev/jasper/sdk/RestoreStateTest.java`
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentSpec.java`, `terminal/SessionSpec.java`
- Modify (SDK): `ui/Windows.java`, `ui/ContentTab.java`
- Modify (testkit): `FakeContent.java`, `FakeUi.java`, `FakePluginHost.java`, `FakeWorkspace.java`, `FakeTerminals.java`,
  `FakeSessions.java`; `testFixtures/.../contract/ContractHarness.java`, `PluginContractTest.java`;
  `src/test/.../FakeContractTest.java`
- Modify (application): `layout/package-info.java`, `contributions/Tab.java`, `contributions/TabRegistry.java`,
  `terminals/SessionRequest.java`, `plugins/HostedSessions.java`, `plugins/HostedUi.java`, `workspace/WindowContent.java`
- Test: `TabRegistryTest`, `plugins/TerminalFixture.java`, `plugins/AppContractTest.java`, `plugins/HostedUiTest.java`

**Interfaces:**
- Consumes: plan 6a's `TerminalPane.restored()`; plan 4b's `TabRegistry.Windows.emptyWindow`.
- Produces (SDK, `@since 0.21.0`): `record RestoreState(String kind, Map<String, String> values)` with `MAX_ENTRIES`
  (32), `MAX_CHARACTERS` (4096) and `of(kind, key, value)`; `ContentSpec(String key, String title, Optional<RestoreState>
  restore)` with the two-argument constructor and `withRestore(RestoreState)`; `SessionSpec(…, Optional<RestoreState>
  restore)` with the four-argument constructor and `withRestore(RestoreState)`; `void ContentTab.setRestore(Optional<RestoreState>)`;
  `@FunctionalInterface TabRestorer { Optional<String> restore(RestoreRequest request); }` (empty once the tab is open,
  else why not); `record RestoreRequest(WindowHandle window, String title, RestoreState state)`; `Subscription
  Windows.restorer(String kind, TabRestorer restorer)`. Testkit: `FakePluginHost.restoreState(UUID)`,
  `restoreState(ContentTab)`, `restoreTab(String pluginId, UUID windowId, String title, RestoreState state)`,
  `List<String> declinedRestores()`; `ContractHarness.restoreTab(…)`, `restoreState(UUID)`. Application: `record
  layout.TabRestore(String kind, Map<String, String> state)`; `Optional<TabRestore> Tab.restore()`, `void
  Tab.restore(Optional<TabRestore>)`; `TabRegistry.Restoring(UUID window, String title, TabRestore state)`,
  `TabRegistry.Restored(Optional<Tab> tab, String problem)`, `Subscription TabRegistry.restorer(String plugin, String kind,
  Function<Restoring, Optional<String>>)`, `boolean restores(String plugin, String kind)`, `void
  onRestoreChanged(Runnable)`, `Restored restore(TabId id, String plugin, Restoring saved)`, `Optional<TabId> slot(String
  plugin, UUID window)`, `void restored(Tab)`, `boolean restoring(UUID window)`; `SessionRequest(…, Icon icon,
  Optional<TabRestore> restore)` with the six-argument constructor kept. Testkit, package: `FakeWorkspace.Slot`,
  `Optional<Slot> takeSlot(FakePluginContext)`, `Tab placeTab(Window, TabHandle.Kind, String)`, `Pane
  placeTerminal(Window, String, PaneInfo)`, `void FakeSessions.hold(FakePluginContext, Pane, SessionSpec)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-sdk/src/test/java/dev/jasper/sdk/RestoreStateTest.java`:

```java
package dev.jasper.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.jasper.sdk.terminal.SessionSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 13 and 14: how a plugin's tab comes back when Jasper restores its windows. */
class RestoreStateTest {
    @Test void aRestoreStateNamesItsRestorerAndHoldsAFewSmallStrings() {
        var values = new HashMap<String, String>(Map.of("path", "/work/notes.md"));
        var state = new RestoreState("document", values);
        values.put("later", "ignored");
        assertThat(state.values()).as("copied").containsExactly(Map.entry("path", "/work/notes.md"));
        assertThat(RestoreState.of("document", "path", "/a")).isEqualTo(new RestoreState("document", Map.of("path", "/a")));
        assertThat(new RestoreState("dev.jasper-x_1", Map.of()).kind()).isEqualTo("dev.jasper-x_1");
        for (String kind : new String[]{"", "has space", "colon:kind", "x".repeat(65)})
            assertThatIllegalArgumentException().as(kind).isThrownBy(() -> new RestoreState(kind, Map.of()));
        var many = new HashMap<String, String>();
        for (int i = 0; i <= RestoreState.MAX_ENTRIES; i++) many.put("k" + i, "");
        assertThatIllegalArgumentException().isThrownBy(() -> new RestoreState("many", many));
        assertThatIllegalArgumentException().isThrownBy(() -> new RestoreState("long", Map.of("v", "x".repeat(RestoreState.MAX_CHARACTERS))));
        assertThat(new RestoreState("long", Map.of("v", "x".repeat(RestoreState.MAX_CHARACTERS - 1))).values()).hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> new RestoreState("nulls", Collections.singletonMap("key", null)));
    }

    @Test void contentAndSessionsComeBackOnlyWithRestoreState() {
        var state = RestoreState.of("document", "path", "/a");
        assertThat(new dev.jasper.sdk.ui.ContentSpec("k", "Title").restore()).isEmpty();
        assertThat(new dev.jasper.sdk.ui.ContentSpec("k", "Title").withRestore(state)).isEqualTo(new dev.jasper.sdk.ui.ContentSpec("k", "Title", Optional.of(state)));
        assertThatNullPointerException().isThrownBy(() -> new dev.jasper.sdk.ui.ContentSpec("k", "Title", null));
        var session = SessionSpec.of("host", pending -> { });
        assertThat(session.restore()).isEmpty();
        assertThat(session.withRestore(state).restore()).contains(state);
        assertThat(session.withRestore(state).connector()).isSameAs(session.connector());
    }
}
```

Five contract tests run on the fake and on the application: a restorer reopens its content tab in the window being
restored with the saved title, whatever window or placement it names; one that declines, throws or opens nothing
restores no tab, and a kind has one restorer per plugin; a session's tab gives its spec's restore state; another content
tab a restorer opens with the window being restored as owner lands where an owner-less tab would, in the main window
(Rulings 24); and a tab placed in a new window while no window is open opens the main window and then a child window for
it (Rulings 25). The harness's two new methods go on the fake, and a test of the fake alone pins the session half of
Rulings 24, which the application's harness, whose sessions a scripted fixture opens, cannot; the application pins it in
Task 2's `PluginTabRestoreTest.aSessionsTabComesBackThroughItsPluginAndConnectsWhenFirstShown`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
C = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/"
edit(C + "ContractHarness.java", [("""    /** UI thread: moves a tab the test added to another open window, where it is selected; a child window left with no tab closes. */
    void moveTab(java.util.UUID tabId, java.util.UUID windowId);
""", """    /** UI thread: moves a tab the test added to another open window, where it is selected; a child window left with no tab closes. */
    void moveTab(java.util.UUID tabId, java.util.UUID windowId);

    /**
     * UI thread: restores one saved tab through its plugin's restorer, as Jasper does when it restores its windows (workspace
     * tabs spec 13).
     *
     * @return the tab the restorer opened in the window; empty when the plugin has no restorer for the kind, or it threw or
     *     opened nothing
     */
    java.util.Optional<java.util.UUID> restoreTab(String pluginId, java.util.UUID windowId, String title, dev.jasper.sdk.RestoreState state);

    /** UI thread: the restore state a tab gives, a content tab's or a provided session's; empty when it does not come back. */
    java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(java.util.UUID tabId);
""", 1)])
edit(C + "PluginContractTest.java", [("""    /** Workspace tabs spec 14's TabPlacement: the end of the active group, a new group after it, or a new child window. */
    @Test void aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow() {""",
"""    /** Workspace tabs spec 13 and 14: a content tab saved with restore state comes back through its plugin's restorer. */
    @Test void aRestorerOpensItsSavedContentTabAgainInTheWindowBeingRestored() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        var requests = new ArrayList<dev.jasper.sdk.ui.RestoreRequest>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("note", request -> {
                requests.add(request);
                // Whatever window or placement it names, the saved tab comes back in the window being restored.
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:" + request.state().values().get("name"), request.title())
                    .withRestore(request.state()), null, kitView(context), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW);
                return Optional.empty();
            });
        });
        var state = new dev.jasper.sdk.RestoreState("note", java.util.Map.of("name", "todo"));
        h.ui(() -> {
            var window = alpha.get().terminals().window(main).orElseThrow();
            var first = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:first", "First").withRestore(state), window,
                kitView(alpha.get()));
            UUID firstTab = window.activeTab().orElseThrow().id();
            assertThat(h.restoreState(firstTab)).contains(state);
            first.setRestore(Optional.empty());
            assertThat(h.restoreState(firstTab)).as("a tab that gives no restore state is not saved").isEmpty();
            Optional<UUID> back = h.restoreTab("test.alpha", main, "Todo", state);
            assertThat(back).isPresent();
            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(main);
                assertThat(request.title()).isEqualTo("Todo");
                assertThat(request.state()).isEqualTo(state);
            });
            var reopened = alpha.get().terminals().tab(back.get()).orElseThrow();
            assertThat(reopened.kind()).isEqualTo(TabHandle.Kind.CONTENT);
            assertThat(reopened.window().id()).as("the window being restored, not a new one").isEqualTo(main);
            assertThat(alpha.get().terminals().windows()).hasSize(1);
            assertThat(reopened.title()).isEqualTo("Todo");
            assertThat(h.restoreState(back.get())).contains(state);
        });
    }

    /**
     * Workspace tabs spec 13: a restorer that declines, throws or opens nothing restores no tab; a plugin has one restorer
     * per kind.
     */
    @Test void aRestorerThatFailsRestoresNothingAndAKindHasOneRestorer() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var windows = alpha.get().windows();
            windows.restorer("broken", request -> { throw new IllegalStateException("the file is gone"); });
            windows.restorer("declined", request -> Optional.of("its file is gone"));
            Subscription empty = windows.restorer("empty", request -> Optional.empty());
            assertThatIllegalArgumentException().as("one restorer per kind").isThrownBy(() -> windows.restorer("empty", request -> Optional.empty()));
            assertThatIllegalArgumentException().as("not a kind").isThrownBy(() -> windows.restorer("no:colons", request -> Optional.empty()));
            assertThat(h.restoreTab("test.alpha", main, "Gone", new dev.jasper.sdk.RestoreState("broken", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Declined", new dev.jasper.sdk.RestoreState("declined", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Nothing", new dev.jasper.sdk.RestoreState("empty", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Unknown", new dev.jasper.sdk.RestoreState("unknown", java.util.Map.of()))).isEmpty();
            empty.close();
            windows.restorer("empty", request -> {
                windows.openTab(new dev.jasper.sdk.ui.ContentSpec("again", request.title()), request.window(), kitView(alpha.get()));
                return Optional.empty();
            });
            assertThat(h.restoreTab("test.alpha", main, "Again", new dev.jasper.sdk.RestoreState("empty", java.util.Map.of())))
                .as("a closed registration frees its kind").isPresent();
            assertThat(alpha.get().terminals().window(main).orElseThrow().tabs()).hasSize(1);
        });
    }

    /** Workspace tabs spec 13 and 14: a provided session's tab gives the restore state its spec carries. */
    @Test void aSessionsTabGivesTheRestoreStateOfItsSpec() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        var state = new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of("host", "build-host"));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle saved = terminals.openTab(terminals.window(main).orElseThrow(),
                OpenRequest.session(dev.jasper.sdk.terminal.SessionSpec.of("build-host", pending -> { }).withRestore(state))).orElseThrow();
            PaneHandle plain = terminals.openTab(terminals.window(main).orElseThrow(),
                OpenRequest.session(dev.jasper.sdk.terminal.SessionSpec.of("other", pending -> { }))).orElseThrow();
            assertThat(h.restoreState(saved.tab().id())).contains(state);
            assertThat(h.restoreState(plain.tab().id())).isEmpty();
        });
    }

    /**
     * Workspace plan 6b Rulings 24: while a restorer runs, the window being restored takes only the saved tab; another
     * content tab the restorer opens there lands where an owner-less tab would, in the main window.
     */
    @Test void aRestorersOtherContentTabLandsWhereAnOwnerlessTabWouldNotInTheWindowBeingRestored() {
        UUID main = h.addTerminalWindow(), child = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        var other = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("note", request -> {
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:saved", request.title()), request.window(), kitView(context));
                other.set(context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:other", "Other"), request.window(), kitView(context)));
                return Optional.empty();
            });
        });
        h.ui(() -> {
            Optional<UUID> back = h.restoreTab("test.alpha", child, "Saved", new dev.jasper.sdk.RestoreState("note", java.util.Map.of()));
            assertThat(back).isPresent();
            assertThat(alpha.get().terminals().tab(back.get()).orElseThrow().window().id()).as("the saved tab").isEqualTo(child);
            assertThat(other.get().window().id()).as("where an owner-less tab goes").isEqualTo(main);
        });
    }

    /**
     * Workspace tabs spec 6.4 and 14: a content tab placed in a new window while no window is open opens the main window
     * first, then a child window of its own for the tab.
     */
    @Test void aTabPlacedInANewWindowWhileNoWindowIsOpenOpensTheMainWindowAndAChildForIt() {
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var tab = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("away", "Away"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW);
            var windows = alpha.get().terminals().windows();
            assertThat(windows).as("the main window, then the tab's own").hasSize(2);
            assertThat(windows).filteredOn(window -> window.isMain()).hasSize(1);
            assertThat(tab.window().isMain()).as("a child window of its own").isFalse();
        });
    }

    /** Workspace tabs spec 14's TabPlacement: the end of the active group, a new group after it, or a new child window. */
    @Test void aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow() {""", 1)])
F = "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java"
edit(F, [("""            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }
""", """            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }
            @Override public java.util.Optional<java.util.UUID> restoreTab(String pluginId, java.util.UUID windowId, String title,
                    dev.jasper.sdk.RestoreState state) { return host.restoreTab(pluginId, windowId, title, state); }
            @Override public java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(java.util.UUID tabId) { return host.restoreState(tabId); }
""", 1), ("""    /** The contract's line for a fake post. */
    static String contractLine(FakeNotifications.Post post) {""", """    /**
     * Workspace plan 6b Rulings 24, the fake alone: while a restorer runs, the window being restored opens no session but
     * the saved one. The application pins it in {@code PluginTabRestoreTest}.
     */
    @org.junit.jupiter.api.Test void whileARestorerRunsTheWindowBeingRestoredOpensNoOtherSession() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            java.util.UUID window = host.addTerminalWindow();
            var other = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>>();
            host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(),
                context -> context.windows().restorer("ssh", request -> {
                    var terminals = context.terminals();
                    terminals.openTab(request.window(), dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("saved", pending -> { }).withRestore(request.state())));
                    other.set(terminals.openTab(request.window(), dev.jasper.sdk.terminal.OpenRequest.session(
                        dev.jasper.sdk.terminal.SessionSpec.of("other", pending -> { }))));
                    return java.util.Optional.empty();
                }));
            org.assertj.core.api.Assertions.assertThat(host.restoreTab("test.alpha", window, "saved",
                new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of()))).isPresent();
            org.assertj.core.api.Assertions.assertThat(other.get()).as("the window being restored takes only the saved tab").isEmpty();
        } });
    }

    /** The contract's line for a fake post. */
    static String contractLine(FakeNotifications.Post post) {""", 1)])
PY
```

The registry's own test, and the application's harness methods (the scripted workspace shows a rebuilt tab where the
window being restored would):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "contributions/TabRegistryTest.java", [("    @Test void aHostedTabHasAnIdItsKindAndItsContent() {",
"""    /** Workspace tabs spec 13: a plugin's restorer rebuilds a saved tab, which takes the saved id and window, not yet shown. */
    @Test void aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot() {
        hosts();
        TabId saved = TabId.random();
        var state = new dev.jasper.app.layout.TabRestore("note", Map.of("name", "todo"));
        var registration = registry.restorer("plugin.notes", "note", restoring -> {
            registry.open("plugin.notes", "note:todo", restoring.title(), null, new JPanel());
            return Optional.empty();
        });
        assertThat(registry.restores("plugin.notes", "note")).isTrue();
        TabRegistry.Restored restored = registry.restore(saved, "plugin.notes", new TabRegistry.Restoring(detached, "Todo", state));
        Tab tab = restored.tab().orElseThrow();
        assertThat(tab.id()).isEqualTo(saved);
        assertThat(tab.window()).as("the window being restored, whatever the plugin named").isEqualTo(detached);
        assertThat(tab.title()).isEqualTo("Todo");
        assertThat(mounted).as("that window places it").isEmpty();
        assertThat(registry.tab(saved)).contains(tab);
        assertThat(registry.slot("plugin.notes", detached)).as("taken").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.restorer("plugin.notes", "note", restoring -> Optional.empty()));
        var other = new TabRegistry.Restoring(main, "Other", new dev.jasper.app.layout.TabRestore("other", Map.of()));
        assertThat(registry.restore(TabId.random(), "plugin.notes", other).problem()).isEqualTo("it does not restore other tabs");
        registry.restorer("plugin.notes", "broken", restoring -> Optional.of("the file is gone"));
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("broken", Map.of()))).problem()).isEqualTo("the file is gone");
        registry.restorer("plugin.notes", "again", restoring -> {
            assertThat(registry.restore(TabId.random(), "plugin.notes", restoring).problem()).as("one restore at a time")
                .isEqualTo("another tab is being restored");
            return Optional.of("declined");
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("again", Map.of()))).problem()).isEqualTo("declined");
        assertThat(registry.restore(saved, "plugin.notes", new TabRegistry.Restoring(main, "x", state)).problem())
            .as("an id already open is never taken twice").isEqualTo("a tab with its id is already open");
        registry.restorer("plugin.notes", "nothing", restoring -> {
            assertThat(registry.slot("plugin.notes", main)).as("a session opened in that window would take it").isPresent();
            assertThat(registry.slot("plugin.other", main)).isEmpty();
            return Optional.empty();
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(main, "x", new dev.jasper.app.layout.TabRestore("nothing", Map.of()))).problem()).isEqualTo("it opened no tab");
        // Workspace plan 6b Rulings 24: while it runs, the window being restored takes only the saved tab.
        Tab[] extra = new Tab[1];
        registry.restorer("plugin.notes", "pair", restoring -> {
            assertThat(registry.restoring(detached)).isTrue();
            registry.open("plugin.notes", "pair:one", "One", restoring.window(), new JPanel());
            extra[0] = registry.open("plugin.notes", "pair:two", "Two", restoring.window(), new JPanel());
            return Optional.empty();
        });
        Tab pair = registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(detached, "Pair", new dev.jasper.app.layout.TabRestore("pair", Map.of()))).tab().orElseThrow();
        assertThat(pair.window()).isEqualTo(detached);
        assertThat(extra[0].window()).as("where an owner-less tab goes: the main window").isEqualTo(main);
        assertThat(mounted).containsExactly(extra[0]);
        assertThat(registry.restoring(detached)).as("only while the restorer runs").isFalse();
        // So does a tab of the same key that waits for its window: it keeps waiting, out of the window being restored.
        registry.windows(new TabRegistry.Windows(() -> null, main::equals, ignored -> { }));
        Tab waiting = registry.open("plugin.notes", "wait", "Wait", null, new JPanel());
        registry.restorer("plugin.notes", "wait", restoring -> {
            registry.open("plugin.notes", "wait", "Wait", restoring.window(), new JPanel());
            return Optional.empty();
        });
        assertThat(registry.restore(TabId.random(), "plugin.notes",
            new TabRegistry.Restoring(detached, "Wait", new dev.jasper.app.layout.TabRestore("wait", Map.of()))).problem())
            .as("its open key is selected as always: no tab opened").isEqualTo("it opened no tab");
        assertThat(waiting.window()).as("still waiting, not in the window being restored").isNull();
        assertThat(mounted).doesNotContain(waiting);
        registry.windows(windows(main, detached));
        registration.close();
        assertThat(registry.restores("plugin.notes", "note")).isFalse();
        int[] changed = {0};
        registry.onRestoreChanged(() -> changed[0]++);
        tab.restore(Optional.of(state));
        tab.restore(Optional.of(state));
        assertThat(changed[0]).as("told once per change").isEqualTo(1);
        assertThat(tab.restore()).contains(state);
    }

    @Test void aHostedTabHasAnIdItsKindAndItsContent() {""", 1)])
edit(T + "plugins/TerminalFixture.java", [(
    """    /** The pane waits, as a restored terminal tab not yet shown does (workspace tabs spec 13). */""",
    """    /** How a provided session's tab comes back, as its request carries it (workspace tabs spec 13). */
    Optional<dev.jasper.app.layout.TabRestore> sessionRestore(UUID tabId) {
        return Optional.ofNullable(tabs.get(tabId)).map(tab -> tab.pane).map(pane -> pane.request).flatMap(SessionRequest::restore);
    }

    /** The pane waits, as a restored terminal tab not yet shown does (workspace tabs spec 13). */""", 1)])
edit(T + "plugins/AppContractTest.java", [(
    """            @Override public void moveTab(UUID tabId, UUID windowId) { onEdt(() -> terminalFixture.moveTab(tabId, windowId)); }
""", """            @Override public void moveTab(UUID tabId, UUID windowId) { onEdt(() -> terminalFixture.moveTab(tabId, windowId)); }
            @Override public Optional<UUID> restoreTab(String pluginId, UUID windowId, String title, dev.jasper.sdk.RestoreState state) {
                return onEdtValue(() -> {
                    var restored = contributions.tabs().restore(dev.jasper.app.layout.TabId.random(), pluginId,
                        new dev.jasper.app.contributions.TabRegistry.Restoring(windowId, title,
                            new dev.jasper.app.layout.TabRestore(state.kind(), state.values())));
                    // The window being restored places the tab; here the scripted workspace shows it in that window.
                    restored.tab().ifPresent(tab -> terminalFixture.addContentTab(windowId, tab.id().value(), tab::title));
                    return restored.tab().map(tab -> tab.id().value());
                });
            }
            @Override public Optional<dev.jasper.sdk.RestoreState> restoreState(UUID tabId) {
                return onEdtValue(() -> contributions.tabs().tab(new dev.jasper.app.layout.TabId(tabId))
                    .flatMap(dev.jasper.app.contributions.Tab::restore).or(() -> terminalFixture.sessionRestore(tabId))
                    .map(saved -> new dev.jasper.sdk.RestoreState(saved.kind(), saved.state())));
            }
""", 1)])
PY
```

A saved state the SDK refuses never reaches the plugin's restorer, and is no incident (Rulings 30):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java", [(
    """    @Test void dialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {""",
    """    /** Workspace plan 6b Rulings 30: a saved state the SDK refuses restores nothing, runs no restorer and raises no incident. */
    @Test void aSavedStateTheSdkRefusesRestoresNothingAndRaisesNoIncident() {
        int[] restored = {0};
        ui.windows().restorer("note", request -> { restored[0]++; return java.util.Optional.empty(); });
        var outcome = model.tabs().restore(dev.jasper.app.layout.TabId.random(), "dev.x.tool",
            new dev.jasper.app.contributions.TabRegistry.Restoring(java.util.UUID.randomUUID(), "Too long",
                new dev.jasper.app.layout.TabRestore("note", java.util.Map.of("text", "x".repeat(dev.jasper.sdk.RestoreState.MAX_CHARACTERS)))));
        assertThat(outcome.tab()).isEmpty();
        assertThat(outcome.problem()).isEqualTo("its saved state is not valid");
        assertThat(restored[0]).as("the plugin never sees it").isZero();
        assertThat(containment.failures("dev.x.tool")).as("no incident").isZero();
    }

    @Test void dialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:compileTestJava --continue`
Expected: FAIL: `cannot find symbol` for `RestoreState`, `withRestore`, `setRestore`, `restorer`, `RestoreRequest`,
`restoreTab`, `restoreState`, `TabRestore`, `TabRegistry.Restoring`, `restoring` and `sessionRestore`.

- [ ] **Step 3: The SDK**

The restore state is in the SDK's root package: `SessionSpec`, in `terminal`, needs it, and `terminal` may not reach
`ui`, which already reaches `terminal`.

Create `jasper-sdk/src/main/java/dev/jasper/sdk/RestoreState.java`:

```java
package dev.jasper.sdk;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How a plugin's tab comes back when Jasper restores its windows (workspace tabs spec 13 and 14): the kind of restorer the
 * plugin registered with {@code Windows.restorer} and a small map of strings the restorer rebuilds the tab from, such as a
 * file's path. Jasper writes it into {@code workspace.toml} with the tab, so it holds no secret. A content tab gives it in
 * its {@code ContentSpec} or later with {@code ContentTab.setRestore}; a provided session gives it in its
 * {@code SessionSpec}.
 *
 * @param kind the restorer kind: 1 to 64 letters, digits, dots, dashes and underscores
 * @param values what the restorer needs: at most {@link #MAX_ENTRIES} entries whose keys and values together hold at
 *               most {@link #MAX_CHARACTERS} characters; copied
 * @since 0.21.0
 */
public record RestoreState(String kind, Map<String, String> values) {
    /** The most entries a restore state holds. */
    public static final int MAX_ENTRIES = 32;
    /** The most characters a restore state's keys and values hold together. */
    public static final int MAX_CHARACTERS = 4096;
    private static final Pattern KIND = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Checks the kind and the size, and copies the values; a null key or value is refused. */
    public RestoreState {
        Objects.requireNonNull(kind, "kind");
        if (!KIND.matcher(kind).matches())
            throw new IllegalArgumentException("A restorer kind is 1 to 64 letters, digits, dots, dashes and underscores: " + kind);
        values = Map.copyOf(values);
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("A restore state holds at most " + MAX_ENTRIES + " entries");
        long characters = values.entrySet().stream().mapToLong(entry -> entry.getKey().length() + entry.getValue().length()).sum();
        if (characters > MAX_CHARACTERS)
            throw new IllegalArgumentException("A restore state holds at most " + MAX_CHARACTERS + " characters, not " + characters);
    }

    /**
     * A restore state of {@code kind} with one value.
     *
     * @param kind the restorer kind
     * @param key the value's key
     * @param value the value
     * @return the state
     */
    public static RestoreState of(String kind, String key, String value) { return new RestoreState(kind, Map.of(key, value)); }
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentSpec.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.RestoreState;
import java.util.Optional;

/** Stable document identity, scoped to the calling plugin, initial literal title, and how the tab comes back.
 * @param key stable nonblank identity; reopening selects the existing content
 * @param title initial literal tab title
 * @param restore how the tab comes back when Jasper restores its windows (since 0.21.0); empty, and the tab is not saved
 * @since 0.8.5
 */
public record ContentSpec(String key, String title, Optional<RestoreState> restore) {
    /** Validates required values. */
    public ContentSpec {
        if(key==null||key.isBlank())throw new IllegalArgumentException("Content needs a stable key");
        java.util.Objects.requireNonNull(title,"title");
        java.util.Objects.requireNonNull(restore,"restore");
    }

    /**
     * Content that does not come back when Jasper restores its windows.
     *
     * @param key stable nonblank identity
     * @param title initial literal tab title
     */
    public ContentSpec(String key, String title) { this(key, title, Optional.empty()); }

    /**
     * This content, coming back as {@code state} says when Jasper restores its windows.
     *
     * @param state the restorer kind and what it rebuilds the tab from
     * @return the spec
     * @since 0.21.0
     */
    public ContentSpec withRestore(RestoreState state) { return new ContentSpec(key, title, Optional.of(state)); }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabRestorer.java`:

```java
package dev.jasper.sdk.ui;

/**
 * Rebuilds a plugin's saved tabs when Jasper restores its windows (workspace tabs spec 13 and 14): at start, and whenever
 * the main window opens again after it closed. Registered with {@link Windows#restorer} for one restorer kind; called on
 * the UI thread once for each saved tab of that kind, while its window is being restored and before it shows.
 *
 * @since 0.21.0
 */
@FunctionalInterface
public interface TabRestorer {
    /**
     * Opens one saved tab again, before returning, the way the plugin opened it: a content tab with
     * {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView)}, or a provided session with
     * {@code Terminals.openTab}, with the same restore state. The first content tab the plugin opens meanwhile is the saved
     * tab, whatever window or placement it names, and so is the first session it opens in {@code request.window()}; Jasper
     * puts it where it was saved, with the selection it had, and a session's tab connects when it is first shown. Open only
     * that tab: while the restorer runs, the window being restored takes no other, so another content tab opens where an
     * owner-less one would, and another session does not open. When the tab
     * cannot come back now for a reason the plugin expects (its file or host is gone), return why: a placeholder in its
     * place says so, with Retry, and nothing is reported as the plugin's failure. Opening nothing without a reason, or
     * throwing, also leaves a placeholder; a throw is the plugin's failure.
     *
     * @param request the window, the saved title and the restore state
     * @return empty once the tab is open again; else why it cannot come back, which its placeholder shows after
     *     "could not restore this tab:"
     */
    java.util.Optional<String> restore(RestoreRequest request);
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/RestoreRequest.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;

/**
 * One saved tab a {@link TabRestorer} opens again (workspace tabs spec 13).
 *
 * @param window the Jasper window being restored: the owner of a content tab, or the window of a session's tab
 * @param title the tab's title when it was saved
 * @param state the restore state the tab had when it was saved
 * @since 0.21.0
 */
public record RestoreRequest(WindowHandle window, String title, RestoreState state) {
    /** Requires every value. */
    public RestoreRequest {
        Objects.requireNonNull(window, "window"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(state, "state");
    }
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/SessionSpec.java`:

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.RestoreState;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A session your plugin provides. The pane appears first, waiting; {@code connector} is then called on the
 * event thread, once for the first connect and once for every Reconnect, each time with a fresh
 * {@link PendingSession}. It must not block: start the connection on {@code context.background()}.
 *
 * @param title the pane's title until the remote program sets one
 * @param icon the tab's icon (since 0.7.6), drawn in a 16 &times; 16 logical-pixel slot; empty shows
 *     Jasper's terminal icon
 * @param onExit what happens to the pane when the session ends
 * @param connector starts one connection attempt
 * @param restore how the session's tab comes back when Jasper restores its windows (since 0.21.0); empty, and the tab is
 *     not saved
 */
public record SessionSpec(String title, Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector,
                          Optional<RestoreState> restore) {
    /** Rejects nulls and a blank title. */
    public SessionSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onExit, "onExit");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(restore, "restore");
        if (title.isBlank()) throw new IllegalArgumentException("A session needs a title");
    }

    /**
     * A session whose tab does not come back when Jasper restores its windows.
     *
     * @param title the pane's title until the remote program sets one
     * @param icon the tab's icon; empty shows Jasper's terminal icon
     * @param onExit what happens to the pane when the session ends
     * @param connector starts one connection attempt
     */
    public SessionSpec(String title, Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector) {
        this(title, icon, onExit, connector, Optional.empty());
    }

    /**
     * A session whose pane stays open when it ends.
     *
     * @param title the pane's title until the remote program sets one
     * @param connector starts one connection attempt
     * @return the spec
     */
    public static SessionSpec of(String title, Consumer<PendingSession> connector) {
        return new SessionSpec(title, Optional.empty(), ExitPolicy.KEEP_OPEN, connector);
    }

    /**
     * This session, whose tab comes back as {@code state} says when Jasper restores its windows: the plugin's restorer
     * for that kind opens it again, waiting until its tab is first shown.
     *
     * @param state the restorer kind and what it rebuilds the session from
     * @return the spec
     * @since 0.21.0
     */
    public SessionSpec withRestore(RestoreState state) { return new SessionSpec(title, icon, onExit, connector, Optional.of(state)); }
}
```

`Windows.restorer` and `ContentTab.setRestore`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
U = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/"
edit(U + "Windows.java", [("""    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);
""", """    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);

    /**
     * Registers how this plugin rebuilds its tabs saved with restore state of {@code kind} (workspace tabs spec 13 and 14).
     * Register it in {@code start()}: Jasper restores its windows once every plugin has started, and again whenever the
     * main window opens after it closed. A tab is saved only while its plugin has a restorer for its kind; a saved tab
     * whose plugin is missing or disabled, or has no restorer for its kind any more, comes back as a placeholder that
     * says why.
     *
     * @param kind the restorer kind, as {@link dev.jasper.sdk.RestoreState} allows it
     * @param restorer opens one saved tab again
     * @return the registration; closing it ends this restorer
     * @throws IllegalArgumentException when the kind is not valid, or this plugin already has a restorer for it
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @since 0.21.0
     */
    Subscription restorer(String kind, TabRestorer restorer);
""", 1)])
edit(U + "ContentTab.java", [("""    /** @param focus UI-thread callback focusing the editable content */
    void setFocusTarget(Runnable focus);
""", """    /** @param focus UI-thread callback focusing the editable content */
    void setFocusTarget(Runnable focus);
    /**
     * How the tab comes back when Jasper restores its windows, in place of what its {@link ContentSpec#restore()} said:
     * a document saved under a new name names its new file. Empty: the tab is not saved.
     *
     * @param state the restore state, or empty
     * @since 0.21.0
     */
    void setRestore(java.util.Optional<dev.jasper.sdk.RestoreState> state);
""", 1)])
PY
```

- [ ] **Step 4: The fakes**

A fake content tab keeps its restore state, `FakeUi` keeps the plugin's restorers by kind, and `FakePluginHost` reads a
tab's state and runs a restorer for a test as Jasper does (Rulings 15): the first tab the plugin opens meanwhile takes
the window being restored and is placed at the end of its active group, neither selected nor shown
(`FakeWorkspace.placeTab`), once the fake has checked the owner, as the application's `HostedUi` does, and before it
resolves one, so it never opens a window of its own; a session's waits,
`NOT_STARTED`, until its tab is shown; a reason the restorer gives is kept in `declinedRestores()`, never in
`failures()`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
T = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(T + "FakeContent.java", [
    ("    List<ContentStatus> status=List.of();\n",
     "    List<ContentStatus> status=List.of();\n"
     "    /** How the tab comes back when Jasper restores its windows; empty while it does not (workspace tabs spec 13). */\n"
     "    Optional<dev.jasper.sdk.RestoreState> restore;\n", 1),
    ("        presentation=new ContentPresentation(spec.title(),\"\",null,false,\"\",\"\");\n",
     "        presentation=new ContentPresentation(spec.title(),\"\",null,false,\"\",\"\");\n"
     "        restore=spec.restore();\n", 1),
    ("    public void setFocusTarget(Runnable value){if(!closed)focus=value;}\n",
     "    public void setFocusTarget(Runnable value){if(!closed)focus=value;}\n"
     "    public void setRestore(Optional<dev.jasper.sdk.RestoreState> value){Objects.requireNonNull(value,\"value\");if(!closed)restore=value;}\n", 1)])
edit(T + "FakeUi.java", [
    ("    final List<java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>>> quitGuards = new ArrayList<>();\n",
     "    final List<java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>>> quitGuards = new ArrayList<>();\n"
     "    /** This plugin's restorers by kind (workspace tabs spec 13). */\n"
     "    final java.util.Map<String, dev.jasper.sdk.ui.TabRestorer> restorers = new java.util.LinkedHashMap<>();\n", 1),
    ("""                quitGuards.add(guard);
                return () -> quitGuards.remove(guard);
            }
""", """                quitGuards.add(guard);
                return () -> quitGuards.remove(guard);
            }
            @Override public Subscription restorer(String kind, dev.jasper.sdk.ui.TabRestorer restorer) {
                context.requireOpen();
                java.util.Objects.requireNonNull(restorer, "restorer");
                // The kind's rules are the restore state's.
                new dev.jasper.sdk.RestoreState(kind, java.util.Map.of());
                if (restorers.putIfAbsent(kind, restorer) != null) throw new IllegalArgumentException("This plugin already restores " + kind);
                return () -> restorers.remove(kind, restorer);
            }
""", 1),
    ("""                    throw new IllegalArgumentException("Content requires a workspace owner");
                if (owner == null || !((WindowHandle)owner).isOpen()) {
""", """                    throw new IllegalArgumentException("Content requires a workspace owner");
                // A saved tab being restored comes back in the window being restored, whatever window or placement it named,
                // placed and not shown: Jasper shows it where it was saved (workspace tabs spec 13).
                var slot = host.workspace.takeSlot(context);
                if (slot.isPresent()) {
                    var window = slot.get().window;
                    var restored = new FakeContent(FakeUi.this, spec, context.terminals.windowHandle(window.id), component,
                        host.workspace.placeTab(window, dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT, spec.title()));
                    contents.add(restored);
                    slot.get().taken = restored.tab;
                    return restored;
                }
                // Meanwhile the window being restored takes no other tab: it goes where an owner-less one goes (workspace
                // plan 6b Rulings 24).
                if (owner != null && host.workspace.slot != null && ((WindowHandle) owner).id().equals(host.workspace.slot.window.id)) owner = null;
                if (owner == null || !((WindowHandle)owner).isOpen()) {
""", 1)])
edit(T + "FakeWorkspace.java", [
    ("""    final FakeSessions sessions;
""", """    final FakeSessions sessions;
    /** The saved tab a plugin's restorer is rebuilding, while it runs (workspace tabs spec 13). */
    Slot slot;

    /** A saved tab being rebuilt: the first tab its plugin opens takes it, in the window being restored. */
    static final class Slot {
        final Window window; final FakePluginContext owner; Tab taken;
        Slot(Window window, FakePluginContext owner) { this.window = window; this.owner = owner; }
    }

    /** @return the slot, while {@code context}'s plugin has not opened its tab yet; the caller sets what took it */
    Optional<Slot> takeSlot(FakePluginContext context) {
        return slot != null && slot.taken == null && slot.owner == context ? Optional.of(slot) : Optional.empty();
    }
""", 1), ("""    private Tab announceTab(Window window, TabHandle.Kind kind, String title, Group beside) {
""", """    /** A tab of {@code kind} at the end of {@code window}'s active group, announced and not selected: a restored tab. */
    Tab placeTab(Window window, TabHandle.Kind kind, String title) { return announceTab(window, kind, title, null); }

    /** A terminal tab with its one pane, placed as {@link #placeTab} places a tab: a restored session's. */
    Pane placeTerminal(Window window, String title, PaneInfo info) {
        return addPane(announceTab(window, TabHandle.Kind.TERMINAL, title, null), info);
    }

    private Tab announceTab(Window window, TabHandle.Kind kind, String title, Group beside) {
""", 1)])
edit(T + "FakeTerminals.java", [(
    """        return workspace.window(window.id()).map(target -> switch (request) {""",
    """        return workspace.window(window.id())
            // While a restorer runs, the window being restored takes only the saved tab (workspace plan 6b Rulings 24).
            .filter(target -> workspace.slot == null || workspace.slot.window != target
                || request instanceof OpenRequest.Session && workspace.slot.taken == null && workspace.slot.owner == context)
            .map(target -> switch (request) {""", 1), (
    """            case OpenRequest.Session session -> {
                workspace.openRequests.add("session-tab|" + target.id + "|" + session.spec().title());
                FakeWorkspace.Pane pane = workspace.openTerminal(target, session.spec().title(), sessionInfo(session));
                workspace.sessions.connect(context, pane, session.spec());
                yield new Pane(pane.id, pane.tab.id);
            }""",
    """            case OpenRequest.Session session -> {
                // A session a restorer opens in the window being restored is the saved tab: it comes back there, placed and
                // not shown, and waits, not started, until its tab is shown (workspace tabs spec 13).
                var slot = workspace.takeSlot(context).filter(found -> found.window == target);
                FakeWorkspace.Window into = target;
                workspace.openRequests.add("session-tab|" + into.id + "|" + session.spec().title());
                FakeWorkspace.Pane pane;
                if (slot.isPresent()) {
                    pane = workspace.placeTerminal(into, session.spec().title(), sessionInfo(session));
                    slot.get().taken = pane.tab;
                    workspace.sessions.hold(context, pane, session.spec());
                } else {
                    pane = workspace.openTerminal(target, session.spec().title(), sessionInfo(session));
                    workspace.sessions.connect(context, pane, session.spec());
                }
                yield new Pane(pane.id, pane.tab.id);
            }""", 1)])
edit(T + "FakeSessions.java", [(
    """    String state(FakeWorkspace.Pane paneOrNull) {""",
    """    /** A restored session waits, not started, until its tab is shown, when the workspace connects it. */
    void hold(FakePluginContext owner, FakeWorkspace.Pane pane, SessionSpec spec) {
        pane.session = new Session(owner, spec);
        pane.session.state = "NOT_STARTED";
        pane.held = true;
        pane.info = FakeTerminals.withState(pane.info, SessionState.NOT_STARTED, OptionalInt.empty());
    }

    String state(FakeWorkspace.Pane paneOrNull) {""", 1)])
edit(T + "FakePluginHost.java", [("""    /** A {@code Terminals.environment} contributor and the plugin that registered it. */
""", """    /**
     * The restore state a tab gives (workspace tabs spec 13): a plugin's content tab's, or the one a plugin's provided
     * session carries. Call it on the UI thread.
     *
     * @param tabId a tab of the scripted workspace
     * @return its restore state; empty when the tab does not come back
     * @since 0.21.0
     */
    public java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(UUID tabId) {
        for (FakePluginContext context : contexts.values())
            for (FakeContent content : context.ui.contents) if (content.tab.id.equals(tabId)) return content.restore;
        return workspace.tab(tabId).map(tab -> tab.pane).map(pane -> pane.session).flatMap(session -> session.spec.restore());
    }

    /**
     * The restore state a plugin's content tab gives (workspace tabs spec 13). Call it on the UI thread.
     *
     * @param tab a content tab from this host
     * @return its restore state; empty when the tab does not come back
     * @since 0.21.0
     */
    public java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(dev.jasper.sdk.ui.ContentTab tab) { return ((FakeContent) tab).restore; }

    /**
     * Restores one saved tab as Jasper does when it restores its windows (workspace tabs spec 13): the plugin's restorer for
     * {@code state}'s kind runs, and the first tab it opens, a content tab or a provided session's, is the saved tab, in
     * window {@code windowId} whatever window or placement it named. A content tab is placed there, not shown: it is
     * neither selected nor active until the test shows it. A session's tab waits as Jasper's does, {@code NOT_STARTED} with
     * no connection attempt, until its tab is shown ({@link #focusTerminalPane} or its {@code TabHandle.select()}). Jasper
     * places the tab where it was saved; this fake puts it at the end of the window's active group. Call it on the UI
     * thread.
     *
     * @param pluginId the plugin that saved the tab
     * @param windowId the window being restored
     * @param title the tab's saved title
     * @param state its saved restore state
     * @return the tab the restorer opened, content or terminal, while it is open in that window; empty when the plugin is
     *     not running or has no restorer for the kind, or its restorer declined (recorded in {@link #declinedRestores()}),
     *     threw before it opened a tab (recorded in {@link #failures()}) or opened nothing, where Jasper shows a placeholder
     * @since 0.21.0
     */
    public java.util.Optional<UUID> restoreTab(String pluginId, UUID windowId, String title, dev.jasper.sdk.RestoreState state) {
        FakeWorkspace.Window window = workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No window " + windowId));
        FakePluginContext context = contexts.get(pluginId);
        if (context == null || context.state != FakePluginContext.State.ACTIVE) return java.util.Optional.empty();
        dev.jasper.sdk.ui.TabRestorer restorer = context.ui.restorers.get(state.kind());
        if (restorer == null || workspace.slot != null) return java.util.Optional.empty();
        var slot = new FakeWorkspace.Slot(window, context);
        workspace.slot = slot;
        java.util.Optional<String> declined;
        try {
            declined = restorer.restore(new dev.jasper.sdk.ui.RestoreRequest(context.terminals.windowHandle(windowId), title, state));
        } catch (RuntimeException | LinkageError failure) {
            failures.add(pluginId + " restorer " + state.kind() + ": " + failure);
            return java.util.Optional.ofNullable(slot.taken).filter(tab -> tab.open && tab.window == window).map(tab -> tab.id);
        } finally {
            workspace.slot = null;
        }
        if (declined != null && declined.isPresent()) declines.add(pluginId + " " + state.kind() + ": " + declined.get());
        // As Jasper's: only a tab still open in the window being restored is the saved tab.
        return java.util.Optional.ofNullable(slot.taken).filter(tab -> tab.open && tab.window == window).map(tab -> tab.id);
    }

    /**
     * Why restorers declined to restore a tab, in order: an expected outcome a placeholder shows, which is no failure.
     *
     * @return lines of the form {@code <plugin id> <kind>: <reason>}
     * @since 0.21.0
     */
    public List<String> declinedRestores() { return List.copyOf(declines); }

    /** A {@code Terminals.environment} contributor and the plugin that registered it. */
""", 1), ("""    private final List<String> failures = new CopyOnWriteArrayList<>();
""", """    private final List<String> failures = new CopyOnWriteArrayList<>();
    /** Why restorers declined, in order (workspace tabs spec 13). */
    private final List<String> declines = new CopyOnWriteArrayList<>();
""", 1)])
PY
```

- [ ] **Step 5: The application**

Create `jasper-app/src/main/java/dev/jasper/app/layout/TabRestore.java`:

```java
package dev.jasper.app.layout;

import java.util.Map;
import java.util.Objects;

/**
 * How a plugin's tab comes back when the windows are restored (workspace tabs spec 13): the restorer kind its plugin
 * registered and the restore state it gave, the application's twin of the SDK's {@code RestoreState}.
 *
 * @param kind the restorer kind; not empty
 * @param state what the restorer rebuilds the tab from; copied
 */
public record TabRestore(String kind, Map<String, String> state) {
    /** Requires a kind and copies the state. */
    public TabRestore {
        Objects.requireNonNull(kind, "kind");
        if (kind.isEmpty()) throw new IllegalArgumentException("A restore names its restorer kind");
        state = Map.copyOf(state);
    }
}
```

A tab carries its restore state and tells the registry when it changes; the registry keeps each plugin's restorers and the
slot of the saved tab being rebuilt, which `open` (once an open tab of the same key has been selected as always) and a
session's terminal tab take while its id is free, before any placement or wait for a main window applies; while the
slot is set, the window being restored takes no other tab: `open` sends one where an owner-less tab goes, and
`openTerminal` refuses one (Rulings 24); a restore asked for while one runs, or for an id already open, says why instead
of running; `HostedUi` registers a plugin's restorer (its code runs contained: a reason it returns is the placeholder's,
with no incident, and what it throws is the placeholder's reason and the plugin's failure) and passes a content tab's
state on; `HostedSessions` passes a session's:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "layout/package-info.java", [(
    " * The workspace layout model (workspace tabs spec, section 7): tab and group ids, a workspace's tab groups in nested",
    " * The workspace layout model (workspace tabs spec, section 7): tab and group ids, how a plugin's tab comes back\n"
    " * ({@code TabRestore}, section 13), a workspace's tab groups in nested", 1)])
C = A + "contributions/"
edit(C + "Tab.java", [
    ("import dev.jasper.app.layout.TabId;\n", "import dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.TabRestore;\n", 1),
    ("    private List<ContentStatus> status = List.of();\n",
     "    private List<ContentStatus> status = List.of();\n    private Optional<TabRestore> restore = Optional.empty();\n", 1),
    ("    public List<ContentStatus> status() { return status; }\n",
     """    public List<ContentStatus> status() { return status; }
    /** @return how the tab comes back when the windows are restored; empty while it does not (workspace tabs spec 13) */
    public Optional<TabRestore> restore() { return restore; }

    /**
     * How the tab comes back when the windows are restored, as its plugin says; a change is saved as a layout change is.
     * Ignored once the tab has closed.
     *
     * @param value the restorer kind and restore state, or empty
     */
    public void restore(Optional<TabRestore> value) {
        Objects.requireNonNull(value, "value");
        if (closed || value.equals(restore)) return;
        restore = value;
        registry.restoreChanged.run();
    }
""", 1)])
R = C + "TabRegistry.java"
edit(R, [
    ("import dev.jasper.app.layout.TabId;\n", "import dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.TabRestore;\n", 1),
    ("import java.util.function.Consumer;\n", "import java.util.function.Consumer;\nimport java.util.function.Function;\n", 1),
    ("    private CompletableFuture<Boolean> closing;\n",
     """    private CompletableFuture<Boolean> closing;
    /** Each plugin's restorers by kind (workspace tabs spec 13). */
    private final Map<String, Map<String, Function<Restoring, Optional<String>>>> restorers = new HashMap<>();
    /** The saved tab being rebuilt: the next tab its plugin opens takes its id and window. */
    private Slot slot;
    /** Told when a tab's restore state changes, which is saved as a layout change is. */
    Runnable restoreChanged = () -> { };

    /** A saved tab being rebuilt, and the tab its plugin opened for it so far. */
    private static final class Slot {
        final TabId id; final UUID window; final String plugin; Tab taken;
        Slot(TabId id, UUID window, String plugin) { this.id = id; this.window = window; this.plugin = plugin; }
    }

    /**
     * A saved tab its plugin rebuilds (workspace tabs spec 13).
     *
     * @param window the window being restored
     * @param title the tab's title when it was saved
     * @param state the plugin's restorer kind and the tab's restore state
     */
    public record Restoring(UUID window, String title, TabRestore state) {
        /** Requires every value. */
        public Restoring {
            Objects.requireNonNull(window, "window"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(state, "state");
        }
    }

    /**
     * What rebuilding a saved tab gave (workspace tabs spec 13).
     *
     * @param tab the tab its plugin opened, with the saved id, in the window being restored, which places it
     * @param problem why there is no tab, as its placeholder words it after the plugin's name; empty with a tab
     */
    public record Restored(Optional<Tab> tab, String problem) {
        /** Requires both values. */
        public Restored { Objects.requireNonNull(tab, "tab"); Objects.requireNonNull(problem, "problem"); }
    }
""", 1),
    ("""        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        WorkspaceHost host = owner != null ? hosts.get(owner) : null;""",
     """        if (slot != null && slot.taken == null && slot.plugin.equals(plugin) && tab(slot.id).isEmpty()) {
            // A saved tab its plugin's restorer rebuilds (workspace tabs spec 13): under its saved id, straight into the
            // window being restored, as add registers a restored terminal tab; that window places it with its other saved
            // tabs. No placement applies, and it never waits for a main window being composed (workspace plan 6b Rulings 4
            // and 23).
            var restored = new Tab(this, slot.id, Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
            tabs.add(restored);
            restored.window(slot.window);
            slot.taken = restored;
            return restored;
        }
        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        // While a saved tab is rebuilt there, the window being restored takes no other tab: it goes where an owner-less
        // tab goes (workspace plan 6b Rulings 24).
        WorkspaceHost host = owner != null && !restoring(owner) ? hosts.get(owner) : null;""", 1),
    ("""                WorkspaceHost retry = owner != null ? hosts.get(owner) : null;""",
     """                // While a saved tab is rebuilt there, the window being restored takes no other tab (workspace plan 6b Rulings 24).
                WorkspaceHost retry = owner != null && !restoring(owner) ? hosts.get(owner) : null;""", 1),
    ("""    /**
     * Disposes the tab at once, without asking a guard, and takes it out of its window. Whatever a listener or the content
""",
     """    /**
     * Registers how {@code plugin} rebuilds its saved tabs of {@code kind} (workspace tabs spec 13).
     *
     * @param plugin the plugin
     * @param kind the restorer kind
     * @param restorer runs the plugin's restorer and gives back why it did not restore the tab, if it said or threw why
     * @return the registration
     * @throws IllegalArgumentException when the plugin already has a restorer for {@code kind}
     */
    public Subscription restorer(String plugin, String kind, Function<Restoring, Optional<String>> restorer) {
        requireEdt();
        Objects.requireNonNull(restorer, "restorer");
        var byKind = restorers.computeIfAbsent(plugin, ignored -> new HashMap<>());
        if (byKind.putIfAbsent(kind, restorer) != null) throw new IllegalArgumentException(plugin + " already restores " + kind + " tabs");
        return new Subscription(() -> {
            var current = restorers.get(plugin);
            if (current != null && current.remove(kind, restorer) && current.isEmpty()) restorers.remove(plugin);
        });
    }

    /**
     * Whether {@code plugin} rebuilds saved tabs of {@code kind} now: only such a tab is saved (workspace plan 2 Rulings 20).
     *
     * @param plugin the plugin
     * @param kind the restorer kind
     * @return true while its restorer is registered
     */
    public boolean restores(String plugin, String kind) { return restorers.getOrDefault(plugin, Map.of()).containsKey(kind); }

    /**
     * Tells {@code listener} whenever a tab's restore state changes (workspace tabs spec 13).
     *
     * @param listener told on the EDT; it replaces the one before
     */
    public void onRestoreChanged(Runnable listener) { restoreChanged = Objects.requireNonNull(listener, "listener"); }

    /**
     * Rebuilds one saved tab through its plugin's restorer (workspace tabs spec 13). The tab the plugin opens meanwhile
     * takes {@code id} and the window being restored, whatever window it named, and is not shown: that window places it.
     *
     * @param id the saved tab's id
     * @param plugin the plugin that saved it
     * @param saved the window, the saved title and the restore state
     * @return the tab, or why there is none: a restore already running (a restorer that restores again), an id already
     *     taken, no restorer for the kind, what the restorer said or threw, or that it opened no tab
     */
    public Restored restore(TabId id, String plugin, Restoring saved) {
        requireEdt();
        if (slot != null) return new Restored(Optional.empty(), "another tab is being restored");
        if (tab(id).isPresent()) return new Restored(Optional.empty(), "a tab with its id is already open");
        var restorer = restorers.getOrDefault(plugin, Map.of()).get(saved.state().kind());
        if (restorer == null) return new Restored(Optional.empty(), "it does not restore " + saved.state().kind() + " tabs");
        slot = new Slot(id, saved.window(), plugin);
        Optional<String> problem;
        Tab taken;
        try {
            problem = restorer.apply(saved);
        } finally {
            taken = slot.taken;
            slot = null;
        }
        // Only a tab still open in the window being restored is the saved tab (workspace plan 6b Rulings 30).
        if (taken != null && !taken.closed() && saved.window().equals(taken.window())) return new Restored(Optional.of(taken), "");
        return new Restored(Optional.empty(), problem.orElse("it opened no tab"));
    }

    /**
     * The saved tab being rebuilt, while {@code plugin} has not opened its tab in {@code window} yet: a session the plugin
     * opens there now takes its id (workspace tabs spec 13).
     *
     * @param plugin the plugin opening a session
     * @param window the window it opens in
     * @return the saved id, or empty when no saved tab of that plugin waits there
     */
    public Optional<TabId> slot(String plugin, UUID window) {
        return slot != null && slot.taken == null && slot.plugin.equals(plugin) && slot.window.equals(window) && tab(slot.id).isEmpty()
            ? Optional.of(slot.id) : Optional.empty();
    }

    /**
     * A window opened the saved tab being rebuilt itself, as a session's terminal tab: the restore hands it back.
     *
     * @param tab the tab, registered with the saved id
     */
    public void restored(Tab tab) { if (slot != null && slot.taken == null && slot.id.equals(tab.id())) slot.taken = tab; }

    /**
     * Whether a saved tab is being rebuilt in {@code window} now: while its restorer runs, that window takes no other tab
     * (workspace plan 6b Rulings 24).
     *
     * @param window a window's id
     * @return true while a restore's slot names it
     */
    public boolean restoring(UUID window) { return slot != null && slot.window.equals(window); }

    /**
     * Disposes the tab at once, without asking a guard, and takes it out of its window. Whatever a listener or the content
""", 1)])
edit(A + "terminals/SessionRequest.java", [
    ("import java.util.Objects;\n", "import dev.jasper.app.layout.TabRestore;\nimport java.util.Objects;\nimport java.util.Optional;\n", 1),
    (" * @param icon the provider's tab icon, or {@code null} for the host terminal icon; carried, never painted here\n */\n"
     "public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,\n"
     "                             Executor cleanup, Icon icon) {",
     " * @param icon the provider's tab icon, or {@code null} for the host terminal icon; carried, never painted here\n"
     " * @param restore how the session's tab comes back when the windows are restored; empty when it does not\n */\n"
     "public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,\n"
     "                             Executor cleanup, Icon icon, Optional<TabRestore> restore) {", 1),
    ("        Objects.requireNonNull(cleanup, \"cleanup\");\n    }\n",
     "        Objects.requireNonNull(cleanup, \"cleanup\");\n        Objects.requireNonNull(restore, \"restore\");\n    }\n\n"
     "    /** A request whose tab does not come back when the windows are restored. */\n"
     "    public SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,\n"
     "                          Executor cleanup, Icon icon) {\n"
     "        this(providerId, title, closeOnExit, connector, cleanup, icon, Optional.empty());\n    }\n", 1)])
P = A + "plugins/"
edit(P + "HostedSessions.java", [(
    "        }, contained, spec.icon().map(icons).orElse(null));",
    "        }, contained, spec.icon().map(icons).orElse(null),\n"
    "            spec.restore().map(state -> new dev.jasper.app.layout.TabRestore(state.kind(), state.values())));", 1)])
U = P + "HostedUi.java"
edit(U, [
    ("        public void setFocusTarget(Runnable focus) { requireUi(\"content focus\"); entry.focusTarget(() -> containment.run(pluginId, \"content focus\", focus)); }\n",
     "        public void setFocusTarget(Runnable focus) { requireUi(\"content focus\"); entry.focusTarget(() -> containment.run(pluginId, \"content focus\", focus)); }\n"
     "        public void setRestore(Optional<dev.jasper.sdk.RestoreState> state) { requireUi(\"content restore\"); entry.restore(state.map(HostedUi::restore)); }\n", 1),
    ("""                var existing = ownedContent.get(entry); if (existing != null) return existing;
                var owned = new OwnedContent(entry); ownedContent.put(entry, owned);""",
     """                var existing = ownedContent.get(entry); if (existing != null) return existing;
                var owned = new OwnedContent(entry); ownedContent.put(entry, owned);
                entry.restore(spec.restore().map(HostedUi::restore));""", 1),
    ("""            @Override public Subscription onQuitting(Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {""",
     """            @Override public Subscription restorer(String kind, dev.jasper.sdk.ui.TabRestorer restorer) {
                HostedUi.this.guard("tab restorer");
                java.util.Objects.requireNonNull(restorer, "restorer");
                // The kind's rules are the restore state's (workspace tabs spec 14).
                new dev.jasper.sdk.RestoreState(kind, java.util.Map.of());
                var registration = model.tabs().restorer(pluginId, kind, saved -> {
                    dev.jasper.sdk.RestoreState state;
                    try {
                        state = new dev.jasper.sdk.RestoreState(saved.state().kind(), saved.state().state());
                    } catch (IllegalArgumentException | NullPointerException invalid) {
                        // A file this build did not write may hold a state the SDK refuses: no restorer sees it, and it
                        // is no failure (workspace plan 6b Rulings 30).
                        return Optional.of("its saved state is not valid");
                    }
                    var request = new dev.jasper.sdk.ui.RestoreRequest(terminals.windowHandle(saved.window()), saved.title(), state);
                    // A reason the restorer gives is expected, and no failure; what it throws is the plugin's failure.
                    var said = new java.util.concurrent.atomic.AtomicReference<Optional<String>>(Optional.empty());
                    Throwable thrown = containment.attempt(pluginId, "tab restorer", () -> {
                        Optional<String> declined = restorer.restore(request);
                        said.set(declined == null ? Optional.empty() : declined.filter(reason -> !reason.isBlank()));
                        return null;
                    });
                    return thrown == null ? said.get() : Optional.of(problem(thrown));
                });
                return tracked(registration::close);
            }
            @Override public Subscription onQuitting(Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {""", 1),
    ("""    private WindowOwner resolveContentOwner(WindowOwner owner) {""",
     """    /** The application's twin of a plugin's restore state (workspace tabs spec 13). */
    private static dev.jasper.app.layout.TabRestore restore(dev.jasper.sdk.RestoreState state) {
        return new dev.jasper.app.layout.TabRestore(state.kind(), state.values());
    }

    /** Why a restorer that threw restored nothing, as a placeholder words it. */
    private static String problem(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private WindowOwner resolveContentOwner(WindowOwner owner) {""", 1)])
W = A + "workspace/WindowContent.java"
edit(W, [("""        if (closed) return null;
        TabId tabId = TabId.random();
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
""", """        if (closed) return null;
        // A plugin's session opened while its saved tab is rebuilt here takes that tab's place (workspace tabs spec 13); any
        // other terminal tab is refused until the restorer is done (workspace plan 6b Rulings 24).
        Optional<TabId> saved = requestOrNull == null ? Optional.empty() : registry.slot(requestOrNull.providerId(), id);
        if (saved.isEmpty() && registry.restoring(id)) return null;
        TabId tabId = saved.orElseGet(TabId::random);
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        if (requestOrNull != null) tab.restore(requestOrNull.restore());
        if (saved.isPresent()) {
            // The restore places it with the window's other tabs; its session connects when it is first shown.
            terminal.pane().restored();
            registry.restored(tab);
            return terminal;
        }
""", 1)])
PY
```

The contract harness opens the main window, then a child window, for a tab placed in a new window while none is open,
as `WindowManager.emptyWindow` does (plan 4b's final-review hand-off; Rulings 25):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
    """                contributions.tabs().move(tab, workspace.get());
            }, window -> { }, workspace)));
""", """                contributions.tabs().move(tab, workspace.get());
            }, window -> { }, () -> {
                // A tab placed in a new window while none is open: the main window first, then the tab's own, as the window
                // manager's emptyWindow does (workspace plan 6b Rulings 25).
                if (main[0] == null) workspace.get();
                return workspace.get();
            })));
""", 1)])
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.contributions.TabRegistryTest' --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.HostedSessionsTest' --tests 'dev.jasper.app.plugins.HostedUiTest' --continue`
Expected: PASS: `RestoreStateTest`, the five new contract tests in `FakeContractTest` and `AppContractTest`,
`FakeContractTest.whileARestorerRunsTheWindowBeingRestoredOpensNoOtherSession`,
`aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot` and `aSavedStateTheSdkRefusesRestoresNothingAndRaisesNoIncident`;
every other contract test as before.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app
git commit -F - <<'EOF'
Workspace tabs: restore state and restorers

ContentSpec and SessionSpec carry how a plugin's tab comes back,
ContentTab.setRestore changes it, and Windows.restorer registers the
restorer that rebuilds it. The tab registry keeps each plugin's
restorers and gives the tab a restorer opens the saved id and window,
which takes no other tab meanwhile; the fakes and the contract suite
restore through the same SDK. The application's contract harness opens
the main window before a new window's tab, as the window manager does.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Plugins' tabs saved and restored in every kind of window, and placeholders

Spec 9.2 and 13, parent tabs spec 9 and 10: a plugin's tab is saved while its plugin restores its kind (Rulings 3) and
comes back through its restorer in its saved parent tab and pane of whichever window kind held it (Rulings 19); a tab
whose plugin does not run, or whose restorer declines or fails, comes back as a placeholder that says why (not
installed, turned off, waiting for review, could not start, or the restorer's reason), with Close and, while the plugin
runs, Retry, both acting in whatever window holds it then (Rulings 5, 6 and 7). A saved record naming Jasper itself is
left out (Rulings 22), the window being restored takes no other tab while a restorer runs (Rulings 24), and a workspace
that would be too large to read back leaves out plugins' tabs, largest first, as closing them would (Rulings 16 and
21). Plan 6a's test of a full and a pane window left out for holding only plugins' tabs now expects both back, holding
placeholders (Rulings 20).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/PlaceholderContent.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/PluginTabRestoreTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Tab.java`, `persistence/WorkspaceFile.java`,
  `workspace/WorkspaceView.java`, `workspace/WindowContent.java`, `workspace/WindowManager.java`,
  `plugins/PluginRuntime.java`, `application/JasperApplication.java`, `docs/design/theme-engine/migration.csv` (two rows)
- Test: `workspace/WorkspaceRestoreTest.java` (one case), `plugins/PluginRuntimeTest.java`,
  `application/JasperApplicationWorkspaceTest.java`

**Interfaces:**
- Consumes: Task 1's `Tab.restore()`, `TabRegistry.restore`, `restores`, `restoring`, `onRestoreChanged`, `Restoring`,
  `Restored`, `TabRestore`; plan 6a's `WindowContent.restore`, `mount`, `startShown`, `WindowManager.snapshot`,
  `windowRecord`, `changed`, `open(…, prepare)`, `WorkspaceFile.keeping`, `TestWindows`, `WorkspaceStore`; plan 7a's
  `ParentTabsView.holding(Tab)`, `WindowContent.workspace()`, `parentTabs()`; plan 7b's `WindowManager.popOutTab(Tab)`,
  `WindowContent.isPaneWindow()`; plan 4b's `TabRegistry.APP_OWNER`.
- Produces: `Tab.Kind.PLACEHOLDER`; package `final class PlaceholderContent(WorkspaceFile.TabRecord.Content saved, String
  why, boolean retryable, Runnable onRetry, Runnable onClose, Runnable onShown)` (`saved()`, `reason()`, `retryable()`,
  `failed(String, boolean)`, `shown()`, `retryButton()`, `closeButton()`); package `BiFunction<TabId,
  WorkspaceFile.TabRecord.Content, Optional<Tab>> WindowContent.savedTabs`, `void WindowContent.retry(Tab)`, `void
  WorkspaceView.openAt(Tab, GroupId, int)`; `public record WindowManager.PluginState(String name, Kind kind)` with
  `Kind { RUNNING, NOT_INSTALLED, TURNED_OFF, NEEDS_CONSENT, FAILED }`; `public void
  WindowManager.plugins(Function<String, PluginState> states)`; `public record PluginRuntime.Standing(String name, Kind
  kind)` with `Kind { RUNNING, NEEDS_CONSENT, TURNED_OFF, FAILED }` and `public Optional<Standing>
  PluginRuntime.standing(String id)`; `public static final int WorkspaceFile.MAX_BYTES` (now public) and `public static
  int WorkspaceFile.encodedBytes(Snapshot)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/PluginTabRestoreTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.PaneLayout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.TabRestore;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 9.2 and 13, parent tabs spec 9: a plugin's saved tab comes back through its restorer in its saved
 * parent tab and pane, in every kind of window, or as a placeholder that says why.
 */
class PluginTabRestoreTest {
    private static final String NOTES = "dev.example.notes";
    @TempDir Path folder;
    private TestWindows windows;
    /** How each plugin stands, by id; a plugin not named is not installed. */
    private final Map<String, WindowManager.PluginState> plugins = new java.util.concurrent.ConcurrentHashMap<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private Path file() { return folder.resolve("workspace.toml"); }

    /** Windows restored from {@code saved}, where the plugins {@code running} names run, by id and name. */
    private void windows(WorkspaceFile.Snapshot saved, Map<String, String> running) throws Exception {
        running.forEach((id, name) -> plugins.put(id, new WindowManager.PluginState(name, WindowManager.PluginState.Kind.RUNNING)));
        WorkspaceFile.write(file(), saved);
        edt(() -> {
            windows = new TestWindows();
            var store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> () -> { }, Runnable::run, kept -> { });
            windows.manager.workspace(store);
            windows.manager.plugins(id -> plugins.getOrDefault(id, new WindowManager.PluginState(id, WindowManager.PluginState.Kind.NOT_INSTALLED)));
        });
    }

    /** The notes plugin's saved note {@code name}: its title is {@code <name>.md}, and it reopens under the key {@code note:<name>}. */
    private static WorkspaceFile.TabRecord.Content note(String name) {
        return new WorkspaceFile.TabRecord.Content(name + ".md", NOTES, "note", Map.of("name", name));
    }

    private static final WorkspaceFile.TabRecord.Content NOTE = note("todo");
    private static final WorkspaceFile.TabRecord.Terminal SHELL = new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), "");

    /** The main window, saved without a place, holding {@code parents}, the first one shown. */
    private static WorkspaceFile.WindowRecord main(Map<TabId, WorkspaceFile.TabRecord> tabs, ParentTab... parents) {
        return new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, new ParentTabs(List.of(parents), parents[0].id()), tabs);
    }

    /** The main window with one parent tab whose one pane holds a terminal tab, then {@code record}'s tab, selected. */
    private static WorkspaceFile.Snapshot mainWith(TabId shell, TabId saved, WorkspaceFile.TabRecord record) {
        Group group = new Group(GroupId.random(), List.of(shell, saved), Optional.of(saved));
        return new WorkspaceFile.Snapshot(List.of(main(Map.of(shell, SHELL, saved, record),
            new ParentTab(ParentTabId.random(), new Layout(group, group.id(), Optional.empty())))));
    }

    /** The notes plugin's restorer: it opens the note its state names, under the saved title, in the window being restored. */
    private void notesRestorer() throws Exception {
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            Tab opened = windows.model.tabs().open(NOTES, "note:" + restoring.state().state().get("name"), restoring.title(),
                restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
    }

    @Test void aPluginsTabComesBackInItsSavedParentTabAndPaneInEveryKindOfWindow() throws Exception {
        TabId shell = TabId.random(), left = TabId.random(), right = TabId.random(), away = TabId.random(), popped = TabId.random();
        Group leftPane = new Group(GroupId.random(), List.of(left), Optional.of(left));
        Group rightPane = new Group(GroupId.random(), List.of(right), Optional.of(right));
        ParentTab shown = new ParentTab(ParentTabId.random(), Layout.of(shell));
        ParentTab hidden = new ParentTab(ParentTabId.random(), new Layout(new Split(Orientation.HORIZONTAL,
            List.of(new Child(leftPane, 0.5), new Child(rightPane, 0.5))), rightPane.id(), Optional.empty()));
        var mainRecord = main(Map.of(shell, SHELL, left, note("left"), right, note("right")), shown, hidden);
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0,
            ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(away))), Map.of(away, note("away")));
        var pane = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0,
            new PaneLayout(Layout.of(popped), new Origin(hidden.id(), leftPane.id(), Side.RIGHT, 0.5)), Map.of(popped, note("popped")));
        var saved = new WorkspaceFile.Snapshot(List.of(mainRecord, full, pane));
        windows(saved, Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout()).as("the notes in the hidden parent tab's two panes, under their saved ids")
                .isEqualTo(mainRecord.layout());
            for (TabId note : List.of(left, right, away, popped))
                assertThat(windows.model.tabs().tab(note)).as("rebuilt by its plugin")
                    .hasValueSatisfying(tab -> assertThat(tab.kind()).isEqualTo(Tab.Kind.HOSTED));
            List<ChildWindow> children = windows.manager.children();
            assertThat(children).hasSize(2);
            assertThat(children.get(0).content().windowLayout()).as("a full window").isEqualTo(full.layout());
            assertThat(children.get(1).content().isPaneWindow()).isTrue();
            assertThat(children.get(1).content().windowLayout()).as("a pane window, with its origin").isEqualTo(pane.layout());
            assertThat(windows.model.tabs().tab(popped).orElseThrow().window()).isEqualTo(children.get(1).id());
            assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(saved);
        });
        windows.launch();
    }

    @Test void aTabWhosePluginIsNotRunningComesBackAsAPlaceholderAndIsSavedAgainAsItWas() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            assertThat(placeholder.id()).isEqualTo(saved);
            assertThat(placeholder.kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
            assertThat(placeholder.title()).isEqualTo("todo.md");
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("The plugin dev.example.notes is not installed.");
            assertThat(content.retryable()).as("no Retry without its plugin").isFalse();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(saved)).as("saved again as it was").isEqualTo(NOTE);
            assertThat(windows.terminals.tab(saved.value()).orElseThrow().kind()).as("a content tab to plugins")
                .isEqualTo(dev.jasper.app.terminals.TabEntry.Kind.CONTENT);
            content.closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aRestorerThatFailsLeavesAPlaceholderAndRetryPutsItsTabInThePlaceholdersPlace() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            Tab opened = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            GroupId pane = main.content().workspace().model().groupOf(saved).orElseThrow();
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("Notes could not restore this tab: the note is locked.");
            assertThat(content.retryable()).isTrue();
            content.retryButton().doClick();
            assertThat(placeholder.closed()).as("still failing: the placeholder stays").isFalse();
            assertThat(main.content().tabs()).hasSize(2);
            works[0] = true;
            content.retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            Tab note = main.content().selectedTab();
            assertThat(note.kind()).isEqualTo(Tab.Kind.HOSTED);
            assertThat(note.title()).isEqualTo("todo.md");
            assertThat(main.content().tabs()).as("in the placeholder's place").extracting(Tab::id).containsExactly(shell, note.id());
            assertThat(main.content().workspace().model().groupOf(note.id())).as("in the placeholder's pane").contains(pane);
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(note.id())).isEqualTo(NOTE);
        });
        windows.launch();
    }

    @Test void aPlaceholderSaysHowItsPluginStandsAndOffersRetryOnlyWhileItRuns() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.TURNED_OFF));
        windows(mainWith(shell, saved, NOTE), Map.of());
        int[] restored = {0};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            restored[0]++;
            return Optional.of("the note is locked");
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            var content = (PlaceholderContent) main.content().selectedTab().content();
            assertThat(content.reason()).isEqualTo("Notes is turned off.");
            assertThat(content.retryable()).isFalse();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.RUNNING));
            content.shown();
            assertThat(content.reason()).as("asked again when it shows").isEqualTo("Notes runs now: Retry brings this tab back.");
            assertThat(content.retryable()).isTrue();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.FAILED));
            content.retryButton().doClick();
            assertThat(restored[0]).as("and again on Retry, before the restorer runs").isZero();
            assertThat(content.reason()).isEqualTo("Notes could not start.");
            assertThat(content.retryable()).isFalse();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.NEEDS_CONSENT));
            content.shown();
            assertThat(content.reason()).isEqualTo("Notes waits for your review in Manage Plugins.");
        });
        windows.launch();
    }

    /** Parent tabs spec 5: a placeholder moves like any tab, and its Retry rebuilds the tab in the window that holds it then. */
    @Test void aPlaceholderPoppedOutToAPaneWindowRetriesThere() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow pane = windows.manager.popOutTab(placeholder).orElseThrow();
            assertThat(pane.content().isPaneWindow()).isTrue();
            assertThat(placeholder.window()).isEqualTo(pane.id());
            works[0] = true;
            ((PlaceholderContent) placeholder.content()).retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(pane.closed()).as("it holds the rebuilt tab").isFalse();
            assertThat(pane.content().selectedTab().kind()).as("rebuilt in the window that held it").isEqualTo(Tab.Kind.HOSTED);
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aPlaceholderInAPaneWindowClosesFromTheWindowThatHoldsItNow() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow pane = windows.manager.popOutTab(placeholder).orElseThrow();
            ((PlaceholderContent) placeholder.content()).closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(pane.closed()).as("its last tab closed").isTrue();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aWorkspaceTooLargeToReadBackLeavesOutPluginsTabsLargestFirst() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> windows.model.tabs().restorer(NOTES, "big", restoring -> Optional.empty()));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            for (int index = 0; index < 80; index++) {
                Tab big = windows.model.tabs().open(NOTES, "big:" + index, "Big " + index, main.id(), new javax.swing.JPanel());
                big.restore(Optional.of(new TabRestore("big", Map.of("text", "x".repeat(4000 + index)))));
            }
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            assertThat(WorkspaceFile.encodedBytes(snapshot)).as("a read never sets it aside").isLessThanOrEqualTo(WorkspaceFile.MAX_BYTES);
            assertThat(snapshot.main().tabs()).as("terminal tabs and the smaller plugins' tabs stay").containsKeys(shell, saved);
            assertThat(snapshot.main().tabs()).hasSizeLessThan(82).hasSizeGreaterThan(40);
            assertThat(snapshot.main().tabs().values()).noneSatisfy(record -> assertThat(record.title()).isEqualTo("Big 79"));
        });
        windows.launch();
    }

    @Test void aPluginsTabWithoutRestoreStateOrRestorerIsNotSavedAndJaspersOwnTabsNeverAre() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, SHELL), Map.of(NOTES, "Notes"));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab plain = windows.model.tabs().open(NOTES, "plain", "Plain", main.id(), new javax.swing.JPanel());
            Tab unknown = windows.model.tabs().open(NOTES, "unknown", "Unknown", main.id(), new javax.swing.JPanel());
            unknown.restore(Optional.of(new TabRestore("unknown", Map.of())));
            Tab errors = windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.restore(Optional.of(new TabRestore("errors", Map.of())));
            windows.model.tabs().restorer(TabRegistry.APP_OWNER, "errors", restoring -> Optional.empty());
            var tabs = windows.manager.snapshot().orElseThrow().main().tabs();
            assertThat(tabs).containsOnlyKeys(shell, saved);
            assertThat(plain.closed() || unknown.closed() || errors.closed()).isFalse();
        });
        windows.launch();
    }

    /** Workspace plan 6b Rulings 22: a saved tab naming Jasper itself, which only a file this build did not write holds, is left out. */
    @Test void aSavedTabNamingJasperItselfIsLeftOut() throws Exception {
        TabId shell = TabId.random(), errors = TabId.random();
        windows(mainWith(shell, errors, new WorkspaceFile.TabRecord.Content("Errors", TabRegistry.APP_OWNER, "errors", Map.of())), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).as("no placeholder").containsExactly(shell);
            assertThat(windows.model.tabs().tab(errors)).isEmpty();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs()).containsOnlyKeys(shell);
        });
        windows.launch();
    }

    @Test void aSessionsTabComesBackThroughItsPluginAndConnectsWhenFirstShown() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        var record = new WorkspaceFile.TabRecord.Content("build-host", "dev.example.ssh", "ssh", Map.of("host", "build-host"));
        Group group = new Group(GroupId.random(), List.of(shell, saved), Optional.of(shell));
        windows(new WorkspaceFile.Snapshot(List.of(main(Map.of(shell, SHELL, saved, record),
            new ParentTab(ParentTabId.random(), new Layout(group, group.id(), Optional.empty()))))), Map.of("dev.example.ssh", "SSH"));
        Queue<SessionAttempt> attempts = new ArrayDeque<>();
        boolean[] refused = {false};
        edt(() -> windows.model.tabs().restorer("dev.example.ssh", "ssh", restoring -> {
            var request = new SessionRequest("dev.example.ssh", restoring.title(), false, attempts::add, Runnable::run, null,
                Optional.of(restoring.state()));
            WindowContent window = windows.manager.window(restoring.window()).orElseThrow().content();
            window.openTab(HOME, request);
            // The window being restored takes no other tab while the restorer runs (workspace plan 6b Rulings 24).
            refused[0] = window.openTab(HOME, new SessionRequest("dev.example.ssh", "other", false, attempts::add, Runnable::run)) == null;
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab session = main.content().tabs().get(1);
            assertThat(session.id()).isEqualTo(saved);
            assertThat(session.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(refused[0]).as("a second session in the window being restored").isTrue();
            assertThat(main.content().tabs()).hasSize(2);
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(saved)).as("saved again through its plugin")
                .isEqualTo(new WorkspaceFile.TabRecord.Content(session.title(), "dev.example.ssh", "ssh", Map.of("host", "build-host")));
        });
        edt(() -> {
            assertThat(attempts).as("not shown: it has not connected").isEmpty();
            WindowContent content = windows.manager.main().orElseThrow().content();
            content.selectTab(content.tabs().get(1));
        });
        edt(() -> assertThat(attempts).as("shown, it connects once that EDT turn ends").hasSize(1));
        windows.launch();
    }

    /**
     * Workspace plan 6b Rulings 23 and 24: the saved tab names the window being restored at once, though the main window is
     * still being composed; another tab its restorer opens there waits for that window, naming none, and opens in it once
     * it shows.
     */
    @Test void aRestoredTabNamesItsWindowAtOnceAndARestorersOtherTabOpensOnceTheWindowShows() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        var during = new ArrayList<UUID>();
        Tab[] other = new Tab[1];
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            Tab note = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            other[0] = windows.model.tabs().open(NOTES, "note:other", "Other", restoring.window(), new javax.swing.JPanel());
            during.add(note.window());
            during.add(other[0].window());
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(during).as("the saved tab's window at once; none for the other while it waits").containsExactly(main.id(), null);
            assertThat(other[0].window()).isEqualTo(main.id());
            assertThat(main.content().tabs()).as("the other at the end of the shown parent tab's active pane")
                .extracting(Tab::id).containsExactly(shell, saved, other[0].id());
        });
        windows.launch();
    }
}
```

The runtime says which plugins run; plan 6a's full and pane windows holding only plugins' tabs come back holding their
placeholders; through the application, a saved tab of a plugin that does not run is a placeholder:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "plugins/PluginRuntimeTest.java", [(
    """                assertThat(problem.cause()).as("no exception lies behind it").isEmpty();
            });
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
""", """                assertThat(problem.cause()).as("no exception lies behind it").isEmpty();
            });
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        // Workspace tabs spec 13: only a plugin that runs rebuilds its saved tabs; the others' placeholders say why.
        assertThat(runtime.standing("dev.example.probe")).hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.RUNNING));
        assertThat(runtime.standing("dev.example.future")).as("it needs another SDK").hasValueSatisfying(standing -> {
            assertThat(standing.name()).isEqualTo("Future");
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.FAILED);
        });
        assertThat(runtime.standing("dev.example.absent")).as("not installed").isEmpty();
""", 1)])
# Plan 6a left a full and a pane window holding only plugins' tabs out; with placeholders both come back (Rulings 20).
edit(T + "workspace/WorkspaceRestoreTest.java", [(
    """    @Test void aChildWindowWithNothingToRestoreIsLeftOut() throws Exception {""",
    """    @Test void aChildWindowHoldingOnlyAMissingPluginsTabsComesBackWithTheirPlaceholders() throws Exception {""", 1), (
    """            assertThat(windows.manager.children()).as("a full window and a pane window holding only plugins' tabs (6b restores those)").isEmpty();
            assertThat(windows.events).doesNotContain("show child");
""", """            List<ChildWindow> children = windows.manager.children();
            assertThat(children).as("a full window and a pane window, each holding its plugin's tab as a placeholder").hasSize(2);
            assertThat(children.get(0).content().tabs()).extracting(Tab::id, Tab::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(document, Tab.Kind.PLACEHOLDER));
            assertThat(children.get(1).content().isPaneWindow()).isTrue();
            assertThat(children.get(1).content().tabs()).extracting(Tab::id, Tab::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(notes, Tab.Kind.PLACEHOLDER));
""", 1)])
edit(T + "application/JasperApplicationWorkspaceTest.java", [(
    """    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {""",
    """    @Test void aSavedTabOfAPluginThatDoesNotRunComesBackAsAPlaceholder() throws Exception {
        var shell = dev.jasper.app.layout.TabId.random();
        var note = dev.jasper.app.layout.TabId.random();
        var group = new dev.jasper.app.layout.Group(dev.jasper.app.layout.GroupId.random(), List.of(shell, note), java.util.Optional.of(note));
        var parent = new dev.jasper.app.layout.ParentTab(dev.jasper.app.layout.ParentTabId.random(),
            new dev.jasper.app.layout.Layout(group, group.id(), java.util.Optional.empty()));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN,
            java.util.Optional.empty(), 0, ParentTabs.of(parent),
            java.util.Map.of(shell, new WorkspaceFile.TabRecord.Terminal("", home.toString(), ""),
                note, new WorkspaceFile.TabRecord.Content("todo.md", "dev.example.notes", "note", java.util.Map.of("name", "todo")))))));
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                MainWindow main = application.openFirstWindow(List.of()).orElseThrow();
                var tab = application.contributions().tabs().tab(note).orElseThrow();
                assertThat(tab.kind()).isEqualTo(dev.jasper.app.contributions.Tab.Kind.PLACEHOLDER);
                assertThat(tab.title()).isEqualTo("todo.md");
                assertThat(tab.window()).isEqualTo(main.id());
            });
            launch();
        } finally {
            edt(application::quitNow);
            // Quit's save lands in the temporary home on a virtual thread: it ends before JUnit deletes the home.
            application.workspaceStore().written().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `PlaceholderContent`, `Tab.Kind.PLACEHOLDER`, `PluginState`, `plugins(…)`,
`standing(…)`, `encodedBytes` and `MAX_BYTES` (not public).

- [ ] **Step 3: The placeholder**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/PlaceholderContent.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.TabContent;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A plugin's tab that could not come back when the windows were restored (workspace tabs spec 9.2 and 13): it says why,
 * with Close and, while its plugin runs, Retry. It keeps what was saved, so it is saved again as it was and comes back as
 * the plugin's tab once the plugin can restore it. Whenever it shows, it asks again how its plugin stands. EDT only.
 */
final class PlaceholderContent extends JPanel implements TabContent {
    private final WorkspaceFile.TabRecord.Content saved;
    private final JLabel reason = new JLabel("", SwingConstants.CENTER);
    private final JButton retry = new JButton("Retry");
    private final JButton close = new JButton("Close");
    private final Runnable onShown;

    /**
     * A placeholder for {@code saved}.
     *
     * @param saved what was saved for the tab
     * @param why why it did not come back, a sentence
     * @param retryable whether its plugin runs, so that Retry can rebuild it
     * @param onRetry runs the plugin's restorer again
     * @param onClose closes the tab
     * @param onShown asks how its plugin stands, whenever it shows
     */
    PlaceholderContent(WorkspaceFile.TabRecord.Content saved, String why, boolean retryable, Runnable onRetry, Runnable onClose,
                       Runnable onShown) {
        super(new GridBagLayout());
        this.saved = Objects.requireNonNull(saved, "saved");
        this.onShown = Objects.requireNonNull(onShown, "onShown");
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) shown();
        });
        setName("workspace.placeholder");
        setOpaque(false);
        reason.setName("workspace.placeholder.reason");
        reason.putClientProperty("html.disable", Boolean.TRUE);
        NativeStyles.installed().label(reason, Typography.Role.LABEL, SemanticColor.TEXT_PRIMARY, () -> ThemeZone.theme(reason));
        for (JButton button : new JButton[]{retry, close}) {
            button.setName("workspace.placeholder." + button.getText().toLowerCase(java.util.Locale.ROOT));
            button.setFocusable(false);
            NativeStyles.installed().button(button, () -> ThemeZone.theme(button));
        }
        retry.addActionListener(event -> onRetry.run());
        close.addActionListener(event -> onClose.run());
        int gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        var place = new GridBagConstraints();
        place.gridwidth = 2;
        place.insets = new Insets(0, 0, gap, 0);
        add(reason, place);
        place = new GridBagConstraints();
        place.gridy = 1;
        place.insets = new Insets(0, 0, 0, gap / 2);
        add(retry, place);
        place = new GridBagConstraints();
        place.gridy = 1;
        place.insets = new Insets(0, gap / 2, 0, 0);
        add(close, place);
        failed(why, retryable);
    }

    /** What was saved for the tab: it is saved again unchanged. */
    WorkspaceFile.TabRecord.Content saved() { return saved; }

    /** Why the tab did not come back. */
    String reason() { return reason.getText(); }

    /** Whether Retry is offered: its plugin runs. */
    boolean retryable() { return retry.isVisible(); }

    /** A Retry that failed again, or the first failure: says why, and offers Retry while the plugin runs. */
    void failed(String why, boolean retryable) {
        reason.setText(Objects.requireNonNull(why, "why"));
        retry.setVisible(retryable);
        revalidate();
        repaint();
    }

    /** It shows: what it says follows how its plugin stands now. */
    void shown() { onShown.run(); }

    /** The Retry button, for tests. */
    JButton retryButton() { return retry; }

    /** The Close button, for tests. */
    JButton closeButton() { return close; }

    @Override public JComponent component() { return this; }

    @Override public void dispose() { }
}
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); import ledger
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "PlaceholderContent.java", "app.workspace", "PlaceholderContent", T + "PluginTabRestoreTest.java",
    "New in workspace-tabs plan 6b Task 2; a plugin's tab that could not come back; NativeStyles label and buttons")
ledger.add(T + "PluginTabRestoreTest.java", "app.workspace", "PluginTabRestoreTest", T + "PluginTabRestoreTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 6b Task 2; plugins' tabs restored from workspace.toml")
PY
```

- [ ] **Step 4: A window restores plugins' tabs**

`Tab.Kind` gains `PLACEHOLDER`; a window's restore asks `savedTabs` for each plugin's tab, so it places plugins' tabs
with its terminal tabs in their saved parent tabs and panes; Retry puts the rebuilt tab in the placeholder's place in
the pane of whichever parent tab holds it; a placeholder takes the theme as a plugin's tab does:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "contributions/Tab.java", [(
    """        /** A plugin's view. */
        HOSTED
""", """        /** A plugin's view. */
        HOSTED,
        /** A plugin's tab that could not come back when the windows were restored, saying why (workspace tabs spec 9.2). */
        PLACEHOLDER
""", 1)])
W = A + "workspace/"
edit(W + "WorkspaceView.java", [(
    """    private void place(Tab tab, Layout next) {""",
    """    /** Opens {@code tab} at {@code index} of {@code group}, selected: a placeholder's Retry puts the rebuilt tab in its place. */
    void openAt(Tab tab, GroupId group, int index) { place(tab, layout.open(tab.id(), group, index)); }

    private void place(Tab tab, Layout next) {""", 1)])
edit(W + "WindowContent.java", [
    ("""    /** What {@code workspace.toml} saves of this window changed: its layout, or a terminal tab's name or directory (spec 13). */
    Runnable onLayoutChanged = () -> {};
""", """    /** What {@code workspace.toml} saves of this window changed: its layout, or a terminal tab's name or directory (spec 13). */
    Runnable onLayoutChanged = () -> {};
    /**
     * How a plugin's saved tab comes back in this window (workspace tabs spec 13): its plugin's restorer rebuilds it with
     * the saved id, else it is a placeholder; empty leaves it out, as closing it would. The window manager gives it.
     */
    java.util.function.BiFunction<TabId, WorkspaceFile.TabRecord.Content, Optional<Tab>> savedTabs = (tabId, saved) -> Optional.empty();
""", 1),
    ("""            for (TabId saved : record.layout().tabs())
                if (record.tabs().get(saved) instanceof WorkspaceFile.TabRecord.Terminal terminal) restored.put(saved, restoredTerminal(saved, terminal));
""", """            // A plugin's tab comes back through its plugin, or as a placeholder (workspace tabs spec 13; workspace plan 6b
            // Rulings 19): every tab takes its saved parent tab and pane below, terminal tabs and plugins' alike.
            for (TabId saved : record.layout().tabs()) {
                Optional<Tab> tab = switch (record.tabs().get(saved)) {
                    case WorkspaceFile.TabRecord.Terminal terminal -> Optional.of(restoredTerminal(saved, terminal));
                    case WorkspaceFile.TabRecord.Content content -> savedTabs.apply(saved, content);
                };
                tab.ifPresent(found -> restored.put(saved, found));
            }
""", 1),
    ("""    /** A saved terminal tab whose shell waits until it first shows (workspace tabs spec 13). */""",
     """    /**
     * Retry on a placeholder (workspace tabs spec 9.2): its plugin's restorer runs again, and the tab it opens takes the
     * placeholder's place in its pane, in whichever parent tab holds it, selected; a placeholder that fails again says
     * why (workspace plan 6b Rulings 6 and 19).
     */
    void retry(Tab placeholder) {
        if (closed || !(placeholder.content() instanceof PlaceholderContent saved)) return;
        Optional<WorkspaceView> view = parentTabs.holding(placeholder);
        if (view.isEmpty()) return;
        Optional<Tab> rebuilt = savedTabs.apply(TabId.random(), saved.saved());
        if (rebuilt.isEmpty()) return;
        if (rebuilt.get().content() instanceof PlaceholderContent again) {
            saved.failed(again.reason(), again.retryable());
            rebuilt.get().close();
            return;
        }
        GroupId pane = view.get().model().groupOf(placeholder.id()).orElseThrow();
        int index = view.get().model().group(pane).orElseThrow().tabs().indexOf(placeholder.id());
        Runnable replace = () -> {
            mount(rebuilt.get());
            view.get().openAt(rebuilt.get(), pane, index);
            placeholder.close();
        };
        if (terminals != null) terminals.atomically(replace); else replace.run();
    }

    /** A saved terminal tab whose shell waits until it first shows (workspace tabs spec 13). */""", 1),
    ("            if (tab.kind() == Tab.Kind.HOSTED) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));",
     "            if (tab.kind() != Tab.Kind.TERMINAL) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));", 1)])
PY
```

- [ ] **Step 5: The window manager saves and rebuilds them**

The snapshot saves a plugin's tab, content or session, while its plugin restores that kind, and a placeholder as it was
saved; an app-owned tab never, going or coming; a snapshot too large for the codec's limit leaves out tabs, plugins'
first and largest first, as closing them would (`WorkspaceFile.keeping`). `savedTab` rebuilds through the registry or
makes the placeholder, whose Retry and Close find the window that holds it when pressed and which asks how its plugin
stands whenever it shows and on Retry; a restore state that changes is saved as a layout change is; the application says
how each plugin stands:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
# The codec says how big a file it reads, so a snapshot can stay within it.
edit(A + "persistence/WorkspaceFile.java", [
    ("""    /** The largest file read: {@code ui-state.toml}'s cap (workspace plan 2, Rulings 15). */
    static final int MAX_BYTES = 256 * 1024;""",
     """    /** The largest file read, in bytes: {@code ui-state.toml}'s cap (workspace plan 2, Rulings 15); a larger one is set aside. */
    public static final int MAX_BYTES = 256 * 1024;""", 1),
    ("""    /**
     * The file's text: the windows in order; a main or full window's parent tabs in bar order, each with its nodes parents
     * first, or a pane window's origin and nodes; then the window's tabs in window order.
     */
    static String encode(Snapshot snapshot) {""",
     """    /**
     * How many bytes {@link #write} writes for {@code snapshot}: more than {@link #MAX_BYTES} and the next read sets the file
     * aside (workspace plan 6b Rulings 16).
     *
     * @param snapshot the windows
     * @return the encoded size in UTF-8 bytes
     */
    public static int encodedBytes(Snapshot snapshot) {
        return encode(snapshot).getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * The file's text: the windows in order; a main or full window's parent tabs in bar order, each with its nodes parents
     * first, or a pane window's origin and nodes; then the window's tabs in window order.
     */
    static String encode(Snapshot snapshot) {""", 1)])
M = A + "workspace/WindowManager.java"
edit(M, [
    ("import dev.jasper.app.layout.TabId;\n", "import dev.jasper.app.layout.TabId;\nimport dev.jasper.app.layout.TabRestore;\n", 1),
    ("""    /**
     * A window was shown, minimized or restored.""",
     """    /**
     * How a plugin stands when the windows restore its tabs (workspace tabs spec 13): its name, and whether it runs or
     * why not, which a placeholder says.
     *
     * @param name its display name; its id when it is not installed
     * @param kind whether it runs, or why not
     */
    public record PluginState(String name, Kind kind) {
        /** Whether a plugin runs, or why not. */
        public enum Kind {
            /** It started at this launch: its restorers rebuild its tabs. */
            RUNNING,
            /** No plugin with that id is installed. */
            NOT_INSTALLED,
            /** The user turned it off. */
            TURNED_OFF,
            /** It waits for the user's review of what it asks to do. */
            NEEDS_CONSENT,
            /** It could not start at this launch. */
            FAILED
        }

        /** Requires both values. */
        public PluginState { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); }
    }

    /**
     * A window was shown, minimized or restored.""", 1),
    ("""    /** Where the windows are saved; none in a test that does not save. */
    private WorkspaceStore store;
""", """    /** Where the windows are saved; none in a test that does not save. */
    private WorkspaceStore store;
    /** How each plugin stands, by id: without the application's answer, none is installed. */
    private Function<String, PluginState> plugins = id -> new PluginState(id, PluginState.Kind.NOT_INSTALLED);
""", 1),
    ("""        model.panelRoute(this::routePanel);
    }
""", """        model.panelRoute(this::routePanel);
        // A plugin's tab giving new restore state changes what is saved (workspace tabs spec 13).
        model.tabs().onRestoreChanged(this::changed);
    }

    /**
     * How the plugins stand (workspace tabs spec 13): a saved tab of a plugin that runs is rebuilt by its restorer, else it
     * is a placeholder that says why, with Retry while its plugin runs; asked again whenever a placeholder shows or its
     * Retry is pressed.
     *
     * @param states a plugin's standing by its id
     */
    public void plugins(Function<String, PluginState> states) { plugins = Objects.requireNonNull(states, "states"); }
""", 1),
    ("""            content.onLayoutChanged = this::changed;
            if (!prepare.test(window)) {""", """            content.onLayoutChanged = this::changed;
            // A plugin's saved tab comes back through its plugin, or as a placeholder; one naming Jasper itself is left out
            // (workspace plan 6b Rulings 3 and 22).
            content.savedTabs = (tabId, saved) -> saved.pluginId().equals(TabRegistry.APP_OWNER) ? Optional.empty()
                : Optional.of(savedTab(content, tabId, saved));
            if (!prepare.test(window)) {""", 1),
    ("""        var records = new ArrayList<WorkspaceFile.WindowRecord>();
        for (JasperWindow window : windows()) windowRecord(window).ifPresent(records::add);
        return Optional.of(new WorkspaceFile.Snapshot(records));
    }
""", """        var records = new ArrayList<WorkspaceFile.WindowRecord>();
        for (JasperWindow window : windows()) windowRecord(window).ifPresent(records::add);
        return Optional.of(fitted(records));
    }

    /**
     * The windows as {@code records} save them, with tabs taken out until the file fits {@link WorkspaceFile#MAX_BYTES}, which
     * a read would otherwise set aside (workspace plan 6b Rulings 16): plugins' tabs first, the largest first, then terminal
     * tabs; each is logged, and taken out as closing it would (Rulings 21): a parent tab left with none goes, and a child
     * window left with none is left out.
     */
    private WorkspaceFile.Snapshot fitted(List<WorkspaceFile.WindowRecord> records) {
        var windows = new ArrayList<>(records);
        var snapshot = new WorkspaceFile.Snapshot(windows);
        while (WorkspaceFile.encodedBytes(snapshot) > WorkspaceFile.MAX_BYTES) {
            int holder = -1;
            TabId largest = null;
            long largestSize = -1;
            boolean largestContent = false;
            for (int index = 0; index < windows.size(); index++)
                for (var entry : windows.get(index).tabs().entrySet()) {
                    boolean content = entry.getValue() instanceof WorkspaceFile.TabRecord.Content;
                    long size = size(entry.getValue());
                    if (largest == null || content && !largestContent || content == largestContent && size > largestSize) {
                        holder = index; largest = entry.getKey(); largestSize = size; largestContent = content;
                    }
                }
            if (largest == null) break;
            WorkspaceFile.WindowRecord window = windows.get(holder);
            TabId out = largest;
            LOG.log(System.Logger.Level.WARNING, "Not saving the tab " + window.tabs().get(out).title()
                + ": the saved windows would pass workspace.toml's size limit");
            Optional<WindowLayout> kept = WorkspaceFile.keeping(window.layout(), tab -> !tab.equals(out), window.role() == WorkspaceFile.Role.MAIN);
            var tabs = new HashMap<>(window.tabs());
            tabs.remove(out);
            if (kept.isEmpty()) windows.remove(holder);
            else windows.set(holder, new WorkspaceFile.WindowRecord(window.role(), window.bounds(), window.extendedState(), kept.get(), tabs));
            snapshot = new WorkspaceFile.Snapshot(windows);
        }
        return snapshot;
    }

    /** About how much of the file a tab's record takes. */
    private static long size(WorkspaceFile.TabRecord record) {
        return switch (record) {
            case WorkspaceFile.TabRecord.Terminal terminal -> terminal.title().length() + terminal.directory().length() + terminal.profile().length();
            case WorkspaceFile.TabRecord.Content content -> content.title().length() + content.state().entrySet().stream()
                .mapToLong(value -> value.getKey().length() + value.getValue().length()).sum();
        };
    }
""", 1),
    ("""    /** What a tab needs to come back: a terminal tab's name, directory and profile; any other tab is not saved. */
    private Optional<WorkspaceFile.TabRecord> tabRecord(Tab tab) {
        if (tab.content() instanceof TerminalContent terminal) return terminal.record().map(WorkspaceFile.TabRecord.class::cast);
        return Optional.empty();
    }
""", """    /**
     * What a tab needs to come back (workspace tabs spec 13): a terminal tab's name, directory and profile; a plugin's tab,
     * content or session, its restore state while its plugin restores that kind; a placeholder what was saved for it. An
     * app-owned tab and any other plugin's tab is not saved.
     */
    private Optional<WorkspaceFile.TabRecord> tabRecord(Tab tab) {
        if (tab.content() instanceof PlaceholderContent placeholder) return Optional.of(placeholder.saved());
        Optional<String> plugin = tab.content() instanceof TerminalContent terminal ? terminal.pane().snapshot().providerId()
            : Optional.of(tab.plugin()).filter(id -> !id.isEmpty());
        if (plugin.isEmpty())
            return tab.content() instanceof TerminalContent terminal ? terminal.record().map(WorkspaceFile.TabRecord.class::cast) : Optional.empty();
        if (plugin.get().equals(TabRegistry.APP_OWNER)) return Optional.empty();
        return tab.restore().filter(state -> model.tabs().restores(plugin.get(), state.kind()))
            .map(state -> new WorkspaceFile.TabRecord.Content(tab.title(), plugin.get(), state.kind(), state.state()));
    }

    /**
     * A plugin's saved tab for {@code content}'s window, with the saved id: rebuilt by its plugin's restorer, else a
     * placeholder that says why (workspace tabs spec 9.2 and 13).
     */
    private Tab savedTab(WindowContent content, TabId id, WorkspaceFile.TabRecord.Content saved) {
        PluginState state = plugins.apply(saved.pluginId());
        if (state.kind() != PluginState.Kind.RUNNING) return placeholder(content, id, saved, reason(state), false);
        TabRegistry.Restored restored = model.tabs().restore(id, saved.pluginId(),
            new TabRegistry.Restoring(content.id(), saved.title(), new TabRestore(saved.restorer(), saved.state())));
        if (restored.tab().isPresent()) return restored.tab().get();
        String problem = restored.problem().endsWith(".") ? restored.problem() : restored.problem() + ".";
        return placeholder(content, id, saved, state.name() + " could not restore this tab: " + problem, true);
    }

    /** Why a plugin that does not run restores nothing, as a placeholder says it. */
    private static String reason(PluginState state) {
        return switch (state.kind()) {
            case NOT_INSTALLED -> "The plugin " + state.name() + " is not installed.";
            case TURNED_OFF -> state.name() + " is turned off.";
            case NEEDS_CONSENT -> state.name() + " waits for your review in Manage Plugins.";
            case FAILED -> state.name() + " could not start.";
            case RUNNING -> state.name() + " runs.";
        };
    }

    /**
     * A placeholder tab in {@code content}'s window, titled as the saved tab was. Its Retry and Close act in whatever window
     * holds it when they are pressed, and it asks how its plugin stands again when it shows and on Retry.
     */
    private Tab placeholder(WindowContent content, TabId id, WorkspaceFile.TabRecord.Content saved, String why, boolean retryable) {
        Tab[] tab = new Tab[1];
        var placeholder = new PlaceholderContent(saved, why, retryable, () -> retry(tab[0]),
            () -> window(tab[0].window()).ifPresent(holder -> holder.content().closeTab(tab[0])), () -> standing(tab[0]));
        tab[0] = model.tabs().add(id, Tab.Kind.PLACEHOLDER, placeholder, content.id());
        tab[0].presentation(saved.title(), "", null, false, "", "");
        return tab[0];
    }

    /** Retry on a placeholder: its plugin's restorer runs again in the window that holds it, while the plugin runs. */
    private void retry(Tab placeholder) {
        if (placeholder.closed() || !standing(placeholder)) return;
        window(placeholder.window()).ifPresent(holder -> holder.content().retry(placeholder));
    }

    /**
     * Says again how a placeholder's plugin stands: why it does not run, without Retry, or, for one that came back as not
     * running and runs now, that Retry brings the tab back.
     *
     * @return whether the plugin runs
     */
    private boolean standing(Tab placeholder) {
        if (placeholder.closed() || !(placeholder.content() instanceof PlaceholderContent content)) return false;
        PluginState state = plugins.apply(content.saved().pluginId());
        if (state.kind() != PluginState.Kind.RUNNING) {
            content.failed(reason(state), false);
            return false;
        }
        if (!content.retryable()) content.failed(state.name() + " runs now: Retry brings this tab back.", true);
        return true;
    }
""", 1)])
J = A + "application/JasperApplication.java"
edit(J, [("""        windows.workspace(workspaceStore);
    }
""", """        windows.workspace(workspaceStore);
        // A saved tab of a plugin that does not run comes back as a placeholder that says why (workspace tabs spec 13).
        windows.plugins(id -> {
            PluginRuntime running = plugins;
            return (running == null ? Optional.<PluginRuntime.Standing>empty() : running.standing(id))
                .map(found -> new WindowManager.PluginState(found.name(), switch (found.kind()) {
                    case RUNNING -> WindowManager.PluginState.Kind.RUNNING;
                    case NEEDS_CONSENT -> WindowManager.PluginState.Kind.NEEDS_CONSENT;
                    case TURNED_OFF -> WindowManager.PluginState.Kind.TURNED_OFF;
                    case FAILED -> WindowManager.PluginState.Kind.FAILED;
                }))
                .orElseGet(() -> new WindowManager.PluginState(id, WindowManager.PluginState.Kind.NOT_INSTALLED));
        });
    }
""", 1)])
R = A + "plugins/PluginRuntime.java"
edit(R, [("""    /** Discovered plugin display names, without performing file I/O. */""",
          """    /**
     * How a discovered plugin stands at this launch (workspace tabs spec 13): its name, and whether it runs or why not.
     *
     * @param name its display name
     * @param kind whether it runs, or why not
     */
    public record Standing(String name, Kind kind) {
        /** Whether it runs, or why not; earlier is better, so a copy that runs wins over one that lost. */
        public enum Kind {
            /** It started at this launch. */
            RUNNING,
            /** It waits for the user's review of its capabilities. */
            NEEDS_CONSENT,
            /** The user turned it off. */
            TURNED_OFF,
            /** It was skipped or failed to load or start. */
            FAILED
        }

        /** Requires both values. */
        public Standing { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); }
    }

    /**
     * How the plugin with {@code id} stands at this launch. UI thread.
     *
     * @param id the plugin
     * @return its name and standing; empty when no plugin with that id was discovered
     */
    public Optional<Standing> standing(String id) {
        Standing found = null;
        for (PluginStatus status : statuses) {
            if (!status.id().equals(id)) continue;
            Standing.Kind kind = switch (status.state()) {
                case ACTIVE -> Standing.Kind.RUNNING;
                case NEEDS_CONSENT -> Standing.Kind.NEEDS_CONSENT;
                case DISABLED -> Standing.Kind.TURNED_OFF;
                case SKIPPED, FAILED -> Standing.Kind.FAILED;
            };
            if (found == null || kind.ordinal() < found.kind().ordinal()) found = new Standing(status.name(), kind);
        }
        return Optional.ofNullable(found);
    }

    /** Discovered plugin display names, without performing file I/O. */""", 1)])
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.JasperApplicationWorkspaceTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `PluginTabRestoreTest`'s eleven cases, `WorkspaceRestoreTest` with its renamed case, the new
`JasperApplicationWorkspaceTest` case, `PluginRuntimeTest` and the ledger's test; every other workspace test as
before.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: plugins' tabs come back, or a placeholder says why

A plugin's tab is saved while its plugin restores that kind and is
rebuilt by the plugin's restorer in its saved parent tab and pane, in
the main, a full or a pane window. A tab whose plugin does not run, or
whose restorer declines or fails, comes back as a placeholder that says
why, with Close and, while the plugin runs, Retry, in whatever window
holds it; it is saved again as it was. Jasper's own tabs are neither
saved nor restored, and a workspace too large to read back leaves out
plugins' tabs.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The Editor reopens its files

Spec 13: "the Editor reopens files (an unsaved untitled buffer is not kept)". A document's tab gives its document's key
(`document` restorer, Rulings 8); the restorer reopens it through the same reopeners Recent Files uses, local files
itself and other plugins' documents (Remote's `remote:`) through theirs. A document on another host waits until its tab
is first active or Load is pressed and then reads asking nothing; a key nothing opens declines with why (Rulings 5),
saying whether no running plugin handles its prefix or its file can no longer be opened (Rulings 31). A tab is active
while it is the selected tab of the active pane of its window's shown parent tab (fact 5), so a remote
document restored in a hidden parent tab reads once that parent tab shows it.

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `ui/EditorTab.java`,
  `session/DocumentSession.java`, `api/EditorDocument.java`, `recent/Reopeners.java`, `EditorPlugin.java`,
  `src/main/resources/plugin.toml` (0.11.0 to 0.12.0, Rulings 18)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`, `ui/EditorTabTest.java`

**Interfaces:**
- Consumes: Task 1's `ContentSpec.withRestore`, `ContentTab.setRestore`, `Windows.restorer`, `RestoreRequest`,
  `RestoreState.of`; the Editor's `Reopeners.resolve(String)`.
- Produces: `EditorWindow.RESTORE_KIND` (`"document"`) and `RESTORE_KEY` (`"key"`); `public boolean
  EditorWindow.openRestored(EditorDocument document, WindowOwner owner)`; `public void DocumentSession.defer()` and
  `boolean deferred()`; `public boolean Reopeners.handles(String key)`; Editor API `default CompletableFuture<Content>
  EditorDocument.readQuietly(long limitBytes)`.

- [ ] **Step 1: Write the failing test**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java", [(
    """    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */""",
    """    /**
     * Workspace tabs spec 13: a document's tab gives its file's key and comes back through the Editor's restorer; an
     * untitled one is not saved, and a key nothing can open now restores nothing.
     */
    @Test void aDocumentComesBackFromItsKeyAndAnUntitledOneIsNotSaved() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\\n");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(notes);
            drain(host);
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            var state = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreState(tab)).contains(state);
            tab.close();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            var back = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            assertThat(back.window().id()).isEqualTo(main);
            assertThat(host.restoreState(back)).contains(state);
            assertThat(host.invoke("dev.jasper.editor.new", main, null)).isTrue();
            var untitled = host.contentTabs().stream().filter(open -> host.contentPresentation(open).title().startsWith("Untitled"))
                .findFirst().orElseThrow();
            assertThat(host.restoreState(untitled)).as("an unsaved untitled buffer is not kept").isEmpty();
            assertThat(host.restoreTab("dev.jasper.editor", main, "gone.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "remote:gone"))).isEmpty();
            // A prefix a running plugin handles, whose reopener cannot open the key now: the file, not the plugin, is gone.
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.empty());
            assertThat(host.restoreTab("dev.jasper.editor", main, "kept.txt",
                dev.jasper.sdk.RestoreState.of("document", "key", "test:kept"))).isEmpty();
            assertThat(host.declinedRestores()).containsExactly(
                "dev.jasper.editor document: no running plugin opens remote: documents",
                "dev.jasper.editor document: its file can no longer be opened");
            assertThat(host.failures()).as("expected outcomes, no failure").isEmpty();
        }
    }

    /**
     * Workspace tabs spec 13: a restored document on another host waits until its tab is first active, then reads asking
     * nothing; a document already open restores no second tab.
     */
    @Test void aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var quiet = new java.util.concurrent.atomic.AtomicInteger();
            var loud = new java.util.concurrent.atomic.AtomicInteger();
            dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
                @Override public String key() { return "test:/srv/notes.md"; }
                @Override public String name() { return "notes.md"; }
                @Override public String location() { return "box:/srv/notes.md"; }
                @Override public Optional<String> host() { return Optional.of("box"); }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                    loud.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content(new byte[0], new dev.jasper.editor.api.Revision(1, 0)));
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> readQuietly(long limitBytes) {
                    quiet.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("Not signed in to box; Retry signs in"));
                }
                @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                    return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
                }
            };
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.of(owner -> remote));
            var state = dev.jasper.sdk.RestoreState.of("document", "key", remote.key());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).isPresent();
            drain(host);
            assertThat(quiet.get() + loud.get()).as("not read while its tab is not active").isZero();
            var tab = host.contentTab("dev.jasper.editor", "notes.md").orElseThrow();
            tab.select();
            drain(host);
            assertThat(quiet).as("read once it is active, asking nothing").hasValue(1);
            assertThat(loud).hasValue(0);
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).as("already open").isEmpty();
            assertThat(host.declinedRestores()).singleElement().asString().contains("the file is open in another tab");
        }
    }

    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */""", 1)])
edit("plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java", [(
    """    @Test void theBannerOffersReloadOrKeepMineAndCloseOrKeep() throws Exception {""",
    """    /** Workspace tabs spec 13: a restored remote document waits with Load, reads first asking nothing, then as usual on Retry. */
    @Test void aDeferredDocumentOffersLoadReadsQuietlyFirstAndAsUsualOnRetry() {
        var quiet = new java.util.concurrent.atomic.AtomicInteger();
        var loud = new java.util.concurrent.atomic.AtomicInteger();
        dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
            @Override public String key() { return "test:/srv/notes.md"; }
            @Override public String name() { return "notes.md"; }
            @Override public String location() { return "box:/srv/notes.md"; }
            @Override public Optional<String> host() { return Optional.of("box"); }
            @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                loud.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content("hi\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    new dev.jasper.editor.api.Revision(1, 3)));
            }
            @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> readQuietly(long limitBytes) {
                quiet.incrementAndGet();
                return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("Not signed in to box; Retry signs in"));
            }
            @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
            }
            @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
            }
        };
        var tab = new EditorTab(new dev.jasper.sdk.testing.FakeKit("dev.jasper.editor"), new DocumentSession(remote, environment), closed::add, false);
        tab.session().defer();
        assertThat(stateText(tab)).isEqualTo("notes.md on box loads when you open it");
        assertThat(button(tab, EditorTab.RETRY).label()).isEqualTo("Load");
        assertThat(button(tab, EditorTab.RETRY).state().visible()).isTrue();
        assertThat(quiet.get() + loud.get()).isZero();
        click(tab, EditorTab.RETRY);
        drain();
        assertThat(quiet).hasValue(1);
        assertThat(tab.session().state()).isEqualTo(DocumentSession.State.FAILED);
        assertThat(button(tab, EditorTab.RETRY).label()).isEqualTo("Retry");
        click(tab, EditorTab.RETRY);
        drain();
        assertThat(loud).as("Retry reads as usual, which may sign in").hasValue(1);
        assertThat(tab.session().state()).isEqualTo(DocumentSession.State.READY);
        tab.dispose();
    }

    @Test void theBannerOffersReloadOrKeepMineAndCloseOrKeep() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: FAIL: `cannot find symbol` for `readQuietly`, `defer()` and `declinedRestores` (the Editor's tests compile
against Task 1's testkit), then, once compiled, the document's tab gives no restore state.

- [ ] **Step 3: The document's restore state and the restorer**

A tab opened for a document carries its key, and every refresh of the tabs' headers passes it on again, so a document
saved under a new name, or an untitled one saved for the first time, comes back as that file; the restorer reopens the
key in the window being restored, placed and not selected, and declines a key nothing can open now or a file already
open. A document on another host waits ("loads when you open it", with Load) until its tab is first active, then reads
with `readQuietly`; Retry after that reads as usual:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
E = "plugins/editor/src/main/java/dev/jasper/editor/"
W = E + "ui/EditorWindow.java"
edit(W, [
    ("""public final class EditorWindow {
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");
""", """public final class EditorWindow {
    /** The restorer kind of a document tab, and the key its restore state names the document by (workspace tabs spec 13). */
    public static final String RESTORE_KIND = "document", RESTORE_KEY = "key";
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");
""", 1),
    ("""        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab.view(), placement);""",
     """        var spec = new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name());
        var handle = context.windows().openTab(restoreState(session).map(spec::withRestore).orElse(spec), origin, tab.view(), placement);""", 1),
    ("""            entry.getValue().setPresentation(new ContentPresentation(session.name(), model.detail(session),
                session.host().isPresent() ? serverIcon : fileIcon, session.dirty(), session.location(), session.host().orElse("")));
""", """            entry.getValue().setPresentation(new ContentPresentation(session.name(), model.detail(session),
                session.host().isPresent() ? serverIcon : fileIcon, session.dirty(), session.location(), session.host().orElse("")));
            // A document saved under a new name, or an untitled one saved for the first time, comes back as that file.
            entry.getValue().setRestore(restoreState(session));
""", 1),
    ("""    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */""",
     """    /**
     * Opens a document Jasper restores with its windows (workspace tabs spec 13): its tab is placed where it was saved, not
     * selected. A document on another host waits until its tab is first active, or Load is pressed, and then reads without
     * asking anything ({@code EditorDocument.readQuietly}); Retry then reads it as usual, which may sign in.
     *
     * @param document the saved document
     * @param owner the window being restored
     * @return false when the document is open already, and no tab opened
     */
    public boolean openRestored(EditorDocument document, WindowOwner owner) {
        if (model.find(document.key()).isPresent()) return false;
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var created = new DocumentSession(document, sessionEnvironment(ref));
        add(created, ref); reportWhenLoaded(created);
        if (document.host().isPresent()) created.defer(); else created.load();
        return true;
    }

    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */""", 1),
    ("""        handle.onActive(active -> { if (active) activate(tab); });""",
     """        // A restored document on another host reads when its tab is first active (workspace tabs spec 13).
        handle.onActive(active -> { if (active) { activate(tab); if (session.deferred()) session.load(); } });""", 1),
    ("""    private static Optional<Path> localFile(DocumentSession session) {""",
     """    /**
     * How a document's tab comes back when Jasper restores its windows (workspace tabs spec 13): by its document's key, as
     * Recent Files reopens it. An untitled document has none: an unsaved untitled buffer is not kept.
     */
    private static Optional<dev.jasper.sdk.RestoreState> restoreState(DocumentSession session) {
        return session.document().map(document -> dev.jasper.sdk.RestoreState.of(RESTORE_KIND, RESTORE_KEY, document.key()));
    }

    private static Optional<Path> localFile(DocumentSession session) {""", 1)])
edit(E + "EditorPlugin.java", [
    ("""        context.palette().register(scope);
        PluginMenu file = context.menus().standard(StandardMenu.FILE);""",
     """        context.palette().register(scope);
        // Workspace tabs spec 13: a saved document tab comes back from its key, as Recent Files reopens it, local or not.
        context.windows().restorer(EditorWindow.RESTORE_KIND, request -> {
            String key = request.state().values().getOrDefault(EditorWindow.RESTORE_KEY, "");
            var document = reopeners.resolve(key);
            // Which miss it is (workspace plan 6b Rulings 31): no running plugin for the key's prefix, or its file is gone.
            if (document.isEmpty()) return Optional.of(reopeners.handles(key) ? "its file can no longer be opened"
                : "no running plugin opens " + key.substring(0, key.indexOf(':') + 1) + " documents");
            return window().openRestored(document.get().apply(request.window()), request.window())
                ? Optional.empty() : Optional.of("the file is open in another tab");
        });
        PluginMenu file = context.menus().standard(StandardMenu.FILE);""", 1)])
edit(E + "recent/Reopeners.java", [(
    """    /**
     * @param listener runs when a registration is added or removed""",
    """    /**
     * @param key a recorded document key
     * @return whether the key's prefix is one the Editor ({@code local:}) or a running plugin handles, though it may not
     *     reopen the key now
     */
    public boolean handles(String key) {
        return key.startsWith(LOCAL) || byPrefix.keySet().stream().anyMatch(key::startsWith);
    }

    /**
     * @param listener runs when a registration is added or removed""", 1)])
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.11.0"', 'version = "0.12.0"', 1)])
# The Editor's API: a read that asks nothing, which a restored remote document uses first.
E2 = "plugins/editor/src/main/java/dev/jasper/editor/"
edit(E2 + "api/EditorDocument.java", [(
    """    CompletableFuture<Content> read(long limitBytes);
""", """    CompletableFuture<Content> read(long limitBytes);

    /**
     * Reads the whole file as {@link #read} does, without asking the user anything: a document that would have to (a sign-in
     * prompt) fails instead and says so. The Editor reads a document this way when Jasper has restored its tab and it is
     * first shown; Retry then reads it with {@link #read}. Reads as {@link #read} does unless the document asks.
     *
     * @param limitBytes the largest size the editor accepts
     * @return the content and the revision it was read at
     */
    default CompletableFuture<Content> readQuietly(long limitBytes) { return read(limitBytes); }
""", 1)])
edit(E2 + "session/DocumentSession.java", [
    ("""    private CompletableFuture<Content> loading;
""", """    private CompletableFuture<Content> loading;
    /** Restored and not read yet: it reads when its tab is first active, or Load is pressed (workspace tabs spec 13). */
    private boolean deferred;
    /** The next read asks nothing ({@code EditorDocument.readQuietly}): a restored document's first. */
    private boolean quiet;
""", 1),
    ("""    /** Reads the document for the first time, or again after a failure. */
    public void load() {
        if (document == null) return;
        state = State.LOADING;""", """    /**
     * Waits to read the document: Jasper restored its tab, and it reads when its tab is first active or Load is pressed,
     * that first read asking nothing (workspace tabs spec 13).
     */
    public void defer() {
        if (document == null || state != State.LOADING || loading != null) return;
        deferred = true;
        quiet = true;
        changed();
    }

    /** @return whether the document waits to be read: see {@link #defer()} */
    public boolean deferred() { return deferred; }

    /** Reads the document for the first time, or again after a failure. */
    public void load() {
        if (document == null) return;
        deferred = false;
        state = State.LOADING;""", 1),
    ("""        CompletableFuture<Content> read = document.read(environment.limitBytes().getAsLong());""",
     """        CompletableFuture<Content> read = quiet && !again ? document.readQuietly(environment.limitBytes().getAsLong())
            : document.read(environment.limitBytes().getAsLong());
        quiet = false;""", 1)])
edit(E2 + "ui/EditorTab.java", [
    ("""            case RETRY -> { if (session.state() == DocumentSession.State.FAILED && session.retryable()) session.load(); }""",
     """            case RETRY -> { if (session.deferred() || session.state() == DocumentSession.State.FAILED && session.retryable()) session.load(); }""", 1),
    ("""        boolean ready = showingText(), failed = session.state() == DocumentSession.State.FAILED;""",
     """        boolean ready = showingText(), failed = session.state() == DocumentSession.State.FAILED, waiting = session.deferred();""", 1),
    ("""            failed ? FAILED_TITLE : "Loading " + session.name() + session.host().map(host -> " from " + host).orElse("") + "…", failed ? session.failure() : "",
            List.of(button(DISMISS, failed ? "Close" : "Cancel", true),
                new Nodes.Button(RETRY, visible(failed && session.retryable()), "Retry", "", Nodes.ButtonKind.PRIMARY, RETRY, ""))));""",
     """            failed ? FAILED_TITLE : waiting ? session.name() + session.host().map(host -> " on " + host).orElse("") + " loads when you open it"
                : "Loading " + session.name() + session.host().map(host -> " from " + host).orElse("") + "…", failed ? session.failure() : "",
            List.of(button(DISMISS, failed || waiting ? "Close" : "Cancel", true),
                new Nodes.Button(RETRY, visible(waiting || failed && session.retryable()), waiting ? "Load" : "Retry", "", Nodes.ButtonKind.PRIMARY, RETRY, ""))));""", 1)])
PY
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor
git commit -F - <<'EOF'
Workspace tabs: the Editor reopens its files

A document's tab is saved with its document's key, and the Editor's
restorer reopens it as Recent Files does; a document on another host
waits until its tab is first active and then reads asking nothing. An
untitled document that was never saved is not kept. Editor 0.12.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Remote's SSH tabs come back

Spec 13: "Remote chooses between reconnecting and a Reconnect state". An SSH tab's session carries its host, the host's
name and its folder (`ssh` restorer); the restored tab waits until it is first shown, as every restored terminal tab
does, and then Remote reconnects when signing in asks nothing, after any connection in progress in its window, and
otherwise leaves it not connected with Reconnect; a missing host declines (Rulings 9). An SSH tab restored in a hidden
parent tab, or behind another tab of its pane, waits until it shows there (plan 6a Rulings 4). The pane says Reconnect
rather than Retry for a restored session (Rulings 13). Remote's remote documents reopen before its hosts have loaded and
read quietly only when signing in asks nothing (Rulings 17). Both are quiet (Rulings 29): while a restored tab's first
connect, or a restored document's quiet read, runs in a window, a host-key question there is answered Cancel, as a host
accepted "Once" is unknown again at the next start, and a quiet connect that fails closes its overlay and leaves the tab
Not connected with Reconnect; Reconnect and Retry ask as usual. A restored tab cancelled while it waits for its window's
connection passes its turn on.

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/SavedRemoteDocument.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, `edit/RemoteEditing.java`,
  `edit/RemoteDocument.java`, `edit/EditChannels.java`, `edit/package-info.java`, `src/main/resources/plugin.toml` (0.15.0
  to 0.16.0, Rulings 18); `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`; `AGENTS.md` (Remote's
  Editor API names)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`, `edit/RemoteEditingTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/SessionPaneTest.java`

**Interfaces:**
- Consumes: Task 1's `SessionSpec.withRestore`, `Windows.restorer`, `RestoreRequest`, `RestoreState`; plan 6a's
  `TerminalPane.restored()`.
- Produces: `RemotePlugin.SSH_TAB` (`"ssh"`); package `static RestoreState RemotePlugin.sshTab(UUID hostId, String name,
  Optional<String> folder)` and `Optional<String> restoreHost(RestoreRequest)`; `public RemoteEditing(Editor,
  EditChannels, Supplier<Optional<WindowHandle>>, NotificationGroup, Function<UUID, Optional<RemoteHost>>,
  Supplier<CompletableFuture<Void>> hostsLoaded, Executor ui, Predicate<ConnectionIdentity> silent)`; `public
  RemoteDocument(ConnectionIdentity, String, EditChannels, Supplier<Optional<WindowHandle>>, Predicate<ConnectionIdentity>
  silent)` and its `readQuietly`; package `final class SavedRemoteDocument implements EditorDocument`; `public
  EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
  BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> quietOpener,
  Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui)` beside the four-argument one, and
  `public <T> CompletableFuture<T> EditChannels.submitQuietly(ConnectionIdentity, Supplier<Optional<WindowHandle>>,
  Work<T>)`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java", [(
    """    @Test void renamesDefaultGroupThroughThePanel(@TempDir Path dir) throws Exception {""",
    """    /**
     * Workspace tabs spec 13: an SSH tab gives its host and comes back through Remote's restorer, waiting until it is
     * shown; then not connected while signing in would ask (the Vault is locked), and connected when nothing has to be
     * asked. A state naming no host declines, which is no failure.
     */
    @Test void anSshTabComesBackAndConnectsOnlyWhenSigningInAsksNothing(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved)); settle(host);
            UUID pane = host.terminalPanes().getFirst();
            UUID tab = Kits.value(() -> context.terminals().pane(pane).orElseThrow().tab().id());
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            assertThat(Kits.value(() -> host.restoreState(tab))).contains(state);

            vault.state = dev.jasper.vault.api.LockState.LOCKED;
            Optional<UUID> locked = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state));
            assertThat(locked).isPresent();
            settle(host);
            UUID waiting = Kits.value(() -> context.terminals().tab(locked.get()).orElseThrow().terminal().orElseThrow().id());
            assertThat(host.sessionState(waiting)).as("restored, it waits to be shown").isEqualTo("NOT_STARTED|");
            Kits.edt(() -> host.focusTerminalPane(waiting));
            Kits.await(host, () -> host.sessionState(waiting).startsWith("EXITED|"), "the restored tab to decide");
            assertThat(host.sessionState(waiting)).as("signing in would ask: not connected, with Reconnect").startsWith("EXITED|Not connected");
            assertThat(host.windows()).as("nothing asks at restore").isEmpty();
            vault.state = dev.jasper.vault.api.LockState.UNLOCKED;
            Kits.edt(() -> host.reconnectSession(waiting)); settle(host);
            assertThat(host.sessionState(waiting)).isEqualTo("RUNNING|");

            Optional<UUID> unlocked = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state));
            settle(host);
            UUID connected = Kits.value(() -> context.terminals().tab(unlocked.orElseThrow()).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(connected));
            Kits.await(host, () -> host.sessionState(connected).equals("RUNNING|"), "the restored tab to connect");
            assertThat(Kits.value(() -> host.restoreState(unlocked.get()))).contains(state);

            assertThat(Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "gone",
                new dev.jasper.sdk.RestoreState("ssh", Map.of("host", "not-a-host"))))).isEmpty();
            assertThat(Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "deleted",
                new dev.jasper.sdk.RestoreState("ssh", Map.of("host", UUID.randomUUID().toString()))))).as("hosts loaded: a gone host").isEmpty();
            assertThat(host.declinedRestores()).hasSize(2).allSatisfy(line -> assertThat(line).contains("it names no saved host"));
            assertThat(host.failures()).as("expected outcomes, no failures").isEmpty();
        }
    }

    /** Workspace tabs spec 13: restored SSH tabs shown together in one window connect one after another, none refused. */
    @Test void restoredSshTabsShownTogetherInOneWindowAllConnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID first = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID second = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID one = Kits.value(() -> context.terminals().tab(first).orElseThrow().terminal().orElseThrow().id());
            UUID two = Kits.value(() -> context.terminals().tab(second).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> { host.focusTerminalPane(one); host.focusTerminalPane(two); });
            Kits.await(host, () -> host.sessionState(one).equals("RUNNING|") && host.sessionState(two).equals("RUNNING|"),
                "both restored tabs to connect");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b Rulings 29: a restored SSH tab whose host key is not trusted (a host accepted Once is unknown again
     * at the next start) connects quietly, asks nothing and offers Reconnect, which asks as usual.
     */
    @Test void aRestoredSshTabWhoseHostKeyIsUnknownAsksNothingAndOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane));
            Kits.await(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the quiet connect to end");
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(host.windows()).as("no host-key question asked, the overlay closed").isEmpty();
            Kits.edt(() -> host.reconnectSession(pane));
            Kits.await(host, () -> host.windows().stream().anyMatch(line -> line.startsWith("dialog|Verify host key")),
                "Reconnect to ask as usual");
            Kits.edt(() -> host.requestClose("dialog"));
            settle(host);
            Kits.edt(() -> cancel(plugin.currentConnectionPanel()));
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a restored SSH tab closed while it waits for its window's connection passes its turn on. */
    @Test void aRestoredSshTabClosedWhileItWaitsPassesItsTurnOn(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            // A connection in progress in the window, its connect held on the host's background executor until it settles.
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved));
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID first = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID second = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID one = Kits.value(() -> context.terminals().tab(first).orElseThrow().terminal().orElseThrow().id());
            UUID two = Kits.value(() -> context.terminals().tab(second).orElseThrow().terminal().orElseThrow().id());
            // Both shown while the window's connection is in progress: both wait for it, the first one first.
            Kits.edt(() -> { host.focusTerminalPane(one); host.focusTerminalPane(two); });
            Kits.edt(() -> host.closeTerminalPane(one));
            Kits.edt(() -> cancel(plugin.currentConnectionPanel()));
            Kits.await(host, () -> host.sessionState(two).equals("RUNNING|"), "the second waiting tab to connect");
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void renamesDefaultGroupThroughThePanel(@TempDir Path dir) throws Exception {""", 1)])
PY
```

A recorded key reopens before Remote's hosts have loaded, and a quiet read reads only when signing in asks nothing:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java", [(
    """    @Test void registeringHandsTheEditorTheRemotePrefixUntilClosed() {""",
    """    /**
     * Workspace tabs spec 13: Jasper restores its windows before Remote has read its saved hosts; a recorded key still
     * reopens, and the document finds its hosts when it is first read. A host gone once they are read fails that read.
     */
    @Test void aKeyReopensBeforeTheSavedHostsHaveLoadedAndFindsThemWhenRead() throws Exception {
        var loaded = new CompletableFuture<Void>();
        var early = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> loaded, ui, route -> true);
        String key = RemoteDocument.key(prod, "/etc/hosts");
        EditorDocument document = early.reopen(key).orElseThrow().apply(origin);
        assertThat(document.key()).isEqualTo(key);
        assertThat(document.name()).isEqualTo("hosts");
        var read = onUi(() -> document.read(1 << 20));
        assertThat(read).as("waits for the saved hosts").isNotDone();
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        loaded.complete(null);
        assertThat(read.get(10, TimeUnit.SECONDS).bytes()).isEqualTo(Files.readAllBytes(Path.of("/etc/hosts")));
        assertThat(onUi(document::host)).contains("prod");
        var gone = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(null), ui, route -> true);
        assertThat(gone.reopen(key)).as("the saved hosts are read: a host that is gone cannot reopen").isEmpty();
        var pending = new CompletableFuture<Void>();
        var later = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.empty(), () -> pending, ui, route -> true);
        EditorDocument orphan = later.reopen(key).orElseThrow().apply(origin);
        var failed = onUi(() -> orphan.read(1 << 20));
        pending.complete(null);
        assertThatThrownBy(() -> failed.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Its host is no longer saved in Remote");
    }

    /** Workspace tabs spec 13: a quiet read asks nothing: it reads when signing in would not, and otherwise fails saying so. */
    @Test void aQuietReadSignsInOnlyWhenNothingWouldBeAsked() throws Exception {
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        connected = false;
        var asking = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> CompletableFuture.completedFuture(null), ui, route -> false);
        EditorDocument document = asking.reopen(RemoteDocument.key(prod, "/etc/hosts")).orElseThrow().apply(origin);
        var quiet = onUi(() -> document.readQuietly(1 << 20));
        assertThatThrownBy(() -> quiet.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Not signed in to prod; Retry signs in");
        assertThat(owners).as("no channel opened, nothing asked").isEmpty();
        var silent = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> CompletableFuture.completedFuture(null), ui, route -> true);
        EditorDocument signsIn = silent.reopen(RemoteDocument.key(prod, "/etc/hosts")).orElseThrow().apply(origin);
        assertThat(onUi(() -> signsIn.readQuietly(1 << 20)).get(10, TimeUnit.SECONDS).bytes()).isNotEmpty();
    }

    @Test void registeringHandsTheEditorTheRemotePrefixUntilClosed() {""", 1)])
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/SessionPaneTest.java", [(
    """    @Test void aFailedTransportSaysWhyAndClosePaneOnExitClosesIt() throws Exception {""",
    """    /** Workspace tabs plan 6b: a restored session its plugin leaves not connected offers Reconnect, not Retry. */
    @Test void aRestoredSessionLeftNotConnectedOffersReconnect() throws Exception {
        edt(() -> {
            var request = new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run);
            pane = new TerminalPane(HOME, launcher(new ArrayDeque<>()), request);
            pane.restored();
            pane.start();
        });
        attempts.get(0).fail("Not connected: Reconnect to sign in");
        until(() -> pane.noticeText().equals("Not connected: Reconnect to sign in"));
        edt(() -> {
            assertThat(pane.noticePrimary().getText()).isEqualTo("Reconnect");
            pane.close();
        });
    }

    @Test void aFailedTransportSaysWhyAndClosePaneOnExitClosesIt() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemotePluginTest' --tests 'dev.jasper.remote.edit.RemoteEditingTest' :jasper-app:test --tests 'dev.jasper.app.workspace.SessionPaneTest' --continue`
Expected: FAIL: `cannot find symbol` for the eight-argument `RemoteEditing`, then the SSH tab gives no restore state,
the restored pane offers Retry, and a restored tab with an unknown host key asks about it.

- [ ] **Step 3: The SSH tab's restore state, the restorer and Reconnect**

The restorer declines a missing host, waits for the tab to show, and connects one restored tab at a time per window;
Remote's editing gets the hosts' loading, the UI thread and the silent sign-in test:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ("""    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", PANEL = "dev.jasper.remote.panel";
""", """    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", PANEL = "dev.jasper.remote.panel";
    /** The restorer kind of an SSH tab (workspace tabs spec 13). */
    public static final String SSH_TAB = "ssh";
""", 1),
    ("    private boolean stopped;\n",
     "    private boolean stopped;\n"
     "    /** Completes once the saved hosts have been read at start; a restored SSH tab decides how to connect after it. */\n"
     "    private CompletableFuture<Void> hostsLoaded = CompletableFuture.completedFuture(null);\n"
     "    /** Restored SSH tabs waiting, per window, for the connection in progress there (workspace tabs spec 13). */\n"
     "    private final Map<UUID, java.util.ArrayDeque<Runnable>> waitingConnects = new HashMap<>();\n"
     "    /** Windows where a restored document's quiet read opens its channel, how many: their host-key questions answer Cancel. */\n"
     "    private final Map<UUID, Integer> quietWindows = new HashMap<>();\n", 1),
    ("""        context.palette().register(scope);
""", """        context.palette().register(scope);
        // Workspace tabs spec 13: an SSH tab saved with the workspace comes back to its host and folder.
        context.windows().restorer(SSH_TAB, this::restoreHost);
""", 1),
    ("        store.load();\n", "        hostsLoaded = store.reload();\n", 1),
    ("""                if (first == null) connect(pending, host.id(), folder);
                else ui.execute(() -> attach(pending, first));
            });""", """                if (first == null) connect(pending, host.id(), folder);
                else ui.execute(() -> attach(pending, first));
            }).withRestore(sshTab(host.id(), host.name(), folder));""", 1),
    ("""    void activateHost(WindowHandle window, RemoteHost host) {""",
     """    /** How an SSH tab comes back (workspace tabs spec 13): its saved host, that host's name for its title, and its folder. */
    static dev.jasper.sdk.RestoreState sshTab(UUID hostId, String name, Optional<String> folder) {
        var values = new java.util.LinkedHashMap<String, String>();
        values.put("host", hostId.toString());
        values.put("name", name);
        folder.ifPresent(path -> values.put("folder", path));
        return new dev.jasper.sdk.RestoreState(SSH_TAB, values);
    }

    /**
     * Opens a saved SSH tab again (workspace tabs spec 13), waiting, as every restored terminal tab does, until it is first
     * shown. Then Remote chooses: it reconnects when every host on the route signs in with nothing to ask (through the SSH
     * agent, or the Vault while it is unlocked), after any connection already in progress in that window, and otherwise
     * leaves the tab not connected with Reconnect, so restoring windows never asks for a password before the user does. A
     * state that names no host, or a host gone from the saved hosts once they have loaded, declines.
     *
     * @return empty once the tab is open; else why it does not come back
     */
    Optional<String> restoreHost(dev.jasper.sdk.ui.RestoreRequest request) {
        Map<String, String> saved = request.state().values();
        UUID hostId;
        try { hostId = UUID.fromString(saved.getOrDefault("host", "")); }
        catch (IllegalArgumentException malformed) { return Optional.of("it names no saved host"); }
        // The saved hosts are read in the background at start: once they are, a host that is gone restores nothing.
        if (hostsLoaded.isDone() && store.host(hostId).isEmpty()) return Optional.of("it names no saved host");
        Optional<String> folder = Optional.ofNullable(saved.get("folder"));
        String name = saved.getOrDefault("name", "").isBlank() ? request.title() : saved.get("name");
        boolean[] first = {true};
        SessionSpec spec = new SessionSpec(name.isBlank() ? "SSH" : name, Optional.of(serverIcon), dev.jasper.sdk.terminal.ExitPolicy.KEEP_OPEN, pending -> {
            if (!first[0]) { connect(pending, hostId, folder); return; }
            first[0] = false;
            // The saved hosts are read in the background at start: the choice waits for them.
            hostsLoaded.whenComplete((loaded, failure) -> ui.execute(() -> {
                if (stopped || pending.isCancelled()) return;
                if (store.host(hostId).isEmpty()) pending.fail("Its host is no longer saved in Remote");
                else if (signsInAlone(hostId)) connectWhenFree(pending, hostId, folder);
                else pending.fail("Not connected: Reconnect to sign in");
            }));
        }).withRestore(sshTab(hostId, name, folder));
        context.terminals().openTab(request.window(), OpenRequest.session(spec));
        return Optional.empty();
    }

    /**
     * Connects a restored SSH tab once no connection is in progress in its window, which shows one at a time: several
     * restored tabs shown together connect one after another (workspace tabs spec 13).
     */
    private void connectWhenFree(PendingSession pending, UUID hostId, Optional<String> folder) {
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (attempts.stream().noneMatch(attempt -> attempt.window.id().equals(window.id()))) { connect(pending, hostId, folder, true); return; }
        waitingConnects.computeIfAbsent(window.id(), id -> new java.util.ArrayDeque<>()).add(() -> connectWhenFree(pending, hostId, folder));
    }

    /** The connection in progress in {@code window} ended: the next restored SSH tab still waiting there connects. */
    private void nextWaitingConnect(UUID window) {
        var waiting = waitingConnects.get(window);
        // The next one that still waits connects; one cancelled meanwhile, or failing before it starts, passes its turn on.
        while (waiting != null && !waiting.isEmpty() && attempts.stream().noneMatch(attempt -> attempt.window.id().equals(window)))
            waiting.poll().run();
        if (waiting != null && waiting.isEmpty()) waitingConnects.remove(window);
    }

    /**
     * {@code open}, a restored document's quiet channel, with {@code owner}'s host-key questions answered Cancel until it
     * completes (workspace plan 6b Rulings 29).
     */
    private <T> CompletableFuture<T> quietly(Optional<WindowHandle> owner, java.util.function.Supplier<CompletableFuture<T>> open) {
        owner.ifPresent(window -> quietWindows.merge(window.id(), 1, Integer::sum));
        CompletableFuture<T> opened;
        try { opened = open.get(); }
        catch (RuntimeException failure) { opened = CompletableFuture.failedFuture(failure); }
        opened.whenComplete((value, failure) -> ui.execute(() -> owner.ifPresent(window ->
            quietWindows.computeIfPresent(window.id(), (id, count) -> count == 1 ? null : count - 1))));
        return opened;
    }

    /** Whether a host-key question for {@code owner} is answered Cancel now: a quiet connect runs there (Rulings 29). */
    private boolean quiet(WindowHandle owner) {
        return owner != null && (quietWindows.containsKey(owner.id())
            || attempts.stream().anyMatch(attempt -> attempt.quiet && attempt.window.id().equals(owner.id())));
    }

    /** Whether every host on {@code hostId}'s route signs in without asking: through the SSH agent, or the unlocked Vault. */
    private boolean signsInAlone(UUID hostId) {
        var seen = new java.util.HashSet<UUID>();
        Optional<RemoteHost> current = store.host(hostId);
        if (current.isEmpty()) return false;
        while (current.isPresent()) {
            RemoteHost hop = current.get();
            if (!seen.add(hop.id()) || !signsInAlone(hop)) return false;
            current = hop.jump().flatMap(store::host);
        }
        return true;
    }

    /** Whether {@code host} signs in without asking: through the SSH agent, or the Vault while it is unlocked. */
    private boolean signsInAlone(RemoteHost host) {
        return host.auth() instanceof Auth.Agent || vault.isPresent() && vault.get().lockState() == LockState.UNLOCKED;
    }

    /** Whether signing in to every host of {@code identity}'s route asks nothing: an open connection, or each signs in alone. */
    private boolean signsInAlone(dev.jasper.remote.client.ConnectionIdentity identity) {
        return connections.authenticated(identity) || identity.hops().stream().allMatch(hop -> signsInAlone(hop.host()));
    }

    void activateHost(WindowHandle window, RemoteHost host) {""", 1)])
edit(R, [
    ("""            dialog.close();
            if (currentConnectionPanel == panel) currentConnectionPanel = null;
        }""", """            dialog.close();
            if (currentConnectionPanel == panel) currentConnectionPanel = null;
            // A restored SSH tab waiting for this window connects next (workspace tabs spec 13).
            ui.execute(() -> nextWaitingConnect(window.id()));
        }""", 1),
    # A quiet connect, a restored tab's first (workspace plan 6b Rulings 29).
    ("""    private void connect(PendingSession pending, UUID hostId, Optional<String> folder) {
        var host = store.host(hostId);""", """    private void connect(PendingSession pending, UUID hostId, Optional<String> folder) { connect(pending, hostId, folder, false); }

    /**
     * As {@link #connect(PendingSession, UUID, Optional)}; a {@code quiet} connect, a restored SSH tab's first, asks
     * nothing: a host-key question is answered Cancel, and a failure closes the overlay and leaves the tab not connected,
     * with Reconnect, which asks as usual (workspace plan 6b Rulings 29).
     */
    private void connect(PendingSession pending, UUID hostId, Optional<String> folder, boolean quiet) {
        var host = store.host(hostId);""", 1),
    ("""        attempt.cancelled = () -> { if (!stopped) pending.fail("Cancelled"); };""",
     """        attempt.quiet = quiet;
        attempt.cancelled = () -> { if (!stopped) pending.fail(quiet ? "Not connected: Reconnect to sign in" : "Cancelled"); };""", 1),
    ("""        boolean closed, delivered;
        Runnable cancelled = () -> {}, finished = () -> {};""", """        boolean closed, delivered;
        /** A restored tab's first connect: it asks nothing, and a failure leaves the tab not connected (Rulings 29). */
        boolean quiet;
        Runnable cancelled = () -> {}, finished = () -> {};""", 1),
    ("""                if (failure != null) { panel.failed(message(failure)); return; }""",
     """                // A quiet connect that fails asks nothing more: its overlay closes and the tab offers Reconnect (Rulings 29).
                if (failure != null && quiet) { close(); return; }
                if (failure != null) { panel.failed(message(failure)); return; }""", 1),
    ("""            if (decision.isDone() || stopped) { decision.cancel(true); return; }
            questions.add(decision);""", """            if (decision.isDone() || stopped) { decision.cancel(true); return; }
            // A quiet connect there, a restored tab's, asks nothing: it fails, and Reconnect or Retry asks (Rulings 29).
            if (quiet(owner)) { decision.complete(HostKeyVerifier.Decision.CANCEL); return; }
            questions.add(decision);""", 1),
    ("""                editChannels=new dev.jasper.remote.edit.EditChannels((identity,owner)->endpoints.open(Optional.of(identity),owner.orElse(null),status->{}),connections::authenticated,transferBackground,ui);""",
     """                // A restored document's quiet read opens its channel asking nothing (workspace plan 6b Rulings 29).
                editChannels=new dev.jasper.remote.edit.EditChannels((identity,owner)->endpoints.open(Optional.of(identity),owner.orElse(null),status->{}),
                    (identity,owner)->quietly(owner,()->endpoints.open(Optional.of(identity),owner.orElse(null),status->{})),connections::authenticated,transferBackground,ui);""", 1),
    ("""                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst());""",
     """                // Jasper restores its windows while the saved hosts still load: a remote document finds them when first read
                // (workspace tabs spec 13), and its first read after a restore asks nothing unless signing in would not.
                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst(),()->hostsLoaded,ui,this::signsInAlone);""", 1)])
edit("plugins/remote/src/main/resources/plugin.toml", [('version = "0.15.0"', 'version = "0.16.0"', 1)])
PY
```

Create `plugins/remote/src/main/java/dev/jasper/remote/edit/SavedRemoteDocument.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.remote.client.ConnectionIdentity;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A remote document reopened by its key before Remote has read its saved hosts (workspace tabs spec 13): Jasper restores
 * its windows while they load in the background. It finds its route's saved hosts when it is first read, checked or
 * written, and is that {@link RemoteDocument} from then on; a host that is no longer saved fails those operations. The
 * Editor calls it on the UI thread.
 */
final class SavedRemoteDocument implements EditorDocument {
    private final String key;
    private final RemoteDocument.ParsedKey parsed;
    private final Function<RemoteDocument.ParsedKey, Optional<ConnectionIdentity>> identity;
    private final Function<ConnectionIdentity, RemoteDocument> document;
    private final Supplier<CompletableFuture<Void>> hostsLoaded;
    private final Executor ui;
    private RemoteDocument resolved;

    /**
     * @param key the recorded key
     * @param parsed the key parsed
     * @param identity the key's route with the saved hosts as they are now; empty while a host is not known
     * @param document the document on that route
     * @param hostsLoaded completes once the saved hosts have been read
     * @param ui the UI thread
     */
    SavedRemoteDocument(String key, RemoteDocument.ParsedKey parsed, Function<RemoteDocument.ParsedKey, Optional<ConnectionIdentity>> identity,
                        Function<ConnectionIdentity, RemoteDocument> document, Supplier<CompletableFuture<Void>> hostsLoaded, Executor ui) {
        this.key = Objects.requireNonNull(key); this.parsed = Objects.requireNonNull(parsed);
        this.identity = Objects.requireNonNull(identity); this.document = Objects.requireNonNull(document);
        this.hostsLoaded = Objects.requireNonNull(hostsLoaded); this.ui = Objects.requireNonNull(ui);
    }

    @Override public String key() { return key; }
    @Override public String name() { return parsed.path().equals("/") ? parsed.path() : parsed.path().substring(parsed.path().lastIndexOf('/') + 1); }
    @Override public String location() { return now().map(RemoteDocument::location).orElse(parsed.path()); }
    /** The saved host's name once it is known; until then a remote document whose host is not known yet. */
    @Override public Optional<String> host() { return now().map(RemoteDocument::host).orElse(Optional.of("a saved host")); }

    @Override public CompletableFuture<Content> read(long limitBytes) { return use(found -> found.read(limitBytes)); }
    @Override public CompletableFuture<Content> readQuietly(long limitBytes) { return use(found -> found.readQuietly(limitBytes)); }
    @Override public CompletableFuture<Optional<Revision>> revision() { return use(RemoteDocument::revision); }
    @Override public CompletableFuture<Revision> write(byte[] content) { return use(found -> found.write(content)); }

    /** {@code operation} on the document once its hosts are known: at once when they are, else after they load. */
    private <T> CompletableFuture<T> use(Function<RemoteDocument, CompletableFuture<T>> operation) {
        Optional<RemoteDocument> found = now();
        if (found.isPresent()) return operation.apply(found.get());
        return hostsLoaded.get().handle((done, failure) -> (Void) null).thenComposeAsync(ignored -> now().map(operation)
            .orElseGet(() -> CompletableFuture.failedFuture(new IOException("Its host is no longer saved in Remote"))), ui);
    }

    /** The document, once every host on its route is known. */
    private Optional<RemoteDocument> now() {
        if (resolved == null) identity.apply(parsed).map(document).ifPresent(found -> resolved = found);
        return Optional.ofNullable(resolved);
    }
}
```

`RemoteEditing` reopens a key before the hosts have loaded, and `RemoteDocument` reads quietly:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
E = "plugins/remote/src/main/java/dev/jasper/remote/edit/"
# A remote document reads quietly when signing in asks nothing (workspace tabs spec 13).
edit(E + "RemoteDocument.java", [
    ("import java.util.concurrent.CompletableFuture;\nimport java.util.function.Supplier;\n",
     "import java.util.concurrent.CompletableFuture;\nimport java.util.function.Predicate;\nimport java.util.function.Supplier;\n", 1),
    ("""    private final Supplier<Optional<WindowHandle>> owner;
""", """    private final Supplier<Optional<WindowHandle>> owner;
    private final Predicate<ConnectionIdentity> silent;
""", 1),
    ("""    public RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner) {
        this.identity = Objects.requireNonNull(identity);""",
     """    public RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner) {
        this(identity, path, channels, owner, route -> false);
    }

    /**
     * @param identity the connection the file was browsed on
     * @param path the absolute path as browsed
     * @param channels where its operations queue
     * @param owner the window for sign-in prompts when reconnecting
     * @param silent whether signing in to a route asks nothing (an open connection, the SSH agent, the unlocked Vault):
     *     only then does {@link #readQuietly} read
     */
    public RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner,
                          Predicate<ConnectionIdentity> silent) {
        this.silent = Objects.requireNonNull(silent);
        this.identity = Objects.requireNonNull(identity);""", 1),
    ("""    @Override public CompletableFuture<Optional<Revision>> revision() {""",
     """    /**
     * Reads as {@link #read} does when signing in to the route asks nothing, on a channel opened quietly, which answers a
     * host-key question Cancel; otherwise fails at once and says so, so a restored tab never asks before the user does
     * (workspace tabs spec 13; workspace plan 6b Rulings 29). Retry then reads with {@link #read}.
     */
    @Override public CompletableFuture<Content> readQuietly(long limitBytes) {
        if (silent.test(identity)) return channels.submitQuietly(identity, owner, endpoint -> read(endpoint, path, limitBytes));
        return CompletableFuture.failedFuture(new IOException("Not signed in to " + identity.host().name() + "; Retry signs in"));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {""", 1)])
R = E + "RemoteEditing.java"
edit(R, [
    ("import java.util.concurrent.CompletableFuture;\nimport java.util.function.Function;\nimport java.util.function.Supplier;\n",
     "import java.util.concurrent.CompletableFuture;\nimport java.util.concurrent.Executor;\nimport java.util.function.Function;\n"
     "import java.util.function.Predicate;\nimport java.util.function.Supplier;\n", 1),
    ("""    private final Function<UUID, Optional<RemoteHost>> hosts;
""", """    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Supplier<CompletableFuture<Void>> hostsLoaded;
    private final Executor ui;
    private final Predicate<ConnectionIdentity> silent;
""", 1),
    ("""    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications,
                         Function<UUID, Optional<RemoteHost>> hosts) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notifications = Objects.requireNonNull(notifications);
        this.hosts = Objects.requireNonNull(hosts);
    }""", """    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications,
                         Function<UUID, Optional<RemoteHost>> hosts) {
        this(editor, channels, activeWindow, notifications, hosts, () -> CompletableFuture.completedFuture(null), Runnable::run, route -> false);
    }

    /**
     * @param editor the Editor's service
     * @param channels where documents queue their operations
     * @param activeWindow the Jasper window used last, for sign-in prompts once the origin window has closed
     * @param notifications where a file the Editor cannot open is posted
     * @param hosts finds a saved host by id, in memory, for reopening recent files
     * @param hostsLoaded completes once the saved hosts have been read at start: before that a recorded key reopens a
     *     document that finds them when it is first used (workspace tabs spec 13)
     * @param ui the UI thread
     * @param silent whether signing in to a route asks nothing, so a restored document's first read may sign in
     */
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications,
                         Function<UUID, Optional<RemoteHost>> hosts, Supplier<CompletableFuture<Void>> hostsLoaded, Executor ui,
                         Predicate<ConnectionIdentity> silent) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notifications = Objects.requireNonNull(notifications);
        this.hosts = Objects.requireNonNull(hosts); this.hostsLoaded = Objects.requireNonNull(hostsLoaded);
        this.ui = Objects.requireNonNull(ui); this.silent = Objects.requireNonNull(silent);
    }""", 1),
    ("""    /**
     * @param key a recorded {@link RemoteDocument#key}
     * @return empty when the key is malformed or a host on its route is no longer saved; otherwise builds the document on
     *     the recorded route and logins, with the saved hosts as they are now, for the window reopening it
     */
    Optional<Function<WindowOwner, EditorDocument>> reopen(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        Optional<ConnectionIdentity> identity = parsed.flatMap(this::identity);
        if (identity.isEmpty()) return Optional.empty();
        String path = parsed.get().path();
        return Optional.of(owner -> new RemoteDocument(identity.get(), path, channels, owner instanceof WindowHandle window ? owner(window) : activeWindow));
    }""", """    /**
     * @param key a recorded {@link RemoteDocument#key}
     * @return empty when the key is malformed, or a host on its route is no longer saved once the saved hosts have loaded;
     *     otherwise builds the document on the recorded route and logins, with the saved hosts as they are now, for the
     *     window reopening it. Before the saved hosts have loaded (Jasper restoring its windows at start), the document
     *     finds them when it is first used.
     */
    Optional<Function<WindowOwner, EditorDocument>> reopen(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        String path = parsed.get().path();
        Optional<ConnectionIdentity> identity = identity(parsed.get());
        if (identity.isPresent()) return Optional.of(owner -> new RemoteDocument(identity.get(), path, channels, owner(owner), silent));
        if (hostsLoaded.get().isDone()) return Optional.empty();
        return Optional.of(owner -> new SavedRemoteDocument(key, parsed.get(), this::identity,
            found -> new RemoteDocument(found, path, channels, owner(owner), silent), hostsLoaded, ui));
    }

    /** The window that owns a reopened document's sign-in prompts: the one reopening it, else the one used last. */
    private Supplier<Optional<WindowHandle>> owner(WindowOwner owner) {
        return owner instanceof WindowHandle window ? owner(window) : activeWindow;
    }""", 1)])
PY
```

`EditChannels` opens a quiet job's channel with a quiet opener, which the plugin gives; the package's contract and
AGENTS.md name the new document among those that name Editor types:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
E = "plugins/remote/src/main/java/dev/jasper/remote/edit/"
edit(E + "EditChannels.java", [
    ("""    private final BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener;
""", """    private final BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener;
    /** Opens a quiet job's channel, a restored document's first read, asking nothing (workspace plan 6b Rulings 29). */
    private final BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> quietOpener;
""", 1),
    ("""    public EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
                        Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui) {
        this.opener = Objects.requireNonNull(opener); this.authenticated = Objects.requireNonNull(authenticated);
        this.background = Objects.requireNonNull(background); this.ui = Objects.requireNonNull(ui);
    }""", """    public EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
                        Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui) {
        this(opener, opener, authenticated, background, ui);
    }

    /**
     * @param opener opens an endpoint, on the UI thread; the window, when present, owns sign-in prompts
     * @param quietOpener opens one for a quiet job ({@link #submitQuietly}), asking nothing
     * @param authenticated whether an authenticated connection exists, so opening needs no prompt
     * @param background where operations run
     * @param ui the UI thread
     */
    public EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
                        BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> quietOpener,
                        Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui) {
        this.opener = Objects.requireNonNull(opener); this.quietOpener = Objects.requireNonNull(quietOpener);
        this.authenticated = Objects.requireNonNull(authenticated);
        this.background = Objects.requireNonNull(background); this.ui = Objects.requireNonNull(ui);
    }""", 1),
    ("""    public <T> CompletableFuture<T> submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work) {
        var job = new Job<T>(Objects.requireNonNull(owner), Objects.requireNonNull(work));""", """    public <T> CompletableFuture<T> submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work) {
        return submit(identity, owner, work, false);
    }

    /**
     * As {@link #submit}, a restored document's first read (workspace tabs spec 13): a channel it must open opens with the
     * quiet opener, which asks nothing.
     *
     * @param owner the window whose questions the quiet opener answers Cancel
     * @return the work's result, completed on the UI thread
     */
    public <T> CompletableFuture<T> submitQuietly(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work) {
        return submit(identity, owner, work, true);
    }

    private <T> CompletableFuture<T> submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work, boolean quiet) {
        var job = new Job<T>(Objects.requireNonNull(owner), Objects.requireNonNull(work), quiet);""", 1),
    ("""        try { opening = opener.apply(channel.identity, owner); }""",
     """        try { opening = (job.quiet ? quietOpener : opener).apply(channel.identity, owner); }""", 1),
    ("""        final Supplier<Optional<WindowHandle>> owner;
        final Work<T> work;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Job(Supplier<Optional<WindowHandle>> owner, Work<T> work) { this.owner = owner; this.work = work; }""",
     """        final Supplier<Optional<WindowHandle>> owner;
        final Work<T> work;
        /** A restored document's first read: its channel opens asking nothing. */
        final boolean quiet;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Job(Supplier<Optional<WindowHandle>> owner, Work<T> work, boolean quiet) { this.owner = owner; this.work = work; this.quiet = quiet; }""", 1)])
edit(E + "package-info.java", [(
    """ * {@link dev.jasper.remote.edit.RemoteDocument} and {@link dev.jasper.remote.edit.RemoteEditing} name Editor types; they""",
    """ * {@link dev.jasper.remote.edit.RemoteDocument}, {@code SavedRemoteDocument} and
 * {@link dev.jasper.remote.edit.RemoteEditing} name Editor types; they""", 1)])
edit("AGENTS.md", [(
    "named only in `dev.jasper.remote.edit`'s `RemoteDocument` and `RemoteEditing`)",
    "named only in `dev.jasper.remote.edit`'s `RemoteDocument`, `SavedRemoteDocument` and `RemoteEditing`)", 1)])
PY
```

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java", [(
    """    private void showDisconnected(String message) {
        showNotice(message, view == null ? "Retry" : "Reconnect", this::connect, "Close", () -> onClose.run());""",
    """    private void showDisconnected(String message) {
        // A restored session was connected when its tab was saved: connecting it again is a Reconnect (workspace tabs spec 13).
        showNotice(message, view == null && !restored ? "Retry" : "Reconnect", this::connect, "Close", () -> onClose.run());""", 1)])
PY
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test :jasper-app:test --tests 'dev.jasper.app.workspace.SessionPaneTest' --continue`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A plugins/remote jasper-app AGENTS.md
git commit -F - <<'EOF'
Workspace tabs: Remote's SSH tabs come back

An SSH tab is saved with its host and folder; restored, it waits until
it is shown and then reconnects when signing in asks nothing (the SSH
agent, or the unlocked Vault), one tab after another in a window, and
otherwise shows Not connected with Reconnect; a missing host declines.
Remote documents reopen before the saved hosts have loaded and read
quietly. A restored tab's first connect and a quiet read answer a
host-key question Cancel, so nothing asks; Reconnect asks as usual.
Remote 0.16.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The sample's About comes back

Spec 14's plugin list: the sample's About, the content tab plan 4b made of its window, comes back with the windows
(Rulings 11); it shows the guide's pattern: one method opens the tab for its action and for its restorer. The Vault's
manager is a dialog over the main window and does not come back (Spec issues 1; Rulings 28): the Vault changes nothing
here.

**Files:**
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `src/main/resources/plugin.toml` (0.4.0 to
  0.5.0, Rulings 18); `docs/plugin-authoring.md` (the `pluginpanels` snippet)
- Test: `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Consumes: Task 1's `ContentSpec.withRestore`, `Windows.restorer`, `RestoreState`.
- Produces: the sample's restorer kind `about`.

- [ ] **Step 1: Write the failing test**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
edit("plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java", [(
    """    private static final PluginInfo OBSERVING = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0",""",
    """    /** Workspace tabs spec 13: the About tab gives its restore state and comes back through the sample's restorer. */
    @Test void theAboutTabComesBackThroughItsRestorer() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.sample", "demo_ui", true);
            host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);
            start(host,INFO, Set.of(), Set.of(), new SamplePlugin());
            UUID window = host.addTerminalWindow();
            assertThat(host.invoke("dev.jasper.sample.about", window, null)).isTrue();
            var about = host.contentTab("dev.jasper.sample", "About Sample").orElseThrow();
            var state = new dev.jasper.sdk.RestoreState("about", java.util.Map.of());
            assertThat(host.restoreState(about)).contains(state);
            about.close();
            assertThat(host.restoreTab("dev.jasper.sample", window, "About Sample", state)).isPresent();
            assertThat(host.contentTab("dev.jasper.sample", "About Sample"))
                .hasValueSatisfying(tab -> assertThat(tab.window().id()).isEqualTo(window));
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static final PluginInfo OBSERVING = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0",""", 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: FAIL: the About tab gives no restore state.

- [ ] **Step 3: The restorer**

The sample's About opens through one consumer, for the action and for the restorer; the guide shows the same block:

```bash
python3 -I - <<'PY'
import sys, pathlib; sys.path.insert(0, "build/plan6b"); from edits import edit
S = "plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java"
edit(S, [
    ("import dev.jasper.sdk.ui.ContentSpec;\n", "import dev.jasper.sdk.RestoreState;\nimport dev.jasper.sdk.WindowOwner;\nimport dev.jasper.sdk.ui.ContentSpec;\n", 1),
    ("""        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK), invoked -> {
            // A tab in the window the action ran in, which Jasper draws with its strip; an open About is selected again.
            if (about[0] != null && !about[0].isClosed()) { about[0].select(); return; }""",
     """        // A tab in the given window, which Jasper draws with its strip; an open About is selected again.
        java.util.function.Consumer<WindowOwner> showAbout = owner -> {
            if (about[0] != null && !about[0].isClosed()) { about[0].select(); return; }""", 1),
    ("""            about[0] = context.windows().openTab(new ContentSpec("about", "About Sample"), invoked.window(), content);
        });
""", """            // Jasper saves the tab with its windows, and the "about" restorer below opens it again (SDK 0.21.0).
            about[0] = context.windows().openTab(new ContentSpec("about", "About Sample")
                .withRestore(new RestoreState("about", java.util.Map.of())), owner, content);
        };
        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(FLASK),
            invoked -> showAbout.accept(invoked.window()));
        context.windows().restorer("about", request -> {
            showAbout.accept(request.window());
            return java.util.Optional.empty();
        });
""", 1)])
edit("plugins/sample/src/main/resources/plugin.toml", [('version = "0.4.0"', 'version = "0.5.0"', 1)])
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

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-sample:test :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest' --continue`
Expected: PASS (the guide's `pluginpanels` snippet equals the sample's source).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/sample docs/plugin-authoring.md
git commit -F - <<'EOF'
Workspace tabs: the sample's About comes back

The About tab is saved with its restore state and comes back through
the sample's restorer, which opens it the way its action does; the
authoring guide shows the same block. Sample 0.5.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, STATUS, the THD hand-off and the whole build

**Files:**
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`,
  `docs/configuration.md`, `docs/editor.md`, `docs/remote.md`, `docs/STATUS.md` (the entry at the top), this plan's
  status banner (line 3)
- Test: none new; the whole build

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentences true (AGENTS.md "Documentation edits"): the SDK README's 0.21
section and package table gain restore state and restorers, the authoring guide shows how a content tab and a session
come back, the SDK architecture names the slot and the placeholder, and the user guides say which tabs come back and
where. The Credential Vault's guide already says its manager is a dialog over the main window, and nothing here changes
it:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
R = "jasper-sdk/README.md"
edit(R, [
    ("`ContentTab`, `TabPlacement`, `ContentSpec`,", "`ContentTab`, `TabPlacement`, `ContentSpec`, `TabRestorer`, `RestoreRequest`,", 1),
    ("| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |",
     "| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `RestoreState`, `Capabilities`, `MissingCapabilityException` |", 1),
    ("""pane's `info().state()` is `SessionState.NOT_STARTED` until the tab is first shown, and text sent to it is dropped; then
its shell starts as a new tab's does.
""", """pane's `info().state()` is `SessionState.NOT_STARTED` until the tab is first shown, and text sent to it is dropped; then
its shell starts, or its session connects, as a new tab's does.

A plugin's tab comes back when the plugin says how: a `RestoreState` (`dev.jasper.sdk`: a restorer kind and at most 32
strings, which Jasper writes into `workspace.toml`, so never a secret) in its `ContentSpec` or `SessionSpec`
(`withRestore`), or later through `ContentTab.setRestore`, and a restorer for that kind registered in `start()` with
`Windows.restorer(kind, restorer)`. The restorer gets a `RestoreRequest` (the window being restored, the saved title, the
state) and opens the tab the way the plugin opens it, before it returns `Optional.empty()`; the first content tab it
opens is the saved tab, whatever window or placement it names, and so is the first session it opens in
`request.window()`; Jasper puts it where it was saved, in its window's pane. When the
tab cannot come back for a reason the plugin expects (its file or host is gone), the restorer returns that reason
instead, which is no failure. A tab whose plugin is not installed, turned off, waiting for review or failed to start, has
no restorer for its kind, or whose restorer declines, throws or opens nothing comes back as a placeholder that says why,
with Close and, while the plugin runs, Retry. In the testkit `FakePluginHost.restoreState` reads the restore state a tab
gives and `restoreTab` runs a plugin's restorer as Jasper does: the tab is placed in the window being restored, not
shown, and a session's tab waits, `NOT_STARTED`, until it is shown; `declinedRestores()` lists the reasons restorers
gave.
""", 1)])
A = "docs/plugin-authoring.md"
edit(A, [
    ("""new child window (`NEW_WINDOW`). Preserve the component/session during moves. The tab is a
content tab of its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.
""", """new child window (`NEW_WINDOW`). Preserve the component/session during moves. The tab is a
content tab of its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.

A tab comes back when Jasper restores its windows (SDK 0.21.0) if you say how: give its `ContentSpec` a `RestoreState`
(`spec.withRestore(RestoreState.of("document", "path", file.toString()))`), change it with `ContentTab.setRestore` when the tab
comes to stand for something else, and register the restorer for the kind in `start()`:
`context.windows().restorer("document", request -> openDocument(request.state().values().get("path"), request.window()))`,
where `openDocument` returns empty once the tab is open, or why it could not open it.
The restorer opens the tab the way your action does, with the same restore state, and returns `Optional.empty()`; Jasper
places it where it was saved, in whichever window and pane held it. When it cannot come back now (its file is gone),
return why instead, `Optional.of("its file is gone")`: its placeholder says so with Retry, and nothing reports a failure.
A restore state is saved in `workspace.toml`: keep it small, and never put a secret in it. A tab without one is not
saved, and one whose restorer throws or opens nothing comes back as a placeholder with Retry too. Open only that tab: the
window being restored takes no other while your restorer runs, so another tab opens where an owner-less one would.
""", 1),
    ("""- `PipedInputStream` fails once the thread that wrote last has ended. The sample uses a queue.
""", """- `PipedInputStream` fails once the thread that wrote last has ended. The sample uses a queue.
- **Coming back (SDK 0.21.0).** `SessionSpec.withRestore(state)` saves the session's tab with Jasper's windows; your
  restorer for the kind (`Windows.restorer`) opens the session again with `Terminals.openTab(request.window(), …)`, and
  Jasper connects it when its tab is first shown, calling your `connector` as for a first connect.
""", 1)])
S = "docs/sdk-architecture.md"
edit(S, [("""The application translates SDK values to `contributions.TabRegistry`/`Tab`.
""", """The application translates SDK values to `contributions.TabRegistry`/`Tab`.
Since SDK 0.21.0 a tab carries its plugin's restore state (`Tab.restore()`, the app's `layout.TabRestore`), and the
registry keeps each plugin's restorers (`Windows.restorer`). `WindowManager.snapshot()` saves a plugin's tab as a
`TabRecord.Content` of its window while its plugin has a restorer for its kind, never Jasper's own tabs, and leaves out
tabs, plugins' first, when the file would pass `WorkspaceFile.MAX_BYTES`. Restoring a window, `WindowContent.restore`
asks the window manager for each such record; `TabRegistry.restore` runs the restorer with a slot set (one at a time),
and the first content tab the plugin opens meanwhile (`TabRegistry.open`), or the first session it opens in the window
being restored (`WindowContent.openTerminal`), takes the saved id, while it is free, and that window, before any
placement applies; the window then places it in its saved parent tab and pane with its other tabs, and takes no other tab while the
restorer runs. A reason the restorer returns is the placeholder's and no incident; what it throws is both. Otherwise
the tab is a `Tab.Kind.PLACEHOLDER` (`workspace.PlaceholderContent`) that says why, from `WindowManager.PluginState` (the
application's reading of `PluginRuntime.standing`), saved again as it was; its Retry and Close act in the window that
holds it then, and it asks how its plugin stands whenever it shows and on Retry.
""", 1)])
edit("docs/configuration.md", [("""tab shows starts its shell at once, the others when first shown. Scrollback and running programs do not come back. A
""", """tab shows starts its shell at once, the others when first shown. A plugin's tab comes back in its parent tab and pane
when its plugin can restore it (the Editor's files, Remote's SSH tabs), and otherwise as a placeholder that says why (the
plugin is not installed, turned off, waiting for your review or could not start, or it could not restore that tab), with
Close and, while its plugin runs, Retry. Scrollback and running programs do not come back. A
""", 1)])
edit("docs/editor.md", [("""indentation and Wrap (click to toggle soft wrap for the tab).
""", """indentation and Wrap (click to toggle soft wrap for the tab).

When Jasper restores its windows, each tab comes back with its file, in its parent tab and pane: a local file is read at
once; a remote one, through Remote, waits until its tab is first active or you press Load, and then reads only if
signing in asks nothing (an open connection, the SSH agent, or the unlocked Vault); otherwise it says so, and Retry signs
in. An untitled tab that was never saved does not come back.
""", 1)])
edit("docs/remote.md", [("""shows the disconnected banner and Reconnect makes a new connection. The status bar shows how many
SSH sessions are open; clicking it toggles the panel.""", """shows the disconnected banner and Reconnect makes a new connection. When Jasper restores its windows, each SSH tab
comes back to its host and folder and waits until it is first shown; then it connects at once when signing in asks
nothing (every host on its route signs in through the SSH agent, or through the Vault while it is unlocked), after any
connection in progress in that window, and otherwise, or when its host's key is not trusted yet, it shows Not connected
with Reconnect, which asks as usual. The status bar shows how many
SSH sessions are open; clicking it toggles the panel.""", 1)])
edit("docs/app-architecture.md", [("""window's shown parent tab first shows it, and until then its pane reports `NOT_STARTED`.
""", """window's shown parent tab first shows it, and until then its pane reports `NOT_STARTED`; a plugin's tab comes back through
its plugin's restorer or as a placeholder (see [SDK architecture](sdk-architecture.md)).
""", 1)])
PY
```

- [ ] **Step 2: Source hygiene**

```bash
python3 -I - $(git diff --name-only --diff-filter=d HEAD~5 -- '*.java') <<'PY'
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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 6a's
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 6b: plugins' tabs come back — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-6b-plugin-restorers.md),
[spec](superpowers/specs/2026-10-19-jasper-parent-tabs-design.md), amending
[the workspace tabs spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). SDK 0.21.0 (plan 6a's bump; no
bump of this plan's own) gains `dev.jasper.sdk.RestoreState` (a restorer kind and at most 32 strings, 4096 characters in
all), restore state on `ContentSpec` and `SessionSpec` (`withRestore`), `ContentTab.setRestore`,
`Windows.restorer(kind, TabRestorer)` (a restorer returns empty, or why the tab cannot come back, which raises no
incident) and `RestoreRequest`; the testkit gains `FakePluginHost.restoreState`, `restoreTab` (the restored tab takes the
window being restored, placed, not shown; a session waits `NOT_STARTED` until shown) and `declinedRestores`, and
`PluginContractTest` checks restorers on the fake and on the application. In the application a tab carries its restore
state (`Tab.restore()`, `layout.TabRestore`); `TabRegistry` keeps each plugin's restorers and, while one runs (one at a
time), a slot whose saved id (while free) and window the next content tab that plugin opens takes, or the next session
it opens in that window, unshown, before any placement or wait for the main window, so its `ContentTab.window()` names that window
at once; the window being restored takes no other tab meanwhile (another content tab goes where an owner-less one goes,
another session is refused). `WindowManager` saves a plugin's tab as `TabRecord.Content` of its window's `WindowRecord`
while its plugin restores that kind (never Jasper's own tabs, going or coming), leaving out tabs, plugins' first and as
closing them would, if the file would pass `WorkspaceFile.MAX_BYTES`; restoring, plan 6a's `WindowContent.restore` asks
it for each record (`savedTabs`), so a plugin's tab comes back in its saved parent tab and pane of the main window, a
full window or a pane window, or as a `Tab.Kind.PLACEHOLDER` tab (`workspace.PlaceholderContent`) that says why from how
its plugin stands (`PluginRuntime.standing`, given as `WindowManager.PluginState`: not installed, turned off, waiting for
review, could not start, or "… could not restore this tab: …" with Retry), asks again when it shows and on
Retry, and acts in whatever window holds it; a placeholder is saved again as it was, and a full or pane window holding
only plugins' tabs comes back with them. The Editor (<0.12.0>) saves a document's key and reopens it through its
reopeners (`openRestored`): a remote document waits until its tab is first active or Load, then reads with the Editor
API's new `EditorDocument.readQuietly`, asking nothing. Remote (<0.16.0>) reopens remote documents before its hosts have
loaded (`SavedRemoteDocument`) and saves an SSH tab's host and folder; when the tab is first shown it reconnects, one tab
after another per window, if signing in asks nothing (an open connection, the agent, or the unlocked Vault), else shows
Not connected with Reconnect; a missing host declines. A restored tab's first connect and a restored document's quiet
read answer a host-key question Cancel (`ConnectAttempt.quiet`, `EditChannels.submitQuietly`), so a host accepted Once
comes back Not connected with Reconnect, which asks. The sample's About (<0.5.0>) comes back; the Vault's manager, a
dialog over the main window, does not, and the Vault keeps 0.7.0. `AppContractTest` now opens the main window and then a
child for a tab placed in a new window while none is open (plan 4b's hand-off), and the two other harness hand-offs
(`FakeWorkspace.closeWindow` nulling `Panel.instance` with `AppContractTest`'s panel caches, and `AppContractTest`'s panel
routing) closed without code: no harness can close a main window and no contract case toggles a panel; they belong to the
plan that first adds such a case. Rulings (plan, "Rulings"): <one clause each, 1 to 31; 10 and 12 superseded>.
Deviations: <none, or each with its reason>. THD Sourcing Crypto (`~/projects/thd-sourcing-crypto-jasper-plugin`, branch
`jasper-sdk-0.17.1` at `3740e4e`): SDK 0.21.0 is the series' last bump and the one to rebuild it against, once, with the
jars `sdkRelease` publishes; plan 4b's entry lists what 0.18, 0.19 and 0.20 need, and its `plugin.toml` and
`PluginPackagingTest.java:49` take `sdk = ">=0.21.0, <0.22"` and `build.gradle.kts:8` and `README.md:28` name 0.21.0;
0.21 needs nothing more (it names `SessionState.RUNNING` in one fixture and switches over no `SessionState`, and it
opens only a dialog, which is not saved). `./gradlew check --continue`: **<counts>**. GUI checks for the user: two parent
tabs in the main window, an Editor tab in the hidden one beside a terminal, a Remote SSH tab (one host through the agent,
one with a password in the Vault, locked), and, with the sample on, its About popped out to a pane window; an Editor tab
in a full window; quit and start: each comes back in its window, parent tab and pane, the agent's SSH tab connects when
shown, the other says Not connected with Reconnect, and the hidden parent tab's remote file loads only when that parent
tab shows; the Vault's manager, if it was open, stays closed; under the Enterprise look the parent-tab bar shows as
before; turn the sample off in Manage Plugins, restart: its About is a placeholder without Retry in its pane window, and
Close closes that window; turn Remote off with a remote file open in the Editor, restart: that tab is a placeholder with
Retry ("Editor could not restore this tab: no running plugin opens remote: documents."), and turning Remote on again and
restarting brings it back; a remote file open in the Editor comes back without asking for anything, loads when its tab
is clicked if the agent or the unlocked Vault signs in, and otherwise says so with Retry; two SSH tabs restored side by
side in one window both connect; an Editor tab whose file was deleted comes back as the Editor's failed read; pop a
placeholder out, then Bring Back: it returns to its pane, and Retry works there; connect an SSH host whose key you
accept Once, quit and start: its tab comes back Not connected with Reconnect, asking nothing, and Reconnect asks about
the key; start in safe mode with a user plugin's tab saved: its placeholder says the plugin is turned off. Not merged,
not pushed.
```

Replace the angle-bracketed parts with the facts of the run (the three plugin versions as Tasks 3 to 5 set them). Set
this plan's status banner (line 3) to "**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and
list any deviation from the plan's text there.

```bash
git add jasper-sdk/README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for plugins' tabs coming back

The SDK README and the authoring guide describe restore state and
restorers, the architecture names the slot and the placeholder, and
the user guides say which tabs come back and where; STATUS records plan
6b with its rulings, the THD Sourcing Crypto hand-off and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage, parent tabs spec: 9's "covers every window kind" for plugins' tabs (Task 2: the main window's hidden
  parent tab and its panes, a full window, a pane window); 10's plugins seeing windows only (Task 1's `RestoreRequest`
  names the window; Task 2 places the tab in its parent tab and pane); 4's encoding (content records ride in plan 6a's
  `WindowRecord.tabs`) and invariants (the size limit takes tabs out through `keeping`). Workspace tabs spec: 9.2's
  placeholder (Task 2); 13's content tabs: rebuilt by their plugin's restorer from the state given when the tab opened
  (Tasks 1 and 2), the Editor reopening files and not keeping an unsaved untitled buffer (Task 3), Remote choosing
  between reconnecting and a Reconnect state (Task 4), a tab whose plugin is missing or whose restorer fails becoming a
  placeholder (Task 2); 14's `ContentSpec`/`SessionSpec` restore state (as amended in `27874bf7d`) and `Windows.restorer`
  rows (Task 1), its plugin list for this plan: Remote, sample (About tab) and Editor (restorer) (Tasks 3 to 5), with the
  Vault's manager now a dialog (Spec issues 1), the testkit fakes and the contract suites (Task 1), the THD hand-off
  (Task 6); 15's disabled-plugin row (Rulings 7, Task 2's placeholder); 17's SDK line: contract tests on the app and the
  testkit (Task 1).
- Hand-offs: each in "Hand-offs absorbed" is met by the task or Ruling it names; none is left without a disposition.
- Every quoted anchor was matched, at its stated count, in a replay of plans 7a, 7b, 7c and 6a and of Tasks 1-6 in order,
  on `git archive` copies of `ec41c97db` and `e80f2c322`, and of `8e129ff34` but for Remote's manifest version there (see
  Workspace); nothing was compiled. The ledger after Task 2 was checked with a Python copy of
  `ThemeMigrationCoverageTest`'s two path checks, and this plan's and the edited documents' links and the
  `pluginpanels` snippet with a Python copy of `AppDocumentationTest`'s rules.
- Type consistency: `RestoreState`, `ContentSpec.withRestore`, `SessionSpec.withRestore`, `ContentTab.setRestore`,
  `TabRestorer` (returning `Optional<String>`), `RestoreRequest`, `Windows.restorer`, `FakePluginHost.restoreState`/
  `restoreTab`/`declinedRestores`, `FakeWorkspace.Slot`/`takeSlot`/`placeTab`/`placeTerminal`, `FakeSessions.hold`,
  `TabRestore`, `Tab.restore`, `TabRegistry.Restoring`/`Restored`/`restorer`/`restores`/`restore`/`slot`/`restored`/
  `restoring`/`onRestoreChanged`, `SessionRequest.restore`, `Tab.Kind.PLACEHOLDER`, `PlaceholderContent`,
  `WindowContent.savedTabs`/`retry`, `WorkspaceView.openAt`, `WindowManager.PluginState`/`plugins`,
  `PluginRuntime.Standing`/`standing`, `WorkspaceFile.MAX_BYTES`/`encodedBytes`, `EditorWindow.RESTORE_KIND`/
  `RESTORE_KEY`/`openRestored`, `DocumentSession.defer`/`deferred`, `EditorDocument.readQuietly`,
  `RemotePlugin.SSH_TAB`/`sshTab`/`restoreHost`, `SavedRemoteDocument`, the eight-argument `RemoteEditing`,
  `EditChannels.submitQuietly` and its five-argument constructor, `Reopeners.handles` and `TabRegistry.restoring` keep one
  name and one signature from the task that creates them to the last task that uses them.
