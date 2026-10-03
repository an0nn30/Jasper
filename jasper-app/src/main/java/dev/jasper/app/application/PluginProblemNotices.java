package dev.jasper.app.application;

import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.plugins.PluginRuntime;
import java.util.List;

/**
 * What kept plugins from running at this launch, as plugin problems (spec section 7, producer 5): one ERROR
 * notification each, with Open Plugins, and Show more… when an exception lies behind it. A plugin that failed while
 * starting is not repeated here, because its incident's notification already says so. EDT only.
 */
final class PluginProblemNotices {
    static final String OPEN_PLUGINS = "Open Plugins";

    private final NotificationCenter center;
    private final IncidentNotices incidents;
    private final Runnable openPlugins;

    PluginProblemNotices(NotificationCenter center, IncidentNotices incidents, Runnable openPlugins) {
        this.center = center;
        this.incidents = incidents;
        this.openPlugins = openPlugins;
    }

    /** Posts one notification per problem. */
    void announce(List<PluginRuntime.Problem> problems) {
        for (PluginRuntime.Problem problem : problems) {
            var request = NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.ERROR, title(problem))
                .withContent(problem.detail())
                .withAction(new NotificationAction(OPEN_PLUGINS, openPlugins, false));
            if (problem.cause().isPresent())
                incidents.pluginNotice(problem.pluginId().orElse(NotificationRequest.JASPER), request, problem.cause().get(), posted -> { });
            else center.post(request);
        }
    }

    /** "Remote could not be loaded", "Remote was not started" or "A plugin could not be read". */
    static String title(PluginRuntime.Problem problem) {
        String name = problem.name().orElse("A plugin");
        return switch (problem.kind()) {
            case NOT_LOADED -> name + " could not be loaded";
            case NOT_STARTED -> name + " was not started";
            case UNREADABLE -> "A plugin could not be read";
        };
    }
}
