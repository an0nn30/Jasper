# GTK application style

Status: design approved in conversation on 2026-09-24; written spec awaiting user review.
Branch: `claude/gtk-theme` (from `main` at `fb576f78`).

## Intent and authority

On a Linux desktop, a user who sets `ui.theme.style = "gtk"` gets a Jasper that looks like
a native GTK application under their current desktop theme: controls, colours, fonts,
window chrome and icons all come from the GTK theme and freedesktop icon theme the
desktop has selected. The terminal follows the theme's text colours.

This adds a third value beside `"modern"` (FlatLaf) and `"retro"` (Metal/Ocean). It amends
the [retro Metal design](2026-09-22-jasper-retro-metal-design.md) and the
[SDK skin icons design](2026-09-23-jasper-sdk-skin-icons-design.md): the native-chrome
structure retro introduced is shared with GTK, and host-supplied skin icons gain a GTK
source. The SDK's Java API does not change; only its JavaDoc wording does.

Decisions taken with the user:

- **Native chrome.** GTK uses the structure retro already has: OS title bar, standard
  `JTabbedPane` tabs (`RetroTabs`), standard `JToolBar` (`RetroToolbar`), in-window menus.
  Jasper's custom modern tab strip, toolbar and integrated title bar are not used.
- **Terminal colours derived from GTK.** Background, foreground, cursor and selection come
  from the GTK theme; the 16 ANSI colours come from Jasper Dark or Jasper Light, chosen by
  the derived background's luminance.
- **Icons from the freedesktop icon theme** (approach 1): resolved in Java from the user's
  icon theme directories following the Icon Theme Specification, with bundled artwork as
  the per-icon fallback. No JNI/Panama, no new native dependency.

## Non-goals

- No GTK support on macOS or Windows; `"gtk"` falls back to modern there (see Fallback).
- No live following of desktop theme changes. Like retro, a style change (or a desktop
  theme change) takes effect on the next full launch.
- No new SDK types or methods. `OldGnomeIcon` keeps its name.
- No client-side (CSD/header bar) decorations; the window manager draws the title bar.
- No change to modern or retro appearance.

## Configuration

- `ThemeStyle` gains `GTK`. `ConfigLoader` accepts `ui.theme.style = "gtk"`; the unknown-value
  warning lists all three values.
- `ui.theme.variant` is retained but ignored in GTK mode (the GTK theme decides light or dark),
  exactly as retro ignores it. View → Appearance shows the same kind of disabled note retro
  shows ("GTK follows the desktop theme; change style in Settings and restart.").
- Changing `ui.theme.style` to or from `"gtk"` needs a full restart, like retro.
- `ConfigTemplate`, `config.example.toml` and `docs/configuration.md` document the value.

## Architecture

### Style resolution and fallback

`ThemeController` distinguishes the **requested** style (from configuration) from the
**effective** style (what is installed). At construction, if the requested style is `GTK`:

1. It asks the GTK installer to install `com.sun.java.swing.plaf.gtk.GTKLookAndFeel` by
   class name through `UIManager.setLookAndFeel(String)` — the class is Linux-only and
   module-internal, so Jasper never references it at compile time.
