# Finished-command notifications — design

**Status:** Designed. Development branch: `claude/command-notifications`, from main `1f1afeb`.

**Builds on:** the [desk buddy design](2026-09-14-jasper-desk-buddy-design.md) (the floating window, sprite strip, animator and bubble) and the [shell integration design](2026-09-16-jasper-shell-integration-design.md) (the OSC 133 C and D marks that bracket a command).

## Purpose

A command that takes a while finishes in a tab you are not looking at, and you find out when you next glance at it. Jasper already knows when a command starts and stops — the shell integration marks both — so it can tell you.

The desk buddy is the messenger. He gains the status bubble that was built and deferred (`BuddyBubbleContent.Style.MESSAGE` already carries a bold title, a grey detail line and a glyph — exactly the shape of the mock-up), and a typing animation so a long command has a visible "still going" state rather than only an ending.

## Confirmed decisions

- **Threshold:** `notifications.long_command_seconds`, default `10`, live. `0` disables the feature.
- **Which commands count:** one that finished in a tab that is not the selected tab of an active window, or any command at all when no Jasper window is active. Rejected: a different pane in the same visible tab (you can see it), and a pane in an unfocused window while another Jasper window is active.
- **When the buddy is off:** fall back to a native macOS notification, so turning the buddy off does not silently turn the feature off.
- **Sprites:** extend `packaging/buddy/generate.py` with Pillow, matching the existing pixel-art pipeline and palette. Pillow 12.3.0 is installed on the dev machine for this.
- **Typing starts at the threshold**, not at command start, so the animation means "this one is taking a while" and the buddy does not twitch for every `ls`. Starting it at C instead is a one-line change if that reads better in practice.

## Command duration

`TerminalSession.Listener.commandExecuted` carries the command, exit status and directory — **not** how long it took. It gains a fourth argument:

```java
default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory,
                             Duration duration) {
}
```

The session timestamps the **C** mark in `captureCommand` and subtracts in `flushPendingCommand`. A cycle that never saw a C — a prompt mark flushing a stale command — reports `Duration.ZERO`, which no threshold above zero can match, so it can never notify.

Timing uses a `LongSupplier` nanosecond clock defaulting to `System::nanoTime`, injected through the package-private constructor so tests drive it without sleeping. `System.nanoTime` is monotonic, so a wall-clock change mid-command cannot produce a negative or absurd duration.

## Deciding whether to notify

A small pure function, so the rule is testable without a window:

```java
/** Where a finished command ran, relative to what the user is looking at. */
record CommandOrigin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected) {}

static boolean shouldNotify(CommandOrigin origin, Duration ran, Duration threshold) {
    if (threshold.isZero() || ran.compareTo(threshold) < 0) return false;
    if (!origin.anyWindowActive()) return true;                 // Jasper is in the background
    return origin.ownWindowActive() && !origin.ownTabSelected(); // another tab, same window
}
```

The inputs come from state the app already maintains: `TerminalWindow` sets `WindowContent.active` from `windowActivated`/`windowDeactivated`, and `WindowContent` already computes `tab == currentTab()`. `anyWindowActive` is the OR of every open window's `isActiveAndOpen()`.

The excluded third case — a command finishing in a *different* Jasper window while one is focused — is deliberate and lives in this one function, so it is a single line to change.

## The buddy's side

**Animation.** `BuddyAnimator` gains a `WORKING` mode, entered when at least one tracked command has been running past the threshold and left when none are. It cycles the new typing frames at 4 fps. `WORKING` outranks the idle/sit/sleep progression — a working buddy does not fall asleep — and yields to `SPAWNING` and `GREETING`, which are short.

**Sprites.** Three new frames appended to the strip so existing column indices are untouched:

| # | Name | Meaning |
|---|---|---|
| 17 | `TYPE_A` | Sitting behind an open laptop, both hands on the keys, head down |
| 18 | `TYPE_B` | As A, hands swapped and head a pixel higher |
| 19 | `TYPE_REST` | As A with hands lowered, for the beat between bursts |

