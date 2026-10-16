package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A variant's {@link DerivedColor}s: each the variant's override when it lists one, else its recipe over the palette
 * and the derived roles before it. A pure function of the palette and the overrides; every colour is opaque, and a
 * translucent input is first laid over the surface its recipe mixes it with. Chrome under a title bar of its own derives
 * from {@link TitleBarTheme#of}'s theme, which carries the overrides.
 */
public final class DerivedPalette {
    /** A tint's share of a ground (Notices, pills), and of a border drawn round it. */
    private static final double GROUND = .14, EDGE = .40;
    /** Derived palettes kept, by theme identity: chrome, workspace and the title bar alternate while painting. */
    private static final int KEPT = 4;
    private static final GeneratedTheme[] THEMES = new GeneratedTheme[KEPT];
    private static final DerivedPalette[] PALETTES = new DerivedPalette[KEPT];
    private static int next;

    private final Map<DerivedColor, Color> values;

    private DerivedPalette(Map<DerivedColor, Color> values) { this.values = Map.copyOf(values); }

    /** {@code theme}'s derived roles, computed once per theme object while it is among the last few asked for. */
    public static synchronized DerivedPalette of(GeneratedTheme theme) {
        Objects.requireNonNull(theme, "theme");
        for (int i = 0; i < KEPT; i++) if (THEMES[i] == theme) return PALETTES[i];
        var palette = derive(theme.semantic(), theme.derivedOverrides());
        THEMES[next] = theme;
        PALETTES[next] = palette;
        next = (next + 1) % KEPT;
        return palette;
    }

    /** The colour of {@code role}; opaque. */
    public Color color(DerivedColor role) { return values.get(Objects.requireNonNull(role, "role")); }

    @Override public boolean equals(Object other) { return other instanceof DerivedPalette p && values.equals(p.values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return "DerivedPalette" + values; }

    /** Every derived role of {@code palette}, in declaration order: an override when {@code overrides} has one. */
    static DerivedPalette derive(SemanticPalette palette, Map<DerivedColor, Color> overrides) {
        var values = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        for (var role : DerivedColor.values()) {
            Color override = overrides.get(role);
            values.put(role, override != null ? override : recipe(role, input -> read(input, palette, values, role)));
        }
        return new DerivedPalette(values);
    }

    /** The roles {@code role}'s recipe reads over {@code palette}, in the order it reads them. */
    static Set<ColorRole> inputs(DerivedColor role, SemanticPalette palette) {
        var earlier = derive(palette, Map.of());
        var reads = new LinkedHashSet<ColorRole>();
        var values = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        for (var before : DerivedColor.values()) if (before.ordinal() < role.ordinal()) values.put(before, earlier.color(before));
        recipe(role, input -> { reads.add(input); return read(input, palette, values, role); });
        return reads;
    }

    private static Color read(ColorRole input, SemanticPalette palette, Map<DerivedColor, Color> values, DerivedColor reader) {
        return switch (input) {
            case SemanticColor role -> palette.color(role);
            case DerivedColor role -> {
                Color value = values.get(role);
                if (value == null) throw new IllegalStateException(reader + " reads " + role + ", which is computed after it");
                yield value;
            }
        };
    }

    /** {@code role}'s recipe (spec 7.1), reading its inputs through {@code in}. */
    private static Color recipe(DerivedColor role, Function<ColorRole, Color> in) {
        return switch (role) {
            case SURFACE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .05), in.apply(SURFACE_APP), 3);
            case SURFACE_HEADER_ACTIVE -> SurfaceContrast.apart(mix(in, ACCENT, SURFACE_APP, .10),
                in.apply(DerivedColor.SURFACE_HEADER), 2);
            case SURFACE_STRIPE -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .035), in.apply(SURFACE_APP), 1.5);
            case SURFACE_TABLE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .06), in.apply(SURFACE_CARD), 3);
            case SURFACE_ZEBRA -> closer(SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .025), in.apply(SURFACE_CARD), 1.5),
                in.apply(DerivedColor.SURFACE_TABLE_HEADER), in.apply(SURFACE_CARD));
            case SURFACE_ROW_HOVER -> mix(in, ACCENT, SURFACE_CARD, .08);
            case WARNING_GROUND -> mix(in, STATUS_WARNING, SURFACE_CARD, GROUND);
            case WARNING_BORDER -> mix(in, STATUS_WARNING, SURFACE_CARD, EDGE);
            case DANGER_GROUND -> mix(in, STATUS_DANGER, SURFACE_CARD, GROUND);
            case DANGER_BORDER -> mix(in, STATUS_DANGER, SURFACE_CARD, EDGE);
            case INFO_GROUND -> mix(in, ACCENT, SURFACE_CARD, GROUND);
            case INFO_BORDER -> mix(in, ACCENT, SURFACE_CARD, EDGE);
            case SURFACE_FOOTER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_DIALOG, .03), in.apply(SURFACE_DIALOG), 2);
            case BORDER_DIVIDER -> ColorMath.mix(boundary(in, BORDER_WINDOW, SurfaceContrast.Guard.STRUCTURAL), in.apply(SURFACE_CARD), .73);
            case BORDER_ROW -> ColorMath.mix(boundary(in, BORDER_WINDOW, SurfaceContrast.Guard.STRUCTURAL), in.apply(SURFACE_CARD), .46);
            case BORDER_HOVER -> {
                Color edge = boundary(in, BORDER_CONTROL, SurfaceContrast.Guard.CONTROL);
                yield ColorMath.mix(flat(in.apply(TEXT_PRIMARY), edge), edge, .24);
            }
            case ACCENT_EDGE -> away(flat(in.apply(ACCENT), in.apply(SURFACE_CARD)), in.apply(SURFACE_CARD), 7);
            case BUTTON_END -> mix(in, TEXT_PRIMARY, BUTTON_SECONDARY_BG, .04);
            case TAG_BORDER_BLUE -> mix(in, HUE_BLUE, SURFACE_CARD, EDGE);
            case TAG_BORDER_GREEN -> mix(in, HUE_GREEN, SURFACE_CARD, EDGE);
            case TAG_BORDER_AMBER -> mix(in, HUE_AMBER, SURFACE_CARD, EDGE);
            case TAG_BORDER_PURPLE -> mix(in, HUE_PURPLE, SURFACE_CARD, EDGE);
            case TAG_BORDER_NEUTRAL -> mix(in, HUE_NEUTRAL, SURFACE_CARD, EDGE);
        };
    }

    /** {@code share} of {@code top} over {@code bottom}. */
    private static Color mix(Function<ColorRole, Color> in, SemanticColor top, SemanticColor bottom, double share) {
        Color under = in.apply(bottom);
        return ColorMath.mix(flat(in.apply(top), under), under, share);
    }

    /** {@code role} as painted on {@code surface.card}, held there by {@code guard} toward {@code text.primary}. */
    private static Color boundary(Function<ColorRole, Color> in, SemanticColor role, SurfaceContrast.Guard guard) {
        Color card = in.apply(SURFACE_CARD), ink = in.apply(TEXT_PRIMARY);
        return guard.apply(flat(in.apply(role), card), card, ink);
    }

    /** {@code color} laid over {@code under} when it carries alpha; opaque. */
    private static Color flat(Color color, Color under) {
        return color.getAlpha() == 255 ? color : ColorMath.mix(color, under, color.getAlpha() / 255.0);
    }

    /** {@code zebra}, or halfway from {@code header} to {@code card} when it would not be closer to {@code card}. */
    private static Color closer(Color zebra, Color header, Color card) {
        double surface = ColorMath.lightness(card);
        return Math.abs(ColorMath.lightness(zebra) - surface) < Math.abs(ColorMath.lightness(header) - surface)
            ? zebra : ColorMath.mix(header, card, .5);
    }

    /**
     * {@code color} moved {@code distance} CIE L* further from {@code from}, in 1% steps toward black (when it is darker
     * than {@code from}, or as light and {@code from} is light) or white, ending there when that is not far enough.
     */
    private static Color away(Color color, Color from, double distance) {
        double own = ColorMath.lightness(color), other = ColorMath.lightness(from);
        Color toward = own < other || (own == other && other >= 50) ? Color.BLACK : Color.WHITE;
        for (int step = 1; step <= 100; step++) {
            Color candidate = ColorMath.mix(toward, color, step / 100.0);
            if (Math.abs(ColorMath.lightness(candidate) - own) >= distance) return candidate;
        }
        return toward;
    }
}
