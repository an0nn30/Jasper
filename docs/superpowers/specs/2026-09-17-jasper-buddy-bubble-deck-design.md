# Buddy bubble deck — design

**Status:** Designed. Development branch: `claude/command-notifications` (continuing), from `612a7d6`.

**Revises:** the [finished-command notifications design](2026-09-16-jasper-finished-command-notifications-design.md). That design's bubble was a transient message on completion. This replaces it with a standing drawer of cards that never auto-hide, and moves completion notices to the OS.

## Purpose

The bubble shipped as a five-second message shown *after* a command finished, only when the tab was out of sight. In use that is backwards: the interesting moment is while something is running, and by the time a message appears the information is already stale. Three separate problems:

1. **It looks wrong.** FlatLaf sets `Label.font` to **Helvetica Neue** — the pre-2015 macOS system font — so the bubble is not falling back to a generic sans, it is using a real but dated face. The corners are a 14px rounded rectangle rather than a pill, and it appears and disappears with no motion at all.
2. **It says nothing while you wait.** The buddy types, but the bubble is silent until the end.
3. **One bubble cannot represent several sessions.** A second command finishing replaced the first bubble outright.

## Confirmed decisions

- **A drawer, not a message.** Nothing auto-hides. The deck is a standing list of what is going on, kept so you can look back at it.
- **The deck does not know what a pane is.** A notice carries an identity supplied by whatever produced it. The terminal is one producer and uses the pane as the identity, which gives one card per tab/pane/window today. A future sftp transfer or SSH session is another producer with its own identities, and needs no change to the deck.
- **A card is created on demand** — for the terminal, when a pane's first command passes `notifications.long_command_seconds`. An idle pane has no card, so four quiet panes are not four cards beside the buddy.
- **Bounded at 50, newest kept.** Oldest fall off silently. Individual cards can be dismissed, and the expanded list has a clear-all.
- **Cleared on restart.** The drawer covers this run; shell history is the long-term record.
- **Cards stack.** The newest is fully drawn; older ones peek out a few pixels behind it like a deck, with a count when there are more than three. Clicking anywhere expands the deck into a vertical list.
- **Clicking an entry runs its action.** For a terminal notice that means raise the window, select the tab, focus the pane; a later producer supplies its own. The deck only knows there is a `Runnable`.
- **Completion goes to the OS when the pane is not focused.** Only the focused pane is quiet: a command finishing in a *visible but unfocused* split pane still notifies. This is a deliberate widening of the previous rule.
- **The card shows the result and keeps it**, including after its pane closes: the point is to remember. A card whose origin is gone still reads, but no longer clicks.
- **System font, pill shape, bounce in, grow on hover.**

## Appearance

**Font.** `.AppleSystemUIFont` is the macOS system font and resolves; `"SF Pro"` and `"SF Pro Text"` do **not** — they fall back silently to Dialog, which is how a wrong choice would go unnoticed. `BuddyFonts.system(style, size)` asks for `.AppleSystemUIFont` on macOS, falls back to `Label.font`, then to `Font.SANS_SERIF`, and is verified by asking the resolved font for its family rather than trusting the name it was asked for.

**Shape.** The card is a pill: corner radius is half its height, capped so a two-line card stays a rounded rectangle rather than a lozenge. One fill, a hairline border, no shadow — the existing colours are kept.

**Motion.** All three animations run on the buddy window's existing repaint timer; none adds a thread.

- **In:** scale from 0.6 to 1.0 over 220 ms on an overshoot curve that reaches about 1.06 before settling — the bounce of a thought bubble arriving. Opacity follows the same curve, clamped to 0..1.
- **Hover:** scale to 1.04 over 90 ms, and back on exit. Enough to read as "this one is selected" without moving the text noticeably.
- **Expand/collapse:** the stack's cards travel from their peeking offsets to their list positions over 180 ms.

Scaling is about the card's own centre-left anchor so it grows away from the buddy, not over him.

## The deck

`BuddyBubbleDeck` owns the notices and all of the geometry. One window holds the whole deck, replacing the current one-window-per-bubble arrangement.

The deck deals in notices, not terminals. It never mentions a pane, a tab or a command:

```java
/**
 * One thing worth remembering. {@code key} is whatever produced it — a terminal pane today, an
 * sftp transfer or an SSH session later — and is what makes a later update replace this notice
 * rather than stack on top of it. Two producers can never collide because {@code source} is part
 * of the key.
 */
record BuddyNotice(String source, Object key, String title, String detail, State state,
                   long at, Runnable activate) {
    /** Deliberately not "running/succeeded/failed": a transfer is in progress, not running. */
    enum State { ACTIVE, DONE, FAILED }

    /** Its origin is gone — the pane closed. Nothing to click, but still worth reading. */
    boolean orphaned() { return activate == null; }
}
```

- `post(notice)` adds a notice, or replaces the one with the same `source` and `key`, and moves it to the top.
- `dismiss(source, key)` removes one; `clear()` empties the drawer.
- `orphan(source, key)` clears a notice's action when its origin goes away. Orphaning is deliberately *not* a fourth `State`: a pane that closes after its build succeeded must still read "Finished in 1m 12s". The outcome and the reachability are two facts, so they are two fields — a null `activate` is the whole of it, and `orphaned()` derives rather than duplicates.
- `notices()` is newest first, capped at `MAX_NOTICES = 50`.

