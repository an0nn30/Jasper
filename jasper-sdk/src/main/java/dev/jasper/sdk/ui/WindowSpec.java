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
 * @param sizeBasis whether the initial preferred size describes outer bounds or body content in host user units; saved outer bounds win
 * @param singleton when true, creating the window again while it is open returns the open one
 */
public record WindowSpec(String id, String title, Dimension preferredSize, boolean singleton, SizeBasis sizeBasis) {
    /** Initial size interpretation; remembered bounds always describe the outer window. */
    public enum SizeBasis { OUTER, CONTENT }
    /** Retains the original outer-size JVM constructor. */
    public WindowSpec(String id, String title, Dimension preferredSize, boolean singleton) {
        this(id, title, preferredSize, singleton, SizeBasis.OUTER);
    }
    /** Returns a defensive copy. */
    @Override public Dimension preferredSize() { return new Dimension(preferredSize); }
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates and copies. */
    public WindowSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced window id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A window needs a title");
        Objects.requireNonNull(preferredSize, "preferredSize");
        Objects.requireNonNull(sizeBasis, "sizeBasis");
        if (preferredSize.width <= 0 || preferredSize.height <= 0) throw new IllegalArgumentException("A window needs a positive size");
        preferredSize = new Dimension(preferredSize);
    }
}
