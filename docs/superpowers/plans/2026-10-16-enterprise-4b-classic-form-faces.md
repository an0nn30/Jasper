# Enterprise presentation 4b: the classic form faces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jasper-ui gains `KitFaces` and the form half of spec 6.4's face pairs, each with its default and classic
implementation: the toggle (switch or checkbox), the buttons with the text-field policy (raised or classic gradient,
dropdown and picker faces, leading glyphs, read-only fields), the exclusive choice (segments or radios, with swatches and
preview thumbnails), the tabs (segments or folder tabs), the group (card or titled box) with the label policy (plain or
colon label column), the stepper (stepper or spinner) and the prompt (tile or plain); the isolation and variant-read
tests guard them, and the default look stays pixel-identical (Rulings 23).

**Architecture:** A face is a package-private interface in `dev.jasper.ui.swing` extending `Face` (which declares the
`(slot, against, guard)` pairs it paints), with one default and one classic class. `KitFaces.of(kit)` reads the kit's
`ControlVariants` once and holds one face per point; `KitContext` builds it with the context, and each control reads its
face from `context().faces()` at construction or refresh, never the kit. A default face delegates to the control's
existing code, moved verbatim into a named method on the control, so the default look paints exactly what it painted.
Classic faces paint theme roles and spec 7.1's derived roles, every declared pair through its guard; their geometry is
4a's `DesignMetrics.CLASSIC`. `StrategyIsolationTest` keeps faces off the kit and the reconciler off the faces;
`VariantReadTest` keeps `ControlVariants` inside the kit package, the app's presentation package and `KitFaces`;
`PaintSpecTest` freezes every face's declared pairs in `face-pairs.tsv`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, the JDK class-file API (`java.lang.classfile`),
Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts
(always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2, 6.4, 7.1 to 7.3, 16.1 and 19.1 row 4, the prompt row as amended), with
[DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) §6 and the mocks in
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (New File Server, Add Host, New Tunnel,
Settings, Credential Vault). The measured targets are in plan 4a's "Mock measurements"
([plan 4a](2026-10-16-enterprise-4a-classic-catalog.md)), whose values (roles, catalogs, fixture kits) this plan reads.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-4`, branch
`claude/enterprise-classic-catalog`, continuing from `91153a4ce` (`main`, which holds plans 3a to 3d and 4a). Before
Task 1 check that 4a is complete:

```bash
git log --oneline -1 --grep 'Classic catalog: documentation and status'   # prints 4a's 46aa55a80
grep -c 'public static final Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java   # 1
git merge-base --is-ancestor 91153a4ce HEAD && echo contains-main                               # contains-main
```

Then, before Task 1's first edit, re-record the gallery base at the current `HEAD` with plan 4a Task 1 Step 1's
commands (the base left by 4a predates plan 3c's `tables-0-18` fixture, so its `manifest.json`, `sheet-10.png` and
`tables-0-18.*` differ from today's gallery; Execution notes X1):

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan4-base && mkdir -p jasper-ui/build/plan4-base
cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan4-base/
ls jasper-ui/build/plan4-base/dark-1x | wc -l   # 161: 75 fixtures' PNG and JSON, 10 contact sheets, the manifest
```

Keep this directory in place through plan 4c. Run every command from the worktree root. Every quoted anchor was re-read
at `91153a4ce` (`main` with plans 3a to 3d and 4a merged; plan 3e is not on this branch and shares only
`docs/STATUS.md` with this plan); lines that plans 3b and 3c changed are quoted in their current form. The edit scripts
stop with a message when their text is not found the stated number of times; re-find the quoted text and re-run, never
edit around a missing anchor.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-classic-catalog`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look does not change. Every PNG file the headless gallery writes for Slate Cobalt Dark and Light at scale 1 is byte-identical to the base (`jasper-ui/build/plan4-base/`, re-recorded at `HEAD` before Task 1; Workspace), and every JSON file is identical except the `class` field of a section's surface, `javax.swing.JPanel` before and `dev.jasper.ui.swing.Cards$SectionSurface` from Task 5 on (Rulings 23; the digest script reads that one name as the class it replaces, and plan 4c adds `ItemLists$ListPane` for `javax.swing.JScrollPane`); no `tools/visual/` pair changes. Classic renders are new files under `jasper-ui/build/classic-faces/` only (inspection output, not committed). Tasks 1 to 7 each prove it; Task 8 proves it once more.
- Faces never reference `Kit`, `ControlVariants` or (plan 5's) `ChromeSpec`; only `KitFaces` reads the variants outside the kit package; the reconciler references control classes only (spec 3.3, 6.4, 16.1.7).
- A default face's implementation is the control's former code, moved into a named method on the control and called unchanged; renderers never branch on a look (spec 3.6), they ask their face.
- Each face declares every pair it repairs (spec 7.3); a classic face paints each declared pair with `ThemePainting.painted`; default faces declare only the two pairs the default look repairs today (the selected segment label and the selected preview caption), so the default look is unchanged.
- Colours come from the theme only (`ColourLiteralScanTest`); classic glyphs (check mark, triangles, radio dot) are drawn shapes in theme roles, never characters or literals.
- No interface without two real implementations (AGENTS.md): every face interface has its default and its classic class.
- jasper-ui's package graph: `dev.jasper.ui.swing` reads `dev.jasper.ui.kit` (already allowed by plan 1's `package-info`); `verifyUiArchitecture` stays green.
- Javadoc doclint fails the build on errors: jasper-ui runs `-Xdoclint:all,-missing`, jasper-app `-Xdoclint:all`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- Every new candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`); a row has exactly eight comma-separated fields. This plan adds two jasper-app tests (Task 1), so two rows.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; `…` and `×` in copy are ordinary characters.
- The identifier word "hero" stays reserved for `Form.hero` (3a's `PromptVocabularyTest`).

## Review Focus

1. A checkbox on a selected list row (a plugin row in Settings › Plugins) or a disabled one (Require TLS while Use TLS
   is off): the box takes the row's ink on the fill, with no accent square, and a disabled box is flat and grey; it
   never animates. Task 1: `ClassicTogglesTest.aBoxOnASelectedRowTakesTheRowsInkAndADisabledBoxIsFlat` and
   `aClassicToggleSettlesWithoutMotion`.
2. A disabled primary button (Add Server while the form is incomplete): a flat fill, a lighter edge and faded text, not a
   faded gradient or a faded accent, and no drop shadow left behind from the default look. Task 2:
   `ClassicButtonsTest.aDisabledButtonIsFlatWithFadedText` and `aPrimaryButtonHasNoDrop`.
3. A theme whose controls are borderless (Catppuccin's `border.control` equals `surface.input`): classic radios,
   checkboxes, buttons and group boxes stay outlined, because each edge is painted through its guard. Tasks 1, 2, 3 and
   5: the painted-pair tests over `KitFixtures.themes()` and `degenerate()`.
4. A narrow window or a large interface font: radio rows wrap to a second line instead of clipping a label, the colon
   label column grows to the widest label, and a trailing Port field keeps its "Port:" label beside it. Task 3:
   `ClassicChoicesTest.radiosWrapRatherThanClipALabel`; Task 5: `ClassicGroupsTest.theLabelColumnIsTheWidestLabelOrTheFloor`
   and `aTrailingPortFieldHasItsOwnLabel`.
5. Keyboard use of a classic radio row and of folder tabs: Left, Right, Home and End move and select as on the segment
   track, Up and Down never throw, and a SLIDER under the classic kit is a spinner whose arrows step the value. Task 3:
   `ClassicChoicesTest.theKeysMoveAlongTheRadios`; Task 6: `ClassicSpinnersTest.aSliderIsASpinnerUnderTheClassicKit`.

## Rulings

Decisions where the spec is silent, taken on the user's behalf (2026-10-16): "what — why — cost if wrong". Record them in
`docs/STATUS.md` (Task 8) and in the banner when executed.

1. **Faces are package-private interfaces in `dev.jasper.ui.swing`, named `*Face` (and `LabelPolicy`, `FieldPolicy`),
   each extending `Face`; `KitFaces` is public only for `KitFaces.declared(Kit)`** — the painted-contrast suite in
   jasper-app needs the declared pairs, nothing else outside the package needs a face — cost if wrong: one more
   accessor.
2. **A default face calls the control's former code, moved into a named method on the control** (`paintSwitch`,
   `paintRaised`, `paintSegments`, `paintDefault`, `paintRichFace`, `paintTile`) — moving bodies verbatim is the only
   way to keep the default look byte-identical with no risk of drift — cost if wrong: two-line methods on controls.
3. **The exclusive and tabs points share one interface, `ChoiceFace`** — both draw a choice's options on a track and
   differ only in their classic implementation; `KitFaces` holds a `ChoiceFace` for each point — cost if wrong: none.
4. **The classic checkbox's edges and the radio's ring use `border.hover`** — the mocks draw an unchecked box and ring
   in `#9AA0A8`, darker than the control border `#B4B8BF`; `border.hover` (Enterprise Light ≈ `#909399`) is the derived
   darker control edge — cost if wrong: a slightly darker ring than the mock.
5. **Classic faces declare their pairs against the kit's dialog surface slot** (`ColorSlots.dialogSurface()`, Enterprise
   `surface.app`), which `KitFaces.of` passes to their constructors — classic group boxes are transparent, so a control
   stands on the dialog body; faces still never see the kit — cost if wrong: a guard measured against the wrong surface
   on a Settings page (plan 9 can pass the page surface).
6. **A disabled classic button is flat: `surface.tableHeader` (secondary) or `surface.header` (primary), a
   `border.divider` edge and `text.primary` at the 0.42 alpha** (mocks: Remove and Add Server) — spec 6.4 says "a flat
   fill, a lighter border and disabled text" and 7.1 keeps disabled text's 0.42 alpha — cost if wrong: a grey a little
   off the mock's `#A3A8B0`.
7. **Classic ghost buttons are flat until hovered only in INLINE and NAVIGATION stacks, panel headers, panel rows and
   contextual slots; elsewhere (ACTIONS, FOOTER, VERTICAL, forms, side columns) they draw as standard buttons** — spec
   6.4 names toolbars, panel headers and navigation rows; `StackSurface` learns its layout role to tell them apart —
   cost if wrong: a ghost in an unusual stack draws standard.
8. **A LINK button keeps the default treatment under the classic face in this plan**; plan 4c's side column draws a LINK
   `addAction` as a standard button (spec 6.4) — cost if wrong: none.
9. **Picker menu rows lose their tiles; the rich Settings dropdown rows (`RichOptions`) keep theirs until plan 9** —
   spec 6.4 says popup rows draw no tiles; `RichOptions` belongs to the Settings composition plan 9 owns — cost if
   wrong: tiles in one Settings dropdown's rows until plan 9.
10. **Classic SEARCH, PATH and PORT fields show no leading glyph or `:` prefix** — spec 6.4 drops the SEARCH and PATH
    glyphs, and the classic PORT field has its own "Port:" label (the label policy), so its `:` prefix would read twice
    — cost if wrong: a missing colon in a PORT field with no row label.
11. **The classic radio row is left-aligned, options 18 apart, and wraps line by line**; Up and Down move by a line's
    worth like the segment track — DESIGN_LANGUAGE "generous gaps", and wrapping keeps labels whole at large fonts —
    cost if wrong: none.
12. **Classic preview thumbnails keep their size and sit left-aligned 20 apart**, with the radio and its label below,
    left-aligned under each thumbnail (Settings › Appearance mock) — the default grid stretches them — cost if wrong:
    none.
13. **Folder tabs: the active tab `surface.card`, inactive `surface.stripe`, edges `border.control`, labels
    `text.primary`, 26 tall, 14 side padding, a 2 px top radius** (Settings › Plugins mock: `#FFFFFF`, `#E4E6E9`,
    `#B4B8BF`) — `surface.stripe` is the derived role whose Enterprise override is that grey — cost if wrong: an
    inactive tab one shade off in other themes.
14. **The titled box draws `border.window` through the structural guard, with its title in `text.primary` as spec 6.4
    says**; the New File Server mock draws the title `#3A3D42` (`text.strong.secondary`) — spec decisions outrank mocks;
    recorded as a measured residual — cost if wrong: one role.
15. **Classic FormRow labels are `text.strong.secondary` through the text guard; a colon is added unless the label is
    blank or already ends with one; captions use the DESCRIPTION face in `text.secondary` and may wrap** (mocks: "Name:"
    `#3A3D42`, "Tried in order" `#5C6370`) — cost if wrong: none.
16. **The colon label column is the widest label in its card, at least `FORM_LABEL_WIDTH` (classic 100), plus
    `COLUMN_GAP` (classic 12)** — spec 6.4's "as wide as the card's widest label" with a floor that reproduces the mocks'
    field column (x = 144) in New File Server, Add Host and New Tunnel; Add Login's narrower column (94) is a residual for
    plan 10's Vault compositions — cost if wrong: 19 px in Add Login.
17. **A non-Toggle SettingRow in a classic form card takes the colon label column; its control sits at the field column
    at its natural width** (Add Host's Options › Group) — spec 6.4's composition rule — cost if wrong: none.
18. **An unlabelled EXCLUSIVE choice directly in a classic card is inset to the field column** (spec 6.4: "an unlabelled
    EXCLUSIVE choice in a MIXED card sits in the control column"); it applies in FORM and MIXED cards alike — cost if
    wrong: a choice in a FORM card that wanted the flush-left position moves right.
19. **Classic SETTINGS cards pad 8 above and below their rows** (`ROW_GAP`), so a 26 px checkbox row sits where the New
    File Server mock draws it under its group title — cost if wrong: a few pixels in other forms.
20. **A SLIDER under the classic stepper face is built as a spinner** (`ControlRegistry.create` asks the face) — spec 5.2:
    "SLIDER draws the same" — cost if wrong: none; the field's value and events are the same.
21. **The classic prompt hides its `PROMPT_TITLE` text in the body** (the dialog header strip shows the form's title,
    plan 6) **and inks its glyph on an accent tile** — the amended prompt row; between plans 5 and 6 a classic prompt in a
    window without that strip shows no title — cost if wrong: one plan's interval without a title in Unlock Vault.
22. **Keycaps, the joined Back/Forward pair and notification cards ("looks that need no point", spec 5.2) are not faces
    here**: their geometry is in 4a's catalog and their paint belongs to the plans that own those surfaces (plans 6, 7 and
    9) — cost if wrong: those three keep the default paint until their plan.
23. **A section's surface is a named `JPanel` subclass (`Cards.SectionSurface`), so the gallery's JSON names a new class
    for it** — the gallery records each owned component's class (`KitGallery.metadata`); the classic titled box paints
    from the section's own `paintComponent`, which a plain `JPanel` has no hook for, and the pixel rule is the spec's
    "pixel-identical" (spec 3, 7.3): every PNG stays byte-identical, and the JSON differs only in that `class` field of each
    section, which the digest script reads as `javax.swing.JPanel`, nothing else — cost if wrong: a border
    or UI-delegate hook instead of the subclass, the same faces.

## Verified facts

Re-read at `91153a4ce` (`main`: plans 3a to 3d and 4a merged; Execution notes, 2026-10-16).

1. `ControlRegistry.Control` holds `view`, `component`, `node` and `context()` (`ControlRegistry.java:92-110`);
   `ControlRegistry.create` builds a `Field` as `NumericControls.Stepper`, `NumericControls.Slider` or
   `TextFields.FieldControl` by presentation (`:33`), and a `Choice` as `PreviewChoices` or `Choices.ChoiceControl`
   (`:35`).
2. `KitContext` has a private constructor taking the kit (`KitContext.java:24-30`), a copy constructor (`:32-34`) and
   `KitContext.of` (`:39-42`); `kit()` at `:44`.
3. `Switches.SwitchButton` holds `context`, `focus`, `position`, `compact`, `error`, `pending`, `onSelection`, `ink`
   (`Switches.java:64-73`); its `getPreferredSize` and `paintComponent` bodies are at `:75-108`; `ToggleControl.refresh`
   animates `if (view.motion().enabled())` (`:55`).
4. `SettingRows.RowControl` mounts `[title, description, control]` (`SettingRows.java:66`); `RowLayout` (`:101-259`):
   `height` and `layoutContainer` handle settings-stacked, detail and plain rows (`:165-189`, `:202-258`), and the plain
   row's floor expression is in `height` (`:180-187`); `paintInset` reserves a switch's halo (`:157-162`).
5. `ItemLists.ListControl.inlineHeight` returns a toggle's `COMPACT_SWITCH_HEIGHT` or `SWITCH_HEIGHT` (`ItemLists.java:322-327`).
6. `Buttons.KitButton` has `private final KitContext context` (`Buttons.java:104`), flags `prompt, panel, fullFooter,
   orderedAction, panelDetail, circular, hint`, `embedded, contextual, panelRow`, `toggle`, `focus` (`:123-139`),
   `onSelection`, `ink` and `textRole` (`:188-202`); plan 3b's `noticeGround` and `textColor`, which holds a Notice
   link to the text floor on its severity's ground before the role's colour (`:203-210`); `fill` and `border`
   (`:246-256`); `paintComponent` fades a disabled button as one layer (`:322-328`) and `paintFace` computes fill and
   border, paints the shape, `primaryShadows`, then the glyph and label (`:330-385`). `ButtonControl.refresh` sets the
   label font from a role chosen by flags (`:73`).
7. `Choices.ChoiceControl` builds EXCLUSIVE and TABS as a `SegmentTrack` of `SegmentButton`s laid out by
   `SegmentLayout` (`Choices.java:80`, `:142-172`); the UP and DOWN bindings cast the layout to `SegmentLayout`
   (`:151-152`); the per-option styling block sets the font, foreground, `HueDot` icon and padding (`:165-172`).
   `HueDot` is a private record (`:231`). `SegmentButton.textColor` uses `SurfaceContrast.on` (`:535-539`), and its
   `getPreferredSize` and `paintComponent` are at `:540-552`; `SegmentTrack.paintComponent` at `:517-526`.
   `ChoiceCombo.paintComponent` returns early for a bare cell (plan 3c), then paints the control face (`:439-442`); the
   arrow button paints the chevron in `bare() && cellInk != null ? cellInk.supporting : SemanticColor.TEXT_SECONDARY`
   (`:364-375`, 3c's selected-row ink); `bare()` at `:321`; `PickerButton.paintComponent` paints a rich face or itself
   and a chevron (`:248-258`).
8. `PreviewChoices.Tile` is a private inner class (`PreviewChoices.java:53`); its `getPreferredSize` and
   `paintComponent` are at `:58-84`, `miniature` at `:89-104`; the tiles are laid out by `GridLayout` (`:43`).
9. `PickerMenus.paintFace(JComponent, Graphics, KitContext, PickerFace, boolean, boolean, boolean, boolean)` paints the
   rich face (`PickerMenus.java:23-54`); `line` is private with 13 parameters, `technical` last (`:55`); menu rows
   reserve and paint a tile (`:141-150`); the footer divider is `BORDER_MENU` (`:74`); the search glyph's border is set
   in `refresh` (`:111`).
10. `TextFields.FieldControl` holds `label`, `error`, `leading` (`TextFields.java:21`); `refresh` (`:273`) sets
    `input.setEditable(component.isEnabled() && !field.readOnly()); surface.setEnabled(component.isEnabled());`
    (`:276`), the input's foreground to `text.primary` (`:287`), the leading glyph (`:306`) and its visibility (`:316`);
    `FieldSurface` (`:388`) holds `port` (`:397`) and paints the field in `paintComponent` (`:449-456`).
11. `Cards.CardControl.refresh` pads FORM cards (`Cards.java:29-47`, the border at `:37`); `children` embeds EXCLUSIVE
    labels and mounts children (`:49-60`); `CardSurface.paintComponent` paints the rounded card and the settings
    dividers (`:138-157`); `SectionControl` is a plain `JPanel` with a SECTION_LABEL title over its body (`:160-187`).
12. `FormRows.RowControl` mounts `[label, caption, controls…]` (`FormRows.java:16-70`, `Stacks.mount` at `:62`);
    `RowLayout.labelWidth()` is `FORM_LABEL_WIDTH` (`:79-81`), `trailing` sizes component 3 (`:87-93`).
13. `NumericControls.Stepper` puts `minus` WEST and `plus` EAST on the field surface with a centred `ValueLayout`
    (`NumericControls.java:13-50`); `button(...)` paints "−" and "+" with its `cue` focus (`:51-74`); `refresh` sets
    the value to LABEL and the unit to `text.tertiary` (`:75-93`).
14. `Stacks.StackSurface` holds `joined` and `route` (`Stacks.java:21`); `StackControl.refresh` sets them and the
    layout, PROMPT_HEADER using `PromptLayout` (`:91-121`, the route flag at `:95`, `PromptLayout` at `:103`);
    `childParent` puts a PROMPT_HEADER's Icon in the `Tile` (`:124-130`); `children` starts at `:132`;
    `Tile.paintComponent` paints `surface.control` with a `border.control` edge at `PROMPT_TILE_RADIUS` (`:190-203`).
15. `Labels.TextControl` (`Labels.java:100`) holds `override` and `settings` (`:106-108`) and ends `refresh` with
    `centeredLeading` (`:143-145`); `Labels.IconControl.refresh` paints a glyph in the row ink when `inked`
    (`:161-180`); `InteractiveRows.Ink` has `ACCENT` and `SELECTION` (`InteractiveRows.java:1268-1270`).
16. `PaintedContrastTest.DEFAULT_PAIRS` (`PaintedContrastTest.java:46`) declares "selected segment label"
    `(TEXT_PRIMARY, SEGMENT_SELECTED_BG, TEXT)` and "selected preview caption" `(TEXT_PRIMARY, SURFACE_CARD, TEXT)`
    (`:58-62`), then plan 3b's Notice and Properties pairs; `palettes()` follows the list (`:90`).
17. `StaticDefaultTest.classFiles(Path, int)` reads a jar's class files and is package-visible to other
    `dev.jasper.app.architecture` tests (`StaticDefaultTest.java:157`); jasper-app's tests get `jasper.appJar` and
    `jasper.uiJar` system properties (`jasper-app/build.gradle.kts:95-98`).
18. `KitViewTest.find(Component, String, Class)` and `release(KitContext)` are static helpers other swing tests import
    (`KitViewTest.java:24-39`); `KitView.control(id)` is package-private (`KitView.java:353`); `ViewReconciler`
    resets a SettingRow's `panelFooter` at `:94` and a TextControl's `override` at `:116`.

## File structure

- New in `jasper-ui/src/main/java/dev/jasper/ui/swing/`: `Face`, `KitFaces`, `ClassicGlyphs`; `ToggleFace`,
  `DefaultToggleFace`, `ClassicToggleFace` (Task 1); `ButtonFace`, `DefaultButtonFace`, `ClassicButtonFace`,
  `FieldPolicy`, `DefaultFieldPolicy`, `ClassicFieldPolicy` (Task 2); `ChoiceFace`, `DefaultChoiceFace`,
  `ClassicRadiosFace` (Task 3); `ClassicFolderTabsFace` (Task 4); `GroupFace`, `DefaultGroupFace`, `ClassicGroupFace`,
  `LabelPolicy`, `DefaultLabelPolicy`, `ClassicLabelPolicy` (Task 5); `StepperFace`, `DefaultStepperFace`,
  `ClassicStepperFace` (Task 6); `PromptFace`, `DefaultPromptFace`, `ClassicPromptFace` (Task 7).
- Changed controls: `KitContext`, `ThemePainting`, `Switches`, `SettingRows`, `ItemLists` (Task 1); `Buttons`,
  `Choices`, `PickerMenus`, `TextFields`, `Stacks` (Task 2); `Choices`, `PreviewChoices` (Task 3); `Cards`, `FormRows`,
  `SettingRows` (Task 5); `NumericControls`, `ControlRegistry` (Task 6); `Stacks` (Task 7).
- Tests: `jasper-ui/src/test/java/dev/jasper/ui/swing/` `ClassicTogglesTest`, `ClassicButtonsTest`, `ClassicChoicesTest`,
  `ClassicTabsTest`, `ClassicGroupsTest`, `ClassicSpinnersTest`, `ClassicPromptsTest`, `PaintSpecTest`, and the frozen
  `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv`; `jasper-app/src/test/java/dev/jasper/app/architecture/`
  `StrategyIsolationTest`, `VariantReadTest`; `PaintedContrastTest` (Task 3).
- Fixtures: `KitFixtures.capture(KitContext, ViewSpec, int, int, String)` writes a classic render under
  `jasper-ui/build/classic-faces/` (Task 1).

---

### Task 1: `KitFaces` and the toggle

`Face`, `KitFaces` and the first face pair: the default switch and the classic checkbox, which leads its label in a
setting row and never animates. `KitContext` builds the faces; `ThemePainting` gains `role` and `painted`; the isolation
and variant-read tests and `PaintSpecTest` start here.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/Face.java`, `KitFaces.java`, `ClassicGlyphs.java`, `ToggleFace.java`, `DefaultToggleFace.java`, `ClassicToggleFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitContext.java:22-44`, `ThemePainting.java:27`, `Switches.java:52-108`, `SettingRows.java:101-259`, `ItemLists.java:322-327`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java` (`capture`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTogglesTest.java`, `PaintSpecTest.java`, `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv`
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/StrategyIsolationTest.java`, `VariantReadTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (two rows)

**Interfaces:**
- Consumes: plan 4a's `Kit.controls()`, `Kit.colors()`, `ControlVariants`, `ColorSlots`, `KitFixtures.enterprise()`,
  the roles `CHECKBOX_BOX`, `CHECKBOX_RADIUS`, `CHECKBOX_LABEL_GAP`.
- Produces: `interface Face { List<PaintPair> pairs(); }`; `public final class KitFaces` with package-private `final
  ToggleFace toggle`, `static KitFaces of(Kit)`, `List<Face> all()` and `public static List<PaintPair> declared(Kit)`;
  `KitFaces KitContext.faces()`; `static Color ThemePainting.role(KitContext, ColorRole)` and `static Color
  ThemePainting.painted(KitContext, PaintPair)`; `interface ToggleFace extends Face { Dimension
  preferredSize(Switches.SwitchButton); void paint(Switches.SwitchButton, Graphics2D); boolean leads(); boolean moves();
  int rowHeight(KitContext, boolean compact); }`; `final ToggleFace Switches.SwitchButton.face`,
  `Dimension SwitchButton.switchSize()`, `void SwitchButton.paintSwitch(Graphics2D)`; `ClassicGlyphs.check`, `dash`,
  `down`, `up`, `right`; `public static java.awt.image.BufferedImage KitFixtures.capture(KitContext, ViewSpec, int, int, String)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTogglesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's toggle point: the classic face is a checkbox leading its label; the default keeps the switch. */
@SuppressWarnings("try")
class ClassicTogglesTest {
    static ViewSpec row(boolean checked, ControlState state) {
        return new ViewSpec(new Nodes.Card("card", ControlState.READY, Nodes.CardKind.SETTINGS, List.of(
            new Nodes.SettingRow("row", ControlState.READY, "Allow changes", "Clients can upload, rename and delete files",
                new Nodes.Toggle("toggle", state, "Allow changes", checked, "toggle", false), false))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicToggleIsACheckboxLeadingItsTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, row(false, ControlState.READY), events::add)) {
                KitFixtures.capture(context, view.spec(), 480, 80, "toggle-checkbox");
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                var title = find(view, "row.title", JComponent.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                assertThat(box.getPreferredSize()).isEqualTo(new java.awt.Dimension(size + 2 * ring, size + 2 * ring));
                var boxAt = SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view);
                var titleAt = SwingUtilities.convertPoint(title.getParent(), title.getLocation(), view);
                assertThat(boxAt.x + ring + size + ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP)).as("the box leads").isEqualTo(titleAt.x);
                box.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Toggled("toggle", true));
            } finally { release(context); }
        });
    }

    /** Spec 16.1.2 for the face: the box paints its declared pairs as their guards make them, over every palette. */
    @Test void theBoxPaintsItsDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().toggle.pairs();
            try {
                for (boolean checked : new boolean[] {true, false})
                    try (var view = new KitView(context, row(checked, ControlState.READY), e -> {})) {
                        KitFixtures.render(view, 480, 80, 1);
                        var box = find(view, "toggle", Switches.SwitchButton.class);
                        var image = paint(box);
                        int size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                        int x = (box.getWidth() - size) / 2, y = (box.getHeight() - size) / 2;
                        Color edge = new Color(image.getRGB(x, y + size / 2), true), fill = new Color(image.getRGB(x + size - 3, y + size - 3), true);
                        String where = theme.name() + (theme.dark() ? " Dark" : " Light") + (checked ? " checked" : " unchecked");
                        assertThat(edge).as(where + " edge").isEqualTo(ThemePainting.painted(context, checked ? pairs.get(0) : pairs.get(2)));
                        assertThat(fill).as(where + " fill").isEqualTo(ThemePainting.color(context, checked ? SemanticColor.ACCENT : SemanticColor.SURFACE_INPUT));
                    }
            } finally { release(context); }
        });
    }

    /** Review Focus 1: on a selected row's fill the box is an outline in the row's ink; a disabled box is flat and grey. */
    @Test void aBoxOnASelectedRowTakesTheRowsInkAndADisabledBoxIsFlat() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, row(true, new ControlState(true, false, false, "")), e -> {})) {
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                int size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                int x = (box.getWidth() - size) / 2, y = (box.getHeight() - size) / 2;
                var image = paint(box);
                assertThat(new Color(image.getRGB(x + size - 3, y + size - 3), true)).as("disabled fill")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(new Color(image.getRGB(x, y + size / 2), true)).as("disabled edge")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                box.onSelection = true; box.ink = InteractiveRows.Ink.SELECTION; box.setEnabled(true);
                image = paint(box);
                assertThat(new Color(image.getRGB(x, y + size / 2), true)).as("ink edge")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION));
                assertThat(image.getRGB(x + size - 3, y + size - 3) >>> 24).as("no fill on the row's fill").isZero();
            } finally { release(context); }
        });
    }

    @Test void aClassicToggleSettlesWithoutMotion() {
        Edt.run(() -> {
            var theme = KitFixtures.light();
            var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
            var context = KitContext.of(KitFixtures.enterprise(), binding, ThemeRoute.WORKSPACE, new IconRegistry(), () -> true, action -> "");
            try (var view = new KitView(context, row(false, ControlState.READY), e -> {})) {
                view.update(row(true, ControlState.READY));
                var control = (Switches.ToggleControl) view.control("toggle");
                assertThat(control.animation.isRunning()).isFalse();
                assertThat(control.button.position).isEqualTo(1);
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheTrailingSwitch() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(false, ControlState.READY), e -> {})) {
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                assertThat(context.faces().toggle).isInstanceOf(DefaultToggleFace.class);
                assertThat(box.getPreferredSize()).isEqualTo(box.switchSize());
                var title = find(view, "row.title", JComponent.class);
                assertThat(SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view).x)
                    .as("the switch trails").isGreaterThan(SwingUtilities.convertPoint(title.getParent(), title.getLocation(), view).x);
            } finally { release(context); }
        });
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/PaintSpecTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Spec 16.1.1: every face's declared (slot, against, guard) table, frozen; a face change edits face-pairs.tsv with it. */
class PaintSpecTest {
    static List<String> rows() {
        var rows = new ArrayList<String>();
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC))
            for (var face : KitFaces.of(kit).all()) for (var pair : face.pairs())
                rows.add(String.join("\t", kit.id(), face.getClass().getSimpleName(), pair.slot().key(), pair.against().key(),
                    String.format(Locale.ROOT, "%s:%s", pair.guard().kind(), pair.guard().floor())));
        return rows;
    }

    @Test void theDeclaredPairsAreFrozen() throws Exception {
        var frozen = new ArrayList<String>();
        try (var in = PaintSpecTest.class.getResourceAsStream("face-pairs.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) frozen.add(line);
        }
        assertThat(rows()).containsExactlyElementsOf(frozen);
    }

    @Test void everyPairStandsOnAnotherRoleAndNoFaceDeclaresOneTwice() {
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC, KitFixtures.enterprise(), KitFixtures.complement()))
            for (var face : KitFaces.of(kit).all()) {
                var seen = new HashSet<>();
                for (var pair : face.pairs()) {
                    assertThat(pair.slot()).as("%s %s", kit.id(), face).isNotEqualTo(pair.against());
                    assertThat(seen.add(pair)).as("%s %s declares %s twice", kit.id(), face, pair).isTrue();
                }
            }
    }

    @Test void everyClassicFaceDeclaresWhatItPaints() {
        for (var face : KitFaces.of(Kit.CLASSIC).all())
            assertThat(face.pairs()).as(face.getClass().getSimpleName()).isNotEmpty();
    }
}
```

Create `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` with a script (exact tabs):

```bash
mkdir -p jasper-ui/src/test/resources/dev/jasper/ui/swing
python3 -I - <<'PY'
import pathlib
rows = [("classic", "ClassicToggleFace", "accent.edge", "surface.app", "CONTRAST:3.0"),
        ("classic", "ClassicToggleFace", "text.onAccent", "accent", "CONTRAST:3.0"),
        ("classic", "ClassicToggleFace", "border.hover", "surface.input", "CONTRAST:1.5")]
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv").write_text(
    "# kit\tface\tslot\tagainst\tguard (spec 16.1.1): every pair each face of Kit.DEFAULT and Kit.CLASSIC declares, in KitFaces order\n"
    + "".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/architecture/StrategyIsolationTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 16.1.7: faces never reference Kit, ControlVariants or ChromeSpec (they take what they
 * paint as arguments), and the reconciler references control classes only, never a face or KitFaces.
 */
