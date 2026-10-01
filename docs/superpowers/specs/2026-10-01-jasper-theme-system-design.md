# Jasper theme system

**Status:** Design approved in chat by the user on 2026-10-01, section by section. Plan 1 (format/generator) and its
review fixes are merged to main at `5fc76c7d`; implementation deviations are recorded in plan 1 and `docs/STATUS.md`.
[Remaining-work assessment and plans 2–5](../plans/2026-10-01-theme-system-remaining-work.md) track implementation.
Plan 2 is implemented, headless-verified and independently reviewed, then merged and verified on local `main`
on 2026-10-01 (not pushed); user GUI acceptance remains pending. Plans 3–5 remain future work. Plan 2 deviations are recorded in its status banner and `docs/STATUS.md`;
the assessment labels later integration decisions. The user approved the combo-dropdown clarification below on
2026-10-01 while planning Plan 3.

Replaces, once implemented: the IntelliJ Themes page and Marketplace client
([2026-09-30-intellij-theme-marketplace-design.md](2026-09-30-intellij-theme-marketplace-design.md)), the Alacritty
Themes page and `terminal-themes/` folder
([2026-09-30-alacritty-terminal-themes-design.md](2026-09-30-alacritty-terminal-themes-design.md)), and the runtime
IntelliJ theme loading of the theme engine
([2026-09-25-jasper-theme-engine-design.md](2026-09-25-jasper-theme-engine-design.md)). The FlatLaf base and the
contrast-floor derivation from that engine are kept.

## Purpose

The tab strip inside the terminal pane takes its colours from the UI theme, so IntelliJ Light with a dark Alacritty
terminal palette draws a white tab on a dark terminal. Behind that is a structural issue: UI themes are IntelliJ
`.theme.json` files, terminal themes are Alacritty files, and the editor's syntax colours are RSyntaxTextArea's fixed
`idea.xml` / `dark.xml`. Nothing ties them together, and the product's look depends on JetBrains' ecosystem.

Jasper gets its own theme format. A Jasper theme describes everything: chrome, terminal palette, editor surfaces and
syntax. The app is split into two zones, each with its own theme, so a light UI with a dark workspace looks deliberate
rather than broken.

## Decisions (user, 2026-10-01)

1. **Own format, own themes.** Jasper themes are the only runtime format. IntelliJ and Alacritty become *import
   sources*, converted once.
2. **Vendor, don't browse.** Remove the IntelliJ Themes page with its JetBrains Marketplace client, and the Alacritty
   Themes page with its `terminal-themes/` folder. Instead bundle, converted: the 177 Alacritty themes already vendored,
   the ten most-downloaded permissively licensed Marketplace themes (section 6), IntelliJ Light and Darcula.
3. **Importers for both.** "Import theme…" accepts IntelliJ `.theme.json`, plugin `.jar`/`.zip` and Alacritty `.toml`,
   converts with the same code that vendors, and writes a Jasper theme the user owns.
4. **Three layers in TOML:** seed colours (required), Jasper tokens and syntax roles (optional, derived), raw UI keys
   (optional escape hatch).
5. **Syntax colours are part of the theme** and follow the workspace, so a dark terminal means a dark editor.
6. **Two zones.** The chrome follows the UI theme; the workspace (terminal panes, editor panes, the pane tab strip,
   split dividers *and plugin panes*) follows the workspace theme.
7. **The host restyles plugin panes automatically**; the SDK adds palettes for what plugins paint themselves.
8. **Defaults are Jasper's own:** Jasper Dark (default) and a new Jasper Light, both written natively. IntelliJ Light and
   Darcula become ordinary bundled themes. The New UI / Islands / High Contrast files stay only as importer-only
   parents.
9. **The vendoring list** in section 6, with renames for trademarked names, Studio's terminal palette written by hand,
   and one theme per name (the IntelliJ conversion wins over an Alacritty duplicate).
