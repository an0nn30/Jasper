# Workspace tabs 6b: plugins' tabs come back Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A plugin's tab comes back when Jasper restores its windows: the plugin gives restore state (a restorer kind
and a few strings) when it opens the tab, and the restorer it registered rebuilds the tab in its saved place. The Editor
reopens its files, Remote's SSH tabs come back and reconnect or wait for Reconnect, the Vault's manager and the
sample's About come back, and a tab whose plugin is missing, turned off or failed comes back as a placeholder that says
why, with Close and, while its plugin runs, Retry.

**Architecture:** The SDK (still 0.21.0, plan 6a's) gains `dev.jasper.sdk.RestoreState`, an optional restore state on
`ContentSpec` and `SessionSpec`, `ContentTab.setRestore`, `Windows.restorer(kind, TabRestorer)` (a restorer may decline
with a reason) and `RestoreRequest`.
In the application a tab carries its restore state (`Tab.restore()`, the app's twin `layout.TabRestore`), the
`TabRegistry` keeps each plugin's restorers and, while one runs, a slot: the next tab that plugin opens, a content tab
in `TabRegistry.open` or a session's terminal tab in `WindowContent.openTerminal`, takes the saved id and the window
being restored, unshown, and the window places it with its other tabs. `WindowManager` saves a plugin's tab as
`TabRecord.Content` while its plugin has a restorer for its kind, within the size a read accepts, rebuilds it through the
registry, and otherwise makes a `Tab.Kind.PLACEHOLDER` tab (`workspace.PlaceholderContent`) that says why from how its
plugin stands, which is saved again as it was. The testkit's fakes restore as Jasper does (the tab placed, a session
waiting until shown), and the contract suite runs on both runtimes. The Editor defers a restored remote document until
its tab is active and reads it with the Editor API's new quiet read; Remote reopens remote documents before its hosts
have loaded and connects restored SSH tabs one at a time per window.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1 decision 5, 9.2's placeholder, 13's content tabs, 14's `ContentSpec`, `SessionSpec` (amended in `27874bf7d`)
and `Windows.restorer` rows and its plugin list, 15's disabled-plugin row, 17's SDK line and 18). The second half of section 18's plan 6;
[6a](2026-10-18-workspace-6a-save-restore.md) (saving, restoring windows and terminal tabs, SDK 0.21.0) executes first,
after plans [1](2026-10-17-workspace-1-single-instance.md) to [4b](2026-10-18-workspace-4b-plugin-windows.md) and
[5](2026-10-18-workspace-5-tear-out.md).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`, after plan 6a's last commit. Run every command from the worktree root. Every anchor below was
taken from, and every edit script of Tasks 1-6 replayed on, a copy of `22b702456` with the edit scripts and created
files of plans 1, 2, 3a, 3b (as committed in `17830fac6`), 4a and 4b (as committed in `68e1bbb80` and `ef9ab4523`), 5
(as committed in `7d79fdffb`) and 6a applied in order (plan 1's later `onQuitting` Javadoc line, `fe8488bb6`, is quoted
by no anchor here); nothing was compiled or run. Every edit quotes the text it replaces, and the scripts stop with a
message when that text is not found the stated number of times: re-find the quoted text (a plan before this one may have
reworded it), adjust the anchor, re-run, and record it in this plan's banner; never edit around a missing anchor. Later
tasks anchor on text earlier tasks of this plan wrote, and quote it as those tasks leave it.

## Spec issues

None open. Spec 14's `SessionSpec` row (amended in `27874bf7d`, "sessions carry restore state") gives a session's tab
restore state, so Remote's SSH tabs come back through Remote like content tabs (Rulings 2). Section 15's disabled-plugin
row ("Its tabs close; at the next start they restore as placeholders") holds without code: turning a plugin off takes
effect at the next start, so Quit saves its open tabs, and the next start, where it does not run, makes them
placeholders (Rulings 7).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- The SDK stays 0.21.0 (plan 6a's bump; Rulings 2 of 6a): every SDK addition here carries `@since 0.21.0`, and no
  manifest range changes. The Editor (0.11.0), Remote (0.14.0; plan 4b took 0.13.0), the Vault (0.7.0) and the sample
  (0.4.0) bump their versions because their behaviour changes; the other plugins keep theirs (Rulings 12). The Editor's
  API (`dev.jasper.editor.api`) gains one default method, `EditorDocument.readQuietly`, which older callers never see.
- Plan 2's `WorkspaceFile` changes only as Task 2 says: `MAX_BYTES` becomes public and `encodedBytes(Snapshot)` is new, so
  a snapshot can stay within what a read accepts.
- Clean break: `ContentSpec` and `SessionSpec` gain a component, and their old constructors stay as the ordinary way to
  make a spec that does not come back; no deprecation.
- Package edges (package-info "Allowed outgoing" lists; `verifySdkArchitecture`, `verifyApplicationArchitecture`,
  `verifyPluginArchitecture`): nothing gains an edge. `RestoreState` is in the SDK's root package, which `ui` and
  `terminal` already reach; `TabRestore` is in `layout`, which `contributions`, `terminals`, `workspace` and `plugins`
  already reach.
- Plugins' code runs contained: a restorer runs through `Containment.attempt` on the EDT; a reason it returns is a
  placeholder's, with no incident, and what it throws is a placeholder's reason and the plugin's failure, never an
  exception out of a window's restore.
- No motion (spec 4): a placeholder, and a tab Retry rebuilds, appear at once.
- A restore state is saved in plain text in `workspace.toml`: no plugin here puts a secret in it (a host id, a name, a
  folder, a document key, the word "manager" or "about").
- The theme-migration ledger (`docs/design/theme-engine/migration.csv`, checked by `ThemeMigrationCoverageTest`):
  `PlaceholderContent` gets a row in Task 2 with the `ledger.py` helper; the SDK and the plugins are not covered.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, every new public member has a comment, and an SDK addition carries `@since 0.21.0`.
- `WindowContent` and its tests import both `java.awt.*` and `java.util.*`: write `java.util.List` in full there.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
  `AppDocumentationTest` checks every guide's links and that a guide's `<!-- example:… -->` snippet equals its source
  between the `// example:…:start` and `:end` markers (Task 5 changes the sample's `pluginpanels` block and the guide's
  copy together).

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan6b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan6b
cp build/plan6a/edits.py build/plan6a/ledger.py build/plan6b/ 2>/dev/null || cp build/plan3a/edits.py build/plan3a/ledger.py build/plan6b/ 2>/dev/null || echo "the helpers are gone: recreate them from plan 3a's 'The two helper scripts', with build/plan6b in place of build/plan3a"
ls build/plan6b/edits.py build/plan6b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `add`, `rename` and `drop`. Every script below runs as `python3 -I - <<'PY'`
from the worktree root and starts with `import sys; sys.path.insert(0, "build/plan6b")`.

## Review Focus

1. The slot: while a restorer runs, the first tab its plugin opens, a content tab or a session's terminal tab, takes
   the saved id (while it is free) and the window being restored, whatever window or placement it named, is not shown,
   and the window places it where it was saved; one restore runs at a time. Task 1:
   `TabRegistryTest.aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot` and the three contract tests on both
   runtimes; Task 2: `PluginTabRestoreTest.aSessionsTabComesBackThroughItsPluginAndConnectsWhenFirstShown`.
2. What is saved: a plugin's tab only while its plugin restores its kind, a placeholder exactly as it was saved, never an
   app-owned tab (the Errors tab), and never more than a read accepts. Task 2:
   `PluginTabRestoreTest.aPluginsTabWithoutRestoreStateOrRestorerIsNotSavedAndAppOwnedTabsNeverAre`,
   `aTabWhosePluginIsNotRunningComesBackAsAPlaceholderAndIsSavedAgainAsItWas`,
   `aWorkspaceTooLargeToReadBackLeavesOutPluginsTabsLargestFirst`.
