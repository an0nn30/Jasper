# Enterprise presentation 3c: SDK 0.18 tables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SDK 0.18 gains spec 8.2's table vocabulary: `Tree.columns`, `TableRow.cellNodes` (with the borderless in-cell
DROPDOWN), `TableRow.heading` (with `Heading` and `countNoun`), `Table.empty`, `Table.emptyText` and
`ItemList.emptyText`, each validated, mirrored in the jasper-ui model, projected losslessly by `KitValues`, carried by
the testkit fake and the shared kit contract, and drawn by the default look, with no existing gallery pixel changed.

**Architecture:** The SDK records and their identical jasper-ui model mirrors gain trailing components; every existing
constructor stays as a convenience with the new components empty, so no caller changes. Record constructors check what
one record can see (cell-node kinds, heading rows, the exclusive empty states); `ViewSpec` checks what needs the whole
view (cell-node columns, global ids of cell nodes and empty states, tree values against columns). The default look
renders the additions inside the existing row machinery: `Tables` mounts cell nodes in their weighted columns and
heading rows as non-data rows, `Choices` gives a DROPDOWN in a cell its bare face, `InteractiveRows` gains the empty
line and lets an empty table's state fill the body, and `Trees` gains a column header aligned with its trailing values.
Nothing renders differently while the new components are empty.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(section 8.2's first paragraph and its rows `Tree.columns`, `TableRow.cellNodes`, `TableRow.heading`, `Table.empty`,
`Table.emptyText` and `ItemList.emptyText`; principles 3.6 and 3.9; 16.1.3 and 16.1.9 for the tests; 17 for the
documentation; row 3c of 19.1). The mocks: `docs/design/enterprise/reference/01 Workstation/` (Sessions Name/Host/Port,
Tunnels "No Tunnels" [New Tunnel…], SFTP "Not Connected", File Servers "test [HTTP]"), `05 Settings/Settings 03 -
Appearance & Behavior - Notifications.png` (heading rows "6 groups", the "Card ▼" cells), `05 Settings/Settings 07 -
Keymap.png` (Action/Scope/Shortcut) and `03 Add Host/Add Host - Default.png` ("No keys"). Adopting the vocabulary in
plugins and app pages is plans 3e, 9 and 10, not this plan.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-3c`, branch
`claude/enterprise-sdk-0-18-tables`. The branch holds only this plan's commit on `222969926`; plans 3a and 3b merge to
`main` first. Before Task 1, run `git rebase main` (it replays this one commit) and check the base: `grep -c
'EXCLUSIVE' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java` prints a number above 0 and `grep VERSION
jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` shows `0.18.0`. Run every command from the worktree root,
never in `~/projects/moray` itself. Line numbers below are from `222969926`; every edit quotes the text it replaces,
and the edit scripts stop with a message when their text is not found exactly the stated number of times (3a and 3b
edit some of the same files, so re-find an anchor by its text, never by its number).

## Verified facts

At `222969926` (the mirrors are byte-identical once the package names are mapped; checked with `diff`):

1. `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java` and `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java`
   are identical but for the package; so are `TableRow.java`, `TreeItem.java`, `Column.java`, `ListItem.java`,
   `ModelChecks.java`, `ViewSpec.java` and `UiEvent.java`.
2. `Nodes.Table` is `(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, scroll)`
   with 8- and 7-argument conveniences (`Nodes.java:591-613`); `Nodes.Tree` is `(id, state, roots, expandedIds,
   selectedIds, activateAction)` with no convenience (`:615-623`); `Nodes.ItemList` has 15 components ending in
   `cancelAction` and conveniences of 12, 10, 9 and 7 arguments (`:460-524`); `Nodes.Empty` is `(id, state, icon,
   title, description, actions)` (`:450-458`).
3. `TableRow` is `(id, cells, icon, muted, contextActions, tooltip)` with 5- and 3-argument conveniences
   (`TableRow.java:14-27`); `TreeItem.trailing` is a `List<String>` drawn as right-aligned columns
   (`TreeItem.java:9-16`); `Column` is `(id, title, role, numeric, weight, sorted)` (`Column.java:11`).
4. `ViewSpec.validate` checks table cell keys against column ids (`ViewSpec.java:54-58`), walks tree items with
   `tree(item, ids, branches, depth)` (`:21-26`, `:59-63`), and collects children per node type (`:33-65`).
   `ListItem.inline` is the row whitelist (`ListItem.java:42-51`).
5. `KitValues` projects by constructing the model records field by field: Table at `KitValues.java:32`, Tree at `:33`,
   ItemList at `:27`, `toModule(TableRow)` at `:43`. `KitValuesTest` (jasper-app) checks losslessness by comparing
   `toString()` of the SDK and the projected spec (`KitValuesTest.java:35`, `:85`). `Map.copyOf` of an immutable map
   returns that map, which is why `cells` prints alike after projection.
6. The descriptor walks: `FakeKitView.children` (`jasper-sdk-testkit/.../FakeKitView.java:101-118`) and
   `KitContent.children` (`jasper-app/.../plugins/KitContent.java:24-41`) feed both `KitIconNames` copies
   (`:48` in each), the fake's node index and anchors. Neither visits table rows.
7. `KitContractTest` (testkit `testFixtures`) runs against the host (`HostedKitContractTest`) and the fake
   (`FakeKitTest`); its `descriptorIconCases` map (`KitContractTest.java:148-173`) is the parity table for icon
   validation; `browser(...)` and `browserDescriptorsMountUpdateAndReportSortsCrumbsAndMenus` (`:227-247`) are the
   pattern for a descriptor-family contract.
8. Rendering: `ControlRegistry.validate` treats Table and Tree as leaves (`ControlRegistry.java:20`);
   `ControlRegistry.children` gives an ItemList its `addAction` (`:62`) and nothing for Table; the reconciler mounts
   exactly `ControlRegistry.children` (`ViewReconciler.java:338-340`) and hands them to `Control.children`
   (`:52`). Row content is mounted per row by `InteractiveRows.Row.control` (`InteractiveRows.java:915-985`), which inks
   Text, Icon, Button, Toggle and Chip controls on a selected row and calls `embeddedLabel(true)`.
9. `Tables.TableControl.populate` builds one synthetic `Text` per column, casts every title child to `JLabel`, and sets
   alignment, the row glyph and muted ink (`Tables.java:187-231`); the header labels are built in `data()`
   (`:154-182`) and report `UiEvent.Sorted`; `itemText` reads the first column's plain cell (`:89-93`).
10. Headings: `RowsControl.dataRow` (`InteractiveRows.java:221`) drives `selectable`, `step`, `typeAhead`, `range`,
    `activate`, `context` and `paintFill`; a non-data row lays out with `Row.headingLayout` (`:1176-1186`), which
    zeroes the trailing slot, and paints a hairline above itself when it is not first (`:1218-1228`).
    `ItemLists.ListControl` overrides `dataRow` for `ListItem.heading` (`ItemLists.java:157-160`).
11. Row geometry: `Canvas.getPreferredSize` is `rowsHeight() + footerHeight()` and its viewport size
    `viewportRowsHeight() + footerHeight()` (`InteractiveRows.java:769-776`); it never tracks the viewport height
    (`:794-796`); `sync` places `footer` at `rowsHeight()` (`:443-444`) and then calls `layoutAccessories(width)`.
    `RowsControl.inline` and `revealInline` let `KitView.focus` and `reveal` reach row-mounted nodes
    (`ViewReconciler.java:399-407`, `KitView.java:220-238`); `ItemLists` implements them with a `nodeRows` index
    (`ItemLists.java:351-375`).
12. `Choices.ChoiceControl` builds a DROPDOWN as a `ChoiceCombo` in a `BorderLayout` panel (`Choices.java:34-75`); the
    closed value is rendered by `optionLabel` (`:76-84`), but `BasicComboBoxUI.paintCurrentValue` then sets the
    renderer's foreground to the combo's own; `ChoiceCombo` paints the
    control face in `paintComponent` (`:382-384`), the chevron in its arrow button (`:318-330`) and sizes itself with
    `naturalWidth()` (`:352-374`).
13. `Trees.TreeControl.data` measures `trailingWidths` over every item in CAPTION type, at least `TREE_DETAIL_COLUMN`
    and, for the last, `TREE_VALUE_COLUMN` (`Trees.java:265-275`); `populate` adds one CAPTION text per trailing value,
    right-aligned at that width, then the COUNT text (`:356-391`); a plain tree row's side inset is `ICON_GAP`, or
    `COLUMN_LIST_SIDE` in a manager column (`InteractiveRows.java:1096`). `Trees.validateDetail` walks table rows for
    icons (`Trees.java:35-41`).
14. Gallery: `KitGallery.fixtures()` ends with the `picker-menu` fixture before `return List.copyOf(result);`
    (`KitGallery.java:66-67`); contact sheets group eight fixtures per `sheet-N.png` in list order (`:165-180`); the
    metadata JSON walks record components that are Nodes, ListItems, Optionals and Iterables only (`:261-273`), so map
    values and table rows' new components add nothing to existing fixtures' JSON. `GalleryCoverageTest.collect` does not
    walk map values (`GalleryCoverageTest.java:139-148`). The headless gallery wrote 142 files per theme at
    `995ff899f` (STATUS, plan 2).
15. Gates: `ThemeMigrationCoverageTest` scans jasper-app, the SDK, the testkit and plugins only
    (`ThemeMigrationCoverageTest.java:62-77`); this plan creates no file there (its new files are under jasper-ui), so
    it adds no `migration.csv` row. The SDK's javadoc runs `-Xdoclint:all` with `isFailOnError`
    (`jasper-sdk/build.gradle.kts:4-11`), jasper-ui's `-Xdoclint:all,-missing` (`jasper-ui/build.gradle.kts:51-52`).

16. Dry run (2026-10-16, while writing this plan, without Gradle): every edit script and file of Tasks 1 to 7 applied
    cleanly, in order, to a copy of `222969926`; the SDK, jasper-ui (main, `testFixtures`, tests), the testkit and
    `KitValues` compiled with the JBR's `javac`; the new and neighbouring tests passed under a JUnit launcher (SDK
    `KitValuesTest` 22, jasper-app `KitValuesTest` 11, the fake's kit contract 32, 231 jasper-ui tests across
    `TableVocabularyTest`, the table, tree, list, choice, ink, empty-state, panel, settings and gallery suites, and
    `KitKeyBindingAuditTest`; `KitMatrixTest` 3); and `KitGallery.main` for Slate Cobalt Dark and Light wrote 142 base
    files of which 140 were byte-identical, `manifest.json` and `sheet-9.png` changing and `tables-0-18.png`/`.json`
    added. 3a's and 3b's edits come in between, so the executor's runs are the evidence; the host contract
    (`HostedKitContractTest`) was not run.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-sdk-0-18-tables`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Scope is exactly spec 8.2's rows `Tree.columns`, `TableRow.cellNodes`, `TableRow.heading` (with `Heading` and `countNoun`), `Table.empty`, `Table.emptyText` and `ItemList.emptyText`. No plugin or app page adopts them (plans 3e, 9 and 10); no other 8.2 row is touched (3b and 3d own them).
- Existing record constructors stay as conveniences with the new components at their empty values (`Map.of()`, `Optional.empty()`, `List.of()`, `""`); new components go last; no existing component is renamed or reordered.
- The SDK and the jasper-ui model stay identical but for their package names: every edit to `Nodes.java`, `TableRow.java` or `ViewSpec.java` is applied to both copies by the same script.
- Pixel rule: every existing gallery fixture renders byte-identically for Slate Cobalt Dark and Light. The headless gallery may change only in: the new `tables-0-18.png` and `tables-0-18.json`; `manifest.json` (`fixtureCount` + 1); and the last contact sheet `sheet-N.png`, which gains the new fixture's cell (or a new `sheet-N.png` when the previous last sheet held eight). Task 6 proves it.
- Javadoc doclint fails the build on errors: every `{@link}` resolves and no comment holds a raw `<`, `>` (outside HTML tags), `&` or `->`.
- `docs/design/theme-engine/migration.csv`: every new or renamed candidate source under jasper-app, the SDK, the testkit or plugins gets its row in the same commit (`ThemeMigrationCoverageTest`); this plan creates none, and Task 7's full build confirms it.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file (`…` and `▼` are ordinary characters and allowed).

## Review Focus

1. A table whose rows arrive while its empty state shows, or vanish while a row is selected: the state unmounts and
   leaves no stale component or focus target behind, and returns when the rows go again. Task 4:
   `TableVocabularyTest.anEmptyTableKeepsItsHeadersAndCentresItsStateInTheBody`.
2. A selected row whose cell dropdown is bare: its value must take the selection's ink, or a dark-on-accent theme
   paints it unreadable; while hovered it shows the control face in the face's own colours. Task 3:
   `TableVocabularyTest.aSelectedRowsBareDropdownTakesTheSelectionInk`.
3. Keyboard, mouse and type-ahead over a grouped table: Down, Shift-ranges, double-click, typing the heading's title and
   the context menu never land on a heading row or report it in `Selected`. Task 3:
   `TableVocabularyTest.aHeadingRowSpansTheTableWithItsMutedCountAndIsNeverSelected`; Task 1:
   `KitValuesTest.headingRowsCarryATitleAndACountAndAreNeverSelected` (a selected heading is refused).
4. A column tree whose items carry fewer values than columns, or whose rows differ in having a count: every value still
   sits under its title. Task 5: `TableVocabularyTest.aColumnTreeShowsItsHeaderOverTheValuesAndReportsSorts`.
5. A row with many cell nodes built from a hash map (an order the plugin does not control): the projection prints and
   compares alike on both sides, and both runtimes walk the cell nodes for ids and icons. Task 1:
   `KitValuesTest.cellNodesArePresentationOrADropdownInAColumnOfTheirOwn` (column-id order); Task 2:
   `KitValuesTest.zeroEighteenTableFieldsProjectWithoutLoss` (jasper-app) and the `table-cell-node` and `table-empty`
   icon cases.

## Planned deviations and rulings

Record each of these in `docs/STATUS.md` (Task 7) and in this plan's status banner when it is executed.

1. **The contract cases live in `KitContractTest`.** Spec 16.1.9 lists "tree columns; cell nodes; heading rows; `empty`
   and `emptyText`" under `PluginContractTest`; they are view-descriptor behaviour, which `KitContractTest` owns and
   runs against the host and the fake, so Task 6 adds them there. `PluginContractTest` is unchanged.
2. **`cellNodes` iterates in column-id order.** The record keeps an unmodifiable sorted copy, so two equal rows print
   alike whatever map built them, which the lossless projection test relies on.
3. **Heading validation the spec leaves open.** A `countNoun` needs a count and is lowercase with no surrounding spaces
   (inner spaces allowed: "file server"); a heading row has no cells, cell nodes, icon, muted flag, menu or tooltip;
   a table never selects a heading row.
4. **The default look of a heading row** is its title in BODY_STRONG and the count in CAPTION (tertiary) at the row's
   trailing inset, with the hairline a list heading draws above it; the rows below are not indented (the Notifications
   mock's indent is the classic look's, plans 4 and 9).
5. **The table's `empty` is mounted only while the table has no rows** (`ControlRegistry.children`); the descriptor
   walks (`FakeKitView.children`, `KitContent.children`) always include it, so ids and icons are checked either way.
   Focusing the state's button while rows show succeeds in the fake and fails in the host; that call has no use.
6. **The empty line** (`emptyText`) shows only while the collection has no items at all (a heading is an item); it is
   one row-height band, centred, in DESCRIPTION type and tertiary ink, above an ItemList's `addAction`.
