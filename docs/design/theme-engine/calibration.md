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
Scoped rereview is complete with one known Minor expression-parity gap; user/native artifact acceptance
remains pending. See the [final review record](verification.md).

Final fix matrix: dark 1x/2x, light 1x, custom 1x and calibration 1x/2x each produced
49 fixture PNGs, 49 fixture JSON files, seven contact sheets and one manifest (106 files/run;
636 total). Metadata audit confirms 26 node kinds, requested scales, device scale 1, actual
FlatLaf, motion disabled, nonzero dropdown list bounds and no transferred sample secret in JSON.
Representative changed surfaces inspected at native resolution include calibration/light sheet 6,
calibration 2x and dark/light/custom 1x open dropdowns, calibration Credential Vault, Add SSH Key
and context menu, and dark 2x revealed stored secret. These checks cover selected popup fill,
read-only secret masking/reveal and inset, semantic colors, radii and context hue. They do not
claim fresh visual inspection of all 42 contact sheets or native application acceptance.

## Plan 3 Task 1 — actual Vault owners, Desktop reference family

The authorized 2026-10-10 set adds sixteen hash-preserved originals under
[reference/desktop-2026-10-10](reference/desktop-2026-10-10/manifest.json). Existing nine references
remain for states without a newer overlap. Create and Unlock use the Desktop originals as primary.

`./gradlew :jasper-app:themeSurfacePreview` opens the real staged Vault plugin through PluginHost,
using a disposable fixture home and unbound synthetic vaults. It writes 44 actual-owner captures
under `jasper-app/build/theme-surfaces` (calibration, dark, light and custom; Create empty/focus/
entered/revealed/mismatch/busy and Unlock empty/entered/revealed/busy/error). No Window is created.
The fixture settles the real switch animation; only empty Create's depicted binding is switched on,
and no depicted bound state is submitted. Synthetic focus is explicitly headless evidence.
The fixture restores focus-manager state, closes owners and wipes/deletes its temporary home.

The native-installable fixture palette is
`jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-hero.toml`, named **Desktop Hero
Calibration**; custom counterpart is `custom-hero.toml`. Install through the normal theme catalog
in an isolated QA home. Colors are theme semantic overrides, never feature-local paint. Display
profile source pixels are converted to common sRGB for analysis; dominant dialog #1f232b,
input #14161c, card #1b1d22, hero tile #2b3240, primary #e6e8ec and accent #3478f6 are explicit
fixture evidence rather than production defaults.

Source scale is 2×, independently established by fields, switch and card anchors. Candidate
Java2D device scale is 2× with logical UI scale 1; images are never resized. Normal mounted
preferred content sizes are Create400×428 and Unlock380×264 logical. Busy/error capture height
follows the actual preferred size, as native DialogFit does. The exact analysis crops are Create
original820×912 at [10,46,800,856], Unlock original770×578 (measured frame extent764×568) at [2,38,760,528]. These are content
anchor comparisons, **not whole-window matches**: traffic-light bottoms occur at their top edge.
Actual full-frame reference interior is Create[10,8,800,894]; Unlock[2,0,760,566] has a cropped top.

Shared measured composition amendments are HERO_COMPACT380, CONFIRMATION secrets (group reveal,
no separate eye or hidden AX child), and optional SettingRow.titleNote (tertiary annotation,
backward-compatible constructors). The tile outer footprint is54×54 logical; the52×52 measurement
in the initial audit was its interior. Shared HERO optical icon stroke is1.75logical at24px;
ordinary control icon strokes and the16-grid registration contract remain. An actual native
400px minimum imposed by title-only MacTitleBar was reproduced by a mounted-root regression and
removed; workspace preference remains400. HERO semantics hide the duplicate title and share
SURFACE_DIALOG without a divider. Native controls remain genuine JBR controls.

