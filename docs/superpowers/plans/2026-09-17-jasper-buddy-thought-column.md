# Buddy thought column Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move live notices to a thought column above the buddy's head that empties as you look at things, and demote the full history to a drawer he opens on a single click.

**Architecture:** `BuddyNotice` gains a `Kind` (task vs connection) and kind-specific states, with `live()` and `wantsAttention()` derived on the notice so nothing re-derives them. Acknowledgement moves onto `BuddyDeck`, because it is a fact about the reader rather than the thing. Column membership is then `live() || !acknowledged`. Card painting is extracted so the column and the drawer share it; they become two windows over one deck.

**Tech Stack:** Java 25 on the JetBrains Runtime, Swing/AWT (`JWindow`), JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-17-jasper-buddy-thought-column-design.md`

## Global Constraints

- Java 25 on the **JetBrains Runtime**. Use `./gradlew`, never a system `gradle`.
- `jasper-terminal` never depends on `jasper-app`. No public method in `jasper-terminal` takes or returns a JediTerm type.
- **No interface without two real implementations.** Every seam here is a standard functional type.
- Tests run headless (`java.awt.headless=true` is set in `build.gradle.kts`). **A `JWindow` cannot be constructed in a test.** Anything a window decides must be extracted and tested on its own, the way `BuddyDeckWindow.shows` already is — a test that only skips is not coverage.
- Never put raw control, private-use or unpaired surrogate characters in source. `·`, `↵`, `…`, `×` are ordinary printable characters, written literally as the existing sources do.
- Do not launch the GUI. GUI checks are handed to the user.
- Work on `claude/command-notifications`; do not merge. End every commit message with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## What this replaces

The collapsed stack beside the buddy, built earlier today, is thrown away. `BuddyDeckLayout.collapsed`, `collapsedHeight` and the `paintCollapsed`/`paintBacking` path go with it. The expanded list, `BubbleMotion`, the dismiss and clear-all behaviour and `BuddyDeckWindow` all survive as the drawer.

---

### Task 1: Kind, states and acknowledgement

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyNotice.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeck.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/BuddyNoticeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `BuddyNotice.Kind { TASK, CONNECTION }`; `BuddyNotice.State { RUNNING, NEEDS_INPUT, DONE, FAILED, UP, DEGRADED, DOWN }` with `boolean fits(Kind)`.
  - `record BuddyNotice(String source, Object key, Kind kind, String title, State state, Supplier<String> detail, Runnable activate)` with `live()`, `wantsAttention()`, `orphaned()`, `sameAs(String, Object)`.
  - `BuddyDeck.acknowledge(String, Object)`, `BuddyDeck.acknowledged(String, Object)`, `List<BuddyNotice> column()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/BuddyNoticeTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyNoticeTest {
    private static BuddyNotice of(BuddyNotice.Kind kind, BuddyNotice.State state) {
        return new BuddyNotice("terminal", "k", kind, "title", state, () -> "d", () -> { });
    }

    /** A tunnel never completes and a command is never "up"; an impossible notice cannot be built. */
    @Test void eachKindAcceptsOnlyItsOwnStates() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            boolean task = switch (state) {
                case RUNNING, NEEDS_INPUT, DONE, FAILED -> true;
                case UP, DEGRADED, DOWN -> false;
            };
            assertThat(state.fits(BuddyNotice.Kind.TASK)).as("%s as a task", state).isEqualTo(task);
            assertThat(state.fits(BuddyNotice.Kind.CONNECTION)).as("%s as a connection", state).isEqualTo(!task);
        }
    }

    @Test void aNoticeWhoseStateDoesNotFitItsKindIsRefused() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> of(BuddyNotice.Kind.TASK, BuddyNotice.State.UP));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DONE));
    }

    /** Still happening, so it belongs above his head whether or not you have seen it. */
    @Test void liveIsTheThingsStillHappening() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.NEEDS_INPUT).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DEGRADED).live()).isTrue();

        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.DONE).live()).isFalse();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.FAILED).live()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DOWN).live()).isFalse();
    }

    /** Wants you specifically: it ended, it broke, or it is waiting on you. */
    @Test void attentionIsEverythingExceptQuietProgress() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).wantsAttention()).isFalse();

        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            if (state == BuddyNotice.State.RUNNING || state == BuddyNotice.State.UP) continue;
            BuddyNotice.Kind kind = state.fits(BuddyNotice.Kind.TASK)
                ? BuddyNotice.Kind.TASK : BuddyNotice.Kind.CONNECTION;
            assertThat(of(kind, state).wantsAttention()).as("%s", state).isTrue();
        }
    }
}
```

Replace the `notice` helper in `BuddyDeckTest.java` and add the acknowledgement tests. The helper becomes:

```java
    private static BuddyNotice notice(String source, Object key, String title) {
        return new BuddyNotice(source, key, BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.DONE, () -> "done", () -> { });
    }

    private static BuddyNotice running(String source, Object key, String title) {
        return new BuddyNotice(source, key, BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.RUNNING, () -> "running", () -> { });
    }