class StrategyIsolationTest {
    static final String FACE = "dev/jasper/ui/swing/Face", RECONCILER = "dev/jasper/ui/swing/ViewReconciler";
    static final Pattern FORBIDDEN = Pattern.compile(
        "dev/jasper/ui/kit/(?:Kit|ControlVariants)(?=[;$.]|$)|dev/jasper/app/presentation/ChromeSpec(?=[;$.]|$)");

    /** A class's name, the interfaces it names and every string in its constant pool. */
    record Scanned(String name, List<String> interfaces, Set<String> strings) {
        static Scanned of(byte[] bytes) {
            var model = ClassFile.of().parse(bytes);
            var strings = new HashSet<String>();
            for (var entry : model.constantPool()) if (entry instanceof Utf8Entry utf8) strings.add(utf8.stringValue());
            return new Scanned(model.thisClass().asInternalName(), model.interfaces().stream().map(i -> i.asInternalName()).toList(), strings);
        }
    }

    /** The face classes: every class implementing Face or an interface that extends it, and their nested classes. */
    static Set<String> faces(List<Scanned> classes) {
        var interfaces = new HashSet<String>(Set.of(FACE));
        for (boolean grew = true; grew; ) {
            grew = false;
            for (var c : classes) if (!interfaces.contains(c.name()) && c.interfaces().stream().anyMatch(interfaces::contains)) {
                interfaces.add(c.name()); grew = true;
            }
        }
        var faces = new HashSet<String>();
        for (var c : classes) if (interfaces.contains(c.name()) || c.interfaces().stream().anyMatch(interfaces::contains)) faces.add(c.name());
        for (var c : classes) for (var face : List.copyOf(faces)) if (c.name().startsWith(face + "$")) faces.add(c.name());
        return faces;
    }

    static List<String> violations(List<Scanned> classes) {
        var faces = faces(classes);
        var found = new ArrayList<String>();
        for (var c : classes) {
            if (faces.contains(c.name()))
                for (var s : c.strings()) { var m = FORBIDDEN.matcher(s); if (m.find()) found.add(c.name() + " references " + m.group()); }
            if (c.name().equals(RECONCILER) || c.name().startsWith(RECONCILER + "$"))
                for (var s : c.strings()) {
                    if (s.equals("dev/jasper/ui/swing/KitFaces") || s.contains("Ldev/jasper/ui/swing/KitFaces;")) { found.add(c.name() + " references " + s); continue; }
                    for (var face : faces) if (s.equals(face) || s.contains("L" + face + ";")) { found.add(c.name() + " references " + s); break; }
                }
        }
        return found;
    }

    static List<Scanned> uiClasses() throws Exception {
        var classes = new ArrayList<Scanned>();
        for (var bytes : StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100)) classes.add(Scanned.of(bytes));
        return classes;
    }

    @Test void facesNeverReferenceTheKitAndTheReconcilerNoFace() throws Exception {
        var classes = uiClasses();
        assertThat(faces(classes)).as("the scan is live").contains("dev/jasper/ui/swing/DefaultToggleFace", "dev/jasper/ui/swing/ClassicToggleFace");
        assertThat(violations(classes)).isEmpty();
    }

    /** A probe face holding a Kit field, compiled here, is reported; so is a reconciler naming a face. */
    @Test void aFaceThatReadsTheKitIsReported() throws Exception {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.ui.swing.ProbeFace"), builder -> builder
            .withInterfaceSymbols(ClassDesc.of("dev.jasper.ui.swing.ToggleFace"))
            .withField("kit", ClassDesc.of("dev.jasper.ui.kit.Kit"), ClassFile.ACC_PRIVATE));
        var classes = new ArrayList<>(uiClasses());
        classes.add(Scanned.of(probe));
        assertThat(violations(classes)).contains("dev/jasper/ui/swing/ProbeFace references dev/jasper/ui/kit/Kit");
        byte[] reconciler = ClassFile.of().build(ClassDesc.of("dev.jasper.ui.swing.ViewReconciler$Probe"), builder -> builder
            .withField("faces", ClassDesc.of("dev.jasper.ui.swing.KitFaces"), ClassFile.ACC_PRIVATE));
        assertThat(violations(List.of(Scanned.of(reconciler)))).contains("dev/jasper/ui/swing/ViewReconciler$Probe references Ldev/jasper/ui/swing/KitFaces;");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 16.1.7, the variant-read scan: ControlVariants is read only in dev.jasper.ui.kit,
 * dev.jasper.app.presentation (plan 5's parser) and KitFaces, which chooses each control's face once.
 */
class VariantReadTest {
    static final Pattern VARIANTS = Pattern.compile("dev/jasper/ui/kit/ControlVariants(?=[;$.]|$)");

    static boolean allowed(String type) {
        return type.startsWith("dev/jasper/ui/kit/") || type.startsWith("dev/jasper/app/presentation/")
            || type.equals("dev/jasper/ui/swing/KitFaces") || type.startsWith("dev/jasper/ui/swing/KitFaces$");
    }

    /** The classes in {@code files} that name ControlVariants, as internal names; {@code all} keeps the allowed ones too. */
    static List<String> readers(List<byte[]> files, boolean all) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (!all && allowed(scanned.name())) continue;
            if (scanned.strings().stream().anyMatch(s -> VARIANTS.matcher(s).find())) found.add(scanned.name());
        }
        return found;
    }

    static List<byte[]> jars() throws Exception {
        var files = new ArrayList<byte[]>(StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400));
        files.addAll(StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100));
        return files;
    }

    @Test void onlyTheKitPackageThePresentationPackageAndKitFacesReadTheVariants() throws Exception {
        var files = jars();
        assertThat(readers(files, true)).as("the scan is live").contains("dev/jasper/ui/swing/KitFaces", "dev/jasper/ui/kit/Kit");
        assertThat(readers(files, false)).isEmpty();
    }

    @Test void aReadElsewhereIsReported() throws Exception {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.VariantProbe"), builder -> builder
            .withField("controls", ClassDesc.of("dev.jasper.ui.kit.ControlVariants"), ClassFile.ACC_PRIVATE));
        assertThat(readers(List.of(probe), false)).containsExactly("dev/jasper/app/workspace/VariantProbe");
    }
}
```

Add the two `migration.csv` rows (each eight fields; the text uses semicolons):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
rows = [
    "jasper-app/src/test/java/dev/jasper/app/architecture/StrategyIsolationTest.java,app.architecture,StrategyIsolationTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/architecture/StrategyIsolationTest.java,New in Enterprise presentation Plan 4b; faces never reference the kit or its variants and the reconciler references no face",
    "jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java,app.architecture,VariantReadTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java,New in Enterprise presentation Plan 4b; ControlVariants is read only in the kit package; the presentation package and KitFaces",
]
for row in rows:
    if len(row.split(",")) != 8: raise SystemExit("a migration row needs eight fields: " + row)
path.write_text(text.rstrip("\n") + "\n" + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicTogglesTest' --tests 'dev.jasper.ui.swing.PaintSpecTest'`
Expected: FAIL at compile: "cannot find symbol … method faces()", "class KitFaces", "class DefaultToggleFace" and
"method capture".

- [ ] **Step 3: Add the faces, the seam and the fixture helper**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/Face.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.util.List;

/**
 * A control point's strategy (Enterprise presentation spec 6.4), built once per kit by {@link KitFaces#of}: it draws
 * what its control asks, from the arguments it is given, and never reads the kit.
 */
interface Face {
    /** The (slot, against, guard) pairs this face paints, each held by its guard where it is painted (spec 7.3). */
    List<PaintPair> pairs();
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.PaintPair;
import java.util.List;
import java.util.Objects;

/**
 * The faces a kit's controls draw with (Enterprise presentation spec 6.4): one implementation per control point, chosen
 * once from the kit's {@link ControlVariants}. Outside the kit package it is the only reader of the variants; the faces
 * themselves never see the kit.
 */
public final class KitFaces {
    final ToggleFace toggle;

    private KitFaces(ControlVariants controls, ColorSlots colors) {
        toggle = switch (controls.toggle()) {
            case SWITCH -> new DefaultToggleFace();
            case CHECKBOX -> new ClassicToggleFace(colors.dialogSurface());
        };
    }

    /** The faces {@code kit} draws with. */
    static KitFaces of(Kit kit) {
        Objects.requireNonNull(kit, "kit");
        return new KitFaces(kit.controls(), kit.colors());
    }

    /** Every face, in spec 5.2's order of control points. */
    List<Face> all() { return List.of(toggle); }

    /**
     * Every pair {@code kit}'s faces paint, in their order, for the painted-contrast suite.
     *
     * @param kit the kit whose faces are asked
     * @return the declared pairs
     */
    public static List<PaintPair> declared(Kit kit) {
        return of(kit).all().stream().flatMap(face -> face.pairs().stream()).toList();
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicGlyphs.java`:

```java
package dev.jasper.ui.swing;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** The classic look's drawn glyphs (DESIGN_LANGUAGE §6): a check mark, a dash and filled triangles, in a theme colour. */
final class ClassicGlyphs {
    private ClassicGlyphs() {}

    /** A check mark filling the {@code size} square at ({@code x}, {@code y}). */
    static void check(Graphics2D g, double x, double y, double size, Color color) {
        double u = size / 13;
        var path = new Path2D.Double();
        path.moveTo(x + 3.25 * u, y + 6.75 * u); path.lineTo(x + 5.5 * u, y + 9 * u); path.lineTo(x + 9.75 * u, y + 4.25 * u);
        stroke(g, path, 1.5 * u, color);
    }

    /** A dash across the {@code size} square at ({@code x}, {@code y}): a box whose state is not yet confirmed. */
    static void dash(Graphics2D g, double x, double y, double size, Color color) {
        double u = size / 13;
        stroke(g, new Line2D.Double(x + 3.5 * u, y + 6.5 * u, x + 9.5 * u, y + 6.5 * u), 1.5 * u, color);
    }

    /** A triangle {@code width} wide pointing down, centred on {@code cx} with its top at {@code y}; {@code width} × 0.8 tall. */
    static void down(Graphics2D g, double cx, double y, double width, Color color) {
        fill(g, color, cx - width / 2, y, cx + width / 2, y, cx, y + width * .8);
    }

    /** A triangle {@code width} wide pointing up, centred on {@code cx} with its top at {@code y}. */
    static void up(Graphics2D g, double cx, double y, double width, Color color) {
        fill(g, color, cx - width / 2, y + width * .8, cx + width / 2, y + width * .8, cx, y);
    }

    /** A triangle {@code width} tall pointing right, its left edge at {@code x}, centred on {@code cy}. */
    static void right(Graphics2D g, double x, double cy, double width, Color color) {
        fill(g, color, x, cy - width / 2, x, cy + width / 2, x + width * .8, cy);
    }

    private static void fill(Graphics2D g, Color color, double x1, double y1, double x2, double y2, double x3, double y3) {
        var path = new Path2D.Double();
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.closePath();
        var s = (Graphics2D) g.create();
        try { s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); s.setColor(color); s.fill(path); }
        finally { s.dispose(); }
    }

    private static void stroke(Graphics2D g, Shape shape, double width, Color color) {
        var s = (Graphics2D) g.create();
        try {
            s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            s.setColor(color); s.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); s.draw(shape);
        } finally { s.dispose(); }
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ToggleFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Dimension;
import java.awt.Graphics2D;

/** The toggle point (spec 5.2): a switch trailing its setting row, or a checkbox leading its label. */
interface ToggleFace extends Face {
    /** The control's size, its focus reservation included. */
    Dimension preferredSize(Switches.SwitchButton button);
    /** Paints the control in its own bounds. */
    void paint(Switches.SwitchButton button, Graphics2D g);
    /** Whether the control leads its title in a setting row; otherwise it sits at the row's end. */
    boolean leads();
    /** Whether a change of state moves the face (the knob's travel) while motion is on. */
    boolean moves();
    /** The control's height in a list row, without its focus reservation. */
    int rowHeight(KitContext context, boolean compact);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultToggleFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

/** The default toggle: the switch, trailing its row, its knob travelling while motion is on. */
final class DefaultToggleFace implements ToggleFace {
    @Override public Dimension preferredSize(Switches.SwitchButton button) { return button.switchSize(); }
    @Override public void paint(Switches.SwitchButton button, Graphics2D g) { button.paintSwitch(g); }
    @Override public boolean leads() { return false; }
    @Override public boolean moves() { return true; }
    @Override public int rowHeight(KitContext context, boolean compact) {
        return ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT : DesignMetrics.Role.SWITCH_HEIGHT);
    }
    /** The off switch is painted as authored; the default toggle repairs nothing (spec 7.3). */
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicToggleFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The classic toggle (spec 5.2, DESIGN_LANGUAGE §6): a 13 px box leading its label. Checked, the accent inside an
 * {@code accent.edge} edge with an on-accent check; unchecked, the input surface inside a {@code border.hover} edge;
 * disabled, flat {@code surface.tableHeader} inside {@code border.divider}; on a selected row, an outline in the row's
 * ink. It never animates.
 */
final class ClassicToggleFace implements ToggleFace {
    private final PaintPair checkedEdge, mark, edge;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicToggleFace(SemanticColor surface) {
        checkedEdge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.INDICATOR);
        mark = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.INDICATOR);
        edge = new PaintPair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.CONTROL);
    }

    @Override public List<PaintPair> pairs() { return List.of(checkedEdge, mark, edge); }
    @Override public boolean leads() { return true; }
    @Override public boolean moves() { return false; }
    @Override public int rowHeight(KitContext context, boolean compact) { return ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX); }

    @Override public Dimension preferredSize(Switches.SwitchButton button) {
        int ring = ThemePainting.metric(button.context, DesignMetrics.Role.FOCUS_RING), box = ThemePainting.metric(button.context, DesignMetrics.Role.CHECKBOX_BOX);
        return new Dimension(box + 2 * ring, box + 2 * ring);
    }

    @Override public void paint(Switches.SwitchButton button, Graphics2D g) {
        var c = button.context;
        ThemePainting.antialias(g);
        int box = ThemePainting.metric(c, DesignMetrics.Role.CHECKBOX_BOX), ring = ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
        int radius = ThemePainting.metric(c, DesignMetrics.Role.CHECKBOX_RADIUS), line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        int x = (button.getWidth() - box) / 2, y = (button.getHeight() - box) / 2;
        boolean enabled = button.isEnabled(), on = button.isSelected() || button.pending;
        if (enabled && (button.error || button.focus != null && button.focus.visible())) {
            g.setColor(ColorMath.opacity(ThemePainting.color(c, button.error ? SemanticColor.BORDER_ERROR : SemanticColor.BORDER_FOCUS),
                ThemePainting.focusHaloShare(c, button.error)));
            g.fill(new RoundRectangle2D.Double(x - ring, y - ring, box + 2.0 * ring, box + 2.0 * ring, 2.0 * (radius + ring), 2.0 * (radius + ring)));
        }
        Color fill, border, ink;
        if (button.onSelection) { fill = null; border = ink = ThemePainting.color(c, button.ink.text); }
        else if (!enabled) {
            fill = ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER); border = ThemePainting.role(c, DerivedColor.BORDER_DIVIDER);
            ink = ThemePainting.color(c, SemanticColor.TEXT_PLACEHOLDER);
        } else if (on) {
            fill = ThemePainting.color(c, button.pending ? SemanticColor.STATUS_WARNING : SemanticColor.ACCENT);
            border = ThemePainting.painted(c, checkedEdge); ink = ThemePainting.painted(c, mark);
        } else { fill = ThemePainting.color(c, SemanticColor.SURFACE_INPUT); border = ThemePainting.painted(c, edge); ink = null; }
        // The edge is the box minus its inside, and the fill the inside: both stay crisp at every scale.
        var outer = new RoundRectangle2D.Double(x, y, box, box, 2.0 * radius, 2.0 * radius);
        var inner = new RoundRectangle2D.Double(x + line, y + line, box - 2.0 * line, box - 2.0 * line,
            2.0 * Math.max(0, radius - line), 2.0 * Math.max(0, radius - line));
        var ring2 = new Area(outer); ring2.subtract(new Area(inner));
        g.setColor(border); g.fill(ring2);
        if (fill != null) { g.setColor(fill); g.fill(inner); }
        if (on && ink != null) {
            if (button.pending) ClassicGlyphs.dash(g, x, y, box, ink);
            else ClassicGlyphs.check(g, x, y, box, ink);
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 1 step 3: the context builds the faces; the switch, setting rows and list rows ask the toggle face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitContext.java", [
    ('''    private final Function<String, String> shortcuts;
''', '''    private final Function<String, String> shortcuts;
    private final KitFaces faces;
''', 1),
    ('''        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
    }''', '''        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
        this.faces = KitFaces.of(kit);
    }''', 1),
    ('''    /** The kit this context renders with. */
    public Kit kit() { return kit; }''', '''    /** The kit this context renders with. */
    public Kit kit() { return kit; }
    /** The faces the kit's controls draw with, chosen once for this context. */
    KitFaces faces() { return faces; }''', 1)])
edit(S + "ThemePainting.java", [
    ('''    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }''',
     '''    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }
    /** {@code role}'s colour in the context's theme: a semantic role or a derived one. */
    static Color role(KitContext context, ColorRole role) { return role.color(context.theme()); }
    /** {@code pair}'s slot as its guard paints it in the context's theme (spec 7.3). */
    static Color painted(KitContext context, PaintPair pair) { return SurfaceContrast.painted(context.theme(), pair); }''', 1)])
edit(S + "Switches.java", [
    ('''                if (view.motion().enabled()) {
                    if (effect != null) try { effect.close(); } catch (Exception failure) { view.report(failure); }''',
     '''                if (view.motion().enabled() && button.face.moves()) {
                    if (effect != null) try { effect.close(); } catch (Exception failure) { view.report(failure); }''', 1),
    ('''        InteractiveRows.Ink ink = InteractiveRows.Ink.SELECTION;
        SwitchButton(KitContext context) { this.context = context; }''',
     '''        InteractiveRows.Ink ink = InteractiveRows.Ink.SELECTION;
        /** The kit's toggle face, fixed for the button's life. */
        final ToggleFace face;
        SwitchButton(KitContext context) { this.context = context; face = context.faces().toggle; }''', 1),
    ('''        @Override public Dimension getPreferredSize() {
            int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);''',
     '''        @Override public Dimension getPreferredSize() { return face.preferredSize(this); }
        /** The switch's size, its focus reservation included (the default face). */
        Dimension switchSize() {
            int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);''', 1),
    ('''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));''',
     '''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try { face.paint(this, g); } finally { g.dispose(); }
        }
        /** Paints the switch: its track, knob and halo (the default face). */
        void paintSwitch(Graphics2D g) {
            {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));''', 1),
    ('''                g.setColor(ColorMath.mix(knobOn, knobOff, position)); g.fillOval(knobX, y+inset, knob, knob);
            } finally { g.dispose(); }
        }''',
     '''                g.setColor(ColorMath.mix(knobOn, knobOff, position)); g.fillOval(knobX, y+inset, knob, knob);
            }
        }''', 1)])
edit(S + "ItemLists.java", [
    ('''                case Nodes.Toggle toggle ->
                        ThemePainting.metric(
                                context(),
                                toggle.compact()
                                        ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT
                                        : DesignMetrics.Role.SWITCH_HEIGHT);''',
     '''                case Nodes.Toggle toggle -> context().faces().toggle.rowHeight(context(), toggle.compact());''', 1)])
edit(S + "SettingRows.java", [
    ('''        @Override public Dimension minimumLayoutSize(Container parent) {
            return owner.settings ? new Dimension(0, 0)''',
     '''        /** The control leads the title (a classic checkbox): the box at the row's leading inset, the text after it. */
        boolean leading(Container p) {
            return p.getComponentCount() > 2 && p.getComponent(2) instanceof Switches.SwitchButton button && button.face.leads();
        }

        /** How far the title starts after the leading inset: the box and its gap to the label. */
        int lead(Container p) {
            int ring = ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING);
            return p.getComponent(2).getPreferredSize().width - 2 * ring + ThemePainting.metric(owner.context(), DesignMetrics.Role.CHECKBOX_LABEL_GAP);
        }

        int leadingText(int width) {
            return owner.title.height(width) + (owner.description.isVisible()
                ? owner.description.height(width) + ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0);
        }

        /** The row's least height, as {@link #height} keeps it for a trailing control. */
        int floor() {
            var c = owner.context();
            return owner.panelFooter ? ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE) + ThemePainting.metric(c, DesignMetrics.Role.PANEL_FOOTER_ROW_HEIGHT)
                : ThemePainting.metric(c, owner.settings ? owner.description.isVisible() ? DesignMetrics.Role.SETTINGS_DESCRIPTION_HEIGHT
                    : owner.grouped ? DesignMetrics.Role.SETTINGS_GROUP_ROW_HEIGHT : DesignMetrics.Role.SETTINGS_ROW_HEIGHT
                    : owner.prompt && owner.description.isVisible() ? DesignMetrics.Role.PROMPT_SETTING_HEIGHT
                    : owner.description.isVisible() ? owner.fullForm ? DesignMetrics.Role.FULL_SETTING_DESCRIPTION_HEIGHT : DesignMetrics.Role.SETTING_DESCRIPTION_HEIGHT
                    : DesignMetrics.Role.SETTING_HEIGHT) + (owner.fullForm && !owner.prompt ? ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE) : 0);
        }

        int leadingHeight(Container p, int width) {
            Insets i = p.getInsets();
            int ring = ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING);
            int text = leadingText(Math.max(0, width - i.left - i.right - lead(p)));
            int box = p.getComponent(2).getPreferredSize().height - 2 * ring;
            return Math.max(floor(), i.top + i.bottom + Math.max(text, box));
        }

        /** The box centres on the title's first line, its focus ring in the row's padding; the description sits under the title. */
        void layoutLeading(Container p) {
            Insets i = p.getInsets();
            int ring = ThemePainting.metric(owner.context(), DesignMetrics.Role.FOCUS_RING), lead = lead(p);
            int w = Math.max(0, p.getWidth() - i.left - i.right - lead), text = leadingText(w);
            var control = p.getComponent(2); var size = control.getPreferredSize();
            int box = size.height - 2 * ring, y = Math.max(i.top, (p.getHeight() - Math.max(text, box)) / 2), th = owner.title.height(w);
            owner.title.setBounds(i.left + lead, y, w, th);
            int gap = owner.description.isVisible() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0;
            owner.description.setBounds(i.left + lead, y + th + gap, w, owner.description.isVisible() ? owner.description.height(w) : 0);
            int line = Math.min(th, owner.title.getFontMetrics(owner.title.getFont()).getHeight());
            control.setBounds(i.left - ring, y + (line - box) / 2 - ring, size.width, size.height);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return owner.settings ? new Dimension(0, 0)''', 1),
    ('''        @Override
        public int height(Container p, int width) {
            Insets i = p.getInsets();''',
     '''        @Override
        public int height(Container p, int width) {
            if (leading(p)) return leadingHeight(p, width);
            Insets i = p.getInsets();''', 1),
    ('''        @Override
        public Dimension preferredLayoutSize(Container p) {
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;''',
     '''        @Override
        public Dimension preferredLayoutSize(Container p) {
            if (leading(p)) {
                int w = p.getInsets().left + p.getInsets().right + lead(p)
                    + Math.max(owner.title.getPreferredSize().width, owner.description.isVisible() ? owner.description.getPreferredSize().width : 0);
                return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
            }
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;''', 1),
    ('''        @Override
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();''',
     '''        @Override
        public void layoutContainer(Container p) {
            if (leading(p)) { layoutLeading(p); return; }
            Insets i = p.getInsets();''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''    /** Headless paint with layout settling; the view and context remain owned by the caller. */''',
     '''    /**
     * {@code spec} rendered at 1x in {@code context} and written to {@code build/classic-faces/<name>.png} (relative to the
     * test's working directory) for inspection; the view is closed, the context stays the caller's. EDT.
     */
    public static java.awt.image.BufferedImage capture(KitContext context, dev.jasper.ui.model.ViewSpec spec, int width, int height, String name) {
        try (var view = new dev.jasper.ui.swing.KitView(context, spec, event -> {})) {
            var image = render(view, width, height, 1);
            var out = java.nio.file.Path.of("build", "classic-faces", name + ".png");
            java.nio.file.Files.createDirectories(out.getParent());
            javax.imageio.ImageIO.write(image, "png", out.toFile());
            return image;
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Headless paint with layout settling; the view and context remain owned by the caller. */''', 1)])
PY
```

`SwitchButton.paintSwitch` keeps the former body in a block (`{ … }`) so its lines are unchanged; the outer
`paintComponent` owns the graphics copy, as before.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --continue && ./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.PaintedContrastTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`ClassicTogglesTest` 5 tests, `PaintSpecTest` 3, `StrategyIsolationTest` 2, `VariantReadTest` 2; every
existing swing test, `SwitchesTest` and the settings suites included, unchanged).

- [ ] **Step 5: Prove the default gallery unchanged**

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
"""The headless gallery matches the plan-4 base: every PNG byte-identical, every JSON identical once the class names of
plan 4's two component substitutions (4b's Cards$SectionSurface for a section's JPanel, 4c's ItemLists$ListPane for an
item list's JScrollPane) are read as the classes they replace (pixel rule)."""
import hashlib, pathlib, sys
SUBSTITUTED = {b'"class":"dev.jasper.ui.swing.Cards$SectionSurface"': b'"class":"javax.swing.JPanel"',
               b'"class":"dev.jasper.ui.swing.ItemLists$ListPane"': b'"class":"javax.swing.JScrollPane"'}
def content(path):
    data = path.read_bytes()
    if path.suffix == ".json":
        for new, old in SUBSTITUTED.items(): data = data.replace(new, old)
    return data
def digests(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(content(p)).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan4-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none")
sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`. Open `jasper-ui/build/classic-faces/toggle-checkbox.png` and check by eye that the box leads
the title and the description sits under the title.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTogglesTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/PaintSpecTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv jasper-app/src/test/java/dev/jasper/app/architecture/StrategyIsolationTest.java \
  jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'MSG'
