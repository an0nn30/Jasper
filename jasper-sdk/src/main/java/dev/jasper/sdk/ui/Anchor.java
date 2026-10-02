package dev.jasper.sdk.ui;

/**
 * Where a panel appears by default; the user may move it. A side has two halves: a panel starts in the first unless
 * its spec says {@link PanelSpec#withSplit()}.
 */
public enum Anchor {
    /** The left side, beside the left stripe: its upper half, or with a split its lower half. */
    LEFT,
    /** The right side, beside the right stripe: its upper half, or with a split its lower half. */
    RIGHT,
    /** The bottom area, the window's full width under the sides and the terminal: its left half, or with a split its right half. */
    BOTTOM
}
