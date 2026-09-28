package dev.jasper.sdk.settings;

import java.util.Objects;

/**
 * One option of a {@link SettingKind#choice} setting.
 *
 * @param token the text the settings file holds; non-blank
 * @param displayName what Settings shows; non-blank
 * @param value the value the plugin reads
 * @param <T> the value type
 */
public record Choice<T>(String token, String displayName, T value) {
    /** Rejects blank text and a null value. */
    public Choice {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("A choice needs a token");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("A choice needs a display name");
        Objects.requireNonNull(value, "value");
    }

    /**
     * A choice.
     *
     * @param token the text the settings file holds
     * @param displayName what Settings shows
     * @param value the value the plugin reads
     * @param <T> the value type
     * @return the choice
     */
    public static <T> Choice<T> of(String token, String displayName, T value) { return new Choice<>(token, displayName, value); }
}
