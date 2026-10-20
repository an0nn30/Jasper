# Panels acceptance (Plan 3 Tasks 6 and 7)

Headless calibration record for the Tunnels, SFTP, File Servers and Sessions panels, the New Tunnel dialog and the
unmocked Remote transfer surfaces. All captures are the actual staged plugins in the real plugin host
(`PanelSurfacePreview`, `SessionsSurfacePreview`), rendered at device scale 2 and compared without resizing. Positions
below are crop-relative device pixels (2x) unless they say logical; "+n" means the render is n px lower or further right
than the reference. Mock text and hosts are fixture data only.

## Regenerating

```
./gradlew :jasper-app:themeSurfacePreview --args="build/theme-surfaces panel"        # all four themes
./gradlew :jasper-app:themeSurfacePreview --args="build/theme-surfaces tunnel-new"
./gradlew :jasper-app:themeSurfacePreview --args="build/theme-surfaces panel calibration"
python3 -I tools/visual/compare_all.py OUT_DIR panel
python3 -I tools/visual/compare_all.py OUT_DIR tunnel-new
```

Arguments after the output directory are surface filter, theme, state, system font size and logical width. A state
filter is required before the font size (for example `panel dark populated 16`). Images land in
`jasper-app/build/theme-surfaces/<theme>/{tunnels-panel,sftp-panel,fileservers-panel,tunnel-new,sessions-panel}/`,
each PNG with a `.txt` of its named anchors. The pairs are in `tools/visual/pairs.json`.

## Edge agreement

| Pair | Reference | Before (base) | After |
|---|---|---|---|
| sessions-panel | 11.16.50 crop 168,144 | 0.958 | 0.962 |
| tunnels-panel | 11.16.50 crop 2220,946 | 0.122 | 0.952 |
| tunnels-panel-plugin-panels | plugin-panels.png bottom-left | n/a | 0.952 |
| sftp-panel (cancelled) | 11.16.50 crop 168,946 | 0.493 | 0.834 |
| sftp-panel-plugin-panels | plugin-panels.png top-right | n/a | 0.829 |
| fileservers-panel (empty) | 11.16.50 crop 2220,144 | 0.152 | 0.954 |
| fileservers-panel-populated | plugin-panels.png bottom-right | 0.541 | 0.947 |
| tunnel-new | new_tunnel_mock.png crop 8,62 | n/a | 0.799 |

"Before" renders are the base branch through the same harness; "n/a" states did not exist there.

## Measurements and residuals

**Panel empty state.** Tunnels matches 11.16.50 exactly: tile 268-341, title 369-388, description lines 414, 444, 474,
button 528-581, tile x 222. File Servers and SFTP sit 2 px (1 logical) higher than their references (tile 282 vs 284,
title 382 vs 385, button 512 vs 514 for File Servers; tile 218 vs 220, button 448 vs 450 for SFTP): the three
references do not share one vertical formula, and the shared rule follows Tunnels. Tile 37 logical with a 16 logical
glyph; description width 206 logical; title-to-description and description-to-button gaps from the 11.16.50 Tunnels and
File Servers panels (the lead's measurement: tile 74, description to button 32, button 54 device px).

- SFTP description wraps as "Connect to an SSH session to / browse its files." Both SFTP references wrap after "its"
  (about 226 logical), which would put "host," and "SSH" on earlier lines in the Tunnels and File Servers references.
  The references contradict each other; 206 satisfies two of three. Residual: the SFTP description lines.
- 11.16.50 paints the empty-state buttons with a lighter fill (+37 over the body) and secondary text, consistent with
  an inactive window; plugin-panels.png paints the active look (+28, primary text), which the kit follows. The fixture
  theme's secondary fill is +21 over the body: a theme-data difference, not geometry.
- Text is 4-6% wider than the references (native font advance): "Not Connected" 187 vs 179, "Follow terminal folder"
  251 vs 239.

**Tunnels rows.** Compact two-line rows: name then host (or what is wrong) on the first line, the route alone on the
second, one switch. A missing host reads "Host missing · Edit to choose a host" on the first line. No populated
reference exists; the narrow and short states were inspected for clipping. The name keeps its width while it fits in
half the line; the text beside it is cut short first.

**SFTP.** Choose Session is a menu button with the label first and the chevron after (no leading glyph). The cancelled
footer matches: "Cancelled" ink 678-698 vs 678-696, rule 710-711 vs 710-711, Follow terminal folder 741-760 vs 741-760.
The status line is body text, as bright as the row below it (the reference is primary text, 215,222,228), and the rule
fills one logical hairline (2 px at 2x, as both references show) in the theme's subtle border colour (fixture 42,45,52
vs reference 53,58,65: theme data). Choose Session button 449-504 vs 450-506, 276 vs 272 px wide (font advance); the
reference puts the chevron 15 px after the label with 34 px end padding, the kit's menu button pins it to the end (18 px).

**File Servers.** The populated row matches within 1 px: name 98-116 vs 99-116 at x 70; chip-and-endpoint line 128-146
vs 129-145, x 79-319 in both; switch x 440-495 vs 444-496. The filled folder and HTTP chip take the theme's blue hue;
the fixture's `hue.blue` is paler than plugin-panels.png (theme data). Share Folder... has no glyph. A row shows as
selected only when chosen; the switch acts without selecting. Chips keep their labels whole in narrow panels.

**Sessions.** Import uses the new `import` glyph (an arrow into a corner bracket), matching the reference stroke for
stroke within 1 px; the overflow dots match the reference brightness (206,208,213) and size. Residual: the header's dots
sit 2.5 px right of the reference (header layout belongs to the workspace header).

**New Tunnel.** Grouped Tunnel and Ports cards, one-line captions with tooltips, the rich Via Host face, the ROUTE
strip and the command preview beside Cancel and Add Tunnel. Left column x matches (headings 52, labels 80). Vertical
drift: Tunnel card rows +2 px, Ports heading and rows +3 to +5 px, route strip text 749-796 vs 744-788 (3 px taller),
footer +6 px; these come from the shared FULL-form card metrics (forms stream) and were not changed here. Port fields
are 73 logical against the reference's 84 (the Add Host reference measures 72 for the same shared PORT field), so the
address field is about 11 logical wider. The best whole-image offset is dy=-4.

**Transfers (unmocked).** Canonical compositions only, covered by `TransferCompositionTest`: SECTION_LABEL heading and
DOUBLE rows keyed by job (progress, status, one applicable LINK action, close); Replace Existing Items? as a compact
hero question with Cancel, Skip and the primary Replace and no Enter submission; Resolve and Rename as grouped forms.

## Lead review items

- (a) Empty states are centred in the panel body; tile 37 logical, glyph 16. (b) Share Folder... has no glyph.
  (c) Descriptions wrap at 206 logical (SFTP residual above). (d) Panel overflow dots are two logical pixels across and
  match the reference brightness. (e) Sessions Import uses `import`. (f) Choose Session's chevron trails.
  (g) The SFTP status row and footer rule match the reference positions; the status is body text.
- The Editor find bar's field (PANEL_SEARCH_HEIGHT) and action (PANEL_ACTION) sizes were not recalibrated, so the
  Editor bars are unchanged by this stream.

## Found, not changed

- The breadcrumb clips its root crumb to a sliver when a long path overflows (SFTP narrow state); this predates this
  stream and lives in the shared breadcrumb.
