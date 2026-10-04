# Notifications Plan 5: OS Notifiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on branch `claude/notifications-5` (worktree `.worktrees/notifications-5`, cut from main
`a23d0f15`, where plans 1–4 are merged), executed with subagent-driven development (an implementer and a reviewer
per task); awaiting the user's GUI checks and merge decision. Deviations from the plan text, and per-task review
fixes:

- Task 1: three existing `NotificationCenterTest` cases that post card or sticky notifications in the background
  (`backgroundCardsOnlyEnterHistory`, now `backgroundCardsShowNoCardAndAskTheOperatingSystem`;
  `stickyCardsWaitForFocusOnce`; `aRepeatThatShowsItsCardNoLongerWaitsForFocus`) now also expect `SystemRequested`;
  the plan had missed them.
- Task 3 review: `JNI_OnLoad` returns `JNI_ERR` when it cannot bind `MacNotifications.clicked`, so a broken binding
  fails the library load (and the real-library test) instead of silently dropping clicks (`7d420d6c`).
- Task 5 review: the "a killed process is no click" tests now kill processes that have already printed `default`,
  and `SystemNotifier`'s worker stop waits up to 1 s so the backend's own close (which kills waiting `notify-send`
  processes) runs before Jasper exits (`077800ec`).
- Task 6 review: the Windows backend test asserts that every tray call made through the backend runs on the EDT
  (`673d6410`).
- Task 8: removing the heading "macOS notification identity" from `docs/app-maintenance.md` broke the two dated
  records that linked to its anchor (STATUS's 2026-09-27 entry and the finished-command notifications design); both
  links now point at `app-maintenance.md#system-notifications` and their text is unchanged. STATUS names the plan's
  interpretations as 1 to 18, since the plan lists 18 (the task text said 17).

Final review: pending. `./gradlew check --continue`: **8,495 tests, 0 failures, 0 errors, 6 skipped**. No GUI launched.

**Goal:** While Jasper is not focused, its notifications appear as the operating system's own (macOS banners, Windows
tray balloons, Linux `notify-send`), a click on one takes you to what it is about, and reading it in Jasper withdraws it.

**Architecture:**
- **Delivery.** `Delivery.Outcome` gains a `system` flag. Presence gains `OPENING` (Jasper starting, or a window not
  yet activated), and `NO_WINDOW` narrows to a resident Jasper whose windows are all closed. `NotificationCenter`
  emits a new `Change.SystemRequested` when the outcome asks for the operating system.
- **Incidents.** `NotificationRequest` gains `incident`. Every notification an incident announces names it, so the
  operating system shows only its title and a click opens the Errors window. Two notifications coalesce only when they
  announce the same incident, or none.
- **Platform.** `platform.SystemNotifier` sends to the operating system: one daemon worker, plain capped text, and
  backends as records of functions.
  - **macOS:** the UserNotifications bridge gains identifiers, withdrawal and a click callback through a cached
    `JavaVM`.
  - **Linux:** `LinuxNotifications` drives `notify-send` through an injected process starter.
  - **Windows:** `WindowsTray` replaces `ResidentTray` as the one owner of Jasper's tray icon, for the resident icon
    and for balloons.
- **Application.** `application.SystemNotices` listens to the center:
  - it sends what the operating system should show when `notifications.system` is on;
  - it withdraws what is read, expired, removed or cleared;
  - a click raises the notification's window, then runs its default action, opens the Errors window for an incident,
    or shows the Notifications panel.

  `CommandNotifier`'s interim OS sink and `NativeNotifier` are deleted.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing, AWT `SystemTray`, Objective-C with JNI and UserNotifications
(macOS 11+), `notify-send` (libnotify), JUnit 5, AssertJ, the Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 5 of the six in its section 14; sections 7, 9 and 11 are this plan's). Read it before starting.

## Global Constraints

**Build and process**
- Java 25 on the JetBrains Runtime; use `./gradlew`, never a system Gradle.
- Tests are headless. Never launch the GUI or the benchmark.
- Never commit on `main`; never push without the user. End every commit message with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- MVP: no compatibility shims, deprecations or migration notes (remove outright). No animations.

**Package boundaries**
- `dev.jasper.app.platform` depends on no other Jasper package. The OS notifier takes plain strings and UUIDs; the
  application adapts notifications to it.
- `dev.jasper.app.notifications` must not import `platform`.

**Text and privacy**
- Spec section 9: text is stripped of control characters and capped (**title 120, body 300 characters**). Incident
  notifications send their title only. Backends are injected functions, not interfaces. One daemon worker, closed at
  shutdown.
- Spec section 11:
  - the log gains no notification text;
  - OS notifications for incidents carry only the title;
  - notification text is plain text, never HTML.

**Platform backends (spec section 9)**
- **macOS:** the existing UserNotifications bridge, packaged `.app` only. The notification id is the request
  identifier. The delegate implements `didReceiveNotificationResponse` and calls back into Java (cached `JavaVM`,
  daemon-attached thread). A notification read in-app is withdrawn with `removeDeliveredNotificationsWithIdentifiers`.
  Signing, staging and verification are unchanged.
- **Windows:** `TrayIcon.displayMessage` with the matching `MessageType`.
  - In resident mode the resident icon is reused; otherwise an icon is added on the first background notification and
    removed when Jasper is focused again.
  - A click activates the most recent notification.
- **Linux:** `notify-send`, resolved on `PATH` once.
  - Arguments, never through a shell: `--app-name=Jasper`, `--icon=<PNG>`, `--`, title, body. The body escapes `&`,
    `<` and `>`.
  - If `notify-send --help` lists `--action`, add `--action=default=Open --wait`. Stdout `default` activates the
    notification.
  - At most 8 processes wait (the oldest is killed). They are killed when their notification is read or Jasper shuts
    down.
  - Without `--action` support, fire-and-forget. Without `notify-send`, no OS notifications, logged once.

**Delivery and clicks (spec section 7)**
- **Background:** `CARD` gets an OS notification plus history and unread, and no card later. `STICKY` gets an OS
  notification and waits.
- **`notifications.system = false`:** the OS cells add nothing.
- **A click** raises the target window, runs `activate` if present (else opens the Errors window for an incident, or
  the history panel), and marks the notification read.
- **Configuration (spec section 10):** `[notifications] system = true` is the default and applies live.

**Source hygiene**
- No raw control, private-use or unpaired-surrogate characters in source.
- Write such characters in tests as casts (`(char) 0x202E`), never as `\u` escapes typed into a tool call (they land
  as raw characters).
- Run the AGENTS.md Python check.

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **Presence has four states.**
   - `FOCUSED`: a Jasper window is active.
   - `BACKGROUND`: Jasper's windows are all inactive.
   - `NO_WINDOW`: no window is open after start-up has settled (a resident Jasper whose windows are all closed).
   - `OPENING`: Jasper is starting, or its window has not been activated since it opened.

   The operating system shows `CARD` and `STICKY` notifications in `BACKGROUND` and `NO_WINDOW`, never in `OPENING`
   (the card is about to show). A `CARD` in `NO_WINDOW` still waits for the next window, as spec section 7 says
   ("cards wait until one opens"), and the operating system shows it meanwhile.
2. **Start-up settles** when a terminal window is first activated, or when a resident Jasper started with no window
   finishes warming up. Before that, no window open is `OPENING`: config and plugin problems posted during start-up
   wait for the first window and send no banner.
3. **A repeat asks the operating system again under the same id.** This covers a coalesced post, or an incident repeat
   whose card may show. macOS replaces the banner; Windows shows a new balloon; Linux shows a new notification, within
   the cap of 8.
4. **One click handler per notifier**, `SystemNotifier.onClick(Consumer<UUID>)`, instead of the spec's per-`send`
   `onClick`. A per-send callback would keep every notification's closures reachable, the leak plan 4's review fixed.
   Backends report the notification's id; the handler hears it on the EDT.
5. **`send` takes a `Level`** (`INFO`, `WARNING`, `ERROR`), because spec section 9's Windows balloon needs the
   matching `MessageType`.
6. **Plain text.**
   - Control characters (C0 and C1), bidirectional controls (U+061C, U+200E, U+200F, U+202A–U+202E, U+2066–U+2069)
     and lone surrogates are dropped.
   - The title is one line; line breaks and tabs become spaces.
   - The body keeps its line breaks.
   - Both are stripped of surrounding whitespace and cut by code points with a final "…".
   - A title with nothing left reads "Jasper".
7. **Withdrawal follows read, expiry, removal and Clear All**, not only read: an expired or removed entry's banner leads
   nowhere.
   - macOS withdraws by id, and Clear All withdraws every Jasper banner.
   - Linux kills the waiting process.
   - Windows forgets the balloon, so a later icon click does not activate it.
8. **`NotificationRequest.incident`** (`Optional<UUID>`) is set wherever Show more… is added: Jasper and plugin
   incidents, launch failures, and a plugin's notification with a cause.
   - Two notifications coalesce only when they announce the same incident, or none. This fixes plan 2's deferred "two
     different incidents whose notifications read the same within 60 s share one notification", while repeated launch
     failures of one incident still count on one entry.
   - An expired entry keeps its incident.
9. **A plugin's notification with a cause counts as an incident's:** title only to the operating system, since its
   content may quote the cause.
10. **The Linux icon** is written to `<root>/notification-icon.png` (`AppDirs.notificationIcon()`), once per process at
    the first notification. `AppDirs` has no data directory; everything else lives under the root too. Without the
    icon, `--icon` is left out.
11. **Linux repeats:** a repeat does not kill the process that waits for the earlier post; a click on either leads to
    the same entry.
12. **The Windows icon.**
    - The background-notification icon has no menu.
    - When the resident icon appears, it replaces that icon and keeps the latest notification.
    - A double-click on the resident icon activates the latest unread notification when one waits, otherwise Open.
13. **`notifications.system`** is read from the loaded setting values (`ConfigService.State.values()`), not added to
    `ConfigSnapshot`, which would need another compatibility constructor. Its row goes on the existing Settings ›
    Notifications page; plan 6 builds that page out.
14. **A click with no window open** opens a new terminal window first. While Jasper quits, a click does nothing.
15. **Shutdown leaves delivered banners in place.** A macOS banner clicked after Jasper quit launches Jasper, which has
    nothing to route it to.
16. **`CommandNotifier`'s interim OS sink and `platform.NativeNotifier` are deleted.** Long commands reach the operating
    system through the center like every other notification.
