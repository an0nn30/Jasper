# Enterprise presentation 9b: the Settings page compositions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the Enterprise look (`Composition.CLASSIC`) the app's own Settings pages take spec 12.2's classic
compositions, each measured against its mock: the Appearance theme ⋯ menus, the Notifications grouped table, the Keymap
tree with Action, Scope and Shortcut columns and its inline recording band, the Plugins checkbox rows with Overview and
Details as folder tabs, and the Shell environment table with its Add Variable… sheet; every one of the eighteen
`05 Settings` mocks and `07b` is compared with the real page; the default look stays byte-identical except one approved
change (Rulings 2).

**Architecture:** Each composition branches once at its root on `PageContext.composition()` (spec 3.6, 9.1):
`AppearanceSelection` and `Editors` (theme menus, the environment table, the ordered lists' empty line),
`NotificationGroupsEditor` (the grouped table), `KeymapPage` (a `KeymapShape` value for `ShortcutPanel`, `ShortcutTree`
and `ShortcutEditPanel`) and `PluginsPage` (a `PluginShape` value for the manager's panel, list, rows and details);
everything below a root reads values. jasper-ui gains the few renderings the mocks need and no face had: a grouped
table's count under its last column and list heading bands (the table face), a keycap tree laid on its header's lanes,
the classic recording band and the recorder drawn as a field (the tree and button faces). `SettingsPagesMockTest`
renders each page in plan 9a's `EnterpriseSettings` fixture and compares its body with the mock.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 9.1, 12.2, 16 and 19.1 row 9), with
[DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) and the mocks in
[`docs/design/enterprise/reference/05 Settings/`](../../design/enterprise/reference/05%20Settings/). This plan is the
second of spec row 9's two plans; [9a](2026-10-16-enterprise-9a-settings-chrome.md) (the chrome) executes first on the
same branch, and its Global Constraints, Verified facts and Mock measurements apply here.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-9`, branch
`claude/enterprise-settings`, after plan 9a's last commit. Before Task 1:

```bash
git log --oneline -1 --grep 'Settings chrome: documentation and status'      # prints 9a's last commit
grep -c 'record Frame(boolean integratedTitle, boolean collapsibleNavigation)' jasper-ui/src/main/java/dev/jasper/ui/swing/SettingsShell.java  # 1
ls jasper-app/src/test/java/dev/jasper/app/settingsui/EnterpriseSettings.java jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatch.java
ls build/plan9-base/compare.py
```

If the base is gone, re-create it from 9a's last commit with plan 9a's "Before you start" (9a changed no default pixel).
Line numbers are from `deafed424`; every edit quotes the exact text it replaces, as plans 3c, 4b, 4c, 5a and 9a leave it,
and the scripts stop when it is not found the stated number of times: re-find the text, adjust the anchor, re-run, and
record the re-anchoring in the banner.

## Global Constraints

- Plan 9a's Global Constraints apply unchanged (Java 25 on JBR 25 through `./gradlew`, never `--stop`; no GUI, no AOT; work only on `claude/enterprise-settings`; one commit per task with the two trailer lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`; no push or merge; doclint; `migration.csv` rows; source hygiene; "hero" reserved).
- Pixel rule: the default look does not change, with one exception, Rulings 2: the Plugins page's Overview | Details becomes `ChoiceKind.TABS` in both looks (spec 12.2), which draws the same segment track; only if today's default render shows the choice's label "Plugin information" above the track may that line disappear from the `plugins*` files of `:jasper-app:settingsPreview`. Every other file the gallery and the Settings preview write stays byte-identical to `build/plan9-base` (plan 9a's compare script). Tasks 1 to 7 prove it.
- Only spec 9.1's composition roots read `Composition` (`CompositionReadTest`): `CorePages`, `AppearanceSelection`, `NotificationGroupsEditor`, `Editors`, `KeymapPage`, `PluginsPage` and their nested classes. The rest of each composition receives values (`KeymapShape`, `PluginShape`, an icon name, a boolean of the root's making).
- jasper-ui never sees `Composition`; a jasper-ui change is a face behaviour (in a face's default and classic implementations, the default returning today's value), a metric role, or a layout that only a descriptor the default look never builds can reach (a keycap tree with columns, a recorder value presentation).
- Every action stays reachable (spec 14.7): what a composition moves (theme import, plugin removal, Reset, Edit and Remove of a variable) is in a ⋯ or context menu.
- New metric roles follow plan 9a's rule (appended last, both tables, `NewRolesTest.ADDED`); a role whose classic value differs from its default joins `ClassicCatalogTest`'s exception set.

## Review Focus

1. A theme's ⋯ under Enterprise with nothing to remove (only built-in themes): Import Theme… is enabled and Remove Theme is
   disabled, as with the gear. Task 1: `ClassicCompositionsTest.theThemeMenusAreEllipsesWithTheGearsItems`.
2. A notification group whose saved display mode no longer exists, or a plugin's groups arriving while the page is open:
   the table shows the group's default in its Display cell and "Default" in Source, the new groups appear under their
   owner's heading with the right count, and Reset to Default in a row's menu returns a Custom row to Default. Task 2:
   `NotificationsTableTest` (three cases).
3. Keymap under Enterprise while searching, recording ("Find by Shortcut"), or editing a row whose section then collapses:
   the lanes stay aligned under Action, Scope and Shortcut, the band closes with its section, and Escape cancels the band.
   Task 3: `KeymapLanesTest.theLanesHoldWhileSearchingAndRecording`; Task 4: `KeymapBandTest.collapsingTheSectionClosesTheBand`.
4. Plugins under Enterprise with a plugin that needs review, a failed one and one pending removal: each row's checkbox
   shows its state and why it cannot act, and Remove… and Discard Install stay in the plugin's ⋯ and its row menu.
   Task 5: `PluginsCompositionTest.everyRowsStateAndActionStayReachable`.
5. An environment variable whose value is empty or contains `=`, an edit that renames a variable to an existing name, and
   Cancel in the sheet: the table shows the empty value, the rename replaces the other entry (as the file would hold one
   key), and Cancel changes nothing. Task 6: `EnvironmentTableTest` (three cases).

## Rulings

Decisions where the spec is silent or a literal reading cannot be built, taken on the user's behalf (2026-10-16):
"what — why — cost if wrong". Record them in `docs/STATUS.md` (Task 8) and in the banner.

1. **Each composition root maps `Composition` to a value once: a ⋯ icon name, `KeymapShape(columns, textTools,
   recorderField)`, `PluginShape(checkboxRows, ellipsisActions)`, a classic flag for its own editor** — spec 3.6 and 9.1
   ("the owner branches once at its composition root") — cost if wrong: none.
2. **Overview | Details is `ChoiceKind.TABS` in both looks, keeping the identity section's segment padding for TABS** —
   spec 12.2 ("in both looks; the default draws today's segment track"); plan 3b's TABS hides the choice's visible
   label, so the default render can lose the "Plugin information" label line if it showed one; no other pixel moves —
   cost if wrong: one recorded default-look change.
3. **The theme ⋯ is the gear's own menu with the "more" glyph, beside each theme dropdown and the terminal scheme
   dropdown** — spec 12.2; the mock draws none because its fixture has no themes folder, which the comparison
   reproduces (plan 9a's fixture has no `ThemeImports`) — cost if wrong: none.
4. **The grouped table's columns are weighted 10 : 3 : 2.2 (Group, Display, Source)** — the mock's columns 495, 147 and
   107 of 751 — cost if wrong: a few pixels.
5. **A grouped table's heading count stands under the last column's title** (the classic table face; the default face
   keeps it at the row's end) — the mock's "6 groups" starts where "Source" and "Default" do (2x 1827 against 1828);
   plan 4c Rulings 7 left this to plan 9 — cost if wrong: one face method.
6. **The Display cell's dropdown lists the modes without their icons; Source reads "Default" or "Custom"; each row's
   menu holds Reset to Default; the description line stays above the table** — spec 12.2 and the mock ("Card ▼",
   "Default") — cost if wrong: none.
7. **The classic Keymap tree is a keycap tree with three columns; its rows lie on lanes measured from the tree's right
   edge: Shortcut 137 wide, Scope 107; keys and a section's "N shortcuts" start 25 into the Shortcut lane; a scope ends
   30 before it; the titles keep the classic header's cell inset** — the mock (separators at 2x 1560 and 1774, keys and
   counts at 1824, "Global" ending at 1713, titles at 1579 and 1793); the lanes are four roles equal in both catalogs,
   read only by a keycap tree with columns, which only the classic Keymap builds — cost if wrong: four role values.
8. **A section row reads "N shortcuts" (1 shortcut) in its Shortcut lane instead of a count, its modified mark follows
   its title, and a read-only contextual key shows no lock glyph in the lanes** (the status line and the row's tooltip
   still say it) — the mock ("3 shortcuts", "Application ●", no lock) — cost if wrong: one glyph.
9. **The Keymap tools under Enterprise are the text buttons Find by Shortcut (the recording search; "Stop" while it
   records), Expand All and Collapse All; Clear appears beside them while there is something to clear; the search field
   has no trailing Record glyph; the status line has no lock glyph** — spec 12.2 and the mock — cost if wrong: none.
10. **The recording band is the tree face's: the edited row stays selected, the band below it fills the tree's width on
    `surface.rowHover` with 1 px edges of `border.focus` at 20% over it (#C8D6F0 for #C9D7F2 in Enterprise Light), and the
    recorder is drawn as a classic field (`surface.input`, the focus ring while armed, a solid 7 px `status.danger` dot,
    the prompt in `text.placeholder`)** — spec 12.2 and the mock; the edge's alpha is a metric role
    (`KEYCAP_DETAIL_EDGE_ALPHA`, 0 by default) — cost if wrong: an edge one shade off.
11. **The band's hint line holds the message and the ⋯ of shortcut actions, without the Record text action** — spec
    12.2 lists the field (a click starts recording), Cancel, Set and the hint line with the ⋯; the mock draws no Record —
    cost if wrong: none; Record any key… stays in the ⋯.
12. **The classic Plugins list is one boxed lane on `surface.card` holding the search row (with the list's ⋯, Install from
    Zip…) and the rows; a row leads with its enable checkbox and has no icon; a group heading is a `surface.tableHeader`
    band between `border.row` rules (the table face's list headings); the plugin's ⋯ (its Open… actions and Remove… or
    Discard Install) sits beside Disable** — spec 12.2 ("install from disk and uninstall move to ⋯ menus"), the mock's box,
    bands and rules — cost if wrong: one ⋯ the mock does not draw.
13. **The environment table under Enterprise is a Name / Value table under the row "Environment variables" with Add
    Variable… at the row's end; with no variables the table is not built; double-click and the row menu's Edit… open
    the sheet; Remove is in the row menu; the sheet is a SHEET form with Name and Value fields and Add or Save** — spec
    12.2 — cost if wrong: none.
14. **A sheet is asked through `PageContext.sheets()` (a `Sheets` value the dialog implements as it implements its
    confirmations); pages made without a dialog get `Sheets.NONE`, which answers Cancel** — editors receive only their
    `PageContext` — cost if wrong: one component.
15. **The ordered lists of the Settings pages show "Empty" while they hold nothing, under Enterprise** (`Editors.Items`,
    plan 3c's `ItemList.emptyText`) — the Shell mock's arguments box — cost if wrong: none.
16. **The page comparison crops each mock's body (right of the sidebar rule, below the header rule, above the footer
    rule) and asserts its probes on every OS and its share on macOS, from a 0.93 floor that may only be lowered, with
    its cause recorded, to the measured share rounded down to 0.01 when the difference image shows text rasterization or
    fixture text, never below 0.85** — plan 9a Rulings 17 — cost if wrong: none.

## Verified facts

Read at `deafed424`; facts about plans 3b to 9a cite the plan.

1. `ThemeMenu.gear(id, shown)` builds a `MenuButton` with the "settings" glyph and the tooltip "Import or remove themes"
   (`ThemeMenu.java:53-62`); `AppearanceSelection` makes one per theme dropdown (`:37-38`, `withGear` at `:79-82`) and
   `SchemePick` one beside the scheme dropdown (`SchemePick.java:29`, `:45`); "more" is the built-in horizontal ellipsis
   (`IconRegistry.java:366`).
2. `NotificationGroupsEditor.node` builds a SUPPORTING description, then per owner a `SETTINGS_GROUP_HEADER` stack (owner
   icon, PANEL_TITLE, "N groups" COUNT) and a SETTINGS card of rows with a mode DROPDOWN and Reset
   (`NotificationGroupsEditor.java:57-92`); `MODES` is card, sticky, history, off (`:145-152`); plan 9a Task 3 adds
   `inlineReset`.
3. Plan 3c: `TableRow(id, cells, icon, muted, contextActions, tooltip, cellNodes, heading)`, `TableRow.heading(id, title,
   count, countNoun)` ("6 groups"), a cell node may be a DROPDOWN; `Column(id, title, role, numeric, weight, sorted)`;
   `Nodes.Tree(…, activateAction, columns)`; `Nodes.ItemList(…, cancelAction, emptyText)`; `InteractiveRows.Row.headingLayout`
   puts a table heading's count at the row's end (3c Task 3); `Trees.ColumnHeaderLayout` packs a column tree's titles
   right over its values (3c Task 5).
4. Plan 4c: `TableFace` and `TreeFace` extend `HeaderFace`; `KitFaces.table` and `.tree`; `ClassicTableFace` insets a cell
   after its separator and indents a grouped row 14; `Tables.TableControl.header` holds the title labels styled by the
   face (`Tables.CellInset`); `ClassicTreeFace.disclosure()` is EMBEDDED and `styleDisclosure` draws the triangle; the
   keycap trees keep their geometry "until plan 9" (4c Rulings 8).
5. `ShortcutTree.descriptor` builds sections (`group/<name>`, icon, count, emphasized when customized) and leaves
   (`action/<id>`, trailing `[where, keys]`) with no columns (`ShortcutTree.java:98-118`); `presentation()` gives each leaf
   its context, lock and keycaps (`:92-96`).
6. `ShortcutPanel` builds the search (with the embedded Record glyph as its trailing), the tools (Clear, expand.all and
   collapse.all glyph buttons) and the status line with a lock icon (`ShortcutPanel.java:177-216`); "record" toggles the
   recording search, "expand" and "collapse" open and close every section (`:117-149`).
7. `ShortcutEditPanel.spec` builds the band: the recorder value button `editor.value`, Cancel and Set in a NAVIGATION
   stack, then the note (message, the Record text button, the ⋯ `editor.options`) and the reassign row
   (`ShortcutEditPanel.java:159-179`); `update` sets the value's `ButtonValuePresentation` RECORDER or RECORDER_ARMED
   (`:143-147`).
8. `Trees.TreeControl.attach` mounts the detail view, `layoutAccessories` places it below the row by the KEYCAP_DETAIL
   roles (`Trees.java:95-104`, `:182-188`); `InteractiveRows.Canvas.paintComponent` paints the keycap detail's card
   (`SURFACE_CONTROL`, `BORDER_FOCUS`, rounded, from the row's top) and `Row.paintFill` skips the edited row
   (`InteractiveRows.java:739-753`, `:818-821`); `layoutPresented` lays a presented row's title, context and keys
   (`Trees.java:138-160`).
9. `Buttons.KitButton` paints a recorder value as an input with the armed dot and halo (`Buttons.java:109-115`,
   `:342-357`); plan 4b's `ClassicButtonFace.paintFace` hands recorders to `paintRaised` (4b Task 2:
   `if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK) { b.paintRaised(g, hover, focused); return; }`)
   and `text` returns the default role for them.
10. `PluginCard.item` builds a row with the plugin icon (leading), name BODY_STRONG, a meta subtitle and the enable
    switch (trailing) and its menu (`PluginCard.java:23-45`); `PluginCardList.spec` makes a DOUBLE `ItemList` of group
    headings and rows (`PluginCardList.java:135-152`); `PluginManagerPanel` lays the search and the "more" gear above the
    list, beside the details (`PluginManagerPanel.java:53-90`, gear at `:167-170`); `PluginDetails.headerSpec` builds the
    identity head with Disable, the ancillary line with the homepage link and the chevron More actions, and the EXCLUSIVE
    Overview | Details choice in a SETTINGS card (`PluginDetails.java:171-198`); `ViewReconciler` gives an EXCLUSIVE choice
    in the identity section its padding (`ViewReconciler.java:374`).
11. `Editors.Table` edits `terminal.env` as Name/Value field rows with remove buttons and "Add variable"
    (`Editors.java:446-490`); `Editors.Items` builds an ORDERED `ItemList` and an entry (`:385-410`).
12. `SettingsDialog.confirm` asks a SHEET form over the dialog with `windows.dialog(title, true, surface)` and a kit view
    (`SettingsDialog.java:128-149`).
13. Plan 9a: `PageContext(… kit, chrome, composition)`, `SchemaPage.resetMenu`, `EnterpriseSettings.open/render`,
    `SettingsChromeMockTest.PAGES`, `MockMatch`.

### Mock measurements

Logical pixels at the mocks' 2x, halved; positions inside a table, tree or list box count from the box's inner edge (the
canvas); colours are Enterprise Light's.

| Measured | Mock | Value | Role or rule |
|---|---|---|---|
| Groups table | 03 | box 751 wide from the Groups box's padding; header 20; heading rows 22 on #F0F1F3; data rows 22 white | 4c's classic table |
| Columns | 03 | Group 495, Display 147, Source 107; titles 9.5 after each separator | Rulings 4, 4c cell inset |
| Rows | 03 | heading title at 9; a group's name at 23 (14 deeper); "Card ▼" at Display + 11.5; "Default" and "6 groups" at Source + 11 | 4c `GROUP_ROW_INDENT`, Rulings 5 |
| Keymap tools | 07 | search 22 tall at 19 below the header rule, 16 from the body's sides; Find by Shortcut 110, Expand All 80, Collapse All 85, 24 tall, 6 apart | `KEYMAP_PAGE_TOP_PADDING` 18, `KEYMAP_PAGE_PADDING` 16, `KEYMAP_TOOLS_GAP` 6 |
| Keymap tree | 07 | box from 48 below the header rule; header 20; lanes Scope 107 and Shortcut 137 from the right edge (canvas 775 wide) | `KEYMAP_SECTION_GAP` 6, `KEYMAP_SCOPE_COLUMN`, `KEYMAP_SHORTCUT_COLUMN` |
| Keymap rows | 07 | sections 22 tall: ▶ at 8.5, title at 25.5, "N shortcuts" at Shortcut + 25; leaves 24 tall: title at 34.5, "Global" ending 30.5 before Shortcut, keys at Shortcut + 25, keycaps 16 tall, 2 apart | `KEYCAP_GROUP_SIDE` 8, `KEYCAP_GROUP_TEXT_GAP` 7, `KEYCAP_TREE_LEAF_INDENT` 26, `KEYMAP_KEYS_INSET` 25, `KEYMAP_SCOPE_GAP` 30, `KEYCAP_DETAIL_INSET` 4 |
| Status line | 07 | "131 shortcuts · Contextual keys are read-only" #5C6370, no glyph | Rulings 9 |
| Band | 07b | from the row's bottom, 60 tall incl. 1 px edges #C9D7F2, #EEF2FA; the field from canvas 34 (its halo from 32) to 644.5, 22 tall, 7 below the band's top; Cancel 62 and Set 48, 24 tall, 6 apart, ending 8 before the box; the hint at the field's left in #5C6370 | `KEYCAP_DETAIL_TOP` 7, `KEYCAP_DETAIL_LEADING` 32, `KEYCAP_CONTENT_TRAILING` 8, `KEYCAP_DETAIL_AFTER` 0, Rulings 10 |
| Recorder | 07b | #FFFFFF in #2F65CA with a 1 px #C9D7F2 halo; dot 7 #D03A3A at 9.5; prompt #838992 at 27.5 | Rulings 10 |
| Plugins list | 08 | box 292 wide at the body's left 16, from 18 below the header rule to 16 above the footer; search inside at 6; group bands 22 on #F0F1F3 with #DDE0E4 rules; rows 36; checkbox at 8, name at 28.5; selection across the box | Rulings 12 |
| Plugin details | 08 | 12 right of the box; title 15/600; meta line; Disable ending at the body's right 16; folder tabs 26 tall below | 4b's folder tabs |
| Shell | 11 | "Environment variables" row with Add Variable… (100 × 24) at the box's padding; arguments box "Empty" | Rulings 13, 15 |

## File structure

- `jasper-app/src/main/java/dev/jasper/app/settingsui/`: `ThemeMenu`, `SchemePick`, `AppearanceSelection`, `Editors`
  (menus, the environment composition, empty lines), `NotificationGroupsEditor` (the table), `PageContext`, `Sheets`
  (new), `SettingsDialog` (sheets).
- `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/`: `KeymapShape` (new), `KeymapPage`, `ShortcutPanel`,
  `ShortcutTree`, `ShortcutEditPanel`.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/`: `PluginShape` (new), `PluginsPage`, `PluginManager`,
  `PluginManagerPanel`, `PluginCardList`, `PluginCard`, `PluginDetails`.
- `jasper-ui/src/main/java/dev/jasper/ui/swing/`: `TableFace`, `DefaultTableFace`, `ClassicTableFace`, `TreeFace`,
  `DefaultTreeFace`, `ClassicTreeFace`, `ClassicButtonFace`, `Tables`, `Trees`, `InteractiveRows`, `ViewReconciler`;
  `metrics/DesignMetrics.java` (five roles and the classic Keymap values).
- Tests: `jasper-app/src/test/java/dev/jasper/app/settingsui/` `ClassicCompositionsTest`, `NotificationsTableTest`,
  `EnvironmentTableTest`, `SettingsPagesMockTest`; `shortcuthelp/KeymapLanesTest`, `KeymapBandTest`;
  `pluginmanager/PluginsCompositionTest`; `jasper-ui/src/test/java/dev/jasper/ui/swing/` `ClassicListHeadingsTest`,
  `KeycapLanesTest`, `RecordingBandTest`.

---

### Task 1: The page comparison and the Appearance theme menus

`SettingsPagesMockTest` compares a page's body with its mock (Rulings 16) and starts with the two Appearance & Behavior
pages whose controls plans 4b and 9a already draw: Appearance and Window. Under `Composition.CLASSIC` each theme gear is a
⋯ with the same menu.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeMenu.java:37-62`, `SchemePick.java:21-45`, `AppearanceSelection.java:34-38`, `Editors.java:154-157`
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/ClassicCompositionsTest.java`, `SettingsPagesMockTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (two rows)

**Interfaces:**
- Consumes: plan 9a's `PageContext.composition()`, `EnterpriseSettings`, `SettingsChromeMockTest.Page` and `PAGES`, `MockMatch`.
- Produces: `ThemeMenu(ThemeImports, Supplier<InstalledThemes>, PageContext, Target, Runnable, String icon)`;
  package-private `static String Editors.menuIcon(PageContext)` ("more" under CLASSIC, "settings" otherwise);
  `SchemePick(String, Supplier<InstalledThemes>, Optional<ThemeImports>, PageContext, String icon)`;
  `SettingsPagesMockTest.body(Page, BufferedImage)`, `share(…)`, `PROBES` (a map from page id to its probes), which
  Tasks 2 to 7 extend.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/ClassicCompositionsTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Spec 12.2: the Appearance theme menus under each composition. */
class ClassicCompositionsTest {
    static PageContext context(Composition composition) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, composition == Composition.CLASSIC ? SettingsChromeTest.CLASSIC : LookRowsTest.LINUX_DEFAULT, composition);
    }

    static AppearanceChoices withImports() {
        var imports = new ThemeImports(new ThemeFolder(Path.of("themes"), ThemeLibrary::bundledOnly), surface -> Optional.empty(),
            (surface, question) -> { }, (surface, notice) -> { }, edit -> CompletableFuture.completedFuture(null),
            () -> CompletableFuture.completedFuture(null), Runnable::run, Runnable::run);
        var base = AppearanceFixtures.CHOICES;
        return new AppearanceChoices(base.opacity(), base.blur(), base.systemDark(), ignored -> new Subscription(() -> { }), base.themes(), Optional.of(imports));
    }

    static Nodes.Node find(Nodes.Node node, String id) { return ResetMenuTest.find(node, id); }

    @Test void theThemeMenusAreEllipsesWithTheGearsItems() throws Exception {
        edt(() -> {
            for (var composition : Composition.values()) {
                var editor = new AppearanceSelection(withImports(), context(composition));
                var root = editor.node("ui.appearance", dev.jasper.ui.model.ControlState.READY);
                for (var id : List.of("ui.appearance:dark-gear", "ui.appearance:light-gear")) {
                    var menu = (Nodes.MenuButton) find(root, id);
                    assertThat(menu.icon()).as(composition + " " + id).isEqualTo(composition == Composition.CLASSIC ? "more" : "settings");
                    assertThat(menu.items()).extracting(MenuItem::label).containsExactly("Import Theme…", "", "Remove Theme");
                    assertThat(menu.items().get(2).enabled()).as("only built-in themes: nothing to remove").isFalse();
                }
                editor.dispose();
                var scheme = new SchemePick("Terminal scheme", AppearanceFixtures.CHOICES.themes(), withImports().imports(), context(composition),
                    Editors.menuIcon(context(composition)));
                var gear = (Nodes.MenuButton) find(scheme.node("terminal.scheme", dev.jasper.ui.model.ControlState.READY), "terminal.scheme:gear");
                assertThat(gear.icon()).isEqualTo(composition == Composition.CLASSIC ? "more" : "settings");
                scheme.dispose();
            }
        });
    }
}
```

`ThemeFolder(Path, Supplier<ThemeLibrary>)` is plan 5b's constructor; `AppearanceChoices` has the components of fact
1's fixture (`AppearanceFixtures.java:16-19`).

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java`:

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.MockMatch;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 12.2 and 16.1.4: each Settings page's body against its 05 Settings mock (plan 9b Rulings
 * 16): probes on every OS, the pixel share on macOS. Renders go to jasper-app/build/enterprise-settings/pages.
 */
