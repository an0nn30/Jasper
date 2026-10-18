# Workspace tabs 3b: the SDK's tab and pane break Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The plugin SDK models what plan 3a built: every tab of a Jasper window is a tab of the SDK, a terminal tab
holds one pane, a split is a terminal tab in a new group, a tab that moves between windows is reported once as a move,
and the Pane menu is part of the Tab menu; SDK 0.19.0 carries the break, and the testkit, the contract suite, the app's
registry and every in-repo plugin move with it.

**Architecture:** `TabHandle` gains `kind()` (`TERMINAL`, `CONTENT`) and `terminal()` in place of `panes()` and
`activePane()`; the app's `terminals.TabEntry` gains a kind and swaps its pane list for its one `terminal`, and
`workspace.WindowTerminals` lists every tab of its window, terminal and content, in workspace order. A move between
windows is reported by the window the tab arrives in: 3a's `Tab.moving()` becomes `Tab.movingFrom()`, the window the
registry is taking the tab out of, so the old window publishes no `TabClosed`, the new one publishes `TabMoved` instead
of `TabOpened`, and `TerminalBridge` turns it into the new `TerminalEvents.TAB_MOVED`; a window that announced an arrival
and then fails to show the tab takes the announcement back. The testkit's `FakeWorkspace` and the app's contract
`TerminalFixture` model the same rules, so `PluginContractTest` holds `FakePluginHost` and the
application to one contract. `StandardMenu.PANE` and `MenuTarget.Slot.PANE` fold into Tab, and `ExitPolicy.CLOSE_PANE`
becomes `ExitPolicy.CLOSE`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python
3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1, 2.6, 4, 9.4, 11, 14, 16, 17 and 18). The second half of section 18's plan 3:
[3a](2026-10-17-workspace-3a-tab-registry.md) built the tab registry and the workspace view and left the SDK's shape
alone; this plan changes it. Plans [1](2026-10-17-workspace-1-single-instance.md),
[2](2026-10-17-workspace-2-layout-model.md) and 3a execute first. Spec 14's rows for `TabPlacement`, the `ContentTab`
move API, `Windows.create`/`PluginWindow`/`WindowSpec`/`ShortcutContext.Kind.WINDOW`, panels in the main window only and
`WindowHandle.isMain()` are plan 4's; `ContentSpec` restore state and `Windows.restorer` are plan 6's.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`,
after plan 3a's last commit. Run every command from the worktree root. Line numbers are from `22b702456` ("Plan 3a: the
tab registry and the workspace view", on `main` `11e40192c`); plans 1 to 3a move some of them. Edits into files that
plan 3a creates or rewrites (`Tab`, `TabRegistry`, `WindowContent`, `WindowTerminals`, `TabMoveTest`, `TabRegistryTest`,
`TabMenuTest`, `WindowTerminalsTest`, the renamed `AppContractTest` and `WindowChrome` lines) quote 3a's text exactly as
its committed plan writes it; that code is not on disk while this plan is written. Every edit of this plan was replayed
on a scratch copy of `22b702456` after replaying plan 3a's scripts and files there, and every anchor matched; plans 1
and 2 change none of the files this plan edits (a replay of plans 1, 2 and 3a leaves them byte for byte the same). Every edit
script stops with a message when its quoted text is not found the stated number of times: re-find the text, adjust the
anchor, re-run, and record the change in this plan's banner; never edit around a missing anchor. Later tasks anchor on text
earlier tasks of this plan wrote, and quote it as those tasks leave it.

## Spec issues

None. Spec 14's SDK rows for plan 3 and 9.4's `TabEntry` change land here as the series brief divides them; nothing
contradicts the spec.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 4, 14): `TabHandle.panes()`, `TabHandle.activePane()`, `StandardMenu.PANE`, `MenuTarget.Slot.PANE`,
  `ExitPolicy.CLOSE_PANE` and `TabEntry`'s `panes`/`focusedPane` go outright. No deprecation, no shim, no migration and
  no "was removed" text; `KitCompatibility` refuses a plugin that still references a removed member, naming SDK 0.19.
  In-repo plugins, the testkit, the contract suite and the guides move in the same tasks.
- SDK versioning (the 0.17 and 0.18 bumps, Enterprise plan 3a): `JasperSdk.VERSION` is `0.19.0`; every bundled
  manifest, the example external plugin and every range the guides tell authors to declare say `>=0.19.0, <0.20`;
  `VersionRangeTest` lists 0.18 among the incompatible minors; plugin versions do not change (Rulings 1).
- Architecture: `dev.jasper.app.terminals` stays free of SDK types and takes nothing from Swing beyond
  `TerminalRegistry`'s EDT check (`SwingUtilities`); it already depends on `layout` and `lifecycle` after 3a. SDK types
  appear in the app only inside `dev.jasper.app.plugins`; `jasper-sdk` stays JDK-only.
  `verifyApplicationArchitecture`, `verifySdkArchitecture` and `verifyPluginArchitecture` are part of `check`.
- The theme-migration ledger: this plan creates and deletes no file, so `docs/design/theme-engine/migration.csv` gains no
  row. `ThemeMigrationCoverageTest` scans `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and every
  `plugins/*/src`; files without a row (`FakeTerminals`, `FakeTerminalsTest`, `FakeContractTest`, `ContractHarness`,
  `TabHandle`, `TerminalEvents`, `TabEntry`, `WindowEntry`, `TerminalEvent`, `TerminalBridge`, `MenuTarget`,
  `WindowTerminals`, `LookFixtures`) must not start matching its `CANDIDATE` pattern: no `new ArrayList` (or any
  `new <Name>List`, `…Panel`, `…View`, `…Window`), no Swing or AWT import and no word "appearance" in them. Write
  `new java.util.ArrayList<>()` where a test needs a list.
- Javadoc is part of `check` for `jasper-sdk` and `jasper-app` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&`
  in a comment, every `{@link}` resolves, every new public SDK member and enum constant has a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").

### The helper script

Create it once, before Task 1, in the scratch directory `build/plan3b/` (ignored by git; never commit it):

```bash
mkdir -p build/plan3b
cat > build/plan3b/edits.py <<'PY'
"""Exact edits that stop when an anchor is not found the stated number of times."""
import pathlib, re, sys

def _read(path):
    return pathlib.Path(path).read_text(encoding="utf-8")

def _write(path, text):
    pathlib.Path(path).write_text(text, encoding="utf-8")

def edit(path, pairs):
    """pairs: (old, new, count); each old must occur exactly count times."""
    text = _read(path)
    for old, new, count in pairs:
        found = text.count(old)
        if found != count:
            sys.exit(f"{path}: expected {count} of {old[:90]!r}, found {found}")
        text = text.replace(old, new)
    _write(path, text)

def span(path, start, end, new):
    """Replaces from the one occurrence of start through the first end after it, both included."""
    text = _read(path)
    if text.count(start) != 1:
        sys.exit(f"{path}: expected one {start[:90]!r}, found {text.count(start)}")
    i = text.index(start)
    j = text.find(end, i)
    if j < 0:
        sys.exit(f"{path}: no {end[:90]!r} after {start[:60]!r}")
    _write(path, text[:i] + new + text[j + len(end):])

def regex(path, pattern, replacement, count):
    text = _read(path)
    found = len(re.findall(pattern, text))
    if found != count:
        sys.exit(f"{path}: expected {count} of /{pattern}/, found {found}")
    _write(path, re.sub(pattern, replacement, text))

def comments(path, count):
    """Every "terminal window" on a comment line of path becomes "Jasper window"; count is how many there are."""
    lines = _read(path).split("\n")
    hits = 0
    for i, line in enumerate(lines):
        if line.lstrip().startswith(("*", "/**", "/*", "//")) and "terminal window" in line:
            hits += line.count("terminal window")
            lines[i] = line.replace("terminal window", "Jasper window")
    if hits != count:
        sys.exit(f"{path}: expected {count} comment mentions of 'terminal window', found {hits}")
    _write(path, "\n".join(lines))
PY
```

Scripts call it as `python3 -I - <<'PY'` with `import sys; sys.path.insert(0, "build/plan3b"); from edits import ...`. Java
text inside a script is a raw string (`r"""…"""`), so `\n` in Java source stays two characters.

## Review Focus

1. A plugin's content tab (an Editor document) is the active tab of the active window: `Terminals.activePane()` is
   empty, `WindowHandle.activeTab()` is that tab with an empty `terminal()`, and selecting the terminal tab again brings
   the pane back. Task 3: `PluginContractTest.aContentTabIsATabOfItsWindowWithNoTerminal` (both runtimes) and
   `WindowTerminalsTest.aPluginsTabIsListedOpenedMovedAndClosedLikeATerminalTab`.
2. A terminal tab moved to another window: its `PaneHandle` keeps its id, `pane.tab().window()` is the new window, and
   the tab and pane topics carry `TAB_MOVED` only: no `TAB_CLOSED` or `TAB_OPENED`, no `PANE_OPENED` or `PANE_CLOSED`. A
   window the move empties reports `WINDOW_CLOSED` before it, and a window opened for it `WINDOW_OPENED` before it, on
   both runtimes. Task 3: `PluginContractTest.aTabMovedToAnotherWindowKeepsItsPaneAndReportsOnlyTheMove` and
   `aPluginsContentTabOpensMovesAndClosesOnTheTabTopics`; `TabMoveTest.aMovedTabReportsToItsNewWindow`.
3. A window that announced a tab's arrival and then fails to show it: a moved tab is reported moving back (`TabMoved`
   there and back, no close or open) and is in the window it left, a later close reports `TabClosed` from that window,
   and a tab that failed to open is reported closed; `Tab.movingFrom()` is empty again. Task 3:
   `TabMoveTest.anArrivalItsWindowFailsToShowIsTakenBack`, `TabRegistryTest.aMoveTellsBothWindowsWhereTheTabComesFromUntilItEnds`.
4. A plugin's content tab moved to a separate window, then closed: `TAB_MOVED` names both windows, `TAB_CLOSED` names
   the separate one, and that window, empty, closes. Task 3:
   `PluginContractTest.aPluginsContentTabOpensMovesAndClosesOnTheTabTopics`.
5. A plugin built on 0.18 with an open range (`>=0.18`) that calls `TabHandle.panes()` or reads `ExitPolicy.CLOSE_PANE`
   or `StandardMenu.PANE` is refused at load with "rebuild it for SDK 0.19", and one that uses the new members loads.
   Task 2: `KitCompatibilityTest.theTabMembersSdkZeroNineteenRemovedAreRefused`; Task 4:
   `KitCompatibilityTest.theConstantsSdkZeroNineteenRemovedAreRefused`.

## Verified facts

At `22b702456` unless a plan is named (every file cited here is as it was at `ac2f7cf34`).

1. SDK terminal model: `TabHandle.panes()` and `activePane()` (`TabHandle.java:23-35`); `WindowHandle.tabs()` and
   `activeTab()` (`WindowHandle.java:17-29`), the class comment "One terminal window" (`:8`); `Terminals.activePane()`,
   `openTab`, `split(PaneHandle, Direction, OpenRequest)` (`Terminals.java:19-78`); `Direction { RIGHT, DOWN }`
   (`Direction.java:3-9`); `PaneHandle.tab()` (`PaneHandle.java:20-25`); `ExitPolicy { KEEP_OPEN, CLOSE_PANE }`
   (`ExitPolicy.java:3-9`); `StandardMenu { FILE, EDIT, VIEW, PANE, TAB, TOOLS, SESSION, TRANSFER }` (`StandardMenu.java:3-31`);
   `JasperSdk.VERSION = "0.18.0"` (`JasperSdk.java:6`).
2. `TerminalEvents` has `WindowEvent`, `TabEvent(windowId, tabId)`, `PaneEvent(tabId, paneId)` and 16 topics
   (`TerminalEvents.java:26-51`, `:135-166`); `TerminalValuesTest.everyTerminalTopicIsNamespacedAndDistinct` counts them
   (`TerminalValuesTest.java:71-82`) and derives each id from the field name.
3. App registry: `TabEntry(id, windowId, panes, focusedPane, title, select)` (`terminals/TabEntry.java:9-10`);
   `WindowEntry(id, tabs, selectedTab, active, toFront, openTab)` (`WindowEntry.java:15-16`); `TerminalEvent` has
   `TabOpened`, `TabClosed`, `TabSelected` (`TerminalEvent.java:14-16`); `TerminalRegistry.activePane()` reads the
   selected tab's `focusedPane` and `pane(id)` walks `panes()` (`TerminalRegistry.java:95-112`); `TerminalBridge` maps
   each fact to its topic in an exhaustive `switch` (`plugins/TerminalBridge.java:24-50`). `TerminalRegistry` checks the
   EDT with `SwingUtilities` (`TerminalRegistry.java:12`), the package's only Swing use.
4. After plan 3a (its Task 5): `WindowTerminals` lists `owner.terminalTabs()` only, its `selectedTab` is the current
   terminal (empty while a plugin's tab is active), its `TabEntry entry(TerminalContent terminal)` builds
   `() -> List.of(entry(terminal, terminal.pane()))` and `() -> Optional.of(entry(terminal, terminal.pane()))`, and it
   publishes `TabOpened`/`TabClosed` through `tabOpened(TerminalContent)`/`tabClosed(TerminalContent)`;
   `WindowContent.attach(Tab, boolean, GroupId, Side)` calls
   `if (tab.content() instanceof TerminalContent terminal && terminals != null) terminals.tabOpened(terminal);` and
   `detach(Tab)` the matching `tabClosed`, so a moved terminal tab reports `TabClosed` then `TabOpened` (3a Rulings 17)
   and a plugin's tab reports nothing. `TabRegistry.transfer(Tab, WorkspaceHost, WorkspaceHost)` sets the tab's window,
   then, with `tab.moving = true`, detaches it from the old host and attaches it to the new, recovering on failure, sets
   `tab.moving = false` in a `finally`, and selects (3a Tasks 1, 4); `Tab.moving()` (`boolean moving`, package-private
   field) tells `WindowContent.attach`/`detach` to unmaximize and `WorkspaceView` not to animate (3a Rulings 17, 27);
   `move(Tab, UUID)` and `move(Tab, boolean)` call it. `WindowContent.tabs()`, `selectedTab()` and `selectTab(Tab)` are
   package-private; `PaneEntry.split` takes the layout's `Side` and still needs the pane's shell running
   (`openBeside`, 3a Rulings 11, 19); the app's Split Right and Split Down are enabled whenever New Tab is.
   `WindowContent.attach` announces the tab before `TabHost.attach` and `workspace.open`, and both can run `update()`
   (`TabHost.present` fires the tab's change listeners; every layout change runs `workspaceChanged`), which calls the
   package-private `onTitle`; its failure path removes the listener, unmounts the tab and rethrows, and
   `TabRegistry.transfer` then attaches the tab to the window it left, else another, else none, and rethrows. A window
   whose last tab leaves closes at once (`onEmpty`), so its `WindowClosed` comes before the arrival in the new window;
   `TabRegistry.move(Tab, true)` creates the new window, `WindowOpened`, before the transfer.
5. `HostedTerminals` (`plugins/HostedTerminals.java`): `Window.tabs()`/`activeTab()` map entries (`:102-109`); the `Tab`
   handle implements `panes()` and `activePane()` (`:118-147`); `Pane.tab()` falls back to `new Tab(tabId, NOWHERE)`
   (`:161-165`); `focusedPane(windowId)` reads `focusedPane()` (`:235-240`). 3a changes only its `Side` import and the
   split's `Side.RIGHT : Side.DOWN` (`:6`, `:280`).
6. Testkit: `FakeWorkspace` keeps tabs with `panes` and `focused` and closes a tab with its last pane
   (`FakeWorkspace.java:17-136`); `FakeTerminals.split` adds the pane to the target's tab (`FakeTerminals.java:179-202`);
   `FakeContent` is not a workspace tab, so `WindowHandle.tabs()` never lists it (`FakeContent.java:12-46`,
   `FakeUi.java:544-558`); `FakeSessions.pump` closes a `CLOSE_PANE` session's pane (`FakeSessions.java:186`).
7. Contract suite: `ContractHarness.addTerminalTab`, `addTerminalPane`, `closeTerminalPane`, `menu` (Javadoc names `PANE`)
   (`ContractHarness.java:61-66`, `:122-138`); `PluginContractTest` adds two panes to one tab in
   `terminalsAreFoundThroughTheContextAndHandlesAreEqualById`, `injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt`
   and `terminalEventsNameIdsAndFollowTheActivePane` (`:1313-1420`) and asserts `split.tab().id() == tab`
   (`:1432`). `AppContractTest` runs it over `TerminalFixture` (`plugins/TerminalFixture.java:27-234`), whose split adds a
   pane to the same tab (`:49-64`); its content factory registers a `WorkspaceHost` whose attach and detach do nothing
   (`AppContractTest.java:139-147`, renamed by 3a Task 1), and `addTerminalWindow` registers no host (`:379`).
   `FakeContractTest` runs it over `FakePluginHost` (`FakeContractTest.java:78-101`).
8. Plugins: only Remote reads the removed API, `window.activeTab().flatMap(tab -> tab.activePane())` six times
   (`RemotePlugin.java:273`, `:304`, `:517`, `:638`, `:650`, `:727`); `FileServerPluginTest` adds a second pane to a tab
   (`:472`, `:655`); no plugin uses `StandardMenu.PANE` or `ExitPolicy.CLOSE_PANE`. Indexer, history, snippets,
   fileserver, editor, sample, vault and enterprise read `Terminals.activePane()`, `PaneHandle.tab().window()` and events,
   which keep their meaning.
9. Menus: `MenuTarget.Slot` (`contributions/MenuTarget.java:20`); `MenuBarLayout.BuiltIn(file, edit, view, pane, tab)`
   and `fresh(slots, pane, tab)` (`workspace/MenuBarLayout.java:29`, `:63`); `StandardMenuBar.fixed` (`:13`);
   `ClassicMenuBar.fresh` and `window` move Pane then Tab into Window (`:20-50`); `WindowChrome` builds `paneMenu()` and
   `tabMenu()` (`:96-103`, `:401-402`, `:437-443`; 3a renames `FOCUS_PANE_*` there to `FOCUS_GROUP_*`); `HostedUi` maps
   `StandardMenu` by name (`plugins/HostedUi.java:289`). Tests name the Pane menu in `WindowChromeMenusTest.java:75`,
   `:78`, `:95-108`, `ChromeRenderTest.java:111-112`, `WindowChromeContributionsTest.java:270` and 3a's
   `TabMenuTest.theFileViewAndPaneMenusNameTabsAndGroups`.
10. `HostedSessions` maps `ExitPolicy.CLOSE_PANE` to `SessionRequest.closeOnExit` (`plugins/HostedSessions.java:41`);
    `terminal.on_exit` takes `keep_open`, `close_on_success` and `close` (`config/CoreSettings.java:146-148`): no value
    names a pane.
11. Version gates: `KitCompatibility` keys removed members by the SDK that removed them, with a "0.18" list
    (`plugins/KitCompatibility.java:24-67`); `KitCompatibilityTest.aZeroSeventeenRangeIsSkippedBeforeItLoads` pins the
    message "this is SDK 0.18.0" (`:65-71`); `VersionRangeTest` reads every range in `docs/plugin-authoring.md` (14),
    `jasper-sdk/README.md` (8), `docs/sdk-icons.md` (1) and the example `plugin.toml`, and the example build's default
    version (`VersionRangeTest.java:16-41`, `:58-70`); `LookFixtures.descriptor` writes `>=0.18.0, <0.19` into plugins the
    resolver checks against `JasperSdk.VERSION` (`testsupport/LookFixtures.java:15-19`); the SDK version tests are
    `CapabilitiesTest.java:8`, `FilesValuesTest.java:54`, `PaletteValuesTest.java:107`, `KitBoundaryTest.java:5`. The
    nine bundled manifests (editor, enterprise, fileserver, history, indexer, remote, sample, snippets, vault) declare
    `>=0.18.0, <0.19`; the 0.17 and 0.18 bumps changed only those ranges, never a plugin's `version`.
12. "terminal window" in SDK Javadoc: 21 places in 12 files plus "terminal owner" (`ui/Windows.java:76`); in the testkit's
    Javadoc 9 (`FakePluginHost.java` 6, `FakeUi.java:135,137`, `ContractHarness.java:122`). The plugin-facing exception
    messages say "live terminal owner" or "terminal window" in both runtimes (`FakeUi.java:515`, `:582`, `:597`, `:615`;
    `HostedUi.java:605`, `:754`, `:786`), and no test asserts them. The guides say it once across a line break
    (`docs/plugin-authoring.md:883-884`), and `docs/sdk-architecture.md:340` says "terminal owner". The application's
    user-facing text keeps the phrase (`shortcuthelp/ShortcutCatalog.java:59`, Remote's `RemoteHosts.java:105` and
    `EditChannels.java:99`, `docs/remote.md:234`, the user guides), and plan 4 adds New Terminal Window, a child window
    with a terminal tab (spec 6.5).
13. THD Sourcing Crypto (read only, `~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at
    `3740e4e`): `plugin.toml:5` `sdk = ">=0.16.1, <0.18"`, `PluginPackagingTest.java:49` pins it, `build.gradle.kts:8` and
    `README.md:28` name 0.17.1; `CryptoForm.java:112` and `CryptoFormTest.java:61` still read 0.18's renamed
    `ChoiceKind.SEGMENTED`; `CryptoFormTest.java:39-41` adds two panes to one tab; its main code reads only
    `StandardMenu.TOOLS` and `Terminals.activePane()` (`CryptoPlugin.java:30`, `:37`).

