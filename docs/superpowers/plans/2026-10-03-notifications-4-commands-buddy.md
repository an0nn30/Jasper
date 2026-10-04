# Notifications Plan 4: Long Commands and Buddy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Merged to local main on 2026-10-03, with main `be3b593d` merged in first (not pushed; `./gradlew check
--continue` on the merge: **8,435 tests, 0 failures, 0 errors, 6 skipped**). Implemented on
branch `claude/notifications-4` (worktree `.worktrees/notifications-4`, cut from main
`3c64ea5c`, where plans 1–3 are merged). Main moved to `43894416` during execution (the dock polish round; plans 2 and 3
pushed) and is merged in at `81f71fe2`. Deviations from the plan text: Task 3, the closing fence after Step 3's
`PluginRuntime.forward` block had a period glued to it and so never closed (fixed here; no code change); Task 4, the three
`BuddyTestSupport` lines Step 4 removes are not adjacent in the file, so each was deleted on its own; Task 5, main was
merged before the full check so the counts cover the tree that will land, and STATUS's plan 3 heading says "merged to
`main` (`3c64ea5c`) and pushed" rather than "merged to local `main`". Final review (Opus, one pass): three important findings fixed — only the pane a coalesced command
outcome last came from expires it (`b35fc8db`, `aRepeatFromAnotherPaneIsEndedOnlyByThePaneItLastCameFrom`); an expired
entry keeps its text, labels and target but drops its poster's callbacks, which held closed panes, their windows and
stopped plugins (`28810afb`, `anExpiredEntryKeepsItsLabelsButLetsGoOfItsPostersCallbacks`); clicking a running Buddy
capsule keeps the column (`be5a344d`, `clickingABubbleRunsItsActionAndKeepsTheColumn`). One is left for the user: a
command that finishes while Jasper is in the background has no click route to its pane until plan 5, since interpretation
2's "body disabled" assumed a history-row body action the spec never gave (section 4); the guides now say a history row
has no click of its own. Deferred minor findings: the app and SDK `NotificationAction` docs still say an expiring action
expires "afterwards", and the testkit's `FakeNotifications.runAction` still expires after running;
`Notification.repeated(Instant)` is used only by a test; `CommandNotifier.titleChanged` accepts a title of only invisible characters;
a pane's posted outcome ids grow while it stays open; no test withdraws a waiting card; STATUS said "reviewed" before
the review ran; the "Posting a notification" recipe does not mention that a repeat replaces actions and target.
`./gradlew check --continue`: **8,339 tests, 0 failures, 0 errors, 6 skipped**. No GUI launched.

**Goal:** Finished long commands become notifications in "Long-running commands", and Buddy keeps only live work:
running commands and activities, input requests and connections.

**Architecture:**
- `CommandNotifier` keeps its rule for *whether* a finished command is worth telling (it ran at least
  `notifications.long_command_seconds` and did not finish in the pane you were typing in). It posts the outcome to the
  center in `jasper.commands`, in the pane's window, with a click that focuses the pane. Its Buddy card now lives only
  while the command runs.
- `ActivityNotifier` dismisses an activity's card when the activity ends; plan 3 already posts the outcome.
- Buddy loses `DONE`, `FAILED`, acknowledgement and orphaning; its column and drawer show the live cards.
- The center's coalescing now follows the newest post, so a repeated outcome points at the pane and window it last came
  from.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing with FlatLaf, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 4 of the six in its section 14). Section 7 producer 3, section 8 (Buddy) and section 2 (coalescing, read state,
actions) bind this plan. Read the status banners of [plan 1](2026-10-02-notifications-1-core.md),
[plan 2](2026-10-02-notifications-2-incidents.md) and [plan 3](2026-10-03-notifications-3-sdk.md): their deferred
findings keyed to plan 4 are handled here (interpretations 5 to 7 below).

## Global Constraints

**Build and workflow**

- Build and test only with `./gradlew` on JBR 25; tests are headless. Never launch the GUI or the benchmark.
- Work only in `.worktrees/notifications-4` on branch `claude/notifications-4`. Never edit the main checkout, never
  push, never use a bare `git stash`.

**Architecture**

- `dev.jasper.app.notifications` stays Swing-free; its outgoing dependencies stay `dev.jasper.buddy.notice`,
  `dev.jasper.buddy.view` and `dev.jasper.app.lifecycle` (spec section 1).
- `jasper-buddy` stays JDK-only. Only its five supported types (`BuddyCompanion`, `BuddyNotice`, `BuddyNoticeId`,
  `BuddyOptions`, `BuddyPosition`) are used by the application; `internal` packages are never imported by app code.
  `BuddyTestSupport` is a test fixture only.
- `verifyApplicationArchitecture` (both package DAGs, Buddy signatures) stays green.

**Long-running commands (spec section 7, producer 3)**

- Group `jasper.commands`, named "Long-running commands", owner `jasper`, default mode `CARD`.
- A finished command is posted when it ran at least `notifications.long_command_seconds` and did not finish in the pane
  you were typing in (`CommandNotice.shouldNotify`). `0` turns the feature off.
- Title: the captured command (one line). Content: `Finished in 2m 03s` (INFO) or `Exited 1 · 2m 03s` (WARNING).
  Default action: focus the pane. Target: the pane's window.

**Buddy (spec section 8)**

- `BuddyNotice` loses `DONE` and `FAILED`, and the acknowledge and orphan machinery goes. The column and the drawer list
  live work: running, needs input, and connections. The working animation and the greeting are unchanged.
- `CommandNotifier` and `ActivityNotifier` dismiss a Buddy card when its work ends.

**Unchanged rules**

- Notification text is plain, never HTML. The log is unchanged.
- No new animation anywhere. Buddy's existing animation is unchanged (spec section 8).
- Doclint runs on public types: no bare `<`, `>` or `&` in JavaDoc outside `{@code}`, and `@param` names must match.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java source. `"…"`, `"·"`
  and `"↵"` may be written as characters.

**Tests and commits**

- Inject clocks and schedulers; don't sleep in unit tests. EDT tests use `@ExtendWith(EdtTestExtension.class)`. Field
  initializers, `@BeforeEach` and `@AfterEach` do not run on the EDT.
- One commit per task. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **The macOS banner for long commands stays until plan 5, but only while Jasper is in the background.** Spec
   section 7 removes `CommandNotifier`'s private OS sink, but the OS notifiers it moves to arrive with plan 5. Dropping it
   now would lose the packaged app's background banner between the two plans. In the foreground the card covers the
   outcome, and the macOS bridge presents banners in the foreground too, so the sink fires only when the center's
   presence is not `FOCUSED`. Plan 5 deletes it when `SystemNotifier` takes over.
2. **A command's notification expires when its pane closes.** The spec does not say; its default action focuses a pane
   that no longer exists. Expiry closes the card and keeps the history entry with its body disabled.
3. **Focusing a pane does not mark its notification read.** Spec section 2 lists the read transitions, and focusing a
   pane is not one. `CommandNotifier.looked`, which acknowledged Buddy cards, is removed.
4. **A pane closed while its command runs** dismisses the running card and posts nothing: there is no outcome, and the
   user closed it.
5. **A coalesced repeat carries the newest post's actions, default action, target, icon and source** (plans 1 and 2
   deferred "coalescing keeps the first request's actions, default action and target" to plan 4). Its card moves to the
   newest target window: the other windows withdraw it.
6. **An expiring action expires its entry before it runs** (plan 3's deferred finding: a Retry that fails again within
   60 s was folded into the entry it then expired, so the new failure never showed). The user sees the same thing: the
   card closes and the links stop working.
7. **The history panel's and card layer's per-change rebuilds stay** (plans 1 and 2 deferred them to plan 4 for
   "higher post rates"). Long commands post at most once per command that ran 10 seconds or more, and activities at the
   pace of the work, so the rate does not rise enough to matter. The rail part is already fixed: the dock work updates a
   badge in place. Deferred again, to a plan that raises the rate.
8. **`ActivityNotifier.ended` replaces `finished` and `cancelled`.** Every end dismisses the card. A click raises the
   application and runs the activity's action; nothing is acknowledged any more.
9. **`BuddyNotice.activate` is required.** It was nullable only to mark an orphan. `live()` and `orphaned()` go: every
   retained notice is live. `wantsAttention()` is `NEEDS_INPUT`, `DEGRADED` or `DOWN`. A card's glyph is a cross for
   `DOWN` and a dot for `NEEDS_INPUT` or `DEGRADED`.
10. **The drawer keeps its dismiss target and Clear all** for live cards, and a running command's card dismissed there
    stays gone until the next command in that pane.
11. **A command whose text shows nothing is titled "Command"**, for both its Buddy card and its notification, since both
    need a visible title.
12. **The approved app/Buddy amendment's facade shrinks** by `acknowledge` and both `orphan` overloads, as spec section 8
    requires; the Buddy README's supported-API table and examples are updated with it.

## Review Focus

1. **A command whose captured text is blank or only invisible characters** still gets a Buddy card and a notification,
   titled "Command", instead of an exception on the EDT. Pinned by Task 2's
   `aCommandWithNothingVisibleToSayIsCalledCommand`.
2. **A pane that closes after its command's notification was posted**: the card closes, the history entry stays and its
   body no longer focuses a pane that is gone. Pinned by Task 2's `closingThePaneExpiresItsCommandsNotifications`.
3. **Two panes finishing the same command in the same time within 60 s**: one entry counted twice, its card in the
   newest pane's window, and a click focuses the newest pane. Pinned by Task 1's
   `aRepeatCarriesTheNewestPostsActionsDefaultActionAndTarget` and `aRepeatFromAnotherWindowMovesItsCardThere`.
4. **A plugin that stops with an activity still open** leaves no Buddy card behind (and plan 3 posts no outcome).
   Pinned by Task 3's change to `PluginRuntimeTest.runsADevelopmentPluginBridgesItsActivityAndStopsIt`.
5. **Jasper in the background when a long command finishes**: no card, the entry is unread in history, and the interim
   macOS banner fires; in the foreground no banner duplicates the card. Pinned by Task 2's
   `theOperatingSystemHearsOnlyWhileJasperIsInTheBackground`.

## File structure

**Create:** nothing.

**Modify**

- **App model:** `notifications/Notification` (`repeatedAs`), `notifications/NotificationCenter` (coalescing,
  `runAction`, `presence()`), `notifications/CommandNotifier`, `notifications/ActivityNotifier`.
- **App views and composition:** `notificationui/CardLayer` (`withdraw`), `application/NotificationsFeature` (routing),
  `application/JasperApplication` (wiring).
- **App plugins:** `plugins/PluginRuntime` (`forward`).
- **Buddy:** `notice/BuddyNotice`, `internal/model/BuddyDeck` and its `package-info`, `view/BuddyCompanion`,
  `internal/presentation/BuddyCard`, `BuddyColumnPanel`, `BuddyDeckPanel`, `BuddyColumnWindow`; the
  `testFixtures` `BuddyTestSupport`.
- **Tests:** `NotificationCenterTest`, `NotificationsFeatureTest`, `CommandNotifierTest` (rewritten),
  `ActivityNotifierTest` (rewritten), `TerminalTitleIntegrationTest`, `PluginRuntimeTest`, `BundledSamplePluginTest`,
  `BuddyIntegrationTest`, `AppExamplesTest`; Buddy's `BuddyNoticeTest` and `BuddyDeckTest` (rewritten),
  `BuddyColumnPanelTest`, `BuddyDeckPanelTest`, `BuddyCardTest`, `BuddyShimmerTest`, `BuddyCompanionTest`,
  `BuddyExamplesTest`.
- **Docs:** `jasper-buddy/README.md`, `docs/buddy-architecture.md`, `docs/buddy-maintenance.md`,
  `docs/configuration.md`, `docs/notifications.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`,
  `docs/app-maintenance.md`, `docs/STATUS.md`, and the spec's and this plan's status banners.

---

### Task 1: A coalesced repeat follows the newest post

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/Notification.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java`

