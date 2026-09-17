package dev.jasper.app;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandNotifierTest {
    private record Sent(String title, String detail, boolean succeeded) {}

    private final List<Sent> buddy = new ArrayList<>();
    private final List<Sent> os = new ArrayList<>();
    private final List<Boolean> working = new ArrayList<>();

    private CommandNotifier notifier(int seconds) {
        return new CommandNotifier(() -> Duration.ofSeconds(seconds),
            (title, detail, succeeded, activate) -> buddy.add(new Sent(title, detail, succeeded)),
            (title, detail, succeeded, activate) -> os.add(new Sent(title, detail, succeeded)),
            working::add);
    }

    private static final CommandNotice.Origin HIDDEN_TAB = new CommandNotice.Origin(true, true, false);
    private static final CommandNotice.Origin VISIBLE_TAB = new CommandNotice.Origin(true, true, true);

    @Test void aLongCommandInAHiddenTabReachesTheBuddyWithHowLongItTook() {
        notifier(10).finished("./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), HIDDEN_TAB, true, () -> {});

        assertThat(buddy).containsExactly(new Sent("./gradlew build", "Finished in 1m 12s", true));
        assertThat(os).isEmpty();
    }

    @Test void aFailureSaysSoAndCarriesItsExitStatus() {
        notifier(10).finished("make", OptionalInt.of(2), Duration.ofSeconds(45), HIDDEN_TAB, true, () -> {});

        assertThat(buddy).containsExactly(new Sent("make", "Exited 2 · 45s", false));
    }

    @Test void aTabYouAreLookingAtIsNotWorthInterruptingFor() {
        notifier(10).finished("./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), VISIBLE_TAB, true, () -> {});

        assertThat(buddy).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void aShortCommandNotifiesNobody() {
        notifier(10).finished("ls", OptionalInt.of(0), Duration.ofSeconds(2), HIDDEN_TAB, true, () -> {});

        assertThat(buddy).isEmpty();
        assertThat(os).isEmpty();
    }

    @Test void theOperatingSystemTakesOverWhenTheBuddyIsAway() {
        notifier(10).finished("./gradlew build", OptionalInt.of(0), Duration.ofSeconds(72), HIDDEN_TAB, false, () -> {});

        assertThat(os).containsExactly(new Sent("./gradlew build", "Finished in 1m 12s", true));
        assertThat(buddy).isEmpty();
    }

    /** The History palette already renders a multi-line command this way; the bubble matches it. */
    @Test void multiLineCommandsCollapseForTheTitle() {
        notifier(10).finished("echo a\necho b", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, true, () -> {});

        assertThat(buddy).singleElement().extracting(Sent::title).isEqualTo("echo a ↵ echo b");
    }

    @Test void durationsReadAsPeopleSayThem() {
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(72))).isEqualTo("1m 12s");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(120))).isEqualTo("2m");
        assertThat(CommandNotifier.humanize(Duration.ofSeconds(7500))).isEqualTo("2h 5m");
    }

    /** The typing animation starts when a command passes the threshold, not when it starts. */
    @Test void theBuddyWorksWhileALongCommandIsInFlightAndStopsWhenTheLastOneEnds() {
        CommandNotifier notifier = notifier(10);

        notifier.passedThreshold();
        assertThat(working).containsExactly(true);
        notifier.passedThreshold();
        assertThat(working).as("a second long command does not restart the animation").containsExactly(true);

        notifier.finished("a", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, true, () -> {});
        assertThat(working).as("one still running").containsExactly(true);

        notifier.finished("b", OptionalInt.of(0), Duration.ofSeconds(11), HIDDEN_TAB, true, () -> {});
        assertThat(working).containsExactly(true, false);
    }

    @Test void aPaneClosingDropsItsRunningCommand() {
        CommandNotifier notifier = notifier(10);
        notifier.passedThreshold();

        notifier.abandoned();

        assertThat(working).containsExactly(true, false);
    }

    @Test void aZeroThresholdTurnsTheWholeFeatureOff() {
        notifier(0).finished("./gradlew build", OptionalInt.of(0), Duration.ofHours(1), HIDDEN_TAB, true, () -> {});

        assertThat(buddy).isEmpty();
        assertThat(os).isEmpty();
    }
}