3. Placeholders: the reason follows how the plugin stands (asked again when it shows and on Retry), Retry only while it
   runs, and Retry and Close act in the window that holds it after a move. Task 2:
   `aRestorerThatFailsLeavesAPlaceholderAndRetryPutsItsTabInThePlaceholdersPlace`,
   `aPlaceholderSaysHowItsPluginStandsAndOffersRetryOnlyWhileItRuns`,
   `aPlaceholderMovedToAnotherWindowRetriesAndClosesThere`, `aPlaceholderInAChildWindowClosesFromTheWindowThatHoldsItNow`.
4. Nothing asks for a password while the windows come back, and expected misses raise no incident: a restored SSH tab
   connects only when first shown, after the hosts have loaded, when every hop signs in alone, one after another in a
   window; a restored remote document reads only when its tab is first active (or Load), asking nothing; restorers that
   miss decline. Task 4: `RemotePluginTest.anSshTabComesBackAndConnectsOnlyWhenSigningInAsksNothing`,
   `restoredSshTabsShownTogetherInOneWindowAllConnect`, `RemoteEditingTest
   .aKeyReopensBeforeTheSavedHostsHaveLoadedAndFindsThemWhenRead`, `aQuietReadSignsInOnlyWhenNothingWouldBeAsked`,
   `SessionPaneTest.aRestoredSessionLeftNotConnectedOffersReconnect`; Task 3:
   `EditorPluginTest.aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive`,
   `EditorTabTest.aDeferredDocumentOffersLoadReadsQuietlyFirstAndAsUsualOnRetry`.
5. The Editor's untitled buffer is not saved, a saved document comes back by its key, and a key nothing opens declines
   with why. Task 3: `EditorPluginTest.aDocumentComesBackFromItsKeyAndAnUntitledOneIsNotSaved`.

## Verified facts

From a replay of plans 1, 2, 3a, 3b, 4a, 4b, 5 and 6a on `22b702456` (see Workspace).

1. `ContentSpec(String key, String title)` (`jasper-sdk/…/ui/ContentSpec.java:8`) and `SessionSpec(String title,
   Optional<String> icon, ExitPolicy onExit, Consumer<PendingSession> connector)` (`terminal/SessionSpec.java:18`) are
   records; `Windows.openTab(spec, owner, content, placement)` is abstract with a three-argument default
   (`ui/Windows.java:28-45`). The SDK's `ui` package reaches `terminal`; `terminal` may not reach `ui`.
2. `TabRegistry.open(plugin, key, title, owner, component[, placement])` (`contributions/TabRegistry.java:185`, `:194`)
   makes a plugin's tab and attaches it through its window's host: a `NEW_WINDOW` placement through
   `Windows.emptyWindow`, and an owner-less tab asked for while the main window is being composed waits for it (`later`,
   since `Windows.main` is then null); `TabRegistry.add(TabId, …)` (`:146`) registers a tab under a given id with no host
   call. `WindowContent.openTerminal` (`workspace/WindowContent.java:1325`) makes every terminal tab, a session's
   included, with a fresh `TabId`. A plugin's tab is active while it is its window's active group's selected tab
   (`WindowContent.update`, `:1559`).
3. Plan 6a's `WindowContent.restore` keeps only `TabRecord.Terminal` records and takes the others out of the layout;
   `WindowManager.tabRecord` saves only local terminal tabs; `TerminalPane.restored()` makes a pane wait, a provided
   session's included (`start()` then connects).
4. Plan 4b's Errors tab is app-owned: owner `TabRegistry.APP_OWNER` (`jasper`, `contributions/TabRegistry.java:36`),
   key `errors` (`application/ErrorsTab.java:24`).
5. Remote opens an SSH tab with `new SessionSpec(host.name(), …)` (`plugins/remote/…/RemotePlugin.java:448`) and reads
   its hosts in the background at start (`store.load()`, `:256`; `HostStore.reload()` returns a future,
   `hosts/HostStore.java:53`); `Auth` is `Agent`, `Vault` or `VaultKeys`. A window shows one connection attempt at a
   time: `connect` refuses a second with "Another connection is already in progress" (`focusExistingAttempt`, `:436`).
   Its reopener for Recent Files (`edit/RemoteEditing.java:97`) needs the saved hosts in memory, and an edit channel
   with no owner window fails rather than prompt (`edit/EditChannels.java:91`).
