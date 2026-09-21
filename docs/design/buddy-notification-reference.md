# Notification capsule reference measurements

The user supplied `Screen Recording 2026-09-17 at 14.26.55.mov` (light) and
`Screen Recording 2026-09-17 at 14.28.41.mov` (dark), and requested their appearance
and motion replace the branch's existing notification presentation. This amends
the thought-column spec's visual rules, leaving notice routing and lifetime intact.

## Evidence

Both recordings were decoded using ffmpeg with `-fps_mode passthrough` so no source
frames were invented. `ffprobe -show_entries frame=best_effort_timestamp_time`
provided the actual timestamps: both are variable-rate recordings, despite their
60 Hz nominal rate. Extraction and measurement files live in `/tmp/buddy-reference`.
The dark clip is 842 × 510 physical pixels, 13.983 seconds; light is 640 × 510,
18.208 seconds. The light recording clips the capsule's left edge.

At 2x display density, the dark capsule spans physical x=158…817 and y=44…155:
330 × 56 logical pixels, with a full-height corner diameter. Left text padding is
16 logical pixels. Both lines are 13-point macOS system text; the title is bold,
and baselines are 25 and 41 pixels below the capsule top. The rendered title's ink
bounds agree within two physical pixels of the reference. The material's measured
flat fill is #414141 in dark mode and #f9f9f9 in light mode, against the captured
near-white background. The light shadow is broader/stronger than the dark shadow.
There are no thought-tail dots and no size change on arrival or hover.

The clearest arrival starts at dark t=1.550 s. Width remains 660 physical pixels.
Its bottom edge moves from y=91 to the resting y=155, briefly reaching y=158.
A damped spring fitted to t=1.550…2.167 s has approximately 0.35 physical-pixel RMS
error: remaining displacement = exp(-10.2t) × (cos(9.9t) + 10.2/9.9 × sin(9.9t)).
Comparing the actual 2x Jasper render to the recorded frames gives 0.95 physical-pixel
RMS edge error, with a maximum two-pixel difference. Jasper uses 32 logical pixels of travel and settles exactly at 600 ms. A new
capsule starts above its resting position and descends; the below-buddy fallback
mirrors this motion. Opacity is already settled in the first visible source frame.
The dark capsule vanishes between t=11.533 and 11.550 s without a visible exit
transition, so removal remains immediate.

## Implementation and limits of the evidence

`BuddyCard` shares capsule painting across the column and drawer. It uses a cached
Gaussian outer shadow, avoiding the darker fill caused by painting a shadow under
a translucent body. The material stays partially transparent over other desktop
backgrounds, and follows the installed app theme. A flat-background video cannot
uniquely recover source RGBA or prove a particular native blur material; the
chosen RGBA matches the measured composite, without claiming native vibrancy.

Hover reveals the existing open action with a title fade and no enlargement.
The drawer retains its working dismiss control. The reference app's task-stop
button is not copied as a nonfunctional control or mapped to terminating a shell:
Jasper's notices have activation and dismissal, not a task cancellation API.

Neither clip shows multiple simultaneous capsules. Jasper retains the existing
three-notice limit and newest-nearest ordering, using 6px gaps, with movement
keyed by `(source, key)`. Status/title changes on the same item do not replay its
arrival. Retargeting samples the current position so an interrupted stack move
cannot teleport. Painted bounds drive input, including rounded corners.

The windows switch between 16ms animation frames and 1s live detail ticks, and
stop when settled with no live text. Switching from a slow text tick to animation
restarts the timer immediately. Theme changes repaint visible surfaces; dismissing
in the drawer refreshes the live column as well.

## Reproduce

Run `./gradlew check :jasper-buddy:buddyNotificationPreview`. The preview creates no
window and starts no shell. It writes actual-component 2x PNGs and 60fps frame
sequences to `jasper-buddy/build/reports/buddy-notifications/`. The sample titles
match the reference solely for comparing font placement; production notices keep
the real command and status text. The render includes Jasper's actual sprite.

Encode the dark sequence with:

```sh
ffmpeg -framerate 60 -i jasper-buddy/build/reports/buddy-notifications/dark/%04d.png \
  -c:v libx264 -pix_fmt yuv420p jasper-buddy/build/reports/buddy-notifications/dark.mp4
```

Headless regression coverage includes measured arrival positions, interrupted
stack movement, unchanged identity updates, moving hit regions, theme colors,
actual material alpha, rounded corners, outer shadows and dismissal propagation.
Native desktop acceptance remains user-run: check movement and transparency over
a textured desktop, theme changes with a visible capsule, and successive notices.


## Running subtext and dragging follow-up (2026-09-17)

The additional `Screen Recording 2026-09-17 at 14.35.58.mov` is 1032 × 2870,
9.693 seconds, with 482 encoded frames and variable frame timestamps. All frames
were extracted with ffmpeg `-fps_mode passthrough`; timestamps and detected capsule/
sprite bounds are in `/tmp/buddy-motion-reference/` on the development machine.

Ordinary dragging keeps the capsule attached without lag. The capsule changes
sides around the screen midpoint: the upward pass flips near 2.15s, and the return
pass near 5.61s. It travels continuously through the buddy with a small overshoot.
A fit to the return pass gives approximately 8.6/s decay and 8.0 rad/s frequency
(about 9.8 physical-pixel RMS, limited by the moving/animated sprite used as the
anchor). The implementation uses that damped spring with an 800ms settle bound,
preserves position and velocity on retarget, and interpolates the stack's internal
order too. An eight-point midpoint dead band prevents repeated direction changes.
Every intermediate window position is constrained to the selected usable screen,
including monitors with negative origins. Showing a hidden column settles its
placement immediately; its independent capsule-arrival animation remains intact.

Running TASK subtext now has a soft glyph-only highlight moving left to right.
The visual tuning is a 48-point-wide band, a 1.65s sweep and a 2.4s cycle; those
shader parameters are an approximation, not uniquely measurable from the recording.
Dark mode brightens the text, light mode increases its dark contrast. Titles,
backgrounds and completed/waiting/orphaned notices remain steady. Both the live
column and the drawer use the same painter and request 16ms frames while a visible
running task shimmers; other live details keep the one-second cadence. Hiding or
disposing a surface stops its timer.

`./gradlew :jasper-buddy:buddyMotionPreview` renders actual components, placement and
sprite without native windows, using a scripted upward/downward drag in both
themes. Frames and encoded previews are in `jasper-buddy/build/reports/buddy-motion/`.
Tests cover highlight direction and glyph-only changes, completed-task stability,
midpoint/edge placement, interrupted flips, velocity continuity, monitor bounds,
initial placement, direction-order interpolation, and pointer targets. Native
macOS compositing and pointer dragging remain user-run acceptance under AGENTS.md.


## Drag performance follow-up (2026-09-17)

High-rate pointer events no longer rebuild the column or compete with its animation
timer. `BuddyDragFrames` holds only the newest pointer position, presents buddy and
column from a shared 16ms callback while dragging, keeps advancing animations while
the pointer pauses, and flushes the final position on release. The column resumes
its ordinary clock afterward. Monitor geometry is sampled at drag start, not per
pointer frame. Unchanged native size/location/visibility writes are skipped.

The unchanged rounded material and shadow are cached as bounded 2x premultiplied
images. Title/detail/shimmer remain live; light/dark materials have distinct cache
keys. Deck list snapshots invalidate on every relevant mutation. Settled shimmer
requests only its text rectangle, with a full final frame after movement settles.

Run `./gradlew :jasper-buddy:buddyPerformanceMeasurement` for the headless actual-paint
benchmark (three running capsules at 2x, 200 warm-up and 600 measured frames). On the
development Mac the median/p95/p99 fell from 0.983/1.251/1.454ms to
0.731/0.896/0.981ms. RGBA before/after images differed by at most one channel value
in 12 pixels. These timings exclude native window movement, desktop composition,
and refresh synchronization. Coordinating requests reduces conflicting native
updates, but separate JWindows do not provide an atomic compositor transaction;
native tearing must be checked on the user's display. No rendering-backend or
system-wide graphics settings were changed.
