package dev.jasper.sdk;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A plugin's verified identity, as read from its descriptor by the runtime.
 *
 * @param id namespaced id matching {@code [a-z][a-z0-9_.-]{0,127}}; {@code jasper} and the
 *           {@code jasper.} prefix are reserved for the application
 * @param name non-blank display name
 * @param version non-blank semantic version
 * @param capabilities capabilities the user consented to; copied
 */
public record PluginInfo(String id, String name, String version, Set<String> capabilities) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    /** Validates the id, rejects blank text and copies the capability set. */
    public PluginInfo {
        if (!validId(id)) throw new IllegalArgumentException("Invalid plugin id: " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A plugin needs a name");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("A plugin needs a version");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    /**
     * Whether a string may be used as a plugin id.
     *
     * @param id candidate id, possibly null
     * @return true when well-formed and outside the reserved {@code jasper} namespace
     */
    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches() && !id.equals("jasper") && !id.startsWith("jasper.");
    }
}
