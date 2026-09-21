package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * A panel as the rail shows it.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; the application also registers
 *           the action {@code <id>.toggle}, which users may bind under {@code [keybindings]}
 * @param title non-blank title, used for the rail tooltip and View menu
 * @param icon rail icon, normally from {@link Appearance#icon}
 * @param defaultAnchor the region used until the user moves the panel
 */
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
    }
}