7. **Tree columns in the default look:** the first title starts at the rows' content inset, the others are
   right-aligned over right-aligned values; each column is as wide as its widest value or title; rows are padded
   with blank values; when any item has a count, a count column is reserved on every row. Trees with the app's keycap
   presentation keep their own layout (the Keymap page is plan 9's).
8. **Muted rows and cell nodes:** a muted row dims its plain cells and its Text cell nodes; other cell nodes keep their
   colours; the row's glyph leads the first column only when that column is a plain cell.
9. **The in-cell dropdown** is as wide as its selected value and chevron (plain options; rich options keep their
   natural width), left-aligned and vertically centred in its cell; the selection ink applies only while it is bare.
10. **No README edit.** Spec 17 names `docs/plugin-ui.md` for the vocabulary; `jasper-sdk/README.md`'s version list is
    3a's and 3d's.

---

### Task 1: The SDK vocabulary and its model mirror

`TableRow` gains `cellNodes` and `heading` (with the nested `Heading` and the static `heading(...)` factory), `Table`
gains `empty` and `emptyText`, `Tree` gains `columns`, and `ItemList` gains `emptyText`, in the SDK and in the
jasper-ui model alike. Record constructors check cell-node kinds, the one-content-per-column rule, heading rows and
the exclusive empty states; `ViewSpec` checks cell-node columns, walks cell nodes and empty states as view nodes, and
caps each tree item's values at the columns after the title.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java` (ItemList `:460-524`, Table `:591-613`, Tree `:615-623`) and the same lines of `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java`
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TableRow.java`, `jasper-ui/src/main/java/dev/jasper/ui/model/TableRow.java` (whole files)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java:21-26,54-63` and the same lines of `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java` (five tests appended), `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java` (one test appended)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces, in `dev.jasper.sdk.ui` and `dev.jasper.ui.model` alike:
  `record TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip, Map<String, Nodes.Node> cellNodes, Optional<TableRow.Heading> heading)`;
  `record TableRow.Heading(String title, OptionalInt count, String countNoun)`;
  `static TableRow TableRow.heading(String id, String title, OptionalInt count, String countNoun)`;
  `Nodes.Table(..., ScrollPolicy scroll, Optional<Nodes.Empty> empty, String emptyText)`;
  `Nodes.Tree(..., String activateAction, List<Column> columns)`;
  `Nodes.ItemList(..., String cancelAction, String emptyText)`. Every former canonical constructor remains.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 1 step 1: the SDK and model tests of the table vocabulary."""
import pathlib
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the class's closing brace at the end")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
before_last_brace("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", '''    private static TableRow cellRow(Node node) {
        return new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("col", node), Optional.empty());
    }
    private static Button emptyAction(String id) {
        return new Button(id, ControlState.READY, "New Tunnel…", "", ButtonKind.SECONDARY, id, "");
    }
    /** Plan 3c (spec 8.2): the new components default to nothing through every existing constructor. */
    @Test void zeroEighteenTableFieldsDefaultToNothing() {
        var row = new TableRow("r", Map.of("name", "a"), Optional.empty());
        assertThat(row.cellNodes()).isEmpty();
        assertThat(row.heading()).isEmpty();
        assertThat(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "tip").cellNodes()).isEmpty();
        var table = new Table("t", ControlState.READY, List.of(), List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD);
        assertThat(table.empty()).isEmpty();
        assertThat(table.emptyText()).isEmpty();
        assertThat(new Tree("tree", ControlState.READY, List.of(), Set.of(), Set.of(), "").columns()).isEmpty();
        var list = new ItemList("l", ControlState.READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "");
        assertThat(list.emptyText()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> new ItemList("l", ControlState.READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "", "", "", "", null));
    }
    /** Plan 3c (spec 8.2): a cell node is presentation or a DROPDOWN, in a column of its own, and a view node. */
    @Test void cellNodesArePresentationOrADropdownInAColumnOfTheirOwn() {
        var dot = new StatusDot("d", ControlState.READY, StatusKind.SUCCESS);
        var options = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));
        List<Node> accepted = List.of(text("t"), dot, new Icon("i", ControlState.READY, "server", Hue.NONE, IconSize.CAPTION, "Server"),
            new Chip("c", ControlState.READY, "HTTP", Hue.BLUE), new Nodes.Stack("s", ControlState.READY, LayoutRole.INLINE, List.of(dot, text("word"))),
            new Choice("choice", ControlState.READY, ChoiceKind.DROPDOWN, options, "card", "Display", ""));
        for (var node : accepted) assertThat(cellRow(node).cellNodes()).containsEntry("col", node);
        List<Node> refused = List.of(new Button("b", ControlState.READY, "Go", "", ButtonKind.SECONDARY, "go", ""),
            new Nodes.Stack("v", ControlState.READY, LayoutRole.VERTICAL, List.of(text("x"))),
            new Nodes.Stack("deep", ControlState.READY, LayoutRole.INLINE, List.of(new Nodes.Stack("inner", ControlState.READY, LayoutRole.INLINE, List.of(text("y"))))),
            new Nodes.Stack("act", ControlState.READY, LayoutRole.INLINE, List.of(new Button("b2", ControlState.READY, "Go", "", ButtonKind.SECONDARY, "go", ""))),
            new Choice("picker", ControlState.READY, ChoiceKind.PICKER, List.of(), "", "Pick", "pick"),
            new Toggle("on", ControlState.READY, "On", true, "on", true));
        for (var node : refused) assertThatIllegalArgumentException().as(node.id()).isThrownBy(() -> cellRow(node));
        assertThatIllegalArgumentException().as("one column, two contents").isThrownBy(() ->
            new TableRow("r", Map.of("col", "text"), Optional.empty(), false, Optional.empty(), "", Map.of("col", text("t")), Optional.empty()));
        var columns = List.of(new Column("col", "Column", TextRole.BODY, false));
        assertThatIllegalArgumentException().as("unknown column").isThrownBy(() -> new ViewSpec(new Table("t", ControlState.READY, columns,
            List.of(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("other", text("t")), Optional.empty())), Set.of(), "", List.of())));
        assertThatIllegalArgumentException().as("cell node ids are view ids").isThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY,
            LayoutRole.VERTICAL, List.of(text("same"), new Table("t", ControlState.READY, columns, List.of(cellRow(text("same"))), Set.of(), "", List.of())))));
        assertThatCode(() -> new ViewSpec(new Table("t", ControlState.READY, columns, List.of(cellRow(text("cell"))), Set.of(), "", List.of())))
            .doesNotThrowAnyException();
        var many = new LinkedHashMap<String, Node>();
        for (int i = 11; i >= 0; i--) many.put("c" + (char) ('a' + i), text("t" + i));
        var row = new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", many, Optional.empty());
        assertThat(row.cellNodes().keySet()).as("column-id order")
            .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(0, 11).mapToObj(i -> "c" + (char) ('a' + i)).toList());
        assertThat(row.cellNodes()).isEqualTo(Map.copyOf(many));
        assertThatThrownBy(() -> row.cellNodes().put("x", text("x"))).isInstanceOf(UnsupportedOperationException.class);
    }
    /** Plan 3c (spec 8.2): a heading row is a title and a count, and a table never selects it. */
    @Test void headingRowsCarryATitleAndACountAndAreNeverSelected() {
        var heading = TableRow.heading("jasper", "Jasper", OptionalInt.of(6), "group");
        assertThat(heading.heading()).hasValue(new TableRow.Heading("Jasper", OptionalInt.of(6), "group"));
        assertThat(heading.cells()).isEmpty();
        assertThat(heading.cellNodes()).isEmpty();
        assertThat(TableRow.heading("bare", "Bare", OptionalInt.of(4), "").heading().orElseThrow().countNoun()).isEmpty();
        assertThat(TableRow.heading("none", "None", OptionalInt.empty(), "").heading().orElseThrow().count()).isEmpty();
        assertThat(TableRow.heading("two", "Two", OptionalInt.of(2), "file server").heading().orElseThrow().countNoun()).isEqualTo("file server");
        assertThatIllegalArgumentException().as("blank title").isThrownBy(() -> TableRow.heading("h", " ", OptionalInt.empty(), ""));
        assertThatIllegalArgumentException().as("negative count").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(-1), ""));
        assertThatIllegalArgumentException().as("noun without count").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.empty(), "group"));
        assertThatIllegalArgumentException().as("capitalised noun").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(2), "Group"));
        assertThatIllegalArgumentException().as("noun with spaces around it").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(2), " group"));
        var title = Optional.of(new TableRow.Heading("H", OptionalInt.empty(), ""));
        assertThatIllegalArgumentException().as("cells").isThrownBy(() -> new TableRow("h", Map.of("c", "x"), Optional.empty(), false, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("cell nodes").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("c", text("x")), title));
        assertThatIllegalArgumentException().as("icon").isThrownBy(() -> new TableRow("h", Map.of(), Optional.of("file"), false, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("muted").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), true, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("menu").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.of(List.of()), "", Map.of(), title));
        assertThatIllegalArgumentException().as("tooltip").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.empty(), "Tip", Map.of(), title));
        var columns = List.of(new Column("c", "C", TextRole.BODY, false));
        assertThatIllegalArgumentException().as("selected heading").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(heading), Set.of("jasper"), "", List.of()));
        assertThatCode(() -> new ViewSpec(new Table("t", ControlState.READY, columns, List.of(heading, new TableRow("r", Map.of("c", "x"), Optional.empty())),
            Set.of("r"), "", List.of()))).doesNotThrowAnyException();
    }
    /** Plan 3c (spec 8.2): an empty table shows a state with at most one action or a line, not both. */
    @Test void emptyStatesAreExclusiveAndATablesStateHasOneAction() {
        var columns = List.of(new Column("c", "C", TextRole.BODY, false));
        var icon = new Icon("e.icon", ControlState.READY, "server", Hue.NEUTRAL, IconSize.EMPTY, "Servers");
        var one = new Empty("e", ControlState.READY, icon, "No Tunnels", "", List.of(emptyAction("new")));
        var table = new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(one), "");
        assertThat(table.empty()).hasValue(one);
        assertThat(new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.empty(), "Not Connected").emptyText()).isEqualTo("Not Connected");
        assertThatIllegalArgumentException().as("state and line").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(one), "Not Connected"));
        assertThatCode(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.of(one), " ")).as("a blank line is no line").doesNotThrowAnyException();
        var two = new Empty("e", ControlState.READY, icon, "No Tunnels", "", List.of(emptyAction("a"), emptyAction("b")));
        assertThatIllegalArgumentException().as("two actions").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(two), ""));
        assertThatIllegalArgumentException().as("the state's ids are view ids").isThrownBy(() ->
            new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(text("e"), table))));
        assertThatCode(() -> new ViewSpec(table)).doesNotThrowAnyException();
        var list = new ItemList("l", ControlState.READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "", "No keys");
        assertThat(new ViewSpec(list).root()).isEqualTo(list);
    }
    /** Plan 3c (spec 8.2): tree columns title the items and their trailing values. */
    @Test void treeColumnsTitleTheItemsAndTheirTrailingValues() {
        var columns = List.of(new Column("name", "Name", TextRole.BODY, false), new Column("host", "Host", TextRole.MONO_SMALL, false),
            new Column("port", "Port", TextRole.MONO_SMALL, true));
        var mac = new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("192.168.1.119", "22"), Optional.empty());
        var tree = new Tree("sessions", ControlState.READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(mac))),
            Set.of("lan"), Set.of("mac"), "connect", columns);
        assertThat(new ViewSpec(tree).root()).isEqualTo(tree);
        var extra = new TreeItem("extra", "Extra", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("a", "b", "c"), Optional.empty());
        assertThatIllegalArgumentException().as("a nested item with three values under two value columns").isThrownBy(() -> new ViewSpec(new Tree("t",
            ControlState.READY, List.of(new TreeItem("b", "B", Optional.empty(), false, List.of(extra))), Set.of(), Set.of(), "", columns)));
        assertThatCode(() -> new ViewSpec(new Tree("t", ControlState.READY, List.of(extra), Set.of(), Set.of(), "")))
            .as("without columns, values are free").doesNotThrowAnyException();
        assertThatCode(() -> new ViewSpec(new Tree("t", ControlState.READY, List.of(new TreeItem("few", "Few", Optional.empty(), true, List.of())),
            Set.of(), Set.of(), "", columns))).as("fewer values than columns").doesNotThrowAnyException();
        assertThatIllegalArgumentException().as("duplicate column").isThrownBy(() -> new Tree("t", ControlState.READY, List.of(), Set.of(), Set.of(), "",
            List.of(columns.get(0), columns.get(0))));
    }
''')
before_last_brace("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", '''    /** Plan 3c: the model mirrors SDK 0.18's table vocabulary and its validation. */
    @Test void tableVocabularyOfSdkZeroEighteenIsMirrored() {
        var columns = List.of(new Column("c", "C", TextRole.BODY, false), new Column("d", "D", TextRole.BODY, false));
        var dropdown = new Choice("pick", ControlState.READY, ChoiceKind.DROPDOWN, List.of(new Option("a", "A", Optional.empty())), "a", "Pick", "");
        var row = new TableRow("r", Map.of("c", "x"), Optional.empty(), false, Optional.empty(), "", Map.of("d", dropdown), Optional.empty());
        var heading = TableRow.heading("h", "Group", OptionalInt.of(3), "group");
        var table = new Table("t", ControlState.READY, columns, List.of(heading, row), Set.of("r"), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.empty(), "");
        assertThat(new ViewSpec(table).root()).isEqualTo(table);
        assertThatIllegalArgumentException().isThrownBy(() -> new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "",
            Map.of("d", new Choice("p", ControlState.READY, ChoiceKind.PICKER, List.of(), "", "Pick", "pick")), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(heading), Set.of("h"), "", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE,
            ScrollPolicy.STANDARD, Optional.of(new Empty("e", ControlState.READY, text("i"), "None", "", List.of())), "Nothing"));
        var three = new TreeItem("x", "X", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("1", "2", "3"), Optional.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Tree("tree", ControlState.READY, List.of(three), Set.of(), Set.of(), "", columns)));
        assertThat(new ItemList("l", ControlState.READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of()).emptyText()).isEmpty();
    }
''')
PY
```

The SDK `KitValuesTest` imports `java.util.*` and `static dev.jasper.sdk.ui.Nodes.*` (`KitValuesTest.java:3-6`), and the
model `ViewSpecTest` the same for its package (`ViewSpecTest.java:3-6`), so `LinkedHashMap`, `Option`, `Column`,
`TableRow` and `TreeItem` resolve without new imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --continue`
Expected: FAIL. Both `compileTestJava` tasks stop with "cannot find symbol" (`cellNodes()`, `heading(...)`,
`TableRow.Heading`, `empty()`, `emptyText()`, `columns()`) and "no suitable constructor found for TableRow(...)".

- [ ] **Step 3: Add the components and their checks to both copies**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 1 step 3: Table, Tree, ItemList, TableRow and ViewSpec gain SDK 0.18's table vocabulary, in both copies."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
TABLE_OLD = '''    /** Column collection with explicit single or multiple (Shift, Cmd/Ctrl or Space) selection and an explicit scroll policy. */
    public record Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll) implements Node {
        public Table {
            ModelChecks.id(id); Objects.requireNonNull(state);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(scroll);
            if (selectionMode == SelectionMode.NONE && !selectedIds.isEmpty()) throw new IllegalArgumentException("Passive rows cannot be selected");
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            ModelChecks.unique(columns, Column::id);
            ModelChecks.unique(rows, TableRow::id);
            ModelChecks.unique(contextActions, MenuItem::id);
        }
'''
TABLE_NEW = '''    /**
     * Column collection with explicit single or multiple (Shift, Cmd/Ctrl or Space) selection and an explicit scroll policy.
     * A heading row ({@link TableRow#heading()}) groups the rows below it and is never selected. With no rows the body
     * shows {@code empty} (a state with at most one action) or the muted line {@code emptyText}, below the column
     * headers, which stay; the two are exclusive, and a blank {@code emptyText} shows nothing.
     *
     * @since 0.18.0 for {@code empty} and {@code emptyText}
     */
    public record Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll,
                        Optional<Empty> empty, String emptyText) implements Node {
        public Table {
            ModelChecks.id(id); Objects.requireNonNull(state);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(scroll);
            if (selectionMode == SelectionMode.NONE && !selectedIds.isEmpty()) throw new IllegalArgumentException("Passive rows cannot be selected");
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            ModelChecks.unique(columns, Column::id);
            ModelChecks.unique(rows, TableRow::id);
            ModelChecks.unique(contextActions, MenuItem::id);
            Objects.requireNonNull(empty); Objects.requireNonNull(emptyText);
            if (empty.isPresent() && !emptyText.isBlank()) throw new IllegalArgumentException("A table shows an empty state or an empty line, not both");
            if (empty.isPresent() && empty.get().actions().size() > 1) throw new IllegalArgumentException("A table's empty state has at most one action");
            for (var row : rows) if (row.heading().isPresent() && selectedIds.contains(row.id())) throw new IllegalArgumentException("A heading row is never selected");
        }
        /** A table whose body shows nothing while it has no rows. */
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, scroll, Optional.empty(), "");
        }
'''
TREE_OLD = '''    public record Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction) implements Node {
        public Tree {
            ModelChecks.id(id); Objects.requireNonNull(state);
            roots = List.copyOf(roots);
            expandedIds = Set.copyOf(expandedIds);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
        }
    }
'''
TREE_NEW = '''    /**
     * A tree of items. A non-empty {@code columns} shows a header over the rows: the first column titles the items, and
     * each later column titles one of the items' {@link TreeItem#trailing()} values, in order, so an item has at most
     * one value per column after the first. Clicking a column header reports {@link UiEvent.Sorted}, as a
     * {@link Table}'s does; the owner orders the items. Empty {@code columns} shows no header.
     *
     * @since 0.18.0 for {@code columns}
     */
    public record Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction, List<Column> columns) implements Node {
        public Tree {
            ModelChecks.id(id); Objects.requireNonNull(state);
            roots = List.copyOf(roots);
            expandedIds = Set.copyOf(expandedIds);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            columns = List.copyOf(columns);
            ModelChecks.unique(columns, Column::id);
        }
        /** A tree without column headers. */
        public Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction) {
            this(id, state, roots, expandedIds, selectedIds, activateAction, List.of());
        }
    }
'''
LIST_EDITS = [
    ('''     * Escape only while the row canvas has focus; blank actions pass the key to an enclosing control.
     *
     * @param id stable list id''', '''     * Escape only while the row canvas has focus; blank actions pass the key to an enclosing control. With no items the
     * body shows {@code emptyText} as one muted line, above {@code addAction}; blank shows nothing (since 0.18.0).
     *
     * @param id stable list id''', 1),
    ('''     * @param cancelAction focused-row Escape action
     */''', '''     * @param cancelAction focused-row Escape action
     * @param emptyText the line the body shows while there are no items; blank for none
     */''', 1),
    ('''String detailsAction, String copyAction, String cancelAction) implements Node {
        public ItemList {''', '''String detailsAction, String copyAction, String cancelAction, String emptyText) implements Node {
        public ItemList {''', 1),
    ('''            Objects.requireNonNull(detailsAction); Objects.requireNonNull(copyAction); Objects.requireNonNull(cancelAction);
''', '''            Objects.requireNonNull(detailsAction); Objects.requireNonNull(copyAction); Objects.requireNonNull(cancelAction);
            Objects.requireNonNull(emptyText);
''', 1),
    ('''        /** A list without D, Copy or Escape row actions. */''', '''        /** A list that shows nothing while it has no items. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction, String detailsAction, String copyAction, String cancelAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, enterAction, deleteAction, detailsAction, copyAction, cancelAction, "");
        }
        /** A list without D, Copy or Escape row actions. */''', 1),
]
VIEWSPEC_EDITS = [
    ('''    private static void tree(TreeItem item, Set<String> ids, Set<String> branches, int depth) {
        if (depth > 128) throw new IllegalArgumentException("Tree too deep");
        if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate tree item: " + item.id());
        if (!item.leaf()) branches.add(item.id());
        for (var child : item.children()) tree(child, ids, branches, depth + 1);
    }''', '''    private static void tree(TreeItem item, Set<String> ids, Set<String> branches, int depth, int values) {
        if (depth > 128) throw new IllegalArgumentException("Tree too deep");
        if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate tree item: " + item.id());
        if (item.trailing().size() > values) throw new IllegalArgumentException("More trailing values than columns after the title: " + item.id());
        if (!item.leaf()) branches.add(item.id());
        for (var child : item.children()) tree(child, ids, branches, depth + 1, values);
    }''', 1),
    ('''            case Table n -> {
                var columns = new HashSet<>(n.columns().stream().map(Column::id).toList());
                for (var row : n.rows()) references(row.cells().keySet(), columns);
                references(n.selectedIds(), new HashSet<>(n.rows().stream().map(TableRow::id).toList()));
            }''', '''            case Table n -> {
                var columns = new HashSet<>(n.columns().stream().map(Column::id).toList());
                for (var row : n.rows()) {
                    references(row.cells().keySet(), columns);
                    references(row.cellNodes().keySet(), columns);
                    children.addAll(row.cellNodes().values());
                }
                references(n.selectedIds(), new HashSet<>(n.rows().stream().map(TableRow::id).toList()));
                n.empty().ifPresent(children::add);
            }''', 1),
    ('''                for (var item : n.roots()) tree(item, itemIds, branches, 0);''',
     '''                int values = n.columns().isEmpty() ? Integer.MAX_VALUE : n.columns().size() - 1;
                for (var item : n.roots()) tree(item, itemIds, branches, 0, values);''', 1),
]
TABLE_ROW = '''package PACKAGE;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Immutable tablerow descriptor. A {@code muted} row shows every cell in the de-emphasized text color (for example
 * entries that cannot be chosen); a present {@code contextActions} replaces the table's context menu for this row, and
 * an empty list means the row has none. {@code tooltip} is the row's literal tooltip and accessible description, shown
 * over every cell; blank shows none.
 * <p>
 * A column's content is a plain cell ({@code cells}) or a cell node ({@code cellNodes}), never both. A cell node is a
 * {@link Nodes.Text}, {@link Nodes.StatusDot}, {@link Nodes.Icon} or {@link Nodes.Chip}, an INLINE {@link Nodes.Stack}
 * of those, or a DROPDOWN {@link Nodes.Choice}, which reports {@link UiEvent.Selected} and is drawn without a border
 * (its value, then its chevron) until it is hovered or focused. A muted row dims its plain cells and its Text cell
 * nodes. Cell nodes are view nodes, so their ids are unique in the view; {@code cellNodes} iterates in column-id order.
 * <p>
 * A heading row ({@link #heading(String, String, OptionalInt, String)}) has a {@link Heading} and nothing else: it
 * spans the row, groups the rows below it, and is never selected or activated.
 *
 * @since 0.18.0 for {@code cellNodes} and {@code heading}
 */
public record TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip,
                       Map<String, Nodes.Node> cellNodes, Optional<TableRow.Heading> heading) {
    /**
     * A heading row's title and an optional count, shown muted at the row's end and followed by {@code countNoun}: a
     * lowercase singular noun to which the presentation adds "s" unless the count is 1 ("6 groups", "1 group"). A blank
     * noun shows the bare number; a noun needs a count.
     */
    public record Heading(String title, OptionalInt count, String countNoun) {
        public Heading {
            ModelChecks.label(title, "title");
            Objects.requireNonNull(count); Objects.requireNonNull(countNoun);
            if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
            if (!countNoun.isBlank() && count.isEmpty()) throw new IllegalArgumentException("A count noun needs a count");
            if (!countNoun.equals(countNoun.strip().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A count noun is lowercase, without spaces around it");
        }
    }
    public TableRow {
        ModelChecks.id(id);
        cells = Map.copyOf(cells); cells.keySet().forEach(ModelChecks::id); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon"));
        Objects.requireNonNull(contextActions); contextActions = contextActions.map(List::copyOf); contextActions.ifPresent(items -> ModelChecks.unique(items, MenuItem::id));
        Objects.requireNonNull(tooltip);
        Objects.requireNonNull(cellNodes); Objects.requireNonNull(heading);
        var nodes = new TreeMap<String, Nodes.Node>();
        for (var entry : cellNodes.entrySet()) {
            ModelChecks.id(entry.getKey());
            cell(Objects.requireNonNull(entry.getValue(), "cell node"));
            if (cells.containsKey(entry.getKey())) throw new IllegalArgumentException("A column is a plain cell or a cell node, not both: " + entry.getKey());
            nodes.put(entry.getKey(), entry.getValue());
        }
        cellNodes = Collections.unmodifiableMap(nodes);
        if (heading.isPresent() && (!cells.isEmpty() || !cellNodes.isEmpty() || icon.isPresent() || muted || contextActions.isPresent() || !tooltip.isBlank()))
            throw new IllegalArgumentException("A heading row has a title and a count only");
    }
    /** A row without cell nodes that is not a heading. */
    public TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip) {
        this(id, cells, icon, muted, contextActions, tooltip, Map.of(), Optional.empty());
    }
    /** A row without a tooltip. */
    public TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions) {
        this(id, cells, icon, muted, contextActions, "");
    }
    public TableRow(String id, Map<String,String> cells, Optional<String> icon) {
        this(id, cells, icon, false, Optional.empty());
    }
    /**
     * A heading row: {@code title} over the rows below it, with an optional count and its noun.
     *
     * @param id stable row id
     * @param title the group's title
     * @param count how many the group holds; absent shows no count
     * @param countNoun the lowercase singular noun after the count; blank for the bare number
     * @return the heading row
     */
    public static TableRow heading(String id, String title, OptionalInt count, String countNoun) {
        return new TableRow(id, Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of(), Optional.of(new Heading(title, count, countNoun)));
    }
    /** Cell content is read-only presentation, or a DROPDOWN whose choice the owner applies. */
    private static void cell(Nodes.Node node) {
        switch (node) {
            case Nodes.Text _, Nodes.StatusDot _, Nodes.Icon _, Nodes.Chip _ -> { }
            case Nodes.Choice choice when choice.kind() == Nodes.ChoiceKind.DROPDOWN -> { }
            case Nodes.Stack stack when stack.role() == Nodes.LayoutRole.INLINE -> {
                for (var child : stack.children()) switch (child) {
                    case Nodes.Text _, Nodes.StatusDot _, Nodes.Icon _, Nodes.Chip _ -> { }
                    default -> throw new IllegalArgumentException("An inline cell stack holds text, status dots, icons and chips only");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported table cell node: " + node.getClass().getSimpleName());
        }
    }
}
'''
for package, root in [("dev.jasper.sdk.ui", "jasper-sdk/src/main/java/dev/jasper/sdk/ui"), ("dev.jasper.ui.model", "jasper-ui/src/main/java/dev/jasper/ui/model")]:
    edit(f"{root}/Nodes.java", [(TABLE_OLD, TABLE_NEW, 1), (TREE_OLD, TREE_NEW, 1)] + LIST_EDITS)
    edit(f"{root}/ViewSpec.java", VIEWSPEC_EDITS)
    pathlib.Path(f"{root}/TableRow.java").write_text(TABLE_ROW.replace("PACKAGE", package), encoding="utf-8")
PY
```

Then confirm the mirrors are still identical but for their packages:

```bash
python3 -I - <<'PY'
import pathlib
for name in ["Nodes", "TableRow", "ViewSpec"]:
    sdk = pathlib.Path(f"jasper-sdk/src/main/java/dev/jasper/sdk/ui/{name}.java").read_text(encoding="utf-8").replace("dev.jasper.sdk.ui", "P")
    ui = pathlib.Path(f"jasper-ui/src/main/java/dev/jasper/ui/model/{name}.java").read_text(encoding="utf-8").replace("dev.jasper.ui.model", "P")
    print(name, "identical" if sdk == ui else "DIFFERENT")
PY
```

Expected: `Nodes identical`, `TableRow identical`, `ViewSpec identical`. (They are identical at `222969926`, and 3a and
3b edit both copies alike; if one already differed at the rebased base, the difference must be the base's alone.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest :jasper-ui:javadoc :jasper-app:compileJava :jasper-sdk-testkit:compileJava --continue`
Expected: BUILD SUCCESSFUL. The five new SDK tests and `ViewSpecTest.tableVocabularyOfSdkZeroEighteenIsMirrored` pass;
both javadoc tasks are clean; the app and the testkit still compile against the conveniences.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src/main/java/dev/jasper/ui/model jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: the vocabulary and its model mirror

TableRow gains cellNodes (text, dots, icons, chips, an inline stack of
those, or a DROPDOWN, in a column of their own) and heading rows with a
counted noun; Table gains an empty state or line, Tree gains columns
that title its trailing values, and ItemList gains an empty line. The
records and ViewSpec check them, the jasper-ui model mirrors them, and
every existing constructor stays with the new components empty.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The projection and both runtimes' descriptor walks

`KitValues` projects the new components (the cell nodes through the ordinary node projection), and the fake's and the
host's descriptor walks visit a table's cell nodes and its empty state, so their ids are indexed and their icons are
validated alike. The shared icon contract gains the two new positions.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:27,32,33,43`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java:30`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java:107`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java` (one test appended), `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java:161` (two icon cases)

**Interfaces:**
- Consumes: Task 1's records and accessors.
- Produces: `KitValues.cellNodes(Map<String, sdk Nodes.Node>)` and `KitValues.toModule(sdk TableRow.Heading)`
  (package-private); `FakeKitView.children(Table)` and `KitContent.children(Table)` return the rows' cell nodes, then
  the empty state. Task 6's contract test relies on both walks.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 2 step 1: the lossless projection test and the two icon positions of the kit contract."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the class's closing brace at the end")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
before_last_brace("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", ''' /** Plan 3c (spec 8.2): tree columns, cell nodes, heading rows and empty states cross the projection unchanged. */
 @Test void zeroEighteenTableFieldsProjectWithoutLoss(){
  var columns=List.of(new Column("name","Name",TextRole.BODY,false),new Column("host","Host",TextRole.MONO_SMALL,false),new Column("port","Port",TextRole.MONO_SMALL,true));
  var mac=new TreeItem("mac","Mac",Optional.empty(),true,List.of(),OptionalInt.empty(),false,"",Optional.of(StatusKind.SUCCESS),List.of("192.168.1.119","22"),Optional.empty());
  var tree=new Tree("sessions",READY,List.of(new TreeItem("lan","Local Network",Optional.empty(),false,List.of(mac))),Set.of("lan"),Set.of("mac"),"connect",columns);
  var cells=new LinkedHashMap<String,Node>();
  for(int i=11;i>=0;i--)cells.put("c"+i,text("cell"+i));
  cells.put("display",new Choice("display",READY,ChoiceKind.DROPDOWN,List.of(new Option("card","Card",Optional.empty()),new Option("sticky","Sticky card",Optional.empty())),"card","Display",""));
  cells.put("type",new Nodes.Stack("type",READY,LayoutRole.INLINE,List.of(new StatusDot("dot",READY,StatusKind.SUCCESS),new Chip("chip",READY,"HTTP",Hue.BLUE),
   new Icon("glyph",READY,"server",Hue.NONE,IconSize.CAPTION,"Server"))));
  var tableColumns=new ArrayList<Column>();for(var id:cells.keySet())tableColumns.add(new Column(id,id,TextRole.BODY,false));
  var groups=new Table("groups",READY,tableColumns,List.of(TableRow.heading("jasper","Jasper",OptionalInt.of(6),"group"),
   new TableRow("errors",Map.of(),Optional.empty(),false,Optional.empty(),"",cells,Optional.empty())),Set.of("errors"),"",List.of(),SelectionMode.SINGLE,ScrollPolicy.STANDARD,Optional.empty(),"");
  var tunnels=new Table("tunnels",READY,List.of(new Column("kind","Type",TextRole.BODY,false)),List.of(),Set.of(),"",List.of(),SelectionMode.SINGLE,ScrollPolicy.STANDARD,
   Optional.of(new Empty("tunnels.empty",READY,new Icon("tunnels.icon",READY,"tunnel",Hue.NEUTRAL,IconSize.EMPTY,"Tunnels"),"No Tunnels","",List.of(button("tunnels.new")))),"");
  var files=new Table("files",READY,List.of(new Column("name","Name",TextRole.BODY,false)),List.of(),Set.of(),"",List.of(),SelectionMode.SINGLE,ScrollPolicy.STANDARD,Optional.empty(),"Not Connected");
  var keys=new ItemList("keys",READY,ListKind.ORDERED,List.of(),Set.of(),"",List.of(),SelectionMode.SINGLE,Optional.empty(),OptionalInt.empty(),"","","","","","No keys");
  var spec=new ViewSpec(new Nodes.Stack("tables",READY,LayoutRole.VERTICAL,List.of(tree,groups,tunnels,files,keys)));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  var module=(dev.jasper.ui.model.Nodes.Stack)KitValues.toModule(spec).root();
  assertThat(((dev.jasper.ui.model.Nodes.Tree)module.children().get(0)).columns()).extracting(dev.jasper.ui.model.Column::id).containsExactly("name","host","port");
  var rows=((dev.jasper.ui.model.Nodes.Table)module.children().get(1)).rows();
  assertThat(rows.get(0).heading()).hasValue(new dev.jasper.ui.model.TableRow.Heading("Jasper",OptionalInt.of(6),"group"));
  assertThat(rows.get(1).cellNodes().keySet()).containsExactlyElementsOf(groups.rows().get(1).cellNodes().keySet());
  assertThat(rows.get(1).cellNodes().get("display")).isInstanceOf(dev.jasper.ui.model.Nodes.Choice.class);
  assertThat(((dev.jasper.ui.model.Nodes.Table)module.children().get(2)).empty()).hasValueSatisfying(state -> assertThat(state.actions()).hasSize(1));
  assertThat(((dev.jasper.ui.model.Nodes.Table)module.children().get(3)).emptyText()).isEqualTo("Not Connected");
  assertThat(((dev.jasper.ui.model.Nodes.ItemList)module.children().get(4)).emptyText()).isEqualTo("No keys");
 }
''')
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java", [
    ('''        cases.put("table-menu",name -> descriptorTable(Optional.empty(),Optional.empty(),List.of(descriptorMenu(name))));
''', '''        cases.put("table-menu",name -> descriptorTable(Optional.empty(),Optional.empty(),List.of(descriptorMenu(name))));
        // SDK 0.18 (plan 3c): a cell node and an empty table's state are descriptor positions too.
        cases.put("table-cell-node",name -> new Nodes.Table("table",ControlState.READY,List.of(new Column("col","Column",Nodes.TextRole.BODY,false)),
            List.of(new TableRow("row",Map.of(),Optional.empty(),false,Optional.empty(),"",Map.of("col",descriptorIcon(name)),Optional.empty())),Set.of(),"",List.of()));
        cases.put("table-empty",name -> new Nodes.Table("table",ControlState.READY,List.of(new Column("col","Column",Nodes.TextRole.BODY,false)),List.of(),Set.of(),"",List.of(),
            Nodes.SelectionMode.SINGLE,Nodes.ScrollPolicy.STANDARD,Optional.of(new Nodes.Empty("empty",ControlState.READY,descriptorIcon(name),"Nothing","",List.of())),""));
''', 1)])
PY
```

The jasper-app `KitValuesTest` imports `dev.jasper.sdk.ui.*`, `java.util.*` and `static dev.jasper.sdk.ui.Nodes.*`
and defines `READY`, `text(id)` and `button(id)` (`KitValuesTest.java:1-11`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeKitTest*' --continue`
Expected: FAIL. `KitValuesTest.zeroEighteenTableFieldsProjectWithoutLoss` fails on the `toString` comparison (the
projection drops the new components through the conveniences); in both `HostedKitContractTest` and the fake's
contract, `everyDescriptorIconPositionValidatesOwnershipBeforeCreateOrUpdateMutation` fails with "table-cell-node
create missing" (an unknown icon in a cell node is accepted, because neither walk visits it).

- [ ] **Step 3: Project and walk the new components**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 2 step 3: KitValues projects the table vocabulary; both descriptor walks visit cell nodes and the empty state."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    ("n.detailsAction(), n.copyAction(), n.cancelAction());", "n.detailsAction(), n.copyAction(), n.cancelAction(), n.emptyText());", 1),
    ("dev.jasper.ui.model.Nodes.SelectionMode.valueOf(n.selectionMode().name()), dev.jasper.ui.model.Nodes.ScrollPolicy.valueOf(n.scroll().name()));",
     "dev.jasper.ui.model.Nodes.SelectionMode.valueOf(n.selectionMode().name()), dev.jasper.ui.model.Nodes.ScrollPolicy.valueOf(n.scroll().name()), "
     "n.empty().map(e -> (dev.jasper.ui.model.Nodes.Empty) toModule(e)), n.emptyText());", 1),
    ("n.roots().stream().map(KitValues::toModule).toList(), n.expandedIds(), n.selectedIds(), n.activateAction());",
     "n.roots().stream().map(KitValues::toModule).toList(), n.expandedIds(), n.selectedIds(), n.activateAction(), n.columns().stream().map(KitValues::toModule).toList());", 1),
    (" static dev.jasper.ui.model.TableRow toModule(dev.jasper.sdk.ui.TableRow n) { return new dev.jasper.ui.model.TableRow(n.id(), n.cells(), n.icon(), n.muted(), n.contextActions().map(items -> items.stream().map(KitValues::toModule).toList()), n.tooltip()); }\n",
     " static dev.jasper.ui.model.TableRow toModule(dev.jasper.sdk.ui.TableRow n) { return new dev.jasper.ui.model.TableRow(n.id(), n.cells(), n.icon(), n.muted(), n.contextActions().map(items -> items.stream().map(KitValues::toModule).toList()), n.tooltip(), cellNodes(n.cellNodes()), n.heading().map(KitValues::toModule)); }\n"
     " /** Each column's cell node, projected; the model row keeps them in column-id order, as the SDK row does. */\n"
     " static java.util.Map<String, dev.jasper.ui.model.Nodes.Node> cellNodes(java.util.Map<String, dev.jasper.sdk.ui.Nodes.Node> nodes) { var result = new java.util.LinkedHashMap<String, dev.jasper.ui.model.Nodes.Node>(); nodes.forEach((column, node) -> result.put(column, toModule(node))); return result; }\n"
     " static dev.jasper.ui.model.TableRow.Heading toModule(dev.jasper.sdk.ui.TableRow.Heading n) { return new dev.jasper.ui.model.TableRow.Heading(n.title(), n.count(), n.countNoun()); }\n", 1)])
ITEM_LIST = "            case Nodes.ItemList n -> { n.addAction().ifPresent(result::add); for(var item:n.items()) { item.leading().ifPresent(result::add);result.addAll(item.title());result.addAll(item.subtitle());result.addAll(item.trailing()); } }\n"
TABLE = ITEM_LIST + "            case Nodes.Table n -> { for(var row:n.rows()) result.addAll(row.cellNodes().values()); n.empty().ifPresent(result::add); }\n"
for name in ["jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java", "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java"]:
    edit(name, [(ITEM_LIST, TABLE, 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest --tests dev.jasper.app.plugins.KitMountingTest :jasper-sdk-testkit:test --continue`
Expected: BUILD SUCCESSFUL. `KitValuesTest` (the new test included), both contract runs (the two new icon cases included) and the
testkit's own tests pass. The host mounts the cell-node case already: its renderer still ignores cell nodes until
Task 3, and it mounts no empty state until Task 4.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: lossless projection and both descriptor walks

KitValues carries tree columns, cell nodes, heading rows and the empty
states into the jasper-ui model, and the fake's and the host's
descriptor walks visit a table's cell nodes and empty state, so ids and
icons there are checked alike; the kit contract's icon table gains both
positions.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: Cell nodes, the bare cell dropdown and heading rows in the default look

`Tables` mounts each column's cell node in its weighted column (a plain cell keeps today's synthetic text exactly), lets
`KitView.focus` and `reveal` reach cell nodes, reads a Text cell node for type-ahead, and draws a heading row as a
non-data row with its strong title and its muted count at the row's end. A DROPDOWN in a cell is bare: its value and
chevron, as wide as the value, with the control face only while hovered, focused or open, and on a selected row its
bare value and chevron take the selection's ink. `ControlRegistry.validate` and `Trees.validateDetail` walk cell nodes
and empty states. A plain table, list and dropdown paint exactly as before.

**Files:**
- Replace: `jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java` (whole file)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java:30,72,280-285,322-323,352-385`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java:1175-1186` (`headingLayout`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java:20`, `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java:35-41`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java`

**Interfaces:**
- Consumes: Task 1's model records (`TableRow.cellNodes()`, `heading()`, `TableRow.Heading`).
- Produces: `Tables.headerLabel(ControlRegistry.Control owner, String prefix, Column column)` (a header title reporting
  `UiEvent.Sorted`, used by Task 5); `Tables.countText(TableRow.Heading)`; `Tables.TableControl.cellText(TableRow,
  String)`; `Choices.ChoiceControl.inCell(InteractiveRows.Ink ink)`; `Choices.ChoiceCombo.cell`, `cellInk`, `bare()`,
  `cellWidth()` and its `getForeground()` override; `Choices.CellLayout`. Task 4 adds its empty-state methods to the `TableControl` of this task's
  file, anchored on its text.

- [ ] **Step 1: Write the failing tests**

Before replacing `Tables.java`, check that nothing changed it since `222969926` (3a and 3b do not touch it):
`git diff --stat 222969926 -- jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java` prints nothing. If it prints a
change, carry that change into the replacement in Step 3.

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** SDK 0.18's table vocabulary in the default look (plan 3c): cell nodes, the cell dropdown, heading rows, empty states and tree columns. */
class TableVocabularyTest {
    static final ControlState READY = ControlState.READY;
    static final List<Option> DISPLAYS = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));

    static TableRow group(String id, String name, String display) {
        return new TableRow(id, Map.of("group", name, "source", "Default"), Optional.empty(), false, Optional.empty(), "",
            Map.of("display", new Choice(id + ".display", READY, ChoiceKind.DROPDOWN, DISPLAYS, display, "Display", "")), Optional.empty());
    }

    static Table groups(Set<String> selected) {
        return new Table("groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 3, Optional.empty()),
                new Column("display", "Display", TextRole.BODY, false, 1.5, Optional.empty()), new Column("source", "Source", TextRole.DESCRIPTION, false)),
            List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(2), "group"), group("errors", "Internal errors", "card"),
                group("config", "Configuration", "sticky"), TableRow.heading("editor", "Editor", OptionalInt.of(1), "group"), group("editor.main", "Editor", "card")),
            selected, "edit", List.of(), SelectionMode.SINGLE);
    }

    static Table tunnels() {
        return new Table("tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false)),
            List.of(new TableRow("web", Map.of("local", "8080"), Optional.empty(), false, Optional.empty(), "", Map.of("type",
                new Nodes.Stack("web.type", READY, LayoutRole.INLINE, List.of(new StatusDot("web.dot", READY, StatusKind.SUCCESS),
                    new Text("web.kind", READY, "Local", TextRole.BODY), new Chip("web.chip", READY, "SSH", Hue.GREEN)))), Optional.empty())),
            Set.of(), "toggle", List.of());
    }

    static Choices.ChoiceCombo combo(Tables.TableControl table, String row) {
        return (Choices.ChoiceCombo) ((Choices.ChoiceControl) table.mounted.get(row).controls.get(row + ".display")).input;
    }

    /** How many pixels a component paints on a transparent image. */
    static int painted(JComponent component) {
        var image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) != 0) count++;
        return count;
    }

    @Test void cellNodesFillTheirColumnsAndTheCellDropdownReportsItsChoice() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(groups(Set.of()), tunnels()))), events::add)) {
            view.setSize(640, 480); InteractiveRowsTest.layout(view);
            var tunnels = (Tables.TableControl) view.control("tunnels");
            var row = tunnels.mounted.get("web");
            var stack = row.controls.get("web.type");
            assertThat(row.title.getComponent(0)).as("the cell node is the first column's cell").isSameAs(stack.component);
            assertThat(((JLabel) row.title.getComponent(1)).getText()).isEqualTo("8080");
            assertThat(row.cells).containsExactly(stack.component, (JComponent) row.title.getComponent(1));
            var header = find(view, "@table/tunnels/header/type", JLabel.class);
            assertThat(SwingUtilities.convertPoint(stack.component, 0, 0, view).x).as("cell nodes line up under their header")
                .isEqualTo(SwingUtilities.convertPoint(header, 0, 0, view).x);
            var groups = (Tables.TableControl) view.control("groups");
            assertThat(groups.itemText(1)).as("type-ahead reads the plain first column").isEqualTo("Internal errors");
            combo(groups, "errors").setSelectedIndex(1);
            assertThat(events).containsExactly(new UiEvent.Selected("errors.display", Set.of("sticky")));
            view.reveal("web.kind");
            view.focus("config.display");
            assertThat(groups.focusItem).as("focusing a cell node focuses its row").isEqualTo("config");
        } finally { release(context); }
    }); }

    @Test void aCellDropdownIsItsValueAndChevronUntilHoveredOrFocused() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of())), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var card = combo(groups, "errors");
            var sticky = combo(groups, "config");
            assertThat(card.cell).isTrue();
            assertThat(card.bare()).isTrue();
            assertThat(card.getWidth()).as("as wide as its value and chevron").isEqualTo(card.cellWidth()).isLessThan(sticky.getWidth());
            assertThat(card.getX()).as("at the cell's leading edge").isZero();
            assertThat(card.getWidth()).isLessThan(card.getParent().getWidth());
            int bare = painted(card);
            assertThat(bare).as("the value and chevron paint").isPositive();
            card.hover = true;
            assertThat(card.bare()).isFalse();
            assertThat(painted(card)).as("the control face shows on hover").isGreaterThan(bare);
            card.hover = false;
            // A dropdown outside a table keeps its face.
            try (var plain = new KitView(context, new ViewSpec(new Choice("plain", READY, ChoiceKind.DROPDOWN, DISPLAYS, "card", "Display", "")), e -> {})) {
                var face = (Choices.ChoiceCombo) ((Choices.ChoiceControl) plain.control("plain")).input;
                assertThat(face.cell).isFalse();
                assertThat(face.bare()).isFalse();
            }
        } finally { release(context); }
    }); }

    @Test void aSelectedRowsBareDropdownTakesTheSelectionInk() { Edt.run(() -> {
        var context = KitFixtures.context();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of("errors"))), e -> {})) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var selected = combo(groups, "errors");
            assertThat(selected.cellInk).isEqualTo(groups.ink());
            // BasicComboBoxUI paints the closed value in the combo's foreground.
            assertThat(selected.getForeground()).isEqualTo(ThemePainting.color(context, groups.ink().text));
            assertThat(combo(groups, "config").getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            selected.hover = true;
            assertThat(selected.getForeground()).as("the face shows its own colours").isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
        } finally { release(context); }
    }); }

    @Test void aHeadingRowSpansTheTableWithItsMutedCountAndIsNeverSelected() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(groups(Set.of())), events::add)) {
            view.setSize(640, 300); InteractiveRowsTest.layout(view);
            var groups = (Tables.TableControl) view.control("groups");
            var heading = groups.mounted.get("jasper");
            assertThat(groups.dataRow(0)).isFalse();
            assertThat(groups.selectable(0)).isFalse();
            assertThat(heading.cells).isEmpty();
            var title = (JLabel) heading.title.getComponent(0);
            var count = (JLabel) heading.trailing.getComponent(0);
            assertThat(title.getText()).isEqualTo("Jasper");
            assertThat(count.getText()).isEqualTo("2 groups");
            assertThat(count.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
            int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
            assertThat(heading.trailing.getX() + heading.trailing.getWidth()).as("the count ends at the row's inset").isEqualTo(heading.getWidth() - pad);
            assertThat(heading.title.getX()).isEqualTo(pad);
            assertThat(heading.title.getX() + heading.title.getWidth()).isLessThanOrEqualTo(heading.trailing.getX());
            assertThat(((JLabel) groups.mounted.get("editor").trailing.getComponent(0)).getText()).isEqualTo("1 group");
            groups.move(1); groups.move(1); groups.move(1);
            assertThat(events).as("Down steps over the heading").containsExactly(new UiEvent.Selected("groups", Set.of("errors")),
                new UiEvent.Selected("groups", Set.of("config")), new UiEvent.Selected("groups", Set.of("editor.main")));
            events.clear();
            groups.select("jasper", false);
            groups.activate("editor");
            groups.context("jasper", groups.canvas, 0, 0);
            assertThat(events).as("a heading is never selected or activated").isEmpty();
            assertThat(groups.popup.getComponentCount()).as("nor offered a menu").isZero();
            groups.typeAhead('E', 1);
            assertThat(events).as("type-ahead skips the heading Editor").containsExactly(new UiEvent.Selected("groups", Set.of("editor.main")));
        } finally { release(context); }
    }); }

    @Test void aHeadingCountReadsAsItsNoun() {
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(6), "group"))).isEqualTo("6 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(1), "group"))).isEqualTo("1 group");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(0), "group"))).isEqualTo("0 groups");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(2), "file server"))).isEqualTo("2 file servers");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.of(4), ""))).isEqualTo("4");
        assertThat(Tables.countText(new TableRow.Heading("H", OptionalInt.empty(), ""))).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest`
Expected: FAIL. `:jasper-ui:compileTestJava` stops with "cannot find symbol" for `ChoiceCombo.cell`, `bare()`,
`cellWidth()`, `cellInk` and `Tables.countText`.

- [ ] **Step 3: Render cell nodes, the bare dropdown and heading rows**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java` with:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.*;

/**
 * Column-aligned virtual rows; numeric cells align to the trailing column edge. Columns share the width by weight,
 * the header shows the host's sort and reports clicks as {@link UiEvent.Sorted}, and a muted row dims every cell.
 * A column holds a plain cell or a cell node (a bare dropdown among them), and a heading row shows its title and its
 * muted count across the row.
 */
final class Tables {
    private Tables() {}

    static JScrollPane pane(KitView view, Nodes.Table node) {
        return node.scroll() == Nodes.ScrollPolicy.OVERLAY ? new OverlayScrollPane(view.context()) : new JScrollPane();
    }

    /**
     * A column title as a table's or a tree's header shows it: caption type, the host's sort indicator, and left clicks
     * reported as {@link UiEvent.Sorted} for {@code owner}'s node.
     */
    static JLabel headerLabel(ControlRegistry.Control owner, String prefix, Column column) {
        var context = owner.context();
        var label = Labels.literal();
        KitOwnership.mark(label, prefix + owner.node.id() + "/header/" + column.id());
        label.setText(column.title());
        label.setFont(ThemePainting.font(context, Typography.Role.CAPTION));
        label.setForeground(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
        label.setHorizontalAlignment(
                column.numeric() ? SwingConstants.RIGHT : SwingConstants.LEFT);
        label.setHorizontalTextPosition(SwingConstants.LEADING);
        label.setIcon(column.sorted().map(ascending -> context.icons().icon(ascending ? "chevron.up" : "chevron.down",
                context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION)))).orElse(null));
        label.getAccessibleContext().setAccessibleName(column.title()
                + column.sorted().map(ascending -> ascending ? ", sorted ascending" : ", sorted descending").orElse(""));
        String columnId = column.id();
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) owner.emit(new UiEvent.Sorted(owner.node.id(), columnId));
            }
        });
        return label;
    }

    /** A heading row's count as it reads: "6 groups", "1 group", the bare number without a noun, or nothing. */
    static String countText(TableRow.Heading heading) {
        if (heading.count().isEmpty()) return "";
        int count = heading.count().getAsInt();
        return heading.countNoun().isBlank() ? Integer.toString(count) : count + " " + heading.countNoun() + (count == 1 ? "" : "s");
    }

    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new JPanel();
        /** The row of every cell node and its descendants, so focus and reveal reach row-mounted nodes. */
        final Map<String, Integer> nodeRows = new HashMap<>();
        private List<Column> measuredColumns=List.of();
        private List<TableRow> measuredRows=List.of();
        private List<Font> measuredFonts=List.of();
        private int[] minimums=new int[0];
        private float measuredScale=-1;
        private void measureColumns(){
            var columns=value().columns();var fonts=columns.stream().map(c->ThemePainting.font(context(),Typography.Role.valueOf(c.role().name()))).toList();
            float scale=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            if(measuredColumns.equals(columns)&&measuredRows.equals(value().rows())&&measuredFonts.equals(fonts)&&measuredScale==scale)return;
            measuredScale=scale;measuredColumns=columns;measuredRows=value().rows();measuredFonts=fonts;minimums=new int[columns.size()];
            for(int i=0;i<columns.size();i++){
                var column=columns.get(i);var probe=Labels.literal();probe.setFont(fonts.get(i));probe.setText("…");minimums[i]=probe.getPreferredSize().width;
                if(column.numeric())for(var row:measuredRows){probe.setText(row.cells().getOrDefault(column.id(),""));minimums[i]=Math.max(minimums[i],probe.getPreferredSize().width);}
            }
        }

        /** Minimum negotiation only: rendering still uses the existing weighted allocation and literal cells. */
        int usableWidth() {
            int padding=2*ThemePainting.metric(context(),DesignMetrics.Role.ICON_GAP);
            if(value().columns().isEmpty())return padding;
            var first=value().columns().getFirst();
            var font=ThemePainting.font(context(),Typography.Role.valueOf(first.role().name()));
            int target=ThemePainting.measureText(font,"M".repeat(DesignMetrics.TABLE_MIN_VISIBLE_GLYPHS));
            var icon=value().rows().stream().flatMap(row->row.icon().stream()).findFirst();
            if(icon.isPresent())target+=context().icons().icon(icon.get(),context().theme().semantic(),Nodes.Hue.NONE,
                com.formdev.flatlaf.util.UIScale.scale(14)).getIconWidth()+new JLabel().getIconTextGap();
            // Allocation is monotonic only after existing column minima fit. Below that
            // boundary proportional fallback can give the first column a misleading usable width.
            var layout=weights();int low=java.util.Arrays.stream(minimums).sum(),high=Math.max(low,Math.max(1,target));
            while(layout.allocation(high)[0]<target && high<Integer.MAX_VALUE/2)high*=2;
            while(low<high){int middle=low+(high-low)/2;if(layout.allocation(middle)[0]<target)low=middle+1;else high=middle;}
            return padding+high;
        }

        TableControl(KitView view, Nodes.Table node) {
            super(view, node, pane(view, node));
            header.setOpaque(false);
            scroll.setColumnHeaderView(header);
            // The titles sit on whatever is behind the table, as a heading does: an opaque header viewport painted its
            // own strip over a rounded pane's top.
            scroll.getColumnHeader().setOpaque(false);
            refresh();
        }

        Nodes.Table value() {
            return (Nodes.Table) node;
        }

        @Override
        int size() {
            return value().rows().size();
        }

        @Override
        String itemId(int i) {
            return value().rows().get(i).id();
        }

        @Override
        String itemText(int i) {
            var columns = value().columns();
            return columns.isEmpty() ? "" : cellText(value().rows().get(i), columns.getFirst().id());
        }

        /** A column's text for type-ahead: its plain cell, or a Text cell node's text. */
        static String cellText(TableRow row, String column) {
            if (row.cellNodes().get(column) instanceof Nodes.Text text) return text.text();
            return row.cells().getOrDefault(column, "");
        }

        /** A heading row groups the rows below it; it is never selected, activated or offered a menu. */
        @Override
        boolean dataRow(int index) {
            return index < 0 || index >= size() || value().rows().get(index).heading().isEmpty();
        }

        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */
        @Override
        boolean keepsEnterWithoutFocus() {
            return true;
        }

        @Override
        boolean reportsClicks() {
            return true;
        }

        /** A context press outside every row opens the table's shared menu, as the JTable's component popup did. */
        @Override
        boolean sharedMenuOutsideRows() {
            return true;
        }

        @Override
        Set<String> specifiedSelection() {
            return value().selectedIds();
        }

        @Override
        boolean multiple() {
            return value().selectionMode() == Nodes.SelectionMode.MULTIPLE;
        }

        @Override
        String activation() {
            return value().activateAction();
        }

        @Override
        List<MenuItem> contextActions() {
            return value().contextActions();
        }

        @Override
        List<MenuItem> contextActions(String id) {
            for (var row : value().rows())
                if (row.id().equals(id)) return row.contextActions().orElse(value().contextActions());
            return List.of();
        }

        @Override
        boolean ownsContextActions(String id) {
            return value().rows().stream().anyMatch(row -> row.id().equals(id) && row.contextActions().isPresent());
        }

        void index(Nodes.Node cell, int index) {
            nodeRows.put(cell.id(), index);
            ControlRegistry.children(cell).forEach(child -> index(child, index));
        }

        /** Reveal can arrive before layout and never changes the row's logical keyboard focus. */
        @Override
        boolean revealInline(String id) {
            Integer index = nodeRows.get(id);
            if (index == null) return false;
            pendingReveal = itemId(index);
            ensure(index);
            return true;
        }

        @Override
        ControlRegistry.Control inline(String id) {
            Integer index = nodeRows.get(id);
            if (index == null) return null;
            focusItem = itemId(index);
            focusControl = id;
            ensure(index);
            var row = mounted.get(focusItem);
            return row == null ? null : row.controls.get(id);
        }

        WeightLayout weights() {
            return new WeightLayout(value().columns().stream().mapToDouble(Column::weight).toArray(),minimums);
        }

        @Override
        void data() {
            nodeRows.clear();
            for (int i = 0; i < size(); i++) for (var cell : value().rows().get(i).cellNodes().values()) index(cell, i);
            if (focusControl != null && !nodeRows.containsKey(focusControl)) focusControl = null;
            measureColumns();
            scroll.setMinimumSize(new Dimension(usableWidth(),0));
            // A manager list column's rows start at the rail's top inset below the header, level with a rail beside it.
            scroll.setViewportBorder(column >= 0 ? BorderFactory.createEmptyBorder(ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_RAIL_TOP), 0, 0, 0) : null);
            header.removeAll();
            header.setLayout(weights());
            for (var column : value().columns()) header.add(headerLabel(this, "@table/", column));
            // The header's titles start where the cells' text does: the row inset (a manager column's, else the icon gap).
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP),
                    side = (column >= 0 ? ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_LIST_SIDE) : pad) + com.formdev.flatlaf.util.UIScale.scale(gutter);
            header.setBorder(BorderFactory.createEmptyBorder(pad, side, pad, side));
            header.revalidate();
            header.repaint();
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            row.slot(row.leading, List.of());
            row.slot(row.subtitle, List.of());
            var data = value().rows().get(index);
            if (data.heading().isPresent()) {
                heading(row, data, data.heading().get());
                return;
            }
            row.slot(row.trailing, List.of());
            var nodes = new java.util.ArrayList<Nodes.Node>();
            for (var column : value().columns()) {
                var cell = data.cellNodes().get(column.id());
                nodes.add(cell != null ? cell : new Nodes.Text(
                                "@table/" + node.id() + "/" + data.id() + "/" + column.id(),
                                ControlState.READY,
                                data.cells().getOrDefault(column.id(), ""),
                                column.role()));
            }
            row.slot(row.title, nodes);
            row.title.setLayout(weights());
            // A selected row's cells and glyph take its fill's ink (row.inked); a muted row keeps its placeholder ink only unselected.
            Color muted = data.muted() && !row.inked ? ThemePainting.color(context(), SemanticColor.TEXT_PLACEHOLDER) : null;
            for (int i = 0; i < nodes.size(); i++) {
                var cell = (JComponent) row.title.getComponent(i);
                var control = row.controls.get(nodes.get(i).id());
                // A cell dropdown is bare; on a selected row its bare value takes the fill's ink.
                if (control instanceof Choices.ChoiceControl choice) choice.inCell(row.inked ? ink() : null);
                if (control instanceof Labels.TextControl && cell instanceof JLabel label) {
                    label.setHorizontalAlignment(
                            value().columns().get(i).numeric()
                                    ? SwingConstants.RIGHT
                                    : SwingConstants.LEFT);
                    // The row's glyph leads the first column when that column is a plain cell.
                    if (!data.cellNodes().containsKey(value().columns().get(i).id()))
                        label.setIcon(
                                i == 0 && data.icon().isPresent() && row.inked
                                        ? context().icons().icon(data.icon().get(), context().theme().semantic(), row.owner.ink().text,
                                                com.formdev.flatlaf.util.UIScale.scale(14))
                                        : i == 0 && data.icon().isPresent()
                                        ? context()
                                                .icons()
                                                .icon(
                                                        data.icon().get(),
                                                        context().theme().semantic(),
                                                        Nodes.Hue.NONE,
                                                        com.formdev.flatlaf.util.UIScale.scale(14))
                                        : null);
                    if (muted != null) label.setForeground(muted);
                }
                row.cells.add(cell);
            }
            row.tooltip(data.tooltip());
            // A table row is named by its first column, as its identity (a file's name, an entry's title).
            row.name(nodes.isEmpty() ? List.of() : List.of(nodes.getFirst()));
        }

        /** A heading row: its strong title across the row and its count, muted, at the row's end. */
        private void heading(InteractiveRows.Row row, TableRow data, TableRow.Heading heading) {
            String prefix = "@table/" + node.id() + "/" + data.id();
            String count = countText(heading);
            row.slot(row.trailing, count.isEmpty() ? List.of()
                    : List.of(new Nodes.Text(prefix + "/count", ControlState.READY, count, Nodes.TextRole.CAPTION)));
            var title = new Nodes.Text(prefix + "/heading", ControlState.READY, heading.title(), Nodes.TextRole.BODY_STRONG);
            row.slot(row.title, List.of(title));
            row.tooltip("");
            row.name(List.of(title));
        }
    }

    /** Columns side by side, each taking its weight's share of the width; children beyond the weights get none. */
    static final class WeightLayout implements LayoutManager {
        final double[] weights;
        final int[] minimums;

        WeightLayout(double[] weights) {this(weights,new int[weights.length]);}
        WeightLayout(double[] weights,int[] minimums) {this.weights=weights;this.minimums=minimums;}
        /** Preserve ordinary weighted widths; freeze only undersized columns and redistribute their deficit. */
        private int[] allocation(int width){
            int count=weights.length;var sizes=new double[count];var fixed=new boolean[count];
            int required=java.util.Arrays.stream(minimums).sum();boolean feasible=required<=width;
            double remaining=width,total=java.util.Arrays.stream(weights).sum();
            if(feasible)for(int pass=0;pass<count;pass++){
                boolean changed=false;
                for(int i=0;i<count;i++)if(!fixed[i]&&total>0&&remaining*weights[i]/total<minimums[i]){
                    fixed[i]=true;sizes[i]=minimums[i];remaining-=minimums[i];total-=weights[i];changed=true;
                }
                if(!changed)break;
            }
            for(int i=0;i<count;i++)if(!fixed[i])sizes[i]=total>0?Math.max(0,remaining)*weights[i]/total:0;
            var result=new int[count];double end=0;int previous=0;
            for(int i=0;i<count;i++){end+=sizes[i];int edge=Math.min(width,(int)Math.round(end));result[i]=Math.max(0,edge-previous);previous=edge;}
            return result;
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0;
            for (var c : parent.getComponents()) {
                width += c.getPreferredSize().width;
                height = Math.max(height, c.getPreferredSize().height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right),
                    height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int[] sizes=allocation(width);int x=insets.left;var children=parent.getComponents();
            for(int i=0;i<children.length;i++){
                int w=i<sizes.length?sizes[i]:0;children[i].setBounds(x,insets.top,w,height);x+=w;
            }
        }
    }
}
```

The header labels, `measureColumns`, `usableWidth`, the plain-cell path of `populate` and `WeightLayout` are today's
code: a table without cell nodes or headings builds the same components with the same calls.

Then run from the worktree root (the bare dropdown, the heading row's count, and the walks of cell nodes):

```bash
python3 -I - <<'PY'
"""Plan 3c Task 3 step 3: the bare cell dropdown, a heading row's trailing count, and validation walks of cell nodes."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java", [
    ('''        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(!value); }
''', '''        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(!value); }
        /**
         * A DROPDOWN in a table cell (its row sets this on every populate): bare until hovered, focused or open, as wide
         * as its value and chevron, and while bare on a selected row its value and chevron take {@code ink}; null off one.
         */
        void inCell(InteractiveRows.Ink ink) {
            if (!(input instanceof ChoiceCombo combo) || combo.cell && combo.cellInk == ink) return;
            combo.cell = true;
            combo.cellInk = ink;
            component.revalidate(); component.repaint();
        }
''', 1),
    ('''            if (input instanceof SegmentTrack) container.setLayout(new SegmentColumn());
''', '''            if (input instanceof SegmentTrack) container.setLayout(new SegmentColumn());
            else if (node.kind() == Nodes.ChoiceKind.DROPDOWN) container.setLayout(new CellLayout((ChoiceCombo) input));
''', 1),
    ('''    static class ChoiceCombo extends JComboBox<Option> {
''', '''    /** A choice's label, input and error, where a cell dropdown keeps its own width, centred on the line it is given. */
    static final class CellLayout extends BorderLayout {
        final ChoiceCombo combo;
        CellLayout(ChoiceCombo combo) { this.combo = combo; }
        @Override public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            if (!combo.cell) return;
            var size = combo.getPreferredSize();
            int height = Math.min(combo.getHeight(), size.height);
            combo.setBounds(combo.getX(), combo.getY() + (combo.getHeight() - height) / 2, Math.min(combo.getWidth(), size.width), height);
        }
    }
    static class ChoiceCombo extends JComboBox<Option> {
''', 1),
    ('''        boolean hover;
        /** A rich value's trailing chevron lane;''', '''        boolean hover;
        /** In a table cell (set by {@link ChoiceControl#inCell}); the ink of its selected row while it is bare, else null. */
        boolean cell;
        InteractiveRows.Ink cellInk;
        /** A cell dropdown shows only its value and chevron until it is hovered, focused or open. */
        boolean bare() { return cell && !hover && !(focus != null && focus.visible()) && !isPopupVisible(); }
        /** The closed value is painted in the combo's foreground (BasicComboBoxUI), so a bare value on a selected row takes the row's ink here. */
        @Override public Color getForeground() { return bare() && cellInk != null ? ThemePainting.color(context, cellInk.text) : super.getForeground(); }
        /** A rich value's trailing chevron lane;''', 1),
    ('''                        boolean settings = settingsWidth > 0;
                        var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,''',
     '''                        boolean settings = settingsWidth > 0;
                        var icon = context.icons().icon("chevron.down", context.theme().semantic(), bare() && cellInk != null ? cellInk.supporting : SemanticColor.TEXT_SECONDARY,''', 1),
    ('''            return new Dimension(settingsWidth>0?settingsWidth:Math.max(size.width, naturalWidth()), compactHeight && face == null ? height : Math.max(size.height, height));''',
     '''            return new Dimension(settingsWidth>0?settingsWidth:cell && naturalWidth() > 0 ? cellWidth() : Math.max(size.width, naturalWidth()), compactHeight && face == null ? height : Math.max(size.height, height));''', 1),
    ('''                + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_ARROW_WIDTH) + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET);
        }
''', '''                + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_ARROW_WIDTH) + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET);
        }
        /** A cell dropdown's width: its selected value (or the placeholder), then the chevron, so the chevron follows the value. */
        int cellWidth() {
            var metrics = getFontMetrics(ThemePainting.font(context, Typography.Role.BODY));
            String value = getSelectedItem() instanceof Option selected ? selected.label() : "Choose…";
            var insets = getInsets();
            return insets.left + insets.right + metrics.stringWidth(value) + 2 * ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)
                + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_ARROW_WIDTH) + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET);
        }
''', 1),
    ('''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.rounded(g, this, context, ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER''',
     '''        @Override protected void paintComponent(Graphics graphics) {
            if (bare()) { super.paintComponent(graphics); return; }
            var g = (Graphics2D)graphics.create(); try { ThemePainting.rounded(g, this, context, ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java", [
    ('''        /** A heading shows only its title, inset like the rows below it. */
        void headingLayout() {
            int pad = ThemePainting.metric(owner.context(), owner.resultsLayout() ? DesignMetrics.Role.RESULTS_ROW_PADDING : owner.identityRows() ? DesignMetrics.Role.IDENTITY_ROW_SIDE : DesignMetrics.Role.ICON_GAP);
            leading.setBounds(0, 0, 0, 0);
            subtitle.setBounds(0, 0, 0, 0);
            trailing.setBounds(0, 0, 0, 0);
            // An identity list's heading sits low in its band, close to the rows it introduces.
            int line = owner.identityRows() ? Math.min(getHeight(), title.getPreferredSize().height) : getHeight();
            int top = owner.identityRows() ? Math.max(0, getHeight() - line - ThemePainting.metric(owner.context(), DesignMetrics.Role.IDENTITY_HEADING_BOTTOM)) : 0;
            title.setBounds(pad, top, Math.max(0, getWidth() - 2 * pad), line);
            title.doLayout();
        }''', '''        /** A heading shows its title, inset like the rows below it, and a table heading's count at the row's end. */
        void headingLayout() {
            int pad = ThemePainting.metric(owner.context(), owner.resultsLayout() ? DesignMetrics.Role.RESULTS_ROW_PADDING : owner.identityRows() ? DesignMetrics.Role.IDENTITY_ROW_SIDE : DesignMetrics.Role.ICON_GAP);
            leading.setBounds(0, 0, 0, 0);
            subtitle.setBounds(0, 0, 0, 0);
            // An identity list's heading sits low in its band, close to the rows it introduces.
            int line = owner.identityRows() ? Math.min(getHeight(), title.getPreferredSize().height) : getHeight();
            int top = owner.identityRows() ? Math.max(0, getHeight() - line - ThemePainting.metric(owner.context(), DesignMetrics.Role.IDENTITY_HEADING_BOTTOM)) : 0;
            // Only a table heading has trailing content (its count); a list heading has a title only.
            int tw = trailing.getComponentCount() == 0 ? 0 : Math.min(trailing.getPreferredSize().width, Math.max(0, getWidth() - 2 * pad));
            if (tw == 0) trailing.setBounds(0, 0, 0, 0);
            else trailing.setBounds(getWidth() - pad - tw, top, tw, line);
            title.setBounds(pad, top, Math.max(0, getWidth() - 2 * pad - (tw > 0 ? tw + pad : 0)), line);
            title.doLayout();
            trailing.doLayout();
        }''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java", [
    ('''            case Nodes.Table _, Nodes.Tree _, Nodes.Breadcrumbs _ -> {}
''', '''            case Nodes.Table n -> { for (var row : n.rows()) row.cellNodes().values().forEach(ControlRegistry::validate); n.empty().ifPresent(ControlRegistry::validate); }
            case Nodes.Tree _, Nodes.Breadcrumbs _ -> {}
''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java", [
    ('''                    row.contextActions().ifPresent(menu->validateMenus(menu,icons));
                }
            }
''', '''                    row.contextActions().ifPresent(menu->validateMenus(menu,icons));
                    row.cellNodes().values().forEach(child->validateDetail(child,icons));
                }
                table.empty().ifPresent(child->validateDetail(child,icons));
            }
