package dev.jasper.app.appearance;

import dev.jasper.ui.theme.ColorMath;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic, opaque sRGB repairs; compositing belongs to the caller that knows the parent. */
public final class DesktopContrast {
    private DesktopContrast() { }

    /** The nearest passing 5% step toward black or white; black wins equal RGB distances. */
    public static Optional<Color> repair(Color color, Color background, double minimum) {
        return repair(color, List.of(background), minimum);
    }

    static Optional<Color> repair(Color color, List<Color> backgrounds, double minimum) {
        Objects.requireNonNull(color);
        if (!Double.isFinite(minimum) || minimum < 1 || minimum > 21) throw new IllegalArgumentException("contrast target");
        if (color.getAlpha() != 255 || backgrounds.stream().anyMatch(b -> b.getAlpha() != 255)) return Optional.empty();
        return repair(color, candidate -> backgrounds.stream().allMatch(b -> ColorMath.contrast(candidate, b) >= minimum));
    }

    static Optional<Color> repair(Color color, java.util.function.Predicate<Color> accepts) {
        Color best = null;
        long distance = Long.MAX_VALUE;
        for (Color end : List.of(Color.BLACK, Color.WHITE)) {
            for (int step = 0; step <= 20; step++) {
                Color candidate = ColorMath.mix(end, color, step / 20.0);
                if (!accepts.test(candidate)) continue;
                long next = square(candidate.getRed() - color.getRed()) + square(candidate.getGreen() - color.getGreen())
                    + square(candidate.getBlue() - color.getBlue());
                if (next < distance) { best = candidate; distance = next; }
            }
        }
        return Optional.ofNullable(best);
    }

    private static long square(int value) { return (long) value * value; }
}
