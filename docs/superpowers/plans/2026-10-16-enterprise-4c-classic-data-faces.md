# Enterprise presentation 4c: the classic data faces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jasper-ui gains the data half of spec 6.4's face pairs, each with its default and classic implementation: the
table (a transparent header, or a header band with column separators, zebra rows and heading bands), the tree (today's
tree, or ▶/▼ triangles, a 24 px indent and medium category rows), the empty state (the tile, or a bold line, a sentence
and one button), the tag (a borderless tint, or an uppercase label in a 1 px box), the route (icon tiles, or edged
cells), and the ordered list (per-row commands, or a side column acting on the selection); then the full kit matrix
(the Enterprise fixture kit over the theme set, the complement kit and a strength-2 covering array over five palettes,
`marked()` and `custom()` under the classic look) and an opt-in comparison of classic gallery renders with the Enterprise
mocks. The default look stays byte-identical.

**Architecture:** As in plan 4b: a face is a package-private interface in `dev.jasper.ui.swing` extending `Face`, with
one default and one classic class, built once per `KitContext` by `KitFaces.of(kit)` and read by its control through
`context().faces()`. Each default face calls the control's former code, moved into a named method or kept in place behind
a hook whose default is today's value, so the default look paints exactly what it painted. The table and tree points
share `HeaderFace` (the header band, the box and the hover) and the classic `ClassicHeaders` painter. The classic
ordered list keeps its rows in the list's own scroll pane (`ItemLists.ListPane`) and places a side column of real kit
buttons beside them, adopting a following entry row from a VERTICAL parent so the column spans both (spec 6.4's
composition rule). The kit matrix renders the gallery and six new Enterprise fixtures (the mocks' compositions as
descriptors) under the fixture kits; `tools/visual/check_pairs.py` compares their 2× renders with the mocks.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ for the mechanical edit scripts (always `python3 -I`), Pillow and NumPy for the opt-in
comparison tools (as `tools/visual/README.md` already requires).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2, 6.4, 7.1 to 7.3, 7.6, 16.1.2, 16.1.3, 16.1.9, 17 and 19.1 row 4), with
[DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) §5 to §8 and the mocks in
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (New Tunnel – Local, Add Host – Default, New
File Server – FTP, Settings 03 Notifications, 07 Keymap, 15 Project Index, Workstation – Connected). The values this plan
reads come from [plan 4a](2026-10-16-enterprise-4a-classic-catalog.md) (roles, catalogs, `ControlVariants`,
`ColorSlots`, the fixture kits; its "Mock measurements"), and the seam from
[plan 4b](2026-10-16-enterprise-4b-classic-form-faces.md) (`Face`, `KitFaces`, `ThemePainting.role` and `painted`,
`ClassicGlyphs`, `KitFixtures.capture`, `PaintSpecTest` and `face-pairs.tsv`). The table vocabulary is plan 3c's
(`docs/superpowers/plans/2026-10-15-enterprise-3c-sdk-0-18-tables.md`, on branch `claude/enterprise-sdk-0-18-tables`).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-4`, branch
`claude/enterprise-classic-catalog`, continuing after plan 4b's last commit. Before Task 1 check that 4b is complete:

```bash
git log --oneline -1 --grep 'Classic form faces: documentation and status'      # prints 4b's last commit
grep -c 'final PromptFace prompt;' jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java   # 1
grep -c 'TableRow heading(' jasper-ui/src/main/java/dev/jasper/ui/model/TableRow.java          # 3c: 1
ls jasper-ui/build/plan4-base/dark-1x | head -1                                                 # the gallery base exists
```

If the base directory is gone, re-create it from 4a's first commit's parent with plan 4a Task 1 Step 1 (plans 4a and 4b
changed no default pixel, so the current branch head renders the same base). Run every command from the worktree root.
Every quoted anchor is post-3c and post-4b text: the Tables, Trees, InteractiveRows and ItemLists anchors are quoted
as plan 3c's Tasks 3 to 5 leave them, the `KitFaces`, `Stacks`, `Buttons` and `ClassicButtonFace` anchors as plan 4b's
Tasks 1 to 7 leave them. The edit scripts stop with a message when their text is not found the stated number of times;
re-find the quoted text and re-run, never edit around a missing anchor.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run, at either scale and with either kit.
- Work only in the worktree above on `claude/enterprise-classic-catalog`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look does not change. Every PNG and JSON file the headless gallery writes for Slate Cobalt Dark and Light at scale 1 with the default kit is byte-identical to plan 4a's base (`jasper-ui/build/plan4-base/`); the existing `tools/visual/pairs.json` entries do not change. New output appears only under `jasper-ui/build/classic-faces/` (tests' inspection captures), `jasper-ui/build/gallery/enterprise-light-1x/` and `-2x/` (the opt-in Enterprise gallery, Task 5) and the comparison's own output directory; none of it is committed. Tasks 1 to 5 each prove it; Task 6 proves it once more.
- Faces never reference `Kit`, `ControlVariants` or (plan 5's) `ChromeSpec`; only `KitFaces` reads the variants outside the kit package; the reconciler references control classes only, never a face (spec 3.3, 6.4, 16.1.7; 4b's `StrategyIsolationTest` and `VariantReadTest`).
- A default face's implementation is the control's former code, moved into a named method and called unchanged, or a hook on the control whose default returns today's value; renderers never branch on a look (spec 3.6), they ask their face.
- Each face declares every pair it repairs (spec 7.3); a classic face paints each declared pair with `ThemePainting.painted`; the six default faces of this plan declare none.
- Colours come from the theme only (`ColourLiteralScanTest`); classic glyphs (triangles, the route's arrow) are drawn shapes in theme roles, never characters or literals.
- No interface without two real implementations (AGENTS.md): every face interface here, and `HeaderFace`, has its default and classic classes.
- jasper-ui's package graph is unchanged (`dev.jasper.ui.swing` already reads `dev.jasper.ui.kit`); `verifyUiArchitecture` stays green.
- Javadoc doclint fails the build on errors: jasper-ui runs `-Xdoclint:all,-missing`, jasper-app `-Xdoclint:all`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- Every new candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`); this plan creates no file there (it edits jasper-app's existing `PaintedContrastTest` only), so it adds no row.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; `…`, `▶`, `▼`, `→`, `›` and `⌘` are ordinary characters and allowed in strings and comments.
- The identifier word "hero" stays reserved for `Form.hero` (3a's `PromptVocabularyTest`).

## Review Focus

1. A grouped table (Settings › Notifications) driven by keyboard and mouse: a heading row takes no selection or hover
   fill, a press on it selects nothing, its band is never striped, and the rows under it are indented without
   misaligning the later columns from their titles. Task 1:
   `ClassicTablesTest.aGroupedTableBandsItsHeadingsIndentsItsRowsAndStripesNothing`.
2. An ordered list with nothing selected, or its first or last row selected, or a row the host just removed: Remove is
   disabled with no selection, Move Up at the top and Move Down at the bottom, and no command emits for a row that is
   gone. Task 3: `ClassicOrderedTest.theSideColumnActsOnTheSelectionAndDisablesAtTheEnds` and
   `aRowTheHostRemovedIsNoLongerTheTarget`.
3. A palette whose borders equal their surfaces (Catppuccin's `border.control` on `surface.input`, the degenerate
   "borders on the surfaces"): the table and tree box, the route strip, the ordered list's box and the tags' boxes stay
   visible, each through its guard. Tasks 1 to 3: the painted-pair tests over `KitFixtures.themes()` and `degenerate()`.
4. A route of two to five steps at narrow and wide widths: the cells share the strip equally, each step stays inside its
   cell (truncated, never overlapping), and every arrow ends before the next cell's hue edge. Task 2:
   `ClassicIndicatorsTest.routeCellsShareTheStripAndArrowsEndBeforeTheNextEdge`.
5. An empty state with two actions under the classic look, updated by its host: one button shows, the tile is gone, and
   the second action stays hidden after the update (and reappears under the default kit). Task 2:
   `ClassicIndicatorsTest.aClassicEmptyStateIsABoldLineASentenceAndOneButton`.

## Rulings

Decisions where the spec is silent, taken on the user's behalf (2026-10-16): "what — why — cost if wrong". Record them in
`docs/STATUS.md` (Task 6) and in the banner when executed.

1. **The table and tree points share one interface for what they have in common, `HeaderFace`** (the header's titles,
   insets and band, the box, the hover), and the classic faces share the painter `ClassicHeaders` — the mocks draw the
   Keymap tree's header exactly like the Notifications table's; each point still has its own face, so the complement kit
   can mix them — cost if wrong: none.
2. **The classic table and tree draw a 1 px `border.control` box, guarded against the dialog surface slot, unless they
   lie in a panel body (`Panels.Body`)** — the Settings and dialog tables are boxed in every mock, the Workstation's panel
   tables are not (their panel draws the rules) — cost if wrong: one box too many or too few in an unusual host.
3. **Zebra rows stripe an ungrouped table's odd rows (`surface.zebra`); a grouped table (any heading row) stripes none and
   bands its heading rows in `surface.tableHeader` with no hairline above them; trees are not striped; ordered lists are**
   — spec 5.2 says "zebra rows"; the Notifications mock bands groups instead, the Workstation SFTP table and Project
   Index's markers stripe, the Keymap and Sessions trees do not — cost if wrong: Project Index's Projects table (drawn
   unstriped) is a recorded residual.
4. **Header titles are `text.strong.secondary` through the text guard against `surface.tableHeader`, in the CAPTION face
   (11 px under `TypeScale.CLASSIC`)** — the mocks draw `#4A4F57`, between `text.strong.secondary` (`#3A3D42`) and
   `text.secondary` (`#5C6370`); the darker role keeps the titles readable — cost if wrong: a slightly darker title
   (recorded residual).
5. **Every title and cell after the first column is inset by the separator and `ICON_GAP`** (the mocks: 9 to 10 px after
   each separator), as a `Tables.CellInset` border over the cell control's own; a cell dropdown takes the same inset, so
   its value still starts under its title (3c's rule) — cost if wrong: none.
6. **A grouped table indents its data rows' first cell by `GROUP_ROW_INDENT` (14, a new role equal in both catalogs and
   read only by the classic table face)** — the Notifications mock indents the rows under "Jasper" by 14; the later
   columns keep their positions under their titles — cost if wrong: one role.
7. **A heading row's count stays where 3c puts it, at the row's trailing inset, in `text.secondary` through the text guard
   against `surface.tableHeader`** — the mock draws it under the last column's start; moving it needs a column-aware
   heading layout that plan 9's Notifications page can ask for — cost if wrong: a recorded residual.
8. **The classic tree's disclosure is the existing embedded expand button showing a drawn ▶ or ▼ (`DISCLOSURE_GLYPH`) in
   `text.secondary` through the indicator guard against `surface.card` (the row's ink on a selected row), as wide as the
   glyph and `DISCLOSURE_GAP` less the row's icon gap; children are indented `TREE_INDENT` per depth; branch titles are
   BODY_STRONG** — the Keymap mock: the glyph 6 × 5, text 16 after the glyph, children 24 deeper; the keycap trees the
   app presents (Keymap) keep their own geometry until plan 9 — cost if wrong: a few pixels in the indent.
9. **The classic hover fill is `surface.rowHover` for tables and trees only**; lists keep the default hover overlay until
   their compositions (plans 9 and 10) — spec 7.1 names row hover, and lists are not one of this plan's points — cost if
   wrong: a list's hover one shade off.
10. **The classic empty state shows no tile, its title (PANEL_TITLE, `text.primary`) and description (`text.secondary`)
    through the text guard against the dialog surface slot, and its first action only; later actions keep their
    controls but are not placed in the view** — spec 5.2: "at most one button"; DESIGN_LANGUAGE §8 — cost if wrong: a
    second empty-state action is unreachable under the classic look until a composition supplies one action (plan 10).
11. **The classic tag is the chip's label in upper case on the default tint, inside a 1 px box in the hue's
    `tag.border` role (structural guard against the hue surface), its text in the hue through the text guard; Hue.NONE
    takes the neutral roles; a chip on a selected row keeps the default inked colours and boxes them in the ink** —
    spec 5.2 and 7.1, the Workstation "HTTP" chip — cost if wrong: none.
12. **The classic route is one boxed strip (`border.control`, control guard, on `surface.input`) of equal cells, each led
    by a 3 px edge in its step's leading Icon hue (indicator guard) with the icon hidden, its caption upper case in
    `text.secondary` (text guard), and a drawn → in `text.placeholder` at the end of every cell but the last; the steps
    lay out their texts alone (the icon is given no room) and the captions keep their styling through refreshes by 4b's
    `TextControl.styled`; `ROUTE_HEIGHT` stays 42** — the New Tunnel mock (cells 159 wide, the arrow flush before the next edge, the strip 44
    tall) — cost if wrong: 2 px of strip height (recorded residual).
13. **The classic side column lives in the ordered list's own scroll pane (`ItemLists.ListPane` with
    `ClassicOrderedFace.SideLayout`), top-aligned, `SIDE_COLUMN_WIDTH` wide and `SIDE_COLUMN_GAP` from the box; when the
    list's VERTICAL parent holds an INLINE stack right after it, that entry row is adopted into the pane under the box, so
    the column spans both** — spec 6.4's composition rule and the Project Index mock; a FormRow control (Add Host's keys)
    has no entry row — cost if wrong: an entry row that is not the list's sibling is not spanned.
14. **The side column acts on the focused row while it is selected, else on the first selected row; Remove, Move Up and
    Move Down are always shown, disabled when they cannot act; the selection shows without focus** — spec 5.2 lists all
    four buttons (the Add Host mock shows only Add Key… and Remove, a recorded residual); the default ordered list shows
    its selection only while focused, which a side column would hide whenever a button takes focus — cost if wrong: two
    extra buttons in Add Host.
15. **The ordered list's add action leads the side column as a standard button, without its glyph, a LINK included, with
    `text.primary` through the classic button face's label pair** — spec 6.4, "A LINK addAction in a side column draws as
    a standard button" — cost if wrong: none.
16. **The classic index column is MONO_CAPTION (the data face) in `text.tertiary` through the text guard against
    `surface.input`; the classic box is square (`ORDERED_RADIUS` 0) and its rows have no dividers** — spec 5.2 "a muted
    data-face index column"; the Project Index mock — cost if wrong: none.
17. **`PaintedContrastTest` (jasper-app) checks that every pair the classic faces declare meets its guard as painted over
    the theme set, Enterprise Light and the degenerate palettes; sampling each pair in pixels is the per-face tests' job
    in jasper-ui (plans 4b and 4c)**, which have the package access to find each painted part — spec 16.1.2 — cost if
    wrong: one more test class that duplicates the samplers.
18. **`marked()`'s mapping check runs on the six Enterprise fixtures under the Enterprise kit: every 3 × 3 block of one
    colour is a role, a derived role, a guard-painted declared pair, black or white, or lies within 3 levels of the
    segment between two of them (a translucent fill, a gradient or a disabled fade)** — spec 16.1.3's "every classic pixel
    maps to a role"; antialiased text and edges never fill a 3 × 3 block, and a blend is what a translucent role paints —
    cost if wrong: a three-way blend would need a wider rule.
19. **The covering array is checked in as `jasper-ui/src/test/resources/dev/jasper/ui/gallery/control-covering-array.tsv`:
    row 0 all default, the 14 columns the first 14 four-of-six subsets of rows 1 to 6 in lexicographic order** — any two
    distinct four-subsets of six rows share at least two rows and each has a row the other lacks, so with row 0 every pair
    of columns shows 00, 01, 10 and 11 (strength 2 in seven rows); the test re-checks the property — cost if wrong: none.
20. **The Enterprise Light fixture theme (`KitFixtures.enterpriseLight()`) is Slate Cobalt Light with spec 7.6's named
    roles and its eight overrides; every role the table does not name keeps Slate Cobalt Light's value** — the ΔL*-keeping
    derivation of the unnamed neutrals belongs to the shipped theme (plan 5); the fixture exists for the matrix and the
    mock comparison — cost if wrong: unnamed neutrals one shade off in the comparison.
21. **The mock comparison is opt-in: `-Pjasper.galleryKit=enterprise` renders the gallery and the Enterprise fixtures
    with the Enterprise fixture kit over Enterprise Light (Light only; Enterprise Dark is plan 5's) into
    `jasper-ui/build/gallery/enterprise-light-<scale>x/`, and `tools/visual/check_pairs.py` passes a pair when its edge
    agreement reaches the pair's `min_agreement` (0.30 for whole dialogs, 0.40 for tables and lists, as fonts differ
    until plan 5 registers Plex; a dry run of the six fixtures with the default catalog and faces measured 0.21 to 0.47) and each listed colour fills a 2 × 2 block within ΔE76 6 in both images** — spec 16.1.4
    keeps the comparison opt-in — cost if wrong: floors tuned once in Task 5 Step 7.
22. **Unlock Vault is not compared yet**: the classic prompt's title belongs to the dialog header strip (plan 6), so the
    body alone would compare badly — cost if wrong: none; plan 6 adds the pair.
23. **The host's `HostedKitContractTest` runs under the Enterprise kit from plan 5**, when the host can be started with a
    kit; this plan's `ClassicContractTest` checks spec 16.1.9's events (checkbox, radio, folder tab, side column,
    read-only field) under both shipped kits in jasper-ui — cost if wrong: none.
24. **`KitMatrixTest` renders the Enterprise fixtures with the gallery under the Enterprise, complement and covering-array
    kits only**; the default kit's matrix keeps rendering the gallery alone, so its run is unchanged — cost if wrong: none.

## Verified facts

Read at `222969926` with 3a's renames (3a's branch head `deafed424`), plan 3c's text (branch
`claude/enterprise-sdk-0-18-tables`, `1b0e88eb1`) and plans 4a and 4b. Line numbers are from `deafed424`.

1. Plan 3c Task 3 Step 3 replaces `Tables.java` whole: `TableControl` holds `final JPanel header = new JPanel();` (set as
   the scroll's column header view, non-opaque, in the constructor, which ends `scroll.getColumnHeader().setOpaque(false);`
   then `refresh();`), `data()` starts `nodeRows.clear();`, adds `headerLabel(this, "@table/", column)` per column, and
   pads the header with `BorderFactory.createEmptyBorder(pad, side, pad, side)` (`pad` = `ICON_GAP`); `populate` casts
   each title child to `JComponent cell`, calls `choice.inCell(row.inked ? ink() : null)` on a dropdown cell, and
   `heading(row, data, heading)` mounts the count as `prefix + "/count"` (CAPTION) in the trailing slot. 3c Task 4 adds
   `emptyText`, `fillsViewport`, `childParent`, `children` and `layoutAccessories` before the comment "A table keeps Enter
   with no focused row, as the JTable it replaced did (its Enter moved the selection)."
2. `InteractiveRows.RowsControl.selectionShown` shows an ORDERED list's selection only while its canvas has focus
   (`InteractiveRows.java:33-38`); `identityRows()` is at `:335`; `sync()` ends by calling `layoutAccessories(width)`
   (`:450`); `Canvas.paintComponent` paints the keycap detail card, then the ORDERED box and dividers (`:755-764`), then
   every mounted row's fill (`:766`); `Canvas.getPreferredSize` follows at `:768` (3c adds the empty line's height);
   `Row.paintFill` uses `ColorMath.opacity(HOVER_OVERLAY, .05)` for hover and keyboard focus (`:818-829`);
   `Row.paintComponent` paints a hairline above a non-data row after the first (`:1218-1228`).
3. `Trees.TreeControl` (`Trees.java:77-415`): the constructor sets `((TreePane)scroll).owner=this;` (`:86`);
   `uniformRows()` at `:125`; `populate` indents `entry.depth() * UIScale.scale(16)` (`:304`), builds a GHOST expand
   button with presentation `presentation.isEmpty() ? STANDARD : EMBEDDED` (`:306-317`), slots the leading nodes
   (`:328`), titles a branch BODY unless emphasized (`:337`) and binds the expand button to `UiEvent.Expanded`
   (`:396-413`). Plan 3c Task 5 adds `final javax.swing.JPanel header = new javax.swing.JPanel();`, `columnHeader()`
   (titles from `Tables.headerLabel(this, "@tree/", columns.get(i))`, border
   `javax.swing.BorderFactory.createEmptyBorder(pad, side, pad, side)`), `ColumnHeaderLayout`, `rowPad()` and
   `trailingColumns`.
4. `ItemLists.ListControl` (`ItemLists.java:18-524`): the constructor begins `super(view, node);` (`:26-27`); ItemLists
   imports `java.awt.Container` only from AWT (`:9`); `children` mounts the add action as the canvas footer, an ORDERED
   one with `orderedAction` (`:381-390`); `populate` adds the ORDERED index (CAPTION) and the up, down and remove
   commands (`:399-411`), sets the index's `columnWidth` (`:412-415`) and binds the commands (`:452-464`);
   `internalId` at `:484`; no `layoutAccessories` override.
5. `Stacks.StackSurface` paints a route through the private `paintRoute(Graphics)` (`Stacks.java:26`, `:31-41`); plan 4b
   Task 2 adds its `Nodes.LayoutRole role` field before the constructor. `StackControl.refresh` picks
   `new RouteLayout(this)` for ROUTE (`:99`); `children` ends with the `barTexts` collection (`:148-151`), and plan 4b
   Task 7 adds the prompt face's call at its start. `RouteLayout` (`:322-355`) lays equal columns with the arrow at each
   later column's start; `RouteStepLayout` (`:358-395`) leads with a visible icon-only `JLabel`.
6. `ViewReconciler.resetComposition` resets the button flags, `orderedAction` among them (`ViewReconciler.java:96-103`);
   `applyRoute(node, next, tiles)` marks route steps before the update and tiles each step's leading Icon after it
   (`:297-308`, called at `:39` and `:50`).
7. `EmptyStates.EmptyControl` sizes the tile with `tileSize()` (`EmptyStates.java:45`), spaces it with `gap(index)`
   (`:50-57`), styles title and description in `refresh` (`:60-80`) and mounts tile, title, description and actions in
   `children` (`:87-99`).
8. `Indicators.ChipControl.refresh` sets the chip's text, face, colour, fill and padding, then its accessible name
   (`Indicators.java:22-29`); `ChipLabel.paintComponent` paints the tile at the tile share (`:38-46`).
9. `Buttons.ButtonControl(KitView, Nodes.Button, Consumer<UiEvent>)` routes its click to the consumer
   (`Buttons.java:49-62`); `KitButton` declares `orderedCommand` (`:124-125`) and sizes it first in `getPreferredSize`
   (`:212`). Plan 4b's `ClassicButtonFace.paintFace` and `text` treat `b.kind == Nodes.ButtonKind.LINK` as the default
   look (4b Task 2).
10. `Choices.hue(KitContext, Hue)` and `hueSurface` map NONE to the neutral roles (`Choices.java:206-217`).
11. `UiEvent` has `Selected(id, Set)`, `Toggled`, `Expanded`, `Removed(id, itemId)` and `Reordered(id, itemId,
    destination)` (`UiEvent.java:13-37`); a choice emits `Selected(id, Set.of(optionId))` (`Choices.java:180`).
12. `KitGallery.fixtures()` (`KitGallery.java:37-67`), `main` (`:92-113`, writing `<theme>-<scale>x/` and a manifest),
    `installed(theme, kit, action)` (`:122-147`) and `capture(fixture, context, focus)` (`:149`); the `gallery` task reads
    `jasper.galleryScale` and `jasper.galleryTheme` (`jasper-ui/build.gradle.kts:28-46`).
13. `KitFixtures.themes()`, `degenerate()`, `marked()`, `custom()`, `context(theme, kit)` (`KitFixtures.java:46-233`);
    plan 4a adds `custom(base)`, `marked(base)`, `enterprise()`, `complement()` and `withControls(base, controls)`;
    plan 4b adds `capture(context, spec, width, height, name)`.
14. `KitMatrixTest.captures(theme, kit)` renders `KitGallery.fixtures()` (`KitMatrixTest.java:19-25`); plan 4a adds
    `areas(BufferedImage)` (2 × 2 blocks). `PaintedContrastTest.palettes()` returns a mutable list of the theme set and
    the degenerate palettes (`PaintedContrastTest.java:65-69`).
15. `tools/visual/compare_mock.py` prints `best_offset_render_px dx=… dy=… edge_agreement=…` as its second line
    (`compare_mock.py:139-141`); `compare_all.py` reads `name`, `mock`, `crop`, `render` and `render_crop` and ignores
    other keys (`compare_all.py:22-36`); `pairs.json` holds 34 entries.