''', 1)])
PY
```

`Choices.java` imports `java.awt.*` (so `Container`, `Color` and `BorderLayout` resolve) and `dev.jasper.ui.metrics.*`;
an ordinary dropdown has `cell == false`, so `bare()` is false and its paint, size, colours and layout are today's
(`bare()` reads `cell` first, so the `getForeground()` calls Swing makes while the combo is constructed never reach
`isPopupVisible()`). The value's ink is the combo's foreground because `BasicComboBoxUI.paintCurrentValue` paints the
closed value in `comboBox.getForeground()`, whatever the renderer set.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest --tests dev.jasper.ui.swing.TablesTest --tests dev.jasper.ui.swing.BrowserKitTest --tests dev.jasper.ui.swing.ChoicesTest --tests dev.jasper.ui.swing.InteractiveRowsTest --tests dev.jasper.ui.swing.ResultsKitTest --tests dev.jasper.ui.swing.SelectionInkTest --tests 'dev.jasper.ui.gallery.*' :jasper-ui:javadoc --continue`
Expected: BUILD SUCCESSFUL; the five `TableVocabularyTest` tests pass and the existing table, list, dropdown, ink and
gallery suites stay green unchanged.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: cell nodes, the bare cell dropdown and heading rows

A table mounts each column's cell node in its weighted column, and focus
and reveal reach them; a DROPDOWN in a cell is its value and chevron
until hovered or focused, taking a selected row's ink while bare; a
heading row shows its strong title and muted count across the row and
is never selected, activated or typed to. Plain tables, lists and
dropdowns paint as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Empty states in the default look

An empty `ItemList` or `Table` with an `emptyText` shows one muted, centred line in its body (above an ItemList's add
action); an empty `Table` with an `empty` state mounts it on its canvas, below the kept headers, filling and centring
in the body; rows replace the state, and the state returns when the rows go. A collection without either draws as
before.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java:384,401-404,443-444,769-776,793-796`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java:148-151` (after `lines()`), `jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java` (Task 3's `TableControl`), `jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java:62`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java` (two tests appended)

**Interfaces:**
- Consumes: Task 3's `Tables.TableControl` (its `dataRow` override is the anchor) and Task 1's `emptyText()` and
  `empty()`.
- Produces: `RowsControl.emptyLine` (a `JLabel`, null until a collection first has an empty text), `String
  emptyText()`, `boolean fillsViewport()`, `int emptyLineHeight()`, `void emptyLine()`; `ControlRegistry.children(Table)`
  is the table's `empty` while it has no rows.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 4 step 1: the empty line and an empty table's state."""
import pathlib
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the class's closing brace at the end")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
before_last_brace("jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java", '''
    static Table empties(boolean rows) {
        var state = new Empty("tunnels.empty", READY, new Nodes.Icon("tunnels.empty.icon", READY, "server", Hue.NEUTRAL, IconSize.EMPTY, "Tunnels"), "No Tunnels",
            "Forward a local port to a remote host.", List.of(new Nodes.Button("tunnels.new", READY, "New Tunnel\\u2026", "", ButtonKind.SECONDARY, "new-tunnel", "")));
        return new Table("tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false)),
            rows ? List.of(new TableRow("web", Map.of("type", "Local", "local", "8080"), Optional.empty())) : List.of(),
            Set.of(), "toggle", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(state), "");
    }

    static ItemList keys(List<ListItem> items) {
        return new ItemList("keys", READY, ListKind.ORDERED, items, Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(),
            "", "", "", "", "", "No keys");
    }

    @Test void anEmptyTableKeepsItsHeadersAndCentresItsStateInTheBody() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(empties(false)), events::add)) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            var table = (Tables.TableControl) view.control("tunnels");
            assertThat(table.header.getComponentCount()).as("the headers stay").isEqualTo(2);
            var state = view.control("tunnels.empty").component;
            assertThat(state.getParent()).isSameAs(table.canvas);
            assertThat(table.canvas.getHeight()).as("the body fills the viewport").isEqualTo(table.scroll.getViewport().getHeight());
            assertThat(state.getY()).isZero();
            assertThat(state.getHeight()).isEqualTo(table.canvas.getHeight()).isGreaterThanOrEqualTo(state.getPreferredSize().height);
            ((Buttons.ButtonControl) view.control("tunnels.new")).button.doClick(0);
            assertThat(events).containsExactly(new UiEvent.Action("new-tunnel"));
            view.update(new ViewSpec(empties(true))); InteractiveRowsTest.layout(view);
            assertThat(view.controlOrNull("tunnels.empty")).as("rows replace the state").isNull();
            assertThat(view.controlOrNull("tunnels.new")).isNull();
            assertThat(state.getParent()).isNull();
            assertThat(table.canvas.getHeight()).as("rows keep their own height").isEqualTo(table.rowsHeight());
            view.update(new ViewSpec(empties(false))); InteractiveRowsTest.layout(view);
            assertThat(view.controlOrNull("tunnels.empty")).as("and the state returns with no rows").isNotNull();
        } finally { release(context); }
    }); }

    @Test void anEmptyTextIsOneMutedLineUntilItemsArrive() { Edt.run(() -> {
        var context = KitFixtures.context();
        var files = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false)), List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty(), "Not Connected");
        var plain = new ItemList("plain", READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of());
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(files, keys(List.of()), plain))), e -> {})) {
            view.setSize(420, 360); InteractiveRowsTest.layout(view);
            for (String id : List.of("files", "keys")) {
                var rows = (InteractiveRows.RowsControl) view.control(id);
                assertThat(rows.emptyLine.isVisible()).as(id).isTrue();
                assertThat(rows.emptyLine.getText()).isEqualTo(id.equals("files") ? "Not Connected" : "No keys");
                assertThat(rows.emptyLine.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                assertThat(rows.emptyLine.getHorizontalAlignment()).isEqualTo(SwingConstants.CENTER);
                assertThat(rows.emptyLine.getHeight()).isEqualTo(rows.rowHeight);
                assertThat(rows.canvas.getPreferredScrollableViewportSize().height).isEqualTo(rows.rowHeight + rows.footerHeight());
            }
            assertThat(((InteractiveRows.RowsControl) view.control("plain")).emptyLine).as("no empty text, no line").isNull();
            var key = new ListItem("id_ed25519", Optional.empty(), List.of(new Text("key.name", READY, "id_ed25519", TextRole.BODY)), List.of(), List.of());
            view.update(new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(files, keys(List.of(key)), plain)))); InteractiveRowsTest.layout(view);
            var list = (InteractiveRows.RowsControl) view.control("keys");
            assertThat(list.emptyLine.isVisible()).isFalse();
            assertThat(list.emptyLineHeight()).isZero();
        } finally { release(context); }
    }); }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest`
Expected: FAIL. `:jasper-ui:compileTestJava` stops with "cannot find symbol" for `RowsControl.emptyLine` and
`emptyLineHeight()`.

- [ ] **Step 3: Draw the empty line and mount an empty table's state**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 4 step 3: the empty line in every row collection, and an empty table's state on its canvas."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java", [
    ('''            measureRows();
            canvas.setFocusable(!externallyDriven() && !passive());''', '''            measureRows();
            emptyLine();
            canvas.setFocusable(!externallyDriven() && !passive());''', 1),
    ('''        int footerHeight() {
            return footer == null || !footer.isVisible() ? 0 : footer.getPreferredSize().height;
        }
''', '''        int footerHeight() {
            return footer == null || !footer.isVisible() ? 0 : footer.getPreferredSize().height;
        }

        /** The muted line an empty collection shows in its body (ItemList.emptyText, Table.emptyText); null until first set. */
        JLabel emptyLine;

        /** The text an empty collection shows in its body; blank shows none. */
        String emptyText() {
            return "";
        }

        /** Whether the canvas fills the viewport's height, as an empty table's state does; never by default. */
        boolean fillsViewport() {
            return false;
        }

        /** The empty line's band: one row tall while it shows. */
        int emptyLineHeight() {
            return emptyLine != null && emptyLine.isVisible() ? rowHeight : 0;
        }

        /** Shows the empty line while the collection has no items and a non-blank empty text; created on first use. */
        void emptyLine() {
            String text = emptyText();
            if (emptyLine == null && text.isBlank()) return;
            if (emptyLine == null) {
                emptyLine = Labels.literal();
                emptyLine.setHorizontalAlignment(SwingConstants.CENTER);
                KitOwnership.mark(emptyLine, "@rows/" + node.id() + "/empty");
                canvas.add(emptyLine);
            }
            emptyLine.setText(text);
            emptyLine.setFont(ThemePainting.font(context(), Typography.Role.DESCRIPTION));
            emptyLine.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY));
            emptyLine.setVisible(size() == 0 && !text.isBlank());
        }
''', 1),
    ('''                for (var row : spare) canvas.remove(row);
                if (footer != null)
                    footer.setBounds(inset, rowsHeight(), Math.max(0, width - 2 * inset), footerHeight());''', '''                for (var row : spare) canvas.remove(row);
                if (emptyLine != null)
                    emptyLine.setBounds(inset, rowsHeight(), Math.max(0, width - 2 * inset), emptyLineHeight());
                if (footer != null)
                    footer.setBounds(inset, rowsHeight() + emptyLineHeight(), Math.max(0, width - 2 * inset), footerHeight());''', 1),
    ('''            return new Dimension(360, owner.rowsHeight() + owner.footerHeight());''',
     '''            return new Dimension(360, owner.rowsHeight() + owner.emptyLineHeight() + owner.footerHeight());''', 1),
    ('''            return new Dimension(360, owner.viewportRowsHeight() + owner.footerHeight());''',
     '''            return new Dimension(360, owner.viewportRowsHeight() + owner.emptyLineHeight() + owner.footerHeight());''', 1),
    ('''        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }''', '''        @Override
        public boolean getScrollableTracksViewportHeight() {
            // An empty table's state takes the whole body below the headers; otherwise the rows keep their own height.
            return owner.fillsViewport() && getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java", [
    ('''        @Override
        int lines() {
            return value().kind() == Nodes.ListKind.DOUBLE ? 2 : 1;
        }
''', '''        @Override
        int lines() {
            return value().kind() == Nodes.ListKind.DOUBLE ? 2 : 1;
        }

        @Override
        String emptyText() {
            return value().emptyText();
        }
''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java", [
    ('''        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */''',
     '''        @Override
        String emptyText() {
            return value().emptyText();
        }

        /** An empty table's state fills the body below the headers; the reconciler mounts it only while there are no rows. */
        @Override
        boolean fillsViewport() {
            return footer != null && value().rows().isEmpty();
        }

        @Override
        Container childParent(Nodes.Node child) {
            return canvas;
        }

        /** The table's only child is its empty state, which lies on the canvas where the rows would be. */
        @Override
        void children(List<ControlRegistry.Control> controls) {
            JComponent next = controls.isEmpty() ? null : controls.getFirst().component;
            if (footer != next && footer != null) canvas.remove(footer);
            footer = next;
            if (footer != null && footer.getParent() != canvas) canvas.add(footer);
            canvas.revalidate();
            sync();
        }

        /** An empty table's state takes the canvas's whole height, so it centres in the body. */
        @Override
        void layoutAccessories(int width) {
            if (!fillsViewport()) return;
            int inset = com.formdev.flatlaf.util.UIScale.scale(gutter);
            footer.setBounds(inset, 0, Math.max(0, width - 2 * inset), Math.max(footerHeight(), canvas.getHeight()));
        }

        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java", [
    ('''            case Nodes.ItemList n -> n.addAction().ifPresent(result::add);
''', '''            case Nodes.ItemList n -> n.addAction().ifPresent(result::add);
            // An empty table's state is mounted only while it has no rows; its cell nodes are mounted by their rows.
            case Nodes.Table n -> { if (n.rows().isEmpty()) n.empty().ifPresent(result::add); }
''', 1)])
PY
```

