package dev.jasper.app.presentation;

/**
 * The window shells' projection (spec 9.1).
 *
 * @param unifiedTitle whether frames draw the unified macOS title bar
 * @param windowMenus whether each window shows its menus in a row of its own
 * @param followThemeAppearance whether a native-titled frame's appearance follows the theme's darkness
 */
public record ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance) { }
