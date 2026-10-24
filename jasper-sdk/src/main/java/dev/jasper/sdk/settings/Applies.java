package dev.jasper.sdk.settings;

/** When a saved change to a setting takes effect. */
public enum Applies {
    /** At once: the plugin reads the new value when it changes. */
    LIVE,
    /**
     * When the plugin next starts. Settings marks the setting "Takes effect after restart"; the handle's value and its
     * listeners still change at once.
     */
    RESTART
}