```

Every existing `new BuddyNotice(...)` in the file gains `BuddyNotice.Kind.TASK` after the key. Then append:

```java
    /** The column's whole rule: still happening, or you have not looked at it yet. */
    @Test void theColumnHoldsWhatIsLiveOrUnseen() {
        deck.post(running("terminal", "a", "a build"));
        deck.post(notice("terminal", "b", "a finished thing"));

        assertThat(deck.column()).extracting(BuddyNotice::title)
            .containsExactly("a finished thing", "a build");
    }

    @Test void lookingAtAFinishedNoticeTakesItOutOfTheColumnButNotTheDrawer() {
        deck.post(notice("terminal", "b", "a finished thing"));

        deck.acknowledge("terminal", "b");

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a finished thing");
        assertThat(deck.acknowledged("terminal", "b")).isTrue();
    }

    /** It is live, so looking at it changes nothing until it stops being live. */
    @Test void lookingAtSomethingStillRunningLeavesItInTheColumn() {
        deck.post(running("terminal", "a", "a build"));

        deck.acknowledge("terminal", "a");

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("a build");
    }

    /** Or a flapping tunnel would go quiet after the first drop. */
    @Test void aNewStateOverAnAcknowledgedKeyAsksForAttentionAgain() {
        deck.post(running("terminal", "a", "a build"));
        deck.acknowledge("terminal", "a");

        deck.post(notice("terminal", "a", "a build"));

        assertThat(deck.acknowledged("terminal", "a")).isFalse();
        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("a build");
    }

    @Test void acknowledgingSomethingThatIsNotThereIsHarmless() {
        deck.acknowledge("terminal", "missing");

        assertThat(deck.acknowledged("terminal", "missing")).isFalse();
    }

    @Test void dismissingForgetsTheAcknowledgementToo() {
        deck.post(notice("terminal", "a", "one"));
        deck.acknowledge("terminal", "a");

        deck.dismiss("terminal", "a");
        deck.post(notice("terminal", "a", "one again"));

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("one again");
    }

    @Test void clearingForgetsEveryAcknowledgement() {
        deck.post(notice("terminal", "a", "one"));
        deck.acknowledge("terminal", "a");

        deck.clear();
        deck.post(notice("terminal", "a", "one again"));

        assertThat(deck.column()).hasSize(1);
    }

    /** The oldest falling off must not leave its acknowledgement behind to leak forever. */
    @Test void aNoticePushedOutByTheBoundTakesItsAcknowledgementWithIt() {
        deck.post(notice("terminal", "first", "first"));
        deck.acknowledge("terminal", "first");
        for (int i = 0; i < BuddyDeck.MAX_NOTICES; i++) deck.post(notice("terminal", "k" + i, "n" + i));

        assertThat(deck.acknowledged("terminal", "first")).isFalse();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyNoticeTest' --tests 'dev.jasper.app.BuddyDeckTest'`

Expected: FAIL — `Kind` does not exist, `BuddyNotice` takes six components, `acknowledge`/`column` are undefined.

- [ ] **Step 3: Write the implementation**

Replace `BuddyNotice.java` entirely:

```java
package dev.jasper.app;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One thing worth showing. {@code key} is whatever produced it — a terminal pane today, an sftp
 * transfer or an SSH session later — and a later notice with the same source and key replaces this
 * one rather than stacking on top of it. Two producers cannot collide, because the source is part of
 * the identity.
 *
 * <p>{@code detail} is a supplier rather than a string so a running notice can tick without the deck
 * being re-posted every second, and so a future transfer can report bytes through the same field.
 *
 * <p>Identity is {@link #sameAs}, never {@code equals}: the record holds two lambdas, so its
 * generated {@code equals} compares them by reference and means nothing useful.
 */
record BuddyNotice(String source, Object key, Kind kind, String title, State state,
                   Supplier<String> detail, Runnable activate) {

    /** What sort of thing this is, which is what decides when it stops mattering. */
    enum Kind {
        /** Begins and ends: a command, a file transfer. */
        TASK,
        /** Up until it is not: an SSH session, a tunnel. It never "completes". */
        CONNECTION
    }

    enum State {
        RUNNING, NEEDS_INPUT, DONE, FAILED, UP, DEGRADED, DOWN;

        /** A task is never UP; a connection is never DONE. */
        boolean fits(Kind kind) {
            return kind == Kind.TASK
                ? this == RUNNING || this == NEEDS_INPUT || this == DONE || this == FAILED
                : this == UP || this == DEGRADED || this == DOWN;
        }
    }

    BuddyNotice {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A notice needs a non-blank title");
        if (!state.fits(kind)) throw new IllegalArgumentException(state + " is not a state a " + kind + " can be in");
    }

    /** Still happening, so it belongs above his head whether or not you have seen it. */
    boolean live() {
        return state == State.RUNNING || state == State.NEEDS_INPUT
            || state == State.UP || state == State.DEGRADED;
    }

    /** Wants you specifically: it ended, it broke, or it is waiting on you. */
    boolean wantsAttention() { return state != State.RUNNING && state != State.UP; }

    /**
     * Its origin is gone — the pane closed. Still worth reading, so this is a missing action rather
     * than another state: a pane closed after a successful build must still say it succeeded.
     */
    boolean orphaned() { return activate == null; }

    boolean sameAs(String otherSource, Object otherKey) {
        return source.equals(otherSource) && key.equals(otherKey);
    }
}
```

In `BuddyDeck.java`, add the acknowledgement set and the column query. Acknowledgement is keyed by a
small record so it cannot collide across sources, and it is cleaned up everywhere a notice leaves:

```java
import java.util.HashSet;
import java.util.Set;
```

```java
    /** Identity of a notice, so acknowledgements cannot collide across producers. */
    private record Id(String source, Object key) {}

    private final List<BuddyNotice> notices = new ArrayList<>();
    /** Notices the reader has already looked at. About the reader, not about the thing. */
    private final Set<Id> seen = new HashSet<>();
```

```java
    void post(BuddyNotice notice) {
        Objects.requireNonNull(notice, "notice");
        Id id = new Id(notice.source(), notice.key());
        notices.removeIf(existing -> existing.sameAs(notice.source(), notice.key()));
        // A new state is new news: a tunnel that drops after you acknowledged it must speak up again.
        seen.remove(id);
        notices.addFirst(notice);
        while (notices.size() > MAX_NOTICES) seen.remove(idOf(notices.removeLast()));
    }

    boolean dismiss(String source, Object key) {
        seen.remove(new Id(source, key));
        return notices.removeIf(notice -> notice.sameAs(source, key));
    }

    void clear() {
        notices.clear();
        seen.clear();
    }

    /** You looked at it. Only matters once it stops being live. */
    void acknowledge(String source, Object key) {
        if (notices.stream().anyMatch(notice -> notice.sameAs(source, key))) seen.add(new Id(source, key));
    }

    boolean acknowledged(String source, Object key) { return seen.contains(new Id(source, key)); }

    /** What belongs above his head: still happening, or not yet seen. Newest first. */
    List<BuddyNotice> column() {
        return notices.stream().filter(notice -> notice.live() || !seen.contains(idOf(notice))).toList();
    }

    private static Id idOf(BuddyNotice notice) { return new Id(notice.source(), notice.key()); }
```

Update `BuddyDeck.orphan(source, key)` and `orphan(source, key, finalDetail)` to carry `kind` through
when they rebuild the notice — both already copy every other component:

```java
            ? new BuddyNotice(notice.source(), notice.key(), notice.kind(), notice.title(),
                notice.state(), notice.detail(), null)
```

and, for the freezing form:

```java
            ? new BuddyNotice(notice.source(), notice.key(), notice.kind(), notice.title(),
                notice.state(), () -> finalDetail, null)
```

- [ ] **Step 4: Keep the module compiling, or no test can run at all**

Gradle compiles all of `main` before it runs any test, so a single broken call site means **zero**
tests execute — including this task's. `BuddyNotice` just gained a component, so every construction
site must be updated now, not in a later task:

- `CommandNotifier.started`: `new BuddyNotice(SOURCE, key, BuddyNotice.Kind.TASK, title, BuddyNotice.State.RUNNING, …)`
- `CommandNotifier.finished`: the same, with `State.DONE` / `State.FAILED`
- `BuddyDeck.orphan` (both forms): pass `notice.kind()` through, as shown above
- Any `new BuddyNotice(...)` in `BuddyDeckPanelTest` and `CommandNotifierTest`: insert `BuddyNotice.Kind.TASK` after the key

The old `State.ACTIVE` becomes `State.RUNNING` at every site. Nothing else changes yet.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyNoticeTest' --tests 'dev.jasper.app.BuddyDeckTest'`

Expected: PASS, with the whole module compiling.

- [ ] **Step 5: Prove the re-post rule has teeth**

Temporarily delete `seen.remove(id);` from `post` and re-run. `aNewStateOverAnAcknowledgedKeyAsksForAttentionAgain` must fail. Revert.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyNotice.java jasper-app/src/main/java/dev/jasper/app/BuddyDeck.java jasper-app/src/test/java/dev/jasper/app/BuddyNoticeTest.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckTest.java
git commit -m "$(printf 'feat: split notices into tasks and connections, and track what you have seen\n\nA tunnel never completes, so a model built only around completion cannot\nexpress one. Kind decides which states are legal and the constructor\nrefuses the rest.\n\nAcknowledgement lives on the deck rather than the notice because it is a\nfact about the reader, not about the thing that happened. Column\nmembership is then one line: still happening, or not yet seen.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 2: Extract the card painter

The column and the drawer draw the same card. Extracting it before either changes keeps one
definition of what a card looks like, and shrinks `BuddyDeckPanel` from 375 lines.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyCard.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyCardTest.java`

**Interfaces:**
- Consumes: `BuddyNotice`, `BuddyFonts`, `BuddyBubblePanel.radiusFor`, `BuddyDeckLayout.DISMISS_*`.
- Produces: `BuddyCard` with `static final int WIDTH = 300`, `static final int PAD_X/PAD_Y/LINE_GAP/GLYPH_SIZE/GLYPH_GAP`, `static int height(JComponent owner)`, `static void paint(Graphics2D g2, JComponent owner, BuddyNotice notice, Rectangle bounds, boolean highlighted, float scale, float opacity, boolean dismissable, int count)`, `static String detailOf(BuddyNotice)`, `static Font titleFont()`, `static Font detailFont()`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyCardTest.java`:

```java
package dev.jasper.app;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyCardTest {
    private final JComponent owner = new JComponent() {};

    private static BuddyNotice notice(BuddyNotice.State state, String detail) {
        return new BuddyNotice("terminal", "k", BuddyNotice.Kind.TASK, "./gradlew build", state,
            () -> detail, () -> { });
    }

    private void paint(BuddyNotice notice, boolean dismissable, int count) {
        Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
        BufferedImage image = new BufferedImage(bounds.width + 40, bounds.height + 40,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            BuddyCard.paint(g, owner, notice, bounds, false, 1f, 1f, dismissable, count);
        } finally { g.dispose(); }
    }

    @Test void aCardIsTallEnoughForBothItsLines() {
        int height = BuddyCard.height(owner);

        assertThat(height).isGreaterThan(2 * BuddyCard.PAD_Y + BuddyCard.GLYPH_SIZE);
        assertThat(height).isEqualTo(BuddyCard.height(owner));
    }

    @Test void everyStatePaintsWithoutBlowingUp() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            BuddyNotice.Kind kind = state.fits(BuddyNotice.Kind.TASK)
                ? BuddyNotice.Kind.TASK : BuddyNotice.Kind.CONNECTION;
            BuddyNotice any = new BuddyNotice("s", "k", kind, "title", state, () -> "detail", () -> { });
            Rectangle bounds = new Rectangle(0, 0, BuddyCard.WIDTH, BuddyCard.height(owner));
            BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { BuddyCard.paint(g, owner, any, bounds, true, 1.04f, 1f, true, 0); } finally { g.dispose(); }
        }
    }

    /** A producer's supplier is arbitrary code; a broken one must not take the surface down. */
    @Test void aDetailSupplierThatThrowsIsSurvived() {
        assertThat(BuddyCard.detailOf(new BuddyNotice("s", "k", BuddyNotice.Kind.TASK, "t",
            BuddyNotice.State.RUNNING, () -> { throw new IllegalStateException("boom"); }, () -> { })))
            .isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, null))).isEmpty();
        assertThat(BuddyCard.detailOf(notice(BuddyNotice.State.DONE, "Finished in 3s")))
            .isEqualTo("Finished in 3s");
    }

    @Test void paintingIsSafeWithADismissTargetAndWithACount() {
        paint(notice(BuddyNotice.State.DONE, "Finished in 3s"), true, 0);
        paint(notice(BuddyNotice.State.RUNNING, "Running · 3s"), false, 7);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyCardTest'`

Expected: FAIL — `BuddyCard` does not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyCard.java` by moving the painting out of
`BuddyDeckPanel` unchanged in behaviour, with the owner passed in for font metrics:

```java
package dev.jasper.app;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** One card, wherever it is drawn. The column and the drawer share this so they cannot drift apart. */
final class BuddyCard {
    static final int WIDTH = 300;
    static final int PAD_X = 14;
    static final int PAD_Y = 10;
    static final int LINE_GAP = 3;
    static final int GLYPH_SIZE = 12;
    static final int GLYPH_GAP = 10;
    private static final String ELLIPSIS = "…";
    private static final String DISMISS = "×";

    private static final Color FILL = new Color(30, 30, 32, 235);
    private static final Color FILL_HIGHLIGHTED = new Color(58, 58, 62, 242);
    private static final Color BORDER = new Color(255, 255, 255, 26);
    private static final Color TITLE_COLOR = Color.WHITE;
    private static final Color DETAIL_COLOR = new Color(160, 160, 166);
    private static final Color ORPHAN_TITLE_COLOR = new Color(255, 255, 255, 140);
    static final Color MUTED_COLOR = new Color(190, 190, 196);

    private BuddyCard() { }

    static Font titleFont() { return BuddyFonts.system(Font.BOLD, 14f); }

    static Font detailFont() { return BuddyFonts.system(Font.PLAIN, 12f); }

    static int height(JComponent owner) {
        FontMetrics title = owner.getFontMetrics(titleFont());
        FontMetrics detail = owner.getFontMetrics(detailFont());
        return Math.max(title.getHeight() + LINE_GAP + detail.getHeight(), GLYPH_SIZE) + 2 * PAD_Y;
    }

    /** A producer's supplier is arbitrary code; one that throws must not take the surface down. */
    static String detailOf(BuddyNotice notice) {
        try {
            String detail = notice.detail().get();
            return detail == null ? "" : detail;
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /**
     * {@code count}, when non-zero, is drawn as a remainder badge; {@code dismissable} adds the ×
     * that {@link BuddyDeckLayout#dismissTarget} hit-tests.
     */
    static void paint(Graphics2D g2, JComponent owner, BuddyNotice notice, Rectangle bounds,
                      boolean highlighted, float scale, float opacity, boolean dismissable, int count) {
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(1f, Math.max(0f, opacity))));
            // Grow about the card's centre-left, so it expands away from the buddy rather than over him.
            c.translate(bounds.x, bounds.y + bounds.height / 2f);
            c.scale(scale, scale);
            c.translate(0, -bounds.height / 2f);
            int arc = BuddyBubblePanel.radiusFor(bounds.height) * 2;
            c.setColor(highlighted ? FILL_HIGHLIGHTED : FILL);
            c.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc);
            c.setColor(BORDER);
            c.drawRoundRect(0, 0, bounds.width - 1, bounds.height - 1, arc, arc);

            c.setFont(detailFont());
            FontMetrics small = c.getFontMetrics();
            int right = bounds.width - PAD_X;
            if (dismissable) {
                c.setColor(highlighted ? MUTED_COLOR : DETAIL_COLOR);
                c.drawString(DISMISS, bounds.width - BuddyDeckLayout.DISMISS_INSET
                        - BuddyDeckLayout.DISMISS_SIZE + 2,
                    BuddyDeckLayout.DISMISS_INSET + small.getAscent());
                right = bounds.width - BuddyDeckLayout.DISMISS_INSET - BuddyDeckLayout.DISMISS_SIZE - 4;
            } else if (count > 0) {
                String badge = Integer.toString(count);
                c.setColor(MUTED_COLOR);
                c.drawString(badge, bounds.width - PAD_X - small.stringWidth(badge),
                    bounds.height - PAD_Y - small.getHeight() + small.getAscent());
                right = bounds.width - PAD_X - small.stringWidth(badge) - GLYPH_GAP;
            }
            if (notice.wantsAttention()) {
                paintGlyph(c, notice.state(), right - GLYPH_SIZE, (bounds.height - GLYPH_SIZE) / 2);
                right -= GLYPH_SIZE + GLYPH_GAP;
            }

            int available = Math.max(0, right - PAD_X);
            FontMetrics title = c.getFontMetrics(titleFont());
            int textHeight = title.getHeight() + LINE_GAP + small.getHeight();
            int y = (bounds.height - textHeight) / 2;
            c.setFont(titleFont());
            c.setColor(notice.orphaned() ? ORPHAN_TITLE_COLOR : TITLE_COLOR);
            c.drawString(fit(title, notice.title(), available), PAD_X, y + title.getAscent());
            c.setFont(detailFont());
            c.setColor(DETAIL_COLOR);
            c.drawString(fit(small, detailOf(notice), available),
                PAD_X, y + title.getHeight() + LINE_GAP + small.getAscent());
        } finally { c.dispose(); }
    }

    /**
     * A check for a good ending, a cross for a bad one, a dot for anything still unresolved —
     * waiting on you, or a connection that is not healthy.
     */
    private static void paintGlyph(Graphics2D g2, BuddyNotice.State state, int x, int y) {
        boolean good = state == BuddyNotice.State.DONE;
        boolean bad = state == BuddyNotice.State.FAILED || state == BuddyNotice.State.DOWN;
        Color configured = UIManager.getColor(
            good ? "Jasper.configSuccessForeground" : "Jasper.configErrorForeground");
        Color ink = bad || good
            ? (configured != null ? configured
                : good ? new Color(0x5A, 0xB0, 0x7A) : new Color(0xC4, 0x5A, 0x53))
            : MUTED_COLOR;
        Graphics2D c = (Graphics2D) g2.create();
        try {
            c.setColor(ink);
            c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (good) {
                c.drawLine(x + 2, y + 6, x + 5, y + 9);
                c.drawLine(x + 5, y + 9, x + 10, y + 3);
            } else if (bad) {
                c.drawLine(x + 3, y + 3, x + 9, y + 9);
                c.drawLine(x + 9, y + 3, x + 3, y + 9);
            } else {
                c.fillOval(x + 3, y + 3, GLYPH_SIZE - 6, GLYPH_SIZE - 6);
            }
        } finally { c.dispose(); }
    }

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

