package dev.jasper.app.config;

import java.util.Optional;

/**
 * The saved workspace glass, in percent. {@link #UNSET_OPACITY} and {@link #UNSET_BLUR} mean "use the workspace
 * theme's value"; they lie outside the settings' ranges, so they never collide with a saved value.
 *
 * @param opacityPercent 10–100, or {@link #UNSET_OPACITY}
 * @param blurPercent 0–100, or {@link #UNSET_BLUR}
 */
public record GlassSettings(float opacityPercent, float blurPercent) {
    /** The opacity a configuration without {@code ui.workspace.opacity} has. */
    public static final float UNSET_OPACITY = 0f;
    /** The blur a configuration without {@code ui.workspace.blur} has. */
    public static final float UNSET_BLUR = -1f;
    /** Both values defer to the theme. */
    public static final GlassSettings UNSET = new GlassSettings(UNSET_OPACITY, UNSET_BLUR);

    public GlassSettings {
        if (opacityPercent != UNSET_OPACITY && !(opacityPercent >= 10f && opacityPercent <= 100f))
            throw new IllegalArgumentException("Opacity must be 10\u2013100 percent.");
        if (blurPercent != UNSET_BLUR && !(blurPercent >= 0f && blurPercent <= 100f))
            throw new IllegalArgumentException("Blur must be 0\u2013100 percent.");
    }

    /** The saved opacity as a fraction, or empty to use the theme's. */
    public Optional<Double> opacity() { return opacityPercent == UNSET_OPACITY ? Optional.empty() : Optional.of(opacityPercent / 100.0); }

    /** The saved blur as a fraction, or empty to use the theme's. */
    public Optional<Double> blur() { return blurPercent == UNSET_BLUR ? Optional.empty() : Optional.of(blurPercent / 100.0); }
}
