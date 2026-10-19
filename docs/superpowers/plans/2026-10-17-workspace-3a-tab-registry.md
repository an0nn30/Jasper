# Workspace tabs 3a: the tab registry and the workspace view Implementation Plan

**Status: executed on 2026-10-19** (`f623a24bc..ea571cec8`, then the documentation commits and the final review's
fixes); see `docs/STATUS.md`.
Deviations from the plan's text: (a) Task 2:
`SplitContainerTest.aDividerDragMovesItsBoundaryAndReportsTheWeightsOnRelease` releases at the divider-relative `2, 50`,
and Task 3's `WorkspaceViewTest.aDividerDragWritesTheLayoutsWeights` at `2`, because AWT delivers a release relative to
the divider where it now is (the verbatim release was a second move); (b) Task 2's review round changed
`SplitContainer`: a press and release, or a drag back to its start, reports no weights (the plan's exact comparison
reported about 60% of non-dyadic weights), and a boundary clamps to `[min(least, start[i]), pair - min(least,
start[i+1])]` instead of a plain tenth, so a press never snaps one and a divider whose pair is under a fifth stays
draggable (it can grow a narrow side, never shrink one further; two adjacent columns both under a tenth cannot trade
space directly, they widen through their other dividers); `SplitContainerTest` has 10 tests, not 7; (c) Task 3 Step 6
also ports `TerminalTabTest`'s `reorderTab(0, 1)` to `workspace().activeGroupView().reorder(...)`, since Task 3 deletes
`reorderTab` and Task 5 replaces the file; (d) Task 3's
`WindowPanelsTest.theStripesAndSidePanelsReachTheToolbarAndTheBottomSpansStripeToStripe` takes `deck` as the selected
tab's component after the `tabStrip()` to `workspace()` port, because the workspace now holds the strip (assertions
unchanged), and `WindowTabs.capped` and `WindowContent.tabTitle` keep the Java escapes of the ellipsis and the bullet
where the plan text carries the characters; (e) Task 3's fix round: `Tab.dispose` isolates its listeners (the first
failure is rethrown, later ones suppressed) and always disposes its content, `TabRegistry.remove` always detaches, the
`addHost` unregister removes every tab before it forgets the host, and `WindowContent.close` guards its registry leave,
so a throwing listener never leaks a session or a window; five tests (`WorkspaceViewTest` has 10, not 8); (f) Task 4's
`TabRegistryTest` insertion anchor was re-found, since the fix round had changed the end of the class; (g) Task 5, D1:
`workspaceChanged` tells the palette only when the selected tab changed, as the old deck did, because the plan's Task 3
code and Task 5 test contradicted each other (the palette stays open across a maximize); (h) Task 5, D2: a closed
`TerminalContent` reports no pane to the terminal registry, so `ActivePaneChanged(empty)` precedes `WindowClosed` as
before when `WindowContent.close` closes a window; when a window's last tab closes, `WindowClosed` now comes before
`ActivePaneChanged(empty)`;
(i) Task 5 closes again what a failed attach announced: the pane is wired before it is announced, the terminal
registry hears `PaneOpened` before the activity's OPENED, and a failure ends in `PaneClosed`, `TabClosed` and a CLOSED
for every OPENED, with `WorkspaceTabsTest.aFailedAttachClosesWhatItAnnounced` (11 tests, not 10); (j) Task 5's
mechanical test ports beyond the brief: `WorkspaceViewTest.shellOf` reads `.pane().session()`, `WindowCommandsTest`
expects the split commands while a pane is pending (Rulings 11), `ConfigurationControllerTest` counts four tabs, not
three (a split is a tab); (k) `ContentRegistryTest` has 10 cases, not 11, so `TabRegistryTest` has 13 after Task 1, 15
after Task 3 and 16 after Task 4 (the plan says 14, 16 and 17), and 19 as built (the fix round's three tests); Task 1
touched 22 test files, not 21; `LayoutGeometryTest`'s class Javadoc loses only "today's" and keeps its citations of the
deleted `SplitTreeTest`; `Layout.unmaximize`'s Javadoc keeps "plans 4 and 5 call it"; (l) Task 7 rewords
`docs/app-architecture.md` and `docs/app-maintenance.md`, which named the removed `WindowContent.configurePane`, to
`TabHost.configure`, and leaves `TAB_REVEAL_INSET` in the dated history of `docs/STATUS.md`,
`docs/design/theme-engine/temporary-paths.md` and `docs/design/theme-engine/migration.csv`;
(m) final review: `TabRegistry.requestClose` removes every approved tab whatever another's removal throws (through the
new `TabRegistry.removeAll`), always ends its pending close and completes its result, and logs a failure as a warning,
since a rethrow would end in a future nobody reads; `Tab.keep`, and with it `open`'s rollback, never suppresses a
failure under itself; a layout change gives the active tab focus only while the palette's card does not hold the
window's input and the permanent focus owner was in the workspace, nowhere, or not showing (a closed terminal, or the
palette that just closed to run a command), so focus in the open palette, the Run window or a tool window stays there;
Close Others and Close Group of terminal tabs alone close at once, without the registry's approval; every tab's
`Tab.active()` follows its window's active tab, terminal tabs included; a group outside the active one shows no Select
Tab shortcut; a window stops hearing the focus manager first when it closes, and when its first tab fails in its
constructor; the `WorkspaceHost.detach`, `WindowContent.detach`, `connectContributions` and `closeTabs` Javadoc and
`docs/windows-startup-verification.md`'s Ctrl+Enter row are corrected; ten tests (`TabRegistryTest` 22, `TabMenuTest` 8,
`WorkspaceTabsTest` 14, `WorkspaceViewTest` 11). Every other anchor and all
other code and test text were applied as written.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every tab of every window lives in one application-wide `TabRegistry` (which absorbs `ContentRegistry`), each
window renders its tabs as a `Layout` of tab groups in n-ary splits with an always-visible strip per group, Cmd+D and
Cmd+Shift+D open a terminal tab in a new group beside the active one, and per-terminal splits are gone. The
plugin-facing SDK keeps its shape; plan 3b changes it.

**Architecture:** `dev.jasper.app.contributions` gains `TabRegistry`, `Tab`, `TabContent`, `HostedContent` and
`WorkspaceHost` (the registry's view of a window), replacing `ContentRegistry`, `ContentEntry` and `ContentHost`; the
registry never looks inside a tab's content, so `dev.jasper.app.workspace` adds the second content kind,
`TerminalContent` (one `TerminalPane`). Each `WindowContent` renders its tabs through `WorkspaceView`, which holds the
window's plan-2 `Layout` and draws it as `SplitContainer`s over `GroupView`s; each `GroupView` has its own `TabStrip`
(the equal tabs or the classic ones) over a card area. A `TabHost` per window wires a terminal tab's pane to that window
(shortcut, context-menu and file-link handlers, activity and terminal-registry facts, theme and opacity); a tab moved
between windows through the registry is detached from one host and attached to the next with its session untouched.
Focus going into a tab, a terminal's pane or a plugin's view, makes it the window's active tab.
`WindowCommands`, `WorkspaceActions`, `ActionId`, the menus, the palette and the Keymap move to groups:
`close_group`, `maximize_group` and `focus_group_*` replace `close_pane`, `zoom_pane` and `focus_pane_*`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1, 2.2, 2.3, 2.6, 3, 4, 7, 9, 10, 11, 15, 16, 17 and 18). The first half of section 18's plan 3; plan 3b
(the SDK break and the plugins) follows it. Plan 2
([`2026-10-17-workspace-2-layout-model.md`](2026-10-17-workspace-2-layout-model.md)) supplies `dev.jasper.app.layout`;
plan 1 ([`2026-10-17-workspace-1-single-instance.md`](2026-10-17-workspace-1-single-instance.md)) executes before this
one too.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`
(on `main` at `4ed94fb78`), after plans 1 and 2, which have executed. Run every command from the worktree root. Line
numbers are from `93aa015fd`, plan 2's last commit, jasper-ui's role catalog lines included (fact 17); the two
`JasperApplication` anchors this plan uses (`contributions.content().setFactory(separate -> {` twice, and
`windowsMayClose`) are found by their quoted text. Every edit quotes the text it replaces, and the edit scripts stop with a message when that text is not
found the stated number of times: re-find the quoted text, adjust the anchor and re-run; never edit around a missing
anchor. Later tasks anchor on text earlier tasks wrote; the plan quotes that text as those tasks leave it.

## Spec issues

None. The series split puts spec 9.4's `TabEntry` change (a kind, no pane list) and spec 14's SDK rows in plan 3b, as the
coordinator's brief for this plan says; nothing here contradicts them.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (spec 4, 16): `TerminalTab`, `SplitTree`, `TerminalDeck`, `SplitAxis`, `ContentRegistry`, `ContentEntry`,
  `ContentHost`, `close_pane`, `zoom_pane` and `focus_pane_left`/`_right`/`_up`/`_down` go outright, with their tests;
  saved key bindings for removed ids become unknown keys (spec 11). No shim, no migration, no "was removed" text.
- The SDK keeps its shape (plan 3b changes it): no file under `jasper-sdk`, `jasper-sdk-testkit` or `plugins` changes;
  `HostedTerminals` answers `TabHandle.panes()` and `activePane()` with a terminal tab's one pane, `WindowHandle.tabs()`
  with terminal tabs only, and `Terminals.split` with a terminal tab in a new group; TAB events stay terminal-only.
- Windows stay as they are (plan 4 changes them): several `TerminalWindow`s, each with one workspace; a window closes
  when its last tab closes; "Open in Separate Window" and "Move to Main Window" keep working for hosted tabs.
- No motion is added (spec 4): splits, moves, reorders and maximize are instant. The equal tabs keep their existing open
  and close motion under the animation settings, for a tab that opens or closes only, never for one that joins or
  leaves a group by a move (Rulings 27).
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture` checks the graph is acyclic):
  `contributions` (Task 1), `workspace` (Task 2), `terminals`, `plugins` and `benchmark` (Task 5) gain
  `dev.jasper.app.layout`; nothing else gains an edge. `dev.jasper.app.layout` stays JDK-only.
- The view never chooses a group's selection or a layout's active group: it renders `Group.selected()`,
  `Layout.activeGroup()` and `Layout.maximized()` as plan 2's operations leave them (plan 2 keeps a per-group order of use
  for the tab selected after a removal and keeps a surviving maximized group maximized). It asks the layout to change
  only for a user's gesture: a click, a key, a drag, focus going into a tab (Rulings 26), or a tab moving between windows
  (Rulings 17).
- No new method on a `Component` subclass reuses the name of a method AWT's `Component` or `Container` declares,
  deprecated or not, unless its parameter types keep every call from resolving to the AWT one (`WorkspaceView`'s
  `remove(Tab)` and `contains(Tab)`); none is called `layout`, `show`, `move`, `resize`, `size`, `bounds`, `location` or
  `action` (fact 13, Rulings 29).
- The theme-migration ledger: `ThemeMigrationCoverageTest` (`appearance/ThemeMigrationCoverageTest.java:17-18`) requires a
  `docs/design/theme-engine/migration.csv` row for every Java file under `jasper-app/src` whose text matches its
  `CANDIDATE` pattern (`import javax.swing`, `import java.awt`, the word appearance, `paintComponent`, `new J…`, or
  `new …List`, `…Panel`, `…View`, `…Window`, …), and every path a row names (its source and its evidence tests) must
  exist. Each task that creates, renames or deletes such a file edits the ledger in the same commit with the
  `ledger.py` helper below, and runs the coverage test.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment (write `{@code}`; never
  "->" in prose), and every `{@link}` must resolve; comments of public types link only public members.
- `WindowContent`, `GroupView` and the test files import both `java.awt.*` and `java.util.*`: write `java.util.List`
  in full there, as `WindowContent` already does.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").

### The two helper scripts

Tasks call two helpers. Create them once, before Task 1, in the scratch directory `build/plan3a/` (ignored by git; never
commit them):

```bash
mkdir -p build/plan3a
cat > build/plan3a/edits.py <<'PY'
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
PY
cat > build/plan3a/ledger.py <<'PY'
"""Line-wise edits of docs/design/theme-engine/migration.csv; untouched lines stay byte for byte."""
import csv, io, pathlib, sys

LEDGER = pathlib.Path("docs/design/theme-engine/migration.csv")

def _parse(line):
    return next(csv.reader([line]))

def _format(fields):
    out = io.StringIO()
    csv.writer(out, lineterminator="").writerow(fields)
    return out.getvalue()

def _lines():
    text = LEDGER.read_text(encoding="utf-8")
    if not text.endswith("\n"):
        sys.exit("migration.csv must end with a newline")
    return text[:-1].split("\n")

def _save(lines):
    LEDGER.write_text("\n".join(lines) + "\n", encoding="utf-8")

def _cite(fields, old, new):
    tests = []
    for test in fields[6].split(";"):
        test = new if test == old else test
        if test and test not in tests:
            tests.append(test)
    fields[6] = ";".join(tests)

def rename(old, new, surface):
    """Moves old's row to new (and its surface name) and every citation of old to new."""
    lines, hits = _lines(), 0
    for i, line in enumerate(lines[1:], 1):
        before = _parse(line)
        fields = list(before)
        if fields[0] == old:
            fields[0], fields[2] = new, surface
            hits += 1
        if old in fields[6].split(";"):
            _cite(fields, old, new)
        if fields != before:
            lines[i] = _format(fields)
    if hits != 1:
        sys.exit(f"expected one row for {old}, found {hits}")
    _save(lines)

def drop(path, successor):
    """Deletes path's row; its citations elsewhere move to successor."""
    lines, kept, hits = _lines(), [], 0
    for line in lines:
        fields = _parse(line)
        if fields[0] == path:
            hits += 1
            continue
        if path in fields[6].split(";"):
            _cite(fields, path, successor)
            line = _format(fields)
        kept.append(line)
    if hits != 1:
        sys.exit(f"expected one row for {path}, found {hits}")
    _save(kept)

def add(path, owner, surface, evidence, disposition):
    """Appends a new file's row: a native workspace or contributions helper of this series."""
    lines = _lines()
    if any(_parse(line)[0] == path for line in lines[1:]):
        sys.exit(f"{path} already has a row")
    lines.append(_format([path, owner, surface, "nonvisual-helper", "none", "none", evidence, disposition]))
    _save(lines)
PY
```

A task's ledger step runs, for example,
`python3 -I -c 'import sys; sys.path.insert(0, "build/plan3a"); import ledger; ledger.add(...)'`, from the worktree
root. `edits.py` is used the same way.

## Review Focus

1. Cmd+D, then the new shell exits under `terminal.on_exit = "close"`: its group collapses, the sizes before the split
   come back exactly, and the original terminal has focus and is undimmed. Task 5:
   `WorkspaceTabsTest.aSplitWhoseShellExitsCollapsesAndRestoresTheSizes`.
2. A divider dragged past its neighbour: each side keeps a tenth of the split, nothing collapses to zero, the layout gets
   the weights once on release, and a theme change afterwards moves no divider. Task 2:
   `SplitContainerTest.aDragKeepsATenthOnEachSide`; Task 3: `WorkspaceViewTest.aThemeChangeKeepsTheDividers`.
3. The activity's "go to" (what a notification card's click and the terminal registry's focus run) for a command that
   finished in a terminal tab that has since moved to another window: that window comes to the front with the tab
   selected; the old window is not raised by it. Task 4: `TabMoveTest.goToFollowsAMovedTab`. An operating-system
   notification's click still raises the window the command finished in first (plan 4's hand-off, Rulings 18).
4. Close Group over a group holding a plugin tab whose guard says no and a terminal tab: nothing closes and the shell
   keeps running; when the guard agrees, both close and the group goes. Task 6:
   `TabMenuTest.closeGroupAsksTheGuardsFirstAndARefusalClosesNothing`.
5. Cmd+D beside a terminal whose shell ended, then beside a plugin view: each opens a terminal tab in a new group with
   the default profile (the ended shell's directory, then where a new tab starts); New Tab opens in the plugin view's
   group. Task 5: `WorkspaceTabsTest.splitsBesideAnEndedShellOrAPluginViewOpenWithTheDefaultProfile`.
6. Two groups; a click into the inactive group's terminal, or into a plugin view there: that tab becomes the active
   tab, its strip active, the terminal undimmed, and Cmd+W closes it, not the tab that was active; focus is not moved
   again. Task 5: `WorkspaceTabsTest.focusGoingIntoAnotherGroupsTerminalMakesItsTabActive`,
   `focusGoingIntoAPluginViewInAnotherGroupMakesItsTabActive`; `ApplicationActionsTest.focusingFindFieldRetargetsGlobalPaneActions`.
7. A tab moved into or out of a window with a maximized group ends that maximize on both sides; closing a tab of a
   maximized group keeps it; the move plays no strip motion. Task 4:
   `TabMoveTest.aMoveEndsTheMaximizeOnBothSidesAndClosingATabKeepsIt`; Task 3:
   `WindowTabMotionTest.movesAndReordersTakeTheirPlaceAtOnce`.

## Verified facts

At `93aa015fd`.

1. `contributions/ContentRegistry.java:12-215` owns hosted tabs: hosts by window id (`:24-31`; unregistering a host
   removes, so disposes, the entries it holds), `open` reuses a (plugin, key) entry and mounts it again when it lost its
   window (`:45-77`), `move(entry, separate)` with rollback to the old or another main host (`:97-129`), `remove`
   disposes then unmounts (`:131-137`), batched per-plugin approval (`:139-175`), `requestClose` (`:177-188`) and the
   synchronous `mayClose` used by native quit (`:190-200`). `ContentEntry` (`:9-55`) holds presentation, commands,
   status, focus target, pins and the window id; `ContentHost` (`:7-8`) is `(id, detached, mount, remove, select,
   toFront)`. `ContentRegistryTest` has 11 tests. `Contributions.content()` (`Contributions.java:28-29`) is the one
   instance.
2. `workspace/WindowContent.java`: the deck is `TerminalDeck` (`:59`), the strip `look.tabs(this)` (`:61`, `:267`) in the
   terminal column's NORTH over the deck (`:268-272`); the deck's change listener publishes the selection, tells the
   palette, updates and focuses (`:291-298`); `connectContributions` registers a `ContentHost` only when `hostsContent`
   (`:854-867`; the warm-up rehearsal passes false, `JasperApplication.java:1090`); `mountContent`/`unmountContent`
   (`:869-896`); `selectedContent`, `tabTitle`, `tabIcon`, `tabTooltip`, `tabMenu`, `focusSelected`, `mayClose`
   (`:897-916`); `terminalTabs` (`:828-832`); `openTab` (`:994-1015`); `connectActivity` (`:1017-1052`) and
   `configurePane` (`:1056-1083`) wire a pane to the window; `selectTab`, `reorderTab`, `closeTab` (`:1097-1127`);
   `update` (`:1212-1229`); `applyTheme` with `TerminalTab.begin/endThemeUpdate` (`:1327-1363`); `close` (`:1512-1547`).
3. `workspace/TerminalTab.java`: a split starts in `target.inheritableDirectory()` with `target.profile()` unless a
   request is given (`:108-124`); a failed pane closes unless it is the window's only one (`:70-74`); divider drags clamp
   to 0.1-0.9 (`:218-226`); theme updates restore dividers from the model (`:231-252`).
4. `WindowTabs` hides itself with one tab (`:95`, `:116`) and plays a "reveal" when its second tab arrives (`:55-62`,
   `:80-88`); `ClassicTabs` always shows (`:100-101`). Both read the window: `owner.tabStrip()`, `selectedComponent()`,
   `tabTitle`, `tabIcon`, `tabTooltip`, `tabShortcut`, `tabMenu`, `selectTab`, `closeTab`, `reorderTab`, `invoke(NEW_TAB)`.
   `TabStrip` (`TabStrip.java:11-24`) is sealed over the two; `WorkspaceChrome.tabs(WindowContent)` makes one
   (`WorkspaceChrome.java:94-100`).
5. `WorkspaceActions.java:42-90`: `close_pane` (cmd+w) closes the focused pane, `close_tab` (alt+cmd+w) the tab,
   `split_*`, `zoom_pane` and `focus_pane_*` act on `currentTab()`; `select_tab_N`, `next_tab` and `previous_tab` act on
   the deck (`:67-71`, `:102-104`); a content tab handles `close_tab`, `close_pane`, `next_tab`, `previous_tab` and its
   own find/copy/paste commands (`:40-45`, `:116-117`). `ActionId.java:11-23` defines the ids and keys.
6. Menus listing the pane actions: File (`WindowChrome.java:80`), View's Zoom Pane item (`:91`), the toolbar's maximize
   button (`:125`), the Pane menu (`:437-443`), the terminal context menu (`:564-567`), the classic header's Maximize
   (`ClassicHeader.java:46`); `MenuBarLayout`, `StandardMenuBar` and `ClassicMenuBar` place menus by slot and name no
   action. `WindowCommands.java:37-67` adds palette keywords and icons; `:137-138` titles Zoom Pane. `ShortcutCatalog
   .java:84-89` files the pane actions under "Panes".
7. `terminals/PaneEntry.java:16-19`: `split` is a `BiFunction<SplitAxis, OpenSpec, Optional<PaneEntry>>`;
   `HostedTerminals.java:271-282` maps `Direction` to `SplitAxis`; `WindowTerminals.java:46-57` splits within the tab.
8. `TerminalView` keeps its session across `removeNotify`/`addNotify` (spec 2.3); `TerminalPane.close()` resets every
   callback (`TerminalPane.java:594-615`).
9. `FakeConnector` is `jasper-terminal`'s internal test fixture (`jasper-terminal/src/test/.../FakeConnector.java`), not on
   jasper-app's test class path; jasper-app's in-process session is `testsupport/IdleTerminalSession`, used through
   `DesktopTestSupport.idleLauncher`.
10. `ThemeManager.java:17-24` sets `SplitPane.dividerSize` to `NATIVE_SPLIT_DIVIDER` (8); `ChromePainter.splitDivider`
    (`ChromePainter.java:248-256`) paints the `SPLIT_STROKE` rule in `border.window`.
11. `BenchmarkRun.java:214` splits the current tab, `:280-357` reads `currentTab().panes()`.
12. Plan 2's `Layout` (its Tasks 1-3, as revised): `open`, `select`, `activate`, `move`, `remove`, `split` (refuses the
    empty workspace), `moveToNewGroup`, `resize(List<Integer> path, List<Double> weights)`, `toggleMaximize`,
    `unmaximize`, `neighbour`, `bounds`, `root`, `groups`, `group`, `groupOf`, `tabs`, `contains`, `isEmpty`,
    `activeGroup`, `activeTab`, `maximized`, `recent`; `Layout.empty()`, `Layout.of(TabId)`; `Group(GroupId id,
    List<TabId> tabs, Optional<TabId> selected)`, `Split(Orientation, List<Child>)`, `Child(Node, double weight)`,
    `Side { LEFT, RIGHT, UP, DOWN }` with `orientation()`.
13. AWT's `Container` declares `public void layout()` (deprecated), and `Component` declares `show()`, `show(boolean)`,
    `move(int, int)`, `resize(int, int)`, `resize(Dimension)`, `size()`, `bounds()`, `location()` and
    `action(Event, Object)` (`javap -protected java.awt.Component java.awt.Container javax.swing.JComponent`). A
    `JPanel` subclass that declares `Layout layout()` does not compile: the return type is incompatible and package
    access is weaker than public. Checked against every Swing class this plan creates or replaces (`WorkspaceView`,
    `GroupView`, `SplitContainer`, `TerminalContent`, `WindowTabs`, `ClassicTabs`): with `model()`, `moveTab`,
    `resizeSplit` and `display`, only `WorkspaceView.remove(Tab)` and `contains(Tab)` share a name with an AWT method,
    with a parameter type no AWT overload takes.
14. Focus: `TerminalTab.java:68` sets `pane.onFocused = () -> focus(pane)`, and `focus` (`:128-134`) makes the pane its
    tab's focused pane without requesting focus; `TerminalPane.trackFocus` (`TerminalPane.java:287-288`, `:416-424`)
    runs `onFocused` when the view, the find bar or a child of theirs gains focus.
    `ApplicationActionsTest.focusingFindFieldRetargetsGlobalPaneActions` (`:80-97`) depends on it.
15. Notifications: the activity listener `JasperApplication` adds when it composes a window passes that window's id to
    `CommandNotifier.finished` (`JasperApplication.java:526-528`), which makes it the request's target
    (`CommandNotifier.java:158`); a click on the operating system's notification raises the target window, then runs
    the activity's `activate` (`SystemNotices.java:87-99`); an in-app card shows in the target window
    (`NotificationsFeature.java:147`).
16. `WindowTabs.refresh` (`WindowTabs.java:47-98`) picks the open curve when a tab entered the strip and the close
    curve when one left (`:53-54`, `:80`), whatever the reason.
17. `DesignMetrics.Role.TAB_REVEAL_INSET` (`jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:97`)
    is read only by the reveal (`WindowTabs.java:87`); `classic-roles.tsv:433` and `compact-roles.tsv:433`
    (`jasper-ui/src/test/resources/dev/jasper/ui/metrics/`) freeze it for `ClassicCatalogTest` and `DesignMetricsTest`,
    whose role lists must equal `Role.values()`; `WorkspaceMetricsTest.java:24,66` names it.

## Rulings

Record them in `docs/STATUS.md` (Task 7).

1. **The registry lives in `contributions`: `TabRegistry`, `Tab`, `TabContent` (an interface), `HostedContent` and
   `WorkspaceHost` (a record of the window's callbacks), with an edge to `layout` for the ids; `workspace.TerminalContent`
   is the second `TabContent`** — `contributions` is below both `workspace` and `plugins` in the package graph and
   `TerminalPane` lives in `workspace`; the registry never looks inside content.
2. **Placeholder content is plan 6's** — nothing in 3a creates a placeholder; `Tab.Kind` is `TERMINAL` and `HOSTED`, and
   plan 6 adds `PLACEHOLDER` with its content and view when restoring first needs it.
3. **A window starts on a registry of its own and joins the application's when `connectContributions` hosts content,
   taking its tabs along (`TabRegistry.adopt`); the warm-up rehearsal and unconnected test windows never join** — a
   window builds its first tab before it is connected, and the rehearsal must stay invisible to the registry.
4. **Terminal tabs have no close guard: they close at once (Close Tab, a shell's exit, a failed launch); hosted tabs
   keep per-plugin approval. `inWindow` lists every tab of a window for the window-close approval; `hosted()` lists the
   plugin tabs, which Quit asks** — today's behaviour for both kinds.
5. **`TabRegistry.add(TabId, Kind, TabContent, UUID window)` registers a tab its window places itself; `open` (hosted)
   and `move` call the window's `WorkspaceHost.attach`, which appends to the active group** — plan 4 adds placements;
   plan 6 restores saved ids through `add`.
6. **A strip drives one group: `WorkspaceChrome.tabs(GroupView)` makes one per group; it lists, selects, closes,
   reorders and opens tabs in that group, and shows as active while its window is active and its group is the active
   group** — spec 10.
7. **Strips always show (spec 1, decision 4): the equal tabs lose their one-tab hiding and the "reveal" played when the
   strip appeared; their open and close motion stays. `DesignMetrics.Role.TAB_REVEAL_INSET`, read only by the reveal,
   goes from jasper-ui with its two frozen catalog rows (Task 3)** — the reveal animated a strip appearing, which no
   longer happens, and the role would be left without a reader (fact 17).
8. **A terminal tab is active (`setActive`) while it is its window's active tab (the active group's selected tab) and
   the window is active, so `terminal.dim_inactive_panes` dims every terminal tab outside the active group; a hosted
   tab's `active(boolean)` follows the active tab alone** — spec 10; today's two rules with groups for the deck.
9. **A divider drag moves the boundary between its two neighbours, each keeping at least a tenth of the split (two
   children: today's 0.1-0.9), lays out continuously and writes the weights to the layout on release; positions follow
   the weights alone, so a theme change cannot move a divider** — replaces `TerminalTab`'s theme-update divider
   restoring; plan 2 Rulings 4 leaves the minimum to the view.
10. **The view rebuilds its splits only when the shape changes (orientation, children, group ids, the maximize); otherwise
    it pushes weights and moves tab components between group views. The active tab takes focus when it or the shape
    changed, except when focus going into it made the change (Rulings 26)** — a reparented terminal keeps its session
    (spec 2.3) but loses focus.
11. **Split Right and Split Down are enabled whenever the window can open a tab, as New Tab is. Beside a running
    terminal the new terminal tab starts in its inheritable directory with its profile; beside a plugin's view or a
    terminal whose shell ended it starts where a new tab from here starts (`directory()`) with the default profile; on
    an empty workspace it opens in the empty group** — VS Code splits whatever the editor holds (follow the industry
    standard); spec 11 names the active terminal's directory and profile; plan 2's `split` refuses the empty workspace.
    The SDK's `Terminals.split` of a pane still needs that pane's shell running (Rulings 19).
12. **New Tab, Next Tab, Previous Tab and Select Tab 1-9 act in the active group; reordering keeps the selection** —
    spec 11; today's deck behaviour within one group. `WorkspaceView.reorder` is plan 2's `move` followed by selecting
    the tab the group showed, so a dragged tab that was not selected becomes second in the group's order of use and can
    be the tab selected after the next close; acceptable, since plan 2 owns that order and the user just touched it.
13. **The tab menu reads: Rename Tab (terminal tabs), Open in Separate Window or Move to Main Window (hosted tabs, until
    plan 4's Move to New Window), Close Tab, Close Others, Close Group, then the content's commands** — spec 11's order
    with today's separate-window item where plan 4's will stand.
14. **Close Others and Close Group ask the hosted tabs' guards as one batch; a refusal closes nothing; then every tab of
    the batch closes, terminal tabs included** — one decision for one gesture, as the batched approval does today.
15. **The Pane menu keeps its slot until plan 3b folds `StandardMenu.PANE` into Tab: Split Right, Split Down, Focus Group
    Left/Right/Up/Down, Previous and Next Prompt; File lists New Tab, New Window, Open File…, Close Tab, Close Group;
    View's item and the toolbar's maximize button are Maximize Group; the terminal context menu ends with Maximize Group
    and Close Tab** — plugins contribute to the Pane slot today.
16. **Maximize Group is enabled while the workspace has two or more groups; Focus Group needs a group that way. Their
    palette titles read Maximize Group and Restore Group** — plan 2 returns a one-group layout unchanged.
17. **A tab moved between windows through the registry keeps its content and session; a terminal tab's facts are
    `TabClosed` in the old window and `TabOpened` in the new (plan 3b adds `TAB_MOVED`); its panes are not re-announced.
    The registry marks the tab `moving()` while it detaches and attaches it, and each window then ends its maximize
    (spec 15: "A maximized group loses a tab or gains one by a move: Maximize clears; closing one of its tabs keeps it")**
    — nothing in 3a's UI moves terminal tabs (plans 4 and 5 do); the registry and its test need the operation.
18. **The activity's "go to" (a notification card's click and the terminal registry's focus run it) selects the tab
    through the registry when it runs (`Tab.select()`), so it follows a moved tab to its window. An operating-system
    notification's click first raises the window the command finished in, which `JasperApplication` captured when it
    composed that window (fact 15); resolving that window at click time is plan 4's (a hand-off), as nothing in 3a's UI
    moves a terminal tab between windows** — spec 9.3; `TabMoveTest.goToFollowsAMovedTab` proves the activity's path.
19. **The terminal registry keeps `TabEntry`'s pane list (the tab's one pane) and lists terminal tabs only;
    `PaneEntry.split` takes the layout's `Side` and opens a terminal tab in a new group beside the pane's group;
    `SplitAxis` goes** — spec 9.4's app side; plan 3b changes `TabEntry` with the SDK.
20. **`TabState` stays the terminal tab's naming state, owned by `TerminalContent`, which exposes `renamed()` for plan 6**
    — spec 9.2 moves it "to the tab"; the content is the tab's terminal half.
21. **The move test runs `IdleTerminalSession`** — `FakeConnector` is not reachable from jasper-app (fact 9).
22. **The benchmark alternates Split Right and Split Down on the active group and reads every terminal tab of its window**
    — it measured panes; they are tabs now.
23. **A group view reports its strip's and its card area's rectangles in any component's coordinates** — plan 5 resolves
    drops over the views' pixel bounds.
24. **New files take ledger rows `nonvisual-helper,none,none` with "New in workspace-tabs plan 3a Task N"; a renamed
    class keeps its row under the new path; a deleted file's row goes and its citations move to its successor** — the
    convention of plans 1 and 2 and of Enterprise 6b.
25. **The keymap page's Panes category becomes Groups (Split Right, Split Down, Close Group, Maximize Group and Focus
    Group); Close Tab stays in Tabs. The toolbar's maximize button reads "Maximize group", and Maximize Group's palette
    keywords are "zoom" and "restore"; the split commands gain the keyword "group"** — the spec names the commands, not
    their category or toolbar label; the keywords keep the old names findable.
26. **Focus going into a tab makes it the window's active tab and its group the active group, without moving focus
    again: a pane's `onFocused` (set by `TabHost`) and a window-level `KeyboardFocusManager` "permanentFocusOwner"
    listener (any tab, plugin views included; added when the window is built, removed when it closes) call
    `WindowContent.tabFocused`, which selects through `WorkspaceView.focused` only when the tab is not already active**
    — today `TerminalTab` does this for its panes (fact 14); without it Cmd+W, Cmd+D, content shortcuts, dimming, the
    active strip and the palette's origin would follow the old group.
27. **The equal tabs move only for a tab that opens or closes: a tab entering a strip animates unless it came by a move
    (`WorkspaceView.moved`: moved to a group, to a new group, or in from another window), and one leaving animates only
    when it closed; reorders settle at once as before** — spec 4 and the no-animations rule; fact 16.
28. **A terminal tab whose attach fails after the registry added it is closed again, so neither the tab nor its pane
    outlives the failure, which propagates** — `TabRegistry.open` does the same for a plugin's tab.
29. **`WorkspaceView`'s layout is `model()`, its moves `moveTab` and `resizeSplit`, and `GroupView` takes its tabs with
    `display`** — AWT's `Container.layout()`, `Component.show()`, `move(int, int)` and `resize(int, int)` (fact 13).
30. **What the rewrite leaves without a caller goes: `WindowContent.selectedComponent()`, `reorderTab(int, int)` (a strip
    reorders through its `GroupView`) and Task 4's `tabHost()` accessor; `TAB_REVEAL_INSET` (Rulings 7)** — test-only
    accessors that tests use stay (`tabRegistry()`, `terminalOf`), as do `moveTab`, `stripBounds` and `contentBounds`
    for plan 5.

## File structure

- `dev.jasper.app.contributions`: `TabContent`, `HostedContent`, `WorkspaceHost`, `Tab`, `TabRegistry` (Task 1;
  `TabRegistry.add` and `adopt` in Task 3, `move(Tab, UUID)` in Task 4), `Contributions.tabs()`; tests `TabRegistryTest`.
- `dev.jasper.app.workspace`: `SplitContainer` (Task 2); `GroupView`, `WorkspaceView`, `TabStrip`, `WindowTabs`,
  `ClassicTabs`, `WorkspaceChrome`, `WindowContent`, `WorkspaceActions`, `WindowCommandPalette` (Task 3); `TabHost`
  (Task 4); `TerminalContent`, `WindowTerminals`, `WorkspaceConfiguration`, `WindowCommands`, `TerminalPane` (Task 5);
  `WindowChrome`, `ClassicHeader` (Task 6); tests `SplitContainerTest`, `WorkspaceViewTest`, `TabMoveTest`,
  `WorkspaceTabsTest`, `TabMenuTest` and the existing workspace tests.
- `dev.jasper.app.terminals`: `PaneEntry`, `SplitAxis` deleted (Task 5). `dev.jasper.app.plugins.HostedTerminals`
  (Task 5). `dev.jasper.app.commands.ActionId`, `shortcuthelp.ShortcutCatalog` (Task 6). `benchmark.BenchmarkRun`
  (Task 5). `application.JasperApplication` (Task 1).
- `jasper-ui`: `DesignMetrics.Role.TAB_REVEAL_INSET` and its `classic-roles.tsv` and `compact-roles.tsv` rows (Task 3).
- Deleted: `ContentRegistry`, `ContentEntry`, `ContentHost` (Task 1), `TerminalDeck` (Task 3), `TerminalTab`, `SplitTree`,
  `SplitAxis`, `SplitTreeTest` (Task 5).
- Documents (Task 7): `README.md`, `docs/configuration.md`, `docs/command-palette.md`, `docs/app-architecture.md`,
  `docs/app-maintenance.md`, `docs/sdk-architecture.md`, `docs/benchmarks.md`, `docs/remote.md`, `docs/packaging.md`,
  `docs/STATUS.md`; the ledger in each task. `docs/keyboard-shortcuts.md` describes the keymap page, not the commands,
  and needs no change.

## Before you start

```bash
git branch --show-current                                                     # claude/workspace-tabs
git status --short                                                            # nothing
ls jasper-app/src/main/java/dev/jasper/app/layout/Layout.java                 # plan 2
grep -c 'public Layout resize(List<Integer> path, List<Double> weights)' jasper-app/src/main/java/dev/jasper/app/layout/Layout.java   # 1
grep -c 'public Layout split(TabId tab, GroupId group, Side side)' jasper-app/src/main/java/dev/jasper/app/layout/Layout.java        # 1
grep -c 'public Optional<GroupId> neighbour(GroupId group, Side direction)' jasper-app/src/main/java/dev/jasper/app/layout/Layout.java  # 1
grep -c 'public Layout unmaximize()' jasper-app/src/main/java/dev/jasper/app/layout/Layout.java                                     # 1
grep -c 'public record Group(GroupId id, List<TabId> tabs, Optional<TabId> selected)' jasper-app/src/main/java/dev/jasper/app/layout/Group.java  # 1
grep -c 'contributions.content().setFactory(separate -> {' jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java  # 2
grep -c 'var requested = contributions.content().entries();' jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java  # 1
grep -c 'private final JTabbedPane tabs = new TerminalDeck();' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java     # 1
```

Every command prints the stated result. Every `Layout` signature this plan calls is plan 2's as executed (checked at
`93aa015fd`). Plan 2's `docs/STATUS.md` entry ("Workspace tabs, plan 2: the layout model and its encoding") is the top
one, above plan 1's; this plan's goes above it. Create the two helper scripts (Global Constraints).

---

### Task 1: The tab registry

Spec 9.1: one registry of tabs with an id, a kind, a content and the window that holds them, folding in
`ContentRegistry` with its guards (per-plugin guards, batched approval, the synchronous `mayClose`). In this task only
hosted tabs are registered; Task 3 registers terminal tabs.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/TabContent.java`, `HostedContent.java`,
  `WorkspaceHost.java`, `Tab.java`, `TabRegistry.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/contributions/ContentRegistry.java`, `ContentEntry.java`,
  `ContentHost.java`
- Modify: `contributions/Contributions.java:28-29`, `contributions/package-info.java`, and by script
  `application/JasperApplication.java`, `plugins/HostedUi.java`, `workspace/WindowContent.java`,
  `workspace/WindowStatusBar.java` and 21 test files (Step 4)
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java` (renamed from `ContentRegistryTest`)
- Ledger: `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: plan 2's `TabId` (`random()`, `value()`), `GroupId`.
- Produces: `public interface TabContent { JComponent component(); void dispose(); }`;
  `public record HostedContent(JComponent component) implements TabContent`;
  `public record WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab>
  select, Runnable toFront, Function<TabId, Optional<GroupId>> groupOf)` with a six-argument constructor (no groups);
  `public final class Tab` with `enum Kind { TERMINAL, HOSTED }`, `TabId id()`, `Kind kind()`, `String plugin()`,
  `String key()`, `TabContent content()`, `JComponent component()`, `title()`, `detail()`, `icon()`, `location()`,
  `host()`, `dirty()`, `closed()`, `active()`, `boolean moving()` (true while the registry moves it between
  windows), `UUID window()`, `Optional<GroupId> group()`,
  `List<ContentCommand> commands()`, `List<ContentStatus> status()`, `presentation(String, String, Icon, boolean, String,
  String)`, `commands(List)`, `status(List)`, `focusTarget(Runnable)`, `focus()`, `pin()`, `pinned()`, `detached()`,
  `select()`, `move(boolean separate)`, `CompletableFuture<Boolean> requestClose()`, `close()`, `onChanged(Runnable)`,
  `onClosed(Runnable)`, `onActive(Consumer<Boolean>)`, `active(boolean)`; `public final class TabRegistry` with
  `setFactory(Function<Boolean, UUID>)`, `Subscription addHost(WorkspaceHost)`, `activated(UUID)`, `detached(UUID)`,
  `List<Tab> tabs()`, `List<Tab> hosted()`, `List<Tab> inWindow(UUID)`, `Optional<Tab> tab(TabId)`,
  `Optional<GroupId> groupOf(Tab)`, `Subscription guard(String plugin, BiFunction<List<Tab>, Boolean,
  CompletableFuture<Boolean>>)`, `Tab open(String plugin, String key, String title, UUID owner, JComponent component)`,
  `select(Tab)`, `move(Tab, boolean separate)`, `remove(Tab)`, `approve(List<Tab>)`, `requestClose(List<Tab>)`,
  `boolean mayClose(List<Tab>)`, `boolean mayClose(Supplier<List<Tab>>)`; `Contributions.tabs()`.

- [ ] **Step 1: Write the failing test**

```bash
git mv jasper-app/src/test/java/dev/jasper/app/contributions/ContentRegistryTest.java \
       jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java
```

Replace the whole of `TabRegistryTest.java` with (the 11 ported cases keep their names and assertions; the last three are
new):

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class TabRegistryTest {
    private final TabRegistry registry = new TabRegistry();
    private final List<Tab> mounted = new ArrayList<>();
    private final UUID main = UUID.randomUUID(), detached = UUID.randomUUID();
    private void hosts() {
        registry.addHost(new WorkspaceHost(main, false, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, true, mounted::add, mounted::remove, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? detached : main);
    }
    @Test void failedOpenLeavesNoEntryAndSameKeyCanMountAgain() {
        var fail = new boolean[] {true};
        registry.addHost(new WorkspaceHost(main, false, tab -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("mount failed"); }
            mounted.add(tab);
        }, mounted::remove, tab -> {}, () -> {}));
        assertThatThrownBy(() -> registry.open("plugin.file", "one", "One", main, new JPanel()))
            .isInstanceOf(IllegalStateException.class).hasMessage("mount failed");
        assertThat(registry.hosted()).isEmpty();
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(registry.hosted()).containsExactly(tab);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationMountRestoresSourceAndKeepsDirtyDocument() {
        hosts();
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, t -> { throw new IllegalStateException("destination failed"); },
            t -> {}, t -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(tab.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersLeavesRecoverableUnmountedEntry() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, false, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : main);
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        // Model the source window unregistering synchronously when its final tab leaves.
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isNull();
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(registry.hosted()).containsExactly(tab);
        var replacement = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(replacement, false, mounted::add, mounted::remove, t -> {}, () -> {}));
        assertThat(registry.open("plugin.file", "one", "One", replacement, new JPanel())).isSameAs(tab);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(tab.window()).isEqualTo(replacement);
        assertThat(mounted).containsExactly(tab);
    }

    @Test void failedDestinationAfterSourceUnregistersUsesAnotherMainHost() {
        var source = new dev.jasper.app.lifecycle.Subscription[1];
        source[0] = registry.addHost(new WorkspaceHost(main, false, mounted::add, tab -> {
            mounted.remove(tab);
            source[0].close();
        }, tab -> {}, () -> {}));
        var fallback = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(fallback, false, mounted::add, mounted::remove, t -> {}, () -> {}));
        var bad = UUID.randomUUID();
        registry.addHost(new WorkspaceHost(bad, true, tab -> { throw new IllegalStateException("destination failed"); },
            tab -> {}, tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? bad : fallback);
        var panel = new JTextArea("unsaved");
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        tab.presentation("One", "", null, true, "", "");
        assertThatThrownBy(() -> registry.move(tab, true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("destination failed");
        assertThat(tab.window()).isEqualTo(fallback);
        assertThat(tab.closed()).isFalse();
        assertThat(tab.dirty()).isTrue();
        assertThat(tab.component()).isSameAs(panel);
        assertThat(mounted).containsExactly(tab);
    }
    @Test void openingAndMovingRetainIdentityAndNeverDisposeContent() {
        hosts();
        var panel = new JPanel();
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        int[] disposed = {0}; tab.onClosed(() -> disposed[0]++);
        assertThat(registry.open("plugin.file", "one", "Other", detached, new JPanel())).isSameAs(tab);
        registry.move(tab, true);
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.component()).isSameAs(panel);
        assertThat(disposed[0]).isZero();
        registry.move(tab, false);
        assertThat(tab.window()).isEqualTo(main);
        tab.close(); tab.close();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.hosted()).isEmpty();
    }
    @Test void pendingBatchCloseIsSharedAndCancellationRemovesNothing() {
        hosts();
        var a=registry.open("plugin.file", "a", "A", main, new JPanel());
        var b=registry.open("plugin.file", "b", "B", main, new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs,batch) -> { assertThat(tabs).containsExactly(a,b); return answer; });
        var first=registry.requestClose(List.of(a,b));
        assertThat(registry.requestClose(List.of(a,b))).isSameAs(first);
        registry.move(a,true);
        assertThat(a.window()).isEqualTo(main);
        answer.complete(false);
        assertThat(first.join()).isFalse();
        assertThat(mounted).containsExactly(a,b);
        registry.guard("plugin.file", (tabs,batch) -> CompletableFuture.completedFuture(true));
        assertThat(registry.requestClose(List.of(a,b)).join()).isTrue();
        assertThat(mounted).isEmpty();
    }
    @Test void lateDecisionAfterDisposalCannotReattachOrDoubleClose() {
        hosts(); var tab=registry.open("plugin.file","a","A", main,new JPanel());
        var answer=new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs,batch) -> answer);
        int[] disposed={0}; tab.onClosed(() -> disposed[0]++);
        var closing=registry.requestClose(List.of(tab));
        tab.close(); answer.complete(true); registry.move(tab,true); tab.select();
        assertThat(closing.join()).isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(mounted).isEmpty();
    }
    @Test void modalOwnerCannotMoveOrCloseUntilReleased() {
        hosts();
        var tab = registry.open("plugin.file", "a", "A", main, new JPanel());
        var pin = tab.pin();
        tab.move(true);
        assertThat(tab.window()).isEqualTo(main);
        assertThat(tab.requestClose().join()).isFalse();
        pin.close();
        tab.move(true);
        assertThat(tab.window()).isEqualTo(detached);
        assertThat(tab.requestClose().join()).isTrue();
    }
    @Test void reentrantWindowCloseDoesNotWaitOnItsOwnPendingGuard() {
        hosts();
        var tab = registry.open("plugin.file", "a", "A", main, new JPanel());
        var answer = new CompletableFuture<Boolean>();
        registry.guard("plugin.file", (tabs, batch) -> answer);
        registry.approve(List.of(tab));
        var timer = new javax.swing.Timer(30, event -> answer.complete(true));
        timer.setRepeats(false); timer.start();
        try {
            assertThat(registry.mayClose(List.of(tab))).as("nested close is refused immediately").isFalse();
            assertThat(answer).isNotDone();
        } finally { timer.stop(); answer.complete(false); }
    }
    @Test void windowCloseRefusesNewDocumentsOpenedDuringApproval() {
        hosts();
        registry.open("plugin.file", "a", "A", main, new JPanel());
        registry.guard("plugin.file", (tabs, batch) -> {
            registry.open("plugin.file", "b", "B", main, new JPanel());
            return CompletableFuture.completedFuture(true);
        });
        assertThat(registry.mayClose(() -> registry.inWindow(main))).isFalse();
        assertThat(registry.hosted()).hasSize(2);
    }

    @Test void aHostedTabHasAnIdItsKindAndItsContent() {
        hosts();
        var panel = new JPanel();
        var tab = registry.open("plugin.file", "one", "One", main, panel);
        assertThat(tab.kind()).isEqualTo(Tab.Kind.HOSTED);
        assertThat(tab.content()).isEqualTo(new HostedContent(panel));
        assertThat(tab.component()).isSameAs(panel);
        assertThat(registry.tab(tab.id())).contains(tab);
        assertThat(registry.tabs()).containsExactly(tab);
        assertThat(registry.inWindow(main)).containsExactly(tab);
        var other = registry.open("plugin.file", "two", "Two", main, new JPanel());
        assertThat(other.id()).isNotEqualTo(tab.id());
        tab.close();
        assertThat(registry.tab(tab.id())).isEmpty();
        assertThat(registry.tabs()).containsExactly(other);
    }

    @Test void aTabSaysItIsMovingWhileItsWindowsDetachAndAttachIt() {
        var seen = new ArrayList<String>();
        registry.addHost(new WorkspaceHost(main, false, tab -> seen.add("attach main " + tab.moving()),
            tab -> seen.add("detach main " + tab.moving()), tab -> {}, () -> {}));
        registry.addHost(new WorkspaceHost(detached, true, tab -> seen.add("attach detached " + tab.moving()),
            tab -> seen.add("detach detached " + tab.moving()), tab -> {}, () -> {}));
        registry.setFactory(separate -> separate ? detached : main);
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        registry.move(tab, true);
        assertThat(tab.moving()).as("only during the move").isFalse();
        tab.close();
        assertThat(seen).containsExactly("attach main false", "detach main true", "attach detached true", "detach detached false");
    }

    @Test void theHostSaysWhichGroupHoldsATab() {
        var group = GroupId.random();
        registry.addHost(new WorkspaceHost(main, false, mounted::add, mounted::remove, tab -> {}, () -> {},
            id -> Optional.of(group)));
        var tab = registry.open("plugin.file", "one", "One", main, new JPanel());
        assertThat(tab.group()).contains(group);
        assertThat(registry.groupOf(tab)).contains(group);
        tab.close();
        assertThat(tab.group()).as("a closed tab is in no group").isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `TabRegistry`, `Tab`, `WorkspaceHost`, `HostedContent`.

- [ ] **Step 3: Create the registry's types**

Create `jasper-app/src/main/java/dev/jasper/app/contributions/TabContent.java`:

```java
package dev.jasper.app.contributions;

import javax.swing.JComponent;

/**
 * What one tab shows (workspace tabs spec 9.2): {@link HostedContent}, a plugin's view, or the workspace's terminal
 * content, one terminal pane. The registry holds it for the tab's whole life and never looks inside; a window mounts
 * {@link #component()} and moves it to another window without replacing it. EDT only.
 */
public interface TabContent {
    /**
     * The component a window shows for the tab, the same object for the tab's whole life.
     *
     * @return the component
     */
    JComponent component();

    /** Releases what the content holds once its tab has closed, as a terminal closes its session. Called once. */
    void dispose();
}
```

Create `HostedContent.java`:

```java
package dev.jasper.app.contributions;

import java.util.Objects;
import javax.swing.JComponent;

/**
 * A plugin's view as a tab's content (workspace tabs spec 9.2). The plugin releases its view through the tab's
 * {@link Tab#onClosed(Runnable)}, so disposing it does nothing more.
 *
 * @param component the plugin's mounted view
 */
public record HostedContent(JComponent component) implements TabContent {
    /** Requires the component. */
    public HostedContent { Objects.requireNonNull(component, "component"); }

    @Override public void dispose() { }
}
```

Create `WorkspaceHost.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). A {@code detached} workspace is a
 * separate window for plugin views (workspace plan 4 replaces it). EDT only.
 *
 * @param id the window's id
 * @param detached whether it is a separate window for plugin views
 * @param attach shows a tab the registry placed in this window, at the end of its active group
 * @param detach takes a tab out of this window's layout, before the tab closes or moves to another window
 * @param select shows a tab of this window and activates its group
 * @param toFront raises the window
 * @param groupOf the group that holds a tab of this window
 */
public record WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf) {
    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
    }

    /**
     * A workspace that keeps no groups, as a fake window in a test does.
     *
     * @param id the window's id
     * @param detached whether it is a separate window for plugin views
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     */
    public WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                         Runnable toFront) {
        this(id, detached, attach, detach, select, toFront, tab -> Optional.empty());
    }
}
```

Create `Tab.java` (today's `ContentEntry` with an id, a kind and a content):

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * One tab of any window (workspace tabs spec 9.1): its id and kind, its content, its presentation (title, detail, icon,
 * unsaved marker, location, host, status items), its commands and focus target, and the window that holds it. Moving the
 * tab between windows never replaces this object or its content; only {@link TabRegistry#remove(Tab)} disposes it. EDT
 * only.
 */
public final class Tab implements AutoCloseable {
    /** What a tab holds. */
    public enum Kind {
        /** One terminal pane. */
        TERMINAL,
        /** A plugin's view. */
        HOSTED
    }

    /** The registry this tab lives in; a window's own until the window joins the application's. */
    TabRegistry registry;
    /** Set by the registry while it moves the tab from one window to another. */
    boolean moving;
    private final TabId id;
    private final Kind kind;
    private final String plugin, key;
    private final TabContent content;
    private final List<Runnable> changed = new ArrayList<>(), disposed = new ArrayList<>();
    private final List<Consumer<Boolean>> activation = new ArrayList<>();
    private String title, detail = "", location = "", host = "";
    private Icon icon;
    private boolean dirty, closed, active;
    private int pins;
    private UUID window;
    private Runnable focus;
    private List<ContentCommand> commands = List.of();
    private List<ContentStatus> status = List.of();

    Tab(TabRegistry registry, TabId id, Kind kind, String plugin, String key, String title, TabContent content) {
        this.registry = registry;
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        JComponent component = content.component();
        focus = component::requestFocusInWindow;
    }

    /** @return the tab's id, for the life of the tab */
    public TabId id() { return id; }
    /** @return what the tab holds */
    public Kind kind() { return kind; }
    /** @return the plugin that opened a hosted tab; empty for a terminal tab */
    public String plugin() { return plugin; }
    /** @return the plugin's key for a hosted tab; the id for a terminal tab */
    public String key() { return key; }
    /** @return the tab's content */
    public TabContent content() { return content; }
    /** @return the component the window shows, the content's */
    public JComponent component() { return content.component(); }
    public String title() { return title; }
    public String detail() { return detail; }
    public Icon icon() { return icon; }
    public String location() { return location; }
    public String host() { return host; }
    public boolean dirty() { return dirty; }
    public boolean closed() { return closed; }
    public boolean active() { return active; }
    /**
     * @return whether the registry is moving this tab between windows right now: a window that detaches or attaches it
     *     meanwhile sees a move, not a close or an open (workspace tabs spec 15)
     */
    public boolean moving() { return moving; }
    /** @return the id of the window holding the tab, or null while no window does */
    public UUID window() { return window; }
    public List<ContentCommand> commands() { return commands; }
    public List<ContentStatus> status() { return status; }

    /**
     * The group that holds the tab in its window's layout.
     *
     * @return the group, or empty for a closed tab or a window that keeps no groups
     */
    public Optional<GroupId> group() { return closed ? Optional.empty() : registry.groupOf(this); }

    /** A modal owner keeps the tab where it is and open until the returned subscription closes. */
    public Subscription pin() { pins++; return new Subscription(() -> pins--); }
    public boolean pinned() { return pins > 0; }
    /** @return whether a separate window for plugin views holds the tab */
    public boolean detached() { return registry.detached(window); }

    /** Sets the presentation and tells the listeners; ignored once closed. */
    public void presentation(String title, String detail, Icon icon, boolean dirty, String location, String host) {
        if (closed) return;
        this.title = Objects.requireNonNull(title); this.detail = Objects.requireNonNull(detail); this.icon = icon;
        this.dirty = dirty; this.location = Objects.requireNonNull(location); this.host = Objects.requireNonNull(host);
        fire();
    }
    public void commands(List<ContentCommand> value) { if (!closed) { commands = List.copyOf(value); fire(); } }
    public void status(List<ContentStatus> value) { if (!closed) { status = List.copyOf(value); fire(); } }
    /** What {@link #focus()} runs; the component's own focus request by default. */
    public void focusTarget(Runnable value) { if (!closed) focus = Objects.requireNonNull(value); }
    public void focus() { if (!closed) focus.run(); }
    /** Shows the tab in its window and raises the window, wherever the tab is now. */
    public void select() { registry.select(this); }
    /** Moves a hosted tab to a separate window or back to the main one (workspace plan 4 replaces this). */
    public void move(boolean separate) { registry.move(this, separate); }
    /** Closes the tab once its plugin's guard allows it. */
    public CompletableFuture<Boolean> requestClose() { return registry.requestClose(List.of(this)); }

    public Subscription onChanged(Runnable listener) {
        if (closed) return new Subscription(() -> {});
        changed.add(listener);
        return new Subscription(() -> changed.remove(listener));
    }
    public Subscription onClosed(Runnable listener) {
        if (closed) return new Subscription(() -> {});
        disposed.add(listener);
        return new Subscription(() -> disposed.remove(listener));
    }
    public Subscription onActive(Consumer<Boolean> listener) {
        if (closed) return new Subscription(() -> {});
        activation.add(listener);
        return new Subscription(() -> activation.remove(listener));
    }
    /** Whether the tab is its window's active tab; the window sets it. */
    public void active(boolean value) {
        if (closed || active == value) return;
        active = value;
        for (var listener : List.copyOf(activation)) listener.accept(value);
    }

    void window(UUID value) { window = value; fire(); }

    private void fire() { for (var listener : List.copyOf(changed)) listener.run(); }

    /** Marks the tab closed, tells its close listeners, then releases its content. */
    void dispose() {
        if (closed) return;
        active(false);
        closed = true;
        for (var listener : List.copyOf(disposed)) listener.run();
        changed.clear(); disposed.clear(); activation.clear();
        content.dispose();
    }

    /** Removes the tab from its registry at once, without asking a guard. */
    @Override public void close() { registry.remove(this); }

    @Override public String toString() { return "Tab[" + kind + " " + id + " " + title + "]"; }
}
```

Create `TabRegistry.java` (today's `ContentRegistry` over every tab):

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Every tab of every window (workspace tabs spec 9.1). The application owns one; a window not yet connected to the
 * contributions keeps one of its own. Tabs outlive the windows that show them: moving a tab detaches it from one
 * {@link WorkspaceHost} and attaches it to another, and only {@link #remove(Tab)} disposes it. Close guards are per
 * plugin: a batch is approved by each plugin's guard in turn, and a tab without a guard, such as a terminal tab, needs no
 * approval. EDT only.
 */
public final class TabRegistry {
    private final Map<UUID, WorkspaceHost> hosts = new LinkedHashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>>> guards = new HashMap<>();
    private Function<Boolean, UUID> factory = separate -> { throw new IllegalStateException("No workspace host available"); };
    private UUID lastMain;
    private List<Tab> pending = List.of();
    private CompletableFuture<Boolean> decision;
    private CompletableFuture<Boolean> closing;

    /** How a window for plugin views is made when none is open; the argument asks for a separate one. */
    public void setFactory(Function<Boolean, UUID> factory) { this.factory = Objects.requireNonNull(factory); }

    /**
     * Registers a window's workspace. Closing the subscription removes, so disposes, the tabs that window still holds,
     * then forgets it.
     */
    public Subscription addHost(WorkspaceHost host) {
        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
        if (!host.detached()) lastMain = host.id();
        return new Subscription(() -> {
            for (var tab : List.copyOf(tabs)) if (host.id().equals(tab.window())) remove(tab);
            hosts.remove(host.id());
        });
    }

    /** The user turned to this window; the last such main window takes plugin views opened without an owner. */
    public void activated(UUID id) {
        if (hosts.containsKey(id) && !hosts.get(id).detached()) lastMain = id;
    }
    public boolean detached(UUID id) { var host = hosts.get(id); return host != null && host.detached(); }
    /** @return every tab, in the order the tabs were registered */
    public List<Tab> tabs() { return List.copyOf(tabs); }
    /** @return the plugin tabs, in the order they opened */
    public List<Tab> hosted() { return tabs.stream().filter(tab -> tab.kind() == Tab.Kind.HOSTED).toList(); }
    /** @return every tab {@code window} holds */
    public List<Tab> inWindow(UUID window) { return tabs.stream().filter(tab -> window.equals(tab.window())).toList(); }
    /** @return the tab with this id, while it is open */
    public Optional<Tab> tab(TabId id) {
        for (var tab : tabs) if (tab.id().equals(id)) return Optional.of(tab);
        return Optional.empty();
    }
    /** @return the group that holds the tab in its window, as that window says */
    public Optional<GroupId> groupOf(Tab tab) {
        var host = tab.window() == null ? null : hosts.get(tab.window());
        return host == null ? Optional.empty() : host.groupOf().apply(tab.id());
    }

    /** Registers a plugin's close guard, which approves a list of its tabs; {@code true} marks a batch (a window or quit). */
    public Subscription guard(String plugin, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>> callback) {
        guards.put(plugin, callback);
        return new Subscription(() -> guards.remove(plugin, callback));
    }

    /**
     * Opens a plugin's view as a tab in {@code owner}'s window, or the main window when {@code owner} is null or not
     * registered, and selects it. A (plugin, key) already open is selected again, and attached to a window again when it
     * lost its own.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component) {
        requireEdt();
        for (var tab : tabs) if (tab.kind() == Tab.Kind.HOSTED && tab.plugin().equals(plugin) && tab.key().equals(key)) {
            if (tab.window() == null) {
                WorkspaceHost retry = owner != null ? hosts.get(owner) : null;
                if (retry == null) retry = main();
                tab.window(retry.id());
                try { retry.attach().accept(tab); }
                catch (RuntimeException | Error failure) {
                    rollbackAttach(retry, tab, failure);
                    tab.window(null);
                    throw failure;
                }
            }
            select(tab);
            return tab;
        }
        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        WorkspaceHost host = owner != null ? hosts.get(owner) : null;
        if (host == null) host = main();
        tabs.add(tab);
        tab.window(host.id());
        try { host.attach().accept(tab); }
        catch (RuntimeException | Error failure) {
            rollbackAttach(host, tab, failure);
            tabs.remove(tab);
            try { tab.dispose(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        select(tab);
        return tab;
    }

    private static void rollbackAttach(WorkspaceHost host, Tab tab, Throwable failure) {
        try { host.detach().accept(tab); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
    }

    private WorkspaceHost main() {
        var host = hosts.get(lastMain);
        if (host == null) host = hosts.values().stream().filter(h -> !h.detached()).findFirst().orElse(null);
        if (host == null) host = hosts.get(factory.apply(false));
        return Objects.requireNonNull(host, "Factory did not register a workspace");
    }

    /** Shows the tab in its window and raises the window. */
    public void select(Tab tab) {
        if (tab.closed()) return;
        var host = hosts.get(tab.window());
        if (host != null) { host.select().accept(tab); host.toFront().run(); }
    }

    /** Moves a tab to a separate window or back to the main one; a pinned tab or one being closed stays. */
    public void move(Tab tab, boolean separate) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || detached(tab.window()) == separate) return;
        var old = hosts.get(tab.window());
        var next = separate ? hosts.get(factory.apply(true)) : main();
        if (old == null || next == null || old == next) return;
        transfer(tab, old, next);
    }

    /**
     * Detaches the tab from {@code old} and attaches it to {@code next}, then selects it. When {@code next} fails, the tab
     * goes back to {@code old}, or another main window, or stays in no window, and the failure propagates.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next) {
        // Change ownership before removal: an emptied old window may unregister itself synchronously.
        tab.active(false);
        tab.window(next.id());
        tab.moving = true;
        try {
            old.detach().accept(tab);
            try { next.attach().accept(tab); }
            catch (RuntimeException | Error failure) {
                rollbackAttach(next, tab, failure);
                if (tabs.contains(tab) && !tab.closed()) {
                    var recovery = new ArrayList<WorkspaceHost>();
                    if (hosts.get(old.id()) == old) recovery.add(old);
                    for (var host : hosts.values())
                        if (host != old && host != next && !host.detached()) recovery.add(host);
                    boolean restored = false;
                    for (var host : recovery) {
                        tab.window(host.id());
                        try { host.attach().accept(tab); restored = true; break; }
                        catch (RuntimeException | Error recoveryFailure) {
                            rollbackAttach(host, tab, recoveryFailure);
                            failure.addSuppressed(recoveryFailure);
                        }
                    }
                    if (!restored) tab.window(null);
                }
                throw failure;
            }
        } finally {
            tab.moving = false;
        }
        select(tab);
    }

    /** Disposes the tab at once, without asking a guard, and takes it out of its window. */
    public void remove(Tab tab) {
        requireEdt();
        if (!tabs.remove(tab)) return;
        var host = hosts.get(tab.window());
        tab.dispose();
        if (host != null) host.detach().accept(tab);
    }

    public CompletableFuture<Boolean> approve(List<Tab> requested) { return approve(requested, true); }

    private CompletableFuture<Boolean> approve(List<Tab> requested, boolean batch) {
        requireEdt();
        var live = requested.stream().filter(tab -> !tab.closed()).distinct().toList();
        if (decision != null) return pending.equals(live) ? decision : CompletableFuture.completedFuture(false);
        if (live.isEmpty()) return CompletableFuture.completedFuture(true);
        if (live.stream().anyMatch(Tab::pinned)) return CompletableFuture.completedFuture(false);
        pending = live;
        var result = new CompletableFuture<Boolean>();
        decision = result;
        var groups = new LinkedHashMap<String, List<Tab>>();
        for (var tab : live) groups.computeIfAbsent(tab.plugin(), plugin -> new ArrayList<>()).add(tab);
        var iterator = groups.entrySet().iterator();
        class Next implements Runnable {
            @Override public void run() {
                if (!iterator.hasNext()) { finish(true); return; }
                var group = iterator.next();
                var guard = guards.get(group.getKey());
                if (guard == null) { run(); return; }
                try {
                    Objects.requireNonNull(guard.apply(List.copyOf(group.getValue()), batch))
                        .whenComplete((allowed, error) -> onEdt(() -> {
                            if (error != null || !Boolean.TRUE.equals(allowed)) finish(false);
                            else run();
                        }));
                } catch (RuntimeException failure) { finish(false); }
            }
            void finish(boolean allowed) {
                pending = List.of();
                decision = null;
                result.complete(allowed);
            }
        }
        new Next().run();
        return result;
    }

    /** Asks the guards about these tabs as one decision, then removes them all, or none. */
    public CompletableFuture<Boolean> requestClose(List<Tab> requested) {
        if (decision != null && closing == null) return CompletableFuture.completedFuture(false);
        if (closing != null) return pending.equals(requested) ? closing : CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        closing = result;
        approve(requested, false).whenComplete((allowed, error) -> onEdt(() -> {
            if (error == null && Boolean.TRUE.equals(allowed)) for (var tab : requested) remove(tab);
            closing = null;
            result.complete(error == null && Boolean.TRUE.equals(allowed));
        }));
        return result;
    }

    /** Existing synchronous native quit/restart guards pump EDT events while asynchronous saves complete. */
    public boolean mayClose(List<Tab> requested) {
        // A nested native close must not wait for the outer modal guard's own stack to unwind.
        if (decision != null) return false;
        return await(approve(requested));
    }

    /** As {@link #mayClose(List)}, refused also when the tabs changed while the guards decided. */
    public boolean mayClose(Supplier<List<Tab>> current) {
        var requested = List.copyOf(current.get());
        return mayClose(requested) && requested.equals(current.get());
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        if (future.isDone()) return future.getNow(false);
        var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(loop::exit));
        loop.enter();
        return future.getNow(false);
    }
    private static void onEdt(Runnable run) {
        if (SwingUtilities.isEventDispatchThread()) run.run(); else SwingUtilities.invokeLater(run);
    }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Tabs belong to the EDT");
    }
}
```

Delete the old types and point `Contributions` at the registry:

```bash
git rm -q jasper-app/src/main/java/dev/jasper/app/contributions/ContentRegistry.java \
          jasper-app/src/main/java/dev/jasper/app/contributions/ContentEntry.java \
          jasper-app/src/main/java/dev/jasper/app/contributions/ContentHost.java
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
C = "jasper-app/src/main/java/dev/jasper/app/contributions/"
edit(C + "Contributions.java", [(
    "    private final ContentRegistry content = new ContentRegistry();\n    public ContentRegistry content() { return content; }\n",
    "    private final TabRegistry tabs = new TabRegistry();\n"
    "    /** Every window's tabs (workspace tabs spec 9.1): the application's one registry. */\n"
    "    public TabRegistry tabs() { return tabs; }\n", 1)])
edit(C + "package-info.java", [
    (" * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections,\n"
     " * status entries, panels, palette scopes and file-chooser sources, in app-native types.",
     " * EDT model of what extensions contribute to the chrome: actions, toolbar entries, menu sections,\n"
     " * status entries, panels, palette scopes and file-chooser sources, in app-native types, and the tab registry that owns\n"
     " * every window's tabs and their content.", 1),
    ("dev.jasper.app.lifecycle, dev.jasper.app.palette.", "dev.jasper.app.layout, dev.jasper.app.lifecycle, dev.jasper.app.palette.", 1)])
PY
```

- [ ] **Step 4: Rename the registry's users**

Every user of the old names moves to the new ones: `contributions.content()` and `model.content()` become `.tabs()`,
its `entries()` becomes `hosted()`, and the three type names change. The counts are per file:
(`.content()` receivers, `contributions().content()`, `.entries()`, `ContentHost`, `ContentEntry`, `ContentRegistry`).

```bash
python3 -I - <<'PY'
import pathlib, re, sys
R = "jasper-app/src/"
M, T = R + "main/java/dev/jasper/app/", R + "test/java/dev/jasper/app/"
EXPECTED = {
    M + "application/JasperApplication.java": (5, 0, 2, 0, 0, 0),
    M + "plugins/HostedUi.java": (2, 0, 0, 0, 3, 0),
    M + "workspace/WindowContent.java": (1, 0, 0, 1, 5, 1),
    M + "workspace/WindowStatusBar.java": (0, 0, 0, 0, 1, 0),
    T + "application/JasperApplicationBackgroundTest.java": (0, 1, 0, 2, 0, 0),
    T + "plugins/AppContractTest.java": (5, 0, 1, 1, 0, 0),
    T + "plugins/EditorCompositionTest.java": (5, 0, 5, 0, 0, 0),
    T + "plugins/EditorDocumentPreservationTest.java": (3, 0, 3, 0, 0, 0),
    T + "plugins/HostedChooserTest.java": (2, 0, 0, 1, 0, 0),
    T + "plugins/KitMountingTest.java": (4, 0, 1, 2, 0, 0),
    T + "plugins/StagedEditorHost.java": (3, 0, 0, 2, 0, 0),
    T + "plugins/WorkspaceContentRouteTest.java": (5, 0, 0, 3, 0, 0),
    T + "workspace/AlacrittyTabsPreview.java": (2, 0, 0, 0, 0, 0),
    T + "workspace/FlatTabsPreview.java": (1, 0, 0, 0, 0, 0),
    T + "workspace/HostedContentPreview.java": (2, 0, 1, 0, 1, 0),
    T + "workspace/HostedContentTest.java": (7, 0, 1, 0, 0, 0),
    T + "workspace/PluginPaneAppearanceTest.java": (24, 0, 3, 0, 0, 0),
    T + "workspace/PresentationOwnershipTest.java": (2, 0, 0, 0, 0, 0),
    T + "workspace/RuledCellsTest.java": (0, 0, 0, 2, 0, 2),
    T + "workspace/ShortcutContextDispatchTest.java": (1, 0, 0, 0, 0, 0),
    T + "workspace/TabShortcutsTest.java": (1, 0, 0, 0, 0, 0),
    T + "workspace/WindowPanelsTest.java": (1, 0, 0, 0, 0, 0),
    T + "workspace/WindowTabMotionPreview.java": (3, 0, 0, 0, 0, 0),
    T + "workspace/WindowTitleTest.java": (1, 0, 0, 0, 0, 0),
    T + "workspace/WorkspaceGlassTest.java": (5, 0, 1, 0, 0, 0),
    T + "workspace/WorkspaceThemeTest.java": (6, 0, 0, 0, 0, 0),
}
STEPS = [(r"\b(contributions|model)\.content\(\)", r"\1.tabs()"), (r"\bcontributions\(\)\.content\(\)", "contributions().tabs()"),
         (r"\.tabs\(\)\.entries\(\)", ".tabs().hosted()"), (r"\bContentHost\b", "WorkspaceHost"),
         (r"\bContentEntry\b", "Tab"), (r"\bContentRegistry\b", "TabRegistry")]
for path, counts in EXPECTED.items():
    file = pathlib.Path(path); text = file.read_text(encoding="utf-8")
    for (pattern, replacement), count in zip(STEPS, counts):
        found = len(re.findall(pattern, text))
        if found != count: sys.exit(f"{path}: expected {count} of /{pattern}/, found {found}")
        text = re.sub(pattern, replacement, text)
    file.write_text(text, encoding="utf-8")
left = [str(p) for p in pathlib.Path(R).rglob("*.java")
        if re.search(r"\bContent(Host|Entry|Registry)\b|\b(contributions|model)\.content\(\)", p.read_text(encoding="utf-8"))]
if left: sys.exit("still naming the old registry: " + ", ".join(left))
PY
```

`WindowContent` keeps today's behaviour: its `WorkspaceHost` uses the six-argument constructor, `selectedContent()` returns
the hosted `Tab`; Task 3 rewires it. `JasperApplication.windowsMayClose` now reads
`contributions.tabs().hosted()` (Quit asks the plugin tabs, Rulings 4).

- [ ] **Step 5: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/", "jasper-app/src/test/java/dev/jasper/app/"
ledger.rename(T + "contributions/ContentRegistryTest.java", T + "contributions/TabRegistryTest.java", "TabRegistryTest")
ledger.rename(M + "contributions/ContentEntry.java", M + "contributions/Tab.java", "Tab")
ledger.rename(M + "contributions/ContentRegistry.java", M + "contributions/TabRegistry.java", "TabRegistry")
for name, what in [("TabContent", "what a tab shows"), ("HostedContent", "a plugin's view as a tab's content")]:
    ledger.add(M + "contributions/" + name + ".java", "app.contributions", name, T + "contributions/TabRegistryTest.java",
               "New in workspace-tabs plan 3a Task 1; " + what)
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.HostedContentTest' --tests 'dev.jasper.app.workspace.PluginPaneAppearanceTest' --tests 'dev.jasper.app.workspace.WorkspaceThemeTest' --tests 'dev.jasper.app.workspace.WorkspaceGlassTest' --tests 'dev.jasper.app.workspace.RuledCellsTest' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`TabRegistryTest` 14). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Tab registry: every tab with an id, a kind and a content

ContentRegistry, ContentEntry and ContentHost become TabRegistry, Tab and
WorkspaceHost, with TabContent and HostedContent; the guards, the batched
approval and the synchronous mayClose keep their behaviour.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The split container

Spec 10: one container for a split of any number of children with draggable dividers whose drags write the weights
back (Rulings 9). Unwired here; Task 3 builds the workspace from it.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/SplitContainer.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java` (the `layout` edge)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/SplitContainerTest.java`
- Ledger: two rows

**Interfaces:**
- Consumes: plan 2's `Orientation`.
- Produces: `final class SplitContainer extends JPanel` with `static final double MINIMUM_SHARE = 0.1`,
  `SplitContainer(Orientation orientation, List<Integer> path, List<Double> weights, List<? extends JComponent> children,
  BiConsumer<List<Integer>, List<Double>> resized)`, `Orientation orientation()`, `List<Integer> path()`,
  `List<Double> weights()`, `List<JComponent> children()`, `JComponent divider(int index)`,
  `void setWeights(List<Double>)`, `void setGlass(boolean)`, `static int dividerSize()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/SplitContainerTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.layout.Orientation;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10: a split of the workspace, its dividers and their drags (workspace plan 3a Rulings 9). */
class SplitContainerTest {
    private ThemeController themes;
    private final List<List<Integer>> paths = new ArrayList<>();
    private final List<List<Double>> reported = new ArrayList<>();

    @BeforeEach void theme() throws Exception { edt(() -> themes = ThemeTestSupport.controller(true)); }
    @AfterEach void restore() throws Exception { edt(() -> { themes.configure(true, TerminalSchemeChoice.MATCH); themes.close(); }); }

    /** A child whose minimum is {@code size} by {@code size + 10}. */
    private static JPanel child(int size) {
        var panel = new JPanel();
        panel.setMinimumSize(new Dimension(size, size + 10));
        panel.setPreferredSize(new Dimension(2 * size, 2 * size));
        return panel;
    }

    private SplitContainer split(Orientation orientation, List<Double> weights, JComponent... children) {
        return new SplitContainer(orientation, List.of(1, 0), weights, List.of(children), (path, values) -> {
            paths.add(path);
            reported.add(values);
        });
    }

    private static void mouse(JComponent component, int id, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test void childrenShareTheLengthByWeightWithADividerBetweenEachTwo() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10); var c = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.25, 0.25), a, b, c);
            int divider = SplitContainer.dividerSize();
            split.setSize(400 + 2 * divider, 300); split.doLayout();
            assertThat(a.getBounds()).isEqualTo(new Rectangle(0, 0, 200, 300));
            assertThat(split.divider(0).getBounds()).isEqualTo(new Rectangle(200, 0, divider, 300));
            assertThat(b.getBounds()).isEqualTo(new Rectangle(200 + divider, 0, 100, 300));
            assertThat(split.divider(1).getBounds()).isEqualTo(new Rectangle(300 + divider, 0, divider, 300));
            assertThat(c.getBounds()).isEqualTo(new Rectangle(300 + 2 * divider, 0, 100, 300));
            var stacked = split(Orientation.VERTICAL, List.of(0.75, 0.25), child(10), child(10));
            stacked.setSize(300, 200 + divider); stacked.doLayout();
            assertThat(stacked.children().get(0).getBounds()).isEqualTo(new Rectangle(0, 0, 300, 150));
            assertThat(stacked.children().get(1).getBounds()).isEqualTo(new Rectangle(0, 150 + divider, 300, 50));
            assertThat(stacked.weights()).containsExactly(0.75, 0.25);
            assertThat(stacked.path()).containsExactly(1, 0);
            assertThat(stacked.orientation()).isEqualTo(Orientation.VERTICAL);
            assertThat(stacked.divider(0).getCursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
        });
    }

    @Test void aDividerDragMovesItsBoundaryAndReportsTheWeightsOnRelease() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10); var c = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.25, 0.25), a, b, c);
            int divider = SplitContainer.dividerSize();
            split.setSize(400 + 2 * divider, 300); split.doLayout();
            var rule = split.divider(1);
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 - 40, 50);
            assertThat(b.getWidth()).as("laid out while dragging").isEqualTo(60);
            assertThat(c.getWidth()).isEqualTo(140);
            assertThat(a.getWidth()).as("the other children stay").isEqualTo(200);
            assertThat(reported).as("nothing is written before the release").isEmpty();
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2 - 40, 50);
            assertThat(paths).containsExactly(List.of(1, 0));
            assertThat(reported).hasSize(1);
            assertThat(reported.getFirst().get(0)).isEqualTo(0.5);
            assertThat(reported.getFirst().get(1)).isCloseTo(0.15, within(1e-9));
            assertThat(reported.getFirst().get(2)).isCloseTo(0.35, within(1e-9));
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2, 50);
            assertThat(reported).as("a click that moves nothing writes nothing").hasSize(1);
        });
    }

    @Test void aDragKeepsATenthOnEachSide() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), a, b);
            int divider = SplitContainer.dividerSize();
            split.setSize(1000 + divider, 300); split.doLayout();
            var rule = split.divider(0);
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 5);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 + 2000, 5);
            assertThat(split.weights().get(0)).isCloseTo(0.9, within(1e-9));
            assertThat(b.getWidth()).as("the far side keeps a tenth").isEqualTo(100);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 - 2000, 5);
            assertThat(split.weights().get(0)).isCloseTo(0.1, within(1e-9));
            assertThat(a.getWidth()).isEqualTo(100);
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2 - 2000, 5);
            assertThat(reported).singleElement().satisfies(values -> {
                assertThat(values.get(0)).isCloseTo(0.1, within(1e-9));
                assertThat(values.get(1)).isCloseTo(0.9, within(1e-9));
            });
            var narrow = split(Orientation.VERTICAL, List.of(0.05, 0.05, 0.9), child(1), child(1), child(1));
            narrow.setSize(100, 1000 + 2 * divider); narrow.doLayout();
            mouse(narrow.divider(0), MouseEvent.MOUSE_PRESSED, 5, 2);
            mouse(narrow.divider(0), MouseEvent.MOUSE_DRAGGED, 5, 300);
            mouse(narrow.divider(0), MouseEvent.MOUSE_RELEASED, 5, 300);
            assertThat(narrow.weights()).as("two neighbours under two tenths together cannot trade")
                .containsExactly(0.05, 0.05, 0.9);
            assertThat(reported).hasSize(1);
        });
    }

    @Test void weightsFromTheLayoutLayOutWithoutReporting() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), a, b);
            split.setSize(1000 + SplitContainer.dividerSize(), 300); split.doLayout();
            split.setWeights(List.of(0.3, 0.7)); split.doLayout();
            assertThat(a.getWidth()).isEqualTo(300);
            assertThat(split.weights()).containsExactly(0.3, 0.7);
            assertThat(reported).isEmpty();
        });
    }

    @Test void aThemeChangeKeepsTheDividers() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.7, 0.3), a, b);
            split.setSize(1000 + SplitContainer.dividerSize(), 300); split.doLayout();
            var before = a.getBounds();
            themes.configure(false, TerminalSchemeChoice.MATCH);
            SwingUtilities.updateComponentTreeUI(split); split.doLayout();
            assertThat(a.getBounds()).isEqualTo(before);
            assertThat(split.weights()).containsExactly(0.7, 0.3);
            assertThat(reported).isEmpty();
        });
    }

    @Test void theMinimumSizeAddsTheChildrenAlongAndTakesTheLargestAcross() throws Exception {
        edt(() -> {
            int divider = SplitContainer.dividerSize();
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), child(40), child(60));
            assertThat(split.getMinimumSize()).isEqualTo(new Dimension(100 + divider, 70));
            var stacked = split(Orientation.VERTICAL, List.of(0.5, 0.5), child(40), child(60));
            assertThat(stacked.getMinimumSize()).isEqualTo(new Dimension(60, 120 + divider));
        });
    }

    @Test void dividersPaintTheWindowBorderRuleAndStaySolidUnderGlass() throws Exception {
        edt(() -> {
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), child(10), child(10));
            var surface = new Color(30, 40, 50);
            split.setBackground(surface);
            split.setGlass(true);
            assertThat(split.isOpaque()).as("translucent children show through").isFalse();
            split.setSize(200 + SplitContainer.dividerSize(), 100); split.doLayout();
            var rule = split.divider(0);
            assertThat(rule.isOpaque()).isTrue();
            assertThat(rule.getCursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
            var image = new BufferedImage(rule.getWidth(), rule.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { rule.paint(g); } finally { g.dispose(); }
            assertThat(new Color(image.getRGB(rule.getWidth() / 2, 50), true)).as("the rule")
                .isEqualTo(ThemeTestSupport.color(true, SemanticColor.BORDER_WINDOW));
            assertThat(new Color(image.getRGB(0, 50), true)).as("solid behind the rule").isEqualTo(surface);
            split.setGlass(false);
            assertThat(split.isOpaque()).isTrue();
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.SplitContainerTest'`
Expected: FAIL, `cannot find symbol: class SplitContainer`.

- [ ] **Step 3: Implement `SplitContainer`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/SplitContainer.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.layout.Orientation;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One split of a workspace layout (workspace tabs spec 10): its children side by side (HORIZONTAL) or stacked
 * (VERTICAL), sized by weight, with a draggable divider between each two. A drag moves the boundary between the
 * divider's two neighbours, each keeping at least {@link #MINIMUM_SHARE} of the split, lays them out as the pointer moves
 * and, on release, reports the split's path and its new weights. Positions follow the weights alone, so laying the window
 * out again (a theme change) never moves a divider. Below opacity 1 the split paints nothing behind its children; its
 * dividers stay solid. EDT only.
 */
final class SplitContainer extends JPanel {
    /** The least share of the split a drag leaves either neighbour of a divider: two children get 0.1 to 0.9. */
    static final double MINIMUM_SHARE = 0.1;
    private final Orientation orientation;
    private final List<Integer> path;
    private final List<JComponent> children;
    private final List<Divider> dividers = new ArrayList<>();
    private final BiConsumer<List<Integer>, List<Double>> resized;
    private double[] weights;

    /**
     * @param orientation HORIZONTAL lays the children out left to right
     * @param path the split's place in the layout, as {@code Layout.resize} takes it
     * @param weights one per child
     * @param children two or more
     * @param resized told the path and the weights when a divider drag ends having moved something
     */
    SplitContainer(Orientation orientation, List<Integer> path, List<Double> weights, List<? extends JComponent> children,
                   BiConsumer<List<Integer>, List<Double>> resized) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.path = List.copyOf(path);
        this.children = List.copyOf(children);
        this.resized = Objects.requireNonNull(resized, "resized");
        if (this.children.size() < 2 || weights.size() != this.children.size())
            throw new IllegalArgumentException("A split needs two or more children and a weight for each");
        this.weights = weights.stream().mapToDouble(Double::doubleValue).toArray();
        setLayout(new Arrangement());
        setName("split");
        for (int i = 0; i < this.children.size(); i++) {
            if (i > 0) {
                var divider = new Divider(i - 1);
                dividers.add(divider);
                add(divider);
            }
            add(this.children.get(i));
        }
    }

    Orientation orientation() { return orientation; }
    List<Integer> path() { return path; }
    List<Double> weights() { return Arrays.stream(weights).boxed().toList(); }
    List<JComponent> children() { return children; }
    /** The divider after child {@code index}. */
    JComponent divider(int index) { return dividers.get(index); }

    /** Takes the weights the layout holds now and lays the children out again; reports nothing. */
    void setWeights(List<Double> next) {
        if (next.size() != weights.length) throw new IllegalArgumentException("A weight for each child");
        double[] values = next.stream().mapToDouble(Double::doubleValue).toArray();
        if (Arrays.equals(values, weights)) return;
        weights = values;
        revalidate(); repaint();
    }

    /** Below opacity 1 the split leaves its children's translucent fill alone. */
    void setGlass(boolean glass) {
        setOpaque(!glass);
        repaint();
    }

    /** A divider's thickness: the native split divider's, scaled. */
    static int dividerSize() { return NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER); }

    private boolean horizontal() { return orientation == Orientation.HORIZONTAL; }
    private int lengthOf(Dimension size) { return horizontal() ? size.width : size.height; }
    private int breadthOf(Dimension size) { return horizontal() ? size.height : size.width; }
    /** The length the children share: the split's, less its dividers. */
    private int available() { return Math.max(0, (horizontal() ? getWidth() : getHeight()) - dividers.size() * dividerSize()); }

    /** Lays the children out by weight along the split, each divider after its child; the last child takes the rounding. */
    private final class Arrangement implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return measure(false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return measure(true); }

        @Override public void layoutContainer(Container parent) {
            int available = available(), size = dividerSize(), breadth = horizontal() ? getHeight() : getWidth();
            double total = Arrays.stream(weights).sum(), sum = 0;
            int start = 0;
            for (int i = 0; i < children.size(); i++) {
                sum += weights[i];
                int end = i == children.size() - 1 ? available : (int) Math.round(available * sum / total);
                place(children.get(i), start + i * size, end - start, breadth);
                if (i < dividers.size()) place(dividers.get(i), end + i * size, size, breadth);
                start = end;
            }
        }

        private void place(Component component, int along, int length, int breadth) {
            if (horizontal()) component.setBounds(along, 0, Math.max(0, length), breadth);
            else component.setBounds(0, along, breadth, Math.max(0, length));
        }

        private Dimension measure(boolean minimum) {
            int length = dividers.size() * dividerSize(), breadth = 0;
            for (JComponent child : children) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                length += lengthOf(size);
                breadth = Math.max(breadth, breadthOf(size));
            }
            return horizontal() ? new Dimension(length, breadth) : new Dimension(breadth, length);
        }
    }

    /** The rule after child {@code index}: dragging it trades length between that child and the next. */
    private final class Divider extends JComponent {
        private final int index;
        private Point origin;
        private double[] start;

        Divider(int index) {
            this.index = index;
            setName("splitDivider");
            setOpaque(true);
            setCursor(Cursor.getPredefinedCursor(horizontal() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            var drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    origin = SwingUtilities.convertPoint(Divider.this, event.getPoint(), SplitContainer.this);
                    start = weights.clone();
                }
                @Override public void mouseDragged(MouseEvent event) { if (origin != null) follow(event); }
                @Override public void mouseReleased(MouseEvent event) {
                    if (origin == null) return;
                    follow(event);
                    boolean moved = !Arrays.equals(start, weights);
                    origin = null;
                    start = null;
                    if (moved) resized.accept(path, weights());
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        private void follow(MouseEvent event) {
            Point at = SwingUtilities.convertPoint(this, event.getPoint(), SplitContainer.this);
            int available = available();
            double total = Arrays.stream(start).sum(), pair = start[index] + start[index + 1], least = MINIMUM_SHARE * total;
            if (available <= 0 || pair < 2 * least) return;
            double moved = (horizontal() ? at.x - origin.x : at.y - origin.y) * total / available;
            double first = Math.clamp(start[index] + moved, least, pair - least);
            double[] next = start.clone();
            next[index] = first;
            next[index + 1] = pair - first;
            weights = next;
            SplitContainer.this.doLayout();
            SplitContainer.this.revalidate();
            SplitContainer.this.repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            graphics.setColor(SplitContainer.this.getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            ChromePainter.installed().splitDivider(graphics, 0, 0, getWidth(), getHeight(), horizontal(), ThemeZone.theme(this));
        }
    }
}
```

Add the `layout` edge to the workspace package:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java", [
    ("dev.jasper.app.launch, dev.jasper.app.lifecycle,", "dev.jasper.app.launch, dev.jasper.app.layout, dev.jasper.app.lifecycle,", 1)])
PY
```

- [ ] **Step 4: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "SplitContainer.java", "app.workspace", "SplitContainer", T + "SplitContainerTest.java",
           "New in workspace-tabs plan 3a Task 2; a workspace split with draggable dividers")
ledger.add(T + "SplitContainerTest.java", "app.workspace", "SplitContainerTest", T + "SplitContainerTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 3a Task 2; split geometry, drags and dividers")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.SplitContainerTest' --tests 'dev.jasper.app.workspace.SplitDividerTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (`SplitContainerTest` 7). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Split container: n-ary splits with weighted, draggable dividers

A divider drag trades length between its two neighbours, each keeping a
tenth of the split, and reports the weights on release; positions follow
the weights, so a theme change cannot move a divider.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: The workspace view, its groups and their strips

Spec 10: each window renders a `Layout`; each group shows its own always-visible strip over the selected tab; every tab,
the window's terminal tabs included, is a registry `Tab` (Rulings 3, 5, 6, 7, 10, 27, 29). Terminal tabs are still
`TerminalTab`s with their internal splits until Task 5, so Cmd+D behaves as today here; the view already renders any
layout, which the tests drive through `WorkspaceView`. A tab that joins or leaves a strip by a move takes its place at
once. `TerminalDeck`, the strip's reveal and its `TAB_REVEAL_INSET` role go.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java`, `WorkspaceView.java`
- Replace: `workspace/WindowTabs.java`, `workspace/ClassicTabs.java`, `workspace/TabStrip.java`
- Delete: `workspace/TerminalDeck.java`
- Modify: `workspace/WorkspaceChrome.java:22-23,94-100`, `workspace/WindowContent.java` (Step 5),
  `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:97` (`TAB_REVEAL_INSET` goes),
  `workspace/WorkspaceActions.java:65-71,102-104,127-129`, `workspace/WindowCommandPalette.java:79,295`,
  `workspace/TerminalTab.java:15` (a `TabContent`), `contributions/TabRegistry.java` (`add`, `adopt`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceViewTest.java` (new); `TabRegistryTest` (two cases);
  `WindowTabMotionTest` (a case for moves); the existing tests and
  `jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv:433`, `compact-roles.tsv:433` in Step 6
- Ledger: three rows, one dropped

**Interfaces:**
- Consumes: Task 1's `Tab`, `TabRegistry`, `WorkspaceHost`; Task 2's `SplitContainer`; plan 2's `Layout`, `Group`, `Split`,
  `Child`, `Node`, `Side`, `TabId`, `GroupId`.
- Produces: `TabRegistry.add(TabId id, Tab.Kind kind, TabContent content, UUID window)` and
  `TabRegistry.adopt(TabRegistry from, UUID window)`; `final class WorkspaceView extends JPanel` with `Layout model()`,
  `boolean isEmpty()`, `boolean contains(Tab)`, `List<Tab> tabs()`, `int tabCount()`, `Tab selected()`,
  `Optional<Tab> tab(TabId)`, `List<GroupView> groupViews()`, `GroupView groupView(GroupId)`,
  `GroupView activeGroupView()`, `SplitContainer split(List<Integer> path)`, `void open(Tab)`,
  `void openBeside(Tab, GroupId, Side)`, `void remove(Tab)`, `void select(Tab)`, `void activate(GroupId)`,
  `void moveTab(Tab, GroupId, int)`, `void reorder(Tab, GroupId, int)`, `void moveToNewGroup(Tab, GroupId, Side)`,
  `void resizeSplit(List<Integer>, List<Double>)`, `void toggleMaximize()`, `void unmaximize()`,
  `boolean moved(Tab)`, `Optional<GroupId> neighbour(Side)`,
  `void refresh()`, `void applyTheme(ResolvedTheme, boolean)`, `void setGlass(boolean)`, `void setAnimations(AnimationSettings)`,
  `void close()`; `final class GroupView extends JPanel` with `GroupId id()`, `TabStrip strip()`, `WindowContent owner()`,
  `List<Tab> tabs()`, `Tab selected()`, `boolean activeGroup()`, `void display(List<Tab>, Tab)`, `void refresh()`, the
  strip's callbacks (`title`, `icon`, `tooltip`, `shortcut`, `terminal`, `running`, `menu`, `select`, `close`, `reorder`,
  `newTab`, `moved`), `Rectangle stripBounds(Component target)`, `Rectangle contentBounds(Component target)`, `void close()`;
  `WorkspaceChrome.tabs(GroupView)`; `WindowTabs(GroupView)`, `WindowTabs(GroupView, LongSupplier)`, `boolean active()`;
  `ClassicTabs(GroupView)`; on `WindowContent`: `WorkspaceView workspace()`, `TabRegistry tabRegistry()`,
  `java.util.List<Tab> tabs()`, `int tabCount()`, `int groupTabCount()`, `Tab selectedTab()`, `Tab tab(Component)`,
  `void selectTab(Tab)`, `void closeTab(Tab)`, `void selectInGroup(int)`, `void selectRelative(int)`,
  `boolean windowActive()`, `AnimationSettings animations()`, `boolean running(Tab)`, `void workspaceChanged(boolean)`,
  and `tabTitle`, `tabIcon`, `tabTooltip`, `tabMenu` taking a `Tab`; `selectTab(Component)`, `closeTab(Component)`,
  `currentTab()`, `currentPane()`, `terminalTabs()`, `windowTabs()` and `tabBar()` (the active group's strip) stay;
  `tabStrip()`, `selectedComponent()` and `reorderTab(int, int)` go (a strip reorders through its `GroupView`).
  `DesignMetrics.Role.TAB_REVEAL_INSET` goes from jasper-ui.

- [ ] **Step 1: Write the failing tests**

Add to `TabRegistryTest` (the import goes beside the `GroupId` one; the cases go before the class's closing brace):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java", [
    ("import dev.jasper.app.layout.GroupId;\n", "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.TabId;\n", 1),
    ("""        tab.close();
        assertThat(tab.group()).as("a closed tab is in no group").isEmpty();
    }
}
""", """        tab.close();
        assertThat(tab.group()).as("a closed tab is in no group").isEmpty();
    }

    @Test void aTabItsWindowPlacesIsRegisteredWithoutCallingTheHost() {
        hosts();
        var panel = new JPanel();
        int[] disposed = {0};
        var content = new TabContent() {
            @Override public JComponent component() { return panel; }
            @Override public void dispose() { disposed[0]++; }
        };
        var id = TabId.random();
        var tab = registry.add(id, Tab.Kind.TERMINAL, content, main);
        assertThat(mounted).as("the window placed it itself").isEmpty();
        assertThat(tab.id()).isEqualTo(id);
        assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
        assertThat(tab.plugin()).isEmpty();
        assertThat(tab.window()).isEqualTo(main);
        assertThat(registry.inWindow(main)).containsExactly(tab);
        assertThat(registry.hosted()).as("not a plugin's tab").isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.add(id, Tab.Kind.TERMINAL, content, main));
        assertThat(tab.requestClose().join()).as("no guard to ask").isTrue();
        assertThat(disposed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }

    @Test void aJoiningWindowsTabsMoveIntoTheRegistryItJoins() {
        var own = new TabRegistry();
        var window = UUID.randomUUID();
        var left = new ArrayList<Tab>();
        var registration = own.addHost(new WorkspaceHost(window, false, tab -> {}, left::add, tab -> {}, () -> {}));
        var tab = own.add(TabId.random(), Tab.Kind.TERMINAL, new HostedContent(new JPanel()), window);
        int[] closed = {0};
        tab.onClosed(() -> closed[0]++);
        registry.adopt(own, window);
        registration.close();
        assertThat(left).as("leaving the window's own registry closes nothing").isEmpty();
        assertThat(closed[0]).isZero();
        assertThat(own.tabs()).isEmpty();
        assertThat(registry.tabs()).containsExactly(tab);
        registry.addHost(new WorkspaceHost(window, false, mounted::add, mounted::remove, t -> {}, () -> {}));
        tab.close();
        assertThat(closed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }
}
""", 1)])
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceViewTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10: a window's layout as groups in splits, each with its own strip (workspace plan 3a). */
class WorkspaceViewTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A window holding {@code count} terminal tabs in one group, sized and laid out. */
    private static WindowContent window(int count) {
        var owner = content(idleLauncher(new ArrayDeque<>()));
        for (int i = 1; i < count; i++) owner.newTab(HOME);
        owner.setSize(1208, 700);
        LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);
        return owner;
    }

    private static void layout(WindowContent owner) { LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner); }

    @Test void oneTabFillsTheWorkspaceUnderItsAlwaysVisibleStrip() throws Exception {
        edt(() -> {
            var owner = window(1);
            var workspace = owner.workspace();
            assertThat(workspace.groupViews()).hasSize(1);
            var group = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).as("one group fills the workspace").isSameAs(group);
            assertThat(group.strip().component().isVisible()).isTrue();
            assertThat(group.strip().component().getHeight()).isEqualTo(owner.tabHeight());
            var tab = owner.selectedTab();
            assertThat(group.tabs()).containsExactly(tab);
            assertThat(group.selected()).isSameAs(tab);
            assertThat(SwingUtilities.isDescendingFrom(tab.component(), group)).isTrue();
            assertThat(tab.component().isVisible()).isTrue();
            assertThat(owner.tabRegistry().inWindow(owner.id())).as("the window's terminal tab is a registry tab").containsExactly(tab);
            assertThat(tab.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tab.group()).contains(workspace.model().activeGroup());
        });
    }

    @Test void twoGroupsRenderASplitWithAStripEach() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            var first = owner.tabs().get(0); var second = owner.tabs().get(1);
            var left = workspace.model().activeGroup();
            workspace.moveToNewGroup(second, left, Side.RIGHT);
            layout(owner);
            assertThat(workspace.model().root()).isInstanceOf(Split.class);
            var split = workspace.split(List.of());
            assertThat(workspace.getComponent(0)).isSameAs(split);
            assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
            var right = workspace.model().groupOf(second.id()).orElseThrow();
            assertThat(split.children()).containsExactly(workspace.groupView(left), workspace.groupView(right));
            assertThat(workspace.groupView(left).tabs()).containsExactly(first);
            assertThat(workspace.groupView(right).tabs()).containsExactly(second);
            assertThat(SwingUtilities.isDescendingFrom(second.component(), workspace.groupView(right)))
                .as("the moved tab's component left one group view for the other").isTrue();
            assertThat(first.component().isVisible()).as("each group shows its selected tab").isTrue();
            assertThat(owner.selectedTab()).isSameAs(second);
            assertThat(((WindowTabs) workspace.groupView(right).strip()).active()).as("the active group's strip").isTrue();
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).as("another group's strip").isFalse();
            owner.selectTab(first);
            assertThat(workspace.model().activeGroup()).isEqualTo(left);
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).isTrue();
            owner.setActive(false);
            assertThat(((WindowTabs) workspace.groupView(left).strip()).active()).as("an inactive window's strips").isFalse();
        });
    }

    @Test void aDividerDragWritesTheLayoutsWeights() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            layout(owner);
            var split = workspace.split(List.of());
            var divider = split.divider(0);
            int available = split.getWidth() - SplitContainer.dividerSize();
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 10, 1, false, MouseEvent.BUTTON1));
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 0, 0, 2 + available / 5, 10, 1, false, MouseEvent.BUTTON1));
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, 2 + available / 5, 10, 1, false, MouseEvent.BUTTON1));
            var root = (Split) workspace.model().root();
            assertThat(root.children().get(0).weight()).isCloseTo(0.7, within(0.01));
            assertThat(workspace.split(List.of())).as("weights alone rebuild nothing").isSameAs(split);
            assertThat(split.weights().get(0)).isEqualTo(root.children().get(0).weight());
        });
    }

    @Test void aThemeChangeKeepsTheDividers() throws Exception {
        edt(() -> {
            var owner = window(3);
            var workspace = owner.workspace();
            var group = workspace.model().activeGroup();
            workspace.moveToNewGroup(owner.tabs().get(1), group, Side.RIGHT);
            workspace.moveToNewGroup(owner.tabs().get(0), workspace.model().activeGroup(), Side.DOWN);
            workspace.resizeSplit(List.of(), List.of(0.63, 0.37));
            layout(owner);
            var split = workspace.split(List.of());
            var bounds = workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList();
            owner.selectVariant(false); layout(owner);
            owner.selectVariant(true); layout(owner);
            assertThat(workspace.split(List.of())).isSameAs(split);
            assertThat(workspace.groupViews().stream().map(view -> SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), owner)).toList())
                .isEqualTo(bounds);
            assertThat(((Split) workspace.model().root()).children().get(0).weight()).isCloseTo(0.63, within(1e-9));
        });
    }

    @Test void closingTheLastTabOfAGroupCollapsesItAndRestoresTheSizes() throws Exception {
        edt(() -> {
            var owner = window(3);
            var workspace = owner.workspace();
            var a = workspace.model().activeGroup();
            var second = owner.tabs().get(1); var third = owner.tabs().get(2);
            workspace.moveToNewGroup(second, a, Side.RIGHT);
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            var b = workspace.model().groupOf(second.id()).orElseThrow();
            workspace.moveToNewGroup(third, b, Side.RIGHT);
            layout(owner);
            assertThat(workspace.split(List.of()).children()).hasSize(3);
            owner.closeTab(third);
            layout(owner);
            assertThat(workspace.model().groups()).extracting(Group::id).containsExactly(a, b);
            assertThat(workspace.split(List.of()).weights().get(0)).isCloseTo(0.7, within(1e-9));
            assertThat(workspace.split(List.of()).weights().get(1)).isCloseTo(0.3, within(1e-9));
            owner.closeTab(second);
            layout(owner);
            assertThat(workspace.getComponent(0)).as("one group fills the workspace again").isSameAs(workspace.groupView(a));
            assertThat(workspace.split(List.of())).isNull();
        });
    }

    @Test void maximizeShowsTheActiveGroupAloneAndKeepsTheOthersTabs() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            var first = owner.tabs().get(0); var second = owner.tabs().get(1);
            workspace.moveToNewGroup(second, workspace.model().activeGroup(), Side.DOWN);
            var hidden = workspace.groupView(workspace.model().groupOf(first.id()).orElseThrow());
            workspace.toggleMaximize();
            layout(owner);
            var shown = workspace.activeGroupView();
            assertThat(workspace.getComponent(0)).isSameAs(shown);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).as("the other group is out of the tree").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(first.component(), hidden)).as("and keeps its tab").isTrue();
            assertThat(owner.tabCount()).isEqualTo(2);
            workspace.toggleMaximize();
            layout(owner);
            assertThat(SwingUtilities.isDescendingFrom(hidden, workspace)).isTrue();
            assertThat(workspace.split(List.of()).orientation()).isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void groupsReportTheirStripAndCardRectangles() throws Exception {
        edt(() -> {
            var owner = window(2);
            var workspace = owner.workspace();
            workspace.moveToNewGroup(owner.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            layout(owner);
            for (GroupView group : workspace.groupViews()) {
                Rectangle strip = group.stripBounds(workspace), cards = group.contentBounds(workspace);
                Rectangle whole = SwingUtilities.convertRectangle(group.getParent(), group.getBounds(), workspace);
                assertThat(strip.height).isEqualTo(owner.tabHeight());
                assertThat(strip.y).isEqualTo(whole.y);
                assertThat(cards.y).as("the cards start under the strip").isEqualTo(strip.y + strip.height);
                assertThat(strip.union(cards)).isEqualTo(whole);
            }
        });
    }

    @Test void stripsReorderWithinTheirGroupAndKeepTheSelection() throws Exception {
        edt(() -> {
            var owner = window(3);
            var tabs = owner.tabs();
            owner.selectTab(tabs.get(0));
            owner.workspace().activeGroupView().reorder(tabs.get(2), 0);
            assertThat(owner.tabs()).containsExactly(tabs.get(2), tabs.get(0), tabs.get(1));
            assertThat(owner.selectedTab()).as("reordering keeps the selection").isSameAs(tabs.get(0));
            assertThat(owner.workspace().activeGroupView().tabs()).isEqualTo(owner.tabs());
        });
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `WorkspaceView`, `GroupView`, `TabRegistry.add`, `adopt`, `owner.workspace()`.

- [ ] **Step 3: The registry registers a window's own tabs and takes a joining window's**

Insert into `TabRegistry.java`, immediately before `    /** Registers a plugin's close guard`:

```java
    /**
     * Registers a tab that {@code window} has opened and places itself, such as a terminal tab; no host is called.
     * Workspace plan 6 restores saved ids through here.
     *
     * @param id the tab's id, fresh or restored
     * @param kind what the tab holds; a plugin's tab opens through {@link #open}
     * @param content its content
     * @param window the window that shows it
     * @return the tab
     */
    public Tab add(TabId id, Tab.Kind kind, TabContent content, UUID window) {
        requireEdt();
        if (kind == Tab.Kind.HOSTED) throw new IllegalArgumentException("A plugin's tab opens through open()");
        if (tab(id).isPresent()) throw new IllegalArgumentException("Tab " + id + " is already registered");
        var tab = new Tab(this, id, kind, "", id.toString(), "", content);
        tabs.add(tab);
        tab.window(Objects.requireNonNull(window, "window"));
        return tab;
    }

    /**
     * Takes over the tabs {@code window} holds in {@code from}, its own registry, as the window joins this one. The tabs
     * keep their identity, content and listeners; {@code from} forgets them, so the window's leaving it closes nothing.
     *
     * @param from the window's own registry
     * @param window the joining window
     */
    public void adopt(TabRegistry from, UUID window) {
        requireEdt();
        if (from == this) return;
        for (var tab : from.inWindow(window)) {
            if (tab(tab.id()).isPresent()) throw new IllegalArgumentException("Tab " + tab.id() + " is already registered");
            from.tabs.remove(tab);
            tab.registry = this;
            tabs.add(tab);
        }
    }

```

`TerminalTab` becomes a tab's content (Task 5 replaces it):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java", [
    ("public final class TerminalTab extends JPanel implements AutoCloseable {",
     "public final class TerminalTab extends JPanel implements dev.jasper.app.contributions.TabContent, AutoCloseable {", 1),
    ("    void start() { focusedPane().start(); }\n",
     "    void start() { focusedPane().start(); }\n\n"
     "    @Override public JComponent component() { return this; }\n"
     "    @Override public void dispose() { close(); }\n", 1)])
PY
```

- [ ] **Step 4: The group view, the workspace view and the strips per group**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/GroupView.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * One tab group of a window's workspace (workspace tabs spec 10): the look's tab strip, always shown, over a card area
 * showing the group's selected tab. The strip lists, selects, closes, reorders and opens tabs in this group only. A tab's
 * component enters and leaves the card area as the layout puts the tab in and out of the group, so a tab moved to
 * another group leaves one group view for the other with its session untouched (spec 2.3). EDT only.
 */
final class GroupView extends JPanel {
    private final WindowContent owner;
    private final WorkspaceView workspace;
    private final GroupId id;
    private final TabStrip strip;
    private final JPanel cards = new JPanel(new Cards());
    private List<Tab> tabs = List.of();
    private Tab selected;
    private boolean closed;

    GroupView(WindowContent owner, WorkspaceView workspace, GroupId id) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.id = Objects.requireNonNull(id, "id");
        setName("group");
        setOpaque(false);
        cards.setName("groupCards");
        cards.setOpaque(false);
        strip = owner.look().tabs(this);
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        add(strip.component(), BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
    }

    GroupId id() { return id; }
    TabStrip strip() { return strip; }
    WindowContent owner() { return owner; }
    /** The group's tabs in strip order. */
    List<Tab> tabs() { return tabs; }
    /** The group's selected tab; null in the empty workspace. */
    Tab selected() { return selected; }
    /** Whether this is its window's active group. */
    boolean activeGroup() { return workspace.model().activeGroup().equals(id); }

    /** Takes the group's tabs and selection from the layout: mounts the tabs it gained, drops the others, shows the selected one. */
    void display(List<Tab> next, Tab chosen) {
        if (closed) return;
        for (Component child : cards.getComponents())
            if (next.stream().noneMatch(tab -> tab.component() == child)) cards.remove(child);
        for (Tab tab : next) if (tab.component().getParent() != cards) cards.add(tab.component());
        for (Tab tab : next) tab.component().setVisible(tab == chosen);
        tabs = List.copyOf(next);
        selected = chosen;
        cards.revalidate();
        cards.repaint();
        refresh();
    }

    /** Refreshes the strip, which shows as active while the window is active and this is its active group. */
    void refresh() { if (!closed) strip.setActive(owner.windowActive() && activeGroup()); }

    String title(Tab tab) { return owner.tabTitle(tab); }
    Icon icon(Tab tab) { return owner.tabIcon(tab); }
    String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** The Select Tab shortcut for the tab at {@code index} of this group, or empty. */
    String shortcut(int index) { return owner.tabShortcut(index); }
    /** Whether the tab is a terminal tab, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    boolean running(Tab tab) { return owner.running(tab); }
    JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }
    void select(Tab tab) { owner.selectTab(tab); }
    void close(Tab tab) { owner.closeTab(tab); }
    /** A strip drag: moves the tab to {@code index} of this group, keeping the selection. */
    void reorder(Tab tab, int index) { workspace.reorder(tab, id, index); }
    /** The classic tabs' "+": a new terminal tab in this group. */
    void newTab() { workspace.activate(id); owner.invoke(ActionId.NEW_TAB); }
    /** Whether {@code tab} joined this group by a move: the strip shows it at once, without the open motion (Rulings 27). */
    boolean moved(Tab tab) { return workspace.moved(tab); }

    /** The strip's rectangle in {@code target}'s coordinates (workspace plan 5 resolves drops over it). */
    Rectangle stripBounds(Component target) { return SwingUtilities.convertRectangle(this, strip.component().getBounds(), target); }
    /** The card area's rectangle in {@code target}'s coordinates. */
    Rectangle contentBounds(Component target) { return SwingUtilities.convertRectangle(this, cards.getBounds(), target); }

    /** The group left the layout: its strip is released and its card area keeps no tab. */
    void close() {
        if (closed) return;
        closed = true;
        strip.close();
        cards.removeAll();
    }

    /** Every tab fills the card area, as a tabbed pane's cards do; only the selected one is visible. */
    private static final class Cards implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return largest(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return largest(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            for (Component child : parent.getComponents())
                child.setBounds(insets.left, insets.top, parent.getWidth() - insets.left - insets.right,
                    parent.getHeight() - insets.top - insets.bottom);
        }
        private static Dimension largest(Container parent, boolean minimum) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceView.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One window's workspace (workspace tabs spec 10): its {@link Layout} and the tabs the layout places, drawn as
 * {@link SplitContainer}s over {@link GroupView}s, or the maximized group alone. Every change is a layout operation; the
 * view renders the selection, the active group and the maximize the layout holds and never picks them itself. It
 * rebuilds its splits only when the layout's shape changes (orientation, children, groups, maximize); otherwise it moves
 * tab components between group views, shows selections and passes divider weights on. It never creates or disposes a
 * tab: its window attaches and detaches them. Its layout accessor is {@code model()}: a {@code JPanel} cannot declare a
 * {@code layout()} that returns a value, because {@code java.awt.Container} has a {@code void layout()}. EDT only.
 */
final class WorkspaceView extends JPanel {
    private final WindowContent owner;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
    private final Map<TabId, Tab> tabs = new HashMap<>();
    private final Map<List<Integer>, SplitContainer> splits = new HashMap<>();
    /** The tabs that came into their group by a move, which the strips show at once, without motion (Rulings 27). */
    private final Set<TabId> moved = new HashSet<>();
    private Layout layout = Layout.empty();
    private Object shape;
    private boolean glass, closed;

    WorkspaceView(WindowContent owner) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("workspace");
        render();
    }

    /** The layout this view renders. */
    Layout model() { return layout; }
    boolean isEmpty() { return layout.isEmpty(); }
    boolean contains(Tab tab) { return tab != null && tabs.get(tab.id()) == tab; }
    /** Every tab in workspace order: the groups in tree order, each group's tabs in strip order. */
    List<Tab> tabs() { return layout.tabs().stream().map(tabs::get).toList(); }
    int tabCount() { return tabs.size(); }
    /** The active group's selected tab; null in the empty workspace. */
    Tab selected() { return layout.activeTab().map(tabs::get).orElse(null); }
    Optional<Tab> tab(TabId id) { return Optional.ofNullable(tabs.get(id)); }
    List<GroupView> groupViews() { return List.copyOf(groups.values()); }
    GroupView groupView(GroupId id) { return groups.get(id); }
    GroupView activeGroupView() { return groups.get(layout.activeGroup()); }
    /** The split at {@code path}, while the layout has one there and shows it; null otherwise. */
    SplitContainer split(List<Integer> path) { return splits.get(path); }

    /** Opens {@code tab} at the end of the active group, selected; a tab arriving from another window counts as moved. */
    void open(Tab tab) {
        if (tab.moving()) moved.add(tab.id());
        Group active = layout.group(layout.activeGroup()).orElseThrow();
        place(tab, layout.open(tab.id(), active.id(), active.tabs().size()));
    }

    /** Opens {@code tab} in a new group on {@code side} of {@code group}; the empty workspace opens it in its group. */
    void openBeside(Tab tab, GroupId group, Side side) {
        if (layout.isEmpty()) open(tab);
        else place(tab, layout.split(tab.id(), group, side));
    }

    private void place(Tab tab, Layout next) {
        tabs.put(tab.id(), tab);
        apply(next);
    }

    /** Takes {@code tab} out of the layout; its component leaves its group view. */
    void remove(Tab tab) {
        if (!contains(tab)) return;
        Layout next = layout.remove(tab.id());
        tabs.remove(tab.id());
        moved.remove(tab.id());
        apply(next);
    }

    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }
    void activate(GroupId group) { apply(layout.activate(group)); }
    /** Moves {@code tab} to {@code index} of {@code group}, which may be another group. */
    void moveTab(Tab tab, GroupId group, int index) {
        if (!contains(tab)) return;
        moved.add(tab.id());
        apply(layout.move(tab.id(), group, index));
    }

    /** Moves {@code tab} to {@code index} of {@code group}, its own, keeping the tab that group showed. */
    void reorder(Tab tab, GroupId group, int index) {
        if (!contains(tab)) return;
        Optional<TabId> shown = layout.group(group).flatMap(Group::selected);
        Layout next = layout.move(tab.id(), group, index);
        if (shown.isPresent() && !shown.get().equals(tab.id())) next = next.select(shown.get());
        apply(next);
    }

    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        moved.add(tab.id());
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }
    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
    void toggleMaximize() { apply(layout.toggleMaximize(layout.activeGroup())); }
    /** Ends a maximize, as a tab moving in or out of the window does (spec 15). */
    void unmaximize() { if (layout.maximized().isPresent()) apply(layout.unmaximize()); }
    /** Whether {@code tab} came into its group by a move rather than by opening (Rulings 27). */
    boolean moved(Tab tab) { return moved.contains(tab.id()); }
    /** The group in {@code direction} from the active one, by the layout's geometry. */
    Optional<GroupId> neighbour(Side direction) { return layout.neighbour(layout.activeGroup(), direction); }

    private void apply(Layout next) { apply(next, true); }

    /** Renders {@code next}; with {@code refocus} the active tab takes focus when it or the shape changed (Rulings 10). */
    private void apply(Layout next, boolean refocus) {
        if (closed) return;
        Tab before = selected();
        Object previous = shape;
        layout = next;
        render();
        owner.workspaceChanged(refocus && (selected() != before || !Objects.equals(previous, shape)));
    }

    private void render() {
        var live = new LinkedHashMap<GroupId, GroupView>();
        for (Group group : layout.groups()) {
            GroupView view = groups.remove(group.id());
            live.put(group.id(), view != null ? view : new GroupView(owner, this, group.id()));
        }
        for (GroupView gone : groups.values()) gone.close();
        groups.clear();
        groups.putAll(live);
        for (Group group : layout.groups())
            groups.get(group.id()).display(group.tabs().stream().map(tabs::get).toList(), group.selected().map(tabs::get).orElse(null));
        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        Object next = shape(shown);
        if (!next.equals(shape)) {
            shape = next;
            splits.clear();
            removeAll();
            add(build(shown, List.of()), BorderLayout.CENTER);
            revalidate();
        } else {
            for (var entry : splits.entrySet()) entry.getValue().setWeights(weightsAt(entry.getKey()));
        }
        repaint();
    }

    /** The tree's shape without its weights: what a rebuild depends on. */
    private static Object shape(Node node) {
        return switch (node) {
            case Group group -> group.id();
            case Split split -> List.of(split.orientation(), split.children().stream().map(child -> shape(child.node())).toList());
        };
    }

    private JComponent build(Node node, List<Integer> path) {
        return switch (node) {
            case Group group -> groups.get(group.id());
            case Split split -> {
                var children = new ArrayList<JComponent>();
                for (int i = 0; i < split.children().size(); i++) {
                    var child = new ArrayList<>(path);
                    child.add(i);
                    children.add(build(split.children().get(i).node(), List.copyOf(child)));
                }
                var container = new SplitContainer(split.orientation(), path, split.children().stream().map(Child::weight).toList(),
                    children, this::resizeSplit);
                container.setBackground(getBackground());
                container.setGlass(glass);
                splits.put(path, container);
                yield container;
            }
        };
    }

    private List<Double> weightsAt(List<Integer> path) {
        Node node = layout.root();
        for (int index : path) node = ((Split) node).children().get(index).node();
        return ((Split) node).children().stream().map(Child::weight).toList();
    }

    /** Refreshes every group's strip: its tabs, titles, selection and activity. */
    void refresh() { if (!closed) for (GroupView group : groups.values()) group.refresh(); }

    /** Takes the theme: backgrounds, the strips' zone, and the delegates of a group the maximize keeps out of the tree. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        setBackground(theme.surface());
        for (SplitContainer split : splits.values()) split.setBackground(theme.surface());
        for (GroupView group : groups.values()) {
            if (updateDelegates && !SwingUtilities.isDescendingFrom(group, this)) SwingUtilities.updateComponentTreeUI(group);
            WorkspaceTheme.apply(group.strip().component(), theme);
            group.refresh();
        }
    }

    /** Below opacity 1 the workspace and its splits paint nothing behind the tabs; dividers stay solid. */
    void setGlass(boolean value) {
        glass = value;
        setOpaque(!value);
        for (SplitContainer split : splits.values()) split.setGlass(value);
        repaint();
    }

    void setAnimations(AnimationSettings settings) { for (GroupView group : groups.values()) group.strip().setAnimations(settings); }

    /** The window closed: every strip is released; the tabs are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (GroupView group : groups.values()) group.close();
    }
}
```

Replace the whole of `workspace/TabStrip.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * One tab group's strip (workspace tabs spec 10; Enterprise presentation spec 10.5): today's equal tabs or the classic
 * tabs, always shown, even for one tab. It lists, selects, closes, reorders and opens tabs in its group view only, and
 * shows as active while that group is the active group of an active window. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the group's cards. */
    JComponent component();
    /** Takes the group's tabs, titles, selection and states. */
    void refresh();
    /** Whether the strip shows as active: its window active and its group the active group. */
    void setActive(boolean active);
    /** The configured animations; classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** The group's tabs, as a menu of literal titles of at most {@code WindowTabs.TITLE_LIMIT} characters. */
    JPopupMenu tabList();
    /** Releases the tabs. */
    @Override void close();
}
```

`WorkspaceChrome` makes one per group:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java", [
    ("    /** The editor tabs, made for each window by {@link #tabs(WindowContent)}. */",
     "    /** The editor tabs, made for each tab group by {@link #tabs(GroupView)}. */", 1),
    ("""    /** The tab strip for {@code owner} (spec 10.5). */
    TabStrip tabs(WindowContent owner) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(owner);
            case CLASSIC -> new ClassicTabs(owner);
        };
    }""", """    /** One tab group's strip (spec 10.5; workspace tabs spec 10). */
    TabStrip tabs(GroupView group) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(group);
            case CLASSIC -> new ClassicTabs(group);
        };
    }""", 1)])
PY
```

Replace the whole of `workspace/WindowTabs.java` with (the equal tabs over one group, always shown, with their open and
close motion and without the reveal, Rulings 7):

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;

/**
 * Equal-width, centered tabs of one tab group, always shown (workspace tabs spec 10). A tab that opens or closes moves
 * into or out of its place; one that joins or leaves the group by a move, or is reordered, takes its place at once
 * (spec 4, Rulings 27). EDT only.
 */
final class WindowTabs extends JPanel implements TabStrip {
    static final int TITLE_LIMIT = 200;
    private final GroupView group;
    private final LongSupplier clock;
    private final List<Slot> slots = new ArrayList<>();
    private final IdentityHashMap<Tab, Entry> entries = new IdentityHashMap<>();
    private final List<Tab> order = new ArrayList<>();
    private final JPanel viewport = new JPanel(null);
    private AnimationSettings animations = AnimationSettings.defaults();
    private Timer timer;
    private Tab selected;
    private int layoutWidth = -1, layoutHeight = -1;
    private boolean active = true, disposed;

    WindowTabs(GroupView group) { this(group, System::nanoTime); }
    WindowTabs(GroupView group, LongSupplier clock) {
        super(null);
        this.group = group; this.clock = clock;
        setName("windowTabs"); setOpaque(false);
        viewport.setOpaque(false); viewport.setName("tabViewport"); add(viewport);
    }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);
        var updated = new ArrayList<>(group.tabs());
        boolean changed = !updated.equals(order);
        boolean added = updated.stream().anyMatch(tab -> !entries.containsKey(tab));
        boolean removed = order.stream().anyMatch(tab -> !updated.contains(tab));
        // Only an open or a close moves: a tab that arrives or leaves by a move does not (Rulings 27).
        boolean opened = updated.stream().anyMatch(tab -> !entries.containsKey(tab) && !group.moved(tab));
        boolean closing = order.stream().anyMatch(tab -> !updated.contains(tab) && tab.closed());
        if (changed) {
            for (Tab tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.remove(tab);
                entry.slot.entry = null; // The shrinking gap retains no closed session, plugin or controls.
                viewport.remove(entry);
            }
            if (!added && !removed) { settle(); slots.clear(); }
            order.clear(); order.addAll(updated);
            for (int i = 0; i < order.size(); i++) {
                Tab tab = order.get(i);
                if (!entries.containsKey(tab)) {
                    var entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
                    int at = i == 0 ? 0 : slots.indexOf(entries.get(order.get(i - 1)).slot) + 1;
                    while (at < slots.size() && slots.get(at).entry == null) at++;
                    slots.add(at, entry.slot);
                } else if (!added && !removed) slots.add(entries.get(tab).slot);
            }
            Curve curve = added ? Curve.OPEN : Curve.CLOSE;
            int millis = added ? animations.tabOpenMillis() : animations.tabCloseMillis();
            if (order.size() <= 1 || !opened && !closing || !animations.enabled() || !group.owner().lookAnimates() || layoutWidth <= 0) millis = 0;
            for (Slot slot : slots) slot.width.target(slot.entry == null ? 0 : 1.0 / order.size(), now, millis, curve);
            sample(now);
        }
        selected = group.selected();
        setBackground(group.owner().theme().surface());
        for (Tab tab : order) entries.get(tab).refresh();
        doLayout(); syncTimer(); revalidate(); repaint();
    }

    @Override public void setActive(boolean active) { this.active = active; refresh(); }
    /** Whether the strip shows as active. */
    boolean active() { return active; }

    /** Literal bounded titles, also used by hosted-content integrations. */
    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Tab tab : order) {
            var item = new JCheckBoxMenuItem(capped(group.title(tab)), group.icon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) group.select(tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(group.owner().tabHeight())); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        // A genuine resize settles motion.
        if (layoutWidth > 0 && (width != layoutWidth || layoutHeight > 0 && height > 0 && height != layoutHeight)) settle();
        layoutWidth = width; layoutHeight = height;
        viewport.setBounds(0, 0, width, height);
        double x = 0;
        for (Slot slot : slots) {
            int left = (int) Math.round(x);
            x += slot.width.current * width;
            int right = (int) Math.round(x);
            slot.bounds.setBounds(left, 0, Math.max(0, right - left), height);
            if (slot.entry != null) {
                slot.entry.setBounds(slot.bounds);
                slot.entry.setVisible(right > left);
                slot.entry.doLayout();
            }
        }
    }

    // Repaint the translucent surface exactly once, even for child hover repaints.
    @Override protected boolean isPaintingOrigin() { return true; }
    @Override protected void paintComponent(Graphics graphics) {
        var bounds = new ArrayList<Rectangle>(slots.size());
        int chosen = -1, hovered = -1;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) chosen = bounds.size();
            if (slot.entry != null && slot.entry.hovered) hovered = bounds.size();
            bounds.add(slot.bounds);
        }
        ChromePainter.installed().tabStrip(this, graphics, group.owner().theme().workspace(), group.owner().workspaceSurface(), bounds, chosen, hovered, active);
    }

    private Color surface(boolean selected) {
        return ChromePainter.installed().tabSurface(group.owner().theme().workspace(), group.owner().workspaceSurface(), selected);
    }

    /** {@code title}, or past {@code TITLE_LIMIT} characters its start and an ellipsis within that limit, for tips and menus. */
    static String capped(String title) {
        if (title.length() <= TITLE_LIMIT) return title;
        int end = TITLE_LIMIT - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "…";
    }

    @Override public void setAnimations(AnimationSettings settings) {
        if (disposed || animations.equals(settings)) return;
        animations = settings; settle(); doLayout(); repaint();
    }

    /** One elapsed-time frame; tests inject the same monotonic clock used when retargeting. */
    void animate(long now) {
        if (disposed) return;
        sample(now); doLayout(); syncTimer(); repaint();
    }
    private void sample(long now) {
        for (Slot slot : slots) slot.width.sample(now);
        slots.removeIf(slot -> slot.entry == null && !slot.width.running);
    }
    private void settle() {
        slots.removeIf(slot -> slot.entry == null);
        for (Slot slot : slots) slot.width.set(order.isEmpty() ? 0 : 1.0 / order.size());
        syncTimer();
    }
    boolean motionRunning() { return timer != null && timer.isRunning(); }
    private void syncTimer() {
        boolean moving = slots.stream().anyMatch(slot -> slot.width.running);
        if (moving && !disposed && timer == null) {
            timer = new Timer(8, event -> animate(clock.getAsLong())); timer.start();
        } else if ((!moving || disposed) && timer != null) { timer.stop(); timer = null; }
    }
    @Override public void close() {
        disposed = true; syncTimer(); slots.clear(); entries.clear(); order.clear(); selected = null;
        viewport.removeAll(); setVisible(false);
    }

    private static final class Slot {
        Entry entry;
        final Value width = new Value();
        final Rectangle bounds = new Rectangle();
        Slot(Entry entry) { this.entry = entry; }
    }

    /** Measured monotonic response, normalized to operation duration. See docs/design/alacritty-tabs.md. */
    private enum Curve {
        OPEN(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 192, 208, 225, 250, 283, 317, 367, 417, 475},
            new double[]{0, .088, .201, .331, .444, .546, .637, .711, .775, .824, .863, .894, .919, .940, .958, .975, .986, .993, .997, 1}),
        CLOSE(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 200, 233, 267, 300, 350, 375, 425},
            new double[]{0, .077, .250, .373, .486, .585, .665, .736, .796, .842, .877, .919, .951, .972, .986, .993, .997, 1});
        private final double[] times, values;
        Curve(double[] times, double[] values) { this.times = times; this.values = values; }
        double at(double fraction) {
            double time = fraction * times[times.length - 1];
            for (int i = 1; i < times.length; i++) if (time <= times[i]) {
                double local = (time - times[i - 1]) / (times[i] - times[i - 1]);
                return values[i - 1] + local * (values[i] - values[i - 1]);
            }
            return 1;
        }
    }
    private static final class Value {
        double current, from, to;
        long start, nanos;
        boolean running;
        Curve curve = Curve.OPEN;
        void set(double value) { current = from = to = value; running = false; }
        void target(double value, long now, int millis, Curve curve) {
            from = current; to = value; start = now; nanos = millis * 1_000_000L; this.curve = curve;
            running = millis > 0 && current != to;
            if (!running) set(value);
        }
        void sample(long now) {
            if (!running) return;
            double progress = Math.max(0, Math.min(1, (double) (now - start) / nanos));
            current = from + (to - from) * curve.at(progress);
            if (progress >= 1) set(to);
        }
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        NativeStyles.installed().unframed(button);
        button.setContentAreaFilled(false); button.setFocusable(false);
    }
    private final class Entry extends JPanel {
        private final Tab tab;
        private final Slot slot;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
            }
        };
        private boolean hovered;
        private Point origin;

        Entry(Tab tab) {
            super(null); this.tab = tab; slot = new Slot(this); setOpaque(false);
            flat(select); flat(close); select.setHorizontalAlignment(SwingConstants.CENTER);
            select.addActionListener(event -> { if (!disposed) group.select(tab); });
            close.setRolloverEnabled(true); close.setToolTipText("Close tab");
            close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) group.close(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                private void popup(MouseEvent event) { group.menu(tab).show(event.getComponent(), event.getX(), event.getY()); }
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { group.close(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        group.select(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    Point start = origin; origin = null;
                    if (disposed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    if (start.distance(end) <= NativeStyles.installed().metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)
                        || !viewport.getBounds().contains(end)) return;
                    settle(); doLayout();
                    for (int i = 0; i < order.size(); i++) {
                        Entry target = entries.get(order.get(i));
                        if (target.isVisible() && SwingUtilities.convertRectangle(viewport, target.getBounds(), WindowTabs.this).contains(end)) {
                            group.reorder(tab, i); break;
                        }
                    }
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            add(close); add(select);
        }
        private void hover(boolean value) { hovered = value; doLayout(); repaint(); }
        void refresh() {
            String text = group.title(tab);
            select.setText(text); select.getAccessibleContext().setAccessibleName(text);
            select.setName("select:" + text); close.setName("close:" + text);
            String shortcut = group.shortcut(order.indexOf(tab));
            String tip = capped(group.tooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            select.getAccessibleContext().setAccessibleDescription(group.running(tab) ? "Session running" : null);
            Color surface = surface(tab == selected), ink = ThemeZone.color(this, SemanticColor.TEXT_PRIMARY);
            close.setIcon(AppIcons.forSurface(AppIcons.named("CLOSE"), surface, ink));
            close.setRolloverIcon(AppIcons.forSurface(AppIcons.named("CLOSE_HOVERED"), surface, ink));
            select.setSelected(tab == selected); select.setFont(ThemeZone.font(Typography.Role.BODY));
            select.setForeground(tab == selected ? ink
                : ThemeZone.color(this, !active ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_SECONDARY));
            doLayout();
        }
        @Override public void doLayout() {
            boolean closeFits = getWidth() >= NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH);
            int edge = Math.min(NativeStyles.installed().metric(closeFits ? DesignMetrics.Role.TAB_CLOSE_GUTTER
                : DesignMetrics.Role.TAB_COMPACT_GUTTER), getWidth() / 2);
            // Reserve symmetric close gutters only when text and the affordance can fit together.
            select.setBounds(edge, 0, Math.max(0, getWidth() - 2 * edge), getHeight());
            int closeWidth = NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_WIDTH);
            close.setBounds(Math.max(0, getWidth() - NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_TRAILING) - closeWidth),
                0, closeWidth, getHeight());
            close.setVisible(hovered && closeFits);
        }
    }
}
```

Replace the whole of `workspace/ClassicTabs.java` with (the classic tabs over one group; geometry, painting and
accessibility unchanged):

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Classic editor tabs of one tab group (spec 10.5; the Workstation mocks): always visible, content-sized, each with a
 * status dot for a terminal tab, its label and a persistent ×, a "+" after the last one (at the trailing edge, over the
 * tabs a crowded strip clips) that opens a tab in this group; the active tab on {@code surface.card} with a 2 px
 * {@code tab.indicator} underline; 1 px {@code border.window} dividers and the strip's rule on {@code surface.tabstrip}.
 * Never moves.
 */
final class ClassicTabs extends JPanel implements TabStrip {
    private final GroupView group;
    private final List<Entry> entries = new ArrayList<>();
    private final Add add = new Add();
    private Tab selected;
    private boolean active = true, disposed;

    ClassicTabs(GroupView group) {
        super(null);
        this.group = group;
        setName("classicTabs");
        setOpaque(true);
        add(add);
    }

    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        var current = group.tabs();
        if (!current.equals(entries.stream().map(entry -> entry.tab).toList())) {
            entries.forEach(this::remove);
            entries.clear();
            for (Tab tab : current) { var entry = new Entry(tab); entries.add(entry); add(entry); }
        }
        selected = group.selected();
        for (Entry entry : entries) entry.refresh();
        setBackground(ThemeZone.color(this, SemanticColor.SURFACE_TABSTRIP));
        revalidate(); repaint();
    }

    @Override public void setActive(boolean value) { active = value; refresh(); }
    @Override public void setAnimations(AnimationSettings settings) { }

    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Entry entry : entries) {
            var item = new JCheckBoxMenuItem(WindowTabs.capped(group.title(entry.tab)), group.icon(entry.tab), entry.tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) group.select(entry.tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public void close() { disposed = true; entries.clear(); removeAll(); selected = null; }

    List<Entry> entries() { return List.copyOf(entries); }
    JComponent add() { return add; }

    @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(group.owner().tabHeight())); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override public void doLayout() {
        int height = Math.max(0, getHeight() - metric(DesignMetrics.Role.HAIRLINE)), divider = metric(DesignMetrics.Role.HAIRLINE);
        int addWidth = 2 * metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET) + metric(DesignMetrics.Role.CHROME_GLYPH);
        int[] labels = new int[entries.size()];
        int labelRoom = Math.max(0, getWidth() - addWidth);
        for (int i = 0; i < labels.length; i++) { labels[i] = entries.get(i).labelWidth(); labelRoom -= entries.get(i).chromeWidth() + divider; }
        // Too many tabs: the longest labels shorten first, down to the minimum; past that the strip clips.
        int cap = cap(labels, labelRoom, metric(DesignMetrics.Role.CLASSIC_TAB_MIN_LABEL)), x = 0;
        for (int i = 0; i < labels.length; i++) {
            int width = entries.get(i).chromeWidth() + Math.min(labels[i], cap);
            entries.get(i).setBounds(x, 0, width, height);
            x += width + divider;
        }
        // The + follows the last divider; when the tabs run past the edge it stays at the edge, over the clipped tabs,
        // behind a divider of its own. As child 0 it paints and takes the mouse above them.
        add.covering = x + addWidth > getWidth();
        if (add.covering) add.setBounds(Math.max(0, getWidth() - addWidth - divider), 0, addWidth + divider, height);
        else add.setBounds(x, 0, addWidth, height);
    }

    /** The largest label width, at least {@code minimum}, at which every label cut to it fits {@code room}. */
    static int cap(int[] labels, int room, int minimum) {
        long total = 0;
        int max = 0;
        for (int label : labels) { total += label; max = Math.max(max, label); }
        if (total <= room) return max;
        int low = Math.min(minimum, max), high = max;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            long sum = 0;
            for (int label : labels) sum += Math.min(label, middle);
            if (sum <= room) low = middle; else high = middle - 1;
        }
        return low;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var theme = ThemeZone.theme(this); var palette = theme.semantic();
        graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int line = metric(DesignMetrics.Role.HAIRLINE), indicator = metric(DesignMetrics.Role.TAB_INDICATOR);
        for (Entry entry : entries) {
            Rectangle at = entry.getBounds();
            if (entry.tab == selected) {
                graphics.setColor(palette.color(SemanticColor.SURFACE_CARD));
                graphics.fillRect(at.x, at.y, at.width, at.height);
                graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.TAB_INDICATOR));
                graphics.fillRect(at.x, at.y + at.height - indicator, at.width, indicator);
            }
            graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
            graphics.fillRect(at.x + at.width, 0, line, at.height);
        }
        graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
        graphics.fillRect(0, getHeight() - line, getWidth(), line);
    }

    /** One tab: its dot, its label and its ×, which closes it on a release over it, as a button does. */
    final class Entry extends JComponent {
        private final Tab tab;
        private boolean closeHover, closePressed;
        private Point origin;

        Entry(Tab tab) {
            this.tab = tab;
            putClientProperty("html.disable", true);
            setFont(ThemeZone.font(Typography.Role.BODY));
            var mouse = new MouseAdapter() {
                private void popup(MouseEvent event) { group.menu(tab).show(Entry.this, event.getX(), event.getY()); }
                private void hover(MouseEvent event) {
                    boolean over = closeTarget().contains(event.getPoint());
                    if (over != closeHover) { closeHover = over; repaint(); }
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { group.close(tab); return; }
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    // A press on the × neither selects nor drags; dragging off it before the release cancels the close.
                    if (closeTarget().contains(event.getPoint())) { closePressed = true; return; }
                    group.select(tab);
                    origin = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    boolean closing = closePressed;
                    closePressed = false;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (closing) {
                        if (SwingUtilities.isLeftMouseButton(event) && closeTarget().contains(event.getPoint())) closeClicked();
                        return;
                    }
                    Point start = origin; origin = null;
                    if (disposed || start == null) return;
                    Point end = SwingUtilities.convertPoint(Entry.this, event.getPoint(), ClassicTabs.this);
                    if (start.distance(end) <= metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)) return;
                    for (int i = 0; i < entries.size(); i++)
                        if (entries.get(i).getBounds().contains(end)) { group.reorder(tab, i); break; }
                }
                @Override public void mouseMoved(MouseEvent event) { hover(event); }
                // While the × is held, its hover shows whether a release would close.
                @Override public void mouseDragged(MouseEvent event) { if (closePressed) hover(event); }
                @Override public void mouseExited(MouseEvent event) { if (closeHover) { closeHover = false; repaint(); } }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        // A bare JComponent has no accessible context; this one names its role and gives the tab's actions.
        @Override public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleEntry();
            return accessibleContext;
        }

        /** A page tab, selected while it is; its first action selects it, its second ("Close tab") is the ×. */
        private final class AccessibleEntry extends AccessibleJComponent implements AccessibleAction {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.PAGE_TAB; }
            @Override public AccessibleStateSet getAccessibleStateSet() {
                var states = super.getAccessibleStateSet();
                if (tab == selected) states.add(AccessibleState.SELECTED);
                return states;
            }
            @Override public AccessibleAction getAccessibleAction() { return this; }
            @Override public int getAccessibleActionCount() { return 2; }
            // The first is a button's "click", so assistive technology's default action selects rather than closes.
            @Override public String getAccessibleActionDescription(int index) {
                return switch (index) {
                    case 0 -> UIManager.getString("AbstractButton.clickText");
                    case 1 -> "Close tab";
                    default -> null;
                };
            }
            @Override public boolean doAccessibleAction(int index) {
                if (disposed || index < 0 || index > 1) return false;
                if (index == 0) group.select(tab); else closeClicked();
                return true;
            }
        }

        // A tab title comes from the terminal (OSC 0/2), so its tip never renders HTML.
        @Override public JToolTip createToolTip() {
            var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
        }

        void refresh() {
            String title = group.title(tab), shortcut = group.shortcut(entries.indexOf(this));
            String tip = WindowTabs.capped(group.tooltip(tab));
            setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            getAccessibleContext().setAccessibleName(title);
            getAccessibleContext().setAccessibleDescription(running() ? "Session running" : null);
            // A title IBM Plex cannot draw (a dirty tab's "●", CJK) takes the platform face (plan 5b, ruling 26).
            setFont(ThemeZone.font(Typography.Role.BODY, label()));
            repaint();
        }

        private boolean dotted() { return group.terminal(tab); }
        boolean running() { return group.running(tab); }
        private String label() { return WindowTabs.capped(group.title(tab)); }
        int labelWidth() { return getFontMetrics(getFont()).stringWidth(label()); }
        /** Everything but the label: the leading inset, the dot and its gap, the gap, the ×, the trailing inset. */
        int chromeWidth() {
            int dot = dotted() ? metric(DesignMetrics.Role.STATUS_DOT) + metric(DesignMetrics.Role.CLASSIC_TAB_GAP) : 0;
            return metric(DesignMetrics.Role.CLASSIC_TAB_LEADING) + dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP)
                + metric(DesignMetrics.Role.CHROME_GLYPH) + metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING);
        }

        /** Where the × is, in this tab. */
        Rectangle closeBounds() {
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), x = getWidth() - metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING) - size;
            return new Rectangle(x, (getHeight() - size) / 2, size, size);
        }

        /**
         * Where the × takes the pointer: a square around the glyph reaching into the gap before it and the trailing inset
         * after it, at most the tab's height, so the small glyph is easy to hit (as IntelliJ's classic tabs are).
         */
        Rectangle closeTarget() {
            Rectangle glyph = closeBounds();
            int reach = Math.min(metric(DesignMetrics.Role.CLASSIC_TAB_TRAILING), metric(DesignMetrics.Role.CLASSIC_TAB_GAP));
            int side = Math.min(getHeight(), glyph.width + 2 * reach);
            return new Rectangle(glyph.x + glyph.width / 2 - side / 2, (getHeight() - side) / 2, side, side);
        }

        void closeClicked() { if (!disposed) group.close(tab); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                FlatUIUtils.setRenderingHints(g);
                var theme = ThemeZone.theme(this);
                boolean chosen = tab == selected;
                int x = metric(DesignMetrics.Role.CLASSIC_TAB_LEADING), middle = getHeight() / 2;
                if (dotted()) {
                    int dot = metric(DesignMetrics.Role.STATUS_DOT);
                    g.setColor(SurfaceContrast.painted(theme, WindowPairs.tabDot(running(), chosen)));
                    g.fillOval(x, middle - dot / 2, dot, dot);
                    x += dot + metric(DesignMetrics.Role.CLASSIC_TAB_GAP);
                }
                Rectangle close = closeBounds();
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                int room = Math.max(0, close.x - metric(DesignMetrics.Role.CLASSIC_TAB_GAP) - x);
                Color ink = SurfaceContrast.painted(theme, WindowPairs.tabLabel(active, chosen));
                g.setColor(ink);
                FlatUIUtils.drawString(this, g, TitleSearchField.clip(label(), metrics, room), x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                g.setColor(closeHover ? ink : SurfaceContrast.painted(theme, WindowPairs.tabClose(chosen)));
                g.setStroke(new java.awt.BasicStroke(Math.max(1f, UIScale.scale(1.2f))));
                g.drawLine(close.x, close.y, close.x + close.width - 1, close.y + close.height - 1);
                g.drawLine(close.x, close.y + close.height - 1, close.x + close.width - 1, close.y);
            } finally { g.dispose(); }
        }
    }

    /** The "+" after the last tab: a flat, unfocusable button that opens a new tab in this group on a release over it. */
    final class Add extends JButton {
        /** Whether the + stands over clipped tabs; it then hides them under the strip's fill and a divider before it. */
        private boolean covering;

        Add() {
            setFocusable(false); setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setToolTipText("New Tab");
            getAccessibleContext().setAccessibleName("New Tab");
            addActionListener(event -> { if (!disposed) group.newTab(); });
        }

        @Override protected void paintComponent(Graphics graphics) {
            var theme = ThemeZone.theme(this); var palette = theme.semantic();
            int size = metric(DesignMetrics.Role.CHROME_GLYPH), stroke = Math.max(1, UIScale.scale(1));
            int x = metric(DesignMetrics.Role.CLASSIC_TAB_PLUS_INSET), middle = getHeight() / 2;
            if (covering) {
                int line = metric(DesignMetrics.Role.HAIRLINE);
                graphics.setColor(palette.color(SemanticColor.SURFACE_TABSTRIP));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(palette.color(SemanticColor.BORDER_WINDOW));
                graphics.fillRect(0, 0, line, getHeight());
                x += line;
            }
            graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.TAB_PLUS));
            graphics.fillRect(x, middle - stroke / 2, size, stroke);
            graphics.fillRect(x + (size - stroke) / 2, middle - size / 2, stroke, size);
        }
    }
}
```

- [ ] **Step 5: The window renders its workspace and registers every tab**

Delete the deck and rewire `WindowContent` (quoted text as Task 1 left it; raw strings keep the Java escapes):

```bash
git rm -q jasper-app/src/main/java/dev/jasper/app/workspace/TerminalDeck.java
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(W, [
    ("import dev.jasper.app.contributions.Contributions;\n",
     "import dev.jasper.app.contributions.Contributions;\nimport dev.jasper.app.contributions.Tab;\n"
     "import dev.jasper.app.contributions.TabRegistry;\nimport dev.jasper.app.contributions.WorkspaceHost;\n"
     "import dev.jasper.app.layout.TabId;\n", 1),
    (r"""    private final java.util.Map<Component, dev.jasper.app.contributions.Tab> hosted = new IdentityHashMap<>();
    private dev.jasper.app.contributions.TabRegistry contentRegistry;
    private Subscription contentHost;
""", r"""    /** Each tab this window shows, with its change listener. */
    private final Map<Tab, Subscription> tabListeners = new IdentityHashMap<>();
    /** Where this window's tabs live: a registry of its own until it joins the application's (connectContributions). */
    private TabRegistry registry = new TabRegistry();
    /** This window's workspace in {@link #registry}; closing it removes the tabs the window still holds. */
    private Subscription host;
""", 1),
    (r"""    private final JTabbedPane tabs = new TerminalDeck();
    /** The look's editor tabs: the equal tabs, or the classic ones (spec 10.5). */
    private final TabStrip windowTabs;
""", r"""    /** The window's tabs as groups in splits, each group under the look's strip (workspace tabs spec 10). */
    private final WorkspaceView workspace;
    /** The configured tab motion, which strips made later take too. */
    private dev.jasper.app.config.AnimationSettings animations = dev.jasper.app.config.AnimationSettings.defaults();
""", 1),
    ("    private final Map<Component, dev.jasper.app.lifecycle.Subscription> contentListeners = new IdentityHashMap<>();\n", "", 1),
    ("    private boolean rearranging;\n", "", 1),
    (r"""        windowTabs = look.tabs(this);
        // The tabs belong to the terminal column, so the stripes and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(windowTabs.component(), BorderLayout.NORTH); terminalColumn.add(tabs, BorderLayout.CENTER);
""", r"""        workspace = new WorkspaceView(this);
        // The workspace belongs to the terminal column, so the stripes and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(workspace, BorderLayout.CENTER);
""", 1),
    (r"""        tabs.addChangeListener(event -> {
            if (!rearranging) {
                if (terminals != null) terminals.tabSelected();
                if (commandPalette != null) commandPalette.tabChanged();
                update();
                focusSelected();
            }
        });
        themeRegistration = themes.subscribe(this::applyTheme);
        if (!detached) newTab(directory);
""", r"""        themeRegistration = themes.subscribe(this::applyTheme);
        host = registry.addHost(workspaceHost());
        if (!detached) newTab(directory);
""", 1),
    (r"""        if (hostsContent) {
            contentRegistry = model.tabs();
            contentHost = contentRegistry.addHost(new dev.jasper.app.contributions.WorkspaceHost(id, detached,
                this::mountContent, this::unmountContent, entry -> selectTab(entry.component()), () -> bringToFront.run()));
        }
""", r"""        if (hostsContent) {
            // The window joins the application's registry, taking the tabs it already holds along (workspace plan 3a Rulings 3).
            TabRegistry shared = model.tabs();
            shared.adopt(registry, id);
            host.close();
            registry = shared;
            host = registry.addHost(workspaceHost());
        }
""", 1),
    (r"""    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) instanceof TerminalTab tab) result.add(tab);
        return result;
    }
""", r"""    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (Tab tab : tabs()) if (tab.content() instanceof TerminalTab terminal) result.add(terminal);
        return result;
    }
""", 1),
    (r"""    JTabbedPane tabStrip() { return tabs; }
    /** The equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) windowTabs; }
    TabStrip tabBar() { return windowTabs; }
    private void refreshTabs() { windowTabs.refresh(); }
""", r"""    /** The active group's equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) tabBar(); }
    /** The active group's strip. */
    TabStrip tabBar() { return workspace.activeGroupView().strip(); }
    private void refreshTabs() { workspace.refresh(); }
""", 1),
    ("    public TerminalTab currentTab() { return tabs.getSelectedComponent() instanceof TerminalTab tab ? tab : null; }",
     "    public TerminalTab currentTab() { Tab tab = selectedTab(); return tab != null && tab.content() instanceof TerminalTab terminal ? terminal : null; }", 1),
    (r"""        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull, profile, program);
        if (terminals != null) terminals.tabOpened(tab);
        tab.onChanged = () -> {
            for (TerminalPane pane : tab.panes()) if (pane.view() == null) pane.applyTheme(themes.current());
            update();
        };
        tab.onEmpty = () -> closeTab(tab);
        tab.onError = (message, cause) -> onLaunchFailure.accept(message, cause);
        tab.lastInWindow = () -> tabs.getTabCount() == 1;
        tab.configure = pane -> configurePane(tab, pane);
        tab.onPaneCreated = pane -> connectActivity(tab, pane);
        for (TerminalPane pane : tab.panes()) connectActivity(tab, pane);
        WorkspaceTheme.apply(tab, themes.current());
        tab.setBackground(themes.current().surface());
        tab.setBackgroundOpacity((float) backgroundOpacity);
        for (TerminalPane pane : tab.panes()) pane.applyTheme(themes.current());
        tabs.addTab(tab.title(), tab);
        tabs.setSelectedComponent(tab); update(); tab.start();
        return tab;
""", r"""        if (closed) return null;
        TerminalTab terminal = new TerminalTab(directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(new TabId(terminal.id()), Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        present(tab, terminal);
        if (terminals != null) terminals.tabOpened(terminal);
        terminal.onChanged = () -> {
            for (TerminalPane pane : terminal.panes()) if (pane.view() == null) pane.applyTheme(themes.current());
            present(tab, terminal);
        };
        terminal.onEmpty = () -> closeTab(tab);
        terminal.onError = (message, cause) -> onLaunchFailure.accept(message, cause);
        terminal.lastInWindow = () -> tabCount() == 1;
        terminal.configure = pane -> configurePane(terminal, pane);
        terminal.onPaneCreated = pane -> connectActivity(terminal, pane);
        for (TerminalPane pane : terminal.panes()) connectActivity(terminal, pane);
        terminal.setBackground(themes.current().surface());
        terminal.setBackgroundOpacity((float) backgroundOpacity);
        for (TerminalPane pane : terminal.panes()) pane.applyTheme(themes.current());
        attach(tab);
        terminal.start();
        return terminal;
""", 1),
    (r"""        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component tab = tabs.getComponentAt(i);
            tabs.setTitleAt(i, tabTitle(tab));
            if (tab instanceof TerminalTab terminal) terminal.setActive(active && tab == currentTab());
            else { var entry = hosted.get(tab); if (entry != null) entry.active(tab == selectedComponent()); }
        }
""", r"""        Tab selected = selectedTab();
        for (Tab tab : tabs()) {
            if (tab.content() instanceof TerminalTab terminal) terminal.setActive(active && tab == selected);
            else tab.active(tab == selected);
        }
""", 1),
    ("""        onTitle.accept(selectedComponent() == null ? "Jasper" : tabTitle(selectedComponent()));""",
     """        onTitle.accept(selected == null ? "Jasper" : tabTitle(selected));""", 1),
    (r"""            WorkspaceTheme.apply(windowTabs.component(), theme);
            windowTabs.refresh();
            for (var component : new ArrayList<>(hosted.keySet())) {
                var entry = hosted.get(component);
                if (entry != null) WorkspaceTheme.apply(entry.component(), theme, () -> hosted.get(component) == entry);
            }
""", r"""            workspace.applyTheme(theme, updateDelegates);
            for (Tab tab : new ArrayList<>(tabListeners.keySet()))
                if (tab.kind() == Tab.Kind.HOSTED) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));
""", 1),
    ("            tabs.setBackground(theme.surface());\n", "", 1),
    (r"""        tabs.setOpaque(!glass);
        for (TerminalTab tab : terminalTabs()) tab.setBackgroundOpacity(value);
        windowTabs.refresh();
""", r"""        workspace.setGlass(glass);
        for (TerminalTab tab : terminalTabs()) tab.setBackgroundOpacity(value);
        workspace.refresh();
""", 1),
    ("SwingUtilities.isDescendingFrom(component, tabs) ? backgroundOpacity : 1;",
     "SwingUtilities.isDescendingFrom(component, workspace) ? backgroundOpacity : 1;", 1),
    ("        if (windowTabs != null) windowTabs.setAnimations(settings);\n",
     "        animations = settings;\n        workspace.setAnimations(settings);\n", 1),
    ("        if (windowTabs != null) { windowTabs.component().revalidate(); windowTabs.component().repaint(); }\n",
     "        for (GroupView group : workspace.groupViews()) { group.strip().component().revalidate(); group.strip().component().repaint(); }\n", 1),
    ("active = value; if (value && contentRegistry != null) contentRegistry.activated(id); if (windowTabs != null) windowTabs.setActive(value); update();",
     "active = value; if (value) registry.activated(id); update();", 1),
    (r"""        if (contentHost != null) { contentHost.close(); contentHost = null; }
        for (var component : new ArrayList<>(hosted.keySet())) {
            WorkspaceTheme.clear(hosted.get(component).component());
        }
""", "", 1),
    ("        if (windowTabs != null) windowTabs.close();\n", "", 1),
    (r"""        Runnable closeTabs = () -> {
            for (TerminalTab tab : terminalTabs()) { tab.close(); if (terminals != null) terminals.tabClosed(tab); }
        };
        if (terminals != null) terminals.atomically(closeTabs); else closeTabs.run();
        tabs.removeAll(); removeRootBindings();
""", r"""        // Leaving the registry removes the tabs this window still holds: each closes, a terminal tab announced closed.
        java.util.List<Tab> held = registry.inWindow(id);
        if (terminals != null) terminals.atomically(host::close); else host.close();
        for (Tab tab : held) WorkspaceTheme.clear(tab.component());
        workspace.close(); removeRootBindings();
""", 1),
])
span(W, "    private void mountContent(dev.jasper.app.contributions.Tab entry) {",
     "    public boolean mayClose() { return contentRegistry == null || contentRegistry.mayClose(() -> contentRegistry.inWindow(id)); }\n",
     r"""    /** The registry-facing side of this window's workspace (workspace tabs spec 9.1). */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, detached, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            tab -> workspace.model().groupOf(tab));
    }

    /**
     * Shows a tab in this window, at the end of its active group and selected: one the registry placed here, or one this
     * window opened. A failure leaves nothing mounted and propagates.
     */
    private void attach(Tab tab) {
        JComponent component = tab.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            workspace.open(tab);
        } catch (RuntimeException | Error failure) {
            var listener = tabListeners.remove(tab);
            if (listener != null) listener.close();
            workspace.remove(tab);
            WorkspaceTheme.clear(component);
            throw failure;
        }
        // After the mount stands: a failing glass handler must not roll it back.
        if (backgroundOpacity < 1) glassListener.run();
    }

    /**
     * Takes a tab out of this window before it closes or moves to another window: a terminal tab is announced closed here,
     * and the window closes when its last tab has left. A tab whose attach failed has nothing to take out.
     */
    private void detach(Tab tab) {
        var listener = tabListeners.remove(tab);
        if (listener == null) return;
        listener.close();
        if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabClosed(terminal);
        if (closed) return;
        workspace.remove(tab);
        WorkspaceTheme.clear(tab.component());
        update();
        if (backgroundOpacity < 1) glassListener.run();
        if (workspace.isEmpty()) onEmpty.run();
    }

    /** The workspace's layout changed: the registry hears the selection, the palette its origin; the active tab takes focus when {@code refocus}. */
    void workspaceChanged(boolean refocus) {
        if (closed) return;
        if (terminals != null) terminals.tabSelected();
        if (commandPalette != null) commandPalette.tabChanged();
        update();
        if (refocus) focusSelected();
    }

    /** A terminal tab's presentation: its title and icon. */
    private static void present(Tab tab, TerminalTab terminal) {
        tab.presentation(terminal.title(), "", terminal.icon(), false, "", "");
    }

    /** The selected plugin tab; null while a terminal tab is selected or the window is empty. */
    public Tab selectedContent() { Tab tab = selectedTab(); return tab != null && tab.kind() == Tab.Kind.HOSTED ? tab : null; }
    /** The active group's selected tab; null in an empty workspace. */
    Tab selectedTab() { return workspace.selected(); }
    /** This window's tabs in workspace order. */
    java.util.List<Tab> tabs() { return workspace.tabs(); }
    int tabCount() { return workspace.tabCount(); }
    /** The active group's tab count. */
    int groupTabCount() { return workspace.activeGroupView().tabs().size(); }
    /** The tab showing {@code component}; null when none of this window's does. */
    Tab tab(Component component) {
        for (Tab tab : tabs()) if (tab.component() == component) return tab;
        return null;
    }
    WorkspaceView workspace() { return workspace; }
    TabRegistry tabRegistry() { return registry; }
    boolean windowActive() { return active; }
    dev.jasper.app.config.AnimationSettings animations() { return animations; }
    String tabTitle(Tab tab) {
        return tab.kind() == Tab.Kind.TERMINAL ? tab.title()
            : (tab.dirty() ? "● " : "") + tab.title() + (tab.detail().isEmpty() ? "" : "  " + tab.detail());
    }
    Icon tabIcon(Tab tab) { return tab.icon(); }
    String tabTooltip(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL ? tabTitle(tab) : tab.location(); }
    /** Whether a terminal tab's shell runs, which its strip entry tells assistive technology. */
    boolean running(Tab tab) {
        return tab.content() instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running();
    }
    JPopupMenu tabMenu(Tab tab) {
        var menu = new JPopupMenu();
        if (tab.kind() == Tab.Kind.HOSTED) {
            var move = new JMenuItem(tab.detached() ? "Move to Main Window" : "Open in Separate Window");
            move.addActionListener(event -> tab.move(!tab.detached())); menu.add(move);
            for (var command : tab.commands()) { var item = new JMenuItem(command.title()); item.putClientProperty("html.disable", true);
                item.setEnabled(command.enabled()); item.setAccelerator(command.shortcut()); item.addActionListener(event -> command.run().run()); menu.add(item); }
        }
        var close = new JMenuItem("Close Tab"); close.setAccelerator(bindings().strokeFor(ActionId.CLOSE_TAB).orElse(null)); close.addActionListener(event -> closeTab(tab)); menu.add(close); dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }
    public void focusSelected() { Tab tab = selectedTab(); if (tab != null) tab.focus(); }
    public boolean mayClose() { return registry.mayClose(() -> registry.inWindow(id)); }
""")
span(W, "    void selectTab(Component tab) {",
     "        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();\n    }\n",
     r"""    /** Shows the tab holding {@code component}, as a click on its strip entry does. */
    void selectTab(Component component) { selectTab(tab(component)); }

    /** Shows {@code tab} in its group, activates the group and focuses the tab. */
    void selectTab(Tab tab) {
        if (closed || !workspace.contains(tab)) return;
        workspace.select(tab);
        focusSelected();
    }

    /** Selects the active group's tab at {@code index}; nothing past its last tab. */
    void selectInGroup(int index) {
        var group = workspace.activeGroupView().tabs();
        if (index >= 0 && index < group.size()) selectTab(group.get(index));
    }

    /** Selects the active group's tab {@code delta} places from the selected one, wrapping around. */
    void selectRelative(int delta) {
        var view = workspace.activeGroupView();
        var group = view.tabs();
        if (group.isEmpty()) return;
        selectTab(group.get(Math.floorMod(Math.max(0, group.indexOf(view.selected())) + delta, group.size())));
    }

    /** Closes the tab holding {@code component}. */
    public void closeTab(Component component) { closeTab(tab(component)); }

    /**
     * Closes {@code tab}: a plugin's tab once its guard allows it; a terminal tab at once, its registry facts published as
     * one change (workspace plan 3a Rulings 4).
     */
    public void closeTab(Tab tab) {
        if (!workspace.contains(tab)) return;
        if (tab.kind() == Tab.Kind.HOSTED) { tab.requestClose(); return; }
        if (terminals != null) terminals.atomically(tab::close); else tab.close();
    }
""")
PY
```

`WorkspaceActions` selects within the active group; the palette listens to the workspace:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(A + "WorkspaceActions.java", [
    ("            case NEXT_TAB -> selectRelative(1);\n            case PREVIOUS_TAB -> selectRelative(-1);\n",
     "            case NEXT_TAB -> owner.selectRelative(1);\n            case PREVIOUS_TAB -> owner.selectRelative(-1);\n", 1),
    ("""                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> {
                int index = id.ordinal() - ActionId.SELECT_TAB_1.ordinal();
                if (index < owner.tabStrip().getTabCount()) owner.tabStrip().setSelectedIndex(index);
            }
""", """                 SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 ->
                owner.selectInGroup(id.ordinal() - ActionId.SELECT_TAB_1.ordinal());
""", 1),
    ("""    private void selectRelative(int delta) {
        if (owner.tabStrip().getTabCount() > 0) owner.tabStrip().setSelectedIndex(Math.floorMod(owner.tabStrip().getSelectedIndex() + delta, owner.tabStrip().getTabCount()));
    }

""", "", 1),
    ("                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.tabStrip().getTabCount();",
     "                        id.ordinal() - ActionId.SELECT_TAB_1.ordinal() < owner.groupTabCount();", 1)])
edit(A + "WindowCommandPalette.java", [("owner.tabStrip()", "owner.workspace()", 2)])
PY
```

- [ ] **Step 6: Move the existing tests to the workspace's API**

`tabStrip()` (the deck) is gone: a tab count is `tabCount()`, a tab is `tabs().get(i)` (a `Tab`), the selected tab
`selectedTab()`, the deck's bounds the workspace's (`workspace()`), and a single tab keeps its strip (spec 1, decision 4).

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, regex, span
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
COUNTS = {"ApplicationActionsTest": 1, "ClassicTabsTest": 9, "CommandPaletteShortcutsTest": 1, "ConfigurationControllerTest": 3,
          "MockUiTest": 1, "PaletteKeyRouterTest": 1, "PaletteScopesTest": 1, "PluginPaneAppearanceTest": 2,
          "RunWindowModelessTest": 2, "ShellExitBehaviorTest": 13, "ShortcutContextDispatchTest": 6, "TitleBarPreview": 2,
          "WindowContributionsTest": 2, "WindowTabsTest": 1}
for name, count in COUNTS.items():
    regex(T + name + ".java", r"\.tabStrip\(\)\.getTabCount\(\)", ".tabCount()", count)
edit(T + "ClassicTabsTest.java", [
    ("owner.selectTab(owner.tabStrip().getComponentAt(strip.entries().indexOf(under)));",
     "owner.selectTab(owner.tabs().get(strip.entries().indexOf(under)));", 1),
    ('assertThat(owner.selectedComponent()).as("nor selects its tab").isSameAs(owner.tabStrip().getComponentAt(1));',
     'assertThat(owner.selectedTab()).as("nor selects its tab").isSameAs(owner.tabs().get(1));', 1),
    ('assertThat(owner.selectedComponent()).as("the first action selects the tab").isSameAs(owner.tabStrip().getComponentAt(0));',
     'assertThat(owner.selectedTab()).as("the first action selects the tab").isSameAs(owner.tabs().get(0));', 1),
    ('assertThat(owner.tabBar().component().getHeight()).as("unlike the classic tabs, the equal tabs hide at one tab").isZero();',
     'assertThat(owner.tabBar().component().getHeight()).as("like the classic tabs, the equal tabs show at one tab").isEqualTo(owner.tabHeight());', 1)])
edit(T + "ConfigurationControllerTest.java", [
    ("import dev.jasper.app.workspace.TerminalTab;\n", "", 1),
    ("owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));", "owner.selectTab(owner.tabs().get(0));", 1)])
edit(T + "TitleBarPreview.java", [
    ("owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));", "owner.selectTab(owner.tabs().get(0));", 1),
    ('assertThat(owner.windowTabs().isVisible()).isEqualTo(!name.equals("single"));',
     'assertThat(owner.windowTabs().isVisible()).as("every group shows its strip").isTrue();', 1),
    ("        if (!tabs.isVisible()) return; // One tab intentionally has no strip in the current workspace.\n", "", 1)])
edit(T + "MockUiPreview.java", [("owner[0].tabStrip().setSelectedIndex(0);", "owner[0].selectTab(owner[0].tabs().get(0));", 1)])
edit(T + "TabShortcutsTest.java", [
    ("owner.tabStrip().getSelectedIndex()", "owner.tabs().indexOf(owner.selectedTab())", 4),
    ("var menu = owner.tabMenu(owner.currentTab());", "var menu = owner.tabMenu(owner.selectedTab());", 1)])
edit(T + "TerminalTitleIntegrationTest.java", [("f.content.tabStrip().getTitleAt(0)", "f.content.tabs().get(0).title()", 8)])
edit(T + "WorkspaceGlassTest.java", [
    ("var terminalTab = owner.tabStrip().getComponentAt(0);", "var terminalTab = owner.tabs().get(0).component();", 1),
    ('assertThat(owner.tabStrip().indexOfComponent(component)).as("the registry rolled the mount back").isEqualTo(-1);',
     'assertThat(owner.tabs().stream().map(tab -> tab.component()).toList()).as("the registry rolled the mount back").doesNotContain(component);', 1)])
edit(T + "WindowTabsTest.java", [
    ("assertThat(owner.tabStrip().getComponentAt(1)).isSameAs(first);", "assertThat(owner.tabs().get(1).component()).isSameAs(first);", 1),
    ("            owner.reorderTab(1, 0);\n", "            owner.workspace().activeGroupView().reorder(owner.tabs().get(1), 0);\n", 1),
    ("            close.doClick(); assertThat(owner.terminalTabs()).doesNotContain(first);\n            assertThat(strip.isVisible()).isFalse();\n",
     "            close.doClick(); assertThat(owner.terminalTabs()).doesNotContain(first);\n            assertThat(strip.isVisible()).as(\"one tab keeps its strip\").isTrue();\n", 1)])
span(T + "WindowTabsTest.java", "    @Test void oneTabHidesTheBarAndTwoTabsRestoreIt() throws Exception {",
     '            assertThat(named(strip, "tabList")).isNull();\n        });\n    }\n',
     '''    @Test void oneTabStillShowsTheStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var strip = owner.windowTabs();
            assertThat(strip.isVisible()).as("workspace tabs spec 1, decision 4").isTrue();
            assertThat(strip.getPreferredSize().height).isEqualTo(owner.tabHeight());
            owner.invoke(ActionId.NEW_TAB);
            assertThat(strip.isVisible()).isTrue();
            owner.closeTab(owner.currentTab());
            assertThat(strip.isVisible()).isTrue();
            assertThat(strip.getMinimumSize().height).isEqualTo(owner.tabHeight());
            assertThat(named(strip, "newTab")).isNull();
            assertThat(named(strip, "tabList")).isNull();
        });
    }
''')
edit(T + "TabHeightTest.java", [("            assertThat(owner.windowTabs().isVisible()).isFalse();\n",
                                 "            assertThat(owner.windowTabs().isVisible()).as(\"one tab keeps its strip\").isTrue();\n", 1)])
edit(T + "MockUiTest.java", [
    ("                assertThat(tabs).isZero();\n                assertThat(owner.windowTabs().isVisible()).isFalse();\n",
     "                assertThat(tabs).as(\"a single tab keeps its strip\").isEqualTo(owner.tabHeight());\n"
     "                assertThat(owner.windowTabs().isVisible()).isTrue();\n", 1),
    ('                assertThat(new Color(image.getRGB(650, toolbar))).as("single terminal begins immediately below the header").isEqualTo(owner.theme().palette().background());\n',
     '                assertThat(javax.swing.SwingUtilities.convertPoint(owner.windowTabs(), 0, 0, root).y).as("the strip begins immediately below the header").isEqualTo(toolbar);\n'
     '                assertThat(new Color(image.getRGB(650, toolbar + tabs))).as("the terminal begins under the strip").isEqualTo(owner.theme().palette().background());\n', 1)])
edit(T + "ChromeRenderTest.java", [(
    'assertThat(owner.tabBar().component().isVisible() || workspace.tabs() == WorkspaceLook.Tabs.EQUAL).as(look.name() + ": classic tabs always show").isTrue();',
    'assertThat(owner.tabBar().component().isVisible()).as(look.name() + ": the strip always shows").isTrue();', 1)])
edit(T + "GraphiteChromeTest.java", [
    ("var column = owner.windowTabs().getParent();", "var column = owner.workspace().getParent();", 1),
    (".isEqualTo(y == 0 && x > 0 ? owner.windowTabs().getBackground().getRGB() : terminal);",
     ".isEqualTo(y == 0 ? stripTop(owner, x == 0 ? 0 : owner.windowTabs().getWidth() - 1) : terminal);", 1),
    ("var host = (javax.swing.JComponent) owner.windowTabs().getParent().getParent();",
     "var host = (javax.swing.JComponent) owner.workspace().getParent().getParent();", 1),
    ("var host = (javax.swing.JComponent) strip.getParent().getParent();",
     "var host = (javax.swing.JComponent) owner.workspace().getParent().getParent();", 1),
    ("    /** A window on each stripe, so both show: an empty stripe stays hidden. */\n",
     """    /** The strip's top row at {@code x}: the workspace's top edge, now that a single tab keeps its strip. */
    private static int stripTop(WindowContent owner, int x) {
        var strip = owner.windowTabs();
        var image = new java.awt.image.BufferedImage(strip.getWidth(), strip.getHeight(), 2);
        var g = image.createGraphics();
        try { strip.paint(g); } finally { g.dispose(); }
        return image.getRGB(x, 0);
    }

    /** A window on each stripe, so both show: an empty stripe stays hidden. */
""", 1)])
for name, count in {"BarHeightTest": 1, "GraphiteChromeTest": 1, "RailWidthTest": 1, "WindowCommandPaletteTest": 2,
                     "WindowDragTest": 4, "WindowPanelsTest": 4, "ZenModeTest": 4}.items():
    regex(T + name + ".java", r"\.tabStrip\(\)", ".workspace()", count)
edit(T + "WorkspaceMetricsTest.java", [('"WindowTabs.java", List.of("TAB_REVEAL_INSET", "TAB_CLOSE_FIT_WIDTH",',
                                        '"WindowTabs.java", List.of("TAB_CLOSE_FIT_WIDTH",', 1),
    ("Map.of(DesignMetrics.Role.TAB_REVEAL_INSET, 100, DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH, 80,",
     "Map.of(DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH, 80,", 1)])
edit(T + "HostedContentTest.java", [("owner.reorderTab(1,0);", "owner.workspace().activeGroupView().reorder(owner.tabs().get(1), 0);", 1)])
# The reveal's inset has no reader left: the role and its frozen catalog rows go (Rulings 7).
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ("        TAB_REVEAL_INSET(100), TAB_CLOSE_FIT_WIDTH(80),", "        TAB_CLOSE_FIT_WIDTH(80),", 1)])
for catalog in ("classic-roles.tsv", "compact-roles.tsv"):
    edit("jasper-ui/src/test/resources/dev/jasper/ui/metrics/" + catalog, [("TAB_REVEAL_INSET\t100\n", "", 1)])
edit(T + "WindowTabMotionPreview.java", [("new WindowTabs(owner, clock::get)", "new WindowTabs(owner.workspace().activeGroupView(), clock::get)", 1)])
edit(T + "WindowTabMotionTest.java", [
    ("new WindowTabs(owner, clock::get)", "new WindowTabs(owner.workspace().activeGroupView(), clock::get)", 2),
    ("            assertThat(f.strip.isVisible()).isFalse();\n            assertThat(f.strip.getPreferredSize().height).isZero();\n",
     "            assertThat(f.strip.isVisible()).as(\"one tab keeps its strip\").isTrue();\n"
     "            assertThat(f.strip.getPreferredSize().height).isEqualTo(30);\n", 1)])
span(T + "WindowTabMotionTest.java", "    @Test void firstRevealUsesFullHeightAndExpandsBothTitlesFromTheRecordedInset() throws Exception {",
     "            assertThat(f.strip.motionRunning()).isFalse();\n        });\n    }\n",
     '''    @Test void aSecondTabOpensAtTheRecordedSpeed() throws Exception {
        edt(() -> {
            var f = fixture();
            assertThat(f.entry("first").getBounds()).as("one tab fills its strip").isEqualTo(new Rectangle(0, 0, 800, 30));
            f.add("second");
            assertThat(f.entry("first").getX()).isZero();
            f.advance(75);
            // The open curve at 75 ms of 475: the first tab from 800 px towards 400, the second from 0.
            assertThat(f.entry("first").getWidth()).isBetween(618, 626);
            assertThat(f.entry("second").getX()).isEqualTo(f.entry("first").getWidth());
            f.advance(400);
            assertThat(f.entry("first").getBounds()).isEqualTo(new Rectangle(0, 0, 400, 30));
            assertThat(f.entry("second").getBounds()).isEqualTo(new Rectangle(400, 0, 400, 30));
            assertThat(f.strip.motionRunning()).isFalse();
        });
    }
''')
edit(T + "WindowTabMotionTest.java", [("    private Fixture fixture() {\n", '''    /** Spec 4: a tab that joins or leaves a group by a move, or is reordered, takes its place at once (Rulings 27). */
    @Test void movesAndReordersTakeTheirPlaceAtOnce() throws Exception {
        edt(() -> {
            var f = fixture(); f.add("second"); f.add("third"); f.advance(1000);
            var workspace = f.owner.workspace();
            var group = workspace.model().activeGroup();
            var third = f.owner.selectedTab();
            workspace.moveToNewGroup(third, group, dev.jasper.app.layout.Side.RIGHT); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a tab leaving by a move").isFalse();
            assertThat(f.entry("first").getBounds()).isEqualTo(new Rectangle(0, 0, 400, 30));
            workspace.moveTab(third, group, 1); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a tab joining by a move").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(267, 0, 266, 30));
            workspace.reorder(third, group, 0); f.strip.refresh();
            assertThat(f.strip.motionRunning()).as("a reorder").isFalse();
            assertThat(f.entry("third").getBounds()).isEqualTo(new Rectangle(0, 0, 267, 30));
            f.add("fourth");
            assertThat(f.strip.motionRunning()).as("an open still moves").isTrue();
        });
    }

    private Fixture fixture() {
''', 1)])
span(T + "WindowTabMotionTest.java", "    @Test void firstRevealAnimatesAfterRealHiddenParentLayout() throws Exception {",
     "            assertThat(owner.windowTabs().motionRunning()).isFalse();\n        });\n    }\n",
     '''    @Test void aSecondTabAnimatesInTheMountedStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setSize(800, 500); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().isVisible()).isTrue();
            owner.newTab(HOME); WindowTabsTest.layoutTree(owner);
            assertThat(owner.windowTabs().motionRunning()).isTrue();
            owner.setAnimations(new AnimationSettings(false, 475, 425, 0));
            assertThat(owner.windowTabs().motionRunning()).isFalse();
        });
    }
''')
PY
grep -rn 'tabStrip()' jasper-app/src && exit 1 || true
```

The last line prints nothing: no `tabStrip()` is left. If another test fails because the one-tab window now shows its
strip (a measured height, a pixel row, a terminal's origin), re-derive its expectation from the strip's own geometry
(`owner.windowTabs()` bounds, `owner.tabHeight()`), never weaken it, and record the test in this plan's banner.

- [ ] **Step 7: Ledger, run, commit**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.drop(M + "TerminalDeck.java", T + "WorkspaceViewTest.java")
ledger.add(M + "WorkspaceView.java", "app.workspace", "WorkspaceView", T + "WorkspaceViewTest.java",
           "New in workspace-tabs plan 3a Task 3; a window's layout as groups in splits")
ledger.add(M + "GroupView.java", "app.workspace", "GroupView", T + "WorkspaceViewTest.java",
           "New in workspace-tabs plan 3a Task 3; a tab group's strip over its selected tab")
ledger.add(T + "WorkspaceViewTest.java", "app.workspace", "WorkspaceViewTest", T + "WorkspaceViewTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 3a Task 3; groups, splits, strips and maximize")
PY
```

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WorkspaceViewTest` 8, `TabRegistryTest` 16, `WindowTabMotionTest` with
`movesAndReordersTakeTheirPlaceAtOnce`; `DesignMetricsTest` and `ClassicCatalogTest` without `TAB_REVEAL_INSET`). Then
`./gradlew :jasper-ui:check :jasper-app:check`: PASS.

```bash
git add -A jasper-app/src jasper-ui/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Workspace view: tab groups in splits, a strip per group, every tab registered

WindowContent renders its Layout through WorkspaceView: SplitContainers over
GroupViews, each with the look's strip, always shown; a tab that moves or
is reordered takes its place without motion. Terminal tabs are registry
tabs; a window joins the application's registry with its tabs.
TerminalDeck, the strip's reveal and TAB_REVEAL_INSET are gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Tab hosts and moves between windows

Spec 9.3: a `TabHost` per window wires its terminal tabs to that window; moving a tab detaches it from one host and
attaches it to the next, its session untouched, and ends a maximize on both sides (spec 15); "go to" callbacks select
the tab through the registry when they run (Rulings 17, 18). `WindowContent.connectActivity` and `configurePane` move
into the host.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java`
- Modify: `contributions/TabRegistry.java` (`move(Tab, UUID)`), `workspace/WindowContent.java` (Step 3: the host, and
  a move's end of the maximize in `attach` and `detach`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabMoveTest.java`; `TabRegistryTest` (one case)
- Ledger: two rows

**Interfaces:**
- Consumes: Task 1's `Tab.moving()`; Task 3's `WindowContent.attach`/`detach`, `selectedTab()`, `tabCount()`,
  `closeTab(Tab)`, `selectTab(Tab)`, `WorkspaceView.unmaximize()`, `moved(Tab)`, `toggleMaximize()`, `moveToNewGroup`;
  `TerminalTab`'s hooks (`onChanged`, `onEmpty`, `onError`, `lastInWindow`, `configure`, `onPaneCreated`, `focus(pane)`).
- Produces: `TabRegistry.move(Tab tab, UUID window)` (any kind; detach, attach to the target's active group, select;
  a failed attach goes back as `move(Tab, boolean)` does); `final class TabHost` with `TabHost(WindowContent owner)`,
  `void attach(Tab tab, boolean opened)`, `void detach(Tab tab)`; on `WindowContent`: `void emit(WorkspaceActivity.Event)`,
  `void report(TerminalEvent)`, `ConfigSnapshot configured()`, `void terminalConfigured(TerminalPane)`; a window that
  detaches or attaches a tab while `Tab.moving()` ends its maximize (`WorkspaceView.unmaximize()`).

- [ ] **Step 1: Write the failing tests**

Add to `TabRegistryTest`, before the class's closing brace:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java", [(
"""        tab.close();
        assertThat(closed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }
}
""", """        tab.close();
        assertThat(closed[0]).isEqualTo(1);
        assertThat(registry.tabs()).isEmpty();
    }

    @Test void anyTabMovesToANamedWindowAndIsSelectedThere() {
        hosts();
        var other = UUID.randomUUID();
        var arrived = new ArrayList<Tab>();
        var selected = new ArrayList<Tab>();
        registry.addHost(new WorkspaceHost(other, false, arrived::add, arrived::remove, selected::add, () -> {}));
        var tab = registry.add(TabId.random(), Tab.Kind.TERMINAL, new HostedContent(new JPanel()), main);
        mounted.add(tab);
        registry.move(tab, other);
        assertThat(tab.window()).isEqualTo(other);
        assertThat(mounted).as("detached from the old window").isEmpty();
        assertThat(arrived).containsExactly(tab);
        assertThat(selected).containsExactly(tab);
        registry.move(tab, other);
        assertThat(arrived).as("a move to its own window does nothing").containsExactly(tab);
        assertThatIllegalArgumentException().isThrownBy(() -> registry.move(tab, UUID.randomUUID()));
        var pin = tab.pin();
        registry.move(tab, main);
        assertThat(tab.window()).as("a pinned tab stays").isEqualTo(other);
        pin.close();
    }
}
""", 1)])
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabMoveTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 9.3 and 17: a tab moved between windows through the registry keeps its session. */
class TabMoveTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private WindowContent source, target;

    @BeforeEach void windows() throws Exception {
        edt(() -> {
            source = content(idleLauncher(pending));
            target = content(idleLauncher(pending));
            source.connectContributions(model);
            target.connectContributions(model);
            source.newTab(HOME);
        });
        while (!pending.isEmpty()) pending.remove().run();
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void aTerminalTabMovedBetweenWindowsKeepsItsSession() throws Exception {
        edt(() -> {
            Tab tab = source.selectedTab();
            var terminal = (TerminalTab) tab.content();
            var pane = terminal.focusedPane();
            var session = pane.session(); var view = pane.view();
            assertThat(session).as("its shell started").isNotNull();
            model.tabs().move(tab, target.id());
            assertThat(tab.window()).isEqualTo(target.id());
            assertThat(source.tabs()).doesNotContain(tab);
            assertThat(source.tabCount()).as("the source keeps its other tab").isEqualTo(1);
            assertThat(target.tabs()).contains(tab);
            assertThat(target.selectedTab()).as("it arrives selected in the active group").isSameAs(tab);
            assertThat(SwingUtilities.isDescendingFrom(terminal, target.workspace())).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(terminal, source)).isFalse();
            assertThat(pane.session()).as("the same session").isSameAs(session);
            assertThat(pane.view()).isSameAs(view);
            assertThat(session.exitFuture()).as("still running").isNotDone();
            assertThat(tab.closed()).isFalse();
        });
    }

    @Test void aMovedTabReportsToItsNewWindow() throws Exception {
        edt(() -> {
            var registry = new TerminalRegistry();
            var facts = new ArrayList<TerminalEvent>();
            registry.onEvent(facts::add);
            source.connectTerminals(registry, () -> { });
            target.connectTerminals(registry, () -> { });
            var before = new ArrayList<WorkspaceActivity.Event>(); var after = new ArrayList<WorkspaceActivity.Event>();
            source.activity(before::add); target.activity(after::add);
            before.clear(); after.clear(); facts.clear();
            Tab tab = source.selectedTab();
            var pane = ((TerminalTab) tab.content()).focusedPane();
            model.tabs().move(tab, target.id());
            assertThat(after).as("a moved pane is not opened again").isEmpty();
            assertThat(facts).contains(new TerminalEvent.TabClosed(source.id(), tab.id().value()),
                new TerminalEvent.TabOpened(target.id(), tab.id().value()));
            assertThat(facts).noneMatch(fact -> fact instanceof TerminalEvent.PaneOpened || fact instanceof TerminalEvent.PaneClosed);
            assertThat(registry.tab(tab.id().value()).orElseThrow().windowId()).isEqualTo(target.id());
            pane.onTitleChanged.accept("moved");
            assertThat(after).containsExactly(new WorkspaceActivity.TitleChanged(pane.id(), "moved"));
            assertThat(before).isEmpty();
            tab.close();
            assertThat(after).contains(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            assertThat(facts).contains(new TerminalEvent.PaneClosed(tab.id().value(), pane.id()),
                new TerminalEvent.TabClosed(target.id(), tab.id().value()));
        });
    }

    @Test void goToFollowsAMovedTab() throws Exception {
        edt(() -> {
            int[] raised = new int[2];
            var registry = new TerminalRegistry();
            source.connectTerminals(registry, () -> raised[0]++);
            target.connectTerminals(registry, () -> raised[1]++);
            var finished = new ArrayList<WorkspaceActivity.Finished>();
            source.activity(event -> { if (event instanceof WorkspaceActivity.Finished done) finished.add(done); });
            Tab tab = source.selectedTab();
            var pane = ((TerminalTab) tab.content()).focusedPane();
            pane.onCommandFinished.accept("make", OptionalInt.of(0), Duration.ofSeconds(3), Optional.empty(), Optional.empty());
            assertThat(finished).hasSize(1);
            model.tabs().move(tab, target.id());
            target.selectTab(target.tabs().get(0));
            assertThat(target.selectedTab()).isNotSameAs(tab);
            raised[0] = 0; raised[1] = 0;
            finished.getFirst().activate().run();
            assertThat(target.selectedTab()).as("the tab is selected where it is now").isSameAs(tab);
            assertThat(raised[1]).as("its window comes to the front").isEqualTo(1);
            assertThat(raised[0]).as("the old window is left alone").isZero();
            assertThat(source.tabs()).doesNotContain(tab);
        });
    }

    @Test void aHostedTabMovesTooAndItsGuardStillAnswers() throws Exception {
        edt(() -> {
            var panel = new javax.swing.JPanel();
            Tab tab = model.tabs().open("dev.example.editor", "notes", "notes.txt", source.id(), panel);
            assertThat(source.selectedContent()).isSameAs(tab);
            model.tabs().move(tab, target.id());
            assertThat(target.selectedContent()).isSameAs(tab);
            assertThat(tab.component()).isSameAs(panel);
            int[] asked = {0};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> { asked[0]++; return java.util.concurrent.CompletableFuture.completedFuture(false); });
            target.closeTab(tab);
            assertThat(asked[0]).isEqualTo(1);
            assertThat(target.tabs()).contains(tab);
            assertThat(target.mayClose()).as("the guard still answers for the window it is in").isFalse();
        });
    }

    @Test void aMoveEndsTheMaximizeOnBothSidesAndClosingATabKeepsIt() throws Exception {
        edt(() -> { source.newTab(HOME); source.newTab(HOME); target.newTab(HOME); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var from = source.workspace(); var to = target.workspace();
            var moving = source.tabs();
            from.moveToNewGroup(moving.get(3), from.model().activeGroup(), Side.RIGHT);
            source.selectTab(moving.get(0));
            from.toggleMaximize();
            var maximized = from.model().maximized();
            assertThat(maximized).contains(from.model().groupOf(moving.get(0).id()).orElseThrow());
            source.closeTab(moving.get(0));
            assertThat(from.model().maximized()).as("closing one of its tabs keeps the maximize").isEqualTo(maximized);
            var staying = target.tabs();
            to.moveToNewGroup(staying.get(1), to.model().activeGroup(), Side.RIGHT);
            to.toggleMaximize();
            assertThat(to.model().maximized()).isPresent();
            model.tabs().move(moving.get(1), target.id());
            assertThat(from.model().maximized()).as("its group lost a tab by a move").isEmpty();
            assertThat(to.model().maximized()).as("the active group gained one").isEmpty();
            assertThat(target.selectedTab()).isSameAs(moving.get(1));
        });
    }

    @Test void aTabThatArrivesByAMoveIsShownWithoutTheOpenMotion() throws Exception {
        edt(() -> {
            Tab tab = source.selectedTab();
            assertThat(source.workspace().moved(tab)).as("it opened here").isFalse();
            model.tabs().move(tab, target.id());
            assertThat(target.workspace().moved(tab)).as("it arrived by a move (Rulings 27)").isTrue();
            assertThat(source.workspace().moved(tab)).isFalse();
        });
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol: method move(Tab,UUID)`.

- [ ] **Step 3: Move tabs between windows through the registry and wire them through a host**

Insert into `TabRegistry.java`, immediately before `    /**\n     * Detaches the tab from {@code old} and attaches it to {@code next}`:

```java
    /**
     * Moves a tab of any kind to {@code window}, at the end of that window's active group, and selects it there; its
     * content and session are untouched. A pinned tab, one being closed or one already there stays.
     *
     * @param tab the tab
     * @param window the window to move it to
     * @throws IllegalArgumentException when no workspace {@code window} is registered
     */
    public void move(Tab tab, UUID window) {
        requireEdt();
        var next = hosts.get(Objects.requireNonNull(window, "window"));
        if (next == null) throw new IllegalArgumentException("No workspace " + window);
        if (tab.closed() || tab.pinned() || pending.contains(tab) || window.equals(tab.window())) return;
        var old = hosts.get(tab.window());
        if (old == null) return;
        transfer(tab, old, next);
    }

```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.terminal.view.FileLink;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.KeyStroke;

/**
 * One window's side of the terminal tabs it shows (workspace tabs spec 9.3): the shortcut, context-menu and file-link
 * handlers a pane's view gets, the window its activity and terminal-registry facts report from, the window's theme and
 * opacity, and focus. A tab moving to another window is detached from this host and attached to the next window's; its
 * session is never touched (spec 2.3). A "go to" callback selects its tab through the registry when it runs, so it
 * follows the tab to whichever window holds it then. EDT only.
 */
final class TabHost {
    private final WindowContent owner;

    TabHost(WindowContent owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

    /**
     * Wires a terminal tab to this window; a tab of another kind needs nothing. With {@code opened} its panes are
     * announced as new first; a tab arriving from another window keeps its panes unannounced.
     */
    void attach(Tab tab, boolean opened) {
        if (!(tab.content() instanceof TerminalTab terminal)) return;
        terminal.onChanged = () -> {
            for (TerminalPane pane : terminal.panes()) if (pane.view() == null) pane.applyTheme(owner.theme());
            present(tab, terminal);
        };
        terminal.onEmpty = () -> owner.closeTab(tab);
        terminal.onError = (message, cause) -> owner.onLaunchFailure.accept(message, cause);
        terminal.lastInWindow = () -> owner.tabCount() == 1;
        terminal.configure = pane -> configure(tab, terminal, pane);
        terminal.onPaneCreated = pane -> { announce(tab, pane); wire(tab, terminal, pane); };
        terminal.setBackground(owner.theme().surface());
        terminal.setBackgroundOpacity((float) owner.backgroundOpacity());
        for (TerminalPane pane : terminal.panes()) {
            pane.applyTheme(owner.theme());
            if (opened) announce(tab, pane);
            wire(tab, terminal, pane);
            if (pane.view() != null) { bind(tab, terminal, pane); behave(pane); }
        }
        present(tab, terminal);
    }

    /** Unwires a terminal tab from this window before it closes or moves on. */
    void detach(Tab tab) {
        if (!(tab.content() instanceof TerminalTab terminal)) return;
        terminal.onChanged = () -> { };
        terminal.onEmpty = () -> { };
        terminal.onError = (message, cause) -> { };
        terminal.lastInWindow = () -> false;
        terminal.configure = pane -> { };
        terminal.onPaneCreated = pane -> { };
        for (TerminalPane pane : terminal.panes()) unwire(pane);
    }

    /** A terminal tab's presentation: its title and icon. */
    static void present(Tab tab, TerminalTab terminal) {
        tab.presentation(terminal.title(), "", terminal.icon(), false, "", "");
    }

    /** A pane that has just opened: this window's activity and the terminal registry hear of it. */
    private void announce(Tab tab, TerminalPane pane) {
        owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        owner.report(new TerminalEvent.PaneOpened(tab.id().value(), pane.id()));
    }

    /** The pane reports its activity and facts to this window from now on. */
    private void wire(Tab tab, TerminalTab terminal, TerminalPane pane) {
        UUID tabId = tab.id().value();
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            owner.emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> goTo(tab, terminal, pane), pane.watched() && owner.isActiveAndOpen() && tab == owner.selectedTab()));
            owner.report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            owner.emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            owner.report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            owner.report(new TerminalEvent.PaneClosed(tabId, pane.id()));
        };
        pane.onPaneFocused = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            owner.report(new TerminalEvent.PaneFocused(tabId, pane.id()));
        };
        pane.onPaneBlurred = () -> owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {
            owner.emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(owner.anyWindowActive.getAsBoolean(), owner.isActiveAndOpen(),
                    tab == owner.selectedTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> goTo(tab, terminal, pane)));
            owner.report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote));
        };
        pane.onDirectoryChanged = (directory, remote) -> owner.report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
        pane.onBell = () -> owner.report(new TerminalEvent.Bell(tabId, pane.id()));
        pane.onStarted = () -> owner.report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> owner.report(new TerminalEvent.SessionExited(pane.id(), status));
        pane.onConnecting = () -> owner.report(new TerminalEvent.SessionConnecting(pane.id()));
    }

    /** "Go to": the tab, wherever it is now, then the pane (workspace plan 3a Rulings 18). */
    private static void goTo(Tab tab, TerminalTab terminal, TerminalPane pane) {
        tab.select();
        terminal.focus(pane);
        pane.focusTerminal();
    }

    /** A pane whose view just became ready: this window's configuration, its handlers and the first-terminal timing. */
    private void configure(Tab tab, TerminalTab terminal, TerminalPane pane) {
        pane.applyTheme(owner.theme());
        ConfigSnapshot configured = owner.configured();
        if (configured == null) pane.view().setFontSize(owner.configuredFontSize());
        else pane.view().applyOptions(configured.viewOptions(owner.configuredFontSize(), owner.theme().palette()));
        behave(pane);
        bind(tab, terminal, pane);
        owner.terminalConfigured(pane);
    }

    /** This window's dimming and shell-exit policy; a moved pane keeps its view's own options. */
    private void behave(TerminalPane pane) {
        ConfigSnapshot configured = owner.configured();
        if (configured == null) return;
        pane.setConfiguredDim(configured.terminal().dimInactivePanes());
        pane.setShellExitBehavior(configured.terminal().onExit());
    }

    /** The view's shortcut, context-menu and file-link handlers, and its launch focus, go to this window. */
    private void bind(Tab tab, TerminalTab terminal, TerminalPane pane) {
        pane.allowLaunchFocus = () -> owner.commandPalette() == null || !owner.commandPalette().blocksActions();
        pane.view().setShortcutHandler(event -> owner.dispatchShortcut(KeyStroke.getKeyStrokeForEvent(event), pane.view()));
        pane.view().setContextMenuHandler(event -> {
            owner.selectTab(tab); terminal.focus(pane); pane.focusTerminal(); owner.update();
            owner.chrome().contextMenu().show(pane.view(), event.getX(), event.getY());
        });
        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
    }

    private static void unwire(TerminalPane pane) {
        pane.onCommandStarted = command -> { };
        pane.onTitleChanged = title -> { };
        pane.onClosed = () -> { };
        pane.onPaneFocused = () -> { };
        pane.onPaneBlurred = () -> { };
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> { };
        pane.onDirectoryChanged = (directory, remote) -> { };
        pane.onBell = () -> { };
        pane.onStarted = () -> { };
        pane.onExited = status -> { };
        pane.onConnecting = () -> { };
        pane.allowLaunchFocus = () -> true;
        if (pane.view() != null) {
            pane.view().setShortcutHandler(null); pane.view().setContextMenuHandler(null); pane.view().setFileLinkHandler(null);
        }
    }

    /**
     * A Cmd-click on a file in {@code pane}: resolved against the pane's local directory and given to the window's
     * {@code onOpenFile}. A provided session, or a shell that reports a directory on another host, opens nothing.
     */
    private void openFileLink(TerminalPane pane, FileLink link) {
        Optional<Path> local = pane.localDirectory();
        if (local.isEmpty()) return;
        FileLinks.resolve(link.path(), local, Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").startsWith("Windows"), pane.wsl())
            .ifPresent(file -> owner.onOpenFile.accept(file, link.line()));
    }
}
```

`WindowContent` hands its wiring to the host (quoted text as Task 3 left it):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(W, [
    ("    private final Map<Tab, Subscription> tabListeners = new IdentityHashMap<>();\n",
     "    private final Map<Tab, Subscription> tabListeners = new IdentityHashMap<>();\n"
     "    /** This window's side of its terminal tabs (workspace tabs spec 9.3). */\n"
     "    private final TabHost tabHost = new TabHost(this);\n", 1),
    ("    private void emit(WorkspaceActivity.Event event) {", "    void emit(WorkspaceActivity.Event event) {", 1),
    (r"""        TerminalTab terminal = new TerminalTab(directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(new TabId(terminal.id()), Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        present(tab, terminal);
        if (terminals != null) terminals.tabOpened(terminal);
        terminal.onChanged = () -> {
            for (TerminalPane pane : terminal.panes()) if (pane.view() == null) pane.applyTheme(themes.current());
            present(tab, terminal);
        };
        terminal.onEmpty = () -> closeTab(tab);
        terminal.onError = (message, cause) -> onLaunchFailure.accept(message, cause);
        terminal.lastInWindow = () -> tabCount() == 1;
        terminal.configure = pane -> configurePane(terminal, pane);
        terminal.onPaneCreated = pane -> connectActivity(terminal, pane);
        for (TerminalPane pane : terminal.panes()) connectActivity(terminal, pane);
        terminal.setBackground(themes.current().surface());
        terminal.setBackgroundOpacity((float) backgroundOpacity);
        for (TerminalPane pane : terminal.panes()) pane.applyTheme(themes.current());
        attach(tab);
        terminal.start();
        return terminal;
""", r"""        TerminalTab terminal = new TerminalTab(directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(new TabId(terminal.id()), Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        attach(tab, true);
        terminal.start();
        return terminal;
""", 1),
    (r"""    private void attach(Tab tab) {
        JComponent component = tab.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            workspace.open(tab);
        } catch (RuntimeException | Error failure) {
            var listener = tabListeners.remove(tab);
            if (listener != null) listener.close();
            workspace.remove(tab);
""", r"""    private void attach(Tab tab) { attach(tab, false); }

    /** As {@link #attach(Tab)}; with {@code opened} a new terminal tab's panes are announced as opened. */
    private void attach(Tab tab, boolean opened) {
        JComponent component = tab.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabOpened(terminal);
            tabHost.attach(tab, opened);
            workspace.open(tab);
            // A tab that moves in ends this window's maximize; one that opens here does not (spec 15).
            if (tab.moving()) workspace.unmaximize();
        } catch (RuntimeException | Error failure) {
            var listener = tabListeners.remove(tab);
            if (listener != null) listener.close();
            tabHost.detach(tab);
            workspace.remove(tab);
""", 1),
    (r"""        if (listener == null) return;
        listener.close();
        if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabClosed(terminal);
""", r"""        if (listener == null) return;
        listener.close();
        tabHost.detach(tab);
        if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabClosed(terminal);
""", 1),
    ("        if (closed) return;\n        workspace.remove(tab);\n",
     "        if (closed) return;\n        // A tab that moves out ends the maximize; closing a tab keeps it while its group stands (spec 15).\n"
     "        if (tab.moving()) workspace.unmaximize();\n        workspace.remove(tab);\n", 1),
    (r"""    /** A terminal tab's presentation: its title and icon. */
    private static void present(Tab tab, TerminalTab terminal) {
        tab.presentation(terminal.title(), "", terminal.icon(), false, "", "");
    }

""", "", 1),
    ("    private void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }",
     r"""    void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }

    /** This window's configuration, or null before the first one arrives. */
    ConfigSnapshot configured() { return workspaceConfiguration.snapshot(); }

    /** A pane's view is ready: the first in this window runs the start-up timing, once. */
    void terminalConfigured(TerminalPane pane) {
        if (firstTerminalSeen) return;
        firstTerminalSeen = true;
        try {
            firstTerminal.accept(pane);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The first-terminal observer failed", failure);
        }
    }""", 1),
])
span(W, "    private void connectActivity(TerminalTab tab, TerminalPane pane) {",
     "        pane.onConnecting = () -> report(new TerminalEvent.SessionConnecting(pane.id()));\n    }\n", "")
span(W, "    private void configurePane(TerminalTab tab, TerminalPane pane) {",
     "            .ifPresent(file -> onOpenFile.accept(file, link.line()));\n    }\n", "")
PY
```

The second `span` removes `configurePane` and, after it, `openFileLink` with its comment; `git diff` shows the
method that follows them, `selectTab(Component component)`, untouched.

- [ ] **Step 4: Ledger rows**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(M + "TabHost.java", "app.workspace", "TabHost", T + "TabMoveTest.java",
           "New in workspace-tabs plan 3a Task 4; a window's side of its terminal tabs")
ledger.add(T + "TabMoveTest.java", "app.workspace", "TabMoveTest", T + "TabMoveTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 3a Task 4; tabs moved between windows")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`TabMoveTest` 6, `TabRegistryTest` 17; `WindowTerminalsTest`, `WindowContentFirstTerminalTest`,
`FileLinkClickTest` and `WorkspaceActivityTest` unchanged). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Tab hosts: tabs move between windows with their sessions

TabHost wires a window's terminal tabs (handlers, activity, registry facts,
theme and opacity); TabRegistry.move detaches a tab from one window and
attaches it to another, ending a maximize on both sides, and "go to"
follows a moved tab.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Splits become groups

Spec 9.2, 9.4, 10, 11 and 15: a terminal tab is one pane (`TerminalContent`); Split Right and Split Down open a terminal
tab in a new group beside the active one, in a running active terminal's directory with its profile, otherwise where a
new tab would start with the default profile (Rulings 11); Close Pane closes the active tab, Zoom Pane maximizes the
active group and Focus Pane moves between groups (Task 6 renames the three); Next, Previous and Select Tab act within
the active group; focus going into a tab (a pane or a plugin's view) makes it the active tab (Rulings 26);
`dim_inactive_panes` dims every terminal tab outside the active group (Rulings 8); a tab whose attach fails is closed
again (Rulings 28). The terminal registry splits into new groups (Rulings 19). `TerminalTab`, `SplitTree` and
`SplitAxis` go.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java`
- Replace: `workspace/TabHost.java`, `workspace/WindowTerminals.java`
- Delete: `workspace/TerminalTab.java`, `workspace/SplitTree.java`, `terminals/SplitAxis.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/SplitTreeTest.java` (its cases live in plan 2's layout tests)
- Modify: `workspace/WindowContent.java`, `workspace/WorkspaceView.java` (`focused`), `workspace/WorkspaceActions.java`,
  `workspace/TerminalPane.java:107-108` (a comment), `workspace/WindowCommands.java:137-138`,
  `workspace/WindowCommandPalette.java:40,105,111,155-157`, `workspace/WorkspaceConfiguration.java:34-48`,
  `workspace/TabState.java`, `terminals/PaneEntry.java`, `terminals/package-info.java`,
  `plugins/HostedTerminals.java:6,280`, `plugins/package-info.java`, `benchmark/BenchmarkRun.java`,
  `benchmark/package-info.java`, `layout/Layout.java:382,513` (two comments naming `SplitTree`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceTabsTest.java` (new), `TerminalTabTest.java`
  (rewritten), `TabStateTest`, `TabMoveTest`, and the tests in Step 5
- Ledger: one row renamed, two dropped, one added

**Interfaces:**
- Consumes: Tasks 3 and 4's `WorkspaceView` (`openBeside`, `toggleMaximize`, `neighbour`, `activate`), `TabHost`,
  `WindowContent.attach`, `tab(Component)`, `closeTab(Tab)`.
- Produces: `public final class TerminalContent extends JPanel implements TabContent` with `TabId id()`,
  `public TerminalPane pane()`, `void start()`, `String title()`, `void rename(String)`, `Optional<String> renamed()`,
  `Icon icon()`, `void focusTerminal()`, `void setActive(boolean)`, `void setBackgroundOpacity(float)`, `boolean closed()`,
  hooks `onChanged`, `onEmpty`, `onError`, `lastInWindow`, `configure`; `TabState.renamed()`; on `WindowContent`:
  `public TerminalContent currentTerminal()` (replaces `currentTab()`), `public java.util.List<TerminalContent>
  terminalTabs()`, `TerminalContent openTab(...)` (the four overloads), `public TerminalContent split(Side)`,
  `TerminalContent openBeside(TerminalContent target, Side side, Path directoryOrNull, SessionRequest requestOrNull,
  Optional<String> profile, Optional<java.util.List<String>> program)`, `void toggleMaximize()`, `void focusGroup(Side)`,
  `TerminalContent terminalOf(TerminalPane)`, `void tabFocused(Tab)`, `Tab tabHolding(Component)`;
  `WorkspaceView.focused(Tab)`; `PaneEntry.split` is a `BiFunction<Side, OpenSpec, Optional<PaneEntry>>`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceTabsTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ShellExitBehavior;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10, 11 and 15: terminal tabs in groups (workspace plan 3a Task 5). */
class WorkspaceTabsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** A window of {@code count} running terminal tabs in one group. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); for (int i = 1; i < count; i++) owner[0].newTab(HOME); });
        launch();
        edt(() -> { });
        return owner[0];
    }

    private static float dim(TerminalPane pane) throws ReflectiveOperationException {
        var field = pane.view().getClass().getDeclaredField("inactiveDim");
        field.setAccessible(true);
        return (float) field.get(pane.view());
    }

    @Test void cmdDOpensATerminalTabInANewGroupWhereTheActiveTerminalIsWithItsProfile(@TempDir Path folder) throws Exception {
        var starts = new ArrayList<Path>();
        var profiles = new ArrayList<Optional<String>>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(recording(starts, profiles)); owner[0].openTab(folder, null, Optional.of("zsh")); });
        launch();
        edt(() -> {
            var window = owner[0];
            var target = window.currentTerminal();
            var group = window.workspace().model().activeGroup();
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.SPLIT_RIGHT).orElseThrow(), window.currentPane()))
                .as("Cmd+D").isTrue();
            var layout = window.workspace().model();
            assertThat(layout.groups()).hasSize(2);
            var root = (Split) layout.root();
            assertThat(root.orientation()).isEqualTo(Orientation.HORIZONTAL);
            assertThat(((Group) root.children().get(0).node()).id()).as("the active group stays on the left").isEqualTo(group);
            var created = window.currentTerminal();
            assertThat(created).isNotSameAs(target);
            assertThat(((Group) root.children().get(1).node()).tabs()).containsExactly(window.selectedTab().id());
            assertThat(created.pane().profile()).as("the active terminal's profile").contains("zsh");
            assertThat(root.children().get(0).weight()).isCloseTo(0.5, within(1e-9));
        });
        launch();
        assertThat(starts.getLast()).as("the active terminal's directory").isEqualTo(folder);
        assertThat(profiles.getLast()).contains("zsh");
        edt(() -> {
            owner[0].invoke(ActionId.SPLIT_DOWN);
            var right = (Split) ((Split) owner[0].workspace().model().root()).children().get(1).node();
            assertThat(right.orientation()).as("Cmd+Shift+D splits the active group downwards").isEqualTo(Orientation.VERTICAL);
        });
    }

    @Test void aSplitWhoseShellExitsCollapsesAndRestoresTheSizes() throws Exception {
        var window = window(2);
        TerminalPane[] original = new TerminalPane[1];
        TerminalContent[] split = new TerminalContent[1];
        edt(() -> {
            var workspace = window.workspace();
            workspace.moveToNewGroup(window.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            workspace.resizeSplit(List.of(), List.of(0.7, 0.3));
            window.selectTab(window.tabs().get(0));
            original[0] = window.currentPane();
            split[0] = window.split(Side.DOWN);
            assertThat(workspace.model().groups()).hasSize(3);
        });
        launch();
        edt(() -> {
            split[0].pane().setShellExitBehavior(ShellExitBehavior.CLOSE);
            split[0].pane().session().close();
        });
        until(() -> window.tabCount() == 2);
        edt(() -> {
            var root = (Split) window.workspace().model().root();
            assertThat(root.children()).hasSize(2);
            assertThat(root.children().get(0).weight()).as("the sizes before the split").isCloseTo(0.7, within(1e-9));
            assertThat(root.children().get(1).weight()).isCloseTo(0.3, within(1e-9));
            assertThat(window.currentPane()).as("the terminal it split from is active again").isSameAs(original[0]);
            try { assertThat(dim(original[0])).as("and undimmed").isZero(); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test void dimmingFollowsTheActiveGroup() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            workspace.moveToNewGroup(window.tabs().get(1), workspace.model().activeGroup(), Side.RIGHT);
            var left = window.terminalTabs().get(0).pane(); var right = window.terminalTabs().get(1).pane();
            try {
                assertThat(dim(right)).as("the active group").isZero();
                assertThat(dim(left)).as("a terminal tab outside the active group").isPositive();
                window.focusGroup(Side.LEFT);
                assertThat(dim(left)).isZero();
                assertThat(dim(right)).isPositive();
                window.setActive(false);
                assertThat(dim(left)).as("an inactive window dims every terminal").isPositive();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test void maximizeTogglesTheActiveGroupAndNeedsTwoGroups() throws Exception {
        var window = window(2);
        edt(() -> {
            window.updateActions();
            assertThat(window.action(ActionId.ZOOM_PANE).isEnabled()).as("one group already fills the workspace").isFalse();
            window.workspace().moveToNewGroup(window.tabs().get(1), window.workspace().model().activeGroup(), Side.DOWN);
            window.updateActions();
            assertThat(window.action(ActionId.ZOOM_PANE).isEnabled()).isTrue();
            window.invoke(ActionId.ZOOM_PANE);
            assertThat(window.workspace().model().maximized()).contains(window.workspace().model().activeGroup());
            assertThat(window.workspace().getComponent(0)).isSameAs(window.workspace().activeGroupView());
            window.invoke(ActionId.ZOOM_PANE);
            assertThat(window.workspace().model().maximized()).isEmpty();
        });
    }

    @Test void focusMovesToTheNeighbouringGroupAndStopsAtTheEdge() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a = window.tabs().get(0); var b = window.tabs().get(1); var c = window.tabs().get(2);
            workspace.moveToNewGroup(b, workspace.model().activeGroup(), Side.RIGHT);
            workspace.moveToNewGroup(c, workspace.model().groupOf(b.id()).orElseThrow(), Side.DOWN);
            assertThat(window.selectedTab()).isSameAs(c);
            window.invoke(ActionId.FOCUS_PANE_UP);
            assertThat(window.selectedTab()).isSameAs(b);
            window.invoke(ActionId.FOCUS_PANE_LEFT);
            assertThat(window.selectedTab()).isSameAs(a);
            window.updateActions();
            assertThat(window.action(ActionId.FOCUS_PANE_LEFT).isEnabled()).as("nothing further left").isFalse();
            window.invoke(ActionId.FOCUS_PANE_LEFT);
            assertThat(window.selectedTab()).isSameAs(a);
            window.invoke(ActionId.FOCUS_PANE_RIGHT);
            assertThat(window.selectedTab()).as("the nearest centre across, then tree order").isSameAs(b);
            window.invoke(ActionId.FOCUS_PANE_DOWN);
            assertThat(window.selectedTab()).isSameAs(c);
        });
    }

    /** A launcher that records where each shell starts and with which profile. */
    private ShellLauncher recording(List<Path> starts, List<Optional<String>> profiles) {
        return new ShellLauncher(pending::add, (Optional<String> profile) -> {
            profiles.add(profile);
            String id = profile.orElse("login");
            return new LaunchSettings(List.of("idle"), Map.of(), 80, 24, 100,
                new ShellProfile(id, "Profile " + id, ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("idle")));
        }, (directory, settings) -> { starts.add(directory); return IdleTerminalSession.start(); });
    }

    @Test void splitsBesideAnEndedShellOrAPluginViewOpenWithTheDefaultProfile(@TempDir Path folder) throws Exception {
        var starts = new ArrayList<Path>();
        var profiles = new ArrayList<Optional<String>>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(recording(starts, profiles)); owner[0].openTab(folder, null, Optional.of("zsh")); });
        launch();
        TerminalPane[] ended = new TerminalPane[1];
        edt(() -> { ended[0] = owner[0].currentPane(); ended[0].session().close(); });
        until(() -> !ended[0].running());
        edt(() -> {
            owner[0].updateActions();
            assertThat(owner[0].action(ActionId.SPLIT_DOWN).isEnabled()).as("beside a terminal whose shell ended").isTrue();
            owner[0].invoke(ActionId.SPLIT_DOWN);
            assertThat(owner[0].workspace().model().groups()).hasSize(2);
        });
        launch();
        assertThat(starts.getLast()).as("where a new tab from there starts").isEqualTo(folder);
        assertThat(profiles.getLast()).as("with the default profile").isEmpty();
        Object[] view = new Object[1];
        edt(() -> {
            var model = new Contributions();
            owner[0].connectContributions(model);
            var opened = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner[0].id(), new JPanel());
            view[0] = opened;
            assertThat(owner[0].selectedContent()).isSameAs(opened);
            owner[0].updateActions();
            assertThat(owner[0].action(ActionId.SPLIT_RIGHT).isEnabled()).as("beside a plugin view").isTrue();
            owner[0].invoke(ActionId.SPLIT_RIGHT);
            var layout = owner[0].workspace().model();
            assertThat(layout.groups()).hasSize(3);
            assertThat(layout.groupOf(owner[0].selectedTab().id())).isNotEqualTo(layout.groupOf(opened.id()));
        });
        launch();
        assertThat(profiles.getLast()).as("beside a plugin view, the default profile").isEmpty();
        edt(() -> {
            var opened = (dev.jasper.app.contributions.Tab) view[0];
            owner[0].selectTab(opened);
            owner[0].invoke(ActionId.NEW_TAB);
            var layout = owner[0].workspace().model();
            assertThat(layout.groupOf(owner[0].selectedTab().id())).as("New Tab opens in the plugin view's group")
                .isEqualTo(layout.groupOf(opened.id()));
        });
        launch();
    }

    @Test void focusGoingIntoAnotherGroupsTerminalMakesItsTabActive() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            var left = window.tabs().get(0); var right = window.tabs().get(1);
            workspace.moveToNewGroup(right, workspace.model().activeGroup(), Side.RIGHT);
            assertThat(window.selectedTab()).isSameAs(right);
            var pane = ((TerminalContent) left.content()).pane();
            var gained = new FocusEvent(pane.view(), FocusEvent.FOCUS_GAINED);
            for (var listener : pane.view().getFocusListeners()) listener.focusGained(gained);
            assertThat(window.selectedTab()).as("the focused terminal's tab").isSameAs(left);
            assertThat(workspace.model().activeGroup()).isEqualTo(workspace.model().groupOf(left.id()).orElseThrow());
            try {
                assertThat(dim(pane)).as("undimmed").isZero();
                assertThat(dim(((TerminalContent) right.content()).pane())).isPositive();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), pane.view())).isTrue();
            assertThat(left.closed()).as("the shortcut closed the focused tab").isTrue();
            assertThat(window.tabs()).containsExactly(right);
        });
    }

    /** Sets the permanent focus owner as a click into a component does, without a native window. */
    private static final class FocusOwners extends DefaultKeyboardFocusManager {
        void own(Component component) { setGlobalPermanentFocusOwner(component); }
    }

    @Test void focusGoingIntoAPluginViewInAnotherGroupMakesItsTabActive() throws Exception {
        var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new FocusOwners();
        // The window listens to the focus manager current when it is built.
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focus));
        try {
            var window = window(1);
            edt(() -> {
                var model = new Contributions();
                window.connectContributions(model);
                var field = new JTextField();
                var panel = new JPanel(); panel.add(field);
                var view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), panel);
                window.workspace().moveToNewGroup(view, window.workspace().model().activeGroup(), Side.RIGHT);
                var terminal = window.tabs().get(0);
                window.selectTab(terminal);
                assertThat(window.selectedTab()).isSameAs(terminal);
                focus.own(field);
                assertThat(window.selectedTab()).as("focus went into the plugin view").isSameAs(view);
                assertThat(window.workspace().model().activeGroup()).isEqualTo(window.workspace().model().groupOf(view.id()).orElseThrow());
                focus.own(null);
            });
        } finally {
            edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(prior));
        }
    }

    @Test void aTerminalTabWhoseAttachFailsGoesWithItsPane() throws Exception {
        var window = window(1);
        edt(() -> {
            var before = window.tabs();
            var registered = window.tabRegistry().inWindow(window.id());
            Object known = window.currentPane().id();
            window.activity(event -> {
                if (event instanceof WorkspaceActivity.PaneState state && state.state() == WorkspaceActivity.State.OPENED
                        && !known.equals(state.id())) throw new IllegalStateException("an activity listener failed");
            });
            assertThatThrownBy(() -> window.newTab(HOME)).hasMessage("an activity listener failed");
            assertThat(window.tabs()).isEqualTo(before);
            assertThat(window.tabRegistry().inWindow(window.id())).as("the registry forgot the tab").isEqualTo(registered);
        });
        assertThat(pending).as("no shell started for it").isEmpty();
    }

    @Test void nextPreviousAndSelectTabActWithinTheActiveGroup() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1); var b1 = window.tabs().get(2);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            window.selectTab(a1);
            window.invoke(ActionId.SELECT_TAB_2);
            assertThat(window.selectedTab()).isSameAs(a2);
            window.invoke(ActionId.NEXT_TAB);
            assertThat(window.selectedTab()).as("wraps within the group").isSameAs(a1);
            window.invoke(ActionId.PREVIOUS_TAB);
            assertThat(window.selectedTab()).isSameAs(a2);
            window.selectTab(b1);
            window.updateActions();
            assertThat(window.action(ActionId.SELECT_TAB_2).isEnabled()).as("the active group has one tab").isFalse();
            window.invoke(ActionId.NEXT_TAB);
            assertThat(window.selectedTab()).isSameAs(b1);
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTabTest.java` with (terminal tabs, one pane
each, in groups; the session, title, failure and minimum-size cases keep their assertions):

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.Side;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.jasper.terminal.session.TerminalSession;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Terminal tabs: one pane each, in groups (workspace tabs spec 9.2). */
@DisabledOnOs(OS.WINDOWS)
class TerminalTabTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void completionAfterOwnerCloseTerminatesReturnedProcess() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        List<TerminalSession> sessions = new ArrayList<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            TerminalSession session = shell(path); sessions.add(session); return session;
        }, "sh");
        edt(() -> { WindowContent owner = content(launcher); owner.close(); owner.close(); });
        pending.remove().run();
        edt(() -> {});
        assertThat(sessions.getFirst().exitFuture().get(5, TimeUnit.SECONDS)).isNotNull();
    }
    @Test void closingOneOwnerPreservesOtherAndReorderPreservesActiveIdentity() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> { owners[0] = content(launcher(pending)); owners[1] = content(launcher(pending)); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            owners[0].close();
            assertThat(owners[1].currentPane().running()).isTrue();
            TerminalContent original = owners[1].currentTerminal();
            owners[1].newTab(HOME);
            owners[1].selectTab(original);
            owners[1].workspace().activeGroupView().reorder(owners[1].tabs().get(0), 1);
            assertThat(owners[1].currentTerminal()).isSameAs(original);
            owners[1].close();
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {});
    }
    @Test void splitsAndMaximizeReuseSessionsAndRenameSurvivesShellTitle() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> {
            first[0] = owner[0].currentPane();
            owner[0].currentTerminal().rename("work");
            owner[0].invoke(ActionId.SPLIT_RIGHT);
        });
        pending.remove().run();
        edt(() -> {
            TerminalContent work = owner[0].terminalOf(first[0]);
            owner[0].toggleMaximize(); owner[0].focusGroup(Side.LEFT); owner[0].toggleMaximize();
            assertThat(owner[0].currentPane()).isSameAs(first[0]);
            assertThat(owner[0].workspace().model().maximized()).isEmpty();
            assertThat(first[0].running()).isTrue();
            assertThat(work.title()).isEqualTo("work");
            owner[0].closeTab(work);
            assertThat(owner[0].tabCount()).isEqualTo(1);
            assertThat(owner[0].workspace().model().groups()).hasSize(1);
            assertThat(owner[0].currentPane().running()).isTrue();
            owner[0].close();
        });
    }
    @Test void failedSplitShowsErrorAndKeepsExistingSession() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        int[] launches = {0};
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            if (++launches[0] > 1) throw new IllegalStateException("shell executable unavailable");
            return shell(path);
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        List<String> errors = new ArrayList<>();
        edt(() -> { owner[0] = content(launcher); owner[0].onError = (title, message) -> errors.add(title + ": " + message); });
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> { first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.SPLIT_DOWN); });
        pending.remove().run();
        edt(() -> {
            assertThat(errors).singleElement().asString().contains("shell executable unavailable");
            assertThat(errors.getFirst()).startsWith("Could not start the terminal: ");
            assertThat(owner[0].currentPane()).isSameAs(first[0]);
            assertThat(first[0].running()).isTrue();
            assertThat(owner[0].tabCount()).as("the failed split's tab closed").isEqualTo(1);
            assertThat(owner[0].workspace().model().groups()).as("and its group with it").hasSize(1);
            owner[0].close();
        });
    }

    @Test void aFailedLaunchReportsItsDetailsAndWhatWasThrown() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var thrown = new IllegalStateException("shell executable unavailable");
        int[] launches = {0};
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            if (++launches[0] > 1) throw thrown;
            return shell(path);
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        List<String> details = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].onLaunchFailure = (text, cause) -> { details.add(text); causes.add(cause); };
        });
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN));
        pending.remove().run();
        edt(() -> {
            assertThat(details).singleElement().asString().contains("shell executable unavailable");
            assertThat(causes).containsExactly(thrown);
            owner[0].close();
        });
    }

    @Test void titleOverrideSurvivesRealOscTitleChangeAndBlankRestoresIt() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of("/bin/bash")), "Bash fixture unavailable");
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/bash", "--noprofile", "--norc", "-c",
                    "read answer; printf '\\033]2;new-shell-title\\007'; read answer")).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception e) { throw new RuntimeException(e); }
        }, "bash");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher)); pending.remove().run();
        edt(() -> { owner[0].currentTerminal().rename("manual"); owner[0].currentPane().session().write("go\n"); });
        until(() -> owner[0].currentPane().title().equals("new-shell-title"));
        edt(() -> {
            assertThat(owner[0].currentTerminal().title()).isEqualTo("manual");
            assertThat(owner[0].currentTerminal().renamed()).contains("manual");
            owner[0].currentTerminal().rename("");
            assertThat(owner[0].currentTerminal().title()).isEqualTo("new-shell-title");
            assertThat(owner[0].selectedTab().title()).as("the tab's presentation follows").isEqualTo("new-shell-title");
            owner[0].close();
        });
    }

    @Test void weightsSurviveMaximizeAndAStaleSplitChangesNothing() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> {
            owner[0].setSize(1000, 600); LayoutTestSupport.layoutTree(owner[0]);
            var old = owner[0].workspace().split(List.of());
            owner[0].workspace().resizeSplit(List.of(), List.of(0.7, 0.3));
            owner[0].toggleMaximize(); owner[0].toggleMaximize();
            LayoutTestSupport.layoutTree(owner[0]);
            var restored = owner[0].workspace().split(List.of());
            assertThat(restored).as("the maximize rebuilt the split").isNotSameAs(old);
            assertThat(restored.weights().get(0)).isCloseTo(0.7, within(1e-9));
            int available = restored.getWidth() - SplitContainer.dividerSize();
            assertThat(restored.children().get(0).getWidth()).isCloseTo((int) Math.round(available * 0.7), within(1));
            owner[0].closeTab(owner[0].currentTerminal());
            old.setWeights(List.of(0.2, 0.8));
            assertThat(owner[0].workspace().model().groups()).hasSize(1);
            assertThat(owner[0].tabCount()).isEqualTo(1);
            owner[0].close();
        });
    }
    @Test void layoutMinimumIncludesLargeFontsNestedGroupsAndVisibleChrome() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN)); pending.remove().run();
        edt(() -> {
            var content = owner[0];
            var panes = content.terminalTabs().stream().map(TerminalContent::pane).toList();
            for (TerminalPane pane : panes) pane.view().setFontSize(72);
            int twoColumns = panes.get(0).view().getMinimumSize().width + panes.get(1).view().getMinimumSize().width;
            int twoRows = panes.get(1).view().getMinimumSize().height + panes.get(2).view().getMinimumSize().height;
            var workspace = content.workspace();
            assertThat(workspace.getMinimumSize().width).isGreaterThan(twoColumns);
            assertThat(workspace.getMinimumSize().height).isGreaterThan(twoRows);
            javax.swing.JRootPane root = new javax.swing.JRootPane();
            root.setContentPane(content); root.setJMenuBar(content.menuBar());
            var outer = TerminalWindow.minimumSize(root, new java.awt.Insets(30, 2, 2, 2));
            assertThat(outer.height).isGreaterThan(workspace.getMinimumSize().height + 32);
            assertThat(outer.width).isGreaterThan(twoColumns + 4);
            java.awt.Dimension previous = outer;
            content.toggleMaximize();
            assertThat(TerminalWindow.minimumSize(root, new java.awt.Insets(30, 2, 2, 2)).height).isLessThan(previous.height);
            content.close();
        });
    }
    @Test void longOscDirectoryAndTitlesDoNotInflateNativeWindowMinimum() throws Exception {
        String directory = "/" + "long-directory-segment/".repeat(100);
        String title = "long shell title ".repeat(100);
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
                    "read answer; printf '\\033]7;file://localhost" + directory + "\\007\\033]2;" + title + "\\007'; read answer")).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception e) { throw new RuntimeException(e); }
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        javax.swing.JRootPane[] root = new javax.swing.JRootPane[1];
        java.awt.Dimension[] minimum = new java.awt.Dimension[1];
        edt(() -> owner[0] = content(launcher)); pending.remove().run();
        edt(() -> {
            root[0] = new javax.swing.JRootPane(); root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
            minimum[0] = TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2));
            owner[0].currentPane().session().write("go\n");
        });
        until(() -> owner[0].currentPane().title().equals(title));
        edt(() -> {
            owner[0].update();
            assertThat(owner[0].status().getText()).contains("long-directory-segment/");
            assertThat(TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].currentTerminal().rename("manual rename ".repeat(200));
            assertThat(TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].close();
        });
    }
}
```

Add to `TabStateTest`, before its last closing brace:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/TabStateTest.java", [(
"""        assertThat(tab.title(null, Path.of("/different/path"))).isEqualTo("path");
    }
}
""", """        assertThat(tab.title(null, Path.of("/different/path"))).isEqualTo("path");
    }

    @Test
    void theNameTheUserGaveIsKeptApartFromTheAutomaticTitle() {
        TabState tab = new TabState();
        assertThat(tab.renamed()).isEmpty();
        tab.rename("build");
        assertThat(tab.renamed()).contains("build");
        tab.rename(" ");
        assertThat(tab.renamed()).isEmpty();
    }
}
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `TerminalContent`, `currentTerminal`, `split(Side)`, `TabState.renamed`.

- [ ] **Step 3: A terminal tab is one pane**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.TabContent;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.terminals.SessionRequest;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A terminal tab's content (workspace tabs spec 9.2): one {@link TerminalPane}, so one session, and the tab's own name.
 * Its window wires it through a {@code TabHost}; moving it to another window rewires it and never touches the session.
 * EDT only.
 */
public final class TerminalContent extends JPanel implements TabContent, AutoCloseable {
    private final TabId id;
    private final TerminalPane pane;
    private final TabState state = new TabState();
    private boolean closed;
    private Icon terminalIcon;
    /** The pane changed: its title, directory, state or size. */
    Runnable onChanged = () -> { };
    /** The pane asked to close (its shell exited under a closing policy, its provider ended, a failed launch). */
    Runnable onEmpty = () -> { };
    BiConsumer<String, Throwable> onError = (message, cause) -> { };
    /** Whether this is the only tab its window holds; a failed launch then stays to show why. */
    BooleanSupplier lastInWindow = () -> false;
    /** The pane's view is ready. */
    Consumer<TerminalPane> configure = ready -> { };

    /**
     * A tab whose shell starts in {@code directory}, where null means where a new shell starts; a request makes the pane
     * a provided session; {@code profile} names a local shell's profile, empty for the user's default; {@code program}
     * runs instead of the shell.
     */
    TerminalContent(TabId id, Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile,
                    Optional<List<String>> program) {
        super(new BorderLayout());
        this.id = Objects.requireNonNull(id, "id");
        pane = new TerminalPane(directory, launcher, requestOrNull, profile, program);
        pane.onChanged = () -> onChanged.run();
        pane.onClose = () -> onEmpty.run();
        pane.onFailure = (message, cause) -> {
            onError.accept(message, cause);
            // The window's only tab stays, showing why it failed: closing it would close the window before anyone read the error.
            if (!lastInWindow.getAsBoolean()) onEmpty.run();
        };
        pane.onReady = view -> configure.accept(pane);
        add(pane, BorderLayout.CENTER);
    }

    TabId id() { return id; }
    /** @return the tab's one pane */
    public TerminalPane pane() { return pane; }
    void start() { pane.start(); }
    /** The tab's title: the name the user gave it, else the shell's title, else its folder's name, else "Terminal". */
    String title() { return state.title(pane.tabTitle(), pane.directory()); }
    /** Names the tab; blank goes back to the automatic title. */
    void rename(String name) { state.rename(name); onChanged.run(); }
    /** The name the user gave the tab, kept apart from the shell's title (workspace plan 6 saves it). */
    Optional<String> renamed() { return state.renamed(); }

    /** The provided session's icon, or the host terminal icon for a local shell. */
    Icon icon() {
        Icon provided = pane.providedIcon();
        if (provided != null) return provided;
        if (terminalIcon == null) terminalIcon = AppIcons.named("TERMINAL");
        return terminalIcon;
    }

    void focusTerminal() { if (!closed) pane.focusTerminal(); }
    /** Undimmed while this is its window's active tab in an active window (workspace plan 3a Rulings 8). */
    void setActive(boolean active) { pane.setActive(active); }

    /** Below 1 the tab paints nothing behind its pane, which fills its padding at that opacity. */
    void setBackgroundOpacity(float opacity) {
        setOpaque(opacity >= 1f);
        pane.setBackgroundOpacity(opacity);
        repaint();
    }

    boolean closed() { return closed; }

    @Override public JComponent component() { return this; }
    @Override public void dispose() { close(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        pane.close();
        removeAll();
        onChanged = () -> { }; onEmpty = () -> { }; onError = (message, cause) -> { };
        lastInWindow = () -> false; configure = ready -> { };
    }
}
```

Give `TabState` the name it keeps:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/workspace/TabState.java", [(
"""        renamedTitle = title == null || title.isBlank() ? null : title;
    }
""", """        renamedTitle = title == null || title.isBlank() ? null : title;
    }

    /** The name the user gave, if any. */
    java.util.Optional<String> renamed() { return java.util.Optional.ofNullable(renamedTitle); }
""", 1)])
PY
```

Replace the whole of `workspace/TabHost.java` with (Task 4's host over one pane per tab):

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.terminal.view.FileLink;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.KeyStroke;

/**
 * One window's side of the terminal tabs it shows (workspace tabs spec 9.3): the shortcut, context-menu and file-link
 * handlers a pane's view gets, the window its activity and terminal-registry facts report from, the window's theme and
 * opacity, and focus. A tab moving to another window is detached from this host and attached to the next window's; its
 * session is never touched (spec 2.3). A "go to" callback selects its tab through the registry when it runs, so it
 * follows the tab to whichever window holds it then. EDT only.
 */
final class TabHost {
    private final WindowContent owner;

    TabHost(WindowContent owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

    /**
     * Wires a terminal tab to this window; a tab of another kind needs nothing. With {@code opened} its pane is announced
     * as new first; a tab arriving from another window keeps its pane unannounced.
     */
    void attach(Tab tab, boolean opened) {
        if (!(tab.content() instanceof TerminalContent terminal)) return;
        TerminalPane pane = terminal.pane();
        terminal.onChanged = () -> {
            if (pane.view() == null) pane.applyTheme(owner.theme());
            present(tab, terminal);
        };
        terminal.onEmpty = () -> owner.closeTab(tab);
        terminal.onError = (message, cause) -> owner.onLaunchFailure.accept(message, cause);
        terminal.lastInWindow = () -> owner.tabCount() == 1;
        terminal.configure = ready -> configure(tab, ready);
        terminal.setBackground(owner.theme().surface());
        terminal.setBackgroundOpacity((float) owner.backgroundOpacity());
        pane.applyTheme(owner.theme());
        if (opened) announce(tab, pane);
        wire(tab, pane);
        if (pane.view() != null) { bind(tab, pane); behave(pane); }
        present(tab, terminal);
    }

    /** Unwires a terminal tab from this window before it closes or moves on. */
    void detach(Tab tab) {
        if (!(tab.content() instanceof TerminalContent terminal)) return;
        terminal.onChanged = () -> { };
        terminal.onEmpty = () -> { };
        terminal.onError = (message, cause) -> { };
        terminal.lastInWindow = () -> false;
        terminal.configure = ready -> { };
        unwire(terminal.pane());
    }

    /** A terminal tab's presentation: its title and icon. */
    static void present(Tab tab, TerminalContent terminal) {
        tab.presentation(terminal.title(), "", terminal.icon(), false, "", "");
    }

    /** A pane that has just opened: this window's activity and the terminal registry hear of it. */
    private void announce(Tab tab, TerminalPane pane) {
        owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        owner.report(new TerminalEvent.PaneOpened(tab.id().value(), pane.id()));
    }

    /** The pane reports its activity and facts to this window from now on. */
    private void wire(Tab tab, TerminalPane pane) {
        UUID tabId = tab.id().value();
        // Focus going into the pane makes its tab the window's active tab, focus staying put (Rulings 26).
        pane.onFocused = () -> owner.tabFocused(tab);
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            owner.emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> goTo(tab, pane), pane.watched() && owner.isActiveAndOpen() && tab == owner.selectedTab()));
            owner.report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            owner.emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            owner.report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            owner.report(new TerminalEvent.PaneClosed(tabId, pane.id()));
        };
        pane.onPaneFocused = () -> {
            owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            owner.report(new TerminalEvent.PaneFocused(tabId, pane.id()));
        };
        pane.onPaneBlurred = () -> owner.emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {
            owner.emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(owner.anyWindowActive.getAsBoolean(), owner.isActiveAndOpen(),
                    tab == owner.selectedTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> goTo(tab, pane)));
            owner.report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote));
        };
        pane.onDirectoryChanged = (directory, remote) -> owner.report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
        pane.onBell = () -> owner.report(new TerminalEvent.Bell(tabId, pane.id()));
        pane.onStarted = () -> owner.report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> owner.report(new TerminalEvent.SessionExited(pane.id(), status));
        pane.onConnecting = () -> owner.report(new TerminalEvent.SessionConnecting(pane.id()));
    }

    /** "Go to": the tab, wherever it is now, then its pane (workspace plan 3a Rulings 18). */
    private static void goTo(Tab tab, TerminalPane pane) {
        tab.select();
        pane.focusTerminal();
    }

    /** A pane whose view just became ready: this window's configuration, its handlers and the first-terminal timing. */
    private void configure(Tab tab, TerminalPane pane) {
        pane.applyTheme(owner.theme());
        ConfigSnapshot configured = owner.configured();
        if (configured == null) pane.view().setFontSize(owner.configuredFontSize());
        else pane.view().applyOptions(configured.viewOptions(owner.configuredFontSize(), owner.theme().palette()));
        behave(pane);
        bind(tab, pane);
        owner.terminalConfigured(pane);
    }

    /** This window's dimming and shell-exit policy; a moved pane keeps its view's own options. */
    private void behave(TerminalPane pane) {
        ConfigSnapshot configured = owner.configured();
        if (configured == null) return;
        pane.setConfiguredDim(configured.terminal().dimInactivePanes());
        pane.setShellExitBehavior(configured.terminal().onExit());
    }

    /** The view's shortcut, context-menu and file-link handlers, and its launch focus, go to this window. */
    private void bind(Tab tab, TerminalPane pane) {
        pane.allowLaunchFocus = () -> owner.commandPalette() == null || !owner.commandPalette().blocksActions();
        pane.view().setShortcutHandler(event -> owner.dispatchShortcut(KeyStroke.getKeyStrokeForEvent(event), pane.view()));
        pane.view().setContextMenuHandler(event -> {
            owner.selectTab(tab); pane.focusTerminal(); owner.update();
            owner.chrome().contextMenu().show(pane.view(), event.getX(), event.getY());
        });
        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
    }

    private static void unwire(TerminalPane pane) {
        pane.onFocused = () -> { };
        pane.onCommandStarted = command -> { };
        pane.onTitleChanged = title -> { };
        pane.onClosed = () -> { };
        pane.onPaneFocused = () -> { };
        pane.onPaneBlurred = () -> { };
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> { };
        pane.onDirectoryChanged = (directory, remote) -> { };
        pane.onBell = () -> { };
        pane.onStarted = () -> { };
        pane.onExited = status -> { };
        pane.onConnecting = () -> { };
        pane.allowLaunchFocus = () -> true;
        if (pane.view() != null) {
            pane.view().setShortcutHandler(null); pane.view().setContextMenuHandler(null); pane.view().setFileLinkHandler(null);
        }
    }

    /**
     * A Cmd-click on a file in {@code pane}: resolved against the pane's local directory and given to the window's
     * {@code onOpenFile}. A provided session, or a shell that reports a directory on another host, opens nothing.
     */
    private void openFileLink(TerminalPane pane, FileLink link) {
        Optional<Path> local = pane.localDirectory();
        if (local.isEmpty()) return;
        FileLinks.resolve(link.path(), local, Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").startsWith("Windows"), pane.wsl())
            .ifPresent(file -> owner.onOpenFile.accept(file, link.line()));
    }
}
```

Replace the whole of `workspace/WindowTerminals.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One window's presence in the terminal registry (workspace tabs spec 9.4): entries that read this window's terminal
 * tabs, one pane each, on demand, and the facts the window publishes. A pane's split opens a terminal tab in a new group
 * beside the pane's group. Owned and closed by {@link WindowContent}. EDT only.
 */
final class WindowTerminals implements AutoCloseable {
    private final WindowContent owner;
    private final TerminalRegistry registry;
    private final Subscription registration;
    private UUID selected;
    private boolean closed;

    WindowTerminals(WindowContent owner, TerminalRegistry registry, Runnable toFront) {
        this.owner = owner; this.registry = registry;
        var entry = new WindowEntry(owner.id(), () -> owner.terminalTabs().stream().map(this::entry).toList(),
            () -> Optional.ofNullable(owner.currentTerminal()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (TerminalContent terminal : owner.terminalTabs()) {
                tabOpened(terminal);
                publish(new TerminalEvent.PaneOpened(terminal.id().value(), terminal.pane().id()));
            }
            tabSelected();
        });
    }

    TabEntry entry(TerminalContent terminal) {
        return new TabEntry(terminal.id().value(), owner.id(), () -> List.of(entry(terminal, terminal.pane())),
            () -> Optional.of(entry(terminal, terminal.pane())), terminal::title, () -> owner.selectTab(terminal));
    }

    PaneEntry entry(TerminalContent terminal, TerminalPane pane) {
        return new PaneEntry(pane.id(), terminal.id().value(), pane::snapshot, pane::queryForegroundJob, pane::write, pane::paste,
            pane::selectedText, () -> { owner.selectTab(terminal); pane.focusTerminal(); },
            (side, spec) -> {
                TerminalContent created = switch (spec) {
                    case OpenSpec.Local local -> owner.openBeside(terminal, side, local.directory().orElse(null), null,
                        local.profile(), local.program());
                    case OpenSpec.Session session -> owner.openBeside(terminal, side, home(), session.request(),
                        Optional.empty(), Optional.empty());
                };
                return Optional.ofNullable(created).map(split -> entry(split, split.pane()));
            });
    }

    private static Path home() { return Path.of(System.getProperty("user.home")); }

    private Optional<PaneEntry> openTab(OpenSpec spec) {
        if (closed) return Optional.empty();
        TerminalContent terminal = switch (spec) {
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory), null, local.profile(), local.program());
            case OpenSpec.Session session -> owner.openTab(home(), session.request());
        };
        return terminal == null ? Optional.empty() : Optional.of(entry(terminal, terminal.pane()));
    }

    void publish(TerminalEvent event) { if (!closed) registry.publish(event); }
    void atomically(Runnable change) { if (closed) change.run(); else registry.atomically(change); }
    void refresh() { if (!closed) registry.refresh(); }

    void tabOpened(TerminalContent terminal) { publish(new TerminalEvent.TabOpened(owner.id(), terminal.id().value())); }
    void tabClosed(TerminalContent terminal) { publish(new TerminalEvent.TabClosed(owner.id(), terminal.id().value())); }

    /** Reports the active terminal tab when it actually changed; layout changes happen for other reasons too. */
    void tabSelected() {
        TerminalContent current = owner.currentTerminal();
        UUID now = current == null ? null : current.id().value();
        if (Objects.equals(now, selected)) return;
        selected = now;
        if (now != null) publish(new TerminalEvent.TabSelected(owner.id(), now));
    }

    @Override public void close() {
        if (closed) return;
        registration.close();
        closed = true;
    }
}
```

- [ ] **Step 4: The window splits into groups**

`WindowContent` (quoted text as Task 4 left it):

```bash
git rm -q jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java \
          jasper-app/src/main/java/dev/jasper/app/workspace/SplitTree.java \
          jasper-app/src/main/java/dev/jasper/app/terminals/SplitAxis.java \
          jasper-app/src/test/java/dev/jasper/app/workspace/SplitTreeTest.java
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span
W = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"
edit(W, [
    ("import dev.jasper.app.layout.TabId;\n",
     "import dev.jasper.app.layout.GroupId;\nimport dev.jasper.app.layout.Side;\nimport dev.jasper.app.layout.TabId;\n", 1),
    ("    private final TabHost tabHost = new TabHost(this);\n", """    private final TabHost tabHost = new TabHost(this);
    /** Where focus goes: focus going into a plugin's view, or a pane, makes its tab the active tab (Rulings 26). */
    private final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private final java.beans.PropertyChangeListener focusOwners = event -> {
        if (event.getNewValue() instanceof Component focused) tabFocused(tabHolding(focused));
    };
""", 1),
    ("        themeRegistration = themes.subscribe(this::applyTheme);\n        host = registry.addHost(workspaceHost());\n",
     "        themeRegistration = themes.subscribe(this::applyTheme);\n"
     "        focusManager.addPropertyChangeListener(\"permanentFocusOwner\", focusOwners);\n"
     "        host = registry.addHost(workspaceHost());\n", 1),
    ("        themeRegistration.close();\n",
     "        themeRegistration.close();\n        focusManager.removePropertyChangeListener(\"permanentFocusOwner\", focusOwners);\n", 1),
    (r"""            for (TerminalTab tab : terminalTabs())
                for (TerminalPane pane : tab.panes())
                    listener.accept(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
""", r"""            for (TerminalContent terminal : terminalTabs())
                listener.accept(new WorkspaceActivity.PaneState(terminal.pane().id(), WorkspaceActivity.State.OPENED));
""", 1),
    (r"""    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (Tab tab : tabs()) if (tab.content() instanceof TerminalTab terminal) result.add(terminal);
""", r"""    /** This window's terminal tabs in workspace order. */
    public java.util.List<TerminalContent> terminalTabs() {
        var result = new ArrayList<TerminalContent>();
        for (Tab tab : tabs()) if (tab.content() instanceof TerminalContent terminal) result.add(terminal);
""", 1),
    (r"""    public TerminalTab currentTab() { Tab tab = selectedTab(); return tab != null && tab.content() instanceof TerminalTab terminal ? terminal : null; }
    public TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
""", r"""    /** The active tab's terminal; null while a plugin's tab is active or the workspace is empty. */
    public TerminalContent currentTerminal() {
        Tab tab = selectedTab();
        return tab != null && tab.content() instanceof TerminalContent terminal ? terminal : null;
    }
    public TerminalPane currentPane() { return currentTerminal() == null ? null : currentTerminal().pane(); }
""", 1),
    ("    TerminalTab openTab(", "    TerminalContent openTab(", 4),
    (r"""        if (closed) return null;
        TerminalTab terminal = new TerminalTab(directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(new TabId(terminal.id()), Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        attach(tab, true);
        terminal.start();
        return terminal;
    }
""", r"""        return openTerminal(directory, requestOrNull, profile, program, null, null);
    }

    /**
     * A terminal tab starting in {@code directory} (null: where a new shell starts), in the active group, or in a new
     * group on {@code side} of {@code beside} when that is given. Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side) {
        if (closed) return null;
        TabId tabId = TabId.random();
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        try {
            attach(tab, true, beside, side);
        } catch (RuntimeException | Error failure) {
            // attach mounted nothing; the registered tab and its pane go with the failure (workspace plan 3a Rulings 28).
            try { tab.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        terminal.start();
        return terminal;
    }

    /**
     * Split Right or Split Down (workspace tabs spec 11, workspace plan 3a Rulings 11): a terminal tab in a new group on
     * {@code side} of the active group; beside a running terminal in its directory with its profile, otherwise (beside a
     * plugin's view or a terminal whose shell ended) where a new tab from here starts, with the default profile. On an
     * empty workspace it opens in the empty group. Null once the window has closed.
     */
    public TerminalContent split(Side side) {
        TerminalContent target = currentTerminal();
        if (target != null && target.pane().running())
            return openBeside(target, side, null, null, target.pane().profile(), Optional.empty());
        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace.model().activeGroup(), side);
    }

    /**
     * A terminal tab in a new group on {@code side} of {@code target}'s group (spec 9.4), starting in
     * {@code directoryOrNull}, else where {@code target} is; a request makes it a provided session. Null when the target
     * is not this window's or its shell is not running.
     */
    TerminalContent openBeside(TerminalContent target, Side side, Path directoryOrNull, SessionRequest requestOrNull,
                               Optional<String> profile, Optional<java.util.List<String>> program) {
        Tab beside = target == null ? null : tab(target);
        if (closed || beside == null || !target.pane().running()) return null;
        GroupId group = workspace.model().groupOf(beside.id()).orElseThrow();
        Path directory = directoryOrNull == null ? target.pane().inheritableDirectory() : directoryOrNull;
        return openTerminal(directory, requestOrNull, profile, program, group, side);
    }

    /** Maximize Group: the active group fills the workspace, or the splits come back. */
    void toggleMaximize() { workspace.toggleMaximize(); }

    /** Focus Group: the neighbouring group that way takes focus with its selected tab; nothing past the edge. */
    void focusGroup(Side side) { workspace.neighbour(side).ifPresent(workspace::activate); }

    /** The terminal tab holding {@code pane}; null when none of this window's does. */
    TerminalContent terminalOf(TerminalPane pane) {
        for (TerminalContent terminal : terminalTabs()) if (terminal.pane() == pane) return terminal;
        return null;
    }

    /**
     * Focus went into {@code tab} (a click, a traversal, a focus request): it becomes the window's active tab and its
     * group the active group, while focus stays where it went (workspace plan 3a Rulings 26). Null, or a tab that is
     * already active, changes nothing.
     */
    void tabFocused(Tab tab) { if (!closed && tab != null) workspace.focused(tab); }

    /** The tab whose component holds {@code component}; null when none of this window's does. */
    Tab tabHolding(Component component) {
        for (Tab tab : tabs()) if (SwingUtilities.isDescendingFrom(component, tab.component())) return tab;
        return null;
    }
""", 1),
    (r"""    private void attach(Tab tab) { attach(tab, false); }

    /** As {@link #attach(Tab)}; with {@code opened} a new terminal tab's panes are announced as opened. */
    private void attach(Tab tab, boolean opened) {
        JComponent component = tab.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabOpened(terminal);
            tabHost.attach(tab, opened);
            workspace.open(tab);
""", r"""    private void attach(Tab tab) { attach(tab, false, null, null); }

    /**
     * As {@link #attach(Tab)}; with {@code opened} a new terminal tab's pane is announced as opened, and with
     * {@code beside} the tab opens in a new group on {@code side} of that group.
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side) {
        JComponent component = tab.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            if (tab.content() instanceof TerminalContent terminal && terminals != null) terminals.tabOpened(terminal);
            tabHost.attach(tab, opened);
            if (beside == null) workspace.open(tab); else workspace.openBeside(tab, beside, side);
""", 1),
    ("        if (tab.content() instanceof TerminalTab terminal && terminals != null) terminals.tabClosed(terminal);",
     "        if (tab.content() instanceof TerminalContent terminal && terminals != null) terminals.tabClosed(terminal);", 1),
    ("            if (tab.content() instanceof TerminalTab terminal) terminal.setActive(active && tab == selected);",
     "            if (tab.content() instanceof TerminalContent terminal) terminal.setActive(active && tab == selected);", 1),
    ("        return tab.content() instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running();",
     "        return tab.content() instanceof TerminalContent terminal && terminal.pane().running();", 1),
    ("        for (TerminalTab tab : terminalTabs()) tab.setBackgroundOpacity(value);",
     "        for (TerminalContent terminal : terminalTabs()) terminal.setBackgroundOpacity(value);", 1),
])
span(W, "    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {",
     "            retained.forEach(TerminalTab::endThemeUpdate);\n        }\n    }\n",
     r"""    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        if (closed) return;
        if (updateDelegates) SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
        if (updateDelegates && (bindingRoot == null || menuBar().getParent() == null)) SwingUtilities.updateComponentTreeUI(menuBar());
        for (DockStripe stripe : stripes.all()) stripe.refreshTheme();
        area.refreshTheme();
        area.setTerminalBackground(theme.surface());
        // A group the maximize keeps out of the tree updates its delegates there; split positions follow the weights.
        workspace.applyTheme(theme, updateDelegates);
        for (Tab tab : new ArrayList<>(tabListeners.keySet()))
            if (tab.kind() == Tab.Kind.HOSTED) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));
        if (updateDelegates && contributed != null) contributed.refreshTheme();
        for (TerminalContent terminal : terminalTabs()) {
            WorkspaceTheme.apply(terminal, theme);
            terminal.setBackground(theme.surface());
            terminal.pane().applyTheme(theme);
        }
        setBackground(theme.surface());
        chrome.refreshTheme();
        if (commandPalette != null) commandPalette.refreshTheme();
        onThemeChanged.accept(theme); update();
        requestGlass();
        revalidate(); repaint();
    }
""")
PY
grep -n 'TerminalTab\b' jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java && exit 1 || true
```

The last line prints nothing. The other workspace classes:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span, regex
A = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(A + "TerminalPane.java", [(
    "     * This pane took keyboard focus. Separate from {@link #onFocused}, which the tab owns to track\n"
    "     * its active pane; this one is for whoever cares that the user is now looking here.\n",
    "     * This pane took keyboard focus. Separate from {@link #onFocused}, with which its window makes the pane's tab\n"
    "     * the active tab; this one is for whoever cares that the user is now looking here.\n", 1)])
edit(A + "WorkspaceView.java", [("    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }\n",
    """    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }

    /**
     * Focus went into {@code tab}: it becomes the active tab without being focused again, so focus stays where it went
     * (Rulings 26); nothing changes while it already is the active tab, so no focus request loops back here.
     */
    void focused(Tab tab) { if (contains(tab) && selected() != tab) apply(layout.select(tab.id()), false); }
""", 1)])
edit(A + "WorkspaceActions.java", [
    ("import dev.jasper.app.commands.ActionId;\n", "import dev.jasper.app.commands.ActionId;\nimport dev.jasper.app.layout.Side;\n", 1),
    ("        TerminalTab tab = owner.currentTab();\n", "        TerminalContent terminal = owner.currentTerminal();\n", 1),
    ("""            case CLOSE_TAB -> owner.closeTab(tab);
            case CLOSE_PANE -> tab.closePane(pane);
            case SPLIT_RIGHT -> tab.split(SplitTree.Axis.RIGHT);
            case SPLIT_DOWN -> tab.split(SplitTree.Axis.DOWN);
            case ZOOM_PANE -> tab.toggleZoom();
            case TOGGLE_ZEN_MODE -> owner.toggleZenMode();
            case FOCUS_PANE_LEFT -> tab.navigate(SplitTree.Direction.LEFT);
            case FOCUS_PANE_RIGHT -> tab.navigate(SplitTree.Direction.RIGHT);
            case FOCUS_PANE_UP -> tab.navigate(SplitTree.Direction.UP);
            case FOCUS_PANE_DOWN -> tab.navigate(SplitTree.Direction.DOWN);
""", """            case CLOSE_TAB, CLOSE_PANE -> owner.closeTab(owner.selectedTab());
            case SPLIT_RIGHT -> owner.split(Side.RIGHT);
            case SPLIT_DOWN -> owner.split(Side.DOWN);
            case ZOOM_PANE -> owner.toggleMaximize();
            case TOGGLE_ZEN_MODE -> owner.toggleZenMode();
            case FOCUS_PANE_LEFT -> owner.focusGroup(Side.LEFT);
            case FOCUS_PANE_RIGHT -> owner.focusGroup(Side.RIGHT);
            case FOCUS_PANE_UP -> owner.focusGroup(Side.UP);
            case FOCUS_PANE_DOWN -> owner.focusGroup(Side.DOWN);
""", 1),
    ("""                String name = owner.askTabName(tab.title());
                if (name != null) tab.rename(name);
""", """                String name = owner.askTabName(terminal.title());
                if (name != null) terminal.rename(name);
""", 1),
    ("                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, TOGGLE_ZEN_MODE -> true;\n"
     "                    case SPLIT_RIGHT, SPLIT_DOWN, PASTE -> running;\n",
     "                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, TOGGLE_ZEN_MODE, SPLIT_RIGHT, SPLIT_DOWN -> true;\n"
     "                    case PASTE -> running;\n", 1),
    ("                    default -> present;\n", """                    case ZOOM_PANE -> owner.workspace().model().groups().size() > 1;
                    case FOCUS_PANE_LEFT -> owner.workspace().neighbour(Side.LEFT).isPresent();
                    case FOCUS_PANE_RIGHT -> owner.workspace().neighbour(Side.RIGHT).isPresent();
                    case FOCUS_PANE_UP -> owner.workspace().neighbour(Side.UP).isPresent();
                    case FOCUS_PANE_DOWN -> owner.workspace().neighbour(Side.DOWN).isPresent();
                    default -> present;
""", 1)])
edit(A + "WindowCommands.java", [(
    "            owner.currentTab() != null && owner.currentTab().tree().zoomed() ? \"Restore Pane\" : \"Zoom Pane\");",
    "            owner.workspace().model().maximized().isPresent() ? \"Restore Pane\" : \"Zoom Pane\");", 1)])
edit(A + "WindowCommandPalette.java", [("    private TerminalTab originTab;", "    private TerminalContent originTab;", 1),
    ("originTab != null && originTab.panes().contains(originPane)", "originTab != null && originTab.pane() == originPane", 1)])
regex(A + "WindowCommandPalette.java", r"\bcurrentTab\(\)", "currentTerminal()", 3)
span(A + "WorkspaceConfiguration.java", "            for (TerminalTab tab : owner.terminalTabs()) {",
     "                    if (exitChanged) pane.setShellExitBehavior(next.terminal().onExit());\n                }\n            }\n",
     """            for (TerminalContent terminal : owner.terminalTabs()) {
                TerminalPane pane = terminal.pane();
                if (optionsChanged && pane.view() != null) {
                    float size = sizeChanged ? next.fontSize() : pane.view().fontSize();
                    pane.view().applyOptions(pane.view().options().toBuilder()
                        .fontFamily(next.font().family()).fontSize(size).fallbackFonts(next.font().fallback())
                        .ligatures(next.font().ligatures()).lineHeight(next.font().lineHeight())
                        .cursorStyle(next.terminal().cursorShape()).cursorBlink(next.terminal().cursorBlink())
                        .optionAsMeta(next.terminal().optionAsMeta()).scrollback(next.terminal().scrollback())
                        .copyOnSelect(next.terminal().copyOnSelect()).bell(next.terminal().bell()).build());
                }
                if (dimChanged) pane.setConfiguredDim(next.terminal().dimInactivePanes());
                if (exitChanged) pane.setShellExitBehavior(next.terminal().onExit());
            }
""")
T = "jasper-app/src/main/java/dev/jasper/app/terminals/"
edit(T + "PaneEntry.java", [
    ("package dev.jasper.app.terminals;\n\n", "package dev.jasper.app.terminals;\n\nimport dev.jasper.app.layout.Side;\n", 1),
    (" * its future. {@code split} returns the new pane, or empty when this pane cannot be split now.",
     " * its future. {@code split} opens a terminal tab in a new group on that side of the pane's group (workspace tabs spec\n"
     " * 9.4) and returns its pane, or empty when this pane cannot be split now.", 1),
    ("BiFunction<SplitAxis, OpenSpec, Optional<PaneEntry>> split) {", "BiFunction<Side, OpenSpec, Optional<PaneEntry>> split) {", 1)])
edit(T + "package-info.java", [("Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.",
                                "Allowed outgoing Jasper dependencies: dev.jasper.app.layout, dev.jasper.app.lifecycle.", 1)])
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "HostedTerminals.java", [
    ("import dev.jasper.app.terminals.SplitAxis;\n", "import dev.jasper.app.layout.Side;\n", 1),
    ("direction == Direction.RIGHT ? SplitAxis.RIGHT : SplitAxis.DOWN", "direction == Direction.RIGHT ? Side.RIGHT : Side.DOWN", 1)])
edit(P + "package-info.java", [("dev.jasper.app.launch, dev.jasper.app.notifications,",
                                "dev.jasper.app.launch, dev.jasper.app.layout, dev.jasper.app.notifications,", 1)])
B = "jasper-app/src/main/java/dev/jasper/app/benchmark/"
edit(B + "package-info.java", [("dev.jasper.app.launch, dev.jasper.app.startup,",
                                "dev.jasper.app.launch, dev.jasper.app.layout, dev.jasper.app.startup,", 1)])
edit(B + "BenchmarkRun.java", [
    ("import dev.jasper.app.workspace.SplitTree;\n", "import dev.jasper.app.layout.Side;\n", 1),
    ("import dev.jasper.app.workspace.TerminalTab;\n", "import dev.jasper.app.workspace.TerminalContent;\n", 1),
    ("edt(() -> { window.content().currentTab().split(index % 2 == 0 ? SplitTree.Axis.DOWN : SplitTree.Axis.RIGHT); return null; });",
     "edt(() -> { window.content().split(index % 2 == 0 ? Side.DOWN : Side.RIGHT); return null; });", 1),
    ("""            TerminalTab temporaryTab = edt(() -> { window.content().newTab(directory); return window.content().currentTab(); });
            ready(window, 1);
            edt(() -> { temporaryTab.split(SplitTree.Axis.RIGHT); return null; }); ready(window, 2);
            edt(() -> { window.content().closeTab(temporaryTab); return null; });
""", """            int before = edt(() -> panes(window).size());
            TerminalContent temporaryTab = edt(() -> { window.content().newTab(directory); return window.content().currentTerminal(); });
            ready(window, before + 1);
            TerminalContent temporarySplit = edt(() -> window.content().split(Side.RIGHT)); ready(window, before + 2);
            edt(() -> { window.content().closeTab(temporarySplit); window.content().closeTab(temporaryTab); return null; });
""", 1),
    ("""    private void retireChildrenExcept(Set<Child> retained) throws Exception {""",
     """    /** Every terminal pane of {@code owner}: one per terminal tab, in workspace order. */
    private static List<TerminalPane> panes(TerminalWindow owner) {
        return owner.content().terminalTabs().stream().map(TerminalContent::pane).toList();
    }

    private void retireChildrenExcept(Set<Child> retained) throws Exception {""", 1)])
regex(B + "BenchmarkRun.java", r"window\.content\(\)\.currentTab\(\)\.panes\(\)", "panes(window)", 4)
regex(B + "BenchmarkRun.java", r"owner\.content\(\)\.currentTab\(\)\.panes\(\)", "panes(owner)", 2)
# Plan 2's layout comments stop naming SplitTree, which goes here.
edit("jasper-app/src/main/java/dev/jasper/app/layout/Layout.java", [
    ("over the unit square, by today's {@code SplitTree.navigate} rule:\n", "over the unit square:\n", 1),
    ("    /** Distances this close are equal: today's {@code SplitTree} tolerance. */\n", "    /** Distances this close are equal. */\n", 1)])
PY
grep -rn 'TerminalTab\b\|SplitTree\|SplitAxis\|currentTab()' jasper-app/src/main && exit 1 || true
```

The last line prints nothing.

- [ ] **Step 5: Move the existing tests to one pane per tab**

`currentTab()` becomes `currentTerminal()` and `TerminalTab` becomes `TerminalContent` everywhere in the tests; then the
tests that split, zoomed or walked a tab's panes move to groups:

```bash
python3 -I - <<'PY'
import pathlib, re, sys
sys.path.insert(0, "build/plan3a"); from edits import edit, span, regex
R = pathlib.Path("jasper-app/src/test")
for path in R.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    moved = re.sub(r"\bTerminalTab\b", "TerminalContent", re.sub(r"\bcurrentTab\(\)", "currentTerminal()", text))
    if moved != text: path.write_text(moved, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
X = "dev.jasper.app.layout.Side"
edit(T + "ShellExitBehaviorTest.java", [
    ("import dev.jasper.app.workspace.SplitTree;\n", "", 1),
    ("            assertThat(tab.panes()).hasSize(closes ? 0 : 1);\n", "            assertThat(tab.closed()).isEqualTo(closes);\n", 1),
    ("            assertThat(owner.currentTerminal().panes()).hasSize(1);\n", "            assertThat(owner.currentTerminal().closed()).isFalse();\n", 1),
    ("            assertThat(first.panes()).hasSize(1);\n            assertThat(first.focusedPane().running()).isTrue();\n",
     "            assertThat(first.closed()).isFalse();\n            assertThat(first.pane().running()).isTrue();\n", 1),
    ("dev.jasper.app.terminals.SplitAxis.RIGHT", X + ".RIGHT", 1)])
span(T + "ShellExitBehaviorTest.java", "    @Test void hiddenZoomedAndInactiveExitsPreserveSiblingSessionsAndSelection() throws Exception {",
     "        until(() -> empty.get() == 1);\n    }\n", """    @Test void hiddenMaximizedAndInactiveExitsPreserveOtherSessionsAndSelection() throws Exception {
        start("keep_open", false, false); deliver();
        TerminalPane hidden = owner.currentPane();
        edt(() -> owner.invoke(dev.jasper.app.commands.ActionId.SPLIT_RIGHT)); deliver();
        TerminalPane sibling = owner.currentPane();
        edt(() -> { owner.invoke(dev.jasper.app.commands.ActionId.ZOOM_PANE); owner.newTab(directory); }); deliver();
        TerminalContent selected = owner.currentTerminal(); TerminalPane selectedPane = owner.currentPane();
        reload("close");
        edt(() -> exit(hidden, 0));
        until(() -> owner.tabCount() == 2);
        edt(() -> {
            assertThat(owner.terminalOf(hidden)).as("the hidden group's tab closed").isNull();
            assertThat(sibling.running()).isTrue();
            assertThat(selectedPane.running()).isTrue();
            assertThat(owner.currentTerminal()).isSameAs(selected);
            assertThat(empty).hasValue(0);
            exit(sibling, 7);
        });
        until(() -> owner.tabCount() == 1);
        edt(() -> {
            assertThat(owner.currentTerminal()).isSameAs(selected);
            assertThat(selectedPane.running()).isTrue();
            assertThat(empty).hasValue(0);
            exit(selectedPane, 0);
        });
        until(() -> empty.get() == 1);
    }
""")
edit(T + "ShellProfilePaneTest.java", [
    ("        edt(() -> owner[0].currentTerminal().split(SplitTree.Axis.RIGHT));\n", f"        edt(() -> owner[0].split({X}.RIGHT));\n", 1),
    ("        edt(() -> owner[0].currentTerminal().split(SplitTree.Axis.DOWN));\n", f"        edt(() -> owner[0].split({X}.DOWN));\n", 1),
    ("            owner[0].currentTerminal().split(SplitTree.Axis.RIGHT);\n", f"            owner[0].split({X}.RIGHT);\n", 1),
    ('            wsl[0] = tab.split(tab.focusedPane(), SplitTree.Axis.DOWN, null, null, Optional.of("wsl:Ubuntu"));\n',
     f'            wsl[0] = owner[0].openBeside(tab, {X}.DOWN, null, null, Optional.of("wsl:Ubuntu"), Optional.empty()).pane();\n', 1),
    ("        edt(() -> assertThat(owner[0].currentTerminal().split(wsl[0], SplitTree.Axis.RIGHT, null, null)).isNotNull());\n",
     f"""        edt(() -> {{
            TerminalContent target = owner[0].terminalOf(wsl[0]);
            assertThat(owner[0].openBeside(target, {X}.RIGHT, null, null, target.pane().profile(), Optional.empty())).isNotNull();
        }});
""", 1)])
span(T + "ShortcutContextDispatchTest.java", "    @Test void closePaneClosesTheFocusedPaneThenTheTabWithItsLastPane() throws Exception {",
     "            assertThat(owner[0].tabCount()).isEqualTo(1);\n        });\n    }\n",
     """    @Test void closePaneClosesTheActiveTabAndAGroupGoesWithItsLastTab() throws Exception {
        // The launcher's queued work runs between the steps, so the split inherits from a running shell, as in TerminalTabTest.
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending), true); owner[0].newTab(HOME); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(owner[0].workspace().model().groups()).hasSize(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(owner[0].workspace().model().groups()).as("the split's group goes with its tab").hasSize(1);
            assertThat(owner[0].tabCount()).isEqualTo(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(owner[0].tabCount()).isEqualTo(1);
        });
    }
""")
edit(T + "SessionPaneTest.java", [
    (".focusedPane();", ".pane();", 2),
    ("""                var tab = owner.currentTerminal();
                tab.split(SplitTree.Axis.RIGHT);
                assertThat(field[0].contextText()).isNotEqualTo("Claude Code — Reviewing files");
                tab.focus(pane);
""", f"""                var tab = owner.currentTerminal();
                owner.split({X}.RIGHT);
                assertThat(field[0].contextText()).isNotEqualTo("Claude Code — Reviewing files");
                owner.selectTab(tab);
""", 1)])
edit(T + "NetworkShareDirectoryTest.java", [
    ("""    private static TerminalPane split(TerminalContent tab) throws Exception {
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = tab.split(tab.focusedPane(), SplitTree.Axis.RIGHT, null));
""", f"""    private static TerminalPane split(WindowContent owner, TerminalContent tab) throws Exception {{
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner.openBeside(tab, {X}.RIGHT, null, null, tab.pane().profile(), Optional.empty()).pane());
""", 1),
    ("tab[0].focusedPane()", "tab[0].pane()", 6),
    ("TerminalPane split = split(tab[0]);", "TerminalPane split = split(owner, tab[0]);", 3)])
edit(T + "ConfigurationControllerTest.java", [
    ("        edt(() -> { tab.toggleZoom(); owner.newTab(HOME); });\n", "        edt(() -> { owner.toggleMaximize(); owner.newTab(HOME); });\n", 1),
    ("            assertThat(tab.tree().zoomed()).isTrue();\n            assertThat(retained.view().selectedText()).contains(\"alpha\");\n",
     "            assertThat(owner.workspace().model().maximized()).isPresent();\n            assertThat(retained.view().selectedText()).contains(\"alpha\");\n", 1),
    ("        edt(() -> { tab.toggleZoom(); first.newTab(HOME); });\n", "        edt(() -> { first.toggleMaximize(); first.newTab(HOME); });\n", 1),
    ("            assertThat(tab.tree().zoomed()).isTrue();\n            assertThat(retained.session()).isSameAs(session);\n",
     "            assertThat(first.workspace().model().maximized()).isPresent();\n            assertThat(retained.session()).isSameAs(session);\n", 1),
    ("            first.selectTab(tab); tab.toggleZoom(); tab.focus(retained);\n",
     "            first.selectTab(tab); first.toggleMaximize(); first.selectTab(tab);\n", 1)])
edit(T + "WindowTerminalsTest.java", [
    ("import dev.jasper.app.terminals.SplitAxis;\n", "import dev.jasper.app.layout.Side;\n", 1),
    ("pane.split().apply(SplitAxis.RIGHT, new OpenSpec.Local(Optional.empty()))", "pane.split().apply(Side.RIGHT, new OpenSpec.Local(Optional.empty()))", 1),
    ("""        Optional<PaneEntry> split = onEdt(() -> first.split().apply(SplitAxis.DOWN, new OpenSpec.Local(Optional.empty())));
        assertThat(split).isPresent();
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.PaneOpened(first.tabId(), split.get().id()));
            assertThat(registry.tab(first.tabId()).orElseThrow().panes().get()).containsExactly(first, split.get());
        });
""", """        Optional<PaneEntry> split = onEdt(() -> first.split().apply(Side.DOWN, new OpenSpec.Local(Optional.empty())));
        assertThat(split).isPresent();
        edt(() -> {
            UUID splitTab = split.get().tabId();
            assertThat(splitTab).as("a split is a terminal tab of its own").isNotEqualTo(first.tabId());
            assertThat(events).contains(new TerminalEvent.TabOpened(windowId, splitTab), new TerminalEvent.PaneOpened(splitTab, split.get().id()));
            assertThat(registry.tab(splitTab).orElseThrow().panes().get()).containsExactly(split.get());
            assertThat(registry.tab(first.tabId()).orElseThrow().panes().get()).containsExactly(first);
            assertThat(owner.workspace().model().groups()).as("in a new group below").hasSize(2);
        });
""", 1),
    ("                new TerminalEvent.TabSelected(windowId, first.tabId()),\n",
     "                new TerminalEvent.TabSelected(windowId, split.get().tabId()),\n", 1)])
edit(T + "WindowCommandPaletteTest.java", [(
"""                content.currentTerminal().split(SplitTree.Axis.RIGHT); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); content.currentTerminal().focus(origin); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(content.commandPalette().component()).setText("zoom");
                content.currentTerminal().toggleZoom();
                assertThat(content.commandPalette().isOpen()).isTrue();
                assertThat(content.action(ActionId.ZOOM_PANE).getValue(Command.TITLE)).isEqualTo("Restore Pane");
                content.currentTerminal().closePane(origin); assertThat(content.commandPalette().isOpen()).isFalse();
""", f"""                content.split({X}.RIGHT); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); content.selectTab(content.terminalOf(origin)); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(content.commandPalette().component()).setText("zoom");
                content.toggleMaximize();
                assertThat(content.commandPalette().isOpen()).isTrue();
                assertThat(content.action(ActionId.ZOOM_PANE).getValue(Command.TITLE)).isEqualTo("Restore Pane");
                content.closeTab(content.terminalOf(origin)); assertThat(content.commandPalette().isOpen()).isFalse();
""", 1)])
span(T + "WindowCommandPaletteTest.java", "            DesktopTestSupport.edt(() -> owner[0].currentTerminal().split(SplitTree.Axis.RIGHT));",
     "                assertThat(oldRequests.get()).isEqualTo(1);\n            });\n", f"""            DesktopTestSupport.edt(() -> owner[0].split({X}.RIGHT));
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {{
                var window = owner[0];
                var original = window.terminalTabs().getFirst().pane(); var destination = window.terminalTabs().getLast().pane();
                // Record requests on a real focusable child of the old pane. Like TerminalPane's
                // focus listener, accepting a stale request would select that pane again.
                var prior = new JTextField() {{
                    @Override public boolean requestFocusInWindow() {{
                        oldRequests.incrementAndGet(); window.selectTab(window.terminalOf(original)); return true;
                    }}
                }};
                prior.removeCaretListener((javax.swing.event.CaretListener) prior.getAccessibleContext());
                // Accessible text location callbacks require a native screen peer, which this
                // lightweight fixture deliberately does not create.
                for (var listener : prior.getComponentListeners()) prior.removeComponentListener(listener);
                original.add(prior, BorderLayout.NORTH);
                var query = dev.jasper.app.palette.PaletteTestSupport.query(owner[0].commandPalette().component());
                query.removeCaretListener((javax.swing.event.CaretListener) query.getAccessibleContext());
                for (var listener : query.getComponentListeners()) query.removeComponentListener(listener);
                var root = SwingUtilities.getRootPane(owner[0]);
                host[0] = new JPanel(); host[0].add(root); host[0].addNotify(); LayoutTestSupport.layoutTree(root);
                assertThat(original.isShowing()).isTrue(); assertThat(destination.isShowing()).isTrue();
                assertThat(prior.isShowing()).isTrue(); assertThat(original.view().isShowing()).isTrue();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {{
                    @Override public Component getFocusOwner() {{ return prior; }}
                }});
                window.selectTab(window.terminalOf(original)); owner[0].commandPalette().toggle();
                if (navigate) window.focusGroup({X}.RIGHT); else window.selectTab(window.terminalOf(destination));
                assertThat(owner[0].commandPalette().isOpen()).isFalse();
                assertThat(oldRequests.get()).isZero();
                assertThat(window.currentPane()).isSameAs(destination);
                // Cancellation with a still-valid origin must retain normal synchronous restoration.
                window.selectTab(window.terminalOf(original)); owner[0].commandPalette().toggle(); owner[0].commandPalette().dismiss();
                assertThat(oldRequests.get()).isEqualTo(1);
            }});
""")
span(T + "WorkspaceGlassTest.java", "    @Test void splitsKeepASolidDividerBetweenTranslucentPanes() throws Exception {",
     "        } finally { edt(tab::close); }\n    }\n", """    @Test void splitsKeepASolidDividerBetweenTranslucentTabs() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(pending)));
        pending.remove().run();
        edt(() -> { owner[0].applyBackgroundOpacity(0.5); owner[0].invoke(dev.jasper.app.commands.ActionId.SPLIT_RIGHT); });
        pending.remove().run();
        edt(() -> {
            var split = owner[0].workspace().split(java.util.List.of());
            assertThat(split.isOpaque()).isFalse();
            assertThat(owner[0].terminalTabs()).hasSize(2).allMatch(tab -> !tab.isOpaque() && !tab.pane().isOpaque());
            owner[0].setSize(900, 500);
            LayoutTestSupport.layoutTree(owner[0]);
            LayoutTestSupport.layoutTree(owner[0]);
            var image = render(owner[0]);
            var divider = split.divider(0);
            assertThat(alpha(image, owner[0], divider, divider.getWidth() / 2, divider.getHeight() / 2)).as("divider").isEqualTo(255);
            for (TerminalContent tab : owner[0].terminalTabs())
                assertThat(alpha(image, owner[0], tab.pane(), 2, 2)).as("pane padding").isEqualTo(128);
            owner[0].applyBackgroundOpacity(1);
            assertThat(split.isOpaque()).isTrue();
            assertThat(owner[0].terminalTabs()).allMatch(tab -> tab.isOpaque() && tab.pane().isOpaque());
        });
    }
""")
edit(T + "WorkspaceGlassTest.java", [("            assertThat(tab.panes()).allMatch(pane -> !pane.isOpaque());\n",
                                       "            assertThat(tab.pane().isOpaque()).isFalse();\n", 1)])
edit(T + "WindowContentFirstTerminalTest.java", [("second[0].panes().getFirst().view()", "second[0].pane().view()", 1)])
edit(T + "WindowTabMotionTest.java", [("var pane = closing.focusedPane();", "var pane = closing.pane();", 1)])
for name in ("AlacrittyTabsPreview", "FlatTabsPreview", "FolderTabsPreview"):
    edit(T + name + ".java", [("tab -> tab.focusedPane().view() != null", "tab -> tab.pane().view() != null", 1)])
edit(T + "WorkspaceSurfacePreview.java", [("tab.focusedPane()", "tab.pane()", 3),
    ("for (TerminalPane pane : tab.panes()) {", "for (TerminalPane pane : java.util.List.of(tab.pane())) {", 1)])
edit(T + "FileLinkClickTest.java", [(".focusedPane());", ".pane());", 1)])
edit(T + "RemoteDirectoryPaneTest.java", [(".focusedPane();", ".pane();", 1)])
edit(T + "TabMoveTest.java", [(".focusedPane();", ".pane();", 3)])
edit(T + "HostedContentTest.java", [("assertThat(owner.action(ActionId.SPLIT_RIGHT).isEnabled()).isFalse();",
    'assertThat(owner.action(ActionId.SPLIT_RIGHT).isEnabled()).as("a split beside a plugin view (Rulings 11)").isTrue();', 1)])
PY
```

Rewrite the three split and zoom cases of `ThemeControllerTest` (quoted as the rename left them):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span
T = "jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java"
edit(T, [("import dev.jasper.app.workspace.SplitTree;\n", "", 1)])
span(T, "    void sharedOwnersUpdateHiddenZoomedAndLatePanesWithoutChangingTheirState() throws Exception {",
     "            owners[0].removeNotify();\n        });\n    }\n", """    void sharedOwnersUpdateHiddenMaximizedAndLatePanesWithoutChangingTheirState() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[3];
        ThemeController[] themes = new ThemeController[1];
        TerminalContent[] retained = new TerminalContent[1];
        TerminalPane[] first = new TerminalPane[1];
        TerminalPane[] sibling = new TerminalPane[1];
        TerminalSession[] session = new TerminalSession[1];
        edt(() -> {
            themes[0] = ThemeTestSupport.controller(true);
            owners[0] = content(launcher(pending), themes[0]);
            owners[1] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            retained[0] = owners[0].currentTerminal(); first[0] = owners[0].currentPane();
            session[0] = first[0].session();
            first[0].findBar().queryField().removeCaretListener((javax.swing.event.CaretListener)
                first[0].findBar().queryField().getAccessibleContext());
            owners[0].addNotify();
            first[0].view().setFontSize(23);
            first[0].findBar().open(); first[0].findBar().queryField().setText("[");
            first[0].findBar().regexButton().doClick();
            retained[0].rename("retained"); owners[0].split(dev.jasper.app.layout.Side.RIGHT);
        });
        pending.remove().run();
        until(() -> first[0].findBar().result().error() != null);
        edt(() -> {
            sibling[0] = owners[0].currentPane();
            owners[0].toggleMaximize();
            owners[0].newTab(HOME); // completion deliberately waits across theme selection
            owners[1].selectVariant(false);
            assertThat(themes[0].current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(false));
            for (TerminalPane pane : java.util.List.of(first[0], sibling[0])) {
                assertThat(pane.view().palette().background()).isEqualTo(ThemeTestSupport.terminal(false).background());
                // The invalid pattern keeps its no-match tint, in the new theme's colour.
                assertThat(pane.findBar().queryField().getBackground()).isEqualTo(pane == first[0] ? dev.jasper.ui.theme.ColorMath.mix(dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.BORDER_ERROR), dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT), .16)
                    : dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
            }
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(first[0].view().fontSize()).isEqualTo(23);
            assertThat(first[0].findBar().isVisible()).isTrue();
            assertThat(first[0].findBar().queryField().getText()).isEqualTo("[");
            assertThat(first[0].findBar().result().error()).isNotNull();
            assertThat(owners[0].workspace().model().maximized()).isPresent();
            assertThat(javax.swing.SwingUtilities.isDescendingFrom(first[0], owners[0].workspace())).as("the maximize hides its group").isFalse();
            assertThat(retained[0].title()).isEqualTo("retained");
            assertThat(owners[0].currentTerminal()).isNotSameAs(retained[0]);
            assertThat(themes[0].current().chrome().dark()).isFalse();
            assertThat(owners[1].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            owners[2] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            assertThat(owners[2].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            var query = first[0].findBar().queryField();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.COPY).orElseThrow(), query)).isFalse();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.PASTE).orElseThrow(), query)).isFalse();
            owners[0].selectTab(retained[0]);
            assertThat(first[0].view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            owners[1].selectVariant(true);
            assertThat(first[0].view().getBackground()).isEqualTo(ThemeTestSupport.terminal(true).background());
            assertThat(first[0].findBar().result().error()).isNotNull();
            for (TerminalPane pane : java.util.List.of(first[0], sibling[0])) assertThat(pane.view().palette()).isEqualTo(ThemeTestSupport.terminal(true));
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(owners[0].workspace().model().maximized()).as("selecting the hidden tab moved the maximize to its group").isPresent();
            assertThat(themes[0].current().chrome().dark()).isTrue();
            owners[0].removeNotify();
        });
    }
""")
span(T, "    void delegateUpdatesRetainTheExistingSplitComponentsAndModelRatios() throws Exception {",
     "            assertThat(split[0].getDividerLocation()).isBetween(685, 705);\n        });\n    }\n",
     """    void delegateUpdatesRetainTheSplitContainerAndTheLayoutsWeights() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT));
        pending.remove().run();
        edt(() -> {
            owner[0].setSize(1200, 700); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner[0]);
            owner[0].workspace().resizeSplit(java.util.List.of(), java.util.List.of(0.7, 0.3));
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner[0]);
            var split = owner[0].workspace().split(java.util.List.of());
            var left = split.children().get(0).getBounds();
            var before = owner[0].workspace().model();
            owner[0].selectVariant(false);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner[0]);
            assertThat(owner[0].workspace().split(java.util.List.of())).as("a theme change rebuilds no split").isSameAs(split);
            assertThat(owner[0].workspace().model()).isEqualTo(before);
            assertThat(split.children().get(0).getBounds()).as("nor moves a divider").isEqualTo(left);
        });
    }
""")
span(T, "    void nestedSplitsKeepModelRatiosAndDividerPositionsAcrossRootDelegateChanges() throws Exception {",
     "            assertThat(root[0].getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(copy)).isEqualTo(ActionId.COPY.id());\n        });\n    }\n",
     """    void nestedSplitsKeepTheirWeightsAndPositionsAcrossRootDelegateChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        JRootPane[] root = new JRootPane[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN)); pending.remove().run();
        edt(() -> {
            root[0] = new JRootPane(); root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
            owner[0].installRootBindings(root[0]);
            root[0].setSize(1200, 700); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root[0]);
            owner[0].workspace().resizeSplit(java.util.List.of(), java.util.List.of(0.63, 0.37));
            owner[0].workspace().resizeSplit(java.util.List.of(1), java.util.List.of(0.31, 0.69));
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root[0]);
            var bounds = owner[0].workspace().groupViews().stream()
                .map(view -> javax.swing.SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), root[0])).toList();
            var before = owner[0].workspace().model();
            owner[0].selectVariant(false);
            owner[0].selectVariant(true);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root[0]);
            assertThat(owner[0].workspace().model()).isEqualTo(before);
            assertThat(owner[0].workspace().groupViews().stream()
                .map(view -> javax.swing.SwingUtilities.convertRectangle(view.getParent(), view.getBounds(), root[0])).toList())
                .isEqualTo(bounds);
            var copy = owner[0].bindings().strokeFor(ActionId.COPY).orElseThrow();
            assertThat(root[0].getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(copy)).isEqualTo(ActionId.COPY.id());
        });
    }
""")
PY
grep -rnE '\bTerminalTab\b|SplitTree\b|SplitAxis|currentTab\(\)' jasper-app/src && exit 1 || true
```

The last line prints nothing. (`.focusedPane()` stays on `TabEntry` in `WindowTerminalsTest` and `ShellExitBehaviorTest`,
where it is the registry entry's; `SplitTree\b` passes over plan 2's `LayoutGeometryTest`, whose comments and one test
name cite the `SplitTreeTest` cases it ports.)

- [ ] **Step 6: Ledger**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
M, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.rename(M + "TerminalTab.java", M + "TerminalContent.java", "TerminalContent")
ledger.drop(M + "SplitTree.java", T + "WorkspaceTabsTest.java")
ledger.drop(T + "SplitTreeTest.java", T + "WorkspaceViewTest.java")
ledger.add(T + "WorkspaceTabsTest.java", "app.workspace", "WorkspaceTabsTest", T + "WorkspaceTabsTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 3a Task 5; terminal tabs in groups")
PY
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.terminals.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.benchmark.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`WorkspaceTabsTest` 10, `TerminalTabTest` 9, `TabStateTest` 3, `TabMoveTest` 6,
`ApplicationActionsTest.focusingFindFieldRetargetsGlobalPaneActions` through the pane's focus hook). Then `./gradlew :jasper-app:check`: PASS
(`verifyApplicationArchitecture` with the `terminals`, `plugins` and `benchmark` edges to `layout`).

- [ ] **Step 8: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Splits become groups: a terminal tab is one pane

TerminalContent replaces TerminalTab; Cmd+D and Cmd+Shift+D open a terminal
tab in a new group in the active terminal's directory and profile; close,
zoom and focus act on tabs and groups; dimming follows the active group;
the terminal registry splits into groups. TerminalTab, SplitTree and
SplitAxis are gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Commands, keys and menus

Spec 11 and 16: `close_tab` takes ⌘W / Ctrl+Shift+W and absorbs `close_pane`; `close_group` takes ⌥⌘W /
Ctrl+Alt+Shift+W; `maximize_group` replaces `zoom_pane` and `focus_group_*` replace `focus_pane_*`, with the same keys.
The tab menu reads Rename Tab, Open in Separate Window or Move to Main Window, Close Tab, Close Others, Close Group, then
the content's commands (Rulings 13, 14). Menus, the toolbar, the palette and the keymap page name tabs and groups
(Rulings 15, 16).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java:12-23`, `workspace/WorkspaceActions.java`,
  `workspace/WindowCommands.java:43,54,64-67,137-138`, `workspace/WindowChrome.java:80,91,125,437-438,475,567`,
  `workspace/ClassicHeader.java:46`, `shortcuthelp/ShortcutCatalog.java:81,87-88`, `workspace/WindowContent.java`
  (the tab menu and the batch closes)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabMenuTest.java` (new); `config/KeymapDefaultsTest.java`,
  `config/KeyBindingsTest.java`, `config/ConfigTemplateTest.java`; the label and id updates in Step 4
- Ledger: one row added

**Interfaces:**
- Consumes: Task 3's `WorkspaceView.groupView`, `GroupView.tabs()`, `WindowContent.closeTab(Tab)`; Task 1's
  `TabRegistry.requestClose(List<Tab>)`; Task 5's `toggleMaximize`, `focusGroup(Side)`.
- Produces: `ActionId.CLOSE_GROUP` (`close_group`), `ActionId.MAXIMIZE_GROUP` (`maximize_group`),
  `ActionId.FOCUS_GROUP_LEFT/RIGHT/UP/DOWN` (`focus_group_left` …); `CLOSE_PANE`, `ZOOM_PANE` and `FOCUS_PANE_*` are
  gone. On `WindowContent` (package-private): `JPopupMenu tabMenu(Tab)`, `void closeOthers(Tab)`,
  `void closeGroup(Tab)`, `void closeTabs(java.util.List<Tab>)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TabMenuTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.ContentCommand;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 11: the tab menu, Close Others, Close Group and the menus (workspace plan 3a Task 6). */
class TabMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with {@code count} running terminal tabs in one group. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            for (int i = 1; i < count; i++) owner[0].newTab(HOME);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    private static List<String> titles(JPopupMenu menu) {
        var titles = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) titles.add(item.getText());
        return titles;
    }

    private static JMenuItem item(JPopupMenu menu, String title) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(title)) return item;
        throw new AssertionError("no " + title + " in " + titles(menu));
    }

    /** The plain items of the menu bar's menu called {@code title}; its submenus are left out. */
    private static List<String> items(WindowContent window, String title) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !title.equals(menu.getText())) continue;
            var items = new ArrayList<String>();
            for (var child : menu.getMenuComponents())
                if (child instanceof JMenuItem item && !(child instanceof JMenu)) items.add(item.getText());
            return items;
        }
        throw new AssertionError("no " + title + " menu");
    }

    @Test void aTerminalTabsMenuRenamesAndClosesAndAPluginTabsMenuMovesThenListsItsCommands() throws Exception {
        var window = window(1);
        edt(() -> {
            var keys = window.bindings();
            var menu = window.tabMenu(window.selectedTab());
            assertThat(titles(menu)).containsExactly("Rename Tab", "Close Tab", "Close Others", "Close Group");
            assertThat(item(menu, "Rename Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.RENAME_TAB).orElseThrow());
            assertThat(item(menu, "Close Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            assertThat(item(menu, "Close Group").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_GROUP).orElseThrow());
            assertThat(item(menu, "Close Others").isEnabled()).as("the group has no other tab").isFalse();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            int[] saved = {0};
            view.commands(List.of(new ContentCommand("save", "Save", null, true, () -> saved[0]++)));
            var hosted = window.tabMenu(view);
            assertThat(titles(hosted)).containsExactly("Open in Separate Window", "Close Tab", "Close Others", "Close Group", "Save");
            assertThat(item(hosted, "Close Others").isEnabled()).as("the terminal tab shares its group").isTrue();
            item(hosted, "Save").doClick();
            assertThat(saved[0]).isEqualTo(1);
        });
    }

    @Test void closeOthersClosesTheRestOfItsGroupAndLeavesTheOtherGroups() throws Exception {
        var window = window(4);
        edt(() -> {
            var workspace = window.workspace();
            var b1 = window.tabs().get(3);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1); var a3 = window.tabs().get(2);
            item(window.tabMenu(a2), "Close Others").doClick();
            assertThat(window.tabs()).containsExactly(a2, b1);
            assertThat(a1.closed()).isTrue();
            assertThat(a3.closed()).isTrue();
            assertThat(workspace.model().groups()).hasSize(2);
            assertThat(((TerminalContent) b1.content()).pane().running()).isTrue();
        });
    }

    @Test void closeGroupAsksTheGuardsFirstAndARefusalClosesNothing() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            var other = window.tabs().get(1);
            workspace.moveToNewGroup(other, workspace.model().activeGroup(), Side.RIGHT);
            var terminal = window.tabs().get(0);
            window.selectTab(terminal);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            assertThat(workspace.model().groupOf(view.id())).isEqualTo(workspace.model().groupOf(terminal.id()));
            var asked = new ArrayList<List<Tab>>();
            boolean[] allow = {false};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            window.invoke(ActionId.CLOSE_GROUP);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(terminal.closed()).as("a refusal closes nothing").isFalse();
            assertThat(window.tabs()).containsExactly(terminal, view, other);
            allow[0] = true;
            window.invoke(ActionId.CLOSE_GROUP);
            assertThat(asked).hasSize(2);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(workspace.model().groups()).as("the group went with its tabs").hasSize(1);
        });
    }

    @Test void closeTabAndCloseGroupRunFromTheirShortcuts() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var b1 = window.tabs().get(2);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1);
            window.selectTab(a1);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).containsExactly(a2, b1);
            window.selectTab(a2);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_GROUP).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).containsExactly(b1);
        });
    }

    @Test void theFileViewAndPaneMenusNameTabsAndGroups() throws Exception {
        var window = window(1);
        edt(() -> {
            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Window", "Open File…", "Close Tab", "Close Group");
            assertThat(items(window, "View")).contains("Maximize Group");
            assertThat(items(window, "Pane")).containsExactly("Split Right", "Split Down", "Focus Group Left", "Focus Group Right",
                "Focus Group Up", "Focus Group Down", "Previous Prompt", "Next Prompt");
        });
    }
}
```

The approved defaults move with the ids. In `KeymapDefaultsTest`, `KeyBindingsTest` and `ConfigTemplateTest`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, regex
C = "jasper-app/src/test/java/dev/jasper/app/config/"
edit(C + "KeymapDefaultsTest.java", [
    ('        new Row("close_tab", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),              // 23\n',
     '        new Row("close_tab", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                      // 23, workspace tabs spec 11\n'
     '        new Row("close_group", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),            // workspace tabs spec 11\n', 1),
    ('        new Row("close_pane", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                     // 34\n'
     '        new Row("zoom_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"),    // 35\n',
     '        new Row("maximize_group", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"), // 35, workspace tabs spec 11\n', 1)])
regex(C + "KeymapDefaultsTest.java", r'"focus_pane_', '"focus_group_', 4)
edit(C + "KeyBindingsTest.java", [
    ("            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.NEW_WINDOW, ActionId.OPEN_FILE,\n",
     "            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP, ActionId.NEW_WINDOW, ActionId.OPEN_FILE,\n", 1),
    ("            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.CLOSE_PANE, ActionId.ZOOM_PANE,\n",
     "            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_GROUP,\n", 1),
    ("            ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT,\n            ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,\n",
     "            ActionId.FOCUS_GROUP_LEFT, ActionId.FOCUS_GROUP_RIGHT,\n            ActionId.FOCUS_GROUP_UP, ActionId.FOCUS_GROUP_DOWN,\n", 1)])
edit(C + "ConfigTemplateTest.java", [(r'"# close_pane = \"cmd+w\""', r'"# close_tab = \"cmd+w\""', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.TabMenuTest' --tests 'dev.jasper.app.config.*'`
Expected: FAIL to compile (`ActionId.CLOSE_GROUP`, `MAXIMIZE_GROUP` and `FOCUS_GROUP_LEFT` are not defined).

- [ ] **Step 3: The ids, the actions, the menus and the tab menu**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit, span, regex
M = "jasper-app/src/main/java/dev/jasper/app/"
W = M + "workspace/"
edit(M + "commands/ActionId.java", [
    ('    CLOSE_TAB("close_tab", "Close Tab", "alt+cmd+w", "ctrl+alt+shift+w"),\n',
     '    CLOSE_TAB("close_tab", "Close Tab", "cmd+w", "ctrl+shift+w"),\n'
     '    CLOSE_GROUP("close_group", "Close Group", "alt+cmd+w", "ctrl+alt+shift+w"),\n', 1),
    ('    CLOSE_PANE("close_pane", "Close Pane", "cmd+w", "ctrl+shift+w"),\n'
     '    ZOOM_PANE("zoom_pane", "Zoom Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),\n',
     '    MAXIMIZE_GROUP("maximize_group", "Maximize Group", "cmd+shift+enter", "ctrl+alt+shift+enter"),\n', 1),
    ('    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left", "ctrl+alt+shift+left"),\n'
     '    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right", "ctrl+alt+shift+right"),\n'
     '    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up", "ctrl+alt+shift+up"),\n'
     '    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down", "ctrl+alt+shift+down"),\n',
     '    FOCUS_GROUP_LEFT("focus_group_left", "Focus Group Left", "cmd+alt+left", "ctrl+alt+shift+left"),\n'
     '    FOCUS_GROUP_RIGHT("focus_group_right", "Focus Group Right", "cmd+alt+right", "ctrl+alt+shift+right"),\n'
     '    FOCUS_GROUP_UP("focus_group_up", "Focus Group Up", "cmd+alt+up", "ctrl+alt+shift+up"),\n'
     '    FOCUS_GROUP_DOWN("focus_group_down", "Focus Group Down", "cmd+alt+down", "ctrl+alt+shift+down"),\n', 1)])
for path, zoom, focus in ((W + "WorkspaceActions.java", 2, 8), (W + "WindowChrome.java", 4, 4), (W + "ClassicHeader.java", 1, 0),
                          (W + "WindowCommands.java", 3, 0), (M + "shortcuthelp/ShortcutCatalog.java", 1, 4)):
    regex(path, r"\bZOOM_PANE\b", "MAXIMIZE_GROUP", zoom)
    regex(path, r"\bFOCUS_PANE_", "FOCUS_GROUP_", focus)
edit(W + "WorkspaceActions.java", [
    ("            if (id == ActionId.CLOSE_TAB || id == ActionId.CLOSE_PANE) { content.requestClose(); return; }\n",
     "            if (id == ActionId.CLOSE_TAB) { content.requestClose(); return; }\n", 1),
    ("            case CLOSE_TAB, CLOSE_PANE -> owner.closeTab(owner.selectedTab());\n",
     "            case CLOSE_TAB -> owner.closeTab(owner.selectedTab());\n"
     "            case CLOSE_GROUP -> owner.closeGroup(owner.selectedTab());\n", 1),
    ("boolean contentEnabled = content != null && (id == ActionId.CLOSE_TAB || id == ActionId.CLOSE_PANE\n",
     "boolean contentEnabled = content != null && (id == ActionId.CLOSE_TAB\n", 1),
    ("                    case MAXIMIZE_GROUP -> owner.workspace().model().groups().size() > 1;\n",
     "                    case MAXIMIZE_GROUP -> owner.workspace().model().groups().size() > 1;\n"
     "                    case CLOSE_GROUP -> owner.selectedTab() != null;\n", 1)])
edit(W + "WindowCommands.java", [
    ('                case MAXIMIZE_GROUP -> List.of("maximize", "restore");\n',
     '                case MAXIMIZE_GROUP -> List.of("zoom", "restore");\n', 1),
    ('List.of("split", "vertical", "right", "side by side", "pane")', 'List.of("split", "vertical", "right", "side by side", "pane", "group")', 1),
    ('List.of("split", "horizontal", "down", "above below", "pane")', 'List.of("split", "horizontal", "down", "above below", "pane", "group")', 1),
    ('maximized().isPresent() ? "Restore Pane" : "Zoom Pane");', 'maximized().isPresent() ? "Restore Group" : "Maximize Group");', 1)])
edit(W + "WindowChrome.java", [
    ("ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,\n", "ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_GROUP,\n", 1),
    ('case SPLIT_RIGHT -> "Split"; case MAXIMIZE_GROUP -> "Zoom pane";', 'case SPLIT_RIGHT -> "Split"; case MAXIMIZE_GROUP -> "Maximize group";', 1),
    ("ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_GROUP, ActionId.CLOSE_PANE}) menu.add(owner.action(id));",
     "ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_GROUP, ActionId.CLOSE_TAB}) menu.add(owner.action(id));", 1)])
edit(M + "shortcuthelp/ShortcutCatalog.java", [
    ('case "Tabs"->"window";case "Panes"->"split";', 'case "Tabs"->"window";case "Groups"->"split";', 1),
    ("            case SPLIT_RIGHT, SPLIT_DOWN, CLOSE_PANE, MAXIMIZE_GROUP, FOCUS_GROUP_LEFT, FOCUS_GROUP_RIGHT,\n"
     '                FOCUS_GROUP_UP, FOCUS_GROUP_DOWN -> "Panes";\n',
     "            case SPLIT_RIGHT, SPLIT_DOWN, CLOSE_GROUP, MAXIMIZE_GROUP, FOCUS_GROUP_LEFT, FOCUS_GROUP_RIGHT,\n"
     '                FOCUS_GROUP_UP, FOCUS_GROUP_DOWN -> "Groups";\n', 1)])
span(W + "WindowContent.java", "    JPopupMenu tabMenu(Tab tab) {", "return menu;\n    }\n", r'''    /**
     * A tab's menu (workspace plan 3a Rulings 13): Rename Tab for a terminal tab, Open in Separate Window or Move to
     * Main Window for a plugin's tab, Close Tab, Close Others and Close Group, then the content's commands.
     */
    JPopupMenu tabMenu(Tab tab) {
        var menu = new JPopupMenu();
        if (tab.kind() == Tab.Kind.TERMINAL)
            menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(tab); invoke(ActionId.RENAME_TAB); }));
        else menu.add(tabMenuItem(tab.detached() ? "Move to Main Window" : "Open in Separate Window", null, () -> tab.move(!tab.detached())));
        menu.addSeparator();
        menu.add(tabMenuItem("Close Tab", ActionId.CLOSE_TAB, () -> closeTab(tab)));
        var others = tabMenuItem("Close Others", null, () -> closeOthers(tab));
        others.setEnabled(groupTabs(tab).size() > 1);
        menu.add(others);
        menu.add(tabMenuItem("Close Group", ActionId.CLOSE_GROUP, () -> closeGroup(tab)));
        if (!tab.commands().isEmpty()) menu.addSeparator();
        for (var command : tab.commands()) {
            var item = new JMenuItem(command.title()); item.putClientProperty("html.disable", true);
            item.setEnabled(command.enabled()); item.setAccelerator(command.shortcut());
            item.addActionListener(event -> command.run().run());
            menu.add(item);
        }
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /** A tab menu item showing {@code shortcut}'s binding, if any, that runs {@code run}. */
    private JMenuItem tabMenuItem(String title, ActionId shortcut, Runnable run) {
        var item = new JMenuItem(title);
        if (shortcut != null) item.setAccelerator(bindings().strokeFor(shortcut).orElse(null));
        item.addActionListener(event -> run.run());
        return item;
    }
''')
edit(W + "WindowContent.java", [(
"""        if (terminals != null) terminals.atomically(tab::close); else tab.close();
    }
""", """        if (terminals != null) terminals.atomically(tab::close); else tab.close();
    }

    /** The tabs of {@code tab}'s group, in strip order. */
    private java.util.List<Tab> groupTabs(Tab tab) {
        return workspace.model().groupOf(tab.id()).map(workspace::groupView)
            .map(group -> java.util.List.copyOf(group.tabs())).orElse(java.util.List.of());
    }

    /** Closes the other tabs of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeOthers(Tab tab) { closeTabs(groupTabs(tab).stream().filter(other -> other != tab).toList()); }

    /** Closes every tab of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeGroup(Tab tab) { if (tab != null) closeTabs(groupTabs(tab)); }

    /**
     * Closes {@code batch} on one decision: the plugin tabs' guards answer once and a refusal closes nothing; then every
     * tab of the batch closes, terminal tabs included, their registry facts published as one change.
     */
    void closeTabs(java.util.List<Tab> batch) {
        var live = batch.stream().filter(workspace::contains).toList();
        if (live.isEmpty()) return;
        Runnable close = () -> registry.requestClose(live);
        if (terminals != null) terminals.atomically(close); else close.run();
    }
""", 1)])
PY
```

`TabRegistry.requestClose` already asks every plugin's guard once for its tabs in the list (terminal tabs have none) and
removes all of them or none; with guards that answer at once, the removals run inside `atomically`.

- [ ] **Step 4: Move the remaining tests to the new ids and labels**

```bash
python3 -I - <<'PY'
import pathlib, re, sys
sys.path.insert(0, "build/plan3a"); from edits import edit
for path in pathlib.Path("jasper-app/src/test").rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    moved = re.sub(r"\bFOCUS_PANE_", "FOCUS_GROUP_", re.sub(r"\bZOOM_PANE\b", "MAXIMIZE_GROUP", text))
    if moved != text: path.write_text(moved, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(T + "TerminalContextMenuTest.java", [('                "Zoom Pane", "Close Pane");', '                "Maximize Group", "Close Tab");', 1)])
edit(T + "ZenModeTest.java", [('item(view, "Zoom Pane")', 'item(view, "Maximize Group")', 1)])
edit(T + "WindowChromeContributionsTest.java", [('endsWith("Zoom pane", "Run Tool"', 'endsWith("Maximize group", "Run Tool"', 1)])
edit(T + "WindowChromeTest.java", [('"Split", "Zoom pane", "(|)"', '"Split", "Maximize group", "(|)"', 1)])
edit(T + "WindowCommandPaletteTest.java", [('getValue(Command.TITLE)).isEqualTo("Restore Pane");', 'getValue(Command.TITLE)).isEqualTo("Restore Group");', 1)])
edit(T + "ShortcutContextDispatchTest.java", [("void closePaneClosesTheActiveTabAndAGroupGoesWithItsLastTab()",
                                               "void cmdWClosesTheActiveTabAndAGroupGoesWithItsLastTab()", 1)])
left = []
for root in ("jasper-app/src", "plugins"):
    for path in pathlib.Path(root).rglob("*.java"):
        text = path.read_text(encoding="utf-8").replace("ExitPolicy.CLOSE_PANE", "")
        if re.search(r"\b(CLOSE_PANE|ZOOM_PANE|FOCUS_PANE_\w+)\b|\b(close|zoom|focus)_pane|\"(Zoom|Close|Restore) Pane\"|Zoom pane", text):
            left.append(str(path))
if left: sys.exit("still naming the pane commands: " + ", ".join(left))
PY
```

The SDK's `ExitPolicy.CLOSE_PANE` (a session's exit policy, not a command) stays; plan 3b owns the SDK.

- [ ] **Step 5: Ledger**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); import ledger
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
ledger.add(T + "TabMenuTest.java", "app.workspace", "TabMenuTest", T + "TabMenuTest.java",
           "Test/fixture ownership; New in workspace-tabs plan 3a Task 6; the tab menu and batch closes")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`TabMenuTest` 5, `KeymapDefaultsTest` 4, `KeyBindingsTest` unchanged count). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Close Tab is Cmd+W; Close Group, Maximize Group and Focus Group

close_tab takes Cmd+W and absorbs close_pane; close_group takes
Option+Cmd+W; maximize_group and focus_group_* replace zoom_pane and
focus_pane_*. The tab menu gains Rename Tab, Close Others and Close
Group, which ask the plugins' guards once for the batch. Menus, the
toolbar, the palette and the keymap name tabs and groups.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Documentation, STATUS and the whole build

**Files:**
- Modify: `README.md:9,113`, `docs/app-architecture.md` (the `workspace` and `contributions` rows, "Window chrome"),
  `docs/app-maintenance.md:270,644,682-683`, `docs/sdk-architecture.md:418-420`, `docs/command-palette.md:74-75`,
  `docs/benchmarks.md:93,95`, `docs/configuration.md:348,506,534-535,580,681`, `docs/remote.md:33,169`,
  `docs/packaging.md:332,370`, `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Each edit is the smallest that makes its sentence true (AGENTS.md "Documentation edits"); plans 1 and 2 leave these
sentences alone:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan3a"); from edits import edit
edit("README.md", [
    ("multiple windows, tabs, splits, pane zoom/navigation,", "multiple windows, tabs in split tab groups, group maximize/navigation,", 1),
    ("Cmd+Option+arrows focus a pane, Cmd+Shift+Enter zoom,", "Cmd+Option+arrows focus a group, Cmd+Shift+Enter maximize a group,", 1),
    ("Cmd+W close tab, Cmd+Shift+W close pane.", "Cmd+W close tab, Cmd+Option+W close group.", 1),
    ("On macOS, a single tab is represented by a centered title; multiple tabs share the available title-bar width.",
     "Every tab group shows its tab strip, even with one tab; its tabs share the strip's width.", 1)])
edit("docs/app-architecture.md", [
    ("| `workspace` | EDT windows, tabs, splits, panes, the tool window area",
     "| `workspace` | EDT windows, their workspaces of tab groups (`WorkspaceView`, `GroupView`, `SplitContainer`), terminal tabs and panes, the tool window area", 1),
    ("in app-native types. Application owns the single instance; the plugin runtime writes it and every window renders it. |",
     "in app-native types, and the `TabRegistry` of every window's tabs, terminal and plugin. Application owns the single instance; the plugin runtime writes it, every window renders it and registers its tabs in it. |", 1),
    ("Every window has a header row and `WindowTabs` below it.",
     "Every window has a header row above its workspace of tab groups, each with its own tab strip.", 1),
    ("`TabStrip` (`WindowTabs`, or the content-sized `ClassicTabs`)", "`TabStrip` (one per tab group: `WindowTabs`, or the content-sized `ClassicTabs`)", 1)])
edit("docs/app-maintenance.md", [
    ("`TerminalDeck`, `TerminalTab` and its `PaneSplit`s paint nothing except solid dividers;",
     "`WorkspaceView`, its `SplitContainer`s and the groups' card areas paint nothing except solid dividers;", 1),
    ("terminal-internal splits keep their existing visible indicators.", "the workspace's group splits keep their visible dividers.", 1),
    ("Workspace tabs use centered text and equal-width cells, hiding the rail when only one tab\nremains.",
     "Workspace tabs use centered text and equal-width cells; each group's strip shows even with\none tab.", 1)])
edit("docs/sdk-architecture.md", [
    ("`contributions.ContentRegistry`/`ContentEntry`.", "`contributions.TabRegistry`/`Tab`.", 1),
    ("`WindowContent` hosts both terminal and plugin components; `WindowTabs` and\n`ToolWindowArea` remain the single renderer.",
     "`WindowContent` hosts both terminal and plugin components; its `WorkspaceView` and\n`ToolWindowArea` remain the single renderer.", 1)])
edit("docs/command-palette.md", [("Split Right\ncreates side-by-side panes; Split Down creates vertically stacked panes.",
     "Split Right\nopens a terminal tab in a new group to the right; Split Down opens one in a new group below.", 1)])
edit("docs/benchmarks.md", [
    ("Cycles create a tab, split it, close it, create a sibling window, then close it;",
     "Cycles create a tab and a split beside it, close both, create a sibling window, then close it;", 1),
    ("Splits alternate right/down on the focused pane.",
     "Splits alternate right/down on the active group, each opening a terminal tab in a new group.", 1)])
edit("docs/configuration.md", [
    ("(a visible but unfocused split pane included)", "(a visible tab in another group included)", 1),
    ("including hidden tabs, zoomed-out sibling panes and pending shell launches",
     "including hidden tabs, groups hidden behind a maximized group and pending shell launches", 1),
    ("A split starts the profile of\nthe pane it splits; New Tab starts the configured one.",
     "A split beside a running terminal starts\nthat terminal's profile; any other split, and New Tab, start the configured one.", 1),
    ("Only the exited pane closes. Its tab closes when its final pane closes, and its window closes when its final tab closes. Sibling panes and tabs keep running.",
     "Only the exited pane's tab closes, and its window closes when its final tab closes. Other tabs keep running.", 1),
    ("Launch failures continue to show an error and remove the failed pane.", "Launch failures continue to show an error and close the failed tab.", 1),
    ("pane zoom\nworks independently.", "Maximize Group\nworks independently.", 1)])
edit("docs/remote.md", [
    ("**Open in split** splits the current pane.", "**Open in split** opens it in a new tab group beside the current one.", 1),
    ("Cmd/Ctrl+Enter splits the current pane,", "Cmd/Ctrl+Enter opens it in a new tab group beside the current one,", 1)])
edit("docs/packaging.md", [("Confirm split, close, zoom, pane navigation, search, and prompt navigation.",
     "Confirm split into groups, close, maximize, group navigation, search, and prompt navigation.", 2)])
PY
```

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

Record the line in the STATUS entry. A test that fails because a single tab now shows its strip (spec 1, decision 4)
follows Task 3 Step 6's rule: re-derive its expectation from the strip's own geometry, never weaken it or hide the
strip, and record it in the banner and the STATUS entry.

- [ ] **Step 4: STATUS, the banner, and commit**

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above plan 2's entry,
"Workspace tabs, plan 2: the layout model and its encoding"), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 3a: the tab registry and the workspace view — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-17-workspace-3a-tab-registry.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). Every tab of every window is a
`contributions.Tab` in the application's `TabRegistry`, which absorbs `ContentRegistry`, `ContentEntry` and
`ContentHost` and keeps the per-plugin close guards and batched approval; a tab holds a `TabContent`, either
`workspace.TerminalContent` (one terminal pane and its `TabState`) or a plugin's `HostedContent`. Each window renders
plan 2's `Layout` in a `WorkspaceView`: `SplitContainer`s with draggable dividers (a tenth of the split on each side,
weights written to the layout on release) over `GroupView`s, each an always-visible `TabStrip` (the equal or the
classic tabs, shown active for the active group of the active window) over its selected tab, reporting its strip and
card rectangles for plan 5. Focus going into a tab (a pane, or a plugin's view through a window-level focus
listener) makes it the active tab without moving focus again. `TabHost` wires a terminal tab to its window and again
after `TabRegistry.move(Tab, UUID)`, which keeps its session and ends a maximize on both sides (`Tab.moving()`); a tab
that moves or is reordered takes its place in a strip without motion. Cmd+D and Cmd+Shift+D open a terminal tab in a
new group to the right or below, in a running active terminal's directory with its profile, otherwise (beside a
plugin's view or an ended shell) where a new tab starts with the default profile; `close_tab` (⌘W / Ctrl+Shift+W)
absorbs `close_pane`; `close_group` (⌥⌘W / Ctrl+Alt+Shift+W) is new; `maximize_group` and `focus_group_*` replace
`zoom_pane` and `focus_pane_*`; Next, Previous and Select Tab act in the active group; `terminal.dim_inactive_panes`
dims terminal tabs outside the active group. The tab menu reads Rename Tab, Open in Separate Window or Move to Main
Window, Close Tab, Close Others, Close Group, then the content's commands. `TerminalTab`, `SplitTree`, `TerminalDeck`,
`terminals.SplitAxis` and jasper-ui's `DesignMetrics.Role.TAB_REVEAL_INSET` are gone; the terminal registry splits
into new groups (`PaneEntry.split` takes `layout.Side`). Rulings (plan, "Rulings"): <one clause each, 1 to 30>.
Deviations: <none, or each with its reason>. Left for plan 3b (the SDK break): `TabEntry` gains a kind and loses its
pane list, `WindowEntry` lists every tab, `TabHandle.kind()`/`terminal()` replace `panes()`/`activePane()`,
`WindowHandle.tabs()` lists every tab, TAB events cover every content and gain `TAB_MOVED`, `StandardMenu.PANE` and
`MenuTarget.Slot.PANE` fold into Tab; the test-side fake still splits within a tab: `TerminalFixture` adds a split's
pane to the same tab and `PluginContractTest.openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane` asserts
`split.tab().id()` is the old tab, so `AppContractTest` never exercises the new split behaviour. Left for plan 4: an
operating-system notification's click raises the window its command finished in, captured when `JasperApplication`
composed that window, before the activity's "go to" follows a moved tab; resolve that window at click time once tabs
move between windows. `./gradlew check --continue`: **<counts>**. GUI checks for the user: each group's strip in the
equal and the classic tabs and its active state across windows; clicking into another group's terminal or plugin view
(it becomes active, Cmd+W closes it); Cmd+D, Cmd+Shift+D (also beside a plugin view), dragging a divider and a theme
change with splits; Maximize Group and Focus Group; dimming; the tab menu, and Close Group over a modified Editor tab
(one prompt); a translucent workspace with groups; Open in Separate Window. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add README.md docs
git commit -F - <<'EOF'
Workspace tabs: documentation and status for the tab registry and groups

The documents describe tab groups, Close Group, Maximize Group and Focus
Group in place of panes, zoom and pane focus; STATUS records plan 3a
with its rulings, the hand-off to plan 3b and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 4 no motion (Rulings 27), 9.1 the tab registry folding `ContentRegistry` with its guards (Task 1;
  Rulings 1, 3, 4, 5), 9.2 terminal and hosted content with `TabState` on the tab (Tasks 1, 5; Rulings 2, 20), 9.3 hosts
  and moves keeping the session (Task 4; Rulings 17, 18), 9.4 the terminal registry's app side (Task 5; Rulings 19), 10
  the workspace view, `SplitContainer`, `GroupView` with the kit's strip, active strip, dimming, focus making a tab
  active (Tasks 2, 3, 5; Rulings 6-10, 23, 26), 11 the commands and the tab menu (Tasks 5, 6; Rulings 11-16, 25), 15 the
  last tab of a group, a maximized group losing a tab by a close (plan 2's operations rendered as they leave the layout)
  or a move into or out of a window (Task 4, Rulings 17), 16 the removed types and commands (Tasks 3, 5, 6), 17 the view
  tests: a terminal tab moved between windows keeps its session (`TabMoveTest`, `IdleTerminalSession`, Rulings 21),
  Cmd+D makes a group (`WorkspaceTabsTest`), closing the last tab of a group collapses it (`WorkspaceViewTest`,
  `WorkspaceTabsTest`). Out of scope here: the empty main workspace and child windows (plan 4), an operating-system
  notification's click after a move (plan 4, Rulings 18), drag and drop (plan 5), placeholders and restore (plan 6),
  the SDK rows of spec 14 and the testkit's split (plan 3b).
- Every `Layout` call uses plan 2's signatures as "Before you start" checks them; the view renders `Group.selected()`,
  `activeGroup()` and `maximized()` without choosing (Global Constraints).
- Swing names: no new method on a component reuses an AWT method's name where a call could reach the AWT one (fact
  13, Rulings 29); `layout()` in particular would not compile.
- Every edit script and every created, replaced or inserted file of Tasks 1 to 7 was replayed in order on a copy of
  `93aa015fd`: every anchor matches its stated count; nothing was compiled or run.
- Type consistency: `Tab`, `TabId`, `GroupId`, `Side`, `TerminalContent`, `WorkspaceView`, `GroupView` and
  `SplitContainer` keep one name and one signature from the task that creates them to the last task that calls them.
