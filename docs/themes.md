# Themes

Jasper's themes are Jasper theme files: TOML that colours the app, workspace, terminal palette and editor syntax.
Jasper ships **284 native themes**, including Jasper Light/Dark, IntelliJ Light/Darcula, Dracula,
Catppuccin, Solarized, Nord, Gruvbox, One Dark, Material Lite, Code, Studio and converted Alacritty palettes.
The complete offline collection, pinned sources, licenses, exclusions and explicit renames are in
[third-party theme notices](../THIRD-PARTY-THEMES.md). No Marketplace network client remains.

## Choosing a theme

Name the theme in `config.toml`:

```toml
[ui.theme]
name = "Dracula"
workspace = "match" # or "Jasper Dark", another native theme
```

The name is the theme's own name, matched ignoring case and surrounding spaces. Changing it applies the theme to
every window at once, keeping shells, scrollback and splits. Settings › Appearance & Behavior › Appearance has two
dropdowns: **Theme** (the UI theme) and **Workspace scheme**, whose first entry, **Same as UI theme**, is `match`.
Terminal › Font & Colors links to them. The workspace scheme switches independently, including between two dark
themes. The pane tab strip, find controls,
split dividers, terminal palette, zone-aware icons and Editor syntax follow it. Menus, sidebars and dialogs follow
the UI theme. Ordinary controls in hosted center plugin panes also follow the workspace, including lists, tables,
trees, selections, headers, combo dropdowns, scrollbars, disabled controls and progress track/fill colors. A workspace
combo's own dropdown is the sole popup exception: other menus and tooltips remain chrome. Plugin-authored styles and
exempt custom document surfaces retain ownership. Moving a pane out of the workspace or selecting `match` removes
the host's generated overrides without losing document edits, selection or undo. Remote SFTP/Tunnels, File Server
and Vault rail panels remain chrome. Vim errors, bell feedback and banner rules resolve the mounted zone.

FlatLaf 3.7 has no per-control progress text styling; an external plugin's string-painted progress bar can retain
chrome text colors. Bundled transfers use a bar without painted text and a separate status label. Custom renderers
and painters use mounted-owner palette lookup; see [plugin authoring](plugin-authoring.md#hosted-pane-control-styling).

User visual acceptance: try light chrome/dark workspace and the reverse, then two themes of the same darkness;
inspect controls, selections, combo dropdowns, rail panels and popups; retain an edited document while moving,
detaching, reopening and closing its pane; check Vim error/bell feedback and banners; return to `match`. Repeat with
GitHub Dark or another complete imported theme. These checks require the native GUI and are not claimed by headless
test results.

## Settings and importing

Both dropdowns list every installed theme by name and update when theme files are added, imported, edited or removed.
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

View › **UI Theme** and **Workspace Scheme** offer the current catalog. Large catalogs are grouped into numbered
submenus of at most 30 themes. View › **Themes…** opens Settings at Appearance; the command palette also finds it.
These View choices save immediately, whereas Settings choices stay staged until Apply/OK.

## Writing a theme

Put a `.toml` file in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS) and name it in
`ui.theme.name`. Jasper checks the folder every second, independently of config edits, and also on explicit reload. Content hashes detect equal-size edits even when timestamps are preserved; parent edits refresh inherited themes. The smallest theme names its seed colours:

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
  a file extending IntelliJ Light, Darcula or a converted theme inherits `ui_complete = true`, so its `[colors]` and
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

With `ui.theme.workspace = "match"` (the default), workspace surfaces, Editor syntax and terminal colours
follow `ui.theme.name`. A named workspace theme supplies its own effective surface, text, syntax and terminal
values without installing a second look and feel. A converted IntelliJ theme with an editor colour scheme
supplies the terminal's console background, text, cursor, selection and 16 ANSI colours.
- **Missing colours:** colours the scheme leaves out come from its parent schemes, as in IntelliJ's console. A theme's `editorScheme` or a scheme's `parent_scheme` may name any scheme IntelliJ bundles: `Default`, `Darcula`, `IntelliJ Light`, `Light`, `Dark`, `Darcula Contrast`, `High contrast` and `Islands Dark`.
- **No scheme:** Jasper theme files, Jasper Dark and Jasper Light included, take their terminal palette from their own `[colors]`, and IntelliJ Light and Darcula from their own editor scheme. Only an installed plugin theme without a scheme uses Jasper Dark's or Jasper Light's palette by the theme's darkness.
- **Choosing Jasper's palettes:** `"Jasper Light"` and `"Jasper Dark"` select the complete native themes.
  Legacy `terminal.colors = "light"` or `"dark"` maps to these names when read. Jasper Dark's native terminal
  background is `#191c20`.

Scheme colours are used exactly as the theme author chose them.

## Converted terminal palettes

The offline catalog includes 168 Alacritty variants after native-family precedence and explicit exclusions; all 177
upstream inputs remain locked for provenance. For conversion, primary foreground/background are required; normal,
bright, cursor and selection colors are retained. Missing bright colors use their normal colors; cursor defaults to
foreground and selection to a blend. Dim/search/hint/indexed colors are ignored. The native file contains a complete
workspace theme derived from that terminal palette. `terminal-themes/` is no longer scanned at runtime.

An unknown workspace name warns and temporarily follows chrome. The requested name remains saved for repair.

## When something is wrong

Problems appear under the status bar's **Config warnings**, naming the file:
- An unknown `ui.theme.name` lists the themes available. At start-up Jasper uses Jasper Dark; on reload it uses the safe built-in for the last successfully applied chrome darkness. The requested name remains saved for a later retry.
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
