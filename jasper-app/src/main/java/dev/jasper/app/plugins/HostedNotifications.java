package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Notifications;
import dev.jasper.sdk.notifications.Severity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One plugin's notifications (spec section 6).
 * <ul>
 *   <li>Its default group, the host's activities group and the groups it declares, registered with the application's
 *     center.</li>
 *   <li>Posts from any thread.</li>
 *   <li>Actions and default actions that run inside the plugin's containment.</li>
 *   <li>A cause recorded as the plugin's incident.</li>
 *   <li>Expiry of everything it posted when it stops.</li>
 * </ul>
 * The center is reached on the UI thread; the handles' bookkeeping lives there too.
 */
final class HostedNotifications implements Notifications {
    /** The host's group for a plugin's activity outcomes: {@code <plugin-id>.activities}. */
    static final String ACTIVITIES = ".activities";

    private final String pluginId;
    private final java.util.function.Function<String,javax.swing.Icon> icons;
    private final Consumer<String> validateIcon;
    private final PluginRuntime.NotificationHost host;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final Group defaultGroup;
    private final Map<String, Group> declared = new ConcurrentHashMap<>();
    /** The handles of every live notification posted, by the center's id. UI thread. */
    private final Map<UUID, List<Handle>> posted = new HashMap<>();
    private final Subscription listening;
    private volatile boolean closed;

    /** UI thread: declares the plugin's default group and the host's activities group for it. */
    HostedNotifications(String pluginId, String pluginName, PluginRuntime.NotificationHost host, Containment containment,
                        Consumer<Runnable> ui, BooleanSupplier onUi, java.util.function.Function<String,javax.swing.Icon> icons, Consumer<String> validateIcon) {
        this.icons=icons;this.validateIcon=validateIcon;
        this.pluginId = pluginId;
        this.host = host;
        this.containment = containment;
        this.ui = ui;
        this.onUi = onUi;
        defaultGroup = new Group(pluginId);
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId, pluginName, pluginId,
            dev.jasper.app.notifications.DisplayMode.CARD));
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId + ACTIVITIES, pluginName + " activities",
            pluginId, dev.jasper.app.notifications.DisplayMode.CARD));
        listening = host.center().onChange(this::changed);
    }

    @Override public NotificationGroup group() { return defaultGroup; }

    @Override public NotificationGroup group(GroupSpec spec) {
        Objects.requireNonNull(spec, "spec");
        requireOpen();
        if (spec.id().equals(pluginId)) return defaultGroup;
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A notification group of " + pluginId + " starts with its id and a dot: " + spec.id());
        if (spec.id().equals(pluginId + ACTIVITIES))
            throw new IllegalArgumentException("The host declares " + spec.id() + " for activity outcomes");
        boolean[] made = new boolean[1];
        Group group = declared.computeIfAbsent(spec.id(), id -> { made[0] = true; return new Group(id); });
        if (made[0]) onUiThread(() -> host.center().register(new dev.jasper.app.notifications.NotificationGroup(spec.id(),
            spec.name(), pluginId, mode(spec.defaultMode()))));
        return group;
    }

    /** UI thread: the plugin stopped. Everything it posted expires, and later posts are refused. */
    void close() {
        if (closed) return;
        closed = true;
        listening.close();
        for (UUID id : List.copyOf(posted.keySet())) host.center().expire(id);
        posted.values().forEach(handles -> handles.forEach(Handle::markExpired));
        posted.clear();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Plugin context is closed: " + pluginId);
    }

    private void onUiThread(Runnable task) {
        if (onUi.getAsBoolean()) task.run();
        else ui.accept(task);
    }

    /** The application's request for a plugin's spec; actions and the body run inside the plugin's containment. */
    private NotificationRequest request(String group, NotificationSpec spec) {
        var request = NotificationRequest.of(group, severity(spec.severity()), spec.title()).withContent(spec.content()).withSource(pluginId);
        if (spec.icon().isPresent()) request = request.withIcon(icons.apply(spec.icon().get()));
        for (var action : spec.actions())
            request = request.withAction(new dev.jasper.app.notifications.NotificationAction(action.label(),
                () -> containment.run(pluginId, "notification action " + action.label(), action.run()), action.expires()));
        if (spec.activate().isPresent()) {
            Runnable body = spec.activate().get();
            request = request.withActivate(() -> containment.run(pluginId, "notification body", body));
        }
        if (spec.window().isPresent()) request = request.withTarget(spec.window().get().id());
        return request;
    }

    /** UI thread: the center says what became of the plugin's notifications. */
    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.Upserted upserted when upserted.notification().expired() -> expired(upserted.notification().id());
            case NotificationCenter.Change.Removed removed -> expired(removed.id());
            case NotificationCenter.Change.Cleared cleared -> {
                posted.values().forEach(handles -> handles.forEach(Handle::markExpired));
                posted.clear();
            }
            default -> { }
        }
    }

    private void expired(UUID id) {
        List<Handle> handles = posted.remove(id);
        if (handles != null) handles.forEach(Handle::markExpired);
    }

    private static dev.jasper.app.notifications.Severity severity(Severity value) {
        return switch (value) {
            case INFO -> dev.jasper.app.notifications.Severity.INFO;
            case WARNING -> dev.jasper.app.notifications.Severity.WARNING;
            case ERROR -> dev.jasper.app.notifications.Severity.ERROR;
        };
    }

    private static dev.jasper.app.notifications.DisplayMode mode(DisplayMode value) {
        return switch (value) {
            case CARD -> dev.jasper.app.notifications.DisplayMode.CARD;
            case STICKY -> dev.jasper.app.notifications.DisplayMode.STICKY;
            case HISTORY -> dev.jasper.app.notifications.DisplayMode.HISTORY;
            case OFF -> dev.jasper.app.notifications.DisplayMode.OFF;
        };
    }

    private final class Group implements NotificationGroup {
        private final String id;

        Group(String id) { this.id = id; }

        @Override public String id() { return id; }

        @Override public NotificationHandle post(NotificationSpec spec) {
            Objects.requireNonNull(spec, "spec");
            requireOpen();
            spec.icon().ifPresent(validateIcon);
            NotificationRequest request = request(id, spec);
            var handle = new Handle();
            if (spec.cause().isPresent())
                host.caused().accept(new PluginRuntime.CausedNotice(pluginId, request, spec.cause().get(), handle::posted));
            else onUiThread(() -> handle.posted(closed ? Optional.empty() : host.center().post(request)));
            return handle;
        }
    }

    /** One post's handle: it learns the center's id on the UI thread, and may be expired before that. */
    private final class Handle implements NotificationHandle {
        private volatile UUID id;
        private volatile boolean expired;

        @Override public void expire() {
            if (expired) return;
            expired = true;
            UUID known = id;
            if (known != null) onUiThread(() -> host.center().expire(known));
        }

        @Override public boolean expired() { return expired; }

        /** UI thread: where the post landed; empty when its group dropped it or the plugin had stopped. */
        void posted(Optional<UUID> landed) {
            if (landed.isEmpty()) { expired = true; return; }
            UUID known = landed.get();
            id = known;
            if (closed || expired) {
                expired = true;
                host.center().expire(known);
                return;
            }
            posted.computeIfAbsent(known, key -> new ArrayList<>()).add(this);
        }

        void markExpired() { expired = true; }
    }
}
