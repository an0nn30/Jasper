# Buddy bubble deck Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the buddy's transient completion bubble with a standing, source-keyed notification drawer of pill cards that appear while a command runs and stay until dismissed.

**Architecture:** A pure model (`BuddyDeck` of `BuddyNotice`) that knows nothing about panes, keyed by `(source, key)` so a future sftp transfer or SSH session posts to it unchanged. Pure geometry (`BuddyDeckLayout`) and pure motion (`BubbleMotion`) sit beside it, so everything except pixel-pushing is testable headlessly. `BuddyDeckPanel` paints and hit-tests; `BuddyDeckWindow` is the Swing shell. `CommandNotifier` becomes the terminal's producer: it schedules a card at the threshold, replaces it on finish, and sends a system notification when the pane was not focused.

**Tech Stack:** Java 25 on the JetBrains Runtime, Swing/AWT (`JWindow`, `javax.swing.Timer`), FlatLaf, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-17-jasper-buddy-bubble-deck-design.md`

## Global Constraints

- Java 25 on the **JetBrains Runtime**. Use the wrapper: `./gradlew`, never a system `gradle`.
- `jasper-terminal` (package `dev.jasper.terminal`) never depends on `jasper-app` (`dev.jasper.app`). **No public method in `jasper-terminal` takes or returns a JediTerm type.**
- **No interface without two real implementations.** This plan *deletes* `CommandNotifier.Channel` rather than inventing a second implementation; every new seam is a standard functional type (`Supplier`, `Consumer`, `BiConsumer`, `BiFunction`), not a new interface.
- Threading: JediTerm runs on the session's reader thread, the view on the EDT. Every read of buffer state takes `TerminalTextBuffer`'s lock; **keep work under the lock small and bounded** — listener callbacks fire outside it.
- Never put raw control, private-use or unpaired surrogate characters in source. `·` (U+00B7), `↵` (U+21B5), `…` (U+2026) and `×` (U+00D7) are ordinary printable characters and are written literally, as the existing sources already do.
- Tests are headless. Do **not** launch the GUI (`./gradlew :jasper-app:run`) — GUI checks are handed to the user.
- Do not commit on `main`. This plan continues on `claude/command-notifications`. End every commit message with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- macOS `grep` has no `-P`; use Python for character checks.

## Deviations from the spec, decided while planning

Record these in the plan's status banner if they change during execution.

1. **`BuddyNotice.detail` is a `Supplier<String>`, not a `String`.** The spec's card table needs a running card to tick (`Running · 1m 12s`). A fixed string cannot tick; a `long startedAt` field would reintroduce the **zero-sentinel bug this project has already hit twice** (an injected test clock legitimately reads 0). A supplier has neither problem, and it is what lets a future sftp card read `3.2 MB of 40 MB` without the deck learning a second notion of progress.
2. **The spec's `long at` field is dropped.** Ordering is list position and nothing renders a timestamp, so it was an unused field.
3. **`BuddyDeck.orphan` takes the final detail text.** The spec requires an orphaned running card to stop ticking and read `Stopped after 1m 12s`. Freezing the detail and dropping the action is one in-place replacement, and it must *not* promote the notice to the top.
4. **A gap in the branch this plan must close:** `CommandNotifier.passedThreshold()` has **no production caller** — only tests. Nothing tells the app a command has *started*, so the typing animation currently never runs. Task 7 adds `TerminalSession.Listener.commandStarted`.

---

### Task 1: The system font and the pill

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyFonts.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyBubblePanel.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/BuddyFontsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyBubblePanelTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `BuddyFonts.system(int style, float size)` → `Font`; `BuddyBubblePanel.radiusFor(int height)` → `int`; `BuddyBubblePanel.MAX_RADIUS` → `int`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/BuddyFontsTest.java`:

```java
package dev.jasper.app;

import java.awt.Font;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BuddyFontsTest {
    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    @Test void theStyleAndSizeAskedForAreTheOnesReturned() {
        Font font = BuddyFonts.system(Font.BOLD, 15f);

        assertThat(font.isBold()).isTrue();
        assertThat(font.getSize2D()).isEqualTo(15f);
    }

    /**
     * Java answers with Dialog for a family it does not have, so a wrong name is silent. This is the
     * whole reason BuddyFonts checks the resolved family instead of trusting the name it asked for.
     */
    @Test void sfProLooksLikeTheRightNameAndIsNot() {
        assumeTrue(mac(), "the macOS font stack");

        assertThat(new Font("SF Pro", Font.PLAIN, 13).getFamily()).isEqualToIgnoringCase(Font.DIALOG);
        assertThat(new Font("SF Pro Text", Font.PLAIN, 13).getFamily()).isEqualToIgnoringCase(Font.DIALOG);
    }

    @Test void theSystemFontResolvesOnMacAndIsNeverTheDialogFallback() {
        assumeTrue(mac(), "the macOS font stack");

        assertThat(new Font(BuddyFonts.MAC_SYSTEM_FONT, Font.PLAIN, 13).getFamily())
            .isNotEqualToIgnoringCase(Font.DIALOG);
        assertThat(BuddyFonts.system(Font.PLAIN, 13f).getFamily()).isNotEqualToIgnoringCase(Font.DIALOG);
    }
}
```

Append to `jasper-app/src/test/java/dev/jasper/app/BuddyBubblePanelTest.java` (inside the class):

```java
    @Test void aSingleLineBubbleIsAFullPill() {
        assertThat(BuddyBubblePanel.radiusFor(28)).isEqualTo(14);
        assertThat(BuddyBubblePanel.radiusFor(0)).isZero();
    }

    /** Half of a two-line card's height would be a lozenge, so the radius is capped. */
    @Test void aTallCardIsCappedRatherThanRoundedIntoALozenge() {
        assertThat(BuddyBubblePanel.radiusFor(80)).isEqualTo(BuddyBubblePanel.MAX_RADIUS);
        assertThat(BuddyBubblePanel.MAX_RADIUS).isLessThan(40);
    }

    @Test void theBubbleUsesTheSystemFontRatherThanTheLookAndFeelsLabelFont() {
        assertThat(BuddyBubblePanel.detailFont().getFamily())
            .isEqualTo(BuddyFonts.system(java.awt.Font.PLAIN, 14f).getFamily());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyFontsTest' --tests 'dev.jasper.app.BuddyBubblePanelTest'`

Expected: FAIL — `BuddyFonts` does not exist, `radiusFor` and `MAX_RADIUS` are not defined.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyFonts.java`:

```java
package dev.jasper.app;

import java.awt.Font;
import java.util.Locale;
import javax.swing.UIManager;

/**
 * The OS system font for the buddy's surfaces. Asking the look and feel is not the same as asking the
 * system: FlatLaf sets {@code Label.font} to Helvetica Neue, which is a real face but the pre-2015
 * macOS system font, so a bubble that trusts it looks a decade old rather than obviously broken.
 */
final class BuddyFonts {
    /**
     * The only name under which macOS exposes its system font to Java. "SF Pro" and "SF Pro Text"
     * resolve to Dialog instead — silently, which is how a wrong choice goes unnoticed.
     */
    static final String MAC_SYSTEM_FONT = ".AppleSystemUIFont";

    private BuddyFonts() { }

    /** The system font, falling back to the look and feel's label font and then to a generic sans. */
    static Font system(int style, float size) {
        Font system = macSystemFont();
        if (system != null) return system.deriveFont(style, size);
        Font label = UIManager.getFont("Label.font");
        return label != null ? label.deriveFont(style, size) : new Font(Font.SANS_SERIF, style, (int) size);
    }

    /**
     * Null unless the request actually resolved. Java never fails a font lookup — it answers with
     * Dialog — so the resolved family is the only honest evidence that the name exists.
     */
    private static Font macSystemFont() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) return null;
        Font candidate = new Font(MAC_SYSTEM_FONT, Font.PLAIN, 13);
        return candidate.getFamily().equalsIgnoreCase(Font.DIALOG) ? null : candidate;
    }
}
```

In `BuddyBubblePanel.java`, add the cap constant beside the other layout constants:

```java
    static final int MAX_WIDTH = 320;
    /** A pill's radius is half its height; past this a two-line card would read as a lozenge. */
    static final int MAX_RADIUS = 22;
```

Replace the `font` helper so it delegates:

```java
    private static Font font(int style, float size) {
        return BuddyFonts.system(style, size);
    }
```

Add the pill radius and delete the fixed `MESSAGE_RADIUS`/`MENU_RADIUS` arithmetic in favour of it. Replace the `radius()` method with:

```java
    /** A pill: half the height, capped so a two-line card stays a rounded rectangle. */
    static int radiusFor(int height) {
        return Math.min(Math.max(0, height) / 2, MAX_RADIUS);
    }
```

In `paintComponent`, replace `int arc = radius() * 2;` with:

```java
            int arc = radiusFor(height) * 2;
```

Delete the now-unused `MESSAGE_RADIUS`, `MENU_RADIUS` constants and the private `radius()` method. Leave `MESSAGE_PAD_X`, `MENU_PAD_X` and the rest untouched.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyFontsTest' --tests 'dev.jasper.app.BuddyBubblePanelTest'`

