# Buddy bubble deck — design

**Status:** Designed. Development branch: `claude/command-notifications` (continuing), from `612a7d6`.

**Revises:** the [finished-command notifications design](2026-09-16-jasper-finished-command-notifications-design.md). That design's bubble was a transient message on completion. This replaces it with a persistent per-session card, and moves completion notices to the OS.

## Purpose

The bubble shipped as a five-second message shown *after* a command finished, only when the tab was out of sight. In use that is backwards: the interesting moment is while something is running, and by the time a message appears the information is already stale. Three separate problems:

1. **It looks wrong.** FlatLaf sets `Label.font` to **Helvetica Neue** — the pre-2015 macOS system font — so the bubble is not falling back to a generic sans, it is using a real but dated face. The corners are a 14px rounded rectangle rather than a pill, and it appears and disappears with no motion at all.
2. **It says nothing while you wait.** The buddy types, but the bubble is silent until the end.
3. **One bubble cannot represent several sessions.** A second command finishing replaced the first bubble outright.

## Confirmed decisions

- **A card per session, created on demand.** A session — one pane — gets a card when its first command passes `notifications.long_command_seconds`. The card then persists, its content updating through later commands, until the pane or tab closes. An idle pane has no card, so four quiet panes are not four cards beside the buddy.
- **Cards stack.** The newest is fully drawn; older ones peek out a few pixels behind it like a deck, with a count when there are more than three. Clicking anywhere expands the deck into a vertical list.
- **Clicking an entry focuses that pane** — raise the window, select the tab, focus the pane.
- **Completion goes to the OS when the pane is not focused.** Only the focused pane is quiet: a command finishing in a *visible but unfocused* split pane still notifies. This is a deliberate widening of the previous rule.
- **The card shows the result and keeps it.** A finished command leaves its card in place showing the outcome, rather than auto-hiding, because the card is the session's status, not a transient message.
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

`BuddyBubbleDeck` owns the cards and all of the geometry. One window holds the whole deck, replacing the current one-window-per-bubble arrangement.

```java
/** One session's card. Identity is the pane; content changes as commands come and go. */
record BubbleCard(Object sessionId, String title, String detail, State state, Runnable focus) {
    enum State { RUNNING, SUCCEEDED, FAILED }
}
```

- `upsert(card)` adds a card or replaces the one with the same `sessionId`, and moves it to the top of the stack.
- `remove(sessionId)` drops a card when its pane closes.
- `cards()` is newest first.

Collapsed, the top card is drawn in full and each card behind it is offset 4px down and inset 6px, up to three visible; beyond that the deck draws a count. Expanded, the cards are laid out vertically with 6px between them, and the whole list is clipped to the screen's usable height.

**Closing the expanded list** is on pointer exit, matching the bubble's existing dismiss. The user did not pick a close mechanism, and a list with no way back would be a trap; this is the least surprising choice and is one line to change.

## What a card says

| State | Title | Detail | Glyph |
|---|---|---|---|
| `RUNNING` | the command | `Running · 1m 12s`, ticking | none |
| `SUCCEEDED` | the command | `Finished in 1m 12s` | check |
| `FAILED` | the command | `Exited 2 · 1m 12s` | cross |

The running elapsed time updates from the buddy window's repaint timer, which already runs while the buddy is visible. The title collapses newlines to `↵` and truncates, as it does today.

## Routing

`CommandNotifier` changes shape: it no longer decides *whether* to show a bubble, only whether to notify the OS.

- **`passedThreshold(sessionId, command, focus)`** — upserts a `RUNNING` card and starts the buddy typing. This happens whatever has focus: the card is status, not an interruption.
- **`finished(sessionId, command, exitStatus, ran, origin, focus)`** — upserts a `SUCCEEDED` or `FAILED` card, stops the typing when it was the last running command, and sends a **system notification** when `origin` says the pane was not focused.
- **`closed(sessionId)`** — removes the card and releases any running count.

`CommandNotice.Origin` gains `ownPaneFocused`, and `shouldNotify` becomes: notify unless the command's own pane had keyboard focus in an active window. A command shorter than the threshold still notifies nobody and gets no card.

The buddy being hidden or disabled no longer changes the *channel* — a hidden buddy simply has no visible deck, and completion still reaches the OS. That removes the previous "buddy off ⇒ native" branch, and with it the reason `CommandNotifier` needed two `Channel` implementations. `NativeNotifier` stays as the single notification channel; the interface goes, satisfying the repository's "no interface without two implementations" rule by deleting the interface rather than inventing a second implementation.

## Errors and edge cases

- A pane that closes while its command runs has its card removed and its running count released, so the buddy stops typing.
- A session whose command finishes while the deck is expanded updates in place; the list does not jump or reorder, because reordering under the pointer would misfire a click.
- More cards than fit the screen height: the expanded list is capped and the oldest are dropped from view, never the newest.
- Scaling never makes a card exceed its window bounds; the window is sized for the largest scale so a growing card is not clipped.
- With no shell integration nothing reaches the notifier, so there are no cards at all — unchanged, and still stated in the documentation.

## Testing

- Fonts: the resolved family is the system font on macOS and a real family elsewhere; `"SF Pro"` is asserted **not** to be used, since it silently resolves to Dialog.
- Deck: `upsert` replaces by `sessionId` and promotes to the top; `remove` drops one; `cards()` is newest first; the visible count caps at three plus a remainder.
- Geometry: collapsed offsets, expanded list positions, and that the expanded height is clipped rather than overflowing.
- Animation: the in-curve starts below 1, exceeds 1 once, and settles at exactly 1; hover grows and returns; every animation is driven by ticks, so a test advances time rather than sleeping.
- Routing: a threshold pass creates a `RUNNING` card with no notification; a finish updates the same card and notifies only when the pane was unfocused; a close removes the card and stops the typing.
- `shouldNotify`: every combination of the origin booleans including the new pane one.

User-run afterwards: several long commands across tabs and splits on the real desktop, watching the deck stack, expand, and the cards update.

## Out of scope

Dismissing a single finished card; a keyboard route into the deck; sound; grouping by tab rather than pane; notification history beyond the live cards.
