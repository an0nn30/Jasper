# Jasper app pixel sprites

Local-only revision, 2026-10-19. The user approved `website/assets/jasper-pixel-study-v2.png`
for the app and retained the homepage’s existing dimensional illustration.

Generated with the built-in image tool from that approved character reference. Prompt: create a
transparent 5-column × 4-row animation atlas of the exact olive turtle, brown glasses, cream belly,
brown shell and compact expression. Match the reference’s hard square pixel edges, stair-step outline
and clustered 3–4-level shading; avoid smoothing, gradients and painterly texture. Keep shared scale,
head size, viewpoint and baseline. Order: idle, blink, wink, wave A, wave B; hop, lean left, lean right,
sit, sit blink; tuck, sleep A/B/C, sparkle A; sparkle B/C, typing A/B/rest. Preserve transparent gutters,
closed eyes in blink poses and consistent body/laptop geometry. No labels or floor shadows.

`source-atlas.png` is the original generated output. `packaging/buddy/pack-atlas.swift` records measured
transparent gutters, applies one shared scale with nearest-neighbor sampling, and packs opaque or
transparent pixels onto 84 × 96 cells. The three existing sparkle overlays are retained from the
original pixel strip. The runtime and editable master contain 20 frames in the same order.

The app still paints a 42 × 48 base frame at a default scale of 2.5 (105 × 120 logical pixels), now with nearest-neighbor
rendering. Animation timing and reduced-motion behavior are unchanged. See
[the sprite workflow](../../../packaging/buddy/README.md).
