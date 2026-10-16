# UI architecture

`jasper-ui` is the independent theme engine and Swing kit. The app composes it with Terminal
and Buddy; plugins compile against the JDK-only SDK projection. The coordinated Plan 2 migration makes this module the sole theme and colour authority.
Plan 3 owns final surface composition; the [completion record](design/theme-engine/completion.md) records the handoff.

| Package | Allowed outgoing UI packages | Ownership |
| --- | --- | --- |
| `theme` | none | Jasper theme files (Slate Cobalt is bundled), semantic palettes and derived roles, terminal and editor schemes, syntax, glass, FlatLaf keys and paint-time contrast guards; EDT appearance binding |
| `model` | none | Immutable descriptors and events; no secret values |
| `metrics` | `theme` | Logical geometry catalogs (`DesignMetrics`), type scales and faces, central typography/motion roles |
| `kit` | `metrics`, `theme` | The look: `Kit` names a metric catalog, type scale, faces and FlatLaf key set; `Kit.DEFAULT` is the default look |
| `icons` | `theme`, `metrics`, `model` | Named artwork and context-scoped tint caches |
| `swing` | `model`, `theme`, `metrics`, `icons`, `kit` | EDT views, delegates, owned controls, registrations and FlatLaf installation |

`verifyUiArchitecture` checks each package's edges against the list its package-info names (this table), the acyclic
package graph, independence from other Jasper modules and JDK-only theme/model/kit boundaries. FlatLaf remains an implementation dependency. SDK `ui` values mirror the model without exposing Swing or UI types;
`dev.jasper.app.plugins.KitValues` is the translation boundary. Host and testkit implement the same
SDK facade. Plugins never receive the engine jar on their runtime classpath.

Read a Slate Cobalt role without constructing Swing controls:

<!-- example:palette -->
```java
var theme = JasperTheme.bundled().generated(true);
var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
```

Slate Cobalt's semantic roles supply colors. Geometry, spacing, artwork and typography roles
come from the context's kit (`KitContext.kit()`: its `DesignMetrics`, `TypeScale` and `FaceSet`), `Typography` and
`IconRegistry`.
`TerminalColors` orders background then foreground; the terminal palette orders foreground then
background. Translate by meaning. ANSI arrays are defensively copied.

Install appearance on EDT before widget creation. `AppearanceTransaction` installs real FlatLaf
and publishes a complete chrome/workspace `ThemePair`, restoring prior global defaults on failure.
Each `KitContext`, made with `KitContext.of(kit, …)`, carries its kit and selects a route. Binding subscriptions replay and close independently; views retain
stable control/document identity on updates. Font-only default changes refresh central typography.

The caller owns binding and registry lifetimes; a view owns listeners, its descriptor controls and
registered specialized content. Close the view first. This compiled example runs inside an EDT block;
`generated` is the previous example's output and `events` is a caller-owned list of `UiEvent`:

<!-- example:view -->
```java
try (var binding = new ThemeBinding(new ThemePair(generated, generated), failure -> {});
     var icons = new IconRegistry()) {
    var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.WORKSPACE,
            icons, () -> false, action -> "");
    var root = new Nodes.Stack("editor", ControlState.READY, LayoutRole.VERTICAL,
            List.of(new Field("name", ControlState.READY, FieldKind.TEXT,
                    "", "Name", "Name", ""),
                    new Secret("password", ControlState.READY,
                            "Password", "Password", "")));
    try (var view = new KitView(context, new ViewSpec(root), events::add)) {
        view.withSecret("password", temporary -> {
            // Consume synchronously; the kit erases this temporary array on return.
        });
        view.clearSecret("password");
        binding.publish(new ThemePair(generated, generated));
    }
}
```

`withSecret` exposes only a temporary array and erases it in `finally`, including callback failure.
Asynchronous consumers must transfer a clone to an owner that erases it. Secrets never enter nodes,
events, metadata or diagnostics. Reveal state and wipeable documents belong to the view.

