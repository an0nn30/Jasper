package dev.jasper.app.config;

/** Whether the desktop behind a translucent workspace is blurred (macOS). */
public enum WorkspaceBlur {
    /** Follow the workspace theme's {@code [workspace] blur}. */
    THEME,
    /** Blur, whatever the workspace theme says. */
    ON,
    /** Never blur, whatever the workspace theme says. */
    OFF
}
