# Notifications Plan 1: Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on branch `claude/notifications` (worktree `.worktrees/notifications`, cut from main
`e91512f6`); `./gradlew check` green (8,076 tests, 0 failures, 0 errors, 6 skips); awaiting the final
whole-branch review and the user's GUI checks (listed in [STATUS](../../STATUS.md)). The editor plugin's
`ExFilesTest` fails on a temp path containing `zz` (main fixed it in `185a5f8e`); the check ran with `java.io.tmpdir`
elsewhere, and merging main removes the need.

**Execution deviations** (the plan text below was amended where noted):

1. Task 3: `NotificationCenter.activate(id)` marks every unexpired entry read and runs its default action only when
   present, since the spec says a body click marks it read (`a259123b`, `24d65f14`; plan text for Tasks 3, 5 and 11
   amended in `36f46e9c`).
2. Task 5: every component that shows notification text is built empty, given `html.disable`, then `setText`, because
   setting the property after construction left HTML views in place; the card picks its visible action links by
   measured width (up to three, fewer beside `More ▾`) so wide labels never push `More ▾` out of the row
   (`f25ff87b`, `de35fd85`).
3. Task 7: added a test pinning the corner layer's resize listeners and their removal (`660f6f17`, test only).
4. Task 8: `WrappedText` derives `Font.PLAIN` / `Font.BOLD` explicitly (Metal's `Label.font` is bold); New lists every
   unread entry plus entries read by looking until the panel hides, so an unfocused window's unread rows sit under New,
   and an entry read elsewhere while the panel shows stays under New until it hides; section accessors read the
   displayed rows, and wrap and theme-rebuild tests were added (`ca783db3`, `9f4d9bb0`; guide text amended in
   `32ba66c1`).
5. Task 9: after a failed shell launch the window's only pane stays open showing its failure, because without the old
   blocking dialog closing that pane closed the window and, by default, Jasper before the error could be seen; any
   other failed pane still closes (`e6cfec3d`, `c9e13b76`; spec section 7 amended in `fc670a51`).
6. Task 10: `windowClosed` guards `detach` against the null window existing tests pass; window errors and info target
   the posting window (`NotificationsFeature.error` / `info(title, details, windowId)` with `withTarget`), per spec
   section 7 "the poster's window, else the terminal window used last"; headless app-level wiring tests were added
   (`58ca9237`, `34d624fd`).

Final-review fixes (after the plan's tasks):

7. `JasperApplication.presence()` reports `NO_WINDOW` until an open terminal window has been activated at least once
   (reset when the last window closes), through the pure `presence(windowsOpen, activatedSinceOpen,
   jasperWindowActive)`: a failure raised while the first window opens is queued ahead of the native activation event
   and was classed background, so its card never showed (`d2bd488f`).
8. `NotificationCenter.post` drops a repeat's id from the waiting set when its outcome is a card, so `focusReturned()`
   cannot request that card a second time (`08a2c9ff`).
9. `CardLayer` renders each card's shadow once into an image keyed by card size, arc, margin, colour with alpha and
   device scale, and draws it on later paints, instead of twelve antialiased rings per card on every repaint
   (`498e4d61`).

**Goal:** Replace every modal message dialog with an in-memory notification center whose notifications appear as
themed cards in the window's bottom-right corner and in a built-in Notifications rail panel with an unread badge.

**Architecture:** A Swing-free, EDT-confined `NotificationCenter` in `dev.jasper.app.notifications` holds groups, the
history and read/expired state, and routes each post through the pure `Delivery` rule. A new
`dev.jasper.app.notificationui` package renders it: one `CardLayer` per window (mounted by `WindowContent` on the root's
palette layer) and a `HistoryPanel` per window. `dev.jasper.app.application.NotificationsFeature` composes them,
registers the built-in panel and its actions, keeps the rail badge current and routes cards to windows.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing with FlatLaf, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 1 of the six in its section 14). Read it before starting.

## Global Constraints

- Build and test only with `./gradlew` on JBR 25; tests are headless. Never launch the GUI or the benchmark.
- **No animations anywhere.** Cards appear and disappear instantly; nothing fades, slides, blinks or pulses. The only
  timers are the auto-hide countdowns.
- Notification text is **plain text, never HTML**: every `JLabel`, `JButton` and `JMenuItem` that shows notification
  text sets the client property `"html.disable"` to `Boolean.TRUE`; card text is painted with `drawString`.
- Limits (spec sections 2–4): title 200 characters, content 4,000, at most 8 actions, history 500 entries,
  coalescing window 60 s, at most 3 visible cards per window, card width 360 logical px, 5 content lines, auto-hide
  after 10 s of focused, unhovered time, 12 px inset from the content corner.
- The center is EDT-confined; `NotificationCenter.submit` is the only any-thread entry point.
- Package graph (checked by `verifyApplicationArchitecture`): `notificationui → notifications`, never the reverse;
  `workspace` imports neither `notifications` nor `notificationui`; no SDK types outside `dev.jasper.app.plugins`
  (this plan adds none). Update each touched package's `package-info.java` "Allowed outgoing" list.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java source. Write
  `"…"` (ellipsis), `"×"` (multiplication sign), `"▾"` (small down triangle), `"·"` (middle dot).
- Tests: inject clocks and schedulers; never sleep. EDT tests use `@ExtendWith(EdtTestExtension.class)`;
  `@BeforeEach` / `@AfterEach` do **not** run on the EDT.
- Work on branch `claude/notifications`; one commit per task; every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

- Merge-conflict risk: another session has uncommitted changes in the main checkout to `WindowCommandPalette.java`,
  `WindowContent.java` and `PaletteController.java` (branch `palette-remove-scope-hints`). Keep edits to those files
  minimal and exactly as written here.

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **Over-long titles are cut, not rejected, in the app model** (`…` at 200 characters). Rejecting belongs to the SDK
   boundary (plan 3); app producers can carry data such as command lines in titles.
2. **No window open** is its own presence (`Delivery.Presence.NO_WINDOW`): `CARD` and `STICKY` notifications both wait
   and show when a window is next focused (spec section 7, "Target window"). Background delivery adds nothing beyond
   history until plan 5 adds OS notifications.
3. **The card inset is the layer's margin.** `WindowContent` keeps the layer flush with the content corner; the layer's
   12 px margin is both the spec's inset and the room for the cards' shadow.
4. **Colours derive in `ChromeKeys`** from IntelliJ keys that Jasper themes generate from the spec's tokens
   (`PopupMenu.background` = `SURFACE_RAISED`, `Popup.borderColor` = `BORDER`, `Link.activeForeground` = `LINK`), with
   `Notification.*` winning when a theme authors them. New badge keys: `Jasper.railBadgeAccent`, `Jasper.railBadgeError`.
5. **Plugin `Notices.error` messages** (unchanged until plan 3) post to `jasper.general` through
   `NotificationRequest.message`, which splits a message of unknown shape into a title and content.
6. **`WindowContent.onError` / `onInfo` become `BiConsumer<String, String>`** (title, details), so every window message
   has a real title.
7. **One configuration notification at a time:** a newer one expires the older.
8. **History rows show the repeat count beside the title** as a muted `×N` label (cards do the same).

## Review Focus

1. **Exception text as details** — a window error whose details are `null`, multi-line, or contain `<html>` must post
   a plain, non-null notification and render literally. Tests: Task 1 `contentKeepsLinesAndIsBounded`, Task 5
   `titleContentAndCountArePlainWrappedText`, Task 10 `errorsWithoutDetailsStillPost`.
2. **The window holding a card closes** — its cards go away without errors and later cards find another window.
   Test: Task 10 `aClosedWindowsCardsGoAndLaterOnesFindAnotherWindow`.
3. **The theme changes while a card is showing** — the card re-reads its colours. Test: Task 5
   `theCardRestylesWhenTheThemeChanges`.
4. **A burst of identical errors** (a broken theme on every reload) — one entry with a rising `×N` and one card, not a
   pile. Tests: Task 3 `identicalPostsWithinAMinuteCoalesce`, Task 6 `aRepeatRefreshesTheSameCardAndRestartsItsCountdown`.
5. **Problems found before any window exists** (config errors at startup) — they show when the first window is
   focused. Test: Task 10 `notificationsFromBeforeTheFirstWindowShowWhenItIsFocused`.

## File structure

| File | Responsibility |
|---|---|
| `jasper-app/src/main/java/dev/jasper/app/notifications/Severity.java` | INFO / WARNING / ERROR |
| `.../notifications/DisplayMode.java` | CARD / STICKY / HISTORY / OFF |
| `.../notifications/NotificationGroup.java` | Group value with id validation |
| `.../notifications/NotificationAction.java` | Labelled action value |
| `.../notifications/NotificationText.java` | Plain-text normalization and cutting (package-private) |
| `.../notifications/NotificationRequest.java` | What a producer posts |
| `.../notifications/Notification.java` | One history entry |
| `.../notifications/Delivery.java` | Pure routing rule |
| `.../notifications/NotificationCenter.java` | The EDT store, coalescing, read/expire, pending cards, listeners |
| `.../contributions/PanelBadge.java` | Generic rail badge value |
| `.../contributions/PanelEntry.java` (modify) | Holds the badge |
| `.../workspace/BadgedIcon.java` | Paints the badge dot |
| `.../workspace/WindowRail.java`, `WindowContributions.java` (modify) | Render badges |
| `.../appearance/ChromeKeys.java` (modify) | Badge and card colours |
| `.../notificationui/package-info.java` | Package contract |
| `.../notificationui/TextBlock.java` | Wrapped, cut, painted plain text |
| `.../notificationui/NotificationStyle.java` | Shared icons, colours, link buttons |
| `.../notificationui/NotificationCard.java` | One card |
| `.../notificationui/WindowFocus.java` | Tracks a component's window activation |
| `.../notificationui/CardLayer.java` | A window's card stack |
| `.../notificationui/WrappedText.java` | Wrapping read-only text area for history rows |
| `.../notificationui/HistoryRow.java` | One history row |
| `.../notificationui/HistoryPanel.java` | The Notifications panel view |
| `.../workspace/WindowContent.java` (modify) | Corner mount point; titled message sinks |
| `.../application/ConfigurationController.java` (modify) | Titled failures; problem publication |
| `.../application/NotificationsFeature.java` | Composition: groups, panel, actions, badge, card routing |
| `.../application/ConfigProblemNotices.java` | Configuration-problem producer |
| `.../application/JasperApplication.java` (modify) | Wiring |
| `docs/notifications.md` | User guide |

---

### Task 1: Notification values

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Severity.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/DisplayMode.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationGroup.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationAction.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationText.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Notification.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java`

**Interfaces:**
- Produces: `enum Severity { INFO, WARNING, ERROR }`; `enum DisplayMode { CARD, STICKY, HISTORY, OFF }`;
  `record NotificationGroup(String id, String name, String owner, DisplayMode defaultMode)`;
  `record NotificationAction(String label, Runnable run, boolean expires)` with `static of(String, Runnable)`;
  `record NotificationRequest(String group, Severity severity, String title, String content, Optional<Icon> icon,
  List<NotificationAction> actions, Optional<Runnable> activate, String source, Optional<UUID> target)` with constants
  `MAX_TITLE = 200`, `MAX_CONTENT = 4_000`, `MAX_ACTIONS = 8`, `JASPER = "jasper"`, package-private
  `SHORT_TITLE = 120`, factories `of(String group, Severity, String title)` and `message(String group, Severity, String text)`,
  withers `withContent(String)`, `withIcon(Icon)`, `withAction(NotificationAction)`, `withActivate(Runnable)`,
  `withSource(String)`, `withTarget(UUID)`, package-private `boolean sameText(NotificationRequest)`;
  `record Notification(UUID id, NotificationRequest request, Instant posted, int count, boolean read, boolean expired)`
  with package-private `withRead(boolean)`, `withExpired()`, `repeated(Instant)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationValuesTest {
    private static NotificationRequest titled(String title) {
        return NotificationRequest.of("jasper.general", Severity.INFO, title);
    }

    @Test void titlesAreOnePlainTrimmedLine() {
        assertThat(titled("  Could not\r\n start\tthe   shell \u0007 ").title()).isEqualTo("Could not start the shell");
        assertThat(titled("<html><b>bold</b>").title()).as("markup is kept as text").isEqualTo("<html><b>bold</b>");
    }

    @Test void longTitlesAreCutWithAnEllipsisWithoutSplittingAPair() {
        assertThat(titled("a".repeat(250)).title()).hasSize(NotificationRequest.MAX_TITLE).endsWith("…");
        assertThat(titled("a".repeat(198) + "😀" + "bb").title()).isEqualTo("a".repeat(198) + "…");
    }

    @Test void contentKeepsLinesAndIsBounded() {
        assertThat(titled("t").withContent("first\r\nsecond\rthird\tend\u0000").content()).isEqualTo("first\nsecond\nthird end");
        assertThat(titled("t").withContent("x".repeat(5000)).content())
            .hasSize(NotificationRequest.MAX_CONTENT).endsWith("…");
        assertThat(titled("t").withContent("<html><i>literal</i>").content()).isEqualTo("<html><i>literal</i>");
    }

    @Test void requestsRejectWhatCannotBeShown() {
        assertThatThrownBy(() -> titled(" \n\t")).isInstanceOf(IllegalArgumentException.class);
        var eight = titled("t");
        for (int i = 0; i < NotificationRequest.MAX_ACTIONS; i++) eight = eight.withAction(NotificationAction.of("Action " + i, () -> {}));
        var full = eight;
        assertThatThrownBy(() -> full.withAction(NotificationAction.of("One more", () -> {})))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationAction.of(" ", () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> titled("t").withSource(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aMessageOfUnknownShapeBecomesATitleAndContent() {
        var shortOne = NotificationRequest.message("jasper.general", Severity.ERROR, "Remote: Could not connect");
        assertThat(shortOne.title()).isEqualTo("Remote: Could not connect");
        assertThat(shortOne.content()).isEmpty();
        String wordy = "Vault: " + "word ".repeat(40);
        var cut = NotificationRequest.message("jasper.general", Severity.ERROR, wordy);
        assertThat(cut.title()).hasSize(NotificationRequest.SHORT_TITLE).endsWith("…");
        assertThat(cut.content()).isEqualTo(wordy.strip());
        var lines = NotificationRequest.message("jasper.general", Severity.ERROR, "Could not start terminal in /tmp:\nNo such file");
        assertThat(lines.title()).isEqualTo("Could not start terminal in /tmp:");
        assertThat(lines.content()).isEqualTo("Could not start terminal in /tmp:\nNo such file");
        assertThat(NotificationRequest.message("jasper.general", Severity.ERROR, "  ").title()).isEqualTo("No message");
    }

    @Test void groupIdsAreNamespaced() {
        assertThat(new NotificationGroup("jasper.general", "Jasper", "jasper", DisplayMode.CARD).id()).isEqualTo("jasper.general");
        for (String bad : List.of("jasper", "Jasper.general", "jasper..x", ".x", "x.y z"))
            assertThatThrownBy(() -> new NotificationGroup(bad, "Name", "jasper", DisplayMode.CARD)).as(bad)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationGroup("jasper.x", " ", "jasper", DisplayMode.CARD))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void entriesTrackRepeatsReadAndExpiry() {
        var entry = new Notification(UUID.randomUUID(), titled("t"), Instant.EPOCH, 1, false, false);
        var later = Instant.EPOCH.plusSeconds(5);
        var repeated = entry.withRead(true).repeated(later);
        assertThat(repeated.count()).isEqualTo(2);
        assertThat(repeated.read()).as("a repeat is news again").isFalse();
        assertThat(repeated.posted()).isEqualTo(later);
        assertThat(repeated.withExpired().expired()).isTrue();
        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), titled("t"), Instant.EPOCH, 0, false, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationValuesTest'`
Expected: FAIL — compilation errors (`NotificationRequest`, `Severity`, … do not exist).

- [ ] **Step 3: Write the implementation**

`Severity.java`:

```java
package dev.jasper.app.notifications;

/** How serious a notification is; it picks the default icon and the rail badge's tone. */
public enum Severity { INFO, WARNING, ERROR }
```

`DisplayMode.java`:

```java
package dev.jasper.app.notifications;

/** How a group's notifications appear: an auto-hiding card, a sticky card, history only, or not at all. */
public enum DisplayMode { CARD, STICKY, HISTORY, OFF }
```

`NotificationGroup.java`:

```java
package dev.jasper.app.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of notification the user can tune: a namespaced id, the name Settings and history show, who declared it
 * ({@code jasper} or a plugin id) and how its notifications appear until the user says otherwise.
 */
public record NotificationGroup(String id, String name, String owner, DisplayMode defaultMode) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    public NotificationGroup {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced group id: " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A group needs a name");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A group needs an owner");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
```

`NotificationText.java`:

```java
package dev.jasper.app.notifications;

/** Normalizes notification text: plain, bounded, never split inside a surrogate pair. Pure. */
final class NotificationText {
    static final String ELLIPSIS = "…";

    private NotificationText() {}

    /** One line: breaks and tabs become spaces, other control characters go, runs of spaces collapse, ends trimmed. */
    static String line(String text, int max) {
        var out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { space = true; continue; }
            if (Character.isISOControl(c)) continue;
            if (space && !out.isEmpty()) out.append(' ');
            space = false;
            out.append(c);
        }
        return cut(out.toString(), max);
    }

    /** Several lines: CR LF and CR become LF, tabs become spaces, other control characters go, ends trimmed. */
    static String block(String text, int max) {
        String lines = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        var out = new StringBuilder(lines.length());
        for (int i = 0; i < lines.length(); i++) {
            char c = lines.charAt(i);
            if (c == '\n' || !Character.isISOControl(c)) out.append(c);
        }
        return cut(out.toString().strip(), max);
    }

    /** {@code text}, or its first characters and an ellipsis in {@code max}; a surrogate pair is kept whole or dropped. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
```

`NotificationAction.java`:

```java
package dev.jasper.app.notifications;

import java.util.Objects;

/**
 * A labelled link on a card and history row. {@code run} runs on the EDT; with {@code expires} the notification
 * expires afterwards, so its other actions stop working.
 */
public record NotificationAction(String label, Runnable run, boolean expires) {
    static final int MAX_LABEL = 40;

    public NotificationAction {
        label = NotificationText.line(Objects.requireNonNull(label, "label"), MAX_LABEL);
        if (label.isEmpty()) throw new IllegalArgumentException("An action needs a label");
        Objects.requireNonNull(run, "run");
    }

    /** An action that expires its notification after running. */
    public static NotificationAction of(String label, Runnable run) {
        return new NotificationAction(label, run, true);
    }
}
```

`NotificationRequest.java`:

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
 * names the window whose card it is, empty for the window the user used last.
 */
public record NotificationRequest(String group, Severity severity, String title, String content, Optional<Icon> icon,
                                  List<NotificationAction> actions, Optional<Runnable> activate, String source,
                                  Optional<UUID> target) {
    public static final int MAX_TITLE = 200;
    public static final int MAX_CONTENT = 4_000;
    public static final int MAX_ACTIONS = 8;
    public static final String JASPER = "jasper";
    /** The room a title gets when it is only the opening of a longer message. */
    static final int SHORT_TITLE = 120;

    public NotificationRequest {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(severity, "severity");
        title = NotificationText.line(Objects.requireNonNull(title, "title"), MAX_TITLE);
        if (title.isEmpty()) throw new IllegalArgumentException("A notification needs a title");
        content = NotificationText.block(Objects.requireNonNull(content, "content"), MAX_CONTENT);
        Objects.requireNonNull(icon, "icon");
        actions = List.copyOf(actions);
        if (actions.size() > MAX_ACTIONS) throw new IllegalArgumentException("A notification has at most " + MAX_ACTIONS + " actions");
        Objects.requireNonNull(activate, "activate");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("A notification needs a source");
        Objects.requireNonNull(target, "target");
    }

    /** A notification of Jasper's own with only a title. */
    public static NotificationRequest of(String group, Severity severity, String title) {
        return new NotificationRequest(group, severity, title, "", Optional.empty(), List.of(), Optional.empty(), JASPER, Optional.empty());
    }

    /**
     * A message of unknown shape, such as a plugin's one-or-two-sentence notice: a short single line is the title;
     * otherwise its first line, cut, is the title and the whole message the content.
     */
    public static NotificationRequest message(String group, Severity severity, String text) {
        String whole = NotificationText.block(Objects.requireNonNull(text, "text"), MAX_CONTENT);
        if (whole.isEmpty()) return of(group, severity, "No message");
        int newline = whole.indexOf('\n');
        String first = newline < 0 ? whole : whole.substring(0, newline);
        if (newline < 0 && first.length() <= SHORT_TITLE) return of(group, severity, first);
        return of(group, severity, NotificationText.cut(first, SHORT_TITLE)).withContent(whole);
    }

    public NotificationRequest withContent(String value) {
        return new NotificationRequest(group, severity, title, value, icon, actions, activate, source, target);
    }

    public NotificationRequest withIcon(Icon value) {
        return new NotificationRequest(group, severity, title, content, Optional.of(value), actions, activate, source, target);
    }

    public NotificationRequest withAction(NotificationAction action) {
        var more = new ArrayList<>(actions);
        more.add(Objects.requireNonNull(action, "action"));
        return new NotificationRequest(group, severity, title, content, icon, more, activate, source, target);
    }

    /** The default action, run when the user clicks the card's body or its OS notification. */
    public NotificationRequest withActivate(Runnable value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, Optional.of(value), source, target);
    }

    public NotificationRequest withSource(String value) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, value, target);
    }

    public NotificationRequest withTarget(UUID window) {
        return new NotificationRequest(group, severity, title, content, icon, actions, activate, source, Optional.of(window));
    }

    /** Whether {@code other} reads the same: same group, severity, title and content. */
    boolean sameText(NotificationRequest other) {
        return group.equals(other.group) && severity == other.severity && title.equals(other.title) && content.equals(other.content);
    }
}
```

`Notification.java`:

```java
package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One history entry. {@code posted} is the latest post's time and {@code count} how many identical posts were
 * coalesced into it. Expired entries keep their place in history with their actions disabled.
 */
public record Notification(UUID id, NotificationRequest request, Instant posted, int count, boolean read, boolean expired) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(posted, "posted");
        if (count < 1) throw new IllegalArgumentException("A notification was posted at least once");
    }

    Notification withRead(boolean value) { return new Notification(id, request, posted, count, value, expired); }

    Notification withExpired() { return new Notification(id, request, posted, count, read, true); }

    /** The same text posted again: counted, re-stamped and unread again. */
    Notification repeated(Instant at) { return new Notification(id, request, at, count + 1, false, expired); }
}
```

Replace `notifications/package-info.java` with:

```java
/**
 * EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its
 * values, plus terminal and activity notice production for Buddy, active producer identities, attention and
 * visibility policy. The application owns the center and closes notifiers before the companion.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle, dev.jasper.buddy.notice, dev.jasper.buddy.view.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.notifications;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationValuesTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java
git commit -m "feat(notifications): add notification values" -m "Severity, display modes, groups, actions, requests and history entries with plain, bounded text." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The delivery rule

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Delivery.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/DeliveryTest.java`

**Interfaces:**
- Consumes: `DisplayMode` (Task 1).
- Produces: `Delivery.Presence { FOCUSED, BACKGROUND, NO_WINDOW }`; `record Delivery.Outcome(boolean record, boolean card,
  boolean pending)`; `static Delivery.Outcome Delivery.route(DisplayMode mode, Delivery.Presence presence)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.notifications;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {
    @ParameterizedTest(name = "{0} while {1}")
    @CsvSource({
        "OFF, FOCUSED, false, false, false", "OFF, BACKGROUND, false, false, false", "OFF, NO_WINDOW, false, false, false",
        "HISTORY, FOCUSED, true, false, false", "HISTORY, BACKGROUND, true, false, false", "HISTORY, NO_WINDOW, true, false, false",
        "CARD, FOCUSED, true, true, false", "CARD, BACKGROUND, true, false, false", "CARD, NO_WINDOW, true, false, true",
        "STICKY, FOCUSED, true, true, false", "STICKY, BACKGROUND, true, false, true", "STICKY, NO_WINDOW, true, false, true"})
    void everyModeAndPresence(DisplayMode mode, Delivery.Presence presence, boolean record, boolean card, boolean pending) {
        assertThat(Delivery.route(mode, presence)).isEqualTo(new Delivery.Outcome(record, card, pending));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.DeliveryTest'`
Expected: FAIL — `Delivery` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package dev.jasper.app.notifications;

/**
 * Where a posted notification goes (spec section 7). Pure, so the rule is testable without a window. While Jasper is
 * in the background an auto-hiding card would be stale on return, so it only enters history; a sticky one waits. With
 * no window open, both wait for the first window to be focused.
 */