Classic faces: KitFaces and the checkbox

KitFaces chooses one face per control point from the kit's variants; KitContext
builds it once. The toggle point's default face is the switch (its code moved
into SwitchButton.paintSwitch); the classic face is a 13 px checkbox leading its
label, painted through its declared guards, with no motion. StrategyIsolationTest,
VariantReadTest and PaintSpecTest (face-pairs.tsv) guard the faces.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 2: The buttons and the text-field policy

The buttons point's two faces and the field policy `KitFaces` builds with them (spec 6.4, "Classic button and field
details"). The default face is today's raised button, dropdown and picker faces; the classic face draws the
top-to-bottom gradient from `button.secondary.bg` to `button.end` inside a guarded `button.secondary.border` edge that
darkens to `border.hover` on hover, the primary filled with the accent inside `accent.edge` with a medium label and no
inset line or drop, a flat disabled face (Rulings 6), flat ghosts in toolbars and rows (Rulings 7), dropdown and picker
faces like buttons ending with a drawn ▼, picker rows without tiles (Rulings 9), and fields without leading glyphs and
with a grey read-only face (Rulings 10).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/ButtonFace.java`, `DefaultButtonFace.java`, `ClassicButtonFace.java`, `FieldPolicy.java`, `DefaultFieldPolicy.java`, `ClassicFieldPolicy.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Buttons.java:73,203-210,322-350`, `Choices.java:248-258,321,364-375,439-442`, `PickerMenus.java:23-55,74,111,141-150`, `TextFields.java:276,287,316,397,449-456`, `Stacks.java:21,95`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicButtonsTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (rows appended)

**Interfaces:**
- Consumes: Task 1's `Face`, `KitFaces`, `ThemePainting.role` and `painted`, `ClassicGlyphs.down`; 3b's `Nodes.Field`
  13-argument constructor with `readOnly()` and `PickerFace.technicalCaption()` with the 13-parameter `PickerMenus.line`.
- Produces: `final ButtonFace KitFaces.buttons`, `final FieldPolicy KitFaces.fields`; `interface ButtonFace extends Face
  { void paintFace(Buttons.KitButton, Graphics2D, boolean hover, boolean focused); Color text(Buttons.KitButton, boolean
  hover); boolean fades(); Typography.Role label(Nodes.ButtonKind, Typography.Role); void paintCombo(Choices.ChoiceCombo,
  Graphics2D, boolean hover, boolean focused); void paintArrow(Choices.ChoiceCombo combo, JComponent arrow, Graphics2D,
  int beyond, boolean settings); void paintPickerArrow(Choices.PickerButton, Graphics2D); void paintPicker(JComponent, Graphics,
  KitContext, Nodes.PickerFace, boolean empty, boolean focus, boolean hover, boolean expanded); boolean tiles(); Color
  menuDivider(KitContext); }`; `interface FieldPolicy extends Face { boolean glyph(Nodes.FieldKind); void
  paintField(TextFields.FieldSurface, Graphics2D); Color readOnlyText(KitContext); }`; on `Buttons.KitButton`:
  `KitContext context()`, `void paintRaised(Graphics2D, boolean hover, boolean focused)`, the overridable
  `void paintKitFace(Graphics2D, boolean, boolean)` and `boolean fadesDisabled()`; `static void Choices.paintChevron(JComponent,
  Graphics2D, KitContext, int, boolean, SemanticColor)`; `SemanticColor Choices.ChoiceCombo.arrowRole()` (3c's bare
  cell ink, else `text.secondary`); `static void ClassicButtonFace.down(JComponent, Graphics2D, KitContext, int beyond,
  Color)`; `void Choices.PickerButton.paintChevron(Graphics2D)`; `static void
  PickerMenus.paintRichFace(…)` (the former eight-argument `paintFace` body) and a package-private `PickerMenus.line`;
  `boolean TextFields.FieldSurface.readOnly` and `void FieldSurface.paintDefault(Graphics2D)`;
  `Nodes.LayoutRole Stacks.StackSurface.role`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicButtonsTest.java`:

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

/** Spec 5.2's buttons point and 6.4's classic button and field details. */
@SuppressWarnings("try")
class ClassicButtonsTest {
    static final ControlState DISABLED = new ControlState(true, false, false, "");

    static Nodes.Button button(String id, Nodes.ButtonKind kind, ControlState state) {
        return new Nodes.Button(id, state, "Choose…", "", kind, id, "");
    }

    static ViewSpec stack(Nodes.LayoutRole role, Nodes.Node... children) {
        return new ViewSpec(new Nodes.Stack("stack", ControlState.READY, role, List.of(children)));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    static boolean near(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) <= 6 && Math.abs(a.getGreen() - b.getGreen()) <= 6 && Math.abs(a.getBlue() - b.getBlue()) <= 6;
    }

    /** The guarded edge and label over every palette; the fill runs from button.secondary.bg at the top to button.end. */
    @Test void aStandardButtonIsAGradientInsideItsGuardedEdge() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("b", Nodes.ButtonKind.SECONDARY, ControlState.READY)), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var b = find(view, "b", Buttons.KitButton.class);
                var image = paint(b);
                int h = b.getHeight();
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(at(image, 0, h / 2)).as(where + " edge").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                assertThat(b.getForeground()).as(where + " label").isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(near(at(image, 3, 1), ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BG))).as(where + " top").isTrue();
                assertThat(near(at(image, 3, h - 2), ThemePainting.role(context, DerivedColor.BUTTON_END))).as(where + " bottom").isTrue();
            } finally { release(context); }
        });
    }

    @Test void aPrimaryButtonIsTheAccentWithAMediumLabelAndNoDrop() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("ok", Nodes.ButtonKind.PRIMARY, ControlState.READY)), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var b = find(view, "ok", Buttons.KitButton.class);
                var image = paint(b);
                assertThat(at(image, 3, b.getHeight() / 2)).isEqualTo(ThemePainting.color(context, SemanticColor.ACCENT));
                assertThat(at(image, 0, b.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, pairs.get(3)));
                assertThat(b.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(b.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY_STRONG));
                assertThat(b.getClientProperty(ThemePainting.DROP)).as("Review Focus 2: no drop").isNull();
            } finally { release(context); }
        });
    }

    /** Review Focus 2: a disabled button is a flat fill, a lighter edge and the label at 0.42 alpha, not a faded face. */
    @Test void aDisabledButtonIsFlatWithFadedText() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("remove", Nodes.ButtonKind.SECONDARY, DISABLED),
                    button("add", Nodes.ButtonKind.PRIMARY, DISABLED)), e -> {})) {
                KitFixtures.capture(context, view.spec(), 300, 60, "buttons-disabled");
                KitFixtures.render(view, 300, 60, 1);
                var remove = find(view, "remove", Buttons.KitButton.class);
                var add = find(view, "add", Buttons.KitButton.class);
                assertThat(at(paint(remove), 3, remove.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(at(paint(add), 3, add.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_HEADER));
                assertThat(at(paint(remove), 0, remove.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                assertThat(remove.getForeground().getAlpha()).isEqualTo(Math.round(.42f * 255));
            } finally { release(context); }
        });
    }

    /** Rulings 7: a ghost in a toolbar row is flat until hovered; among actions it draws as a standard button. */
    @Test void aGhostIsFlatInARowAndStandardAmongActions() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var inline = new KitView(context, stack(Nodes.LayoutRole.INLINE, button("ghost", Nodes.ButtonKind.GHOST, ControlState.READY)), e -> {});
                 var actions = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("ghost", Nodes.ButtonKind.GHOST, ControlState.READY)), e -> {})) {
                KitFixtures.render(inline, 300, 60, 1); KitFixtures.render(actions, 300, 60, 1);
                var flat = find(inline, "ghost", Buttons.KitButton.class);
                assertThat(paint(flat).getRGB(3, flat.getHeight() / 2) >>> 24).as("flat at rest").isZero();
                flat.getModel().setRollover(true);
                assertThat(at(paint(flat), 3, flat.getHeight() / 2)).isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL_HOVER));
                var standard = find(actions, "ghost", Buttons.KitButton.class);
                assertThat(near(at(paint(standard), 3, 1), ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BG))).isTrue();
            } finally { release(context); }
        });
    }

    @Test void aDropdownFollowsTheButtonsAndEndsWithItsDrawnArrow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            var choice = new Nodes.Choice("reachable", ControlState.READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("local", "This computer only",
                Optional.empty()), new Option("lan", "Local network", Optional.empty())), "local", "Reachable from", "reachable");
            try (var view = new KitView(context, new ViewSpec(choice), e -> {})) {
                KitFixtures.capture(context, view.spec(), 300, 40, "dropdown");
                KitFixtures.render(view, 300, 40, 1);
                var combo = find(view, "reachable.input", Choices.ChoiceCombo.class);
                var image = paint(combo);
                assertThat(at(image, 0, combo.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var arrow = ThemePainting.painted(context, pairs.get(5)); boolean found = false;
                for (int y = 0; y < image.getHeight(); y++) for (int x = image.getWidth() - 20; x < image.getWidth(); x++)
                    found |= at(image, x, y).equals(arrow);
                assertThat(found).as("the ▼ in text.secondary").isTrue();
            } finally { release(context); }
        });
    }

    /** Rulings 10: SEARCH, PATH and PORT fields lose their leading glyph; a read-only field is grey with muted text. */
    @Test void fieldsLoseTheirLeadingGlyphAndAReadOnlyFieldIsGrey() {
        Edt.run(() -> {
            var classic = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var standard = KitFixtures.context(KitFixtures.light());
            var search = new Nodes.Field("search", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search", "Search", "", Optional.empty());
            var folder = new Nodes.Field("folder", ControlState.READY, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(),
                Nodes.FieldPresentation.STANDARD, "", "", "", true);
            try (var view = new KitView(classic, new ViewSpec(new Nodes.Stack("fields", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(search, folder))), e -> {});
                 var plain = new KitView(standard, new ViewSpec(search), e -> {})) {
                KitFixtures.render(view, 300, 120, 1); KitFixtures.render(plain, 300, 40, 1);
                assertThat(((TextFields.FieldControl) view.control("search")).leading.isVisible()).isFalse();
                assertThat(((TextFields.FieldControl) plain.control("search")).leading.isVisible()).isTrue();
                var control = (TextFields.FieldControl) view.control("folder");
                assertThat(at(paint(control.surface), 3, control.surface.getHeight() / 2)).isEqualTo(ThemePainting.role(classic, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(control.input.getForeground()).isEqualTo(classic.faces().fields.readOnlyText(classic));
            } finally { release(classic); release(standard); }
        });
    }

    /** Plan 3c's selected-row ink survives the faces: a bare cell dropdown's arrow takes the row's supporting ink under both kits. */
    @Test void aBareCellDropdownsArrowTakesTheSelectedRowsInkUnderBothFaces() {
        for (var kit : List.of(dev.jasper.ui.kit.Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.dark(), kit);
            try (var view = new KitView(context, new ViewSpec(TableVocabularyTest.groups(Set.of("errors"))), e -> {})) {
                view.setSize(640, 300); InteractiveRowsTest.layout(view);
                var groups = (Tables.TableControl) view.control("groups");
                var selected = TableVocabularyTest.combo(groups, "errors");
                assertThat(selected.bare()).as("precondition: a bare cell dropdown").isTrue();
                assertThat(selected.arrowRole()).as(kit.id()).isEqualTo(groups.ink().supporting);
                assertThat(TableVocabularyTest.combo(groups, "config").arrowRole()).isEqualTo(SemanticColor.TEXT_SECONDARY);
                var arrow = Arrays.stream(selected.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast).findFirst().orElseThrow();
                int beyond = selected.getWidth() - arrow.getX() - arrow.getWidth();
                var expected = new BufferedImage(arrow.getWidth(), arrow.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = expected.createGraphics();
                try {
                    if (kit == dev.jasper.ui.kit.Kit.DEFAULT) Choices.paintChevron(arrow, g, context, beyond, false, groups.ink().supporting);
                    else ClassicButtonFace.down(arrow, g, context, beyond, ThemePainting.color(context, groups.ink().supporting));
                } finally { g.dispose(); }
                var actual = paint(arrow);
                assertThat(actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth())).as(kit.id())
                    .isEqualTo(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()));
            } finally { release(context); }
        });
    }

    @Test void pickerRowsDrawNoTilesUnderTheClassicFace() {
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.CLASSIC).buttons.tiles()).isFalse();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.DEFAULT).buttons.tiles()).isTrue();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.CLASSIC).fields.glyph(Nodes.FieldKind.TEXT)).isFalse();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.DEFAULT).fields.glyph(Nodes.FieldKind.PATH)).isTrue();
    }
}
```

Append the classic button and field rows to `face-pairs.tsv` (they follow the toggle's, in `KitFaces` order):

```bash
python3 -I - <<'PY'
import pathlib
rows = [("classic", "ClassicButtonFace", "text.onAccent", "accent", "CONTRAST:4.5"),
        ("classic", "ClassicButtonFace", "text.primary", "button.secondary.bg", "CONTRAST:4.5"),
        ("classic", "ClassicButtonFace", "button.secondary.border", "surface.app", "CONTRAST:1.5"),
        ("classic", "ClassicButtonFace", "accent.edge", "surface.app", "CONTRAST:1.5"),
        ("classic", "ClassicButtonFace", "border.hover", "surface.app", "CONTRAST:1.5"),
        ("classic", "ClassicButtonFace", "text.secondary", "button.secondary.bg", "CONTRAST:3.0"),
        ("classic", "ClassicFieldPolicy", "border.divider", "surface.app", "CONTRAST:1.3"),
        ("classic", "ClassicFieldPolicy", "text.secondary", "surface.tableHeader", "CONTRAST:4.5")]
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
path.write_text(path.read_text(encoding="utf-8") + "".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicButtonsTest'`
Expected: FAIL at compile: "cannot find symbol … variable buttons" (and `fields`) in `KitFaces`.

- [ ] **Step 3: Add the faces and route the controls through them**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ButtonFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** The buttons point (spec 5.2): buttons' faces, and the dropdown and picker faces that follow them. */
interface ButtonFace extends Face {
    /** Paints the face behind a button's glyph and label: its fill and border (and the default primary's inset line and drop). */
    void paintFace(Buttons.KitButton button, Graphics2D g, boolean hover, boolean focused);
    /** The button's label colour. */
    Color text(Buttons.KitButton button, boolean hover);
    /** Whether a disabled button is its whole face faded as one layer; otherwise the face draws its own disabled state. */
    boolean fades();
    /** The label's type role for a {@code kind} button whose label would take {@code role}. */
    Typography.Role label(Nodes.ButtonKind kind, Typography.Role role);
    /** Paints a dropdown's closed face behind its value. */
    void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused);
    /**
     * Paints {@code combo}'s arrow in {@code arrow}, its arrow button, whose trailing edge lies {@code beyond} inside the
     * combo's; a bare cell dropdown on a selected row (plan 3c) takes that row's ink.
     */
    void paintArrow(Choices.ChoiceCombo combo, JComponent arrow, Graphics2D g, int beyond, boolean settings);
    /** Paints a plain picker button's trailing arrow. */
    void paintPickerArrow(Choices.PickerButton picker, Graphics2D g);
    /** Paints a rich picker or dropdown face. */
    void paintPicker(JComponent owner, Graphics graphics, KitContext context, Nodes.PickerFace face, boolean empty, boolean focus,
                     boolean hover, boolean expanded);
    /** Whether picker menu rows draw their icon tiles. */
    boolean tiles();
    /** The rule above a picker menu's footer row. */
    Color menuDivider(KitContext context);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultButtonFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JComponent;

/** The default buttons: raised faces with the primary's inset line and drop, rounded dropdowns with their chevron. */
final class DefaultButtonFace implements ButtonFace {
    @Override public void paintFace(Buttons.KitButton button, Graphics2D g, boolean hover, boolean focused) { button.paintRaised(g, hover, focused); }
    @Override public Color text(Buttons.KitButton button, boolean hover) { return ThemePainting.color(button.context(), button.textRole(hover)); }
    @Override public boolean fades() { return true; }
    @Override public Typography.Role label(Nodes.ButtonKind kind, Typography.Role role) { return role; }
    @Override public void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused) {
        ThemePainting.rounded(g, combo, combo.context, ThemePainting.color(combo.context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL),
            ThemePainting.color(combo.context, SemanticColor.BORDER_CONTROL), focused, false);
    }
    @Override public void paintArrow(Choices.ChoiceCombo combo, JComponent arrow, Graphics2D g, int beyond, boolean settings) {
        Choices.paintChevron(arrow, g, combo.context, beyond, settings, combo.arrowRole());
    }
    @Override public void paintPickerArrow(Choices.PickerButton picker, Graphics2D g) { picker.paintChevron(g); }
    @Override public void paintPicker(JComponent owner, Graphics graphics, KitContext context, Nodes.PickerFace face, boolean empty, boolean focus,
                                      boolean hover, boolean expanded) {
        PickerMenus.paintRichFace(owner, graphics, context, face, empty, focus, hover, expanded);
    }
    @Override public boolean tiles() { return true; }
    @Override public Color menuDivider(KitContext context) { return ThemePainting.color(context, SemanticColor.BORDER_MENU); }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicButtonFace.java`:

```java
package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The classic buttons (spec 6.4, DESIGN_LANGUAGE §6): a top-to-bottom gradient from {@code button.secondary.bg} to
 * {@code button.end} inside a {@code button.secondary.border} edge that darkens to {@code border.hover} on hover; the
 * primary filled with the accent inside {@code accent.edge}, its label medium, with no inset line or drop; a disabled
 * face flat; ghosts flat until hovered where a toolbar holds them. Dropdowns and picker faces follow, ending with a ▼.
 */
final class ClassicButtonFace implements ButtonFace {
    private static final float DISABLED = .42f;
    private final PaintPair primaryLabel, label, edge, primaryEdge, hoverEdge, arrow;

    /** A face whose buttons stand on {@code surface}, the kit's dialog surface. */
    ClassicButtonFace(SemanticColor surface) {
        primaryLabel = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
        label = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.TEXT);
        edge = new PaintPair(SemanticColor.BUTTON_SECONDARY_BORDER, surface, SurfaceContrast.Guard.CONTROL);
        primaryEdge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.CONTROL);
        hoverEdge = new PaintPair(DerivedColor.BORDER_HOVER, surface, SurfaceContrast.Guard.CONTROL);
        arrow = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.INDICATOR);
    }

    @Override public List<PaintPair> pairs() { return List.of(primaryLabel, label, edge, primaryEdge, hoverEdge, arrow); }

    /** Rulings 7: flat until hovered in toolbars, rows, panel headers and contextual slots; a standard button elsewhere. */
    static boolean flat(Buttons.KitButton b) {
        return b.panel || b.panelRow || b.contextual || b.orderedCommand
            || SwingUtilities.getAncestorOfClass(InteractiveRows.Row.class, b) != null
            || b.getParent() instanceof Stacks.StackSurface stack && (stack.role == Nodes.LayoutRole.INLINE || stack.role == Nodes.LayoutRole.NAVIGATION);
    }

    private static boolean ghost(Buttons.KitButton b) { return b.kind == Nodes.ButtonKind.GHOST || b.kind == Nodes.ButtonKind.DESTRUCTIVE_GHOST; }

    /** The standard fill in {@code face}: {@code button.secondary.bg} at the top to {@code button.end} at the bottom, reversed while pressed. */
    private static void gradient(Graphics2D g, KitContext c, Shape face, boolean pressed) {
        Color top = ThemePainting.color(c, SemanticColor.BUTTON_SECONDARY_BG), bottom = ThemePainting.role(c, DerivedColor.BUTTON_END);
        var bounds = face.getBounds2D();
        g.setPaint(new GradientPaint(0, (float) bounds.getMinY(), pressed ? bottom : top, 0, (float) bounds.getMaxY(), pressed ? top : bottom));
        g.fill(face);
    }

    @Override public void paintFace(Buttons.KitButton b, Graphics2D g, boolean hover, boolean focused) {
        if (b.embedded || b.recorder() || b.circular || b.kind == Nodes.ButtonKind.LINK) { b.paintRaised(g, hover, focused); return; }
        var c = b.context();
        boolean flat = ghost(b) && flat(b), selected = b.toggle && b.isSelected(), pressed = b.isEnabled() && b.getModel().isPressed();
        if (flat && !hover && !selected) { ThemePainting.rounded(g, b, c, null, null, focused, false); return; }
        ThemePainting.antialias(g);
        int inset = b.panelDetail ? ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING) : 0;
        double trim = b.panelDetail ? UIScale.scale((float) c.metrics().value(DesignMetrics.Role.PANEL_DETAIL_ACTION_BOTTOM_TRIM)) : 0;
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
        var face = new RoundRectangle2D.Double(inset, inset, b.getWidth() - 2.0 * inset, b.getHeight() - trim - 2.0 * inset, 2 * radius, 2 * radius);
        boolean primary = b.kind == Nodes.ButtonKind.PRIMARY;
        Color border;
        if (!b.isEnabled()) {
            g.setColor(ThemePainting.role(c, primary ? DerivedColor.SURFACE_HEADER : DerivedColor.SURFACE_TABLE_HEADER)); g.fill(face);
            border = ThemePainting.role(c, DerivedColor.BORDER_DIVIDER);
        } else if (selected || flat) {
            g.setColor(ThemePainting.color(c, selected ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SURFACE_CONTROL_HOVER)); g.fill(face);
            border = ThemePainting.painted(c, hoverEdge);
        } else if (primary) {
            g.setColor(ThemePainting.color(c, hover || pressed ? SemanticColor.ACCENT_HOVER : SemanticColor.ACCENT)); g.fill(face);
            border = ThemePainting.painted(c, primaryEdge);
        } else {
            gradient(g, c, face, pressed);
            border = ThemePainting.painted(c, hover ? hoverEdge : edge);
        }
        ThemePainting.rounded(g, b, c, null, border, focused, false, radius, inset, trim);
    }

    @Override public Color text(Buttons.KitButton b, boolean hover) {
        var c = b.context();
        if (b.embedded || b.recorder() || b.circular || b.onSelection || b.kind == Nodes.ButtonKind.LINK) return ThemePainting.color(c, b.textRole(hover));
        if (!b.isEnabled()) return ColorMath.opacity(ThemePainting.color(c, SemanticColor.TEXT_PRIMARY), DISABLED);
        if (ghost(b) && flat(b)) return ThemePainting.color(c, b.textRole(hover));
        if (b.kind == Nodes.ButtonKind.PRIMARY) return ThemePainting.painted(c, primaryLabel);
        var role = b.textRole(hover);
        return role == SemanticColor.TEXT_STRONG_SECONDARY || ghost(b) ? ThemePainting.painted(c, label) : ThemePainting.color(c, role);
    }

    @Override public boolean fades() { return false; }

    @Override public Typography.Role label(Nodes.ButtonKind kind, Typography.Role role) {
        return kind == Nodes.ButtonKind.PRIMARY && role == Typography.Role.LABEL ? Typography.Role.BODY_STRONG : role;
    }

    @Override public void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused) {
        var c = combo.context;
        ThemePainting.antialias(g);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
        var face = new RoundRectangle2D.Double(0, 0, combo.getWidth(), combo.getHeight(), 2 * radius, 2 * radius);
        if (combo.isEnabled()) gradient(g, c, face, false);
        else { g.setColor(ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER)); g.fill(face); }
        ThemePainting.rounded(g, combo, c, null, focused ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS)
            : ThemePainting.painted(c, hover ? hoverEdge : edge), focused, false);
    }

    /** The guarded ▼ in text.secondary; a bare cell dropdown on a selected row takes the row's supporting ink, as by default. */
    @Override public void paintArrow(Choices.ChoiceCombo combo, JComponent owner, Graphics2D g, int beyond, boolean settings) {
        var c = combo.context;
        down(owner, g, c, beyond, combo.bare() && combo.cellInk != null ? ThemePainting.color(c, combo.cellInk.supporting) : ThemePainting.painted(c, arrow));
    }

    /** The drawn ▼ in {@code colour}, centred {@code DROPDOWN_GLYPH_INSET} in from a trailing edge {@code beyond} past {@code owner}'s. */
    static void down(JComponent owner, Graphics2D g, KitContext c, int beyond, Color colour) {
        int glyph = ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH);
        double right = owner.getWidth() + beyond - ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH_INSET);
        ClassicGlyphs.down(g, right - glyph / 2.0, (owner.getHeight() - glyph * .8) / 2, glyph, colour);
    }

    @Override public void paintPickerArrow(Choices.PickerButton picker, Graphics2D g) {
        down(picker, g, picker.context, 0, ThemePainting.painted(picker.context, arrow));
    }

    @Override public void paintPicker(JComponent owner, Graphics graphics, KitContext c, Nodes.PickerFace face, boolean empty, boolean focus,
                                      boolean hover, boolean expanded) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            if (!owner.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(c.metrics().value(DesignMetrics.Role.PICKER_DISABLED_PERCENT) / 100f));
            double radius = ThemePainting.metric(c, DesignMetrics.Role.CONTROL_RADIUS);
            gradient(g, c, new RoundRectangle2D.Double(0, 0, owner.getWidth(), owner.getHeight(), 2 * radius, 2 * radius), false);
            ThemePainting.rounded(g, owner, c, null, focus || expanded ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS)
                : ThemePainting.painted(c, hover ? hoverEdge : edge), focus || expanded, false);
            int pad = ThemePainting.metric(c, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING), glyph = ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH);
            int right = owner.getWidth() - ThemePainting.metric(c, DesignMetrics.Role.DROPDOWN_GLYPH_INSET) - glyph;
            ClassicGlyphs.down(g, right + glyph / 2.0, (owner.getHeight() - glyph * .8) / 2, glyph, ThemePainting.painted(c, arrow));
            PickerMenus.line(g, c, empty ? "Choose…" : face.primary(), empty ? "" : face.caption(), pad, 0, Math.max(0, right - 2 * pad),
                owner.getHeight(), false, empty, face.density() == Nodes.PickerDensity.COMPACT, false, face.technicalCaption());
        } finally { g.dispose(); }
    }

    @Override public boolean tiles() { return false; }
    @Override public Color menuDivider(KitContext context) { return ThemePainting.role(context, DerivedColor.BORDER_ROW); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/FieldPolicy.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Color;
import java.awt.Graphics2D;

/** The text-field policy built with the buttons point (spec 6.4): leading glyphs and the field's surface. */
interface FieldPolicy extends Face {
    /** Whether a {@code kind} field shows its leading glyph (a SEARCH or PATH icon, a PORT's colon). */
    boolean glyph(Nodes.FieldKind kind);
    /** Paints a field's surface and outline, read-only or not. */
    void paintField(TextFields.FieldSurface surface, Graphics2D g);
    /** A read-only field's text colour. */
    Color readOnlyText(KitContext context);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultFieldPolicy.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The default fields: leading glyphs, one surface whether read-only or not. */
final class DefaultFieldPolicy implements FieldPolicy {
    @Override public boolean glyph(Nodes.FieldKind kind) { return true; }
    @Override public void paintField(TextFields.FieldSurface surface, Graphics2D g) { surface.paintDefault(g); }
    @Override public Color readOnlyText(KitContext context) { return ThemePainting.color(context, SemanticColor.TEXT_PRIMARY); }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicFieldPolicy.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** The classic fields (spec 6.4, DESIGN_LANGUAGE §6): no leading glyph; a read-only field grey with muted text. */
final class ClassicFieldPolicy implements FieldPolicy {
    private final PaintPair readOnlyEdge, readOnlyLabel;

    /** A policy whose fields stand on {@code surface}, the kit's dialog surface. */
    ClassicFieldPolicy(SemanticColor surface) {
        readOnlyEdge = new PaintPair(DerivedColor.BORDER_DIVIDER, surface, SurfaceContrast.Guard.STRUCTURAL);
        readOnlyLabel = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(readOnlyEdge, readOnlyLabel); }

    /** Rulings 10: no SEARCH or PATH glyph, and no PORT colon (its row gives it a "Port:" label). */
    @Override public boolean glyph(Nodes.FieldKind kind) { return false; }

    @Override public void paintField(TextFields.FieldSurface s, Graphics2D g) {
        if (s.transparent || !s.readOnly) { s.paintDefault(g); return; }
        var c = s.context;
        ThemePainting.rounded(g, s, c, ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER),
            s.focus.visible() ? ThemePainting.color(c, SemanticColor.BORDER_FOCUS) : ThemePainting.painted(c, readOnlyEdge),
            s.focus.visible(), s.error, s.radiusRole);
    }

    @Override public Color readOnlyText(KitContext context) { return ThemePainting.painted(context, readOnlyLabel); }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 2 step 3: buttons, dropdowns, pickers and fields ask the buttons face and the field policy."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final ToggleFace toggle;
''', '''    final ToggleFace toggle;
    final ButtonFace buttons;
    final FieldPolicy fields;
''', 1),
    ('''            case CHECKBOX -> new ClassicToggleFace(colors.dialogSurface());
        };''', '''            case CHECKBOX -> new ClassicToggleFace(colors.dialogSurface());
        };
        // The text-field policy is built with the buttons point (spec 6.4).
        switch (controls.buttons()) {
            case RAISED -> { buttons = new DefaultButtonFace(); fields = new DefaultFieldPolicy(); }
            case CLASSIC -> { buttons = new ClassicButtonFace(colors.dialogSurface()); fields = new ClassicFieldPolicy(colors.dialogSurface()); }
            default -> throw new IllegalStateException("Unknown buttons variant " + controls.buttons());
        }''', 1),
    ('''    List<Face> all() { return List.of(toggle); }''', '''    List<Face> all() { return List.of(toggle, buttons, fields); }''', 1)])
edit(S + "Buttons.java", [
    ('''            button.setText(value.label()); button.setFont(ThemePainting.font(context(), button.hint ? Typography.Role.PALETTE_HINT : button.panelDetail || button.panel || button.panelEmpty ? Typography.Role.PANEL_DETAIL_LABEL : button.orderedAction ? Typography.Role.ORDERED_ACTION : Typography.Role.LABEL));''',
     '''            button.setText(value.label()); button.setFont(ThemePainting.font(context(), context().faces().buttons.label(value.kind(), button.hint ? Typography.Role.PALETTE_HINT : button.panelDetail || button.panel || button.panelEmpty ? Typography.Role.PANEL_DETAIL_LABEL : button.orderedAction ? Typography.Role.ORDERED_ACTION : Typography.Role.LABEL)));''', 1),
    # Post-3b text: 3b's Notice link floor stays ahead of the face under both kits.
    ('''                return SurfaceContrast.painted(context.theme(), Notices.link(noticeGround, hover));
            return ThemePainting.color(context, textRole(hover));
        }''',
     '''                return SurfaceContrast.painted(context.theme(), Notices.link(noticeGround, hover));
            return context.faces().buttons.text(this, hover);
        }
        /** The context this button paints in. */
        KitContext context() { return context; }''', 1),
    ('''            // Disabled is the whole button at disabledOpacity, faded as one layer.
            if (!isEnabled()) { ThemePainting.faded(graphics, this, disabledOpacity, this::paintFace); return; }''',
     '''            // Disabled is the whole button at disabledOpacity, faded as one layer, unless the face draws its own disabled state.
            if (!isEnabled() && fadesDisabled()) { ThemePainting.faded(graphics, this, disabledOpacity, this::paintFace); return; }''', 1),
    ('''            boolean hover = isEnabled() && getModel().isRollover();
            // A selected toggle shows the segmented control's selected fill, embedded or not.
            Color fill = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG) : embedded ? null : fill(hover);''',
     '''            boolean hover = isEnabled() && getModel().isRollover();
            boolean focused = !embedded && focus != null && focus.visible();
            paintKitFace(g, hover, focused);
            g.setFont(getFont()); g.setColor(textColor(hover));
            paintContent(g, hover);
        }
        /** Whether a disabled button fades as one layer: the buttons face's choice; an option control keeps its own. */
        boolean fadesDisabled() { return context.faces().buttons.fades(); }
        /** The face behind the glyph and label: the buttons face's; an option control paints its own. */
        void paintKitFace(Graphics2D g, boolean hover, boolean focused) { context.faces().buttons.paintFace(this, g, hover, focused); }
        /** The raised face: fill, border, the primary's inset line and drop (the default buttons face). */
        void paintRaised(Graphics2D g, boolean hover, boolean focused) {
            // A selected toggle shows the segmented control's selected fill, embedded or not.
            Color fill = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG) : embedded ? null : fill(hover);''', 1),
    ('''            Color border = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.BORDER_CONTROL) : embedded ? null : border();
            boolean focused = !embedded && focus != null && focus.visible();''',
     '''            Color border = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.BORDER_CONTROL) : embedded ? null : border();''', 1),
    ('''            primaryShadows(g, fill);
            g.setFont(getFont()); g.setColor(textColor(hover));
            Insets insets = getInsets();''',
     '''            primaryShadows(g, fill);
        }
        /** The glyph and label (and a recorder's dot and keycaps) over the face, in the graphics' font and colour. */
        private void paintContent(Graphics2D g, boolean hover) {
            Insets insets = getInsets();''', 1)])
edit(S + "Choices.java", [
    ('''            try {
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                    com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION)));
                icon.paintIcon(this, g, getWidth()-ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)-icon.getIconWidth(), (getHeight()-icon.getIconHeight())/2);
            } finally { g.dispose(); }
        }''',
     '''            try {
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                context.faces().buttons.paintPickerArrow(this, g);
            } finally { g.dispose(); }
        }
        /** The default look's chevron at the button's trailing padding. */
        void paintChevron(Graphics2D g) {
            var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION)));
            icon.paintIcon(this, g, getWidth()-ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)-icon.getIconWidth(), (getHeight()-icon.getIconHeight())/2);
        }''', 1),
    ('''                    @Override protected void paintComponent(Graphics graphics) {
                        // The dropdown chevron shares the picker face's measured glyph and trailing inset; a Settings dropdown
                        // sets it at a fixed distance from its own arrow lane's trailing edge.
                        boolean settings = settingsWidth > 0;
                        var icon = context.icons().icon("chevron.down", context.theme().semantic(), bare() && cellInk != null ? cellInk.supporting : SemanticColor.TEXT_SECONDARY,
                            ThemePainting.metric(context, settings ? DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON : DesignMetrics.Role.CHOICE_CHEVRON));
                        int beyond = comboBox.getWidth() - getX() - getWidth();
                        int x = settings ? getWidth() - ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON_INSET) - icon.getIconWidth()
                            : getWidth() + beyond - ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET) - icon.getIconWidth();
                        icon.paintIcon(this, graphics, Math.max(0, x), (getHeight()-icon.getIconHeight())/2);
                    }''',
     '''                    @Override protected void paintComponent(Graphics graphics) {
                        context.faces().buttons.paintArrow(ChoiceCombo.this, this, (Graphics2D) graphics, comboBox.getWidth() - getX() - getWidth(), settingsWidth > 0);
                    }''', 1),
    # 3c's bare-cell state: the chevron's role, read by both buttons faces.
    ('''        boolean bare() { return cell && !hover && !(focus != null && focus.visible()) && !isPopupVisible(); }