class SettingsPagesMockTest {
    static final double FLOOR = 0.93;
    /** Per page, the probes: a name, a logical point of the content and the mock's pixel there is the target. */
    static final Map<String, List<SettingsChromeMockTest.Probe>> PROBES = new LinkedHashMap<>();
    /** Per page, a floor lowered under Rulings 16, with its cause in the banner. */
    static final Map<String, Double> FLOORS = new LinkedHashMap<>();
    static {
        PROBES.put("appearance", List.of(new SettingsChromeMockTest.Probe("Light preview frame", 414, 102),
            new SettingsChromeMockTest.Probe("Light radio dot", 420, 186), new SettingsChromeMockTest.Probe("Dark theme dropdown edge", 760, 232),
            new SettingsChromeMockTest.Probe("dropdown arrow", 961, 232)));
        PROBES.put("window", List.of(new SettingsChromeMockTest.Probe("Icons & Labels radio", 721, 112),
            new SettingsChromeMockTest.Probe("spinner box", 900, 152), new SettingsChromeMockTest.Probe("spinner arrows", 963, 145)));
    }

    /** The body: right of the sidebar rule, below the header rule, above the footer rule. */
    static Rectangle body(SettingsChromeMockTest.Page page) { return new Rectangle(2 * 231, 2 * 35, 2080 - 2 * 231, 2 * (page.height() - 35 - 45)); }

    static SettingsChromeMockTest.Page page(String id) {
        return SettingsChromeMockTest.PAGES.stream().filter(page -> page.id().equals(id)).findFirst().orElseThrow();
    }

    /** Renders {@code ids}, checks each page's probes and, on macOS, its body share; the failures. */
    static List<String> compare(EnterpriseSettings settings, List<String> ids) throws Exception {
        var failures = new ArrayList<String>();
        var shares = new StringBuilder();
        for (String id : ids) {
            var page = page(id);
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> render[0] = settings.render(id, 1040, page.height()));
            var out = Files.createDirectories(EnterpriseSettings.OUT.resolve("pages"));
            String number = page.mock().substring(9, 11);
            ImageIO.write(render[0], "png", out.resolve(number + ".png").toFile());
            var mock = SettingsChromeMockTest.content(page);
            for (var probe : PROBES.getOrDefault(id, List.of())) {
                int x = 2 * probe.x(), y = 2 * probe.y();
                double distance = MockMatch.deltaE(render[0].getRGB(x, y), mock.getRGB(x, y));
                if (distance > SettingsChromeMockTest.PROBE) failures.add(String.format("%s %s at (%d, %d): render #%06X, mock #%06X, ΔE %.1f", id,
                    probe.name(), probe.x(), probe.y(), render[0].getRGB(x, y) & 0xFFFFFF, mock.getRGB(x, y) & 0xFFFFFF, distance));
            }
            var ours = MockMatch.crop(render[0], body(page)); var theirs = MockMatch.crop(mock, body(page));
            var result = MockMatch.compare(ours, theirs, 2, SettingsChromeMockTest.TOLERANCE);
            double floor = FLOORS.getOrDefault(id, FLOOR);
            shares.append(String.format("%s %.3f (%d, %d) floor %.2f%n", id, result.matching(), result.dx(), result.dy(), floor));
            if (result.matching() < floor) {
                MockMatch.inspect(EnterpriseSettings.OUT.resolve("compare"), number + "-body", ours, theirs, SettingsChromeMockTest.TOLERANCE);
                if (SettingsChromeMockTest.MAC) failures.add(String.format("%s body: %.3f below %.2f", id, result.matching(), floor));
            }
        }
        Files.writeString(EnterpriseSettings.OUT.resolve("pages-" + ids.getFirst() + ".txt"), shares);
        return failures;
    }

    @Test void appearanceAndWindowMatchTheirMocks() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("appearance", "window"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
}
```

The probe points are inside drawn parts of the two mocks (a preview frame, a radio dot, a dropdown edge and arrow, a
radio, a spinner's box and arrows); `SettingsChromeMockTest.Probe`, `content`, `PROBE`, `TOLERANCE` and `MAC` are plan
9a's package members.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.ClassicCompositionsTest' --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --continue`
Expected: FAIL at `compileTestJava`: "constructor SchemePick … cannot be applied" and "cannot find symbol: method menuIcon".

- [ ] **Step 3: Give the theme gears the composition's glyph**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 1 step 3: the theme gears are a ⋯ under the classic composition (spec 12.2)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "ThemeMenu.java", [
    ('''    private final Runnable render;
    private boolean busy;

    ThemeMenu(ThemeImports hooks, Supplier<InstalledThemes> themes, PageContext context, Target target, Runnable render) {
        this.hooks = hooks;''', '''    private final Runnable render;
    private final String icon;
    private boolean busy;

    /** A menu whose button shows {@code icon}: the gear, or the ⋯ of the classic composition (spec 12.2). */
    ThemeMenu(ThemeImports hooks, Supplier<InstalledThemes> themes, PageContext context, Target target, Runnable render, String icon) {
        this.icon = icon;
        this.hooks = hooks;''', 1),
    ('''        return new Nodes.MenuButton(id, new ControlState(true, !busy, busy, ""), "", "settings", Nodes.ButtonKind.GHOST, "", items,''',
     '''        return new Nodes.MenuButton(id, new ControlState(true, !busy, busy, ""), "", icon, Nodes.ButtonKind.GHOST, "", items,''', 1)])
edit(S + "AppearanceSelection.java", [
    ('''        darkMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.DARK, () -> render.run()));
        lightMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.LIGHT, () -> render.run()));''',
     '''        // Spec 12.2: the classic composition's theme menus are a ⋯ beside each dropdown, with the gear's items.
        String icon = context.composition() == dev.jasper.app.presentation.Composition.CLASSIC ? "more" : "settings";
        darkMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.DARK, () -> render.run(), icon));
        lightMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.LIGHT, () -> render.run(), icon));''', 1)])
path = pathlib.Path(S + "SchemePick.java"); text = path.read_text(encoding="utf-8")
old = "ThemeMenu.Target.TERMINAL, () -> render.run()));"
if text.count(old) != 1: raise SystemExit("SchemePick: its ThemeMenu moved")
text = text.replace(old, "ThemeMenu.Target.TERMINAL, () -> render.run(), icon));", 1)
import re
ctor = re.search(r"    SchemePick\(([^)]*)PageContext context\) \{", text)
if not ctor: raise SystemExit("SchemePick: its constructor moved")
text = text.replace(ctor.group(0), f"    SchemePick({ctor.group(1)}PageContext context, String icon) {{", 1)
path.write_text(text, encoding="utf-8")
edit(S + "Editors.java", [
    ('''        return new Factory<>((setting, context) -> new SchemePick(setting.label(), appearance.themes(), appearance.imports(), context),''',
     '''        return new Factory<>((setting, context) -> new SchemePick(setting.label(), appearance.themes(), appearance.imports(), context, menuIcon(context)),''', 1),
    ('''    /** The Dark / Light / Match System tiles, then the Dark theme and Light theme rows over the installed themes. */''',
     '''    /** The glyph of a theme menu's button: the classic composition's ⋯, else the gear (spec 12.2). */
    static String menuIcon(PageContext context) {
        return context.composition() == dev.jasper.app.presentation.Composition.CLASSIC ? "more" : "settings";
    }

    /** The Dark / Light / Match System tiles, then the Dark theme and Light theme rows over the installed themes. */''', 1)])
PY
```

If `SchemePick`'s constructor declares its parameters across lines, the regular expression still matches (it stops at
`PageContext context) {`); if it does not, add `, String icon` after `PageContext context` by hand and keep the rest.
`AppearanceSelection` is a composition root, so it reads `Composition` itself; `SchemePick` and `ThemeMenu` receive the
glyph. Add the two tests' `migration.csv` rows:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/settingsui/ClassicCompositionsTest.java,app.settingsui,ClassicCompositionsTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/ClassicCompositionsTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 1; the classic page compositions",
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java,app.settingsui,SettingsPagesMockTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 1; each Settings page against its mock"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and compare with the mocks**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.architecture.CompositionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicCompositionsTest` (1), `SettingsPagesMockTest` (1), `CompositionReadTest` (the new readers are the
roots `AppearanceSelection` and `Editors`), `AppearancePageTest` and `ThemeMenuTest` unchanged.

For a `SettingsPagesMockTest` failure, open `jasper-app/build/enterprise-settings/compare/<nn>-body-diff.png` and fix by
cause, re-running after each change: a probe failure is a geometry or role defect (a classic face's value from plans 4a
and 4b, or a Settings page role of plan 9a, both tables); magenta along whole control or box edges is geometry; magenta
inside glyphs, or the fixture's own text (the "View mode" box below Interface Font, "Use the default font size" for the
mock's "Use platform font size"), is a residual under Rulings 16: put the page's measured share, rounded down to 0.01
and at least 0.85, in `FLOORS` with a comment naming the cause, and record it in the banner.

- [ ] **Step 5: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-app/src/main/java/dev/jasper/app/settingsui jasper-app/src/test/java/dev/jasper/app/settingsui/ClassicCompositionsTest.java \
  jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: the page comparison and the Appearance theme menus

SettingsPagesMockTest compares a Settings page's body under the Enterprise
look with its mock, probes on every OS and the pixel share on macOS,
starting with Appearance and Window. Under the classic composition each
theme dropdown's gear is a ⋯ with the same Import and Remove items.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: `changed: none`.

### Task 2: Notifications as one grouped table

Under `Composition.CLASSIC` the groups editor is one table (spec 12.2): Group, Display and Source columns, a heading row
per owner with "N groups", the Display cell a borderless DROPDOWN, Source "Default" or "Custom", and Reset to Default in
each row's menu. The classic table face puts a heading's count under the last column's title (Rulings 5).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupsEditor.java`, `Editors.java:191-194`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/TableFace.java`, `DefaultTableFace.java`, `ClassicTableFace.java` (plan 4c's files), `Tables.java`, `InteractiveRows.java` (3c's `headingLayout`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicListHeadingsTest.java` (the count; Task 5 adds the list headings), `jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationsTableTest.java`
- Modify: `SettingsPagesMockTest.java` (probes and a test), `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: plan 3c's `TableRow`, `TableRow.heading`, `Column`, cell DROPDOWN; plan 4c's `TableFace`, `Tables.TableControl.header`;
  plan 9a's `NotificationGroupsEditor(choices, kit, inlineReset)`.
- Produces: `NotificationGroupsEditor(NotificationGroupChoices, Supplier<KitContext>, boolean inlineReset, boolean table)`;
  `boolean TableFace.countUnderLastColumn()`; `int InteractiveRows.RowsControl.headingCountStart()` (−1 by default),
  overridden by `Tables.TableControl`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicListHeadingsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plan 9b Rulings 5 and 12: the classic table face's heading counts and list heading bands. */
class ClassicListHeadingsTest {
    static ViewSpec groups() {
        var columns = List.of(new Column("group", "Group", Nodes.TextRole.BODY, false, 10, Optional.empty()),
            new Column("display", "Display", Nodes.TextRole.BODY, false, 3, Optional.empty()), new Column("source", "Source", Nodes.TextRole.BODY, false, 2.2, Optional.empty()));
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, columns, List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(6), "group"),
            new TableRow("a", Map.of("group", "Configuration", "display", "Sticky card", "source", "Default"), Optional.empty())), Set.of(), "", List.of()));
    }

    @Test void aClassicHeadingsCountStandsUnderTheLastColumnsTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var source = table.header.getComponent(2);
                int titleStart = source.getX() + ((javax.swing.JComponent) source).getInsets().left;
                var heading = table.mounted.get("jasper");
                assertThat(heading.trailing.getX()).as("the count under Source").isEqualTo(titleStart);
            } finally { release(context); }
        });
    }

    @Test void theDefaultHeadingsCountStaysAtTheRowsEnd() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var heading = table.mounted.get("jasper");
                assertThat(table.headingCountStart()).isEqualTo(-1);
                assertThat(heading.trailing.getX() + heading.trailing.getWidth()).as("at the row's end, inset")
                    .isLessThanOrEqualTo(heading.getWidth()).isGreaterThan(heading.getWidth() - 40);
            } finally { release(context); }
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationsTableTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.Composition;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): Notifications' groups as one grouped table. */
class NotificationsTableTest {
    static final String ID = "notifications.groups";

    static Nodes.Table table(NotificationGroupsEditor editor) {
        return (Nodes.Table) ResetMenuTest.find(editor.node(ID, ControlState.READY), ID + ":table");
    }

    @Test void everyOwnerHeadsItsGroupsWithTheirDisplayAndSource() throws Exception {
        edt(() -> {
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(EnterpriseSettings::groups), null, false, true);
            editor.show(Map.of("jasper.config", "card"));
            var table = table(editor);
            assertThat(table.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Group", "Display", "Source");
            var headings = table.rows().stream().filter(row -> row.heading().isPresent()).map(row -> row.heading().orElseThrow()).toList();
            assertThat(headings).extracting(TableRow.Heading::title).containsExactly("Jasper", "THD Sourcing Crypto", "Editor", "Credential Vault", "File Server", "Snippets");
            assertThat(headings.getFirst().count()).hasValue(6);
            assertThat(headings.getFirst().countNoun()).isEqualTo("group");
            var configuration = table.rows().stream().filter(row -> "Configuration".equals(row.cells().get("group"))).findFirst().orElseThrow();
            assertThat(configuration.cells().get("source")).as("saved as card, its default is sticky").isEqualTo("Custom");
            var display = (Nodes.Choice) configuration.cellNodes().get("display");
            assertThat(display.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
            assertThat(display.selectedId()).isEqualTo("card");
            assertThat(display.options()).allMatch(option -> option.icon().isEmpty());
            assertThat(configuration.contextActions().orElseThrow()).extracting(MenuItem::label).containsExactly("Reset to Default");
            assertThat(ResetMenuTest.find(editor.node(ID, ControlState.READY), ID + ":description")).as("the description stays").isNotNull();
        });
    }

    @Test void resetToDefaultReturnsACustomRowToDefault() throws Exception {
        edt(() -> {
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(EnterpriseSettings::groups), null, false, true);
            editor.show(Map.of("jasper.config", "card"));
            int index = EnterpriseSettings.groups().stream().map(NotificationGroupChoices.Group::id).toList().indexOf("jasper.config");
            assertThat(editor.event(ID, new UiEvent.Action(NotificationGroupsEditor.resetId(ID, index)))).isTrue();
            assertThat(editor.value()).doesNotContainKey("jasper.config");
            var row = table(editor).rows().stream().filter(candidate -> "Configuration".equals(candidate.cells().get("group"))).findFirst().orElseThrow();
            assertThat(row.cells().get("source")).isEqualTo("Default");
            assertThat(((Nodes.Choice) row.cellNodes().get("display")).selectedId()).isEqualTo("sticky");
            assertThat(row.contextActions().orElseThrow().getFirst().enabled()).isFalse();
        });
    }

    @Test void aGroupThatArrivesLaterJoinsItsOwnersHeadingAndAnUnknownModeShowsTheDefault() throws Exception {
        edt(() -> {
            var groups = new ArrayList<>(EnterpriseSettings.groups().subList(0, 2));
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(() -> List.copyOf(groups)), null, false, true);
            editor.show(Map.of("jasper.general", "retired-mode"));
            var general = table(editor).rows().stream().filter(row -> "Jasper".equals(row.cells().get("group"))).findFirst().orElseThrow();
            assertThat(((Nodes.Choice) general.cellNodes().get("display")).selectedId()).isEqualTo("card");
            groups.add(EnterpriseSettings.groups().get(2));
            editor.refresh();
            var heading = table(editor).rows().getFirst().heading().orElseThrow();
            assertThat(heading.count()).hasValue(3);
            assertThat(editor.value()).as("an unknown saved mode is kept as written").containsEntry("jasper.general", "retired-mode");
        });
    }
}
```

`EnterpriseSettings.groups()` is plan 9a's fixture (the mock's seventeen groups under six owners; Configuration's
default is sticky). `Nodes.Choice.selectedId()` and `Option.icon()` are the jasper-ui model's accessors; if they are named
otherwise, use their names.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicListHeadingsTest' :jasper-app:test --tests 'dev.jasper.app.settingsui.NotificationsTableTest' --continue`
Expected: FAIL at compile: "cannot find symbol: method headingCountStart()" and "constructor NotificationGroupsEditor …
cannot be applied to given types" (four arguments).

