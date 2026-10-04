package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CommandNotifierTest {
    private record Sent(String title, String detail) {}

    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<Sent> os = new ArrayList<>();
    private final List<Boolean> working = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private NotificationCenter center;
    private final UUID window = UUID.randomUUID();

    /** EDT: nothing fires by itself; a test runs the scheduled task when it wants the threshold to pass. */
    private CommandNotifier notifier(int seconds) {
        center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
        var notifier = new CommandNotifier(() -> Duration.ofSeconds(seconds), deck.companion(), center,
            (title, detail) -> os.add(new Sent(title, detail)), working::add,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
        for (String key : List.of("pane", "a", "b")) notifier.opened(key);
        return notifier;
    }

    private void passThreshold() { List.copyOf(scheduled).forEach(Runnable::run); }

    /** Each notification, newest first, as {@code group|SEVERITY|title|content|target|state}. */
    private List<String> notifications() {
        return center.history().stream().map(entry -> String.join("|", entry.request().group(), entry.request().severity().name(),
            entry.request().title(), entry.request().content(), entry.request().target().map(UUID::toString).orElse("-"),
            entry.expired() ? "expired" : "live")).toList();
    }

    /** Started in the pane the user is looking at, so the threshold is what promotes it. */
    private static final boolean WATCHED = true;
    private static final boolean UNWATCHED = false;

    private static final CommandNotice.Origin HIDDEN_TAB = new CommandNotice.Origin(true, true, false, false);
    private static final CommandNotice.Origin FOCUSED_PANE = new CommandNotice.Origin(true, true, true, true);
    private static final CommandNotice.Origin UNFOCUSED_SPLIT = new CommandNotice.Origin(true, true, true, false);

    @Test void theGroupIsLongRunningCommandsShownAsCards() {
        notifier(10);
        assertThat(center.group("jasper.commands")).contains(CommandNotifier.GROUP);
        assertThat(CommandNotifier.GROUP.name()).isEqualTo("Long-running commands");
        assertThat(center.mode("jasper.commands")).isEqualTo(DisplayMode.CARD);
    }

    @Test void aCommandThatPassesTheThresholdGetsARunningCardAndNothingElse() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("./gradlew build");
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.TASK);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
        });
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).containsExactly(true);
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

    @Test void finishingOutOfSightPostsTheOutcomeInThePanesWindowAndRemovesTheCard() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).as("Buddy keeps live work only").isEmpty();
        assertThat(working).containsExactly(true, false);
        assertThat(notifications()).containsExactly("jasper.commands|INFO|./gradlew build|Finished in 1m 12s|" + window + "|live");
    }

    @Test void aFailureIsAWarningThatCarriesItsExitStatus() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "make", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "make", OptionalInt.of(2), Duration.ofSeconds(45), HIDDEN_TAB, window, () -> {});
        assertThat(notifications()).containsExactly("jasper.commands|WARNING|make|Exited 2 · 45s|" + window + "|live");
    }

    @Test void clickingTheNotificationFocusesThePane() {
        CommandNotifier notifier = notifier(10);
        var focused = new ArrayList<String>();
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> focused.add("pane"));
        center.activate(center.history().getFirst().id());
        assertThat(focused).containsExactly("pane");
    }

    /** You were looking straight at it, so there is nothing to tell. */
    @Test void aCommandThatEndsInTheFocusedPaneIsNotPosted() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), FOCUSED_PANE, window, () -> {});
        assertThat(center.history()).isEmpty();
        assertThat(deck.notices()).isEmpty();
        assertThat(os).isEmpty();
    }

    /** The deliberate widening: a visible split pane you are not typing in still notifies. */
    @Test void aVisibleButUnfocusedSplitPaneStillNotifies() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), UNFOCUSED_SPLIT, window, () -> {});
        assertThat(center.history()).hasSize(1);
    }

    @Test void theOperatingSystemHearsOnlyWhileJasperIsInTheBackground() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("a", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        assertThat(os).as("the card says it in the foreground").isEmpty();
        presence = Delivery.Presence.BACKGROUND;
        notifier.finished("b", "./gradlew build", OptionalInt.of(1), Duration.ofSeconds(72), HIDDEN_TAB, window, () -> {});
        assertThat(os).containsExactly(new Sent("./gradlew build", "Exited 1 · 1m 12s"));
        assertThat(center.history()).as("history and unread either way").hasSize(2);
        assertThat(center.unread()).isEqualTo(2);
    }

    @Test void aShortCommandLeavesNothingBehind() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "ls", () -> 0L, () -> {}, WATCHED);
        notifier.finished("pane", "ls", OptionalInt.of(0), Duration.ofSeconds(2), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).isEmpty();
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
        assertThat(working).as("it never passed the threshold").isEmpty();
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
    }

    @Test void aZeroThresholdTurnsTheWholeFeatureOff() {
        CommandNotifier notifier = notifier(0);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);
        notifier.hidden("pane");
        notifier.finished("pane", "./gradlew build", OptionalInt.of(0), Duration.ofHours(1), HIDDEN_TAB, window, () -> {});
        assertThat(deck.notices()).isEmpty();
        assertThat(center.history()).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void aSecondLongCommandDoesNotRestartTheTypingAndTheLastOneEndsIt() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "one", () -> 0L, () -> {}, WATCHED);
        notifier.started("b", "two", () -> 0L, () -> {}, WATCHED);
        passThreshold();
        assertThat(working).containsExactly(true);
        notifier.finished("a", "one", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(working).as("one still running").containsExactly(true);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("two");
        notifier.finished("b", "two", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(working).containsExactly(true, false);
    }

    @Test void aPaneClosingMidCommandRemovesItsCardStopsTheTypingAndPostsNothing() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "sleep 600", () -> Duration.ofSeconds(72).toNanos(), () -> {}, WATCHED);
        passThreshold();
        notifier.closed("pane");
        assertThat(deck.notices()).isEmpty();
        assertThat(working).containsExactly(true, false);
        assertThat(center.history()).isEmpty();
    }

    @Test void closingThePaneExpiresItsCommandsNotifications() {
        CommandNotifier notifier = notifier(10);
        notifier.finished("pane", "make", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        notifier.finished("pane", "make test", OptionalInt.of(1), Duration.ofSeconds(40), HIDDEN_TAB, window, () -> {});
        notifier.finished("a", "other", OptionalInt.of(0), Duration.ofSeconds(30), HIDDEN_TAB, window, () -> {});
        notifier.closed("pane");
        assertThat(notifications()).containsExactly(
            "jasper.commands|INFO|other|Finished in 30s|" + window + "|live",
            "jasper.commands|WARNING|make test|Exited 1 · 40s|" + window + "|expired",
            "jasper.commands|INFO|make|Finished in 30s|" + window + "|expired");
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
        notifier.started("pane", "first", () -> 0L, () -> {}, UNWATCHED);
        notifier.started("pane", "second", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second");
        assertThat(working).as("the first command's typing ends before the second's begins").containsExactly(true, false, true);
    }

    @Test void multiLineCommandsCollapseForTheTitle() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "echo a\necho b", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices().getFirst().title()).isEqualTo("echo a ↵ echo b");
        notifier.finished("pane", "echo a\necho b", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(center.history().getFirst().request().title()).isEqualTo("echo a ↵ echo b");
    }

    @Test void aCommandWithNothingVisibleToSayIsCalledCommand() {
        CommandNotifier notifier = notifier(10);
        String invisible = "  " + new String(new int[] {0x200B, 0x2060}, 0, 2) + " ";
        notifier.started("pane", invisible, () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices().getFirst().title()).isEqualTo("Command");
        notifier.finished("pane", invisible, OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        assertThat(center.history().getFirst().request().title()).isEqualTo("Command");
    }

    @Test void durationsReadAsPeopleSayThem() {
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(72))).isEqualTo("1m 12s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(120))).isEqualTo("2m");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(7500))).isEqualTo("2h 5m");
    }

    /** Out of sight is the whole reason a card exists, so there is nothing to wait for. */
    @Test void aCommandStartedInAPaneYouAreNotWatchingShowsAtOnce() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, UNWATCHED);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("it did not wait for the threshold").isEmpty();
        assertThat(working).containsExactly(true);
    }

    @Test void backgroundingThePaneShowsWhateverIsRunningInItImmediately() {
        CommandNotifier notifier = notifier(10);
        notifier.started("pane", "./gradlew build", () -> 0L, () -> {}, WATCHED);
        assertThat(deck.notices()).as("watched, so it is waiting for the threshold").isEmpty();
        notifier.hidden("pane");
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("./gradlew build");
        assertThat(scheduled).as("its timer was cancelled").isEmpty();
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

    @Test void programTitleChangesUpdateOnlyTheirPaneWithoutReordering() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker a", () -> 0L, () -> {}, UNWATCHED);
        notifier.started("b", "worker b", () -> 0L, () -> {}, UNWATCHED);
        int generation = deck.generation();
        notifier.titleChanged("a", "Reviewing database migration");
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("worker b", "Reviewing database migration");
        assertThat(deck.generation()).isEqualTo(generation);
    }

    @Test void titlesReceivedBeforeTheThresholdAreUsedWhenTheCardAppears() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, WATCHED);
        notifier.titleChanged("a", "Indexing the repository");
        assertThat(deck.notices()).isEmpty();
        passThreshold();
        assertThat(deck.notices().getFirst().title()).isEqualTo("Indexing the repository");
    }

    /** Completion names the command that ran, never the program or prompt title around it. */
    @Test void theNotificationNamesTheCommandThatRanNotItsTitle() {
        CommandNotifier notifier = notifier(10);
        notifier.started("a", "worker", () -> 0L, () -> {}, UNWATCHED);
        notifier.titleChanged("a", "Review complete");
        notifier.finished("a", "worker", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, window, () -> {});
        notifier.titleChanged("a", "user@host: ~/projects");
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.request().title()).isEqualTo("worker"));
        assertThat(deck.notices()).as("a prompt title cannot bring the card back").isEmpty();
    }

    @Test void failedCommandsRetainArgumentsAndSanitizeMultilineTitles() {
        CommandNotifier notifier = notifier(10);
        String command = "  printf 'hello'\r\necho goodbye  ";
        notifier.started("pane", command, () -> 0L, () -> {}, WATCHED);
        notifier.titleChanged("pane", "/tmp/project");
        presence = Delivery.Presence.BACKGROUND;
        notifier.finished("pane", command, OptionalInt.of(2), Duration.ofSeconds(12), HIDDEN_TAB, window, () -> {});
        assertThat(os).containsExactly(new Sent("printf 'hello' ↵ echo goodbye", "Exited 2 · 12s"));
        assertThat(center.history().getFirst().request().title()).isEqualTo("printf 'hello' ↵ echo goodbye");
    }

    @Test void changingATitleDoesNotResurrectADismissedRunningCard() {
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
                notifier.finished(key, "build", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, window, () -> {});
            }
            if (scenario == 3) notifier.close(); else notifier.closed(key);
            int generation = deck.generation(), sent = os.size(), changes = working.size(), posted = center.history().size();
            saved.run();
            notifier.started(key, "late", () -> 20_000_000_000L, () -> {}, UNWATCHED);
            notifier.finished(key, "late", OptionalInt.of(0), Duration.ofSeconds(20), HIDDEN_TAB, window, () -> {});
            notifier.hidden(key); notifier.titleChanged(key, "late");
            assertThat(deck.generation()).isEqualTo(generation);
            assertThat(os).hasSize(sent);
            assertThat(working).hasSize(changes);
            assertThat(center.history()).hasSize(posted);
            notifier.close(); notifier.close();
        }
    }
}