Controller's immutable WIP-A bundle passed actual readiness/reveal/theme/focus and synthetic
create/unlock behavior, while exposing the400px clamp and contrasting titlebar. Manager-inline
Unlock remains the separate Task4 surface. Controller native WIP-B confirmed the HERO20 title height, full visible native controls, unified
chrome and compact width; normal28/workspace behavior is retained. Native acceptance and final
regular-face calibration findings are recorded below and with the Task1 implementation report; headless
captures alone do not accept native control clearance, dragging, focus or full-frame matching.


Final mounted text calibration compares installed HN Regular, HN Light and Apple regular at identical
physical scale, preserving each unresized capture. HN Light was rejected (description554px versus
reference572, placeholder190 versus203). Apple regular improved translated placeholder mask overlap
61.7%→78.6% and setting title44.6%→88.6%; description results were mixed (Create72.6% essentially
tied, Unlock75.7%→67.7%). The coherent macOS default uses Apple regular roles, HN Medium600 with
.018 tracking, DESCRIPTION12.5/18px line spacing, CAPTION11.5, and SETTING_DESCRIPTION11. Explicit
user families and proportional sizes remain authoritative. This reduces regular ink-mass drift from
24–32% to16–18%; it does not identify the authored source font or waive remaining differences.

Final normal logical anchors (the harness also records exact first painted baselines and font faces):

| Anchor | Create | Unlock |
| --- | --- | --- |
| Preferred content | 400×428 | 380×264 |
| Tile outer x,y,w,h | 173,19,54,54 | 163,19,54,54 |
| Title x,y,w,h; baseline | 155,87,89,19;102 | 144,87,91,19;102 |
| Description x,y,w,h; first baseline | 28,110,344,36;123 | 28,110,324,18;123 |
| Password | 28,164,344,32 | 28,146,324,32 |
| Meter / confirmation | 28,202,280,3 / 28,215,344,32 | absent |
| Bind card | 28,271,344,54 | absent |
| Warning | 28,339,344,15 | absent |
| Equal actions | x28/204,y374,w168,h30 | x28/194,y210,w158,h30 |

Measured original/candidate text ink boxes in source pixels (alpha threshold0.2, common sRGB,
not arbitrary cropped screenshot bounds): Create title [313,181,175,24] / [311,181,175,24]; first
description [114,227,572,24] / [111,227,575,24]; second [333,264,133,19] / [331,263,134,19];
placeholder [80,350,203,24] / [78,350,205,25]. Unlock title [292,181,178,24] / [290,182,178,23];
description [68,228,623,24] / [66,227,626,24]. Shield [383,71,34,42] / [383,70,34,42]; lock
[363,72,34,40] / [363,72,34,41]. These small residual shape/position differences remain recorded,
not declared pixel acceptance. Shared HERO stroke is1.75logical; ordinary24px stroke2.25 and
14px control stroke1.3125 remain covered by alpha-width tests.

Native WIP-B additionally exposed a stale narrow viewport after wrong-password reflow. The real
mounted regression deliberately lays out the error at the old height before repacking. It failed
with a retained vertical scrollbar; Form Body now measures current scroll allocation rather than
its stale scrollbar-narrowed width, and repacks with the visible error at380×304. Controller native
C recapture confirmed760×648 source pixels with the message fully visible, one-line description,
no scrollbar, input cleared and focused, and disabled primary; correct-password recovery passed. Native captures are JPEG; common-space
analysis records compression limits, and lossless actual-owner PNGs provide exact paint evidence.


Final shared SettingRow optical alignment uses13logical side padding and aligns the painted switch
track, accounting for its3logical halo reserve only where row padding can contain it. The prior
painted right inset17 displaced the switch8 source pixels left of the reference. A RED regression
measured17 versus13; default/enlarged13/20pt tests cover wrapped annotations, actual focus-ring paint,
toggled state and retained control identity. Zero-padding panel footers retain focus containment.
This is a shared row policy, with no Vault-specific position. It changes production after native C
and requires the controller's final committed-snapshot comparison.

