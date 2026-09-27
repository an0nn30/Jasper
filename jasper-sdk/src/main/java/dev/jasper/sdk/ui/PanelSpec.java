package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * A panel as the rail shows it.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; unless {@code toggleActionId}
 *           names one, the application also registers the action {@code <id>.toggle}, which users
 *           may bind under {@code [keybindings]}
 * @param title non-blank title, used for the rail tooltip and the application's toggle action
 * @param icon rail icon, normally from {@link Appearance#icon}
 * @param defaultAnchor the region used until the user moves the panel
 * @param toggleActionId one of the plugin's own actions, already registered, that toggles this panel:
 *                       View &gt; Tools lists it, with its shortcut, instead of an application-made
 *                       {@code <id>.toggle} (since 0.8.1)
 */
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        Objects.requireNonNull(toggleActionId, "toggleActionId");
    }

    /**
     * A panel toggled by the application's own {@code <id>.toggle} action.
     *
     * @param id the panel id
     * @param title the title
     * @param icon the rail icon
     * @param defaultAnchor the starting region
     */
    public PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {
        this(id, title, icon, defaultAnchor, Optional.empty());
    }

    /**
     * Derives a value. Name an action when the plugin already has one that toggles this panel, so the
     * View menu and the palette offer it once.
     *
     * @param value the plugin's toggle action, or null for the application's {@code <id>.toggle}
     * @return a copy naming it
     * @since 0.8.1
     */
    public PanelSpec withToggleActionId(String value) {
        return new PanelSpec(id, title, icon, defaultAnchor, Optional.ofNullable(value));
    }
}
