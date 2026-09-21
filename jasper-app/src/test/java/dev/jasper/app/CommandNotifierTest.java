package dev.jasper.app;

import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(EdtTestExtension.class)
class CommandNotifierTest {
    private record Sent(String title, String detail) {}

    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<Sent> os = new ArrayList<>();
    private final List<Boolean> working = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();

    /** Nothing fires by itself: a test runs the scheduled task when it wants the threshold to pass. */
    private CommandNotifier notifier(int seconds) {
        var notifier = new CommandNotifier(() -> Duration.ofSeconds(seconds), deck.companion(),
            (title, detail) -> os.add(new Sent(title, detail)), working::add,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
        for (String key : List.of("pane", "a", "b")) notifier.opened(key);
        return notifier;
    }

    private void passThreshold() {
        List.copyOf(scheduled).forEach(Runnable::run);
    }

    /** Started in the pane the user is looking at, so the threshold is what promotes it. */
    private static final boolean WATCHED = true;
    private static final boolean UNWATCHED = false;

    private static final CommandNotice.Origin HIDDEN_TAB = new CommandNotice.Origin(true, true, false, false);
    private static final CommandNotice.Origin FOCUSED_PANE = new CommandNotice.Origin(true, true, true, true);
    private static final CommandNotice.Origin UNFOCUSED_SPLIT = new CommandNotice.Origin(true, true, true, false);

    @Test void aCommandThatPassesTheThresholdGetsARunningCardAndNoNotification() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();

        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("./gradlew build");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
        });
        assertThat(os).isEmpty();
        assertThat(working).containsExactly(true);
    }

    /** The card is status, not an interruption: it appears whatever has focus. */
    @Test void theRunningCardAppearsEvenWhenYouAreLookingRightAtThePane() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            FOCUSED_PANE, () -> {});

        assertThat(deck.notices()).singleElement()
            .extracting(BuddyNotice::state).isEqualTo(BuddyNotice.State.DONE);
        assertThat(os).as("the pane had focus, so the OS is not involved").isEmpty();
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

    @Test void finishingReplacesTheRunningCardInPlaceRatherThanAddingASecond() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> {});

        assertThat(deck.size()).isEqualTo(1);
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(working).containsExactly(true, false);
    }

    @Test void aFailureSaysSoAndCarriesItsExitStatus() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "make", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(2), Duration.ofSeconds(45), HIDDEN_TAB, () -> {});

        assertThat(deck.notices().getFirst()).satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED);
            assertThat(notice.detail().get()).isEqualTo("Exited 2 · 45s");
        });
    }

    @Test void aCommandThatFinishesOutOfSightAlsoReachesTheOperatingSystem() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> {});

        assertThat(os).containsExactly(new Sent("./gradlew build", "Finished in 1m 12s"));
    }

    /** The deliberate widening: a visible split pane you are not typing in still notifies. */
    @Test void aVisibleButUnfocusedSplitPaneStillNotifies() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "make", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), UNFOCUSED_SPLIT, () -> {});

        assertThat(os).hasSize(1);
    }

    @Test void aShortCommandLeavesNoCardAndNotifiesNobody() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "ls", () -> 0L, () -> {}, WATCHED);
        notifier.finished("pane", "ls", OptionalInt.of(0), Duration.ofSeconds(2), HIDDEN_TAB, () -> {});

        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).as("it never passed the threshold").isEmpty();
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    @Test void aZeroThresholdTurnsTheWholeFeatureOff() {
        CommandNotifier notifier = notifier(0);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofHours(1),
            HIDDEN_TAB, () -> {});

        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void aSecondLongCommandDoesNotRestartTheTypingAndTheLastOneEndsIt() {
        CommandNotifier notifier = notifier(10);

        notifier.started("a", "one", () -> 0L, () -> {}, WATCHED);
        notifier.started("b", "two", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        assertThat(working).containsExactly(true);

        notifier.finished("a", "one", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, () -> {});
        assertThat(working).as("one still running").containsExactly(true);

        notifier.finished("b", "two", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, () -> {});
        assertThat(working).containsExactly(true, false);
    }

    /** Never removed: the drawer is what you look at to remember, so a closed pane leaves its card. */
    @Test void aPaneClosingOrphansItsCardAndStopsTheTyping() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> {}, WATCHED);
        passThreshold();

        notifier.closed("pane");

        assertThat(deck.size()).isEqualTo(1);
        assertThat(deck.notices().getFirst().orphaned()).isTrue();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Stopped after 1m 12s");
        assertThat(working).containsExactly(true, false);
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
        notifier.started("pane", "first", () -> 0L, () -> {}, WATCHED);

        notifier.started("pane", "second", () -> 0L, () -> {}, WATCHED);
        passThreshold();

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second");
        assertThat(working).as("one pane, one typing buddy").containsExactly(true);
    }

    @Test void multiLineCommandsCollapseForTheTitle() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "echo a\necho b", () -> 0L, () -> {}, WATCHED);
        passThreshold();

        assertThat(deck.notices().getFirst().title()).isEqualTo("echo a ↵ echo b");
    }

    @Test void durationsReadAsPeopleSayThem() {
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(72))).isEqualTo("1m 12s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(120))).isEqualTo("2m");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(7500))).isEqualTo("2h 5m");
    }

    /**
     * The spec's whole reason for orphaning being a lost action rather than a fourth state: closing
     * the pane afterwards must not rewrite what happened.
     */
    @Test void closingThePaneAfterTheCommandFinishedKeepsWhatItSaid() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            HIDDEN_TAB, () -> {});

        notifier.closed("pane");

        BuddyNotice card = deck.notices().getFirst();
        assertThat(card.state()).isEqualTo(BuddyNotice.State.DONE);
        assertThat(card.detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(card.orphaned()).as("there is nowhere left to click to").isTrue();
    }

    /** A running card is frozen at however long it had actually been going, not at zero. */
    @Test void closingThePaneMidCommandFreezesTheRealElapsedTime() {
        CommandNotifier notifier = notifier(10);
        long[] elapsed = {Duration.ofSeconds(11).toNanos()};
        notifier.started("pane", "sleep 600", () -> elapsed[0], () -> {}, WATCHED);
        passThreshold();
        elapsed[0] = Duration.ofSeconds(154).toNanos();

        notifier.closed("pane");

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Stopped after 2m 34s");
    }

    /** You were watching it happen; he does not need to tell you about it. */
    @Test void aCommandThatEndsUnderYourEyesNeverReachesTheColumn() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72),
            FOCUSED_PANE, () -> {});

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).as("but it is still in the drawer").hasSize(1);
    }

    @Test void aCommandThatEndsOutOfSightWaitsInTheColumnUntilYouLook() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
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
        for (String key : List.of("a", "b")) notifier.started(key, "cmd " + key, () -> 0L, () -> {}, WATCHED);
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
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> {}, WATCHED);
        passThreshold();

        notifier.closed("pane");

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).hasSize(1);
        assertThat(deck.notices().getFirst().orphaned()).isTrue();
    }

    @Test void aRunningCommandStaysInTheColumnEvenWhileYouWatchIt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> 0L, () -> {}, WATCHED);
        passThreshold();

        notifier.looked("pane");

        assertThat(deck.column()).hasSize(1);
    }

    @Test void everyNoticeThisProducerPostsIsATask() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();

        assertThat(deck.notices().getFirst().kind()).isEqualTo(BuddyNotice.Kind.TASK);
    }

    /** Out of sight is the whole reason a bubble exists, so there is nothing to wait for. */
    @Test void aCommandStartedInAPaneYouAreNotWatchingShowsAtOnce() {
        CommandNotifier notifier = notifier(10);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("it did not wait for the threshold").isEmpty();
        assertThat(working).containsExactly(true);
    }

    @Test void backgroundingThePaneShowsWhateverIsRunningInItImmediately() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        assertThat(deck.column()).as("watched, so it is waiting for the threshold").isEmpty();

        notifier.hidden("pane");

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    /** The threshold is the fallback for a command running in the pane you are still watching. */
    @Test void aWatchedCommandStillGetsItsBubbleWhenItRunsLongEnough() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        assertThat(deck.column()).isEmpty();

        passThreshold();

        assertThat(deck.column()).hasSize(1);
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

    /** Zero still means off, however you leave the pane. */
    @Test void aZeroThresholdIgnoresBackgroundingToo() {
        CommandNotifier notifier = notifier(0);

        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);
        notifier.hidden("pane");

        assertThat(deck.notices()).isEmpty();
    }
    @Test void programTitleChangesUpdateOnlyTheirPaneWithoutReorderingOrReacknowledging() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker a", () -> 0L, () -> {}, UNWATCHED);
        notifier.started("b", "worker b", () -> 0L, () -> {}, UNWATCHED);
        deck.acknowledge(new BuddyNoticeId(CommandNotifier.SOURCE, "a"));
        int generation = deck.generation();
        notifier.titleChanged("a", "Reviewing database migration");
        assertThat(deck.notices()).extracting(BuddyNotice::title)
            .containsExactly("worker b", "Reviewing database migration");
        assertThat(deck.generation()).isEqualTo(generation);
        assertThat(deck.acknowledged(new BuddyNoticeId(CommandNotifier.SOURCE, "a"))).isTrue();
        assertThat(working).containsExactly(true);
    }

    @Test void titlesReceivedBeforeTheThresholdAreUsedWhenTheBubbleAppears() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, WATCHED);
        notifier.titleChanged("a", "Indexing the repository");
        assertThat(deck.notices()).isEmpty();
        passThreshold();
        assertThat(deck.notices().getFirst().title()).isEqualTo("Indexing the repository");
    }

    @Test void finishedNoticesKeepTheirFinalProgramTitleAfterTheShellChangesBackToItsPrompt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, UNWATCHED);
        notifier.titleChanged("a", "Review complete");
        notifier.finished("a", "worker", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, () -> {});
        notifier.titleChanged("a", "user@host: ~/projects");
        assertThat(deck.notices().getFirst().title()).isEqualTo("Review complete");
        assertThat(os).containsExactly(new Sent("Review complete", "Finished in 11s"));
    }

    @Test void changingATitleDoesNotResurrectADismissedRunningNotice() {
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
                notifier.finished(key, "build", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, () -> {});
            }
            if (scenario == 3) notifier.close(); else notifier.closed(key);
            int generation = deck.generation(), sent = os.size(), changes = working.size();
            saved.run();
            notifier.started(key, "late", () -> 20_000_000_000L, () -> {}, UNWATCHED);
            notifier.finished(key, "late", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, () -> {});
            notifier.hidden(key); notifier.looked(key); notifier.titleChanged(key, "late");
            assertThat(deck.generation()).isEqualTo(generation);
            assertThat(os).hasSize(sent); assertThat(working).hasSize(changes);
            notifier.close(); notifier.close();
        }
    }
}
