# Buddy thought column — design

**Status:** Designed. Development branch: `claude/command-notifications` (continuing), from `9e8d61a`.

**Revises:** the [buddy bubble deck design](2026-09-17-jasper-buddy-bubble-deck-design.md), which is
implemented and shipped on this branch. That design put a permanent stack of cards beside the buddy.
This moves the live ones above his head and demotes the rest to a history he holds for you.

## Purpose

The shipped deck sits *beside* the buddy and never empties. Both are wrong, and they are the same
mistake: the deck is a record pretending to be a status display.

Position is not decoration — it is a claim about lifetime. Beside reads as a **panel**: a document
attached to an application, something you consult. Above the head reads as a **thought**: what he is
holding right now. A permanent stack of fifty finished commands floating over the screen is noise,
and the only reason a pet-style notifier feels calm is that it shows nothing when nothing is
happening.

So the deck splits into two surfaces:

- **The thought column, above his head.** Only what is live or has not been seen yet. Empty and
  silent most of the time.
- **The drawer, behind him.** Everything that happened this run, on demand.

## Confirmed decisions

- **Live above, history behind.** The column carries what is happening; single-clicking the buddy
  opens the drawer of everything, which is the surface the previous design already built.
- **Looking at it clears it.** Focusing the pane a notice came from acknowledges it, so it leaves the
  column and settles into the drawer. Explicit dismissal still works. The column self-empties as you
  work, rather than needing to be tidied.
- **Two lifetimes, not one.** A **task** begins and ends; a **connection** is up indefinitely and
  only ever *changes*. A model built solely around completion cannot express a tunnel, so the kind is
  part of the notice from the start.
- **"Blocked on you" is a first-class state**, designed now and detected later. For the shell we
  already get it free — OSC 133 `A` means "at a prompt, ready for input", so a finished command and a
  shell waiting on you are the same event. A sub-process prompting (`sudo`, `[y/N]`) needs the pty's
  foreground process group, which is separate work.
- **Newest nearest his head**, the column growing upward, flipping below him when he is too near the
  top of the screen.

## The model

`BuddyNotice` gains a kind, and its states become specific to that kind:

```java
/** What sort of thing this is, which is what decides when it stops mattering. */
enum Kind {
    /** Begins and ends: a command, a file transfer. */
    TASK,
    /** Up until it is not: an SSH session, a tunnel. It never "completes". */
    CONNECTION
}

enum State {
    RUNNING,      // TASK: in flight
    NEEDS_INPUT,  // TASK: blocked on you
    DONE,         // TASK: ended well
    FAILED,       // TASK: ended badly
    UP,           // CONNECTION: healthy
    DEGRADED,     // CONNECTION: reconnecting
    DOWN;         // CONNECTION: lost

    /** A TASK is never UP; a CONNECTION is never DONE. */
    boolean fits(Kind kind) {
        return kind == Kind.TASK
            ? this == RUNNING || this == NEEDS_INPUT || this == DONE || this == FAILED
            : this == UP || this == DEGRADED || this == DOWN;
    }
}
```

One enum with a legality check rather than a sealed hierarchy: the column and the drawer both want to
ask plain questions of any notice regardless of kind, and two type hierarchies would make every such
question a pattern match for no gain. The compact constructor rejects a mismatch, so an impossible
notice cannot be built.

Two derived questions carry the whole behaviour, and nothing outside the notice re-derives them:

```java
/** Still happening, so it belongs above his head whether or not you have seen it. */
boolean live() { return state == RUNNING || state == NEEDS_INPUT || state == UP || state == DEGRADED; }

/** Wants you specifically: it ended, it broke, or it is waiting on you. */
boolean wantsAttention() { return state != RUNNING && state != UP; }
```

Acknowledgement is deck state, not notice state — it is about the reader, not the thing:

```java
void acknowledge(String source, Object key);   // you looked at it
boolean acknowledged(String source, Object key);
```

**Column membership** is then one line: `live() || !acknowledged`. A running command is there because
it is running; a finished one is there until you look; a healthy connection is there because it is up.
Posting a *new* state over a key clears its acknowledgement, so a tunnel that drops after you
acknowledged it comes back.

## The column

`BuddyColumn` replaces the collapsed half of `BuddyDeckPanel`; the expanded half becomes the drawer.

- Bubbles stack upward from just above his head, newest at the bottom, nearest him. A new arrival
  appears at the bottom and lifts the ones above it, which is the direction a thought rises and keeps
  the newest closest to the thing your eye is already on.
