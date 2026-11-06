package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeNotificationsTest {
    @Test void aStandaloneGroupRecordsPostsForAComponentUnderTest() {
        var notifications = new FakeNotifications();
        var group = notifications.group("dev.example.tool");
        var ran = new ArrayList<String>();
        group.info("Saved", "Three hosts");
        var handle = group.post(NotificationSpec.of(Severity.ERROR, "Could not copy").withContent("busy")
            .withCause(new IllegalStateException("busy")).withAction(NotificationAction.of("Retry", () -> ran.add("retry"))));
        assertThat(notifications.lines()).containsExactly("dev.example.tool|INFO|Saved|Three hosts",
            "dev.example.tool|ERROR|Could not copy|busy|IllegalStateException");
        assertThat(notifications.groups()).containsExactly("dev.example.tool|dev.example.tool|CARD");
        assertThat(notifications.runAction("Could not copy", "Retry")).isTrue();
        assertThat(ran).containsExactly("retry");
        assertThat(handle.expired()).as("Retry expires it").isTrue();
        assertThat(notifications.runAction("Could not copy", "Retry")).isFalse();
        assertThat(notifications.runAction("Saved", "Nothing")).isFalse();
    }

    /**
     * SDK 0.30.0: as in Jasper, a post with the same text at most a minute after another counts on that post, which
     * moves last and takes the newest spec; it leaves history once each of its handles has been removed, and stays listed
     * as removed.
     */
    @Test void aRepeatCountsOnItsPostWhichLeavesHistoryOnceEachHandleIsRemoved() {
        var notifications = new FakeNotifications();
        var group = notifications.group("dev.example.tool");
        var first = group.info("Same", "text");
        group.info("Other", "");
        var again = group.post(NotificationSpec.of(Severity.INFO, "Same").withContent("text")
            .withAction(NotificationAction.of("Open", () -> { })));
        assertThat(notifications.lines()).containsExactly("dev.example.tool|INFO|Other|", "dev.example.tool|INFO|Same|text");
        var same = notifications.posts().getLast();
        assertThat(same.spec().actions()).as("the newest post's").extracting(NotificationAction::label).containsExactly("Open");
        assertThat(same.count()).isEqualTo(2);
        assertThat(notifications.posts().getFirst().count()).isEqualTo(1);
        first.remove();
        assertThat(first.expired()).as("a removed handle").isTrue();
        assertThat(again.expired()).isFalse();
        assertThat(same.removed()).isFalse();
        again.remove();
        again.remove();
        assertThat(same.removed()).isTrue();
        assertThat(same.expired()).isTrue();
        assertThat(notifications.runAction("Same", "Open")).as("its links stop working").isFalse();
        assertThat(notifications.posts()).as("still listed").hasSize(2);
        group.info("Same", "text");
        assertThat(notifications.posts()).as("a repeat of a removed post is a new post").hasSize(3);
        assertThat(notifications.posts().getLast().removed()).isFalse();
    }

    /** As in Jasper, a post counts on the last one with its line until exactly 60 seconds after it, and not later. */
    @Test void aRepeatCountsUntilSixtySecondsAfterTheLastPost() {
        var notifications = new FakeNotifications();
        long[] now = {0};
        notifications.nanos = () -> now[0];
        var group = notifications.group("dev.example.tool");
        group.info("Same", "");
        now[0] = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        group.info("Same", "");
        assertThat(notifications.posts()).as("at 60 seconds").singleElement().satisfies(post -> assertThat(post.count()).isEqualTo(2));
        now[0] = java.util.concurrent.TimeUnit.SECONDS.toNanos(120) + 1;
        group.info("Same", "");
        assertThat(notifications.posts()).as("past 60 seconds after the last post, a new post").hasSize(2);
        assertThat(notifications.posts().getLast().count()).isEqualTo(1);
    }

    @Test void aStandaloneGroupLetsAThrowingActionReachTheTest() {
        var notifications = new FakeNotifications();
        notifications.group("dev.example.tool").post(NotificationSpec.of(Severity.INFO, "t")
            .withAction(NotificationAction.of("Break", () -> { throw new IllegalStateException("broken"); })));
        assertThatIllegalStateException().isThrownBy(() -> notifications.runAction("t", "Break"));
    }

    @Test void theHostDeclaresAPluginsGroupsAndContainsItsFailingActions() {
        try (var host = new FakePluginHost()) {
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            var sync = context.get().notifications().group(new GroupSpec("dev.example.tool.sync", "Sync", DisplayMode.HISTORY));
            sync.post(NotificationSpec.of(Severity.WARNING, "Slow")
                .withAction(new NotificationAction("Break", () -> { throw new IllegalStateException("broken"); }, false)));
            assertThat(host.notifications().groups()).containsExactly("dev.example.tool|Tool|CARD",
                "dev.example.tool.activities|Tool activities|CARD", "dev.example.tool.sync|Sync|HISTORY");
            assertThat(host.notifications().runAction("Slow", "Break")).isTrue();
            assertThat(host.failures()).singleElement().asString()
                .startsWith("dev.example.tool notification action Break: java.lang.IllegalStateException");
            host.stopAll();
            assertThat(host.notifications().posts()).allSatisfy(post -> assertThat(post.expired()).isTrue());
        }
    }
}
