# Enterprise presentation 3b: SDK 0.18 controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SDK 0.18 gains the first group of spec 8.2's additions: `Nodes.Notice`, `Nodes.Properties` with
`PropertyRow`, `Nodes.Separator`, `ChoiceKind.TABS`, `Field.readOnly` (replacing the host-only
`KitView.setFieldReadOnly`), `PickerFace.technicalCaption`, `EditorHint.technical()`, `IconSize.SWATCH`,
`Form.footnote` and the `ViewSpec` rule that an EXCLUSIVE choice has 2 to 4 options, each mirrored in the jasper-ui
model, projected losslessly by `KitValues`, validated by `ViewSpec`, served by the testkit fake, pinned by the shared
contract suites and drawn by the default look, with every existing gallery pixel unchanged.

**Architecture:** Each addition is one task that changes the SDK record or enum and its jasper-ui mirror together
(after 3a the two `Nodes.java` and `ViewSpec.java` files differ only in the package and SDK-only Javadoc, and every
edit anchors on text present in both, or for TABS and SWATCH on the SDK's 3a Javadoc, so one edit script edits both),
adds the `KitValues` mapping, the default-look renderer in `dev.jasper.ui.swing` (new package-private
`Separators`, `Notices` and `PropertyLists` classes, the rest small changes to existing controls), the testkit fake's
part and a new headless gallery fixture appended after the existing 66, so every existing fixture keeps its pixels.
New nodes reuse existing metric roles and spec 7.1's derived roles; the pairs they paint are declared with guards in
`PaintedContrastTest`, as plan 2 placed the default look's other pairs. One task adds the cross-cutting contract
cases; the last updates the guides and `docs/STATUS.md`, proves the gallery unchanged and runs the whole build.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 8, 16 and 19; 19.1 defines this plan: "3b `sdk-0-18-controls` (Notice, Properties, `ChoiceKind.TABS`,
`Field.readOnly`, `PickerFace.technicalCaption`, `EditorHint.technical()`, Separator, `IconSize.SWATCH`,
`Form.footnote` and the EXCLUSIVE validation)"; also 6.4, 7.1, 7.3 and 13 for what the default look draws and what
3e changes). Plans 1, 2 and 3a (SDK 0.18 renames,
[plan](2026-10-15-enterprise-3a-sdk-0-18-renames.md)) are merged on `main` (3a as `deafed424`). Plan 3d executes in
parallel in another worktree and 3c follows; the files both touch are reconciled when the second of them merges.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-3b`, branch
`claude/enterprise-sdk-0-18-controls`. The branch stands on `main` at `deafed424` (3a merged) and holds only this
plan's commits (`5c0a2f374`, `41a3d9c38` and the anchor refresh); do not rebase or merge it. Before Task 1, confirm:

```bash
git merge-base --is-ancestor deafed424 HEAD && echo ok   # expect ok
git log --oneline deafed424..HEAD | grep -vc '^[0-9a-f]* Plan'   # expect 0: only the plan's own commits
grep -c 'ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java  # expect 1
```

Run every command from the worktree root, never in `~/projects/moray`. Every quoted anchor below is in its post-3a
form (`EXCLUSIVE`, `HELP`, `PROMPT`, `PROMPT_COMPACT`, `PROMPT_HEADER`, `PROMPT_TITLE`, `IconSize.PROMPT`,
`JasperSdk.VERSION` 0.18.0). The edit scripts replace text, not line numbers, and stop with a message when their text is
not found the stated number of times; line numbers in **Files** and **Verified facts** were re-read at `deafed424` on
2026-10-16 (see "Execution notes").

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-sdk-0-18-controls`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Scope is spec 8.2's rows for Notice, Properties (with PropertyRow), `ChoiceKind.TABS`, `Field.readOnly`, `PickerFace.technicalCaption`, `EditorHint.technical()`, Separator, `IconSize.SWATCH`, `Form.footnote` and "An EXCLUSIVE choice has 2 to 4 options". Tree columns, cell nodes, heading rows and empty tables are 3c's; SESSION/TRANSFER, toolbar views, scope hues, nouns, verb roles and `DialogSpec.swatch` are 3d's; the plugin adoptions of spec 13 are 3e's. The one exception is Task 3's File Server change, which the new EXCLUSIVE rule forces (Rulings 1).
- SDK 0.18.0 stays the version (`JasperSdk.VERSION`, every manifest's `sdk = ">=0.18.0, <0.19"`): every change is additive. Each record keeps its former canonical constructor as a convenience with the new component at its empty value (spec 8.2, "Existing record constructors stay as conveniences"); no member is removed, so `KitCompatibility` is unchanged.
- The jasper-ui model mirror keeps the SDK's code but for the package (SDK-only Javadoc aside): `KitValues` maps enums by name and `KitValuesTest` compares `toString()` of both trees, so component order and names match exactly.
- Pixel rule: every file the headless gallery wrote before this plan is byte-identical after it, except `manifest.json` (its `fixtureCount` grows from 66 to 74) and the last contact sheet `sheet-9.png` (it held two fixtures and now holds eight). The eight new fixtures are appended after `picker-menu`, in task order: `read-only-fields`, `technical-captions`, `tabs`, `swatches`, `separators`, `notice-severities`, `properties`, `form-footnote` (16 files, plus the new `sheet-10.png`). `StateFixtures`' enumerating sheets (`controls`, `icons-status`) skip `ChoiceKind.TABS` and `IconSize.SWATCH` to keep their pixels. Task 10 proves this.
- No new `DesignMetrics` role and no type-scale change: new renderers use existing roles (`NOTICE_*`, `ICON_*`, `CARD_PADDING`, `HAIRLINE`, `ROW_GAP`, `COLUMN_GAP`, `SECTION_LABEL_GAP`, `CONTROL_RADIUS`), so `compact-roles.tsv` and `default-type.tsv` are unchanged.
- Colours come from the theme only: semantic roles or spec 7.1's derived roles (`DerivedColor`), and every pair a new renderer paints on a known surface is held by a `SurfaceContrast.Guard` and declared in `PaintedContrastTest`. A glyph tinted on a surface through `IconRegistry.icon(name, surface, foreground, size)` (the Notice's) is held to 3:1 by the registry's `surfaceTint`, the module rule for surface-tinted artwork, and is not declared.
- Javadoc doclint fails the build on errors: the SDK and jasper-app run `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`. New public SDK members say "since 0.18.0".
- `ThemeMigrationCoverageTest` needs a `docs/design/theme-engine/migration.csv` row for every new candidate source under jasper-app, the SDK, the testkit or plugins. This plan creates new files only under `jasper-ui/src` (outside the ledger's roots) and edits existing files elsewhere, so it adds no row; an executor who creates a new file under those roots adds its row in the same commit.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file. The middle dot, em dash and ellipsis used in copy are ordinary characters.
- The identifier word "hero" is reserved for `Form.hero` (3a's `PromptVocabularyTest`); no new identifier uses it.

## Review Focus

1. A toolbar row whose items on one side of a `Separator` are hidden (`ControlState.visible == false`, as SFTP hides
   actions while disconnected): no rule dangles at an edge or doubles up; the separator shows again when the items do.
   Task 5: `SeparatorsTest.aSeparatorWithNoShownItemOnOneSideIsHidden`.
2. A New File Server editor offered one protocol (`Set.of(Protocol.HTTP)`), or switched to TFTP (one login): the
   editor opens and updates instead of throwing at `ViewSpec`, and the fact reads as a read-only field. Task 3:
   `ServerCompositionTest` (both cases).
3. A person trying to change a read-only value by every route (typing, Delete, a NUMBER field's arrow keys, the
   host-typing hook of the contracts): nothing changes and no `Edited` is reported, while selection and copy keep
   working. Task 1: `ReadOnlyFieldsTest` and `KitContractTest.aReadOnlyFieldTakesNoTypingAndKeepsItsSelection`.
4. A property value wider than its column (a long path in a narrow tool window): it is shortened in place, a data value
   from its start so its end stays visible, and its whole text is the tooltip. Task 7:
   `PropertiesTest.aLongValueIsShortenedAndKeepsItsWholeTextAsItsTooltip`.
5. A plugin settings panel whose root `Form` has a footnote (and FormSubmission.NONE), passing through the host rows'
   projection: both survive. Task 8: `PluginPanelPageTest.aPanelFormsFootnoteSurvivesTheHostRowProjection` and
   `FakePluginSettingsTest.aPanelFormsFootnoteSurvivesTheRowProjection`.

## Rulings

Decisions where the spec is silent. Record them in `docs/STATUS.md` (Task 10).

1. **File Server's one-option choices become read-only fields here.** The EXCLUSIVE rule (2 to 4 options) refuses
   TFTP's one-option login choice (`ServerEditor.java:379-381,417`) and a protocol choice when only one protocol is
   offered (`ServerEditor.java:123-127,395`, exercised by `ServerCompositionTest.java:67`). Task 3 shows each as a
   read-only TEXT field in the choice's place (the login one EMBEDDED, as the choice was unlabelled), which is spec 13's
   TFTP item; 3e keeps the rest of spec 13 and must not redo it.
2. **Where Notice and Separator stand.** `ViewSpec` refuses a Notice anywhere but a form's `sections` or a VERTICAL
   stack, and a Separator anywhere but an INLINE or ACTIONS stack; a view whose root is either is refused too.
3. **List rows take neither Separator nor Notice nor Properties.** The `ListItem` inline whitelist is unchanged
   (its `default` already refuses them; Task 5 and 6 pin it); SWATCH icons and TABS choices pass as the Icon and Choice
   types they are. A row's INLINE stack is a row slot, not a toolbar.
4. **A Separator with no shown item on one side is not shown**, and nor is a second one with no shown item between.
   The default look draws a 1 px rule in `border.subtle`, as tall as a control glyph, centred in its row.
5. **Notice words and default look.** INFO, WARNING and DANGER read "Information", "Warning" and "Error"; the glyphs
   are `info`, `warning` and `error`. The default look draws a rounded (`CONTROL_RADIUS`) box on the severity's derived
   ground (`info.ground`, `warning.ground`, `danger.ground`), its border (`*.border`) guarded to 1.3:1 against the
   ground (plan 2's hand-off: each Catppuccin flavour's `warning.border` equals its `warning.ground`), the line "Word ·
   text" in `text.primary` guarded to 4.5:1 on the ground and wrapping, the glyph tinted with the severity colour
   through `IconRegistry.surfaceTint` (3:1), and the action at the trailing edge. `PaintedContrastTest` declares the
   text and border pairs; the glyph is held by the registry's surface tint and is not declared.
6. **Properties' default look** is a section-label heading (omitted when blank) over a card on `surface.card` with a
   label column as wide as the widest label; values are one line, BODY or MONO (technical, shortened at the start),
   with the whole value as the tooltip; a severity colours the value through the text guard against `surface.card`. A
   label that ends with a colon is refused; a colon inside a label is not.
7. **`Field.readOnly`.** A STEPPER or SLIDER is never read-only (refused); a read-only NUMBER ignores its arrow keys;
   a read-only PATH keeps its Browse action (the owner sets the value). The default look draws a read-only field as
   today's host-only read-only field: the same surface, no caret. The fake drops an `Edited` addressed to a read-only
   field and gains `FakeKitView.type(id, text)`, which also ignores hidden, disabled and busy fields.
8. **TABS** must have two or more options (checked in `ViewSpec` with the EXCLUSIVE count); the default look is the
   segment track without a visible label (the label stays the accessible name).
9. **SWATCH** takes no severity; its square is `ICON_CAPTION` wide in the hue's glyph colour, painted as authored
   (plan 4's faces add the indicator guard where they know the surface), whatever the composition (a tile slot or a
   selected row keeps the square and its colour).
10. **`Form.footnote`** is one line (a line break is refused) and excludes a preview (both own the footer's leading
    edge). The default look draws it in CAPTION, `text.tertiary`, in the preview's place; in a PROMPT form, whose
    actions span the footer, it takes the line above them.
11. **`PickerFace.technicalCaption`** draws the caption in `MONO_CAPTION` at both densities.
12. **`EditorHint.technical()`** applies to text settings; other kinds ignore it, as `EditorHint.Directory` is
    ignored today. Generated pages use the new `Editors.technical()`; host rows and the testkit's rows use
    `FieldPresentation.TECHNICAL`.
13. **The testkit's row projection keeps `Form.submission`** as well as the footnote: it rebuilt forms with the
    nine-argument constructor and dropped the submission (`KitSettingRows.java:97`); the host's projection keeps both.
14. **Contract cases.** Spec 16.1.9 names the new cases in `PluginContractTest`; the plugin-facing admission and
    refusal case is there (Task 9), and the per-view behaviour (round trip, events, typing into read-only fields) is in
    `KitContractTest`, which both the host and the fake run, with a new `type` hook.

## Verified facts

Re-read at `deafed424` (post-3a) on 2026-10-16. A bare `Nodes.java:N` is the jasper-ui model's line; the SDK's, lower
by 3a's Javadoc, follows as "SDK `:N`".

1. `Nodes.Node` permits 28 types (`jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:12`); at `222969926` the
   jasper-ui model `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java`, `ViewSpec.java` and `ListItem.java` are
   byte-identical to the SDK's but for the package. After 3a (its Task 4) the SDK's `Nodes.java` also carries SDK-only
   Javadoc on `TextRole`, `IconSize`, `ButtonKind`, `ChoiceKind`, `ListKind`, `FormKind`, `ScrollPolicy`,
   `ButtonPresentation`, `FieldPresentation`, `PickerDensity`, `Toggle`, `FormRow`, `Section` and `Empty`, so a `diff`
   with the package normalised shows those comments only (`ViewSpec.java` and `ListItem.java` are identical but for the
   package); every edit here anchors on text present in both files (or, for TABS and SWATCH, on the SDK's 3a
   `ChoiceKind` and `IconSize` Javadoc).
2. `IconSize` is `{ CAPTION, CONTROL, RAIL, EMPTY, PROMPT }` (`Nodes.java:18`, SDK `:20` under 3a's one-line Javadoc);
   `ChoiceKind` is `{ DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }` (`:22`, SDK `:26` under 3a's one-line
   Javadoc); `Severity` is `{ INFO, WARNING, DANGER }` (`:82`, SDK `:90`).
3. `Field` has 12 components and three conveniences (`Nodes.java:201-231`, SDK `:211-241`); `PickerFace` has 5
   (`:290-292`, SDK `:300-302`); `Form` has 10 with two conveniences (`:555-580`, SDK `:568-593`); `Icon` checks only
   non-null hue and size (`:85-97`, SDK `:93-105`); `Choice` validates face, icon and search but not option counts
   (`:309-338`, SDK `:319-348`).
4. `ViewSpec.validate` is one exhaustive switch whose last case lists the leaves (`ViewSpec.java:27-67`, `:64`);
   `ListItem.inline` admits Text, Icon, Chip, StatusDot, Progress, Strength, Button, MenuButton, Toggle, Choice and INLINE
   stacks of them and refuses anything else (`ListItem.java:42-51`).
5. `KitValues.toModule` is an exhaustive switch over the SDK nodes (`jasper-app/.../plugins/KitValues.java:7-36`); Field
   at `:13`, Form at `:30`, PickerFace at `:47`.
6. `ControlRegistry` dispatches with exhaustive switches: `validate` (`ControlRegistry.java:13-23`), `create`
   (`:24-55`); `children` (`:56-73`) and `kind` (`:74-84`) default. `KitContent.children` (app, `KitContent.java:24`) and
   `FakeKitView.children` (testkit, `FakeKitView.java:101-118`) repeat `children`, and `KitIconNames` walks them.
7. `KitView.setFieldReadOnly` (`KitView.java:163-166`) sets `TextFields.FieldControl.readOnly` (`TextFields.java:27`),
   reset on close (`:89`) and applied as `input.setEditable(component.isEnabled() && !readOnly)` (`:254`); its only
   production caller is `ShortcutPanel.java:181` (the Keymap search while recording), tested by `FieldTrailingTest.java:38-54`
   and `ShortcutEditingTest.java:364,368`. A NUMBER field steps on its arrow keys with `input.setText` (`TextFields.java:201-211`).
8. `Choices.ChoiceControl` draws SEGMENTED/EXCLUSIVE (any kind not DROPDOWN, SEARCHABLE or PICKER) as a `SegmentTrack`
   (`Choices.java:68`); its label shows unless embedded (`:30`, `:162`); `Cards` hides an EXCLUSIVE choice's label in a
   card (`Cards.java:50`); `ItemLists.inlineHeight` switches exhaustively on `ChoiceKind` (`ItemLists.java:282-305`).
9. `PickerMenus.paintFace` draws a rich face's caption through `line(...)` in CAPTION or PICKER_CAPTION
   (`PickerMenus.java:22-38`, `line` at `:51-56`); popup rows call `line` too (`:146`).
10. `Labels.IconControl.refresh` picks a glyph, tile or prompt rendering (`Labels.java:165-187`); `IconRegistry.size`
    switches exhaustively on `IconSize` (`IconRegistry.java:226-234`); hue tints are `HUE_*`, NONE `TEXT_SECONDARY`
    (`:306-312`); `icon(name, Color surface, Color foreground, size)` holds a glyph to 3:1 on a surface (`:155-170`).
11. `Stacks.StackControl.children` mounts the children (`Stacks.java:130-160`, `mount` at `:147`); horizontal
    `StackLayout` roles are INLINE, ACTIONS, FOOTER, NAVIGATION, SETTINGS_CONTROLS, JOINED_ACTIONS (`:545-550`) and a
    child's height is centred in the row (`:700-710`).
12. `Forms.FormControl` holds a `preview` panel at the footer's leading edge (`Forms.java:24-25`, mount `:296-301`,
    visibility `:309`); `FooterLayout` keeps a non-technical preview beside the actions when it fits and stacks it
    above them otherwise, and PROMPT forms' actions take the whole footer width (`:458-560`).
13. `DesignMetrics` already has `NOTICE_VERTICAL_PADDING(8)`, `NOTICE_HORIZONTAL_PADDING(12)`, `NOTICE_BUTTON_GAP(8)`
    (`DesignMetrics.java:93`, used by `TerminalPane.java:222-225`), `ICON_CAPTION(12)`, `ICON_CONTROL(14)` (`:62`) and
    `HAIRLINE(1)` (`:81`).
14. `DerivedColor` lists `INFO_GROUND`, `INFO_BORDER`, `WARNING_GROUND`, `WARNING_BORDER`, `DANGER_GROUND`,
    `DANGER_BORDER` (`DerivedColor.java:10-33`); `ColorRole.color(theme)` resolves either kind (`ColorRole.java:14-19`);
    `SurfaceContrast.painted(theme, PaintPair)` applies a pair's guard (`SurfaceContrast.java:92-95`); `Guard.TEXT`
    and `Guard.STRUCTURAL` exist (`:48-54`).
15. The gallery's fixtures are listed in `KitGallery.fixtures()` (`KitGallery.java:37-68`); `main` writes a PNG and a
    JSON per fixture, contact sheets of eight (`:284-298`) and `manifest.json` with `fixtureCount` (`:109`): 66
    fixtures and 142 files per theme today (plan 2's STATUS entry). A fixture's JSON lists mounted kit-owned components
    only (`:246-256`). `StateFixtures` enumerates every `IconSize` (`StateFixtures.java:20`) and every `ChoiceKind`
    (`:29`).
16. `GalleryCoverageTest.everyNodeAndEnumVariantHasARealFixture` requires every node type and every `Nodes` enum
    constant in a fixture (`GalleryCoverageTest.java:13-21`); `KitMatrixTest` renders every fixture over the theme set
    and looks for foreign literals under `custom()` (`KitMatrixTest.java:27-46`).
17. `PaintedContrastTest` declares the default look's guarded pairs with samplers (`PaintedContrastTest.java:46-62`)
    over the theme set and the degenerate palettes (`:65-80`).
18. `KitValuesTest.everyNodeAndSupportFieldProjectsWithoutLoss` builds a `ViewSpec` around each listed node and counts
    the listed classes against `Node`'s permitted subclasses (`jasper-app/.../plugins/KitValuesTest.java:12-36`).
19. `KitContractTest` (testkit `testFixtures`) is abstract over `kit`, `emit`, `secret`, `owns`, `stop`, `fieldText` and
    `fieldSelection` (`KitContractTest.java:12-18`), implemented by `HostedKitContractTest` (app) and
    `FakeKitContractTest` (`FakeKitTest.java:36-44`); `PluginContractTest` runs on both through `ContractHarness.ui`
    (`PluginContractTest.java:120-131`).
20. `FakeKitView.emit` records an `Edited` of a field as its text and delivers it (`FakeKitView.java:91`).
21. `EditorHint` has `Font`, `Directory` and `File` (`EditorHint.java:4-39`); `PluginSettingsPage.Hint` maps them for
    generated pages (`PluginSettingsPage.java:102-127`, used by `editor` at `:77-88`); host rows pick the field
    presentation at `KitSettingRows.java:46-51` (app) and `:36-44` (testkit); `Editors.Line` draws a TEXT field
    (`Editors.java:312-316`).
22. The app's host-row projection rebuilds forms keeping the submission (`jasper-app/.../KitSettingRows.java:139`); the
    testkit's drops it (`jasper-sdk-testkit/.../KitSettingRows.java:97`).
23. `ServerEditor` builds TFTP's login as a one-option choice ("No login (TFTP has none)", `ServerEditor.java:377-386,417`)
    and the protocol choice from the offered protocols, which can be one (`:123-127,395-398`).
24. `docs/plugin-ui.md` lists the controls (`:55-60`) and other building blocks (`:173-185`) and describes the testkit
    (`:207-212`); `docs/plugin-authoring.md:608-612` lists the editor hints.

## File structure

- `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java`: the
  records and enums (identical edits).
- `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java`, `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java`:
  placement, separator neighbours and choice counts (identical edits).
- `jasper-sdk/src/main/java/dev/jasper/sdk/settings/EditorHint.java`: `Technical`.
- `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java`: the projections.
- `jasper-ui/src/main/java/dev/jasper/ui/swing/`: new `Separators.java`, `Notices.java`, `PropertyLists.java`; changes in
  `ControlRegistry`, `TextFields`, `KitView`, `Choices`, `ItemLists`, `PickerMenus`, `Labels`, `Stacks`, `Forms`;
  `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java` (`size`).
- Host settings: `PluginSettingsPage.java`, `KitSettingRows.java` (app), `Editors.java`; testkit `KitSettingRows.java`.
- Testkit: `FakeKitView.java`; contracts `KitContractTest.java`, `PluginContractTest.java`; implementations
  `HostedKitContractTest.java`, `FakeKitTest.java`.
- App: `ShortcutPanel.java` (descriptor read-only); plugin: `ServerEditor.java` (Rulings 1).
- Gallery: `KitGallery.java` (eight fixtures), `StateFixtures.java` (skip the new constants), `GalleryCoverageTest.java`.
- New jasper-ui tests: `ReadOnlyFieldsTest`, `TechnicalCaptionsTest`, `TabsTest`, `SwatchesTest`, `SeparatorsTest`,
  `NoticesTest`, `PropertiesTest`, `FootnotesTest` (all `dev.jasper.ui.swing`).

---
### Task 1: `Field.readOnly`

A field can show a value as a fact: focusable, selectable and copyable, never edited, so it reports no `Edited`. The
SDK and model `Field` gain `boolean readOnly` (the 12-component constructor stays as the editable convenience); a
STEPPER or SLIDER is never read-only. `KitValues` carries it, `TextFields` reads it from the descriptor (a read-only
NUMBER also ignores its arrow keys), and the host-only `KitView.setFieldReadOnly` goes: the Keymap search's recording
state moves into its descriptor. The fake drops an `Edited` addressed to a read-only field and gains
`FakeKitView.type(id, text)`; `KitContractTest` gains the matching `type` hook and a read-only case. One gallery
fixture shows two read-only fields.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:203-241`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:193-231` (Field)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:13`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java:27,89,206,254`, `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:163-166` (removed)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutPanel.java:181,190-193`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java:90-92`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java:18,39` (hook and case)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java:16`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java:19,43`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java:67` (fixture `read-only-fields`)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/FieldTrailingTest.java:12-14,38-54`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ReadOnlyFieldsTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`

**Interfaces:**
- Consumes: post-3a `main`.
- Produces: `Nodes.Field(String id, ControlState state, FieldKind kind, String value, String hint, String label,
  String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation, String alternateAction,
  String cancelAction, String listId, boolean readOnly)` and `boolean readOnly()` in the SDK and model;
  `public void FakeKitView.type(String fieldId, String text)`; `protected abstract void KitContractTest.type(UiView
  view, String id, String text)`. Task 3 builds read-only fields with the 13-argument constructor.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ReadOnlyFieldsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.event.ActionEvent;
import java.util.*;
import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code Field.readOnly}: focusable, selectable and copyable, never edited, so it reports no Edited. */
@SuppressWarnings("try")
class ReadOnlyFieldsTest {
    static Field readOnlyField(String id, FieldKind kind, String value, boolean readOnly) {
        return new Field(id, ControlState.READY, kind, value, "", "Login", "",
            kind == FieldKind.NUMBER ? Optional.of(new Numeric(0, 10, 1, 0)) : Optional.empty(), FieldPresentation.STANDARD, "", "", "", readOnly);
    }

    @Test void aReadOnlyFieldKeepsFocusSelectionAndCopyButTakesNoEdit() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            String fact = "No login (TFTP has none)";
            try (var view = new KitView(context, new ViewSpec(readOnlyField("login", FieldKind.TEXT, fact, true)), events::add)) {
                var input = find(view, "login.input", JTextField.class);
                assertThat(input.isEditable()).isFalse();
                assertThat(input.isEnabled()).isTrue();
                assertThat(input.isFocusable()).isTrue();
                assertThat(input.getAccessibleContext().getAccessibleStateSet().contains(javax.accessibility.AccessibleState.EDITABLE)).isFalse();
                new DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "x"));
                input.getActionMap().get(DefaultEditorKit.deletePrevCharAction).actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "delete"));
                view.selectAll("login");
                assertThat(input.getSelectedText()).isEqualTo(fact);
                assertThat(input.getText()).isEqualTo(fact);
                assertThat(events).isEmpty();
                var document = input.getDocument();
                view.update(new ViewSpec(readOnlyField("login", FieldKind.TEXT, fact, false)));
                assertThat(input.getDocument()).isSameAs(document);
                assertThat(input.isEditable()).isTrue();
                new DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "x"));
                assertThat(events).isNotEmpty().last().isEqualTo(new UiEvent.Edited("login", "x"));
            } finally { release(context); }
        });
    }

    @Test void aReadOnlyNumberIgnoresItsStepKeys() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(readOnlyField("count", FieldKind.NUMBER, "5", true)), events::add)) {
                var input = find(view, "count.input", JTextField.class);
                for (String key : List.of("number.step.UP", "number.step.DOWN"))
                    input.getActionMap().get(key).actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, key));
                assertThat(input.getText()).isEqualTo("5");
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void stepperAndSliderAreNeverReadOnlyAndFieldsDefaultToEditable() {
        for (var presentation : List.of(FieldPresentation.STEPPER, FieldPresentation.SLIDER))
            assertThatIllegalArgumentException().isThrownBy(() -> new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "N", "",
                Optional.of(new Numeric(0, 10, 1, 0)), presentation, "", "", "", true)).withMessageContaining("never read-only");
        assertThat(new Field("t", ControlState.READY, FieldKind.TEXT, "", "", "T", "").readOnly()).isFalse();
    }
}
```

Then run from the worktree root (the SDK and model record tests, the projection, the contract hook and case, the
fake's test, the host's rewritten test and the gallery fixture):

```bash
python3 -I - <<'PY'
"""Plan 3b Task 1 step 1: the tests that pin Field.readOnly."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
RECORD = r'''    /** Plan 3b: fields are editable unless they say so, and a stepper or slider is never read-only. */
    @Test void readOnlyFieldsAreValidatedAndDefaultToEditable() {
        assertThat(new Field("name", ControlState.READY, FieldKind.TEXT, "", "", "Name", "").readOnly()).isFalse();
        assertThat(new Field("find", ControlState.READY, FieldKind.SEARCH, "", "", "Find", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "").readOnly()).isFalse();
        assertThat(new Field("count", ControlState.READY, FieldKind.NUMBER, "1", "", "Count", "", Optional.of(new Numeric(0, 9, 1, 0)),
            FieldPresentation.STANDARD, "", "", "", true).readOnly()).isTrue();
        for (var presentation : List.of(FieldPresentation.STEPPER, FieldPresentation.SLIDER))
            assertThatIllegalArgumentException().isThrownBy(() -> new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "N", "",
                Optional.of(new Numeric(0, 9, 1, 0)), presentation, "", "", "", true)).withMessageContaining("never read-only");
    }
'''
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", RECORD + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", RECORD + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (" @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){\n",
     r''' /** Plan 3b: a read-only field crosses the projection unchanged. */
 @Test void readOnlyFieldsProjectWithoutLoss(){
  for(boolean readOnly:List.of(false,true)){
   var spec=new ViewSpec(new Field("login",READY,FieldKind.TEXT,"No login (TFTP has none)","","Login","",Optional.empty(),FieldPresentation.STANDARD,"","","",readOnly));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
   assertThat(((dev.jasper.ui.model.Nodes.Field)KitValues.toModule(spec).root()).readOnly()).isEqualTo(readOnly);
  }
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
''', 1)])
CONTRACT = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java"
edit(CONTRACT, [
    ("    protected abstract int[] fieldSelection(UiView view,String id);\n",
     "    protected abstract int[] fieldSelection(UiView view,String id);\n"
     "    /** Types {@code text} into field {@code id} as a person would, replacing its text; a read-only, hidden or disabled field takes nothing. */\n"
     "    protected abstract void type(UiView view,String id,String text);\n", 1),
    ("    @Test void windowPlainPathAndDiscreetInputRetainTheAdditiveContract() throws Exception { edt(()->{\n",
     r'''    /** Spec 8.2: a read-only field is focusable, selectable and copyable but takes no typing; an editable one reports it. */
    @Test void aReadOnlyFieldTakesNoTypingAndKeepsItsSelection() throws Exception { edt(() -> {
        var kit = kit("owner"); var events = new ArrayList<UiEvent>();
        var view = kit.create(readOnlyFields(true), events::add);
        try {
            type(view, "locked", "typed"); type(view, "shown", "typed");
            assertThat(events).isEmpty();
            view.selectAll("shown");
            assertThat(fieldSelection(view, "shown")).containsExactly(0, "No folder selected".length());
            view.update(readOnlyFields(false));
            assertThat(view.spec()).isEqualTo(readOnlyFields(false));
            type(view, "locked", "typed");
            assertThat(events).containsExactly(new UiEvent.Edited("locked", "typed"));
        } finally { view.close(); stop(kit); }
    }); }
    private static ViewSpec readOnlyFields(boolean locked) {
        return new ViewSpec(new Nodes.Stack("root", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Field("locked", ControlState.READY, Nodes.FieldKind.TEXT, "", "", "Login", "", Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", "", locked),
            new Nodes.Field("shown", ControlState.READY, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", "", true))));
    }

    @Test void windowPlainPathAndDiscreetInputRetainTheAdditiveContract() throws Exception { edt(()->{
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java", [
    (" protected void stop(UiKit kit){((HostedKit)kit).close();}\n",
     " /** As a person types: the text component's key-typed action, which a read-only or disabled field ignores. */\n"
     " protected void type(UiView view,String id,String text){var field=input(((HostedKitView)view).component(),id+\".input\");field.selectAll();"
     "new javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new java.awt.event.ActionEvent(field,java.awt.event.ActionEvent.ACTION_PERFORMED,text));}\n"
     " protected void stop(UiKit kit){((HostedKit)kit).close();}\n", 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java", [
    (" protected void stop(dev.jasper.sdk.ui.UiKit kit){((FakeKit)kit).close();}\n",
     " protected void type(dev.jasper.sdk.ui.UiView view,String id,String text){((FakeKitView)view).type(id,text);}\n"
     " protected void stop(dev.jasper.sdk.ui.UiKit kit){((FakeKit)kit).close();}\n", 1),
    (" @Test void contextFailedStartAndStopOwnKitAndSemanticRefresh() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {\n",
     r''' /** Plan 3b: the fake takes no typing in a read-only field, or in one a hidden container holds, as the host does. */
 @Test void aReadOnlyOrHiddenFieldTakesNoTypingInTheFake() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
  var kit=new FakeKit("sample");var events=new java.util.ArrayList<dev.jasper.sdk.ui.UiEvent>();var ready=dev.jasper.sdk.ui.ControlState.READY;
  var locked=new dev.jasper.sdk.ui.Nodes.Field("locked",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"x","","Locked","",java.util.Optional.empty(),dev.jasper.sdk.ui.Nodes.FieldPresentation.STANDARD,"","","",true);
  var open=new dev.jasper.sdk.ui.Nodes.Field("open",ready,dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,"","","Open","");
  var hidden=new dev.jasper.sdk.ui.Nodes.Stack("hidden",new dev.jasper.sdk.ui.ControlState(false,true,false,""),dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(open));
  var view=kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("root",ready,dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,java.util.List.of(locked,hidden))),events::add);
  view.type("locked","typed");view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("locked","typed"));view.type("open","typed");
  assertThat(events).isEmpty();
  assertThatIllegalArgumentException().isThrownBy(() -> view.type("root","typed"));
  kit.close();
 });}
 @Test void contextFailedStartAndStopOwnKitAndSemanticRefresh() throws Exception {javax.swing.SwingUtilities.invokeAndWait(() -> {
''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/swing/FieldTrailingTest.java", [
    ('''    private static ViewSpec spec(ControlState state) {return new ViewSpec(new Nodes.Stack("root",state,LayoutRole.INLINE,List.of(field("find",ControlState.READY),action("clear"))));}\n''',
     '''    private static ViewSpec spec(ControlState state) {return new ViewSpec(new Nodes.Stack("root",state,LayoutRole.INLINE,List.of(field("find",ControlState.READY),action("clear"))));}\n'''
     '''    /** The same view whose field says whether it is read-only (spec 8.2's Field.readOnly). */\n'''
     '''    private static ViewSpec spec(ControlState state,boolean readOnly) {return new ViewSpec(new Nodes.Stack("root",state,LayoutRole.INLINE,List.of(\n'''
     '''        new Field("find",ControlState.READY,FieldKind.SEARCH,"pending","Find","Search","",Optional.empty(),FieldPresentation.STANDARD,"","","",readOnly),action("clear"))));}\n''', 1),
    ('''view.setFieldTrailing("find",Optional.of(action("stop")));view.setFieldReadOnly("find",true);''',
     '''view.update(spec(ControlState.READY,true));view.setFieldTrailing("find",Optional.of(action("stop")));''', 1),
    ('''view.update(spec(ControlState.READY));assertThat(input.getDocument()).isSameAs(document);assertThat(input.getSelectedText()).isEqualTo("end");assertThat(input.isEditable()).isFalse();''',
     '''view.update(spec(ControlState.READY,true));assertThat(input.getDocument()).isSameAs(document);assertThat(input.getSelectedText()).isEqualTo("end");assertThat(input.isEditable()).isFalse();''', 1),
    ('''view.update(spec(new ControlState(true,true,true,"")));assertThat(stop.isEnabled()).isFalse();''',
     '''view.update(spec(new ControlState(true,true,true,""),true));assertThat(stop.isEnabled()).isFalse();''', 1),
    ('''view.update(spec(ControlState.READY));view.setFieldReadOnly("find",false);assertThat(input.isEditable()).isTrue();''',
     '''view.update(spec(ControlState.READY));assertThat(input.isEditable()).isTrue();''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): two read-only fields, a fact and a data value.
        result.add(new Fixture("read-only-fields", "", "normal", 400, 110, new ViewSpec(new Nodes.Stack("read-only", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Nodes.Field("read-only-login", ControlState.READY, FieldKind.TEXT, "No login (TFTP has none)", "", "Login", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "", true),
            new Nodes.Field("read-only-folder", ControlState.READY, FieldKind.TEXT, "/Users/me/Downloads", "No folder selected", "Folder", "", Optional.empty(), FieldPresentation.TECHNICAL, "", "", "", true))))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.ReadOnlyFieldsTest --continue`
Expected: FAIL. `:jasper-sdk:compileTestJava` and `:jasper-ui:compileTestJava` report "constructor Field in record
Field cannot be applied to given types" (13 arguments) and "cannot find symbol … readOnly()".

- [ ] **Step 3: Add the component and read it**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 1 step 3: Field.readOnly in the SDK, the model, the projection, the kit, the Keymap search and the fake."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [
        (r'''     * {@code listId}, and a NUMBER field (whose arrows step it) takes no {@code listId}.
     */
    public record Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                        String alternateAction, String cancelAction, String listId) implements Node {
''', r'''     * {@code listId}, and a NUMBER field (whose arrows step it) takes no {@code listId}. A {@code readOnly} field (since
     * 0.18.0) is focusable, selectable and copyable but never edited, so it reports no {@link UiEvent.Edited}; a STEPPER
     * or SLIDER is never read-only.
     */
    public record Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                        String alternateAction, String cancelAction, String listId, boolean readOnly) implements Node {
''', 1),
        (r'''            if (kind == FieldKind.NUMBER && !listId.isBlank()) throw new IllegalArgumentException("A number field's arrow keys step its value, not a list");
        }
''', r'''            if (kind == FieldKind.NUMBER && !listId.isBlank()) throw new IllegalArgumentException("A number field's arrow keys step its value, not a list");
            if (readOnly && (presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER))
                throw new IllegalArgumentException("A stepper or slider is never read-only");
        }
        /** An editable field. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                     String alternateAction, String cancelAction, String listId) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, presentation, alternateAction, cancelAction, listId, false);
        }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    ("n.alternateAction(), n.cancelAction(), n.listId());", "n.alternateAction(), n.cancelAction(), n.listId(), n.readOnly());", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java", [
    ("        boolean embedded, readOnly;\n", "        boolean embedded;\n", 1),
    ("            own(() -> { clearTrailing(); readOnly=false; });\n", "            own(this::clearTrailing);\n", 1),
    ("                    if (!interactive()) return;\n                    input.setText(NumberFilter.step(",
     "                    if (!interactive() || ((Nodes.Field)node).readOnly()) return;\n                    input.setText(NumberFilter.step(", 1),
    ("            input.setEditable(component.isEnabled() && !readOnly); surface.setEnabled(component.isEnabled());",
     "            input.setEditable(component.isEnabled() && !field.readOnly()); surface.setEnabled(component.isEnabled());", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java", [
    (r'''    /** App-only native read-only input, retaining focus, selection, copy, document and undo history. */
    public void setFieldReadOnly(String fieldId, boolean readOnly) {
        requireOpen(); var field = textField(fieldId); field.readOnly = readOnly; field.refresh();
    }
''', "", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutPanel.java", [
    ("        top.setFieldReadOnly(SEARCH,recording);\n", "", 1),
    (r'''        return new ViewSpec(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query,
            "Search actions, plugins, or Cmd+Shift+H", "Search keyboard shortcuts", ""));
''', r'''        // While recording, the field shows the query and takes no typing (spec 8.2's Field.readOnly).
        return new ViewSpec(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query,
            "Search actions, plugins, or Cmd+Shift+H", "Search keyboard shortcuts", "", Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", "", recording));
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java", [
    (" /** Deliver a typed event through containment. */\n public void emit(UiEvent event){guard();if(event instanceof UiEvent.Edited edit && index(spec.root()).get(edit.id()) instanceof Nodes.Field){",
     " /** Deliver a typed event through containment; an edit of a read-only field, which a person cannot make, is dropped. */\n"
     " public void emit(UiEvent event){guard();if(event instanceof UiEvent.Edited edit && index(spec.root()).get(edit.id()) instanceof Nodes.Field field && field.readOnly())return;"
     "if(event instanceof UiEvent.Edited edit && index(spec.root()).get(edit.id()) instanceof Nodes.Field){", 1),
    (" @Override public void registerContent(CustomContent content){",
     r''' /** Simulate a person typing {@code text} into a field, replacing its text: it reports {@link UiEvent.Edited} as the host's field does, while a read-only field, or one that is hidden, disabled or busy (itself or a container above it), takes nothing.
 * @param fieldId field node
 * @param text the text typed */
 public void type(String fieldId,String text){guard();Objects.requireNonNull(text);if(!(index(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");if(field.readOnly()||!live(fieldId))return;emit(new UiEvent.Edited(fieldId,text));}
 private boolean live(String id){var nodes=index(spec.root());for(String step:path(spec.root(),id)){var state=nodes.get(step).state();if(!state.visible()||!state.enabled()||state.busy())return false;}return true;}
 @Override public void registerContent(CustomContent content){''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.ReadOnlyFieldsTest --tests dev.jasper.ui.swing.FieldTrailingTest --tests dev.jasper.ui.swing.TextFieldsTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeKit*' :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest --tests 'dev.jasper.app.shortcuthelp.*' --continue`
Expected: BUILD SUCCESSFUL. `ShortcutEditingTest.switchingFromSearchCaptureToEditCaptureStopsSearchCapture` still sees
the search field not editable while recording and editable after, now from the descriptor.

Run: `git grep -n 'setFieldReadOnly' -- '*.java'`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src jasper-sdk-testkit/src
git commit -F - <<'EOF'
SDK 0.18: Field.readOnly

A field can show a value as a fact: focusable, selectable and copyable,
never edited, so it reports no Edited, and a read-only number ignores its
arrow keys. A stepper or slider is never read-only. The host-only
KitView.setFieldReadOnly is gone; the Keymap search's recording state is
in its descriptor. The fake drops an edit of a read-only field and gains
type(id, text), and the kit contract checks typing on both runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: `PickerFace.technicalCaption` and `EditorHint.technical()`

Two ways to say "this is a data value". A rich face's caption can be one (a saved host's endpoint under its name): the
SDK and model `PickerFace` gain `boolean technicalCaption` (the five-component constructor stays), and the default look
draws such a caption in `MONO_CAPTION`. A text setting can be one (an FTP passive port range): `EditorHint.Technical`
and `EditorHint.technical()` make generated pages use the new `Editors.technical()` and host rows (the app's and the
testkit's) use `FieldPresentation.TECHNICAL`. One gallery fixture shows technical captions at both densities.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:295-302`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:285-292` (PickerFace)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:47`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/PickerMenus.java:37,51-56,146`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/settings/EditorHint.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java:77-88,102-127`, `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java:125-127,312-316`, `jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java:50-51`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java:42-44`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `technical-captions`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/TechnicalCaptionsTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/settings/SettingSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`

**Interfaces:**
- Consumes: Task 1's `Field` (unchanged here).
- Produces: `Nodes.PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density, boolean
  technicalCaption)` and `technicalCaption()`; `record EditorHint.Technical()` and `static EditorHint
  EditorHint.technical()`; `static Typography.Role PickerMenus.captionRole(boolean compact, boolean technical)`;
  `public static Editors.Factory<String> Editors.technical()`; `PluginSettingsPage.Hint.TECHNICAL`. Task 9's contract
  view uses a technical caption.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/TechnicalCaptionsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code PickerFace.technicalCaption}: the caption is a data value, drawn in the data face. */
@SuppressWarnings("try")
class TechnicalCaptionsTest {
    @Test void aTechnicalCaptionTakesTheDataFaceAtEitherDensity() {
        assertThat(PickerMenus.captionRole(false, false)).isEqualTo(Typography.Role.CAPTION);
        assertThat(PickerMenus.captionRole(true, false)).isEqualTo(Typography.Role.PICKER_CAPTION);
        assertThat(PickerMenus.captionRole(false, true)).isEqualTo(Typography.Role.MONO_CAPTION);
        assertThat(PickerMenus.captionRole(true, true)).isEqualTo(Typography.Role.MONO_CAPTION);
    }

    @Test void aTechnicalCaptionPaintsInAnotherFaceThanAPlainOne() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var kind : List.of(ChoiceKind.PICKER, ChoiceKind.DROPDOWN))
                    assertThat(Arrays.equals(pixels(render(context, kind, false)), pixels(render(context, kind, true)))).as(kind.name()).isFalse();
            } finally { release(context); }
        });
    }

    private static BufferedImage render(KitContext context, ChoiceKind kind, boolean technical) {
        var face = new PickerFace("Mac", "dustin@192.168.1.119:22", "server", Hue.BLUE, PickerDensity.DEFAULT, technical);
        var options = kind == ChoiceKind.DROPDOWN ? List.of(new Option("mac", "Mac", Optional.empty())) : List.<Option>of();
        var spec = new ViewSpec(new Choice("via", ControlState.READY, kind, options, kind == ChoiceKind.DROPDOWN ? "mac" : "", "Via host", "pick", "",
            Optional.empty(), Optional.of(face)));
        try (var view = new KitView(context, spec, e -> {})) { return KitFixtures.render(view, 320, 60, 1); }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 2 step 1: the tests that pin technical captions and the technical editor hint."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", r'''    /** Plan 3b: a rich face's caption is plain text unless it says it is a data value. */
    @Test void pickerFacesDefaultToAPlainCaption() {
        assertThat(new PickerFace("Mac", "dustin@host:22", "server", Hue.BLUE, PickerDensity.DEFAULT).technicalCaption()).isFalse();
        assertThat(new PickerFace("Mac", "dustin@host:22", "server", Hue.BLUE, PickerDensity.DEFAULT, true).technicalCaption()).isTrue();
    }
    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {
''', 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/settings/SettingSpecTest.java", [
    ("    @Test void badDeclarationsAreRejected() {\n", r'''    /** Plan 3b: the technical hint is a value, like the others. */
    @Test void theTechnicalHintIsAValue() {
        assertThat(EditorHint.technical()).isEqualTo(new EditorHint.Technical());
        assertThat(SettingSpec.of("ports", SettingKind.text(), "").editor(EditorHint.technical()).editor()).contains(EditorHint.technical());
    }

    @Test void badDeclarationsAreRejected() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (" @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){\n", r''' /** Plan 3b: a technical caption crosses the projection unchanged. */
 @Test void technicalPickerCaptionsProjectWithoutLoss(){
  for(boolean technical:List.of(false,true)){
   var face=new PickerFace("Mac","dustin@192.168.1.119:22","server",Hue.BLUE,PickerDensity.DEFAULT,technical);
   var spec=new ViewSpec(new Choice("via",READY,ChoiceKind.DROPDOWN,List.of(new Option("mac","Mac",Optional.empty())),"mac","Via host","","",Optional.empty(),Optional.of(face)));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
   assertThat(((dev.jasper.ui.model.Nodes.Choice)KitValues.toModule(spec).root()).face().orElseThrow().technicalCaption()).isEqualTo(technical);
  }
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java", [
    ("    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */\n    private static dev.jasper.sdk.ui.Nodes.Node hostControl(",
     r'''    /** Spec 8.2: a technical hint's host row is the generated page's text field in the data face. */
    @Test void aTechnicalHintsHostRowIsTheGeneratedPagesTechnicalField() throws Exception {
        var view = new AtomicReference<HostedKitView>();
        var ports = new AtomicReference<Setting<String>>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            getClass().getClassLoader(), () -> plugin -> {
                ports.set(plugin.settings().declare(SettingSpec.of("ports", SettingKind.text(), "50000-50100").label("Passive ports")
                    .editor(dev.jasper.sdk.settings.EditorHint.technical())));
                plugin.settings().page((SettingsPanel) panelHost -> {
                    var made = (HostedKitView) plugin.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                        dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(panelHost.row(ports.get())))), event -> { });
                    view.set(made);
                    return made;
                });
            })));
        var context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
            () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);
        var page = onEdtValue(() -> (PluginPanelPage) host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            var field = hostControl(view.get(), 0);
            var generated = dev.jasper.app.settingsui.Editors.technical().build().apply(appText("ports", "Passive ports"), context);
            generated.show("50000-50100");
            assertThat(KitValues.toModule(field)).as("the generated page's technical field")
                .isEqualTo(generated.node("settings:ports", KitValues.toModule(field.state())));
            assertThat(((dev.jasper.sdk.ui.Nodes.Field) field).presentation()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldPresentation.TECHNICAL);
            assertThat(PluginSettingsPage.Hint.of(SettingSpec.of("ports", SettingKind.text(), "").editor(dev.jasper.sdk.settings.EditorHint.technical())))
                .isEqualTo(PluginSettingsPage.Hint.TECHNICAL);
        });
        onEdt(page::dispose);
    }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node hostControl(''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java", [
    ("    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */\n    private static dev.jasper.sdk.ui.Nodes.Node control(",
     r'''    /** As the host: a technical hint's row is a text field in the data face. */
    @Test void aTechnicalHintsRowIsATextFieldInTheDataFace() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        var ports = new AtomicReference<Setting<String>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            ports.set(context.settings().declare(SettingSpec.of("ports", SettingKind.text(), "50000-50100").label("Passive ports").editor(EditorHint.technical())));
            context.settings().page(rows -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(rows.row(ports.get())))), e -> {}));
        });
        host.settingsPanel("test.fake").orElseThrow();
        var view = host.settingsPanelView("test.fake").orElseThrow();
        assertThat(control(view, 0)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Field.class, field -> {
            assertThat(field.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.TEXT);
            assertThat(field.presentation()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldPresentation.TECHNICAL);
            assertThat(field.value()).isEqualTo("50000-50100");
        });
        view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("settings:ports", "50000-50010"));
        assertThat(host.pendingSettings("test.fake")).containsEntry("ports", "50000-50010");
        assertThat(host.failures()).isEmpty();
    }); }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node control(''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): rich faces whose captions are data values, at both densities.
        result.add(new Fixture("technical-captions", "", "normal", 260, 110, new ViewSpec(new Nodes.Stack("technical-captions", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Nodes.Choice("technical-via", ControlState.READY, ChoiceKind.DROPDOWN, List.of(new Option("mac", "Mac", Optional.empty()), new Option("box", "build-box", Optional.empty())),
                "mac", "Via host", "via", "", Optional.empty(), Optional.of(new PickerFace("Mac", "dustin@192.168.1.119:22", "server", Hue.BLUE, PickerDensity.DEFAULT, true))),
            new Nodes.Choice("technical-key", ControlState.READY, ChoiceKind.PICKER, List.of(), "", "Credential", "pick", "lock", Optional.empty(),
                Optional.of(new PickerFace("root", "SHA256:4f2a9c", "key", Hue.GREEN, PickerDensity.COMPACT, true))))))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest --tests dev.jasper.sdk.settings.SettingSpecTest :jasper-ui:test --tests dev.jasper.ui.swing.TechnicalCaptionsTest --continue`
Expected: FAIL. The SDK test compile reports "constructor PickerFace in record PickerFace cannot be applied to given
types" and "cannot find symbol … technical()"; jasper-ui's reports "cannot find symbol … captionRole".

- [ ] **Step 3: Add the component, the hint and their presentation**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 2 step 3: PickerFace.technicalCaption and EditorHint.technical()."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [(r'''     * still lists the options and reports Selected; its owner keeps the face describing the selected option.
     */
    public record PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density) {
        public PickerFace { Objects.requireNonNull(primary);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(density); }
    }
''', r'''     * still lists the options and reports Selected; its owner keeps the face describing the selected option. A
     * {@code technicalCaption} (since 0.18.0) is a data value, such as a saved host's endpoint, drawn in the data face.
     */
    public record PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density, boolean technicalCaption) {
        public PickerFace { Objects.requireNonNull(primary);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(density); }
        /** A face whose caption is plain text. */
        public PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density) { this(primary, caption, icon, hue, density, false); }
    }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    ("dev.jasper.ui.model.Nodes.PickerDensity.valueOf(n.density().name()));}",
     "dev.jasper.ui.model.Nodes.PickerDensity.valueOf(n.density().name()),n.technicalCaption());}", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/PickerMenus.java", [
    ("button.getHeight(),false,empty,face.density()==Nodes.PickerDensity.COMPACT,false);",
     "button.getHeight(),false,empty,face.density()==Nodes.PickerDensity.COMPACT,false,face.technicalCaption());", 1),
    ("line(g,c,item.label(),item.caption(),x,top,getWidth()-x-pad-tile,rowHeight,active,false,false,footer);",
     "line(g,c,item.label(),item.caption(),x,top,getWidth()-x-pad-tile,rowHeight,active,false,false,footer,false);", 1),
    ("    private static void line(Graphics2D g,KitContext c,String label,String caption,int x,int y,int width,int height,boolean active,boolean empty,boolean compact,boolean link){",
     "    /** A caption's type: a data value (a technical caption) in the data face, otherwise the density's caption. */\n"
     "    static Typography.Role captionRole(boolean compact,boolean technical){\n"
     "        return technical?Typography.Role.MONO_CAPTION:compact?Typography.Role.PICKER_CAPTION:Typography.Role.CAPTION;\n"
     "    }\n"
     "    private static void line(Graphics2D g,KitContext c,String label,String caption,int x,int y,int width,int height,boolean active,boolean empty,boolean compact,boolean link,boolean technical){", 1),
    ("g.setFont(ThemePainting.font(c,compact?Typography.Role.PICKER_CAPTION:Typography.Role.CAPTION));",
     "g.setFont(ThemePainting.font(c,captionRole(compact,technical)));", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/settings/EditorHint.java", [
    ('''    /** A file chooser beside a text field. */
    record File() implements EditorHint { }
''', '''    /** A file chooser beside a text field. */
    record File() implements EditorHint { }

    /**
     * A data value, such as a host, a port range, a path or a command: a text setting's field is drawn in the data face.
     * Other kinds ignore it.
     *
     * @since 0.18.0
     */
    record Technical() implements EditorHint { }
''', 1),
    ('''    static EditorHint file() { return new File(); }
''', '''    static EditorHint file() { return new File(); }

    /**
     * A data value's text field.
     *
     * @return the hint
     * @since 0.18.0
     */
    static EditorHint technical() { return new Technical(); }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java", [
    ('''        /** A path with Browse, which asks for an existing file. */
        FILE,
''', '''        /** A path with Browse, which asks for an existing file. */
        FILE,
        /** A text setting's field in the data face. */
        TECHNICAL,
''', 1),
    ("                case EditorHint.File _ -> FILE;\n", "                case EditorHint.File _ -> FILE;\n                case EditorHint.Technical _ -> TECHNICAL;\n", 1),
    ("        if (hint == Hint.FILE) return Editors.path(Hint.FILE_CHOOSER_TITLE);\n",
     "        if (hint == Hint.FILE) return Editors.path(Hint.FILE_CHOOSER_TITLE);\n"
     "        if (hint == Hint.TECHNICAL && spec.kind() instanceof dev.jasper.sdk.settings.SettingKind.Text) return Editors.technical();\n", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java", [
    ('''    public static Factory<String> text() {
        return new Factory<>((setting, context) -> new Line(setting.label()), List.of());
    }
''', '''    public static Factory<String> text() {
        return new Factory<>((setting, context) -> new Line(setting.label()), List.of());
    }

    /** A text field for a data value (a host, a port range, a path or a command), in the data face. */
    public static Factory<String> technical() {
        return new Factory<>((setting, context) -> new Line(setting.label(), Nodes.FieldPresentation.TECHNICAL), List.of());
    }
''', 1),
    ('''        private final String label;
        String text = "";
        Line(String label) { this.label = label; }
        @Override public Nodes.Node node(String id, ControlState state) { return new Nodes.Field(id, state, Nodes.FieldKind.TEXT, text, "", label, ""); }
''', '''        private final String label;
        private final Nodes.FieldPresentation presentation;
        String text = "";
        Line(String label) { this(label, Nodes.FieldPresentation.STANDARD); }
        Line(String label, Nodes.FieldPresentation presentation) { this.label = label; this.presentation = presentation; }
        @Override public Nodes.Node node(String id, ControlState state) {
            return new Nodes.Field(id, state, Nodes.FieldKind.TEXT, text, "", label, "", Optional.empty(), presentation);
        }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java", [
    ("                spec.kind() instanceof SettingKind.IntegerRange?Nodes.FieldPresentation.STEPPER:Nodes.FieldPresentation.STANDARD);",
     "                spec.kind() instanceof SettingKind.IntegerRange?Nodes.FieldPresentation.STEPPER\n"
     "                    :kind==Nodes.FieldKind.TEXT&&PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.TECHNICAL?Nodes.FieldPresentation.TECHNICAL:Nodes.FieldPresentation.STANDARD);", 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java", [
    ("                spec.kind() instanceof SettingKind.IntegerRange ? Nodes.FieldPresentation.STEPPER : Nodes.FieldPresentation.STANDARD);",
     "                spec.kind() instanceof SettingKind.IntegerRange ? Nodes.FieldPresentation.STEPPER\n"
     "                    : kind == Nodes.FieldKind.TEXT && spec.editor().orElse(null) instanceof EditorHint.Technical ? Nodes.FieldPresentation.TECHNICAL : Nodes.FieldPresentation.STANDARD);", 1)])
PY
```

The host row's comment above the changed line ("As the generated pages: a bounded integer is the Settings stepper…")
stays accurate.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest --tests dev.jasper.sdk.settings.SettingSpecTest :jasper-ui:test --tests dev.jasper.ui.swing.TechnicalCaptionsTest --tests dev.jasper.ui.swing.PickerMenusTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakePluginSettingsTest :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.PluginPanelPageTest --tests dev.jasper.app.settingsui.EditorsTest --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src jasper-sdk-testkit/src
git commit -F - <<'EOF'
SDK 0.18: technical captions and the technical editor hint

A rich face's caption can be a data value (a saved host's endpoint), drawn
in MONO_CAPTION, and EditorHint.technical() asks for a text setting's
field in the data face: generated pages use Editors.technical() and host
rows FieldPresentation.TECHNICAL, in the app and the testkit alike.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: `ChoiceKind.TABS` and the EXCLUSIVE option count

`ChoiceKind` gains `TABS` (two or more options; it reports `Selected` and the owner composes the content below), and
`ViewSpec` refuses an EXCLUSIVE choice with fewer than two or more than four options and a TABS choice with fewer than
two, wherever the choice sits (list rows included). The default look draws TABS as the segment track without a visible
label. The count rule refuses File Server's two one-option choices, so a single offered protocol and TFTP's one login
read as read-only fields (Rulings 1). `StateFixtures`' controls sheet skips TABS, which gets its own fixture.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:26` (and 3a's one-line Javadoc above it, `:25`), `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:22`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java:51-53`, `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java:51-53`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java:30,162`, `jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java:296`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java:371-373,395-398,417`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/StateFixtures.java:29`, `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `tabs`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/TabsTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java` (a new test, and 3a's `zeroEighteenNamesTheExclusiveHelpAndPromptConstants`), `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/StagedFileServerFormTest.java:86` (TFTP's login is a read-only field), `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerCompositionTest.java`

**Interfaces:**
- Consumes: Task 1's 13-argument `Nodes.Field` constructor and `readOnly()`.
- Produces: `Nodes.ChoiceKind.TABS` (last constant) in the SDK and model; the `ViewSpec` messages "An EXCLUSIVE choice
  has 2 to 4 options" and "A TABS choice has two or more options"; `boolean Choices.ChoiceControl.labelShown()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/TabsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code ChoiceKind.TABS}: the default look draws the segment track, labelled by its tabs. */
@SuppressWarnings("try")
class TabsTest {
    static Choice choice(ChoiceKind kind) {
        return new Choice("tabs", ControlState.READY, kind, List.of(new Option("overview", "Overview", Optional.empty()),
            new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "");
    }

    @Test void tabsAreASegmentTrackWithoutAVisibleLabelThatReportsSelected() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(choice(ChoiceKind.TABS)), events::add)) {
                KitFixtures.render(view, 360, 60, 1);
                var control = (Choices.ChoiceControl) view.control("tabs");
                assertThat(control.input).isInstanceOf(Choices.SegmentTrack.class);
                assertThat(control.label.isVisible()).isFalse();
                assertThat(control.input.getAccessibleContext().getAccessibleName()).isEqualTo("Plugin information");
                find(view, "tabs.option.details", Choices.SegmentButton.class).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("tabs", Set.of("details")));
                control.embeddedLabel(false);
                assertThat(control.label.isVisible()).as("not even when a composition un-embeds it").isFalse();
            } finally { release(context); }
        });
    }

    @Test void anExclusiveChoiceStillShowsItsLabel() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(choice(ChoiceKind.EXCLUSIVE)), e -> {})) {
                KitFixtures.render(view, 360, 80, 1);
                assertThat(((Choices.ChoiceControl) view.control("tabs")).label.isVisible()).isTrue();
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 3 step 1: the tests that pin TABS and the EXCLUSIVE option count."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
COUNTS = r'''    /** Plan 3b (spec 8.2): EXCLUSIVE shows two to four options and TABS two or more, wherever the choice sits. */
    @Test void exclusiveChoicesShowTwoToFourOptionsAndTabsTwoOrMore() {
        for (int count = 0; count <= 6; count++) {
            var options = java.util.stream.IntStream.range(0, count).mapToObj(i -> new Option("o" + i, "Option " + i, Optional.empty())).toList();
            var exclusive = new Choice("pick", ControlState.READY, ChoiceKind.EXCLUSIVE, options, "", "Pick", "");
            var tabs = new Choice("tabs", ControlState.READY, ChoiceKind.TABS, options, "", "Tabs", "");
            if (count >= 2 && count <= 4) assertThatCode(() -> new ViewSpec(exclusive)).as("%d options", count).doesNotThrowAnyException();
            else assertThatIllegalArgumentException().as("%d options", count).isThrownBy(() -> new ViewSpec(exclusive)).withMessageContaining("2 to 4 options");
            if (count >= 2) assertThatCode(() -> new ViewSpec(tabs)).as("%d tabs", count).doesNotThrowAnyException();
            else assertThatIllegalArgumentException().as("%d tabs", count).isThrownBy(() -> new ViewSpec(tabs)).withMessageContaining("two or more options");
        }
        var one = new Choice("one", ControlState.READY, ChoiceKind.EXCLUSIVE, List.of(new Option("a", "A", Optional.empty())), "a", "One", "");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new ItemList("list", ControlState.READY, ListKind.SINGLE,
            List.of(new ListItem("row", Optional.empty(), List.of(text("title")), List.of(), List.of(one))), Set.of(), "", List.of())))
            .withMessageContaining("2 to 4 options");
    }
'''
SDK_TEST = "jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java"
edit(SDK_TEST, [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", COUNTS + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1),
    ('''assertThat(names(ChoiceKind.class)).containsExactly("DROPDOWN", "PICKER", "EXCLUSIVE", "SEARCHABLE", "PREVIEWS");''',
     '''assertThat(names(ChoiceKind.class)).containsExactly("DROPDOWN", "PICKER", "EXCLUSIVE", "SEARCHABLE", "PREVIEWS", "TABS");''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", COUNTS + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (" @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){\n", r''' /** Plan 3b: a TABS choice crosses the projection unchanged. */
 @Test void tabsProjectWithoutLoss(){
  var spec=new ViewSpec(new Choice("tabs",READY,ChoiceKind.TABS,List.of(new Option("overview","Overview",Optional.empty()),new Option("details","Details",Optional.empty())),"overview","Plugin information",""));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  assertThat(((dev.jasper.ui.model.Nodes.Choice)KitValues.toModule(spec).root()).kind()).isEqualTo(dev.jasper.ui.model.Nodes.ChoiceKind.TABS);
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
''', 1)])
COMPOSITION = "plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerCompositionTest.java"
edit(COMPOSITION, [
    ('''                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("TFTP")));
                assertThat(node(editor,"server.account.row").state().visible()).isFalse();
                assertThat(((Nodes.Text)node(editor,"server.preview")).text()).isEqualTo("tftp://127.0.0.1:6969");
''', '''                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("TFTP")));
                assertThat(node(editor,"server.account.row").state().visible()).isFalse();
                assertThat(((Nodes.Text)node(editor,"server.preview")).text()).isEqualTo("tftp://127.0.0.1:6969");
                // Spec 8.2: TFTP's one login is a fact, not a choice: a read-only field in the choice's place.
                assertThat(node(editor, ServerEditor.LOGIN)).isInstanceOfSatisfying(Nodes.Field.class, login -> {
                    assertThat(login.readOnly()).isTrue();
                    assertThat(login.value()).isEqualTo("No login (TFTP has none)");
                    assertThat(login.presentation()).isEqualTo(Nodes.FieldPresentation.EMBEDDED);
                });
                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("HTTP")));
                assertThat(node(editor, ServerEditor.LOGIN)).isInstanceOf(Nodes.Choice.class);
''', 1),
    ('''                String longPath=root.resolve("very-long-synthetic-folder/another-segment/still-editable").toString();
''', '''                // One offered protocol is a fact, not a choice (spec 8.2: an EXCLUSIVE choice has 2 to 4 options).
                assertThat(node(editor, ServerEditor.PROTOCOL)).isInstanceOfSatisfying(Nodes.Field.class, protocol -> {
                    assertThat(protocol.readOnly()).isTrue();
                    assertThat(protocol.value()).isEqualTo("HTTP");
                });
                String longPath=root.resolve("very-long-synthetic-folder/another-segment/still-editable").toString();
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/StagedFileServerFormTest.java", [
    ('''                        var segments=((Nodes.Choice)node(form,"server.login")).options().stream().map(option->require(form,"server.login.option."+option.id())).toList();
''', '''                        // TFTP's one login is a read-only field (spec 8.2: an EXCLUSIVE choice has 2 to 4 options).
                        if(protocol.equals("TFTP")){assertThat(node(form,"server.login")).isInstanceOfSatisfying(Nodes.Field.class,f->assertThat(f.readOnly()).isTrue());continue;}
                        var segments=((Nodes.Choice)node(form,"server.login")).options().stream().map(option->require(form,"server.login.option."+option.id())).toList();
''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): a TABS choice (Overview and Details).
        result.add(new Fixture("tabs", "", "normal", 360, 60, new ViewSpec(new Nodes.Choice("tabs", ControlState.READY, ChoiceKind.TABS,
            List.of(new Option("overview", "Overview", Optional.empty()), new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "tabs"))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/StateFixtures.java", [
    ('''        for (ChoiceKind kind : ChoiceKind.values()) controls.add(choice("choice-" + kind, kind, "Selected", "Selected", "Other"));
''', '''        // TABS has its own fixture (KitGallery's "tabs"), so this sheet keeps its pixels.
        for (ChoiceKind kind : ChoiceKind.values()) if (kind != ChoiceKind.TABS) controls.add(choice("choice-" + kind, kind, "Selected", "Selected", "Other"));
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-plugin-fileserver:test --tests dev.jasper.fileserver.ui.ServerCompositionTest --continue`
Expected: FAIL. The SDK test compile reports "cannot find symbol … TABS"; `ServerCompositionTest` fails both changed
tests (the login is still a `Choice`; the HTTP-only protocol is still a `Choice`). `StagedFileServerFormTest`'s TFTP
branch would fail the same way (Step 4 runs it).

- [ ] **Step 3: Add TABS, the counts, the presentation and File Server's facts**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 3 step 3: ChoiceKind.TABS, the option counts, the default look and File Server's read-only facts."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
SDK_NODES, MODEL_NODES = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"
edit(SDK_NODES, [("    /** EXCLUSIVE is a choice among two to four options, all of them visible. */\n",
    "    /**\n     * EXCLUSIVE is a choice among two to four options, all of them visible. TABS (since 0.18.0) switches among two or\n"
    "     * more views of one thing (Overview and Details): it reports Selected, and the owner composes the chosen content.\n     */\n", 1)])
for nodes in [SDK_NODES, MODEL_NODES]:
    edit(nodes, [("    public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }",
                  "    public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS, TABS }", 1)])
for spec in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java", "jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java"]:
    edit(spec, [(r'''            case Choice n -> {
                if (!n.selectedId().isEmpty()) references(Set.of(n.selectedId()), new HashSet<>(n.options().stream().map(Option::id).toList()));
            }
''', r'''            case Choice n -> {
                if (!n.selectedId().isEmpty()) references(Set.of(n.selectedId()), new HashSet<>(n.options().stream().map(Option::id).toList()));
                // Spec 8.2: EXCLUSIVE shows two to four options, all of them visible; TABS switches among two or more.
                if (n.kind() == ChoiceKind.EXCLUSIVE && (n.options().size() < 2 || n.options().size() > 4))
                    throw new IllegalArgumentException("An EXCLUSIVE choice has 2 to 4 options, not " + n.options().size());
                if (n.kind() == ChoiceKind.TABS && n.options().size() < 2)
                    throw new IllegalArgumentException("A TABS choice has two or more options, not " + n.options().size());
            }
''', 1)])
CHOICES = "jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java"
edit(CHOICES, [
    ("        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(!value); }\n",
     "        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(labelShown()); }\n"
     "        /** A TABS choice is labelled by its tabs; its label stays the accessible name. */\n"
     "        boolean labelShown() { return !embedded && ((Nodes.Choice)node).kind() != Nodes.ChoiceKind.TABS; }\n", 1),
    ("label.setVisible(!embedded);label.setText(choice.label());", "label.setVisible(labelShown());label.setText(choice.label());", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java", [("case EXCLUSIVE ->", "case EXCLUSIVE, TABS ->", 1)])
EDITOR = "plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java"
edit(EDITOR, [
    (r'''    private static Nodes.Text text(String id, String value, Nodes.TextRole role) {
        return new Nodes.Text(id, state(!value.isBlank(), true), value, role);
    }
''', r'''    private static Nodes.Text text(String id, String value, Nodes.TextRole role) {
        return new Nodes.Text(id, state(!value.isBlank(), true), value, role);
    }
    /** A value shown as a fact rather than as a one-option choice (spec 8.2: an EXCLUSIVE choice has 2 to 4 options). */
    private static Nodes.Field fact(String id, String value, String label, Nodes.FieldPresentation presentation) {
        return new Nodes.Field(id, ControlState.READY, Nodes.FieldKind.TEXT, value, "", label, "", Optional.empty(), presentation, "", "", "", true);
    }
''', 1),
    (r'''        server.add(row("server.protocol.row", "Protocol", new Nodes.Choice(PROTOCOL, state(true, !existing && protocols.size() > 1), Nodes.ChoiceKind.EXCLUSIVE,
''', r'''        server.add(row("server.protocol.row", "Protocol", protocols.size() == 1 ? fact(PROTOCOL, protocol.label(), "Protocol", Nodes.FieldPresentation.STANDARD)
            : new Nodes.Choice(PROTOCOL, state(true, !existing && protocols.size() > 1), Nodes.ChoiceKind.EXCLUSIVE,
''', 1),
    (r'''        access.add(new Nodes.Choice(LOGIN, state(true, protocol.hasLogin()), Nodes.ChoiceKind.EXCLUSIVE, loginOptions, shownLogin, "Login", ""));
''', r'''        // A single login (TFTP has none) is a fact: a read-only field in the choice's place, unlabelled as the choice was.
        access.add(loginOptions.size() == 1 ? fact(LOGIN, loginOptions.getFirst().label(), "Login", Nodes.FieldPresentation.EMBEDDED)
            : new Nodes.Choice(LOGIN, state(true, protocol.hasLogin()), Nodes.ChoiceKind.EXCLUSIVE, loginOptions, shownLogin, "Login", ""));
''', 1)])
PY
```

The Access card's comment above the login ("The Access card is the login's own card, so its segments need no label…")
stays accurate for the choice it still describes.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.TabsTest --tests dev.jasper.ui.swing.ChoicesTest --tests dev.jasper.ui.swing.InteractiveRowsTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests 'dev.jasper.app.plugins.Staged*' --tests 'dev.jasper.app.appearance.Surface*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.*' :jasper-plugin-fileserver:test :jasper-plugin-remote:test :jasper-plugin-vault:test --continue`
Expected: BUILD SUCCESSFUL. `StagedFileServerFormTest` walks every protocol's login through the real host, TFTP's as
the read-only field. `InteractiveRowsTest.inlineChoiceKindsKeepUsableInputHeightWithoutStandaloneLabels` covers
TABS in list rows through `ItemLists.inlineHeight`. If another test fails with "An EXCLUSIVE choice has 2 to 4
options", that test builds a one-option or five-option choice: give it two to four options (or, for a fact, a
read-only field as above) and record the file in the plan's status banner.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src plugins/fileserver/src
git commit -F - <<'EOF'
SDK 0.18: TABS and the EXCLUSIVE option count

ChoiceKind.TABS switches among two or more views, drawn by the default
look as the segment track without a visible label. ViewSpec refuses an
EXCLUSIVE choice with fewer than two or more than four options and a TABS
choice with fewer than two. File Server's one-option choices, a single
offered protocol and TFTP's one login, read as read-only fields instead.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: `IconSize.SWATCH`

`IconSize` gains `SWATCH`: a solid square of the icon's hue at caption size, for the Run window's hue squares and a
radio's swatch. The hue must not be NONE and a swatch takes no severity; the name stays a valid icon name (it is still
validated and is what a presentation without swatches would draw). `IconRegistry.size` gives it `ICON_CAPTION`, and
`Labels.IconControl` paints the square in the hue's glyph colour (Rulings 9). `StateFixtures`' icon sheet skips SWATCH,
which gets its own fixture. The app's `KitValuesTest` also pins the full name lists of `IconSize`, `FormKind`,
`LayoutRole` and `TextRole` against the model's, which 3a's final review found unpinned there.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:19-20,92-105` (with 3a's one-line Javadoc at `:19`), `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:18,85-97`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:226-234`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Labels.java:148-154,165-167`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/StateFixtures.java:20`, `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `swatches`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/SwatchesTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java` (a new test, and 3a's names test), `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`

**Interfaces:**
- Consumes: nothing from Tasks 1 to 3.
- Produces: `Nodes.IconSize.SWATCH` (last constant) in the SDK and model; `static SemanticColor Labels.hue(Nodes.Hue)`;
  `record Labels.Swatch(Color color, int extent) implements javax.swing.Icon`. Task 9's contract view and 3d's
  `DialogSpec.swatch` may reuse them.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/SwatchesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
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

/** Spec 8.2's {@code IconSize.SWATCH}: a solid square of the icon's hue at caption size. */
@SuppressWarnings("try")
class SwatchesTest {
    @Test void aSwatchIsASolidSquareOfItsHueAtCaptionSize() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var spec = new ViewSpec(new Nodes.Icon("swatch", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "FTP"));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 40, 40, 1);
                var label = find(view, "swatch", JLabel.class);
                int extent = UIScale.scale(context.metrics().value(DesignMetrics.Role.ICON_CAPTION));
                assertThat(label.getIcon().getIconWidth()).isEqualTo(extent);
                assertThat(label.getIcon().getIconHeight()).isEqualTo(extent);
                var image = new BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { label.getIcon().paintIcon(label, g, 0, 0); } finally { g.dispose(); }
                int amber = KitFixtures.marker(SemanticColor.HUE_AMBER).getRGB();
                for (int[] at : new int[][] {{0, 0}, {extent - 1, 0}, {0, extent - 1}, {extent - 1, extent - 1}, {extent / 2, extent / 2}})
                    assertThat(image.getRGB(at[0], at[1])).isEqualTo(amber);
                assertThat(label.getAccessibleContext().getAccessibleName()).isEqualTo("FTP");
            } finally { release(context); }
        });
    }

    @Test void eachHueHasItsGlyphColour() {
        assertThat(List.of(Hue.BLUE, Hue.GREEN, Hue.AMBER, Hue.PURPLE, Hue.NEUTRAL).stream().map(Labels::hue).toList())
            .containsExactly(SemanticColor.HUE_BLUE, SemanticColor.HUE_GREEN, SemanticColor.HUE_AMBER, SemanticColor.HUE_PURPLE, SemanticColor.HUE_NEUTRAL);
    }

    @Test void aSwatchKeepsItsSquareWhenTheIconChangesSize() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.CONTROL, "File")), e -> {})) {
                var label = find(view, "icon", JLabel.class);
                assertThat(label.getIcon()).isNotInstanceOf(Labels.Swatch.class);
                view.update(new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.SWATCH, "File")));
                assertThat(label.getIcon()).isInstanceOf(Labels.Swatch.class);
                view.update(new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.CONTROL, "File")));
                assertThat(label.getIcon()).isNotInstanceOf(Labels.Swatch.class);
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 4 step 1: the tests that pin IconSize.SWATCH."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
RULE = r'''    /** Plan 3b: a swatch is a square of a hue, so it needs one and takes no severity. */
    @Test void swatchesNeedAHueAndTakeNoSeverity() {
        assertThat(new Icon("ftp", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "FTP").size()).isEqualTo(IconSize.SWATCH);
        assertThatIllegalArgumentException().isThrownBy(() -> new Icon("none", ControlState.READY, "file", Hue.NONE, IconSize.SWATCH, "None"))
            .withMessageContaining("NONE");
        assertThatIllegalArgumentException().isThrownBy(() -> new Icon("warn", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "Warn", Optional.of(Severity.WARNING)))
            .withMessageContaining("severity");
        assertThat(new Icon("glyph", ControlState.READY, "file", Hue.NONE, IconSize.CAPTION, "File").hue()).isEqualTo(Hue.NONE);
    }
'''
SDK_TEST = "jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java"
edit(SDK_TEST, [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", RULE + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1),
    ('''assertThat(names(IconSize.class)).containsExactly("CAPTION", "CONTROL", "RAIL", "EMPTY", "PROMPT");''',
     '''assertThat(names(IconSize.class)).containsExactly("CAPTION", "CONTROL", "RAIL", "EMPTY", "PROMPT", "SWATCH");''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", RULE + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (" @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){\n", r''' /** Plan 3b: swatches of every hue cross the projection unchanged. */
 @Test void swatchesProjectWithoutLoss(){
  for(var hue:List.of(Hue.BLUE,Hue.GREEN,Hue.AMBER,Hue.PURPLE,Hue.NEUTRAL)){
   var spec=new ViewSpec(new Icon("swatch",READY,"file",hue,IconSize.SWATCH,"Swatch"));
   assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  }
  assertThat(Arrays.stream(IconSize.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.IconSize.values()).map(Enum::name).toArray(String[]::new));
  // 3a's other renamed enums keep the model's names too (3a's final review: only ChoiceKind and ButtonPresentation were pinned).
  assertThat(Arrays.stream(FormKind.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.FormKind.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(LayoutRole.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.LayoutRole.values()).map(Enum::name).toArray(String[]::new));
  assertThat(Arrays.stream(TextRole.values()).map(Enum::name)).containsExactly(Arrays.stream(dev.jasper.ui.model.Nodes.TextRole.values()).map(Enum::name).toArray(String[]::new));
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): a swatch of every hue that has one, each beside its name.
        var swatches = new ArrayList<Nodes.Node>();
        for (Hue hue : List.of(Hue.BLUE, Hue.GREEN, Hue.AMBER, Hue.PURPLE, Hue.NEUTRAL)) {
            swatches.add(new Nodes.Icon("swatch-" + hue, ControlState.READY, "file", hue, IconSize.SWATCH, hue.name()));
            swatches.add(new Nodes.Text("swatch-label-" + hue, ControlState.READY, hue.name(), TextRole.CAPTION));
        }
        result.add(new Fixture("swatches", "", "normal", 420, 40, new ViewSpec(new Nodes.Stack("swatches", ControlState.READY, LayoutRole.INLINE, swatches))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/StateFixtures.java", [
    ('''        for (Hue hue : Hue.values()) for (IconSize size : IconSize.values()) icons.add(icon("icon-" + hue + "-" + size, "key", hue, size));
''', '''        // SWATCH has its own fixture (KitGallery's "swatches"), so this sheet keeps its pixels.
        for (Hue hue : Hue.values()) for (IconSize size : IconSize.values()) if (size != IconSize.SWATCH) icons.add(icon("icon-" + hue + "-" + size, "key", hue, size));
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.SwatchesTest --continue`
Expected: FAIL. Both test compiles report "cannot find symbol … SWATCH" (and jasper-ui's "cannot find symbol … hue",
"… Swatch").

- [ ] **Step 3: Add the size and paint the square**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 4 step 3: IconSize.SWATCH in the SDK, the model, the registry and the icon control."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
SDK_NODES, MODEL_NODES = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"
SWATCH_DOC = ("     * SWATCH (since 0.18.0) is a solid square of the icon's hue at caption size, such as a protocol's colour beside its\n"
              "     * name; its hue is not NONE and it takes no severity, and its name stays a valid icon name.\n")
# The SDK's IconSize carries 3a's one-line Javadoc: SWATCH joins it, so no doc comment is left dangling.
edit(SDK_NODES, [("    /** PROMPT is the large icon: a prompt's header, a detail heading or a sheet's title. */\n"
                  "    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT }\n",
    "    /**\n     * PROMPT is the large icon: a prompt's header, a detail heading or a sheet's title.\n" + SWATCH_DOC + "     */\n"
    "    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT, SWATCH }\n", 1)])
edit(MODEL_NODES, [("    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT }\n",
    "    /**\n" + SWATCH_DOC + "     */\n    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT, SWATCH }\n", 1)])
for nodes in [SDK_NODES, MODEL_NODES]:
    edit(nodes, [
        (r'''            Objects.requireNonNull(severity);
        }
        public Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName) {
''', r'''            Objects.requireNonNull(severity);
            if (size == IconSize.SWATCH && (hue == Hue.NONE || severity.isPresent()))
                throw new IllegalArgumentException("A SWATCH is a square of its hue: the hue is not NONE and it takes no severity");
        }
        public Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName) {
''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java", [
    ("            case PROMPT -> DesignMetrics.Role.ICON_PROMPT;\n",
     "            case PROMPT -> DesignMetrics.Role.ICON_PROMPT;\n            case SWATCH -> DesignMetrics.Role.ICON_CAPTION;\n", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Labels.java", [
    (r'''            case DANGER -> SemanticColor.STATUS_DANGER;
        };
    }
''', r'''            case DANGER -> SemanticColor.STATUS_DANGER;
        };
    }
    /** A hue's glyph colour, as an icon of that hue is drawn (NONE, which no swatch has, as a plain glyph). */
    static SemanticColor hue(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> SemanticColor.TEXT_SECONDARY;
        };
    }
    /** An {@code IconSize.SWATCH} icon: a solid square of {@code color}, {@code extent} pixels wide. */
    record Swatch(Color color, int extent) implements javax.swing.Icon {
        @Override public int getIconWidth() { return extent; }
        @Override public int getIconHeight() { return extent; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) { graphics.setColor(color); graphics.fillRect(x, y, extent, extent); }
    }
''', 1),
    ("            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();\n",
     "            var icon = (Nodes.Icon)node; var label = (JLabel)component; state();\n"
     "            if (icon.size() == Nodes.IconSize.SWATCH) {\n"
     "                // A swatch is its hue, whatever the composition: no tile, no artwork, no selection ink (spec 8.2).\n"
     "                int extent = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.SWATCH));\n"
     "                ((LiteralLabel)label).iconOffset = 0;\n"
     "                label.setIcon(new Swatch(ThemePainting.color(context(), hue(icon.hue())), extent)); label.setDisabledIcon(label.getIcon());\n"
     "                label.getAccessibleContext().setAccessibleName(icon.accessibleName()); label.setToolTipText(icon.accessibleName());\n"
     "                return;\n"
     "            }\n", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.SwatchesTest --tests dev.jasper.ui.swing.TextRolesTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src
git commit -F - <<'EOF'
SDK 0.18: IconSize.SWATCH

A swatch is a solid square of the icon's hue at caption size (the Run
window's hue squares, a radio's swatch). Its hue is not NONE and it takes
no severity; the default look paints the hue's glyph colour.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: `Nodes.Separator`

A Separator divides a toolbar row into groups (SFTP's ← → ⟳ | Hidden files | Upload ▾). It is a leaf node, valid only
between two items of an INLINE or ACTIONS stack: `ViewSpec` refuses it first, last, beside another separator,
anywhere else, or as a view's root, and list rows keep refusing it (Rulings 2 and 3). The default look draws a 1 px
`border.subtle` rule as tall as a control glyph and hides it while every item on one side of it is hidden (Rulings 4).
`KitValuesTest`'s every-node test now counts node classes inside its stacks and forms, since a Separator cannot be a
root.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:12,656`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:12,643` (permits and the record)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java:3-7,15,21,35,64,66`, `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java` (same)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:35`
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/Separators.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java:20,53`, `jasper-ui/src/main/java/dev/jasper/ui/swing/Stacks.java:147`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `separators`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/SeparatorsTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java:11,33-34`

**Interfaces:**
- Consumes: nothing from Tasks 1 to 4.
- Produces: `record Nodes.Separator(String id, ControlState state) implements Node` in the SDK and model (permitted
  after `Custom`); `ViewSpec`'s private `place(Node parent, Node child)` (Task 6 adds the Notice rule to it) and
  `separators(List<Node>)`; `Separators.SeparatorControl`, `Separators.Rule`, `static void
  Separators.settle(List<ControlRegistry.Control>)`; `static Set<Class<?>> KitValuesTest.classes(List<Node>)` (Tasks 6
  and 7 add their nodes to the list it counts).

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/SeparatorsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Separator: a hairline between two shown items of an INLINE or ACTIONS stack. */
@SuppressWarnings("try")
class SeparatorsTest {
    static final ControlState READY = ControlState.READY, HIDDEN = new ControlState(false, true, false, "");
    static Nodes.Button button(String id, ControlState state) { return new Nodes.Button(id, state, id, "", ButtonKind.GHOST, id, ""); }
    static ViewSpec bar(ControlState a, ControlState b, ControlState c) {
        return new ViewSpec(new Stack("bar", READY, LayoutRole.ACTIONS, List.of(button("a", a), new Separator("first", READY), button("b", b),
            new Separator("second", READY), button("c", c))));
    }
    static boolean shown(KitView view, String id) { return find(view, id, JComponent.class).isVisible(); }

    @Test void aSeparatorIsAHairlineBetweenItsNeighbours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var spec = new ViewSpec(new Stack("toolbar", READY, LayoutRole.INLINE, List.of(button("back", READY), new Separator("rule", READY), button("hidden-files", READY))));
            try (var view = new KitView(context, spec, e -> {})) {
                var image = KitFixtures.render(view, 400, 40, 1);
                var rule = find(view, "rule", JComponent.class);
                var back = find(view, "back", JComponent.class);
                var next = find(view, "hidden-files", JComponent.class);
                assertThat(rule.isVisible()).isTrue();
                assertThat(rule.getWidth()).isEqualTo(UIScale.scale(context.metrics().value(DesignMetrics.Role.HAIRLINE)));
                assertThat(rule.getHeight()).isEqualTo(UIScale.scale(context.metrics().value(DesignMetrics.Role.ICON_CONTROL)));
                assertThat(rule.getX()).isGreaterThanOrEqualTo(back.getX() + back.getWidth()).isLessThan(next.getX());
                assertThat(rule.getAccessibleContext().getAccessibleRole()).isEqualTo(javax.accessibility.AccessibleRole.SEPARATOR);
                var at = SwingUtilities.convertPoint(rule.getParent(), rule.getX(), rule.getY() + rule.getHeight() / 2, view);
                assertThat(image.getRGB(at.x, at.y)).isEqualTo(KitFixtures.marker(SemanticColor.BORDER_SUBTLE).getRGB());
            } finally { release(context); }
        });
    }

    @Test void aSeparatorWithNoShownItemOnOneSideIsHidden() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, bar(READY, READY, READY), e -> {})) {
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).containsExactly(true, true);
                view.update(bar(HIDDEN, READY, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("nothing shown before the first").containsExactly(false, true);
                view.update(bar(READY, HIDDEN, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("one rule between a and c").containsExactly(true, false);
                view.update(bar(READY, READY, HIDDEN));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("nothing shown after the second").containsExactly(true, false);
                view.update(bar(READY, READY, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).containsExactly(true, true);
                view.update(bar(HIDDEN, READY, READY));
                view.control("first").refresh();
                assertThat(shown(view, "first")).as("a refresh (an appearance change) keeps it hidden").isFalse();
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 5 step 1: the tests that pin Separator."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
PLACEMENT = r'''    /** Plan 3b (spec 8.2): a Separator stands between two items of an INLINE or ACTIONS stack, and nowhere else. */
    @Test void separatorsStandBetweenTwoItemsOfAnInlineOrActionsStack() {
        var rule = new Separator("rule", ControlState.READY);
        for (var role : List.of(LayoutRole.INLINE, LayoutRole.ACTIONS))
            assertThatCode(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, role, List.of(text("a"), rule, text("b"))))).doesNotThrowAnyException();
        for (var children : List.of(List.<Node>of(rule, text("a")), List.<Node>of(text("a"), rule),
                List.<Node>of(text("a"), rule, new Separator("again", ControlState.READY), text("b"))))
            assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, LayoutRole.INLINE, children)))
                .withMessageContaining("between two items");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, LayoutRole.VERTICAL,
            List.of(text("a"), rule, text("b"))))).withMessageContaining("INLINE or ACTIONS");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(rule)).withMessageContaining("INLINE or ACTIONS");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Card("card", ControlState.READY, CardKind.FORM,
            List.of(text("a"), rule, text("b"))))).withMessageContaining("INLINE or ACTIONS");
        // List rows hold presentation nodes only; a separator is not one, even inside a row's INLINE stack.
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(new Nodes.Stack("line", ControlState.READY,
            LayoutRole.INLINE, List.of(text("a"), rule, text("b")))), List.of(), List.of())).withMessageContaining("Unsupported inline row node: Separator");
    }
'''
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", PLACEMENT + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", PLACEMENT + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (''' static Header header(){return new Header("header",READY,"Title",OptionalInt.of(2),Optional.of(text("context")),Optional.of(button("primary")),List.of());}
''', ''' static Header header(){return new Header("header",READY,"Title",OptionalInt.of(2),Optional.of(text("context")),Optional.of(button("primary")),List.of());}
 /** The node classes in {@code nodes}, those inside their stacks and their forms' sections included (a Separator or a Notice is never a root). */
 static Set<Class<?>> classes(List<Node> nodes){var found=new HashSet<Class<?>>();for(var node:nodes){found.add(node.getClass());if(node instanceof Nodes.Stack stack)found.addAll(classes(stack.children()));if(node instanceof Form form)found.addAll(classes(form.sections()));}return found;}
''', 1),
    ('''   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
  assertThat(nodes.stream().map(Object::getClass).distinct()).hasSize(Node.class.getPermittedSubclasses().length);
''', '''   new Nodes.Stack("separated",READY,LayoutRole.INLINE,List.of(text("before"),new Separator("separator",READY),text("after"))),
   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
  assertThat(classes(nodes)).hasSize(Node.class.getPermittedSubclasses().length);
''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): SFTP's navigation row, its groups divided by separators.
        result.add(new Fixture("separators", "", "normal", 420, 44, new ViewSpec(new Nodes.Stack("sftp-toolbar", ControlState.READY, LayoutRole.INLINE, List.of(
            new Nodes.Button("toolbar-back", ControlState.READY, "", "back", ButtonKind.GHOST, "back", "Back"),
            new Nodes.Button("toolbar-forward", ControlState.READY, "", "forward", ButtonKind.GHOST, "forward", "Forward"),
            new Nodes.Button("toolbar-refresh", ControlState.READY, "", "refresh", ButtonKind.GHOST, "refresh", "Refresh"),
            new Nodes.Separator("toolbar-first", ControlState.READY),
            new Nodes.Button("toolbar-hidden", ControlState.READY, "Hidden files", "", ButtonKind.GHOST, "hidden", ""),
            new Nodes.Separator("toolbar-second", ControlState.READY),
            new MenuButton("toolbar-upload", ControlState.READY, "Upload", "upload", ButtonKind.GHOST, "",
                List.of(new dev.jasper.ui.model.MenuItem("upload-files", "Files…", "", "files", true, false)), ""))))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.SeparatorsTest --continue`
Expected: FAIL. Both test compiles report "cannot find symbol … class Separator".

- [ ] **Step 3: Add the node, its rules and its rule**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/Separators.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.List;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/** SDK 0.18's Separator (spec 8.2): a hairline between two shown items of an INLINE or ACTIONS stack. */
final class Separators {
    private Separators() {}

    static final class SeparatorControl extends ControlRegistry.Control {
        /** Whether a shown item stands on each side of it in its stack, as {@link Separators#settle} last found. */
        boolean between = true;
        SeparatorControl(KitView view, Nodes.Separator node) {
            super(view, node, new Rule(view.context()));
            refresh();
        }
        @Override void refresh() {
            state();
            if (!between) component.setVisible(false);
            component.revalidate(); component.repaint();
        }
    }

    /** One hairline in {@code border.subtle}, as tall as a control glyph; the stack's row centres it. */
    static final class Rule extends JSeparator {
        final KitContext context;
        Rule(KitContext context) { super(SwingConstants.VERTICAL); this.context = context; setOpaque(false); }
        @Override public Dimension getPreferredSize() {
            if (context == null) return super.getPreferredSize(); // the look and feel may measure during construction
            return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL));
        }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics graphics) {
            int stroke = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            graphics.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
            graphics.fillRect((getWidth() - stroke) / 2, 0, stroke, getHeight());
        }
    }

    /**
     * Shows each separator among a stack's {@code controls} only while a shown item stands before it, with no other shown
     * separator since that item, and a shown item stands after it: hiding the items on one side (their descriptor
     * state) hides it too, so a row never starts, ends or doubles with a rule.
     */
    static void settle(List<ControlRegistry.Control> controls) {
        SeparatorControl pending = null;
        boolean item = false;
        for (var control : controls) {
            if (control instanceof SeparatorControl separator) {
                separator.between = false;
                if (item && pending == null && separator.node.state().visible()) pending = separator;
            } else if (control.node.state().visible()) {
                if (pending != null) { pending.between = true; pending = null; }
                item = true;
            }
        }
        for (var control : controls) if (control instanceof SeparatorControl separator) separator.refresh();
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 5 step 3: Separator in the SDK, the model, ViewSpec, the projection and the kit."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
SEPARATOR = r'''    /**
     * A thin rule between two items of an INLINE or ACTIONS stack, dividing a toolbar row into groups; never a stack's
     * first or last child and never beside another separator. It is not shown while every item on one side of it is
     * hidden.
     *
     * @since 0.18.0
     */
    public record Separator(String id, ControlState state) implements Node {
        public Separator { ModelChecks.id(id); Objects.requireNonNull(state); }
    }

    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
'''
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [
        ("Table, Tree, Breadcrumbs, Custom {", "Table, Tree, Breadcrumbs, Custom, Separator {", 1),
        ("    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {\n", SEPARATOR, 1)])
for spec in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java", "jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java"]:
    edit(spec, [
        ("import java.util.HashSet;\n", "import java.util.HashSet;\nimport java.util.List;\n", 1),
        ("        validate(root, new HashSet<>(), new HashSet<>(), lists, listRefs, 0, new int[]{0});\n",
         "        place(null, root);\n        validate(root, new HashSet<>(), new HashSet<>(), lists, listRefs, 0, new int[]{0});\n", 1),
        ("    private static void tree(TreeItem item, Set<String> ids, Set<String> branches, int depth) {\n", r'''    /** Spec 8.2: where a node may stand ({@code parent} is null at the root). A Separator belongs only in an INLINE or ACTIONS stack. */
    private static void place(Node parent, Node child) {
        if (child instanceof Separator && !(parent instanceof Stack stack && (stack.role() == LayoutRole.INLINE || stack.role() == LayoutRole.ACTIONS)))
            throw new IllegalArgumentException("A Separator belongs only in an INLINE or ACTIONS stack");
    }
    /** A Separator stands between two items: never a stack's first or last child, never beside another separator. */
    private static void separators(List<Node> children) {
        for (int i = 0; i < children.size(); i++)
            if (children.get(i) instanceof Separator && (i == 0 || i == children.size() - 1 || children.get(i - 1) instanceof Separator))
                throw new IllegalArgumentException("A Separator stands between two items, never first, last or beside another separator");
    }
    private static void tree(TreeItem item, Set<String> ids, Set<String> branches, int depth) {
''', 1),
        ("            case Stack n -> children.addAll(n.children());\n",
         "            case Stack n -> { children.addAll(n.children()); separators(n.children()); }\n", 1),
        ("Strength _, Breadcrumbs _, Custom _ -> { }", "Strength _, Breadcrumbs _, Custom _, Separator _ -> { }", 1),
        ("        for (var child : children) validate(child, ids, contentIds, lists, listRefs, depth + 1, count);\n",
         "        for (var child : children) place(node, child);\n        for (var child : children) validate(child, ids, contentIds, lists, listRefs, depth + 1, count);\n", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    (" case dev.jasper.sdk.ui.Nodes.Custom n -> new dev.jasper.ui.model.Nodes.Custom(n.id(), toModule(n.state()), n.contentId(), dev.jasper.ui.model.Nodes.CustomKind.valueOf(n.kind().name()), n.accessibleName());\n",
     " case dev.jasper.sdk.ui.Nodes.Custom n -> new dev.jasper.ui.model.Nodes.Custom(n.id(), toModule(n.state()), n.contentId(), dev.jasper.ui.model.Nodes.CustomKind.valueOf(n.kind().name()), n.accessibleName());\n"
     " case dev.jasper.sdk.ui.Nodes.Separator n -> new dev.jasper.ui.model.Nodes.Separator(n.id(), toModule(n.state()));\n", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java", [
    ("            case Nodes.Table _, Nodes.Tree _, Nodes.Breadcrumbs _ -> {}\n",
     "            case Nodes.Table _, Nodes.Tree _, Nodes.Breadcrumbs _, Nodes.Separator _ -> {}\n", 1),
    ("            case Nodes.Breadcrumbs crumbs -> new BreadcrumbControls.BreadcrumbsControl(view, crumbs);\n",
     "            case Nodes.Breadcrumbs crumbs -> new BreadcrumbControls.BreadcrumbsControl(view, crumbs);\n"
     "            case Nodes.Separator separator -> new Separators.SeparatorControl(view, separator);\n", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Stacks.java", [
    ("            mount(component, wanted);\n            captions = controls.stream()",
     "            mount(component, wanted);\n            Separators.settle(controls);\n            captions = controls.stream()", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.SeparatorsTest --tests dev.jasper.ui.swing.KitViewTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeKit*' :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src
git commit -F - <<'EOF'
SDK 0.18: Separator

A Separator divides a toolbar row into groups: valid only between two
items of an INLINE or ACTIONS stack, never first, last, beside another or
elsewhere, and never in a list row. The default look draws a hairline as
tall as a control glyph and hides it while every item on one side of it
is hidden.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: `Nodes.Notice`

A Notice tells the person something about a form or a pane (FTP's "Warning · Plain FTP sends the password
unencrypted"): a severity, one non-blank sentence and at most one SECONDARY or LINK action, as a form section or a
VERTICAL stack child only (Rulings 2). Its action is a node of the view, so every children walk (`ViewSpec`,
`ControlRegistry`, `KitContent`, `FakeKitView`) gains it and icon validation, anchors and ids reach it. The default
look (Rulings 5) is the new `Notices` control; its two painted pairs per severity join `PaintedContrastTest`.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:12,656`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:12,643` (permits and the record)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java:41`, `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java:41` (the case and `place`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java` (after the Separator case)
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/Notices.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java:21,53,68`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java:24-40`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java:113`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `notice-severities`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/NoticesTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java:24-31,46-62,188`

**Interfaces:**
- Consumes: Task 5's `ViewSpec.place` and the `KitValuesTest` node list counted by `classes`.
- Produces: `record Nodes.Notice(String id, ControlState state, Severity severity, String text, List<Button>
  actions) implements Node` with the convenience `Notice(String id, ControlState state, Severity severity, String
  text)`, in the SDK and model (permitted after `Separator`); package-private `Notices.word(Severity)`,
  `Notices.glyph(Severity)`, `Notices.ground(Severity)`, `Notices.border(Severity)`, `Notices.text(Severity)` and
  `Notices.edge(Severity)` (the declared `PaintPair`s), `Notices.NoticeControl` (components `<id>`, `<id>.glyph`,
  `<id>.text`). Task 9's contract view holds a Notice with an action.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/NoticesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SurfaceContrast;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Notice: the severity as a glyph and its word before one sentence, on the severity's ground. */
@SuppressWarnings("try")
class NoticesTest {
    static final ControlState READY = ControlState.READY;
    static final String FTP = "Plain FTP sends the password unencrypted";

    @Test void aNoticeShowsItsSeverityAsAGlyphAndItsWordBeforeItsText() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var notices = new ArrayList<Node>();
            for (var severity : Severity.values()) notices.add(new Notice("notice-" + severity, READY, severity, FTP));
            try (var view = new KitView(context, new ViewSpec(new Stack("notices", READY, LayoutRole.VERTICAL, notices)), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                for (var severity : Severity.values()) {
                    String id = "notice-" + severity;
                    var text = find(view, id + ".text", JLabel.class);
                    assertThat(text.getText()).isEqualTo(Notices.word(severity) + " · " + FTP);
                    assertThat(text.getForeground()).isEqualTo(SurfaceContrast.painted(context.theme(), Notices.text(severity)));
                    assertThat(text.getAccessibleContext().getAccessibleName()).isEqualTo(text.getText());
                    var glyph = find(view, id + ".glyph", JLabel.class);
                    assertThat(glyph.getIcon()).isNotNull();
                    assertThat(glyph.getAccessibleContext().getAccessibleName()).isEqualTo(Notices.word(severity));
                }
                assertThat(List.of(Notices.word(Severity.INFO), Notices.word(Severity.WARNING), Notices.word(Severity.DANGER)))
                    .containsExactly("Information", "Warning", "Error");
            } finally { release(context); }
        });
    }

    @Test void aLongNoticeWrapsBesideItsGlyphAndKeepsItsActionAtTheTrailingEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var choose = new Nodes.Button("choose", READY, "Choose…", "", ButtonKind.SECONDARY, "choose", "");
            var wordy = new Notice("wordy", READY, Severity.DANGER,
                "The folder no longer exists, so the server cannot start until you choose another folder to share", List.of(choose));
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(wordy))), events::add)) {
                KitFixtures.render(view, 300, 160, 1);
                var surface = find(view, "wordy", JComponent.class);
                var text = find(view, "wordy.text", JLabel.class);
                var glyph = find(view, "wordy.glyph", JLabel.class);
                var button = find(view, "choose", JButton.class);
                assertThat(text.getHeight()).as("the text wraps").isGreaterThan(text.getFontMetrics(text.getFont()).getHeight());
                assertThat(glyph.getX() + glyph.getWidth()).isLessThanOrEqualTo(text.getX());
                assertThat(text.getX() + text.getWidth()).isLessThanOrEqualTo(button.getX());
                assertThat(button.getX() + button.getWidth()).isEqualTo(surface.getWidth() - surface.getInsets().right);
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("choose"));
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 6 step 1: the tests that pin Notice."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
NOTICE = r'''    /** Plan 3b (spec 8.2): a Notice is one sentence with at most one SECONDARY or LINK action, in a form's sections or a VERTICAL stack. */
    @Test void noticesAreValidatedAndPlacedInFormsOrVerticalStacks() {
        var link = new Button("details", ControlState.READY, "Details", "", ButtonKind.LINK, "details", "");
        var notice = new Notice("notice", ControlState.READY, Severity.WARNING, "Plain FTP sends the password unencrypted", List.of(link));
        assertThat(new Notice("plain", ControlState.READY, Severity.INFO, "Saved").actions()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Notice("blank", ControlState.READY, Severity.INFO, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Notice("two", ControlState.READY, Severity.INFO, "Saved", List.of(link,
            new Button("more", ControlState.READY, "More", "", ButtonKind.LINK, "more", "")))).withMessageContaining("at most one action");
        for (var kind : List.of(ButtonKind.PRIMARY, ButtonKind.GHOST, ButtonKind.DESTRUCTIVE_GHOST))
            assertThatIllegalArgumentException().isThrownBy(() -> new Notice("kind", ControlState.READY, Severity.INFO, "Saved",
                List.of(new Button("go", ControlState.READY, "Go", "", kind, "go", "")))).withMessageContaining("SECONDARY or LINK");
        assertThatCode(() -> new ViewSpec(new Nodes.Stack("body", ControlState.READY, LayoutRole.VERTICAL, List.of(notice)))).doesNotThrowAnyException();
        assertThatCode(() -> new ViewSpec(new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(notice),
            Optional.empty(), List.of()))).doesNotThrowAnyException();
        for (Node misplaced : List.<Node>of(notice, new Nodes.Stack("bar", ControlState.READY, LayoutRole.INLINE, List.of(notice)),
                new Card("card", ControlState.READY, CardKind.FORM, List.of(notice)),
                new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.of(notice), List.of(), Optional.empty(), List.of())))
            assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(misplaced)).withMessageContaining("form's sections or a VERTICAL stack");
        // The action is a node of the view: its id is checked with the others, and list rows take no notice.
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("details", ControlState.READY, LayoutRole.VERTICAL, List.of(notice))))
            .withMessageContaining("Duplicate node: details");
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(notice), List.of(), List.of()))
            .withMessageContaining("Unsupported inline row node: Notice");
    }
'''
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", NOTICE + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", NOTICE + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    ('''   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
''', '''   new Nodes.Stack("noticed",READY,LayoutRole.VERTICAL,List.of(new Notice("notice",READY,Severity.WARNING,"Plain FTP sends the password unencrypted",
    List.of(new Button("details",READY,"Details","",ButtonKind.LINK,"details",""))))),
   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
''', 1)])
PAINTED = "jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java"
edit(PAINTED, [
    (''' * no guard repairs, is painted as authored. The default look declares no other pair (spec 7.3), so it stays
 * pixel-identical.
''', ''' * no guard repairs, is painted as authored. The default look declares no other pair for the controls it had (spec 7.3),
 * so they stay pixel-identical; SDK 0.18's Notice declares its own.
''', 1),
    ('''        new Declared("selected preview caption", new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedPreview));
''', '''        new Declared("selected preview caption", new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedPreview),
        // Plan 3b: a Notice's text on its severity's ground, and its border held apart from that ground.
        new Declared("information notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.DANGER)),
        new Declared("information notice border", new PaintPair(DerivedColor.INFO_BORDER, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning notice border", new PaintPair(DerivedColor.WARNING_BORDER, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error notice border", new PaintPair(DerivedColor.DANGER_BORDER, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.DANGER)));
''', 1),
    ('''    private static BufferedImage paint(JComponent component) {
''', '''    /** A notice of {@code severity}: its text label's colour. */
    private static boolean noticeText(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        return withNotice(theme, severity, view -> KitTestSupport.require(view, "notice.text", JLabel.class).getForeground().equals(expected));
    }

    /** The middle of a notice's top edge, where its border is drawn over its ground. */
    private static boolean noticeBorder(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        return withNotice(theme, severity, view -> {
            var surface = KitTestSupport.require(view, "notice", JComponent.class);
            return paint(surface).getRGB(surface.getWidth() / 2, 0) == expected.getRGB();
        });
    }

    private static boolean withNotice(GeneratedTheme theme, Nodes.Severity severity, java.util.function.Predicate<KitView> check) {
        var context = KitFixtures.context(theme);
        var spec = new ViewSpec(new Nodes.Stack("root", ControlState.READY, Nodes.LayoutRole.VERTICAL,
            List.of(new Nodes.Notice("notice", ControlState.READY, severity, "Plain FTP sends the password unencrypted"))));
        try (var view = new KitView(context, spec, event -> { })) {
            KitFixtures.render(view, 400, 80, 1);
            return check.test(view);
        } finally { context.icons().close(); context.binding().close(); }
    }

    private static BufferedImage paint(JComponent component) {
''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): a notice of each severity, the last with its action.
        var notices = new ArrayList<Nodes.Node>();
        for (var severity : Nodes.Severity.values()) notices.add(new Nodes.Notice("notice-" + severity, ControlState.READY, severity,
            switch (severity) { case INFO -> "Changes apply when the server restarts"; case WARNING -> "Plain FTP sends the password unencrypted"; case DANGER -> "The folder no longer exists"; },
            severity == Nodes.Severity.DANGER ? List.of(new Nodes.Button("notice-choose", ControlState.READY, "Choose…", "", ButtonKind.SECONDARY, "choose", "")) : List.of()));
        result.add(new Fixture("notice-severities", "", "normal", 420, 170, new ViewSpec(new Nodes.Stack("notice-severities", ControlState.READY, LayoutRole.VERTICAL, notices))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.NoticesTest --continue`
Expected: FAIL. Both test compiles report "cannot find symbol … class Notice" (and jasper-ui's "… Notices").

- [ ] **Step 3: Add the node, its walks and its control**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/Notices.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Notice (spec 8.2): the severity's glyph and word, then one sentence, on the severity's ground. */
final class Notices {
    private Notices() {}

    /** The word the presentation puts before a notice's text. */
    static String word(Nodes.Severity severity) {
        return switch (severity) { case INFO -> "Information"; case WARNING -> "Warning"; case DANGER -> "Error"; };
    }

    /** The severity's glyph. */
    static String glyph(Nodes.Severity severity) {
        return switch (severity) { case INFO -> "info"; case WARNING -> "warning"; case DANGER -> "error"; };
    }

    /** The severity's ground, one of spec 7.1's derived roles. */
    static DerivedColor ground(Nodes.Severity severity) {
        return switch (severity) { case INFO -> DerivedColor.INFO_GROUND; case WARNING -> DerivedColor.WARNING_GROUND; case DANGER -> DerivedColor.DANGER_GROUND; };
    }

    /** The severity's border, one of spec 7.1's derived roles. */
    static DerivedColor border(Nodes.Severity severity) {
        return switch (severity) { case INFO -> DerivedColor.INFO_BORDER; case WARNING -> DerivedColor.WARNING_BORDER; case DANGER -> DerivedColor.DANGER_BORDER; };
    }

    /** The notice's text on its ground, held to the text floor. */
    static PaintPair text(Nodes.Severity severity) {
        return new PaintPair(SemanticColor.TEXT_PRIMARY, ground(severity), SurfaceContrast.Guard.TEXT);
    }

    /** The notice's border on its ground, held to the structural floor (a theme may author the two alike). */
    static PaintPair edge(Nodes.Severity severity) {
        return new PaintPair(border(severity), ground(severity), SurfaceContrast.Guard.STRUCTURAL);
    }

    static final class NoticeControl extends ControlRegistry.Control {
        final JLabel glyph = Labels.literal();
        final Labels.LiteralLabel text = (Labels.LiteralLabel) Labels.literal();
        List<ControlRegistry.Control> actions = List.of();

        NoticeControl(KitView view, Nodes.Notice node) {
            super(view, node, new Surface(view.context()));
            KitOwnership.mark(glyph, node.id() + ".glyph");
            KitOwnership.mark(text, node.id() + ".text");
            text.wrap = true;
            component.setLayout(new NoticeLayout(this));
            Stacks.mount(component, List.<JComponent>of(glyph, text));
            refresh();
        }

        @Override void refresh() {
            state();
            var notice = (Nodes.Notice) node; var theme = context().theme();
            ((Surface) component).severity = notice.severity();
            text.setText(word(notice.severity()) + " · " + notice.text());
            text.setFont(ThemePainting.font(context(), Typography.Role.BODY));
            text.setForeground(SurfaceContrast.painted(theme, Notices.text(notice.severity())));
            text.setEnabled(component.isEnabled());
            text.getAccessibleContext().setAccessibleName(text.getText());
            int size = com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.CONTROL));
            // The severity colour, held to 3:1 on the ground by the registry's surface tint.
            glyph.setIcon(context().icons().icon(glyph(notice.severity()), ground(notice.severity()).color(theme),
                theme.semantic().color(Labels.tint(notice.severity())), size));
            glyph.setDisabledIcon(glyph.getIcon());
            glyph.setEnabled(component.isEnabled());
            glyph.getAccessibleContext().setAccessibleName(word(notice.severity()));
            int vertical = ThemePainting.metric(context(), DesignMetrics.Role.NOTICE_VERTICAL_PADDING);
            int horizontal = ThemePainting.metric(context(), DesignMetrics.Role.NOTICE_HORIZONTAL_PADDING);
            component.setBorder(new EmptyBorder(vertical, horizontal, vertical, horizontal));
            component.revalidate(); component.repaint();
        }

        @Override void children(List<ControlRegistry.Control> controls) {
            actions = List.copyOf(controls);
            var wanted = new ArrayList<JComponent>(List.of(glyph, text));
            for (var control : controls) wanted.add(control.component);
            Stacks.mount(component, wanted);
        }
    }

    /** The notice's box: the severity's ground inside its guarded border. */
    static final class Surface extends JPanel {
        final KitContext context;
        Nodes.Severity severity = Nodes.Severity.INFO;

        Surface(KitContext context) { this.context = context; setOpaque(false); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                var theme = context.theme();
                ThemePainting.rounded(g, this, context, ground(severity).color(theme), SurfaceContrast.painted(theme, edge(severity)),
                    false, false, DesignMetrics.Role.CONTROL_RADIUS);
            } finally { g.dispose(); }
        }
    }

    /** The glyph on the text's first line, the wrapping text, then the action centred at the trailing edge. */
    static final class NoticeLayout implements Composition.WidthLayout {
        final NoticeControl owner;

        NoticeLayout(NoticeControl owner) { this.owner = owner; }

        private int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP); }
        private int actionGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.NOTICE_BUTTON_GAP); }

        /** The shown action's component, or null. */
        private Component action() {
            for (var control : owner.actions) if (control.component.isVisible()) return control.component;
            return null;
        }

        private int textWidth(Container parent, int width) {
            Insets insets = parent.getInsets(); var action = action();
            int used = owner.glyph.getPreferredSize().width + gap() + (action == null ? 0 : actionGap() + action.getPreferredSize().width);
            return Math.max(1, width - insets.left - insets.right - used);
        }

        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets(); var action = action();
            int content = Math.max(Composition.height(owner.text, textWidth(parent, width)), owner.glyph.getPreferredSize().height);
            if (action != null) content = Math.max(content, action.getPreferredSize().height);
            return insets.top + insets.bottom + content;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets(); var action = action();
            int width = insets.left + insets.right + owner.glyph.getPreferredSize().width + gap() + owner.text.getPreferredSize().width
                + (action == null ? 0 : actionGap() + action.getPreferredSize().width);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets(); var action = action();
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int textWidth = textWidth(parent, parent.getWidth()), textHeight = Composition.height(owner.text, textWidth);
            int textY = insets.top + Math.max(0, (height - textHeight) / 2);
            int line = owner.text.getFontMetrics(owner.text.getFont()).getHeight();
            var glyph = owner.glyph.getPreferredSize();
            owner.glyph.setBounds(insets.left, textY + Math.max(0, (Math.min(line, textHeight) - glyph.height) / 2), glyph.width, glyph.height);
            owner.text.setBounds(insets.left + glyph.width + gap(), textY, textWidth, textHeight);
            if (action != null) {
                var size = action.getPreferredSize();
                action.setBounds(parent.getWidth() - insets.right - size.width, insets.top + Math.max(0, (height - size.height) / 2), size.width, size.height);
            }
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 6 step 3: Notice in the SDK, the model, ViewSpec, the projection, the walks and the kit."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
NOTICE = r'''    /**
     * A message for the person reading a form or a pane: a severity, one sentence and at most one SECONDARY or LINK
     * action. The presentation shows the severity as a glyph and its word ("Warning") before the text. A Notice is a
     * section of a form or a child of a VERTICAL stack, nowhere else.
     *
     * @since 0.18.0
     */
    public record Notice(String id, ControlState state, Severity severity, String text, List<Button> actions) implements Node {
        public Notice {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(severity);
            ModelChecks.label(text, "text");
            actions = List.copyOf(actions);
            if (actions.size() > 1) throw new IllegalArgumentException("A Notice has at most one action");
            if (actions.stream().anyMatch(button -> button.kind() != ButtonKind.SECONDARY && button.kind() != ButtonKind.LINK))
                throw new IllegalArgumentException("A Notice's action is SECONDARY or LINK");
        }
        /** A notice without an action. */
        public Notice(String id, ControlState state, Severity severity, String text) { this(id, state, severity, text, List.of()); }
    }

    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
'''
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [
        ("Breadcrumbs, Custom, Separator {", "Breadcrumbs, Custom, Separator, Notice {", 1),
        ("    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {\n", NOTICE, 1)])
for spec in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java", "jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java"]:
    edit(spec, [
        ("    /** Spec 8.2: where a node may stand ({@code parent} is null at the root). A Separator belongs only in an INLINE or ACTIONS stack. */\n"
         "    private static void place(Node parent, Node child) {\n",
         "    /**\n     * Spec 8.2: where a node may stand ({@code parent} is null at the root). A Notice belongs in a form's sections or a\n"
         "     * VERTICAL stack, a Separator only in an INLINE or ACTIONS stack.\n     */\n"
         "    private static void place(Node parent, Node child) {\n"
         "        if (child instanceof Notice && !(parent instanceof Form form && form.sections().contains(child)\n"
         "                || parent instanceof Stack stack && stack.role() == LayoutRole.VERTICAL))\n"
         "            throw new IllegalArgumentException(\"A Notice belongs in a form's sections or a VERTICAL stack\");\n", 1),
        ("            case Empty n -> { children.add(n.icon()); children.addAll(n.actions()); }\n",
         "            case Empty n -> { children.add(n.icon()); children.addAll(n.actions()); }\n            case Notice n -> children.addAll(n.actions());\n", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    (" case dev.jasper.sdk.ui.Nodes.Separator n -> new dev.jasper.ui.model.Nodes.Separator(n.id(), toModule(n.state()));\n",
     " case dev.jasper.sdk.ui.Nodes.Separator n -> new dev.jasper.ui.model.Nodes.Separator(n.id(), toModule(n.state()));\n"
     " case dev.jasper.sdk.ui.Nodes.Notice n -> new dev.jasper.ui.model.Nodes.Notice(n.id(), toModule(n.state()), dev.jasper.ui.model.Nodes.Severity.valueOf(n.severity().name()), n.text(), n.actions().stream().map(KitValues::toModule).toList());\n", 1)])
CHILDREN = ("            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }\n",
            "            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }\n            case Nodes.Notice n -> result.addAll(n.actions());\n", 1)
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java", [
    ("Nodes.Form _, Nodes.Split _ -> children(node).forEach(ControlRegistry::validate);",
     "Nodes.Form _, Nodes.Split _, Nodes.Notice _ -> children(node).forEach(ControlRegistry::validate);", 1),
    ("            case Nodes.Breadcrumbs crumbs -> new BreadcrumbControls.BreadcrumbsControl(view, crumbs);\n",
     "            case Nodes.Breadcrumbs crumbs -> new BreadcrumbControls.BreadcrumbsControl(view, crumbs);\n"
     "            case Nodes.Notice notice -> new Notices.NoticeControl(view, notice);\n", 1),
    CHILDREN])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java", [CHILDREN])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKitView.java", [CHILDREN])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.NoticesTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeKit*' :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest --tests dev.jasper.app.appearance.PaintedContrastTest --continue`
Expected: BUILD SUCCESSFUL. `PaintedContrastTest.eachDeclaredPairIsPaintedAsItsGuardMakesItOverEveryPalette` covers the
six notice pairs over the theme set and the eight degenerate palettes, Catppuccin's equal `warning.border` and
`warning.ground` included.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src jasper-sdk-testkit/src
git commit -F - <<'EOF'
SDK 0.18: Notice

A Notice is a severity, one sentence and at most one SECONDARY or LINK
action, as a form section or a VERTICAL stack child. The default look
draws the severity's glyph and word before the text on the severity's
derived ground, the text held to 4.5:1 and the border to 1.3:1 (a theme
may author the border and the ground alike), with the action at the
trailing edge.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: `Nodes.Properties` with `PropertyRow`

Properties shows a selected thing's labelled values ("Properties — minecraft-local": Host, User, Port, Credentials). It
is a leaf node with a title (blank shows no heading) and one or more `PropertyRow(label, value, technical, severity)`
rows; a label is not blank and does not end with a colon (the presentation punctuates). The default look (Rulings 6) is
the new `PropertyLists` control; its three severity pairs join `PaintedContrastTest`.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:12,656`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:12,643` (permits and the records)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java:64`, `jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java:64`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java` (after the Notice case, and before the Numeric mapping)
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertyLists.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java:20,53`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `properties`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/PropertiesTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java`

**Interfaces:**
- Consumes: Task 6's `PaintedContrastTest` entries (the anchor after which the property pairs go) and the `KitValuesTest`
  node list.
- Produces: `record Nodes.PropertyRow(String label, String value, boolean technical, Optional<Severity> severity)` with
  the convenience `PropertyRow(String label, String value, boolean technical)`, and `record Nodes.Properties(String id,
  ControlState state, String title, List<PropertyRow> rows) implements Node` (permitted after `Notice`), in the SDK
  and model; package-private `PropertyLists.valuePair(Severity)` and `PropertyLists.PropertiesControl` (components
  `<id>.title`, `<id>.rows`, `<id>.label.<i>`, `<id>.value.<i>`). Task 9's contract view holds Properties.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/PropertiesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Properties: a heading over a card of label and value rows. */
@SuppressWarnings("try")
class PropertiesTest {
    static final ControlState READY = ControlState.READY;
    static Nodes.Properties properties(String title, List<PropertyRow> rows) { return new Nodes.Properties("properties", READY, title, rows); }
    static List<PropertyRow> host() {
        return List.of(new PropertyRow("Host", "192.168.1.124", true), new PropertyRow("User", "dustin", true),
            new PropertyRow("Credentials", "Vault locked", false, Optional.of(Severity.WARNING)));
    }
    static JLabel label(KitView view, String id) { return find(view, id, JLabel.class); }

    @Test void propertiesShowAHeadingOverALabelColumnBesideTheirValues() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(properties("Properties — minecraft-local", host())), e -> {})) {
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.title").isVisible()).isTrue();
                assertThat(label(view, "properties.title").getText()).isEqualTo("Properties — minecraft-local");
                int widest = 0;
                for (int i = 0; i < 3; i++) widest = Math.max(widest, label(view, "properties.label." + i).getPreferredSize().width);
                for (int i = 0; i < 3; i++) {
                    var name = label(view, "properties.label." + i); var value = label(view, "properties.value." + i);
                    assertThat(value.getX()).as("one value column").isEqualTo(label(view, "properties.value.0").getX());
                    assertThat(value.getX()).isGreaterThanOrEqualTo(name.getX() + widest);
                    assertThat(value.getY()).isEqualTo(name.getY());
                    assertThat(name.getLabelFor()).isSameAs(value);
                }
                assertThat(label(view, "properties.label.0").getText()).isEqualTo("Host");
                assertThat(label(view, "properties.value.0").getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.MONO));
                assertThat(label(view, "properties.value.2").getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
                assertThat(label(view, "properties.value.0").getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
                assertThat(label(view, "properties.value.2").getForeground())
                    .isEqualTo(SurfaceContrast.painted(context.theme(), PropertyLists.valuePair(Severity.WARNING)));
            } finally { release(context); }
        });
    }

    @Test void aBlankTitleShowsNoHeadingAndTheRowsFollowTheDescriptor() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(properties("", host())), e -> {})) {
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.title").isVisible()).isFalse();
                view.update(new ViewSpec(properties("", List.of(new PropertyRow("Port", "22", true)))));
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.value.0").getText()).isEqualTo("22");
                assertThat(find(view, "properties.label.1", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }

    @Test void aLongValueIsShortenedAndKeepsItsWholeTextAsItsTooltip() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            String path = "/Users/dustin/projects/jasper/a/rather/long/synthetic/folder/name";
            var rows = List.of(new PropertyRow("Folder", path, true), new PropertyRow("Note", "plain", false));
            try (var view = new KitView(context, new ViewSpec(properties("", rows)), e -> {})) {
                KitFixtures.render(view, 200, 100, 1);
                var value = label(view, "properties.value.0");
                assertThat(value.getWidth()).isLessThan(value.getPreferredSize().width);
                assertThat(value.getToolTipText()).isEqualTo(path);
                assertThat(value.getClientProperty("Jasper.leadingEllipsis")).isEqualTo(true);
                assertThat(label(view, "properties.value.1").getClientProperty("Jasper.leadingEllipsis")).isEqualTo(false);
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 7 step 1: the tests that pin Properties."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
PROPERTIES = r'''    /** Plan 3b (spec 8.2): properties hold one or more labelled values, their labels without a closing colon. */
    @Test void propertiesNeedRowsAndLabelsWithoutAColon() {
        var host = new PropertyRow("Host", "192.168.1.124", true);
        assertThat(host.severity()).isEmpty();
        assertThat(new Nodes.Properties("properties", ControlState.READY, "", List.of(host)).title()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Nodes.Properties("properties", ControlState.READY, "Properties", List.of()))
            .withMessageContaining("at least one row");
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRow(" ", "x", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRow("Host:", "x", false)).withMessageContaining("colon");
        assertThat(new PropertyRow("Time (UTC:local)", "12:30", false).label()).isEqualTo("Time (UTC:local)");
        assertThatNullPointerException().isThrownBy(() -> new PropertyRow("Host", null, false));
        var properties = new Nodes.Properties("properties", ControlState.READY, "Properties — minecraft-local", List.of(host,
            new PropertyRow("Credentials", "Vault locked", false, Optional.of(Severity.WARNING))));
        assertThatCode(() -> new ViewSpec(new Nodes.Stack("detail", ControlState.READY, LayoutRole.VERTICAL, List.of(properties)))).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(properties), List.of(), List.of()))
            .withMessageContaining("Unsupported inline row node: Properties");
    }
'''
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", PROPERTIES + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", PROPERTIES + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    ('''   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
''', '''   new Nodes.Properties("properties",READY,"Properties — minecraft-local",List.of(new PropertyRow("Host","192.168.1.124",true),
    new PropertyRow("Credentials","Vault locked",false,Optional.of(Severity.WARNING)))),
   new Custom("custom",READY,"document",CustomKind.EDITOR,"Document"));
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java", [
    (" * so they stay pixel-identical; SDK 0.18's Notice declares its own.\n",
     " * so they stay pixel-identical; SDK 0.18's Notice and Properties declare their own.\n", 1),
    ('''            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.DANGER)));
''', '''            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.DANGER)),
        // Plan 3b: a property value coloured by its severity, held readable on the Properties card.
        new Declared("information property value", new PaintPair(ACCENT, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning property value", new PaintPair(STATUS_WARNING, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error property value", new PaintPair(STATUS_DANGER, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.DANGER)));
''', 1),
    ('''    private static BufferedImage paint(JComponent component) {
''', '''    /** A Properties row whose value has {@code severity}: the value label's colour. */
    private static boolean propertyValue(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        var context = KitFixtures.context(theme);
        var spec = new ViewSpec(new Nodes.Properties("properties", ControlState.READY, "",
            List.of(new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(severity)))));
        try (var view = new KitView(context, spec, event -> { })) {
            KitFixtures.render(view, 320, 80, 1);
            return KitTestSupport.require(view, "properties.value.0", JLabel.class).getForeground().equals(expected);
        } finally { context.icons().close(); context.binding().close(); }
    }

    private static BufferedImage paint(JComponent component) {
''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): a selected host's properties, three data values and one with a severity.
        result.add(new Fixture("properties", "", "normal", 320, 170, new ViewSpec(new Nodes.Properties("properties", ControlState.READY, "Properties — minecraft-local", List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("User", "dustin", true), new Nodes.PropertyRow("Port", "22", true),
            new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(Nodes.Severity.WARNING)))))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.PropertiesTest --continue`
Expected: FAIL. Both test compiles report "cannot find symbol … class PropertyRow" and "… Properties".

- [ ] **Step 3: Add the node and its control**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertyLists.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Properties (spec 8.2): a heading over a card of label and value rows. */
final class PropertyLists {
    private PropertyLists() {}

    /** A value coloured by its severity on the card, held to the text floor. */
    static PaintPair valuePair(Nodes.Severity severity) {
        return new PaintPair(Labels.tint(severity), SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    }

    static final class PropertiesControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal();
        final Cards.CardSurface card;
        final ArrayList<JLabel> labels = new ArrayList<>(), values = new ArrayList<>();

        PropertiesControl(KitView view, Nodes.Properties node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            card = new Cards.CardSurface(view.context());
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(card, node.id() + ".rows");
            card.setLayout(new Grid(this));
            Stacks.mount(component, List.<JComponent>of(title, card));
            refresh();
        }

        @Override void refresh() {
            state();
            var properties = (Nodes.Properties) node; var theme = context().theme();
            int count = properties.rows().size();
            while (labels.size() > count) { card.remove(labels.removeLast()); card.remove(values.removeLast()); }
            while (labels.size() < count) {
                int index = labels.size();
                var label = Labels.literal(); var value = Labels.literal();
                KitOwnership.mark(label, node.id() + ".label." + index);
                KitOwnership.mark(value, node.id() + ".value." + index);
                label.setLabelFor(value);
                labels.add(label); values.add(value); card.add(label); card.add(value);
            }
            for (int i = 0; i < count; i++) {
                var row = properties.rows().get(i); var label = labels.get(i); var value = values.get(i);
                label.setText(row.label());
                Composition.style(label, context(), Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
                value.setText(row.value());
                value.setToolTipText(row.value().isEmpty() ? null : row.value());
                value.setFont(ThemePainting.font(context(), row.technical() ? Typography.Role.MONO : Typography.Role.BODY));
                value.setForeground(row.severity().map(severity -> SurfaceContrast.painted(theme, valuePair(severity)))
                    .orElseGet(() -> ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY)));
                // A data value keeps its end in view when it is shortened, as monospaced text does.
                value.putClientProperty("Jasper.leadingEllipsis", row.technical());
                label.setEnabled(component.isEnabled()); value.setEnabled(component.isEnabled());
            }
            title.setText(properties.title());
            title.setVisible(!properties.title().isBlank());
            title.setEnabled(component.isEnabled());
            Composition.style(title, context(), Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE);
            card.setBorder(new EmptyBorder(pad, pad, pad, pad));
            component.setLayout(new Composition.Vertical(ThemePainting.metric(context(), DesignMetrics.Role.SECTION_LABEL_GAP)));
            component.revalidate(); component.repaint();
        }
    }

    /** A label column as wide as the widest label, then one value column; one line per row. */
    static final class Grid implements Composition.WidthLayout {
        final PropertiesControl owner;

        Grid(PropertiesControl owner) { this.owner = owner; }

        private int labelWidth() { return owner.labels.stream().mapToInt(label -> label.getPreferredSize().width).max().orElse(0); }
        private int columnGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP); }
        private int rowGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.ROW_GAP); }
        private int rowHeight(int index) {
            return Math.max(owner.labels.get(index).getPreferredSize().height, owner.values.get(index).getPreferredSize().height);
        }

        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int height = insets.top + insets.bottom;
            for (int i = 0; i < owner.labels.size(); i++) height += rowHeight(i) + (i == 0 ? 0 : rowGap());
            return height;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int values = owner.values.stream().mapToInt(value -> value.getPreferredSize().width).max().orElse(0);
            int width = insets.left + insets.right + labelWidth() + columnGap() + values;
            return new Dimension(width, height(parent, width));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int labelWidth = labelWidth(), x = insets.left + labelWidth + columnGap(), y = insets.top;
            int valueWidth = Math.max(0, parent.getWidth() - insets.right - x);
            for (int i = 0; i < owner.labels.size(); i++) {
                int height = rowHeight(i);
                owner.labels.get(i).setBounds(insets.left, y, labelWidth, height);
                owner.values.get(i).setBounds(x, y, valueWidth, height);
                y += height + rowGap();
            }
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 7 step 3: Properties and PropertyRow in the SDK, the model, ViewSpec, the projection and the kit."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
PROPERTIES = r'''    /**
     * One labelled value of {@link Properties}. {@code label} is not blank and does not end with a colon (the
     * presentation punctuates it); {@code technical} draws the value in the data face; a present {@code severity}
     * colours the value, held readable on its surface.
     *
     * @since 0.18.0
     */
    public record PropertyRow(String label, String value, boolean technical, Optional<Severity> severity) {
        public PropertyRow {
            ModelChecks.label(label, "label");
            if (label.strip().endsWith(":")) throw new IllegalArgumentException("A property label carries no colon; the presentation punctuates it");
            Objects.requireNonNull(value); Objects.requireNonNull(severity);
        }
        /** A value without a severity. */
        public PropertyRow(String label, String value, boolean technical) { this(label, value, technical, Optional.empty()); }
    }

    /**
     * A read-only list of labelled values, such as a selected host's properties. A blank {@code title} shows no heading.
     *
     * @since 0.18.0
     */
    public record Properties(String id, ControlState state, String title, List<PropertyRow> rows) implements Node {
        public Properties {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(title);
            rows = List.copyOf(rows);
            if (rows.isEmpty()) throw new IllegalArgumentException("Properties need at least one row");
        }
    }

    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
'''
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [
        ("Breadcrumbs, Custom, Separator, Notice {", "Breadcrumbs, Custom, Separator, Notice, Properties {", 1),
        ("    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {\n", PROPERTIES, 1)])
for spec in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/ViewSpec.java", "jasper-ui/src/main/java/dev/jasper/ui/model/ViewSpec.java"]:
    edit(spec, [("Breadcrumbs _, Custom _, Separator _ -> { }", "Breadcrumbs _, Custom _, Separator _, Properties _ -> { }", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    (" case dev.jasper.sdk.ui.Nodes.Notice n -> ",
     " case dev.jasper.sdk.ui.Nodes.Properties n -> new dev.jasper.ui.model.Nodes.Properties(n.id(), toModule(n.state()), n.title(), n.rows().stream().map(KitValues::toModule).toList());\n"
     " case dev.jasper.sdk.ui.Nodes.Notice n -> ", 1),
    (" static dev.jasper.ui.model.Nodes.Numeric toModule(dev.jasper.sdk.ui.Nodes.Numeric n) {",
     " static dev.jasper.ui.model.Nodes.PropertyRow toModule(dev.jasper.sdk.ui.Nodes.PropertyRow n) { return new dev.jasper.ui.model.Nodes.PropertyRow(n.label(), n.value(), n.technical(), n.severity().map(s -> dev.jasper.ui.model.Nodes.Severity.valueOf(s.name()))); }\n"
     " static dev.jasper.ui.model.Nodes.Numeric toModule(dev.jasper.sdk.ui.Nodes.Numeric n) {", 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ControlRegistry.java", [
    ("Nodes.Breadcrumbs _, Nodes.Separator _ -> {}", "Nodes.Breadcrumbs _, Nodes.Separator _, Nodes.Properties _ -> {}", 1),
    ("            case Nodes.Notice notice -> new Notices.NoticeControl(view, notice);\n",
     "            case Nodes.Notice notice -> new Notices.NoticeControl(view, notice);\n"
     "            case Nodes.Properties properties -> new PropertyLists.PropertiesControl(view, properties);\n", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.PropertiesTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.HostedKitContractTest --tests dev.jasper.app.appearance.PaintedContrastTest --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src
git commit -F - <<'EOF'
SDK 0.18: Properties

Properties lists a selected thing's labelled values under an optional
heading: PropertyRow labels carry no colon, technical values take the data
face and keep their end in view when shortened, and a severity colours a
value held to 4.5:1 on the card. The default look draws a label column
beside the values on a card.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: `Form.footnote`

A form can carry one plain line at the leading edge of its footer, beside its actions (Unlock Vault's "Auto-locks
after 15 min idle"). The SDK and model `Form` gain `String footnote` (the ten-component constructor stays, with no
footnote); a footnote is one line and excludes a preview (Rulings 10). The default look mounts it, in CAPTION and
`text.tertiary`, in the preview's place only while it is not blank, so no existing form changes. Both host-row
projections now keep it, and the testkit's also keeps the submission it used to drop (Rulings 13).

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:567-593`, `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java:554-580` (Form)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java:30`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Forms.java:32,55,190,296-309`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java:139`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java:97`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java` (fixture `form-footnote`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/FootnotesTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`

**Interfaces:**
- Consumes: nothing beyond post-3a `main` and the earlier tasks' anchors.
- Produces: `Nodes.Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node>
  sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission, String
  footnote)` and `footnote()` in the SDK and model; the footer label `<form id>.footnote`. Task 9's contract view has a
  footnote.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/FootnotesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code Form.footnote}: one muted line at the footer's leading edge, beside the actions. */
@SuppressWarnings("try")
class FootnotesTest {
    static final ControlState READY = ControlState.READY;
    static final String NOTE = "Auto-locks after 15 min idle";
    static Form form(FormKind kind, String footnote) {
        return new Form("form", READY, kind, "Unlock Vault", Optional.empty(), List.of(new Secret("password", READY, "", "Master password", "")), Optional.empty(),
            List.of(new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""), new Nodes.Button("unlock", READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")),
            "cancel", FormSubmission.PRIMARY, footnote);
    }

    @Test void aFootnoteIsOneMutedLineAtTheFootersLeadingEdgeBesideTheActions() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.FULL, NOTE)), e -> {})) {
                KitFixtures.render(view, 540, 260, 1);
                var note = find(view, "form.footnote", JLabel.class);
                var cancel = find(view, "cancel", JComponent.class);
                assertThat(note).isNotNull();
                assertThat(note.isShowing() || note.isVisible()).isTrue();
                assertThat(note.getText()).isEqualTo(NOTE);
                assertThat(note.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.CAPTION));
                assertThat(note.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                var noteAt = SwingUtilities.convertPoint(note.getParent(), note.getLocation(), view);
                var cancelAt = SwingUtilities.convertPoint(cancel.getParent(), cancel.getLocation(), view);
                assertThat(noteAt.x + note.getWidth()).as("before the actions").isLessThanOrEqualTo(cancelAt.x);
                assertThat(noteAt.y).as("on the actions' line").isLessThan(cancelAt.y + cancel.getHeight());
                assertThat(noteAt.y + note.getHeight()).isGreaterThan(cancelAt.y);
            } finally { release(context); }
        });
    }

    @Test void aPromptFormsFullWidthActionsPutTheFootnoteOnTheLineAboveThem() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.PROMPT_COMPACT, NOTE)), e -> {})) {
                KitFixtures.render(view, 380, 240, 1);
                var note = find(view, "form.footnote", JLabel.class);
                var cancel = find(view, "cancel", JComponent.class);
                var noteAt = SwingUtilities.convertPoint(note.getParent(), note.getLocation(), view);
                var cancelAt = SwingUtilities.convertPoint(cancel.getParent(), cancel.getLocation(), view);
                assertThat(noteAt.y + note.getHeight()).isLessThanOrEqualTo(cancelAt.y);
            } finally { release(context); }
        });
    }

    @Test void aBlankFootnoteMountsNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.FULL, "")), e -> {})) {
                assertThat(find(view, "form.footnote", JLabel.class)).isNull();
                view.update(new ViewSpec(form(FormKind.FULL, NOTE)));
                assertThat(find(view, "form.footnote", JLabel.class)).isNotNull();
                view.update(new ViewSpec(form(FormKind.FULL, " ")));
                assertThat(find(view, "form.footnote", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 8 step 1: the tests that pin Form.footnote."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
FOOTNOTE = r'''    /** Plan 3b (spec 8.2): a footnote is one line beside the actions, and it shares the footer's leading edge with no preview. */
    @Test void footnotesAreOneLineAndExcludeAPreview() {
        var unlock = new Button("unlock", ControlState.READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "");
        assertThat(new Form("form", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock", Optional.empty(), List.of(text("body")), Optional.empty(), List.of(unlock))
            .footnote()).isEmpty();
        var noted = new Form("form", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock", Optional.empty(), List.of(text("body")), Optional.empty(), List.of(unlock),
            "cancel", FormSubmission.PRIMARY, "Auto-locks after 15 min idle");
        assertThat(noted.footnote()).isEqualTo("Auto-locks after 15 min idle");
        assertThatIllegalArgumentException().isThrownBy(() -> new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")),
            Optional.of(text("preview")), List.of(unlock), "cancel", FormSubmission.PRIMARY, "Saved")).withMessageContaining("preview");
        for (String broken : List.of("One" + (char) 10 + "Two", "One" + (char) 13 + "Two"))
            assertThatIllegalArgumentException().isThrownBy(() -> new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")),
                Optional.empty(), List.of(unlock), "cancel", FormSubmission.PRIMARY, broken)).withMessageContaining("one line");
        assertThat(new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")), Optional.of(text("preview")),
            List.of(unlock), "cancel", FormSubmission.PRIMARY, " ").footnote()).isBlank();
    }
'''
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ("    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", FOOTNOTE + "    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {\n", 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/model/ViewSpecTest.java", [
    ("    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", FOOTNOTE + "    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java", [
    (" @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){\n", r''' /** Plan 3b: a footnote crosses the projection unchanged. */
 @Test void footnotesProjectWithoutLoss(){
  var spec=new ViewSpec(new Form("unlock-form",READY,FormKind.PROMPT_COMPACT,"Unlock Vault",Optional.empty(),List.of(text("password")),Optional.empty(),List.of(button("unlock")),
   "cancel",FormSubmission.NONE,"Auto-locks after 15 min idle"));
  assertThat(KitValues.toModule(spec).toString()).isEqualTo(spec.toString());
  assertThat(((dev.jasper.ui.model.Nodes.Form)KitValues.toModule(spec).root()).footnote()).isEqualTo("Auto-locks after 15 min idle");
 }
 @Test void explicitPickerCuesSurviveHostProjectionAndRejectOtherKinds(){
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java", [
    ("    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */\n    private static dev.jasper.sdk.ui.Nodes.Node hostControl(",
     r'''    /** Spec 8.2: a settings panel's form keeps its footnote, and its submission, through the host rows' projection. */
    @Test void aPanelFormsFootnoteSurvivesTheHostRowProjection() throws Exception {
        var view = new AtomicReference<HostedKitView>();
        var name = new AtomicReference<Setting<String>>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            getClass().getClassLoader(), () -> plugin -> {
                name.set(plugin.settings().declare(SettingSpec.of("name", SettingKind.text(), "files").label("Name")));
                plugin.settings().page((SettingsPanel) panelHost -> {
                    var made = (HostedKitView) plugin.kit().create(footnoted(panelHost.row(name.get())), event -> { });
                    view.set(made);
                    return made;
                });
            })));
        var context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
            () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);
        var page = onEdtValue(() -> (PluginPanelPage) host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            view.get().update(footnoted(hostControlRow(view.get())));
            var form = (dev.jasper.sdk.ui.Nodes.Form) view.get().spec().root();
            assertThat(form.footnote()).isEqualTo("Saved to the plugin's settings file");
            assertThat(form.submission()).isEqualTo(dev.jasper.sdk.ui.Nodes.FormSubmission.NONE);
        });
        onEdt(page::dispose);
    }

    /** A FULL form around one host row, with a footnote and no Enter submission. */
    private static dev.jasper.sdk.ui.ViewSpec footnoted(dev.jasper.sdk.ui.Nodes.Node row) {
        return new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Form("form", dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.FormKind.FULL,
            "Panel", Optional.empty(), List.of(row), Optional.empty(), List.of(), "", dev.jasper.sdk.ui.Nodes.FormSubmission.NONE, "Saved to the plugin's settings file"));
    }

    /** The host row a {@link #footnoted} form holds. */
    private static dev.jasper.sdk.ui.Nodes.Node hostControlRow(HostedKitView view) {
        return ((dev.jasper.sdk.ui.Nodes.Form) view.spec().root()).sections().getFirst();
    }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node hostControl(''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java", [
    ("    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */\n    private static dev.jasper.sdk.ui.Nodes.Node control(",
     r'''    /** As the host: a panel's form keeps its footnote, and its submission, through the row projection. */
    @Test void aPanelFormsFootnoteSurvivesTheRowProjection() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        var name = new AtomicReference<Setting<String>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            name.set(context.settings().declare(SettingSpec.of("name", SettingKind.text(), "files").label("Name")));
            context.settings().page(rows -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Form("form",
                dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.FormKind.FULL, "Panel", Optional.empty(), List.of(rows.row(name.get())),
                Optional.empty(), List.of(), "", dev.jasper.sdk.ui.Nodes.FormSubmission.NONE, "Saved to the plugin's settings file")), e -> {}));
        });
        host.settingsPanel("test.fake").orElseThrow();
        var view = host.settingsPanelView("test.fake").orElseThrow();
        view.update(view.sourceSpec());
        var form = (dev.jasper.sdk.ui.Nodes.Form) view.spec().root();
        assertThat(form.footnote()).isEqualTo("Saved to the plugin's settings file");
        assertThat(form.submission()).isEqualTo(dev.jasper.sdk.ui.Nodes.FormSubmission.NONE);
    }); }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node control(''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ("        return List.copyOf(result);\n    }\n    private static Fixture panelState(",
     r'''        // Plan 3b (SDK 0.18): a prompt with a footnote, which takes the line above its full-width actions.
        result.add(new Fixture("form-footnote", "", "normal", 380, 200, new ViewSpec(new Form("unlock-footnote", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock Vault",
            Optional.empty(), List.of(new Secret("footnote-password", ControlState.READY, "Master password", "Master password", "")), Optional.empty(),
            List.of(new Nodes.Button("footnote-cancel", ControlState.READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("footnote-unlock", ControlState.READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "")),
            "cancel", FormSubmission.PRIMARY, "Auto-locks after 15 min idle"))));
        return List.copyOf(result);
    }
    private static Fixture panelState(''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.swing.FootnotesTest --continue`
Expected: FAIL. Both test compiles report "constructor Form in record Form cannot be applied to given types" (11
arguments) and "cannot find symbol … footnote()".

- [ ] **Step 3: Add the component, its footer line and the projections**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 8 step 3: Form.footnote in the SDK, the model, the projection, the footer and the row projections."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
for nodes in ["jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java", "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java"]:
    edit(nodes, [
        (r'''    /** Form content only; cancellation uses an explicit action key, blank means none. */
    public record Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission) implements Node {
''', r'''    /**
     * Form content only; cancellation uses an explicit action key, blank means none. A nonblank {@code footnote} (since
     * 0.18.0) is one plain line drawn muted at the leading edge of the footer, beside the actions (an auto-lock note); a
     * form with a footnote has no preview, which takes the same place.
     */
    public record Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission, String footnote) implements Node {
''', 1),
        (r'''            if (actions.stream().filter(button -> button.kind() == ButtonKind.PRIMARY).count() > 1)
                throw new IllegalArgumentException("Form supports at most one primary action");
        }
''', r'''            if (actions.stream().filter(button -> button.kind() == ButtonKind.PRIMARY).count() > 1)
                throw new IllegalArgumentException("Form supports at most one primary action");
            Objects.requireNonNull(footnote);
            if (footnote.chars().anyMatch(c -> c == 10 || c == 13)) throw new IllegalArgumentException("A footnote is one line");
            if (!footnote.isBlank() && preview.isPresent())
                throw new IllegalArgumentException("A footnote and a preview share the footer's leading edge; a form has one or the other");
        }
        /** A form without a footnote. */
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission) {
            this(id, state, kind, title, hero, sections, preview, actions, cancelAction, submission, "");
        }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitValues.java", [
    ("dev.jasper.ui.model.Nodes.FormSubmission.valueOf(n.submission().name()));",
     "dev.jasper.ui.model.Nodes.FormSubmission.valueOf(n.submission().name()), n.footnote());", 1)])
FORMS = "jasper-ui/src/main/java/dev/jasper/ui/swing/Forms.java"
edit(FORMS, [
    ("        final JLabel title = Labels.literal();\n",
     "        final JLabel title = Labels.literal();\n"
     "        /** The form's footnote, mounted in the preview's place only while it is not blank (spec 8.2). */\n"
     "        final JLabel footnote = Labels.literal();\n", 1),
    ("            KitOwnership.mark(title, node.id() + \".title\");\n",
     "            KitOwnership.mark(title, node.id() + \".title\");\n            KitOwnership.mark(footnote, node.id() + \".footnote\");\n", 1),
    ("            title.setText(form.title());\n",
     "            title.setText(form.title());\n"
     "            footnote.setText(form.footnote());\n"
     "            Composition.style(footnote, context(), Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);\n", 1),
    (r'''            Stacks.mount(
                    preview,
                    controls.stream()
                            .filter(c -> childParent(c.node) == preview)
                            .map(c -> c.component)
                            .toList());
''', r'''            var leading = new java.util.ArrayList<JComponent>();
            if (!f.footnote().isBlank()) leading.add(footnote);
            controls.stream().filter(c -> childParent(c.node) == preview).forEach(c -> leading.add(c.component));
            Stacks.mount(preview, leading);
''', 1),
    ("            preview.setVisible(f.preview().isPresent());\n",
     "            preview.setVisible(f.preview().isPresent() || !f.footnote().isBlank());\n", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java", [
    ("v.preview().map(this::replace),v.actions(),v.cancelAction(),v.submission());",
     "v.preview().map(this::replace),v.actions(),v.cancelAction(),v.submission(),v.footnote());", 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java", [
    ("n.preview().map(child), n.actions(), n.cancelAction());",
     "n.preview().map(child), n.actions(), n.cancelAction(), n.submission(), n.footnote());", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-ui:test --tests dev.jasper.ui.model.ViewSpecTest --tests dev.jasper.ui.swing.FootnotesTest --tests dev.jasper.ui.swing.FormsTest --tests dev.jasper.ui.swing.GroupedFormGeometryTest --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakePluginSettingsTest --tests 'dev.jasper.sdk.testing.FakeKit*' :jasper-app:test --tests dev.jasper.app.plugins.KitValuesTest --tests dev.jasper.app.plugins.PluginPanelPageTest --tests dev.jasper.app.plugins.HostedKitContractTest --continue`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src jasper-ui/src jasper-app/src jasper-sdk-testkit/src
git commit -F - <<'EOF'
SDK 0.18: Form.footnote

A form can carry one plain line at the footer's leading edge, beside its
actions (an auto-lock note): one line, and never with a preview, whose
place it takes. The default look draws it muted, mounted only while it is
not blank. Both host-row projections keep it, and the testkit's keeps the
form's submission too, which it used to drop.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The shared contracts and the gallery's new fields

The behavioural contracts both runtimes run (spec 16.1.9): `KitContractTest` mounts one view holding every addition
of this plan, updates it, reads it back as given and delivers the events a person can cause (the Notice's action, a
TABS selection); `PluginContractTest` checks that a plugin's own kit admits that view and refuses each misplaced or
miscounted one (Rulings 14). `GalleryCoverageTest` gains the new fields' coverage: a read-only field, a technical
caption, a footnote, technical and graded property values and a notice with an action each have a real fixture.

**Files:**
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java` (one case and the shared view)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java:37,134` (imports and one case)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java:21,139`

**Interfaces:**
- Consumes: every earlier task's API: `Nodes.Notice`, `Nodes.Properties`, `Nodes.PropertyRow`, `Nodes.Separator`,
  `ChoiceKind.TABS`, `IconSize.SWATCH`, the 13-argument `Field`, the 6-argument `PickerFace`, the 11-argument `Form`.
- Produces: `public static ViewSpec KitContractTest.zeroEighteen(String warning)`, which `PluginContractTest` reuses.

- [ ] **Step 1: Write the cases**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 9 step 1: the shared contract cases and the gallery's coverage of the new fields."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java", [
    ("    /** Spec 8.2: a read-only field is focusable, selectable and copyable but takes no typing; an editable one reports it. */\n",
     r'''    /** Spec 16.1.9: SDK 0.18's controls mount, update and read back as given, and the events a person can cause reach the owner. */
    @Test void zeroEighteenControlsMountUpdateAndReportTheirEvents() throws Exception { edt(() -> {
        var kit = kit("owner"); var events = new ArrayList<UiEvent>();
        var spec = zeroEighteen("Plain FTP sends the password unencrypted");
        var view = kit.create(spec, events::add);
        try {
            view.update(spec);
            assertThat(view.spec()).isEqualTo(spec);
            assertThat(events).isEmpty();
            var next = zeroEighteen("Plain FTP sends passwords unencrypted");
            view.update(next);
            assertThat(view.spec()).isEqualTo(next);
            emit(view, new UiEvent.Action("notice-details"));
            emit(view, new UiEvent.Selected("tabs", Set.of("details")));
            assertThat(events).containsExactly(new UiEvent.Action("notice-details"), new UiEvent.Selected("tabs", Set.of("details")));
        } finally { view.close(); stop(kit); }
    }); }

    /**
     * One view of every SDK 0.18 control of plan 3b: a TABS choice, a toolbar row with a separator, a swatch, a technical
     * caption, a read-only field and properties in the body, a notice as a section, and a footnote.
     */
    public static ViewSpec zeroEighteen(String warning) {
        var ready = ControlState.READY;
        var tabs = new Nodes.Choice("tabs", ready, Nodes.ChoiceKind.TABS, List.of(new Option("overview", "Overview", Optional.empty()),
            new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "");
        var toolbar = new Nodes.Stack("toolbar", ready, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button("back", ready, "", "back", Nodes.ButtonKind.GHOST, "back", "Back"), new Nodes.Separator("toolbar-separator", ready),
            new Nodes.Button("hidden", ready, "Hidden files", "", Nodes.ButtonKind.GHOST, "hidden", "")));
        var swatch = new Nodes.Icon("swatch", ready, "file", Nodes.Hue.AMBER, Nodes.IconSize.SWATCH, "FTP");
        var via = new Nodes.Choice("via", ready, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("mac", "Mac", Optional.empty()),
            new Option("box", "build-box", Optional.empty())), "mac", "Via host", "", "", Optional.empty(),
            Optional.of(new Nodes.PickerFace("Mac", "dustin@192.168.1.119:22", "server", Nodes.Hue.BLUE, Nodes.PickerDensity.DEFAULT, true)));
        var folder = new Nodes.Field("folder", ready, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(),
            Nodes.FieldPresentation.STANDARD, "", "", "", true);
        var properties = new Nodes.Properties("properties", ready, "Properties — minecraft-local", List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(Nodes.Severity.WARNING))));
        var notice = new Nodes.Notice("notice", ready, Nodes.Severity.WARNING, warning,
            List.of(new Nodes.Button("notice-details", ready, "Details", "", Nodes.ButtonKind.LINK, "notice-details", "")));
        return new ViewSpec(new Nodes.Form("form", ready, Nodes.FormKind.FULL, "New File Server", Optional.empty(),
            List.of(new Nodes.Stack("body", ready, Nodes.LayoutRole.VERTICAL, List.of(tabs, toolbar, swatch, via, folder, properties)), notice),
            Optional.empty(), List.of(new Nodes.Button("cancel", ready, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", "")),
            "cancel", Nodes.FormSubmission.PRIMARY, "Auto-locks after 15 min idle"));
    }

    /** Spec 8.2: a read-only field is focusable, selectable and copyable but takes no typing; an editable one reports it. */
''', 1)])
CONTRACT = "jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java"
edit(CONTRACT, [
    ("import dev.jasper.sdk.ui.WindowSpec;\n",
     "import dev.jasper.sdk.ui.WindowSpec;\nimport dev.jasper.sdk.ui.ControlState;\nimport dev.jasper.sdk.ui.Nodes;\nimport dev.jasper.sdk.ui.Option;\nimport dev.jasper.sdk.ui.ViewSpec;\n", 1),
    ("    @Test void semanticPresentationIconsValidateOwnershipAtEveryBoundary() {\n", r'''    /** Spec 16.1.9: a plugin's kit admits SDK 0.18's controls and refuses a misplaced Notice or Separator, a NONE swatch and a miscounted choice. */
    @Test void aPluginsKitAdmitsZeroEighteenControlsAndRefusesMisplacedOnes() {
        var context = started("test.controls", Set.of());
        h.ui(() -> {
            var spec = dev.jasper.sdk.testing.KitContractTest.zeroEighteen("Plain FTP sends the password unencrypted");
            var view = context.kit().create(spec, event -> { });
            assertThat(view.spec()).isEqualTo(spec);
            view.close();
            var ready = ControlState.READY;
            var text = new Nodes.Text("a", ready, "A", Nodes.TextRole.BODY);
            var separator = new Nodes.Separator("separator", ready);
            var one = List.of(new Option("one", "One", Optional.empty()));
            var five = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new Option("o" + i, "Option " + i, Optional.empty())).toList();
            List<java.util.function.Supplier<ViewSpec>> refused = List.of(
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(separator, text))),
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.VERTICAL, List.of(text, separator, new Nodes.Text("b", ready, "B", Nodes.TextRole.BODY)))),
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(new Nodes.Notice("notice", ready, Nodes.Severity.INFO, "Saved")))),
                () -> new ViewSpec(new Nodes.Icon("swatch", ready, "file", Nodes.Hue.NONE, Nodes.IconSize.SWATCH, "None")),
                () -> new ViewSpec(new Nodes.Choice("pick", ready, Nodes.ChoiceKind.EXCLUSIVE, one, "one", "Pick", "")),
                () -> new ViewSpec(new Nodes.Choice("pick", ready, Nodes.ChoiceKind.EXCLUSIVE, five, "o0", "Pick", "")),
                () -> new ViewSpec(new Nodes.Choice("tabs", ready, Nodes.ChoiceKind.TABS, one, "one", "Tabs", "")));
            for (var bad : refused)
                assertThatIllegalArgumentException().isThrownBy(() -> context.kit().create(bad.get(), event -> { }));
        });
    }

    @Test void semanticPresentationIconsValidateOwnershipAtEveryBoundary() {
''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java", [
    ("    @Test void allNineOriginalReferencesAndFourPanelsAreRepresented() {\n", r'''    /** Plan 3b: SDK 0.18's new fields have real fixtures too, not only its new nodes and constants. */
    @Test void zeroEighteenFieldsHaveARealFixture() {
        var records = new ArrayList<Record>();
        KitGallery.fixtures().forEach(f -> records(f.spec().root(), records));
        assertThat(records).as("a read-only field").anyMatch(r -> r instanceof Nodes.Field field && field.readOnly());
        assertThat(records).as("a technical caption").anyMatch(r -> r instanceof Nodes.PickerFace face && face.technicalCaption());
        assertThat(records).as("a footnote").anyMatch(r -> r instanceof Nodes.Form form && !form.footnote().isBlank());
        assertThat(records).as("a technical property").anyMatch(r -> r instanceof Nodes.PropertyRow row && row.technical());
        assertThat(records).as("a property with a severity").anyMatch(r -> r instanceof Nodes.PropertyRow row && row.severity().isPresent());
        assertThat(records).as("a notice with an action").anyMatch(r -> r instanceof Nodes.Notice notice && !notice.actions().isEmpty());
    }

    @Test void allNineOriginalReferencesAndFourPanelsAreRepresented() {
''', 1),
    ("    static void collect(Object value, Set<String> values) {\n", r'''    /** Every record reachable from {@code value}: nodes and their parts (faces, rows, items). */
    static void records(Object value, List<Record> out) {
        if (value instanceof Optional<?> o) { o.ifPresent(v -> records(v, out)); return; }
        if (value instanceof Iterable<?> items) { items.forEach(v -> records(v, out)); return; }
        if (value instanceof Record record && !(value instanceof dev.jasper.ui.model.ControlState)) {
            out.add(record);
            try { for (RecordComponent c : record.getClass().getRecordComponents()) records(c.getAccessor().invoke(record), out); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }

    static void collect(Object value, Set<String> values) {
''', 1)])
PY
```

- [ ] **Step 2: Run the cases**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.gallery.GalleryCoverageTest :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeKitContractTest --tests dev.jasper.sdk.testing.FakeContractTest :jasper-app:test --tests dev.jasper.app.plugins.HostedKitContractTest --tests dev.jasper.app.plugins.AppContractTest --continue`
Expected: BUILD SUCCESSFUL: the host and the fake agree on every new case (the earlier tasks built what these cases
check, so they pass on their first run; a failure here is a disagreement between the runtimes, which is a host bug to
fix before committing).

- [ ] **Step 3: Commit**

```bash
git add jasper-sdk-testkit/src jasper-ui/src
git commit -F - <<'EOF'
SDK 0.18: the shared contracts for plan 3b's controls

Both runtimes mount one view of every new control, read it back as given,
deliver a notice's action and a tab's selection, and refuse a misplaced
notice or separator, a NONE swatch and a miscounted choice through a
plugin's own kit. The gallery's coverage test now asks for the new fields
too.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: The guides, the gallery proof, STATUS and the whole build

The plugin UI guide gains the new building blocks and the fake's `type`, the authoring guide the technical hint and
the SDK README's 0.18.0 sentence the additions (spec 17: smallest edits). The headless gallery proves the pixel rule against the branch's base, `./gradlew check
--continue` runs everything, and `docs/STATUS.md` gains this plan's entry above 3a's, with the counts from the result
XML; the plan's status banner records the execution.

**Files:**
- Modify: `docs/plugin-ui.md:173-185,207-212`, `docs/plugin-authoring.md:611`, `jasper-sdk/README.md:45` (3a's 0.18.0 sentence)
- Modify: `docs/STATUS.md:2` (a new entry at the top), `docs/superpowers/plans/2026-10-15-enterprise-3b-sdk-0-18-controls.md:1` (the status banner)

**Interfaces:**
- Consumes: Tasks 1 to 9.
- Produces: documentation only.

- [ ] **Step 1: Update the guides**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3b Task 10 step 1: the guides name plan 3b's vocabulary (spec 17)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/plugin-ui.md", [
    ('''  [Declared settings](plugin-authoring.md#declared-settings)).
''', '''  [Declared settings](plugin-authoring.md#declared-settings)).
- **Notices (SDK 0.18).** `Notice(severity, text, actions)`: one sentence, shown after the severity's glyph and word,
  with at most one SECONDARY or LINK action; a form section or a child of a VERTICAL stack.
- **Properties (SDK 0.18).** `Properties(title, rows)` of `PropertyRow(label, value, technical, severity)`: labelled
  values under an optional heading. Labels carry no colon; `technical` puts a value in the data face and a severity
  colours it.
- **Toolbar groups (SDK 0.18).** A `Separator` stands between two items of an INLINE or ACTIONS stack, never first,
  last or beside another; it is not shown while every item on one side of it is hidden.
- **Tabs (SDK 0.18).** `ChoiceKind.TABS` switches among two or more views (Overview and Details) and reports
  `Selected`; compose the chosen content below it. A view whose EXCLUSIVE choice has fewer than two or more than four
  options is refused.
- **Facts and data values (SDK 0.18).** A `readOnly` field is focusable, selectable and copyable but never edited (a
  stepper or slider is never read-only). `PickerFace.technicalCaption` marks a caption as a data value,
  `IconSize.SWATCH` is a solid square of an icon's hue (never NONE), and `Form.footnote` is one muted line beside the
  actions, in place of a preview.
''', 1),
    ("whose `emit(event)` delivers user input.", "whose `emit(event)` delivers user input; `type(id, text)` types into a field as a person would, and a\nread-only, hidden or disabled field takes nothing.", 1)])
edit("docs/plugin-authoring.md", [
    ("and so do `EditorHint.font` (the font family picker) and `EditorHint.file` (a path with Browse…).",
     "and so do `EditorHint.font` (the font family picker), `EditorHint.file` (a path with Browse…) and `EditorHint.technical()` (SDK 0.18: a text field in the data face).", 1)])
edit("jasper-sdk/README.md", [
    ("and it adds the `presentation.provide` capability;",
     "and it adds the `presentation.provide` capability, the `Notice`, `Properties` and `Separator` nodes, `ChoiceKind.TABS`, "
     "`Field.readOnly`, `PickerFace.technicalCaption`, `EditorHint.technical()`, `IconSize.SWATCH` and `Form.footnote`;", 1)])
PY
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: BUILD SUCCESSFUL (the guides' links resolve and their compiled examples are unchanged).

- [ ] **Step 2: Prove the pixel rule with the headless gallery**

Run from the worktree root (it renders the gallery at the branch's base and here, for both themes, and compares every
file; it never opens a window):

```bash
B=$(mktemp -d) && BASE=$(git merge-base HEAD main) && git worktree add --detach "$B/base" "$BASE"
for theme in dark light; do
  (cd "$B/base" && ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/base-$theme")
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/new-$theme"
done
python3 -I - "$B" <<'PY'
"""Plan 3b's pixel rule: every file the gallery wrote before is byte-identical, but the manifest and the last old sheet."""
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
NEW = ["read-only-fields", "technical-captions", "tabs", "swatches", "separators", "notice-severities", "properties", "form-footnote"]
for theme in ["dark", "light"]:
    base, new = root / f"base-{theme}", root / f"new-{theme}"
    before = json.loads((base / "manifest.json").read_text(encoding="utf-8"))["fixtureCount"]
    after = json.loads((new / "manifest.json").read_text(encoding="utf-8"))["fixtureCount"]
    assert after == before + len(NEW), (theme, before, after)
    last = (before + 7) // 8
    changed = {"manifest.json"} | ({f"sheet-{last}.png"} if before % 8 else set())
    added = {f"{name}.{ext}" for name in NEW for ext in ("png", "json")} | {f"sheet-{n}.png" for n in range(last + 1, (after + 7) // 8 + 1)}
    names_before, names_after = {p.name for p in base.iterdir()}, {p.name for p in new.iterdir()}
    assert names_before <= names_after, (theme, sorted(names_before - names_after))
    assert names_after - names_before == added, (theme, sorted((names_after - names_before) ^ added))
    for name in sorted(names_before - changed):
        assert (base / name).read_bytes() == (new / name).read_bytes(), (theme, name)
    print(theme, len(names_before - changed), "files byte-identical;", sorted(changed), "changed;", len(added), "added")
PY
git worktree remove --force "$B/base"
```

Expected: `dark 140 files byte-identical; ['manifest.json', 'sheet-9.png'] changed; 17 added`, and the same for
`light`. Open the new PNGs under `$B/new-dark` and `$B/new-light` (`read-only-fields.png` … `form-footnote.png`) and
check by eye that each addition draws as Rulings 4 to 11 describe; note anything unexpected in the status banner.

- [ ] **Step 3: Run the whole build and read the counts**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL, including `verifyUiArchitecture`, `verifySdkArchitecture`, `verifyApplicationArchitecture`,
`verifyPluginArchitecture`, the javadoc doclint of the SDK, jasper-ui and jasper-app, `ThemeMigrationCoverageTest`,
`PromptVocabularyTest`, `KitMatrixTest` and `PaintedContrastTest`.

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""The test counts of the last build, from the result XML."""
import pathlib, xml.etree.ElementTree as ET
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in sorted(pathlib.Path(".").glob("**/build/test-results/test/*.xml")):
    suite = ET.parse(path).getroot()
    for key in totals: totals[key] += int(suite.get(key, "0"))
print(f"{totals['tests']:,} tests, {totals['failures']} failures, {totals['errors']} errors, {totals['skipped']} skipped")
PY
```

Expected: one line such as `10,6xx tests, 0 failures, 0 errors, 25 skipped`; Step 4 writes it into STATUS.

Then check source hygiene (AGENTS.md):

```bash
python3 -I - $(git diff --name-only "$(git merge-base HEAD main)" -- '*.java') <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 4: Write the STATUS entry and the status banner**

Run from the worktree root, with `COUNTS` set to the line Step 3 printed and `DEVIATIONS` listing anything an earlier
step told you to record (leave it empty when there is none):

```bash
COUNTS='<the line Step 3 printed>' python3 -I - <<'PY'
"""Plan 3b Task 10 step 4: the STATUS entry above 3a's and the plan's status banner."""
import datetime, os, pathlib, subprocess
DEVIATIONS = []  # one sentence each, for example "Task 3: plugins/remote/.../X.java built a one-option choice; it now has two."
def git(*args): return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()
counts = os.environ["COUNTS"].strip()
if not counts or counts.startswith("<"): raise SystemExit("set COUNTS to the line the count script printed")
base = git("merge-base", "HEAD", "main")
# The plan's own commits ("Plan: …", "Plan 3b: …") are not the execution's.
commits = [line.split(" ", 1)[0] for line in git("log", "--reverse", "--format=%h %s", f"{base}..HEAD").splitlines()
           if not line.split(" ", 1)[1].startswith("Plan")]
first, last = commits[0], commits[-1]
date = datetime.date.today().isoformat()
deviations = " ".join(DEVIATIONS) if DEVIATIONS else "none beyond the plan's Rulings"
entry = f"""**Enterprise presentation, plan 3b: SDK 0.18 controls — {date}** (`claude/enterprise-sdk-0-18-controls`, worktree
`.claude/worktrees/enterprise-3b`, `{first}..{last}`, then this documentation commit;
[plan](superpowers/plans/2026-10-15-enterprise-3b-sdk-0-18-controls.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). SDK 0.18 gains the first group of spec
8.2's additions, each in the SDK and the jasper-ui model, projected losslessly by `KitValues`, validated by `ViewSpec`,
served by the testkit fake, pinned by `KitContractTest` and `PluginContractTest` on both runtimes and drawn by the
default look: `Nodes.Notice` (the severity's glyph and word before one sentence on the severity's derived ground, the
text held to 4.5:1 and the border to 1.3:1, at most one SECONDARY or LINK action; a form section or a VERTICAL stack
child), `Nodes.Properties` with `PropertyRow` (a heading over a card of label and value rows; technical values in the
data face, kept from their end when shortened; a severity colours a value held to 4.5:1 on the card), `Nodes.Separator`
(a hairline between two items of an INLINE or ACTIONS stack, hidden while every item on one side is), `ChoiceKind.TABS`
(the segment track without a visible label), `Field.readOnly` (focusable, selectable and copyable, never edited; it
replaces the host-only `KitView.setFieldReadOnly`, and the Keymap search sets it while recording),
`PickerFace.technicalCaption` (`MONO_CAPTION`), `EditorHint.technical()` (generated pages and host rows draw a text
setting's field TECHNICAL), `IconSize.SWATCH` (a solid square of the hue at caption size) and `Form.footnote` (one muted
line in the preview's place), and the rule that an EXCLUSIVE choice has 2 to 4 options and a TABS choice two or more.
That rule refuses File Server's two one-option choices, so a single offered protocol and TFTP's one login are
read-only fields now (spec 13's TFTP item; 3e keeps the rest of section 13 and must not redo it). Zero pixel change to
existing fixtures: the headless gallery's 140 earlier files per theme (Slate Cobalt Dark and Light) are byte-identical,
`manifest.json` and `sheet-9.png` change, and eight new fixtures (`read-only-fields`, `technical-captions`, `tabs`,
`swatches`, `separators`, `notice-severities`, `properties`, `form-footnote`) add 17 files; `StateFixtures`' controls and
icon sheets skip TABS and SWATCH. Rulings (plan, "Rulings"): Notice and Separator placement, root views included; list
rows take neither, nor Properties; the separator's hiding; the Notice words "Information", "Warning" and "Error"; the
Properties card; a read-only stepper or slider refused, a read-only number deaf to its arrows; TABS needs two options;
a swatch takes no severity and is painted as authored; a footnote is one line and excludes a preview, above a PROMPT
form's full-width actions; technical captions in `MONO_CAPTION`; the technical hint applies to text settings; the
testkit's row projection keeps `Form.submission`; the contract cases split between `KitContractTest` (with a new `type`
hook) and `PluginContractTest`. Deviations from the plan's text: {deviations}. Hand-offs: plan 4's classic faces draw
these nodes the classic way (the classic Notice in its tinted box, Properties as a table, TABS as folder tabs, read-only
fields on `surface.tableHeader`, the swatch's indicator guard, the footnote in the classic footer); 3c and 3d add their
nodes to `KitValuesTest`'s list, which `classes` counts with the nodes inside stacks and form sections; 3d's
`DialogSpec.swatch` can reuse `Labels.Swatch`. `./gradlew check --continue`: **{counts}**. GUI checks for the user: in
New File Server, choose TFTP and see "No login (TFTP has none)" as a read-only field in the Access card; nothing else
visible changed in the shipped surfaces. Not merged, not pushed.

"""
status = pathlib.Path("docs/STATUS.md"); text = status.read_text(encoding="utf-8")
header = "# Jasper — Status and Handoff\n\n"
if not text.startswith(header): raise SystemExit("docs/STATUS.md: unexpected first lines")
status.write_text(header + entry + text[len(header):], encoding="utf-8")
plan = pathlib.Path("docs/superpowers/plans/2026-10-15-enterprise-3b-sdk-0-18-controls.md"); body = plan.read_text(encoding="utf-8")
title = "# Enterprise presentation 3b: SDK 0.18 controls Implementation Plan\n"
if not body.startswith(title): raise SystemExit("plan: unexpected title")
banner = f"\n> **Status:** executed {date} (`{first}..{last}`); deviations from the text: {deviations}; `./gradlew check --continue`: {counts}.\n"
plan.write_text(title + banner + body[len(title):], encoding="utf-8")
print("STATUS entry and banner written for", f"{first}..{last}")
PY
```

Expected: `STATUS entry and banner written for <first>..<last>`. Read the new entry once: it sits above plan 3a's
entry and names the right range.

- [ ] **Step 5: Commit**

```bash
git add docs/plugin-ui.md docs/plugin-authoring.md jasper-sdk/README.md docs/STATUS.md docs/superpowers/plans/2026-10-15-enterprise-3b-sdk-0-18-controls.md
git commit -F - <<'EOF'
SDK 0.18 controls: documentation and status

The plugin UI guide names Notice, Properties, Separator, TABS, read-only
fields, technical captions, swatches, footnotes and the fake's type; the
authoring guide the technical hint; the SDK README's 0.18.0 sentence the
additions. STATUS records plan 3b, its rulings,
the gallery proof and the whole build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Then stop: do not push or merge. The user reviews the branch and the GUI check above.

## Execution notes (2026-10-16)

The anchors, quoted text, paths, signatures and line numbers above were re-verified against the worktree at
`41a3d9c38` (on `main`'s `deafed424`, 3a merged), and the text was updated where the code had moved. How: every
Python edit script of Tasks 1 to 10 (and each "Create" file) was applied in task order to an exported copy of the
tree, with every anchor found its stated number of times. The result then compiled with JBR 25's `javac -Xlint:all`
(no Gradle) in every source set it touches (SDK, jasper-ui with its test fixtures, app, testkit with its contracts,
File Server); its only new lint warnings, both fixed below, were a dangling SDK doc comment and a redundant cast. The
SDK, jasper-ui and app javadoc ran with the build's doclint flags (the testkit's with the defaults) and reported no
errors. The JUnit launcher,
run outside Gradle, passed every new and named test. Its failures (jasper-ui's shipped-jar check, the app's staged
plugin tests, File Server's network tests) need Gradle-built jars or open sockets and fail the same way on the
unchanged base. The headless gallery's bytes and the Gradle architecture tasks were not run; Task 10 still proves
them.

Corrections made in this refresh: the Workspace step (the branch already stands on `deafed424`, so there is no
rebase); SDK `Nodes.java` line numbers, which sit 2 to 13 lines below the model's after 3a's Javadoc (both are now
given); `PickerMenus.line`, `KitGallery`, `FakeKitTest`, `KitContractTest`, `Forms` and `docs/plugin-ui.md` line
numbers; Verified fact 1's list of SDK-only Javadoc (`IconSize` has one too); Task 4's SDK `IconSize` edit; Task 4's
enum-name pins; Task 1's redundant cast; Task 10's commit range.

### Pre-flight scan

Pairs of tasks that share a file or an interface. "Disjoint" means each task's anchors are different text, and the
in-order simulation found each later anchor its stated number of times after the earlier task had run. Every task
from 1 to 8 edits both `Nodes.java` files, appends a fixture before `KitGallery`'s `return List.copyOf(result);`
(so fixtures land in task order, as the pixel rule needs), and inserts tests before one fixed anchor in the SDK
`KitValuesTest`, the model `ViewSpecTest` and the app `KitValuesTest`. Those shared files are not repeated in each row
below unless something more is shared.

| Pair | Shared beyond the common files | Finding | Ruling |
|---|---|---|---|
| 1 × 2 | `KitValues` (Field line, PickerFace line) | disjoint | A |
| 1 × 3 | interface: the 13-argument `Field` that `ServerEditor.fact` builds | 3 consumes 1's signature exactly | A |
| 1 × 4 | none | disjoint | A |
| 1 × 5 | `KitValues` | disjoint | A |
| 1 × 6 | `FakeKitView` (1: `emit`/`type` before `registerContent`; 6: the `children` Empty case) | disjoint | A |
| 1 × 7 | `KitValues` | disjoint | A |
| 1 × 8 | `KitValues` (Field line, Form line) | disjoint | A |
| 1 × 9 | `KitContractTest`: 9 inserts its case before 1's read-only case Javadoc; interface: `type` hook, read-only `Field` | 1's Javadoc line occurs once, so 9's anchor holds | A |
| 1 × 10 | interface: the guide documents `FakeKitView.type` and `readOnly` | names match 1's code | A |
| 2 × 3 | none | disjoint | A |
| 2 × 4 | none | disjoint | A |
| 2 × 5 | `KitValues` | disjoint | A |
| 2 × 6 | `KitValues` | disjoint | A |
| 2 × 7 | `KitValues` | disjoint | A |
| 2 × 8 | `KitValues`; both `KitSettingRows` (2: the field presentation, 8: the Form rebuild); `PluginPanelPageTest`, `FakePluginSettingsTest` (both insert before the `hostControl`/`control` helper Javadoc, which 2 re-emits once) | disjoint; 8's anchor still occurs once after 2 | A |
| 2 × 9 | interface: the 6-argument `PickerFace` in `zeroEighteen` | matches | A |
| 2 × 10 | interface: `EditorHint.technical()`, `technicalCaption` in the guides | matches | A |
| 3 × 4 | `StateFixtures` (3: the controls line, 4: the icons line) | disjoint | A |
| 3 × 5 | `ViewSpec` (3: the `Choice` case, 5: imports, `place`, the Stack case, the leaf case, the child loop) | disjoint | A |
| 3 × 6 | `ViewSpec` (6: `place` and the `Empty` case) | disjoint | A |
| 3 × 7 | `ViewSpec` (7: the leaf case) | disjoint | A |
| 3 × 8 | none | disjoint | A |
| 3 × 9 | interface: TABS, the 2-to-4 rule (9's refused cases) | messages match ("2 to 4 options", "two or more options") | A |
| 3 × 10 | interface: the guide's TABS and EXCLUSIVE sentences | matches | A |
| 4 × 5 | none | disjoint | A |
| 4 × 6 | none (6's glyph uses `Labels.tint`, untouched by 4) | disjoint | A |
| 4 × 7 | none (7's `valuePair` uses `Labels.tint`) | disjoint | A |
| 4 × 8 | none | disjoint | A |
| 4 × 9 | interface: `IconSize.SWATCH`, the NONE refusal | matches | A |
| 4 × 10 | interface: the guide's SWATCH sentence | matches | A |
| 5 × 6 | permits chain (`Custom, Separator {` then `…, Notice {`); `ViewSpec.place` (6 rewrites 5's Javadoc and adds its rule); `KitValues` (6 anchors on 5's Separator case); `ControlRegistry` (both add a `create` case after Breadcrumbs, so Notice lands before Separator); app `KitValuesTest`'s list, which 5's `classes` counts | chained anchors each occur once; the order of switch cases does not matter | A, D |
| 5 × 7 | permits chain; the `ViewSpec` leaf case (`Custom _, Separator _` then `…, Properties _`); `ControlRegistry` validate leaf line; `KitValues`; the counted list | chained anchors each occur once | A |
| 5 × 8 | `KitValues` | disjoint | A |
| 5 × 9 | interface: `Separator`, placement messages | matches | A |
| 5 × 10 | interface: the guide's toolbar-groups sentence | matches | A |
| 6 × 7 | permits chain; `KitValues` (7 inserts before 6's Notice case); `ControlRegistry` (7 anchors on 6's create case); `PaintedContrastTest` (7 anchors on 6's last `Declared` line and 6's class Javadoc line; both insert helpers before `paint(`) | chained anchors each occur once | A |
| 6 × 8 | `KitValues` | disjoint | A |
| 6 × 9 | interface: `Notice` with one LINK action; the notice-with-action fixture (DANGER, "Choose…") that 9's coverage asks for | matches | A |
| 6 × 10 | interface: the guide's Notice sentence | matches | A |
| 7 × 8 | `KitValues` (7 adds a `PropertyRow` mapping before the `Numeric` one; 8 edits the Form line) | disjoint | A |
| 7 × 9 | interface: `Properties`, `PropertyRow` (technical and graded rows in the fixture) | matches | A |
| 7 × 10 | interface: the guide's Properties sentence | matches | A |
| 8 × 9 | interface: the 11-argument `Form` with `FormSubmission.PRIMARY` and a footnote; the `form-footnote` fixture | matches | A |
| 8 × 10 | interface: the guide's footnote sentence | matches | A |
| 9 × 10 | none (10 runs `check`, which runs 9's cases) | none | A |

Each task against itself:

| Task | Finding | Ruling |
|---|---|---|
| 1 | Files cited `KitContractTest.java:18,36`, `FakeKitTest.java:21,44` and `KitGallery.java:63` (now `:18,39`, `:19,43`, `:67`); the fake test's `(FakeKitView)` cast is redundant (`FakeKit.create` returns `FakeKitView`), a new `-Xlint:cast` warning | C |
| 2 | SDK `PickerFace` lines and `PickerMenus.line` lines had moved; text otherwise agrees (Interfaces, steps, commit) | B |
| 3 | SDK `ChoiceKind` line had moved. Other one- or five-option EXCLUSIVE choices in main code: none (Plugin Details 2, toolbar setting 3, Remote and the fixtures 2 to 4), so Step 4's fallback should not fire | B, E |
| 4 | Its SDK edit put a new Javadoc under 3a's one-line `IconSize` Javadoc, leaving that one dangling (`-Xlint:dangling-doc-comments`, and the PROMPT sentence lost from the SDK's javadoc); the enum-name pins carried from 3a's review were missing | F, G |
| 5 | SDK `Custom` line had moved (`:656`) | B |
| 6 | SDK `Custom` line had moved | B |
| 7 | SDK `Custom` line had moved | B |
| 8 | SDK `Form` and `Forms.java` lines had moved | B |
| 9 | agrees with itself; its cases pass on the host and the fake in the simulation | A |
| 10 | Step 4 took `commits[1]` as the first task commit, but the branch now holds three plan commits before Task 1; the guide line numbers had moved | H, B |

### Rulings

- **A. Shared files stay shared as written.** What: the edits that several tasks make to `Nodes.java`, `ViewSpec`,
  `KitValues`, `ControlRegistry`, `KitGallery`, `PaintedContrastTest` and the tests run in task order, unchanged. Why:
  each anchor occurs exactly once at its turn in the in-order simulation, and the end state compiles and passes.
  Cost if wrong: an edit script stops with "expected 1 of …" and names the anchor; nothing is half-applied in that
  file, because each script writes a file only after all its edits match.
- **B. Line numbers are refreshed; text anchors rule.** What: **Files** and **Verified facts** now give the model's line
  and the SDK's separately. Why: 3a's SDK-only Javadoc moved the SDK lines by 2 to 13. Cost if wrong: a reader opens
  the wrong line; the scripts do not use line numbers.
- **C. Task 1's fake test drops the redundant cast.** What: `var view=kit.create(…)`. Why: `FakeKit.create` already
  returns `FakeKitView`, and the cast added a `-Xlint:cast` warning. Cost if wrong: none (one warning).
- **D. Notice's `create` case sits before Separator's in `ControlRegistry`.** What: accept the order the anchors
  produce. Why: pattern-switch cases over distinct record types do not depend on order. Cost if wrong: none.
- **E. The EXCLUSIVE count rule needs no other code change.** What: Task 3 changes only File Server's two one-option
  choices. Why: every other EXCLUSIVE producer in main code has two to four options. Cost if wrong: Step 4's
  fallback (give the choice two to four options and record it in the banner) still applies.
- **F. In the SDK, SWATCH joins 3a's `IconSize` Javadoc instead of adding a second one.** What: the SDK gets one
  block (the PROMPT sentence, then the SWATCH sentence); the model, which has no Javadoc there, gets the SWATCH
  sentence alone. Why: two doc comments in a row leave the first dangling (a new lint warning) and drop the PROMPT
  sentence from the SDK javadoc. The model may differ in comments only (Global Constraints). Cost if wrong: one
  warning and a missing sentence of public SDK documentation.
- **G. Task 4 pins the 3a enums' names in the app's `KitValuesTest`.** What: `IconSize` (already in the text),
  `FormKind`, `LayoutRole` and `TextRole` each compared by name with the model, one line each, in
  `swatchesProjectWithoutLoss`. Why: 3a's final review found only `ChoiceKind` and `ButtonPresentation` pinned there,
  and each extra pin is one line. They pass at once (no red step), as the mirrors agree today. Cost if wrong: none;
  a later mismatch fails loudly.
- **H. Task 10's STATUS range skips the plan's own commits by subject.** What: commits whose subject starts with
  "Plan" are left out, and the range runs from the first `SDK 0.18` commit to the last. Why: the branch holds three
  plan commits (`5c0a2f374`, `41a3d9c38`, this refresh), so `commits[1]` would name a plan commit. Cost if wrong: the
  STATUS entry and banner name the wrong range, which the step's "read the new entry once" check catches.
- **I. Plan 3d is not anticipated.** What: no task changes for 3d's StandardMenu, ScopeSpec, PaletteVerb,
  `DialogSpec.swatch`, `ToolbarItem.View` or the header. Why: 3d runs in parallel; whichever plan merges second
  reconciles the shared files (`docs/STATUS.md`, `FakeKitView`, `KitContractTest`, `PluginContractTest`,
  `docs/plugin-ui.md`, and the SDK and model `Nodes.java` and `KitValues`; this plan adds no `migration.csv` row).
  Cost if wrong: textual merge conflicts in those files at the second merge.
- **J. A timing-sensitive test that fails once is re-run alone first.** What: before treating such a failure as real,
  re-run that test by itself. Why: another plan's Gradle builds share the machine. Cost if wrong: one extra run.
