# Jasper theme import

Status: **Draft for the user's review, 2026-10-13.**
Branch: `claude/theme-import`, from `main` at `38557864`.

This amends the [Slate Steel specification](2026-10-12-jasper-slate-steel-themes-design.md): its decision 2 (Slate
Steel only, no themes folder, no import) and decision 4 (a read-only Theme dropdown) are superseded. Slate Steel stays
the only built-in theme, and everything else in that document stands, including the token rule and decision 3 (a
terminal scheme recolours only the terminal text area).

## 1. Intent and decisions

Users can bring the themes they already like. A Python tool converts **Alacritty** themes (terminal colours only) and
**IntelliJ** themes (the whole UI, its terminal and its editor) into **Jasper theme files**, and Settings imports Jasper
theme files. Imported terminal schemes appear in the Terminal scheme dropdown; imported UI themes recolour the whole
application.

The user's decisions (2026-10-13):

1. **The app reads only Jasper's own format.** All Alacritty and IntelliJ parsing lives in the Python tool; the app has
   no converter.
2. **Separate Dark and Light theme picks.** `ui.theme.dark` and `ui.theme.light` choose the theme for each appearance;
   the Dark / Light / Match System tiles choose which applies. A dark-only theme is offered only for Dark.
3. **Imported UI themes recolour the Editor plugin** with the syntax colours of their IntelliJ editor scheme.
4. **Settings has Import… and Remove.** Imported files live in `<home>/themes/`.
5. **One public format and one loader** (approach A): the Slate Steel loader becomes the general loader and
   `slate-steel.json` moves onto the public format.
6. **A faithful converter** (approach 1): IntelliJ themes are resolved through their full parent chain and mapped key by
   key, with derivation rules only where no key exists; seed-colour generation was rejected because it loses a theme's
   character.

Jasper is an MVP: settings and APIs change outright, with no migration, aliases or deprecation shims.

## 2. The Jasper theme format

A Jasper theme is one UTF-8 JSON file.

```json
{
  "jasper-theme": 1,
  "name": "Dracula",
  "author": "Dracula Theme",
  "ui": {
    "dark": { "surface.app": "#21222C", "surface.panel": "#282A36", "...": "every role" }
  },
  "terminal": {
    "dark": { "name": "Dracula", "bg": "#282A36", "fg": "#F8F8F2", "...": "the 21 colours" }
  },
  "editor": {
    "dark": { "background": "terminal.bg", "keyword": "#FF79C6, bold", "comment": "#6272A4, italic", "...": "..." }
  }
}
```

### 2.1 Top level

| Key | Rule |
|---|---|
| `jasper-theme` | Required, the number `1`. A file without it is "not a Jasper theme". |
| `name` | Required, a non-empty string. The display name. |
| `author` | Optional string. |
| `ui` | Optional object with `dark`, `light` or both, and nothing else. |
| `terminal` | Required object with at least one scheme. |
| `editor` | Required when `ui` is present, with exactly the keys `ui` has; absent otherwise. |

Any other key fails loading.

### 2.2 UI variants

Each `ui.dark` / `ui.light` object lists every Jasper role exactly as `slate-steel.json` does today: every role in
`SemanticColor` except the ruled ones, as `#RRGGBB` or `#RRGGBBAA`. The fixed rules still apply: `border.focus` is
`accent`, `surface.status` is `surface.app`, `border.identity` is `border.control`, and each `hue.<name>.surface` is its
hue. A missing role, an unknown key or a malformed colour fails loading.

### 2.3 Terminal schemes

`terminal` maps scheme keys to schemes. A key matches `[a-z0-9]+(-[a-z0-9]+)*` and has at most 32 characters. Each scheme has a `name` (required,
non-empty) and exactly the 21 colour keys used today: `bg`, `fg`, `fg.bold`, `cursor`, `selection`, the eight ANSI
names and their eight `bright.` forms.

