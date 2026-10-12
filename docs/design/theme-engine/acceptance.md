# Theme engine Plan 3 acceptance record

Plan 3 Task 11's reference → fixture → output → review mapping, written 2026-10-12 when the last surface workstream
merged into `codex/theme-engine`. Authority: the [theme engine specification](../../superpowers/specs/2026-10-04-jasper-theme-engine-design.md)
(sections 8 and 10), its 2026-10-10 reference-completion amendment (all 16 Desktop references), and the user's
2026-10-11 instruction for this round: the mocks fix every non-color detail, colors come from the active theme,
component looks live in `jasper-ui` (exposed to plugins through the SDK) and layout lives in the owning plugin or app.

## How the evidence is made

- Every render is the **actual owner** (staged plugins in a disposable home, the real app windows and Settings pages)
  painted headlessly at device scale 2, at the logical size the reference crop implies. Nothing is resized.
- Calibration themes (`jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-*.toml`) approximate the mock
  colors only so that edges are comparable; production uses real themes, and light/custom/enlarged renders of every
  family were inspected too.
- [`tools/visual`](../../../tools/visual/README.md) compares luminance edges (`compare_mock.py`), cuts matching
  regions at original pixels (`region.py`) and runs all 33 recorded pairs (`compare_all.py`, `pairs.json`).
  The edge agreement is a guide; each stream measured bounds, insets, baselines, control sizes, radii and glyphs with
  pixel probes, and recorded what still differs.
- Regenerate: `./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview
  :jasper-app:workspaceSurfacePreview`, `./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"`,
  `./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest'`,
  then `python3 -I tools/visual/compare_all.py <outdir>`.

## Mocked surfaces

Edge agreement at the start of this round → at merge (calibration theme, device scale 2). Stream notes hold the
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
- Colors follow the active theme (the user's rule); calibration themes only make comparisons legible.

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

- Opacity is applied once: scrims are painted as their token gives them (legacy default black at 55%/25%); the focus ring
  applies 32%/25% of `border.focus`, the error ring 22%, tiles and chips 17%/13% of their hue surface.
- The title bar's command field is a raised field (hover changes only its edge); selected toolbar actions, rail items and
  manager-rail rows take `surface.control.selected`; tool windows sit on `surface.panel`.
- The tab strip is `surface.tabstrip`, the selected tab `surface.terminal`, at the window's alpha; hovered inactive tabs
  take the hover overlay.
- Secondary buttons take `button.secondary.border` and `text.strong.secondary`; the segmented track `segment.track` with a
  `border.input` edge; panel empty tiles `surface.emptyTile`.
- Captions, counts, table headers and row subtitles are `text.tertiary`; Settings row titles and the current breadcrumb
  `text.strong.secondary`; empty picker values `text.placeholder`; the idle status dot `status.idle`.
- The command palette is a `surface.menu` card with a `border.menu` hint rule and an accent highlight; menus highlight with
  the accent; highlighted subtitles take `text.onAccent.secondary`, checkmarks `text.link`; the Settings body is
  `surface.dialog` and the file chooser's Places `surface.sidebar`.
- theme.md's shadows, black: the modal overlay's card, the palette, FlatLaf menus, the Light selected segment and the
  primary button (a white inset line on its top row and a 1 px drop below the face).

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

## User-run checks still open

Screen capture and UI scripting are unavailable to the agent session, so these need a person at the Mac:

1. Open each mocked page in the running app and compare it with its mock at 100%: Settings (all five pages),
   Vault create/unlock/manager/menu/sheets, Add Host, New File Server, New Tunnel, the credential picker, the four
   panels docked as in `Screenshot … 11.16.50 AM.png`, the title bar and status bar, and the command palette.
2. Native behavior: attached sheets follow their window, popups and menus place correctly, focus rings and keyboard
   order, IME in fields, live theme switching with open dialogs, light theme.
3. VoiceOver on the new descriptors (route strip, row switches, keycaps).
4. Windows and Linux packaging and a native pass there.
