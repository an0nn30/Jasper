package dev.jasper.app.notifications;

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
