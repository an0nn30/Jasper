# UI architecture

`jasper-ui` is the theme engine and Swing kit that draws the whole workstation: windows, dialogs, tool windows,
Settings and every plugin view. The application composes it with the terminal library and Buddy; plugins compile
against the JDK-only SDK, whose [`dev.jasper.sdk.ui`](sdk-reference.html#package-dev.jasper.sdk.ui) values the
application translates into this module's model. The module is the only colour authority: jasper-app's
`ColourLiteralScanTest` fails on a colour literal in production code or resources outside the theme files and the
boot splash's `SplashColors` ([maintenance](ui-maintenance.md#rules-the-tests-enforce)).

## Packages

| Package | Allowed outgoing UI packages | Ownership |
| --- | --- | --- |
| `theme` | none | Jasper theme files (the built-in Slate Cobalt, Catppuccin, Ubuntu Yaru and Jasper Meadow themes and installed themes), semantic palettes and derived roles, terminal and editor schemes, syntax styles, glass, FlatLaf colour keys and paint-time contrast guards; EDT appearance binding |
| `model` | none | Immutable descriptors and events; no secret values |
| `metrics` | `theme` | Logical geometry catalogs (`DesignMetrics`, `DEFAULT` and `CLASSIC`) and the 11 public `MetricTokens` a kit file tunes, type scales and faces, central typography and the motion policy |
| `kit` | `metrics`, `theme` | The look: `Kit` names a metric catalog, type scale, faces, control variants (`ControlVariants`), colour slots (`ColorSlots`) and FlatLaf key set; `Kit.DEFAULT` is the default look and `Kit.CLASSIC` the classic base |
| `icons` | `theme`, `metrics`, `model` | Named artwork and context-scoped tint caches |
| `swing` | `model`, `theme`, `metrics`, `icons`, `kit` | EDT views, faces, owned controls, registrations, FlatLaf installation and the native chrome's painters |

Each package-info names its allowed outgoing Jasper packages. `verifyUiArchitecture`, a root-project task that
`:jasper-ui:check` depends on, runs `jdeps` over the module's classes and fails on a dependency on another Jasper
module, an edge the package-info does not list, a package cycle, Swing in `theme`, `model` or `kit`, and any class
outside `java.`, `javax.`, `jdk.` and the UI packages in those three. FlatLaf (3.7, with FlatLaf Extras) and JSVG
(2.0.0) are implementation dependencies.

The SDK's `ui` values mirror the model without exposing Swing or UI types: the SDK and module `Nodes` declare the same
31 node kinds and enum constants, and `dev.jasper.app.plugins.KitValues` translates one into the other. The host and
the testkit implement the same SDK facade. Plugins never load the engine: a plugin class loader's parent is the
platform loader, so nothing on the application classpath except the SDK is visible to it, and `verifyUiDistribution`
checks that no plugin runtime or staged plugin jar carries `dev/jasper/ui/`.

## Themes

A theme supplies colours only. `JasperTheme` reads a Jasper theme file with the JDK-only `ThemeJson` reader.
`JasperTheme.builtIns()` holds the seven built-ins in their order (Slate Cobalt, Catppuccin Latte, Catppuccin Frappé,
Catppuccin Macchiato, Catppuccin Mocha, Ubuntu Yaru, Jasper Meadow), `builtIn(id)` finds one, `DARK_DEFAULT_ID` and
`LIGHT_DEFAULT_ID` are both Jasper Meadow's id (`jasper-meadow`, the default of either appearance) and `bundled()` is
Slate Cobalt (`slate-cobalt.json`, kept identical to the machine-readable block of
[`slate-cobalt.md`](design/theme-engine/slate-cobalt.md) by `SlateCobaltFileTest`). `parse(json, id)` reads any other
file; the app reads installed and plugin theme files through it. [Themes](themes.md) describes the file format.

Read a Slate Cobalt role without constructing Swing controls:

<!-- example:palette -->
```java
var theme = JasperTheme.bundled().generated(true);
var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
```

A theme has a Dark and/or Light semantic palette (`SemanticPalette` over the 70 `SemanticColor` roles; roles a file
must not list follow fixed rules), its terminal schemes (`TerminalScheme`, each with an id and a bold foreground) and
an editor scheme per variant (`EditorColors` and the 16 `SyntaxRole` styles, written as colours or as references into
the palettes). `generated(dark)` assembles a variant's `GeneratedTheme`: the palette, its matching terminal scheme, the
editor scheme and syntax, opaque glass (`GlassLevels.OPAQUE`; workspace glass is a setting) and the derived-role
overrides the file lists.

- **Derived roles.** `DerivedPalette.of(theme)` gives all 23 `DerivedColor`s, each the override or its recipe over
  semantic roles and the derived roles before it, cached by theme identity. Nine are overridable
  (`GeneratedTheme.derivedOverrides()`). Every derived colour is opaque.
- **Contrast guards.** A `PaintPair` declares a pair a painter paints, naming either kind of `ColorRole`, with its
  `SurfaceContrast.Guard`: text 4.5:1, indicator 3:1, structural boundary 1.3:1, control boundary 1.5:1, or a CIE L*
  distance (`apart`, `beyond`). `SurfaceContrast.painted` applies the guard where the pair is painted and never changes
  a role.
- **FlatLaf keys.** `UiColorKeys.flatLaf(theme, keys)` derives FlatLaf's colour keys from the roles. `UiKeySet.DEFAULT`
  is the default look's set; `UiKeySet.CLASSIC` adds the classic overlay (table headers, zebra rows, gradient buttons).
  A kit names its set, so the theme engine never sees the kit.
- **Title bar.** `TitleBarTheme.of(theme)` is the window header's theme: when a variant sets `surface.titlebar` or
  `text.titlebar`, the header's surface and inks derive from them.
- **Terminal colours.** `TerminalColors` takes background before foreground and copies exactly 16 non-null ANSI
  colours; the terminal library's `Palette` takes foreground before background, so adapters map them by meaning.
  `GlassLevels` takes an opacity from 0.1 to 1.
- **Binding.** `ThemeBinding` is EDT-only and holds the current `ThemePair` (chrome and workspace themes): a subscription
  replays the current pair, an equal publish is ignored, a re-entrant publish is rejected and a listener failure goes
  to the binding's failure handler. `ThemeRoute` selects a view's theme from the pair: `CHROME`, `WORKSPACE`, or
  `TITLE_BAR` (`TitleBarTheme.of` the chrome theme).

Alpha treatments (focus and error rings, hover, category tiles) apply one kit metric to a base role; a role that
carries alpha (`surface.scrim`, `border.subtle`) is painted as given.

## Kits

A `Kit` is the look: `record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants
controls, ColorSlots colors, UiKeySet uiKeys)`, every part required and compared by value. Colours always come from
the theme; a kit holds none.

| Kit | Metrics | Type | Faces | Controls | Colour slots | FlatLaf keys |
| --- | --- | --- | --- | --- | --- | --- |
| `Kit.DEFAULT` (`"default"`) | `DesignMetrics.DEFAULT` | `TypeScale.DEFAULT` (13 points) | `FaceSet.PLATFORM` | `ControlVariants.DEFAULT` | `ColorSlots.DEFAULT` | `UiKeySet.DEFAULT` |
| `Kit.CLASSIC` (`"classic"`) | `DesignMetrics.CLASSIC` | `TypeScale.CLASSIC` (12 points) | `FaceSet.PLATFORM` | `ControlVariants.CLASSIC` | `ColorSlots.CLASSIC` | `UiKeySet.CLASSIC` |

One kit serves a process and is fixed before the first window. The application uses `Kit.DEFAULT` unless a look plugin
is active; then `dev.jasper.app.presentation.KitParser` reads the plugin's kit file, which names `base = "default"` or
`base = "classic"` and tunes that base's chrome, controls, metric tokens, type and faces and colour slots. The base
also fixes the FlatLaf key set, which a kit file cannot choose. [Looks](looks.md) is the kit-file reference;
[Enterprise](enterprise.md) is the bundled look.

### Control points

`ControlVariants` holds 14 control points, each two-valued: the default look's value first, the classic look's
second. A kit file names a point by its key and a value by the enum constant in lower case.

| Point | Default | Classic | What it changes |
| --- | --- | --- | --- |
| `toggle` | `switch` | `checkbox` | A `Toggle`; also a secret's reveal and copy face |
| `exclusive` | `segments` | `radios` | An EXCLUSIVE `Choice` |
| `tabs` | `segments` | `folder` | A TABS `Choice` |
| `group` | `card` | `titled_box` | `Section` and `Card`: a section label over a card, or a titled box |
| `form_label` | `plain` | `colon` | `FormRow` labels: plain, or a colon-ended label column |
| `empty` | `tile` | `text` | An `Empty` state: with its tile, or text only |
| `table` | `plain` | `classic` | A `Table` (classic: a header band and zebra rows); also the `Properties` face |
| `tree` | `plain` | `classic` | A `Tree` (classic disclosure and category rows) |
| `stepper` | `stepper` | `spinner` | STEPPER and SLIDER number fields |
| `ordered_list` | `inline` | `side_column` | An ORDERED list: per-row commands, or a side column |
| `buttons` | `raised` | `classic` | Buttons and dropdown faces (classic gradients); also the text-field policy and the separator face |
| `tag` | `tint` | `boxed` | A `Chip`: a borderless tint, or a boxed uppercase label |
| `route` | `tiles` | `edged` | A ROUTE strip: icon tiles, or edged cells |
| `prompt` | `tile` | `plain` | A PROMPT form: the icon tile and title, or the plain classic prompt |

`ControlVariants.of(boolean...)` takes 14 flags (true for the classic value) in this order; `flags()`, `complement()`,
`values()` and `keys()` read them back.

`KitFaces` holds one face per point, chosen once per `KitContext` from the kit's variants; the separator face
(separators, document-split hairlines, manager column edges and pane strips) follows `buttons`. Each face declares the
pairs it paints (`Face.pairs()`; `KitFaces.declared(kit)` lists them for the painted-contrast suite) and never reads
the kit: it receives what it paints. Classic faces other than the stepper and tag faces receive the kit's dialog
surface. Outside `dev.jasper.ui.kit` and the app's presentation package, `KitFaces` is the only reader of the
variants.

### Colour slots

`ColorSlots` picks the role two surfaces use, each from a closed pair: `dialog_surface` (dialog bodies) is
`surface.dialog` or `surface.app`, and `navigation_surface` (the Settings navigation tree) is `surface.sidebar` or
`surface.card`. `ColorSlots.DEFAULT` is `surface.dialog` and `surface.sidebar`; `ColorSlots.CLASSIC` is `surface.app`
and `surface.card`.

## Metrics and density

`DesignMetrics` holds one logical integer per `DesignMetrics.Role`: 707 roles covering heights, paddings, gaps,
radii, widths, icon sizes, shadow and tint percentages and motion durations. `DesignMetrics.DEFAULT` is each role's
declared value and is Jasper's compact geometry (frozen by `compact-roles.tsv`). `DesignMetrics.CLASSIC` is the default
catalog with the classic look's delta, measured on the Enterprise references (frozen by `classic-roles.tsv`). Density
belongs to the kit: no setting changes the catalog, and a look tunes it only through its kit file's `[metrics]` tokens.
(`window.view_mode = "compact"` is a window chrome mode, not a geometry catalog.)

| Role | Default | Classic |
| --- | ---: | ---: |
| `FIELD_HEIGHT` / `BUTTON_HEIGHT` | 24 / 24 | 22 / 24 |
| `ROW_HEIGHT` / `MENU_ITEM_HEIGHT` | 24 / 22 | 22 / 22 |
| `CONTROL_RADIUS` / `FIELD_RADIUS` / `CARD_RADIUS` | 4 / 4 / 6 | 3 / 2 / 3 |
| `FOCUS_RING` | 3 | 2 |
| `NATIVE_TOOL_HEADER_HEIGHT` / `PANEL_HEADER` | 32 / 35 | 24 / 24 |
| `MANAGER_SIDEBAR_WIDTH` / `MANAGER_LIST_WIDTH` | 192 / 272 | 171 / 348 |
| `WINDOW_RADIUS` / `WORKSPACE_GUTTER` / `PALETTE_CORNER_ARC` | 8 / 6 / 12 | 0 / 0 / 0 |
| `PROMPT_FIELD_HEIGHT` / `PROMPT_TILE` | 28 / 54 | 22 / 30 |

`value(role)` reads a role; `with(role, value)` returns a copy with one role changed (never negative);
`contentHeight(role, font, frc)` is the larger of the role and the font's measured line plus the role's padding
(`contentPadding(role)`), so enlarged fonts grow content without scaling the catalog. Display scaling belongs to
FlatLaf (`UIScale`), applied once.

### Metric tokens

`MetricTokens` are the 11 public tokens a kit file's `[metrics]` sets. Each has a key, an inclusive range and the roles
it moves; the first role is the one `MetricTokens.of(catalog)` reads as the catalog's own value.

| Token | Range | Default | Classic | Roles it moves |
| --- | --- | ---: | ---: | --- |
| `control_height` | 20–28 | 24 | 22 | `FIELD_HEIGHT`, `BUTTON_HEIGHT`, `PICKER_DEFAULT_HEIGHT`, `FULL_FOOTER_BUTTON_HEIGHT`, `JOINED_ACTION_HEIGHT`, `PANEL_EMPTY_ACTION_HEIGHT`, `PANEL_SEARCH_HEIGHT`, `COLUMN_SEARCH_HEIGHT`, `KEYMAP_SEARCH_HEIGHT`, `FULL_SETTING_CHOICE_HEIGHT` |
| `row_height` | 18–28 | 24 | 22 | `ROW_HEIGHT`, `MENU_ITEM_HEIGHT`, `PICKER_ROW_HEIGHT`, `RESULTS_ROW_HEIGHT`, `RESULTS_HEADING_HEIGHT`, `KEYCAP_TREE_ROW_HEIGHT`, `KEYCAP_TREE_GROUP_HEIGHT`, `ORDERED_ROW_HEIGHT`, `SETTINGS_NAV_ROW`, `SETTINGS_NAV_HEADING`, `COLUMN_RAIL_ROW`, `PANEL_COMPACT_ROW_HEIGHT` |
| `control_radius` | 0–4 | 4 | 3 | `CONTROL_RADIUS`, `SEGMENT_TRACK_RADIUS`, `SEGMENT_ITEM_RADIUS`, `PICKER_SEARCH_RADIUS`, `KEYCAP_RADIUS`, `CHIP_RADIUS`, `ORDERED_RADIUS` |
| `field_radius` | 0–4 | 4 | 2 | `FIELD_RADIUS`, `TRANSPARENT_FIELD_RADIUS` |
| `group_radius` | 0–6 | 6 | 3 | `CARD_RADIUS` |
| `focus_ring` | 1–3 | 3 | 2 | `FOCUS_RING` |
| `tool_window_header` | 22–32 | 32 | 24 | `NATIVE_TOOL_HEADER_HEIGHT`, `PANEL_HEADER` |
| `group_padding` | 8–16 | 11 | 12 | `CARD_PADDING`, `FULL_SETTING_SIDE_PADDING`, `SETTINGS_ROW_SIDE` |
| `dependent_indent` | 12–28 | 13 | 20 | `SETTING_DEPENDENT_INDENT` |
| `navigation_width` | 180–300 | 236 | 230 | `SETTINGS_SIDEBAR_WIDTH` |
| `status_dot` | 5–9 | 6 | 7 | `STATUS_DOT`, `PANEL_ROW_DOT`, `TOOL_HEADER_ACTIVE_DOT` |

`DesignMetrics.with(tokens)` moves every role a token names by the token's change from the base catalog's own value,
never below zero, and sets `FIELD_VERTICAL_PADDING` and `BUTTON_VERTICAL_PADDING` to
`2 × max(2, (control_height − 16) / 2)` (16 is `MetricTokens.LINE_HEIGHT`). A value outside its range fails with
`[metrics].<key>: <value> is outside <min>-<max>`.

### Geometry rules

- A control paints its full nominal bounds (`FIELD_HEIGHT`, `BUTTON_HEIGHT`, …). The view owns the external focus or
  error halo, `FOCUS_RING` wide (`focus_ring`), `border.focus` at `FOCUS_HALO_PERCENT_DARK` (32) or
  `FOCUS_HALO_PERCENT_LIGHT` (25) and `border.error` at `ERROR_HALO_PERCENT` (22); real viewports clip it and it never
  changes row gaps.
- Named radii are radii; Java rounded rectangles receive twice the value as their arc diameter.
- Category tiles and chips paint their `hue.<name>.surface` at `TILE_PERCENT_DARK` (17) or `TILE_PERCENT_LIGHT` (13);
  selected tiles use `text.onAccent` on the accent.
- Shadows are black at the kit's shadow metrics. `ThemePainting.softShadow` blurs one mask per treatment and
  nine-slices it across face sizes (a face too small to slice gets its own cached mask); a hollow mask has the face
  cut out, so nothing of the shadow lies under a translucent face. It serves the modal overlay's card (hollow, so a
  fading card does not darken) and the command palette; FlatLaf paints menus' shadows off macOS
  (`Popup.dropShadowOpacity` from `POPOVER_SHADOW_PERCENT_DARK`/`_LIGHT`); the Light selected segment has a soft
  shadow; and a primary button's 1 px drop is painted by its `KitView` outside the face, so the face keeps its extent
  while a white inset line tints its top row. The view repaints over the drop when the button is disabled, hidden,
  moved, removed or stops being primary, as it does for the focus halo.
