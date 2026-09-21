# Jasper Buddy — embedding and maintenance

`jasper-buddy` is a JDK-only desktop companion library. It has no dependency on the
Jasper app, terminal, TOML, FlatLaf or JetBrains-specific APIs. Use a Java 25 runtime;
repository builds require the JetBrains vendor's JBR 25 and use the Gradle wrapper.
Add `implementation(project(":jasper-buddy"))` in this multi-project build. Buddy needs
no external runtime library; its host must provide a runtime with Swing/AWT and ImageIO.

## Supported API

Only these top-level types and their nested values/builders are supported:

- [BuddyCompanion](src/main/java/dev/jasper/buddy/view/BuddyCompanion.java): notice model
  and lazy presentation, closed by the host.
- [BuddyNotice](src/main/java/dev/jasper/buddy/notice/BuddyNotice.java): validated kind/state,
  title, nonblocking detail supplier and nullable activation callback.
- [BuddyNoticeId](src/main/java/dev/jasper/buddy/notice/BuddyNoticeId.java): source plus
  stable opaque key. A source does not have to be a terminal.
- [BuddyOptions](src/main/java/dev/jasper/buddy/config/BuddyOptions.java): explicit resolved
  font, dark mode, optional initial position and host callbacks, with builder/toBuilder.
- [BuddyPosition](src/main/java/dev/jasper/buddy/config/BuddyPosition.java): screen coordinates.

Everything under `internal` is an implementation detail, including Java-public classes
needed for cross-package access. No supported method exposes those types, a native window,
a mutable model, or an executor. There is no plugin ABI or persistence service here.
The five-type allowlist and package dependency graph are checked from compiled classes
by [`verifyApplicationArchitecture`](../gradle/application-architecture.gradle.kts).

## Minimal host

Run this entire body on the Swing EDT. The same snippet is compiled and executed in
[BuddyExamplesTest](src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java).

<!-- example:buddy -->
```java
var options = BuddyOptions.builder(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13))
    .dark(true).activateHost(() -> {}).toggleRequested(() -> {}).build();
try (var buddy = new BuddyCompanion(options)) {
    var id = new BuddyNoticeId("example", new Object());
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.DONE,
        () -> "Finished", () -> {}));
    buddy.acknowledge(id);
    buddy.hide();
    buddy.applyOptions(options.toBuilder().dark(false).build());
}
```

In an interactive host, call `buddy.show()` on the EDT and retain the companion until
host shutdown. The example is headless and intentionally exercises model/lifecycle
without opening a window. `show()` returns false when headless, unsupported or the
sprite is unavailable. Other native runtime failures propagate to the host; partially
initialized presentation is disposed before construction failure is rethrown. Jasper's
[BuddyIntegration](../jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java)
remembers unavailable presentation instead of retrying on each UI event. The library
itself does not cache a failed `show()` attempt.

## Threading and lifetime

Construct and operate `BuddyCompanion` on the Swing EDT. This includes `close()`, and
every call after close; off-EDT calls throw `IllegalStateException`. Constructing values
or building options requires no EDT and performs no I/O. A detail supplier or host callback
runs on EDT when presentation needs it, so it must be fast and nonblocking. Do not read a
live terminal buffer or wait for a worker from one of these callbacks.

| Operation | Model and resource effect |
| --- | --- |
| Construction | Allocates the model only: no native window, timer or worker. |
| `post`, `updateTitle`, `acknowledge`, `dismiss`, `orphan`, `clear` | Update the model and refresh existing presentation; never create a native window. Posting while hidden is supported. |
| `show` | Lazily creates presentation; returns whether it is available. Repeated calls while visible reuse it. |
| `hide` | Stops presentation timers and pending drag frames, hides dependent windows, retains notices, options and working intent. |
| `setWorking` | Sets host-supplied typing intent, including before first show; no threshold policy lives in Buddy. |
| `greet`, `poke` | Act only on existing visible presentation; do not realize a window. |
| `close` | Once-only final disposal: stops presentation, clears notices and releases retained options/callbacks. |
| Calls after close | No mutation or presentation work; `show()` returns false. EDT enforcement remains active. |

For an open companion, required inputs must be nonnull and titles nonblank; an unknown
valid notice ID is a harmless no-op. After close, operations return before validating
other inputs. The host must stop delayed producers before closing the companion.
See [architecture and lifetime](../docs/buddy-architecture.md) for internal owners and
[maintenance recipes](../docs/buddy-maintenance.md) for changes and focused tests.

## Notice identity and state

`BuddyNoticeId` equality uses both `source` and `key`. Choose a distinct nonblank source
for each producer family, and a stable nonnull key within that source. Keys must keep
stable equality/hashCode and should not retain UI objects. Reusing an ID deliberately
replaces that notice; `BuddyNotice.equals` also compares callbacks and is not notice identity.

| Kind | Accepted states | Live while activation is nonnull |
| --- | --- | --- |
| `TASK` | `RUNNING`, `NEEDS_INPUT`, `DONE`, `FAILED` | `RUNNING`, `NEEDS_INPUT` |
| `CONNECTION` | `UP`, `DEGRADED`, `DOWN` | `UP`, `DEGRADED` |

A post promotes its ID to newest and resets acknowledgement. The model holds at most
50 notices, dropping the oldest beyond that limit. Updating a title does neither.
The collapsed column contains live or unacknowledged notices and shows at most three;
the drawer provides the retained history. Acknowledgement marks a notice seen without
removing it or changing its state. Dismiss removes one notice; clear removes all.

Null activation means orphaned, even if the state remains `RUNNING` or `UP`. An orphan
is never live. `orphan(id)` removes activation but retains state and the existing detail
supplier. `orphan(id, finalDetail)` also replaces the supplier with fixed text **regardless
of state**. The producer must choose the overload: freeze unfinished details, preserve
completed outcome wording. Cancel producer work first, orphan, then acknowledge if the
notice should leave the column. Acknowledgement is separate; an unseen orphan remains
in the column until acknowledged. Hide and disable do not close producers.

## Host appearance and position

`BuddyOptions.builder(font)` requires a resolved nonnull font and defaults to dark mode,
no initial position and inert callbacks. `toBuilder()` preserves all fields and callback
references. Buddy derives fixed presentation styles and sizes from the supplied font;
changing its point size alone is not a UI-scale control. `dark` explicitly selects
notice-card materials independently of the installed look and feel; the menu bubble
keeps its fixed dark material.

`applyOptions` replaces appearance and future host callbacks. Initial position is used
only at first successful native realization and is clamped to usable screens; a later
options update does not undo a drag. Negative coordinates are valid for secondary
monitors. Position is reported on completed drag release, not on pointer frames. Hiding
cancels a pending drag and is not an extra persistence flush. The host owns persistence,
visibility policy, activation and producer cancellation; Buddy accepts values and requests.

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
[application integration](../docs/app-architecture.md), [producer recipes](../docs/app-maintenance.md)
and [verification/manual gaps](../docs/app-refactor-verification.md). Model tests live in
`internal/model`, clock-driven animation tests in `internal/animation`, geometry/paint
and drag-frame tests in `internal/presentation`, and API/lifecycle tests in `view`.
