# Jasper stand-up source artwork

`source-atlas.png` was generated on 2026-10-24 with the built-in `image_gen.imagegen` tool,
using `docs/design/jasper-pixel/source-atlas.png` as the character/style reference and
`transparent_background: true`. The exact prompt is in [prompt.txt](prompt.txt).
The selected original is unchanged from the generated output:
`/Users/dustin/.codex/generated_images/01a170a6-c0f7-7902-ba2c-b6d1203ed5ed/exec-dad2d977-5a43-4aa6-8aea-563d5bb9339c.png`.

The 2172 × 724 atlas contains close-laptop, lower-laptop and rise from left to right.
`packaging/buddy/pack-stand-up.swift` records the measured opaque bounds within its three transparent gutters,
scales all poses by 0.15 and places their feet on row 91. `append-stand-up.py` appends those cells while preserving
the original runtime strip's twenty cells. See [reproduction commands](../../../packaging/buddy/README.md).

`sequence-actual.png` shows TYPE_REST, CLOSE_LAPTOP, LOWER_LAPTOP, RISE and IDLE at 84 × 96 per cell;
`sequence-enlarged.png` shows the same sequence at 4× nearest-neighbour scale. All heads, feet and the laptop
remain inside their cells. The generated closing pose has a slightly larger head than TYPE_REST; the shared scale
keeps the rising endpoint close to IDLE in overall height, with all five poses sharing the same ground baseline.