6. The Editor opens a document's tab with `new ContentSpec("document:" + UUID, name)` (`plugins/editor/…/ui/EditorWindow
   .java:769`) and reads it at once (`:191`); Recent Files reopens a document by its key through
   `Reopeners.resolve(String)` (`recent/Reopeners.java:48`): `local:` itself, other prefixes through the plugin that
   registered them (Remote's `remote:`).
7. The Vault's manager tab opens with `new ContentSpec(KEY, "Credential Vault")` (`plugins/vault/…/ui/VaultManagerTab
   .java:73`); the sample's About with `new ContentSpec("about", "About Sample")` (`plugins/sample/…/SamplePlugin.java:147`).
8. Turning a plugin off writes its state for the next launch (`plugins/PluginAdmin.setEnabled`); the running plugin keeps
   running. `PluginRuntime` keeps each plugin's `PluginStatus` (`ACTIVE`, `DISABLED`, `NEEDS_CONSENT`, `SKIPPED`,
   `FAILED`) and has no public accessor for it; `pluginNames()` lists every discovered plugin, running or not.
   `Containment.attempt` (`plugins/Containment.java:34`) records what a plugin's code throws as its failure.
9. `WorkspaceFile.read` sets aside a file over `MAX_BYTES` (256 KiB, package-private, `persistence/WorkspaceFile.java:181`).

## Rulings

Record them in `docs/STATUS.md` (Task 6). "What — why".

1. **Restore state is `dev.jasper.sdk.RestoreState(kind, values)`: a kind of 1 to 64 letters, digits, dots, underscores
   or hyphens, at most 32 values of at most 4096 characters in all, in the SDK's root package** — spec 14 ("a restorer
   kind and a small string map"); `SessionSpec` in `terminal` needs it and `terminal` may not reach `ui`.
2. **`SessionSpec` carries restore state too, and a session's terminal tab is saved as `TabRecord.Content` under its
   plugin** — spec 14 as amended in `27874bf7d` ("sessions carry restore state").
3. **A plugin's tab is saved only while its plugin has a restorer for its kind; an app-owned tab
   (`TabRegistry.APP_OWNER`) never is** — plan 2 Rulings 20; the Errors tab must never come back as "plugin missing".
4. **A restorer rebuilds a tab by opening it the way the plugin always does (`Windows.openTab`, `Terminals.openTab` with
   a session) while the registry holds a slot: the first tab that plugin opens takes the saved id, while no open tab has
   it, and the window being restored, whatever owner or placement it named, unshown, and the window places it with its
   other tabs; the slot is taken before `open` looks for a window, so neither `emptyWindow` nor the wait for a main
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
   takes effect at the next start, so its tabs, saved at Quit, come back as placeholders** — spec 15's row; fact 8; the
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
   shows one connection attempt at a time (fact 5).
10. **The Vault's manager (`manager`) comes back as the Vault is, locked or not, asking nothing** — spec 14; a start
    must not open an unlock prompt.
11. **The sample's About (`about`) comes back through the one method its action uses; the sample's echo session is not
    saved** — the guide's pattern; a demo session has nothing to come back to.
12. **Editor 0.11.0, Remote 0.14.0 (plan 4b took 0.13.0), Vault 0.7.0, sample 0.4.0; the other plugins keep their
    versions** — plan 4b Rulings 1's rule: a version moves with behaviour.
13. **A restored session that is not connected offers Reconnect, not Retry** — it was connected when it was saved.
14. **THD Sourcing Crypto rebuilds once, against 0.21.0, and 0.21 needs nothing more of it** — it names
    `SessionState.RUNNING` in one fixture, switches over no `SessionState` and opens only a dialog.
15. **The testkit's fake restores as Jasper does (`FakePluginHost.restoreTab`): the first tab the plugin opens takes the
    window being restored, whatever it named, and is placed, neither selected nor active; a session's tab waits,
    `NOT_STARTED` with no connection attempt, until it is shown; a reason the restorer returns is kept in
    `declinedRestores()`, never `failures()`. It puts the tab at the end of the window's active group, where Jasper puts
    it where it was saved** — plugin tests must see the lazy start and the declines they ship with.
16. **A snapshot that `WorkspaceFile.encodedBytes` finds larger than `MAX_BYTES` leaves out tabs until it fits:
    plugins' tabs first, the largest restore state first, then terminal tabs; each is logged** — a file a read would set
    aside loses every window at the next start, while a restore state holds at most 4096 characters, so this takes many
    plugin tabs.
17. **Remote's reopener answers before its saved hosts have loaded: a recorded key reopens a document
    (`SavedRemoteDocument`) that finds its hosts when first used; once they have loaded, a key whose host is gone does
    not reopen** — Jasper restores its windows while the hosts load in the background (fact 5), and a restored remote
    document must not become a placeholder for that.

## File structure

- SDK (`jasper-sdk`): `RestoreState` (new), `ui/ContentSpec`, `terminal/SessionSpec` (whole files), `ui/TabRestorer`
  and `ui/RestoreRequest` (new), `ui/Windows`, `ui/ContentTab` (Task 1).
- Testkit (`jasper-sdk-testkit`): `FakeContent`, `FakeUi`, `FakePluginHost`, `FakeWorkspace`, `FakeTerminals`,
  `FakeSessions`, `ContractHarness`, `PluginContractTest`, `FakeContractTest` (Task 1).
- Application: `layout.TabRestore` (new), `layout/package-info`, `contributions.Tab`, `TabRegistry`,
  `terminals.SessionRequest`, `plugins.HostedSessions`, `HostedUi`, `workspace.WindowContent` (Task 1);
  `workspace.PlaceholderContent` (new), `contributions.Tab`, `persistence.WorkspaceFile`, `workspace.WorkspaceView`,
  `WindowContent`, `WindowManager`, `plugins.PluginRuntime`, `application.JasperApplication` (Task 2);
  `workspace.TerminalPane` (Task 4).
- Plugins: the Editor's `api/EditorDocument`, `session/DocumentSession`, `ui/EditorTab`, `ui/EditorWindow`,
  `EditorPlugin` (Task 3); Remote's `RemotePlugin`, `edit/RemoteEditing`, `edit/RemoteDocument`,
  `edit/SavedRemoteDocument` (new) (Task 4); the Vault's `VaultManagerTab`, `VaultPlugin`, the sample's `SamplePlugin`
  (Task 5); their `plugin.toml` files.
- Tests: `RestoreStateTest` (new), `TabRegistryTest`, `TerminalFixture`, `AppContractTest` (Task 1);
  `PluginTabRestoreTest` (new), `WorkspaceRestoreTest`, `PluginRuntimeTest`, `JasperApplicationWorkspaceTest` (Task 2);
  `EditorPluginTest`, `EditorTabTest` (Task 3); `RemotePluginTest`, `RemoteEditingTest`, `SessionPaneTest` (Task 4);
  `VaultPluginTest`, `SamplePluginTest` (Task 5).
- Documents (Task 6): `jasper-sdk/README.md`, `docs/plugin-authoring.md` (and its `pluginpanels` snippet in Task 5),
  `docs/sdk-architecture.md`, `docs/configuration.md`, `docs/editor.md`, `docs/remote.md`, `docs/credential-vault.md`,
  `docs/STATUS.md`; the ledger in Task 2.

## Before you start

```bash
git branch --show-current                                                                                  # claude/workspace-tabs
git status --short                                                                                         # nothing
git log --oneline -1 --grep "Workspace tabs: documentation and status for saving the workspace"           # 6a's last commit
grep -c 'VERSION = "0.21.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                                         # 1
grep -c 'public record ContentSpec(String key, String title) {' jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentSpec.java   # 1
grep -c 'public Optional<WorkspaceFile.Snapshot> snapshot()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowManager.java  # 1
grep -c 'int restore(WorkspaceFile.WindowRecord record)' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java  # 1
grep -c 'public static final String APP_OWNER = "jasper";' jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java  # 1
```

Every command prints the stated result. Plan 6a's `docs/STATUS.md` entry is the newest; this plan's goes above it.
Create the two helper scripts (Global Constraints).

---

### Task 1: Restore state and restorers in the SDK, the fakes and the registry

Spec 14's `ContentSpec`/`SessionSpec` and `Windows.restorer` rows (sessions since the spec amendment `27874bf7d`): a
plugin says how its tab comes back and registers the restorer that rebuilds it, which may decline with a reason (Rulings
5); the application keeps the state on the tab and, while a restorer runs, gives the first tab the plugin opens the saved
id and the window being restored (Rulings 4). The contract suite holds both runtimes to it, and the testkit's fake
restores as Jasper does: the tab takes the window being restored, and a session waits until its tab is shown.

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
- Test: `TabRegistryTest`, `plugins/TerminalFixture.java`, `plugins/AppContractTest.java`

**Interfaces:**
- Consumes: plan 6a's `TerminalPane.restored()`.
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
  plugin, UUID window)`, `void restored(Tab)`; `SessionRequest(…, Icon icon, Optional<TabRestore> restore)` with the
  six-argument constructor kept.

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