## Rulings

Record them in `docs/STATUS.md` (Task 5).

1. **SDK 0.19.0 is a breaking boundary; every in-repo range is `>=0.19.0, <0.20`; plugin versions stay** — the 0.17 and
   0.18 bumps changed only ranges; a plugin's version follows its own features, and no plugin's behaviour changes here.
2. **`TabHandle.Kind` is a nested enum, `TERMINAL` and `CONTENT`; `kind()` is fixed for the tab's life and, like `id()`,
   needs neither a capability nor the UI thread; `terminal()` needs no capability (structure); `title()` keeps
   `terminal.observe` for every tab** — spec 14 names the kinds; one rule per method.
3. **`TAB_MOVED`'s payload is `TabMoved(tabId, fromWindowId, toWindowId)`; groups stay out of the SDK; a move within a
   window (between groups, a reorder) reports only the selection it changes** — the SDK has no group handle, and a
   window's tab list keeps its members.
4. **`TAB_OPENED`, `TAB_CLOSED` and `TAB_SELECTED` cover every tab; `TAB_SELECTED` reports the window's active tab
   (the active group's selected tab) of either kind** — spec 14.
5. **The window a tab arrives in reports the move, after the old window let it go: 3a's `Tab.moving` flag becomes the
   window being left, `Tab.movingFrom()` (`moving()` keeps its meaning); the old window then publishes no `TabClosed`
   and the new one `TabMoved` in place of `TabOpened`. A window that announced an arrival and then fails to show the tab
   takes the announcement back: a moved tab moves back (`TabMoved` to the window it left), an opened tab closes
   (`TabClosed`); where the registry's recovery then attaches the tab reports its own arrival, and a recovery into the
   window the tab left reports nothing** — only the arriving side knows the move succeeded, and its announcement must
   come before the tab's selection and panes are reported, so a failure withdraws it rather than the success delaying
   it; plugins never believe a tab is where it is not. Only the application's attach can fail; the fakes' moves cannot.
6. **A moved terminal tab reports no `PANE_OPENED` or `PANE_CLOSED` and its pane keeps its id (3a Rulings 17 kept;
   focus following the tab may report `PANE_FOCUSED`); a window the move empties reports `WINDOW_CLOSED` before the
   `TAB_MOVED`, and a window opened for the move `WINDOW_OPENED` before it, on both runtimes** — the pane and its
   session never closed; the old window lets the tab go, and the new one exists, before the tab arrives.
7. **`StandardMenu.PANE` and `MenuTarget.Slot.PANE` go; the Tab menu reads Split Right, Split Down, Focus Group Left,
   Right, Up and Down, Previous and Next Prompt, a rule, then Next Tab, Previous Tab, Rename Tab and Select Tab 1-9** —
   spec 14 folds Pane into Tab; this order keeps the classic Window menu's items and order.
8. **`ExitPolicy.CLOSE_PANE` becomes `ExitPolicy.CLOSE`, `terminal.on_exit`'s word for closing; the `on_exit` values
   stay** — a pane closes only with its tab now, and no `on_exit` value names a pane.
9. **The testkit and the app's contract fixture model the application: a terminal tab holds one pane
   (`addTerminalPane` refuses a second), a split opens a terminal tab in the pane's window, focusing a pane selects its
   tab, a plugin's content tab is a `CONTENT` tab of its window, and the last tab of any kind takes its window with it;
   `FakePluginHost.addContentTab` and `moveTab` and `ContractHarness.addContentTab` and `moveTab` script the rest, and
   `moveTab` refuses a plugin's own content tab** — one contract on both runtimes; a plugin moves its tab with
   `ContentTab.move`.
10. **`KitCompatibility` lists `TabHandle#panes`, `TabHandle#activePane`, `ExitPolicy#CLOSE_PANE` and
    `StandardMenu#PANE` under "0.19"** — the repository's gate for open ranges.
11. **"Jasper window" replaces "terminal window" in everything a plugin reads: the SDK's and the testkit's Javadoc, the
    exception messages `HostedUi` and `FakeUi` throw at plugins, and the plugin guides; the application's own comments
    and its user-facing text (the shortcut help's `GLOBAL` row, Remote's connect and sign-in messages, the user guides)
    are plan 4's** — spec 14's wording row is an SDK row; plan 4 defines main and child windows, and its New Terminal
    Window keeps the phrase in the user's vocabulary (spec 6.5).
12. **Remote needs no `TAB_MOVED` subscription: a moved tab arrives selected, so its `TAB_SELECTED` handler follows it**
    — Remote's per-window panels become one in plan 4.
13. **The app's `TabEntry.Kind` (`TERMINAL`, `CONTENT`) is `TabHandle.Kind`'s app-native twin: a tab whose content is a
    `TerminalContent` is `TERMINAL`; a plugin's tab, and plan 6's placeholder, is `CONTENT`** — `terminals` may not
    name SDK types.
14. **A content tab's title in the registry is its presentation title, without the unsaved marker or the detail the strip
    adds** — what `TabHandle.title()` gives a plugin.
15. **In `AppContractTest` every window is a `WorkspaceHost`, as every application window is, so a plugin's content opens
    in the window it names on both runtimes, and a fixture window that closes takes its host with it** — before, only
    the factory's windows were hosts.

## File structure

- `jasper-sdk`: `JasperSdk` and the "Jasper window" Javadoc (Task 1); `terminal/TabHandle`, `Direction`, `PaneHandle`,
  `Terminals` (Task 2); `terminal/TerminalEvents`, `WindowHandle` (Task 3); `ui/StandardMenu`, `terminal/ExitPolicy`
  (Task 4); tests `CapabilitiesTest`, `FilesValuesTest`, `PaletteValuesTest`, `KitBoundaryTest` (Task 1),
  `TerminalValuesTest` (Tasks 2, 3), `SessionValuesTest`, `UiValuesTest` (Task 4).
- `dev.jasper.app.terminals`: `TabEntry`, `TerminalRegistry` (Task 2); `TerminalEvent`, `WindowEntry` (Task 3).
  `dev.jasper.app.contributions`: `TabRegistry` (Task 3), `MenuTarget` (Task 4). `dev.jasper.app.workspace`:
  `WindowTerminals` (Tasks 2, 3), `WindowContent` (Task 3), `MenuBarLayout`, `StandardMenuBar`, `ClassicMenuBar`,
  `WindowChrome` (Task 4). `dev.jasper.app.plugins`: `HostedUi` (Task 1), `HostedTerminals`, `KitCompatibility` (Task 2), `TerminalBridge`
  (Task 3), `HostedSessions`, `KitCompatibility` (Task 4).
- App tests: `VersionRangeTest`, `KitCompatibilityTest`, `testsupport/LookFixtures` (Task 1); `TerminalRegistryTest`,
  `TerminalFixture`, `HostedTerminalsTest`, `WindowTerminalsTest`, `ShellExitBehaviorTest`, `KitCompatibilityTest`
  (Task 2); `TabRegistryTest`, `TabMoveTest`, `WindowTerminalsTest`, `TerminalBridgeTest`, `HostedTerminalsTest`,
  `TerminalFixture`, `AppContractTest` (Task 3); `WindowChromeMenusTest`, `ChromeRenderTest`,
  `WindowChromeContributionsTest`, `TabMenuTest`, `KitCompatibilityTest` (Task 4).
- Testkit: `FakePluginHost`, `FakeUi`, `ContractHarness` Javadoc and `FakeUi`'s messages (Task 1); `FakeWorkspace`, `FakeTerminals`,
  `FakePluginHost`, `ContractHarness`, `PluginContractTest`, `FakeTerminalsTest` (Task 2); `FakeWorkspace`, `FakeContent`,
  `FakeUi`, `FakePluginHost`, `ContractHarness`, `FakeContractTest`, `PluginContractTest`, `FakeTerminalsTest` (Task 3);
  `FakeSessions`, `FakePluginHost`, `ContractHarness`, `PluginContractTest` (Task 4).
- Plugins: the nine manifests (Task 1); `remote/RemotePlugin`, `fileserver/FileServerPluginTest` (Task 2).
- Documents (Tasks 1 and 5): `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-icons.md`, the example
  external plugin, `docs/plugin-ui.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/STATUS.md`.

## Before you start

```bash
git branch --show-current                                                                    # claude/workspace-tabs
git status --short                                                                           # nothing
git log --oneline -1 --grep 'Workspace tabs: documentation and status for the tab registry and groups'   # 3a's last commit
grep -c 'VERSION = "0.18.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java                     # 1
# Plan 3a's text this plan quotes, file by file:
C=jasper-app/src/main/java/dev/jasper/app/contributions; W=jasper-app/src/main/java/dev/jasper/app/workspace
T=jasper-app/src/test/java/dev/jasper/app
grep -c '    boolean moving;' $C/Tab.java                                                                  # 1
grep -c 'public boolean moving() { return moving; }' $C/Tab.java                                          # 1
grep -c 'public void move(Tab tab, UUID window)' $C/TabRegistry.java                                       # 1
grep -c 'private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next) {' $C/TabRegistry.java    # 1
grep -c 'tab.moving = true;' $C/TabRegistry.java                                                          # 1
grep -c 'tab.moving = false;' $C/TabRegistry.java                                                         # 1
grep -c 'terminals.tabOpened(terminal);' $W/WindowContent.java                                            # 1
grep -c 'terminals.tabClosed(terminal);' $W/WindowContent.java                                            # 1
grep -c 'window opened. A failure leaves nothing mounted and propagates.' $W/WindowContent.java          # 1
grep -c '        JComponent component = tab.component();' $W/WindowContent.java                          # 1
grep -c '            WorkspaceTheme.clear(component);' $W/WindowContent.java                              # 1
grep -c 'window: a terminal tab is announced closed here,' $W/WindowContent.java                         # 1
grep -c 'each closes, a terminal tab announced closed.' $W/WindowContent.java                             # 1
grep -c "One window's presence in the terminal registry" $W/WindowTerminals.java                          # 1
grep -c 'var entry = new WindowEntry(owner.id(),' $W/WindowTerminals.java                                 # 1
grep -c 'TabEntry entry(TerminalContent terminal) {' $W/WindowTerminals.java                              # 1
grep -c 'void tabOpened(TerminalContent terminal) {' $W/WindowTerminals.java                              # 1
grep -c 'void tabClosed(TerminalContent terminal) {' $W/WindowTerminals.java                              # 1
grep -c 'Reports the active terminal tab when it actually changed' $W/WindowTerminals.java                # 1
grep -c 'ActionId.FOCUS_GROUP_LEFT,' $W/WindowChrome.java                                                 # 1
grep -c 'private static final ActionId\[\] PANE_ITEMS = {' $W/WindowChrome.java                           # 1
grep -c 'BiFunction<Side, OpenSpec, Optional<PaneEntry>> split' jasper-app/src/main/java/dev/jasper/app/terminals/PaneEntry.java  # 1
grep -c 'private final class Tab implements TabHandle {' jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java     # 1
grep -c 'void anyTabMovesToANamedWindowAndIsSelectedThere() {' $T/contributions/TabRegistryTest.java     # 1
grep -c 'new TerminalEvent.TabOpened(target.id(), tab.id().value()));' $T/workspace/TabMoveTest.java      # 1
grep -c 'void goToFollowsAMovedTab() throws Exception {' $T/workspace/TabMoveTest.java                    # 1
grep -c -x 'import java.util.OptionalInt;' $T/workspace/TabMoveTest.java                                  # 1
grep -c -x 'import static org.assertj.core.api.Assertions.assertThat;' $T/workspace/TabMoveTest.java      # 1
grep -c 'void tabsSplitsInjectionAndExitAreReported() throws Exception {' $T/workspace/WindowTerminalsTest.java  # 1
grep -o '\.panes()\.get()\.get(0)' $T/workspace/WindowTerminalsTest.java | wc -l                          # 2
grep -c 'assertThat(tab.focusedPane().get()).contains(pane);' $T/workspace/WindowTerminalsTest.java      # 1
grep -o '\.orElseThrow()\.panes()\.get())\.containsExactly(' $T/workspace/WindowTerminalsTest.java | wc -l  # 2
grep -c '.selectedTab().get().orElseThrow().focusedPane().get().orElseThrow();' $T/workspace/ShellExitBehaviorTest.java  # 1
grep -c 'void theFileViewAndPaneMenusNameTabsAndGroups()' $T/workspace/TabMenuTest.java                    # 1
grep -c 'contains("Pane", "Tab").doesNotContain("Window");' $T/workspace/ChromeRenderTest.java           # 1
grep -c '"View", "Pane", "Tab", "Tools", "SSH"' $T/workspace/WindowChromeContributionsTest.java          # 1
grep -c 'contributions.tabs().setFactory(separate -> {' $T/plugins/AppContractTest.java                  # 1
grep -c 'public UUID addTerminalWindow() { return onEdtValue(terminalFixture::addWindow); }' $T/plugins/AppContractTest.java  # 1
grep -c 'App-native registry of terminal windows, tabs and panes' docs/sdk-architecture.md docs/app-architecture.md  # 1 each
grep -c 'Terminal enumeration and active-pane APIs continue to' docs/sdk-architecture.md                 # 1
```

Every command prints the stated result; the greps after the comment cover every text this plan quotes from a file plan
3a creates or changes. Read plan 3a's STATUS entry and banner: where a grep fails or 3a's execution deviated from its
plan text in a place this plan quotes, re-anchor on the code, keep this plan's replacement, and record it in the
banner. This plan calls none of the
members 3a renamed or removed (`WorkspaceView.model()`, `moveTab`, `resizeSplit`, `GroupView.display`;
`selectedComponent()`, `reorderTab(int, int)`, `tabHost()`). Plans 1, 2 and 3a each added a `docs/STATUS.md`
entry; this plan's goes above them. Create the helper script (Global Constraints).

---

### Task 1: SDK 0.19: the version, every range and "Jasper window"

`JasperSdk.VERSION` becomes `0.19.0`; every in-repo range moves to `>=0.19.0, <0.20` (the nine bundled manifests, the
example external plugin and its build's default, every range the guides tell authors to declare, and the test fixture
that writes a current plugin); 0.18 joins the incompatible minors (Rulings 1). The SDK's and the testkit's Javadoc and
the exception messages both runtimes throw at plugins say "Jasper window" (Rulings 11). No API changes yet.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`; Javadoc in `jasper-sdk/src/main/java/dev/jasper/sdk/`
  `WindowOwner.java:4`, `files/FileChoice.java:11`, `plugin/PluginContext.java:148`, `terminal/TerminalEvents.java:135-139`,
  `terminal/Terminals.java:27`, `terminal/WindowHandle.java:8`, `ui/DialogSpec.java:10`, `ui/FileEditor.java:20`,
  `ui/OverlaySpec.java:7,9`, `ui/Panels.java:20,23`, `ui/ShortcutContext.java:20,47`, `ui/Windows.java:39,73,76,88,103,119`,
  `cli/CommandCall.java:21`; testkit Javadoc `FakePluginHost.java:552,557,572,1188,1472,1758`, `FakeUi.java:135,137`,
  `testFixtures/.../contract/ContractHarness.java:122`; exception messages `FakeUi.java:515,582,597,615`,
  `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:605,754,786`
- Modify: `plugins/{editor,enterprise,fileserver,history,indexer,remote,sample,snippets,vault}/src/main/resources/plugin.toml:5`,
  `docs/examples/external-plugin/src/main/resources/plugin.toml:5`, `docs/examples/external-plugin/build.gradle.kts:7`,
  `docs/plugin-authoring.md` (14 ranges), `jasper-sdk/README.md` (8), `docs/sdk-icons.md:33`,
  `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java:15-18`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java:7-8`, `files/FilesValuesTest.java:54`,
  `palette/PaletteValuesTest.java:107`, `ui/KitBoundaryTest.java:5`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java:58-70`, `KitCompatibilityTest.java:65-71`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `JasperSdk.VERSION == "0.19.0"`; every bundled range `>=0.19.0, <0.20`. Tasks 2 and 4 add 0.19's removed
  members to `KitCompatibility`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
S = "jasper-sdk/src/test/java/dev/jasper/sdk/"
for test in ["CapabilitiesTest.java", "files/FilesValuesTest.java", "palette/PaletteValuesTest.java", "ui/KitBoundaryTest.java"]:
    edit(S + test, [('isEqualTo("0.18.0")', 'isEqualTo("0.19.0")', 1)])
edit(S + "CapabilitiesTest.java", [("    @Test void theSdkIsVersionEighteenWithThePresentationCapability() {",
                                    "    @Test void theSdkIsVersionNineteenWithThePresentationCapability() {", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(A + "VersionRangeTest.java", [
    ("    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroEighteen() {",
     "    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroNineteen() {", 1),
    ('        assertThat(VersionRange.parse(">=0.18.0, <0.19").contains(host)).as("every bundled range").isTrue();\n',
     '        assertThat(VersionRange.parse(">=0.18.0, <0.19").contains(host)).as("the former bundled range").isFalse();\n'
     '        assertThat(VersionRange.parse(">=0.19.0, <0.20").contains(host)).as("every bundled range").isTrue();\n', 1)])
edit(A + "KitCompatibilityTest.java", [(r'''    /** Spec 8.1: a 0.17 manifest is skipped by its range before any class is read. */
    @Test void aZeroSeventeenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.17.0, <0.18",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.17.0, <0.18.0; this is SDK 0.18.0"));
    }''', r'''    /** Workspace tabs plan 3b: a manifest of the previous minor, 0.18, is skipped by its range before any class is read. */
    @Test void aZeroEighteenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.18.0, <0.19.0; this is SDK 0.19.0"));
    }''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test :jasper-app:test --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: FAIL. The four SDK version tests ("expected: "0.19.0" but was: "0.18.0""),
`VersionRangeTest.pluginsBuiltForOlderMinorsAreIncompatibleWithZeroNineteen` (the 0.18 range still admits the host) and
`KitCompatibilityTest.aZeroEighteenRangeIsSkippedBeforeItLoads` (a 0.18 range loads on 0.18.0).

- [ ] **Step 3: Bump the version, move every range, reword the Javadoc**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, comments
edit("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java", [('VERSION = "0.18.0";', 'VERSION = "0.19.0";', 1)])
for plugin in ["editor", "enterprise", "fileserver", "history", "indexer", "remote", "sample", "snippets", "vault"]:
    edit(f"plugins/{plugin}/src/main/resources/plugin.toml", [('sdk = ">=0.18.0, <0.19"', 'sdk = ">=0.19.0, <0.20"', 1)])
edit("docs/examples/external-plugin/src/main/resources/plugin.toml", [('sdk = ">=0.18.0, <0.19"', 'sdk = ">=0.19.0, <0.20"', 1)])
edit("docs/examples/external-plugin/build.gradle.kts", [('getOrElse("0.18.0")', 'getOrElse("0.19.0")', 1)])
# 0.19 is a clean break: a guide's range for an older feature no longer loads, so every range in the guides is 0.19's.
for guide, count in {"docs/plugin-authoring.md": 14, "jasper-sdk/README.md": 8, "docs/sdk-icons.md": 1}.items():
    edit(guide, [('sdk = ">=0.18.0, <0.19"', 'sdk = ">=0.19.0, <0.20"', count)])
edit("jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java", [
    ("    /** The required descriptor keys of a plugin on SDK 0.18. */", "    /** The required descriptor keys of a plugin on SDK 0.19. */", 1),
    (r'+ "sdk = \">=0.18.0, <0.19\"\n";', r'+ "sdk = \">=0.19.0, <0.20\"\n";', 1)])
S = "jasper-sdk/src/main/java/dev/jasper/sdk/"
for name, count in {"WindowOwner.java": 1, "files/FileChoice.java": 1, "plugin/PluginContext.java": 1,
                    "terminal/TerminalEvents.java": 3, "terminal/Terminals.java": 1, "terminal/WindowHandle.java": 1,
                    "ui/DialogSpec.java": 1, "ui/FileEditor.java": 1, "ui/OverlaySpec.java": 2, "ui/Panels.java": 2,
                    "ui/ShortcutContext.java": 2, "ui/Windows.java": 5}.items():
    comments(S + name, count)
edit(S + "ui/Windows.java", [("     * @param spec accessible title and terminal owner", "     * @param spec accessible title and owning Jasper window", 1)])
edit(S + "cli/CommandCall.java", [(
    "    /** @return the window of the Jasper pane the command ran in, while that window is open; empty when it ran elsewhere */",
    "    /** @return the Jasper window of the pane the command ran in, while that window is open; empty when it ran elsewhere */", 1)])
K = "jasper-sdk-testkit/src/"
comments(K + "main/java/dev/jasper/sdk/testing/FakePluginHost.java", 6)
comments(K + "main/java/dev/jasper/sdk/testing/FakeUi.java", 2)
comments(K + "testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", 1)
# The messages a plugin reads when it names a window that cannot own the surface, the same in both runtimes.
for path, pickers in {"jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java": 1, K + "main/java/dev/jasper/sdk/testing/FakeUi.java": 2}.items():
    edit(path, [
        ('"Picker requires a shown plugin window or live terminal owner from this host"',
         '"Picker requires a shown plugin window or an open Jasper window from this host"', pickers),
        ('"An overlay needs an open terminal window from this host"', '"An overlay needs an open Jasper window from this host"', 1),
        ('"A dialog\'s owner must be an open window of this plugin or a terminal window"',
         '"A dialog\'s owner must be an open window of this plugin or a Jasper window"', 1)])
PY
git grep -n '<0\.19"' -- . ':!docs/superpowers' ':!docs/STATUS.md' ':!jasper-app/src/test/java/dev/jasper/app/plugins'
git grep -n -i 'terminal window\|terminal owner' -- jasper-sdk/src jasper-sdk-testkit/src
git grep -n 'terminal owner\|"[^"]*terminal window' -- jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java
```

The three `git grep` lines print nothing. No test asserts the reworded messages:
`git grep -n 'from this host\|window of this plugin' -- '*Test.java' jasper-sdk-testkit/src/testFixtures` prints nothing. (`DescriptorParserTest`, `PluginStateStoreOptInTest`, `PresentationSelectionTest`
and the 0.18 rename cases of `KitCompatibilityTest` keep their `>=0.18.0, <0.19` fixtures: they parse a descriptor or
pin their own host version, and none is resolved against `JasperSdk.VERSION`.) If a guide's count differs, a plan
before this one added or removed a range: re-count, adjust the script, and record it in the banner.

- [ ] **Step 4: Run the tests and the whole build**

Run: `./gradlew :jasper-sdk:test :jasper-app:test --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: PASS. Then `./gradlew check --continue`: BUILD SUCCESSFUL (every staged bundled plugin loads on 0.19.0; the
presentation tests' `LookFixtures` plugins resolve; doclint passes).

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src plugins/*/src/main/resources/plugin.toml docs/examples/external-plugin \
  docs/plugin-authoring.md jasper-sdk/README.md docs/sdk-icons.md jasper-app/src/test \
  jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java
git commit -F - <<'EOF'
SDK 0.19: the version, every range and "Jasper window"

JasperSdk.VERSION is 0.19.0, a breaking boundary for workspace tabs: every
bundled manifest, the example external plugin and every range the guides
tell authors to declare move to >=0.19.0, <0.20, and 0.18 joins the
incompatible minors. The SDK's and the testkit's Javadoc and the
exception messages both runtimes throw at plugins say "Jasper window"
where they said "terminal window" or "terminal owner".

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: A terminal tab's one pane: `TabHandle.kind()` and `terminal()`

Spec 14 and 9.4: `TabHandle` gains `kind()` and `terminal()` and loses `panes()` and `activePane()`; a split is a
terminal tab of its own (3a made it so in the application). The app's `TabEntry` gains a kind and swaps its pane list for
its one `terminal`; `HostedTerminals`, the registry, the testkit's `FakeWorkspace` and `FakeTerminals`, the app's
contract `TerminalFixture` and the contract suite follow (Rulings 2, 9, 13). `WindowTerminals` still lists terminal tabs
only; Task 3 lists the rest.

**Files:**
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TabHandle.java`, `Direction.java`;
  `jasper-app/src/main/java/dev/jasper/app/terminals/TabEntry.java`;
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`,
  `jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java`
- Modify: `jasper-sdk/.../terminal/PaneHandle.java:7-25`, `Terminals.java:19-78`; `jasper-app/.../terminals/TerminalRegistry.java:14-18,95-112`;
  `workspace/WindowTerminals.java` (3a's `entry(TerminalContent)`); `plugins/HostedTerminals.java:97,118-135,164,235-238`;
  `plugins/KitCompatibility.java:59-67`; `jasper-sdk-testkit/.../FakeTerminals.java:43-85,98-101,154,185-201`,
  `FakePluginHost.java:1195-1235`; `testFixtures/.../contract/ContractHarness.java:125-138`;
  `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:28,273,304,517,638,650,727`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`;
  `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java:61-75,1313-1435`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java:95-222`, `KitCompatibilityTest.java`,
  `workspace/WindowTerminalsTest.java`, `workspace/ShellExitBehaviorTest.java:363`;
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java:472,655`

**Interfaces:**
- Consumes: Task 1's 0.19 ranges; 3a's `WindowTerminals.entry(TerminalContent, TerminalPane)`, `PaneEntry.split`
  (`BiFunction<Side, OpenSpec, Optional<PaneEntry>>`), `TerminalContent.pane()`, `WindowContent.selectTab(Component)`.
- Produces: `TabHandle.Kind { TERMINAL, CONTENT }`, `TabHandle.Kind kind()`, `Optional<PaneHandle> terminal()`;
  `public record TabEntry(UUID id, UUID windowId, TabEntry.Kind kind, Supplier<Optional<PaneEntry>> terminal,
  Supplier<String> title, Runnable select)` with `public enum Kind { TERMINAL, CONTENT }`; `FakeWorkspace.Tab` with
  `final TabHandle.Kind kind`, `Window window`, `String title`, `Pane pane`; `FakeWorkspace.addTab(Window, TabHandle.Kind,
  String)`, `addTab(Window, String)` (a terminal tab), `addPane(Tab, PaneInfo)` (refuses a second),
  `close(Tab)`, `close(Pane)`; `TerminalFixture` with `private void close(Tab)`, `private void closeWindow(Window)`,
  `private void select(Tab)` and a `Tab` of `UUID id`, `Window window`, `TabEntry.Kind kind`, `Supplier<String> title`,
  `Pane pane`; `KitCompatibility`'s "0.19" list.

- [ ] **Step 1: Write the failing tests**

SDK values. Insert into `TerminalValuesTest`, before `    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
edit("jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java", [(
"    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {\n",
r'''    /** Workspace tabs spec 14: a tab is a terminal tab, holding one pane, or a content tab, holding none. */
    @Test void aTabHasAKindAndATerminalTabItsOnePane() throws Exception {
        assertThat(TabHandle.Kind.values()).containsExactly(TabHandle.Kind.TERMINAL, TabHandle.Kind.CONTENT);
        assertThat(TabHandle.class.getMethod("kind").getReturnType()).isEqualTo(TabHandle.Kind.class);
        assertThat(TabHandle.class.getMethod("terminal").getGenericReturnType().getTypeName())
            .isEqualTo("java.util.Optional<dev.jasper.sdk.terminal.PaneHandle>");
        assertThat(java.util.Arrays.stream(TabHandle.class.getMethods()).map(java.lang.reflect.Method::getName))
            .as("a tab has no pane list").doesNotContain("panes", "activePane");
        assertThat(Direction.values()).containsExactly(Direction.RIGHT, Direction.DOWN);
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
''', 1)])
PY
```

The testkit's own test. Insert into `FakeTerminalsTest`, before `    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java", [(
"    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {\n",
r'''    /** Workspace tabs spec 9.4: a terminal tab holds one pane, and a split is a terminal tab of its own. */
    @Test void aTerminalTabHoldsOnePaneAndASplitOpensATabOfItsOwn() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            assertThatIllegalStateException().isThrownBy(() -> host.addTerminalPane(tab, info("top"))).withMessageContaining("one pane");
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(), plugin -> { });
            var terminals = context.terminals();
            var tabHandle = terminals.tab(tab).orElseThrow();
            assertThat(tabHandle.kind()).isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.TERMINAL);
            assertThat(tabHandle.terminal().map(PaneHandle::id)).contains(pane);
            PaneHandle split = terminals.split(terminals.pane(pane).orElseThrow(), dev.jasper.sdk.terminal.Direction.RIGHT,
                OpenRequest.local()).orElseThrow();
            assertThat(split.tab().id()).as("a split is a terminal tab of its own").isNotEqualTo(tab);
            assertThat(split.tab().window().id()).isEqualTo(window);
            assertThat(terminals.window(window).orElseThrow().tabs()).extracting(dev.jasper.sdk.terminal.TabHandle::id)
                .containsExactly(tab, split.tab().id());
            assertThat(terminals.window(window).orElseThrow().activeTab()).as("and it is selected").contains(split.tab());
            assertThat(host.openRequests()).containsExactly("split|" + pane + "|RIGHT|-");
            host.closeTerminalPane(pane);
            assertThat(tabHandle.isOpen()).as("a pane closes with its tab").isFalse();
            assertThat(tabHandle.terminal()).isEmpty();
            assertThat(tabHandle.kind()).as("a closed tab keeps its kind").isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.TERMINAL);
        }
    }

    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {
''', 1)])
PY
```

The contract suite: one pane per terminal tab, `kind()` and `terminal()`, and a split that opens a tab of its own. The
first `span` rewrites `terminalsAreFoundThroughTheContextAndHandlesAreEqualById`; the next edits give each second pane a
tab of its own:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [("import dev.jasper.sdk.terminal.SessionState;\n", "import dev.jasper.sdk.terminal.SessionState;\nimport dev.jasper.sdk.terminal.TabHandle;\n", 1)])
span(P, "    @Test void terminalsAreFoundThroughTheContextAndHandlesAreEqualById() {\n",
     "        h.ui(() -> assertThat(alpha.get().terminals().activePane().map(PaneHandle::id)).contains(left));\n    }\n",
r'''    @Test void terminalsAreFoundThroughTheContextAndHandlesAreEqualById() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), left = h.addTerminalPane(tab, "make", Path.of("/src"));
        UUID other = h.addTerminalTab(window, "top"), right = h.addTerminalPane(other, "top", Path.of("/"));
        h.focusTerminalPane(left);
        h.activateTerminalWindow(window);
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.windows()).extracting(handle -> handle.id()).containsExactly(window);
            assertThat(terminals.activeWindow()).contains(terminals.windows().get(0));
            assertThat(terminals.windows().get(0).tabs()).extracting(TabHandle::id).containsExactly(tab, other);
            var tabHandle = terminals.windows().get(0).activeTab().orElseThrow();
            assertThat(tabHandle.id()).isEqualTo(tab);
            assertThat(tabHandle.kind()).isEqualTo(TabHandle.Kind.TERMINAL);
            assertThat(tabHandle.title()).isEqualTo("build");
            PaneHandle pane = terminals.activePane().orElseThrow();
            assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.terminal().orElseThrow());
            assertThat(pane.tab()).isEqualTo(tabHandle);
            assertThat(pane.tab().window().isActive()).isTrue();
            assertThat(pane.info().title()).isEqualTo("make");
            assertThat(pane.info().workingDirectory()).contains(Path.of("/src"));
            assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
            terminals.pane(right).orElseThrow().focus();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
            assertThat(terminals.windows().get(0).activeTab().map(TabHandle::id)).as("focusing a pane selects its tab").contains(other);
        });
        h.closeTerminalPane(right);
        h.ui(() -> {
            assertThat(alpha.get().terminals().activePane().map(PaneHandle::id)).contains(left);
            assertThat(alpha.get().terminals().tab(other)).as("a pane closes with its tab").isEmpty();
        });
    }
''')
edit(P, [
    ('        UUID only = h.addTerminalPane(tab, "make", Path.of("/src")), other = h.addTerminalPane(tab, "top", Path.of("/"));\n',
     '        UUID only = h.addTerminalPane(tab, "make", Path.of("/src")), other = h.addTerminalPane(h.addTerminalTab(window, "top"), "top", Path.of("/"));\n', 1),
    ('''        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src")), right = h.addTerminalPane(tab, "top", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
''', '''        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src"));
        UUID other = h.addTerminalTab(window, "top"), right = h.addTerminalPane(other, "top", Path.of("/"));
        h.focusTerminalPane(left);
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
''', 1),
    ("        assertThat(heard.subList(3, 5)).containsExactlyInAnyOrder(new TerminalEvents.PaneEvent(tab, right),\n",
     "        assertThat(heard.subList(3, 5)).containsExactlyInAnyOrder(new TerminalEvents.PaneEvent(other, right),\n", 1),
    ("            assertThat(split.tab().id()).isEqualTo(tab);\n",
     r'''            assertThat(split.tab().id()).as("a split is a terminal tab of its own").isNotEqualTo(tab).isNotEqualTo(opened.tab().id());
            assertThat(split.tab().kind()).isEqualTo(TabHandle.Kind.TERMINAL);
            assertThat(split.tab().terminal()).contains(split);
            assertThat(split.tab().window().id()).isEqualTo(window);
            assertThat(terminals.window(window).orElseThrow().activeTab()).as("and it is selected").contains(split.tab());
''', 1)])
PY
```