- [ ] **Step 3: Put the heading count under the last column**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 2 step 3: the classic table face puts a heading's count under the last column's title (Rulings 5)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the closing brace last")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
before_last_brace(S + "TableFace.java", '''    /** Whether a heading row's count stands under the last column's title (the classic table); otherwise at the row's end. */
    boolean countUnderLastColumn();
''')
before_last_brace(S + "DefaultTableFace.java", '''    @Override public boolean countUnderLastColumn() { return false; }
''')
before_last_brace(S + "ClassicTableFace.java", '''    /** Plan 9b Rulings 5: the Notifications mock's "6 groups" starts where the Source column's title does. */
    @Override public boolean countUnderLastColumn() { return true; }
''')
edit(S + "InteractiveRows.java", [
    ('''            if (tw == 0) trailing.setBounds(0, 0, 0, 0);
            else trailing.setBounds(getWidth() - pad - tw, top, tw, line);''',
     '''            int countAt = owner.headingCountStart();
            if (tw == 0) trailing.setBounds(0, 0, 0, 0);
            else if (countAt >= 0) trailing.setBounds(Math.min(countAt, Math.max(0, getWidth() - pad - tw)), top, tw, line);
            else trailing.setBounds(getWidth() - pad - tw, top, tw, line);''', 1)])
path = pathlib.Path(S + "InteractiveRows.java"); text = path.read_text(encoding="utf-8")
anchor = "        abstract List<MenuItem> contextActions();\n"
if text.count(anchor) != 1: raise SystemExit("InteractiveRows: RowsControl's contextActions moved")
text = text.replace(anchor, anchor + '''        /** Where a heading row's count starts, in row coordinates; -1 puts it at the row's end (plan 9b Rulings 5). */
        int headingCountStart() { return -1; }
''', 1)
path.write_text(text, encoding="utf-8")
path = pathlib.Path(S + "Tables.java"); text = path.read_text(encoding="utf-8")
anchor = "        void data() {\n            underHeading = new boolean[size()];"
if text.count(anchor) != 1: raise SystemExit("Tables: plan 4c's data() moved")
text = text.replace(anchor, '''        /** Under a face that puts a heading's count under the last column, that column title's text start; else -1. */
        @Override int headingCountStart() {
            if (!context().faces().table.countUnderLastColumn() || header.getComponentCount() == 0) return -1;
            var last = header.getComponent(header.getComponentCount() - 1);
            return last.getX() + (last instanceof javax.swing.JComponent title ? title.getInsets().left : 0);
        }

''' + anchor, 1)
path.write_text(text, encoding="utf-8")
PY
```

The header's title labels and the rows share the table's horizontal origin (the header is the scroll pane's column header
view over the same viewport), so a title's x is the column's x in a row.

- [ ] **Step 4: Compose the groups as a table**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 2 step 4: NotificationGroupsEditor's classic composition, one grouped table (spec 12.2)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "NotificationGroupsEditor.java", [
    ('''    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit, boolean inlineReset) {
        this.kit=kit;
        this.inlineReset=inlineReset;''', '''    /** Spec 12.2's classic composition: one grouped table rather than a card per owner. */
    private final boolean table;
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit, boolean inlineReset) {this(choices,kit,inlineReset,false);}
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit, boolean inlineReset, boolean table) {
        this.kit=kit;
        this.inlineReset=inlineReset;
        this.table=table;''', 1),
    ('''        Map<String,String> value=value();
        var owners=new LinkedHashMap<String,List<Integer>>();''', '''        if(table)return table(id,plain);
        Map<String,String> value=value();
        var owners=new LinkedHashMap<String,List<Integer>>();''', 1),
    ('''    private static String modeIcon(String mode) {''', '''    /**
     * Spec 12.2 (CLASSIC): the description, then one table of Group, Display and Source; a heading row per owner with its
     * count; a group's Display is a borderless dropdown of the modes, its Source "Default" or "Custom", and its menu Reset
     * to Default (plan 9b Rulings 4 and 6).
     */
    private Nodes.Node table(String id, ControlState plain) {
        var columns = List.of(new dev.jasper.ui.model.Column("group", "Group", Nodes.TextRole.BODY, false, 10, Optional.empty()),
            new dev.jasper.ui.model.Column("display", "Display", Nodes.TextRole.BODY, false, 3, Optional.empty()),
            new dev.jasper.ui.model.Column("source", "Source", Nodes.TextRole.BODY, false, 2.2, Optional.empty()));
        Map<String, String> value = value();
        var owners = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i < groups.size(); i++) owners.computeIfAbsent(groups.get(i).ownerId(), ignored -> new ArrayList<>()).add(i);
        var rows = new ArrayList<dev.jasper.ui.model.TableRow>();
        int section = 0;
        for (var indices : owners.values()) {
            rows.add(dev.jasper.ui.model.TableRow.heading(id + ":owner-" + section++, groups.get(indices.getFirst()).owner(),
                java.util.OptionalInt.of(indices.size()), "group"));
            for (int i : indices) {
                var group = groups.get(i);
                var options = new ArrayList<Option>();
                for (var mode : MODES.entrySet()) options.add(new Option(mode.getKey(), mode.getValue(), Optional.empty()));
                var display = new Nodes.Choice(modeId(id, i), plain, Nodes.ChoiceKind.DROPDOWN, options, mode(group.id()),
                    group.name() + ": how its notifications appear", "");
                boolean custom = value.containsKey(group.id());
                var menu = List.of(new dev.jasper.ui.model.MenuItem(resetId(id, i), "Reset to Default", "", resetId(id, i), plain.enabled() && custom, false));
                rows.add(new dev.jasper.ui.model.TableRow(id + ":row-" + i, Map.of("group", group.name(), "source", custom ? "Custom" : "Default"),
                    Optional.empty(), false, Optional.of(menu), group.owner() + " · " + group.id(), Map.of("display", display), Optional.empty()));
            }
        }
        var description = new Nodes.Text(id + ":description", plain, "How each kind of notification appears. A plugin's groups are listed once it has run.",
            Nodes.TextRole.SUPPORTING);
        return new Nodes.Stack(id, plain, Nodes.LayoutRole.VERTICAL, List.of(description,
            new Nodes.Table(id + ":table", plain, columns, rows, java.util.Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE)));
    }

    private static String modeIcon(String mode) {''', 1),
    ('''    @Override public String revealTarget(String id) { return id + (groups.isEmpty() ? ":empty-row" : ":row-0"); }''',
     '''    @Override public String revealTarget(String id) { return id + (groups.isEmpty() ? ":empty-row" : table ? ":table" : ":row-0"); }''', 1)])
edit(S + "Editors.java", [
    ('''new NotificationGroupsEditor(choices, context.kit(), context.chrome().inlineReset()),''',
     '''new NotificationGroupsEditor(choices, context.kit(), context.chrome().inlineReset(),
            context.composition() == dev.jasper.app.presentation.Composition.CLASSIC),''', 1)])
PY
```

With no groups the editor keeps its "No notification groups yet." row in both compositions. A table row's menu reports
`resetId(id, i)`, which `event` already handles; the Display dropdown reports `Selected` on `modeId(id, i)` as the row
dropdown does. `NotificationGroupsEditor` is a composition root, but here `Editors` reads the composition and hands it
a boolean; both are roots. Add the app test's ledger row:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationsTableTest.java,app.settingsui,NotificationsTableTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationsTableTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 2; the grouped Notifications table")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Compare the page with its mock**

Append to `SettingsPagesMockTest` (before its final brace), and its probes to the static block:

```java
    @Test void notificationsMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("notifications"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
```

```java
        PROBES.put("notifications", List.of(new SettingsChromeMockTest.Probe("table box", 260, 232),
            new SettingsChromeMockTest.Probe("header band", 400, 262), new SettingsChromeMockTest.Probe("column separator", 755, 262),
            new SettingsChromeMockTest.Probe("heading band", 400, 284), new SettingsChromeMockTest.Probe("data row", 400, 306),
            new SettingsChromeMockTest.Probe("Display arrow", 999, 305)));
```

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.architecture.CompositionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicListHeadingsTest` (2), `NotificationsTableTest` (3), `SettingsPagesMockTest` (2),
`NotificationGroupsEditorTest` unchanged (the ROWS composition), `ClassicTablesTest` and `TableVocabularyTest`
unchanged. Fix a comparison failure as in Task 1 Step 4; the table's column weights (Rulings 4) and the cell insets are
the first suspects for magenta along its columns.

- [ ] **Step 6: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-ui/src/main/java/dev/jasper/ui/swing jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicListHeadingsTest.java \
  jasper-app/src/main/java/dev/jasper/app/settingsui jasper-app/src/test/java/dev/jasper/app/settingsui docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: Notifications as one grouped table

Under the classic composition the notification groups are one table of
Group, Display and Source: a heading row per owner with its count, a
borderless Display dropdown, Default or Custom, and Reset to Default in
each row's menu. The classic table face puts a heading's count under the
last column's title, as the mock does; the default face keeps it at the
row's end.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: `changed: none`.

### Task 3: The Keymap tree on Action, Scope and Shortcut lanes

Under `Composition.CLASSIC` the Keymap tree has Action, Scope and Shortcut columns, its tools are the text buttons Find
by Shortcut, Expand All and Collapse All, and its rows lie on the header's lanes (Rulings 7 to 9). `KeymapPage` maps
the composition to a `KeymapShape`; jasper-ui lays a keycap tree with columns on lanes.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapShape.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapPage.java:80-86`, `ShortcutPanel.java:60-216`, `ShortcutTree.java:19-118`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java` (post-3c and post-4c text), `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (four roles; classic Keymap values), the role tables, `NewRolesTest.java`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/KeycapLanesTest.java`, `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapLanesTest.java`
- Modify: `SettingsPagesMockTest.java`, `docs/design/theme-engine/migration.csv` (rows)

**Interfaces:**
- Consumes: plan 3c's `Nodes.Tree(…, columns)`, `Trees.ColumnHeaderLayout`, `Trees.TreeControl.header`; plan 4c's tree face.
- Produces: `public record KeymapShape(boolean columns, boolean textTools, boolean recorderField)` with `ROWS` and
  `CLASSIC`; `ShortcutPanel(KitContext, boolean, Function<…>, KeymapShape)`; `ShortcutTree(boolean macOs, boolean columns)`;
  `boolean Trees.TreeControl.laned()`, `int scopeLaneStart(int)`, `int shortcutLaneStart(int)`;
  `DesignMetrics.Role.KEYMAP_SCOPE_COLUMN(107)`, `KEYMAP_SHORTCUT_COLUMN(137)`, `KEYMAP_KEYS_INSET(25)`, `KEYMAP_SCOPE_GAP(30)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/KeycapLanesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.KeycapSequence;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 9b Rulings 7 and 8: a keycap tree with columns lies on its header's lanes (the Keymap mock's geometry). */
class KeycapLanesTest {
    static final List<Column> COLUMNS = List.of(new Column("action", "Action", Nodes.TextRole.BODY, false),
        new Column("scope", "Scope", Nodes.TextRole.BODY, false), new Column("shortcut", "Shortcut", Nodes.TextRole.BODY, false));

    static ViewSpec tree() {
        var copy = new TreeItem("action/copy", "Copy", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(),
            List.of("Global", "⌘C"), Optional.empty());
        var clipboard = new TreeItem("group/Clipboard", "Clipboard", Optional.empty(), false, List.of(copy), OptionalInt.empty(), true, "",
            Optional.empty(), List.of("", "1 shortcut"), Optional.empty());
        return new ViewSpec(new Nodes.Tree("shortcuts", ControlState.READY, List.of(clipboard), Set.of("group/Clipboard"), Set.of(), "", COLUMNS));
    }

    static KeycapSequence keys() { return KeycapTestSupport.sequence("⌘", "C"); }

    @Test void theRowsLieOnTheHeadersLanes() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, tree(), event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(keys()))));
                view.setSize(777, 300);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var tree = (Trees.TreeControl) view.control("shortcuts");
                assertThat(tree.laned()).isTrue();
                var group = tree.mounted.get("group/Clipboard"); var leaf = tree.mounted.get("action/copy");
                int width = leaf.getWidth();
                int scope = tree.scopeLaneStart(width), shortcut = tree.shortcutLaneStart(width);
                assertThat(width - shortcut).as("the Shortcut lane").isEqualTo(137);
                assertThat(shortcut - scope).as("the Scope lane").isEqualTo(107);
                var keysLabel = KitViewTest.find(leaf, "@tree/shortcuts/action/copy/keys", javax.swing.JLabel.class);
                var count = KitViewTest.find(group, "@tree/shortcuts/group/Clipboard/trailing/1", javax.swing.JLabel.class);
                var where = KitViewTest.find(leaf, "@tree/shortcuts/action/copy/context", javax.swing.JLabel.class);
                assertThat(SwingUtilities.convertPoint(keysLabel, 0, 0, leaf).x).as("keys 25 into the Shortcut lane").isEqualTo(shortcut + 25);
                assertThat(SwingUtilities.convertPoint(count, 0, 0, group).x).as("the section's count with the keys").isEqualTo(shortcut + 25);
                assertThat(SwingUtilities.convertPoint(where, where.getWidth(), 0, leaf).x).as("the scope ends 30 before Shortcut").isEqualTo(shortcut - 30);
                assertThat(where.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.RIGHT);
                var titles = tree.header.getComponents();
                assertThat(titles[1].getX()).as("Scope's title over its lane").isEqualTo(scope);
                assertThat(titles[2].getX()).as("Shortcut's title over its lane").isEqualTo(shortcut);
                var dot = KitViewTest.find(group, "@tree/shortcuts/group/Clipboard/modified", javax.swing.JComponent.class);
                assertThat(SwingUtilities.convertPoint(dot, 0, 0, group).x).as("the modified mark follows the title")
                    .isLessThan(scope).isGreaterThan(group.title.getX());
            } finally { release(context); }
        });
    }

    @Test void aKeycapTreeWithoutColumnsIsUnchanged() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = tree().root() instanceof Nodes.Tree t ? new ViewSpec(new Nodes.Tree(t.id(), t.state(), t.roots(), t.expandedIds(), t.selectedIds(), t.activateAction())) : null;
            try (var view = new KitView(context, spec, event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(keys()))));
                view.setSize(777, 300);
                InteractiveRowsTest.layout(view);
                assertThat(((Trees.TreeControl) view.control("shortcuts")).laned()).isFalse();
            } finally { release(context); }
        });
    }
}
```

`TreeRowPresentation(String context, boolean locked, Optional<KeycapSequence> keycaps)` is the record the Keymap hands
the tree (fact 5); `KeycapTestSupport.sequence` stands for however the jasper-ui tests build a `KeycapSequence` (read
`RecorderButtonTest` or `TreeDetailTest` and use its construction; if none exists, build the sequence with its public
constructor from one `Keycap` per glyph). The positions are plan 9b's "Mock measurements".

Create `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapLanesTest.java`:

```java
package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Nodes;
import java.util.List;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the Keymap's columns, its text tools and its status line. */
class KeymapLanesTest {
    static List<ShortcutEntry> rows() {
        return ShortcutTestSupport.rows();
    }