Three contract tests run on the fake and on the application (a restorer reopens its content tab in the window being
restored with the saved title, whatever window or placement it names; one that declines, throws or opens nothing restores
no tab, a kind has one restorer per plugin; a session's tab gives its spec's restore state), with the harness's two new
methods on the fake:

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
edit(C + "PluginContractTest.java", [("""    @Test void aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow() {""",
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

    @Test void aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow() {""", 1)])
F = "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java"
edit(F, [("""            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }
""", """            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }
            @Override public java.util.Optional<java.util.UUID> restoreTab(String pluginId, java.util.UUID windowId, String title,
                    dev.jasper.sdk.RestoreState state) { return host.restoreTab(pluginId, windowId, title, state); }
            @Override public java.util.Optional<dev.jasper.sdk.RestoreState> restoreState(java.util.UUID tabId) { return host.restoreState(tabId); }
""", 1)])
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

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:compileTestJava --continue`
Expected: FAIL: `cannot find symbol` for `RestoreState`, `withRestore`, `setRestore`, `restorer`, `RestoreRequest`,
`restoreTab`, `restoreState`, `TabRestore`, `TabRegistry.Restoring` and `sessionRestore`.

- [ ] **Step 3: The SDK**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/RestoreState.java` (the root package: `SessionSpec`, in `terminal`, needs
it, and `terminal` may not reach `ui`, which already reaches `terminal`):

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
     * {@code Terminals.openTab}, with the same restore state. The first tab the plugin opens meanwhile is the saved tab,
     * whatever window or placement it names; Jasper puts it where it was saved, with the selection it had, and a session's
     * tab connects when it is first shown. When the tab cannot come back now for a reason the plugin expects (its file or
     * host is gone), return why: a placeholder in its place says so, with Retry, and nothing is reported as the plugin's
     * failure. Opening nothing without a reason, or throwing, also leaves a placeholder; a throw is the plugin's failure.
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
the window being restored and is placed, not shown, and a session's waits, `NOT_STARTED`, until its tab is shown; a
reason the restorer gives is kept in `declinedRestores()`, never in `failures()`:

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
    ("""                // A plugin's content is a content tab of its window (workspace tabs spec 14), placed as asked: in a new child
                // window of its own, opened for it, the owner's window untouched.
""", """                // A saved tab being restored comes back in the window being restored, whatever it named (workspace tabs spec 13).
                var slot = host.workspace.takeSlot(context);
                if (slot.isPresent()) { owner = context.terminals.windowHandle(slot.get().window.id); placement = dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP; }
                // A plugin's content is a content tab of its window (workspace tabs spec 14), placed as asked: in a new child
                // window of its own, opened for it, the owner's window untouched.
""", 1),
    ("""                contents.add(entry);entry.select();
                return entry;""",
     """                contents.add(entry);
                // A restored tab is placed, not shown: Jasper shows it where it was saved.
                if (slot.isPresent()) slot.get().taken = entry.tab; else entry.select();
                return entry;""", 1)])
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
""", 1)])
edit(T + "FakeTerminals.java", [(
    """            case OpenRequest.Session session -> {
                workspace.openRequests.add("session-tab|" + target.id + "|" + session.spec().title());
                FakeWorkspace.Tab tab = workspace.addTab(target, session.spec().title());
                FakeWorkspace.Pane pane = workspace.addPane(tab, sessionInfo(session));
                workspace.sessions.connect(context, pane, session.spec());
                yield new Pane(pane.id, tab.id);
            }""",
    """            case OpenRequest.Session session -> {
                // A session a restorer opens is the saved tab: it comes back in the window being restored and waits, not
                // started, until its tab is shown (workspace tabs spec 13).
                var slot = workspace.takeSlot(context);
                FakeWorkspace.Window into = slot.map(found -> found.window).orElse(target);
                workspace.openRequests.add("session-tab|" + into.id + "|" + session.spec().title());
                FakeWorkspace.Tab tab = workspace.addTab(into, session.spec().title());
                FakeWorkspace.Pane pane = workspace.addPane(tab, sessionInfo(session));
                if (slot.isPresent()) { slot.get().taken = tab; workspace.sessions.hold(context, pane, session.spec()); }
                else workspace.sessions.connect(context, pane, session.spec());
                yield new Pane(pane.id, tab.id);
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
     * @return the tab the restorer opened, content or terminal; empty when the plugin is not running or has no restorer for
     *     the kind, or its restorer declined (recorded in {@link #declinedRestores()}), threw (recorded in
     *     {@link #failures()}) or opened nothing, where Jasper shows a placeholder
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
            return java.util.Optional.ofNullable(slot.taken).map(tab -> tab.id);
        } finally {
            workspace.slot = null;
        }
        if (declined != null && declined.isPresent()) declines.add(pluginId + " " + state.kind() + ": " + declined.get());
        return java.util.Optional.ofNullable(slot.taken).map(tab -> tab.id);
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
slot of the saved tab being rebuilt, which `open` and a session's terminal tab take while its id is free; a restore asked
for while one runs, or for an id already open, says why instead of running; `HostedUi` registers a plugin's restorer (its
code runs contained: a reason it returns is the placeholder's, with no incident, and what it throws is the placeholder's
reason and the plugin's failure) and passes a content tab's state on; `HostedSessions` passes a session's:

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
            // A saved tab being rebuilt (workspace tabs spec 13): the window being restored places it with the others.
            var restored = new Tab(this, slot.id, Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
            tabs.add(restored);
            restored.window(slot.window);
            slot.taken = restored;
            return restored;
        }
        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        WorkspaceHost host = owner != null ? hosts.get(owner) : null;""", 1),
    ("""    /** Disposes the tab at once, without asking a guard, and takes it out of its window. */""",
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

    /** Tells {@code listener} whenever a tab's restore state changes (workspace tabs spec 13). */
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
        if (taken != null) return new Restored(Optional.of(taken), "");
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

    /** Disposes the tab at once, without asking a guard, and takes it out of its window. */""", 1)])
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
                    // A reason the restorer gives is expected, and no failure; what it throws is the plugin's failure.
                    var said = new java.util.concurrent.atomic.AtomicReference<Optional<String>>(Optional.empty());
                    Throwable thrown = containment.attempt(pluginId, "tab restorer", () -> {
                        Optional<String> declined = restorer.restore(new dev.jasper.sdk.ui.RestoreRequest(terminals.windowHandle(saved.window()),
                            saved.title(), new dev.jasper.sdk.RestoreState(saved.state().kind(), saved.state().state())));
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
        // A plugin's session opened while its saved tab is rebuilt here takes that tab's place (workspace tabs spec 13).
        Optional<TabId> saved = requestOrNull == null ? Optional.empty() : registry.slot(requestOrNull.providerId(), id);
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

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.contributions.TabRegistryTest' --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.HostedSessionsTest' --continue`
Expected: PASS: `RestoreStateTest`, the three contract tests in `FakeContractTest` and `AppContractTest`, and
`aRestorerRebuildsASavedTabWithItsIdAndWindowOrSaysWhyNot`.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app
git commit -F - <<'EOF'
Workspace tabs: restore state and restorers

ContentSpec and SessionSpec carry how a plugin's tab comes back,
ContentTab.setRestore changes it, and Windows.restorer registers the
restorer that rebuilds it. The tab registry keeps each plugin's
restorers and gives the tab a restorer opens the saved id and window;
the fakes and the contract suite restore through the same SDK.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Plugins' tabs saved and restored, and placeholders

Spec 9.2 and 13: a plugin's tab is saved while its plugin restores its kind (Rulings 3) and comes back through its
restorer in its saved place; a tab whose plugin does not run, or whose restorer declines or fails, comes back as a
placeholder that says why (not installed, turned off, waiting for review, could not start, or the restorer's reason),
with Close and, while the plugin runs, Retry, both acting in whatever window holds it then (Rulings 5, 6 and 7). A
workspace that would be too large to read back leaves out plugins' tabs, largest first (Rulings 16). Plan 6a's test of
a child window left out for holding only a plugin's tab now expects the window back, holding its placeholder.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/PlaceholderContent.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/PluginTabRestoreTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Tab.java`, `persistence/WorkspaceFile.java`,
  `workspace/WorkspaceView.java`, `workspace/WindowContent.java`, `workspace/WindowManager.java`,
  `plugins/PluginRuntime.java`, `application/JasperApplication.java`, `docs/design/theme-engine/migration.csv` (one row)
- Test: `workspace/WorkspaceRestoreTest.java`, `plugins/PluginRuntimeTest.java`,
  `application/JasperApplicationWorkspaceTest.java`

**Interfaces:**
- Consumes: Task 1's `Tab.restore()`, `TabRegistry.restore`, `restores`, `onRestoreChanged`, `Restoring`, `Restored`,
  `TabRestore`; plan 6a's `WindowContent.restore`, `mount`, `WindowManager.snapshot`, `changed`, `open(…, prepare)`;
  plan 4b's `TabRegistry.APP_OWNER`.
- Produces: `Tab.Kind.PLACEHOLDER`; package `final class PlaceholderContent(Content saved, String why, boolean
  retryable, Runnable onRetry, Runnable onClose, Runnable onShown)` (`saved()`, `reason()`, `retryable()`,
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
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.TabRestore;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
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

/** Workspace tabs spec 9.2 and 13: a plugin's saved tab comes back through its restorer, or as a placeholder that says why. */
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

    private static final WorkspaceFile.TabRecord.Content NOTE =
        new WorkspaceFile.TabRecord.Content("todo.md", NOTES, "note", Map.of("name", "todo"));

    /** The main window holding a terminal tab, then {@code record}'s tab, selected, in the same group. */
    private static WorkspaceFile.Snapshot mainWith(TabId shell, TabId saved, WorkspaceFile.TabRecord record) {
        Group group = new Group(GroupId.random(), List.of(shell, saved), Optional.of(saved));
        return new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            new Layout(group, group.id(), Optional.empty()),
            Map.of(shell, new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), ""), saved, record))));
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
            Tab opened = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), null, new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
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

    @Test void aPlaceholderMovedToAnotherWindowRetriesAndClosesThere() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            windows.model.tabs().open(NOTES, "note:todo", restoring.title(), null, new javax.swing.JPanel());
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow child = windows.manager.moveToNewWindow(placeholder).orElseThrow();
            assertThat(placeholder.window()).isEqualTo(child.id());
            works[0] = true;
            ((PlaceholderContent) placeholder.content()).retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(child.content().selectedTab().kind()).as("rebuilt in the window that held it").isEqualTo(Tab.Kind.HOSTED);
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aPlaceholderInAChildWindowClosesFromTheWindowThatHoldsItNow() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow child = windows.manager.moveToNewWindow(placeholder).orElseThrow();
            ((PlaceholderContent) placeholder.content()).closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(child.closed()).as("its last tab closed").isTrue();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aWorkspaceTooLargeToReadBackLeavesOutPluginsTabsLargestFirst() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
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

    @Test void aPluginsTabWithoutRestoreStateOrRestorerIsNotSavedAndAppOwnedTabsNeverAre() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), "")), Map.of(NOTES, "Notes"));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab plain = windows.model.tabs().open(NOTES, "plain", "Plain", main.id(), new javax.swing.JPanel());
            Tab unknown = windows.model.tabs().open(NOTES, "unknown", "Unknown", main.id(), new javax.swing.JPanel());
            unknown.restore(Optional.of(new TabRestore("unknown", Map.of())));
            Tab errors = windows.model.tabs().open(dev.jasper.app.contributions.TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.restore(Optional.of(new TabRestore("errors", Map.of())));
            windows.model.tabs().restorer(dev.jasper.app.contributions.TabRegistry.APP_OWNER, "errors", restoring -> Optional.empty());
            var tabs = windows.manager.snapshot().orElseThrow().main().tabs();
            assertThat(tabs).containsOnlyKeys(shell, saved);
            assertThat(plain.closed() || unknown.closed() || errors.closed()).isFalse();
        });
        windows.launch();
    }

    @Test void aSessionsTabComesBackThroughItsPluginAndConnectsWhenFirstShown() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        var record = new WorkspaceFile.TabRecord.Content("build-host", "dev.example.ssh", "ssh", Map.of("host", "build-host"));
        Group group = new Group(GroupId.random(), List.of(shell, saved), Optional.of(shell));
        WorkspaceFile.Snapshot snapshot = new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            new Layout(group, group.id(), Optional.empty()), Map.of(shell, new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), ""), saved, record))));
        windows(snapshot, Map.of("dev.example.ssh", "SSH"));
        Queue<SessionAttempt> attempts = new ArrayDeque<>();
        edt(() -> windows.model.tabs().restorer("dev.example.ssh", "ssh", restoring -> {
            var request = new SessionRequest("dev.example.ssh", restoring.title(), false, attempts::add, Runnable::run, null,
                Optional.of(restoring.state()));
            windows.manager.window(restoring.window()).orElseThrow().content().openTab(HOME, request);
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab session = main.content().tabs().get(1);
            assertThat(session.id()).isEqualTo(saved);
            assertThat(session.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(attempts).as("not shown yet: it does not connect").isEmpty();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(saved)).as("saved again through its plugin")
                .isEqualTo(new WorkspaceFile.TabRecord.Content(session.title(), "dev.example.ssh", "ssh", Map.of("host", "build-host")));
            main.content().selectTab(session);
            assertThat(attempts).as("shown, it connects").hasSize(1);
        });
        windows.launch();
    }
}
```

The runtime says which plugins run; plan 6a's child window comes back holding its placeholder; through the application,
a saved tab of a plugin that does not run is a placeholder:

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
# Plan 6a left a child window holding only a plugin's tab out; with placeholders it comes back holding one.
edit(T + "workspace/WorkspaceRestoreTest.java", [(
    """    @Test void aChildWindowWithNothingToRestoreIsLeftOut() throws Exception {""",
    """    @Test void aChildWindowHoldingAMissingPluginsTabComesBackWithItsPlaceholder() throws Exception {""", 1), (
    """            windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(windows.events).doesNotContain("show child");
""", """            windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).singleElement().satisfies(child ->
                assertThat(child.content().tabs()).extracting(Tab::id, Tab::kind).containsExactly(org.assertj.core.groups.Tuple.tuple(document, Tab.Kind.PLACEHOLDER)));
""", 1)])
edit(T + "application/JasperApplicationWorkspaceTest.java", [(
    """    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {""",
    """    @Test void aSavedTabOfAPluginThatDoesNotRunComesBackAsAPlaceholder() throws Exception {
        var shell = dev.jasper.app.layout.TabId.random();
        var note = dev.jasper.app.layout.TabId.random();
        var group = new dev.jasper.app.layout.Group(dev.jasper.app.layout.GroupId.random(), List.of(shell, note), java.util.Optional.of(note));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN,
            java.util.Optional.empty(), 0, new dev.jasper.app.layout.Layout(group, group.id(), java.util.Optional.empty()),
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
        }
    }

    @Test void theWarmUpNeverReadsOrWritesTheWorkspace() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `cannot find symbol` for `PlaceholderContent`, `Tab.Kind.PLACEHOLDER`, `PluginState`, `standing(…)`
and `encodedBytes`.

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
PY
```

- [ ] **Step 4: A window restores plugins' tabs**

`Tab.Kind` gains `PLACEHOLDER`; a window's restore asks `savedTabs` for each plugin's tab; Retry puts the rebuilt tab in
the placeholder's place; a placeholder takes the theme as a plugin's tab does:

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
     * the saved id, else it is a placeholder; empty leaves it out. The window manager gives it.
     */
    java.util.function.BiFunction<TabId, WorkspaceFile.TabRecord.Content, Optional<Tab>> savedTabs = (tabId, saved) -> Optional.empty();
""", 1),
    ("""            for (TabId saved : record.layout().tabs()) {
                if (record.tabs().get(saved) instanceof WorkspaceFile.TabRecord.Terminal terminal) restored.add(restoredTerminal(saved, terminal));
                else layout = layout.remove(saved);
            }
""", """            for (TabId saved : record.layout().tabs()) {
                Optional<Tab> tab = switch (record.tabs().get(saved)) {
                    case WorkspaceFile.TabRecord.Terminal terminal -> Optional.of(restoredTerminal(saved, terminal));
                    case WorkspaceFile.TabRecord.Content content -> savedTabs.apply(saved, content);
                };
                if (tab.isPresent()) restored.add(tab.get()); else layout = layout.remove(saved);
            }
""", 1),
    ("""    /** A saved terminal tab whose shell waits until its tab is first shown (workspace tabs spec 13). */""",
     """    /**
     * Retry on a placeholder (workspace tabs spec 9.2): its plugin's restorer runs again, and the tab it opens takes the
     * placeholder's place in its group, selected; a placeholder that fails again says why.
     */
    void retry(Tab placeholder) {
        if (closed || !(placeholder.content() instanceof PlaceholderContent saved) || !workspace.contains(placeholder)) return;
        Optional<Tab> rebuilt = savedTabs.apply(TabId.random(), saved.saved());
        if (rebuilt.isEmpty()) return;
        if (rebuilt.get().content() instanceof PlaceholderContent again) {
            saved.failed(again.reason(), again.retryable());
            rebuilt.get().close();
            return;
        }
        GroupId group = workspace.model().groupOf(placeholder.id()).orElseThrow();
        int index = workspace.model().group(group).orElseThrow().tabs().indexOf(placeholder.id());
        Runnable replace = () -> {
            mount(rebuilt.get());
            workspace.openAt(rebuilt.get(), group, index);
            placeholder.close();
        };
        if (terminals != null) terminals.atomically(replace); else replace.run();
    }

    /** A saved terminal tab whose shell waits until its tab is first shown (workspace tabs spec 13). */""", 1),
    ("            if (tab.kind() == Tab.Kind.HOSTED) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));",
     "            if (tab.kind() != Tab.Kind.TERMINAL) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));", 1)])
PY
```

- [ ] **Step 5: The window manager saves and rebuilds them**

The snapshot saves a plugin's tab, content or session, while its plugin restores that kind, and a placeholder as it was
saved; an app-owned tab never; a snapshot too large for the codec's limit leaves out tabs, plugins' first and largest
first. `savedTab` rebuilds through the registry or makes the placeholder, whose Retry and Close find the window that holds
it when pressed and which asks how its plugin stands whenever it shows and on Retry; a restore state that changes is saved
as a layout change is; the application says how each plugin stands:

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
    ("""    /** The file's text: the windows in order, each window's nodes parents first, then its tabs in workspace order. */
    static String encode(Snapshot snapshot) {""",
     """    /**
     * How many bytes {@link #write} writes for {@code snapshot}: more than {@link #MAX_BYTES} and the next read sets the file
     * aside (workspace plan 6b Rulings 16).
     *
     * @param snapshot the windows
     * @return the encoded size in UTF-8 bytes
     */
    public static int encodedBytes(Snapshot snapshot) {
        return encode(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** The file's text: the windows in order, each window's nodes parents first, then its tabs in workspace order. */
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
            content.savedTabs = (tabId, saved) -> Optional.of(savedTab(content, tabId, saved));
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
     * tabs; each is logged. A child window left with none is left out.
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
            LOG.log(System.Logger.Level.WARNING, "Not saving the tab " + window.tabs().get(largest).title()
                + ": the saved windows would pass workspace.toml's size limit");
            Layout layout = window.layout().remove(largest);
            var tabs = new HashMap<>(window.tabs());
            tabs.remove(largest);
            if (window.role() == WorkspaceFile.Role.CHILD && layout.isEmpty()) windows.remove(holder);
            else windows.set(holder, new WorkspaceFile.WindowRecord(window.role(), window.bounds(), window.extendedState(), layout, tabs));
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
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Workspace tabs: plugins' tabs come back, or a placeholder says why

A plugin's tab is saved while its plugin restores that kind and is
rebuilt in its place by the plugin's restorer. A tab whose plugin does
not run, or whose restorer declines or fails, comes back as a placeholder
that says why, with Close and, while the plugin runs, Retry, in whatever
window holds it; it is saved again as it was. App-owned tabs are never
saved, and a workspace too large to read back leaves out plugins' tabs.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The Editor reopens its files

Spec 13: "the Editor reopens files (an unsaved untitled buffer is not kept)". A document's tab gives its document's key
(`document` restorer, Rulings 8); the restorer reopens it through the same reopeners Recent Files uses, local files
itself and other plugins' documents (Remote's `remote:`) through theirs. A document on another host waits until its tab
is first active or Load is pressed and then reads asking nothing; a key nothing opens declines with why (Rulings 5).

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `ui/EditorTab.java`,
  `session/DocumentSession.java`, `api/EditorDocument.java`, `EditorPlugin.java`, `src/main/resources/plugin.toml`
  (0.11.0)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`, `ui/EditorTabTest.java`

**Interfaces:**
- Consumes: Task 1's `ContentSpec.withRestore`, `ContentTab.setRestore`, `Windows.restorer`, `RestoreRequest`,
  `RestoreState.of`; the Editor's `Reopeners.resolve(String)`.
- Produces: `EditorWindow.RESTORE_KIND` (`"document"`) and `RESTORE_KEY` (`"key"`); `public boolean
  EditorWindow.openRestored(EditorDocument document, WindowOwner owner)`; `public void DocumentSession.defer()` and
  `boolean deferred()`; Editor API `default CompletableFuture<Content> EditorDocument.readQuietly(long limitBytes)`.

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
            assertThat(host.declinedRestores()).singleElement().asString().contains("no running plugin opens remote: documents");
            assertThat(host.failures()).as("an expected outcome, no failure").isEmpty();
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
            if (document.isEmpty()) return Optional.of("no running plugin opens " + key.substring(0, key.indexOf(':') + 1) + " documents");
            return window().openRestored(document.get().apply(request.window()), request.window())
                ? Optional.empty() : Optional.of("the file is open in another tab");
        });
        PluginMenu file = context.menus().standard(StandardMenu.FILE);""", 1)])
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.10.0"', 'version = "0.11.0"', 1)])
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
untitled document that was never saved is not kept. Editor 0.11.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Remote's SSH tabs come back

Spec 13: "Remote chooses between reconnecting and a Reconnect state". An SSH tab's session carries its host, the host's
name and its folder (`ssh` restorer); the restored tab waits until it is first shown, as every restored terminal tab
does, and then Remote reconnects when signing in asks nothing, after any connection in progress in its window, and
otherwise leaves it not connected with Reconnect; a missing host declines (Rulings 9). The pane says Reconnect rather
than Retry for a restored session (Rulings 13). Remote's remote documents reopen before its hosts have loaded and read
quietly only when signing in asks nothing (Rulings 17).

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/SavedRemoteDocument.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, `edit/RemoteEditing.java`,
  `edit/RemoteDocument.java`, `src/main/resources/plugin.toml` (0.14.0);
  `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`
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
  silent)` and its `readQuietly`; package `final class SavedRemoteDocument implements EditorDocument`.

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
and the restored pane offers Retry.

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
     "    private final Map<UUID, java.util.ArrayDeque<Runnable>> waitingConnects = new HashMap<>();\n", 1),
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
        if (attempts.stream().noneMatch(attempt -> attempt.window.id().equals(window.id()))) { connect(pending, hostId, folder); return; }
        waitingConnects.computeIfAbsent(window.id(), id -> new java.util.ArrayDeque<>()).add(() -> connectWhenFree(pending, hostId, folder));
    }

    /** The connection in progress in {@code window} ended: the next restored SSH tab waiting there connects. */
    private void nextWaitingConnect(UUID window) {
        var waiting = waitingConnects.get(window);
        if (waiting == null) return;
        Runnable next = waiting.poll();
        if (waiting.isEmpty()) waitingConnects.remove(window);
        if (next != null) next.run();
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
    ("""                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst());""",
     """                // Jasper restores its windows while the saved hosts still load: a remote document finds them when first read
                // (workspace tabs spec 13), and its first read after a restore asks nothing unless signing in would not.
                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst(),()->hostsLoaded,ui,this::signsInAlone);""", 1)])
edit("plugins/remote/src/main/resources/plugin.toml", [('version = "0.13.0"', 'version = "0.14.0"', 1)])
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
     * Reads as {@link #read} does when signing in to the route asks nothing; otherwise fails at once and says so, so a
     * restored tab never asks before the user does (workspace tabs spec 13). Retry then reads with {@link #read}.
     */
    @Override public CompletableFuture<Content> readQuietly(long limitBytes) {
        if (silent.test(identity)) return read(limitBytes);
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
git add -A plugins/remote jasper-app
git commit -F - <<'EOF'
Workspace tabs: Remote's SSH tabs come back

An SSH tab is saved with its host and folder; restored, it waits until
it is shown and then reconnects when signing in asks nothing (the SSH
agent, or the unlocked Vault), one tab after another in a window, and
otherwise shows Not connected with Reconnect; a missing host declines.
Remote documents reopen before the saved hosts have loaded and read
quietly. Remote 0.14.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The Vault's manager and the sample's About come back

Spec 14's plugin list: the two plugin windows plan 4b made tabs come back with the windows (Rulings 10, 11). The manager
comes back as the Vault is, locked or not, and asks nothing; the sample's About shows the guide's pattern: one method
opens the tab for its action and for its restorer.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerTab.java`, `VaultPlugin.java`,
  `src/main/resources/plugin.toml` (0.7.0); `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`,
  `src/main/resources/plugin.toml` (0.4.0); `docs/plugin-authoring.md` (the `pluginpanels` snippet)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`,
  `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Consumes: Task 1's `ContentSpec.withRestore`, `Windows.restorer`, `RestoreState`.
- Produces: the restorer kinds `manager` (the Vault) and `about` (the sample).

- [ ] **Step 1: Write the failing tests**

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
edit("plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java", [(
    """    @Test void stoppingDuringUnlockClosesThePromptAndRejectsLateOpen() throws Exception {""",
    """    /** Workspace tabs spec 13: the manager's tab comes back through the Vault's restorer, locked, and asks nothing. */
    @Test void theManagerTabComesBackAsTheVaultIsAndAsksNothing() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            host.invoke(VaultPlugin.OPEN, window, null);
            var manager = host.contentTabs().getFirst();
            var state = new dev.jasper.sdk.RestoreState("manager", Map.of());
            assertThat(host.restoreState(manager)).contains(state);
            manager.close();
            host.invoke(VaultPlugin.LOCK, window, null);
            assertThat(host.restoreTab("dev.jasper.vault", window, "Credential Vault", state)).isPresent();
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(host.contentTabs().getFirst().window().id()).isEqualTo(window);
            assertThat(plugin.currentUnlock()).as("restoring asks for nothing").isNull();
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }); }

    @Test void stoppingDuringUnlockClosesThePromptAndRejectsLateOpen() throws Exception {""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.VaultPluginTest' :jasper-plugin-sample:test --continue`
Expected: FAIL: neither tab gives restore state.

- [ ] **Step 3: The two restorers**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
V = "plugins/vault/src/main/java/dev/jasper/vault/"
edit(V + "ui/VaultManagerTab.java", [(
    """            tab = context.windows().openTab(new ContentSpec(KEY, "Credential Vault"), owner, shown.view());""",
    """            // Workspace tabs spec 13: the manager comes back with the windows; its key is all it needs.
            tab = context.windows().openTab(new ContentSpec(KEY, "Credential Vault").withRestore(new RestoreState(KEY, java.util.Map.of())),
                owner, shown.view());""", 1),
    ("import dev.jasper.sdk.plugin.PluginContext;\n", "import dev.jasper.sdk.RestoreState;\nimport dev.jasper.sdk.plugin.PluginContext;\n", 1)])
edit(V + "VaultPlugin.java", [(
    "        managerTab = new VaultManagerTab(context, lock, manager, service, this::managerStatus, this::showGenerator, layout);\n",
    "        managerTab = new VaultManagerTab(context, lock, manager, service, this::managerStatus, this::showGenerator, layout);\n"
    "        // Workspace tabs spec 13: a saved manager tab comes back as the Vault is, locked or not, and asks nothing.\n"
    "        context.windows().restorer(VaultManagerTab.KEY, request -> {\n"
    "            managerTab.show(request.window(), Optional.empty());\n"
    "            return Optional.empty();\n"
    "        });\n", 1)])
edit("plugins/vault/src/main/resources/plugin.toml", [('version = "0.6.0"', 'version = "0.7.0"', 1)])
PY
```

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
edit("plugins/sample/src/main/resources/plugin.toml", [('version = "0.3.0"', 'version = "0.4.0"', 1)])
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

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test :jasper-plugin-sample:test :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest' --continue`
Expected: PASS (the guide's `pluginpanels` snippet equals the sample's source).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/vault plugins/sample docs/plugin-authoring.md
git commit -F - <<'EOF'
Workspace tabs: the Vault's manager and the sample's About come back

Both tabs are saved with their restore state and come back through
their plugin's restorer; the manager comes back locked or not, asking
nothing. Vault 0.7.0, sample 0.4.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, STATUS, the THD hand-off and the whole build

**Files:**
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/configuration.md`,
  `docs/editor.md`, `docs/remote.md`, `docs/credential-vault.md`, `docs/STATUS.md` (the entry at the top), this plan's
  status banner (line 3)

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentences true (AGENTS.md "Documentation edits"): the SDK README's 0.21
section and package table gain restore state and restorers, the authoring guide shows how a content tab and a session
come back, the SDK architecture names the slot and the placeholder, and the user guides say which tabs come back:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan6b"); from edits import edit
R = "jasper-sdk/README.md"
edit(R, [
    ("`ContentTab`, `TabPlacement`, `ContentSpec`,", "`ContentTab`, `TabPlacement`, `ContentSpec`, `TabRestorer`, `RestoreRequest`,", 1),
    ("| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `Capabilities`, `MissingCapabilityException` |",
     "| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription`, `WindowOwner`, `RestoreState`, `Capabilities`, `MissingCapabilityException` |", 1),
    ("""its pane's `info().state()` is `SessionState.NOT_STARTED` until the tab is first shown, and text sent to it is dropped;
then its shell starts as a new tab's does.
""", """its pane's `info().state()` is `SessionState.NOT_STARTED` until the tab is first shown, and text sent to it is dropped;
then its shell starts, or its session connects, as a new tab's does.

A plugin's tab comes back when the plugin says how: a `RestoreState` (`dev.jasper.sdk`: a restorer kind and at most 32
strings, which Jasper writes into `workspace.toml`, so never a secret) in its `ContentSpec` or `SessionSpec`
(`withRestore`), or later through `ContentTab.setRestore`, and a restorer for that kind registered in `start()` with
`Windows.restorer(kind, restorer)`. The restorer gets a `RestoreRequest` (the window being restored, the saved title, the
state) and opens the tab the way the plugin opens it, before it returns `Optional.empty()`; the first tab it opens is the
saved tab, whatever window or placement it names, and Jasper puts it where it was saved. When the tab cannot come back
for a reason the plugin expects (its file or host is gone), the restorer returns that reason instead, which is no
failure. A tab whose plugin is not installed, turned off, waiting for review or failed to start, has no restorer for its
kind, or whose restorer declines, throws or opens nothing comes back as a placeholder that says why, with Close and,
while the plugin runs, Retry. In the testkit `FakePluginHost.restoreState` reads the restore state a tab gives and
`restoreTab` runs a plugin's restorer as Jasper does: the tab is placed in the window being restored, not shown, and a
session's tab waits, `NOT_STARTED`, until it is shown; `declinedRestores()` lists the reasons restorers gave.
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
places it where it was saved. When it cannot come back now (its file is gone), return why instead,
`Optional.of("its file is gone")`: its placeholder says so with Retry, and nothing reports a failure. A restore state is
saved in `workspace.toml`: keep it small, and never put a secret in it. A tab without one is not saved, and one whose
restorer throws or opens nothing comes back as a placeholder with Retry too.
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
registry keeps each plugin's restorers (`Windows.restorer`). `WindowManager.snapshot()` saves a plugin's tab while its
plugin has a restorer for its kind, never an app-owned tab, and leaves out tabs, plugins' first, when the file would pass
`WorkspaceFile.MAX_BYTES`; restoring, `TabRegistry.restore` runs the restorer with a slot set (one at a time), and the
first tab the plugin opens meanwhile, a content tab in `TabRegistry.open` or a session's terminal tab in
`WindowContent.openTerminal`, takes the saved id, while it is free, and the window being restored, which places it. A
reason the restorer returns is the placeholder's and no incident; what it throws is both. Otherwise the tab is a
`Tab.Kind.PLACEHOLDER` (`workspace.PlaceholderContent`) that says why, from `WindowManager.PluginState` (the
application's reading of `PluginRuntime.standing`), saved again as it was; its Retry and Close act in the window that
holds it then, and it asks how its plugin stands whenever it shows and on Retry.
""", 1)])
edit("docs/configuration.md", [("""others when first shown. Scrollback and running programs do not come back. A file Jasper cannot read is kept as""",
    """others when first shown. A plugin's tab comes back when its plugin can restore it (the Editor's files, Remote's SSH
tabs, the Vault's manager), and otherwise as a placeholder that says why (the plugin is not installed, turned off,
waiting for your review or could not start, or it could not restore that tab), with Close and, while its plugin runs,
Retry.
Scrollback and running programs do not come back. A file Jasper cannot read is kept as""", 1)])
edit("docs/editor.md", [("""indentation and Wrap (click to toggle soft wrap for the tab).
""", """indentation and Wrap (click to toggle soft wrap for the tab).

When Jasper restores its windows, each tab comes back with its file: a local file is read at once; a remote one, through
Remote, waits until its tab is first active or you press Load, and then reads only if signing in asks nothing (an open
connection, the SSH agent, or the unlocked Vault); otherwise it says so, and Retry signs in. An untitled tab that was
never saved does not come back.
""", 1)])
edit("docs/remote.md", [("""shows the disconnected banner and Reconnect makes a new connection. The status bar shows how many
SSH sessions are open; clicking it toggles the panel.""", """shows the disconnected banner and Reconnect makes a new connection. When Jasper restores its windows, each SSH tab
comes back to its host and folder and waits until it is first shown; then it connects at once when signing in asks
nothing (every host on its route signs in through the SSH agent, or through the Vault while it is unlocked), after any
connection in progress in that window, and otherwise it shows Not connected with Reconnect. The status bar shows how many
SSH sessions are open; clicking it toggles the panel.""", 1)])
edit("docs/credential-vault.md", [("""and an optional device binding. Later use unlocks the vault and opens its manager in a tab. Creating and unlocking""",
    """and an optional device binding. Later use unlocks the vault and opens its manager in a tab, which comes back, locked
or not, when Jasper restores its windows. Creating and unlocking""", 1)])
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
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). SDK 0.21.0 (plan 6a's bump) gains
`dev.jasper.sdk.RestoreState` (a restorer kind and at most 32 strings, 4096 characters in all), restore state on
`ContentSpec` and `SessionSpec` (`withRestore`), `ContentTab.setRestore`, `Windows.restorer(kind, TabRestorer)` (a
restorer returns empty, or why the tab cannot come back, which raises no incident) and `RestoreRequest`; the testkit
gains `FakePluginHost.restoreState`, `restoreTab` (the restored tab takes the window being restored, placed, not shown;
a session waits `NOT_STARTED` until shown) and `declinedRestores`, and `PluginContractTest` checks restorers on the fake
and on the application. In the application a tab carries its restore state (`Tab.restore()`,
`layout.TabRestore`); `TabRegistry` keeps each plugin's restorers and, while one runs (one at a time), a slot whose
saved id (while free) and window the next tab that plugin opens takes, a content tab or a session's terminal tab,
unshown, for the window being restored to place. `WindowManager` saves a plugin's tab as `TabRecord.Content` while its plugin restores that kind (never an
app-owned tab), leaving out tabs, plugins' first, if the file would pass `WorkspaceFile.MAX_BYTES`, and otherwise
rebuilds it, or makes a `Tab.Kind.PLACEHOLDER` tab (`workspace.PlaceholderContent`) that says why from how its plugin
stands (`PluginRuntime.standing`, given as `WindowManager.PluginState`: not installed, turned off, waiting for review,
could not start, or "<name> could not restore this tab: <why>." with Retry), asks again when it shows and on Retry, and
acts in whatever window holds it; a placeholder is saved again as it was. The Editor (0.11.0) saves a document's key and
reopens it through its reopeners (`openRestored`): a remote document waits until its tab is first active or Load, then
reads with the Editor API's new `EditorDocument.readQuietly`, asking nothing. Remote (0.14.0) reopens remote documents
before its hosts have loaded (`SavedRemoteDocument`) and saves an SSH tab's host and folder; when the tab is first shown
it reconnects, one tab after another per window, if signing in asks nothing (an open connection, the agent, or the
unlocked Vault), else shows Not connected with Reconnect; a missing host declines. The Vault's manager (0.7.0) and the
sample's About (0.4.0) come back. Rulings (plan, "Rulings"): <one clause each, 1 to 17>. Deviations: <none, or each with its reason>.
THD Sourcing Crypto (`~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at `3740e4e`): SDK
0.21.0 is the series' last bump and the one to rebuild it against, once, with the jars `sdkRelease` publishes; plan
4b's entry lists what 0.18, 0.19 and 0.20 need, and its `plugin.toml` and `PluginPackagingTest.java:49` take
`sdk = ">=0.21.0, <0.22"` and `build.gradle.kts:8` and `README.md:28` name 0.21.0; 0.21 needs nothing more (it names
`SessionState.RUNNING` in one fixture and switches over no `SessionState`, and it opens only a dialog, which is not
saved). `./gradlew check --continue`: **<counts>**. GUI checks for the user: an Editor tab, a Remote SSH tab (one host
through the agent, one with a password in the Vault, locked), the Vault's manager and, with the sample on, its About,
spread over the main window and a child window; quit and start: each comes back in its place, the agent's SSH tab
connects when shown, the other says Not connected with Reconnect; turn the sample off in Manage Plugins, restart: its
About is a placeholder without Retry, and Close closes it; turn Remote off with a remote file open in the Editor,
restart: that tab is a placeholder with Retry ("Editor could not restore this tab: no running plugin opens remote:
documents."), and turning Remote on again and restarting brings it back; a remote file open in the Editor comes back
without asking for anything, loads when its tab is clicked if the agent or the unlocked Vault signs in, and otherwise
says so with Retry; two SSH tabs restored side by side in one window both connect; an Editor tab whose file was deleted
comes back as the Editor's failed read. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add jasper-sdk/README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for plugins' tabs coming back

The SDK README and the authoring guide describe restore state and
restorers, the architecture names the slot and the placeholder, and
the user guides say which tabs come back; STATUS records plan 6b with
its rulings, the THD Sourcing Crypto hand-off and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 9.2's placeholder (Task 2); 13's content tabs: rebuilt by their plugin's restorer from the state given
  when the tab opened (Tasks 1 and 2), the Editor reopening files and not keeping an unsaved untitled buffer (Task 3),
  Remote choosing between reconnecting and a Reconnect state (Task 4), a tab whose plugin is missing or whose restorer
  fails becoming a placeholder (Task 2); 14's `ContentSpec`/`SessionSpec` restore state (as amended in `27874bf7d`) and
  `Windows.restorer` rows (Task 1), its
  plugin list for this plan: Remote, Vault (manager tab), sample (About tab), Editor (restorer) (Tasks 3 to 5), the
  testkit fakes and the contract suites (Task 1), the THD hand-off (Task 6); 15's disabled-plugin row (Rulings 7, Task 2's
  placeholder); 17's SDK line: contract tests on the app and the testkit (Task 1).
- Every quoted anchor was matched, at its stated count, in a replay of plans 1, 2, 3a, 3b, 4a, 4b and 6a and of Tasks
  1-6 in order; nothing was compiled. The ledger was checked with a Python copy of `ThemeMigrationCoverageTest`'s
  pattern, and the documents' links and the `pluginpanels` snippet with a Python copy of `AppDocumentationTest`'s rules.
- Type consistency: `RestoreState`, `ContentSpec.withRestore`, `SessionSpec.withRestore`, `ContentTab.setRestore`,
  `TabRestorer` (returning `Optional<String>`), `RestoreRequest`, `Windows.restorer`, `FakePluginHost.restoreState`/
  `restoreTab`/`declinedRestores`, `FakeWorkspace.Slot`/`takeSlot`, `FakeSessions.hold`, `TabRestore`, `Tab.restore`,
  `TabRegistry.Restoring`/`Restored`/`restorer`/`restores`/`restore`/`slot`/`restored`/`onRestoreChanged`,
  `SessionRequest.restore`, `Tab.Kind.PLACEHOLDER`, `PlaceholderContent`, `WindowContent.savedTabs`/`retry`,
  `WorkspaceView.openAt`, `WindowManager.PluginState`/`plugins`, `PluginRuntime.Standing`/`standing`,
  `WorkspaceFile.MAX_BYTES`/`encodedBytes`, `EditorWindow.RESTORE_KIND`/`RESTORE_KEY`/`openRestored`,
  `DocumentSession.defer`/`deferred`, `EditorDocument.readQuietly`, `RemotePlugin.SSH_TAB`/`sshTab`/`restoreHost`,
  `SavedRemoteDocument` and the eight-argument `RemoteEditing` keep one name and one signature from the task that creates
  them to the last task that uses them.