**Interfaces:**
- Consumes: plan 1's `NotificationCenter`, `CardLayer` and `NotificationsFeature`.
- Produces: package-private `Notification repeatedAs(NotificationRequest request, Instant at)`; `public void
  CardLayer.withdraw(UUID id)`. `NotificationCenter.post` coalesces into the newest request;
  `NotificationCenter.runAction` expires an expiring action's entry before running it.

- [ ] **Step 1: Write the failing center tests**

Add to `NotificationCenterTest` (it already imports `AtomicReference`, `ArrayList`, `Duration` and `UUID`):

```java
    @Test void aRepeatCarriesTheNewestPostsActionsDefaultActionAndTarget() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID id = center.post(request(CARDS, "same").withTarget(first).withActivate(() -> ran.add("first body"))
            .withAction(NotificationAction.of("Open first", () -> ran.add("first")))).orElseThrow();
        clock.advance(Duration.ofSeconds(5));
        assertThat(center.post(request(CARDS, "same").withTarget(second).withActivate(() -> ran.add("second body"))
            .withAction(NotificationAction.of("Open second", () -> ran.add("second"))))).contains(id);
        Notification entry = center.find(id).orElseThrow();
        assertThat(entry.count()).isEqualTo(2);
        assertThat(entry.request().target()).contains(second);
        assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Open second");
        center.activate(id);
        center.runAction(id, 0);
        assertThat(ran).containsExactly("second body", "second");
    }

    @Test void whatAnExpiringActionPostsIsNotFoldedIntoTheEntryItEnds() {
        var center = center();
        var posted = new ArrayList<UUID>();
        var retry = new AtomicReference<Runnable>();
        UUID id = center.post(request(CARDS, "Could not copy").withAction(NotificationAction.of("Retry", () -> retry.get().run())))
            .orElseThrow();
        retry.set(() -> center.post(request(CARDS, "Could not copy")).ifPresent(posted::add));
        center.runAction(id, 0);
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
        assertThat(posted).singleElement().isNotEqualTo(id);
        assertThat(center.find(posted.getFirst()).orElseThrow()).satisfies(entry -> {
            assertThat(entry.expired()).isFalse();
            assertThat(entry.count()).isEqualTo(1);
        });
    }
```

- [ ] **Step 2: Write the failing routing test**

Add to `NotificationsFeatureTest`, after `aWindowsOwnMessagesShowInThatWindowEvenWhenAnotherWasUsedLast`:

```java
    @Test void aRepeatFromAnotherWindowMovesItsCardThere() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.error("Could not save", "same details", first.id());
        assertThat(feature.layer(first.id()).showing()).hasSize(1);
        feature.error("Could not save", "same details", second.id());
        assertThat(center.history()).hasSize(1);
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(feature.layer(first.id()).showing()).as("one card, in the window it last happened in").isEmpty();
    }
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationCenterTest' --tests 'dev.jasper.app.application.NotificationsFeatureTest'`
Expected: FAIL. `aRepeatCarriesTheNewestPostsActionsDefaultActionAndTarget` finds the first target;
`whatAnExpiringActionPostsIsNotFoldedIntoTheEntryItEnds` finds the re-post folded into the same id;
`aRepeatFromAnotherWindowMovesItsCardThere` finds no card in the second window.

- [ ] **Step 4: Coalesce into the newest request, and expire before an expiring action runs**

In `Notification.java`, after `repeated(Instant at, int times)`, add:

```java
    /**
     * The same text posted again by {@code request}: counted, re-stamped, unread again, and carrying the newest post's
     * actions, default action, target, icon and source.
     */
    Notification repeatedAs(NotificationRequest request, Instant at) {
        return new Notification(id, request, at, count + 1, false, expired);
    }
```

In `NotificationCenter.java`:

1. In `post`, replace `            entry = earlier.repeated(now);` with `            entry = earlier.repeatedAs(request, now);`.
2. Replace

```java
    /** Runs one of the entry's actions: the entry is read first, and expired afterwards when the action says so. */
    public void runAction(UUID id, int index) {
        requireEdt();
        Notification entry = history.get(id);
        if (entry == null || entry.expired() || index < 0 || index >= entry.request().actions().size()) return;
        NotificationAction action = entry.request().actions().get(index);
        markRead(id);
        contained(action.run(), "A notification action failed");
        if (action.expires()) expire(id);
    }
```

   with

```java
    /**
     * Runs one of the entry's actions: the entry is read first, and an expiring action expires it before running, so
     * what the action posts is never folded into the entry it ends.
     */
    public void runAction(UUID id, int index) {
        requireEdt();
        Notification entry = history.get(id);
        if (entry == null || entry.expired() || index < 0 || index >= entry.request().actions().size()) return;
        NotificationAction action = entry.request().actions().get(index);
        markRead(id);
        if (action.expires()) expire(id);
        contained(action.run(), "A notification action failed");
    }
```

- [ ] **Step 5: Move a repeat's card to its newest window**

In `CardLayer.java`, after `public List<UUID> showing() { … }`, add:

```java
    /** Takes the entry's card out of this window, showing or waiting, because it now shows in another one. */
    public void withdraw(UUID id) {
        if (closed) return;
        boolean waited = waiting.remove(id);
        Slot slot = slot(id);
        if (slot == null && !waited) return;
        if (slot != null) discard(slot);
        promote();
        refresh();
    }
```

In `NotificationsFeature.java`, replace

```java
        if (target != null) target.layer().show(notification);
```

with

```java
        if (target == null) return;
        // A repeat may come from another window than its first post: one card, in the window it last came from.
        for (Attached other : windows.values()) if (other != target) other.layer().withdraw(notification.id());
        target.layer().show(notification);
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS, including every existing center, card layer and incident test.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/Notification.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java
git commit -m "fix(notifications): a coalesced repeat follows the newest post" -m "A repeat carries the newest post's actions, default action and target, and its card moves to that window; an expiring action expires its entry before it runs, so what it posts is a new entry." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Long commands post to the center

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java` (rewritten)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/CommandNotifierTest.java` (rewritten)
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTitleIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1's coalescing; `CommandNotice.shouldNotify(Origin, Duration, Duration)`; `BuddyCompanion` (`post`,
  `updateTitle`, `dismiss`, `setWorking`); `NotificationText.visible(String)` (package-private, plan 3).
- Produces:
  - `public Delivery.Presence NotificationCenter.presence()`;
  - `public static final NotificationGroup CommandNotifier.GROUP` (`jasper.commands`, "Long-running commands", CARD);
  - `CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center,
    BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
    BiFunction<Duration, Runnable, Runnable> schedule)`, which registers `GROUP`;
  - `finished(Object key, String command, OptionalInt exitStatus, Duration ran, CommandNotice.Origin origin,
    UUID window, Runnable activate)` (`window` may be null);
  - `looked(Object)` is removed. `opened`, `started`, `hidden`, `titleChanged`, `closed` and `close` keep their
    signatures.

- [ ] **Step 1: Write the failing tests**

Add to `NotificationCenterTest`:

```java
    @Test void presenceIsWhatDeliverySees() {
        var center = center();
        assertThat(center.presence()).isEqualTo(Delivery.Presence.FOCUSED);
        presence = Delivery.Presence.BACKGROUND;
        assertThat(center.presence()).isEqualTo(Delivery.Presence.BACKGROUND);
    }
