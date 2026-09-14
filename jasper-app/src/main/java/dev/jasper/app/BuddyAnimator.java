package dev.jasper.app;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Clock-driven frame selection; owns no timers and touches no Swing.
 *
 * <p>Left alone he stands, sits down after {@link #SIT_AFTER_NANOS}, tucks into his shell after
 * {@link #SLEEP_AFTER_NANOS} and then sleeps. Those postures are absolute deadlines measured from
 * the boredom origin, so a tick that arrives hours late (a closed lid) resolves in constant time
 * instead of replaying every step. The pointer entering plays a one-second greeting and restarts
 * the boredom clock; entering while he is tucked or asleep wakes him first.
 */
final class BuddyAnimator {
    static final long BLINK_MIN_NANOS = TimeUnit.SECONDS.toNanos(3);
    static final long BLINK_MAX_NANOS = TimeUnit.SECONDS.toNanos(6);
    static final long BLINK_NANOS = TimeUnit.MILLISECONDS.toNanos(120);
    static final long STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(125);
    static final long SIT_AFTER_NANOS = TimeUnit.SECONDS.toNanos(20);
    static final long SLEEP_AFTER_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final long TUCK_NANOS = TimeUnit.MILLISECONDS.toNanos(400);
    static final long WAKE_STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    static final long SLEEP_STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(600);
    static final List<BuddyFrame> GREETING = List.of(BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP,
        BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT, BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP);
    static final List<BuddyFrame> SLEEP = List.of(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);

    private enum Mode { HIDDEN, RESTING, TUCKING, SLEEPING, WAKING, GREETING }

    private final Random random;
    private Mode mode = Mode.HIDDEN;
    private BuddyFrame frame = BuddyFrame.IDLE;
    private long boredomOrigin;
    private long blinkDue;
    private boolean blinking;
    private boolean sitting;
    private BuddyFrame blinkFrame = BuddyFrame.BLINK;
    private long stepDue;
    private int step;

    BuddyAnimator(Random random) { this.random = Objects.requireNonNull(random, "random"); }

    BuddyFrame frame() { return frame; }

    /** The next moment {@link #tick} would change something; empty only while hidden. */
    OptionalLong nextDueNanos() {
        return switch (mode) {
            case HIDDEN -> OptionalLong.empty();
            case RESTING -> OptionalLong.of(Math.min(blinkDue, postureDue()));
            default -> OptionalLong.of(stepDue);
        };
    }

    void shown(long now) { standUp(now); }

    void hidden() {
        mode = Mode.HIDDEN; frame = BuddyFrame.IDLE;
        blinking = false; sitting = false; step = 0;
    }

    /** The pointer entered: greet, or wake first if he had gone to sleep. Ignored mid-greeting. */
    void hoverEntered(long now) {
        if (mode == Mode.HIDDEN) return;
        tick(now);
        switch (mode) {
            case RESTING -> startGreeting(now);
            case TUCKING, SLEEPING -> { mode = Mode.WAKING; step = 0; frame = BuddyFrame.TUCK; stepDue = now + WAKE_STEP_NANOS; }
            default -> { }
        }
    }

    /** Applies everything due at or before {@code now}; bounded work however late the tick is. */
    void tick(long now) {
        // WAKING -> GREETING -> RESTING -> settled posture is the longest chain of mode changes.
        for (int guard = 0; guard < 4 && mode != Mode.HIDDEN; guard++) {
            Mode before = mode;
            switch (mode) {
                case GREETING -> advanceGreeting(now);
                case WAKING -> advanceWaking(now);
                default -> advanceRest(now);
            }
            if (mode == before) return;
        }
    }

    private void advanceGreeting(long now) {
        while (now >= stepDue) {
            step++;
            if (step >= GREETING.size()) { standUp(stepDue); return; }
            frame = GREETING.get(step);
            stepDue += STEP_NANOS;
        }
    }

    private void advanceWaking(long now) {
        while (now >= stepDue) {
            step++;
            if (step > 1) { startGreeting(stepDue); return; }
            frame = BuddyFrame.IDLE;
            stepDue += WAKE_STEP_NANOS;
        }
    }

    /** Resolves standing / sitting / tucking / sleeping straight from the boredom origin. */
    private void advanceRest(long now) {
        long tuckAt = boredomOrigin + SLEEP_AFTER_NANOS;
        long sleepAt = tuckAt + TUCK_NANOS;
        if (now >= sleepAt) {
            long steps = (now - sleepAt) / SLEEP_STEP_NANOS;
            mode = Mode.SLEEPING;
            frame = SLEEP.get((int) (steps % SLEEP.size()));
            stepDue = sleepAt + (steps + 1) * SLEEP_STEP_NANOS;
            return;
        }
        if (now >= tuckAt) { mode = Mode.TUCKING; frame = BuddyFrame.TUCK; stepDue = sleepAt; return; }
        mode = Mode.RESTING;
        sitting = now >= boredomOrigin + SIT_AFTER_NANOS;
        advanceBlink(now);
        if (sitting) frame = blinking ? BuddyFrame.SIT_BLINK : BuddyFrame.SIT;
        else frame = blinking ? blinkFrame : BuddyFrame.IDLE;
    }

    private void advanceBlink(long now) {
        if (now < blinkDue) return;
        if (blinking) {
            blinking = false;
            scheduleBlink(blinkDue);                       // the eye reopened exactly on its deadline
        } else if (now < blinkDue + BLINK_NANOS) {
            blinking = true;
            if (!sitting) blinkFrame = random.nextInt(4) == 0 ? BuddyFrame.WINK : BuddyFrame.BLINK;
            blinkDue += BLINK_NANOS;
            return;
        } else {
            blinking = false;                              // a late tick skipped the whole blink
        }
        if (now >= blinkDue) scheduleBlink(now);
    }

    private void standUp(long now) {
        mode = Mode.RESTING; frame = BuddyFrame.IDLE;
        boredomOrigin = now; sitting = false; blinking = false; step = 0;
        scheduleBlink(now);
    }

    private void startGreeting(long now) {
        mode = Mode.GREETING; step = 0; blinking = false;
        frame = GREETING.getFirst(); stepDue = now + STEP_NANOS;
    }

    private long postureDue() { return boredomOrigin + (sitting ? SLEEP_AFTER_NANOS : SIT_AFTER_NANOS); }

    private void scheduleBlink(long from) {
        blinkDue = from + BLINK_MIN_NANOS + (long) (random.nextDouble() * (BLINK_MAX_NANOS - BLINK_MIN_NANOS));
    }
}