In `BuddyDeckPanel.java`: delete `paintCard`, `paintGlyph`, `text`, `fit`, `titleFont`, `detailFont`,
the colour constants now on `BuddyCard`, and the `CARD_WIDTH`/`PAD_*`/`GLYPH_*`/`ELLIPSIS`/`DISMISS`
constants. Replace `CARD_WIDTH` uses with `BuddyCard.WIDTH`, `cardHeight()` with
`BuddyCard.height(this)`, and each `paintCard(...)` call with the matching `BuddyCard.paint(g2, this, ...)`.
Keep `MOTION_MARGIN`, `WHEEL_STEP`, `CLEAR_ALL`, the input handlers and the expanded painting. Keep a
package-private `static final int CARD_WIDTH = BuddyCard.WIDTH;` alias only if the tests reference it;
otherwise update the tests.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyCardTest' --tests 'dev.jasper.app.BuddyDeckPanelTest'`

Expected: PASS. `BuddyDeckPanelTest` is a pure refactor check here — if any of its assertions change
meaning, the extraction was not behaviour-preserving and must be corrected rather than the test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyCard.java jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java jasper-app/src/test/java/dev/jasper/app/BuddyCardTest.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java
git commit -m "$(printf 'refactor: extract the card painter so two surfaces can share it\n\nThe column and the drawer draw the same card. One definition, so they\ncannot drift. The glyph gains a neutral dot for the states that are\nneither a good nor a bad ending - waiting on you, or a connection that is\nnot healthy.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 3: Column geometry

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: on `BuddyDeckLayout` — `static final int COLUMN_GAP = 6`, `MAX_IN_COLUMN = 3`, `TAIL_HEIGHT = 16`, `TAIL_BIG = 7`, `TAIL_SMALL = 4`; and
  `static int visibleInColumn(int count)`,
  `static int columnHeight(int count, int cardHeight)`,
  `static Rectangle column(int index, int count, int width, int cardHeight, boolean below)`,
  `static Rectangle[] tail(int width, int columnHeight, boolean below)`.
  `collapsed`, `collapsedHeight`, `PEEK_Y`, `PEEK_INSET` and `MAX_PEEKED` stay for now — `BuddyDeckPanel` still calls them and deleting them here would stop the module compiling, so they go in Task 5 when the last caller does.