2. If the class is absent, `isSupportedLookAndFeel()` is false (macOS, Windows, or a
   Linux toolkit without native GTK — possible under JBR's Wayland toolkit), or
   installation throws, the controller installs modern with the configured variant,
   sets the effective style to `MODERN`, and records a fallback reason.

`style()` returns the effective style; `requestedStyle()` returns the configured one.
`ConfigurationController`'s restart-required check compares the snapshot style with
`requestedStyle()`, so a fallback does not report "restart required" forever. The
fallback reason is surfaced as a configuration warning in the status bar and logged once.

`ApplicationBootstrap.configureDesktopProperties` keeps the macOS screen menu bar for every
effective style except retro; GTK is never effective on macOS.

### Presentation predicates

`SwingAppearance.retro()` currently answers two questions. It is split:

- `SwingAppearance.nativeChrome()` — `UIManager.getBoolean("Jasper.nativeChrome")`; true for
  retro and GTK. Every structural branch (tabs, toolbar construction, title bar, rail,
  status bar layout, command palette corner radius, `TerminalDeck` tab policy) moves to it.
- `SwingAppearance.retro()` — stays for Metal-only styling (hard-coded Ocean colours,
  `Jasper.retroTitleBackground`, Metal font handling).
- `SwingAppearance.gtk()` — `UIManager.getBoolean("Jasper.gtk")`, used by icon selection.

`WindowContent.retro()` likewise becomes `nativeChrome()` (true for effective `RETRO` or
`GTK`) for structure, keeping a retro-only check where the code sets Metal colours. Each
call site is classified during implementation; the rule is "structure → nativeChrome,
Metal paint → retro". Retro's behaviour must be unchanged (existing retro tests stay green).

### `GtkDefaults` (package `appearance`)

Beside `MetalDefaults`. `install()`:

- installs GTKLookAndFeel by class name (above);
- samples the installed theme's colours by creating throwaway components under the new LAF
  (`JPanel`, `JLabel`, `JList`, `JTextArea`, `JTabbedPane`, `JMenuBar`) and reading their
  installed background, foreground, selection and caret colours, with the LAF's system
  colour defaults (`control`, `controlText`, `text`, `textText`, `textHighlight`,
  `textHighlightText`) as fallbacks when a component yields `null`;
- puts `Jasper.nativeChrome = true`, `Jasper.gtk = true`, and the same `Jasper.*` alias set
  `MetalDefaults` puts (title, chrome, muted, palette, tab, split, border, focus, selection
  keys), sourced from the sampled colours instead of Metal keys;
- sets semantic status colours (`Actions.Red/Yellow/Green`, `Jasper.runningForeground`,
  `Jasper.config*Foreground`) to the Jasper Light values on a light theme and the Jasper
  Dark values on a dark one;
- records the sampled text colours in a `GtkThemeColors` value for the palette.

Sampling logic is a pure function over a `ColorSource` (a lookup from key to colour) so it
is testable without GTK.

**Fonts.** `ui.font.family = "system"` uses the GTK theme's font unchanged. An explicit
`ui.font` family or size is applied by putting `<Region>.font` values (for example
`Label.font`, `Button.font`, `Menu.font`) into `UIManager`, which `GTKStyle` consults before
its theme font. This is a Linux-verification item: if the user's check shows GTK ignores
them, explicit `ui.font` becomes documented as ignored in GTK mode with a warning.

### `GtkPalette` (package `appearance`)

`GtkPalette.from(GtkThemeColors)` builds the terminal `Palette`:

- background / foreground: the sampled text-view (`JTextArea`) background and foreground;
- cursor: the sampled caret colour, else the foreground;
- selection background / foreground: the sampled text selection colours;
- ANSI 0–15: Jasper Dark's when the background's relative luminance is below 0.5, Jasper
  Light's otherwise.

`BuiltinTheme` gains `GTK`, whose palette is supplied at install time (the others are
constants). `ResolvedTheme` for effective GTK carries that palette; `appearance()` and the
SDK `Appearance.variant()` report dark or light by the same luminance rule.

### Freedesktop icons (package `platform`)

`IconThemeName` finds the active icon theme name, first hit wins:

1. `Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Net/IconThemeName")` (XSETTINGS,
   exposed by the X toolkit);
2. `gtk-icon-theme-name` in `$XDG_CONFIG_HOME/gtk-3.0/settings.ini`, then `gtk-4.0`;
3. output of `gsettings get org.gnome.desktop.interface icon-theme` (quotes stripped;
   bounded two-second timeout; absent binary is not an error);
4. none — lookup uses `hicolor` only.

Each source is injected, so tests supply a map, file text and command output.

`FreedesktopIcons` resolves `(candidate names, size)` to an image, following the Icon Theme
Specification:

- base directories: `$XDG_DATA_HOME/icons` (default `~/.local/share/icons`), `~/.icons`,
  each `$XDG_DATA_DIRS/icons` (default `/usr/local/share:/usr/share`), then
  `/usr/share/pixmaps` for unthemed lookup;
- themes: the named theme, its `Inherits=` chain from `index.theme` (depth-first, cycles
  ignored), then `hicolor`;
- directories: `Directories=` / `ScaledDirectories=` with `Size`, `Scale`, `Type`
  (`Fixed`, `Scalable`, `Threshold`), `MinSize`, `MaxSize`, `Threshold` — the spec's
  `DirectoryMatchesSize` / `DirectorySizeDistance` algorithm, checked per candidate name
  within a theme before moving to the next theme;
- formats: PNG through `ImageIO`; SVG through `FlatSVGIcon(URL)` from `flatlaf-extras` (jsvg
  underneath; no new dependency), rasterised at 1x and 2x into a `BaseMultiResolutionImage`;
- `-symbolic` icons are recoloured to the sampled label foreground at rasterisation;
- results (including misses) are cached per `(names, size)`; parsed `index.theme` files are
  cached per path; a file that fails to decode is logged once and treated as a miss.

Icons are returned as `ImageIcon`, so GTK's LAF produces disabled variants itself.

`FreedesktopNames` maps Jasper names to candidate freedesktop names (first found wins):

| Jasper | Candidates |
|---|---|
| `square-plus` | `tab-new`, `list-add` |
| `app-window` | `window-new` |
| `columns-2` | `view-split-left-right`, `view-dual`, `view-column` |
| `maximize` | `view-fullscreen`, `window-maximize` |
| `search` | `edit-find`, `system-search` |
| `settings` | `preferences-system`, `emblem-system` |
| `refresh` | `view-refresh` |
| `command` | `system-run`, `utilities-terminal` |
| `history` | `document-open-recent` |
| `bookmark` | `bookmark-new`, `user-bookmarks` |
| `close` | `window-close` |
| `exit` | `application-exit`, `system-log-out` |
| `LOCK` | `changes-prevent`, `system-lock-screen` |
| `UNLOCK` | `changes-allow` |
| `KEY` | `dialog-password`, `channel-secure` |
| `FOLDER` | `folder` |
| `SAVE` | `document-save` |
| `SEARCH` | `edit-find`, `system-search` |
| `HISTORY` | `document-open-recent` |
| `BOOKMARK` | `bookmark-new`, `user-bookmarks` |
| `ADD` | `list-add` |
| `REMOVE` | `list-remove` |
| `DELETE` | `edit-delete` |
| `COPY` | `edit-copy` |
| `PASTE` | `edit-paste` |
| `REFRESH` | `view-refresh` |
| `SETTINGS` | `preferences-system`, `emblem-system` |
| `EXECUTE` | `system-run`, `media-playback-start` |
| `CONNECT` | `network-connect`, `network-transmit-receive` |
| `DISCONNECT` | `network-disconnect`, `network-offline` |
| `NETWORK` | `network-workgroup`, `network-server`, `network-wired` |
| `INFO` | `dialog-information` |
| `HELP` | `help-browser`, `help-contents` |
| `CLOSE` | `window-close` |

Every Jasper name in `GnomeIcons.NAMES` and `OldGnomeCatalog.NAMES` must have an entry
(enforced by a test).

### Icon selection in `AppIcons`

In GTK mode (`SwingAppearance.gtk()`):

- `icon(name)` → freedesktop at 16 px, else the bundled GNOME 2/Tango icon;
- `toolbarIcon(name)` → freedesktop at 24 px (GTK's large-toolbar size), else bundled at 24;
- `named(name)` and `skin(loader, svg, oldGnomeName)` → a GTK skin icon: freedesktop at 16,
  with a 24 px toolbar variant for `forToolbar`, else the bundled OldGNOME2 artwork;
  argument validation is unchanged in every style;
- `themed(loader, svg)` (plugin monochrome SVG) stays an SVG recoloured to
  `Jasper.chromeForeground`, which GTK mode aliases to the sampled label foreground.

Nothing is ever blank: a miss always yields bundled artwork.

### SDK

No API change. JavaDoc for `Appearance.icon(IconName)`, `Appearance.icon(String, OldGnomeIcon)`
and `OldGnomeIcon` is updated to say the host may also draw the named artwork from the
desktop icon theme in GTK mode, and that GTK host toolbars use 24 px.

## Error handling

| Situation | Behaviour |
|---|---|
| GTK LAF class missing / unsupported / throws | Install modern; warning in config status and log; `requestedStyle()` stays `GTK` |
| No icon theme name found | `hicolor` only, then bundled artwork |
| `gsettings` missing, slow or failing | Ignored after the timeout; next source |
| `index.theme` unreadable or malformed | That theme skipped; inheritance continues |
| Icon file fails to decode | Logged once, cached as a miss, bundled artwork used |
| Sampled colour `null` | System colour default; failing that, the Jasper Light/Dark value |

## Testing

All tests are headless and run on macOS CI and locally.

- `FreedesktopIconsTest`: temporary base directories with a two-level `Inherits` chain,
  cycle, `hicolor` fallback, `Fixed`/`Scalable`/`Threshold` directories, `@2x` scaled
  directories, PNG and SVG, `-symbolic` recolouring, candidate order, miss caching and a
  corrupt file.
- `IconThemeNameTest`: source order, quoting, missing/malformed values, command timeout.
- `FreedesktopNamesTest`: every bundled Jasper name and `OldGnomeIcon` has candidates.
- `GtkDefaultsTest`: alias and status-colour derivation from a fake `ColorSource`,
  light and dark; null fallbacks.
- `GtkPaletteTest`: palette from fake theme colours; ANSI switch at the luminance threshold.
- `ThemeControllerTest` / config tests: `"gtk"` parses; restart-required uses the requested
  style; an installer stub reporting GTK unsupported yields effective modern plus a warning;
  on this macOS host the real installer falls back.
- `SwingAppearance` / chrome tests: with `Jasper.nativeChrome` and `Jasper.gtk` stubbed on a
  non-Metal LAF, windows build `RetroTabs` and `RetroToolbar` and icons come from a fake
  icon root; all existing retro and modern tests pass unchanged.
- `./gradlew check`, including architecture verification, doclint and source hygiene.

## User verification (Linux, GUI)

Agents do not launch the GUI. The user checks on a Linux GTK desktop:

1. Adwaita (light) and Adwaita-dark: controls, menus, tabs, toolbar, dialogs, status bar,
   command palette and terminal colours follow the theme.
2. A third-party icon theme (for example Papirus): toolbar, menu and plugin icons change.
3. An explicit `ui.font` family/size: applied, or confirm it is ignored (see Fonts).
4. Under Wayland with JBR's Wayland toolkit: either GTK works or Jasper starts in modern
   with the fallback warning.
5. Retro and modern still look as before.

## Documentation

`docs/configuration.md` (style table, a GTK section), `ConfigTemplate`,
`config.example.toml`, `docs/app-architecture.md` (appearance section),
`docs/app-maintenance.md` (adding an icon now needs a freedesktop mapping), SDK JavaDoc,
and `docs/STATUS.md`.
