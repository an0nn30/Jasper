# Buddy architecture and lifetime

Start at the [embedding guide](../jasper-buddy/README.md). Buddy owns notice state and
presentation. Jasper owns terminal-command policy, visibility reasons, native OS
notifications and saved position. Neither Buddy nor the terminal library depends on
the other; the application composes their supported APIs.

## Package and owner map

| Package | Responsibility and outgoing Buddy dependencies |
| --- | --- |
| `config` | Resource-free options, builder and position values; JDK only. |
| `notice` | Resource-free identity and validated notice values; JDK only. |
| `internal.model` | EDT notice ordering, acknowledgement and orphaning; depends on `notice`. |
| `internal.animation` | Caller-confined frame selection and spring calculations; JDK only, no Swing timers. |
| `internal.presentation` | EDT painting, layout, native surfaces and scheduling; depends on `config`, `notice`, model and animation. |
| `view` | Supported EDT facade over options, notice model and lazy presentation. |

Only the five top-level types in `config`, `notice` and `view` listed in the README
are supported. Java-public internal classes exist for package collaboration and must
not be imported by app code. [The bytecode guard](../gradle/application-architecture.gradle.kts)
checks Buddy's JDK-only dependencies, supported signatures, app usage and acyclic
package graph. [BuddyApiTest](../jasper-buddy/src/test/java/dev/jasper/buddy/architecture/BuddyApiTest.java)
also loads the produced jar with only the JDK as its parent classloader and decodes
its actual sprite. Test fixtures are a separate test-only artifact.

## Model ownership

[BuddyCompanion](../jasper-buddy/src/main/java/dev/jasper/buddy/view/BuddyCompanion.java)
owns one [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java).
The deck retains up to 50 notices by source-qualified ID, newest first. Posts replace,
promote and reset acknowledgement; title changes preserve order and acknowledgement.
Snapshots are cached immutable lists, invalidated by mutations. The post generation
counter is model bookkeeping exposed only internally; it is not a public subscription API.

The column filters for `notice.live() || !acknowledged`. The drawer includes all retained
notices. Orphaning removes activation without changing the state; the final-detail
overload also replaces the supplier unconditionally. A producer chooses whether its
outcome needs freezing. Orphaning and acknowledgement are independent operations.

A notice retains its key, detail supplier and activation closure. Avoid UI objects in
keys and unnecessary captures in callbacks. Replacement, dismissal, capacity eviction
and clear release the model's references. Presentation may hold a snapshot while it is
hidden; hiding is deliberately not final disposal. Final companion close clears the
model and releases its presentation/options references. Producers must separately stop
delayed work and release their own captures.

## Presentation ownership

`show()` is the only facade operation that creates native presentation. Construction,
posting and hidden model updates work headlessly. Headless, unsupported-translucency/
always-on-top, and unavailable-sprite cases return false. Other initialization failures
propagate after cleanup. A host decides whether to retry; Jasper remembers unavailable
presentation for the application lifetime.

[BuddyWindow](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyWindow.java)
owns the sprite window, animator timer, drag-frame owner, look-and-feel listener and
child surfaces. First realization creates the drawer and column; the right-click menu
bubble is created on demand. These are nonfocusable, translucent, always-on-top utility
windows. Initial coordinates are monitor-clamped once. Later option application updates
fonts, notice-card colors and callbacks without restoring those initial coordinates.
The menu bubble keeps its fixed dark material.

| Surface/owner | Responsibility and lifecycle |
| --- | --- |
| `BuddyWindow` | Sprite painting, single-click drawer, double-click host activation, menu toggle request and drag persistence callback. Its one-shot timer follows the animator's next deadline. |
| `BuddyColumnWindow` / `BuddyColumnPanel` | At most three live/unseen capsules, newest nearest the sprite; arrival/hover motion, running shimmer and live detail. |
| `BuddyDeckWindow` / `BuddyDeckPanel` | Full retained history with scrolling, activation, per-notice dismissal and clear-all. No auto-hide timeout; pointer exit and host hide dismiss the drawer. |
| `BuddyBubble` / `BuddyBubblePanel` | Menu bubble; hides on exit, activation or its five-second timeout. |
| `BuddyDragFrames` | Coalesces pointer positions into roughly 16 ms presentation frames; finish presents the latest position, cancel discards it. |

The column and drawer schedule roughly 16 ms frames while moving or shimmering and
one-second refreshes for other live detail; they stop scheduling when settled with no
live detail. During drag, the column's own frame timer pauses and drag frames drive its
placement and paint. Hiding stops timers and cancels drag frames while retaining state;
disposal removes the look-and-feel listener and disposes native children. Queued timer
callbacks check disposal/visibility before continuing. Native lifecycle acceptance still
requires desktop tests; pure geometry tests do not instantiate these windows.

## Animation, drawing and placement

[BuddyAnimator](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java)
selects frames from a supplied clock and random source. It owns no timer. Showing starts
a two-second fade/sparkle/wave sequence. Idle posture deadlines sit after 20 seconds
and tuck after 60, followed by sleep. Greeting and activity wake/reset idle behavior.
Host `setWorking(true)` selects typing; Buddy does not know command thresholds or focus
policy. Idle/posture catch-up resolves absolute deadlines; the working loop currently
advances through each elapsed typing step, so do not describe all animation catch-up
as constant-time.

[BubbleMotion](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BubbleMotion.java)
controls arrival displacement and hover timing. [BubbleSpring](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BubbleSpring.java)
preserves position and velocity when a placement target changes.
[BuddyColumnPlacement](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyColumnPlacement.java)
chooses above/below placement with an eight-pixel midpoint dead band and screen-fit
constraints; every intermediate frame is clamped. Ordinary drag motion moves the anchor
directly while relative offsets spring through flips.

[BuddyCard](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyCard.java)
is the shared capsule painter. Options supply the font family/attributes and explicit
dark mode; the painter derives fixed styles/sizes. Live detail is read at paint time;
null detail results or suppliers throwing a runtime exception display empty text. The sprite uses nearest-neighbor
integer scaling. Its 20-frame, 840 × 48 PNG lives beside `BuddySprite` in the produced jar.
See [sprite maintenance](../packaging/buddy/README.md) for the generated master and order.

## Application boundary and verification

[Jasper's BuddyIntegration](../jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java)
applies app visibility and appearance, converts saved position into options and owns the
typing-activity listener. [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java)
owns producer identities, thresholds, command completion and native notifications.
Workspace events are translated by application wiring; Buddy imports none of those owners.

[Maintenance recipes](buddy-maintenance.md) name the focused tests for each owner.
The [verification report](app-refactor-verification.md) records full checks and remaining
native/Windows acceptance. Headless paint previews establish neither native window
layering/drag smoothness nor desktop performance.
