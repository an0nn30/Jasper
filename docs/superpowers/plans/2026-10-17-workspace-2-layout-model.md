# Workspace tabs 2: the layout model and its encoding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A pure, immutable layout model for VS Code-style workspaces (n-ary weighted splits whose leaves are tab groups)
in a new JDK-only package `dev.jasper.app.layout`, and the `workspace.toml` codec that writes and reads every window's
layout and tabs, unwired and with no visible change.

**Architecture:** `dev.jasper.app.layout` holds value records (`TabId`, `GroupId`, `Split`, `Child`, `Group`, `Region`),
two enums (`Orientation`, `Side`) and the final class `Layout`, whose operations each return a new layout and reject
arguments that break spec 7.2; `verifyApplicationArchitecture` keeps the package JDK-only, as it does `dock`. The codec
`dev.jasper.app.persistence.WorkspaceFile` (tomlj stays out of `layout`) turns a `Snapshot` of `WindowRecord`s into the
flat TOML of spec 8 through `TomlStateFile`'s atomic write, and reads it back with a bounded read that drops bad tab and
window entries alone and sets an unreadable file aside. Plan 3 renders `Layout` and plan 6 wires the codec; plan 5
resolves drops over the group views' pixel bounds, as `DockDropResolver` does, and `Layout.bounds()` serves `neighbour`
and tests. Nothing in the app calls either here.