16. The dialog mocks (1044 px wide at 2×) draw a 2 px window edge, the title bar in rows 2 to 57 and its rule in rows 58
    and 59; the content runs from x 2 to 1041 and y 60 to the height less 2 (measured with Pillow, Task 5's crops).
17. Plan 4b's final `face-pairs.tsv` lists the classic faces in `KitFaces.all()` order, and its last
    `ClassicLabelPolicy`, `ClassicStepperFace` and `ClassicFieldPolicy` rows are
    `classic	ClassicLabelPolicy	text.secondary	surface.app	CONTRAST:4.5`,
    `classic	ClassicStepperFace	text.placeholder	surface.input	CONTRAST:4.5` and
    `classic	ClassicFieldPolicy	text.secondary	surface.tableHeader	CONTRAST:4.5`.

18. `KitButton.paintContent` paints the glyph its `Jasper.button.icon` client property names in the current role, and
    `getIcon()` only when that property is null (`Buttons.java:363-375`; `ButtonControl.refresh` sets both, `:79-90`), so
    a face that replaces a button's icon clears the property too.
19. `KitGallery.capture` composites each render on the context's surface (`onSurface`, `KitGallery.java:163`), so the
    gallery's images are opaque; it renders at the gallery's UI scale (`flatlaf.uiScale`).
20. `KitView`'s constructor subscribes to its theme binding right after the first reconcile, and the subscription
    notifies at once (`KitView.java:55-56`, `ThemeBinding.java:19-25`), so every mounted control refreshes once more
    after `children` ran; `ViewReconciler.refresh` does not call `children` (`ViewReconciler.java:416`). A face's
    styling therefore lives where a refresh re-applies it: a layout, a flag the refresh reads, a row population, or
    4b's `Labels.TextControl.styled`.
21. Every colour Task 5 lists for a `classic-*` pair fills a 2 × 2 block within ΔE76 6 in its mock crop (checked
    2026-10-16 with `check_pairs.py`'s own colour test on the six crops).
22. Dry run (2026-10-16, while writing this plan, without Gradle): on a copy of `deafed424` with plan 3c's Tables, Trees,
    InteractiveRows, ItemLists and model edits applied, plan 4b's Task 7 `TextControl.styled` edits applied and stubs
    for the rest of 4a and 4b (the new roles, `Face`, `ClassicGlyphs`, `ThemePainting.role` and `painted`, a `KitFaces`
    holding this plan's faces), every edit script of Tasks 1 to 5 applied without a stop; jasper-ui's main sources,
    test fixtures and the new swing and gallery tests compiled with the JBR's `javac` (only 3b's `ChoiceKind.TABS` and
    `Field.readOnly` missing); under the JUnit launcher the classic halves of `ClassicTablesTest`,
    `ClassicIndicatorsTest` and `ClassicOrderedTest` passed with classic stub faces and their default halves with
    default ones, `ClassicContractTest` emitted the expected events but the read-only field's (3b), the six Enterprise
    fixtures rendered at 1× and 2×, `EnterpriseGalleryTest` passed against the appended `pairs.json`, and
    `check_pairs.py` ran end to end (agreement 0.21 to 0.47 with the default catalog and 4b's faces absent). The
    executor's runs on the real branch are the evidence.

## Mock measurements

Measured with Pillow (`python3 -I`) at the mocks' 2× pixels, halved; colours are Enterprise Light's (spec 7.6).

| Measured | Mock and place | Logical value | Rule |
|---|---|---|---|
| Header band, rule | Notifications, Keymap | 20 tall `#F0F1F3`, then a 1 px `#D3D6DA` rule | `TABLE_HEADER_HEIGHT` + `HAIRLINE` (Rulings 4) |
| Column separators | Same | 1 px `#D3D6DA`, the band's height | `border.divider` |
| Title and cell inset after a separator | Notifications "Display", "Default" | 9 to 10 | separator + `ICON_GAP` (Rulings 5) |
| First title and cell | Same | 9.5 from the box's outer edge | box + `ICON_GAP` |
| Heading rows | Notifications "Jasper" | 22 tall on `#F0F1F3`, no rule; title `#1E1F22` medium | Rulings 3 |
| Grouped row indent | Notifications "Jasper" under "Jasper" | 14 | `GROUP_ROW_INDENT` (Rulings 6) |
| Heading count | Same, "6 groups" | `#5C6370`, under the last column's start | Rulings 7 (residual) |
| Zebra | Workstation SFTP; Project Index markers | odd rows `#F7F8FA` | Rulings 3 |
| Table box | Notifications, Keymap, Project Index | 1 px `#B4B8BF` | `border.control` (Rulings 2) |
| Tree triangles | Keymap "Appearance", "Clipboard" | ▶ and ▼ 6 × 5 `#5C6370`, 9.5 from the box; text 15.5 after the glyph | Rulings 8 |
| Tree child indent | Keymap "Copy" | text 26 right of the branch glyph | `TREE_INDENT` 24 (2 px residual) |
| Tree trailing values | Keymap "3 shortcuts" | `#7A808A` | residual (3c's CAPTION colour) |
| Route strip | New Tunnel – Local | 44 tall incl. its 1 px `#B4B8BF` box; 3 equal cells of 159; 3 px hue edges `#6B7280`, `#2F65CA`, `#2E9E4F`; caption 11 from the box (edge 3 + 8) | Rulings 12 |
| Route arrow | Same | 13 wide `#7A808A`, ending at the next cell's edge | `ROUTE_ARROW`, `ROUTE_ARROW_INSET` |
| Ordered box and side column | Project Index markers | box 64 tall for three 20 px rows, zebra row 2; column 91 wide, 6.5 from the box; Remove, Move Up, Move Down 24 tall, 4 apart, spanning the box and the entry row 6 below it | Rulings 13, 14 |
| Dialog content crops | New Tunnel, Add Host, New File Server – FTP | x 2 to 1041 from y 60: 520 × 449, 520 × 521, 520 × 605 | Task 5 |
| Table crops | Notifications box; Project Index markers; Keymap box | 751 × 529; 751 × 92; 777 × 489 | Task 5 |

## File structure

- New in `jasper-ui/src/main/java/dev/jasper/ui/swing/`: `HeaderFace`, `ClassicHeaders`, `TableFace`, `DefaultTableFace`,
  `ClassicTableFace`, `TreeFace`, `DefaultTreeFace`, `ClassicTreeFace` (Task 1); `EmptyFace`, `DefaultEmptyFace`,
  `ClassicEmptyFace`, `TagFace`, `DefaultTagFace`, `ClassicTagFace`, `RouteFace`, `DefaultRouteFace`, `ClassicRouteFace`
  (Task 2); `OrderedFace`, `DefaultOrderedFace`, `ClassicOrderedFace` (Task 3).
- Changed controls: `KitFaces`, `Tables`, `Trees`, `InteractiveRows`, `Buttons`, `DesignMetrics` (Task 1); `KitFaces`,
  `EmptyStates`, `Indicators`, `Stacks`, `ViewReconciler` (Task 2); `KitFaces`, `ItemLists`, `InteractiveRows`, `Stacks`,
  `Buttons`, `ClassicButtonFace`, `ViewReconciler` (Task 3).
- Test fixtures (`jasper-ui/src/testFixtures/java/dev/jasper/ui/`): `gallery/EnterpriseFixtures` and
  `KitGallery.enterpriseCatalog()`, `testsupport/KitFixtures.enterpriseLight()` (Task 4); `KitGallery.main`'s
  Enterprise mode (Task 5).
- Tests: `jasper-ui/src/test/java/dev/jasper/ui/swing/` `ClassicTablesTest` (Task 1), `ClassicIndicatorsTest` (Task 2),
  `ClassicOrderedTest` (Task 3), `ClassicContractTest` (Task 4); `gallery/KitMatrixTest` and
  `control-covering-array.tsv`, `GalleryCoverageTest`, `testsupport/KitFixturesTest` (Task 4); `gallery/EnterpriseGalleryTest`
  (Task 5); `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest` (Task 4); frozen tables
  `metrics/compact-roles.tsv`, `metrics/classic-roles.tsv` (Task 1) and `swing/face-pairs.tsv` (Tasks 1 to 3).
- Tools: `tools/visual/pairs.json` (six `classic-*` entries), new `tools/visual/check_pairs.py`, `tools/visual/README.md`
  (Task 5); `jasper-ui/build.gradle.kts` (`jasper.galleryKit`, Task 5).

---

### Task 1: The table and the tree

The table and tree points' faces (spec 5.2, 6.4). The default faces keep today's transparent header, plain rows, hover
overlay and chevron disclosure. The classic table draws a header band in `surface.tableHeader` with 1 px `border.divider`
separators and rule, titles in `text.strong.secondary`, insets after each separator, zebra rows, heading bands and a
grouped row indent, the box, and `surface.rowHover` (Rulings 1 to 7); the classic tree shares the header, box and hover
and draws ▶/▼ triangles, a `TREE_INDENT` per depth and BODY_STRONG category rows (Rulings 8). One new role,
`GROUP_ROW_INDENT`, is added to both catalogs.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/HeaderFace.java`, `ClassicHeaders.java`, `TableFace.java`, `DefaultTableFace.java`, `ClassicTableFace.java`, `TreeFace.java`, `DefaultTreeFace.java`, `ClassicTreeFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Tables.java` (3c's file), `Trees.java:62-415`, `InteractiveRows.java:335,755-766,818-829,1218-1228`, `Buttons.java:124-125,212`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (one role), `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java` (`ADDED`), `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv` and `classic-roles.tsv` (one row each)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTablesTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (six rows inserted)

**Interfaces:**
- Consumes: plan 4b's `Face`, `KitFaces` (package fields, `all()`), `ThemePainting.role` and `painted`, `ClassicGlyphs.down`
  and `right`, `KitFixtures.capture`; plan 4a's `TABLE_HEADER_HEIGHT`, `TREE_INDENT`, `DISCLOSURE_GLYPH`,
  `DISCLOSURE_GAP`, `ROW_HEIGHT`, `ControlVariants.Table` (`PLAIN`, `CLASSIC`) and `.Tree` (`PLAIN`, `CLASSIC`),
  `ColorSlots.dialogSurface()`, `KitFixtures.enterprise()`; plan 3c's `Tables.headerLabel`, `TableRow.heading`,
  `Nodes.Tree` with `columns`, `Trees.TreeControl.header` and `columnHeader()`.
- Produces: `interface HeaderFace extends Face { void styleTitle(JLabel, KitContext, int index); Insets
  headerInsets(KitContext, int side, int line); void paintHeader(JComponent, Graphics2D, KitContext); Border
  box(KitContext); Color hover(KitContext); }`; `interface TableFace extends HeaderFace { void
  paintBands(Tables.TableControl, Graphics2D, int width); boolean headingRule(); int cellInset(KitContext); int
  groupIndent(KitContext); void styleCount(JLabel, KitContext); }`; `interface TreeFace extends HeaderFace { int
  indent(KitContext, int depth); Nodes.ButtonPresentation disclosure(); void styleDisclosure(Buttons.KitButton,
  KitContext, boolean expanded); Nodes.TextRole title(boolean leaf, Nodes.TextRole declared); }`; `final TableFace
  KitFaces.table`, `final TreeFace KitFaces.tree`; `ClassicHeaders.Box` (a `Border`) and `ClassicTreeFace.Disclosure`
  (an `Icon` record `(int size, boolean expanded, Color color)`); `Tables.HeaderBand` (a `JPanel` taking its owner
  control), `Tables.CellInset` and `static void Tables.inset(JComponent, int)`; on `Tables.TableControl`:
  `boolean grouped()`, `boolean underHeading(int)`; on `InteractiveRows.RowsControl`: `void paintBands(Graphics2D, int)`,
  `Color hoverFill()`, `boolean headingRule()`; `boolean Buttons.KitButton.disclosure`; `DesignMetrics.Role.GROUP_ROW_INDENT(14)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTablesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's table and tree points: the classic header band, zebra and heading bands, the box, triangles and indent. */
@SuppressWarnings("try")
class ClassicTablesTest {
    static final List<Column> COLUMNS = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
        new Column("size", "Size", Nodes.TextRole.DESCRIPTION, false), new Column("modified", "Modified", Nodes.TextRole.DESCRIPTION, false));

    static TableRow file(String id, String name) {
        return new TableRow(id, Map.of("name", name, "size", "—", "modified", "Sep 28"), Optional.empty());
    }

    static ViewSpec files() {
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, COLUMNS,
            List.of(file("a", "Applications"), file("b", "bin"), file("c", "Desktop"), file("d", "Documents")), Set.of("a"), "", List.of()));
    }

    static ViewSpec groups() {
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, COLUMNS, List.of(
            TableRow.heading("jasper", "Jasper", OptionalInt.of(2), "group"), file("a", "Configuration"), file("b", "Plugin problems"),
            TableRow.heading("editor", "Editor", OptionalInt.of(1), "group"), file("c", "Editor")), Set.of(), "", List.of()));
    }

    static TreeItem host(String id, String title, String address) {
        return new TreeItem(id, title, Optional.empty(), true, List.of(), OptionalInt.empty(), false, "",
            Optional.of(Nodes.StatusKind.SUCCESS), List.of(address, "22"), Optional.empty());
    }

    static ViewSpec tree() {
        var network = new TreeItem("net", "Local Network", Optional.empty(), false,
            List.of(host("mac", "Mac", "192.168.1.119"), host("mine", "minecraft-local", "192.168.1.124")));
        var other = new TreeItem("other", "Other", Optional.empty(), false, List.of());
        return new ViewSpec(new Nodes.Tree("tree", ControlState.READY, List.of(network, other), Set.of("net"), Set.of("mine"), "",
            List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
                new Column("host", "Host", Nodes.TextRole.DESCRIPTION, false), new Column("port", "Port", Nodes.TextRole.DESCRIPTION, true))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    @Test void theClassicHeaderIsABandWithSeparatorsAndARule() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, files(), e -> {})) {
                KitFixtures.capture(context, view.spec(), 420, 160, "table");
                KitFixtures.render(view, 420, 160, 1);
                var header = ((Tables.TableControl) view.control("table")).header;
                int rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                assertThat(header.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.TABLE_HEADER_HEIGHT) + rule);
                var image = paint(header);
                assertThat(at(image, header.getWidth() / 2, 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(at(image, header.getWidth() / 2, header.getHeight() - 1)).isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                var size = (JLabel) header.getComponent(1);
                assertThat(at(image, size.getX(), 2)).as("the separator before Size").isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                assertThat(size.getInsets().left).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP) + rule);
                assertThat(size.getForeground()).isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(0)));
            } finally { release(context); }
        });
    }

    @Test void anUngroupedTableStripesItsOddRowsAndIsBoxed() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, files(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                var canvas = paint(table.canvas);
                var odd = table.mounted.get("b"); var even = table.mounted.get("c");
                assertThat(at(canvas, table.canvas.getWidth() - 2, odd.getY() + odd.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ZEBRA));
                assertThat(at(canvas, table.canvas.getWidth() - 2, even.getY() + even.getHeight() / 2).getAlpha()).isZero();
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                assertThat(table.scroll.getBorder()).isInstanceOf(ClassicHeaders.Box.class);
                assertThat(table.scroll.getInsets()).isEqualTo(new Insets(line, line, line, line));
                assertThat(at(paint(table.scroll), 0, table.scroll.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(1)));
                assertThat(table.hoverFill()).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER));
                assertThat(((Tables.CellInset) odd.cells.get(1).getBorder()).inset).as("a cell after a separator").isEqualTo(ClassicHeaders.inset(context));
                assertThat(odd.cells.get(0).getBorder() instanceof Tables.CellInset).as("an ungrouped first cell").isFalse();
            } finally { release(context); }
        });
    }

    /** Review Focus 1: headings take no fill or press, are banded, and the rows under them indent only their first cell. */
    @Test void aGroupedTableBandsItsHeadingsIndentsItsRowsAndStripesNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, groups(), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 200, "table-grouped");
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                var canvas = paint(table.canvas);
                var band = ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER);
                var jasper = table.mounted.get("jasper"); var editor = table.mounted.get("editor"); var second = table.mounted.get("b");
                assertThat(at(canvas, table.canvas.getWidth() - 2, jasper.getY() + 2)).isEqualTo(band);
                assertThat(at(canvas, table.canvas.getWidth() / 2, editor.getY())).as("no hairline above a band").isEqualTo(band);
                assertThat(at(canvas, table.canvas.getWidth() - 2, second.getY() + second.getHeight() / 2).getAlpha()).as("no zebra").isZero();
                var first = table.mounted.get("a").cells;
                assertThat(((Tables.CellInset) first.get(0).getBorder()).inset).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.GROUP_ROW_INDENT));
                assertThat(((Tables.CellInset) first.get(1).getBorder()).inset).as("later columns keep their inset").isEqualTo(ClassicHeaders.inset(context));
                assertThat(find(view, "@table/table/jasper/count", JLabel.class).getForeground())
                    .isEqualTo(ThemePainting.painted(context, context.faces().table.pairs().get(2)));
                table.select("jasper", false);
                assertThat(table.selected).isEmpty();
                assertThat(events).isEmpty();
                table.select("a", false);
                assertThat(events).containsExactly(new UiEvent.Selected("table", Set.of("a")));
            } finally { release(context); }
        });
    }

    @Test void theDefaultTableKeepsItsTransparentHeaderAndPlainRows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, groups(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var table = (Tables.TableControl) view.control("table");
                assertThat(paint(table.header).getRGB(table.header.getWidth() / 2, 2) >>> 24).isZero();
                assertThat(table.scroll.getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
                for (var cell : table.mounted.get("a").cells) assertThat(cell.getBorder() instanceof Tables.CellInset).isFalse();
                assertThat(table.hoverFill()).isEqualTo(ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05));
            } finally { release(context); }
        });
    }

    @Test void theClassicTreeDrawsTrianglesIndentsItsChildrenAndStrengthensItsCategories() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, tree(), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 200, "tree");
                KitFixtures.render(view, 420, 200, 1);
                var tree = (Trees.TreeControl) view.control("tree");
                var expand = find(view, "@tree/tree/net/expand", Buttons.KitButton.class);
                assertThat(expand.getIcon()).isEqualTo(new ClassicTreeFace.Disclosure(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH), true,
                    ThemePainting.painted(context, context.faces().tree.pairs().get(2))));
                assertThat(((ClassicTreeFace.Disclosure) find(view, "@tree/tree/other/expand", Buttons.KitButton.class).getIcon()).expanded()).isFalse();
                assertThat(expand.getPreferredSize().width).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH)
                    + ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GAP) - ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
                assertThat(tree.mounted.get("mac").indent).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.TREE_INDENT));
                assertThat(find(view, "@tree/tree/net/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY_STRONG));
                assertThat(find(view, "@tree/tree/mac/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
                assertThat(at(paint(tree.header), tree.header.getWidth() / 2, 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(tree.hoverFill()).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER));
                expand.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Expanded("tree", "net", false));
            } finally { release(context); }
        });
    }

    @Test void theDefaultTreeKeepsItsChevronsAndIndent() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, tree(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var tree = (Trees.TreeControl) view.control("tree");
                assertThat(find(view, "@tree/tree/net/expand", Buttons.KitButton.class).getIcon()).isNotInstanceOf(ClassicTreeFace.Disclosure.class);
                assertThat(tree.mounted.get("mac").indent).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(16));
                assertThat(find(view, "@tree/tree/net/title", JLabel.class).getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the titles, box, count and glyph are painted as their guards make them, over every palette. */
    @Test void theTableAndTreePaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var table = context.faces().table.pairs(); var tree = context.faces().tree.pairs();
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, groups(), e -> {}); var trees = new KitView(context, tree(), e -> {})) {
                KitFixtures.render(view, 420, 200, 1); KitFixtures.render(trees, 420, 200, 1);
                var control = (Tables.TableControl) view.control("table");
                assertThat(((JLabel) control.header.getComponent(0)).getForeground()).as(where + " title").isEqualTo(ThemePainting.painted(context, table.get(0)));
                assertThat(at(paint(control.scroll), 0, control.scroll.getHeight() / 2)).as(where + " box").isEqualTo(ThemePainting.painted(context, table.get(1)));
                assertThat(find(view, "@table/table/jasper/count", JLabel.class).getForeground()).as(where + " count").isEqualTo(ThemePainting.painted(context, table.get(2)));
                var glyph = (ClassicTreeFace.Disclosure) find(trees, "@tree/tree/net/expand", Buttons.KitButton.class).getIcon();
                assertThat(glyph.color()).as(where + " glyph").isEqualTo(ThemePainting.painted(context, tree.get(2)));
                assertThat(((JLabel) ((Trees.TreeControl) trees.control("tree")).header.getComponent(0)).getForeground()).as(where + " tree title")
                    .isEqualTo(ThemePainting.painted(context, tree.get(0)));
            } finally { release(context); }
        });
    }
}
```

`find(root, id, type)` and `release(context)` are `KitViewTest`'s helpers (4b's tests use them the same way);
`view.control(id)` is package-private. The table's synthetic cell and count ids are `@table/<table>/<row>/…` (3c Task 3)
and the tree's `@tree/<tree>/<item>/…` (`Trees.java:307`, `:330`).

Insert the table and tree rows into `face-pairs.tsv` after the last `ClassicLabelPolicy` row (`KitFaces.all()` puts the
two points after the label policy):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
anchor = "classic\tClassicLabelPolicy\ttext.secondary\tsurface.app\tCONTRAST:4.5\n"
if text.count(anchor) != 1: raise SystemExit("face-pairs.tsv: expected one last ClassicLabelPolicy row")
rows = ("classic\tClassicTableFace\ttext.strong.secondary\tsurface.tableHeader\tCONTRAST:4.5\n"
        "classic\tClassicTableFace\tborder.control\tsurface.app\tCONTRAST:1.5\n"
        "classic\tClassicTableFace\ttext.secondary\tsurface.tableHeader\tCONTRAST:4.5\n"
        "classic\tClassicTreeFace\ttext.strong.secondary\tsurface.tableHeader\tCONTRAST:4.5\n"
        "classic\tClassicTreeFace\tborder.control\tsurface.app\tCONTRAST:1.5\n"
        "classic\tClassicTreeFace\ttext.secondary\tsurface.card\tCONTRAST:3.0\n")
path.write_text(text.replace(anchor, anchor + rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicTablesTest'`
Expected: FAIL at compile: "cannot find symbol … variable table" in `KitFaces`, "class ClassicHeaders",
"class ClassicTreeFace", "variable GROUP_ROW_INDENT" and "method hoverFill()".

- [ ] **Step 3: Add the faces**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/HeaderFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * What the table and tree points share (spec 5.2, plan 4c Rulings 1): the header over the rows, the box around them and
 * the hover fill.
 */
interface HeaderFace extends Face {
    /** Styles column title {@code index} after the control builds it: its ink, and its inset after a column separator. */
    void styleTitle(JLabel title, KitContext context, int index);
    /** The header's padding: {@code side} at each end; {@code line} is the titles' height. */
    Insets headerInsets(KitContext context, int side, int line);
    /** Paints behind the titles: nothing, or the classic band with its separators and rule. */
    void paintHeader(JComponent header, Graphics2D g, KitContext context);
    /** The scroll pane's border, or null to keep the pane's own. */
    Border box(KitContext context);
    /** A hovered or keyboard-focused row's fill. */
    Color hover(KitContext context);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/TableFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Graphics2D;
import javax.swing.JLabel;

/** The table point (spec 5.2): a transparent header over plain rows, or the classic band, zebra rows and heading bands. */
interface TableFace extends HeaderFace {
    /** Paints the bands under the rows' selection and hover fills, across {@code width}. */
    void paintBands(Tables.TableControl table, Graphics2D g, int width);
    /** Whether a heading row after other rows opens with a hairline rule. */
    boolean headingRule();
    /** How far a title or cell after the first column sits from its column's start. */
    int cellInset(KitContext context);
    /** How far a data row's first cell is indented under a heading row. */
    int groupIndent(KitContext context);
    /** Styles a heading row's count after it refreshes. */
    void styleCount(JLabel count, KitContext context);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/TreeFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;

/** The tree point (spec 5.2): today's tree, or ▶/▼ disclosure, a deeper indent and medium category rows. */
interface TreeFace extends HeaderFace {
    /** A row's indent at {@code depth}. */
    int indent(KitContext context, int depth);
    /** The presentation of a branch row's expand button. */
    Nodes.ButtonPresentation disclosure();
    /** Prepares a branch row's expand button after it refreshes. */
    void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded);
    /** A row title's role: {@code declared}, or the category role for a branch. */
    Nodes.TextRole title(boolean leaf, Nodes.TextRole declared);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultTableFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/** The default table: caption titles over a transparent header, plain rows, the shared hover overlay. */
final class DefaultTableFace implements TableFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void styleTitle(JLabel title, KitContext context, int index) { }
    @Override public Insets headerInsets(KitContext context, int side, int line) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
        return new Insets(pad, side, pad, side);
    }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { }
    @Override public Border box(KitContext context) { return null; }
    @Override public Color hover(KitContext context) { return ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05); }
    @Override public void paintBands(Tables.TableControl table, Graphics2D g, int width) { }
    @Override public boolean headingRule() { return true; }
    @Override public int cellInset(KitContext context) { return 0; }
    @Override public int groupIndent(KitContext context) { return 0; }
    @Override public void styleCount(JLabel count, KitContext context) { }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultTreeFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/** The default tree: chevron buttons, a 16 px indent per depth, the declared title roles and the shared hover overlay. */
final class DefaultTreeFace implements TreeFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void styleTitle(JLabel title, KitContext context, int index) { }
    @Override public Insets headerInsets(KitContext context, int side, int line) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
        return new Insets(pad, side, pad, side);
    }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { }
    @Override public Border box(KitContext context) { return null; }
    @Override public Color hover(KitContext context) { return ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .05); }
    @Override public int indent(KitContext context, int depth) { return depth * com.formdev.flatlaf.util.UIScale.scale(16); }
    @Override public Nodes.ButtonPresentation disclosure() { return Nodes.ButtonPresentation.STANDARD; }
    @Override public void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded) { }
    @Override public Nodes.TextRole title(boolean leaf, Nodes.TextRole declared) { return declared; }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicHeaders.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * The classic table and tree header and box (spec 5.2; the Settings Notifications and Keymap mocks), shared by both
 * classic faces (plan 4c Rulings 1 to 5).
 */