Stored credentials use `SecretKind.DETAIL` plus a value-free copy action; the legacy constructor
continues to create INPUT. `replaceSecret(id, char[])` defensively copies without an edit event and
leaves the original caller-owned. DETAIL uses character-array painting, never a text-component value.
Replacement/removal/kind change/close wipe owned values; `clearSecret` and replacement remask details.
Copy emits only `Action(copyAction)`; the domain callback uses `withSecret` and its credential clipboard
service. No secret-bearing immutable string or general clipboard write is added by the kit.
`SecretKind.MULTILINE` (Plan 2 Task 7) is an editable secure note: a wrapping text area over the same wipeable
buffer, shown in clear, with no reveal group, copy action or reveal control; its storage, transfer and wipe rules
are those of INPUT. An INPUT secret's optional `submitAction` (Task 7 review fix round 1) is what Enter in that
field runs; the field keeps the key, so a dialog can scope Enter to its password field.

Updates invoked from an in-progress update are rejected before mutation at module/SDK/fake boundaries.
Disposal still contains callback diagnostics and permits reentrant close; normal event handlers can
continue updating synchronously outside reconciliation.

Containers own semantic layout. Forms consume a host-supplied logical available-content-height;
the host owns window chrome, sheet/scrim lifetime and cancellation cleanup.

`ModalOverlay` is the kit's modal overlay, which `DialogSpec.Presentation.OVERLAY` and the SDK's progress
overlays present: a scrim over a window's whole content (`surface.scrim`, painted as its role gives it) and the content in a centred, outlined, rounded card
with theme.md's dialog shadow (black, `DIALOG_SHADOW_*`). Opening fades
the scrim in while the card fades from transparent and rises `OVERLAY_RISE` into place over
`OVERLAY_ENTER_MILLIS`, easing out (cubic); closing is its opposite over `OVERLAY_EXIT_MILLIS`: the card lifts off
at once and rises another `OVERLAY_RISE` (easing out, from wherever it is if the entrance had not finished) while it
fades (easing in) and the scrim lifts. With one shared ease-in the card barely moved before it had faded. Each
transition's clock starts at its first drawn frame (after a new form's layout and the card's snapshot), so none of the
motion is spent before it can be seen, and a leaving card keeps its size although the closed dialog's content leaves
it at once. The motion
follows the kit's motion policy, so it is instant with animations off and settles at once if they are turned off
mid-way. While it moves the card paints one snapshot of its content, so it fades as a single surface (no control
paints its own disabled alpha ahead of the rest); at rest the content paints live. The host adds the overlay to a
layered pane and owns keyboard routing and focus; the app's `WindowOverlay` stacks them per window.

