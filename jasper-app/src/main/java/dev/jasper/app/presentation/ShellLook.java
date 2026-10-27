package dev.jasper.app.presentation;

/**
 * The window shells' projection (spec 9.1).
 *
 * @param unifiedTitle whether frames draw the unified macOS title bar
 * @param windowMenus whether a window shows its menus in a row of its own
 * @param followThemeAppearance whether a native-titled frame's appearance follows the theme's darkness
 * @param classicTitle whether Jasper's window frames draw IntelliJ Classic's title: the band on macOS, the custom title bar
 *     on Windows
 */
public record ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance, boolean classicTitle) {
    /**
     * A shell look without a classic title.
     *
     * @param unifiedTitle whether frames draw the unified macOS title bar
     * @param windowMenus whether a window shows its menus in a row of its own
     * @param followThemeAppearance whether a native-titled frame's appearance follows the theme's darkness
     */
    public ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance) {
        this(unifiedTitle, windowMenus, followThemeAppearance, false);
    }
}