final class ClassicHeaders {
    private ClassicHeaders() {}

    /** How far a title or cell after a column separator sits from its column's start: the separator and the icon gap. */
    static int inset(KitContext context) {
        return ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) + ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP);
    }

    /** Title {@code index} in {@code ink}'s guarded colour, inset after its separator unless it is the first. */
    static void title(JLabel title, KitContext context, int index, PaintPair ink) {
        title.setForeground(ThemePainting.painted(context, ink));
        if (index > 0) title.setBorder(BorderFactory.createEmptyBorder(0, inset(context), 0, 0));
    }

    /** A band {@code TABLE_HEADER_HEIGHT} tall around titles {@code line} high, then the rule under it. */
    static Insets insets(KitContext context, int side, int line) {
        int band = ThemePainting.metric(context, DesignMetrics.Role.TABLE_HEADER_HEIGHT), rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        int top = Math.max(0, (band - line) / 2);
        return new Insets(top, side, Math.max(0, band - line - top) + rule, side);
    }

    /** The band, a separator before every later title and the rule along the bottom. */
    static void paint(JComponent header, Graphics2D g, KitContext context) {
        int rule = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), width = header.getWidth(), height = header.getHeight();
        g.setColor(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
        g.fillRect(0, 0, width, height);
        g.setColor(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
        g.fillRect(0, height - rule, width, rule);
        for (int i = 1; i < header.getComponentCount(); i++) {
            var title = header.getComponent(i);
            if (title.isVisible()) g.fillRect(title.getX(), 0, rule, Math.max(0, height - rule));
        }
    }

    /** The 1 px box around a table or tree that is not in a panel body (Rulings 2), its edge through {@code edge}'s guard. */
    static final class Box implements Border {
        final KitContext context;
        final PaintPair edge;

        Box(KitContext context, PaintPair edge) { this.context = context; this.edge = edge; }

        /** A panel body draws its own rules around the rows. */
        static boolean drawn(Component c) { return SwingUtilities.getAncestorOfClass(Panels.Body.class, c) == null; }

        @Override public Insets getBorderInsets(Component c) {
            int line = drawn(c) ? ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) : 0;
            return new Insets(line, line, line, line);
        }

        @Override public boolean isBorderOpaque() { return false; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if (!drawn(c)) return;
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            g.setColor(ThemePainting.painted(context, edge));
            g.fillRect(x, y, width, line);
            g.fillRect(x, y + height - line, width, line);
            g.fillRect(x, y, line, height);
            g.fillRect(x + width - line, y, line, height);
        }
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTableFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * The classic table (spec 5.2 and 6.4, DESIGN_LANGUAGE §6): a header band with 1 px column separators, zebra rows or,
 * in a grouped table, heading bands, the box, and the row hover role.
 */
final class ClassicTableFace implements TableFace {
    private final PaintPair title, box, count;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicTableFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        count = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, box, count); }
    @Override public void styleTitle(JLabel label, KitContext context, int index) { ClassicHeaders.title(label, context, index, title); }
    @Override public Insets headerInsets(KitContext context, int side, int line) { return ClassicHeaders.insets(context, side, line); }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { ClassicHeaders.paint(header, g, context); }
    @Override public Border box(KitContext context) { return new ClassicHeaders.Box(context, box); }
    @Override public Color hover(KitContext context) { return ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER); }

    /** Rulings 3: an ungrouped table stripes its odd rows; a grouped one bands its headings and stripes nothing. */
    @Override public void paintBands(Tables.TableControl table, Graphics2D g, int width) {
        var context = table.context();
        boolean grouped = table.grouped();
        for (var row : table.mounted.values()) {
            boolean heading = !table.dataRow(row.index);
            if (!heading && (grouped || row.index % 2 == 0)) continue;
            g.setColor(ThemePainting.role(context, heading ? DerivedColor.SURFACE_TABLE_HEADER : DerivedColor.SURFACE_ZEBRA));
            g.fillRect(0, row.getY(), width, row.getHeight());
        }
    }

    @Override public boolean headingRule() { return false; }
    @Override public int cellInset(KitContext context) { return ClassicHeaders.inset(context); }
    @Override public int groupIndent(KitContext context) { return ThemePainting.metric(context, DesignMetrics.Role.GROUP_ROW_INDENT); }
    @Override public void styleCount(JLabel label, KitContext context) { label.setForeground(ThemePainting.painted(context, count)); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTreeFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * The classic tree (spec 5.2 and 6.4, the Keymap mock): the shared header band and box, a drawn ▶ or ▼ on each branch,
 * {@code TREE_INDENT} per depth, medium-weight category rows and the row hover role.
 */
final class ClassicTreeFace implements TreeFace {
    private final PaintPair title, box, glyph;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicTreeFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        glyph = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.INDICATOR);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, box, glyph); }
    @Override public void styleTitle(JLabel label, KitContext context, int index) { ClassicHeaders.title(label, context, index, title); }
    @Override public Insets headerInsets(KitContext context, int side, int line) { return ClassicHeaders.insets(context, side, line); }
    @Override public void paintHeader(JComponent header, Graphics2D g, KitContext context) { ClassicHeaders.paint(header, g, context); }
    @Override public Border box(KitContext context) { return new ClassicHeaders.Box(context, box); }
    @Override public Color hover(KitContext context) { return ThemePainting.role(context, DerivedColor.SURFACE_ROW_HOVER); }
    @Override public int indent(KitContext context, int depth) { return depth * ThemePainting.metric(context, DesignMetrics.Role.TREE_INDENT); }
    @Override public Nodes.ButtonPresentation disclosure() { return Nodes.ButtonPresentation.EMBEDDED; }
    @Override public Nodes.TextRole title(boolean leaf, Nodes.TextRole declared) { return leaf ? declared : Nodes.TextRole.BODY_STRONG; }

    /** Rulings 8: the glyph alone, leading its button, in the row's ink on a selected row. */
    @Override public void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded) {
        button.disclosure = true;
        Color ink = button.onSelection ? ThemePainting.color(context, button.ink.text) : ThemePainting.painted(context, glyph);
        var icon = new Disclosure(ThemePainting.metric(context, DesignMetrics.Role.DISCLOSURE_GLYPH), expanded, ink);
        button.setIcon(icon);
        button.setDisabledIcon(icon);
        // KitButton repaints the glyph its client property names; without a name it paints this icon.
        button.putClientProperty("Jasper.button.icon", null);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setHorizontalAlignment(SwingConstants.LEADING);
    }

    /** A drawn ▼ when {@code expanded}, else ▶, {@code size} square, in {@code color}. */
    record Disclosure(int size, boolean expanded, Color color) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g;
            if (expanded) ClassicGlyphs.down(g2, x + size / 2.0, y + size * .1, size, color);
            else ClassicGlyphs.right(g2, x + size * .1, y + size / 2.0, size, color);
        }
    }
}
```

- [ ] **Step 4: Route the table, the tree and the rows through the faces, and add the role**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 1 step 4: the table and tree faces, the row hooks, the disclosure size and GROUP_ROW_INDENT."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final PromptFace prompt;
''', '''    final TableFace table;
    final TreeFace tree;
    final PromptFace prompt;
''', 1),
    ('''        prompt = switch (controls.prompt()) {''', '''        table = switch (controls.table()) {
            case PLAIN -> new DefaultTableFace();
            case CLASSIC -> new ClassicTableFace(colors.dialogSurface());
        };
        tree = switch (controls.tree()) {
            case PLAIN -> new DefaultTreeFace();
            case CLASSIC -> new ClassicTreeFace(colors.dialogSurface());
        };
        prompt = switch (controls.prompt()) {''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, stepper, buttons, fields, prompt); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, table, tree, stepper, buttons, fields, prompt); }''', 1)])
edit(S + "InteractiveRows.java", [
    ('''        boolean identityRows() { return false; }
''', '''        boolean identityRows() { return false; }

        /** Paints bands under the rows' fills (a classic table's zebra and heading bands); none by default. */
        void paintBands(Graphics2D g, int width) { }

        /** A hovered or keyboard-focused row's fill. */
        Color hoverFill() { return ColorMath.opacity(ThemePainting.color(context(), SemanticColor.HOVER_OVERLAY), .05); }

        /** Whether a heading row after other rows opens with a hairline rule. */
        boolean headingRule() { return true; }
''', 1),
    ('''            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.
            for (var row : owner.mounted.values()) row.paintFill(graphics, getWidth());''',
     '''            var bands = (Graphics2D) graphics.create();
            try { owner.paintBands(bands, getWidth()); } finally { bands.dispose(); }
            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.
            for (var row : owner.mounted.values()) row.paintFill(graphics, getWidth());''', 1),
    (''': hover || focusedOnly ? ColorMath.opacity(ThemePainting.color(owner.context(), SemanticColor.HOVER_OVERLAY), .05) : null;''',
     ''': hover || focusedOnly ? owner.hoverFill() : null;''', 1),
    ('''                if (index > 0) {
                    g.setColor(ThemePainting.color(owner.context(), owner.resultsLayout() ? SemanticColor.BORDER_MENU : SemanticColor.BORDER_SUBTLE));''',
     '''                if (index > 0 && owner.headingRule()) {
                    g.setColor(ThemePainting.color(owner.context(), owner.resultsLayout() ? SemanticColor.BORDER_MENU : SemanticColor.BORDER_SUBTLE));''', 1)])
edit(S + "Tables.java", [
    ('''    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new JPanel();''',
     '''    /** A table's or a column tree's header: the titles over whatever its face paints behind them (nothing by default). */
    static final class HeaderBand extends JPanel {
        final ControlRegistry.Control owner;

        HeaderBand(ControlRegistry.Control owner) { this.owner = owner; }

        HeaderFace face() { return owner instanceof Trees.TreeControl ? owner.context().faces().tree : owner.context().faces().table; }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            var g = (Graphics2D) graphics.create();
            try { face().paintHeader(this, g, owner.context()); } finally { g.dispose(); }
        }
    }

    /** A cell's padding after its column's start (the classic look, plan 4c Rulings 5 and 6), over its control's own border. */
    static final class CellInset extends javax.swing.border.CompoundBorder {
        final int inset;

        CellInset(javax.swing.border.Border own, int inset) {
            super(own, BorderFactory.createEmptyBorder(0, inset, 0, 0));
            this.inset = inset;
        }
    }

    /** Pads {@code cell} by {@code inset} on the left over its own border, or gives it back its own border for 0. */
    static void inset(JComponent cell, int inset) {
        var border = cell.getBorder();
        var own = border instanceof CellInset padded ? padded.getOutsideBorder() : border;
        if (inset <= 0) {
            if (border instanceof CellInset) cell.setBorder(own);
            return;
        }
        if (!(border instanceof CellInset current && current.inset == inset)) cell.setBorder(new CellInset(own, inset));
    }

    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new HeaderBand(this);
        /** Which rows are data rows below a heading row (plan 4c), measured with the data. */
        private boolean[] underHeading = new boolean[0];''', 1),
    ('''            scroll.getColumnHeader().setOpaque(false);
            refresh();
        }''', '''            scroll.getColumnHeader().setOpaque(false);
            var box = context().faces().table.box(context());
            if (box != null) scroll.setBorder(box);
            refresh();
        }''', 1),
    ('''        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */''',
     '''        /** Whether any row is a heading row. */
        boolean grouped() {
            return value().rows().stream().anyMatch(row -> row.heading().isPresent());
        }

        /** Whether row {@code index} is a data row below a heading row. */
        boolean underHeading(int index) {
            return index >= 0 && index < underHeading.length && underHeading[index];
        }

        @Override
        void paintBands(Graphics2D g, int width) {
            context().faces().table.paintBands(this, g, width);
        }

        @Override
        Color hoverFill() {
            return context().faces().table.hover(context());
        }

        @Override
        boolean headingRule() {
            return context().faces().table.headingRule();
        }

        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */''', 1),
    ('''        void data() {
            nodeRows.clear();''', '''        void data() {
            underHeading = new boolean[size()];
            boolean seen = false;
            for (int i = 0; i < size(); i++) {
                if (value().rows().get(i).heading().isPresent()) seen = true;
                else underHeading[i] = seen;
            }
            nodeRows.clear();''', 1),
    ('''            for (var column : value().columns()) header.add(headerLabel(this, "@table/", column));''',
     '''            var titles = value().columns();
            for (int i = 0; i < titles.size(); i++) {
                var title = headerLabel(this, "@table/", titles.get(i));
                context().faces().table.styleTitle(title, context(), i);
                header.add(title);
            }''', 1),
    ('''            header.setBorder(BorderFactory.createEmptyBorder(pad, side, pad, side));''',
     '''            var insets = context().faces().table.headerInsets(context(), side,
                    header.getComponentCount() == 0 ? 0 : header.getComponent(0).getPreferredSize().height);
            header.setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));''', 1),
    ('''                if (control instanceof Choices.ChoiceControl choice) choice.inCell(row.inked ? ink() : null);''',
     '''                if (control instanceof Choices.ChoiceControl choice) choice.inCell(row.inked ? ink() : null);
                // The classic look pads a cell after its column's separator and indents a grouped row's first cell.
                inset(cell, i == 0 ? underHeading(index) ? context().faces().table.groupIndent(context()) : 0
                        : context().faces().table.cellInset(context()));''', 1),
    ('''            row.slot(row.trailing, count.isEmpty() ? List.of()
                    : List.of(new Nodes.Text(prefix + "/count", ControlState.READY, count, Nodes.TextRole.CAPTION)));''',
     '''            row.slot(row.trailing, count.isEmpty() ? List.of()
                    : List.of(new Nodes.Text(prefix + "/count", ControlState.READY, count, Nodes.TextRole.CAPTION)));
            if (row.controls.get(prefix + "/count") != null && row.controls.get(prefix + "/count").component instanceof JLabel label)
                context().faces().table.styleCount(label, context());''', 1)])
edit(S + "Trees.java", [
    ('''        final javax.swing.JPanel header = new javax.swing.JPanel();''',
     '''        final javax.swing.JPanel header = new Tables.HeaderBand(this);''', 1),
    ('''            ((TreePane)scroll).owner=this;''',
     '''            ((TreePane)scroll).owner=this;
            var box=context().faces().tree.box(context());
            if(box!=null)scroll.setBorder(box);''', 1),
    ('''        @Override boolean uniformRows(){return !settingsNavigation && !rail() && detail==null && presentation.isEmpty();}''',
     '''        @Override boolean uniformRows(){return !settingsNavigation && !rail() && detail==null && presentation.isEmpty();}
        @Override java.awt.Color hoverFill(){return context().faces().tree.hover(context());}''', 1),
    ('''                var label = Tables.headerLabel(this, "@tree/", columns.get(i));''',
     '''                var label = Tables.headerLabel(this, "@tree/", columns.get(i));
                context().faces().tree.styleTitle(label, context(), i);''', 1),
    ('''            header.setBorder(javax.swing.BorderFactory.createEmptyBorder(pad, side, pad, side));''',
     '''            var insets = context().faces().tree.headerInsets(context(), side,
                    header.getComponentCount() == 0 ? 0 : header.getComponent(0).getPreferredSize().height);
            header.setBorder(javax.swing.BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));''', 1),
    ('''entry.depth() * com.formdev.flatlaf.util.UIScale.scale(16);''',
     '''context().faces().tree.indent(context(), entry.depth());''', 1),
    ('''"Expand or collapse " + item.title(),presentation.isEmpty()?Nodes.ButtonPresentation.STANDARD:Nodes.ButtonPresentation.EMBEDDED));''',
     '''"Expand or collapse " + item.title(),presentation.isEmpty()?context().faces().tree.disclosure():Nodes.ButtonPresentation.EMBEDDED));''', 1),
    ('''            row.slot(row.leading, leading);
''', '''            row.slot(row.leading, leading);
            if (!item.leaf() && presentation.isEmpty() && row.controls.get("@tree/" + node.id() + "/" + item.id() + "/expand") instanceof Buttons.ButtonControl expand)
                context().faces().tree.styleDisclosure(expand.button, context(), value().expandedIds().contains(item.id()));
''', 1),
    (''':item.emphasized() ? Nodes.TextRole.BODY_STRONG : Nodes.TextRole.BODY)));''',
     ''':context().faces().tree.title(item.leaf(), item.emphasized() ? Nodes.TextRole.BODY_STRONG : Nodes.TextRole.BODY))));''', 1)])
edit(S + "Buttons.java", [
    ('''        /** An ORDERED row's compact up/down/remove command. */
        boolean orderedCommand;''', '''        /** An ORDERED row's compact up/down/remove command. */
        boolean orderedCommand;
        /** A classic tree's expand button: its drawn triangle alone, as wide as the glyph and its gap to the title (plan 4c). */
        boolean disclosure;''', 1),
    ('''                if(orderedCommand) {int extent=ThemePainting.metric(context,DesignMetrics.Role.ORDERED_ACTION);return new Dimension(extent,extent);}''',
     '''                if(disclosure) {int glyph=ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GLYPH);
                    return new Dimension(Math.max(glyph,glyph+ThemePainting.metric(context,DesignMetrics.Role.DISCLOSURE_GAP)-ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP)),ThemePainting.metric(context,DesignMetrics.Role.ROW_HEIGHT));}
                if(orderedCommand) {int extent=ThemePainting.metric(context,DesignMetrics.Role.ORDERED_ACTION);return new Dimension(extent,extent);}''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''DOCK_RULE(1), DOCK_RULE_HIT(6);''', '''DOCK_RULE(1), DOCK_RULE_HIT(6),
        // Plan 4c: a grouped table's data rows under their heading (Settings, Notifications mock); classic faces only.
        GROUP_ROW_INDENT(14);''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java", [
    ('''"STRIPE_LABEL_PADDING", "STRIPE_LABEL_GAP", "DOCK_RULE", "DOCK_RULE_HIT");''',
     '''"STRIPE_LABEL_PADDING", "STRIPE_LABEL_GAP", "DOCK_RULE", "DOCK_RULE_HIT", "GROUP_ROW_INDENT");''', 1)])
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics") / table
    text = path.read_text(encoding="utf-8")
    if not text.endswith("DOCK_RULE_HIT\t6\n"): raise SystemExit(f"{table}: expected DOCK_RULE_HIT last")
    path.write_text(text + "GROUP_ROW_INDENT\t14\n", encoding="utf-8")
PY
```

The default faces return today's values: `Tables.inset(cell, 0)` leaves a border that is not a `CellInset` alone, the
header keeps `createEmptyBorder(pad, side, pad, side)`, the table and tree keep `RowsControl`'s empty scroll border, the
hover stays `opacity(HOVER_OVERLAY, .05)`, the heading rule stays, the indent stays `depth × scale(16)`, the expand
button keeps its STANDARD presentation and chevron, and a branch keeps its declared title role. `HeaderBand` paints
nothing more than the non-opaque `JPanel` it replaces. `GROUP_ROW_INDENT` is 14 in both catalogs and only the classic
table face reads it, as 4a's Rulings 6 asks of classic-only roles (`ClassicCatalogTest.theSpecsNamedValuesHold` checks
it with `NewRolesTest.ADDED`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicTablesTest` 7 tests; `PaintSpecTest` with the six new rows; `TablesTest`, `TreesTest`,
`TreeDetailTest`, `TableVocabularyTest`, `InteractiveRowsTest`, `SelectionInkTest`, `NewRolesTest`, `ClassicCatalogTest`,
`DesignMetricsTest` and the gallery suites green).

- [ ] **Step 6: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`. Open `jasper-ui/build/classic-faces/table.png`, `table-grouped.png` and `tree.png` and compare
them with the Notifications and Keymap mocks by eye (the band and separators, the heading bands and the indent, the
triangles).

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTablesTest.java jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: the table and the tree

The table and tree points' faces. The classic table draws a header band with column
separators and a rule, insets its later titles and cells, stripes an ungrouped table's
odd rows, bands a grouped table's headings and indents the rows under them, boxes the
rows outside a panel body and hovers in surface.rowHover; the classic tree shares the
header, box and hover and draws triangles, a deeper indent and medium category rows.
GROUP_ROW_INDENT is added to both catalogs. The default faces keep today's code.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 2: The empty state, the tag and the route

Three faces for spec 5.2's `empty`, `tag` and `route` points. The defaults are today's tile empty state, tinted chip
and tiled route. The classic empty state drops the tile and shows its first action only (Rulings 10); the classic tag
is an upper-case label in a 1 px `tag.border` box on its tint (Rulings 11); the classic route is one boxed strip of
equal cells, each led by its step's hue edge, with upper-case captions and drawn arrows (Rulings 12).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/EmptyFace.java`, `DefaultEmptyFace.java`, `ClassicEmptyFace.java`, `TagFace.java`, `DefaultTagFace.java`, `ClassicTagFace.java`, `RouteFace.java`, `DefaultRouteFace.java`, `ClassicRouteFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `EmptyStates.java:45-99`, `Indicators.java:15-47`, `Stacks.java:20-42,99,148-152`, `ViewReconciler.java:297-308`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicIndicatorsTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (20 rows inserted)

**Interfaces:**
- Consumes: Task 1's `KitFaces` order; plan 4b's `Face`, `ThemePainting.role` and `painted`, `ClassicGlyphs.right`,
  `Labels.TextControl.styled` (4b Task 7);
  plan 4a's `ROUTE_EDGE`, `CHIP_HEIGHT`, `CHIP_RADIUS`, `ControlVariants.Empty` (`TILE`, `TEXT`), `.Tag` (`TINT`, `BOXED`),
  `.Route` (`TILES`, `EDGED`).
