package dev.jasper.sdk.ui;

import java.awt.Dimension;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A plugin window the application builds.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; the window's bounds are remembered under it
 * @param title non-blank title
 * @param preferredSize size used until the user has resized the window; copied, treat as immutable
 * @param singleton when true, creating the window again while it is open returns the open one
 */
public record WindowSpec(String id, String title, Dimension preferredSize, boolean singleton) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates and copies. */
    public WindowSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced window id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A window needs a title");
        Objects.requireNonNull(preferredSize, "preferredSize");
        if (preferredSize.width <= 0 || preferredSize.height <= 0) throw new IllegalArgumentException("A window needs a positive size");
        preferredSize = new Dimension(preferredSize);
    }
}