The app's tests. `TerminalRegistryTest` is replaced whole below; `HostedTerminalsTest`, `WindowTerminalsTest` (3a's
version), `ShellExitBehaviorTest` and `KitCompatibilityTest` are edited:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, regex
A = "jasper-app/src/test/java/dev/jasper/app/"
H = A + "plugins/HostedTerminalsTest.java"
edit(H, [
    ('''        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), left = fixture.addPane(tab, "make", Path.of("/src"));
        UUID right = fixture.addPane(tab, "top", null);
        fixture.activateWindow(window);
''', '''        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), left = fixture.addPane(tab, "make", Path.of("/src"));
        UUID top = fixture.addTab(window, "top"), right = fixture.addPane(top, "top", null);
        fixture.focusPane(left);
        fixture.activateWindow(window);
''', 1),
    ('''        assertThat(handle.tabs()).containsExactly(tabHandle);
        assertThat(tabHandle.id()).isEqualTo(tab);
        assertThat(tabHandle.window()).isEqualTo(handle);
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.panes()).extracting(PaneHandle::id).containsExactly(left, right);
        PaneHandle pane = terminals.activePane().orElseThrow();
        assertThat(pane.id()).isEqualTo(left);
        assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.activePane().orElseThrow());
''', '''        assertThat(handle.tabs()).extracting(TabHandle::id).containsExactly(tab, top);
        assertThat(handle.tabs()).first().isEqualTo(tabHandle);
        assertThat(tabHandle.id()).isEqualTo(tab);
        assertThat(tabHandle.kind()).isEqualTo(TabHandle.Kind.TERMINAL);
        assertThat(tabHandle.window()).isEqualTo(handle);
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.terminal().map(PaneHandle::id)).contains(left);
        PaneHandle pane = terminals.activePane().orElseThrow();
        assertThat(pane.id()).isEqualTo(left);
        assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.terminal().orElseThrow());
''', 1),
    ('''        assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        handle.toFront();
''', '''        assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        assertThat(handle.activeTab().map(TabHandle::id)).as("focusing a pane selects its tab").contains(top);
        handle.toFront();
''', 1),
    ("        assertThat(tabHandle.panes()).isEmpty();\n",
     "        assertThat(tabHandle.terminal()).isEmpty();\n"
     "        assertThat(tabHandle.kind()).as(\"a closed tab keeps its kind\").isEqualTo(TabHandle.Kind.TERMINAL);\n", 1),
    ("        assertThat(pane.tab().panes()).hasSize(1);\n", "        assertThat(pane.tab().terminal()).contains(pane);\n", 1),
    ("        assertThat(split.tab().id()).isEqualTo(tab);\n",
     "        assertThat(split.tab().id()).as(\"a split is a terminal tab of its own\").isNotEqualTo(tab).isNotEqualTo(opened.tab().id());\n"
     "        assertThat(split.tab().window().id()).isEqualTo(window);\n", 1)])
W = A + "workspace/WindowTerminalsTest.java"
regex(W, r"\.panes\(\)\.get\(\)\.get\(0\)", ".terminal().get().orElseThrow()", 2)
edit(W, [("            assertThat(tab.focusedPane().get()).contains(pane);\n",
          "            assertThat(tab.terminal().get()).contains(pane);\n"
          "            assertThat(tab.kind()).isEqualTo(TabEntry.Kind.TERMINAL);\n", 1)])
regex(W, r"\.orElseThrow\(\)\.panes\(\)\.get\(\)\)\.containsExactly\(", ".orElseThrow().terminal().get()).contains(", 2)
edit(A + "workspace/ShellExitBehaviorTest.java", [(
    ".selectedTab().get().orElseThrow().focusedPane().get().orElseThrow();",
    ".selectedTab().get().orElseThrow().terminal().get().orElseThrow();", 1)])