```

Replace the whole of `CommandNotifierTest.java` with:

```java
package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CommandNotifierTest {
    private record Sent(String title, String detail) {}

    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<Sent> os = new ArrayList<>();
    private final List<Boolean> working = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private NotificationCenter center;
    private final UUID window = UUID.randomUUID();

    /** EDT: nothing fires by itself; a test runs the scheduled task when it wants the threshold to pass. */
    private CommandNotifier notifier(int seconds) {
        center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
        var notifier = new CommandNotifier(() -> Duration.ofSeconds(seconds), deck.companion(), center,
            (title, detail) -> os.add(new Sent(title, detail)), working::add,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
        for (String key : List.of("pane", "a", "b")) notifier.opened(key);
        return notifier;
    }

    private void passThreshold() { List.copyOf(scheduled).forEach(Runnable::run); }

    /** Each notification, newest first, as {@code group|SEVERITY|title|content|target|state}. */
    private List<String> notifications() {
        return center.history().stream().map(entry -> String.join("|", entry.request().group(), entry.request().severity().name(),
            entry.request().title(), entry.request().content(), entry.request().target().map(UUID::toString).orElse("-"),
            entry.expired() ? "expired" : "live")).toList();
    }

    /** Started in the pane the user is looking at, so the threshold is what promotes it. */
    private static final boolean WATCHED = true;
    private static final boolean UNWATCHED = false;

    private static final CommandNotice.Origin HIDDEN_TAB = new CommandNotice.Origin(true, true, false, false);
    private static final CommandNotice.Origin FOCUSED_PANE = new CommandNotice.Origin(true, true, true, true);
    private static final CommandNotice.Origin UNFOCUSED_SPLIT = new CommandNotice.Origin(true, true, true, false);

    @Test void theGroupIsLongRunningCommandsShownAsCards() {
        notifier(10);
        assertThat(center.group("jasper.commands")).contains(CommandNotifier.GROUP);
        assertThat(CommandNotifier.GROUP.name()).isEqualTo("Long-running commands");
        assertThat(center.mode("jasper.commands")).isEqualTo(DisplayMode.CARD);
    }

    @Test void aCommandThatPassesTheThresholdGetsARunningCardAndNothingElse() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("./gradlew build");
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.TASK);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
        });
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).containsExactly(true);
    }

    @Test void theRunningCardTicksFromTheSuppliedElapsedTime() {
        CommandNotifier notifier = notifier(10);
        long[] elapsed = {Duration.ofSeconds(11).toNanos()};
        notifier.started("pane", "sleep 600", () -> elapsed[0], () -> {}, WATCHED);
        passThreshold();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 11s");
        elapsed[0] = Duration.ofSeconds(72).toNanos();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 1m 12s");
    }

    @Test void finishingOutOfSightPostsTheOutcomeInThePanesWindowAndRemovesTheCard() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).as("Buddy keeps live work only").isEmpty();
        assertThat(working).containsExactly(true, false);
        assertThat(notifications()).containsExactly("jasper.commands|INFO|./gradlew build|Finished in 1m 12s|" + window + "|live");
    }

    @Test void aFailureIsAWarningThatCarriesItsExitStatus() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "make", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(2), Duration.ofSeconds(45), HIDDEN_TAB, window, () -> {});
        assertThat(notifications()).containsExactly("jasper.commands|WARNING|make|Exited 2 · 45s|" + window + "|live");
    }

    @Test void clickingTheNotificationFocusesThePane() {
        CommandNotifier notifier = notifier(10);
        var focused = new ArrayList<String>();
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> focused.add("pane"));
        center.activate(center.history().getFirst().id());
        assertThat(focused).containsExactly("pane");
    }

    /** You were looking straight at it, so there is nothing to tell. */
    @Test void aCommandThatEndsInTheFocusedPaneIsNotPosted() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), FOCUSED_PANE, window, () -> {});
        assertThat(center.history()).isEmpty();
        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
    }

    /** The deliberate widening: a visible split pane you are not typing in still notifies. */
    @Test void aVisibleButUnfocusedSplitPaneStillNotifies() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), UNFOCUSED_SPLIT, window, () -> {});
        assertThat(center.history()).hasSize(1);
    }

    @Test void theOperatingSystemHearsOnlyWhileJasperIsInTheBackground() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("a", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        assertThat(os).as("the card says it in the foreground").isEmpty();
        presence = Delivery.Presence.BACKGROUND;
        notifier.finished("b", "./gradlew build", OptionalInt.of(1), Duration.ofSeconds(72), HIDDEN_TAB, window, () -> {});
        assertThat(os).containsExactly(new Sent("./gradlew build", "Exited 1 · 1m 12s"));
        assertThat(center.history()).as("history and unread either way").hasSize(2);
        assertThat(center.unread()).isEqualTo(2);
    }

    @Test void aShortCommandLeavesNothingBehind() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "ls", () -> 0L, () -> {}, WATCHED);
        notifier.finished("pane", "ls", OptionalInt.of(0), Duration.ofSeconds(2), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).isEmpty();
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).as("it never passed the threshold").isEmpty();
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    @Test void aZeroThresholdTurnsTheWholeFeatureOff() {
        CommandNotifier notifier = notifier(0);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);
        notifier.hidden("pane");
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofHours(1), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).isEmpty();
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void aSecondLongCommandDoesNotRestartTheTypingAndTheLastOneEndsIt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "one", () -> 0L, () -> {}, WATCHED);
        notifier.started("b", "two", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        assertThat(working).containsExactly(true);
        notifier.finished("a", "one", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(working).as("one still running").containsExactly(true);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("two");
        notifier.finished("b", "two", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(working).containsExactly(true, false);
    }

    @Test void aPaneClosingMidCommandRemovesItsCardStopsTheTypingAndPostsNothing() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> {}, WATCHED);
        passThreshold();
        notifier.closed("pane");
        assertThat(deck.notices()).isEmpty();
        assertThat(working).containsExactly(true, false);
        assertThat(center.history()).isEmpty();
    }

    @Test void closingThePaneExpiresItsCommandsNotifications() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        notifier.finished("pane", "make test", OptionalInt.of(1), Duration.ofSeconds(40), HIDDEN_TAB, window, () -> {});
        notifier.finished("a", "other", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        notifier.closed("pane");
        assertThat(notifications()).containsExactly(
            "jasper.commands|INFO|other|Finished in 30s|" + window + "|live",
            "jasper.commands|WARNING|make test|Exited 1 · 40s|" + window + "|expired",
            "jasper.commands|INFO|make|Finished in 30s|" + window + "|expired");
    }

    @Test void aPaneClosingBeforeTheThresholdLeavesNothingBehind() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "ls", () -> 0L, () -> {}, WATCHED);
        notifier.closed("pane");
        assertThat(deck.notices()).isEmpty();
        assertThat(scheduled).isEmpty();
        assertThat(working).isEmpty();
    }

    @Test void aSecondCommandInTheSamePaneSupersedesTheFirstRatherThanStacking() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "first", () -> 0L, () -> {}, UNWATCHED);
        notifier.started("pane", "second", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second");
        assertThat(working).as("the first command's typing ends before the second's begins").containsExactly(true, false, true);
    }

    @Test void multiLineCommandsCollapseForTheTitle() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "echo a\necho b", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices().getFirst().title()).isEqualTo("echo a ↵ echo b");
        notifier.finished("pane", "echo a\necho b", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(center.history().getFirst().request().title()).isEqualTo("echo a ↵ echo b");
    }

    @Test void aCommandWithNothingVisibleToSayIsCalledCommand() {
        CommandNotifier notifier = notifier(10);
        String invisible = "  " + new String(new int[] {0x200B, 0x2060}, 0, 2) + " ";
        notifier.started("pane", invisible, () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices().getFirst().title()).isEqualTo("Command");
        notifier.finished("pane", invisible, OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(center.history().getFirst().request().title()).isEqualTo("Command");
    }

    @Test void durationsReadAsPeopleSayThem() {
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(72))).isEqualTo("1m 12s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(120))).isEqualTo("2m");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(7500))).isEqualTo("2h 5m");
    }

    /** Out of sight is the whole reason a card exists, so there is nothing to wait for. */
    @Test void aCommandStartedInAPaneYouAreNotWatchingShowsAtOnce() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("it did not wait for the threshold").isEmpty();
        assertThat(working).containsExactly(true);
    }

    @Test void backgroundingThePaneShowsWhateverIsRunningInItImmediately() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        assertThat(deck.notices()).as("watched, so it is waiting for the threshold").isEmpty();
        notifier.hidden("pane");
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    @Test void backgroundingAPaneTwiceDoesNotCountItsCommandTwice() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        notifier.hidden("pane");
        notifier.hidden("pane");
        passThreshold();
        assertThat(deck.notices()).hasSize(1);
        assertThat(working).as("one command, one typing buddy").containsExactly(true);
    }

    @Test void backgroundingAPaneWithNothingRunningDoesNothing() {
        CommandNotifier notifier = notifier(10);
        notifier.hidden("pane");
        assertThat(deck.notices()).isEmpty();
        assertThat(working).isEmpty();
    }

    @Test void programTitleChangesUpdateOnlyTheirPaneWithoutReordering() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker a", () -> 0L, () -> {}, UNWATCHED);
        notifier.started("b", "worker b", () -> 0L, () -> {}, UNWATCHED);
        int generation = deck.generation();
        notifier.titleChanged("a", "Reviewing database migration");
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("worker b", "Reviewing database migration");
        assertThat(deck.generation()).isEqualTo(generation);
    }

    @Test void titlesReceivedBeforeTheThresholdAreUsedWhenTheCardAppears() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, WATCHED);
        notifier.titleChanged("a", "Indexing the repository");
        assertThat(deck.notices()).isEmpty();
        passThreshold();
        assertThat(deck.notices().getFirst().title()).isEqualTo("Indexing the repository");
    }

    /** Completion names the command that ran, never the program or prompt title around it. */
    @Test void theNotificationNamesTheCommandThatRanNotItsTitle() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, UNWATCHED);
        notifier.titleChanged("a", "Review complete");
        notifier.finished("a", "worker", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        notifier.titleChanged("a", "user@host: ~/projects");
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.request().title()).isEqualTo("worker"));
        assertThat(deck.notices()).as("a prompt title cannot bring the card back").isEmpty();
    }

    @Test void failedCommandsRetainArgumentsAndSanitizeMultilineTitles() {
        CommandNotifier notifier = notifier(10);
        String command = "  printf 'hello'\r\necho goodbye  ";
        notifier.started("pane", command, () -> 0L, () -> {}, WATCHED);
        notifier.titleChanged("pane", "/tmp/project");
        presence = Delivery.Presence.BACKGROUND;
        notifier.finished("pane", command, OptionalInt.of(2), Duration.ofSeconds(12), HIDDEN_TAB, window, () -> {});
        assertThat(os).containsExactly(new Sent("printf 'hello' ↵ echo goodbye", "Exited 2 · 12s"));
        assertThat(center.history().getFirst().request().title()).isEqualTo("printf 'hello' ↵ echo goodbye");
    }

    @Test void changingATitleDoesNotResurrectADismissedRunningCard() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, UNWATCHED);
        deck.dismiss(new BuddyNoticeId(CommandNotifier.SOURCE, "a"));
        notifier.titleChanged("a", "Still working");
        assertThat(deck.notices()).isEmpty();
    }

    @Test void longTitlesKeepTheirFullContentAndUnicodeForTheRendererToFit() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, UNWATCHED);
        String title = "review ".repeat(20) + new String(Character.toChars(0x1F422));
        notifier.titleChanged("a", title);
        assertThat(deck.notices().getFirst().title()).isEqualTo(title);
    }

    @Test void savedCallbacksAndLateEventsCannotResurrectClosedProducers() {
        for (int scenario = 0; scenario < 4; scenario++) {
            var notifier = notifier(10);
            String key = "late-" + scenario;
            notifier.opened(key);
            Runnable saved = () -> {};
            if (scenario != 0) {
                notifier.started(key, "build", () -> 20_000_000_000L, () -> {}, WATCHED);
                saved = scheduled.getLast();
            }
            if (scenario == 2) {
                saved.run();
                notifier.finished(key, "build", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, window, () -> {});
            }
            if (scenario == 3) notifier.close(); else notifier.closed(key);
            int generation = deck.generation(), sent = os.size(), changes = working.size(), posted = center.history().size();
            saved.run();
            notifier.started(key, "late", () -> 20_000_000_000L, () -> {}, UNWATCHED);
            notifier.finished(key, "late", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, window, () -> {});
            notifier.hidden(key); notifier.titleChanged(key, "late");
            assertThat(deck.generation()).isEqualTo(generation);
            assertThat(os).hasSize(sent);
            assertThat(working).hasSize(changes);
            assertThat(center.history()).hasSize(posted);
            notifier.close(); notifier.close();
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.CommandNotifierTest' --tests 'dev.jasper.app.notifications.NotificationCenterTest'`
Expected: compilation fails: `NotificationCenter.presence()`, `CommandNotifier.GROUP` and the new constructor and
`finished` signature do not exist.

- [ ] **Step 3: Give the center its presence**

In `NotificationCenter.java`, after `public Clock clock() { return clock; }`, add:

```java

    /** Whether Jasper is focused, in the background, or has no window open, as delivery sees it now. */
    public Delivery.Presence presence() {
        requireEdt();
        return presence.get();
    }
```

- [ ] **Step 4: Rewrite the notifier**

Replace the whole of `CommandNotifier.java` with:

```java
package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The terminal's producer for long commands (spec section 7, producer 3). A command that runs long enough, or runs out
 * of sight, gets a live Buddy card and starts Buddy typing; when it ends the card goes. If it ran at least the threshold
 * and did not finish in the pane you were typing in, its outcome is posted in {@link #GROUP}: the command as the
 * title, "Finished in 2m 03s" (INFO) or "Exited 1 · 2m 03s" (WARNING) as the content, a click that focuses the pane, and
 * a card in the pane's window. A pane closing expires its notifications, since there is no pane left to focus.
 *
 * <p>Until plan 5's OS notifiers, the outcome also goes to the operating system while Jasper is in the background; in
 * the foreground the card says it.
 *
 * <p>Holds no Swing state. Every seam is a standard functional type, including {@code schedule}, which is Swing's
 * {@code Timer} in the application and a list a test runs by hand. EDT only.
 */
public final class CommandNotifier implements AutoCloseable {
    /** Where long commands' outcomes go. */
    public static final NotificationGroup GROUP = new NotificationGroup("jasper.commands", "Long-running commands",
        NotificationRequest.JASPER, DisplayMode.CARD);
    /** Every card this class posts is the terminal's; a transfer or an SSH session brings its own. */
    static final String SOURCE = "terminal";

    private final Set<Object> activeProducers = new HashSet<>();
    private final Map<Object, InFlight> inFlight = new HashMap<>();
    /** The notifications each pane's commands posted, expired when the pane closes. */
    private final Map<Object, Set<UUID>> outcomes = new HashMap<>();
    private boolean disposed;
    private Supplier<Duration> threshold;
    private BuddyCompanion deck;
    private NotificationCenter center;
    private BiConsumer<String, String> operatingSystem;
    private Consumer<Boolean> onWorkingChanged;
    private BiFunction<Duration, Runnable, Runnable> schedule;
    /** Commands that have been running past the threshold. */
    private int running;

    /** A command between its start mark and its end, with the timer that will promote it to a card. */
    private static final class InFlight {
        Runnable cancel = () -> {};
        boolean passed;
        String title = "";
        Supplier<String> detail = () -> "";
        Runnable activate = () -> {};
    }

    /**
     * EDT: registers {@link #GROUP} with the center.
     *
     * @param threshold how long a command must run to be worth telling; zero turns the feature off
     * @param deck shows running commands
     * @param center receives the outcomes
     * @param operatingSystem receives an outcome's title and content while Jasper is in the background (plan 5 replaces
     *     it with the OS notifiers)
     * @param onWorkingChanged hears whether any long command is running, for Buddy's typing
     * @param schedule runs a task after a delay and returns the way to cancel it
     */
    public CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center,
                           BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
                           BiFunction<Duration, Runnable, Runnable> schedule) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.deck = Objects.requireNonNull(deck, "deck");
        this.center = Objects.requireNonNull(center, "center");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        center.register(GROUP);
    }

    /** A pane opened: events for it count from now. */
    public void opened(Object key) { if (!disposed) activeProducers.add(Objects.requireNonNull(key)); }

    private boolean accepts(Object key) { return !disposed && activeProducers.contains(key); }

    /**
     * A command began. Nothing is shown yet: after {@code threshold} it becomes a running card, so Buddy does not twitch
     * for every {@code ls}, unless it started out of sight. {@code elapsedNanos} lets the card tick without being
     * re-posted every second.
     */
    public void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate, boolean watched) {
        if (!accepts(key)) return;
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        flight.title = title(command);
        flight.detail = () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong()));
        flight.activate = activate;
        inFlight.put(key, flight);
        // Out of sight is the whole reason a card exists, so there is nothing to wait for. The threshold stays as the
        // fallback for a command running in the pane you are still watching.
        if (!watched) { promote(key, flight); return; }
        flight.cancel = schedule.apply(wait, () -> promote(key, flight));
    }

    /** The pane stopped being watched: anything running in it is now worth showing. */
    public void hidden(Object key) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight != null && !flight.passed) promote(key, flight);
    }

    /** Turns an in-flight command into a running card, once. */
    private void promote(Object key, InFlight flight) {
        if (!accepts(key)) return;
        if (inFlight.get(key) != flight || flight.passed) return;
        flight.passed = true;
        flight.cancel.run();
        flight.cancel = () -> {};
        if (running++ == 0) onWorkingChanged.accept(true);
        deck.post(new BuddyNotice(new BuddyNoticeId(SOURCE, key), BuddyNotice.Kind.TASK, flight.title,
            BuddyNotice.State.RUNNING, flight.detail, flight.activate));
    }

    /** Refreshes a running card's wording without promoting it or replaying its arrival. */
    public void titleChanged(Object key, String programTitle) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight == null) return; // Nothing is running, so a prompt title has nothing to rename.
        String updated = singleLine(programTitle);
        if (updated.isBlank() || updated.equals(flight.title)) return;
        flight.title = updated;
        if (flight.passed) deck.updateTitle(new BuddyNoticeId(SOURCE, key), updated);
    }

    /**
     * A command ended. Its running card goes; the outcome is posted unless it was short or finished in the pane you were
     * typing in.
     *
     * @param window the pane's window, where the card shows; null for the window used last
     * @param activate focuses the pane
     */
    public void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                         CommandNotice.Origin origin, UUID window, Runnable activate) {
        if (!accepts(key)) return;
        // Shell and program titles can name the working directory (for example "~"); completion carries the command
        // that actually ran, arguments included.
        String title = title(command);
        cancel(key);
        if (!CommandNotice.shouldNotify(origin, ran, threshold.get())) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran) : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        var request = NotificationRequest.of(GROUP.id(), succeeded ? Severity.INFO : Severity.WARNING, title)
            .withContent(detail).withActivate(Objects.requireNonNull(activate, "activate"));
        if (window != null) request = request.withTarget(window);
        center.post(request).ifPresent(id -> outcomes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id));
        if (center.presence() != Delivery.Presence.FOCUSED) operatingSystem.accept(title, detail);
    }

    /** The pane is gone: its running card goes, and its notifications expire, since there is no pane to focus. */
    public void closed(Object key) {
        if (disposed || !activeProducers.remove(key)) return;
        cancel(key);
        Set<UUID> posted = outcomes.remove(key);
        if (posted != null) for (UUID id : posted) center.expire(id);
    }

    /** Drops any in-flight command for this key: its card goes and it releases the typing animation if it held it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        flight.cancel = () -> {};
        flight.activate = () -> {};
        flight.detail = () -> "";
        if (!flight.passed) return;
        deck.dismiss(new BuddyNoticeId(SOURCE, key));
        if (running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** One line; the card and notification renderers fit it to their width. A command showing nothing is "Command". */
    static String title(String command) {
        String line = singleLine(command.strip());
        return NotificationText.visible(line) ? line : "Command";
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ↵ ");
    }

    @Override public void close() {
        if (disposed) return;
        for (Object key : List.copyOf(inFlight.keySet())) cancel(key);
        disposed = true;
        activeProducers.clear();
        outcomes.clear();
        threshold = () -> Duration.ZERO;
        deck = null;
        center = null;
        operatingSystem = (title, detail) -> {};
        onWorkingChanged = value -> {};
        schedule = (delay, action) -> () -> {};
    }

    /** "45s", "1m 12s", "2m", "2h 5m": the way a person would say it, not ISO-8601. */
    static String humanize(Duration ran) {
        long seconds = Math.max(0, ran.toSeconds());
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) {
            long minutes = seconds / 60, rest = seconds % 60;
            return rest == 0 ? minutes + "m" : String.format(Locale.ROOT, "%dm %ds", minutes, rest);
        }
        long hours = seconds / 3600, minutes = (seconds % 3600) / 60;
        return minutes == 0 ? hours + "h" : String.format(Locale.ROOT, "%dh %dm", hours, minutes);
    }
}
```

- [ ] **Step 5: Wire it in the application**

In `JasperApplication.java`:

1. Replace

```java
        notifications = new CommandNotifier(() -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
            buddy.companion(), nativeNotifier::send, buddy.companion()::setWorking, JasperApplication::afterDelay);
```

   with

```java
        notifications = new CommandNotifier(() -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
            buddy.companion(), notificationCenter, nativeNotifier::send, buddy.companion()::setWorking, JasperApplication::afterDelay);
```

2. Replace

```java
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), value.activate());
```

   with

```java
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), windowId, value.activate());
```

3. Replace `                        case FOCUSED -> notifications.looked(value.id());` with
   `                        case FOCUSED -> { }`.

In `TerminalTitleIntegrationTest.java`:

1. Add the imports `dev.jasper.app.notifications.Delivery` and `dev.jasper.app.notifications.NotificationCenter`, and
   `java.time.Clock`.
2. Replace

```java
    private record Fixture(WindowContent content, BuddyTestSupport deck, List<String> nativeTitles,
                           List<String> notificationTitles) { }
```

   with

```java
    private record Fixture(WindowContent content, BuddyTestSupport deck, List<String> nativeTitles,
                           List<String> notificationTitles, NotificationCenter center) { }
```

3. Replace

```java
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck.companion(),
                (title, detail) -> notificationTitles.add(title), working -> {}, (delay, run) -> () -> {});
```

   with

```java
            // In the background, so the interim OS sink hears the outcome as well as the center.
            var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.BACKGROUND);
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck.companion(), center,
                (title, detail) -> notificationTitles.add(title), working -> {}, (delay, run) -> () -> {});
```

4. Replace

```java
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), value.activate());
```

   with

```java
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), null, value.activate());
```

5. Replace `                        case FOCUSED -> notifier.looked(value.id());` with `                        case FOCUSED -> { }`.
6. Replace `            fixture[0] = new Fixture(window, deck, titles, notificationTitles);` with
   `            fixture[0] = new Fixture(window, deck, titles, notificationTitles, center);`.
7. In `aBurstEndingWithAPromptTitleCannotOverwriteTheCompletedNotice`, replace

```java
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("sleep 12");
            assertThat(f.notificationTitles).containsExactly("sleep 12");
            assertThat(f.deck.notices().getFirst().state()).isEqualTo(BuddyNotice.State.DONE);
```

   with

```java
            assertThat(f.center.history()).singleElement()
                .satisfies(entry -> assertThat(entry.request().title()).isEqualTo("sleep 12"));
            assertThat(f.notificationTitles).containsExactly("sleep 12");
            assertThat(f.deck.notices()).as("Buddy keeps live work only").isEmpty();
```

   and remove the import `dev.jasper.buddy.notice.BuddyNotice` if nothing else in the file uses it.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.workspace.TerminalTitleIntegrationTest' --tests 'dev.jasper.app.application.*'`
Expected: PASS. (`TerminalTitleIntegrationTest` runs real shells; it is disabled on Windows.)

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/notifications/CommandNotifierTest.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTitleIntegrationTest.java
git commit -m "feat(notifications): post long commands' outcomes in Long-running commands" -m "A command that ran past the threshold and did not finish in the pane you were typing in is posted in jasper.commands, in the pane's window, with a click that focuses the pane; Buddy's card lives only while the command runs, and a closed pane expires its notifications. The macOS banner stays, background only, until plan 5." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: An activity's card ends with its work

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java` (rewritten)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/ActivityNotifierTest.java` (rewritten)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Consumes: plan 3's `PluginHost` outcome posts (`<plugin-id>.activities`); `PluginRuntime.NotificationHost` and its
  eight-argument `PluginRuntime` constructor.
- Produces: `ActivityNotifier.ended(String source, UUID id)`; `finished(...)` (both overloads) and `cancelled(...)`
  are removed. `started`, `progress`, `connection`, `connectionClosed` and `close` keep their signatures.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `ActivityNotifierTest.java` with:

```java
package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ActivityNotifierTest {
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final UUID id = UUID.randomUUID();

    @Test void aRunningActivityIsOneCardWhoseDetailFollowsProgress() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.started("dev.jasper.sftp", id, "Upload\nsite.tar", "starting");
        notifier.progress("dev.jasper.sftp", id, "40% · 4 of 10 MB");
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(new BuddyNoticeId("dev.jasper.sftp", id));
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.TASK);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.title()).isEqualTo("Upload site.tar");
            assertThat(notice.detail().get()).isEqualTo("40% · 4 of 10 MB");
        });
    }

    /** The outcome is a notification the plugin host posts; Buddy keeps live work only. */
    @Test void anActivityThatEndsLeavesNoCardWhateverItsOutcome() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        UUID other = UUID.randomUUID();
        notifier.started("p.one", id, "Upload", "");
        notifier.started("p.one", other, "Download", "");
        notifier.ended("p.one", id);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("Download");
        notifier.ended("p.one", other);
        notifier.ended("p.one", UUID.randomUUID());
        assertThat(deck.notices()).isEmpty();
        notifier.progress("p.one", id, "late");
        assertThat(deck.notices()).as("progress after the end brings nothing back").isEmpty();
    }

    @Test void aConnectionIsOneCardWhoseNewStateIsNewsAndWhoseDetailIsNot() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        var key = new BuddyNoticeId("dev.jasper.remote", id);
        notifier.connection("dev.jasper.remote", id, "Tunnels via\ndb", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 1 s", Optional.empty());
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(key);
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.CONNECTION);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DEGRADED);
            assertThat(notice.title()).isEqualTo("Tunnels via db");
        });
        int generation = deck.generation();
        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 2 s", Optional.empty());
        assertThat(deck.generation()).as("the same state is not new news").isEqualTo(generation);
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("reconnecting in 2 s");

        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DOWN, "gave up", Optional.empty());
        assertThat(deck.generation()).as("a new state is").isGreaterThan(generation);
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DOWN);
            assertThat(notice.detail().get()).isEqualTo("gave up");
        });

        notifier.connectionClosed("dev.jasper.remote", id);
        assertThat(deck.notices()).isEmpty();
    }

    @Test void aClickRaisesTheApplicationAndRunsTheProducersAction() {
        var raised = new AtomicInteger();
        var ran = new AtomicInteger();
        var notifier = new ActivityNotifier(deck.companion(), raised::incrementAndGet);
        UUID other = UUID.randomUUID();
        notifier.connection("p.one", id, "Tunnels", ActivityNotifier.ConnectionState.DOWN, "gave up",
            Optional.<Runnable>of(ran::incrementAndGet));
        notifier.started("p.one", other, "Upload", "");
        var byId = deck.notices().stream().collect(Collectors.toMap(BuddyNotice::id, notice -> notice));

        byId.get(new BuddyNoticeId("p.one", id)).activate().run();
        assertThat(raised).hasValue(1);
        assertThat(ran).hasValue(1);

        byId.get(new BuddyNoticeId("p.one", other)).activate().run();
        assertThat(raised).hasValue(2);
        assertThat(ran).as("a card without an action only raises").hasValue(1);
        assertThat(deck.notices()).as("a click removes nothing: the work goes on").hasSize(2);
    }

    @Test void nothingIsPostedAfterClose() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.close();
        notifier.close();
        notifier.started("p.one", id, "Upload", "");
        notifier.progress("p.one", id, "x");
        notifier.ended("p.one", id);
        notifier.connection("p.one", id, "Tunnel", ActivityNotifier.ConnectionState.UP, "", Optional.empty());
        notifier.connectionClosed("p.one", id);
        assertThat(deck.notices()).isEmpty();
    }
}
```

In `PluginRuntimeTest.java`:

1. In `runsADevelopmentPluginBridgesItsActivityAndStopsIt`, replace

```java
        onEdt(() -> assertThat(deck.notices()).singleElement()
            .satisfies(notice -> assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED)));
```

   with

```java
        onEdt(() -> assertThat(deck.notices()).as("the activity it left open ends with it, and so does its card").isEmpty());
```

2. In `connectionCardsFollowTheirPluginAndRunOnlyItsOwnAction`, replace

```java
            assertThat(raised).hasValue(1);
            assertThat(deck.acknowledged(link.id())).isTrue();
            stray.activate().run();
            assertThat(raised).as("another plugin's action only raises the application").hasValue(2);
            assertThat(deck.acknowledged(stray.id())).isFalse();
```

   with

```java
            assertThat(raised).hasValue(1);
            stray.activate().run();
            assertThat(raised).as("another plugin's action only raises the application").hasValue(2);
```

In `BundledSamplePluginTest.java`:

1. Add the imports `dev.jasper.app.notifications.Delivery`, `dev.jasper.app.notifications.NotificationCenter`,
   `java.time.Clock` and `javax.swing.SwingUtilities`, and remove `dev.jasper.buddy.notice.BuddyNotice`.
2. Rename the test `theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy` to
   `theStagedSampleLoadsFromItsJarAndItsActivityEndsAsANotification`, and change the class JavaDoc's "to Buddy" to
   "to Buddy and the notification center".
3. Replace

```java
        var deck = new BuddyTestSupport();
        var runtime = new AtomicReference<PluginRuntime>();
```

   with

```java
        var deck = new BuddyTestSupport();
        var center = AppContractTest.onEdtValue(() -> new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(),
            () -> Delivery.Presence.FOCUSED));
        var notifications = new PluginRuntime.NotificationHost(center,
            caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(center.post(caused.request()))), (title, details) -> { });
        var runtime = new AtomicReference<PluginRuntime>();
```

4. Replace `                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));` with
   `                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry(), notifications, path -> { }));`.
5. Replace

```java
        var state = new AtomicReference<BuddyNotice.State>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (state.get() != BuddyNotice.State.DONE && System.nanoTime() < deadline) {
            onEdt(() -> state.set(deck.notices().isEmpty() ? null : deck.notices().get(0).state()));
            Thread.sleep(20);
        }
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.jasper.sample");
            assertThat(notice.title()).isEqualTo("Sample plugin");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DONE);
            assertThat(notice.detail().get()).isEqualTo("Ready");
        }));
```

   with

```java
        String outcome = "dev.jasper.sample.activities|INFO|Sample plugin|Ready";
        var lines = new AtomicReference<List<String>>(List.of());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!lines.get().contains(outcome) && System.nanoTime() < deadline) {
            onEdt(() -> lines.set(center.history().stream().map(entry -> String.join("|", entry.request().group(),
                entry.request().severity().name(), entry.request().title(), entry.request().content())).toList()));
            Thread.sleep(20);
        }
        assertThat(lines.get()).contains(outcome);
        onEdt(() -> assertThat(deck.notices()).as("Buddy keeps live work only").isEmpty());
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.ActivityNotifierTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.plugins.BundledSamplePluginTest'`
Expected: compilation fails, because `ActivityNotifier.ended` does not exist.

- [ ] **Step 3: End cards instead of keeping outcomes**

Replace the whole of `ActivityNotifier.java` with:

```java
package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Buddy's producer for what something other than a terminal pane volunteers: plugin activities and plugin connections,
 * each keyed by its source and id. An activity is one live task card from its start until it ends, however it ends; its
 * outcome is a notification the plugin host posts (spec section 7, producer 4), not a card. A connection is one
 * connection card: a new state posts it again, which is new news, and closing the connection removes it. Progress, and a
 * report that keeps a connection's state, only change the detail the card already reads, so nothing is re-posted. A
 * click raises the application, then runs the producer's action when it named one. This producer never drives Buddy's
 * working animation, which the command notifier owns. EDT only; holds no Swing state.
 */
public final class ActivityNotifier implements AutoCloseable {
    /** What a connection card can say. */
    public enum ConnectionState {
        /** Available. */
        UP,
        /** Available with a problem, or recovering. */
        DEGRADED,
        /** Unavailable. */
        DOWN
    }

    private final Map<BuddyNoticeId, String> details = new HashMap<>();
    private final Map<BuddyNoticeId, ConnectionState> connections = new HashMap<>();
    private BuddyCompanion deck;
    private Runnable activate;

    /**
     * Binds the producer to the companion.
     *
     * @param deck the companion that shows the cards
     * @param activate what every click on a card does first; a producer has no pane to focus, so the application
     *                 raises itself
     */
    public ActivityNotifier(BuddyCompanion deck, Runnable activate) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.activate = Objects.requireNonNull(activate, "activate");
    }

    /**
     * Posts a running card whose click only raises the application.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     */
    public void started(String source, UUID id, String title, String detail) {
        started(source, id, title, detail, Optional.empty());
    }

    /**
     * Posts a running card.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     * @param action run after the application is raised when the card is clicked; empty to only raise the application
     */
    public void started(String source, UUID id, String title, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title), BuddyNotice.State.RUNNING,
            () -> details.getOrDefault(key, ""), activation(action)));
    }

    /**
     * Changes a running card's detail; ignored when no such card is running.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param detail the new detail line
     */
    public void progress(String source, UUID id, String detail) {
        if (deck == null) return;
        details.computeIfPresent(new BuddyNoticeId(source, id), (key, previous) -> detail);
    }

    /**
     * Removes the card of work that ended, however it ended; its outcome, if any, is a notification.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     */
    public void ended(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.dismiss(key);
    }

    /**
     * Shows a connection. A new state posts the card again, which is new news; the same state only changes the detail
     * the card reads.
     *
     * @param source the producer namespace, a plugin id
     * @param id the connection's identity within that source
     * @param title display title; line breaks become spaces
     * @param state what the card says
     * @param detail detail line
     * @param action run after the application is raised when the card is clicked; empty to only raise the application
     */
    public void connection(String source, UUID id, String title, ConnectionState state, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        if (connections.put(key, Objects.requireNonNull(state, "state")) == state) return;
        BuddyNotice.State shown = switch (state) {
            case UP -> BuddyNotice.State.UP;
            case DEGRADED -> BuddyNotice.State.DEGRADED;
            case DOWN -> BuddyNotice.State.DOWN;
        };
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.CONNECTION, singleLine(title), shown,
            () -> details.getOrDefault(key, ""), activation(action)));
    }

    /**
     * Removes a connection's card: its producer no longer reports it.
     *
     * @param source the producer namespace
     * @param id the connection's identity
     */
    public void connectionClosed(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        connections.remove(key);
        deck.dismiss(key);
    }

    /** Raises the application, then runs the producer's action when it named one. */
    private Runnable activation(Optional<Runnable> action) {
        Objects.requireNonNull(action, "action");
        return () -> {
            if (deck == null) return;
            activate.run();
            action.ifPresent(Runnable::run);
        };
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ");
    }

    @Override public void close() {
        deck = null;
        activate = () -> { };
        details.clear();
        connections.clear();
    }
}
```

In `PluginRuntime.java`, in `forward`, replace

```java
            case SUCCEEDED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), true, event.detail(), action);
            case FAILED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), false, event.detail(), action);
            case CANCELLED -> notifier.cancelled(event.sourcePluginId(), event.id());
```

with

```java
            case SUCCEEDED, FAILED, CANCELLED -> notifier.ended(event.sourcePluginId(), event.id());
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/test/java/dev/jasper/app/notifications/ActivityNotifierTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java
git commit -m "feat(notifications): an activity's Buddy card ends with its work" -m "ActivityNotifier.ended dismisses the card however the activity ended; its outcome is the notification the plugin host posts. A click raises the application and runs the activity's action, and nothing is acknowledged." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Buddy keeps live cards only

**Files:**
- Modify: `jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNotice.java` (rewritten)
- Modify: `jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java` (rewritten) and its `package-info.java`
- Modify: `jasper-buddy/src/main/java/dev/jasper/buddy/view/BuddyCompanion.java`
- Modify: `jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddyCard.java`, `BuddyColumnPanel.java`,
  `BuddyDeckPanel.java`, `BuddyColumnWindow.java`
- Modify: `jasper-buddy/src/testFixtures/java/dev/jasper/buddy/view/BuddyTestSupport.java`
- Modify: `jasper-buddy/README.md` (its compiled example), `docs/app-maintenance.md` (its compiled example)
- Test: `jasper-buddy/src/test/java/dev/jasper/buddy/notice/BuddyNoticeTest.java` (rewritten)
- Test: `jasper-buddy/src/test/java/dev/jasper/buddy/internal/model/BuddyDeckTest.java` (rewritten)
- Test: `jasper-buddy/src/test/java/dev/jasper/buddy/internal/presentation/BuddyColumnPanelTest.java`,
  `BuddyDeckPanelTest.java`, `BuddyCardTest.java`, `BuddyShimmerTest.java`
- Test: `jasper-buddy/src/test/java/dev/jasper/buddy/view/BuddyCompanionTest.java`
- Test: `jasper-buddy/src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/BuddyIntegrationTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java`

**Interfaces:**
- Consumes: Tasks 2 and 3, after which no production code calls `acknowledge`, `orphan` or posts `DONE` or `FAILED`.
- Produces:
  - `BuddyNotice.State` is `{ RUNNING, NEEDS_INPUT, UP, DEGRADED, DOWN }`; `activate` is required (non-null);
    `live()` and `orphaned()` are removed; `wantsAttention()` is `NEEDS_INPUT`, `DEGRADED` or `DOWN`.
  - `BuddyCompanion` loses `acknowledge(BuddyNoticeId)`, `orphan(BuddyNoticeId)` and `orphan(BuddyNoticeId, String)`.
  - `BuddyDeck` loses `acknowledge`, `acknowledged`, both `orphan` methods and `column()`; the column and the drawer
    both read `notices()`.
  - `BuddyTestSupport` loses `column()`, `acknowledged(BuddyNoticeId)` and `acknowledge(BuddyNoticeId)`.

- [ ] **Step 1: Write the failing Buddy tests**

Replace the whole of `BuddyNoticeTest.java` with:

```java
package dev.jasper.buddy.notice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BuddyNoticeTest {
    private static BuddyNotice of(BuddyNotice.Kind kind, BuddyNotice.State state) {
        return new BuddyNotice(new BuddyNoticeId("terminal", "k"), kind, "title", state, () -> "d", () -> { });
    }

    /** A tunnel never completes and a command is never "up"; an impossible notice cannot be built. */
    @Test void eachKindAcceptsOnlyItsOwnStates() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            boolean task = switch (state) {
                case RUNNING, NEEDS_INPUT -> true;
                case UP, DEGRADED, DOWN -> false;
            };
            assertThat(state.fits(BuddyNotice.Kind.TASK)).as("%s as a task", state).isEqualTo(task);
            assertThat(state.fits(BuddyNotice.Kind.CONNECTION)).as("%s as a connection", state).isEqualTo(!task);
        }
    }

    /** Buddy keeps live work only: work that ends is dismissed, and its outcome is a notification. */
    @Test void thereAreNoOutcomeStates() {
        assertThat(BuddyNotice.State.values()).extracting(Enum::name)
            .containsExactly("RUNNING", "NEEDS_INPUT", "UP", "DEGRADED", "DOWN");
    }

    @Test void aNoticeWhoseStateDoesNotFitItsKindIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> of(BuddyNotice.Kind.TASK, BuddyNotice.State.UP));
        assertThatIllegalArgumentException().isThrownBy(() -> of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.RUNNING));
    }

    /** Wants you specifically: it is waiting on you, or it is in trouble. */
    @Test void attentionIsInputOrTrouble() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.NEEDS_INPUT).wantsAttention()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DEGRADED).wantsAttention()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DOWN).wantsAttention()).isTrue();
    }

    /** A card always has somewhere to go: a producer whose origin is gone dismisses it instead. */
    @Test void aNoticeNeedsAnActivation() {
        assertThatNullPointerException().isThrownBy(() -> new BuddyNotice(new BuddyNoticeId("terminal", "k"),
            BuddyNotice.Kind.TASK, "sleep 600", BuddyNotice.State.RUNNING, () -> "d", null));
    }
}
```

Replace the whole of `BuddyDeckTest.java` with:

```java
package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyDeckTest {
    private final BuddyDeck deck = new BuddyDeck();

    private static BuddyNotice running(String source, Object key, String title) {
        return new BuddyNotice(new BuddyNoticeId(source, key), BuddyNotice.Kind.TASK, title, BuddyNotice.State.RUNNING,
            () -> "running", () -> { });
    }

    @Test void noticesComeBackNewestFirst() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second", "first");
    }

    @Test void postingOverTheSameKeyReplacesInPlaceAndPromotesToTheTop() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        deck.post(running("terminal", "a", "first again"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("first again", "second");
    }

    /** The whole point of the source being part of the identity: producers cannot collide. */
    @Test void twoProducersWithTheSameKeyAreTwoNotices() {
        deck.post(running("terminal", "1", "a build"));
        deck.post(running("sftp", "1", "a transfer"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a transfer", "a build");
    }

    /** What a producer does when its work ends. */
    @Test void dismissingRemovesOneAndSaysWhetherThereWasOne() {
        deck.post(running("terminal", "a", "first"));
        assertThat(deck.dismiss(new BuddyNoticeId("terminal", "a"))).isTrue();
        assertThat(deck.dismiss(new BuddyNoticeId("terminal", "a"))).isFalse();
        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void clearingEmptiesTheDrawer() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        deck.clear();
        assertThat(deck.notices()).isEmpty();
    }

    @Test void aNewStateOverTheSameKeyIsANewPost() {
        deck.post(running("terminal", "a", "a build"));
        int generation = deck.generation();
        deck.post(new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "a build",
            BuddyNotice.State.NEEDS_INPUT, () -> "waiting", () -> { }));
        assertThat(deck.generation()).isEqualTo(generation + 1);
        assertThat(deck.notices()).singleElement().extracting(BuddyNotice::state).isEqualTo(BuddyNotice.State.NEEDS_INPUT);
    }

    @Test void aTitleChangeIsNotAPost() {
        deck.post(running("terminal", "a", "worker"));
        deck.post(running("terminal", "b", "other"));
        int generation = deck.generation();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "a"), "Reviewing")).isTrue();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "a"), "Reviewing")).isFalse();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "missing"), "Reviewing")).isFalse();
        assertThat(deck.generation()).isEqualTo(generation);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("other", "Reviewing");
    }

    @Test void aSnapshotKeepsWhatItSawWhenTheDeckChanges() {
        deck.post(running("terminal", "a", "first"));
        List<BuddyNotice> before = deck.notices();
        assertThat(deck.notices()).isSameAs(before);
        deck.post(running("terminal", "b", "second"));
        assertThat(before).extracting(BuddyNotice::title).containsExactly("first");
        assertThat(deck.notices()).hasSize(2);
    }

    @Test void theOldestFallOffOnceTheDrawerIsFull() {
        for (int i = 0; i < BuddyDeck.MAX_NOTICES + 5; i++) deck.post(running("terminal", i, "n" + i));
        assertThat(deck.size()).isEqualTo(BuddyDeck.MAX_NOTICES);
        assertThat(deck.notices().getFirst().title()).isEqualTo("n" + (BuddyDeck.MAX_NOTICES + 4));
        assertThat(deck.notices().getLast().title()).isEqualTo("n5");
    }

    @Test void aNoticeNeedsAnIdentityAndSomethingToSay() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "  ", BuddyNotice.State.RUNNING, () -> "d", () -> { }));
    }
}
```

In `BuddyColumnPanelTest.java`:

1. Delete the test `aColumnOfOnlyAcknowledgedFinishedThingsIsEmpty` with the JavaDoc line above it, and the test
   `clickingAnOrphanDoesNothingAtAll`.
2. In `clickingABubbleRunsItsActionAndSaysTheColumnShouldGo`, replace

```java
        post("a", "one", BuddyNotice.State.DONE);
        post("b", "two", BuddyNotice.State.DONE);