10. **Files plus a picker:** a Themes page with two pickers, a live preview, Import…, Duplicate… and Open themes folder.
    No visual theme editor (a seed-colour editor is a possible later plan).

## 1. The model

| Concept | Meaning |
|---|---|
| Jasper theme | A TOML file (section 2). Always describes chrome, workspace, terminal palette and syntax. |
| Generated theme | A Jasper theme run through the generator (section 3): resolved tokens, syntax roles, terminal `Palette`, `dark`, and for chrome use the full FlatLaf key set. |
| Chrome | Title bar, toolbar, rail, sidebar panels, status bar, Settings, dialogs, menus, popups (except a workspace combo dropdown, §5.3), tooltips. |
| Workspace | Everything inside the tabbed content area: terminal panes, editor panes, the pane tab strip, split dividers, the find bar, plugin panes. |
| `ui.theme.name` | The chrome theme, by name. Default `Jasper Dark`. |
| `ui.theme.workspace` | `match` (default: the chrome theme) or any theme name. Replaces `terminal.colors`, which stays a read alias. |

Only one FlatLaf look and feel is ever installed: the chrome's. The workspace theme is applied through host painting,
per-component FlatLaf styles and SDK palettes (section 5).

## 2. The theme file format

### Location and identity

- Bundled: `jasper-app/src/main/resources/dev/jasper/app/themes/<id>.toml`, listed in an `index.txt` (resources cannot be
  listed from a jar).
- User: `<home>/themes/<id>.toml`.
- `<id>` is the file stem; `name` is what config and pickers use, matched ignoring case and surrounding whitespace. A user
  theme takes precedence over a bundled theme of the same name, as today. Two user files with the same name: the first
  in file-name order wins and the other gets a configuration warning.

### Layout

```toml
name    = "Nord"
dark    = true              # optional; inferred from background luminance (relative luminance < 0.5)
extends = "Jasper Dark"     # optional
author  = "Arctic Ice Studio"   # optional metadata
license = "MIT"                 # bundled themes also carry source and commit
source  = "https://github.com/nordtheme/jetbrains"
commit  = "…"

[colors]                    # required
background = "#2e3440"      # required
foreground = "#d8dee9"      # required
accent     = "#88c0d0"      # required
cursor     = "#d8dee9"      # optional: foreground
selection  = "#434c5e"      # optional: derived (section 3)
[colors.normal]             # required: black red green yellow blue magenta cyan white
black = "#3b4252"
# …
[colors.bright]             # optional: each bright colour derived from its normal colour (section 3)
black = "#4c566a"
# …

[tokens]                    # optional; any subset of the tokens in section 2.1
tab_active = "#3b4252"

[syntax]                    # optional; any subset of the roles in section 2.2
keyword = "#81a1c1"
comment = { color = "#616e88", italic = true }

[ui]                        # optional; raw FlatLaf/IntelliJ UI keys, applied to the chrome install only
"Button.arc" = 6
"Component.focusWidth" = 1
```

Colours are `#RRGGBB` or `#RRGGBBAA`. `[colors.normal]` / `[colors.bright]` use Alacritty's layout so Alacritty
conversions are close to a copy. A `[syntax]` value is a colour string or an inline table with `color` (required), `bold`
and `italic` (default false). `[ui]` values are colours, numbers, booleans or strings, passed to FlatLaf as typed.

With `extends`, `[colors]` (including `normal` and `bright`) is optional and every section is merged key by key over the
parent's; `dark` is inherited unless set. Without `extends`, `background`, `foreground`, `accent` and the eight normal
colours are required.

### 2.1 Tokens

Every token is derivable from the seeds; a theme sets only those it wants to fix.