- Produces: `interface EmptyFace extends Face { boolean tile(); int actions(int declared); void style(EmptyStates.EmptyControl); }`;
  `interface TagFace extends Face { void style(Indicators.ChipControl, Indicators.ChipLabel); void paint(Indicators.ChipLabel, Graphics2D); }`;
  `interface RouteFace extends Face { boolean tiles(); LayoutManager layout(Stacks.StackControl route); void
  paintStrip(Stacks.StackSurface, Graphics); void strip(Stacks.StackControl route, List<ControlRegistry.Control>); void
  step(Stacks.StackControl step, List<ControlRegistry.Control>); LayoutManager stepLayout(Stacks.StackControl step); }`; `final EmptyFace KitFaces.empty`, `final TagFace
  KitFaces.tag`, `final RouteFace KitFaces.route`; `PaintPair ClassicTagFace.text(Nodes.Hue)` and `edge(Nodes.Hue)`,
  `PaintPair ClassicRouteFace.edge(Nodes.Hue)`; `void Indicators.ChipLabel.paintTile(Graphics2D)` and its `Color edge`;
  `void Stacks.StackSurface.paintRoute(Graphics)` (package-private now), its `int[] cells` and `List<Nodes.Hue> hues`;
  `boolean Stacks.StackControl.routeTiles()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicIndicatorsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's empty, tag and route points under the classic and the default faces. */
@SuppressWarnings("try")
class ClassicIndicatorsTest {
    static final ControlState READY = ControlState.READY;
    static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.NEUTRAL, Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE);

    static ViewSpec empty() {
        return new ViewSpec(new Nodes.Empty("empty", READY, new Nodes.Icon("empty-icon", READY, "plugin", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"),
            "No Tunnels", "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
            List.of(new Nodes.Button("new", READY, "New Tunnel…", "", Nodes.ButtonKind.SECONDARY, "new", ""),
                new Nodes.Button("help", READY, "Learn More", "", Nodes.ButtonKind.GHOST, "help", ""))));
    }

    static ViewSpec chips() {
        return new ViewSpec(new Nodes.Stack("chips", READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Chip("http", READY, "http", Nodes.Hue.BLUE), new Nodes.Chip("sftp", READY, "sftp", Nodes.Hue.GREEN),
            new Nodes.Chip("ftp", READY, "ftp", Nodes.Hue.AMBER), new Nodes.Chip("rsync", READY, "rsync", Nodes.Hue.PURPLE),
            new Nodes.Chip("plain", READY, "plain", Nodes.Hue.NEUTRAL), new Nodes.Chip("none", READY, "none", Nodes.Hue.NONE))));
    }

    static ViewSpec route(int steps) {
        var children = new ArrayList<Nodes.Node>();
        for (int i = 0; i < steps; i++) children.add(new Nodes.Stack("step" + i, READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Icon("step" + i + "-icon", READY, "terminal", HUES.get(i), Nodes.IconSize.CONTROL, "Step"),
            new Nodes.Text("step" + i + "-caption", READY, "This computer", Nodes.TextRole.CAPTION),
            new Nodes.Text("step" + i + "-value", READY, "localhost:808" + i, Nodes.TextRole.MONO))));
        return new ViewSpec(new Nodes.Stack("route", READY, Nodes.LayoutRole.ROUTE, children));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    /** Review Focus 5: no tile, a bold line over a sentence, the first action only, still so after an update. */
    @Test void aClassicEmptyStateIsABoldLineASentenceAndOneButton() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().empty.pairs();
            try (var view = new KitView(context, empty(), e -> {})) {
                KitFixtures.capture(context, view.spec(), 360, 220, "empty");
                KitFixtures.render(view, 360, 220, 1);
                var control = (EmptyStates.EmptyControl) view.control("empty");
                assertThat(control.tile.getWidth()).isZero();
                assertThat(control.title.getY()).isLessThan(control.description.getY());
                assertThat(control.title.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(control.description.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(view.control("new").component.getParent()).isSameAs(control.actions);
                assertThat(view.control("help").component.getParent()).as("the second action is not placed").isNull();
                view.update(empty());
                KitFixtures.render(view, 360, 220, 1);
                assertThat(view.control("help").component.getParent()).as("nor after an update").isNull();
            } finally { release(context); }
        });
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, empty(), e -> {})) {
                KitFixtures.render(view, 360, 220, 1);
                var control = (EmptyStates.EmptyControl) view.control("empty");
                assertThat(control.tile.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.EMPTY_TILE));
                assertThat(view.control("help").component.getParent()).isSameAs(control.actions);
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the tags' text and boxes as their guards make them, over every palette; NONE is neutral. */
    @Test void aClassicTagIsAnUppercaseLabelInAToneBoxOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var face = (ClassicTagFace) context.faces().tag;
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, chips(), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var hues = Map.of("http", Nodes.Hue.BLUE, "sftp", Nodes.Hue.GREEN, "ftp", Nodes.Hue.AMBER, "rsync", Nodes.Hue.PURPLE,
                    "plain", Nodes.Hue.NEUTRAL, "none", Nodes.Hue.NEUTRAL);
                for (var entry : hues.entrySet()) {
                    var label = find(view, entry.getKey(), Indicators.ChipLabel.class);
                    assertThat(label.getText()).isEqualTo(entry.getKey().toUpperCase(Locale.ROOT));
                    assertThat(label.getForeground()).as(where + " " + entry.getKey()).isEqualTo(ThemePainting.painted(context, face.text(entry.getValue())));
                    assertThat(at(paint(label), 0, label.getHeight() / 2)).as(where + " box " + entry.getKey())
                        .isEqualTo(ThemePainting.painted(context, face.edge(entry.getValue())));
                    assertThat(label.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.CHIP_HEIGHT));
                }
            } finally { release(context); }
        });
    }

    @Test void theDefaultTagKeepsItsTintAndCase() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, chips(), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var label = find(view, "http", Indicators.ChipLabel.class);
                assertThat(label.getText()).isEqualTo("http");
                assertThat(label.edge).isNull();
            } finally { release(context); }
        });
    }

    /** Review Focus 4: equal cells, steps inside them, every arrow before the next cell's edge, at two widths. */
    @Test void routeCellsShareTheStripAndArrowsEndBeforeTheNextEdge() {
        for (int steps = 2; steps <= 5; steps++) for (int width : new int[] {300, 520}) {
            int count = steps, wide = width;
            Edt.run(() -> {
                var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
                var face = (ClassicRouteFace) context.faces().route;
                try (var view = new KitView(context, route(count), e -> {})) {
                    if (count == 3 && wide == 520) KitFixtures.capture(context, view.spec(), wide, 60, "route");
                    KitFixtures.render(view, wide, 60, 1);
                    var surface = (Stacks.StackSurface) view.control("route").component;
                    String where = count + " steps at " + wide;
                    assertThat(surface.cells).as(where).hasSize(count);
                    assertThat(surface.arrows).as(where).hasSize(count - 1);
                    int arrow = ThemePainting.metric(context, DesignMetrics.Role.ROUTE_ARROW), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                    int cell = (surface.getWidth() - 2 * line) / count;
                    var image = paint(surface);
                    int middle = ThemePainting.metric(context, DesignMetrics.Role.ROUTE_TOP) + (surface.getHeight() - ThemePainting.metric(context, DesignMetrics.Role.ROUTE_TOP)) / 2;
                    for (int i = 0; i < count; i++) {
                        int end = i == count - 1 ? surface.getWidth() - line : surface.cells[i + 1];
                        assertThat(end - surface.cells[i]).as(where + " cell " + i).isBetween(cell - 1, cell + 1);
                        var step = find(view, "step" + i, JComponent.class);
                        assertThat(step.getX()).as(where + " step " + i).isGreaterThanOrEqualTo(surface.cells[i] + ThemePainting.metric(context, DesignMetrics.Role.ROUTE_EDGE));
                        if (i < count - 1) {
                            assertThat(step.getX() + step.getWidth()).as(where + " step " + i + " before its arrow").isLessThanOrEqualTo(surface.arrows[i]);
                            assertThat(surface.arrows[i] + arrow).as(where + " arrow " + i).isLessThanOrEqualTo(surface.cells[i + 1]);
                        }
                        assertThat(at(image, surface.cells[i] + 1, middle)).as(where + " edge " + i).isEqualTo(ThemePainting.painted(context, face.edge(HUES.get(i))));
                        assertThat(find(view, "step" + i + "-icon", JComponent.class).getWidth()).as("the icon gives way to the edge").isZero();
                    }
                    var caption = find(view, "step0-caption", JLabel.class);
                    assertThat(caption.getText()).isEqualTo("THIS COMPUTER");
                    assertThat(caption.getForeground()).isEqualTo(ThemePainting.painted(context, face.pairs().get(1)));
                    view.update(route(count));
                    KitFixtures.render(view, wide, 60, 1);
                    assertThat(find(view, "step0-caption", JLabel.class).getText()).as("after an update").isEqualTo("THIS COMPUTER");
                    assertThat(at(image, 0, middle)).as(where + " box").isEqualTo(ThemePainting.painted(context, face.pairs().get(0)));
                } finally { release(context); }
            });
        }
    }

    @Test void theDefaultRouteKeepsItsTiles() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, route(3), e -> {})) {
                KitFixtures.render(view, 520, 60, 1);
                var surface = (Stacks.StackSurface) view.control("route").component;
                assertThat(surface.cells).isEmpty();
                assertThat(find(view, "step0-icon", JComponent.class).getWidth()).isPositive();
                assertThat(((Labels.IconControl) view.control("step0-icon")).tileSize).isPositive();
                assertThat(find(view, "step0-caption", JLabel.class).getText()).isEqualTo("This computer");
            } finally { release(context); }
        });
    }
}
```

`Labels.IconControl.tileSize` is the tile width `tile(...)` stores (`Labels.java:163`).

Insert the rows into `face-pairs.tsv`: the empty state's after the last `ClassicLabelPolicy` row (before the table's),
the tag's and the route's after the last `ClassicFieldPolicy` row (before the prompt's), in `KitFaces.all()` order:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
def after(anchor, rows):
    global text
    if text.count(anchor) != 1: raise SystemExit(f"face-pairs.tsv: expected one {anchor!r}")
    text = text.replace(anchor, anchor + "".join("\t".join(row) + "\n" for row in rows))
after("classic\tClassicLabelPolicy\ttext.secondary\tsurface.app\tCONTRAST:4.5\n", [
    ("classic", "ClassicEmptyFace", "text.primary", "surface.app", "CONTRAST:4.5"),
    ("classic", "ClassicEmptyFace", "text.secondary", "surface.app", "CONTRAST:4.5")])
hues = ["blue", "green", "amber", "purple", "neutral"]
tags = []
for hue in hues:
    tags.append(("classic", "ClassicTagFace", f"hue.{hue}", f"hue.{hue}.surface", "CONTRAST:4.5"))
    tags.append(("classic", "ClassicTagFace", f"tag.border.{hue}", f"hue.{hue}.surface", "CONTRAST:1.3"))
route = [("classic", "ClassicRouteFace", "border.control", "surface.app", "CONTRAST:1.5"),
         ("classic", "ClassicRouteFace", "text.secondary", "surface.input", "CONTRAST:4.5"),
         ("classic", "ClassicRouteFace", "text.placeholder", "surface.input", "CONTRAST:3.0")]
route += [("classic", "ClassicRouteFace", f"hue.{hue}", "surface.input", "CONTRAST:3.0") for hue in hues]
after("classic\tClassicFieldPolicy\ttext.secondary\tsurface.tableHeader\tCONTRAST:4.5\n", tags + route)
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicIndicatorsTest'`
Expected: FAIL at compile: "cannot find symbol … variable empty" in `KitFaces`, "class ClassicTagFace", "class
ClassicRouteFace" and "variable cells" in `StackSurface`.

- [ ] **Step 3: Add the faces**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/EmptyFace.java`:

```java
package dev.jasper.ui.swing;

/** The empty point (spec 5.2): the tile, title, description and actions, or a bold line, a sentence and one button. */
interface EmptyFace extends Face {
    /** Whether the state shows its tile. */
    boolean tile();
    /** How many of the state's {@code declared} actions show, the first ones. */
    int actions(int declared);
    /** Styles the title and description after the state refreshes. */
    void style(EmptyStates.EmptyControl empty);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultEmptyFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.util.List;

/** The default empty state: the tile over the title and description, then every action. */
final class DefaultEmptyFace implements EmptyFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean tile() { return true; }
    @Override public int actions(int declared) { return declared; }
    @Override public void style(EmptyStates.EmptyControl empty) { }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicEmptyFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import java.util.List;

/**
 * The classic empty state (spec 5.2, DESIGN_LANGUAGE §8): a bold line and one sentence, guarded against the dialog
 * surface, and at most one button; no tile (plan 4c Rulings 10).
 */
final class ClassicEmptyFace implements EmptyFace {
    private final PaintPair title, description;

    /** A face whose text stands on {@code surface}, the kit's dialog surface. */
    ClassicEmptyFace(SemanticColor surface) {
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        description = new PaintPair(SemanticColor.TEXT_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(title, description); }
    @Override public boolean tile() { return false; }
    @Override public int actions(int declared) { return Math.min(1, declared); }

    @Override public void style(EmptyStates.EmptyControl empty) {
        empty.title.setForeground(ThemePainting.painted(empty.context(), title));
        empty.description.setForeground(ThemePainting.painted(empty.context(), description));
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/TagFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Graphics2D;

/** The tag point (spec 5.2): a chip as a borderless tint, or a tiny upper-case label in a 1 px box on the tint. */
interface TagFace extends Face {
    /** Styles a chip's label after the chip refreshes. */
    void style(Indicators.ChipControl chip, Indicators.ChipLabel label);
    /** Paints the chip behind its text. */
    void paint(Indicators.ChipLabel label, Graphics2D g);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultTagFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics2D;
import java.util.List;

/** The default tag: the chip's tint at the tile share, its text as the chip control sets it. */
final class DefaultTagFace implements TagFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public void style(Indicators.ChipControl chip, Indicators.ChipLabel label) { }
    @Override public void paint(Indicators.ChipLabel label, Graphics2D g) { label.paintTile(g); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTagFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The classic tag (spec 5.2 and 7.1, the Workstation "HTTP" chip): the label in upper case, in its hue through the text
 * guard, on the default tint inside a 1 px box in the hue's tag border role (plan 4c Rulings 11).
 */
final class ClassicTagFace implements TagFace {
    private static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
    private final List<PaintPair> pairs;

    ClassicTagFace() {
        var all = new ArrayList<PaintPair>();
        for (var hue : HUES) {
            all.add(new PaintPair(hue(hue), surface(hue), SurfaceContrast.Guard.TEXT));
            all.add(new PaintPair(border(hue), surface(hue), SurfaceContrast.Guard.STRUCTURAL));
        }
        pairs = List.copyOf(all);
    }

    @Override public List<PaintPair> pairs() { return pairs; }

    /** The text pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair text(Nodes.Hue hue) { return pairs.get(2 * index(hue)); }

    /** The box pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair edge(Nodes.Hue hue) { return pairs.get(2 * index(hue) + 1); }

    private static int index(Nodes.Hue hue) { return HUES.indexOf(hue == Nodes.Hue.NONE ? Nodes.Hue.NEUTRAL : hue); }

    private static SemanticColor hue(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        };
    }

    private static SemanticColor surface(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE_SURFACE; case GREEN -> SemanticColor.HUE_GREEN_SURFACE; case AMBER -> SemanticColor.HUE_AMBER_SURFACE;
            case PURPLE -> SemanticColor.HUE_PURPLE_SURFACE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL_SURFACE;
        };
    }

    private static DerivedColor border(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> DerivedColor.TAG_BORDER_BLUE; case GREEN -> DerivedColor.TAG_BORDER_GREEN; case AMBER -> DerivedColor.TAG_BORDER_AMBER;
            case PURPLE -> DerivedColor.TAG_BORDER_PURPLE; case NEUTRAL, NONE -> DerivedColor.TAG_BORDER_NEUTRAL;
        };
    }

    @Override public void style(Indicators.ChipControl chip, Indicators.ChipLabel label) {
        var node = (Nodes.Chip) chip.node;
        label.setText(node.label().toUpperCase(Locale.ROOT));
        // A chip on a selected row keeps the default inked colours, boxed in the ink.
        if (chip.inked) { label.edge = label.getForeground(); return; }
        label.setForeground(ThemePainting.painted(chip.context(), text(node.hue())));
        label.edge = ThemePainting.painted(chip.context(), edge(node.hue()));
    }

    @Override public void paint(Indicators.ChipLabel label, Graphics2D g) {
        label.paintTile(g);
        if (label.edge == null) return;
        int line = ThemePainting.metric(label.context, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(label.context, DesignMetrics.Role.CHIP_RADIUS);
        var ring = new Area(new RoundRectangle2D.Double(0, 0, label.getWidth(), label.getHeight(), 2 * radius, 2 * radius));
        ring.subtract(new Area(new RoundRectangle2D.Double(line, line, label.getWidth() - 2.0 * line, label.getHeight() - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line))));
        g.setColor(label.edge);
        g.fill(ring);
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/RouteFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Graphics;
import java.awt.LayoutManager;
import java.util.List;

/** The route point (spec 5.2): ROUTE steps as icon tiles with arrows, or edged cells with a hue edge and arrows. */
interface RouteFace extends Face {
    /** Whether each step's leading Icon is drawn as a hue tile. */
    boolean tiles();
    /** The ROUTE stack's layout. */
    LayoutManager layout(Stacks.StackControl route);
    /** Paints the strip behind its steps. */
    void paintStrip(Stacks.StackSurface strip, Graphics graphics);
    /** Prepares the strip after its steps are mounted. */
    void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps);
    /** Prepares one step's children after they refresh. */
    void step(Stacks.StackControl step, List<ControlRegistry.Control> controls);
    /** A step's layout. */
    LayoutManager stepLayout(Stacks.StackControl step);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultRouteFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.util.List;

/** The default route: equal columns with the arrow opening each later one, each step led by its hue tile. */
final class DefaultRouteFace implements RouteFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean tiles() { return true; }
    @Override public LayoutManager layout(Stacks.StackControl route) { return new Stacks.RouteLayout(route); }
    @Override public void paintStrip(Stacks.StackSurface strip, Graphics graphics) { strip.paintRoute(graphics); }
    @Override public void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps) { }
    @Override public void step(Stacks.StackControl step, List<ControlRegistry.Control> controls) { }
    @Override public LayoutManager stepLayout(Stacks.StackControl step) { return new Stacks.RouteStepLayout(step.context()); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicRouteFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;

/**
 * The classic route (spec 5.2, the New Tunnel mock; plan 4c Rulings 12): one boxed strip on the input surface, equal
 * cells each led by a 3 px edge in its step's hue (the step's leading Icon is hidden), upper-case captions, and a drawn
 * arrow at the end of every cell but the last.
 */
final class ClassicRouteFace implements RouteFace {
    private static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
    private final PaintPair box, caption, arrow;
    private final List<PaintPair> edges;

    /** A face whose strip stands on {@code surface}, the kit's dialog surface. */
    ClassicRouteFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        caption = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);
        arrow = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        edges = HUES.stream().map(hue -> new PaintPair(role(hue), SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR)).toList();
    }

    @Override public List<PaintPair> pairs() {
        var all = new java.util.ArrayList<PaintPair>(List.of(box, caption, arrow));
        all.addAll(edges);
        return List.copyOf(all);
    }

    /** The edge pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair edge(Nodes.Hue hue) { return edges.get(HUES.indexOf(hue == Nodes.Hue.NONE ? Nodes.Hue.NEUTRAL : hue)); }

    private static SemanticColor role(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        };
    }

    /** A step's hue: its leading Icon's, else neutral. */
    static Nodes.Hue hue(Nodes.Node step) {
        Nodes.Node lead = step instanceof Nodes.Stack stack && !stack.children().isEmpty() ? stack.children().getFirst() : step;
        return lead instanceof Nodes.Icon icon ? icon.hue() : Nodes.Hue.NEUTRAL;
    }

    @Override public boolean tiles() { return false; }
    @Override public LayoutManager layout(Stacks.StackControl route) { return new EdgedLayout(route); }

    @Override public void strip(Stacks.StackControl route, List<ControlRegistry.Control> steps) {
        ((Stacks.StackSurface) route.component).hues = ((Nodes.Stack) route.node).children().stream().map(ClassicRouteFace::hue).toList();
    }

    /** Upper-case captions in their guarded ink, kept through every later refresh by the text control's hook. */
    @Override public void step(Stacks.StackControl step, List<ControlRegistry.Control> controls) {
        for (var control : controls)
            if (control.node instanceof Nodes.Text text && text.role() == Nodes.TextRole.CAPTION && control instanceof Labels.TextControl label) {
                label.styled = shown -> {
                    shown.setText(text.text().toUpperCase(Locale.ROOT));
                    shown.setForeground(ThemePainting.painted(step.context(), caption));
                };
                label.styled.accept((JLabel) label.component);
            }
    }

    @Override public LayoutManager stepLayout(Stacks.StackControl step) { return new LinesLayout(); }

    /** A classic step: its texts stacked and centred; its leading Icon gets no room (its cell's hue edge stands for it). */
    static final class LinesLayout implements Composition.WidthLayout {
        static boolean icon(Component c) { return c instanceof JLabel label && label.getIcon() != null && (label.getText() == null || label.getText().isEmpty()); }
        List<Component> lines(Container parent) { return Arrays.stream(parent.getComponents()).filter(c -> c.isVisible() && !icon(c)).toList(); }
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public int height(Container parent, int width) { return lines(parent).stream().mapToInt(c -> c.getPreferredSize().height).sum(); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = lines(parent).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(width, height(parent, width));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, height(parent, 0)); }
        @Override public void layoutContainer(Container parent) {
            for (var child : parent.getComponents()) if (icon(child)) child.setBounds(0, 0, 0, 0);
            int y = Math.max(0, (parent.getHeight() - height(parent, parent.getWidth())) / 2);
            for (var line : lines(parent)) {
                int h = line.getPreferredSize().height;
                line.setBounds(0, y, parent.getWidth(), h);
                y += h;
            }
        }
    }

    @Override public void paintStrip(Stacks.StackSurface strip, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            var c = strip.context;
            ThemePainting.antialias(g);
            if (!strip.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), top = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_TOP);
            int width = strip.getWidth(), height = strip.getHeight() - top;
            g.setColor(ThemePainting.color(c, SemanticColor.SURFACE_INPUT));
            g.fillRect(0, top, width, height);
            g.setColor(ThemePainting.painted(c, box));
            g.fillRect(0, top, width, line);
            g.fillRect(0, top + height - line, width, line);
            g.fillRect(0, top, line, height);
            g.fillRect(width - line, top, line, height);
            int edge = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_EDGE);
            for (int i = 0; i < strip.cells.length; i++) {
                var hue = i < strip.hues.size() ? strip.hues.get(i) : Nodes.Hue.NEUTRAL;
                g.setColor(ThemePainting.painted(c, edge(hue)));
                g.fillRect(strip.cells[i], top + line, edge, Math.max(0, height - 2 * line));
            }
            int size = ThemePainting.metric(c, DesignMetrics.Role.ROUTE_ARROW);
            var ink = ThemePainting.painted(c, arrow);
            for (int x : strip.arrows) arrow(g, x, top + height / 2.0, size, ink);
        } finally { g.dispose(); }
    }

    /** A rightward arrow {@code size} wide from {@code x}, centred on {@code cy}: a shaft and a filled head. */
    static void arrow(Graphics2D g, double x, double cy, double size, Color color) {
        double head = size * .45;
        var shaft = (Graphics2D) g.create();
        try {
            shaft.setColor(color);
            shaft.setStroke(new BasicStroke((float) Math.max(1, size / 12)));
            shaft.draw(new Line2D.Double(x, cy, x + size - head * .6, cy));
        } finally { shaft.dispose(); }
        ClassicGlyphs.right(g, x + size - head * .8, cy, head, color);
    }

    /**
     * Equal cells across the strip inside its edge: each step after its cell's hue edge and padding, truncated before
     * its cell's arrow (or the strip's end padding for the last), centred on the strip's height.
     */
    static final class EdgedLayout implements Composition.WidthLayout {
        final Stacks.StackControl owner;

        EdgedLayout(Stacks.StackControl owner) { this.owner = owner; }

        int metric(DesignMetrics.Role role) { return ThemePainting.metric(owner.context(), role); }

        List<Component> steps(Container parent) { return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList(); }

        /** A cell's room besides its step: the edge, the padding before the step, and the arrow (or end padding) after it. */
        int chrome() {
            return metric(DesignMetrics.Role.ROUTE_EDGE) + metric(DesignMetrics.Role.ROUTE_PADDING) + metric(DesignMetrics.Role.ROUTE_ARROW_GAP)
                + metric(DesignMetrics.Role.ROUTE_ARROW) + metric(DesignMetrics.Role.ROUTE_ARROW_INSET);
        }

        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public int height(Container parent, int width) {
            int tallest = steps(parent).stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0);
            return metric(DesignMetrics.Role.ROUTE_TOP) + Math.max(metric(DesignMetrics.Role.ROUTE_HEIGHT),
                tallest + 2 * (metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_VERTICAL_PADDING)));
        }

        int width(List<Component> steps, boolean minimum) {
            int widest = steps.stream().mapToInt(c -> (minimum ? c.getMinimumSize() : c.getPreferredSize()).width).max().orElse(0);
            return 2 * metric(DesignMetrics.Role.HAIRLINE) + steps.size() * (widest + chrome());
        }

        @Override public Dimension preferredLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, false); return new Dimension(w, height(parent, w)); }
        @Override public Dimension minimumLayoutSize(Container parent) { var steps = steps(parent); int w = width(steps, true); return new Dimension(w, height(parent, w)); }

        @Override public void layoutContainer(Container parent) {
            var steps = steps(parent);
            var surface = (Stacks.StackSurface) parent;
            int count = steps.size(), line = metric(DesignMetrics.Role.HAIRLINE), top = metric(DesignMetrics.Role.ROUTE_TOP);
            int height = parent.getHeight() - top, inner = Math.max(0, parent.getWidth() - 2 * line);
            surface.cells = new int[count];
            surface.arrows = new int[Math.max(0, count - 1)];
            for (int i = 0; i < count; i++) {
                int start = line + (int) ((long) inner * i / count), end = line + (int) ((long) inner * (i + 1) / count);
                boolean last = i == count - 1;
                surface.cells[i] = start;
                if (!last) surface.arrows[i] = end - metric(DesignMetrics.Role.ROUTE_ARROW_INSET) - metric(DesignMetrics.Role.ROUTE_ARROW);
                int x = start + metric(DesignMetrics.Role.ROUTE_EDGE) + metric(DesignMetrics.Role.ROUTE_PADDING);
                int limit = last ? end - metric(DesignMetrics.Role.ROUTE_PADDING) : surface.arrows[i] - metric(DesignMetrics.Role.ROUTE_ARROW_GAP);
                var step = steps.get(i);
                int stepHeight = Math.min(height, step.getPreferredSize().height);
                step.setBounds(x, top + (height - stepHeight) / 2, Math.min(step.getPreferredSize().width, Math.max(0, limit - x)), stepHeight);
            }
        }
    }
}
```

- [ ] **Step 4: Route the empty state, the chip and the route through the faces**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 2 step 4: the empty state, the chip and the route ask their faces."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final TableFace table;
''', '''    final EmptyFace empty;
    final TableFace table;
''', 1),
    ('''    final PromptFace prompt;
''', '''    final TagFace tag;
    final RouteFace route;
    final PromptFace prompt;
''', 1),
    ('''        table = switch (controls.table()) {''', '''        empty = switch (controls.empty()) {
            case TILE -> new DefaultEmptyFace();
            case TEXT -> new ClassicEmptyFace(colors.dialogSurface());
        };
        tag = switch (controls.tag()) {
            case TINT -> new DefaultTagFace();
            case BOXED -> new ClassicTagFace();
        };
        route = switch (controls.route()) {
            case TILES -> new DefaultRouteFace();
            case EDGED -> new ClassicRouteFace(colors.dialogSurface());
        };
        table = switch (controls.table()) {''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, table, tree, stepper, buttons, fields, prompt); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, empty, table, tree, stepper, buttons, fields, tag, route, prompt); }''', 1)])
edit(S + "EmptyStates.java", [
    ('''        int tileSize() { return metric(panel ? DesignMetrics.Role.PANEL_EMPTY_TILE : DesignMetrics.Role.EMPTY_TILE); }''',
     '''        int tileSize() { return !context().faces().empty.tile() ? 0 : metric(panel ? DesignMetrics.Role.PANEL_EMPTY_TILE : DesignMetrics.Role.EMPTY_TILE); }''', 1),
    ('''        int gap(int index) {
            if (!panel) return metric(DesignMetrics.Role.ICON_GAP);''', '''        int gap(int index) {
            if (index == 0 && tileSize() == 0) return 0;
            if (!panel) return metric(DesignMetrics.Role.ICON_GAP);''', 1),
    ('''            content.setLayout(new ContentLayout(this));''', '''            context().faces().empty.style(this);
            content.setLayout(new ContentLayout(this));''', 1),
    ('''            Stacks.mount(
                    actions,
                    controls.subList(1, controls.size()).stream().map(c -> c.component).toList());''', '''            // The classic state places its first action only (spec 5.2: at most one button); the others keep their controls.
            Stacks.mount(
                    actions,
                    controls.subList(1, controls.size()).stream().limit(context().faces().empty.actions(controls.size() - 1)).map(c -> c.component).toList());''', 1)])
edit(S + "Indicators.java", [
    ('''            label.getAccessibleContext().setAccessibleName(chip.label()); label.revalidate(); label.repaint();''',
     '''            context().faces().tag.style(this, (ChipLabel) label);
            label.getAccessibleContext().setAccessibleName(chip.label()); label.revalidate(); label.repaint();''', 1),
    ('''        /** The chip's tile surface role colour; the label paints it at the tile share. */
        Color fill;''', '''        /** The chip's tile surface role colour; the label paints it at the tile share. */
        Color fill;
        /** The classic tag's 1 px box, or null for none (plan 4c). */
        Color edge;''', 1),
    ('''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.CHIP_RADIUS);
                g.setColor(ColorMath.opacity(fill == null ? getForeground() : fill, ThemePainting.tileShare(context))); g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }''', '''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try { context.faces().tag.paint(this, g); } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
        /** The tint at the tile share (the default face, and under the classic box). */
        void paintTile(Graphics2D g) {
            ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            int radius = ThemePainting.metric(context, DesignMetrics.Role.CHIP_RADIUS);
            g.setColor(ColorMath.opacity(fill == null ? getForeground() : fill, ThemePainting.tileShare(context))); g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
        }''', 1)])
edit(S + "Stacks.java", [
    ('''        Nodes.LayoutRole role;
        StackSurface(KitContext context){this.context=context;}''', '''        Nodes.LayoutRole role;
        /** A classic route strip's cell starts and the hue of each step's edge (ClassicRouteFace, plan 4c). */
        int[] cells=new int[0];
        List<Nodes.Hue> hues=List.of();
        StackSurface(KitContext context){this.context=context;}''', 1),
    ('''            if(route){paintRoute(graphics);return;}''', '''            if(route){context.faces().route.paintStrip(this,graphics);return;}''', 1),
    ('''        private void paintRoute(Graphics graphics){''', '''        void paintRoute(Graphics graphics){''', 1),
    ('''stack.role() == Nodes.LayoutRole.ROUTE ? new RouteLayout(this)''', '''stack.role() == Nodes.LayoutRole.ROUTE ? context().faces().route.layout(this)''', 1),
    ('''routeStep ? new RouteStepLayout(context())''', '''routeStep ? context().faces().route.stepLayout(this)''', 1),
    ('''            barTexts = controls.stream().filter(c -> c.node instanceof Nodes.Text).map(c -> (Component)c.component)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
        }''', '''            barTexts = controls.stream().filter(c -> c.node instanceof Nodes.Text).map(c -> (Component)c.component)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
            // The classic faces adopt what they draw (plan 4c): a route strip's hues, a route step's captions.
            if (((Nodes.Stack) node).role() == Nodes.LayoutRole.ROUTE) context().faces().route.strip(this, controls);
            if (routeStep) context().faces().route.step(this, controls);
        }

        /** Whether this ROUTE strip's steps lead with hue tiles (the default) rather than the classic hue edges. */
        boolean routeTiles() { return context().faces().route.tiles(); }''', 1)])
edit(S + "ViewReconciler.java", [
    ('''                glyph = view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.ROUTE_GLYPH);''',
     '''                glyph = view.context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.ROUTE_GLYPH);
            // The classic strip edges its cells in the steps' hues instead of tiling their icons.
            boolean tiled = !(next.get(route.id()) instanceof Stacks.StackControl strip) || strip.routeTiles();''', 1),
    ('''                if (tiles && lead instanceof Nodes.Icon && next.get(lead.id()) instanceof Labels.IconControl icon) icon.tile(tile, tile, glyph, false);''',
     '''                if (tiles && tiled && lead instanceof Nodes.Icon && next.get(lead.id()) instanceof Labels.IconControl icon) icon.tile(tile, tile, glyph, false);''', 1)])
PY
```

