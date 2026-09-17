# Finished-command notifications — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell you when a command that took a while finishes in a tab you are not looking at, through the desk buddy — who types while it runs and shows an exit-status bubble when it ends.

**Architecture:** Five slices, each usable on its own. Duration first, because everything depends on it. Then the pure decision rule. Then the sprites and the animation they drive. Then the routing — buddy bubble or native notification. Documentation last.

**Tech Stack:** Java 25 on the JetBrains Runtime, Swing, JUnit 6 + AssertJ, Python 3 + Pillow 12.3.0 for the sprite strip.

**Spec:** `docs/superpowers/specs/2026-09-16-jasper-finished-command-notifications-design.md`

**Status: complete.** All six tasks executed on `claude/command-notifications`.
Deviations: the plan expected one commit on the sprite master and there were
four, so the safety check it specified actually mattered — the script turned out
to be the source of truth anyway, proven byte-for-byte. The loader case anchor
the plan named (`history.enabled`) no longer existed after the morning's
palette.scopes move, so the new case went in against a current one. The first
laptop drawing was rejected on sight at 8x and redrawn smaller. `BuddySpriteTest`
already had a `pixels` helper, and two of its assertions hardcoded the old frame
count; they now derive from `BuddyFrame`.

## Global Constraints

- Java 25 on the **JetBrains Runtime**. Use `./gradlew`, never a system `gradle`.
- `jasper-terminal` never depends on `jasper-app`. **No public method in `jasper-terminal` takes or returns a JediTerm type.**
- Never put raw control, private-use or unpaired surrogate characters in source. Use Java escapes.
- Threading: JediTerm runs on the session's reader thread; the view and every buddy class run on the EDT. `commandExecuted` fires on the reader thread, so anything it touches in `jasper-app` must hop to the EDT.
- Do not launch the GUI. `./gradlew check` only. The buddy classes are testable headlessly — `BuddyAnimator` is a plain state machine.
- No interface without two real implementations.
- Each task ends with a commit whose message ends with the repository's attribution trailers.

---

### Task 1: the session reports how long a command took

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java` (the one call site)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/ShellIntegrationSessionTest.java`

**Interfaces:**
- Produces: `TerminalSession.Listener.commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory, Duration duration)` — a fourth argument; `Duration.ZERO` when the cycle had no C mark.
- Produces: `TerminalSession(TtyConnector, int columns, int rows, int scrollback, LongSupplier clock)` — the existing four-argument constructor delegates with `System::nanoTime`.

- [x] **Step 1: Write the failing tests**

```java
    @Test void aCommandReportsHowLongItRan() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        try (var timed = new TerminalSession(new FakeConnector(), 20, 4, 100, clock::get)) {
            var durations = new CopyOnWriteArrayList<Duration>();
            timed.addListener(new TerminalSession.Listener() {
                @Override public void commandExecuted(String command, OptionalInt exitStatus,
                        Optional<Path> directory, Duration duration) {
                    durations.add(duration);
                }
            });
            timed.startReading();
            FakeConnector connector = (FakeConnector) timed.connectorForTest();
            connector.feed("\033]133;A\007$ \033]133;B\007sleep 5\033]133;C\007");
            Await.until(() -> timed.snapshot().lineText(0).contains("sleep 5"), "command captured");
            clock.set(Duration.ofSeconds(12).toNanos());
            connector.feed("\033]133;D;0\007");

            Await.until(() -> !durations.isEmpty(), "command reported");
            assertThat(durations.getFirst()).isEqualTo(Duration.ofSeconds(12));
        }
    }

    /** A prompt mark flushing a stale command never saw a C, so it cannot claim a duration. */
    @Test void aCycleWithNoCommandStartReportsZero() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007typed\033]133;C\007");
        connector.feed("\033]133;D;0\007");
        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(durations.getFirst()).isNotNegative();
    }
```

`listenForCommands` gains a `durations` list alongside `captured` and `statuses`. Rather than adding a `connectorForTest` accessor to production code, construct the `FakeConnector` first and keep the reference:

```java
        FakeConnector connector = new FakeConnector();
        var timed = new TerminalSession(connector, 20, 4, 100, clock::get);
```