**A UI variant's matching scheme is the scheme with the same key.** `ui.dark` requires `terminal.dark` and
`ui.light` requires `terminal.light`; "Same as UI theme" uses that scheme. Other keys are extra schemes, such as Slate
Steel's `high-contrast`.

### 2.4 Editor schemes

`editor.dark` and `editor.light` have exactly the entries used today: the surfaces `background`, `foreground`,
`caret`, `selection`, `currentLine`, `gutter`, `lineNumbers`, and one entry for every syntax role. A value is

```
<colour>[ @ <N>%][, italic][, bold]
```

where `<colour>` is either a reference, as today (`terminal.bg`, `terminal.ansi.green`, `terminal.ansi.bright.black`,
or a role such as `text.secondary`), or a literal `#RRGGBB`. Terminal references resolve against the variant's
matching scheme. A share lays the colour over the editor background (`hover.overlay @ 5%`), as today. Surfaces take no
style; `background` takes no share.

### 2.5 Terminal-only themes

A file without `ui` (and so without `editor`) is a terminal-only theme: its schemes appear in the Terminal scheme
dropdown and it offers no UI variant. Alacritty conversions produce these.

### 2.6 Ids

- A **theme id** is the file name without `.json`, and must match `[a-z0-9]+(-[a-z0-9]+)*` with at most 64 characters.
  A file in the themes folder whose name doesn't match is reported and skipped.
- The built-in theme's id is `slate-steel`. It is reserved: a `slate-steel.json` in the themes folder is reported and
  skipped, and Import refuses a theme whose id would be `slate-steel`.
- A **scheme id** is `<theme-id>/<scheme-key>`, for example `slate-steel/high-contrast` or `gruvbox-dark/dark`.
- Import names the file from the theme's `name`: Unicode-decomposed, accents dropped, lower-cased, every run of
  characters other than ASCII letters and digits replaced by `-`, leading and trailing `-` removed, then cut to 64
  characters (and any `-` left at the end removed). A name that leaves nothing ("Ночь") is refused with "The theme's name has no letters or digits Jasper can use for a file name".

### 2.7 The bundled theme

`slate-steel.json` moves onto this format: `"jasper-theme": 1`, `"name": "Slate Steel"`, `"author": "Jasper"`; the
`ui` and `editor` keys become `dark` and `light`; the terminal keys become `dark` ("Slate Steel Dark"), `light`
("Slate Steel Light") and `high-contrast` ("High Contrast"). Every colour value is unchanged. The machine-readable
block in [the colour reference](../../design/theme-engine/slate-cobalt.md) changes the same way.

The token rule is unchanged: hex colours live only in `slate-steel.json` among the app's code and resources.
Imported files live in `<home>/themes/`, outside the scan.

## 3. The app runtime

### 3.1 The loader (`jasper-ui`, `dev.jasper.ui.theme`)

`SlateSteel` is renamed `JasperTheme` and its parser becomes the general loader of the format in section 2:
`JasperTheme.parse(json, id)` returns an
immutable theme with its id, name, author, its UI variants (each with its semantic palette, editor colours, syntax
styles and complete `GeneratedTheme`, built exactly as Slate Steel's are today) and its terminal schemes (id, name,
colours). The built-in theme is `JasperTheme.bundled()`. The `TerminalScheme` enum is replaced by scheme ids. Errors stay
`ThemeFileException`s naming the file and the entry, for example
`dracula.json: ui.dark: Missing colour "accent"`.

A `GeneratedTheme` carries the theme's name and author (an empty author when the file has none) and the workspace
glass stays opaque for every theme.

### 3.2 The library (`jasper-app`, `dev.jasper.app.appearance`)

A `ThemeLibrary` holds the built-in Slate Steel and every valid `<home>/themes/*.json` (`<home>` is the application
home, where `config.toml` lives). It is read:

- at start-up, before the configuration is resolved;
- on Reload config;
- after an Import or a Remove.