Expected: PASS. If `BuddyBubblePanelTest` has existing assertions on `MESSAGE_RADIUS`/`MENU_RADIUS`, update them to `radiusFor`; do not reintroduce the constants.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyFonts.java jasper-app/src/main/java/dev/jasper/app/BuddyBubblePanel.java jasper-app/src/test/java/dev/jasper/app/BuddyFontsTest.java jasper-app/src/test/java/dev/jasper/app/BuddyBubblePanelTest.java
git commit -m "$(printf 'feat: give the buddy bubble the system font and a pill shape\n\nFlatLaf sets Label.font to Helvetica Neue, so the bubble was not falling\nback to a generic sans - it was using the pre-2015 macOS system font.\n.AppleSystemUIFont is the only name that resolves; "SF Pro" answers with\nDialog, silently, so BuddyFonts checks the resolved family rather than\ntrusting the name it asked for.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 2: The notice and the drawer

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyNotice.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyDeck.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `record BuddyNotice(String source, Object key, String title, BuddyNotice.State state, Supplier<String> detail, Runnable activate)`, `enum State { ACTIVE, DONE, FAILED }`, methods `boolean orphaned()`, `boolean sameAs(String, Object)`.
  - `BuddyDeck` with `void post(BuddyNotice)`, `boolean dismiss(String, Object)`, `void clear()`, `void orphan(String source, Object key, String finalDetail)`, `List<BuddyNotice> notices()` (newest first), `boolean isEmpty()`, `int size()`, `static final int MAX_NOTICES = 50`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyDeckTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyDeckTest {
    private final BuddyDeck deck = new BuddyDeck();

    private static BuddyNotice notice(String source, Object key, String title) {
        return new BuddyNotice(source, key, title, BuddyNotice.State.DONE, () -> "done", () -> { });
    }

    @Test void noticesComeBackNewestFirst() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second", "first");
    }

    @Test void postingOverTheSameKeyReplacesInPlaceAndPromotesToTheTop() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));
        deck.post(notice("terminal", "a", "first again"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("first again", "second");
    }

    /** The whole point of the source being part of the identity: producers cannot collide. */
    @Test void twoProducersWithTheSameKeyAreTwoNotices() {
        deck.post(notice("terminal", "1", "a build"));
        deck.post(notice("sftp", "1", "a transfer"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a transfer", "a build");
    }

    @Test void dismissingRemovesOneAndSaysWhetherThereWasOne() {
        deck.post(notice("terminal", "a", "first"));

        assertThat(deck.dismiss("terminal", "a")).isTrue();
        assertThat(deck.dismiss("terminal", "a")).isFalse();
        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void clearingEmptiesTheDrawer() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));

        deck.clear();

        assertThat(deck.notices()).isEmpty();
    }

    /**
     * The regression this design is shaped to avoid: a pane closed after a successful build must
     * still say the build succeeded. Orphaning is a lost action, not a lost outcome.
     */
    @Test void orphaningKeepsTheOutcomeAndOnlyTakesAwayTheAction() {
        deck.post(new BuddyNotice("terminal", "a", "./gradlew build", BuddyNotice.State.DONE,
            () -> "Finished in 1m 12s", () -> { }));

        deck.orphan("terminal", "a", "Finished in 1m 12s");

        BuddyNotice orphan = deck.notices().getFirst();
        assertThat(orphan.state()).isEqualTo(BuddyNotice.State.DONE);
        assertThat(orphan.title()).isEqualTo("./gradlew build");
        assertThat(orphan.detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(orphan.orphaned()).isTrue();
        assertThat(orphan.activate()).isNull();
    }

    @Test void orphaningFreezesARunningNoticeSoItStopsTicking() {
        deck.post(new BuddyNotice("terminal", "a", "sleep 600", BuddyNotice.State.ACTIVE,
            () -> "Running · " + System.nanoTime(), () -> { }));

        deck.orphan("terminal", "a", "Stopped after 1m 12s");

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Stopped after 1m 12s");
    }

    @Test void orphaningKeepsThePlaceRatherThanPromotingToTheTop() {
        deck.post(notice("terminal", "a", "older"));
        deck.post(notice("terminal", "b", "newer"));

        deck.orphan("terminal", "a", "gone");

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("newer", "older");
    }

    @Test void orphaningSomethingThatIsNotThereDoesNothing() {
        deck.post(notice("terminal", "a", "only"));

        deck.orphan("terminal", "missing", "gone");

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("only");
    }

    @Test void theOldestFallOffOnceTheDrawerIsFull() {
        for (int i = 0; i < BuddyDeck.MAX_NOTICES + 5; i++) deck.post(notice("terminal", i, "n" + i));

        assertThat(deck.size()).isEqualTo(BuddyDeck.MAX_NOTICES);
        assertThat(deck.notices().getFirst().title()).isEqualTo("n" + (BuddyDeck.MAX_NOTICES + 4));
        assertThat(deck.notices().getLast().title()).isEqualTo("n5");
    }

    @Test void aNoticeNeedsAnIdentityAndSomethingToSay() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new BuddyNotice("terminal", "a", "  ", BuddyNotice.State.DONE, () -> "d", () -> { }));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckTest'`

Expected: FAIL — `BuddyNotice` and `BuddyDeck` do not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyNotice.java`:

```java
package dev.jasper.app;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One thing worth remembering. {@code key} is whatever produced it — a terminal pane today, an sftp
 * transfer or an SSH session later — and a later notice with the same source and key replaces this
 * one rather than stacking on top of it. Two producers cannot collide, because the source is part of
 * the identity.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck
 * being re-posted every second, and so a future transfer can report bytes through the same field. A
 * start timestamp would have served the first purpose and not the second, and would have needed a
 * zero sentinel — the mistake this repository has already made twice.
 *
 * <p>Identity is {@link #sameAs}, never {@code equals}: the record holds two lambdas, so its
 * generated {@code equals} compares them by reference and means nothing useful.
 */
record BuddyNotice(String source, Object key, String title, State state,
                   Supplier<String> detail, Runnable activate) {
    /** Deliberately not "running/succeeded/failed": a transfer is in progress, not running. */
    enum State { ACTIVE, DONE, FAILED }

    BuddyNotice {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
    }

    /**
     * Its origin is gone — the pane closed. Still worth reading, so this is a missing action rather
     * than a fourth state: a pane closed after a successful build must still say it succeeded.
     */
    boolean orphaned() { return activate == null; }

    boolean sameAs(String otherSource, Object otherKey) {
        return source.equals(otherSource) && key.equals(otherKey);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/BuddyDeck.java`:

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The notification drawer: notices newest first, nothing auto-hides, cleared only by the user or by
 * quitting. It knows nothing about panes, tabs or commands — a producer supplies the identity — so a
 * later sftp transfer or SSH session posts here without the deck changing at all.
 *
 * <p>EDT only, like the rest of the buddy's state.
 */
final class BuddyDeck {
    /** Bounded so a long session cannot grow it without limit; the oldest fall off silently. */
    static final int MAX_NOTICES = 50;

    private final List<BuddyNotice> notices = new ArrayList<>();

    /** Adds a notice, or replaces the one with the same source and key and promotes it to the top. */
    void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        notices.removeIf(existing -> existing.sameAs(notice.source(), notice.key()));
        notices.addFirst(notice);
        while (notices.size() > MAX_NOTICES) notices.removeLast();
    }

    /** Removes one notice; true when there was one to remove. */
    boolean dismiss(String source, Object key) {
        return notices.removeIf(notice -> notice.sameAs(source, key));
    }

    void clear() { notices.clear(); }

    /**
     * Its origin went away. The notice keeps its place and its outcome, stops updating — a running
     * card would otherwise tick forever against a process that no longer exists — and stops
     * responding, because there is nowhere left to go.
     */
    void orphan(String source, Object key, String finalDetail) {
        Objects.requireNonNull(finalDetail, "finalDetail");
        notices.replaceAll(notice -> notice.sameAs(source, key)
            ? new BuddyNotice(notice.source(), notice.key(), notice.title(), notice.state(),
                () -> finalDetail, null)
            : notice);
    }

    /** Newest first. A copy: the caller may be painting while a producer posts. */
    List<BuddyNotice> notices() { return List.copyOf(notices); }

    boolean isEmpty() { return notices.isEmpty(); }

    int size() { return notices.size(); }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckTest'`

Expected: PASS (12 tests).

- [ ] **Step 5: Prove the orphan test has teeth**

Temporarily change `BuddyDeck.orphan` to also call `notices.addFirst` on the replacement, and re-run. `orphaningKeepsThePlaceRatherThanPromotingToTheTop` must fail. Revert the change.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyNotice.java jasper-app/src/main/java/dev/jasper/app/BuddyDeck.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckTest.java
git commit -m "$(printf 'feat: add the source-keyed notification drawer\n\nThe deck never mentions a pane. A notice is identified by its source and\nan opaque key, so the terminal choosing the pane as its key is what makes\n"one card per tab/pane/window" true today, and a future sftp transfer\nposts per-transfer notices without the deck changing.\n\nOrphaning drops the action and freezes the detail, keeping the state: a\npane closed after a successful build must still say it succeeded.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 3: Deck geometry

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `BuddyDeckLayout` with constants `PEEK_Y=4`, `PEEK_INSET=6`, `MAX_PEEKED=3`, `ROW_GAP=6`, `DISMISS_SIZE=14`, `DISMISS_INSET=6`, `CLEAR_ROW_HEIGHT=26`; and statics
  `Rectangle collapsed(int index, int width, int cardHeight)`,
  `int collapsedHeight(int count, int cardHeight)`,
  `Rectangle expanded(int index, int width, int cardHeight, int scroll)`,
  `int expandedHeight(int count, int cardHeight)`,
  `Rectangle clearRow(int count, int width, int cardHeight, int scroll)`,
  `int clampScroll(int scroll, int contentHeight, int viewportHeight)`,
  `int cardAt(int y, int count, int cardHeight, int scroll)`,
  `Rectangle dismissTarget(Rectangle card)`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java`:

```java
package dev.jasper.app;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyDeckLayoutTest {
    private static final int WIDTH = 300;
    private static final int CARD = 58;
    private static final int PITCH = CARD + BuddyDeckLayout.ROW_GAP;

    @Test void theNewestCardIsDrawnInFullAndTheOnesBehindItPeek() {
        assertThat(BuddyDeckLayout.collapsed(0, WIDTH, CARD)).isEqualTo(new Rectangle(0, 0, WIDTH, CARD));
        assertThat(BuddyDeckLayout.collapsed(1, WIDTH, CARD)).isEqualTo(new Rectangle(6, 4, WIDTH - 12, CARD));
        assertThat(BuddyDeckLayout.collapsed(2, WIDTH, CARD)).isEqualTo(new Rectangle(12, 8, WIDTH - 24, CARD));
    }

    /** Past the third the deck draws a count instead, so a deeper card never insets further. */
    @Test void theStackStopsGettingDeeperAfterThreeCards() {
        assertThat(BuddyDeckLayout.collapsed(7, WIDTH, CARD)).isEqualTo(BuddyDeckLayout.collapsed(2, WIDTH, CARD));
    }

    @Test void theCollapsedHeightIsTheTopCardPlusWhatPeeksBelowIt() {
        assertThat(BuddyDeckLayout.collapsedHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.collapsedHeight(1, CARD)).isEqualTo(CARD);
        assertThat(BuddyDeckLayout.collapsedHeight(2, CARD)).isEqualTo(CARD + 4);
        assertThat(BuddyDeckLayout.collapsedHeight(9, CARD)).isEqualTo(CARD + 8);
    }

    @Test void theExpandedListRunsDownTheWindowAtAConstantPitch() {
        assertThat(BuddyDeckLayout.expanded(0, WIDTH, CARD, 0)).isEqualTo(new Rectangle(0, 0, WIDTH, CARD));
        assertThat(BuddyDeckLayout.expanded(2, WIDTH, CARD, 0)).isEqualTo(new Rectangle(0, 2 * PITCH, WIDTH, CARD));
    }

    @Test void scrollingMovesEveryRowUpByTheSameAmount() {
        assertThat(BuddyDeckLayout.expanded(2, WIDTH, CARD, 30).y).isEqualTo(2 * PITCH - 30);
    }

    @Test void oneCardNeedsNoClearAllRow() {
        assertThat(BuddyDeckLayout.expandedHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.expandedHeight(1, CARD)).isEqualTo(CARD);
        assertThat(BuddyDeckLayout.clearRow(1, WIDTH, CARD, 0)).isNull();
    }

    @Test void moreThanOneCardGetsAClearAllRowUnderTheLast() {
        assertThat(BuddyDeckLayout.expandedHeight(3, CARD))
            .isEqualTo(3 * PITCH - BuddyDeckLayout.ROW_GAP
                + BuddyDeckLayout.ROW_GAP + BuddyDeckLayout.CLEAR_ROW_HEIGHT);
        assertThat(BuddyDeckLayout.clearRow(3, WIDTH, CARD, 0))
            .isEqualTo(new Rectangle(0, 3 * PITCH, WIDTH, BuddyDeckLayout.CLEAR_ROW_HEIGHT));
    }

    @Test void theScrollOffsetCanNeitherRunPastTheLastCardNorAboveTheFirst() {
        assertThat(BuddyDeckLayout.clampScroll(-40, 900, 600)).isZero();
        assertThat(BuddyDeckLayout.clampScroll(5_000, 900, 600)).isEqualTo(300);
        assertThat(BuddyDeckLayout.clampScroll(120, 900, 600)).isEqualTo(120);
    }

    @Test void aListShorterThanItsViewportDoesNotScrollAtAll() {
        assertThat(BuddyDeckLayout.clampScroll(200, 300, 600)).isZero();
    }

    @Test void aPointInACardNamesThatCardAndAPointInTheGapNamesNone() {
        assertThat(BuddyDeckLayout.cardAt(5, 4, CARD, 0)).isZero();
        assertThat(BuddyDeckLayout.cardAt(CARD + 2, 4, CARD, 0)).isEqualTo(-1);
        assertThat(BuddyDeckLayout.cardAt(PITCH + 5, 4, CARD, 0)).isEqualTo(1);
    }

    @Test void aPointBelowTheLastCardOrAboveTheFirstNamesNone() {
        assertThat(BuddyDeckLayout.cardAt(4 * PITCH, 4, CARD, 0)).isEqualTo(-1);
        assertThat(BuddyDeckLayout.cardAt(-3, 4, CARD, 0)).isEqualTo(-1);
    }

    @Test void scrollingChangesWhichCardIsUnderThePointer() {
        assertThat(BuddyDeckLayout.cardAt(5, 4, CARD, PITCH)).isEqualTo(1);
    }

    @Test void theDismissTargetSitsInsideItsOwnCardsTopRightCorner() {
        Rectangle card = new Rectangle(10, 20, WIDTH, CARD);
        Rectangle target = BuddyDeckLayout.dismissTarget(card);

        assertThat(card.contains(target)).isTrue();
        assertThat(target.width).isEqualTo(BuddyDeckLayout.DISMISS_SIZE);
        assertThat(target.x + target.width).isEqualTo(card.x + card.width - BuddyDeckLayout.DISMISS_INSET);
        assertThat(target.y).isEqualTo(card.y + BuddyDeckLayout.DISMISS_INSET);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckLayoutTest'`

Expected: FAIL — `BuddyDeckLayout` does not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java`:

```java
package dev.jasper.app;

import java.awt.Rectangle;

/**
 * Where the deck's cards sit, collapsed and expanded. Pure geometry: no AWT devices are queried and
 * no Swing state is read, so every offset, the scroll clamp and the hit tests are unit-testable.
 *
 * <p>All cards are the same size. A stack of differently sized cards reads as a mess rather than a
 * deck, and uniform rows make the hit test arithmetic rather than a search.
 */
final class BuddyDeckLayout {
    static final int PEEK_Y = 4;
    static final int PEEK_INSET = 6;
    /** How many cards are drawn in the collapsed stack; beyond this the deck draws a count. */
    static final int MAX_PEEKED = 3;
    static final int ROW_GAP = 6;
    static final int DISMISS_SIZE = 14;
    static final int DISMISS_INSET = 6;
    static final int CLEAR_ROW_HEIGHT = 26;

    private BuddyDeckLayout() { }

    /** Card {@code index} counting from the newest, in the collapsed stack. */
    static Rectangle collapsed(int index, int width, int cardHeight) {
        int depth = Math.min(Math.max(index, 0), MAX_PEEKED - 1);
        return new Rectangle(PEEK_INSET * depth, PEEK_Y * depth, width - 2 * PEEK_INSET * depth, cardHeight);
    }

    /** The top card plus the sliver of each card peeking below it. */
    static int collapsedHeight(int count, int cardHeight) {
        if (count <= 0) return 0;
        return cardHeight + PEEK_Y * Math.min(count - 1, MAX_PEEKED - 1);
    }

    /** Row {@code index} in the expanded list, already shifted by the scroll offset. */
    static Rectangle expanded(int index, int width, int cardHeight, int scroll) {
        return new Rectangle(0, index * (cardHeight + ROW_GAP) - scroll, width, cardHeight);
    }

    /** Every row, the gaps between them, and the clear-all row when there is more than one card. */
    static int expandedHeight(int count, int cardHeight) {
        if (count <= 0) return 0;
        int rows = count * (cardHeight + ROW_GAP) - ROW_GAP;
        return count > 1 ? rows + ROW_GAP + CLEAR_ROW_HEIGHT : rows;
    }

    /** Null for a single card: clearing all of one card is what the card's own × already does. */
    static Rectangle clearRow(int count, int width, int cardHeight, int scroll) {
        if (count <= 1) return null;
        return new Rectangle(0, count * (cardHeight + ROW_GAP) - scroll, width, CLEAR_ROW_HEIGHT);
    }

    /** Clamped so the list can neither run past the last card nor above the first. */
    static int clampScroll(int scroll, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight)));
    }

    /** The card at {@code y} in the expanded list, or -1 in a gap, the clear-all row, or past the end. */
    static int cardAt(int y, int count, int cardHeight, int scroll) {
        int pitch = cardHeight + ROW_GAP;
        int local = y + scroll;
        if (local < 0) return -1;
        int index = local / pitch;
        if (index >= count) return -1;
        return local % pitch < cardHeight ? index : -1;
    }

    /** The dismiss target, always strictly inside its own card so it cannot hit the one behind. */
    static Rectangle dismissTarget(Rectangle card) {
        return new Rectangle(card.x + card.width - DISMISS_INSET - DISMISS_SIZE,
            card.y + DISMISS_INSET, DISMISS_SIZE, DISMISS_SIZE);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckLayoutTest'`

Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java
git commit -m "$(printf 'feat: lay out the deck collapsed, expanded and scrolled\n\nPure geometry, so the peek offsets, the scroll clamp at both ends and the\nhit tests are testable without a window. Fifty cards do not fit any\nscreen, so the expanded list scrolls rather than capping and silently\nhiding the rest - a drawer that shows twenty of fifty lies about what it\nholds.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 4: Motion

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BubbleMotion.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BubbleMotionTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `BubbleMotion` with `IN_NANOS`, `HOVER_NANOS`, `IN_FROM=0.6f`, `IN_PEAK=1.06f`, `HOVER_SCALE=1.04f`; statics `float easeOutBack(float t)`, `float inScale(long elapsedNanos)`, `float inOpacity(long elapsedNanos)`, `float hoverScale(long elapsedNanos, boolean entering)`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BubbleMotionTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BubbleMotionTest {
    @Test void theCurveStartsAtNothingAndEndsAtExactlyOne() {
        assertThat(BubbleMotion.easeOutBack(0f)).isEqualTo(0f);
        assertThat(BubbleMotion.easeOutBack(1f)).isEqualTo(1f);
    }

    @Test void theCardStartsSmallAndSettlesAtItsRealSize() {
        assertThat(BubbleMotion.inScale(0)).isEqualTo(BubbleMotion.IN_FROM);
        assertThat(BubbleMotion.inScale(BubbleMotion.IN_NANOS)).isEqualTo(1f);
        assertThat(BubbleMotion.inScale(BubbleMotion.IN_NANOS * 3)).isEqualTo(1f);
    }

    /** A bounce, not a fade-up: the scale must overshoot once and come back. */
    @Test void theCardOvershootsOnceOnItsWayIn() {
        float peak = 0f;
        int crossings = 0;
        boolean above = false;
        for (long t = 0; t <= BubbleMotion.IN_NANOS; t += BubbleMotion.IN_NANOS / 200) {
            float scale = BubbleMotion.inScale(t);
            peak = Math.max(peak, scale);
            if (scale > 1f != above) { above = scale > 1f; crossings++; }
        }

        assertThat(peak).isCloseTo(BubbleMotion.IN_PEAK, within(0.005f));
        assertThat(crossings).as("up over 1 and back down, exactly once each").isEqualTo(2);
    }

    @Test void opacityRidesTheSameCurveButNeverLeavesItsRange() {
        for (long t = 0; t <= BubbleMotion.IN_NANOS; t += BubbleMotion.IN_NANOS / 50) {
            assertThat(BubbleMotion.inOpacity(t)).isBetween(0f, 1f);
        }
        assertThat(BubbleMotion.inOpacity(0)).isEqualTo(0f);
        assertThat(BubbleMotion.inOpacity(BubbleMotion.IN_NANOS)).isEqualTo(1f);
    }

    @Test void hoveringGrowsTheCardAndLeavingPutsItBack() {
        assertThat(BubbleMotion.hoverScale(0, true)).isEqualTo(1f);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS, true)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(0, false)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS, false)).isEqualTo(1f);
    }

    @Test void hoverIsClampedPastItsDurationSoALateTickCannotOvershoot() {
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS * 9, true)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS * 9, false)).isEqualTo(1f);
    }

    /** The grow must be readable as selection without visibly moving the text. */
    @Test void theHoverGrowthIsSmall() {
        assertThat(BubbleMotion.HOVER_SCALE).isBetween(1.01f, 1.08f);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BubbleMotionTest'`

Expected: FAIL — `BubbleMotion` does not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BubbleMotion.java`:

```java
package dev.jasper.app;

import java.util.concurrent.TimeUnit;

/**
 * A card's scale and opacity over time: a bounce as it arrives, a small grow under the pointer.
 * Pure and driven by elapsed nanoseconds, so tests advance time instead of sleeping and the whole
 * thing rides the buddy window's existing repaint timer rather than a thread of its own.
 */
final class BubbleMotion {
    static final long IN_NANOS = TimeUnit.MILLISECONDS.toNanos(220);
    static final long HOVER_NANOS = TimeUnit.MILLISECONDS.toNanos(90);
    /** A thought bubble arrives small. */
    static final float IN_FROM = 0.6f;
    /** The scale at the top of the bounce, before it settles back to 1. */
    static final float IN_PEAK = 1.06f;
    static final float HOVER_SCALE = 1.04f;

    /**
     * The overshoot constant of the standard back-out curve, solved for this bounce rather than
     * copied: the curve peaks at {@code (2c - 1) / 27} above 1, and the card's scale is
     * {@code IN_FROM + (1 - IN_FROM) * curve}, so reaching a scale of {@link #IN_PEAK} needs the
     * curve to reach 1.15 — hence {@code c = 2.525}. Using the textbook 1.70158 would peak at 1.036.
     */
    private static final float BACK = 2.525f;

    private BubbleMotion() { }

    /** Eased progress, clamped at the ends; overshoots 1 exactly once in between. */
    static float easeOutBack(float t) {
        float u = Math.min(1f, Math.max(0f, t)) - 1f;
        return 1f + (BACK + 1f) * u * u * u + BACK * u * u;
    }

    /** The card's scale this far into its arrival. */
    static float inScale(long elapsedNanos) {
        if (elapsedNanos >= IN_NANOS) return 1f;
        return IN_FROM + (1f - IN_FROM) * easeOutBack((float) elapsedNanos / IN_NANOS);
    }

    /** The same curve, clamped: an overshooting opacity is not a thing. */
    static float inOpacity(long elapsedNanos) {
        if (elapsedNanos >= IN_NANOS) return 1f;
        return Math.min(1f, Math.max(0f, easeOutBack((float) elapsedNanos / IN_NANOS)));
    }

    /** Linear both ways: 90 ms is too short to read a curve, and a curve here only costs precision. */
    static float hoverScale(long elapsedNanos, boolean entering) {
        float t = Math.min(1f, Math.max(0f, (float) elapsedNanos / HOVER_NANOS));
        return entering ? 1f + (HOVER_SCALE - 1f) * t : HOVER_SCALE - (HOVER_SCALE - 1f) * t;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BubbleMotionTest'`

Expected: PASS (7 tests).

- [ ] **Step 5: Prove the overshoot test has teeth**

Temporarily set `BACK = 0f` (which makes the curve a plain cubic ease-out with no overshoot) and re-run. `theCardOvershootsOnceOnItsWayIn` must fail on both the peak and the crossing count. Revert.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BubbleMotion.java jasper-app/src/test/java/dev/jasper/app/BubbleMotionTest.java
git commit -m "$(printf 'feat: give the bubble a bounce in and a grow on hover\n\nThe back-out constant is solved for the bounce the design asks for rather\nthan copied from the textbook: the curve peaks (2c-1)/27 above 1, and the\nscale is a 0.6-to-1 interpolation of it, so a scale peak of 1.06 needs\nc = 2.525. The usual 1.70158 would have peaked at 1.036 and read as no\nbounce at all.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 5: The deck panel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java`

**Interfaces:**
- Consumes: `BuddyDeck`, `BuddyNotice`, `BuddyDeckLayout`, `BubbleMotion`, `BuddyFonts`.
- Produces: `BuddyDeckPanel(BuddyDeck deck, Runnable onLayoutChanged)` with `void setClock(LongSupplier)`, `void arrived()`, `boolean expanded()`, `void collapse()`, `int scroll()`, `void handleMove(Point)`, `void handleExit()`, `void handleWheel(int units)`, `boolean handleClick(Point)` (false when the deck should now be hidden), `int hovered()`, `Dimension getPreferredSize()`, `static final int CARD_WIDTH`, `static final int MOTION_MARGIN`, `int cardHeight()`.

Follow the codebase's headless-testing pattern (`TerminalView.handleKey`/`handleMouse`): all input arrives through package-private handlers that tests call directly, so no test needs a real mouse event or a visible window.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java`:

```java
package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyDeckPanelTest {
    private final BuddyDeck deck = new BuddyDeck();
    private final List<String> activated = new ArrayList<>();
    private int layouts;
    private long now;
    private final BuddyDeckPanel panel = panel();

    private BuddyDeckPanel panel() {
        BuddyDeckPanel created = new BuddyDeckPanel(deck, () -> layouts++);
        created.setClock(() -> now);
        return created;
    }

    private void post(String key, String title) {
        deck.post(new BuddyNotice("terminal", key, title, BuddyNotice.State.DONE,
            () -> "Finished in 1m 12s", () -> activated.add(title)));
        panel.arrived();
    }

    /** Lays the panel out at its preferred size, the way the window does before showing it. */
    private void layout() {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
    }

    private Point inCard(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());
        return new Point(BuddyDeckPanel.MOTION_MARGIN + 30, BuddyDeckPanel.MOTION_MARGIN + card.y + 4);
    }

    private Point dismissOf(int index) {
        Rectangle card = BuddyDeckLayout.expanded(index, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());
        Rectangle target = BuddyDeckLayout.dismissTarget(card);
        return new Point(BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterX(),
            BuddyDeckPanel.MOTION_MARGIN + (int) target.getCenterY());
    }

    @Test void anEmptyDrawerAsksForNoRoomAndPaintsNothing() {
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void theCollapsedDeckIsAsTallAsItsStackPlusRoomToGrow() {
        post("a", "one");
        post("b", "two");
        layout();

        assertThat(panel.expanded()).isFalse();
        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.collapsedHeight(2, panel.cardHeight()) + 2 * BuddyDeckPanel.MOTION_MARGIN);
    }

    /** A card that grows on hover must not be clipped by the window it lives in. */
    @Test void thePanelReservesEnoughMarginForTheLargestScale() {
        post("a", "one");
        layout();

        float widest = Math.max(BubbleMotion.IN_PEAK, BubbleMotion.HOVER_SCALE);
        int overflow = (int) Math.ceil(BuddyDeckPanel.CARD_WIDTH * (widest - 1f) / 2f);
        assertThat(BuddyDeckPanel.MOTION_MARGIN).isGreaterThanOrEqualTo(overflow);
    }

    @Test void clickingTheCollapsedDeckExpandsItInsteadOfActivatingTheTopCard() {
        post("a", "one");
        post("b", "two");
        layout();

        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20,
            BuddyDeckPanel.MOTION_MARGIN + 10))).isTrue();

        assertThat(panel.expanded()).isTrue();
        assertThat(activated).isEmpty();
    }

    @Test void clickingACardInTheExpandedListRunsItsActionAndCollapses() {
        post("a", "one");
        post("b", "two");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        panel.handleClick(inCard(1));

        assertThat(activated).containsExactly("one");
        assertThat(panel.expanded()).isFalse();
    }

    @Test void anOrphanedCardReadsButDoesNotClick() {
        post("a", "one");
        deck.orphan("terminal", "a", "Stopped after 1m 12s");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        panel.handleClick(inCard(0));

        assertThat(activated).isEmpty();
        assertThat(deck.size()).as("it stays in the drawer").isEqualTo(1);
    }

    @Test void theDismissTargetRemovesItsOwnCardAndNotTheOneBehindIt() {
        post("a", "older");
        post("b", "newer");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        panel.handleClick(dismissOf(0));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("older");
        assertThat(activated).as("dismissing is not activating").isEmpty();
        assertThat(panel.expanded()).as("the list stays open to dismiss more").isTrue();
    }

    @Test void clearAllEmptiesTheDrawerAndClosesIt() {
        post("a", "one");
        post("b", "two");
        post("c", "three");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();
        Rectangle row = BuddyDeckLayout.clearRow(3, BuddyDeckPanel.CARD_WIDTH, panel.cardHeight(), panel.scroll());

        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20,
            BuddyDeckPanel.MOTION_MARGIN + (int) row.getCenterY()))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void dismissingTheLastCardClosesTheDrawer() {
        post("a", "only");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        assertThat(panel.handleClick(dismissOf(0))).isFalse();

        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void theWheelScrollsTheExpandedListAndStopsAtBothEnds() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        panel.handleWheel(-5);
        assertThat(panel.scroll()).as("cannot scroll above the first card").isZero();

        panel.handleWheel(3);
        int scrolled = panel.scroll();
        assertThat(scrolled).isPositive();

        panel.handleWheel(9_999);
        assertThat(panel.scroll()).isGreaterThan(scrolled);
        int end = panel.scroll();
        panel.handleWheel(9_999);
        assertThat(panel.scroll()).as("cannot scroll past the last card").isEqualTo(end);
    }

    @Test void collapsingForgetsTheScrollSoTheListReopensAtTheTop() {
        for (int i = 0; i < 40; i++) post("k" + i, "card " + i);
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();
        panel.handleWheel(6);

        panel.collapse();

        assertThat(panel.scroll()).isZero();
    }

    @Test void thePointerPicksOutOneCardAtATimeAndLeavingPicksNone() {
        post("a", "one");
        post("b", "two");
        layout();
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();

        panel.handleMove(inCard(1));
        assertThat(panel.hovered()).isEqualTo(1);

        panel.handleMove(inCard(0));
        assertThat(panel.hovered()).isZero();

        panel.handleExit();
        assertThat(panel.hovered()).isEqualTo(-1);
        assertThat(panel.expanded()).as("leaving closes the list").isFalse();
    }

    @Test void aRunningCardTicksWithoutTheDeckBeingRePosted() {
        long[] elapsed = {0};
        deck.post(new BuddyNotice("terminal", "a", "sleep 600", BuddyNotice.State.ACTIVE,
            () -> "Running · " + elapsed[0] + "s", () -> { }));
        panel.arrived();
        layout();

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 0s");
        elapsed[0] = 72;
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 72s");
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void aNewNoticeRestartsTheArrivalBounce() {
        post("a", "one");
        now = BubbleMotion.IN_NANOS * 4;
        layout();
        assertThat(panel.topScale()).isEqualTo(1f);

        post("b", "two");

        assertThat(panel.topScale()).isEqualTo(BubbleMotion.IN_FROM);
    }

    @Test void everyCardPaintsCollapsedAndExpandedWithoutBlowingUp() {
        for (int i = 0; i < 12; i++) post("k" + i, "card " + i);
        layout();
        assertThat(paintDoesNotThrow()).isTrue();

        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 20, BuddyDeckPanel.MOTION_MARGIN + 10));
        layout();
        panel.handleMove(inCard(2));
        assertThat(paintDoesNotThrow()).isTrue();
    }

    private boolean paintDoesNotThrow() {
        Dimension size = panel.getPreferredSize();
        BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return true;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckPanelTest'`

Expected: FAIL — `BuddyDeckPanel` does not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java`:

```java
package dev.jasper.app;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Paints the drawer and decides what a click landed on. Every decision it makes comes from
 * {@link BuddyDeckLayout} or {@link BubbleMotion}, so this class holds only the state a component
 * must hold — expanded, scrolled, hovered, and when the top card arrived.
 *
 * <p>Input arrives through the package-private handlers, the way {@code TerminalView} takes keys and
 * mouse events, so the tests drive it without a visible window or a real pointer.
 */
final class BuddyDeckPanel extends JComponent {
    static final int CARD_WIDTH = 300;
    /**
     * Slack around the cards so one at its largest scale is not clipped by its own window. A card
     * grows about its centre-left, so the widest overshoot is half the extra width.
     */
    static final int MOTION_MARGIN = 10;
    static final int PAD_X = 14;
    static final int PAD_Y = 10;
    static final int LINE_GAP = 3;
    static final int GLYPH_SIZE = 12;
    static final int GLYPH_GAP = 10;
    static final int WHEEL_STEP = 24;
    private static final String ELLIPSIS = "…";
    private static final String DISMISS = "×";
    private static final String CLEAR_ALL = "Clear all";

    private static final Color FILL = new Color(30, 30, 32, 235);
    private static final Color FILL_HIGHLIGHTED = new Color(58, 58, 62, 242);
    private static final Color BORDER = new Color(255, 255, 255, 26);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Color DETAIL_COLOR = new Color(160, 160, 166);
    private static final Color ORPHAN_TITLE_COLOR = new Color(255, 255, 255, 140);
    private static final Color COUNT_COLOR = new Color(190, 190, 196);

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private LongSupplier clock = System::nanoTime;
    private boolean expanded;
    private int scroll;
    private int hovered = -1;
    private long topArrivedAt;
    private long hoverChangedAt;
    private boolean hoverEntering;

    BuddyDeckPanel(BuddyDeck deck, Runnable onLayoutChanged) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged");
        setOpaque(false);
    }

    /** Tests advance time instead of sleeping; the window leaves this as {@code System::nanoTime}. */
    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** A notice was posted: the top card bounces in and the window re-sizes around the new stack. */
    void arrived() {
        topArrivedAt = clock.getAsLong();
        changed();
    }

    boolean expanded() { return expanded; }

    int scroll() { return scroll; }

    int hovered() { return hovered; }

    void collapse() {
        if (!expanded && scroll == 0 && hovered < 0) return;
        expanded = false;
        scroll = 0;
        hovered = -1;
        changed();
    }

    /** The scale of the newest card right now; the arrival bounce, plus hover when it is the one. */
    float topScale() {
        return BubbleMotion.inScale(clock.getAsLong() - topArrivedAt);
    }

    int cardHeight() {
        FontMetrics title = getFontMetrics(titleFont());
        FontMetrics detail = getFontMetrics(detailFont());
        return Math.max(title.getHeight() + LINE_GAP + detail.getHeight(), GLYPH_SIZE) + 2 * PAD_Y;
    }

    @Override public Dimension getPreferredSize() {
        int count = deck.size();
        if (count == 0) return new Dimension(0, 0);
        int height = expanded
            ? Math.min(BuddyDeckLayout.expandedHeight(count, cardHeight()), maxListHeight())
            : BuddyDeckLayout.collapsedHeight(count, cardHeight());
        return new Dimension(CARD_WIDTH + 2 * MOTION_MARGIN, height + 2 * MOTION_MARGIN);
    }

    /** The list is bounded by the screen, not by the drawer: what does not fit is scrolled to. */
    private int maxListHeight() {
        List<Rectangle> screens = BuddyWindow.usableScreens();
        int usable = screens.isEmpty() ? 800 : screens.getFirst().height;
        return Math.max(cardHeight(), usable - 4 * MOTION_MARGIN);
    }

    private int viewportHeight() { return Math.max(0, getHeight() - 2 * MOTION_MARGIN); }

    private int contentHeight() { return BuddyDeckLayout.expandedHeight(deck.size(), cardHeight()); }

    // --- input -------------------------------------------------------------------------------

    void handleMove(Point point) {
        int index = indexAt(point);
        if (index == hovered) return;
        hovered = index;
        hoverEntering = index >= 0;
        hoverChangedAt = clock.getAsLong();
        repaint();
    }

    void handleExit() {
        hovered = -1;
        collapse();
    }

    void handleWheel(int units) {
        if (!expanded) return;
        int next = BuddyDeckLayout.clampScroll(scroll + units * WHEEL_STEP, contentHeight(), viewportHeight());
        if (next == scroll) return;
        scroll = next;
        repaint();
    }

    /**
     * Returns false when the drawer should now be hidden — it was emptied, or a card was activated
     * and the window it points at is about to come forward.
     */
    boolean handleClick(Point point) {
        if (deck.isEmpty()) return false;
        if (!expanded) {
            expanded = true;
            scroll = 0;
            hovered = -1;
            changed();
            return true;
        }
        Rectangle clear = BuddyDeckLayout.clearRow(deck.size(), CARD_WIDTH, cardHeight(), scroll);
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        if (clear != null && clear.contains(local)) {
            deck.clear();
            collapse();
            return false;
        }
        int index = BuddyDeckLayout.cardAt(local.y, deck.size(), cardHeight(), scroll);
        if (index < 0) return true;
        List<BuddyNotice> notices = deck.notices();
        BuddyNotice notice = notices.get(index);
        Rectangle card = BuddyDeckLayout.expanded(index, CARD_WIDTH, cardHeight(), scroll);
        if (BuddyDeckLayout.dismissTarget(card).contains(local)) {
            deck.dismiss(notice.source(), notice.key());
            hovered = -1;
            if (deck.isEmpty()) { collapse(); return false; }
            scroll = BuddyDeckLayout.clampScroll(scroll, contentHeight(), viewportHeight());
            changed();
            return true;
        }
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
        collapse();
        notice.activate().run();
        return false;
    }

    /** The card index under a point, or -1; collapsed, only the top card is a target. */
    private int indexAt(Point point) {
        if (deck.isEmpty()) return -1;
        Point local = new Point(point.x - MOTION_MARGIN, point.y - MOTION_MARGIN);
        if (!expanded) {
            return new Rectangle(0, 0, CARD_WIDTH, cardHeight()).contains(local) ? 0 : -1;
        }
        return BuddyDeckLayout.cardAt(local.y, deck.size(), cardHeight(), scroll);
    }

    private void changed() {
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    // --- painting ----------------------------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> notices = deck.notices();
        if (notices.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(MOTION_MARGIN, MOTION_MARGIN);
            if (expanded) paintExpanded(g2, notices); else paintCollapsed(g2, notices);
        } finally { g2.dispose(); }
    }

    /** Deepest first, so the newest card lands on top of the ones peeking out behind it. */
    private void paintCollapsed(Graphics2D g2, List<BuddyNotice> notices) {
        int height = cardHeight();
        int drawn = Math.min(notices.size(), BuddyDeckLayout.MAX_PEEKED);
        for (int index = drawn - 1; index >= 0; index--) {
            Rectangle card = BuddyDeckLayout.collapsed(index, CARD_WIDTH, height);
            float scale = index == 0 ? topScale() * hoverScale(0) : 1f;
            float opacity = index == 0 ? BubbleMotion.inOpacity(clock.getAsLong() - topArrivedAt) : 1f;
            paintCard(g2, notices.get(index), card, index == 0 && hovered == 0, scale, opacity,
                index == 0 && notices.size() > BuddyDeckLayout.MAX_PEEKED ? notices.size() : 0, false);
        }
    }

    private void paintExpanded(Graphics2D g2, List<BuddyNotice> notices) {
        int height = cardHeight();
        Rectangle clip = new Rectangle(-MOTION_MARGIN, -MOTION_MARGIN,
            CARD_WIDTH + 2 * MOTION_MARGIN, getHeight());
        g2.setClip(clip);
        for (int index = 0; index < notices.size(); index++) {
            Rectangle card = BuddyDeckLayout.expanded(index, CARD_WIDTH, height, scroll);
            if (card.y + card.height < 0 || card.y > getHeight()) continue;
            paintCard(g2, notices.get(index), card, hovered == index, hoverScale(index), 1f, 0, true);
        }
        Rectangle clear = BuddyDeckLayout.clearRow(notices.size(), CARD_WIDTH, height, scroll);
        if (clear == null) return;
        g2.setFont(detailFont());
        FontMetrics metrics = g2.getFontMetrics();
        g2.setColor(COUNT_COLOR);
        g2.drawString(CLEAR_ALL, clear.x + CARD_WIDTH - PAD_X - metrics.stringWidth(CLEAR_ALL),
            clear.y + (clear.height - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    private float hoverScale(int index) {
        if (hovered != index && !(hovered < 0 && !hoverEntering)) {
            return hovered == index ? BubbleMotion.HOVER_SCALE : 1f;
        }
        return BubbleMotion.hoverScale(clock.getAsLong() - hoverChangedAt, hoverEntering && hovered == index);
    }

    /**
     * One card. {@code count}, when non-zero, is drawn as a remainder badge on the collapsed top
     * card; {@code dismissable} adds the × the expanded list hit-tests.
     */
    private void paintCard(Graphics2D g2, BuddyNotice notice, Rectangle card, boolean highlighted,
                           float scale, float opacity, int count, boolean dismissable) {
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(1f, Math.max(0f, opacity))));
            // Grow about the card's centre-left, so it expands away from the buddy rather than over him.
            c.translate(card.x, card.y + card.height / 2f);
            c.scale(scale, scale);
            c.translate(0, -card.height / 2f);
            int arc = BuddyBubblePanel.radiusFor(card.height) * 2;
            c.setColor(highlighted ? FILL_HIGHLIGHTED : FILL);
            c.fillRoundRect(0, 0, card.width, card.height, arc, arc);
            c.setColor(BORDER);
            c.drawRoundRect(0, 0, card.width - 1, card.height - 1, arc, arc);

            int right = card.width - PAD_X;
            if (dismissable) {
                c.setFont(detailFont());
                FontMetrics metrics = c.getFontMetrics();
                c.setColor(highlighted ? COUNT_COLOR : DETAIL_COLOR);
                c.drawString(DISMISS, card.width - BuddyDeckLayout.DISMISS_INSET
                        - BuddyDeckLayout.DISMISS_SIZE + 2,
                    BuddyDeckLayout.DISMISS_INSET + metrics.getAscent());
                right = card.width - BuddyDeckLayout.DISMISS_INSET - BuddyDeckLayout.DISMISS_SIZE - 4;
            } else if (count > 0) {
                String badge = count + "";
                c.setFont(detailFont());
                FontMetrics metrics = c.getFontMetrics();
                c.setColor(COUNT_COLOR);
                c.drawString(badge, card.width - PAD_X - metrics.stringWidth(badge),
                    card.height - PAD_Y + metrics.getAscent() - metrics.getHeight());
                right = card.width - PAD_X - metrics.stringWidth(badge) - GLYPH_GAP;
            }
            if (notice.state() != BuddyNotice.State.ACTIVE) {
                paintGlyph(c, notice.state() == BuddyNotice.State.DONE,
                    right - GLYPH_SIZE, (card.height - GLYPH_SIZE) / 2);
                right -= GLYPH_SIZE + GLYPH_GAP;
            }

            int available = Math.max(0, right - PAD_X);
            FontMetrics title = c.getFontMetrics(titleFont());
            FontMetrics detail = c.getFontMetrics(detailFont());
            int textHeight = title.getHeight() + LINE_GAP + detail.getHeight();
            int y = (card.height - textHeight) / 2;
            c.setFont(titleFont());
            c.setColor(notice.orphaned() ? ORPHAN_TITLE_COLOR : TITLE_COLOR);
            c.drawString(fit(title, notice.title(), available), PAD_X, y + title.getAscent());
            c.setFont(detailFont());
            c.setColor(DETAIL_COLOR);
            c.drawString(fit(detail, text(notice), available),
                PAD_X, y + title.getHeight() + LINE_GAP + detail.getAscent());
        } finally { c.dispose(); }
    }

    /** A supplier that throws must not take the whole drawer down with it. */
    private static String text(BuddyNotice notice) {
        try {
            String detail = notice.detail().get();
            return detail == null ? "" : detail;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /** A check or a cross in the colours the status bar already uses for success and failure. */
    private static void paintGlyph(Graphics2D g2, boolean succeeded, int x, int y) {
        Color configured = UIManager.getColor(
            succeeded ? "Jasper.configSuccessForeground" : "Jasper.configErrorForeground");
        Color ink = configured != null ? configured
            : succeeded ? new Color(0x5A, 0xB0, 0x7A) : new Color(0xC4, 0x5A, 0x53);
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(ink);
            c.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            if (succeeded) {
                c.drawLine(x + 2, y + 6, x + 5, y + 9);
                c.drawLine(x + 5, y + 9, x + 10, y + 3);
            } else {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            }
        } finally { c.dispose(); }
    }

    private static Font titleFont() { return BuddyFonts.system(Font.BOLD, 14f); }

    private static Font detailFont() { return BuddyFonts.system(Font.PLAIN, 12f); }

    /** Truncates with an ellipsis instead of wrapping; a card is one line tall per text line. */
    private static String fit(FontMetrics metrics, String text, int available) {
        if (metrics.stringWidth(text) <= available) return text;
        int ellipsis = metrics.stringWidth(ELLIPSIS);
        int end = text.length();
        while (end > 0 && ellipsis + metrics.stringWidth(text.substring(0, end)) > available) end--;
        return text.substring(0, end) + ELLIPSIS;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckPanelTest'`

Expected: PASS (16 tests). If `hoverScale` proves fiddly, simplify it to
`return hovered == index ? BubbleMotion.hoverScale(clock.getAsLong() - hoverChangedAt, true) : 1f;`
— the leave animation is the one that matters least, and a simpler correct version beats a clever wrong one. Record the simplification in the plan status banner.

- [ ] **Step 5: Check source hygiene**

Run:

```bash
python3 - <<'PY'
import pathlib
for name in ["jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java"]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF
              or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    print(name, "bad chars:", bad)
PY
```

Expected: `bad chars: 0`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java
git commit -m "$(printf 'feat: paint the drawer and hit-test what a click landed on\n\nInput arrives through package-private handlers, the way TerminalView takes\nkeys, so the whole thing is driven headlessly: expanding, dismissing the\nright card rather than the one behind it, clear-all, the wheel stopping at\nboth ends, and an orphan that reads but does not click.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 6: The drawer's window

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckWindowTest.java`

**Interfaces:**
- Consumes: `BuddyDeckPanel`, `BuddyDeck`, `BuddyBubblePlacement.beside`, `BuddyWindow.usableScreens()`.
- Produces: `BuddyDeckWindow(BuddyDeck deck)` with `void showBeside(Rectangle anchorOnScreen)`, `void refresh()`, `void hide()`, `void dispose()`, `boolean isShowing()`; and on `BuddyWindow`: `void attachDeck(BuddyDeck)`, `void refreshDeck()` replacing `showMessage(...)`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyDeckWindowTest.java`:

```java
package dev.jasper.app;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class BuddyDeckWindowTest {
    private static final Rectangle ANCHOR = new Rectangle(400, 300, 84, 96);

    private static BuddyNotice notice(String key, String title) {
        return new BuddyNotice("terminal", key, title, BuddyNotice.State.DONE, () -> "Finished in 5s", () -> { });
    }

    @Test void anEmptyDrawerNeverPutsAWindowOnScreen() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BuddyDeck deck = new BuddyDeck();
        BuddyDeckWindow window = new BuddyDeckWindow(deck);
        try {
            window.showBeside(ANCHOR);

            assertThat(window.isShowing()).isFalse();
        } finally { window.dispose(); }
    }

    @Test void postingThenRefreshingShowsTheDrawerAndItStaysUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BuddyDeck deck = new BuddyDeck();
        BuddyDeckWindow window = new BuddyDeckWindow(deck);
        try {
            window.showBeside(ANCHOR);
            deck.post(notice("a", "./gradlew build"));
            window.refresh();

            assertThat(window.isShowing()).isTrue();
        } finally { window.dispose(); }
    }

    @Test void clearingTheDrawerTakesTheWindowAway() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BuddyDeck deck = new BuddyDeck();
        BuddyDeckWindow window = new BuddyDeckWindow(deck);
        try {
            window.showBeside(ANCHOR);
            deck.post(notice("a", "./gradlew build"));
            window.refresh();
            deck.clear();
            window.refresh();

            assertThat(window.isShowing()).isFalse();
        } finally { window.dispose(); }
    }

    @Test void disposingTwiceIsHarmless() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BuddyDeckWindow window = new BuddyDeckWindow(new BuddyDeck());
        window.dispose();
        window.dispose();

        assertThat(window.isShowing()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckWindowTest'`

Expected: FAIL — `BuddyDeckWindow` does not exist. (On a headless runner these assume out; the class must still compile, so the failure is a compile error.)

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyDeckWindow.java`:

```java
package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * The drawer's window: translucent, always on top and never focused, like the bubble it replaces.
 * Unlike the bubble it has no auto-hide timer — the drawer is a standing list, so it goes away only
 * when it is emptied, when a card is activated, or when the buddy does.
 */
final class BuddyDeckWindow {
    private final JWindow window = new JWindow();
    private final BuddyDeck deck;
    private final BuddyDeckPanel panel;
    private Rectangle anchor;
    private boolean disposed;

    BuddyDeckWindow(BuddyDeck deck) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyDeckPanel(deck, this::layout);
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(panel);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { panel.handleMove(event.getPoint()); }
            @Override public void mouseExited(MouseEvent event) {
                // Moving onto a card is an exit from the panel's parent, not from the drawer.
                if (panel.contains(event.getPoint())) return;
                panel.handleExit();
                layout();
            }
            // A macOS control-click is the popup trigger yet reports the left button; it must not activate.
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown()) return;
                if (!panel.handleClick(event.getPoint())) hide();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                panel.handleWheel(event.getWheelRotation());
            }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);
        panel.addMouseWheelListener(mouse);
    }

    /** Remembers where the buddy is and shows the drawer there, if there is anything in it. */
    void showBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        layout();
    }

    /** The drawer changed: re-size and re-place it, or take it away when it is now empty. */
    void refresh() { layout(); }

    void hide() {
        if (disposed) return;
        panel.collapse();
        window.setVisible(false);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        window.dispose();
    }

    boolean isShowing() { return !disposed && window.isVisible(); }

    private void layout() {
        if (disposed || anchor == null) return;
        if (deck.isEmpty()) { window.setVisible(false); return; }
        Dimension size = panel.getPreferredSize();
        if (size.width <= 0 || size.height <= 0) { window.setVisible(false); return; }
        window.setSize(size);
        window.setLocation(place(size));
        window.setVisible(true);
    }

    private Point place(Dimension size) {
        return BuddyBubblePlacement.beside(anchor, size, screenFor(anchor));
    }

    /** The usable screen holding the anchor's centre, else the default one, else a plausible fallback. */
    private static Rectangle screenFor(Rectangle anchor) {
        List<Rectangle> screens = BuddyWindow.usableScreens();
        if (screens.isEmpty()) return new Rectangle(0, 0, 1280, 800);
        for (Rectangle screen : screens) {
            if (screen.contains((int) anchor.getCenterX(), (int) anchor.getCenterY())) return screen;
        }
        return screens.getFirst();
    }
}
```

In `BuddyWindow.java`:

1. Add a field beside `private BuddyBubble bubble;`:

```java
    private BuddyDeckWindow drawer;
