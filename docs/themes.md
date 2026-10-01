# Themes

Jasper's themes are Jasper theme files: TOML that colours the whole app, the terminal palette and (from a later
release) syntax highlighting. Four are built in: **Jasper Dark** (the default), **Jasper Light**, and IntelliJ's
classic **IntelliJ Light** and **Darcula**, converted. Theme plugins from the JetBrains Marketplace can still be
installed; Jasper converts them when it reads them.

## Choosing a theme

Name the theme in `config.toml`:

```toml
[ui.theme]
name = "Dracula"
```

The name is the theme's own name, matched ignoring case and surrounding spaces. Changing it applies the theme to
every window at once, keeping shells, scrollback and splits. Settings › Appearance › Theme lists every built-in and
installed theme; **Get More Themes…** at the bottom opens Settings › Themes › IntelliJ Themes.

## Writing a theme

Put a `.toml` file in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS) and name it in
`ui.theme.name`. Jasper rereads the folder when the configuration is reloaded. The smallest theme names its seed colours:

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
- `dark` (otherwise inferred from the background), `author`, `license`, `source`, `commit`;
- `extends = "<theme>"`: start from any other theme and change only what you write; a file may even be named like the
  theme it extends ("Jasper Dark" extending "Jasper Dark") to replace it;
- `cursor`, `selection` and `[colors.bright]` (otherwise each bright colour is its normal colour lightened, or darkened
  on a light theme);
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
- `[ui]`: raw FlatLaf or IntelliJ keys (`"Button.arc" = 6`), laid over everything else. `[ui]` values that look like a
  Java class name (for example `com.example.Border`) are refused, except FlatLaf's own `com.formdev.flatlaf.` classes,
  and keys ending in `UI` cannot be set.

Derived text colours are adjusted until they are readable (4.5:1 for text, 3:1 for muted text); colours you write are
used as written. Colours are `#RRGGBB` or `#RRGGBBAA`. A mistake (an unknown key, a bad colour, a missing seed, an
`extends` that names no theme or loops) is a configuration warning naming the file and line, and that theme is skipped.

## Installing a theme

Settings › Themes › IntelliJ Themes has two tabs.

- **Marketplace** searches the JetBrains Marketplace's theme plugins (most downloaded first). Select one to read its
  description, then **Install**. Jasper downloads the latest release, checks that it has a theme Jasper can use, and
  puts it in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS); the banner then offers
  **Use "…"**, which chooses it on the Appearance page (Apply or OK switches). Paid plugins are not listed; freemium
  ones are marked. Clicking a link in a description opens it in your browser (http and https only).
- **Installed** lists the theme plugins in the folder, with **Use** for each theme, **Update to …** when the
  Marketplace has a newer release of one installed from it, and **Uninstall**. **Install from Disk…** copies a
  downloaded `.jar` or `.zip` into the folder; **Reveal Folder** shows it.

You can also put a plugin's `.jar` or `.zip`, unchanged, in the folder yourself and choose **Reload config**.

Jasper contacts the Marketplace only while that page is open: searches, the selected plugin's details and icons, the
download, and one update check per Marketplace install each time the Installed tab opens. Requests carry your search
text and nothing else about you. Uncheck **Browse the JetBrains Marketplace** (`themes.marketplace.enabled = false`)
to hide the Marketplace tab and stop update checks.

Jasper finds a plugin's themes through its `META-INF/plugin.xml` and reads each theme's `.theme.json` and editor
colour scheme straight from the file. Nothing is unpacked, and no code in the plugin runs. A plugin whose theme needs
IntelliJ's code, such as Material Theme UI, is refused with a message. A theme you install takes precedence over a
built-in theme of the same name. Marketplace installs are named `<plugin id>-<version>`, and `themes/.sources.toml`
records where each came from.

## What is supported

- Classic-UI theme plugins: the `.theme.json` files a plugin declares with `themeProvider`,
  including several themes in one plugin.
