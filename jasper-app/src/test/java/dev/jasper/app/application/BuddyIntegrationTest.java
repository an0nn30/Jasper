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
    @Test void terminalAndPluginProducersShareWorkingIntentUntilBothStop() {
        var fixture = new BuddyTestSupport();
        var integration = new BuddyIntegration(BuddyOptions.builder(font).build(), fixture.companion(), new BuddyVisibility(),
            () -> false, source -> true, listener -> () -> {});
        var notifier = new dev.jasper.app.notifications.ActivityNotifier(fixture.companion(), () -> {}, integration::pluginWorking);
        var id = java.util.UUID.randomUUID();
        notifier.started("p", id, "work", "");
        assertThat(fixture.working()).isTrue();
        integration.terminalWorking(true);
        notifier.needsInput("p", id, "choose");
        assertThat(fixture.working()).isTrue();
        integration.terminalWorking(false);
        assertThat(fixture.working()).isFalse();
        notifier.resumed("p", id, "continue"); integration.terminalWorking(true);
        notifier.ended("p", id);
        assertThat(fixture.working()).isTrue();
        integration.terminalWorking(false);
        assertThat(fixture.working()).isFalse();
        notifier.started("p", java.util.UUID.randomUUID(), "other", "");
        notifier.close();
        assertThat(fixture.working()).isFalse();
        integration.pluginWorking(true); integration.close();
        integration.terminalWorking(true); integration.pluginWorking(true);
        assertThat(fixture.working()).isFalse();
    }

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
    /**
     * Full windows plan 1 final review: the Buddy follows the most recently active window, and switching between two
     * showing windows never hides it (the new window counts before the old one goes), so a shown bubble stays; following a
     * minimized window, or none, hides it.
     */
    @Test void switchingBetweenShowingWindowsNeverHidesTheBuddy() {
        var fixture = new BuddyTestSupport(); var shows = new AtomicInteger(); var hides = new AtomicInteger();
        var integration = new BuddyIntegration(BuddyOptions.builder(font).build(), fixture.companion(), new BuddyVisibility(),
            () -> { shows.incrementAndGet(); return true; }, hides::incrementAndGet, source -> true, listener -> () -> {});
        var shown = new BuddyIntegration.WindowShown(true, false);
        integration.follow(null, "first", shown);
        var notice = new BuddyNotice(new BuddyNoticeId("test", 1), BuddyNotice.Kind.TASK, "build", BuddyNotice.State.RUNNING, () -> "busy", () -> {});
        integration.companion().post(notice);
        integration.follow("first", "second", shown);
        integration.follow("second", "first", shown);
        integration.follow("first", "first", shown);
        assertThat(hides).as("never hidden on a switch").hasValue(0);
        assertThat(shows).hasValue(4);
        assertThat(fixture.notices()).as("the bubble's notice stays").containsExactly(notice);
        integration.follow("first", "second", new BuddyIntegration.WindowShown(true, true));
        assertThat(hides).as("a minimized window hides it").hasValue(1);
        integration.follow("second", null, null);
        assertThat(hides).as("and so does no window").hasValue(2);
        integration.close();
    }
    @Test void hideRetainsNoticesAndCloseUnregistersActivityExactlyOnce() {
        var fixture = new BuddyTestSupport(); var registrations = new AtomicInteger(); var removals = new AtomicInteger();
        var integration = new BuddyIntegration(BuddyOptions.builder(font).build(), fixture.companion(), new BuddyVisibility(),
            () -> true, source -> true, listener -> { registrations.incrementAndGet(); return removals::incrementAndGet; });
        var notice = new BuddyNotice(new BuddyNoticeId("test", 1), BuddyNotice.Kind.TASK, "build", BuddyNotice.State.RUNNING, () -> "busy", () -> {});
        integration.companion().post(notice);
        integration.window("window", true, false); integration.window("window", true, true);
        integration.appearance(font.deriveFont(18f), false);
        assertThat(fixture.notices()).containsExactly(notice);
        integration.close(); integration.close(); integration.activity(); integration.greet(); integration.window("late",true,false);
        assertThat(registrations).hasValue(1); assertThat(removals).hasValue(1);
        assertThat(fixture.notices()).isEmpty();
    }
    /**
     * Plan 2 Task 6c: the Buddy adapter adapts appearance only through the supported facade values. It imports only the
     * supported top-level config, notice and view types (never Buddy internals), and a theme change replaces exactly the
     * primary font and the dark flag, keeping every other option (position, callbacks) as composed.
     */
    @Test void appearanceAdaptationUsesOnlySupportedFacadeValues() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("jasper.repoRoot"),
            "jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java"));
        var imports = source.lines().filter(line -> line.startsWith("import dev.jasper.buddy.")).toList();
        assertThat(imports).isNotEmpty().allSatisfy(line -> assertThat(line)
            .matches("import dev\\.jasper\\.buddy\\.(config|notice|view)\\.[A-Z]\\w*;"));
        assertThat(source).doesNotContain("buddy.internal");
        var fixture = new BuddyTestSupport();
        Runnable activate = () -> {}, toggle = () -> {};
        var original = BuddyOptions.builder(font).dark(true).activateHost(activate).toggleRequested(toggle)
            .initialPosition(new BuddyPosition(5, 6)).build();
        var integration = new BuddyIntegration(original, fixture.companion(), new BuddyVisibility(), () -> true, s -> true, l -> () -> {});
        var larger = font.deriveFont(18f);
        integration.appearance(larger, false);
        var applied = integration.options();
        assertThat(applied.primaryFont()).isEqualTo(larger);
        assertThat(applied.dark()).isFalse();
        assertThat(applied.activateHost()).isSameAs(original.activateHost());
        assertThat(applied.toggleRequested()).isSameAs(original.toggleRequested());
        assertThat(applied.positionChanged()).isSameAs(original.positionChanged());
        assertThat(applied.initialPosition()).isEqualTo(original.initialPosition());
        integration.close();
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
