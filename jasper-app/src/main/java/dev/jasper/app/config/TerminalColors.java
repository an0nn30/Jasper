package dev.jasper.app.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The saved terminal palette choice, independent of the UI theme: {@link #MATCH} uses the theme's own palette,
 * {@link #LIGHT} and {@link #DARK} Jasper's, and any other name a terminal theme (Settings > Themes > Alacritty Themes).
 *
 * @param name {@code match}, {@code light}, {@code dark}, or a terminal theme's name as written
 */
public record TerminalColors(String name) {
    private static final List<String> RESERVED = List.of("match", "light", "dark");
    /** The UI theme's own terminal palette. */
    public static final TerminalColors MATCH = new TerminalColors("match");
    /** Jasper Light's palette. */
    public static final TerminalColors LIGHT = new TerminalColors("light");
    /** Jasper Dark's palette. */
    public static final TerminalColors DARK = new TerminalColors("dark");

    /** Strips {@code name} and lower-cases match, light and dark; it must not be blank. */
    public TerminalColors {
        name = normalize(Objects.requireNonNull(name, "name"));
        if (name.isEmpty()) throw new IllegalArgumentException("A terminal colours choice needs a name");
    }

    /** The saved text as a choice; blank means {@link #MATCH}. */
    public static TerminalColors parse(String text) {
        String normalized = normalize(text == null ? "" : text);
        return normalized.isEmpty() ? MATCH : new TerminalColors(normalized);
    }

    /** {@code text} stripped, with match, light and dark lower-cased whatever case they were written in. */
    public static String normalize(String text) {
        String stripped = text.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        return RESERVED.contains(lower) ? lower : stripped;
    }

    /** Whether {@code name} is one of the built-in choices, ignoring case and surrounding spaces. */
    public static boolean reserved(String name) { return RESERVED.contains(name.strip().toLowerCase(Locale.ROOT)); }

    /** Match, Light and Dark, in menu order. */
    public static List<TerminalColors> builtIns() { return List.of(MATCH, LIGHT, DARK); }

    /** Whether this names a terminal theme rather than a built-in choice. */
    public boolean named() { return !RESERVED.contains(name); }
}