Retained native C common-sRGB color evidence: dialog reference(31,35,43) versus JPEG-native(30,35,45),
input exact(20,22,28), card exact(27,29,34), secondary reference(45,50,60) versus(45,50,61), disabled
primary reference(40,73,134) versus(37,70,127). JPEG/profile differences and disabled-opacity policy
must be distinguished during final cross-family calibration; no monitor compensation or blind palette
adjustment was made. Remaining font ink differences likewise remain named review work, not a waiver.


Final switch mask reference/candidate is identical at[654,578,64,36]source pixels. The controller's
size-only provenance audit also found that system13 could replace HN Medium headings with SFNS:
actual ThemeController RED/GREEN now covers system13→20→default, mounted BODY/HERO and native
adapter roles, plus explicit Helvetica Neue/Serif. KitContext and NativeControlStyles use the
existing Jasper.uiFontFamilyOverride flag to preserve default role policy at a custom size; they
never infer an explicit choice from a font name. This follow-up changes production after native C
but leaves normal default-size captures unchanged.


### Task1 review round 1: exact font points and supporting text

Controller expanded Task1 to fix inherited C1 after actual ThemeController system20 produced native JLabel20pt but kit/native-adapter30pt at auto UIScale1.5. Host overrides now supply effective point sizes without a second font-derived UIScale multiplication; role ratios remain proportional. With no override, catalog typography still receives UIScale once. Geometry continues to use UIScale; Java2D device transforms remain untouched. Explicit size20 with forced uiScale2 remains20pt, matching native JLabel, while default catalog BODY at forced2 is26pt. Real default→system20→explicit Serif20/Helvetica Neue18.5→default tests exercise mounted and adapter sizes/families under automatic and forced2 scale. Description line spacing and content-height bounds use effective text once plus independently scaled geometry. A same-size system→explicit-family transition now refreshes provenance as well as font values.

The reusable SDK/module `SUPPORTING` layout leaves leading adornments at natural width and aligns them with the first body-text line. Its final body wraps to the remaining width; INLINE retains its single-line contract. Vault's recovery warning selects SUPPORTING without feature fonts, colors or offsets. Real staged-owner tests cover actual ThemeController13/20/32 and natural/constrained340logical allocation, retaining drafts, full warning height, natural icon width, and visible content before actions. Module tests retain all supporting text across lines and verify paint on every line at344/180 widths. First/only strength meters and meters following newly hidden siblings now have nonnegative bounds and positive preferred height. Both settings projectors preserve titleNote and incoming title/description/dependent presentation while controls and validation refresh from the draft.

These fixes preserve the open font-ink/palette/native-frame residuals above. Controller native D confirms focus/draft retention and Escape/native close at the previous commit; fixed-head recapture is required. Compiler fixture-close warnings (review M1) remain deferred to Task11 hygiene. No page is accepted.

Review-fix captures: all44 default actual-owner outputs regenerated. Exact RGB comparison against retained pre-fix empty captures is identical for Create800×856 and Unlock760×528; all prior default anchors remain. At configured20, auto UIScale1.5, Create naturally allocates600×645logical (PNG1200×1290): CAPTION17.692308pt, warning[42,512,516,22], first baseline530. A deliberate sub-preferred340logical allocation yields340×800 (PNG680×1600), warning[42,623,256,66] with three complete lines, first baseline641, actions y719. The existing equal primary action elides at that stress-only width below the600logical preference; this is recorded separately from the complete warning and is not an all-controls constrained-layout acceptance. Both images visually inspected, no reference-image resizing. Evidence lives in `task1-fix1-default-comparison.json` and build `theme-surfaces-system20` / `theme-surfaces-system20-narrow` with metadata.

## Plan 3 Task2a: actual anchored picker route (2026-10-10)