''', '''        boolean bare() { return cell && !hover && !(focus != null && focus.visible()) && !isPopupVisible(); }
        /** The chevron's role: the selected row's supporting ink while this cell dropdown is bare (plan 3c), else text.secondary. */
        SemanticColor arrowRole() { return bare() && cellInk != null ? cellInk.supporting : SemanticColor.TEXT_SECONDARY; }
''', 1),
    ('''    /** A dropdown's menu: as wide as its field, wider only when its widest row needs it (spec 3). */''',
     '''    /**
     * The default look's dropdown chevron in {@code role} in {@code arrow}, whose trailing edge lies {@code beyond} inside
     * the combo's: the picker face's measured glyph and trailing inset; a Settings dropdown sets it at a fixed distance
     * from its own arrow lane's trailing edge.
     */
    static void paintChevron(JComponent arrow, Graphics2D graphics, KitContext context, int beyond, boolean settings, SemanticColor role) {
        var icon = context.icons().icon("chevron.down", context.theme().semantic(), role,
            ThemePainting.metric(context, settings ? DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON : DesignMetrics.Role.CHOICE_CHEVRON));
        int x = settings ? arrow.getWidth() - ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON_INSET) - icon.getIconWidth()
            : arrow.getWidth() + beyond - ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET) - icon.getIconWidth();
        icon.paintIcon(arrow, graphics, Math.max(0, x), (arrow.getHeight()-icon.getIconHeight())/2);
    }
    /** A dropdown's menu: as wide as its field, wider only when its widest row needs it (spec 3). */''', 1),
    ('''            var g = (Graphics2D)graphics.create(); try { ThemePainting.rounded(g, this, context, ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL), ThemePainting.color(context, SemanticColor.BORDER_CONTROL), focus != null && focus.visible(), false); super.paintComponent(g); } finally { g.dispose(); }''',
     '''            var g = (Graphics2D)graphics.create(); try { context.faces().buttons.paintCombo(this, g, hover, focus != null && focus.visible()); super.paintComponent(g); } finally { g.dispose(); }''', 1)])
edit(S + "PickerMenus.java", [
    ('''    static void paintFace(JComponent button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus,boolean hover,boolean expanded){''',
     '''    static void paintFace(JComponent button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus,boolean hover,boolean expanded){
        c.faces().buttons.paintPicker(button,graphics,c,face,empty,focus,hover,expanded);
    }
    /** The default look's rich face: a tile, the primary text and caption, and the chevron. */
    static void paintRichFace(JComponent button,Graphics graphics,KitContext c,Nodes.PickerFace face,boolean empty,boolean focus,boolean hover,boolean expanded){''', 1),
    ('''    private static void line(Graphics2D g,KitContext c,String label,String caption,int x,int y,int width,int height,boolean active,boolean empty,boolean compact,boolean link,boolean technical){''',
     '''    static void line(Graphics2D g,KitContext c,String label,String caption,int x,int y,int width,int height,boolean active,boolean empty,boolean compact,boolean link,boolean technical){''', 1),
    ('''if(y>=stroke&&y<=getHeight()){graphics.setColor(ThemePainting.color(context(),SemanticColor.BORDER_MENU));graphics.fillRect(0,y-stroke,getWidth(),stroke);}''',
     '''if(y>=stroke&&y<=getHeight()){graphics.setColor(context().faces().buttons.menuDivider(context()));graphics.fillRect(0,y-stroke,getWidth(),stroke);}''', 1),
    ('''            searchIcon.setBorder(new EmptyBorder(0,0,0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_GAP)));''',
     '''            searchIcon.setBorder(new EmptyBorder(0,0,0,metric(context(),DesignMetrics.Role.PICKER_SEARCH_GAP)));
            searchIcon.setVisible(context().faces().fields.glyph(Nodes.FieldKind.SEARCH));''', 1),
    ('''            return new Dimension(pad+tile+gap+text+gap+tile+pad,rowHeight(owner,item));''',
     '''            int lead=c.faces().buttons.tiles()?tile+gap:gap;
            return new Dimension(pad+lead+text+gap+tile+pad,rowHeight(owner,item));''', 1),
    ('''            if(item.kind()==Nodes.PickerItemKind.UTILITY){int glyph=''',
     '''            if(!c.faces().buttons.tiles()){}
            else if(item.kind()==Nodes.PickerItemKind.UTILITY){int glyph=''', 1),
    ('''            int x=pad+tile+gap;''', '''            int x=pad+(c.faces().buttons.tiles()?tile+gap:gap);''', 1)])
edit(S + "TextFields.java", [
    ('''        boolean port;
        ThemePainting.Focus focus;''', '''        boolean port;
        /** Shows a value as a fact (Field.readOnly): the field policy may give it its own surface. */
        boolean readOnly;
        ThemePainting.Focus focus;''', 1),
    ('''surface.setEnabled(component.isEnabled());''', '''surface.setEnabled(component.isEnabled()); surface.readOnly = field.readOnly();''', 1),
    ('''            input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));''',
     '''            input.setForeground(field.readOnly() ? context().faces().fields.readOnlyText(context()) : ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));''', 1),
    ('''            leading.setVisible(field.presentation() != Nodes.FieldPresentation.PLAIN_PATH);''',
     '''            leading.setVisible(field.presentation() != Nodes.FieldPresentation.PLAIN_PATH && context().faces().fields.glyph(field.kind()));''', 1),
    ('''            try {
                if (transparent && focus.visible() && !error) ThemePainting.transparentFocus(g, this, context, DesignMetrics.Role.TRANSPARENT_FIELD_FOCUS_RADIUS);
                else ThemePainting.rounded(g, this, context, transparent ? null : ThemePainting.color(context, SemanticColor.SURFACE_INPUT),
                    ThemePainting.color(context, error ? SemanticColor.BORDER_ERROR : focus.visible() ? SemanticColor.BORDER_FOCUS : SemanticColor.BORDER_INPUT),
                    focus.visible(), error,radiusRole);
            } finally { g.dispose(); }
        }''',
     '''            try { context.faces().fields.paintField(this, g); } finally { g.dispose(); }
        }
        /** The field's surface and outline (the default policy, and every field the classic one does not grey). */
        void paintDefault(Graphics2D g) {
            if (transparent && focus.visible() && !error) ThemePainting.transparentFocus(g, this, context, DesignMetrics.Role.TRANSPARENT_FIELD_FOCUS_RADIUS);
            else ThemePainting.rounded(g, this, context, transparent ? null : ThemePainting.color(context, SemanticColor.SURFACE_INPUT),
                ThemePainting.color(context, error ? SemanticColor.BORDER_ERROR : focus.visible() ? SemanticColor.BORDER_FOCUS : SemanticColor.BORDER_INPUT),
                focus.visible(), error,radiusRole);
        }''', 1)])
edit(S + "Stacks.java", [
    ('''final KitContext context;boolean joined,route;StackSurface(KitContext context){this.context=context;}''',
     '''final KitContext context;boolean joined,route;
        /** The stack's layout role, which a classic ghost button reads to know a toolbar row from an actions row. */
        Nodes.LayoutRole role;
        StackSurface(KitContext context){this.context=context;}''', 1),
    ('''            ((StackSurface)component).route=stack.role()==Nodes.LayoutRole.ROUTE;''',
     '''            ((StackSurface)component).route=stack.role()==Nodes.LayoutRole.ROUTE;
            ((StackSurface)component).role=stack.role();''', 1)])
PY
```

The arrow button's anchor is its post-3c text (3c gives the chevron the bare cell's ink); `arrowRole()` keeps that
colour for the default face, and the classic face uses the same ink while the cell is bare, so 3c's selected-row
dropdown paints as before. `KitButton.textColor`'s anchor is its post-3b text: a Notice's link keeps 3b's text floor
on its severity's ground ahead of the face under both kits, and every other label asks the buttons face (the default
face returns the role's colour, as before; Execution notes X2). `KitButton.paintFace` now computes `hover` and `focused`, asks `paintKitFace` (the buttons face), sets the label's font and
colour and calls `paintContent`, which holds the former glyph, label and recorder drawing unchanged; `paintRaised` holds
the former fill, border, shape and `primaryShadows` lines unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicButtonsTest` 8 tests, `PaintSpecTest` with the new rows; `ButtonsTest`, `ChoicesTest`,
`PickerMenusTest`, `TextFieldsTest`, `ReadOnlyFieldsTest`, `TechnicalCaptionsTest`, 3c's `TableVocabularyTest` and the
gallery suites unchanged).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5 (both gallery runs, then the same Python block).
Expected: `changed: none`. Open `jasper-ui/build/classic-faces/buttons-disabled.png` and `dropdown.png` to check the flat
disabled faces and the ▼ by eye.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicButtonsTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: buttons, dropdowns, pickers and fields

The buttons point's default face is the raised button (its code moved into
KitButton.paintRaised); the classic face draws the gradient inside a guarded edge,
the accent primary with a medium label and no drop, flat disabled faces and flat
toolbar ghosts, and dropdown and picker faces ending with a drawn arrow. The field
policy built with it drops the SEARCH, PATH and PORT glyphs and greys read-only
fields; picker rows lose their tiles.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 3: The exclusive choice: radios, swatches and previews

`ChoiceFace` (Rulings 3) draws an EXCLUSIVE or TABS choice's track and options and a PREVIEWS choice's tiles. The default
face is today's segment track and preview grid, and it now declares the default look's two repaired pairs, which move
here from `PaintedContrastTest`; the classic face draws a left-aligned, wrapping row of 14 px radios (Rulings 11), each
with its hue as a 7 px swatch, and thumbnails above their radios (Rulings 12).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/ChoiceFace.java`, `DefaultChoiceFace.java`, `ClassicRadiosFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Choices.java:80,142-172,193,231,514-552`, `PreviewChoices.java:43-104`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/ControlRolesTest.java:37`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicChoicesTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java:46-62`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (rows inserted after the toggle's, rows appended)

**Interfaces:**
- Consumes: Task 1's seam; Task 2's `KitButton.paintKitFace`, `fadesDisabled` and `paintRaised`; 4a's `RADIO_RING`,
  `RADIO_DOT`, `RADIO_LABEL_GAP`, `RADIO_GAP`, `OPTION_SWATCH`, `THEME_PREVIEW_*`.
- Produces: `final ChoiceFace KitFaces.exclusive` and `final ChoiceFace KitFaces.tabs` (both default faces in this task;
  Task 4 gives `tabs` its classic face); `interface ChoiceFace extends Face { LayoutManager layout(KitContext); int
  trackPadding(KitContext, boolean form); void paintTrack(Choices.SegmentTrack, Graphics2D); void style(Choices.SegmentButton,
  Option, boolean settings, boolean identitySection, int track, int count); Dimension optionSize(Choices.SegmentButton);
  void paintOption(Choices.SegmentButton, Graphics, Runnable label); Color selectedText(KitContext); LayoutManager
  previewLayout(KitContext, int count); Dimension previewSize(PreviewChoices.Tile); void paintPreview(PreviewChoices.Tile,
  Graphics2D); }`; `DefaultChoiceFace(List<PaintPair>)` with `static final PaintPair SELECTED_LABEL, SELECTED_CAPTION`;
  `ClassicRadiosFace.RowLayout(KitContext, DesignMetrics.Role gapRole)` (a null role is no gap; Task 4 uses it);
  `Choices.SegmentTrack(KitContext, ChoiceFace)` with `paintSegments(Graphics2D)`; `Choices.SegmentButton(KitContext,
  ChoiceFace)` with `segmentSize()` and `paintSegment(Graphics, Runnable)`; `ChoiceFace Choices.ChoiceControl.optionsFace()`;
  the package-private record `Choices.HueDot`; the package-private `PreviewChoices.Tile` with `option`, `focus`,
  `tileSize()`, `paintTile(Graphics2D)` and `miniature(...)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicChoicesTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's exclusive point: the classic face is a row of radios with hue swatches; PREVIEWS put a thumbnail over each. */
@SuppressWarnings("try")
class ClassicChoicesTest {
    static Nodes.Choice protocol(String selected) {
        return new Nodes.Choice("protocol", ControlState.READY, Nodes.ChoiceKind.EXCLUSIVE, List.of(
            new Option("http", "HTTP", Optional.of(Nodes.Hue.BLUE)), new Option("ftp", "FTP", Optional.of(Nodes.Hue.AMBER)),
            new Option("sftp", "SFTP", Optional.of(Nodes.Hue.GREEN)), new Option("tftp", "TFTP", Optional.of(Nodes.Hue.PURPLE))),
            selected, "Protocol", "protocol");
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static int rgb(BufferedImage image, int x, int y) { return image.getRGB(x, y) & 0xFFFFFF; }

    /**
     * Whether {@code image}'s pixel at ({@code x}, {@code y}) is {@code color} within 2 a channel. A ring's outermost pixel
     * on its middle row is about 96% covered and painted on a transparent image, so Java2D stores its colour rounded by
     * one (for example 0x1F52A6 for 0x1F52A7); a wrong role is far more than 2 away.
     */
    static boolean near(BufferedImage image, int x, int y, Color color) {
        var painted = new Color(image.getRGB(x, y), true);
        return Math.abs(painted.getRed() - color.getRed()) <= 2 && Math.abs(painted.getGreen() - color.getGreen()) <= 2
            && Math.abs(painted.getBlue() - color.getBlue()) <= 2;
    }

    @Test void theRadiosAreALeftAlignedRowWithTheirSwatches() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 40, "radios");
                KitFixtures.render(view, 420, 40, 1);
                var http = find(view, "protocol.option.http", Choices.SegmentButton.class);
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.RADIO_RING);
                assertThat(http.getX()).as("left-aligned").isZero();
                assertThat(ftp.getX()).isEqualTo(http.getWidth() + ThemePainting.metric(context, DesignMetrics.Role.RADIO_GAP));
                assertThat(ftp.getIcon().getIconWidth()).isEqualTo(ring + ThemePainting.metric(context, DesignMetrics.Role.RADIO_LABEL_GAP)
                    + ThemePainting.metric(context, DesignMetrics.Role.OPTION_SWATCH));
                var pairs = context.faces().exclusive.pairs();
                var image = paint(ftp);
                int y = (ftp.getHeight() - ring) / 2;
                // A ring's outermost pixel is antialiased against nothing: its colour is the ring's (within rounding), its alpha
                // the coverage. The dot's centre pixel is fully covered, so it is exact.
                assertThat(near(image, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(1))))
                    .as("selected ring %06X", rgb(image, 0, y + ring / 2)).isTrue();
                assertThat(new Color(image.getRGB(ring / 2, y + ring / 2), true)).as("dot").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var plain = paint(http);
                assertThat(near(plain, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(3))))
                    .as("ring %06X", rgb(plain, 0, y + ring / 2)).isTrue();
                http.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("protocol", Set.of("http")));
            } finally { release(context); }
        });
    }

    /** Review Focus 4: a row too narrow for its labels wraps to a second line instead of clipping one. */
    @Test void radiosWrapRatherThanClipALabel() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), e -> {})) {
                KitFixtures.render(view, 150, 80, 1);
                for (var id : List.of("http", "ftp", "sftp", "tftp")) {
                    var option = find(view, "protocol.option." + id, Choices.SegmentButton.class);
                    assertThat(option.getWidth()).as(id).isEqualTo(option.getPreferredSize().width);
                }
                assertThat(find(view, "protocol.option.tftp", Choices.SegmentButton.class).getY()).as("a second line")
                    .isGreaterThan(find(view, "protocol.option.http", Choices.SegmentButton.class).getY());
            } finally { release(context); }
        });
    }

    /** Review Focus 5: Left, Right, Home and End move and select; Up and Down never throw on the radio row. */
    @Test void theKeysMoveAlongTheRadios() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), events::add)) {
                KitFixtures.render(view, 420, 40, 1);
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                for (String key : List.of("RIGHT", "LEFT", "UP", "DOWN", "HOME", "END")) {
                    var action = ftp.getActionMap().get("choice." + key);
                    assertThat(action).as(key).isNotNull();
                    action.actionPerformed(new java.awt.event.ActionEvent(ftp, 0, key));
                }
                assertThat(events).extracting(Object::toString).hasSize(6);
            } finally { release(context); }
        });
    }

    @Test void theRadiosPaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var pairs = context.faces().exclusive.pairs();
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.RADIO_RING), y = (ftp.getHeight() - ring) / 2;
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                var image = paint(ftp);
                assertThat(near(image, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(1))))
                    .as("%s ring %06X", where, rgb(image, 0, y + ring / 2)).isTrue();
                assertThat(ftp.getForeground()).as(where + " label").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
            } finally { release(context); }
        });
    }

    /** Rulings 12: previews keep their size, left-aligned, the selected one in a 2 px accent frame over its radio. */
    @Test void previewsAreThumbnailsOverTheirRadios() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var palette = new Option.Palette(0xFF202124, 0xFF2A2D33, 0xFF33363C, 0xFFDFE1E5, 0xFF3D74D6);
            var choice = new Nodes.Choice("theme", ControlState.READY, Nodes.ChoiceKind.PREVIEWS, List.of(
                new Option("dark", "Dark", Optional.empty(), "", "", Optional.of(new Option.Preview(palette, Optional.empty()))),
                new Option("light", "Light", Optional.empty(), "", "", Optional.of(new Option.Preview(palette, Optional.empty())))),
                "light", "Theme", "theme");
            try (var view = new KitView(context, new ViewSpec(choice), e -> {})) {
                KitFixtures.capture(context, view.spec(), 520, 140, "previews");
                KitFixtures.render(view, 520, 140, 1);
                JButton dark = find(view, "theme.option.dark", JButton.class), light = find(view, "theme.option.light", JButton.class);
                assertThat(dark.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_WIDTH));
                assertThat(light.getX() - dark.getX() - dark.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_GAP));
                assertThat(new Color(paint(light).getRGB(0, 10), true)).isEqualTo(ThemePainting.painted(context, context.faces().exclusive.pairs().get(4)));
            } finally { release(context); }
        });
    }

    @Test void theDefaultFaceDeclaresTheDefaultLooksTwoRepairs() {
        assertThat(KitFaces.declared(dev.jasper.ui.kit.Kit.DEFAULT).stream().distinct().toList())
            .containsExactly(DefaultChoiceFace.SELECTED_LABEL, DefaultChoiceFace.SELECTED_CAPTION);
    }
}
```

Insert the default rows after the toggle's and append the classic rows in `face-pairs.tsv` (it lists `Kit.DEFAULT`'s
faces first, then `Kit.CLASSIC`'s):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
lines = path.read_text(encoding="utf-8").splitlines()
head, rows = lines[0], lines[1:]
default = ["default\tDefaultChoiceFace\ttext.primary\tsegment.selected.bg\tCONTRAST:4.5",
           "default\tDefaultChoiceFace\ttext.primary\tsurface.card\tCONTRAST:4.5",
           "default\tDefaultChoiceFace\ttext.primary\tsegment.selected.bg\tCONTRAST:4.5"]
classic = ["classic\tClassicRadiosFace\ttext.primary\tsurface.app\tCONTRAST:4.5",
           "classic\tClassicRadiosFace\taccent.edge\tsurface.input\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\taccent\tsurface.input\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\tborder.hover\tsurface.input\tCONTRAST:1.5",
           "classic\tClassicRadiosFace\taccent\tsurface.app\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\tborder.window\tsurface.app\tCONTRAST:1.3",
           "classic\tClassicRadiosFace\thue.blue\tsurface.app\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\thue.green\tsurface.app\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\thue.amber\tsurface.app\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\thue.purple\tsurface.app\tCONTRAST:3.0",
           "classic\tClassicRadiosFace\thue.neutral\tsurface.app\tCONTRAST:3.0",
           "classic\tDefaultChoiceFace\ttext.primary\tsegment.selected.bg\tCONTRAST:4.5"]
# KitFaces.all() is toggle, exclusive, tabs, buttons, fields: the classic choice rows follow the toggle's.
last_toggle = max(i for i, row in enumerate(rows) if "\tClassicToggleFace\t" in row)
rows = rows[:last_toggle + 1] + classic + rows[last_toggle + 1:]
path.write_text("\n".join([head] + default + rows) + "\n", encoding="utf-8")
PY
```

The last classic choice row is the tabs point's default face, which `Kit.CLASSIC` still uses until Task 4.

Run from the worktree root, to declare the two default pairs through the faces in `PaintedContrastTest`. After 3b,
`DEFAULT_PAIRS` continues past the selected preview caption with 3b's Notice pairs, so the first edit replaces only the
two entries (ending at `PaintedContrastTest::selectedPreview),`) and the method and test go in before the class's
`palettes()` helper, outside the list:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 3 step 1: the default look's two repairs are now the default choice face's declarations."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java", [
    ('''        new Declared("selected segment label", new PaintPair(TEXT_PRIMARY, SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedSegment),
        new Declared("selected preview caption", new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedPreview),''',
     '''        new Declared("selected segment label", faces().get(0), PaintedContrastTest::selectedSegment),
        new Declared("selected preview caption", faces().get(1), PaintedContrastTest::selectedPreview),''', 1),
    ('''    /** The theme set, then the degenerate palettes. */''',
     '''    /** The default look's face declarations (plan 4b): the selected segment label, then the selected preview caption. */
    private static List<PaintPair> faces() { return KitFaces.declared(Kit.DEFAULT).stream().distinct().toList(); }

    @Test void theDefaultFacesDeclareExactlyTheTwoRepairsTheDefaultLookMakes() {
        assertThat(faces()).containsExactly(new PaintPair(TEXT_PRIMARY, SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT),
            new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT));
    }

    /** The theme set, then the degenerate palettes. */''', 1)])
edit("jasper-ui/src/test/java/dev/jasper/ui/swing/ControlRolesTest.java", [
    ('''var track = new Choices.SegmentTrack(context);''', '''var track = new Choices.SegmentTrack(context, context.faces().exclusive);''', 1)])
PY
```

`PaintedContrastTest` already imports `dev.jasper.ui.swing.*` (so `KitFaces`) and `dev.jasper.ui.kit.Kit`. `faces()`
is a static method, so `DEFAULT_PAIRS`' initializer may call it although it is declared below the list.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicChoicesTest'`
Expected: FAIL at compile: "cannot find symbol … variable exclusive" in `KitFaces` and "class DefaultChoiceFace".

- [ ] **Step 3: Add the faces and route the choices through them**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ChoiceFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Option;
import java.awt.*;

/**
 * The exclusive and tabs points (spec 5.2, plan 4b Rulings 3): an EXCLUSIVE or TABS choice's track and options, and a
 * PREVIEWS choice's tiles (asked of the exclusive point's face only).
 */
interface ChoiceFace extends Face {
    /** The layout of a choice's options inside its track. */
    LayoutManager layout(KitContext context);
    /** The track's padding round its options. */
    int trackPadding(KitContext context, boolean form);
    /** Paints the track behind its options. */
    void paintTrack(Choices.SegmentTrack track, Graphics2D g);
    /** Styles an option for {@code model}: its font, colours, glyph and padding. */
    void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count);
    /** An option's size. */
    Dimension optionSize(Choices.SegmentButton option);
    /** Paints an option; {@code label} paints the button's own glyph and label, as the default look does. */
    void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label);
    /** The selected option's label colour. */
    Color selectedText(KitContext context);
    /** The layout of a PREVIEWS choice's tiles. */
    LayoutManager previewLayout(KitContext context, int count);
    /** A preview tile's size. */
    Dimension previewSize(PreviewChoices.Tile tile);
    /** Paints a preview tile. */
    void paintPreview(PreviewChoices.Tile tile, Graphics2D g);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultChoiceFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.border.EmptyBorder;

/** The default choices: the segment track, its selected segment's soft shadow in Light, and the stretched preview grid. */
final class DefaultChoiceFace implements ChoiceFace {
    /** The selected segment is not an accent surface: primary text, held to the text floor on its fill. */
    static final PaintPair SELECTED_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT);
    /** A selected preview's caption on the card. */
    static final PaintPair SELECTED_CAPTION = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    private final List<PaintPair> pairs;

    /** A face declaring {@code pairs}: the exclusive point both repairs, the tabs point the segment label alone. */
    DefaultChoiceFace(List<PaintPair> pairs) { this.pairs = List.copyOf(pairs); }

    @Override public List<PaintPair> pairs() { return pairs; }
    @Override public LayoutManager layout(KitContext context) { return new Choices.SegmentLayout(); }
    @Override public int trackPadding(KitContext context, boolean form) {
        return ThemePainting.metric(context, form ? DesignMetrics.Role.FORM_SEGMENT_PADDING : DesignMetrics.Role.SETTINGS_SEGMENT_INSET);
    }
    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) { track.paintSegments(g); }
    @Override public void style(Choices.SegmentButton button, Option option, boolean settings, boolean identitySection, int track, int count) {
        var context = button.context;
        var font = context.typography().font(Typography.Role.LABEL, !settings && button.isSelected());
        button.setFont(SwingTypography.effective(font));
        button.setForeground(button.textColor(false));
        button.setIcon(option.hue().map(h -> (javax.swing.Icon) new Choices.HueDot(context, h)).orElse(null)); button.setDisabledIcon(button.getIcon());
        // A segment's label keeps its padding from the segment's edge everywhere (spec 4).
        int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);
        if (identitySection) pad = Math.max(pad, ((ThemePainting.metric(context, DesignMetrics.Role.IDENTITY_SECTION_WIDTH) - 2 * track) / Math.max(1, count)
            - button.getFontMetrics(button.getFont()).stringWidth(option.label())) / 2);
        button.setBorder(new EmptyBorder(0, pad, 0, pad));
    }
    @Override public Dimension optionSize(Choices.SegmentButton option) { return option.segmentSize(); }
    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) { option.paintSegment(graphics, label); }
    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, SELECTED_LABEL); }
    @Override public LayoutManager previewLayout(KitContext context, int count) {
        return new GridLayout(1, Math.max(1, count), ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_GAP), 0);
    }
    @Override public Dimension previewSize(PreviewChoices.Tile tile) { return tile.tileSize(); }
    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) { tile.paintTile(g); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicRadiosFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * The classic exclusive choice (spec 5.2, DESIGN_LANGUAGE §6): a left-aligned row of 14 px radios, 18 apart, wrapping
 * line by line; a selected ring in {@code accent.edge} round a 6 px accent dot, an unselected one in {@code border.hover};
 * an option's hue as a 7 px swatch before its label; PREVIEWS as thumbnails over their radios, the selected one in a 2 px
 * accent frame.
 */
final class ClassicRadiosFace implements ChoiceFace {
    private final PaintPair label, selectedRing, dot, ring, frame, plainFrame;
    private final List<PaintPair> swatches;

    /** A face whose radios stand on {@code surface}, the kit's dialog surface. */
    ClassicRadiosFace(SemanticColor surface) {
        label = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        selectedRing = new PaintPair(DerivedColor.ACCENT_EDGE, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        dot = new PaintPair(SemanticColor.ACCENT, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        ring = new PaintPair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.CONTROL);
        frame = new PaintPair(SemanticColor.ACCENT, surface, SurfaceContrast.Guard.INDICATOR);
        plainFrame = new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL);
        swatches = List.of(swatch(SemanticColor.HUE_BLUE, surface), swatch(SemanticColor.HUE_GREEN, surface), swatch(SemanticColor.HUE_AMBER, surface),
            swatch(SemanticColor.HUE_PURPLE, surface), swatch(SemanticColor.HUE_NEUTRAL, surface));
    }

    private static PaintPair swatch(SemanticColor hue, SemanticColor surface) { return new PaintPair(hue, surface, SurfaceContrast.Guard.INDICATOR); }

    @Override public List<PaintPair> pairs() {
        var all = new ArrayList<>(List.of(label, selectedRing, dot, ring, frame, plainFrame));
        all.addAll(swatches);
        return List.copyOf(all);
    }

    private PaintPair swatchOf(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> swatches.get(0); case GREEN -> swatches.get(1); case AMBER -> swatches.get(2); case PURPLE -> swatches.get(3);
            case NEUTRAL, NONE -> swatches.get(4);
        };
    }

    @Override public LayoutManager layout(KitContext context) { return new RowLayout(context, DesignMetrics.Role.RADIO_GAP); }
    @Override public int trackPadding(KitContext context, boolean form) { return 0; }
    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) { }

    @Override public void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count) {
        var c = option.context;
        option.setFont(ThemePainting.font(c, Typography.Role.LABEL));
        option.setForeground(ThemePainting.painted(c, label));
        option.setIcon(new Radio(c, option, model.hue())); option.setDisabledIcon(option.getIcon());
        option.setIconTextGap(ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP));
        option.setBorder(new EmptyBorder(0, 0, 0, 0));
        option.setHorizontalAlignment(SwingConstants.LEADING);
    }

    @Override public Dimension optionSize(Choices.SegmentButton option) {
        var c = option.context; var icon = option.getIcon();
        int text = ThemePainting.measureText(option.getFont(), option.getText() == null ? "" : option.getText());
        int width = (icon == null ? 0 : icon.getIconWidth() + option.getIconTextGap()) + text;
        int ring = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING) + 2 * ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
        return new Dimension(width, Math.max(ring, option.getFontMetrics(option.getFont()).getHeight()));
    }

    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            var icon = option.getIcon();
            int x = 0;
            if (icon != null) { icon.paintIcon(option, g, 0, (option.getHeight() - icon.getIconHeight()) / 2); x = icon.getIconWidth() + option.getIconTextGap(); }
            if (!option.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            g.setFont(option.getFont()); g.setColor(option.getForeground());
            var fm = g.getFontMetrics();
            g.drawString(option.getText() == null ? "" : option.getText(), x, (option.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        } finally { g.dispose(); }
    }

    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, label); }

    @Override public LayoutManager previewLayout(KitContext context, int count) { return new RowLayout(context, DesignMetrics.Role.THEME_PREVIEW_GAP); }

    @Override public Dimension previewSize(PreviewChoices.Tile tile) {
        var c = tile.context();
        int line = Math.max(ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING), tile.getFontMetrics(tile.getFont()).getHeight());
        return new Dimension(ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_WIDTH), ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_HEIGHT)
            + ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP) + line);
    }

    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) {
        var c = tile.context();
        ThemePainting.antialias(g);
        int h = Math.min(tile.getHeight(), ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_HEIGHT)), w = tile.getWidth();
        int width = ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_RING), inset = ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_INSET) / 2;
        if (!tile.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
        // The frame: 2 px of accent round the selected thumbnail, of the window border round the others.
        g.setColor(ThemePainting.painted(c, tile.isSelected() ? frame : plainFrame));
        g.fillRect(0, 0, w, width); g.fillRect(0, h - width, w, width); g.fillRect(0, 0, width, h); g.fillRect(w - width, 0, width, h);
        if (tile.option != null && tile.option.preview().isPresent()) {
            var preview = tile.option.preview().get();
            var clip = g.getClip();
            g.clipRect(inset, inset, Math.max(0, w - 2 * inset), Math.max(0, h - 2 * inset));
            tile.miniature(g, preview.primary(), inset, inset, w - 2 * inset, h - 2 * inset);
            preview.alternate().ifPresent(other -> { g.clipRect(w / 2, 0, w, h); tile.miniature(g, other, inset, inset, w - 2 * inset, h - 2 * inset); });
            g.setClip(clip);
        }
        int top = h + ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP), ringSize = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING);
        paintRadio(g, c, 0, top, tile.isSelected(), tile.isEnabled());
        if (tile.focus != null && tile.focus.visible()) {
            g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
            g.draw(new Ellipse2D.Double(-1, top - 1, ringSize + 1, ringSize + 1));
        }
        g.setFont(tile.getFont()); g.setColor(ThemePainting.painted(c, label));
        var fm = g.getFontMetrics();
        int x = ringSize + ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP);
        g.drawString(tile.option == null ? "" : tile.option.label(), x, top + (ringSize - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** A radio at ({@code x}, {@code y}): the ring, its inside and, when selected, the dot. */
    void paintRadio(Graphics2D g, KitContext c, double x, double y, boolean selected, boolean enabled) {
        int size = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING), dotSize = ThemePainting.metric(c, DesignMetrics.Role.RADIO_DOT);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        g.setColor(!enabled ? ThemePainting.role(c, DerivedColor.BORDER_DIVIDER) : ThemePainting.painted(c, selected ? selectedRing : ring));
        g.fill(new Ellipse2D.Double(x, y, size, size));
        g.setColor(!enabled ? ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER) : ThemePainting.color(c, SemanticColor.SURFACE_INPUT));
        g.fill(new Ellipse2D.Double(x + line, y + line, size - 2.0 * line, size - 2.0 * line));
        if (selected) {
            g.setColor(enabled ? ThemePainting.painted(c, dot) : ThemePainting.color(c, SemanticColor.TEXT_PLACEHOLDER));
            double offset = (size - dotSize) / 2.0;
            g.fill(new Ellipse2D.Double(x + offset, y + offset, dotSize, dotSize));
        }
    }

    /** An option's glyph: its radio, then its hue's swatch when it has one. */
    private final class Radio implements Icon {
        private final KitContext c; private final AbstractButton option; private final Optional<Nodes.Hue> hue;
        Radio(KitContext c, AbstractButton option, Optional<Nodes.Hue> hue) { this.c = c; this.option = option; this.hue = hue; }
        private int ring() { return ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING); }
        @Override public int getIconWidth() {
            return ring() + hue.map(h -> ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP) + ThemePainting.metric(c, DesignMetrics.Role.OPTION_SWATCH)).orElse(0);
        }
        @Override public int getIconHeight() { return ring(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                if (option instanceof Choices.SegmentButton button && button.focus != null && button.focus.visible()) {
                    int halo = ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
                    g.setColor(ColorMath.opacity(ThemePainting.color(c, SemanticColor.BORDER_FOCUS), ThemePainting.focusHaloShare(c, false)));
                    g.fill(new Ellipse2D.Double(x - halo, y - halo, ring() + 2.0 * halo, ring() + 2.0 * halo));
                }
                paintRadio(g, c, x, y, option.isSelected(), option.isEnabled());
                hue.ifPresent(h -> {
                    int size = ThemePainting.metric(c, DesignMetrics.Role.OPTION_SWATCH);
                    g.setColor(ThemePainting.painted(c, swatchOf(h)));
                    g.fill(new RoundRectangle2D.Double(x + ring() + ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP), y + (ring() - size) / 2.0, size, size, 0, 0));
                });
            } finally { g.dispose(); }
        }
    }

    /** Children left to right, {@code gapRole} apart (none when null), each at its own width, wrapping line by line. */
    static final class RowLayout implements Composition.WidthLayout {
        private final KitContext context;
        private final DesignMetrics.Role gapRole;
        RowLayout(KitContext context, DesignMetrics.Role gapRole) { this.context = context; this.gapRole = gapRole; }
        int gap() { return gapRole == null ? 0 : ThemePainting.metric(context, gapRole); }
        List<List<Component>> lines(Container parent, int width) {
            Insets i = parent.getInsets(); int room = Math.max(0, width - i.left - i.right), used = 0;
            var lines = new ArrayList<List<Component>>(); var line = new ArrayList<Component>();
            for (var child : parent.getComponents()) if (child.isVisible()) {
                int w = child.getPreferredSize().width;
                if (!line.isEmpty() && used + gap() + w > room) { lines.add(line); line = new ArrayList<>(); used = 0; }
                used += (line.isEmpty() ? 0 : gap()) + w; line.add(child);
            }
            if (!line.isEmpty()) lines.add(line);
            return lines;
        }
        private static int lineHeight(List<Component> line) { return line.stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0); }
        private int between() { return gapRole == null ? 0 : ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP); }
        @Override public int height(Container parent, int width) {
            Insets i = parent.getInsets(); var lines = lines(parent, width);
            return i.top + i.bottom + lines.stream().mapToInt(RowLayout::lineHeight).sum() + Math.max(0, lines.size() - 1) * between();
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets i = parent.getInsets(); int w = 0, h = 0, n = 0;
            for (var child : parent.getComponents()) if (child.isVisible()) {
                var size = child.getPreferredSize(); w += (n++ > 0 ? gap() : 0) + size.width; h = Math.max(h, size.height);
            }
            return new Dimension(i.left + i.right + w, i.top + i.bottom + h);
        }
        @Override public void layoutContainer(Container parent) {
            Insets i = parent.getInsets(); int y = i.top;
            for (var line : lines(parent, parent.getWidth())) {
                int x = i.left, h = lineHeight(line);
                for (var child : line) {
                    var size = child.getPreferredSize();
                    child.setBounds(x, y + (h - size.height) / 2, Math.min(size.width, Math.max(0, parent.getWidth() - i.right - x)), size.height);
                    x += size.width + gap();
                }
                y += h + between();
            }
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 3 step 3: choices and previews ask their face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final FieldPolicy fields;
''', '''    final FieldPolicy fields;
    final ChoiceFace exclusive, tabs;
''', 1),
    ('''            default -> throw new IllegalStateException("Unknown buttons variant " + controls.buttons());
        }''', '''            default -> throw new IllegalStateException("Unknown buttons variant " + controls.buttons());
        }
        exclusive = switch (controls.exclusive()) {
            case SEGMENTS -> new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL, DefaultChoiceFace.SELECTED_CAPTION));
            case RADIOS -> new ClassicRadiosFace(colors.dialogSurface());
        };
        tabs = new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL));''', 1),
    ('''    List<Face> all() { return List.of(toggle, buttons, fields); }''', '''    List<Face> all() { return List.of(toggle, exclusive, tabs, buttons, fields); }''', 1)])