**Tech Stack:** Java 25 on JetBrains Runtime 25 (records, sealed interfaces, pattern switches), tomlj 1.1.1, Gradle 9.7
(Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for checks (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
(sections 1, 3, 4, 7, 8, 13, 15, 17 and 18; section 12, whose drops plan 5 resolves in pixels, not here). Plan 2 of 6
(spec 18).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`.
Run every command from the worktree root. Line numbers are from `941599fc2`; plan 1 of this series may execute first and
touches none of the anchors used here except `docs/STATUS.md`'s top (see "Before you start"). Every edit to an existing
file quotes the text it replaces; if the quoted text is not found exactly once, re-find it, adjust the anchor and record
it in the banner; never edit around a missing anchor.

## Before you start

```bash
git branch --show-current                                                                  # claude/workspace-tabs
git status --short                                                                         # nothing
ls jasper-app/src/main/java/dev/jasper/app/layout 2>&1 | head -1                           # No such file or directory
grep -c 'dev.jasper.app.dock is JDK-only and holds no Swing or AWT components' gradle/application-architecture.gradle.kts   # 1
grep -c 'private static Optional<Bounds> bounds(TomlTable entry) {' jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java          # 1
grep -c 'private static Optional<Integer> number(TomlTable table, String key) {' jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java  # 1
grep -c '    static String quote(String value) {' jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java                       # 1
grep -c 'public Path uiState() {' jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java                                         # 1
grep -c 'void buddyStateLivesBesideCommandHistory()' jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java                  # 1
grep -c "Allowed outgoing Jasper dependencies: dev.jasper.app.dock (slots and the dock's size limits)." jasper-app/src/main/java/dev/jasper/app/persistence/package-info.java  # 1
```

Every command prints the stated result. If plan 1 has executed, its STATUS entry is the top one; this plan's entry goes
above it.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run` or `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`. No push, no merge.
- `dev.jasper.app.layout` is JDK-only: it references `java.*` types outside `java.awt` and its own types, nothing else
  (no Swing, no AWT, no tomlj, no other Jasper package); `verifyApplicationArchitecture` enforces it (Task 1).
- Pure and unwired: no production class outside `layout` and `persistence.WorkspaceFile` references the new types; no
  file is written at run time. `SplitTree` stays (plan 3 deletes it); `UiState`'s `[main_window]` stays (plan 6 removes
  it). No user-visible change.
- Spec 8's file is `<home>/workspace.toml`, the set-aside copy `workspace.toml.bad`, `version = 1`, and the keys exactly
  as spec 8 lists them: `[[windows]]` `role` (`main`/`child`), `x`, `y`, `width`, `height`, `extended_state`,
  `active_group`, `maximized`; `[[windows.nodes]]` `id`, `kind` (`split`/`group`), `orientation`
  (`horizontal`/`vertical`), `parent` (`""` for the root), `weight`, `tabs`, `selected`; `[[windows.tabs]]` `id`, `kind`
  (`terminal` or `content:<plugin id>:<restorer kind>`), `title`, `directory`, `profile`, `state`.
- Theme-migration ledger: `ThemeMigrationCoverageTest` needs a row in `docs/design/theme-engine/migration.csv` for every
  Java file under `jasper-app/src` its pattern matches (`new ArrayList<` does, fact 14): `Split`, `Layout` and
  `LayoutValuesTest` (Task 1) and `WorkspaceFile` (Task 4); a file a later edit makes match gets its row in that task.
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` outside `{@code}`, and every
  `{@link}` must resolve in the task that adds it.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired-surrogate characters; Java escapes only, and never
  a backslash-`u` sequence in a comment.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").

## Review Focus

1. Titles, directories and plugin restore state that carry quotes, backslashes, tabs, newlines, non-ASCII text or keys
   that are not bare (`C:\work`, `Files "prod"`, `"a b"`, `dotted.key`): they come back exactly and never break the
   file. Task 4: `writesSpecEightsWorkspaceExactly`; Task 5: `stringsWithQuotesBackslashesNewlinesAndOddKeysRoundTrip`.
2. Every relaunch saves what it read: the second write is byte-identical and no weight drifts (thirds; 0.1, 0.2, 0.7).
   Task 5: `rewritingAReadFileWritesTheSameText`.
3. Cmd+D or Cmd+Shift+D, then closing the new tab: the sizes the user had come back exactly. Task 3:
   `splittingRightOrDownThenClosingRestoresTheSizes`.
4. A hand-edited `workspace.toml` whose `parent` links form a cycle, leave a node out, or nest absurdly deep: that window
   is dropped and the reader neither hangs nor overflows the stack; a value nested so deep that tomlj's parser overflows
   sets the file aside instead of crashing the start. Task 5: `aBrokenTreeDropsItsWindowAlone`,
   `aTreeDeeperThanTheLimitDropsItsWindow`, `aNestingThatOverflowsTheParserIsSetAside`.
5. Closing every tab of the main window: the empty workspace keeps its group, takes the next tab, and refuses a split
   rather than leave an empty group beside a new one. Task 2: `removalDownToTheEmptyWorkspaceKeepsItsGroupForTheNextTab`;
   Task 3: `splitRefusesAKnownTabAnUnknownGroupAndTheEmptyWorkspace`.

## Verified facts

At `941599fc2`.

1. `gradle/application-architecture.gradle.kts`: `dockAwt` is `java.awt.Rectangle` and `java.awt.Point` (`:26-27`); the
   dock rule is `:63-66` (`packageOf(from) == "dev.jasper.app.dock"`, allowed: its own package, `java.*` outside
   `java.awt.`, and `dockAwt`); the vmoptions rule follows at `:67-71`; edges come from `jdeps -verbose:class` over the
   compiled classes (`:49-55`). No test exercises these rules (no file under `*/src/test` names them), so Task 1 proves
   the new rule with a temporary probe class.
2. `dock/package-info.java:1-9` states the JDK-only contract and "Allowed outgoing Jasper dependencies: no other Jasper
   package."; `persistence/package-info.java:3` allows `dev.jasper.app.dock` only.
   `AppDocumentationTest.everyProductionPackageHasAnOwnershipContract` (`:106-120`) requires a `package-info.java` with
   `/**` and `package dev.jasper.app` in every production package.
3. `workspace/SplitTree.java`: `navigate` (`:111-148`) lays the leaves over the unit square (`collectGeometry`,
   `:281-302`), keeps candidates wholly in the direction (`isInDirection`, `:304-311`) that overlap across it
   (`perpendicularOverlap`, `:313-320`), prefers the smallest forward distance, then the nearest centre across
   (`:322-338`), with `EPSILON = 1.0e-12` (`:46`), strict improvements only (first in traversal order wins a tie), and no
   wrap. Ratios clamp to 0.1-0.9 (`:44-45`, `:179-184`). Splitting and closing clear the zoom (`:95`, `:108`); navigation
   keeps it and moves the focus (`SplitTreeTest.java:121-134`).
4. `SplitTreeTest.java` has eight cases (`:11-168`); Task 3 ports the navigation and split ones, Tasks 2 and 3 the
   removal, zoom and invalid-id ones.
5. `persistence/UiState.java`: `Bounds(int x, int y, int width, int height)` with a positive size (`:68-72`);
   `MainWindow` keeps only bits 2 and 4 of the extended state (`:75-80`); `MAX_BYTES = 256 * 1024` (`:118`);
   `[main_window]` is read at `:185-189` (a bad extended state reads as 0) and written at `:334-339`; `bounds` (`:277-281`,
   a bad key drops the bounds alone) and `number` (`:283-286`) are private; `quote` is package-private (`:365-375`: quotes
   and backslashes escaped, control characters as backslash-u escapes, a lone surrogate as U+FFFD).
6. `persistence/TomlStateFile.java`: `readBounded(file, maxBytes, what)` returns empty for an absent file and throws
   `IOException` naming `what` and the cap for a larger one or for invalid UTF-8 (`:19-29`, `:48-57`);
   `writeAtomically(file, prefix, text)` creates the parent, writes a temp file beside the target and moves it into place
   (`:31-46`). `BuddyStateFile.write` lets the `IOException` through (`:32-34`).
7. `platform/AppDirs.java:54-57` is `uiState()` (`ui-state.toml` in the root); `AppDirsTest.java:57-61` is
   `buddyStateLivesBesideCommandHistory`.
8. tomlj 1.1.1 is on the app's class path (`jasper-app/build.gradle.kts:14`): `TomlArray.size()` and `get(int)`,
   `TomlTable.get(List<String>)` (raw keys, no dotted parsing) and `keySet()`, `TomlParseResult.errors()`; integers read
   as `Long`, floats as `Double` (`UiState.java:222`, `:273` rely on it).
9. `jasper-app/build.gradle.kts:185-193` runs `javadoc` with `-Xdoclint:all` and `failOnError` as part of `check`; the
   root build compiles with `-Xlint:all -Xlint:-serial`, without `-Werror` (`build.gradle.kts:26`).
10. `JTabbedPane.removeTabAt` (JBR 25 `src.zip`, `javax/swing/JTabbedPane.java:1016-1033`): removing the selected tab
    selects the tab now at its index, or the previous one when it was last; removing another tab keeps the selection.
    Today's tab deck is a `JTabbedPane` (`WindowContent.closeTab`, `:1114-1126`).
11. `dock/Side.java:4` is `enum Side { LEFT, RIGHT, BOTTOM }`; the new `layout.Side` is a different type with the same
    simple name (no file in this plan imports both).
12. `docs/app-architecture.md:162` is the `dock` row of the package ownership table; `AGENTS.md:25` states the dock
    package's JDK-only rule.
13. `UUID.nameUUIDFromBytes` is MD5 with version 3 and the IETF variant; `"jasper.split:"` gives
    `e8b5eccb-037b-3d65-a6c6-5e4889602b31` and `"jasper.split:1"` gives `59943c66-5e92-39d1-a8ea-1f88b440a667` (computed
    with Python's `hashlib.md5` and the same bit masks).
14. `ThemeMigrationCoverageTest.sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives` (`:62-83`) scans every
    `.java` file under `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and `plugins/*/src` with `CANDIDATE`
    (`:17-18`), which matches `new [A-Z]\w*List\b` and so `new ArrayList<`, and requires each match to have a row in
    `docs/design/theme-engine/migration.csv`; `everyInventoriedPathExistsAndStandardControlsHaveBothOwners` (`:21-60`)
    wants 8 columns, an existing path, a known classification and existing evidence files. New nonvisual rows read
    `<path>,<owner>,<Class>,nonvisual-helper,none,none,<test>,New in …` (`migration.csv:1391`, `:1412`), appended at the
    end (the file is not sorted). Checked against this plan's files: `Split`, `Layout`, `LayoutValuesTest` and
    `WorkspaceFile` match; the other new files and the edited `AppDirsTest` do not.
15. tomlj 1.1.1's parser has no nesting guard: a value of a few hundred thousand nested `[` overflows the stack inside
    `Toml.parse` (review finding; not reproduced here).

## Rulings

Record them in `docs/STATUS.md` (Task 6).

1. **`Group.selected` is `Optional<TabId>`, empty exactly when the group has no tabs** — spec 7.2 allows one empty group
   (the empty workspace's) and a `TabId` cannot be absent without null.
2. **`Layout` is a final class whose public constructor is spec 7.1's `Layout(Node root, GroupId activeGroup,
   Optional<GroupId> maximized)`; it also keeps the groups' order of use (`recent()`, the active group first) and, privately,
   each group's tab order of use (Rulings 7), both outside `equals`, `hashCode` and the file; the public constructor starts
   the groups' order with the active group, then tree order** — a record cannot hold state outside its equality, and a
   layout read back must equal the one written (spec 7.3: "not persisted").
3. **A split is addressed by its path, the child indexes from the root (`List<Integer>`, empty for the root), in
   `resize`; in the file a split's id is the name-based UUID of `"jasper.split:"` plus that path joined with dots** —
   VS Code's grid addresses views by location arrays (`resizeView(location, size)`) and serializes branches without ids;
   plan 3's view renders from the root and knows each split's path when a divider drag ends, which writes the weights
   back before any other change; the file's ids only link a window's nodes, are stable while the tree keeps its shape,
   and are never equal to a random id (version 3 against version 4). On read, a split's id may be any non-empty string.
4. **Weights within 1e-9 of summing to 1 are kept bit for bit; others are divided by their sum; the model sets no
   minimum weight** — normalizing becomes idempotent, so rewriting a read file changes nothing (Review Focus 2);
   `SplitTree`'s 0.1-0.9 clamp is a view limit for plan 3's dividers.
5. **While a group is maximized it is the active group. `activate`, `select` and `open` into another group move the
   maximize to it; `split`, `moveToNewGroup` and a `move` between groups (it loses or gains a tab by a move) clear it;
   `remove` (closing a tab) keeps it while the maximized group survives and two groups remain, and clears it when the
   maximized group itself goes or one group is left; `resize`, a reorder within a group and `open` into it keep it.
   `unmaximize()` clears it, and plans 4 and 5 call it when a tab moves out to another window (a `remove` there).
   `toggleMaximize` on a one-group layout returns it unchanged** — today's zoom follows focus (fact 3) and a hidden
   active group would take keystrokes unseen; VS Code keeps a group maximized when one of its editors closes, so spec
   15's row is read as "loses a tab by a move or gains one by a move clears it; closing a tab keeps it" (Spec issues);
   one group already fills the workspace.
6. **`select(tab)` also activates the tab's group; `activate` changes no selection** — every caller (a strip click,
   `select_tab_N`, a notification's "go to") focuses the group whose tab it shows.
7. **Removing a group's selected tab selects that group's most recently shown remaining tab; removing another tab keeps
   the selection. `Layout` keeps each group's tab order of use privately (`tabOrders`), outside `equals`, `hashCode` and
   the file: a tab goes to the front whenever it becomes its group's selected tab (`select`, `open`, `move` into the
   group, a removal's choice). The public constructor, and so a read from the file, starts each group's order with its
   selected tab, then the tabs after it in strip order, then those before it, nearest first; so tabs never shown since
   are reached in `JTabbedPane.removeTabAt`'s order (the tab that takes the closed one's place, else the one before
   it)** — VS Code's `workbench.editor.focusRecentEditorAfterClose` (default true); the seed keeps today's behaviour
   (fact 10) where there is no history.
8. **A removed group's weight goes to the sibling before it, or after it when it was first** — Cmd+D (RIGHT) or
   Cmd+Shift+D (DOWN), then closing the new tab, restores the previous sizes exactly (Review Focus 3); a proportional
   share would not. The limit: exact restoration holds for RIGHT and DOWN splits; a LEFT or UP split inside a parent of
   its orientation, of a group with a sibling before it, does not restore exactly: in `h(g1 .25, g2 .75)`, splitting g2
   LEFT gives `h(g1 .25, new .375, g2 .375)`, and closing the new tab gives `h(g1 .625, g2 .375)`.
9. **`move(tab, group, index)` counts the target's tabs without the moved tab; `split` and `moveToNewGroup` need a target
   group with tabs; a group's only tab cannot split its own group** — splitting the empty workspace would leave an empty
   group beside the new one (spec 7.2), and moving a group's only tab beside itself changes nothing but an id; plan 5's
   resolver offers neither zone. On an empty workspace, plan 3's `split_right` and `split_down` (and Enterprise's SPLIT
   palette verb) open the new tab in the empty group with `open` instead of calling `split`.
10. **`neighbour(group, direction)` is a query (`Optional<GroupId>`, empty at an edge, no wrap) over the whole tree's
    geometry, maximize ignored, ties to the first group in tree order; `bounds()` likewise places a maximized group where
    it sits in the tree, and serves `neighbour` and tests** — fact 3's rule with groups for panes; plan 5 resolves drops
    over the group views' pixel bounds, as `DockDropResolver` does, because while a group is maximized the view shows
    only it (plan 3) and drops need strip and tab geometry.
11. **`TabId` and `GroupId` are UUID records with `random()` and `parse(String)`, which accepts only the canonical
    36-character form, in either case** — `UUID.fromString` alone also reads forms like `1-2-3-4-5`, and a file id must
    write back as it was read.
12. **The codec is `dev.jasper.app.persistence.WorkspaceFile`, a stateless final class (like `BuddyStateFile`) with nested
    values `Role`, `TabRecord` (`Terminal`, `Content`), `WindowRecord`, `Snapshot` and `Loaded` (`Restore`, `Fresh`);
    `persistence` gains the edge to `layout`; window bounds reuse `UiState.Bounds`, and `UiState.bounds` and `number`
    become package-private for the reader** — tomlj and `TomlStateFile` cannot live in `layout`; `persistence` already
    owns the state files; one bounds value and one bounds reader for every window file.
13. **`read` never throws, not even a `StackOverflowError` from tomlj's parser (fact 15). An absent file is
    `Fresh(Optional.empty())`; a file that cannot be read (I/O error, over the cap, invalid UTF-8), does not parse (the
    parser's stack overflow included), has a version other than 1 or keeps no main window is moved to
    `workspace.toml.bad` beside it (replacing an older one) and is `Fresh(Optional.of(<that path>))`; `write` throws
    `IOException`** — plan 6 opens one terminal tab for `Fresh` and may tell the user what was set aside; its debounced
    saver logs a failed write, as `BuddyStateFile`'s caller does.
14. **Bad entries. A tab entry is bad when a key the writer writes is missing or of the wrong type, its id is not
    canonical, its kind is unknown or names no plugin or restorer, or a restore-state value is not a string; its tab, and
    a tab a group lists with no entry, is dropped through `Layout.remove`; the first of two entries for one id wins, and
    an entry no group lists is ignored. A window entry is bad when its role is unknown, a node is bad (no id, an unknown
    kind, an orientation or weight that does not parse, a group or tab id that is not canonical, a tab listed twice), the
    nodes are not one tree (none or two roots, a parent that is not a split of the window, a cycle or a node out of the
    tree), the tree nests deeper than 64, the `Layout` constructor refuses it, it is a child window left empty, it is a
    second main window, or a group or tab of it is in an earlier window. Bounds, `extended_state`, `active_group`,
    `maximized` and `selected` fall back alone (no bounds, 0, the first group, none, the first tab)** — spec 8 names tab
    and window entries; per-key fallback is `UiState`'s; files are machine-written, so a broken tree is dropped, not
    repaired; the depth cap bounds the reader's recursion (Review Focus 4).
15. **The cap is `ui-state.toml`'s, 256 KiB (`256 * 1024`)** — spec 8: "capped like `ui-state.toml`"; about 1,300 tabs at
    200 bytes each; a larger file is set aside on the next read like any unreadable one.
16. **Restore-state keys are written bare when they match `[A-Za-z0-9_-]+`, quoted otherwise, in key order; every string
    goes through `UiState.quote`; ids are written raw** — spec 8 shows `state = { path = "…" }`; plugin keys are free text.
17. **`WindowRecord.bounds` is `Optional<UiState.Bounds>`** — a bad bound must not cost the main window its layout
    (`UiState` drops only a bad `[main_window]`'s bounds); plan 6 places a window without bounds as a new window.
18. **`Snapshot` puts the main window first and refuses a group or tab in two windows** — spec 7.2 makes ids unique in a
    layout and the tab registry (plan 3) is application-wide.
19. **A group's place in the unit square is `Region`, not `Bounds`; only `neighbour` and tests read it, not plan 5's
    drop resolver (Rulings 10)** — `UiState.Bounds` is a window's pixel bounds.
20. **For plan 6: `WindowRecord` needs a record for every tab of its layout, so before saving plan 6 takes out, with
    `Layout.remove`, every tab it cannot restore (the app-owned Errors tab of plan 4, hosted tabs without a restorer),
    and leaves out a child window that holds only such tabs** — the record checks exactly the layout's tabs, and only
    the main window may be empty.

## Spec issues

1. **The maximize on closing a tab.** Spec 7.3 says "any structural change clears it" and spec 15 that a maximized
   group that "loses a tab or gains one by a move" clears. Following VS Code (a review decision), closing a tab keeps
   the maximize while the maximized group survives and two groups remain, also when the closing removes another group;
   spec 15's row is read as "loses a tab by a move or gains one by a move", which still clears (Rulings 5).

(Spec 7.3 also lists `neighbour` among operations that "return a new `Layout`"; it returns a group, Rulings 10.)

## File structure

- `dev.jasper.app.layout` (Task 1, then Tasks 2 and 3 add operations to `Layout`): `package-info`, `Ids` (package-private
  parsing), `TabId`, `GroupId`, `Orientation`, `Side`, `Node`, `Child`, `Split`, `Group`, `Region`, `Layout`; tests
  `LayoutFixtures`, `LayoutValuesTest`, `LayoutTest` (Task 1), `LayoutOperationsTest` (Task 2), `LayoutGeometryTest`
  (Task 3).
- `gradle/application-architecture.gradle.kts` (Task 1): the JDK-only rule for `layout`.
- `dev.jasper.app.persistence`: `WorkspaceFile` (values and writing in Task 4, reading in Task 5), `package-info`
  (Task 4), `UiState` (Task 5: two helpers package-private); tests `WorkspaceFileTest` (Task 4), `WorkspaceFileReadTest`
  (Task 5).
- `dev.jasper.app.platform.AppDirs` and `AppDirsTest` (Task 4): `workspaceState()`.
- Documents (Task 6): `docs/app-architecture.md`, `AGENTS.md`, `docs/STATUS.md`, this plan's banner.

---

### Task 1: The layout values and the JDK-only package

Spec 7.1 and 7.2: the ids, the tree's values with their invariants, the `Layout` constructor and its queries, and the
architecture rule.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/layout/package-info.java`, `Ids.java`, `TabId.java`, `GroupId.java`,
  `Orientation.java`, `Side.java`, `Node.java`, `Child.java`, `Split.java`, `Group.java`, `Region.java`, `Layout.java`
- Modify: `gradle/application-architecture.gradle.kts:63-66`, `docs/design/theme-engine/migration.csv` (three rows appended)
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/LayoutFixtures.java`, `LayoutValuesTest.java`, `LayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `record TabId(UUID value)` and `record GroupId(UUID value)` with `static random()`, `static parse(String)`,
  `toString()` the UUID; `enum Orientation { HORIZONTAL, VERTICAL }`; `enum Side { LEFT, RIGHT, UP, DOWN }` with
  `Orientation orientation()` and `boolean leading()`; `sealed interface Node permits Split, Group`;
  `record Child(Node node, double weight)`; `record Split(Orientation orientation, List<Child> children) implements Node`
  (package-private `static final double TOLERANCE = 1e-9`); `record Group(GroupId id, List<TabId> tabs,
  Optional<TabId> selected) implements Node`; `record Region(double x, double y, double width, double height)` with
  `right()`, `bottom()`, `centerX()`, `centerY()`; `final class Layout` with `Layout(Node, GroupId, Optional<GroupId>)`,
  `static Layout empty()`, `static Layout of(TabId)`, `Node root()`, `GroupId activeGroup()`,
  `Optional<GroupId> maximized()`, `List<GroupId> recent()`, `List<Group> groups()`, `Optional<Group> group(GroupId)`,
  `List<TabId> tabs()`, `Optional<GroupId> groupOf(TabId)`, `boolean contains(TabId)`, `boolean isEmpty()`,
  `Optional<TabId> activeTab()`; private: the groups' order of use and each group's tab order of use
  (`tabOrders`, seeded by `tabOrder(Group, List<TabId>)`), which Task 2's `remove` reads. Test fixtures `LayoutFixtures.tab(int)`, `groupId(int)`, `group(int)`,
  `group(int id, int selected, int... tabs)`, `child(Node, double)`, `h(Child...)`, `v(Child...)`,
  `layout(Node, int active)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/layout/LayoutFixtures.java`:

```java
package dev.jasper.app.layout;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Short names for layout tests: ids by number, groups, children, splits and layouts. */
final class LayoutFixtures {
    private LayoutFixtures() { }

    static TabId tab(int n) {
        return new TabId(new UUID(0, n));
    }

    static GroupId groupId(int n) {
        return new GroupId(new UUID(1, n));
    }

    /** Group {@code id} holding the one tab of the same number. */
    static Group group(int id) {
        return group(id, id, id);
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(LayoutFixtures::tab).toList(), Optional.of(tab(selected)));
    }

    static Child child(Node node, double weight) {
        return new Child(node, weight);
    }

    static Split h(Child... children) {
        return new Split(Orientation.HORIZONTAL, List.of(children));
    }

    static Split v(Child... children) {
        return new Split(Orientation.VERTICAL, List.of(children));
    }

    /** A layout over {@code root} with group {@code active} active and nothing maximized. */
    static Layout layout(Node root, int active) {
        return new Layout(root, groupId(active), Optional.empty());
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/layout/LayoutValuesTest.java`:

```java
package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 7.1 and 7.2: the layout's values and their invariants. */
class LayoutValuesTest {
    @Test void idsAreUuidsWithFreshFactoriesAndStrictParsing() {
        assertThat(TabId.random()).isNotEqualTo(TabId.random());
        assertThat(GroupId.random()).isNotEqualTo(GroupId.random());
        TabId tab = TabId.random();
        assertThat(tab.toString()).isEqualTo(tab.value().toString());
        assertThat(TabId.parse(tab.toString())).isEqualTo(tab);
        assertThat(GroupId.parse("3F1E0000-0000-4000-8000-00000000000A")).as("either case")
            .isEqualTo(new GroupId(UUID.fromString("3f1e0000-0000-4000-8000-00000000000a")));
        for (String bad : List.of("", "1-2-3-4-5", "3f1e0000-0000-4000-8000-00000000000", "3f1e0000x0000-4000-8000-00000000000a",
                "not a uuid at all, thirty-six chars!")) {
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> TabId.parse(bad));
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> GroupId.parse(bad));
        }
        assertThatNullPointerException().isThrownBy(() -> new TabId(null));
        assertThatNullPointerException().isThrownBy(() -> new GroupId(null));
        assertThatNullPointerException().isThrownBy(() -> GroupId.parse(null));
    }

    @Test void aGroupSelectsOneOfItsTabsAndAnEmptyGroupSelectsNothing() {
        assertThat(group(1, 2, 1, 2).selected()).contains(tab(2));
        assertThat(new Group(groupId(1), List.of(), Optional.empty()).tabs()).isEmpty();
        assertThatIllegalArgumentException().as("a tab it does not hold").isThrownBy(() -> group(1, 3, 1, 2));
        assertThatIllegalArgumentException().as("an empty group selects nothing")
            .isThrownBy(() -> new Group(groupId(1), List.of(), Optional.of(tab(1))));
        assertThatIllegalArgumentException().as("a group with tabs selects one")
            .isThrownBy(() -> new Group(groupId(1), List.of(tab(1)), Optional.empty()));
        assertThatIllegalArgumentException().as("a tab twice").isThrownBy(() -> group(1, 1, 1, 1));
        assertThatNullPointerException().isThrownBy(() -> new Group(null, List.of(), Optional.empty()));
        var tabs = new ArrayList<>(List.of(tab(1)));
        Group copied = new Group(groupId(1), tabs, Optional.of(tab(1)));
        tabs.add(tab(2));
        assertThat(copied.tabs()).containsExactly(tab(1));
    }

    @Test void childWeightsArePositiveAndFinite() {
        for (double bad : new double[] {0, -0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThatIllegalArgumentException().as("%s", bad).isThrownBy(() -> new Child(group(1), bad)).withMessageContaining("weight");
        assertThatNullPointerException().isThrownBy(() -> new Child(null, 1));
        assertThat(new Child(group(1), 1e-9).weight()).isEqualTo(1e-9);
    }

    @Test void aSplitNeedsTwoChildrenNormalizesItsWeightsAndFlattensItsOwnOrientation() {
        Group a = group(1), b = group(2), c = group(3);
        assertThatIllegalArgumentException().isThrownBy(() -> h(child(a, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Split(Orientation.HORIZONTAL, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new Split(null, List.of(child(a, 1), child(b, 1))));
        assertThat(h(child(a, 1), child(b, 3)).children()).extracting(Child::weight).containsExactly(0.25, 0.75);
        double third = 1.0 / 3;
        assertThat(h(child(a, third), child(b, third), child(c, third)).children()).extracting(Child::weight)
            .as("already normalized: kept bit for bit").containsExactly(third, third, third);
        assertThat(h(child(a, 0.5), child(h(child(b, 0.5), child(c, 0.5)), 0.5))).as("a child of the same orientation is flattened")
            .isEqualTo(h(child(a, 0.5), child(b, 0.25), child(c, 0.25)));
        assertThat(h(child(a, 2), child(h(child(b, 1), child(c, 3)), 2))).as("flattened, then normalized")
            .isEqualTo(h(child(a, 0.5), child(b, 0.125), child(c, 0.375)));
        Split nested = h(child(a, 0.5), child(v(child(b, 0.5), child(c, 0.5)), 0.5));
        assertThat(nested.children()).hasSize(2);
        assertThat(nested.children().get(1).node()).isInstanceOf(Split.class);
        var source = new ArrayList<>(List.of(child(a, 0.5), child(b, 0.5)));
        Split copied = new Split(Orientation.VERTICAL, source);
        source.clear();
        assertThat(copied.children()).hasSize(2);
    }

    @Test void sidesKnowTheirOrientationAndOrder() {
        assertThat(Side.LEFT.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(Side.RIGHT.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(Side.UP.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(Side.DOWN.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(Side.LEFT.leading()).isTrue();
        assertThat(Side.UP.leading()).isTrue();
        assertThat(Side.RIGHT.leading()).isFalse();
        assertThat(Side.DOWN.leading()).isFalse();
    }

    @Test void aRegionKnowsItsEdgesAndCentre() {
        Region region = new Region(0.25, 0.5, 0.5, 0.25);
        assertThat(region.right()).isEqualTo(0.75);
        assertThat(region.bottom()).isEqualTo(0.75);
        assertThat(region.centerX()).isEqualTo(0.5);
        assertThat(region.centerY()).isEqualTo(0.625);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/layout/LayoutTest.java`:

```java
package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.layout;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 7.1 and 7.2: a layout's construction, invariants and queries. */
class LayoutTest {
    @Test void theEmptyWorkspaceIsOneEmptyActiveGroup() {
        Layout empty = Layout.empty();
        Group group = (Group) empty.root();
        assertThat(group.tabs()).isEmpty();
        assertThat(group.selected()).isEmpty();
        assertThat(empty.activeGroup()).isEqualTo(group.id());
        assertThat(empty.maximized()).isEmpty();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.tabs()).isEmpty();
        assertThat(empty.groups()).containsExactly(group);
        assertThat(empty.recent()).containsExactly(group.id());
        assertThat(empty.activeTab()).isEmpty();
        assertThat(Layout.empty().activeGroup()).as("a fresh group each time").isNotEqualTo(empty.activeGroup());
    }

    @Test void ofHoldsOneSelectedTabInOneActiveGroup() {
        Layout one = Layout.of(tab(1));
        GroupId id = one.activeGroup();
        assertThat(one.root()).isEqualTo(new Group(id, List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
        assertThat(one.contains(tab(1))).isTrue();
        assertThat(one.groupOf(tab(1))).contains(id);
        assertThat(one.activeTab()).contains(tab(1));
        assertThatNullPointerException().isThrownBy(() -> Layout.of(null));
    }

    @Test void theConstructorChecksTheInvariants() {
        Group a = group(1), b = group(2);
        assertThatIllegalArgumentException().as("a group twice")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(a, 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("a tab in two groups")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(group(2, 1, 1), 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("an empty group beside another")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(new Group(groupId(2), List.of(), Optional.empty()), 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("an active group outside the tree")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(3), Optional.empty()));
        assertThatIllegalArgumentException().as("a maximized group that is not active")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(1), Optional.of(groupId(2))));
        assertThatIllegalArgumentException().as("a maximized group outside the tree")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(1), Optional.of(groupId(3))));
        assertThatIllegalArgumentException().as("one group cannot be maximized")
            .isThrownBy(() -> new Layout(a, groupId(1), Optional.of(groupId(1))));
        assertThatNullPointerException().isThrownBy(() -> new Layout(null, groupId(1), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new Layout(a, null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new Layout(a, groupId(1), null));
        assertThat(new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty()).isEmpty())
            .as("the empty workspace's group may be empty").isTrue();
        assertThat(new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(2), Optional.of(groupId(2))).maximized()).contains(groupId(2));
    }

    @Test void queriesListGroupsAndTabsInWorkspaceOrder() {
        Group a = group(1, 2, 1, 2), b = group(2, 3, 3), c = group(3, 4, 4);
        Layout grid = layout(h(child(a, 0.5), child(v(child(b, 0.5), child(c, 0.5)), 0.5)), 3);
        assertThat(grid.groups()).containsExactly(a, b, c);
        assertThat(grid.tabs()).containsExactly(tab(1), tab(2), tab(3), tab(4));
        assertThat(grid.group(groupId(2))).contains(b);
        assertThat(grid.group(groupId(9))).isEmpty();
        assertThat(grid.groupOf(tab(4))).contains(groupId(3));
        assertThat(grid.groupOf(tab(9))).isEmpty();
        assertThat(grid.contains(tab(2))).isTrue();
        assertThat(grid.contains(tab(9))).isFalse();
        assertThat(grid.activeTab()).contains(tab(4));
        assertThat(grid.isEmpty()).isFalse();
    }

    @Test void theOrderOfUseStartsWithTheActiveGroupThenFollowsTheTree() {
        Layout row = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 2);
        assertThat(row.recent()).containsExactly(groupId(2), groupId(1), groupId(3));
    }

    @Test void equalLayoutsHaveEqualHashCodes() {
        Layout first = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 2);
        Layout second = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 2);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*'`
Expected: FAIL to compile: "package dev.jasper.app.layout does not exist" or "cannot find symbol … TabId", "… Layout".

- [ ] **Step 3: Create the package**

Create `jasper-app/src/main/java/dev/jasper/app/layout/package-info.java`:

```java
/**
 * The workspace layout model (workspace tabs spec, section 7): tab and group ids, a workspace's tab groups in nested
 * weighted splits, its active and maximized group and the groups' order of use, the operations that return a new
 * {@code Layout}, and each group's region of the unit square. JDK-only: nothing from Swing, AWT or a file format;
 * {@code verifyApplicationArchitecture} enforces it. Immutable values, safe on any thread; whoever holds a layout owns
 * replacing it.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.layout;
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Ids.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/** Parsing shared by {@link TabId} and {@link GroupId}. */
final class Ids {
    private Ids() { }

    /** The UUID {@code text} names in canonical form (36 characters, hyphenated, either case); anything else is rejected. */
    static UUID uuid(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 36) {
            try {
                UUID value = UUID.fromString(text);
                if (value.toString().equalsIgnoreCase(text)) return value;
            } catch (IllegalArgumentException malformed) {
                // Rejected below, naming the text.
            }
        }
        throw new IllegalArgumentException("Not a canonical UUID: " + text);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/TabId.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A tab's identity, unique in the application for the tab's life and kept in {@code workspace.toml}.
 *
 * @param value the UUID
 */
public record TabId(UUID value) {
    /** Checks the value is present. */
    public TabId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static TabId random() {
        return new TabId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static TabId parse(String text) {
        return new TabId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/GroupId.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A tab group's identity, unique in the application and kept in {@code workspace.toml}.
 *
 * @param value the UUID
 */
public record GroupId(UUID value) {
    /** Checks the value is present. */
    public GroupId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static GroupId random() {
        return new GroupId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static GroupId parse(String text) {
        return new GroupId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Orientation.java`:

```java
package dev.jasper.app.layout;

/** How a split lays out its children: HORIZONTAL from left to right, VERTICAL from top to bottom. */
public enum Orientation { HORIZONTAL, VERTICAL }
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Side.java`:

```java
package dev.jasper.app.layout;

/** A side of a group, where a split puts the new group; also a direction to a neighbouring group. */
public enum Side {
    LEFT, RIGHT, UP, DOWN;

    /**
     * The orientation of a split along this side.
     *
     * @return HORIZONTAL for LEFT and RIGHT, VERTICAL for UP and DOWN
     */
    public Orientation orientation() {
        return this == LEFT || this == RIGHT ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    }

    /**
     * Whether a group put on this side comes first in its split.
     *
     * @return true for LEFT and UP
     */
    public boolean leading() {
        return this == LEFT || this == UP;
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Node.java`:

```java
package dev.jasper.app.layout;

/** A node of a layout's tree: a split of nodes, or a group of tabs at a leaf. */
public sealed interface Node permits Split, Group { }
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Child.java`:

```java
package dev.jasper.app.layout;

import java.util.Objects;

/**
 * One child of a {@link Split} and its share of the split's length.
 *
 * @param node the child
 * @param weight its share: positive and finite; the split normalizes its children's weights to sum to 1
 */
public record Child(Node node, double weight) {
    /** Checks the node is present and the weight positive and finite. */
    public Child {
        Objects.requireNonNull(node, "node");
        if (!(weight > 0 && Double.isFinite(weight))) throw new IllegalArgumentException("A weight must be positive and finite: " + weight);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Split.java`:

```java
package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Children laid out along one axis, each taking its weight's share of the length (spec 7.1). Construction flattens a
 * child split of the same orientation into this one, scaling its children's weights by its own; needs at least two
 * children after that; and normalizes the weights to sum to 1, keeping weights that already do (within
 * {@code 1e-9}) bit for bit.
 *
 * @param orientation HORIZONTAL lays the children out from left to right, VERTICAL from top to bottom
 * @param children the children in order: at least two, none a split of this orientation, weights summing to 1
 */
public record Split(Orientation orientation, List<Child> children) implements Node {
    /** Weights this close to summing to 1 are kept as they are, so normalizing twice changes nothing. */
    static final double TOLERANCE = 1e-9;

    /** Flattens, checks and normalizes the children. */
    public Split {
        Objects.requireNonNull(orientation, "orientation");
        var flat = new ArrayList<Child>();
        for (Child child : children) {
            Objects.requireNonNull(child, "child");
            if (child.node() instanceof Split nested && nested.orientation() == orientation) {
                for (Child inner : nested.children()) flat.add(new Child(inner.node(), inner.weight() * child.weight()));
            } else {
                flat.add(child);
            }
        }
        if (flat.size() < 2) throw new IllegalArgumentException("A split needs at least two children: " + flat.size());
        double sum = 0;
        for (Child child : flat) sum += child.weight();
        if (Math.abs(sum - 1) > TOLERANCE) {
            double total = sum;
            flat.replaceAll(child -> new Child(child.node(), child.weight() / total));
        }
        children = List.copyOf(flat);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Group.java`:

```java
package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A tab group, a leaf of the tree: its tabs in strip order and the one it shows (spec 7.1).
 *
 * @param id the group's id
 * @param tabs its tabs in order, none twice
 * @param selected the tab it shows, one of {@code tabs}; empty exactly when there are none, which only the empty
 *     workspace's group may be
 */
public record Group(GroupId id, List<TabId> tabs, Optional<TabId> selected) implements Node {
    /** Copies the tabs and checks the selection. */
    public Group {
        Objects.requireNonNull(id, "id");
        tabs = List.copyOf(tabs);
        Objects.requireNonNull(selected, "selected");
        if (Set.copyOf(tabs).size() != tabs.size()) throw new IllegalArgumentException("Group " + id + " lists a tab twice");
        if (tabs.isEmpty() != selected.isEmpty())
            throw new IllegalArgumentException(tabs.isEmpty() ? "The empty group " + id + " selects nothing" : "Group " + id + " selects one of its tabs");
        if (selected.isPresent() && !tabs.contains(selected.get()))
            throw new IllegalArgumentException("Group " + id + " does not hold its selected tab " + selected.get());
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Region.java`:

```java
package dev.jasper.app.layout;

/**
 * A group's place in its workspace's unit square, from the top left.
 *
 * @param x the left edge, 0 to 1
 * @param y the top edge, 0 to 1
 * @param width the width, 0 to 1
 * @param height the height, 0 to 1
 */
public record Region(double x, double y, double width, double height) {
    /**
     * The right edge.
     *
     * @return {@code x + width}
     */
    public double right() {
        return x + width;
    }

    /**
     * The bottom edge.
     *
     * @return {@code y + height}
     */
    public double bottom() {
        return y + height;
    }

    /**
     * The horizontal centre.
     *
     * @return {@code x + width / 2}
     */
    public double centerX() {
        return x + width / 2;
    }

    /**
     * The vertical centre.
     *
     * @return {@code y + height / 2}
     */
    public double centerY() {
        return y + height / 2;
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java`:

```java
package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One workspace's layout (spec 7): a tree of splits whose leaves are tab groups, the active group, the maximized group
 * if any, the groups' order of use and each group's tab order of use. Immutable: each operation returns a new layout and
 * rejects arguments that would break an invariant of spec 7.2 with an {@link IllegalArgumentException}. Equality covers
 * the tree, the active group and the maximized group; the orders of use only decide focus and selection after a removal
 * and are not saved.
 */
public final class Layout {
    private final Node root;
    private final GroupId activeGroup;
    private final Optional<GroupId> maximized;
    private final List<GroupId> recent;
    private final Map<GroupId, List<TabId>> tabOrders;
    private final List<Group> groups;

    /**
     * A layout over {@code root}. Its order of use starts with {@code activeGroup}, then the other groups in tree order;
     * each group's tab order of use starts with its selected tab, then the tabs after it, then those before it, nearest
     * first (workspace plan 2, Rulings 7).
     *
     * @param root the tree: no group or tab twice, and no empty group unless it is the root (the empty workspace)
     * @param activeGroup a group of the tree
     * @param maximized the active group while it fills the workspace, which needs at least two groups; else empty
     * @throws IllegalArgumentException when an invariant does not hold
     */
    public Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized) {
        this(root, activeGroup, maximized, List.of(), Map.of());
    }

    private Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized, List<GroupId> recent,
            Map<GroupId, List<TabId>> tabOrders) {
        this.root = Objects.requireNonNull(root, "root");
        this.activeGroup = Objects.requireNonNull(activeGroup, "activeGroup");
        this.maximized = Objects.requireNonNull(maximized, "maximized");
        var found = new ArrayList<Group>();
        collect(root, found);
        var groupIds = new HashSet<GroupId>();
        var tabIds = new HashSet<TabId>();
        for (Group group : found) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in the layout twice");
            for (TabId tab : group.tabs())
                if (!tabIds.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in the layout twice");
            if (group.tabs().isEmpty() && !(root instanceof Group))
                throw new IllegalArgumentException("Only the empty workspace's group may be empty: " + group.id());
        }
        if (!groupIds.contains(activeGroup))
            throw new IllegalArgumentException("The active group " + activeGroup + " is not in the layout");
        if (maximized.isPresent() && !maximized.get().equals(activeGroup))
            throw new IllegalArgumentException("The maximized group " + maximized.get() + " is not the active group");
        if (maximized.isPresent() && root instanceof Group)
            throw new IllegalArgumentException("A layout with one group cannot be maximized");
        var order = new LinkedHashSet<GroupId>();
        order.add(activeGroup);
        for (GroupId id : recent) if (groupIds.contains(id)) order.add(id);
        for (Group group : found) order.add(group.id());
        this.recent = List.copyOf(order);
        var orders = new HashMap<GroupId, List<TabId>>();
        for (Group group : found) orders.put(group.id(), tabOrder(group, tabOrders.getOrDefault(group.id(), List.of())));
        this.tabOrders = Map.copyOf(orders);
        this.groups = List.copyOf(found);
    }

    /**
     * The empty workspace (spec 7.2): one new, empty group, active.
     *
     * @return the layout
     */
    public static Layout empty() {
        GroupId id = GroupId.random();
        return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty());
    }

    /**
     * One new group holding {@code tab}, selected and active.
     *
     * @param tab the tab
     * @return the layout
     */
    public static Layout of(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        GroupId id = GroupId.random();
        return new Layout(new Group(id, List.of(tab), Optional.of(tab)), id, Optional.empty());
    }

    /**
     * The tree.
     *
     * @return the root node
     */
    public Node root() {
        return root;
    }

    /**
     * The group that has focus, first in the order of use.
     *
     * @return its id
     */
    public GroupId activeGroup() {
        return activeGroup;
    }

    /**
     * The group that fills the workspace, always the active group.
     *
     * @return its id, or empty when no group is maximized
     */
    public Optional<GroupId> maximized() {
        return maximized;
    }

    /**
     * The groups' order of use, most recent first; not part of equality or the file.
     *
     * @return every group's id, the active group first
     */
    public List<GroupId> recent() {
        return recent;
    }

    /**
     * Every group in tree order: depth first, children in order.
     *
     * @return the groups
     */
    public List<Group> groups() {
        return groups;
    }

    /**
     * The group with this id.
     *
     * @param id the group's id
     * @return the group, or empty when it is not in the layout
     */
    public Optional<Group> group(GroupId id) {
        Objects.requireNonNull(id, "id");
        for (Group group : groups) if (group.id().equals(id)) return Optional.of(group);
        return Optional.empty();
    }

    /**
     * Every tab in workspace order: the groups in tree order, each group's tabs in strip order.
     *
     * @return the tabs
     */
    public List<TabId> tabs() {
        var tabs = new ArrayList<TabId>();
        for (Group group : groups) tabs.addAll(group.tabs());
        return List.copyOf(tabs);
    }

    /**
     * The group holding the tab.
     *
     * @param tab the tab
     * @return its group's id, or empty when the tab is not in the layout
     */
    public Optional<GroupId> groupOf(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        for (Group group : groups) if (group.tabs().contains(tab)) return Optional.of(group.id());
        return Optional.empty();
    }

    /**
     * Whether a group holds the tab.
     *
     * @param tab the tab
     * @return true when the layout holds it
     */
    public boolean contains(TabId tab) {
        return groupOf(tab).isPresent();
    }

    /**
     * Whether this is the empty workspace: one group and no tabs.
     *
     * @return true for the empty workspace
     */
    public boolean isEmpty() {
        return root instanceof Group group && group.tabs().isEmpty();
    }

    /**
     * The active group's selected tab.
     *
     * @return the tab, or empty in the empty workspace
     */
    public Optional<TabId> activeTab() {
        return group(activeGroup).flatMap(Group::selected);
    }

    /** Equal when the trees, the active groups and the maximized groups are equal; the order of use is ignored. */
    @Override public boolean equals(Object other) {
        return other instanceof Layout layout && root.equals(layout.root) && activeGroup.equals(layout.activeGroup)
            && maximized.equals(layout.maximized);
    }

    @Override public int hashCode() {
        return Objects.hash(root, activeGroup, maximized);
    }

    @Override public String toString() {
        return "Layout[root=" + root + ", activeGroup=" + activeGroup + ", maximized=" + maximized + "]";
    }

    /** Adds the groups under {@code node} to {@code into}, in tree order. */
    private static void collect(Node node, List<Group> into) {
        switch (node) {
            case Group group -> into.add(group);
            case Split split -> split.children().forEach(child -> collect(child.node(), into));
        }
    }

    /**
     * A group's tabs in order of use (workspace plan 2, Rulings 7): its selected tab, then the others in
     * {@code previous}'s order, then any tab {@code previous} lacks: those after the selected tab in strip order, then
     * those before it, nearest first, so that closing tabs never shown behaves as {@code JTabbedPane.removeTabAt}.
     */
    private static List<TabId> tabOrder(Group group, List<TabId> previous) {
        var order = new LinkedHashSet<TabId>();
        group.selected().ifPresent(order::add);
        for (TabId tab : previous) if (group.tabs().contains(tab)) order.add(tab);
        int at = group.selected().map(group.tabs()::indexOf).orElse(0);
        for (int i = at + 1; i < group.tabs().size(); i++) order.add(group.tabs().get(i));
        for (int i = at - 1; i >= 0; i--) order.add(group.tabs().get(i));
        return List.copyOf(order);
    }
}
```

- [ ] **Step 4: Keep the package JDK-only and inventory it**

In `gradle/application-architecture.gradle.kts`, replace:

```kotlin
                if (module == app && packageOf(from) == "dev.jasper.app.dock")
                    check(to.startsWith("dev.jasper.app.dock.") || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
                    }
```

with:

```kotlin
                if (module == app && packageOf(from) == "dev.jasper.app.dock")
                    check(to.startsWith("dev.jasper.app.dock.") || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
                    }
                // The workspace layout model is pure: JDK types only and nothing from java.awt.
                if (module == app && packageOf(from) == "dev.jasper.app.layout")
                    check(to.startsWith("dev.jasper.app.layout.") || (to.startsWith("java.") && !to.startsWith("java.awt."))) {
                        "dev.jasper.app.layout is JDK-only and holds no Swing, AWT or file format: $line"
                    }
```

Inventory the new files that `ThemeMigrationCoverageTest`'s pattern matches (each holds `new ArrayList<`; fact 14),
appending to the end of `docs/design/theme-engine/migration.csv`:

```bash
python3 -I - <<'PY2'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/layout/Split.java,app.layout,Split,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/layout/LayoutValuesTest.java,New in workspace tabs plan 2 Task 1; pure layout value; no Swing or AWT",
    "jasper-app/src/main/java/dev/jasper/app/layout/Layout.java,app.layout,Layout,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/layout/LayoutTest.java,New in workspace tabs plan 2 Task 1; pure layout model; no Swing or AWT",
    "jasper-app/src/test/java/dev/jasper/app/layout/LayoutValuesTest.java,app.layout,LayoutValuesTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/layout/LayoutValuesTest.java,Test/fixture ownership; New in workspace tabs plan 2 Task 1",
]
listed = {line.split(",", 1)[0] for line in text.splitlines()}
for row in rows:
    if row.split(",", 1)[0] in listed: raise SystemExit("already listed: " + row.split(",", 1)[0])
    if len(row.split(",")) != 8: raise SystemExit("not 8 columns: " + row)
if not text.endswith("\n"): raise SystemExit("migration.csv does not end with a newline")
path.write_text(text + "".join(row + "\n" for row in rows), encoding="utf-8")
print(len(rows), "rows added")
PY2
```

Expected: `3 rows added`.

- [ ] **Step 5: Run the tests, the architecture check and the Javadoc**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*' --tests 'dev.jasper.app.documentation.AppDocumentationTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL; `LayoutValuesTest` 6 tests and `LayoutTest` 6 tests pass; `ThemeMigrationCoverageTest` passes
with the three new rows.

- [ ] **Step 6: Prove the rule bites, then remove the probe**

```bash
cat > jasper-app/src/main/java/dev/jasper/app/layout/ArchitectureProbe.java <<'EOF'
package dev.jasper.app.layout;

/** Temporary: proves verifyApplicationArchitecture rejects AWT here. Deleted in this step. */
final class ArchitectureProbe {
    java.awt.Point point() { return new java.awt.Point(); }
}
EOF
./gradlew verifyApplicationArchitecture; echo "exit $?"
rm jasper-app/src/main/java/dev/jasper/app/layout/ArchitectureProbe.java
./gradlew verifyApplicationArchitecture
```

Expected: the first run FAILS with `dev.jasper.app.layout is JDK-only and holds no Swing, AWT or file format:` and a line
naming `dev.jasper.app.layout.ArchitectureProbe -> java.awt.Point`, then `exit 1`; after the `rm`, BUILD SUCCESSFUL.
`git status --short` must not list `ArchitectureProbe.java`.

- [ ] **Step 7: Commit**

```bash
git add gradle/application-architecture.gradle.kts docs/design/theme-engine/migration.csv jasper-app/src/main/java/dev/jasper/app/layout jasper-app/src/test/java/dev/jasper/app/layout
git commit -F - <<'EOF'
Workspace layout: the JDK-only layout values

dev.jasper.app.layout holds the tab and group ids, splits, children,
groups and regions of the workspace tabs design (spec 7.1 and 7.2) and
the Layout constructor with its queries; verifyApplicationArchitecture
keeps the package JDK-only.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Opening, selecting, activating, moving and removing tabs

Spec 7.3's tab and group operations with Rulings 5 to 9: the maximize rule (closing a tab keeps it, a move clears it),
`select` activating, the selection after a removal (the group's most recently shown tab), the heir of a removed group's
weight, and the groups' order of use after a removal.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/LayoutOperationsTest.java`

**Interfaces:**
- Consumes: Task 1's values, `Layout` (its private five-argument constructor and `tabOrders`) and `LayoutFixtures`.
- Produces: `Layout open(TabId tab, GroupId group, int index)`, `Layout select(TabId tab)`,
  `Layout activate(GroupId group)`, `Layout move(TabId tab, GroupId group, int index)`, `Layout remove(TabId tab)`;
  private `activated(Node, GroupId, boolean structural)`, `requireGroup(GroupId)`, `requireGroupOf(TabId)`,
  `checkIndex(int, int)`, `replace(Node, GroupId, Function<Group, Node>)`, `without(Node, GroupId)`,
  `holds(Node, GroupId)` (Task 3 uses `activated`, `requireGroup`, `requireGroupOf` and `replace`). Every private
  constructor call passes `recent` and `tabOrders` on.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/layout/LayoutOperationsTest.java`:

```java
package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.layout;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 7.3: opening, selecting, activating, moving and removing tabs, the order of use and the maximize. */
class LayoutOperationsTest {
    /** Group 1 holds tabs 1 and 2 (1 shown), group 2 holds tab 3; side by side, group 2 active. */
    static Layout pair() {
        return layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 2);
    }

    @Test void openInsertsSelectsAndActivates() {
        Layout opened = pair().open(tab(9), groupId(1), 1);
        assertThat(opened.group(groupId(1))).contains(group(1, 9, 1, 9, 2));
        assertThat(opened.activeGroup()).isEqualTo(groupId(1));
        assertThat(opened.recent()).containsExactly(groupId(1), groupId(2));
        assertThat(pair().open(tab(9), groupId(2), 1).group(groupId(2))).contains(group(2, 9, 3, 9));
        assertThat(pair().open(tab(9), groupId(1), 0).tabs()).containsExactly(tab(9), tab(1), tab(2), tab(3));
    }

    @Test void openRejectsAKnownTabAnUnknownGroupAndAnIndexOutsideTheGroup() {
        Layout pair = pair();
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(3), groupId(1), 0)).withMessageContaining(tab(3).toString());
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(7), 0)).withMessageContaining(groupId(7).toString());
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(1), -1));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(1), 3));
        assertThatNullPointerException().isThrownBy(() -> pair.open(null, groupId(1), 0));
    }

    @Test void openFillsTheEmptyWorkspacesGroup() {
        Layout empty = Layout.empty();
        Layout one = empty.open(tab(1), empty.activeGroup(), 0);
        assertThat(one.root()).isEqualTo(new Group(empty.activeGroup(), List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
    }

    @Test void selectShowsTheTabAndActivatesItsGroup() {
        Layout selected = pair().select(tab(2));
        assertThat(selected.group(groupId(1))).contains(group(1, 2, 1, 2));
        assertThat(selected.activeGroup()).isEqualTo(groupId(1));
        assertThat(selected.activeTab()).contains(tab(2));
        assertThatIllegalArgumentException().isThrownBy(() -> pair().select(tab(9))).withMessageContaining(tab(9).toString());
    }

    @Test void activateMovesTheGroupToTheFrontOfTheOrderOfUse() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        assertThat(three.recent()).containsExactly(groupId(1), groupId(2), groupId(3));
        Layout used = three.activate(groupId(3)).activate(groupId(2));
        assertThat(used.recent()).containsExactly(groupId(2), groupId(3), groupId(1));
        assertThat(used.activeGroup()).isEqualTo(groupId(2));
        assertThat(used.root()).isEqualTo(three.root());
        assertThatIllegalArgumentException().isThrownBy(() -> three.activate(groupId(9)));
    }

    @Test void equalityIgnoresTheOrderOfUse() {
        Layout direct = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 3);
        Layout used = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(2)).activate(groupId(3));
        assertThat(used).isEqualTo(direct).hasSameHashCodeAs(direct);
        assertThat(used.recent()).containsExactly(groupId(3), groupId(2), groupId(1));
        assertThat(direct.recent()).containsExactly(groupId(3), groupId(1), groupId(2));
    }

    @Test void removingTheSelectedTabSelectsTheGroupsMostRecentlyShownTab() {
        Layout row = layout(h(child(group(1, 1, 1, 2, 3), 0.5), child(group(2, 4, 4), 0.5)), 1);
        Layout used = row.select(tab(3)).select(tab(1));
        assertThat(used.remove(tab(1)).group(groupId(1))).as("tab 3, not the neighbour").contains(group(1, 3, 2, 3));
        assertThat(used).as("the tabs' order of use is not part of equality").isEqualTo(row).hasSameHashCodeAs(row);
        assertThat(row.remove(tab(2)).group(groupId(1))).as("another tab: the selection stays").contains(group(1, 1, 1, 3));
        assertThat(row.remove(tab(2)).activeGroup()).isEqualTo(groupId(1));
        Layout moved = row.move(tab(4), groupId(1), 3).select(tab(2));
        assertThat(moved.remove(tab(2)).activeTab()).as("a tab moved in counts as shown").contains(tab(4));
    }

    @Test void tabsNeverShownAreReachedAsJTabbedPaneReachesThem() {
        Layout row = layout(h(child(group(1, 2, 1, 2, 3, 4), 0.5), child(group(2, 5, 5), 0.5)), 1);
        Layout closed = row.remove(tab(2));
        assertThat(closed.activeTab()).as("the tab that takes its place").contains(tab(3));
        closed = closed.remove(tab(3));
        assertThat(closed.activeTab()).contains(tab(4));
        closed = closed.remove(tab(4));
        assertThat(closed.activeTab()).as("the last: the one before it").contains(tab(1));
    }

    @Test void anEmptiedGroupGivesItsSpaceToTheSiblingBeforeItElseAfter() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.5), child(group(3), 0.25)), 1);
        assertThat(three.remove(tab(2)).root()).as("the sibling before").isEqualTo(h(child(group(1), 0.75), child(group(3), 0.25)));
        assertThat(three.remove(tab(1)).root()).as("the first: the sibling after").isEqualTo(h(child(group(2), 0.75), child(group(3), 0.25)));
    }

    @Test void aSplitLeftWithOneChildCollapsesAndNestingFlattensAgain() {
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(h(child(group(3), 0.5), child(group(4), 0.5)), 0.5)), 0.5)), 1);
        assertThat(nested.remove(tab(2)).root()).isEqualTo(h(child(group(1), 0.5), child(group(3), 0.25), child(group(4), 0.25)));
    }

    @Test void theActiveGroupAfterARemovalIsTheMostRecentlyUsedRemainingGroup() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(3)).activate(groupId(2));
        Layout afterTwo = three.remove(tab(2));
        assertThat(afterTwo.activeGroup()).as("not the first in the tree nor the sibling before").isEqualTo(groupId(3));
        assertThat(afterTwo.recent()).containsExactly(groupId(3), groupId(1));
        assertThat(afterTwo.remove(tab(3)).activeGroup()).isEqualTo(groupId(1));
        assertThat(three.activate(groupId(1)).remove(tab(3)).activeGroup()).as("the active group stays when another goes").isEqualTo(groupId(1));
    }

    @Test void removalDownToTheEmptyWorkspaceKeepsItsGroupForTheNextTab() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout one = pair.remove(tab(1));
        assertThat(one.root()).as("the split collapses into the remaining group").isEqualTo(group(2));
        assertThat(one.activeGroup()).isEqualTo(groupId(2));
        Layout empty = one.remove(tab(2));
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.root()).isEqualTo(new Group(groupId(2), List.of(), Optional.empty()));
        assertThat(empty.activeGroup()).isEqualTo(groupId(2));
        assertThat(empty.tabs()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> empty.remove(tab(2)));
        assertThat(empty.open(tab(5), groupId(2), 0).root()).as("the next tab goes into the same group").isEqualTo(group(2, 5, 5));
    }

    @Test void moveReordersWithinAGroupCountingWithoutTheMovedTab() {
        Layout row = layout(h(child(group(1, 1, 1, 2, 3), 0.5), child(group(2, 4, 4), 0.5)), 2);
        Layout moved = row.move(tab(1), groupId(1), 2);
        assertThat(moved.group(groupId(1))).contains(group(1, 1, 2, 3, 1));
        assertThat(moved.activeGroup()).isEqualTo(groupId(1));
        assertThat(row.move(tab(3), groupId(1), 0).group(groupId(1))).contains(group(1, 3, 3, 1, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(1), groupId(1), 3));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(9), groupId(1), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(1), groupId(9), 0));
    }

    @Test void moveBetweenGroupsSelectsActivatesAndRemovesAnEmptiedSource() {
        Layout pair = layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 1);
        Layout across = pair.move(tab(1), groupId(2), 0);
        assertThat(across.root()).isEqualTo(h(child(group(1, 2, 2), 0.5), child(group(2, 1, 1, 3), 0.5)));
        assertThat(across.activeGroup()).isEqualTo(groupId(2));
        Layout emptied = pair.move(tab(3), groupId(1), 1);
        assertThat(emptied.root()).isEqualTo(group(1, 3, 1, 3, 2));
        assertThat(emptied.activeGroup()).isEqualTo(groupId(1));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.move(tab(3), groupId(1), 3));
    }

    @Test void theMaximizeFollowsTheActiveGroupClearsOnMovesAndSurvivesClosedTabs() {
        Layout max = new Layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3, 4), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(max.open(tab(9), groupId(2), 0).maximized()).as("a tab opens in it").contains(groupId(2));
        assertThat(max.open(tab(9), groupId(1), 0).maximized()).as("a tab opens in another group").contains(groupId(1));
        assertThat(max.select(tab(2)).maximized()).as("a tab of another group is selected").contains(groupId(1));
        assertThat(max.activate(groupId(1)).maximized()).as("another group is activated").contains(groupId(1));
        assertThat(max.move(tab(4), groupId(2), 0).maximized()).as("a reorder").contains(groupId(2));
        assertThat(max.move(tab(1), groupId(2), 0).maximized()).as("it gains a tab by a move").isEmpty();
        assertThat(max.move(tab(3), groupId(1), 0).maximized()).as("it loses a tab by a move").isEmpty();
        assertThat(max.remove(tab(3)).maximized()).as("one of its tabs closes").contains(groupId(2));
        assertThat(max.remove(tab(1)).maximized()).as("a tab of another group closes").contains(groupId(2));
        assertThat(max.remove(tab(3)).remove(tab(4)).maximized()).as("its last tab closes and it goes").isEmpty();
        assertThat(max.remove(tab(1)).remove(tab(2)).maximized()).as("the last other group goes: one group is not maximized").isEmpty();
        Layout three = new Layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(three.remove(tab(3)).maximized()).as("another group goes and two remain").contains(groupId(2));
        assertThat(three.remove(tab(3)).activeGroup()).isEqualTo(groupId(2));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.LayoutOperationsTest'`
