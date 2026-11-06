package dev.jasper.browser.permissions;

/** What a site's stored audio playback decision says to do when its page first plays sound (spec section 12). */
public enum AudioMode {
    /** Allowed Always: the page plays. */
    ALLOW,
    /** Denied Always: the page is muted. */
    BLOCK,
    /** Nothing stored: the page is muted, and the audio bar asks. */
    ASK
}