- [ ] **Step 1: Write the failing test**

In `BuddyDeckLayoutTest.java`, **leave the existing collapsed-stack tests alone** — that code is still
live until Task 5, and untested live code is worse than dead code. Add alongside them:

```java
    @Test void atMostThreeBubblesAreDrawnAndTheRestBecomeACount() {
        assertThat(BuddyDeckLayout.visibleInColumn(0)).isZero();
        assertThat(BuddyDeckLayout.visibleInColumn(2)).isEqualTo(2);
        assertThat(BuddyDeckLayout.visibleInColumn(9)).isEqualTo(BuddyDeckLayout.MAX_IN_COLUMN);
    }

    @Test void theColumnIsItsBubblesPlusTheTail() {
        assertThat(BuddyDeckLayout.columnHeight(0, CARD)).isZero();
        assertThat(BuddyDeckLayout.columnHeight(1, CARD)).isEqualTo(CARD + BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(BuddyDeckLayout.columnHeight(3, CARD)).isEqualTo(
            3 * CARD + 2 * BuddyDeckLayout.COLUMN_GAP + BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(BuddyDeckLayout.columnHeight(20, CARD))
            .as("past the cap the column stops growing").isEqualTo(BuddyDeckLayout.columnHeight(3, CARD));
    }

    /** Above him, the newest sits at the bottom of the column: nearest his head. */
    @Test void aboveHimTheNewestIsLowestAndOlderOnesRiseAwayFromHim() {
        Rectangle newest = BuddyDeckLayout.column(0, 3, WIDTH, CARD, false);
        Rectangle middle = BuddyDeckLayout.column(1, 3, WIDTH, CARD, false);
        Rectangle oldest = BuddyDeckLayout.column(2, 3, WIDTH, CARD, false);

        assertThat(newest.y).isGreaterThan(middle.y);
        assertThat(middle.y).isGreaterThan(oldest.y);
        assertThat(oldest.y).isZero();
        assertThat(newest.y + newest.height)
            .isEqualTo(BuddyDeckLayout.columnHeight(3, CARD) - BuddyDeckLayout.TAIL_HEIGHT);
        assertThat(newest.x).isZero();
        assertThat(newest.width).isEqualTo(WIDTH);
    }

    /** Flipped below him the order inverts, so the newest is still the one nearest his head. */
    @Test void belowHimTheNewestIsHighestSoItStaysNearestHisHead() {
        Rectangle newest = BuddyDeckLayout.column(0, 3, WIDTH, CARD, true);
        Rectangle oldest = BuddyDeckLayout.column(2, 3, WIDTH, CARD, true);

        assertThat(newest.y).isLessThan(oldest.y);
        assertThat(newest.y).isEqualTo(BuddyDeckLayout.TAIL_HEIGHT);
    }

    @Test void everyBubbleSitsInsideTheColumn() {
        for (boolean below : new boolean[] {false, true}) {
            int height = BuddyDeckLayout.columnHeight(3, CARD);
            for (int index = 0; index < 3; index++) {
                Rectangle card = BuddyDeckLayout.column(index, 3, WIDTH, CARD, below);
                assertThat(card.y).as("below=%s index=%d", below, index).isNotNegative();
                assertThat(card.y + card.height).isLessThanOrEqualTo(height);
            }
        }
    }

    /** Without the tail it is a floating list, not a thought. */
    @Test void theTailRunsFromHisHeadTowardsTheNearestBubble() {
        int height = BuddyDeckLayout.columnHeight(2, CARD);
        Rectangle[] above = BuddyDeckLayout.tail(WIDTH, height, false);

        assertThat(above).hasSize(2);
        assertThat(above[0].width).isGreaterThan(above[1].width);
        assertThat(above[1].y).as("the small one is nearest him, at the bottom")
            .isGreaterThan(above[0].y);
        for (Rectangle circle : above) {
            assertThat(circle.y).isGreaterThanOrEqualTo(height - BuddyDeckLayout.TAIL_HEIGHT);
            assertThat(circle.y + circle.height).isLessThanOrEqualTo(height);
            assertThat(circle.x).isGreaterThan(0);
        }
    }

    @Test void flippedBelowHimTheTailIsAtTheTopAndStillPointsAtHim() {
        int height = BuddyDeckLayout.columnHeight(2, CARD);
        Rectangle[] below = BuddyDeckLayout.tail(WIDTH, height, true);

        assertThat(below[1].y).as("the small one is nearest him, at the top")
            .isLessThan(below[0].y);
        for (Rectangle circle : below) {
            assertThat(circle.y).isNotNegative();
            assertThat(circle.y + circle.height).isLessThanOrEqualTo(BuddyDeckLayout.TAIL_HEIGHT);
        }
    }

    @Test void aPointInAColumnBubbleNamesIt() {
        assertThat(BuddyDeckLayout.columnAt(
            BuddyDeckLayout.column(1, 3, WIDTH, CARD, false).y + 4, 3, CARD, false)).isEqualTo(1);
        assertThat(BuddyDeckLayout.columnAt(
            BuddyDeckLayout.columnHeight(3, CARD) - 2, 3, CARD, false)).as("in the tail").isEqualTo(-1);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckLayoutTest'`

Expected: FAIL — `visibleInColumn`, `columnHeight`, `column`, `tail`, `columnAt` are undefined.

- [ ] **Step 3: Write the implementation**

In `BuddyDeckLayout.java`, add the column geometry beside the existing collapsed geometry, which stays
until its last caller goes in Task 5:

```java
    static final int COLUMN_GAP = 6;
    /** Beyond this the newest card carries a count; a taller column would cover the screen. */
    static final int MAX_IN_COLUMN = 3;
    /** The band between his head and the nearest bubble, where the thought tail sits. */
    static final int TAIL_HEIGHT = 16;
    static final int TAIL_BIG = 7;
    static final int TAIL_SMALL = 4;
```

```java
    static int visibleInColumn(int count) { return Math.min(Math.max(count, 0), MAX_IN_COLUMN); }

    /** The bubbles, the gaps between them, and the band the tail lives in. */
    static int columnHeight(int count, int cardHeight) {
        int visible = visibleInColumn(count);
        if (visible == 0) return 0;
        return visible * cardHeight + (visible - 1) * COLUMN_GAP + TAIL_HEIGHT;
    }

    /**
     * Bubble {@code index} counting from the newest. The newest is always the one nearest his head:
     * lowest when the column is above him, highest when it has been flipped below.
     */
    static Rectangle column(int index, int count, int width, int cardHeight, boolean below) {
        int visible = visibleInColumn(count);
        int pitch = cardHeight + COLUMN_GAP;
        int y = below
            ? TAIL_HEIGHT + index * pitch
            : (visible - 1 - index) * pitch;
        return new Rectangle(0, y, width, cardHeight);
    }

    /**
     * Two circles tapering from his head towards the nearest bubble, largest first. Without them the
     * column is a floating list rather than something he is thinking.
     */
    static Rectangle[] tail(int width, int columnHeight, boolean below) {
        int centre = width / 2;
        int band = below ? 0 : columnHeight - TAIL_HEIGHT;
        int bigY = below ? band + TAIL_HEIGHT - TAIL_BIG - 2 : band + 2;
        int smallY = below ? band + 2 : band + TAIL_HEIGHT - TAIL_SMALL - 2;
        return new Rectangle[] {
            new Rectangle(centre - TAIL_BIG / 2, bigY, TAIL_BIG, TAIL_BIG),
            new Rectangle(centre - TAIL_SMALL / 2 + TAIL_BIG, smallY, TAIL_SMALL, TAIL_SMALL)
        };
    }

    /** The bubble at {@code y} in the column, or -1 in a gap or the tail band. */
    static int columnAt(int y, int count, int cardHeight, boolean below) {
        int visible = visibleInColumn(count);
        for (int index = 0; index < visible; index++) {
            Rectangle card = column(index, count, 1, cardHeight, below);
            if (y >= card.y && y < card.y + card.height) return index;
        }
        return -1;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyDeckLayoutTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java
git commit -m "$(printf 'feat: lay out the thought column above his head\n\nThe newest bubble is always the one nearest him, which means the order\ninverts when the column flips below him rather than the whole thing\nreading upside down. The peek offsets of the old stack beside him are\ngone.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 4: The column panel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyColumnPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyColumnPanelTest.java`

