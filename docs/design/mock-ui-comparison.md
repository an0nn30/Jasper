# Mock UI comparison

The actual previews are produced by `./gradlew :moray-app:mockUiPreview` and saved as `mock-ui-dark.png` and `mock-ui-light.png`. This headless task paints the real `MacTitleBar`, `WindowContent`, tab controls, action buttons, terminal view and status components at 958 × 958 logical pixels into 1916 × 1916 PNGs. Its two controlled `/bin/sh` PTYs print fixture-only prompt/OSC metadata and wait for input; cleanup closes both sessions and waits for exit. It never creates a JFrame or starts a login shell.

The user's compact-tab follow-up supersedes the reference's fixed 54px title/tab row: the actual default is now 38 logical pixels, configurable per window from 28 to 72 through View → Tab height…. These regenerated previews show the settled 38px default. They deliberately no longer match the reference's vertical geometry.

The reference is the unchanged 1970 × 1986 `mock-ui-reference.png`. Measurements convert its embedded BenQ RD320UA profile to sRGB with Pillow ImageCms and crop `(31,31,1947,1947)`. Comparing encoded monitor-profile RGB directly against Swing's sRGB output would be misleading.

## Geometry and color

| Surface | Reference target | Actual logical geometry |
|---|---|---|
| Content | 958 × 958 | 958 × 958 |
| Title/tab row | 54 high (superseded) | 38 high by default; configurable 28–72 |
| Toolbar | 53 high | 53 high |
| Terminal pane | remaining 821 high | 958 × 837 |
| Status | 30 high | 30 high |
| Terminal padding | about 24 | 24 on all four sides |
| Terminal view | live grid | 910 × 789; 91 × 35 cells on this JBR/font installation |
| New tab button | x15–119, 104 wide | x15–119, 104 wide |
| Toolbar separators | x231, x484.5 | x231, x484 |

The screenshot revision established matching large-region colors after ICC conversion; this compact-tab follow-up retains those palette values: title `#23262c`, selected tab `#262a2f`, toolbar/terminal/status `#292c34`, and New tab fill `#3a404b`. The current 2× preview boundaries are title y0–73, title separator y74–75 (`#313439`), toolbar y76–179, toolbar separator y180–181 (`#353940`), and terminal from y182. The reference retains its older 54px title row. Status has no separator.

Sampled terminal ANSI blue `#80b4df`, ANSI green/running dot `#a8c58d`, and cursor `#b3bbc7` also match the dominant reference colors. Other ANSI entries, light palette colors, and explicit truecolor behavior are retained. Selected tab text, inactive/close/plus/title text, and the underline have separate semantic colors.

Toolbar assets are the original plain Tabler outline paths, dynamically recolored neutrally, at 16 logical pixels. The system toolbar font is 11.5 logical pixels; New tab and tab labels request semibold weight. SVG side bearings plus the 5px icon-box gap produce approximately the reference's 8px visible-outline-to-text gap. Small per-button inset and trailing-space adjustments align the measured groups. All buttons remain real controls with shared actions; narrow layouts compact to icons, with the menus retaining every command.

## Remaining differences and native checks

These artifacts are not a claim of complete pixel identity. Native traffic lights, rounded frame corners, desktop shadow, active-window behavior and native font rasterization are absent from a headless Swing render. The real terminal deliberately paints an unfocused outline cursor; the reference has a focused filled cursor. There is no fake cursor/focus or synthetic native control in the preview.

The existing JetBrains Mono renderer and font metrics remain unchanged. Prompt typography differs from the reference, and the compact title row moves the fixture upward by 16 logical pixels. The reference's displayed grid is 100 × 40 while the real preview computes 91 × 35. The preview reports those actual dimensions; production contains no hardcoded prompt, username, directory or dimensions. These typography/content differences require native visual assessment, not renderer coordinate offsets.

Both regenerated dark and light previews were inspected after the compact-tab/motion implementation. The actual geometry is 38px title, 53px toolbar, 837px terminal pane and 30px status; the terminal view is 910 × 789, reporting 91 × 35 cells on this JBR/font installation. These settled still images do not demonstrate native animation smoothness.

New tabs expand from a usable 64px control to 160px using the same 180ms eased curve as the single sliding active underline. The curve has a small, bounded settle; rapid selection retargets from the current painted position. Selection, focus and shell launch take effect immediately. Title updates preserve motion progress. Overflow arrivals, viewport changes, resizing that changes tab geometry, reordering and offscreen/overflow removal settle directly to keep the selected tab and plus/navigation controls available. The single strip timer runs only during visible motion and stops on completion, hiding, detachment and owner disposal; frames update only the title strip.

Twelve deterministic motion tests drive the real strip with an injected monotonic clock and its actual Swing timer callback, checking intermediate/final painted bounds, overshoot, rapid retargeting, title continuity, controls, overflow, resize/reorder/close, idle/lifecycle cleanup and absence of terminal-deck layout on frames. Existing selection, mouse, theme and compact-height tests remain green. The final root `./gradlew check --rerun-tasks` on `20764b3` executed all eight tasks and passed 328 tests (92 app + 236 terminal): 327 passed, one known font skip, zero failures/errors. Three of the motion regressions exercise the real MacTitleBar/JRootPane container, verifying selection, entry and metadata updates when the trailing title changes tab-strip allocation. Source hygiene and diff checks passed. Native motion, traffic-light alignment at minimum/default/maximum height and visual acceptance remain user-run.

Visible tab closes now reverse the same easing: the departing entry contracts to zero, following tabs fill the gap, and the active underline targets the new selection. The model and shell close immediately; only a disabled visual remains until its animation completes. Static previews are unchanged by this behavior. Native reverse-motion acceptance remains user-run.