The terminal producer is the only caller today. It passes `source = "terminal"` and the pane as the key, which is what makes "one card per tab/pane/window" fall out without the deck knowing why.

Collapsed, the top card is drawn in full and each card behind it is offset 4px down and inset 6px, up to three visible; beyond that the deck draws a count. Expanded, the cards are laid out vertically with 6px between them.

**Dismissing.** Each card grows a small × in its top-right corner on hover, which removes that one notice. The expanded list ends with a **Clear all** row. Both are drawn by the deck and hit-tested by the deck; neither is a Swing component, matching how the bubble already works.

**Scrolling.** Fifty cards do not fit any screen, so the expanded list scrolls on the wheel: one offset, clamped to the content height, reset on collapse. Capping the list and silently hiding the rest would make the drawer lie about what it holds.

**Closing the expanded list** is on pointer exit, matching the bubble's existing dismiss. The user did not pick a close mechanism, and a list with no way back would be a trap. The × and **Clear all** targets sit inside the list's bounds, so reaching for one never grazes outside and collapses the thing you were aiming at.

## What a card says

| State | Title | Detail | Glyph |
|---|---|---|---|
| `ACTIVE` | the command | `Running · 1m 12s`, ticking | none |
| `DONE` | the command | `Finished in 1m 12s` | check |
| `FAILED` | the command | `Exited 2 · 1m 12s` | cross |

An orphaned card keeps all three and simply draws dimmer. An orphaned `ACTIVE` card — the pane closed mid-command — stops ticking and reads `Stopped after 1m 12s`, because nothing will ever finish it.

The running elapsed time updates from the buddy window's repaint timer, which already runs while the buddy is visible. The title collapses newlines to `↵` and truncates, as it does today.

## Routing

`CommandNotifier` changes shape: it no longer decides *whether* to show a bubble, only whether to notify the OS.

- **`passedThreshold(sessionId, command, focus)`** — posts an `ACTIVE` notice and starts the buddy typing. This happens whatever has focus: the card is status, not an interruption.
- **`finished(sessionId, command, exitStatus, ran, origin, focus)`** — posts a `DONE` or `FAILED` notice over the same key, stops the typing when it was the last running command, and sends a **system notification** when `origin` says the pane was not focused.
- **`closed(sessionId)`** — orphans the notice rather than removing it, and releases any running count. The drawer keeps what happened; it just cannot take you there any more.

`CommandNotice.Origin` gains `ownPaneFocused`, and `shouldNotify` becomes: notify unless the command's own pane had keyboard focus in an active window. A command shorter than the threshold still notifies nobody and gets no card.

The buddy being hidden or disabled no longer changes the *channel* — a hidden buddy simply has no visible deck, and completion still reaches the OS. That removes the previous "buddy off ⇒ native" branch, and with it the reason `CommandNotifier` needed two `Channel` implementations. `NativeNotifier` stays as the single notification channel; the interface goes, satisfying the repository's "no interface without two implementations" rule by deleting the interface rather than inventing a second implementation.

## Errors and edge cases

- A pane that closes while its command runs has its notice orphaned and its running count released, so the buddy stops typing. An orphaned card reads the same but does nothing when clicked.
- The fifty-first notice pushes the oldest out silently. With one card per pane that bound is unreachable today; it becomes real once a producer posts per-item notices, which is exactly the case this design is meant not to corner.
- A session whose command finishes while the deck is expanded updates in place; the list does not jump or reorder, because reordering under the pointer would misfire a click.
- More cards than fit the screen height: the list scrolls, and the scroll offset is clamped so it can neither run past the last card nor above the first.
- Dismissing the last card leaves an empty deck, which draws nothing at all rather than an empty frame.
- Scaling never makes a card exceed its window bounds; the window is sized for the largest scale so a growing card is not clipped.
- With no shell integration nothing reaches the notifier, so there are no cards at all — unchanged, and still stated in the documentation.

## Testing

- Fonts: the resolved family is the system font on macOS and a real family elsewhere; `"SF Pro"` is asserted **not** to be used, since it silently resolves to Dialog.
- Deck: `post` replaces by source and key and promotes to the top; two sources with the same key never collide; `dismiss` and `clear` remove; `orphan` keeps the card but drops its action; `notices()` is newest first and caps at fifty, dropping the oldest.
- Geometry: collapsed offsets, expanded list positions, the scroll offset clamping at both ends, and that a dismiss × hit-tests to the card it is drawn on rather than the one behind it.
- Animation: the in-curve starts below 1, exceeds 1 once, and settles at exactly 1; hover grows and returns; every animation is driven by ticks, so a test advances time rather than sleeping.
- Routing: a threshold pass creates an `ACTIVE` notice with no notification; a finish replaces the same notice and notifies only when the pane was unfocused; a close orphans rather than removes, and stops the typing.
- `shouldNotify`: every combination of the origin booleans including the new pane one.

User-run afterwards: several long commands across tabs and splits on the real desktop, watching the deck stack, expand, and the cards update.

## Out of scope

A keyboard route into the deck; sound; grouping by tab rather than pane; persisting the drawer across restarts; and the future producers themselves — sftp and SSH are the reason the deck is source-keyed, but nothing here implements them.
