# Buddy maintenance recipes

Read the [embedding contract](../jasper-buddy/README.md) and
[owner map](buddy-architecture.md) before changing code. Repository builds require JBR 25
and `./gradlew`. Buddy is a JDK-only library; native windows, persistence and app policy
must keep their existing ownership. The app's [producer recipe](app-maintenance.md#new-buddy-notice-producer)
covers translating a terminal/workspace event into a notice.

## Add or change a notice producer

1. Use only [BuddyNotice](../jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNotice.java),
   [BuddyNoticeId](../jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNoticeId.java) and
   [BuddyCompanion](../jasper-buddy/src/main/java/dev/jasper/buddy/view/BuddyCompanion.java).
   Choose a distinct source and stable opaque key, and a state compatible with the kind.
2. Keep the producer outside the library. Post on EDT; detail suppliers read a cheap
   snapshot (Buddy reads them on every paint) and activation requests return quickly.
   Repost an ID for a new state; use `updateTitle` for wording that must not count as a
   new post, and let the supplier carry changing detail without a repost. Use optional
   `BuddyNotice.Group` metadata and `post(notice, visible)` for atomic initial visibility;
   use `updateContext(id, group, visible)` for later grouping/suppression without promotion.
3. When the work ends, and on producer close, invalidate identity before cancelling delayed
   callbacks, then `dismiss` the notice. Report how the work ended through the host's
   notifications, never through Buddy. The user can dismiss a notice from the drawer
   without the producer hearing of it; a later post of the same ID shows it again.
4. On host shutdown, close producers before the companion. Verify a callback saved
   before closure cannot post afterward. Hiding the companion must retain notices.

Follow [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java)
(terminal commands) or [ActivityNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java)
(plugin activities and connections) and their tests. A plugin-facing change belongs in the
SDK's activity API, which `ActivityNotifier` consumes; plugins cannot call Buddy. App tests
that need to inspect posted notices use
[BuddyTestSupport](../jasper-buddy/src/testFixtures/java/dev/jasper/buddy/view/BuddyTestSupport.java)
through `testImplementation(testFixtures(project(":jasper-buddy")))`. For model semantics,
use [BuddyDeckTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/model/BuddyDeckTest.java)
and [BuddyNoticeTest](../jasper-buddy/src/test/java/dev/jasper/buddy/notice/BuddyNoticeTest.java).

```sh
./gradlew :jasper-app:test --tests '*CommandNotifierTest' --tests '*ActivityNotifierTest' :jasper-buddy:test --tests '*BuddyDeckTest' --tests '*BuddyNoticeTest' --tests '*BuddyNoticeIdTest'
```

## Change options or the supported API

