package dev.jasper.app.palette;

import javax.swing.Icon;

/**
 * One result row as plain data; the palette paints every scope's rows the same way. {@code token} is
 * scope-private (a scope keeps its own object there to recognise the row later); the palette never reads it.
 */
public record PaletteRow(String id, String title, String detail, String tag, Icon icon, boolean enabled, Object token) {
    public PaletteRow {
        if (id == null || id.isBlank() || id.length() > 256)
            throw new IllegalArgumentException("Row needs an ID of at most 256 characters");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Row needs a title");
        detail = detail == null || detail.isBlank() ? null : detail;
        tag = tag == null || tag.isBlank() ? null : tag;
    }

    public static PaletteRow of(String id, String title) {
        return new PaletteRow(id, title, null, null, null, true, null);
    }
}