```

2. Replace the whole `showMessage` method with:

```java
    /** The application owns the drawer's contents; the buddy only gives it somewhere to sit. */
    void attachDeck(BuddyDeck deck) {
        if (disposed || drawer != null) return;
        drawer = new BuddyDeckWindow(deck);
        if (window.isVisible()) drawer.showBeside(window.getBounds());
    }

    /** A notice was posted, dismissed or cleared: re-place the drawer beside him. */
    void refreshDeck() {
        if (drawer == null) return;
        if (window.isVisible()) drawer.showBeside(window.getBounds()); else drawer.hide();
    }
```

3. Keep `statusGlyph` **only if** nothing else uses it; `BuddyDeckPanel` now paints its own glyph, so delete `statusGlyph` and the `javax.swing.Icon` import it needed.

4. In `hide()`, after `if (bubble != null) bubble.hide();` add:

```java
        if (drawer != null) drawer.hide();
```

5. In `dispose()`, after `if (bubble != null) bubble.dispose();` add:

```java
        if (drawer != null) drawer.dispose();
```

6. In the mouse adapter's `mouseDragged` and in `mouseReleased`, after the window moves, keep the drawer with him — add at the end of `mouseDragged`:

```java
                refreshDeck();
```

7. In `show()` (find the method that calls `window.setVisible(true)`), add after it:

```java
        refreshDeck();
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckWindowTest' --tests 'dev.jasper.app.Buddy*'`