```

   with

```java
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
```

3. In `everyArrangementPaintsWithoutBlowingUp`, replace `        post("c", "failed", BuddyNotice.State.FAILED);` with
   `        post("c", "another", BuddyNotice.State.RUNNING);`.
4. In `updatingTheSameNoticeKeepsItsPositionAndDoesNotReplayArrival`, replace

```java
        deck.post(new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "one", BuddyNotice.State.DONE, () -> "Finished", () -> { }));
```

   with

```java
        deck.post(new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "one", BuddyNotice.State.NEEDS_INPUT, () -> "Waiting", () -> { }));
```

   and `        assertThat(panel.needsDetailUpdates()).isFalse();` with
   `        assertThat(panel.needsDetailUpdates()).as("every retained card is live work").isTrue();`.

In `BuddyDeckPanelTest.java`:

1. In `post`, replace `BuddyNotice.State.DONE, () -> "Finished in 1m 12s"` with
   `BuddyNotice.State.RUNNING, () -> "Running · 1m 12s"`.
2. Delete the test `anOrphanedCardReadsButDoesNotClick`.
3. In `dismissingRefreshesTheLiveColumnAsWellAsTheDrawer`, replace `        assertThat(deck.column()).isEmpty();` with
   `        assertThat(deck.notices()).isEmpty();`.

In `BuddyCardTest.java`, replace every `BuddyNotice.State.DONE` with `BuddyNotice.State.NEEDS_INPUT` (three places).

In `BuddyShimmerTest.java`:

1. Rename `highlightTravelsAcrossDetailOnlyAndStopsWhenTheCommandFinishes` to
   `highlightTravelsAcrossDetailOnlyAndStopsWhileTheTaskWaitsForInput`, and replace

```java
                assertThat(differences(paint(options, BuddyNotice.State.DONE, 400_000_000L),
                    paint(options, BuddyNotice.State.DONE, 1_100_000_000L))).isEqualTo(-1);