`JasperTheme` reads a Jasper theme file with the JDK-only `ThemeJson` reader; `JasperTheme.bundled()` is Slate Cobalt,
the bundled `slate-cobalt.json` (the colour reference `docs/design/theme-engine/slate-cobalt.md`'s machine-readable
block, kept identical by a test). A theme has the Dark and/or Light semantic palettes (roles the file omits follow
fixed rules), its terminal schemes (`TerminalScheme`, with an id and a bold foreground) and an editor scheme per
variant (`EditorColors` and syntax styles, written as colours or as references into the palettes). `generated(dark)` assembles a full `GeneratedTheme`: the
palette, the matching terminal scheme, the editor scheme and syntax and the glass; `FlatLafInstaller.install(theme, kit)`
derives FlatLaf's UI keys from the roles when it installs one (`UiColorKeys.flatLaf(theme, kit.uiKeys())`). Components paint theme.md's roles; alpha treatments (focus and error rings, hover, category tiles)
apply one kit metric to a base role, and a role that carries alpha (the scrim, `border.subtle`) is painted as given.
A variant may also list eight of its derived roles (`GeneratedTheme.derivedOverrides()`); `DerivedPalette.of(theme)`
gives all 23 `DerivedColor`s, each the override or its recipe over semantic roles and the derived roles before it,
kept by theme identity (header chrome passes `TitleBarTheme.of(theme)`). `PaintPair` declares a pair a painter paints,
naming either kind of `ColorRole`, with its `SurfaceContrast.Guard` (text 4.5:1, indicator 3:1, structural boundary
1.3:1, control boundary 1.5:1, or a CIE L* distance); `SurfaceContrast.painted` applies the guard where the pair is
painted and never changes a role.
`ColourLiteralScanTest` fails on any colour literal in production code or resources outside `slate-cobalt.json`. Shadows are black at the kit's shadow
metrics: `ThemePainting.softShadow` blurs one mask per treatment and nine-slices it across face sizes (a face too
small to slice gets its own cached mask), and a hollow mask has the face cut out so nothing of the shadow lies under a
translucent face. It serves the modal overlay's card (hollow, so a fading card does not darken) and the command
palette; FlatLaf paints menus' shadows off macOS; the Light selected segment has a soft shadow; and a primary button's
1 px drop is painted by its `KitView` outside the face (as `box-shadow` does), so the face keeps its calibrated extent
while a white inset line tints its top row. The button repaints its view over the drop when it is disabled, hidden,
moved, removed or stops being primary, as the focus halo does.

Panels own header,
context/navigation, virtual body and persistent footer. Hidden headers consume zero space.
Three-column managers use named sidebar/list widths (192/272 logical pixels), then remaining detail;
narrow hosts fit those roles proportionally. A trailing footer action anchors to the right edge.
Virtual rows retain bounded live controls; mutable fields, secrets and nested custom content stay in
stable composition outside rows. Specialized content registrations validate their kind and dispose
on replacement/close; they are not a route for arbitrary feature forms.

Calibration clarified nominal geometry: a 28-pixel control paints its full 28-pixel bounds. The view
owns the external 3-pixel focus/error halo, clipped by real viewports, without changing row gaps.
Named radii are radii; Java rounded rectangles receive twice that value as their arc diameter.
The kit paints placeholders once rather than delegating a duplicate FlatLaf hint.

`Choice.icon` is a named cue for PICKER only; empty means no cue. The original seven-argument
constructor retains its PICKER `key` default, with other kinds empty. The eight-argument form permits
explicit `lock` or empty artwork; nonempty cues on other kinds are rejected. MenuItem.hue explicitly selects category artwork hue; the original constructor delegates NEUTRAL.
No domain meaning is inferred from ids/text. Category tiles likewise follow explicit SHEET/DETAIL, PROMPT/RAIL and
single/double-list roles; ordinary CONTROL folder icons remain plain glyphs.

Settings capabilities (Plan 2 Task 6a): tree items carry an optional trailing count and an emphasized
title; setting and form rows carry a search `highlighted` flag, and `KitView.reveal(id)` scrolls a node into view
without moving focus; `FieldKind.NUMBER` admits only numbers within its `Numeric` shape and steps with the arrow
keys (no spinner); `ChoiceKind.SEARCHABLE` is the one type-to-filter dropdown, with optional free entry whose
options are suggestions. The [contracts appendix](superpowers/plans/2026-10-04-theme-engine-contracts.md) states
the exact semantics.

Browser capabilities (Plan 2 Task 6b-1): table columns share the width by weight, show the host's sort and report
header clicks as `Sorted`; rows can be muted and carry their own context menu; tree items carry a tooltip, a status
dot and aligned trailing columns; every row collection has type-ahead, Shift ranges for multiple selection and
scrolls a host-set selection into view; tables can opt into the OVERLAY scroll policy; and `Breadcrumbs` is the kit
path control with tail overflow and a typed-path mode. The named glyph vocabulary gains `home`, `desktop` and
`preview`. Tables and trees report a plain left single click as `Clicked` (never after a popup-trigger press), and Enter passes
on to the host when a row collection has no activate action; a context press never changes the selection and its
menu items report only their action (a shared menu opens only on a selected row); ACTIONS stacks wrap instead of squeezing their buttons; trailing tree columns keep minimum
widths; single-line fields never clip an enlarged font.

