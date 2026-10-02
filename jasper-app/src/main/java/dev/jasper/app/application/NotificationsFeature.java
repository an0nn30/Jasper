package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notificationui.CardLayer;
import dev.jasper.app.notificationui.HistoryPanel;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.workspace.WindowContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The application's notification surfaces: Jasper's own groups, the built-in Notifications panel with its unread
 * badge and actions, and one card layer per terminal window, routing each requested card to its target window or the
 * window used last. EDT only; the application closes it at shutdown.
 */
final class NotificationsFeature implements AutoCloseable {
    static final String PANEL_ID = "jasper.notifications";
    static final String SHOW_HISTORY = "notifications.history";
    static final String DISMISS_CARDS = "notifications.dismiss";
    static final NotificationGroup GENERAL = new NotificationGroup("jasper.general", "Jasper", NotificationRequest.JASPER, DisplayMode.CARD);
    static final NotificationGroup CONFIG = new NotificationGroup("jasper.config", "Configuration", NotificationRequest.JASPER, DisplayMode.STICKY);

    private record Attached(WindowContent content, CardLayer layer) {}

    private final NotificationCenter center;
    private final Contributions contributions;
    private final PanelEntry panel;
    private final Map<UUID, Attached> windows = new LinkedHashMap<>();
    private final Subscription listening;
    private UUID lastActive;
    private boolean closed;

    NotificationsFeature(NotificationCenter center, Contributions contributions) {
        this.center = center;
        this.contributions = contributions;
        center.register(GENERAL);
        center.register(CONFIG);
        var icon = AppIcons.bundled("toolwindows/notifications");
        contributions.addAction(SHOW_HISTORY, "Notifications: Show History", icon,
            List.of("notifications", "history", "messages", "errors", "panel"), Optional.empty(),
            invocation -> contributions.requestPanel(new Contributions.PanelRequest(invocation.windowId(), PANEL_ID,
                Contributions.PanelRequest.Op.TOGGLE)));
        contributions.addAction(DISMISS_CARDS, "Notifications: Dismiss Cards", null,
            List.of("notifications", "close", "clear", "balloons", "cards"), Optional.empty(), invocation -> {
                Attached attached = windows.get(invocation.windowId());
                if (attached != null) attached.layer().dismissAll();
            });
        panel = contributions.addPanel(PANEL_ID, "Notifications", icon, PanelRegion.RIGHT, Optional.of(SHOW_HISTORY),
            site -> new HistoryPanel(center, site, focused(site.windowId())));
        listening = center.onChange(this::changed);
        updateBadge();
    }

    /** Gives the window its card layer. */
    void attach(WindowContent content) {
        if (closed || windows.containsKey(content.id())) return;
        UUID id = content.id();
        var layer = new CardLayer(center, content.isActiveAndOpen(), () -> contributions.requestPanel(
            new Contributions.PanelRequest(id, PANEL_ID, Contributions.PanelRequest.Op.SHOW)));
        content.mountCornerLayer(layer);
        windows.put(id, new Attached(content, layer));
        if (lastActive == null) lastActive = id;
    }

    /** The window closed: its cards go; their entries stay in history. */
    void detach(WindowContent content) {
        Attached attached = windows.remove(content.id());
        if (attached == null) return;
        attached.layer().close();
        if (content.id().equals(lastActive)) lastActive = windows.isEmpty() ? null : windows.keySet().iterator().next();
    }

    /** A terminal window was activated: it is the default target, and cards that waited for focus show now. */
    void activated(WindowContent content) {
        if (closed || !windows.containsKey(content.id())) return;
        lastActive = content.id();
        center.focusReturned();
    }

    /** A window or application error: a title and details, which may be missing. */
    void error(String title, String details) { post(Severity.ERROR, title, details); }

    void info(String title, String details) { post(Severity.INFO, title, details); }

    CardLayer layer(UUID windowId) {
        Attached attached = windows.get(windowId);
        return attached == null ? null : attached.layer();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        for (Attached attached : windows.values()) attached.layer().close();
        windows.clear();
    }

    private void post(Severity severity, String title, String details) {
        center.submit(NotificationRequest.of(GENERAL.id(), severity, title).withContent(details == null ? "" : details));
    }

    private boolean focused(UUID windowId) {
        Attached attached = windows.get(windowId);
        return attached != null && attached.content().isActiveAndOpen();
    }

    private void changed(NotificationCenter.Change change) {
        if (change instanceof NotificationCenter.Change.CardRequested requested) route(requested.notification());
        updateBadge();
    }

    private void route(Notification notification) {
        Attached target = notification.request().target().map(windows::get).orElse(null);
        if (target == null && lastActive != null) target = windows.get(lastActive);
        if (target == null && !windows.isEmpty()) target = windows.values().iterator().next();
        if (target != null) target.layer().show(notification);
    }

    private void updateBadge() {
        int unread = center.unread();
        if (unread == 0) {
            panel.setBadge(Optional.empty());
            return;
        }
        int errors = center.unreadErrors();
        String description = unread + (unread == 1 ? " unread notification" : " unread notifications")
            + (errors == 0 ? "" : ", " + errors + (errors == 1 ? " error" : " errors"));
        panel.setBadge(Optional.of(new PanelBadge(unread, errors > 0 ? PanelBadge.Tone.ERROR : PanelBadge.Tone.ACCENT, description)));
    }
}
