package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * A panel as the stripes show it: an icon on a tool window stripe and, while it shows, a tool window with a header.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; unless {@code toggleActionId}
 *           names one, the application also registers the action {@code <id>.toggle}, which users
 *           may bind under {@code [keybindings]}
 * @param title non-blank title, used for the stripe tooltip, the tool window's header and the application's toggle action
 * @param icon transitional legacy stripe icon; null when iconName is supplied
 * @param iconName semantic icon name; null only for a transitional legacy icon
 * @param defaultAnchor the side used until the user moves the panel
 * @param toggleActionId one of the plugin's own actions, already registered, that toggles this panel:
 *                       View &gt; Tools lists it, with its shortcut, instead of an application-made
 *                       {@code <id>.toggle} (since 0.8.1)
 * @param split whether the panel starts in its side's secondary half: the lower half of the left or right side, or
 *              the right half of the bottom area (since 0.13.0)
 */
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId, boolean split, String iconName) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        if ((icon == null) == (iconName == null)) throw new IllegalArgumentException("Exactly one icon source is required");
        if (iconName != null && !iconName.matches("[a-z][a-z0-9.-]*(?::[a-z][a-z0-9.-]*)?")) throw new IllegalArgumentException("Invalid semantic icon name");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        Objects.requireNonNull(toggleActionId, "toggleActionId");
    }

    /** Transitional legacy constructor. */
    public PanelSpec(String id, String title, Icon icon, Anchor anchor, Optional<String> toggle, boolean split) {
        this(id, title, icon, anchor, toggle, split, null);
    }
    /** Semantic stripe icon resolved by the host. */
    public PanelSpec(String id, String title, String iconName, Anchor anchor) {
        this(id, title, null, anchor, Optional.empty(), false, iconName);
    }
    /** Semantic stripe icon with placement and toggle identity. */
    public PanelSpec(String id, String title, String iconName, Anchor anchor, Optional<String> toggle, boolean split) {
        this(id, title, null, anchor, toggle, split, iconName);
    }
    /**
     * A panel toggled by the application's own {@code <id>.toggle} action, in its side's first half.
     *
     * @param id the panel id
     * @param title the title
     * @param icon the stripe icon
     * @param defaultAnchor the starting side
     */
    public PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {
        this(id, title, icon, defaultAnchor, Optional.empty(), false);
    }

    /**
     * A panel in its side's first half. Before 0.13.0 this was the canonical constructor; it stays so that plugins
     * written for earlier SDKs compile unchanged; they need only the new {@code sdk} range.
     *
     * @param id the panel id
     * @param title the title
     * @param icon the stripe icon
     * @param defaultAnchor the starting side
     * @param toggleActionId the plugin's own toggle action, or empty for the application's {@code <id>.toggle}
     */
    public PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId) {
        this(id, title, icon, defaultAnchor, toggleActionId, false);
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
        return new PanelSpec(id, title, icon, defaultAnchor, Optional.ofNullable(value), split, iconName);
    }

    /**
     * Derives a value that starts in its side's secondary half: under the first half's tool window on the left or
     * right side, or to its right in the bottom area. A place the user chose still wins.
     *
     * @return a copy with {@code split} set
     * @since 0.13.0
     */
    public PanelSpec withSplit() {
        return new PanelSpec(id, title, icon, defaultAnchor, toggleActionId, true, iconName);
    }
}
