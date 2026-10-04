package dev.jasper.ui.theme;

import java.util.Locale;
import java.util.Optional;

/** The {@code [colors]} seeds of a Jasper theme; the eight ANSI colours are separate ({@link ThemeDocument#ansi()}). */
public enum Seed {
    BACKGROUND, FOREGROUND, ACCENT, CURSOR, SELECTION;

    /** The TOML key, for example {@code background}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The seed with this TOML key. */
    public static Optional<Seed> of(String key) {
        for (Seed seed : values()) if (seed.key().equals(key)) return Optional.of(seed);
        return Optional.empty();
    }
}
