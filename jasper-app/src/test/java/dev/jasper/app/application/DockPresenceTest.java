package dev.jasper.app.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DockPresenceTest {
    @Test void aPlainLaunchSwitchesOnlyWhenWhetherAWindowIsOpenChanges() {
        List<Boolean> shown = new ArrayList<>();
        var presence = new DockPresence(new DockControl(shown::add, () -> true, () -> false), true);
        presence.windows(true);
        assertThat(shown).as("a plain launch is already in the Dock").isEmpty();
        presence.windows(false);
        presence.windows(false);
        presence.windows(true);
        presence.windows(true);
        assertThat(shown).containsExactly(false, true);
        assertThat(presence.visible()).isTrue();
    }

    @Test void aBackgroundLaunchStartsHiddenAndItsFirstWindowBringsJasperBack() {
        List<Boolean> shown = new ArrayList<>();
        var presence = new DockPresence(new DockControl(shown::add, () -> true, () -> false), false);
        presence.windows(false);
        assertThat(shown).as("a login launch is hidden already").isEmpty();
        presence.windows(true);
        assertThat(shown).containsExactly(true);
    }

    @Test void preparingAndThePowerOffQuestionGoToTheControl() {
        var prepared = new AtomicInteger();
        var presence = new DockPresence(new DockControl(visible -> { }, () -> {
            prepared.incrementAndGet();
            return true;
        }, () -> true), true);
        presence.prepare();
        assertThat(prepared).hasValue(1);
        assertThat(presence.poweringOff()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> new DockControl(null, () -> true, () -> false));
        assertThatNullPointerException().isThrownBy(() -> new DockControl(visible -> { }, null, () -> false));
        assertThatNullPointerException().isThrownBy(() -> new DockControl(visible -> { }, () -> true, null));
        assertThatNullPointerException().isThrownBy(() -> new DockPresence(null, true));
    }

    @Test void thePresenceRemembersThatTheBridgeIsPrepared() {
        var presence = new DockPresence(new DockControl(visible -> { }, () -> true, () -> false), true);
        assertThat(presence.prepared()).as("until then a log-out cannot be told from Quit").isFalse();
        presence.prepare();
        assertThat(presence.prepared()).isTrue();
    }

    @Test void aBridgeThatReportsFailureLeavesThePresenceUnprepared() {
        // The bridge failed to load, or loaded without a power-off observer: poweringOff would stay false for ever.
        var attempts = new AtomicInteger();
        var presence = new DockPresence(new DockControl(visible -> { }, () -> {
            attempts.incrementAndGet();
            return false;
        }, () -> false), true);
        presence.prepare();
        assertThat(attempts).hasValue(1);
        assertThat(presence.prepared()).as("Quit stays a real quit").isFalse();
    }

    @Test void aBridgeThatFailsToPrepareLeavesThePresenceUnprepared() {
        var presence = new DockPresence(new DockControl(visible -> { }, () -> { throw new IllegalStateException("no bridge"); }, () -> false), true);
        assertThatIllegalStateException().isThrownBy(presence::prepare);
        assertThat(presence.prepared()).as("Quit stays a real quit").isFalse();
    }
}