    @Test void theClassicTreeHasColumnsAndSectionsCountInTheirShortcutLane() {
        var tree = new ShortcutTree(true, true);
        tree.showRows(rows(), false);
        var descriptor = tree.descriptor();
        assertThat(descriptor.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Action", "Scope", "Shortcut");
        var first = descriptor.roots().getFirst();
        assertThat(first.count()).isEmpty();
        assertThat(first.trailing()).hasSize(2);
        assertThat(first.trailing().get(1)).matches("\\d+ shortcuts?");
        var plain = new ShortcutTree(true, false);
        plain.showRows(rows(), false);
        assertThat(plain.descriptor().columns()).isEmpty();
        assertThat(plain.descriptor().roots().getFirst().count()).isPresent();
    }

    @Test void theLanesHoldWhileSearchingAndRecording() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true, edits -> java.util.concurrent.CompletableFuture.completedFuture(null),
                KeymapShape.CLASSIC);
            try {
                panel.setRows(rows());
                var tools = (Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root();
                assertThat(tools.children()).extracting(Nodes.Node::id).containsExactly("clear", "record", "expand", "collapse");
                assertThat(((Nodes.Button) tools.children().get(1)).label()).isEqualTo("Find by Shortcut");
                assertThat(((Nodes.Button) tools.children().get(2)).label()).isEqualTo("Expand All");
                assertThat(((Nodes.Button) tools.children().get(3)).label()).isEqualTo("Collapse All");
                panel.setQuery("copy");
                assertThat(panel.tree.descriptor().columns()).as("searching").hasSize(3);
                ShortcutTestSupport.action(panel, "record");
                assertThat(((Nodes.Button) ((Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root()).children().get(1)).label()).isEqualTo("Stop");
                assertThat(panel.tree.descriptor().columns()).as("recording").hasSize(3);
                assertThat(ShortcutTestSupport.statusSpec(panel).root().toString()).doesNotContain("status.lock");
            } finally { panel.close(); }
        });
    }
}
```

`ShortcutTestSupport` (an existing test helper in the package) gains three small accessors in this step if it lacks
them: `rows()` (the catalog the existing tests use, `ShortcutCatalog.rows(KeyBindings.defaults(true), new
Contributions().actions(), Map.of(), Map.of(), true)` or the fixture it already holds), `toolsSpec(ShortcutPanel)` and
`statusSpec(ShortcutPanel)` (calling the panel's package-private `toolsSpec()` and `statusSpec()`), and
`action(ShortcutPanel, String)` (sending `new UiEvent.Action(id)` to the panel's event handler, made package-private for
it). Add them by reading `ShortcutTestSupport` first and keeping its style.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.KeycapLanesTest' :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.KeymapLanesTest' --continue`
Expected: FAIL at compile: "cannot find symbol: method laned()", "class KeymapShape", "constructor ShortcutTree … (boolean, boolean)".

- [ ] **Step 3: Lay a keycap tree with columns on lanes**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 3 step 3: a keycap tree with columns lies on its header's lanes (Rulings 7 and 8)."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/main/java/dev/jasper/ui/"
edit(U + "swing/Trees.java", [
    ('''        boolean layoutPresented(InteractiveRows.Row row) {
            var value=presentation.get(itemId(row.index));if(value==null)return false;''',
     '''        /** A keycap tree with columns (the classic Keymap) lays every row on its header's lanes (plan 9b Rulings 7). */
        boolean laned(){return !presentation.isEmpty() && !value().columns().isEmpty();}
        private int laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role role){return ThemePainting.metric(context(),role);}
        /** The Scope lane's start for a row or header {@code width} wide: the Shortcut and Scope lanes back from its end. */
        int scopeLaneStart(int width){return Math.max(0,width-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SHORTCUT_COLUMN)-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SCOPE_COLUMN));}
        /** The Shortcut lane's start for a row or header {@code width} wide. */
        int shortcutLaneStart(int width){return Math.max(0,width-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SHORTCUT_COLUMN));}
        private static java.awt.Component named(java.awt.Component[] children,String... names){
            for(var name:names)for(var child:children)if(name.equals(child.getName()))return child;
            return null;
        }
        /** Title in the Action lane, scope right-aligned before the Shortcut lane, keys or a section's count in it. */
        private boolean layoutLanes(InteractiveRows.Row row){
            int width=row.getWidth(),height=row.getHeight();
            int pad=laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_GROUP_SIDE),start=Math.min(width,pad+row.indent);
            int lw=row.leading.getComponentCount()==0?0:row.leading.getPreferredSize().width;
            row.leading.setBounds(start,0,lw,height);row.leading.doLayout();
            int titleX=start+lw+(lw>0?laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_GROUP_TEXT_GAP):0);
            int scope=scopeLaneStart(width),shortcut=shortcutLaneStart(width);
            row.title.setBounds(titleX,0,Math.max(0,scope-titleX),height);row.title.doLayout();
            row.subtitle.setBounds(0,0,0,0);
            row.trailing.setBounds(0,0,width,height);
            var children=row.trailing.getComponents();
            for(var child:children)child.setBounds(0,0,0,0);
            String base="@tree/"+node.id()+"/"+itemId(row.index)+"/";
            var keys=named(children,base+"keys",base+"trailing/1");
            var where=named(children,base+"context",base+"trailing/0");
            var mark=named(children,base+"modified");
            int keysX=shortcut+laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_KEYS_INSET);
            if(keys!=null){keys.setBounds(keysX,0,Math.max(0,width-keysX),height);if(keys instanceof javax.swing.JLabel label)label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);}
            if(where!=null){where.setBounds(scope,0,Math.max(0,shortcut-laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.KEYMAP_SCOPE_GAP)-scope),height);
                if(where instanceof javax.swing.JLabel label)label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);}
            if(mark!=null){var size=mark.getPreferredSize();int textEnd=titleX+Math.min(row.title.getWidth(),row.title.getPreferredSize().width);
                mark.setBounds(Math.min(scope-size.width,textEnd+laneMetric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP)),(height-size.height)/2,size.width,size.height);}
            return true;
        }
        boolean layoutPresented(InteractiveRows.Row row) {
            if(laned())return layoutLanes(row);
            var value=presentation.get(itemId(row.index));if(value==null)return false;''', 1),
    ('''            if(presented!=null)row.trailing.setLayout(null);''', '''            if(presented!=null || laned())row.trailing.setLayout(null);''', 1),
    ('''            if(presented!=null) {
                if(presented.locked())trailing.add(''', '''            if(presented!=null) {
                // On lanes a read-only key shows no lock glyph (plan 9b Rulings 8); its row's tooltip still says it.
                if(presented.locked() && value().columns().isEmpty())trailing.add(''', 1),
    ('''            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);''',
     '''            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            if (owner.laned()) {
                // Plan 9b Rulings 7: the titles span the rows' lanes, measured from the header's end as the rows measure theirs.
                int w = parent.getWidth(), scope = owner.scopeLaneStart(w), shortcut = owner.shortcutLaneStart(w);
                int[][] lanes = {{insets.left, scope - insets.left}, {scope, shortcut - scope}, {shortcut, w - shortcut}};
                for (int i = 0; i < children.length && i < lanes.length; i++) {
                    children[i].setBounds(lanes[i][0], insets.top, Math.max(0, lanes[i][1]), height);
                    if (children[i] instanceof javax.swing.JLabel label) label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                }
                return;
            }''', 1)])
M = U + "metrics/DesignMetrics.java"
path = pathlib.Path(M); text = path.read_text(encoding="utf-8")
end = "SETTINGS_NAV_CHILD_INDENT(0), SETTINGS_NAV_TEXT_GAP(6);"
if text.count(end) != 1: raise SystemExit("DesignMetrics: plan 9a's roles are not last")
text = text.replace(end, '''SETTINGS_NAV_CHILD_INDENT(0), SETTINGS_NAV_TEXT_GAP(6),
        // Plan 9b: the lanes of a keycap tree with columns (the classic Keymap mock), read only there; equal in both catalogs.
        KEYMAP_SCOPE_COLUMN(107), KEYMAP_SHORTCUT_COLUMN(137), KEYMAP_KEYS_INSET(25), KEYMAP_SCOPE_GAP(30);''', 1)
anchor = "        values.put(Role.SETTINGS_NAV_CHILD_INDENT, 15);\n"
if text.count(anchor) != 1: raise SystemExit("DesignMetrics: plan 9a's classic values moved")
KEYMAP = [("KEYMAP_PAGE_TOP_PADDING", 18), ("KEYMAP_PAGE_PADDING", 16), ("KEYMAP_SECTION_GAP", 6), ("KEYCAP_GROUP_SIDE", 8),
    ("KEYCAP_GROUP_TEXT_GAP", 7), ("KEYCAP_TREE_LEAF_INDENT", 26), ("KEYCAP_DETAIL_INSET", 4)]
text = text.replace(anchor, anchor + "        // Plan 9b: the classic Keymap (Mock measurements).\n" + "".join(f"        values.put(Role.{n}, {v});\n" for n, v in KEYMAP), 1)
path.write_text(text, encoding="utf-8")
T = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics")
added = "KEYMAP_SCOPE_COLUMN\t107\nKEYMAP_SHORTCUT_COLUMN\t137\nKEYMAP_KEYS_INSET\t25\nKEYMAP_SCOPE_GAP\t30\n"
compact = T / "compact-roles.tsv"; compact.write_text(compact.read_text(encoding="utf-8") + added, encoding="utf-8")
classic = T / "classic-roles.tsv"; rows = (classic.read_text(encoding="utf-8") + added).splitlines()
values = dict(KEYMAP); seen = set()
for i, row in enumerate(rows):
    name = row.split("\t")[0]
    if name in values: rows[i] = f"{name}\t{values[name]}"; seen.add(name)
if seen != set(values): raise SystemExit(f"classic-roles.tsv: missing {sorted(set(values) - seen)}")
classic.write_text("\n".join(rows) + "\n", encoding="utf-8")
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java"); text = path.read_text(encoding="utf-8")
old = '"SETTINGS_NAV_CHILD_INDENT", "SETTINGS_NAV_TEXT_GAP"'
if text.count(old) != 1: raise SystemExit("NewRolesTest: plan 9a's roles are missing")
path.write_text(text.replace(old, old + ', "KEYMAP_SCOPE_COLUMN", "KEYMAP_SHORTCUT_COLUMN", "KEYMAP_KEYS_INSET", "KEYMAP_SCOPE_GAP"', 1), encoding="utf-8")
PY
```

The `ColumnHeaderLayout` anchor is plan 3c's line (`int height = Math.max(0, parent.getHeight() - insets.top -
insets.bottom);`); `layoutPresented` is called from `InteractiveRows.Row.doLayout` for every row of a tree (fact 8), so a
laned tree's section rows take the lanes as well. A keycap tree without columns (the default Keymap) never reaches the
new code.

- [ ] **Step 4: Compose the classic Keymap**

Create `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapShape.java`:

```java
package dev.jasper.app.shortcuthelp;

/**
 * How the Keymap page is composed (spec 12.2), as {@link KeymapPage} maps the look's composition once.
 *
 * @param columns the tree has Action, Scope and Shortcut columns, and a section shows "N shortcuts" in its Shortcut lane
 * @param textTools the tools are Find by Shortcut, Expand All and Collapse All, the search has no Record glyph and the
 *     status line no lock glyph
 * @param recorderField the edit band draws its recorder as a field and its hint line has no Record action (Task 4)
 */
public record KeymapShape(boolean columns, boolean textTools, boolean recorderField) {
    /** Today's Keymap. */
    public static final KeymapShape ROWS = new KeymapShape(false, false, false);
    /** The classic Keymap of the Settings mocks. */
    public static final KeymapShape CLASSIC = new KeymapShape(true, true, true);
}
```

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 3 step 4: KeymapPage maps the composition to a KeymapShape; the panel and tree follow it."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
H = "jasper-app/src/main/java/dev/jasper/app/shortcuthelp/"
edit(H + "KeymapPage.java", [
    ('''        panel = new ShortcutPanel(context.kit().get(), hooks.macOs(), this::stage);''',
     '''        panel = new ShortcutPanel(context.kit().get(), hooks.macOs(), this::stage, shape(context));''', 1),
    ('''    private void refreshRows() { if (panel != null) panel.setRows(rows()); }''',
     '''    private void refreshRows() { if (panel != null) panel.setRows(rows()); }

    /** The composition root's one reading of the look's composition (spec 9.1, 12.2). */
    private static KeymapShape shape(PageContext context) {
        return context.composition() == dev.jasper.app.presentation.Composition.CLASSIC ? KeymapShape.CLASSIC : KeymapShape.ROWS;
    }''', 1)])
edit(H + "ShortcutTree.java", [
    ('''    private final boolean macOs;
    private final Set<String> expanded = new HashSet<>();''', '''    private final boolean macOs;
    private final boolean columns;
    private final Set<String> expanded = new HashSet<>();''', 1),
    ('''    ShortcutTree(boolean macOs) { this.macOs = macOs; }''', '''    ShortcutTree(boolean macOs) { this(macOs, false); }

    /** {@code columns}: Action, Scope and Shortcut columns, a section's count in its Shortcut lane (plan 9b Rulings 8). */
    ShortcutTree(boolean macOs, boolean columns) { this.macOs = macOs; this.columns = columns; }

    static final java.util.List<dev.jasper.ui.model.Column> COLUMNS = java.util.List.of(
        new dev.jasper.ui.model.Column("action", "Action", Nodes.TextRole.BODY, false),
        new dev.jasper.ui.model.Column("scope", "Scope", Nodes.TextRole.BODY, false),
        new dev.jasper.ui.model.Column("shortcut", "Shortcut", Nodes.TextRole.BODY, false));''', 1),
    ('''            roots.add(new TreeItem(groupId(group), group, Optional.of(rows.stream().map(ShortcutEntry::groupIcon).filter(icon->!icon.equals("plugin")).findFirst().orElse("plugin")), false, children, OptionalInt.of(rows.size()),
                customized, shown ? "Collapse section" : "Expand section", Optional.empty(), List.of(), Optional.empty()));''',
     '''            var icon = Optional.of(rows.stream().map(ShortcutEntry::groupIcon).filter(name->!name.equals("plugin")).findFirst().orElse("plugin"));
            if (columns) roots.add(new TreeItem(groupId(group), group, Optional.empty(), false, children, OptionalInt.empty(), customized,
                shown ? "Collapse section" : "Expand section", Optional.empty(), List.of("", rows.size() + (rows.size() == 1 ? " shortcut" : " shortcuts")), Optional.empty()));
            else roots.add(new TreeItem(groupId(group), group, icon, false, children, OptionalInt.of(rows.size()),
                customized, shown ? "Collapse section" : "Expand section", Optional.empty(), List.of(), Optional.empty()));''', 1),
    ('''        return new Nodes.Tree(ID, ControlState.READY, roots, open, selected == null ? Set.of() : Set.of(selected), "");''',
     '''        return new Nodes.Tree(ID, ControlState.READY, roots, open, selected == null ? Set.of() : Set.of(selected), "", columns ? COLUMNS : List.of());''', 1)])
edit(H + "ShortcutPanel.java", [
    ('''    private final Set<Integer> held = new HashSet<>();''', '''    private final Set<Integer> held = new HashSet<>();
    private final KeymapShape shape;''', 1),
    ('''    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) {
        super(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_SECTION_GAP)));
        setOpaque(false);
        tree = new ShortcutTree(macOs);''', '''    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer) {
        this(context, macOs, writer, KeymapShape.ROWS);
    }

    /** The page body in {@code shape}, the composition its page chose (spec 12.2). */
    ShortcutPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer, KeymapShape shape) {
        super(new BorderLayout(0, scaled(context, DesignMetrics.Role.KEYMAP_SECTION_GAP)));
        setOpaque(false);
        this.shape = shape;
        tree = new ShortcutTree(macOs, shape.columns());''', 1),
    ('''        top.setFieldTrailing(SEARCH, Optional.of(new Nodes.Button("record", ControlState.READY, recording ? "Stop recording" : "Record", "keyboard", Nodes.ButtonKind.GHOST, "record", "Record shortcut", Nodes.ButtonPresentation.EMBEDDED)));''',
     '''        top.setFieldTrailing(SEARCH, shape.textTools() ? Optional.empty()
            : Optional.of(new Nodes.Button("record", ControlState.READY, recording ? "Stop recording" : "Record", "keyboard", Nodes.ButtonKind.GHOST, "record", "Record shortcut", Nodes.ButtonPresentation.EMBEDDED)));''', 1),
    ('''    private ViewSpec toolsSpec() {
        return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(''',
     '''    ViewSpec toolsSpec() {
        var clear = new Nodes.Button("clear", new ControlState(captured != null || !query.isEmpty(), true, false, ""), "Clear", "", Nodes.ButtonKind.SECONDARY, "clear", "");
        // Spec 12.2: the classic tools are text buttons; Find by Shortcut is the recording search.
        if (shape.textTools()) return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(clear,
            new Nodes.Button("record", ControlState.READY, recording ? "Stop" : "Find by Shortcut", "", Nodes.ButtonKind.SECONDARY, "record", "Find an action by its shortcut"),
            new Nodes.Button("expand", ControlState.READY, "Expand All", "", Nodes.ButtonKind.SECONDARY, "expand", ""),
            new Nodes.Button("collapse", ControlState.READY, "Collapse All", "", Nodes.ButtonKind.SECONDARY, "collapse", ""))));
        return new ViewSpec(new Nodes.Stack("sections", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(''', 1),
    ('''    private ViewSpec statusSpec() {''', '''    ViewSpec statusSpec() {''', 1),
    ('''            new Nodes.Stack("status.contextual-line", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                new Nodes.Icon("status.lock", ControlState.READY, "lock", Nodes.Hue.NEUTRAL, Nodes.IconSize.CAPTION, "Read-only contextual key"),
                new Nodes.Text("status.contextual", ControlState.READY, "Contextual keys are read-only", Nodes.TextRole.CAPTION))))));''',
     '''            shape.textTools() ? new Nodes.Text("status.contextual", ControlState.READY, "Contextual keys are read-only", Nodes.TextRole.CAPTION)
                : new Nodes.Stack("status.contextual-line", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                    new Nodes.Icon("status.lock", ControlState.READY, "lock", Nodes.Hue.NEUTRAL, Nodes.IconSize.CAPTION, "Read-only contextual key"),
                    new Nodes.Text("status.contextual", ControlState.READY, "Contextual keys are read-only", Nodes.TextRole.CAPTION))))));''', 1)])
PY
```

The default `toolsSpec` keeps its own `Clear` construction after the new early return; the one `clear` local above it is
unused there, which javac accepts. Add the two tests' ledger rows (both import `dev.jasper.ui…`):

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapLanesTest.java,app.shortcuthelp,KeymapLanesTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapLanesTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 3; the classic Keymap columns and tools",
    "jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapShape.java,app.shortcuthelp,KeymapShape,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapLanesTest.java,New in Enterprise plan 9b Task 3; the Keymap composition as values"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

