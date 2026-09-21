package dev.jasper.app.config;

/** Validated palette result bound; immutable and safe to share across threads. */
public record PaletteSettings(int maxResults) {
    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final int MIN_MAX_RESULTS = 1;
    public static final int MAX_MAX_RESULTS = 20;
    public PaletteSettings {
        if (maxResults < MIN_MAX_RESULTS || maxResults > MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be 1–20.");
    }
}
