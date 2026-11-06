package dev.jasper.browser.permissions;

import java.util.Objects;
import java.util.Optional;

/**
 * What a site may be allowed or blocked (spec section 12), in the order the permissions page and the menu button list
 * them. Each has its token, which the {@code permissions} table and Jasper's pages write, and its label; the four a
 * prompt asks about also have the prompt's heading and text. Audio playback has neither, as the audio bar asks for it.
 */
public enum Permission {
    /** The camera. */
    CAMERA("camera", "Camera", "use your camera"),
    /** The microphone. */
    MICROPHONE("microphone", "Microphone", "use your microphone"),
    /** The camera and the microphone, asked for together. */
    CAMERA_AND_MICROPHONE("camera-microphone", "Camera and Microphone", "use your camera and microphone"),
    /** Notifications the page posts. */
    NOTIFICATIONS("notifications", "Notifications", "send you notifications"),
    /** Playing sound. */
    AUDIO_PLAYBACK("audio", "Audio Playback", "");

    private final String token, label, wants;

    Permission(String token, String label, String wants) {
        this.token = token;
        this.label = label;
        this.wants = wants;
    }

    /**
     * Its token, as the {@code permissions} table and Jasper's pages write it.
     *
     * @return the token, such as {@code camera-microphone}
     */
    public String token() {
        return token;
    }

    /**
     * Its label, as the permissions page and the menu button show it.
     *
     * @return the label, such as "Camera and Microphone"
     */
    public String label() {
        return label;
    }

    /**
     * The heading of the prompt that asks for it.
     *
     * @return "Allow " and the label, such as "Allow Camera"
     * @throws UnsupportedOperationException for {@link #AUDIO_PLAYBACK}, which no prompt asks for
     */
    public String heading() {
        prompted();
        return "Allow " + label;
    }

    /**
     * The text of the prompt that asks for it for {@code origin}.
     *
     * @param origin the site that asks, as {@link Origins#of} gives it
     * @return such as "The website https://example.com wants to use your camera."
     * @throws UnsupportedOperationException for {@link #AUDIO_PLAYBACK}, which no prompt asks for
     */
    public String asks(String origin) {
        Objects.requireNonNull(origin, "origin");
        prompted();
        return "The website " + origin + " wants to " + wants + ".";
    }

    /**
     * The permission whose token is {@code token}.
     *
     * @param token a token, as {@link #token()} writes it
     * @return the permission; empty for any other text
     */
    public static Optional<Permission> ofToken(String token) {
        for (Permission permission : values()) if (permission.token.equals(token)) return Optional.of(permission);
        return Optional.empty();
    }

    private void prompted() {
        if (this == AUDIO_PLAYBACK) throw new UnsupportedOperationException("No prompt asks for audio playback: the audio bar does");
    }
}
