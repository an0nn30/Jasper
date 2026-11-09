package dev.jasper.browser.permissions;

import java.util.Optional;

/** What a site's stored audio playback decision says to do when its page first plays sound (spec section 12). */
public enum AudioMode {
    /** Allowed Always: the page plays. */
    ALLOW,
    /** Denied Always: the page is muted. */
    BLOCK,
    /** Nothing stored: the page is muted, and the audio bar asks. */
    ASK;

    /**
     * What {@code stored}, a site's audio playback decision, says.
     *
     * @param stored the decision, or empty when none is stored
     * @return {@link #ALLOW} for Allowed, {@link #BLOCK} for Blocked, and {@link #ASK} for none
     */
    public static AudioMode of(Optional<Decision> stored) {
        return stored.map(decision -> decision == Decision.ALLOWED ? ALLOW : BLOCK).orElse(ASK);
    }
}
