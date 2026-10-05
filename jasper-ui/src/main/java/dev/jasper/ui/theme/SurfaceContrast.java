package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * The one readability rule for colors painted on a specific surface: the generator's derived-token floors, chrome
 * status text on {@code surface.status}, rail badges on {@code surface.app} and surface-tinted icons all use it.
 * {@link #on} applies a floor only where a role is painted; the resolved role itself is never changed, so shared
 * roles stay as resolved on every other surface. Pure: no Swing state.
 */
public final class SurfaceContrast {
    /** Text and status text, as the generator's derived status/link tokens. */
    public static final double TEXT = 4.5;
    /** Non-text indicators (badges, dots, icon strokes). */
    public static final double INDICATOR = 3;
    private SurfaceContrast() {}

    /** {@code role} as painted on {@code surface}: unchanged when it meets {@code minimum}, else made readable there. */
    public static Color on(SemanticPalette palette, SemanticColor role, SemanticColor surface, double minimum) {
        Color color = palette.color(Objects.requireNonNull(role)), fill = palette.color(Objects.requireNonNull(surface));
        return ColorMath.contrast(color, fill) >= minimum ? color : readable(color, fill, minimum, palette.color(SemanticColor.TEXT_PRIMARY));
    }

    /** {@code color} moved toward {@code toward} until it meets {@code minimum} on {@code fill}, else toward black or white. */
    public static Color readable(Color color, Color fill, double minimum, Color toward) {
        Color adjusted = ColorMath.readable(color, fill, minimum, toward);
        return ColorMath.contrast(adjusted, fill) >= minimum ? adjusted : ColorMath.readable(color, fill, minimum, blackOrWhite(fill));
    }

    /** White or black, whichever contrasts more with {@code fill}. */
    public static Color blackOrWhite(Color fill) {
        return ColorMath.contrast(Color.WHITE, fill) >= ColorMath.contrast(Color.BLACK, fill) ? Color.WHITE : Color.BLACK;
    }
}