17. **The background-finish route (plan 4's open decision).** This plan delivers option (c): the OS notification's
    click focuses the pane. A "Show pane" action, option (a), and a clickable history row, option (b), stay open for
    the user.
18. **"Jasper is focused again"** is a terminal window's activation. That is the signal that already flushes waiting
    cards (plan 1). It removes the Windows balloons' icon and forgets the most recent balloon. Activating Settings or a
    plugin window does neither until a terminal window is activated.

## Review Focus

1. **Hostile text reaching the operating system.** ANSI escapes, RLO, NUL, lone surrogates, 10,000 characters, and
   markup in a Linux body must arrive as plain, capped text, and never pass through a shell. Pinned by Task 4's
   `theTitleIsOneLineOfPlainText` and `longTextIsCutWithAnEllipsisAndNeverSplitsACharacter`, and Task 5's
   `argumentsCarryTheTextAsIsWithTheBodysMarkupEscaped`.
2. **A broken or slow `notify-send`** (a `--help` that hangs, a desktop without a notification server, a binary that
   cannot start). The worker must not stall beyond the 2 s probe, nothing may be thrown, and later notifications must
   still go. Pinned by Task 5's `aHelpThatNeverFinishesMeansNoActions` and Task 4's
   `aBackendFailureIsLoggedOnceAndLaterNotificationsStillGo`.
3. **A burst of background notifications** (a plugin failing repeatedly while you are away). Linux must keep at most 8
   waiting processes, Windows one icon, and macOS one banner per id. Pinned by Task 5's
   `atMostEightWaitAndTheOldestIsKilled`, Task 6's `aSecondBalloonReusesTheIcon`, and Task 1's
   `aRepeatInTheBackgroundAsksTheOperatingSystemAgainForTheSameEntry`.
4. **A click that arrives late:** after its notification was removed or cleared, after it expired, or while Jasper
   quits. It must not throw, must not run a dead default action, and must open nothing while quitting. Pinned by Task
   7's `aClickOnAGoneOrExpiredNotificationOnlyShowsTheHistory` and `whileJasperQuitsAClickOpensNothing`.
5. **`notifications.system` turned off while banners show.** Nothing more may be sent, and what already shows is still
   withdrawn when read. Pinned by Task 7's `turningSystemNotificationsOffStopsSendingButStillWithdraws`.

## File structure

**Create:**
- `platform/SystemNotifier.java`
- `platform/LinuxNotifications.java`
- `platform/WindowsTray.java` (replaces `ResidentTray.java`)
- `application/SystemNotices.java`
- Tests: `SystemNotifierTest`, `LinuxNotificationsTest`, `WindowsTrayTest`, `MacNotificationsTest` (Java) and
  `SystemNoticesTest`.

**Delete:**
- `platform/NativeNotifier.java` and `NativeNotifierTest.java` (Task 7)
- `platform/ResidentTray.java` and `ResidentTrayTest.java` (Task 6)

**Modify**

- **Model:** `notifications/Delivery`, `NotificationCenter`, `NotificationRequest`, `CommandNotifier`.
- **Views:** `notificationui/HistoryPanel`, `CardLayer` (a no-op arm for the new change).
- **Application:** `application/JasperApplication`, `NotificationsFeature`, `IncidentNotices`; `plugins/PluginRuntime`
  (the detached center's presence); `bootstrap/ApplicationBootstrap`.
- **Platform:** `platform/MacNotifications.java` and `AppDirs.java`.
- **Native:** `src/main/native/MacNotifications.m` and `src/test/native/MacNotificationsTest.m`.
- **Config:** `config/CoreSettings`, `settingsui/CorePages`, `config.example.toml`.
- **Tests:** `DeliveryTest`, `NotificationCenterTest`, `NotificationValuesTest`, `NotificationsFeatureTest`,
  `JasperApplicationPresenceTest`, `IncidentNoticesTest`, `CommandNotifierTest`, `TerminalTitleIntegrationTest`,
  `CoreSettingsTest` and `AppDirsTest`.
- **Docs:** `docs/notifications.md`, `docs/configuration.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`,
  `platform/package-info.java`, `docs/STATUS.md`, and the spec's and this plan's status banners.

All Java paths above are under `jasper-app/src/main/java/dev/jasper/app/` (tests under `jasper-app/src/test/java/...`)
unless they start with `docs/` or `config.example.toml`.

---

### Task 1: Delivery asks the operating system

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/Delivery.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java`, `CardLayer.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/DeliveryTest.java` (whole file),
  `NotificationCenterTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPresenceTest.java`
  (whole file), `NotificationsFeatureTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `Delivery.Presence { FOCUSED, BACKGROUND, NO_WINDOW, OPENING }`
  - `Delivery.Outcome(boolean record, boolean card, boolean pending, boolean system)`
  - `NotificationCenter.Change.SystemRequested(Notification notification)`
  - `JasperApplication.presence(boolean windowsOpen, boolean activatedSinceOpen, boolean jasperWindowActive, boolean settled)`

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/notifications/DeliveryTest.java` with:

```java
package dev.jasper.app.notifications;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {
    @ParameterizedTest(name = "{0} while {1}")
    @CsvSource({
        "OFF, FOCUSED, false, false, false, false", "OFF, BACKGROUND, false, false, false, false",
        "OFF, NO_WINDOW, false, false, false, false", "OFF, OPENING, false, false, false, false",
        "HISTORY, FOCUSED, true, false, false, false", "HISTORY, BACKGROUND, true, false, false, false",
        "HISTORY, NO_WINDOW, true, false, false, false", "HISTORY, OPENING, true, false, false, false",
        "CARD, FOCUSED, true, true, false, false", "CARD, BACKGROUND, true, false, false, true",
        "CARD, NO_WINDOW, true, false, true, true", "CARD, OPENING, true, false, true, false",
        "STICKY, FOCUSED, true, true, false, false", "STICKY, BACKGROUND, true, false, true, true",
        "STICKY, NO_WINDOW, true, false, true, true", "STICKY, OPENING, true, false, true, false"})
    void everyModeAndPresence(DisplayMode mode, Delivery.Presence presence, boolean record, boolean card, boolean pending,
                              boolean system) {
        assertThat(Delivery.route(mode, presence)).isEqualTo(new Delivery.Outcome(record, card, pending, system));
    }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPresenceTest.java` with:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery.Presence;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule behind {@code JasperApplication.presence()}, as a table: a window counts once it has been activated, and no
 * window at all is a resident Jasper with every window closed once start-up has settled.
 */
class JasperApplicationPresenceTest {
    @Test void beforeStartUpSettlesNoWindowIsStillOpening() {
        // Config and plugin problems are posted before the first window exists: their cards wait for it.
        assertThat(JasperApplication.presence(false, false, false, false)).isEqualTo(Presence.OPENING);
        assertThat(JasperApplication.presence(false, false, true, false)).isEqualTo(Presence.OPENING);
    }

    @Test void onceSettledNoWindowOpenIsNoWindowWhateverElseIsTrue() {
        assertThat(JasperApplication.presence(false, false, false, true)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, true, true)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, false, true)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void aWindowThatWasNeverActivatedIsStillOpening() {
        // A failure raised while a window opens arrives before the native activation event: its card waits.
        assertThat(JasperApplication.presence(true, false, false, true)).isEqualTo(Presence.OPENING);
        assertThat(JasperApplication.presence(true, false, true, false)).isEqualTo(Presence.OPENING);
    }

    @Test void anActivatedWindowIsFocusedOrInTheBackground() {
        assertThat(JasperApplication.presence(true, true, true, true)).isEqualTo(Presence.FOCUSED);
        assertThat(JasperApplication.presence(true, true, false, true)).isEqualTo(Presence.BACKGROUND);
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`, replace

```java
    @Test void cardsPostedWithNoWindowWaitForTheFirstFocus() {
        var center = center();
        presence = Delivery.Presence.NO_WINDOW;
```

with

```java
    @Test void cardsPostedWhileJasperOpensWaitForTheFirstFocus() {
        var center = center();
        presence = Delivery.Presence.OPENING;
```

and add these tests before the class's closing brace:

```java
    @Test void inTheBackgroundCardAndStickyGroupsAskTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(CARDS, "c"));
        center.post(request(STICKY, "s"));
        center.post(request(QUIET, "q"));
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "Upserted", "SystemRequested", "Upserted");
    }

    @Test void withNoWindowOpenTheOperatingSystemHearsAndTheCardStillWaits() {
        var center = center();
        presence = Delivery.Presence.NO_WINDOW;
        center.post(request(CARDS, "c"));
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested");
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "CardRequested");
    }

    @Test void whileJasperOpensNothingGoesToTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.OPENING;
        center.post(request(CARDS, "c"));
        center.post(request(STICKY, "s"));
        assertThat(kinds()).containsExactly("Upserted", "Upserted");
    }

    /** A listener that reads the entry as it is posted must not leave the operating system showing it. */
    @Test void anEntryReadBeforeItsRequestIsDeliveredIsNotSentToTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.onChange(change -> {
            if (change instanceof NotificationCenter.Change.Upserted upserted && !upserted.notification().read())
                center.markRead(upserted.notification().id());
        });
        center.post(request(CARDS, "c"));
        assertThat(kinds()).containsExactly("Upserted", "Upserted");
    }

    @Test void aRepeatInTheBackgroundAsksTheOperatingSystemAgainForTheSameEntry() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID first = center.post(request(CARDS, "c")).orElseThrow();
        assertThat(center.post(request(CARDS, "c"))).contains(first);
        assertThat(changes).filteredOn(NotificationCenter.Change.SystemRequested.class::isInstance)
            .extracting(change -> ((NotificationCenter.Change.SystemRequested) change).notification().id())
            .containsExactly(first, first);
    }
```

In `jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java`, replace

```java
    @Test void notificationsFromBeforeTheFirstWindowShowWhenItIsFocused() {
        presence = Delivery.Presence.NO_WINDOW;
```

with

```java
    @Test void notificationsFromBeforeTheFirstWindowShowWhenItIsFocused() {
        presence = Delivery.Presence.OPENING;
```

and replace

```java
        // The window is attached but the native activation event has not arrived yet: the application reports NO_WINDOW.
        presence = Delivery.Presence.NO_WINDOW;
```

with

```java
        // The window is attached but the native activation event has not arrived yet: the application reports OPENING.
        presence = Delivery.Presence.OPENING;
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.application.*'`
Expected: FAIL to compile. `Delivery.Presence.OPENING`, the four-argument `Delivery.Outcome` and the four-argument
`JasperApplication.presence` do not exist yet.

- [ ] **Step 3: Implement**

Replace `jasper-app/src/main/java/dev/jasper/app/notifications/Delivery.java` with:

```java
package dev.jasper.app.notifications;

/**
 * Where a posted notification goes (spec section 7). Pure, so the rule is testable without a window. While Jasper is
 * in the background an auto-hiding card would be stale on return, so it only enters history; a sticky one waits; and
 * the operating system shows either, which is how a person away from Jasper hears of it. With no window open the card
 * waits for the next one and the operating system shows it meanwhile. While Jasper starts or a window opens the card
 * waits for that window and the operating system is left alone, since the card is about to show.
 */
public final class Delivery {
    private Delivery() {}

    /**
     * Whether a Jasper window is focused; Jasper is behind another application; Jasper runs with every window closed
     * (a resident Jasper); or Jasper is starting, or opening a window that has not been activated yet.
     */
    public enum Presence { FOCUSED, BACKGROUND, NO_WINDOW, OPENING }

    /**
     * @param record whether it enters the history
     * @param card whether a card shows now
     * @param pending whether a card waits until Jasper is next focused
     * @param system whether the operating system shows it now
     */
    public record Outcome(boolean record, boolean card, boolean pending, boolean system) {
        static final Outcome DROP = new Outcome(false, false, false, false);
        static final Outcome HISTORY = new Outcome(true, false, false, false);
        static final Outcome CARD = new Outcome(true, true, false, false);
        static final Outcome PENDING = new Outcome(true, false, true, false);
        static final Outcome HISTORY_AND_SYSTEM = new Outcome(true, false, false, true);
        static final Outcome PENDING_AND_SYSTEM = new Outcome(true, false, true, true);
    }

    public static Outcome route(DisplayMode mode, Presence presence) {
        return switch (mode) {
            case OFF -> Outcome.DROP;
            case HISTORY -> Outcome.HISTORY;
            case CARD -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND -> Outcome.HISTORY_AND_SYSTEM;
                case NO_WINDOW -> Outcome.PENDING_AND_SYSTEM;
                case OPENING -> Outcome.PENDING;
            };
            case STICKY -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND, NO_WINDOW -> Outcome.PENDING_AND_SYSTEM;
                case OPENING -> Outcome.PENDING;
            };
        };
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`:

1. In the class comment, replace

```java
 * change a listener makes is delivered after the one it is reacting to, and a card is never requested for an entry
 * that is read, expired or gone by the time the request is delivered.
```

with

```java
 * change a listener makes is delivered after the one it is reacting to, and neither a card nor the operating system is
 * asked to show an entry that is read, expired or gone by the time the request is delivered.
```

2. Replace

```java
        record CardRequested(Notification notification) implements Change {}
```

with

```java
        record CardRequested(Notification notification) implements Change {}
        /**
         * The operating system should show this entry now, Jasper not being focused; for an entry it already shows, the
         * newer post replaces it.
         */
        record SystemRequested(Notification notification) implements Change {}
```

3. Replace the whole `announce` method:

```java
    /** Emits the entry's change, with its card when the outcome shows one now, or remembers that the card waits. */
    private void announce(Notification entry, Delivery.Outcome outcome) {
        if (outcome.card()) {
            pending.remove(entry.id()); // a repeat may still wait from an earlier background post; its card shows now
            emit(new Change.Upserted(entry), new Change.CardRequested(entry));
        } else {
            if (outcome.pending()) pending.add(entry.id());
            emit(new Change.Upserted(entry));
        }
    }
```

with

```java
    /**
     * Emits the entry's change, with its card when the outcome shows one now, or remembers that the card waits; then
     * asks the operating system when the outcome says so.
     */
    private void announce(Notification entry, Delivery.Outcome outcome) {
        var changes = new ArrayList<Change>(3);
        changes.add(new Change.Upserted(entry));
        if (outcome.card()) {
            pending.remove(entry.id()); // a repeat may still wait from an earlier background post; its card shows now
            changes.add(new Change.CardRequested(entry));
        } else if (outcome.pending()) {
            pending.add(entry.id());
        }
        if (outcome.system()) changes.add(new Change.SystemRequested(entry));
        emit(changes.toArray(Change[]::new));
    }
```

4. In `emit`, replace

```java
                if (next instanceof Change.CardRequested requested) {
                    Notification latest = history.get(requested.notification().id());
                    if (latest == null || latest.read() || latest.expired()) continue;
                    next = new Change.CardRequested(latest);
                }
```

with

```java
                if (next instanceof Change.CardRequested requested) {
                    Notification latest = live(requested.notification());
                    if (latest == null) continue;
                    next = new Change.CardRequested(latest);
                } else if (next instanceof Change.SystemRequested requested) {
                    Notification latest = live(requested.notification());
                    if (latest == null) continue;
                    next = new Change.SystemRequested(latest);
                }
```

5. Add this method directly after `emit`:

```java
    /** The entry as it is now, or null when it was read, expired or removed since it was asked for. */
    private Notification live(Notification asked) {
        Notification latest = history.get(asked.id());
        return latest == null || latest.read() || latest.expired() ? null : latest;
    }
```

In `jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java` and in
`jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java`, replace (once in each file)

```java
            case NotificationCenter.Change.CardRequested requested -> { }
```

with

```java
            case NotificationCenter.Change.CardRequested requested -> { }
            case NotificationCenter.Change.SystemRequested requested -> { }
```

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`:

1. Replace

```java
    /** Whether an open terminal window has been activated since the first of them opened; see {@link #presence}. */
    private boolean activatedSinceOpen;
```

with

```java
    /** Whether an open terminal window has been activated since the first of them opened; see {@link #presence}. */
    private boolean activatedSinceOpen;
    /** Whether start-up has settled: a terminal window was activated, or a resident Jasper warmed up with none. */
    private boolean settled;
```

2. Replace

```java
    /** Where Jasper stands for notifications: focused, in the background, or with no window open. */
    private Delivery.Presence presence() {
        return presence(!windows.isEmpty(), activatedSinceOpen,
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null);
    }

    /**
     * The rule behind {@link #presence()}. A window counts only once it has been activated: a failure raised while the
     * first window opens is queued ahead of the native activation event, and judging it "background" would keep its
     * card out of sight for good. Until then the card waits, as it does with no window at all, and shows on activation.
     */
    static Delivery.Presence presence(boolean windowsOpen, boolean activatedSinceOpen, boolean jasperWindowActive) {
        if (!windowsOpen || !activatedSinceOpen) return Delivery.Presence.NO_WINDOW;
        return jasperWindowActive ? Delivery.Presence.FOCUSED : Delivery.Presence.BACKGROUND;
    }
```

with

```java
    /** Where Jasper stands for notifications: focused, in the background, windowless, or still opening. */
    private Delivery.Presence presence() {
        return presence(!windows.isEmpty(), activatedSinceOpen,
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null, settled);
    }

    /**
     * The rule behind {@link #presence()}. A window counts only once it has been activated: a failure raised while the
     * first window opens is queued ahead of the native activation event, and judging it "background" would keep its
     * card out of sight for good. Until then the card waits, and so does one posted while Jasper starts. Once start-up
     * has settled, no window open is a resident Jasper with every window closed: its cards wait for the next window and
     * the operating system shows them meanwhile.
     */
    static Delivery.Presence presence(boolean windowsOpen, boolean activatedSinceOpen, boolean jasperWindowActive,
                                      boolean settled) {
        if (!windowsOpen) return settled ? Delivery.Presence.NO_WINDOW : Delivery.Presence.OPENING;
        if (!activatedSinceOpen) return Delivery.Presence.OPENING;
        return jasperWindowActive ? Delivery.Presence.FOCUSED : Delivery.Presence.BACKGROUND;
    }
```

3. In `warmUp()`, replace

```java
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
    }
```

with

```java
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
        // A resident Jasper with no window has finished starting: its notifications reach the operating system now.
        settled = true;
    }
```

4. In `windowActivated`, replace

```java
        activatedSinceOpen = true;
```

with

```java
        activatedSinceOpen = true;
        settled = true;
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`, in `NotificationHost.detached()`, replace

```java
() -> Delivery.Presence.NO_WINDOW);
```

with

```java
() -> Delivery.Presence.OPENING);
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS (`DeliveryTest` 16/16, `JasperApplicationPresenceTest` 4/4, the five new `NotificationCenterTest`
tests among the rest).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/Delivery.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/test/java/dev/jasper/app/notifications/DeliveryTest.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPresenceTest.java jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java
git commit -m "feat(notifications): delivery asks the operating system while Jasper is away" -m "In the background and with every window closed, card and sticky notifications are also requested from the operating system (Change.SystemRequested); while Jasper starts or a window opens they only wait for it, through the new OPENING presence." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: An incident's notification names it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java`, `NotificationCenterTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `NotificationRequest.incident()` (`Optional<UUID>`, the record's last component)
  - `NotificationRequest withIncident(UUID incident)`
  - `sameText` is true only when both requests announce the same incident or none.

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java`, add before the class's closing
brace:

```java
    @Test void twoIncidentsNeverReadTheSameHoweverAlikeTheirText() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var plain = NotificationRequest.of("g", Severity.ERROR, "Unexpected error in Jasper").withContent("boom");
        assertThat(plain.withIncident(first).sameText(plain.withIncident(first))).isTrue();
        assertThat(plain.withIncident(first).sameText(plain.withIncident(second))).isFalse();
        assertThat(plain.withIncident(first).sameText(plain)).isFalse();
        assertThat(plain.sameText(plain)).isTrue();
    }

    @Test void anExpiredEntryStillNamesItsIncident() {
        UUID incident = UUID.randomUUID();
        var request = NotificationRequest.of("g", Severity.ERROR, "t").withIncident(incident).withActivate(() -> { });
        var entry = new Notification(UUID.randomUUID(), request, Instant.EPOCH, 1, false, false).withExpired();
        assertThat(entry.request().incident()).contains(incident);
        assertThat(entry.request().activate()).isEmpty();
    }
```

In `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`, add before the class's closing
brace:

```java
    /** Each incident has its own notification, so Show more… opens the right one (plan 2's deferred finding). */
    @Test void differentIncidentsAreNeverOneEntryHoweverAlikeTheyRead() {
        var center = center();
        UUID first = center.post(request(CARDS, "boom").withIncident(UUID.randomUUID())).orElseThrow();
        UUID second = center.post(request(CARDS, "boom").withIncident(UUID.randomUUID())).orElseThrow();
        assertThat(second).isNotEqualTo(first);
        UUID incident = UUID.randomUUID();
        UUID third = center.post(request(CARDS, "again").withIncident(incident)).orElseThrow();
        assertThat(center.post(request(CARDS, "again").withIncident(incident))).contains(third);
    }
```

In `jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java`, add before the class's closing
brace:

```java
    @Test void everyNotificationAnIncidentAnnouncesNamesThatIncident() {
        var notices = notices();
        declareRemote();
        notices.jasperFailure("thread worker", new IllegalStateException("broken"));
        notices.pluginFailure("dev.jasper.remote", "panel remote.sftp", new UnsupportedOperationException("no route"));
        notices.launchFailure(UUID.randomUUID(), "in /tmp", new IllegalArgumentException("no shell"));
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "Clipboard busy"),
            new ArithmeticException("busy"), id -> { });
        assertThat(center.history()).hasSize(4).allSatisfy(entry -> assertThat(entry.request().incident()).isPresent());
        assertThat(center.history()).extracting(entry -> entry.request().incident().orElseThrow())
            .containsExactlyInAnyOrderElementsOf(incidents.list().stream().map(Incident::id).toList());
    }

    /** Two incidents are two notifications, however alike they read: each Show more… opens its own. */
    @Test void differentIncidentsThatReadTheSameAreNeverOneNotification() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "in /tmp", new IllegalStateException("no shell"));
        notices.launchFailure(window, "in /tmp", new IllegalArgumentException("no shell"));
        assertThat(incidents.list()).hasSize(2);
        assertThat(center.history()).hasSize(2).allSatisfy(entry -> assertThat(entry.count()).isEqualTo(1));
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.application.IncidentNoticesTest'`
Expected: FAIL to compile: `withIncident` and `incident()` do not exist.

- [ ] **Step 3: Implement**

Replace `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java` with:

```java
package dev.jasper.app.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.Icon;

/**
 * What a producer posts. Text is plain: the title is one line cut to {@value #MAX_TITLE} characters, the content keeps
 * its line breaks and is cut to {@value #MAX_CONTENT}. {@code source} is {@value #JASPER} or a plugin id; {@code target}
 * names the window whose card it is, empty for the window the user used last. {@code incident} names the incident it
 * announces, if any: the operating system then shows only its title, since an incident's details can name paths and
 * hosts and banners show on a locked screen (spec section 11), and a click on that banner opens the Errors window at it.
 */
public record NotificationRequest(String group, Severity severity, String title, String content, Optional<Icon> icon,
                                  List<NotificationAction> actions, Optional<Runnable> activate, String source,
                                  Optional<UUID> target, Optional<UUID> incident) {
    public static final int MAX_TITLE = 200;
    public static final int MAX_CONTENT = 4_000;
    public static final int MAX_ACTIONS = 8;
    public static final String JASPER = "jasper";

    public NotificationRequest {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(severity, "severity");
        title = NotificationText.line(Objects.requireNonNull(title, "title"), MAX_TITLE);
        if (!NotificationText.visible(title)) throw new IllegalArgumentException("A notification needs a title");
        content = NotificationText.block(Objects.requireNonNull(content, "content"), MAX_CONTENT);
        Objects.requireNonNull(icon, "icon");
        actions = List.copyOf(actions);
        if (actions.size() > MAX_ACTIONS) throw new IllegalArgumentException("A notification has at most " + MAX_ACTIONS + " actions");
        Objects.requireNonNull(activate, "activate");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("A notification needs a source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(incident, "incident");
    }

    /**
     * The same text, action labels, target and incident without the poster's callbacks: what an expired entry keeps,
     * since those callbacks capture the poster (a closed pane and its window, a stopped plugin) and can never run again.
     */
    NotificationRequest released() {
        List<NotificationAction> inert = actions.stream()
            .map(action -> new NotificationAction(action.label(), () -> {}, action.expires())).toList();
        return new NotificationRequest(group, severity, title, content, icon, inert, Optional.empty(), source, target, incident);
    }

    /** A notification of Jasper's own with only a title. */
    public static NotificationRequest of(String group, Severity severity, String title) {
        return new NotificationRequest(group, severity, title, "", Optional.empty(), List.of(), Optional.empty(), JASPER,
            Optional.empty(), Optional.empty());
    }

    public NotificationRequest withContent(String value) {
        return new NotificationRequest(group, severity, title, value, icon, actions, activate, source, target, incident);
    }

    public NotificationRequest withIcon(Icon value) {
        return new NotificationRequest(group, severity, title, content, Optional.of(value), actions, activate, source, target, incident);
    }

    public NotificationRequest withAction(NotificationAction action) {
        var more = new ArrayList<>(actions);
        more.add(Objects.requireNonNull(action, "action"));
        return new NotificationRequest(group, severity, title, content, icon, more, activate, source, target, incident);
    }

    /** The default action, run when the user clicks the card's body or its OS notification. */
    public NotificationRequest withActivate(Runnable value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, Optional.of(value), source, target, incident);
    }

    public NotificationRequest withSource(String value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, value, target, incident);
    }

    public NotificationRequest withTarget(UUID window) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, source, Optional.of(window), incident);
    }

    /** The incident this notification announces (spec section 5). */
    public NotificationRequest withIncident(UUID value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, source, target,
            Optional.of(Objects.requireNonNull(value, "incident")));
    }

    /**
     * Whether {@code other} reads the same (same group, severity, title and content) and announces the same incident,
     * or none: two incidents are never one notification, however alike they read.
     */
    boolean sameText(NotificationRequest other) {
        return group.equals(other.group) && severity == other.severity && title.equals(other.title)
            && content.equals(other.content) && incident.equals(other.incident);
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`:

1. In `launchFailure`, replace

```java
                incident -> launchRequest(window, text).withAction(showMore(incident)), UNHEARD)) return;
```

with

```java
                incident -> linked(launchRequest(window, text), incident), UNHEARD)) return;
```

2. In `pluginNotice`, replace

