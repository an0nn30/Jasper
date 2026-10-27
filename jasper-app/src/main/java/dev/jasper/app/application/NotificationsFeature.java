package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.Slot;
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
 * badge and actions, and one card layer per attached window (the application attaches every window's). Every
 * requested card shows in every layer; a card leaves all of them together, whether it is read, dismissed, expired,
 * removed or timed out in one. EDT only; the application closes it at shutdown.
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
    private final java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit;
    private final LayerFactory layers;
    private final java.util.function.Consumer<UUID> raise;
    private boolean closed;

    /** {@code kit} supplies the chrome kit context the cards and the Notifications panel are built with: the default look's panel. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this(center, contributions, kit, new dev.jasper.app.presentation.AppPanels("Notifications", Slot.RIGHT_TOP));
    }

    /** As the three-argument form, with the panel's title and default slot under the look: Event Log at Bottom Left under labelled stripes. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit,
                         dev.jasper.app.presentation.AppPanels panels) {
        this(center, contributions, kit, panels, id -> { });
    }

    /**
     * Builds a window's card layer from its content, the feature's {@code raise} (a click on a card brings the window the
     * notification pertains to forward) and the callback a countdown that hides a card reports to; tests inject controlled schedules.
     */
    interface LayerFactory {
        CardLayer create(WindowContent content, java.util.function.Consumer<UUID> raise, java.util.function.Consumer<UUID> timedOut);
    }

    /** As the four-argument form, with {@code raise}: told a card's id when it is clicked, to bring its window forward. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit,
                         dev.jasper.app.presentation.AppPanels panels, java.util.function.Consumer<UUID> raise) {
        this(center, contributions, kit, panels, raise, null);
    }

    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit,
                         dev.jasper.app.presentation.AppPanels panels, java.util.function.Consumer<UUID> raise, LayerFactory layers) {
        this.center = center;
        this.contributions = contributions;
        this.kit = java.util.Objects.requireNonNull(kit, "kit");
        this.raise = java.util.Objects.requireNonNull(raise, "raise");
        this.layers = layers != null ? layers : (content, cardRaise, timedOut) -> new CardLayer(this.kit.get(), this.center, content.isActiveAndOpen(),
            () -> this.contributions.requestPanel(new Contributions.PanelRequest(content.id(), PANEL_ID, Contributions.PanelRequest.Op.SHOW)),
            cardRaise, timedOut);
        center.register(GENERAL);
        center.register(CONFIG);
        var icon = AppIcons.semantic("bell");
        contributions.addAction(SHOW_HISTORY, "Notifications: Show History", icon,
            List.of("notifications", "history", "messages", "errors", "panel"), Optional.empty(),
            invocation -> contributions.requestPanel(new Contributions.PanelRequest(invocation.windowId(), PANEL_ID,
                Contributions.PanelRequest.Op.ACTIVATING_TOGGLE)));
        contributions.addAction(DISMISS_CARDS, "Notifications: Dismiss Cards", null,
            List.of("notifications", "close", "clear", "balloons", "cards"), Optional.empty(), invocation -> {
                // Every window shows every card and reading one clears it everywhere, so dismissing the invoking window's layer closes them all.
                Attached attached = windows.get(invocation.windowId());
                if (attached != null) attached.layer().dismissAll();
                else windows.values().forEach(each -> each.layer().dismissAll());
            });
        panel = contributions.addPanel(PANEL_ID, panels.notificationsTitle(), icon, panels.notificationsSlot(), Optional.of(SHOW_HISTORY),
            site -> new HistoryPanel(kit.get(), center, site, focused(site.windowId()), panels.notificationsTitle()));
        listening = center.onChange(this::changed);
        updateBadge();
    }

    /** Gives the window its card layer. */
    void attach(WindowContent content) {
        if (closed || windows.containsKey(content.id())) return;
        UUID id = content.id();
        var layer = layers.create(content, raise, shown -> windows.values().forEach(each -> {
            if (each.content() != content) each.layer().withdraw(shown);
        }));
        content.mountCornerLayer(layer);
        // A window opened while cards show takes them, in their top-to-bottom order, then those waiting.
        var replay = new java.util.LinkedHashSet<UUID>();
        for (Attached each : windows.values()) replay.addAll(each.layer().showing());
        for (Attached each : windows.values()) replay.addAll(each.layer().waiting());
        windows.put(id, new Attached(content, layer));
        for (UUID shown : replay) center.find(shown).ifPresent(layer::show);
    }

    /** The window closed: its cards go; their entries stay in history. */
    void detach(WindowContent content) {
        Attached attached = windows.remove(content.id());
        if (attached == null) return;
        attached.layer().close();
    }

    /**
     * A Jasper window was activated: cards that waited for focus show now.
     */
    void activated(WindowContent content) {
        if (closed || !windows.containsKey(content.id())) return;
        center.focusReturned();
    }

    /** An application error: a title and details, which may be missing; its card shows in every window. */
    void error(String title, String details) { post(Severity.ERROR, title, details, null); }

    void info(String title, String details) { post(Severity.INFO, title, details, null); }

    /** A window's own error: its card shows in every window. */
    void error(String title, String details, UUID window) { post(Severity.ERROR, title, details, window); }

    void info(String title, String details, UUID window) { post(Severity.INFO, title, details, window); }

    /** Shows the Notifications panel in that window: what clicking an OS notification with nothing else to do does. */
    void showHistory(UUID window) {
        if (closed || !windows.containsKey(window)) return;
        contributions.requestPanel(new Contributions.PanelRequest(window, PANEL_ID, Contributions.PanelRequest.Op.SHOW));
    }

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

    private void post(Severity severity, String title, String details, UUID window) {
        var request = NotificationRequest.of(GENERAL.id(), severity, title).withContent(details == null ? "" : details);
        center.submit(window == null ? request : request.withTarget(window));
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
        for (Attached each : windows.values()) each.layer().show(notification);
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
