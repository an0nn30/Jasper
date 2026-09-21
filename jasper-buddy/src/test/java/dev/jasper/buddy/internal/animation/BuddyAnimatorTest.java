package dev.jasper.buddy.internal.animation;

import dev.jasper.buddy.internal.presentation.BuddyWindow;
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

    /** A spawn step: everything the window reads to paint it. {@code overlay} is null when there is none. */
    private record Step(long at, BuddyFrame frame, float opacity, BuddyFrame overlay) { }

    private static Step paintState(BuddyAnimator animator, long at) {
        return new Step(at, animator.frame(), animator.opacity(), animator.overlay().orElse(null));
    }

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

    /** Replays the next {@code count} scheduled steps, recording the whole paint state of each. */
    private static List<Step> runSteps(BuddyAnimator animator, int count) {
        List<Step> steps = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long due = animator.nextDueNanos().orElseThrow();
            animator.tick(due);
            steps.add(paintState(animator, due));
        }
        return steps;
    }

    /** Ticks through the two-second spawn so the test starts from a standing buddy at {@code t0 + 2 s}. */
    private static void spawned(BuddyAnimator animator, long t0) {
        for (int step = 1; step <= BuddyAnimator.SPAWN_STEPS; step++)
            animator.tick(t0 + step * BuddyAnimator.STEP_NANOS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.opacity()).isEqualTo(1f);
        assertThat(animator.overlay()).isEmpty();
    }

    private static long blinks(List<Event> events, BuddyFrame... shut) {
        return events.stream().filter(event -> List.of(shut).contains(event.frame())).count();
    }

    /** Every frame the greeting can show; a quiet wake must produce none of them. */
    private static final List<BuddyFrame> WAVES = List.of(BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP,
        BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT);

    private static void assertNoWave(List<Event> events) {
        assertThat(events).as("a poke never waves")
            .noneSatisfy(event -> assertThat(WAVES).contains(event.frame()));
    }

    /** The first moment he is down, whether or not that frame happens to be a blink. */
    private static Event firstSit(List<Event> events) {
        return events.stream().filter(event -> event.frame() == BuddyFrame.SIT || event.frame() == BuddyFrame.SIT_BLINK)
            .findFirst().orElseThrow();
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

    @Test void shownSpawnsWithSparklesFadeInAndAWave() {
        var animator = new BuddyAnimator(new Random(41));
        animator.shown(0);
        assertThat(paintState(animator, 0)).isEqualTo(new Step(0, BuddyFrame.IDLE, 0f, BuddyFrame.SPARKLE_A));
        assertThat(animator.nextDueNanos()).hasValue(125 * MILLIS);

        assertThat(runSteps(animator, BuddyAnimator.SPAWN_STEPS)).containsExactly(
            new Step(125 * MILLIS, BuddyFrame.IDLE, 0.25f, BuddyFrame.SPARKLE_B),
            new Step(250 * MILLIS, BuddyFrame.IDLE, 0.5f, BuddyFrame.SPARKLE_C),
            new Step(375 * MILLIS, BuddyFrame.IDLE, 0.75f, BuddyFrame.SPARKLE_A),
            new Step(500 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_B),
            new Step(625 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_C),
            new Step(750 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_A),
            new Step(875 * MILLIS, BuddyFrame.IDLE, 1f, null),
            new Step(1_000 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_125 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_250 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_375 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_500 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_625 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_750 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_875 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(2 * SECOND, BuddyFrame.IDLE, 1f, null));

        assertThat(animator.nextDueNanos().orElseThrow() - 2 * SECOND).as("a blink follows the spawn")
            .isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
        Event firstSit = runUntil(animator, 25 * SECOND).stream()
            .filter(event -> event.frame() == BuddyFrame.SIT).findFirst().orElseThrow();
        assertThat(firstSit.at()).isEqualTo(22 * SECOND);
    }

    @Test void hoverDuringSpawnIsIgnored() {
        var animator = new BuddyAnimator(new Random(43));
        animator.shown(0);
        animator.hoverEntered(500 * MILLIS);
        assertThat(paintState(animator, 500 * MILLIS))
            .isEqualTo(new Step(500 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_B));

        assertThat(runSteps(animator, 12)).containsExactly(
            new Step(625 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_C),
            new Step(750 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_A),
            new Step(875 * MILLIS, BuddyFrame.IDLE, 1f, null),
            new Step(1_000 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_125 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_250 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_375 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_500 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_625 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(1_750 * MILLIS, BuddyFrame.WAVE_A, 1f, null),
            new Step(1_875 * MILLIS, BuddyFrame.WAVE_B, 1f, null),
            new Step(2 * SECOND, BuddyFrame.IDLE, 1f, null));

        animator.hoverEntered(2_500 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        assertThat(animator.nextDueNanos()).hasValue(2_500 * MILLIS + BuddyAnimator.STEP_NANOS);
    }

    @Test void hiddenDuringSpawnClearsAndShownSpawnsAgain() {
        var animator = new BuddyAnimator(new Random(47));
        animator.shown(0);
        animator.tick(375 * MILLIS);
        assertThat(paintState(animator, 375 * MILLIS))
            .isEqualTo(new Step(375 * MILLIS, BuddyFrame.IDLE, 0.75f, BuddyFrame.SPARKLE_A));

        animator.hidden();
        assertThat(paintState(animator, 0)).isEqualTo(new Step(0, BuddyFrame.IDLE, 1f, null));
        assertThat(animator.nextDueNanos()).isEmpty();

        animator.shown(SECOND);
        assertThat(paintState(animator, SECOND)).isEqualTo(new Step(SECOND, BuddyFrame.IDLE, 0f, BuddyFrame.SPARKLE_A));
        assertThat(animator.nextDueNanos()).hasValue(SECOND + BuddyAnimator.STEP_NANOS);
        spawned(animator, SECOND);
    }

    @Test void opacityIsOneAndOverlayEmptyOutsideSpawn() {
        var animator = new BuddyAnimator(new Random(53));
        animator.shown(0);
        spawned(animator, 0);

        animator.hoverEntered(3 * SECOND);
        assertThat(paintState(animator, 3 * SECOND)).isEqualTo(new Step(3 * SECOND, BuddyFrame.WAVE_A, 1f, null));

        animator.tick(30 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK);
        assertThat(animator.opacity()).isEqualTo(1f);
        assertThat(animator.overlay()).isEmpty();

        animator.tick(90 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);
        assertThat(animator.opacity()).isEqualTo(1f);
        assertThat(animator.overlay()).isEmpty();
    }

    @Test void standsThenSitsThenSleepsOnSchedule() {
        var animator = new BuddyAnimator(new Random(11));
        animator.shown(0);
        spawned(animator, 0);

        List<Event> standing = runUntil(animator, 21_900 * MILLIS);
        assertThat(standing).isNotEmpty()
            .allSatisfy(event -> assertThat(event.frame()).isIn(BuddyFrame.IDLE, BuddyFrame.BLINK, BuddyFrame.WINK));
        assertThat(blinks(standing, BuddyFrame.BLINK, BuddyFrame.WINK)).isGreaterThanOrEqualTo(3);

        List<Event> sitting = runUntil(animator, 61_900 * MILLIS).stream().filter(event -> event.at() >= 22 * SECOND).toList();
        assertThat(sitting).isNotEmpty();
        assertThat(sitting.getFirst().at()).isEqualTo(22 * SECOND);
        assertThat(sitting).allSatisfy(event -> assertThat(event.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK));
        assertThat(blinks(sitting, BuddyFrame.SIT_BLINK)).isGreaterThanOrEqualTo(3);

        List<Event> tucking = runUntil(animator, 62 * SECOND);
        assertThat(tucking.getLast()).isEqualTo(new Event(62 * SECOND, BuddyFrame.TUCK));
        assertThat(animator.nextDueNanos()).hasValue(62_400 * MILLIS);

        assertThat(runUntil(animator, 64_200 * MILLIS)).containsExactly(
            new Event(62_400 * MILLIS, BuddyFrame.SLEEP_A),
            new Event(63_000 * MILLIS, BuddyFrame.SLEEP_B),
            new Event(63_600 * MILLIS, BuddyFrame.SLEEP_C),
            new Event(64_200 * MILLIS, BuddyFrame.SLEEP_A));
    }

    @Test void standingBlinksKeepTheThreeToSixSecondWindow() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0);
        spawned(animator, 0);
        long resumed = 2 * SECOND;     // the last moment the standing blink clock (re)started
        long nextHover = 17 * SECOND;  // greet him before he would sit at 22 s so he never sits down
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
        // inside the 15-35 band a one-in-four chance is expected to produce. The spawn consumes no
        // randomness and the hovers move with it, so the whole run is the pre-spawn one shifted by 2 s.
        assertThat(winks).isBetween(15, 35).isEqualTo(27);
    }

    @Test void hoverGreetsForOneSecondThenStandsAndRestartsBoredom() {
        var animator = new BuddyAnimator(new Random(3));
        animator.shown(0);
        spawned(animator, 0);
        animator.hoverEntered(3 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);

        assertThat(runUntil(animator, 3_875 * MILLIS)).containsExactly(
            new Event(3_125 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_250 * MILLIS, BuddyFrame.HOP),
            new Event(3_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(3_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(3_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(3_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_875 * MILLIS, BuddyFrame.HOP));
        assertThat(runUntil(animator, 4 * SECOND)).containsExactly(new Event(4 * SECOND, BuddyFrame.IDLE));

        List<Event> onwards = runUntil(animator, 27 * SECOND);
        Event firstSit = onwards.stream().filter(event -> event.frame() == BuddyFrame.SIT).findFirst().orElseThrow();
        assertThat(firstSit.at()).isEqualTo(24 * SECOND);
    }

    @Test void hoverDuringGreetingIsIgnoredAndHoverAfterwardsGreetsAgain() {
        var animator = new BuddyAnimator(new Random(13));
        animator.shown(0);
        spawned(animator, 0);
        animator.hoverEntered(3 * SECOND);
        animator.tick(3_250 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);

        animator.hoverEntered(3_300 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        assertThat(animator.nextDueNanos()).hasValue(3_375 * MILLIS);
        assertThat(runUntil(animator, 4 * SECOND)).containsExactly(
            new Event(3_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(3_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(3_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(3_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_875 * MILLIS, BuddyFrame.HOP),
            new Event(4 * SECOND, BuddyFrame.IDLE));

        animator.hoverEntered(7 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        assertThat(animator.nextDueNanos()).hasValue(7 * SECOND + BuddyAnimator.STEP_NANOS);
    }

    @Test void hoverWhileSleepingWakesThenGreets() {
        var animator = new BuddyAnimator(new Random(17));
        animator.shown(0);
        animator.tick(63 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);

        animator.hoverEntered(63_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(animator.nextDueNanos()).hasValue(63_350 * MILLIS);
        assertThat(runUntil(animator, 63_600 * MILLIS)).containsExactly(
            new Event(63_350 * MILLIS, BuddyFrame.IDLE),
            new Event(63_600 * MILLIS, BuddyFrame.WAVE_A));
        assertThat(runUntil(animator, 64_600 * MILLIS)).containsExactly(
            new Event(63_725 * MILLIS, BuddyFrame.WAVE_B),
            new Event(63_850 * MILLIS, BuddyFrame.HOP),
            new Event(63_975 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(64_100 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(64_225 * MILLIS, BuddyFrame.WAVE_A),
            new Event(64_350 * MILLIS, BuddyFrame.WAVE_B),
            new Event(64_475 * MILLIS, BuddyFrame.HOP),
            new Event(64_600 * MILLIS, BuddyFrame.IDLE));

        Event firstSit = runUntil(animator, 92 * SECOND).stream()
            .filter(event -> event.frame() == BuddyFrame.SIT).findFirst().orElseThrow();
        assertThat(firstSit.at()).isEqualTo(64_600 * MILLIS + 20 * SECOND);
    }

    @Test void hoverWhileTuckingWakes() {
        var animator = new BuddyAnimator(new Random(19));
        animator.shown(0);
        animator.tick(62 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);

        animator.hoverEntered(62_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(runUntil(animator, 62_600 * MILLIS)).containsExactly(
            new Event(62_350 * MILLIS, BuddyFrame.IDLE),
            new Event(62_600 * MILLIS, BuddyFrame.WAVE_A));
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

    @Test void greetMatchesHoverFromEveryPosture() {
        var animator = new BuddyAnimator(new Random(59));
        animator.shown(0);
        spawned(animator, 0);

        animator.greet(3 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        assertThat(runUntil(animator, 4 * SECOND)).containsExactly(
            new Event(3_125 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_250 * MILLIS, BuddyFrame.HOP),
            new Event(3_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(3_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(3_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(3_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_875 * MILLIS, BuddyFrame.HOP),
            new Event(4 * SECOND, BuddyFrame.IDLE));

        animator.tick(25 * SECOND);                       // he sat down 20 s after the greeting ended
        assertThat(animator.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK);
        animator.greet(25 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        assertThat(animator.nextDueNanos()).hasValue(25 * SECOND + BuddyAnimator.STEP_NANOS);

        runUntil(animator, 26 * SECOND);                  // the greeting ends and he stands at 26 s
        animator.tick(90 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);
        animator.greet(90_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(runUntil(animator, 90_600 * MILLIS)).containsExactly(
            new Event(90_350 * MILLIS, BuddyFrame.IDLE),
            new Event(90_600 * MILLIS, BuddyFrame.WAVE_A));
    }

    @Test void pokeWhileStandingOnlyRestartsTheBoredomClock() {
        var animator = new BuddyAnimator(new Random(61));
        animator.shown(0);
        spawned(animator, 0);
        animator.tick(10 * SECOND);
        BuddyFrame frame = animator.frame();
        long due = animator.nextDueNanos().orElseThrow();

        animator.poke(10 * SECOND);
        assertThat(animator.frame()).as("a poke while standing never changes the frame").isEqualTo(frame);
        assertThat(animator.nextDueNanos()).as("the pending blink is untouched").hasValue(due);

        List<Event> onwards = runUntil(animator, 35 * SECOND);
        assertNoWave(onwards);
        assertThat(firstSit(onwards).at()).as("the boredom clock restarted at the poke").isEqualTo(30 * SECOND);
    }

    @Test void pokeWhileSittingStandsWithoutAWave() {
        var animator = new BuddyAnimator(new Random(67));
        animator.shown(0);
        spawned(animator, 0);
        animator.tick(25 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SIT, BuddyFrame.SIT_BLINK);

        animator.poke(25_500 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos().orElseThrow() - 25_500 * MILLIS).as("a blink, not a wave")
            .isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);

        List<Event> onwards = runUntil(animator, 46 * SECOND);
        assertNoWave(onwards);
        assertThat(firstSit(onwards).at()).isEqualTo(45_500 * MILLIS);
    }

    @Test void pokeWhileSleepingWakesWithoutAWave() {
        var animator = new BuddyAnimator(new Random(71));
        animator.shown(0);
        animator.tick(65 * SECOND);
        assertThat(animator.frame()).isIn(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C);

        animator.poke(65_100 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(animator.nextDueNanos()).hasValue(65_350 * MILLIS);
        animator.tick(65_350 * MILLIS);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).hasValue(65_600 * MILLIS);
        animator.tick(65_600 * MILLIS);
        assertThat(animator.frame()).as("he stands up where a greet would have waved").isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos().orElseThrow() - 65_600 * MILLIS)
            .isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);

        List<Event> onwards = runUntil(animator, 86 * SECOND);
        assertNoWave(onwards);
        assertThat(firstSit(onwards).at()).isEqualTo(85_600 * MILLIS);
    }

    @Test void pokeAndGreetAreIgnoredDuringSpawnGreetingAndWaking() {
        var spawning = new BuddyAnimator(new Random(73));
        spawning.shown(0);
        spawning.poke(500 * MILLIS);
        spawning.greet(500 * MILLIS);
        assertThat(paintState(spawning, 500 * MILLIS))
            .isEqualTo(new Step(500 * MILLIS, BuddyFrame.IDLE, 1f, BuddyFrame.SPARKLE_B));
        assertThat(spawning.nextDueNanos()).hasValue(625 * MILLIS);
        spawned(spawning, 0);

        var greeting = new BuddyAnimator(new Random(79));
        greeting.shown(0);
        spawned(greeting, 0);
        greeting.greet(3 * SECOND);
        greeting.tick(3_250 * MILLIS);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.HOP);
        greeting.poke(3_300 * MILLIS);
        greeting.greet(3_300 * MILLIS);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.HOP);
        assertThat(greeting.nextDueNanos()).hasValue(3_375 * MILLIS);
        assertThat(runUntil(greeting, 4 * SECOND)).containsExactly(
            new Event(3_375 * MILLIS, BuddyFrame.LEAN_LEFT),
            new Event(3_500 * MILLIS, BuddyFrame.LEAN_RIGHT),
            new Event(3_625 * MILLIS, BuddyFrame.WAVE_A),
            new Event(3_750 * MILLIS, BuddyFrame.WAVE_B),
            new Event(3_875 * MILLIS, BuddyFrame.HOP),
            new Event(4 * SECOND, BuddyFrame.IDLE));

        var waking = new BuddyAnimator(new Random(83));
        waking.shown(0);
        waking.tick(63 * SECOND);
        waking.greet(63_100 * MILLIS);
        assertThat(waking.frame()).isEqualTo(BuddyFrame.TUCK);
        waking.poke(63_200 * MILLIS);                     // a poke must not turn a greeting wake quiet
        waking.greet(63_200 * MILLIS);
        assertThat(waking.frame()).isEqualTo(BuddyFrame.TUCK);
        assertThat(waking.nextDueNanos()).hasValue(63_350 * MILLIS);
        assertThat(runUntil(waking, 63_600 * MILLIS)).containsExactly(
            new Event(63_350 * MILLIS, BuddyFrame.IDLE),
            new Event(63_600 * MILLIS, BuddyFrame.WAVE_A));
    }

    @Test void pokeAndGreetAreIgnoredWhileHidden() {
        var animator = new BuddyAnimator(new Random(89));
        animator.poke(SECOND);
        animator.greet(SECOND);
        animator.tick(2 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();

        animator.shown(0);
        spawned(animator, 0);
        animator.tick(30 * SECOND);
        animator.hidden();
        animator.poke(31 * SECOND);
        animator.greet(31 * SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
    }

    @Test void hiddenClearsEverything() {
        var greeting = new BuddyAnimator(new Random(31));
        greeting.shown(0);
        spawned(greeting, 0);
        greeting.hoverEntered(3 * SECOND);
        greeting.tick(3_500 * MILLIS);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.LEAN_RIGHT);
        greeting.hidden();
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(greeting.nextDueNanos()).isEmpty();
        greeting.shown(4 * SECOND);
        assertThat(greeting.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(greeting.nextDueNanos()).hasValue(4 * SECOND + BuddyAnimator.STEP_NANOS);
        spawned(greeting, 4 * SECOND);
        assertThat(greeting.nextDueNanos().orElseThrow() - 6 * SECOND)
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

    @Test void workingCyclesTheTypingFramesAndNeverFallsAsleep() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0L);
        spawned(animator, 0L);

        animator.setWorking(true);
        // Well past both the sit and sleep deadlines: a resting buddy would be asleep by now.
        List<Event> events = runUntil(animator, 90 * SECOND);

        assertThat(events).extracting(Event::frame)
            .contains(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B)
            .doesNotContain(BuddyFrame.SLEEP_A, BuddyFrame.SLEEP_B, BuddyFrame.SLEEP_C,
                BuddyFrame.TUCK, BuddyFrame.SIT);
    }

    @Test void clearingWorkingReturnsHimToTheRestingProgression() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0L);
        spawned(animator, 0L);
        animator.setWorking(true);
        runUntil(animator, 10 * SECOND);

        animator.setWorking(false);
        List<Event> after = runUntil(animator, 14 * SECOND);

        assertThat(after).extracting(Event::frame)
            .doesNotContain(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B, BuddyFrame.TYPE_REST);
        assertThat(animator.frame()).isIn(BuddyFrame.IDLE, BuddyFrame.BLINK, BuddyFrame.WINK);
    }

    /** A two-second spawn is short; cutting it off to type would look broken. */
    @Test void workingWaitsForTheSpawnToFinish() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0L);
        animator.setWorking(true);

        // Mid-spawn the wave owns the stage, however long the command has been running.
        for (int step = 1; step < BuddyAnimator.SPAWN_STEPS; step++) {
            animator.tick(step * BuddyAnimator.STEP_NANOS);
            assertThat(animator.frame()).as("step %d", step)
                .isNotIn(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B, BuddyFrame.TYPE_REST);
        }

        animator.tick(BuddyAnimator.SPAWN_STEPS * BuddyAnimator.STEP_NANOS);

        assertThat(animator.frame()).as("and he picks it up the moment the spawn ends")
            .isIn(BuddyFrame.TYPE_A, BuddyFrame.TYPE_B, BuddyFrame.TYPE_REST);
    }

    @Test void settingWorkingTwiceChangesNothing() {
        var animator = new BuddyAnimator(new Random(7));
        animator.shown(0L);
        spawned(animator, 0L);
        animator.setWorking(true);
        runUntil(animator, 5 * SECOND);
        BuddyFrame before = animator.frame();
        long due = animator.nextDueNanos().orElseThrow();

        animator.setWorking(true);

        assertThat(animator.frame()).isEqualTo(before);
        assertThat(animator.nextDueNanos()).hasValue(due);
    }
}
