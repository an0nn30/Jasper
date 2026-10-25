# Buddy architecture and lifetime

Start at the [embedding guide](../jasper-buddy/README.md). Buddy owns notice state and
presentation. Jasper owns command and plugin-activity policy, visibility reasons, native OS
notifications and saved position. Buddy depends on no other Jasper module; the
application composes the libraries' supported APIs.

## Package and owner map

| Package | Responsibility and outgoing Buddy dependencies |
| --- | --- |
| `config` | Resource-free options, builder and position values; JDK only. |
| `notice` | Resource-free identity and validated notice values; JDK only. |
| `internal.model` | EDT live-work notice ordering and bounds; depends on `notice`. |
| `internal.animation` | Caller-confined frame selection and spring calculations; JDK only, no Swing timers. |
| `internal.presentation` | EDT painting, layout, native surfaces and scheduling; depends on `config`, `notice`, model and animation. |
| `view` | Supported EDT facade over options, notice model and lazy presentation; depends on `config`, `notice`, model and presentation. |

Only the five top-level types in `config`, `notice` and `view` listed in the README
are supported. Java-public internal classes exist for package collaboration and must
not be imported by app code. [The bytecode guard](../gradle/application-architecture.gradle.kts)
checks Buddy's JDK-only dependencies, supported signatures, app usage and acyclic
package graph. [BuddyApiTest](../jasper-buddy/src/test/java/dev/jasper/buddy/architecture/BuddyApiTest.java)
also loads the produced jar with only the JDK as its parent classloader, decodes
its actual sprite and checks that no test support is in it. Test fixtures are a separate
test-only artifact: [BuddyTestSupport](../jasper-buddy/src/testFixtures/java/dev/jasper/buddy/view/BuddyTestSupport.java)
gives app tests a companion over a model they can inspect from any thread.

## Model ownership

[BuddyCompanion](../jasper-buddy/src/main/java/dev/jasper/buddy/view/BuddyCompanion.java)
owns one [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java).
[BuddyGroups](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyGroups.java) projects its visible notices into immutable group or notice entries with distinct identity namespaces.
The deck retains up to 50 notices (`MAX_NOTICES`) by source-qualified ID, newest first.
Posts replace and promote; suppression and group/title changes preserve order. A title
equal to the current one changes nothing. Snapshots are cached immutable lists, invalidated by mutations. The
deck's `generation()` counter changes only on a post; tests use it to tell a post from a
title change.

Every retained notice is live work, including suppressed entries. The column projects the
visible snapshot into the newest three groups/cards; the drawer shows visible members,
optionally filtered by source-qualified group ID. Suppression retains identity and arrival
order within the same 50-notice bound. A producer dismisses its notice when the work ends;
Buddy keeps no outcomes. Jasper posts how work ended as a notification instead ([guide](notifications.md)). The drawer's
× and **Clear all** dismiss displayed IDs from the deck directly; no host callback runs.

A notice retains its key, detail supplier and activation closure. Avoid UI objects in
keys and unnecessary captures in callbacks. Replacement, dismissal, capacity eviction
and clear release the model's references. Presentation may hold a snapshot while it is
hidden; hiding is deliberately not final disposal. Final companion close clears the
model and releases its presentation/options references. Producers must separately stop
delayed work and release their own captures.

## Presentation ownership

`show()` is the only facade operation that creates native presentation. Construction,
posting and hidden model updates work headlessly. Headless, unsupported-translucency/
always-on-top, and unavailable-sprite cases return false; the last two log a warning on
each attempt. Other initialization failures propagate after cleanup. A host decides
whether to retry; Jasper remembers unavailable presentation for the application lifetime.

[BuddyWindow](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyWindow.java)
owns the sprite window, animator timer, drag-frame owner, look-and-feel listener and
child surfaces. First realization creates the drawer and column; the right-click menu
bubble is created on demand. These are nonfocusable, translucent, always-on-top utility
windows. Initial coordinates are monitor-clamped once. Later option application updates
fonts, notice-card colours and callbacks without restoring those initial coordinates.
The menu bubble keeps its fixed dark material. All four surfaces compose complete frames in reusable
device-resolution ARGB buffers before copying them to their native windows.

