package dev.jasper.browser.store;

import java.util.Locale;

/** How a page in history was reached (spec section 10), stored as its name in lower case. */
public enum Reached {
    /** Typed in the address field, or opened by Jasper. */
    TYPED,
    /** A link followed. */
    LINK,
    /** A reload. */
    RELOAD,
    /** Back or Forward. */
    BACK_FORWARD,
    /** A redirect. */
    REDIRECT,
    /** A form submitted. */
    FORM_SUBMIT,
    /** Any other way. */
    OTHER;

    /** The value the database stores. */
    String stored() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The way a stored value names; {@link #OTHER} for one this version does not know. */
    static Reached of(String stored) {
        for (Reached reached : values())
            if (reached.stored().equals(stored)) return reached;
        return OTHER;
    }
}