(Add `KeymapShape`'s row only if `ThemeMigrationCoverageTest` names it.)

- [ ] **Step 5: Compare the page with its mock and calibrate**

The fixture's Keymap needs the mock's sections; give `EnterpriseSettings` the application's own actions: in
`EnterpriseSettings.open`, after `runtime[0].start(Map.of(), dark);`, register the actions the mock lists through the
same `contributions` object (`contributions.addAction(id, name, null, keywords, Optional.empty(), invocation -> { })`
for Copy and Paste under Clipboard, and one action per section the mock shows, with ids from `KeyBindings`' defaults so
they carry their default shortcuts). Then append to `SettingsPagesMockTest`:

```java
    @Test void keymapMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            SwingUtilities.invokeAndWait(() -> {
                settings.dialog().select("keymap");
                var tree = (dev.jasper.app.shortcuthelp.KeymapPage) settings.dialog().pages().stream().filter(page -> page.id().equals("keymap")).findFirst().orElseThrow();
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.expand(tree, "Clipboard");
            });
            assertThat(compare(settings, List.of("keymap"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
```

and its probes:

```java
        PROBES.put("keymap", List.of(new SettingsChromeMockTest.Probe("search field edge", 300, 54),
            new SettingsChromeMockTest.Probe("Find by Shortcut edge", 739, 52), new SettingsChromeMockTest.Probe("tree box", 600, 83),
            new SettingsChromeMockTest.Probe("header band", 600, 94), new SettingsChromeMockTest.Probe("Scope separator", 780, 94),
            new SettingsChromeMockTest.Probe("Shortcut separator", 887, 94)));
```

`ShortcutPreviewSupport.expand(KeymapPage, String section)` opens one section, as the Keymap mock shows Clipboard open;
add it to that existing test helper if it lacks it (it calls the panel's `tree.expanded(ShortcutTree.groupId(section),
true)` and re-renders). Run:

`./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.metrics.*' :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --tests 'dev.jasper.app.architecture.CompositionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`

Expected: PASS: `KeycapLanesTest` (2), `KeymapLanesTest` (2), `SettingsPagesMockTest` (3), the existing Keymap tests
(`KeymapPageTest`, `ShortcutEditingTest`, `ShortcutHelpTest`, `ShortcutSectionsTest`) unchanged under ROWS. Calibrate by
the measurements: a section's ▶ at canvas 8.5 and its title at 25.5 (`KEYCAP_GROUP_SIDE`, `KEYCAP_GROUP_TEXT_GAP`), a
leaf title at 34.5 (`KEYCAP_TREE_LEAF_INDENT`), leaf rows 24 tall with 16 px keycaps (`KEYCAP_DETAIL_INSET`), the search
19 below the header rule and the tree 48 below it (`KEYMAP_PAGE_TOP_PADDING`, `KEYMAP_SECTION_GAP`); each value changes in
both tables. Fix a remaining comparison failure as in Task 1 Step 4.

- [ ] **Step 6: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-ui/src/main/java/dev/jasper/ui jasper-ui/src/test/resources/dev/jasper/ui/metrics jasper-ui/src/test/java/dev/jasper/ui \
  jasper-app/src/main/java/dev/jasper/app/shortcuthelp jasper-app/src/test/java/dev/jasper/app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: the Keymap tree on Action, Scope and Shortcut lanes

Under the classic composition the Keymap tree has Action, Scope and
Shortcut columns, a section reads N shortcuts in its Shortcut lane, and
the tools are Find by Shortcut, Expand All and Collapse All. jasper-ui
lays a keycap tree with columns on its header's lanes: the scope
right-aligned before the Shortcut lane, keys and counts 25 into it, as the
Keymap mock measures; a keycap tree without columns is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: `changed: none`.

### Task 4: The Keymap's inline recording band

Editing a row under Enterprise opens a band under it (spec 12.2): the edited row stays selected, the band fills the
tree's width on `surface.rowHover` between faint accent edges, the recorder is a classic field with the armed dot and
"Press a key combination…", Cancel and Set stand to its right, and the hint line below holds the message and the ⋯ of
shortcut actions (Rulings 10 and 11).

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/TreeFace.java`, `DefaultTreeFace.java`, `ClassicTreeFace.java` (plan 4c's files), `InteractiveRows.java:739-753,818-821`, `ClassicButtonFace.java` (plan 4b's file)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (one role; classic band values), the role tables, `NewRolesTest.java`, `ClassicCatalogTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutEditPanel.java:41-47,159-179`, `ShortcutPanel.java:64`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/RecordingBandTest.java`, `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapBandTest.java`
- Modify: `SettingsPagesMockTest.java`, `SettingsChromeMockTest.PAGES` gains no page (07b is compared here), `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 3's `KeymapShape.recorderField()`; plan 4c's `TreeFace`; plan 4b's `ClassicButtonFace`.
- Produces: `void TreeFace.paintDetail(Trees.TreeControl tree, Graphics2D g, int index, int width)` and `boolean
  TreeFace.fillsDetailRow()`; `DesignMetrics.Role.KEYCAP_DETAIL_EDGE_ALPHA(0)`; `ShortcutEditPanel(KitContext, boolean,
  Function<…>, Runnable, boolean recorderField)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/RecordingBandTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 12.2 and plan 9b Rulings 10: the classic recording band and the recorder drawn as a field. */
class RecordingBandTest {
    static ViewSpec band() {
        return new ViewSpec(new Nodes.Stack("editor", ControlState.READY, Nodes.LayoutRole.VERTICAL, java.util.List.of(
            new Nodes.Button("editor.value", ControlState.READY, "Press a key combination…", "", Nodes.ButtonKind.SECONDARY, "editor.value", ""))));
    }

    static BufferedImage paint(KitView view) {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicBandFillsTheWidthUnderTheSelectedRowAndTheRecorderIsAField() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, KeycapLanesTest.tree(), event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(KeycapLanesTest.keys()))));
                var spec = (Nodes.Tree) KeycapLanesTest.tree().root();
                view.update(new ViewSpec(new Nodes.Tree(spec.id(), spec.state(), spec.roots(), spec.expandedIds(), Set.of("action/copy"), "", spec.columns())));
                view.setSize(777, 300);
                var handle = view.attachTreeDetail("shortcuts", "action/copy", band(), event -> { }, () -> { });
                view.setButtonValuePresentation("editor.value", ButtonValuePresentation.RECORDER_ARMED);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var tree = (Trees.TreeControl) view.control("shortcuts");
                var row = tree.mounted.get("action/copy");
                var image = paint(view);
                int x = 400, rowY = tree.canvas.getY() + row.getY(), bandTop = rowY + row.getHeight();
                var theme = context.theme();
                assertThat(image.getRGB(x, rowY + row.getHeight() / 2) & 0xFFFFFF).as("the edited row stays selected")
                    .isEqualTo(SemanticColor.SELECTION_BG.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(2, bandTop + 3) & 0xFFFFFF).as("the band from the tree's left edge")
                    .isEqualTo(DerivedColor.SURFACE_ROW_HOVER.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(x, bandTop) & 0xFFFFFF).as("its edge is not its fill")
                    .isNotEqualTo(DerivedColor.SURFACE_ROW_HOVER.color(theme).getRGB() & 0xFFFFFF);
                var value = KitViewTest.find(view, "editor.value", javax.swing.JComponent.class);
                var p = javax.swing.SwingUtilities.convertPoint(value, value.getWidth() / 2, value.getHeight() / 2, view);
                assertThat(image.getRGB(p.x, p.y) & 0xFFFFFF).as("the recorder is a field").isEqualTo(SemanticColor.SURFACE_INPUT.color(theme).getRGB() & 0xFFFFFF);
                handle.close();
            } finally { release(context); }
        });
    }

    @Test void theDefaultBandIsTodaysCard() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                assertThat(context.faces().tree.fillsDetailRow()).isFalse();
                assertThat(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_EDGE_ALPHA)).isZero();
            } finally { release(context); }
        });
    }
}
```

`KitView.attachTreeDetail`, `setButtonValuePresentation` and `TreeDetailHandle.close` are the app-facing calls the Keymap
uses (fact 7; `ShortcutEditPanel.java:57-62`, `:143-147`). Create
`jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapBandTest.java`:

```java
package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Nodes;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the band's recorder, Cancel and Set, and its hint line with the ⋯ and no Record. */
class KeymapBandTest {
    @Test void theClassicBandsHintLineHasTheEllipsisAndNoRecord() throws Exception {
        edt(() -> {
            var editor = new ShortcutEditPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), () -> { }, true);
            editor.begin(KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow());
            var note = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(editor.specForTests().root(), "editor.note");
            assertThat(note.children()).extracting(Nodes.Node::id).containsExactly("editor.message", "editor.options");
            var plain = new ShortcutEditPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), () -> { }, false);
            plain.begin(KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow());
            var plainNote = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(plain.specForTests().root(), "editor.note");
            assertThat(plainNote.children()).extracting(Nodes.Node::id).contains("editor.record");
        });
    }

    @Test void collapsingTheSectionClosesTheBand() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), KeymapShape.CLASSIC);
            try {
                panel.setRows(KeymapLanesTest.rows());
                var entry = KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow();
                panel.tree.expanded(ShortcutTree.groupId(entry.group()), true);
                ShortcutTestSupport.activate(panel, ShortcutTree.actionId(entry));
                assertThat(panel.editor.visibleEdit()).isTrue();
                panel.tree.expanded(ShortcutTree.groupId(entry.group()), false);
                ShortcutTestSupport.render(panel);
                assertThat(panel.editor.visibleEdit()).as("the edited row left the tree").isFalse();
            } finally { panel.close(); }
        });
    }
}
```

The test needs three small accessors: `ShortcutEditPanel.specForTests()` (package-private, returning `spec()`),
`ShortcutTestSupport.activate(panel, id)` and `render(panel)` (calling the panel's `activate` and `render`, made
package-private), and `dev.jasper.app.settingsui.ResetMenuTestAccess.find(Node, String)` (a public test bridge in the
settingsui test package that returns `ResetMenuTest.find`); add them in this step.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.RecordingBandTest' :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.KeymapBandTest' --continue`
Expected: FAIL at compile: "cannot find symbol: method fillsDetailRow()", "variable KEYCAP_DETAIL_EDGE_ALPHA",
"constructor ShortcutEditPanel … (… boolean)".

- [ ] **Step 3: Give the tree face its band and the classic button face its recorder field**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 4 step 3: the tree face paints a keycap detail; the classic face's band and recorder field (Rulings 10)."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the closing brace last")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
before_last_brace(S + "TreeFace.java", '''    /** Paints the keycap detail of row {@code index} across {@code width}: today's card, or the classic band below the row. */
    void paintDetail(Trees.TreeControl tree, java.awt.Graphics2D g, int index, int width);
    /** Whether the edited row keeps its selection fill above its detail (the classic band); today's card covers it. */
    boolean fillsDetailRow();
''')
before_last_brace(S + "DefaultTreeFace.java", '''    @Override public void paintDetail(Trees.TreeControl tree, java.awt.Graphics2D g, int index, int width) { tree.paintDetailCard(g, index, width); }
    @Override public boolean fillsDetailRow() { return false; }
''')
before_last_brace(S + "ClassicTreeFace.java", '''    /**
     * Plan 9b Rulings 10: the band below the edited row, across the tree, on {@code surface.rowHover}, with 1 px edges of
     * {@code border.focus} at the catalog's edge alpha over it.
     */
    @Override public void paintDetail(Trees.TreeControl tree, java.awt.Graphics2D g, int index, int width) {
        var context = tree.context();
        int top = tree.top(index) + tree.rowShellHeight(index), height = Math.max(0, tree.heightAt(index) - tree.rowShellHeight(index) - tree.detailAfter());
        g.setColor(ThemePainting.role(context, dev.jasper.ui.theme.DerivedColor.SURFACE_ROW_HOVER));
        g.fillRect(0, top, width, height);
        var focus = ThemePainting.color(context, dev.jasper.ui.theme.SemanticColor.BORDER_FOCUS);
        int alpha = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_EDGE_ALPHA);
        g.setColor(new java.awt.Color(focus.getRed(), focus.getGreen(), focus.getBlue(), Math.max(0, Math.min(255, alpha))));
        int line = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE);
        g.fillRect(0, top, width, line);
        g.fillRect(0, top + height - line, width, line);
    }
    @Override public boolean fillsDetailRow() { return true; }
''')
edit(S + "InteractiveRows.java", [
    ('''            if(owner instanceof Trees.TreeControl tree && tree.detail!=null) {
                int index=tree.indexOf(tree.detail.itemId);
                if(index>=0) {
                    var g=(Graphics2D)graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        int inset=ThemePainting.metric(owner.context(),DesignMetrics.Role.KEYCAP_DETAIL_INSET),radius=ThemePainting.metric(owner.context(),DesignMetrics.Role.CONTROL_RADIUS)*2;
                        g.setColor(ThemePainting.color(owner.context(),SemanticColor.SURFACE_CONTROL));
                        int card=Math.max(0,tree.heightAt(index)-tree.detailAfter());
                        g.fillRoundRect(inset,tree.top(index),Math.max(0,getWidth()-2*inset),card,radius,radius);
                        g.setColor(ThemePainting.color(owner.context(),SemanticColor.BORDER_FOCUS));
                        g.drawRoundRect(inset,tree.top(index),Math.max(0,getWidth()-2*inset-1),Math.max(0,card-1),radius,radius);
                    }finally{g.dispose();}
                }
            }''', '''            if(owner instanceof Trees.TreeControl tree && tree.detail!=null) {
                int index=tree.indexOf(tree.detail.itemId);
                if(index>=0) {
                    var g=(Graphics2D)graphics.create();
                    try { tree.context().faces().tree.paintDetail(tree, g, index, getWidth()); } finally { g.dispose(); }
                }
            }''', 1),
    ('''            if (owner instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id)) return;''',
     '''            if (owner instanceof Trees.TreeControl tree && tree.detail != null && tree.detail.itemId.equals(id) && !tree.context().faces().tree.fillsDetailRow()) return;''', 1)])
edit(S + "Trees.java", [
    ('''        void detailChanged(){measureRows();canvas.revalidate();sync();canvas.repaint();}''',
     '''        void detailChanged(){measureRows();canvas.revalidate();sync();canvas.repaint();}
        /** Today's keycap detail: a rounded card from the row's top, in the control surface, edged in the focus colour. */
        void paintDetailCard(java.awt.Graphics2D g, int index, int width) {
            ThemePainting.antialias(g);
            int inset=ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.KEYCAP_DETAIL_INSET),radius=ThemePainting.metric(context(),dev.jasper.ui.metrics.DesignMetrics.Role.CONTROL_RADIUS)*2;
            g.setColor(ThemePainting.color(context(),dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL));
            int card=Math.max(0,heightAt(index)-detailAfter());
            g.fillRoundRect(inset,top(index),Math.max(0,width-2*inset),card,radius,radius);
            g.setColor(ThemePainting.color(context(),dev.jasper.ui.theme.SemanticColor.BORDER_FOCUS));
            g.drawRoundRect(inset,top(index),Math.max(0,width-2*inset-1),Math.max(0,card-1),radius,radius);
        }''', 1)])
# The classic button face draws a recorder as a classic field (plan 4b's line, fact 9).
path = pathlib.Path(S + "ClassicButtonFace.java"); text = path.read_text(encoding="utf-8")
old = "        if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK) { b.paintRaised(g, hover, focused); return; }"
if text.count(old) != 1: raise SystemExit("ClassicButtonFace: plan 4b's delegation line moved")
text = text.replace(old, '''        if (b.recorder()) { recorder(b, g, focused); return; }
        if (b.embedded || b.circular || b.kind == Nodes.ButtonKind.LINK) { b.paintRaised(g, hover, focused); return; }''', 1)
old = "        if (b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK) return ThemePainting.color(c, b.textRole(hover));"
if text.count(old) != 1: raise SystemExit("ClassicButtonFace: plan 4b's text line moved")
text = text.replace(old, '''        // An armed recorder waiting for its keys shows its prompt as a field's placeholder (plan 9b Rulings 10).
        if (b.recorder() && b.armed() && !(b.getIcon() instanceof Keycaps)) return ThemePainting.color(c, dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER);
''' + old, 1)
text = text.rstrip()
if not text.endswith("}"): raise SystemExit("ClassicButtonFace: expected the closing brace last")
text = text[:-1].rstrip() + '''

    /** A recorder value drawn as a classic field: the input surface, its edge, and the focus ring while focused or armed. */
    private static void recorder(Buttons.KitButton b, Graphics2D g, boolean focused) {
        var c = b.context();
        boolean ring = focused || b.armed();
        ThemePainting.rounded(g, b, c, ThemePainting.color(c, dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT),
            ThemePainting.color(c, ring ? dev.jasper.ui.theme.SemanticColor.BORDER_FOCUS : dev.jasper.ui.theme.SemanticColor.BORDER_INPUT),
            ring, false, dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_RADIUS);
    }
}
'''
path.write_text(text + "\n" if not text.endswith("\n") else text, encoding="utf-8")
M = "jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"
path = pathlib.Path(M); text = path.read_text(encoding="utf-8")
end = "KEYMAP_KEYS_INSET(25), KEYMAP_SCOPE_GAP(30);"
if text.count(end) != 1: raise SystemExit("DesignMetrics: Task 3's roles are not last")
text = text.replace(end, '''KEYMAP_KEYS_INSET(25), KEYMAP_SCOPE_GAP(30),
        // Plan 9b: the alpha of the classic recording band's edges over its fill; 0 draws none (today's card has its own edge).
        KEYCAP_DETAIL_EDGE_ALPHA(0);''', 1)
anchor = "        values.put(Role.KEYCAP_DETAIL_INSET, 4);\n"
if text.count(anchor) != 1: raise SystemExit("DesignMetrics: Task 3's classic values moved")
BAND = [("KEYCAP_DETAIL_EDGE_ALPHA", 51), ("KEYCAP_DETAIL_TOP", 7), ("KEYCAP_DETAIL_BOTTOM", 7), ("KEYCAP_DETAIL_LEADING", 32),
    ("KEYCAP_CONTENT_TRAILING", 8), ("KEYCAP_DETAIL_AFTER", 0), ("KEYCAP_RECORDER_HALO", 0), ("KEYCAP_RECORDER_DOT", 7), ("KEYCAP_RECORDER_HEIGHT", 22)]
text = text.replace(anchor, anchor + "        // Plan 9b: the classic recording band (Mock measurements, 07b).\n" + "".join(f"        values.put(Role.{n}, {v});\n" for n, v in BAND), 1)
path.write_text(text, encoding="utf-8")
T = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics")
compact = T / "compact-roles.tsv"; compact.write_text(compact.read_text(encoding="utf-8") + "KEYCAP_DETAIL_EDGE_ALPHA\t0\n", encoding="utf-8")
classic = T / "classic-roles.tsv"; rows = (classic.read_text(encoding="utf-8") + "KEYCAP_DETAIL_EDGE_ALPHA\t0\n").splitlines()
values = dict(BAND); seen = set()
for i, row in enumerate(rows):
    name = row.split("\t")[0]
    if name in values: rows[i] = f"{name}\t{values[name]}"; seen.add(name)
if seen != set(values): raise SystemExit(f"classic-roles.tsv: missing {sorted(set(values) - seen)}")
classic.write_text("\n".join(rows) + "\n", encoding="utf-8")
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java"); text = path.read_text(encoding="utf-8")
old = '"KEYMAP_SCOPE_GAP"'
if text.count(old) != 1: raise SystemExit("NewRolesTest: Task 3's roles are missing")
path.write_text(text.replace(old, old + ', "KEYCAP_DETAIL_EDGE_ALPHA"', 1), encoding="utf-8")
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/ClassicCatalogTest.java"); text = path.read_text(encoding="utf-8")
old = '"SETTINGS_HEADER_INSET", "SETTINGS_NAV_CHILD_INDENT"'
if text.count(old) != 1: raise SystemExit("ClassicCatalogTest: plan 9a's exception set moved")
path.write_text(text.replace(old, old + ', "KEYCAP_DETAIL_EDGE_ALPHA"', 1), encoding="utf-8")
PY
```

`ThemePainting.rounded(Graphics2D, JComponent, KitContext, Color, Color, boolean focused, boolean error, DesignMetrics.Role
radius)` is the overload the field surface paints with (`TextFields.java:431-433`); `Buttons.KitButton.armed()`,
`recorder()` and `context()` are package members (fact 9, 4b). The default face's band is the former canvas code moved
verbatim into `Trees.TreeControl.paintDetailCard`, so the default look paints the same pixels. The `classic-roles.tsv` row
for `KEYCAP_DETAIL_EDGE_ALPHA` is appended at 0 and then set to 51 by the loop.

- [ ] **Step 4: Compose the classic band**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 4 step 4: ShortcutEditPanel's classic band (Rulings 11)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
H = "jasper-app/src/main/java/dev/jasper/app/shortcuthelp/"
edit(H + "ShortcutEditPanel.java", [
    ('''    private final Runnable recordingStarted;''', '''    private final Runnable recordingStarted;
    /** The classic band (spec 12.2): the recorder is a field a click arms, and the hint line has no Record action. */
    private final boolean recorderField;''', 1),
    ('''    ShortcutEditPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer,
                      Runnable recordingStarted) {
        super(new BorderLayout());''', '''    ShortcutEditPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer,
                      Runnable recordingStarted) {
        this(context, macOs, writer, recordingStarted, false);
    }

    ShortcutEditPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer,
                      Runnable recordingStarted, boolean recorderField) {
        super(new BorderLayout());
        this.recorderField = recorderField;''', 1),
    ('''        var note = new Nodes.Stack("editor.note", ControlState.READY, Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Text("editor.message", ControlState.READY, message(), Nodes.TextRole.CAPTION),
            new Nodes.Button("editor.record", idle, recording ? "Stop" : "Record", "", Nodes.ButtonKind.GHOST, "editor.record", "Start or stop recording", Nodes.ButtonPresentation.EMBEDDED),
            new Nodes.MenuButton("editor.options",idle,"","more",Nodes.ButtonKind.GHOST,"",options,"Shortcut actions",Nodes.ButtonPresentation.EMBEDDED)));''',
     '''        var message = new Nodes.Text("editor.message", ControlState.READY, message(), Nodes.TextRole.CAPTION);
        var more = new Nodes.MenuButton("editor.options",idle,"","more",Nodes.ButtonKind.GHOST,"",options,"Shortcut actions",Nodes.ButtonPresentation.EMBEDDED);
        var note = new Nodes.Stack("editor.note", ControlState.READY, Nodes.LayoutRole.NAVIGATION, recorderField ? List.of(message, more) : List.of(message,
            new Nodes.Button("editor.record", idle, recording ? "Stop" : "Record", "", Nodes.ButtonKind.GHOST, "editor.record", "Start or stop recording", Nodes.ButtonPresentation.EMBEDDED),
            more));''', 1),
    ('''    private ViewSpec spec() {''', '''    /** The band's descriptor now; for tests. */
    ViewSpec specForTests() { return spec(); }

    private ViewSpec spec() {''', 1)])
edit(H + "ShortcutPanel.java", [
    ('''        editor = new ShortcutEditPanel(context, macOs, writer, this::stopSearchRecording);''',
     '''        editor = new ShortcutEditPanel(context, macOs, writer, this::stopSearchRecording, shape.recorderField());''', 1)])
PY
```

The recorder already arms on a click (`editor.value` starts recording, fact 7), and Escape and Return already cancel and
set (`gesture`); the band's look is the tree and button faces' (Step 3).

- [ ] **Step 5: Compare the recording band with its mock**

Append to `SettingsPagesMockTest`:

```java
    @Test void keymapRecordingMatchesItsMock() throws Exception {
        var recording = new SettingsChromeMockTest.Page("Settings 07b - Keymap - Recording shortcut.png", "keymap", 713, 7);
        try (var settings = EnterpriseSettings.open(false)) {
            var render = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> {
                settings.dialog().select("keymap");
                var keymap = (dev.jasper.app.shortcuthelp.KeymapPage) settings.dialog().pages().stream().filter(page -> page.id().equals("keymap")).findFirst().orElseThrow();
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.expand(keymap, "Clipboard");
                dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.edit(keymap, "Copy");
                render[0] = settings.render("keymap", 1040, recording.height());
            });
            ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("pages")).resolve("07b.png").toFile());
            var mock = SettingsChromeMockTest.content(recording);
            var failures = new ArrayList<String>();
            for (var probe : List.of(new SettingsChromeMockTest.Probe("band fill", 300, 225), new SettingsChromeMockTest.Probe("band top edge", 600, 195),
                    new SettingsChromeMockTest.Probe("band bottom edge", 600, 254), new SettingsChromeMockTest.Probe("recorder field", 500, 213),
                    new SettingsChromeMockTest.Probe("armed dot", 252, 213), new SettingsChromeMockTest.Probe("Set disabled", 970, 213),
                    new SettingsChromeMockTest.Probe("edited row selected", 600, 183))) {
                double distance = MockMatch.deltaE(render[0].getRGB(2 * probe.x(), 2 * probe.y()), mock.getRGB(2 * probe.x(), 2 * probe.y()));
                if (distance > SettingsChromeMockTest.PROBE) failures.add(probe.name() + " ΔE " + distance);
            }
            var result = MockMatch.compare(MockMatch.crop(render[0], body(recording)), MockMatch.crop(mock, body(recording)), 2, SettingsChromeMockTest.TOLERANCE);
            if (result.matching() < FLOORS.getOrDefault("keymap-recording", FLOOR)) {
                MockMatch.inspect(EnterpriseSettings.OUT.resolve("compare"), "07b-body", MockMatch.crop(render[0], body(recording)), MockMatch.crop(mock, body(recording)), SettingsChromeMockTest.TOLERANCE);
                if (SettingsChromeMockTest.MAC) failures.add("07b body " + result.matching());
            }
            assertThat(failures).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
