package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.util.function.Consumer;

/**
 * Host-owned appearance values and the look Jasper runs with. Declarative views follow these automatically; specialized
 * content subscribes to refresh its domain palettes.
 */
public interface Appearance {
    /**
     * Read a canonical semantic color on the UI thread.
     *
     * @param role canonical role
     * @return current color
     */
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
    EditorPalette editorPalette();

    /** Current workspace variant; safe from any thread.
     * @return workspace darkness
     * @since 0.12.0
     */
    default Variant workspaceVariant() { return variant(); }

    /** Workspace control colors. UI thread only.
     * @return the current workspace palette
     * @since 0.12.0
     */
    WorkspacePalette workspacePalette();

    /** Workspace syntax colors. UI thread only.
     * @return the current syntax palette
     * @since 0.12.0
     */
    SyntaxPalette syntaxPalette();

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

    /** The look Jasper runs with, chosen at start: the same value for every plugin until Jasper restarts. Safe from any thread.
     * @return the presentation
     * @since 0.18.2
     */
    Presentation presentation();

}