```java
        if (capture(pluginId, request.title(), unwrapped(cause), false, incident -> request.withAction(showMore(incident)), posted))
```

with

```java
        if (capture(pluginId, request.title(), unwrapped(cause), false, incident -> linked(request, incident), posted))
```

3. Replace the whole `announcement` method:

```java
    /** An internal error, or a plugin problem with Disable Plugin; Show more… comes last (spec section 3). */
    private NotificationRequest announcement(Incident incident) {
        if (!incident.fromPlugin())
            return NotificationRequest.of(ERRORS.id(), Severity.ERROR, "Unexpected error in Jasper")
                .withContent(incident.summary()).withAction(showMore(incident));
        String plugin = incident.source();
        return NotificationRequest.of(PLUGINS.id(), Severity.ERROR, pluginTitle(nameOf(plugin), incident.operation()))
            .withContent(incident.summary())
            .withAction(new NotificationAction(DISABLE_PLUGIN, () -> disablePlugin(plugin), false))
            .withAction(showMore(incident));
    }
```

with

```java
    /** An internal error, or a plugin problem with Disable Plugin; Show more… comes last (spec section 3). */
    private NotificationRequest announcement(Incident incident) {
        if (!incident.fromPlugin())
            return linked(NotificationRequest.of(ERRORS.id(), Severity.ERROR, "Unexpected error in Jasper")
                .withContent(incident.summary()), incident);
        String plugin = incident.source();
        return linked(NotificationRequest.of(PLUGINS.id(), Severity.ERROR, pluginTitle(nameOf(plugin), incident.operation()))
            .withContent(incident.summary())
            .withAction(new NotificationAction(DISABLE_PLUGIN, () -> disablePlugin(plugin), false)), incident);
    }

    /**
     * The request with Show more… last, naming its incident: the operating system then shows only the title, and two
     * incidents never share a notification.
     */
    private NotificationRequest linked(NotificationRequest request, Incident incident) {
        return request.withAction(showMore(incident)).withIncident(incident.id());
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.notificationui.*'`
Expected: PASS, including `IncidentNoticesTest.identicalLaunchFailuresInOneWindowShareAnEntryThatExpiresWithTheIncident`
(one incident still counts on one entry).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java
git commit -m "feat(notifications): an incident's notification names its incident" -m "Every notification an incident announces carries its id, so the operating system can show its title only and a click can open the Errors window at it; two notifications coalesce only when they announce the same incident or none." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: macOS banners carry their id, withdraw, and report clicks

**Files:**
- Modify: `jasper-app/src/main/native/MacNotifications.m` (whole file)
- Modify: `jasper-app/src/test/native/MacNotificationsTest.m` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacNotifications.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/NativeNotifier.java` (one constructor; the class goes in Task 7)
- Create: `jasper-app/src/test/java/dev/jasper/app/platform/MacNotificationsTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/platform/NativeNotifierTest.java` (whole file; the bridge tests move)

**Interfaces:**
- Consumes: nothing.
- Produces (package-private, `dev.jasper.app.platform.MacNotifications`):
  - `static void send(String id, String title, String detail)`
  - `static void withdraw(String id)`
  - `static void withdrawAll()`
  - `static void onClick(Consumer<String> sink)`
  - `static void clicked(String id)` (the native callback)
  - `static Path libraryPath(String appPath)` (unchanged)
- Produces (natives):
  - `post(String id, String title, String detail)`, `remove(String id)` and `removeAll()`.
  - `JNI_OnLoad` caches the `JavaVM` and `MacNotifications.clicked(String)`.

- [ ] **Step 1: Write the failing native test**

Replace `jasper-app/src/test/native/MacNotificationsTest.m` with:

```objc
// Headless: a fake center exercises production content, identifiers, withdrawal and the click
// callback without requesting permission, registering an application, delivering a banner, or
// creating a window.
#import "../../main/native/MacNotifications.m"
#include <assert.h>

@interface TestNotificationCenter : NSObject
@property NSError *deliveryError;
@property UNAuthorizationOptions options;
@property NSMutableArray<UNNotificationRequest *> *requests;
@property NSMutableArray<NSString *> *withdrawnPending;
@property NSMutableArray<NSString *> *withdrawnDelivered;
@property NSUInteger clearedPending;
@property NSUInteger clearedDelivered;
@property (copy) void (^authorization)(BOOL, NSError *);
@end
@implementation TestNotificationCenter
- (instancetype)init {
    self = [super init];
    if (self) {
        _requests = [NSMutableArray new];
        _withdrawnPending = [NSMutableArray new];
        _withdrawnDelivered = [NSMutableArray new];
    }
    return self;
}
- (void)requestAuthorizationWithOptions:(UNAuthorizationOptions)options
                     completionHandler:(void (^)(BOOL, NSError *))completion {
    self.options = options;
    self.authorization = completion;
}
- (void)addNotificationRequest:(UNNotificationRequest *)request
        withCompletionHandler:(void (^)(NSError *))completion {
    [self.requests addObject:request];
    completion(self.deliveryError);
}
- (void)removePendingNotificationRequestsWithIdentifiers:(NSArray<NSString *> *)identifiers {
    [self.withdrawnPending addObjectsFromArray:identifiers];
}
- (void)removeDeliveredNotificationsWithIdentifiers:(NSArray<NSString *> *)identifiers {
    [self.withdrawnDelivered addObjectsFromArray:identifiers];
}
- (void)removeAllPendingNotificationRequests { self.clearedPending++; }
- (void)removeAllDeliveredNotifications { self.clearedDelivered++; }
@end

// A click as macOS describes it: which action, on which notification.
@interface TestNotification : NSObject
@property UNNotificationRequest *request;
@end
@implementation TestNotification
@end
@interface TestResponse : NSObject
@property NSString *actionIdentifier;
@property TestNotification *notification;
@end
@implementation TestResponse
@end

static NSMutableArray<NSString *> *clicks;
static void RecordClick(NSString *identifier) { [clicks addObject:identifier]; }

static TestResponse *Response(NSString *action, NSString *identifier) {
    TestNotification *notification = [TestNotification new];
    notification.request = [UNNotificationRequest requestWithIdentifier:identifier
        content:[UNMutableNotificationContent new] trigger:nil];
    TestResponse *response = [TestResponse new];
    response.actionIdentifier = action;
    response.notification = notification;
    return response;
}

static const jchar javaCharacters[] = {'b', 0, 0xD83D, 0xDE80};
static BOOL released;
static const jchar *GetCharacters(JNIEnv *env, jstring string, jboolean *copy) {
    return javaCharacters;
}
static jsize GetLength(JNIEnv *env, jstring string) { return 4; }
static void ReleaseCharacters(JNIEnv *env, jstring string, const jchar *characters) {
    assert(characters == javaCharacters);
    released = YES;
}

int main(void) {
    @autoreleasepool {
        struct JNINativeInterface_ functions = {0};
        functions.GetStringChars = GetCharacters;
        functions.GetStringLength = GetLength;
        functions.ReleaseStringChars = ReleaseCharacters;
        JNIEnv env = &functions;
        NSString *converted = JasperString(&env, (jstring)(uintptr_t)1);
        assert([converted isEqualToString:[NSString stringWithCharacters:javaCharacters length:4]]);
        assert(released);
        assert([JasperString(&env, NULL) isEqualToString:@""]);

        TestNotificationCenter *center = [TestNotificationCenter new];
        unichar characters[] = {'b', 'u', 'i', 'l', 'd', '\n', '"', 0, 0xD83D, 0xDE80};
        NSString *title = [NSString stringWithCharacters:characters length:10];
        NSString *body = @"Finished in 1m; $() \\";
        NSString *identifier = @"0f8fad5b-d9cb-469f-a165-70867728950e";
        JasperPost((UNUserNotificationCenter *)center, identifier, title, body);
        assert(center.requests.count == 0); // Delivery waits for authorization.
        assert(center.options == UNAuthorizationOptionAlert);
        center.authorization(YES, nil);
        assert(center.requests.count == 1);
        UNNotificationRequest *first = center.requests.firstObject;
        // The notification's id, so posting it again replaces its banner and withdrawing it removes it.
        assert([first.identifier isEqualToString:identifier]);
        assert([first.content.title isEqualToString:title]);
        assert([first.content.body isEqualToString:body]);
        assert(first.trigger == nil);
        assert(first.content.sound == nil);
        JasperPost((UNUserNotificationCenter *)center, identifier, title, body);
        center.authorization(YES, nil);
        assert(center.requests.count == 2);
        assert([center.requests.lastObject.identifier isEqualToString:identifier]);
        JasperPost((UNUserNotificationCenter *)center, @"denied", title, body);
        center.authorization(NO, nil);
        assert(center.requests.count == 2);
        NSError *error = [NSError errorWithDomain:@"JasperTest" code:1 userInfo:nil];
        JasperPost((UNUserNotificationCenter *)center, @"failed", title, body);
        center.authorization(YES, error);
        assert(center.requests.count == 2);
        center.deliveryError = error;
        JasperPost((UNUserNotificationCenter *)center, @"undelivered", title, body);
        center.authorization(YES, nil); // Delivery errors remain nonfatal.
        assert(center.requests.count == 3);

        JasperWithdraw((UNUserNotificationCenter *)center, identifier);
        assert([center.withdrawnPending isEqualToArray:@[identifier]]);
        assert([center.withdrawnDelivered isEqualToArray:@[identifier]]);
        JasperWithdrawAll((UNUserNotificationCenter *)center);
        assert(center.clearedPending == 1 && center.clearedDelivered == 1);

        __block BOOL presented = NO;
        JasperNotificationDelegate *delegate = [JasperNotificationDelegate new];
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            willPresentNotification:(UNNotification *)[NSObject new]
            withCompletionHandler:^(UNNotificationPresentationOptions options) {
                assert(options == (UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList));
                presented = YES;
            }];
        assert(presented);

        clicks = [NSMutableArray new];
        JasperClickSink = RecordClick;
        __block int completed = 0;
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            didReceiveNotificationResponse:(UNNotificationResponse *)Response(UNNotificationDefaultActionIdentifier, identifier)
            withCompletionHandler:^{ completed++; }];
        assert([clicks isEqualToArray:@[identifier]]);
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            didReceiveNotificationResponse:(UNNotificationResponse *)Response(UNNotificationDismissActionIdentifier, identifier)
            withCompletionHandler:^{ completed++; }];
        assert(clicks.count == 1); // Dismissing a banner is not asking to see it.
        assert(completed == 2);    // macOS waits for the handler either way.

        // With no JVM loaded the bridge drops a click instead of calling into nothing.
        JasperDeliverClick(identifier);

        // A plain headless process must return before touching JNI or notification services.
        Java_dev_jasper_app_platform_MacNotifications_post(NULL, NULL, NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_remove(NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_removeAll(NULL, NULL);
        puts("macOS notification bridge: all headless checks passed");
    }
    return 0;
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:testMacNotifications` (macOS only; on other hosts the task does not exist and this step is
skipped)
Expected: FAIL to compile. `JasperPost` takes three arguments, and `JasperWithdraw`, `JasperWithdrawAll`,
`JasperClickSink`, `JasperDeliverClick` and the `remove`/`removeAll` natives are undefined.

- [ ] **Step 3: Implement the bridge**

Replace `jasper-app/src/main/native/MacNotifications.m` with:

```objc
#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>
#include <stdlib.h>

// The JVM and the Java method a click calls, cached when the library loads.
static JavaVM *JasperVM;
static jclass JasperBridge;
static jmethodID JasperClicked;

// Hands a clicked notification's identifier to Java, on whatever thread macOS calls the delegate.
static void JasperDeliverClick(NSString *identifier) {
    if (!JasperVM || !JasperBridge || !JasperClicked || !identifier) return;
    JNIEnv *env = NULL;
    jint state = (*JasperVM)->GetEnv(JasperVM, (void **)&env, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        // A thread macOS owns: attached as a daemon, it never holds up the JVM's exit.
        if ((*JasperVM)->AttachCurrentThreadAsDaemon(JasperVM, (void **)&env, NULL) != JNI_OK) return;
    } else if (state != JNI_OK) {
        return;
    }
    NSUInteger length = identifier.length;
    unichar *characters = malloc(sizeof(unichar) * (length == 0 ? 1 : length));
    if (!characters) return;
    [identifier getCharacters:characters range:NSMakeRange(0, length)];
    jstring value = (*env)->NewString(env, characters, (jsize)length);
    free(characters);
    if (value) {
        (*env)->CallStaticVoidMethod(env, JasperBridge, JasperClicked, value);
        (*env)->DeleteLocalRef(env, value);
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

// Where a click goes; the headless test points it at a recorder.
static void (*JasperClickSink)(NSString *) = JasperDeliverClick;

// Keep the delegate alive: UNUserNotificationCenter holds it weakly. Jasper's Java delivery rule already decides
// which notifications reach the operating system, so a banner is presented even while the app is active.
@interface JasperNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end
@implementation JasperNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
      willPresentNotification:(UNNotification *)notification
        withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completion {
    completion(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completion {
    // Only a click on the banner itself: dismissing it is not asking to see it.
    if ([response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier])
        JasperClickSink(response.notification.request.identifier);
    completion();
}
@end

static void JasperPost(UNUserNotificationCenter *center, NSString *identifier, NSString *title, NSString *detail) {
    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = title;
    content.body = detail;
    // The notification's id: posting it again replaces its banner, and withdrawing it removes the banner.
    UNNotificationRequest *request = [UNNotificationRequest
        requestWithIdentifier:identifier content:content trigger:nil];
    [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert
        completionHandler:^(BOOL granted, NSError *error) {
            if (error) NSLog(@"Jasper notification authorization failed: %@", error);
            if (!granted || error) return;
            [center addNotificationRequest:request withCompletionHandler:^(NSError *deliveryError) {
                if (deliveryError) NSLog(@"Jasper notification delivery failed: %@", deliveryError);
            }];
        }];
}

static void JasperWithdraw(UNUserNotificationCenter *center, NSString *identifier) {
    NSArray<NSString *> *identifiers = @[identifier];
    [center removePendingNotificationRequestsWithIdentifiers:identifiers];
    [center removeDeliveredNotificationsWithIdentifiers:identifiers];
}

static void JasperWithdrawAll(UNUserNotificationCenter *center) {
    [center removeAllPendingNotificationRequests];
    [center removeAllDeliveredNotifications];
}

static NSString *JasperString(JNIEnv *env, jstring value) {
    if (!value) return @"";
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (!characters) return nil; // The JVM has already raised OutOfMemoryError.
    NSString *result = [[NSString alloc] initWithCharacters:characters
        length:(NSUInteger)(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, characters);
    return result;
}

// Jasper's notification center, with the delegate that routes clicks; nil outside Jasper.app.
static UNUserNotificationCenter *JasperCenter(void) {
    // currentNotificationCenter raises an Objective-C exception outside an app bundle.
    // Never impersonate an installed app when run by Gradle, an IDE, or a test JVM.
    if (![NSBundle.mainBundle.bundleIdentifier isEqualToString:@"dev.jasper.app"]) return nil;
    UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
    static JasperNotificationDelegate *delegate;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ delegate = [JasperNotificationDelegate new]; });
    center.delegate = delegate;
    return center;
}

static void JasperThrow(JNIEnv *env, const char *message) {
    jclass failure = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (failure) (*env)->ThrowNew(env, failure, message);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JasperVM = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) return JNI_VERSION_1_8;
    jclass bridge = (*env)->FindClass(env, "dev/jasper/app/platform/MacNotifications");
    if (!bridge) {
        (*env)->ExceptionClear(env);
        return JNI_VERSION_1_8;
    }
    JasperBridge = (*env)->NewGlobalRef(env, bridge);
    JasperClicked = (*env)->GetStaticMethodID(env, bridge, "clicked", "(Ljava/lang/String;)V");
    if (!JasperClicked) (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, bridge);
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_post(
    JNIEnv *env, jclass type, jstring identifier, jstring title, jstring detail) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            NSString *nativeIdentifier = JasperString(env, identifier);
            if ((*env)->ExceptionCheck(env)) return;
            NSString *nativeTitle = JasperString(env, title);
            if ((*env)->ExceptionCheck(env)) return;
            NSString *nativeDetail = JasperString(env, detail);
            if ((*env)->ExceptionCheck(env)) return;
            JasperPost(center, nativeIdentifier, nativeTitle, nativeDetail);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not submit a Jasper notification");
        }
    }
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_remove(
    JNIEnv *env, jclass type, jstring identifier) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            NSString *nativeIdentifier = JasperString(env, identifier);
            if ((*env)->ExceptionCheck(env)) return;
            JasperWithdraw(center, nativeIdentifier);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not withdraw a Jasper notification");
        }
    }
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_removeAll(JNIEnv *env, jclass type) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            JasperWithdrawAll(center);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not withdraw Jasper's notifications");
        }
    }
}
```

- [ ] **Step 4: Run the native test to see it pass**

Run: `./gradlew :jasper-app:testMacNotifications` (macOS only)
Expected: PASS, printing `macOS notification bridge: all headless checks passed`.

- [ ] **Step 5: Write the failing Java tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/MacNotificationsTest.java`:

```java
package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

class MacNotificationsTest {
    @AfterEach void forgetTheSink() { MacNotifications.onClick(id -> { }); }

    @Test
    @EnabledIfSystemProperty(named = "jasper.testNotificationLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void realNativeBridgeLinksAndDeclinesAnUnbundledJvm() throws Exception {
        System.load(System.getProperty("jasper.testNotificationLibrary"));
        // Real JNI linkage, JNI_OnLoad included, without a permission prompt or a notification.
        String id = "0f8fad5b-d9cb-469f-a165-70867728950e";
        invoke("post", id, "build", "Finished");
        invoke("remove", id);
        invoke("removeAll");
    }

    @Test void aClickReachesTheSinkAndASinkThatThrowsStaysInJava() {
        var heard = new ArrayList<String>();
        MacNotifications.onClick(heard::add);
        MacNotifications.clicked("first");
        MacNotifications.onClick(id -> { throw new IllegalStateException("broken handler"); });
        MacNotifications.clicked("second");
        assertThat(heard).containsExactly("first");
    }

    @Test void nativeLibraryBelongsToTheRunningBundleIncludingRenamedApps() {
        assertThat(MacNotifications.libraryPath("/Applications/Jasper Preview.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper Preview.app/Contents/app/libjasper-notifications.dylib")
                .toAbsolutePath());
    }

    @Test void unpackagedLaunchesDoNotUseAnotherApplicationsIdentity() {
        assertThat(MacNotifications.libraryPath(null)).isNull();
        assertThat(MacNotifications.libraryPath(" ")).isNull();
        assertThat(MacNotifications.libraryPath("/usr/bin/java")).isNull();
        assertThat(MacNotifications.libraryPath("/tmp/Contents/MacOS/Jasper")).isNull();
        assertThat(MacNotifications.libraryPath("/Contents/MacOS/Jasper")).isNull();
    }

    private static void invoke(String name, String... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        Arrays.fill(types, String.class);
        var method = MacNotifications.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(null, (Object[]) arguments);
    }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/platform/NativeNotifierTest.java` with (the bridge tests moved to
`MacNotificationsTest`):

