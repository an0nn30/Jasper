# Jasper Buddy — embedding and maintenance

`jasper-buddy` is a JDK-only desktop companion library. It has no dependency on the
Jasper app, terminal, TOML, FlatLaf or JetBrains-specific APIs. Use a Java 25 runtime;
repository builds use JBR 25. Add `implementation(project(":jasper-buddy"))` in this build.

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
needed for cross-package access. There is no plugin ABI or persistence service here.

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
sprite is unavailable. The application remembers that outcome to avoid repeated attempts.

Construction allocates no window, timer or worker. Posting while hidden is supported.
`hide()` stops presentation work and retains notices; `close()` is idempotent and clears
notices, activation/detail closures, timers, windows and the look-and-feel listener.
Every operation, including close and calls after close, requires EDT. After close,
mutations are inert and show returns false. Supplier/callback work must be short and
nonblocking; the caller owns cancellation of queued producer work.

IDs replace and promote matching notices. The model retains at most 50, newest first.
Acknowledgement does not alter outcomes. Closing a producer should cancel its work,
orphan its notices, freeze an unfinished detail and acknowledge them; a completed
notice retains its completion wording. Hide and disable are not producer close.

The host resolves a real font and dark mode. Option changes update presentation and
future callbacks without applying initialPosition again. Position is read only at first
native realization, clamped to usable screens, and reported at drag end. Persistence,
visibility policy and host activation belong to the embedding app.

## Verify and navigate

```sh
./gradlew :jasper-buddy:test :jasper-buddy:javadoc verifyApplicationArchitecture
./gradlew :jasper-buddy:buddyNotificationPreview
```

The preview is opt-in and headless; it renders real components into images. Native
window/drag/monitor checks are human-run. The jar includes its PNG under
`dev/jasper/buddy/internal/presentation/jasper-buddy.png`; tests decode it from the jar
and load the facade using only the JDK as parent classloader.

See [architecture](../docs/app-architecture.md), [feature recipes](../docs/app-maintenance.md)
and [verification/manual gaps](../docs/app-refactor-verification.md). Model tests live in
`internal/model`, clock-driven animation tests in `internal/animation`, geometry/paint
and drag-frame tests in `internal/presentation`, and API/lifecycle tests in `view`.
