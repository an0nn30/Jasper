# Themes

Jasper's themes are Jasper theme files: TOML that colours the app, workspace, terminal palette and editor syntax.
Jasper ships **five themes**: Jasper Light, Jasper Dark, Nord, Clear Dark and Clear Light.
Clear Dark/Light are ported from Terminal.app. Nord's pinned source and license are in
[third-party theme notices](../THIRD-PARTY-THEMES.md). Additional themes can be imported from local files.

Saved selections for removed built-ins remain in the configuration and show as "(not found)" in Settings.
Jasper uses its existing safe fallback until you select an available theme or import the missing one.

## Choosing a theme

> Slate Steel is Jasper's only theme. The theme files, imports and workspace themes described after this section are
> no longer loaded; Slate Steel Plan 3 rewrites this page.

Choose the appearance and the terminal scheme in `config.toml`:

```toml
[ui]
appearance = "system" # or "dark", "light"

[terminal]
scheme = "match" # or "slate-steel-dark", "slate-steel-light", "high-contrast"
```

`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system; `terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes. Changes apply to every window at once, keeping shells,
scrollback and splits. Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, the
Theme row (Slate Steel) and the **Terminal scheme** dropdown, whose first entry, **Same as UI theme**, is `match`.
Terminal › Font & Colors links to them. Only the terminal text area follows the scheme; tabs, the tab strip, find
controls, menus, sidebars, dialogs and the editor follow the appearance. Standard plugin controls and their dropdowns are kit-owned and resolve the mounted
chrome/workspace route. Moving a view updates its route while preserving document edits, selection and undo.
Remote SFTP/Tunnels, File Server and Vault rail panels remain chrome. Vim errors and bell feedback follow the
mounted Editor route. Specialized document content consumes explicit palettes; the native host publishes a
shallow route marker and does not recursively restyle plugin descendants. Themes supply colors while the kit
owns geometry, typography, delegates and popup presentation. Raw ordinary Swing controls and plugin-authored
style overrides are unsupported in SDK 0.15; see [plugin authoring](plugin-authoring.md#supported-custom-content).

User visual acceptance: try light chrome/dark workspace and the reverse, then two themes of the same darkness;
inspect controls, selections, combo dropdowns, rail panels and popups; retain an edited document while moving,
detaching, reopening and closing its pane; check Vim error/bell feedback and banners; return to `match`. Repeat with
GitHub Dark or another complete imported theme. These checks require the native GUI and are not claimed by headless
test results.

## Settings and importing

Both dropdowns list every installed theme by name and update when theme files are added, imported, edited or removed.
Start typing in an open (or focused) dropdown to search: a search bar appears at the top and the list keeps the names
holding every typed word, ignoring case, so `clear light` finds Clear Light. **Same as UI theme** stays at the top.
Up, Down, Home and End move, Enter picks, Backspace edits the search, × clears it and Escape closes the list.
Running windows change only on **Apply** or **OK**; Cancel discards the choices. A saved name that no installed theme
has shows as "(not found)" and stays saved for repair; Jasper uses the fallback described in
[configuration](configuration.md#theme) meanwhile.

The gear beside **Theme** offers **Import theme…**. It accepts local `.theme.json`, `.jar`, `.zip` and `.toml` files,
then shows providers, conversion warnings and failures. Existing user names default to Skip; choose Replace or Keep
both explicitly. Decisions use a frozen plan, and changed replacement files are refused. The import writes the
`themes` folder at once, survives Cancel and reports the files it imported; it changes neither choice. Closing
Settings cancels an import that has not started writing and closes its dialogs; a confirmed write already running may
finish and refresh the app without reopening Settings.

To copy, change or remove a theme, edit its file in the `themes` folder beside `config.toml`; see
[Writing a theme](#writing-a-theme). Jasper notices the change within a second.

Choose the appearance and terminal scheme in Settings › Appearance. The command palette’s **Themes…** command opens that
page. Settings choices stay staged until Apply/OK.

## Writing a theme

Put a `.toml` file in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS). Jasper checks the folder every second, independently of config edits, and also on explicit reload. Content hashes detect equal-size edits even when timestamps are preserved; parent edits refresh inherited themes. The smallest theme names its seed colours:

```toml
name = "Mocha"

[colors]
background = "#1e1e2e"
foreground = "#cdd6f4"
accent     = "#89b4fa"

[colors.normal]
black = "#45475a"
red = "#f38ba8"
green = "#a6e3a1"
yellow = "#f9e2af"
blue = "#89b4fa"
magenta = "#f5c2e7"
cyan = "#94e2d5"
white = "#bac2de"
```

Everything else is derived. A theme can also set:
- `dark` (otherwise inferred from the background, or inherited through `extends`), `author`, `license`, `source`,
  `commit`;
- `extends = "<theme>"`: start from any other theme and change only what you write; a file may even be named like the
  theme it extends ("Jasper Dark" extending "Jasper Dark") to replace it. Invalid candidates are skipped when
  resolving a parent, so a broken file cannot hide a valid theme with that name. `dark` and `ui_complete` are inherited too:
  a file extending Nord or another complete imported theme inherits `ui_complete = true`, so its `[colors]` and
  `[tokens]` mostly reach only the terminal and Jasper's own chrome (`Jasper.*` keys, which tokens you set
  explicitly reach), not FlatLaf's controls; change those through `[ui]`;
- `cursor` and `selection` (in `[colors]`) and `[colors.bright]` (otherwise each bright colour is its normal colour
  lightened, or darkened on a light theme);
- `ui_complete = true`, which converted IntelliJ themes use because their `[ui]` is a complete key set and Jasper's
  template is skipped; a hand-written theme does not need it;
- `[tokens]`: Jasper's semantic colours, any subset of `surface`, `surface_raised`, `surface_sunken`, `title_bar`,
  `status_bar`, `sidebar`, `text`, `text_muted`, `text_disabled`, `text_on_accent`, `link`, `border`, `separator`,
  `divider`, `focus_ring`, `hover`, `pressed`, `selection`, `selection_text`, `selection_inactive`, `text_selection`,
  `text_selection_text`, `tab_bar`, `tab_active`, `tab_active_text`, `tab_active_border`, `tab_hover`,
  `tab_underline`, `tab_underline_inactive`, `input_background`, `button_background`, `button_border`, `button_text`,
  `button_default`, `scrollbar_thumb`, `scrollbar_track`, `accent`, `success`, `warning`, `error`,
  `error_background`, `editor_background`, `editor_foreground`, `editor_caret`, `editor_selection`,
  `editor_current_line`, `editor_gutter`, `editor_line_numbers`;
- `[syntax]`: `keyword`, `type`, `function`, `variable`, `constant`, `number`, `string`, `comment`, `doc_comment`,
  `annotation`, `operator`, `punctuation`, `tag`, `attribute`, `regex`, `error`, each a colour or
  `{ color = "#…", bold = true, italic = true }`;
- `[icons]`: bounded IntelliJ icon color-palette data retained by conversion; no plugin code or external artwork is loaded;
- `[workspace]`: `opacity` (0.1–1) and `blur = true/false`. When this theme is the workspace theme on macOS, the
  terminal and editor default background becomes translucent and, with `blur = true`, the desktop behind it is
  blurred (a fixed-strength macOS material: `hudWindow` for a dark theme, a light `underWindowBackground` for a light
  one). Clear Dark sets `opacity = 0.95`, Clear Light `opacity = 0.93`, both `blur = true`. Settings › Appearance ›
  Workspace can override both. Absent values are inherited through `extends`, then mean opaque and unblurred. Only
  the workspace theme's table is used;
- `[ui]`: raw FlatLaf or IntelliJ keys (`"Button.arc" = 6`), laid over everything else. `[ui]` values that look like a
  Java class name (for example `com.example.Border`) are refused, except FlatLaf's own `com.formdev.flatlaf.` classes,
  and keys ending in `UI` cannot be set.

Derived text colours are adjusted until they are readable (4.5:1 for text, 3:1 for muted text); colours you write are
used as written. Colours are `#RRGGBB` or `#RRGGBBAA`. A mistake (an unknown key, a bad colour, a missing seed, an
`extends` that names no theme or loops) is a configuration warning naming the file and line, and that theme is skipped.
A theme file larger than 256 KiB is skipped with a warning.

## Adding themes and upgrading existing installations

Native `.toml` files in `themes/` are the runtime format. Place a native file there, or use **Import theme…** in
Settings › Appearance; Jasper discovers it within a second. The former IntelliJ/Alacritty stores and their navigation
actions are removed.

Before the initial configuration is loaded, Jasper upgrades legacy files beside the actual selected config:

- Direct `.jar`, `.zip` and `.theme.json` files in `themes/`, plus `.toml` palettes in `terminal-themes/`, are converted
  to standalone native documents. The converter reads JSON/XML/TOML data; it never loads plugin classes.
- Existing native files are preserved. Name/file collisions get deterministic suffixes; selected names follow the
  source that previously won each chrome/workspace slot, including legacy aliases.
- Fully converted originals move without replacement into `themes/legacy/`. Partially failed archives retain their
  originals and successful outputs. Failed inputs remain where they were, with persistent config warnings.
- `themes/legacy/migration.toml` records hashes, output names, ownership and progress. Staged output permits safe
  resume after interruption. `.migrated` is written only after the config step succeeds. Config edits preserve
  unrelated text/comments and keep numbered `<config>.v1.bak` backups.
- If a renamed legacy theme is the parent of an existing native theme, migration updates only that native file’s
  `extends` value, preserving comments and backing up the original in `themes/legacy/`. Interrupted edits resume from
  the journal; changed native files or occupied backups are retained with warnings.
- Changed originals, edited outputs, corrupt recovery files or conflicting config edits are retained with warnings.
  A completed pass does not silently reconvert failed or newly added legacy files. Removing a failed input clears
  its warning on config reload. **Import theme…** converts the same inputs on demand.

Supported conversion inputs are standalone IntelliJ JSON (including local parents and editor schemes), data-only
IntelliJ archives (multiple providers and nested plugin layouts), Alacritty TOML and native Jasper TOML. Unknown
IntelliJ parents use the documented light/dark fallback with a warning. Code-driven themes, YAML and external
resources are not supported. Archive/XML limits and disabled external entities apply to every path.
`themes.marketplace.enabled` is ignored for compatibility; unknown neighboring settings still warn.

## Workspace and terminal colours

`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system; `terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes. With `match` (the default) the panes use the appearance's own scheme. A named workspace theme supplies its own effective surface, text, syntax and terminal
values without installing a second look and feel. A converted IntelliJ theme with an editor colour scheme
supplies the terminal's console background, text, cursor, selection and 16 ANSI colours.
- **Missing colours:** colours the scheme leaves out come from its parent schemes, as in IntelliJ's console. A theme's `editorScheme` or a scheme's `parent_scheme` may name any scheme IntelliJ bundles: `Default`, `Darcula`, `IntelliJ Light`, `Light`, `Dark`, `Darcula Contrast`, `High contrast` and `Islands Dark`.
- **No scheme:** Jasper theme files, Jasper Dark and Jasper Light included, take their terminal palette from their own `[colors]`, and converted IntelliJ themes take it from their editor scheme. Only an installed plugin theme without a scheme uses Jasper Dark's or Jasper Light's palette by the theme's darkness.

Scheme colours are used exactly as the theme author chose them.

The terminal tab strip is part of the workspace surface: it always shows the terminal background, whatever the
theme's `tab_bar` token says, so terminals, their tabs and hosted content read as one space (and, in a translucent
window, as one sheet of glass). `tab_bar` still colours tabbed panes inside plugin panes.