```

`ShortcutPreviewSupport.edit(KeymapPage, String actionName)` opens the band on the named action, as a click on its row
does; add it beside `expand`. Run:

`./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.metrics.*' :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`

Expected: PASS: `RecordingBandTest` (2), `KeymapBandTest` (2), `SettingsPagesMockTest` (4), `TreeDetailTest`,
`RecorderButtonTest` and the Keymap suites. Calibrate by the measurements: the band 60 tall from the row's bottom, the
field 7 below its top and from canvas 34, Cancel and Set ending 8 before the box (`KEYCAP_DETAIL_*`,
`KEYCAP_CONTENT_TRAILING`, both tables); the band's edges within ΔE 3 of #C9D7F2 (`KEYCAP_DETAIL_EDGE_ALPHA`; if
`border.focus` is not the accent in Enterprise Light, record the colour it is and the alpha that matches).

- [ ] **Step 6: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-ui/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: the Keymap's inline recording band

The tree face paints a keycap row's detail: today's card, or under the
classic face a band below the still-selected row across the tree, on
surface.rowHover with faint accent edges. The classic button face draws a
recorder as a field with its focus ring and placeholder prompt. The
classic Keymap's hint line holds the message and the shortcut actions'
ellipsis, without Record; the field arms on a click.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: `changed: none`.

### Task 5: Plugins: checkbox rows, folder tabs and ⋯ menus

Under `Composition.CLASSIC` the Plugins page (spec 12.2) is a boxed list of checkbox rows without the plugin icon under
banded group headings, the list's ⋯ holds Install from Zip…, the details' ⋯ beside Disable holds the plugin's actions
(Remove… or Discard Install among them), and Overview | Details is TABS in both looks (Rulings 2 and 12).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginShape.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginsPage.java:45-50`, `PluginManager.java` (`attach`), `PluginManagerPanel.java:53-126`, `PluginCardList.java:44-152`, `PluginCard.java:23-45`, `PluginDetails.java:53-198`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/TableFace.java`, `DefaultTableFace.java`, `ClassicTableFace.java`, `InteractiveRows.java` (Canvas), `ViewReconciler.java:374`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicListHeadingsTest.java` (one test)
- Create: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginsCompositionTest.java`
- Modify: `SettingsPagesMockTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: plan 3b's `ChoiceKind.TABS`; plan 4b's checkbox and folder tab faces; `PluginManager.attach(Supplier<AuxiliarySurface>, Consumer<String>, Supplier<KitContext>)` (`PluginsPage.java:47`).
- Produces: `public record PluginShape(boolean checkboxRows, boolean ellipsisActions)` with `ROWS` and `CLASSIC`;
  `PluginManager.attach(…, PluginShape)`; `PluginManagerPanel(KitContext, Handlers, PluginShape)`;
  `PluginCardList(KitContext, Consumer<UiEvent>, Runnable, PluginShape)`; `PluginCard.item(Row, boolean, String, PluginShape)`;
  `PluginDetails(KitContext, Consumer<UiEvent>, PluginShape)`; `void TableFace.paintListHeadings(InteractiveRows.RowsControl,
  Graphics2D, int width)`.

- [ ] **Step 1: Write the failing tests**

Append to `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicListHeadingsTest.java` (before its final brace):

```java
    static ViewSpec plugins() {
        return new ViewSpec(new Nodes.ItemList("plugins", ControlState.READY, Nodes.ListKind.DOUBLE, List.of(
            new dev.jasper.ui.model.ListItem("group:Installed", Optional.empty(), List.of(new Nodes.Text("title", ControlState.READY, "User-installed", Nodes.TextRole.SECTION_LABEL)), List.of(), List.of(), true),
            new dev.jasper.ui.model.ListItem("thd", Optional.of(new Nodes.Toggle("toggle:thd", ControlState.READY, "Enable THD Sourcing Crypto", true, "toggle:thd", true)),
                List.of(new Nodes.Text("name:thd", ControlState.READY, "THD Sourcing Crypto", Nodes.TextRole.BODY)), List.of(), List.of())),
            Set.of("thd"), "", List.of()));
    }

    @Test void aClassicListsHeadingIsABandBetweenRules() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, plugins(), event -> { })) {
                view.setSize(290, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var list = (ItemLists.ListControl) view.control("plugins");
                var heading = list.mounted.get("group:Installed");
                var image = new java.awt.image.BufferedImage(290, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
                var at = javax.swing.SwingUtilities.convertPoint(heading, 200, heading.getHeight() / 2, view);
                assertThat(image.getRGB(at.x, at.y) & 0xFFFFFF).isEqualTo(dev.jasper.ui.theme.DerivedColor.SURFACE_TABLE_HEADER.color(context.theme()).getRGB() & 0xFFFFFF);
                var below = javax.swing.SwingUtilities.convertPoint(heading, 200, heading.getHeight(), view);
                assertThat(image.getRGB(below.x, below.y) & 0xFFFFFF).isEqualTo(dev.jasper.ui.theme.DerivedColor.BORDER_ROW.color(context.theme()).getRGB() & 0xFFFFFF);
            } finally { release(context); }
        });
    }
```

`ItemLists.ListControl` is the item list's control class (`ItemLists.java:85`); if it is named otherwise, use its name.
Create `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginsCompositionTest.java`:

```java
package dev.jasper.app.pluginmanager;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the Plugins page's checkbox rows, folder tabs and ⋯ menus; every action stays reachable. */
class PluginsCompositionTest {
    @Test void aClassicRowLeadsWithItsCheckboxAndHasNoIcon() {
        var row = TestRows.enabled("dev.example.tidy", "Tidy Tabs");
        var item = PluginCard.item(row, false, "plugin", PluginShape.CLASSIC);
        assertThat(item.leading()).containsInstanceOf(Nodes.Toggle.class);
        assertThat(item.trailing()).isEmpty();
        assertThat(item.title()).singleElement().satisfies(title -> assertThat(((Nodes.Text) title).role()).isEqualTo(Nodes.TextRole.BODY));
        var plain = PluginCard.item(row, false, "plugin", PluginShape.ROWS);
        assertThat(plain.leading()).containsInstanceOf(Nodes.Icon.class);
        assertThat(plain.trailing()).singleElement().isInstanceOf(Nodes.Toggle.class);
    }

    @Test void everyRowsStateAndActionStayReachable() {
        for (var row : List.of(TestRows.needsReview("dev.example.review"), TestRows.failed("dev.example.failed"), TestRows.pendingRemoval("dev.example.gone"))) {
            var item = PluginCard.item(row, false, "plugin", PluginShape.CLASSIC);
            var toggle = (Nodes.Toggle) item.leading().orElseThrow();
            assertThat(toggle.checked()).as(row.id()).isEqualTo(PluginCard.checked(row));
            if (!PluginCard.toggleable(row)) assertThat(toggle.tooltip()).as(row.id()).isEqualTo(PluginCard.why(row));
            assertThat(item.contextActions().orElseThrow()).extracting(MenuItem::label).as(row.id()).containsAll(PluginCard.menu(row, false).stream().map(MenuItem::label).toList());
        }
    }

    @Test void theDetailsUseTabsInBothLooksAndTheClassicEllipsisSitsBesideDisable() throws Exception {
        edt(() -> {
            for (var shape : List.of(PluginShape.ROWS, PluginShape.CLASSIC)) {
                var details = new PluginDetails(dev.jasper.app.testsupport.KitTestSupport.context(), event -> { }, shape);
                try {
                    details.show(TestRows.enabled("dev.example.tidy", "Tidy Tabs"), false);
                    var root = details.header().spec().root();
                    var section = (Nodes.Choice) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, PluginDetails.SECTION);
                    assertThat(section.kind()).as(shape.toString()).isEqualTo(Nodes.ChoiceKind.TABS);
                    var more = (Nodes.MenuButton) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, PluginDetails.MORE);
                    assertThat(more.icon()).isEqualTo(shape.ellipsisActions() ? "more" : "chevron.down");
                    var head = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, "head");
                    assertThat(head.children().stream().anyMatch(node -> node.id().equals(PluginDetails.MORE))).as(shape + ": the ⋯ in the head").isEqualTo(shape.ellipsisActions());
                    assertThat(head.children().stream().anyMatch(node -> node.id().equals("icon"))).as(shape + ": the identity icon").isEqualTo(!shape.checkboxRows());
                } finally { details.close(); }
            }
        });
    }
}
```

`TestRows` is the package's existing row fixture (`jasper-app/src/test/java/dev/jasper/app/pluginmanager/TestRows.java`);
use its factories for an enabled row, one that needs review, a failed one and one pending removal, adding any of the four
it lacks in its own style.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicListHeadingsTest' :jasper-app:test --tests 'dev.jasper.app.pluginmanager.PluginsCompositionTest' --continue`
Expected: FAIL: the list heading's band is the default (no `surface.tableHeader`), and at compile "cannot find symbol:
class PluginShape".