public final class Delivery {
    private Delivery() {}

    /** Whether a Jasper window is focused, Jasper is behind another application, or no Jasper window is open. */
    public enum Presence { FOCUSED, BACKGROUND, NO_WINDOW }

    /**
     * @param record whether it enters the history
     * @param card whether a card shows now
     * @param pending whether a card waits until Jasper is next focused
     */
    public record Outcome(boolean record, boolean card, boolean pending) {
        static final Outcome DROP = new Outcome(false, false, false);
        static final Outcome HISTORY = new Outcome(true, false, false);
        static final Outcome CARD = new Outcome(true, true, false);
        static final Outcome PENDING = new Outcome(true, false, true);
    }

    public static Outcome route(DisplayMode mode, Presence presence) {
        return switch (mode) {
            case OFF -> Outcome.DROP;
            case HISTORY -> Outcome.HISTORY;
            case CARD -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND -> Outcome.HISTORY;
                case NO_WINDOW -> Outcome.PENDING;
            };
            case STICKY -> presence == Presence.FOCUSED ? Outcome.CARD : Outcome.PENDING;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.DeliveryTest'`
Expected: PASS (12 cases).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/Delivery.java jasper-app/src/test/java/dev/jasper/app/notifications/DeliveryTest.java
git commit -m "feat(notifications): add the delivery rule" -m "Routes each display mode by whether Jasper is focused, in the background or windowless." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: The notification center

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterThreadTest.java`

**Interfaces:**
- Consumes: Task 1 values, `Delivery` (Task 2), `dev.jasper.app.lifecycle.Subscription(Runnable)`.
- Produces (all EDT-only unless stated): `NotificationCenter(Executor ui, Clock clock, Supplier<Delivery.Presence> presence)`;
  `HISTORY_LIMIT = 500`; `COALESCE = Duration.ofSeconds(60)`; `Clock clock()` (any thread);
  `NotificationGroup register(NotificationGroup)`; `Optional<NotificationGroup> group(String)`;
  `List<NotificationGroup> groups()`; `DisplayMode mode(String groupId)`; `Optional<UUID> post(NotificationRequest)`;
  `void submit(NotificationRequest)` (any thread); `Optional<Notification> find(UUID)`;
  `List<Notification> history()` (newest first); `int unread()`; `int unreadErrors()`; `void markRead(UUID)`;
  `void markAllRead()`; `void expire(UUID)`; `void remove(UUID)`; `void clear()`; `void runAction(UUID, int index)`;
  `void activate(UUID)`; `void focusReturned()`; `Subscription onChange(Consumer<NotificationCenter.Change>)`;
  sealed `NotificationCenter.Change` with records `Upserted(Notification notification)`,
  `CardRequested(Notification notification)`, `Removed(UUID id)`, `Cleared()`.

- [ ] **Step 1: Write the failing tests**

`NotificationCenterTest.java`:

```java
package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class NotificationCenterTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final NotificationGroup STICKY = new NotificationGroup("test.sticky", "Sticky", "jasper", DisplayMode.STICKY);
    private static final NotificationGroup QUIET = new NotificationGroup("test.quiet", "Quiet", "jasper", DisplayMode.HISTORY);
    private static final NotificationGroup MUTED = new NotificationGroup("test.muted", "Muted", "jasper", DisplayMode.OFF);

    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private final List<NotificationCenter.Change> changes = new ArrayList<>();

    private NotificationCenter center() {
        var center = new NotificationCenter(Runnable::run, clock, () -> presence);
        for (var group : List.of(CARDS, STICKY, QUIET, MUTED)) center.register(group);
        center.onChange(changes::add);
        return center;
    }

    private static NotificationRequest request(NotificationGroup group, String title) {
        return NotificationRequest.of(group.id(), Severity.INFO, title);
    }

    private List<String> kinds() { return changes.stream().map(change -> change.getClass().getSimpleName()).toList(); }

    @Test void aFocusedCardIsRecordedThenRequested() {
        var center = center();
        UUID id = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(kinds()).containsExactly("Upserted", "CardRequested");
        assertThat(center.history()).extracting(Notification::id).containsExactly(id);
        assertThat(center.unread()).isEqualTo(1);
    }

    @Test void backgroundCardsOnlyEnterHistory() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(CARDS, "a"));
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted");
    }

    @Test void stickyCardsWaitForFocusOnce() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(STICKY, "s"));
        assertThat(kinds()).containsExactly("Upserted");
        presence = Delivery.Presence.FOCUSED;
        center.focusReturned();
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "CardRequested");
    }

    @Test void cardsPostedWithNoWindowWaitForTheFirstFocus() {
        var center = center();
        presence = Delivery.Presence.NO_WINDOW;
        center.post(request(CARDS, "early"));
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "CardRequested");
    }

    @Test void aWaitingCardReadMeanwhileNeverShows() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID id = center.post(request(STICKY, "s")).orElseThrow();
        center.markRead(id);
        center.focusReturned();
        assertThat(kinds()).doesNotContain("CardRequested");
    }

    @Test void historyOnlyMutedAndUndeclaredGroups() {
        var center = center();
        center.post(request(QUIET, "q"));
        assertThat(kinds()).containsExactly("Upserted");
        assertThat(center.post(request(MUTED, "m"))).isEmpty();
        assertThat(center.post(NotificationRequest.of("test.unknown", Severity.INFO, "u"))).isEmpty();
        assertThat(center.history()).hasSize(1);
        assertThat(center.mode("test.unknown")).isEqualTo(DisplayMode.OFF);
    }

    @Test void registerKeepsTheFirstDeclaration() {
        var center = center();
        var again = center.register(new NotificationGroup("test.cards", "Other", "dev.example", DisplayMode.STICKY));
        assertThat(again).isSameAs(CARDS);
        assertThat(center.groups()).containsExactly(CARDS, STICKY, QUIET, MUTED);
    }

    @Test void identicalPostsWithinAMinuteCoalesce() {
        var center = center();
        UUID first = center.post(request(CARDS, "a")).orElseThrow();
        center.markRead(first);
        clock.advance(Duration.ofSeconds(30));
        UUID second = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(center.find(first).orElseThrow()).satisfies(entry -> {
            assertThat(entry.count()).isEqualTo(2);
            assertThat(entry.read()).isFalse();
            assertThat(entry.posted()).isEqualTo(clock.instant());
        });
        assertThat(center.history()).hasSize(1);
        assertThat(kinds().stream().filter("CardRequested"::equals)).hasSize(2);
        clock.advance(Duration.ofSeconds(61));
        UUID third = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(third).isNotEqualTo(first);
        assertThat(center.history()).extracting(Notification::id).containsExactly(third, first);
    }

    @Test void aRepeatMovesToTheTop() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        UUID b = center.post(request(CARDS, "b")).orElseThrow();
        clock.advance(Duration.ofSeconds(10));
        center.post(request(CARDS, "a"));
        assertThat(center.history()).extracting(Notification::id).containsExactly(a, b);
    }

    @Test void expiredOrDifferentEntriesAreNotRepeats() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.expire(a);
        assertThat(center.post(request(CARDS, "a")).orElseThrow()).isNotEqualTo(a);
        assertThat(center.post(request(CARDS, "a").withContent("other")).orElseThrow()).isNotEqualTo(a);
        assertThat(center.history()).hasSize(3);
    }

    @Test void historyKeepsTheNewest500() {
        var center = center();
        UUID oldest = center.post(request(CARDS, "title 0")).orElseThrow();
        for (int i = 1; i <= NotificationCenter.HISTORY_LIMIT; i++) center.post(request(CARDS, "title " + i));
        assertThat(center.history()).hasSize(NotificationCenter.HISTORY_LIMIT);
        assertThat(center.find(oldest)).isEmpty();
        assertThat(changes).contains(new NotificationCenter.Change.Removed(oldest));
    }

    @Test void unreadCountsFollowReadState() {
        var center = center();
        center.post(request(CARDS, "info"));
        center.post(NotificationRequest.of(CARDS.id(), Severity.ERROR, "error"));
        assertThat(center.unread()).isEqualTo(2);
        assertThat(center.unreadErrors()).isEqualTo(1);
        center.markAllRead();
        assertThat(center.unread()).isZero();
        assertThat(center.unreadErrors()).isZero();
    }

    @Test void actionsRunReadAndExpire() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID id = center.post(request(CARDS, "act")
            .withAction(NotificationAction.of("Fix", () -> ran.add("fix")))
            .withAction(new NotificationAction("Keep", () -> ran.add("keep"), false))).orElseThrow();
        center.runAction(id, 1);
        assertThat(ran).containsExactly("keep");
        assertThat(center.find(id).orElseThrow()).satisfies(entry -> { assertThat(entry.read()).isTrue(); assertThat(entry.expired()).isFalse(); });
        center.runAction(id, 0);
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
        center.runAction(id, 0);
        center.runAction(id, 1);
        assertThat(ran).as("an expired entry's actions no longer run").containsExactly("keep", "fix");
    }

    @Test void aFailingActionIsContainedAndStillExpires() {
        var center = center();
        UUID id = center.post(request(CARDS, "boom").withAction(NotificationAction.of("Boom", () -> { throw new IllegalStateException("boom"); })))
            .orElseThrow();
        assertThatCode(() -> center.runAction(id, 0)).doesNotThrowAnyException();
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
    }

    @Test void activateReadsTheEntryAndRunsItsDefaultActionWhenThereIsOne() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID withDefault = center.post(request(CARDS, "go").withActivate(() -> ran.add("go"))).orElseThrow();
        UUID plain = center.post(request(CARDS, "plain")).orElseThrow();
        center.activate(withDefault);
        center.activate(plain);
        assertThat(ran).containsExactly("go");
        assertThat(center.find(withDefault).orElseThrow().read()).isTrue();
        assertThat(center.find(plain).orElseThrow().read()).as("clicking the body marks it read").isTrue();
    }

    @Test void removeAndClear() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.post(request(CARDS, "b"));
        center.remove(a);
        assertThat(changes).contains(new NotificationCenter.Change.Removed(a));
        center.clear();
        assertThat(center.history()).isEmpty();
        assertThat(changes.getLast()).isEqualTo(new NotificationCenter.Change.Cleared());
        int before = changes.size();
        center.clear();
        assertThat(changes).hasSize(before);
    }

    @Test void aListenersChangeArrivesAfterTheOneItReactsToAndNoCardShowsForAReadEntry() {
        var center = center();
        center.onChange(change -> {
            if (change instanceof NotificationCenter.Change.Upserted upserted && !upserted.notification().read())
                center.markRead(upserted.notification().id());
        });
        center.post(request(CARDS, "seen at once"));
        assertThat(changes).extracting(change -> change instanceof NotificationCenter.Change.Upserted upserted
            ? "Upserted read=" + upserted.notification().read() : change.getClass().getSimpleName())
            .containsExactly("Upserted read=false", "Upserted read=true");
    }

    @Test void theCenterRefusesOtherThreads() throws Exception {
        var center = center();
        var thrown = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { center.history(); } catch (Throwable failure) { thrown.set(failure); } });
        worker.start();
        worker.join();
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }
}
```

`NotificationCenterThreadTest.java` (not on the EDT):

```java
package dev.jasper.app.notifications;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationCenterThreadTest {
    @Test void submitFromAWorkerLandsOnTheEdt() throws Exception {
        var center = new AtomicReference<NotificationCenter>();
        var deliveredOnEdt = new AtomicReference<Boolean>();
        SwingUtilities.invokeAndWait(() -> {
            center.set(new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED));
            center.get().register(new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD));
            center.get().onChange(change -> deliveredOnEdt.compareAndSet(null, SwingUtilities.isEventDispatchThread()));
        });
        center.get().submit(NotificationRequest.of("test.cards", Severity.INFO, "from a worker"));
        var titles = new AtomicReference<Object>();
        SwingUtilities.invokeAndWait(() -> titles.set(center.get().history().stream().map(entry -> entry.request().title()).toList()));
        assertThat(titles.get()).isEqualTo(java.util.List.of("from a worker"));
        assertThat(deliveredOnEdt.get()).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationCenter*'`
Expected: FAIL — `NotificationCenter` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package dev.jasper.app.notifications;

import dev.jasper.app.lifecycle.Subscription;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.SwingUtilities;

/**
 * The application's one notification store: declared groups, the in-memory history (at most {@value #HISTORY_LIMIT}
 * entries, oldest dropped), read and expired state, and cards waiting for Jasper to be focused. Nothing is persisted.
 *
 * <p>EDT-confined; {@link #submit} is the any-thread entry point. Listeners hear changes in the order they happened; a
 * change a listener makes is delivered after the one it is reacting to, and a card is never requested for an entry
 * that is read, expired or gone by the time the request is delivered.
 */
public final class NotificationCenter {
    public static final int HISTORY_LIMIT = 500;
    /** An identical post within this long of the last one counts as a repeat of it. */
    public static final Duration COALESCE = Duration.ofSeconds(60);
    private static final System.Logger LOG = System.getLogger(NotificationCenter.class.getName());

    /** What changed. */
    public sealed interface Change {
        /** A new entry, or an existing one whose count, read or expired state changed. */
        record Upserted(Notification notification) implements Change {}
        /** A card should show now for this entry, or refresh if one is showing. */
        record CardRequested(Notification notification) implements Change {}
        record Removed(UUID id) implements Change {}
        record Cleared() implements Change {}
    }

    private final Executor ui;
    private final Clock clock;
    private final Supplier<Delivery.Presence> presence;
    private final Map<String, NotificationGroup> groups = new LinkedHashMap<>();
    /** Oldest first; a repeat moves to the end. */
    private final LinkedHashMap<UUID, Notification> history = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();
    private final ArrayDeque<Change> queue = new ArrayDeque<>();
    private boolean delivering;