There is no folder watcher. A file that fails to load is skipped and reported through the same channel as
configuration problems, with the loader's message. The library offers the themes that have a Dark variant, the themes
that have a Light variant, every scheme, and lookups by id. Ordering is Slate Steel first, then the imported entries
by display name (case-insensitive), then by id. When two entries share a display name, each shows its id in
parentheses.

The application owns one library; every window shares it.

### 3.3 Settings

| Key | Values | Default |
|---|---|---|
| `ui.appearance` | `system`, `dark`, `light` (unchanged) | `system` |
| `ui.theme.dark` | the id of a theme with a Dark variant | `slate-steel` |
| `ui.theme.light` | the id of a theme with a Light variant | `slate-steel` |
| `terminal.scheme` | `match`, or a scheme id | `match` |

The theme and scheme settings are validated against the library whenever the configuration is read. An unknown id, or
a theme without the needed variant, is reported in the existing form ("Use …; using the default.") and the default is
used. `terminal.scheme`'s old values (`slate-steel-dark`, `slate-steel-light`, `high-contrast`) become
`slate-steel/dark`, `slate-steel/light` and `slate-steel/high-contrast`; the old values are simply unknown.

With `match`, the terminal uses the shown theme's matching scheme: Dracula's `dark` scheme while Dracula is shown.

### 3.4 The controller

`ThemeController`'s selection becomes the Dark theme id, the Light theme id, the appearance in effect (Dark or Light)
and the scheme choice. `ResolvedTheme` is built from the library: the shown variant's `GeneratedTheme` for chrome and
workspace, and the chosen scheme's palette for the terminal panes. Match System switches between the two picked themes
live, as it switches between Slate Steel's variants today. A library refresh re-validates the settings and re-applies
the theme when the resolved values changed, so replacing an in-use theme with Import recolours the app at once.

Installation failure keeps today's policy: at start-up Jasper falls back to Slate Steel Dark; a later failure keeps the
current theme and Reload config retries.

## 4. Settings and the command palette

### 4.1 The Appearance page

```
Appearance
  [ Dark ] [ Light ] [ Match System ]

  Dark theme       [ Dracula            v ]
  Light theme      [ Slate Steel        v ]
  Terminal scheme  [ Same as UI theme   v ]

Imported themes                                  [ Import… ]
  Dracula          UI theme · Dark                [ Remove ]
  Gruvbox Dark     Terminal scheme                [ Remove ]
```

- The tiles are unchanged; each tile's preview follows the staged pick for its appearance.
- **Dark theme** and **Light theme** replace the read-only Theme row. Each lists the library's themes with that variant.
- **Terminal scheme** lists "Same as UI theme", then Slate Steel's three schemes, then every imported scheme,
  including the schemes inside imported UI themes.
- These choices stay staged until Apply or OK, as today.
- The **Imported themes** group lists each imported theme with what it offers: "UI theme · Dark", "UI theme · Light",
  "UI theme · Dark and Light", or "Terminal scheme" ("Terminal schemes" when it has more than one). A UI theme with
  extra schemes says so ("UI theme · Dark, 2 terminal schemes"). With no imported themes the group shows "No imported
  themes" beside Import….
- Terminal › Font & Colors still links to this page and the palette's **Themes…** still opens it.

### 4.2 Import…

Import is a file operation that takes effect at once; it selects nothing.

1. **Import…** opens Jasper's file chooser for `*.json`.
2. The file is read and validated off the EDT; files over 1 MiB are refused ("Theme files are at most 1 MiB").
3. A file that fails shows the loader's message under the button, for example "Could not import dracula.json:
   ui.dark: Missing colour \"accent\"".
