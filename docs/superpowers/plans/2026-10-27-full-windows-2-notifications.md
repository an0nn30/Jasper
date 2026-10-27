# Full windows 2: notifications in every window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A notification card shows in every open window at once, leaves every window together, and clicking it (or
its system notification) takes the user to the window it pertains to.

**Architecture:** `NotificationsFeature.route` shows each requested card in every attached layer instead of one; a
layer that times a card out tells the feature, which withdraws it from the others; a newly attached window takes the
cards already showing. A card's click and action buttons first raise the pertaining window through a callback the
application gives the feature. System notifications already raise the pertaining window (`SystemNotices.clicked`);
this plan pins that with tests.

**Tech Stack:** Java 25 (JBR 25), Swing, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md`](../specs/2026-10-26-jasper-full-windows-design.md)
section 8 (and decision 8 in section 2). Plan 1 (`2026-10-26-full-windows-1-full-windows.md`) is done on this branch.

## Global Constraints

- Work in the worktree `.claude/worktrees/full-windows` on branch `claude/full-windows`; never build in
  `~/projects/moray`. Never launch the GUI or a benchmark. `./gradlew` only.
- One notification center: reading, dismissing, expiring or removing an entry in one window removes its card from all.
- Delivery's presence rule is unchanged: cards while any Jasper window is focused; otherwise a system notification or a
  pending card (`notifications/Delivery.java`), pending cards showing in every window when Jasper is focused again; a
  card pending while no window is open waits for the next window.
- **The window a notification pertains to**, in order: its pane's window (`JasperApplication.holderOf`), its target
  window (`NotificationRequest.target()`), else none — only open windows count.
- A card's click raises that window and then runs its action; with none, the action runs where it was clicked
  (nothing is raised). The card's Close button raises nothing.
- Each window's Notifications tool window lists the whole history (unchanged).
- MVP: no compatibility code. One commit per task, ending with
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- *Ruling (this plan):* a card's 10-second countdown still runs only while its own window is focused; when any layer's
  countdown hides a card, the card leaves every window (one card, one lifetime). Sticky cards never count down.
- *Ruling (this plan):* a card's action buttons raise the pertaining window as a body click does, since an action acts
  on what the card is about; the Notifications tool window's rows are unchanged.

## Review Focus

1. **A window opened while cards show gets those cards too**, in the same order, so every window shows the same
   stack. → Task 1 `aWindowAttachedWhileCardsShowTakesThem`.
2. **Closing the window a card was posted from keeps the card in the other windows** (plan 1's interim lost it). →
   Task 1 `closingTheWindowACardCameFromKeepsItElsewhere`.
3. **A card that times out in the focused window disappears from the unfocused ones** rather than lingering there until
   they are focused. → Task 1 `aCardThatTimesOutInOneWindowLeavesEveryWindow`.
4. **Clicking a card in window B whose pane is in window A** brings A to the front and focuses the pane, even after the
   pane's tab moved from the window it was posted in to another. → Task 2
   `clickingACardRaisesTheWindowHoldingItsPaneNow`.
5. **Clicking a card whose window has closed** runs its action without raising or reopening anything. → Task 2
   `clickingACardWhoseWindowClosedRunsItsActionInPlace`.

---

### Task 1: Every window shows every card

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java` (`route`, `attach`, `detach`, the `lastActive` field, the Dismiss Cards comment, the class Javadoc)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java` (constructors `:83`, `:88`; `hidden` `:248-255`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java`, `jasper-app/src/test/java/dev/jasper/app/notificationui/CardLayerTest.java`

