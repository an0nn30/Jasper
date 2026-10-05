package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * Host-owned appearance values. Declarative views follow these automatically; specialized content
 * subscribes to refresh its domain palettes.
 */
public interface Appearance {
    /** Read a canonical semantic color on the UI thread. @param role canonical role @return current color */
    java.awt.Color color(SemanticColor role);
    /**
     * The current variant; safe from any thread.
     *
     * @return dark or light
     */
    Variant variant();

    /** Host-owned colors for specialized document widgets. UI thread only.
     * @return the active workspace's editor palette
     * @since 0.8.5
     */
    default EditorPalette editorPalette() { return EditorPalette.current(); }

    /** Current workspace variant; safe from any thread.
     * @return workspace darkness
     * @since 0.12.0
     */
    default Variant workspaceVariant() { return variant(); }

    /** Workspace control colors. UI thread only.
     * @return the current workspace palette
     * @since 0.12.0
     */
    default WorkspacePalette workspacePalette() { return WorkspacePalette.current(); }

    /** Workspace syntax colors. UI thread only.
     * @return the current syntax palette
     * @since 0.12.0
     */
    default SyntaxPalette syntaxPalette() { return SyntaxPalette.current(); }

    /** How opaque the workspace behind {@code component} is: 1 unless the component is in the workspace area of a
     * translucent window (where terminals and hosted content tabs live), in which case paint the component's own
     * background with this alpha (the window shows the desktop through it). Chrome, tool windows and side panels stay
     * opaque and report 1. Changes, including the component moving to another window, are reported through
     * {@link #onWorkspaceChanged}. UI thread only.
     * @param component a component of this plugin's, shown or not
     * @return the opacity in effect, from 0.1 to 1
     * @since 0.14.1
     */
    default double workspaceOpacity(java.awt.Component component) {
        java.util.Objects.requireNonNull(component, "component");
        return 1.0;
    }

    /** Reports workspace changes, including changes within the same variant and of {@link #workspaceOpacity}, on the UI thread.
     * @param handler receives the workspace variant after the new palettes are available
     * @return the registration, removed when closed or the plugin stops
     * @since 0.12.0
     */
    default Subscription onWorkspaceChanged(Consumer<Variant> handler) {
        return onChanged(java.util.Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Runs the handler on the UI thread after every chrome or workspace theme change, including
     * color changes within the same variant. Workspace opacity changes use onWorkspaceChanged.
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
