# Jasper Slate Steel themes

Status: **Approved for implementation planning on 2026-10-12.** The user approved the written spec ("lgtm"). Plan 1 (engine and kit) complete on 2026-10-12 on `claude/slate-steel`; Plans 2 and 3 next.
Branch: `claude/slate-steel`, from `codex/theme-engine` at `241d6e22`.

This amends the [theme engine specification](2026-10-04-jasper-theme-engine-design.md) and supersedes the
[Settings theme policy amendment](2026-10-11-jasper-settings-theme-policy-amendment.md)'s persisted theme policy
(`ui.theme.name`, `ui.theme.source`, `ui.theme.follow_system` and the bundled Jasper Dark/Light pairing). Everything
else in those documents stands.

## 1. Intent and decisions

Jasper ships one UI theme, **Slate Steel**, in Dark and Light, and three terminal colour schemes, **Slate Steel
Dark**, **Slate Steel Light** and **High Contrast**, as defined by the user's `theme.md` (2026-10-12). Slate Steel
Dark and Light are each a complete matching set: UI, terminal scheme and editor scheme.

The user's decisions (2026-10-12):

1. Remove Clear Dark, Nord, Clear Light, Jasper Dark and Jasper Light.
2. **Slate Steel only.** No installed themes, no themes folder and no theme import (IntelliJ or Alacritty). The only
   choices are the appearance (Dark, Light, Match System) and the terminal scheme.
3. A terminal scheme that differs from the UI variant recolours **the terminal text area only**. Tabs, the tab strip,
   pane headers, the editor, panels and dialogs stay on the UI variant.
4. The Appearance page keeps the mock's layout: the Dark / Light / Match System tiles, then a **read-only "Theme"
   dropdown** showing Slate Steel as its one entry, without the gear.
5. **Token-native themes** (approach A): one bundled theme file, read straight into the engine's tokens and palettes;
   the legacy TOML pipeline, the catalog, import and the old themes are removed.
6. Slate Steel Light and Dark include **matching terminal and editor schemes**. `theme.md` has no editor scheme, so
   one is derived from the Slate Steel colours (section 3.4).
7. The three terminal colours that miss `theme.md`'s own contrast targets ship **as authored**; the contrast test
   records them as named exceptions (section 9).

`theme.md`'s rules bind the implementation: components reference token keys, never hex values; hex values live only
in the theme file; `#RRGGBB @ N%` is that colour at N% opacity over whatever is beneath it; Dark and Light share the
same keys and are resolved by key, never by assuming a lighter or darker direction; `hover.overlay` is the only token
whose base flips (white in Dark, black in Light), and components apply its alpha; the terminal scheme is chosen
independently of the UI theme and by default follows it; macOS window controls are native and not themed.

Jasper is an MVP: settings and APIs are removed outright, with no migration, aliases or deprecation shims.

## 2. Scope

In scope: the bundled theme file and its reference copy; the engine's theme model, tokens, palettes and derivations;
the two settings and their resolution; terminal panes; the Appearance page; the kit components named by `theme.md`'s
"Used by" columns; the shadows; removal of the old theme pipeline, assets, tools and settings; SDK 0.15.5; tests,
documentation and the visual re-verification.

Out of scope: geometry, typography and layout (the mock-calibrated geometry stands); the macOS window frame and its
controls; new themes or theme authoring; plugin-contributed themes (plugins never contributed themes).

## 3. Theme data

### 3.1 The bundled file

`jasper-ui` bundles `dev/jasper/ui/theme/slate-steel.json`. Its `ui` and `terminal` objects are `theme.md`'s
machine-readable block exactly as written: `ui.slate-steel-dark`, `ui.slate-steel-light`,
`terminal.slate-steel-dark`, `terminal.slate-steel-light` and `terminal.high-contrast`. Colours with transparency use
8-digit `#RRGGBBAA` hex. Section 3.4 adds an `editor` object. No colour value exists anywhere else in production code
or resources.

The repository keeps `theme.md` as `docs/design/theme-engine/slate-steel.md`, the colour reference, extended with
the derived editor schemes (section 3.4) in its prose and in its JSON block. A test requires the bundled file to equal
that JSON block, so the reference and the file cannot drift.

### 3.2 UI tokens

Each UI variant fills the engine's semantic palette by key. The engine gains ten semantic roles that `theme.md`
defines and the engine lacks: `surface.field.raised`, `surface.control.selected`, `surface.tabstrip`,
`surface.emptyTile`, `border.field.raised`, `border.field.raised.hover`, `text.strong.secondary`, `text.field.raised`,
`segment.track` and `button.secondary.border`.

Roles the file does not list are defined by rules in code, never by hex:

| Role | Rule |
| --- | --- |
| `border.focus` | `accent` (`theme.md`: "= accent") |
| `surface.status` | `surface.app` (the status bar uses `surface.app`) |
| `hue.<name>.surface` | `hue.<name>`: the tile's base, to which the kit applies the tile alpha (section 3.3) |
| `border.identity` | `border.control` (the existing rule) |

Any other role with neither a value nor a rule is a defect: loading fails with a message naming the role, and a test
enforces complete coverage for both variants.

### 3.3 Opacity, applied once

A token that carries alpha (`surface.scrim` at 55% Dark / 25% Light, `border.subtle`, `text.onAccent.secondary`) is
painted as given. The kit's own scrim percentages are removed: `ModalOverlay`'s `OVERLAY_SCRIM_PERCENT` and the 35% of
the attached-sheet scrim (`BodyScrim`). The pre-change default of `surface.scrim` (black) goes with the derivation
layer.

Treatments that `theme.md` assigns to components remain kit metrics applied to a base token: the focus ring is
`accent` at 25% (about 32% in Dark), 3 px; the error ring is `border.error` at 22%; hovers apply `hover.overlay` at
4–8%; tile and chip backgrounds apply `hue.<name>.surface` at 17% in Dark and 13% in Light (`theme.md`: 16–18% and
12–14%).

### 3.4 Terminal and editor schemes

Each terminal scheme becomes a terminal palette: background, foreground, bold foreground, cursor, selection and the
16 ANSI colours (`theme.md` section 2). The bold foreground is applied to bold text in terminal panes.

`theme.md` has no editor scheme. The bundled file's `editor` object defines one for each UI variant, as references to
the file's own keys (with `theme.md`'s `key @ N%` notation where needed), so it adds no colour values:

| Editor colour | `editor.slate-steel-dark` | `editor.slate-steel-light` |
| --- | --- | --- |
| Background, gutter | `terminal.bg` (equal to `surface.terminal`) | same |
| Text; variables | `terminal.fg` | same |
| Caret | `terminal.cursor` | same |
| Selection | `terminal.selection` | same |
| Current line | `hover.overlay @ 5%` over the background | same |
| Line numbers | `text.tertiary` | same |
| Keywords | `ansi.magenta` | same |
| Types; regular expressions | `ansi.cyan` | same |
| Functions | `ansi.blue` | same |
| Strings | `ansi.green` | same |
| Numbers, constants, annotations, attributes | `ansi.yellow` | same |
| Tags; errors | `ansi.red` | same |
| Operators, punctuation | `text.secondary` | same |
| Comments, doc comments (italic) | `ansi.bright.black` (the Dark dim tone) | `ansi.white` (the Light dim tone) |

Terminal references resolve in the variant's own scheme (Slate Steel Dark for Dark, Slate Steel Light for Light); UI
references resolve in the variant. Measured against the editor background, every text role is at least 4.5:1 (lowest:
Light types, 4.87:1), comments at least 3:1 (5.89:1 Dark, 5.77:1 Light) and line numbers at least 3:1 (4.79:1 Dark,
4.24:1 Light). The reference copy's prose gains an "Editor color schemes" section stating this table and that it is
derived.

The editor always follows the UI variant (decision 3); High Contrast has no editor scheme. With the terminal scheme on
"Same as UI theme", the UI, terminals and editor show the same Slate Steel variant.

## 4. Engine model

`dev.jasper.ui.theme` loads the bundled file once (`SlateSteel`) and builds, for each variant, a `GeneratedTheme`
holding the semantic palette, the variant's terminal palette, the editor palette and syntax styles, and the FlatLaf UI
defaults; the three terminal palettes are available by scheme. `GeneratedTheme` loses its legacy token map; its other
uses (route selection, glass levels, change notification through `ThemeBinding`/`ThemePair`) stay.

Derived in code from tokens, never from hex:

- FlatLaf's UI defaults, through the existing UI-key mapping (`UiColorKeys`), rewritten onto semantic roles.
- The SDK's `WorkspacePalette`, `EditorPalette` and `SyntaxPalette`, from the semantic palette and the editor scheme.
- The alpha treatments (section 3.3), each applied once to its base token.

Removed from the engine: `Token` and the legacy-to-semantic defaults (`SemanticDefaults`), the resolver and theme
inheritance (`ThemeResolver`, `ThemeDocument` and their parents), the legacy UI-key tables (`UiTemplate`'s token
section, `UiTokenKeys`), seeds, `ThemeGenerator`'s derivation rules (it is rewritten to assemble a `GeneratedTheme`
from the palettes), `themeio` and `themeimport` with their resources.

## 5. Settings and runtime

### 5.1 Settings

Removed outright with their aliases: `ui.theme.name` (`ui.theme.variant`), `ui.theme.source`,
`ui.theme.follow_system`, `ui.theme.workspace` (`terminal.colors`, `ui.theme.terminal`). Added:

| Key | Values | Default |
| --- | --- | --- |
| `ui.appearance` | `"system"`, `"dark"`, `"light"` | `"system"` |
| `terminal.scheme` | `"match"`, `"slate-steel-dark"`, `"slate-steel-light"`, `"high-contrast"` | `"match"` |

`match` selects Slate Steel Dark with the Dark UI and Slate Steel Light with the Light UI. An unknown value uses the
default and is reported in the existing "config.toml has warnings" notice; a removed key left in a file is reported as
an unknown setting there.

### 5.2 Resolution

The UI variant is Dark or Light: `ui.appearance`, or with `system` the operating system's appearance, followed live;
when the OS appearance is unavailable the variant is Dark and a diagnostic is logged. OS changes never write
configuration or mark Settings dirty. The one variant serves the chrome and workspace routes alike. The terminal
scheme resolves independently to a terminal palette. Plugins receive the same change notifications as today.

### 5.3 Terminal panes

A terminal pane's text area takes background, text, bold text, cursor, selection and ANSI colours from the terminal
palette. Everything around it stays on the UI variant, including the active attached tab (`surface.terminal`) and the
tab strip (`surface.tabstrip`). With a matching scheme `terminal.bg` equals `surface.terminal`, so pane and tab meet
seamlessly; with High Contrast the text area is black and the tab stays `surface.terminal`. Workspace opacity and blur
apply to the terminal area as today.

### 5.4 Start-up

No themes folder is scanned and no catalog is built; the bundled file is parsed once.

## 6. The Appearance page

- **Theme card.** The Dark / Light / Match System tiles write `ui.appearance` (`dark`, `light`, `system`). Their
  previews draw on Slate Steel's variants; Match System shows both halves. Below them, the "Theme" row holds a
  read-only dropdown whose only entry is "Slate Steel", without the gear (Import theme… is removed).
- **Scheme row.** Label **"Terminal scheme"**, description **"Colors for terminal panes"**, choices "Same as UI
  theme", "Slate Steel Dark", "Slate Steel Light", "High Contrast". This deliberately differs from the mock's
  "Workspace scheme / Colors for terminal and editor panes", because the scheme now colours terminals only.
- Search keywords, Reset and the draft/Apply/Cancel behaviour follow the existing Settings rules.

## 7. Components on the tokens

Plan 1 audits every "Used by" entry in `theme.md` against the kit component that paints it and records, in the
acceptance record, the role each used before and uses after. The target roles:

| Token | Component |
| --- | --- |
| `surface.app` | Title bar, gutters between panels, status bar, activity rails |
| `surface.panel` / `surface.panel.raised` | Tool window body / panel footer, detail card |
| `surface.dialog` | Dialog, sheet and Settings window body |
| `surface.card` | Grouped form or Settings card |
| `surface.sidebar` | Window sidebars (Vault, Settings, file browser), window footer |
| `surface.input` | Text, search and password fields, inset lists, steppers |
| `surface.field.raised`, `border.field.raised`, `border.field.raised.hover`, `text.field.raised` | The title bar's command field: fill, edge, hover edge, label |
| `surface.control`, `surface.control.hover` | Dropdown, combobox trigger, picker; their hover |
| `surface.control.selected` | Active activity-rail item, active pill tab |
| `surface.menu`, `border.menu` | Menus, combobox popover, command palette; their edge and separators |
| `surface.terminal` | Terminal area, active attached tab |
| `surface.tabstrip` | Tab strip behind inactive tabs |
| `surface.emptyTile` | Empty-state icon tile |
| `surface.scrim` | Backdrop behind sheets, the command palette and modal overlays |
| `border.window` / `border.subtle` / `border.input` / `border.control` | Window and dialog edge / panel and card edges, row dividers / fields, steppers, segmented track / dropdown, combobox |
| `button.secondary.bg`, `.bg.hover`, `button.secondary.border` | Secondary button fill, hover and edge (the edge moves off `border.control`) |
| `text.primary` / `text.secondary` / `text.strong.secondary` / `text.tertiary` / `text.placeholder` | Titles and values / labels, descriptions, default icon tint / Settings row titles, secondary-button labels, current breadcrumb / captions, counts, section labels, row subtitles / input hints, empty picker value |
| `text.onAccent`, `text.onAccent.secondary` | Primary button, highlighted menu row, selected list row; subtitles on them |
| `text.link`, `text.link.hover` | Link buttons, selected checkmark; their hover |
| `accent`, `accent.hover` | Primary button, toggle on, focus, menu and palette highlight, active tab indicator; primary hover |
| `selection.bg` | Selected list rows, including the Settings navigation |
| `segment.track`, `segment.selected.bg` | Segmented control track and selected segment (Light adds the soft shadow) |
| `toggle.track.off`, `toggle.knob` | Toggle |
| `status.*`, `hue.*` | Status dots and states; category tiles, chips and sidebar icons |