**Interfaces:**
- Consumes: `BuddyDeck`, `BuddyCard`, `BuddyDeckLayout`, `BubbleMotion`.
- Produces: `BuddyColumnPanel(BuddyDeck deck, Runnable onLayoutChanged, Runnable onOpenDrawer)` with `setClock(LongSupplier)`, `setBelow(boolean)`, `boolean below()`, `void arrived()`, `void handleMove(Point)`, `void handleExit()`, `boolean handleClick(Point)`, `int hovered()`, `float topScale()`, `Dimension getPreferredSize()`, and `static final int MARGIN`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyColumnPanelTest.java`:

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

class BuddyColumnPanelTest {
    private final BuddyDeck deck = new BuddyDeck();
    private final List<String> activated = new ArrayList<>();
    private int drawerOpened;
    private long now;
    private final BuddyColumnPanel panel = create();

    private BuddyColumnPanel create() {
        BuddyColumnPanel made = new BuddyColumnPanel(deck, () -> { }, () -> drawerOpened++);
        made.setClock(() -> now);
        return made;
    }

    private void post(String key, String title, BuddyNotice.State state) {
        deck.post(new BuddyNotice("terminal", key, BuddyNotice.Kind.TASK, title, state,
            () -> "detail", () -> activated.add(title)));
        panel.arrived();
    }

    private void layout() { panel.setSize(panel.getPreferredSize()); }

    private Point inBubble(int index) {
        int count = deck.column().size();
        Rectangle card = BuddyDeckLayout.column(index, count, BuddyCard.WIDTH,
            BuddyCard.height(panel), panel.below());
        return new Point(BuddyColumnPanel.MARGIN + 30, BuddyColumnPanel.MARGIN + card.y + 4);
    }

    @Test void anEmptyColumnAsksForNoRoomAtAll() {
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
    }

    /** The resting state: he has nothing to think about, so there is nothing above his head. */
    @Test void aColumnOfOnlyAcknowledgedFinishedThingsIsEmpty() {
        post("a", "one", BuddyNotice.State.DONE);
        deck.acknowledge("terminal", "a");
        layout();

        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(0, 0));
        assertThat(deck.notices()).as("still in the drawer").hasSize(1);
    }

    @Test void theColumnIsAsTallAsItsBubblesAndTail() {
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
        layout();

        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.columnHeight(2, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN);
    }

    @Test void pastThreeTheColumnStopsGrowingAndTheNewestCarriesTheCount() {
        for (int i = 0; i < 9; i++) post("k" + i, "card " + i, BuddyNotice.State.RUNNING);
        layout();

        assertThat(panel.getPreferredSize().height).isEqualTo(
            BuddyDeckLayout.columnHeight(3, BuddyCard.height(panel)) + 2 * BuddyColumnPanel.MARGIN);
        assertThat(paintDoesNotThrow()).isTrue();
    }

    @Test void clickingABubbleRunsItsActionAndSaysTheColumnShouldGo() {
        post("a", "one", BuddyNotice.State.DONE);
        post("b", "two", BuddyNotice.State.DONE);
        layout();

        assertThat(panel.handleClick(inBubble(0))).isFalse();

        assertThat(activated).containsExactly("two");
        assertThat(drawerOpened).isZero();
    }

    @Test void clickingAnOrphanDoesNothingAtAll() {
        post("a", "one", BuddyNotice.State.DONE);
        deck.orphan("terminal", "a");
        layout();

        assertThat(panel.handleClick(inBubble(0))).isTrue();

        assertThat(activated).isEmpty();
        assertThat(drawerOpened).isZero();
    }

    /** The gap and the tail are not bubbles; a click there opens the drawer instead. */
    @Test void clickingTheTailOpensTheDrawer() {
        post("a", "one", BuddyNotice.State.RUNNING);
        layout();
        int height = BuddyDeckLayout.columnHeight(1, BuddyCard.height(panel));

        panel.handleClick(new Point(BuddyColumnPanel.MARGIN + 30, BuddyColumnPanel.MARGIN + height - 3));

        assertThat(drawerOpened).isEqualTo(1);
        assertThat(activated).isEmpty();
    }

    @Test void thePointerPicksOutOneBubbleAndLeavingPicksNone() {
        post("a", "one", BuddyNotice.State.RUNNING);
        post("b", "two", BuddyNotice.State.RUNNING);
        layout();

        panel.handleMove(inBubble(1));
        assertThat(panel.hovered()).isEqualTo(1);

        panel.handleExit();
        assertThat(panel.hovered()).isEqualTo(-1);
    }

    @Test void flippingBelowHimReordersWhichBubbleIsWhere() {
        post("a", "older", BuddyNotice.State.RUNNING);
        post("b", "newer", BuddyNotice.State.RUNNING);
        layout();
        Point newestAbove = inBubble(0);

        panel.setBelow(true);
        layout();

        assertThat(panel.below()).isTrue();
        assertThat(inBubble(0)).as("the newest moved to the other end").isNotEqualTo(newestAbove);
        assertThat(panel.handleClick(inBubble(0))).isFalse();
        assertThat(activated).containsExactly("newer");
    }

    @Test void aNewNoticeRestartsTheArrivalBounce() {
        post("a", "one", BuddyNotice.State.RUNNING);
        now = BubbleMotion.IN_NANOS * 4;
        assertThat(panel.topScale()).isEqualTo(1f);

        post("b", "two", BuddyNotice.State.RUNNING);

        assertThat(panel.topScale()).isEqualTo(BubbleMotion.IN_FROM);
    }

    @Test void everyArrangementPaintsWithoutBlowingUp() {
        post("a", "running", BuddyNotice.State.RUNNING);
        post("b", "waiting", BuddyNotice.State.NEEDS_INPUT);
        post("c", "failed", BuddyNotice.State.FAILED);
        layout();
        assertThat(paintDoesNotThrow()).isTrue();

        panel.handleMove(inBubble(1));
        assertThat(paintDoesNotThrow()).isTrue();

        panel.setBelow(true);
        layout();
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

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyColumnPanelTest'`

Expected: FAIL — `BuddyColumnPanel` does not exist.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyColumnPanel.java`:

```java
package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;

/**
 * What the buddy is thinking about: the live and unseen notices, stacked above his head with a
 * thought tail pointing back at him. Empty and invisible whenever nothing is happening, which is
 * most of the time and is the point — a permanent stack of finished commands is noise.
 *
 * <p>Input arrives through the package-private handlers, the way {@code TerminalView} takes keys, so
 * the tests drive it without a window or a pointer.
 */
final class BuddyColumnPanel extends JComponent {
    /** Slack so a bubble at its largest scale is not clipped by its own window. */
    static final int MARGIN = 10;
    private static final Color TAIL = new Color(30, 30, 32, 235);
    private static final Color TAIL_BORDER = new Color(255, 255, 255, 26);

    private final BuddyDeck deck;
    private final Runnable onLayoutChanged;
    private final Runnable onOpenDrawer;
    private LongSupplier clock = System::nanoTime;
    private boolean below;
    private int hovered = -1;
    private int leaving = -1;
    private long topArrivedAt;
    private long hoverChangedAt;