The defaults keep today's code: `tileSize()` and `gap(0)` are unchanged while the face shows the tile, the default face
places every action and styles nothing, `ChipLabel.paintTile` is the former body (the outer `paintComponent` keeps its
graphics copy and `super.paintComponent`), the default route face lays out with `RouteLayout` and paints with the former
`paintRoute`, and the reconciler tiles the steps' icons whenever the strip's face tiles them; a step keeps `RouteStepLayout`. Under the
classic faces the hidden actions and the route icons are handled by placement and layout, and the captions by
`TextControl.styled`, because `KitView` refreshes every control once more after the first pass (fact 20). `ViewReconciler` names
only `Stacks.StackControl.routeTiles()`, never a face. Stacks already imports `java.util.List`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicIndicatorsTest` 5 tests; `PaintSpecTest` with the twenty new rows; `EmptyStatesTest`,
`PanelCompositionTest`, `PanelSurfacesTest`, `TableVocabularyTest` and the gallery suites green).

- [ ] **Step 6: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`. Open `jasper-ui/build/classic-faces/empty.png` and `route.png` and compare them with the
Workstation's "No Tunnels" panel and the New Tunnel route strip by eye.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicIndicatorsTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: the empty state, the tag and the route

The empty, tag and route points' faces. The classic empty state drops its tile and
shows its first action only; the classic tag is an upper-case label in a 1 px tag
border box on its tint; the classic route is one boxed strip of equal cells led by
their steps' hue edges, with upper-case captions and drawn arrows. The default faces
keep today's code.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 3: The ordered list: the side column

The ordered-list point's faces (spec 5.2, 6.4). The default keeps today's rounded box with per-row up, down and remove
glyphs and the add action under the rows. The classic list draws a square boxed list with zebra rows and a muted
data-face index column, and a side column of equal buttons beside it: the add action first (a LINK one drawn as a
standard button), then Remove, Move Up and Move Down, acting on the selection and emitting the row commands' events;
a following entry row in a VERTICAL parent is adopted so the column spans it (Rulings 13 to 16).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/OrderedFace.java`, `DefaultOrderedFace.java`, `ClassicOrderedFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `ItemLists.java:18-524`, `InteractiveRows.java:33-38,755-768`, `Stacks.java` (Task 2's children lines), `Buttons.java:124-125`, `ClassicButtonFace.java` (4b Task 2), `ViewReconciler.java:101`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicOrderedTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (two rows inserted)

**Interfaces:**
- Consumes: Tasks 1 and 2's `KitFaces`; plan 4b's `ClassicButtonFace` (its `label` pair is `pairs().get(1)`) and
  `KitButton.paintRaised`; plan 4a's `SIDE_COLUMN_WIDTH`, `SIDE_COLUMN_GAP`, `SIDE_BUTTON_GAP`, classic
  `ORDERED_ROW_HEIGHT` (20) and `ORDERED_RADIUS` (0), `ControlVariants.OrderedList` (`INLINE`, `SIDE_COLUMN`).
- Produces: `interface OrderedFace extends Face { boolean rowCommands(); Nodes.TextRole indexRole(); void
  styleIndex(JLabel, KitContext); void paintCanvas(InteractiveRows.Canvas, Graphics); void paintPane(ItemLists.ListControl,
  Graphics2D); void mount(ItemLists.ListControl, ControlRegistry.Control add); void span(Stacks.StackControl,
  List<ControlRegistry.Control>); }`; `final OrderedFace KitFaces.ordered`; `ItemLists.ListPane` (a `JScrollPane`),
  `ItemLists.SideColumn` (a `JPanel` with `addAction(ControlRegistry.Control)`, `sync()`, `target()` and the
  `removeCommand`, `upCommand`, `downCommand` button controls); on `ListControl`: `SideColumn side`, `JComponent entry`,
  `void entry(JComponent)`; `void InteractiveRows.Canvas.paintOrdered(Graphics)`; `boolean Buttons.KitButton.sideColumn`;
  `ClassicOrderedFace.SideLayout` and `ClassicOrderedFace.Reserve`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicOrderedTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's ordered-list point: the classic side column acting on the selection; the default per-row commands. */
@SuppressWarnings("try")
class ClassicOrderedTest {
    static final ControlState READY = ControlState.READY;

    static ListItem marker(String id, String text) {
        return new ListItem(id, Optional.empty(), List.of(new Nodes.Text(id + "-text", READY, text, Nodes.TextRole.MONO)), List.of(), List.of());
    }

    static Nodes.ItemList markers(List<ListItem> items, Optional<Nodes.Button> add) {
        return new Nodes.ItemList("markers", READY, Nodes.ListKind.ORDERED, items, Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, add);
    }

    static List<ListItem> three() { return List.of(marker("csproj", "*.csproj"), marker("xcode", "*.xcodeproj"), marker("flake", "flake.nix")); }

    /** The Project Index composition: the list, then its entry row, in a VERTICAL stack. */
    static ViewSpec withEntry(List<ListItem> items) {
        var entry = new Nodes.Stack("entry", READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field("marker", READY, Nodes.FieldKind.TEXT, "", "Add…", "Marker", "", Optional.empty(), Nodes.FieldPresentation.EMBEDDED),
            new Nodes.Button("marker-add", READY, "Add", "", Nodes.ButtonKind.SECONDARY, "marker-add", "")));
        return new ViewSpec(new Nodes.Stack("group", READY, Nodes.LayoutRole.VERTICAL, List.of(markers(items, Optional.empty()), entry)));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    static Buttons.KitButton command(KitView view, String suffix) { return find(view, "@ordered/markers/side/" + suffix, Buttons.KitButton.class); }

    /** Review Focus 2: nothing to act on disables every command; the ends disable Move Up and Move Down. */
    @Test void theSideColumnActsOnTheSelectionAndDisablesAtTheEnds() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, withEntry(three()), events::add)) {
                KitFixtures.capture(context, view.spec(), 600, 120, "ordered");
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(list.side).isNotNull();
                assertThat(List.of(command(view, "remove").isEnabled(), command(view, "up").isEnabled(), command(view, "down").isEnabled()))
                    .containsExactly(false, false, false);
                list.select("flake", false);
                KitFixtures.render(view, 600, 120, 1);
                assertThat(List.of(command(view, "remove").isEnabled(), command(view, "up").isEnabled(), command(view, "down").isEnabled()))
                    .containsExactly(true, true, false);
                assertThat(list.selectionShown(2)).as("the selection shows while a button has focus").isTrue();
                events.clear();
                command(view, "up").doClick(0);
                assertThat(events).containsExactly(new UiEvent.Reordered("markers", "flake", 1));
                list.select("csproj", false);
                KitFixtures.render(view, 600, 120, 1);
                assertThat(command(view, "up").isEnabled()).isFalse();
                events.clear();
                command(view, "down").doClick(0);
                command(view, "remove").doClick(0);
                assertThat(events).containsExactly(new UiEvent.Reordered("markers", "csproj", 1), new UiEvent.Removed("markers", "csproj"));
                for (var row : list.mounted.values())
                    assertThat(row.controls.keySet()).as("no per-row commands").noneMatch(id -> id.endsWith("/up") || id.endsWith("/remove"));
            } finally { release(context); }
        });
    }

    /** Review Focus 2: after the host removes the selected row, no command acts on it. */
    @Test void aRowTheHostRemovedIsNoLongerTheTarget() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, withEntry(three()), events::add)) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                list.select("flake", false);
                view.update(withEntry(List.of(marker("csproj", "*.csproj"), marker("xcode", "*.xcodeproj"))));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(list.side.target()).isNull();
                events.clear();
                command(view, "remove").doClick(0);
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void theSideColumnSpansTheListAndItsEntryRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, withEntry(three()), e -> {})) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var entry = find(view, "entry", JComponent.class);
                assertThat(entry.getParent()).as("the entry row is adopted").isSameAs(list.scroll);
                var viewport = list.scroll.getViewport();
                int gap = ThemePainting.metric(context, DesignMetrics.Role.SIDE_COLUMN_GAP), width = ThemePainting.metric(context, DesignMetrics.Role.SIDE_COLUMN_WIDTH);
                assertThat(list.side.getX() + list.side.getWidth()).isEqualTo(list.scroll.getWidth());
                assertThat(list.side.getY()).isZero();
                assertThat(list.side.getWidth()).isGreaterThanOrEqualTo(width);
                assertThat(entry.getY()).isGreaterThanOrEqualTo(viewport.getY() + viewport.getHeight());
                assertThat(entry.getWidth()).isEqualTo(list.scroll.getWidth() - list.side.getWidth() - gap);
                assertThat(list.scroll.getHeight()).isGreaterThanOrEqualTo(list.side.getPreferredSize().height);
            } finally { release(context); }
        });
    }

    /** Rulings 15: a LINK add action leads the side column as a standard button, without its glyph. */
    @Test void aLinkAddActionLeadsTheSideColumnAsAStandardButton() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var add = new Nodes.Button("key-add", READY, "Add Key…", "add", Nodes.ButtonKind.LINK, "key-add", "");
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(markers(List.of(), Optional.of(add))), events::add)) {
                KitFixtures.render(view, 420, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var button = find(view, "key-add", Buttons.KitButton.class);
                assertThat(list.side.getComponent(0)).isSameAs(button);
                assertThat(button.sideColumn).isTrue();
                assertThat(button.getIcon()).isNull();
                assertThat(button.getForeground()).isEqualTo(ThemePainting.painted(context, context.faces().buttons.pairs().get(1)));
                assertThat(list.footer).isNull();
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("key-add"));
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the box and the index as their guards make them, over every palette; the rows are striped. */
    @Test void theBoxAndIndexPaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().ordered.pairs();
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, new ViewSpec(markers(three(), Optional.empty())), e -> {})) {
                KitFixtures.render(view, 420, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(at(paint(list.scroll), 0, 5)).as(where + " box").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                var index = find(view, list.internalId("csproj", "index"), JLabel.class);
                assertThat(index.getForeground()).as(where + " index").isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(index.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.MONO_CAPTION));
                var odd = list.mounted.get("xcode");
                assertThat(at(paint(list.canvas), list.canvas.getWidth() - 2, odd.getY() + odd.getHeight() / 2)).as(where + " zebra")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ZEBRA));
            } finally { release(context); }
        });
    }

    @Test void theDefaultKeepsItsRowCommandsAndFooter() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            var add = new Nodes.Button("key-add", READY, "Add Stored Key...", "add", Nodes.ButtonKind.LINK, "key-add", "");
            try (var view = new KitView(context, new ViewSpec(markers(three(), Optional.of(add))), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(list.side).isNull();
                assertThat(list.footer).isSameAs(find(view, "key-add", Buttons.KitButton.class));
                assertThat(list.mounted.get("csproj").controls).containsKey(list.internalId("csproj", "up"));
                assertThat(list.scroll).isInstanceOf(ItemLists.ListPane.class);
            } finally { release(context); }
        });
    }
}
```

The side column's commands carry the synthetic ids `@ordered/<list>/side/remove`, `…/up` and `…/down`; a row's index
is `ListControl.internalId(row, "index")` (`ItemLists.java:484`). `Nodes.Field`'s nine-argument constructor is
`(id, state, kind, value, hint, label, trailingAction, numeric, presentation)`; EMBEDDED leaves the label to the host.

Insert the ordered list's rows into `face-pairs.tsv` after the last `ClassicStepperFace` row (`KitFaces.all()` puts the
ordered list after the stepper):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
anchor = "classic\tClassicStepperFace\ttext.placeholder\tsurface.input\tCONTRAST:4.5\n"
if text.count(anchor) != 1: raise SystemExit("face-pairs.tsv: expected one last ClassicStepperFace row")
rows = ("classic\tClassicOrderedFace\tborder.control\tsurface.app\tCONTRAST:1.5\n"
        "classic\tClassicOrderedFace\ttext.tertiary\tsurface.input\tCONTRAST:4.5\n")
path.write_text(text.replace(anchor, anchor + rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicOrderedTest'`
Expected: FAIL at compile: "cannot find symbol … variable ordered" in `KitFaces`, "variable side" in `ListControl`,
"class ListPane" and "variable sideColumn".

- [ ] **Step 3: Add the faces**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/OrderedFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JLabel;

/** The ordered-list point (spec 5.2): per-row commands, or a side column of equal buttons acting on the selection. */
interface OrderedFace extends Face {
    /** Whether each row carries its own up, down and remove commands and the add action sits under the rows. */
    boolean rowCommands();
    /** The role of a row's index. */
    Nodes.TextRole indexRole();
    /** Styles a row's index after it refreshes. */
    void styleIndex(JLabel index, KitContext context);
    /** Paints the list's canvas behind its rows. */
    void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics);
    /** Paints the list's pane behind its viewport. */
    void paintPane(ItemLists.ListControl list, Graphics2D g);
    /** Mounts the list's add action, or null, when the rows carry no commands. */
    void mount(ItemLists.ListControl list, ControlRegistry.Control add);
    /** Lets a side column span an entry row that follows its list in a VERTICAL stack. */
    void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultOrderedFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JLabel;

/** The default ordered list: the rounded box with row dividers, per-row commands and the add action under the rows. */
final class DefaultOrderedFace implements OrderedFace {
    @Override public List<PaintPair> pairs() { return List.of(); }
    @Override public boolean rowCommands() { return true; }
    @Override public Nodes.TextRole indexRole() { return Nodes.TextRole.CAPTION; }
    @Override public void styleIndex(JLabel index, KitContext context) { }
    @Override public void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics) { canvas.paintOrdered(graphics); }
    @Override public void paintPane(ItemLists.ListControl list, Graphics2D g) { }
    @Override public void mount(ItemLists.ListControl list, ControlRegistry.Control add) { }
    @Override public void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls) { }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicOrderedFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.ScrollPaneLayout;
import javax.swing.border.Border;

/**
 * The classic ordered list (spec 5.2 and 6.4, the Project Index and Add Host mocks; plan 4c Rulings 13 to 16): a square
 * box of striped rows with a muted data-face index, and a side column of equal buttons beside it, top-aligned, spanning
 * an entry row its VERTICAL parent holds right after it.
 */
final class ClassicOrderedFace implements OrderedFace {
    private final PaintPair box, index;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicOrderedFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
        index = new PaintPair(SemanticColor.TEXT_TERTIARY, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(box, index); }
    @Override public boolean rowCommands() { return false; }
    @Override public Nodes.TextRole indexRole() { return Nodes.TextRole.MONO_CAPTION; }
    @Override public void styleIndex(JLabel label, KitContext context) { label.setForeground(ThemePainting.painted(context, index)); }

    /** Odd rows in {@code surface.zebra}; the box and its fill are the pane's. */
    @Override public void paintCanvas(InteractiveRows.Canvas canvas, Graphics graphics) {
        graphics.setColor(ThemePainting.role(canvas.owner.context(), DerivedColor.SURFACE_ZEBRA));
        for (var row : canvas.owner.mounted.values())
            if (row.index % 2 == 1) graphics.fillRect(0, row.getY(), canvas.getWidth(), row.getHeight());
    }

    /** The box's fill, inside the edge {@link Reserve} paints. */
    @Override public void paintPane(ItemLists.ListControl list, Graphics2D g) {
        int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
        int width = list.scroll.getWidth() - column(list), height = list.scroll.getHeight() - entry(list);
        g.setColor(ThemePainting.color(list.context(), SemanticColor.SURFACE_INPUT));
        g.fillRect(line, line, Math.max(0, width - 2 * line), Math.max(0, height - 2 * line));
    }

    @Override public void mount(ItemLists.ListControl list, ControlRegistry.Control add) {
        if (list.side == null) {
            list.side = new ItemLists.SideColumn(list);
            list.scroll.setLayout(new SideLayout(list));
            list.scroll.setBorder(new Reserve(list, box));
            list.scroll.add(list.side);
        }
        list.side.addAction(add);
    }

    @Override public void span(Stacks.StackControl stack, List<ControlRegistry.Control> controls) {
        if (((Nodes.Stack) stack.node).role() != Nodes.LayoutRole.VERTICAL) return;
        for (int i = 0; i < controls.size(); i++)
            if (controls.get(i) instanceof ItemLists.ListControl list && list.value().kind() == Nodes.ListKind.ORDERED)
                list.entry(i + 1 < controls.size() && controls.get(i + 1) instanceof Stacks.StackControl row
                    && ((Nodes.Stack) row.node).role() == Nodes.LayoutRole.INLINE ? row.component : null);
    }

    /** The room right of the box: the side column and its gap. */
    static int column(ItemLists.ListControl list) {
        return list.side == null ? 0 : list.side.getPreferredSize().width + ThemePainting.metric(list.context(), DesignMetrics.Role.SIDE_COLUMN_GAP);
    }

    /** The room under the box: an adopted entry row and its gap. */
    static int entry(ItemLists.ListControl list) {
        var entry = list.entry;
        return entry == null || !entry.isVisible() || entry.getParent() != list.scroll ? 0
            : entry.getPreferredSize().height + ThemePainting.metric(list.context(), DesignMetrics.Role.ROW_GAP);
    }

    /** The pane's border: the 1 px box around the rows, and the room for the side column and the entry row outside it. */
    static final class Reserve implements Border {
        final ItemLists.ListControl list;
        final PaintPair edge;

        Reserve(ItemLists.ListControl list, PaintPair edge) { this.list = list; this.edge = edge; }

        @Override public Insets getBorderInsets(Component c) {
            int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
            return new Insets(line, line, line + entry(list), line + column(list));
        }

        @Override public boolean isBorderOpaque() { return false; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            int line = ThemePainting.metric(list.context(), DesignMetrics.Role.HAIRLINE);
            int boxWidth = width - column(list), boxHeight = height - entry(list);
            g.setColor(ThemePainting.painted(list.context(), edge));
            g.fillRect(x, y, boxWidth, line);
            g.fillRect(x, y + boxHeight - line, boxWidth, line);
            g.fillRect(x, y, line, boxHeight);
            g.fillRect(x + boxWidth - line, y, line, boxHeight);
        }
    }

    /** The pane's layout: the rows inside the reserve, the side column at the top right, the entry row under the box. */
    static final class SideLayout extends ScrollPaneLayout {
        final ItemLists.ListControl list;

        SideLayout(ItemLists.ListControl list) { this.list = list; }