Remote row capabilities (Plan 2 Task 8-kit): item-list rows and table rows carry a literal tooltip shown over their
passive content; item lists report a plain left click as `Clicked` (not RESULTS lists or headings) and can give Enter
and Delete/Backspace their own actions (`enterAction`, `deleteAction`), so Enter and a double click can differ; a row's
own buttons and switches report only their intent; `Toggle.pending` shows an operable midway switch on the warning
colour while the domain confirms a change; `Button.iconSeverity` tints a button's glyph with a severity colour (a
favourite's warning star); and the glyph vocabulary gains `star` and `star.filled`.

Editor capabilities (Plan 2 Task 9-kit): a `SplitKind.DOCUMENT` split stacks its children and lets its first visible
`Custom` (or `Empty`, a loading or failed state) fill the height the bars leave, so a document keeps its own scrolling
(the split's minimum height counts the fill at its own minimum, so a long document never sets the window's);
fields take Shift+Enter (`alternateAction`) and Escape (`cancelAction`) actions and can drive an item list with their
arrow and page keys (`listId`) while keeping the focus; a stack's `cancelAction` answers Escape from anything inside it
(an inline find bar); `Button.selected` makes a ghost or secondary button a toggle (pressed fill, CHECKED for assistive
technology, confirmed by the owner); `Text.severity` tints text and `Text.caret` draws a command line's insertion bar.
Host-only: `KitView.route(ThemeRoute)` re-routes one view in place (each view owns a copy of its context) and
`KitView.paintsSurface(true)` paints its slot's `surface.panel` opaque; the app mounts plugin content tabs on the
workspace slot over that surface, and their registered specialized content follows the view's slot. Toolbar views
take `ThemeRoute.TITLE_BAR`, the chrome's `TitleBarTheme`, as the window header's own controls do.

Document bars (Plan 3 Task 8): a `SplitKind.DOCUMENT` split presents its non-document children as compact bars,
by structure and with no SDK shape change. An INLINE stack among them, directly or as a row of a VERTICAL group child,
lays out with `DocumentBarLayout`: `DOCUMENT_BAR_*` padding and gaps (adjacent buttons closer), the first field or
text filling what the controls after it leave (the rows of one group align their fills, so a replace field sits under
its find field), other text holding a stable status width, the row's fields taking the panel search field's look and its
buttons the panel action size. A group stacks its rows without gaps and shows a passive list's rows as text lines
(command-line output); the split draws a subtle hairline at each boundary, inside the bar side. The glyph vocabulary
gains `match.word` (whole word) beside `match.case` and `regex`. Only the Editor uses DOCUMENT splits.

Task 9a's module-only sizing rule: an ItemList named by a field's `listId` reserves its `visibleRows` selectable-row
height even when filtered or empty, so host-packed picker dialogs keep their height. Every list kind follows this rule;
RESULTS reserves its fixed data-row metric, and existing heading sizing is retained. Lists without a referencing field
or without `visibleRows` keep their prior sizing. No SDK shape changes; the fake retains descriptors and validates
references, while module and real-host tests verify layout.