4. If the id derived from its name is `slate-steel`, Import refuses it ("Slate Steel is built in and can't be
   replaced").
5. If an imported theme with that id exists, Import asks "Replace Dracula?" (Replace / Cancel).
6. The original bytes are written to `<home>/themes/<id>.json` (the folder is created if needed) through a temporary
   file in the same folder and a move, so a failed write leaves the old file in place.
7. The library refreshes; the new entries appear in the dropdowns and the group.

### 4.3 Remove

Remove asks for confirmation: "Remove Dracula?". When the theme is in use, saved or staged, the dialog adds what will
change, for example "It is your Dark theme; Slate Steel will be used instead." On Remove:

1. every setting that names the theme or one of its schemes, saved or staged, returns to its default (a saved one is
   written at once, removing the key; other staged changes stay staged);
2. the file is deleted;
3. the library refreshes.

A failed delete is shown under the row and changes nothing.

### 4.4 The command palette

The "Terminal: …" commands are built from the library: "Terminal: Same as UI theme" and one command per scheme,
rebuilt whenever the library changes. Their ids are `view.terminal_scheme.match` and
`view.terminal_scheme.<theme-id>.<scheme-key>`, for example `view.terminal_scheme.slate-steel.high-contrast`; the id
limits in section 2 keep these within the 128 characters a command id allows.

### 4.5 Plugins

Plugins receive the same roles and editor palettes through the SDK; the SDK does not change.

## 5. The converter

### 5.1 The tool

`tools/themes/jasper_theme.py`: one file, Python 3.11 or later (for `tomllib`), standard library only.

```bash
python3 tools/themes/jasper_theme.py Dracula.jar gruvbox_dark.toml -o ~/Desktop/themes
```

| Option | Meaning |
|---|---|
| `INPUT…` | One or more files. `.toml` is Alacritty; `.jar` and `.zip` are IntelliJ plugins; `.json` is an IntelliJ `.theme.json`. |
| `-o DIR`, `--output DIR` | Where to write; the current directory by default; created if missing. |
| `--force` | Overwrite existing output files; without it an existing file is an error. |
| `--list` | List the themes in each input (name, IntelliJ id, Dark or Light) and write nothing. |
| `--theme NAME` | Convert only the named theme of a plugin (by name or IntelliJ id); repeatable. |
| `--name NAME` | The display name; only when exactly one theme is converted. |
| `--scheme FILE` | The editor scheme for a loose `.theme.json` (see 5.3). |
| `--verbose` | Print where every role and colour came from. |

Each theme is written as `<slug>.json`, the slug as in section 2.6. Each conversion prints one summary line, for
example `Dracula (dark): 41 roles from the theme, 12 from Darcula, 4 derived; editor 22 of 30 from the scheme`.
Warnings (an unknown parent, a missing editor scheme, an ignored Alacritty `import`) are printed with the file name.
Exit status: `0` success, `1` a conversion failed (the others are still written), `2` usage error.

Before writing, the tool checks its output against the format rules of section 2 (every key present, nothing unknown,
every colour well formed), so it never writes a file that Import rejects. Output is deterministic: keys in the format's
order, two-space indentation, a final newline.

### 5.2 Reading IntelliJ plugins

Ported from the removed Java importer (`ThemeArchive`, `c1e968ee^`):

- Archives are read in memory and never extracted. Limits: an archive of at most 64 MiB, at most 10,000 entries, a
  read file of at most 4 MiB, a nested jar of at most 32 MiB, at most 16 nested jars, at most 64 themes, and at most
  256 MiB inflated in total.
- A Marketplace `.zip` holds the plugin as `lib/*.jar`; those jars are read (one level of nesting).
- Themes are found through `META-INF/plugin.xml`'s `themeProvider` entries (`path`, `id`). Without a `plugin.xml`, or
  without `themeProvider` entries, every `*.theme.json` in the archive is a theme.
- XML (`plugin.xml`, editor schemes) containing `<!DOCTYPE` or `<!ENTITY` is refused.

### 5.3 Resolving an IntelliJ theme

1. **Variant.** `"dark": true|false`, inherited from the parent when absent.
2. **Parents.** `parentTheme` names a theme id, looked up in the same plugin and then among IntelliJ's own themes,
   vendored under `tools/themes/intellij/`: `Darcula`, `IntelliJ`, `JetBrainsLightTheme`, `ExperimentalDark`,
   `ExperimentalLight`, `ExperimentalLightWithLightHeader`, `Islands Dark`, `Islands Light`, `Islands Darcula`,
   `JetBrainsHighContrastTheme`. Without `parentTheme` the base is `Darcula` (dark) or `IntelliJ` (light). An unknown
   parent is a warning, and the variant's base is used.
3. **The effective `ui` map.** Parent first, child on top. Nested objects flatten to dotted keys
   (`"Button": {"startBackground": …}` is `Button.startBackground`). `colors` names resolve, through the chain. A
   `*.suffix` wildcard supplies `<anything>.suffix` when no exact key does. Non-colour values (numbers, insets, fonts)
   are ignored.
4. **Colours.** `#RGB`, `#RRGGBB`, `#RRGGBBAA` and the same without `#`. A colour with alpha is laid over the
   variant's `surface.panel`, except in the roles that carry opacity (`surface.scrim`, `border.subtle`,
   `text.onAccent.secondary`); `surface.panel` and `surface.app` themselves are laid over black (Dark) or white
   (Light).
