package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class PluginProblemNoticesTest {
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final List<UUID> opened = new ArrayList<>();
    private int pluginPages;

    private PluginProblemNotices notices() {
        center.register(NotificationsFeature.GENERAL);
        var incidentNotices = new IncidentNotices(center, incidents, Runnable::run, (delay, task) -> () -> { },
            id -> id, (id, done) -> { }, opened::add);
        return new PluginProblemNotices(center, incidentNotices, () -> pluginPages++);
    }

    private static List<String> labels(Notification notification) {
        return notification.request().actions().stream().map(NotificationAction::label).toList();
    }

    private static String line(Notification entry) {
        var request = entry.request();
        return request.group() + "|" + request.severity() + "|" + request.title() + "|" + request.content() + "|" + String.join(";", labels(entry));
    }

    @Test void eachProblemIsAPluginProblemWithOpenPlugins() {
        notices().announce(List.of(
            new PluginRuntime.Problem(PluginRuntime.Problem.Kind.NOT_STARTED, Optional.of("dev.example.future"), Optional.of("Future"),
                "needs SDK >=9.0.0; this is SDK 0.14.0", Optional.empty()),
            new PluginRuntime.Problem(PluginRuntime.Problem.Kind.UNREADABLE, Optional.empty(), Optional.empty(),
                "/plugins/stray: no plugin.toml in any jar", Optional.empty())));
        assertThat(center.history()).extracting(PluginProblemNoticesTest::line).containsExactly(
            "jasper.plugins|ERROR|A plugin could not be read|/plugins/stray: no plugin.toml in any jar|Open Plugins",
            "jasper.plugins|ERROR|Future was not started|needs SDK >=9.0.0; this is SDK 0.14.0|Open Plugins");
        center.runAction(center.history().getFirst().id(), 0);
        assertThat(pluginPages).isEqualTo(1);
        assertThat(center.history().getFirst().expired()).as("Open Plugins keeps working").isFalse();
        assertThat(incidents.list()).isEmpty();
    }

    @Test void aLoadFailureWithAnExceptionAlsoOffersShowMore() {
        var cause = new IllegalStateException("Cannot read reserved.jar", new java.io.IOException("bad zip"));
        notices().announce(List.of(new PluginRuntime.Problem(PluginRuntime.Problem.Kind.NOT_LOADED, Optional.of("dev.example.broken"),
            Optional.of("Broken"), "Cannot read reserved.jar: bad zip", Optional.of(cause))));
        Notification notice = center.history().getFirst();
        assertThat(notice.request().title()).isEqualTo("Broken could not be loaded");
        assertThat(labels(notice)).containsExactly("Open Plugins", "Show more…");
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.source()).isEqualTo("dev.example.broken"));
    }
}