edit(A + "plugins/KitCompatibilityTest.java", [(
"    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {\n",
r'''    /** Workspace tabs plan 3b: a plugin built on 0.18 that claims 0.19 and still reads a tab's pane list is refused at load. */
    @Test void theTabMembersSdkZeroNineteenRemovedAreRefused() throws Exception {
        rejects(reference("dev.jasper.sdk.terminal.TabHandle","panes","()Ljava/util/List;"),"dev/jasper/sdk/terminal/TabHandle#panes()Ljava/util/List;","0.19");
        rejects(reference("dev.jasper.sdk.terminal.TabHandle","activePane","()Ljava/util/Optional;"),"dev/jasper/sdk/terminal/TabHandle#activePane()Ljava/util/Optional;","0.19");
        var current=candidate(">=0.19.0, <0.20",Map.of("test/kit/Probe.class",reference("dev.jasper.sdk.terminal.TabHandle","terminal","()Ljava/util/Optional;")),false);
        try(var loader=PluginLoader.load(current,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as("the member that took their place").isNotNull();}
    }
    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {
''', 1)])
PY
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java` with (one pane per
terminal tab; a content tab has no terminal):

```java
package dev.jasper.app.terminals;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class TerminalRegistryTest {
    private final TerminalRegistry registry = new TerminalRegistry();
    private final List<TerminalEvent> events = new ArrayList<>();

    /** A window whose tabs are a plain list the test mutates. */
    private static final class Window {
        final UUID id = UUID.randomUUID();
        final List<TabEntry> tabs = new ArrayList<>();
        TabEntry selected;
        final WindowEntry entry = new WindowEntry(id, () -> List.copyOf(tabs), () -> Optional.ofNullable(selected), () -> true, () -> { },
            directory -> Optional.empty());
    }

    /** A tab of a kind; a terminal tab gets its one pane from {@link #pane()}. */
    private static final class Tab {
        final UUID id = UUID.randomUUID();
        PaneEntry pane;
        final TabEntry entry;
        Tab(Window window) { this(window, TabEntry.Kind.TERMINAL); }
        Tab(Window window, TabEntry.Kind kind) {
            entry = new TabEntry(id, window.id, kind, () -> Optional.ofNullable(pane), () -> "tab", () -> { });
        }
        PaneEntry pane() {
            var snapshot = new PaneSnapshot("sh", Optional.empty(), 80, 24, false, PaneSnapshot.State.RUNNING, OptionalInt.empty(), Optional.empty(), Optional.empty(), "sh");
            pane = new PaneEntry(UUID.randomUUID(), id, () -> snapshot, () -> CompletableFuture.completedFuture(Optional.empty()),
                bytes -> { }, text -> { }, Optional::empty, () -> { }, (side, spec) -> Optional.empty());
            return pane;
        }
    }

    @Test void windowsAreFoundByIdAndTheirTabsAndPanesThroughThem() {
        var window = new Window();
        var tab = new Tab(window);
        var notes = new Tab(window, TabEntry.Kind.CONTENT);
        PaneEntry pane = tab.pane();
        window.tabs.add(tab.entry); window.tabs.add(notes.entry);
        var registration = registry.addWindow(window.entry);
        assertThat(registry.windows()).containsExactly(window.entry);
        assertThat(registry.window(window.id)).contains(window.entry);
        assertThat(registry.tab(tab.id)).contains(tab.entry);
        assertThat(registry.tab(notes.id)).as("a content tab is a tab too").contains(notes.entry);
        assertThat(registry.pane(pane.id())).contains(pane);
        assertThat(registry.pane(UUID.randomUUID())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.addWindow(window.entry));
        registration.close();
        registration.close();
        assertThat(registry.windows()).isEmpty();
        assertThat(registry.pane(pane.id())).isEmpty();
    }

    @Test void theActivePaneFollowsActivationSelectionAndClosing() {
        registry.onEvent(events::add);
        var first = new Window(); var second = new Window();
        var left = new Tab(first); var right = new Tab(first); var other = new Tab(second);
        PaneEntry leftPane = left.pane(), rightPane = right.pane(), lone = other.pane();
        first.tabs.add(left.entry); first.tabs.add(right.entry); first.selected = left.entry;
        second.tabs.add(other.entry); second.selected = other.entry;
        var closeFirst = registry.addWindow(first.entry);
        registry.addWindow(second.entry);
        assertThat(registry.activeWindow()).as("nothing was activated yet").isEmpty();
        assertThat(registry.activePane()).isEmpty();

        registry.windowActivated(first.id);
        assertThat(registry.activePane()).contains(leftPane);
        first.selected = right.entry;
        registry.publish(new TerminalEvent.TabSelected(first.id, right.id));
        registry.publish(new TerminalEvent.PaneFocused(right.id, rightPane.id()));
        registry.windowActivated(second.id);
        registry.windowActivated(UUID.randomUUID());
        closeFirst.close();
        assertThat(events).containsExactly(
            new TerminalEvent.WindowOpened(first.id), new TerminalEvent.WindowOpened(second.id),
            new TerminalEvent.WindowActivated(first.id), new TerminalEvent.ActivePaneChanged(Optional.of(leftPane.id())),
            new TerminalEvent.TabSelected(first.id, right.id), new TerminalEvent.ActivePaneChanged(Optional.of(rightPane.id())),
            new TerminalEvent.PaneFocused(right.id, rightPane.id()),
            new TerminalEvent.WindowActivated(second.id), new TerminalEvent.ActivePaneChanged(Optional.of(lone.id())),
            new TerminalEvent.WindowClosed(first.id));

        events.clear();
        registry.windows().get(0);
        registry.window(second.id).orElseThrow();
        // Closing the active window leaves no active window until the user turns to another.
        registry.atomically(() -> { });
        assertThat(events).isEmpty();
    }

    @Test void aContentTabHasNoTerminalSoNoPaneIsActiveWhileItIsSelected() {
        var window = new Window();
        var shell = new Tab(window); var notes = new Tab(window, TabEntry.Kind.CONTENT);
        PaneEntry pane = shell.pane();
        window.tabs.add(shell.entry); window.tabs.add(notes.entry); window.selected = notes.entry;
        registry.addWindow(window.entry);
        registry.onEvent(events::add);
        registry.windowActivated(window.id);
        assertThat(registry.activePane()).isEmpty();
        assertThat(registry.tab(notes.id).orElseThrow().terminal().get()).isEmpty();
        assertThat(registry.pane(pane.id())).as("the terminal tab's pane is still found").contains(pane);
        window.selected = shell.entry;
        registry.publish(new TerminalEvent.TabSelected(window.id, shell.id));
        assertThat(registry.activePane()).contains(pane);
        assertThat(events).containsExactly(new TerminalEvent.WindowActivated(window.id),
            new TerminalEvent.TabSelected(window.id, shell.id), new TerminalEvent.ActivePaneChanged(Optional.of(pane.id())));
    }

    @Test void anAtomicChangeReportsTheActivePaneOnceAndRefreshNoticesAQuietChange() {
        var window = new Window();
        var one = new Tab(window); var two = new Tab(window); var three = new Tab(window);
        PaneEntry a = one.pane(), b = two.pane(), c = three.pane();
        window.tabs.add(one.entry); window.tabs.add(two.entry); window.tabs.add(three.entry); window.selected = one.entry;
        registry.addWindow(window.entry);
        registry.windowActivated(window.id);
        registry.onEvent(events::add);
        registry.atomically(() -> {
            one.pane = null;
            registry.publish(new TerminalEvent.PaneClosed(one.id, a.id()));
            window.tabs.remove(one.entry);
            registry.publish(new TerminalEvent.TabClosed(window.id, one.id));
            window.selected = two.entry;
            registry.publish(new TerminalEvent.TabSelected(window.id, two.id));
        });
        assertThat(events).as("no transient 'no active pane' in the middle").containsExactly(
            new TerminalEvent.PaneClosed(one.id, a.id()), new TerminalEvent.TabClosed(window.id, one.id),
            new TerminalEvent.TabSelected(window.id, two.id), new TerminalEvent.ActivePaneChanged(Optional.of(b.id())));
        events.clear();
        window.selected = three.entry;
        registry.refresh();
        registry.refresh();
        assertThat(events).containsExactly(new TerminalEvent.ActivePaneChanged(Optional.of(c.id())));
    }

    @Test void aFailingListenerDoesNotStopTheOthersAndClosedSubscriptionsHearNothing() {
        List<String> heard = new ArrayList<>();
        registry.onEvent(event -> { throw new IllegalStateException("listener failure"); });
        var second = registry.onEvent(event -> heard.add("second"));
        registry.onEvent(event -> heard.add("third"));
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        second.close();
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        assertThat(heard).containsExactly("second", "third", "third");
    }
}
```

`HostedTerminalsTest` already imports `TabHandle`, `PaneHandle` and `WindowHandle`, and `WindowTerminalsTest` (3a's
version) `TabEntry`, `WindowEntry` and `UUID`; no import changes.

`FileServerPluginTest` gives each second pane a tab of its own:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
edit("plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java", [
    ("            UUID elsewhere = host.addTerminalPane(tab, pane(Optional.empty()));\n",
     "            UUID elsewhere = host.addTerminalPane(host.addTerminalTab(window, \"zsh\"), pane(Optional.empty()));\n", 1),
    ("            host.focusTerminalPane(host.addTerminalPane(tab, pane(Optional.of(home))));\n",
     "            host.focusTerminalPane(host.addTerminalPane(host.addTerminalTab(window, \"zsh\"), pane(Optional.of(home))));\n", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:compileTestJava --continue`
Expected: FAIL, `cannot find symbol` for `TabHandle.Kind`, `kind()`, `terminal()` and `TabEntry.Kind`.

- [ ] **Step 3: The SDK's tab handle**

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/TabHandle.java` with:

```java
package dev.jasper.sdk.terminal;

import java.util.Optional;
import java.util.UUID;

/**
 * One tab of a Jasper window: a terminal tab, whose {@link #terminal()} is its one pane, or a content tab, such as an
 * editor, a tool view or a plugin's view. Handles keep an id and no Swing object; two handles for the same tab are equal.
 * EDT only, except {@link #id()} and {@link #kind()}.
 */
public interface TabHandle {
    /**
     * What a tab holds. A tab keeps its kind for its whole life.
     *
     * @since 0.19.0
     */
    enum Kind {
        /** A terminal: one pane, so one session. */
        TERMINAL,
        /** Anything else: an editor, a tool view or a plugin's view. */
        CONTENT
    }

    /**
     * The tab's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window the tab was last seen in.
     *
     * @return its handle
     */
    WindowHandle window();

    /**
     * What the tab holds. Needs no capability.
     *
     * @return its kind
     * @since 0.19.0
     */
    Kind kind();

    /**
     * A terminal tab's pane. Needs no capability.
     *
     * @return the pane, or empty for a content tab and once the tab is closed
     * @since 0.19.0
     */
    Optional<PaneHandle> terminal();

    /**
     * The tab's title as its header shows it. Needs {@code terminal.observe}.
     *
     * @return the title; the last known one once the tab is closed
     */
    String title();

    /** Selects the tab in its window. Ignored once the tab is closed. */
    void select();

    /**
     * Whether the tab still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/Direction.java` with:

```java
package dev.jasper.sdk.terminal;

/** Which side of the target pane's tab group a split puts the new terminal tab's group. */
public enum Direction {
    /** To the right of the target's group. */
    RIGHT,
    /** Below the target's group. */
    DOWN
}
```

`PaneHandle` and `Terminals`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
T = "jasper-sdk/src/main/java/dev/jasper/sdk/terminal/"
edit(T + "PaneHandle.java", [
    (''' * One pane. Handles keep an id and no Swing object; two handles for the same pane are equal. A pane can
 * close at any moment: commands to a closed pane are ignored, queries return the last known value, and
 * {@link #isOpen()} tells. A missing capability always throws. EDT only, except the three sending methods.''',
     ''' * One pane: the terminal of a terminal tab, so one session. Handles keep an id and no Swing object; two handles for the
 * same pane are equal. A pane closes with its tab, at any moment: commands to a closed pane are ignored, queries return
 * the last known value, and {@link #isOpen()} tells. A missing capability always throws. EDT only, except the three
 * sending methods.''', 1),
    ("     * The tab the pane was last seen in.\n", "     * The terminal tab whose pane this is; a pane belongs to one tab for its whole life.\n", 1)])
edit(T + "Terminals.java", [
    ('''     * The focused pane of the active window's selected tab.
     *
     * @return the pane, or empty
''', '''     * The pane of the active window's active tab (the selected tab of its active group), when that tab is a terminal tab.
     *
     * @return the pane, or empty while a content tab is active or there is no active window ({@link #activeWindow()})
''', 1),
    ("     * Opens and selects a new tab. A local request needs {@code terminal.open};",
     "     * Opens a terminal tab in the window's active group and selects it. A local request needs {@code terminal.open};", 1),
    ('''     * Splits a pane. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
''', '''     * Opens a terminal tab in a new tab group beside the target pane's group and selects it: Jasper splits a window's
     * workspace, never a tab. A local request needs {@code terminal.open}; one naming a profile the host does not list
     * opens nothing.
''', 1),
    ("     * @param target the pane to split\n     * @param direction where the new pane goes\n",
     "     * @param target the pane whose group the new group goes beside\n     * @param direction which side of the target's group the new group takes\n", 1),
    ("     * @return the new pane, or empty when the target is gone, its session has ended or the profile is unknown\n",
     "     * @return the new tab's pane, or empty when the target is gone, its session has ended or the profile is unknown\n", 1)])
PY
```

- [ ] **Step 4: The app's registry and its plugin view**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/terminals/TabEntry.java` with:

```java
package dev.jasper.app.terminals;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One open tab, as functions over it (workspace tabs spec 9.4): a terminal tab, whose {@code terminal} is its one pane,
 * or a content tab, whose {@code terminal} is empty. EDT only.
 */
public record TabEntry(UUID id, UUID windowId, Kind kind, Supplier<Optional<PaneEntry>> terminal, Supplier<String> title,
                       Runnable select) {
    /** What a tab holds; the plugin runtime reports it to plugins as the SDK's tab kind. */
    public enum Kind {
        /** One terminal pane. */
        TERMINAL,
        /** Anything else: a plugin's view, an editor, a tool view. */
        CONTENT
    }

    /** Entries are recreated on every query; a tab is its id. */
    @Override public boolean equals(Object other) { return other instanceof TabEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
```

The registry, `WindowTerminals` (3a's `entry(TerminalContent)`), `HostedTerminals` and `KitCompatibility`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "terminals/TerminalRegistry.java", [
    (''' * The open terminal windows and what happens in them. Structure is always read through the entries, so
 * it cannot go stale; the one thing derived here is the active pane: the focused pane of the selected tab
 * of the window the user used last. EDT only.''',
     ''' * The open Jasper windows, their tabs and what happens in them. Structure is always read through the entries, so it
 * cannot go stale; the one thing derived here is the active pane: the pane of the active tab of the window the user used
 * last, when that tab is a terminal tab. EDT only.''', 1),
    ("        return activeWindow().flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());",
     "        return activeWindow().flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.terminal().get());", 1),
    ('''            for (TabEntry tab : window.tabs().get())
                for (PaneEntry pane : tab.panes().get()) if (pane.id().equals(id)) return Optional.of(pane);''',
     '''            for (TabEntry tab : window.tabs().get()) {
                Optional<PaneEntry> pane = tab.terminal().get();
                if (pane.isPresent() && pane.get().id().equals(id)) return pane;
            }''', 1)])
span(M + "workspace/WindowTerminals.java", "    TabEntry entry(TerminalContent terminal) {\n", "    }\n", r'''    TabEntry entry(TerminalContent terminal) {
        return new TabEntry(terminal.id().value(), owner.id(), TabEntry.Kind.TERMINAL, () -> Optional.of(entry(terminal, terminal.pane())),
            terminal::title, () -> owner.selectTab(terminal));
    }
''')
H = M + "plugins/HostedTerminals.java"
span(H, "    private final class Tab implements TabHandle {\n", ".map(Pane::new); }\n", r'''    private final class Tab implements TabHandle {
        private final UUID id;
        private final TabHandle.Kind kind;
        private UUID windowId;
        private String title = "";
        Tab(TabEntry entry) { this(entry.id(), entry.windowId(), kindOf(entry.kind())); }
        Tab(UUID id, UUID windowId, TabHandle.Kind kind) { this.id = id; this.windowId = windowId; this.kind = kind; }
        private Optional<TabEntry> entry() {
            Optional<TabEntry> found = registry.tab(id);
            found.ifPresent(entry -> windowId = entry.windowId());
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { requireUi("window"); entry(); return new Window(windowId); }
        @Override public TabHandle.Kind kind() { return kind; }
        @Override public Optional<PaneHandle> terminal() {
            requireUi("terminal");
            return entry().flatMap(entry -> entry.terminal().get()).map(Pane::new);
        }
''')
edit(H, [
    ("            return registry.tab(tabId).<TabHandle>map(Tab::new).orElseGet(() -> new Tab(tabId, NOWHERE));",
     "            return registry.tab(tabId).<TabHandle>map(Tab::new).orElseGet(() -> new Tab(tabId, NOWHERE, TabHandle.Kind.TERMINAL));", 1),
    ("    /** The focused pane in {@code windowId}'s selected tab, else the active pane. */",
     "    /** The pane of {@code windowId}'s active tab when that is a terminal tab, else the active pane. */", 1),
    ("            : registry.window(windowId).flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());",
     "            : registry.window(windowId).flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.terminal().get());", 1),
    ("    private final class Window implements WindowHandle {\n",
     r'''    /** The application's tab kind as plugins see it (workspace plan 3b Rulings 13). */
    private static TabHandle.Kind kindOf(TabEntry.Kind kind) {
        return switch (kind) { case TERMINAL -> TabHandle.Kind.TERMINAL; case CONTENT -> TabHandle.Kind.CONTENT; };
    }

    private final class Window implements WindowHandle {
''', 1)])
edit(M + "plugins/KitCompatibility.java", [(
    '''        "dev/jasper/sdk/ui/Nodes$IconSize#HEROLdev/jasper/sdk/ui/Nodes$IconSize;")));''',
    '''        "dev/jasper/sdk/ui/Nodes$IconSize#HEROLdev/jasper/sdk/ui/Nodes$IconSize;"),
        // The members SDK 0.19 removed with workspace tabs.
        "0.19", List.of(
        "dev/jasper/sdk/terminal/TabHandle#activePane()Ljava/util/Optional;",
        "dev/jasper/sdk/terminal/TabHandle#panes()Ljava/util/List;")));''', 1)])
