package dev.jasper.ui.theme;

/**
 * Colours the classic shapes paint that a theme does not list, each computed from the variant's palette by
 * {@link DerivedPalette}. Host-private:
 * {@link SemanticPalette} does not hold them and the SDK does not see them. A variant may list an
 * {@linkplain #overridable() overridable} role's colour itself ({@link GeneratedTheme#derivedOverrides()}). Declared in
 * the order they are computed: a role is computed from semantic roles and the derived roles declared before it.
 */
public enum DerivedColor implements ColorRole {
    SURFACE_HEADER("surface.header", true),
    SURFACE_HEADER_ACTIVE("surface.header.active", true),
    SURFACE_STRIPE("surface.stripe", true),
    SURFACE_TABLE_HEADER("surface.tableHeader", true),
    SURFACE_ZEBRA("surface.zebra", true),
    SURFACE_ROW_HOVER("surface.rowHover", true),
    WARNING_GROUND("warning.ground", true),
    WARNING_BORDER("warning.border", true),
    DANGER_GROUND("danger.ground", false),
    DANGER_BORDER("danger.border", false),
    INFO_GROUND("info.ground", false),
    INFO_BORDER("info.border", false),
    SURFACE_FOOTER("surface.footer", false),
    BORDER_DIVIDER("border.divider", false),
    BORDER_ROW("border.row", false),
    BORDER_HOVER("border.hover", false),
    ACCENT_EDGE("accent.edge", false),
    BUTTON_END("button.end", false),
    TAG_BORDER_BLUE("tag.border.blue", false),
    TAG_BORDER_GREEN("tag.border.green", false),
    TAG_BORDER_AMBER("tag.border.amber", false),
    TAG_BORDER_PURPLE("tag.border.purple", false),
    TAG_BORDER_NEUTRAL("tag.border.neutral", false);

    private final String key;
    private final boolean overridable;

    DerivedColor(String key, boolean overridable) {
        this.key = key;
        this.overridable = overridable;
    }

    @Override public String key() { return key; }

    /** Whether a theme variant may list this role (an override key) instead of taking its computed colour. */
    public boolean overridable() { return overridable; }
}