```

   with

```java
                assertThat(differences(paint(options, BuddyNotice.State.NEEDS_INPUT, 400_000_000L),
                    paint(options, BuddyNotice.State.NEEDS_INPUT, 1_100_000_000L))).isEqualTo(-1);
```

2. In `onlyAnActivelyRunningTaskShimmersAndRequestsFrames`, replace

```java
        deck.orphan(new BuddyNoticeId("test", "one")); panel.refresh();
```

   with

```java
        deck.post(new BuddyNotice(new BuddyNoticeId("test", "one"), BuddyNotice.Kind.TASK, "title", BuddyNotice.State.NEEDS_INPUT,
            () -> "Waiting", () -> {})); panel.refresh();
```

In `BuddyCompanionTest.java`, replace

```java
            buddy.post(notice); buddy.updateTitle(id,"later"); buddy.acknowledge(id); buddy.dismiss(id);
            buddy.orphan(id); buddy.orphan(id,"done"); buddy.clear(); buddy.setWorking(true);
```

with

```java
            buddy.post(notice); buddy.updateTitle(id,"later"); buddy.dismiss(id);
            buddy.clear(); buddy.setWorking(true);
```

and

```java
            () -> b.acknowledge(id), () -> b.dismiss(id), () -> b.orphan(id), () -> b.orphan(id,"done"),
