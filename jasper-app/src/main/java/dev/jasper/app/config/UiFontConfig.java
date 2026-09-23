package dev.jasper.app.config;

import java.util.Objects;

/** UI typography, independent of terminal fonts. Zero size retains the platform default. */
public record UiFontConfig(String family, float size) {
    public UiFontConfig {
        Objects.requireNonNull(family, "family");
        if (family.isBlank() || family.indexOf('\0') >= 0)
            throw new IllegalArgumentException("UI font family must be nonblank and contain no NUL.");
        if (!Float.isFinite(size) || size != 0 && (size < 8 || size > 32))
            throw new IllegalArgumentException("UI font size must be 8–32, or zero for the platform default.");
    }
    public static UiFontConfig defaults() { return new UiFontConfig("system", 0); }
}
