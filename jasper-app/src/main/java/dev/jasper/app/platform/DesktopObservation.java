package dev.jasper.app.platform;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, toolkit-independent desktop preferences and palette observations. */
public record DesktopObservation(Portal portal, Map<Source, Palette> palettes) {
    public DesktopObservation {
        Objects.requireNonNull(portal);
        palettes = Map.copyOf(palettes);
    }

    public enum Source { GTK3, GTK4 }
    public enum Polarity { DARK, LIGHT, UNKNOWN }
    public enum Pair { WINDOW, VIEW, HEADER, SIDEBAR, DIALOG, POPOVER, SELECTION }

    public record Rgba(double red, double green, double blue, double alpha) {
        public Rgba {
            component(red); component(green); component(blue); component(alpha);
        }
        private static void component(double value) {
            if (!Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Color component outside [0,1]");
        }
    }

    public record Colors(Rgba background, Rgba foreground) {
        public Colors {
            Objects.requireNonNull(background);
            Objects.requireNonNull(foreground);
        }
    }

    public record Portal(long owner, Polarity appearance, Optional<Rgba> accent,
                         Optional<Boolean> highContrast, String reason) {
        public Portal {
            generation(owner);
            Objects.requireNonNull(appearance);
            Objects.requireNonNull(accent);
            Objects.requireNonNull(highContrast);
            reason = DesktopObservation.reason(reason);
        }
    }

    public record Palette(long generation, Source source, Polarity appearance,
                          Map<Pair, Colors> colors, String implementation,
                          String version, String reason) {
        public Palette {
            DesktopObservation.generation(generation);
            Objects.requireNonNull(source);
            Objects.requireNonNull(appearance);
            colors = Map.copyOf(colors);
            Objects.requireNonNull(implementation);
            Objects.requireNonNull(version);
            reason = DesktopObservation.reason(reason);
        }
    }

    private static String reason(String value) {
        Objects.requireNonNull(value);
        if (value.codePointCount(0, value.length()) > 1024)
            throw new IllegalArgumentException("Reason exceeds 1024 characters");
        return value;
    }

    static long generation(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative generation");
        return value;
    }
}