    /**
     * @param ui runs {@link #submit}ted posts on the EDT ({@code SwingUtilities::invokeLater} in the application)
     * @param clock stamps posts and decides coalescing; its zone is the one times are shown in
     * @param presence whether Jasper is focused, in the background, or has no window open
     */
    public NotificationCenter(Executor ui, Clock clock, Supplier<Delivery.Presence> presence) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    public Clock clock() { return clock; }

    /** Declares a group, or returns the one already declared under its id. */
    public NotificationGroup register(NotificationGroup group) {
        requireEdt();
        Objects.requireNonNull(group, "group");
        return groups.computeIfAbsent(group.id(), id -> group);
    }

    public Optional<NotificationGroup> group(String id) {
        requireEdt();
        return Optional.ofNullable(groups.get(id));
    }

    /** Declared groups, in declaration order. */
    public List<NotificationGroup> groups() {
        requireEdt();
        return List.copyOf(groups.values());
    }

    /** How the group's notifications appear: its declared default; {@code OFF} for an undeclared group. */
    public DisplayMode mode(String groupId) {
        requireEdt();
        NotificationGroup group = groups.get(groupId);
        return group == null ? DisplayMode.OFF : group.defaultMode();
    }

    /** Posts, returning the entry it landed in (an earlier one for a repeat), or empty when its group drops it. */
    public Optional<UUID> post(NotificationRequest request) {
        requireEdt();
        Objects.requireNonNull(request, "request");
        if (!groups.containsKey(request.group())) {
            LOG.log(System.Logger.Level.WARNING, "A notification for an undeclared group was dropped: " + request.group());
            return Optional.empty();
        }
        Delivery.Outcome outcome = Delivery.route(mode(request.group()), presence.get());
        if (!outcome.record()) return Optional.empty();
        Instant now = clock.instant();
        Notification earlier = repeatOf(request, now);
        Notification entry;
        if (earlier != null) {
            entry = earlier.repeated(now);
            history.remove(entry.id());
            history.put(entry.id(), entry);
        } else {
            entry = new Notification(UUID.randomUUID(), request, now, 1, false, false);
            history.put(entry.id(), entry);
            trim();
        }
        if (outcome.card()) emit(new Change.Upserted(entry), new Change.CardRequested(entry));
        else {
            if (outcome.pending()) pending.add(entry.id());
            emit(new Change.Upserted(entry));
        }
        return Optional.of(entry.id());
    }

    /** Posts from any thread: at once on the EDT, otherwise through the UI executor. */
    public void submit(NotificationRequest request) {
        Objects.requireNonNull(request, "request");
        if (SwingUtilities.isEventDispatchThread()) post(request);
        else ui.execute(() -> post(request));
    }

    public Optional<Notification> find(UUID id) {
        requireEdt();
        return Optional.ofNullable(history.get(id));
    }

    /** Newest first. */
    public List<Notification> history() {
        requireEdt();
        var newestFirst = new ArrayList<>(history.values());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    public int unread() {
        requireEdt();
        return (int) history.values().stream().filter(entry -> !entry.read()).count();
    }

    public int unreadErrors() {
        requireEdt();
        return (int) history.values().stream()
            .filter(entry -> !entry.read() && entry.request().severity() == Severity.ERROR).count();
    }

    public void markRead(UUID id) {
        requireEdt();
        update(id, entry -> entry.withRead(true));
    }

    public void markAllRead() {
        requireEdt();
        for (UUID id : List.copyOf(history.keySet())) markRead(id);
    }

    /** The poster says it no longer applies: its card closes and its actions stop working; the entry stays. */
    public void expire(UUID id) {
        requireEdt();
        pending.remove(id);
        update(id, Notification::withExpired);
    }

    public void remove(UUID id) {
        requireEdt();
        if (history.remove(id) == null) return;
        pending.remove(id);
        emit(new Change.Removed(id));
    }

    /** Clear All: every entry and every waiting card. */
    public void clear() {
        requireEdt();
        if (history.isEmpty()) return;
        history.clear();
        pending.clear();
        emit(new Change.Cleared());
    }

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

    /** A click on the body: the entry is read, and its default action runs when it has one (spec section 2). */
    public void activate(UUID id) {
        requireEdt();
        Notification entry = history.get(id);
        if (entry == null || entry.expired()) return;
        markRead(id);
        entry.request().activate().ifPresent(task -> contained(task, "A notification's default action failed"));
    }

    /** Jasper is focused again: cards that waited show now, in the order they were posted. */
    public void focusReturned() {
        requireEdt();
        List<UUID> waiting = List.copyOf(pending);
        pending.clear();
        for (UUID id : waiting) {
            Notification entry = history.get(id);
            if (entry != null) emit(new Change.CardRequested(entry));
        }
    }

    public Subscription onChange(Consumer<Change> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private Notification repeatOf(NotificationRequest request, Instant now) {
        for (Notification entry : history.values())
            if (!entry.expired() && entry.request().sameText(request) && !now.isAfter(entry.posted().plus(COALESCE))) return entry;
        return null;
    }

    private void trim() {
        while (history.size() > HISTORY_LIMIT) {
            UUID oldest = history.keySet().iterator().next();
            history.remove(oldest);
            pending.remove(oldest);
            emit(new Change.Removed(oldest));
        }
    }

    private void update(UUID id, UnaryOperator<Notification> change) {
        Notification entry = history.get(id);
        if (entry == null) return;
        Notification next = change.apply(entry);
        if (next.equals(entry)) return;
        history.put(id, next);
        emit(new Change.Upserted(next));
    }

    private void emit(Change... changes) {
        queue.addAll(List.of(changes));
        if (delivering) return;
        delivering = true;
        try {
            for (Change next = queue.poll(); next != null; next = queue.poll()) {
                if (next instanceof Change.CardRequested requested) {
                    Notification latest = history.get(requested.notification().id());
                    if (latest == null || latest.read() || latest.expired()) continue;
                    next = new Change.CardRequested(latest);
                }
                for (Consumer<Change> listener : List.copyOf(listeners)) {
                    try { listener.accept(next); }
                    catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A notification listener failed", failure); }
                }
            }
        } finally { delivering = false; }
    }

    private static void contained(Runnable task, String failed) {
        try { task.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, failed, failure); }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The notification center belongs to the EDT");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationCenter*'`
Expected: PASS (18 + 1 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenter*.java
git commit -m "feat(notifications): add the notification center" -m "EDT-confined in-memory history with coalescing, read and expired state, waiting cards and ordered change delivery." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Rail badges

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/PanelBadge.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/PanelEntry.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/BadgedIcon.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/PanelBadgeTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/BadgedIconTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java` (add one test)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java` (add one test)

**Interfaces:**
- Produces: `record PanelBadge(int count, PanelBadge.Tone tone, String description)` with `enum Tone { ACCENT, ERROR }`;
  `PanelEntry.badge(): Optional<PanelBadge>`; `PanelEntry.setBadge(Optional<PanelBadge>)` (EDT; fires `Kind.RAIL`
  only on change); UI keys `Jasper.railBadgeAccent`, `Jasper.railBadgeError`; package-private
  `BadgedIcon(Icon base, PanelBadge.Tone tone)` with `tone()` and `base()`; `WindowRail.PanelButton` gains a sixth
  component `Optional<PanelBadge> badge` and keeps a five-argument constructor.

- [ ] **Step 1: Write the failing tests**

`PanelBadgeTest.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class PanelBadgeTest {
    @Test void aBadgeChangeRerendersTheRailOnlyWhenItChanges() {
        var model = new Contributions();
        PanelEntry panel = model.addPanel("dev.x.notes", "Notes", new ImageIcon(), PanelRegion.RIGHT, site -> new JLabel());
        var kinds = new ArrayList<Contributions.Kind>();
        model.onChanged(kinds::add);
        var badge = new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications");
        panel.setBadge(Optional.of(badge));
        panel.setBadge(Optional.of(new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications")));
        assertThat(kinds).containsExactly(Contributions.Kind.RAIL);
        assertThat(panel.badge()).contains(badge);
        panel.setBadge(Optional.empty());
        assertThat(kinds).containsExactly(Contributions.Kind.RAIL, Contributions.Kind.RAIL);
        panel.close();
        kinds.clear();
        panel.setBadge(Optional.of(badge));
        assertThat(kinds).as("a closed panel has no rail button to mark").isEmpty();
    }

    @Test void aBadgeCountsSomethingAndSaysWhat() {
        assertThatThrownBy(() -> new PanelBadge(0, PanelBadge.Tone.ACCENT, "none")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelBadge(1, PanelBadge.Tone.ERROR, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

`BadgedIconTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class BadgedIconTest {
    @AfterEach void resetTheme() throws Exception { SwingUtilities.invokeAndWait(ThemeController::new); }

    @Test void theDotTakesItsToneColourInDarkAndLightThemes() {
        for (Theme theme : List.of(Theme.DARK, Theme.INTELLIJ_LIGHT)) {
            new ThemeController(theme);
            for (PanelBadge.Tone tone : PanelBadge.Tone.values()) {
                var icon = new BadgedIcon(new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), tone);
                var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
                int dot = UIScale.scale(6);
                Color expected = UIManager.getColor(tone == PanelBadge.Tone.ERROR ? "Jasper.railBadgeError" : "Jasper.railBadgeAccent");
                assertThat(new Color(image.getRGB(icon.getIconWidth() - dot / 2 - 1, dot / 2), true)).as("%s %s", theme, tone)
                    .isEqualTo(new Color(expected.getRGB()));
            }
        }
    }
}
```

Add to `WindowPanelsTest.java` (add `import dev.jasper.app.contributions.PanelBadge;`):

```java
    @Test void aBadgedPanelShowsADotAndDescribesItsCount() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var icon = new ImageIcon(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB));
            PanelEntry notes = model.addPanel("dev.x.notes", "Notes", icon, PanelRegion.RIGHT, site -> new JLabel("notes"));
            notes.setBadge(Optional.of(new PanelBadge(4, PanelBadge.Tone.ERROR, "4 unread notifications, 1 error")));
            AbstractButton button = owner.rail().buttons().getFirst();
            assertThat(button.getToolTipText()).isEqualTo("Notes (4 unread notifications, 1 error)");
            assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Notes");
            assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("4 unread notifications, 1 error");
            assertThat(button.getIcon()).isInstanceOfSatisfying(BadgedIcon.class, badged -> {
                assertThat(badged.tone()).isEqualTo(PanelBadge.Tone.ERROR);
                assertThat(badged.base()).isSameAs(icon);
            });
            notes.setBadge(Optional.empty());
            assertThat(owner.rail().buttons().getFirst().getIcon()).isSameAs(icon);
            assertThat(owner.rail().buttons().getFirst().getToolTipText()).isEqualTo("Notes");
        });
    }
```

Add to `ChromeKeysTest.java`:

```java
    @Test void railBadgesReadAgainstTheRail() {
        var defaults = flatLafOnly();
        ChromeKeys.derive(defaults, TERMINAL);
        Color rail = defaults.getColor("Jasper.titleBackground");
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.railBadgeError"), rail)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.railBadgeAccent"), rail)).isGreaterThanOrEqualTo(3);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.PanelBadgeTest' --tests 'dev.jasper.app.workspace.BadgedIconTest' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.appearance.ChromeKeysTest'`
Expected: FAIL — `PanelBadge` and `BadgedIcon` do not exist.

- [ ] **Step 3: Write the implementation**

`PanelBadge.java`:

```java
package dev.jasper.app.contributions;

import java.util.Objects;

/** A rail button's unread mark: how many, how urgent, and the words its tooltip and screen readers use. */
public record PanelBadge(int count, Tone tone, String description) {
    public enum Tone { ACCENT, ERROR }

    public PanelBadge {
        if (count < 1) throw new IllegalArgumentException("A badge counts at least one item");
        Objects.requireNonNull(tone, "tone");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("A badge needs a description");
    }
}
```

In `PanelEntry.java`, add imports `java.util.Objects`, `java.util.Optional` and `javax.swing.SwingUtilities`, add the
field `private PanelBadge badge;` after `private boolean closed;`, and add these methods before `close()`:

```java
    /** The rail button's unread mark, if any. */
    public Optional<PanelBadge> badge() { return Optional.ofNullable(badge); }

    /** Replaces the mark; every window's rail re-renders when it changed. EDT only. */
    public void setBadge(Optional<PanelBadge> value) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
        PanelBadge next = value.orElse(null);
        if (closed || Objects.equals(badge, next)) return;
        badge = next;
        owner.changed(Contributions.Kind.RAIL);
    }
```

`BadgedIcon.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelBadge;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/** A rail icon with an unread dot at its top-right corner, ringed in the rail's colour so it reads on any artwork. */
final class BadgedIcon implements Icon {
    private final Icon base;
    private final PanelBadge.Tone tone;

    BadgedIcon(Icon base, PanelBadge.Tone tone) {
        this.base = base;
        this.tone = tone;
    }

    Icon base() { return base; }
    PanelBadge.Tone tone() { return tone; }

    @Override public int getIconWidth() { return base.getIconWidth(); }
    @Override public int getIconHeight() { return base.getIconHeight(); }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        base.paintIcon(component, graphics, x, y);
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int dot = UIScale.scale(6), ring = UIScale.scale(1);
            int left = x + getIconWidth() - dot;
            Color rail = UIManager.getColor("Jasper.titleBackground");
            if (rail != null) {
                g.setColor(rail);
                g.fillOval(left - ring, y - ring, dot + 2 * ring, dot + 2 * ring);
            }
            Color color = UIManager.getColor(tone == PanelBadge.Tone.ERROR ? "Jasper.railBadgeError" : "Jasper.railBadgeAccent");
            g.setColor(color != null ? color : Color.RED);
            g.fillOval(left, y, dot, dot);
        } finally { g.dispose(); }
    }
}
```

In `WindowRail.java`: add imports `dev.jasper.app.contributions.PanelBadge` and `java.util.Optional`; replace the
`PanelButton` record with:

```java
    record PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected, Optional<PanelBadge> badge) {
        PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected) {
            this(id, title, icon, region, selected, Optional.empty());
        }
    }
```

and, in `render`, replace the line `style(button, panel.icon(), panel.title());` with:

```java
                style(button, panel.badge().<Icon>map(badge -> new BadgedIcon(panel.icon(), badge.tone())).orElse(panel.icon()),
                    panel.title());
                panel.badge().ifPresent(badge -> {
                    button.setToolTipText(panel.title() + " (" + badge.description() + ")");
                    button.getAccessibleContext().setAccessibleDescription(badge.description());
                });
```

In `WindowContributions.renderRail()`, replace the `buttons.add(...)` line with:

```java
            buttons.add(new WindowRail.PanelButton(panel.entry.id(), panel.entry.title(), panel.entry.icon(), panel.region,
                panel.visible, panel.entry.badge()));