```

with

```java
            () -> b.dismiss(id),
```

In `BuddyExamplesTest.java`, replace

```java
                buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.DONE,
                    () -> "Finished", () -> {}));
                buddy.acknowledge(id);
```

with

```java
                buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.RUNNING,
                    () -> "Compiling", () -> {}));
                buddy.dismiss(id);
```

and in `jasper-buddy/README.md`, inside the `<!-- example:buddy -->` block, make the same replacement (four-space
indentation as in the README):

```java
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.RUNNING,
        () -> "Compiling", () -> {}));
    buddy.dismiss(id);
```

In `jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java`, in `producer`, replace

```java
                // At producer close: cancel queued work first, then freeze and disconnect the notice.
                buddy.orphan(id, "Stopped after 3 files");
                buddy.acknowledge(id);
```

with

```java
                // When the work ends or the producer closes: cancel queued work first, then remove the card.
                buddy.dismiss(id);
```

and in `docs/app-maintenance.md`, inside the `<!-- example:producer -->` block, replace

```java
    // At producer close: cancel queued work first, then freeze and disconnect the notice.
    buddy.orphan(id, "Stopped after 3 files");
    buddy.acknowledge(id);
```

with

```java
    // When the work ends or the producer closes: cancel queued work first, then remove the card.
    buddy.dismiss(id);
```

In `BuddyIntegrationTest.java`, in `hideRetainsNoticesAndCloseUnregistersActivityExactlyOnce`, replace
`BuddyNotice.State.DONE, () -> "done"` with `BuddyNotice.State.RUNNING, () -> "busy"`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-buddy:test`
Expected: compilation fails: `BuddyNoticeTest`'s switch no longer covers `DONE` and `FAILED`.

- [ ] **Step 3: Trim the notice, the deck and the facade**

Replace the whole of `BuddyNotice.java` with:

```java
package dev.jasper.buddy.notice;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One piece of live work worth showing: a running command or transfer, a task waiting for input, or a connection.
 * {@code id} is whatever produced it (a terminal pane, a plugin activity, a plugin connection), and a later notice with
 * the same source and key replaces this one rather than stacking on top of it. Different source strings distinguish
 * producers; each producer must choose keys that are unique within its own source. A producer dismisses its notice when
 * the work ends: Buddy keeps no outcomes.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck being re-posted
 * every second, and so a transfer can report bytes through the same field.
 *
 * <p>Identity is {@link #id()}, never {@code equals}: the record holds two lambdas, so its generated {@code equals}
 * includes callback equality as well as the displayed values.
 * <p>Construction is resource-free. Detail and activation run on EDT and must not block.
 * @param id nonnull source-qualified identity with stable equality
 * @param kind nonnull task or connection kind
 * @param title nonblank display title
 * @param state nonnull state compatible with kind
 * @param detail nonnull nonblocking detail supplier; presentation tolerates a failed supplier
 * @param activate nonnull activation request
 */
public record BuddyNotice(BuddyNoticeId id, Kind kind, String title, State state,
                   Supplier<String> detail, Runnable activate) {

    /** What sort of thing this is, which is what decides how it ends. */
    public enum Kind {
        /** Begins and ends: a command, a file transfer. */
        TASK,
        /** Up until it is not: an SSH session, a tunnel. It never "completes". */
        CONNECTION
    }

    /** Task and connection states; kind compatibility is validated at construction. */
    public enum State {
        /** A task is in progress. */
        RUNNING,
        /** A task is waiting for user input. */
        NEEDS_INPUT,
        /** A connection is available. */
        UP,
        /** A connection is available with a problem needing attention. */
        DEGRADED,
        /** A connection is unavailable. */
        DOWN;

        /** A task is never UP; a connection is never RUNNING. */
        public boolean fits(Kind kind) {
            return kind == Kind.TASK ? this == RUNNING || this == NEEDS_INPUT : this == UP || this == DEGRADED || this == DOWN;
        }
    }

    /** Rejects null required inputs, blank titles and kind/state mismatches before acquiring anything. */
    public BuddyNotice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activate, "activate");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
        if (!state.fits(kind)) throw new IllegalArgumentException(state + " is not a state a " + kind + " can be in");
    }

    /** True for NEEDS_INPUT, DEGRADED and DOWN: the work is waiting on you or in trouble. */
    public boolean wantsAttention() {
        return state == State.NEEDS_INPUT || state == State.DEGRADED || state == State.DOWN;
    }

    /** Compares only stable notice identity; callback identity never participates. */
    public boolean sameAs(BuddyNoticeId otherId) { return id.equals(otherId); }
}
```

Replace the whole of `BuddyDeck.java` with:

```java
package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded live-work model: newest-first replacement, title changes, dismissal and clear. Notices have no expiry timer;
 * a producer dismisses its notice when the work ends, and the user may dismiss one or clear them all. A producer
 * supplies identity, state and callbacks, so the model knows nothing about panes, command thresholds or persistence.
 *
 * <p>EDT only, like the rest of the buddy's state.
 */
public final class BuddyDeck {
    /** Bounded so a long session cannot grow it without limit; the oldest fall off silently. */
    public static final int MAX_NOTICES = 50;

    private final List<BuddyNotice> notices = new ArrayList<>();
    /**
     * Bumped only by {@link #post}; distinguishes a new post from a title change. Retained as internal model
     * bookkeeping; presentation refreshes through the facade and keys motion by notice identity.
     */
    private int generation;
    private List<BuddyNotice> snapshot;

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    public void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        notices.removeIf(existing -> existing.sameAs(notice.id()));
        notices.addFirst(notice);
        generation++;
        snapshot = null;
        while (notices.size() > MAX_NOTICES) notices.removeLast();
    }

    /** A live producer changed its wording; this is neither a new notice nor news. */
    public boolean updateTitle(BuddyNoticeId id, String title) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < notices.size(); i++) {
            BuddyNotice notice = notices.get(i);
            if (!notice.sameAs(id)) continue;
            if (notice.title().equals(title)) return false;
            snapshot = null;
            notices.set(i, new BuddyNotice(notice.id(), notice.kind(), title, notice.state(), notice.detail(), notice.activate()));
            return true;
        }
        return false;
    }

    /** Removes one notice; true when there was one to remove. */
    public boolean dismiss(BuddyNoticeId id) {
        Objects.requireNonNull(id, "id");
        boolean removed = notices.removeIf(notice -> notice.sameAs(id));
        if (removed) snapshot = null;
        return removed;
    }

    public void clear() {
        notices.clear();
        snapshot = null;
    }

    /**
     * Cached immutable newest-first snapshot, which the column and the drawer both read. Later EDT mutations invalidate
     * the cache without changing an earlier snapshot.
     */
    public List<BuddyNotice> notices() {
        if (snapshot == null) snapshot = List.copyOf(notices);
        return snapshot;
    }

    /** Changes when, and only when, something is posted. */
    public int generation() { return generation; }

    public boolean isEmpty() { return notices.isEmpty(); }

    public int size() { return notices.size(); }
}
```

In `internal/model/package-info.java`, replace
`EDT bounded notice state, acknowledgement and orphaning. Owned and cleared by the companion.` with
`EDT bounded live-work notice state. Owned and cleared by the companion.`

In `BuddyCompanion.java`:

1. Replace
   `/** Posts or replaces a nonnull validated notice, promotes it to newest and resets acknowledgement. The oldest notice is dropped beyond 50. */`
   with
   `/** Posts or replaces a nonnull validated notice and promotes it to newest. The oldest notice is dropped beyond 50. */`.
2. Delete `acknowledge(BuddyNoticeId)` and both `orphan` methods, each with its JavaDoc line.
3. Replace `    /** Removes an existing nonnull ID and its retained callbacks. */` with
   `    /** Removes an existing nonnull ID and its retained callbacks; a producer calls it when the work ends. */`.

- [ ] **Step 4: Paint and click live cards only**

In `BuddyCard.java`:

1. Replace `        return notice.state() == BuddyNotice.State.RUNNING && !notice.orphaned();` with
   `        return notice.state() == BuddyNotice.State.RUNNING;`.
2. Replace `            if (hover > 0 && !notice.orphaned()) {` with `            if (hover > 0) {`.
3. Replace
   `                text.setColor(notice.orphaned() ? detailColor(options) : dark ? Color.WHITE : new Color(30, 30, 30));`
   with `                text.setColor(dark ? Color.WHITE : new Color(30, 30, 30));`.
4. Replace the whole `paintGlyph` method with:

```java
    /** A cross for a connection that is down; a dot for a task waiting on you or a connection in trouble. */
    private static void paintGlyph(BuddyOptions options, Graphics2D g2, BuddyNotice.State state, int x, int y) {
        boolean down = state == BuddyNotice.State.DOWN;
        Color configured = UIManager.getColor("Jasper.configErrorForeground");
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(down ? (configured != null ? configured : new Color(0xC45A53)) : detailColor(options));
            c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (down) {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            } else c.fillOval(x + 3, y + 3, GLYPH_SIZE - 6, GLYPH_SIZE - 6);
        } finally { c.dispose(); }
    }
```

In `BuddyColumnPanel.java`:

1. Replace every `deck.column()` with `deck.notices()`.
2. Replace `    boolean needsDetailUpdates() { return deck.notices().stream().anyMatch(BuddyNotice::live); }` with
   `    boolean needsDetailUpdates() { return !deck.isEmpty(); }`.
3. In `repaintFrame`, delete the line `                if (!column.get(i).live()) continue;`.
4. In `handleClick`, delete the line `        if (notice.orphaned()) return true;`.

In `BuddyDeckPanel.java`:

1. Replace `    boolean needsDetailUpdates() { return deck.notices().stream().anyMatch(BuddyNotice::live); }` with
   `    boolean needsDetailUpdates() { return !deck.isEmpty(); }`.
2. In `handleClick`, delete the two lines

```java
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
```

In `BuddyColumnWindow.java`, replace `        if (deck.column().isEmpty()) { frames.stop(); window.setVisible(false); return; }` with
`        if (deck.isEmpty()) { frames.stop(); window.setVisible(false); return; }`.

In `testFixtures/.../BuddyTestSupport.java`, delete the three lines

```java
    public List<BuddyNotice> column() { return edt(deck::column); }
    public boolean acknowledged(BuddyNoticeId id) { return edt(() -> deck.acknowledged(id)); }
    public void acknowledge(BuddyNoticeId id) { edt(() -> { companion.acknowledge(id); return null; }); }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-buddy:test :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.documentation.*' --tests 'dev.jasper.app.workspace.TerminalTitleIntegrationTest' verifyApplicationArchitecture :jasper-buddy:javadoc`
Expected: PASS.

Then confirm nothing names what is gone:

Run: `grep -rnE "\.(acknowledge|acknowledged|orphan)\(|BuddyNotice\.State\.(DONE|FAILED)|deck\.column\(\)|BuddyNotice::live|notice\.(live|orphaned)\(\)" jasper-buddy/src jasper-app/src --include='*.java'`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add jasper-buddy jasper-app/src/test/java/dev/jasper/app/application/BuddyIntegrationTest.java jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java docs/app-maintenance.md
git commit -m "feat(buddy): keep live cards only" -m "BuddyNotice loses DONE and FAILED, its activation is required, and acknowledgement and orphaning are gone: producers dismiss a card when its work ends, and the column and the drawer show the live cards." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Documentation and full verification

**Files:**
- Modify: `jasper-buddy/README.md`, `docs/buddy-architecture.md`, `docs/buddy-maintenance.md`
- Modify: `docs/configuration.md`, `docs/notifications.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`,
  `docs/app-maintenance.md`
- Modify: `docs/STATUS.md`, `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, this plan's banner

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only. `AppDocumentationTest` checks every local link and `#anchor` outside fenced code and the
  compiled examples. Dated records (`docs/design/`, `docs/*-verification.md`, `docs/documentation-audit.md`) keep what
  they recorded.

- [ ] **Step 1: The Buddy guides**

In `jasper-buddy/README.md`:

1. In the supported API list, replace `title, nonblocking detail supplier and nullable activation callback.` with
   `title, nonblocking detail supplier and activation callback.`
2. In the operations table, replace the start of the row

```
| `post`, `updateTitle`, `acknowledge`, `dismiss`, `orphan`, `clear` |
```

   with

```
| `post`, `updateTitle`, `dismiss`, `clear` |
```
3. Replace

```
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
```

   with

```
| Kind | Accepted states | Wants attention |
| --- | --- | --- |
| `TASK` | `RUNNING`, `NEEDS_INPUT` | `NEEDS_INPUT` |
| `CONNECTION` | `UP`, `DEGRADED`, `DOWN` | `DEGRADED`, `DOWN` |

Buddy shows live work only. A post promotes its ID to newest; updating a title does not.
The model holds at most 50 notices, dropping the oldest beyond that limit. The collapsed
column shows at most three of them; the drawer lists them all. A producer dismisses its
notice when the work ends, and the user may dismiss one from the drawer or clear them all.
Buddy keeps no outcomes: a host that wants to report how work ended does so elsewhere
(Jasper posts a notification). Activation is required. Cancel producer work first, then
dismiss. Hide and disable do not close producers.
```

In `docs/buddy-architecture.md`:

1. Replace

```
| `internal.model` | EDT notice ordering, acknowledgement and orphaning; depends on `notice`. |
```

   with

```
| `internal.model` | EDT live-work notice ordering and bounds; depends on `notice`. |
```
2. Replace

```
The deck retains up to 50 notices by source-qualified ID, newest first. Posts replace,
promote and reset acknowledgement; title changes preserve order and acknowledgement.
Snapshots are cached immutable lists, invalidated by mutations. The post generation
counter is model bookkeeping exposed only internally; it is not a public subscription API.

The column filters for `notice.live() || !acknowledged`. The drawer includes all retained
notices. Orphaning removes activation without changing the state; the final-detail
overload also replaces the supplier unconditionally. A producer chooses whether its
outcome needs freezing. Orphaning and acknowledgement are independent operations.
```

   with

