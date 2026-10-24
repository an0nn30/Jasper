package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/** Parsing shared by {@link TabId}, {@link GroupId} and {@link ParentTabId}. */
final class Ids {
    private Ids() { }

    /** The UUID {@code text} names in canonical form (36 characters, hyphenated, either case); anything else is rejected. */
    static UUID uuid(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 36) {
            try {
                UUID value = UUID.fromString(text);
                if (value.toString().equalsIgnoreCase(text)) return value;
            } catch (IllegalArgumentException malformed) {
                // Rejected below, naming the text.
            }
        }
        throw new IllegalArgumentException("Not a canonical UUID: " + text);
    }
}