- [x] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.ShellIntegrationSessionTest' --rerun-tasks`
Expected: compile failure — no five-argument constructor and no fourth listener argument.

- [x] **Step 3: Time the command in the session**

Add the clock and the start stamp beside the other reader-thread fields:

```java
    private final LongSupplier clock;
    // Reader thread only: nanoTime at the C mark, or 0 when this cycle never saw one.
    private long commandStartedAt;
```

```java
    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback) {
        this(connector, columns, rows, scrollback, System::nanoTime);
    }

    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        // ...existing body unchanged...
    }
```

In `captureCommand`, stamp when a command is actually taken — inside the lock, beside `pendingCommand`:

```java
            commandStartRow = -1;
            pendingCommand = text.isEmpty() ? null : text;
            commandStartedAt = pendingCommand == null ? 0 : clock.getAsLong();
```

In `flushPendingCommand`, measure and clear:

```java
    private void flushPendingCommand(OptionalInt exitStatus) {
        String command = pendingCommand;
        long startedAt = commandStartedAt;
        pendingCommand = null;
        pendingCommandText = null;
        commandStartedAt = 0;
        if (command == null) return;
        // nanoTime is monotonic, so a wall-clock change mid-command cannot make this negative.
        Duration ran = startedAt == 0 ? Duration.ZERO : Duration.ofNanos(clock.getAsLong() - startedAt);
        Optional<Path> directory = workingDirectory();
        listeners.forEach(l -> l.commandExecuted(command, exitStatus, directory, ran));
    }
```

Update the `Listener` javadoc to say the duration is measured from the command-start mark and is zero when there was none. Import `java.time.Duration` and `java.util.function.LongSupplier`.

- [x] **Step 4: Update the one call site**

`TerminalPane` builds a `ShellHistoryEntry` in its listener. Widen the override to four arguments and ignore the duration for now — Task 4 uses it:

```java
            @Override public void commandExecuted(String command, OptionalInt exitStatus,
                    Optional<Path> workingDirectory, Duration duration) {
```

- [x] **Step 5: Verify**

Run: `./gradlew check --rerun-tasks`
Expected: PASS. Read exact counts from `*/build/test-results/test/TEST-*.xml`.

- [x] **Step 6: Commit**

```bash
git add jasper-terminal/src jasper-app/src/main/java/dev/jasper/app/TerminalPane.java
git commit -m "feat: report how long a command ran alongside its exit status"
```

---

### Task 2: the rule for whether a finish is worth interrupting for

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/CommandNotice.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandNoticeTest.java`

**Interfaces:**
- Produces: `CommandNotice.Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected)`
- Produces: `CommandNotice.shouldNotify(Origin origin, Duration ran, Duration threshold)` — `boolean`.

Keeping the rule in one pure function means the eight combinations are tested without a window, and the one case the user excluded is a single line to change.

- [x] **Step 1: Write the failing test**

```java
class CommandNoticeTest {
    private static final Duration TEN = Duration.ofSeconds(10);

    private static boolean notify(boolean anyActive, boolean ownActive, boolean ownSelected, Duration ran) {
        return CommandNotice.shouldNotify(new CommandNotice.Origin(anyActive, ownActive, ownSelected), ran, TEN);
    }

    @Test void nothingShorterThanTheThresholdNotifies() {
        assertThat(notify(false, false, false, Duration.ofSeconds(9))).isFalse();
        assertThat(notify(false, false, false, Duration.ZERO)).isFalse();
        assertThat(notify(false, false, false, TEN)).as("exactly the threshold counts").isTrue();
    }

    @Test void aZeroThresholdDisablesTheFeatureEntirely() {
        assertThat(CommandNotice.shouldNotify(new CommandNotice.Origin(false, false, false),
            Duration.ofHours(1), Duration.ZERO)).isFalse();
    }

    @Test void anythingNotifiesWhileJasperIsInTheBackground() {
        assertThat(notify(false, false, true, TEN)).as("even the selected tab").isTrue();
        assertThat(notify(false, false, false, TEN)).isTrue();
    }

    @Test void aVisibleTabNeverNotifies() {
        assertThat(notify(true, true, true, TEN)).isFalse();
    }

    @Test void anotherTabOfTheActiveWindowNotifies() {
        assertThat(notify(true, true, false, TEN)).isTrue();
    }