| Group | Tokens |
|---|---|
| Surfaces | `surface`, `surface_raised` (popups, menus, tooltips), `surface_sunken` (fields, lists, tables), `title_bar`, `status_bar`, `sidebar` |
| Text | `text`, `text_muted`, `text_disabled`, `text_on_accent`, `link` |
| Lines | `border`, `separator`, `divider`, `focus_ring` |
| Interaction | `hover`, `pressed`, `selection`, `selection_text`, `selection_inactive` |
| Tabs | `tab_bar`, `tab_active`, `tab_active_text`, `tab_text`, `tab_hover`, `tab_underline`, `tab_underline_inactive` |
| Controls | `input_background`, `input_border`, `button_background`, `button_text`, `scrollbar_thumb` |
| Status | `success`, `warning`, `error`, `info` |
| Editor | `editor_background`, `editor_foreground`, `editor_caret`, `editor_selection`, `editor_current_line`, `editor_gutter`, `editor_line_numbers` |

They replace today's `Jasper.*` key derivation: `ChromeKeys` becomes the token → key mapping (section 3).

### 2.2 Syntax roles

`keyword`, `type`, `function`, `variable`, `constant`, `number`, `string`, `comment`, `doc_comment`, `annotation`,
`operator`, `punctuation`, `tag`, `attribute`, `regex`, `error`.

### 2.3 Errors

A file is skipped, with a configuration warning naming the file and line (the existing config diagnostics), when it:
- is not valid TOML;
- has an unknown top-level key, section, token or syntax role (a typo must not pass silently);
- has an unparseable colour;
- lacks a required seed and has no `extends`;
- `extends` a theme that does not exist, or is part of an `extends` cycle.

Unknown `[ui]` keys are not errors: FlatLaf and plugins read keys Jasper does not know.

A setting that names a missing theme falls back to Jasper Dark or Jasper Light, by the last-known `dark` of that slot,
with a warning (as `ui.theme.name` does today). `ui.theme.workspace` naming a missing theme falls back to `match`.

## 3. The generator

`ThemeGenerator` (in `dev.jasper.app.appearance`) turns a resolved Jasper theme into a `GeneratedTheme`:

```java
record GeneratedTheme(String name, boolean dark, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                      Palette terminal, Map<String, Object> uiKeys)
```

1. **Resolve `extends`**, merging layer by layer.
2. **Fill seeds:** `cursor` = foreground; `selection` = mix(accent, background, 0.35) moved to at least 1.5:1 against
   the background; a missing bright colour = its normal colour mixed 20 % toward white on dark themes and toward black
   on light ones. A missing `dark` is inferred from the background's relative luminance.
3. **Derive tokens** that the theme did not set, from seeds and other tokens, in a fixed order documented as a table in
   `ThemeGenerator`'s JavaDoc and tested one by one. The defining choices: `surface` = background; `title_bar`,
   `status_bar`, `sidebar` = surface shifted 4 % toward foreground; `surface_raised` = 7 %; `surface_sunken` = 3 %
   toward black (dark) or white (light); `border` = 16 %; `text_muted` = foreground mixed 35 % toward background;
   `tab_active` = surface shifted 10 % toward foreground; `tab_underline` = accent; status colours from ANSI green,
   yellow, red, blue; editor tokens from background, foreground, cursor and selection. Derived text and stroke tokens
   pass through today's contrast floors (`ChromeKeys.readable`: 4.5:1 for text, 3:1 for muted text, borders and
   strokes). **Explicitly set values are never altered.**
4. **Derive syntax roles** that the theme did not set from ANSI: keyword magenta, type cyan, function blue, variable
   foreground, constant and number yellow, string green, comment and doc_comment bright black (italic), annotation
   yellow, operator and punctuation foreground muted, tag red, attribute yellow, regex cyan, error red. Derived roles are
   moved to at least 4.5:1 against `editor_background` (comments 3:1).
