package dev.jasper.sdk;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How a plugin's tab comes back when Jasper restores its windows: the kind of restorer the
 * plugin registered with {@code Windows.restorer} and a small map of strings the restorer rebuilds the tab from, such as a
 * file's path. Jasper writes it into {@code workspace.toml} with the tab, so it holds no secret. A content tab gives it in
 * its {@code ContentSpec} or later with {@code ContentTab.setRestore}; a provided session gives it in its
 * {@code SessionSpec}.
 *
 * @param kind the restorer kind: 1 to 64 letters, digits, dots, dashes and underscores
 * @param values what the restorer needs: at most {@link #MAX_ENTRIES} entries, no key holding a lone surrogate, whose
 *               keys and values together hold at most {@link #MAX_CHARACTERS} characters; copied
 * @since 0.21.0
 */
public record RestoreState(String kind, Map<String, String> values) {
    /** The most entries a restore state holds. */
    public static final int MAX_ENTRIES = 32;
    /** The most characters a restore state's keys and values hold together. */
    public static final int MAX_CHARACTERS = 4096;
    private static final Pattern KIND = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Checks the kind, the keys and the size, and copies the values; a null key or value is refused. */
    public RestoreState {
        Objects.requireNonNull(kind, "kind");
        if (!KIND.matcher(kind).matches())
            throw new IllegalArgumentException("A restorer kind is 1 to 64 letters, digits, dots, dashes and underscores: " + kind);
        values = Map.copyOf(values);
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("A restore state holds at most " + MAX_ENTRIES + " entries");
        // Jasper writes a lone surrogate into workspace.toml as U+FFFD, so two such keys could become one after a save.
        for (String key : values.keySet())
            if (key.codePoints().anyMatch(point -> Character.getType(point) == Character.SURROGATE))
                throw new IllegalArgumentException("A restore state's key holds no lone surrogate");
        long characters = values.entrySet().stream().mapToLong(entry -> entry.getKey().length() + entry.getValue().length()).sum();
        if (characters > MAX_CHARACTERS)
            throw new IllegalArgumentException("A restore state holds at most " + MAX_CHARACTERS + " characters, not " + characters);
    }

    /**
     * A restore state of {@code kind} with one value.
     *
     * @param kind the restorer kind
     * @param key the value's key
     * @param value the value
     * @return the state
     */
    public static RestoreState of(String kind, String key, String value) { return new RestoreState(kind, Map.of(key, value)); }
}