- A theme's `parentTheme` when it is one of the themes IntelliJ bundles: the classic `Darcula`,
  `IntelliJ` and `JetBrainsLightTheme`, the New UI `ExperimentalDark`, `ExperimentalLight` and
  `ExperimentalLightWithLightHeader`, and `Islands Dark`, `Islands Light`, `Islands Darcula` and
  `JetBrainsHighContrastTheme`. They are vendored as parents only, not offered as themes. Any other
  parent, such as another plugin's theme, is replaced by Darcula or IntelliJ by the theme's darkness,
  with a warning.
- The theme's editor colour scheme, for the terminal (below).

Not supported:
- themes a plugin draws in code, such as Material Theme UI;
- loose `.theme.json` files;
- background images;
- icon recolouring;
- New UI-only layout keys.

## Terminal colours

With `terminal.colors = "match"` (the default), a theme that has an editor colour scheme
colours the terminal with the scheme's console colours: background, text, cursor, selection and
the 16 ANSI colours.
- **Missing colours:** colours the scheme leaves out come from its parent schemes, as in IntelliJ's console. A theme's `editorScheme` or a scheme's `parent_scheme` may name any scheme IntelliJ bundles: `Default`, `Darcula`, `IntelliJ Light`, `Light`, `Dark`, `Darcula Contrast`, `High contrast` and `Islands Dark`.
- **No scheme:** a theme without one, and the built-in themes, use Jasper Dark or Jasper Light by the theme's darkness.
- **Forcing Jasper's palettes:** `"light"` and `"dark"` always use Jasper's palettes.

Scheme colours are used exactly as the theme author chose them.

## Terminal themes

`terminal.colors` can also name a terminal theme in [Alacritty](https://alacritty.org)'s format. Jasper bundles the
whole [alacritty-theme](https://github.com/alacritty/alacritty-theme) collection (177 themes, Apache 2.0), and reads
`.toml` files you put in the `terminal-themes` folder beside `config.toml`; a file of yours wins over a bundled theme of
the same name. A theme's name comes from its file name: `gruvbox_dark.toml` is "Gruvbox Dark". `match`, `light` and
`dark` are Jasper's own choices and cannot be theme names.

Settings › Themes › **Alacritty Themes** lists every theme with its colours and draws a sample terminal in the selected
one; **Use** chooses it (Apply or OK switches the terminals), **Install from Disk…** checks and copies a theme file in,
**Reveal Folder** shows the folder, and **Remove…** deletes one of your files. Settings › Terminal › Font & Colors lists
the themes too, ending in **More Terminal Themes…**.

Jasper reads a theme's `colors.primary` foreground and background (required), `colors.normal` and `colors.bright` (the
16 ANSI colours), `colors.cursor.cursor` and `colors.selection.background`. A missing bright colour uses the normal one;
a missing cursor uses the foreground and a missing selection a blend of foreground and background. Other keys (dim
colours, search, hints, indexed colours) are ignored. A missing normal colour comes from Jasper Dark, or Jasper Light
when the theme's background is light. An unknown name in `terminal.colors` is a warning and the terminal matches the UI
theme.

In the `terminal-themes` folder Jasper reads only `.toml` files; hidden files (a name starting with `.`) and other files
are ignored, and a file over 64 KiB is skipped. A file that cannot be used, or a colour Jasper ignores, is reported
under the status bar's **Config warnings**, naming the file.

## When something is wrong

Problems appear under the status bar's **Config warnings**, naming the file:
- An unknown `ui.theme.name` lists the themes available. At start-up Jasper uses Jasper Dark; on a reload it keeps the theme showing.
- A file that isn't a readable plugin, or a plugin with no theme files, is reported.
- A theme that fails to load, or names a parent theme Jasper doesn't have, is reported.
- Two installed themes with the same name are reported, and the file that sorts first wins.
- An unreadable editor scheme is reported, and the terminal then uses Jasper's palette.
- A terminal theme file that is not valid TOML, lacks `colors.primary` foreground and background, is too large, is not
  UTF-8, takes a reserved name or repeats another file's name is reported and skipped; a colour it cannot read is
  reported and ignored.

No problem stops Jasper from starting. Each plugin is capped at 64 MiB, each file read from it at
4 MiB, and each jar inside a `.zip` at 32 MiB, with at most 16 such jars read.
