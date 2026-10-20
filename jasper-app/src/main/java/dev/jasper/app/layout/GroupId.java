package dev.jasper.app.layout;

import java.util.Objects;
import java.util.UUID;

/**
 * A pane's identity (the code calls a pane a group), unique in the application and kept in {@code workspace.toml}.
 *
 * @param value the UUID
 */
public record GroupId(UUID value) {
    /** Checks the value is present. */
    public GroupId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A new, random id.
     *
     * @return the id
     */
    public static GroupId random() {
        return new GroupId(UUID.randomUUID());
    }

    /**
     * The id {@link #toString()} wrote.
     *
     * @param text a canonical UUID, in either case
     * @return the id
     * @throws IllegalArgumentException when {@code text} is anything else
     */
    public static GroupId parse(String text) {
        return new GroupId(Ids.uuid(text));
    }

    /** The UUID in canonical form. */
    @Override public String toString() {
        return value.toString();
    }
}
