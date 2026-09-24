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
     * A host-owned icon for a semantic name. Jasper selects the artwork for the running skin, including
     * the desktop icon theme when the host runs its GTK style; plugins need no resource path or skin
     * check. Compact icons are 16 pixels; retro host toolbars use an independent 28-pixel variant and
     * GTK host toolbars a 24-pixel one.
     *
     * @param name the meaning of the icon
     * @return the host's icon for the running skin
     * @throws NullPointerException if name is null
     * @throws UnsupportedOperationException if an older custom host has no named catalog
     * @since 0.7.4
     */
    default Icon icon(IconName name) {
        java.util.Objects.requireNonNull(name, "name");
        throw new UnsupportedOperationException("Host does not provide named icons");
    }

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
     * A 16 by 16 icon using the plugin's monochrome SVG in modern mode, bundled OldGNOME2 artwork in
     * retro mode, and the matching desktop icon-theme artwork (else the OldGNOME2 artwork) in GTK mode.
     * Jasper adapts it to 28 pixels in retro host toolbars without resizing this shared icon. Both
     * arguments are validated in both skins. Older implementations inherit the modern-only fallback.
     *
     * @param modernSvgResourcePath SVG classpath path in the plugin's jars, without a leading slash
     * @param retroIcon bundled retro artwork
     * @return the icon for the running skin
     * @throws NullPointerException if retroIcon is null
     * @throws IllegalArgumentException if the SVG path is null or its resource is absent
     * @since 0.7.4
     */
    default Icon icon(String modernSvgResourcePath, OldGnomeIcon retroIcon) {
        java.util.Objects.requireNonNull(retroIcon, "retroIcon");
        return icon(modernSvgResourcePath);
    }
}
