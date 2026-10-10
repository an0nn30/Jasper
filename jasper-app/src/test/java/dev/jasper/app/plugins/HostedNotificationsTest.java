package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedNotificationsTest {
    private final NotificationsFixture fixture = new NotificationsFixture();
    private final List<Runnable> queued = new ArrayList<>();

    /** EDT: a plugin's notifications whose work for the UI thread waits in {@code queued}, so a test can order it. */
    private HostedNotifications hosted() {
        return new HostedNotifications("dev.example.tool", "Tool", fixture.host, new Containment(() -> true), queued::add, () -> false, name -> new javax.swing.ImageIcon(), name -> {});
    }

    private void runQueued() { while (!queued.isEmpty()) queued.removeFirst().run(); }

    private UUID idOf(String title) {
        return fixture.center.history().stream().filter(entry -> entry.request().title().equals(title)).findFirst().orElseThrow().id();
    }

    @Test void theDefaultAndActivitiesGroupsAreDeclaredWithThePluginAsOwner() {
        hosted();
        assertThat(fixture.center.groups()).extracting(group -> group.id() + "|" + group.name() + "|" + group.owner() + "|" + group.defaultMode())
            .containsExactly("dev.example.tool|Tool|dev.example.tool|CARD",
                "dev.example.tool.activities|Tool activities|dev.example.tool|CARD");
    }

    @Test void expiringBeforeThePostLandsExpiresItWhenItLands() {
        var hosted = hosted();
        var handle = hosted.group().info("Saved", "");
        handle.expire();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).as("the post waits for the UI thread").isEmpty();
        runQueued();
        assertThat(fixture.center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.expired()).isTrue();
            assertThat(entry.request().source()).isEqualTo("dev.example.tool");
        });
    }

    @Test void aPostThatLandsAfterThePluginStoppedExpiresAtOnce() {
        var hosted = hosted();
        var handle = hosted.group().info("Late", "");
        hosted.close();
        runQueued();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).as("a stopped plugin's post is dropped").isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> hosted.group().info("After", ""));
        assertThatIllegalStateException().isThrownBy(() -> hosted.group(new GroupSpec("dev.example.tool.x", "X", DisplayMode.CARD)));
    }

    @Test void stoppingExpiresWhatItPostedAndTheCenterTellsHandlesTheirFate() {
        var hosted = hosted();
        var updated = hosted.group().post(NotificationSpec.of(Severity.INFO, "Update ready").withAction(NotificationAction.of("Install", () -> { })));
        var removed = hosted.group().info("Removed", "");
        var stays = hosted.group().info("Stays", "");
        runQueued();
        fixture.center.runAction(idOf("Update ready"), 0);
        fixture.center.remove(idOf("Removed"));
        assertThat(updated.expired()).as("Install expires it").isTrue();
        assertThat(removed.expired()).as("removed from history").isTrue();
        assertThat(stays.expired()).isFalse();
        hosted.close();
        assertThat(stays.expired()).isTrue();
        assertThat(fixture.center.find(idOf("Stays")).orElseThrow().expired()).isTrue();
    }

    @Test void aPostToAMutedGroupReportsItselfExpired() {
        var hosted = hosted();
        var quiet = hosted.group(new GroupSpec("dev.example.tool.quiet", "Quiet", DisplayMode.OFF));
        runQueued();
        var handle = quiet.info("Unseen", "");
        runQueued();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).isEmpty();
    }
}
