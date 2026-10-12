package dev.jasper.ui.theme;

import java.util.Optional;

/** The terminal colour schemes Jasper ships; each UI variant pairs with its own by default. */
public enum TerminalScheme {
    SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");

    private final String key;
    TerminalScheme(String key) { this.key = key; }

    /** The scheme's key in the theme file and in configuration, for example {@code high-contrast}. */
    public String key() { return key; }

    /** The scheme with this key. */
    public static Optional<TerminalScheme> of(String key) {
        for (var scheme : values()) if (scheme.key.equals(key)) return Optional.of(scheme);
        return Optional.empty();
    }

    /** The scheme that matches a UI variant: Slate Steel Dark with Dark, Slate Steel Light with Light. */
    public static TerminalScheme matching(boolean dark) { return dark ? SLATE_STEEL_DARK : SLATE_STEEL_LIGHT; }
}