| Surface/owner | Responsibility and lifecycle |
| --- | --- |
| `BuddyWindow` | The 105 × 120 sprite window. Pointer entry greets; a single click opens the drawer; a double click runs `activateHost`; the popup trigger (right-click, or Ctrl+click on macOS) opens the menu bubble; a press closes it. A drag starts after 3 pixels and reports `positionChanged` on release. Its one-shot timer follows the animator's next deadline. |
| `BuddyColumnWindow` / `BuddyColumnPanel` | At most three groups/cards (`BuddyDeckLayout.MAX_IN_COLUMN`), newest nearest the sprite; overflow counts groups/cards. Multiple members show at most two backing silhouettes and `N running`; clicking a stack opens its filtered drawer, while a singleton activates directly; a click beside them opens the drawer. Arrival and hover motion, running shimmer and live detail. Hidden when the deck is empty. |
| `BuddyDeckWindow` / `BuddyDeckPanel` | Visible notices, optionally group-filtered with a group-title header, wheel scrolling, activation, per-notice × dismissal and a **Clear all** row when there is more than one. No auto-hide timeout; pointer exit, activation, emptying/suppressing its displayed group and host hide close it, and it reopens scrolled to the top. Its height is capped by the usable screen height. |
| `BuddyBubble` / `BuddyBubblePanel` | Menu bubble with one entry, **Hide Jasper**, which runs `toggleRequested`; hides on pointer exit, activation or its five-second timeout. |
| `BuddyDragFrames` | Coalesces pointer positions into roughly 16 ms presentation frames and keeps ticking while the button is held; finish presents the latest position, cancel discards it. |

The column and drawer schedule roughly 16 ms frames while moving, hovering or while any
retained notice is `RUNNING`, and one-second refreshes otherwise while they show any
notice; they stop when hidden or empty. During drag, the column's own frame timer stops and drag
frames drive its placement and paint. Hiding stops timers and cancels drag frames while
retaining state; disposal removes the look-and-feel listener and disposes native
children. Queued timer callbacks check disposal/visibility before continuing. Native
lifecycle acceptance still requires desktop tests; pure geometry tests do not
instantiate these windows.

## Animation, drawing and placement

[BuddyAnimator](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java)
selects frames from a supplied clock and random source. It owns no timer. Most poses
advance in 125 ms steps.

| Behaviour | Timing |
| --- | --- |
| Spawn on `show` | Two seconds: fades in over the first half-second while sparkle overlays cycle until just before the first second ends, then waves for a second and stands. |
| Blink | Every 3 to 6 seconds for 120 ms; a quarter of standing blinks are winks. |
| Idle postures | Sits 20 seconds after the last attention, tucks into his shell at 60 seconds (400 ms), then sleeps through three Z frames at 600 ms each. |
| Greeting (`greet`, pointer entry) | One second of wave, hop and lean frames, then stands with the idle clock restarted; tucked or asleep, he first wakes over two 250 ms steps. Ignored during a spawn, greeting or wake, and while working or rising. |
| `poke` | Restarts the idle clock: standing he keeps his pending blink, sitting he stands, tucked or asleep he wakes without a greeting. Ignored during a spawn, greeting or wake, and while working or rising. |
| Working (`setWorking(true)`) | Sits behind the laptop and cycles type, type, type, type, rest at 250 ms per frame, never tucking or sleeping. Requested during a spawn, greeting or wake, it starts when that ends; cleared after typing, he rests his hands for 100 ms, closes the laptop for 125 ms, lowers it for 125 ms and rises for 150 ms, standing at 500 ms with the idle clock restarted. New work interrupts rising immediately. |

Buddy does not know command thresholds or focus policy. Idle-posture catch-up resolves
absolute deadlines in constant time; spawn, greeting and wake catch-up is bounded by
their fixed step counts, as is rising; working catch-up resolves elapsed typing cycles arithmetically.

[BubbleMotion](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BubbleMotion.java)
controls capsule arrival (a 32-pixel damped-spring travel over 600 ms, at full size and
opacity) and the 120 ms hover transition. [BubbleSpring](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BubbleSpring.java)
preserves position and velocity when a placement target changes, and counts as settled
after 800 ms. [BuddyColumnPlacement](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyColumnPlacement.java)
centres the column on Buddy, 30 pixels from his window: below him while his centre is in
the upper half of the screen, above him in the lower half, with an eight-pixel midpoint
dead band. A column that does not fit on one side goes on the other, and every
intermediate frame is clamped to the screen. Ordinary drag motion moves the anchor
directly while relative offsets spring through flips. The menu bubble sits 8 pixels to
Buddy's right, vertically centred, moving to his left when the right side does not fit
([BuddyBubblePlacement](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyBubblePlacement.java));
the drawer's window is placed the same way, so its capsules, inside the window's 32-pixel
shadow margin, stand 40 pixels from him.