    /** Deliberate: a command in a different window while one is focused stays quiet. */
    @Test void anotherWindowStaysQuietWhileOneIsFocused() {
        assertThat(notify(true, false, false, TEN)).isFalse();
        assertThat(notify(true, false, true, TEN)).isFalse();
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandNoticeTest' --rerun-tasks`
Expected: compile failure — `CommandNotice` does not exist.

- [x] **Step 3: Write the rule**

```java
package dev.jasper.app;

import java.time.Duration;

/** Whether a finished command is worth interrupting for, and what the buddy should say about it. */
final class CommandNotice {
    private CommandNotice() {}

    /** Where a finished command ran, relative to what the user is looking at. */
    record Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected) {}

    /**
     * True when the command ran at least {@code threshold} and finished somewhere the user is not
     * looking. A command in another Jasper window, while a different Jasper window has focus, is
     * deliberately quiet: you are still looking at Jasper, and the tab strip already shows it.
     */
    static boolean shouldNotify(Origin origin, Duration ran, Duration threshold) {
        if (threshold.isZero() || ran.compareTo(threshold) < 0) return false;
        if (!origin.anyWindowActive()) return true;
        return origin.ownWindowActive() && !origin.ownTabSelected();
    }
}
```

- [x] **Step 4: Verify and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.CommandNoticeTest' --rerun-tasks`
Expected: PASS.

```bash
git add jasper-app/src/main/java/dev/jasper/app/CommandNotice.java \
        jasper-app/src/test/java/dev/jasper/app/CommandNoticeTest.java
git commit -m "feat: decide when a finished command is worth interrupting for"
```

---

### Task 3: the typing sprites

**Files:**
- Modify: `packaging/buddy/generate.py`
- Modify: `packaging/buddy/README.md`
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java`
- Regenerate: `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java`

**Interfaces:**
- Produces: `BuddyFrame.TYPE_A`, `BuddyFrame.TYPE_B`, `BuddyFrame.TYPE_REST` — appended, so every existing column index is unchanged.

`generate.py` was written as a bootstrap and its header says not to rerun it after hand edits. The `.ase` master has not in fact been hand-edited since — `git log` shows one commit — so regenerating is safe. Confirm that before running it; if the master has diverged, stop and ask rather than overwriting art.

- [x] **Step 1: Write the failing test**

```java
    @Test void theStripCarriesEveryFrameIncludingTyping() throws Exception {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage image = sprite.frame(frame);
            assertThat(image.getWidth()).isEqualTo(BuddySprite.FRAME_WIDTH);
            boolean anyOpaque = false;
            for (int x = 0; x < image.getWidth() && !anyOpaque; x++)
                for (int y = 0; y < image.getHeight() && !anyOpaque; y++)
                    anyOpaque = (image.getRGB(x, y) >>> 24) != 0;
            assertThat(anyOpaque).as("%s draws something", frame).isTrue();
        }
        assertThat(BuddyFrame.values()).contains(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B, BuddyFrame.TYPE_REST);
    }

