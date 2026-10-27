package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.function.Consumer;

/** One instance's place: in its window's dock or in a window of its own. All methods are UI-thread only. */
public interface PanelHost {
    /**
     * The window whose dock holds this instance, also while it has a window of its own
     * ({@link PanelPlacement#ownWindow()}).
     *
     * @return the window
     */
    WindowHandle window();

    /**
     * Replaces this instance's header metadata and icon actions. Callbacks become inert when the instance closes.
     * @param header the header description
     * @since 0.14.2
     */
    void setHeader(PanelHeader header);

    /**
     * Shows the panel: docked, in its slot, hiding whatever else showed there; with a window of its own, in that window.
     * Called from the panel's own toggle action as its handler runs, it also makes the panel the active tool window under
     * a look with flush tool windows (since 0.18.2).
     */
    void show();

    /** Hides the panel. */
    void hide();

    /**
     * Whether the panel is showing.
     *
     * @return true while visible
     */
    boolean visible();

    /**
     * Runs the handler after each change of the panel's visibility.
     *
     * @param handler receives the new visibility
     * @return the registration
     */
    Subscription onVisibility(Consumer<Boolean> handler);

    /**
     * Where the panel is: its side and half, and whether it is docked or has a window of its own. A panel built in a
     * window of its own, as one restored there at start-up is, reads that in its factory; a closed instance reads where
     * it closed.
     *
     * @return the current placement
     * @since 0.22.0
     */
    PanelPlacement placement();

    /**
     * Runs the handler after each change of the panel's placement, once its content is in its new place: a move to
     * another side or half, or another mode. A move within its half (its stripe button reordered) and a change of
     * visibility alone run nothing. A panel that lays itself out another way in a window of its own describes the other
     * {@link ViewSpec} here and passes it to {@link UiView#update(ViewSpec)}. Callbacks become inert when the instance
     * closes.
     *
     * @param handler receives the new placement
     * @return the registration
     * @since 0.22.0
     */
    Subscription onPlacement(Consumer<PanelPlacement> handler);

    /**
     * Runs the handler when this instance is discarded: when that window closes or the panel is removed.
     *
     * @param handler cleanup for this instance
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
