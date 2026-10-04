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
