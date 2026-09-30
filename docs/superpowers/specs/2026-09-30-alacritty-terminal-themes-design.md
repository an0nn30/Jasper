# Alacritty themes for the terminal

Status: approved design, 2026-09-30. Adds terminal-only colour themes in Alacritty's TOML format: the whole
[alacritty-theme](https://github.com/alacritty/alacritty-theme) collection bundled with Jasper, plus `.toml` files the
user adds, chosen through `terminal.colors` and browsed on a new Settings › Themes › **Alacritty Themes** page with a
live sample terminal. Built on the Themes group and IntelliJ Themes page (2026-09-30).

## Outcome

`terminal.colors` accepts an Alacritty theme's name besides `match`, `light` and `dark`. Settings › Terminal › Font &
Colors lists every Alacritty theme in its Terminal colors dropdown, ending in More Terminal Themes…, which opens
Settings › Themes › Alacritty Themes. That page lists the 177 bundled themes and the user's own, each with a colour
strip; selecting one shows a sample terminal drawn in its colours by Jasper's real renderer; **Use** chooses it (applied
on Apply or OK). The user can install a theme file from disk, reveal the folder and remove their own files. Nothing is
downloaded.

## Decisions (user, 2026-09-30)

1. **Source:** bundle the alacritty-theme collection (177 files, about 110 KB, Apache 2.0); user files in a folder add
   to it. No network.
2. **Setting:** extend `terminal.colors` to accept a theme name; no second setting.
3. **Preview:** a sample terminal in the details pane; nothing changes in real terminals until Apply.
4. **Code:** a new app package `dev.jasper.app.terminalthemes`.

## Alternatives considered

- **Local files only**, or **browsing the collection on GitHub**: the first leaves users to find files; the second adds a
  second network client for 110 KB of text. Rejected.
- **A separate `terminal.theme` key** overriding `terminal.colors`: two settings with a precedence rule. Rejected.
- **Separate choices for dark and light UI themes** (`terminal.colors.dark`/`.light`): more powerful; can be added on top
  later. Deferred.
- **Live preview in open terminals**: breaks the dialog's "nothing changes until Apply" rule and needs revert handling.
  Rejected. **Swatches only**: too little to judge a theme by. Rejected.
- **Folding the code into `appearance`**: that package already carries IntelliJ theme loading. Rejected.

## Design

### Themes and names

- **Bundled:** the collection's `themes/*.toml`, vendored under `jasper-app/src/main/resources/dev/jasper/app/terminalthemes/alacritty/`
  with its `LICENSE` and a `SOURCE.md` naming the upstream commit and date, plus a generated `index.txt` listing the
  files (resources cannot be listed reliably from a jar).
- **User files:** `.toml` files in a `terminal-themes` folder beside `config.toml`, created when missing. Hidden files and
  other extensions are ignored.
- **Names** come from the file name without `.toml`: `_` and `-` become spaces and each word's first letter is
  capitalised, the rest kept (`gruvbox_dark` → "Gruvbox Dark", `Cobalt2` → "Cobalt2", `iterm` → "Iterm"). Names match
  ignoring case and surrounding spaces. A user file takes precedence over a bundled theme of the same name; two user
  files with one name is a problem, and the file sorting first (ignoring case) wins.
- `match`, `light` and `dark` are reserved: a theme file with one of those names is a problem and is not offered.

### Reading a theme

`AlacrittyTheme.read(String name, String toml)` returns the palette or the reasons it has none, plus warnings.

- tomlj parses the text; a syntax error makes the theme unusable.
- Colours are `#rrggbb` or `0xrrggbb`, any case. Anything else where a colour is expected is a warning, and the key is
  treated as missing.
- **Foreground and background:** `colors.primary.foreground` and `.background`; without both the theme is unusable.
- **ANSI 0–7:** `colors.normal.{black, red, green, yellow, blue, magenta, cyan, white}`; **8–15:** `colors.bright.*` of
  the same names. A missing bright colour takes the normal one; a missing normal colour takes Jasper Dark's (background
  luminance below 0.5) or Jasper Light's.
- **Cursor:** `colors.cursor.cursor` when it is a colour; otherwise (missing, or a keyword such as `CellForeground`)
  the foreground.
- **Selection:** `colors.selection.background` when it is a colour; otherwise the foreground blended 30% over the
  background.
- Every other key (`dim`, `indexed_colors`, `footer_bar`, `search`, `hints`, `import`, `bright_foreground`, …) is
  ignored silently.

### The catalog

`TerminalThemeCatalog` holds the bundled themes (read once, on first use) and the folder's, like `ThemeCatalog`:
`scan()` re-reads the folder (only files whose size or modified time changed) and may run on any thread; `latest()`
returns an immutable snapshot with `find(name)`, the themes in list order (user files first by name, then bundled by
name) and the problems (file and message). The application registers `scan` with the configuration service beside the
UI theme catalog's, so it runs at start-up and on every configuration read. Problems join the configuration warnings
under the status bar.

### The setting

- `terminal.colors` changes from a choice to a string setting: default `"match"`; `match`, `light` and `dark` keep their
  meaning; any other non-blank value names an Alacritty theme. The value is stripped; `match`/`light`/`dark` match
  ignoring case.
