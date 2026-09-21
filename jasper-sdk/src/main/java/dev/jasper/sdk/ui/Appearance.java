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
}
