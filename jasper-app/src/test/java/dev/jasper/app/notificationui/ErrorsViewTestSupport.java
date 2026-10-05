package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Incident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Package-local test access, excluded from production artifacts. */
public final class ErrorsViewTestSupport {
    private ErrorsViewTestSupport() {}

    public static Optional<UUID> selected(ErrorsView view) { return view.selected().map(Incident::id); }

    public static List<Incident> listed(ErrorsView view) { return view.listed(); }

    /** The incident list's kit view. */
    public static javax.swing.JComponent list(ErrorsView view) { return view.listView(); }

    /** The incident's mounted summary label in the list, or null while its row is not mounted. */
    public static javax.swing.JComponent summary(ErrorsView view, UUID id) { return NotificationCard.find(view.listView(), "summary:" + id); }
}
