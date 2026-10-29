# Resizable and Sortable Table Columns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can drag column boundaries in every kit table and column tree, click headers to sort, and both survive a restart; the bundled plugins and the app's file chooser answer the events.

**Architecture:** `jasper-ui` gains a header drag (`ColumnResizer`) shared by tables and column trees, keeps dragged widths for the life of a view, and reports a finished drag as a new `UiEvent.ColumnsResized`. `Column` gains an optional pixel `width` that overrides weights (tables) or content sizing (trees). The SDK mirrors both and adds a value `ColumnState` (sort + widths, with a one-line encoding) and `ColumnStateFile` (a JDK `Properties` file) so each plugin persists in a few lines. Plugins keep owning order, as `UiEvent.Sorted` already requires.

**Tech Stack:** Java 25 (JBR 25 JCEF), Swing/FlatLaf in `jasper-ui`, JUnit 5 + AssertJ, SQLite (`DirectoryCache`), Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-28-table-column-resize-sort-design.md`

## Global Constraints

- SDK version becomes **0.25.0** (`JasperSdk.VERSION`); every new public SDK element carries `@since 0.25.0`. Check `main` first: if another branch already took 0.25.0, take the next minor and use it everywhere below.
- Folders sort with everything else (no folders-on-top) — user's rule, like Finder.
- Clicking the sorted column flips direction; another column sorts ascending.
- Sessions: hosts sort within their group; group order unchanged (default group last, others alphabetical).
- Widths are reported and stored in **unscaled** pixels (divide by `UIScale.getUserScaleFactor()`, apply with `UIScale.scale`).
- A plugin that ignores `ColumnsResized` lays out exactly as today except while the user drags in an open view.
- No animation. No Swing in plugins (`PluginPresentationCheck`). SDK types only in `dev.jasper.app.plugins`.
- macOS and Windows fully; Linux best-effort. Nothing platform-specific beyond `Cursor.E_RESIZE_CURSOR`.
- Run `./gradlew check` at the end of each task that touches code (headless). Never launch the GUI.
- Commits end with the Home Depot attribution and `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Review Focus

1. **A press on a boundary must never sort.** A click, or a press and release without moving, inside the boundary zone sends no `Sorted`. Covered in Task 2.
2. **Saved widths wider than the panel** (saved in a wide window, restored in a narrow dock) must not produce a horizontal scroll bar or a column below its minimum. Covered in Task 2 (table) and Task 3 (tree).
3. **The column set changes under kept widths** (SFTP switches between narrow and wide, or Enterprise and default): kept widths for unknown ids are dropped, not misapplied by position. Covered in Task 2.
4. **A corrupt or hand-edited state file** reads as "no state" and is never thrown at start-up. Covered in Task 1 (`decode`, `ColumnStateFile.get`).
5. **SFTP paging after a sort:** page 2 continues the same order and the counts stay right with hidden items off. Covered in Task 4.

---