        @Override public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            var side = list.side;
            if (side != null) {
                var size = side.getPreferredSize();
                side.setBounds(parent.getWidth() - size.width, 0, size.width, size.height);
            }
            var entry = list.entry;
            if (entry != null && entry.getParent() == parent && entry.isVisible()) {
                int height = entry.getPreferredSize().height;
                entry.setBounds(0, parent.getHeight() - height, Math.max(0, parent.getWidth() - column(list)), height);
            }
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            var size = super.preferredLayoutSize(parent);
            if (list.side != null) size.height = Math.max(size.height, list.side.getPreferredSize().height);
            return size;
        }
    }
}
```

- [ ] **Step 4: Route the ordered list through the faces**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 3 step 4: the ordered list's pane, side column, index, canvas and commands ask the ordered face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final TagFace tag;
''', '''    final OrderedFace ordered;
    final TagFace tag;
''', 1),
    ('''        tag = switch (controls.tag()) {''', '''        ordered = switch (controls.orderedList()) {
            case INLINE -> new DefaultOrderedFace();
            case SIDE_COLUMN -> new ClassicOrderedFace(colors.dialogSurface());
        };
        tag = switch (controls.tag()) {''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, empty, table, tree, stepper, buttons, fields, tag, route, prompt); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, empty, table, tree, stepper, ordered, buttons, fields, tag, route, prompt); }''', 1)])
edit(S + "InteractiveRows.java", [
    ('''            return !(node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) || canvas.isFocusOwner();''',
     '''            // A classic side column takes focus from the rows to act on them, so its list shows the selection unfocused.
            return !(node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) || canvas.isFocusOwner()
                || !context().faces().ordered.rowCommands();''', 1),
    ('''            if (owner.node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) {
                var g = (Graphics2D)graphics.create();
                try {
                    ThemePainting.rounded(g, this, owner.context(), ThemePainting.color(owner.context(), SemanticColor.SURFACE_INPUT),
                        ThemePainting.color(owner.context(), SemanticColor.BORDER_INPUT), false, false, DesignMetrics.Role.ORDERED_RADIUS);
                    g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_SUBTLE));
                    int stroke = ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
                    for (int i = 1; i <= owner.size(); i++) g.fillRect(stroke, i * owner.rowHeight, Math.max(0, getWidth() - 2 * stroke), stroke);
                } finally { g.dispose(); }
            }''', '''            if (owner.node instanceof Nodes.ItemList list && list.kind() == Nodes.ListKind.ORDERED) owner.context().faces().ordered.paintCanvas(this, graphics);''', 1),
    ('''        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, owner.rowsHeight()''', '''        /** An ORDERED list's rounded box and row dividers (the default ordered face). */
        void paintOrdered(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.rounded(g, this, owner.context(), ThemePainting.color(owner.context(), SemanticColor.SURFACE_INPUT),
                    ThemePainting.color(owner.context(), SemanticColor.BORDER_INPUT), false, false, DesignMetrics.Role.ORDERED_RADIUS);
                g.setColor(ThemePainting.color(owner.context(), SemanticColor.BORDER_SUBTLE));
                int stroke = ThemePainting.metric(owner.context(), DesignMetrics.Role.HAIRLINE);
                for (int i = 1; i <= owner.size(); i++) g.fillRect(stroke, i * owner.rowHeight, Math.max(0, getWidth() - 2 * stroke), stroke);
            } finally { g.dispose(); }
        }
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, owner.rowsHeight()''', 1)])
edit(S + "ItemLists.java", [
    ('''        void data() {
            nodeRows.clear();
            for (int i = 0; i < size(); i++) {
                var item = value().items().get(i);''', '''        void data() {
            // The side column's own commands are not the reconciler's; they follow the theme and fonts with the list.
            if (side != null) side.restyle();
            nodeRows.clear();
            for (int i = 0; i < size(); i++) {
                var item = value().items().get(i);''', 1),
    ('''        ListControl(KitView view, Nodes.ItemList node) {
            super(view, node);''', '''        /** A classic ordered list's side column (plan 4c), or null. */
        SideColumn side;
        /** The entry row a classic side column spans with the rows (a VERTICAL parent's INLINE row after the list), or null. */
        JComponent entry;

        ListControl(KitView view, Nodes.ItemList node) {
            super(view, node, new ListPane());
            ((ListPane) scroll).owner = this;''', 1),
    ('''        @Override
        void children(List<ControlRegistry.Control> controls) {
            JComponent next = controls.isEmpty() ? null : controls.getFirst().component;''', '''        @Override
        void children(List<ControlRegistry.Control> controls) {
            if (value().kind() == Nodes.ListKind.ORDERED && !context().faces().ordered.rowCommands()) {
                // The side column holds the add action; nothing sits under the rows.
                if (footer != null && footer.getParent() == canvas) canvas.remove(footer);
                footer = null;
                context().faces().ordered.mount(this, controls.isEmpty() ? null : controls.getFirst());
                canvas.revalidate();
                sync();
                return;
            }
            JComponent next = controls.isEmpty() ? null : controls.getFirst().component;''', 1),
    ('''                                Integer.toString(index + 1),
                                Nodes.TextRole.CAPTION));
                trailing.add(commandNode(row, "up", "chevron.up", "Move up", index > 0));
                trailing.add(
                        commandNode(row, "down", "chevron.down", "Move down", index < size() - 1));
                trailing.add(commandNode(row, "remove", "close", "Remove", true));''', '''                                Integer.toString(index + 1),
                                context().faces().ordered.indexRole()));
                if (context().faces().ordered.rowCommands()) {
                    trailing.add(commandNode(row, "up", "chevron.up", "Move up", index > 0));
                    trailing.add(
                            commandNode(row, "down", "chevron.down", "Move down", index < size() - 1));
                    trailing.add(commandNode(row, "remove", "close", "Remove", true));
                }''', 1),
    ('''                ((Labels.LiteralLabel) number.component).columnWidth = ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_INDEX_WIDTH);''',
     '''                ((Labels.LiteralLabel) number.component).columnWidth = ThemePainting.metric(context(), DesignMetrics.Role.ORDERED_INDEX_WIDTH);
                context().faces().ordered.styleIndex((JLabel) number.component, context());''', 1),
    ('''            if (value().kind() == Nodes.ListKind.ORDERED) {
                command(''', '''            if (value().kind() == Nodes.ListKind.ORDERED && context().faces().ordered.rowCommands()) {
                command(''', 1),
    ('''        String internalId(String item, String suffix) {''', '''        /** Adopts {@code next} as the entry row the side column spans, or gives up the current one for null. */
        void entry(JComponent next) {
            if (entry != null && entry != next && entry.getParent() == scroll) scroll.remove(entry);
            entry = next;
            if (entry != null && entry.getParent() != scroll) scroll.add(entry);
            scroll.revalidate();
            scroll.repaint();
        }

        /** The side column's commands follow the selection after every pass over the rows. */
        @Override
        void layoutAccessories(int width) {
            if (side != null) side.sync();
        }

        String internalId(String item, String suffix) {''', 1),
    ('''            c.button.putClientProperty("Jasper.rowCommand", action);
        }
    }
}''', '''            c.button.putClientProperty("Jasper.rowCommand", action);
        }
    }

    /** An item list's scroll pane: the classic ordered face paints its box here and lays out its side column. */
    static final class ListPane extends JScrollPane {
        ListControl owner;

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            if (owner == null || owner.value().kind() != Nodes.ListKind.ORDERED) return;
            var g = (java.awt.Graphics2D) graphics.create();
            try { owner.context().faces().ordered.paintPane(owner, g); } finally { g.dispose(); }
        }
    }

    /**
     * A classic ordered list's side column (spec 6.4): the list's add action, then Remove, Move Up and Move Down, equal
     * buttons acting on the selected row and emitting the row commands' events (plan 4c Rulings 14).
     */
    static final class SideColumn extends JPanel {
        final ListControl owner;
        final Buttons.ButtonControl removeCommand, upCommand, downCommand;
        /** The list's add action, or null. */
        ControlRegistry.Control addControl;

        SideColumn(ListControl owner) {
            super(null);
            this.owner = owner;
            setOpaque(false);
            KitOwnership.mark(this, "@ordered/" + owner.node.id() + "/side");
            removeCommand = command("remove", "Remove");
            upCommand = command("up", "Move Up");
            downCommand = command("down", "Move Down");
            owner.own(() -> { removeCommand.close(); upCommand.close(); downCommand.close(); });
        }

        private Nodes.Button node(String suffix, String label, boolean possible) {
            return new Nodes.Button("@ordered/" + owner.node.id() + "/side/" + suffix, new ControlState(true, possible, false, ""), label, "",
                Nodes.ButtonKind.SECONDARY, suffix, "");
        }

        private Buttons.ButtonControl command(String suffix, String label) {
            var control = new Buttons.ButtonControl(owner.view, node(suffix, label, false), event -> run(suffix));
            control.button.sideColumn = true;
            add(control.component);
            return control;
        }

        /** The add action first, as a standard button without its glyph (spec 6.4); null removes it. */
        void addAction(ControlRegistry.Control action) {
            var next = action == null ? null : action.component;
            if (addControl != null && addControl.component != next && addControl.component.getParent() == this) remove(addControl.component);
            addControl = action;
            if (action instanceof Buttons.ButtonControl button) {
                button.button.sideColumn = true;
                button.button.orderedAction = false;
                // The face paints the glyph its client property names, so both go.
                button.button.setIcon(null);
                button.button.setDisabledIcon(null);
                button.button.putClientProperty("Jasper.button.icon", null);
                button.button.setHorizontalAlignment(SwingConstants.CENTER);
            }
            if (next != null && next.getParent() != this) add(next, 0);
            revalidate();
            repaint();
        }

        /** The row the commands act on: the focused row while it is selected, else the first selected row; null for none. */
        String target() {
            if (owner.focusItem != null && owner.selected.contains(owner.focusItem) && owner.indexOf(owner.focusItem) >= 0) return owner.focusItem;
            for (int i = 0; i < owner.size(); i++) if (owner.selected.contains(owner.itemId(i))) return owner.itemId(i);
            return null;
        }

        private void run(String suffix) {
            String row = target();
            if (row == null || !owner.interactive()) return;
            int index = owner.indexOf(row);
            switch (suffix) {
                case "remove" -> owner.emit(new UiEvent.Removed(owner.node.id(), row));
                case "up" -> { if (index > 0) owner.emit(new UiEvent.Reordered(owner.node.id(), row, index - 1)); }
                default -> { if (index < owner.size() - 1) owner.emit(new UiEvent.Reordered(owner.node.id(), row, index + 1)); }
            }
        }

        /** Refreshes the column's own commands (their fonts and colours) with the list's refresh. */
        void restyle() {
            removeCommand.refresh();
            upCommand.refresh();
            downCommand.refresh();
        }

        /** Enables each command for the current selection. */
        void sync() {
            String row = target();
            int index = row == null ? -1 : owner.indexOf(row);
            boolean live = owner.interactive();
            update(removeCommand, "remove", "Remove", live && index >= 0);
            update(upCommand, "up", "Move Up", live && index > 0);
            update(downCommand, "down", "Move Down", live && index >= 0 && index < owner.size() - 1);
        }

        private void update(Buttons.ButtonControl command, String suffix, String label, boolean possible) {
            if (((Nodes.Button) command.node).state().enabled() != possible) command.update(node(suffix, label, possible));
        }

        int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.SIDE_BUTTON_GAP); }

        @Override public java.awt.Dimension getPreferredSize() {
            int width = ThemePainting.metric(owner.context(), DesignMetrics.Role.SIDE_COLUMN_WIDTH), height = 0, shown = 0;
            for (var child : getComponents()) if (child.isVisible()) {
                var size = child.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
                shown++;
            }
            return new java.awt.Dimension(width, height + Math.max(0, shown - 1) * gap());
        }

        @Override public void doLayout() {
            int y = 0;
            for (var child : getComponents()) if (child.isVisible()) {
                int height = child.getPreferredSize().height;
                child.setBounds(0, y, getWidth(), height);
                y += height + gap();
            }
        }
    }
}''', 1)])
edit(S + "Stacks.java", [
    ('''            if (routeStep) context().faces().route.step(this, controls);
''', '''            if (routeStep) context().faces().route.step(this, controls);
            context().faces().ordered.span(this, controls);
''', 1)])
edit(S + "Buttons.java", [
    ('''        /** An ORDERED row's compact up/down/remove command. */
        boolean orderedCommand;''', '''        /** An ORDERED row's compact up/down/remove command. */
        boolean orderedCommand;
        /** A classic ordered list's side-column button: a standard button without its glyph, whatever its kind (plan 4c). */
        boolean sideColumn;''', 1),
    ('''            button.setCursor(Cursor.getPredefinedCursor(button.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            button.revalidate(); button.repaint();''', '''            button.setCursor(Cursor.getPredefinedCursor(button.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (button.sideColumn) {
                button.setIcon(null); button.setDisabledIcon(null); button.putClientProperty("Jasper.button.icon", null);
                button.setHorizontalAlignment(SwingConstants.CENTER);
            }
            button.revalidate(); button.repaint();''', 1)])
edit(S + "ClassicButtonFace.java", [
    ('''        if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK) { b.paintRaised(g, hover, focused); return; }''',
     '''        if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK && !b.sideColumn) { b.paintRaised(g, hover, focused); return; }''', 1),
    ('''        if (b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK) return ThemePainting.color(c, b.textRole(hover));''',
     '''        if (b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK && !b.sideColumn) return ThemePainting.color(c, b.textRole(hover));
        if (b.sideColumn && b.isEnabled() && b.kind != Nodes.ButtonKind.PRIMARY) return ThemePainting.painted(c, label);''', 1)])
edit(S + "ViewReconciler.java", [
    ('''            button.button.orderedAction = false;
''', '''            button.button.orderedAction = false;
            button.button.sideColumn = false;
''', 1)])
PY
```

ItemLists imports `javax.swing.*`, so `JScrollPane`, `JPanel`, `JLabel`, `JComponent` and `SwingConstants` resolve, and
`dev.jasper.ui.model.*`, so `ControlState`, `UiEvent` and `ListItem` do; AWT types are written with their package, as
ItemLists imports `java.awt.Container` alone. `ListPane` adds nothing to the default look: a non-opaque `JScrollPane`
with the same UI paints nothing, and its hook returns before painting unless the list is ORDERED, where the default face
paints nothing. The default ordered face keeps the canvas's rounded box (`paintOrdered` is the former block), the index
in CAPTION, the row commands and the footer; `selectionShown` is unchanged while the face keeps row commands. The
classic side-column buttons are real `ButtonControl`s whose clicks go to `SideColumn.run`, refreshed with the list
(`restyle`); `ButtonControl.refresh` drops a side-column button's glyph, so the add action stays glyphless through the
extra refresh `KitView` runs after its first pass (fact 20); the reconciler resets
`sideColumn` on the add action as it resets `orderedAction`, and the list's `children` sets it again on every pass.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicOrderedTest` 6 tests; `PaintSpecTest` with the two new rows; `ItemListShortcutsTest`,
`InteractiveRowsTest`, `FormsTest`, `ButtonsTest`, `ClassicButtonsTest` and the gallery suites green).

- [ ] **Step 6: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none` (the add-host fixture's ordered keys list keeps its rounded box, commands and link footer).
Open `jasper-ui/build/classic-faces/ordered.png` and compare it with Project Index's markers by eye.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicOrderedTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: the ordered list's side column

The ordered-list point's faces. The classic list is a square box of striped rows with
a muted data-face index, and a side column of equal buttons beside it: the add action
first (a LINK one as a standard button), then Remove, Move Up and Move Down acting on
the selection; a following entry row in a VERTICAL parent is adopted so the column
spans it. The default face keeps the rounded box, the row commands and the footer.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 4: The Enterprise fixtures and the full kit matrix

Spec 16.1.3's kit matrix in full, 16.1.2's classic guard check and 16.1.9's events under both kits. Six Enterprise
fixtures rebuild the mocks' compositions as descriptors (`EnterpriseFixtures`; Task 5 compares their renders with the
mocks); `KitFixtures.enterpriseLight()` is spec 7.6's Light table as a fixture theme (Rulings 20). `KitMatrixTest`
renders the gallery and the Enterprise fixtures under the Enterprise fixture kit over the theme set and Enterprise Light,
and under the complement kit and the seven covering-array kits over Slate Cobalt Dark and Light, Latte, `marked()` and
`custom()`; it checks `marked()`'s mapping (Rulings 18), `custom()`'s literals and the array's strength (Rulings 19).
`PaintedContrastTest` checks every classic pair's guard (Rulings 17); `ClassicContractTest` checks the events
(Rulings 23).

**Files:**
- Create: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/EnterpriseFixtures.java`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (`enterpriseCatalog()`), `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java` (`enterpriseLight()`)
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/gallery/control-covering-array.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicContractTest.java`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java`, `GalleryCoverageTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java`

**Interfaces:**
- Consumes: Tasks 1 to 3's faces; plan 4a's `KitFixtures.enterprise()`, `complement()`, `withControls(Kit,
  ControlVariants)`, `ControlVariants.of(boolean...)` and `keys()`, `KitMatrixTest.areas`; plan 4b's
  `KitFaces.declared(Kit)`; plan 3c's `nodes(Object, List)` helper in `GalleryCoverageTest`.
- Produces: `public static List<KitGallery.Fixture> EnterpriseFixtures.create()` (fixtures `classic-new-tunnel` 520 × 449,
  `classic-add-host` 520 × 521, `classic-new-file-server-ftp` 520 × 605, `classic-notifications-groups` 751 × 529,
  `classic-project-markers` 751 × 92, `classic-keymap-tree` 777 × 489, each referencing its mock's path under
  `docs/design/enterprise/reference/`); `public static List<KitGallery.Fixture> KitGallery.enterpriseCatalog()`;
  `public static GeneratedTheme KitFixtures.enterpriseLight()`; `KitMatrixTest.captures(GeneratedTheme, Kit,
  List<KitGallery.Fixture>)`, `coveringArray()`, `blocks(BufferedImage, int)`, `blend(int, List<Color>)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/resources/dev/jasper/ui/gallery/control-covering-array.tsv` (Rulings 19):

```bash
python3 -I - <<'PY'
import itertools, pathlib
points = ["toggle", "exclusive", "tabs", "group", "form_label", "empty", "table", "tree", "stepper", "ordered_list", "buttons", "tag", "route", "prompt"]
rows = [[0] * 14 for _ in range(7)]
for column, subset in enumerate(list(itertools.combinations(range(1, 7), 4))[:14]):
    for row in subset: rows[row][column] = 1
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/gallery/control-covering-array.tsv")
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text("# " + " ".join(points) + " (spec 5.2's order; 1 = the classic value); strength 2 in seven rows (plan 4c Rulings 19)\n"
    + "".join("\t".join(map(str, row)) + "\n" for row in rows), encoding="utf-8")
PY
```

The file's rows are `0×14`, `1111111111 0000`, `1111110000 1111`, `1110001110 1110`, `1001101101 1101`,
`0101011011 1011` and `0010110111 0111` (spaces added here for reading).