```java
package dev.jasper.app.platform;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeNotifierTest {
    @Test void sendsLiteralUnicodeTextOffTheCallingThread() throws Exception {
        String title = "echo \"hi\"; say 'boo'\n\\ $() " + (char) 0xD83D + (char) 0xDE80;
        String detail = "Finished\0in 1m";
        var delivered = new CountDownLatch(1);
        var values = new CopyOnWriteArrayList<String>();
        var caller = Thread.currentThread();
        var threads = new CopyOnWriteArrayList<Thread>();
        try (var notifier = new NativeNotifier(true, (t, d) -> {
            values.addAll(List.of(t, d));
            threads.add(Thread.currentThread());
            delivered.countDown();
        })) {
            notifier.send(title, detail);
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(values).containsExactly(title, detail);
            assertThat(threads).noneMatch(thread -> thread == caller);
        }
    }

    @Test void unsupportedAndClosedNotifiersSendNothing() {
        var values = new CopyOnWriteArrayList<String>();
        try (var notifier = new NativeNotifier(false, (t, d) -> values.add(t))) {
            notifier.send("build", "Finished");
        }
        var notifier = new NativeNotifier(true, (t, d) -> values.add(t));
        notifier.close();
        notifier.send("build", "Finished");
        assertThat(values).isEmpty();
    }

    @Test void aNativeLinkFailureDoesNotKillSubsequentDelivery() throws Exception {
        var delivered = new CountDownLatch(1);
        try (var notifier = new NativeNotifier(true, (t, d) -> {
            if (t.equals("first")) throw new UnsatisfiedLinkError("test missing library");
            delivered.countDown();
        })) {
            notifier.send("first", "Finished");
            notifier.send("second", "Finished");
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
```

- [ ] **Step 6: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: FAIL to compile: `MacNotifications.onClick` and `MacNotifications.clicked` do not exist.

- [ ] **Step 7: Implement the Java side**

Replace `jasper-app/src/main/java/dev/jasper/app/platform/MacNotifications.java` with:

```java
package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * In-process UserNotifications bridge; macOS derives identity and icon from the main bundle. Each banner's identifier
 * is its notification's id, so sending it again replaces the banner and withdrawing it removes the banner; a click on
 * a banner comes back through {@link #clicked} on a thread macOS owns. Only a packaged {@code Jasper.app} loads the
 * library; other launches send nothing.
 */
final class MacNotifications {
    private static final System.Logger LOG = System.getLogger(MacNotifications.class.getName());
    private static boolean loaded;
    private static volatile Consumer<String> clicks = id -> { };

    private MacNotifications() { }

    static Path libraryPath(String appPath) {
        if (appPath == null || appPath.isBlank()) return null;
        Path executable = Path.of(appPath).toAbsolutePath().normalize();
        Path macOS = executable.getParent();
        if (macOS == null || !macOS.endsWith("MacOS")) return null;
        Path contents = macOS.getParent();
        if (contents == null || !contents.endsWith("Contents")) return null;
        Path bundle = contents.getParent();
        if (bundle == null || bundle.getFileName() == null
                || !bundle.getFileName().toString().endsWith(".app")) return null;
        return contents.resolve("app/libjasper-notifications.dylib");
    }

    /** Where a click's notification id goes. Any thread. */
    static void onClick(Consumer<String> sink) { clicks = Objects.requireNonNull(sink, "sink"); }

    /** Shows, or replaces, the banner for {@code id}; outside a packaged app nothing happens. */
    static void send(String id, String title, String detail) {
        if (load()) post(id, title, detail);
    }

    /** Removes the banner for {@code id}, if this process loaded the bridge. */
    static void withdraw(String id) {
        if (isLoaded()) remove(id);
    }

    /** Removes every banner Jasper shows, if this process loaded the bridge. */
    static void withdrawAll() {
        if (isLoaded()) removeAll();
    }

    /** Called by the bridge when the user clicks one of Jasper's banners; a failing sink never reaches native code. */
    static void clicked(String id) {
        try {
            clicks.accept(id);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A notification click could not be handled", failure);
        }
    }

    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    private static synchronized boolean load() {
        if (!loaded) {
            Path library = libraryPath(System.getProperty("jpackage.app-path"));
            if (library == null) return false;
            System.load(library.toString());
            loaded = true;
        }
        return true;
    }

    private static synchronized boolean isLoaded() { return loaded; }

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String id, String title, String detail);

    private static native void remove(String id);

    private static native void removeAll();
}
```

In `jasper-app/src/main/java/dev/jasper/app/platform/NativeNotifier.java`, replace

```java
        this(supported && MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) != null,
            MacNotifications::send);
```

with

```java
        this(supported && MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) != null,
            (title, detail) -> MacNotifications.send(java.util.UUID.randomUUID().toString(), title, detail));
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' :jasper-app:testMacNotifications` (drop the
last task on hosts other than macOS)
Expected: PASS. On macOS `realNativeBridgeLinksAndDeclinesAnUnbundledJvm` runs against the freshly compiled library.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/native/MacNotifications.m jasper-app/src/test/native/MacNotificationsTest.m jasper-app/src/main/java/dev/jasper/app/platform/MacNotifications.java jasper-app/src/main/java/dev/jasper/app/platform/NativeNotifier.java jasper-app/src/test/java/dev/jasper/app/platform/MacNotificationsTest.java jasper-app/src/test/java/dev/jasper/app/platform/NativeNotifierTest.java
git commit -m "feat(platform): macOS banners carry their notification's id, withdraw and report clicks" -m "The UserNotifications bridge posts each banner under its notification's id, withdraws one or all, and calls back into Java through a cached JavaVM when the user clicks a banner." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The system notifier

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java`

**Interfaces:**
- Consumes: Task 3's `MacNotifications.send/withdraw/withdrawAll/onClick/libraryPath`.
- Produces (`dev.jasper.app.platform.SystemNotifier`, public unless noted):
  - Types:
    - `enum Level { INFO, WARNING, ERROR }`
    - `record Message(String id, Level level, String title, String body)`
    - `record Backend(Consumer<Message> show, Consumer<String> withdraw, Runnable withdrawAll, Runnable focused, Runnable close)`
      with `Backend.NONE`
  - Factories:
    - `static SystemNotifier none()`
    - `static SystemNotifier using(Executor worker, Function<Consumer<String>, Backend> backend)`
    - `static SystemNotifier onWorker(Function<Consumer<String>, Backend> backend)` (package-private)
    - `static SystemNotifier forThisPlatform(String osName)` (Tasks 5 and 6 add parameters)
  - Instance methods:
    - `void onClick(Consumer<UUID> handler)`
    - `void send(UUID id, Level level, String title, String body)`
    - `void withdraw(UUID id)`
    - `void withdrawAll()`
    - `void focused()`
    - `void close()`
    - `boolean silent()` (package-private)
  - Static helpers: `static String title(String)`, `static String body(String)` (package-private)
  - Constants: `TITLE_LIMIT = 120`, `BODY_LIMIT = 300`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java`:

```java
package dev.jasper.app.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemNotifierTest {
    private final List<String> calls = new ArrayList<>();
    private Consumer<String> clicked;

    private SystemNotifier notifier() {
        return SystemNotifier.using(Runnable::run, clicks -> {
            clicked = clicks;
            return new SystemNotifier.Backend(
                message -> calls.add("show " + message.id() + " " + message.level() + " " + message.title() + "|" + message.body()),
                id -> calls.add("withdraw " + id), () -> calls.add("withdraw all"), () -> calls.add("focused"),
                () -> calls.add("close"));
        });
    }

    @Test void aNotificationReachesTheBackendWithItsIdLevelAndText() {
        UUID id = UUID.randomUUID();
        notifier().send(id, SystemNotifier.Level.WARNING, "make", "Exited 1 · 2m 03s");
        assertThat(calls).containsExactly("show " + id + " WARNING make|Exited 1 · 2m 03s");
    }

    @Test void withdrawingAndFocusAreForwarded() {
        var notifier = notifier();
        UUID id = UUID.randomUUID();
        notifier.withdraw(id);
        notifier.withdrawAll();
        notifier.focused();
        assertThat(calls).containsExactly("withdraw " + id, "withdraw all", "focused");
    }

    @Test void theTitleIsOneLineOfPlainText() {
        String hostile = "make" + (char) 0x1B + "[31m\n\tdone" + (char) 0x202E + "gpj.exe" + (char) 0x0000 + (char) 0xD83D;
        assertThat(SystemNotifier.title(hostile)).isEqualTo("make[31m  donegpj.exe");
        assertThat(SystemNotifier.title(" \n" + (char) 0x200F + "\t")).as("nothing left").isEqualTo("Jasper");
        assertThat(SystemNotifier.title(null)).isEqualTo("Jasper");
    }

    @Test void theBodyKeepsItsLinesAndDropsTheRest() {
        assertThat(SystemNotifier.body("first\r\nsecond\rthird" + (char) 0x07 + (char) 0x2066 + "\tend"))
            .isEqualTo("first\nsecond\nthird end");
        assertThat(SystemNotifier.body(null)).isEmpty();
    }

    @Test void longTextIsCutWithAnEllipsisAndNeverSplitsACharacter() {
        String rocket = new String(Character.toChars(0x1F680));
        String title = "a".repeat(SystemNotifier.TITLE_LIMIT - 2) + rocket + rocket + "tail";
        String cut = SystemNotifier.title(title);
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(SystemNotifier.TITLE_LIMIT);
        assertThat(cut).isEqualTo("a".repeat(SystemNotifier.TITLE_LIMIT - 2) + rocket + "…");
        assertThat(SystemNotifier.body("b".repeat(5_000))).hasSize(SystemNotifier.BODY_LIMIT).endsWith("…");
        assertThat(SystemNotifier.title("a".repeat(SystemNotifier.TITLE_LIMIT))).as("exactly at the limit").doesNotContain("…");
    }

    @Test void aBackendFailureIsLoggedOnceAndLaterNotificationsStillGo() {
        var shown = new ArrayList<String>();
        var notifier = SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(message -> {
            if (message.title().equals("first")) throw new UnsatisfiedLinkError("test missing library");
            if (message.title().equals("second")) throw new IllegalStateException("broken");
            shown.add(message.title());
        }, id -> { }, () -> { }, () -> { }, () -> { }));
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "first", "");
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "second", "");
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "third", "");
        assertThat(shown).containsExactly("third");
    }

    @Test void closingReleasesTheBackendOnceAndShowsNothingAfterwards() {
        var notifier = notifier();
        notifier.close();
        notifier.close();
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "late", "");
        notifier.withdrawAll();
        notifier.focused();
        assertThat(calls).containsExactly("close");
    }

    @Test void aClickReachesTheHandlerOnTheEdtAsTheNotificationsId() throws Exception {
        var notifier = notifier();
        var heard = new CopyOnWriteArrayList<UUID>();
        var onEdt = new CopyOnWriteArrayList<Boolean>();
        notifier.onClick(id -> {
            heard.add(id);
            onEdt.add(SwingUtilities.isEventDispatchThread());
        });
        UUID id = UUID.randomUUID();
        clicked.accept(id.toString());
        clicked.accept("not one of Jasper's");
        clicked.accept(null);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(heard).containsExactly(id);
        assertThat(onEdt).containsExactly(true);
        notifier.close();
        clicked.accept(UUID.randomUUID().toString());
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(heard).containsExactly(id);
    }

    @Test void theWorkerRunsOffTheCallingThreadAsADaemon() throws Exception {
        var delivered = new CountDownLatch(1);
        var threads = new CopyOnWriteArrayList<Thread>();
        var notifier = SystemNotifier.onWorker(clicks -> new SystemNotifier.Backend(message -> {
            threads.add(Thread.currentThread());
            delivered.countDown();
        }, id -> { }, () -> { }, () -> { }, () -> { }));
        notifier.send(UUID.randomUUID(), SystemNotifier.Level.INFO, "build", "Finished");
        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).singleElement().satisfies(thread -> {
            assertThat(thread).isNotSameAs(Thread.currentThread());
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("jasper-system-notifications");
        });
        notifier.close();
    }

    @Test void anUnpackagedMacAndUnknownSystemsShowNothing() {
        // A test JVM has no jpackage.app-path, so no Jasper.app bundle to speak for.
        assertThat(SystemNotifier.forThisPlatform("Mac OS X").silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform("FreeBSD").silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform(null).silent()).isTrue();
        assertThat(SystemNotifier.none().silent()).isTrue();
        assertThat(notifier().silent()).isFalse();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.SystemNotifierTest'`