- The kit paints placeholders once rather than delegating a duplicate FlatLaf hint.

## Type and faces

`TypeScale` gives every `Typography.Role` (40 roles) a size and a weight (400, 500 or 600), the body size those sizes
are written for (`base()`) and the body size the kit draws at when the user sets none (`body()`); a role draws at its
size × body ÷ base. `TypeScale.DEFAULT` is the 13-point table at a 13-point body, with the platform face's extra
letter spacing on section labels (frozen by `default-type.tsv`); `TypeScale.CLASSIC` is the 12-point table at a
12-point body without it (`classic-type.tsv`). `withBody(points)` (6 to 96) sets the body; a kit file's `[type] base`
(10 to 16) calls it.

`FaceSet` names the faces by registered full name: a UI face per weight and a data face regular and medium.
`FaceSet.PLATFORM` is the platform UI family and `Font.MONOSPACED`. The data roles (`MONO`, `MONO_SMALL`,
`MONO_CAPTION`, `PANEL_DETAIL_MONO`, `RUN_DETAIL`) draw in the data face; a face that is not registered draws in the
platform face.

`Typography.of(scale, faces, user)` resolves role fonts:

- The platform UI family is resolved once per process, the first installed of: macOS Helvetica Neue, Helvetica,
  Noto Sans, DejaVu Sans; Windows Segoe UI, Tahoma, Noto Sans, DejaVu Sans; elsewhere Noto Sans, Liberation Sans,
  DejaVu Sans; else `SansSerif`. When that family is Helvetica Neue, roles other than `BODY` under weight 600 use
  `.AppleSystemUIFont` where it exists, and weight 600 uses Helvetica Neue's installed medium face with a small
  tracking adjustment.