    BuddyColumnPanel(BuddyDeck deck, Runnable onLayoutChanged, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onLayoutChanged = Objects.requireNonNull(onLayoutChanged, "onLayoutChanged");
        this.onOpenDrawer = Objects.requireNonNull(onOpenDrawer, "onOpenDrawer");
        setOpaque(false);
    }

    void setClock(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** Flipped when he is too near the top of the screen for the column to fit above him. */
    void setBelow(boolean below) {
        if (this.below == below) return;
        this.below = below;
        hovered = -1;
        leaving = -1;
        repaint();
    }

    boolean below() { return below; }

    /** A notice was posted: the nearest bubble bounces in and the window re-sizes around the column. */
    void arrived() {
        topArrivedAt = clock.getAsLong();
        revalidate();
        repaint();
        onLayoutChanged.run();
    }

    int hovered() { return hovered; }

    float topScale() { return BubbleMotion.inScale(clock.getAsLong() - topArrivedAt); }

    @Override public Dimension getPreferredSize() {
        int count = deck.column().size();
        if (count == 0) return new Dimension(0, 0);
        return new Dimension(BuddyCard.WIDTH + 2 * MARGIN,
            BuddyDeckLayout.columnHeight(count, BuddyCard.height(this)) + 2 * MARGIN);
    }

    // --- input -------------------------------------------------------------------------------

    void handleMove(Point point) {
        int index = indexAt(point);
        if (index == hovered) return;
        leaving = hovered;
        hovered = index;
        hoverChangedAt = clock.getAsLong();
        repaint();
    }

    void handleExit() {
        if (hovered < 0) return;
        leaving = hovered;
        hovered = -1;
        hoverChangedAt = clock.getAsLong();
        repaint();
    }

    /**
     * Returns false when the column should now go away, because a bubble was activated and the pane
     * it points at is about to come forward. A click that is not on a bubble opens the drawer.
     */
    boolean handleClick(Point point) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return true;
        int index = indexAt(point);
        if (index < 0) {
            onOpenDrawer.run();
            return true;
        }
        BuddyNotice notice = column.get(index);
        // An orphan has nowhere to go, so a click on it is not a miss - it is simply nothing.
        if (notice.orphaned()) return true;
        notice.activate().run();
        return false;
    }

    private int indexAt(Point point) {
        int count = deck.column().size();
        if (count == 0) return -1;
        Point local = new Point(point.x - MARGIN, point.y - MARGIN);
        if (local.x < 0 || local.x > BuddyCard.WIDTH) return -1;
        return BuddyDeckLayout.columnAt(local.y, count, BuddyCard.height(this), below);
    }

    // --- painting ----------------------------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        List<BuddyNotice> column = deck.column();
        if (column.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(MARGIN, MARGIN);
            int cardHeight = BuddyCard.height(this);
            int height = BuddyDeckLayout.columnHeight(column.size(), cardHeight);
            paintTail(g2, height);
            int visible = BuddyDeckLayout.visibleInColumn(column.size());
            // Furthest first, so the newest lands on top of anything that overlaps it.
            for (int index = visible - 1; index >= 0; index--) {
                Rectangle card = BuddyDeckLayout.column(index, column.size(), BuddyCard.WIDTH, cardHeight, below);
                boolean nearest = index == 0;
                float scale = (nearest ? topScale() : 1f) * hoverScale(index);
                float opacity = nearest ? BubbleMotion.inOpacity(clock.getAsLong() - topArrivedAt) : 1f;
                int count = nearest && column.size() > visible ? column.size() : 0;
                BuddyCard.paint(g2, this, column.get(index), card, hovered == index, scale, opacity,
                    false, count);
            }
        } finally { g2.dispose(); }
    }

    private void paintTail(Graphics2D g2, int columnHeight) {
        for (Rectangle circle : BuddyDeckLayout.tail(BuddyCard.WIDTH, columnHeight, below)) {
            g2.setColor(TAIL);
            g2.fillOval(circle.x, circle.y, circle.width, circle.height);
            g2.setColor(TAIL_BORDER);
            g2.drawOval(circle.x, circle.y, circle.width - 1, circle.height - 1);
        }
    }

    /** Only the hovered bubble grows, and only the one just left animates back. */
    private float hoverScale(int index) {
        long elapsed = clock.getAsLong() - hoverChangedAt;
        if (index == hovered) return BubbleMotion.hoverScale(elapsed, true);
        if (index == leaving) return BubbleMotion.hoverScale(elapsed, false);
        return 1f;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyColumnPanelTest'`

Expected: PASS (12 tests).

- [ ] **Step 5: Render it and look at it**

Tests cannot tell you a column looks like a thought. Write a temporary JUnit class under
`jasper-app/src/test/java/dev/jasper/app/` that paints the column at scale 2 onto a dark background
into the session scratchpad — above and below, with three bubbles and one hovered — run it, **open the
PNG and look**, then delete the class. Earlier today exactly this caught cards bleeding through each
other while every assertion passed. Check in particular that the tail reads as connected to where his
head would be, and that the count badge is legible.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyColumnPanel.java jasper-app/src/test/java/dev/jasper/app/BuddyColumnPanelTest.java
git commit -m "$(printf 'feat: give the buddy a thought column above his head\n\nOnly what is live or unseen, so it is empty and invisible whenever nothing\nis happening - which is the point, and what the stack beside him could\nnever be. A click on a bubble goes to its pane; a click anywhere else in\nthe column opens the drawer.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 5: Two windows over one deck

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyColumnWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyDeckLayout.java` (delete the collapsed geometry)
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckLayoutTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyColumnWindowTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java`

**Interfaces:**
- Consumes: `BuddyColumnPanel`, `BuddyDeck`, `BuddyBubblePlacement.above`.
- Produces:
  - `BuddyColumnWindow(BuddyDeck deck, Runnable onOpenDrawer)` with `showBeside(Rectangle anchor)`, `refresh()`, `hide()`, `dispose()`, `isShowing()`, and `static boolean fitsAbove(Rectangle anchor, int columnHeight, Rectangle screen)`.
  - `BuddyDeckPanel` is now drawer-only: its `expanded` flag and `handleClick`-to-expand are gone; it always paints the list.
  - `BuddyWindow.attachDeck(BuddyDeck deck, Runnable onOpenDrawer)` unchanged in name, plus a single click opening the drawer.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyColumnWindowTest.java`:

```java
package dev.jasper.app;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window is a JWindow and the test JVM is headless, so only its decisions are testable here.
 * The shell around them is covered by the desktop checks handed to the user.
 */
class BuddyColumnWindowTest {
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1512, 944);

    @Test void thereIsRoomAboveHimInTheMiddleOfTheScreen() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 600, 84, 96), 220, SCREEN)).isTrue();
    }

    @Test void aBuddyNearTheTopHasToFlipBelow() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 40, 84, 96), 220, SCREEN)).isFalse();
    }

    /** Exactly enough room is still room; the boundary must not flip for a single pixel. */
    @Test void theBoundaryIsInclusive() {
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 220, 84, 96), 220, SCREEN)).isTrue();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(400, 219, 84, 96), 220, SCREEN)).isFalse();
    }

    @Test void aScreenWithAnOffsetOriginIsAccountedFor() {
        Rectangle second = new Rectangle(1512, 300, 1000, 800);
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 320, 84, 96), 220, second)).isFalse();
        assertThat(BuddyColumnWindow.fitsAbove(new Rectangle(1600, 700, 84, 96), 220, second)).isTrue();
    }
}
```

In `BuddyDeckPanelTest.java`, the drawer no longer expands: delete `expand()` and the tests that
assert collapsed behaviour (`theCollapsedDeckIsAsTallAsItsStackPlusRoomToGrow`,
`clickingTheCollapsedDeckExpandsItInsteadOfActivatingTheTopCard`,
`whatIsBehindTheTopCardNeverShowsThroughIt`, `aNewNoticeRestartsTheArrivalBounce`), and remove the
`expand();` call from every remaining test — the list is the only thing the drawer draws now. Replace
`BuddyDeckPanel.CARD_WIDTH` with `BuddyCard.WIDTH` and `panel.cardHeight()` with
`BuddyCard.height(panel)` throughout.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyColumnWindowTest' --tests 'dev.jasper.app.BuddyDeckPanelTest'`