Expected: FAIL to compile: "cannot find symbol … method open(TabId,GroupId,int)", "… select", "… activate", "… move",
"… remove".

- [ ] **Step 3: Add the operations**

In `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java`, replace:

```java
import java.util.Optional;
```

with:

```java
import java.util.Optional;
import java.util.function.Function;
```

Insert, immediately before the line
`    /** Equal when the trees, the active groups and the maximized groups are equal; the order of use is ignored. */`:

```java
    /**
     * Inserts {@code tab} into {@code group} at {@code index}, selects it and activates the group.
     *
     * @param tab a tab not in the layout
     * @param group a group of the layout
     * @param index 0 to the group's tab count
     * @return the new layout
     * @throws IllegalArgumentException when the tab is in the layout, the group is not, or the index is outside the group
     */
    public Layout open(TabId tab, GroupId group, int index) {
        Objects.requireNonNull(tab, "tab");
        if (contains(tab)) throw new IllegalArgumentException("Tab " + tab + " is already in the layout");
        Group target = requireGroup(group);
        checkIndex(index, target.tabs().size());
        var tabs = new ArrayList<>(target.tabs());
        tabs.add(index, tab);
        return activated(replace(root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, false);
    }

    /**
     * Shows {@code tab} in its group and activates that group (workspace plan 2, Rulings 6).
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout select(TabId tab) {
        GroupId group = requireGroupOf(tab);
        return activated(replace(root, group, found -> new Group(found.id(), found.tabs(), Optional.of(tab))), group, false);
    }

    /**
     * Gives {@code group} focus and puts it first in the order of use; a maximize moves to it.
     *
     * @param group a group of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Layout activate(GroupId group) {
        requireGroup(group);
        return activated(root, group, false);
    }

    /**
     * Moves {@code tab} into {@code group} at {@code index}, counted without the tab, selects it and activates the group.
     * A source group left empty is removed as {@link #remove(TabId)} removes it.
     *
     * @param tab a tab of the layout
     * @param group a group of the layout, the tab's own or another
     * @param index 0 to the group's tab count without the tab
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the index is outside the group
     */
    public Layout move(TabId tab, GroupId group, int index) {
        GroupId source = requireGroupOf(tab);
        Group target = requireGroup(group);
        var tabs = new ArrayList<>(target.tabs());
        if (source.equals(group)) {
            tabs.remove(tab);
            checkIndex(index, tabs.size());
            tabs.add(index, tab);
            return activated(replace(root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, false);
        }
        checkIndex(index, tabs.size());
        tabs.add(index, tab);
        Layout rest = remove(tab);
        return rest.activated(replace(rest.root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, true);
    }

    /**
     * Removes {@code tab}, closing it or moving it out. When it was its group's selected tab, the group's most recently
     * shown remaining tab is selected (workspace plan 2, Rulings 7). A group left empty is removed: its weight goes to the
     * sibling before it (after it when it was first), a split left with one child collapses into that child and nesting
     * is flattened again, and the active group becomes the most recently used remaining group. The maximize stays while
     * the maximized group stays and two groups remain (Rulings 5). Removing the last tab leaves the empty workspace,
     * keeping its group.
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout remove(TabId tab) {
        GroupId id = requireGroupOf(tab);
        Group holder = group(id).orElseThrow();
        if (holder.tabs().size() > 1) {
            var tabs = new ArrayList<>(holder.tabs());
            tabs.remove(tab);
            TabId shown = holder.selected().orElseThrow();
            TabId selected = shown.equals(tab) ? tabOrders.get(id).get(1) : shown;
            Node next = replace(root, id, found -> new Group(found.id(), tabs, Optional.of(selected)));
            return new Layout(next, activeGroup, maximized, recent, tabOrders);
        }
        if (root instanceof Group) return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty(), recent, tabOrders);
        GroupId heir = recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow();
        Node next = without(root, id).orElseThrow();
        Optional<GroupId> kept = maximized.filter(max -> !max.equals(id) && next instanceof Split);
        return new Layout(next, heir, kept, recent, tabOrders);
    }

```

