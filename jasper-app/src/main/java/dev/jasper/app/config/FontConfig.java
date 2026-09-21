package dev.jasper.app.config;

import java.util.List;
import java.util.Objects;

/** Saved typography defaults, independent of a pane's temporary size override. */
public record FontConfig(String family, float size, List<String> fallback, boolean ligatures, float lineHeight) {
    public FontConfig {
        requireName(family);
        if (!Float.isFinite(size) || size < 6 || size > 72) {
            throw new IllegalArgumentException("Font size must be a finite number from 6–72.");
        }
        if (!Float.isFinite(lineHeight) || lineHeight < 1 || lineHeight > 3) {
            throw new IllegalArgumentException("Line height must be a finite number from 1–3.");
        }
        fallback = List.copyOf(fallback);
        fallback.forEach(FontConfig::requireName);
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name, "font name");
        if (name.isBlank() || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Font names must be nonblank and contain no NUL.");
        }
    }

    public static FontConfig defaults() {
        return new FontConfig("JetBrains Mono", 16f,
            List.of("Symbols Nerd Font Mono", "Apple Color Emoji"), true, 1f);
    }

    public FontConfig withSize(float size) {
        return new FontConfig(family, size, fallback, ligatures, lineHeight);
    }
}