```
The deck retains up to 50 notices by source-qualified ID, newest first. Posts replace and
promote; title changes preserve order. Snapshots are cached immutable lists, invalidated by
mutations. The post generation counter is model bookkeeping exposed only internally; it is
not a public subscription API.

Every retained notice is live work: the column shows the newest three and the drawer all of
them. A producer dismisses its notice when the work ends; Buddy keeps no outcomes. Jasper
posts how work ended as a notification instead ([guide](notifications.md)).
```

In `docs/buddy-maintenance.md`:

1. Replace

```
2. Keep the producer outside the library. Post on EDT; detail suppliers read a cheap
   snapshot and activation requests return quickly. Repost an ID for a new outcome;
   use `updateTitle` for wording that must not create new attention.
3. On producer close, invalidate identity before cancelling delayed callbacks. Freeze
   unfinished detail with `orphan(id, text)`; use `orphan(id)` for a completed outcome
   whose existing detail should remain. Acknowledge separately if it should leave the column.
```

   with

```
2. Keep the producer outside the library. Post on EDT; detail suppliers read a cheap
   snapshot and activation requests return quickly. Repost an ID for a new state; use
   `updateTitle` for wording that must not count as a new post.
3. When the work ends, and on producer close, invalidate identity before cancelling delayed
   callbacks, then `dismiss` the notice. Report how the work ended through the host's
   notifications, never through Buddy.
```

2. Replace

```
## Change notice ordering, capacity or acknowledgement

Edit [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java),
then its model tests. Preserve source-plus-key identity, newest-first replacement,
acknowledgement reset only on post, the 50-notice bound, immutable snapshots and
unconditional detail replacement by the final-detail orphan overload. Column eligibility
is live-or-unseen; orphaning preserves state and removes liveness. Layout's three-card
column limit is separate from retained model capacity. Exercise the panels too when a
model change affects what is displayed.
```

   with

```
## Change notice ordering or capacity

Edit [BuddyDeck](../jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyDeck.java),
then its model tests. Preserve source-plus-key identity, newest-first replacement, the
50-notice bound and immutable snapshots. Every retained notice is live work; the column
and the drawer read the same snapshot. Layout's three-card column limit is separate from
retained model capacity. Exercise the panels too when a model change affects what is
displayed.
```

   Then run `grep -rn "change-notice-ordering-capacity-or-acknowledgement" docs jasper-buddy jasper-app/README.md` and
   point any link it finds at `#change-notice-ordering-or-capacity`.

- [ ] **Step 2: Configuration, notifications and architecture guides**

In `docs/configuration.md`, replace everything from the line `### Finished-command notifications` up to (not
including) the line `### Automatic tab and notification titles` with:

```markdown
### Finished-command notifications

`notifications.long_command_seconds` sets how long a command must run before it is worth telling you about.
The default is 10 seconds; `0` turns the feature off entirely.

**This needs [shell integration](#shell-integration).** The duration is measured between the
command-start and command-end marks the integration scripts emit, so a shell that does not emit them
produces no notifications and no cards at all — Jasper cannot know how long anything took. There is
no fallback guess.

When a command that ran at least `long_command_seconds` finishes anywhere except the pane you were
typing in (a visible but unfocused split pane included), Jasper posts a notification in **Long-running
commands**: the command as its title, and "Finished in 2m 03s" or "Exited 1 · 2m 03s" below it. While
Jasper is focused it shows as a card in the pane's window; otherwise it waits, unread, in the
Notifications panel. Clicking it focuses the pane, and closing the pane expires it. See
[Notifications](notifications.md#long-running-commands).

While a command runs, a **capsule above the buddy's head** shows it, and he sits down and types. The
capsule appears **the moment you look away** — switching tab, switching window, or sending Jasper to
the background — because out of sight is the whole reason it exists. If you stay in the pane and
watch, it appears once the command passes `long_command_seconds` instead. It shows the app-supplied
terminal title (or the command when no title is supplied) and how long it has been running, and it
goes when the command ends.

At most three capsules are shown, newest nearest his head, with a count on the newest when there are
more. A new capsule slides down into place with a small spring settle; existing capsules move
smoothly to make room. Its size stays fixed, and status changes update the text in place. The
translucent material and text follow the light or dark app theme. The column flips below him when he
is too near the top of the screen, and the order inverts so the newest is still the one closest to him.

**Single-clicking him opens the drawer**: everything still going, newest first, with the × on a card
to dismiss one and **Clear all** to empty it. Double-clicking him still raises the terminal and
right-clicking still opens his menu.

In the packaged macOS `Jasper.app`, a command that finishes while Jasper is in the background also
sends a **system notification**, with Jasper's app icon and notification settings. The first eligible
notice asks for permission; allow Jasper in System Settings → Notifications. Other launches (Gradle
and IDE runs included) send none.

```

and, in the following section, replace

```
Once the command finishes, its bubble and native notification show the completed
command, including its arguments (for example, `sleep 12`), instead of the live terminal
title. Later prompt titles do not rewrite the result. Title reception works without shell
integration, but buddy notifications still need command-start/end marks.
```

with

```
Once the command finishes, its notification shows the completed command, including its
arguments (for example, `sleep 12`), instead of the live terminal title. Later prompt titles
do not rewrite it. Title reception works without shell integration, but long-command
notifications still need command-start/end marks.
```

In `docs/notifications.md`:

1. In the Groups table, after the `Configuration` row, add:

```
| Long-running commands | a command that ran at least `notifications.long_command_seconds` and finished anywhere but the pane you were typing in | card |
```

2. Before `## Plugin notifications`, add:

```markdown
## Long-running commands

A command that ran at least `notifications.long_command_seconds` (10 seconds by default) and finished anywhere but the
pane you were typing in is posted in Long-running commands: the command, and "Finished in 2m 03s" or "Exited 1 · 2m
03s". Its card shows in the pane's window, and clicking it focuses the pane; closing the pane expires it. The same
command finishing the same way within a minute counts on one entry, which then points at the pane it last came from.
While a command runs, Buddy (when it is on) shows it above his head; Buddy keeps only work that is still going. See
[configuration](configuration.md#finished-command-notifications).

```

In `docs/sdk-architecture.md`:

1. Replace

```
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which posts Buddy
  cards keyed by `BuddyNoticeId(pluginId, activityId)`.
```

   with

```
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which shows a running
  activity as a Buddy card keyed by `BuddyNoticeId(pluginId, activityId)` and dismisses it when
  the activity ends; `PluginHost` posts the outcome as a notification.
```

2. Replace

```
  posts a `Kind.CONNECTION` card on each new state (new news, even after it was acknowledged),
```

   becomes

```
  posts a `Kind.CONNECTION` card on each new state (new news),
```

3. Replace

```
  the click then invokes it in the last active window and acknowledges the card.
```

   with

```
  the click then invokes it in the last active window.
```

In `docs/app-architecture.md`, replace

```
IDs. CLOSED removes the active ID before timer cancellation/orphaning. A saved delayed
```

with

```
IDs. CLOSED removes the active ID before timer cancellation and card dismissal. A saved delayed
```

In `docs/app-maintenance.md`, in `## New Buddy notice producer`, replace the sentence
`At producer close, orphan and freeze only unfinished details; preserve completed outcomes.` with
`When the work ends, and at producer close, dismiss the card; report how the work ended as a notification (see Posting a notification below), never through Buddy.`

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL: every test, the four architecture gates and doclint. Known flakes not caused by this branch,
each documented in `docs/STATUS.md`: `TerminalAppIntegrationTest`'s "reflow" row-reset case, Remote's
`ListenerTest.acceptsOnLocalhostUntilClosed`, and Editor's `ExFilesTest.bufferSelectsATabByNumberOrName` when
`java.io.tmpdir` contains "zz". If one fails, rerun that test alone three times and the full check once more, and record
it.

Count the results:

```bash
python3 - <<'PY'
import pathlib, re
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    head = report.read_text(encoding="utf-8")[:2000]
    match = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
    if match:
        t, s, f, e = map(int, match.groups())
        tests += t; skipped += s; failures += f; errors += e
print(f"{tests} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Then run the source hygiene check from `AGENTS.md` (expected: no output) and `git diff --check 3c64ea5c` (expected: no
output).

- [ ] **Step 4: Record the outcome**

In `docs/STATUS.md`, add a new first entry under the `## Current state` heading, with the counts from Step 3:

```markdown
- **Notifications plan 4: long commands and Buddy, reviewed on branch `claude/notifications-4`; awaiting the user's GUI
  checks and merge decision** (plan `docs/superpowers/plans/2026-10-03-notifications-4-commands-buddy.md`,
  [guide](notifications.md#long-running-commands)): a command that ran past `notifications.long_command_seconds` and
  did not finish in the pane you were typing in is posted in "Long-running commands" (`jasper.commands`, CARD), in the
  pane's window, with a click that focuses the pane; a closed pane expires its notifications. Buddy keeps live work
  only: `BuddyNotice` lost `DONE` and `FAILED`, its activation is required, and `acknowledge` and both `orphan`
  overloads are gone; `CommandNotifier` and `ActivityNotifier` dismiss a card when its work ends. A coalesced repeat
  now carries the newest post's actions, default action and target, and its card moves to that window; an expiring
  action expires its entry before it runs. The macOS banner for long commands stays, background only, until plan 5.
  Spec interpretations: see the plan's list (1 to 12). `./gradlew check --continue`: COUNTS. No GUI launched. GUI checks
  for the user: a long command finishing in another tab shows a card in its window and a click focuses the pane; with
  Buddy on, the capsule shows while it runs and goes when it ends; the drawer lists only running work; in the packaged
  app with Jasper in the background, the macOS banner still arrives and the notification waits unread.
```

Replace `COUNTS` with the four numbers the count printed, as `**N tests, N failures, N errors, N skipped**`, and name
any failure with its cause. In the plan 3 entry, replace

```
- **Notifications plan 3: SDK notifications, reviewed on branch `claude/notifications-3`; awaiting the user's GUI
  checks and merge decision** (2026-10-03; plan `docs/superpowers/plans/2026-10-03-notifications-3-sdk.md`,
```

with

```
- **Notifications plan 3: SDK notifications, merged to local `main` (`3c64ea5c`); awaiting the user's GUI checks**
  (2026-10-03; plan `docs/superpowers/plans/2026-10-03-notifications-3-sdk.md`,
```

In `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, replace

```
(SDK)](../plans/2026-10-03-notifications-3-sdk.md) is implemented on branch `claude/notifications-3`.
```

with

```
(SDK)](../plans/2026-10-03-notifications-3-sdk.md) is merged to main (`3c64ea5c`). [Plan 4 (long commands and
Buddy)](../plans/2026-10-03-notifications-4-commands-buddy.md) is implemented on branch `claude/notifications-4`.
```

In this plan, replace the `**Status:** Not started. …` paragraph with one that says the plan is implemented on branch
`claude/notifications-4`, lists every deviation from the plan text made during execution (each with its task and
reason) and the final review's fixes, and gives the Step 3 counts.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --rerun`
Expected: PASS: every link, anchor and compiled example matches (`--rerun`, because documentation files are not task
inputs).

- [ ] **Step 5: Commit**

```bash
git add docs jasper-buddy/README.md
git commit -m "docs(notifications): long-running commands and a live-only Buddy" -m "The Buddy guides, configuration, the notifications guide and the architecture notes describe long commands as notifications and Buddy as live work only; STATUS, the spec and the plan record plan 4." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
