# Jasper Buddy — embedding and maintenance

`jasper-buddy` is the JDK-only library behind Jasper's desk buddy: a pixel-art Jasper in a
small always-on-top window who shows live work (running commands, plugin activities,
connections) as capsules beside him. It has no dependency on the Jasper app, the terminal
library, the UI kit (`jasper-ui`), the SDK, TOML, FlatLaf or JetBrains-specific APIs. Use
a Java 25 runtime; repository builds require the JetBrains vendor's JBR 25 and use the
Gradle wrapper.
Add `implementation(project(":jasper-buddy"))` in this multi-project build. Buddy needs
no external runtime library; its host must provide a runtime with Swing/AWT and ImageIO.
In Jasper the buddy is off by default; the `buddy.enabled` setting (**Show Jasper**) turns
it on ([Desk Buddy guide](../docs/notifications.md#desk-buddy),
[configuration](../docs/configuration.md#desk-buddy)).

## Supported API

Only these top-level types and their nested values/builders are supported:

- [BuddyCompanion](src/main/java/dev/jasper/buddy/view/BuddyCompanion.java): the EDT facade
  over the notice model and lazy presentation, closed by the host. Its one public
  constructor takes `BuddyOptions`; every public method returns `void` except `show()`.
- [BuddyNotice](src/main/java/dev/jasper/buddy/notice/BuddyNotice.java): validated kind/state,
  title, nonblocking detail supplier, activation callback and optional
  `Group(BuddyNoticeId id, String title)`; `wantsAttention()`,
  `sameAs(BuddyNoticeId)` and `State.fits(Kind)`.
- [BuddyNoticeId](src/main/java/dev/jasper/buddy/notice/BuddyNoticeId.java): source plus
  stable opaque key. A source does not have to be a terminal.
- [BuddyOptions](src/main/java/dev/jasper/buddy/config/BuddyOptions.java): explicit resolved
  font, dark mode, optional initial position and host callbacks, with builder/toBuilder.
- [BuddyPosition](src/main/java/dev/jasper/buddy/config/BuddyPosition.java): AWT desktop coordinates.

Everything under `internal` is an implementation detail, including Java-public classes
needed for cross-package access. No supported method exposes those types, a native window,
a mutable model, or an executor. The five-type allowlist and package dependency graph are
checked from compiled classes by [`verifyApplicationArchitecture`](../gradle/application-architecture.gradle.kts).
Plugins cannot reach Buddy: Jasper's plugin class loader refuses `dev.jasper.buddy` classes.
A plugin shows live work through the SDK's [activity API](../docs/sdk-reference.html#package-dev.jasper.sdk.activity),
which Jasper turns into notices ([application boundary](../docs/buddy-architecture.md#application-boundary-and-verification)).

## Minimal host

Run this entire body on the Swing EDT. The same snippet is compiled and executed in
[BuddyExamplesTest](src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java).

<!-- example:buddy -->
```java
var options = BuddyOptions.builder(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13))
    .dark(true).activateHost(() -> {}).toggleRequested(() -> {}).build();
try (var buddy = new BuddyCompanion(options)) {
    var id = new BuddyNoticeId("example", new Object());
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.RUNNING,
        () -> "Compiling", () -> {}));
    buddy.dismiss(id);
    buddy.hide();
    buddy.applyOptions(options.toBuilder().dark(false).build());
}
```

In an interactive host, call `buddy.show()` on the EDT and keep the companion until
host shutdown. The example is headless and exercises the model and lifecycle without
opening a window. `show()` returns false when headless, when the toolkit lacks
always-on-top or per-pixel translucency, or when the sprite cannot be loaded; the last
two log a `System.Logger` warning starting "Desk buddy disabled". Other native runtime
failures propagate to the host; partially initialized presentation is disposed before
the failure is rethrown. The library does not cache a failed `show()`: the next call
tries again. Jasper's
[BuddyIntegration](../jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java)
remembers unavailable presentation for the rest of the session instead of retrying on
each UI event.

## Threading and lifetime

Construct and operate `BuddyCompanion` on the Swing EDT. This includes `close()`, and
every call after close; off-EDT calls throw `IllegalStateException`. Constructing values
or building options requires no EDT and performs no I/O. A detail supplier or host callback
runs on the EDT when presentation needs it, so it must be fast and nonblocking. Buddy
reads a shown notice's detail on every paint: about 60 times a second while any retained
notice is `RUNNING`, and at least once a second otherwise. Do not read a live terminal
buffer or wait for a worker from one of these callbacks.

| Operation | Model and resource effect |
| --- | --- |
| Construction | Allocates the model only: no native window, timer or worker. |
| `post`, `updateTitle`, `updateContext`, `dismiss`, `clear` | Update the model and refresh existing presentation; never create a native window. Posting while hidden is supported. |
| `show` | Creates presentation on the first successful call, then shows Buddy with the two-second spawn animation; returns whether presentation is available. A call while visible changes nothing and returns true. |
| `hide` | Stops presentation timers and pending drag frames, hides every Buddy window and resets the animation; retains notices, options and working intent. The next `show` plays the spawn again. |
| `setWorking` | Sets host-supplied typing intent, including before first show; clearing it after typing plays a 500 ms laptop-close and stand-up transition, interrupted immediately by new work. No threshold policy lives in Buddy. |
| `greet` | On visible presentation, plays the one-second greeting, waking him first if he is tucked or asleep. Jasper calls it when one of its workspace windows becomes active. |
| `poke` | On visible presentation, restarts the idle clock without a greeting. Jasper calls it on key presses in its workspace windows, at most once a second. |
| `applyOptions` | Replaces appearance and future host callbacks; see [host appearance and position](#host-appearance-and-position). |
| `close` | Once-only final disposal: stops presentation, clears notices and releases retained options/callbacks. |
| Calls after close | No mutation or presentation work; `show()` returns false. EDT enforcement remains active. |

`greet` and `poke` never create a window. For an open companion, required inputs must be
nonnull (`NullPointerException`) and titles nonblank (`IllegalArgumentException`); an
unknown valid notice ID is a harmless no-op. After close, operations return before
validating other inputs. The host must stop delayed producers before closing the companion.
See [architecture and lifetime](../docs/buddy-architecture.md) for internal owners and
[maintenance recipes](../docs/buddy-maintenance.md) for changes and focused tests.

## Notice identity and state

`BuddyNoticeId` equality uses both `source` and `key`; the source is not trimmed or
normalized. Choose a distinct nonblank source for each producer family, and a stable
nonnull key within that source. Keys must keep stable equality/hashCode and should not
retain UI objects. Reusing an ID deliberately replaces that notice; `BuddyNotice.equals`
also compares callbacks and is not notice identity.

| Kind | Accepted states | Wants attention | Capsule marker |
| --- | --- | --- | --- |
| `TASK` | `RUNNING`, `NEEDS_INPUT` | `NEEDS_INPUT` | `RUNNING`: a highlight sweeps the detail text; `NEEDS_INPUT`: a dot |
| `CONNECTION` | `UP`, `DEGRADED`, `DOWN` | `DEGRADED`, `DOWN` | `DEGRADED`: a dot; `DOWN`: a cross |

Buddy shows live work only. `post(BuddyNotice)` promotes its ID to newest and makes it
visible; `post(BuddyNotice, boolean visible)` can retain it suppressed. The six-argument
notice constructor remains ungrouped; its seven-argument form takes an optional `Group`.
`updateContext(BuddyNoticeId, BuddyNotice.Group, boolean visible)` changes grouping and
visibility in place; a null group makes the notice ungrouped. Context and title updates
preserve arrival order and never recreate a dismissed ID. The model holds at most 50
notices, including suppressed notices, dropping the oldest beyond that limit.

The column shows the newest three groups or ungrouped cards. Groups sort by their newest
member; a singleton uses the group title and activates directly. Multiple members form a
stack with at most two backing silhouettes and exact `N running` detail. Clicking it
opens a drawer filtered to that group without activating a member. The drawer uses the
group title as its header and each member's own title and live detail; duplicate member
titles gain stable discriminators. Clicking Buddy opens all visible notices with group
context. Overflow counts groups/cards. Clicking a drawer entry runs its activation.

A producer dismisses its notice when work ends. The drawer's × dismisses one entry;
**Clear all**, shown for multiple entries, dismisses exactly the displayed entries,
preserving suppressed notices and other groups. Dismissal removes the model entry without
notifying the host; another post of the same ID shows it again. A filtered drawer closes
when its group empties or is suppressed, and can remain open with one member. Buddy keeps
no outcomes: hosts report those elsewhere (Jasper posts a notification). Cancel producer
work first, then dismiss. Hiding the companion does not stop producers.

## Host appearance and position

| Builder method | Default | Effect |
| --- | --- | --- |
| `builder(font)`, `primaryFont(font)` | required | Font family and attributes for all text. Buddy fixes the sizes (capsule title bold 13 pt, detail and menu entry plain 13 pt), so the font's point size is not a scale control. |
| `dark(boolean)` | `true` | Dark or light capsule material, independent of the installed look and feel. The menu bubble is always dark. |
| `initialPosition(BuddyPosition)` | none | Top-left of Buddy's window at first successful realization; `null` clears it. |
| `positionChanged(Consumer<BuddyPosition>)` | no-op | Called on the EDT once per completed drag with the window's new top-left. |
| `activateHost(Runnable)` | no-op | Called when the user double-clicks Buddy. |
| `toggleRequested(Runnable)` | no-op | Called from the right-click menu bubble's single entry, **Hide Jasper**; the host applies its own visibility policy. |

`toBuilder()` preserves all fields and callback references. The builder rejects null
callbacks. `applyOptions` replaces appearance and future host callbacks; Buddy re-lays out
its open surfaces with the new font and material.

Initial position is used only at first successful native realization; a later options
update does not undo a drag. Negative coordinates are valid for secondary monitors.
Buddy keeps the position when at least half his window lies on a usable screen area
(screen bounds less the Dock, menu bar or taskbar insets), and otherwise moves it fully
onto the screen it overlaps most, or the nearest one. Without an initial position he starts 24 pixels in
from the bottom-right corner of the primary screen's usable area. A drag starts after
the pointer moves 3 pixels. Position is reported on completed drag release, not on
pointer frames. Hiding cancels a pending drag and is not an extra persistence flush.

The cross on a `DOWN` connection is the one colour Buddy takes from the look and feel:
the `UIManager` colour `Jasper.semantic.status.danger` (Jasper's theme engine publishes
it), else `#C45A53`. Buddy listens for `lookAndFeel` changes and refreshes its column
and drawer. The host owns persistence, visibility policy, activation and producer
cancellation; Buddy accepts values and requests.

## Verify and navigate

```sh
./gradlew :jasper-buddy:test :jasper-buddy:javadoc verifyApplicationArchitecture
./gradlew check
```

These checks are headless. Opt-in image previews are documented in the
[maintenance guide](../docs/buddy-maintenance.md); they do not create native windows.
Native window/drag/monitor checks are human-run. The jar includes its PNG under
`dev/jasper/buddy/internal/presentation/jasper-buddy.png`; tests decode it from the jar
and load the facade using only the JDK as parent classloader.

See [Buddy architecture](../docs/buddy-architecture.md), [Buddy maintenance](../docs/buddy-maintenance.md),
[application integration](../docs/app-architecture.md), [producer recipes](../docs/app-maintenance.md#new-buddy-notice-producer)
and [sprite assets](../packaging/buddy/README.md). Value tests live in `config` and
`notice`, model tests in `internal/model`, clock-driven animation tests in
`internal/animation`, geometry/paint and drag-frame tests in `internal/presentation`,
API/lifecycle tests in `view`, the jar test in `architecture` and the README example in
`documentation`.