`InteractiveRows.java` imports `dev.jasper.ui.metrics.*`, `dev.jasper.ui.theme.*` and `javax.swing.*`, so `JLabel`,
`JViewport`, `SwingConstants`, `Typography` and `SemanticColor` resolve. With no empty text and no empty state,
`emptyLine` stays null, `emptyLineHeight()` is 0 and `fillsViewport()` is false, so every height, bound and paint is
today's.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest --tests dev.jasper.ui.swing.InteractiveRowsTest --tests dev.jasper.ui.swing.TablesTest --tests dev.jasper.ui.swing.ItemListShortcutsTest --tests dev.jasper.ui.swing.EmptyStatesTest --tests dev.jasper.ui.swing.PanelCompositionTest --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: BUILD SUCCESSFUL; the seven `TableVocabularyTest` tests pass and the others stay green unchanged.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: empty lines and an empty table's state

A list or table with no items shows its emptyText as one muted, centred
line in its body; an empty table mounts its empty state on the canvas,
below the kept headers, filling and centring in the body, and rows
replace it. Collections without either draw as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: Tree columns in the default look

A tree with columns gets a column header (Task 3's `Tables.headerLabel`, so clicks report `Sorted` and the sort
indicator shows): the first title starts at the rows' content inset, the others right-aligned over their values. Each
row shows one value per column after the title (blank when the item has fewer), each column as wide as its widest
value or title, and when any item has a count every row reserves the count column, so the values line up. A tree
without columns has no header and draws as before.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java:75,83,266,273-276,279,361-363,371-373,385-392`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java` (one test appended)

