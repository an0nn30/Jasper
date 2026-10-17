package dev.jasper.ui.swing;

import dev.jasper.ui.theme.PaintPair;
import java.util.List;

/**
 * A control point's strategy (Enterprise presentation spec 6.4), built once per kit by {@link KitFaces#of}: it draws
 * what its control asks, from the arguments it is given, and never reads the kit.
 */
interface Face {
    /** The (slot, against, guard) pairs this face paints, each held by its guard where it is painted (spec 7.3). */
    List<PaintPair> pairs();
}
