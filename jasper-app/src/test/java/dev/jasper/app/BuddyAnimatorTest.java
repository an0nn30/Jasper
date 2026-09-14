package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyAnimatorTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long MILLIS = TimeUnit.MILLISECONDS.toNanos(1);

    /** One scheduled step: the time the window's timer would fire, and the frame it would paint. */
    private record Event(long at, BuddyFrame frame) { }

    /** Replays every step due at or before {@code end}, exactly as {@code BuddyWindow}'s timer would. */
    private static List<Event> runUntil(BuddyAnimator animator, long end) {
        List<Event> events = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        while (true) {
            long due = animator.nextDueNanos().orElseThrow();
            assertThat(due).as("due times must strictly advance").isGreaterThan(previous);
            if (due > end) return events;
            previous = due;
            animator.tick(due);
            events.add(new Event(due, animator.frame()));
        }
    }

    private static long blinks(List<Event> events, BuddyFrame... shut) {
        return events.stream().filter(event -> List.of(shut).contains(event.frame())).count();
    }

    @Test void hiddenAnimatorIsIdleWithNothingScheduled() {
        var animator = new BuddyAnimator(new Random(1));
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
        animator.hoverEntered(0);
        animator.tick(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
    }

    @Test void standsThenSitsThenSleepsOnSchedule() {
        var animator = new BuddyAnimator(new Random(11));
        animator.shown(0);

        List<Event> standing = runUntil(animator, 19_900 * MILLIS);
        assertThat(standing).isNotEmpty()
            .allSatisfy(event -> assertThat(event.frame()).isIn(BuddyFrame.IDLE, BuddyFrame.BLINK, BuddyFrame.WINK));
        assertThat(blinks(standing, BuddyFrame.BLINK, BuddyFrame.WINK)).isGreaterThanOrEqualTo(3);

        List<Event> sitting = runUntil(animator, 59_900 * MILLIS).stream().filter(event -> event.at() >= 20 * SECOND).toList();
        assertThat(sitting).isNotEmpty();
        assertThat(sitting.getFirst().at()).isEqualTo(20 * SECOND);
        assertThat(sitting).allSatisfy(event -> assertThat(event.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK));
        assertThat(blinks(sitting, BuddyFrame.SIT_BLINK)).isGreaterThanOrEqualTo(3);

        List<Event> tucking = runUntil(animator, 60 * SECOND);
        assertThat(tucking.getLast()).isEqualTo(new Event(60 * SECOND, BuddyFrame.TUCK));
        assertThat(animator.nextDueNanos()).hasValue(60_400 * MILLIS);

        assertThat(runUntil(animator, 62_200 * MILLIS)).containsExactly(
            new Event(60_400 * MILLIS, BuddyFrame.SLEEP_A),
            new Event(61_000 * MILLIS, BuddyFrame.SLEEP_B),
            new Event(61_600 * MILLIS, BuddyFrame.SLEEP_C),
            new Event(62_200 * MILLIS, BuddyFrame.SLEEP_A));
    }

    @Test void standingBlinksKeepTheThreeToSixSecondWindow() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0);
        long resumed = 0;            // the last moment the standing blink clock (re)started
        long nextHover = 15 * SECOND;  // greet him before 20 s so he never sits down
        int blinks = 0;
        int winks = 0;
        while (blinks < 100) {
            long due = animator.nextDueNanos().orElseThrow();
            if (due >= nextHover) {
                animator.hoverEntered(nextHover);
                nextHover += 15 * SECOND;
                continue;
            }
            animator.tick(due);
            BuddyFrame frame = animator.frame();
            if (frame == BuddyFrame.BLINK || frame == BuddyFrame.WINK) {
                assertThat(due - resumed).as("gap before blink %d", blinks)
                    .isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
                long reopen = animator.nextDueNanos().orElseThrow();
                assertThat(reopen - due).as("length of blink %d", blinks).isEqualTo(BuddyAnimator.BLINK_NANOS);
                blinks++;
                if (frame == BuddyFrame.WINK) winks++;
                animator.tick(reopen);
                assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
                resumed = reopen;
            } else if (frame == BuddyFrame.IDLE) {
                resumed = due;       // a greeting just ended; the blink clock restarts here
            }
        }
        // Observed once and pinned: seed 7 with this draw order winks 27 times in 100 blinks,
        // inside the 15-35 band a one-in-four chance is expected to produce.
        assertThat(winks).isBetween(15, 35).isEqualTo(27);
    }

    @Test void hoverGreetsForOneSecondThenStandsAndRestartsBoredom() {
        var animator = new BuddyAnimator(new Random(3));
        animator.shown(0);
        animator.hoverEntered(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);

        assertThat(runUntil(animator, 1_875 * MILLIS)).containsExactly(
            new Event(1_125 * MILLIS, BuddyFrame.WAVE_B),
            new Event(1_250 * MILLIS, BuddyFrame.HOP),
            new Event(1_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(1_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(1_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(1_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(1_875 * MILLIS, BuddyFrame.HOP));
        assertThat(runUntil(animator, 2 * SECOND)).containsExactly(new Event(2 * SECOND, BuddyFrame.IDLE));

        List<Event> onwards = runUntil(animator, 25 * SECOND);
        Event firstSit = onwards.stream().filter(event -> event.frame() == BuddyFrame.SIT).findFirst().orElseThrow();
        assertThat(firstSit.at()).isEqualTo(22 * SECOND);
    }

    @Test void hoverDuringGreetingIsIgnoredAndHoverAfterwardsGreetsAgain() {
        var animator = new BuddyAnimator(new Random(13));
        animator.shown(0);
        animator.hoverEntered(SECOND);
        animator.tick(1_250 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);

        animator.hoverEntered(1_300 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        assertThat(animator.nextDueNanos()).hasValue(1_375 * MILLIS);
        assertThat(runUntil(animator, 2 * SECOND)).containsExactly(
            new Event(1_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(1_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(1_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(1_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(1_875 * MILLIS, BuddyFrame.HOP),
            new Event(2 * SECOND, BuddyFrame.IDLE));

        animator.hoverEntered(5 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        assertThat(animator.nextDueNanos()).hasValue(5 * SECOND + BuddyAnimator.STEP_NANOS);
    }

    @Test void hoverWhileSleepingWakesThenGreets() {
        var animator = new BuddyAnimator(new Random(17));
        animator.shown(0);
        animator.tick(61 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);

        animator.hoverEntered(61_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(animator.nextDueNanos()).hasValue(61_350 * MILLIS);
        assertThat(runUntil(animator, 61_600 * MILLIS)).containsExactly(
            new Event(61_350 * MILLIS, BuddyFrame.IDLE),
            new Event(61_600 * MILLIS, BuddyFrame.WAVE_A));
        assertThat(runUntil(animator, 62_600 * MILLIS)).containsExactly(
            new Event(61_725 * MILLIS, BuddyFrame.WAVE_B),
            new Event(61_850 * MILLIS, BuddyFrame.HOP),
            new Event(61_975 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(62_100 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(62_225 * MILLIS, BuddyFrame.WAVE_A),
            new Event(62_350 * MILLIS, BuddyFrame.WAVE_B),
            new Event(62_475 * MILLIS, BuddyFrame.HOP),
            new Event(62_600 * MILLIS, BuddyFrame.IDLE));

        Event firstSit = runUntil(animator, 90 * SECOND).stream()
            .filter(event -> event.frame() == BuddyFrame.SIT).findFirst().orElseThrow();
        assertThat(firstSit.at()).isEqualTo(62_600 * MILLIS + 20 * SECOND);
    }

    @Test void hoverWhileTuckingWakes() {
        var animator = new BuddyAnimator(new Random(19));
        animator.shown(0);
        animator.tick(60 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);

        animator.hoverEntered(60_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(runUntil(animator, 60_600 * MILLIS)).containsExactly(
            new Event(60_350 * MILLIS, BuddyFrame.IDLE),
            new Event(60_600 * MILLIS, BuddyFrame.WAVE_A));
    }

    @Test void veryLateTickLandsInTheRightPostureWithoutSpinning() {
        long eightHours = TimeUnit.HOURS.toNanos(8);
        var asleep = new BuddyAnimator(new Random(23));
        asleep.shown(0);
        long started = System.nanoTime();
        asleep.tick(eightHours);
        assertThat(System.nanoTime() - started).as("a late tick must not spin").isLessThan(SECOND);
        assertThat(asleep.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);
        assertThat(asleep.nextDueNanos().orElseThrow() - eightHours)
            .isPositive().isLessThanOrEqualTo(BuddyAnimator.SLEEP_STEP_NANOS);

        var sitting = new BuddyAnimator(new Random(29));
        sitting.shown(0);
        started = System.nanoTime();
        sitting.tick(30 * SECOND);
        assertThat(System.nanoTime() - started).as("a late tick must not spin").isLessThan(SECOND);
        assertThat(sitting.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK);
        assertThat(sitting.nextDueNanos().orElseThrow() - 30 * SECOND)
            .isPositive().isLessThanOrEqualTo(BuddyAnimator.BLINK_MAX_NANOS);
    }

    @Test void hiddenClearsEverything() {
        var greeting = new BuddyAnimator(new Random(31));
        greeting.shown(0);
        greeting.hoverEntered(SECOND);
        greeting.tick(1_500 * MILLIS);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.LEAN_RIGHT);
        greeting.hidden();
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(greeting.nextDueNanos()).isEmpty();
        greeting.shown(2 * SECOND);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(greeting.nextDueNanos().orElseThrow() - 2 * SECOND)
            .isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);

        var asleep = new BuddyAnimator(new Random(37));
        asleep.shown(0);
        asleep.tick(70 * SECOND);
        assertThat(asleep.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);
        asleep.hidden();
        assertThat(asleep.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(asleep.nextDueNanos()).isEmpty();
        asleep.shown(71 * SECOND);
        assertThat(asleep.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(asleep.nextDueNanos()).isPresent();
    }
}