edit(S + "Choices.java", [
    ('''            } else { input = new SegmentTrack(context()); own(this::closeOptions); }''',
     '''            } else { input = new SegmentTrack(context(), optionsFace()); own(this::closeOptions); }''', 1),
    ('''                        closeOptions(); input.removeAll(); options = choice.options(); input.setLayout(new SegmentLayout());''',
     '''                        closeOptions(); input.removeAll(); options = choice.options(); input.setLayout(optionsFace().layout(context()));''', 1),
    ('''var button = new SegmentButton(context()); button.setText(option.label());''',
     '''var button = new SegmentButton(context(), optionsFace()); button.setText(option.label());''', 1),
    ('''                            bind(button, "UP", () -> navigate(index - ((SegmentLayout)input.getLayout()).columns(input, input.getWidth())));
                            bind(button, "DOWN", () -> navigate(index + ((SegmentLayout)input.getLayout()).columns(input, input.getWidth())));''',
     '''                            bind(button, "UP", () -> navigate(index - columns()));
                            bind(button, "DOWN", () -> navigate(index + columns()));''', 1),
    ('''                    int track = ThemePainting.metric(context(), form ? DesignMetrics.Role.FORM_SEGMENT_PADDING : DesignMetrics.Role.SETTINGS_SEGMENT_INSET);''',
     '''                    int track = optionsFace().trackPadding(context(), form);''', 1),
    ('''                        var font = context().typography().font(Typography.Role.LABEL, !settings && button.isSelected());
                        button.setFont(SwingTypography.effective(font));
                        button.setForeground(button.textColor(false));
                        button.setIcon(option.hue().map(h -> (javax.swing.Icon)new HueDot(context(), h)).orElse(null)); button.setDisabledIcon(button.getIcon());
                        // A segment's label keeps its padding from the segment's edge everywhere (spec 4).
                        int pad = ThemePainting.metric(context(), DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);
                        if(identitySection)pad=Math.max(pad,((ThemePainting.metric(context(),DesignMetrics.Role.IDENTITY_SECTION_WIDTH)-2*track)/Math.max(1,choice.options().size())-button.getFontMetrics(button.getFont()).stringWidth(option.label()))/2);
                        button.setBorder(new EmptyBorder(0, pad, 0, pad));''',
     '''                        optionsFace().style(button, option, settings, identitySection, track, choice.options().size());''', 1),
    ('''        private void select(String id) { emit(new UiEvent.Selected(node.id(), Set.of(id))); }''',
     '''        private void select(String id) { emit(new UiEvent.Selected(node.id(), Set.of(id))); }
        /** The face this choice's options draw with: the tabs point's for a TABS choice, the exclusive point's otherwise. */
        ChoiceFace optionsFace() { return ((Nodes.Choice) node).kind() == Nodes.ChoiceKind.TABS ? context().faces().tabs : context().faces().exclusive; }
        /** How many options Up and Down move by: a segment line's worth, or every option on a radio row. */
        private int columns() { return input.getLayout() instanceof SegmentLayout layout ? layout.columns(input, input.getWidth()) : Math.max(1, options.size()); }''', 1),
    ('''    private record HueDot(KitContext context, Nodes.Hue hue) implements javax.swing.Icon {''',
     '''    record HueDot(KitContext context, Nodes.Hue hue) implements javax.swing.Icon {''', 1),
    ('''        SegmentTrack(KitContext context) { this.context = context; setOpaque(false);''',
     '''        final ChoiceFace face;
        SegmentTrack(KitContext context, ChoiceFace face) { this.context = context; this.face = face; setOpaque(false);''', 1),
    ('''            var g = (Graphics2D)graphics.create(); try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_TRACK)''',
     '''            var g = (Graphics2D)graphics.create(); try { face.paintTrack(this, g); } finally { g.dispose(); }
        }
        /** The segment track, and the selected segment's soft shadow in Light (the default face). */
        void paintSegments(Graphics2D g) {
            { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_TRACK)''', 1),
    ('''                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_SHADOW_BLUR), context.metrics().value(DesignMetrics.Role.SEGMENT_SHADOW_PERCENT_LIGHT) / 100.0);
            } finally { g.dispose(); }
        }
    }''',
     '''                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_SHADOW_BLUR), context.metrics().value(DesignMetrics.Role.SEGMENT_SHADOW_PERCENT_LIGHT) / 100.0);
            }
        }
    }''', 1),
    ('''        SegmentButton(KitContext context) { super(context); this.context = context; kind = Nodes.ButtonKind.GHOST; }''',
     '''        /** The face this option draws with, its choice's. */
        final ChoiceFace face;
        SegmentButton(KitContext context, ChoiceFace face) { super(context); this.context = context; this.face = face; kind = Nodes.ButtonKind.GHOST; }
        // An option's face belongs to its choice face, never to the buttons face.
        @Override boolean fadesDisabled() { return true; }
        @Override void paintKitFace(Graphics2D g, boolean hover, boolean focused) { paintRaised(g, hover, focused); }''', 1),
    ('''            return isSelected() ? dev.jasper.ui.theme.SurfaceContrast.on(context.theme().semantic(), SemanticColor.TEXT_PRIMARY,
                SemanticColor.SEGMENT_SELECTED_BG, dev.jasper.ui.theme.SurfaceContrast.TEXT) : super.textColor(hover);''',
     '''            return isSelected() ? face.selectedText(context) : ThemePainting.color(context, textRole(hover));''', 1),
    ('''        @Override public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            return new Dimension(size.width, settings ?''',
     '''        @Override public Dimension getPreferredSize() { return face.optionSize(this); }
        /** The segment's size (the default face). */
        Dimension segmentSize() {
            var size = super.getPreferredSize();
            return new Dimension(size.width, settings ?''', 1),
    ('''        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                if (isSelected()) { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG), null, false, false, DesignMetrics.Role.SEGMENT_ITEM_RADIUS); }
                super.paintComponent(graphics);
            } finally { g.dispose(); }
        }''',
     '''        @Override protected void paintComponent(Graphics graphics) { face.paintOption(this, graphics, () -> super.paintComponent(graphics)); }
        /** The selected segment's fill, then {@code label}, the button's own glyph and label (the default face). */
        void paintSegment(Graphics graphics, Runnable label) {
            var g = (Graphics2D)graphics.create();
            try {
                if (isSelected()) { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG), null, false, false, DesignMetrics.Role.SEGMENT_ITEM_RADIUS); }
                label.run();
            } finally { g.dispose(); }
        }''', 1)])
edit(S + "PreviewChoices.java", [
    ('''        component.setLayout(new GridLayout(1,Math.max(1,tiles.size()),ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_GAP),0));''',
     '''        component.setLayout(context().faces().exclusive.previewLayout(context(), tiles.size()));''', 1),
    ('''    private final class Tile extends JButton {''', '''    /** One option's tile; its face (the exclusive point's) sizes and paints it. */
    final class Tile extends JButton {''', 1),
    ('''        @Override public Dimension getPreferredSize() {
            return new Dimension(ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_WIDTH),''',
     '''        @Override public Dimension getPreferredSize() { return context().faces().exclusive.previewSize(this); }
        /** The context the tile paints in. */
        KitContext context() { return PreviewChoices.this.context(); }
        /** The selection box over a caption line (the default face). */
        Dimension tileSize() {
            return new Dimension(ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_WIDTH),''', 1),
    ('''        @Override protected void paintComponent(Graphics graphics) {
            var g=(Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g);if(!isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));''',
     '''        @Override protected void paintComponent(Graphics graphics) {
            var g=(Graphics2D)graphics.create();
            try { context().faces().exclusive.paintPreview(this, g); } finally { g.dispose(); }
        }
        /** The miniature, its selection ring and its caption (the default face). */
        void paintTile(Graphics2D g) {
            {
                ThemePainting.antialias(g);if(!isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.42f));''', 1),
    ('''                g.drawString(label,(getWidth()-fm.stringWidth(label))/2,h+ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP)+fm.getAscent());
            } finally {g.dispose();}
        }''',
     '''                g.drawString(label,(getWidth()-fm.stringWidth(label))/2,h+ThemePainting.metric(context(),DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP)+fm.getAscent());
            }
        }''', 1),
    ('''g.setColor(isSelected()?dev.jasper.ui.theme.SurfaceContrast.on(context().theme().semantic(),SemanticColor.TEXT_PRIMARY,SemanticColor.SURFACE_CARD,dev.jasper.ui.theme.SurfaceContrast.TEXT):ThemePainting.color(context(),SemanticColor.TEXT_SECONDARY));''',
     '''g.setColor(isSelected()?ThemePainting.painted(context(),DefaultChoiceFace.SELECTED_CAPTION):ThemePainting.color(context(),SemanticColor.TEXT_SECONDARY));''', 1),
    ('''        private void miniature(Graphics2D g,Option.Palette p,int x,int y,int w,int h) {''',
     '''        void miniature(Graphics2D g,Option.Palette p,int x,int y,int w,int h) {''', 1)])
PY
```

The `SegmentTrack`, `SegmentButton` and `Tile` bodies the default face calls keep their former lines (the inner `{ … }`
blocks); the selected segment label and preview caption now read their declared pairs through `ThemePainting.painted`,
which equals `SurfaceContrast.on` for the opaque roles of every shipped theme and fixture (plan 4a Rulings 2).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue && ./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: PASS (`ClassicChoicesTest` 6 tests; `ChoicesTest`, `TabsTest`, `SwatchesTest`, `ControlRolesTest`,
`SettingsControlsTest` unchanged; `PaintedContrastTest` with its new test).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5. Expected: `changed: none`. Open
`jasper-ui/build/classic-faces/radios.png` and `previews.png` and compare them by eye with New File Server – FTP's
Protocol row and Settings – Appearance's Theme group.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicChoicesTest.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/ControlRolesTest.java jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv \
  jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java
git commit -F - <<'MSG'
Classic faces: radios, swatches and preview thumbnails

ChoiceFace draws EXCLUSIVE and TABS options and PREVIEWS tiles. The default face is
the segment track and preview grid (their code moved into named methods) and now
declares the default look's two repaired pairs, which PaintedContrastTest reads
from it. The classic face draws a wrapping row of 14 px radios with hue swatches,
and thumbnails over their radios.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 4: The tabs: folder tabs

The tabs point gets its classic face (Rulings 13): content-sized folder tabs on a `border.control` baseline, the active
one `surface.card` and joined to the content below, the others `surface.stripe`, each with a 2 px top radius.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicFolderTabsFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTabsTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (the tabs point's classic rows replace its default row)

**Interfaces:**
- Consumes: Task 3's `ChoiceFace`, `ClassicRadiosFace.RowLayout`, `DefaultChoiceFace`; 3b's `ChoiceKind.TABS`; 4a's
  `FOLDER_TAB_HEIGHT`, `FOLDER_TAB_PADDING`, `FOLDER_TAB_RADIUS`.
- Produces: `final class ClassicFolderTabsFace implements ChoiceFace` (constructor `(SemanticColor surface)`); `KitFaces.tabs`
  chosen from `ControlVariants.tabs()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTabsTest.java`:

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

/** Spec 5.2's tabs point: the classic face draws folder tabs, the active one joined to the content. */
@SuppressWarnings("try")
class ClassicTabsTest {
    static Nodes.Choice tabs() {
        return new Nodes.Choice("tabs", ControlState.READY, Nodes.ChoiceKind.TABS, List.of(new Option("overview", "Overview", Optional.empty()),
            new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "");
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void folderTabsAreContentSizedAndTheActiveOneJoinsTheContent() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tabs()), events::add)) {
                if (theme == KitFixtures.light()) KitFixtures.capture(context, view.spec(), 420, 40, "folder-tabs");
                KitFixtures.render(view, 420, 40, 1);
                var overview = find(view, "tabs.option.overview", Choices.SegmentButton.class);
                var details = find(view, "tabs.option.details", Choices.SegmentButton.class);
                var pairs = context.faces().tabs.pairs();
                assertThat(overview.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.FOLDER_TAB_HEIGHT));
                assertThat(details.getX()).as("joined, no gap").isEqualTo(overview.getWidth());
                var active = paint(overview); var inactive = paint(details);
                int h = overview.getHeight(), w = overview.getWidth();
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(new Color(active.getRGB(w / 2, h - 1), true)).as(where + " no bottom edge under the active tab")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                assertThat(new Color(inactive.getRGB(details.getWidth() / 2, h - 1), true)).as(where + " the baseline under the others")
                    .isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                assertThat(new Color(inactive.getRGB(3, h / 2), true)).as(where).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_STRIPE));
                assertThat(new Color(active.getRGB(0, h / 2), true)).as(where + " side edge").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                details.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("tabs", Set.of("details")));
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheSegmentTrack() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            assertThat(context.faces().tabs).isInstanceOf(DefaultChoiceFace.class);
            release(context);
        });
    }
}
```

