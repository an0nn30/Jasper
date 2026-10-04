package dev.jasper.sdk.notifications;

/** How a group's notifications appear until the user chooses otherwise in Settings. */
public enum DisplayMode {
    /** A card that hides by itself, and history. */
    CARD,
    /** A card that stays until it is closed, and history. */
    STICKY,
    /** History only. */
    HISTORY,
    /** Not shown. */
    OFF
}
