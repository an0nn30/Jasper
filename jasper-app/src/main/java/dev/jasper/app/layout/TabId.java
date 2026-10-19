package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A tab's identity, unique in the application for the tab's life and kept in {@code workspace.toml}.
 *
 * @param value the UUID
 */
public record TabId(UUID value) {
    /** Checks the value is present. */
    public TabId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static TabId random() {
        return new TabId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static TabId parse(String text) {
        return new TabId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