Expected: FAIL — `BuddyColumnWindow` does not exist; `BuddyDeckPanel` still has `expanded`.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/BuddyColumnWindow.java`:

```java
package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/** The window the thought column lives in: translucent, always on top, never focused. */
final class BuddyColumnWindow {
    private final JWindow window = new JWindow();
    private final BuddyDeck deck;
    private final BuddyColumnPanel panel;
    private Rectangle anchor;
    private boolean disposed;

    BuddyColumnWindow(BuddyDeck deck, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyColumnPanel(deck, this::layout, Objects.requireNonNull(onOpenDrawer, "onOpenDrawer"));
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(panel);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { panel.handleMove(event.getPoint()); }
            @Override public void mouseExited(MouseEvent event) { panel.handleExit(); }
            // A macOS control-click is the popup trigger yet reports the left button; it must not activate.
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown()) return;
                if (!panel.handleClick(event.getPoint())) hide();
            }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);
    }

    /** Whether the column fits between the top of the screen and the top of his head. */
    static boolean fitsAbove(Rectangle anchor, int columnHeight, Rectangle screen) {
        return anchor.y - columnHeight >= screen.y;
    }

    void showBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        layout();
    }

    void refresh() { layout(); }

    void hide() {
        if (disposed) return;
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
        if (deck.column().isEmpty()) { window.setVisible(false); return; }
        Rectangle screen = screenFor(anchor);
        // Decide the direction before measuring: the panel lays its bubbles out the other way round.
        Dimension size = panel.getPreferredSize();
        panel.setBelow(!fitsAbove(anchor, size.height, screen));
        size = panel.getPreferredSize();
        if (size.width <= 0 || size.height <= 0) { window.setVisible(false); return; }
        window.setSize(size);
        window.setLocation(place(size, screen));
        window.setVisible(true);
    }

    /** Centred on him, above when there is room and below when there is not. */
    private Point place(Dimension size, Rectangle screen) {
        if (!panel.below()) return BuddyBubblePlacement.above(anchor, size, screen);
        int x = anchor.x + (anchor.width - size.width) / 2;
        int y = anchor.y + anchor.height + BuddyBubblePlacement.ABOVE_GAP;
        int clampedX = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));
        int clampedY = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));
        return new Point(clampedX, clampedY);
    }

    private static Rectangle screenFor(Rectangle anchor) {
        if (GraphicsEnvironment.isHeadless()) return new Rectangle(0, 0, 1280, 800);
        List<Rectangle> screens = BuddyWindow.usableScreens();
        if (screens.isEmpty()) return new Rectangle(0, 0, 1280, 800);
        for (Rectangle screen : screens) {
            if (screen.contains((int) anchor.getCenterX(), (int) anchor.getCenterY())) return screen;
        }
        return screens.getFirst();
    }
}
```

In `BuddyDeckLayout.java`, now that nothing calls them, delete `collapsed`, `collapsedHeight`,
`PEEK_Y`, `PEEK_INSET` and `MAX_PEEKED`, and delete their three tests from `BuddyDeckLayoutTest`
(`theNewestCardIsDrawnInFullAndTheOnesBehindItPeek`, `theStackStopsGettingDeeperAfterThreeCards`,
`theCollapsedHeightIsTheTopCardPlusWhatPeeksBelowIt`).

In `BuddyDeckPanel.java`, make it drawer-only:

- Delete the `expanded` field, `expanded()`, and the `if (!expanded) { … }` branch at the top of
  `handleClick` — the drawer always shows its list.
- Delete `paintCollapsed`, `paintBacking`, `arrived()` and `topArrivedAt`; the drawer does not bounce.
- `collapse()` becomes `reset()`: clears scroll and hover, used when the drawer is hidden.
- `paintComponent` calls `paintExpanded` unconditionally.
- `getPreferredSize` always uses `BuddyDeckLayout.expandedHeight(...)` capped by `maxListHeight`.
- `handleExit` clears the hover and calls `onLayoutChanged` — hiding the window is the *window's*
  decision now, taken in `BuddyDeckWindow`'s `mouseExited`.

In `BuddyDeckWindow.java`, hide on pointer exit, since the panel no longer collapses:

```java
            @Override public void mouseExited(MouseEvent event) {
                if (panel.contains(event.getPoint())) return;
                panel.handleExit();
                hide();
            }
```

and `hide()` calls `panel.reset()` in place of `panel.collapse()`.

In `BuddyWindow.java`:

```java
    private BuddyDeckWindow drawer;
    private BuddyColumnWindow column;
```

```java
    /** The application owns the contents; the buddy only gives the two surfaces somewhere to sit. */
    void attachDeck(BuddyDeck deck) {
        if (disposed || drawer != null) return;
        drawer = new BuddyDeckWindow(deck);
        column = new BuddyColumnWindow(deck, this::openDrawer);
        refreshDeck();
    }

    /** A notice was posted, dismissed or cleared: re-place both surfaces around him. */
    void refreshDeck() {
        if (column == null) return;
        if (window.isVisible()) {
            column.showBeside(window.getBounds());
            if (drawer.isShowing()) drawer.showBeside(window.getBounds());
        } else {
            column.hide();
            drawer.hide();
        }
    }

    /** Single click: double-click already raises the terminal and right-click opens his menu. */
    private void openDrawer() {
        if (drawer == null || !window.isVisible()) return;
        drawer.showBeside(window.getBounds());
    }
```

In the mouse adapter's `mouseClicked`, add the single-click case beside the existing double-click:

```java
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown() || dragged) return;
                if (event.getClickCount() == 2) raiseTerminal.run();
                else if (event.getClickCount() == 1) openDrawer();
            }
```

Keep the existing guards exactly as they are; only the click-count branch is new. Add
`if (column != null) column.hide();` to `hide()` and `if (column != null) column.dispose();` to
`dispose()`, beside the drawer's.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.Buddy*'`

Expected: PASS. `CommandNotifier` will not compile until Task 6.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyColumnWindow.java jasper-app/src/main/java/dev/jasper/app/BuddyDeckPanel.java jasper-app/src/main/java/dev/jasper/app/BuddyDeckWindow.java jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java jasper-app/src/test/java/dev/jasper/app/BuddyColumnWindowTest.java jasper-app/src/test/java/dev/jasper/app/BuddyDeckPanelTest.java
git commit -m "$(printf 'feat: hang the column above him and the drawer behind him\n\nTwo windows over one deck: one surface cannot be in two places, and these\nare now genuinely two surfaces with different memberships. The column\nflips below him when he is too near the top of the screen, and the bubble\norder inverts with it so the newest stays nearest his head.\n\nSingle-clicking him opens the drawer. Double-click already raised the\nterminal and right-click already opened his menu, so nothing moves.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 6: Routing and acknowledgement

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/CommandNotifier.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java`

**Interfaces:**
- Consumes: `BuddyDeck.acknowledge`, `BuddyNotice.Kind.TASK`.
- Produces: `CommandNotifier.looked(Object key)`; `finished` acknowledges when `origin.ownPaneFocused()`; `closed` acknowledges as well as orphans. `WindowContent.onPaneFocused` — a `Consumer<Object>`.

- [ ] **Step 1: Write the failing test**

Append to `CommandNotifierTest.java`:

```java
    /** You were watching it happen; he does not need to tell you about it. */
    @Test void aCommandThatEndsUnderYourEyesNeverReachesTheColumn() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {});
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            FOCUSED_PANE, () -> {});

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).as("but it is still in the drawer").hasSize(1);
    }

    @Test void aCommandThatEndsOutOfSightWaitsInTheColumnUntilYouLook() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {});
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> {});
        assertThat(deck.column()).hasSize(1);

        notifier.looked("pane");

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).hasSize(1);
    }

    @Test void lookingAtOnePaneLeavesEveryOtherPanesNoticeAlone() {
        CommandNotifier notifier = notifier(10);
        for (String key : List.of("a", "b")) {
            notifier.started(key, "cmd " + key, () -> 0L, () -> {});
        }
        passThreshold();
        for (String key : List.of("a", "b")) {
            notifier.finished(key, "cmd " + key, OptionalInt.of(0), Duration.ofSeconds(11),
                HIDDEN_TAB, () -> {});
        }

        notifier.looked("a");

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("cmd b");
    }

    /** Its pane is gone, so it can never be looked at; it would sit in the column all session. */
    @Test void aClosedPaneLeavesTheColumnEvenThoughYouNeverSawIt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> {});
        passThreshold();

        notifier.closed("pane");

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).hasSize(1);
        assertThat(deck.notices().getFirst().orphaned()).isTrue();
    }

    @Test void aRunningCommandStaysInTheColumnEvenWhileYouWatchIt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> 0L, () -> {});
        passThreshold();

        notifier.looked("pane");

        assertThat(deck.column()).hasSize(1);
    }

    @Test void everyNoticeThisProducerPostsIsATask() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {});
        passThreshold();

        assertThat(deck.notices().getFirst().kind()).isEqualTo(BuddyNotice.Kind.TASK);
    }
