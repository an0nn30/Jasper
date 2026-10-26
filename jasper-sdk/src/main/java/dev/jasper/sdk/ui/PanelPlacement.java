package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * A tool window's place: its side and half, and its mode. While it has a window of its own, the side and half are its
 * stripe button's, where it docks again. {@link PanelHost#placement()} reads it and {@link PanelHost#onPlacement}
 * hears it change.
 *
 * @param anchor the side it docks on, or its stripe button's while it has a window of its own
 * @param split whether it is in its side's secondary half: the lower half of the left or right side, or the right half
 *              of the bottom area, as {@link PanelSpec#split()} says of the place it starts in
 * @param mode docked, or in a window of its own
 * @since 0.22.0
 */
public record PanelPlacement(Anchor anchor, boolean split, PanelMode mode) {
    /** Requires the anchor and the mode. */
    public PanelPlacement {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * Whether the panel has a window of its own.
     *
     * @return true in {@link PanelMode#FLOAT} and {@link PanelMode#WINDOW}
     */
    public boolean ownWindow() { return mode != PanelMode.DOCKED; }
}