Expected: PASS. The `BuddyDeckWindowTest` cases skip on a headless runner and run on the dev Mac.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyDeckWindow.java jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckWindowTest.java
git commit -m "$(printf 'feat: hang the drawer beside the buddy\n\nOne window for the whole deck, replacing the one-window-per-bubble\narrangement, and with no auto-hide timer: the drawer is a standing list,\nso it leaves only when it is emptied, when a card is activated, or when\nthe buddy does.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 7: The app learns when a command starts

This closes the gap named in the deviations: nothing currently tells the app a command began, so the running card and the typing animation have no trigger.

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/TerminalSessionTest.java`

**Interfaces:**
- Consumes: the existing OSC 133 handling and `FakeConnector`.
- Produces:
  - `TerminalSession.Listener.commandStarted(String command)` — a default no-op, fired on the reader thread at the C mark, **outside** the buffer lock.
  - `TerminalPane.onCommandStarted` — a `Consumer<String>`, delivered on the EDT.
  - `TerminalPane.onClosed` — a `Runnable`, fired once from `close()`.
  - `WindowContent.CommandStartedSink { void accept(String command, Object pane, LongSupplier elapsedNanos, Runnable focus); }` and field `onCommandStarted`; `CommandFinishedSink` widened with the same `Object pane`; `WindowContent.onPaneClosed` — a `Consumer<Object>` taking the pane as the key. These are the **final** signatures: `CommandNotifier` keys every notice on the pane, so both sinks must carry it from the start.

- [ ] **Step 1: Write the failing test**

Append to `jasper-terminal/src/test/java/dev/jasper/terminal/TerminalSessionTest.java`. Match the file's existing helpers for feeding marks — read the neighbouring `commandExecuted` tests and reuse their exact setup rather than inventing a second style.

```java
    @Test void theCommandStartMarkIsReportedBeforeTheCommandFinishes() {
        List<String> started = new ArrayList<>();
        List<String> finished = new ArrayList<>();
        session.addListener(new TerminalSession.Listener() {
            @Override public void commandStarted(String command) { started.add(command); }
            @Override public void commandExecuted(String command, OptionalInt exitStatus,
                    Optional<Path> workingDirectory, Duration duration) { finished.add(command); }
        });

        feedMarks("A", "B");
        connector.feed("./gradlew build");
        feedMarks("C");

        assertThat(started).containsExactly("./gradlew build");
        assertThat(finished).as("nothing has finished yet").isEmpty();

        feedMarks("D;0");

        assertThat(finished).containsExactly("./gradlew build");
        assertThat(started).as("one start per command").hasSize(1);
    }

    @Test void aCycleWithNothingTypedReportsNoStart() {
        List<String> started = new ArrayList<>();
        session.addListener(new TerminalSession.Listener() {
            @Override public void commandStarted(String command) { started.add(command); }
        });

        feedMarks("A", "B", "C", "D;0");

        assertThat(started).isEmpty();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.TerminalSessionTest'`

Expected: FAIL — `commandStarted` is not a member of `TerminalSession.Listener`.

- [ ] **Step 3: Write the implementation**

In `TerminalSession.java`, add to the `Listener` interface, immediately above `commandExecuted`:

```java
        /**
         * The shell marked the start of a command it is about to run (OSC 133 C). Reader thread, and
         * fired outside the buffer lock. Exactly one start per {@link #commandExecuted}, except when
         * the pane is closed mid-command — then there is a start and no finish, which is precisely
         * the case a running notice exists to show.
         */
        default void commandStarted(String command) {
        }
```

In `captureCommand()`, fire after the lock is released. The existing early `return` inside the `try` block leaves through the `finally` and past this code, which is correct: nothing was captured, so there is nothing to report.

```java
    private void captureCommand() {
        String reported = pendingCommandText;
        pendingCommandText = null;
        buffer.lock();
        try {
            String text = reported;
            if (text == null) {
                if (commandStartRow < 0) return;
                long endRow = absoluteRow(terminal.getCursorY() - 1);
                if (terminal.getCursorX() - 1 == 0) endRow--; // Enter moved the cursor to a fresh line
                text = CommandCapture.text(commandStartRow, commandStartColumn, endRow, buffer.getWidth(), this::lineAtLocked);
            }
            commandStartRow = -1;
            pendingCommand = text.isEmpty() ? null : text;
            commandStartedAt = clock.getAsLong();
        } finally {
            buffer.unlock();
        }
        // Outside the lock: listeners run arbitrary code and the buffer lock must stay short.
        String started = pendingCommand;
        if (started != null) listeners.forEach(l -> l.commandStarted(started));
    }
```

In `TerminalPane.java`, add the two hooks beside `onCommandFinished`:

```java
    /** A command began. Delivered on the EDT. */
    Consumer<String> onCommandStarted = command -> { };

    /** This pane is gone: anything holding it as a key should let go. Fired once, on the EDT. */
    Runnable onClosed = () -> { };
```

In the same file's `listener`, add:

```java
        @Override public void commandStarted(String command) {
            // commandStarted arrives on the reader thread; everything downstream is Swing.
            Consumer<String> started = onCommandStarted;
            javax.swing.SwingUtilities.invokeLater(() -> started.accept(command));
        }
```

In `TerminalPane.close()`, fire the hook before the fields are cleared:

```java
    @Override public void close() {
        if (closed) return;
        closed = true;
        Runnable closedHook = onClosed;
        onClosed = () -> { };
        closedHook.run();
        if (findBar != null) findBar.dispose();
```

…and add `onCommandStarted = command -> { };` to the line that already resets `onChanged`/`onFocused`/`onClose`.

In `WindowContent.java`, widen `CommandFinishedSink` and add its twin. Every notice is keyed on the
pane, so both sinks carry it; `ownPaneFocused` joins the origin for the same reason the notification
rule now asks about the pane rather than the tab:

```java
    /** What the application wants to know about a finished command. {@code pane} is the notice's key. */
    interface CommandFinishedSink {
        void accept(String command, java.util.OptionalInt exitStatus, java.time.Duration duration,
                    CommandNotice.Origin origin, Object pane, Runnable focus);
    }

    /** What the application wants to know about a command that has just begun. */
    interface CommandStartedSink {
        void accept(String command, Object pane, java.util.function.LongSupplier elapsedNanos, Runnable focus);
    }

    /** Set by the application; null in tests. */
    CommandStartedSink onCommandStarted;

    /** Set by the application: a pane is gone, so anything keyed on it should be released. */
    java.util.function.Consumer<Object> onPaneClosed = pane -> { };
```

In `configurePane`, replace the existing `pane.onCommandFinished` assignment with all three. The
elapsed supplier is stamped on the EDT at the start mark rather than taken from the session's clock:
the session's duration stays authoritative for the *finished* card, and this one only has to make a
ticking card read sensibly while it runs.

```java
        pane.onCommandStarted = command -> {
            if (onCommandStarted == null) return;
            long startedAt = System.nanoTime();
            onCommandStarted.accept(command, pane, () -> System.nanoTime() - startedAt,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); });
        };
        pane.onCommandFinished = (command, exitStatus, duration) -> {
            if (onCommandFinished == null) return;
            onCommandFinished.accept(command, exitStatus, duration,
                new CommandNotice.Origin(anyWindowActive.getAsBoolean(), isActiveAndOpen(),
                    tab == currentTab(), pane.view() != null && pane.view().isFocusOwner()),
                pane,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); });
        };
        pane.onClosed = () -> onPaneClosed.accept(pane);
```

`CommandNotice.Origin` does not gain its fourth component until Task 8, so **this file will not
compile until Task 8 lands**. Task 7's gate is `./gradlew :jasper-terminal:test`; the app module is
checked at the end of Task 8.

In the method that resets the sinks (the one containing `onToggleBuddy = () -> {}; buddyEnabled = () -> false;`), add:

```java
        onCommandStarted = null; onPaneClosed = pane -> {};
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.TerminalSessionTest' && ./gradlew :jasper-app:compileJava`

Expected: PASS, and `jasper-app` compiles.

- [ ] **Step 5: Prove the lock discipline**

Add a temporary listener in the new test whose `commandStarted` calls `session.workingDirectory()`, and confirm the test still passes rather than deadlocking. `TerminalTextBuffer`'s lock is re-entrant, so this would pass either way — the point of firing outside the lock is bounded work, not deadlock. Remove the temporary listener; do not commit it.

- [ ] **Step 6: Commit**

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java jasper-terminal/src/test/java/dev/jasper/terminal/TerminalSessionTest.java jasper-app/src/main/java/dev/jasper/app/TerminalPane.java jasper-app/src/main/java/dev/jasper/app/WindowContent.java
git commit -m "$(printf 'feat: tell the app when a command starts, not only when it ends\n\nNothing reached the app at the C mark, so CommandNotifier.passedThreshold\nhad no production caller at all and the typing animation could never run.\nThe session now reports the start, outside the buffer lock, and the pane\nforwards it to the EDT along with a hook for its own close.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 8: Routing

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandNotifier.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandNotice.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/NativeNotifier.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandNoticeTest.java` (create if absent)
- Test: `jasper-app/src/test/java/dev/jasper/app/NativeNotifierTest.java`

**Interfaces:**
- Consumes: `BuddyDeck`, `BuddyNotice`, `CommandNotice.Origin`.
- Produces:
  - `record CommandNotice.Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected, boolean ownPaneFocused)`.
  - `CommandNotifier(Supplier<Duration> threshold, BuddyDeck deck, Runnable onDeckChanged, BiConsumer<String,String> operatingSystem, Consumer<Boolean> onWorkingChanged, BiFunction<Duration,Runnable,Runnable> schedule)`.
  - `void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate)`,
    `void finished(Object key, String command, OptionalInt exitStatus, Duration ran, CommandNotice.Origin origin, Runnable activate)`,
    `void closed(Object key, Duration ran)`,
    `static final String SOURCE = "terminal"`.
  - `NativeNotifier.send(String title, String detail)` replacing `deliver(...)`; `CommandNotifier.Channel` is **deleted**.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java` entirely:

```java
package dev.jasper.app;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandNotifierTest {
    private record Sent(String title, String detail) { }

    private final BuddyDeck deck = new BuddyDeck();
    private final List<Sent> os = new ArrayList<>();
    private final List<Boolean> working = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private int refreshes;

    /** Nothing fires by itself: a test runs the scheduled task when it wants the threshold to pass. */
    private CommandNotifier notifier(int seconds) {
        return new CommandNotifier(() -> Duration.ofSeconds(seconds), deck, () -> refreshes++,
            (title, detail) -> os.add(new Sent(title, detail)), working::add,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
    }

    private void passThreshold() {
        List.copyOf(scheduled).forEach(Runnable::run);
    }

    private static final CommandNotice.Origin HIDDEN_TAB = new CommandNotice.Origin(true, true, false, false);
    private static final CommandNotice.Origin FOCUSED_PANE = new CommandNotice.Origin(true, true, true, true);
    private static final CommandNotice.Origin UNFOCUSED_SPLIT = new CommandNotice.Origin(true, true, true, false);

    @Test void aCommandThatPassesTheThresholdGetsARunningCardAndNoNotification() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> { });
        passThreshold();

        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("./gradlew build");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.ACTIVE);
        });
        assertThat(os).isEmpty();
        assertThat(working).containsExactly(true);
    }

    /** The card is status, not an interruption: it appears whatever has focus. */
    @Test void theRunningCardAppearsEvenWhenYouAreLookingRightAtThePane() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            FOCUSED_PANE, () -> { });

        assertThat(deck.notices()).singleElement()
            .extracting(BuddyNotice::state).isEqualTo(BuddyNotice.State.DONE);
        assertThat(os).as("the pane had focus, so the OS is not involved").isEmpty();
    }

    @Test void theRunningCardTicksFromTheSuppliedElapsedTime() {
        CommandNotifier notifier = notifier(10);
        long[] elapsed = {Duration.ofSeconds(11).toNanos()};

        notifier.started("pane", "sleep 600", () -> elapsed[0], () -> { });
        passThreshold();

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 11s");
        elapsed[0] = Duration.ofSeconds(72).toNanos();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Running · 1m 12s");
    }

    @Test void finishingReplacesTheRunningCardInPlaceRatherThanAddingASecond() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> { });

        assertThat(deck.size()).isEqualTo(1);
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(working).containsExactly(true, false);
    }

    @Test void aFailureSaysSoAndCarriesItsExitStatus() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "make", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(2), Duration.ofSeconds(45), HIDDEN_TAB, () -> { });

        assertThat(deck.notices().getFirst()).satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED);
            assertThat(notice.detail().get()).isEqualTo("Exited 2 · 45s");
        });
    }

    @Test void aCommandThatFinishesOutOfSightAlsoReachesTheOperatingSystem() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> { });

        assertThat(os).containsExactly(new Sent("./gradlew build", "Finished in 1m 12s"));
    }

    /** The deliberate widening: a visible split pane you are not typing in still notifies. */
    @Test void aVisibleButUnfocusedSplitPaneStillNotifies() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "make", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), UNFOCUSED_SPLIT, () -> { });

        assertThat(os).hasSize(1);
    }

    @Test void aShortCommandLeavesNoCardAndNotifiesNobody() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "ls", () -> 0L, () -> { });
        notifier.finished("pane", "ls", OptionalInt.of(0), Duration.ofSeconds(2), HIDDEN_TAB, () -> { });

        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).as("it never passed the threshold").isEmpty();
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    @Test void aZeroThresholdTurnsTheWholeFeatureOff() {
        CommandNotifier notifier = notifier(0);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> { });
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofHours(1),
            HIDDEN_TAB, () -> { });

        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void aSecondLongCommandDoesNotRestartTheTypingAndTheLastOneEndsIt() {
        CommandNotifier notifier = notifier(10);

        notifier.started("a", "one", () -> 0L, () -> { });
        notifier.started("b", "two", () -> 0L, () -> { });
        passThreshold();
        assertThat(working).containsExactly(true);

        notifier.finished("a", "one", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, () -> { });
        assertThat(working).as("one still running").containsExactly(true);

        notifier.finished("b", "two", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, () -> { });
        assertThat(working).containsExactly(true, false);
    }

    /** Never removed: the drawer is what you look at to remember, so a closed pane leaves its card. */
    @Test void aPaneClosingOrphansItsCardAndStopsTheTyping() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> { });
        passThreshold();

        notifier.closed("pane", Duration.ofSeconds(72));

        assertThat(deck.size()).isEqualTo(1);
        assertThat(deck.notices().getFirst().orphaned()).isTrue();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Stopped after 1m 12s");
        assertThat(working).containsExactly(true, false);
    }

    @Test void aPaneClosingBeforeTheThresholdLeavesNothingBehind() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "ls", () -> 0L, () -> { });

        notifier.closed("pane", Duration.ofSeconds(1));

        assertThat(deck.notices()).isEmpty();
        assertThat(scheduled).isEmpty();
        assertThat(working).isEmpty();
    }

    @Test void aSecondCommandInTheSamePaneSupersedesTheFirstRatherThanStacking() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "first", () -> 0L, () -> { });

        notifier.started("pane", "second", () -> 0L, () -> { });
        passThreshold();

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second");
        assertThat(working).as("one pane, one typing buddy").containsExactly(true);
    }

    @Test void multiLineCommandsCollapseForTheTitle() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "echo a\necho b", () -> 0L, () -> { });
        passThreshold();

        assertThat(deck.notices().getFirst().title()).isEqualTo("echo a ↵ echo b");
    }

    @Test void durationsReadAsPeopleSayThem() {
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(72))).isEqualTo("1m 12s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(120))).isEqualTo("2m");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(7500))).isEqualTo("2h 5m");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/CommandNoticeTest.java`:

```java
package dev.jasper.app;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandNoticeTest {
    private static final Duration TEN = Duration.ofSeconds(10);
    private static final Duration LONG = Duration.ofSeconds(72);

    private static boolean notify(boolean anyActive, boolean ownWindow, boolean ownTab, boolean ownPane) {
        return CommandNotice.shouldNotify(
            new CommandNotice.Origin(anyActive, ownWindow, ownTab, ownPane), LONG, TEN);
    }

    /** Only the pane you are actually typing in is quiet. */
    @Test void theOneQuietCaseIsTheCommandsOwnFocusedPane() {
        assertThat(notify(true, true, true, true)).isFalse();
    }

    @Test void everyOtherArrangementNotifies() {
        for (int bits = 0; bits < 16; bits++) {
            boolean anyActive = (bits & 1) != 0, ownWindow = (bits & 2) != 0;
            boolean ownTab = (bits & 4) != 0, ownPane = (bits & 8) != 0;
            boolean quiet = anyActive && ownWindow && ownTab && ownPane;

            assertThat(notify(anyActive, ownWindow, ownTab, ownPane))
                .as("active=%s window=%s tab=%s pane=%s", anyActive, ownWindow, ownTab, ownPane)
                .isEqualTo(!quiet);
        }
    }

    @Test void aShortCommandOrADisabledThresholdNotifiesNobodyAtAll() {
        assertThat(CommandNotice.shouldNotify(new CommandNotice.Origin(false, false, false, false),
            Duration.ofSeconds(2), TEN)).isFalse();
        assertThat(CommandNotice.shouldNotify(new CommandNotice.Origin(false, false, false, false),
            Duration.ofHours(1), Duration.ZERO)).isFalse();
    }
}
```