## Converted terminal palettes

The offline catalog includes 168 Alacritty variants after native-family precedence and explicit exclusions; all 177
upstream inputs remain locked for provenance. For conversion, primary foreground/background are required; normal,
bright, cursor and selection colors are retained. Missing bright colors use their normal colors; cursor defaults to
foreground and selection to a blend. Dim/search/hint/indexed colors are ignored. The native file contains a complete
workspace theme derived from that terminal palette. `terminal-themes/` is no longer scanned at runtime.

An unknown workspace name warns and temporarily follows chrome. The requested name remains saved for repair.

## When something is wrong

Problems appear under the status bar's **Config warnings**, naming the file:
- An unknown `ui.appearance` or `terminal.scheme` value is reported ("Use …; using the default.") and uses the default (`system`, `match`). If Slate Steel cannot be installed, start-up uses Dark and a reload keeps the current appearance; Reload config retries.
- A failed legacy conversion is reported from the migration journal.
- A theme that fails to load, or names a parent theme Jasper doesn't have, is reported.
- Two installed themes with the same name are reported, and the file that sorts first wins.
- An unreadable editor scheme is reported, and the terminal then uses Jasper's palette.
- A terminal theme file that is not valid TOML, lacks `colors.primary` foreground and background, is too large, is not
  UTF-8, takes a reserved name or repeats another file's name is reported and skipped; a colour it cannot read is
  reported and ignored.

A theme/config migration failure is reported while startup continues. Each plugin is capped at 64 MiB, each file read from it at
4 MiB, and each jar inside a `.zip` at 32 MiB, with at most 16 such jars read.


## Native acceptance for this upgrade (user-run)

Launch against a copied legacy home. Confirm the selected chrome/workspace colors, preserved originals, suffixed
selection names when needed, and failed-input warnings. Choose Dracula, Catppuccin Mocha, Solarized Light, Gruvbox
and Code Dark with a different workspace and an open plugin pane. Verify Appearance, Font & Colors, View selections,
menus/dialogs/tooltips and workspace combo dropdowns. Repeat launch to verify no duplicate outputs. Headless tests
do not claim this visual acceptance.
