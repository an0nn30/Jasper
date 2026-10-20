package dev.jasper.ui.kit;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import java.util.Objects;

/**
 * A look: the metric catalog, type scale, faces, control variants, colour slots and FlatLaf key set that the Swing kit
 * and the native chrome paint with (Enterprise presentation spec 6.1). Immutable and compared by value; the host fixes
 * one per process before any window.
 *
 * @param id a short name for diagnostics
 * @param metrics the logical geometry
 * @param type the type scale
 * @param faces the faces text draws in
 * @param controls the value of each control point
 * @param colors the colour slots
 * @param uiKeys the FlatLaf colour keys the theme's roles fill
 */
public record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots colors, UiKeySet uiKeys) {
    /** The default look: the default catalog, the 13-point type scale, the platform faces, the default controls, slots and keys. */
    public static final Kit DEFAULT = new Kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, ControlVariants.DEFAULT,
        ColorSlots.DEFAULT, UiKeySet.DEFAULT);
    /** The classic base a kit file names with {@code base = "classic"}: the classic catalog, scale, controls, slots and keys. */
    public static final Kit CLASSIC = new Kit("classic", DesignMetrics.CLASSIC, TypeScale.CLASSIC, FaceSet.PLATFORM, ControlVariants.CLASSIC,
        ColorSlots.CLASSIC, UiKeySet.CLASSIC);

    /** Every part is required, and the id must not be blank. */
    public Kit {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(metrics, "metrics"); Objects.requireNonNull(type, "type");
        Objects.requireNonNull(faces, "faces"); Objects.requireNonNull(controls, "controls"); Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(uiKeys, "uiKeys");
        if (id.isBlank()) throw new IllegalArgumentException("Blank kit id");
    }
}