In `NativeNotifierTest.java`, replace every `deliver(title, detail, succeeded, activate)` call with `send(title, detail)`. Keep the existing argument-vector assertion — it is the one that proves user text travels as `argv` and is never parsed as script.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandNotifierTest' --tests 'dev.jasper.app.CommandNoticeTest' --tests 'dev.jasper.app.NativeNotifierTest'`

Expected: FAIL — `Origin` takes three components, `CommandNotifier`'s constructor and methods do not match, `NativeNotifier.send` does not exist.

- [ ] **Step 3: Write the implementation**

Replace `CommandNotice.java`'s record and rule:

```java
    /**
     * Where a finished command ran, relative to what the user is looking at. All four come from state
     * the app already keeps: {@code WindowContent.active}, whether the pane's tab is the selected one,
     * and whether the pane itself owns the keyboard focus.
     */
    record Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected,
                  boolean ownPaneFocused) { }

    /**
     * True when the command ran at least {@code threshold} and finished anywhere except the pane you
     * were typing in. This is deliberately wider than the previous rule, which stayed quiet for the
     * whole selected tab: a command finishing in a visible but unfocused split pane is easy to miss,
     * and the drawer's card is not enough on its own when the window is behind something.
     */
    static boolean shouldNotify(Origin origin, Duration ran, Duration threshold) {
        if (threshold.isZero() || ran.compareTo(threshold) < 0) return false;
        return !(origin.anyWindowActive() && origin.ownWindowActive()
            && origin.ownTabSelected() && origin.ownPaneFocused());
    }
```

Replace `CommandNotifier.java` entirely:

```java
package dev.jasper.app;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The terminal's producer for the buddy's drawer: it posts a running card once a command has taken
 * long enough to be worth remembering, replaces that card with the outcome, and sends the outcome to
 * the OS when the command did not finish under your hands.
 *
 * <p>Holds no Swing state. Every seam is a standard functional type rather than a new interface —
 * including {@code schedule}, which is Swing's {@code Timer} in the application and a list a test
 * runs by hand. The previous {@code Channel} interface is gone: the buddy being hidden no longer
 * changes where a notice goes, so it had one real implementation left.
 *
 * <p>EDT only.
 */
final class CommandNotifier {
    /** Every notice this class posts is the terminal's; a transfer or an SSH session brings its own. */
    static final String SOURCE = "terminal";

    /** A command title longer than this is cut; the pane it points at is unaffected. */
    private static final int MAX_TITLE = 60;

    private final Supplier<Duration> threshold;
    private final BuddyDeck deck;
    private final Runnable onDeckChanged;
    private final BiConsumer<String, String> operatingSystem;
    private final Consumer<Boolean> onWorkingChanged;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Map<Object, InFlight> inFlight = new HashMap<>();
    /** Commands that have been running past the threshold. */
    private int running;

    /** A command between its start mark and its end, with the timer that will promote it to a card. */
    private static final class InFlight {
        Runnable cancel = () -> { };
        boolean passed;
    }

    CommandNotifier(Supplier<Duration> threshold, BuddyDeck deck, Runnable onDeckChanged,
                    BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
                    BiFunction<Duration, Runnable, Runnable> schedule) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onDeckChanged = Objects.requireNonNull(onDeckChanged, "onDeckChanged");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
    }

    /**
     * A command began. Nothing is shown yet: after {@code threshold} it becomes a running card, so
     * the buddy does not twitch for every {@code ls}. {@code elapsedNanos} lets the card tick without
     * being re-posted every second.
     */
    void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate) {
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        inFlight.put(key, flight);
        String title = title(command);
        flight.cancel = schedule.apply(wait, () -> {
            if (inFlight.get(key) != flight) return;
            flight.passed = true;
            flight.cancel = () -> { };
            if (running++ == 0) onWorkingChanged.accept(true);
            deck.post(new BuddyNotice(SOURCE, key, title, BuddyNotice.State.ACTIVE,
                () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong())), activate));
            onDeckChanged.run();
        });
    }

    /** A command ended. Its card becomes the outcome, and the OS hears about it if you were elsewhere. */
    void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                  CommandNotice.Origin origin, Runnable activate) {
        cancel(key);
        Duration wait = threshold.get();
        if (wait.isZero() || ran.compareTo(wait) < 0) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        String title = title(command);
        deck.post(new BuddyNotice(SOURCE, key, title,
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
        onDeckChanged.run();
        if (CommandNotice.shouldNotify(origin, ran, wait)) operatingSystem.accept(title, detail);
    }

    /**
     * The pane is gone. Its card stays — the drawer is what you look at to remember — but stops
     * ticking and stops responding, because there is no longer anywhere for a click to go.
     */
    void closed(Object key, Duration ran) {
        boolean hadCard = inFlight.containsKey(key) && inFlight.get(key).passed;
        cancel(key);
        deck.orphan(SOURCE, key, hadCard ? "Stopped after " + humanize(ran) : "Stopped");
        onDeckChanged.run();
    }

    /** Drops any in-flight command for this key, releasing the typing animation if it had claimed it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        if (flight.passed && running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** One line, newlines shown the way the History palette shows them, cut to a readable length. */
    static String title(String command) {
        String single = command.strip().replace("\r", "").replace("\n", " ↵ ");
        return single.length() > MAX_TITLE ? single.substring(0, MAX_TITLE - 1) + "…" : single;
    }

    /** "45s", "1m 12s", "2m", "2h 5m" — the way a person would say it, not ISO-8601. */
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

In `NativeNotifier.java`: drop `implements CommandNotifier.Channel` from the class declaration (keep `AutoCloseable`), and replace `deliver` with:

```java
    /** Shows a system notification. Silent on anything that is not macOS, which the docs state. */
    void send(String title, String detail) {
        if (!supported) return;
        try {
            worker.execute(() -> run(command(title, detail)));
        } catch (RejectedExecutionException closed) {
            // Shutting down; a missed notification is not worth reporting.
        }
    }
```

Remove the now-unused `@Override`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandNotifierTest' --tests 'dev.jasper.app.CommandNoticeTest' --tests 'dev.jasper.app.NativeNotifierTest'`

Expected: PASS. `JasperApplication` will not compile yet — it still uses the old constructor. That is Task 9.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/CommandNotifier.java jasper-app/src/main/java/dev/jasper/app/CommandNotice.java jasper-app/src/main/java/dev/jasper/app/NativeNotifier.java jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java jasper-app/src/test/java/dev/jasper/app/CommandNoticeTest.java jasper-app/src/test/java/dev/jasper/app/NativeNotifierTest.java
git commit -m "$(printf 'feat: route running and finished commands into the drawer\n\nThe card is status, not an interruption, so it appears whatever has focus;\nonly the OS notification is conditional, and the condition is now the\npane rather than the tab - a command finishing in a visible but unfocused\nsplit is easy to miss.\n\nThe Channel interface is deleted rather than given a second implementation.\nA hidden buddy no longer changes where a notice goes, so it had one real\nimplementation left and the repository forbids the rest.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 9: Wiring, and the documentation

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Modify: `docs/configuration.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-09-17-jasper-buddy-bubble-deck-design.md` (status banner only)

**Interfaces:**
- Consumes: everything above.
- Produces: a running application.

- [ ] **Step 1: Wire the application**

In `JasperApplication.java`, replace the notifier field block:

```java
    private final NativeNotifier nativeNotifier = new NativeNotifier();
    /** The drawer outlives the buddy's window: hiding him must not throw away what you kept. */
    private final BuddyDeck deck = new BuddyDeck();
    private final CommandNotifier notifications = new CommandNotifier(
        () -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
        deck,
        () -> { if (buddy != null) buddy.refreshDeck(); },
        nativeNotifier::send,
        working -> { if (buddy != null) buddy.setWorking(working); },
        JasperApplication::afterDelay);
```

Add the scheduler helper beside the other private statics:

```java
    /** Swing's timer, as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable afterDelay(java.time.Duration delay, Runnable task) {
        javax.swing.Timer timer = new javax.swing.Timer(
            (int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
```

In `newWindow`, replace the single `onCommandFinished` assignment with the three sinks:

```java
        window.content().anyWindowActive = () -> windows.stream().anyMatch(open -> open.content().isActiveAndOpen());
        window.content().onCommandStarted = (command, pane, elapsed, focus) ->
            notifications.started(pane, command, elapsed, focus);
        window.content().onCommandFinished = (command, exitStatus, duration, origin, pane, focus) ->
            notifications.finished(pane, command, exitStatus, duration, origin, focus);
        window.content().onPaneClosed = pane -> notifications.closed(pane, java.time.Duration.ZERO);
```

In `syncBuddy`, after the buddy is created and shown, attach the drawer:

```java
                    if (buddy != null) { buddy.show(); buddy.attachDeck(deck); }
```

- [ ] **Step 2: Build and run the whole suite**

Run: `./gradlew check`

Expected: BUILD SUCCESSFUL. Read the XML in `*/build/test-results/test/` for exact counts; fix any test that referred to the old `showMessage`, the old three-component `Origin`, or `CommandNotifier.Channel`.

- [ ] **Step 3: Check source hygiene across both modules**

```bash
python3 - <<'PY'
import pathlib
for p in list(pathlib.Path("jasper-terminal/src").rglob("*.java")) + list(pathlib.Path("jasper-app/src").rglob("*.java")):
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF
              or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 4: Update the documentation**

In `docs/configuration.md`, replace the `notifications.long_command_seconds` prose section with:

```markdown
`notifications.long_command_seconds` sets how long a command must run before it is worth remembering.
Past that point the desk buddy grows a card for it, and he keeps that card until you dismiss it — the
drawer is a record of what has been going on, not a message that flashes past.

The card appears whatever you are looking at: it is status, not an interruption. Only the **system
notification** is conditional, and it is sent unless the command finished in the pane you were
actually typing in. A command finishing in a visible but unfocused split pane does notify.

Cards stack newest-on-top beside the buddy; clicking the stack opens it as a list, clicking an entry
takes you to that pane, the × on a card dismisses it and **Clear all** empties the drawer. At most
50 are kept, the oldest falling off, and the drawer is cleared when Jasper restarts.

Duration comes from the shell-integration marks, so **without shell integration there are no
notifications and no cards at all** — Jasper cannot know how long anything took. See
[Shell integration](#shell-integration).

Setting it to `0` turns the whole feature off. System notifications are macOS-only; on other
platforms the drawer is the only channel.
```

In `docs/STATUS.md`, replace the finished-command notifications paragraph with a short record of the drawer: cards keyed by source and key so future producers (sftp, SSH) need no deck change; nothing auto-hides; bounded at 50 with per-card dismiss and clear-all; cleared on restart; notification widened from tab to pane; `CommandNotifier.Channel` deleted.

In the spec, change the status line to:

```markdown
**Status:** Implemented. Development branch: `claude/command-notifications` (continuing), from `612a7d6`.
```

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/WindowContent.java docs/
git commit -m "$(printf 'feat: hand the drawer to the running application\n\nThe deck lives in the application rather than in the buddy window, so\nhiding him does not throw away what you kept. The threshold timer is\nSwing'"'"'s, passed in as a plain function so the notifier stays free of\nSwing and the tests run it by hand.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

- [ ] **Step 6: Hand the GUI checks to the user**

Do **not** launch the GUI. Report that these need a person at the desk:

1. A long command in a background tab: the card appears at the threshold while you are elsewhere, the buddy types, the card bounces in.
2. Two or three panes running long commands at once: the cards stack, the count appears past three, clicking expands the list.
3. Clicking a card raises the right window, tab and pane.
4. The × dismisses one card; **Clear all** empties the drawer; neither activates anything.
5. A pane closed mid-command: its card dims, stops ticking and no longer responds to a click.
6. Hover growth and the pill shape at both one and two lines, and that the font is the current macOS system font rather than Helvetica Neue.
7. Whether collapse-on-pointer-exit feels twitchy when reaching for the × — the spec names click-to-close as the one-line alternative.

---

## Self-review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| System font, verified by resolved family | 1 |
| Pill shape, capped radius | 1 |
| Source-keyed notices, future producers | 2 |
| Bounded at 50, oldest fall off | 2 |
| Orphan keeps the outcome, drops the action | 2 |
| Collapsed stack, peek offsets, count past three | 3, 5 |
| Expanded list, scrolling rather than capping | 3, 5 |
| Per-card dismiss, clear-all | 3, 5 |
| Bounce in, hover grow | 4, 5 |
| Nothing auto-hides | 5, 6 |
| Collapse on pointer exit | 5, 6 |
| One window for the whole deck | 6 |
| Running card regardless of focus | 7, 8 |
| Notification when the pane was not focused | 8 |
| `Channel` deleted, not given a second implementation | 8 |
| Cleared on restart (no persistence) | by construction — nothing is written to disk |
| Threshold 0 disables | 8 |
| Documentation | 9 |

**Type consistency** — `BuddyNotice.detail` is `Supplier<String>` in Tasks 2, 5 and 8. `BuddyDeck.orphan(source, key, finalDetail)` takes three arguments in Tasks 2 and 8. `CommandNotice.Origin` has four components in Tasks 7, 8 and 9. `CommandNotifier` keys on `Object` in Tasks 8 and 9, and `WindowContent`'s sinks carry that same `Object pane` in Tasks 7 and 9 — the first draft of this plan defined them narrower in Task 7 and widened them in Task 9, which is exactly the `clearLayers`/`clearFullLayers` bug this check exists to catch; Task 7 now defines them at their final width.

**Build order** — Tasks 7 and 8 are the one place where a task does not leave both modules compiling: Task 7 writes `WindowContent` against the four-component `Origin` that Task 8 creates. Task 7's gate is therefore `:jasper-terminal:test` and Task 8's is the app module. Every other task leaves `./gradlew check` green.
