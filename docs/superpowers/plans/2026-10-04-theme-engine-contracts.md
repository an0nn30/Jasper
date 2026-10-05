# Shared contracts for the three theme-engine plans

Status: Plan 1 implementation and the combined final-review fix wave are implemented;
scoped independent rereview is complete with one known Minor expression-parity gap assigned to
Plan 2 Task 2 before imported-theme visual acceptance. Native/user acceptance is pending;
Plans 2–3 consumers remain unexecuted. See the [review record](../../design/theme-engine/verification.md).
Authority: [approved spec](../specs/2026-10-04-jasper-theme-engine-design.md).
This is a shared contract appendix, not a fourth implementation plan.

## Source and package notation

All paths in the plans are repository-relative to the `codex/theme-engine` worktree.
`UI` means `jasper-ui/src/main/java/dev/jasper/ui`; `UT` means
`jasper-ui/src/test/java/dev/jasper/ui`; `APP` and `AT` mean the corresponding
`jasper-app/src/{main,test}/java/dev/jasper/app` roots. `SDK` means
`jasper-sdk/src/main/java/dev/jasper/sdk`; `FAKE` means
`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing`.
`P(name)` and `PT(name)` mean `plugins/<name>/src/{main,test}/java/dev/jasper/<name>`.
An entry `UI/model/Nodes.java` therefore names one exact file, not an unspecified directory.

## Package graph and ownership

`theme` is JDK-only, including AWT color/font values, and never imports Swing.
`themeio` depends on `theme` and TOML parsing; `themeimport` depends on those two and parser libraries.
`model` contains JDK-only view descriptors and events; it does not depend on Swing or theme internals.
`metrics` depends on theme values where required for typography; `icons` depends on theme/metrics/model.
`swing` depends on model/theme/metrics/icons and the public data-only `themeimport.FlatLafThemeData`
adapter for real FlatLaf installation, and owns all FlatLaf delegates and kit rendering.
Keep renderer implementations in `swing` until an actual acyclic subdivision is justified; do not
create mutually dependent `components`, `forms`, `panels` and `rendering` packages.
`gallery` is test code only. Each production package gets a package-info dependency/lifetime contract.

The module has no dependency on app, SDK, terminal, Buddy, or plugins. The SDK mirrors the descriptor
contract in its existing `ui` package, avoiding a `ui` ↔ `ui.kit` package cycle. Translation lives in
`APP/plugins/KitValues.java`, never in either public model. No reflection-based bean translation.

## Theme contracts

Keep the existing theme document/inheritance model and algorithms when extracting them. Introduce
`TerminalColors` in `UI/theme/TerminalColors.java` with immutable `Color background`, `foreground`,
`cursor`, `selection`, and `List<Color> ansi` (exactly 16). The current terminal Palette has those
five fields, with foreground before background in its constructor; the adapter must map by meaning
and assert lossless round-trip rather than swapping those two arguments.

`ThemeToml.parse(String text, String origin): ThemeDocument`,
`ThemeTomlWriter.write(ThemeDocument): String`,
`ThemeResolver.resolve(ThemeDocument, Function<String,List<ThemeDocument>>): ThemeDocument`, and
`ThemeGenerator.generate(ThemeDocument): GeneratedTheme` retain their signatures except package names.
`GeneratedTheme` uses module `TerminalColors`, never terminal `Palette`.

Add `SemanticColor` with `String key()` and `Optional<SemanticColor> of(String key)`.
Its enum identifiers are the uppercase semantic names with dots replaced by underscores.
The complete standard color set is:

```text
surface.app surface.panel surface.panel.raised surface.dialog surface.card surface.sidebar
surface.input surface.control surface.control.hover surface.menu surface.terminal surface.scrim
border.window border.subtle border.input border.control border.menu border.focus border.error
text.primary text.secondary text.tertiary text.placeholder text.onAccent text.onAccent.secondary
text.link text.link.hover text.error text.success
accent accent.hover selection.bg hover.overlay segment.selected.bg toggle.track.off toggle.knob
status.success status.warning status.danger status.idle
hue.blue hue.green hue.amber hue.purple hue.neutral
button.secondary.bg button.secondary.bg.hover
```

`SemanticPalette(Map<SemanticColor,Color> values)` validates complete coverage and stores a defensive
copy; `color(SemanticColor): Color` never returns null. Extend `GeneratedTheme` with this palette.
Name the new GeneratedTheme record field `SemanticPalette semantic`; add
`Map<SemanticColor,Color> semanticOverrides` to ThemeDocument. Keep terminal, syntax, workspace glass,
and existing tab/editor-specific roles as supported engine
values, not new ad hoc literals in consumers. Legacy `Token` becomes internal compatibility input.

Write canonical semantic overrides in TOML `[semantic]` using quoted dotted keys, e.g.
`"surface.card" = "#202020"`. Existing `[tokens]`, `[colors]`, `[ui]`, syntax and workspace sections
remain readable. Writer retains unknown non-styling metadata; never rewrite a user's source merely
because it was read. Explicit canonical values override translated legacy/import colors. Missing
canonical values derive from the resolved old generator as follows; the right side names old tokens:

| Canonical roles | Base |
|---|---|
| app; panel; panel.raised; dialog; card; sidebar | TITLE_BAR; SURFACE; SURFACE_RAISED; SURFACE; SURFACE_SUNKEN; SIDEBAR |
| input; control; control.hover; menu; terminal | INPUT_BACKGROUND; BUTTON_BACKGROUND; HOVER; SURFACE_RAISED; terminal background |
| window; subtle; input; control; menu borders | BORDER; SEPARATOR; BORDER; BUTTON_BORDER; BORDER |
| primary; secondary; tertiary; placeholder text | TEXT; TEXT_MUTED; TEXT_MUTED; TEXT_DISABLED |
| onAccent; onAccent.secondary; link; error; success | TEXT_ON_ACCENT; TEXT_ON_ACCENT at 60%; LINK; ERROR; SUCCESS |
| accent; selection.bg; selected segment; off track; knob | ACCENT; SELECTION; BUTTON_BACKGROUND; BUTTON_BACKGROUND; TEXT_ON_ACCENT |
| status success/warning/danger/idle | SUCCESS; WARNING; ERROR; TEXT_DISABLED |
| blue/green/amber/purple/neutral hue | ANSI blue/green/yellow/magenta; TEXT_MUTED |
| secondary button bg; secondary button hover | BUTTON_BACKGROUND; HOVER |

Dependent defaults resolve from the final canonical parent: `border.focus` from `accent`,
`border.error` from `status.danger`; `accent.hover` and `text.link.hover` mix their final base with
`text.primary` at 8%; `hover.overlay` uses `text.primary`. `surface.scrim` uses the darkest terminal
ANSI color by luminance as its base. The kit applies scrim 55%, tile 16%, focus 25%, error ring 22%,
row hover 5%, ghost hover 8%, disabled 42%, and impossible reorder action 30% exactly once.
Validate topological order, including explicit overrides of dependent parents. Source Appendix A
values belong only in the calibration theme fixture, not into component fallback code.

Import palette resolution must retain wildcard/reference precedence and explicit color alpha.
Reject vendor class/delegate overrides; report discarded font/arc/inset/size settings once per key.
Keep import archive/path/XML limits and source-fingerprint conflict detection unchanged.

## Metrics and appearance publication