Run from the worktree root, to extend `KitMatrixTest`:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 4 step 1: the full kit matrix."""
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
matrix = "jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java"
edit(matrix, [
    ('''import dev.jasper.ui.kit.Kit;
''', '''import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.KitFaces;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SurfaceContrast;
''', 1),
    ('''    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit) {
        var captures = new ArrayList<KitGallery.Capture>();
        Edt.run(() -> KitGallery.installed(theme, kit, (context, focus) -> {
            for (var fixture : KitGallery.fixtures()) captures.add(KitGallery.capture(fixture, context, focus));
        }));
        return captures;
    }''', '''    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit) { return captures(theme, kit, KitGallery.fixtures()); }

    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit, List<KitGallery.Fixture> fixtures) {
        var captures = new ArrayList<KitGallery.Capture>();
        Edt.run(() -> KitGallery.installed(theme, kit, (context, focus) -> {
            for (var fixture : fixtures) captures.add(KitGallery.capture(fixture, context, focus));
        }));
        return captures;
    }''', 1)])
before_last_brace(matrix, '''
    /** Spec 16.1.3: the gallery and the Enterprise fixtures render under the Enterprise fixture kit over the theme set. */
    @Test void everyFixtureRendersUnderTheEnterpriseKitOverTheThemeSet() {
        var themes = new ArrayList<>(KitFixtures.themes());
        themes.add(KitFixtures.enterpriseLight());
        var fixtures = KitGallery.enterpriseCatalog();
        for (var theme : themes) {
            var captures = captures(theme, KitFixtures.enterprise(), fixtures);
            assertThat(captures).hasSameSizeAs(fixtures);
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s %s: %s", theme.name(), theme.dark() ? "Dark" : "Light", capture.fixture().name())
                    .hasSizeGreaterThan(1);
        }
    }

    /** The checked-in covering array's rows, each the 14 control points' classic flags in spec 5.2's order. */
    static List<boolean[]> coveringArray() {
        try (var in = KitMatrixTest.class.getResourceAsStream("control-covering-array.tsv")) {
            var rows = new ArrayList<boolean[]>();
            for (var line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var cells = line.split("\\t");
                var row = new boolean[cells.length];
                for (int i = 0; i < cells.length; i++) row[i] = cells[i].equals("1");
                rows.add(row);
            }
            return rows;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Rulings 19: every pair of control points shows all four value combinations in the seven rows. */
    @Test void theCoveringArrayHasStrengthTwoOverTheFourteenControlPoints() {
        var rows = coveringArray();
        assertThat(rows).hasSize(7).allSatisfy(row -> assertThat(row).hasSize(14));
        for (int a = 0; a < 14; a++) for (int b = a + 1; b < 14; b++) {
            var seen = new HashSet<String>();
            for (var row : rows) seen.add((row[a] ? "1" : "0") + (row[b] ? "1" : "0"));
            assertThat(seen).as("points %d and %d", a, b).containsExactlyInAnyOrder("00", "01", "10", "11");
        }
    }

    /** Spec 16.1.3: the complement kit and the seven covering-array kits over Slate Cobalt Dark and Light, Latte, marked() and custom(). */
    @Test void theComplementAndTheCoveringArrayRenderOverFivePalettes() {
        var latte = KitFixtures.themes().stream().filter(t -> t.name().equals("Catppuccin Latte")).findFirst().orElseThrow();
        var palettes = List.of(KitFixtures.dark(), KitFixtures.light(), latte, KitFixtures.marked(), KitFixtures.custom());
        var kits = new ArrayList<Kit>(List.of(KitFixtures.complement()));
        for (var row : coveringArray()) kits.add(KitFixtures.withControls(KitFixtures.enterprise(), ControlVariants.of(row)));
        assertThat(kits.getFirst().controls()).isEqualTo(KitFixtures.enterprise().controls().complement());
        var fixtures = KitGallery.enterpriseCatalog();
        for (var kit : kits) for (var theme : palettes) {
            var captures = captures(theme, kit, fixtures);
            assertThat(captures).hasSameSizeAs(fixtures);
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s under %s: %s", theme.name(), kit.controls().keys(), capture.fixture().name()).hasSizeGreaterThan(1);
        }
    }

    /** Rulings 18: under marked(), every one-colour 3 x 3 block of a classic surface is a role or a blend of two. */
    @Test void underMarkedEveryClassicAreaIsARoleOrABlendOfTwo() {
        var theme = KitFixtures.marked();
        var kit = KitFixtures.enterprise();
        var allowed = new ArrayList<java.awt.Color>();
        for (var role : SemanticColor.values()) allowed.add(theme.semantic().color(role));
        for (var role : DerivedColor.values()) allowed.add(role.color(theme));
        for (var pair : KitFaces.declared(kit)) allowed.add(SurfaceContrast.painted(theme, pair));
        allowed.add(java.awt.Color.BLACK);
        allowed.add(java.awt.Color.WHITE);
        var found = new ArrayList<String>();
        for (var capture : captures(theme, kit, EnterpriseFixtures.create()))
            blocks(capture.image(), 3).forEach((rgb, at) -> {
                if (!blend(rgb, allowed)) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF) + " at " + at);
            });
        assertThat(found).as("classic areas in no role and no blend of two").isEmpty();
    }

    /** Spec 16.1.3: under custom(), the Enterprise kit paints no area in a colour only another theme has. */
    @Test void underCustomTheEnterpriseKitPaintsNoForeignColour() {
        var foreign = foreign(KitFixtures.custom());
        var found = new ArrayList<String>();
        for (var capture : captures(KitFixtures.custom(), KitFixtures.enterprise(), KitGallery.enterpriseCatalog())) {
            var painted = areas(capture.image());
            painted.retainAll(foreign);
            for (int rgb : painted) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF));
        }
        assertThat(found).as("pixels in another theme's colours under custom() and the Enterprise kit").isEmpty();
    }

    @Test void aBlendOfTwoAllowedColoursIsAllowedAndAThirdColourIsNot() {
        var allowed = List.of(new java.awt.Color(0x000000), new java.awt.Color(0xFFFFFF), new java.awt.Color(0x2F65CA));
        assertThat(blend(0x808080, allowed)).isTrue();
        assertThat(blend(0x97B2E4, allowed)).as("accent halfway to white").isTrue();
        assertThat(blend(0xC03030, allowed)).isFalse();
    }

    /** The opaque colours that fill an n x n block of {@code image}, each with its first block's position. */
    static java.util.Map<Integer, String> blocks(BufferedImage image, int n) {
        var found = new java.util.LinkedHashMap<Integer, String>();
        for (int y = 0; y + n <= image.getHeight(); y++) for (int x = 0; x + n <= image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) != 0xFF || found.containsKey(argb)) continue;
            boolean block = true;
            for (int dy = 0; block && dy < n; dy++) for (int dx = 0; block && dx < n; dx++) block = image.getRGB(x + dx, y + dy) == argb;
            if (block) found.put(argb, x + "," + y);
        }
        return found;
    }

    /** Whether {@code rgb} lies within 3 levels of one of {@code colours} or of the segment between two of them. */
    static boolean blend(int rgb, List<java.awt.Color> colours) {
        double r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < colours.size(); i++) for (int j = i; j < colours.size(); j++) {
            var p = colours.get(i); var q = colours.get(j);
            double dr = q.getRed() - p.getRed(), dg = q.getGreen() - p.getGreen(), db = q.getBlue() - p.getBlue();
            double length = dr * dr + dg * dg + db * db;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((r - p.getRed()) * dr + (g - p.getGreen()) * dg + (b - p.getBlue()) * db) / length));
            if (Math.abs(r - (p.getRed() + t * dr)) <= 3 && Math.abs(g - (p.getGreen() + t * dg)) <= 3 && Math.abs(b - (p.getBlue() + t * db)) <= 3) return true;
        }
        return false;
    }
''')
coverage = "jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java"
before_last_brace(coverage, '''
    /** Plan 4c: the Enterprise fixtures hold the classic surfaces, and none shares a name with a gallery fixture. */
    @Test void theEnterpriseFixturesHoldTheClassicSurfaces() {
        var nodes = new ArrayList<Nodes.Node>();
        EnterpriseFixtures.create().forEach(f -> nodes(f.spec().root(), nodes));
        assertThat(nodes).as("a route").anyMatch(n -> n instanceof Nodes.Stack s && s.role() == Nodes.LayoutRole.ROUTE);
        assertThat(nodes).as("an ordered list followed by its entry row").anyMatch(n -> n instanceof Nodes.Stack s && s.role() == Nodes.LayoutRole.VERTICAL
            && s.children().size() > 1 && s.children().get(0) instanceof Nodes.ItemList l && l.kind() == Nodes.ListKind.ORDERED
            && s.children().get(1) instanceof Nodes.Stack e && e.role() == Nodes.LayoutRole.INLINE);
        assertThat(nodes).as("an ordered list with an add action").anyMatch(n -> n instanceof Nodes.ItemList l && l.kind() == Nodes.ListKind.ORDERED && l.addAction().isPresent());
        assertThat(nodes).as("a grouped table with cell dropdowns").anyMatch(n -> n instanceof Nodes.Table t
            && t.rows().stream().anyMatch(r -> r.heading().isPresent()) && t.rows().stream().anyMatch(r -> !r.cellNodes().isEmpty()));
        assertThat(nodes).as("a column tree").anyMatch(n -> n instanceof Nodes.Tree t && !t.columns().isEmpty());
        assertThat(nodes).as("a technical picker caption").anyMatch(n -> n instanceof Nodes.Choice c && c.face().filter(Nodes.PickerFace::technicalCaption).isPresent());
        assertThat(nodes).as("hued options").anyMatch(n -> n instanceof Nodes.Choice c && c.kind() == Nodes.ChoiceKind.EXCLUSIVE && c.options().stream().allMatch(o -> o.hue().isPresent()));
        assertThat(nodes).as("a PATH field").anyMatch(n -> n instanceof Nodes.Field f && f.kind() == Nodes.FieldKind.PATH);
        var names = EnterpriseFixtures.create().stream().map(KitGallery.Fixture::name).toList();
        assertThat(names).doesNotHaveDuplicates().noneMatch(name -> KitGallery.fixtures().stream().anyMatch(f -> f.name().equals(name)));
    }
''')
before_last_brace("jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java", '''
    /** Plan 4c Rulings 20: Enterprise Light names spec 7.6's Light roles and overrides; the rest is Slate Cobalt Light's. */
    @org.junit.jupiter.api.Test void enterpriseLightIsSpecSevenSixsLightTable() {
        var theme = KitFixtures.enterpriseLight();
        org.assertj.core.api.Assertions.assertThat(theme.name()).isEqualTo("Enterprise");
        org.assertj.core.api.Assertions.assertThat(theme.dark()).isFalse();
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP)).isEqualTo(new java.awt.Color(0xECEDEF));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT)).isEqualTo(new java.awt.Color(0x2F65CA));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL)).isEqualTo(new java.awt.Color(0xB4B8BF));
        org.assertj.core.api.Assertions.assertThat(dev.jasper.ui.theme.DerivedColor.SURFACE_TABLE_HEADER.color(theme)).isEqualTo(new java.awt.Color(0xF0F1F3));
        org.assertj.core.api.Assertions.assertThat(dev.jasper.ui.theme.DerivedColor.SURFACE_ZEBRA.color(theme)).isEqualTo(new java.awt.Color(0xF7F8FA));
        org.assertj.core.api.Assertions.assertThat(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL)).as("an unnamed role")
            .isEqualTo(KitFixtures.light().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL));
    }
''')
before_last_brace("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java", '''
    /** Spec 16.1.2 for the classic faces (plan 4c Rulings 17): every pair they declare meets its guard as painted, everywhere. */
    @Test void everyClassicPairMeetsItsGuardOverEveryPaletteAndEnterpriseLight() {
        var palettes = palettes();
        palettes.add(KitFixtures.enterpriseLight());
        for (var kit : List.of(Kit.CLASSIC, KitFixtures.enterprise()))
            for (var pair : KitFaces.declared(kit).stream().distinct().toList())
                for (var theme : palettes) {
                    Color painted = SurfaceContrast.painted(theme, pair);
                    assertThat(pair.guard().holds(painted, pair.against().color(theme)))
                        .as("%s, %s: %s on %s", kit.id(), name(theme), pair.slot().key(), pair.against().key()).isTrue();
                }
    }
''')
PY
```

The `\\n` and `\\t` in the Python block are the Java source's `"\n"` and `"\t"` split patterns. `KitMatrixTest` already
imports `SemanticColor`, `BufferedImage`, `ArrayList`, `HashSet` and `List`; `GalleryCoverageTest` imports `java.util.*`
and `Nodes`, and is in `EnterpriseFixtures`' package; `PaintedContrastTest` imports `dev.jasper.ui.swing.*`,
`dev.jasper.ui.theme.*`, `java.awt.*` and `KitFixtures`.

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicContractTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.AbstractButton;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.9 in jasper-ui (plan 4c Rulings 23): the same descriptors emit the same events under both shipped kits: a
 * checkbox or switch, a radio or segment, a folder tab or segment, the ordered list's commands, and a read-only field.
 */
@SuppressWarnings("try")
class ClassicContractTest {
    static final ControlState READY = ControlState.READY;

    static ListItem item(String id) {
        return new ListItem(id, Optional.empty(), List.of(new Nodes.Text(id + "-text", READY, id, Nodes.TextRole.MONO)), List.of(), List.of());
    }

    static ViewSpec controls() {
        var options = List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty()));
        return new ViewSpec(new Nodes.Stack("controls", READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Toggle("toggle", READY, "Allow changes", false, "toggle", false),
            new Nodes.Choice("protocol", READY, Nodes.ChoiceKind.EXCLUSIVE, options, "a", "Protocol", "protocol"),
            new Nodes.Choice("pages", READY, Nodes.ChoiceKind.TABS, options, "a", "Pages", "pages"),
            new Nodes.Field("folder", READY, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(),
                Nodes.FieldPresentation.STANDARD, "", "", "", true),
            new Nodes.ItemList("keys", READY, Nodes.ListKind.ORDERED, List.of(item("a"), item("b")), Set.of(), "", List.of(),
                Nodes.SelectionMode.SINGLE, Optional.empty()))));
    }

    @Test void theControlsEmitTheSameEventsUnderBothShippedKits() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, controls(), events::add)) {
                KitFixtures.render(view, 520, 420, 1);
                find(view, "toggle", AbstractButton.class).doClick(0);
                find(view, "protocol.option.b", AbstractButton.class).doClick(0);
                find(view, "pages.option.b", AbstractButton.class).doClick(0);
                ((JTextComponent) ((TextFields.FieldControl) view.control("folder")).input).replaceSelection("x");
                var list = (ItemLists.ListControl) view.control("keys");
                list.select("b", false);
                KitFixtures.render(view, 520, 420, 1);
                boolean side = list.side != null;
                find(view, side ? "@ordered/keys/side/up" : list.internalId("b", "up"), AbstractButton.class).doClick(0);
                find(view, side ? "@ordered/keys/side/remove" : list.internalId("b", "remove"), AbstractButton.class).doClick(0);
                assertThat(events).as(kit.id()).containsExactly(new UiEvent.Toggled("toggle", true), new UiEvent.Selected("protocol", Set.of("b")),
                    new UiEvent.Selected("pages", Set.of("b")), new UiEvent.Selected("keys", Set.of("b")), new UiEvent.Reordered("keys", "b", 0),
                    new UiEvent.Removed("keys", "b"));
                assertThat(side).as(kit.id() + " side column").isEqualTo(kit != Kit.DEFAULT);
            } finally { release(context); }
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.*' --tests 'dev.jasper.ui.swing.ClassicContractTest' --tests 'dev.jasper.ui.testsupport.*' --continue`
Expected: FAIL at compile: "cannot find symbol … method enterpriseCatalog()" in `KitGallery`, "variable EnterpriseFixtures"
and "method enterpriseLight()" in `KitFixtures`.

- [ ] **Step 3: Add the Enterprise fixtures, the catalog and Enterprise Light**

Create `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/EnterpriseFixtures.java`:

```java
package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import java.util.*;

/**
 * The Enterprise mocks' compositions as descriptors (plan 4c): each fixture is the size of its mock crop at 2x, halved
 * (the classic-* pairs in tools/visual/pairs.json), and names its mock under docs/design/enterprise/reference/. Only the
 * Enterprise gallery and the classic kit matrix render them; {@link KitGallery#fixtures()} does not list them.
 */
public final class EnterpriseFixtures {
    private EnterpriseFixtures() {}

    static final ControlState READY = ControlState.READY;
    static final ControlState DISABLED = new ControlState(true, false, false, "");

    /** The six fixtures, in pairs.json order. */
    public static List<KitGallery.Fixture> create() {
        return List.of(
            new KitGallery.Fixture("classic-new-tunnel", "04 New Tunnel/New Tunnel - Local.png", "normal", 520, 449, new ViewSpec(tunnel())),
            new KitGallery.Fixture("classic-add-host", "03 Add Host/Add Host - Default.png", "normal", 520, 521, new ViewSpec(host())),
            new KitGallery.Fixture("classic-new-file-server-ftp", "02 New File Server/New File Server - FTP.png", "normal", 520, 605, new ViewSpec(server())),
            new KitGallery.Fixture("classic-notifications-groups", "05 Settings/Settings 03 - Appearance & Behavior - Notifications.png", "normal",
                751, 529, new ViewSpec(groups())),
            new KitGallery.Fixture("classic-project-markers", "05 Settings/Settings 15 - Tools - Project Index.png", "normal", 751, 92, new ViewSpec(markers())),
            new KitGallery.Fixture("classic-keymap-tree", "05 Settings/Settings 07 - Keymap.png", "normal", 777, 489, new ViewSpec(keymap())));
    }

    static Text text(String id, String value, TextRole role) { return new Text(id, READY, value, role); }
    static Field field(String id, String value, String hint, FieldKind kind) { return new Field(id, READY, kind, value, hint, id, ""); }
    static Field technical(String id, String hint) {
        return new Field(id, READY, FieldKind.TEXT, "", hint, id, "", Optional.empty(), FieldPresentation.TECHNICAL);
    }
    static FormRow row(String id, String label, Node control) { return new FormRow(id, READY, label, "", control, Optional.empty()); }
    static FormRow row(String id, String label, String caption, Node control, Node trailing) {
        return new FormRow(id, READY, label, caption, control, Optional.ofNullable(trailing));
    }
    static Option option(String id, String label) { return new Option(id, label, Optional.empty()); }
    static Option option(String id, String label, Hue hue) { return new Option(id, label, Optional.of(hue)); }
    static Choice exclusive(String id, String label, String selected, Option... options) {
        return new Choice(id, READY, ChoiceKind.EXCLUSIVE, List.of(options), selected, label, id);
    }
    static Choice dropdown(String id, String label, String selected, String... options) {
        return new Choice(id, READY, ChoiceKind.DROPDOWN, Arrays.stream(options).map(o -> new Option(o, o, Optional.empty())).toList(), selected, label, id);
    }
    static Choice picker(String id, String label) { return new Choice(id, READY, ChoiceKind.PICKER, List.of(), "", label, id, ""); }
    static Toggle toggle(String id, boolean checked) { return new Toggle(id, READY, id, checked, id, false); }
    static SettingRow setting(String id, String title, String description, Node control) { return new SettingRow(id, READY, title, description, control, false); }
    static Section section(String id, String title, Node body) { return new Section(id, READY, title, body); }
    static Card card(String id, CardKind kind, Node... children) { return new Card(id, READY, kind, List.of(children)); }
    static Nodes.Button button(String id, String label, ButtonKind kind, ControlState state) { return new Nodes.Button(id, state, label, "", kind, id, ""); }
    static Form full(String id, String title, List<Node> sections, String preview, List<Nodes.Button> actions) {
        return new Form(id, READY, FormKind.FULL, title, Optional.empty(), sections, Optional.of(text(id + "-preview", preview, TextRole.MONO_CAPTION)),
            actions, "cancel", FormSubmission.PRIMARY);
    }

    /** New Tunnel – Local. */
    static Node tunnel() {
        var via = new Choice("via", READY, ChoiceKind.PICKER, List.of(), "", "Via host", "via", "", Optional.empty(),
            Optional.of(new PickerFace("Mac", "dustin@192.168.1.119:22", "terminal", Hue.BLUE, PickerDensity.DEFAULT, true)));
        var route = new Stack("route", READY, LayoutRole.ROUTE, List.of(step("this-computer", Hue.NEUTRAL, "This computer", "localhost:8080"),
            step("via-step", Hue.BLUE, "Via", "Mac"), step("target", Hue.GREEN, "Target on Mac", "localhost:5432")));
        return full("new-tunnel", "New Tunnel", List.of(
            section("tunnel", "Tunnel", card("tunnel-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. Postgres", FieldKind.TEXT)),
                row("via-row", "Via host", via),
                row("type-row", "Type", exclusive("type", "Type", "local", option("local", "Local"), option("remote", "Remote"), option("dynamic", "Dynamic (SOCKS)"))),
                row("type-help-row", "", text("type-help", "Connections to a port on this computer go through Mac to the target.", TextRole.CAPTION)))),
            section("ports", "Ports", card("ports-card", CardKind.MIXED,
                row("listen-row", "Listen on", "this computer", field("listen", "localhost", "", FieldKind.TEXT), field("listen-port", "8080", "", FieldKind.PORT)),
                row("forward-row", "Forward to", "as seen from Mac", field("forward", "localhost", "", FieldKind.TEXT), field("forward-port", "5432", "", FieldKind.PORT)),
                route,
                setting("allow-row", "Allow other computers to connect", "Listens on all network interfaces, not just localhost", toggle("allow", false))))),
            "ssh -L 8080:localhost:5432 dustin@192.168.1.119",
            List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("submit", "Add Tunnel", ButtonKind.PRIMARY, DISABLED)));
    }

    static Stack step(String id, Hue hue, String caption, String value) {
        return new Stack(id, READY, LayoutRole.VERTICAL, List.of(new Icon(id + "-icon", READY, "terminal", hue, IconSize.CONTROL, caption),
            text(id + "-caption", caption, TextRole.CAPTION), text(id + "-value", value, TextRole.MONO)));
    }

    /** Add Host – Default. */
    static Node host() {
        var keys = new ItemList("keys", READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE,
            Optional.of(button("key-add", "Add Key…", ButtonKind.SECONDARY, READY)), OptionalInt.empty(), "", "", "", "", "", "No keys");
        return full("add-host", "Add Host", List.of(
            section("connection", "Connection", card("connection-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. homelab", FieldKind.TEXT)),
                row("host-row", "Host", "", technical("host", "hostname or IP address"), field("port", "22", "", FieldKind.PORT)),
                row("username-row", "Username", technical("username", "root")),
                row("jump-row", "Jump host", dropdown("jump", "Jump host", "None", "None")))),
            section("authentication", "Authentication", card("authentication-card", CardKind.FORM,
                exclusive("mode", "Authentication", "vault", option("vault", "Vault Credential"), option("agent", "SSH Agent")),
                row("credential-row", "Credential", picker("credential", "Credential")),
                row("keys-row", "Keys", "Tried in order", keys, null))),
            section("options", "Options", card("options-card", CardKind.SETTINGS,
                setting("group-row", "Group", "", dropdown("group", "Group", "Other", "Other")),
                setting("favorite-row", "Add to Favorites", "", toggle("favorite", false)),
                setting("track-row", "Track shell folder", "SFTP follows the terminal's current directory", toggle("track", true))))),
            "ssh user@host", List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("connect", "Add and Connect", ButtonKind.SECONDARY, DISABLED),
                button("submit", "Add Host", ButtonKind.PRIMARY, DISABLED)));
    }

    /** New File Server – FTP (the mock's warning strip is a residual until a composition places a Notice there). */
    static Node server() {
        return full("new-file-server", "New File Server", List.of(
            section("server", "Server", card("server-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. Downloads", FieldKind.TEXT)),
                row("protocol-row", "Protocol", exclusive("protocol", "Protocol", "ftp", option("http", "HTTP", Hue.BLUE), option("ftp", "FTP", Hue.AMBER),
                    option("sftp", "SFTP", Hue.GREEN), option("tftp", "TFTP", Hue.PURPLE))),
                row("folder-row", "Folder", new Field("folder", READY, FieldKind.PATH, "", "No folder selected", "Folder", "choose-folder")),
                row("reachable-row", "Reachable from", "", dropdown("reachable", "Reachable from", "This computer only", "This computer only", "Local network"),
                    field("port", "2121", "", FieldKind.PORT)),
                row("passive-row", "Passive ports", technical("passive", "Empty uses the range in Settings")))),
            section("access", "Access", card("access-card", CardKind.MIXED,
                exclusive("login", "Login", "vault", option("anonymous", "Anonymous"), option("vault", "Vault Account"), option("session", "Session Password")),
                row("account-row", "Account", picker("account", "Account")),
                row("account-help-row", "", text("account-help", "Clients sign in with the username and password stored in this Vault account.", TextRole.CAPTION)),
                setting("allow-row", "Allow changes", "Clients can upload, rename and delete files", toggle("allow", false)))),
            section("security", "Security & Startup", card("security-card", CardKind.SETTINGS,
                setting("tls-row", "Use TLS", "", toggle("tls", false)),
                new SettingRow("require-row", READY, "Require TLS", "Refuse logins and transfers without it",
                    new Toggle("require", DISABLED, "Require TLS", false, "require", false), true),
                setting("startup-row", "Start when Jasper starts", "", toggle("startup", false))))),
            "ftp://127.0.0.1:2121", List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("submit", "Add Server", ButtonKind.PRIMARY, DISABLED)));
    }

    /** Settings 03 Notifications: the Groups table, grouped by plugin, with bare Display dropdowns. */
    static Node groups() {
        var displays = List.of(option("card", "Card"), option("sticky", "Sticky card"), option("off", "Off"));
        var rows = new ArrayList<TableRow>();
        group(rows, "jasper", "Jasper", List.of("Jasper", "Configuration", "Plugin problems", "Internal errors", "Long-running commands", "Memory & VM options"), displays);
        group(rows, "thd", "THD Sourcing Crypto", List.of("THD Sourcing Crypto", "THD Sourcing Crypto activities"), displays);
        group(rows, "editor", "Editor", List.of("Editor", "Editor activities"), displays);
        group(rows, "vault", "Credential Vault", List.of("Credential Vault", "Credential Vault activities", "Vault copies"), displays);
        group(rows, "fileserver", "File Server", List.of("File Server", "File Server activities"), displays);
        group(rows, "snippets", "Snippets", List.of("Snippets", "Snippets activities"), displays);
        return new Table("groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 4.6, Optional.empty()),
            new Column("display", "Display", TextRole.BODY, false, 1.36, Optional.empty()),
            new Column("source", "Source", TextRole.DESCRIPTION, false, 1, Optional.empty())), rows, Set.of(), "", List.of());
    }

    static void group(List<TableRow> rows, String id, String title, List<String> names, List<Option> displays) {
        rows.add(TableRow.heading(id, title, OptionalInt.of(names.size()), "group"));
        for (int i = 0; i < names.size(); i++) {
            String row = id + "-" + i;
            rows.add(new TableRow(row, Map.of("group", names.get(i), "source", "Default"), Optional.empty(), false, Optional.empty(), "",
                Map.of("display", new Choice(row + "-display", READY, ChoiceKind.DROPDOWN, displays, id.equals("jasper") && i == 1 ? "sticky" : "card", "Display", "")),
                Optional.empty()));
        }
    }

    /** Settings 15 Project Index: the project markers list and its entry row. */
    static Node markers() {
        var list = new ItemList("markers", READY, ListKind.ORDERED, List.of(marker("csproj", "*.csproj"), marker("xcodeproj", "*.xcodeproj"), marker("flake", "flake.nix")),
            Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty());
        // The entry field's label is its accessible name only (EMBEDDED), as the mock draws none.
        var entry = new Stack("marker-entry", READY, LayoutRole.INLINE, List.of(
            new Field("marker-new", READY, FieldKind.TEXT, "", "Add…", "Project marker", "", Optional.empty(), FieldPresentation.EMBEDDED),
            button("marker-add", "Add", ButtonKind.SECONDARY, DISABLED)));
        return new Stack("project-markers", READY, LayoutRole.VERTICAL, List.of(list, entry));
    }

    static ListItem marker(String id, String value) { return new ListItem(id, Optional.empty(), List.of(text(id + "-value", value, TextRole.MONO)), List.of(), List.of()); }

    /** Settings 07 Keymap: the action tree with its Action, Scope and Shortcut columns (keycaps are the app's, plan 9). */
    static Node keymap() {
        var entries = List.of("Appearance:3", "Application:14", "Clipboard:2", "Command Palette:9", "commandline:2", "config:1", "Credential Vault:12",
            "Editor:4", "File Server:16", "notifications:3", "Panes:8", "Project Index:5", "Remote:24", "Search:6", "Shell History:1", "Snippets:2",
            "Tabs:14", "Terminal:4", "THD Sourcing Crypto:1");
        var roots = new ArrayList<TreeItem>();
        for (var entry : entries) {
            String title = entry.substring(0, entry.indexOf(':')), id = title.toLowerCase(Locale.ROOT).replace(' ', '-');
            int count = Integer.parseInt(entry.substring(entry.indexOf(':') + 1));
            var children = id.equals("clipboard") ? List.of(action("copy", "Copy", "⌘C"), action("paste", "Paste", "⌘V")) : List.<TreeItem>of();
            roots.add(new TreeItem(id, title, Optional.empty(), false, children, OptionalInt.empty(), false, "", Optional.empty(),
                List.of("", count + " shortcuts"), Optional.empty()));
        }
        return new Tree("keymap", READY, roots, Set.of("clipboard"), Set.of(), "", List.of(new Column("action", "Action", TextRole.BODY, false, 5, Optional.empty()),
            new Column("scope", "Scope", TextRole.DESCRIPTION, false), new Column("shortcut", "Shortcut", TextRole.DESCRIPTION, false)));
    }

    static TreeItem action(String id, String title, String keys) {
        return new TreeItem(id, title, Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("Global", keys), Optional.empty());
    }
}
```

The mocks' copy is kept as drawn ("1 shortcuts" included). The ItemList's sixteen-argument constructor is plan 3c's
(`addAction`, `visibleRows`, five action strings, `emptyText`); `PickerFace`'s six-argument one is plan 3b's.

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 4 step 3: the Enterprise catalog and the Enterprise Light fixture theme."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ('''    private static Fixture panelState(''', '''    /** The gallery and the Enterprise fixtures (plan 4c): what the Enterprise gallery and the classic kit matrix render. */
    public static List<Fixture> enterpriseCatalog() {
        var all = new ArrayList<>(fixtures());
        all.addAll(EnterpriseFixtures.create());
        return List.copyOf(all);
    }
    private static Fixture panelState(''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */''',
     '''    /**
     * Spec 7.6's Enterprise Light as a fixture theme (plan 4c Rulings 20): Slate Cobalt Light with the table's named roles
     * and its eight derived overrides; a role the table does not name keeps Slate Cobalt Light's value. The shipped theme,
     * with its derived neutrals, is plan 5's.
     */
    public static GeneratedTheme enterpriseLight() {
        var base = light();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, base.semantic().color(role));
        java.util.function.BiConsumer<Integer, SemanticColor[]> name = (rgb, roles) -> { for (var role : roles) values.put(role, new java.awt.Color(rgb)); };
        name.accept(0xECEDEF, new SemanticColor[] {SemanticColor.SURFACE_APP, SemanticColor.SURFACE_DIALOG});
        name.accept(0xE1E3E6, new SemanticColor[] {SemanticColor.SURFACE_TITLEBAR});
        name.accept(0xF7F7F8, new SemanticColor[] {SemanticColor.SURFACE_PANEL, SemanticColor.SURFACE_SIDEBAR});
        name.accept(0xFFFFFF, new SemanticColor[] {SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_INPUT, SemanticColor.SURFACE_TERMINAL,
            SemanticColor.TEXT_ONACCENT, SemanticColor.TEXT_ONSELECTION});
        name.accept(0x2F65CA, new SemanticColor[] {SemanticColor.ACCENT, SemanticColor.SELECTION_BG, SemanticColor.HUE_BLUE});
        name.accept(0xDCE5F7, new SemanticColor[] {SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.TEXT_ONSELECTION_SECONDARY});
        name.accept(0x8A5A00, new SemanticColor[] {SemanticColor.STATUS_WARNING});
        name.accept(0xB5651D, new SemanticColor[] {SemanticColor.HUE_AMBER});
        name.accept(0xC4C7CC, new SemanticColor[] {SemanticColor.BORDER_WINDOW});
        name.accept(0xB4B8BF, new SemanticColor[] {SemanticColor.BORDER_CONTROL, SemanticColor.BORDER_INPUT, SemanticColor.BUTTON_SECONDARY_BORDER});
        name.accept(0x1E1F22, new SemanticColor[] {SemanticColor.TEXT_PRIMARY});
        name.accept(0x3A3D42, new SemanticColor[] {SemanticColor.TEXT_STRONG_SECONDARY});
        name.accept(0x5C6370, new SemanticColor[] {SemanticColor.TEXT_SECONDARY, SemanticColor.TEXT_TERTIARY});
        name.accept(0x7A808A, new SemanticColor[] {SemanticColor.TEXT_PLACEHOLDER});
        name.accept(0x2E9E4F, new SemanticColor[] {SemanticColor.STATUS_SUCCESS, SemanticColor.HUE_GREEN});
        name.accept(0xD03A3A, new SemanticColor[] {SemanticColor.STATUS_DANGER});
        name.accept(0x8A4FBF, new SemanticColor[] {SemanticColor.HUE_PURPLE});
        var overrides = new java.util.EnumMap<DerivedColor, java.awt.Color>(DerivedColor.class);
        overrides.put(DerivedColor.SURFACE_HEADER, new java.awt.Color(0xE1E3E6));
        overrides.put(DerivedColor.SURFACE_HEADER_ACTIVE, new java.awt.Color(0xD9DFEA));
        overrides.put(DerivedColor.SURFACE_STRIPE, new java.awt.Color(0xE4E6E9));
        overrides.put(DerivedColor.SURFACE_TABLE_HEADER, new java.awt.Color(0xF0F1F3));
        overrides.put(DerivedColor.SURFACE_ZEBRA, new java.awt.Color(0xF7F8FA));
        overrides.put(DerivedColor.SURFACE_ROW_HOVER, new java.awt.Color(0xEEF2FA));
        overrides.put(DerivedColor.WARNING_GROUND, new java.awt.Color(0xFBF1DA));
        overrides.put(DerivedColor.WARNING_BORDER, new java.awt.Color(0xE6CF95));
        return new GeneratedTheme("Enterprise", false, base.author(), new SemanticPalette(values), base.terminal(), base.editor(), base.syntax(),
            base.glass(), overrides);
    }

    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */''', 1)])
PY
```

`KitFixtures` imports `dev.jasper.ui.theme.*`, so `SemanticColor`, `DerivedColor`, `SemanticPalette` and
`GeneratedTheme` resolve; the test fixtures are outside `ColourLiteralScanTest`'s production scan, as `custom()`'s
plum and `degenerate()`'s greys already are.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.*' --tests 'dev.jasper.ui.swing.ClassicContractTest' --tests 'dev.jasper.ui.testsupport.*' --continue`
Expected: PASS (`ClassicContractTest` 1 test, `KitFixturesTest` with the new test, `GalleryCoverageTest` with the new test,
`KitMatrixTest` with six new tests). If `underMarkedEveryClassicAreaIsARoleOrABlendOfTwo`
or `underCustomTheEnterpriseKitPaintsNoForeignColour` fails, its message names the fixture, the colour and its position:
find the painter there (`KitFixtures.capture` of the same fixture under `marked()` shows it); a colour literal, an
undeclared guard repair or a FlatLaf colour the classic overlay does not map is the bug — fix the painter or
`UiKeySet.CLASSIC`, never the test. Record the matrix's run time from the test report
(`jasper-ui/build/test-results/test/TEST-dev.jasper.ui.gallery.KitMatrixTest.xml`, its `time` attribute) for Task 6.

- [ ] **Step 5: Run the app's contrast suite**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (the new guard test and the default look's pairs unchanged).

- [ ] **Step 6: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none` (`KitGallery.main` still renders `fixtures()` only).

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/EnterpriseFixtures.java jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java \
  jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java jasper-ui/src/test/resources/dev/jasper/ui/gallery/control-covering-array.tsv \
  jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicContractTest.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java
git commit -F - <<'MSG'
Classic faces: the Enterprise fixtures and the full kit matrix

Six Enterprise fixtures rebuild the mocks' compositions; Enterprise Light is a
fixture theme. KitMatrixTest renders the gallery and the fixtures under the Enterprise
kit over the theme set, and under the complement kit and a checked-in strength-2
covering array over five palettes; marked() maps every classic area to a role or a
blend of two, and custom() finds no literal. PaintedContrastTest checks every classic
pair's guard; ClassicContractTest checks the events under both kits.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 5: The Enterprise gallery and the mock comparison

The opt-in comparison of classic renders with the Enterprise mocks (spec 16.1.4 keeps it opt-in; Rulings 21 and 22).
`-Pjasper.galleryKit=enterprise` makes the headless gallery render `KitGallery.enterpriseCatalog()` with the Enterprise
fixture kit over Enterprise Light into `jasper-ui/build/gallery/enterprise-light-<scale>x/`; the default run is
unchanged. `tools/visual/pairs.json` gains six `classic-*` pairs with a `min_agreement` floor and the colours each crop
must show, and `tools/visual/check_pairs.py` reports PASS or FAIL for them; `EnterpriseGalleryTest` keeps the pairs and
the fixtures consistent.

**Files:**
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java:92-113` (`main`), `jasper-ui/build.gradle.kts:28-46`
- Modify: `tools/visual/pairs.json` (six entries appended), `tools/visual/README.md` (one bullet)
- Create: `tools/visual/check_pairs.py`, `jasper-ui/src/test/java/dev/jasper/ui/gallery/EnterpriseGalleryTest.java`

**Interfaces:**
- Consumes: Task 4's `EnterpriseFixtures.create()`, `KitGallery.enterpriseCatalog()`, `KitFixtures.enterpriseLight()`
  and `KitFixtures.enterprise()`; `tools/visual/compare_mock.py`'s output (fact 15).
- Produces: the `jasper.galleryKit` Gradle and system property (`default` or `enterprise`); the gallery directory
  `enterprise-light-<scale>x`; `pairs.json` keys `min_agreement` (a number) and `colours` (hex strings), which
  `compare_all.py` ignores; `check_pairs.py OUT_DIR [NAME_FILTER]`, exiting 1 when a pair fails.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/gallery/EnterpriseGalleryTest.java`:

```java
package dev.jasper.ui.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Plan 4c: every Enterprise fixture has its classic-* pair, whose crop is the fixture's size at 2x inside its mock. */
class EnterpriseGalleryTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    static final Pattern PAIR = Pattern.compile("\"name\": \"(classic-[^\"]+)\",\\s*\"mock\": \"([^\"]+)\",\\s*\"crop\": \\[\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),"
        + "\\s*(\\d+)\\s*\\],\\s*\"render\": \"([^\"]+)\",\\s*\"min_agreement\": ([0-9.]+),\\s*\"colours\": \\[([^\\]]*)\\]");

    record Pair(String mock, int x, int y, int width, int height, String render, double floor, String colours) {}

    static LinkedHashMap<String, Pair> pairs() throws Exception {
        var pairs = new LinkedHashMap<String, Pair>();
        var matcher = PAIR.matcher(Files.readString(ROOT.resolve("tools/visual/pairs.json")));
        while (matcher.find())
            pairs.put(matcher.group(1), new Pair(matcher.group(2), Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)), matcher.group(7), Double.parseDouble(matcher.group(8)), matcher.group(9)));
        return pairs;
    }

    @Test void everyEnterpriseFixtureHasItsPairAtTwiceItsSize() throws Exception {
        var pairs = pairs();
        var fixtures = EnterpriseFixtures.create();
        assertThat(pairs.keySet()).containsExactlyElementsOf(fixtures.stream().map(KitGallery.Fixture::name).toList());
        for (var fixture : fixtures) {
            var pair = pairs.get(fixture.name());
            assertThat(pair.mock()).isEqualTo("docs/design/enterprise/reference/" + fixture.reference());
            assertThat(pair.render()).isEqualTo("jasper-ui/build/gallery/enterprise-light-2x/" + fixture.name() + ".png");
            assertThat(pair.width()).as(fixture.name()).isEqualTo(2 * fixture.width());
            assertThat(pair.height()).as(fixture.name()).isEqualTo(2 * fixture.height());
            assertThat(pair.floor()).as(fixture.name()).isBetween(0.0, 1.0);
            assertThat(pair.colours()).as(fixture.name()).contains("#");
            var mock = ImageIO.read(ROOT.resolve(pair.mock()).toFile());
            assertThat(pair.x() + pair.width()).as(fixture.name() + " inside its mock").isLessThanOrEqualTo(mock.getWidth());
            assertThat(pair.y() + pair.height()).as(fixture.name() + " inside its mock").isLessThanOrEqualTo(mock.getHeight());
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.EnterpriseGalleryTest'`
Expected: FAIL: "Expecting actual: [] to contain exactly … classic-new-tunnel" (`pairs.json` has no classic pair yet).

- [ ] **Step 3: Add the pairs and the Enterprise gallery mode**

Append the six pairs to `tools/visual/pairs.json` (crops from this plan's "Mock measurements"; colours are spec 7.6's
Enterprise Light roles each crop shows in blocks):

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith("classic-") for p in pairs): raise SystemExit("pairs.json already has classic pairs")
R = "docs/design/enterprise/reference/"
G = "jasper-ui/build/gallery/enterprise-light-2x/"
dialog = ["#ECEDEF", "#FFFFFF", "#C4C7CC", "#B4B8BF", "#2F65CA", "#E6E7EA"]
table = ["#F0F1F3", "#FFFFFF", "#B4B8BF", "#D3D6DA"]
for name, mock, crop, floor, colours in [
        ("classic-new-tunnel", "04 New Tunnel/New Tunnel - Local.png", [2, 60, 1040, 898], 0.30, dialog + ["#2E9E4F"]),
        ("classic-add-host", "03 Add Host/Add Host - Default.png", [2, 60, 1040, 1042], 0.30, dialog),
        ("classic-new-file-server-ftp", "02 New File Server/New File Server - FTP.png", [2, 60, 1040, 1210], 0.30,
         dialog + ["#2E9E4F", "#8A4FBF", "#B5651D"]),
        ("classic-notifications-groups", "05 Settings/Settings 03 - Appearance & Behavior - Notifications.png", [522, 504, 1502, 1058], 0.40, table),
        ("classic-project-markers", "05 Settings/Settings 15 - Tools - Project Index.png", [522, 792, 1502, 184], 0.40,
         ["#FFFFFF", "#F7F8FA", "#B4B8BF", "#ECEDEF"]),
        ("classic-keymap-tree", "05 Settings/Settings 07 - Keymap.png", [496, 226, 1554, 978], 0.40, table)]:
    pairs.append({"name": name, "mock": R + mock, "crop": crop, "render": G + name + ".png", "min_agreement": floor, "colours": colours})
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
PY
git diff --stat tools/visual/pairs.json
```

Expected: one file changed, 118 insertions and no deletion: `json.dumps(indent=2)` reproduces the existing 34 entries
byte for byte (checked at `222969926`), and git aligns the former last entry's brace with the new last entry's.

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4c Task 5 step 3: -Pjasper.galleryKit=enterprise renders the Enterprise catalog under the Enterprise fixture kit."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ('''        String name = System.getProperty("jasper.galleryTheme", "dark");
        GeneratedTheme theme = theme(name);
        Edt.run(() -> installed(theme, dev.jasper.ui.kit.Kit.DEFAULT, (context, focus) -> {''',
     '''        String name = System.getProperty("jasper.galleryTheme", "dark");
        // Plan 4c: the Enterprise gallery renders the gallery and the Enterprise fixtures with the Enterprise fixture kit.
        boolean enterprise = "enterprise".equals(System.getProperty("jasper.galleryKit", "default"));
        GeneratedTheme theme = enterprise ? enterpriseTheme(name) : theme(name);
        var kit = enterprise ? KitFixtures.enterprise() : dev.jasper.ui.kit.Kit.DEFAULT;
        var catalog = enterprise ? enterpriseCatalog() : fixtures();
        String label = (enterprise ? "enterprise-" : "") + name;
        Edt.run(() -> installed(theme, kit, (context, focus) -> {''', 1),
    ('''                Path directory = output.resolve(name + "-" + (int)scale + "x");''',
     '''                Path directory = output.resolve(label + "-" + (int)scale + "x");''', 1),
    ('''                for (Fixture fixture : fixtures()) {''', '''                for (Fixture fixture : catalog) {''', 1),
    ('''"{\\"theme\\":" + quote(name) + ",\\"scale\\":"''', '''"{\\"theme\\":" + quote(label) + ",\\"scale\\":"''', 1),
    ('''    public static GeneratedTheme theme(String name) {''', '''    /** The Enterprise gallery's theme: Enterprise Light (plan 4c Rulings 21; Enterprise Dark is plan 5's). */
    static GeneratedTheme enterpriseTheme(String name) {
        if (!name.equals("light")) throw new IllegalArgumentException("The Enterprise gallery renders Enterprise Light only");
        return KitFixtures.enterpriseLight();
    }
    public static GeneratedTheme theme(String name) {''', 1)])
edit("jasper-ui/build.gradle.kts", [
    ('''    val theme = providers.gradleProperty("jasper.galleryTheme").orElse("dark")
''', '''    val theme = providers.gradleProperty("jasper.galleryTheme").orElse("dark")
    val kit = providers.gradleProperty("jasper.galleryKit").orElse("default")
''', 1),
    ('''        require(theme.get() in listOf("dark", "light")) { "Unknown jasper.galleryTheme" }
''', '''        require(theme.get() in listOf("dark", "light")) { "Unknown jasper.galleryTheme" }
        require(kit.get() in listOf("default", "enterprise")) { "jasper.galleryKit must be default or enterprise" }
        require(kit.get() == "default" || theme.get() == "light") { "The Enterprise gallery renders Enterprise Light only" }
''', 1),
    ('''    systemProperty("jasper.galleryTheme", theme.get())
''', '''    systemProperty("jasper.galleryTheme", theme.get())
    systemProperty("jasper.galleryKit", kit.get())
''', 1)])
PY
```

The manifest anchor is the Java text `"{\"theme\":" + quote(name) + ",\"scale\":"` (the Python string doubles its
backslashes). With the default kit, `label` is the theme name, the catalog is `fixtures()` and the kit `Kit.DEFAULT`, so
the default run writes the same files.

- [ ] **Step 4: Write the comparison tool**

Create `tools/visual/check_pairs.py`:

```python
#!/usr/bin/env python3
"""Check classic renders against the Enterprise mocks: edge agreement and colour presence (plan 4c).

