package dev.jasper.fileserver.engine;

import java.time.Duration;
import java.util.Locale;

/** Byte counts and durations as people read them. */
public final class Sizes {
    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private Sizes() { }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < UNITS.length - 1);
        return String.format(Locale.ROOT, value >= 10 ? "%.0f %s" : "%.1f %s", value, UNITS[unit]);
    }

    public static String seconds(Duration took) {
        return String.format(Locale.ROOT, "%.1f s", took.toMillis() / 1000.0);
    }
}