5. **Terminal palette:** `Palette(foreground, background, cursor, selection, the 16 ANSI colours)`.
6. **FlatLaf keys (chrome only):** start from FlatLaf's light or dark base and set FlatLaf's base variables
   (`@background`, `@foreground`, `@accentColor`, `@selectionBackground`, `@selectionForeground`, `@disabledForeground`
   and the others FlatLaf 3.7's properties derive from) so FlatLaf re-derives its control keys; then map tokens onto
   the keys Jasper and FlatLaf read (the `Jasper.*` keys, `Panel.background`, `EditorTabs.*`, `MainToolbar.*`,
   `SearchEverywhere.*`, `StatusBar.*` …); then lay `[ui]` over everything.

**Plan 1 spike:** before the generator is built, a test confirms that setting FlatLaf 3.7's base variables through
extra defaults on `FlatDarkLaf` / `FlatLightLaf` re-derives dependent control keys (for example, changing
`@background` changes `Panel.background`, `TextField.background` and `List.background` as FlatLaf's properties say).
If it does not, the generator maps every token onto concrete keys itself, and the spike's outcome is recorded in the
plan status.

`ThemeManager` installs the chrome's generated theme in place of today's IntelliJ JSON path; a failed install rolls
back to the previous look and feel, as today. The SDK `Variant` is the chrome theme's `dark`.

## 4. Built-in Jasper themes

- **Jasper Dark** is rewritten as `jasper-dark.toml`. Its seeds and tokens reproduce today's look (the colours in
  `jasper-dark.theme.json` and the graphite palette in `Theme.graphitePalette()`); a test compares the main chrome keys
  with today's values.
- **Jasper Light** is new, `jasper-light.toml`: a neutral light theme in Jasper Dark's spirit (the same accent family,
  a soft grey chrome, white workspace), with its own ANSI set tuned for 4.5:1 on white. It replaces IntelliJ Light as
  the default light theme (`ui.theme.variant = "light"` maps to it).
- **IntelliJ Light** and **Darcula** are converted from the vendored IntelliJ files (section 6) and offered as ordinary
  bundled themes.

## 5. The workspace zone

### 5.1 Resolution

`ThemeController` resolves two generated themes. `ResolvedTheme(Theme chrome, Palette palette)` becomes
`ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace)`. With `ui.theme.workspace = match` both fields are the
same object and every workspace mechanism below is a no-op. Both settings switch live across windows.

### 5.2 Host-painted workspace surfaces

The terminal palette, the pane tab strip (`WindowTabs`), split dividers (`SplitDividerBorder`), the find bar and the
terminal pane background read workspace tokens from the resolved theme instead of `UIManager` `Jasper.*` keys. The
chrome keeps reading `UIManager`. This fixes the original screenshot.

### 5.3 Plugin pane restyler

`WorkspaceStyler` (app, `dev.jasper.app.workspace`):
- Every pane mounted in the workspace gets the client property `Jasper.zone = "workspace"` on its root.
- The styler walks the subtree and gives each component a FlatLaf style (`FlatLaf.style`) built from workspace tokens,
  per component type: `JPanel`, `JLabel`, `JList`, `JTable` (and its header), `JTree`, text components, `JComboBox`
  (including `popupBackground`), buttons, check boxes, radio buttons, `JScrollPane`, `JScrollBar`, `JSplitPane`,
  `JTabbedPane`, `JToolBar`, `JSeparator`.
- The generated style is placed *before* the plugin's own `FlatLaf.style`, so the plugin's values win. The styler
  records what it added in `Jasper.workspaceStyle`, so a theme change replaces only its part, and removing a
  component from the zone restores its original style.
- A container listener styles components added later.
- `Jasper.workspace.exempt = true` on a component exempts its subtree.
- A workspace combo box's own dropdown follows the workspace, including its background and selected rows.
  This control-owned exception was clarified by the user on 2026-10-01. Other popups, menus, tooltips and dialogs
  opened from a pane are chrome; never infer workspace colors by following arbitrary popup invokers.
- With `match`, the styler does nothing.

**Plan 3 spike:** a headless test builds a representative pane (list, table, tree, text field, combo, scroll pane,
tabbed pane) under a light chrome and a dark workspace and asserts each component's effective colours come from the
workspace tokens. Gaps get a direct `setBackground`/`setForeground` fallback in the styler, or a documented
"paint with `WorkspacePalette`" note in plugin authoring. The bundled plugins' custom renderers are audited to read
`WorkspacePalette`.

