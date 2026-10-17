# Visual reference comparison

Headless tools for comparing actual Jasper renders against the preserved references in
`docs/design/theme-engine/reference/`. The references are about 2x; renders must be captured at device
scale 2 (the preview tasks do this) and are never resized. Colors come from the active theme, so the
geometry check compares luminance edges, not RGB values.

- `compare_mock.py MOCK RENDER OUT --crop x,y,w,h [--render-crop x,y,w,h]` writes `OUT-side.png`
  (mock | render | edge overlay: mock red, render cyan, shared white), `OUT-blend.png` and `OUT.txt`
  (best alignment offset and per-band edge agreement).
- `region.py MOCK RENDER OUT --crop ... --region x,y,w,h` cuts the same content region from both at
  original pixels, with a 20px tick grid, for close inspection.
- `compare_all.py OUT_DIR [FILTER]` runs every pair in `pairs.json` (mock path, content crop, render path
  under `*/build/`, and an optional `note` naming a deliberate difference, which it prints). Regenerate the renders first (`:jasper-app:themeSurfacePreview`,
  `:jasper-app:settings9bPreview`, the staged render evidence tests and the workspace preview).

Run with `python3 -I`; the tools need Pillow and NumPy. A high edge agreement is not acceptance: inspect the
region crops, and record native font rasterization and platform title-bar differences separately.

The `chrome-*` pairs compare the `enterprise-*.png` renders that the `Enterprise*MockTest` classes write under
`jasper-app/build/chrome-renders/` (run `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*Mock*'`);
`ChromeRenderTest`'s `chrome-*.png` files there are for inspection.