- A **thought tail** — two small circles tapering from his head to the lowest bubble — because the
  whole point of the position is the metaphor, and without the tail it is just a floating list.
- Three bubbles at most, then a count. Clicking the column opens the drawer.
- When he is too close to the top of the screen the column flips below him and the order inverts, so
  the newest stays nearest his head either way. `BuddyBubblePlacement` already flips left-to-right
  for the same reason.
- An empty column draws nothing and its window is hidden. This is the resting state and it should be
  the common one.

## What a bubble says

| Kind | State | Title | Detail |
|---|---|---|---|
| TASK | `RUNNING` | the command | `Running · 1m 12s`, ticking |
| TASK | `NEEDS_INPUT` | the command | `Waiting for you` |
| TASK | `DONE` | the command | `Finished in 1m 12s` |
| TASK | `FAILED` | the command | `Exited 2 · 1m 12s` |
| CONNECTION | `UP` | the host | `Connected · 20m` |
| CONNECTION | `DEGRADED` | the host | `Reconnecting…` |
| CONNECTION | `DOWN` | the host | `Lost · 14:32` |

Detail stays a `Supplier<String>`, so a running task ticks and a future transfer reports bytes through
the same field without the column learning a second notion of progress.

## The drawer

Unchanged from the shipped design except for how it opens and what it holds.

- **Single-clicking the buddy opens it.** Double-click already raises the terminal and right-click
  already opens his menu; single click is free, so no existing gesture moves.
- It holds everything this run, acknowledged or not, newest first, bounded at 50 with the oldest
  falling off, with per-card dismiss and **Clear all**. Nothing is persisted across restarts.
- Dismissing from the drawer removes a notice from the column too; they are one collection.

## Routing

`CommandNotifier` keeps its shape and gains one call. `WindowContent` already knows when a pane takes
focus, which is the only new signal:

- **`started` / `finished`** — unchanged, except the notice now carries `Kind.TASK`.
- **`looked(key)`** — the pane took focus: acknowledge its notice so it leaves the column. Called from
  the same focus path that already drives `ownPaneFocused`.
- **`closed`** — orphan, keeping the outcome, **and acknowledge**. Its pane is gone, so focusing it
  is no longer possible and it could otherwise sit in the column for the rest of the session with no
  way to clear it but an explicit dismissal.

The system-notification rule does not change: everything except the pane you were typing in.

## Errors and edge cases

- Acknowledging a running task does nothing visible — it is `live()`, so it stays in the column until
  it ends. Acknowledgement only matters at the moment attention is wanted.
- A notice acknowledged and then re-posted with a new state is unacknowledged again, or a flapping
  tunnel would go quiet after the first drop.
- A task that finishes in the pane you are already focused on is acknowledged on the spot — the
  notifier already receives `origin.ownPaneFocused()` for the notification rule — so it never reaches
  the column at all. You watched it happen; he does not need to tell you.
- The column and the drawer read the same collection, so a card dismissed in one cannot linger in the
  other.
- A notice that is never acknowledged and never dismissed stays in the column, by design: that is the
  missed-it case the whole feature exists for. The bound of 50 and the orphan rule above are what stop
  it growing without limit.
- With no shell integration nothing is posted and the column never appears — unchanged, and still
  stated in the documentation.
- A screen with no room above *or* below the buddy clamps to the usable area, as the bubble does now.

## Testing

- `State.fits(Kind)`: every combination, so an impossible notice cannot be constructed.
- `live()` and `wantsAttention()` across all seven states.
- Membership: a running task is in the column; a finished one leaves on acknowledgement; a re-post
  after acknowledgement puts it back; an acknowledged running task stays.
- Column geometry: newest nearest the head, the stack growing away from him, the flip below when
  there is no room above and the order inverting with it, the tail sitting between head and bubble.
- The drawer still holds what the column has dropped, and a dismissal reaches both.
- Routing: focusing a pane acknowledges exactly that pane's notice and no other.

User-run afterwards: a long command in a background tab, watching the bubble rise above his head,
then visiting the pane and watching it leave the column but remain in the drawer.

## Out of scope

Detecting a blocked sub-process (the `NEEDS_INPUT` state is defined and rendered; nothing sets it for
anything but the shell's own prompt). The connection producers themselves — `Kind.CONNECTION` and its
three states exist so the column is not cornered, but no code posts one yet, and **grouping healthy
connections into a single "2 tunnels up" summary is deliberately deferred until something produces
them**, rather than shipping untested machinery for a caller that does not exist. Also out: a
keyboard route into the column, sound, an attention animation for the buddy himself, and persisting
anything across restarts.
