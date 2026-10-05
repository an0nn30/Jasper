# Theme-engine calibration

The nine [original references](reference/README.md) are unchanged. Measurements below use original
pixels, excluding shadow/native title bars, never the resized chat preview. Crop tuples are
`x,y,width,height` in source pixels. Divide by the established 2× source scale for logical geometry.
`KitGallery.REFERENCES` retains these crop records; generated PNG/JSON files use kit controls.

| Original | Original dimensions | Content crop | Multiple scale anchors (source pixels → logical pixels) |
| --- | --- | --- | --- |
| [Add host](reference/add-host.png) | 1188×1600 | 48,100,1080,1464 | Default control 52 interior + 2 border each side = 56 → 28; compact setting switch 64×36 → 32×18; card inset 28 → 14 |
| [Unlock vault](reference/unlock-vault.png) | 856×672 | 62,112,760,528 | Hero field 60 interior + 2 border each side = 64 → 32; hero tile 104 → 52; action height 60 → 30 |
| [New file server](reference/new-file-server.png) | 1134×1560 | 30,74,1080,1460 | Account/reachability control interiors 52 + borders = 56 → 28; setting switch 64×36 → 32×18; card inset 28 → 14 |
| [Create vault](reference/create-vault.png) | 874×974 | 44,94,800,856 | Hero field outer 64 → 32; tile 104 → 52; action 60 → 30 |
| [Credential vault](reference/credential-vault.png) | 1860×1244 | 38,114,1800,1110 | Detail tile 88 → 44 and list tile 56 → 28; detail inset 48 → 24; manager columns 384/544 → 192/272 |
| [Vault add menu](reference/vault-add-menu.png) | 716×518 | 160,140,444,274 | Category tile 36 → 18; row step approximately 58 → 29; popup surface width 444 → 222 (natural text width varies by host font) |
| [Add login sheet](reference/add-login-sheet.png) | 1872×1240 | 434,106,964,730 | Fields outer 56 → 28; category tile 64 → 32; card inset 28 → 14, column gap 24 → 12 |
| [Add SSH key sheet](reference/add-ssh-key-sheet.png) | 1878×1268 | 446,120,964,614 | Fields outer 56 → 28; category tile 64 → 32; card inset 28 → 14, column gap 24 → 12 |
| [Plugin panels](reference/plugin-panels.png) | 2810×2026 | 168,144,520,1686 (left-column content envelope) | Plain folder artwork 28 → 14; compact footer switch full outer 56×32 → 28×16 (exact-color interior 54×28); Connect full height about 44 → inline 22 (exact-color interior 42) |

The panel image is independently anchored by its plain glyph, compact switch and inline action;
its 2810×2026 dimensions alone do not establish scale. Individual source panel content regions are:
Sessions `168,144,520,838`; Tunnels `168,994,520,836`; SFTP `2122,144,520,838`;
File Servers `2122,994,520,836`. Sessions has its separator at y808 and footer y809–981;
SFTP body ends at y901 and footer is y904–981. These flat-content bounds include the full footers. The gallery uses logical 264×424/426 hosts (source flat content is 260×419/418), documenting
this small host-size difference rather than treating the source envelope as a control metric. Header,
empty/body and pinned footer behavior can be compared independently. The Sessions image has a detail footer;
SFTP has a settings footer. Empty Tunnels uses an ordinary action, empty SFTP a dropdown action.

Raw flat-color interior regions were supporting evidence. Interior bounds exclude antialiasing and
border pixels; e.g. the source default field's 52-pixel interior is not a 26-logical-pixel footprint.
Crop boundaries omit native chrome; the gallery supplies the semantic host background behind the
manager and forms. The first two full forms begin after the native title bar, hence the source crop
starts at y100/y74 rather than the first section caption. Transparent native shadows do not define
control radii or content padding.

## Corrections established by rendered regressions

Real FlatLaf is installed before any fixtures or local controllers are constructed. Captures scale
logical dimensions once through UIScale and use device transform 1. Headless native keyboard-lock
queries are suppressed; popup artwork is painted directly without opening a window. Focus/caret
listeners and all views/controllers/bindings/registries close normally.