App manager surfaces (Plan 3 Task 9, unmocked app windows): `SplitKind.SIDEBAR_DETAIL` is the three-column manager
without its rail: its last child is the detail column and the others list columns (list width `MANAGER_LIST_WIDTH`,
fitted to at most half the width; no gap, the columns meet at their hairline). A list column without a search row starts
its rows at `COLUMN_RAIL_TOP`, and one-line rows in a list column take the rail's `COLUMN_RAIL_ROW` (two-line rows keep
`COLUMN_ITEM_ROW`). A manager detail heading's title block keeps `DETAIL_IDENTITY_MIN_WIDTH` (or its own width) before its
actions wrap, and a caption over its value in a manager detail keeps `DETAIL_VALUE_GAP`. Host-only: `KitView.managerColumn`
presents a whole native-arranged view as a RAIL (rail rows; a tree's branches become flat `SECTION_LABEL` headings
without disclosure) or LIST column (rounded selection; a table's header on the row inset and its rows below
`COLUMN_RAIL_TOP`); `KitView.panelSurface(false)` leaves a body panel's rounded surface to a native host that already
paints it; `SurfacePanel.footerBand` is the WINDOW footer band for native shells. The notice surface (notification
cards) is a grouped card: `CARD_RADIUS` corners and a crisp pure-stroke hairline. A dropdown in an ACTIONS stack is
labelled by its accessible name only, and a natural-width dropdown reserves its chevron lane for its longest plain option.
None of these is part of the plugin SDK except the SIDEBAR_DETAIL presentation, which plugins reach through the same kind.

Panel capabilities (Plan 3 Tasks 6-7): an `Empty` that is a panel's body (or the last child of a DOCUMENT split
body under a note) takes the plugin-panel geometry, centred in the body; a two-line `ItemList` directly in a panel is a
compact inset row whose title line keeps its first text (the name) whole while it fits in half the line, and a `Chip` on
an inline line keeps its label whole; a panel footer `Stack` (VERTICAL) of status lines above a `SettingRow` paints a
full-width hairline-thick rule above the row and keeps the row flush with the bottom. `LayoutRole.ROUTE` is a framed strip
of equal columns, one per child stack (a tile beside two lines), with the kit's arrow before every later column; a
DROPDOWN `Choice` may carry a `PickerFace` (title, detail, glyph, hue) painted in place of its plain label. FormRow
captions stay on one line, shortened at their end with the full caption as tooltip. The glyph vocabulary gains
`laptop`, `target`, `arrow.right` and `import` (an arrow into a corner bracket, distinct from the `download` tray).

Palette capabilities (Plan 2 Task 6b-2): `ListItem.heading` is a title-only section row (never selected, activated,
hovered or given a menu; a hairline opens every heading after the first); `ListKind.RESULTS` is the one-line result
list that follows an external field: it never takes focus, a left press runs a row, rows and headings keep fixed heights
(`RESULTS_ROW_HEIGHT`/`RESULTS_HEADING_HEIGHT`), the trailing tag drops first, and every host update keeps the
selection and the heading above it in view while a theme change keeps the scroll; `ItemList.visibleRows` sizes the
viewport in selectable rows; `KitView.composing(fieldId)` reports uncommitted input-method text; and a SEARCH field's
label is its accessible name only (the glyph and hint label it visually). `Field.presentation` lets a host composition
supply a field's visible label (EMBEDDED) and, on a surface that backs the field, drop the input fill (TRANSPARENT).

Workspace chrome and palette calibration (Plan 3 Task 10): RESULTS headings use `Typography.Role.RESULTS_HEADING` (11)
for their text and measured height; a TRANSPARENT search field shows the rail-size glyph with `TRANSPARENT_FIELD_ICON_GAP`.
The host-only `KitView.setHintPresentation(stackId, hint)` gives one stack (the palette footer) the compact hint face:
its CAPTION text and embedded links use `PALETTE_HINT` (11) and its children `PALETTE_HINT_ACTION_GAP`; same-id updates
keep it and it is not part of the plugin SDK. `ChromePainter` (one per kit; app chrome paints with `ChromePainter.installed()`) owns the native chrome's painting (tab strip, toolbar
rules and action widths, status dot, rail badges, connected dot, split dividers, drop guides, find and title-bar rules,
palette shadow) over named roles; `TITLE_CONTEXT`, `TOOLBAR_LABEL`, `STATUS_TEXT` and `STATUS_PATH` carry the reference
chrome text sizes. `IconRegistry.chromeIcon` is the native chrome's receiving context: the meanings the workspace chrome
depicts (add, new window, split, expand, chevron down, folder, server, bell, lock, terminal) use their byte-identical
original desktop artwork (`assets-chrome.tsv`); descriptor, panel, tile and plugin-registered contexts never receive it.

