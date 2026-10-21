package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * What plugins learn about the presentation (spec 9.1), read only by the plugin runtime's adapter.
 *
 * @param classic whether the kit's base is classic
 * @param classicIcons whether the look paints with IntelliJ's classic icons
 * @param stripes whether tool windows are reached by labelled stripes rather than icon rails
 * @param titleBar the title bar on this platform
 * @param menus where the menus are on this platform, in the Normal view with the platform's default decorations
 * @param search how the toolbar offers Search Everywhere
 */
public record PluginLook(boolean classic, boolean classicIcons, boolean stripes, TitleBar titleBar, Menus menus, Search search) {
    /** The unified band, the platform's own title bar, or IntelliJ Classic's. */
    public enum TitleBar { UNIFIED, NATIVE, CLASSIC }
    /** The macOS screen menu bar, the window's title bar, or a menu bar row in the window. */
    public enum Menus { SCREEN_MENU_BAR, TITLE_BAR, WINDOW }
    /** A search field or an icon button. */
    public enum Search { FIELD, ICON }

    /** Every variant is required. */
    public PluginLook { Objects.requireNonNull(titleBar); Objects.requireNonNull(menus); Objects.requireNonNull(search); }
}