Insert, immediately before the line `    /** Adds the groups under {@code node} to {@code into}, in tree order. */`:

```java
    /**
     * A layout over {@code next} with {@code group} active and first in the order of use; a structural change clears the
     * maximize, anything else moves it to {@code group} (workspace plan 2, Rulings 5).
     */
    private Layout activated(Node next, GroupId group, boolean structural) {
        return new Layout(next, group, structural ? Optional.empty() : maximized.map(ignored -> group), recent, tabOrders);
    }

    private Group requireGroup(GroupId id) {
        Objects.requireNonNull(id, "group");
        return group(id).orElseThrow(() -> new IllegalArgumentException("Group " + id + " is not in the layout"));
    }

    private GroupId requireGroupOf(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        return groupOf(tab).orElseThrow(() -> new IllegalArgumentException("Tab " + tab + " is not in the layout"));
    }

    private static void checkIndex(int index, int size) {
        if (index < 0 || index > size) throw new IllegalArgumentException("Index " + index + " is outside 0 to " + size);
    }

    /** {@code node} with the group {@code id} replaced by what {@code change} makes of it; Split flattens the result. */
    private static Node replace(Node node, GroupId id, Function<Group, Node> change) {
        return switch (node) {
            case Group group -> group.id().equals(id) ? change.apply(group) : group;
            case Split split -> {
                var children = new ArrayList<Child>(split.children().size());
                boolean changed = false;
                for (Child child : split.children()) {
                    Node next = replace(child.node(), id, change);
                    changed |= next != child.node();
                    children.add(next == child.node() ? child : new Child(next, child.weight()));
                }
                yield changed ? new Split(split.orientation(), children) : split;
            }
        };
    }

    /**
     * {@code node} without the group {@code id}: empty when the node is that group. Its weight goes to the sibling before
     * it, or after it when it was first (workspace plan 2, Rulings 8), and a split left with one child collapses into that child.
     */
    private static Optional<Node> without(Node node, GroupId id) {
        return switch (node) {
            case Group group -> group.id().equals(id) ? Optional.empty() : Optional.of(group);
            case Split split -> {
                var children = new ArrayList<>(split.children());
                for (int i = 0; i < children.size(); i++) {
                    Child child = children.get(i);
                    if (!holds(child.node(), id)) continue;
                    Optional<Node> rest = without(child.node(), id);
                    if (rest.isPresent()) {
                        children.set(i, new Child(rest.get(), child.weight()));
                    } else {
                        children.remove(i);
                        int heir = i == 0 ? 0 : i - 1;
                        Child taker = children.get(heir);
                        children.set(heir, new Child(taker.node(), taker.weight() + child.weight()));
                    }
                    break;
                }
                yield Optional.of(children.size() == 1 ? children.getFirst().node() : new Split(split.orientation(), children));
            }
        };
    }

    /** Whether the group {@code id} is under {@code node}. */
    private static boolean holds(Node node, GroupId id) {
        return switch (node) {
            case Group group -> group.id().equals(id);
            case Split split -> split.children().stream().anyMatch(child -> holds(child.node(), id));
        };
    }

```

`tabs.remove(tab)` removes the `TabId` object and `children.remove(i)` removes by `int` index, as intended. A group's tab
order always starts with its selected tab and holds every one of its tabs, so `tabOrders.get(id).get(1)` is the most
recently shown other tab. While a group is maximized it is the active group, the head of `recent`, so it is the `heir`
whenever it survives a removal, and `kept` stays valid.

- [ ] **Step 4: Run the layout tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*' :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL; `LayoutOperationsTest` 15 tests pass, Task 1's tests still pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/layout/Layout.java jasper-app/src/test/java/dev/jasper/app/layout/LayoutOperationsTest.java
git commit -F - <<'EOF'
Workspace layout: open, select, activate, move and remove

Spec 7.3's tab operations: a removed group's weight goes to its sibling
before (Cmd+D then close restores the sizes), a lone split child
collapses and re-flattens, focus goes to the most recently used group,
and the maximize follows the active group until the tree or its tabs
change.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Splits, sizes, maximize, regions and neighbours

Spec 7.3's `split`, `moveToNewGroup`, `resize`, `toggleMaximize` and `neighbour`, `unmaximize()` (Rulings 5), and
`bounds()` over the unit square for `neighbour` and tests; `SplitTreeTest`'s cases ported (fact 4).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/layout/LayoutGeometryTest.java`