### Task 1: Values — `Column.width`, `ColumnsResized`, `ColumnState`, SDK 0.25.0

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/model/Column.java`, `jasper-ui/src/main/java/dev/jasper/ui/model/UiEvent.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Column.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/UiEvent.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ColumnState.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ColumnStateFile.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java` (lines ~45 and ~80)
- Test (existing): `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`
- Modify (version 0.24.0 → 0.25.0, ranges `<0.25` → `<0.26`): every file `git grep -l '0\.24\.0\|<0\.25' -- ':!docs/superpowers' ':!docs/sdk-reference.html'` lists — the nine bundled `plugin.toml`s, `FakePluginHost`, the SDK tests asserting `VERSION`, the app plugin tests and `LookFixtures`, `docs/examples/external-plugin/*`, the guides and `tools/sdk-reference/guide/*.html` (add a 0.25.0 entry to `15-versions.html`).
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/ColumnStateTest.java` (new)

**Interfaces:**
- Produces (both `dev.jasper.ui.model` and `dev.jasper.sdk.ui`, identical shape):
  - `record Column(String id, String title, TextRole role, boolean numeric, double weight, Optional<Boolean> sorted, OptionalInt width)`; `width` present must be `> 0`. Keep the six-argument constructor (width empty) and the four-argument one. Add `Column withWidth(OptionalInt width)` and `Column withSorted(Optional<Boolean> sorted)`.
  - `UiEvent.ColumnsResized(String id, Map<String,Integer> widths)` — `id` the table or tree node, `widths` every column's id → unscaled width, each `> 0`, copied.
- Produces (SDK only, `dev.jasper.sdk.ui`):
  - `record ColumnState(Optional<String> sortColumn, boolean ascending, Map<String,Integer> widths)` with `static final ColumnState NONE`, `static ColumnState sortedBy(String columnId, boolean ascending)`, `ColumnState sorted(String columnId)` (same column flips, other column ascending), `ColumnState resized(Map<String,Integer> widths)`, `List<Column> apply(List<Column> columns)` (sets `sorted` on the sort column only and `width` on ids present in `widths`; ids not in `columns` are ignored), `String encode()`, `static ColumnState decode(String text)` (any malformed text → `NONE`).
  - `final class ColumnStateFile` — `ColumnStateFile(Path file)`, `ColumnState get(String key)` (missing/unreadable file or key → `NONE`), `void put(String key, ColumnState state) throws IOException` (keeps other keys; creates parent folders; writes via a temp file and `ATOMIC_MOVE`). Backed by `java.util.Properties`.

- [ ] **Step 1: Write the failing tests** in `ColumnStateTest`:

```java
@Test void sortingTheSameColumnFlipsAndAnotherStartsAscending() {
    var s = ColumnState.NONE.sorted("name");
    assertThat(s.sortColumn()).contains("name"); assertThat(s.ascending()).isTrue();
    assertThat(s.sorted("name").ascending()).isFalse();
    assertThat(s.sorted("name").sorted("size")).isEqualTo(ColumnState.sortedBy("size", true));
}
@Test void applyMarksTheSortColumnAndWidthsByIdIgnoringUnknownIds() {
    var cols = List.of(new Column("name","Name",Nodes.TextRole.BODY,false), new Column("size","Size",Nodes.TextRole.BODY,true));
    var out = ColumnState.sortedBy("size", false).resized(Map.of("name", 240, "gone", 90)).apply(cols);
    assertThat(out.get(0).sorted()).isEmpty(); assertThat(out.get(0).width()).hasValue(240);
    assertThat(out.get(1).sorted()).contains(false); assertThat(out.get(1).width()).isEmpty();
}
@Test void encodeRoundTripsAndGarbageDecodesToNone() {
    var s = ColumnState.sortedBy("modified", false).resized(Map.of("name", 200, "size", 80));
    assertThat(ColumnState.decode(s.encode())).isEqualTo(s);
    for (String bad : List.of("", "x", "sort=;widths=a:-3", "widths=name:abc", "sort=name,sideways"))
        assertThat(ColumnState.decode(bad)).as(bad).isEqualTo(ColumnState.NONE);
}
@Test void fileKeepsOtherKeysAndTreatsBrokenFilesAsEmpty(@TempDir Path dir) throws IOException {
    var file = new ColumnStateFile(dir.resolve("data/columns.properties"));
    assertThat(file.get("sftp")).isEqualTo(ColumnState.NONE);
    file.put("sftp", ColumnState.sortedBy("name", true)); file.put("sessions", ColumnState.sortedBy("host", false));
    assertThat(new ColumnStateFile(dir.resolve("data/columns.properties")).get("sftp")).isEqualTo(ColumnState.sortedBy("name", true));
    Files.writeString(dir.resolve("data/columns.properties"), "\u0000￿ not properties");
    assertThat(file.get("sessions")).isEqualTo(ColumnState.NONE);
}
```

Add to the two `KitValuesTest`s: a `Column` with `width` 180 and a `ColumnsResized("files", Map.of("name",180,"size",70))` round-trip SDK ↔ module unchanged; `new Column(..., OptionalInt.of(0))` throws `IllegalArgumentException`. Add to `KitContractTest`'s browser contract: `emit(view, new UiEvent.ColumnsResized("files", Map.of("name",200,"size",80)))` is delivered as emitted.

- [ ] **Step 2: Run them to see them fail** — `./gradlew :jasper-sdk:test --tests '*ColumnStateTest' --tests '*KitValuesTest'` → compilation failure (`ColumnState`, `width`, `ColumnsResized` missing).

- [ ] **Step 3: Implement** the records, `ColumnState`, `ColumnStateFile` and the two `KitValues` conversions (`toModule(Column)` passes `width`; the event switch maps `ColumnsResized` both ways). Encoding: `sort=<id>,<asc|desc>;widths=<id>:<n>,<id>:<n>` with either part optional; ids are `ModelChecks.id`-valid so they never contain `,;:=`. Javadoc on every new element (the `verifySdkReference` and doclint checks require it); `Column`'s Javadoc says a table column with a width takes that width and the last column absorbs the rest, and a tree's trailing column with a width takes it instead of fitting its values.

- [ ] **Step 4: Bump the SDK to 0.25.0** across the files listed above, then `./gradlew sdkReference` and include `docs/sdk-reference.html`.

- [ ] **Step 5: Run** `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests '*KitValues*' --tests '*VersionRange*' --tests '*KitCompatibility*'` → all pass.

- [ ] **Step 6: Commit** — `git commit -m "SDK 0.25.0: column widths, ColumnsResized and ColumnState"`.

---

### Task 2: Table header drag, kept widths and width-aware layout

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/ColumnResizer.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java` (`headerLabel` ~38-61, `TableControl.data` ~384-412, `weights()` ~342, `WeightLayout` ~506)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ColumnResizeTest.java` (new; reuse `KitFixtures.context()`, `Edt.run`, `InteractiveRowsTest.layout` as `TablesTest` does)

**Interfaces:**
- Consumes: `Column.width()`, `UiEvent.ColumnsResized` (Task 1, module copies).
- Produces:
  - `final class ColumnResizer` (package-private) — `static void install(ControlRegistry.Control owner, JComponent header, ColumnResizer.Target target)`; `interface Target { int[] edges(); /* x of each boundary in header coordinates, one per adjacent pair */ void drag(int boundary, int dx); void fit(int boundary); void finish(); }` — the table and the tree each implement `Target` (two real implementations). `static boolean inBoundary(JComponent title, MouseEvent e)` for `headerLabel` to skip `Sorted`.
  - `TableControl.kept` — `Map<String,Integer>` of the user's widths for this view, cleared of ids that are no longer columns on each `data()`; the effective width of a column is `kept` first, then `Column.width`, else weight layout.

- [ ] **Step 1: Write the failing tests** in `ColumnResizeTest` (a two- or three-column table, view sized 600 wide):
  - `dragOnABoundaryMovesWidthBetweenNeighboursAndReportsOnce` — press at boundary 0, drag +40 in three moves, release: column 0 is 40 wider, column 1 is 40 narrower, the others unchanged; `events` holds exactly one `ColumnsResized("files", …)` whose widths sum to the header's content width (unscaled), and no `Sorted`.
  - `pressAndReleaseOnABoundaryNeverSorts` — click (press+release, no move) inside the zone → no event at all; a click on a title's middle → `Sorted`.
  - `doubleClickFitsTheLeftColumnToItsWidestCellOrTitle` — row text "MMMMMMMMMMMMMMMMMMMM" in column 0: after a double-click on boundary 0, column 0's width ≥ `ThemePainting.measureText(font, that text)` and one `ColumnsResized` is sent.
  - `aColumnNeverShrinksBelowItsMinimum` — drag −2000: column 0 equals its `minimums` entry; column 1 grows by the same amount.
  - `specWidthsWiderThanTheViewportFitWithoutScrolling` — columns given widths 500/500/500 in a 600-wide view: the header's preferred width ≤ the viewport width, every column ≥ its minimum, the last column absorbs.
  - `keptWidthsSurviveARerenderWithoutWidthsAndDropUnknownIds` — drag, then `view.update` with the same columns and no widths → the dragged widths stay; update with a different column id set → layout returns to weights.
  - `numericColumnKeepsItsContentMinimumUnderAWidth` — a numeric column given width 10 still fits its widest plain cell (the existing rule).

- [ ] **Step 2: Run** `./gradlew :jasper-ui:test --tests '*ColumnResizeTest'` → FAIL (no `ColumnResizer`).

- [ ] **Step 3: Implement** `ColumnResizer` (zone: ±`UIScale.scale(3)` px around each edge; `E_RESIZE_CURSOR` while over a zone; listeners on the header and every title label, converting to header coordinates; send nothing until release; `finish()` emits once) and the table's `Target`. Replace `weights()` with a layout that gives fixed-width columns (kept or spec, scaled, floored at `minimums[i]`) their width, shares the rest by weight among unfixed columns, and makes the last column absorb any remainder; when fixed widths exceed the space, shrink the non-last fixed columns proportionally down to their minimums. Rows (`row.title.setLayout`) use the same layout as the header.

- [ ] **Step 4: Run** `./gradlew :jasper-ui:check` → PASS (includes `TablesTest`, `ClassicTablesTest`, architecture checks).

- [ ] **Step 5: Commit** — `git commit -m "Kit tables: drag column boundaries to resize"`.

---

### Task 3: Column-tree header drag

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java` (`data()` ~384-412, `columnHeader()` ~431-465, `ColumnHeaderLayout`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ColumnResizeTest.java` (tree cases)

**Interfaces:**
- Consumes: `ColumnResizer.install` / `Target` (Task 2).
- Produces: `TreeControl.kept` with the same rules as tables. A trailing column's effective width is kept, then `Column.width`, else today's content fit (`trailingWidths`); the first column takes the remainder. A laned tree (`laned()`) installs no resizer.

- [ ] **Step 1: Write the failing tests:**
  - `treeDragShrinksTheTrailingColumnAndTheTitleColumnTakesTheRest` — tree with columns name/host/port; drag boundary 0 by +30: host's width −30, port unchanged, one `ColumnsResized("rows", …)` with all three ids.
  - `treeDoubleClickReturnsATrailingColumnToItsContentFit` — after a drag, double-click boundary 0 → host's width equals the width with no width set.
  - `treeWithoutWidthsLaysOutExactlyAsBefore` — header title x positions with no widths equal those captured from today's layout (compute both in the test via a tree whose `Column`s have no width, before and after an unrelated update).
  - `tooWideTrailingWidthsKeepTheTitleColumnAtItsMinimum` — widths host 900, port 900 in a 400-wide view: no horizontal scroll, the title column ≥ its minimum.
  - `lanedTreeHasNoResizeZone` — a laned tree's header shows the default cursor at a boundary and a press there reports nothing new.

- [ ] **Step 2: Run** `./gradlew :jasper-ui:test --tests '*ColumnResizeTest'` → the tree cases FAIL.

- [ ] **Step 3: Implement** the tree's `Target` and the effective-width rule in `data()` / `ColumnHeaderLayout` and the row trailing layout. Minimum of a trailing column: its title's preferred width.

- [ ] **Step 4: Run** `./gradlew :jasper-ui:check` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "Kit column trees: drag column boundaries to resize"`.

---

### Task 4: SFTP — whole-folder sort, widths, persistence

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/DirectoryCache.java` (`page(...)` ~55-61)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpController.java` (page requests ~156, ~167)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java` (`handle` ~286-303, `table()` ~483, `classicTable()` ~252)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (next to `hiddenItems` ~89), `DestinationPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (~170, ~203)
- Create: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/DirectoryCacheTest.java` (uses `@TempDir`)
- Test (existing): `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpPanelTest.java`

**Interfaces:**
- Consumes: `ColumnState`, `ColumnStateFile`, `UiEvent.ColumnsResized` (SDK, Task 1).
- Produces:
  - `DirectoryCache.Order` — `record Order(String column, boolean ascending)` with `static final Order DEFAULT = new Order("name", true)`; columns `name`, `size`, `modified`, `permissions`; unknown → name. `page(long offset, int limit, boolean foldersOnly, boolean showHidden, Order order)`; the existing overloads use `Order.DEFAULT`. Ordering ignores `rank` (folders mixed); ties break by `name COLLATE NOCASE, name`.
  - `SftpUi.columns(ColumnStateFile file)` — shared by every SFTP browser and destination panel. Keys: `sftp` (narrow and destination), `sftp-wide`, `sftp-classic`, `sftp-classic-wide`. The sort (column + direction) is shared by all four keys; widths are per key. A sort click in any browser re-queries page 0 in every open browser.

- [ ] **Step 1: Write the failing tests:**
  - `DirectoryCacheTest.ordersByTheChosenColumnWithFoldersMixedIn` — entries `b.txt`(10 B), `A`(dir), `c`(dir, 5 B), `a.log`(99 B): `name` ascending → `A, a.log, b.txt, c`; `size` descending → `a.log, b.txt, c, A`(sizes 99,10,5,0).
  - `DirectoryCacheTest.secondPageContinuesTheOrderWithHiddenItemsOff` — 450 entries incl. dot-files, `size` descending, `showHidden=false`: concatenating `page(0,200,…)`, `page(200,200,…)`, `page(400,200,…)` equals the full visible list sorted the same way and its size equals `count(false,false)`.
  - `SftpPanelTest.sortClickMarksTheColumnAndAsksForPageZeroInThatOrder` — emit `Sorted(FILES,"size")` → the next table's `size` column has `sorted` = `Optional.of(true)` and the controller's page callback received offset 0 with `Order("size", true)`; again → `false`.
  - `SftpPanelTest.resizedWidthsAreSavedUnderTheLayoutsKeyAndAppliedOnTheNextRender` — wide layout: emit `ColumnsResized(FILES, {name:300,modified:120,permissions:90,size:70})` → `columns.properties` holds key `sftp-wide` with those widths and the next spec's columns carry them; the narrow table's widths are unchanged.
  - `SftpPanelTest.restoredStateAppliesOnFirstRender` — a pre-written `columns.properties` with `sftp=sort=size,desc;widths=name:260,size:80` → the first loaded narrow table's `size` column shows `sorted = Optional.of(false)`, both columns carry those widths, and the first page was requested with `Order("size", false)`.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-remote:test --tests '*DirectoryCacheTest' --tests '*SftpPanelTest'` → FAIL.

- [ ] **Step 3: Implement.** `RemotePlugin` creates `new ColumnStateFile(context.dataDirectory().resolve("columns.properties"))` once and hands it to `SftpUi.columns` (and Task 5). Write failures log at WARNING, as `saveSftpShowHidden` does. Replace "folders first" in `SftpPanel`'s class Javadoc.

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-remote:check` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "SFTP: sort the whole folder by any column and remember widths"`.

---

### Task 5: Sessions — tree sort and widths, default-look sort menu

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostRows.java` (`rows(...)` ~60-80 and the WSL overload)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java` (`handle` ~222-239, `rowsHeader()` ~362, `TREE_COLUMNS` ~599, `classicTree()` ~623)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (~872-882)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/HostRowsTest.java`, `HostsPanelTest.java` (existing; add cases)

**Interfaces:**
- Consumes: `ColumnState`, `ColumnStateFile` from Task 4's `RemotePlugin` wiring.
- Produces:
  - `HostRows.Order` — `record Order(String column, boolean ascending)`, `DEFAULT = new Order("name", true)`; `name` (case-insensitive, then exact), `host` (hostname case-insensitive), `port` (numeric); ties by name. New overloads of both `rows(...)` taking a trailing `Order`; old ones use `DEFAULT`. Only hosts within a group reorder; groups and WSL distributions keep their order.
  - `HostsPanel.setColumns(ColumnState state, Consumer<ColumnState> changed)` — key `sessions`. Sort shared by both looks; widths apply to the classic tree only.
  - Default look: `rowsHeader()`'s `primary` becomes `Nodes.MenuButton("sort", READY, "", <an existing IconRegistry icon, e.g. "chevron.down">, GHOST, "", items, "Sort sessions")` with items `sort:name` "Name", `sort:host` "Host", `sort:port` "Port"; the current column's item shows icon `chevron.up` (ascending) or `chevron.down` (descending), the others no icon. `UiEvent.Action("sort:<id>")` and `UiEvent.Sorted(ROWS, id)` go through the same `ColumnState.sorted`.

- [ ] **Step 1: Write the failing tests:**
  - `HostRowsTest.sortsHostsWithinGroupsAndKeepsGroupOrder` — groups `prod` and default with hosts on ports 22/2222/80: `Order("port", false)` → group rows in today's order, each group's hosts by port descending.
  - `HostsPanelTest.sortMenuOffersThreeColumnsAndMarksTheCurrentOne` — default look: the header's `primary` is a `MenuButton` with ids `sort:name`, `sort:host`, `sort:port`; after `Action("sort:host")` twice the `sort:host` item's icon is `chevron.down` and hosts are host-descending.
  - `HostsPanelTest.classicHeaderClickAndDefaultMenuShareOneSort` — `Sorted(ROWS,"port")` in the classic look, then switch to the default look → rows ordered by port and the `sort:port` item marked; the `changed` callback received `sortedBy("port", true)`.
  - `HostsPanelTest.classicTreeAppliesAndReportsWidths` — `setColumns(state with widths host:140)` → `classicTree()` column `host` has width 140; `ColumnsResized(ROWS, …)` → `changed` called with those widths.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-remote:test --tests '*HostRowsTest' --tests '*HostsPanelTest'` → FAIL.

- [ ] **Step 3: Implement**, wiring `RemotePlugin` to `columnStateFile.get("sessions")` / `put` (log WARNING on failure). Every `HostsPanel` instance (one per window) re-renders on a change.

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-remote:check` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "Sessions: sort by name, host or port in both looks"`.

---

### Task 6: Tunnels, File Servers, Indexer, Vault

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java` (~126, ~163-165, ~282-288) + its wiring in `RemotePlugin`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java` (~173-179, ~363), `ui/ServerEditor.java` (~384, event switch ~173-214), `FileServerPlugin.java` (~145-155)
- Modify: `plugins/indexer/src/main/java/dev/jasper/indexer/ui/IndexerSettingsPanel.java` (~139-143 and its event handling), `IndexerPlugin.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java` (~157-176, its `sortColumn`/`ascending` fields), `VaultPlugin.java`
- Test: each panel's existing test class (`TunnelsPanelTest`, `FileServersPanelTest`, `ServerEditorTest`, `IndexerSettingsPanelTest`, `ManagerPanelTest`; find with `git grep -l "class <Panel>Test"`)

**Interfaces:**
- Consumes: `ColumnState`, `ColumnStateFile` (Task 1). Each plugin owns `data/columns.properties`. Keys: `tunnels`, `servers`, `server-keys`, `projects`, `vault`.
- Produces: per panel, `setColumns(ColumnState state, Consumer<ColumnState> changed)` (constructor parameter instead where the panel has no setters today). Vault replaces its `sortColumn`/`ascending` fields with a `ColumnState` and keeps its current default order.

Sort rules (builder fills the comparators): text case-insensitive then exact; ports, sizes and counts numeric; times by instant; status/state by its displayed text; ties by the row's name, then id. Before any sort, each table keeps today's order and shows no arrow.

- [ ] **Step 1: Write the failing tests**, one pair per panel:
  - `<Panel>Test.headerClickSortsAndFlips` — emit `Sorted(<table id>, <a non-first column>)` → rows reorder by it ascending and that column shows `sorted = Optional.of(true)`; again → descending.
  - `<Panel>Test.resizeIsReportedAndApplied` — emit `ColumnsResized` → `changed` receives the widths and the next spec's columns carry them.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-remote:test :jasper-plugin-fileserver:test :jasper-plugin-indexer:test :jasper-plugin-vault:test` → the new cases FAIL.

- [ ] **Step 3: Implement** and wire each plugin's `columns.properties`.

- [ ] **Step 4: Run** `./gradlew check` → PASS (includes `verifyPluginArchitecture`'s presentation check).

- [ ] **Step 5: Commit** — `git commit -m "Tunnels, File Servers, Indexer and Vault: sortable, resizable columns"`.

---

### Task 7: File chooser widths and sort persistence

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java` (`Chooser` record ~77, read ~169-171, write path)
- Modify: `jasper-app/src/main/java/dev/jasper/app/files/ChooserPrefs.java`, `ChooserModel.java` (`sortBy` ~222), `FileChooserPanel.java` (~148, `Sorted` ~455)
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java`, `jasper-app/src/test/java/dev/jasper/app/files/FileChooserPanelTest.java` (existing)

**Interfaces:**
- Consumes: module `UiEvent.ColumnsResized`, `Column.width` (Task 1).
- Produces: `UiState.Chooser(List<String> recent, List<String> pinned, boolean preview, boolean showHidden, String sortColumn, boolean sortAscending, Map<String,Integer> widths)`; TOML keys under `[chooser]`: `sort = "name"`, `sort_ascending = true`, `[chooser.widths]` table of ints. Missing or malformed → today's default sort and no widths. `ChooserModel.sortBy(Column column, boolean ascending)` for restoring.

- [ ] **Step 1: Write the failing tests:**
  - `UiStateTest.chooserSortAndWidthsRoundTrip` — save `sort="size"`, descending, widths `{name:320,size:90}`, reload → equal; a file with `sort = 7` and `widths = "x"` loads today's defaults.
  - `FileChooserPanelTest.sortAndResizeAreRememberedForTheNextChooser` — emit `Sorted("files","modified")` and `ColumnsResized("files", {...})`, close, open a new `FileChooserPanel` on the same `UiState` → the listing is modified-ordered and the columns carry the widths.

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*UiStateTest' --tests '*FileChooserPanelTest'` → FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-app:check` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "File chooser: remember sort and column widths"`.

---

### Task 8: Docs and status

**Files:**
- Modify: `docs/plugin-authoring.md` (tables section: `Sorted`, `ColumnsResized`, `ColumnState`/`ColumnStateFile` with a short example; link SDK anchors `sdk-reference.html#dev.jasper.sdk.ui.ColumnState` etc.), `tools/sdk-reference/guide/` page that covers tables if any, then `./gradlew sdkReference`
- Modify: `plugins/sample` if `docs/plugin-authoring.md`'s compiled examples live there (the guide's examples are compiled; add the example there)
- Modify: `docs/STATUS.md` (entry: what landed, GUI checks pending on macOS and Windows: drag a boundary, double-click, sort, restart)
- Modify: user guides that describe the Sessions and SFTP tool windows (find with `git grep -ln "SFTP\|Sessions" docs/*.md`): one sentence each that headers sort and resize, and the Sessions sort menu.

- [ ] **Step 1:** Write the docs; follow AGENTS.md's documentation rules (smallest accurate edit).
- [ ] **Step 2:** Run `./gradlew sdkReference check` → PASS, and `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip` → PASS.
- [ ] **Step 3: Commit** — `git commit -m "Docs: resizable and sortable columns"`.
