package dev.jasper.app.appearance;

/**
 * How translucent the workspace is, as fractions: {@code opacity} from {@link #MIN_OPACITY} to 1 (1 is opaque) and
 * {@code blur} from 0 to 1, Terminal.app's scale for how strongly the desktop behind is blurred.
 *
 * @param opacity the default background's opacity
 * @param blur the blur behind translucent pixels
 */
public record GlassLevels(double opacity, double blur) {
    /** The least opacity a theme or setting may ask for. */
    public static final double MIN_OPACITY = 0.10;
    /** An opaque, unblurred workspace: every theme without a {@code [workspace]} table. */
    public static final GlassLevels OPAQUE = new GlassLevels(1, 0);

    public GlassLevels {
        if (!(opacity >= MIN_OPACITY && opacity <= 1)) throw new IllegalArgumentException("opacity must be from 0.1 to 1");
        if (!(blur >= 0 && blur <= 1)) throw new IllegalArgumentException("blur must be from 0 to 1");
    }

    /** Whether anything behind the workspace shows through. */
    public boolean translucent() { return opacity < 1; }
}
