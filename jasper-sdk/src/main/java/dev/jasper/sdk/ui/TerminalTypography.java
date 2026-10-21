package dev.jasper.sdk.ui;

import java.util.List;

/**
 * Saved terminal typography, shared by every window and independent of a pane's temporary zoom.
 * Read it through {@link Appearance#terminalTypography()}.
 *
 * @param family primary font family, nonblank and without NUL
 * @param size finite point size from 6 to 72, including fractional points
 * @param fallback ordered fallback family names, nonblank and without NUL
 * @param ligatures whether font ligatures are enabled
 * @param lineHeight finite line-height multiplier from 0.5 to 3
 * @since 0.18.3
 */
public record TerminalTypography(String family, double size, List<String> fallback,
                                 boolean ligatures, double lineHeight) {
    /** Copies the fallback list and validates the family names and dimensions.
     * @throws IllegalArgumentException when a name or dimension is invalid
     * @throws NullPointerException when the fallback list or an element is null
     */
    public TerminalTypography {
        requireName(family);
        fallback = List.copyOf(fallback);
        fallback.forEach(TerminalTypography::requireName);
        if (!Double.isFinite(size) || size < 6 || size > 72)
            throw new IllegalArgumentException("Font size must be between 6 and 72.");
        if (!Double.isFinite(lineHeight) || lineHeight < .5 || lineHeight > 3)
            throw new IllegalArgumentException("Line height must be between 0.5 and 3.");
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
            throw new IllegalArgumentException("A font family must be nonblank and contain no NUL.");
    }
}
