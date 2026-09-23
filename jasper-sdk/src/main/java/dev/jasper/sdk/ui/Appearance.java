package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * The application's look. Plugins build ordinary Swing components and the global look and feel applies
 * to them; this is for the few things that must follow the theme by hand.
 */
public interface Appearance {
    /**
     * The current variant; safe from any thread.
     *
     * @return dark or light
     */
    Variant variant();

    /**
     * Runs the handler on the UI thread after each change of variant.
     *
     * @param handler receives the new variant
     * @return the registration
     */
    Subscription onChanged(Consumer<Variant> handler);

    /**
     * A 16 by 16 icon from an SVG in the plugin's own jars, recolored to the chrome's foreground so it
     * follows the theme without being reloaded. Use monochrome artwork.
     *
     * @param svgResourcePath classpath path without a leading slash, for example {@code dev/example/tool/run.svg}
     * @return the icon
     * @throws IllegalArgumentException when the plugin's jars hold no such resource
     */
    Icon icon(String svgResourcePath);
    /**
     * A 16 by 16 icon using the plugin's monochrome SVG in modern mode and bundled
     * OldGNOME2 artwork in retro mode. Jasper adapts it to 28 pixels in retro host
     * toolbars without resizing this shared icon. Both arguments are validated in both skins.
     * Older implementations inherit the modern-only fallback.
     *
     * @param modernSvgResourcePath SVG classpath path in the plugin's jars, without a leading slash
     * @param retroIcon bundled retro artwork
     * @return the icon for the running skin
     * @throws NullPointerException if retroIcon is null
     * @throws IllegalArgumentException if the SVG path is null or its resource is absent
     * @since 0.7.2
     */
    default Icon icon(String modernSvgResourcePath, OldGnomeIcon retroIcon) {
        java.util.Objects.requireNonNull(retroIcon, "retroIcon");
        return icon(modernSvgResourcePath);
    }
}