Replace the tabs point's classic row in `face-pairs.tsv` (the last classic `DefaultChoiceFace` row) with the folder
tabs' three:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
old = "classic\tDefaultChoiceFace\ttext.primary\tsegment.selected.bg\tCONTRAST:4.5\n"
if text.count(old) != 1: raise SystemExit("expected the tabs point's one classic default row")
new = ("classic\tClassicFolderTabsFace\ttext.primary\tsurface.card\tCONTRAST:4.5\n"
       "classic\tClassicFolderTabsFace\ttext.primary\tsurface.stripe\tCONTRAST:4.5\n"
       "classic\tClassicFolderTabsFace\tborder.control\tsurface.app\tCONTRAST:1.5\n")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicTabsTest'`
Expected: FAIL. The classic tabs are still segments: "expected: 26 but was: 36" on the tab height.

- [ ] **Step 3: Add the folder tabs face**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicFolderTabsFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * The classic tabs (spec 5.2, DESIGN_LANGUAGE §6, Settings › Plugins mock): content-sized folder tabs on a baseline in
 * {@code border.control}; the active tab {@code surface.card} and open below, joining the content; the others
 * {@code surface.stripe}; each with a small top radius.
 */
final class ClassicFolderTabsFace implements ChoiceFace {
    private final PaintPair active, inactive, edge;
    /** PREVIEWS choices ask the exclusive point's face; these tabs never draw one. */
    private final ChoiceFace previews = new DefaultChoiceFace(List.of());

    /** A face whose tabs stand on {@code surface}, the kit's dialog surface. */
    ClassicFolderTabsFace(SemanticColor surface) {
        active = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        inactive = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_STRIPE, SurfaceContrast.Guard.TEXT);
        edge = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
    }

    @Override public List<PaintPair> pairs() { return List.of(active, inactive, edge); }
    @Override public LayoutManager layout(KitContext context) { return new ClassicRadiosFace.RowLayout(context, null); }
    @Override public int trackPadding(KitContext context, boolean form) { return 0; }

    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) {
        int line = ThemePainting.metric(track.context, DesignMetrics.Role.HAIRLINE);
        g.setColor(ThemePainting.painted(track.context, edge));
        g.fillRect(0, track.getHeight() - line, track.getWidth(), line);
    }

    @Override public void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count) {
        var c = option.context;
        option.setFont(ThemePainting.font(c, Typography.Role.LABEL));
        option.setForeground(ThemePainting.painted(c, option.isSelected() ? active : inactive));
        option.setIcon(null); option.setDisabledIcon(null);
        int pad = ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_PADDING);
        option.setBorder(new EmptyBorder(0, pad, 0, pad));
        option.setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override public Dimension optionSize(Choices.SegmentButton option) {
        var c = option.context;
        int text = ThemePainting.measureText(option.getFont(), option.getText() == null ? "" : option.getText());
        return new Dimension(text + 2 * ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_PADDING), ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_HEIGHT));
    }

    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) {
        var c = option.context;
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), r = ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_RADIUS);
            double w = option.getWidth(), h = option.getHeight();
            boolean selected = option.isSelected();
            var outer = top(0, 0, w, h, r);
            var inner = top(line, line, w - 2.0 * line, h - line - (selected ? 0 : line), Math.max(0, r - line));
            var border = new Area(outer); border.subtract(new Area(inner));
            g.setColor(selected ? ThemePainting.color(c, SemanticColor.SURFACE_CARD) : ThemePainting.role(c, DerivedColor.SURFACE_STRIPE));
            g.fill(inner);
            g.setColor(ThemePainting.painted(c, edge));
            g.fill(border);
            if (!option.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            g.setFont(option.getFont()); g.setColor(option.getForeground());
            var fm = g.getFontMetrics(); String text = option.getText() == null ? "" : option.getText();
            g.drawString(text, (float) ((w - ThemePainting.measureText(option.getFont(), text)) / 2), (float) ((h - fm.getHeight()) / 2 + fm.getAscent()));
            if (option.focus != null && option.focus.visible()) {
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
                g.draw(new Rectangle2D.Double(line + .5, line + .5, w - 2.0 * line - 1, h - 2.0 * line - 1));
            }
        } finally { g.dispose(); }
    }

    /** A rectangle with only its top corners rounded by {@code r}. */
    private static Shape top(double x, double y, double w, double h, double r) {
        var path = new Path2D.Double();
        path.moveTo(x, y + h); path.lineTo(x, y + r); path.quadTo(x, y, x + r, y); path.lineTo(x + w - r, y);
        path.quadTo(x + w, y, x + w, y + r); path.lineTo(x + w, y + h); path.closePath();
        return path;
    }

    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, active); }
    @Override public LayoutManager previewLayout(KitContext context, int count) { return previews.previewLayout(context, count); }
    @Override public Dimension previewSize(PreviewChoices.Tile tile) { return previews.previewSize(tile); }
    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) { previews.paintPreview(tile, g); }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 4 step 3: the tabs point chooses its face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java", [
    ('''        tabs = new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL));''',
     '''        tabs = switch (controls.tabs()) {
            case SEGMENTS -> new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL));
            case FOLDER -> new ClassicFolderTabsFace(colors.dialogSurface());
        };''', 1)])
PY
```

The track's height is its tabs' (padding 0), so the baseline lies under the inactive tabs' own bottom edges and the
active tab paints over it with its open fill.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --continue`
Expected: PASS (`ClassicTabsTest` 2 tests, `TabsTest` and `PaintSpecTest` green).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5. Expected: `changed: none`. Open `jasper-ui/build/classic-faces/folder-tabs.png`
and compare it with Settings – Plugins' Overview and Details tabs by eye.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicFolderTabsFace.java jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicTabsTest.java jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: folder tabs

The tabs point's classic face draws content-sized folder tabs on a border.control
baseline, the active one surface.card and joined to the content, the others
surface.stripe.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 5: The group and the label policy: titled boxes and colon labels

The group point's two faces and the label policy built with the form-label point. The classic group draws a Section and
its Card as one titled box: a 1 px `border.window` edge (structural guard) whose top line the semibold title breaks
(Rulings 14), the card itself transparent, SETTINGS cards padded 8 (Rulings 19), and one `border.row` divider at a MIXED
card's content-to-settings transition (spec 6.4). The classic label policy ends FormRow labels with a colon in a label
column as wide as the card's widest label (Rulings 15, 16), gives a trailing PORT field its "Port:" label, lays a
non-Toggle SettingRow in a form card out like a FormRow (Rulings 17) and insets an unlabelled EXCLUSIVE choice to the
field column (Rulings 18).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/GroupFace.java`, `DefaultGroupFace.java`, `ClassicGroupFace.java`, `LabelPolicy.java`, `DefaultLabelPolicy.java`, `ClassicLabelPolicy.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Cards.java:29-187`, `FormRows.java:16-203`, `SettingRows.java:19-66,101-259`, `ViewReconciler.java:94`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicGroupsTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (rows inserted in `KitFaces` order)

**Interfaces:**
- Consumes: Tasks 1 to 3; 4a's `GROUP_TITLE_INSET`, `GROUP_TITLE_PADDING`, `GROUP_TITLE_GAP`, classic `FORM_LABEL_WIDTH`,
  `COLUMN_GAP`, `CARD_PADDING`, `ROW_GAP`.
- Produces: `final GroupFace KitFaces.group`, `final LabelPolicy KitFaces.labels`; `interface GroupFace extends Face {
  LayoutManager sectionLayout(Cards.SectionControl); void paintSection(Cards.SectionSurface, Graphics2D); void
  paintCard(Cards.CardSurface, Graphics2D); void styleTitle(JLabel, KitContext); Border cardBorder(KitContext,
  Nodes.CardKind, boolean page, EmptyBorder standard); }`; `interface LabelPolicy extends Face { String label(String);
  Color labelInk(KitContext, Color standard); Color settingInk(KitContext, boolean formLabel, Color standard); void
  styleCaption(Composition.WrappedText, KitContext); boolean singleCaption(); int column(KitContext, List<Integer>
  labelWidths); boolean portLabel(); Border choiceInset(KitContext, int column); }`; `Cards.SectionSurface`;
  `Cards.CardSurface.kind` and `page`; `void CardSurface.paintDefault(Graphics2D)`; `int FormRows.RowControl.column`
  and `JLabel FormRows.RowControl.port`; `boolean SettingRows.RowControl.formLabel` and `int column`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicGroupsTest.java`:

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

/** Spec 5.2's group and form-label points, with 6.4's composition rules for classic forms. */
@SuppressWarnings("try")
class ClassicGroupsTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Field field(String id, String hint, Nodes.FieldKind kind) {
        return new Nodes.Field(id, READY, kind, "", hint, id, "", Optional.empty());
    }

    /** New File Server's Server box: Name, Reachable from with its Port, Passive ports. */
    static ViewSpec server() {
        var reachable = new Nodes.Choice("reachable", READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("local", "This computer only", Optional.empty())),
            "local", "Reachable from", "reachable");
        return new ViewSpec(new Nodes.Section("server", READY, "Server", new Nodes.Card("server-card", READY, Nodes.CardKind.FORM, List.of(
            new Nodes.FormRow("name-row", READY, "Name", "", field("name", "e.g. Downloads", Nodes.FieldKind.TEXT), Optional.empty()),
            new Nodes.FormRow("reachable-row", READY, "Reachable from", "", reachable, Optional.of(field("port", "", Nodes.FieldKind.PORT))),
            new Nodes.FormRow("passive-row", READY, "Passive ports", "", field("passive", "Empty uses the range in Settings", Nodes.FieldKind.TEXT), Optional.empty())))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void aSectionAndItsCardAreOneTitledBoxWhoseTitleBreaksTheEdge() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                if (theme == KitFixtures.light()) KitFixtures.capture(context, view.spec(), 488, 150, "titled-box");
                KitFixtures.render(view, 488, 150, 1);
                var section = find(view, "server", Cards.SectionSurface.class);
                var title = find(view, "server.title", JLabel.class);
                var pairs = context.faces().group.pairs();
                var image = paint(section);
                int line = title.getHeight() / 2;
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(new Color(image.getRGB(0, section.getHeight() / 2), true)).as(where + " left edge").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(new Color(image.getRGB(section.getWidth() - 10, line), true)).as(where + " top edge").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(image.getRGB(title.getX() + 1, line) >>> 24 == 0 || !new Color(image.getRGB(title.getX() + 1, line), true).equals(ThemePainting.painted(context, pairs.get(0))))
                    .as(where + " the title breaks the edge").isTrue();
                assertThat(title.getX()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_INSET) + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_PADDING));
                assertThat(title.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                var card = find(view, "server-card", Cards.CardSurface.class);
                assertThat(paint(card).getRGB(card.getWidth() / 2, card.getHeight() - 2) >>> 24).as(where + " the card is transparent").isZero();
            } finally { release(context); }
        });
    }

    /** Rulings 16 and Review Focus 4: the label column is the widest label, at least the floor; labels end with a colon. */
    @Test void theLabelColumnIsTheWidestLabelOrTheFloor() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 150, 1);
                var name = find(view, "name-row.label", JLabel.class);
                assertThat(name.getText()).isEqualTo("Name:");
                var rows = List.of("name-row", "reachable-row", "passive-row");
                int widest = rows.stream().mapToInt(id -> find(view, id + ".label", JLabel.class).getPreferredSize().width).max().orElseThrow();
                int column = Math.max(widest, ThemePainting.metric(context, DesignMetrics.Role.FORM_LABEL_WIDTH));
                for (var id : rows) assertThat(((FormRows.RowControl) view.control(id)).column).as(id).isEqualTo(column);
                var nameField = find(view, "name", JComponent.class);
                assertThat(SwingUtilities.convertPoint(nameField.getParent(), nameField.getLocation(), find(view, "name-row", JComponent.class)).x)
                    .isEqualTo(column + ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP));
            } finally { release(context); }
        });
    }

    /** Review Focus 4: a trailing PORT field gets its own "Port:" label beside it. */
    @Test void aTrailingPortFieldHasItsOwnLabel() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 150, 1);
                var port = find(view, "reachable-row.port", JLabel.class);
                assertThat(port.getText()).isEqualTo("Port:");
                assertThat(port.isVisible()).isTrue();
                var field = find(view, "port", JComponent.class);
                assertThat(field.getX()).isGreaterThan(port.getX() + port.getWidth());
            } finally { release(context); }
        });
    }

    /** Rulings 17 and 18: a non-Toggle setting row in a form card takes the label column; a bare EXCLUSIVE choice is inset to it. */
    @Test void aSettingRowAndABareChoiceTakeTheFieldColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var group = new Nodes.Choice("group", READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("other", "Other", Optional.empty())), "other", "Group", "group");
            var mode = new Nodes.Choice("mode", READY, Nodes.ChoiceKind.EXCLUSIVE, List.of(new Option("vault", "Vault Credential", Optional.empty()),
                new Option("agent", "SSH Agent", Optional.empty())), "vault", "Authentication", "mode");
            var spec = new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(mode,
                new Nodes.FormRow("user-row", READY, "Username", "", field("user", "root", Nodes.FieldKind.TEXT), Optional.empty()),
                new Nodes.SettingRow("group-row", READY, "Group", "", group, false))));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 488, 140, 1);
                int column = ((FormRows.RowControl) view.control("user-row")).column, gap = ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP);
                var row = (SettingRows.RowControl) view.control("group-row");
                assertThat(row.formLabel).isTrue();
                assertThat(find(view, "group-row.title", JComponent.class).getAccessibleContext().getAccessibleName()).isEqualTo("Group:");
                var combo = find(view, "group", JComponent.class);
                assertThat(SwingUtilities.convertPoint(combo.getParent(), combo.getLocation(), row.component).x)
                    .isEqualTo(row.component.getInsets().left + column + gap);
                assertThat(((javax.swing.border.EmptyBorder) find(view, "mode", JComponent.class).getBorder()).getBorderInsets().left).isEqualTo(column + gap);
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsPlainLabelsAndCards() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 200, 1);
                assertThat(find(view, "name-row.label", JLabel.class).getText()).isEqualTo("Name");
                assertThat(((FormRows.RowControl) view.control("name-row")).column).isEqualTo(-1);
                assertThat(find(view, "reachable-row.port", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }
}
```

Insert the classic group and label rows in `face-pairs.tsv` after the classic tabs rows, which `KitFaces.all()` will
order as toggle, exclusive, tabs, group, labels, buttons, fields:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
anchor = "classic\tClassicFolderTabsFace\tborder.control\tsurface.app\tCONTRAST:1.5\n"
if text.count(anchor) != 1: raise SystemExit("expected the folder tabs' edge row")
rows = ("classic\tClassicGroupFace\tborder.window\tsurface.app\tCONTRAST:1.3\n"
        "classic\tClassicGroupFace\ttext.primary\tsurface.app\tCONTRAST:4.5\n"
        "classic\tClassicLabelPolicy\ttext.strong.secondary\tsurface.app\tCONTRAST:4.5\n"
        "classic\tClassicLabelPolicy\ttext.primary\tsurface.app\tCONTRAST:4.5\n"
        "classic\tClassicLabelPolicy\ttext.secondary\tsurface.app\tCONTRAST:4.5\n")
path.write_text(text.replace(anchor, anchor + rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicGroupsTest'`
Expected: FAIL at compile: "cannot find symbol … class SectionSurface", "variable group" and "variable column".

- [ ] **Step 3: Add the faces and route sections, cards and rows through them**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/GroupFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** The group point (spec 5.2): a Section over its Card, as a section label and a card, or one titled box. */
interface GroupFace extends Face {
    /** The section's layout of its title and body. */
    LayoutManager sectionLayout(Cards.SectionControl section);
    /** Paints the section behind its title and body (the classic titled box), or nothing. */
    void paintSection(Cards.SectionSurface surface, Graphics2D g);
    /** Paints a card's surface and dividers. */
    void paintCard(Cards.CardSurface card, Graphics2D g);
    /** Styles the section's title. */
    void styleTitle(JLabel title, KitContext context);
    /** A {@code kind} card's padding, {@code standard} being the default look's. */
    Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultGroupFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** The default group: a tracked section label above a rounded card. */
final class DefaultGroupFace implements GroupFace {
    @Override public LayoutManager sectionLayout(Cards.SectionControl section) {
        return new Composition.Vertical(ThemePainting.metric(section.context(), DesignMetrics.Role.SECTION_LABEL_GAP));
    }
    @Override public void paintSection(Cards.SectionSurface surface, Graphics2D g) { }
    @Override public void paintCard(Cards.CardSurface card, Graphics2D g) { card.paintDefault(g); }
    @Override public void styleTitle(JLabel title, KitContext context) { Composition.style(title, context, Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY); }
    @Override public Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard) { return standard; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicGroupFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * The classic group (spec 6.4, DESIGN_LANGUAGE §6, New File Server mock): a Section and its Card as one titled box, a
 * 1 px {@code border.window} edge whose top line the semibold title breaks; the card inside is transparent, a SETTINGS
 * card pads its rows 8 above and below, a MIXED card draws one {@code border.row} divider where its content gives way to
 * its settings, and a Settings page's card divides every row.
 */
final class ClassicGroupFace implements GroupFace {
    private final PaintPair box, title;

    /** A face whose boxes stand on {@code surface}, the kit's dialog surface. */
    ClassicGroupFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL);
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(box, title); }

    @Override public LayoutManager sectionLayout(Cards.SectionControl section) { return new TitledBoxLayout(section.context()); }

    @Override public void styleTitle(JLabel label, KitContext context) {
        label.setFont(ThemePainting.font(context, Typography.Role.SECTION_LABEL));
        label.setForeground(ThemePainting.painted(context, title));
    }

    @Override public void paintSection(Cards.SectionSurface s, Graphics2D g) {
        if (s.getComponentCount() < 2) return;
        var c = s.context; var label = s.getComponent(0);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), top = label.getHeight() / 2, pad = ThemePainting.metric(c, DesignMetrics.Role.GROUP_TITLE_PADDING);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CARD_RADIUS);
        ThemePainting.antialias(g);
        var edge = new Area(new RoundRectangle2D.Double(0, top, s.getWidth(), s.getHeight() - top, 2 * radius, 2 * radius));
        edge.subtract(new Area(new RoundRectangle2D.Double(line, top + line, s.getWidth() - 2.0 * line, s.getHeight() - top - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line))));
        // The title breaks the top line: the gap holds the title and its padding on either side.
        if (label.isVisible() && label.getWidth() > 0) edge.subtract(new Area(new Rectangle(label.getX() - pad, 0, label.getWidth() + 2 * pad, top + 2 * line)));
        g.setColor(ThemePainting.painted(c, box));
        g.fill(edge);
    }

    @Override public void paintCard(Cards.CardSurface card, Graphics2D g) {
        var c = card.context;
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), pad = ThemePainting.metric(c, DesignMetrics.Role.CARD_PADDING);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_ROW));
        Component previous = null; boolean divided = false;
        for (var child : card.getComponents()) if (child.isVisible()) {
            boolean setting = card.settings.contains(child), before = previous != null && card.settings.contains(previous);
            if (previous != null && card.page) g.fillRect(0, child.getY(), card.getWidth(), line);
            else if (previous != null && !divided && card.kind == Nodes.CardKind.MIXED && setting && !before) {
                g.fillRect(pad, child.getY(), Math.max(0, card.getWidth() - 2 * pad), line);
                divided = true;
            }
            previous = child;
        }
    }

    @Override public Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard) {
        if (kind != Nodes.CardKind.SETTINGS || page) return standard;
        int gap = ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP);
        return new EmptyBorder(gap, 0, gap, 0);
    }

    /** The title at the box's title inset; the body from just below the title's middle, where the box's top line runs. */
    static final class TitledBoxLayout implements Composition.WidthLayout {
        private final KitContext context;
        TitledBoxLayout(KitContext context) { this.context = context; }
        int x() { return ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_INSET) + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_PADDING); }
        int offset(Component title) { return title.getPreferredSize().height / 2 + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_GAP); }
        @Override public int height(Container p, int width) {
            return p.getComponentCount() < 2 ? 0 : offset(p.getComponent(0)) + Composition.height(p.getComponent(1), width);
        }
        @Override public Dimension preferredLayoutSize(Container p) {
            int w = p.getComponentCount() < 2 ? 0 : Math.max(p.getComponent(1).getPreferredSize().width, 2 * x() + p.getComponent(0).getPreferredSize().width);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }
        @Override public void layoutContainer(Container p) {
            if (p.getComponentCount() < 2) return;
            var title = p.getComponent(0); var size = title.getPreferredSize();
            title.setBounds(x(), 0, Math.min(size.width, Math.max(0, p.getWidth() - 2 * x())), size.height);
            int top = offset(title);
            p.getComponent(1).setBounds(0, top, p.getWidth(), Composition.height(p.getComponent(1), p.getWidth()));
        }
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/LabelPolicy.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;

/** The form-label point (spec 5.2): how FormRow labels, captions and columns read. */
interface LabelPolicy extends Face {
    /** A FormRow label's text for the descriptor's {@code label}. */
    String label(String label);
    /** A FormRow label's colour, {@code standard} being the default look's. */
    Color labelInk(KitContext context, Color standard);
    /** A SettingRow title's colour: as a label in the label column when {@code formLabel}, otherwise as a setting's title. */
    Color settingInk(KitContext context, boolean formLabel, Color standard);
    /** Styles a FormRow caption. */
    void styleCaption(Composition.WrappedText caption, KitContext context);
    /** Whether a caption is one line, shortened at its end. */
    boolean singleCaption();
    /** A card's shared label column for its labels' widths, or -1 to keep each row's fixed column. */
    int column(KitContext context, List<Integer> labelWidths);
    /** Whether a trailing PORT field gets its own "Port:" label. */
    boolean portLabel();
    /** The inset an unlabelled EXCLUSIVE choice in a card takes to reach the field column, or null for none. */
    Border choiceInset(KitContext context, int column);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultLabelPolicy.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;

/** The default labels: plain, in a fixed column, with one-line captions. */
final class DefaultLabelPolicy implements LabelPolicy {
    @Override public String label(String label) { return label; }
    @Override public Color labelInk(KitContext context, Color standard) { return standard; }
    @Override public Color settingInk(KitContext context, boolean formLabel, Color standard) { return standard; }
    @Override public void styleCaption(Composition.WrappedText caption, KitContext context) {
        Composition.style(caption, context, Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);
    }
    @Override public boolean singleCaption() { return true; }
    @Override public int column(KitContext context, List<Integer> labelWidths) { return -1; }
    @Override public boolean portLabel() { return false; }
    @Override public Border choiceInset(KitContext context, int column) { return null; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicLabelPolicy.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.List;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * The classic labels (spec 5.2 and 6.4, DESIGN_LANGUAGE §6 and §10): each ends with a colon, in a left column as wide as
 * the card's widest label and at least {@code FORM_LABEL_WIDTH}; a caption sits under its label in the muted description
 * face and may wrap; a trailing PORT field gets its own "Port:" label.
 */
final class ClassicLabelPolicy implements LabelPolicy {
    private final PaintPair label, title, caption;

    /** A policy whose labels stand on {@code surface}, the kit's dialog surface. */
    ClassicLabelPolicy(SemanticColor surface) {
        label = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        caption = new PaintPair(SemanticColor.TEXT_SECONDARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(label, title, caption); }
    @Override public String label(String text) { return text.isBlank() || text.endsWith(":") ? text : text + ":"; }
    @Override public Color labelInk(KitContext context, Color standard) { return ThemePainting.painted(context, label); }
    @Override public Color settingInk(KitContext context, boolean formLabel, Color standard) { return ThemePainting.painted(context, formLabel ? label : title); }
    @Override public void styleCaption(Composition.WrappedText text, KitContext context) {
        text.setFont(ThemePainting.font(context, Typography.Role.DESCRIPTION));
        text.setForeground(ThemePainting.painted(context, caption));
    }
    @Override public boolean singleCaption() { return false; }
    @Override public int column(KitContext context, List<Integer> labelWidths) {
        return Math.max(ThemePainting.metric(context, DesignMetrics.Role.FORM_LABEL_WIDTH), labelWidths.stream().mapToInt(Integer::intValue).max().orElse(0));
    }
    @Override public boolean portLabel() { return true; }
    @Override public Border choiceInset(KitContext context, int column) {
        return column < 0 ? null : new EmptyBorder(0, column + ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP), 0, 0);
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 5 step 3: sections, cards, form rows and setting rows ask the group face and the label policy."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final ChoiceFace exclusive, tabs;
''', '''    final ChoiceFace exclusive, tabs;
    final GroupFace group;
    final LabelPolicy labels;
''', 1),
    ('''            case FOLDER -> new ClassicFolderTabsFace(colors.dialogSurface());
        };''', '''            case FOLDER -> new ClassicFolderTabsFace(colors.dialogSurface());
        };
        group = switch (controls.group()) {
            case CARD -> new DefaultGroupFace();
            case TITLED_BOX -> new ClassicGroupFace(colors.dialogSurface());
        };
        labels = switch (controls.formLabel()) {
            case PLAIN -> new DefaultLabelPolicy();
            case COLON -> new ClassicLabelPolicy(colors.dialogSurface());
        };''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, buttons, fields); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, buttons, fields); }''', 1)])
edit(S + "Cards.java", [
    ('''            component.setBorder(new EmptyBorder(pad, pad, bottom, pad));''',
     '''            component.setBorder(context().faces().group.cardBorder(context(), ((Nodes.Card) node).kind(), settings, new EmptyBorder(pad, pad, bottom, pad)));
            ((CardSurface) component).kind = ((Nodes.Card) node).kind();
            ((CardSurface) component).page = settings;''', 1),
    ('''            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE).forEach(c -> Composition.label(c, true));''',
     '''            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE).forEach(c -> Composition.label(c, true));
            labelColumn(controls);''', 1),
    ('''    static final class MixedLayout implements Composition.WidthLayout {''',
     '''    /**
     * The label policy's shared column for a form card (spec 6.4): its FormRows and non-Toggle SettingRows take it, and a
     * bare EXCLUSIVE choice is inset to the field column; the default policy keeps each row's fixed column.
     */
    private static void labelColumnFor(CardControl card, List<ControlRegistry.Control> controls) {
        var labels = card.context().faces().labels;
        boolean form = card.fullForm || controls.stream().anyMatch(c -> c instanceof FormRows.RowControl);
        var widths = new java.util.ArrayList<Integer>();
        for (var c : controls) {
            if (c instanceof FormRows.RowControl row) widths.add(row.label.getPreferredSize().width);
            if (c instanceof SettingRows.RowControl row && !(((Nodes.SettingRow) row.node).control() instanceof Nodes.Toggle))
                widths.add(ThemePainting.measureText(row.title.getFont(), labels.label(((Nodes.SettingRow) row.node).title())));
        }
        int column = form ? labels.column(card.context(), widths) : -1;
        for (var c : controls) {
            if (c instanceof FormRows.RowControl row && row.column != column) { row.column = column; row.component.revalidate(); }
            if (c instanceof SettingRows.RowControl row && !(((Nodes.SettingRow) row.node).control() instanceof Nodes.Toggle)) {
                boolean asRow = column >= 0;
                if (row.formLabel != asRow || row.column != column) { row.formLabel = asRow; row.column = column; row.refresh(); }
            }
            if (c.node instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.EXCLUSIVE) {
                var inset = labels.choiceInset(card.context(), column);
                if (inset != null || c.component.getBorder() != null) c.component.setBorder(inset);
            }
        }
    }

    static final class MixedLayout implements Composition.WidthLayout {''', 1),
    ('''        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice''',
     '''        private void labelColumn(List<ControlRegistry.Control> controls) { labelColumnFor(this, controls); }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            controls.stream().filter(c -> c.node instanceof Nodes.Choice choice''', 1),
    ('''    static final class CardSurface extends JPanel {
        List<JComponent> settings = List.of();''',
     '''    static final class CardSurface extends JPanel {
        List<JComponent> settings = List.of();
        /** The card's kind and whether it is on a Settings page, which the group face reads to place its dividers. */
        Nodes.CardKind kind = Nodes.CardKind.FORM;
        boolean page;''', 1),
    ('''            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);''',
     '''            var g = (Graphics2D) graphics.create();
            try { context.faces().group.paintCard(this, g); } finally { g.dispose(); }
        }

        /** The rounded card, its edge and its setting rows' dividers (the default face). */
        void paintDefault(Graphics2D g) {
            {
                ThemePainting.antialias(g);
                int r = ThemePainting.metric(context, DesignMetrics.Role.CARD_RADIUS);''', 1),
    ('''                    previous = child;
                }
            } finally {
                g.dispose();
            }
        }
    }

    static final class SectionControl''',
     '''                    previous = child;
                }
            }
        }
    }

    /** A section's surface: the classic group face paints its titled box here; the default face paints nothing. */
    static final class SectionSurface extends JPanel {
        final KitContext context;

        SectionSurface(KitContext context) {
            this.context = context;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try { context.faces().group.paintSection(this, g); } finally { g.dispose(); }
        }
    }

    static final class SectionControl''', 1),
    ('''            super(view, node, new JPanel());
            component.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");''',
     '''            super(view, node, new SectionSurface(view.context()));
            component.setOpaque(false);
            KitOwnership.mark(title, node.id() + ".title");''', 1),
    ('''            Composition.style(
                    title, context(), Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
            component.setLayout(
                    new Composition.Vertical(
                            ThemePainting.metric(context(), DesignMetrics.Role.SECTION_LABEL_GAP)));''',
     '''            context().faces().group.styleTitle(title, context());
            component.setLayout(context().faces().group.sectionLayout(this));''', 1)])
