package dev.jasper.ui.swing;

import java.awt.Color;
import java.awt.Dimension;

/**
 * How the rules between controls draw (plan 10b Rulings 2), read from the kit's buttons point: a separator's size and
 * colour, the colour of a document split's hairline and of a manager column's edge, and whether a manager column's
 * header is a header strip.
 */
interface SeparatorFace extends Face {
    /** A separator's size. */
    Dimension size(KitContext context);

    /** The colour of a separator, a document split's hairline and a manager column's edge. */
    Color rule(KitContext context);

    /** Whether the header of a manager column's panel paints as a header strip. */
    boolean paneStrips();
}