PY
```

The `span` on `HostedTerminals` ends at the first `.map(Pane::new); }` after the `Tab` class's start, which is the old
`activePane()` line; `git diff` shows `title()`, `select()` and `isOpen()` untouched. The method is `kindOf`, not
`kind`, because the inner class's own `kind()` would hide an outer `kind(...)`.

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java` with (one pane per terminal
tab, a split in a tab of its own, focus selects the pane's tab; the sessions part is today's):

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
 * pane, a split opens a terminal tab of its own in the pane's window, focusing a pane selects its tab, a pane closes with
 * its tab and the last tab takes its window with it (workspace tabs spec 9.4). EDT only.
 */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); dev.jasper.app.terminals.RemoteLocation remote;
        SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = ""; String shell = "zsh"; }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; TabEntry.Kind kind = TabEntry.Kind.TERMINAL; Supplier<String> title; Pane pane; }
    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; }

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
                        yield addPane(addTab(window, "split"), "split", local.directory().orElse(pane.directory));
                    }
                    case OpenSpec.Session session -> {
                        opened.add("session-split|" + pane.id + "|" + side + "|" + session.request().title());
                        yield addSessionPane(addTab(window, session.request().title()), session.request());
                    }
                };
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, tab.kind, () -> Optional.ofNullable(tab.pane).map(this::entry), tab.title, () -> select(tab));
    }

    UUID addWindow() {
        var window = new Window();
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> opened.add("front|" + window.id), spec -> switch (spec) {
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

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.window = window; tab.title = () -> title;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
        return tab.id;
    }

    private void select(Tab tab) {
        if (tab.window.selected == tab) return;
        tab.window.selected = tab;
        registry.publish(new TerminalEvent.TabSelected(tab.window.id, tab.id));
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

    /** Closes a tab, its pane with it; the last tab takes its window with it. */
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
            window.tabs.remove(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) closeWindow(window);
        });
    }

    private void closeWindow(Window window) {
        windows.remove(window.id);
        window.registration.close();
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

- [ ] **Step 5: The testkit's fake workspace and terminals**

Replace the whole of `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java` with:

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
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 9.4): a terminal tab
 * holds one pane, a split opens a terminal tab of its own, the active pane is the pane of the active tab of the window
 * activated last when that tab is a terminal tab, focusing a pane selects its tab, a pane closes with its tab and the last
 * tab takes its window with it, and every change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; String title; Pane pane; boolean open = true;
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID(); final List<Tab> tabs = new ArrayList<>(); Tab selected; boolean open = true; boolean detached;
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

    Window addWindow() {
        var window = new Window();
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title); }

    /** A tab of {@code kind}, selected. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        window.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        select(tab);
        return tab;
    }

    void select(Tab tab) {
        if (!tab.open || tab.window.selected == tab) return;
        tab.window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(tab.window.id, tab.id));
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

    /** A pane closes with its tab. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes a tab, its pane with it; the last tab takes its window with it. */
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
        window.tabs.remove(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
        if (window.tabs.isEmpty()) closeWindow(window);
        refreshActive();
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

`FakeTerminals`, `FakePluginHost` and `ContractHarness`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
F = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java"
edit(F, [
    ("window.tabs.stream().<TabHandle>map(tab -> new Tab(tab.id, id)).toList()",
     "window.tabs.stream().<TabHandle>map(tab -> new Tab(tab.id, id, tab.kind)).toList()", 1),
    ("map(window -> window.selected).map(tab -> new Tab(tab.id, id));", "map(window -> window.selected).map(tab -> new Tab(tab.id, id, tab.kind));", 1),
    ("            return new Tab(tabId, workspace.tab(tabId).map(tab -> tab.window.id).orElse(new UUID(0, 0)));",
     "            return new Tab(tabId, workspace.tab(tabId).map(tab -> tab.window.id).orElse(new UUID(0, 0)), TabHandle.Kind.TERMINAL);", 1),
    ("return workspace.tab(id).map(tab -> new Tab(tab.id, tab.window.id)); }",
     "return workspace.tab(id).map(tab -> new Tab(tab.id, tab.window.id, tab.kind)); }", 1)])
span(F, "    private final class Tab implements TabHandle {\n", ".map(pane -> new Pane(pane.id, id)); }\n", r'''    private final class Tab implements TabHandle {
        private final UUID id;
        private final TabHandle.Kind kind;
        private UUID windowId;
        private String title = "";
        Tab(UUID id, UUID windowId, TabHandle.Kind kind) { this.id = id; this.windowId = windowId; this.kind = kind; }
        private Optional<FakeWorkspace.Tab> model() {
            Optional<FakeWorkspace.Tab> found = workspace.tab(id);
            found.ifPresent(tab -> windowId = tab.window.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { context.requireOpen(); model(); return new Window(windowId); }
        @Override public TabHandle.Kind kind() { return kind; }
        @Override public Optional<PaneHandle> terminal() {
            context.requireOpen();
            return model().map(tab -> tab.pane).filter(pane -> pane.open).map(pane -> new Pane(pane.id, id));
        }
''')
span(F, "        return workspace.pane(target.id()).map(model -> {\n", "            return new Pane(pane.id, model.tab.id);\n        });\n", r'''        return workspace.pane(target.id()).map(model -> {
            // A split is a terminal tab of its own in the target's window, as in the application (spec 9.4).
            FakeWorkspace.Window window = model.tab.window;
            FakeWorkspace.Pane pane = switch (request) {
                case OpenRequest.Local local -> {
                    workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-")
                        + profile(local) + program(local));
                    yield workspace.addPane(workspace.addTab(window, "opened"), openedInfo(local));
                }
                case OpenRequest.Session session -> {
                    workspace.openRequests.add("session-split|" + model.id + "|" + direction + "|" + session.spec().title());
                    FakeWorkspace.Pane created = workspace.addPane(workspace.addTab(window, session.spec().title()), sessionInfo(session));
                    workspace.sessions.connect(context, created, session.spec());
                    yield created;
                }
            };
            workspace.focus(pane);
            return new Pane(pane.id, pane.tab.id);
        });
''')
H = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java"
edit(H, [
    ("     * Adds and selects a tab.\n", "     * Adds and selects a terminal tab; {@link #addTerminalPane} gives it its one pane.\n", 1),
    ("     * Adds a pane; a tab's first pane is its focused pane.\n",
     "     * Adds a terminal tab's pane. A terminal tab holds one pane, so a second throws {@code IllegalStateException}.\n", 1),
    ("     * Closes a pane; the last pane takes its tab with it, and the last tab its window.\n",
     "     * Closes a pane with its tab; the last tab takes its window with it.\n", 1),
    ("     * The user focused a pane, which also selects its tab.\n", "     * The user focused a pane, which selects its tab.\n", 1)])
C = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java"
edit(C, [
    ("    /** UI thread: adds and selects a tab in an open window. */",
     "    /** UI thread: adds and selects a terminal tab in an open window; {@link #addTerminalPane} gives it its one pane. */", 1),
    ("    /** UI thread: adds a running local pane, 80 by 24, with shell integration; a tab's first pane is its focused pane. */",
     "    /** UI thread: adds a terminal tab's one pane: running, local, 80 by 24, with shell integration. */", 1),
    ("    /** UI thread: the user focused this pane. */", "    /** UI thread: the user focused this pane, which selects its tab. */", 1),
    ("    /** UI thread: closes a pane; the last pane takes its tab with it, and the last tab its window. */",
     "    /** UI thread: closes a pane with its tab; the last tab takes its window with it. */", 1)])
PY
```

`FakeSessions` keeps calling `workspace.close(pane)`, which now closes the pane's tab; `FakePluginHost.focusTerminalPane`
and `closeTerminalPane` keep their method references (`workspace::focus`, `workspace::close` resolve to the `Pane`
overloads).

- [ ] **Step 6: Remote reads a tab's terminal**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, regex
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [("import dev.jasper.sdk.terminal.ShellProfileInfo;\n",
          "import dev.jasper.sdk.terminal.ShellProfileInfo;\nimport dev.jasper.sdk.terminal.TabHandle;\n", 1)])
regex(R, r"tab ?-> ?tab\.activePane\(\)", "TabHandle::terminal", 6)
PY
git grep -n 'activePane()' -- 'plugins/*/src/main' | grep -v 'terminals().activePane()' && exit 1 || true
git grep -n -e '\.panes()' -e 'focusedPane()' -- jasper-app/src/main jasper-sdk-testkit/src plugins | grep -v -e 'options.panes()' -e 'terminal.focusedPane' && exit 1 || true
```

Both checks print nothing (`Terminals.activePane()` keeps its meaning; `options.panes()` is the benchmark's option).

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.terminals.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.workspace.WindowTerminalsTest' --tests 'dev.jasper.app.workspace.ShellExitBehaviorTest' --tests 'dev.jasper.app.workspace.TabMoveTest' :jasper-plugin-remote:test :jasper-plugin-fileserver:test --continue`
Expected: PASS (`TerminalValuesTest` +1, `FakeTerminalsTest` +1, `TerminalRegistryTest` 5, `KitCompatibilityTest` +1;
`FakeContractTest` and `AppContractTest` run the changed contract cases). Then `./gradlew check --continue`: BUILD
SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins/remote/src plugins/fileserver/src
git commit -F - <<'EOF'
SDK tab handles: kind() and terminal() for a tab's one pane

TabHandle.kind() (TERMINAL, CONTENT) and terminal() replace panes() and
activePane(); a split is a terminal tab of its own. TabEntry gains a kind
and its one terminal; HostedTerminals, the registry, FakeWorkspace,
FakeTerminals and the contract fixture hold one pane per terminal tab, and
the contract suite checks it on both runtimes. Remote reads
TabHandle::terminal; KitCompatibility refuses the removed members as 0.19's.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Every tab in the registry and the SDK, and `TAB_MOVED`

Spec 14 and 9.4: `WindowHandle.tabs()` lists every tab in workspace order, `activeTab()` is the active group's selected
tab of either kind, the `TAB_*` topics cover content tabs, and a move between windows is one `TAB_MOVED` (Rulings 3-6,
14, 15). 3a's `Tab.moving` flag becomes `Tab.movingFrom()`, the window a move is taking the tab out of, so windows tell a
move from a close or an open; `WindowContent` publishes for every tab and takes an arrival back when it then fails to
show the tab; `WindowTerminals` lists every tab. The testkit lists a plugin's content as a `CONTENT` tab and moves it;
the app's contract harness makes every window a workspace host.

**Files:**
- Modify: `jasper-sdk/.../terminal/TerminalEvents.java:11-51,141-146`, `WindowHandle.java:17-29`;
  `jasper-app/.../terminals/TerminalEvent.java:16`, `WindowEntry.java:11-14`; `plugins/TerminalBridge.java:31`;
  `contributions/Tab.java` (3a's `moving` field and `moving()`), `contributions/TabRegistry.java` (3a's `transfer`);
  `workspace/WindowContent.java` (3a's `attach` with its failure path, `detach`, `close`); `workspace/WindowTerminals.java`
  (3a's class comment, constructor, `tabOpened`, `tabClosed`, `tabSelected`; Task 2's `entry`)
- Modify (testkit): `FakeWorkspace.java`, `FakeUi.java:557`, `FakePluginHost.java` (after `closeTerminalPane`),
  `ContractHarness.java` (after `closeTerminalPane`); replace `FakeContent.java`
- Test: `TerminalValuesTest`, `PluginContractTest`, `FakeTerminalsTest`, `FakeContractTest.java:79`;
  `TabRegistryTest`, `TabMoveTest`, `WindowTerminalsTest`, `TerminalBridgeTest`, `HostedTerminalsTest`,
  `TerminalFixture`, `AppContractTest.java:139-147,379`

**Interfaces:**
- Consumes: Task 2's `TabEntry`, `FakeWorkspace.addTab(Window, TabHandle.Kind, String)`, `close(Tab)`, `select(Tab)`,
  `TerminalFixture.close(Tab)`, `select(Tab)`, `closeWindow(Window)`; 3a's `TabRegistry.move(Tab, UUID)`,
  `move(Tab, boolean)`, `Tab.moving()`, `WindowContent.tabs()`, `selectedTab()`, `selectTab(Tab)`, `Tab.id().value()`,
  `Tab.title()`.
- Produces: `public record TerminalEvents.TabMoved(UUID tabId, UUID fromWindowId, UUID toWindowId)` (rejects nulls and
  `fromWindowId.equals(toWindowId)`), `public static final Topic<TabMoved> TerminalEvents.TAB_MOVED`
  (`jasper.terminal.tab_moved`); `TerminalEvent.TabMoved(UUID tabId, UUID fromWindowId, UUID toWindowId)`;
  `public Optional<UUID> Tab.movingFrom()` (`moving()` is `movingFrom().isPresent()`); `WindowTerminals.entry(Tab)`,
  `tabArrived(Tab, Optional<UUID>)`, `tabWithdrawn(Tab, Optional<UUID>)`, `tabClosed(Tab)`; `FakeWorkspace.move(Tab, Window)`; `FakeContent.tab`; `public UUID FakePluginHost.addContentTab(UUID
  windowId, String title)`, `public void FakePluginHost.moveTab(UUID tabId, UUID windowId)`;
  `ContractHarness.addContentTab(UUID, String)`, `moveTab(UUID, UUID)`; `TerminalFixture.addContentTab(UUID windowId,
  UUID tabId, Supplier<String> title)`, `selectTab(UUID)`, `moveTab(UUID, UUID)`, `closeTab(UUID)`,
  `onWindowClosed(UUID, Runnable)`.

- [ ] **Step 1: Write the failing tests**

SDK values and the testkit's test:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
edit("jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java", [
    ("        assertThat(ids).hasSize(16).doesNotHaveDuplicates();", "        assertThat(ids).hasSize(17).doesNotHaveDuplicates();", 1),
    ("    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {\n", r'''    /** Workspace tabs spec 14: a move names the tab and both windows. */
    @Test void aMoveNamesTheTabAndBothWindows() {
        var tab = java.util.UUID.randomUUID(); var from = java.util.UUID.randomUUID(); var to = java.util.UUID.randomUUID();
        var moved = new TerminalEvents.TabMoved(tab, from, to);
        assertThat(moved.tabId()).isEqualTo(tab);
        assertThat(moved.fromWindowId()).isEqualTo(from);
        assertThat(moved.toWindowId()).isEqualTo(to);
        assertThat(TerminalEvents.TAB_MOVED.payloadType()).isEqualTo(TerminalEvents.TabMoved.class);
        assertThatNullPointerException().isThrownBy(() -> new TerminalEvents.TabMoved(tab, null, to));
        assertThatNullPointerException().isThrownBy(() -> new TerminalEvents.TabMoved(tab, from, null));
        assertThatIllegalArgumentException().as("a move goes to another window").isThrownBy(() -> new TerminalEvents.TabMoved(tab, from, from));
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java", [(
"    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {\n",
r'''    /** Workspace tabs spec 14: scripted content tabs and moves are tabs of their windows, announced on the tab topics. */
    @Test void scriptedContentTabsAndMovesAreTabsOfTheirWindows() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), shell = host.addTerminalTab(window, "zsh"), pane = host.addTerminalPane(shell, info("zsh"));
            var heard = new java.util.ArrayList<Object>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE)), Set.of(), Set.of(), plugin -> {
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TAB_OPENED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TAB_MOVED, heard::add);
            });
            host.flush();
            heard.clear();
            UUID notes = host.addContentTab(window, "notes.txt");
            UUID other = host.addTerminalWindow();
            host.moveTab(shell, other);
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.TabEvent(window, notes),
                new dev.jasper.sdk.terminal.TerminalEvents.TabMoved(shell, window, other));
            var terminals = context.terminals();
            assertThat(terminals.window(window).orElseThrow().tabs()).extracting(dev.jasper.sdk.terminal.TabHandle::id).containsExactly(notes);
            assertThat(terminals.tab(notes).orElseThrow().kind()).isEqualTo(dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT);
            assertThat(terminals.tab(notes).orElseThrow().terminal()).isEmpty();
            assertThat(terminals.pane(pane).orElseThrow().tab().window().id()).as("the pane follows its tab").isEqualTo(other);
            assertThatIllegalArgumentException().isThrownBy(() -> host.addTerminalPane(notes, info("pane")));
        }
    }

    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {
''', 1)])
PY
```

The contract suite: content tabs, a terminal tab's move and a plugin's content tab moving and closing, inserted before
`openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane`; the harness gains `addContentTab` and `moveTab`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [("    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {\n", r'''    @Test void aContentTabIsATabOfItsWindowWithNoTerminal() {
        UUID window = h.addTerminalWindow(), shell = h.addTerminalTab(window, "zsh"), pane = h.addTerminalPane(shell, "zsh", Path.of("/src"));
        h.activateTerminalWindow(window);
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(TerminalEvents.TAB_OPENED, heard::add);
            context.events().subscribe(TerminalEvents.TAB_SELECTED, heard::add);
            context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
        });
        h.flush();
        heard.clear();
        UUID notes = h.addContentTab(window, "notes.txt");
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabEvent(window, notes), new TerminalEvents.TabEvent(window, notes),
            new TerminalEvents.ActivePaneChanged(Optional.empty()));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            var handle = terminals.window(window).orElseThrow();
            assertThat(handle.tabs()).extracting(TabHandle::id).containsExactly(shell, notes);
            assertThat(handle.tabs()).extracting(TabHandle::kind).containsExactly(TabHandle.Kind.TERMINAL, TabHandle.Kind.CONTENT);
            TabHandle content = handle.activeTab().orElseThrow();
            assertThat(content.id()).isEqualTo(notes);
            assertThat(content.terminal()).isEmpty();
            assertThat(content.title()).isEqualTo("notes.txt");
            assertThat(content.window()).isEqualTo(handle);
            assertThat(terminals.tab(notes)).contains(content);
            assertThat(terminals.activePane()).as("a content tab has no terminal").isEmpty();
            terminals.tab(shell).orElseThrow().select();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(pane);
        });
    }

    @Test void aTabMovedToAnotherWindowKeepsItsPaneAndReportsOnlyTheMove() {
        UUID first = h.addTerminalWindow(), stays = h.addTerminalTab(first, "stays");
        h.addTerminalPane(stays, "zsh", Path.of("/"));
        UUID moving = h.addTerminalTab(first, "build"), pane = h.addTerminalPane(moving, "make", Path.of("/src"));
        UUID second = h.addTerminalWindow(), other = h.addTerminalTab(second, "other");
        h.addTerminalPane(other, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_CLOSED, TerminalEvents.TAB_MOVED,
                    TerminalEvents.PANE_OPENED, TerminalEvents.PANE_CLOSED, TerminalEvents.WINDOW_CLOSED))
                context.events().subscribe(topic, heard::add);
        });
        h.flush();
        heard.clear();
        h.moveTab(moving, second);
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabMoved(moving, first, second));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle handle = terminals.pane(pane).orElseThrow();
            assertThat(handle.tab().id()).isEqualTo(moving);
            assertThat(handle.tab().window().id()).isEqualTo(second);
            assertThat(terminals.window(second).orElseThrow().tabs()).extracting(TabHandle::id).containsExactly(other, moving);
            assertThat(terminals.window(second).orElseThrow().activeTab().map(TabHandle::id)).as("it arrives selected").contains(moving);
            assertThat(terminals.window(first).orElseThrow().tabs()).extracting(TabHandle::id).containsExactly(stays);
        });
        heard.clear();
        h.moveTab(stays, second);
        h.flush();
        assertThat(heard).as("the window the move empties closes first").containsExactly(new TerminalEvents.WindowEvent(first),
            new TerminalEvents.TabMoved(stays, first, second));
        h.ui(() -> assertThat(alpha.get().terminals().window(first)).isEmpty());
    }

    @Test void aPluginsContentTabOpensMovesAndClosesOnTheTabTopics() {
        UUID window = h.addTerminalWindow(), shell = h.addTerminalTab(window, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_MOVED, TerminalEvents.TAB_CLOSED,
                    TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_CLOSED))
                context.events().subscribe(topic, heard::add);
        });
        h.flush();
        heard.clear();
        var tab = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.ui(() -> tab.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"),
            alpha.get().terminals().window(window).orElseThrow(), kitView(alpha.get()))));
        h.flush();
        assertThat(heard).singleElement().isInstanceOfSatisfying(TerminalEvents.TabEvent.class,
            opened -> assertThat(opened.windowId()).isEqualTo(window));
        UUID id = ((TerminalEvents.TabEvent) heard.getFirst()).tabId();
        h.ui(() -> {
            var opened = alpha.get().terminals().tab(id).orElseThrow();
            assertThat(opened.kind()).isEqualTo(TabHandle.Kind.CONTENT);
            assertThat(opened.terminal()).isEmpty();
            assertThat(opened.title()).isEqualTo("Notes");
            assertThat(alpha.get().terminals().window(window).orElseThrow().activeTab()).contains(opened);
        });
        heard.clear();
        h.ui(() -> tab.get().move(true));
        h.flush();
        assertThat(heard).hasSize(2).first().isInstanceOf(TerminalEvents.WindowEvent.class);
        UUID separate = ((TerminalEvents.WindowEvent) heard.getFirst()).windowId();
        assertThat(heard.get(1)).as("the window opened for the move reports first").isEqualTo(new TerminalEvents.TabMoved(id, window, separate));
        heard.clear();
        h.ui(() -> assertThat(tab.get().requestClose().join()).isTrue());
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabEvent(separate, id), new TerminalEvents.WindowEvent(separate));
        h.ui(() -> {
            assertThat(alpha.get().terminals().tab(id)).isEmpty();
            assertThat(alpha.get().terminals().window(separate)).as("its last tab took the window with it").isEmpty();
            assertThat(alpha.get().terminals().window(window)).isPresent();
        });
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
    "    void closeTerminalPane(java.util.UUID paneId);\n",
    r'''    void closeTerminalPane(java.util.UUID paneId);

    /** UI thread: adds and selects a content tab, as the application or another plugin shows a document; it has no pane. */
    java.util.UUID addContentTab(java.util.UUID windowId, String title);

    /** UI thread: moves a tab the test added to another open window, where it is selected; a window left with no tab closes. */
    void moveTab(java.util.UUID tabId, java.util.UUID windowId);
''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
    "            @Override public java.util.UUID addTerminalTab(java.util.UUID windowId, String title) { return host.addTerminalTab(windowId, title); }\n",
    "            @Override public java.util.UUID addTerminalTab(java.util.UUID windowId, String title) { return host.addTerminalTab(windowId, title); }\n"
    "            @Override public java.util.UUID addContentTab(java.util.UUID windowId, String title) { return host.addContentTab(windowId, title); }\n"
    "            @Override public void moveTab(java.util.UUID tabId, java.util.UUID windowId) { host.moveTab(tabId, windowId); }\n", 1)])
PY
```

The app's tests. `TabRegistryTest` gains a case before 3a's `anyTabMovesToANamedWindowAndIsSelectedThere`;
`TabMoveTest`'s interim facts (3a Rulings 17) become the move, and it gains a window that fails to show what arrives
(`update()` names the window through `onTitle`, as the tab's presentation and its layout change come in, after the
arrival was announced); `WindowTerminalsTest` gains a plugin's tab before
`tabsSplitsInjectionAndExitAreReported`; `TerminalBridgeTest` and `HostedTerminalsTest` gain a case each:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
A = "jasper-app/src/test/java/dev/jasper/app/"
edit(A + "contributions/TabRegistryTest.java", [("    @Test void anyTabMovesToANamedWindowAndIsSelectedThere() {\n", r'''    @Test void aMoveTellsBothWindowsWhereTheTabComesFromUntilItEnds() {
        var seen = new ArrayList<String>();
        var first = UUID.randomUUID(); var second = UUID.randomUUID(); var failing = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(first, false, tab -> seen.add("attach first " + tab.movingFrom().orElse(null)),
            tab -> seen.add("detach first " + tab.movingFrom().orElse(null)), tab -> { }, () -> { }));
        registry.addHost(new WorkspaceHost(second, false, tab -> seen.add("attach second " + tab.movingFrom().orElse(null)),
            tab -> seen.add("detach second " + tab.movingFrom().orElse(null)), tab -> { }, () -> { }));
        registry.addHost(new WorkspaceHost(failing, false, tab -> { throw new IllegalStateException("attach failed"); },
            tab -> { }, tab -> { }, () -> { }));
        var tab = registry.open("plugin.file", "one", "One", first, new JPanel());
        registry.move(tab, second);
        assertThat(seen).containsExactly("attach first null", "detach first " + first, "attach second " + first);
        assertThat(tab.movingFrom()).as("the move is over").isEmpty();
        assertThat(tab.moving()).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> registry.move(tab, failing)).withMessage("attach failed");
        assertThat(tab.window()).as("back where it was").isEqualTo(second);
        assertThat(tab.movingFrom()).as("a failed move is over too").isEmpty();
        seen.clear();
        tab.close();
        assertThat(seen).containsExactly("detach second null");
    }

    @Test void anyTabMovesToANamedWindowAndIsSelectedThere() {
''', 1)])
edit(A + "workspace/TabMoveTest.java", [(
'''            assertThat(facts).contains(new TerminalEvent.TabClosed(source.id(), tab.id().value()),
                new TerminalEvent.TabOpened(target.id(), tab.id().value()));
''', '''            assertThat(facts).as("one move, reported where it arrives").contains(new TerminalEvent.TabMoved(tab.id().value(), source.id(), target.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
''', 1),
    ("import java.util.OptionalInt;\n", "import java.util.OptionalInt;\nimport java.util.UUID;\n", 1),
    ("import static org.assertj.core.api.Assertions.assertThat;\n",
     "import static org.assertj.core.api.Assertions.assertThat;\nimport static org.assertj.core.api.Assertions.assertThatIllegalStateException;\n", 1),
    ("    @Test void goToFollowsAMovedTab() throws Exception {\n", r'''    /** Workspace plan 3b Rulings 5: a window that announced an arrival and then fails to show the tab takes it back. */
    @Test void anArrivalItsWindowFailsToShowIsTakenBack() throws Exception {
        edt(() -> {
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            Tab tab = source.selectedTab();
            UUID id = tab.id().value();
            target.onTitle = title -> { target.onTitle = ignored -> { }; throw new IllegalStateException("title failed"); };
            facts.clear();
            assertThatIllegalStateException().isThrownBy(() -> model.tabs().move(tab, target.id())).withMessage("title failed");
            assertThat(tab.window()).as("back where it was").isEqualTo(source.id());
            assertThat(source.tabs()).contains(tab);
            assertThat(target.tabs()).doesNotContain(tab);
            assertThat(facts).as("the move there and back").containsSubsequence(new TerminalEvent.TabMoved(id, source.id(), target.id()),
                new TerminalEvent.TabMoved(id, target.id(), source.id()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            assertThat(registry.tab(id).orElseThrow().windowId()).isEqualTo(source.id());
            facts.clear();
            tab.close();
            assertThat(facts).contains(new TerminalEvent.TabClosed(source.id(), id));
            target.onTitle = title -> { target.onTitle = ignored -> { }; throw new IllegalStateException("title failed"); };
            facts.clear();
            assertThatIllegalStateException().isThrownBy(() -> model.tabs().open("dev.example.editor", "notes", "notes.txt",
                target.id(), new javax.swing.JPanel())).withMessage("title failed");
            var opened = facts.stream().filter(TerminalEvent.TabOpened.class::isInstance).map(TerminalEvent.TabOpened.class::cast).toList();
            assertThat(opened).singleElement().extracting(TerminalEvent.TabOpened::windowId).isEqualTo(target.id());
            assertThat(facts).as("an open the window failed to show closes").containsSubsequence(opened.getFirst(),
                new TerminalEvent.TabClosed(target.id(), opened.getFirst().tabId()));
        });
    }

    @Test void goToFollowsAMovedTab() throws Exception {
''', 1)])
edit(A + "workspace/WindowTerminalsTest.java", [("    @Test void tabsSplitsInjectionAndExitAreReported() throws Exception {\n", r'''    @Test void aPluginsTabIsListedOpenedMovedAndClosedLikeATerminalTab() throws Exception {
        open();
        WindowContent[] other = new WindowContent[1];
        edt(() -> {
            var model = new dev.jasper.app.contributions.Contributions();
            owner.connectContributions(model);
            other[0] = content(launcher(pending));
            other[0].connectContributions(model);
            other[0].connectTerminals(registry, () -> { });
            UUID windowId = owner.id(), otherId = other[0].id();
            registry.windowActivated(windowId);
            events.clear();
            var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", windowId, new javax.swing.JPanel());
            UUID tabId = view.id().value();
            assertThat(events).containsSubsequence(new TerminalEvent.TabOpened(windowId, tabId), new TerminalEvent.TabSelected(windowId, tabId),
                new TerminalEvent.ActivePaneChanged(Optional.empty()));
            WindowEntry window = registry.window(windowId).orElseThrow();
            assertThat(window.tabs().get()).extracting(TabEntry::kind).containsExactly(TabEntry.Kind.TERMINAL, TabEntry.Kind.CONTENT);
            TabEntry entry = window.selectedTab().get().orElseThrow();
            assertThat(entry.id()).isEqualTo(tabId);
            assertThat(entry.terminal().get()).isEmpty();
            assertThat(entry.title().get()).isEqualTo("notes.txt");
            assertThat(registry.activePane()).as("a plugin's tab has no terminal").isEmpty();
            events.clear();
            model.tabs().move(view, otherId);
            assertThat(events).contains(new TerminalEvent.TabMoved(tabId, windowId, otherId), new TerminalEvent.TabSelected(otherId, tabId));
            assertThat(events).noneMatch(fact -> fact instanceof TerminalEvent.TabClosed || fact instanceof TerminalEvent.TabOpened);
            assertThat(registry.tab(tabId).orElseThrow().windowId()).isEqualTo(otherId);
            events.clear();
            view.close();
            assertThat(events).contains(new TerminalEvent.TabClosed(otherId, tabId));
        });
    }

    @Test void tabsSplitsInjectionAndExitAreReported() throws Exception {
''', 1)])
edit(A + "plugins/TerminalBridgeTest.java", [("    @Test void everyRegistryFactBecomesItsTopic() {\n", r'''    @Test void aMoveAndAContentTabBecomeTheirTopics() {
        TerminalBridge.connect(fixture.registry, bus);
        for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_MOVED, TerminalEvents.TAB_CLOSED, TerminalEvents.PANE_CLOSED))
            bus.subscribe("dev.x.tool", topic, heard::add);
        UUID first = fixture.addWindow(), keep = fixture.addTab(first, "keep");
        fixture.addPane(keep, "zsh", null);
        UUID second = fixture.addWindow(), shell = fixture.addTab(second, "zsh");
        fixture.addPane(shell, "zsh", null);
        UUID notes = UUID.randomUUID();
        fixture.addContentTab(first, notes, () -> "notes.txt");
        deliver();
        assertThat(heard).endsWith(new TerminalEvents.TabEvent(first, notes));
        heard.clear();
        fixture.moveTab(notes, second);
        fixture.closeTab(notes);
        deliver();
        assertThat(heard).containsExactly(new TerminalEvents.TabMoved(notes, first, second), new TerminalEvents.TabEvent(second, notes));
    }

    @Test void everyRegistryFactBecomesItsTopic() {
''', 1)])
edit(A + "plugins/HostedTerminalsTest.java", [("    /** Final review M3: a program tab's audit line names the program's file name, never its folder or arguments. */\n", r'''    @Test void aContentTabIsListedWithItsKindAndNoTerminalAndAMovedTabsHandlesFollowIt() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE);
        UUID window = fixture.addWindow(), shell = fixture.addTab(window, "zsh"), pane = fixture.addPane(shell, "zsh", null);
        UUID notes = UUID.randomUUID();
        fixture.addContentTab(window, notes, () -> "notes.txt");
        fixture.activateWindow(window);
        WindowHandle handle = terminals.window(window).orElseThrow();
        assertThat(handle.tabs()).extracting(TabHandle::kind).containsExactly(TabHandle.Kind.TERMINAL, TabHandle.Kind.CONTENT);
        TabHandle content = handle.activeTab().orElseThrow();
        assertThat(content.id()).isEqualTo(notes);
        assertThat(content.terminal()).isEmpty();
        assertThat(content.title()).isEqualTo("notes.txt");
        assertThat(terminals.activePane()).isEmpty();
        UUID other = fixture.addWindow();
        fixture.addPane(fixture.addTab(other, "zsh"), "zsh", null);
        PaneHandle moved = terminals.pane(pane).orElseThrow();
        fixture.moveTab(shell, other);
        assertThat(moved.tab().window().id()).as("the handle follows the move").isEqualTo(other);
        assertThat(handle.tabs()).extracting(TabHandle::id).containsExactly(notes);
    }

    /** Final review M3: a program tab's audit line names the program's file name, never its folder or arguments. */
''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL, `cannot find symbol` for `TerminalEvents.TabMoved`, `TAB_MOVED`, `FakePluginHost.addContentTab`,
`moveTab`, `Tab.movingFrom`, `TerminalEvent.TabMoved` and `TerminalFixture.addContentTab`; `AppContractTest`
does not implement `ContractHarness.addContentTab`.

- [ ] **Step 3: The SDK's move and its tab list**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
T = "jasper-sdk/src/main/java/dev/jasper/sdk/terminal/"
edit(T + "TerminalEvents.java", [
    (" * What happens in terminals. Subscribing to any of these topics needs {@code terminal.observe}. Payloads",
     " * What happens in Jasper's windows, their tabs and terminal tabs' panes. Subscribing to any of these topics needs\n"
     " * {@code terminal.observe}. Payloads", 1),
    ('''    /**
     * A tab.
     *
     * @param windowId its window
''', '''    /**
     * A tab, terminal or content.
     *
     * @param windowId its window
''', 1),
    ('''        public PaneEvent { Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(paneId, "paneId"); }
    }
''', '''        public PaneEvent { Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(paneId, "paneId"); }
    }

    /**
     * A tab moved to another window, keeping its id, its content and a terminal tab's pane, whose id stays too: the move
     * reports no {@code TAB_CLOSED} or {@code TAB_OPENED} and no {@code PANE_OPENED} or {@code PANE_CLOSED}. A window the
     * move empties reports {@code WINDOW_CLOSED}, and a window opened for the move {@code WINDOW_OPENED}, before it.
     *
     * @param tabId the tab
     * @param fromWindowId the window it left
     * @param toWindowId the window it is in now
     * @since 0.19.0
     */
    public record TabMoved(UUID tabId, UUID fromWindowId, UUID toWindowId) {
        /** Rejects nulls and a move to the window the tab is in. */
        public TabMoved {
            Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(fromWindowId, "fromWindowId");
            Objects.requireNonNull(toWindowId, "toWindowId");
            if (fromWindowId.equals(toWindowId)) throw new IllegalArgumentException("A move goes to another window");
        }
    }
''', 1),
    ("    /** A tab opened. */\n", "    /** A tab opened: a terminal tab or a content tab. */\n", 1),
    ("    /** A tab closed. */\n", "    /** A tab closed; a tab that moved to another window reports {@link #TAB_MOVED} instead. */\n", 1),
    ('''    /** A window's selected tab changed. */
    public static final Topic<TabEvent> TAB_SELECTED = Topic.of(PREFIX + "tab_selected", TabEvent.class);
''', '''    /** A window's active tab, the selected tab of its active group, changed. */
    public static final Topic<TabEvent> TAB_SELECTED = Topic.of(PREFIX + "tab_selected", TabEvent.class);
    /** A tab moved to another window; see {@link TabMoved}. @since 0.19.0 */
    public static final Topic<TabMoved> TAB_MOVED = Topic.of(PREFIX + "tab_moved", TabMoved.class);
''', 1)])
edit(T + "WindowHandle.java", [
    ('''     * The window's tabs, in display order; empty once the window is closed.
''', '''     * The window's tabs, terminal and content, in workspace order (group by group, each group's tabs in strip order);
     * empty once the window is closed.
''', 1),
    ('''     * The selected tab.
     *
     * @return the tab, or empty once the window is closed
''', '''     * The active tab: the selected tab of the window's active group, terminal or content.
     *
     * @return the tab, or empty in an empty workspace and once the window is closed
''', 1)])
PY
```

- [ ] **Step 4: Moves through the registry, and every tab in the terminal registry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "terminals/TerminalEvent.java", [(
    "    record TabSelected(UUID windowId, UUID tabId) implements TerminalEvent { }\n",
    "    record TabSelected(UUID windowId, UUID tabId) implements TerminalEvent { }\n"
    "    /**\n"
    "     * A tab moved to another window: no TabClosed or TabOpened, and its pane keeps its id with no PaneOpened or\n"
    "     * PaneClosed; a window the move empties reports WindowClosed first (workspace plan 3b Rulings 5, 6).\n"
    "     */\n"
    "    record TabMoved(UUID tabId, UUID fromWindowId, UUID toWindowId) implements TerminalEvent { }\n", 1)])
edit(M + "terminals/WindowEntry.java", [(
    ''' * One open terminal window, as functions over it. EDT only. {@code openTab} returns the new tab's pane, or empty
 * when the window is closing or the spec cannot be opened.''',
    ''' * One open Jasper window, as functions over it: {@code tabs} lists every tab, terminal and content, in workspace order,
 * and {@code selectedTab} is the active group's selected tab. EDT only. {@code openTab} returns the new tab's pane, or
 * empty when the window is closing or the spec cannot be opened.''', 1)])
edit(M + "plugins/TerminalBridge.java", [(
    "                case TerminalEvent.TabSelected fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));\n",
    "                case TerminalEvent.TabSelected fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));\n"
    "                case TerminalEvent.TabMoved fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_MOVED,\n"
    "                    new TerminalEvents.TabMoved(fact.tabId(), fact.fromWindowId(), fact.toWindowId()));\n", 1)])
C = M + "contributions/"
edit(C + "Tab.java", [
    ("    /** Set by the registry while it moves the tab from one window to another. */\n    boolean moving;\n",
     "    /** The window the registry is taking the tab out of while it moves it to another; null otherwise. */\n    UUID movingFrom;\n", 1),
    (r'''    /**
     * @return whether the registry is moving this tab between windows right now: a window that detaches or attaches it
     *     meanwhile sees a move, not a close or an open (workspace tabs spec 15)
     */
    public boolean moving() { return moving; }
''', r'''    /**
     * @return whether the registry is moving this tab between windows right now: a window that detaches or attaches it
     *     meanwhile sees a move, not a close or an open (workspace tabs spec 15)
     */
    public boolean moving() { return movingFrom != null; }
    /**
     * @return the window the registry is taking this tab out of while it moves it to another, else empty: the window it
     *     arrives in reports the move (workspace plan 3b Rulings 5)
     */
    public Optional<UUID> movingFrom() { return Optional.ofNullable(movingFrom); }
''', 1)])
edit(C + "TabRegistry.java", [("        tab.moving = true;\n", "        tab.movingFrom = old.id();\n", 1),
                              ("            tab.moving = false;\n", "            tab.movingFrom = null;\n", 1)])
edit(M + "workspace/WindowContent.java", [
    ("     * window opened. A failure leaves nothing mounted and propagates.\n",
     "     * window opened. A failure leaves nothing mounted, takes back what the terminal registry heard, and propagates.\n", 1),
    ("        JComponent component = tab.component();\n", "        JComponent component = tab.component();\n        boolean announced = false;\n", 1),
    ("            if (tab.content() instanceof TerminalContent terminal && terminals != null) terminals.tabOpened(terminal);\n",
     "            if (terminals != null) { terminals.tabArrived(tab, tab.movingFrom()); announced = true; }\n", 1),
    ("            WorkspaceTheme.clear(component);\n            throw failure;\n",
     "            WorkspaceTheme.clear(component);\n"
     "            // Plugins must not believe the tab is here (workspace plan 3b Rulings 5).\n"
     "            if (announced && terminals != null) terminals.tabWithdrawn(tab, tab.movingFrom());\n"
     "            throw failure;\n", 1),
    ("each closes, a terminal tab announced closed.", "each closes and is announced closed.", 1),
    ("        if (tab.content() instanceof TerminalContent terminal && terminals != null) terminals.tabClosed(terminal);\n",
     "        if (terminals != null && !tab.moving()) terminals.tabClosed(tab);\n", 1),
    ("     * Takes a tab out of this window before it closes or moves to another window: a terminal tab is announced closed here,\n",
     "     * Takes a tab out of this window before it closes or moves to another window: a closing tab is announced closed here,\n", 1)])
W = M + "workspace/WindowTerminals.java"
edit(W, [("package dev.jasper.app.workspace;\n\n", "package dev.jasper.app.workspace;\n\nimport dev.jasper.app.contributions.Tab;\n", 1)])
span(W, "/**\n * One window's presence in the terminal registry", " */\nfinal class WindowTerminals implements AutoCloseable {\n", r'''/**
 * One window's presence in the terminal registry (workspace tabs spec 9.4): entries that read every tab of this window,
 * terminal and content, in workspace order, and its terminal tabs' panes, on demand, and the facts the window publishes.
 * A tab that moved here from another window is reported once, as a move (workspace plan 3b Rulings 5). A pane's split
 * opens a terminal tab in a new group beside the pane's group. Owned and closed by {@link WindowContent}. EDT only.
 */
final class WindowTerminals implements AutoCloseable {
''')
span(W, "        var entry = new WindowEntry(owner.id(),", "            tabSelected();\n        });\n", r'''        var entry = new WindowEntry(owner.id(), () -> owner.tabs().stream().map(this::entry).toList(),
            () -> Optional.ofNullable(owner.selectedTab()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (Tab tab : owner.tabs()) {
                publish(new TerminalEvent.TabOpened(owner.id(), tab.id().value()));
                if (tab.content() instanceof TerminalContent terminal)
                    publish(new TerminalEvent.PaneOpened(tab.id().value(), terminal.pane().id()));
            }
            tabSelected();
        });
''')
span(W, "    TabEntry entry(TerminalContent terminal) {\n", "    }\n", r'''    /** A tab of this window: a terminal tab, or content (a plugin's tab, and later a placeholder; Rulings 13, 14). */
    TabEntry entry(Tab tab) {
        if (tab.content() instanceof TerminalContent terminal)
            return new TabEntry(tab.id().value(), owner.id(), TabEntry.Kind.TERMINAL, () -> Optional.of(entry(terminal, terminal.pane())),
                terminal::title, () -> owner.selectTab(tab));
        return new TabEntry(tab.id().value(), owner.id(), TabEntry.Kind.CONTENT, Optional::empty, tab::title, () -> owner.selectTab(tab));
    }
''')
span(W, "    void tabOpened(TerminalContent terminal) {", "    void tabClosed(TerminalContent terminal) { publish(new TerminalEvent.TabClosed(owner.id(), terminal.id().value())); }\n", r'''    /**
     * A tab this window now shows: opened here, or moved here from {@code from}, which reports the move. A move that ended
     * back in this window, as a failed move's recovery does, reports nothing.
     */
    void tabArrived(Tab tab, Optional<UUID> from) {
        UUID id = tab.id().value();
        if (from.isEmpty()) publish(new TerminalEvent.TabOpened(owner.id(), id));
        else if (!from.get().equals(owner.id())) publish(new TerminalEvent.TabMoved(id, from.get(), owner.id()));
    }

    /**
     * Takes back a {@link #tabArrived} when this window then failed to show the tab: a tab that opened here is reported
     * closed, and a tab that moved here moves back to {@code from}; wherever the registry's recovery then attaches it
     * reports its own arrival (workspace plan 3b Rulings 5).
     */
    void tabWithdrawn(Tab tab, Optional<UUID> from) {
        UUID id = tab.id().value();
        if (from.isEmpty()) publish(new TerminalEvent.TabClosed(owner.id(), id));
        else if (!from.get().equals(owner.id())) publish(new TerminalEvent.TabMoved(id, owner.id(), from.get()));
    }

    /** A tab of this window closed; a tab leaving for another window is reported where it arrives. */
    void tabClosed(Tab tab) { publish(new TerminalEvent.TabClosed(owner.id(), tab.id().value())); }
''')
span(W, "    /** Reports the active terminal tab when it actually changed", "        if (now != null) publish(new TerminalEvent.TabSelected(owner.id(), now));\n    }\n", r'''    /** Reports the active tab, terminal or content, when it actually changed; layout changes happen for other reasons too. */
    void tabSelected() {
        Tab current = owner.selectedTab();
        UUID now = current == null ? null : current.id().value();
        if (Objects.equals(now, selected)) return;
        selected = now;
        if (now != null) publish(new TerminalEvent.TabSelected(owner.id(), now));
    }
''')
PY
grep -n 'TerminalContent terminal) {\|tabOpened(\|currentTerminal()' jasper-app/src/main/java/dev/jasper/app/workspace/WindowTerminals.java && exit 1 || true
```

The last line prints nothing: `WindowTerminals` no longer lists terminal tabs alone, and 3a's `PaneEntry entry(TerminalContent,
TerminalPane)` and `openTab(OpenSpec)` are untouched (`git diff` shows them). `WindowContent` calls only
`tabArrived`, `tabWithdrawn`, `tabClosed`, `tabSelected`, `publish`, `atomically` and `refresh` on it (grep `terminals\.`
there).

- [ ] **Step 5: The fixtures: content tabs, moves and windows that close**

The app's `TerminalFixture` (Task 2's text) and its contract harness (3a's text):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
F = "jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java"
edit(F, [
    (" * its tab and the last tab takes its window with it (workspace tabs spec 9.4). EDT only.\n",
     " * its tab and the last tab takes its window with it (workspace tabs spec 9.4); a plugin's tab is a content tab of its\n"
     " * window, and a tab moved to another window is reported as a move. EDT only.\n", 1),
    ("    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; }\n",
     "    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; Runnable closed = () -> { }; }\n", 1),
    ("        windows.remove(window.id);\n        window.registration.close();\n",
     "        windows.remove(window.id);\n        window.registration.close();\n        window.closed.run();\n", 1),
    ("    void activateWindow(UUID windowId) { registry.windowActivated(windowId); }\n", r'''    /** A content tab, such as a plugin's view, with the id and title its window gives it; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.id = tabId; tab.window = window; tab.title = title; tab.kind = TabEntry.Kind.CONTENT;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
    }

    /** Selects a tab in its window, as a click on it does. */
    void selectTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) select(tab);
    }

    /**
     * Moves a tab to another window, where it is selected: the move is reported once, its pane keeps its id with no
     * PaneOpened or PaneClosed, and a window left with no tab closes before the move is reported (workspace plan 3b
     * Rulings 5, 6).
     */
    void moveTab(UUID tabId, UUID windowId) {
        Tab tab = tabs.get(tabId);
        Window to = windows.get(windowId);
        if (tab == null || to == null || tab.window == to) return;
        Window from = tab.window;
        registry.atomically(() -> {
            from.tabs.remove(tab);
            if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
            if (from.tabs.isEmpty()) closeWindow(from);
            tab.window = to;
            to.tabs.add(tab);
            registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
            select(tab);
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
''', 1)])
C = "jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java"
span(C, "        onEdt(() -> contributions.tabs().setFactory(separate -> {\n", "            return id;\n        }));\n", r'''        // Every window is a workspace, as every application window is (workspace plan 3b Rulings 15): a plugin's tab is a
        // content tab of the window that shows it, the window it moves to reports the move, and a window whose last tab
        // left closes, taking its workspace with it.
        java.util.function.Function<Boolean, UUID> workspace = separate -> {
            UUID id = terminalFixture.addWindow();
            var tabs = contributions.tabs();
            var registration = tabs.addHost(new dev.jasper.app.contributions.WorkspaceHost(id, separate,
                tab -> tab.movingFrom().ifPresentOrElse(from -> terminalFixture.moveTab(tab.id().value(), id),
                    () -> terminalFixture.addContentTab(id, tab.id().value(), tab::title)),
                tab -> { if (!tab.moving()) terminalFixture.closeTab(tab.id().value()); },
                entry -> {
                    for (var other : tabs.inWindow(id)) other.active(other == entry);
                    terminalFixture.selectTab(entry.id().value());
                }, () -> {}));
            terminalFixture.onWindowClosed(id, registration::close);
            return id;
        };
        onEdt(() -> contributions.tabs().setFactory(workspace));
''')
edit(C, [(
    "            @Override public UUID addTerminalWindow() { return onEdtValue(terminalFixture::addWindow); }\n",
    "            @Override public UUID addTerminalWindow() { return onEdtValue(() -> workspace.apply(false)); }\n"
    "            @Override public UUID addContentTab(UUID windowId, String title) {\n"
    "                return onEdtValue(() -> { UUID id = UUID.randomUUID(); terminalFixture.addContentTab(windowId, id, () -> title); return id; });\n"
    "            }\n"
    "            @Override public void moveTab(UUID tabId, UUID windowId) { onEdt(() -> terminalFixture.moveTab(tabId, windowId)); }\n", 1)])
PY
```

The testkit's `FakeWorkspace` (Task 2's text), `FakeUi` and `FakePluginHost`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeWorkspace.java", [
    (" * tab takes its window with it, and every change is announced on the terminal topics.\n",
     " * tab takes its window with it, a tab moved to another window is announced once as a move, and every change is\n"
     " * announced on the terminal topics.\n", 1),
    ("    /** A pane closes with its tab. */\n", r'''    /** Moves a tab to another open window, where it is selected; a window left with no tab closes before the move is announced. */
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
    }

    /** A pane closes with its tab. */
''', 1)])
edit(K + "FakeUi.java", [(
    "                var entry=new FakeContent(FakeUi.this,spec,owner,component);contents.add(entry);entry.select();return entry;\n",
    "                // A plugin's content is a content tab of its window (workspace tabs spec 14).\n"
    "                var shown = host.workspace.window(((WindowHandle) owner).id()).orElseThrow();\n"
    "                var entry=new FakeContent(FakeUi.this,spec,owner,component,host.workspace.addTab(shown,dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT,spec.title()));\n"
    "                contents.add(entry);entry.select();return entry;\n", 1)])
