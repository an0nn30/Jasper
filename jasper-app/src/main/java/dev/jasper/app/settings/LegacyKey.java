package dev.jasper.app.settings;

import java.util.List;

/**
 * An old key found in a file, and the current key it moved to.
 *
 * @param from the key as the file wrote it
 * @param to the setting's current key
 */
public record LegacyKey(List<String> from, List<String> to) {
    public LegacyKey {
        from = List.copyOf(from);
        to = List.copyOf(to);
    }
}