The strip becomes 20 frames, 840 × 48. `BuddySprite` validates width against `BuddyFrame.values().length`, so it follows automatically.

The laptop reuses the existing palette: shell outline for the case, the terminal's own background for the screen and the prompt green for a `>` glyph, matching the mock-up.

**Bubble.** On a qualifying finish the buddy shows a `MESSAGE` bubble:

- **title** — the command, newlines collapsed to `↵` the way the History palette already renders them, truncated to fit.
- **detail** — `Finished in 1m 12s` on success, `Exited 1 · 1m 12s` on failure.
- **glyph** — a check or a cross, tinted with the status bar's existing `Jasper.configSuccessForeground` / `Jasper.configErrorForeground`, so success and failure read the same way they already do elsewhere.

Clicking the bubble focuses that window, selects that tab and focuses that pane, through the `onActivate` hook `BuddyBubble` already takes. The bubble auto-hides on the existing five-second timer.

If a second command finishes while a bubble is up, the newest replaces it. A queue would make the buddy a backlog to work through, which is the opposite of the point.

## When the buddy is off

`buddy.enabled = false`, or the buddy hidden for the session, routes to a native notification instead. On macOS that is `osascript`, invoked with arguments rather than an interpolated script:

```
osascript -e 'on run argv' -e 'display notification (item 1 of argv) with title (item 2 of argv)' \
          -e 'end run' -- "<detail>" "<command>"
```

**This matters for safety.** The title is the command line, which is whatever ran — it can contain quotes, backslashes and newlines. Building an AppleScript string by interpolation would let a crafted command inject script. Passing them as `argv` removes the quoting problem entirely rather than trying to escape it.

The subprocess is launched on a short-lived executor, never on the EDT, and its failure is logged once rather than per notification. On any platform that is not macOS there is no fallback: the buddy is the only channel, which the documentation states.

The routing lives behind one interface with two real implementations — the buddy and the native notifier — satisfying the repository's "no interface without two implementations" rule.

## Setting

```toml
[notifications]
# Live. Notify when a command that ran at least this long finishes in a tab you are not looking at.
# Zero disables notifications entirely. Range 0-3600.
long_command_seconds = 10
```

## Errors and edge cases

- A command with no C mark (no shell integration, or a stale flush) has `Duration.ZERO` and never notifies. Users without integration get nothing, which is correct: Jasper cannot know how long anything took.
- A command still running when its pane closes never notifies; the tracker drops it with the pane.
- Changing the threshold live affects the next command, not one already running.
- A command that finishes while the buddy is mid-spawn queues until he is resting, rather than interrupting the spawn.
- `osascript` missing or failing logs once and is not retried for that session.
- The bubble's title is truncated for display only; the click target is still the right pane.

## Testing

- Duration: a session driven through A/B/C/D with an injected clock reports the exact duration; a cycle with no C reports zero.
- `shouldNotify`: every combination of the three booleans against a threshold, plus zero-threshold and below-threshold cases.
- Animator: `WORKING` is entered at the threshold and left when the last command finishes; a working buddy does not sleep; spawn still wins.
- Sprite: the strip is 20 frames wide and every new frame is non-empty and uses only palette colours.
- Bubble: title collapsing, both detail forms, the right glyph per exit status, and that activation focuses the originating pane.
- Native fallback: the notifier is called with the command and detail when the buddy is off, through a seam, so no test spawns `osascript`; and the argument vector is asserted to carry the raw command rather than an escaped script.
- Config: parsing, the 0-3600 range, zero disabling, and a live change reaching the tracker.

User-run afterwards: a long command in a background tab on the real desktop, watching the typing animation and the bubble, and the same with the buddy disabled.

## Out of scope

Notification history; per-command opt-out; Windows and Linux native notifications; sound; a badge on the tab or in the Dock; notifying for commands that are still running.