**Interfaces:**
- Consumes: Task 2's `activated`, `requireGroup`, `requireGroupOf`, `replace`, `remove`.
- Produces: `Layout split(TabId tab, GroupId group, Side side)`, `Layout moveToNewGroup(TabId tab, GroupId group,
  Side side)`, `Layout resize(List<Integer> path, List<Double> weights)`, `Layout toggleMaximize(GroupId group)`,
  `Layout unmaximize()` (plans 4 and 5 call it on a move out to another window), `Optional<GroupId> neighbour(GroupId
  group, Side direction)`, `Map<GroupId, Region> bounds()` (tree order; for `neighbour` and tests, not plan 5's drops).
  Task 5's depth test builds nested layouts with `split`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/layout/LayoutGeometryTest.java`:

```java
package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.layout;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 7.3: splits, new groups, sizes, maximize, regions and neighbours; today's SplitTreeTest cases, ported. */
class LayoutGeometryTest {
    /** The group {@code split} made for tab {@code n}. */
    static Group fresh(Layout split, int n) {
        return new Group(split.groupOf(tab(n)).orElseThrow(), List.of(tab(n)), Optional.of(tab(n)));
    }

    @Test void splitPutsTheTabInANewGroupOnTheSideTakingHalfTheSpace() {
        Layout one = layout(group(1), 1);
        for (Side side : Side.values()) {
            Layout split = one.split(tab(2), groupId(1), side);
            List<Child> children = side.leading()
                ? List.of(child(fresh(split, 2), 0.5), child(group(1), 0.5))
                : List.of(child(group(1), 0.5), child(fresh(split, 2), 0.5));
            assertThat(split.root()).as(side.name()).isEqualTo(new Split(side.orientation(), children));
            assertThat(split.activeGroup()).isEqualTo(split.groupOf(tab(2)).orElseThrow()).isNotEqualTo(groupId(1));
            assertThat(split.activeTab()).contains(tab(2));
        }
    }

    @Test void aParentOfTheSameOrientationGainsASibling() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout right = pair.split(tab(3), groupId(1), Side.RIGHT);
        assertThat(right.root()).isEqualTo(h(child(group(1), 0.25), child(fresh(right, 3), 0.25), child(group(2), 0.5)));
        Layout left = pair.split(tab(3), groupId(2), Side.LEFT);
        assertThat(left.root()).isEqualTo(h(child(group(1), 0.5), child(fresh(left, 3), 0.25), child(group(2), 0.25)));
    }

    @Test void aParentOfTheOtherOrientationNestsANewSplit() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout down = pair.split(tab(3), groupId(2), Side.DOWN);
        assertThat(down.root()).isEqualTo(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(fresh(down, 3), 0.5)), 0.5)));
    }

    @Test void splittingRightOrDownThenClosingRestoresTheSizes() {
        Layout pair = layout(h(child(group(1), 0.25), child(group(2), 0.75)), 2);
        for (Side side : List.of(Side.RIGHT, Side.DOWN)) {
            Layout split = pair.split(tab(9), groupId(2), side);
            assertThat(split.remove(tab(9))).as(side.name()).isEqualTo(pair);
        }
    }

    @Test void aSplitKeepsTheOtherWeightsAndClearsTheMaximize() {
        // SplitTreeTest.splitRebuildsOnlyTheFocusedLeafAndRetainsBranchRatiosThroughZoom
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1)
            .resize(List.of(), List.of(0.73, 0.27)).toggleMaximize(groupId(2));
        Layout split = pair.split(tab(3), groupId(2), Side.DOWN);
        assertThat(split.root()).isEqualTo(h(child(group(1), 0.73), child(v(child(group(2), 0.5), child(fresh(split, 3), 0.5)), 0.27)));
        assertThat(split.maximized()).isEmpty();
        assertThat(split.activeTab()).contains(tab(3));
    }

    @Test void splitRefusesAKnownTabAnUnknownGroupAndTheEmptyWorkspace() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        assertThatIllegalArgumentException().isThrownBy(() -> pair.split(tab(2), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.split(tab(9), groupId(9), Side.RIGHT));
        Layout empty = Layout.empty();
        assertThatIllegalArgumentException().as("an empty group beside a new one")
            .isThrownBy(() -> empty.split(tab(1), empty.activeGroup(), Side.RIGHT));
        assertThatNullPointerException().isThrownBy(() -> pair.split(tab(9), groupId(1), null));
    }

    @Test void moveToNewGroupMovesAnExistingTabBesideAGroup() {
        Layout pair = layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 1);
        Layout below = pair.moveToNewGroup(tab(2), groupId(2), Side.DOWN);
        assertThat(below.root()).isEqualTo(h(child(group(1, 1, 1), 0.5), child(v(child(group(2, 3, 3), 0.5), child(fresh(below, 2), 0.5)), 0.5)));
        assertThat(below.activeGroup()).isEqualTo(below.groupOf(tab(2)).orElseThrow());
        Layout own = pair.moveToNewGroup(tab(1), groupId(1), Side.RIGHT);
        assertThat(own.root()).as("out of its own group")
            .isEqualTo(h(child(group(1, 2, 2), 0.25), child(fresh(own, 1), 0.25), child(group(2, 3, 3), 0.5)));
        Layout emptied = pair.moveToNewGroup(tab(3), groupId(1), Side.LEFT);
        assertThat(emptied.root()).as("its emptied group goes first").isEqualTo(h(child(fresh(emptied, 3), 0.5), child(group(1, 1, 1, 2), 0.5)));
        assertThatIllegalArgumentException().as("a group's only tab").isThrownBy(() -> pair.moveToNewGroup(tab(3), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(9), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(1), groupId(9), Side.RIGHT));
    }

    @Test void resizeAddressesASplitByItsPathAndNormalizes() {
        // SplitTreeTest.ratiosAreClampedAndNonFiniteRatiosAreRejected: no clamp here (plan Rulings 4), the rest rejected
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.5)), 2);
        assertThat(nested.resize(List.of(), List.of(1.0, 3.0)).root())
            .isEqualTo(h(child(group(1), 0.25), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.75)));
        assertThat(nested.resize(List.of(1), List.of(0.75, 0.25)).root())
            .isEqualTo(h(child(group(1), 0.5), child(v(child(group(2), 0.75), child(group(3), 0.25)), 0.5)));
        assertThat(nested.resize(List.of(1), List.of(3.0, 1.0)).activeGroup()).isEqualTo(groupId(2));
        for (List<Integer> path : List.of(List.of(0), List.of(2), List.of(-1), List.of(1, 0)))
            assertThatIllegalArgumentException().as("path %s", path).isThrownBy(() -> nested.resize(path, List.of(0.5, 0.5)));
        assertThatIllegalArgumentException().as("one weight per child").isThrownBy(() -> nested.resize(List.of(), List.of(1.0)));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThatIllegalArgumentException().as("weight %s", bad)
                .isThrownBy(() -> nested.resize(List.of(), List.of(bad, 1.0))).withMessageContaining("weight");
        assertThatIllegalArgumentException().as("one group has no split")
            .isThrownBy(() -> layout(group(1), 1).resize(List.of(), List.of(1.0, 1.0)));
    }

    @Test void toggleMaximizeMaximizesAndActivatesThenRestoresAndUnmaximizeClears() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout max = pair.toggleMaximize(groupId(2));
        assertThat(max.maximized()).contains(groupId(2));
        assertThat(max.activeGroup()).isEqualTo(groupId(2));
        assertThat(max.toggleMaximize(groupId(2)).maximized()).isEmpty();
        assertThat(max.toggleMaximize(groupId(2)).activeGroup()).isEqualTo(groupId(2));
        assertThat(max.toggleMaximize(groupId(1)).maximized()).as("another group takes it").contains(groupId(1));
        Layout one = layout(group(1), 1);
        assertThat(one.toggleMaximize(groupId(1))).as("one group: unchanged").isEqualTo(one);
        assertThat(one.toggleMaximize(groupId(1)).maximized()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> pair.toggleMaximize(groupId(9)));
        assertThat(max.unmaximize().maximized()).isEmpty();
        assertThat(max.unmaximize().activeGroup()).isEqualTo(groupId(2));
        assertThat(max.unmaximize().root()).isEqualTo(max.root());
        assertThat(pair.unmaximize()).as("nothing maximized: this layout").isSameAs(pair);
    }

    @Test void newGroupsClearTheMaximizeAndResizingKeepsIt() {
        Layout max = layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 1).toggleMaximize(groupId(2));
        assertThat(max.split(tab(9), groupId(2), Side.RIGHT).maximized()).isEmpty();
        assertThat(max.moveToNewGroup(tab(2), groupId(1), Side.DOWN).maximized()).isEmpty();
        assertThat(max.resize(List.of(), List.of(1.0, 3.0)).maximized()).contains(groupId(2));
    }

    @Test void boundsTileTheUnitSquareInTreeOrder() {
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.75), child(group(3), 0.25)), 0.5)), 1);
        assertThat(nested.bounds()).containsExactly(
            entry(groupId(1), new Region(0, 0, 0.5, 1)),
            entry(groupId(2), new Region(0.5, 0, 0.5, 0.75)),
            entry(groupId(3), new Region(0.5, 0.75, 0.5, 0.25)));
        assertThat(layout(group(1), 1).bounds()).containsExactly(entry(groupId(1), new Region(0, 0, 1, 1)));
        assertThat(nested.toggleMaximize(groupId(2)).bounds()).as("a maximized group keeps its place").isEqualTo(nested.bounds());
    }

    @Test void directionalNavigationRequiresOverlapAndUsesGeometryInsteadOfTreeOrder() {
        // SplitTreeTest.directionalNavigationRequiresOverlapAndUsesGeometryInsteadOfTraversalOrder
        Layout grid = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.5)), 1);
        assertThat(grid.neighbour(groupId(1), Side.RIGHT)).as("a tie across goes to the first in tree order").contains(groupId(2));
        assertThat(grid.neighbour(groupId(2), Side.DOWN)).contains(groupId(3));
        assertThat(grid.neighbour(groupId(3), Side.LEFT)).contains(groupId(1));
        assertThat(grid.neighbour(groupId(3), Side.UP)).contains(groupId(2));
        assertThat(grid.neighbour(groupId(1), Side.DOWN)).isEmpty();
    }

    @Test void asymmetricGeometryChoosesTheGroupThatOverlaps() {
        // SplitTreeTest.asymmetricGeometryChoosesTheNearestPerpendicularCenter
        Layout grid = layout(h(child(v(child(group(1), 0.8), child(group(2), 0.2)), 0.65), child(v(child(group(3), 0.3), child(group(4), 0.7)), 0.35)), 2);
        assertThat(grid.neighbour(groupId(2), Side.RIGHT)).contains(groupId(4));
    }

    @Test void theNearestGroupAheadWinsThenTheNearestCentreAcross() {
        Layout row = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        assertThat(row.neighbour(groupId(1), Side.RIGHT)).contains(groupId(2));
        Layout grid = layout(v(child(h(child(group(1), 0.5), child(group(2), 0.5)), 0.5), child(h(child(group(3), 0.625), child(group(4), 0.375)), 0.5)), 2);
        assertThat(grid.neighbour(groupId(2), Side.DOWN)).as("both overlap; group 4's centre is nearer").contains(groupId(4));
    }

    @Test void navigationDoesNotWrapAndMovesTheMaximize() {
        // SplitTreeTest.navigationDoesNotWrapAndStillChangesTheVisiblePaneWhileZoomed
        Layout max = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1).toggleMaximize(groupId(1));
        Layout moved = max.activate(max.neighbour(groupId(1), Side.RIGHT).orElseThrow());
        assertThat(moved.maximized()).contains(groupId(2));
        assertThat(moved.neighbour(groupId(2), Side.RIGHT)).isEmpty();
        assertThat(moved.neighbour(groupId(2), Side.LEFT)).contains(groupId(1));
        assertThatIllegalArgumentException().isThrownBy(() -> max.neighbour(groupId(9), Side.LEFT));
        assertThatNullPointerException().isThrownBy(() -> max.neighbour(groupId(1), null));
    }

    @Test void theSplitTreeScenarioKeepsAValidActiveGroup() {
        // SplitTreeTest.splitNavigateCloseAndZoomPreserveAValidFocus and closingTheLastPaneLeavesAnEmptyUnzoomedTree
        Layout tree = Layout.of(tab(1));
        tree = tree.split(tab(2), tree.activeGroup(), Side.RIGHT);
        tree = tree.split(tab(3), tree.activeGroup(), Side.DOWN);
        tree = tree.activate(tree.neighbour(tree.activeGroup(), Side.UP).orElseThrow());
        assertThat(tree.activeTab()).contains(tab(2));
        tree = tree.remove(tab(2));
        assertThat(tree.tabs()).containsExactly(tab(1), tab(3));
        assertThat(tree.activeTab()).contains(tab(3));
        tree = tree.toggleMaximize(tree.activeGroup());
        assertThat(tree.maximized()).contains(tree.activeGroup());
        tree = tree.remove(tab(3));
        assertThat(tree.tabs()).containsExactly(tab(1));
        assertThat(tree.activeTab()).contains(tab(1));
        assertThat(tree.maximized()).isEmpty();
        tree = tree.remove(tab(1));
        assertThat(tree.isEmpty()).isTrue();
        assertThat(tree.maximized()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.LayoutGeometryTest'`
Expected: FAIL to compile: "cannot find symbol … method split(TabId,GroupId,Side)", "… moveToNewGroup", "… resize",
"… toggleMaximize", "… neighbour", "… bounds".

- [ ] **Step 3: Add the operations and the geometry**

In `jasper-app/src/main/java/dev/jasper/app/layout/Layout.java`, replace:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
```

with:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
```

Insert, immediately before the line
`    /** Equal when the trees, the active groups and the maximized groups are equal; the order of use is ignored. */`:

```java
    /**
     * Opens {@code tab} in a new group on {@code side} of {@code group}, selected and active. The new group takes half of
     * {@code group}'s space: a parent split of the side's orientation gains it as a sibling, otherwise the two share a new
     * split in {@code group}'s place. Clears the maximize.
     *
     * @param tab a tab not in the layout
     * @param group a group of the layout holding tabs
     * @param side where the new group goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab is in the layout, or the group is not or holds no tabs
     */
    public Layout split(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(side, "side");
        if (contains(tab)) throw new IllegalArgumentException("Tab " + tab + " is already in the layout");
        if (requireGroup(group).tabs().isEmpty()) throw new IllegalArgumentException("The empty workspace's group cannot be split");
        return beside(tab, group, side);
    }

    /**
     * Moves {@code tab} into a new group on {@code side} of {@code group}, as {@link #split(TabId, GroupId, Side)} opens
     * one; a source group left empty is removed first, as {@link #remove(TabId)} removes it.
     *
     * @param tab a tab of the layout
     * @param group a group of the layout; the tab's own only when it holds other tabs
     * @param side where the new group goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the tab is the group's only tab
     */
    public Layout moveToNewGroup(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(side, "side");
        GroupId source = requireGroupOf(tab);
        Group target = requireGroup(group);
        if (source.equals(group) && target.tabs().size() == 1)
            throw new IllegalArgumentException("Tab " + tab + " is the only tab of group " + group + " and cannot split it");
        return remove(tab).beside(tab, group, side);
    }

    /**
     * Sets the weights of the split at {@code path} (workspace plan 2, Rulings 3), from a divider drag. Keeps the maximize.
     *
     * @param path the child indexes from the root to the split; empty for the root
     * @param weights one per child, positive and finite; normalized to sum to 1
     * @return the new layout
     * @throws IllegalArgumentException when no split is at the path or the weights do not fit it
     */
    public Layout resize(List<Integer> path, List<Double> weights) {
        return new Layout(resized(root, List.copyOf(path), 0, List.copyOf(weights)), activeGroup, maximized, recent, tabOrders);
    }

    /**
     * Maximizes {@code group} and activates it, or restores it when it is the maximized group. A layout with one group is
     * returned unchanged.
     *
     * @param group a group of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Layout toggleMaximize(GroupId group) {
        requireGroup(group);
        if (root instanceof Group) return this;
        if (maximized.filter(group::equals).isPresent()) return unmaximize();
        return new Layout(root, group, Optional.of(group), recent, tabOrders);
    }

    /**
     * The layout with no group maximized; plans 4 and 5 call it when a tab moves out to another window (workspace plan 2,
     * Rulings 5).
     *
     * @return the new layout, or this one when no group is maximized
     */
    public Layout unmaximize() {
        return maximized.isEmpty() ? this : new Layout(root, activeGroup, Optional.empty(), recent, tabOrders);
    }

    /**
     * The group in {@code direction} from {@code group} over the unit square, by today's {@code SplitTree.navigate} rule:
     * of the groups wholly on that side that overlap it across the direction, the nearest, then the one whose centre is
     * nearest across; a tie goes to the first in tree order. The maximize is ignored.
     *
     * @param group a group of the layout
     * @param direction the direction
     * @return the neighbour, or empty at the workspace's edge (navigation does not wrap)
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Optional<GroupId> neighbour(GroupId group, Side direction) {
        Objects.requireNonNull(direction, "direction");
        requireGroup(group);
        Map<GroupId, Region> regions = bounds();
        Region current = regions.get(group);
        GroupId best = null;
        double bestForward = Double.POSITIVE_INFINITY;
        double bestAcross = Double.POSITIVE_INFINITY;
        for (var entry : regions.entrySet()) {
            Region candidate = entry.getValue();
            if (entry.getKey().equals(group) || !ahead(current, candidate, direction) || !overlaps(current, candidate, direction)) continue;
            double forward = forward(current, candidate, direction);
            double across = across(current, candidate, direction);
            if (forward < bestForward - EPSILON || (Math.abs(forward - bestForward) <= EPSILON && across < bestAcross - EPSILON)) {
                best = entry.getKey();
                bestForward = forward;
                bestAcross = across;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Each group's region of the unit square; a maximized group is placed where it sits in the tree.
     *
     * @return the regions by group, in tree order
     */
    public Map<GroupId, Region> bounds() {
        var regions = new LinkedHashMap<GroupId, Region>();
        place(root, new Region(0, 0, 1, 1), regions);
        return Collections.unmodifiableMap(regions);
    }

```

Insert, immediately before the line `    /** Adds the groups under {@code node} to {@code into}, in tree order. */`:

```java
    /** Distances this close are equal: today's {@code SplitTree} tolerance. */
    private static final double EPSILON = 1.0e-12;

    /** {@code tab} in a new group on {@code side} of {@code group}, selected and active; clears the maximize. */
    private Layout beside(TabId tab, GroupId group, Side side) {
        Group created = new Group(GroupId.random(), List.of(tab), Optional.of(tab));
        Node next = replace(root, group, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(created, 0.5), new Child(found, 0.5))
            : List.of(new Child(found, 0.5), new Child(created, 0.5))));
        return activated(next, created.id(), true);
    }

    /** {@code node} with the split at {@code path} (from {@code depth} on) given {@code weights}. */
    private static Node resized(Node node, List<Integer> path, int depth, List<Double> weights) {
        if (!(node instanceof Split split)) throw new IllegalArgumentException("No split at " + path);
        List<Child> children = split.children();
        if (depth == path.size()) {
            if (weights.size() != children.size())
                throw new IllegalArgumentException("The split at " + path + " has " + children.size() + " children, not " + weights.size() + " weights");
            var sized = new ArrayList<Child>();
            for (int i = 0; i < children.size(); i++) sized.add(new Child(children.get(i).node(), weights.get(i)));
            return new Split(split.orientation(), sized);
        }
        int index = path.get(depth);
        if (index < 0 || index >= children.size()) throw new IllegalArgumentException("No split at " + path);
        var next = new ArrayList<>(children);
        next.set(index, new Child(resized(children.get(index).node(), path, depth + 1, weights), children.get(index).weight()));
        return new Split(split.orientation(), next);
    }

    /** Puts each group under {@code node} into {@code into} with its share of {@code region}; the last child takes the rest. */
    private static void place(Node node, Region region, Map<GroupId, Region> into) {
        switch (node) {
            case Group group -> into.put(group.id(), region);
            case Split split -> {
                boolean horizontal = split.orientation() == Orientation.HORIZONTAL;
                double start = horizontal ? region.x() : region.y();
                double length = horizontal ? region.width() : region.height();
                double offset = 0;
                List<Child> children = split.children();
                for (int i = 0; i < children.size(); i++) {
                    double size = i == children.size() - 1 ? length - offset : length * children.get(i).weight();
                    place(children.get(i).node(), horizontal
                        ? new Region(start + offset, region.y(), size, region.height())
                        : new Region(region.x(), start + offset, region.width(), size), into);
                    offset += size;
                }
            }
        }
    }

    private static boolean ahead(Region current, Region candidate, Side direction) {
        return switch (direction) {
            case LEFT -> candidate.right() <= current.x() + EPSILON;
            case RIGHT -> candidate.x() >= current.right() - EPSILON;
            case UP -> candidate.bottom() <= current.y() + EPSILON;
            case DOWN -> candidate.y() >= current.bottom() - EPSILON;
        };
    }

    private static boolean overlaps(Region current, Region candidate, Side direction) {
        return direction.orientation() == Orientation.HORIZONTAL
            ? Math.min(current.bottom(), candidate.bottom()) - Math.max(current.y(), candidate.y()) > EPSILON
            : Math.min(current.right(), candidate.right()) - Math.max(current.x(), candidate.x()) > EPSILON;
    }

    private static double forward(Region current, Region candidate, Side direction) {
        return switch (direction) {
            case LEFT -> Math.max(0.0, current.x() - candidate.right());
            case RIGHT -> Math.max(0.0, candidate.x() - current.right());
            case UP -> Math.max(0.0, current.y() - candidate.bottom());
            case DOWN -> Math.max(0.0, candidate.y() - current.bottom());
        };
    }

    private static double across(Region current, Region candidate, Side direction) {
        return direction.orientation() == Orientation.HORIZONTAL
            ? Math.abs(current.centerY() - candidate.centerY())
            : Math.abs(current.centerX() - candidate.centerX());
    }

```

- [ ] **Step 4: Run the layout tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.layout.*' :jasper-app:javadoc verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL; `LayoutGeometryTest` 16 tests pass, Tasks 1 and 2's tests still pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/layout/Layout.java jasper-app/src/test/java/dev/jasper/app/layout/LayoutGeometryTest.java
git commit -F - <<'EOF'
Workspace layout: splits, sizes, maximize, regions and neighbours

split and moveToNewGroup give a new group half of its neighbour's space
(a same-orientation parent gains a sibling), resize addresses a split by
its path, toggleMaximize keeps the maximize on the active group, bounds
tiles the unit square and neighbour ports SplitTree.navigate's rule.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The workspace file's values and writing it

Spec 8's records and exact text, Rulings 3, 12, 16 to 18; `AppDirs` gains the path.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/package-info.java:3`,
  `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java:54-57`, `docs/design/theme-engine/migration.csv` (one
  row appended)
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java:57-61`

**Interfaces:**
- Consumes: Tasks 1 to 3's `Layout` (`root`, `activeGroup`, `maximized`, `tabs`, `groups`, `isEmpty`, `of`, `empty`),
  `Split`, `Child`, `Group`, `Orientation`, `TabId`, `GroupId`; `UiState.Bounds`, `UiState.quote`,
  `TomlStateFile.writeAtomically`.
- Produces: `WorkspaceFile.Role { MAIN, CHILD }`; `sealed interface WorkspaceFile.TabRecord` with `String title()`,
  `record TabRecord.Terminal(String title, String directory, String profile)` and
  `record TabRecord.Content(String title, String pluginId, String restorer, Map<String, String> state)`;
  `record WorkspaceFile.WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, Layout layout,
  Map<TabId, TabRecord> tabs)`; `record WorkspaceFile.Snapshot(List<WindowRecord> windows)` with `WindowRecord main()`
  and `List<WindowRecord> children()`; `static void write(Path file, Snapshot snapshot) throws IOException`;
  package-private `static String encode(Snapshot)` and `static String splitId(String path)`; `AppDirs.workspaceState()`.
  Test helpers `WorkspaceFileTest.tab(int)`, `groupId(int)`, `group(int id, int selected, int... tabs)`, `BOUNDS`,
  `snapshot()` (Task 5 uses them). For plan 6 (Rulings 20): `WindowRecord` needs a record for every tab, so plan 6
  `Layout.remove`s the tabs it cannot restore (the app-owned Errors tab of plan 4, hosted tabs without a restorer)
  before saving, and leaves out a child window that holds only such tabs.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java`:

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
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Role;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8: the workspace file's values and its exact text. */
class WorkspaceFileTest {
    @TempDir Path dir;