The Desktop `combo_box_mock.png` is 1990×1664, SHA-256
`09539eb2f89f4d0830bc9c8044fb020e17e3334402bf4e1fb6300bc2a9bb53b4`.
The full original and its audit were inspected. Its compact field outer envelope is approximately
`222,206,380,44`; the 44-pixel height, 2-pixel border and 6-pixel outer focus independently support
2× source scale and COMPACT22, without changing DEFAULT28. Source search fill is
`1531,269,418,40`; menu width is about444 source pixels including border. Shared roles now give
190×22 compact /190×28 default faces, 222-wide menu, 21 visible search, 28 VALUE/26 UTILITY allocations (24 utility active fill), 6 search top/5 horizontal padding, 43 empty area with a 5 bottom text inset, 16 menu tiles,
14 face tiles, 8 picker glyphs, 4 footer top gap/2 bottom padding, compact10/caption9 typography and disabled45% opacity. Font scaling remains central.

`StagedAnchoredPickerTest` loads actual staged Vault and Remote under PluginHost, creates an unbound
synthetic Vault/Login, opens actual Import with a supplied synthetic sshDir, and renders the mounted
producer/consumer. Empty, selected, open, filtering, no-match and genuine review/completion-disabled
states are in `jasper-app/build/theme-migration-evidence/anchored-picker/actual/`, with PNG/JSON
owner/bounds/scale evidence. Additional light, enlarged, short popup and narrow Import captures use
the same route, with an intentionally long synthetic username. The dedicated Credential FormRow
prevents the host-summary text from shrinking the nominal face; narrow layouts clip the caption.
Creation routes through the existing Login/path-key manager operations, without grants on save.

The tracked `jasper-app/src/test/resources/dev/jasper/app/plugins/desktop-picker.toml` (also copied to scratch `desktop-picker.toml`) is the supported source-calibration theme used by these actual-owner renders. Its source-profile-converted measured control/menu, border and search colors differ deliberately from the hero palette. The `filtering-default-theme` image retains shipped Jasper Dark evidence separately. Source search21 is a visible kit outline and its allocation; the prior stock field lost6 logical pixels to internal focus insets. Full popup444×214 no-match now matches the source envelope.

Scratch `task-2a-overlays.py` produces original-pixel crops, ICC-to-sRGB conversion and equal-scale
side-by-side/50% overlays; no image resizing. Different supported synthetic content/row counts are
retained. The model has no credential groups, so Home Lab/Work captions are not fabricated.
Broad palette/font/native-chrome matching remains open: shipped Jasper Dark's accent/category hues differ from the source-calibration palette, and headless output cannot prove native attachment, physical
focus, IME or outside dismissal. Native controller verification and final pixel acceptance are pending;
no page is accepted. Task2b consumes DEFAULT28; Task4 refines sheets using this real presenter.


The full-gate narrow SFTP probe exposed a genuine shared table allocation defect after the approved
platform typography became active: the numeric size cell received55 pixels for57 pixels of ink.
Tables now cache content minima with their data/font snapshot and preserve ordinary weighted
allocation unless a column falls below its minimum. Only that deficit is redistributed; header and
body use the same calculation. Impossibly narrow viewports retain bounded weighted/ellipsis layout.
Focused coverage checks long values, wide/narrow/enlarged alignment and unchanged virtualization.

The final bounded picker role probe records actual `.AppleSystemUIFont` at 11pt, UIScale1.0,
no developer defaultFont/Label.font override and family override false before correction; 10/9
shared compact/caption roles reduce same-text empty ink298×19 to272×17 (source273×17), footer
174×16 to160×15 (source158×15). No source family identification is claimed. Footer label ink
remains about1px right/up and2px wider at2×; device rasterization and native capture remain open.
The source None utility is a circle with an ascending slash; shared `none` artwork and 8logical
picker glyphs replace the minus and oversized plus/category artwork. The footer uses a4logical
leading separator gap, a24logical active fill and2logical menu bottom padding. VALUE rows remain28,
ordinary UTILITY rows26, footer allocation30; default face remains28 with BODY typography.

