package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * Where the palette's view lives in its window and how a drag attaches it there (Enterprise presentation spec 11.5):
 * dropped from the title field ({@link TitleFieldHome}) or at the bottom-left of the dock work area ({@link CornerHome}).
 * The three-argument {@code WorkspaceChrome.of} picks one from the look's palette. Every rectangle is in the palette
 * overlay's coordinates. EDT only.
 */
sealed interface PaletteHome permits TitleFieldHome, CornerHome {
    /**
     * What a home reads of its window.
     *
     * @param window the overlay's own bounds
     * @param workspace the window's workspace (the whole content, its rail and tool panels included)
     * @param field the title field's bounds while the card can drop from it, else null
     * @param titleClear the title row clear of the window controls
     * @param workArea the dock work area (between the stripes, above the bottom strip and the status bar), or null
     * @param margin the view's inset from the window's edges
     * @param compact whether the window is in Compact mode
     */
    record Geometry(Rectangle window, Rectangle workspace, Rectangle field, Rectangle titleClear, Rectangle workArea, int margin,
                    boolean compact) { }

    /** The view's home for its {@code preferred} size, or null when it has none here. */
    Rectangle home(Geometry geometry, Dimension preferred);
    /** Whether a view dragged to {@code bounds} re-attaches at {@code home}. */
    boolean attaches(Rectangle home, Rectangle bounds, int reach);
    /** Whether a dragged view snaps to the window's centre line and default top. */
    boolean guides();
    /** The view's size away from its home. */
    Dimension detachedSize(Geometry geometry, Dimension preferred);
    /** The saved place of this home's kind for a view of {@code size}, if one is saved. */
    Optional<Rectangle> saved(UiState state, Geometry geometry, Dimension size);
    /** Saves {@code bounds} as this home's kind of place; a snapped guide saves its exact line. */
    void save(UiState state, Geometry geometry, Rectangle bounds, boolean snappedX, boolean snappedY);
    /** Forgets this home's kind of place. */
    void forget(UiState state);
    /** Where the view sits with no saved place and no home. */
    Rectangle fallback(Geometry geometry, Dimension preferred);
    /** The height the view should fit in at its home. */
    int available(Geometry geometry);

    /** {@code value} clamped to 0 to 1. */
    static double fraction(double value) { return Math.max(0, Math.min(1, value)); }
}
