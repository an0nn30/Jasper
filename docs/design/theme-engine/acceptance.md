# Theme engine Plan 3 acceptance record

Plan 3 Task 11's reference → fixture → output → review mapping, written 2026-10-12 when the last surface workstream
merged into `codex/theme-engine`. Authority: the [theme engine specification](../../superpowers/specs/2026-10-04-jasper-theme-engine-design.md)
(sections 8 and 10), its 2026-10-10 reference-completion amendment (all 16 Desktop references), and the user's
2026-10-11 instruction for this round: the mocks fix every non-color detail, colors come from the active theme,
component looks live in `jasper-ui` (exposed to plugins through the SDK) and layout lives in the owning plugin or app.

## How the evidence is made

- Every render is the **actual owner** (staged plugins in a disposable home, the real app windows and Settings pages)
  painted headlessly at device scale 2, at the logical size the reference crop implies. Nothing is resized.
- Since Slate Steel Plan 3 the renders are **Slate Steel Dark** (each family's Light render was inspected too, see
  [Slate Steel Plan 3](#slate-steel-plan-3-removal-and-verification-2026-10-12)). The calibration themes that
  approximated the mock colours are retired; the tables below that say "calibration" record the values they gave.
- [`tools/visual`](../../../tools/visual/README.md) compares luminance edges (`compare_mock.py`), cuts matching
  regions at original pixels (`region.py`) and runs all 34 recorded pairs (`compare_all.py`, `pairs.json`). Edges are
  luminance, so mocks drawn in other palettes (the Vault mock's accent `#4676EE`) still compare.
  The edge agreement is a guide; each stream measured bounds, insets, baselines, control sizes, radii and glyphs with
  pixel probes, and recorded what still differs.
- Regenerate: `./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview
  :jasper-app:workspaceSurfacePreview`, `./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"`,
  `./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest' --tests '*SessionsCompositionTest'`,
  then `python3 -I tools/visual/compare_all.py <outdir>`.

## Mocked surfaces

Edge agreement at the start of this round → at merge (calibration theme, device scale 2; Slate Steel Dark values are in
[Slate Steel Plan 3](#slate-steel-plan-3-removal-and-verification-2026-10-12)). Stream notes hold the
measurements and every residual.

| Reference (`reference/desktop-2026-10-10/` unless noted) | Surface and owner | Before | After | Notes |
| --- | --- | ---: | ---: | --- |
| `rename_vault_dialog.png` (Unlock), `unlock-vault.png` | Vault unlock hero (`vault/ui/PasswordPanel`) | 0.972 | 0.977 | [forms](acceptance/forms.md) |
| `create_vault_dialog_mock.png`, `create-vault.png` | Vault create hero | 0.925 | 0.949 | [forms](acceptance/forms.md) |
| `add_host_dialog_mock.png`, `add-host.png` | Add Host (`remote/ui/HostEditor`) | 0.863 | 0.914 | [forms](acceptance/forms.md) |
| `new_file_server_mock.png`, `new-file-server.png` | New File Server (`fileserver/ui/ServerEditor`) | 0.865 | 0.922 | [forms](acceptance/forms.md) |
| `combo_box_mock.png` (six states) | Anchored credential picker (Vault producer, kit `PickerMenus`) | 0.548–0.986 | 0.763–0.987 | [forms](acceptance/forms.md) |
| `vault_ui_mock.png`, `credential-vault.png` | Credential Vault manager (`vault/ui/ManagerPanel`) | 0.620 | 0.783 | [forms](acceptance/forms.md) |
| `reference/vault-add-menu.png` | Vault add menu | 0.874 | 0.968 | [forms](acceptance/forms.md) |
| `reference/add-login-sheet.png`, `add-ssh-key-sheet.png` | Attached Login / SSH key sheets | 0.971 / 0.949 | 0.974 / 0.967 | [forms](acceptance/forms.md) |
| `new_tunnel_mock.png` | New Tunnel (`remote/ui/tunnels/TunnelEditor`) | 0.799 | 0.912 | [panels](acceptance/panels.md) |
| `Screenshot … 11.16.50 AM.png`, `reference/plugin-panels.png` | Sessions, SFTP, Tunnels, File Servers panels | 0.958 / 0.493 / 0.122 / 0.152–0.541 | 0.967 / 0.831 / 0.948 / 0.949 | [panels](acceptance/panels.md) |
| `settings_dialog_mock.png` | Settings Appearance | 0.579 | 0.876 | [settings](acceptance/settings.md) |
| `settings_windows_mock.png` | Settings Window | 0.634 | 0.807 | [settings](acceptance/settings.md) |
| `settings_notification_mock.png` | Settings Notifications (popup open) | 0.513 | 0.847 | [settings](acceptance/settings.md) |
| `settings_keymap_mock.png` | Settings Keymap (recording) | 0.413 | 0.841 | [settings](acceptance/settings.md) |
| `settings_plugins_mock.png` | Settings Plugins | 0.736 | 0.817 | [settings](acceptance/settings.md) |
| `Screenshot … 11.16.25 AM.png` / `11.16.41 AM.png` | Workspace chrome: title bar, command centre, toolbar, tabs, rails, status bar | 0.780 / 0.788 | 0.807 / 0.862 | [workspace](acceptance/workspace.md) |
| `Screenshot … 11.16.50 AM.png` | Integrated workspace with the four docked panels | 0.553 | 0.770 | [workspace](acceptance/workspace.md) |
| `Screenshot … 11.16.31 AM.png` | Command palette | 0.873 | 0.897 | [workspace](acceptance/workspace.md) |

The remaining agreement gap is overwhelmingly text rasterization (headless text runs 2–6% wider than the native
captures) and fixture content (key names, paths, counts, the mock's sample data), not geometry: the streams' probes put
depicted elements within 1 logical pixel except for the recorded residuals below.

## Unmocked surfaces

Each follows the compositions the mocked families calibrated; rendered dark, light and enlarged, narrow and short.

| Family | Composition | Notes |
| --- | --- | --- |
| Editor find/replace, banners, Vim strip, load failure, go-to-line, questions; Sample; Project Index; Shell History; Snippets | Document bars, standard empty states, SHEET/FULL form cards, Settings pages of labelled section cards | [editor](acceptance/editor.md) |
| File chooser, Errors window, notification cards and history, theme import sheets, chooser prompts | Vault manager columns and footer band, detail cards, panel empty state, grouped cards | [app-unmocked](acceptance/app-unmocked.md) |
| Tunnels populated rows, SFTP connected browser, transfer strip and conflict dialogs | Compact panel rows with row switches, canonical navigation/table, hero question and grouped forms | [panels](acceptance/panels.md) |
| Plugin Settings pages (generated and custom) | Settings rows with steppers and units, Reset only when it applies | [settings](acceptance/settings.md), [editor](acceptance/editor.md) |

## Accepted differences

- macOS draws the window frame, traffic lights and window shadow; renders leave that space and never paint
  counterfeit controls. The title rows around them follow the references (see the follow-up round below).
- Native text advance and antialiasing differ from headless Java2D; font families, sizes, weights and baselines are
  matched, ink widths are not.
- Mock text, hosts, keys, counts and paths are fixture data; real defaults, stored data and validation were never
  changed to imitate them. Where the domain has no data the mock shows (key algorithms in Add Host, "Used by" in the
  Vault detail), the render shows only what exists.
- Colors follow the active theme (the user's rule): Slate Steel Dark or Light; the retired calibration themes only made comparisons legible.

## Recorded residuals (decisions)

- Port fields use the 83-logical width that New File Server (both references) and New Tunnel share; Add Host's
  references show 71, a 12-logical residual against the majority.
- Panel empty-state descriptions wrap at 206 logical (Tunnels and File Servers); both SFTP references wrap near 226.
- The Settings font dropdown shows the stored family label ("System"), not the mock's resolved "System (SF Pro)".
- Settings Plugins titles use Helvetica Neue Medium because JBR has no SF Semibold (about 3 logical narrower).
- The Vault manager's rail has no More menu, as depicted; Change Master Password and Plugin Grants are Vault commands
  in the File menu and palette.
- Stream-specific sub-pixel residuals (0.5–2 logical) are listed in each stream's notes.

## Verification

- `./gradlew check` on the merged branch: 10,042 tests, 0 failures, 0 errors, 6 environment skips (829 XML files);
  architecture, JavaDoc doclint and compiled documentation examples included.
- An independent whole-branch review of `76369085..HEAD` found 3 Important and 5 Minor issues and no Critical ones:
  a New Tunnel primary disabled without a visible reason, selected segment and theme-preview text using the on-accent
  role on non-accent surfaces, plugin Details text not wrapping long paths, a captioned label moving with an error,
  Reset visibility and unit inconsistencies in plugin setting rows, missing tests and stale docs. All were fixed with
  regressions, several verified to fail without the fix. The review confirmed every parallel-merge hunk kept both
  sides and no calibration value was lost.
- Native smoke launch (2026-10-12, `./gradlew :jasper-app:run`, isolated `build/dev-home`): all eight bundled plugins
  active, window shown and painted with shell output, no warnings or errors in the log.

## Follow-up round (2026-10-12)

The user's checks after the first native pass found the dialog title rows standard rather than as mocked.

- Title rows, measured on the references with pixel probes (logical, from the window's top edge): a window row is 45
  above a hairline, title centred 23 down (Credential Vault); a dialog's title and controls centre 21 down and its
  content starts 30 down, with no rule and the dialog surface throughout (Add Host, New File Server, New Tunnel); the
  Settings header row is the window's title row (rule at 45, controls over the sidebar). They are kit metrics
  (`WINDOW_TITLE_HEIGHT`, `DIALOG_TITLE_HEIGHT`, `DIALOG_TITLE_CENTER`, `HERO_TITLE_HEIGHT`, `HERO_CONTROLS_CENTER`)
  applied by `MacTitleBar.Style`; Settings declares `AuxiliarySurface.integratedTitle`. The Vault manager's title
  row now compares at 0.648 (`vault-manager-title`; the native traffic lights are absent from the render) and its
  body stays at 0.786 with the render crop moved below the new row.
- Indeterminate progress loops through the whole track, and the searchable dropdown rebuilds its rows in one step
  (the installed-font list opened slowly because each open re-measured every row once per row).
- Unlock Vault and Create Vault are the kit's modal overlay (`ModalOverlay`, SDK 0.15.4
  `DialogSpec.Presentation.OVERLAY`), as the user specified: the window dims under a black scrim (40% then; since Slate Steel Plan 1 the scrim is painted as its token gives it) while the
  card fades up 16 logical into place over 220 ms (ease-out cubic), and leaves as its opposite over 200 ms, lifting
  off at once and rising another 16 (ease-out) as it fades (ease-in); instant with animations off. The centred hero mocks' window frame no longer applies to these two prompts; their
  hero content keeps its geometry inside the outlined card. Remote's progress overlay uses the same kit overlay.
  `surface.scrim` now defaults to black (a theme may name its own), since a terminal's darkest colour could leave a
  dark window undimmed.
- Show more… on an error notification failed for good once the Errors window's view had failed to build (the
  running app's jars had been replaced by a build); a failed open now closes its window, so the next one works.

## Slate Steel Plan 1: engine and kit (2026-10-12)

Plan [`2026-10-12-slate-steel-1-engine-and-kit.md`](../../superpowers/plans/2026-10-12-slate-steel-1-engine-and-kit.md)
of the [Slate Steel specification](../../superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md). The theme is
built into the engine from [`slate-steel.md`](slate-steel.md) (the user's `theme.md` plus derived editor schemes) but not
yet installed: Plan 2 switches the app to it, Plan 3 removes the old themes. Until then the legacy themes give the ten new
roles the colour their component painted before, so the app looks as it did except where a component moved roles (the
legacy tab strip is the terminal background sunk 30% toward black in a dark theme and 6% in a light one, as the strip
painted before, so the selected tab stays the lighter slot; tool windows move from the sidebar to the panel surface).

- Opacity is applied once: scrims are painted as their token gives them (legacy default black at 55%/25%) (was
  `surface.scrim` at 40% for the modal overlay and 35% for sheets); the focus ring applies 32%/25% of `border.focus` (was
  25% in both), the error ring 22% (unchanged), tiles and chips 17%/13% of their hue surface (was 18% or 16% of the hue).
- The title bar's command field is a raised field (hover changes only its edge) (was `surface.input` with a `border.input`
  edge, `surface.control.hover` on hover); selected toolbar actions, rail items and manager-rail rows take
  `surface.control.selected` (was `segment.selected.bg`, and `surface.control` for rail rows); tool windows sit on
  `surface.panel` (was `surface.sidebar`).
- The tab strip is `surface.tabstrip`, the selected tab `surface.terminal` (was the `TAB_BAR` and `TAB_ACTIVE` tokens), at
  the window's alpha; hovered inactive tabs take the hover overlay (was none).
- Secondary buttons take `button.secondary.border` and `text.strong.secondary` (was `border.control` and `text.primary`);
  the segmented track `segment.track` with a `border.input` edge (was `surface.input` with `border.subtle`); panel empty
  tiles `surface.emptyTile` (was `surface.control`).
- Captions, counts, table headers and row subtitles are `text.tertiary` (was `text.secondary`); Settings row titles and
  the current breadcrumb `text.strong.secondary` (was `text.tertiary` and `text.primary`); empty picker values
  `text.placeholder` (was `text.tertiary`); the idle status dot `status.idle` (was `text.secondary`).
- The command palette is a `surface.menu` card with a `border.menu` hint rule and an accent highlight (was a
  `surface.card` card with a `border.subtle` rule and a `selection.bg` highlight); menus highlight with the accent (was
  `selection.bg`); highlighted subtitles take `text.onAccent.secondary` (was `text.onAccent`), checkmarks `text.link` (was
  `accent`); the Settings body is `surface.dialog` (was `surface.app`) and the file chooser's Places `surface.sidebar` (was
  the window surface).
- theme.md's shadows, black: the modal overlay's card, the palette, FlatLaf menus, the Light selected segment and the
  primary button (a white inset line on its top row and a 1 px drop below the face) (was the scrim colour for the overlay
  card's shadow, FlatLaf's default popup shadow and no button or segment shadow).

Decisions the plan recorded for review:

1. Manager-rail selected rows (Vault categories, file-chooser Places) take `surface.control.selected` (the Vault mock's
   grey "All Items" row), not `selection.bg`.
2. Tab dividers stay `border.window` (theme.md is silent); terminal split dividers take `border.window` too (the legacy
   divider token maps to it; Plan 3 moves the painter).
3. The equal-width tab strip gets no accent "active tab indicator": the workspace mocks show none (the active tab is the
   lighter `surface.terminal` slot). Inactive tabs gain the hover overlay.
4. The command palette gets no backdrop scrim: the palette mock leaves the window undimmed, although theme.md lists the
   palette among the scrim's users.
5. Chrome icons (title bar toolbar, rails) keep `text.primary`, as the workspace mocks show them bright; kit icons already
   tint `text.secondary`.
6. The segmented control's Light "soft shadow" (theme.md gives no values) is black at 12%, 1 px down, 2 px blur.
7. theme.md's "current breadcrumb" (`text.strong.secondary`) applies to the kit's breadcrumb control (file browsers). The
   Settings header's current crumb is the page title and stays `text.primary`, which is what the Settings mock paints.
8. The primary button's 1 px drop is painted by its kit view outside the face, as CSS `box-shadow` does, so the face keeps
   the extent calibrated against the mocks (the plan first drew it inside the bounds with a 1 px shorter face; the
   calibrated face tests showed that moved the visible bottom edge). A primary button outside a kit view has the inset
   line only.

## Slate Steel Plan 2: app switch (2026-10-12)

Plan [`2026-10-12-slate-steel-2-app-switch.md`](../../superpowers/plans/2026-10-12-slate-steel-2-app-switch.md) of the
[Slate Steel specification](../../superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md). The app now starts
and runs on Slate Steel only; Plan 3 removes the legacy themes and their test seams.

- Settings: `ui.appearance` (`system`, `dark`, `light`) and `terminal.scheme` (`match`, `slate-steel-dark`,
  `slate-steel-light`, `high-contrast`) replace `ui.theme.name`, `ui.theme.source`, `ui.theme.follow_system` and
  `ui.theme.workspace` (with the aliases `ui.theme.variant`, `ui.theme.terminal` and `terminal.colors`); a file that
  still sets them starts Slate Steel and reports them as unknown settings.
- Runtime: start-up and every reload apply the appearance (`system` follows the operating system live, Dark when it is
  unknown) and the scheme through `ThemeController`; no themes folder is scanned and no theme is upgraded at start-up.
- Terminal surroundings: a scheme colours only the terminal text area. The window behind panes, a tab's background, the
  inset below the cell grid, the window tab strip and the active tab's title stay on the UI variant's roles
  (`surface.terminal`, `text.primary`), so High Contrast under Light is a black text area in a Light window.
- Bold colour: bold text in the default foreground takes the scheme's bold foreground (`terminal.fg.bold`).
- The Appearance page: Dark / Light / Match System tiles, a read-only Theme row with the one entry "Slate Steel", and the
  Terminal scheme dropdown ("Colors for terminal panes"); Terminal › Font & Colors links there.
- The command palette: "Terminal: Same as UI theme / Slate Steel Dark / Slate Steel Light / High Contrast"
  (`view.terminal_scheme.<name>`) choose a scheme for every window; "Themes…" opens Appearance.

Decisions the plan recorded for review:

1. An unknown `ui.appearance` or `terminal.scheme` value is reported like every other enumerated setting's: "Use …; using the default.", which titles the existing notice "config.toml has errors". The spec says "warnings"; the plan keeps one convention for all enumerated settings.
2. A removed `ui.theme.*` key left in a file is reported once for its table (`ui.theme`: "Unknown setting or table; ignored."), as any unknown table is; `terminal.colors` is reported as its own key.
3. The Theme dropdown is enabled with its one entry, so it opens and shows "Slate Steel" and nothing else can be chosen; it is not greyed out, matching the mock.
4. The Match System tile says "System appearance: Dark/Light", or "System appearance unavailable; using Dark".
5. The command palette's "Terminal: Match UI / Light / Dark" become "Terminal: Same as UI theme / Slate Steel Dark / Slate Steel Light / High Contrast" (`view.terminal_scheme.<name>`); "Themes…" stays and opens Appearance.
6. Bold text in the default foreground takes the bold colour; an explicit SGR colour stays; the bold colour is chosen before inverse, so inverse bold text has it as its background (as xterm's bold colour does).
7. `new ThemeController()` and the other legacy seams keep Jasper Dark for existing tests until Plan 3 migrates them; production starts with `ThemeController.startup(dark, scheme, …)` and configures only Slate Steel.
8. The app's `themeimport` and `themesui` packages lose their wiring here (no Import theme…, no start-up theme upgrade) and are deleted with the rest in Plan 3.

## Slate Steel Plan 3: removal and verification (2026-10-12)

Plan [`2026-10-12-slate-steel-3-removal-and-verification.md`](../../superpowers/plans/2026-10-12-slate-steel-3-removal-and-verification.md)
of the [Slate Steel specification](../../superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md): the legacy
themes, their layer and their test seams are gone; every test, preview and render runs Slate Steel Dark and Light.

- Removed from the app: `Theme`, `ThemeCatalog`, `BuiltInThemes`, `ThemeState`, `config.WorkspaceThemeChoice`,
  `config.TerminalColors`, the `themeimport` and `themesui` packages (theme import, conversion and the one-time
  migration with its aliases), `ThemeManager`'s legacy install and `ThemeController`'s legacy constructors and
  `configure` overloads, the `convertThemes` and `themeMigrationPreview` tasks. An older install's `themes` and
  `terminal-themes` folders are neither read nor changed.
- Removed from jasper-ui: `Token`, `SemanticDefaults`, `ThemeResolver`, `ThemeDocument`, `ThemeGenerator`,
  `AuthoredUiColors`, `RuntimePlatform`, `Seed`, `UiTemplate`, `UiTokenKeys`, `WorkspaceGlass`, the `themeio` and
  `themeimport` packages and their resources, and the `tomlj` dependency; from the repository, `third_party/themes`,
  `tools/themes` and `THIRD-PARTY-THEMES.md`. `GeneratedTheme` is `(name, dark, author, semantic, terminal, editor,
  syntax, uiKeys, glass)`; FlatLaf's keys come from the roles (`UiColorKeys`), and plugins' palettes from the roles and
  the editor scheme (`HostedPalettes`).
- The scan: `ColourLiteralScanTest` fails on any colour literal in production code or resources of jasper-ui, the app
  and the plugins outside `slate-steel.json`; `RemovedThemeLayerTest` pins the removal.
- Tests run on `ThemeTestSupport` and `KitFixtures` (Slate Steel's variants, recoloured copies where a test needs
  another theme).

Decisions this plan recorded for review:

1. **Scan scope.** The colour-literal scan covers production Java and text resources of `jasper-ui`, `jasper-app` and `plugins/*`. It excludes SVG artwork (the kit's JetBrains icons are SHA-256-pinned and painted as drawn; plugin icons are plugin-owned ink by contract), and the independent libraries `jasper-terminal` (its built-in default palettes for embedders that set none), `jasper-buddy` (the desk buddy's own artwork, JDK-only) and the test library `jasper-sdk-testkit`.
2. **Scan pattern.** It matches the spec's hex forms: `#RRGGBB`/`#RRGGBBAA` in Java string literals and text resources, and hex integers given to `new Color(`, `new ColorUIResource(` or `Color.decode(`. Decimal `new Color(r, g, b)` literals (fully transparent fills; the editor plugin's vim sneak highlight) and the `Color.BLACK`/`Color.WHITE` constants that spec 8's shadow and inset metrics are painted in are not matched.
3. **The 26 `Jasper.*` UI aliases are dropped** from the FlatLaf keys: nothing reads them (the kit reads the installed `Jasper.semantic.*` roles), so they would be a second, unused colour map.
4. **`GeneratedTheme` becomes `(name, dark, author, semantic, terminal, editor, syntax, uiKeys, glass)`**: it gains the editor palette spec 4 lists and loses the legacy `tokens` and the always-empty `icons`.
5. **Route machinery stays** (spec 4): `ThemeRoute` CHROME/WORKSPACE, `ResolvedTheme`'s chrome and workspace, `WorkspaceTheme`/`ThemeZone`/`HostedUi`'s workspace paths. Slate Steel always matches, so tests of a controller that splits chrome and workspace are deleted; tests of hosting code that accepts any `ResolvedTheme` keep a mixed pair from the test helper.
6. **Calibration and custom renders are retired.** Previews and render tests paint Slate Steel Dark and Light only; `tools/visual/pairs.json` points at the Dark renders. The record's "Before" column is the last recorded value (calibration theme), "After" is Slate Steel Dark; edge agreement may fall where a mock's palette differs, and is recorded as measured.
7. **A test that needs "another theme" uses a recoloured Slate Steel palette** (`ThemeTestSupport.recolored`, `KitFixtures.custom`), never a test-only theme file.
8. **`WindowContent.selectVariant(boolean dark)` replaces `selectTheme(Theme)`** as the package-private switch tests and previews use (it configures the window's `ThemeController` as `ui.appearance` does, keeping the terminal scheme).
9. **`docs/themes.md` keeps a "Native acceptance for this upgrade (user-run)" section**, now Slate Steel's native checks, because `docs/STATUS.md` links its anchor.
10. **The popup shadow's strength moves from the theme's UI keys to `FlatLafInstaller`**, read from `DesignMetrics` (it duplicated `POPOVER_SHADOW_PERCENT_DARK/LIGHT`); the switch knob's shadow gets a metric.
11. **`ThemeGenerator` is deleted, not rewritten.** Spec 4 has it "rewritten to assemble a `GeneratedTheme` from the palettes"; `SlateSteel.generate` already does exactly that, so a second assembler would have no caller.

### The mock comparisons on Slate Steel Dark

The calibration column is the last value the calibration themes gave (re-rendered at `f65b40bb`, the end of Slate
Steel Plan 2); the Slate Steel column is the same owner on Slate Steel Dark. Notes explain each drop over 0.05.

| Reference (`reference/desktop-2026-10-10/` unless noted) | Surface (pair) | Calibration | Slate Steel Dark | Notes |
| --- | --- | ---: | ---: | --- |
| `settings_dialog_mock.png` | Settings Appearance (`settings-appearance`) | 0.865 | 0.843 |  |
| `settings_windows_mock.png` | Settings Window (`settings-window`) | 0.796 | 0.801 |  |
| `settings_notification_mock.png` | Settings Notifications (popup open) (`settings-notifications`) | 0.848 | 0.820 |  |
| `settings_keymap_mock.png` | Settings Keymap (recording) (`settings-keymap`) | 0.846 | 0.866 |  |
| `settings_plugins_mock.png` | Settings Plugins (`settings-plugins`) | 0.799 | 0.827 |  |
| `create_vault_dialog_mock.png` | Vault create hero (`vault-create`) | 0.949 | 0.965 |  |
| `rename_vault_dialog.png` | Vault unlock hero (`vault-unlock`) | 0.977 | 0.977 |  |
| `add_host_dialog_mock.png` | Add Host (`host-add`) | 0.890 | 0.925 |  |
| `new_file_server_mock.png` | New File Server (`fileserver-add`) | 0.906 | 0.923 |  |
| `vault_ui_mock.png` | Credential Vault manager (`vault-manager`) | 0.785 | 0.817 |  |
| `vault_ui_mock.png` | Credential Vault title row (`vault-manager-title`) | 0.648 | 0.648 |  |
| `Screenshot … 11.16.25 AM.png` | Workspace title bar, toolbar and tabs (`workspace-tabs`) | 0.796 | 0.820 |  |
| `Screenshot … 11.16.41 AM.png` | Workspace rails and status bar (`workspace-rails`) | 0.854 | 0.871 |  |
| `Screenshot … 11.16.50 AM.png` | Integrated workspace, four docked panels (`workspace-integrated`) | 0.770 | 0.823 |  |
| `Screenshot … 11.16.31 AM.png` | Command palette (`workspace-palette`) | 0.897 | 0.897 |  |
| `Screenshot … 11.16.50 AM.png` | Sessions panel (`sessions-panel`) | 0.964 | 0.963 |  |
| `reference/vault-add-menu.png` | Vault add menu (`vault-menu`) | 0.966 | 0.973 |  |
| `reference/add-login-sheet.png` | Attached Login sheet (`vault-login-sheet`) | 0.959 | 0.974 |  |
| `reference/add-ssh-key-sheet.png` | Attached SSH key sheet (`vault-key-sheet`) | 0.967 | 0.967 |  |
| `combo_box_mock.png` | Credential picker, empty (`picker-empty`) | 0.987 | 0.891 | The mock's highlight is `#4676EE` and its menu card `#2B2F37`; Slate Steel Dark's are `accent` `#4E7DB4` and `surface.menu` `#30343C`, so the highlight and the text on it give weaker luminance edges (`-side.png`); geometry unchanged. |
| `combo_box_mock.png` | Credential picker, selected (`picker-selected`) | 0.875 | 0.880 |  |
| `combo_box_mock.png` | Credential picker, open (face) (`picker-open-face`) | 0.879 | 0.881 |  |
| `combo_box_mock.png` | Credential picker, open (top) (`picker-open-top`) | 0.935 | 0.846 | The mock's highlight is `#4676EE` and its menu card `#2B2F37`; Slate Steel Dark's are `accent` `#4E7DB4` and `surface.menu` `#30343C`, so the highlight and the text on it give weaker luminance edges (`-side.png`); geometry unchanged. |
| `combo_box_mock.png` | Credential picker, open (footer) (`picker-open-footer`) | 0.987 | 0.987 |  |
| `combo_box_mock.png` | Credential picker, filtering (`picker-filtering-top`) | 0.985 | 0.844 | The mock's highlight is `#4676EE` and its menu card `#2B2F37`; Slate Steel Dark's are `accent` `#4E7DB4` and `surface.menu` `#30343C`, so the highlight and the text on it give weaker luminance edges (`-side.png`); geometry unchanged. |
| `combo_box_mock.png` | Credential picker, no matches (`picker-no-matches`) | 0.902 | 0.840 | The mock's highlight is `#4676EE` and its menu card `#2B2F37`; Slate Steel Dark's are `accent` `#4E7DB4` and `surface.menu` `#30343C`, so the highlight and the text on it give weaker luminance edges (`-side.png`); geometry unchanged. |
| `combo_box_mock.png` | Credential picker, disabled (`picker-disabled`) | 0.764 | 0.762 |  |
| `Screenshot … 11.16.50 AM.png` | Tunnels panel (`tunnels-panel`) | 0.948 | 0.947 |  |
| `reference/plugin-panels.png` | Tunnels panel (plugin-panels) (`tunnels-panel-plugin-panels`) | 0.948 | 0.948 |  |
| `Screenshot … 11.16.50 AM.png` | SFTP panel (`sftp-panel`) | 0.831 | 0.830 |  |
| `reference/plugin-panels.png` | SFTP panel (plugin-panels) (`sftp-panel-plugin-panels`) | 0.825 | 0.825 |  |
| `Screenshot … 11.16.50 AM.png` | File Servers panel (`fileservers-panel`) | 0.949 | 0.949 |  |
| `reference/plugin-panels.png` | File Servers panel, populated (`fileservers-panel-populated`) | 0.947 | 0.947 |  |
| `new_tunnel_mock.png` | New Tunnel (`tunnel-new`) | 0.909 | 0.916 |  |

### Light, inspected

Each family's Light render was opened beside its Dark render (Settings pages, Vault hero/manager/menu/sheets, Add Host,
New File Server, New Tunnel, the workspace, the palette, the credential picker and the four panels).

- Fixed: a selected row in a list outside the panels (the Vault item list, the Settings plugin list) kept
  `text.primary` and `text.secondary`/`text.tertiary` on `selection.bg`; on Light's `#5482B9` the subtitle was
  1.5:1 (secondary) and 1.1:1 (tertiary). Selected rows on `selection.bg` now take the on-accent roles with the text
  floor, as selected panel rows did (`AppManagerKitTest.aSelectedManagerRowsTextReadsOnTheSelectionInBothVariants`).
  White on Light's `selection.bg` is 3.98:1, so the floor gives those rows dark text while their glyphs stay white
  (a theme.md design note).
- Observed, not Light-specific: New File Server's three-way Login control wraps to two rows when the form shows its
  vertical scroll bar (344 instead of 354 logical wide); it is one row at natural height and in both variants.
- Authored values below the former floors (theme.md design notes): Light's off-switch track 1.68:1; Light
  `status.success` 3.32:1 and `status.warning` 3.76:1 on `surface.panel`; the split divider `border.window` 1.43:1
  (Dark) and 1.58:1 (Light) against the terminal; Light menu selection `text.onAccent` on `selection.bg` 3.98:1; the
  status bar's path slash `border.subtle` 1.19:1 (Dark) and 1.14:1 (Light).

## User-run checks still open

Screen capture and UI scripting are unavailable to the agent session, so these need a person at the Mac:

1. Compare each mocked page with its mock at 100% in Slate Steel Dark: Settings (all five pages), Vault
   create/unlock/manager/menu/sheets, Add Host, New File Server, New Tunnel, the credential picker, the four panels docked
   as in `Screenshot … 11.16.50 AM.png`, the title bar and status bar, and the command palette.
2. Native behaviour with Dark, Light and Match System followed live (switch the OS appearance with Settings and a sheet
   open), and each terminal scheme including High Contrast (the band below the grid, a tab meeting a black pane);
   attached sheets, popups and menus, focus rings and keyboard order, IME in fields.
3. The Dark active tab's title colour.
4. VoiceOver on the new descriptors (route strip, row switches, keycaps).
5. Windows and Linux packaging and a native pass there.
