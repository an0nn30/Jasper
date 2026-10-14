package dev.jasper.ui.theme;

import java.util.Optional;

/**
 * A terminal scheme's id: its theme's id and its key in that theme's file, written {@code theme/key}, such as
 * {@code slate-steel/high-contrast}.
 */
public record SchemeId(String theme, String key) {
    public SchemeId {
        if (!JasperTheme.validId(theme)) throw new IllegalArgumentException("Not a theme id: " + theme);
        if (!validKey(key)) throw new IllegalArgumentException("Not a scheme key: " + key);
    }

    /** A scheme key: lowercase letters and digits in dash-separated words, at most 32 characters. */
    public static boolean validKey(String key) { return key != null && key.length() <= 32 && JasperTheme.WORDS.matcher(key).matches(); }

    /** The id {@code text} names, or empty when it is not {@code <theme>/<key>}. */
    public static Optional<SchemeId> parse(String text) {
        int slash = text == null ? -1 : text.indexOf('/');
        if (slash < 0) return Optional.empty();
        String theme = text.substring(0, slash), key = text.substring(slash + 1);
        return JasperTheme.validId(theme) && validKey(key) ? Optional.of(new SchemeId(theme, key)) : Optional.empty();
    }

    /** {@code theme/key}, as config.toml writes it. */
    @Override public String toString() { return theme + "/" + key; }
}
