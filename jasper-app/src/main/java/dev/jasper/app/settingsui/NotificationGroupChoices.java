package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The notification groups the Notifications page lists, supplied by the application as plain text. EDT only.
 *
 * @param groups every group known now, in the order to list them
 */
public record NotificationGroupChoices(Supplier<List<Group>> groups) {
    /** No groups, for pages built without an application. */
    public static final NotificationGroupChoices NONE = new NotificationGroupChoices(List::of);

    public NotificationGroupChoices {
        Objects.requireNonNull(groups, "groups");
    }

    /**
     * One group.
     *
     * @param id its id: the key in {@code notifications.groups}
     * @param name the name it is listed with
     * @param owner who declared it, as shown: "Jasper" or a plugin's name
     * @param defaultMode how its notifications appear by default: "card", "sticky", "history" or "off"
     */
    public record Group(String id, String name, String owner, String defaultMode, String ownerId, String ownerIcon) {
        public Group(String id,String name,String owner,String defaultMode) { this(id,name,owner,defaultMode,owner,""); }
        public Group {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(defaultMode, "defaultMode");
            Objects.requireNonNull(ownerId);Objects.requireNonNull(ownerIcon);
        }
    }
}