- [ ] **Step 3: Band a classic list's headings and keep TABS in the identity section**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 5 step 3: list heading bands under the classic table face; TABS keeps the identity section's padding."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def before_last_brace(name, block):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}"): raise SystemExit(f"{name}: expected the closing brace last")
    path.write_text(text[:-1].rstrip() + "\n" + block + "}\n", encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
before_last_brace(S + "TableFace.java", '''    /** Paints behind an item list's heading rows across {@code width}: nothing, or the classic heading bands (plan 9b Rulings 12). */
    void paintListHeadings(InteractiveRows.RowsControl rows, java.awt.Graphics2D g, int width);
''')
before_last_brace(S + "DefaultTableFace.java", '''    @Override public void paintListHeadings(InteractiveRows.RowsControl rows, java.awt.Graphics2D g, int width) { }
''')
before_last_brace(S + "ClassicTableFace.java", '''    /** Plan 9b Rulings 12: a list heading is a {@code surface.tableHeader} band with a faint {@code border.row} rule below it. */
    @Override public void paintListHeadings(InteractiveRows.RowsControl rows, java.awt.Graphics2D g, int width) {
        var context = rows.context();
        int line = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE);
        for (var row : rows.mounted.values()) {
            if (rows.dataRow(row.index)) continue;
            g.setColor(ThemePainting.role(context, dev.jasper.ui.theme.DerivedColor.SURFACE_TABLE_HEADER));
            g.fillRect(0, row.getY(), width, row.getHeight());
            g.setColor(ThemePainting.role(context, dev.jasper.ui.theme.DerivedColor.BORDER_ROW));
            if (row.getY() > 0) g.fillRect(0, row.getY() - line, width, line);
            g.fillRect(0, row.getY() + row.getHeight(), width, line);
        }
    }
''')
edit(S + "InteractiveRows.java", [
    ('''            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.''',
     '''            if (owner.node instanceof Nodes.ItemList) {
                var g = (Graphics2D)graphics.create();
                try { owner.context().faces().table.paintListHeadings(owner, g, getWidth()); } finally { g.dispose(); }
            }
            // Selection and hover fill each row's whole band, edge to edge of the list, under the row's inset content.''', 1)])
edit(S + "ViewReconciler.java", [
    ('''        if(control instanceof Choices.ChoiceControl choice)choice.identitySection=identity && ((Nodes.Choice)node).kind()==Nodes.ChoiceKind.EXCLUSIVE;''',
     '''        if(control instanceof Choices.ChoiceControl choice)choice.identitySection=identity && (((Nodes.Choice)node).kind()==Nodes.ChoiceKind.EXCLUSIVE || ((Nodes.Choice)node).kind()==Nodes.ChoiceKind.TABS);''', 1)])
PY
```

The default face paints nothing, so a default list's headings keep their own separator. `RowsControl.mounted`,
`dataRow(int)` and `Row.index` are the package members `Canvas` already reads (fact 8).

- [ ] **Step 4: Compose the classic Plugins page**

Create `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginShape.java`:

```java
package dev.jasper.app.pluginmanager;

/**
 * How the Plugins page is composed (spec 12.2), as {@link PluginsPage} maps the look's composition once.
 *
 * @param checkboxRows rows lead with their enable checkbox and show no plugin icon; the list is one boxed lane holding its
 *     search; the details show no identity icon
 * @param ellipsisActions the plugin's actions are a ⋯ beside its primary action rather than a More actions menu
 */
public record PluginShape(boolean checkboxRows, boolean ellipsisActions) {
    /** Today's Plugins page. */
    public static final PluginShape ROWS = new PluginShape(false, false);
    /** The classic Plugins page of the Settings mocks. */
    public static final PluginShape CLASSIC = new PluginShape(true, true);
}
```

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 5 step 4: PluginsPage maps the composition to a PluginShape; the manager's views follow it (Rulings 12)."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/pluginmanager/"
edit(P + "PluginsPage.java", [
    ('''        panel = manager.attach(context.surface(), context.navigate(), context.kit());''',
     '''        panel = manager.attach(context.surface(), context.navigate(), context.kit(),
            context.composition() == dev.jasper.app.presentation.Composition.CLASSIC ? PluginShape.CLASSIC : PluginShape.ROWS);''', 1)])
path = pathlib.Path(P + "PluginManager.java"); text = path.read_text(encoding="utf-8")
found = re.search(r"(PluginManagerPanel attach\([^)]*\)) \{", text)
if not found: raise SystemExit("PluginManager: attach moved")
signature = found.group(1)
text = text.replace(found.group(0), signature[:-1] + ", PluginShape shape) {", 1)
ctor = text.count("new PluginManagerPanel(")
if ctor != 1: raise SystemExit(f"PluginManager: expected one new PluginManagerPanel, found {ctor}")
text = re.sub(r"new PluginManagerPanel\(([^;]*?)\)(\s*;)", lambda m: f"new PluginManagerPanel({m.group(1)}, shape){m.group(2)}", text, count=1)
path.write_text(text, encoding="utf-8")
edit(P + "PluginCard.java", [
    ('''    static ListItem item(PluginRuntime.Row row, boolean busy, String artwork) {
        String id = row.id();''', '''    static ListItem item(PluginRuntime.Row row, boolean busy, String artwork) { return item(row, busy, artwork, PluginShape.ROWS); }

    /** The row in {@code shape}: under checkbox rows, the checkbox leads the name and there is no icon (spec 12.2). */
    static ListItem item(PluginRuntime.Row row, boolean busy, String artwork, PluginShape shape) {
        String id = row.id();''', 1),
    ('''        return new ListItem(id, Optional.of(icon), List.of(new Nodes.Text("name:" + id, ControlState.READY, row.name(), Nodes.TextRole.BODY_STRONG)),
            List.of(new Nodes.Stack("second:" + id, ControlState.READY, Nodes.LayoutRole.INLINE, subtitle)), List.of(toggle), false,
            Optional.of(menu(row, busy)));''', '''        if (shape.checkboxRows())
            return new ListItem(id, Optional.of(toggle), List.of(new Nodes.Text("name:" + id, ControlState.READY, row.name(), Nodes.TextRole.BODY)),
                List.of(new Nodes.Stack("second:" + id, ControlState.READY, Nodes.LayoutRole.INLINE, subtitle)), List.of(), false,
                Optional.of(menu(row, busy)));
        return new ListItem(id, Optional.of(icon), List.of(new Nodes.Text("name:" + id, ControlState.READY, row.name(), Nodes.TextRole.BODY_STRONG)),
            List.of(new Nodes.Stack("second:" + id, ControlState.READY, Nodes.LayoutRole.INLINE, subtitle)), List.of(toggle), false,
            Optional.of(menu(row, busy)));''', 1)])
edit(P + "PluginCardList.java", [
    ('''    private boolean busy;
''', '''    private boolean busy;
    private final PluginShape shape;
''', 1),
    ('''    PluginCardList(KitContext kit, Consumer<UiEvent> events, Runnable selectionChanged) {
        this.selectionChanged = selectionChanged;''', '''    PluginCardList(KitContext kit, Consumer<UiEvent> events, Runnable selectionChanged) { this(kit, events, selectionChanged, PluginShape.ROWS); }

    PluginCardList(KitContext kit, Consumer<UiEvent> events, Runnable selectionChanged, PluginShape shape) {
        this.shape = shape;
        this.selectionChanged = selectionChanged;''', 1),
    ('''            for (PluginRuntime.Row row : matching) items.add(PluginCard.item(row, busy, artNames.getOrDefault(row.id(), "plugin")));''',
     '''            for (PluginRuntime.Row row : matching) items.add(PluginCard.item(row, busy, artNames.getOrDefault(row.id(), "plugin"), shape));''', 1),
    ('''            for(var row:shown)view.setArtworkPresentation("icon:"+row.id(),dev.jasper.ui.swing.ArtworkPresentation.IDENTITY_ROW);''',
     '''            if(!shape.checkboxRows())for(var row:shown)view.setArtworkPresentation("icon:"+row.id(),dev.jasper.ui.swing.ArtworkPresentation.IDENTITY_ROW);''', 1)])
edit(P + "PluginDetails.java", [
    ('''    private String section = OVERVIEW;
''', '''    private String section = OVERVIEW;
    private final PluginShape shape;
''', 1),
    ('''    PluginDetails(KitContext kit, Consumer<UiEvent> events) {
        this.kit = kit;''', '''    PluginDetails(KitContext kit, Consumer<UiEvent> events) { this(kit, events, PluginShape.ROWS); }

    PluginDetails(KitContext kit, Consumer<UiEvent> events, PluginShape shape) {
        this.shape = shape;
        this.kit = kit;''', 1),
    ('''        var more = new Nodes.MenuButton(MORE, present ? actionsState : hidden, "", "chevron.down", Nodes.ButtonKind.SECONDARY, "", items, "More actions");''',
     '''        var more = shape.ellipsisActions() ? new Nodes.MenuButton(MORE, present ? actionsState : hidden, "", "more", Nodes.ButtonKind.GHOST, "", items, "Plugin actions")
            : new Nodes.MenuButton(MORE, present ? actionsState : hidden, "", "chevron.down", Nodes.ButtonKind.SECONDARY, "", items, "More actions");''', 1),
    ('''        var head = new Nodes.Stack("head", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon("icon",new ControlState(true,!present || !PluginCard.greyed(shown),false,""),artName,Nodes.Hue.NEUTRAL,Nodes.IconSize.PROMPT,present?shown.name():"Plugin"),identity,primary));
        var ancillary=new Nodes.Stack("ancillary",new ControlState(section.equals(DETAILS),true,false,""),Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Button(HOMEPAGE, present && homepage(shown).isPresent() ? ControlState.READY : hidden, "Plugin homepage ↗", "", Nodes.ButtonKind.LINK, HOMEPAGE, ""),more));
        var choice = new Nodes.Choice(SECTION, ControlState.READY, Nodes.ChoiceKind.EXCLUSIVE,''',
     '''        var icon = new Nodes.Icon("icon",new ControlState(true,!present || !PluginCard.greyed(shown),false,""),artName,Nodes.Hue.NEUTRAL,Nodes.IconSize.PROMPT,present?shown.name():"Plugin");
        var homepage = new Nodes.Button(HOMEPAGE, present && homepage(shown).isPresent() ? ControlState.READY : hidden, "Plugin homepage ↗", "", Nodes.ButtonKind.LINK, HOMEPAGE, "");
        // Spec 12.2: the classic details have no identity icon, and the plugin's ⋯ stands beside its primary action.
        var head = new Nodes.Stack("head", ControlState.READY, Nodes.LayoutRole.NAVIGATION, shape.checkboxRows()
            ? (shape.ellipsisActions() ? List.of(identity, primary, more) : List.of(identity, primary)) : List.of(icon, identity, primary));
        var ancillary=new Nodes.Stack("ancillary",new ControlState(section.equals(DETAILS),true,false,""),Nodes.LayoutRole.NAVIGATION,
            shape.ellipsisActions() ? List.of(homepage) : List.of(homepage, more));
        var choice = new Nodes.Choice(SECTION, ControlState.READY, Nodes.ChoiceKind.TABS,''', 1),
    ('''            header.setArtworkPresentation("icon",dev.jasper.ui.swing.ArtworkPresentation.IDENTITY_HEADER);''',
     '''            if(!shape.checkboxRows())header.setArtworkPresentation("icon",dev.jasper.ui.swing.ArtworkPresentation.IDENTITY_HEADER);''', 1)])
edit(P + "PluginManagerPanel.java", [
    ('''    private boolean error, busy, closed;
''', '''    private boolean error, busy, closed;
    private final PluginShape shape;
''', 1),
    ('''    PluginManagerPanel(KitContext kit, Handlers handlers) {
        super(new BorderLayout());
        this.kit = kit;''', '''    PluginManagerPanel(KitContext kit, Handlers handlers) { this(kit, handlers, PluginShape.ROWS); }

    PluginManagerPanel(KitContext kit, Handlers handlers, PluginShape shape) {
        super(new BorderLayout());
        this.shape = shape;
        this.kit = kit;''', 1),
    ('''        details = new PluginDetails(kit, this::event);''', '''        details = new PluginDetails(kit, this::event, shape);''', 1),
    ('''        list = new PluginCardList(kit, this::event, () -> { if (cardList() != null) details.show(cardList().selectedRow(), busy); });''',
     '''        list = new PluginCardList(kit, this::event, () -> { if (cardList() != null) details.show(cardList().selectedRow(), busy); }, shape);''', 1),
    ('''        list.view().setBorder(BorderFactory.createEmptyBorder(0,0,0,side));''',
     '''        list.view().setBorder(BorderFactory.createEmptyBorder(0,0,0,side));
        if (shape.checkboxRows()) {
            // Spec 12.2 and plan 9b Rulings 12: one boxed lane on the card surface holding the search row and the rows.
            var palette = kit.theme().semantic();
            int inset = metric(DesignMetrics.Role.PLUGIN_SEARCH_GAP) + 1;
            listLane.setOpaque(true);
            listLane.setBackground(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));
            listLane.setBorder(BorderFactory.createLineBorder(palette.color(dev.jasper.ui.theme.SemanticColor.BORDER_CONTROL)));
            bar.setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
            list.view().setBorder(BorderFactory.createEmptyBorder());
        }''', 1)])
PY
```

The classic lane keeps the panel's own geometry roles (`PLUGIN_*`); the box's margins and the details' 12 px gap are
calibrated in Step 5 by classic values of those roles, if the comparison asks. Add the test's ledger row:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginsCompositionTest.java,app.pluginmanager,PluginsCompositionTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginsCompositionTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 5; the classic Plugins page")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Compare the page with its mock**

Append to `SettingsPagesMockTest`:

```java
    @Test void pluginsMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            SwingUtilities.invokeAndWait(() -> {
                settings.dialog().select("plugins");
                settings.dialog().pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow().highlight("THD Sourcing Crypto");
            });
            assertThat(compare(settings, List.of("plugins"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
```

and its probes:

```java
        PROBES.put("plugins", List.of(new SettingsChromeMockTest.Probe("list box", 247, 300), new SettingsChromeMockTest.Probe("list surface", 400, 400),
            new SettingsChromeMockTest.Probe("heading band", 450, 99), new SettingsChromeMockTest.Probe("selected row", 450, 120),
            new SettingsChromeMockTest.Probe("checkbox on the selection", 257, 127), new SettingsChromeMockTest.Probe("active tab", 600, 113),
            new SettingsChromeMockTest.Probe("inactive tab", 650, 113)));
```

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --tests 'dev.jasper.app.architecture.CompositionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicListHeadingsTest` (3), `PluginsCompositionTest` (3), `SettingsPagesMockTest` (5), and the
existing manager suites (`PluginManagerPanelTest`, `PluginCardListTest`, `PluginManagerTest`) under ROWS, except where one
asserts `ChoiceKind.EXCLUSIVE` for Overview | Details: change that assertion to `TABS` (Rulings 2). Calibrate the box
(292 wide, 16 from the body's left, 18 below the header rule, 16 above the footer), the search inside it, rows 36 tall,
the checkbox at 8 and the name at 28.5, the details 12 right of the box, by classic values of the `PLUGIN_*` roles (both
tables) and, for the row height, the role `ListKind.DOUBLE` rows read (find it in `InteractiveRows.RowsControl.refresh`).

- [ ] **Step 6: Prove the default look and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: `changed:` lists at most the `plugins*` preview files, and only if today's render showed the choice's label
(Rulings 2). Open each listed file beside its base copy: the only difference may be the "Plugin information" label line
above the Overview | Details track (and what moves up by its height). Any other difference is a defect: fix it before the
commit. Record the listed files in the banner, then:

```bash
git add jasper-ui/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: Plugins' checkbox rows, folder tabs and ellipsis menus

Under the classic composition the Plugins page is one boxed list of
checkbox rows without icons under banded group headings, the list's
ellipsis holds Install from Zip and the plugin's ellipsis beside Disable
holds its actions, Remove among them. Overview and Details are TABS in
both looks; the default look draws its segment track as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: Shell: the environment table and its sheet

Under `Composition.CLASSIC` the Shell page's environment variables are a Name / Value table under the row "Environment
variables" with Add Variable… at its end (spec 12.2); Add Variable… and the row menu's Edit… open a sheet with Name and
Value; Remove is in the row menu; with no variables only the row shows. The page's ordered lists show "Empty" while empty
(Rulings 13 to 15). Sheets reach editors through `PageContext.sheets()`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/Sheets.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageContext.java` (a tenth component), `SettingsDialog.java` (sheets), `Editors.java:176-190,385-490`
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/EnvironmentTableTest.java`
- Modify: `SettingsPagesMockTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: plan 3c's `Nodes.Table`, `TableRow`, `Nodes.ItemList(…, emptyText)`; `SettingsDialog.confirm`'s sheet pattern (fact 12).
- Produces: `public record Sheets(Function<Sheets.Request, Optional<List<String>>> ask)` with `Sheets.NONE`,
  `record Sheets.Request(String title, String heading, List<Sheets.Field> fields, String confirm)`, `record Sheets.Field(String id,
  String label, String value)`; `PageContext(… chrome, composition, Sheets sheets)` (the 9-argument form keeps `Sheets.NONE`);
  `Editors.Table(Setting<Map<String,String>>, PageContext, boolean classic)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/EnvironmentTableTest.java`:

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the environment table, Add Variable…, Edit… and Remove. */
class EnvironmentTableTest {
    static final String ID = CoreSettings.TERMINAL_ENV.name();

    static Editors.Table editor(ArrayDeque<Optional<List<String>>> answers) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        var context = new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, SettingsChromeTest.CLASSIC, Composition.CLASSIC, new Sheets(request -> answers.isEmpty() ? Optional.empty() : answers.poll()));
        return new Editors.Table(CoreSettings.TERMINAL_ENV, context, true);
    }

    @Test void withNoVariablesOnlyTheRowAndAddVariableShow() {
        var editor = editor(new ArrayDeque<>());
        editor.show(Map.of());
        var node = (Nodes.Stack) editor.node(ID, ControlState.READY);
        assertThat(node.children()).singleElement().isInstanceOf(Nodes.SettingRow.class);
        var row = (Nodes.SettingRow) node.children().getFirst();
        assertThat(row.title()).isEqualTo(CoreSettings.TERMINAL_ENV.label());
        assertThat(row.description()).isEqualTo("Applies to new panes");
        assertThat(((Nodes.Button) row.control()).label()).isEqualTo("Add Variable…");
    }

    @Test void addingAnEmptyValueAndAValueWithEqualsShowsThemAsWritten() {
        var answers = new ArrayDeque<Optional<List<String>>>(List.of(Optional.of(List.of("EMPTY", "")), Optional.of(List.of("OPTS", "a=b=c"))));
        var editor = editor(answers);
        editor.show(Map.of());
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":add"))).isTrue();
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":add"))).isTrue();
        assertThat(editor.value()).containsExactly(Map.entry("EMPTY", ""), Map.entry("OPTS", "a=b=c"));
        var table = (Nodes.Table) ((Nodes.Stack) editor.node(ID, ControlState.READY)).children().get(1);
        assertThat(table.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Name", "Value");
        assertThat(table.rows()).extracting(row -> row.cells().get("value")).containsExactly("", "a=b=c");
        assertThat(table.rows().getFirst().contextActions().orElseThrow()).extracting(MenuItem::label).containsExactly("Edit…", "Remove");
    }

    @Test void aRenameReplacesTheOtherEntryAndCancelChangesNothing() {
        var answers = new ArrayDeque<Optional<List<String>>>(List.of(Optional.of(List.of("B", "2")), Optional.empty()));
        var editor = editor(answers);
        editor.show(Map.of("A", "1", "B", "x"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":edit-0"))).as("rename A to B").isTrue();
        assertThat(editor.value()).containsExactly(Map.entry("B", "2"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":edit-0"))).as("cancelled").isFalse();
        assertThat(editor.value()).containsExactly(Map.entry("B", "2"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":remove-0"))).isTrue();
        assertThat(editor.value()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.EnvironmentTableTest'`
Expected: FAIL at compile: "cannot find symbol: class Sheets" and "constructor Table … cannot be applied".

- [ ] **Step 3: Give pages a sheet**

Create `jasper-app/src/main/java/dev/jasper/app/settingsui/Sheets.java`:

```java
package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Asks a small form in a sheet over the Settings dialog (plan 9b Rulings 14): its fields' values in order on its confirm
 * button, empty on Cancel, Escape or a closed sheet. EDT only; the dialog's implementation is modal.
 *
 * @param ask shows a request and returns its answer
 */
public record Sheets(Function<Sheets.Request, Optional<List<String>>> ask) {
    /** No dialog to ask over: every request is cancelled. */
    public static final Sheets NONE = new Sheets(request -> Optional.empty());

    /** The function is required. */
    public Sheets { Objects.requireNonNull(ask, "ask"); }

    /**
     * One text field of a sheet.
     *
     * @param id its id
     * @param label its label
     * @param value its initial text
     */
    public record Field(String id, String label, String value) {
        /** Every part is required. */
        public Field { Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(value); }
    }

    /**
     * A sheet.
     *
     * @param title the sheet's window title
     * @param heading the form's title
     * @param fields its fields, in order
     * @param confirm the primary button's label
     */
    public record Request(String title, String heading, List<Field> fields, String confirm) {
        /** Every part is required; the fields are copied. */
        public Request { Objects.requireNonNull(title); Objects.requireNonNull(heading); fields = List.copyOf(fields); Objects.requireNonNull(confirm); }
    }

    /** Asks {@code request}. */
    public Optional<List<String>> ask(Request request) { return ask.apply(request); }
}
```

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 6 step 3: PageContext carries Sheets; the dialog asks sheets as it asks confirmations."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "PageContext.java", [
    (''' * @param composition how the look composes the app's pages (spec 12.2); only the composition roots read it
 */''', ''' * @param composition how the look composes the app's pages (spec 12.2); only the composition roots read it
 * @param sheets asks a small form in a sheet over the dialog
 */''', 1),
    ('''                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome,
                          Composition composition) {
    public PageContext {''', '''                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome,
                          Composition composition, Sheets sheets) {
    public PageContext {
        Objects.requireNonNull(sheets, "sheets");''', 1),
    ('''    /** A context under the default look on this host ({@link #standard()}). */''', '''    /** A context that asks no sheets ({@link Sheets#NONE}). */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                       Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome, Composition composition) {
        this(draft, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition, Sheets.NONE);
    }

    /** A context under the default look on this host ({@link #standard()}). */''', 1),
    ('''        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition);''',
     '''        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition, sheets);''', 1)])
edit(S + "SettingsDialog.java", [
    ('''            () -> isOpen() ? surface : null, this::kit, hooks.chrome(), hooks.composition())));''',
     '''            () -> isOpen() ? surface : null, this::kit, hooks.chrome(), hooks.composition(), new Sheets(this::sheet))));''', 1),
    ('''    public boolean isOpen() { return surface != null && !surface.closed(); }''', '''    /**
     * Asks {@code request} in a modal sheet over the dialog (plan 9b Rulings 14): the fields' values in order on its
     * primary button, empty on Cancel, Escape or a closed sheet, or while the dialog is closed.
     */
    Optional<List<String>> sheet(Sheets.Request request) {
        if (!isOpen() || kit == null) return Optional.empty();
        var values = new java.util.ArrayList<String>();
        for (var field : request.fields()) values.add(field.value());
        boolean[] confirmed = {false};
        var sheet = windows.dialog(request.title(), true, surface);
        var fields = new java.util.ArrayList<Nodes.Node>();
        for (var field : request.fields())
            fields.add(new Nodes.FormRow("sheet-row-" + field.id(), ControlState.READY, field.label(),
                new Nodes.Field("sheet-" + field.id(), ControlState.READY, Nodes.FieldKind.TEXT, field.value(), "", field.label(), "")));
        var sheetView = new KitView(kit, new ViewSpec(new Nodes.Form("value-sheet", ControlState.READY, Nodes.FormKind.SHEET, request.heading(),
            Optional.empty(), List.of(new Nodes.Card("sheet-card", ControlState.READY, Nodes.CardKind.FORM, fields)), Optional.empty(),
            List.of(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("confirm", ControlState.READY, request.confirm(), "", Nodes.ButtonKind.PRIMARY, "confirm", "")), "cancel")),
            event -> {
                if (event instanceof UiEvent.Edited edited)
                    for (int i = 0; i < request.fields().size(); i++)
                        if (edited.id().equals("sheet-" + request.fields().get(i).id())) values.set(i, edited.text());
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals("confirm")) confirmed[0] = true;
                if (action.id().equals("confirm") || action.id().equals("cancel")) sheet.close();
            });
        sheet.onClosed(sheetView::close);
        sheet.setContent(sheetView);
        sheet.show();
        sheet.close();
        if (isOpen()) surface.toFront();
        return confirmed[0] ? Optional.of(List.copyOf(values)) : Optional.empty();
    }

    public boolean isOpen() { return surface != null && !surface.closed(); }''', 1)])