```

In `ChromeKeys.java`: append `"Jasper.railBadgeAccent", "Jasper.railBadgeError"` to the end of `KEYS` (after
`"Jasper.titleSearch.hintForeground"`), and add, immediately before `return added;` in `derive`:

```java
        put(defaults, added, "Jasper.railBadgeAccent", readable(first(defaults, "TabbedPane.underlineColor"), title, 3, foreground));
        put(defaults, added, "Jasper.railBadgeError", readable(first(defaults, "Actions.Red"), title, 3, foreground));
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.BadgedIconTest' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.workspace.GraphiteChromeTest' --tests 'dev.jasper.app.appearance.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/contributions jasper-app/src/main/java/dev/jasper/app/workspace/BadgedIcon.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java jasper-app/src/test/java/dev/jasper/app/contributions/PanelBadgeTest.java jasper-app/src/test/java/dev/jasper/app/workspace/BadgedIconTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java
git commit -m "feat(workspace): show unread badges on rail buttons" -m "Panels carry an optional badge; the rail paints a dot in the accent or error colour and puts the count in the tooltip." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: The notification card

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/TextBlock.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/NotificationStyle.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/NotificationCard.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/TextBlockTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java` (add one test)

**Interfaces:**
- Consumes: `NotificationCenter` (`post`, `find`, `runAction`, `activate`, `markRead`), `Notification`,
  `NotificationRequest`, `NotificationAction`, `Severity`; `AppIcons.bundled(String)` from `dev.jasper.app.platform`.
- Produces (package-private unless noted): UI keys `Jasper.notification.background`, `.borderColor`, `.foreground`,
  `.mutedForeground`, `.linkForeground`; `TextBlock(String colorKey, boolean bold, int maxLines)` with `setText`,
  `text()`, `setWrapWidth(int)`, `lines()`, `lineHeight()`, `lastLineWidth()` and `static List<String> wrap(String,
  ToIntFunction<String>, int width, int maxLines)`; `NotificationStyle.icon(Notification)`,
  `NotificationStyle.color(String key, Color fallback)`, `NotificationStyle.link(String label, Runnable run)`,
  `NotificationStyle.severityName(Severity)`; `NotificationCard(NotificationCenter, Notification, boolean sticky)` with
  `WIDTH = 360`, `CONTENT_LINES = 5`, `VISIBLE_ACTIONS = 3`, `ARC = 10`, `notification()`, `sticky()`,
  `update(Notification)`, `setHovered(boolean)`, field `Consumer<Boolean> onHover`, test accessors `titleLines()`,
  `contentLines()`, `countText()`, `actionButtons()`, `moreMenu()`, `closeButton()`, `titleColor()`.

- [ ] **Step 1: Write the failing tests**

`TextBlockTest.java`:

```java
package dev.jasper.app.notificationui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextBlockTest {
    @Test void wordsWrapToTheWidth() {
        assertThat(TextBlock.wrap("aaaa bbbb cccc", String::length, 10, 0)).containsExactly("aaaa bbbb", "cccc");
    }

    @Test void aWordWiderThanTheLineBreaksByCharacter() {
        assertThat(TextBlock.wrap("abcdefghijklm", String::length, 5, 0)).containsExactly("abcde", "fghij", "klm");
    }

    @Test void lineBreaksAreKept() {
        assertThat(TextBlock.wrap("one\n\ntwo", String::length, 10, 0)).containsExactly("one", "", "two");
    }

    @Test void cutTextEndsInAnEllipsis() {
        assertThat(TextBlock.wrap("a b c d e f", String::length, 3, 2)).containsExactly("a b", "c…");
        assertThat(TextBlock.wrap("a b", String::length, 3, 2)).containsExactly("a b");
    }

    @Test void surrogatePairsStayWhole() {
        assertThat(TextBlock.wrap("😀😀", String::length, 3, 0)).containsExactly("😀", "😀");
    }

    @Test void emptyTextHasNoLines() {
        assertThat(TextBlock.wrap("", String::length, 10, 5)).isEqualTo(List.of());
    }
}
```

`NotificationCardTest.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationCardTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);

    private Notification posted(NotificationRequest request) {
        center.register(CARDS);
        return center.find(center.post(request).orElseThrow()).orElseThrow();
    }

    private static NotificationRequest titled(String title) { return NotificationRequest.of(CARDS.id(), Severity.INFO, title); }

    private static void click(JComponent card) {
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
    }

    @Test void titleContentAndCountArePlainWrappedText() {
        var card = new NotificationCard(center, posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))), false);
        assertThat(card.getPreferredSize().width).isEqualTo(UIScale.scale(NotificationCard.WIDTH));
        assertThat(String.join(" ", card.titleLines())).isEqualTo("<html><b>Disk</b> full");
        assertThat(card.contentLines()).hasSize(NotificationCard.CONTENT_LINES);
        assertThat(card.contentLines().getLast()).endsWith("…");
        assertThat(card.countText()).isEmpty();
        card.update(posted(titled("<html><b>Disk</b> full").withContent("word ".repeat(300))));
        assertThat(card.countText()).isEqualTo("×2");
    }

    @Test void threeActionsShowAndTheRestWaitUnderMore() {
        var ran = new ArrayList<String>();
        var request = titled("Actions");
        for (String label : List.of("One", "Two", "Three", "Four", "Five"))
            request = request.withAction(NotificationAction.of(label, () -> ran.add(label)));
        var card = new NotificationCard(center, posted(request), false);
        assertThat(card.actionButtons()).extracting(JButton::getText).containsExactly("One", "Two", "Three", "More ▾");
        assertThat(card.actionButtons()).allSatisfy(button -> assertThat(button.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE));
        assertThat(Arrays.stream(card.moreMenu().getComponents()).map(item -> ((JMenuItem) item).getText()))
            .containsExactly("Four", "Five");
        ((JMenuItem) card.moreMenu().getComponent(1)).doClick();
        assertThat(ran).containsExactly("Five");
        assertThat(center.find(card.notification().id()).orElseThrow().expired()).isTrue();
    }

    @Test void clickingTheBodyReadsItAndRunsADefaultActionWhenThereIsOne() {
        var ran = new ArrayList<String>();
        var withDefault = new NotificationCard(center, posted(titled("Default").withActivate(() -> ran.add("activated"))), false);
        click(withDefault);
        assertThat(ran).containsExactly("activated");
        assertThat(center.find(withDefault.notification().id()).orElseThrow().read()).isTrue();
        var plain = new NotificationCard(center, posted(titled("Plain")), false);
        click(plain);
        assertThat(center.find(plain.notification().id()).orElseThrow().read()).as("spec section 2: clicking the body marks it read").isTrue();
        assertThat(ran).containsExactly("activated");
    }

    @Test void closeShowsOnHoverForAutoHidingCardsAndAlwaysForStickyOnes() {
        var hovers = new ArrayList<Boolean>();
        var card = new NotificationCard(center, posted(titled("Hover")), false);
        card.onHover = hovers::add;
        assertThat(card.closeButton().isVisible()).isFalse();
        card.setHovered(true);
        assertThat(card.closeButton().isVisible()).isTrue();
        card.setHovered(false);
        assertThat(card.closeButton().isVisible()).isFalse();
        assertThat(hovers).containsExactly(true, false);
        var sticky = new NotificationCard(center, posted(titled("Sticky")), true);
        assertThat(sticky.closeButton().isVisible()).isTrue();
        sticky.closeButton().doClick();
        assertThat(center.find(sticky.notification().id()).orElseThrow().read()).isTrue();
    }

    @Test void theCardRestylesWhenTheThemeChanges() {
        try {
            new ThemeController(Theme.DARK);
            Color dark = UIManager.getColor("Jasper.notification.background");
            var card = new NotificationCard(center, posted(titled("Theme")), false);
            new ThemeController(Theme.INTELLIJ_LIGHT);
            SwingUtilities.updateComponentTreeUI(card);
            Color light = UIManager.getColor("Jasper.notification.background");
            assertThat(light).isNotEqualTo(dark);
            assertThat(card.titleColor()).isEqualTo(new Color(UIManager.getColor("Jasper.notification.foreground").getRGB(), true));
            card.setSize(card.getPreferredSize());
            var image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { card.paint(g); } finally { g.dispose(); }
            assertThat(new Color(image.getRGB(card.getWidth() / 2, card.getHeight() - UIScale.scale(4)), true))
                .isEqualTo(new Color(light.getRGB()));
        } finally { new ThemeController(); }
    }
}
```

Add to `ChromeKeysTest.java`:

```java
    @Test void notificationCardsFollowIntellijNotificationKeysOrDeriveThem() {
        var derived = flatLafOnly();
        ChromeKeys.derive(derived, TERMINAL);
        assertThat(derived.getColor("Jasper.notification.background")).isEqualTo(new Color(0xf2f2f2));
        assertThat(derived.getColor("Jasper.notification.borderColor")).isEqualTo(new Color(0xc4c4c4));
        assertThat(derived.getColor("Jasper.notification.foreground")).isEqualTo(new Color(0x000000));
        assertThat(ChromeKeys.contrast(derived.getColor("Jasper.notification.linkForeground"), new Color(0xf2f2f2))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(derived.getColor("Jasper.notification.mutedForeground"), new Color(0xf2f2f2))).isGreaterThanOrEqualTo(4.5);
        var authored = flatLafOnly();
        put(authored, "Notification.background", 0xfff8e0); put(authored, "Notification.borderColor", 0xd0b060);
        put(authored, "Notification.foreground", 0x222222); put(authored, "PopupMenu.background", 0xffffff);
        ChromeKeys.derive(authored, TERMINAL);
        assertThat(authored.getColor("Jasper.notification.background")).isEqualTo(new Color(0xfff8e0));
        assertThat(authored.getColor("Jasper.notification.borderColor")).isEqualTo(new Color(0xd0b060));
        assertThat(authored.getColor("Jasper.notification.foreground")).isEqualTo(new Color(0x222222));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.appearance.ChromeKeysTest'`
Expected: FAIL — `TextBlock` and `NotificationCard` do not exist.

- [ ] **Step 3: Write the implementation**

`notificationui/package-info.java`:

```java
/**
 * EDT Swing views of the notification center: each window's card stack, the history panel, and their text, style
 * and focus helpers. Views subscribe to the center and close their subscriptions when closed; the application owns
 * the center, mounts each window's card layer and closes it with the window.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.contributions, dev.jasper.app.lifecycle, dev.jasper.app.notifications, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.notificationui;
```

`TextBlock.java`:

```java
package dev.jasper.app.notificationui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Plain text word-wrapped to a fixed width and, with a line limit, cut with an ellipsis. Painted with
 * {@code drawString}, so markup is never interpreted. EDT only.
 */
final class TextBlock extends JComponent {
    static final String ELLIPSIS = "…";
    private final String colorKey;
    private final boolean bold;
    private final int maxLines;
    private String text = "";
    private int wrapWidth = 1;
    private List<String> lines = List.of();

    /** {@code maxLines} 0 means no limit. */
    TextBlock(String colorKey, boolean bold, int maxLines) {
        this.colorKey = colorKey;
        this.bold = bold;
        this.maxLines = maxLines;
        setOpaque(false);
        setFocusable(false);
        restyle();
    }

    void setText(String value) {
        text = Objects.requireNonNull(value, "value");
        relayout();
    }

    String text() { return text; }

    void setWrapWidth(int pixels) {
        int width = Math.max(1, pixels);
        if (width == wrapWidth) return;
        wrapWidth = width;
        relayout();
    }

    List<String> lines() { return lines; }

    int lineHeight() { return getFontMetrics(getFont()).getHeight(); }

    int lastLineWidth() { return lines.isEmpty() ? 0 : measure(lines.getLast()); }

    @Override public void updateUI() {
        super.updateUI();
        if (colorKey != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) setFont(bold ? base.deriveFont(Font.BOLD) : base);
        setForeground(NotificationStyle.color(colorKey, Color.BLACK));
        relayout();
    }

    private int measure(String value) {
        return (int) Math.ceil(BasicGraphicsUtils.getStringWidth(this, getFontMetrics(getFont()), value));
    }

    private void relayout() {
        if (getFont() == null) return;
        lines = wrap(text, this::measure, wrapWidth, maxLines);
        revalidate();
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(wrapWidth, lines.size() * lineHeight()); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics metrics = g.getFontMetrics();
            int y = metrics.getAscent();
            for (String line : lines) {
                BasicGraphicsUtils.drawString(this, g, line, 0, y);
                y += metrics.getHeight();
            }
        } finally { g.dispose(); }
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
            @Override public String getAccessibleName() { return text; }
        };
        return accessibleContext;
    }

    /**
     * Words that fit {@code width} per line, a word wider than a line broken by character, explicit line breaks kept.
     * With {@code maxLines > 0}, cut text ends its last kept line with an ellipsis. Never splits a surrogate pair.
     */
    static List<String> wrap(String text, ToIntFunction<String> measure, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        int limit = maxLines > 0 ? maxLines + 1 : Integer.MAX_VALUE;
        for (String paragraph : text.split("\n", -1)) {
            if (lines.size() >= limit) break;
            int before = lines.size();
            var line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (rest.length() > 1 && measure.applyAsInt(rest) > width) {
                    int fit = fitting(rest, measure, width);
                    lines.add(rest.substring(0, fit));
                    rest = rest.substring(fit);
                }
                line.append(rest);
                if (lines.size() >= limit) break;
            }
            if (!line.isEmpty() || lines.size() == before) lines.add(line.toString());
        }
        if (maxLines <= 0 || lines.size() <= maxLines) return lines;
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        String last = kept.getLast();
        while (!last.isEmpty() && measure.applyAsInt(last + ELLIPSIS) > width) last = dropLast(last);
        kept.set(maxLines - 1, last.stripTrailing() + ELLIPSIS);
        return kept;
    }

    /** How many leading characters of {@code word} fit, at least one, never ending on half a surrogate pair. */
    private static int fitting(String word, ToIntFunction<String> measure, int width) {
        int fit = 1;
        while (fit < word.length() && measure.applyAsInt(word.substring(0, fit + 1)) <= width) fit++;
        if (fit < word.length() && Character.isHighSurrogate(word.charAt(fit - 1))) fit = fit > 1 ? fit - 1 : fit + 1;
        return fit;
    }

    private static String dropLast(String value) {
        int end = value.length() - 1;
        if (end > 0 && Character.isLowSurrogate(value.charAt(end)) && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
```

`NotificationStyle.java`:

```java
package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/** What cards and history rows share: severity icons, theme colours and link-style action buttons. EDT only. */
final class NotificationStyle {
    private NotificationStyle() {}

    /** The poster's icon, or the bundled one for the severity. */
    static Icon icon(Notification notification) {
        return notification.request().icon().orElseGet(() -> AppIcons.bundled(switch (notification.request().severity()) {
            case INFO -> "status/info";
            case WARNING -> "status/warning";
            case ERROR -> "status/error";
        }));
    }

    /** A theme colour as a plain {@link Color}, so a component keeps it through {@code updateUI} until restyled. */
    static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : new Color(color.getRGB(), true);
    }

    /** A borderless, link-coloured button that never takes focus and never renders markup. */
    static JButton link(String label, Runnable run) {
        var button = new JButton(label);
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setFocusable(false);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(color("Jasper.notification.linkForeground", Color.BLUE));
        button.addActionListener(event -> run.run());
        return button;
    }

    static String severityName(Severity severity) {
        return switch (severity) {
            case INFO -> "Information";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
```

`NotificationCard.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * One notification as a card (spec section 3): icon, wrapping bold title with a muted repeat count, at most five lines
 * of content, up to three action links with the rest under More, and a close button shown on hover (always for a
 * sticky card). Clicking the body runs the notification's default action. Plain text only. EDT only.
 */
final class NotificationCard extends JPanel {
    static final int WIDTH = 360;
    static final int CONTENT_LINES = 5;
    static final int VISIBLE_ACTIONS = 3;
    static final int ARC = 10;
    private static final int PAD = 12, ICON = 16, GAP = 8, CLOSE = 16;

    private final NotificationCenter center;
    private final boolean sticky;
    private Notification notification;
    private final JLabel icon = new JLabel();
    private final TextBlock title = new TextBlock("Jasper.notification.foreground", true, 0);
    private final JLabel count = new JLabel();
    private final TextBlock content = new TextBlock("Jasper.notification.foreground", false, CONTENT_LINES);
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JButton close = new JButton();
    private final MouseAdapter hover;
    private JPopupMenu more;
    private boolean hovered;
    /** Told when the pointer enters or leaves the card, so its countdown pauses. */
    Consumer<Boolean> onHover = value -> {};

    NotificationCard(NotificationCenter center, Notification notification, boolean sticky) {
        this.center = center;
        this.sticky = sticky;
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        count.putClientProperty("html.disable", Boolean.TRUE);
        actions.setOpaque(false);
        close.setIcon(AppIcons.bundled("general/closeSmall"));
        close.setRolloverIcon(AppIcons.bundled("general/closeSmallHovered"));
        close.putClientProperty("JButton.buttonType", "borderless");
        close.setFocusable(false);
        close.setToolTipText("Close");
        close.getAccessibleContext().setAccessibleName("Close notification");
        close.addActionListener(event -> center.markRead(this.notification.id()));
        hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { setHovered(true); }
            @Override public void mouseExited(MouseEvent event) { if (!pointerInside()) setHovered(false); }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getSource() == NotificationCard.this && SwingUtilities.isLeftMouseButton(event))
                    center.activate(NotificationCard.this.notification.id());
            }
        };
        addMouseListener(hover);
        close.addMouseListener(hover);
        add(icon); add(title); add(count); add(content); add(actions); add(close);
        update(notification);
        restyle();
    }

    Notification notification() { return notification; }

    boolean sticky() { return sticky; }

    /** Shows {@code next}: the same entry with a new count, or new text. */
    void update(Notification next) {
        notification = next;
        var request = next.request();
        icon.setIcon(NotificationStyle.icon(next));
        title.setText(request.title());
        count.setText(next.count() > 1 ? "×" + next.count() : "");
        count.setVisible(next.count() > 1);
        content.setText(request.content());
        content.setVisible(!request.content().isEmpty());
        actions.removeAll();
        more = null;
        List<NotificationAction> all = request.actions();
        for (int i = 0; i < Math.min(all.size(), VISIBLE_ACTIONS); i++) actions.add(actionLink(i, all.get(i).label()));
        if (all.size() > VISIBLE_ACTIONS) actions.add(moreLink(all));
        actions.setVisible(!all.isEmpty());
        setCursor(request.activate().isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        close.setVisible(sticky || hovered);
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(request.severity()) + ": " + request.title());
        getAccessibleContext().setAccessibleDescription(request.content());
        arrange(true);
        revalidate();
        repaint();
    }

    void setHovered(boolean value) {
        if (hovered == value) return;
        hovered = value;
        close.setVisible(sticky || hovered);
        onHover.accept(value);
    }

    List<String> titleLines() { return title.lines(); }
    List<String> contentLines() { return content.lines(); }
    String countText() { return count.getText(); }
    List<JButton> actionButtons() { return Arrays.stream(actions.getComponents()).map(JButton.class::cast).toList(); }
    JPopupMenu moreMenu() { return more; }
    JButton closeButton() { return close; }
    Color titleColor() { return title.getForeground(); }

    private boolean pointerInside() {
        try { return getMousePosition(true) != null; }
        catch (HeadlessException headless) { return false; }
    }

    private JButton actionLink(int index, String label) {
        JButton link = NotificationStyle.link(label, () -> center.runAction(notification.id(), index));
        if (index > 0) link.setBorder(BorderFactory.createEmptyBorder(2, UIScale.scale(12), 2, 0));
        link.addMouseListener(hover);
        return link;
    }

    private JButton moreLink(List<NotificationAction> all) {
        var menu = new JPopupMenu();
        for (int i = VISIBLE_ACTIONS; i < all.size(); i++) {
            int index = i;
            var item = new JMenuItem(all.get(i).label());
            item.putClientProperty("html.disable", Boolean.TRUE);
            item.addActionListener(event -> center.runAction(notification.id(), index));
            menu.add(item);
        }
        more = menu;
        JButton link = NotificationStyle.link("More ▾", () -> {});
        link.addActionListener(event -> menu.show(link, 0, link.getHeight()));
        link.setBorder(BorderFactory.createEmptyBorder(2, UIScale.scale(12), 2, 0));
        link.addMouseListener(hover);
        return link;
    }

    /** Places the parts and returns the height they need; with {@code apply} false it only measures. */
    private int arrange(boolean apply) {
        int pad = UIScale.scale(PAD), iconSize = UIScale.scale(ICON), gap = UIScale.scale(GAP), closeSize = UIScale.scale(CLOSE);
        int width = UIScale.scale(WIDTH);
        int textX = pad + iconSize + gap;
        int titleWidth = width - textX - pad - closeSize - UIScale.scale(4);
        int textWidth = width - textX - pad;
        title.setWrapWidth(titleWidth);
        content.setWrapWidth(textWidth);
        int y = pad;
        Dimension titleSize = title.getPreferredSize();
        int titleHeight = titleSize.height;
        if (count.isVisible()) {
            Dimension countSize = count.getPreferredSize();
            int lineHeight = title.lineHeight();
            int lines = Math.max(1, title.lines().size());
            int countX = textX + title.lastLineWidth() + UIScale.scale(6);
            int countY = y + (lines - 1) * lineHeight;
            if (countX + countSize.width > textX + titleWidth) {
                countX = textX;
                countY = y + lines * lineHeight;
                titleHeight += lineHeight;
            }
            if (apply) count.setBounds(countX, countY + (lineHeight - countSize.height) / 2, countSize.width, countSize.height);
        }
        if (apply) {
            icon.setBounds(pad, pad, iconSize, iconSize);
            title.setBounds(textX, y, titleSize.width, titleSize.height);
            close.setBounds(width - pad - closeSize, pad, closeSize, closeSize);
        }
        y += Math.max(titleHeight, iconSize);
        if (content.isVisible()) {
            y += UIScale.scale(4);
            Dimension contentSize = content.getPreferredSize();
            if (apply) content.setBounds(textX, y, contentSize.width, contentSize.height);
            y += contentSize.height;
        }
        if (actions.isVisible()) {
            y += gap;
            Dimension actionSize = actions.getPreferredSize();
            if (apply) actions.setBounds(textX, y, textWidth, actionSize.height);
            y += actionSize.height;
        }
        return y + pad;
    }

    @Override public void doLayout() { arrange(true); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(WIDTH), arrange(false)); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = UIScale.scale(ARC);
            g.setColor(NotificationStyle.color("Jasper.notification.background", Color.WHITE));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(NotificationStyle.color("Jasper.notification.borderColor", Color.GRAY));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        } finally { g.dispose(); }
    }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist; the constructor styles the card itself.
        if (close != null) restyle();
    }

    private void restyle() {
        count.setForeground(NotificationStyle.color("Jasper.notification.mutedForeground", Color.GRAY));
        for (Component link : actions.getComponents()) link.setForeground(NotificationStyle.color("Jasper.notification.linkForeground", Color.BLUE));
        close.setBorder(BorderFactory.createEmptyBorder());
    }
}
```

In `ChromeKeys.java`: append `"Jasper.notification.background", "Jasper.notification.borderColor",
"Jasper.notification.foreground", "Jasper.notification.mutedForeground", "Jasper.notification.linkForeground"` to the
end of `KEYS`, and add immediately before `return added;`:

```java
        put(defaults, added, "Jasper.notification.background", first(defaults, "Notification.background", "PopupMenu.background", "Panel.background"));
        Color card = defaults.getColor("Jasper.notification.background");
        put(defaults, added, "Jasper.notification.borderColor", first(defaults, "Notification.borderColor", "Popup.borderColor", "Component.borderColor"));
        put(defaults, added, "Jasper.notification.foreground", first(defaults, "Notification.foreground", "Label.foreground"));
        Color cardText = defaults.getColor("Jasper.notification.foreground");
        put(defaults, added, "Jasper.notification.mutedForeground",
            readable(first(defaults, "Label.infoForeground", "Label.disabledForeground"), card, 4.5, cardText));
        put(defaults, added, "Jasper.notification.linkForeground",
            readable(first(defaults, "Link.activeForeground", "TabbedPane.underlineColor"), card, 4.5, cardText));
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.appearance.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notificationui jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java jasper-app/src/test/java/dev/jasper/app/notificationui jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java
git commit -m "feat(notifications): add the notification card" -m "Plain, wrapped text with a five-line cut, action links with an overflow menu, hover close and themed colours." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: The card layer

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/WindowFocus.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/CardLayerTest.java`

**Interfaces:**
- Consumes: `NotificationCard` (Task 5), `NotificationCenter` (`onChange`, `find`, `mode`, `markRead`, `post`, `expire`,
  `remove`, `clear`), `DisplayMode`.
- Produces: package-private `WindowFocus.track(Component, Consumer<Boolean> changed): Runnable` (returns the stop);
  public `CardLayer(NotificationCenter center, boolean focused, Runnable openHistory)` implementing `AutoCloseable`,
  constants `VISIBLE = 3`, `AUTO_HIDE = Duration.ofSeconds(10)`, package-private `MARGIN = 12`, `GAP = 8`; public
  `show(Notification)`, `dismissAll()`, `List<UUID> showing()` (top to bottom), `close()`; package-private test
  constructor `CardLayer(center, focused, openHistory, BiFunction<Duration, Runnable, Runnable> schedule,
  LongSupplier nanoTime)`, `windowFocusChanged(boolean)`, `waitingIds()`, `moreButton()`, `card(UUID)`. The layer
  publishes its stack size through `setPreferredSize` (property `preferredSize`).

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CardLayerTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final NotificationGroup STICKY = new NotificationGroup("test.sticky", "Sticky", "jasper", DisplayMode.STICKY);
    private record Scheduled(Duration delay, Runnable task, boolean[] cancelled) {}

    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private long now;

    private CardLayer layer(boolean focused) {
        center.register(CARDS);
        center.register(STICKY);
        return new CardLayer(center, focused, () -> opened.add("history"), (delay, task) -> {
            boolean[] cancelled = {false};
            scheduled.add(new Scheduled(delay, task, cancelled));
            return () -> cancelled[0] = true;
        }, () -> now);
    }

    private List<Scheduled> live() { return scheduled.stream().filter(item -> !item.cancelled()[0]).toList(); }

    private Notification post(NotificationGroup group, String title) {
        return center.find(center.post(NotificationRequest.of(group.id(), Severity.INFO, title)).orElseThrow()).orElseThrow();
    }

    @Test void aCardShowsInsideATransparentUnfocusableLayer() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(layer.showing()).containsExactly(a.id());
        assertThat(layer.isVisible()).isTrue();
        assertThat(layer.getMouseListeners()).as("clicks beside a card reach the terminal").isEmpty();
        assertThat(layer.isFocusable()).isFalse();
        int margin = UIScale.scale(CardLayer.MARGIN);
        assertThat(layer.getPreferredSize().width).isEqualTo(UIScale.scale(NotificationCard.WIDTH) + 2 * margin);
        assertThat(layer.getPreferredSize().height).isEqualTo(layer.card(a.id()).getPreferredSize().height + 2 * margin);
    }

    @Test void theNewestCardSitsAtTheBottom() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var b = post(CARDS, "b");
        layer.show(a);
        layer.show(b);
        layer.setSize(layer.getPreferredSize());
        layer.doLayout();
        assertThat(layer.card(b.id()).getY()).isGreaterThan(layer.card(a.id()).getY());
        assertThat(layer.card(b.id()).getY() + layer.card(b.id()).getHeight()).isEqualTo(layer.getHeight() - UIScale.scale(CardLayer.MARGIN));
    }

    @Test void aFourthCardPushesOutTheOldestAutoHidingOne() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var s = post(STICKY, "s");
        var b = post(CARDS, "b");
        var c = post(CARDS, "c");
        for (var entry : List.of(a, s, b, c)) layer.show(entry);
        assertThat(layer.showing()).containsExactly(s.id(), b.id(), c.id());
        assertThat(center.find(a.id()).orElseThrow().read()).as("it stays unread in history").isFalse();
    }

    @Test void aFullStackOfStickyCardsQueuesTheRestBehindAChip() {
        var layer = layer(true);
        var s1 = post(STICKY, "s1");
        var s2 = post(STICKY, "s2");
        var s3 = post(STICKY, "s3");
        var s4 = post(STICKY, "s4");
        for (var entry : List.of(s1, s2, s3, s4)) layer.show(entry);
        assertThat(layer.showing()).containsExactly(s1.id(), s2.id(), s3.id());
        assertThat(layer.waitingIds()).containsExactly(s4.id());
        assertThat(layer.moreButton().isVisible()).isTrue();
        assertThat(layer.moreButton().getText()).isEqualTo("+1 more");
        layer.moreButton().doClick();
        assertThat(opened).containsExactly("history");
        center.markRead(s1.id());
        assertThat(layer.showing()).containsExactly(s2.id(), s3.id(), s4.id());
        assertThat(layer.moreButton().isVisible()).isFalse();
    }

    @Test void autoHideCountsTenFocusedSeconds() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(CardLayer.AUTO_HIDE));
        live().getFirst().task().run();
        assertThat(layer.showing()).isEmpty();
        assertThat(center.find(a.id()).orElseThrow().read()).isFalse();
    }

    @Test void theCountdownPausesWhileUnfocusedOrHovered() {
        var layer = layer(false);
        var a = post(CARDS, "a");
        layer.show(a);
        assertThat(live()).isEmpty();
        layer.windowFocusChanged(true);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(10)));
        now += Duration.ofSeconds(4).toNanos();
        layer.windowFocusChanged(false);
        assertThat(live()).isEmpty();
        layer.windowFocusChanged(true);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(6)));
        now += Duration.ofSeconds(1).toNanos();
        layer.card(a.id()).setHovered(true);
        assertThat(live()).isEmpty();
        layer.card(a.id()).setHovered(false);
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(Duration.ofSeconds(5)));
    }

    @Test void stickyCardsNeverCountDown() {
        var layer = layer(true);
        layer.show(post(STICKY, "s"));
        assertThat(live()).isEmpty();
    }

    @Test void cardsCloseWhenReadExpiredRemovedOrCleared() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        var b = post(CARDS, "b");
        var c = post(CARDS, "c");
        for (var entry : List.of(a, b, c)) layer.show(entry);
        center.markRead(a.id());
        assertThat(layer.showing()).containsExactly(b.id(), c.id());
        center.expire(b.id());
        assertThat(layer.showing()).containsExactly(c.id());
        center.remove(c.id());
        assertThat(layer.showing()).isEmpty();
        layer.show(post(CARDS, "d"));
        layer.show(post(CARDS, "e"));
        center.clear();
        assertThat(layer.showing()).isEmpty();
        assertThat(layer.isVisible()).isFalse();
    }

    @Test void aRepeatRefreshesTheSameCardAndRestartsItsCountdown() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        layer.show(a);
        now += Duration.ofSeconds(7).toNanos();
        var again = post(CARDS, "a");
        assertThat(again.id()).isEqualTo(a.id());
        layer.show(again);
        assertThat(layer.showing()).containsExactly(a.id());
        assertThat(layer.card(a.id()).countText()).isEqualTo("×2");
        assertThat(live()).singleElement().satisfies(item -> assertThat(item.delay()).isEqualTo(CardLayer.AUTO_HIDE));
    }

    @Test void dismissAllReadsEveryCardShowingOrWaiting() {
        var layer = layer(true);
        List<UUID> ids = new ArrayList<>();
        for (String title : List.of("s1", "s2", "s3", "s4")) {
            var entry = post(STICKY, title);
            ids.add(entry.id());
            layer.show(entry);
        }
        layer.dismissAll();
        assertThat(layer.showing()).isEmpty();
        assertThat(layer.waitingIds()).isEmpty();
        assertThat(ids).allSatisfy(id -> assertThat(center.find(id).orElseThrow().read()).isTrue());
    }

    @Test void aCardForAReadEntryNeverShows() {
        var layer = layer(true);
        var a = post(CARDS, "a");
        center.markRead(a.id());
        layer.show(a);
        assertThat(layer.showing()).isEmpty();
    }

    @Test void aClosedLayerIgnoresTheCenter() {
        var layer = layer(true);
        layer.show(post(CARDS, "a"));
        layer.close();
        assertThat(layer.showing()).isEmpty();
        assertThat(live()).isEmpty();
        layer.show(post(CARDS, "b"));
        assertThat(layer.showing()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.CardLayerTest'`
Expected: FAIL — `CardLayer` does not exist.

- [ ] **Step 3: Write the implementation**

`WindowFocus.java`:

```java
package dev.jasper.app.notificationui;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Follows whether a component's window is the active one, across re-parenting. EDT only. */
final class WindowFocus {
    private WindowFocus() {}

    /** Calls {@code changed} with the window's state when it gets one, then on every activation change; returns the stop. */
    static Runnable track(Component component, Consumer<Boolean> changed) {
        var listener = new WindowAdapter() {
            @Override public void windowActivated(WindowEvent event) { changed.accept(true); }
            @Override public void windowDeactivated(WindowEvent event) { changed.accept(false); }
        };
        Window[] current = {null};
        Runnable sync = () -> {
            Window next = SwingUtilities.getWindowAncestor(component);
            if (next == current[0]) return;
            if (current[0] != null) current[0].removeWindowListener(listener);
            current[0] = next;
            if (next != null) {
                next.addWindowListener(listener);
                changed.accept(next.isActive());
            }
        };
        HierarchyListener hierarchy = event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) sync.run();
        };
        component.addHierarchyListener(hierarchy);
        sync.run();
        return () -> {
            component.removeHierarchyListener(hierarchy);
            if (current[0] != null) current[0].removeWindowListener(listener);
            current[0] = null;
        };
    }
}
```

`CardLayer.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * One window's stack of notification cards for its content area's bottom-right corner (spec section 3): newest at the
 * bottom, at most {@value #VISIBLE}; a fourth pushes out the oldest auto-hiding card, and when every visible card is
 * sticky the rest wait behind a "+N more" chip that opens the history. An auto-hiding card counts ten seconds of
 * focused, unhovered time. The layer has no mouse listeners and is never focusable, so every click beside a card
 * reaches the terminal and the shell keeps the keyboard. No animation. EDT only; close it when its window closes.
 */
public final class CardLayer extends JComponent implements AutoCloseable {
    public static final int VISIBLE = 3;
    public static final Duration AUTO_HIDE = Duration.ofSeconds(10);
    /** Room around the stack: the cards' inset from the content corner, and their shadow. */
    static final int MARGIN = 12;
    static final int GAP = 8;

    private final NotificationCenter center;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final LongSupplier nanoTime;
    /** Top to bottom: oldest to newest. */
    private final List<Slot> slots = new ArrayList<>();
    /** Waiting for room, oldest first. */
    private final List<UUID> waiting = new ArrayList<>();
    private final JButton more;
    private final Subscription subscription;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean closed;

    private static final class Slot {
        final NotificationCard card;
        long remaining = AUTO_HIDE.toNanos();
        long started;
        Runnable cancel;
        boolean hovered;
        Slot(NotificationCard card) { this.card = card; }
        UUID id() { return card.notification().id(); }
    }

    public CardLayer(NotificationCenter center, boolean focused, Runnable openHistory) {
        this(center, focused, openHistory, CardLayer::swingTimer, System::nanoTime);
    }

    CardLayer(NotificationCenter center, boolean focused, Runnable openHistory,
              BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime) {
        this.center = Objects.requireNonNull(center, "center");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.focused = focused;
        Objects.requireNonNull(openHistory, "openHistory");
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        more = NotificationStyle.link("", openHistory);
        more.setVisible(false);
        add(more);
        subscription = center.onChange(this::changed);
        stopTracking = WindowFocus.track(this, this::windowFocusChanged);
        refresh();
    }

    /** Shows a card for the entry, or refreshes and restarts the one already showing. */
    public void show(Notification notification) {
        if (closed) return;
        Notification latest = center.find(notification.id()).orElse(null);
        if (latest == null || latest.read() || latest.expired()) return;
        Slot existing = slot(latest.id());
        if (existing != null) {
            existing.card.update(latest);
            pause(existing);
            existing.remaining = AUTO_HIDE.toNanos();
            resume(existing);
            refresh();
            return;
        }
        if (!waiting.contains(latest.id())) {
            if (slots.size() >= VISIBLE) {
                Slot oldest = slots.stream().filter(slot -> !slot.card.sticky()).findFirst().orElse(null);
                if (oldest == null) waiting.add(latest.id());
                else {
                    discard(oldest);
                    place(latest);
                }
            } else place(latest);
        }
        refresh();
    }

    /** Dismiss Cards: every card here, showing or waiting, closes and is read. */
    public void dismissAll() {
        var ids = new ArrayList<UUID>();
        for (Slot slot : slots) ids.add(slot.id());
        ids.addAll(waiting);
        for (UUID id : ids) center.markRead(id);
    }

    /** The entries with a card showing, top to bottom. */
    public List<UUID> showing() { return slots.stream().map(Slot::id).toList(); }

    List<UUID> waitingIds() { return List.copyOf(waiting); }

    JButton moreButton() { return more; }

    NotificationCard card(UUID id) {
        Slot slot = slot(id);
        return slot == null ? null : slot.card;
    }

    void windowFocusChanged(boolean value) {
        if (focused == value) return;
        focused = value;
        for (Slot slot : slots) {
            if (value) resume(slot);
            else pause(slot);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        stopTracking.run();
        for (Slot slot : List.copyOf(slots)) discard(slot);
        waiting.clear();
        refresh();
    }

    @Override public void updateUI() {
        super.updateUI();
        // The chip's link colour is a plain colour, so a theme change has to set it again.
        if (more != null) more.setForeground(NotificationStyle.color("Jasper.notification.linkForeground", Color.BLUE));
    }

    private Slot slot(UUID id) {
        for (Slot slot : slots) if (slot.id().equals(id)) return slot;
        return null;
    }

    private void place(Notification notification) {
        boolean sticky = center.mode(notification.request().group()) == DisplayMode.STICKY;
        var slot = new Slot(new NotificationCard(center, notification, sticky));
        slot.card.onHover = hovered -> {
            slot.hovered = hovered;
            if (hovered) pause(slot);
            else resume(slot);
        };
        slots.add(slot);
        add(slot.card);
        resume(slot);
    }

    private void discard(Slot slot) {
        pause(slot);
        slots.remove(slot);
        remove(slot.card);
    }

    private void promote() {
        while (slots.size() < VISIBLE && !waiting.isEmpty()) {
            Notification next = center.find(waiting.removeFirst()).orElse(null);
            if (next != null && !next.read() && !next.expired()) place(next);
        }
    }

    private void resume(Slot slot) {
        if (closed || slot.card.sticky() || slot.hovered || !focused || slot.cancel != null) return;
        slot.started = nanoTime.getAsLong();
        slot.cancel = schedule.apply(Duration.ofNanos(slot.remaining), () -> hidden(slot));
    }

    private void pause(Slot slot) {
        if (slot.cancel == null) return;
        slot.cancel.run();
        slot.cancel = null;
        slot.remaining = Math.max(0, slot.remaining - (nanoTime.getAsLong() - slot.started));
    }

    /** The countdown ran out: the card leaves; the entry stays unread in history. */
    private void hidden(Slot slot) {
        slot.cancel = null;
        if (!slots.remove(slot)) return;
        remove(slot.card);
        promote();
        refresh();
    }

    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                boolean gone = entry.read() || entry.expired();
                Slot slot = slot(entry.id());
                if (gone) {
                    waiting.remove(entry.id());
                    if (slot != null) discard(slot);
                    promote();
                } else if (slot != null) slot.card.update(entry);
                refresh();
            }
            case NotificationCenter.Change.Removed removed -> {
                waiting.remove(removed.id());
                Slot slot = slot(removed.id());
                if (slot != null) discard(slot);
                promote();
                refresh();
            }
            case NotificationCenter.Change.Cleared cleared -> {
                waiting.clear();
                for (Slot slot : List.copyOf(slots)) discard(slot);
                refresh();
            }
            case NotificationCenter.Change.CardRequested requested -> { }
        }
    }

    /** Re-lays the stack and publishes its size; the window keeps the layer in its corner from that. */
    private void refresh() {
        more.setText("+" + waiting.size() + " more");
        more.setVisible(!waiting.isEmpty());
        setPreferredSize(stackSize());
        setVisible(!slots.isEmpty() || !waiting.isEmpty());
        revalidate();
        repaint();
    }

    private Dimension stackSize() {
        if (slots.isEmpty() && waiting.isEmpty()) return new Dimension(0, 0);
        int margin = UIScale.scale(MARGIN), gap = UIScale.scale(GAP);
        int height = margin * 2;
        if (more.isVisible()) height += more.getPreferredSize().height + (slots.isEmpty() ? 0 : gap);
        for (int i = 0; i < slots.size(); i++) height += slots.get(i).card.getPreferredSize().height + (i > 0 ? gap : 0);
        return new Dimension(UIScale.scale(NotificationCard.WIDTH) + margin * 2, height);
    }

    @Override public void doLayout() {
        int margin = UIScale.scale(MARGIN), gap = UIScale.scale(GAP), width = UIScale.scale(NotificationCard.WIDTH);
        // Bottom-aligned, so a clipped layer still shows the newest card.
        int y = getHeight() - margin;
        for (int i = slots.size() - 1; i >= 0; i--) {
            NotificationCard card = slots.get(i).card;
            int height = card.getPreferredSize().height;
            y -= height;
            card.setBounds(margin, y, width, height);
            y -= gap;
        }
        if (slots.isEmpty()) y += gap;
        if (more.isVisible()) {
            Dimension size = more.getPreferredSize();
            more.setBounds(margin + width - size.width, y - size.height, size.width, size.height);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (slots.isEmpty()) return;
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color shadow = UIManager.getColor("Popup.dropShadowColor");
            if (shadow == null) shadow = Color.BLACK;
            g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), FlatLaf.isLafDark() ? 4 : 2));
            int arc = UIScale.scale(NotificationCard.ARC);
            for (Slot slot : slots) {
                Rectangle card = slot.card.getBounds();
                for (int i = MARGIN; i >= 1; i--) {
                    int grow = UIScale.scale(i);
                    g.fillRoundRect(card.x - grow, card.y - grow, card.width + grow * 2, card.height + grow * 2, arc + grow, arc + grow);
                }
            }
        } finally { g.dispose(); }
    }

    /** Swing's timer as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable swingTimer(Duration delay, Runnable task) {
        var timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notificationui/WindowFocus.java jasper-app/src/main/java/dev/jasper/app/notificationui/CardLayer.java jasper-app/src/test/java/dev/jasper/app/notificationui/CardLayerTest.java
git commit -m "feat(notifications): stack cards in a per-window layer" -m "Three visible cards, a waiting chip behind sticky ones, and auto-hide that counts only focused, unhovered time." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: The window's corner mount

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowCornerLayerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (a plain `JComponent`).
- Produces: `public void WindowContent.mountCornerLayer(JComponent layer)` (once per window; installed on the root's
  `JLayeredPane.PALETTE_LAYER` whenever root bindings are installed; kept flush with the bottom-right of the content
  area — the `WorkspaceRegions` bounds — at the layer's preferred size, clipped to the area; re-laid on layered-pane,
  regions and `preferredSize` changes; removed at `close()`); package-private `void layoutCornerLayer()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.testsupport.LayoutTestSupport.layoutTree;
import static dev.jasper.app.workspace.DesktopTestSupport.content;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowCornerLayerTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void theLayerSitsFlushInTheContentCornerOnThePaletteLayer() throws Exception {
        var owner = content(launcher(new ArrayDeque<>()));
        var layer = new JPanel();
        layer.setPreferredSize(new Dimension(200, 120));
        owner.mountCornerLayer(layer);
        assertThat(layer.getParent()).as("nothing to mount on before the window has a root").isNull();
        var root = new JRootPane();
        try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
            owner.installRootBindings(root);
            root.setSize(1000, 640);
            layoutTree(root);
            layoutTree(root);
            owner.layoutCornerLayer();
            assertThat(layer.getParent()).isSameAs(root.getLayeredPane());
            assertThat(JLayeredPane.getLayer(layer)).isEqualTo(JLayeredPane.PALETTE_LAYER.intValue());
            Rectangle area = SwingUtilities.convertRectangle(owner.regions().getParent(), owner.regions().getBounds(), root.getLayeredPane());
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120));
            layer.setPreferredSize(new Dimension(300, 50));
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 300, area.y + area.height - 50, 300, 50));
            layer.setPreferredSize(new Dimension(5000, 5000));
            assertThat(layer.getBounds()).as("clipped to the content area").isEqualTo(area);
            owner.rebind();
            assertThat(Arrays.stream(root.getLayeredPane().getComponents()).filter(component -> component == layer)).hasSize(1);
            owner.close();
            assertThat(layer.getParent()).isNull();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowCornerLayerTest'`
