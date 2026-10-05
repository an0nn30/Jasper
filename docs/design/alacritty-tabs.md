# Alacritty-style workspace tabs — 2026-10-05

Approved in chat after inspecting the supplied screenshots and the 8:03:04 AM recording.
This supersedes the earlier [flat tabs](flat-tabs.md) and [folder tabs](folder-tabs.md).
The bounded follow-up uses native inline implementation, regression-first checks and an
independent final review; historical implementation plans are not re-executed.

## Layout and behavior

The rail is hidden with zero or one workspace tab, reclaiming its entire height. With two
or more tabs, all terminal and hosted-document tabs divide the terminal column width equally.
Rounding consecutive edges distributes leftover pixels without gaps. Titles are centered,
plain text, clipped with ellipsis, and have literal bounded tooltips. The selected cell has a
brighter workspace-derived surface; inactive cells are darker, with vertical separators and
a thin bottom rule. The existing configured tab height (30 logical pixels by default) remains.

No accent underline, icons, live dots, scrolling rail or fixed list/add buttons. The toolbar,
palette and keyboard shortcuts still create and select tabs. Close appears on hover without
moving the title. Middle-click close, context menus, drag reorder and immediate content/focus
selection remain. Very narrow cells hide the close affordance; middle-click, context menu and
keyboard close remain available. Theme changes follow the workspace palette and preserve its
glass alpha. The terminal/library/plugin ownership model is unchanged.

## Motion evidence

The first two recordings decoded mostly black using both ffmpeg and AVFoundation. The third,
`Screen Recording 2026-10-05 at 8.03.04 AM.mov`, supplied 193 usable frames at variable timestamps
(3456 × 2234, duration 7.585 seconds). Analysis used ffprobe `pts_time` and ffmpeg frame crops
`crop=1760:240:900:240`; it did not infer timing from the declared 120 fps. Reference terminal
bounds were approximately x928, y260, width1700; the tab row occupied y318–374 (physical pixels).

| Operation | Last stable / first changed / settled timestamps | Implemented duration |
| --- | --- | --- |
| One → two | 1.675 / 1.700 / 2.166667 s | 490 ms |
| Two → three | 2.833333 / 2.858333 / 3.308333 s | 475 ms |
| Three → two | 4.966667 / 4.991667 / 5.391667 s | 425 ms |
| Two → one | 5.816667 / 5.833333 s | immediate |

The first reveal exposes the rail at full height immediately and expands both cells from a
compressed group around 100 logical pixels from the left. Subsequent additions shrink existing
cells together and grow the new cell; removals contract the gap as survivors widen. Selection
changes immediately. Titles follow each cell center without fading or bouncing. The bar height
does not tween. Closing a tab releases its resources immediately; only a numeric empty gap survives.

`WindowTabs.Curve` interpolates monotonic normalized samples of the recorded boundaries. For example,
the first cell's right edge during two → three moves from x1775 to x1649 at 75 ms and ends at x1491;
during three → two it moves from x1491 to x1629 at 75 ms and ends at x1775. First reveal at 75 ms
has an approximately 122-physical-pixel inset and 329-pixel first cell. Tests compare the rendered
geometry against these samples after scaling to an 800-pixel strip. These are measurements to
captured-frame precision, not a claim to know AppKit's original easing constants. Samples are in
[the reference CSV](alacritty-tab-reference.csv); no desktop video is committed.

One elapsed-time Swing timer drives widths and the reveal inset; interrupted operations retarget
current geometry rather than queue. Metadata/selection updates never restart the deadline.
Resize, reorder and disabling animations settle immediately. `ui.animations.enabled` remains the
only saved animation setting. The internal opening duration scales first reveal by 490/475;
zero still means instant. The legacy switch-duration value stays source-compatible but this
strip switches its selected surface immediately.

## Verification and previews

Headless test suites cover visibility, pixel-exact equal division, centered titles, hover/close,
literal tooltips, resource teardown, real hidden-parent reveal, sampled motion, interruptions,
theme/glass repaints and UI scale. No native application window or login shell is launched.

```sh
./gradlew -I tools/previews/alacritty-tabs.gradle :jasper-app:alacrittyTabsPreview :jasper-app:alacrittyTabMotionPreview
./gradlew -I tools/previews/alacritty-tabs.gradle :jasper-app:test -Pjasper.tabTestScale=2 --tests '*FolderTabScaleTest'
./gradlew check --continue
```

Previews live in `jasper-app/build/alacritty-tabs-preview/`, including single/two/three/four-tab
layouts and 60 fps deterministic Swing motion frames under `motion/`. The terminal preview uses
controlled `/bin/sh` fixtures; the motion preview uses hosted text components. Native feel remains
for the user to verify in the worktree build.

![Four dark workspace tabs](alacritty-tabs-dark.png)

![Three light workspace tabs](alacritty-tabs-light.png)
