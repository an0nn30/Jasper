# Command palette verification

These images render the real `WindowContent`, `JRootPane`, terminal component,
command registry, in-memory history, and palette overlay. The fixture creates no
`JFrame` and never starts the user's login shell. On macOS and Linux it runs the
repository's controlled `/bin/sh` test command, then closes the owner and waits for
every session exit future.

The recents and `pane` search use real built-in commands. The narrow-layout case
registers five harmless preview-only Swing actions with deliberately long labels;
they exist only in the test-runtime renderer and do nothing if invoked. The
synthetic entries make truncation deterministic without adding product commands.

| State | Purple | Classic dark | Light |
|---|---|---|---|
| Three recents | [1×](recents-purple-900x600-1x.png) / [2×](recents-purple-900x600-2x.png) | [1×](recents-classic-900x600-1x.png) / [2×](recents-classic-900x600-2x.png) | [1×](recents-light-900x600-1x.png) / [2×](recents-light-900x600-2x.png) |
| `pane`, five results | [1×](pane-query-purple-900x600-1x.png) / [2×](pane-query-purple-900x600-2x.png) | [1×](pane-query-classic-900x600-1x.png) / [2×](pane-query-classic-900x600-2x.png) | [1×](pane-query-light-900x600-1x.png) / [2×](pane-query-light-900x600-2x.png) |
| No match | [1×](no-match-purple-900x600-1x.png) / [2×](no-match-purple-900x600-2x.png) | [1×](no-match-classic-900x600-1x.png) / [2×](no-match-classic-900x600-2x.png) | [1×](no-match-light-900x600-1x.png) / [2×](no-match-light-900x600-2x.png) |
| Long labels, 360×500 | [1×](long-labels-narrow-purple-360x500-1x.png) / [2×](long-labels-narrow-purple-360x500-2x.png) | [1×](long-labels-narrow-classic-360x500-1x.png) / [2×](long-labels-narrow-classic-360x500-2x.png) | [1×](long-labels-narrow-light-360x500-1x.png) / [2×](long-labels-narrow-light-360x500-2x.png) |

The 1×/2× labels above describe output pixels: both paint the same logical Swing
geometry. A separate fresh JVM with `flatlaf.uiScale=2x` produced the
[actual UI-scale render](actual-ui-scale-2x.png) and [dimension record](actual-ui-scale-2x.txt).
The assertions measured a 1120px preferred card width, 112px input row and 80px
result row, exactly twice the 560/56/40 logical geometry and therefore scaled once.

All 24 matrix PNGs and the UI-scale image were inspected with the image tool. The
controller independently inspected a cross-theme/state subset. The first render
pass exposed blank command rows because the null-layout cell renderer had not laid
out its labels at the final paint width, and it showed the Escape hint stretched
through the full input row. Focused component regressions reproduced both issues.
The renderer now lays out at paint time and a transparent wrapper centers a compact
Escape hint. The regenerated matrix has visible titles and badges, preserves badges
before truncating narrow titles, stays centered, and has readable selection and
muted text in purple, classic dark and light.

Native focus, IME behavior, accessibility announcements, window deactivation and
physical-display placement cannot be inferred from headless PNGs. They remain
unchecked in the [native acceptance checklist](../../superpowers/plans/2026-09-12-moray-command-palette-manual-check.md).

## Reproduce

From the repository root, choose an absolute output directory:

```bash
OUTPUT="$(pwd)/docs/design/command-palette"
./gradlew :moray-app:commandPalettePreview --args="$OUTPUT"
./gradlew :moray-app:commandPalettePreview -Pmoray.uiScale=2x \
  --args="$OUTPUT --expect-ui-scale=2"
./gradlew :moray-app:commandSearchMeasurement --args="$OUTPUT"
```

These tasks are opt-in and are not dependencies of `check`. The UI task is
headless; the output-pixel scaling is separate from the fresh-JVM FlatLaf scale
property. The [search report](search-measurement.md) uses 1,000 entries indexed
once, 5,000 warmup calls and 10,000 measured calls for exact, prefix, fuzzy and
zero-match queries. Its workstation numbers describe matching only, not native
key-to-paint latency, and set no CI threshold.

## Durable execution record

The feature used the existing project-local ignored worktree and branch so `main`
remained unchanged. Plan source blocks were treated as recipes below the approved
design; proven defects received focused regressions and are recorded here rather
than being mechanically preserved. Task 4 excluded the future `command_palette`
ID by string until Task 5 added the enum member, preserving sequential builds.

The app-level byte-isolation check used the existing controlled PTY on macOS
because `FakeConnector` is intentionally package-private to the terminal module;
synthetic routing retains cross-platform coverage, while native Windows PTY input
remains manual. Command-history decoding reports malformed UTF-8 instead of
silently replacing it. Persistence is staged before callbacks so a reentrant
listener close cannot discard an accepted command.

Key routing tracks press provenance so a fresh foreign press is not swallowed by
an older owned typed tail. An EDT-confined roster of installed window dispatchers
gives a held sequence's residual events priority across windows; disposal removes
the owner. These choices preserve owned-tail containment without creating a public
input or terminal testing API. Existing unrelated negative-fixture output was not
expanded into baseline cleanup.