Expected: FAIL to compile: `SystemNotifier` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java`:

```java
package dev.jasper.app.platform;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Jasper's notifications as the operating system shows them (spec section 9). One daemon worker hands each call to
 * the platform's backend, so a slow or broken backend never holds up the EDT; a backend failure is logged once and
 * never thrown. Text is plain: control characters, bidirectional controls and lone surrogates are dropped, the title is
 * one line of at most {@value #TITLE_LIMIT} characters and the body at most {@value #BODY_LIMIT}. A click on a
 * notification reaches the click handler on the EDT as the notification's id. Any thread; close at shutdown.
 */
public final class SystemNotifier implements AutoCloseable {
    /** The longest title the operating system is given, in characters. */
    public static final int TITLE_LIMIT = 120;
    /** The longest body the operating system is given, in characters. */
    public static final int BODY_LIMIT = 300;
    private static final System.Logger LOG = System.getLogger(SystemNotifier.class.getName());

    /** How serious a notification is, for a platform that shows it (the Windows balloon's icon). */
    public enum Level { INFO, WARNING, ERROR }

    /**
     * One notification as a backend receives it.
     *
     * @param id the notification's id as text, which a click hands back
     * @param level how serious it is
     * @param title one line, never blank
     * @param body plain text, may be empty
     */
    public record Message(String id, Level level, String title, String body) {
        public Message {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * What a platform does, as functions the worker runs.
     *
     * @param show shows a notification, replacing the one already shown for its id
     * @param withdraw removes one by id, where the platform can
     * @param withdrawAll removes every one Jasper showed, where the platform can
     * @param focused hears that Jasper is focused again
     * @param close releases what the platform holds; nothing runs after it
     */
    public record Backend(Consumer<Message> show, Consumer<String> withdraw, Runnable withdrawAll, Runnable focused,
                          Runnable close) {
        /** A platform that shows nothing. */
        public static final Backend NONE = new Backend(message -> { }, id -> { }, () -> { }, () -> { }, () -> { });

        public Backend {
            Objects.requireNonNull(show, "show");
            Objects.requireNonNull(withdraw, "withdraw");
            Objects.requireNonNull(withdrawAll, "withdrawAll");
            Objects.requireNonNull(focused, "focused");
            Objects.requireNonNull(close, "close");
        }
    }

    private final Executor worker;
    private final Runnable stopWorker;
    private final Backend backend;
    private volatile Consumer<UUID> clicks = id -> { };
    private volatile boolean closed;
    /** Worker-confined: whether a backend failure was logged already. */
    private boolean warned;

    private SystemNotifier(Executor worker, Runnable stopWorker, Function<Consumer<String>, Backend> backend) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.stopWorker = Objects.requireNonNull(stopWorker, "stopWorker");
        this.backend = Objects.requireNonNull(backend.apply(this::clicked), "backend");
    }

    /** A notifier that shows nothing and starts no thread: tests, and platforms without notifications. */
    public static SystemNotifier none() {
        return using(task -> { }, clicked -> Backend.NONE);
    }

    /**
     * A notifier over the backend {@code backend} builds from the function that reports clicks; {@code worker} runs
     * every call. For composition and tests.
     */
    public static SystemNotifier using(Executor worker, Function<Consumer<String>, Backend> backend) {
        return new SystemNotifier(worker, () -> { }, backend);
    }

    /** A notifier whose calls run on one daemon worker thread, stopped by {@link #close()}. */
    static SystemNotifier onWorker(Function<Consumer<String>, Backend> backend) {
        ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-system-notifications").factory());
        return new SystemNotifier(worker, worker::shutdown, backend);
    }

    /**
     * The notifier for this desktop: banners from a packaged macOS {@code Jasper.app}; nothing elsewhere, including
     * Gradle and IDE runs on macOS, which lack Jasper's bundle identity.
     *
     * @param osName the {@code os.name} property
     */
    public static SystemNotifier forThisPlatform(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac"))
            return MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) == null ? none() : onWorker(SystemNotifier::mac);
        return none();
    }

    /** macOS: the UserNotifications bridge, which hands a click's id to {@code clicked}. */
    static Backend mac(Consumer<String> clicked) {
        MacNotifications.onClick(clicked);
        return new Backend(message -> MacNotifications.send(message.id(), message.title(), message.body()),
            MacNotifications::withdraw, MacNotifications::withdrawAll, () -> { }, () -> { });
    }

    /** Where a click goes: the clicked notification's id, on the EDT. */
    public void onClick(Consumer<UUID> handler) {
        clicks = Objects.requireNonNull(handler, "handler");
    }

    /** Shows a notification, or replaces the one already shown for {@code id}. */
    public void send(UUID id, Level level, String title, String body) {
        var message = new Message(Objects.requireNonNull(id, "id").toString(), level, title(title), body(body));
        run(() -> backend.show().accept(message));
    }

    /** Removes the notification shown for {@code id}, where the platform can. */
    public void withdraw(UUID id) {
        String text = Objects.requireNonNull(id, "id").toString();
        run(() -> backend.withdraw().accept(text));
    }

    /** Removes every notification Jasper showed, where the platform can. */
    public void withdrawAll() { run(backend.withdrawAll()); }

    /** Jasper is focused again. */
    public void focused() { run(backend.focused()); }

    /** Whether this notifier shows nothing at all. */
    boolean silent() { return backend == Backend.NONE; }

    /** Lets the backend release what it holds, then stops the worker; nothing is shown afterwards. */
    @Override public synchronized void close() {
        if (closed) return;
        try {
            worker.execute(() -> contained(backend.close()));
        } catch (RejectedExecutionException stopped) {
            // Already shutting down.
        }
        closed = true;
        stopWorker.run();
    }

    private void run(Runnable task) {
        if (closed) return;
        try {
            worker.execute(() -> contained(task));
        } catch (RejectedExecutionException stopped) {
            // Shutting down: a missed notification is not worth reporting.
        }
    }

    private void contained(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError failure) {
            // A missing native library or a broken notifier must never take Jasper down.
            if (!warned) {
                warned = true;
                LOG.log(System.Logger.Level.WARNING, "Could not show a system notification", failure);
            }
        }
    }

    /** A backend reports a click on a thread of its own: the handler hears it on the EDT. */
    private void clicked(String id) {
        if (closed || id == null) return;
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (IllegalArgumentException foreign) {
            return; // not one of Jasper's
        }
        SwingUtilities.invokeLater(() -> {
            if (!closed) clicks.accept(parsed);
        });
    }

    /** One line of plain text, at most {@value #TITLE_LIMIT} characters; "Jasper" when nothing is left. */
    static String title(String text) {
        String plain = plain(text, false);
        return plain.isEmpty() ? "Jasper" : capped(plain, TITLE_LIMIT);
    }

    /** Plain text that keeps its lines, at most {@value #BODY_LIMIT} characters. */
    static String body(String text) {
        return capped(plain(text, true), BODY_LIMIT);
    }

    /**
     * Drops control characters (keeping line breaks when {@code lines}), bidirectional controls, which can make text
     * read differently from what it is, and lone surrogates; tabs become spaces.
     */
    private static String plain(String text, boolean lines) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        var out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(c -> {
            if (c == '\n') out.append(lines ? '\n' : ' ');
            else if (c == '\t') out.append(' ');
            else if (!Character.isISOControl(c) && !bidiControl(c) && Character.getType(c) != Character.SURROGATE)
                out.appendCodePoint(c);
        });
        return out.toString().strip();
    }

    private static boolean bidiControl(int c) {
        return c == 0x061C || c == 0x200E || c == 0x200F || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
    }

    /** At most {@code limit} code points, the last an ellipsis when it was cut. */
    private static String capped(String text, int limit) {
        if (text.codePointCount(0, text.length()) <= limit) return text;
        return text.substring(0, text.offsetByCodePoints(0, limit - 1)) + "…";
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: PASS (`SystemNotifierTest` 10/10).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java
git commit -m "feat(platform): a system notifier with one worker and plain, capped text" -m "SystemNotifier hands each call to the platform's backend on one daemon worker, strips control and bidirectional characters, caps titles at 120 and bodies at 300 characters, and brings clicks back to the EDT as notification ids; macOS uses the UserNotifications bridge." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Linux notifications through notify-send

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/LinuxNotifications.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java` (the factory)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/LinuxNotificationsTest.java`, `SystemNotifierTest.java`,
  `AppDirsTest.java`

**Interfaces:**
- Consumes: Task 4's `SystemNotifier.Message`, `Backend`, `onWorker`.
- Produces:
  - `AppDirs.notificationIcon()` (`<root>/notification-icon.png`)
  - `SystemNotifier.forThisPlatform(String osName, AppDirs dirs)`
  - Package-private, `LinuxNotifications`:
    - `LinuxNotifications(Supplier<Optional<Path>> locate, Function<List<String>, Process> start, Supplier<Optional<Path>> icon, Consumer<String> clicked)`
    - `show(Message)`, `withdraw(String)`, `withdrawAll()`, `close()`
    - `static Backend backend(Path iconFile, Consumer<String> clicked)`
    - `static Optional<Path> onPath(String path)`
    - `static Optional<Path> iconIn(Path target)`
    - `static String markupEscaped(String body)`
    - `MAX_WAITING = 8`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/LinuxNotificationsTest.java`:

```java
package dev.jasper.app.platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LinuxNotificationsTest {
    /** A process a test finishes by hand, with what it printed. */
    private static final class FakeProcess extends Process {
        final List<String> arguments;
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        private volatile byte[] output = new byte[0];
        volatile boolean destroyed;

        FakeProcess(List<String> arguments) { this.arguments = List.copyOf(arguments); }

        void finish(String printed) {
            output = printed.getBytes(StandardCharsets.UTF_8);
            exit.complete(this);
        }

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(output); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return exit.isDone(); }
        @Override public int exitValue() {
            if (!exit.isDone()) throw new IllegalThreadStateException("running");
            return 0;
        }
        @Override public void destroy() {
            destroyed = true;
            exit.complete(this);
        }
        @Override public CompletableFuture<Process> onExit() { return exit; }
    }

    private static final Path PROGRAM = Path.of("/usr/bin/notify-send");
    private static final Path ICON = Path.of("/home/me/.config/jasper/notification-icon.png");
    private static final String WITH_ACTIONS =
        "Usage:\n  notify-send [OPTION...] <SUMMARY> [BODY]\n  -A, --action=[NAME=]Text...\n  -w, --wait\n";
    private static final String WITHOUT_ACTIONS = "Usage:\n  notify-send [OPTION...] <SUMMARY> [BODY]\n  -u, --urgency=LEVEL\n";

    private final List<FakeProcess> started = new ArrayList<>();
    private final List<String> clicks = new ArrayList<>();
    private int located;
    private Optional<Path> program = Optional.of(PROGRAM);
    private String help = WITH_ACTIONS;
    private boolean helpFinishes = true;

    private LinuxNotifications linux() { return linux(() -> Optional.of(ICON)); }

    private LinuxNotifications linux(Supplier<Optional<Path>> icon) {
        return new LinuxNotifications(() -> {
            located++;
            return program;
        }, arguments -> {
            var process = new FakeProcess(arguments);
            started.add(process);
            if (arguments.equals(List.of(PROGRAM.toString(), "--help")) && helpFinishes) process.finish(help);
            return process;
        }, icon, clicks::add);
    }

    private static SystemNotifier.Message message(String id, String title, String body) {
        return new SystemNotifier.Message(id, SystemNotifier.Level.INFO, title, body);
    }

    /** The notifications started, without the --help probe. */
    private List<FakeProcess> shown() {
        return started.stream().filter(process -> !process.arguments.equals(List.of(PROGRAM.toString(), "--help"))).toList();
    }

    private long probes() {
        return started.stream().filter(process -> process.arguments.equals(List.of(PROGRAM.toString(), "--help"))).count();
    }

    @Test void argumentsCarryTheTextAsIsWithTheBodysMarkupEscaped() {
        help = WITHOUT_ACTIONS;
        linux().show(message("id-1", "-rf <b>build</b> & co", "a <b>bold</b> & \"quoted\" $(rm -rf ~)"));
        assertThat(shown()).singleElement().satisfies(process -> assertThat(process.arguments).containsExactly(
            PROGRAM.toString(), "--app-name=Jasper", "--icon=" + ICON, "--", "-rf <b>build</b> & co",
            "a &lt;b&gt;bold&lt;/b&gt; &amp; \"quoted\" $(rm -rf ~)"));
    }

    @Test void withoutAnIconTheArgumentIsLeftOut() {
        help = WITHOUT_ACTIONS;
        linux(Optional::empty).show(message("id-1", "make", "done"));
        assertThat(shown().getFirst().arguments).containsExactly(PROGRAM.toString(), "--app-name=Jasper", "--", "make", "done");
    }

    @Test void withActionsANotificationWaitsAndDefaultIsAClick() {
        var linux = linux();
        linux.show(message("id-1", "make", "Finished in 30s"));
        linux.show(message("id-2", "test", "Exited 1"));
        List<FakeProcess> shown = shown();
        assertThat(shown.getFirst().arguments).containsSubsequence("--action=default=Open", "--wait", "--", "make");
        shown.get(0).finish("default\n");
        shown.get(1).finish(""); // dismissed, or the server let it go
        assertThat(clicks).containsExactly("id-1");
    }

    @Test void withoutActionsNotificationsAreFireAndForgetAndTheProbeRunsOnce() {
        help = WITHOUT_ACTIONS;
        var linux = linux();
        linux.show(message("id-1", "make", ""));
        linux.show(message("id-2", "again", ""));
        assertThat(shown()).hasSize(2).allSatisfy(process -> assertThat(process.arguments).doesNotContain("--wait"));
        shown().getFirst().finish("default");
        assertThat(clicks).isEmpty();
        assertThat(probes()).isEqualTo(1);
    }

    @Test void atMostEightWaitAndTheOldestIsKilled() {
        var linux = linux();
        for (int i = 0; i <= LinuxNotifications.MAX_WAITING; i++) linux.show(message("id-" + i, "n" + i, ""));
        List<FakeProcess> shown = shown();
        assertThat(shown).hasSize(LinuxNotifications.MAX_WAITING + 1);
        assertThat(shown.getFirst().destroyed).isTrue();
        assertThat(shown.subList(1, shown.size())).noneMatch(process -> process.destroyed);
        assertThat(clicks).as("a killed process is no click").isEmpty();
    }

    @Test void readingANotificationOrShuttingDownKillsItsWaitingProcess() {
        var linux = linux();
        linux.show(message("id-1", "first", ""));
        linux.show(message("id-2", "second", ""));
        linux.show(message("id-3", "third", ""));
        linux.withdraw("id-1");
        assertThat(shown().get(0).destroyed).isTrue();
        assertThat(shown().get(1).destroyed).isFalse();
        linux.withdrawAll();
        assertThat(shown().get(1).destroyed).isTrue();
        assertThat(shown().get(2).destroyed).isTrue();
        linux.show(message("id-4", "fourth", ""));
        linux.close();
        assertThat(shown().get(3).destroyed).isTrue();
        linux.show(message("id-5", "after", ""));
        assertThat(shown()).hasSize(4);
        shown().get(3).finish("default");
        assertThat(clicks).isEmpty();
    }

    @Test void withoutNotifySendNothingStartsAndThePathIsSearchedOnce() {
        program = Optional.empty();
        var linux = linux();
        linux.show(message("id-1", "first", ""));
        linux.show(message("id-2", "second", ""));
        assertThat(started).isEmpty();
        assertThat(located).isEqualTo(1);
    }

    @Test void aHelpThatNeverFinishesMeansNoActions() {
        helpFinishes = false;
        linux().show(message("id-1", "make", ""));
        FakeProcess probe = started.getFirst();
        assertThat(probe.arguments).containsExactly(PROGRAM.toString(), "--help");
        assertThat(probe.destroyed).as("the hung probe is killed").isTrue();
        assertThat(shown().getFirst().arguments).doesNotContain("--wait");
    }

    @Test void aProgramThatCannotStartFailsTheCallForTheNotifierToLog() {
        var linux = new LinuxNotifications(() -> Optional.of(PROGRAM), arguments -> {
            throw new UncheckedIOException(new IOException("cannot run"));
        }, Optional::empty, clicks::add);
        assertThatThrownBy(() -> linux.show(message("id-1", "make", ""))).isInstanceOf(UncheckedIOException.class);
    }

    @Test void notifySendIsFoundOnThePathAsAnExecutableFile(@TempDir Path directory) throws IOException {
        Path empty = Files.createDirectories(directory.resolve("empty"));
        Path bin = Files.createDirectories(directory.resolve("bin"));
        Path tool = Files.writeString(bin.resolve("notify-send"), "#!/bin/sh\n");
        assumeTrue(tool.toFile().setExecutable(true));
        Path other = Files.createDirectories(directory.resolve("other"));
        Files.writeString(other.resolve("notify-send"), "not executable");
        String path = String.join(java.io.File.pathSeparator, "", empty.toString(), bin.toString(), other.toString());
        assertThat(LinuxNotifications.onPath(path)).contains(tool);
        assertThat(LinuxNotifications.onPath(empty.toString())).isEmpty();
        assertThat(LinuxNotifications.onPath(null)).isEmpty();
        // Windows reports every readable file as executable; elsewhere a plain file is passed over.
        if (!System.getProperty("os.name", "").startsWith("Windows"))
            assertThat(LinuxNotifications.onPath(other.toString())).isEmpty();
    }

    @Test void theIconIsWrittenWhereNotifySendCanReadIt(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("config/jasper/notification-icon.png");
        assertThat(LinuxNotifications.iconIn(target)).contains(target);
        assertThat(Files.readAllBytes(target)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(LinuxNotifications.iconIn(target)).as("again, over the old one").contains(target);
    }

    @Test void anIconThatCannotBeWrittenIsLeftOut(@TempDir Path directory) throws IOException {
        Path blocker = Files.writeString(directory.resolve("file"), "");
        assertThat(LinuxNotifications.iconIn(blocker.resolve("notification-icon.png"))).isEmpty();
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java`, replace the test
`anUnpackagedMacAndUnknownSystemsShowNothing` with:

```java
    @Test void anUnpackagedMacAndUnknownSystemsShowNothing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        // A test JVM has no jpackage.app-path, so no Jasper.app bundle to speak for.
        assertThat(SystemNotifier.forThisPlatform("Mac OS X", dirs).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform("FreeBSD", dirs).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform(null, dirs).silent()).isTrue();
        assertThat(SystemNotifier.none().silent()).isTrue();
        assertThat(notifier().silent()).isFalse();
    }

    @Test void linuxHasNotifySend(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        // Nothing is looked up or started until the first notification.
        try (var linux = SystemNotifier.forThisPlatform("Linux", dirs)) {
            assertThat(linux.silent()).isFalse();
        }
    }
```

In `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java`, add before the class's closing brace:

```java
    @Test void theNotificationIconLivesInTheRoot() {
        AppDirs dirs = AppDirs.resolve("Linux", Map.of(), home);
        assertThat(dirs.notificationIcon()).isEqualTo(dirs.root().resolve("notification-icon.png"));
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: FAIL to compile. `LinuxNotifications`, `AppDirs.notificationIcon()` and the two-argument
`SystemNotifier.forThisPlatform` do not exist yet.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/platform/LinuxNotifications.java`:

```java
package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Linux notifications through {@code notify-send} (spec section 9), found on {@code PATH} once. Arguments go straight
 * to the process, never through a shell, and the body's markup characters are escaped. When {@code notify-send --help}
 * lists {@code --action}, each notification waits for a click with {@code --action=default=Open --wait}, which prints
 * {@code default} when it gets one; at most {@value #MAX_WAITING} wait at once, the oldest killed to make room, and a
 * notification read in Jasper, or Jasper shutting down, kills its process. Without {@code --action} a notification is
 * fire-and-forget; without {@code notify-send} there are none, which is logged once. Calls come from the system
 * notifier's worker and exits from other threads, so the state is guarded by this object's lock.
 */
final class LinuxNotifications {
    static final int MAX_WAITING = 8;
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final String ICON_RESOURCE = "/dev/jasper/app/icons/app/linux/icon-128.png";
    private static final System.Logger LOG = System.getLogger(LinuxNotifications.class.getName());

    private record Waiting(String id, Process process) {}

    private final Supplier<Optional<Path>> locate;
    private final Function<List<String>, Process> start;
    private final Supplier<Optional<Path>> icon;
    private final Consumer<String> clicked;
    private final ArrayDeque<Waiting> waiting = new ArrayDeque<>();
    private boolean resolved;
    private Path program;
    private boolean actions;
    private Optional<Path> iconPath = Optional.empty();
    private boolean closed;

    /**
     * @param locate finds {@code notify-send}, asked once
     * @param start starts a process from its arguments, the program first; throws when it cannot
     * @param icon writes Jasper's icon where {@code notify-send} can read it, asked once
     * @param clicked hears a clicked notification's id, on the thread that saw its process exit
     */
    LinuxNotifications(Supplier<Optional<Path>> locate, Function<List<String>, Process> start,
                       Supplier<Optional<Path>> icon, Consumer<String> clicked) {
        this.locate = Objects.requireNonNull(locate, "locate");
        this.start = Objects.requireNonNull(start, "start");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.clicked = Objects.requireNonNull(clicked, "clicked");
    }

    /** This desktop's backend: {@code notify-send} on {@code PATH}, with Jasper's icon written to {@code iconFile}. */
    static SystemNotifier.Backend backend(Path iconFile, Consumer<String> clicked) {
        var linux = new LinuxNotifications(() -> onPath(System.getenv("PATH")), LinuxNotifications::launch,
            () -> iconIn(iconFile), clicked);
        return new SystemNotifier.Backend(linux::show, linux::withdraw, linux::withdrawAll, () -> { }, linux::close);
    }

    synchronized void show(SystemNotifier.Message message) {
        if (closed || !resolve()) return;
        Process process = start.apply(arguments(message));
        if (!actions) return;
        if (waiting.size() >= MAX_WAITING) waiting.removeFirst().process().destroy();
        var entry = new Waiting(message.id(), process);
        waiting.addLast(entry);
        process.onExit().thenAccept(done -> finished(entry));
    }

    synchronized void withdraw(String id) {
        var ended = new ArrayList<Waiting>();
        for (Waiting entry : waiting) if (entry.id().equals(id)) ended.add(entry);
        waiting.removeAll(ended);
        ended.forEach(entry -> entry.process().destroy());
    }

    synchronized void withdrawAll() {
        var ended = List.copyOf(waiting);
        waiting.clear();
        ended.forEach(entry -> entry.process().destroy());
    }

    synchronized void close() {
        closed = true;
        withdrawAll();
    }

    /** A waiting process ended: a click when it printed {@code default}; nothing when Jasper killed it. */
    private void finished(Waiting entry) {
        synchronized (this) {
            if (!waiting.remove(entry)) return; // withdrawn, crowded out, or shut down
        }
        String printed;
        try (InputStream output = entry.process().getInputStream()) {
            printed = new String(output.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            return;
        }
        if (printed.equals("default")) clicked.accept(entry.id());
    }

    /** Finds {@code notify-send} and asks it about {@code --action}, once. */
    private boolean resolve() {
        if (!resolved) {
            resolved = true;
            program = locate.get().orElse(null);
            if (program == null) {
                LOG.log(System.Logger.Level.INFO, "notify-send is not on PATH, so Jasper shows no system notifications");
                return false;
            }
            actions = supportsActions(program);
            iconPath = icon.get();
        }
        return program != null;
    }

    private boolean supportsActions(Path program) {
        Process probe;
        try {
            probe = start.apply(List.of(program.toString(), "--help"));
        } catch (RuntimeException failure) {
            return false;
        }
        try {
            if (!probe.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                probe.destroyForcibly();
                return false;
            }
            try (InputStream output = probe.getInputStream()) {
                return new String(output.readAllBytes(), StandardCharsets.UTF_8).contains("--action");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            probe.destroyForcibly();
            return false;
        } catch (IOException unreadable) {
            return false;
        }
    }

    private List<String> arguments(SystemNotifier.Message message) {
        var arguments = new ArrayList<String>();
        arguments.add(program.toString());
        arguments.add("--app-name=Jasper");
        iconPath.ifPresent(path -> arguments.add("--icon=" + path));
        if (actions) {
            arguments.add("--action=default=Open");
            arguments.add("--wait");
        }
        arguments.add("--");
        arguments.add(message.title());
        arguments.add(markupEscaped(message.body()));
        return arguments;
    }

    /** Notification servers read the body as markup; Jasper's text is plain. */
    static String markupEscaped(String body) {
        return body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The first {@code notify-send} on {@code path} that is an executable file. */
    static Optional<Path> onPath(String path) {
        if (path == null) return Optional.empty();
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            Path candidate;
            try {
                candidate = Path.of(directory, "notify-send");
            } catch (InvalidPathException unusable) {
                continue;
            }
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Writes Jasper's 128-pixel icon to {@code target}; empty when it cannot, so the notification goes without one. */
    static Optional<Path> iconIn(Path target) {
        try (InputStream png = LinuxNotifications.class.getResourceAsStream(ICON_RESOURCE)) {
            if (png == null) return Optional.empty();
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(png, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the notification icon", failure);
            return Optional.empty();
        }
    }

    private static Process launch(List<String> arguments) {
        try {
            return new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java`, replace the whole `forThisPlatform` method
with:

```java
    /**
     * The notifier for this desktop: banners from a packaged macOS {@code Jasper.app}, {@code notify-send} on Linux,
     * and nothing elsewhere, including Gradle and IDE runs on macOS, which lack Jasper's bundle identity.
     *
     * @param osName the {@code os.name} property
     * @param dirs where Jasper keeps the icon {@code notify-send} shows
     */
    public static SystemNotifier forThisPlatform(String osName, AppDirs dirs) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac"))
            return MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) == null ? none() : onWorker(SystemNotifier::mac);
        if (os.startsWith("linux"))
            return onWorker(clicked -> LinuxNotifications.backend(dirs.notificationIcon(), clicked));
        return none();
    }
```

In `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`, replace

```java
    public Path buddyState() {
        return root.resolve("buddy.toml");
    }
```

with

```java
    public Path buddyState() {
        return root.resolve("buddy.toml");
    }

    /** Jasper's icon as a PNG file, written for Linux's {@code notify-send}. */
    public Path notificationIcon() {
        return root.resolve("notification-icon.png");
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: PASS (`LinuxNotificationsTest` 12/12, `SystemNotifierTest` 11/11).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform/LinuxNotifications.java jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java jasper-app/src/test/java/dev/jasper/app/platform/LinuxNotificationsTest.java jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
git commit -m "feat(platform): Linux notifications through notify-send" -m "notify-send is found on PATH once and run without a shell; when it supports --action, a click on a notification comes back to Jasper, at most eight wait at once, and reading one in Jasper or shutting down kills its process." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 6: One Windows tray icon for residency and balloons

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/WindowsTray.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/platform/ResidentTray.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/ResidentTrayTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java` (the factory)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/WindowsTrayTest.java`, `SystemNotifierTest.java`

**Interfaces:**
- Consumes: Task 4's `SystemNotifier.Message`, `Level`, `Backend`, `onWorker`; Task 5's two-argument factory.
- Produces:
  - Public, `WindowsTray`:
    - `static WindowsTray forThisDesktop()`
    - `Runnable resident(Runnable open, Runnable quit)` (a `BinaryOperator<Runnable>` for
      `JasperApplication.residentIndicator`)
  - Package-private, `WindowsTray`:
    - `balloon(Message)`, `focused()`, `withdraw(String)`, `withdrawAll()`, `onClick(Consumer<String>)`
    - `static Backend backend(WindowsTray tray, Consumer<String> clicked)`
    - `static TrayIcon.MessageType type(Level)`
    - `static boolean applies(String, boolean)`
    - `static Image image(List<Image>, Dimension)`
    - records `Icon(Consumer<String> tooltip, Consumer<Message> balloon, Runnable remove)` and
      `Menu(Runnable open, Runnable quit)`
  - `SystemNotifier.forThisPlatform(String osName, AppDirs dirs, WindowsTray tray)`.

- [ ] **Step 1: Write the failing tests**

Delete `jasper-app/src/test/java/dev/jasper/app/platform/ResidentTrayTest.java` and create
`jasper-app/src/test/java/dev/jasper/app/platform/WindowsTrayTest.java`:

```java
package dev.jasper.app.platform;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.TrayIcon;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsTrayTest {
    private final List<String> events = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<String> clicks = new ArrayList<>();
    private int opened;
    private boolean refuse;

    private WindowsTray tray() {
        var tray = new WindowsTray((action, menu) -> {
            if (refuse) return null;
            int number = actions.size() + 1;
            actions.add(action);
            events.add("add " + number + (menu.isPresent() ? " with Open and Quit" : ""));
            return new WindowsTray.Icon(tip -> events.add("tooltip " + number + " " + tip),
                message -> events.add("balloon " + number + " " + message.level() + " " + message.title()),
                () -> events.add("remove " + number));
        });
        tray.onClick(clicks::add);
        return tray;
    }

    private static SystemNotifier.Message message(String id) {
        return new SystemNotifier.Message(id, SystemNotifier.Level.WARNING, "title " + id, "body");
    }

    /** A click on the newest icon's balloon or on the icon itself, which Windows reports alike. */
    private void click() { actions.getLast().run(); }

    @Test void onlyADesktopWindowsSessionGetsATrayIcon() {
        assertThat(WindowsTray.applies("Windows 11", false)).isTrue();
        assertThat(WindowsTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(WindowsTray.applies("Mac OS X", false)).as("macOS keeps its Dock icon").isFalse();
        assertThat(WindowsTray.applies("Linux", false)).isFalse();
        assertThat(WindowsTray.applies(null, false)).isFalse();
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(WindowsTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(WindowsTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(WindowsTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(WindowsTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void aBalloonInTheBackgroundAddsAnIconThatGoesWhenJasperIsFocused() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.focused();
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1");
    }

    @Test void aSecondBalloonReusesTheIcon() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.balloon(message("b"));
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a",
            "balloon 1 WARNING title b");
    }

    @Test void aClickActivatesTheMostRecentNotificationOnce() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.balloon(message("b"));
        click();
        click();
        assertThat(clicks).containsExactly("b");
    }

    @Test void aNotificationReadInJasperIsNotActivated() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.withdraw("other");
        tray.withdraw("a");
        click();
        tray.balloon(message("b"));
        tray.withdrawAll();
        click();
        assertThat(clicks).isEmpty();
    }

    @Test void theResidentIconCarriesBalloonsAndStaysWhenJasperIsFocused() {
        var tray = tray();
        Runnable remove = tray.resident(() -> opened++, () -> { });
        tray.balloon(message("a"));
        tray.focused();
        tray.balloon(message("b"));
        click();
        click();
        remove.run();
        remove.run();
        assertThat(clicks).containsExactly("b");
        assertThat(opened).as("with no notification waiting, the click is Open").isEqualTo(1);
        assertThat(events).containsExactly("add 1 with Open and Quit", "tooltip 1 Jasper is running in the background",
            "balloon 1 WARNING title a", "balloon 1 WARNING title b", "remove 1");
    }

    @Test void aBalloonsIconGivesWayToTheResidentIconAndItsNotificationStillWaits() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.resident(() -> opened++, () -> { });
        click();
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1",
            "add 2 with Open and Quit", "tooltip 2 Jasper is running in the background");
        assertThat(clicks).containsExactly("a");
        assertThat(opened).isZero();
    }

    @Test void whereTheTrayRefusesAnIconNothingShowsAndRemovalIsANoOp() {
        refuse = true;
        var tray = tray();
        tray.resident(() -> opened++, () -> { }).run();
        tray.balloon(message("a"));
        tray.focused();
        assertThat(events).isEmpty();
        assertThat(opened).isZero();
    }

    @Test void headlessTheRealTrayShowsNothing() {
        // Headless only: elsewhere on Windows this would add an icon to the real notification area.
        assumeTrue(GraphicsEnvironment.isHeadless());
        var ran = new ArrayList<String>();
        var tray = WindowsTray.forThisDesktop();
        tray.resident(() -> ran.add("open"), () -> ran.add("quit")).run();
        tray.balloon(message("a"));
        assertThat(ran).isEmpty();
    }

    @Test void eachLevelHasItsBalloonIcon() {
        assertThat(WindowsTray.type(SystemNotifier.Level.INFO)).isEqualTo(TrayIcon.MessageType.INFO);
        assertThat(WindowsTray.type(SystemNotifier.Level.WARNING)).isEqualTo(TrayIcon.MessageType.WARNING);
        assertThat(WindowsTray.type(SystemNotifier.Level.ERROR)).isEqualTo(TrayIcon.MessageType.ERROR);
    }

    @Test void theBackendHopsToTheEdtAndRoutesClicks() throws Exception {
        var tray = tray();
        var backend = WindowsTray.backend(tray, clicks::add);
        backend.show().accept(message("a"));
        SwingUtilities.invokeAndWait(() -> { });
        click();
        backend.focused().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(clicks).containsExactly("a");
        assertThat(events).endsWith("remove 1");
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/platform/SystemNotifierTest.java`, replace the two factory tests
(`anUnpackagedMacAndUnknownSystemsShowNothing` and `linuxHasNotifySend`) with:

```java
    @Test void anUnpackagedMacAndUnknownSystemsShowNothing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        var tray = new WindowsTray((action, menu) -> null);
        // A test JVM has no jpackage.app-path, so no Jasper.app bundle to speak for.
        assertThat(SystemNotifier.forThisPlatform("Mac OS X", dirs, tray).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform("FreeBSD", dirs, tray).silent()).isTrue();
        assertThat(SystemNotifier.forThisPlatform(null, dirs, tray).silent()).isTrue();
        assertThat(SystemNotifier.none().silent()).isTrue();
        assertThat(notifier().silent()).isFalse();
    }

    @Test void linuxHasNotifySendAndWindowsTheTray(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        var dirs = new AppDirs(root, root.resolve("config.toml"), root.resolve("logs"));
        var tray = new WindowsTray((action, menu) -> null);
        // Nothing is looked up, started or shown until the first notification.
        try (var linux = SystemNotifier.forThisPlatform("Linux", dirs, tray);
             var windows = SystemNotifier.forThisPlatform("Windows 11", dirs, tray)) {
            assertThat(linux.silent()).isFalse();
            assertThat(windows.silent()).isFalse();
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: FAIL to compile. `WindowsTray` and the three-argument `SystemNotifier.forThisPlatform` do not exist.

- [ ] **Step 3: Implement**

Delete `jasper-app/src/main/java/dev/jasper/app/platform/ResidentTray.java` and create
`jasper-app/src/main/java/dev/jasper/app/platform/WindowsTray.java`:

```java
package dev.jasper.app.platform;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Jasper's one Windows notification-area icon (spec section 9). A resident Jasper with no window shows it with Open and
 * Quit, since a windowless process is otherwise invisible and has nothing to quit from. A notification sent while Jasper
 * is in the background shows its balloon from that icon, or from one added for the purpose and removed when Jasper is
 * focused again. A click on a balloon or on the icon activates the most recent notification still unread, since
 * {@code TrayIcon} cannot tell balloons apart; with none, a click on the resident icon is Open. macOS keeps its Dock
 * icon and gets no tray icon; neither does Linux. EDT only.
 */
public final class WindowsTray {
    private static final System.Logger LOG = System.getLogger(WindowsTray.class.getName());
    static final String RESIDENT_TOOLTIP = "Jasper is running in the background";
    static final String TOOLTIP = "Jasper";

    /**
     * An icon in the notification area, as functions.
     *
     * @param tooltip sets its tooltip
     * @param balloon shows a notification's balloon on it
     * @param remove takes it out of the notification area
     */
    record Icon(Consumer<String> tooltip, Consumer<SystemNotifier.Message> balloon, Runnable remove) {}

    /** What the resident icon's menu does. */
    record Menu(Runnable open, Runnable quit) {}

    /** Adds an icon whose action runs the given task, with the menu when there is one; null when the tray refuses. */
    private final BiFunction<Runnable, Optional<Menu>, Icon> add;
    private Icon icon;
    private boolean resident;
    private Runnable open = () -> { };
    /** The most recent notification still unread, which a click on a balloon or on the icon activates. */
    private String latest;
    private Consumer<String> clicked = id -> { };

    WindowsTray(BiFunction<Runnable, Optional<Menu>, Icon> add) {
        this.add = Objects.requireNonNull(add, "add");
    }

    /** This desktop's notification area: the real one on a Windows desktop, otherwise one that never shows an icon. */
    public static WindowsTray forThisDesktop() {
        if (!applies(System.getProperty("os.name"), GraphicsEnvironment.isHeadless()) || !SystemTray.isSupported())
            return new WindowsTray((action, menu) -> null);
        return new WindowsTray(WindowsTray::addToSystemTray);
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && osName.startsWith("Windows");
    }

    /**
     * Shows the resident icon with Open and Quit; its click is Open, unless a notification waits to be clicked. Returns
     * the way to remove it, a no-op where the tray refuses an icon. Shaped for {@code JasperApplication.residentIndicator}.
     */
    public Runnable resident(Runnable open, Runnable quit) {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(quit, "quit");
        // A background notification's icon gives way to the resident one, which carries its balloons from now on.
        if (icon != null) icon.remove().run();
        icon = add.apply(this::action, Optional.of(new Menu(open, quit)));
        resident = icon != null;
        if (icon == null) return () -> { };
        this.open = open;
        icon.tooltip().accept(RESIDENT_TOOLTIP);
        Icon shown = icon;
        return () -> {
            if (icon != shown) return;
            shown.remove().run();
            icon = null;
            resident = false;
            this.open = () -> { };
            latest = null;
        };
    }

    /** Shows a notification's balloon, adding Jasper's icon for it when none is there. */
    void balloon(SystemNotifier.Message message) {
        if (icon == null) {
            icon = add.apply(this::action, Optional.empty());
            if (icon == null) return;
            icon.tooltip().accept(TOOLTIP);
        }
        icon.balloon().accept(message);
        latest = message.id();
    }

    /** Jasper is focused again: no notification waits to be clicked, and an icon shown only for balloons goes. */
    void focused() {
        latest = null;
        if (icon != null && !resident) {
            icon.remove().run();
            icon = null;
        }
    }

    /** The notification was read in Jasper: a click no longer activates it. */
    void withdraw(String id) {
        if (id.equals(latest)) latest = null;
    }

    void withdrawAll() { latest = null; }

    /** Where a click on a notification's balloon goes: its id. */
    void onClick(Consumer<String> handler) { clicked = Objects.requireNonNull(handler, "handler"); }

    /** A click on a balloon, or on the icon: Windows reports both, and Enter on a focused icon, as the icon's action. */
    private void action() {
        String id = latest;
        if (id == null) {
            open.run();
            return;
        }
        latest = null;
        clicked.accept(id);
    }

    /** The backend over {@code tray}: every call hops to the EDT, where the tray lives. Call on the EDT. */
    static SystemNotifier.Backend backend(WindowsTray tray, Consumer<String> clicked) {
        tray.onClick(clicked);
        return new SystemNotifier.Backend(
            message -> SwingUtilities.invokeLater(() -> tray.balloon(message)),
            id -> SwingUtilities.invokeLater(() -> tray.withdraw(id)),
            () -> SwingUtilities.invokeLater(tray::withdrawAll),
            () -> SwingUtilities.invokeLater(tray::focused),
            () -> SwingUtilities.invokeLater(tray::focused));
    }

    static TrayIcon.MessageType type(SystemNotifier.Level level) {
        return switch (level) {
            case INFO -> TrayIcon.MessageType.INFO;
            case WARNING -> TrayIcon.MessageType.WARNING;
            case ERROR -> TrayIcon.MessageType.ERROR;
        };
    }

    /** The smallest image at least as large as the tray's slot, or else the largest; Windows scales it to fit. */
    static Image image(List<Image> images, Dimension slot) {
        int wanted = Math.max(slot.width, slot.height);
        return images.stream()
            .filter(image -> image.getWidth(null) >= wanted)
            .findFirst()
            .orElse(images.getLast());
    }

    /** Adds a real icon; never throws, since the icon is not worth a failed launch. */
    private static Icon addToSystemTray(Runnable action, Optional<Menu> menu) {
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu popup = null;
            if (menu.isPresent()) {
                popup = new PopupMenu();
                MenuItem openItem = new MenuItem("Open Jasper");
                openItem.addActionListener(event -> menu.get().open().run());
                MenuItem quitItem = new MenuItem("Quit Jasper");
                quitItem.addActionListener(event -> menu.get().quit().run());
                popup.add(openItem);
                popup.addSeparator();
                popup.add(quitItem);
            }
            TrayIcon icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), TOOLTIP, popup);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> action.run());
            tray.add(icon);
            return new Icon(icon::setToolTip,
                message -> icon.displayMessage(message.title(), message.body(), type(message.level())),
                () -> tray.remove(icon));
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add the notification-area icon", failure);
            return null;
        }
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/platform/SystemNotifier.java`, replace the whole `forThisPlatform` method
with:

```java
    /**
     * The notifier for this desktop: banners from a packaged macOS {@code Jasper.app}, {@code notify-send} on Linux,
     * balloons from the notification area on Windows, and nothing elsewhere, including Gradle and IDE runs on macOS,
     * which lack Jasper's bundle identity. Call on the EDT.
     *
     * @param osName the {@code os.name} property
     * @param dirs where Jasper keeps the icon {@code notify-send} shows
     * @param tray Jasper's Windows notification-area icon, which a resident Jasper shares
     */
    public static SystemNotifier forThisPlatform(String osName, AppDirs dirs, WindowsTray tray) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac"))
            return MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) == null ? none() : onWorker(SystemNotifier::mac);
        if (os.startsWith("linux"))
            return onWorker(clicked -> LinuxNotifications.backend(dirs.notificationIcon(), clicked));
        if (os.startsWith("windows")) return onWorker(clicked -> WindowsTray.backend(tray, clicked));
        return none();
    }
```

In `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`, replace

```java
import dev.jasper.app.platform.ResidentTray;
```

with

```java
import dev.jasper.app.platform.WindowsTray;
```

and replace

```java
            // Shown only once startup settles residency, and only on Windows.
            application.residentIndicator(ResidentTray::install);
```

with

```java
            // Shown only once startup settles residency, and only on Windows.
            WindowsTray tray = WindowsTray.forThisDesktop();
            application.residentIndicator(tray::resident);
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS (`WindowsTrayTest` 12/12).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src/main/java/dev/jasper/app/platform jasper-app/src/test/java/dev/jasper/app/platform jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java
git commit -m "feat(platform): one Windows tray icon for residency and notification balloons" -m "WindowsTray replaces ResidentTray: a resident Jasper's icon carries balloons, a background notification adds an icon for as long as Jasper stays out of focus, and a click activates the most recent notification still unread." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 7: Jasper's notifications reach the operating system

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/SystemNotices.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/platform/NativeNotifier.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/NativeNotifierTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, `NotificationsFeature.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`,
  `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`, `config.example.toml`, `docs/configuration.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/SystemNoticesTest.java`, `NotificationsFeatureTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/notifications/CommandNotifierTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTitleIntegrationTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`

**Interfaces:**
- Consumes:
  - Task 1's `Change.SystemRequested` and presences.
  - Task 2's `NotificationRequest.incident()`.
  - Task 4's `SystemNotifier` (`using`, `none`, `send`, `withdraw`, `withdrawAll`, `focused`, `onClick`, `close`) and
    `Level`.
  - Task 6's `WindowsTray` and three-argument `forThisPlatform`.
- Produces:
  - Package-private, `SystemNotices`:
    - `SystemNotices(NotificationCenter center, BooleanSupplier enabled, Windows windows)`
    - `record Windows(Function<Optional<UUID>, Optional<UUID>> raise, Consumer<UUID> showHistory, Consumer<UUID> showErrors)`
    - `use(SystemNotifier)`, `focused()`, `clicked(UUID)`, `close()`
    - `static SystemNotifier.Level level(Severity)`
  - `NotificationsFeature.showHistory(UUID window)`
  - `JasperApplication.systemNotifier(SystemNotifier notifier)`
  - `CoreSettings.NOTIFICATIONS_SYSTEM` (`notifications.system`, boolean, default `true`)
  - `CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center, Consumer<Boolean> onWorkingChanged, BiFunction<Duration, Runnable, Runnable> schedule)`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/SystemNoticesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class SystemNoticesTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final UUID LAST_USED = UUID.randomUUID();

    private Delivery.Presence presence = Delivery.Presence.BACKGROUND;
    private boolean enabled = true;
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
    private final List<String> sent = new ArrayList<>();
    private final List<String> withdrawn = new ArrayList<>();
    private final List<String> backendCalls = new ArrayList<>();
    private final List<Optional<UUID>> raised = new ArrayList<>();
    private final List<UUID> histories = new ArrayList<>();
    private final List<UUID> errors = new ArrayList<>();
    /** What raising a window returns: the window it raised, empty while Jasper quits. */
    private Optional<UUID> raises = Optional.of(LAST_USED);

    private SystemNotices notices() {
        center.register(CARDS);
        var notices = new SystemNotices(center, () -> enabled, new SystemNotices.Windows(target -> {
            raised.add(target);
            return raises.map(last -> target.orElse(last));
        }, histories::add, errors::add));
        notices.use(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
            message -> sent.add(message.level() + " " + message.title() + "|" + message.body()),
            withdrawn::add, () -> withdrawn.add("all"), () -> backendCalls.add("focused"), () -> backendCalls.add("close"))));
        return notices;
    }

    private UUID post(NotificationRequest request) { return center.post(request).orElseThrow(); }

    private static NotificationRequest request(Severity severity, String title) {
        return NotificationRequest.of(CARDS.id(), severity, title);
    }

    @Test void inTheBackgroundANotificationGoesToTheOperatingSystemWithItsLevelAndText() {
        notices();
        post(request(Severity.WARNING, "make").withContent("Exited 1 · 2m 03s"));
        assertThat(sent).containsExactly("WARNING make|Exited 1 · 2m 03s");
    }

    @Test void inTheForegroundTheCardSaysItAndTheOperatingSystemHearsNothing() {
        notices();
        presence = Delivery.Presence.FOCUSED;
        post(request(Severity.INFO, "make"));
        assertThat(sent).isEmpty();
    }

    /** An incident's details can name paths and hosts, and banners show on a locked screen (spec section 11). */
    @Test void anIncidentShowsOnlyItsTitle() {
        notices();
        post(request(Severity.ERROR, "Unexpected error in Jasper").withContent("IOException: /home/me/secret")
            .withIncident(UUID.randomUUID()));
        assertThat(sent).containsExactly("ERROR Unexpected error in Jasper|");
    }

    @Test void turningSystemNotificationsOffStopsSendingButStillWithdraws() {
        notices();
        UUID shown = post(request(Severity.INFO, "shown"));
        enabled = false;
        post(request(Severity.INFO, "quiet"));
        center.markRead(shown);
        assertThat(sent).containsExactly("INFO shown|");
        assertThat(withdrawn).containsExactly(shown.toString());
        assertThat(center.history()).as("history keeps both").hasSize(2);
    }

    @Test void readingExpiringRemovingAndClearingWithdrawWhatTheOperatingSystemShows() {
        notices();
        UUID read = post(request(Severity.INFO, "read"));
        UUID expired = post(request(Severity.INFO, "expired"));
        UUID removed = post(request(Severity.INFO, "removed"));
        center.markRead(read);
        center.expire(expired);
        center.remove(removed);
        center.markRead(read);
        post(request(Severity.INFO, "cleared"));
        center.clear();
        center.clear();
        assertThat(withdrawn).containsExactly(read.toString(), expired.toString(), removed.toString(), "all");
    }

    @Test void whatTheOperatingSystemNeverShowedIsNeverWithdrawn() {
        notices();
        presence = Delivery.Presence.FOCUSED;
        UUID card = post(request(Severity.INFO, "card"));
        center.markRead(card);
        center.clear();
        assertThat(withdrawn).isEmpty();
    }

    @Test void aClickRaisesItsWindowAndRunsItsDefaultAction() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        var ran = new ArrayList<String>();
        UUID id = post(request(Severity.INFO, "make").withTarget(window).withActivate(() -> ran.add("focus the pane")));
        notices.clicked(id);
        assertThat(raised).containsExactly(Optional.of(window));
        assertThat(ran).containsExactly("focus the pane");
        assertThat(center.find(id).orElseThrow().read()).isTrue();
        assertThat(withdrawn).containsExactly(id.toString());
        assertThat(histories).isEmpty();
    }

    @Test void aClickOnAnIncidentOpensTheErrorsWindowAtIt() {
        var notices = notices();
        UUID incident = UUID.randomUUID();
        UUID id = post(request(Severity.ERROR, "Unexpected error in Jasper").withIncident(incident));
        notices.clicked(id);
        assertThat(errors).containsExactly(incident);
        assertThat(center.find(id).orElseThrow().read()).isTrue();
        assertThat(histories).isEmpty();
    }

    @Test void aClickWithNothingToRunShowsTheHistoryInTheRaisedWindow() {
        var notices = notices();
        UUID id = post(request(Severity.INFO, "Theme installed"));
        notices.clicked(id);
        assertThat(raised).containsExactly(Optional.empty());
        assertThat(histories).containsExactly(LAST_USED);
        assertThat(center.find(id).orElseThrow().read()).isTrue();
    }

    @Test void aClickOnAGoneOrExpiredNotificationOnlyShowsTheHistory() {
        var notices = notices();
        var ran = new ArrayList<String>();
        UUID expired = post(request(Severity.INFO, "expired").withActivate(() -> ran.add("ran")));
        center.expire(expired);
        notices.clicked(expired);
        notices.clicked(UUID.randomUUID());
        assertThat(ran).isEmpty();
        assertThat(histories).containsExactly(LAST_USED, LAST_USED);
    }

    @Test void whileJasperQuitsAClickOpensNothing() {
        var notices = notices();
        raises = Optional.empty();
        var ran = new ArrayList<String>();
        UUID id = post(request(Severity.INFO, "late").withActivate(() -> ran.add("ran")));
        notices.clicked(id);
        assertThat(ran).isEmpty();
        assertThat(histories).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test void focusIsPassedOnAndCloseReleasesTheNotifierAndStopsListening() {
        var notices = notices();
        notices.focused();
        notices.close();
        notices.close();
        UUID after = post(request(Severity.INFO, "after"));
        notices.clicked(after);
        assertThat(backendCalls).containsExactly("focused", "close");
        assertThat(sent).isEmpty();
        assertThat(raised).isEmpty();
    }

    @Test void aNewNotifierReplacesTheOldOneWhichIsClosed() {
        var notices = notices();
        var later = new ArrayList<String>();
        notices.use(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
            message -> later.add(message.title()), id -> { }, () -> { }, () -> { }, () -> { })));
        post(request(Severity.INFO, "routed"));
        assertThat(later).containsExactly("routed");
        assertThat(backendCalls).containsExactly("close");
        assertThat(sent).isEmpty();
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java`, add before the class's closing
brace:

```java
    @Test void showHistoryShowsThePanelInAnAttachedWindowOnly() {
        var feature = new NotificationsFeature(center, contributions);
        var requests = new ArrayList<Contributions.PanelRequest>();
        contributions.onPanelRequest(requests::add);
        var window = window();
        feature.attach(window);
        feature.showHistory(window.id());
        feature.showHistory(UUID.randomUUID());
        assertThat(requests).containsExactly(
            new Contributions.PanelRequest(window.id(), NotificationsFeature.PANEL_ID, Contributions.PanelRequest.Op.SHOW));
    }
```

In `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, replace

```java
        assertThat(CoreSettings.SCHEMA.settings()).hasSize(37);
```

with

```java
        assertThat(CoreSettings.SCHEMA.settings()).hasSize(38);
```

In `jasper-app/src/test/java/dev/jasper/app/notifications/CommandNotifierTest.java`:

1. Delete the line `    private record Sent(String title, String detail) {}` and the line
   `    private final List<Sent> os = new ArrayList<>();`.
2. In `notifier(int seconds)`, replace

```java
            (title, detail) -> os.add(new Sent(title, detail)), working::add,
```

   with

```java
            working::add,
```

3. Delete every line that reads exactly `        assertThat(os).isEmpty();` (four of them).
4. Replace the whole test `theOperatingSystemHearsOnlyWhileJasperIsInTheBackground` with:

```java
    /** The operating system hears through the center's delivery (spec section 7), not from this class. */
    @Test void inTheBackgroundTheOutcomeAsksTheOperatingSystemThroughTheCenter() {
        CommandNotifier notifier = notifier(10);
        var asked = new ArrayList<String>();
        center.onChange(change -> {
            if (change instanceof NotificationCenter.Change.SystemRequested requested)
                asked.add(requested.notification().request().title());
        });
        notifier.finished("a", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        assertThat(asked).as("the card says it in the foreground").isEmpty();
        presence = Delivery.Presence.BACKGROUND;
        notifier.finished("b", "./gradlew build", OptionalInt.of(1), Duration.ofSeconds(72), HIDDEN_TAB, window, () -> {});
        assertThat(asked).containsExactly("./gradlew build");
        assertThat(center.history()).as("history and unread either way").hasSize(2);
        assertThat(center.unread()).isEqualTo(2);
    }
```

5. In `failedCommandsRetainArgumentsAndSanitizeMultilineTitles`, replace

```java
        assertThat(os).containsExactly(new Sent("printf 'hello' ↵ echo goodbye", "Exited 2 · 12s"));
```

   with

```java
        assertThat(center.history().getFirst().request().content()).isEqualTo("Exited 2 · 12s");
```

6. In `savedCallbacksAndLateEventsCannotResurrectClosedProducers`, replace

```java
            int generation = deck.generation(), sent = os.size(), changes = working.size(), posted = center.history().size();
```

   with

```java
            int generation = deck.generation(), changes = working.size(), posted = center.history().size();
```

   and delete the line `            assertThat(os).hasSize(sent);`.

In `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTitleIntegrationTest.java`:

1. Replace

```java
    private record Fixture(WindowContent content, BuddyTestSupport deck, List<String> nativeTitles,
                           List<String> notificationTitles, NotificationCenter center) { }
```

   with

```java
    private record Fixture(WindowContent content, BuddyTestSupport deck, List<String> nativeTitles,
                           NotificationCenter center) { }
```

2. Replace

```java
            List<String> notificationTitles = new ArrayList<>();
            // In the background, so the interim OS sink hears the outcome as well as the center.
            var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.BACKGROUND);
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck.companion(), center,
                (title, detail) -> notificationTitles.add(title), working -> {}, (delay, run) -> () -> {});
```

   with

```java
            // In the background, as when a command finishes out of sight.
            var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.BACKGROUND);
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck.companion(), center,
                working -> {}, (delay, run) -> () -> {});
```

3. Replace `            fixture[0] = new Fixture(window, deck, titles, notificationTitles, center);` with
   `            fixture[0] = new Fixture(window, deck, titles, center);`.
4. Delete the line `            assertThat(f.notificationTitles).containsExactly("sleep 12");`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.workspace.TerminalTitleIntegrationTest' --tests 'dev.jasper.app.config.CoreSettingsTest'`
Expected: FAIL to compile. `SystemNotices` and `NotificationsFeature.showHistory` do not exist, and `CommandNotifier`
still wants six arguments.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/application/SystemNotices.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Jasper's notifications in the operating system (spec sections 7, 9 and 11). What the center asks the operating
 * system to show while Jasper is not focused goes to the system notifier when {@code notifications.system} is on, an
 * incident's with its title only; what the operating system shows is withdrawn once it is read, expires or is removed
 * in Jasper; and a click on one raises its window and does what a click on its card would, or opens the Errors window
 * for an incident, or shows the Notifications panel. EDT only; it owns the notifier it is given, and the application
 * closes it at shutdown.
 */
final class SystemNotices implements AutoCloseable {
    /**
     * What a click needs from the application.
     *
     * @param raise brings the notification's window (empty: the one used last) to the front, opening one when none is
     *     open, and names it; empty while Jasper quits
     * @param showHistory shows the Notifications panel in that window
     * @param showErrors opens the Errors window at that incident
     */
    record Windows(Function<Optional<UUID>, Optional<UUID>> raise, Consumer<UUID> showHistory, Consumer<UUID> showErrors) {
        Windows {
            Objects.requireNonNull(raise, "raise");
            Objects.requireNonNull(showHistory, "showHistory");
            Objects.requireNonNull(showErrors, "showErrors");
        }
    }

    private final NotificationCenter center;
    private final BooleanSupplier enabled;
    private final Windows windows;
    private final Subscription listening;
    /** What the operating system shows now, so that only that is withdrawn. */
    private final Set<UUID> shown = new HashSet<>();
    private SystemNotifier notifier = SystemNotifier.none();
    private boolean closed;

    /**
     * @param center whose requests it carries out
     * @param enabled {@code notifications.system}, read at each request
     * @param windows what a click needs from the application
     */
    SystemNotices(NotificationCenter center, BooleanSupplier enabled, Windows windows) {
        this.center = Objects.requireNonNull(center, "center");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.windows = Objects.requireNonNull(windows, "windows");
        listening = center.onChange(this::changed);
    }

    /** The notifier to use from now on; the one it replaces is closed, and clicks come back to {@link #clicked}. */
    void use(SystemNotifier value) {
        Objects.requireNonNull(value, "value");
        if (closed) {
            value.close();
            return;
        }
        SystemNotifier previous = notifier;
        notifier = value;
        value.onClick(this::clicked);
        previous.close();
    }

    /** Jasper is focused again. */
    void focused() {
        if (!closed) notifier.focused();
    }

    /**
     * A click on the operating system's notification: its window comes to the front, then its default action runs; with
     * none (or once it has expired) the notification is read and the Errors window opens at its incident, or the
     * Notifications panel shows. While Jasper quits nothing opens.
     */
    void clicked(UUID id) {
        if (closed) return;
        Optional<Notification> entry = center.find(id);
        Optional<UUID> window = windows.raise().apply(entry.flatMap(found -> found.request().target()));
        if (window.isEmpty()) return;
        if (entry.isEmpty()) {
            windows.showHistory().accept(window.get());
            return;
        }
        Notification notification = entry.get();
        if (!notification.expired() && notification.request().activate().isPresent()) {
            center.activate(id);
            return;
        }
        center.markRead(id);
        Optional<UUID> incident = notification.request().incident();
        if (incident.isPresent()) windows.showErrors().accept(incident.get());
        else windows.showHistory().accept(window.get());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        notifier.close();
    }

    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.SystemRequested requested -> send(requested.notification());
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                if ((entry.read() || entry.expired()) && shown.remove(entry.id())) notifier.withdraw(entry.id());
            }
            case NotificationCenter.Change.Removed removed -> {
                if (shown.remove(removed.id())) notifier.withdraw(removed.id());
            }
            case NotificationCenter.Change.Cleared cleared -> {
                if (!shown.isEmpty()) {
                    shown.clear();
                    notifier.withdrawAll();
                }
            }
            case NotificationCenter.Change.CardRequested requested -> { }
        }
    }

    private void send(Notification notification) {
        if (!enabled.getAsBoolean()) return;
        NotificationRequest request = notification.request();
        // An incident's details can name paths and hosts, and banners show on a locked screen (spec section 11).
        String body = request.incident().isPresent() ? "" : request.content();
        notifier.send(notification.id(), level(request.severity()), request.title(), body);
        shown.add(notification.id());
    }

    static SystemNotifier.Level level(Severity severity) {
        return switch (severity) {
            case INFO -> SystemNotifier.Level.INFO;
            case WARNING -> SystemNotifier.Level.WARNING;
            case ERROR -> SystemNotifier.Level.ERROR;
        };
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java`, replace

```java
    CardLayer layer(UUID windowId) {
```

with

```java
    /** Shows the Notifications panel in that window: what clicking an OS notification with nothing else to do does. */
    void showHistory(UUID window) {
        if (closed || !windows.containsKey(window)) return;
        contributions.requestPanel(new Contributions.PanelRequest(window, PANEL_ID, Contributions.PanelRequest.Op.SHOW));
    }

    CardLayer layer(UUID windowId) {
```

In `jasper-app/src/main/java/dev/jasper/app/notifications/CommandNotifier.java`:

1. Delete the line `import java.util.function.BiConsumer;`.
2. In the class comment, replace

```java
 * <p>Until plan 5's OS notifiers, the outcome also goes to the operating system while Jasper is in the background; in
 * the foreground the card says it.
```

   with

```java
 * <p>While Jasper is in the background the center's delivery also has the operating system show the outcome (spec
 * section 7); this class does not talk to it.
```

3. Delete the line `    private BiConsumer<String, String> operatingSystem;`.
4. Delete these two lines from the constructor's comment:

```java
     * @param operatingSystem receives an outcome's title and content while Jasper is in the background (plan 5 replaces
     *     it with the OS notifiers)
```

5. Replace

```java
    public CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center,
                           BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
                           BiFunction<Duration, Runnable, Runnable> schedule) {
```

   with

```java
    public CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center,
                           Consumer<Boolean> onWorkingChanged, BiFunction<Duration, Runnable, Runnable> schedule) {
```

6. Delete the line `        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");`.
7. Delete the line `        if (center.presence() != Delivery.Presence.FOCUSED) operatingSystem.accept(title, detail);`.
8. Delete the line `        operatingSystem = (title, detail) -> {};`.

Delete `jasper-app/src/main/java/dev/jasper/app/platform/NativeNotifier.java` and
`jasper-app/src/test/java/dev/jasper/app/platform/NativeNotifierTest.java`.

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`:

1. Delete the line `import dev.jasper.app.platform.NativeNotifier;` and replace

```java
import dev.jasper.app.platform.SystemFonts;
```

   with

```java
import dev.jasper.app.platform.SystemFonts;
import dev.jasper.app.platform.SystemNotifier;
```

2. Replace `    private final NativeNotifier nativeNotifier = new NativeNotifier();` with
   `    private final SystemNotices systemNotices;`.
3. Replace

```java
        notificationViews = new NotificationsFeature(notificationCenter, contributions);
```

   with

```java
        notificationViews = new NotificationsFeature(notificationCenter, contributions);
        systemNotices = new SystemNotices(notificationCenter, this::systemNotificationsEnabled,
            new SystemNotices.Windows(this::raiseFor, notificationViews::showHistory, this::showErrors));
```

4. Replace

```java
            buddy.companion(), notificationCenter, nativeNotifier::send, buddy.companion()::setWorking, JasperApplication::afterDelay);
```

   with

```java
            buddy.companion(), notificationCenter, buddy.companion()::setWorking, JasperApplication::afterDelay);
```

5. Replace

```java
    public void residentIndicator(java.util.function.BinaryOperator<Runnable> install) {
        residentIndicator = Objects.requireNonNull(install, "install");
    }
```

   with

```java
    public void residentIndicator(java.util.function.BinaryOperator<Runnable> install) {
        residentIndicator = Objects.requireNonNull(install, "install");
    }

    /**
     * Where notifications go while Jasper is away: this desktop's banners, balloons or {@code notify-send}. Injected,
     * like {@link #residentIndicator}, so tests never reach the real operating system; the application closes it.
     */
    public void systemNotifier(SystemNotifier notifier) {
        systemNotices.use(Objects.requireNonNull(notifier, "notifier"));
    }
```

6. Replace

```java
    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }
```

   with

```java
    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }

    /**
     * Brings a terminal window to the front for a click on an OS notification: the notification's own window while it
     * is open, else the one used last, and with none open a new one. Names the window; empty while Jasper quits.
     */
    private Optional<UUID> raiseFor(Optional<UUID> target) {
        if (quitting || stopped) return Optional.empty();
        TerminalWindow window = target.flatMap(this::openWindowWith).orElse(null);
        if (window == null && lastActive != null && windows.contains(lastActive)) window = lastActive;
        if (window == null && !windows.isEmpty()) window = windows.iterator().next();
        if (window == null) window = newWindow();
        if (window == null) return Optional.empty();
        window.toFront();
        return Optional.of(window.content().id());
    }

    private Optional<TerminalWindow> openWindowWith(UUID id) {
        for (TerminalWindow window : windows) if (window.content().id().equals(id)) return Optional.of(window);
        return Optional.empty();
    }

    /** {@code notifications.system}, live: whether the operating system shows notifications while Jasper is away. */
    private boolean systemNotificationsEnabled() {
        return configuration == null || configuration.state().values().get(dev.jasper.app.config.CoreSettings.NOTIFICATIONS_SYSTEM);
    }
```

7. In `windowActivated`, replace

```java
        notificationViews.activated(window.content());