Controller final comparison inspection retains concrete shared-picker residuals for native calibration:
the search magnifier is visibly larger than the source; the separator is inset while source spans
the popup width; search outline/rounding differs; footer/message ink mass differs despite close
bounding boxes. These are app-owned shared-renderer differences, not dismissed as native chrome.
Current headless geometry evidence remains distinct from future actual native popup/focus proof.
Controller explicitly requested freezing this candidate and carrying these residuals into review/native
calibration rather than adding unreviewed ad hoc patches during the stable gate.


### Task2a fix round1: installed host envelope and remaining ink

Native picker-a at `d40439c3` was not accepted: an actual444×214 popup acquired LAF
wrapper insets4/1/4/1 and a scroll border, reducing its content, adding a scrollbar and clipping
the footer. A headless reproducer now performs `updateComponentTreeUI` on the actual host
JPopupMenu/kit tree. Non-UIResource zero borders survive refresh in both shared host and
menu scroll container; no popup-size inflation. Staged captures now explicitly record
`hostWrapper=true`, zero insets and absent scrollbars except the intentionally short viewport.

Shared search roles are radius5, side6, padding7, glyph9, gap7 (logical); default face28 and
compact22 remain unchanged. The divider is painted across the scroll container rather than
inside the inset row. Search focus still uses the shared keyboard-focus halo with its matching
radius. No focus suppression or per-string offsets were introduced. Equal-scale no-match
comparison retains444×214: color-threshold source/actual magnifier boxes15×15/13×13;
message boxes both[85,96,273,17]; footer source[71,176,158,15], actual[72,175,160,15].
The source divider has two strong device rows; actual full-width line has different antialias
ink. Search outline edges/rounding, glyph stroke and footer/message ink mass remain distinct.
These are measured app-owned residuals for the native rerun, not waived by equal boxes.

Narrow font provenance inspection found picker-a config has no `ui.font` override.
ConfigurationController passes its default `UiFontConfig(system,0)` to ThemeController,
which clears developer defaultFont/form-font entries and uses the platform family.
The staged actual owner records plain `.AppleSystemUIFont`,10pt, weight1,width1,posture0,
tracking0, UIScale1, no defaultFont/Label.font override and family override false.
This finds no bootstrap override explaining heavier native ink; it does not prove the actual
native raster font is identical. No font-family search or global size change was made.

Fresh fix-round comparison/capture/input hashes are separate from d404 evidence in the
scratch fix1 manifest; picker-a native originals remain immutable. All874 affected tests pass
(zero failures/errors, one existing skip); SDK/fake are unchanged/up-to-date. Controller must
rerun actual Save/Cancel (Login and path-key), popup layout and keyboard behavior in picker-b.
Independent re-review and final pixel acceptance remain pending; no page is accepted.

## Plan3 Task2b — actual grouped Add Host (2026-10-10, not accepted)

Actual staged RemotePlugin/HostEditor mounts use the exported Vault anchored API at DEFAULT28. Captures live in
`jasper-app/build/theme-migration-evidence/add-host/actual/`; every PNG has component bounds/font provenance in
its sibling text file. States include empty, opened empty/selected, filtering, no matches, selected Login,
real imported ordered keys/reordered, Agent disabled/valid, busy, persistence error, light, enlarged and narrow/short.
The real Login selection grants one secret fetch; the next fetch requires normal consent. No connection is made.
The compact Import six-state captures are regenerated after the shared corrections.

Desktop original content crop `(26,82,1080,1464)` is 540×732 logical at device2. The exact source ICC→sRGB palette
is `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-host.toml`, SHA-256
`437699cd4a486865b2d8cea730a559ddf84a9900d992e56ff51bcf3f72a67407`. No production theme is replaced.
Unresampled source/candidate/overlay/diff and hashes are retained in scratch `task-2b-comparison/manifest.json`.
The selected real two-key form naturally prefers **540×705**, empty **540×646**; capture at540×732 pins the footer
at the comparison envelope but does not prove native geometry. At540×732 candidate Connection card is
`24,42,492,170`, Authentication `24,254,492,201`, Options `24,497,492,140`; Name `146,56,356,28`,
picker `146,307,356,28`, ordered keys `146,345,356,96`, footer buttons y684. Native platform chrome is additional
and must be measured by the controller; do not resize a native screenshot to force agreement.