edit(S + "FormRows.java", [
    ('''        final Composition.WrappedText caption;
        java.util.List<ControlRegistry.Control> controls = java.util.List.of();''',
     '''        final Composition.WrappedText caption;
        java.util.List<ControlRegistry.Control> controls = java.util.List.of();
        /** The card's shared label column (the label policy's), or -1 for the fixed FORM_LABEL_WIDTH column. */
        int column = -1;
        /** A trailing PORT field's own label, mounted only when the label policy gives one. */
        final JLabel port = Labels.literal();''', 1),
    ('''            KitOwnership.mark(label, node.id() + ".label");''',
     '''            KitOwnership.mark(label, node.id() + ".label");
            KitOwnership.mark(port, node.id() + ".port");''', 1),
    ('''            label.setText(row.label());''', '''            label.setText(context().faces().labels.label(row.label()));''', 1),
    ('''            caption.single = true;''', '''            caption.single = context().faces().labels.singleCaption();''', 1),
    ('''            Composition.style(
                    caption, context(), Typography.Role.CAPTION, SemanticColor.TEXT_TERTIARY);''',
     '''            label.setForeground(context().faces().labels.labelInk(context(), label.getForeground()));
            context().faces().labels.styleCaption(caption, context());
            port.setText(context().faces().labels.label("Port")); port.setFont(label.getFont()); port.setForeground(label.getForeground());
            port.setEnabled(component.isEnabled());''', 1),
    ('''            Stacks.mount(
                    component,
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(label, caption),
                                    controls.stream().map(c -> c.component))
                            .toList());''',
     '''            boolean ported = controls.size() > 1 && controls.get(1).node instanceof Nodes.Field field && field.kind() == Nodes.FieldKind.PORT
                && context().faces().labels.portLabel();
            Stacks.mount(
                    component,
                    java.util.stream.Stream.concat(java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(label, caption),
                                    controls.stream().map(c -> c.component)),
                                    ported ? java.util.stream.Stream.of((JComponent) port) : java.util.stream.Stream.<JComponent>empty())
                            .toList());''', 1),
    ('''        int labelWidth() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH);
        }''',
     '''        int labelWidth() {
            return owner.column >= 0 ? owner.column : ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH);
        }

        /** The "Port:" label's width and its gap, when it is mounted after the trailing field. */
        int portWidth(Container p) {
            return p.getComponentCount() > 4 && p.getComponent(4) == owner.port ? owner.port.getPreferredSize().width + gap() : 0;
        }''', 1),
    ('''                            p.getComponent(3).getPreferredSize().width,
''', '''                            p.getComponent(3).getPreferredSize().width + portWidth(p),
''', 1),
    ('''            if (p.getComponentCount() > 3)
                h = Math.max(h, Composition.height(p.getComponent(3), trailing) + shift);''',
     '''            if (p.getComponentCount() > 3)
                h = Math.max(h, Composition.height(p.getComponent(3), trailing - portWidth(p)) + shift);''', 1),
    ('''            if (p.getComponentCount() > 3) {
                int th = Composition.height(p.getComponent(3), trailing);
                // Shorter trailing content (a unit or note) centres on the control's first line as the label does.
                p.getComponent(3).setBounds(x + w + gap(), shift + (th < line ? (line - th) / 2 : 0), trailing, th);
            }''',
     '''            if (p.getComponentCount() > 3) {
                int ported = portWidth(p), tx = x + w + gap();
                if (ported > 0) {
                    var size = owner.port.getPreferredSize();
                    owner.port.setBounds(tx, shift + Math.max(0, (line - size.height) / 2), size.width, size.height);
                }
                int th = Composition.height(p.getComponent(3), trailing - ported);
                // Shorter trailing content (a unit or note) centres on the control's first line as the label does.
                p.getComponent(3).setBounds(tx + ported, shift + (th < line ? (line - th) / 2 : 0), trailing - ported, th);
            }''', 1)])
edit(S + "SettingRows.java", [
    ('''        boolean panelFooter, prompt, fullForm, detail, settings, grouped;''',
     '''        boolean panelFooter, prompt, fullForm, detail, settings, grouped;
        /** In a classic form card: the title is a colon label in the card's column and the control sits at the field column (spec 6.4). */
        boolean formLabel;
        /** That shared column, or -1. */
        int column = -1;''', 1),
    ('''            title.text(row.title() + (row.titleNote().isEmpty() ? "" : " · " + row.titleNote()));''',
     '''            title.text((formLabel ? context().faces().labels.label(row.title()) : row.title()) + (row.titleNote().isEmpty() ? "" : " · " + row.titleNote()));''', 1),
    ('''            if (row.highlighted()) {
                title.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONSELECTION));''',
     '''            if (!detail) title.setForeground(context().faces().labels.settingInk(context(), formLabel, title.getForeground()));
            if (row.highlighted()) {
                title.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ONSELECTION));''', 1),
    ('''        @Override
        public int height(Container p, int width) {
            if (leading(p)) return leadingHeight(p, width);''',
     '''        int column() { return owner.column >= 0 ? owner.column : ThemePainting.metric(owner.context(), DesignMetrics.Role.FORM_LABEL_WIDTH); }

        /** A form-card row: the title (and description) in the label column, the control at the field column at its own width. */
        boolean formRow(Container p) { return owner.formLabel && p.getComponentCount() > 2 && !leading(p); }

        int formHeight(Container p, int width) {
            Insets i = p.getInsets(); int col = column();
            int text = owner.title.height(col) + (owner.description.isVisible()
                ? owner.description.height(col) + ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0);
            var control = p.getComponent(2); int room = Math.max(0, width - i.left - i.right - col - gap());
            return i.top + i.bottom + Math.max(text, Composition.height(control, Math.min(room, control.getPreferredSize().width)));
        }

        void layoutForm(Container p) {
            Insets i = p.getInsets(); int col = column(); var control = p.getComponent(2);
            int room = Math.max(0, p.getWidth() - i.left - i.right - col - gap()), cw = Math.min(room, control.getPreferredSize().width);
            int ch = Composition.height(control, cw), th = owner.title.height(col);
            int line = Math.min(ch, ThemePainting.height(owner.context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY));
            int y = i.top + Math.max(0, (line - th) / 2);
            owner.title.setBounds(i.left, y, col, th);
            int gap = owner.description.isVisible() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.SETTING_DESCRIPTION_GAP) : 0;
            owner.description.setBounds(i.left, y + th + gap, col, owner.description.isVisible() ? owner.description.height(col) : 0);
            control.setBounds(i.left + col + gap(), i.top, cw, ch);
        }

        @Override
        public int height(Container p, int width) {
            if (leading(p)) return leadingHeight(p, width);
            if (formRow(p)) return formHeight(p, width);''', 1),
    ('''                return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
            }
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;''',
     '''                return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
            }
            if (formRow(p)) {
                int w = p.getInsets().left + p.getInsets().right + column() + gap() + p.getComponent(2).getPreferredSize().width;
                return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
            }
            int control = p.getComponentCount() > 2 ? p.getComponent(2).getPreferredSize().width : 0;''', 1),
    ('''            if (leading(p)) { layoutLeading(p); return; }''',
     '''            if (leading(p)) { layoutLeading(p); return; }
            if (formRow(p)) { layoutForm(p); return; }''', 1)])
edit(S + "ViewReconciler.java", [
    ('''        if (control instanceof SettingRows.RowControl row) row.panelFooter = false;''',
     '''        if (control instanceof SettingRows.RowControl row) { row.panelFooter = false; row.formLabel = false; row.column = -1; }
        if (control instanceof FormRows.RowControl row) row.column = -1;''', 1)])
PY
```

`Cards.labelColumnFor` runs from `CardControl.children`, after every control's `refresh` (the reconciler mounts children
last), so a row's label text and font are current when it is measured.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicGroupsTest` 5 tests; `FormsTest`, `GroupedFormGeometryTest`, `SettingsKitTest`,
`SettingsControlsTest`, `PanelCompositionTest` unchanged).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5. Expected: `changed: none`. Open `jasper-ui/build/classic-faces/titled-box.png`
and compare it with New File Server – FTP's Server box by eye (title on the line, colon labels, the Port label).

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicGroupsTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: titled boxes and colon labels

The group point's classic face draws a Section and its Card as one titled box whose
top line the title breaks, with one row divider at a MIXED card's settings; the
default keeps the section label over a card (its code moved into named methods).
The label policy ends classic labels with a colon in a column as wide as the
card's widest label, labels a trailing Port field, lays form-card settings out as
rows and insets a bare exclusive choice to the field column.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 6: The stepper: spinners

The stepper point's two faces. The default is today's "− value +" stepper and the slider; the classic face is a spinner
(DESIGN_LANGUAGE §6): the value right-aligned in the data face with its unit muted, a 16 px column of stacked ▲ and ▼
step buttons with the button gradient and `border.divider` rules, the whole 82 px wide; a SLIDER is built as the same
spinner (Rulings 20).

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/StepperFace.java`, `DefaultStepperFace.java`, `ClassicStepperFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `NumericControls.java:13-93`, `ControlRegistry.java:33`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicSpinnersTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (rows inserted in `KitFaces` order)

**Interfaces:**
- Consumes: Tasks 1 to 5; 4a's `SPINNER_WIDTH`, `SPINNER_ARROW_WIDTH`, `SPINNER_GLYPH`.
- Produces: `final StepperFace KitFaces.stepper`; `interface StepperFace extends Face { void arrange(JPanel surface,
  JButton decrease, JButton increase); int alignment(); Dimension stepSize(KitContext, int direction); void
  paintStep(JButton, Graphics2D, KitContext, int direction, boolean focus, Runnable standard); void styleValue(KitContext,
  JComponent value, JLabel unit); int valueWidth(KitContext); Insets surfaceInsets(KitContext); boolean slider(); }`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicSpinnersTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's stepper point: the classic face is a spinner, and a SLIDER draws the same. */
@SuppressWarnings("try")
class ClassicSpinnersTest {
    static Nodes.Field number(String id, Nodes.FieldPresentation presentation) {
        return new Nodes.Field(id, ControlState.READY, Nodes.FieldKind.NUMBER, "34", "px", "Toolbar height", "", Optional.of(new Nodes.Numeric(28, 64, 1, 0)), presentation);
    }

    @Test void aSpinnerIsARightAlignedDataValueWithStackedArrows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(number("height", Nodes.FieldPresentation.STEPPER)), e -> {})) {
                KitFixtures.capture(context, view.spec(), 200, 40, "spinner");
                KitFixtures.render(view, 200, 40, 1);
                var up = find(view, "height.plus", JButton.class); var down = find(view, "height.minus", JButton.class);
                assertThat(up.getX()).as("stacked").isEqualTo(down.getX());
                assertThat(up.getY()).isLessThan(down.getY());
                assertThat(up.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH));
                var input = (JTextField) ((TextFields.FieldControl) view.control("height")).input;
                assertThat(input.getHorizontalAlignment()).isEqualTo(SwingConstants.RIGHT);
                assertThat(input.getFont()).isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.MONO));
                up.doClick(0);
                assertThat(input.getText()).isEqualTo("35");
            } finally { release(context); }
        });
    }

    /** Review Focus 5: a SLIDER under the classic kit is a spinner whose arrows step the value. */
    @Test void aSliderIsASpinnerUnderTheClassicKit() {
        Edt.run(() -> {
            var classic = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var standard = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(classic, new ViewSpec(number("opacity", Nodes.FieldPresentation.SLIDER)), e -> {});
                 var plain = new KitView(standard, new ViewSpec(number("opacity", Nodes.FieldPresentation.SLIDER)), e -> {})) {
                KitFixtures.render(view, 200, 40, 1); KitFixtures.render(plain, 200, 40, 1);
                assertThat(view.control("opacity")).isInstanceOf(NumericControls.Stepper.class);
                assertThat(plain.control("opacity")).isInstanceOf(NumericControls.Slider.class);
                find(view, "opacity.minus", JButton.class).doClick(0);
                assertThat(((TextFields.FieldControl) view.control("opacity")).input.getText()).isEqualTo("33");
            } finally { release(classic); release(standard); }
        });
    }

    @Test void theDefaultKitKeepsTheStepper() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, new ViewSpec(number("height", Nodes.FieldPresentation.STEPPER)), e -> {})) {
                KitFixtures.render(view, 200, 40, 1);
                JButton minus = find(view, "height.minus", JButton.class), plus = find(view, "height.plus", JButton.class);
                assertThat(minus.getX()).as("minus leads, plus trails").isLessThan(plus.getX());
                assertThat(minus.getText()).isEqualTo("−");
            } finally { release(context); }
        });
    }
}
```

Insert the classic stepper rows after the label policy's in `face-pairs.tsv`:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
text = path.read_text(encoding="utf-8")
anchor = "classic\tClassicLabelPolicy\ttext.secondary\tsurface.app\tCONTRAST:4.5\n"
if text.count(anchor) != 1: raise SystemExit("expected the label policy's caption row")
rows = ("classic\tClassicStepperFace\ttext.secondary\tbutton.secondary.bg\tCONTRAST:3.0\n"
        "classic\tClassicStepperFace\ttext.placeholder\tsurface.input\tCONTRAST:4.5\n")
path.write_text(text.replace(anchor, anchor + rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicSpinnersTest'`
Expected: FAIL at compile: "cannot find symbol … variable stepper" in `KitFaces`.

- [ ] **Step 3: Add the faces and route the stepper and the slider through them**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/StepperFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The stepper point (spec 5.2): a STEPPER (and a SLIDER) field as "− value +", or as a spinner. */
interface StepperFace extends Face {
    /** Places the decrease and increase buttons on the field's surface. */
    void arrange(JPanel surface, JButton decrease, JButton increase);
    /** The value's horizontal alignment in its cell. */
    int alignment();
    /** A step button's size. */
    Dimension stepSize(KitContext context, int direction);
    /** Paints a step button; {@code standard} paints the default look's. */
    void paintStep(JButton button, Graphics2D g, KitContext context, int direction, boolean focus, Runnable standard);
    /** Styles the value and its unit. */
    void styleValue(KitContext context, JComponent value, JLabel unit);
    /** The value cell's least width. */
    int valueWidth(KitContext context);
    /** The field surface's insets round the value and the buttons. */
    Insets surfaceInsets(KitContext context);
    /** Whether a SLIDER field is a slider; otherwise it is built as this stepper. */
    boolean slider();
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultStepperFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/** The default stepper: "−" leading and "+" trailing a centred value; a SLIDER is a slider. */
final class DefaultStepperFace implements StepperFace {
    @Override public void arrange(JPanel surface, JButton decrease, JButton increase) {
        surface.add(decrease, BorderLayout.WEST); surface.add(increase, BorderLayout.EAST);
    }
    @Override public int alignment() { return SwingConstants.CENTER; }
    @Override public Dimension stepSize(KitContext context, int direction) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.STEPPER_BUTTON_WIDTH),
            ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY));
    }
    @Override public void paintStep(JButton button, Graphics2D g, KitContext context, int direction, boolean focus, Runnable standard) { standard.run(); }
    @Override public void styleValue(KitContext context, JComponent value, JLabel unit) {
        // A compact value reads at label size, its unit set after one space.
        value.setFont(ThemePainting.font(context, Typography.Role.LABEL));
        unit.setFont(value.getFont()); unit.setForeground(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
    }
    @Override public int valueWidth(KitContext context) { return ThemePainting.metric(context, DesignMetrics.Role.STEPPER_VALUE_WIDTH); }
    @Override public Insets surfaceInsets(KitContext context) { return new Insets(0, 0, 0, 0); }
    @Override public boolean slider() { return true; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicStepperFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/**
 * The classic spinner (DESIGN_LANGUAGE §6, Settings › Window mock): a right-aligned data-face value with its unit muted,
 * and a narrow column of stacked ▲ and ▼ step buttons on the button gradient, ruled off in {@code border.divider}.
 */
final class ClassicStepperFace implements StepperFace {
    private final PaintPair arrow = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.INDICATOR);
    private final PaintPair unit = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);

    @Override public List<PaintPair> pairs() { return List.of(arrow, unit); }

    @Override public void arrange(JPanel surface, JButton decrease, JButton increase) {
        var column = new JPanel(new GridLayout(2, 1, 0, 0));
        column.setOpaque(false);
        column.add(increase); column.add(decrease);
        surface.add(column, BorderLayout.EAST);
    }

    @Override public int alignment() { return SwingConstants.RIGHT; }

    @Override public Dimension stepSize(KitContext context, int direction) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH),
            ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY) / 2);
    }

    @Override public void paintStep(JButton button, Graphics2D g, KitContext c, int direction, boolean focus, Runnable standard) {
        ThemePainting.antialias(g);
        int w = button.getWidth(), h = button.getHeight(), line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        Color top = ThemePainting.color(c, SemanticColor.BUTTON_SECONDARY_BG), bottom = ThemePainting.role(c, DerivedColor.BUTTON_END);
        g.setPaint(button.getModel().isPressed() ? new GradientPaint(0, 0, bottom, 0, h, top) : new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_DIVIDER));
        g.fillRect(0, 0, line, h);
        if (direction < 0) g.fillRect(0, 0, w, line);
        if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
        int glyph = ThemePainting.metric(c, DesignMetrics.Role.SPINNER_GLYPH);
        double y = (h - glyph * .8) / 2;
        if (direction > 0) ClassicGlyphs.up(g, (w + line) / 2.0, y, glyph, ThemePainting.painted(c, arrow));
        else ClassicGlyphs.down(g, (w + line) / 2.0, y, glyph, ThemePainting.painted(c, arrow));
        if (focus) {
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
            g.drawRect(line, 0, w - line - 1, h - 1);
        }
    }

    @Override public void styleValue(KitContext context, JComponent value, JLabel label) {
        value.setFont(ThemePainting.font(context, Typography.Role.MONO));
        label.setFont(value.getFont()); label.setForeground(ThemePainting.painted(context, unit));
    }

    @Override public int valueWidth(KitContext context) {
        return ThemePainting.metric(context, DesignMetrics.Role.SPINNER_WIDTH) - ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH);
    }

    /** The arrow column stays inside the field's 1 px outline on its top, bottom and trailing sides. */
    @Override public Insets surfaceInsets(KitContext context) {
        int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        return new Insets(line, 0, line, line);
    }

    @Override public boolean slider() { return false; }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 6 step 3: the stepper and the slider ask the stepper face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final LabelPolicy labels;
''', '''    final LabelPolicy labels;
    final StepperFace stepper;
''', 1),
    ('''            case COLON -> new ClassicLabelPolicy(colors.dialogSurface());
        };''', '''            case COLON -> new ClassicLabelPolicy(colors.dialogSurface());
        };
        stepper = switch (controls.stepper()) {
            case STEPPER -> new DefaultStepperFace();
            case SPINNER -> new ClassicStepperFace();
        };''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, buttons, fields); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, stepper, buttons, fields); }''', 1)])
edit(S + "ControlRegistry.java", [
    ('''            case Nodes.Field field -> field.presentation() == Nodes.FieldPresentation.STEPPER ? new NumericControls.Stepper(view, field) : field.presentation() == Nodes.FieldPresentation.SLIDER ? new NumericControls.Slider(view, field) : new TextFields.FieldControl(view, field);''',
     '''            case Nodes.Field field -> field.presentation() == Nodes.FieldPresentation.SLIDER && view.context().faces().stepper.slider() ? new NumericControls.Slider(view, field)
                : field.presentation() == Nodes.FieldPresentation.STEPPER || field.presentation() == Nodes.FieldPresentation.SLIDER ? new NumericControls.Stepper(view, field)
                : new TextFields.FieldControl(view, field);''', 1)])
edit(S + "NumericControls.java", [
    ('''            ((JTextField)input).setHorizontalAlignment(SwingConstants.CENTER);''',
     '''            ((JTextField)input).setHorizontalAlignment(context().faces().stepper.alignment());''', 1),
    ('''            surface.add(minus, BorderLayout.WEST); surface.add(plus, BorderLayout.EAST);''',
     '''            context().faces().stepper.arrange(surface, minus, plus);''', 1),
    ('''                return new Dimension(Math.max(ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_VALUE_WIDTH), valueWidth() + unitWidth() + 2 * pad), input.getPreferredSize().height);''',
     '''                return new Dimension(Math.max(context().faces().stepper.valueWidth(context()), valueWidth() + unitWidth() + 2 * pad), input.getPreferredSize().height);''', 1),
    ('''                int value = valueWidth(), unitWidth = unitWidth(), x = Math.max(0, (parent.getWidth() - value - unitWidth) / 2);''',
     '''                int value = valueWidth(), unitWidth = unitWidth();
                // The default centres the value and its unit; the classic spinner sets them against the arrows.
                int x = context().faces().stepper.alignment() == SwingConstants.RIGHT
                    ? Math.max(0, parent.getWidth() - value - unitWidth - ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_VALUE_PADDING))
                    : Math.max(0, (parent.getWidth() - value - unitWidth) / 2);''', 1),
    ('''                @Override public Dimension getPreferredSize() { return new Dimension(ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_BUTTON_WIDTH), ThemePainting.height(context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)); }
                @Override protected void paintComponent(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try {''',
     '''                @Override public Dimension getPreferredSize() { return context().faces().stepper.stepSize(context(), direction); }
                @Override protected void paintComponent(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try { context().faces().stepper.paintStep(this, g, context(), direction, cue[0] != null && cue[0].visible(), () -> standard(g)); }
                    finally { g.dispose(); }
                }
                /** The default look's step button: its focus edge, its rule and its "−" or "+". */
                private void standard(Graphics2D g) {
                    {''', 1),
    ('''                        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    } finally { g.dispose(); }
                }''',
     '''                        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    }
                }''', 1),
    ('''            surface.hint = ""; surface.setBorder(new EmptyBorder(0, 0, 0, 0));
            // A compact value reads at label size, its unit set after one space.
            input.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); ((JTextField)input).setBorder(new EmptyBorder(0, 0, 0, 0));
            unit.setText(field.hint().isBlank() ? "" : " " + field.hint());
            unit.setFont(input.getFont()); unit.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY));''',
     '''            var insets = context().faces().stepper.surfaceInsets(context());
            surface.hint = ""; surface.setBorder(new EmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
            ((JTextField)input).setBorder(new EmptyBorder(0, 0, 0, 0));
            unit.setText(field.hint().isBlank() ? "" : " " + field.hint());
            context().faces().stepper.styleValue(context(), input, unit);''', 1)])
PY
```

The default step button's former paint body is kept in `standard(Graphics2D)` (an inner `{ … }` block); the anonymous
button's `direction` and `cue` are the enclosing `button(...)` method's effectively final locals.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicSpinnersTest` 3 tests; `SettingsControlsTest` and the numeric suites unchanged).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5. Expected: `changed: none`. Open `jasper-ui/build/classic-faces/spinner.png`
and compare it with Settings – Window's Toolbar height spinner by eye.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicSpinnersTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: spinners

The stepper point's classic face is a spinner: a right-aligned data-face value with
its unit muted and a column of stacked arrows on the button gradient; a SLIDER is
built as the same spinner. The default keeps the stepper and the slider (its step
painting moved into a named method).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 7: The prompt: the plain classic prompt

The prompt point's two faces (spec 5.2's prompt row as amended). The default is today's PROMPT header: the 54 px tile
over the prompt title and description, centred. The classic face hides the `PROMPT_TITLE` text in the body (the dialog's
header strip, plan 6, shows the form's title; Rulings 21) and puts a 30 px accent tile with an on-accent glyph beside the
message.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/PromptFace.java`, `DefaultPromptFace.java`, `ClassicPromptFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Stacks.java:102-133,190-203`, `Labels.java:107-145` (`TextControl.styled`), `ViewReconciler.java:116`
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicPromptsTest.java`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv` (one row appended)

**Interfaces:**
- Consumes: Tasks 1 to 6; 4a's classic `PROMPT_TILE` (30), `PROMPT_TILE_RADIUS` (0), `ICON_PROMPT` (16).
- Produces: `final PromptFace KitFaces.prompt`; `interface PromptFace extends Face { LayoutManager
  headerLayout(Stacks.StackControl); void paintTile(Stacks.Tile, Graphics2D); void children(Stacks.StackControl,
  List<ControlRegistry.Control>); }`; `void Stacks.Tile.paintDefault(Graphics2D)`; `java.util.function.Consumer<JLabel>
  Labels.TextControl.styled` (a face's styling, applied at the end of every refresh, cleared by the reconciler before
  each update; plan 4c uses it too).

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicPromptsTest.java`:

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's prompt point as amended: the classic prompt hides its body title and puts a 30 px accent tile beside the message. */
@SuppressWarnings("try")
class ClassicPromptsTest {
    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicPromptIsATileBesideItsMessage() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                KitFixtures.capture(context, view.spec(), 440, 200, "prompt");
                KitFixtures.render(view, 440, 200, 1);
                var title = find(view, "unlock-title", JComponent.class);
                assertThat(title.isVisible()).as("the header strip shows the title").isFalse();
                var tile = find(view, "unlock-hero.tile", Stacks.Tile.class);
                var message = find(view, "unlock-description", JComponent.class);
                int size = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE);
                assertThat(tile.getWidth()).isEqualTo(size);
                assertThat(message.getX()).as("beside the tile").isGreaterThanOrEqualTo(tile.getX() + size);
                var image = paint(tile);
                assertThat(new Color(image.getRGB(2, size / 2), true)).isEqualTo(ThemePainting.color(context, SemanticColor.ACCENT));
                assertThat(new Color(image.getRGB(0, size / 2), true)).isEqualTo(ThemePainting.painted(context, context.faces().prompt.pairs().get(0)));
                view.update(KitFixtures.form("unlock"));
                assertThat(find(view, "unlock-title", JComponent.class).isVisible()).as("still hidden after an update").isFalse();
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheTileAndTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                KitFixtures.render(view, 440, 260, 1);
                assertThat(find(view, "unlock-title", JComponent.class).isVisible()).isTrue();
                assertThat(find(view, "unlock-hero.tile", Stacks.Tile.class).getWidth())
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE));
            } finally { release(context); }
        });
    }
}
```

The `unlock` fixture's header stack is `unlock-hero` (3a kept node id strings), so its tile is `unlock-hero.tile`.

Append the classic prompt's row to `face-pairs.tsv` (the prompt follows the buttons and fields in `KitFaces` order, and is
the last point):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv")
path.write_text(path.read_text(encoding="utf-8") + "classic\tClassicPromptFace\taccent.edge\tsurface.app\tCONTRAST:3.0\n", encoding="utf-8")
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicPromptsTest'`
Expected: FAIL at compile: "cannot find symbol … variable prompt" in `KitFaces`.

- [ ] **Step 3: Add the faces and route the prompt header through them**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/PromptFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;

/** The prompt point (spec 5.2, amended): a PROMPT form's header, as the tile over the title, or the plain classic prompt. */
interface PromptFace extends Face {
    /** The PROMPT_HEADER stack's layout. */
    LayoutManager headerLayout(Stacks.StackControl header);
    /** Paints the tile behind the prompt's icon. */
    void paintTile(Stacks.Tile tile, Graphics2D g);
    /** Prepares the header's children after they refresh (the classic face hides the title and inks the glyph). */
    void children(Stacks.StackControl header, List<ControlRegistry.Control> controls);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultPromptFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;

/** The default prompt: the tile over the prompt title and description, centred. */
final class DefaultPromptFace implements PromptFace {
    @Override public LayoutManager headerLayout(Stacks.StackControl header) { return new Stacks.PromptLayout(header); }
    @Override public void paintTile(Stacks.Tile tile, Graphics2D g) { tile.paintDefault(g); }
    @Override public void children(Stacks.StackControl header, List<ControlRegistry.Control> controls) { }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicPromptFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The classic prompt (spec 5.2 as amended, Unlock Vault mock): no tile over a title; the title belongs to the dialog's
 * header strip, and a 30 px accent tile with an on-accent glyph sits beside the message.
 */
final class ClassicPromptFace implements PromptFace {
    private final PaintPair edge;

    /** A face whose tile stands on {@code surface}, the kit's dialog surface. */
    ClassicPromptFace(SemanticColor surface) { edge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.INDICATOR); }

    @Override public List<PaintPair> pairs() { return List.of(edge); }

    @Override public LayoutManager headerLayout(Stacks.StackControl header) { return new BesideLayout(header); }

    @Override public void paintTile(Stacks.Tile tile, Graphics2D g) {
        var c = tile.context;
        ThemePainting.antialias(g);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.PROMPT_TILE_RADIUS);
        var outer = new RoundRectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight(), 2 * radius, 2 * radius);
        var inner = new RoundRectangle2D.Double(line, line, tile.getWidth() - 2.0 * line, tile.getHeight() - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line));
        g.setColor(ThemePainting.color(c, SemanticColor.ACCENT)); g.fill(inner);
        var ring = new Area(outer); ring.subtract(new Area(inner));
        g.setColor(ThemePainting.painted(c, edge)); g.fill(ring);
    }

    @Override public void children(Stacks.StackControl header, List<ControlRegistry.Control> controls) {
        for (var control : controls) {
            // The title belongs to the dialog's header strip; the hook keeps it hidden through every later refresh
            // (KitView refreshes every control once more when it subscribes to the theme, after the first pass).
            if (control.node instanceof Nodes.Text text && text.role() == Nodes.TextRole.PROMPT_TITLE && control instanceof Labels.TextControl title) {
                title.styled = label -> label.setVisible(false);
                title.component.setVisible(false);
            }
            if (control instanceof Labels.IconControl icon && !icon.inked) { icon.inked = true; icon.ink = InteractiveRows.Ink.ACCENT; icon.refresh(); }
        }
    }

    /** The tile, then the other children stacked beside it, the block centred on the tile when it is shorter. */
    static final class BesideLayout implements Composition.WidthLayout {
        private final Stacks.StackControl owner;
        BesideLayout(Stacks.StackControl owner) { this.owner = owner; }
        int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.PROMPT_TILE_GAP); }
        List<Component> lines(Container p) {
            return java.util.Arrays.stream(p.getComponents()).filter(c -> c.isVisible() && c != owner.tile).toList();
        }
        int room(int width) { return Math.max(0, width - owner.tile.getPreferredSize().width - gap()); }
        int text(Container p, int width) {
            var lines = lines(p); int h = 0;
            for (int i = 0; i < lines.size(); i++) h += (i > 0 ? ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP) : 0) + Composition.height(lines.get(i), room(width));
            return h;
        }
        @Override public int height(Container p, int width) { return Math.max(owner.tile.getPreferredSize().height, text(p, width)); }
        @Override public Dimension preferredLayoutSize(Container p) {
            int w = owner.tile.getPreferredSize().width + gap() + lines(p).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }
        @Override public void layoutContainer(Container p) {
            var size = owner.tile.getPreferredSize();
            int textHeight = text(p, p.getWidth()), top = Math.max(0, (size.height - textHeight) / 2);
            owner.tile.setBounds(0, Math.max(0, (textHeight - size.height) / 2), size.width, size.height);
            int x = size.width + gap(), y = top, width = room(p.getWidth());
            for (var line : lines(p)) {
                int h = Composition.height(line, width);
                line.setBounds(x, y, width, h);
                y += h + ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP);
            }
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 4b Task 7 step 3: the PROMPT_HEADER stack and its tile ask the prompt face."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final StepperFace stepper;
''', '''    final StepperFace stepper;
    final PromptFace prompt;
''', 1),
    ('''            case SPINNER -> new ClassicStepperFace();
        };''', '''            case SPINNER -> new ClassicStepperFace();
        };
        prompt = switch (controls.prompt()) {
            case TILE -> new DefaultPromptFace();
            case PLAIN -> new ClassicPromptFace(colors.dialogSurface());
        };''', 1),
    ('''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, stepper, buttons, fields); }''',
     '''    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, stepper, buttons, fields, prompt); }''', 1)])