5. **The editor scheme.** `editorScheme` names a path in the plugin. For a loose `.theme.json`, `--scheme` gives the
   file, otherwise the file with that path's base name next to the theme is used if it exists. `parent_scheme`
   follows the scheme chain through the vendored IntelliJ schemes (`Darcula`, `Default` and the New UI schemes).
   Without an editor scheme the variant's vendored base scheme is used, with a warning.

The vendored files are IntelliJ Community's, Apache License 2.0, restored byte for byte from `c1e968ee^`
(`jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/`) with their `LICENSE.txt`, `NOTICE.txt`,
`SOURCE.md` and `assets.tsv`; nothing is downloaded.

### 5.4 Mapping an IntelliJ theme

The terminal scheme is resolved first, so rules can use its ANSI colours. For each role the tool then takes the first
IntelliJ key present in the effective map, else the derivation rule. Rules may name other roles; the tool resolves
them in dependency order, and the table has no cycles. The table is
the starting point: Plan 3 confirms each key against the vendored base themes and the fixtures, drops keys that no
base theme or fixture uses, and the golden files pin the result. `mix(a, b, N%)` moves `a` N% toward `b`.

| Role | IntelliJ keys, in order | Otherwise |
|---|---|---|
| `surface.app` | `StatusBar.background`, `MainToolbar.background`, `Panel.background` | |
| `surface.panel` | `ToolWindow.background`, `Panel.background` | |
| `surface.panel.raised` | `ToolWindow.Header.background` | `mix(surface.panel, text.primary, 4%)` |
| `surface.dialog` | `Panel.background` | `surface.panel` |
| `surface.card` | `Panel.background` | `surface.panel` |
| `surface.sidebar` | `SidePanel.background`, `ToolWindow.background` | `surface.panel` |
| `surface.input` | `TextField.background`, `TextArea.background` | |
| `surface.field.raised` | `ComboBox.nonEditableBackground`, `ComboBox.background` | `surface.input` |
| `surface.control` | `Button.startBackground`, `Button.background` | |
| `surface.control.hover` | `ActionButton.hoverBackground` | `mix(surface.control, text.primary, 8%)` |
| `surface.control.selected` | `ActionButton.pressedBackground` | `mix(surface.control, text.primary, 14%)` |
| `surface.menu` | `PopupMenu.background`, `Popup.background`, `Menu.background` | |
| `surface.terminal` | | the matching scheme's `bg` |
| `surface.tabstrip` | `EditorTabs.background` | `surface.app` |
| `surface.emptyTile` | | `mix(surface.panel, text.primary, 8%)` |
| `surface.scrim` | | `#000000` at 55% (dark), `text.primary` at 25% (light) |
| `border.window` | `Borders.ContrastBorderColor`, `Borders.color` | |
| `border.subtle` | | `hover.overlay` at 6% |
| `border.input` | `TextField.borderColor`, `Component.borderColor` | |
| `border.field.raised` | `ComboBox.borderColor`, `Component.borderColor` | |
| `border.field.raised.hover` | `Component.hoverBorderColor` | `mix(border.field.raised, text.primary, 15%)` |
| `border.control` | `Button.startBorderColor`, `Button.borderColor`, `Component.borderColor` | |
| `border.menu` | `Popup.borderColor`, `Borders.color` | |
| `border.error` | `Component.errorFocusColor` | `status.danger` |
| `text.primary` | `Label.foreground`, `*.foreground` | |
| `text.secondary` | `Label.infoForeground`, `Component.infoForeground` | `mix(text.primary, surface.panel, 35%)` |
| `text.strong.secondary` | | `mix(text.secondary, text.primary, 50%)` |
| `text.tertiary` | `Label.disabledForeground` | `mix(text.secondary, surface.panel, 30%)` |
| `text.placeholder` | `TextField.placeholderForeground`, `TextField.inactiveForeground` | `mix(text.tertiary, surface.input, 25%)` |
| `text.field.raised` | `ComboBox.foreground` | `mix(text.primary, text.secondary, 30%)` |
| `text.onAccent` | `Button.default.foreground` | white or black, whichever contrasts more with `accent` |
| `text.onAccent.secondary` | | `text.onAccent` at 65% (dark), 75% (light) |
| `text.link` | `Link.activeForeground` | `accent` |
| `text.link.hover` | `Link.hoverForeground` | `mix(text.link, text.primary, 25%)` |
| `text.error` | `Label.errorForeground` | `status.danger` |
| `text.success` | `Label.successForeground` | `status.success` |
| `accent` | `Button.default.startBackground`, `Button.default.background`, `Component.focusColor` | |
| `accent.hover` | | `mix(accent, text.primary, 10%)` |
| `selection.bg` | `List.selectionBackground`, `Tree.selectionBackground`, `Table.selectionBackground` | |
| `hover.overlay` | | `#FFFFFF` (dark), `#000000` (light) |
| `segment.track` | | `surface.input` |
| `segment.selected.bg` | `SegmentedButton.selectedButtonColor` | `border.control` (dark), `surface.control` (light) |
| `toggle.track.off` | `ToggleButton.offBackground` | `border.control` |
| `toggle.knob` | `ToggleButton.buttonColor` | `#FFFFFF` |
| `button.secondary.bg` | `Button.startBackground`, `Button.background` | `surface.control` |
| `button.secondary.bg.hover` | | `surface.control.hover` |
| `button.secondary.border` | `Button.startBorderColor`, `Button.borderColor` | `border.control` |
| `status.success` | `ProgressBar.passedColor` | ANSI green |
| `status.warning` | `Component.warningFocusColor` | ANSI yellow |
| `status.danger` | `ProgressBar.failedColor`, `Component.errorFocusColor` | ANSI red |
| `status.idle` | | `mix(text.tertiary, surface.panel, 40%)` |
| `hue.blue`, `hue.green`, `hue.amber`, `hue.purple` | | ANSI blue, green, yellow, magenta, each `mix(…, text.primary, 30%)` |
| `hue.neutral` | | `text.strong.secondary` |

