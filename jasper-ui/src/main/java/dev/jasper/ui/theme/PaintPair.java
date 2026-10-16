package dev.jasper.ui.theme;

import java.util.Objects;

/**
 * A pair a face paints, as it declares it: {@code slot} painted on or beside {@code against}, held there by
 * {@code guard} at paint time ({@link SurfaceContrast#painted}). Neither role is changed.
 *
 * @param slot the role painted
 * @param against the role it must stand apart from
 * @param guard the repair applied where it is painted
 */
public record PaintPair(ColorRole slot, ColorRole against, SurfaceContrast.Guard guard) {
    /** Every part is required. */
    public PaintPair {
        Objects.requireNonNull(slot, "slot"); Objects.requireNonNull(against, "against"); Objects.requireNonNull(guard, "guard");
    }
}