### 5.4 Icons

The host's named icons choose their artwork at paint time: `paintIcon(component, …)` looks up the nearest ancestor
`Jasper.zone` and uses the workspace theme's light or dark artwork inside the workspace. No SDK change.

### 5.5 SDK 0.12.0

A minor bump from 0.11.x. Every bundled plugin's `plugin.toml` range moves to `>=0.12.0, <0.13`.

- `dev.jasper.sdk.ui.WorkspacePalette`: a record of `background`, `surfaceRaised`, `surfaceSunken`, `text`,
  `textMuted`, `border`, `selection`, `selectionText`, `hover`, `accent`, `success`, `warning`, `error`, with a static
  `current()` that falls back to `UIManager` for custom hosts (the `EditorPalette` pattern).
- `dev.jasper.sdk.ui.SyntaxPalette`: a `SyntaxRole` enum (section 2.2) and `SyntaxStyle(Color color, boolean bold,
  boolean italic)` per role.
- `Appearance` gains `workspaceVariant()`, `workspacePalette()`, `syntaxPalette()` and
  `onWorkspaceChanged(Consumer<Variant>)` (fires after any workspace theme change, including one that keeps the
  variant). `editorPalette()` now returns the **workspace's** editor palette. `variant()` and `onChanged` keep meaning
  the chrome. New members are defaults where a sensible fallback exists, so older custom hosts keep compiling.
- The testkit fake implements all of it; the contract suite covers it.

### 5.6 Editor plugin

`EditorTheme` maps `SyntaxPalette` roles onto RSyntaxTextArea token types (keyword → `RESERVED_WORD`/`RESERVED_WORD_2`,
type → `DATA_TYPE`, function → `FUNCTION`, constant → `LITERAL_BOOLEAN`, number → `LITERAL_NUMBER_*`, string →
`LITERAL_STRING_DOUBLE_QUOTE`/`LITERAL_CHAR`/`LITERAL_BACKQUOTE`, comment → `COMMENT_EOL`/`COMMENT_MULTILINE`,
doc_comment → `COMMENT_DOCUMENTATION`/`COMMENT_KEYWORD`/`COMMENT_MARKUP`, annotation → `ANNOTATION`, variable →
`VARIABLE`, operator → `OPERATOR`, punctuation → `SEPARATOR`, tag → `MARKUP_TAG_*`, attribute →
`MARKUP_TAG_ATTRIBUTE`/`MARKUP_TAG_ATTRIBUTE_VALUE`, regex → `REGEX`, error → `ERROR_*`, and `PREPROCESSOR` →
keyword). It no longer loads `idea.xml` / `dark.xml`. Surfaces come from `appearance.editorPalette()`, and it re-applies
on `onWorkspaceChanged`.

## 6. Converters, vendoring, importers, migration

### 6.1 The converter (`dev.jasper.app.themeimport`)

One Java converter, used by the runtime importer and by the vendoring task, so bundled and imported themes are produced
by the same code. It writes a standalone Jasper theme (no `extends`).

**IntelliJ → Jasper.**
- Resolve the `parentTheme` chain and named colours with today's `ThemeLoader` logic, which moves here. The classic,
  New UI, Islands and High Contrast themes and their editor schemes ship as importer-only resources under
  `dev/jasper/app/themeimport/intellij/`.
- `[colors]`: background, foreground and ANSI from the editor scheme's `CONSOLE_BACKGROUND_KEY`,
  `CONSOLE_NORMAL_OUTPUT` and `CONSOLE_*_OUTPUT` keys (reading parent schemes, as `EditorScheme` does today); accent
  from the first present of `ColorPalette.accent`, `Component.focusColor`, `EditorTabs.underlineColor`,
  `Button.default.startBackground`. A theme with no console colours takes Jasper Dark's or Jasper Light's ANSI set, and
  the import report says so.
