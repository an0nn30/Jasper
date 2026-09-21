# Buddy maintenance recipes

Read the [embedding contract](../jasper-buddy/README.md) and
[owner map](buddy-architecture.md) before changing code. Repository builds require JBR 25
and `./gradlew`. Buddy is a JDK-only library; native windows, persistence and app policy
must keep their existing ownership. The app's [producer recipe](app-maintenance.md)
covers translating a terminal/workspace event into a notice.

## Add or change a notice producer

1. Use only [BuddyNotice](../jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNotice.java),
   [BuddyNoticeId](../jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNoticeId.java) and
   [BuddyCompanion](../jasper-buddy/src/main/java/dev/jasper/buddy/view/BuddyCompanion.java).
   Choose a distinct source and stable opaque key, and a state compatible with the kind.
2. Keep the producer outside the library. Post on EDT; detail suppliers read a cheap
   snapshot and activation requests return quickly. Repost an ID for a new outcome;
   use `updateTitle` for wording that must not create new attention.
3. On producer close, invalidate identity before cancelling delayed callbacks. Freeze
   unfinished detail with `orphan(id, text)`; use `orphan(id)` for a completed outcome
   whose existing detail should remain. Acknowledge separately if it should leave the column.
4. On host shutdown, close producers before the companion. Verify a callback saved
   before closure cannot post afterward. Hiding the companion must retain notices.

Follow [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java)
and its [tests](../jasper-app/src/test/java/dev/jasper/app/notifications/CommandNotifierTest.java).
For model semantics, use [BuddyDeckTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/model/BuddyDeckTest.java)
and [BuddyNoticeTest](../jasper-buddy/src/test/java/dev/jasper/buddy/notice/BuddyNoticeTest.java).

```sh
./gradlew :jasper-app:test --tests '*CommandNotifierTest' :jasper-buddy:test --tests '*BuddyDeckTest' --tests '*BuddyNoticeTest'
```

## Change options or the supported API

Start with [BuddyOptions](../jasper-buddy/src/main/java/dev/jasper/buddy/config/BuddyOptions.java)
and the facade. Propagate a new option through validation, builder defaults, `toBuilder`,
facade application, affected presentation owners and the host's options construction.
Keep options resource-free and callbacks EDT-only; initial position must remain a
first-realization input. The current font input supplies the font from which fixed
presentation styles/sizes are derived, not a global scale factor.

Update [BuddyOptionsTest](../jasper-buddy/src/test/java/dev/jasper/buddy/config/BuddyOptionsTest.java)
to prove one-field changes preserve every unrelated value and callback. Cover
validation and post-close behavior in [BuddyCompanionTest](../jasper-buddy/src/test/java/dev/jasper/buddy/view/BuddyCompanionTest.java).
Update the README example together with [BuddyExamplesTest](../jasper-buddy/src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java):
the app documentation check compares their marked bodies verbatim. An API change also
needs the explicit five-type allowlist and signature check reviewed; making an internal
class Java-public does not make it supported.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyOptionsTest' --tests '*BuddyCompanionTest' --tests '*BuddyApiTest' --tests '*BuddyExamplesTest'
./gradlew :jasper-app:test --tests '*AppDocumentationTest' :jasper-buddy:javadoc verifyApplicationArchitecture
```

## Change notice ordering, capacity or acknowledgement

Edit [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java),
then its model tests. Preserve source-plus-key identity, newest-first replacement,
acknowledgement reset only on post, the 50-notice bound, immutable snapshots and
unconditional detail replacement by the final-detail orphan overload. Column eligibility
is live-or-unseen; orphaning preserves state and removes liveness. Layout's three-card
column limit is separate from retained model capacity. Exercise the panels too when a
model change affects what is displayed.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyDeckTest' --tests '*BuddyColumnPanelTest' --tests '*BuddyDeckPanelTest'
```

## Change sprite animation or capsule motion

1. For sprite poses and transitions, edit [BuddyAnimator](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java)
   and [BuddyFrame](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyFrame.java).
   Frame order is also the PNG column order; follow the [sprite workflow](../packaging/buddy/README.md)
   when adding artwork. Host working intent is independent of notification thresholds.
2. For capsule arrival/hover timing use `BubbleMotion`; for retargeted placement use
   `BubbleSpring` and `BuddyColumnPlacement`. Keep calculations caller-clock-driven;
   schedule Swing timers only in presentation owners.
3. Extend clock-driven tests for interrupted motion, long clock gaps, hiding, working
   transitions and retargeting. Verify the exact path being changed: idle deadline
   resolution does not establish constant-time working catch-up.

[BuddyAnimatorTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/animation/BuddyAnimatorTest.java),
[BubbleMotionTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/animation/BubbleMotionTest.java)
and [BuddyColumnPlacementTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyColumnPlacementTest.java)
are the starting tests. `BuddyColumnPanelTest` covers identity-based interrupted arrival.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyAnimatorTest' --tests '*BubbleMotionTest' --tests '*BuddyColumnPlacementTest' --tests '*BuddyColumnPanelTest'
```

## Change presentation, dragging or native lifetime

Use [BuddyCard](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyCard.java)
for shared text/materials; column and drawer panels own their input and paint state.
Placement helpers own screen geometry; `BuddyDragFrames` coalesces pointer updates.
`BuddyWindow` owns native acquisition and child disposal. Preserve nonfocusable surfaces,
nearest-neighbor sprite scaling, screen clamps, drag-end position reporting and hidden
state retention. Add cleanup when acquiring a new timer, window or global listener.
Check both initialization failure and final disposal; no new native acquisition belongs
in a facade constructor or a model mutation.

Drive package-local panel handlers and injected clocks in headless tests. Geometry/window
predicate tests do not instantiate a native `JWindow`; desktop layering, actual focus,
monitor changes, drag persistence, repeated hide/show and queued-event disposal need
human-run acceptance. Do not claim those checks from image previews.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyCardTest' --tests '*BuddyShimmerTest' --tests '*BuddyColumnPanelTest' --tests '*BuddyDeckPanelTest' --tests '*BuddyDragFramesTest' --tests '*BuddyCompanionTest'
```

## Preview and verify

Both image tools use actual components headlessly and are opt-in, outside `check`:

```sh
./gradlew :jasper-buddy:buddyNotificationPreview
./gradlew :jasper-buddy:buddyMotionPreview
```

The [notification preview](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyNotificationPreview.java)
writes light/dark frame sequences and settled PNGs under
`jasper-buddy/build/reports/buddy-notifications/`; its optional first argument overrides
the directory (`--args='/absolute/output/path'`). The [motion preview](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyMotionPreview.java)
writes 420 frames per theme under `jasper-buddy/build/reports/buddy-motion/` and has no
output-directory argument. Relative defaults use the Gradle subproject working directory.

`./gradlew :jasper-buddy:buddyPerformanceMeasurement` is a separate opt-in headless paint
measurement. It reports median/p95/p99 for three running capsules at 2× density and
excludes native window/drag scheduling. Do not run benchmarks during an unattended
maintenance task unless requested, and do not infer desktop throughput from this tool.

Finish a change with `./gradlew check`. The suite includes all three modules, compiled
examples, Javadoc and bytecode architecture checks. `BuddyApiTest` verifies the actual
jar sprite and JDK-only loading. Ordinary builds need neither Python nor LibreSprite.