**Interfaces:**
- Produces:
  - `CardLayer(KitContext kit, NotificationCenter center, boolean focused, Runnable openHistory, Consumer<UUID> timedOut)`
    and the test overload with `BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime` appended
    after `timedOut`. `timedOut` receives a card's id after its countdown hides it here.
  - `NotificationsFeature.route` shows the card in every attached layer; `attach` shows, in the new layer, every card
    any attached layer shows (in that layer's top-to-bottom order) plus those waiting; `timedOut` from one layer calls
    `withdraw(id)` on every other layer.

- [ ] **Step 1: Write the failing tests** in `NotificationsFeatureTest`, replacing `cardsGoToTheirTargetOrTheWindowUsedLast`,
  `aWindowsOwnMessagesShowInThatWindowEvenWhenAnotherWasUsedLast`, `aRepeatFromAnotherWindowMovesItsCardThere` and
  `aClosedWindowsCardsGoAndLaterOnesFindAnotherWindow`:

```java
@Test void everyCardShowsInEveryWindow() {
    var feature = new NotificationsFeature(center, contributions, dev.jasper.app.testsupport.KitTestSupport::context);
    var first = window();
    var second = window();
    feature.attach(first);
    feature.attach(second);
    feature.activated(second);
    feature.info("Untargeted", "");
    feature.error("From first", "", first.id());
    assertThat(feature.layer(first.id()).showing()).hasSize(2);
    assertThat(feature.layer(second.id()).showing()).isEqualTo(feature.layer(first.id()).showing());
}

@Test void readingACardInOneWindowRemovesItFromAll() {
    // Two windows, one card; center.markRead(id) → both layers' showing() empty. Same for center.expire(id).
}

@Test void aRepeatKeepsOneCardInEveryWindow() {
    // error("Could not save", "same details", first) then the same from second: center.history() hasSize 1 and each
    // layer shows exactly that one id.
}

@Test void aWindowAttachedWhileCardsShowTakesThem() {
    // Two cards showing in an attached window; attach a third window: its layer's showing() equals the first's.
}

@Test void closingTheWindowACardCameFromKeepsItElsewhere() {
    // A card targeted at first, shown in both; detach(first): second still shows it; center.history() keeps it.
}

@Test void aCardThatTimesOutInOneWindowLeavesEveryWindow() {
    // Build the feature with layers whose schedule the test controls (use the CardLayer test seam the way
    // CardLayerTest does, through a package-private NotificationsFeature constructor taking the layer factory if one
    // is needed); run the focused layer's countdown: the card leaves both layers, and stays unread in history.
}
```

  In `CardLayerTest` add `aCountdownThatHidesACardReportsIt` (the `timedOut` consumer receives the id exactly once; a
  read or a withdraw reports nothing).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*NotificationsFeatureTest' --tests '*CardLayerTest'`
Expected: FAIL (a card shows in one window; `CardLayer` has no `timedOut` parameter).

- [ ] **Step 3: Implement.** `route` loses the target choice and the withdraw loop. `lastActive` goes unless a remaining
  use needs it (`activated` still calls `center.focusReturned()`). `attach` replays the showing and waiting cards of any
  attached layer through `layer.show(notification)` (each starts its own countdown). Dismiss Cards dismisses the
  invoking window's layer, which reads every card and so clears all; its comment says so. If the timeout test needs a
  controlled schedule, add a package-private `NotificationsFeature` constructor that takes the layer factory
  (`Function<WindowContent, CardLayer>`-shaped); the production constructors build `CardLayer`s as today.

- [ ] **Step 4: Run the notification tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*Notification*' --tests 'dev.jasper.app.notificationui.*'`
Expected: PASS.

- [ ] **Step 5: Commit** — `Notifications: every card shows in every window and leaves them together`

---

### Task 2: A click takes the user to the window it pertains to

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/NotificationCard.java` (`:89` body click, `:139` actions)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java` (`place`, the constructors)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java` (constructors; `attach`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`:378` the feature's construction; a `pertainingWindow` helper beside `holderOf` `:2011`)
- Modify: `docs/workspace.md` (the notifications section, ~`:440-446` in plan 1's numbering: cards in every window, clicks), `docs/configuration.md` notifications wording if it says one window, `docs/STATUS.md` (a 2026-10-27 plan 2 entry; drop plan 1's "a card shows in one window and closes with it" interim)
- Modify: this plan's status banner
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java`, `application/NotificationsFeatureTest.java`, `application/JasperApplicationNotificationsTest.java`, `application/JasperApplicationSystemNoticesTest.java`

**Interfaces:**
- Consumes: Task 1's `CardLayer` constructors and every-window routing.
- Produces:
  - `NotificationCard(KitContext kit, NotificationCenter center, Notification notification, boolean sticky, Consumer<UUID> raise)`:
    a body click calls `raise.accept(id)` then `center.activate(id)`; an action button calls `raise.accept(id)` then
    `center.runAction(id, index)`; Close only reads.
  - `CardLayer` constructors gain `Consumer<UUID> raise` after `openHistory` (before `timedOut`) and pass it to each card.
  - `NotificationsFeature(NotificationCenter center, Contributions contributions, Supplier<KitContext> kit, AppPanels panels, Consumer<UUID> raise)`;
    the existing three- and four-argument constructors pass `id -> { }`.
  - `JasperApplication`: `private Optional<UUID> pertainingWindow(UUID notification)` — `holderOf(notification)`, else
    the entry's `request().target()`, kept only while `windows.window(id)` is open; the feature's `raise` is
    `id -> pertainingWindow(id).flatMap(windows::window).ifPresent(JasperWindow::toFront)`. `SystemNotices`'s
    `holder`/`raise` wiring is unchanged.

- [ ] **Step 1: Write the failing tests**

```java
// NotificationCardTest
@Test void aClickRaisesThenActivates() {
    var order = new ArrayList<String>();
    // a card for an entry whose activate() adds "activated" to order, built with raise = id -> order.add("raised " + id)
    // click its body → order == ["raised <id>", "activated"]; click an action → raise before the action's run;
    // click Close → no "raised".
}

// JasperApplicationNotificationsTest (it already drives two windows and a command notification; reuse its fixture)
@Test void clickingACardRaisesTheWindowHoldingItsPaneNow() {
    // A command finishes in a pane of window A; its card shows in A and B; move the pane's tab to window C; click the
    // card in B: C comes to the front (its frame's front event) and the pane is focused.
}

@Test void clickingACardWhoseWindowClosedRunsItsActionInPlace() {
    // An info targeted at window A with a default action; close A; click the card in B: the action ran, no window came
    // to the front, no window opened.
}

// JasperApplicationSystemNoticesTest
@Test void clickingASystemNotificationRaisesThePertainingWindowNotTheLastActive() {
    // A command's notification from a pane in A while B was active last; SystemNotices.clicked(id): A comes to the
    // front, not B.
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*NotificationCardTest' --tests '*JasperApplicationNotificationsTest' --tests '*JasperApplicationSystemNoticesTest'`
Expected: FAIL to compile (`NotificationCard` has no `raise` parameter); the system-notification test may already pass
(it pins existing behaviour).

- [ ] **Step 3: Implement** as the Interfaces block says.

- [ ] **Step 4: Run the application module**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 5: Update the docs** (smallest accurate edits, AGENTS.md "Documentation edits") and the plan banner, then
  run `./gradlew check`.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** — `Notifications: a click raises the window the notification pertains to`

- [ ] **Step 7: Hand the GUI checks to the user** (not run by an agent): a command finishing in one window shows its
  card in every window; dismissing it in one clears all; clicking it in another window raises the first and focuses
  the pane; with Jasper unfocused a system notification goes out and clicking it raises that window.
