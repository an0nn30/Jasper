package dev.jasper.app.presentation;

import dev.jasper.app.dock.Slot;
import java.util.Objects;

/**
 * The app's own panels under the look (spec 10.7).
 *
 * @param notificationsTitle the Notifications panel's title
 * @param notificationsSlot its default slot
 */
public record AppPanels(String notificationsTitle, Slot notificationsSlot) {
    /** Both parts are required. */
    public AppPanels { Objects.requireNonNull(notificationsTitle); Objects.requireNonNull(notificationsSlot); }
}