**Interfaces:**
- Consumes: `Tables.headerLabel(ControlRegistry.Control, String, Column)` (Task 3); `Nodes.Tree.columns()` (Task 1).
- Produces: `Trees.TreeControl.header` (a `JPanel`), `countWidth`, `columnHeader()`, `rowPad()`,
  `trailingColumns(TreeItem)`; `Trees.ColumnHeaderLayout`.

- [ ] **Step 1: Write the failing test**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 5 step 1: a column tree's header over its values."""
import pathlib
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the class's closing brace at the end")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
before_last_brace("jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java", '''
    static final List<Column> SESSION_COLUMNS = List.of(new Column("name", "Name", TextRole.BODY, false),
        new Column("host", "Host", TextRole.MONO_SMALL, false, 1, Optional.of(true)), new Column("port", "Port number", TextRole.MONO_SMALL, true));

    static Tree sessions(List<Column> columns) {
        var mac = new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.SUCCESS),
            List.of("192.168.1.119", "22"), Optional.empty());
        var mine = new TreeItem("mine", "minecraft-local", Optional.empty(), true, List.of(), OptionalInt.of(12), false, "", Optional.of(StatusKind.IDLE),
            List.of("192.168.1.124"), Optional.empty());
        return new Tree("sessions", READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(mac, mine))),
            Set.of("lan"), Set.of(), "connect", columns);
    }

    static int left(java.awt.Component c, java.awt.Component root) { return SwingUtilities.convertPoint(c, 0, 0, root).x; }
    static int right(java.awt.Component c, java.awt.Component root) { return SwingUtilities.convertPoint(c, c.getWidth(), 0, root).x; }

    @Test void aColumnTreeShowsItsHeaderOverTheValuesAndReportsSorts() { Edt.run(() -> {
        var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
        try (var view = new KitView(context, new ViewSpec(sessions(SESSION_COLUMNS)), events::add)) {
            view.setSize(480, 320); InteractiveRowsTest.layout(view);
            var tree = (Trees.TreeControl) view.control("sessions");
            assertThat(tree.scroll.getColumnHeader().getView()).isSameAs(tree.header);
            var name = find(view, "@tree/sessions/header/name", JLabel.class);
            var host = find(view, "@tree/sessions/header/host", JLabel.class);
            var port = find(view, "@tree/sessions/header/port", JLabel.class);
            assertThat(host.getIcon()).as("the sort indicator").isNotNull();
            assertThat(left(name, view)).as("the item column's title starts where the rows' content does").isEqualTo(left(tree.mounted.get("lan").leading, view));
            for (String item : List.of("lan", "mac", "mine")) {
                var row = tree.mounted.get(item);
                assertThat(row.trailing.getComponentCount()).as(item + ": a value per column, then the count column").isEqualTo(3);
                assertThat(right(row.trailing.getComponent(0), view)).as(item + " under Host").isEqualTo(right(host, view));
                assertThat(right(row.trailing.getComponent(1), view)).as(item + " under Port").isEqualTo(right(port, view));
            }
            assertThat(((JLabel) tree.mounted.get("mine").trailing.getComponent(1)).getText()).as("a missing value is blank").isEmpty();
            assertThat(tree.mounted.get("mac").trailing.getComponent(1).getWidth()).as("a column is as wide as its title")
                .isGreaterThanOrEqualTo(ThemePainting.measureText(port.getFont(), "Port number"));
            for (var listener : host.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(host, 0));
            assertThat(events).containsExactly(new UiEvent.Sorted("sessions", "host"));
            view.update(new ViewSpec(sessions(List.of()))); InteractiveRowsTest.layout(view);
            assertThat(tree.scroll.getColumnHeader()).as("no columns, no header").isNull();
            assertThat(tree.mounted.get("mine").trailing.getComponentCount()).as("its own value and its count").isEqualTo(2);
        } finally { release(context); }
    }); }
''')
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest`
Expected: FAIL. `:jasper-ui:compileTestJava` stops with "cannot find symbol: variable header" in
`Trees.TreeControl`.

- [ ] **Step 3: Give a column tree its header and aligned values**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 5 step 3: a column tree's header, padded values and count column."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java", [
    ('''    record Visible(TreeItem item, int depth) {}
''', '''    record Visible(TreeItem item, int depth) {}

    /**
     * A column tree's header: the item column's title from the rows' content inset, then each later title right-aligned
     * over its trailing column, packed against the count column at the rows' trailing inset, as the rows pack them.
     */
    static final class ColumnHeaderLayout implements java.awt.LayoutManager {
        final TreeControl owner;
        ColumnHeaderLayout(TreeControl owner) { this.owner = owner; }
        @Override public void addLayoutComponent(String name, java.awt.Component component) {}
        @Override public void removeLayoutComponent(java.awt.Component component) {}
        @Override public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
            int height = 0;
            for (var child : parent.getComponents()) height = Math.max(height, child.getPreferredSize().height);
            var insets = parent.getInsets();
            return new java.awt.Dimension(insets.left + insets.right, height + insets.top + insets.bottom);
        }
        @Override public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(java.awt.Container parent) {
            var insets = parent.getInsets();
            var children = parent.getComponents();
            int gap = ThemePainting.metric(owner.context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int end = parent.getWidth() - insets.right - (owner.countWidth > 0 ? owner.countWidth + gap : 0);
            for (int i = children.length - 1; i >= 1; i--) {
                int width = i - 1 < owner.trailingWidths.length ? owner.trailingWidths[i - 1] : children[i].getPreferredSize().width;
                children[i].setBounds(end - width, insets.top, width, height);
                end -= width + gap;
            }
            if (children.length > 0) children[0].setBounds(insets.left, insets.top, Math.max(0, end - insets.left), height);
        }
    }
''', 1),
    ('''        int[] trailingWidths = new int[0];
''', '''        int[] trailingWidths = new int[0];
        /** A column tree's header (Tree.columns): the item column's title and one title over each trailing column. */
        final javax.swing.JPanel header = new javax.swing.JPanel();
        /** A column tree's count column: as wide as its widest count, so every row's values line up; zero without counts. */
        int countWidth;
''', 1),
    ('''            int columns = items.values().stream().mapToInt(item -> item.trailing().size()).max().orElse(0);''',
     '''            int columns = value().columns().isEmpty() ? items.values().stream().mapToInt(item -> item.trailing().size()).max().orElse(0)
                : value().columns().size() - 1;''', 1),
    ('''            for (var item : items.values())
                for (int i = 0; i < item.trailing().size(); i++)
                    trailingWidths[i] = Math.max(trailingWidths[i], metrics.stringWidth(item.trailing().get(i)));
        }
''', '''            for (var item : items.values())
                for (int i = 0; i < item.trailing().size(); i++)
                    trailingWidths[i] = Math.max(trailingWidths[i], metrics.stringWidth(item.trailing().get(i)));
            columnHeader();
        }

        /** A plain tree row's side inset, as InteractiveRows.Row.doLayout gives it. */
        int rowPad() {
            return ThemePainting.metric(context(), column >= 0 ? dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_LIST_SIDE : dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
        }

        /** How many trailing values a row shows: its own, or in a column tree one per column after the title. */
        int trailingColumns(TreeItem item) {
            return value().columns().isEmpty() ? item.trailing().size() : value().columns().size() - 1;
        }

        /** A column tree's header, or none; widens each trailing column to its title and measures the count column. */
        private void columnHeader() {
            var columns = value().columns();
            countWidth = 0;
            header.removeAll();
            if (columns.isEmpty()) {
                if (scroll.getColumnHeader() != null && scroll.getColumnHeader().getView() == header) scroll.setColumnHeader(null);
                return;
            }
            for (int i = 0; i < columns.size(); i++) {
                var label = Tables.headerLabel(this, "@tree/", columns.get(i));
                // The item column's title reads from the rows' start; the others sit over their right-aligned values.
                if (i > 0) {
                    label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                    trailingWidths[i - 1] = Math.max(trailingWidths[i - 1], label.getPreferredSize().width);
                }
                header.add(label);
            }
            var counts = canvas.getFontMetrics(ThemePainting.font(context(), dev.jasper.ui.metrics.Typography.Role.COUNT));
            for (var item : items.values())
                if (item.count().isPresent()) countWidth = Math.max(countWidth, counts.stringWidth(Integer.toString(item.count().getAsInt())));
            header.setOpaque(false);
            header.setLayout(new ColumnHeaderLayout(this));
            int pad = ThemePainting.metric(context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP),
                    side = rowPad() + com.formdev.flatlaf.util.UIScale.scale(gutter);
            header.setBorder(javax.swing.BorderFactory.createEmptyBorder(pad, side, pad, side));
            if (scroll.getColumnHeader() == null || scroll.getColumnHeader().getView() != header) {
                scroll.setColumnHeaderView(header);
                scroll.getColumnHeader().setOpaque(false);
            }
            header.revalidate();
            header.repaint();
        }
''', 1),
    ('''            for (int i = 0; presented==null && i < item.trailing().size(); i++)
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/trailing/" + i,
                        ControlState.READY, item.trailing().get(i), Nodes.TextRole.CAPTION));''',
     '''            for (int i = 0; presented==null && i < trailingColumns(item); i++)
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/trailing/" + i,
                        ControlState.READY, i < item.trailing().size() ? item.trailing().get(i) : "", Nodes.TextRole.CAPTION));''', 1),
    ('''            item.count().ifPresent(count -> trailing.add(new Nodes.Text(
                    "@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, Integer.toString(count), Nodes.TextRole.COUNT)));
''', '''            item.count().ifPresent(count -> trailing.add(new Nodes.Text(
                    "@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, Integer.toString(count), Nodes.TextRole.COUNT)));
            // A column tree reserves the count column on every row, so the values before it line up.
            if (presented == null && countWidth > 0 && item.count().isEmpty())
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, "", Nodes.TextRole.COUNT));
''', 1),
    ('''            for (int i = 0; presented==null && i < item.trailing().size(); i++) {
                var label = (javax.swing.JLabel) row.controls.get(trailing.get(i).id()).component;''',
     '''            for (int i = 0; presented==null && i < trailingColumns(item); i++) {
                var label = (javax.swing.JLabel) row.controls.get(trailing.get(i).id()).component;''', 1),
    ('''            // The row, its slots and its passive texts (title, trailing columns, count) show the item's literal tooltip.''',
     '''            if (presented == null && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/count") instanceof Labels.TextControl countText
                    && countText.component instanceof javax.swing.JLabel count) {
                count.setPreferredSize(null);
                if (countWidth > 0) {
                    count.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                    count.setPreferredSize(new java.awt.Dimension(countWidth + count.getInsets().left + count.getInsets().right, count.getPreferredSize().height));
                }
            }
            // The row, its slots and its passive texts (title, trailing columns, count) show the item's literal tooltip.''', 1)])
PY
```