`DesignMetrics` is an immutable role-to-value catalog. Defaults: field/button 28, hero field 32,
hero button 30, panel action 24, inline action 22, form label 96, column gap 12, card pad 14,
row gap 10, section gap 20, sheet section gap 14, form sides 24, hero sides 28, control radius 6,
card radius 9, window radius 12, segment track/item radius 7/5, focus ring 3, panel header 38,
panel footer 34, switch 32×18 with 14 knob and compact 28×16 with 12 knob.
Typography role sizes: hero 15, detail 16, window/panel/body/bodyStrong 13, label/description 12.5,
caption/monoSmall 11.5, section/count 11, chip 9.5, mono 12. Resolve actual platform family centrally.
Task 7 source-derived named metrics: segment inner padding 2, chip height/padding/radius 16/5/4,
status dot 6, strength height/gap/radius 3/3/2, from preserved source §§8.5/8.8/8.9/8.17.
Progress reuses the source's 3px meter thickness; meter widths fit content and available layout.
Switch confirmation motion is 135ms (within the source's 120–150ms range). Focus margins are
outside the exact 32×18 or 28×16 track; they do not enlarge that painted track.
Standard title/section weights are 600, bodyStrong and selected labels 500, other text 400.
Resolve ranges this way once; mock-calibration corrections are reviewed changes to named metrics.

`ThemePair(GeneratedTheme chrome, GeneratedTheme workspace)` is immutable.
`ThemeBinding` is a concrete EDT-confined owner with `current(): ThemePair`,
`subscribe(Consumer<ThemePair>): AutoCloseable`, `publish(ThemePair): void`, and `close(): void`.
Subscribe replays immediately; listener failures reach a constructor-supplied `Consumer<Throwable>`
and do not prevent later listeners. Publication and teardown are idempotent where appropriate.
`ThemeRoute` has CHROME and WORKSPACE. `KitContext(ThemeBinding, ThemeRoute, DesignMetrics,
IconRegistry, BooleanSupplier animationsEnabled)` supplies rendering context.
The app owns transaction/rollback around `FlatLafInstaller.install(GeneratedTheme): void`.

Tasks 4–5 concrete API choices (approved execution rulings): `DesignMetrics.DEFAULT` and
`value(DesignMetrics.Role)` expose fixed logical geometry. `contentHeight(Role,Font,FontRenderContext)`
fits real font line metrics plus named vertical padding without changing catalog values.
`Typography` owns its own identically named `Role` enum, keeping metrics independent of model;
`Typography(String family,float bodySize)` resolves roles proportionally to a 13-point body base.
`font(Role,boolean selected)` applies medium selected-label weight. `KitContext` exposes the supplied
values through named accessors, `theme()` selects its route, and `typography()` uses the unscaled
host `UIManager` `defaultFont` override (otherwise central platform defaults), never FlatLaf's already
scaled font. Renderer code applies Swing user scaling exactly once. `motionPolicy()` creates a
view-owned `MotionPolicy` backed by the context supplier; effects `register(Runnable settle)` and
poll `enabled()` each frame/appearance refresh so disabled motion settles running effects once.

`ThemeBinding(ThemePair,Consumer<Throwable>)` confines every operation and subscription close to EDT,
suppresses equal pairs, rejects reentrant publication, contains listener/error-handler exceptions,
and uses independent subscription identities. `AppearanceTransaction` snapshots raw LAF and developer
defaults separately (merged `entrySet` must not be used), invokes restore hooks on failure, and restores
the exact previous LAF and both layers even if a restore hook throws. Success publishes only after install.

`IconArtwork(String pathData)` uses kit-owned 16-grid/1.5 stroke; the metadata constructor rejects
other grids and stroke outside 1.4–1.6. Paths are fully validated before the tolerant SVG parser is used,
with no markup, resources or colors and bounded size/commands/geometry. `IconRegistry.names()` returns
an immutable name snapshot; `register` returns idempotent `IconRegistration` (an `AutoCloseable`).
The required hue-based `icon` method is joined by an explicit `SemanticColor` tint overload, `size(IconSize)`,
and `tile(name,palette,hue,logicalSize,selected)` for shared 16% category/18% selected tile treatment.
Built-in paths are original Jasper artwork. JSVG 2.0.0 (already FlatLaf Extras' runtime dependency) is
explicitly declared for its vector path parser; no vendor types appear in public signatures.

## Declarative view contract

Use the following small value vocabulary in `UI/model/Nodes.java`. Each record has `String id` and
`ControlState state` first, followed by the fields listed below. Collections are immutable copies;
node ids are nonblank and unique within a view. Option/item/row/column/tree data ids are
unique within their owning control; references resolve only in that control. Do not use an untyped property map or arbitrary pixel grid.
`Node` is sealed; the records below implement it. `ControlState` is a record of `boolean visible`,
`boolean enabled`, `boolean busy`, and `String error`, with constant `READY`.

| Record | Additional fields |
|---|---|
| Text | `String text, TextRole role` |
| Icon | `String name, Hue hue, IconSize size, String accessibleName` |
| Button | `String label, String icon, ButtonKind kind, String action, String tooltip` |
| MenuButton | `String label, String icon, ButtonKind kind, String action, List<MenuItem> items, String tooltip` |
| StatusDot | `StatusKind kind` |
| Field | `FieldKind kind, String value, String hint, String label, String trailingAction` |
| Secret | `String hint, String label, String revealGroup, SecretKind kind, String copyAction` (no string value) |
| Choice | `ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon` |
| Toggle | `String label, boolean checked, String action, boolean compact` |
| Chip | `String label, Hue hue` |
| Progress | `double fraction, String label` (-1 means indeterminate) |
| Strength | `int filled, String label` (0–4) |
| Stack | `LayoutRole role, List<Node> children` |
| FormRow | `String label, String caption, Node control, Optional<Node> trailing` |
| SettingRow | `String title, String description, Node control, boolean dependent` |
| Card | `CardKind kind, List<Node> children` |
| Section | `String title, Node body` |
| Empty | `Node icon, String title, String description, List<Node> actions` |
| ItemList | `ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction` |
| Header | `String title, OptionalInt count, Optional<Node> context, Optional<Node> primary, List<MenuItem> overflow` |
| Panel | `Header header, Optional<Node> searchOrNavigation, Node body, Optional<Node> footer` |
| Form | `FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction` |
| Split | `SplitKind kind, List<Node> children` (semantic manager/sidebar/document divisions) |
| Table | `List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode` |
| Tree | `List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction` |
| Custom | `String contentId, CustomKind kind, String accessibleName` |

Support records, each in its own model file: `Option(String id,String label,Optional<Hue> hue)`,
`MenuItem(String id,String label,String icon,String action,boolean enabled,boolean separator,Nodes.Hue hue)`,
`ListItem(String id,Optional<Node> leading,List<Node> title,List<Node> subtitle,List<Node> trailing)`,
`Column(String id,String title,TextRole role,boolean numeric)`,
`TableRow(String id,Map<String,String> cells,Optional<String> icon)`,
`TreeItem(String id,String title,Optional<String> icon,boolean leaf,List<TreeItem> children)`.

Enums are nested in `Nodes`: TextRole is HERO_TITLE/DETAIL_TITLE/WINDOW_TITLE/PANEL_TITLE/BODY/
BODY_STRONG/LABEL/DESCRIPTION/CAPTION/SECTION_LABEL/COUNT/CHIP/MONO/MONO_SMALL; Hue is
BLUE/GREEN/AMBER/PURPLE/NEUTRAL/NONE; IconSize is CAPTION/CONTROL/RAIL/EMPTY/HERO;
ButtonKind is PRIMARY/SECONDARY/GHOST/DESTRUCTIVE_GHOST/LINK;
StatusKind is SUCCESS/WARNING/DANGER/IDLE;
FieldKind is TEXT/SEARCH/PORT/PATH/TEXTAREA; ChoiceKind is DROPDOWN/PICKER/SEGMENTED;
CardKind is FORM/SETTINGS/MIXED; ListKind is SINGLE/DOUBLE/COMPACT/ORDERED; SelectionMode is SINGLE/MULTIPLE;
FormKind is HERO/SHEET/FULL/SETTINGS; LayoutRole is VERTICAL/INLINE/ACTIONS/HERO/DETAIL/FOOTER/NAVIGATION;
SplitKind is SIDEBAR_DETAIL/THREE_COLUMN/DOCUMENT; CustomKind is EDITOR/TERMINAL/CHART/PREVIEW.
Header primary nodes are restricted to Button or MenuButton. MenuButton's `action` is blank for a
menu-only button; nonblank gives the main side of a split button. Its arrow opens the listed menu
without firing the main action; item actions dispatch their own ids. StatusDot uses status tokens,
never category hues. A field's browse action uses the app/SDK file chooser service; a picker action opens its domain picker.
Form permits at most one PRIMARY in its action list. `cancelAction` is an explicit semantic action
key; blank means no cancellation. The prior constructor without this final argument delegates to
blank for existing callers. Cancellation is never inferred from a localized label. Enter submits the
primary only when visible/enabled validation state is valid and no applicable operation is busy;
textarea newlines and open-menu confirmation keep their own bindings. Escape dismisses a popup
first, then delegates a subsequent Escape to the containing form's cancel action, including when
focus stays on a menu button. Cancellation cleanup remains the domain owner's responsibility.

FormRow permits a nonnull blank label for helper rows: this reserves the same fixed label cell and
places the helper node in the control column. Actual input descriptors still require their labels
for accessibility. Embedded inputs suppress duplicate displayed labels while retaining their
accessible names; moving back to standalone composition restores their labels. SettingRow's
`dependent` flag is domain presentation information; it never resets the child value automatically.

Widths/gaps come from these semantic roles; plugins cannot pass dimensions or UI delegates.

`ViewSpec(Node root)` validates the tree and references. `UiEvent` is sealed with nested records
`Action(String id)`, `Edited(String id,String text)`, `Selected(String id,Set<String> ids)`,
`Toggled(String id,boolean checked)`, `Expanded(String id,String itemId,boolean expanded)`,
`Reordered(String id,String itemId,int destination)`, `Removed(String id,String itemId)`, `SecretChanged(String id)`.
Event identifier mapping (approved Task 7 ruling): `Action.id` is the configured action key
(Button/MenuButton/menu item, Browse, or Choice PICKER opening). Every payload-bearing event
(`Edited`, `Selected`, `Toggled`, `Expanded`, `Reordered`, `Removed`, `SecretChanged`) identifies its owning
node. For Choice DROPDOWN/SEGMENTED, `Selected.id` is the Choice node id and `ids` contains its
local option id; for Toggle, `Toggled.id` is the Toggle node id. Hosts/plugins resolve the node's
configured `action` from the current spec when routing selection/toggle intent. These operations
emit no additional generic Action, preserving the payload and node context for inline rows.
Secret content never appears in an event, immutable descriptor or `toString()`.

Task 9 approved corrections (mirror exactly in the later SDK projection):
- ItemList/Table explicitly select SINGLE or MULTIPLE; the old constructors default to SINGLE,
  and the ItemList old constructor has no add footer. SINGLE rejects more than one selected id.
  MULTIPLE displays real checkboxes; mode never depends on selectedIds cardinality. Changing mode
  reasserts the descriptor selectedIds, so a SINGLE transition cannot retain pending multiple selection.
- ItemList.addAction is an optional Button inside the bounded list surface, below the data rows.
  It emits its configured Action and is never a selectable item. ORDERED rows emit Reordered with
  the list owner id, stable local itemId and zero-based destination, or Removed(owner,itemId).
  Impossible first/last direction buttons use the ordinary button painter at 30% exactly once;
  other disabled buttons remain 42%, preserving authored alpha.
- Expanded identifies the tree owner in id and the tree-local item in itemId. Expansion and
  inline toggle/choice values are confirmed by the next descriptor. Collapsing a branch only hides its
  rows; selection membership still uses the complete tree descriptor. Tree flattening is bounded
  in mounted components, including large expanded branches.
- Row pointer selection (single click), keyboard navigation and multi-checkbox selection emit
  Selected(owner,ids). Double click/Enter activation first emits Selected for the activated item,
  then Action(activateAction), when nonblank. Context opening selects an unselected clicked row;
  context invocation emits current Selected(owner,ids), then Action(menuItem.action). A synchronous
  selection-confirming update keeps the intended action. Closing/removing the owner/item or rebinding
  that action during Selected suppresses the stale subsequent Action. Inline controls never activate
  the containing row; Toggle emits only Toggled with its globally unique node id.
- ListItem slots accept Text/Icon/Chip/StatusDot/Progress/Strength, Button/MenuButton/Toggle/Choice,
  and recursively INLINE Stacks only. Mutable Field/Secret, Custom, nested data and other composition
  kinds are rejected, rather than silently losing edits during recycling. Ordinary fields/query
  inputs remain outside virtual rows in the stable parent composition. Inline Choices suppress their
  standalone label while retaining accessible names; descriptor-only content measurement reserves usable
  input height in single/two-line lanes without creating offscreen controls. Standalone inputs are unchanged.
- Only viewport rows plus two overscan rows on each side own live controls. Row shells are reused;
  same mounted node id/kind retains its control, while a recycled row closes old control listeners,
  popups and motion owners. Controlled values remain in descriptors. Offscreen focus is remembered by
  list item/control identity; KitView.focus(globalInlineNodeId) indexes data and scrolls/mounts the
  target instead of allocating every offscreen widget. Removal clears that logical identity. Native
  desktop focus acceptance remains a user-run check; headless coverage verifies identity and teardown.
- Reorder/filter retains local selection for surviving item ids when the declared selection is
  unchanged. ViewSpec still rejects selected ids absent from current data; the host owns selection
  outside a filtered dataset and may restore it on repopulation. No removed domain objects are cached.
  Mono row/navigation text uses leading ellipsis; navigation reserves action width before path width.
- Current consumers remain unchanged until Plan 2. Task 10 composes these owners into real Panels;
  this task verifies query-document preservation through the existing parent Stack composition.

`KitView` in `UI/swing/KitView.java` is a final `JPanel implements AutoCloseable`:

```java
public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events);
public void update(ViewSpec spec);
public ViewSpec spec();
public void withSecret(String fieldId, Consumer<char[]> use);
public void clearSecret(String fieldId);
public void replaceSecret(String fieldId, char[] value);
public void focus(String id);
public void setAvailableHeight(int logicalPixels);
public void registerContent(String id, JComponent component, AutoCloseable lifetime);
public JComponent panelHeader(String panelId);
public void setPanelHostActions(String panelId, List<Nodes.Button> actions, Consumer<UiEvent> events);
public void close();
```

Secret arrays passed to `withSecret` are temporary copies zeroed in `finally`, including when callbacks
throw. The Vault explicitly clones/transfers a needed value to its existing async operation owner.
`update` diffs stable ids/kinds; reused controls preserve documents/caret/scroll/focus. Removing an id
disposes it and erases its secret. Changing a kind replaces just that control. Registering content
uses `Custom.contentId`, not its node id, and requires a matching Custom node. Content ids are unique
within the view. Stable node id/kind/contentId retains registration; replacing any identity closes it.
Ordinary component trees cannot use this API.
All methods require EDT except idempotent close may enqueue disposal from another thread.
`setAvailableHeight` is module/app integration only and is not projected into UiView or descriptors.
The app supplies a positive logical content height excluding native chrome; the latest supplied
value lasts for this view's lifetime and is scaled exactly once through Swing user scaling. The
initial value is unconstrained. Forms cap their preferred height at 85% of the supplied height and
scroll the body independently of the reachable action/preview footer. Native screen queries and
plugin-provided geometry are forbidden. Native title bars/windows/scrims stay app-owned; sheet
content has an in-content title and bottom-only rounded corners.
Panel Header.count is an explicit **total collection count**, not the number of filtered results.
A present zero hides count and search/navigation while keeping the latter mounted. A positive total
retains both through filtered-empty body changes. An absent count hides the count only: the size is
unknown, so search/navigation retains its declared visibility. Callers must supply zero to request
automatic collection-empty hiding; the renderer never infers emptiness from body kind or text.
Header/footer/query identity survives body transitions and appearance refreshes. A short panel scrolls
navigation and the body (data owners retain a bounded viewport), keeping the footer attached and reachable.
A SettingRow used directly as a panel footer receives the dense footer treatment with wrapped text;
its ordinary settings/card treatment is restored when moved elsewhere.

The two panel-header methods are module/app integration only and are not projected into UiView.
`panelHeader` returns the existing kit-owned header so the host can attach drag/focus handling; it
does not transfer ownership. Host actions occupy reserved header slots and use their separate callback.
For a mounted kit Panel, app ToolWindowSurface suppresses its old outer header and attaches host
placement/collapse/drag behavior to this header. Exactly one title/count/action row is visible.

The SDK mirrors Nodes/support records/ViewSpec/UiEvent in `dev.jasper.sdk.ui`, without module imports.
`UiView extends Subscription` exposes update/spec/withSecret/clearSecret/replaceSecret/focus and typed content
registration via `void registerContent(CustomContent content)`, but never `component()`. `UiKit.create(ViewSpec,Consumer<UiEvent>): UiView` is implemented
by `HostedKit` and `FakeKit`. `PluginContext.kit(): UiKit` supplies the factory. `KitValues` exhaustively
maps the two sealed vocabularies and events. Host-owned views cannot be passed to another plugin.
`Panels`, `Windows`, `WindowSurface` and settings adapters mount UiView handles at the cutover.
SDK `UiKit.registerIcon(IconAsset): Subscription` accepts `IconAsset(String name,String pathData)`
using a namespaced name and 16-grid monochrome SVG path data; stroke/tint/size remain kit-owned.
UI `IconRegistry.register(String name,String pathData): AutoCloseable` is the module equivalent.
Registration validates path syntax and disposes with the plugin; foreign resources/embedded colors are
not part of this path-only format. `Appearance.color(SemanticColor): Color` and
`onChanged(Consumer<Variant>): Subscription` provide read-only semantic access and all theme updates,
including same-variant changes. Mirror SemanticColor in the SDK; retain supported syntax/editor/opacity
values as host-provided custom-content values without static UIManager lookup.

New SDK custom content is explicit `CustomContent(String id,CustomKind kind,JComponent component,
AutoCloseable lifetime)` and receives semantic appearance/opacity refreshes. Only approved custom
content code may construct the specialized Swing widget; other plugin UI uses descriptors.
Task 11–12 execution ruling: this is a generic capability, without plugin-id restrictions. Runtime
validation accepts a multiline text/editor root (or its scroll wrapper) for EDITOR, and a purpose-built
JComponent canvas/preview root for TERMINAL/CHART/PREVIEW. Ordinary JPanel form trees, buttons,
labels, fields and data/choice controls are rejected as specialized roots. Necessary implementation
children under an accepted specialized root are allowed; this is API discipline, not a sandbox.
Host registration ownership is recorded independently of widget class names, with cross-view/component
reuse rejected; kit-owned roots cannot be re-registered. Plan 2 supplies the source-owner inventory.
The host refreshes registered root semantic foreground/background and workspace opacity. Domain code
uses the existing Appearance editor/syntax/workspace subscription APIs to refresh specialized styling;
the host never reaches into editor vendor internals or replaces its document/caret. `onChanged` covers
both chrome and workspace theme changes including same-variant updates; opacity-only changes remain
`onWorkspaceChanged`. The Plan 1 KitThemeBridge copies explicit old app values into independent module
values; existing application theme ownership and consumer APIs stay in place until Plan 2.
Kit factories initialize EDT resources lazily: legacy contexts may be created off EDT, but actual
create/update/read/input/icon/content operations retain strict EDT confinement. Off-EDT close requests
immediately revoke handles and enqueue owned teardown. Event and content-lifetime failures have fixed
sanitized diagnostics, never throwable messages or causes that could retain plugin secret text.


## Test support and verification conventions

`UT/testsupport/Edt.java` provides `static void run(Runnable)` and propagates assertion failures from
`SwingUtilities.invokeAndWait` to the caller; all Swing tests use it. `KitFixtures` in the same test
support package provides `context()`, `dark()`, `light()`, `custom()`, `panel(String name)`,
`form(String name)` and `render(KitView,int width,int height,double deviceScale): BufferedImage`.
Fixtures use this contract, not application or SDK classes. Font/layout scaling is separate from the
device-scale transform; never double-scale logical metrics. Core test methods shown in plans live
inside JUnit classes with normal imports; their code blocks contain complete test methods.

Do not share a Swing-global UIManager concurrently between fixtures. Always restore global LAF/font
state in finally and close every view/binding. Async tests use controllable futures and EDT draining,
never real networking, native windows, arbitrary sleeps, or the user's credential stores.

Every task follows red → implement → green → review → commit. Review uses the repository's
per-task implementer/reviewer workflow once execution is approved. Each commit ends with
`Co-Authored-By: Codex <noreply@openai.com>`. Do not merge or push without user authorization.
Run broad `./gradlew check` only at runnable-deliverable boundaries or when a failure justifies it.
No GUI, benchmarks, AOT training, packageDist, or `-Pjasper.aot=true`.

For each plan finish, record real command results and XML counts in the plan banner and STATUS;
perform independent whole-branch review, resolve findings, and repeat only affected verification.
Plan 1 tests and gallery now have recorded task evidence; remaining consumer/surface tests are
future Plans 2–3 work. The final whole-branch gate and independent review were completed in Task 14; the known Minor
expression-parity gap is carried to Plan 2 as recorded below.


### Task 13 calibration clarifications

Nominal control bounds are their full visible fill/border geometry (28 default/32 hero), with external
3-pixel focus/error halos owned by the view and clipped by real viewports. Named rounded-rectangle
radii map to twice-radius Java arc diameters. The kit owns placeholder paint once. HERO and embedded
card labels do not duplicate visible labels. Source-derived gaps/heights remain unchanged.

`Choice.icon` names PICKER artwork; empty means no cue. Nonempty cues on other kinds are rejected.
The original seven-argument constructor delegates to key for PICKER and empty otherwise. `MenuItem.hue`
explicitly controls named category artwork; the original six-argument constructor delegates NEUTRAL.
Both changes mirror through UI/SDK and host projection, without domain inference from ids/text.

Named manager roles supply 192/272 sidebar/list widths plus remaining detail, with 24 detail padding;
hidden panel headers consume zero height. Category tile roles use 20 single/rail, 28 double-list,
32 sheet and 44 detail pixels, determined by explicit parents, IconSize and ListKind. Ordinary
CONTROL icons remain glyphs. Ordered rows use bounded input chrome, separators and leading add;
settings choices use the 160 trailing-width role. FormRow helper CAPTION/DESCRIPTION text wraps.
Gallery evidence and scale/crop anchors are in [calibration](../../design/theme-engine/calibration.md).


## Combined final-review contract rulings — 2026-10-04

`ThemeGenerator.generate(document, RuntimePlatform)` resolves a deterministic MAC/WINDOWS/LINUX/OTHER
snapshot; the original overload selects `RuntimePlatform.current()` from the JDK OS property. The
model never reads Swing defaults. Shared `UiTokenKeys` supplies representative inverse keys and
legacy aliases. Ordered authored UI colors (including platform branch/default, references, aliases,
button gradient-start pairs and wildcard order) populate semantic legacy bases. Explicit native
`[tokens]` override that inverse input; explicit `[semantic]` values apply last. Authored focus colors
supply border.focus when present; otherwise it still derives from final accent. Component.borderColor
installs the matching border.input role. Imported token provenance and post-JSON exclusions remain
owned by the existing importer; no import goldens or source assets were regenerated.

The pure bounded evaluator supports admitted deterministic color functions, references and alpha;
selected function cases are differentially checked against pinned FlatLaf 3.7, not a complete parity
guarantee. Known Minor: spin(relative) hue handling and one-channel spin/rgba rounding drift remain
for Plan 2 Task 2 correction before imported-theme visual acceptance; exact reproductions are in
[UI maintenance](../../ui-maintenance.md#known-minor-expression-parity-gap). `systemColor` uses its explicit fallback
because the pure model has no native color source. Missing/unresolvable fallback, missing required
references and cycles yield origin/key-aware actionable diagnostics; optional `$?` references may
resolve to null. Function `derived`/`lazy` inputs become colors in the immutable snapshot, with
no live UI lookup. New publications recompute from current authored inputs.

`SecretKind` is INPUT or DETAIL. The five-argument constructor remains INPUT with no copy action.
DETAIL is read-only, uses mutable owned character storage and char-array painting, and keeps reveal
and optional copy controls enabled independently of editability. No stored value enters a text
component, descriptor, event, metadata or diagnostic. `replaceSecret(id, char[])` synchronously copies
caller-owned data without an edit event, and works for INPUT and DETAIL. The caller retains and erases
its original. Replacement, kind change, removal and close erase owned data; detail replacement and
clear also remask. `withSecret` always erases its callback copy in finally. A detail copy control emits
only `Action(copyAction)`; the domain handler calls `withSecret` and transfers to its existing credential
clipboard/async owner, which remains responsible for that independent lifetime. The kit never silently
writes the system clipboard. Fake input simulation rejects DETAIL; the fake/host mirror transfers.

A nested `update` during reconciliation is rejected before mutation by KitView, HostedKitView and
FakeKitView. Disposal callbacks retain existing contained-diagnostic behavior, outer reconciliation
commits its matching descriptor/tree, and each lifetime closes exactly once. Reentrant close remains
allowed. Ordinary event callbacks outside reconciliation may still update synchronously.

Dropdown popup list/background/border/row height use the menu treatment and refresh on the existing
popup/list during same-variant changes. Selected segment labels use central medium weight, scaled once.
All named radii are full radii (Java arcs receive twice the metric). Row context items retain their
explicit hue and refresh retained icons on appearance publication. Icon paths with only moves/closes
are rejected by both the SDK grammar and module artwork validation.

Plan 2 must replace HostedKit's temporary always-enabled animation supplier with the real live
`ui.animations.enabled` source before exposing migrated production surfaces. This obligation remains
in Plan 2 and the temporary-path ledger. Full app composition, font/native gesture acceptance and
user review of gallery artifacts remain Plan 3/user-run; the secure detail capability is completed
in Plan 1 rather than deferred.


Final-review compatibility ruling: canonical roles honor authored supported UI colors ahead of
FlatLaf theme-name/author-specific post-JSON heuristics. For example, Carbon's authored Panel
`#202020` and TextField `#112233` resolve/install `surface.input=#112233`; the foreign heuristic
`lighten($Panel.background,4%)` does not replace that role. The model has no vendor-name logic or
new provenance mechanism. Existing importer exclusions/provenance and ancillary legacy UI behavior
remain; this is not full vendor visual parity. Themes relying on those heuristics may need explicit
semantic adjustment during Plan 2 visual acceptance. A concrete Carbon regression preserves this ruling.

## Plan 2 Tasks 2–3 integration corrections (2026-10-05)

The app `ThemeController` keeps selection, persisted names, configuration schema and file policy.
It publishes module `ThemePair` through `AppearanceBindings.binding()`; `context(ThemeRoute)` supplies
app-native views with controller-owned icons/metrics. Hosted kits consume the same module snapshots
without conversion. `TerminalPaletteAdapter.toTerminal(TerminalColors)` maps all 20 colors by meaning,
including alpha. App snapshots carry module GeneratedTheme values. Application shutdown closes bindings.

`ThemeGenerator` resolves authored UI tokens before recomputing dependent tokens and syntax.
Explicit document tokens win in this value graph; canonical semantic overrides are last. Raw legacy
controls temporarily retain native `[ui]` last-word colors through the app adapter, independently of
canonical kit roles. Generic native chrome aliases and named `ColorPalette.accentColor` are interpreted
in the module, with no vendor-name heuristics. Native source files are never rewritten by reading them.
Only unresolved, syntactically valid, top-level unprefixed bare names from old native exports may fall
back. Required `@`/`$` references, cycles, malformed colors/functions and missing required operands
remain errors. Known bare names continue resolving normally.

Pinned FlatLaf parity: spin always rotates hue absolutely, including `relative`; RGB(A) percentage
channels truncate like the pinned parser, while HSL uses float hue interpolation before Color channel
materialization. This corrects the carried green-spin and 50%-alpha probes without a rounding-policy exception.

Import publication uses these public module utility boundaries (no SDK projection):
`ThemeSources.hash(byte[]): String` is SHA-256; `safe(Path,String): Path` retains existing containment
validation. `ThemeConverter.archive(Path): Batch` explicitly reads staged archives regardless of suffix.
`MAX_ARCHIVE=64 MiB`, `MAX_FILE=4 MiB`, `MAX_PROVIDERS=64`, `MAX_NESTED_JARS=16` expose existing limits
for app journal/publication guards; ThemeArchive stays package-private. `explicitTokens(Map<String,Object>)`
returns authored legacy token ownership without vendor-name inference. The app keeps locks, identities,
fingerprints and atomic filesystem publication. Documents cross this boundary directly, preserving
semantic overrides during rename; no serialization bridge is used.

KitContext typography uses the raw developer defaultFont family and, when a raw developer Label.font
also exists, that exact unscaled body size. It never reads an inherited/scaled LAF Label.font as an
explicit body setting. KitView invalidates on both override changes/removal even when ThemePair is equal.
This preserves existing app title/menu offsets and fractional user form/body settings with one scaling pass.

Color-policy correction: `*.disabledText`, `*.selectionBackgroundInactive` and
`*.selectionForegroundInactive` are color suffixes. Proven exact color keys
`ScrollBar.trackHighlight`, `TabbedPane.focus`, and `VersionControl.RefLabel.backgroundBase`
are admitted without broad `focus`/`backgroundBase` heuristics. Values still require the supported
color grammar; booleans, Java delegates, border geometry and font/metric values remain rejected.
The pinned FlatLaf 3.7 properties/UI readers and unchanged Nord source provide the concrete evidence.
Conversion reports discarded geometry once per key and does not diagnose these restored color slots.
The existing color-policy golden gains only four proven color lines; original upstream and legacy
golden bytes remain untouched.

Tasks 2–3 review correction: each generation resolves authored UI expressions once against the complete
initial fallback map. Rebuilt dependent tokens and semantic colors reuse those resolved values and authored
focus provenance; semantic construction must not reevaluate an expression against the rebuilt defaults.
Explicit document tokens and final semantic overrides retain their precedence. App native-file publication
validates full generation of the serialized/resolved candidate before staging or replacement; import planning
uses the same validator for actionable source failures, and commit independently revalidates frozen plans.


## Plan 2 Task 4 mounted-view contracts and temporary names

The coordinated final signatures remain PanelFactory.create → UiView, Panels.register with that factory,
WindowSurface.setContent(UiView), Windows.openTab(ContentSpec,WindowOwner,UiView), SettingsPanel.create
→ UiView and SettingsPanelHost.row → Nodes.Node. During bundled migration only, PanelFactory.View /
Panels.registerView and SettingsPanel.View.createView / SettingsPanelHost.rowNode carry the typed path.
A SettingsPanel.View implementer needs no dummy legacy create; hosts select that marker deterministically
and reject null/foreign/disposed/already-mounted views. The legacy SettingsPanel remains functional until
Task 11. PanelSpec accepts either a legacy Icon or semantic iconName, never both/neither, and preserves
anchor, split and toggle-action identity. Exact member removal mapping is in the temporary-path ledger.

Views have one surface lifetime. Re-setting the identical view is idempotent; replacing window content
closes the old view; final window/panel/tab/settings closure closes its view once. Tab reparenting retains
the same component/document and lifetime. Duplicate content keys select their existing entry without
consuming the unused caller-owned view. Failed tab publication releases the attempted mount claim.
Foreign views are rejected before ownership transfer. Existing chooser pinning and close/quit guards
are retained; document owner closure discards chooser results. Custom-content hierarchy changes resample
workspace opacity while retaining editor document, caret and the separate editor palette route.

Standard settings row descriptors use host-owned settings:<key> ids, receive draft stage/reset/validation,
and intercept their events before the plugin callback. Unrelated events reach the plugin once. Apply
persists declared values through the existing settings-file owner; Cancel discards the draft; disposal
and plugin shutdown stop row callbacks and release the view. The actual custom-page owner is
PluginPanelPage (the plan brief named PluginSettingsPage, which owns generated legacy pages).

Motion integration is live: ConfigurationController feeds ThemeController independently of theme-install
success; AppearanceBindings reads its supplier and PluginRuntime passes the same source to HostedKit
before startup. Existing effects poll and settle on their next frame; no global UI default controls motion.


### Task 4 review round 1 persistence/reload/retry clarification

Standard row Reset stages an absent override (deletes the persisted key on Apply), distinct from
programmatic stage(default), which retains an explicit default when a value was saved. Clean rows
follow saved reloads; pending edits survive differing reloads and are removed when the saved
representation matches the edit. A page closes its reload subscription with its view. Real and fake
hosts verify these rules independently; the fake's row helper now projects descriptors only and its
own draft/setting conversion supplies behavior, without a new SDK helper or reflective framework.

Reopening an existing typed content key still enters ContentRegistry.open, so an entry stranded by a
failed move and failed recovery can reattach. It uses the original component/view; the unused incoming
view remains caller-owned. Generic custom-content document/caret/opacity tests are not evidence of
actual Editor-plugin applied syntax palette; the latter remains an Editor migration obligation (Task 9).