A role with no key and no rule cannot occur: every row ends in a rule or in a key every vendored base theme defines,
and the tool fails with "internal: no value for <role>" if one is ever missing, so the gap is fixed in the table.

**The terminal scheme** (`terminal.<variant>`, named after the theme), from the editor scheme, ported from the removed
`EditorScheme`:

- `bg`: colour `CONSOLE_BACKGROUND_KEY`, else the `TEXT` attribute's background;
- `fg`: `CONSOLE_NORMAL_OUTPUT`'s foreground, else `TEXT`'s foreground; `fg.bold` is `fg`;
- `cursor`: colour `CARET_COLOR`; `selection`: colour `SELECTION_BACKGROUND`;
- the 16 ANSI colours: the foregrounds of `CONSOLE_BLACK_OUTPUT`, `…RED…`, `…GREEN…`, `…YELLOW…`, `…BLUE…`,
  `…MAGENTA…`, `…CYAN…`, `CONSOLE_GRAY_OUTPUT` (white), `CONSOLE_DARKGRAY_OUTPUT` (bright black), the `…_BRIGHT_OUTPUT`
  forms, and `CONSOLE_WHITE_OUTPUT` (bright white);
- anything still missing comes from the parent scheme chain, which ends in a vendored scheme that defines all of them.