The default face now uses density-specific shared inset/tile/gap/glyph/chevron metrics. At device2, source
empty-face text ink starts x68 versus candidate x57 before and x67 after (same text). Lock source bounds
`23,18..41,38`, candidate before `25,22..35,35`, after `20,16..38,38`. Chevron source12×7 versus candidate
before15×9, after13×8. Source face708×56 and candidate712×56 are preserved, so trailing absolute positions
must be interpreted with their different widths. The compact first text-ink x52±1 and default x68±1 have
focused raster assertions. Icon silhouette/optical origin and one-pixel baseline residuals remain unaccepted.

Full-form anatomy remains open beyond ink: Name top candidate112device vs source106; Auth card508 vs source
about504; key box690 vs698; Options994 vs about1015 and card bottom1274 vs1317. Shared form label/card spacing,
ordered-row padding/actions and settings description height account for cumulative drift. Actual free-entry Group
retains its default explanation, real keys show `Stored key` with fingerprint tooltip because algorithm/bit count
is not exported metadata, and empty keys do not acquire invented rows. Existing LINK treatment now gives Add Stored
Key its depicted semantic link color. These domain qualifications do not waive shared spacing/reference work.

Controller cf8 native compact evidence found successful visible creation returns but teardown warnings/blank
completed Import Close; retained Form traversal during removal now has a concrete RED and a shared inert-after-close
fix, with staged Cancel/Close regression. Native rerun is pending. Enlarged installed-LAF footer fitting passes
at system20 after layout, so the earlier transient native Refresh/Cancel ellipsis is not declared fixed or explained.
The read-only native ink report measures query +15–17% and footer +21–26% relative to frozen headless pixels;
JPEG/ICC controls do not explain it. Same-live-popup lossless/offscreen and draw-time metadata remain controller
investigation, without per-string font workarounds. Independent review, native keyboard/focus/consent/teardown,
all compact/default states and final typography/icon/outline/pixel acceptance remain open. Zero pages accepted.

## Plan3 Task3 File Server / shared FULL forms — 2026-10-10

Implementation evidence remains **unaccepted** pending independent and actual native review. The actual staged `FileServerPlugin` NEW action produces the candidate through SDK/host/module projection; no recreated form descriptor is used for the comparison. New test-only `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-server.toml` carries measured source ICC-to-sRGB palette roles. Other roles inherit the existing complete host-calibration fixture; this is not a claim that unpictured colors were measured or a production theme change.

Desktop `new_file_server_mock.png` SHA256 `82e63e92d5fe3b0ce66b89dce6ec60c74a89349c932ef5e9eb77a621c13229d4` is1096×1452; content crop `(8,64,1080,1380)` gives540×690 logical at inferred2×. Old `reference/new-file-server.png` SHA256 `5bd130e077246725cb808c2104fa620cf307db9d036293ecd3b2b1250c1d0fa3` is1134×1560; practical content crop `(30,74,1080,1460)` gives540×730. Strong scale anchors are56px fields,48px selected-segment fill and28px card inset. These are original-size crops, not resized screenshots; full originals remain unchanged.

Shared geometry now accounts for Card14 padding plus1 hairline; FULL top17, MIXED gap12, dependent indent16 and FULL-only setting descriptions64/insets15. Default Settings48/inset13, hero54 and panel-footer rules are preserved, including live FULL→Settings→Hero→FULL refresh. Unscrolled actual Add Host and File Server now place Name at logical `(147,54,354,28)` instead of the prior `(146,53,356,28)` tendency. Source File Server outer field `(302,171,708,56)` becomes `(147,53.5,354,28)` after crop; flat fill starts `(304,173)` and excludes the2px source border. Source antialias fringe/component/flat-fill bounds are distinct; the remaining1-device-pixel y offset is **not** a rounding waiver or an arbitrary half-pixel correction. Add Host's separately measured source field is708×56 too.

