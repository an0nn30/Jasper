package dev.jasper.app.plugins;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/**
 * A notification center for plugin hosts in tests, read back as lines. It holds what the application's center would:
 * a cause becomes a Show more… link (the application records the incident), and problems are kept as
 * {@code title: details}. Accessors hop to the UI thread.
 */
final class NotificationsFixture {
    static final String SHOW_MORE = "Show more…";
    final NotificationCenter center = new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    final List<String> problems;
    final PluginRuntime.NotificationHost host;

    NotificationsFixture() { this(new CopyOnWriteArrayList<>()); }

    NotificationsFixture(List<String> problems) {
        this.problems = problems;
        host = new PluginRuntime.NotificationHost(center,
            caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(
                center.post(caused.request().withAction(new NotificationAction(SHOW_MORE, () -> { }, false))))),
            (title, details) -> problems.add(title + ": " + details));
    }

    /** Every notification, oldest first, as the contract's {@code group|SEVERITY|title|content|labels|cause|window|state}. */
    List<String> lines() {
        return onEdtValue(() -> {
            List<Notification> oldestFirst = new ArrayList<>(center.history());
            Collections.reverse(oldestFirst);
            return oldestFirst.stream().map(NotificationsFixture::line).toList();
        });
    }

    /** Declared groups, in declaration order, as {@code id|name|MODE}. */
    List<String> groups() {
        return onEdtValue(() -> center.groups().stream().map(group -> group.id() + "|" + group.name() + "|" + group.defaultMode()).toList());
    }

    boolean runAction(String title, String label) {
        return onEdtValue(() -> newest(title).map(entry -> {
            List<NotificationAction> actions = entry.request().actions();
            for (int i = 0; i < actions.size(); i++) {
                if (!actions.get(i).label().equals(label)) continue;
                center.runAction(entry.id(), i);
                return true;
            }
            return false;
        }).orElse(false));
    }

    boolean click(String title) {
        return onEdtValue(() -> newest(title).map(entry -> { center.activate(entry.id()); return true; }).orElse(false));
    }

    /** UI thread: the newest entry with this title, unless it has expired. */
    private Optional<Notification> newest(String title) {
        return center.history().stream().filter(entry -> entry.request().title().equals(title)).findFirst().filter(entry -> !entry.expired());
    }

    private static String line(Notification entry) {
        var request = entry.request();
        List<String> labels = new ArrayList<>(request.actions().stream().map(NotificationAction::label).toList());
        boolean cause = !labels.isEmpty() && labels.getLast().equals(SHOW_MORE);
        if (cause) labels.removeLast();
        return String.join("|", request.group(), request.severity().name(), request.title(), request.content(),
            labels.isEmpty() ? "-" : String.join(";", labels), cause ? "cause" : "-",
            request.target().map(UUID::toString).orElse("-"), entry.expired() ? "expired" : "live");
    }
}
