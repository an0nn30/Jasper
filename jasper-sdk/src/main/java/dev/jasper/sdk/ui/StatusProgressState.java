package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * An atomic status-progress update. Text is plain, never HTML.
 * @param text short operation label
 * @param detail rate/remaining or other short detail
 * @param accessibleDescription complete spoken progress description
 * @param fraction zero through one, or empty for an unknown total
 * @param actionId primary action owned by this plugin, or null
 * @param secondaryActionId optional secondary action owned by this plugin, or null
 * @since 0.7.5
 */
public record StatusProgressState(String text, String detail, String accessibleDescription,
                                  OptionalDouble fraction, String actionId, String secondaryActionId) {
    /** Validates the progress and normalizes line breaks in plain text. */
    public StatusProgressState {
        text = plain(text); detail = plain(detail); accessibleDescription = plain(accessibleDescription);
        Objects.requireNonNull(fraction, "fraction");
        if (fraction.isPresent() && (!Double.isFinite(fraction.getAsDouble())
                || fraction.getAsDouble() < 0 || fraction.getAsDouble() > 1))
            throw new IllegalArgumentException("Progress must be between zero and one");
    }
    private static String plain(String value) {
        return Objects.requireNonNull(value, "text").replace('\r', ' ').replace('\n', ' ');
    }
}