Controller-approved SUPPORTING text owns regular sans11.5, tertiary tint, wrapping and18 logical line height, including nested control-column stacks; DESCRIPTION/CAPTION and mono roles remain unchanged. Generic segmented padding4 fits all three real login labels. Button preferred width now uses the same fractional text measurement as its painter, fixing actual `Choose…` clipping (82→83 logical at the tested font). Explicit engine-filled built-in `folder.filled` supplies all PATH controls; existing folder/custom artwork stays outlined with the public1.4–1.6 stroke contract. The shared gallery's path/key sheet and actual forms were rerendered; no FileServer font, paint, icon path or pixel constant was added.

Anonymous/write-OFF final natural preferred height is690, matching its source envelope without scrolling (pre-final-V1 was683). Old Vault-account/write-ON adds40 logical pixels of Account row and helper spacing, plus the retained immediate plaintext warning absent from the mock: final natural preferred height768. Same-envelope540×730 evidence therefore scrolls and loses10 logical pixels of content width; a separate540×768 natural capture preserves full-width geometry. Add Host two-key final natural preferred height is732, matching its source envelope and retaining354-wide fields without scrolling (pre-final-V1 was736). The Group row is now single-line; its real blank/default guidance remains in the accessible control label and empty/typed/renamed-default persistence is tested. The warning is required existing domain behavior and remains visible/accessibly named. It must not be suppressed to match the source. HTTP/FTP/SFTP/TFTP are actual capabilities; the mock's RSYNC is not implemented. FTP TLS remains explicit (`ftp://`); HTTPS applies only to HTTP TLS.

Source versus candidate palette and geometry residuals remain: source default dropdown/selected-segment text is brighter than shared TEXT_PRIMARY; protocol/heading weight and letter spacing, folder silhouette exact ink, DEFAULT picker artwork, toggle/disabled alpha, source FileServer port84 versus current73 (Add Host source port~72), footer mono weight/tint and exact button border footprint. No feature-specific port width was installed to break Add Host. Fresh native raster/font/chrome and keyboard/popup checks remain controller-owned; the Mac was locked. SDD `task-3-comparison/manifest.json` freezes hashes, unresampled ICC overlays/diffs, precise per-control bounds/font/baseline metadata and all affected-owner captures.

Final render verification also exposed selected segment text using TEXT_ONACCENT on a neutral selected-control surface, making labels disappear in light themes. It now routes TEXT_PRIMARY through the existing surface-specific4.5:1 readability policy against SEGMENT_SELECTED_BG, retaining readable authored colors unchanged and correcting insufficient custom contrast at paint time. Light/dark/authored-custom painted regressions distinguish this correctness fix from remaining source palette calibration. Notification links share the fractional content sizing: their geometry test now verifies complete painted labels and unchanged adjacency/close bounds instead of asserting integer font widths. A separate hover-restyle fixture explicitly pins/restores user scale to test a font-only fit change; production typography is unchanged.

Final V1 anatomy resolves the previously isolated envelope differences through reusable owners: FULL SettingRow content floors42/64 plus1 hairline give43/65 outer rows; compact/hero/panel-footer remain unchanged. MIXED outer padding remains14+1stroke, while an internal content/setting transition uses14 content padding without double-counting the stroke. FULL actions are30 high with22 top gap (ordinary28/hero30 remain distinct). ORDERED rows35 and add action32 give102 for two keys. Add Host Authentication uses existing MIXED12 spacing while Connection remains FORM10. Its unnecessary visible default-group helper is removed, preserving actual default semantics and accessible guidance.

| Final actual logical bounds | File Server Anonymous | Add Host two keys |
|---|---|---|
| Natural envelope |540×690|540×732|
| First card |24,39,492,173|24,39,492,172|
| Name |147,54,354,28|147,54,354,28|
| Access / Authentication |24,254,492,171|24,253,492,213|
| Account / Credential face |conditional; old state147,310,354,28|147,309,354,28|
| Ordered keys |not applicable|147,349,354,102|
| Security / Options |24,467,492,151|24,508,492,152|
| Footer Cancel |341,640,69,30|352,682,69,30|