A tree without columns computes `trailingWidths` as before, gets no header (`columnHeader` returns at once and
`countWidth` stays 0), pads nothing, adds no count placeholder, and leaves its count label's preferred size unset, as
it always was.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.TableVocabularyTest --tests dev.jasper.ui.swing.TreesTest --tests dev.jasper.ui.swing.TreeDetailTest --tests dev.jasper.ui.swing.BrowserKitTest --tests dev.jasper.ui.swing.SettingsKitTest --tests 'dev.jasper.ui.gallery.*' :jasper-ui:javadoc --continue`
Expected: BUILD SUCCESSFUL; the eight `TableVocabularyTest` tests pass and the tree, keycap-detail, browser, settings
navigation and gallery suites stay green unchanged.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java jasper-ui/src/test/java/dev/jasper/ui/swing/TableVocabularyTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: tree columns

A tree with columns shows a header whose titles sit over its trailing
values (the first from the rows' start, the rest right-aligned), reports
header clicks as Sorted with the sort indicator, pads rows with blank
values and reserves a count column so every value lines up. Trees
without columns draw as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The shared contract, the gallery fixture and the pixel proof

The shared kit contract mounts, updates and drives the whole vocabulary on the host and the fake alike (focus and
reveal into cell nodes and the empty state, the cell dropdown's `Selected`, a tree header's `Sorted`, the state's
action). The gallery gains one fixture with every addition, `GalleryCoverageTest` checks each has a real fixture (the
in-cell DROPDOWN included) and walks map values, and the headless gallery proves no existing pixel moved.

**Files:**
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java` (one descriptor builder and one test appended)
- Create: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/TableFixtures.java`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java:66-67`, `jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java:143` (and one test appended)

