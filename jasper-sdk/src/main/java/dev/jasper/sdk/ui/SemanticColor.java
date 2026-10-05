package dev.jasper.sdk.ui;

import java.util.Optional;

/** Stable canonical theme roles. Paint opacity and geometry belong to the kit. */
public enum SemanticColor {
    SURFACE_APP("surface.app"),
    SURFACE_PANEL("surface.panel"),
    SURFACE_PANEL_RAISED("surface.panel.raised"),
    SURFACE_DIALOG("surface.dialog"),
    SURFACE_CARD("surface.card"),
    SURFACE_SIDEBAR("surface.sidebar"),
    SURFACE_INPUT("surface.input"),
    SURFACE_CONTROL("surface.control"),
    SURFACE_CONTROL_HOVER("surface.control.hover"),
    SURFACE_MENU("surface.menu"),
    SURFACE_TERMINAL("surface.terminal"),
    SURFACE_SCRIM("surface.scrim"),
    BORDER_WINDOW("border.window"),
    BORDER_SUBTLE("border.subtle"),
    BORDER_INPUT("border.input"),
    BORDER_CONTROL("border.control"),
    BORDER_MENU("border.menu"),
    BORDER_FOCUS("border.focus"),
    BORDER_ERROR("border.error"),
    TEXT_PRIMARY("text.primary"),
    TEXT_SECONDARY("text.secondary"),
    TEXT_TERTIARY("text.tertiary"),
    TEXT_PLACEHOLDER("text.placeholder"),
    TEXT_ONACCENT("text.onAccent"),
    TEXT_ONACCENT_SECONDARY("text.onAccent.secondary"),
    TEXT_LINK("text.link"),
    TEXT_LINK_HOVER("text.link.hover"),
    TEXT_ERROR("text.error"),
    TEXT_SUCCESS("text.success"),
    ACCENT("accent"),
    ACCENT_HOVER("accent.hover"),
    SELECTION_BG("selection.bg"),
    HOVER_OVERLAY("hover.overlay"),
    SEGMENT_SELECTED_BG("segment.selected.bg"),
    TOGGLE_TRACK_OFF("toggle.track.off"),
    TOGGLE_KNOB("toggle.knob"),
    STATUS_SUCCESS("status.success"),
    STATUS_WARNING("status.warning"),
    STATUS_DANGER("status.danger"),
    STATUS_IDLE("status.idle"),
    HUE_BLUE("hue.blue"),
    HUE_GREEN("hue.green"),
    HUE_AMBER("hue.amber"),
    HUE_PURPLE("hue.purple"),
    HUE_NEUTRAL("hue.neutral"),
    BUTTON_SECONDARY_BG("button.secondary.bg"),
    BUTTON_SECONDARY_BG_HOVER("button.secondary.bg.hover");

    private final String key;
    SemanticColor(String key) { this.key = key; }
    /** The case-sensitive native TOML key. */
    public String key() { return key; }
    /** Finds a standard semantic name. */
    public static Optional<SemanticColor> of(String key) {
        for (var role : values()) if (role.key.equals(key)) return Optional.of(role);
        return Optional.empty();
    }
}