Manager and notification capabilities (Plan 2 Task 6c): item-list rows can carry their own context menu (offered on
any row, never selecting it; the menu key and Shift+F10 open the focused row's menu); Space on a row whose trailing slot
holds exactly one switch operates that switch, keeping a list of switch rows one focus stop; switches take a literal
tooltip; glyphs can carry a severity (INFO, WARNING, DANGER) that tints them with the accent or status colour, and the
vocabulary gains `error` and `plugin`; buttons and menu buttons can be EMBEDDED (content-sized inline links and
glyph buttons for a host arrangement); body, description and caption text flows (wraps, keeping line breaks,
no wider than `TEXT_FLOW_COLUMNS`) in vertical stacks, form bodies, cards and sections; and an app host can mount a
pointer-only `KitView` whose controls never take keyboard focus (not part of the plugin SDK).

Borrowed panel headers (Plan 2 Task 6d): a native host that borrows a kit Panel's header (`panelHeader`) adds its
placement/collapse buttons with `setPanelHostActions` and its own header metadata with `setPanelHostHeader`
(`PanelHostHeader`: a detail after the title and count in the count role, an optional connected dot (the success
status held to the indicator floor on the panel surface), and icon-only actions with host-supplied artwork). The row
order mirrors a native tool window header: title, count, detail; the panel's own header actions; the host's
contributed actions; the host's placement/collapse actions. Metadata controls are kept by position across refreshes
and descriptor updates; `PanelHostHeader.EMPTY` clears them; a replacement header starts empty; closing the view
releases them. Host integration only, not part of the plugin SDK (plugins reach it through `PanelHost.setHeader`).

See [maintenance](ui-maintenance.md), [calibration evidence](design/theme-engine/calibration.md),
[module entry point](../jasper-ui/README.md) and [SDK architecture](sdk-architecture.md).


Plan 2 Task 9b adds `KitView.selectAll(fieldId)` (SDK UiView/host/fake mirrored): select current live Nodes.Field
text without Edited or forced focus; unknown/non-Field IDs reject IllegalArgumentException and usual EDT/open guards
apply. Find explicitly focuses then selects. Specialized EDITOR roots accept multiline text/scroll or nonstandard
JComponent document canvases; ordinary JPanel/control roots remain rejected. Shared contracts exercise module/host/fake
admission and selection. Compiled source-owner enforcement is installed by Task11b; final Editor owners are DocumentArea and
VimTextArea. Task 9c removes the temporary raw VimBar bridge; Vim feedback is ordinary descriptor content.

Plan 2 Task 9c adds mirrored SelectionMode.NONE for passive ItemList/Table rows: selectedIds must be empty,
canvas is not a focus stop, and row pointer/key selection and activation are suppressed. Embedded controls keep their
own enabled behavior and text paints at full opacity. Field.listId keys fall back to the LAF for a passive list.
Data-row height counting is separate from selection capability. Inline-row reveal waits for first viewport sizing
through pendingReveal and does not change logical row focus or keyboard focus.

Task4b shared composition: `FormKind.WINDOW` requires one content section and no hero; it fills the owner above the shared scrollable footer. `PLAIN_PATH` retains PATH/Browse without a leading icon; `DISCREET_INPUT` retains secret behavior with a stable, focus-aware reveal slot. All are mirrored in SDK/host/fake. Category fill bases `hue.{blue,green,amber,purple,neutral}.surface` are Slate Cobalt's roles (following their glyph hue). Tile paint applies the kit's tile metric (17% Dark, 13% Light) to the base once; selected tiles use onAccent independently. See the Task4b native-composition amendment for measured source calibration and compatibility costs.