```

Add `import java.util.List;` if it is not already there, and update every existing
`new BuddyNotice(...)` in this file for the new component order.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandNotifierTest'`

Expected: FAIL — `looked` is undefined and `BuddyNotice` takes seven components.

- [ ] **Step 3: Write the implementation**

In `CommandNotifier.java`:

```java
    void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate) {
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        flight.elapsed = elapsedNanos;
        inFlight.put(key, flight);
        String title = title(command);
        flight.cancel = schedule.apply(wait, () -> {
            if (inFlight.get(key) != flight) return;
            flight.passed = true;
            flight.cancel = () -> {};
            if (running++ == 0) onWorkingChanged.accept(true);
            deck.post(new BuddyNotice(SOURCE, key, BuddyNotice.Kind.TASK, title,
                BuddyNotice.State.RUNNING,
                () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong())), activate));
            onDeckChanged.run();
        });
    }
```

```java
    void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                  CommandNotice.Origin origin, Runnable activate) {
        cancel(key);
        Duration wait = threshold.get();
        if (wait.isZero() || ran.compareTo(wait) < 0) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        String title = title(command);
        deck.post(new BuddyNotice(SOURCE, key, BuddyNotice.Kind.TASK, title,
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
        // You were looking straight at it, so it is already seen and never reaches the column.
        if (origin.ownPaneFocused()) deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
        if (CommandNotice.shouldNotify(origin, ran, wait)) operatingSystem.accept(title, detail);
    }

    /** That pane took focus: whatever it posted has now been seen. */
    void looked(Object key) {
        deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
    }
```

```java
    void closed(Object key) {
        InFlight flight = inFlight.get(key);
        boolean stillRunning = flight != null && flight.passed;
        Duration ran = flight == null ? Duration.ZERO : Duration.ofNanos(flight.elapsed.getAsLong());
        cancel(key);
        if (stillRunning) deck.orphan(SOURCE, key, "Stopped after " + humanize(ran));
        else deck.orphan(SOURCE, key);
        // Its pane is gone, so it can never be looked at and would sit in the column all session.
        deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
    }
```

In `TerminalPane.java`, the pane already has `onFocused`; confirm it fires when the terminal takes
keyboard focus and add nothing if so. If it does not, add beside `onCommandStarted`:

```java
    /** This pane took keyboard focus. Delivered on the EDT. */
    Runnable onPaneFocused = () -> {};
```

and fire it from the same place `onFocused` is fired.

In `WindowContent.java`, add the sink and wire it in `configurePane`:

```java
    /** Set by the application: this pane took focus, so whatever it posted has been seen. */
    java.util.function.Consumer<Object> onPaneFocused = pane -> {};
```

```java
        pane.onPaneFocused = () -> onPaneFocused.accept(pane);
```

and add `onPaneFocused = pane -> {};` to the line that already resets `onPaneClosed`.

In `JasperApplication.java`, in `newWindow`:

```java
        window.content().onPaneFocused = notifications::looked;
```

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew check`

Expected: BUILD SUCCESSFUL. Read the XML in `*/build/test-results/test/` for exact counts.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/CommandNotifier.java jasper-app/src/main/java/dev/jasper/app/WindowContent.java jasper-app/src/main/java/dev/jasper/app/TerminalPane.java jasper-app/src/main/java/dev/jasper/app/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java
git commit -m "$(printf 'feat: clear the column by looking at what it is holding\n\nFocusing a pane acknowledges its notice, so the column empties as you work\nrather than needing to be tidied. A command that ends in the pane you are\nalready in is acknowledged on the spot and never appears at all - you\nwatched it happen.\n\nClosing a pane acknowledges as well as orphans: its pane is gone, so\nfocusing it is no longer possible and it would otherwise sit above his\nhead for the rest of the session.\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
```

---

### Task 7: Documentation, build, install

**Files:**
- Modify: `docs/configuration.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-09-17-jasper-buddy-thought-column-design.md` (status banner)

- [ ] **Step 1: Update the documentation**

In `docs/configuration.md`, replace the drawer paragraphs of **Finished-command notifications** with
the two surfaces: a column above his head holding only what is live or unseen, emptying as you visit
the panes; a drawer on a single click holding everything this run, bounded at 50, with per-card
dismiss and **Clear all**, cleared on restart. Keep the shell-integration requirement, the tmux
paragraph and the notification rule exactly as they are.

In `docs/STATUS.md`, record: the deck split into two surfaces; `Kind` separating tasks from
connections so a tunnel is expressible; acknowledgement as deck state; `NEEDS_INPUT` defined and
rendered but set only by the shell's own prompt; connection producers and healthy-connection grouping
deliberately deferred.

Set the spec's status line to `**Status:** Implemented.`

- [ ] **Step 2: Check source hygiene**

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

- [ ] **Step 3: Full verification**

Run: `./gradlew check --rerun-tasks` and report exact counts from the XML.

- [ ] **Step 4: Commit, build and install**

```bash
git add docs/
git commit -m "$(printf 'docs: record the thought column and the drawer\n\n🤖 Generated with Claude Code at The Home Depot\n\nCo-Authored-By: Claude <noreply@anthropic.com>')"
./gradlew :jasper-app:packageApp
```

Then verify the built jar contains `BuddyColumnPanel` and `BuddyCard`, replace
`/Applications/Jasper.app` with `jasper-app/build/packaging/image/Jasper.app`, and verify the
installed jar the same way. **Do not launch it.**

- [ ] **Step 5: Hand the GUI checks to the user**

1. A long command in a background tab: a bubble rises above his head with the tail pointing at him.
2. Visiting that pane empties the column; single-clicking him shows it still in the drawer.
3. A command in the pane you are watching produces no bubble at all.
4. Three or more at once: three bubbles and a count on the nearest.
5. Drag him to the top of the screen: the column flips below and the newest stays nearest his head.
6. Closing a pane mid-command: its bubble leaves the column and is dimmed and inert in the drawer.

---

## Self-review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| Column above the head, newest nearest him | 3, 4 |
| Thought tail | 3, 4 |
| Flip below with the order inverting | 3, 4, 5 |
| Three bubbles then a count | 3, 4 |
| Empty column draws nothing | 4, 5 |
| `Kind` task vs connection, `fits` | 1 |
| Seven states, `live()`, `wantsAttention()` | 1 |
| Acknowledgement on the deck | 1 |
| Membership `live() || !acknowledged` | 1 |
| Re-post un-acknowledges | 1 |
| Looking at a pane acknowledges | 6 |
| Finished-under-your-eyes never reaches the column | 6 |
| Close acknowledges as well as orphans | 6 |
| Drawer on a single click | 5 |
| Drawer keeps dismiss, clear-all, bound of 50 | unchanged from the previous plan |
| `NEEDS_INPUT` rendered, not detected | 2 (glyph), 4 (paints) |
| Notification rule unchanged | 6 |
| Documentation | 7 |

**Type consistency** — `BuddyNotice` takes seven components in Tasks 1, 2, 4 and 6. `BuddyCard.WIDTH`
and `BuddyCard.height(owner)` replace `BuddyDeckPanel.CARD_WIDTH`/`cardHeight()` in Tasks 2, 4 and 5,
including in the tests. `BuddyDeckLayout.column(index, count, width, cardHeight, below)` keeps that
argument order in Tasks 3 and 4. `deck.orphan` has both a two- and a three-argument form from the
previous plan and Task 1 updates both.

**Build order** — the first draft of this plan had Task 1 change `BuddyNotice`'s shape and leave
`CommandNotifier` broken until Task 6. That does not merely look untidy: Gradle compiles all of `main`
before running any test, so **no test would have run at all**, including Task 1's own, and the TDD
gate would have been unreachable. Every task now leaves the module compiling, updating its own call
sites, and deletions happen in the task that removes the last caller rather than in the task that
introduces the replacement. `./gradlew check` should pass after every task.