edit(S + "Stacks.java", [
    ('''stack.role() == Nodes.LayoutRole.PROMPT_HEADER
                            ? new PromptLayout(this)''',
     '''stack.role() == Nodes.LayoutRole.PROMPT_HEADER
                            ? context().faces().prompt.headerLayout(this)''', 1),
    ('''        void children(List<ControlRegistry.Control> controls) {
            children = List.copyOf(controls);''',
     '''        void children(List<ControlRegistry.Control> controls) {
            children = List.copyOf(controls);
            if (((Nodes.Stack) node).role() == Nodes.LayoutRole.PROMPT_HEADER) context().faces().prompt.children(this, controls);''', 1),
    ('''        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius * 2, radius * 2);
            } finally {
                g.dispose();
            }
        }''',
     '''        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try { context.faces().prompt.paintTile(this, g); } finally { g.dispose(); }
        }

        /** The control-tone tile and its edge (the default face). */
        void paintDefault(Graphics2D g) {
            ThemePainting.antialias(g);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL));
            int radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            g.setColor(ThemePainting.color(context, SemanticColor.BORDER_CONTROL));
            g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius * 2, radius * 2);
        }''', 1)])
edit(S + "Labels.java", [
    ('''        /** On a Settings page supporting text keeps its font's own line height. */
        boolean settings;''', '''        /** On a Settings page supporting text keeps its font's own line height. */
        boolean settings;
        /** A face's styling of this text, applied at the end of every refresh; set by the face, cleared before each update. */
        java.util.function.Consumer<JLabel> styled;''', 1),
    ('''            ((LiteralLabel)label).wrapped.centeredLeading = supporting;
            label.repaint();
        }''', '''            ((LiteralLabel)label).wrapped.centeredLeading = supporting;
            if (styled != null) styled.accept(label);
            label.repaint();
        }''', 1)])
edit(S + "ViewReconciler.java", [
    ('''        if (control instanceof Labels.TextControl label) label.override = null;''',
     '''        if (control instanceof Labels.TextControl label) { label.override = null; label.styled = null; }''', 1)])
PY
```

The classic face's `children` sets the prompt icon's `inked` only when it is not yet inked, so a repeated pass does not
refresh it again (`IconControl.refresh` reads `inked`, so a later refresh keeps the ink). `KitView` refreshes every
control once more right after its first pass (its theme subscription notifies at once), and `children` does not run
again then, so the title is hidden through `TextControl.styled`, which every refresh applies last and the reconciler
clears before each update.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.*' --tests 'dev.jasper.ui.gallery.*' --continue`
Expected: PASS (`ClassicPromptsTest` 2 tests; `PromptCompositionTest`, `FormsTest` unchanged; `PaintSpecTest` with the
complete `face-pairs.tsv`; `StrategyIsolationTest` in jasper-app is run in Task 8).

- [ ] **Step 5: Prove the default gallery unchanged**

Run the gallery comparison of Task 1 Step 5. Expected: `changed: none`. Open `jasper-ui/build/classic-faces/prompt.png`
and compare it with Unlock Vault's body by eye (the tile beside the message, no title in the body).

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/ jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicPromptsTest.java \
  jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv
git commit -F - <<'MSG'
Classic faces: the plain prompt

The prompt point's classic face hides the prompt title in the body (the dialog's
header strip shows the form's title, plan 6) and puts a 30 px accent tile with an
on-accent glyph beside the message; the default keeps the tile over the title.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/ui-architecture.md`, `docs/ui-maintenance.md`, `jasper-ui/README.md`, `docs/plugin-ui.md` (the smallest accurate edits)
- Modify: `docs/STATUS.md` (a new entry at the top), this plan's status banner

**Interfaces:**
- Consumes: Tasks 1 to 7.
- Produces: nothing new.

- [ ] **Step 1: Prove the default gallery unchanged and run the architecture tests**

Run the gallery comparison of Task 1 Step 5, then:
`./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.*' verifyUiArchitecture verifyApplicationArchitecture --continue`
Expected: `changed: none`; BUILD SUCCESSFUL (`StrategyIsolationTest` finds every face of Tasks 1 to 7 and no violation).

- [ ] **Step 2: Edit the documentation**

Read each document first and keep its voice; find the paragraph by its text; add no section.

- `docs/ui-architecture.md`: after the sentence "Each `KitContext`, made with `KitContext.of(kit, …)`, carries its kit
  and selects a route.", add: "A structurally different control draws through a face (`KitFaces`, one per control point, chosen once from the kit's `ControlVariants`); faces
  declare the pairs they paint and never read the kit. The default faces call the controls' own code."
- `docs/ui-maintenance.md`: after "A painter that repairs a pair declares it in `PaintedContrastTest`." (the end of the
  paragraph that lists the frozen tables), add: "A face declares the pairs it paints in `pairs()`, and a change to them
  edits `face-pairs.tsv` (every face's declared pairs) in the same commit. Change a control's look through its face; a
  classic face paints every declared pair with `ThemePainting.painted`."
- `jasper-ui/README.md`: after "`KitContext.of(kit, …)` carries a kit, selects each view's theme route and supplies
  host-owned values.", add one sentence naming `KitFaces` (one face per control point, chosen from the kit's
  `ControlVariants`) and the eight form points' faces (the field policy built with the buttons point).
- `docs/plugin-ui.md`: in plan 3a's "`Toggle` (a boolean, drawn as a switch or a checkbox)" paragraph (or the controls
  list), add only what plugin authors see: "Under the classic look an EXCLUSIVE choice draws radios, TABS draws folder
  tabs, a STEPPER or SLIDER draws a spinner, and a Section with its Card draws a titled box; the descriptors and events
  are the same."

- [ ] **Step 3: Write the STATUS entry**

Insert a new entry at the top of `docs/STATUS.md`, in the style of plan 4a's entry, filling the bracketed values from
Step 4 and `git log --oneline` (if plan 3e's entry has reached this branch's `docs/STATUS.md` by then, insert above it;
otherwise the two are reconciled at merge):

```text
**Enterprise presentation, plan 4b: the classic form faces — 2026-10-16** (`claude/enterprise-classic-catalog`, worktree
`.claude/worktrees/enterprise-4`, `[first]..[last]`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-4b-classic-form-faces.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). `KitFaces` chooses one face per control
point from the kit's `ControlVariants`, once per `KitContext`; each control asks its face, and each default face calls
the control's former code, moved into a named method, so the default look is pixel-identical (the headless gallery for
Slate Cobalt Dark and Light matches the plan-4 base re-recorded at `91153a4ce`: every PNG byte-identical, the JSON
differing only in a section's `class`, now `Cards$SectionSurface`, Rulings 23). The form half of spec 6.4's pairs: the toggle (switch, or a 13 px
checkbox leading its label, never animated), the buttons with the field policy (raised, or the classic gradient with an
accent primary, flat disabled faces, flat toolbar ghosts, dropdown and picker faces ending with a drawn ▼, picker rows
without tiles, no SEARCH, PATH or PORT glyphs, grey read-only fields), the exclusive choice (segments, or a wrapping row
of 14 px radios with hue swatches and thumbnails over their radios), the tabs (segments, or folder tabs), the group with
the label policy (a card, or a titled box; plain labels, or a colon label column as wide as the widest label with a
"Port:" label, form-card settings as rows and bare choices at the field column), the stepper (stepper, or a spinner, a
SLIDER included) and the prompt (the tile over the title, or the plain prompt with a 30 px accent tile beside the message
and the title left to the header strip). The default look's two repaired pairs are now the default choice face's
declarations. `StrategyIsolationTest` (faces never reference `Kit`, `ControlVariants` or `ChromeSpec`; the reconciler no
face), `VariantReadTest` (`ControlVariants` only in the kit package, `dev.jasper.app.presentation` and `KitFaces`) and
`PaintSpecTest` (`face-pairs.tsv`) guard them. Rulings (plan, "Rulings"): faces package-private with
`KitFaces.declared` public; default faces call moved code; one `ChoiceFace` for exclusive and tabs; `border.hover`
edges; pairs against the dialog surface slot; flat disabled buttons; where ghosts are flat; LINK unchanged until 4c;
`RichOptions` keeps tiles until plan 9; no PORT colon; wrapping radio rows; left-aligned previews; folder tab roles;
the box title in `text.primary` (the mock's `#3A3D42` is a residual); colon labels in `text.strong.secondary`; the
label column's 100 px floor (Add Login's 94 a residual); form-card settings as rows; bare choices inset; SETTINGS cards
padded 8; SLIDER as a spinner; the prompt title hidden until plan 6's header strip; keycaps, the joined pair and
notification cards left to plans 6, 7 and 9; the section surface's class name the only JSON change in the default
gallery. Deviations (Task 2's review; the plan's Execution notes X12 and X14): `ButtonFace.fades` takes the button, so
the classic face fades as one layer only the buttons it leaves to the default treatment (plan 4c's Task 3 edits its
condition with `paintFace`'s and `text`'s); a disabled classic picker is flat like a disabled button; a classic
read-only field with an error keeps its `border.error` edge; `FieldPolicy.readOnlyText` takes the field's surface, so a
transparent read-only field keeps `text.primary`; a menu button's parts paint no face of their own, a strip's icon-only
menu draws no edge, and a ghost menu is flat in a toolbar row or a panel header, where its surface stands; a secret's
reveal and copy and each crumb are flat until hovered wherever their control stands (`KitButton.inner`). Residuals: 4a's
flat focused, hovered or pressed classic native button stays for 4c (the classic `ButtonFace` paints kit buttons only,
its gradient in every state); a disabled classic dropdown without a rich face is faded again over its flat face by
`ChoiceCombo.paint` (fill `#ECEFF1` and edge `#E2E6EA` on Slate Cobalt Light's `surface.app`, where a disabled button
paints `#F1F2F2` inside `#D5DAE0`), and a disabled SECONDARY or PRIMARY button on a selected row fades as one layer over
its flat face (Execution notes X13); spec 6.4's face table still says "small glyph, plain title" for `PromptFace`, which
the amended 5.2 prompt row supersedes; TFTP's read-only login field in the control column (spec 6.4) is left to plan
10's File Server composition. Plan 4c adds the data faces, the full kit matrix and the mock comparison.
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

- [ ] **Step 5: Set this plan's status banner**

Insert below this plan's title:

```text
> **Status (2026-10-16): executed on `claude/enterprise-classic-catalog` (`[first]..[last]`).** Deviations from this
> plan's text: [Task 2's three (Execution notes X12) and two (X14), then each other one].
```

- [ ] **Step 6: Commit**

```bash
git add docs/ui-architecture.md docs/ui-maintenance.md jasper-ui/README.md docs/plugin-ui.md docs/STATUS.md \
  docs/superpowers/plans/2026-10-16-enterprise-4b-classic-form-faces.md
git commit -F - <<'MSG'
Classic form faces: documentation and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

## Execution notes (2026-10-16)

Pre-flight for `superpowers:subagent-driven-development`, run on `claude/enterprise-classic-catalog` at `91153a4ce`
(`main`, with plans 3a to 3d and 4a merged, and nothing else). Every anchor, quoted text, path, signature and line
number above was re-read against that code, and the text above now carries the current ones. Evidence without Gradle:
every `Create` block and edit script of Tasks 1 to 7 was replayed in order on a `git archive` copy of `HEAD`, and each
script found its text the stated number of times (after X2). The replay was also stopped after each task: at every stop
jasper-ui's main sources, test fixtures and tests compiled with the JBR's `javac`, the whole jasper-ui suite passed
under a JUnit launcher (705 tests at `HEAD`; 713, 721, 727, 729, 734, 737 and 739 after Tasks 1 to 7, 0 failures,
which matches each task's new test count), and `KitGallery.main` for Slate Cobalt Dark and Light at 1x matched a fresh
render of `HEAD` under Task 1 Step 5's digest script (`changed: none`, 161 files per theme). On the final state,
`javadoc -Xdoclint:all -Xdoclint:-missing` reported nothing on jasper-ui's main sources, `javac -Xlint:all` added only
`serial` warnings (the build turns `serial` off), and the AGENTS.md hygiene scan found no bad character in the 58 new or
changed files. In jasper-app, `StrategyIsolationTest` and `VariantReadTest` compiled with `StaticDefaultTest` and passed
(4 tests) against the patched jasper-ui jar and the worktree's last-built `jasper-app.jar`, and the patched
`PaintedContrastTest` compiled and passed against the worktree's last-built jasper-app classes. Not run here:
`ThemeMigrationCoverageTest`, `verifyUiArchitecture`, `verifyApplicationArchitecture`, the Gradle gallery task and
`./gradlew check`; the executor's runs are the evidence for those.

### Shared files and interfaces

| Pair | What they share | Agree? | Ruling |
|---|---|---|---|
| Task 1 and Tasks 2 to 7 | `KitFaces.java`: each task's anchors (the field block, the end of the previous `switch`, `all()`) are the text the task before it wrote | Yes, in order | X3 |
| Tasks 1 to 7 | `face-pairs.tsv`: Task 1 writes it; Tasks 2 and 7 append; Task 3 puts the default rows first and the classic choice rows after the toggle's; Task 4 replaces the tabs point's classic default row; Tasks 5 and 6 anchor on rows Tasks 4 and 5 wrote. `PaintSpecTest` freezes the order of `KitFaces.all()`, which each task extends to match | Yes: `PaintSpecTest` passed after every task | X3 |
| Task 1 and Task 5 | `SettingRows.java`: Task 5's `height`, `preferredLayoutSize` and `layoutContainer` anchors contain Task 1's leading-row lines | Yes, Task 5 after Task 1 (its Consumes says Tasks 1 to 3) | X3 |
| Task 2 and Task 3 | `Buttons.KitButton`'s `paintKitFace`, `fadesDisabled`, `paintRaised` and `textColor` (Task 2), which `Choices.SegmentButton` overrides or bypasses (Task 3); their `Choices.java` anchors are disjoint | Yes | X3 |
| Task 2 and Task 7 | `Stacks.java`: Task 2 the `StackSurface` field and `refresh`'s route line (`:21`, `:95`); Task 7 the PROMPT_HEADER layout (`:102-103`), `children` (`:132`) and `Tile.paintComponent` (`:190-203`) | Yes, disjoint | none |
| Task 5 and Task 7 | `ViewReconciler.java`'s resets (`:94`, `:116`); both reset control fields only, which `StrategyIsolationTest` allows | Yes, disjoint | none |
| Task 1 and Task 3 | `PaintedContrastTest`: Task 1 runs it unchanged; Task 3 reads its two default repairs from `KitFaces.declared(Kit.DEFAULT)` | Yes | none |
| Task 1 and Task 8 | `migration.csv` (Task 1's two eight-field rows, appended) and the documents Task 8 edits | Yes | X7 |
| Workspace and Tasks 1 to 8 | The gallery base, compared at every Step 5 and in Task 8 Step 1 | Only once re-recorded at `HEAD` | X1 |
| Task 2 and plan 3b | `KitButton.textColor`: 3b added the Notice link floor to it | No: the anchor was the pre-3b one-line method | X2 |
| Task 2 and plan 3c | The `ChoiceCombo` arrow's bare-cell ink and `paintComponent`'s bare-cell return; `ClassicButtonsTest`'s use of `TableVocabularyTest.groups` and `combo` | Yes: already quoted post-3c | none |
| This plan and plan 3e (parallel) | `docs/STATUS.md` only: 3e's branch (`claude/enterprise-default-look`, `e9cffab2d`) changes no file Tasks 1 to 7 change and nothing in jasper-ui | Yes | X6 |
| This plan and plan 5a (parallel) | 5a's precondition lists `KitFaces.java`, `ClassicButtonFace.java` and `ClassicFolderTabsFace.java`; `VariantReadTest` allows `dev.jasper.app.presentation`, where 5a's `KitParser` reads `ControlVariants`; `StrategyIsolationTest` forbids 5a's `dev.jasper.app.presentation.ChromeSpec` in faces | Yes: the names match | X6 |
| This plan and plan 4c (next on this branch) | 4c consumes `Face`, `KitFaces`' fields and `all()` order, `ThemePainting.role` and `painted`, `ClassicGlyphs.down` and `right`, `KitButton.paintContent`, `StackSurface.role`, the prompt face's call at the start of `StackControl.children`, `TextControl.styled`, `KitFixtures.capture`, the final `face-pairs.tsv` rows (4c's verified fact 17), the digest script's two substitutions and the base; 4c's Task 3 edits `ClassicButtonFace.paintFace` and `text` so a side-column LINK draws as a standard button, and since Task 2's review `ClassicButtonFace.fades(KitButton)` repeats their condition | Yes: each is produced here under that name; `fades` only once 4c's Task 3 edits it too (4c's Execution notes) | X1, X12 |

### Each task against itself

| Task | Finding | Ruling |
|---|---|---|
| Workspace | 4a's checks hold (`46aa55a80`; one `Kit.CLASSIC`); the base left by 4a predates 3c's `tables-0-18` fixture | X1 |
| 1 | Anchors hold; `ClassicTogglesTest` 5 and `PaintSpecTest` 3 pass on the replay; its Files line numbers had moved | X4 |
| 2 | The `textColor` anchor was pre-3b; `ClassicButtonsTest` 8 pass once it is current | X2 |
| 3 | Anchors hold; `ClassicChoicesTest` 6 and the patched `PaintedContrastTest` pass | none |
| 4 | Step 2's red is measured: "expected: 26 but was: 36" | X5 |
| 5 | Anchors hold; `ClassicGroupsTest` 5 pass; spec 6.4's TFTP read-only field in the control column is not in this plan | X10 |
| 6 | Anchors hold; `ClassicSpinnersTest` 3 pass | none |
| 7 | Anchors hold; `ClassicPromptsTest` 2 pass; the face follows spec 5.2's amended prompt row, while 6.4's face table still says "small glyph, plain title"; Step 4 named `HeroCompositionTest`, which 3a renamed `PromptCompositionTest` | X5, X8 |
| 8 | `jasper-ui/README.md` has no list of the swing package's parts and `docs/ui-architecture.md` no paragraph on the swing controls; the form points are eight, not seven; 4a's STATUS residual for `ButtonFace` (flat focused, hovered or pressed native buttons) was not addressed | X7, X9 |
| 2, 6 (tests) | `javac -Xlint:all` reports two redundant `JTextComponent` casts (`input` is already one) and a third on `ClassicGroupsTest`'s `EmptyBorder` cast, which `getBorderInsets()` needs | X11 |

### Rulings

- **X1. Re-record `jasper-ui/build/plan4-base` at `HEAD` before Task 1's first edit, and keep it through plan 4c** — the
  base 4a left predates plan 3c's `tables-0-18` fixture and differs from a render of `91153a4ce` in eight files
  (`manifest.json`, `sheet-10.png`, `tables-0-18.json` and `.png`, in each theme), while the replay of every task
  matched a fresh render of `HEAD` — cost if wrong: every Step 5 reports those eight files, or a base taken after an
  edit hides a real change.
- **X2. Task 2's `textColor` edit keeps 3b's Notice link floor ahead of the face and asks the buttons face for every
  other label** — 3b put the floor into `textColor` after this plan was written; the default face's `text` returns
  the role's colour, the former last line, so the default look is unchanged (replayed gallery identical) and a Notice
  link keeps its floor under both kits — cost if wrong: the script stops on the old anchor, or classic Notice links
  lose their floor.
- **X3. Tasks run in order 1 to 8, one commit each, as written** — `KitFaces`, `face-pairs.tsv` and `SettingRows`
  anchors chain from task to task, and the replay applied them in that order with every intermediate state compiling,
  passing and pixel-identical — cost if wrong: a script stops on a missing anchor.
- **X4. The Verified facts and every Files line number carry `91153a4ce`'s lines; their meaning is unchanged** — plans
  3b to 3d and 4a moved them; the scripts match by text, so lines only guide reading — cost if wrong: an executor
  opens a file a few lines off.
- **X5. Two expected outputs corrected: Task 4 Step 2's red reads 36, and Task 7 Step 4 names `PromptCompositionTest`**
  — the first measured on the replay, the second 3a's rename — cost if wrong: none.
- **X6. Plans 3e and 5a are not merged here and this plan does not adapt to them** — 3e shares only
  `docs/STATUS.md` (Task 8 inserts above 3e's entry if it has arrived, else the merge keeps both) and 5a's branch holds
  plans only, whose plan-4 names this plan produces — cost if wrong: one `docs/STATUS.md` hunk at merge.
- **X7. Task 8's documentation edits target text that exists** — after the `KitContext` sentence of
  `docs/ui-architecture.md`, after the `PaintedContrastTest` sentence that ends `docs/ui-maintenance.md`'s frozen-table
  paragraph, after the `KitContext.of` sentence of `jasper-ui/README.md`, and in `docs/plugin-ui.md`'s `Toggle`
  paragraph (`:57`); the points are eight — cost if wrong: wording only.
- **X8. Task 7 follows spec 5.2's amended prompt row** (no 54 px tile and body title; the title goes to the dialog
  header strip, plan 6; a 30 px accent tile with an on-accent glyph beside the message), **and records 6.4's face-table
  row ("small glyph, plain title") as a residual instead of editing the spec** — the amendment is the later, binding
  text and spec edits are outside this plan — cost if wrong: a reader of 6.4's table expects a small glyph.
- **X9. 4a's residual stays open for plan 4c: a focused, hovered or pressed classic native (FlatLaf) button paints
  flat** — the classic `ButtonFace` paints kit buttons, which keep the gradient in every state (the edge darkens to
  `border.hover` on hover, the gradient reverses while pressed, focus adds the ring), but no face reaches FlatLaf's
  `Button.focusedBackground`, `hoverBackground` and `pressedBackground` keys; Task 8's STATUS entry says so — cost if
  wrong: native buttons in app dialogs stay flat on hover until 4c.
- **X10. TFTP's read-only login field in the control column (spec 6.4's composition rules) is left to plan 10's File
  Server composition** — the label policy insets only an unlabelled EXCLUSIVE choice (Rulings 18), and the spec's own
  remedy for such a field is the composition's empty-label FormRow (as for Add Host) — cost if wrong: the field sits
  flush left under the classic kit until plan 10.
- **X11. The two redundant `JTextComponent` casts and their imports are dropped from `ClassicButtonsTest` and
  `ClassicSpinnersTest`; `ClassicGroupsTest`'s `EmptyBorder` cast stays** — the build compiles with `-Xlint:all`, and
  without the third cast `getBorderInsets()` does not compile — cost if wrong: none.
- **X12. Task 2's first review round deviates from its text in three places, which Task 8's STATUS entry and banner
  list** — `ButtonFace.fades` takes the button (`boolean fades(Buttons.KitButton)`, where Produces names `fades()`): the
  classic face fades as one layer the buttons it leaves to the default treatment (those `paintFace` hands to
  `paintRaised`, and those on a selected row, whose labels `text` keeps in their role), so a disabled LINK still fades
  and a disabled button whose face draws its own state paints its glyph at 0.42; a disabled classic picker is flat like
  a disabled button (`surface.tableHeader`, a `border.divider` edge, its line and ▼ at 0.42), not its gradient at
  `PICKER_DISABLED_PERCENT`; a classic read-only field with an error keeps its `border.error` edge. The `fades`
  condition repeats `paintFace`'s and `text`'s, and plan 4c's Task 3 changes those two to
  `b.kind == Nodes.ButtonKind.LINK && !b.sideColumn`, so plan 4c's Execution notes (E1) give `fades` the same edit and
  a test — cost if wrong: after 4c a disabled side-column LINK add action is faded twice, its label near 0.18 alpha on a
  faded flat fill (Rulings 6, spec 6.4).
- **X13. Two disabled faces are faded over their flat faces; Task 8's STATUS entry lists them as residuals** — a
  disabled classic dropdown without a rich face paints its flat face, and `ChoiceCombo.paint` then fades the closed
  face to 0.42 (measured with the Enterprise fixture kit on Slate Cobalt Light's `surface.app`: fill `#ECEFF1`, edge
  `#E2E6EA`, where a disabled button paints `#F1F2F2` inside `#D5DAE0`); a disabled SECONDARY or PRIMARY button on a
  selected row draws its flat face with its label in its kind's role (`text.strong.secondary`, `text.onAccent`) and
  fades as one layer, because `text` and `fades` leave buttons on a selected row to the default treatment. Routing the
  combo's fade through the buttons face (as `fades` does for buttons) is later work; handing buttons on a selected row
  to `paintRaised` instead would give them the default face (no gradient; the primary's inset line and drop) — cost if
  wrong: two disabled faces paler than a disabled button.
- **X14. Task 2's third review round deviates from its text in two places, which Task 8's STATUS entry and banner
  list** — `FieldPolicy.readOnlyText` takes the field's surface (`Color readOnlyText(TextFields.FieldSurface)`, where
  Produces names `readOnlyText(KitContext)`): the classic policy leaves a transparent read-only field on its default
  surface, so its text keeps `text.primary` instead of the muted text guarded against a grey face it never paints
  (`TextFields` now sets the surface's `transparent` before its text colour). And composite controls keep their own
  treatment under the classic buttons face: a menu button's parts paint only their focus ring under both faces
  (`Menus.Part.paintKitFace` calls `paintRaised`; their fill and border are null), its face honours `borderless` (a
  strip's icon-only menu has no edge), and its parts and face stand where its surface does (`KitButton.placement()`; the
  face takes the parts' `panel` flag), so a ghost menu in a toolbar row or a panel header is flat until hovered; a
  secret's reveal and copy and every crumb set `KitButton.inner`, which `ClassicButtonFace.flat` reads as flat until
  hovered wherever the control stands, the current crumb keeping `text.strong.secondary`. Rulings 7's places stand
  otherwise, and `ChoiceCombo.paint` names X13 at its fade — without them the menu parts drew a second gradient and an
  edge where the main part meets the arrow, a reveal drew a boxed button inside its field and the crumbs boxed
  buttons, and a transparent read-only field drew muted text — cost if wrong: one accessor and one flag on `KitButton`.
