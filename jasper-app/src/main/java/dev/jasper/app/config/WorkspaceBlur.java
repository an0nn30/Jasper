package dev.jasper.app.config;

/** Whether the desktop behind a translucent workspace is blurred (macOS). */
public enum WorkspaceBlur {
    /** Follow the theme's blur: Slate Steel does not blur. */
    THEME,
    /** Blur, whatever the theme says. */
    ON,
    /** Never blur, whatever the theme says. */
    OFF
}
