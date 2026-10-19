# Visual reference comparison

Headless tools that compare actual Jasper renders with the preserved design references in
`docs/design/enterprise/reference/` (the Enterprise look) and `docs/design/theme-engine/reference/` (the default look).
The references are about 2x, so renders are captured at device scale 2 and never resized; only the Boot Splash frames
are 1x (720 × 440). Colours come from the active theme, so the geometry check compares luminance edges, not RGB
values. Run the tools with `python3 -I`; they need Pillow and NumPy. None of them is part of `./gradlew check`.

- `compare_mock.py MOCK RENDER OUT [--crop x,y,w,h] [--render-crop x,y,w,h] [--search N]` crops both images to the
  same logical area, searches render offsets up to `N` pixels each way (default 12) for the best edge alignment, and
  writes `OUT-side.png` (mock | render | edge overlay: mock red, render cyan, shared white), `OUT-blend.png` (a 50%
  blend of the aligned pair) and `OUT.txt` (the best offset, the edge agreement and the agreement per 40-row band).
- `region.py MOCK RENDER OUT --crop … [--render-crop …] --region x,y,w,h [--offset dx,dy] [--grid N]` cuts the same
  content region from both at original pixels into one image, side by side when the region is tall and stacked
  otherwise. `--offset` shifts the render's region; `--grid` sets the spacing of the magenta tick marks (default 20 px,
  0 for none).
- `compare_all.py OUT_DIR [FILTER]` runs `compare_mock.py` for every pair in `pairs.json` whose name contains `FILTER`,
  prints one summary line per pair (and its `note`, which names a deliberate difference), and reports a missing render
  as `MISSING`.
- `check_pairs.py OUT_DIR [FILTER]` runs every pair that has a `min_agreement` and prints PASS or FAIL: the edge
  agreement must reach the floor, and each colour in the pair's `colours` must appear within ΔE76 1.5 in the render, as
  a 2×2 block or a three-pixel line (the mock is not searched: the colours are the theme's). A missing render fails. It
  exits with status 1 when any pair fails.

`pairs.json` lists each pair as `name`, `mock` and `render` (paths from the repository root), `crop`, and optionally
`render_crop`, `note`, `min_agreement` and `colours`. Regenerate the renders before comparing:

| Pairs | Renders under | Produced by |
|---|---|---|
| `settings-appearance`, `settings-window`, `settings-notifications`, `sessions-*`, `sftp-*`, `tunnel-*`, `tunnels-*`, `fileservers-*`, `vault-create`, `vault-unlock` | `jasper-app/build/theme-surfaces/` | `./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview` |
| `settings-keymap`, `settings-plugins` | `jasper-app/build/theme-surfaces9b/` | `./gradlew :jasper-app:settings9bPreview` |
| `workspace-tabs`, `workspace-rails`, `workspace-integrated` | `jasper-app/build/workspace-surfaces/` | `./gradlew :jasper-app:workspaceSurfacePreview` |
| `workspace-palette` | `jasper-app/build/palette-surfaces/` | `./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"` |
| `picker-*`, `host-add`, `fileserver-add` | `jasper-app/build/theme-migration-evidence/` | `StagedAnchoredPickerTest`, `StagedAddHostTest`, `StagedFileServerFormTest` |
| `vault-manager`, `vault-manager-title`, `vault-menu`, `vault-login-sheet`, `vault-key-sheet` | `jasper-app/build/task-4b-captures/` | `VaultSheetRenderTest` |
| `classic-*` | `jasper-ui/build/gallery/` | `./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light -Pjasper.galleryScale=2` |
| `run-window-*` | `jasper-app/build/run-window/` | `RunWindowRenderTest` |
| `enterprise-light-*`, `enterprise-dark-*` | `jasper-app/build/enterprise-kit/` | `EnterpriseKitRenderTest` |
| `chrome-*` | `jasper-app/build/chrome-renders/` | the `Enterprise*MockTest` classes (`./gradlew :jasper-app:test --tests '*Enterprise*MockTest'`) |
| `dock-*` | `jasper-app/build/dock-renders/` | `DockMockTest` (`./gradlew :jasper-app:test --tests '*DockMockTest'`) |
| `composition-*` | `jasper-app/build/enterprise-compositions/` | `ClassicCompositionRenderTest`, `ClassicVaultRenderTest` |
| `enterprise-settings-*` | `jasper-app/build/enterprise-settings/` | `SettingsChromeMockTest`, `SettingsPagesMockTest` |
| `enterprise-splash-*` (1x) | `jasper-app/build/enterprise-splash/` | `BootSplashMockTest` |

Run a named test with `./gradlew :jasper-app:test --tests '*<Name>'`. The pairs with a `min_agreement`, which
`check_pairs.py` runs, are the `classic-*`, `run-window-*`, `chrome-*`, `enterprise-light-*`, `enterprise-dark-*`,
`dock-*`, `composition-*` and `enterprise-settings-*` pairs. `ChromeRenderTest` also writes `chrome-*.png` files under
`jasper-app/build/chrome-renders/` for inspection; no pair uses them.

A high edge agreement is not acceptance: inspect the region crops, and record native font rasterization and platform
title-bar differences separately.
