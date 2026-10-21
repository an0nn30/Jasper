package dev.jasper.sdk;

/**
 * Whether a theme is dark or light, so a plugin can pick colours and icons that suit it. The UI package reports one
 * variant for the chrome theme and one for the workspace theme.
 */
public enum Variant {
    /** A dark theme. The terminal's colour scheme is configured separately and may differ. */
    DARK,
    /** A light theme. The terminal's colour scheme is configured separately and may differ. */
    LIGHT
}
