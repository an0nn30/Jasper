package dev.jasper.app.appearance;

import dev.jasper.app.config.GlassSettings;

/**
 * How translucent the workspace is: {@code opacity} from {@link #MIN_OPACITY} to 1 (1 is opaque), and whether the
 * desktop behind translucent pixels is blurred. macOS materials have a fixed strength, so blur is on or off.
 *
 * @param opacity the default background's opacity
 * @param blur whether the desktop behind translucent pixels is blurred
 */
public record GlassLevels(double opacity, boolean blur) {
    /** The least opacity a theme or setting may ask for. */
    public static final double MIN_OPACITY = 0.10;
    /** An opaque, unblurred workspace: every theme without a {@code [workspace]} table. */
    public static final GlassLevels OPAQUE = new GlassLevels(1, false);

    public GlassLevels {
        if (!(opacity >= MIN_OPACITY && opacity <= 1)) throw new IllegalArgumentException("opacity must be from 0.1 to 1");
    }

    /**
     * The levels a window asks for: each saved value wins over the workspace theme's.
     *
     * @param saved the saved settings
     * @param theme the workspace theme's levels
     * @return the effective levels
     */
    public static GlassLevels resolve(GlassSettings saved, GlassLevels theme) {
        return new GlassLevels(saved.opacity().orElse(theme.opacity()), saved.blur().orElse(theme.blur()));
    }

    /** Whether anything behind the workspace shows through. */
    public boolean translucent() { return opacity < 1; }
}
