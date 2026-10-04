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
}
