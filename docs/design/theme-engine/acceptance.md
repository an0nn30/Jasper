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

- macOS draws the window frame, traffic lights, title-bar and window shadow; renders leave that space and never paint
  counterfeit controls.
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

## User-run checks still open

Screen capture and UI scripting are unavailable to the agent session, so these need a person at the Mac:

1. Open each mocked page in the running app and compare it with its mock at 100%: Settings (all five pages),
   Vault create/unlock/manager/menu/sheets, Add Host, New File Server, New Tunnel, the credential picker, the four
   panels docked as in `Screenshot … 11.16.50 AM.png`, the title bar and status bar, and the command palette.
2. Native behavior: attached sheets follow their window, popups and menus place correctly, focus rings and keyboard
   order, IME in fields, live theme switching with open dialogs, light theme.
3. VoiceOver on the new descriptors (route strip, row switches, keycaps).
4. Windows and Linux packaging and a native pass there.