```

   with

```java
        notificationViews.activated(window.content());
        systemNotices.focused();
```

8. In `shutdown`, replace

```java
        notificationViews.close();
```

   with

```java
        notificationViews.close();
        systemNotices.close();
```

`JasperApplication` imports `java.util.*`, so `Optional` and `UUID` need no new imports.

In `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`, replace

```java
import dev.jasper.app.platform.WindowsTray;
```

with

```java
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.platform.WindowsTray;
```

and replace

```java
            application.residentIndicator(tray::resident);
```

with

```java
            application.residentIndicator(tray::resident);
            application.systemNotifier(SystemNotifier.forThisPlatform(System.getProperty("os.name"), dirs, tray));
```

In `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`:

1. Replace

```java
            + "Zero disables notifications. Range 0-3600.").build();
```

   with

```java
            + "Zero disables notifications. Range 0-3600.").build();
    public static final Setting<Boolean> NOTIFICATIONS_SYSTEM = Setting.of("notifications.system", SettingKind.bool(), true)
        .label("Show system notifications").keywords("notification", "system", "banner", "balloon", "desktop", "tray")
        .description("Live. While Jasper is in the background, also show its notifications as the operating system's\n"
            + "own: banners from the packaged macOS app, Windows tray balloons, or notify-send on Linux.").build();