- `[tokens]`: the inverse of the token → key mapping, for keys present in the resolved theme.
- `[syntax]`: from scheme attributes (`DEFAULT_KEYWORD`, `DEFAULT_STRING`, `DEFAULT_NUMBER`, `DEFAULT_LINE_COMMENT`,
  `DEFAULT_DOC_COMMENT`, `DEFAULT_CLASS_NAME`, `DEFAULT_FUNCTION_DECLARATION`, `DEFAULT_CONSTANT`, `DEFAULT_METADATA`,
  `DEFAULT_OPERATION_SIGN`, `DEFAULT_COMMA`, `HTML_TAG`, `HTML_ATTRIBUTE_NAME`, `REGEXP_*`, `ERRORS_ATTRIBUTES`, …),
  with bold and italic from the font type.
- `[ui]`: every other resolved key, so nothing is lost.

**Alacritty → Jasper.** `colors.primary.background`/`foreground` → background/foreground; `colors.cursor.cursor` →
cursor; `colors.selection.background` → selection; `colors.normal`/`colors.bright` copied; accent = normal blue moved to
at least 3:1 against the background. `dim` colours are dropped. TOML only (not Alacritty's old YAML).

### 6.2 Vendoring (build time, checked in)

- `tools/themes/fetch-themes.py` (replacing `fetch-intellij-themes.py`) fetches each upstream at a pinned commit into
  `third_party/themes/<source>/` with its licence, its NOTICE if any, and a `SOURCE.md` + `assets.tsv` (upstream path,
  SHA-256), following today's convention.
- `./gradlew :jasper-app:convertThemes` (JavaExec) writes the generated TOMLs into `dev/jasper/app/themes/` and
  regenerates `index.txt` and `THIRD-PARTY-THEMES.md`.
- A **drift test** re-runs the converter over `third_party/themes/` and fails if any checked-in TOML differs.
- A **uniqueness test** asserts one theme per name; a **provenance test** asserts every bundled theme except Jasper's
  own carries `author`, `license`, `source` and `commit`, and that its licence file exists under `third_party/themes/`.

The vendored set (licences verified against each repository's LICENSE file on 2026-09-30):

| Upstream | Licence | Jasper themes |
|---|---|---|
| intellij-community | Apache 2.0 | IntelliJ Light, Darcula |
| One Dark (one-dark/jetbrains-one-dark-theme) | ISC | One Dark, One Dark Vivid |
| Dracula (dracula/jetbrains) | MIT | Dracula, Alucard, Dracula Colorful |
| Solarized (snowe2010/solarized-jetbrains) | MIT | Solarized Dark, Solarized Light |
| Catppuccin (catppuccin/jetbrains) | MIT | Catppuccin Latte, Frappé, Macchiato, Mocha (taken from the release jar: the repo holds templates only) |
| Material Theme UI Lite (AtomMaterialUI/material-theme-ui-lite) | MIT | Every variant except Monokai Pro; variants named after other projects are renamed |
| VSCode Theme (dinbtechit/vscode-theme) | BSD-2 | renamed Code Dark, Code Dark Modern, Code Light |
| Nord (nordtheme/jetbrains) | MIT | Nord |
| Gruvbox (lonre/gruvbox-intellij-theme) | MIT | Gruvbox Dark and Light, each Hard / Medium / Soft |
| One Dark Modern (cweijan/idea-one-dark-modern-theme) | MIT | One Dark Modern |
| Xcode Theme (WhiteVermouth/XcodeTheme) | Apache 2.0 | renamed Studio Dark, Studio Light; terminal palettes written by hand as `extends` override files |
| alacritty-theme (pinned at `ab88d5a8`) | Apache 2.0 | the 177 themes, minus names an IntelliJ conversion already has |

Renames and dropped variants are recorded in each `SOURCE.md`. Excluded after the licence check: Material Theme UI and
Monokai Pro (proprietary), Gerry Themes (repository and Marketplace licences disagree), Gradianto, Atom One, GitHub
Theme and Trash Panda (no licence or no source).

### 6.3 Runtime importer

- Accepts an IntelliJ `.theme.json`, a plugin `.jar`/`.zip` (every `themeProvider` in `META-INF/plugin.xml` is
  converted) and an Alacritty `.toml`. Archive reading keeps `ThemeArchive`'s existing safety limits.
- Writes `<home>/themes/<id>.toml`. A name that a user theme already has asks *Replace* or *Keep both* (the new theme's
  name gets " 2").
- Shows a short report: what was imported, and any fallback (for example "no console colours: used Jasper Dark's").

### 6.4 First-start migration

- Old IntelliJ archives in `themes/` and Alacritty files in `terminal-themes/` are converted once into `themes/`. The
  originals move to `themes/legacy/` and are never deleted; a marker file (`themes/legacy/.migrated`) prevents a rerun.
- A file that fails to convert stays where it is, with a configuration warning.
- Config: `ui.theme.name` keeps resolving by name (converted themes keep their names). `terminal.colors` is read as
  `ui.theme.workspace`; its `light` / `dark` map to Jasper Light / Jasper Dark. `themes.marketplace.enabled` becomes
  an ignored legacy key with no warning.

### 6.5 Removed

- `dev.jasper.app.themestore` (IntelliJ Themes page, Marketplace client) and `themes.marketplace.enabled`.
- `dev.jasper.app.terminalthemes` page and catalog, and the `terminal-themes/` folder (`SampleTerminal` moves to the new
  page).
- Runtime IntelliJ JSON loading: `Theme.Resource`/`Theme.Archive`, `ThemeArchive`'s install role, `ThemeLoader`'s
  runtime role (both move to the importer), `EditorScheme`'s runtime role.
- The vendored `dev/jasper/app/terminalthemes/alacritty/` and `dev/jasper/app/themes/intellij/` resources move to
  `third_party/themes/` (sources) and `dev/jasper/app/themeimport/intellij/` (importer parents).

## 7. Settings › Themes

One page replaces the Themes group's IntelliJ Themes and Alacritty Themes pages.

- **UI theme** and **Workspace theme** pickers. The workspace picker's first entry is "Same as UI theme" (`match`).
  Each picker is a searchable list with All / Dark / Light and Bundled / Mine filters; each row shows the name and a
  swatch (background, accent, four ANSI colours).
- **Live preview** of the staged choice, drawn as a miniature window: a chrome strip (title bar with search field, a
  short sidebar list, status bar) and a workspace (pane tab strip, `SampleTerminal` with canned ANSI output, an editor
  snippet). The app cannot use the Editor plugin's RSyntaxTextArea, so the snippet is a fixed, pre-tokenised
  `JTextPane` coloured from the syntax roles.
- Choices are staged in the draft and apply on Apply or OK.
- **Import…** (file chooser for `.theme.json`, `.jar`, `.zip`, `.toml`), **Duplicate…** (asks a name, writes
  `extends = "<original>"` followed by every seed and token as a comment at its current value), **Open themes folder**,
  **Remove** (user themes only; moves to the system trash where supported, otherwise confirms first).
- The `themes/` folder is watched like `config.toml`; a saved theme re-applies within the usual debounce. Problems show
  on the page beside the theme, with the line.
- Settings search keywords: theme, colour scheme, palette, alacritty, intellij, marketplace, syntax, workspace.
- "About bundled themes…" opens `THIRD-PARTY-THEMES.md`.
- The Font & Colors page's Terminal colors dropdown is replaced by a link to this page. The View menu's theme items read
  the new catalog.

## 8. Plans

One specification, five plans, each leaving `./gradlew check` green and the app shippable:

1. **Format and generator.** TOML schema and errors, `extends`, the FlatLaf base-variable spike, the generator and
   contrast floors, native Jasper Dark and Jasper Light, chrome install through the generator, IntelliJ Light and
   Darcula converted (the IntelliJ half of the converter lands here, enough to convert them), runtime IntelliJ loading
   removed.
2. **Workspace zone.** `ui.theme.workspace` with the `terminal.colors` alias, `ResolvedTheme` with two generated
   themes, the Alacritty converter (in memory, for `terminal-themes/` names), host-painted workspace surfaces,
   zone-aware icons, SDK 0.12.0 (`WorkspacePalette`, `SyntaxPalette`,
   `Appearance` additions, testkit), the Editor plugin's syntax mapping. Fixes the reported mismatch.
3. **Plugin pane restyler.** The coverage spike, `WorkspaceStyler`, the bundled plugins' renderer audit, plugin
   authoring notes.
4. **Converters, vendoring, migration.** The rest of the IntelliJ converter (syntax and full `[ui]`), `fetch-themes.py`,
   `convertThemes`, the vendored set with provenance, drift / uniqueness / provenance tests, the runtime importer,
   first-start migration, removal of both theme stores and their pages.
5. **Themes page.** Pickers, preview, Import…, Duplicate…, Open folder, Remove, live reload of `themes/`.

Plans 1–3 keep today's IntelliJ Themes and Alacritty Themes pages working: from plan 1, `ThemeCatalog` converts the
IntelliJ archives in `themes/` in memory with the converter at scan time, and `terminal.colors` keeps reading
`terminal-themes/` as terminal palettes until plan 2 makes it the `ui.theme.workspace` alias (an Alacritty name then
resolves through the in-memory Alacritty conversion, which lands in plan 2). Plan 4 replaces the in-memory conversions
with migration and removes both pages; plan 5 adds their replacement. Between plans 4 and 5, themes are chosen
through `config.toml` and the existing theme dropdown.

## 9. Testing

All headless, inside `./gradlew check`:
- **Format:** parsing, every error in 2.3 with file and line, `extends` merging, cycles and missing parents, `dark`
  inference.
- **Generator:** each token and syntax role's derivation, contrast floors, explicit values untouched, FlatLaf
  base-variable re-derivation (spike), Jasper Dark matching today's chrome keys.
- **Converter:** golden tests on small IntelliJ (theme + scheme with parents) and Alacritty fixtures; drift,
  uniqueness and provenance tests over the bundled set.
- **Workspace:** tab strip, dividers, find bar and terminal palette follow the workspace theme under a mismatched
  chrome; `match` leaves them on the chrome; zone-aware icons.
- **Restyler:** per-type coverage (spike), plugin styles winning, re-theming replacing only the styler's part,
  components added later, removal restoring styles, the exempt flag, no-op under `match`.
- **SDK:** contract tests for `WorkspacePalette`, `SyntaxPalette`, `workspaceVariant` and `onWorkspaceChanged`;
  the testkit fake.
- **Editor:** role → token-type mapping; re-application on a workspace change.
- **Migration:** a temporary home with old archives and Alacritty files; moves to `legacy/`; the marker; config
  aliases; failures leaving originals in place.
- **Page:** pickers and filters, staging, Duplicate's output, import name clashes, Remove on user themes only.

GUI checks for the user (recorded in `docs/STATUS.md`): IntelliJ Light UI with a dark workspace (the original
screenshot); Jasper Light and Jasper Dark across chrome and workspace; a plugin pane (SFTP, File Server) under a
mismatched workspace; editor syntax under a light and a dark workspace; several vendored themes (Dracula, Catppuccin
Mocha, Solarized Light, Gruvbox, Code Dark); importing an IntelliJ jar and an Alacritty file; editing a duplicated
theme and seeing it reload.

## Out of scope

- A visual theme editor (a seed-colour editor is a candidate later plan).
- Network theme sources of any kind.
- Per-window or per-pane themes.
- Theming popups, menus and dialogs opened from workspace panes with the workspace theme, except a workspace combo box's own dropdown (§5.3).
- Icon recolouring beyond choosing light or dark artwork.
