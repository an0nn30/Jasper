package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
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
 *   <li>Removal, through the {@link Holds} every plugin of the host shares.</li>
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
    private final Holds holds;
    private volatile boolean closed;

    /** UI thread: declares the plugin's default group and the host's activities group for it. */
    HostedNotifications(String pluginId, String pluginName, PluginRuntime.NotificationHost host, Holds holds, Containment containment,
                        Consumer<Runnable> ui, BooleanSupplier onUi, java.util.function.Function<String,javax.swing.Icon> icons, Consumer<String> validateIcon) {
        this.icons=icons;this.validateIcon=validateIcon;
        this.pluginId = pluginId;
        this.host = host;
        this.holds = Objects.requireNonNull(holds, "holds");
        this.containment = containment;
        this.ui = ui;
        this.onUi = onUi;
        defaultGroup = new Group(pluginId);
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId, pluginName, pluginId,
            dev.jasper.app.notifications.DisplayMode.CARD));
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId + ACTIVITIES, pluginName + " activities",
            pluginId, dev.jasper.app.notifications.DisplayMode.CARD));
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

    /**
     * UI thread: the plugin stopped. Its handles expire, and so does everything it posted, unless another running
     * plugin's handle holds it too ({@link Holds}); later posts are refused, and removing a handle still takes its entry
     * out of history.
     */
    void close() {
        if (closed) return;
        closed = true;
        holds.stopped(this);
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

    /**
     * The handles on each entry plugins posted that is still in history, live or expired, by the center's id, oldest
     * first; one per host, shared by its plugins (ruling V9). The center counts a post with the same text on an earlier
     * entry (a repeat) and gives the entry the repeat's request, and plugin {@code a} may declare the group {@code a.b},
     * plugin {@code a.b}'s default group, so an entry can have the handles of several plugins. It leaves history once
     * each of them has been removed. While it stays, it carries the request of a running plugin's handle (ruling V8):
     * when the handle whose request it carries is removed, or that handle's plugin stops, the newest handle left of a
     * running plugin gives it its request ({@link NotificationCenter#restate}), and with none left it expires. UI thread.
     */
    static final class Holds {
        private final NotificationCenter center;
        private final Map<UUID, List<Handle>> held = new HashMap<>();
        private Subscription listening;

        Holds(NotificationCenter center) { this.center = Objects.requireNonNull(center, "center"); }

        /** {@code handle}'s post landed in the entry {@code id}, which carries the request it was given just now. */
        private void landed(UUID id, Handle handle) {
            if (listening == null) listening = center.onChange(this::changed);
            handle.carried = center.find(id).map(Notification::request).orElse(null);
            held.computeIfAbsent(id, key -> new ArrayList<>()).add(handle);
        }

        /** {@code handle} was removed: its entry leaves history once no handle on it is left. Idempotent. */
        private void drop(UUID id, Handle handle) {
            List<Handle> handles = held.get(id);
            if (handles == null || !handles.remove(handle)) return;
            if (!handles.isEmpty()) carry(id);
            else {
                held.remove(id);
                center.remove(id);
            }
        }

        /** {@code owner} stopped: its handles expire, and each entry they hold is carried by another plugin's, or expires. */
        private void stopped(HostedNotifications owner) {
            for (var entry : List.copyOf(held.entrySet())) {
                boolean ours = false;
                for (Handle handle : entry.getValue())
                    if (handle.owner() == owner) { handle.markExpired(); ours = true; }
                if (ours) carry(entry.getKey());
            }
        }

        /**
         * Unless a running plugin's handle on the live entry {@code id} carries its request, the newest such handle gives it
         * its request; with none left it expires.
         */
        private void carry(UUID id) {
            Notification entry = center.find(id).orElse(null);
            List<Handle> handles = held.get(id);
            if (entry == null || entry.expired() || handles == null) return;
            Handle newest = null;
            for (Handle handle : handles) {
                if (handle.owner().closed) continue;
                if (handle.carried == entry.request()) return;
                newest = handle;
            }
            if (newest == null) center.expire(id);
            else center.restate(id, newest.carried);
        }

        /** The center says what became of an entry: expired, its handles expire too; gone, it is forgotten. */
        private void changed(NotificationCenter.Change change) {
            switch (change) {
                case NotificationCenter.Change.Upserted upserted when upserted.notification().expired() -> {
                    List<Handle> handles = held.get(upserted.notification().id());
                    if (handles != null) handles.forEach(Handle::markExpired);
                }
                case NotificationCenter.Change.Removed removed -> {
                    List<Handle> handles = held.remove(removed.id());
                    if (handles != null) handles.forEach(Handle::markExpired);
                }
                case NotificationCenter.Change.Cleared cleared -> {
                    held.values().forEach(handles -> handles.forEach(Handle::markExpired));
                    held.clear();
                }
                default -> { }
            }
        }
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
            // A handle removed before its post is made leaves nothing to make: no card shows, and no repeat is counted.
            else onUiThread(() -> handle.posted(closed || handle.removed ? Optional.empty() : host.center().post(request)));
            return handle;
        }
    }

    /** One post's handle: it learns the center's id on the UI thread, and may be expired or removed before that. */
    private final class Handle implements NotificationHandle {
        private volatile UUID id;
        private volatile boolean expired, removed;
        /** The request its entry carried when its post landed. UI thread ({@link Holds}). */
        private NotificationRequest carried;

        @Override public void expire() {
            if (expired) return;
            expired = true;
            UUID known = id;
            if (known != null) onUiThread(() -> host.center().expire(known));
        }

        /** SDK 0.30.0: a handle removed before its post lands is dropped as it lands ({@link #posted}). */
        @Override public void remove() {
            if (removed) return;
            removed = true;
            expired = true;
            UUID known = id;
            if (known != null) onUiThread(() -> holds.drop(known, this));
        }

        @Override public boolean expired() { return expired; }

        /** UI thread: where the post landed; empty when its group dropped it or the plugin had stopped. */
        void posted(Optional<UUID> landed) {
            if (landed.isEmpty()) { expired = true; return; }
            UUID known = landed.get();
            id = known;
            holds.landed(known, this);
            if (removed) holds.drop(known, this);
            else if (closed) {
                // Landed after its plugin stopped (a post with a cause): another running plugin's handle may carry it.
                expired = true;
                holds.carry(known);
            } else if (expired) host.center().expire(known);
        }

        private HostedNotifications owner() { return HostedNotifications.this; }

        private void markExpired() { expired = true; }
    }
}
