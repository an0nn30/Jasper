package dev.jasper.app.notifications;

import java.util.Locale;
import java.util.Optional;

/** How a group's notifications appear: an auto-hiding card, a sticky card, history only, or not at all. */
public enum DisplayMode {
    CARD, STICKY, HISTORY, OFF;

    /** How {@code notifications.groups} writes it: "card", "sticky", "history" or "off". */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode {@code token} names in {@code notifications.groups}, if it names one. */
    public static Optional<DisplayMode> parse(String token) {
        for (DisplayMode mode : values()) if (mode.token().equals(token)) return Optional.of(mode);
        return Optional.empty();
    }
}
