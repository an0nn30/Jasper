package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyAnimatorTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    @Test void hiddenAnimatorIsIdleWithNothingScheduled() {
        var animator = new BuddyAnimator(new Random(1));
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
        animator.hoverEntered(0); animator.tick(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
    }

    @Test void idleBlinksBetweenThreeAndSixSecondsForOneHundredTwentyMilliseconds() {
        var animator = new BuddyAnimator(new Random(7));
        long now = 10 * SECOND;
        animator.shown(now);
        int winks = 0;
        for (int i = 0; i < 200; i++) {
            long due = animator.nextDueNanos().orElseThrow();
            assertThat(due - now).isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
            animator.tick(due - 1);
            assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
            animator.tick(due);
            assertThat(animator.frame()).isIn(BuddyFrame.BLINK, BuddyFrame.WINK);
            if (animator.frame() == BuddyFrame.WINK) winks++;
            long reopen = animator.nextDueNanos().orElseThrow();
            assertThat(reopen - due).isEqualTo(BuddyAnimator.BLINK_NANOS);
            animator.tick(reopen);
            assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
            now = reopen;
        }
        assertThat(winks).isBetween(25, 75);
    }

    @Test void hoverDancesInOrderAtEightFramesPerSecondAndFinishesTheCycleAfterExit() {
        var animator = new BuddyAnimator(new Random(3));
        animator.shown(0);
        animator.hoverEntered(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        List<BuddyFrame> seen = new ArrayList<>();
        long now = SECOND;
        for (int i = 0; i < 7; i++) {
            long due = animator.nextDueNanos().orElseThrow();
            assertThat(due - now).isEqualTo(BuddyAnimator.STEP_NANOS);
            animator.tick(due); now = due;
            seen.add(animator.frame());
        }
        assertThat(seen).containsExactly(BuddyFrame.WAVE_B, BuddyFrame.HOP, BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT,
            BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP);
        animator.hoverExited(now);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        animator.tick(animator.nextDueNanos().orElseThrow()); assertThat(animator.frame()).isEqualTo(BuddyFrame.LEAN_LEFT);
        animator.tick(animator.nextDueNanos().orElseThrow()); assertThat(animator.frame()).isEqualTo(BuddyFrame.LEAN_RIGHT);
        long end = animator.nextDueNanos().orElseThrow();
        animator.tick(end);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos().orElseThrow() - end).isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
    }

    @Test void hoverInterruptsABlinkAndHiddenClearsEverything() {
        var animator = new BuddyAnimator(new Random(5));
        animator.shown(0);
        long blink = animator.nextDueNanos().orElseThrow();
        animator.tick(blink);
        assertThat(animator.frame()).isIn(BuddyFrame.BLINK, BuddyFrame.WINK);
        animator.hoverEntered(blink + 1);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        animator.hidden();
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
        animator.shown(blink + 2);
        assertThat(animator.nextDueNanos()).isPresent();
    }

    @Test void lateTicksCatchUpWithoutSkippingTheDanceOrder() {
        var animator = new BuddyAnimator(new Random(9));
        animator.shown(0);
        animator.hoverEntered(0);
        animator.tick(BuddyAnimator.STEP_NANOS * 2 + 1);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        assertThat(animator.nextDueNanos().orElseThrow()).isEqualTo(BuddyAnimator.STEP_NANOS * 3);
    }
}
