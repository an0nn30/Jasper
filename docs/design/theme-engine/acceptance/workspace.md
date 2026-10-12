# Workspace stream: Plan 3 Task 10 chrome integration and palette residuals

Headless evidence only. No native window, login shell, traffic lights or native acceptance is implied; the
macOS window frame, traffic lights, window shadow and native text rasterization are the accepted differences.

## References and captures

| Reference (2x, `reference/desktop-2026-10-10/`) | Content crop | Actual capture (device scale 2) |
| --- | --- | --- |
| `Screenshot 2026-10-06 at 11.16.25 AM.png` (two zsh tabs, rails/status hidden) | 112,76,1582,1424 | `jasper-app/build/workspace-surfaces/tabs/calibration-791x712.png` |
| `Screenshot 2026-10-06 at 11.16.41 AM.png` (rails and status visible) | 112,76,1582,1424 | `jasper-app/build/workspace-surfaces/rails/calibration-791x712.png` |
| `Screenshot 2026-10-06 at 11.16.50 AM.png` (integrated Sessions/SFTP + File Servers/Tunnels) | 112,76,2684,1716 | `jasper-app/build/workspace-surfaces/integrated/calibration-1342x858.png` |
| `Screenshot 2026-10-06 at 11.16.31 AM.png` (command palette) | 342,84,1120,712 | `jasper-app/build/palette-surfaces/user_palette.toml-13.0-791x712.png`, render crop 230,8 |

The file names contain U+202F before `AM`. `tools/visual/pairs.json` lists the four pairs as `workspace-tabs`,
`workspace-rails`, `workspace-integrated` and `workspace-palette`.

`WorkspaceSurfacePreview` (test sources, `:jasper-app:workspaceSurfacePreview`) renders the actual `WindowContent`
with its `MacTitleBar` header, equal-width tabs, stripes, dock and status bar. Shells are controlled `zsh -f` (or
`/bin/sh`) children printing a truecolor two-line fixture prompt with OSC 7 and OSC 133 marks, redrawn on SIGWINCH;
headless views are asked to fit their laid-out grid before capture. `WorkspacePanelsFixture` (plugins test package)
starts the staged Vault (created and locked with a synthetic password), Remote (synthetic hosts through its real
store, no network or agent) and File Server (synthetic share, never listens) in one plugin host; the window mounts
their panels and rail buttons through its ordinary contributions path, plus the Notifications rail registration.
The calibration theme is `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-workspace.toml` (chrome
`#25292f`, terminal `#222629`, panel `#191c20`, outline `#343a42`; colors are not required to match).

Fixture-only data: user `dustin`, `~/Desktop/Screenshots` under a disposable home, host rows, and the reference user's
saved values as a real `ConfigSnapshot` (toolbar height 34, tab height 28, status bar height 28, terminal font
13.5 pt with line height 0.88 so the grids read the references' `90 × 40` and `93 × 50`). Real defaults are unchanged.

## Regeneration

```
./gradlew :jasper-app:workspaceSurfacePreview            # all scenarios; -Pjasper.workspaceFilter=tabs|rails|integrated
./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"
python3 -I tools/visual/compare_all.py /tmp/workspace-compare workspace-
python3 -I tools/visual/region.py MOCK RENDER OUT.png --crop 112,76,2684,1716 --region 1900,0,784,70
```

Additional captures (same directory): 1x, narrow 460×320, enlarged 20 pt UI font, long working directory and tab
title (normal and 520×360), Jasper Dark and Jasper Light, light chrome around a dark workspace, hidden rails,
collapsed panels (only Sessions shown) and Zen mode. Each PNG has a `.txt` beside it with the actual component
bounds. All were inspected: no clipping or overlap; long text ellipsizes in the command centre, tabs and breadcrumbs.

## Measured result (render coordinates + crop origin = reference coordinates, device px)

| Element | Reference | Actual | Difference |
| --- | --- | --- | --- |
| Title row | y76–143 (34 logical) | y76–143 | 0 |
| Command centre field | x502–1300, y84–135, outline `#343a42` | same | 0 |
| Search / terminal glyph / context text / `:` / `zsh` / `⇧⌘P` (16.25) | 522–545, 564–591, 607–963, 972–974, 985–1020, 1226–1283 | identical extents | 0–1 px |
| Toolbar `+`, new window, split, `Split`, chevron, expand, globe, `Sessions`, chevron, search, gear (16.50) | 2152, 2218, 2278, 2315–2361, 2372–2379, 2412, 2472, 2511–2606, 2618, 2660, 2723 | identical | 0 |
| Toolbar separators | x2262, 2458, 2640, y90–129 | identical | 0 |
| Tab strip | y144–197 + 2 px rule, divider x902–903 | identical | 0 |
| Tab titles `zsh` | 487–527, y162–182 | 488–526 | ≤1 px (rasterization) |
| Rails | 56 px; globe y160–187, folder y230–253; bell y160–188, server y214–237, tunnel y280–307 | identical | 0–1 px |
| Terminal surface / panels | corner radius 8 logical, gutters 12 px between frames, none at the window edges | identical | 0 |
| Integrated frames | rail 112–167, panels 168–687 and 2220–2739, gaps 688–699 and 2208–2219, terminal 700–2207 | identical | 0 |
| Status bar | y1736–1788; dot 140–149; `zsh ●` 165–220; folder 280–307; crumbs 382–473, 519–659; lock 2388–2407; `Vault` 2424–2474; `93 × 50` 2505–2575; `Config loaded` 2633–2766 | identical | ≤1 px |
| Palette query glyph / placeholder | 376–399 y118–141 / 412 | identical | 0 |
| Palette headings `Recently used` / `Most used` | 362–501 / 362–467, y187–207 / 327–343 | 362–506 / 362–467 | `Recently used` +5 px (2.5 logical) advance |
| Palette footer detail / verbs | 369–978, 1091–1276, 1307–1434 | 369–982, 1090–1279, 1308–1432 | ≤4 px (≤2 logical) advance |
| Palette tags `moray`, `⌘O`; row icons | 1379–1442, 1408–1441; 362–389 | 1378–1442, 1406–1441; identical | ≤2 px |

Edge agreement (`compare_all.py`): tabs 0.780 → 0.807, rails 0.788 → 0.862, integrated 0.553 → 0.581,
palette 0.873 → 0.897. Title/toolbar bands reach 0.96–0.97; the rest is terminal glyphs (nerd-font prompt symbols are
fixture placeholders), the native window frame and shadow inside the crop, the title-field fill colour, and panel
content owned by the panels stream (below).

## Changes and decisions

- Typography roles (anchor after `PICKER_COMPACT`): `TITLE_CONTEXT` 12, `TOOLBAR_LABEL` 11.5, `STATUS_TEXT` 10,
  `STATUS_PATH` 12, `RESULTS_HEADING` 11, `PALETTE_HINT` 11. They reproduce the references' measured advances (the
  references were captured with the former 12 pt chrome label font); the title field, toolbar labels, status
  segments/items/progress and breadcrumbs, RESULTS headings and the palette footer use them.