edit(K + "FakePluginHost.java", [(
    "    public void closeTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::close); }\n",
    r'''    public void closeTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::close); }

    /**
     * Adds and selects a content tab, as the application or another plugin shows a document or a tool view: a tab with no
     * pane, which {@code TabHandle.kind()} reports as {@code CONTENT}.
     *
     * @param windowId an open window
     * @param title the tab's title
     * @return the new tab's id
     * @since 0.19.0
     */
    public UUID addContentTab(UUID windowId, String title) {
        return workspace.addTab(workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No such window: " + windowId)),
            dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT, Objects.requireNonNull(title, "title")).id;
    }

    /**
     * Moves a tab the test added to another open window, where it is selected, as dragging it there does: a terminal
     * tab's pane keeps its id and session with no {@code PANE_OPENED} or {@code PANE_CLOSED}, the move is announced on
     * {@code TAB_MOVED}, and a window left with no tab closes before it. A plugin moves its own content tab with
     * {@code ContentTab.move}.
     *
     * @param tabId an open tab the test added
     * @param windowId another open window
     * @since 0.19.0
     */
    public void moveTab(UUID tabId, UUID windowId) {
        var tab = workspace.tab(tabId).orElseThrow(() -> new IllegalArgumentException("No such tab: " + tabId));
        var window = workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No such window: " + windowId));
        for (FakePluginContext context : contexts.values())
            if (context.ui.contents.stream().anyMatch(content -> content.tab == tab))
                throw new IllegalArgumentException("A plugin moves its own content tab with ContentTab.move");
        workspace.move(tab, window);
    }
''', 1)])
PY
```

Replace the whole of `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java` with (today's handle,
whose tab is now a workspace tab that is selected, moved and closed with it):

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * Recorded host content, retaining the actual component across moves. Its tab is a content tab of the scripted
 * workspace (workspace tabs spec 14): it opens, is selected, moves and closes on the tab topics like any tab.
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
    boolean active,closed,separate;
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
    public void move(boolean value){if(!closed&&pins==0&&ui.pendingContent==null&&separate!=value){
        active(false); separate=value;
        var window = value ? ui.host.workspace.addWindow() : ui.host.workspace.windows.stream().filter(w -> w.open && !w.detached).findFirst().orElseGet(ui.host.workspace::addWindow);
        if(value)window.detached=true;
        ui.host.workspace.move(tab, window);
        owner=ui.context.terminals.windowHandle(window.id); select();
    }}
    public boolean isSeparate(){return separate;} public boolean isActive(){return active;} public boolean isClosed(){return closed;}
    public CompletableFuture<Boolean> requestClose(){return ui.closeContent(List.of(this),true);}
    public Subscription onActive(Consumer<Boolean> value){activated.add(value);return ()->activated.remove(value);}
    public Subscription onClosed(Runnable value){disposed.add(value);return ()->disposed.remove(value);}
    public void close(){if(closed)return;active(false);closed=true;ui.contents.remove(this);ui.host.workspace.close(tab);for(var listener:List.copyOf(disposed))listener.run();disposed.clear();activated.clear();}
}
```