A component that disagrees moves to its role. Only colours change; the calibrated geometry stands.

## 8. Shadows

`theme.md`'s shadows are kit metrics (offset, blur or spread, and alpha of black), with Dark and Light strengths,
not theme colours:

| Use | Dark | Light |
| --- | --- | --- |
| Primary button | Inset top line white at 14%, 1 px drop black at 30% | Same inset line, 1 px drop black at 12% |
| Popover, menu | Black at 50%, 12 px offset, 32 px blur | Black at 18% |
| The modal overlay's card | Black at 55–60%, 30 px offset, 80 px blur | Black at 22% |

Native windows, attached sheets among them, keep the macOS window shadow. The modal overlay's current shadow (`OVERLAY_SHADOW`,
`OVERLAY_SHADOW_PERCENT`) is replaced by the dialog shadow.

## 9. Removals

- `jasper-ui`: `themeimport` and its resources (the five native TOMLs, the IntelliJ `.theme.json` files,
  `THIRD-PARTY-THEMES.md`); `themeio`; the legacy layer listed in section 4.
- `jasper-app`: `themeimport` (conversion, the one-time theme migration, its journal), `themesui` (import sheets and
  dialogs), `ThemeCatalog`, `BuiltInThemes`, `ThemeManager`'s themes folder and installed-theme handling, `Theme`'s
  named constants, `ThemeSource`, `WorkspaceThemeChoice`, `themes/legacy-aliases.json`. `ThemeChoices`,
  `ThemeSelection` and `WorkspaceEditors` are rewritten for section 6.
- Repository: `third_party/themes`, `tools/themes`, the root `THIRD-PARTY-THEMES.md`, the `convertThemes` and
  `themeMigrationPreview` Gradle tasks.
- Documentation: `docs/themes.md` is rewritten for Slate Steel, the appearance setting, terminal schemes and the token
  rule; theme authoring and import instructions are removed everywhere (configuration guide, plugin guides, the
  `config.toml` example).

## 10. SDK 0.15.5

Additive. `dev.jasper.sdk.ui.SemanticColor` gains the ten roles of section 3.2; the host and the testkit map them as
they map the existing roles. `JasperSdk.VERSION` becomes `0.15.5` and every bundled manifest requires
`>=0.15.5, <0.16`. No plugin-facing type is removed: plugins keep the same tokens, `WorkspacePalette`,
`EditorPalette`, `SyntaxPalette` and change events, with values derived from Slate Steel.

## 11. Testing

- **Theme file**: both UI variants cover every semantic role by value or rule; the bundled file equals the reference
  copy's JSON block; a source scan fails on any colour hex literal in production Java or resources outside the
  bundled file.
- **Contrast**: each terminal palette meets `theme.md`'s targets against its own background (Dark normal ≥7:1, bright
  ≥9.5:1; Light normal ≥4.9:1, bright ≥4.5:1; High Contrast normal ≥8:1, bright ≥12:1), with the named exceptions:
  black in Dark and High Contrast (backgrounds only); Dark bright black (dim tone, 5.89:1); Light black and bright
  white (text and emphasis); Light cyan `#0F7A8A` (4.87:1, as authored); High Contrast bright red `#FFA8A2` (11.37:1,
  as authored) and bright black `#A6A6A6` (8.63:1, the dim tone, as authored). The editor schemes meet section 3.4's
  ratios.
- **Settings**: the appearance policy (dark, light, system with live OS changes, unavailable OS appearance); each
  terminal scheme value; warnings for unknown values and for removed keys.
- **Terminal panes**: the scheme recolours the text area only; the surrounding tab and strip keep UI roles.
- **Components**: one test per section 7 row that a component paints its role.
- **Test migration**: references to the removed themes (about 150 files) move to a Slate Steel test helper; test-only
  calibration themes are retired.

## 12. Verification

All recorded mock comparisons (`tools/visual/pairs.json`) are re-rendered with Slate Steel Dark and recorded in the
acceptance record; every family is also rendered in Light and inspected. The comparisons measure luminance edges, so
mocks drawn in other palettes (the Vault mock's accent is `#4676EE`) still compare. The user runs the native pass.

## 13. Phasing

Three plans, each leaving `./gradlew check` green:

1. **Engine and kit.** The bundled file and its reference copy, the loader, the ten roles, opacity from tokens, the
   terminal and editor palettes, the derivations, the component audit (section 7) and the shadows (section 8), and
   SDK 0.15.5. The new path is built alongside the old one.
2. **App switch.** The two settings and their resolution, terminal panes, the Appearance page, and Slate Steel as the
   only theme.
3. **Removal and verification.** Delete the old pipeline, assets, tools and settings; migrate the tests; update the
   documentation; re-render and record the comparisons.
