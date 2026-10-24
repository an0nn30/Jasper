package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A parent tab's identity (parent tabs spec 4), unique in the application and kept in {@code workspace.toml}.
 *
 * @param value the UUID
 */
public record ParentTabId(UUID value) {
    /** Checks the value is present. */
    public ParentTabId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static ParentTabId random() {
        return new ParentTabId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static ParentTabId parse(String text) {
        return new ParentTabId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