- `TerminalColors` becomes a value (sealed: `Match`, `Light`, `Dark`, `Named(String)`) parsed from the setting.
- `ThemeState.resolve` uses the named theme's palette from the latest catalog snapshot. An unknown name is a
  configuration warning listing nothing but the name and pointing at Settings › Themes › Alacritty Themes, and the
  terminal uses `match`. When a rescan changes the named theme, the terminals take the new palette on the same reload.
- The configuration guide table and `config.example.toml` document the new values.

### Font & Colors

The Terminal colors row becomes an editable combo like the UI theme's: "Match the theme", "Light", "Dark", a rule, every
theme name in list order, and a final **More Terminal Themes…** entry that navigates to `themes.alacritty` and is never
stored. The display names map back to `match`/`light`/`dark`. It shares the UI theme combo's behaviour: re-read on open,
arrow keys never select the action entry, Enter on the highlighted action entry navigates. The shared part moves into
one class used by both combos rather than being copied.

### The page

Settings › Themes › **Alacritty Themes** (id `themes.alacritty`, help anchor `alacritty-themes`), after IntelliJ Themes.
It has no settings of its own, so it is not a schema page.

- **Left:** a search field (filters by name as you type) over a list. Each row: the name, "Bundled" or "Your file", a
  mark when it is the theme `terminal.colors` names in the draft, and a strip of the eight normal colours on the theme's
  background. Keyboard: Up/Down move, Enter uses.
- **Right:** the name and source, the file's problems if any, the sample terminal, and buttons: **Use** (stages
  `terminal.colors` = the name in the draft; the banner says Apply or OK switches), **Remove…** (user files only; asks,
  deletes the file, rescans), and in the page header **Install from Disk…** and **Reveal Folder**.
- **Install from Disk…** uses the dialog's chooser; the file must end in `.toml`, parse as a usable theme, and not use a
  reserved name. It is copied in through a dot-named temporary file and an atomic move; an existing file of that name
  asks before being replaced. Then the catalog rescans (through the configuration reload, on the EDT) and the new theme
  is selected.
- The page reads the catalog's latest snapshot when shown and after each install or remove.

### The sample terminal

A real `TerminalView` on `TerminalSession.attach`, whose connection's output is a canned UTF-8 stream: a prompt line,
an `ls`-style listing using ANSI colours 1–6 and bold, a row of the 16 colours as blocks, and a line that the view
selects so the selection colour shows. Its input is discarded, its resize ignored, and it never exits until the page is
disposed, which closes the session. It uses the configured terminal font and size (a supplier from the application), is
not focusable, and fixes its grid at 60 × 10. Selecting a row calls `setPalette` with that theme's palette. It is
created when the page component is first built, on the EDT after the session starts off the EDT as the terminal module
requires; until then the pane shows the colour strip alone.

### Package and ownership

`dev.jasper.app.terminalthemes`, allowed outgoing dependencies `appearance`, `config`, `lifecycle`, `settings`,
`settingsui`, and the terminal module's supported `session`, `view` and `config` packages. The application owns the
`TerminalThemeCatalog` (created with the UI theme catalog) and supplies the page factory to `CorePages`. `CorePages.pages`
takes its application-supplied page factories as one record (`CorePages.Pages`: keymap, plugins, tools, IntelliJ themes,
Alacritty themes), replacing the growing overload list. `appearance` resolves named themes through a function it is
given, so it does not depend on `terminalthemes`.

## Testing

Headless and TDD.

- **Bundled set:** every bundled file reads as a usable theme with no warnings, and the names are distinct.
- **Parser:** `#` and `0x` colours in either case; missing bright and normal colours; keyword and missing cursor and
  selection; a bad colour string (warning); missing primary colours and bad TOML (unusable); ignored keys.
- **Names:** file names to display names; reserved names refused.
- **Catalog:** user file beats bundled; duplicate user names; rescan picks up added, changed and removed files; hidden
  and non-`.toml` files ignored.
- **Setting and resolution:** `match`/`light`/`dark` in any case, a known name, an unknown name (warning, `match`
  palette); the old choice values still load unchanged.
- **Combo:** entries and mapping; More Terminal Themes… navigates and is never stored; shared behaviour covered once.
- **Page:** list and filter; Use stages the setting; install (valid, invalid, reserved name, replace asks) and remove;
  the sample's palette follows the selection; dispose closes the sample session.

## Documentation

`docs/configuration.md` (`terminal.colors` values and the Settings tree), `docs/themes.md` (a Terminal themes section:
bundled collection, the folder, names, what is read and ignored), `docs/app-architecture.md` (the package), and
`docs/STATUS.md`. The vendored collection's licence and provenance live beside it (`LICENSE`, `SOURCE.md`), as the
vendored IntelliJ themes' do.

## GUI checks (for the user)

Font & Colors dropdown lists the themes and More Terminal Themes… opens the page; the sample looks like a real terminal
in the chosen font; Use then Apply recolours open terminals; install a downloaded theme and remove it; a theme with no
cursor or selection colours still shows both; switching back to Match the theme.
