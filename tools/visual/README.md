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
  `:jasper-app:settings9bPreview`, the staged render evidence tests, `EnterpriseKitRenderTest` and the
  workspace preview).
- `check_pairs.py OUT_DIR [FILTER]` runs every pair that has a `min_agreement` (the classic look's `classic-*`, the `run-window-*`, the `chrome-*` and the `enterprise-*` pairs)
  and prints PASS or FAIL: the edge agreement must reach the floor and each listed colour must fill a 2×2 block within
  ΔE76 6 in the mock and appear within ΔE76 1.5 in the render, as a 2×2 block or a three-pixel line. Render the `classic-*` pairs first with `:jasper-ui:gallery -Pjasper.galleryKit=enterprise
  -Pjasper.galleryTheme=light -Pjasper.galleryScale=2`; `RunWindowRenderTest` writes the `run-window-*` renders under `jasper-app/build/run-window/`, and
  `EnterpriseKitRenderTest` the `enterprise-*` ones under `jasper-app/build/enterprise-kit/`.

Run with `python3 -I`; the tools need Pillow and NumPy. A high edge agreement is not acceptance: inspect the
region crops, and record native font rasterization and platform title-bar differences separately.

The `chrome-*` pairs compare the `enterprise-*.png` renders that the `Enterprise*MockTest` classes write under
`jasper-app/build/chrome-renders/` (run `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*Mock*'`);
`ChromeRenderTest`'s `chrome-*.png` files there are for inspection.