[BuddyCard](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyCard.java)
is the shared 330 × 56 capsule painter for the column and the drawer. Options supply the
font family/attributes and explicit dark mode; the painter derives fixed styles and sizes
(title bold 13 pt, detail plain 13 pt) and caches the rasterized material per size and mode.
Live detail is read at paint time; null detail results or suppliers throwing a runtime
exception display empty text, and text is truncated by whole code points with an
ellipsis. A `RUNNING` notice's detail carries a sweeping highlight; `NEEDS_INPUT` and
`DEGRADED` show a dot, and `DOWN` a cross in the `UIManager` colour
`Jasper.semantic.status.danger` (fallback `#C45A53`). Hovering a capsule reveals an open
affordance without moving its text. The sprite is shaded pixel art: `BuddySprite` draws
each 84 × 96 source cell with nearest-neighbour sampling into its 42 × 48 logical frame at
the window's scale of 2.5, so 105 × 120. Its 23-frame, 1932 × 96 PNG lives beside `BuddySprite` in
the produced jar; a sheet of any other size fails to load. See
[sprite maintenance](../packaging/buddy/README.md) for the editable master and frame order.

## Application boundary and verification

[Jasper's BuddyIntegration](../jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java)
builds the options (the system font and the current theme's dark flag, reapplied on every
theme change), reads the saved position from `buddy.toml` in the Jasper home once at
composition and writes it from `positionChanged`, both through
[BuddyStateFile](../jasper-app/src/main/java/dev/jasper/app/persistence/BuddyStateFile.java).
Its read is strict (exactly `version = 1` and integer `x` and `y`, at most 4 KiB); an
unreadable file is logged and ignored. Its write goes to a temporary file moved into place,
atomically where the file system allows. BuddyIntegration shows Buddy while he is enabled
(`buddy.enabled`, or the session toggle flipped by the menu bubble's **Hide Jasper** and
the **Show Jasper**/**Hide Jasper** command) and the main window is showing and not
minimized, as `JasperApplication.windowStateChanged` reports it; child and auxiliary windows
do not count. It calls `greet` when a Jasper window (a `workspace.JasperWindow`) becomes
active, and owns the key listener that pokes on key presses in Jasper windows at most once a
second. Visibility reasons live in
[BuddyVisibility](../jasper-app/src/main/java/dev/jasper/app/notifications/BuddyVisibility.java).

Two producers post notices. [CommandNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java)
(source `terminal`, keyed by pane) owns thresholds and command completion: a command that
runs past `notifications.long_command_seconds`, or starts or continues out of sight, gets
a `RUNNING` task with detail "Running · <elapsed>" (none while the setting is 0).
`WorkspaceActivity.Presentation` supplies current parent-tab identity, strip title, pane
display title, watched state and activation. Notices group under source `terminal-tab`;
all panes in the selected parent tab of an active, showing, non-minimized workspace window
are suppressed. Settled workspace changes refresh context after moves and title changes;
activation resolves current ownership. Command lifetime and dismissal remain independent
of that visible projection, and qualified work keeps Buddy typing while suppressed. A long
command's outcome reaches the operating system through the notification centre's delivery and
`application.SystemNotices`, not through CommandNotifier.
[ActivityNotifier](../jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java)
(source: the plugin id) turns plugin activities into `RUNNING` tasks, whose detail
progress updates without a repost, and plugin connections into `CONNECTION` notices that
repost on each state change; a click raises Jasper, then runs the plugin's named action.
Workspace and plugin events are translated by application wiring; Buddy imports none of
those owners. At shutdown Jasper closes both producers before the companion.

[Maintenance recipes](buddy-maintenance.md) name the focused tests for each owner.
Headless paint previews establish neither native window layering/drag smoothness nor
desktop performance; those, and Windows and Linux desktop behaviour, need human-run
acceptance.
