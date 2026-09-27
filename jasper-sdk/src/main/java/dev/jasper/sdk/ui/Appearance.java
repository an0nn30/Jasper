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

    /** Host-owned colors for specialized document widgets. UI thread only.
     * @return the active theme's editor palette
     * @since 0.8.5
     */
    default EditorPalette editorPalette() { return EditorPalette.current(); }

    /**
     * Runs the handler on the UI thread after each change of variant.
     *
     * @param handler receives the new variant
     * @return the registration
     */
    Subscription onChanged(Consumer<Variant> handler);

    /**
     * A host-owned 16 by 16 icon for a semantic name. Jasper owns the artwork; plugins need no
     * resource path or appearance check.
     *
     * @param name the meaning of the icon
     * @return the host's icon
     * @throws NullPointerException if name is null
     * @throws UnsupportedOperationException if an older custom host has no named catalog
     * @since 0.7.4
     */
    default Icon icon(IconName name) {
        java.util.Objects.requireNonNull(name, "name");
        throw new UnsupportedOperationException("Host does not provide named icons");
    }

    /**
     * A choice from the complete host-owned New UI catalog. The host selects light/dark artwork
     * and fits it proportionally inside a 16 by 16 box. Prefer {@link #icon(IconName)} when a
     * semantic meaning exists. Custom plugin SVG loading remains {@link #icon(String)}.
     * @param name catalog choice, including optional upstream size variants
     * @return the host's icon
     * @throws NullPointerException if name is null
     * @throws UnsupportedOperationException if an older host has no bundled catalog
     * @since 0.8.2
     */
    default Icon icon(BundledIcon name) {
        java.util.Objects.requireNonNull(name, "name");
        throw new UnsupportedOperationException("Host does not provide bundled icons");
    }

    /**
     * A 16 by 16 icon from an SVG in the plugin's own jars, drawn with its authored colours. Colours
     * from the IntelliJ light icon palette (grey {@code #6E6E6E}, blue {@code #389FD6}, green
     * {@code #59A869}, red {@code #DB5860}, yellow {@code #EDA200}) follow dark and light themes
     * without reloading; other colours are drawn as they are. Since 0.7.6 the icon is no longer
     * recoloured to the chrome foreground.
     *
     * @param svgResourcePath classpath path without a leading slash, for example {@code dev/example/tool/run.svg}
     * @return the icon
     * @throws IllegalArgumentException when the plugin's jars hold no such resource
     */
    Icon icon(String svgResourcePath);
}