Expected: FAIL — `mountCornerLayer` does not exist.

- [ ] **Step 3: Write the implementation**

In `WindowContent.java`, add these fields after `private JRootPane bindingRoot;`:

```java
    private JComponent cornerLayer;
    private JRootPane cornerRoot;
    private final ComponentAdapter cornerRelayout = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutCornerLayer(); }
        @Override public void componentMoved(ComponentEvent event) { layoutCornerLayer(); }
    };
    private final java.beans.PropertyChangeListener cornerResized = event -> layoutCornerLayer();
```

In `installRootBindings(JRootPane root)`, add `installCornerLayer(root);` directly after `commandPalette.install(root);`.

Add these methods after `removeRootBindings()`:

```java
    /**
     * The application's notification card layer: kept flush with the bottom-right corner of the content area (inside
     * the rail, above the status bar, over any side or bottom panel) on the root's palette layer, at its preferred size.
     * Once per window; removed at close.
     */
    public void mountCornerLayer(JComponent layer) {
        if (closed || cornerLayer != null) return;
        cornerLayer = Objects.requireNonNull(layer, "layer");
        layer.addPropertyChangeListener("preferredSize", cornerResized);
        if (bindingRoot != null) installCornerLayer(bindingRoot);
    }

    private void installCornerLayer(JRootPane root) {
        if (cornerLayer == null || cornerRoot == root) return;
        uninstallCornerLayer();
        cornerRoot = root;
        root.getLayeredPane().add(cornerLayer, JLayeredPane.PALETTE_LAYER);
        root.getLayeredPane().addComponentListener(cornerRelayout);
        regions.addComponentListener(cornerRelayout);
        layoutCornerLayer();
    }

    private void uninstallCornerLayer() {
        if (cornerRoot == null) return;
        cornerRoot.getLayeredPane().removeComponentListener(cornerRelayout);
        regions.removeComponentListener(cornerRelayout);
        cornerRoot.getLayeredPane().remove(cornerLayer);
        cornerRoot.repaint();
        cornerRoot = null;
    }

    void layoutCornerLayer() {
        if (cornerRoot == null || cornerLayer == null) return;
        JLayeredPane pane = cornerRoot.getLayeredPane();
        if (!SwingUtilities.isDescendingFrom(regions, pane)) { cornerLayer.setBounds(0, 0, 0, 0); return; }
        Rectangle area = SwingUtilities.convertRectangle(regions.getParent(), regions.getBounds(), pane);
        Dimension size = cornerLayer.getPreferredSize();
        int width = Math.min(size.width, area.width), height = Math.min(size.height, area.height);
        cornerLayer.setBounds(area.x + area.width - width, area.y + area.height - height, width, height);
        cornerLayer.revalidate();
        cornerLayer.repaint();
    }
```