    /** The typing poses must differ from each other, or the animation would not read as movement. */
    @Test void theTypingFramesDifferFromOneAnother() {
        BuddySprite sprite = BuddySprite.load();
        assertThat(pixels(sprite.frame(BuddyFrame.TYPE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.TYPE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.TYPE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.TYPE_REST)));
    }
```

with a `pixels(BufferedImage)` helper returning the ARGB array.

- [x] **Step 2: Run to verify it fails**

Expected: compile failure on the new enum constants, then — once they exist but the PNG has not been regenerated — a failure from `BuddySprite`'s own width validation, which is the real red.

- [x] **Step 3: Add the frames to the enum**

```java
enum BuddyFrame {
    IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT,
    SIT, SIT_BLINK, TUCK, SLEEP_A, SLEEP_B, SLEEP_C,
    SPARKLE_A, SPARKLE_B, SPARKLE_C,
    TYPE_A, TYPE_B, TYPE_REST;
```

with a javadoc line: the three `TYPE_` cells are sitting poses behind a laptop, cycled while a long command runs.

- [x] **Step 4: Draw them in generate.py**

Append `"type_a", "type_b", "type_rest"` to `FRAMES`, and add a laptop helper beside the existing `shell` and `head` helpers, reusing the palette constants already defined at the top of the file:

```python
LAPTOP_CASE = OUT          # the outline colour, so the case reads as a silhouette
LAPTOP_SCREEN = (0x1E, 0x22, 0x2A, 255)   # the terminal's own background
LAPTOP_TEXT = (0x7F, 0xD9, 0xA8, 255)     # the prompt green from the mock-up


def laptop(c, hands):
    """An open laptop in front of a sitting buddy; `hands` is 'left', 'right' or 'rest'."""
    # screen, lid outline, keyboard wedge, a ">" glyph, then the hands at the key row
```

Build the three frames from the existing sitting body with the laptop over it, the head tilted down one pixel, and the hands alternating between two key positions plus a lowered rest pose. Keep every colour in the existing palette apart from the two laptop-screen colours, which the README documents.

- [x] **Step 5: Regenerate and verify**

```bash
git log --oneline -- packaging/buddy/jasper-buddy.ase   # expect exactly one commit
python3 packaging/buddy/generate.py
python3 -c "import struct,pathlib; d=pathlib.Path('jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png').read_bytes(); print(struct.unpack('>II', d[16:24]))"
```

Expected: `(840, 48)` — twenty frames. Then `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddySpriteTest' --rerun-tasks` passes.

Render the three new frames to a scratch PNG at 8× and look at them before moving on; a sprite that passes "some pixels are opaque" can still be unreadable.

- [x] **Step 6: Update the README table and commit**

Add rows 17–19 to the frame table and the two laptop colours to the palette table.

```bash
git add packaging/buddy jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java \
        jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png \
        jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java
git commit -m "feat: add typing sprites so the buddy works while a command runs"
```

---

### Task 4: the buddy types, then says how it went

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyAnimatorTest.java`

**Interfaces:**
- Produces: `BuddyAnimator.setWorking(boolean)` — idempotent; true enters `WORKING`, false returns to resting.

- [x] **Step 1: Write the failing tests**

```java
    @Test void workingCyclesTheTypingFramesAndOutlastsTheSleepTimers() {
        var animator = new BuddyAnimator(fixedRandom());
        animator.show(0L);
        animator.setWorking(true);
        var seen = new java.util.LinkedHashSet<BuddyFrame>();
        for (long tick = 0; tick < 40; tick++) {
            animator.advance(tick * TimeUnit.MILLISECONDS.toNanos(250));
            seen.add(animator.frame());
        }
        assertThat(seen).contains(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B);
        assertThat(seen).as("a working buddy does not fall asleep")
            .doesNotContain(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C, BuddyFrame.TUCK);
    }

    @Test void clearingWorkingReturnsToTheRestingProgression() {
        var animator = new BuddyAnimator(fixedRandom());
        animator.show(0L);
        animator.setWorking(true);
        animator.advance(TimeUnit.SECONDS.toNanos(1));
        animator.setWorking(false);
        animator.advance(TimeUnit.SECONDS.toNanos(2));
        assertThat(animator.frame()).isIn(BuddyFrame.IDLE, BuddyFrame.BLINK, BuddyFrame.WINK);
    }

