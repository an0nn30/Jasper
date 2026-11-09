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
    /** The handles every plugin of the host holds on the center's entries, as a host shares them. */
    private final HostedNotifications.Holds holds = new HostedNotifications.Holds(fixture.center);

    /** EDT: a plugin's notifications whose work for the UI thread waits in {@code queued}, so a test can order it. */
    private HostedNotifications hosted() { return hosted("dev.example.tool", "Tool"); }

    private HostedNotifications hosted(String id, String name) {
        return new HostedNotifications(id, name, fixture.host, holds, new Containment(() -> true), queued::add, () -> false, icon -> new javax.swing.ImageIcon(), icon -> {});
    }

    private static List<String> labels(dev.jasper.app.notifications.Notification entry) {
        return entry.request().actions().stream().map(dev.jasper.app.notifications.NotificationAction::label).toList();
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

    /**
     * SDK 0.30.0: a handle removed before its post lands leaves nothing in history: a plain post is not made, so a repeat
     * does not count on its earlier notification again, and a post with a cause, which lands as its incident is
     * recorded, is removed as it lands.
     */
    @Test void aRemoveBeforeThePostLandsRemovesItWhenItLands() {
        var hosted = new HostedNotifications("dev.example.tool", "Tool", new PluginRuntime.NotificationHost(fixture.center,
            caused -> queued.add(() -> caused.posted().accept(fixture.center.post(caused.request()))), (title, details) -> { }),
            holds, new Containment(() -> true), queued::add, () -> false, name -> new javax.swing.ImageIcon(), name -> {});
        var kept = hosted.group().info("Same", "");
        runQueued();
        var plain = hosted.group().info("Saved", "");
        var repeat = hosted.group().info("Same", "");
        var caused = hosted.group().error("Could not save", new IllegalStateException("disk full"));
        plain.remove();
        repeat.remove();
        caused.remove();
        assertThat(List.of(plain, repeat, caused)).allSatisfy(handle -> assertThat(handle.expired()).isTrue());
        runQueued();
        assertThat(fixture.center.history()).as("the earlier notification alone, not counted again").singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Same");
            assertThat(entry.count()).isEqualTo(1);
            assertThat(entry.expired()).isFalse();
        });
        assertThat(kept.expired()).isFalse();
    }

    /**
     * Ruling V9: plugin {@code dev.example} may declare the group {@code dev.example.tool}, the default group of plugin
     * {@code dev.example.tool}, so the two plugins' same-text posts count on one entry. Removing one plugin's handle, or
     * stopping that plugin, leaves the entry to the other plugin's handle, with its links; it leaves history once every
     * handle on it has been removed.
     */
    @Test void anEntryTwoPluginsHoldStaysForTheOtherWithItsLinks() {
        var tool = hosted("dev.example.tool", "Tool");
        var outer = hosted("dev.example", "Example");
        var shared = outer.group(new GroupSpec("dev.example.tool", "Tool", DisplayMode.CARD));
        runQueued();
        var ran = new ArrayList<String>();
        var inner = tool.group().post(NotificationSpec.of(Severity.INFO, "Same")
            .withAction(new NotificationAction("Tool", () -> ran.add("tool"), false)));
        var mine = shared.post(NotificationSpec.of(Severity.INFO, "Same")
            .withAction(new NotificationAction("Example", () -> ran.add("example"), false)));
        runQueued();
        assertThat(fixture.center.history()).as("one entry, with the newer post's links").singleElement()
            .satisfies(entry -> assertThat(labels(entry)).containsExactly("Example"));
        mine.remove();
        runQueued();
        assertThat(mine.expired()).isTrue();
        assertThat(inner.expired()).isFalse();
        assertThat(fixture.center.history()).as("the other plugin's handle keeps it, with its links").singleElement().satisfies(entry -> {
            assertThat(entry.expired()).isFalse();
            assertThat(labels(entry)).containsExactly("Tool");
            fixture.center.runAction(entry.id(), 0);
        });
        assertThat(ran).containsExactly("tool");
        var again = shared.post(NotificationSpec.of(Severity.INFO, "Same")
            .withAction(new NotificationAction("Example", () -> ran.add("example"), false)));
        runQueued();
        outer.close();
        assertThat(again.expired()).isTrue();
        assertThat(fixture.center.history()).as("stopping leaves it to the other plugin too").singleElement().satisfies(entry -> {
            assertThat(entry.expired()).isFalse();
            assertThat(labels(entry)).containsExactly("Tool");
        });
        inner.remove();
        runQueued();
        assertThat(fixture.center.history()).as("held by a stopped plugin's handle alone, it expires").singleElement()
            .satisfies(entry -> assertThat(entry.expired()).isTrue());
        again.remove();
        runQueued();
        assertThat(fixture.center.history()).as("and leaves once that handle is removed too").isEmpty();
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
