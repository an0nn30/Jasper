# Mock UI comparison

The actual previews are produced by `./gradlew :moray-app:mockUiPreview` and saved as `mock-ui-dark.png` and `mock-ui-light.png`. This headless task paints the real `MacTitleBar`, `WindowContent`, tab controls, action buttons, terminal view and status components at 958 × 958 logical pixels into 1916 × 1916 PNGs. Its two controlled `/bin/sh` PTYs print fixture-only prompt/OSC metadata and wait for input; cleanup closes both sessions and waits for exit. It never creates a JFrame or starts a login shell.

The reference is the unchanged 1970 × 1986 `mock-ui-reference.png`. Measurements convert its embedded BenQ RD320UA profile to sRGB with Pillow ImageCms and crop `(31,31,1947,1947)`. Comparing encoded monitor-profile RGB directly against Swing's sRGB output would be misleading.

## Geometry and color

| Surface | Reference target | Actual logical geometry |
|---|---|---|
| Content | 958 × 958 | 958 × 958 |
| Title/tab row | 54 high | 54 high |
| Toolbar | 53 high | 53 high |
| Terminal pane | remaining 821 high | 958 × 821 |
| Status | 30 high | 30 high |
| Terminal padding | about 24 | 24 on all four sides |
| Terminal view | live grid | 910 × 773; 91 × 35 cells on this JBR/font installation |
| New tab button | x15–119, 104 wide | x15–119, 104 wide |
| Toolbar separators | x231, x484.5 | x231, x484 |

The six large-region dominant RGB samples match exactly after ICC conversion: title `#23262c`, selected tab `#262a2f`, toolbar/terminal/status `#292c34`, and New tab fill `#3a404b`. The vertical runs at raster x1100 also match exactly: title y0–105, title separator y106–107 (`#313439`), toolbar y108–211, toolbar separator y212–213 (`#353940`), terminal from y214. Status has no separator.

Sampled terminal ANSI blue `#80b4df`, ANSI green/running dot `#a8c58d`, and cursor `#b3bbc7` also match the dominant reference colors. Other ANSI entries, light palette colors, and explicit truecolor behavior are retained. Selected tab text, inactive/close/plus/title text, and the underline have separate semantic colors.

Toolbar assets are the original plain Tabler outline paths, dynamically recolored neutrally, at 16 logical pixels. The system toolbar font is 11.5 logical pixels; New tab and tab labels request semibold weight. SVG side bearings plus the 5px icon-box gap produce approximately the reference's 8px visible-outline-to-text gap. Small per-button inset and trailing-space adjustments align the measured groups. All buttons remain real controls with shared actions; narrow layouts compact to icons, with the menus retaining every command.

## Remaining differences and native checks

These artifacts are not a claim of complete pixel identity. Native traffic lights, rounded frame corners, desktop shadow, active-window behavior and native font rasterization are absent from a headless Swing render. The real terminal deliberately paints an unfocused outline cursor; the reference has a focused filled cursor. There is no fake cursor/focus or synthetic native control in the preview.

The existing JetBrains Mono renderer and font metrics remain unchanged. Solid blue/green prompt ink in the normalized reference occupies raster bounds x53–420, y275–353; the fixture occupies x56–439, y270–339. The reference's displayed grid is 100 × 40 while the real preview computes 91 × 35. The preview reports those actual dimensions; production contains no hardcoded prompt, username, directory or dimensions. These typography/content differences require native visual assessment, not renderer coordinate offsets.

Both dark and light actual component previews were inspected during iteration. Focused tests verify continuous surfaces, row dimensions, padding and application font reset, live palette behavior, status clipping/running state, toolbar compact/hidden modes, accessible disabled actions, and the sampled title separator. The implementer full headless check passed 308 tests (72 app + 236 terminal), zero failures/errors, one known font skip. The final title-separator refinement subsequently passed all nine focused MockUi/MacTitleBar tests and regenerated both previews; the root then ran `./gradlew check --rerun-tasks` on final code `63a3325`: all eight tasks executed, 308 tests, 307 passed, one known font skip, no failures/errors or compiler warnings. Source hygiene and diff checks passed. Independent root pixel sampling confirmed all eight surface/divider colors and the vertical row boundaries; both previews were visually inspected.
