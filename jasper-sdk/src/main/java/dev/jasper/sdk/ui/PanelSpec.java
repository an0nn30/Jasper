package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A panel as the stripes show it: an icon on a tool window stripe and, while it shows, a tool window with a header.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; unless {@code toggleActionId}
 *           names one, the application also registers the action {@code <id>.toggle}, which users
 *           may bind under {@code [keybindings]}
 * @param title non-blank title, used for the stripe tooltip, the tool window's header and the application's toggle action
 * @param icon semantic stripe icon name
 * @param defaultAnchor the side used until the user moves the panel
 * @param toggleActionId one of the plugin's own actions, already registered, that toggles this panel:
 *                       View &gt; Tool Windows lists it, with its shortcut, instead of an application-made
 *                       {@code <id>.toggle} (since 0.8.1)
 * @param split whether the panel starts in its side's secondary half: the lower half of the left or right side, or
 *              the right half of the bottom area (since 0.13.0)
 */
public record PanelSpec(String id, String title, String icon, Anchor defaultAnchor, Optional<String> toggleActionId, boolean split) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        if (icon == null || !icon.matches("[a-z][a-z0-9.-]*(?::[a-z][a-z0-9.-]*)?")) throw new IllegalArgumentException("Invalid semantic icon name");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        Objects.requireNonNull(toggleActionId, "toggleActionId");
    }

    /** Semantic stripe icon resolved by the host. */
    public PanelSpec(String id, String title, String icon, Anchor anchor) {
        this(id, title, icon, anchor, Optional.empty(), false);
    }

    /**
     * Derives a value. Name an action when the plugin already has one that toggles this panel, so the
     * View menu and the palette offer it once.
     *
     * @param value the plugin's toggle action, or null for the application's {@code <id>.toggle}
     * @return a copy naming it, with the same {@code split}
     * @since 0.8.1
     */
    public PanelSpec withToggleActionId(String value) {
        return new PanelSpec(id, title, icon, defaultAnchor, Optional.ofNullable(value), split);
    }

    /**
     * Derives a value that starts in its side's secondary half: under the first half's tool window on the left or
     * right side, or to its right in the bottom area. A place the user chose still wins.
     *
     * @return a copy with {@code split} set
     * @since 0.13.0
     */
    public PanelSpec withSplit() {
        return new PanelSpec(id, title, icon, defaultAnchor, toggleActionId, true);
    }
}