- A user font family replaces the kit's UI faces and a user size replaces the body size. The host applies them as
  Swing developer defaults: `defaultFont` (family and size), `Label.font` (size) and `Jasper.uiFontFamilyOverride`
  (false keeps the faces and takes only the size). The app sets them from `ui.font.family` and `ui.font.size`.
- `font(role, text)` switches the whole text to the platform face when a kit's own face lacks a glyph for it (IBM
  Plex has no macOS modifier symbols and no CJK), and to the logical `SansSerif` (`Monospaced` for a data role) when
  the platform family lacks it too (Liberation Sans on Linux, for example), so Java's installed fallback fonts fill it
  in glyph by glyph.
- Under a kit whose regular UI face is registered, `FlatLafInstaller` gives FlatLaf's component fonts that face at the
  kit's body size; menu accelerators keep the platform face.

## Motion

`MotionPolicy` polls the context's animations supplier on each frame; turning motion off settles every registered
effect once, and `close()` settles them. A `KitView` owns its policy, and switches and progress indicators register
their effects with it; a `ModalOverlay` owns its own policy from its context and settles it on close. In the app the
supplier follows `ui.animations.enabled`, and a look whose `[chrome] motion` is `none` turns motion off.

## Installing appearance

`FlatLafInstaller.install(theme, kit)` (EDT) builds an IntelliJ-theme JSON from `UiColorKeys.flatLaf(theme,
kit.uiKeys())` and installs a FlatLaf `IntelliJTheme.ThemeLaf` with geometry from the kit's metrics:

| FlatLaf key | From |
| --- | --- |
| `Component.arc`, `Button.arc` | `CONTROL_RADIUS` × 2 |
| `TextComponent.arc` | `FIELD_RADIUS` × 2 |
| `Component.focusWidth` | `FOCUS_RING` |
| `Button.minimumHeight` / `Component.minimumHeight` | `BUTTON_HEIGHT` / `FIELD_HEIGHT` |
| `Button.margin` | `BUTTON_LABEL_MARGIN`, `BUTTON_HORIZONTAL_PADDING` |
| `Button.minimumWidth` | `WINDOW_ACTION_MIN_WIDTH` |
| `List`, `Tree`, `Table`, `ComboBox`, `MenuItem` `.selectionArc` / `.selectionInsets` | `0` / `0,0,0,0` (selection is a plain edge-to-edge rectangle) |
| `MenuItem`, `Menu`, `CheckBoxMenuItem`, `RadioButtonMenuItem` `.margin` | `MENU_ITEM_VERTICAL_PADDING`, `MENU_ITEM_PADDING` |
| `PopupMenu.borderInsets` | `MENU_PADDING`, `HAIRLINE` |
| `Popup.borderCornerRadius`, `PopupMenu.borderCornerRadius` | `CARD_RADIUS` |

It also publishes `Jasper.installedTheme`, `Jasper.installedKit`, `Jasper.semantic.<role>` for every semantic role,
`Popup.dropShadowColor` (black) and `Popup.dropShadowOpacity` in the look and feel's defaults, so a rollback restores
them with it. `installedTheme()` reads the theme (and throws when none is installed); `installedKit()`, safe on any
thread, reads the kit, or gives `Kit.DEFAULT` when no Jasper look and feel is installed. The app's
`ThemeManager.install` adds `SplitPane.dividerSize` (`NATIVE_SPLIT_DIVIDER`), the workspace's split divider border and
`TitlePane.centerTitle`.

`AppearanceTransaction(binding, install, restore).apply(pair)` (EDT, not re-entrant) installs the pair's chrome theme
through the caller's `install` before publishing the pair; on failure it calls `restore` with the previous chrome theme
and restores the previous look and feel, its defaults and the developer overrides. Install appearance on the EDT before
creating widgets.

`ChromePainter` and `NativeStyles` (one per kit; `installed()` follows `FlatLafInstaller.installedKit()`) serve native
Swing in the app. `ChromePainter` paints the workspace chrome over named roles: the tab strip, toolbar rules and action
widths, the title-bar search field and rules, the status dot, rail badges, the connected dot, split dividers, drop
guides, the palette shadow and the classic dock's rules, stripe labels and tiles, count pills and header strips, whose
colours it paints through the pairs `ChromePainter.DOCK_PAIRS` declares. `NativeStyles` styles native buttons, labels,
text areas, menus, borderless fields and split panes with the kit's fonts and metrics. `TITLE_CONTEXT`,
`TOOLBAR_LABEL`, `STATUS_TEXT` and `STATUS_PATH` are the chrome's text roles.

## Views

`KitContext.of(kit, binding, route, icons, animationsEnabled, shortcuts)` carries the kit (fixed for the context's
life), the caller's `ThemeBinding` and `IconRegistry`, a theme route, the animations supplier and a map from action id
to the binding text menus show (`shortcut(actionId)`, `""` when unbound). `theme()` is the route's theme from the
binding's current pair; `typography()` is read live, so it survives theme installs. The app's `AppearanceBindings`
builds every app-native context over the presentation's kit.

The caller owns binding and registry lifetimes; a view owns its listeners, descriptor controls, registered specialized
content and motion. Close the view first. This compiled example runs inside an EDT block; `generated` is the previous
example's output and `events` is a caller-owned list of `UiEvent`:

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

A `KitView` mounts a validated `ViewSpec`, subscribes to the binding and to font default changes (`defaultFont`,
`Label.font`, `Jasper.uiFontFamilyOverride`) and reports user intent through its event consumer. Every operation
except `close()` requires the EDT; `close()` is idempotent and may be called from any thread. Each view takes its own
copy of the context, so `route(ThemeRoute)` re-routes only that view.

- **Updates.** `update(spec)` reconciles by stable id and kind: unchanged values keep pending input, caret, selection,
  focus and scroll, and controls and documents keep their identity. An update called from inside an update is rejected
  before any change, at the module, SDK host and fake boundaries; disposal still contains callback failures and allows
  re-entrant close, and ordinary event handlers may update synchronously.
- **Focus and selection.** `focus(id)` focuses a control (mounting an off-screen row control first); `reveal(id)`
  scrolls a node into view without moving focus; `selectAll(fieldId)` selects a field's text without an `Edited` event
  or a focus change (an unknown or non-field id is an `IllegalArgumentException`); `composing(fieldId)` reports
  uncommitted input-method text.
- **Specialized content.** `registerContent(contentId, component, lifetime)` fills a `Custom` node by its content id.
  An EDITOR accepts a `JTextArea`, `JTextPane` or `JEditorPane` (optionally in a scroll pane) or a non-standard
  `JComponent` canvas; other kinds accept a canvas only. Panels, ordinary controls and kit-owned components are
  rejected, so this is not a route for feature forms. Each refresh gives the component the kit's body font. A
  registration is disposed on replacement and on close.
- **Containers.** Containers own semantic layout. Forms consume a host-supplied logical available height
  (`setAvailableHeight`); the host owns window chrome, sheet and scrim lifetime and cancellation cleanup.
- **Pointer-only views.** `new KitView(context, spec, events, true)` mounts controls that never take keyboard focus but
  stay operable by pointer, for host overlays such as notification cards over a terminal.

`KitOwnership.owns(component)` says whether the kit created a component; `KitOwnership.CONTEXT_MENU`
(`"Jasper.kit.contextMenu"`) is the client property that holds a row collection's, breadcrumbs' or setting row's
context menu.

### Secrets

`withSecret(id, use)` exposes a temporary array and erases it in `finally`, also when the callback fails; an
asynchronous consumer transfers a clone to an owner that erases it. `replaceSecret(id, char[])` copies a caller-owned
value without an edit event; `clearSecret(id)` erases live storage. Secrets never enter nodes, events, metadata or
diagnostics; reveal state and wipeable documents belong to the view, and replacement, removal, a kind change and close
wipe owned values.