```

2. Replace `NOTIFICATIONS_LONG_COMMAND_SECONDS, SYSTEM_RUN_IN_BACKGROUND,` with
   `NOTIFICATIONS_LONG_COMMAND_SECONDS, NOTIFICATIONS_SYSTEM, SYSTEM_RUN_IN_BACKGROUND,`.
3. Replace `Map.entry(List.of("notifications"), "Desktop notifications for finished commands."),` with
   `Map.entry(List.of("notifications"), "Finished commands, and what the operating system shows while Jasper is away."),`.

In `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`, replace

```java
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off."),
```

with

```java
                .row(NOTIFICATIONS_SYSTEM)
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off."),
```

In `config.example.toml`, replace

```toml
long_command_seconds = 10
```

with

```toml
long_command_seconds = 10
# Live. While Jasper is in the background, also show its notifications as the operating system's
# own: banners from the packaged macOS app, Windows tray balloons, or notify-send on Linux.
system = true
```

In `docs/configuration.md`, replace

```
[notifications]
long_command_seconds = 10
```

with

```
[notifications]
long_command_seconds = 10
system = true
```

and replace

```
| `notifications.long_command_seconds` | `10` | Integer 0–3600 | Live |
```

with

```
| `notifications.long_command_seconds` | `10` | Integer 0–3600 | Live |
| `notifications.system` | `true` | Boolean | Live |
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.workspace.TerminalTitleIntegrationTest' verifyApplicationArchitecture`
Expected: PASS (`SystemNoticesTest` 13/13, `CorePagesTest` and `ConfigTemplateTest` with the new setting,
architecture check clean).

Then run `grep -rn "NativeNotifier\|nativeNotifier\|ResidentTray\|operatingSystem" jasper-app/src --include='*.java'`.
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "feat(notifications): Jasper's notifications reach the operating system" -m "SystemNotices sends what the center asks the operating system to show when notifications.system is on (an incident's title only), withdraws what is read, expired, removed or cleared, and turns a click into the window raised and the default action, the Errors window or the Notifications panel. The long-command OS sink and NativeNotifier are gone." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 8: Documentation and full verification

**Files:**
- Modify: `docs/notifications.md`, `docs/configuration.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`,
  `jasper-app/src/main/java/dev/jasper/app/platform/package-info.java`
- Modify: `docs/STATUS.md`, `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, this plan's banner

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only. `AppDocumentationTest` checks every local link and `#anchor` outside fenced code.
  Dated records (`docs/design/`, `docs/*-verification.md`, `docs/documentation-audit.md`, old STATUS entries) keep what
  they recorded.

- [ ] **Step 1: The user guides**

In `docs/notifications.md`, before `## Shortcuts`, add:

```markdown
## System notifications

While Jasper is in the background, or running with every window closed, a notification whose group shows cards also
appears as the operating system's own: a banner on macOS, a balloon from Jasper's notification-area icon on Windows,
and a desktop notification through `notify-send` on Linux. Clicking it brings its window to the front (opening one
when none is open) and does what clicking its card would; a notification with nothing to run opens the Errors window
for an internal error or a plugin problem, or else the Notifications panel. Reading a notification in Jasper, its
expiry and its removal take the system notification away where the system allows it. While Jasper starts or a window
opens nothing is sent, since the card is about to show.

- **macOS**: only the packaged `Jasper.app` sends banners, with Jasper's icon. The first one asks for permission; allow
  Jasper in System Settings → Notifications. Gradle and IDE runs send none.
- **Windows**: the balloon comes from Jasper's icon in the notification area, which stays while Jasper is out of focus
  (and for as long as a resident Jasper has no window). A click on the balloon or on the icon opens the most recent
  unread notification.
- **Linux**: Jasper runs `notify-send` from `PATH` when there is one. When it supports actions, a click on the
  notification comes back to Jasper; otherwise the notification only informs.

An internal error or a plugin problem sends only its title, since its details can name files and hosts and system
notifications show on a locked screen. Turn system notifications off with `notifications.system = false`, or in
Settings › Notifications ([configuration](configuration.md#finished-command-notifications)).

```

In `docs/notifications.md`, replace

```
03s". Its card shows in the pane's window, and clicking the card focuses the pane (a history row has no click of its
own, so a command that finished while Jasper was in the background is found by its tab); closing the pane expires it.
```

with

```
03s". Its card shows in the pane's window, and clicking the card focuses the pane; while Jasper is in the background
its [system notification](#system-notifications) does (a history row has no click of its own). Closing the pane
expires it.
```

In `docs/configuration.md`, replace

```
In the packaged macOS `Jasper.app`, a command that finishes while Jasper is in the background also
sends a **system notification**, with Jasper's app icon and notification settings. The first eligible
notice asks for permission; allow Jasper in System Settings → Notifications. Other launches (Gradle
and IDE runs included) send none.
```

with

```
While Jasper is in the background the outcome is also a **system notification**: a banner from the
packaged macOS `Jasper.app`, a balloon from Jasper's notification-area icon on Windows, or `notify-send`
on Linux, and clicking it focuses the pane. `notifications.system = false` turns system notifications
off for everything (Settings › Notifications › Show system notifications). See
[Notifications](notifications.md#system-notifications).
```

- [ ] **Step 2: The maintenance and architecture notes**

In `docs/app-maintenance.md`, replace everything from the line `### macOS notification identity` up to (not
including) the line `### Unified workspace header` with:

```markdown
### System notifications

`application.SystemNotices` listens to the center. A `Change.SystemRequested` goes to `platform.SystemNotifier` when
`notifications.system` is on, an incident's notification with its title only. A read, expired or removed entry, and
Clear All, withdraw what the operating system shows. A click raises the notification's window, then runs its default
action, opens the Errors window for an incident, or shows the Notifications panel. `Delivery` decides when the
operating system is asked: in `BACKGROUND` and `NO_WINDOW`, never in `OPENING`.

`SystemNotifier` runs every backend call on one daemon worker (`jasper-system-notifications`, closed at shutdown),
makes text plain (no control, bidirectional-control or lone-surrogate characters; a one-line title of at most 120
characters, a body of at most 300) and hands clicks back on the EDT as notification ids. A backend is a record of
functions, so tests bring their own; `SystemNotifier.using(Runnable::run, …)` runs it on the calling thread. The
bootstrap injects the real notifier, as it does the resident icon, so tests never reach the operating system.

- **macOS**: `MacNotifications` loads `Contents/app/libjasper-notifications.dylib` beside the current jpackage
  launcher. The Objective-C bridge uses `UNUserNotificationCenter` in Jasper's process: identity, icon and permission
  belong to `dev.jasper.app`. Each banner's identifier is its notification's id, so a repeat replaces it and
  withdrawal removes it. The delegate's `didReceiveNotificationResponse` calls `MacNotifications.clicked` through the
  `JavaVM` that `JNI_OnLoad` cached, on a thread attached as a daemon. Never restore an `osascript` fallback; its
  notices carry AppleScript's icon. Java text is copied as UTF-16, never evaluated.
- **Windows**: `WindowsTray` owns Jasper's one notification-area icon, the resident icon and the balloons' icon alike;
  a click on a balloon or the icon activates the most recent unread notification.
- **Linux**: `LinuxNotifications` runs `notify-send` without a shell, asks `--help` about `--action` once (waiting at
  most 2 s), keeps at most 8 processes waiting for a click and kills one when its notification is read; tests inject
  the process starter.

`gradle/mac-notifications.gradle` compiles the bridge with the JBR JNI headers and Apple's command line tools on
macOS. `check` runs its headless fake-center harness (permission granted/denied/error, deferred delivery, literal
Unicode, identifiers, withdrawal, the click callback, foreground presentation, non-bundle guard). Other platforms skip
native compilation. Native packaging signs the dylib before signing Jasper and `verifyPackage` checks it. Gradle/IDE
runs intentionally send no macOS notifications because they lack Jasper's bundle identity. Test a packaged app
manually: run `sleep 12`, switch to another app, allow Jasper's notification request, click the banner and check that
the pane is focused; then let another finish, read it in Jasper and check that its banner goes. GUI acceptance is
user-run; headless tests never show a prompt, banner or balloon.

API references: [Apple UserNotifications](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter),
[requesting permission](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications).

```

In `docs/app-architecture.md`, replace

```
| `platform` | OS adapters, icons, fonts, title-bar paint and logging. Callers own registrations and native handles; Swing operations run on EDT and logging has its own worker. |
```

with

```
| `platform` | OS adapters, icons, fonts, title-bar paint, logging and system notifications. Callers own registrations and native handles; Swing operations run on EDT, and logging and system notifications each have their own worker. |
```

In `jasper-app/src/main/java/dev/jasper/app/platform/package-info.java`, replace

```
 * OS adapters, icons, fonts, title-bar paint and logging. Callers own registrations and native handles; Swing operations run on EDT and logging has its own worker.
```

with

```
 * OS adapters, icons, fonts, title-bar paint, logging and system notifications. Callers own registrations and native handles; Swing operations run on EDT, and logging and system notifications each have their own worker.
```

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL: every test, the four architecture gates, doclint and, on macOS, the native notification
harness.

Known flakes not caused by this branch, each documented in `docs/STATUS.md`:
- `TerminalAppIntegrationTest`'s "reflow" row-reset case;
- Remote's `ListenerTest.acceptsOnLocalhostUntilClosed`;
- Editor's `ExFilesTest.bufferSelectsATabByNumberOrName` when `java.io.tmpdir` contains "zz".

If one fails, rerun that test alone three times and the full check once more, and record it.

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

Then run the source hygiene check from `AGENTS.md` (expected: no output) and `git diff --check a23d0f15` (expected:
no output).

- [ ] **Step 4: Record the outcome**

In `docs/STATUS.md`, add a new first entry under the `## Current state` heading, with the counts from Step 3:

```markdown
- **Notifications plan 5: OS notifiers, reviewed on branch `claude/notifications-5`; awaiting the user's GUI checks and
  merge decision** (plan `docs/superpowers/plans/2026-10-03-notifications-5-os-notifiers.md`,
  [guide](notifications.md#system-notifications)): while Jasper is in the background, or a resident Jasper has every
  window closed, card and sticky notifications are also the operating system's own (`Change.SystemRequested`,
  `application.SystemNotices`, `platform.SystemNotifier`):
  - macOS banners keyed by the notification's id, withdrawn when read, with clicks back through a cached `JavaVM`;
  - Windows balloons from one `WindowsTray` icon shared with residency;
  - Linux `notify-send` with `--action` when supported, at most 8 waiting.

  A click raises the notification's window and runs its default action, or opens the Errors window for an incident,
  or shows the Notifications panel. An incident's notification names its incident (title only to the operating
  system; two incidents never coalesce). `notifications.system` (default `true`, live) is on Settings ›
  Notifications. `Delivery.Presence` gains `OPENING`, so start-up posts send no banners. `CommandNotifier`'s interim
  sink and `NativeNotifier` are gone. Spec interpretations: see the plan's list (1 to 17); plan 4's open decision is
  answered with option (c). `./gradlew check --continue`: COUNTS. No GUI launched.

  GUI checks for the user:
  - macOS (packaged app): a long command finishing with Jasper in the background shows a banner with Jasper's icon,
    and a click focuses the pane; reading it in Jasper removes the banner; an internal error's banner shows only its
    title, and a click opens the Errors window.
  - Windows: a balloon from the tray icon, the icon gone once Jasper is focused, a click on the balloon focusing the
    pane, and the resident icon carrying balloons with no window open.
  - Linux (GNOME or KDE): a notification with Jasper's icon, and a click focusing the pane.
  - `notifications.system = false` sends nothing.
```

Replace `COUNTS` with the four numbers the count printed, as `**N tests, N failures, N errors, N skipped**`, and name
any failure with its cause.

In `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, replace

```
Buddy)](../plans/2026-10-03-notifications-4-commands-buddy.md) is merged to main.
```

with

```
Buddy)](../plans/2026-10-03-notifications-4-commands-buddy.md) is merged to main. [Plan 5 (OS
notifiers)](../plans/2026-10-03-notifications-5-os-notifiers.md) is implemented on branch `claude/notifications-5`.
```

In this plan, replace the `**Status:** Not started. …` paragraph with one that:
- says the plan is implemented on branch `claude/notifications-5`;
- lists every deviation from the plan text made during execution, each with its task and reason;
- lists the final review's fixes;
- gives the Step 3 counts.

Never write a triple backtick inside a paragraph: `AppDocumentationTest` pairs fences by counting them.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --rerun`
Expected: PASS: every link, anchor and compiled example matches (`--rerun`, because documentation files are not task
inputs).

- [ ] **Step 5: Commit**

```bash
git add docs jasper-app/src/main/java/dev/jasper/app/platform/package-info.java
git commit -m "docs(notifications): system notifications on macOS, Windows and Linux" -m "The notifications and configuration guides describe system notifications and notifications.system; the maintenance and architecture notes describe SystemNotices, SystemNotifier and its three backends; STATUS, the spec and the plan record plan 5." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
