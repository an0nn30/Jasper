# Shared contracts for the three theme-engine plans

Status: implementation-planning decisions for review; no production code exists yet.
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
`swing` depends on model/theme/metrics/icons and owns all FlatLaf delegates and kit rendering.
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

## Declarative view contract

Use the following small value vocabulary in `UI/model/Nodes.java`. Each record has `String id` and
`ControlState state` first, followed by the fields listed below. Collections are immutable copies;
ids are nonblank and unique within a view. Do not use an untyped property map or arbitrary pixel grid.
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
| Secret | `String hint, String label, String revealGroup` (no string value) |
| Choice | `ChoiceKind kind, List<Option> options, String selectedId, String label, String action` |
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
| ItemList | `ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions` |
| Header | `String title, OptionalInt count, Optional<Node> context, Optional<Node> primary, List<MenuItem> overflow` |
| Panel | `Header header, Optional<Node> searchOrNavigation, Node body, Optional<Node> footer` |
| Form | `FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions` |
| Split | `SplitKind kind, List<Node> children` (semantic manager/sidebar/document divisions) |
| Table | `List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions` |
| Tree | `List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction` |
| Custom | `String contentId, CustomKind kind, String accessibleName` |

Support records, each in its own model file: `Option(String id,String label,Optional<Hue> hue)`,
`MenuItem(String id,String label,String icon,String action,boolean enabled,boolean separator)`,
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
CardKind is FORM/SETTINGS/MIXED; ListKind is SINGLE/DOUBLE/COMPACT/ORDERED;
FormKind is HERO/SHEET/FULL/SETTINGS; LayoutRole is VERTICAL/INLINE/ACTIONS/HERO/DETAIL/FOOTER/NAVIGATION;
SplitKind is SIDEBAR_DETAIL/THREE_COLUMN/DOCUMENT; CustomKind is EDITOR/TERMINAL/CHART/PREVIEW.
Header primary nodes are restricted to Button or MenuButton. MenuButton's `action` is blank for a
menu-only button; nonblank gives the main side of a split button. Its arrow opens the listed menu
without firing the main action; item actions dispatch their own ids. StatusDot uses status tokens,
never category hues. A field's browse action uses the app/SDK file chooser service; a picker action opens its domain picker.
Widths/gaps come from these semantic roles; plugins cannot pass dimensions or UI delegates.

`ViewSpec(Node root)` validates the tree and references. `UiEvent` is sealed with nested records
`Action(String id)`, `Edited(String id,String text)`, `Selected(String id,Set<String> ids)`,
`Toggled(String id,boolean checked)`, `Expanded(String id,boolean expanded)`,
`Reordered(String id,String itemId,int destination)`, `SecretChanged(String id)`.
Secret content never appears in an event, immutable descriptor or `toString()`.

`KitView` in `UI/swing/KitView.java` is a final `JPanel implements AutoCloseable`:

```java
public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events);
public void update(ViewSpec spec);
public ViewSpec spec();
public void withSecret(String fieldId, Consumer<char[]> use);
public void clearSecret(String fieldId);
public void focus(String id);
public void registerContent(String id, JComponent component, AutoCloseable lifetime);
public JComponent panelHeader(String panelId);
public void setPanelHostActions(String panelId, List<Nodes.Button> actions, Consumer<UiEvent> events);
public void close();
```

Secret arrays passed to `withSecret` are temporary copies zeroed in `finally`, including when callbacks
throw. The Vault explicitly clones/transfers a needed value to its existing async operation owner.
`update` diffs stable ids/kinds; reused controls preserve documents/caret/scroll/focus. Removing an id
disposes it and erases its secret. Changing a kind replaces just that control. Registering content
requires a matching Custom node; ordinary component trees cannot use this API.
All methods require EDT except idempotent close may enqueue disposal from another thread.
The two panel-header methods are module/app integration only and are not projected into UiView.
`panelHeader` returns the existing kit-owned header so the host can attach drag/focus handling; it
does not transfer ownership. Host actions occupy reserved header slots and use their separate callback.
For a mounted kit Panel, app ToolWindowSurface suppresses its old outer header and attaches host
placement/collapse/drag behavior to this header. Exactly one title/count/action row is visible.

The SDK mirrors Nodes/support records/ViewSpec/UiEvent in `dev.jasper.sdk.ui`, without module imports.
`UiView extends Subscription` exposes update/spec/withSecret/clearSecret/focus and typed content
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
Tests or goldens described here are future implementation work, not claims that they already pass.