**The editor scheme** (`editor.<variant>`):

- `background` and `foreground`: the `TEXT` attribute; `caret`: `CARET_COLOR`; `selection`: `SELECTION_BACKGROUND`;
  `currentLine`: `CARET_ROW_COLOR`; `gutter`: `GUTTER_BACKGROUND`, else the background; `lineNumbers`:
  `LINE_NUMBERS_COLOR`.
- Syntax roles take the first attribute with a foreground: `keyword` `DEFAULT_KEYWORD`; `type` `DEFAULT_CLASS_NAME`;
  `function` `DEFAULT_FUNCTION_DECLARATION`, `DEFAULT_FUNCTION_CALL`; `variable` `DEFAULT_IDENTIFIER`; `constant`
  `DEFAULT_CONSTANT`; `number` `DEFAULT_NUMBER`; `string` `DEFAULT_STRING`; `comment` `DEFAULT_LINE_COMMENT`,
  `DEFAULT_BLOCK_COMMENT`; `doc_comment` `DEFAULT_DOC_COMMENT`; `annotation` `DEFAULT_METADATA`; `operator`
  `DEFAULT_OPERATION_SIGN`; `punctuation` `DEFAULT_COMMA`, `DEFAULT_SEMICOLON`; `tag` `HTML_TAG`, `XML_TAG`;
  `attribute` `HTML_ATTRIBUTE_NAME`, `XML_ATTRIBUTE_NAME`; `regex` `REGEXP_CHAR_CLASS`, `REGEXP_CHARACTER`, then any
  `REGEXP_*`; `error` `ERRORS_ATTRIBUTES`. `FONT_TYPE` 1 is bold, 2 italic, 3 both.
- A role with no attribute in the chain uses Slate Steel's reference for it (`keyword` is `terminal.ansi.magenta`, and
  so on), which resolves against the theme's own terminal scheme.

### 5.5 Mapping an Alacritty theme

- `colors.primary.background` and `foreground` give `bg` and `fg`; `bright_foreground` gives `fg.bold`, else `fg`.
- `colors.cursor.cursor` gives `cursor`, else `fg`; `colors.selection.background` gives `selection`, else
  `mix(bg, fg, 25%)`. `CellForeground` and `CellBackground` resolve to `fg` and `bg`.
- `colors.normal.*` and `colors.bright.*` give the 16 ANSI colours; a missing bright colour uses its normal one; a
  missing normal colour fails the conversion.
- Colours are `#rrggbb` or `0xrrggbb`. Other tables (`dim`, `indexed_colors`, `search`, `hints`, `footer_bar`) and the
  cursor and selection `text` colours have no Jasper counterpart and are ignored. An `import` is ignored with a warning,
  since the imported file's colours are missing.
- The name is the file name in title case, `_` and `-` as spaces (`gruvbox_dark.toml` is "Gruvbox Dark"), or `--name`.
  The one scheme is named the same and keyed `dark` when its background is darker than its foreground (relative
  luminance), else `light`.

## 6. Testing

### 6.1 The converter

`tools/themes/test_jasper_theme.py`, standard `unittest`, with fixtures under `tools/themes/testdata/`:

- Alacritty: `0x` and `#` colours, `CellForeground`/`CellBackground`, missing brights, a missing normal colour, an
  `import`, dark and light keying, naming.
- IntelliJ: a theme on a Darcula parent with `colors` names, wildcards and nested objects; a light theme on `IntelliJ`;
  an unknown parent; alpha colours; an `.icls` with a parent scheme, console colours and `FONT_TYPE`s; a loose
  `.theme.json` with and without `--scheme`; a plugin jar and a Marketplace zip built in the test, holding two themes,
  with `--list` and `--theme`.
