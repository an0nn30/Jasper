package dev.jasper.app.application;

import dev.jasper.app.notifications.BuddyVisibility;
import dev.jasper.app.persistence.BuddyStateFile;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.config.*;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.awt.Font;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class BuddyIntegrationTest {
    @TempDir Path temp;
    private final Font font = new Font("Dialog", Font.PLAIN, 13);
    @Test void unavailablePresentationIsAttemptedOnlyOnce() {
        var fixture = new BuddyTestSupport(); var attempts = new AtomicInteger();
        var options = BuddyOptions.builder(font).build();
        var integration = new BuddyIntegration(options, fixture.companion(), new BuddyVisibility(),
            () -> { attempts.incrementAndGet(); return false; }, source -> true,
            listener -> { throw new AssertionError("No key listener for unavailable presentation"); });
        integration.window("window", true, false); integration.configured(true);
        integration.toggle(); integration.toggle(); integration.window("window", true, false);
        assertThat(attempts).hasValue(1);
        integration.close(); integration.close();
    }
    @Test void hideRetainsNoticesAndCloseUnregistersActivityExactlyOnce() {
        var fixture = new BuddyTestSupport(); var registrations = new AtomicInteger(); var removals = new AtomicInteger();
        var integration = new BuddyIntegration(BuddyOptions.builder(font).build(), fixture.companion(), new BuddyVisibility(),
            () -> true, source -> true, listener -> { registrations.incrementAndGet(); return removals::incrementAndGet; });
        var notice = new BuddyNotice(new BuddyNoticeId("test", 1), BuddyNotice.Kind.TASK, "build", BuddyNotice.State.DONE, () -> "done", () -> {});
        integration.companion().post(notice);
        integration.window("window", true, false); integration.window("window", true, true);
        integration.appearance(font.deriveFont(18f), false);
        assertThat(fixture.notices()).containsExactly(notice);
        integration.close(); integration.close(); integration.activity(); integration.greet(); integration.window("late",true,false);
        assertThat(registrations).hasValue(1); assertThat(removals).hasValue(1);
        assertThat(fixture.notices()).isEmpty();
    }
    @Test void savedPositionIsReadOnceAndOnlyDragCallbackWritesIt() throws Exception {
        Path state = temp.resolve("buddy.toml"); BuddyStateFile.write(state, new java.awt.Point(-70, 30));
        var options = BuddyIntegration.options(state, font, true, () -> {}, () -> {});
        assertThat(options.initialPosition()).contains(new BuddyPosition(-70, 30));
        var changed = options.toBuilder().primaryFont(font.deriveFont(18f)).dark(false).build();
        assertThat(BuddyStateFile.read(state)).contains(new java.awt.Point(-70, 30));
        changed.positionChanged().accept(new BuddyPosition(40, 50));
        assertThat(BuddyStateFile.read(state)).contains(new java.awt.Point(40, 50));
        assertThat(changed.initialPosition()).contains(new BuddyPosition(-70, 30));
    }
}
