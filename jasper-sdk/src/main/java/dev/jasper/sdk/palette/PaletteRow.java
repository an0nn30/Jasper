package dev.jasper.sdk.palette;

import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;

/**
 * One result row as plain data; the palette paints every scope's rows the same way. The host never
 * reads {@code token}: a scope keeps its own object there and gets this same row back in
 * {@code available}, {@code step} and {@code execute}.
 *
 * @param id      unique within one result list, at most 256 characters; a step's {@code reopen} names it
 * @param title   the main text
 * @param detail  secondary text under the title, such as a directory
 * @param tag     a short right-aligned marker, such as a shortcut or "2 fields"
 * @param icon    a row icon
 * @param enabled false paints the row dimmed and makes it unavailable
 * @param token   scope-private, may be null
 */
public record PaletteRow(String id, String title, Optional<String> detail, Optional<String> tag, Optional<Icon> icon,
                         boolean enabled, Object token) {
    /** Validates the id and title; blank {@code detail} and {@code tag} become empty. */
    public PaletteRow {
        if (id == null || id.isBlank() || id.length() > 256) throw new IllegalArgumentException("A row needs an id of at most 256 characters");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A row needs a title");
        detail = Objects.requireNonNull(detail, "detail").filter(text -> !text.isBlank());
        tag = Objects.requireNonNull(tag, "tag").filter(text -> !text.isBlank());
        Objects.requireNonNull(icon, "icon");
    }

    /**
     * An enabled row with a title only.
     *
     * @param id    the row id
     * @param title the title
     * @return the row
     */
    public static PaletteRow of(String id, String title) {
        return new PaletteRow(id, title, Optional.empty(), Optional.empty(), Optional.empty(), true, null);
    }

    /**
     * Derives a value.
     *
     * @param value the detail text, or null for none
     *  @return a copy with that detail */
    public PaletteRow withDetail(String value) { return new PaletteRow(id, title, Optional.ofNullable(value), tag, icon, enabled, token); }

    /**
     * Derives a value.
     *
     * @param value the tag, or null for none
     *  @return a copy with that tag */
    public PaletteRow withTag(String value) { return new PaletteRow(id, title, detail, Optional.ofNullable(value), icon, enabled, token); }

    /**
     * Derives a value.
     *
     * @param value the icon, or null for none
     *  @return a copy with that icon */
    public PaletteRow withIcon(Icon value) { return new PaletteRow(id, title, detail, tag, Optional.ofNullable(value), enabled, token); }

    /**
     * Derives a value.
     *
     * @param value whether the row can be chosen
     *  @return a copy with that state */
    public PaletteRow withEnabled(boolean value) { return new PaletteRow(id, title, detail, tag, icon, value, token); }

    /**
     * Derives a value.
     *
     * @param value the scope-private object, or null
     *  @return a copy carrying it */
    public PaletteRow withToken(Object value) { return new PaletteRow(id, title, detail, tag, icon, enabled, value); }
}