Start with [BuddyOptions](../jasper-buddy/src/main/java/dev/jasper/buddy/config/BuddyOptions.java)
and the facade. Propagate a new option through validation, builder defaults, `toBuilder`,
facade application, affected presentation owners and the host's options construction
(Jasper's [BuddyIntegration](../jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java)).
Keep options resource-free and callbacks EDT-only; initial position must remain a
first-realization input. The font input supplies the family and attributes from which
fixed presentation styles and sizes are derived, not a global scale factor.

Update [BuddyOptionsTest](../jasper-buddy/src/test/java/dev/jasper/buddy/config/BuddyOptionsTest.java)
to prove one-field changes preserve every unrelated value and callback. Cover
validation and post-close behaviour in [BuddyCompanionTest](../jasper-buddy/src/test/java/dev/jasper/buddy/view/BuddyCompanionTest.java).
Update the README example together with [BuddyExamplesTest](../jasper-buddy/src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java):
the app documentation check compares their marked bodies verbatim. An API change also
needs the explicit five-type allowlist and signature check reviewed; making an internal
class Java-public does not make it supported. [BuddyApiTest](../jasper-buddy/src/test/java/dev/jasper/buddy/architecture/BuddyApiTest.java)
requires the facade's single `BuddyOptions` constructor and `void` returns on every
public method except `show()`. Supported types carry Javadoc that doclint checks;
`internal` packages are excluded from the Javadoc. On the app side,
[BuddyVisibilityTest](../jasper-app/src/test/java/dev/jasper/app/notifications/BuddyVisibilityTest.java)
covers when Jasper shows Buddy and
[BuddyStateFileTest](../jasper-app/src/test/java/dev/jasper/app/persistence/BuddyStateFileTest.java)
how `buddy.toml` saves his position.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyOptionsTest' --tests '*BuddyCompanionTest' --tests '*BuddyApiTest' --tests '*BuddyExamplesTest'
./gradlew :jasper-app:test --tests '*AppDocumentationTest' --tests '*BuddyIntegrationTest' --tests '*BuddyVisibilityTest' --tests '*BuddyStateFileTest' :jasper-buddy:javadoc verifyApplicationArchitecture
```

## Change notice ordering or capacity

Edit [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java),
then its model tests. Preserve source-plus-key identity, newest-first replacement, the
50-notice bound (`MAX_NOTICES`) and immutable snapshots. Every retained notice is live
work, including suppressed entries. `BuddyGroups` projects visible snapshots into groups;
context updates preserve arrival order and cannot restore dismissed IDs. The column's
three-group/card limit (`BuddyDeckLayout.MAX_IN_COLUMN`) is separate from retained model capacity. Exercise the
panels too when a model change affects what is displayed.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyDeckTest' --tests '*BuddyDeckLayoutTest' --tests '*BuddyGroupsPresentationTest' --tests '*BuddyColumnPanelTest' --tests '*BuddyDeckPanelTest'
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
   transitions and retargeting. Verify the exact path being changed: idle postures
   resolve absolute deadlines in constant time, working resolves elapsed typing cycles
   arithmetically, and rising or lowering traverses at most four deadlines.

[BuddyAnimatorTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/animation/BuddyAnimatorTest.java),
[BubbleMotionTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/animation/BubbleMotionTest.java)
and [BuddyColumnPlacementTest](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyColumnPlacementTest.java)
are the starting tests. `BuddyColumnPanelTest` covers identity-based interrupted arrival.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyAnimatorTest' --tests '*BubbleMotionTest' --tests '*BuddyColumnPlacementTest' --tests '*BuddyColumnPanelTest'
```

## Change presentation, dragging or native lifetime

Use [BuddyCard](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyCard.java)
for shared text/materials; column, drawer and bubble panels own their input and paint
state. Placement helpers (`BuddyPlacement`, `BuddyColumnPlacement`, `BuddyBubblePlacement`)
own screen geometry; `BuddyDragFrames` coalesces pointer updates. `BuddyWindow` owns
native acquisition and child disposal. Preserve nonfocusable surfaces, nearest-neighbour
sprite sampling and the sprite's logical size (a 42 × 48 frame at scale 2.5), screen
clamps, drag-end position reporting and hidden state retention. Add cleanup when
acquiring a new timer, window or global listener. Check both initialization failure and
final disposal; no new native acquisition belongs in a facade constructor or a model
mutation.

Drive package-local panel handlers and injected clocks in headless tests. Geometry/window
predicate tests do not instantiate a native `JWindow`; desktop layering, actual focus,
monitor changes, drag persistence, repeated hide/show and queued-event disposal need
human-run acceptance. Do not claim those checks from image previews.

```sh
./gradlew :jasper-buddy:test --tests '*BuddyCardTest' --tests '*BuddyShimmerTest' --tests '*BuddyColumnPanelTest' --tests '*BuddyDeckPanelTest' --tests '*BuddyBubblePanelTest' --tests '*BuddyDragFramesTest' --tests '*BuddyCompanionTest'
./gradlew :jasper-buddy:test --tests '*BuddyPlacementTest' --tests '*BuddyBubblePlacementTest' --tests '*BuddyColumnWindowTest' --tests '*BuddyDeckWindowTest' --tests '*BuddySpriteTest'
```

## Preview and verify

The five tools below use actual components headlessly (`-Djava.awt.headless=true`),
open no window and are opt-in, outside `check`:

```sh
./gradlew :jasper-buddy:buddyNotificationPreview
./gradlew :jasper-buddy:buddyGroupsPreview
./gradlew :jasper-buddy:buddyMotionPreview
./gradlew :jasper-buddy:buddyStandUpPreview
./gradlew :jasper-buddy:buddyPerformanceMeasurement
```

The [notification preview](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyNotificationPreview.java)
paints one running capsule beside the typing sprite at 2× density and writes 91 frames
(1.5 seconds at 60 Hz) per theme under `light/` and `dark/`, plus the settled
`light.png` and `dark.png`, in `jasper-buddy/build/reports/buddy-notifications/`. Its
optional first argument overrides the directory (`--args='/absolute/output/path'`). The
[motion preview](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyMotionPreview.java)
moves the sprite up and back down a 600 × 700 screen so the column flips, and writes
420 frames (7 seconds at 60 Hz) per theme under `jasper-buddy/build/reports/buddy-motion/`;
it takes no output-directory argument and prints the directory; both previews paint the sprite at scale 2
(84 × 96), smaller than the app's 105 × 120. Relative paths use the
Gradle subproject working directory.

`buddyGroupsPreview` writes dark/light 2× contact sheets of a singleton, stacks, a
filtered drawer and group overflow under `jasper-buddy/build/reports/buddy-groups/`.

`buddyStandUpPreview` writes normal and interrupted animated GIFs and timestamped contact sheets
under `jasper-buddy/build/reports/buddy-stand-up/`, using the production animator and runtime
sprites at window scale 2.5 and enlarged scale 5. GIF centisecond timing rounds the 225 ms
boundary to 230 ms while preserving the 500 ms total; contact labels show exact animator times.
`buddyOpenLaptopPreview` writes matching normal and interrupted opening GIFs and contact sheets
under `jasper-buddy/build/reports/buddy-open-laptop/`.

The [performance measurement](../jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyPerformanceMeasurement.java)
paints three running capsules at 2× density for 200 warm-up frames and 600 measured
frames, and prints the median, p95 and p99 paint time; an optional first argument
names a PNG file for the last frame. It excludes native window/drag scheduling, so do
not infer desktop throughput from it.

Finish a change with `./gradlew check`. It runs every module's tests, compiled
documentation examples, Javadoc and the bytecode architecture checks. `BuddyApiTest`
verifies the actual jar sprite and JDK-only loading. It reads the jar from the
`jasper.buddyJar` property that `:jasper-buddy:test` sets after building the jar, so run it
through Gradle. No build task runs the sprite tools or LibreSprite: Buddy builds and
tests from the committed artwork, so Pillow (and Swift, for the atlas packer) is needed only to [export, repack or verify it](../packaging/buddy/README.md#editing-and-exporting).