A content tab that closes now takes an otherwise empty window with it, as in the application: a test that opened a
plugin's content in a window with no other tab and then asks that window for something after the content closed meets a
closed window. `EditorPluginTest.theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen` does,
and passes (the Editor then opens its next tab in a fresh window, as the application does). If another plugin test
fails that way, give the window a terminal tab (`host.addTerminalPane(host.addTerminalTab(window, "zsh"), …)`) so it
stays, and record it in the banner; never weaken the fake.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.terminals.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS (`TerminalValuesTest` +1, `FakeTerminalsTest` +1, the contract suite +3 on both runtimes,
`TabRegistryTest` +1, `TabMoveTest` +1, `WindowTerminalsTest` +1, `TerminalBridgeTest` +1, `HostedTerminalsTest` +1). A workspace test
that asserts the registry's facts exactly and now also sees `TabOpened`/`TabSelected` for a plugin's tab is seeing this
task's behaviour: add those facts to its expectation and record it in the banner. Then `./gradlew check --continue`:
BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src
git commit -F - <<'EOF'
Every tab in the terminal registry and the SDK, and TAB_MOVED

WindowHandle.tabs() lists terminal and content tabs in workspace order and
activeTab() is the active group's selected tab; the TAB topics cover
content tabs, and a move between windows is one TAB_MOVED reported by the
window it arrives in (Tab.movingFrom), and taken back when that window
fails to show the tab. The testkit lists a plugin's content as a CONTENT
tab and moves it; the app's contract harness makes every window a
workspace host; both run the new contract cases.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The Tab menu takes the Pane items, and `ExitPolicy.CLOSE`

Spec 14: `StandardMenu.PANE` folds into `TAB`; the app's Pane menu slot goes with it (Rulings 7). `ExitPolicy.CLOSE_PANE`
becomes `ExitPolicy.CLOSE` (Rulings 8). `KitCompatibility` refuses both removed constants (Rulings 10).

**Files:**
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/ExitPolicy.java`
- Modify: `jasper-app/.../contributions/MenuTarget.java:20`, `workspace/MenuBarLayout.java:23-29,62-63`,
  `workspace/StandardMenuBar.java:11-15`, `workspace/ClassicMenuBar.java:12-50`, `workspace/WindowChrome.java:96-103,400-402,437-443`,
  `plugins/HostedSessions.java:41`, `plugins/KitCompatibility.java` (Task 2's "0.19" list);
  `jasper-sdk-testkit/.../FakeSessions.java:186`, `FakePluginHost.java:586`, `ContractHarness.java:61-66`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`, `terminal/SessionValuesTest.java`;
  `PluginContractTest` (one case); `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java:75,78,95-104`,
  `ChromeRenderTest.java:111-112`, `WindowChromeContributionsTest.java:270`, 3a's `TabMenuTest`; `KitCompatibilityTest`

**Interfaces:**
- Consumes: Task 2's `KitCompatibility` "0.19" list; 3a's `TabMenuTest.items(WindowContent, String)` and
  `ActionId.FOCUS_GROUP_*`.
- Produces: `StandardMenu { FILE, EDIT, VIEW, TAB, TOOLS, SESSION, TRANSFER }`; `ExitPolicy { KEEP_OPEN, CLOSE }`;
  `MenuTarget.Slot { FILE, EDIT, VIEW, TAB, TOOLS, SESSION, TRANSFER }`; `MenuBarLayout.BuiltIn(JMenu file, JMenu edit,
  JMenu view, JMenu tab)`, `void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> tab)`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
S = "jasper-sdk/src/test/java/dev/jasper/sdk/"
edit(S + "ui/UiValuesTest.java", [("class UiValuesTest {\n", r'''class UiValuesTest {
    /** Workspace tabs spec 14: the Tab menu holds what the Pane menu held. */
    @Test void theStandardMenusAreFileEditViewTabAndThePluginOnes() {
        assertThat(StandardMenu.values()).containsExactly(StandardMenu.FILE, StandardMenu.EDIT, StandardMenu.VIEW, StandardMenu.TAB,
            StandardMenu.TOOLS, StandardMenu.SESSION, StandardMenu.TRANSFER);
    }

''', 1)])
edit(S + "terminal/SessionValuesTest.java", [("class SessionValuesTest {\n", r'''class SessionValuesTest {
    /** Workspace tabs plan 3b: a session's tab stays, or closes as {@code terminal.on_exit = "close"} closes a shell's. */
    @Test void aSessionKeepsItsTabOpenOrClosesIt() {
        assertThat(ExitPolicy.values()).containsExactly(ExitPolicy.KEEP_OPEN, ExitPolicy.CLOSE);
        assertThat(new SessionSpec("build-host", Optional.empty(), ExitPolicy.CLOSE, pending -> { }).onExit()).isEqualTo(ExitPolicy.CLOSE);
    }

''', 1)])
P = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(P, [("    @Test void stoppingAPluginCancelsWhatItIsStillConnecting() {\n", r'''    @Test void aSessionThatClosesWhenItEndsTakesItsTabWithIt() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, new SessionSpec("build-host", Optional.empty(), ExitPolicy.CLOSE, pendings::add));
        var tab = new AtomicReference<UUID>();
        h.ui(() -> tab.set(pane.tab().id()));
        var probe = new Probe();
        pendings.get(0).attach(probe.connection("bye"));
        h.flush();
        probe.exited.complete(0);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
        assertThat(probe.closes).hasValue(1);
        h.ui(() -> {
            assertThat(pane.isOpen()).isFalse();
            assertThat(alpha.get().terminals().tab(tab.get())).as("its tab closed with it").isEmpty();
            assertThat(alpha.get().terminals().window(window)).as("the window keeps its other tab").isPresent();
        });
    }

    @Test void stoppingAPluginCancelsWhatItIsStillConnecting() {
''', 1)])
A = "jasper-app/src/test/java/dev/jasper/app/"
edit(A + "workspace/WindowChromeMenusTest.java", [
    ('"View", "Pane", "Tab", "Session"', '"View", "Tab", "Session"', 2),
    ("    @Test void theClassicWindowMenuHoldsPaneThenTabThenTheToolWindows() throws Exception {",
     "    @Test void theClassicWindowMenuHoldsTheTabMenuThenTheToolWindows() throws Exception {", 1),
    ('            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.PANE)).set(List.of(new MenuEntry.Item("dev.x.split")));',
     '            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TAB)).set(List.of(new MenuEntry.Item("dev.x.split")));', 1),
    ('''            assertThat(window).as("Pane's items and the plugin's Pane section, Tab's items, then the toggles")
                .containsSubsequence("Split Right", "Next Prompt", "---", "split", "---", "Next Tab", "Previous Tab", "---", "Toggle Hosts");''',
     '''            assertThat(window).as("the Tab menu's group, prompt and tab items with the plugin's Tab section, then the toggles")
                .containsSubsequence("Split Right", "Next Prompt", "---", "Next Tab", "Previous Tab", "---", "split", "---", "Toggle Hosts");''', 1)])
edit(A + "workspace/ChromeRenderTest.java", [
    ('contains("Window").doesNotContain("Pane", "Tab");', 'contains("Window").doesNotContain("Tab");', 1),
    ('contains("Pane", "Tab").doesNotContain("Window");', 'contains("Tab").doesNotContain("Window");', 1)])
edit(A + "workspace/WindowChromeContributionsTest.java", [('"View", "Pane", "Tab", "Tools", "SSH"', '"View", "Tab", "Tools", "SSH"', 1)])
edit(A + "workspace/TabMenuTest.java", [
    ("    @Test void theFileViewAndPaneMenusNameTabsAndGroups() throws Exception {",
     "    @Test void theFileViewAndTabMenusNameTabsAndGroups() throws Exception {", 1),
    ('''            assertThat(items(window, "Pane")).containsExactly("Split Right", "Split Down", "Focus Group Left", "Focus Group Right",
                "Focus Group Up", "Focus Group Down", "Previous Prompt", "Next Prompt");''',
     '''            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Group Left", "Focus Group Right",
                "Focus Group Up", "Focus Group Down", "Previous Prompt", "Next Prompt", "Next Tab", "Previous Tab", "Rename Tab",
                "Select Tab 1", "Select Tab 2", "Select Tab 3", "Select Tab 4", "Select Tab 5", "Select Tab 6", "Select Tab 7",
                "Select Tab 8", "Select Tab 9");''', 1)])