Source diagnostic flat-fill vertical runs (raw original RGB; not ICC-normalized color claims): FileServer x70 has Security fill999..1081, separator1082..1083, fill1084..1211, separator1212..1213, fill1214..1296. AddHost x90 has Options fill1099..1181, separator1182..1183, fill1184..1267, separator1268..1269, fill1270..1396. Rounded-card fringes extend beyond these flat-fill runs. They support row/stroke accounting; the crop-origin and half-device-pixel component residuals are still explicitly unaccepted. Final source-matched envelopes are natural measured results, not allocated screenshot dimensions used as acceptance.

Final deliverable gate `./gradlew check :jasper-app:installDist :jasper-app:themeSurfacePreview :jasper-ui:gallery -Pjasper.galleryScale=2` passed in5m34s:9,769 tests, zero failures/errors, six existing skips;109tasks23executed86up-to-date;61 gallery fixtures. Source-depicted Add Host additionally clears real Name/Host controls, keeps genuine imported order and disables Add Host. Real SINGLE-list last-imported selection cannot be cleared by its user interaction and remains visible/qualified; no fake unselected descriptor is injected. The behavioral filled/reordered captures remain separate. Manifest retains paired pre/post actual-owner overall/component crops, unchanged-context hero/compact/settings coverage and original source hashes; independent/native acceptance remains pending.


## Plan3 Task4a host integration — 2026-10-11

Actual staged Vault title actions, menu, Login and SSH-key sheets are captured through the production NativeShells chrome/attachment helpers at device2 in dark/light/custom/system20 and short-owner45 states. Manager body is exactly900×555 excluding measured headless28px title; enlarged fixture owner is1350×833. Native transparency/shadow branch is not instantiated by this harness, so its opaque root/holder backing does not establish the bottom-corner native seam. Shared SHEET path is square-top/rounded-bottom; native verification remains required.

Original references retain their SHA256/ICC metadata; derived crops are converted to sRGB, never resampled. `task-4a-comparison/manifest.json` records original/candidate hashes, crop origins, original-scale overlays/diffs, top-left-aligned padded comparisons and named-control bounds/font/baselines. No page is accepted. Source independent2× anchors remain56px fields,36px menu tiles/58px row steps and1800×1110 manager body.

| State | Source logical body/crop | Actual logical body/crop | Open composition residual |
|---|---|---|---|
|Manager|900×555|900×555|Existing single-list body/footer, three-column detail design remains4b|
|Add menu|222×137|200×131|Existing text menu lacks source category tiles;22px narrower and6px shorter|
|Login sheet|480×363.5|480×471|Existing stacked inputs/auth dropdown and all fields retained;107.5px taller with body scrolling|
|SSH-key sheet|480×306|480×363|Existing above-field labels, no identity tile/card;57px taller|

For default key sheet, real title24,20 with15pt/15baseline; Name24,53,432×50, Name text input34,81,412×16; footer0,295,480×68; Cancel318,315,69×28 and Save395,315,61×28 with19baseline. These component rectangles are not source ink bounds. Colors remain theme roles; sample source colors are not requirements. No fixture fills in absent domain metadata or fake accounts.

Shared footer viewport did not alter retained default anatomy: actual Create, standalone Unlock, grouped Add Host and File Server calibration/light renders are each pixel-identical to Task3 originals (8 equal-size pixel comparisons). Real32pt controller test proves100px usable-screen overflow with focus-listener/scrollbar reveal and recovery retaining draft document/action ancestry; it is programmatic headless evidence, not physical keyboard proof. Actual enlarged-family renders and native controller checks remain recorded in the Task4a report. Task4b retains all body/auth/menu/card obligations; Task10 retains genuine title/control/shadow calibration.