| `SecretKind` | Behaviour |
| --- | --- |
| `INPUT` | A masked single-line entry with a reveal control (the five-argument constructor's kind) |
| `DISCREET_INPUT` | An input that reserves the reveal slot and shows the control while it is not empty or has focus |
| `CONFIRMATION` | An input without its own reveal control, following its reveal group |
| `DETAIL` | A read-only stored value with reveal and an optional value-free copy action, painted from the character array, never a text component |
| `MULTILINE` | An editable wrapping secure note over the same wipeable storage, shown in clear, with no reveal group, copy action or reveal control |

An input secret's `submitAction` (INPUT, DISCREET_INPUT or CONFIRMATION) is what Enter runs; the field keeps the key,
so a dialog can scope Enter to its password field. Copy emits only `Action(copyAction)`; the domain callback reads the
value through `withSecret` and its own credential clipboard service. `clearSecret` and replacement remask a detail.

### Host-only view presentation

These `KitView` methods serve the app's native hosts; none is part of the plugin SDK.

| Method | Effect |
| --- | --- |
| `route(ThemeRoute)` | Moves the view to another theme route in place (workspace content tabs take `WORKSPACE`, toolbar views `TITLE_BAR`) |
| `paintsSurface(true)` | Paints the route's `surface.panel` opaque behind the view (workspace content tabs) |
| `panelSurface(false)` | Leaves a body panel's rounded surface to a native host that already paints it |
| `managerColumn(RAIL \| LIST)` | Presents the whole view as a manager column: RAIL rows (a tree's branches become flat `SECTION_LABEL` headings) or LIST rows (rounded selection, a table's header on the row inset) |
| `titleStrip(true)`, `toolbarStrip(true)` | A window title action strip or a main-toolbar view: icon-only menu buttons show their indicator on the borderless control surface; a toolbar strip keeps the kit's control height, draws an icon-only button's glyph at `ICON_RAIL`, and under the classic buttons face draws a DROPDOWN with a `PickerFace` as IntelliJ's run-configuration combo (`TOOLBAR_COMBO_*`: 24 px, the face's icon, the name cut with an ellipsis at 240 px, an arrow box) |
| `hostTitled(true)` | The host shows the form title, so the form draws neither its title label nor a hero that only repeats it |
| `dialogBand(true)` | The form footer is a band on `surface.footer` under a `border.window` rule, footnote leading, actions trailing |
| `dialogCard(true)` | A classic dialog's card paints the form's fill and frame |
| `runWindow(true)` | The view is part of the Run window (see [Run window and chrome](#run-window-and-chrome)) |
| `classicChromeIcons(true)` | A toolbar strip's or a panel header's buttons and menu buttons named one of the six classic chrome names draw IntelliJ's classic art ([icons](#icons)) |
| `setHintPresentation(stackId, true)` | The compact hint face for one stack (the palette footer) |
| `setFieldTrailing(fieldId, button)` | An owned action inside a TEXT or SEARCH field |
| `setTreeRowPresentation`, `setButtonValuePresentation`, `setButtonKeycaps`, `attachTreeDetail` | Keymap rows: locked rows and keycaps, the shortcut recorder face, keycap values and a detail below a tree leaf (`TreeDetailHandle`) |
| `setArtworkPresentation(iconId, …)` | Identity row or header treatment for an icon |
| `anchorTarget(id)` | The component a host attaches a popup to: a visible, enabled, idle `Button` or PICKER `Choice` |
| `panelHeader`, `setPanelHostActions`, `setPanelHostHeader` | Borrowed panel headers (see [Panels](#panels)) |

### Shells, dialogs and surfaces

- **`ModalOverlay(context, content)`** is the modal overlay that `DialogSpec.Presentation.OVERLAY` dialogs and the
  SDK's progress overlays (`OverlaySpec`) use: a scrim over the window's whole content (`surface.scrim`, painted as
  its role gives it) and the content in a centred, outlined, rounded card with the dialog shadow (black,
  `DIALOG_SHADOW_*`). Opening fades the scrim in while the card fades from transparent and rises `OVERLAY_RISE` into
  place over `OVERLAY_ENTER_MILLIS`, easing out (cubic); closing is its opposite over `OVERLAY_EXIT_MILLIS`: the card
  lifts off at once and rises another `OVERLAY_RISE` (easing out, from wherever it is if the entrance had not
  finished) while it fades (easing in) and the scrim lifts. Each transition's clock starts at its first drawn frame,
  and a leaving card keeps its size although the closed dialog's content leaves it at once. With motion off it is
  instant, and it settles at once if motion is turned off mid-way. While it moves the card paints one snapshot of its
  content, so it fades as a single surface; at rest the content paints live. The host adds the overlay to a layered
  pane and owns keyboard routing and focus; the app's `WindowOverlay` stacks them per window.
- **`ClassicDialog(context, content, title, swatch, placement)`** is the classic look's in-window dialog: a still scrim,
  a card framed 1 px in `border.hover` with `DIALOG_RADIUS` and the popover shadow, a header strip on
  `surface.header.active` holding the dialog's swatch and title, and the host's content on the kit's dialog surface.
  `CENTERED` centres it on the whole window; `SHEET` joins it under the title bar. It neither fades nor moves; its host
  views take `hostTitled`, `dialogBand` and `dialogCard`.
- **`SettingsShell(context[, frame], search, navigation, header, banner, body, footer)`** lays out Settings.
  `Frame(integratedTitle, collapsibleNavigation)`: `Frame.INTEGRATED` makes the header row the window's title row with
  open navigation; otherwise the shell draws a search strip, ruled frames and a footer band. `settingsPage(view)` gives
  a page's view the Settings geometry, `pageScroller(context)` is the OVERLAY page scroller and `framePairs(context)`
  lists the classic frame's guarded pairs.
- **`SurfacePanel`** paints host surfaces by `Kind` (`POPUP`, `CONTENT`, `NOTICE`, `RUN_WINDOW`, `FIELD`, `BOX`) and
  provides native layout helpers (`NativeSplit`, `sidecar`, `scrolling`, `toolbar`, `actionGroups`, `labelledRow`,
  `footerBand` for a WINDOW footer in a native shell, `row`).

## Descriptor vocabulary

`Nodes` is the sealed vocabulary of 31 node kinds: `Text`, `Icon`, `Button`, `MenuButton`, `StatusDot`, `Field`,
`Secret`, `Choice`, `PickerMenu`, `Toggle`, `Chip`, `Progress`, `Strength`, `Stack`, `FormRow`, `SettingRow`, `Card`,
`Section`, `Empty`, `ItemList`, `Header`, `Panel`, `Form`, `Split`, `Table`, `Tree`, `Breadcrumbs`, `Custom`,
`Separator`, `Notice`, `Properties`. Every node carries a `ControlState(visible, enabled, busy, error)`; `error` is
validation prose and `ControlState.READY` is visible, enabled, idle and without an error. No node carries pixel
dimensions, delegates or secret values, and no domain meaning is inferred from ids or text. `UiEvent` has 10 kinds:
`Action`, `Edited`, `Selected`, `Toggled`, `Expanded`, `Removed`, `Reordered`, `SecretChanged`, `Clicked`, `Sorted`.

`ViewSpec` validates the whole tree: unique node ids and `Custom` content ids; depth at most 128 and at most 100,000
nodes; a `Notice` only in a form's sections or a VERTICAL stack; a `Separator` only in an INLINE or ACTIONS stack and
never first, last or beside another; an EXCLUSIVE choice with 2 to 4 options and a TABS choice with 2 or more;
selection and expansion references that exist; tree depth at most 128 with no more trailing values than columns after
the title; and a field's `listId` naming an `ItemList` in the same view.

### Forms, fields and choices

- `FormKind` is PROMPT, PROMPT_COMPACT, SHEET, FULL, SETTINGS or WINDOW. A form has at most one PRIMARY action; Enter
  submits it (`FormSubmission.PRIMARY`; `NONE` leaves Enter to the fields and buttons) and Escape runs the cancel
  action. A WINDOW form has exactly one content section and no hero and fills its owner above the shared scrollable
  footer. A footnote is one line and excludes a preview. `FormRow` captions carry the full caption as a tooltip; under
  the default `form_label` point they stay on one line, shortened at their end, and under the classic point they wrap.
- `FieldKind` is TEXT, SEARCH, PORT, PATH, TEXTAREA or NUMBER. NUMBER takes a `Numeric` shape (finite bounds, a positive
  step, 0 to 6 decimals), admits only numbers within it and steps with the arrow keys; STEPPER and SLIDER present it
  (the `stepper` point draws STEPPER and SLIDER as a spinner in the classic look) and are never read-only. PLAIN_PATH
  is a PATH with Browse and no leading icon. TECHNICAL draws the value in the data face. EMBEDDED takes its visible
  label from the host composition and TRANSPARENT drops the input fill on a surface that backs the field; a
  TRANSPARENT search field shows the rail-size glyph with `TRANSPARENT_FIELD_ICON_GAP`. A SEARCH field's label is its
  accessible name only (the glyph and hint label it visually). Read-only fields report no `Edited`. Single-line fields
  never clip an enlarged font.
- Field actions: `trailingAction` (Enter, not TEXTAREA or PATH), `alternateAction` (Shift+Enter, not TEXTAREA),
  `cancelAction` (Escape) and `listId`, which drives an `ItemList` with the arrow and page keys while the field keeps
  focus. A list named by a field's `listId` reserves its `visibleRows` selectable-row height even when filtered or
  empty (RESULTS reserves its fixed row height), so picker dialogs keep their height.
- `ChoiceKind` is DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS or TABS. SEARCHABLE is the one type-to-filter
  dropdown and requires a `ChoiceSearch` (pinned options, a no-matches line and optional free entry, whose options are
  suggestions). `Choice.icon` is a named cue for PICKER only (the seven-argument constructor gives a PICKER the `key`
  cue and other kinds none). A PICKER or DROPDOWN may carry a `PickerFace` (title, detail, glyph, hue, density) painted
  in place of its plain label. A dropdown in an ACTIONS stack is labelled by its accessible name only, and a
  natural-width dropdown reserves its chevron lane for its longest plain option.
- `SettingRow` and `FormRow` carry a search `highlighted` flag; tree items carry an optional trailing count and an
  emphasized title.
- `Toggle.pending` shows an operable midway switch on the warning colour while the domain confirms a change; switches
  take a literal tooltip.
- `Button.selected` makes a ghost or secondary button a toggle (pressed fill, CHECKED for assistive technology,
  confirmed by the owner); `Button.iconSeverity` and `Icon` severities (INFO, WARNING, DANGER) tint a glyph with the
  accent or a status colour. `ButtonPresentation` EMBEDDED buttons are content-sized inline links and glyph buttons;
  CONTEXTUAL_EMBEDDED buttons reserve their full geometry but paint only for a selected, hovered or keyboard-active row
  or direct focus; HELP is a round help button. `MenuItem.hue` selects a menu item's category artwork hue.
- `Text.severity` tints text and `Text.caret` draws a command line's insertion bar. Body, description and caption text
  flows (wraps, keeping line breaks, no wider than `TEXT_FLOW_COLUMNS`) in vertical stacks, form bodies, cards and
  sections. ACTIONS stacks wrap instead of squeezing their buttons. A stack's `cancelAction` answers Escape from
  anything inside it.
- `Notice` is a severity line with actions; `Properties` a titled list of label and value rows.

### Row collections

`ItemList` (`ListKind` SINGLE, DOUBLE, COMPACT, ORDERED, RESULTS), `Table` and `Tree` share one row machinery:

- Type-ahead, Shift ranges under `SelectionMode.MULTIPLE`, and a host-set selection scrolled into view. A plain left
  single click reports `Clicked` after `Selected` (not in RESULTS lists or on headings, never after a popup-trigger
  press); a double click reports the activate action; Enter reports a list's `enterAction`, or the activate action
  when it has none, and passes on to the host when neither is set. `deleteAction` answers Delete and Backspace.
- Rows of all three carry a literal tooltip and their own context menu, and table rows can be muted. A context press
  never changes the selection and its menu items report only their action (a shared menu opens only on a selected
  row). Space on a row whose trailing slot holds exactly one switch operates that switch. A row's own buttons and
  switches report only their intent.
- `SelectionMode.NONE` makes rows passive: no selected ids, the canvas is not a focus stop, row selection and
  activation are off, embedded controls keep their own behaviour and text paints at full opacity; a field's `listId`
  keys fall back to the look and feel.
- Tables share the width by column weight, draw the sort indicator of the `Column.sorted` the view's owner sets and
  report header clicks as `Sorted`; the owner orders the rows. Cells are plain text or cell nodes (Text, StatusDot,
  Icon, Chip, an INLINE stack, or a bare DROPDOWN); heading rows carry a title and an optional count; tables can take
  the OVERLAY scroll policy. A table with no rows shows its `emptyText` as a muted line or its `Empty` state (at most
  one action) below the column headers. Tree items carry a tooltip, a status dot and aligned trailing columns with
  minimum widths.
- Item-list row slots accept Text, Icon, Chip, StatusDot, Progress, Strength, Button, MenuButton, Toggle, Choice and
  INLINE stacks of those. `ListItem.heading` is a title-only section row (never selected, activated, hovered or given
  a menu; a hairline opens every heading after the first). `emptyText` is the muted line an empty list shows, above
  its `addAction`.
- RESULTS is the one-line result list that follows an external field: it never takes focus, a left press runs a row,
  rows and headings keep `RESULTS_ROW_HEIGHT` and `RESULTS_HEADING_HEIGHT` (headings in `Typography.Role.RESULTS_HEADING`),
  the trailing tag drops first, and every host update keeps the selection and the heading above it in view while a
  theme change keeps the scroll. `ItemList.visibleRows` sizes the viewport in selectable rows.
- `Breadcrumbs` is the path control, with tail overflow and a typed-path mode (Enter submits, Escape cancels).

### Splits and documents

`SplitKind` is SIDEBAR_DETAIL, THREE_COLUMN or DOCUMENT.

- SIDEBAR_DETAIL is the three-column manager without its rail: its last child is the detail column and the others are
  list columns (one list column takes `MANAGER_LIST_WIDTH`, at most half the width; with more, every column takes an
  equal share; the columns meet at their hairline). THREE_COLUMN managers use `MANAGER_SIDEBAR_WIDTH` and
  `MANAGER_LIST_WIDTH`, then the detail; narrow hosts fit them proportionally.
  A list column without a search row starts its rows at `COLUMN_RAIL_TOP`; one-line rows take `COLUMN_RAIL_ROW` and
  two-line rows `COLUMN_ITEM_ROW`. A manager detail heading keeps `DETAIL_IDENTITY_MIN_WIDTH` before its actions wrap,
  and a caption over its value keeps `DETAIL_VALUE_GAP`.
- A DOCUMENT split stacks its children and lets its first visible `Custom`, `Empty` or nested `Split` fill the height
  the bars leave, so the document keeps its own scrolling; its minimum height counts the fill at its own minimum. The
  other children are compact bars: an INLINE stack among them, directly or as a row of a VERTICAL group,
  takes the `DOCUMENT_BAR_*` padding and gaps, its first field or text fills what the controls after it leave (rows of
  one group align their fills), other text holds a stable status width, fields take the panel search field's look and
  buttons the panel action size. A group stacks its rows without gaps and shows a passive list's rows as text lines;
  the split draws a subtle hairline at each boundary, inside the bar side. A bar's empty caption takes no focus. An
  INLINE bar that is an empty caption followed only by buttons (the Editor's Markdown view controls) scrolls sideways
  when the split is narrower than its buttons, and a button that takes the focus scrolls into view. The Editor, the
  Vault's classic manager, Remote's tunnels and File Servers use DOCUMENT splits.

### Panels

A `Panel` owns its header, context or navigation row, virtual body and persistent footer; a hidden header takes no
space and a trailing footer action anchors to the right edge. Virtual rows keep a bounded set of live controls;
mutable fields, secrets and nested custom content stay in stable composition outside rows.

- An `Empty` that is a panel's body (or the last child of a DOCUMENT split body under a note) takes the plugin-panel
  geometry, centred in the body.
- A two-line `ItemList` directly in a panel is a compact inset row with inset rounded selection; its title line keeps
  its first text (the name) whole while it fits in half the line, and a `Chip` on an inline line keeps its label
  whole.
- A panel footer `Stack` (VERTICAL) of status lines above a `SettingRow` paints a full-width hairline rule above the
  row and keeps the row flush with the bottom; a DETAIL stack in a footer uses compact identity and action metrics,
  while a manager detail keeps its own inset and category scale.
- `LayoutRole.ROUTE` is a framed strip of equal columns, one per child stack, with the kit's arrow before every later
  column.
- The notice surface (notification cards) is a grouped card: `CARD_RADIUS` corners and a crisp hairline.

A native host that borrows a panel's header (`panelHeader`) adds its placement and collapse buttons with
`setPanelHostActions` and its metadata with `setPanelHostHeader(PanelHostHeader)`: a detail after the title and count,
an optional connected dot (the success status held to the indicator floor on the panel surface) and icon-only actions.
The row order is title, count, detail; the panel's own header actions; the host's contributed actions; the host's
placement and collapse actions. Metadata controls are kept by position across refreshes and descriptor updates,
`PanelHostHeader.EMPTY` clears them, a replacement header starts empty and closing the view releases them. A
`PanelHostHeader.Action` takes either an arbitrary host `Icon`, shown as it is, or a monochrome
`PanelHostHeader.Artwork(name, pathData)` that the view tints and validates (custom paths keep their authored stroke).
`PanelHostHeader.Frame` `FLUSH` or `FLUSH_FOCUSED` makes the header a flush frame's header strip: the strip's surface
over a 1 px rule, a parenthesised count, a muted detail and actions 2 px apart. Plugins reach this through
[`PanelHost.setHeader`](sdk-reference.html#dev.jasper.sdk.ui.PanelHost).

### Run window and chrome

`runWindow(true)` presents a view as part of the Run window: its RESULTS rows, heading bands and counts, PROMPT_HEADER
tile, transparent field, buttons' label padding (`RUN_WINDOW_BUTTON_PADDING`) and text take the `RUN_WINDOW_*` roles,
`RUN_HEADING` and `RUN_DETAIL`; `SurfacePanel`'s `RUN_WINDOW`, `FIELD` and `BOX` kinds paint the window's frame and
bands, its field box and its list box, and `RunWindowPresentation` holds its pairs. The palette footer's hint face
(`setHintPresentation`) uses `PALETTE_HINT` for its caption text and links and `PALETTE_HINT_ACTION_GAP` between its
children; same-id updates keep it.

## Keyboard behaviour of kit controls

| Control | Keys |
| --- | --- |
| Row collections (`ItemList`, `Table`, `Tree`) | Up/Down move; Shift+Up/Down extend (MULTIPLE); typing selects by prefix within `TYPE_AHEAD_MILLIS` (not with Ctrl, Cmd or Alt); Enter activates the focused row or runs `enterAction`, else passes on; Delete and Backspace run `deleteAction`, else pass on; Space selects, or operates a row's single switch; the menu key and Shift+F10 open the focused row's menu |
| `Tree` | Right expands, Left collapses |
| `ItemList` | Escape runs `cancelAction`; D runs `detailsAction`; the platform Copy shortcut (Cmd+C on macOS, Ctrl+C on Linux and Windows) runs `copyAction` (each only while the rows have focus) |
| `Field` | Enter runs `trailingAction` (not TEXTAREA or PATH); Shift+Enter `alternateAction`; Escape `cancelAction`; NUMBER: Up/Down (and the keypad arrows) step; with `listId`: Up/Down/Page Up/Page Down move the list |
| `Secret` (input kinds) | Enter runs `submitAction` |
| `Stack` | Escape runs `cancelAction` from anything inside |
| `Form` | Enter presses the primary action (`FormSubmission.PRIMARY`; `NONE` leaves Enter to the fields and buttons); Escape runs the cancel action |
| EXCLUSIVE and TABS `Choice` | Left/Right/Up/Down move; Home/End go to the first and last |
| PREVIEWS `Choice` | Left/Right, Home/End |
| PICKER `Choice` | Enter or Down opens it |
| SEARCHABLE `Choice` popup | Up/Down, Page Up/Page Down, Home/End, Enter, Escape, Backspace, Space |
| `PickerMenu` | Up/Down, Enter, Escape |
| `Breadcrumbs` typed path | Enter submits, Escape cancels |
| `SettingRow` with a menu | The menu key and Shift+F10 open it |

A kit binding whose action is blank never hides the look and feel's own binding for the same key
(`KitKeyBindingAuditTest`).

## Icons

`IconRegistry` (closeable) holds the built-in path glyphs, the built-in SVG artwork and scoped registrations. The host
owns a registry; `register(...)` returns an `IconRegistration` whose idempotent close
removes only its own registration. `IconRegistry.prepareBuiltIns()` decodes the built-ins ahead of time.

The built-in path glyphs are original Jasper vectors on a 16-unit grid (89 names; `folder.filled`, `play.filled`,
`run` and `triangle.down` are filled, the rest outlines):

`add`, `appearance`, `application`, `arrow.right`, `back`, `bell`, `bookmark`, `brightness`, `check`, `chevron.down`,
`chevron.left`, `chevron.right`, `chevron.up`, `clock`, `close`, `collapse`, `collapse.all`, `command-palette`, `copy`,
`cursor`, `delete`, `desktop`, `download`, `edit`, `error`, `expand.all`, `file`, `folder`, `folder.add`,
`folder.filled`, `font`, `forward`, `help`, `hidden`, `history`, `home`, `import`, `info`, `key`, `keyboard`, `laptop`,
`link`, `lock`, `lock.keyhole`, `login`, `logout`, `match.case`, `match.word`, `menu`, `more`, `none`, `note`,
`notification.card`, `notification.history`, `notification.off`, `notification.sticky`, `paste`, `pause`, `play`,
`play.filled`, `plugin`, `preview`, `question`, `refresh`, `regex`, `remove`, `reset`, `reveal`, `run`, `save`,
`search`, `server`, `settings`, `shield`, `sparkle`, `split`, `star`, `star.filled`, `target`, `terminal`,
`triangle.down`, `tunnel`, `unlock`, `upload`, `user`, `warning`, `window`, `window.add`, `zoom`.

Four sets of byte-identical IntelliJ Platform icons (Apache-2.0; `icons/LICENSE.txt`, `NOTICE.txt`, `ATTRIBUTION.txt`
and `SOURCE.md` beside them, listed in the app's third-party notices as "IntelliJ Platform icons (New UI and Classic)")
ship as light SVGs with `_dark` siblings (two classic files serve both variants), each file checked against its
SHA-256 in a manifest:

- **The catalog** (`assets-selected.tsv`, 14 files): `settings`, `network`, `tunnel`, `more.vertical`,
  `chevron.right`, `search`, `file`. Ordinary contexts given a theme draw these meanings with this artwork, picking
  the sibling for the theme's darkness; the surface context draws only `network` and `more.vertical` from it (the
  sibling for the surface's darkness) and the path glyphs otherwise. `network` and `more.vertical` exist only here.
  Compact contexts (panel, prompt, tile and palette-only icons) draw only `network`, `tunnel` and `more.vertical` from
  it and the path glyphs otherwise.
- **The chrome set** (`assets-chrome.tsv`, 20 files): `add`, `window.add`, `split`, `zoom`, `chevron.down`, `folder`,
  `server`, `bell`, `lock`, `terminal`. Only `chromeIcon(name, size[, artwork])`, the native workspace chrome's
  context (title bar, toolbar, tool window stripes and status bar), draws them; descriptor, panel, tile and plugin
  contexts keep the path glyphs for these names, except for a toolbar strip's and a panel header's classic names
  (below).
- **The classic set** (`assets-classic.tsv`, 26 files, IntelliJ's classic icons): a chrome receiver marked
  `ChromeArtwork.CLASSIC` (a look's `[chrome] icons = "classic"`) draws them for add, split, zoom, settings, new window
  and search, and the chrome set's New UI originals for the rest. The host-only `KitView.classicChromeIcons(true)` gives
  a toolbar strip's and a panel header's buttons and menu buttons named one of `IconRegistry.classicChromeNames()` the
  same art (`IconRegistry.classicChromeIcon`); a document bar and every body keep theirs. The `run` and `application`
  names are authored host artwork from this set (`BuiltInArtwork`) wherever a control draws them. Its `toolwindows/`
  rows are a table of their own, reached only through `IconRegistry.stripeIcon`, which gives a labelled stripe
  IntelliJ's classic 13 px tool-window icons for `network`, `folder`, `tunnel`, `download`, `server` and `bell`, so the
  classic chrome set is unchanged.
- **The document actions** (`assets-editor.tsv`, 16 files under `editor/`): `editor.source`, `editor.split`,
  `editor.preview` and `editor.scroll-sync` are IntelliJ's New UI editor-only, editor-and-preview, preview-only and
  synchronise-scrolling icons, and the same names with `.classic` its classic ones. Ordinary, panel and native
  contexts draw them in their own colours, never tinted, with the sibling for the theme's darkness (the panel
  surface's, given a palette only); they are decoded the first time one is asked for, and `IconRegistry.names()`
  lists them.

Receiving contexts:

| Method | Context |
| --- | --- |
| `icon(name, theme \| palette, hue \| tint, size)` | Ordinary artwork in a hue or semantic tint |
| `icon(name, surface, foreground, size)` | Artwork on a surface outside the palette: the foreground moved toward black or white until it reaches 3:1 (`surfaceTint`) |
| `panel(...)` | Compact panel artwork: a lighter stroke for `add`, `search`, `edit` and `import`, 2 px dots for `more` |
| `prompt(...)` | Prompt glyphs, with a 1.75 px optical stroke at 24 px |
| `tile(...)` | A category tile at the kit's tile opacity; selected tiles on the accent |
| `identityHeader(...)` | An identity header tile outlined in `border.identity` |
| `themedIcon(name, size)`, `chromeIcon(name, size)`, `chromeIcon(name, size, ChromeArtwork)` | A `PreparedIcon` for native Swing, resolved against the live theme; the chrome forms draw the chrome or classic set |
| `classicChromeIcon(name, size)`, `stripeIcon(name, size)` | An optional `PreparedIcon` of authored light and dark art: one of the six classic chrome names' classic art, or a labelled stripe's classic tool-window icon; empty for any other name |

`size(metrics, IconSize)` maps the model's sizes to roles: CAPTION `ICON_CAPTION` (12), CONTROL `ICON_CONTROL` (14),
RAIL `ICON_RAIL` (16), EMPTY `ICON_EMPTY` (20), PROMPT `ICON_PROMPT` (24; 16 in the classic catalog) and SWATCH
`ICON_SWATCH` (12; 10 classic), a solid square of the icon's hue.

Registrations are named `namespace:name` (`[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*`), and a name already registered is
refused:

- **Path artwork** (`IconArtwork`) is colour-free outline data on the 16-unit grid, drawn with the kit's stroke
  (1.5 for registrations; the class accepts 1.4 to 1.6). It must start with a move, contain drawable segments and fit
  the grid; the path is bounded in length, command count and coordinate size.
- **Encoded artwork** (`EncodedArtwork`, SVG or PNG) is 1 to 262,144 bytes, decoded once, at most 4096 pixels a side
  and 4,194,304 pixels in all. An SVG must have an `svg` root and passes the receiving rules: no doctype or external
  entities, no processing instructions, no `script`, `foreignObject`, `animate`, `animateTransform` or `set`, no `on*`
  or `base` attributes, fragment-only `href` and `url(...)`, no `\` or `@` in CSS, depth at most 128; external
  resources are denied. `EncodedArtwork.checkSvg(bytes)` applies the rules without AWT and `raster(width, height)`
  draws the artwork into a new image (1 to 8192 pixels a side).
- **Identity artwork** (`IdentityArtwork(artwork, tileHue, selectedInk)`) adds a tile hue (not NONE) and whether a
  selected tile keeps the authored ink (`AUTHORED`) or draws a foreground silhouette (`FOREGROUND`).
- `ReceivingArtwork` gives a receiving view scoped aliases (`received<N>:art<M>`) for host-supplied artwork, reconciled
  on the EDT.

Named artwork expresses explicit intent: category tiles follow explicit SHEET/DETAIL, PROMPT/RAIL and single/double-list
roles, and ordinary CONTROL folder icons remain plain glyphs. Plugins name icons through the SDK
([plugin icons](sdk-icons.md)).