**Interfaces:**
- Consumes: everything from Tasks 1 to 5.
- Produces: `KitContractTest.tables(String display, boolean tunnels)` (a `ViewSpec`); `TableFixtures.create()` (a
  `ViewSpec`); the gallery fixture `tables-0-18`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/TableFixtures.java`:

```java
package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import java.util.*;
import java.util.List;

/**
 * SDK 0.18's table vocabulary (plan 3c), as the Workstation and Settings mocks use it: a column tree, a grouped table
 * with cell dropdowns on a selected row, cell stacks with a chip and a status dot, an empty table's state, and the empty
 * lines of a table and an ordered list.
 */
public final class TableFixtures {
    private TableFixtures() {}
    static final ControlState READY = ControlState.READY;

    public static ViewSpec create() {
        var sessions = new Tree("t18-sessions", READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(
                new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.SUCCESS),
                    List.of("192.168.1.119", "22"), Optional.empty()),
                new TreeItem("minecraft", "minecraft-local", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.IDLE),
                    List.of("192.168.1.124", "22"), Optional.empty())))),
            Set.of("lan"), Set.of("mac"), "connect", List.of(new Column("name", "Name", TextRole.BODY, false),
                new Column("host", "Host", TextRole.MONO_SMALL, false, 1, Optional.of(true)), new Column("port", "Port", TextRole.MONO_SMALL, true)));
        var displays = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));
        var groups = new Table("t18-groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 3, Optional.empty()),
                new Column("display", "Display", TextRole.BODY, false, 1.5, Optional.empty()), new Column("source", "Source", TextRole.DESCRIPTION, false)),
            List.of(TableRow.heading("t18-jasper", "Jasper", OptionalInt.of(2), "group"), group("t18-config", "Configuration", "sticky", displays),
                group("t18-errors", "Internal errors", "card", displays), TableRow.heading("t18-editor", "Editor", OptionalInt.of(1), "group"),
                group("t18-editor-main", "Editor", "card", displays)),
            Set.of("t18-errors"), "edit", List.of(), SelectionMode.SINGLE);
        var servers = new Table("t18-servers", READY, List.of(new Column("folder", "Folder", TextRole.BODY, false, 2, Optional.empty()),
                new Column("address", "Address", TextRole.MONO_SMALL, false, 2, Optional.empty()), new Column("status", "Status", TextRole.BODY, false)),
            List.of(new TableRow("t18-test", Map.of("address", "http://192.168.1.119:8080"), Optional.empty(), false, Optional.empty(), "", Map.of(
                "folder", new Stack("t18-test-folder", READY, LayoutRole.INLINE, List.of(new Text("t18-test-name", READY, "test", TextRole.BODY),
                    new Chip("t18-test-chip", READY, "HTTP", Hue.BLUE))),
                "status", new Stack("t18-test-status", READY, LayoutRole.INLINE, List.of(new StatusDot("t18-test-dot", READY, StatusKind.SUCCESS),
                    new Text("t18-test-word", READY, "Running", TextRole.BODY)))), Optional.empty())),
            Set.of(), "toggle", List.of());
        var tunnels = new Table("t18-tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false),
                new Column("remote", "Remote", TextRole.MONO_SMALL, false)),
            List.of(), Set.of(), "toggle", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.of(new Empty("t18-tunnels-empty", READY, new Icon("t18-tunnels-empty-icon", READY, "tunnel", Hue.NEUTRAL, IconSize.EMPTY, "Tunnels"),
                "No Tunnels", "Forward a local port to a remote host.",
                List.of(new Nodes.Button("t18-tunnels-new", READY, "New Tunnel…", "", ButtonKind.SECONDARY, "new-tunnel", "")))), "");
        var files = new Table("t18-files", READY, List.of(new Column("name", "Name", TextRole.BODY, false, 3, Optional.empty()),
                new Column("size", "Size", TextRole.DESCRIPTION, false), new Column("modified", "Modified", TextRole.DESCRIPTION, false, 2, Optional.empty())),
            List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty(), "Not Connected");
        var keys = new ItemList("t18-keys", READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "", "No keys");
        return new ViewSpec(new Stack("t18", READY, LayoutRole.VERTICAL, List.of(sessions, groups, servers, tunnels, files, keys)));
    }

    private static TableRow group(String id, String name, String display, List<Option> displays) {
        return new TableRow(id, Map.of("group", name, "source", "Default"), Optional.empty(), false, Optional.empty(), "",
            Map.of("display", new Choice(id + "-display", READY, ChoiceKind.DROPDOWN, displays, display, "Display", "")), Optional.empty());
    }
}
```

Then run from the worktree root (the coverage test and the contract test; the gallery entry follows in Step 3):

```bash
python3 -I - <<'PY'
"""Plan 3c Task 6 step 1: gallery coverage of the table vocabulary and the shared contract."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the class's closing brace at the end")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
coverage = "jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java"
edit(coverage, [('''        if (value instanceof Iterable<?> items) { items.forEach(v -> collect(v, values)); return; }
''', '''        if (value instanceof Iterable<?> items) { items.forEach(v -> collect(v, values)); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(v -> collect(v, values)); return; }
''', 1)])
before_last_brace(coverage, '''
    /** Spec 16.1.3 and plan 3c: SDK 0.18's table vocabulary has a real fixture, the in-cell DROPDOWN among it. */
    @Test void everyTableAdditionOfSdkZeroEighteenHasARealFixture() {
        var nodes = new ArrayList<Nodes.Node>();
        KitGallery.fixtures().forEach(f -> nodes(f.spec().root(), nodes));
        assertThat(nodes).as("a tree with columns").anyMatch(n -> n instanceof Nodes.Tree tree && tree.columns().size() > 1);
        assertThat(nodes).as("a heading row with a counted noun").anyMatch(n -> n instanceof Nodes.Table table
            && table.rows().stream().anyMatch(row -> row.heading().filter(h -> h.count().isPresent() && !h.countNoun().isBlank()).isPresent()));
        assertThat(nodes).as("an in-cell DROPDOWN").anyMatch(n -> n instanceof Nodes.Table table && table.rows().stream()
            .anyMatch(row -> row.cellNodes().values().stream().anyMatch(c -> c instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.DROPDOWN)));
        assertThat(nodes).as("an inline cell stack").anyMatch(n -> n instanceof Nodes.Table table && table.rows().stream()
            .anyMatch(row -> row.cellNodes().values().stream().anyMatch(c -> c instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.INLINE)));
        assertThat(nodes).as("an empty table's state").anyMatch(n -> n instanceof Nodes.Table table && table.rows().isEmpty() && table.empty().isPresent());
        assertThat(nodes).as("an empty table's line").anyMatch(n -> n instanceof Nodes.Table table && table.rows().isEmpty() && !table.emptyText().isBlank());
        assertThat(nodes).as("an empty list's line").anyMatch(n -> n instanceof Nodes.ItemList list && list.items().isEmpty() && !list.emptyText().isBlank());
    }

    /** Every node in a descriptor value: records, optionals, lists and map values, walked like {@link #collect}. */
    static void nodes(Object value, List<Nodes.Node> into) {
        if (value == null || value instanceof Enum<?>) return;
        if (value instanceof Optional<?> o) { o.ifPresent(v -> nodes(v, into)); return; }
        if (value instanceof Iterable<?> items) { items.forEach(v -> nodes(v, into)); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(v -> nodes(v, into)); return; }
        if (value instanceof Nodes.Node n) into.add(n);
        if (value instanceof Record record) try {
            for (RecordComponent c : record.getClass().getRecordComponents()) nodes(c.getAccessor().invoke(record), into);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
''')
before_last_brace("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java", '''    /** SDK 0.18 (plan 3c): a column tree, a grouped table with a cell dropdown and an inline cell, an empty table's state and two empty lines. */
    static ViewSpec tables(String display, boolean tunnels) {
        var c=ControlState.READY;
        var sessions=new Nodes.Tree("sessions",c,List.of(new TreeItem("lan","Local Network",Optional.empty(),false,List.of(new TreeItem("mac","Mac",Optional.empty(),true,List.of(),
            OptionalInt.empty(),false,"",Optional.of(Nodes.StatusKind.SUCCESS),List.of("192.168.1.119","22"),Optional.empty())))),Set.of("lan"),Set.of("mac"),"connect",
            List.of(new Column("name","Name",Nodes.TextRole.BODY,false),new Column("host","Host",Nodes.TextRole.MONO_SMALL,false),new Column("port","Port",Nodes.TextRole.MONO_SMALL,true)));
        var displays=List.of(new Option("card","Card",Optional.empty()),new Option("sticky","Sticky card",Optional.empty()));
        var groups=new Nodes.Table("groups",c,List.of(new Column("group","Group",Nodes.TextRole.BODY,false),new Column("display","Display",Nodes.TextRole.BODY,false)),
            List.of(TableRow.heading("jasper","Jasper",OptionalInt.of(1),"group"),new TableRow("errors",Map.of("group","Internal errors"),Optional.empty(),false,Optional.empty(),"",
                Map.of("display",new Nodes.Choice("errors.display",c,Nodes.ChoiceKind.DROPDOWN,displays,display,"Display","")),Optional.empty())),Set.of(),"edit",List.of());
        var rows=tunnels?List.of(new TableRow("web",Map.of("local","8080"),Optional.empty(),false,Optional.empty(),"",Map.of("type",new Nodes.Stack("web.type",c,Nodes.LayoutRole.INLINE,
            List.of(new Nodes.StatusDot("web.dot",c,Nodes.StatusKind.SUCCESS),new Nodes.Text("web.kind",c,"Local",Nodes.TextRole.BODY)))),Optional.empty())):List.<TableRow>of();
        var state=new Nodes.Empty("tunnels.empty",c,new Nodes.Icon("tunnels.empty.icon",c,"tunnel",Nodes.Hue.NEUTRAL,Nodes.IconSize.EMPTY,"Tunnels"),"No Tunnels",
            "Forward a local port to a remote host.",List.of(new Nodes.Button("tunnels.new",c,"New Tunnel\\u2026","",Nodes.ButtonKind.SECONDARY,"new-tunnel","")));
        var table=new Nodes.Table("tunnels",c,List.of(new Column("type","Type",Nodes.TextRole.BODY,false),new Column("local","Local",Nodes.TextRole.MONO_SMALL,false)),rows,Set.of(),"toggle",
            List.of(),Nodes.SelectionMode.SINGLE,Nodes.ScrollPolicy.STANDARD,Optional.of(state),"");
        var files=new Nodes.Table("files",c,List.of(new Column("name","Name",Nodes.TextRole.BODY,false)),List.of(),Set.of(),"",List.of(),Nodes.SelectionMode.SINGLE,
            Nodes.ScrollPolicy.STANDARD,Optional.empty(),"Not Connected");
        var keys=new Nodes.ItemList("keys",c,Nodes.ListKind.ORDERED,List.of(),Set.of(),"",List.of(),Nodes.SelectionMode.SINGLE,Optional.empty(),OptionalInt.empty(),"","","","","","No keys");
        return new ViewSpec(new Nodes.Stack("tables",c,Nodes.LayoutRole.VERTICAL,List.of(sessions,groups,table,files,keys)));
    }
    @Test void tableVocabularyMountsUpdatesAndReportsCellChoicesSortsAndTheEmptyStateAction() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(tables("card",false),events::add);
        try {
            assertThat(view.spec()).isEqualTo(tables("card",false));
            view.focus("tunnels.new");
            view.update(tables("sticky",true));assertThat(view.spec()).isEqualTo(tables("sticky",true));
            view.focus("errors.display");view.reveal("web.kind");
            emit(view,new UiEvent.Selected("errors.display",Set.of("card")));emit(view,new UiEvent.Sorted("sessions","host"));
            emit(view,new UiEvent.Selected("groups",Set.of("errors")));
            view.update(tables("card",false));emit(view,new UiEvent.Action("new-tunnel"));
            assertThat(events).containsExactly(new UiEvent.Selected("errors.display",Set.of("card")),new UiEvent.Sorted("sessions","host"),
                new UiEvent.Selected("groups",Set.of("errors")),new UiEvent.Action("new-tunnel"));
        }finally{view.close();stop(kit);}
    }); }
''')
PY
```

`GalleryCoverageTest` imports `java.util.*` and `java.lang.reflect.RecordComponent` (`GalleryCoverageTest.java:3-9`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-app:test --tests dev.jasper.app.plugins.HostedKitContractTest :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeKitTest*' --continue`
Expected: `GalleryCoverageTest.everyTableAdditionOfSdkZeroEighteenHasARealFixture` FAILS ("a tree with columns": no
fixture has one yet). The two runs of `tableVocabularyMountsUpdatesAndReportsCellChoicesSortsAndTheEmptyStateAction`
pass already (Tasks 2 to 5 built what they exercise); they are the contract's record of it.

- [ ] **Step 3: Add the fixture to the gallery**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 6 step 3: the gallery's table vocabulary fixture, last in the list so earlier contact sheets keep their cells."""
import pathlib
name = "jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java"
path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
old = '''        return List.copyOf(result);
    }
    private static Fixture panelState('''
