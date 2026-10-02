package dev.jasper.app.config;

import java.util.Objects;
import java.util.Optional;

/**
 * The saved workspace glass. {@link #UNSET_OPACITY} and {@link WorkspaceBlur#THEME} mean "use the workspace theme's
 * value"; the unset opacity lies outside the setting's range, so it never collides with a saved value.
 *
 * @param opacityPercent 10–100, or {@link #UNSET_OPACITY}
 * @param blurChoice the saved blur choice
 */
public record GlassSettings(float opacityPercent, WorkspaceBlur blurChoice) {
    /** The opacity a configuration without {@code ui.workspace.opacity} has. */
    public static final float UNSET_OPACITY = 0f;
    /** Both values defer to the theme. */
    public static final GlassSettings UNSET = new GlassSettings(UNSET_OPACITY, WorkspaceBlur.THEME);

    public GlassSettings {
        if (opacityPercent != UNSET_OPACITY && !(opacityPercent >= 10f && opacityPercent <= 100f))
            throw new IllegalArgumentException("Opacity must be 10\u2013100 percent.");
        Objects.requireNonNull(blurChoice, "blurChoice");
    }

    /** The saved opacity as a fraction, or empty to use the theme's. */
    public Optional<Double> opacity() { return opacityPercent == UNSET_OPACITY ? Optional.empty() : Optional.of(opacityPercent / 100.0); }

    /** Whether to blur, or empty to use the theme's choice. */
    public Optional<Boolean> blur() {
        return switch (blurChoice) {
            case THEME -> Optional.empty();
            case ON -> Optional.of(true);
            case OFF -> Optional.of(false);
        };
    }
}
