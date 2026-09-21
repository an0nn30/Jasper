package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Where a status item goes.
 *
 * @param id namespaced id that starts with the plugin's id and a dot
 * @param side which end of the status bar
 * @param priority items on one side are ordered by ascending priority, left to right
 */
public record StatusItemSpec(String id, Side side, int priority) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and side. */
    public StatusItemSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced status item id: " + id);
        Objects.requireNonNull(side, "side");
    }
}
