package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.platform.AppIcons;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.SwingUtilities;

/**
 * The Errors tab (spec section 5; workspace tabs spec 6.6): one content tab over this session's incidents, in a new tab
 * of the most recently active window, opened by an incident's Show more… link with that incident selected, and by Notifications: Show
 * Errors. Jasper owns it ({@link TabRegistry#APP_OWNER}); its tab strip closes it, closing it stops its view, and the tab
 * registry closes it at shutdown. EDT only.
 */
final class ErrorsTab {
    static final String SHOW_ERRORS = "notifications.errors";
    /** The tab's key under {@link TabRegistry#APP_OWNER}. */
    static final String KEY = "errors";

    private final Contributions contributions;
    private final Incidents incidents;
    private final ErrorsView.Hooks hooks;
    private final ZoneId zone;
    /** The workspace route's kit context: the view is a content tab's. */
    private final java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit;
    private Tab tab;
    private ErrorsView view;

    ErrorsTab(Contributions contributions, Incidents incidents, ErrorsView.Hooks hooks, ZoneId zone,
              java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this.kit = java.util.Objects.requireNonNull(kit, "kit");
        this.contributions = contributions;
        this.incidents = incidents;
        this.hooks = hooks;
        this.zone = zone;
        contributions.addAction(SHOW_ERRORS, "Notifications: Show Errors", AppIcons.semantic("error"),
            List.of("notifications", "errors", "exceptions", "stack trace", "incidents", "problems"), Optional.empty(),
            invocation -> show(Optional.empty()));
    }

    /**
     * Opens the tab in the most recently active window, or selects it, and selects {@code incident} when it is still listed. A tab that
     * opens now is laid out as it shows, so its selected row is scrolled into view again on the next EDT turn.
     */
    void show(Optional<UUID> incident) {
        if (tab == null || tab.closed()) {
            // A view that fails to build opens no tab, so the next Show more… starts afresh.
            ErrorsView opened = new ErrorsView(kit.get(), incidents, hooks, zone);
            incident.ifPresent(opened::select);
            Tab created;
            try { created = contributions.tabs().open(TabRegistry.APP_OWNER, KEY, "Errors", null, opened); }
            catch (RuntimeException | Error failure) { opened.close(); throw failure; }
            created.onClosed(opened::close);
            tab = created;
            view = opened;
        } else {
            incident.ifPresent(view::select);
            tab.select();
        }
        if (incident.isPresent()) SwingUtilities.invokeLater(view::revealSelection);
    }

    /** Test access: the open tab's view. */
    Optional<ErrorsView> view() { return tab == null || tab.closed() ? Optional.empty() : Optional.of(view); }

    /** Test access: the open tab. */
    Optional<Tab> tab() { return tab == null || tab.closed() ? Optional.empty() : Optional.of(tab); }
}
