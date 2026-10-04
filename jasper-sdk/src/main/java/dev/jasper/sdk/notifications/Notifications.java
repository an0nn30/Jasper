package dev.jasper.sdk.notifications;

/**
 * The plugin's notifications. Each belongs to a group the user can tune in Settings: the plugin's default group, or one
 * it declares. Activity outcomes need nothing here: the host posts {@code ActivityHandle.succeed} and {@code fail} in
 * its own {@code <plugin-id>.activities} group, so muting those never mutes the plugin's errors. Plugins cannot
 * observe other notifications. No capability is needed.
 */
public interface Notifications {
    /**
     * The plugin's default group: its id is the plugin's id, it is named after the plugin and it shows cards.
     *
     * @return the default group
     */
    NotificationGroup group();

    /**
     * Declares another group; call it during start so Settings can list the group. Declaring an id again returns the
     * same group, and the plugin's own id returns the default group.
     *
     * @param spec the group
     * @return the group
     * @throws IllegalArgumentException when the id is outside the plugin's namespace or is {@code <plugin-id>.activities}
     * @throws IllegalStateException when the plugin's context is closed
     */
    NotificationGroup group(GroupSpec spec);
}