- Native chrome artwork: the header, both stripes and the status bar are receiving contexts
  (`AppIcons.chromeReceiver`); painted named glyphs there use `IconRegistry.chromeIcon`, which admits twenty
  byte-identical original SVGs (`assets-chrome.tsv`, recovered from `main` with matching SHA-256) for add, new window,
  split, expand, chevron down, folder, server, bell, lock and terminal. Icon values and identities are unchanged;
  menus, panels, tiles, descriptor views and plugin registrations keep their ordinary glyphs. This extends the
  approved Task10a original-artwork pattern to the meanings the workspace references depict.
- Kit painters (`NativeChromePainting`): tab strip and tab surfaces, toolbar rules and action widths, status running
  dot, rail badges, tool window connected dot, split dividers, dock drop guides, find bar hairline, title-bar edges and
  the palette shadow, over new named metrics (anchor after `TAB_OPEN_MILLIS`): `TOOLBAR_BUTTON_TRIM` 6,
  `TOOLBAR_ACTION_ICON` 16, `TAB_SELECTED_LIFT_DARK/LIGHT` 35/120 ‰, `TAB_IDLE_SHADE_DARK/LIGHT` 300/60 ‰,
  `NATIVE_HOVER_OVERLAY_PERCENT` 8, `PALETTE_SHADOW_SPREAD` 12, `PALETTE_SHADOW_ALPHA_DARK/LIGHT` 4/2,
  `PALETTE_WINDOW_MARGIN` 16, `PALETTE_HINT_ACTION_GAP` 12, `TRANSPARENT_FIELD_ICON_GAP` 4. The values are the former
  owner literals; all sixteen workspace captures were byte-identical before and after the move.
- Palette: RESULTS headings use `RESULTS_HEADING` for text and measured height; the footer stack takes the host-only
  `KitView.setHintPresentation` face; the transparent query uses the rail-size search glyph with a 4 px gap, keeping
  the placeholder origin. Ranking, scopes, keyboard routing, selection and step behaviour are unchanged.
- Preserved: equal-width tab sizing, motion curves and timing roles, docking and drag, Zen/view modes, glass routes,
  native title-bar insets (no painted window controls) and terminal behaviour. `MacTitleBar`'s native safe insets and
  the auxiliary dialog title font remain platform-owned values.

## Remaining differences

- Native versus headless text advances: long palette details and the selected row run up to about 2 % wider than
  the reference; `Recently used` is 2.5 logical px wider. Sizes and baselines match.
- Colours: the command centre fill uses `surface.input` (the reference fill is almost the chrome colour); tab and rail
  shades follow the theme roles. Colours are not required to match.
- Terminal: prompt symbols (Apple logo, folder) are fixture placeholders; the cursor and terminal glyphs are terminal
  rendering, out of scope.
- Native window frame, traffic lights and shadow inside the crops (rows 1680–1716 of the integrated band, the left
  and right crop columns) are platform-owned.

Panel content seen in the integrated capture (panels stream; kit Panel/PanelHeaders/PanelHostHeader not changed here):
empty states are top-aligned under the header while the reference centres them in the body; the empty tile is 40
logical with a larger glyph (reference 36 with a 12 logical glyph); File Servers' `Share Folder…` shows a `+` glyph
the reference omits; empty-state descriptions wrap wider than the reference; header `⋯` dots are dimmer; the Sessions
header import action uses a tray glyph where the reference shows a corner arrow; SFTP's `Choose Session` chevron leads
rather than trails; the SFTP `Cancelled` status row and the `Follow terminal folder` footer spacing differ.