Usage: check_pairs.py OUT_DIR [NAME_FILTER]

For every pairs.json entry with "min_agreement", runs compare_mock.py into OUT_DIR and passes the pair when its edge
agreement is at least that floor and every colour in "colours" fills a 2x2 block within Delta E 6 (CIE76) in both the
mock crop and the render. Prints one PASS or FAIL line per pair and exits 1 when any pair fails. Opt-in: never part of
./gradlew check. Render first: ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light
-Pjasper.galleryScale=2
"""
import json
import pathlib
import re
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
TOLERANCE = 6.0


def lab(rgb):
    """sRGB in 0..1, shape (..., 3), as CIE L*a*b* under D65."""
    linear = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)
    xyz = linear @ np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]]).T
    xyz = xyz / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], axis=-1)


def present(image, colour):
    """Whether a 2x2 block of image is within TOLERANCE of colour (#RRGGBB)."""
    pixels = lab(np.asarray(image.convert("RGB"), dtype=np.float64) / 255.0)
    target = lab(np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float64) / 255.0)
    close = np.sqrt(((pixels - target) ** 2).sum(axis=-1)) <= TOLERANCE
    block = close[:-1, :-1] & close[1:, :-1] & close[:-1, 1:] & close[1:, 1:]
    return bool(block.any())


def crop(path, box):
    image = Image.open(path).convert("RGB")
    if box:
        x, y, w, h = box
        image = image.crop((x, y, x + w, y + h))
    return image


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    out = pathlib.Path(sys.argv[1])
    wanted = sys.argv[2] if len(sys.argv) > 2 else ""
    out.mkdir(parents=True, exist_ok=True)
    failed = False
    for pair in json.loads((HERE / "pairs.json").read_text(encoding="utf-8")):
        if "min_agreement" not in pair or (wanted and wanted not in pair["name"]):
            continue
        mock, render = ROOT / pair["mock"], ROOT / pair["render"]
        if not render.exists():
            print(f"{pair['name']}: FAIL missing render {pair['render']}")
            failed = True
            continue
        command = [sys.executable, "-I", str(HERE / "compare_mock.py"), str(mock), str(render), str(out / pair["name"]),
                   "--crop", ",".join(map(str, pair["crop"]))]
        if pair.get("render_crop"):
            command += ["--render-crop", ",".join(map(str, pair["render_crop"]))]
        result = subprocess.run(command, capture_output=True, text=True)
        found = re.search(r"edge_agreement=([0-9.]+)", result.stdout)
        agreement = float(found.group(1)) if found else 0.0
        mock_image, render_image = crop(mock, pair["crop"]), crop(render, pair.get("render_crop"))
        missing = [c for c in pair.get("colours", []) if not (present(mock_image, c) and present(render_image, c))]
        ok = found is not None and agreement >= pair["min_agreement"] and not missing
        failed |= not ok
        detail = f"agreement={agreement:.3f} (floor {pair['min_agreement']:.2f})"
        if missing:
            detail += " missing " + " ".join(missing)
        print(f"{pair['name']}: {'PASS' if ok else 'FAIL'} {detail}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
```

Edit `tools/visual/README.md`: after the `compare_all.py` bullet, add one bullet (read the file first and keep its
voice):

```text
- `check_pairs.py OUT_DIR [FILTER]` runs every pair that has a `min_agreement` (the classic look's `classic-*` pairs)
  and prints PASS or FAIL: the edge agreement must reach the floor and each listed colour must fill a 2×2 block within
  ΔE76 6 in both images. Render first with `:jasper-ui:gallery -Pjasper.galleryKit=enterprise
  -Pjasper.galleryTheme=light -Pjasper.galleryScale=2`.
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`EnterpriseGalleryTest` 1 test; the gallery suites unchanged).

- [ ] **Step 6: Render the Enterprise gallery**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light -Pjasper.galleryScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light
ls jasper-ui/build/gallery/enterprise-light-2x | grep -c '^classic-.*\.png$'
./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=dark 2>&1 | grep -c 'Enterprise Light only'
```

Expected: both renders print "Rendered N fixtures with FlatLaf … to …/enterprise-light-2x" (and `-1x`), N being the
gallery's count plus six; the count prints 6; the dark request fails with "The Enterprise gallery renders Enterprise
Light only" (the grep prints at least 1).

- [ ] **Step 7: Compare with the mocks**

Run from the worktree root:

```bash
python3 -I tools/visual/check_pairs.py jasper-ui/build/classic-compare
```

Expected: six lines, each `classic-…: PASS agreement=… (floor …)`. For a FAIL, open
`jasper-ui/build/classic-compare/<name>-side.png` (mock | render | edges) and `<name>-blend.png`:

- a missing colour or a misdrawn face (a box, band, separator, edge or glyph in the wrong place or role) is a face
  defect: fix the face in its task's file, re-run Steps 5 to 7 and Task 1 Step 6's gallery proof;
- a difference of content or font (Plex arrives with plan 5; the mocks' copy differs from a fixture) is a residual:
  write the measured agreement into that pair's `min_agreement` rounded down to 0.05 and the cause into this plan's
  banner and the STATUS entry (Task 6).

Record each pair's measured agreement for Task 6's STATUS entry.

- [ ] **Step 8: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
git diff --stat tools/visual/pairs.json
```

Expected: `changed: none`; the stat shows insertions only, so no existing pair changed.

- [ ] **Step 9: Commit**

```bash
git add jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java jasper-ui/build.gradle.kts \
  jasper-ui/src/test/java/dev/jasper/ui/gallery/EnterpriseGalleryTest.java tools/visual/pairs.json tools/visual/check_pairs.py tools/visual/README.md
git commit -F - <<'MSG'
Classic faces: the Enterprise gallery and the mock comparison

-Pjasper.galleryKit=enterprise renders the gallery and the Enterprise fixtures with the
Enterprise fixture kit over Enterprise Light; the default run is unchanged.
pairs.json gains six classic-* pairs with an edge-agreement floor and the colours each
crop shows, and tools/visual/check_pairs.py reports PASS or FAIL for them (opt-in).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 6: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/ui-architecture.md`, `docs/ui-maintenance.md`, `jasper-ui/README.md`, `docs/plugin-ui.md` (the smallest accurate edits)
- Modify: `docs/STATUS.md` (a new entry at the top), this plan's status banner

**Interfaces:**
- Consumes: Tasks 1 to 5.
- Produces: nothing new.

- [ ] **Step 1: Prove the default gallery unchanged and run the architecture tests**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery is byte-identical to plan 4a's base."""
import hashlib, pathlib, sys
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.*' :jasper-ui:verifyUiArchitecture :jasper-app:verifyApplicationArchitecture --continue
```

Expected: `changed: none`; BUILD SUCCESSFUL (`StrategyIsolationTest` finds the faces of plans 4b and 4c and no
violation: no face names `Kit`, `ControlVariants` or `ChromeSpec`, and `ViewReconciler` names no face; `VariantReadTest`
finds `ControlVariants` read only by the kit package, `dev.jasper.app.presentation` and `KitFaces`).

- [ ] **Step 2: Edit the documentation**

Read each document first and keep its voice; find the paragraph by its text; add no section.

- `docs/ui-architecture.md`: in plan 4b's sentence on faces ("A structurally different control draws through a face
  …"), name the data faces after the form faces: "… the table and tree (sharing their header and box), the empty state,
  the tag, the route and the ordered list's side column …".
- `docs/ui-maintenance.md`: where plan 4b lists `face-pairs.tsv`, add `control-covering-array.tsv` ("the seven
  covering-array kits `KitMatrixTest` renders"); where it describes the gallery, add one sentence: "`-Pjasper.galleryKit=enterprise
  -Pjasper.galleryTheme=light` renders the gallery and the Enterprise fixtures under the Enterprise fixture kit into
  `build/gallery/enterprise-light-<scale>x/`; `tools/visual/check_pairs.py` compares the 2× renders with the Enterprise
  mocks (opt-in)."
- `jasper-ui/README.md`: where plan 4b names `KitFaces` and the faces, add the six data faces' names.
- `docs/plugin-ui.md`: extend plan 4b's sentence on the classic look ("Under the classic look an EXCLUSIVE choice draws
  radios …") with: "a Table draws a header band, zebra rows and banded headings, a Tree draws ▶ and ▼, an Empty state
  shows one button, a Chip is boxed in upper case, a ROUTE stack draws edged cells, and an ORDERED list's commands move
  to a side column that acts on the selection; the descriptors and events are the same."

- [ ] **Step 3: Write the STATUS entry**

Insert a new entry at the top of `docs/STATUS.md`, in the style of the plan-1 and plan-2 entries, filling the bracketed
values from Steps 4 and 5, Task 4 Step 4 and Task 5 Step 7, and `git log --oneline`:

```text
**Enterprise presentation, plan 4c: the classic data faces — 2026-10-16** (`claude/enterprise-classic-catalog`, worktree
`.claude/worktrees/enterprise-4`, `[first]..[last]`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-4c-classic-data-faces.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The data half of spec 6.4's face pairs,
each default face calling the control's former code, so the default look is byte-identical (the headless gallery for
Slate Cobalt Dark and Light matches plan 4a's base): the table (a header band with column separators and a rule, insets
after each separator, zebra rows or, grouped, heading bands with the rows under them indented by the new
`GROUP_ROW_INDENT`, a 1 px box outside panel bodies, `surface.rowHover`), the tree (the same header, box and hover, drawn
▶/▼, `TREE_INDENT` per depth, medium category rows), the empty state (no tile, one button), the tag (upper case in a
1 px `tag.border` box), the route (one boxed strip of equal cells with hue edges and drawn arrows) and the ordered list
(a square striped box with a data-face index and a side column — the add action, Remove, Move Up, Move Down — acting on
the selection, spanning a following entry row). Six Enterprise fixtures rebuild the mocks' compositions;
`KitFixtures.enterpriseLight()` is spec 7.6's Light table. The full kit matrix runs in `check`: the gallery and the
fixtures under the Enterprise kit over the theme set and Enterprise Light, the complement kit and a checked-in
strength-2 covering array over five palettes, `marked()` mapping every classic area to a role or a blend of two,
`custom()` finding no literal ([seconds] s for `KitMatrixTest`); `PaintedContrastTest` checks every classic pair's
guard; `ClassicContractTest` checks spec 16.1.9's events under both kits. Opt-in: `-Pjasper.galleryKit=enterprise` and
`tools/visual/check_pairs.py`, whose six pairs measured [name agreement, …] against floors of 0.30 (dialogs) and 0.40
(tables and lists); residuals: [each, or none beyond the plan's]. Rulings (plan, "Rulings"): a shared header face; the
box outside panel bodies; zebra only ungrouped; header titles in `text.strong.secondary` (mock `#4A4F57`); separator
insets; the grouped indent role; the heading count's place; drawn disclosure; row hover for tables and trees; one empty
button; boxed tags; edged routes with `ROUTE_HEIGHT` kept; the side column in the list's pane, its target, all four
buttons (Add Host's two a residual) and the selection shown unfocused; a LINK add action as a standard button; the
index face; guards checked in jasper-app and sampled in jasper-ui; the marked() rule; the covering array; Enterprise
Light as a fixture; the opt-in comparison and its floors; Unlock Vault's pair left to plan 6; the host contract under
Enterprise left to plan 5; the default matrix unchanged. Plan 5 turns the classic look on through the Enterprise kit.
`./gradlew check --continue`: **[tests] tests, [failures] failures, [errors] errors, [skipped] skipped**. GUI checks for
the user: none new (no shipped kit uses the classic faces until plan 5). Not merged, not pushed.
```

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    head = path.read_text(encoding="utf-8", errors="replace")[:2000]
    suite = re.search(r"<testsuite\b[^>]*>", head)
    if not suite: continue
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        m = re.search(r'\b%s="(\d+)"' % key, suite.group(0)); totals[i] += int(m.group(1)) if m else 0
print("tests=%d failures=%d errors=%d skipped=%d" % tuple(totals))
PY
```

Write the four numbers into the STATUS entry.

- [ ] **Step 5: Read the matrix's run time**

Run: `python3 -I -c "import re,pathlib; t=pathlib.Path('jasper-ui/build/test-results/test/TEST-dev.jasper.ui.gallery.KitMatrixTest.xml').read_text(); print(re.search(r'<testsuite\b[^>]*\btime=\"([0-9.]+)\"', t).group(1))"`
Expected: a number of seconds; write it into the STATUS entry.

- [ ] **Step 6: Set this plan's status banner**

Insert below this plan's title:

```text
> **Status (2026-10-16): executed on `claude/enterprise-classic-catalog` (`[first]..[last]`).** Deviations from this
> plan's text: [none, or each one, including any pair floor Task 5 Step 7 lowered and why].
```

- [ ] **Step 7: Commit**

```bash
git add docs/ui-architecture.md docs/ui-maintenance.md jasper-ui/README.md docs/plugin-ui.md docs/STATUS.md \
  docs/superpowers/plans/2026-10-16-enterprise-4c-classic-data-faces.md
git commit -F - <<'MSG'
Classic data faces: documentation and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```
