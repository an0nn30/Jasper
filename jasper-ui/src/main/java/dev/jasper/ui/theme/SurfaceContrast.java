package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * The one readability rule for colors painted on a specific surface: the generator's derived-token floors, chrome
 * status text on {@code surface.status}, rail badges on {@code surface.app} and surface-tinted icons all use it.
 * {@link #on} applies a floor only where a role is painted; the resolved role itself is never changed, so shared
 * roles stay as resolved on every other surface. A face declares each pair it paints ({@link PaintPair}) with its
 * {@link Guard} and paints it with {@link #painted}. Pure: no Swing state.
 */
public final class SurfaceContrast {
    /** Text and status text, as the generator's derived status/link tokens. */
    public static final double TEXT = 4.5;
    /** Non-text indicators (badges, dots, icon strokes). */
    public static final double INDICATOR = 3;
    /** How far apart in CIE L* an inactive tab's surface stays from the active tab's. */
    public static final double TAB_LIGHTNESS = 9;
    /** Structural boundaries (window and panel rules, dividers) on the surface they divide. */
    public static final double STRUCTURAL = 1.3;
    /** Control boundaries (field, button and box edges) on the surface the control stands on. */
    public static final double CONTROL = 1.5;
    /** The highest contrast every opaque colour reaches against black or white, so the highest floor a guard takes. */
    public static final double REACHABLE = 4.58;
    /** The largest CIE L* distance {@link #apart} always reaches. */
    public static final double REACHABLE_LIGHTNESS = 50;
    private SurfaceContrast() {}

    /**
     * A repair applied where a pair is painted: a contrast floor ({@link #TEXT}, {@link #INDICATOR}, {@link #STRUCTURAL},
     * {@link #CONTROL}) or a CIE L* distance ({@link #apart(double)}). Every guard terminates on any opaque colours.
     *
     * @param kind whether {@code floor} is a contrast ratio or a lightness distance
     * @param floor the ratio (1 to {@link SurfaceContrast#REACHABLE}) or the distance (0 to
     *     {@link SurfaceContrast#REACHABLE_LIGHTNESS})
     */
    public record Guard(Kind kind, double floor) {
        /** What a guard's floor measures. */
        public enum Kind {
            /** The WCAG contrast ratio of the painted colour and the colour it stands on. */
            CONTRAST,
            /** The CIE L* distance between the painted surface and the one it stands apart from. */
            APART
        }

        /** Text and status text. */
        public static final Guard TEXT = new Guard(Kind.CONTRAST, SurfaceContrast.TEXT);
        /** Non-text indicators: a checked box, a radio dot, a status dot, a swatch. */
        public static final Guard INDICATOR = new Guard(Kind.CONTRAST, SurfaceContrast.INDICATOR);
        /** Structural boundaries. */
        public static final Guard STRUCTURAL = new Guard(Kind.CONTRAST, SurfaceContrast.STRUCTURAL);
        /** Control boundaries. */
        public static final Guard CONTROL = new Guard(Kind.CONTRAST, SurfaceContrast.CONTROL);

        /** A floor that every opaque colour can reach. */
        public Guard {
            Objects.requireNonNull(kind, "kind");
            double limit = kind == Kind.CONTRAST ? REACHABLE : REACHABLE_LIGHTNESS;
            if (!(floor >= (kind == Kind.CONTRAST ? 1 : 0) && floor <= limit))
                throw new IllegalArgumentException(kind + " floor out of range: " + floor);
        }

        /** Surfaces kept {@code lightness} CIE L* apart, as an {@code apart} recipe names. */
        public static Guard apart(double lightness) { return new Guard(Kind.APART, lightness); }

        /**
         * {@code color} as painted against {@code against}: unchanged when it holds; otherwise a contrast guard moves it
         * toward {@code toward}, then black or white, and an apart guard moves it as {@link SurfaceContrast#apart} does.
         */
        public Color apply(Color color, Color against, Color toward) {
            if (holds(color, against)) return color;
            return switch (kind) {
                case CONTRAST -> readable(color, against, floor, toward);
                case APART -> SurfaceContrast.apart(color, against, floor);
            };
        }

        /** Whether {@code color} meets this guard against {@code against}. */
        public boolean holds(Color color, Color against) {
            return switch (kind) {
                case CONTRAST -> ColorMath.contrast(color, against) >= floor;
                case APART -> Math.abs(ColorMath.lightness(color) - ColorMath.lightness(against)) >= floor;
            };
        }
    }

    /**
     * {@code pair}'s slot as painted in {@code theme}: its guard applied against the pair's other role, toward
     * {@code text.primary} first. A translucent slot is measured as it shows, laid over the role it is painted on (a
     * translucent {@code against} first over {@code surface.app}); while that holds the slot keeps its alpha, and a
     * repair is opaque. The roles themselves are never changed.
     */
    public static Color painted(GeneratedTheme theme, PaintPair pair) {
        Color under = flat(pair.against().color(theme), theme.semantic().color(SemanticColor.SURFACE_APP));
        Color slot = pair.slot().color(theme), shown = flat(slot, under);
        return pair.guard().holds(shown, under) ? slot
            : pair.guard().apply(shown, under, theme.semantic().color(SemanticColor.TEXT_PRIMARY));
    }

    /** {@code color} as it shows over {@code under} (taken as opaque): itself when opaque, else laid over it. */
    static Color flat(Color color, Color under) {
        Color base = under.getAlpha() == 255 ? under : new Color(under.getRGB() | 0xFF000000);
        return color.getAlpha() == 255 ? color : ColorMath.mix(color, base, color.getAlpha() / 255.0);
    }

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

    /**
     * {@code surface} kept at least {@code minimum} CIE L* from {@code from}: unchanged when it already is, on either side;
     * otherwise moved toward black, in 1% steps, just until it is, or toward white when {@code from} is too dark for that.
     */
    public static Color apart(Color surface, Color from, double minimum) {
        double target = ColorMath.lightness(from);
        if (Math.abs(target - ColorMath.lightness(surface)) >= minimum) return surface;
        Color toward = target >= minimum ? Color.BLACK : Color.WHITE;
        for (int step = 1; step <= 100; step++) {
            Color candidate = ColorMath.mix(toward, surface, step / 100.0);
            if (Math.abs(target - ColorMath.lightness(candidate)) >= minimum) return candidate;
        }
        return toward;
    }

    /**
     * {@code surface} kept at least {@code minimum} CIE L* beyond {@code from} on the side of {@code text}'s lightness
     * (lighter when the text is lighter than {@code from}, darker otherwise): unchanged when it already is; otherwise
     * moved toward white or black in 1% steps just until it is, or toward the other end when that side has no room. The
     * derived surfaces use it, so a dark variant's header strips stand lighter than its window surface.
     */
    public static Color beyond(Color surface, Color from, double minimum, Color text) {
        double target = ColorMath.lightness(from);
        boolean lighter = ColorMath.lightness(text) > target;
        if ((lighter ? ColorMath.lightness(surface) - target : target - ColorMath.lightness(surface)) >= minimum) return surface;
        for (Color end : lighter ? java.util.List.of(Color.WHITE, Color.BLACK) : java.util.List.of(Color.BLACK, Color.WHITE)) {
            boolean up = end.equals(Color.WHITE);
            for (int step = 1; step <= 100; step++) {
                Color candidate = ColorMath.mix(end, surface, step / 100.0);
                if ((up ? ColorMath.lightness(candidate) - target : target - ColorMath.lightness(candidate)) >= minimum) return candidate;
            }
        }
        return lighter ? Color.WHITE : Color.BLACK;
    }

    /** White or black, whichever contrasts more with {@code fill}. */
    public static Color blackOrWhite(Color fill) {
        return ColorMath.contrast(Color.WHITE, fill) >= ColorMath.contrast(Color.BLACK, fill) ? Color.WHITE : Color.BLACK;
    }
}