- Refusals: DOCTYPE and ENTITY XML, each archive limit, overwriting without `--force`, `--name` with two themes, an
  unknown input extension.
- Golden files: the expected output for each fixture is committed under `tools/themes/testdata/expected/` and compared
  byte for byte.

A root task `checkThemeTool`, part of `./gradlew check`, runs the suite. It finds Python 3.11 or later (`python3`,
then `python`) and fails with "The theme tool's tests need Python 3.11 or later on the PATH" when there is none. CI
installs Python with `actions/setup-python` on all three operating systems.

### 6.2 The contract

A `jasper-ui` test loads every file in `tools/themes/testdata/expected/` with the Java loader and checks it has the
variants and schemes its fixture should produce. If the converter and the app disagree about the format, `check` fails.

### 6.3 The app (headless)

- Loader: every rule in section 2 (missing and unknown keys at each level, the marker, variant pairing, editor
  literals, references, shares and styles, terminal-only files, scheme keys, the name rules).
- Slate Steel: the existing palette, terminal, editor and generated-theme tests pass on the migrated file; the colour
  reference's block and the file agree.
- Library: folder scan, a missing folder, bad and badly named files skipped and reported, the reserved id, ordering and
  duplicate names.
- Settings: defaults, unknown ids, a theme without the needed variant, old scheme values reported.
- Controller: Dark and Light picks, Match System switching between two different themes, `match` using the shown
  theme's scheme, a library refresh re-applying a replaced theme.
- Appearance page: dropdown contents and staging, tile previews following the picks, the Imported themes group's
  descriptions, Import through an injected chooser (a good file, a broken file, an oversized file, Replace, the
  reserved id, a failed write keeping the old file), Remove (confirmation text, in-use saved and staged settings reset,
  other staged changes kept, a failed delete).
- Palette: the "Terminal: …" commands follow the library.
- Guards: new classes avoid the names `RemovedThemeLayerTest` lists, `docs/design/theme-engine/migration.csv` gains a
  row for every new matching source file, and `ColourLiteralScanTest` still passes.

### 6.4 For the user (GUI)

No GUI is launched by the agent. The user checks: Import… with the real file chooser; a converted Dracula and Nord (or
any favourites) across the app; Match System switching between an imported Dark theme and Slate Steel Light; an
Alacritty scheme on Slate Steel; Remove of the theme in use.

## 7. Documentation

- `docs/themes.md` is rewritten: choosing themes and schemes, the format (section 2 in reference form), converting with
  the tool, Import and Remove, and what goes wrong.
- `config.example.toml` and the setting descriptions in `CoreSettings` cover `ui.theme.dark`, `ui.theme.light` and the
  new `terminal.scheme` values.
- `tools/themes/intellij/SOURCE.md` (restored and updated) records the vendored files.
- `docs/STATUS.md` gets an entry per plan.

## 8. Plans

1. **Format and runtime.** The general loader, Slate Steel on the format, the library reading `<home>/themes/`, the new
   settings, the controller and the palette commands. Themes copied in by hand work.
2. **Settings.** The Dark theme and Light theme dropdowns, the dynamic Terminal scheme list, the Imported themes group,
   Import… and Remove.
3. **The converter.** The vendored IntelliJ files, the tool, its tests and golden files, the contract test, the Gradle
   task and CI.

Plan 3 depends only on Plan 1's format, so it can run alongside Plan 2.

## 9. Out of scope

- Converting inside the app, or importing Alacritty or IntelliJ files directly.
- Alacritty's YAML format (dropped by Alacritty 0.13).
- IntelliJ icon palettes, fonts, metrics (arcs, insets, sizes) and anything else that is not a colour.
- Watching the themes folder, exporting themes, editing themes in Settings, downloading themes.
- Per-theme workspace glass.