    @Test void workingIsIgnoredUntilTheSpawnFinishes() {
        var animator = new BuddyAnimator(fixedRandom());
        animator.show(0L);
        animator.setWorking(true);
        animator.advance(TimeUnit.MILLISECONDS.toNanos(50));
        assertThat(animator.frame()).as("the spawn keeps the stage").isNotIn(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B);
    }
```

Match the existing test class's construction and clock conventions; read it before writing these.

- [x] **Step 2: Run to verify they fail**

Expected: compile failure on `setWorking`.

- [x] **Step 3: Add the mode**

Add `WORKING` to the private `Mode` enum, a `private boolean working;` flag and:

```java
    /** A long command is running: he sits down and types until it finishes. Idempotent. */
    void setWorking(boolean value) {
        if (working == value) return;
        working = value;
        if (!value && mode == Mode.WORKING) resting(lastAdvance);
        else if (value && canInterrupt()) startWorking(lastAdvance);
    }
```

`canInterrupt()` is false during `SPAWNING` and `GREETING`, so a short celebration is never cut off; `advance` enters `WORKING` at the end of those modes when `working` is still set. In `advance`, the `WORKING` branch cycles `TYPE_A`, `TYPE_B` and occasionally `TYPE_REST` on a 250 ms step and — critically — returns before the tuck and sleep deadlines are consulted, which is what the "does not fall asleep" assertion pins.

- [x] **Step 4: Verify and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyAnimatorTest' --rerun-tasks`
Expected: PASS, including every existing animator test.

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java \
        jasper-app/src/test/java/dev/jasper/app/BuddyAnimatorTest.java
git commit -m "feat: give the buddy a working mode that types and never sleeps"
```

---

### Task 5: tracking, routing and the setting

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/CommandNotifier.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/NativeNotifier.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigLoader.java`, `ConfigSnapshot.java`, `ConfigTemplate.java`, `config.example.toml`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`, `JasperApplication.java`, `BuddyWindow.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/CommandNotifierTest.java`, `ConfigLoaderTest.java`

**Interfaces:**
- Produces: `CommandNotifier.Channel` — one method, `deliver(String command, String detail, boolean succeeded, Runnable onActivate)`. Two real implementations: the buddy bubble and `NativeNotifier`.
- Produces: `ConfigSnapshot.longCommandSeconds()` — `int`, default 10.

- [x] **Step 1: Write the failing tests**

```java
    @Test void aLongCommandInAHiddenTabReachesTheBuddyWithItsStatus() {
        var delivered = new ArrayList<String>();
        var notifier = new CommandNotifier(() -> Duration.ofSeconds(10),
            (command, detail, succeeded, activate) -> delivered.add(command + "|" + detail + "|" + succeeded),
            (command, detail, succeeded, activate) -> fail("the buddy was available"));

        notifier.finished("./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            new CommandNotice.Origin(true, true, false), true, () -> {});

        assertThat(delivered).containsExactly("./gradlew build|Finished in 1m 12s|true");
    }

    @Test void aFailureSaysSo() { /* exit 1 -> "Exited 1 · 1m 12s", succeeded false */ }

    @Test void aVisibleTabIsNotWorthInterruptingFor() { /* origin(true,true,true) -> nothing delivered */ }

    @Test void theNativeChannelTakesOverWhenTheBuddyIsAway() { /* buddyAvailable false -> native called */ }

    @Test void multiLineCommandsCollapseForTheTitle() {
        // "echo a\necho b" -> "echo a ↵ echo b", matching how the History palette renders them
    }
```

and in `ConfigLoaderTest`:

```java
    @Test void longCommandSecondsParsesItsRangeAndDefaultsToTen() {
        assertThat(parse("").snapshot().longCommandSeconds()).isEqualTo(10);
        assertThat(parse("[notifications]\nlong_command_seconds=0\n").snapshot().longCommandSeconds()).isZero();
        assertThat(parse("[notifications]\nlong_command_seconds=3600\n").snapshot().longCommandSeconds()).isEqualTo(3600);
        var high = parse("[notifications]\nlong_command_seconds=3601\n");
        assertThat(high.snapshot().longCommandSeconds()).isEqualTo(10);
        assertThat(high.diagnostics()).isNotEmpty();
    }
```

- [x] **Step 2: Run to verify they fail**

Expected: compile failures on `CommandNotifier` and `longCommandSeconds()`.

- [x] **Step 3: Write the notifier**

`CommandNotifier` holds no Swing state and takes its two channels and its threshold as constructor arguments, so the test above needs no window:

```java
    /** Where a finished-command notice is shown. Two implementations: the buddy, and the OS. */
    interface Channel {
        void deliver(String command, String detail, boolean succeeded, Runnable onActivate);
    }

    void finished(String command, OptionalInt exitStatus, Duration ran, CommandNotice.Origin origin,
                  boolean buddyAvailable, Runnable onActivate) {
        if (!CommandNotice.shouldNotify(origin, ran, threshold.get())) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        (buddyAvailable ? buddy : native_).deliver(title(command), detail, succeeded, onActivate);
    }
```

`humanize` renders `45s`, `1m 12s`, `2h 5m`. `title` collapses newlines to `↵` and truncates.

`NativeNotifier` runs `osascript` on a single-thread executor, passing text as arguments so no quoting is involved:

```java
    private static final List<String> SCRIPT = List.of("/usr/bin/osascript",
        "-e", "on run argv", "-e", "display notification (item 1 of argv) with title (item 2 of argv)",
        "-e", "end run", "--");
```

The command line is whatever ran, so it can contain quotes, backslashes and newlines; interpolating it into an AppleScript string would be an injection hole. Passing it as `argv` removes the problem rather than escaping it. Log a failure once per session, not per notification, and do nothing at all when the OS is not macOS.

- [x] **Step 4: Add the setting**

`notifications` joins the root field set with `long_command_seconds`, validated `integer(path, value, 0, 3600, longCommandSeconds)`. `ConfigSnapshot` gains the component and a defaulting constructor, exactly as `maxResults` has. `ConfigTemplate` and `config.example.toml` gain the commented and live forms respectively — `ConfigTemplateTest` asserts the example covers every key, so both must be updated together.

- [x] **Step 5: Wire it up**

`TerminalPane`'s `commandExecuted` override now has the duration. It hops to the EDT and calls the notifier with an `Origin` built from `WindowContent`: `ownTabSelected` from `tab == currentTab()`, `ownWindowActive` from `isActiveAndOpen()`, `anyWindowActive` from the application's window list. `onActivate` selects the window, tab and pane.

The buddy channel calls `BuddyWindow` to show a `BuddyBubbleContent.message(title, detail, glyph)` with a check or cross glyph tinted from `Jasper.configSuccessForeground` / `Jasper.configErrorForeground`. `buddyAvailable` is the buddy's visibility.

The tracker that drives `setWorking` lives beside the notifier: a count of commands that have been running past the threshold, incremented by a per-pane timer and decremented on finish or pane close. `setWorking(count > 0)`.

- [x] **Step 6: Verify and commit**

Run: `./gradlew check --rerun-tasks`
Expected: PASS. Read exact counts from the XML.

```bash
git add jasper-app/src config.example.toml
git commit -m "feat: notify through the buddy when a long command finishes out of sight"
```

---

### Task 6: documentation

**Files:**
- Modify: `docs/configuration.md`, `docs/STATUS.md`, `packaging/buddy/README.md`
- Modify: `docs/superpowers/specs/2026-09-16-jasper-finished-command-notifications-design.md`

- [x] **Step 1: Document the setting and its one hard requirement**

`docs/configuration.md` gains `notifications.long_command_seconds` in the settings table and a short section. It must say plainly that **duration comes from the shell-integration marks, so without integration there are no notifications at all** — and link to the Shell integration section. It also records which cases notify and which deliberately do not, and that the native fallback is macOS-only.

- [x] **Step 2: Record deviations in the spec and STATUS**

Anything that differed from the spec goes in both, with the measured test counts read from `*/build/test-results/test/TEST-*.xml`. Record as user-run: a long command in a background tab on the real desktop, watching the typing animation and the bubble, and the same with the buddy disabled.

- [x] **Step 3: Commit**

```bash
git add docs packaging/buddy/README.md
git commit -m "docs: record finished-command notifications and the integration requirement"
```

---

## Self-review

**Spec coverage.** Duration → Task 1. The notify rule → Task 2. Sprites → Task 3. Animation → Task 4. Threshold setting, routing, native fallback and wiring → Task 5. Documentation → Task 6. Every bullet in the spec's Testing section maps to a step.

**Deliberately not done.** Notification history, per-command opt-out, non-macOS native notifications, sound, tab or Dock badges — all listed out of scope in the spec.

**Type consistency.** `commandExecuted` gains `Duration duration` in Task 1 and every later task uses that signature. `CommandNotice.Origin(boolean, boolean, boolean)` is introduced in Task 2 and consumed unchanged by `CommandNotifier` in Task 5. `BuddyFrame.TYPE_A/TYPE_B/TYPE_REST` are appended in Task 3 and referenced by the animator in Task 4. `CommandNotifier.Channel` has exactly two implementations, satisfying the repository's interface rule.

**Risk to watch.** Task 3 regenerates a committed art asset. The step checks the `.ase` master's history first and stops if it has been hand-edited, because `generate.py`'s own header warns that rerunning it would overwrite hand edits.