PY
```

The sheet is `confirm`'s pattern (fact 12) with a FORM card of `FormRow`s; `Nodes.FormRow(id, state, label, control)` and
`Nodes.CardKind.FORM` are the model's form vocabulary (read `Forms`' callers for their exact forms and adapt if a
constructor differs). The canonical `PageContext` now has ten components; plan 9a's tests use the nine-argument form,
which keeps `Sheets.NONE`.

- [ ] **Step 4: Compose the environment table and the empty lines**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 6 step 4: the classic environment table and the ordered lists' "Empty" line (Rulings 13 and 15)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "Editors.java", [
    ('''        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.empty()), List.of());
    }''', '''        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.empty(), classic(context)), List.of());
    }

    /** The composition root's reading: whether the page is composed classically (spec 12.2). */
    private static boolean classic(PageContext context) { return context.composition() == dev.jasper.app.presentation.Composition.CLASSIC; }''', 1),
    ('''        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.of(() -> Fonts.families(monospaced))), List.of());''',
     '''        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.of(() -> Fonts.families(monospaced)), classic(context)), List.of());''', 1),
    ('''        return new Factory<>((setting, context) -> new Table(), List.of());''',
     '''        return new Factory<>((setting, context) -> new Table(setting, context, classic(context)), List.of("Add Variable", "variable"));''', 1),
    ('''        String pending = "";
        Items(String label, Optional<Supplier<List<String>>> suggestions) { this.label = label; this.suggestions = suggestions; }''',
     '''        String pending = "";
        private final boolean classic;
        Items(String label, Optional<Supplier<List<String>>> suggestions) { this(label, suggestions, false); }
        /** {@code classic}: the list shows "Empty" while it holds nothing (plan 9b Rulings 15). */
        Items(String label, Optional<Supplier<List<String>>> suggestions, boolean classic) { this.label = label; this.suggestions = suggestions; this.classic = classic; }''', 1),
    ('''            var list = new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, Set.of(), "", List.of());''',
     '''            var list = classic
                ? new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(),
                    java.util.OptionalInt.empty(), "", "", "", "", "", "Empty")
                : new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, Set.of(), "", List.of());''', 1),
    ('''    /** Name/Value rows; a blank name is left out of the value. */
    static final class Table implements ValueEditor<Map<String, String>> {
        private final List<String[]> rows = new ArrayList<>();

        @Override public Nodes.Node node(String id, ControlState state) {''', '''    /**
     * Name/Value rows; a blank name is left out of the value. {@code classic} (spec 12.2): a Name / Value table under the
     * setting's row with Add Variable… at its end, Edit… and Remove in each row's menu, a sheet for Add and Edit; with no
     * variables, the row alone.
     */
    static final class Table implements ValueEditor<Map<String, String>> {
        private final List<String[]> rows = new ArrayList<>();
        private final boolean classic;
        private final Setting<Map<String, String>> setting;
        private final PageContext context;

        Table() { this(null, null, false); }

        Table(Setting<Map<String, String>> setting, PageContext context, boolean classic) {
            this.setting = setting; this.context = context; this.classic = classic && setting != null && context != null;
        }

        @Override public boolean fullSpan() { return classic; }
        @Override public boolean cardRows() { return classic; }

        @Override public Nodes.Node node(String id, ControlState state) {
            if (classic) return classicNode(id, state);''', 1),
    ('''        void addRow(String name, String value) { rows.add(new String[]{name, value}); }''', '''        private Nodes.Node classicNode(String id, ControlState state) {
            String applies = switch (setting.applies()) {
                case LIVE -> ""; case NEW_PANES -> "Applies to new panes"; case NEW_WINDOWS -> "Applies to new windows"; case RESTART -> "Takes effect after restart";
            };
            var add = new Nodes.Button(id + ":add", plain(state), "Add Variable…", "", Nodes.ButtonKind.SECONDARY, id + ":add", "");
            var row = new Nodes.SettingRow("row:" + id, ControlState.READY, setting.label(), applies, add, false);
            if (rows.isEmpty()) return new Nodes.Stack(id + ":environment", plain(state), Nodes.LayoutRole.VERTICAL, List.of(row));
            var columns = List.of(new dev.jasper.ui.model.Column("name", "Name", Nodes.TextRole.MONO, false),
                new dev.jasper.ui.model.Column("value", "Value", Nodes.TextRole.MONO, false, 2, Optional.empty()));
            var tableRows = new ArrayList<dev.jasper.ui.model.TableRow>();
            for (int i = 0; i < rows.size(); i++)
                tableRows.add(new dev.jasper.ui.model.TableRow("variable-" + i, Map.of("name", rows.get(i)[0], "value", rows.get(i)[1]), Optional.empty(), false,
                    Optional.of(List.of(new MenuItem("edit-" + i, "Edit…", "", id + ":edit-" + i, state.enabled(), false),
                        new MenuItem("remove-" + i, "Remove", "", id + ":remove-" + i, state.enabled(), false)))));
            var table = new Nodes.Table(id + ":table", plain(state), columns, tableRows, Set.of(), id + ":activate", List.of(), Nodes.SelectionMode.SINGLE);
            return new Nodes.Stack(id + ":environment", plain(state), Nodes.LayoutRole.VERTICAL, List.of(row, table));
        }

        /** Asks the variable sheet with {@code name} and {@code value}; the answer as a name and value, or empty. */
        private Optional<String[]> ask(String title, String confirm, String name, String value) {
            return context.sheets().ask(new Sheets.Request(title, title, List.of(new Sheets.Field("name", "Name", name), new Sheets.Field("value", "Value", value)), confirm))
                .filter(answer -> answer.size() == 2 && !answer.get(0).isBlank()).map(answer -> new String[]{answer.get(0).strip(), answer.get(1)});
        }

        /** Puts {@code entry} at {@code index} (or at the end), dropping another entry with the same name (the file holds one key). */
        private void put(int index, String[] entry) {
            for (int i = rows.size() - 1; i >= 0; i--) if (i != index && rows.get(i)[0].equals(entry[0])) { rows.remove(i); if (i < index) index--; }
            if (index < 0) rows.add(entry); else rows.set(index, entry);
        }

        void addRow(String name, String value) { rows.add(new String[]{name, value}); }''', 1),
    ('''        @Override public boolean event(String id, UiEvent event) {
            if (event instanceof UiEvent.Action action && action.id().equals(id + ":add")) { addRow("", ""); return true; }''',
     '''        @Override public boolean event(String id, UiEvent event) {
            if (classic && event instanceof UiEvent.Action action) {
                if (action.id().equals(id + ":add")) { var answer = ask("Add Variable", "Add", "", ""); answer.ifPresent(entry -> put(-1, entry)); return answer.isPresent(); }
                if (action.id().startsWith(id + ":edit-")) {
                    int index = Integer.parseInt(action.id().substring((id + ":edit-").length()));
                    if (index >= rows.size()) return false;
                    var answer = ask("Edit Variable", "Save", rows.get(index)[0], rows.get(index)[1]);
                    answer.ifPresent(entry -> put(index, entry));
                    return answer.isPresent();
                }
            }
            if (classic && event instanceof UiEvent.Selected selected && selected.id().equals(id + ":table")) return false;
            if (event instanceof UiEvent.Action action && action.id().equals(id + ":add")) { addRow("", ""); return true; }''', 1)])
PY
```

`Nodes.ItemList`'s sixteen-component form is plan 3c's (`…, cancelAction, emptyText`); if 3c's canonical orders the
string actions otherwise, keep the four blanks and put "Empty" in `emptyText`. A table row's activation (double-click or
Enter) reports the table's `activateAction` with the row's id the way the table reports it; route it to the same edit as
`:edit-<i>` by reading `Tables`' activation event in `KitView` and adding that case beside the `:edit-` one. Add the
test's ledger row:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
row = ("jasper-app/src/test/java/dev/jasper/app/settingsui/EnvironmentTableTest.java,app.settingsui,EnvironmentTableTest,nonvisual-helper,none,none,"
       "jasper-app/src/test/java/dev/jasper/app/settingsui/EnvironmentTableTest.java,Test/fixture ownership; New in Enterprise plan 9b Task 6; the classic environment table")
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Compare the page with its mock**

Append to `SettingsPagesMockTest`:

```java
    @Test void shellMatchesItsMock() throws Exception {
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, List.of("terminal.shell"))).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }
```

and its probes:

```java
        PROBES.put("terminal.shell", List.of(new SettingsChromeMockTest.Probe("Add Variable… edge", 990, 333),
            new SettingsChromeMockTest.Probe("arguments box", 400, 226), new SettingsChromeMockTest.Probe("Empty line", 270, 238),
            new SettingsChromeMockTest.Probe("Remove disabled", 925, 237)));
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.architecture.CompositionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `EnvironmentTableTest` (3), `SettingsPagesMockTest` (6), `EditorsTest` and `SettingsDialogTest` unchanged
(the ROWS composition keeps its field rows). Fix a comparison failure as in Task 1 Step 4.

- [ ] **Step 6: Prove the default look unchanged and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Settings pages: Shell's environment table and its sheet

Under the classic composition the environment variables are a Name and
Value table under their row, with Add Variable at the row's end; Add and
the row menu's Edit open a sheet with Name and Value, Remove is in the row
menu, and with no variables only the row shows. Pages ask sheets through
PageContext.sheets, which the dialog implements as it asks confirmations.
The page's ordered lists read Empty while empty.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: the compare script lists only Task 5's recorded `plugins*` files, if any.

### Task 7: The other twelve pages against their mocks

The pages whose bodies plans 4b, 4c, 9a and the plugins compose (Command Palette, Desk Buddy, System Settings, Font &
Colors, Cursor and the seven Tools pages) are compared with their mocks; the Enterprise Dark render of a composed page is
checked for structure. No new composition: a failure here is a Settings role or a face value.

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java`
- Modify (calibration only): `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (classic Settings values) and `classic-roles.tsv`

**Interfaces:**
- Consumes: Tasks 1 to 6; plan 9a's fixture.
- Produces: tests only.

- [ ] **Step 1: Write the comparisons**

Append to `SettingsPagesMockTest`:

```java
    @Test void theRemainingPagesMatchTheirMocks() throws Exception {
        var ids = List.of("command-palette", "desk-buddy", "system", "terminal.font", "terminal.cursor",
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.vault"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.editor"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.fileserver"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.indexer"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.remote"), dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.sample"),
            dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.history"));
        try (var settings = EnterpriseSettings.open(false)) {
            assertThat(compare(settings, ids)).as("see jasper-app/build/enterprise-settings/compare").isEmpty();
        }
    }

    @Test void composedPagesFollowEnterpriseDark() throws Exception {
        try (var settings = EnterpriseSettings.open(true)) {
            var palette = dev.jasper.app.testsupport.EnterpriseResources.theme().palette(true);
            for (var id : List.of("notifications", "keymap", "plugins", "terminal.shell")) {
                var page = page(id);
                var render = new BufferedImage[1];
                SwingUtilities.invokeAndWait(() -> render[0] = settings.render(id, 1040, page.height()));
                ImageIO.write(render[0], "png", Files.createDirectories(EnterpriseSettings.OUT.resolve("dark")).resolve(page.mock().substring(9, 11) + ".png").toFile());
                assertThat(render[0].getRGB(2 * 600, 2 * 40) & 0xFFFFFF).as(id + ": the body on dark surface.app")
                    .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB() & 0xFFFFFF);
                long light = 0;
                for (int y = 0; y < render[0].getHeight(); y += 7) for (int x = 2 * 231; x < render[0].getWidth(); x += 7) {
                    int rgb = render[0].getRGB(x, y);
                    if (((rgb >> 16) & 0xFF) > 0xF0 && ((rgb >> 8) & 0xFF) > 0xF0 && (rgb & 0xFF) > 0xF0) light++;
                }
                assertThat(light).as(id + ": no light surface left in a dark page (text aside)").isLessThan(200);
            }
        }
    }
```

and the probes, inside the static block (each at a drawn part of its mock: a box edge, a control's edge, a checkbox):

```java
        PROBES.put("command-palette", List.of(new SettingsChromeMockTest.Probe("box top", 600, 53), new SettingsChromeMockTest.Probe("spinner", 900, 108)));
        PROBES.put("system", List.of(new SettingsChromeMockTest.Probe("checkbox", 257, 112), new SettingsChromeMockTest.Probe("Edit Custom VM Options… edge", 756, 239)));
        PROBES.put("terminal.font", List.of(new SettingsChromeMockTest.Probe("fallback list box", 400, 196), new SettingsChromeMockTest.Probe("zebra row", 400, 225),
            new SettingsChromeMockTest.Probe("Ligatures checkbox", 257, 298)));
        PROBES.put(dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.indexer"), List.of(new SettingsChromeMockTest.Probe("projects table box", 251, 150),
            new SettingsChromeMockTest.Probe("projects header", 400, 107)));
        PROBES.put(dev.jasper.app.plugins.PluginRuntime.settingsPageId("dev.jasper.sample"), List.of(new SettingsChromeMockTest.Probe("first checkbox", 257, 112)));
```

- [ ] **Step 2: Run, calibrate and record**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --continue`

Expected: PASS: `SettingsPagesMockTest` (8). For each failure open its difference image and fix by cause as in Task 1
Step 4: a geometry difference shared by several pages (box edges, row heights, section gaps, trailing controls) is a
classic Settings role (`SETTINGS_*`, `SETTING_*`, `FULL_SETTING_*`, both tables) or plan 4b's face value, corrected once
and re-checked against every page; a page-only difference in a plugin page's own composition is plan 10's, recorded as a
residual with its page's floor; fixture text is a residual. Record every lowered floor and its cause in the banner.

- [ ] **Step 3: Prove the default look and commit**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
git add jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsPagesMockTest.java jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/classic-roles.tsv
git commit -F - <<'EOF'
Settings pages: the other twelve pages against their mocks

SettingsPagesMockTest now compares every 05 Settings page under the
Enterprise look with its mock, probes on every OS and the body's pixel
share on macOS, and checks that the composed pages follow Enterprise Dark.
The classic Settings values the comparison settled are in the catalog.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected before the commit: the compare script lists only Task 5's recorded `plugins*` files, if any.

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/configuration.md:78-95` (Settings' Keymap and Plugins sentences), `docs/app-architecture.md:166,182-183`, `docs/STATUS.md` (new entry at the top), this plan's banner

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: Edit the documents**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 9b Task 8 step 1: the smallest accurate documentation edits (spec 17)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [
    ('''| `shortcuthelp` | The Settings dialog's Keymap page: effective shortcut catalog, expandable searchable sections, staged binding edits saved on Apply and a window-scoped key recorder.''',
     '''| `shortcuthelp` | The Settings dialog's Keymap page: effective shortcut catalog, expandable searchable sections, staged binding edits saved on Apply and a window-scoped key recorder; `KeymapPage` maps the look's composition to a `KeymapShape` (columns, text tools, the recording band).''', 1),
    ('''| `pluginmanager` | The Settings dialog's Plugins page: passive view, consent view and an EDT controller over PluginRuntime's public records.''',
     '''| `pluginmanager` | The Settings dialog's Plugins page: passive view, consent view and an EDT controller over PluginRuntime's public records; `PluginsPage` maps the look's composition to a `PluginShape` (checkbox rows, ⋯ actions).''', 1)])
PY
grep -n "Keymap lists every shortcut" -A3 docs/configuration.md
```

In `docs/configuration.md`, after the sentence that begins "Keymap lists every shortcut", add in its voice: "Under the
Enterprise look the Keymap shows Action, Scope and Shortcut columns, with Find by Shortcut, Expand All and Collapse All;
Plugins lists checkbox rows, with each plugin's actions in its ⋯; Terminal › Shell lists environment variables in a table
with Add Variable…, and Edit… and Remove in a variable's context menu." Keep it one paragraph addition; add nothing else.

- [ ] **Step 2: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count the results with plan 9a Task 7 Step 2's script.

- [ ] **Step 3: Prove the default look once more**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-app:settingsPreview
python3 -I build/plan9-base/compare.py
```

Expected: only Task 5's recorded `plugins*` files, if any.

- [ ] **Step 4: Write the STATUS entry and the banner**

Add at the top of `docs/STATUS.md`, above plan 9a's entry, in its style (fill the bracketed values):

```markdown
**Enterprise presentation, plan 9b: the Settings page compositions — 2026-10-16** (`claude/enterprise-settings`, worktree
`.claude/worktrees/enterprise-9`, `[first]..[last]`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-9b-settings-pages.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Spec 12.2 under `Composition.CLASSIC`,
each composition branching once at its root: the Appearance theme gears are ⋯ menus; Notifications' groups are one table
(Group, Display, Source; a heading per owner with "N groups", a borderless Display dropdown, Default or Custom, Reset to
Default in each row's menu), the classic table face putting a heading's count under the last column; Keymap has Action,
Scope and Shortcut columns on lanes measured from the mock (`KEYMAP_*`), the text tools Find by Shortcut, Expand All and
Collapse All, and an inline recording band the tree face paints (rowHover, faint accent edges) with the recorder drawn as
a field by the classic button face; Plugins is a boxed list of checkbox rows under banded headings (the table face's list
headings), with the plugin's ⋯ beside Disable and Overview | Details as TABS in both looks; Shell's environment variables
are a Name / Value table with Add Variable… and a sheet (`PageContext.sheets()`), Edit… and Remove in the row menu, and
the ordered lists read "Empty". `SettingsPagesMockTest` compares all eighteen pages and 07b with their mocks (probes on
every OS, the body's share on macOS: [shares and lowered floors with causes]) and checks the composed pages under
Enterprise Dark. Default look: byte-identical but [the plugins* files, or "none"] (Rulings 2). Rulings (plan, "Rulings"):
[the sixteen, one line each]. Deviations from the plan's text: [list or "none"]. `./gradlew check --continue`: **[n]
tests, 0 failures, 0 errors, [n] skipped**. GUI checks for the user: under Enterprise, the Keymap's recording band and
Find by Shortcut, the Plugins page's checkboxes and ⋯, Add Variable… and its sheet, and a right-click on a Notifications
row. Not merged, not pushed.
```

Update this plan's banner: "Executed on 2026-10-16: [first]..[last]; floors [list]; deviations [list or none]."

- [ ] **Step 5: Commit**

```bash
git add docs/app-architecture.md docs/configuration.md docs/STATUS.md docs/superpowers/plans/2026-10-16-enterprise-9b-settings-pages.md
git commit -F - <<'EOF'
Settings pages: documentation and status

The architecture guide names the Keymap and Plugins shapes, the
configuration guide the classic Keymap, Plugins and environment table, and
STATUS records plan 9b with its mock shares, floors and the whole build's
counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
