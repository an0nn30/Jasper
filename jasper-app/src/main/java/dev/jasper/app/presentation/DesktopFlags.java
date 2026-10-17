package dev.jasper.app.presentation;

/**
 * What the bootstrap commits before AWT (spec 9.1): the macOS screen menu bar and the operating system's title bar.
 *
 * @param screenMenuBar whether macOS shows menus in the screen menu bar
 * @param nativeTitle whether windows take the operating system's title bar
 */
public record DesktopFlags(boolean screenMenuBar, boolean nativeTitle) {
    /** The default look's flags. */
    public static final DesktopFlags STANDARD = new DesktopFlags(true, false);
}