In `close()`, add directly after `if (windowTabs != null) windowTabs.close();`:

```java
        uninstallCornerLayer();
        if (cornerLayer != null) { cornerLayer.removePropertyChangeListener("preferredSize", cornerResized); cornerLayer = null; }
```

(`java.awt.*` and `javax.swing.*` are already imported by wildcard; `ComponentAdapter`/`ComponentEvent` come from the
existing `java.awt.event.*` import.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowCornerLayerTest' --tests 'dev.jasper.app.workspace.WindowCommandPaletteTest' --tests 'dev.jasper.app.workspace.GraphiteChromeTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowCornerLayerTest.java
git commit -m "feat(workspace): mount a corner layer over the content area" -m "Windows keep an application-supplied layer flush with the content area's bottom-right corner on the palette layer." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: The history panel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/WrappedText.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryRow.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/HistoryPanelTest.java`

**Interfaces:**
- Consumes: `NotificationCenter`, `Notification`, `NotificationGroup`, `NotificationStyle` (Task 5), `WindowFocus`
  (Task 6), `PanelSite` (`onVisibility`, `onClosed`, `visible`, `notifyVisibility`, `notifyClosed`).
- Produces: public `HistoryPanel(NotificationCenter center, PanelSite site, boolean focused)`; package-private
  `windowFocusChanged(boolean)`, `newSection()`, `earlierSection()`, `showingEmpty()`, `row(UUID)`,
  `clearAllButton()`; package-private `HistoryRow` with `notification()`, `update(Notification)`, `titleText()`,
  `titleBold()`, `metaText()`, `countText()`, `actionButtons()`, `removeButton()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.notificationui;

import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class HistoryPanelTest {
    private static final NotificationGroup GENERAL = new NotificationGroup("jasper.general", "Jasper", "jasper", DisplayMode.CARD);
    private final NotificationCenter center = new NotificationCenter(Runnable::run,
        Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
    private final boolean[] visible = {false};
    private PanelSite site;

    private HistoryPanel panel(boolean focused) {
        center.register(GENERAL);
        site = new PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> visible[0]);
        return new HistoryPanel(center, site, focused);
    }

    private void show() { visible[0] = true; site.notifyVisibility(true); }
    private void hide() { visible[0] = false; site.notifyVisibility(false); }

    private UUID post(String title) {
        center.register(GENERAL);
        return center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, title)).orElseThrow();
    }

    @Test void anEmptyHistorySaysSo() {
        var panel = panel(true);
        show();
        assertThat(panel.showingEmpty()).isTrue();
        assertThat(panel.clearAllButton().isEnabled()).isFalse();
    }

    @Test void showingInAFocusedWindowReadsEverythingAndKeepsItUnderNewUntilHidden() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        assertThat(panel.newSection()).containsExactly(b, a);
        assertThat(panel.earlierSection()).isEmpty();
        assertThat(center.unread()).isZero();
        assertThat(panel.row(a).titleBold()).isFalse();
        hide();
        show();
        assertThat(panel.newSection()).isEmpty();
        assertThat(panel.earlierSection()).containsExactly(b, a);
    }

    @Test void anUnfocusedWindowReadsNothingUntilFocused() {
        UUID a = post("a");
        var panel = panel(false);
        show();
        assertThat(center.unread()).isEqualTo(1);
        assertThat(panel.earlierSection()).containsExactly(a);
        assertThat(panel.row(a).titleBold()).isTrue();
        panel.windowFocusChanged(true);
        assertThat(center.unread()).isZero();
        assertThat(panel.newSection()).containsExactly(a);
    }

    @Test void aNotificationArrivingWhileLookingIsReadAndNew() {
        var panel = panel(true);
        show();
        UUID c = post("c");
        assertThat(center.find(c).orElseThrow().read()).isTrue();
        assertThat(panel.newSection()).containsExactly(c);
    }

    @Test void aHiddenPanelReadsNothing() {
        panel(true);
        post("unseen");
        assertThat(center.unread()).isEqualTo(1);
    }

    @Test void clearAllAndRowRemoval() {
        UUID a = post("a");
        UUID b = post("b");
        var panel = panel(true);
        show();
        panel.row(a).removeButton().doClick();
        assertThat(center.history()).extracting(entry -> entry.id()).containsExactly(b);
        assertThat(panel.row(a)).isNull();
        panel.clearAllButton().doClick();
        assertThat(center.history()).isEmpty();
        assertThat(panel.showingEmpty()).isTrue();
    }

    @Test void rowsShowGroupTimeCountAndPlainTitles() {
        UUID id = post("<html><i>x</i>");
        var panel = panel(true);
        show();
        assertThat(panel.row(id).titleText()).isEqualTo("<html><i>x</i>");
        assertThat(panel.row(id).metaText()).isEqualTo("Jasper · 14:05");
        assertThat(panel.row(id).countText()).isEmpty();
        post("<html><i>x</i>");
        assertThat(panel.row(id).countText()).isEqualTo("×2");
    }

    @Test void expiredActionsAreDisabled() {
        center.register(GENERAL);
        var ran = new ArrayList<String>();
        UUID id = center.post(NotificationRequest.of(GENERAL.id(), Severity.INFO, "act")
            .withAction(NotificationAction.of("Fix", () -> ran.add("fix")))).orElseThrow();
        var panel = panel(true);
        show();
        assertThat(panel.row(id).actionButtons()).singleElement().satisfies(button -> assertThat(button.isEnabled()).isTrue());
        panel.row(id).actionButtons().getFirst().doClick();
        assertThat(ran).containsExactly("fix");
        assertThat(panel.row(id).actionButtons().getFirst().isEnabled()).isFalse();
    }

    @Test void aClosedSiteStopsListening() {
        var panel = panel(true);
        show();
        site.notifyClosed();
        UUID late = post("late");
        assertThat(panel.row(late)).isNull();
        assertThat(center.find(late).orElseThrow().read()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.HistoryPanelTest'`
Expected: FAIL — `HistoryPanel` does not exist.

- [ ] **Step 3: Write the implementation**

`WrappedText.java`:

```java
package dev.jasper.app.notificationui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.View;

/**
 * Read-only plain text that wraps to the width its row gives it and never asks for more (the same approach as the
 * Settings dialog's wrapping text). A text area never interprets markup. EDT only.
 */
final class WrappedText extends JTextArea {
    private static final int NOMINAL_WIDTH = 200;
    private final String colorKey;
    private boolean bold;

    WrappedText(String text, String colorKey, boolean bold) {
        super(text);
        this.colorKey = colorKey;
        this.bold = bold;
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        restyle();
    }

    void setBold(boolean value) {
        if (bold == value) return;
        bold = value;
        restyle();
        revalidate();
    }

    @Override public void updateUI() {
        super.updateUI();
        // JTextArea's constructor calls this before the fields exist; the constructor styles the text itself.
        if (colorKey != null) restyle();
    }

    private void restyle() {
        setBorder(BorderFactory.createEmptyBorder());
        Font label = UIManager.getFont("Label.font");
        if (label != null) setFont(bold ? label.deriveFont(Font.BOLD) : label);
        setForeground(NotificationStyle.color(colorKey, Color.BLACK));
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(NOMINAL_WIDTH, heightFor(getWidth() > 0 ? getWidth() : NOMINAL_WIDTH));
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    private int heightFor(int width) {
        Insets insets = getInsets();
        View root = getUI().getRootView(this);
        root.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
        return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        int before = getWidth();
        super.setBounds(x, y, width, height);
        // The height follows the width, which the layout has only just decided.
        if (width != before) SwingUtilities.invokeLater(this::revalidate);
    }
}
```

`HistoryRow.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** One history entry: icon, title (bold while unread) and count, full content, group and time, actions, remove. EDT only. */
final class HistoryRow extends JPanel {
    private Notification notification;
    private final WrappedText title;
    private final JLabel count = new JLabel();
    private final JLabel meta = new JLabel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JButton remove = new JButton();

    HistoryRow(NotificationCenter center, Notification notification, String groupName, DateTimeFormatter time) {
        super(new BorderLayout(UIScale.scale(8), 0));
        this.notification = notification;
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, NotificationStyle.color("Separator.foreground", Color.LIGHT_GRAY)),
            BorderFactory.createEmptyBorder(8, 10, 8, 6)));
        var icon = new JLabel(NotificationStyle.icon(notification));
        icon.setVerticalAlignment(SwingConstants.TOP);
        add(icon, BorderLayout.WEST);
        var body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        title = new WrappedText(notification.request().title(), "Label.foreground", !notification.read());
        var titleRow = new JPanel(new BorderLayout(UIScale.scale(6), 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.add(title, BorderLayout.CENTER);
        count.putClientProperty("html.disable", Boolean.TRUE);
        count.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        count.setVerticalAlignment(SwingConstants.TOP);
        titleRow.add(count, BorderLayout.EAST);
        body.add(titleRow);
        if (!notification.request().content().isEmpty()) {
            var content = new WrappedText(notification.request().content(), "Label.foreground", false);
            content.setAlignmentX(LEFT_ALIGNMENT);
            body.add(Box.createVerticalStrut(UIScale.scale(2)));
            body.add(content);
        }
        meta.putClientProperty("html.disable", Boolean.TRUE);
        meta.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        meta.setText(groupName + " · " + time.format(notification.posted()));
        meta.setAlignmentX(LEFT_ALIGNMENT);
        body.add(Box.createVerticalStrut(UIScale.scale(4)));
        body.add(meta);
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        List<NotificationAction> all = notification.request().actions();
        for (int i = 0; i < all.size(); i++) {
            int index = i;
            JButton link = NotificationStyle.link(all.get(i).label(), () -> center.runAction(this.notification.id(), index));
            if (i > 0) link.setBorder(BorderFactory.createEmptyBorder(2, UIScale.scale(12), 2, 0));
            actions.add(link);
        }
        if (!all.isEmpty()) {
            body.add(Box.createVerticalStrut(UIScale.scale(4)));
            body.add(actions);
        }
        add(body, BorderLayout.CENTER);
        remove.setIcon(AppIcons.bundled("general/closeSmall"));
        remove.setRolloverIcon(AppIcons.bundled("general/closeSmallHovered"));
        remove.putClientProperty("JButton.buttonType", "borderless");
        remove.setFocusable(false);
        remove.setToolTipText("Remove");
        remove.getAccessibleContext().setAccessibleName("Remove notification");
        remove.addActionListener(event -> center.remove(this.notification.id()));
        var side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.add(remove, BorderLayout.NORTH);
        add(side, BorderLayout.EAST);
        update(notification);
    }

    Notification notification() { return notification; }

    /** The same entry with a new read or expired state. */
    void update(Notification next) {
        notification = next;
        title.setBold(!next.read());
        count.setText(next.count() > 1 ? "×" + next.count() : "");
        for (Component link : actions.getComponents()) link.setEnabled(!next.expired());
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(next.request().severity()) + ": " + next.request().title());
    }

    String titleText() { return title.getText(); }
    boolean titleBold() { return title.getFont().isBold(); }
    String metaText() { return meta.getText(); }
    String countText() { return count.getText(); }
    List<JButton> actionButtons() { return Arrays.stream(actions.getComponents()).map(JButton.class::cast).toList(); }
    JButton removeButton() { return remove; }
}
```

`HistoryPanel.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The Notifications panel's view in one window (spec section 4): New and Earlier sections over the shared center,
 * newest first, with Clear All. Showing it in a focused window marks everything read; what was unread stays under New
 * until the panel hides. EDT only; stops listening when its site closes.
 */
public final class HistoryPanel extends JPanel {
    private final NotificationCenter center;
    private final Set<UUID> fresh = new LinkedHashSet<>();
    private final Rows rows = new Rows();
    private final JLabel empty = new JLabel("No notifications", SwingConstants.CENTER);
    private final JButton clearAll = new JButton("Clear All");
    private final DateTimeFormatter time;
    private final Map<UUID, HistoryRow> shown = new LinkedHashMap<>();
    private final Subscription subscription;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean visible;
    private boolean closed;

    public HistoryPanel(NotificationCenter center, PanelSite site, boolean focused) {
        super(new BorderLayout());
        this.center = Objects.requireNonNull(center, "center");
        this.focused = focused;
        time = DateTimeFormatter.ofPattern("HH:mm").withZone(center.clock().getZone());
        getAccessibleContext().setAccessibleName("Notifications");
        var header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        var heading = new JLabel("Notifications");
        heading.putClientProperty("html.disable", Boolean.TRUE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        header.add(heading, BorderLayout.WEST);
        clearAll.putClientProperty("JButton.buttonType", "borderless");
        clearAll.setToolTipText("Remove every notification");
        clearAll.addActionListener(event -> center.clear());
        header.add(clearAll, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        empty.putClientProperty("html.disable", Boolean.TRUE);
        empty.setAlignmentX(LEFT_ALIGNMENT);
        empty.setBorder(BorderFactory.createEmptyBorder(24, 10, 24, 10));
        var scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        subscription = center.onChange(this::changed);
        stopTracking = WindowFocus.track(this, this::windowFocusChanged);
        site.onVisibility(this::visibilityChanged);
        site.onClosed(this::close);
        visible = site.visible();
        rebuild();
        readIfLooking();
    }

    void windowFocusChanged(boolean value) {
        focused = value;
        readIfLooking();
    }

    List<UUID> newSection() { return center.history().stream().map(Notification::id).filter(fresh::contains).toList(); }

    List<UUID> earlierSection() { return center.history().stream().map(Notification::id).filter(id -> !fresh.contains(id)).toList(); }

    boolean showingEmpty() { return empty.getParent() == rows; }

    HistoryRow row(UUID id) { return shown.get(id); }

    JButton clearAllButton() { return clearAll; }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist. Rows hold plain colours, so a theme change rebuilds them.
        if (center != null) rebuild();
    }

    private void visibilityChanged(boolean value) {
        visible = value;
        if (!value) fresh.clear();
        rebuild();
        readIfLooking();
    }

    /** Looking at the panel in a focused window reads everything. */
    private void readIfLooking() {
        if (closed || !visible || !focused) return;
        boolean any = false;
        for (Notification entry : center.history()) {
            if (entry.read()) continue;
            fresh.add(entry.id());
            any = true;
        }
        if (!any) return;
        center.markAllRead();
        rebuild();
    }

    private void changed(NotificationCenter.Change change) {
        if (closed) return;
        switch (change) {
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                if (!entry.read() && visible && focused) {
                    fresh.add(entry.id());
                    center.markRead(entry.id());
                }
                HistoryRow row = shown.get(entry.id());
                if (row != null && row.notification().count() == entry.count()) row.update(entry);
                else rebuild();
            }
            case NotificationCenter.Change.Removed removed -> {
                fresh.remove(removed.id());
                rebuild();
            }
            case NotificationCenter.Change.Cleared cleared -> {
                fresh.clear();
                rebuild();
            }
            case NotificationCenter.Change.CardRequested requested -> { }
        }
    }

    private void rebuild() {
        if (closed || !visible) return;
        rows.removeAll();
        shown.clear();
        List<Notification> all = center.history();
        if (all.isEmpty()) rows.add(empty);
        section("New", all.stream().filter(entry -> fresh.contains(entry.id())).toList());
        section("Earlier", all.stream().filter(entry -> !fresh.contains(entry.id())).toList());
        clearAll.setEnabled(!all.isEmpty());
        rows.revalidate();
        rows.repaint();
    }

    private void section(String name, List<Notification> entries) {
        if (entries.isEmpty()) return;
        var heading = new JLabel(name);
        heading.putClientProperty("html.disable", Boolean.TRUE);
        heading.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.getAccessibleContext().setAccessibleName(name + " notifications");
        rows.add(heading);
        for (Notification entry : entries) {
            String group = center.group(entry.request().group()).map(NotificationGroup::name).orElse(entry.request().group());
            var row = new HistoryRow(center, entry, group, time);
            row.setAlignmentX(LEFT_ALIGNMENT);
            shown.put(entry.id(), row);
            rows.add(row);
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        stopTracking.run();
    }

    /** Rows that follow the viewport's width, so text wraps instead of scrolling sideways. */
    private static final class Rows extends JPanel implements Scrollable {
        Rows() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return UIScale.scale(16); }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(UIScale.scale(16), visible.height - UIScale.scale(16));
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notificationui.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notificationui/WrappedText.java jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryRow.java jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java jasper-app/src/test/java/dev/jasper/app/notificationui/HistoryPanelTest.java
git commit -m "feat(notifications): add the history panel view" -m "New and Earlier sections, read on looking in a focused window, Clear All and per-row removal." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Titled window messages and problem publication

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (interim, two lines)
- Modify: `jasper-app/src/main/java/dev/jasper/app/benchmark/BenchmarkRun.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`
- Modify tests: `workspace/ShellExitBehaviorTest.java`, `workspace/TerminalTabTest.java`,
  `workspace/ConfigurationControllerTest.java`, `workspace/ThemeControllerTest.java`, `workspace/WindowCommandPaletteTest.java`
- Create test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowMessagesTest.java`

**Interfaces:**
- Produces: `public BiConsumer<String, String> WindowContent.onError` and `onInfo` (title, details; default logs the
  title at WARNING and never opens a dialog); `public void WindowContent.showConfigurationDetails()`;
  package-private `void ConfigurationController.onProblems(Consumer<ConfigService.State>)` (replays at once, then fires
  after every change to the merged problems); `ConfigurationTestSupport.onProblems(...)`.
- Titles used: "Could not start the terminal", "Could not apply the theme", "Settings not saved", "Command failed",
  "Could not open Settings", "Could not reload the configuration", "Migrate Config", "Migrate Config failed".

- [ ] **Step 1: Update existing tests to the new shape and write the new ones**

Run this from the repository root to convert the method-reference sinks (macOS `sed`):

```bash
sed -i '' -E 's/onError = ([a-zA-Z]+)::add;/onError = (title, message) -> \1.add(message);/; s/onInfo = ([a-zA-Z]+)::add;/onInfo = (title, message) -> \1.add(title + ": " + message);/' \
  jasper-app/src/test/java/dev/jasper/app/workspace/ShellExitBehaviorTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandPaletteTest.java
```

Then make these edits by hand:

In `TerminalTabTest.java`, replace `owner[0].onError = errors::add;` with
`owner[0].onError = (title, message) -> errors.add(title + ": " + message);` and, after
`assertThat(errors).singleElement().asString().contains("shell executable unavailable");`, add
`assertThat(errors.getFirst()).startsWith("Could not start the terminal: ");`.

In `ConfigurationControllerTest.java`:
- In `settingsFailureIsVisibleOnEdtAndDoesNotRollBackCreatedConfiguration`, replace
  `owner.onError = message -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); errors.add(message); };` with
  `owner.onError = (title, message) -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); errors.add(title + ": " + message); };`
  and replace `assertThat(errors.poll(5, java.util.concurrent.TimeUnit.SECONDS)).contains("Editor unavailable");` with
  `assertThat(errors.poll(5, java.util.concurrent.TimeUnit.SECONDS)).startsWith("Could not open Settings: ").contains("Editor unavailable");`.
- In the Migrate Config test, replace
  `assertThat(infos).containsExactly("Migrated 1 old setting to the new layout. Backup: config.toml.v1.bak.");` with
  `assertThat(infos).containsExactly("Migrate Config: Migrated 1 old setting to the new layout. Backup: config.toml.v1.bak.");`.
- In `aRejectedFileRefusesViewChoicesAndTheMenuStaysTruthful`, replace the sed-converted
  `owner.onError = (title, message) -> errors.add(message);` with `owner.onError = (title, message) -> errors.add(title + ": " + message);`
  and the assertion's expected text with `"Settings not saved: config.toml has errors, so changes can't be saved until they're fixed."`.
- Add this test method:

```java
    @Test void problemsReachTheApplicationWheneverTheyChange() throws Exception {
        start("[terminal.font]\nsize = 19\n");
        var states = new ArrayList<ConfigService.State>();
        var plugin = directory.resolve("plugins/dev.example/dev.example.toml");
        edt(() -> {
            controller.onProblems(states::add);
            assertThat(states).hasSize(1);
            controller.report("terminal.shell.profile", "No such shell profile");
            assertThat(states.getLast().diagnostics()).extracting(dev.jasper.app.config.ConfigDiagnostic::message).contains("No such shell profile");
            controller.report(new dev.jasper.app.config.ConfigDiagnostic(dev.jasper.app.config.ConfigDiagnostic.Severity.ERROR, plugin, 2, 1, "x", "Bad value"));
            assertThat(states.getLast().diagnostics()).extracting(dev.jasper.app.config.ConfigDiagnostic::message).contains("Bad value");
            controller.clearReports(plugin);
            assertThat(states.getLast().diagnostics()).extracting(dev.jasper.app.config.ConfigDiagnostic::message).doesNotContain("Bad value");
        });
        reload("[terminal.font]\nsize = 20\n");
        edt(() -> assertThat(states.getLast().diagnostics()).extracting(dev.jasper.app.config.ConfigDiagnostic::message)
            .doesNotContain("No such shell profile"));
    }
```

In `ConfigurationTestSupport.java`, add:

```java
    public void onProblems(Consumer<ConfigService.State> listener) { controller.onProblems(listener); }
```

Create `WindowMessagesTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.content;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(EdtTestExtension.class)
class WindowMessagesTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** Headless, a message dialog would throw; a window without a receiver only logs. */
    @Test void aWindowWithNoReceiverNeverOpensADialog() {
        var owner = content(launcher(new ArrayDeque<>()));
        assertThatCode(() -> {
            owner.onError.accept("Could not start the terminal", "details");
            owner.onInfo.accept("Migrate Config", "done");
        }).doesNotThrowAnyException();
    }

    @Test void configurationDetailsOpenFromOutsideTheStatusBar() {
        var owner = content(launcher(new ArrayDeque<>()));
        boolean[] shown = {false};
        owner.status().onConfigurationDetails = () -> shown[0] = true;
        owner.showConfigurationDetails();
        assertThat(shown[0]).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL — `onError` is still a `Consumer<String>`; `showConfigurationDetails` and `onProblems` do not exist.

- [ ] **Step 3: Write the implementation**

`WindowContent.java`:
- Add the field `private static final System.Logger LOG = System.getLogger(WindowContent.class.getName());` as the
  first field of the class.
- Replace the `onError` field, the `onInfo` javadoc line and the `onInfo` field with:

```java
    /** An error to tell the user about, as a title and details; the application posts it as a notification. */
    public java.util.function.BiConsumer<String, String> onError = WindowContent::unheard;
    /** Information to tell the user, such as the result of Migrate Config; the application posts it as a notification. */
    public java.util.function.BiConsumer<String, String> onInfo = WindowContent::unheard;
```

- Add these methods directly after `setConfigurationState(ConfigService.State state)`:

```java
    /** Opens the status bar's configuration details, as clicking its configuration button does. */
    public void showConfigurationDetails() {
        if (!closed) status().onConfigurationDetails.run();
    }

    /** A message before the application connected a receiver: logged by its fixed title, never shown in a dialog. */
    private static void unheard(String title, String details) {
        LOG.log(System.Logger.Level.WARNING, "A window message had no receiver: " + title);
    }
```

- In `openTab(...)`, replace `tab.onError = message -> onError.accept(message);` with
  `tab.onError = message -> onError.accept("Could not start the terminal", message);`.
- In `selectTheme(Theme)`, replace `onError.accept(failure.getMessage());` with
  `onError.accept("Could not apply the theme", failure.getMessage());`.
- In `choose(...)`, replace `onError.accept(REJECTED_CHOICE);` with `onError.accept("Settings not saved", REJECTED_CHOICE);`
  and `onError.accept(cause.getMessage());` with `onError.accept("Settings not saved", cause.getMessage());`.
- In `close()`, replace `onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};` with
  `onTitle = title -> {}; onError = (title, details) -> {}; onInfo = (title, details) -> {}; onMinimumSizeChanged = () -> {};`.

`WindowCommandPalette.java`: replace `message -> owner.onError.accept(message), this::open);` with
`message -> owner.onError.accept("Command failed", message), this::open);`.

`ConfigurationController.java`:
- Add the field `private Consumer<ConfigService.State> problemListener = state -> {};` after `stateListeners`.
- Replace every one of the four lines `for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());`
  (in `report(ConfigDiagnostic)`, `clearReports`, `report(String, String)` and `pluginSettingsExist`) with `publish();`.
- Add after `onSnapshot(...)`:

```java
    /** One application-level observer of the problems windows show: the current state at once, then after every change. */
    void onProblems(Consumer<ConfigService.State> listener) {
        requireEdt();
        problemListener = java.util.Objects.requireNonNull(listener, "listener");
        if (!closed) listener.accept(shown());
    }

    /** Shows the merged problems in every window and tells the application. */
    private void publish() {
        ConfigService.State shown = shown();
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown);
        problemListener.accept(shown);
    }
