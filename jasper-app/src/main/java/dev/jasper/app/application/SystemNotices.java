package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Jasper's notifications in the operating system (spec sections 7, 9 and 11). What the center asks the operating
 * system to show while Jasper is not focused goes to the system notifier when {@code notifications.system} is on, an
 * incident's with its title only; what the operating system shows is withdrawn once it is read, expires or is removed
 * in Jasper; and a click on one raises its window and does what a click on its card would, or opens the Errors window
 * for an incident, or shows the Notifications panel. EDT only; it owns the notifier it is given, and the application
 * closes it at shutdown.
 */
final class SystemNotices implements AutoCloseable {
    /**
     * What a click needs from the application.
     *
     * @param raise brings the notification's window (empty: the one used last) to the front, opening one when none is
     *     open, and names it; empty while Jasper quits
     * @param showHistory shows the Notifications panel in that window
     * @param showErrors opens the Errors window at that incident
     * @param holder the window that holds what a notification is about now (a command's pane, whose tab may have moved
     *     since it posted), resolved when the click arrives; empty to use the notification's own target
     */
    record Windows(Function<Optional<UUID>, Optional<UUID>> raise, Consumer<UUID> showHistory, Consumer<UUID> showErrors,
                   Function<UUID, Optional<UUID>> holder) {
        Windows {
            Objects.requireNonNull(raise, "raise");
            Objects.requireNonNull(showHistory, "showHistory");
            Objects.requireNonNull(showErrors, "showErrors");
            Objects.requireNonNull(holder, "holder");
        }
    }

    private final NotificationCenter center;
    private final BooleanSupplier enabled;
    private final Windows windows;
    private final Subscription listening;
    /** What the operating system shows now, so that only that is withdrawn. */
    private final Set<UUID> shown = new HashSet<>();
    private SystemNotifier notifier = SystemNotifier.none();
    private boolean closed;

    /**
     * @param center whose requests it carries out
     * @param enabled {@code notifications.system}, read at each request
     * @param windows what a click needs from the application
     */
    SystemNotices(NotificationCenter center, BooleanSupplier enabled, Windows windows) {
        this.center = Objects.requireNonNull(center, "center");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.windows = Objects.requireNonNull(windows, "windows");
        listening = center.onChange(this::changed);
    }

    /** The notifier to use from now on; the one it replaces is closed, and clicks come back to {@link #clicked}. */
    void use(SystemNotifier value) {
        Objects.requireNonNull(value, "value");
        if (closed) {
            value.close();
            return;
        }
        SystemNotifier previous = notifier;
        notifier = value;
        value.onClick(this::clicked);
        previous.close();
    }

    /** Jasper is focused again. */
    void focused() {
        if (!closed) notifier.focused();
    }

    /**
     * A click on the operating system's notification: its window comes to the front, then its default action runs; with
     * none (or once it has expired) the notification is read and the Errors window opens at its incident, or the
     * Notifications panel shows. While Jasper quits nothing opens.
     */
    void clicked(UUID id) {
        if (closed) return;
        Optional<Notification> entry = center.find(id);
        Optional<UUID> window = windows.raise().apply(entry.flatMap(found ->
            windows.holder().apply(found.id()).or(() -> found.request().target())));
        if (window.isEmpty()) return;
        if (entry.isEmpty()) {
            windows.showHistory().accept(window.get());
            return;
        }
        Notification notification = entry.get();
        if (!notification.expired() && notification.request().activate().isPresent()) {
            center.activate(id);
            return;
        }
        center.markRead(id);
        Optional<UUID> incident = notification.request().incident();
        if (incident.isPresent()) windows.showErrors().accept(incident.get());
        else windows.showHistory().accept(window.get());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        notifier.close();
    }

    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.SystemRequested requested -> send(requested.notification());
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                if ((entry.read() || entry.expired()) && shown.remove(entry.id())) notifier.withdraw(entry.id());
            }
            case NotificationCenter.Change.Removed removed -> {
                if (shown.remove(removed.id())) notifier.withdraw(removed.id());
            }
            case NotificationCenter.Change.Cleared cleared -> {
                if (!shown.isEmpty()) {
                    shown.clear();
                    notifier.withdrawAll();
                }
            }
            case NotificationCenter.Change.CardRequested requested -> { }
        }
    }

    private void send(Notification notification) {
        if (!enabled.getAsBoolean()) return;
        NotificationRequest request = notification.request();
        // An incident's details can name paths and hosts, and banners show on a locked screen (spec section 11).
        String body = request.incident().isPresent() ? "" : request.content();
        notifier.send(notification.id(), level(request.severity()), request.title(), body);
        shown.add(notification.id());
    }

    static SystemNotifier.Level level(Severity severity) {
        return switch (severity) {
            case INFO -> SystemNotifier.Level.INFO;
            case WARNING -> SystemNotifier.Level.WARNING;
            case ERROR -> SystemNotifier.Level.ERROR;
        };
    }
}