if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {text.count(old)}")
path.write_text(text.replace(old, '''        // Plan 3c (SDK 0.18): tree columns, cell nodes with the bare DROPDOWN, heading rows and the empty states.
        result.add(new Fixture("tables-0-18", "", "normal", 640, 860, TableFixtures.create()));
''' + old), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and prove the pixel rule**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests dev.jasper.app.plugins.HostedKitContractTest :jasper-sdk-testkit:test --continue`
Expected: BUILD SUCCESSFUL. `GalleryCoverageTest` (the new test included), `KitMatrixTest` (the new fixture under the
default kit over the theme set, and no literal under `custom()`), `KitKeyBindingAuditTest` and both contract runs pass.

Then render the headless gallery here and at the branch's base, and compare. Every file of the base must be
byte-identical here except `manifest.json` and the last contact sheet; the only new files are the fixture's two and,
when the base's last sheet held eight fixtures, one new sheet:

```bash
B=$(mktemp -d) && BASE=$(git merge-base HEAD main) && git worktree add --detach "$B/base" "$BASE"
for theme in dark light; do
  (cd "$B/base" && ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/base-$theme")
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/new-$theme"
done
python3 -I - "$B" <<'PY'
import json, pathlib, re, sys
root = pathlib.Path(sys.argv[1])
for theme in ["dark", "light"]:
    base, new = root / f"base-{theme}", root / f"new-{theme}"
    old = {p.name: p for p in base.iterdir()}; now = {p.name: p for p in new.iterdir()}
    sheets = sorted((n for n in old if re.fullmatch(r"sheet-\d+\.png", n)), key=lambda n: int(n[6:-4]))
    last = sheets[-1]
    fixtures = json.loads(old["manifest.json"].read_text(encoding="utf-8"))["fixtureCount"]
    added = set(now) - set(old)
    allowed = {"tables-0-18.png", "tables-0-18.json"} | ({f"sheet-{len(sheets) + 1}.png"} if fixtures % 8 == 0 else set())
    assert not set(old) - set(now), (theme, "missing", sorted(set(old) - set(now)))
    assert added == allowed, (theme, "added", sorted(added), "allowed", sorted(allowed))
    changed = sorted(n for n in old if old[n].read_bytes() != now[n].read_bytes())
    expected = {"manifest.json"} | ({last} if fixtures % 8 else set())
    assert set(changed) == expected, (theme, "changed", changed, "expected", sorted(expected))
    manifest = json.loads(now["manifest.json"].read_text(encoding="utf-8"))
    assert manifest["fixtureCount"] == fixtures + 1, (theme, manifest["fixtureCount"], fixtures)
    print(theme, len(old), "base files;", len(old) - len(changed), "byte-identical; changed", changed, "; added", sorted(added))
PY
git worktree remove --force "$B/base"
```

Expected, for each theme: every base file byte-identical except `manifest.json` (and the last sheet when it had room),
and the added files exactly `tables-0-18.png`, `tables-0-18.json` (and the new sheet when the last was full). Open
`$B/new-dark/tables-0-18.png` and `$B/new-light/tables-0-18.png` and look at them: the Sessions tree with Name, Host and
Port over its values; the grouped table's bold group rows with "2 groups" and "1 group" at the right and "Card" /
"Sticky card" values each followed by its chevron with no border, the selected row's in the selection ink; "test
[HTTP]" and "● Running" cells; "No Tunnels" with [New Tunnel…] below the Type, Local and Remote headers; "Not
Connected" under Name, Size and Modified; and the ordered list's "No keys". Compare with the mocks named in the
header; the classic look (plan 4) is what makes them match, not this plan.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java \
  jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/TableFixtures.java jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java \
  jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java
git commit -F - <<'EOF'
SDK 0.18 tables: the shared contract and the gallery fixture

The kit contract mounts, updates and drives tree columns, cell nodes,
heading rows and the empty states on the host and the fake alike; the
gallery's last fixture shows every addition, coverage checks each one
(the in-cell DROPDOWN included), and every earlier fixture renders
byte-identically.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Documentation, STATUS and the whole build

`docs/plugin-ui.md` describes the additions where plugin authors read about building blocks; `docs/STATUS.md` records
the plan, its rulings and hand-offs; the whole build runs and its counts are recorded; the plan gets its banner.

**Files:**
- Modify: `docs/plugin-ui.md` (one bullet after "Empty states", `:173-174` at `222969926`)
- Modify: `docs/STATUS.md:2` (a new entry above the newest one)
- Modify: `docs/superpowers/plans/2026-10-15-enterprise-3c-sdk-0-18-tables.md:1` (the status banner)

**Interfaces:**
- Consumes: Tasks 1 to 6, committed.
- Produces: documentation only.

- [ ] **Step 1: Document the vocabulary**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3c Task 7 step 1: the table vocabulary in the plugin UI guide."""
import pathlib
name = "docs/plugin-ui.md"
path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
old = '''- **Empty states.** `Empty(icon, title, description, actions)`; actions may be buttons, a `MenuButton` (Choose
  Session) or none.
'''
if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {text.count(old)}")
path.write_text(text.replace(old, old + '''- **Tables and trees (SDK 0.18).** A `Tree` with `columns` shows a header: the first column titles the items, each
  later one titles one of the items' `trailing` values, and header clicks report `Sorted`, as a `Table`'s do. A
  `TableRow` may give a column a cell node (`cellNodes`) instead of a plain cell: `Text`, `StatusDot`, `Icon`, `Chip`,
  an INLINE `Stack` of those, or a DROPDOWN `Choice`, which reports `Selected` and draws without a border until
  hovered or focused. `TableRow.heading(id, title, count, countNoun)` groups the rows below it and reads "6 groups"; it
  is never selected. With no rows a `Table` shows its `empty` state (at most one action) or its `emptyText` below the
  kept headers, and an `ItemList` with no items shows its `emptyText`.
'''), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL (javadoc doclint, `verifyUiArchitecture`, `verifyApplicationArchitecture`,
`verifySdkArchitecture`, `ThemeMigrationCoverageTest` with no new row needed, `UiDocumentationTest` reading the edited
guide). Then print the totals:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = re.search(r"<testsuite\b[^>]*>", path.read_text(encoding="utf-8")).group(0)
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        totals[i] += int(re.search(rf'\b{key}="(\d+)"', suite).group(1))
print(f"{totals[0]:,} tests, {totals[1]} failures, {totals[2]} errors, {totals[3]} skipped")
PY
```

Expected: 0 failures and 0 errors, and 18 tests more than the base's count (the base is `main` after 3b; its count is
in 3b's STATUS entry): five SDK `KitValuesTest`, one model `ViewSpecTest`, one jasper-app `KitValuesTest`, eight
`TableVocabularyTest`, one `GalleryCoverageTest`, and the new kit contract test twice (host and fake); skipped as the
base. Then run the AGENTS.md source-hygiene script over every Java source; it must print nothing.

- [ ] **Step 3: Record the plan in STATUS and in this plan**

In `docs/STATUS.md`, insert after line 2 (the blank line under the title) this entry, followed by a blank line, with
the line Step 2's totals script printed in place of `TOTALS` and the first and last commit of Tasks 1-6 in place of
`FIRST..LAST`:

```markdown
**Enterprise presentation, plan 3c: SDK 0.18 tables — 2026-10-15** (`claude/enterprise-sdk-0-18-tables`, worktree
`.claude/worktrees/enterprise-3c`, `FIRST..LAST`, then this documentation commit;
[plan](superpowers/plans/2026-10-15-enterprise-3c-sdk-0-18-tables.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). SDK 0.18 gains spec 8.2's table
vocabulary, mirrored in the jasper-ui model, projected losslessly by `KitValues`, walked by both runtimes' descriptor
walks, and carried by the shared kit contract: `Tree.columns` (a header over the trailing values, header clicks report
`Sorted`), `TableRow.cellNodes` (Text, StatusDot, Icon, Chip, an INLINE stack of those, or a DROPDOWN `Choice`; one
content per column), `TableRow.heading` with `Heading` and `countNoun` ("6 groups"), `Table.empty` (one action at
most) or `Table.emptyText`, and `ItemList.emptyText`. Every former constructor stays with the new components empty.
The default look draws a cell node in its weighted column, the cell DROPDOWN bare (value then chevron, the face only
on hover, focus or while open, the selection ink while bare), a heading row as a strong title with its muted count
that keys, clicks and type-ahead never land on, the empty line, an empty table's state centred below its kept
headers, and a column tree's header over right-aligned values with a reserved count column. Zero pixel change: the
headless gallery's files for Slate Cobalt Dark and Light are byte-identical to the base but for `manifest.json`, the
last contact sheet and the new `tables-0-18` fixture. Planned deviations and rulings (plan, "Planned deviations and
rulings"): (1) the contract cases are in `KitContractTest`, not `PluginContractTest`; (2) `cellNodes` iterates in
column-id order; (3) a `countNoun` needs a count and is lowercase with no surrounding spaces, and a heading row has
nothing but its heading; (4) a heading row is BODY_STRONG with a CAPTION count and no indent below it; (5) a table's
`empty` is mounted only while it has no rows; (6) the empty line shows only with no items, one row tall, above an
ItemList's add action; (7) tree columns right-align the values, pad missing ones and reserve the count column, and
keycap-presented trees are plan 9's; (8) a muted row dims plain cells and Text cell nodes; (9) the cell dropdown is as
wide as its selected plain value; (10) no README edit. Hand-offs: plan 4's classic faces draw the same descriptors
(grid lines, the indent under headings, the mocks' header strip); plans 9 and 10 adopt them (Notifications, Keymap,
Sessions, Tunnels, File Servers, SFTP, Add Host's keys); 3e owns the default-look changes of section 13.
`./gradlew check --continue`: **TOTALS**. GUI checks for the user: none new (no plugin or page uses the vocabulary
yet). Not merged, not pushed.
```

Then insert, after this plan's first line (`# Enterprise presentation 3c: SDK 0.18 tables Implementation Plan`) and a
blank line:

```markdown
> **Status (2026-10-15): executed on `claude/enterprise-sdk-0-18-tables` (`FIRST..LAST`).** Deviations from this
> plan's text: none beyond its "Planned deviations and rulings".
```

replacing `FIRST..LAST` the same way, and naming any step whose text had to change instead of "none" (and the same in
the STATUS entry). Use the execution date in both if it is not 2026-10-15.

- [ ] **Step 4: Commit**

```bash
git add docs/plugin-ui.md docs/STATUS.md docs/superpowers/plans/2026-10-15-enterprise-3c-sdk-0-18-tables.md
git commit -F - <<'EOF'
SDK 0.18 tables: documentation and status

The plugin UI guide describes tree columns, cell nodes, heading rows and
the empty states; STATUS records plan 3c with its rulings, hand-offs and
the whole build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