```

- In the constructor, replace `settingsOpener = (owner, page) -> reportFailure(owner, service.openSettings(editor));` with
  `settingsOpener = (owner, page) -> reportFailure(owner, "Could not open Settings", service.openSettings(editor));`.
- In `register(...)`, replace `() -> reportFailure(owner, service.reload())` with
  `() -> reportFailure(owner, "Could not reload the configuration", service.reload())`.
- Replace `reportFailure` with:

```java
    private void reportFailure(WindowContent owner, String title, CompletableFuture<?> result) {
        result.whenComplete((ignored, failure) -> {
            if (failure != null) SwingUtilities.invokeLater(() -> {
                if (!closed && owners.contains(owner)) owner.onError.accept(title, failure.getMessage());
            });
        });
    }
```

- In `migrateFor`, replace `if (failure == null) owner.onInfo.accept(message);` with
  `if (failure == null) owner.onInfo.accept("Migrate Config", message);` and
  `else owner.onError.accept((failure instanceof CompletionException ...` with
  `else owner.onError.accept("Migrate Config failed", (failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());`.
- In `accept(...)`, replace `for (WindowContent owner : List.copyOf(owners)) owner.onError.accept(failure.getMessage());`
  with (one notification, not one per window):

```java
            owners.stream().findFirst().ifPresent(owner -> owner.onError.accept("Could not apply the theme", failure.getMessage()));
```

  and add `problemListener.accept(visible);` after the `stateListeners` loop.
- In `close()`, add `problemListener = state -> {};` after `stateListeners.clear();`.

`JasperApplication.java` (interim; Task 10 replaces both methods): in `info(String)` replace
`else target.content().onInfo.accept(message);` with `else target.content().onInfo.accept("Jasper", message);`, and in
`notice(String)` replace `else target.content().onError.accept(message);` with
`else target.content().onError.accept("Jasper", message);`.

`BenchmarkRun.java`: replace both `.onError = message -> samplingFailure = new IllegalStateException(message);` with
`.onError = (title, message) -> samplingFailure = new IllegalStateException(title + ": " + message);`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `grep -rnE "onError = [a-zA-Z]+::add|onInfo = [a-zA-Z]+::add|onError = message ->" jasper-app/src` — expect no output.
Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -u jasper-app/src
git add jasper-app/src/test/java/dev/jasper/app/workspace/WindowMessagesTest.java
git commit -m "refactor(workspace): give window messages titles and drop dialogs" -m "onError and onInfo carry a title and details and never open a modal; configuration problems are published to the application." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Application wiring

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/application/ConfigProblemNotices.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/NotificationsFeatureTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/ConfigProblemNoticesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationNotificationsTest.java`

**Interfaces:**
- Consumes: everything above; `WindowContent.mountCornerLayer`, `onError`, `onInfo`, `showConfigurationDetails`,
  `isActiveAndOpen`, `id`; `ConfigurationController.onProblems`; `Contributions.addAction/addPanel/requestPanel`;
  `PanelEntry.setBadge`.
- Produces: package-private `NotificationsFeature(NotificationCenter, Contributions)` with `PANEL_ID =
  "jasper.notifications"`, `SHOW_HISTORY = "notifications.history"`, `DISMISS_CARDS = "notifications.dismiss"`,
  `GENERAL` (`jasper.general`, "Jasper", CARD), `CONFIG` (`jasper.config`, "Configuration", STICKY), `attach(WindowContent)`,
  `detach(WindowContent)`, `activated(WindowContent)`, `error(String title, String details)`,
  `info(String title, String details)`, `CardLayer layer(UUID windowId)` (tests), `close()`; package-private
  `ConfigProblemNotices(NotificationCenter, Runnable showProblems, Runnable openFile)` with
  `accept(ConfigService.State)`.

- [ ] **Step 1: Write the failing tests**

`NotificationsFeatureTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.notificationui.CardLayer;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.WindowContent;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationsFeatureTest {
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
    private final Contributions contributions = new Contributions();

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private PanelEntry panel() {
        return contributions.panels().stream().filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow();
    }

    @Test void registersTheGroupsThePanelAndItsActions() {
        new NotificationsFeature(center, contributions);
        assertThat(center.groups()).extracting(NotificationGroup::id).containsExactly("jasper.general", "jasper.config");
        assertThat(center.mode("jasper.config")).isEqualTo(DisplayMode.STICKY);
        assertThat(panel().defaultRegion()).isEqualTo(PanelRegion.RIGHT);
        assertThat(panel().toggleActionId()).isEqualTo(NotificationsFeature.SHOW_HISTORY);
        assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Show History"));
        assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS))
            .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Dismiss Cards"));
        var requests = new ArrayList<Contributions.PanelRequest>();
        contributions.onPanelRequest(requests::add);
        UUID window = UUID.randomUUID();
        contributions.action(NotificationsFeature.SHOW_HISTORY).orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(requests).containsExactly(new Contributions.PanelRequest(window, NotificationsFeature.PANEL_ID, Contributions.PanelRequest.Op.TOGGLE));
    }

    @Test void theBadgeCountsUnreadAndTurnsRedForErrors() {
        var feature = new NotificationsFeature(center, contributions);
        assertThat(panel().badge()).isEmpty();
        feature.info("Migrate Config", "Nothing to migrate");
        assertThat(panel().badge()).contains(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification"));
        feature.error("Could not open config.toml", "denied");
        assertThat(panel().badge()).contains(new PanelBadge(2, PanelBadge.Tone.ERROR, "2 unread notifications, 1 error"));
        center.markAllRead();
        assertThat(panel().badge()).isEmpty();
    }

    @Test void cardsGoToTheirTargetOrTheWindowUsedLast() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(second);
        feature.info("Last used", "");
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(feature.layer(first.id()).showing()).isEmpty();
        center.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "Targeted").withTarget(first.id()));
        assertThat(feature.layer(first.id()).showing()).hasSize(1);
    }

    @Test void aClosedWindowsCardsGoAndLaterOnesFindAnotherWindow() {
        var feature = new NotificationsFeature(center, contributions);
        var first = window();
        var second = window();
        feature.attach(first);
        feature.attach(second);
        feature.activated(first);
        feature.error("Before", "");
        CardLayer closing = feature.layer(first.id());
        assertThat(closing.showing()).hasSize(1);
        feature.detach(first);
        assertThat(feature.layer(first.id())).isNull();
        assertThat(closing.showing()).isEmpty();
        feature.error("After", "");
        assertThat(feature.layer(second.id()).showing()).hasSize(1);
        assertThat(center.history()).hasSize(2);
    }

    @Test void notificationsFromBeforeTheFirstWindowShowWhenItIsFocused() {
        presence = Delivery.Presence.NO_WINDOW;
        var feature = new NotificationsFeature(center, contributions);
        feature.error("Startup problem", "details");
        center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), Severity.WARNING, "config.toml has warnings"));
        presence = Delivery.Presence.FOCUSED;
        var window = window();
        feature.attach(window);
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        feature.activated(window);
        assertThat(feature.layer(window.id()).showing()).hasSize(2);
    }

    @Test void dismissCardsClosesTheInvokingWindowsCardsAndReadsThem() {
        var feature = new NotificationsFeature(center, contributions);
        var window = window();
        feature.attach(window);
        feature.info("One", "");
        feature.info("Two", "");
        contributions.action(NotificationsFeature.DISMISS_CARDS).orElseThrow()
            .invoke(new Contributions.Invocation(window.id(), Optional.empty()));
        assertThat(feature.layer(window.id()).showing()).isEmpty();
        assertThat(center.unread()).isZero();
    }

    @Test void errorsWithoutDetailsStillPost() {
        var feature = new NotificationsFeature(center, contributions);
        feature.error("Could not open config.toml", null);
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Could not open config.toml");
            assertThat(entry.request().content()).isEmpty();
            assertThat(entry.request().severity()).isEqualTo(Severity.ERROR);
        });
    }
}
```

`ConfigProblemNoticesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ConfigProblemNoticesTest {
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final Path file = Path.of("config.toml").toAbsolutePath();
    private final List<String> ran = new ArrayList<>();

    private ConfigProblemNotices notices() {
        center.register(NotificationsFeature.CONFIG);
        return new ConfigProblemNotices(center, () -> ran.add("show"), () -> ran.add("open"));
    }

    private ConfigService.State state(ConfigDiagnostic... problems) {
        return new ConfigService.State(ConfigSnapshot.defaults(), List.of(problems), file, true);
    }

    private ConfigDiagnostic warning(int line, String key, String message) {
        return new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file, line, line == 0 ? 0 : 1, key, message);
    }

    @Test void aCleanConfigurationPostsNothing() {
        notices().accept(state());
        assertThat(center.history()).isEmpty();
    }

    @Test void aNewProblemPostsOneStickyNotificationWithItsActions() {
        var notices = notices();
        notices.accept(state(warning(3, "font.size", "Expected a number")));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.config");
            assertThat(entry.request().severity()).isEqualTo(Severity.WARNING);
            assertThat(entry.request().title()).isEqualTo("config.toml has warnings");
            assertThat(entry.request().content()).isEqualTo("config.toml:3: [font.size] Expected a number");
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Show Problems", "Open config.toml");
        });
        UUID id = center.history().getFirst().id();
        center.runAction(id, 0);
        assertThat(ran).containsExactly("show");
        assertThat(center.find(id).orElseThrow().expired()).as("the problems are still there").isFalse();
        notices.accept(state(warning(3, "font.size", "Expected a number")));
        assertThat(center.history()).as("the same problems post nothing").hasSize(1);
    }

    @Test void aNewerProblemReplacesTheNoticeAndAFixedFileExpiresIt() {
        var notices = notices();
        var first = warning(3, "font.size", "Expected a number");
        notices.accept(state(first));
        UUID earlier = center.history().getFirst().id();
        notices.accept(state(first, new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, file, 7, 1, "", "Unclosed table")));
        assertThat(center.find(earlier).orElseThrow().expired()).isTrue();
        Notification current = center.history().getFirst();
        assertThat(current.request().severity()).isEqualTo(Severity.ERROR);
        assertThat(current.request().title()).isEqualTo("config.toml has errors");
        assertThat(current.request().content()).isEqualTo("config.toml:3: [font.size] Expected a number\nand 1 more");
        notices.accept(state());
        assertThat(center.find(current.id()).orElseThrow().expired()).isTrue();
    }

    @Test void problemsInOtherFilesNameTheConfigurationInstead() {
        var notices = notices();
        notices.accept(state(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file.resolveSibling("themes/broken.toml"), 2, 1, "", "Unknown key")));
        assertThat(center.history().getFirst().request().title()).isEqualTo("The configuration has warnings");
        assertThat(center.history().getFirst().request().content()).isEqualTo("broken.toml:2: Unknown key");
    }
}
```

`JasperApplicationNotificationsTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.PanelEntry;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationNotificationsTest {
    @Test void theApplicationOffersTheNotificationsPanelAndActions() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            var contributions = application[0].contributions();
            assertThat(contributions.panels()).extracting(PanelEntry::id).contains(NotificationsFeature.PANEL_ID);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).isPresent();
            assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS)).isPresent();
            application[0].quitNow();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.NotificationsFeatureTest' --tests 'dev.jasper.app.application.ConfigProblemNoticesTest' --tests 'dev.jasper.app.application.JasperApplicationNotificationsTest'`
Expected: FAIL — `NotificationsFeature` and `ConfigProblemNotices` do not exist.

- [ ] **Step 3: Write the implementation**

`NotificationsFeature.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notificationui.CardLayer;
import dev.jasper.app.notificationui.HistoryPanel;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.workspace.WindowContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The application's notification surfaces: Jasper's own groups, the built-in Notifications panel with its unread
 * badge and actions, and one card layer per terminal window, routing each requested card to its target window or the
 * window used last. EDT only; the application closes it at shutdown.
 */
final class NotificationsFeature implements AutoCloseable {
    static final String PANEL_ID = "jasper.notifications";
    static final String SHOW_HISTORY = "notifications.history";
    static final String DISMISS_CARDS = "notifications.dismiss";
    static final NotificationGroup GENERAL = new NotificationGroup("jasper.general", "Jasper", NotificationRequest.JASPER, DisplayMode.CARD);
    static final NotificationGroup CONFIG = new NotificationGroup("jasper.config", "Configuration", NotificationRequest.JASPER, DisplayMode.STICKY);

    private record Attached(WindowContent content, CardLayer layer) {}

    private final NotificationCenter center;
    private final Contributions contributions;
    private final PanelEntry panel;
    private final Map<UUID, Attached> windows = new LinkedHashMap<>();
    private final Subscription listening;
    private UUID lastActive;
    private boolean closed;

    NotificationsFeature(NotificationCenter center, Contributions contributions) {
        this.center = center;
        this.contributions = contributions;
        center.register(GENERAL);
        center.register(CONFIG);
        var icon = AppIcons.bundled("toolwindows/notifications");
        contributions.addAction(SHOW_HISTORY, "Notifications: Show History", icon,
            List.of("notifications", "history", "messages", "errors", "panel"), Optional.empty(),
            invocation -> contributions.requestPanel(new Contributions.PanelRequest(invocation.windowId(), PANEL_ID,
                Contributions.PanelRequest.Op.TOGGLE)));
        contributions.addAction(DISMISS_CARDS, "Notifications: Dismiss Cards", null,
            List.of("notifications", "close", "clear", "balloons", "cards"), Optional.empty(), invocation -> {
                Attached attached = windows.get(invocation.windowId());
                if (attached != null) attached.layer().dismissAll();
            });
        panel = contributions.addPanel(PANEL_ID, "Notifications", icon, PanelRegion.RIGHT, Optional.of(SHOW_HISTORY),
            site -> new HistoryPanel(center, site, focused(site.windowId())));
        listening = center.onChange(this::changed);
        updateBadge();
    }

    /** Gives the window its card layer. */
    void attach(WindowContent content) {
        if (closed || windows.containsKey(content.id())) return;
        UUID id = content.id();
        var layer = new CardLayer(center, content.isActiveAndOpen(), () -> contributions.requestPanel(
            new Contributions.PanelRequest(id, PANEL_ID, Contributions.PanelRequest.Op.SHOW)));
        content.mountCornerLayer(layer);
        windows.put(id, new Attached(content, layer));
        if (lastActive == null) lastActive = id;
    }

    /** The window closed: its cards go; their entries stay in history. */
    void detach(WindowContent content) {
        Attached attached = windows.remove(content.id());
        if (attached == null) return;
        attached.layer().close();
        if (content.id().equals(lastActive)) lastActive = windows.isEmpty() ? null : windows.keySet().iterator().next();
    }

    /** A terminal window was activated: it is the default target, and cards that waited for focus show now. */
    void activated(WindowContent content) {
        if (closed || !windows.containsKey(content.id())) return;
        lastActive = content.id();
        center.focusReturned();
    }

    /** A window or application error: a title and details, which may be missing. */
    void error(String title, String details) { post(Severity.ERROR, title, details); }

    void info(String title, String details) { post(Severity.INFO, title, details); }

    CardLayer layer(UUID windowId) {
        Attached attached = windows.get(windowId);
        return attached == null ? null : attached.layer();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        for (Attached attached : windows.values()) attached.layer().close();
        windows.clear();
    }

    private void post(Severity severity, String title, String details) {
        center.submit(NotificationRequest.of(GENERAL.id(), severity, title).withContent(details == null ? "" : details));
    }

    private boolean focused(UUID windowId) {
        Attached attached = windows.get(windowId);
        return attached != null && attached.content().isActiveAndOpen();
    }

    private void changed(NotificationCenter.Change change) {
        if (change instanceof NotificationCenter.Change.CardRequested requested) route(requested.notification());
        updateBadge();
    }

    private void route(Notification notification) {
        Attached target = notification.request().target().map(windows::get).orElse(null);
        if (target == null && lastActive != null) target = windows.get(lastActive);
        if (target == null && !windows.isEmpty()) target = windows.values().iterator().next();
        if (target != null) target.layer().show(notification);
    }

    private void updateBadge() {
        int unread = center.unread();
        if (unread == 0) {
            panel.setBadge(Optional.empty());
            return;
        }
        int errors = center.unreadErrors();
        String description = unread + (unread == 1 ? " unread notification" : " unread notifications")
            + (errors == 0 ? "" : ", " + errors + (errors == 1 ? " error" : " errors"));
        panel.setBadge(Optional.of(new PanelBadge(unread, errors > 0 ? PanelBadge.Tone.ERROR : PanelBadge.Tone.ACCENT, description)));
    }
}
```

`ConfigProblemNotices.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration problems as one sticky notification (spec section 7, producer 2): posted when the problems gain one
 * not notified before, replacing the previous notice, and expired when every problem is gone. The status bar
 * indicator and its dialog remain the full list. EDT only.
 */
final class ConfigProblemNotices {
    private final NotificationCenter center;
    private final Runnable showProblems;
    private final Runnable openFile;
    private Set<String> notified = Set.of();
    private UUID current;

    ConfigProblemNotices(NotificationCenter center, Runnable showProblems, Runnable openFile) {
        this.center = center;
        this.showProblems = showProblems;
        this.openFile = openFile;
    }

    void accept(ConfigService.State state) {
        List<ConfigDiagnostic> problems = state.diagnostics();
        Set<String> now = new LinkedHashSet<>();
        for (ConfigDiagnostic problem : problems) now.add(problem.formatted());
        if (now.isEmpty()) {
            expireCurrent();
            notified = Set.of();
            return;
        }
        if (notified.containsAll(now)) {
            notified = now;
            return;
        }
        expireCurrent();
        boolean errors = problems.stream().anyMatch(problem -> problem.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean oneFile = problems.stream().allMatch(problem -> problem.file().equals(state.file()));
        String subject = oneFile ? state.file().getFileName().toString() : "The configuration";
        String content = describe(problems.getFirst()) + (problems.size() == 1 ? "" : "\nand " + (problems.size() - 1) + " more");
        current = center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), errors ? Severity.ERROR : Severity.WARNING,
                subject + (errors ? " has errors" : " has warnings"))
            .withContent(content)
            .withAction(new NotificationAction("Show Problems", showProblems, false))
            .withAction(new NotificationAction("Open " + state.file().getFileName(), openFile, false))).orElse(null);
        notified = now;
    }

    private void expireCurrent() {
        if (current == null) return;
        center.expire(current);
        current = null;
    }

    static String describe(ConfigDiagnostic problem) {
        return problem.file().getFileName() + (problem.line() == 0 ? "" : ":" + problem.line()) + ": "
            + (problem.key().isEmpty() ? "" : "[" + problem.key() + "] ") + problem.message();
    }
}
```

`JasperApplication.java`:
- Add imports `dev.jasper.app.notifications.Delivery`, `dev.jasper.app.notifications.NotificationCenter`,
  `dev.jasper.app.notifications.NotificationRequest`, `dev.jasper.app.notifications.Severity`.
- Add fields after `private ActivityNotifier activityNotifier;`:

```java
    private final NotificationCenter notificationCenter;
    private final NotificationsFeature notificationViews;
```

- In the main constructor, directly after `themes = created;` (the constructor runs on the EDT, as `ThemeController`,
  `BuddyIntegration` and `ConfigurationController` already require):

```java
        notificationCenter = new NotificationCenter(SwingUtilities::invokeLater, java.time.Clock.systemDefaultZone(), this::presence);
        notificationViews = new NotificationsFeature(notificationCenter, contributions);
```

- Directly after the `if (configuration != null) configuration.onSnapshot(...)` block:

```java
        if (configuration != null) {
            var problems = new ConfigProblemNotices(notificationCenter, this::showConfigurationProblems, this::openConfigFile);
            configuration.onProblems(problems::accept);
        }
```

- In `openWindow`, directly after `window.content().connectContributions(contributions, uiState);`:

```java
        window.content().onError = notificationViews::error;
        window.content().onInfo = notificationViews::info;
        notificationViews.attach(window.content());
```

- In `windowClosed`, directly after `windows.remove(window);`, add `notificationViews.detach(window.content());`.
- In `windowActivated`, directly after `lastActive = window;`, add `notificationViews.activated(window.content());`.
- In `migrateConfiguration`, replace the two branch lines with:

```java
                if (failure == null) notificationViews.info("Migrate Config", message);
                else notificationViews.error("Migrate Config failed",
                    (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());
```

- In `openConfigFile`, replace `notice(failure.getMessage())` with
  `notificationViews.error("Could not open config.toml", failure.getMessage())`.
- In `revealConfigFile`, replace `notice("Could not reveal " + folder)` with
  `notificationViews.error("Could not reveal the folder", folder.toString())`.
- Delete the method `info(String message)` and its javadoc. Replace `notice(String message)` and its javadoc with:

```java
        /** A plugin's error notice, until plugins post notifications themselves: one message, split into title and details. */
        void notice(String message) {
            notificationCenter.submit(NotificationRequest.message(NotificationsFeature.GENERAL.id(), Severity.ERROR, message));
        }

    /** Where Jasper stands for notifications: focused, in the background, or with no window open. */
    private Delivery.Presence presence() {
        if (windows.isEmpty()) return Delivery.Presence.NO_WINDOW;
        return java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null
            ? Delivery.Presence.FOCUSED : Delivery.Presence.BACKGROUND;
    }

    /** Show Problems on the configuration notification: the details over the window used last. */
    private void showConfigurationProblems() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive : windows.stream().findFirst().orElse(null);
        if (target == null) return;
        target.toFront();
        target.content().showConfigurationDetails();
    }
```

- In `shutdown()`, add `notificationViews.close();` directly before `if (configuration != null) configuration.close();`.

`application/package-info.java`: add `dev.jasper.app.notificationui` to the "Allowed outgoing Jasper dependencies" list,
directly after `dev.jasper.app.notifications`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew verifyApplicationArchitecture :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*'`
Expected: PASS, and the architecture check passes.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/application
git commit -m "feat(notifications): wire the center into the application" -m "Window errors and info, plugin notices and configuration problems become notifications with cards, the Notifications panel and its badge." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: Documentation and full verification

**Files:**
- Create: `docs/notifications.md`
- Modify: `docs/README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`
- Modify: `jasper-app/src/test/java/dev/jasper/app/documentation/AppDocumentationTest.java`
- Modify: this plan's status banner

- [ ] **Step 1: Add the guide to the checked guides (failing first)**

In `AppDocumentationTest.java`, add `"docs/notifications.md"` to `GUIDES` after `"docs/configuration.md"`.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest'`
Expected: FAIL — `docs/notifications.md` does not exist.

- [ ] **Step 2: Write `docs/notifications.md`**

```markdown
# Notifications

Jasper tells you about errors and results with notifications instead of dialogs that stop your work. Nothing is
saved: notifications start fresh every time Jasper starts, and you can clear them at any time.

## Cards

A notification appears as a card in the bottom-right corner of the window you used last:

- an icon for its severity (information, warning or error), or one its sender chose;
- a bold title, followed by `×N` when the same notification arrived several times within a minute;
- up to five lines of details (the full text is in the Notifications panel);
- links for what you can do next; more than three are under **More ▾**.

Clicking a card's body goes where it points, when it points somewhere, and closes the card. Close it with `✕`. At most three cards show at
once; the newest is at the bottom. Cards never take the keyboard from your terminal, and clicks beside them reach
the terminal as usual. Nothing animates.

Most cards hide after ten seconds of you being in that window; the countdown pauses while the pointer is over the
card and while the window is in the background. A hidden card stays unread in the panel. Sticky cards, such as
configuration problems, stay until you close them. When three sticky cards are showing, more wait behind a
**+N more** link that opens the panel.

When Jasper is in the background, notifications collect in the panel; sticky ones show their card when you come
back. Notifications that arrive before the first window opens show when it does.

## The Notifications panel

The Notifications button on the rail opens the panel, which starts on the right; right-click the button to move it.
**Notifications: Show History** in the command palette does the same. The button shows a dot while anything is
unread: red when an error is unread. Its tooltip gives the count.

Unread notifications are listed under **New**, in bold. Opening the panel in a focused window marks everything read;
what it read stays under **New** until you close the panel, and everything else is under **Earlier**, newest first. Each row shows the full details, the notification's
group and time, and its links (disabled once the notification no longer applies). `✕` removes one row; **Clear All**
removes them all.

## Groups

| Group | What it covers | Shows as |
|---|---|---|
| Jasper | errors and results of Jasper's own commands: a shell that could not start, a theme that could not be applied, Migrate Config | card |
| Configuration | problems in `config.toml`, themes and plugin settings files | sticky card |

A configuration notification offers **Show Problems** (the same list as the status bar's configuration indicator)
and **Open config.toml**. It goes away by itself once every problem is fixed.

## Shortcuts

**Notifications: Show History** and **Notifications: Dismiss Cards** (closes every card in the window) have no
default shortcut; assign one in Settings › Keymap.
```

- [ ] **Step 3: Update the other documents**

`docs/README.md`: under "User and operational guides", after the Configuration line, add:

```markdown
- [Notifications](notifications.md): cards, the Notifications panel and its badge, groups.
```

`docs/app-architecture.md`: replace the `notifications` row with

```
| `notifications` | EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its values, plus terminal and activity notice production for Buddy. Application owns the center and closes notifiers before companion. |
```

and add after it

```
| `notificationui` | EDT Swing views of the center: each window's CardLayer, the history panel and their text, style and focus helpers. Application mounts each layer through WindowContent and closes it with its window. |
```

`docs/app-maintenance.md`: add this section before `## Platform integration`:

```markdown
## Posting a notification

Application code never opens a message dialog to tell the user something; it posts a notification
([guide](notifications.md), [design](superpowers/specs/2026-10-02-jasper-notifications-design.md)).

- Window code calls `WindowContent.onError` / `onInfo` with a fixed title and the details; the application posts them
  to `jasper.general`. Never put user data in the title: it is what a window logs when nothing receives the message.
- Application code calls `NotificationsFeature.error` / `info`, or `NotificationCenter.post` with a
  `NotificationRequest` for another group, actions or a default action. Declare a group with
  `NotificationCenter.register` before posting to it; posts to an undeclared group are dropped and logged.
- Text is plain and bounded: titles are one line (cut at 200 characters), details at most 4,000. Pass exception
  messages as details, never as titles.
- Contextual feedback (a Settings field error, a palette error row, a plugin's inline row) stays where it is, and
  confirmation or input prompts stay dialogs: notifications are for things that happen elsewhere or later.
- Cards, the panel and the badge read `Jasper.notification.*` and `Jasper.railBadge*` colours derived in `ChromeKeys`;
  a theme's own `Notification.*` keys win.
```

`docs/STATUS.md`: add as the first bullet under "Current state" (fill in the counts from Step 5):

```markdown
- **Notifications plan 1: core** (branch `claude/notifications`, spec
  `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, plan
  `docs/superpowers/plans/2026-10-02-notifications-1-core.md`): an EDT `NotificationCenter` (in-memory history of 500,
  60 s coalescing, read/expired state, waiting cards), per-window card stacks in the content corner, the built-in
  Notifications rail panel with an unread badge, and the `jasper.general` / `jasper.config` producers. Window errors
  and info, plugin `Notices` and configuration problems no longer open dialogs. Recorded interpretations are in the
  plan's "Spec interpretations" section. `./gradlew check`: **N tests, 0 failures/errors, N skips**. GUI checks for
  the user: cards in light and dark themes, several windows, the badge, the panel, startup config problems.
```

- [ ] **Step 4: Run the documentation test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS.

- [ ] **Step 5: Full verification**

Run: `./gradlew check 2>&1 | tee build/notifications-1-check.log`
Expected: BUILD SUCCESSFUL, all architecture gates included.

Count the results:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
totals = [0, 0, 0, 0]
for f in pathlib.Path('.').glob('**/build/test-results/test/*.xml'):
    r = ET.parse(f).getroot()
    for i, k in enumerate(('tests', 'failures', 'errors', 'skipped')): totals[i] += int(r.get(k, 0))
print('tests %d failures %d errors %d skipped %d' % tuple(totals))
PY
```

Run the AGENTS.md source-hygiene script over the changed Java files:

```bash
python3 - $(git diff --name-only main -- '*.java') <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output. Then `git diff --check main` — expected: no output.

Put the counts into the STATUS entry, and change this plan's status banner to "Implemented on branch
`claude/notifications`; `./gradlew check` green (counts); awaiting review and the user's GUI checks."

- [ ] **Step 6: Commit**

```bash
git add docs jasper-app/src/test/java/dev/jasper/app/documentation/AppDocumentationTest.java
git commit -m "docs(notifications): document cards, the panel and posting" -m "User guide, architecture and maintenance notes, status entry and plan banner for notifications plan 1." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
