package dev.jasper.app.config;

/** Validated palette list height in rows; immutable and safe to share across threads. */
public record PaletteSettings(int visibleRows) {
    public static final int DEFAULT_VISIBLE_ROWS = 5;
    public static final int MIN_VISIBLE_ROWS = 1;
    public static final int MAX_VISIBLE_ROWS = 20;
    public PaletteSettings {
        if (visibleRows < MIN_VISIBLE_ROWS || visibleRows > MAX_VISIBLE_ROWS)
            throw new IllegalArgumentException("Visible rows must be 1–20.");
    }
}
