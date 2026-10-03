package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.SwingUtilities;

/**
 * The Errors window (spec section 5): one auxiliary window over this session's incidents, opened by an incident's
 * Show more… link with that incident selected, and by Notifications: Show Errors. The auxiliary windows remember its
 * bounds and close it at shutdown; closing it stops its view. EDT only.
 */
final class ErrorsWindow {
    static final String ID = "jasper.errors";
    static final String SHOW_ERRORS = "notifications.errors";
    static final Dimension SIZE = new Dimension(900, 560);

    private final AuxiliaryWindows windows;
    private final Incidents incidents;
    private final ErrorsView.Hooks hooks;
    private final ZoneId zone;
    private AuxiliarySurface surface;
    private ErrorsView view;

    ErrorsWindow(Contributions contributions, AuxiliaryWindows windows, Incidents incidents, ErrorsView.Hooks hooks, ZoneId zone) {
        this.windows = windows;
        this.incidents = incidents;
        this.hooks = hooks;
        this.zone = zone;
        contributions.addAction(SHOW_ERRORS, "Notifications: Show Errors", AppIcons.bundled("status/error"),
            List.of("notifications", "errors", "exceptions", "stack trace", "incidents", "problems"), Optional.empty(),
            invocation -> show(Optional.empty()));
    }

    /**
     * Opens the window, or brings it forward, and selects {@code incident} when it is still listed. A window that
     * opens now is laid out as it shows, so its selected row is scrolled into view again on the next EDT turn.
     */
    void show(Optional<UUID> incident) {
        if (surface == null || surface.closed()) {
            surface = windows.window(ID, "Errors", SIZE, true);
            ErrorsView opened = new ErrorsView(incidents, hooks, zone);
            surface.setContent(opened);
            surface.onClosed(opened::close);
            view = opened;
        }
        incident.ifPresent(view::select);
        surface.show();
        surface.toFront();
        if (incident.isPresent()) SwingUtilities.invokeLater(view::revealSelection);
    }

    /** Test access: the open window's view. */
    Optional<ErrorsView> view() { return surface == null || surface.closed() ? Optional.empty() : Optional.of(view); }

    /** Test access: the open window. */
    Optional<AuxiliarySurface> surface() { return surface == null || surface.closed() ? Optional.empty() : Optional.of(surface); }
}