edit(A + "plugins/KitCompatibilityTest.java", [("    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {\n", r'''    /** Workspace tabs plan 3b: a constant SDK 0.19 removed, the Pane menu or the pane exit policy, is refused at load. */
    @Test void theConstantsSdkZeroNineteenRemovedAreRefused() throws Exception {
        rejects(constant("dev.jasper.sdk.ui.StandardMenu","PANE"),"dev/jasper/sdk/ui/StandardMenu#PANELdev/jasper/sdk/ui/StandardMenu;","0.19");
        rejects(constant("dev.jasper.sdk.terminal.ExitPolicy","CLOSE_PANE"),"dev/jasper/sdk/terminal/ExitPolicy#CLOSE_PANELdev/jasper/sdk/terminal/ExitPolicy;","0.19");
        var current=candidate(">=0.19.0, <0.20",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.terminal.ExitPolicy","CLOSE")),false);
        try(var loader=PluginLoader.load(current,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as("the constant that took its place").isNotNull();}
    }
    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {
''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:test --tests 'dev.jasper.app.workspace.WindowChromeMenusTest' --tests 'dev.jasper.app.workspace.TabMenuTest' --tests 'dev.jasper.app.plugins.KitCompatibilityTest' --continue`
Expected: FAIL. `cannot find symbol ExitPolicy.CLOSE`; `WindowChromeMenusTest` still finds a Pane menu; `TabMenuTest`'s
Tab menu lacks the group items; `KitCompatibilityTest.theConstantsSdkZeroNineteenRemovedAreRefused` loads the plugin
that reads `PANE`.

- [ ] **Step 3: The SDK constants**

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java` with:

```java
package dev.jasper.sdk.ui;

/** The application's built-in menus; a plugin's entries form a section at the end of one. A presentation may show the
 * TAB items in a Window menu. */
public enum StandardMenu {
    /** The File menu. */
    FILE,
    /** The Edit menu. */
    EDIT,
    /** The View menu. */
    VIEW,
    /** The Tab menu: splitting and focusing tab groups, moving between prompts, and the tab commands. */
    TAB,
    /**
     * The Tools menu, for utility commands. Jasper has none of its own, so it shows only while a plugin has entries in
     * it, after Tab, Session and Transfer and before plugins' own menus. @since 0.16.1
     */
    TOOLS,
    /**
     * The Session menu, for connecting to hosts and working with sessions. Jasper has none of its own, so it shows only
     * while a plugin has entries in it, after Tab. @since 0.18.0
     */
    SESSION,
    /**
     * The Transfer menu, for moving files: file browsers, transfers and file servers. Jasper has none of its own, so it
     * shows only while a plugin has entries in it, after Session. @since 0.18.0
     */
    TRANSFER
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/ExitPolicy.java` with:

```java
package dev.jasper.sdk.terminal;

/** What happens to a provided session's pane, and so its tab, when the session ends. */
public enum ExitPolicy {
    /** The pane stays, shows how the session ended, and offers Reconnect. What plugins normally choose. */
    KEEP_OPEN,
    /**
     * The pane's tab closes once the final output has been shown, as {@code terminal.on_exit = "close"} closes a shell's.
     *
     * @since 0.19.0
     */
    CLOSE
}
```

- [ ] **Step 4: The Tab menu, the session policy and the gate**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit, span
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "contributions/MenuTarget.java", [("    public enum Slot { FILE, EDIT, VIEW, PANE, TAB, TOOLS, SESSION, TRANSFER }",
                                           "    public enum Slot { FILE, EDIT, VIEW, TAB, TOOLS, SESSION, TRANSFER }", 1)])
W = M + "workspace/"
edit(W + "MenuBarLayout.java", [
    ("     * @param view View\n     * @param pane Pane\n     * @param tab Tab\n     */\n    record BuiltIn(JMenu file, JMenu edit, JMenu view, JMenu pane, JMenu tab) { }",
     "     * @param view View\n     * @param tab Tab\n     */\n    record BuiltIn(JMenu file, JMenu edit, JMenu view, JMenu tab) { }", 1),
    ("    void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab);",
     "    void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> tab);", 1)])
edit(W + "StandardMenuBar.java", [
    ("/** Today's menu bar: File, Edit, View, Pane, Tab, Session, Transfer, Tools, then top-level menus as registered. */",
     "/** Today's menu bar: File, Edit, View, Tab, Session, Transfer, Tools, then top-level menus as registered. */", 1),
    ("return List.of(menus.file(), menus.edit(), menus.view(), menus.pane(), menus.tab());",
     "return List.of(menus.file(), menus.edit(), menus.view(), menus.tab());", 1),
    ("    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) { }",
     "    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> tab) { }", 1)])
edit(W + "ClassicMenuBar.java", [
    (" * The classic menu bar (spec 10.2): File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds\n * the Pane items, the Tab items and the tool-window toggles;",
     " * The classic menu bar (spec 10.2): File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds\n * the Tab items and the tool-window toggles;", 1),
    ('''    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) {
        // Window gathers Pane and Tab every pass, so they are built fresh with their sections, as Tools is.
        slots.put(MenuTarget.Slot.PANE, pane.get());
        slots.put(MenuTarget.Slot.TAB, tab.get());
    }''', '''    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> tab) {
        // Window gathers Tab every pass, so it is built fresh with its sections, as Tools is.
        slots.put(MenuTarget.Slot.TAB, tab.get());
    }''', 1),
    ('''    /** Pane's items, a rule, Tab's items, then a rule and the tool-window toggles. */
    private static JMenu window(Pass pass) {
        JMenu window = pass.menu().apply("Window");
        move(pass.slots().get(MenuTarget.Slot.PANE), window);
        if (window.getMenuComponentCount() > 0) window.addSeparator();
        move(pass.slots().get(MenuTarget.Slot.TAB), window);''', '''    /** Tab's items, then a rule and the tool-window toggles. */
    private static JMenu window(Pass pass) {
        JMenu window = pass.menu().apply("Window");
        move(pass.slots().get(MenuTarget.Slot.TAB), window);''', 1)])
C = W + "WindowChrome.java"
edit(C, [
    ("        JMenu pane = paneMenu();\n        JMenu tab = tabMenu();\n", "        JMenu tab = tabMenu();\n", 1),
    ("new MenuBarLayout.BuiltIn(file, edit, view, pane, tab)", "new MenuBarLayout.BuiltIn(file, edit, view, tab)", 1),
    ("        standardMenus.put(MenuTarget.Slot.VIEW, view); standardMenus.put(MenuTarget.Slot.PANE, pane);\n        standardMenus.put(MenuTarget.Slot.TAB, tab);\n",
     "        standardMenus.put(MenuTarget.Slot.VIEW, view); standardMenus.put(MenuTarget.Slot.TAB, tab);\n", 1),
    ("            // A layout that gathers Pane and Tab into another menu builds them fresh too.\n            layout.fresh(standardMenus, this::paneMenu, this::tabMenu);",
     "            // A layout that gathers Tab into another menu builds it fresh too.\n            layout.fresh(standardMenus, this::tabMenu);", 1)])
span(C, "    private static final ActionId[] PANE_ITEMS = {", "    private JMenu tabMenu() { return menu(\"Tab\", TAB_ITEMS); }\n", r'''    /** The Tab menu's group and prompt items, before its tab items (workspace plan 3b Rulings 7). */
    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_GROUP_LEFT,
        ActionId.FOCUS_GROUP_RIGHT, ActionId.FOCUS_GROUP_UP, ActionId.FOCUS_GROUP_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
    private static final ActionId[] TAB_ITEMS = {ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
        ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9};

    /** Tab: splitting and focusing groups and moving between prompts, a rule, then the tab commands. */
    private JMenu tabMenu() {
        JMenu menu = menu("Tab", GROUP_ITEMS);
        menu.addSeparator();
        for (ActionId id : TAB_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
        return menu;
    }
''')
edit(M + "plugins/HostedSessions.java", [("spec.onExit() == ExitPolicy.CLOSE_PANE", "spec.onExit() == ExitPolicy.CLOSE", 1)])
edit(M + "plugins/KitCompatibility.java", [(
    '''        "dev/jasper/sdk/terminal/TabHandle#panes()Ljava/util/List;")));''',
    '''        "dev/jasper/sdk/terminal/TabHandle#panes()Ljava/util/List;",
        "dev/jasper/sdk/terminal/ExitPolicy#CLOSE_PANELdev/jasper/sdk/terminal/ExitPolicy;",
        "dev/jasper/sdk/ui/StandardMenu#PANELdev/jasper/sdk/ui/StandardMenu;")));''', 1)])
K = "jasper-sdk-testkit/src/"
edit(K + "main/java/dev/jasper/sdk/testing/FakeSessions.java", [("spec.onExit() == ExitPolicy.CLOSE_PANE", "spec.onExit() == ExitPolicy.CLOSE", 1)])
edit(K + "main/java/dev/jasper/sdk/testing/FakePluginHost.java", [(
    "     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code TOOLS}, {@code SESSION},",
    "     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code TAB}, {@code TOOLS}, {@code SESSION},", 1)])
edit(K + "testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
    "     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code TOOLS},",
    "     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code TAB}, {@code TOOLS},", 1)])
PY
git grep -n -e 'Slot.PANE' -e 'StandardMenu.PANE' -e 'CLOSE_PANE' -e 'paneMenu' -e 'PANE_ITEMS' -e 'menus.pane()' -- jasper-app/src jasper-sdk/src jasper-sdk-testkit/src plugins docs/examples | grep -v -e 'KitCompatibility' && exit 1 || true
```

The last line prints nothing (`KitCompatibility` and its test name the removed constants on purpose).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.plugins.*' --continue`
Expected: PASS (`UiValuesTest` +1, `SessionValuesTest` +1, the contract suite +1 on both runtimes,
`KitCompatibilityTest` +1). Then `./gradlew check --continue`: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src
git commit -F - <<'EOF'
The Tab menu takes the Pane items; ExitPolicy.CLOSE

StandardMenu.PANE and the app's Pane slot fold into Tab, which reads
Split Right, Split Down, Focus Group, the prompts, a rule and the tab
commands, so the classic Window menu keeps its items. ExitPolicy.CLOSE_PANE
is ExitPolicy.CLOSE, terminal.on_exit's word; KitCompatibility refuses
both removed constants as 0.19's.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/plugin-authoring.md:192,221,293,295-299,347-350,423,697-698,852,864,869,883-884,917,949,1055`,
  `docs/plugin-ui.md:72`, `jasper-sdk/README.md:45,48,79`, `docs/sdk-architecture.md:13,147-153,340,426-427`,
  `docs/app-architecture.md:171`,
  `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentence true (AGENTS.md "Documentation edits"):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3b"); from edits import edit
edit("docs/plugin-authoring.md", [
    ("A panel is an owned `UiView` docked as a tool window in every terminal window, as in IntelliJ:",
     "A panel is an owned `UiView` docked as a tool window in every Jasper window, as in IntelliJ:", 1),
    ("its job; the content is yours. Dialogs belong to a terminal window or to one of your windows, so",
     "its job; the content is yours. Dialogs belong to a Jasper window or to one of your windows, so", 1),
    ("- Jasper stays running while any plugin window is open, even with no terminal window.",
     "- Jasper stays running while any plugin window is open, even when no other window is.", 1),
    ('''`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane
it was invoked on. Handles keep an id, never a Swing object, and two handles for the same pane are
equal.''', '''`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane
it was invoked on. A window's tabs are terminal tabs, each holding one pane, and content tabs such as an editor
(SDK 0.19: `TabHandle.kind()` and `terminal()`); a split opens a terminal tab in a new tab group beside the pane's
group. Handles keep an id, never a Swing object, and two handles for the same pane are
equal.''', 1),
    ("- **Threads.** Everything here is event-thread only, except `sendText`, `sendBytes` and `paste`,\n",
     "- **Tabs move between windows** (SDK 0.19). `TAB_OPENED`, `TAB_CLOSED` and `TAB_SELECTED` cover every tab, and a\n"
     "  tab that moves to another window reports `TAB_MOVED` (`TabMoved(tabId, fromWindowId, toWindowId)`) instead of a\n"
     "  close and an open; a moved terminal tab reports no `PANE_OPENED` or `PANE_CLOSED`, and its pane keeps its id. A\n"
     "  window the move empties reports `WINDOW_CLOSED`, and a window opened for the move `WINDOW_OPENED`, before the\n"
     "  `TAB_MOVED`. Ask `pane.tab().window()` again rather than keeping a pane's window.\n"
     "- **Threads.** Everything here is event-thread only, except `sendText`, `sendBytes` and `paste`,\n", 1),
    ("its message is shown. `ExitPolicy.CLOSE_PANE` closes the pane instead.", "its message is shown. `ExitPolicy.CLOSE` closes the pane's tab instead.", 1),
    ("registered panel in a terminal window. It creates the panel lazily and reuses that",
     "registered panel in a Jasper window. It creates the panel lazily and reuses that", 1),
    ("short-lived progress inside a terminal window. Set owned `UiView` content, supply a Cancel button",
     "short-lived progress inside a Jasper window. Set owned `UiView` content, supply a Cancel button", 1),
    ("Overlays accept terminal owners from the same host only.", "Overlays accept Jasper window owners from the same host only.", 1),
    ("`move(false)` returns to the last main workspace. Preserve the component/session during moves.",
     "`move(false)` returns to the last main workspace. Preserve the component/session during moves. The tab is a\n"
     "content tab of its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.", 1),
    ("including obsolete factory implementations, and SDK 0.18's renamed constants.",
     "including obsolete factory implementations, SDK 0.18's renamed constants and the members SDK 0.19 removed.", 1),
    ("off. The owner may be a terminal window, one of", "off. The owner may be a Jasper window, one of", 1),
    ("`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane`, `activateTerminalWindow`,\n"
     "`focusTerminalPane` and `closeTerminalPane` script a workspace;",
     "`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane` (a terminal tab's one pane), `addContentTab`,\n"
     "`moveTab`, `activateTerminalWindow`, `focusTerminalPane` and `closeTerminalPane` script a workspace;", 1),
    ("Invoke on the UI thread with a live terminal\nwindow or a shown plugin window.",
     "Invoke on the UI thread with a live Jasper\nwindow or a shown plugin window.", 1)])
edit("docs/plugin-ui.md", [("and the PANE and TAB items may appear in a Window menu.", "and the TAB items may appear in a Window menu.", 1)])
edit("jasper-sdk/README.md", [
    ('Bundled manifests require `sdk = ">=0.19.0, <0.20"` (0.18.0 is a breaking boundary:',
     'Bundled manifests require `sdk = ">=0.19.0, <0.20"` (0.19.0 is a breaking boundary, described under Tabs of every kind below; 0.18.0 is one too:', 1),
    ("\n## Shell variables, the file editor and program tabs (SDK 0.17.1)\n", r'''
## Tabs of every kind (SDK 0.19.0)

A window's tabs are terminal tabs and content tabs (an editor, a tool view, a plugin's view), and a split is a terminal
tab in a new tab group. `WindowHandle.tabs()` lists every tab in workspace order and `activeTab()` is the active
group's selected tab; `TabHandle.kind()` says which kind a tab is and `terminal()` is a terminal tab's one pane.
`Terminals.split` opens a terminal tab beside the pane's group. `TAB_OPENED`, `TAB_CLOSED` and `TAB_SELECTED` cover
every tab, and `TAB_MOVED` reports a tab moving to another window: a moved terminal tab reports no `PANE_OPENED` or
`PANE_CLOSED`, and its pane keeps its id; a window the move empties reports `WINDOW_CLOSED`, and a window opened for the
move `WINDOW_OPENED`, before the `TAB_MOVED`. The Tab menu (`StandardMenu.TAB`) holds the split, group-focus and prompt
items; `ExitPolicy.CLOSE` closes a session's tab when it ends. The testkit adds `FakePluginHost.addContentTab` and
`moveTab`, and `addTerminalPane` gives a terminal tab its one pane.

## Shell variables, the file editor and program tabs (SDK 0.17.1)
''', 1),
    ("pickers to a live terminal window or plugin surface.", "pickers to a live Jasper window or plugin surface.", 1)])
edit("docs/sdk-architecture.md", [
    ("| `dev.jasper.app.terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events, the derived active pane | `lifecycle` |",
     "| `dev.jasper.app.terminals` | App-native registry of Jasper windows, their tabs and terminal tabs' panes: pull-based entries, id-only events, the derived active pane | `layout`, `lifecycle` |", 1),
    ('''`workspace.WindowTerminals`: records of suppliers and callbacks that read the window's tabs and
panes on demand, so structure is never stale, plus id-only `TerminalEvent`s for what happens. The
registry derives one fact itself, the active pane (the focused pane of the selected tab of the
window used last), and reports it once per change; `atomically` keeps a tab or window close from
reporting a transient "no active pane".''', '''`workspace.WindowTerminals`: records of suppliers and callbacks that read the window's tabs, terminal and
content, and its terminal tabs' panes on demand, so structure is never stale, plus id-only `TerminalEvent`s for what
happens. The registry derives one fact itself, the active pane (the pane of the active tab of the window used last,
when that tab is a terminal tab), and reports it once per change; `atomically` keeps a tab or window close from
reporting a transient "no active pane". A tab moved to another window is reported once, as `TabMoved`, by the window
it arrives in: `Tab.movingFrom()` tells both windows that a detach and an attach belong to a move, and a window that
fails to show a tab it announced takes the announcement back.''', 1),
    ('''Terminal enumeration and active-pane APIs continue to
report terminal content only.''', '''`WindowHandle.tabs()` lists these tabs as content
tabs, which have no terminal.''', 1),
    ("HostedUi validates the terminal owner and AuxiliaryWindows reserves",
     "HostedUi validates the owning Jasper window and AuxiliaryWindows reserves", 1)])
edit("docs/app-architecture.md", [(
    "| `terminals` | App-native registry of terminal windows, tabs and panes:",
    "| `terminals` | App-native registry of Jasper windows, their tabs (terminal and content) and terminal tabs' panes:", 1)])
PY
git grep -n -i 'terminal window\|terminal owner' -- docs/plugin-authoring.md docs/plugin-ui.md jasper-sdk/README.md docs/sdk-architecture.md
python3 -I - <<'PY'
import pathlib, re
for name in ["docs/plugin-authoring.md", "docs/plugin-ui.md", "jasper-sdk/README.md", "docs/sdk-architecture.md"]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    for hit in re.finditer(r"terminal\s*\n\s*(window|owner)", text, re.I): print(name, text.count("\n", 0, hit.start()) + 1)
PY
```

Both commands print nothing: no guide says "terminal window" or "terminal owner", on one line or across two.

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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 3a's
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 3b: the SDK's tab and pane break — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-18-workspace-3b-sdk-tabs.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). The SDK is 0.19.0, a breaking boundary: every
in-repo manifest, the example external plugin and every guide range declare `sdk = ">=0.19.0, <0.20"`, plugin versions
are unchanged, and the SDK's and the testkit's Javadoc, the exception messages both runtimes throw at plugins and the
guides say "Jasper window". `TabHandle` covers every tab: `kind()` (`TERMINAL`, `CONTENT`) and `terminal()` replace
`panes()` and `activePane()`; `WindowHandle.tabs()` lists every tab in workspace order and `activeTab()` is the active
group's selected tab of either kind; `Terminals.split` opens a terminal tab in a new group beside the pane's group and
`openTab` one in the active group. `TAB_OPENED`, `TAB_CLOSED`
and `TAB_SELECTED` cover content tabs, and `TAB_MOVED` (`TabMoved(tabId, fromWindowId, toWindowId)`) reports a move
between windows in place of a close and an open; a moved terminal tab reports no `PANE_OPENED` or `PANE_CLOSED` and its
pane keeps its id, a window the move empties reports `WINDOW_CLOSED`, and one opened for it `WINDOW_OPENED`, before the
`TAB_MOVED`, and a window that fails to show an arrival takes it back (the move back, or a close). `StandardMenu.PANE` and
`MenuTarget.Slot.PANE` are gone: the Tab menu reads Split Right, Split Down, Focus Group, the prompts, a rule and the
tab commands. `ExitPolicy.CLOSE_PANE` is `ExitPolicy.CLOSE`. App side: `TabEntry(id, windowId, kind, terminal, title,
select)`, `WindowEntry` and `WindowTerminals` list every tab, `TerminalEvent.TabMoved`, `Tab.movingFrom()`,
`WindowTerminals.tabWithdrawn`; `KitCompatibility` refuses the four removed members as 0.19's. The testkit and the app's contract fixture hold one pane
per terminal tab, split into a tab of their own, list a plugin's content as a `CONTENT` tab of its window and close a
window with its last tab; `FakePluginHost.addContentTab` and `moveTab` and `ContractHarness.addContentTab` and `moveTab`
are new, and `PluginContractTest` checks content tabs, moves and `ExitPolicy.CLOSE` on both runtimes. Remote reads
`TabHandle::terminal`; File Server's tests give a second pane its own tab. Rulings (plan, "Rulings"): <one clause each,
1 to 15>. Deviations: <none, or each with its reason>. THD Sourcing Crypto
(`~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at `3740e4e`, not yet rebuilt for 0.18)
stays skipped. Plan 4 removes `Windows.create`, `PluginWindow` and `WindowSpec` (spec 14) and bumps the SDK again, so
rebuild it once, after the series' last SDK bump, against the jars `sdkRelease` then publishes; that rebuild includes
0.19's changes: its `plugin.toml` (`sdk = ">=0.16.1, <0.18"`) and `PluginPackagingTest.java:49` move to the final range,
and `build.gradle.kts:8` and `README.md:28` name the final version; 0.18's rename still applies (`CryptoForm.java:112`
and `CryptoFormTest.java:61` read `ChoiceKind.SEGMENTED`, now `EXCLUSIVE`); `CryptoFormTest.java:39-41` adds two panes
to one tab, which the testkit refuses from 0.19: give `other` its own tab
(`host.addTerminalPane(host.addTerminalTab(window, "zsh"), zsh())`), after which focusing `other` selects its tab. Its
main code (`CryptoPlugin.java:30`, `:37`: `StandardMenu.TOOLS`, `Terminals.activePane()`) needs nothing for 0.19. Left
for plan 4: `WindowHandle.isMain()`, `TabPlacement`, the `ContentTab` move API, `WINDOW_*` from child windows, panels in
the main window only; the fakes' rule that the last tab takes its window with it, which the main window will not
follow; `FakeWorkspace` and `TerminalFixture` keep each window's tabs in insertion order and add a split's or
`openTab`'s tab at the end, while the application puts a split's group beside the target's group and
`WindowHandle.tabs()` promises group-by-group order, so `TabPlacement` needs a group model in both fakes; and the
application's user-facing "terminal window" (the shortcut help's `GLOBAL` row, Remote's connect and sign-in messages,
the user guides; Rulings 11). `./gradlew check --continue`: **<counts>**. GUI checks for the user: the Tab menu in
the standard bar and the classic Window menu (no Pane menu); a Remote SSH split opens in a new group; Remote's Split
with Same Host disabled while an Editor tab is active and enabled again on an SSH tab; Open in Separate Window on an
Editor tab and back (the separate window closes with its last tab). Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to
"**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text
there, including every anchor re-found on 3a's final text.

```bash
git add docs jasper-sdk/README.md
git commit -F - <<'EOF'
Workspace tabs: documentation and status for the SDK's tab break

The guides describe tabs of both kinds, splits as tab groups, TAB_MOVED,
the Tab menu and ExitPolicy.CLOSE, and say "Jasper window"; STATUS records
plan 3b with its rulings, the THD Sourcing Crypto hand-off and the full
build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 14's `TabHandle` row (Task 2), `PaneHandle` (Task 2: its tab is the terminal tab), `WindowHandle.tabs()`
  and `activeTab()` (Task 3; `isMain()` is plan 4's), `Terminals.activePane()` unchanged in meaning (Tasks 2, 3),
  `Terminals.split` and `openTab` (Task 2's Javadoc and fakes; 3a's application code), Events (Task 3; child windows'
  `WINDOW_*` are plan 4's), `StandardMenu.PANE` (Task 4), Wording (Tasks 1, 5); the in-repo plugins (Tasks 1, 2), the
  testkit fakes and the contract suites on both runtimes (Tasks 2-4), the THD hand-off (Task 5); 9.4's `TabEntry` and
  every-tab registry (Tasks 2, 3); 17's SDK contract tests on the app and the testkit (Tasks 2-4); 16's clean break
  (`KitCompatibility`, no shims). Out of scope here: plan 4's and plan 6's rows of spec 14.
- Every text this plan quotes from a file plan 3a creates or changes has a count in "Before you start" (checked against a
  replay of 3a's committed plan), and the plan says how to re-anchor where 3a's execution differs.
- Type consistency: `TabHandle.Kind`, `TabEntry.Kind`, `TabMoved(tabId, fromWindowId, toWindowId)` (SDK and app),
  `Tab.movingFrom(): Optional<UUID>`, `tabArrived(Tab, Optional<UUID>)`, `tabWithdrawn(Tab, Optional<UUID>)`,
  `FakeWorkspace.Tab.pane`, `addContentTab` and `moveTab` keep one name and one signature from the task that creates
  them to the last task that calls them.