    static final UiState.Bounds BOUNDS = new UiState.Bounds(40, 60, 1400, 900);

    static TabId tab(int n) {
        return TabId.parse("00000000-0000-4000-8000-%012d".formatted(n));
    }

    static GroupId groupId(int n) {
        return GroupId.parse("00000000-0000-4000-9000-%012d".formatted(n));
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(WorkspaceFileTest::tab).toList(), Optional.of(tab(selected)));
    }

    /**
     * Spec 8's example: a main window of two columns, the right one in two rows, its lower right group maximized, and a
     * child window without bounds. Listed child first: the snapshot puts the main window first.
     */
    static Snapshot snapshot() {
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3, 3), 0.75), new Child(group(3, 4, 4), 0.25))), 0.5)));
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 6, new Layout(root, groupId(2), Optional.of(groupId(2))), Map.of(
            tab(1), new TabRecord.Terminal("build", "/Users/me/src/jasper", "zsh"),
            tab(2), new TabRecord.Content("README.md", "dev.jasper.editor", "document", Map.of("path", "/Users/me/src/jasper/README.md")),
            tab(3), new TabRecord.Terminal("", "C:\\work", ""),
            tab(4), new TabRecord.Content("Files \"prod\"", "dev.jasper.remote", "sftp", Map.of("remote path", "/srv/a", "host", "example"))));
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, new Layout(group(4, 5, 5), groupId(4), Optional.empty()),
            Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(child, main));
    }

    @Test void writesSpecEightsWorkspaceExactly() throws Exception {
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
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = "00000000-0000-4000-9000-000000000002"

            [[windows.nodes]]
            id = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            kind = "split"
            orientation = "horizontal"
            parent = ""
            weight = 1.0

            [[windows.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.nodes]]
            id = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.75
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.nodes]]
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
            role = "child"
            extended_state = 0
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

    @Test void theEmptyMainWorkspaceWritesAGroupWithNoTabs() {
        Layout empty = new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty());
        assertThat(WorkspaceFile.encode(new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(new UiState.Bounds(0, 0, 800, 600)), 0, empty, Map.of())))))
            .isEqualTo("""
                version = 1

                [[windows]]
                role = "main"
                x = 0
                y = 0
                width = 800
                height = 600
                extended_state = 0
                active_group = "00000000-0000-4000-9000-000000000001"
                maximized = ""

                [[windows.nodes]]
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
        Layout one = Layout.of(tab(1));
        Map<TabId, TabRecord> record = Map.of(tab(1), new TabRecord.Terminal("", "/tmp", ""));
        assertThatIllegalArgumentException().as("a tab without a record")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, one, Map.of()));
        assertThatIllegalArgumentException().as("a record without a tab")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, Layout.empty(), record));
        assertThatIllegalArgumentException().as("an empty child window")
            .isThrownBy(() -> new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.empty(), Map.of()));
        assertThatIllegalArgumentException().as("minimized is not kept")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 1, one, record));
        var main = new WindowRecord(Role.MAIN, Optional.empty(), 6, one, record);
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.of(tab(2)), Map.of(tab(2), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(child, main)).windows()).as("the main window first").containsExactly(main, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main)).children()).containsExactly(child);
        assertThatIllegalArgumentException().as("no main window").isThrownBy(() -> new Snapshot(List.of(child)));
        assertThatIllegalArgumentException().as("two main windows").isThrownBy(() -> new Snapshot(List.of(main, main)));
        assertThatIllegalArgumentException().as("a tab in two windows")
            .isThrownBy(() -> new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.of(tab(1)), record))));
        Layout sameGroup = new Layout(new Group(one.activeGroup(), List.of(tab(2)), Optional.of(tab(2))), one.activeGroup(), Optional.empty());
        assertThatIllegalArgumentException().as("a group in two windows").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, sameGroup, Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x:y", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x", "", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new TabRecord.Terminal(null, "", ""));
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java`, replace:

```java
    @Test void buddyStateLivesBesideCommandHistory() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        assertThat(dirs.buddyState())
            .isEqualTo(home.resolve(".config/jasper/buddy.toml"));
    }
```

with:

```java
    @Test void buddyStateLivesBesideCommandHistory() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        assertThat(dirs.buddyState())
            .isEqualTo(home.resolve(".config/jasper/buddy.toml"));
    }

    @Test void theWorkspaceStateLivesInTheRootBesideTheUiState() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), home);
        assertThat(dirs.workspaceState()).isEqualTo(home.resolve(".config/jasper/workspace.toml"));
        assertThat(dirs.workspaceState().getParent()).isEqualTo(dirs.uiState().getParent());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.WorkspaceFileTest' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: FAIL to compile: "cannot find symbol … WorkspaceFile", "… method workspaceState()".

- [ ] **Step 3: The codec's values and writer, the path and the package edge**

Create `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java`:

```java
package dev.jasper.app.persistence;

import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8): each Jasper window's
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. Writes are atomic.
 * Stateless: the caller chooses the thread; the values are immutable.
 */
public final class WorkspaceFile {
    /** Whether a window is the main window or a child window. */
    public enum Role { MAIN, CHILD }

    /** What a tab needs to come back (spec 13): a terminal's title, directory and profile, or a plugin's restore state. */
    public sealed interface TabRecord permits TabRecord.Terminal, TabRecord.Content {
        /**
         * The tab's saved title.
         *
         * @return the title; empty for a terminal tab that was not renamed
         */
        String title();

        /**
         * A terminal tab ({@code kind = "terminal"}).
         *
         * @param title its renamed title, or empty
         * @param directory its last local directory, or empty
         * @param profile its shell profile's id, or empty
         */
        record Terminal(String title, String directory, String profile) implements TabRecord {
            /** Checks every value is present. */
            public Terminal {
                Objects.requireNonNull(title, "title");
                Objects.requireNonNull(directory, "directory");
                Objects.requireNonNull(profile, "profile");
            }
        }

        /**
         * A plugin's content tab ({@code kind = "content:<plugin id>:<restorer kind>"}).
         *
         * @param title its title when saved
         * @param pluginId the plugin that restores it: not empty, no colon
         * @param restorer the plugin's restorer kind: not empty
         * @param state the restore state the plugin gave when it opened the tab
         */
        record Content(String title, String pluginId, String restorer, Map<String, String> state) implements TabRecord {
            /** Checks the ids and copies the state. */
            public Content {
                Objects.requireNonNull(title, "title");
                if (pluginId.isEmpty() || pluginId.indexOf(':') >= 0) throw new IllegalArgumentException("Not a plugin id: " + pluginId);
                if (restorer.isEmpty()) throw new IllegalArgumentException("A content tab names its restorer kind");
                state = Map.copyOf(state);
            }
        }
    }

    /**
     * One window as saved.
     *
     * @param role main or child
     * @param bounds its normal bounds; empty places it as a new window
     * @param extendedState its maximized bits only (2 and 4), as {@code UiState.MainWindow} keeps them
     * @param layout its workspace; only the main window's may be empty
     * @param tabs a record for exactly each tab of the layout
     */
    public record WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        /** Copies the records and checks them against the layout. */
        public WindowRecord {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(layout, "layout");
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Only the maximized bits are kept: " + extendedState);
            tabs = Map.copyOf(tabs);
            if (!tabs.keySet().equals(Set.copyOf(layout.tabs())))
                throw new IllegalArgumentException("A window's tab records name exactly its layout's tabs");
            if (role == Role.CHILD && layout.isEmpty()) throw new IllegalArgumentException("Only the main window's workspace may be empty");
        }
    }

    /**
     * Every Jasper window to restore, the main window first.
     *
     * @param windows exactly one main window and any child windows; no group or tab in two windows
     */
    public record Snapshot(List<WindowRecord> windows) {
        /** Puts the main window first and checks the ids. */
        public Snapshot {
            var ordered = new ArrayList<WindowRecord>();
            for (WindowRecord window : windows) if (window.role() == Role.MAIN) ordered.add(window);
            if (ordered.size() != 1) throw new IllegalArgumentException("A workspace has exactly one main window, not " + ordered.size());
            for (WindowRecord window : windows) if (window.role() == Role.CHILD) ordered.add(window);
            var groups = new HashSet<GroupId>();
            var tabs = new HashSet<TabId>();
            for (WindowRecord window : ordered) {
                for (Group group : window.layout().groups())
                    if (!groups.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in two windows");
                for (TabId tab : window.layout().tabs())
                    if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two windows");
            }
            windows = List.copyOf(ordered);
        }

        /**
         * The main window.
         *
         * @return the first window
         */
        public WindowRecord main() {
            return windows.getFirst();
        }

        /**
         * The child windows.
         *
         * @return every window after the main window, in order
         */
        public List<WindowRecord> children() {
            return windows.subList(1, windows.size());
        }
    }

    private static final String CONTENT = "content:";
    /** Restore-state keys written bare; any other key is quoted (workspace plan 2, Rulings 16). */
    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_-]+");

    private WorkspaceFile() { }

    /**
     * Writes {@code snapshot} to {@code file} atomically, through a temporary file beside it.
     *
     * @param file the workspace file
     * @param snapshot what to save
     * @throws IOException when the file cannot be written
     */
    public static void write(Path file, Snapshot snapshot) throws IOException {
        TomlStateFile.writeAtomically(file, ".workspace-", encode(snapshot));
    }

    /** The file's text: the windows in order, each window's nodes parents first, then its tabs in workspace order. */
    static String encode(Snapshot snapshot) {
        var text = new StringBuilder("version = 1\n");
        for (WindowRecord window : snapshot.windows()) {
            Layout layout = window.layout();
            text.append("\n[[windows]]\nrole = \"").append(window.role() == Role.MAIN ? "main" : "child").append("\"\n");
            window.bounds().ifPresent(bounds -> text.append("x = ").append(bounds.x()).append("\ny = ").append(bounds.y())
                .append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
            text.append("extended_state = ").append(window.extendedState())
                .append("\nactive_group = \"").append(layout.activeGroup().value())
                .append("\"\nmaximized = \"").append(layout.maximized().map(id -> id.value().toString()).orElse("")).append("\"\n");
            appendNode(text, layout.root(), "", 1.0, "");
            for (TabId tab : layout.tabs()) appendTab(text, tab, window.tabs().get(tab));
        }
        return text.toString();
    }

    /**
     * A split's id in the file (workspace plan 2, Rulings 3): the name-based UUID of {@code "jasper.split:"} and its path, the child indexes
     * from the root joined with dots ({@code ""} for the root, {@code "1.0"} for the first child of the root's second).
     */
    static String splitId(String path) {
        return UUID.nameUUIDFromBytes(("jasper.split:" + path).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void appendNode(StringBuilder text, Node node, String parent, double weight, String path) {
        switch (node) {
            case Split split -> {
                String id = splitId(path);
                text.append("\n[[windows.nodes]]\nid = \"").append(id).append("\"\nkind = \"split\"\norientation = \"")
                    .append(split.orientation() == Orientation.HORIZONTAL ? "horizontal" : "vertical")
                    .append("\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight).append('\n');
                for (int i = 0; i < split.children().size(); i++) {
                    Child child = split.children().get(i);
                    appendNode(text, child.node(), id, child.weight(), path.isEmpty() ? Integer.toString(i) : path + "." + i);
                }
            }
            case Group group -> text.append("\n[[windows.nodes]]\nid = \"").append(group.id().value())
                .append("\"\nkind = \"group\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight)
                .append("\ntabs = ").append(group.tabs().stream().map(tab -> "\"" + tab.value() + "\"").collect(Collectors.joining(", ", "[", "]")))
                .append("\nselected = \"").append(group.selected().map(tab -> tab.value().toString()).orElse("")).append("\"\n");
        }
    }

    private static void appendTab(StringBuilder text, TabId id, TabRecord saved) {
        text.append("\n[[windows.tabs]]\nid = \"").append(id.value()).append("\"\n");
        switch (saved) {
            case TabRecord.Terminal terminal -> text.append("kind = \"terminal\"\ntitle = ").append(UiState.quote(terminal.title()))
                .append("\ndirectory = ").append(UiState.quote(terminal.directory()))
                .append("\nprofile = ").append(UiState.quote(terminal.profile())).append('\n');
            case TabRecord.Content content -> text.append("kind = ").append(UiState.quote(CONTENT + content.pluginId() + ":" + content.restorer()))
                .append("\ntitle = ").append(UiState.quote(content.title()))
                .append("\nstate = ").append(inline(content.state())).append('\n');
        }
    }

    /** A restore state as one inline table, in key order. */
    private static String inline(Map<String, String> state) {
        if (state.isEmpty()) return "{}";
        return new TreeMap<>(state).entrySet().stream()
            .map(entry -> (BARE.matcher(entry.getKey()).matches() ? entry.getKey() : UiState.quote(entry.getKey())) + " = " + UiState.quote(entry.getValue()))
            .collect(Collectors.joining(", ", "{ ", " }"));
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/persistence/package-info.java`, replace:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.dock (slots and the dock's size limits).
```

with:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.dock (slots and the dock's size limits), dev.jasper.app.layout
 * (the workspace layouts workspace.toml saves).
```

In `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`, replace:

```java
    /** Application-owned layout state: panel placement, rail visibility and auxiliary window bounds. */
    public Path uiState() {
        return root.resolve("ui-state.toml");
    }
```

with:

```java
    /** Application-owned layout state: panel placement, rail visibility and auxiliary window bounds. */
    public Path uiState() {
        return root.resolve("ui-state.toml");
    }

    /** The saved windows, splits, groups and tabs ({@code workspace.toml}). */
    public Path workspaceState() {
        return root.resolve("workspace.toml");
    }
```

Inventory `WorkspaceFile`, which `ThemeMigrationCoverageTest`'s pattern matches (it holds `new ArrayList<`; fact 14),
appending to the end of `docs/design/theme-engine/migration.csv`:

```bash
python3 -I - <<'PY2'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java,app.persistence,WorkspaceFile,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java,New in workspace tabs plan 2 Task 4; the workspace.toml codec; no Swing or AWT",
]
listed = {line.split(",", 1)[0] for line in text.splitlines()}
for row in rows:
    if row.split(",", 1)[0] in listed: raise SystemExit("already listed: " + row.split(",", 1)[0])
    if len(row.split(",")) != 8: raise SystemExit("not 8 columns: " + row)
if not text.endswith("\n"): raise SystemExit("migration.csv does not end with a newline")
path.write_text(text + "".join(row + "\n" for row in rows), encoding="utf-8")
print(len(rows), "rows added")
PY2
```

Expected: `1 rows added`.

`WorkspaceFileTest` and `AppDirsTest` do not match the pattern.

- [ ] **Step 4: Run the tests, the architecture check and the Javadoc**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.platform.AppDirsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL; `WorkspaceFileTest` 4 tests pass; `AppDirsTest` gains one passing test;
`ThemeMigrationCoverageTest` passes with the new row.

If `splitIdsAreNameBasedUuidsOfTheirPath` fails on the two literal ids, the computation in fact 13 is wrong: put the
actual values into that test and the exact text, and record it in the banner.

- [ ] **Step 5: Commit**

```bash
git add docs/design/theme-engine/migration.csv jasper-app/src/main/java/dev/jasper/app/persistence jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java \
  jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileTest.java jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
git commit -F - <<'EOF'
Workspace file: the saved windows and their exact text

WorkspaceFile writes spec 8's workspace.toml atomically from a Snapshot
of window records (role, bounds, maximized bits, layout, tab records):
nodes flat with parent links, split ids name-based on their path, tabs
in workspace order. AppDirs names the file. Nothing calls it yet.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Reading the workspace file

The bounded, never-throwing read: round trips, normalization, per-key fallbacks, bad tab and window entries dropped alone,
an unreadable file set aside, the cap and the depth limit (Rulings 13 to 15).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java`,
  `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java:277`, `:283`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileReadTest.java`

**Interfaces:**
- Consumes: Task 4's values, `encode`, `splitId`, test helpers; Tasks 1 to 3's `Layout` (`activate`, `toggleMaximize`,
  `remove`, `split`, `group`, `groups`); `TomlStateFile.readBounded`; `UiState.bounds(TomlTable)` and
  `UiState.number(TomlTable, String)`, made package-private here.
- Produces: `sealed interface WorkspaceFile.Loaded` with `record Loaded.Restore(Snapshot snapshot)` and
  `record Loaded.Fresh(Optional<Path> setAside)`; `static Loaded read(Path file)`; package-private
  `static final int MAX_BYTES = 256 * 1024` and `MAX_DEPTH = 64`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileReadTest.java`:

```java
package dev.jasper.app.persistence;

import static dev.jasper.app.persistence.WorkspaceFileTest.BOUNDS;
import static dev.jasper.app.persistence.WorkspaceFileTest.group;
import static dev.jasper.app.persistence.WorkspaceFileTest.groupId;
import static dev.jasper.app.persistence.WorkspaceFileTest.snapshot;
import static dev.jasper.app.persistence.WorkspaceFileTest.tab;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Loaded;
import dev.jasper.app.persistence.WorkspaceFile.Role;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8 and 17: reading the workspace file back, dropping bad entries alone, setting a bad file aside. */
class WorkspaceFileReadTest {
    @TempDir Path dir;

    Path file() {
        return dir.resolve("workspace.toml");
    }

    Path bad() {
        return dir.resolve("workspace.toml.bad");
    }

    Loaded read(String text) throws Exception {
        Files.writeString(file(), text, StandardCharsets.UTF_8);
        return WorkspaceFile.read(file());
    }

    /** {@code text} with {@code anchor}, which must occur exactly once, replaced. */
    static String replaceOnce(String text, String anchor, String replacement) {
        assertThat(text.split(Pattern.quote(anchor), -1)).as("occurrences of %s", anchor).hasSize(2);
        return text.replace(anchor, replacement);
    }

    static String id(TabId tab) {
        return tab.value().toString();
    }

    /** {@link WorkspaceFileTest#snapshot()} with its main window changed. */
    static Snapshot withMain(Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, bounds, extendedState, layout, tabs), snapshot().children().getFirst()));
    }

    static Map<TabId, TabRecord> without(Map<TabId, TabRecord> tabs, TabId tab) {
        var rest = new HashMap<>(tabs);
        rest.remove(tab);
        return rest;
    }

    static Map<TabId, TabRecord> terminals(Layout layout) {
        var tabs = new HashMap<TabId, TabRecord>();
        for (TabId tab : layout.tabs()) tabs.put(tab, new TabRecord.Terminal("", "/tmp", ""));
        return tabs;
    }

    /** A window of groups {@code first} and {@code second} side by side, each holding the tab of its number. */
    static WindowRecord window(Role role, int first, int second) {
        Layout layout = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(first, first, first), 0.5),
            new Child(group(second, second, second), 0.5))), groupId(first), Optional.empty());
        return new WindowRecord(role, Optional.of(BOUNDS), 0, layout, terminals(layout));
    }

    @Test void aWrittenWorkspaceReadsBackEqual() throws Exception {
        WorkspaceFile.write(file(), snapshot());
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(snapshot()));
    }

    @Test void theEmptyMainWorkspaceReadsBack() throws Exception {
        var empty = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.empty(), Map.of())));
        WorkspaceFile.write(file(), empty);
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(empty));
    }

    @Test void stringsWithQuotesBackslashesNewlinesAndOddKeysRoundTrip() throws Exception {
        Layout layout = window(Role.MAIN, 1, 2).layout();
        var odd = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, layout, Map.of(
            tab(1), new TabRecord.Terminal("say \"hi\"\tnow\nplease", "C:\\Users\\me\\caf\u00e9", "pwsh 7"),
            tab(2), new TabRecord.Content("", "dev.jasper.sample", "about",
                Map.of("a b", "x\\y", "dotted.key", "\"quoted\"", "plain", "line\nbreak", "_-9", ""))))));
        WorkspaceFile.write(file(), odd);
        assertThat(Files.readString(file()))
            .contains("state = { _-9 = \"\", \"a b\" = \"x\\\\y\", \"dotted.key\" = \"\\\"quoted\\\"\", plain = \"line\\u000Abreak\" }");
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(odd));
    }

    @Test void rewritingAReadFileWritesTheSameText() throws Exception {
        Layout drifting = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1, 1), 1), new Child(group(2, 2, 2), 1),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(3, 3, 3), 0.1), new Child(group(4, 4, 4), 0.2),
                new Child(group(5, 5, 5), 0.7))), 1))), groupId(1), Optional.empty());
        var saved = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, drifting, terminals(drifting))));
        WorkspaceFile.write(file(), saved);
        String first = Files.readString(file());
        var restored = (Loaded.Restore) WorkspaceFile.read(file());
        assertThat(restored.snapshot()).isEqualTo(saved);
        WorkspaceFile.write(file(), restored.snapshot());
        assertThat(Files.readString(file())).isEqualTo(first);
    }

    @Test void weightsAreNormalizedOnRead() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        text = replaceOnce(text, "weight = 0.5\ntabs = [\"" + id(tab(1)), "weight = 3\ntabs = [\"" + id(tab(1)));
        text = replaceOnce(text, "weight = 0.5\ntabs = [\"" + id(tab(2)), "weight = 1.0\ntabs = [\"" + id(tab(2)));
        var restored = (Loaded.Restore) read(text);
        assertThat(((Split) restored.snapshot().main().layout().root()).children()).extracting(Child::weight).containsExactly(0.75, 0.25);
    }

    @Test void boundsAndTheChoicesFallBackAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().main();
        Layout layout = main.layout();
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString();
        assertThat(read(replaceOnce(text, "width = 1400", "width = 0"))).as("bounds")
            .isEqualTo(new Loaded.Restore(withMain(Optional.empty(), 6, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "extended_state = 6", "extended_state = 1"))).as("extended state")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 0, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "active_group = \"" + g2 + "\"", "active_group = \"nope\""))).as("the first group; the maximize no longer names it")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(1), Optional.empty()), main.tabs())));
        assertThat(read(replaceOnce(text, "maximized = \"" + g2 + "\"", "maximized = \"" + g1 + "\""))).as("a maximize of a group that is not active")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(2), Optional.empty()), main.tabs())));
        Split root = (Split) layout.root();
        var firstTab = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1, 1, 2), 0.5), root.children().get(1)));
        assertThat(read(replaceOnce(text, "selected = \"" + id(tab(2)) + "\"", "selected = \"" + id(tab(9)) + "\""))).as("the first tab")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(firstTab, groupId(2), Optional.of(groupId(2))), main.tabs())));
    }

    @Test void aBadTabEntryIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().main();
        String t1 = "id = \"" + id(tab(1)) + "\"\n";
        var droppedTerminal = new Loaded.Restore(withMain(main.bounds(), 6, main.layout().remove(tab(1)), without(main.tabs(), tab(1))));
        for (String[] change : new String[][] {
                {"directory = \"/Users/me/src/jasper\"", "directory = 5"},
                {t1 + "kind = \"terminal\"", t1 + "kind = \"telnet\""},
                {"title = \"build\"\n", ""},
                {"profile = \"zsh\"", "profile = [\"zsh\"]"},
                {t1, "id = \"one\"\n"}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedTerminal);
        String state = "state = { path = \"/Users/me/src/jasper/README.md\" }";
        String kind = "\"content:dev.jasper.editor:document\"";
        var droppedContent = new Loaded.Restore(withMain(main.bounds(), 6, main.layout().remove(tab(2)), without(main.tabs(), tab(2))));
        for (String[] change : new String[][] {
                {state, "state = { path = 1 }"},
                {state, "state = \"/Users/me/src/jasper/README.md\""},
                {kind, "\"content:dev.jasper.editor\""},
                {kind, "\"content::document\""},
                {kind, "\"content:dev.jasper.editor:\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedContent);
        String second = "\n[[windows.tabs]]\nid = \"" + id(tab(2)) + "\"\nkind = \"terminal\"\ntitle = \"\"\ndirectory = \"\"\nprofile = \"\"\n";
        assertThat(read(replaceOnce(text, "\n[[windows]]\nrole = \"child\"", second + "\n[[windows]]\nrole = \"child\"")))
            .as("the first entry for an id wins").isEqualTo(new Loaded.Restore(snapshot()));
    }

    @Test void aGroupEmptiedByADroppedTabGoesAsRemoveWould() throws Exception {
        WindowRecord main = snapshot().main();
        Layout expected = main.layout().remove(tab(3));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(3, 4, 4), 0.5))));
        assertThat(expected.activeGroup()).isEqualTo(groupId(1));
        assertThat(expected.maximized()).isEmpty();
        String t3 = "id = \"" + id(tab(3)) + "\"\n";
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), t3 + "kind = \"terminal\"", t3 + "kind = \"telnet\"")))
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(3)))));
    }

    @Test void aTabWithoutAnEntryIsDroppedAndAnEntryNoGroupListsIsIgnored() throws Exception {
        WindowRecord main = snapshot().main();
        Layout expected = main.layout().remove(tab(4));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(2, 3, 3), 0.5))));
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), "id = \"" + id(tab(4)) + "\"\n", "id = \"" + id(tab(9)) + "\"\n")))
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(4)))));
    }

    @Test void aBrokenTreeDropsItsWindowAlone() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4))));
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        String g3 = groupId(3).value().toString(), g4 = groupId(4).value().toString(), t3 = id(tab(3)), t4 = id(tab(4));
        String split = "kind = \"split\"\norientation = \"horizontal\"\nparent = \"\"\nweight = 1.0\n\n[[windows.nodes]]\nid = \"" + g3 + "\"";
        String g4Node = "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"" + WorkspaceFile.splitId("") + "\"";
        String g4Weight = "weight = 0.5\ntabs = [\"" + t4 + "\"]";
        String g3Tabs = "tabs = [\"" + t3 + "\"]";
        for (String[] change : new String[][] {
                {"role = \"child\"", "role = \"side\""},
                {split, split.replace("kind = \"split\"", "kind = \"pane\"")},
                {split, split.replace("horizontal", "diagonal")},
                {g4Weight, g4Weight.replace("0.5", "-1.0")},
                {g4Weight, g4Weight.replace("0.5", "\"half\"")},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"\""},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"" + g3 + "\""},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"00000000-0000-4000-9000-000000000099\""},
                {g3Tabs, "tabs = [\"" + t3 + "\", \"" + t3 + "\"]"},
                {g3Tabs, "tabs = \"" + t3 + "\""},
                {g3Tabs, "tabs = [\"three\"]"},
                {g3Tabs + "\nselected = \"" + t3 + "\"", "tabs = []\nselected = \"\""},
                {"id = \"" + g3 + "\"", "id = \"group-three\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(mainOnly);
        String cycle = "\n[[windows.nodes]]\nid = \"x\"\nkind = \"split\"\norientation = \"vertical\"\nparent = \"y\"\nweight = 0.5\n"
            + "\n[[windows.nodes]]\nid = \"y\"\nkind = \"split\"\norientation = \"horizontal\"\nparent = \"x\"\nweight = 0.5\n";
        assertThat(read(text + cycle)).as("a cycle under no root").isEqualTo(mainOnly);
    }

    @Test void aTreeDeeperThanTheLimitDropsItsWindow() throws Exception {
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.of(tab(1)), Map.of(tab(1), new TabRecord.Terminal("", "/tmp", "")));
        for (int depth : new int[] {WorkspaceFile.MAX_DEPTH, WorkspaceFile.MAX_DEPTH + 1}) {
            Layout nested = Layout.of(tab(100));
            for (int i = 1; i <= depth; i++)
                nested = nested.split(tab(100 + i), nested.groupOf(tab(99 + i)).orElseThrow(), i % 2 == 0 ? Side.RIGHT : Side.DOWN);
            var saved = new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, nested, terminals(nested))));
            WorkspaceFile.write(file(), saved);
            assertThat(WorkspaceFile.read(file())).as("depth %d", depth)
                .isEqualTo(new Loaded.Restore(depth > WorkspaceFile.MAX_DEPTH ? new Snapshot(List.of(main)) : saved));
        }
    }

    @Test void aSecondMainWindowAnEmptiedChildAndAWindowHoldingAnotherWindowsTabAreDropped() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(snapshot().main())));
        assertThat(read(replaceOnce(text, "role = \"child\"", "role = \"main\""))).as("a second main window").isEqualTo(mainOnly);
        String t5 = "id = \"" + id(tab(5)) + "\"\n";
        assertThat(read(replaceOnce(text, t5 + "kind = \"terminal\"", t5 + "kind = \"x\""))).as("a child window left empty").isEqualTo(mainOnly);
        assertThat(text.split(Pattern.quote(id(tab(5))), -1)).hasSize(4);
        assertThat(read(text.replace(id(tab(5)), id(tab(1))))).as("a tab the main window holds").isEqualTo(mainOnly);
    }

    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Fresh(Optional.empty()));
        try (var listing = Files.list(dir)) {
            assertThat(listing).isEmpty();
        }
    }

    @Test void anUnreadableFileIsSetAsideAndStartsFresh() throws Exception {
        String valid = WorkspaceFile.encode(snapshot());
        var cases = new LinkedHashMap<String, byte[]>();
        cases.put("not TOML", "not toml = = =".getBytes(StandardCharsets.UTF_8));
        cases.put("empty", new byte[0]);
        cases.put("version 2", replaceOnce(valid, "version = 1", "version = 2").getBytes(StandardCharsets.UTF_8));
        cases.put("no version", replaceOnce(valid, "version = 1\n", "").getBytes(StandardCharsets.UTF_8));
        cases.put("no windows", "version = 1\n".getBytes(StandardCharsets.UTF_8));
        cases.put("no main window", replaceOnce(valid, "role = \"main\"", "role = \"child\"").getBytes(StandardCharsets.UTF_8));
        cases.put("a bad main window", replaceOnce(valid, "role = \"main\"", "role = \"boss\"").getBytes(StandardCharsets.UTF_8));
        cases.put("invalid UTF-8", new byte[] {'v', (byte) 0xC3, (byte) 0x28});
        for (var entry : cases.entrySet()) {
            Files.write(file(), entry.getValue());
            assertThat(WorkspaceFile.read(file())).as(entry.getKey()).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
            assertThat(file()).as(entry.getKey()).doesNotExist();
            assertThat(Files.readAllBytes(bad())).as("%s, replacing the older copy", entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test void aNestingThatOverflowsTheParserIsSetAside() throws Exception {
        String deep = "version = 1\nx = " + "[".repeat(250_000);
        assertThat(deep.length()).as("under the cap: the parser, not the cap, refuses it").isLessThan(WorkspaceFile.MAX_BYTES);
        assertThat(read(deep)).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }

    @Test void theSizeCapSetsAsideALargerFile() throws Exception {
        String valid = WorkspaceFile.encode(snapshot());
        String atCap = valid + "#" + " ".repeat(WorkspaceFile.MAX_BYTES - valid.length() - 2) + "\n";
        assertThat(atCap.getBytes(StandardCharsets.UTF_8)).hasSize(WorkspaceFile.MAX_BYTES);
        assertThat(read(atCap)).isEqualTo(new Loaded.Restore(snapshot()));
        assertThat(read(atCap.replace("#", "# "))).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }
}
```

The child window of `snapshot()` names tab 5 three times (its group's `tabs`, its `selected` and its tab entry), hence
`hasSize(4)` pieces. The deep-nesting case uses 250,000 brackets, not more, so that it stays under the 256 KiB cap and
reaches the parser.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.WorkspaceFileReadTest'`
Expected: FAIL to compile: "cannot find symbol … class Loaded", "… method read(Path)", "… MAX_BYTES", "… MAX_DEPTH".

- [ ] **Step 3: The reader**

In `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`, replace:

```java
    private static Optional<Bounds> bounds(TomlTable entry) {
```

with:

```java
    static Optional<Bounds> bounds(TomlTable entry) {
```

and replace:

```java
    private static Optional<Integer> number(TomlTable table, String key) {
```

with:

```java
    static Optional<Integer> number(TomlTable table, String key) {
```

In `jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java`, replace:

```java
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. Writes are atomic.
 * Stateless: the caller chooses the thread; the values are immutable.
```

with:

```java
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. {@link #write} writes it
 * atomically; {@link #read} never throws: it drops bad tab and window entries alone and sets an unreadable file aside as
 * {@code workspace.toml.bad}. Stateless: the caller chooses the thread; the values are immutable.
```

Replace:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
```

with:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
```

Replace:

```java
import java.util.stream.Collectors;
```

with:

```java
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
```

Insert, immediately before the line `    private static final String CONTENT = "content:";`:

```java
    /** What {@link #read(Path)} found. */
    public sealed interface Loaded permits Loaded.Restore, Loaded.Fresh {
        /**
         * The saved workspace, its bad entries dropped. A main window whose layout is empty restores the empty workspace.
         *
         * @param snapshot the windows to restore
         */
        record Restore(Snapshot snapshot) implements Loaded {
            /** Checks the snapshot is present. */
            public Restore {
                Objects.requireNonNull(snapshot, "snapshot");
            }
        }

        /**
         * Nothing to restore: start with one terminal tab (spec 8).
         *
         * @param setAside where an unreadable file was moved ({@code workspace.toml.bad} beside it); empty when there was
         *     no file or it could not be moved
         */
        record Fresh(Optional<Path> setAside) implements Loaded {
            /** Checks the value is present. */
            public Fresh {
                Objects.requireNonNull(setAside, "setAside");
            }
        }
    }

    /** The largest file read: {@code ui-state.toml}'s cap (workspace plan 2, Rulings 15). */
    static final int MAX_BYTES = 256 * 1024;
    /** The deepest a saved tree may nest (workspace plan 2, Rulings 14); it bounds the reader's recursion. */
    static final int MAX_DEPTH = 64;
    private static final System.Logger LOG = System.getLogger(WorkspaceFile.class.getName());

    /**
     * Reads {@code file}; never throws, not even when tomlj's parser overflows the stack on deep nesting (workspace plan 2,
     * Rulings 13 and 14). A bad tab entry, or a tab a group lists without one, is dropped as {@link Layout#remove(TabId)}
     * drops it; a bad window entry is dropped; bounds and choices fall back alone. A file that cannot be read, does not
     * parse, has another version or keeps no main window is moved to {@code workspace.toml.bad} beside it, replacing an
     * older one.
     *
     * @param file the workspace file
     * @return the windows to restore, or that Jasper starts fresh and what was set aside
     */
    public static Loaded read(Path file) {
        String text;
        try {
            Optional<String> read = TomlStateFile.readBounded(file, MAX_BYTES, "Workspace state");
            if (read.isEmpty()) return new Loaded.Fresh(Optional.empty());
            text = read.get();
        } catch (IOException unreadable) {
            return setAside(file, unreadable);
        }
        try {
            return new Loaded.Restore(parse(text));
        } catch (IOException | RuntimeException | StackOverflowError invalid) {
            // tomlj's parser has no nesting guard: deeply nested arrays overflow the stack inside Toml.parse.
            return setAside(file, invalid);
        }
    }

    private static Loaded setAside(Path file, Throwable cause) {
        Path bad = file.resolveSibling(file.getFileName() + ".bad");
        try {
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            failed.addSuppressed(cause);
            LOG.log(System.Logger.Level.WARNING, "Could not set aside the unreadable workspace state " + file, failed);
            return new Loaded.Fresh(Optional.empty());
        }
        LOG.log(System.Logger.Level.WARNING, "Set aside the unreadable workspace state " + file + " as " + bad, cause);
        return new Loaded.Fresh(Optional.of(bad));
    }

    private static Snapshot parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid workspace state: " + toml.errors().getFirst());
        Object version = toml.get(List.of("version"));
        if (!Long.valueOf(1).equals(version)) throw new IOException("Unknown workspace state version: " + version);
        var windows = new ArrayList<WindowRecord>();
        var groups = new HashSet<GroupId>();
        var tabs = new HashSet<TabId>();
        if (toml.get(List.of("windows")) instanceof TomlArray entries)
            for (int i = 0; i < entries.size(); i++) {
                Optional<WindowRecord> read = window(entries.get(i));
                if (read.isEmpty()) continue;
                WindowRecord window = read.get();
                List<GroupId> ids = window.layout().groups().stream().map(Group::id).toList();
                if (window.role() == Role.MAIN && windows.stream().anyMatch(kept -> kept.role() == Role.MAIN)) {
                    LOG.log(System.Logger.Level.WARNING, "Dropping a second main window from the workspace state");
                } else if (ids.stream().anyMatch(groups::contains) || window.layout().tabs().stream().anyMatch(tabs::contains)) {
                    LOG.log(System.Logger.Level.WARNING, "Dropping a window whose groups or tabs an earlier window holds");
                } else {
                    groups.addAll(ids);
                    tabs.addAll(window.layout().tabs());
                    windows.add(window);
                }
            }
        if (windows.stream().noneMatch(kept -> kept.role() == Role.MAIN)) throw new IOException("The workspace state keeps no main window");
        return new Snapshot(windows);
    }

    /** One window entry with its bad tabs dropped; empty, and logged, when the entry itself is bad. */
    private static Optional<WindowRecord> window(Object value) {
        try {
            if (!(value instanceof TomlTable entry)) throw new IllegalArgumentException("a window entry is not a table");
            Object name = entry.get(List.of("role"));
            Role role = "main".equals(name) ? Role.MAIN : "child".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            Layout layout = layout(entry);
            Map<TabId, TabRecord> records = tabs(entry);
            for (TabId tab : layout.tabs()) {
                if (records.containsKey(tab)) continue;
                LOG.log(System.Logger.Level.WARNING, "Dropping tab " + tab + " from the workspace state: it has no readable entry");
                layout = layout.remove(tab);
            }
            records.keySet().retainAll(Set.copyOf(layout.tabs()));
            int extendedState = UiState.number(entry, "extended_state").filter(state -> (state & ~6) == 0).orElse(0);
            return Optional.of(new WindowRecord(role, UiState.bounds(entry), extendedState, layout, records));
        } catch (RuntimeException bad) {
            LOG.log(System.Logger.Level.WARNING, "Dropping a window from the workspace state: " + bad.getMessage());
            return Optional.empty();
        }
    }

    /** A window's tree from its flat nodes, with the active group and the maximize falling back alone. */
    private static Layout layout(TomlTable window) {
        if (!(window.get(List.of("nodes")) instanceof TomlArray nodes)) throw new IllegalArgumentException("a window has no nodes");
        var byId = new HashMap<String, TomlTable>();
        var children = new HashMap<String, List<String>>();
        String root = null;
        for (int i = 0; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof TomlTable node)) throw new IllegalArgumentException("a node entry is not a table");
            String id = string(node, "id");
            if (id.isEmpty() || byId.putIfAbsent(id, node) != null) throw new IllegalArgumentException("node id " + id + " is empty or used twice");
            String parent = string(node, "parent");
            if (!parent.isEmpty()) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
            else if (root == null) root = id;
            else throw new IllegalArgumentException("a window has two root nodes");
        }
        if (root == null) throw new IllegalArgumentException("a window has no root node");
        var reached = new HashSet<String>();
        Node tree = node(root, byId, children, reached, 0);
        if (reached.size() != byId.size()) throw new IllegalArgumentException("a node is not under the window's root");
        Layout plain = new Layout(tree, first(tree), Optional.empty());
        Layout active = groupId(window.get(List.of("active_group"))).filter(id -> plain.group(id).isPresent()).map(plain::activate).orElse(plain);
        return groupId(window.get(List.of("maximized"))).filter(active.activeGroup()::equals).map(active::toggleMaximize).orElse(active);
    }

    /** The node {@code id} and everything under it; a split's children are the nodes naming it, in file order. */
    private static Node node(String id, Map<String, TomlTable> byId, Map<String, List<String>> children, Set<String> reached, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("a window's tree is deeper than " + MAX_DEPTH);
        reached.add(id);
        TomlTable table = byId.get(id);
        Object kind = table.get(List.of("kind"));
        if ("group".equals(kind)) {
            if (children.containsKey(id)) throw new IllegalArgumentException("group " + id + " is a parent");
            if (!(table.get(List.of("tabs")) instanceof TomlArray list)) throw new IllegalArgumentException("group " + id + " has no tab list");
            var tabs = new ArrayList<TabId>();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof String tab)) throw new IllegalArgumentException("group " + id + " lists a tab that is not a string");
                tabs.add(TabId.parse(tab));
            }
            Optional<TabId> selected = tabId(table.get(List.of("selected"))).filter(tabs::contains).or(() -> tabs.stream().findFirst());
            return new Group(GroupId.parse(id), tabs, selected);
        }
        if (!"split".equals(kind)) throw new IllegalArgumentException("node " + id + " has an unknown kind " + kind);
        Object axis = table.get(List.of("orientation"));
        Orientation orientation = "horizontal".equals(axis) ? Orientation.HORIZONTAL : "vertical".equals(axis) ? Orientation.VERTICAL : null;
        if (orientation == null) throw new IllegalArgumentException("split " + id + " has no orientation");
        var parts = new ArrayList<Child>();
        for (String child : children.getOrDefault(id, List.of()))
            parts.add(new Child(node(child, byId, children, reached, depth + 1), weight(byId.get(child))));
        return new Split(orientation, parts);
    }

    /** The first group in tree order. */
    private static GroupId first(Node node) {
        Node current = node;
        while (current instanceof Split split) current = split.children().getFirst().node();
        return ((Group) current).id();
    }

    /** A window's tab entries by id; a bad entry is logged and left out, and the first entry for an id wins. */
    private static Map<TabId, TabRecord> tabs(TomlTable window) {
        var records = new HashMap<TabId, TabRecord>();
        if (!(window.get(List.of("tabs")) instanceof TomlArray entries)) return records;
        for (int i = 0; i < entries.size(); i++) {
            try {
                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a tab entry is not a table");
                TabId id = TabId.parse(string(entry, "id"));
                TabRecord parsed = tab(entry);
                if (records.putIfAbsent(id, parsed) != null) throw new IllegalArgumentException("tab " + id + " is listed twice");
            } catch (RuntimeException bad) {
                LOG.log(System.Logger.Level.WARNING, "Dropping a tab entry from the workspace state: " + bad.getMessage());
            }
        }
        return records;
    }

    private static TabRecord tab(TomlTable entry) {
        String kind = string(entry, "kind");
        String title = string(entry, "title");
        if (kind.equals("terminal")) return new TabRecord.Terminal(title, string(entry, "directory"), string(entry, "profile"));
        if (!kind.startsWith(CONTENT)) throw new IllegalArgumentException("unknown tab kind " + kind);
        String rest = kind.substring(CONTENT.length());
        int colon = rest.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("content kind " + kind + " names no restorer");
        if (!(entry.get(List.of("state")) instanceof TomlTable table)) throw new IllegalArgumentException("a content tab has no state table");
        var state = new HashMap<String, String>();
        for (String key : table.keySet()) {
            if (!(table.get(List.of(key)) instanceof String value)) throw new IllegalArgumentException("restore state " + key + " is not a string");
            state.put(key, value);
        }
        return new TabRecord.Content(title, rest.substring(0, colon), rest.substring(colon + 1), state);
    }

    private static String string(TomlTable table, String key) {
        if (table.get(List.of(key)) instanceof String value) return value;
        throw new IllegalArgumentException(key + " is missing or not a string");
    }

    private static double weight(TomlTable node) {
        if (node.get(List.of("weight")) instanceof Number weight) return weight.doubleValue();
        throw new IllegalArgumentException("a node's weight is not a number");
    }

    private static Optional<GroupId> groupId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(GroupId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<TabId> tabId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(TabId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

```

The enhanced `for` over `layout.tabs()` iterates the list it read once while `layout` is reassigned inside it, as
intended. Weights come in as `Long` or `Double` (fact 8); a root's `weight` is not read.

- [ ] **Step 4: Run the persistence tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' :jasper-app:javadoc verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL; `WorkspaceFileReadTest` 16 tests pass; `WorkspaceFileTest`, `UiStateTest`, `UiStateLookTest`,
`TomlStateFileTest` and `BuddyStateFileTest` still pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/persistence jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFileReadTest.java
git commit -F - <<'EOF'
Workspace file: reading it back

WorkspaceFile.read never throws: it normalizes weights, lets bounds,
the active group, the maximize and selections fall back alone, drops a
bad tab entry as Layout.remove would and a bad window entry alone, and
sets an unreadable or oversized file aside as workspace.toml.bad (a
stack overflow in tomlj's parser included) so the caller starts with
one terminal tab.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/app-architecture.md:162`, `AGENTS.md:25`, `docs/STATUS.md` (top), this plan's status banner (line 3)

- [ ] **Step 1: The documents**

In `docs/app-architecture.md`, after the line that begins ``| `dock` | Pure tool window dock model:`` (the whole row,
unchanged), insert this row:

```markdown
| `layout` | Pure workspace layout model: tab and group ids, a workspace's tab groups in nested weighted splits (`Layout`, `Split`, `Group`), its active and maximized group and the groups' order of use, the operations that return a new layout, and each group's region of the unit square with its directional neighbours. JDK-only (nothing from Swing or AWT, enforced by `verifyApplicationArchitecture`); immutable values, safe on any thread. `persistence.WorkspaceFile` reads and writes it as `workspace.toml`. |
```

In `AGENTS.md`, replace:

```markdown
- `dev.jasper.app.dock` (the tool window dock model: slots, `DockState`, `DockGeometry`) is JDK-only: no Swing, and from `java.awt` only `Rectangle` and `Point`. `verifyApplicationArchitecture` enforces it.
```

with:

```markdown
- `dev.jasper.app.dock` (the tool window dock model: slots, `DockState`, `DockGeometry`) is JDK-only: no Swing, and from `java.awt` only `Rectangle` and `Point`. `dev.jasper.app.layout` (the workspace layout model) is JDK-only with nothing from `java.awt`. `verifyApplicationArchitecture` enforces both.
```

- [ ] **Step 2: Source hygiene**

```bash
python3 -I - jasper-app/src/main/java/dev/jasper/app/layout/*.java jasper-app/src/test/java/dev/jasper/app/layout/*.java \
  jasper-app/src/main/java/dev/jasper/app/persistence/WorkspaceFile.java jasper-app/src/test/java/dev/jasper/app/persistence/WorkspaceFile*.java <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked", len(sys.argv) - 1)
PY
```

Expected: only `checked 20`.

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

Add a new entry at the top of `docs/STATUS.md` (below the `# Jasper — Status and Handoff` title, above the current first
entry), in the style of the earlier entries:

```markdown
**Workspace tabs, plan 2: the layout model and its encoding — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-17-workspace-2-layout-model.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). Pure and unwired: no visible change. New
JDK-only package `dev.jasper.app.layout` (nothing from `java.awt`, enforced by `verifyApplicationArchitecture`):
`TabId` and `GroupId` (UUID records), and `Layout` over a tree of n-ary weighted `Split`s (same-orientation nesting
flattened, weights normalized) and tab `Group`s, with `open`, `select`, `activate`, `split`, `move`, `moveToNewGroup`,
`remove`, `resize`, `toggleMaximize`, `neighbour` (today's `SplitTree.navigate` rule) and `bounds()` over the unit
square (for `neighbour`), `unmaximize`, and orders of use that pick the focused group and the selected tab after a
removal. `persistence.WorkspaceFile` writes spec 8's
`workspace.toml` atomically (`AppDirs.workspaceState()`) and reads it back without throwing: bad tab and window entries
dropped alone, keys falling back alone, an unreadable file (or one over 256 KiB) set aside as `workspace.toml.bad`
(`Loaded.Fresh`). Rulings (plan, "Rulings"): <one clause each, 1 to 20>. Deviations: <none, or each with its reason>.
`SplitTree` stays until plan 3 and `UiState`'s `[main_window]` until plan 6. `./gradlew check --continue`: **<counts>**.
No GUI checks (nothing visible changed). Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add docs AGENTS.md
git commit -F - <<'EOF'
Workspace layout: documentation and status

The package table gains dev.jasper.app.layout, AGENTS.md states its
JDK-only rule, and STATUS records plan 2 with its rulings and the full
build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage: 7.1 values (Task 1; `Group.selected` as `Optional`, Rulings 1), 7.2 invariants checked on construction
  (Task 1: flattening, two children, positive normalized weights, unique ids, selection in tabs, active and maximized
  present, the empty workspace), 7.3 operations (Task 2: `open`, `select`, `activate`, `move`, `remove` with the MRU rule;
  Task 3: `split`, `moveToNewGroup`, `resize`, `toggleMaximize`, `unmaximize`, `neighbour`, plus `bounds()` for
  `neighbour` and tests; plan 5 resolves drops in pixels, Rulings 10), the orders of use not persisted (Rulings 2 and
  7), spec 8's file and its bad-entry rules (Tasks 4 and 5; the cap is `ui-state.toml`'s, Rulings 15), spec 15's
  "maximized group loses or gains a tab by a move" (read with closing keeping it, Spec issues 1) and "last tab in a
  group" rows (Tasks 2 and 3), spec 17's layout, encoding and
  architecture tests (Tasks 1 to 5), spec 4's JDK-only principle (Task 1), the package table and STATUS (Task 6). Out of
  this plan by spec 18: the view, the registry, wiring, `[main_window]`'s removal.
- Review Focus: 1 → Task 4 `writesSpecEightsWorkspaceExactly` and Task 5 `stringsWithQuotesBackslashesNewlinesAndOddKeysRoundTrip`;
  2 → Task 5 `rewritingAReadFileWritesTheSameText`; 3 → Task 3 `splittingRightOrDownThenClosingRestoresTheSizes`;
  4 → Task 5 `aBrokenTreeDropsItsWindowAlone`, `aTreeDeeperThanTheLimitDropsItsWindow` and
  `aNestingThatOverflowsTheParserIsSetAside`; 5 → Task 2
  `removalDownToTheEmptyWorkspaceKeepsItsGroupForTheNextTab` and Task 3 `splitRefusesAKnownTabAnUnknownGroupAndTheEmptyWorkspace`.
- Names across tasks: `activated`, `requireGroup`, `requireGroupOf`, `replace` (Task 2 → Task 3); `Layout.split` (Task 3 →
  Task 5's depth test); `WorkspaceFileTest.tab`, `groupId`, `group`, `BOUNDS`, `snapshot` and `WorkspaceFile.encode`,
  `splitId` (Task 4 → Task 5); `UiState.bounds` and `number` package-private (Task 5); `MAX_BYTES`, `MAX_DEPTH` (Task 5);
  `tabOrders` and the five-argument private constructor (Task 1 → Tasks 2 and 3); `unmaximize` (Task 3, called by
  `toggleMaximize`).
- Ledger: Tasks 1 and 4 append the rows for `Split`, `Layout`, `LayoutValuesTest` and `WorkspaceFile` and run
  `ThemeMigrationCoverageTest`; no other new or edited file matches its pattern (fact 14).