Nominal 28/32 control heights are visible fill/border bounds. Focus/error rings are external 3-pixel
view-owned overlays, clipped at viewport boundaries, without shrinking the fill or moving rows.
The original radii are radii; Java RoundRectangle arc arguments use twice those values. Tests check
unfocused/focused fill dimensions and visible outer halo at 1×/2× under actual FlatLaf. Placeholder
ownership is the kit alone. HERO labels and embedded card segments paint once; settings choices
use the named 160-pixel trailing role. Ordered keys use the input surface, separators and leading
add action. Picker cue/alignment is explicit. Hidden panel headers consume zero height; manager
columns and pinned right footer action fill their semantic host. DETAIL has 24-pixel padding.
FormRow helper captions wrap, keeping the full account explanation readable.

Calibration exposed two small API gaps. `Choice.icon` names PICKER artwork, with empty meaning no
cue; the original constructor retains its key default. `MenuItem.hue` supplies category hue explicitly,
with the original constructor defaulting NEUTRAL. Both mirror through SDK/host values and are tested.
The source fixture chooses BLUE/GREEN/AMBER/PURPLE, never icon-name or text-based colors. Category
tile metrics follow explicit parent/size/list roles (20/28 rail/list, 32 sheet, 44 detail); ordinary
folder glyphs remain plain. Fixture-only custom artwork is registered through the public registry.

## Capture matrix and inspection

Each run writes 46 PNG captures, 46 JSON bounds/state files, a manifest and six full-resolution
sheets to `jasper-ui/build/gallery`. The inspected matrix on 2026-10-04 is:

| Theme | Scale | Sheets | Purpose |
| --- | --- | --- | --- |
| dark | 1×, 2× | 1–6 each | Active semantic color routing and logical scaling |
| light | 1× | 1–6 | Contrasting light surfaces and controls |
| custom | 1× | 1–6 | Distinct authored colors through the same geometry |
| calibration | 1×, 2× | 1–6 each | Source Appendix A semantic colors for reference inspection |

The matrix covers normal/hover/focus/pressed/selected/disabled/error/busy/conditional/empty,
filtered-empty and motion-disabled states; all 26 node types and enum variants. Panel transitions
come from four working local controllers: filtered Sessions, added Tunnel, connected SFTP and short
SFTP at enlarged font, plus confirmed/pending/failed and added-server pending File Server states.
The short SFTP capture preserves reachable rows and footer with real FlatLaf scrollbars.
JSON stores rendered bounds/actual enabled/focus and typed busy/error/visibility state, excluding
values/secrets. An actual-LAF route test compares contrasting chrome/workspace accent pixels.

Inspect every sheet, then critical individual crops at native resolution. Fix components/fixtures
rather than thresholds. Source colors are an explicit calibration test resource; production renderers
always use the active semantic palette. Theme colors therefore deliberately differ in other runs.
Native window/sheet chrome, OS font rasterization and live native keyboard focus are not pixel-golden
assertions. Popup natural width and text wrapping can differ with the central host font; the source
crop is retained as evidence, not stretched to conceal differences. Plan 3 still performs full
application surface composition and screenshot matching after Plan 2 consumer migration. Plan 1's
single final whole-branch review remains Task 14; this gallery is headless kit evidence.

See [architecture](../../ui-architecture.md) and [maintenance](../../ui-maintenance.md).


## Combined final-review fixture extension

The catalog now contains 49 fixtures: stored-secret masked/revealed states and the actual dropdown
popup/list join the existing 46. Credential Vault transfers harmless local characters into its
read-only detail, with trailing reveal/copy, rather than depicting an empty editable password input.
Neither descriptors nor metadata contain the transferred value. Popup capture recursively lays out
the scroll viewport/list; zero-size popup lists are rejected by a regression. The six-run matrix is
regenerated after shared theme/radius fixes; new geometry uses full named radii at both scales.
Scoped rereview and user/native artifact acceptance remain pending.

Final fix matrix: dark 1x/2x, light 1x, custom 1x and calibration 1x/2x each produced
49 fixture PNGs, 49 fixture JSON files, seven contact sheets and one manifest (106 files/run;
636 total). Metadata audit confirms 26 node kinds, requested scales, device scale 1, actual
FlatLaf, motion disabled, nonzero dropdown list bounds and no transferred sample secret in JSON.
Representative changed surfaces inspected at native resolution include calibration/light sheet 6,
calibration 2x and dark/light/custom 1x open dropdowns, calibration Credential Vault, Add SSH Key
and context menu, and dark 2x revealed stored secret. These checks cover selected popup fill,
read-only secret masking/reveal and inset, semantic colors, radii and context hue. They do not
claim fresh visual inspection of all 42 contact sheets or native application acceptance.
